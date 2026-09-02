package com.rabpit.backroom.core

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.exp
import kotlin.math.sqrt

enum class WorldPressureProposal {
  NONE,
  MAZE_PRESSURE,
  ENTITY_PRESSURE,
  ITEM_OPPORTUNITY
}

data class WorldDirectorContext(
  val actionKind: ActionKind,
  val safeZoneTags: Set<String>,
  val visitCount: Int,
  val revision: Int,
  val recentMutationKind: String?,
  val discoveredEvidenceCount: Int,
  val legalProposals: Set<WorldPressureProposal>
)

data class WorldDirectorDecision(
  val proposed: WorldPressureProposal?,
  val accepted: WorldPressureProposal,
  val reason: String,
  val featureText: String
)

fun interface WorldDirectorPolicy {
  fun choose(context: WorldDirectorContext): WorldPressureProposal?
}

/**
 * Proposal-only world pressure director.
 *
 * LiteRT may suggest a pressure class, but Core owns legality and liveness. This class never mutates
 * GameState, topology, combat, inventory, evidence, or campaign progression. Hidden Level IDs,
 * zone IDs, escape tags, blueprint facts/actions, and undiscovered evidence never enter model input.
 */
class WorldDirector(
  private val policy: WorldDirectorPolicy? = null,
  private val closeablePolicy: AutoCloseable? = policy as? AutoCloseable,
  private val telemetry: WorldDirectorTelemetryStore? = null
) : AutoCloseable {

  fun propose(
    state: GameState,
    definition: LevelDefinition,
    kind: ActionKind,
    turnKey: String? = null
  ): WorldDirectorDecision {
    val level = state.levelInstance?.takeIf { it.levelId == definition.id }
      ?: return WorldDirectorDecision(null, WorldPressureProposal.NONE, "no_registered_level", "")
    val legal = legalProposals(state, level, definition, kind)
    val context = WorldDirectorContext(
      actionKind = kind,
      safeZoneTags = safeZoneTags(level.zones[level.currentZoneId]?.tags.orEmpty()),
      visitCount = level.environment["visits:${level.currentZoneId}"]?.toIntOrNull() ?: 0,
      revision = level.revision,
      recentMutationKind = level.mutations.lastOrNull()?.kind,
      discoveredEvidenceCount = level.evidence.values.count(EvidenceState::discovered),
      legalProposals = legal
    )
    val featureText = WorldDirectorFeatures.describe(context)
    val raw = policy?.choose(context)
    val decision = when {
      raw == null -> WorldDirectorDecision(null, WorldPressureProposal.NONE, "model_abstained", featureText)
      raw !in legal -> WorldDirectorDecision(raw, WorldPressureProposal.NONE, "core_rejected_illegal_proposal", featureText)
      else -> WorldDirectorDecision(raw, raw, "core_accepted_proposal", featureText)
    }

    runCatching {
      telemetry?.let { store ->
        val effectiveTurnKey = turnKey?.ifBlank { null }
          ?: state.metadata["lastAction.turnId"]
          ?: state.turn.currentTurnId
        store.record(WorldDirectorTelemetryRecord(
          sessionId = WorldDirectorTelemetryPrivacy.opaqueSessionId(level),
          actionKind = kind,
          turnId = effectiveTurnKey,
          features = featureText,
          legalProposals = legal.toList().sortedBy { it.name },
          rawProposed = raw,
          acceptedProposal = decision.accepted,
          reason = decision.reason,
          modelAccepted = raw != null && raw in legal && raw == decision.accepted,
          visitCount = context.visitCount,
          discoveredEvidenceCount = context.discoveredEvidenceCount,
          worldRevision = context.revision
        ))
      }
    }

    return decision
  }

  fun exportTelemetry(): String = telemetry?.exportJsonl().orEmpty()
  fun clearTelemetry(): Boolean = telemetry?.clear() ?: true

  private fun legalProposals(
    state: GameState,
    level: LevelInstanceState,
    definition: LevelDefinition,
    kind: ActionKind
  ): Set<WorldPressureProposal> = buildSet {
    add(WorldPressureProposal.NONE)
    val combatClear = CombatRuntime.active(state) == null
    if (kind == ActionKind.EXPLORE && definition.generationConstraints.proceduralTopology && mazeLivenessSafe(level)) {
      add(WorldPressureProposal.MAZE_PRESSURE)
    }
    if (kind == ActionKind.EXPLORE && combatClear && definition.generationConstraints.allowEntities) {
      add(WorldPressureProposal.ENTITY_PRESSURE)
    }
    if ((kind == ActionKind.SEARCH || kind == ActionKind.EXPLORE) && combatClear) {
      add(WorldPressureProposal.ITEM_OPPORTUNITY)
    }
  }

  /**
   * The first liveness contract is intentionally conservative: pressure may only be proposed when
   * the current local graph is fully reachable and the current zone has at least two exits. Because
   * the proposal carries no target/edge mutation, it cannot delete or redirect the hidden solution.
   */
  private fun mazeLivenessSafe(level: LevelInstanceState): Boolean {
    val start = level.currentZoneId
    val current = level.zones[start] ?: return false
    if (current.connections.size < 2) return false
    val visited = linkedSetOf<String>()
    val queue = ArrayDeque<String>()
    queue.add(start)
    while (queue.isNotEmpty()) {
      val id = queue.removeFirst()
      if (!visited.add(id)) continue
      level.zones[id]?.connections.orEmpty()
        .filter(level.zones::containsKey)
        .filterNot(visited::contains)
        .forEach(queue::addLast)
    }
    return visited == level.zones.keys
  }

  override fun close() {
    runCatching { closeablePolicy?.close() }
  }

  companion object {
    @JvmField val DETERMINISTIC = WorldDirector()

    fun liteRT(context: Context): WorldDirector {
      val appContext = context.applicationContext
      val policy = LiteRTWorldDirectorPolicy(appContext)
      return WorldDirector(policy, policy, SharedPreferencesWorldDirectorTelemetryStore(appContext))
    }
  }
}

object WorldDirectorFeatures {
  private val hiddenFragments = setOf("escape", "exit", "transition", "solution", "required", "hidden", "secret", "blueprint")

  fun describe(context: WorldDirectorContext): String = buildList {
    add("action_${context.actionKind.name.lowercase()}")
    add(when {
      context.visitCount <= 1 -> "visit_first"
      context.visitCount == 2 -> "visit_repeat"
      else -> "visit_deep"
    })
    add(if (context.revision <= 2) "revision_early" else "revision_changed")
    context.recentMutationKind?.takeIf(String::isNotBlank)?.let { add("recent_${sanitize(it)}") }
    context.safeZoneTags.sorted().forEach { add("zone_${sanitize(it)}") }
    add(when {
      context.discoveredEvidenceCount == 0 -> "evidence_none"
      context.discoveredEvidenceCount <= 3 -> "evidence_some"
      else -> "evidence_many"
    })
    WorldPressureProposal.values().forEach { proposal ->
      if (proposal in context.legalProposals) add("candidate_${proposal.name.lowercase()}")
    }
  }.joinToString(" ")

  fun safeZoneTags(tags: Set<String>): Set<String> = tags.filterTo(linkedSetOf()) { tag ->
    val normalized = tag.lowercase()
    hiddenFragments.none(normalized::contains)
  }

  private fun sanitize(value: String): String = value.lowercase()
    .replace(Regex("[^a-z0-9_]+"), "_")
    .trim('_')
}

private fun safeZoneTags(tags: Set<String>): Set<String> = WorldDirectorFeatures.safeZoneTags(tags)

class LiteRTWorldDirectorPolicy(
  context: Context,
  private val modelAsset: String = "models/backrooms_director.tflite",
  private val labelsAsset: String = "models/backrooms_director_labels.txt",
  private val featureCount: Int = 4096,
  private val highConfidence: Float = .40f,
  private val highMargin: Float = .15f
) : WorldDirectorPolicy, AutoCloseable {
  private val appContext = context.applicationContext
  private val lock = Any()
  @Volatile private var interpreter: Interpreter? = null
  @Volatile private var labels: List<WorldPressureProposal>? = null
  @Volatile private var initializationError: String? = null

  override fun choose(context: WorldDirectorContext): WorldPressureProposal? {
    val runtime = ensureLoaded() ?: return null
    val output = Array(1) { FloatArray(runtime.second.size) }
    synchronized(lock) { runtime.first.run(features(WorldDirectorFeatures.describe(context)), output) }
    val probabilities = softmax(output[0])
    val index = probabilities.indices.maxByOrNull { probabilities[it] } ?: return null
    val score = probabilities.getOrElse(index) { 0f }
    val runnerUp = probabilities.filterIndexed { candidateIndex, _ -> candidateIndex != index }.maxOrNull() ?: 0f
    if (score < highConfidence || score - runnerUp < highMargin) return null
    return runtime.second.getOrNull(index)
  }

  fun loadError(): String? = initializationError

  private fun ensureLoaded(): Pair<Interpreter, List<WorldPressureProposal>>? {
    val ready = interpreter
    val readyLabels = labels
    if (ready != null && readyLabels != null) return ready to readyLabels
    synchronized(lock) {
      interpreter?.let { loaded -> labels?.let { return loaded to it } }
      return try {
        val loadedLabels = appContext.assets.open(labelsAsset).bufferedReader().useLines { lines ->
          lines.map(String::trim).filter(String::isNotEmpty).map { WorldPressureProposal.valueOf(it) }.toList()
        }
        require(loadedLabels.isNotEmpty()) { "world_director_labels_empty" }
        val descriptor = appContext.assets.openFd(modelAsset)
        val buffer = descriptor.createInputStream().channel.use { channel ->
          channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
        }
        descriptor.close()
        val loaded = Interpreter(buffer, Interpreter.Options().apply { setNumThreads(2) })
        require(loaded.getInputTensor(0).shape().contentEquals(intArrayOf(1, featureCount))) {
          "unexpected_world_director_model_input_shape"
        }
        require(loaded.getOutputTensor(0).shape().last() == loadedLabels.size) { "world_director_label_count_mismatch" }
        interpreter = loaded
        labels = loadedLabels
        loaded to loadedLabels
      } catch (error: Exception) {
        initializationError = error.message ?: "world_director_litert_initialization_failed"
        null
      }
    }
  }

  private fun features(text: String): ByteBuffer {
    val vector = FloatArray(featureCount)
    val tokens = tokenize(text)
    tokens.forEach { addFeature(vector, "w:$it") }
    tokens.zipWithNext().forEach { (left, right) -> addFeature(vector, "b:$left|$right") }
    val compact = tokens.joinToString(" ")
    for (size in 3..5) {
      if (compact.length < size) continue
      for (index in 0..compact.length - size) addFeature(vector, "c$size:${compact.substring(index, index + size)}")
    }
    val norm = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1f)
    val buffer = ByteBuffer.allocateDirect(featureCount * 4).order(ByteOrder.nativeOrder())
    vector.forEach { buffer.putFloat(it / norm) }
    buffer.rewind()
    return buffer
  }

  private fun addFeature(vector: FloatArray, feature: String) {
    val index = (feature.hashCode() and Int.MAX_VALUE) % featureCount
    vector[index] += 1f
  }

  private fun tokenize(text: String): List<String> = text.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}_]+"), " ")
    .trim()
    .split(Regex("\\s+"))
    .filter(String::isNotBlank)

  private fun softmax(logits: FloatArray): FloatArray {
    val max = logits.maxOrNull() ?: 0f
    val values = FloatArray(logits.size) { exp((logits[it] - max).toDouble()).toFloat() }
    val total = values.sum().coerceAtLeast(1e-7f)
    return FloatArray(values.size) { values[it] / total }
  }

  override fun close() = synchronized(lock) {
    interpreter?.close()
    interpreter = null
    labels = null
  }
}
