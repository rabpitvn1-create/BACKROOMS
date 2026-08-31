package com.rabpit.backroom.core.knowledge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.ceil

/**
 * Local, indexed runtime knowledge store and budgeted context builder.
 *
 * Canon is packaged in assets/knowledge/knowledge_db.json. The model never scans the
 * whole database. Structured IDs, references, present actors and scene affordances are
 * resolved here before a small packet is handed to the Game Master.
 */
object KnowledgeContextEngine {
  const val TARGET_CONTEXT_BUDGET = 2200
  const val SOFT_CONTEXT_CEILING = 2800
  const val HARD_CONTEXT_CEILING = 3400

  private const val ASSET = "knowledge/knowledge_db.json"
  private const val RECENT_DIALOGUE_ENTRIES = 4
  private const val RECENT_DIALOGUE_CHARS = 700

  data class SourceRef(val document: String, val anchor: String)

  data class Record(
    val id: String,
    val domain: String,
    val kind: String,
    val text: String,
    val authority: String,
    val mutability: String,
    val priority: Int,
    val tags: Set<String>,
    val references: Set<String>,
    val affordances: Set<String>,
    val source: SourceRef
  ) {
    val estimatedTokens: Int = estimateTokens(text) + 8
  }

  data class Database(
    val records: Map<String, Record>,
    val tagIndex: Map<String, Set<String>>,
    val affordanceIndex: Map<String, Set<String>>
  )

  @Volatile private var cached: Database? = null

  @JvmStatic
  fun build(context: Context, stateJson: String, action: String, rollsJson: String): String {
    val state = runCatching { JSONObject(stateJson) }.getOrElse { JSONObject() }
    val rolls = runCatching { JSONObject(rollsJson) }.getOrElse { JSONObject() }
    return Builder(database(context.applicationContext), state, action, rolls).build()
  }

  @JvmStatic
  fun traceRecord(context: Context, id: String): String {
    val record = database(context.applicationContext).records[id] ?: return ""
    return JSONObject()
      .put("id", record.id)
      .put("sourceDocument", record.source.document)
      .put("sourceAnchor", record.source.anchor)
      .put("authority", record.authority)
      .put("mutability", record.mutability)
      .toString()
  }

  @JvmStatic
  fun recordIds(context: Context): Array<String> = database(context.applicationContext).records.keys.sorted().toTypedArray()

  private fun database(context: Context): Database {
    cached?.let { return it }
    return synchronized(this) {
      cached ?: load(context).also { cached = it }
    }
  }

  private fun load(context: Context): Database {
    val raw = context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
    val root = JSONObject(raw)
    val array = root.getJSONArray("records")
    val records = linkedMapOf<String, Record>()
    for (i in 0 until array.length()) {
      val json = array.getJSONObject(i)
      val source = json.getJSONObject("source")
      val record = Record(
        id = json.getString("id"),
        domain = json.getString("domain"),
        kind = json.getString("kind"),
        text = json.getString("text").trim(),
        authority = json.getString("authority"),
        mutability = json.getString("mutability"),
        priority = json.optInt("priority", 80),
        tags = strings(json.optJSONArray("tags")),
        references = rawStrings(json.optJSONArray("references")),
        affordances = strings(json.optJSONArray("affordances")),
        source = SourceRef(source.getString("document"), source.optString("anchor"))
      )
      require(record.id !in records) { "Duplicate knowledge record id: ${record.id}" }
      records[record.id] = record
    }
    fun index(selector: (Record) -> Set<String>): Map<String, Set<String>> {
      val out = linkedMapOf<String, MutableSet<String>>()
      records.values.forEach { record ->
        selector(record).forEach { key -> out.getOrPut(normalize(key)) { linkedSetOf() }.add(record.id) }
      }
      return out.mapValues { it.value.toSet() }
    }
    return Database(records.toMap(), index { it.tags }, index { it.affordances })
  }

  private class Builder(
    private val db: Database,
    private val state: JSONObject,
    private val action: String,
    private val rolls: JSONObject
  ) {
    private val selected = linkedMapOf<String, Record>()
    private val reasons = linkedMapOf<String, String>()
    private val presentActors = linkedSetOf("kai")
    private val actionText = normalize(action)
    private val sceneText = normalize(
      listOf(
        action,
        state.optString("location", ""),
        state.optString("title", ""),
        state.optJSONObject("flags")?.toString().orEmpty()
      ).joinToString(" ")
    )

    fun build(): String {
      resolvePresence()
      addMandatory()
      addCurrentLevel()
      addPresentRuntimeCards()
      addRelationships()
      addDirectStructuredLookups()
      addSceneAffordances()
      addStateDrivenRecords()
      expandReferences()

      val records = budgetedRecords()
      val packet = StringBuilder()
      packet.append("[KNOWLEDGE_PACKET v1]\n")
      packet.append("Budget target=").append(TARGET_CONTEXT_BUDGET)
        .append(" soft=").append(SOFT_CONTEXT_CEILING)
        .append(" hard=").append(HARD_CONTEXT_CEILING).append('\n')
      packet.append("Present actors: ").append(presentActors.joinToString(", ")).append('\n')
      packet.append("Current state:\n").append(compactState()).append('\n')
      packet.append("Recent dialogue buffer:\n").append(recentDialogue()).append('\n')
      packet.append("Retrieved records:\n")
      records.forEach { record ->
        packet.append("<").append(record.id).append("> ")
          .append(record.text.replace('\n', ' ')).append('\n')
        packet.append("  source=").append(record.source.document)
          .append("#").append(record.source.anchor)
          .append("; authority=").append(record.authority)
          .append("; mutability=").append(record.mutability)
          .append("; why=").append(reasons[record.id].orEmpty()).append('\n')
      }
      packet.append("[END_KNOWLEDGE_PACKET]")
      return hardClip(packet.toString(), HARD_CONTEXT_CEILING)
    }

    private fun addMandatory() {
      listOf(
        "GAME.TEXT.CORE",
        "GAME.GM.FAIRNESS",
        "WORLD.CORE",
        "WRITING.KNOWLEDGE_BOUNDARY",
        "WRITING.COMPETENCE",
        "WRITING.PLAYER_AGENCY",
        "CHAR.KAI.RUNTIME_CORE"
      ).forEach { add(it, "mandatory hard context") }
    }

    private fun addCurrentLevel() {
      val number = currentLevel()
      add("LEVEL.%02d".format(Locale.ROOT, number), "current level direct id")
    }

    private fun addPresentRuntimeCards() {
      if ("iris" in presentActors) add("CHAR.IRIS.RUNTIME_CORE", "present actor runtime core")
      if ("syvial" in presentActors) add("CHAR.SYVIAL.RUNTIME_CORE", "present actor runtime core")
    }

    private fun addRelationships() {
      if ("iris" in presentActors) {
        add("REL.KAI.IRIS.BASELINE", "present relationship edge")
        add("ADDR.IRIS.KAI", "present address lock")
      }
      if ("syvial" in presentActors) {
        add("REL.KAI.SYVIAL.BASELINE", "present relationship edge")
        add("ADDR.SYVIAL.KAI", "present address lock")
      }
      if ("iris" in presentActors && "syvial" in presentActors) add("REL.IRIS.SYVIAL.BASELINE", "present relationship edge")
    }

    private fun addDirectStructuredLookups() {
      val direct = linkedSetOf<String>()
      if (hasAny(actionText, "argus", "terrain read")) direct += "CHAR.IRIS.ARGUS"
      if (hasAny(actionText, "thousandfold")) direct += "CHAR.IRIS.THOUSANDFOLD"
      if (hasAny(actionText, "ivory", "ebony")) direct += "CHAR.IRIS.IVORY_EBONY"
      if (hasAny(actionText, "field mednet", "field galley")) direct += "CHAR.IRIS.SUPPORT"
      if (hasAny(actionText, "godkiller override", "twenty-four severance")) direct += "CHAR.SYVIAL.GODKILLER_OVERRIDE"
      else if (hasAny(actionText, "godkiller")) direct += "CHAR.SYVIAL.GODKILLER"
      if (hasAny(actionText, "lucifer core")) direct += "CHAR.SYVIAL.LUCIFER_CORE"
      if (hasAny(actionText, "sparda core")) direct += "CHAR.KAI.SPARDA_CORE"
      if (hasAny(actionText, "guilty crown", "override")) direct += "CHAR.KAI.GUILTY_CROWN_OVERRIDE"
      if (hasAny(actionText, "white wraith", "magnum")) direct += "CHAR.KAI.WHITE_WRAITH"
      if (hasAny(actionText, "omnivault", "nhẫn vạn tàng", "scan", "hoàn nguyên", "restore")) direct += "CHAR.KAI.OMNIVAULT"
      if (hasAny(actionText, "devil trigger")) {
        direct += "CHAR.KAI.DEVIL_TRIGGER"
        if ("syvial" in presentActors) direct += "CHAR.SYVIAL.DEVIL_TRIGGER"
      }
      if (hasAny(actionText, "nói", "hỏi", "trả lời", "trò chuyện", "nói chuyện", "dialogue", "talk", "tell")) {
        direct += "WRITING.DIALOGUE"
      }
      direct.forEach { add(it, "direct structured lookup") }

      // Registry-driven exact tags. Adding a new Entity/Item record with tags makes it
      // discoverable without adding a new prompt branch or hardcoded name here.
      db.tagIndex.entries.asSequence()
        .filter { (tag, _) -> tag.length >= 3 && actionText.contains(tag) }
        .forEach { (tag, ids) ->
          ids.forEach { id ->
            val r = db.records[id] ?: return@forEach
            if (r.domain == "ENTITY" || r.domain == "ITEM") add(id, "explicit structured tag: $tag")
          }
        }
    }

    private fun addSceneAffordances() {
      val affordances = linkedSetOf<String>()
      if (hasAny(sceneText, "dấu vết", "trace", "vết chân", "đường đi", "route", "góc chết", "vật che", "phục kích", "ambush", "địa hình", "target", "mục tiêu thật")) {
        affordances += "trace_analysis"
      }
      if (hasAny(sceneText, "đe dọa", "threat", "tấn công", "attack", "giao chiến", "combat", "entity", "hound", "smiler", "wretch", "skin-stealer", "jeff")) {
        affordances += "direct_threat"
      }
      if (hasAny(sceneText, "bị thương", "injury", "vết thương", "sơ cứu", "medical")) affordances += "field_medical"
      if (hasAny(sceneText, "thức ăn", "nấu", "food", "cooking")) affordances += "field_food"

      affordances.forEach { affordance ->
        db.affordanceIndex[affordance].orEmpty().forEach { id ->
          if (id.startsWith("CHAR.IRIS.") && "iris" !in presentActors) return@forEach
          if (id.startsWith("CHAR.SYVIAL.") && "syvial" !in presentActors) return@forEach
          add(id, "scene affordance: $affordance")
        }
      }
    }

    private fun addStateDrivenRecords() {
      val flags = state.optJSONObject("flags")
      val confirmedEntities = flags?.optInt("entitiesConfirmedLocal", 0) ?: 0
      val entityRoll = rolls.optJSONObject("entityEncounter")?.optBoolean("success", false) ?: false
      if (confirmedEntities > 0 || entityRoll || hasAny(sceneText, "entity", "thực thể", "quái", "hound", "smiler", "skin-stealer", "jeff")) {
        add("ENTITY.GLOBAL_HARD_LOCK", "entity state/scene requires entity rules")
      }
      if (hasAny(sceneText, "loot", "vật phẩm", "inventory", "almond", "liquid pain", "greek fire", "nước", "thuốc")) {
        add("ITEM.GLOBAL_HARD_LOCK", "item/resource state or action")
      }
      if (isMainCampaignSeparated(flags)) {
        add("STORY.MAIN.OBJECTIVE", "active main-campaign objective")
        add("STORY.MAIN.SEPARATION", "active separation continuity")
      }
    }

    private fun expandReferences() {
      val queue = ArrayDeque(selected.values.toList())
      val visited = selected.keys.toMutableSet()
      while (queue.isNotEmpty()) {
        val record = queue.removeFirst()
        record.references.forEach { id ->
          if (!visited.add(id)) return@forEach
          val target = db.records[id] ?: return@forEach
          if (target.priority <= 55) {
            add(id, "direct reference from ${record.id}")
            queue.add(target)
          }
        }
      }
    }

    private fun budgetedRecords(): List<Record> {
      val mandatory = selected.values.filter { it.priority <= 30 }.sortedWith(compareBy<Record> { it.priority }.thenBy { it.id })
      val optional = selected.values.filter { it.priority > 30 }.sortedWith(compareBy<Record> { it.priority }.thenBy { it.id })
      val kept = mutableListOf<Record>()
      var tokens = baseEstimatedTokens()
      mandatory.forEach { r -> kept += r; tokens += r.estimatedTokens }
      optional.forEach { r ->
        val ceiling = if (tokens < TARGET_CONTEXT_BUDGET) TARGET_CONTEXT_BUDGET else SOFT_CONTEXT_CEILING
        if (tokens + r.estimatedTokens <= ceiling) {
          kept += r
          tokens += r.estimatedTokens
        }
      }
      return kept
    }

    private fun baseEstimatedTokens(): Int = estimateTokens(compactState()) + estimateTokens(recentDialogue()) + 80

    private fun add(id: String, reason: String) {
      val record = db.records[id] ?: return
      selected.putIfAbsent(id, record)
      reasons.putIfAbsent(id, reason)
    }

    private fun resolvePresence() {
      val party = state.optJSONArray("party")
      if (party != null) {
        for (i in 0 until party.length()) {
          when (val value = party.opt(i)) {
            is JSONObject -> {
              val id = normalize(value.optString("id", value.optString("name", "")))
              if (id.contains("iris")) presentActors += "iris"
              if (id.contains("syvial")) presentActors += "syvial"
            }
            else -> {
              val id = normalize(value?.toString().orEmpty())
              if (id.contains("iris")) presentActors += "iris"
              if (id.contains("syvial")) presentActors += "syvial"
            }
          }
        }
      }
      val details = state.optJSONObject("partyDetails")?.optJSONArray("members")
      if (details != null) {
        for (i in 0 until details.length()) {
          val member = details.optJSONObject(i) ?: continue
          val id = normalize(member.optString("id", member.optString("name", "")))
          if (id.contains("iris")) presentActors += "iris"
          if (id.contains("syvial")) presentActors += "syvial"
        }
      }
    }

    private fun currentLevel(): Int {
      state.optJSONObject("level")?.let { return it.optInt("number", 0).coerceIn(0, 999) }
      state.optJSONObject("flags")?.optJSONObject("currentLevel")?.let { return it.optInt("number", 0).coerceIn(0, 999) }
      return 0
    }

    private fun compactState(): String {
      val out = JSONObject()
      out.put("turn", state.optInt("turn", 1))
      out.put("level", state.optJSONObject("level") ?: JSONObject().put("number", currentLevel()))
      if (state.has("title")) out.put("title", clipped(state.optString("title"), 180))
      if (state.has("location")) out.put("location", clipped(state.optString("location"), 700))
      state.optJSONObject("player")?.let { player ->
        val compact = JSONObject()
        listOf("hp", "condition", "weapon", "armor", "needs").forEach { key -> if (player.has(key)) compact.put(key, player.get(key)) }
        out.put("player", compact)
      }
      state.optJSONArray("inventory")?.let { inventory -> out.put("inventory", compactArray(inventory, 12)) }
      state.optJSONArray("party")?.let { party -> out.put("party", compactArray(party, 4)) }
      state.optJSONObject("partyDetails")?.optJSONArray("members")?.let { members ->
        val vitals = JSONArray()
        for (index in 0 until minOf(members.length(), 4)) {
          val member = members.optJSONObject(index) ?: continue
          vitals.put(JSONObject().apply {
            listOf("id", "name", "presence", "currentHp", "maxHp", "condition").forEach { key ->
              if (member.has(key)) put(key, member.get(key))
            }
          })
        }
        if (vitals.length() > 0) out.put("partyVitals", vitals)
      }
      val flags = state.optJSONObject("flags")
      if (flags != null) {
        val continuity = JSONObject()
        listOf(
          "communication", "exploration", "iris", "syvial", "reunionPath",
          "survivorRegistry", "entityRegistry", "survivorsConfirmed", "entitiesConfirmedLocal",
          "entityEncounterKey", "currentLevel", "storyContinuity"
        ).forEach { key -> if (flags.has(key)) continuity.put(key, flags.get(key)) }
        if (continuity.length() > 0) out.put("continuity", continuity)
      }
      return clipped(out.toString(), 5200)
    }

    private fun recentDialogue(): String {
      val log = state.optJSONArray("log") ?: return "[]"
      val out = JSONArray()
      val start = (log.length() - RECENT_DIALOGUE_ENTRIES).coerceAtLeast(0)
      for (i in start until log.length()) {
        val entry = log.optJSONObject(i) ?: continue
        out.put(JSONObject()
          .put("role", entry.optString("role", ""))
          .put("text", clipped(entry.optString("text", ""), RECENT_DIALOGUE_CHARS)))
      }
      return out.toString()
    }

    private fun isMainCampaignSeparated(flags: JSONObject?): Boolean {
      if (flags == null) return false
      val iris = normalize(flags.optJSONObject("iris")?.optString("continuity", "").orEmpty())
      val syvial = normalize(flags.optJSONObject("syvial")?.optString("continuity", "").orEmpty())
      return iris.contains("separated") || syvial.contains("separated")
    }
  }

  private fun hardClip(text: String, hardTokens: Int): String {
    val maxChars = hardTokens * 4
    if (text.length <= maxChars) return text
    val suffix = "\n[PACKET_CLIPPED_AT_HARD_CEILING]"
    return text.take((maxChars - suffix.length).coerceAtLeast(0)) + suffix
  }

  private fun compactArray(array: JSONArray, limit: Int): JSONArray {
    val out = JSONArray()
    for (i in 0 until minOf(array.length(), limit)) out.put(array.opt(i))
    return out
  }

  private fun rawStrings(array: JSONArray?): Set<String> {
    if (array == null) return emptySet()
    val out = linkedSetOf<String>()
    for (i in 0 until array.length()) {
      val value = array.optString(i, "").trim()
      if (value.isNotEmpty()) out += value
    }
    return out
  }

  private fun strings(array: JSONArray?): Set<String> {
    if (array == null) return emptySet()
    val out = linkedSetOf<String>()
    for (i in 0 until array.length()) {
      val value = array.optString(i, "").trim()
      if (value.isNotEmpty()) out += normalize(value)
    }
    return out
  }

  private fun tokenizeTags(text: String): Set<String> {
    val result = linkedSetOf<String>()
    val known = listOf(
      "hound", "clump", "duller", "deathmoth", "faceling", "false puddle", "paintings",
      "smiler", "skin-stealer", "skin stealer", "predatory window", "biological pipeline",
      "wretch", "cable mimic", "beast of level 5", "jeff the killer",
      "almond water", "greek fire", "liquid pain"
    )
    known.forEach { if (text.contains(it)) result += normalize(it) }
    return result
  }

  private fun hasAny(text: String, vararg needles: String): Boolean = needles.any { text.contains(normalize(it)) }

  private fun normalize(text: String): String = text.lowercase(Locale.ROOT)
    .replace('–', '-')
    .replace('—', '-')
    .replace(Regex("\\s+"), " ")
    .trim()

  private fun clipped(text: String, max: Int): String = if (text.length <= max) text else text.take(max) + "…"

  private fun estimateTokens(text: String): Int = ceil(text.length / 4.0).toInt().coerceAtLeast(1)
}
