package com.rabpit.backroom.core.foundation

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class FoundationCoordinator private constructor(context: Context) {
  private val appContext = context.applicationContext
  private val catalog = AndroidFoundationSourceCatalog(appContext)
  private val compiler = FoundationCompiler()
  private val store = FoundationStore(File(appContext.filesDir, "foundation"))
  private val scheduler = FoundationBuildScheduler()
  private val executor = Executors.newFixedThreadPool(2) { runnable ->
    Thread(runnable, "foundation-local-worker").apply { isDaemon = true }
  }
  private val active = AtomicReference(store.loadActive())
  private val building = AtomicBoolean(false)
  private val pendingProjection = AtomicReference<String?>(null)
  private val preparedProjectionHash = AtomicReference<String?>(null)
  private val pins = ConcurrentHashMap<String, FoundationHandle>()
  private val lastFailure = AtomicReference<String?>(null)

  fun warm(stateProjectionJson: String) {
    pendingProjection.set(stateProjectionJson)
    if (!building.compareAndSet(false, true)) return
    executor.execute {
      try {
        while (true) {
          val requested = pendingProjection.getAndSet(null) ?: break
          prepare(requested)
        }
      } finally {
        building.set(false)
        if (pendingProjection.get() != null) warm(pendingProjection.get().orEmpty())
      }
    }
  }

  fun pin(turnId: String, stateProjectionJson: String): FoundationHandle? {
    val projectionHash = FoundationDigest.sha256(stateProjectionJson)
    val stableTurnId = turnId.trim().takeIf { it.isNotEmpty() } ?: return prepare(stateProjectionJson)
    val pinKey = "$stableTurnId:$projectionHash"
    return pins[pinKey] ?: prepare(stateProjectionJson)?.also { pins.putIfAbsent(pinKey, it) }
  }

  fun release(turnId: String) {
    if (turnId.isNotBlank()) pins.keys.removeAll { it.startsWith("$turnId:") }
  }

  fun buildSlice(
    turnId: String,
    stateProjectionJson: String,
    legacyStateJson: String,
    action: String,
    rollsJson: String,
    role: FoundationSliceRole
  ): String {
    val handle = pin(turnId, stateProjectionJson) ?: return ""
    return try {
      FoundationTurnSliceBuilder(store).build(handle, legacyStateJson, action, rollsJson, role).also {
        lastFailure.set(null)
      }
    } catch (error: Throwable) {
      rememberFailure("FOUNDATION_SLICE", error)
      ""
    }
  }

  fun lastFailure(): String = lastFailure.get().orEmpty()

  private fun prepare(stateProjectionJson: String): FoundationHandle? {
    val projectionHash = FoundationDigest.sha256(stateProjectionJson)
    if (preparedProjectionHash.get() == projectionHash) active.get()?.let { return it }
    return synchronized(this) {
      if (preparedProjectionHash.get() == projectionHash) active.get()
      else rebuild(stateProjectionJson)?.also { preparedProjectionHash.set(projectionHash) }
    }
  }

  private fun rebuild(stateProjectionJson: String): FoundationHandle? {
    val build = try {
      compiler.compile(catalog.load(), stateProjectionJson)
    } catch (error: Throwable) {
      rememberFailure("FOUNDATION_COMPILE", error)
      return null
    }

    val current = active.get()
    if (current != null && current.manifest.sourcePackHash == build.sourcePackHash &&
      current.manifest.objects == build.objects.associate { it.section to it.objectHash }) {
      lastFailure.set(null)
      return current
    }

    val manifest = try {
      compiler.manifest(build)
    } catch (error: Throwable) {
      rememberFailure("FOUNDATION_MANIFEST", error)
      return null
    }

    try {
      scheduler.install(store, manifest, build.objects)
      store.putManifest(manifest)
    } catch (error: Throwable) {
      rememberFailure("FOUNDATION_INSTALL", error)
      return null
    }

    return try {
      store.activate(manifest)
      FoundationHandle(manifest).also {
        active.set(it)
        lastFailure.set(null)
      }
    } catch (error: Throwable) {
      rememberFailure("FOUNDATION_ACTIVATE", error)
      null
    }
  }

  private fun rememberFailure(phase: String, error: Throwable) {
    val message = (error.message ?: error::class.java.simpleName)
      .replace('\r', ' ')
      .replace('\n', ' ')
      .take(420)
    lastFailure.set(JSONObject()
      .put("component", "FOUNDATION")
      .put("phase", phase)
      .put("errorType", error::class.java.simpleName)
      .put("message", message)
      .toString())
  }

  companion object {
    @Volatile private var instance: FoundationCoordinator? = null

    fun get(context: Context): FoundationCoordinator = instance ?: synchronized(this) {
      instance ?: FoundationCoordinator(context).also { instance = it }
    }
  }
}

object FoundationRuntime {
  @JvmStatic
  fun warm(context: Context, stateProjectionJson: String) = FoundationCoordinator.get(context).warm(stateProjectionJson)

  @JvmStatic
  fun buildSlice(
    context: Context,
    turnId: String,
    stateProjectionJson: String,
    legacyStateJson: String,
    action: String,
    rollsJson: String,
    role: String
  ): String = FoundationCoordinator.get(context).buildSlice(
    turnId, stateProjectionJson, legacyStateJson, action, rollsJson, FoundationSliceRole.fromWireName(role)
  )

  @JvmStatic
  fun releaseTurn(context: Context, turnId: String) = FoundationCoordinator.get(context).release(turnId)

  @JvmStatic
  fun lastFailure(context: Context): String = FoundationCoordinator.get(context).lastFailure()
}

internal class FoundationTurnSliceBuilder(private val store: FoundationStore) {
  private data class Candidate(val id: String, val text: String, val source: String, val score: Int)

  fun build(
    handle: FoundationHandle,
    legacyStateJson: String,
    action: String,
    rollsJson: String,
    role: FoundationSliceRole
  ): String {
    val state = runCatching { JSONObject(legacyStateJson) }.getOrElse { JSONObject() }
    val rolls = runCatching { JSONObject(rollsJson) }.getOrElse { JSONObject() }
    val terms = terms(action + " " + state.optString("location") + " " + state.optString("title"))
    val candidates = mutableListOf<Candidate>()
    val documents = mutableListOf<Candidate>()
    sectionsFor(role).forEach { section ->
      val hash = handle.manifest.objects[section] ?: return@forEach
      val body = store.readObject(hash)?.let(::JSONObject) ?: return@forEach
      val records = body.optJSONArray("records") ?: JSONArray()
      for (index in 0 until records.length()) {
        val record = records.optJSONObject(index) ?: continue
        val id = record.optString("id")
        val text = record.optString("text").trim()
        if (text.isEmpty()) continue
        val searchable = buildString {
          append(id).append(' ').append(record.optString("domain")).append(' ')
          append(record.optJSONArray("tags")?.toString().orEmpty()).append(' ')
          append(record.optJSONArray("affordances")?.toString().orEmpty())
        }.lowercase(Locale.ROOT)
        val mandatory = mandatory(role, id)
        val score = (if (mandatory) 10_000 else 0) + terms.count(searchable::contains) * 20 + record.optInt("priority", 50)
        if (mandatory || score >= 70) candidates += Candidate(id, text, sourceOf(record), score)
      }
      val docs = body.optJSONArray("documents") ?: JSONArray()
      for (index in 0 until docs.length()) {
        val document = docs.optJSONObject(index) ?: continue
        val path = document.optString("path")
        val pathScore = terms.count(path.lowercase(Locale.ROOT)::contains) * 25 + when {
          pathMatchesCurrentLevel(path, state) -> 300
          role == FoundationSliceRole.CANON_AUDIT -> 40
          else -> 0
        }
        if (pathScore >= 100) documents += Candidate(path, FoundationJson.canonical(document.opt("content")), path, pathScore)
      }
    }

    val header = buildString {
      append("[PERSISTENT_FOUNDATION_SLICE v1]\n")
      append("manifestId=").append(handle.manifest.manifestId).append('\n')
      append("sourcePackHash=").append(handle.manifest.sourcePackHash).append('\n')
      append("role=").append(role.wireName).append('\n')
      append("lockedState=").append(compactState(state)).append('\n')
      append("lockedRolls=").append(FoundationJson.canonical(rolls)).append('\n')
      append("records:\n")
    }
    val output = StringBuilder(header)
    (candidates.distinctBy { it.id }.sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.id }) +
      documents.distinctBy { it.id }.sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.id }))
      .forEach { candidate ->
        val line = "<${candidate.id}> ${candidate.text.replace('\n', ' ')} [source=${candidate.source}]\n"
        if (output.length + line.length + 35 <= role.characterBudget) output.append(line)
      }
    output.append("[END_PERSISTENT_FOUNDATION_SLICE]")
    return output.toString()
  }

  private fun sectionsFor(role: FoundationSliceRole): Set<FoundationSection> = when (role) {
    FoundationSliceRole.WRITER, FoundationSliceRole.REPAIR -> FoundationSection.entries.toSet()
    FoundationSliceRole.CANON_AUDIT -> setOf(
      FoundationSection.CORE_RULES, FoundationSection.WORLD_LEVEL, FoundationSection.STORY,
      FoundationSection.GAMEPLAY_CATALOG, FoundationSection.NARRATIVE
    )
    FoundationSliceRole.CHARACTER_AUDIT -> setOf(
      FoundationSection.CORE_RULES, FoundationSection.PARTY, FoundationSection.NARRATIVE
    )
  }

  private fun mandatory(role: FoundationSliceRole, id: String): Boolean {
    if (id in setOf("GAME.TEXT.CORE", "GAME.GM.FAIRNESS", "WRITING.KNOWLEDGE_BOUNDARY", "WRITING.PLAYER_AGENCY")) return true
    if (role == FoundationSliceRole.CHARACTER_AUDIT) return id.startsWith("CHAR.") || id.startsWith("REL.") || id.startsWith("ADDR.")
    return id == "WORLD.CORE" || id == "CHAR.KAI.RUNTIME_CORE"
  }

  private fun terms(value: String): Set<String> = value.lowercase(Locale.ROOT)
    .split(Regex("[^\\p{L}\\p{N}_.:-]+"))
    .filter { it.length >= 3 }
    .toSet()

  private fun sourceOf(record: JSONObject): String {
    val source = record.optJSONObject("source") ?: return "packaged"
    return source.optString("document") + "#" + source.optString("anchor")
  }

  private fun pathMatchesCurrentLevel(path: String, state: JSONObject): Boolean {
    val level = state.optJSONObject("level")?.optInt("number", -1) ?: state.optInt("level", -1)
    if (level < 0) return false
    return path.endsWith("/$level.json", true) || path.contains("level-$level", true) ||
      path.contains("level_$level", true) || path.contains("level$level", true)
  }

  private fun compactState(state: JSONObject): String = FoundationJson.canonical(FoundationJson.copySelected(
    state, "turn", "title", "location", "level", "player", "party", "inventory", "flags"
  )).take(4_800)
}
