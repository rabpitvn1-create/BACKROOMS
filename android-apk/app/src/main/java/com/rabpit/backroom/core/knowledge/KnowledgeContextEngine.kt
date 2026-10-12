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
    return parseDatabase(raw)
  }

  internal fun parseDatabase(raw: String): Database {
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

  internal class Builder(
    private val db: Database,
    private val state: JSONObject,
    private val action: String,
    private val rolls: JSONObject
  ) {
    private val selected = linkedMapOf<String, Record>()
    private val reasons = linkedMapOf<String, String>()
    private val presentActors = linkedSetOf("cao_minh")
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

      val packet = StringBuilder()
      packet.append("[KNOWLEDGE_PACKET v1]\n")
      packet.append("Budget target=").append(TARGET_CONTEXT_BUDGET)
        .append(" soft=").append(SOFT_CONTEXT_CEILING)
        .append(" hard=").append(HARD_CONTEXT_CEILING).append('\n')
      packet.append("Present actors: ").append(presentActors.joinToString(", ")).append('\n')
      packet.append("Current state:\n").append(compactState()).append('\n')
      packet.append("Recent dialogue buffer:\n").append(recentDialogue()).append('\n')
      packet.append("Retrieved records:\n")
      val records = budgetedRecords(packet.toString())
      records.forEach { record ->
        packet.append(renderRecord(record))
      }
      packet.append("[END_KNOWLEDGE_PACKET]")
      return packet.toString()
    }

    private fun renderRecord(record: Record): String = buildString {
      append("<").append(record.id).append("> ")
        .append(record.text.replace('\n', ' ')).append('\n')
      append("  source=").append(record.source.document)
        .append("#").append(record.source.anchor)
        .append("; provenance=").append(
          if (record.source.document.startsWith("Google Drive /Novel/Asset/") ||
            record.source.document.startsWith("https://")) "TRACEABLE"
          else "LEGACY_UNVERIFIED")
        .append("; authority=").append(record.authority)
        .append("; mutability=").append(record.mutability)
        .append("; why=").append(reasons[record.id].orEmpty()).append('\n')
    }

    private fun addMandatory() {
      listOf(
        "GAME.TEXT.CORE",
        "GAME.GM.FAIRNESS",
        "WORLD.CORE",
        "WRITING.KNOWLEDGE_BOUNDARY",
        "WRITING.COMPETENCE",
        "WRITING.PLAYER_AGENCY",
        "CHAR.CAO_MINH.RUNTIME_CORE"
      ).forEach { add(it, "mandatory hard context") }
      add("CHAR.CAO_MINH.AUTHORED", "Drive-authored identity boundary")

    }

    private fun addCurrentLevel() {
      val number = currentLevel()
      val levelId = "LEVEL.%02d".format(Locale.ROOT, number)
      add(levelId, "current level direct id")
      add("$levelId.AUTHORED", "Drive-authored level scene card")
      // A scene node is not interchangeable with its parent level. Only exact IDs
      // with authored entries resolve; absent or conflicting nodes stay UNKNOWN.
      val node = state.optJSONObject("level")?.optString("id", "").orEmpty()
        .ifBlank { state.optJSONObject("flags")?.optJSONObject("currentLevel")?.optString("id", "").orEmpty() }
        .trim()
      if (node.matches(Regex("[a-z0-9_.-]{1,48}"))) {
        add("LEVEL.NODE.$node", "exact current environment node")
      }
    }

    private fun addPresentRuntimeCards() {
      if ("iris" in presentActors) add("CHAR.IRIS.RUNTIME_CORE", "present actor runtime core")
      if ("syvial" in presentActors) add("CHAR.SYVIAL.RUNTIME_CORE", "present actor runtime core")
      if ("lucia" in presentActors) add("CHAR.LUCIA.AUTHORED", "present Lucia authored card")
      if ("trac_lam" in presentActors) add("CHAR.TRAC_LAM.AUTHORED", "present Trác Lâm authored card")
      if ("luc_tram" in presentActors) {
        add("CHAR.LUC_TRAM.AUTHORED", "present Lục Trầm authored card")
        add("CHAR.LUC_TRAM.RUNTIME_CORE", "present actor runtime core")
        add("CHAR.LUC_TRAM.REVIVAL_R06", "post-revival knowledge boundary")
        add("CHAR.LUC_TRAM.OPEN_R06", "unresolved encounter history")
      }
    }

    private fun addRelationships() {
      if ("iris" in presentActors && "syvial" in presentActors) add("REL.IRIS.SYVIAL.BASELINE", "present relationship edge")
      if ("luc_tram" in presentActors) add("REL.CAO_MINH.LUC_TRAM.HISTORY_R06", "relationship history, not remembered current state")
    }

    private fun addDirectStructuredLookups() {
      val direct = linkedSetOf<String>()
      val lucTramConfirmed = "luc_tram" in presentActors || confirmedRoll("lucTramEncounter")
      if ("lucia" in presentActors && hasAny(actionText, "lucia", "hứa thuý mai", "hứa thúy mai")) direct += "CHAR.LUCIA.AUTHORED"
      if ("trac_lam" in presentActors && hasAny(actionText, "trác lâm", "trac lam", "trac_lam")) direct += "CHAR.TRAC_LAM.AUTHORED"
      if (hasAny(actionText, "lục trầm", "luc tram", "luc_tram", "tịch quang", "thiên kiếm linh tâm")) {
        // A name being mentioned is not evidence this actor has appeared.
        if (lucTramConfirmed) {
          direct += "CHAR.LUC_TRAM.AUTHORED"
          direct += "CHAR.LUC_TRAM.RUNTIME_CORE"
          direct += "CHAR.LUC_TRAM.REVIVAL_R06"
          direct += "CHAR.LUC_TRAM.OPEN_R06"
          direct += "REL.CAO_MINH.LUC_TRAM.HISTORY_R06"
          direct += "CHAR.LUC_TRAM.POWERS_AND_VISUAL"
        }
      }
      if ("iris" in presentActors) {
        if (hasAny(actionText, "argus", "terrain read")) direct += "CHAR.IRIS.ARGUS"
        if (hasAny(actionText, "thousandfold")) direct += "CHAR.IRIS.THOUSANDFOLD"
        if (hasAny(actionText, "ivory", "ebony")) direct += "CHAR.IRIS.IVORY_EBONY"
        if (hasAny(actionText, "field mednet", "field galley")) direct += "CHAR.IRIS.SUPPORT"
      }
      if ("syvial" in presentActors) {
        if (hasAny(actionText, "godkiller override", "twenty-four severance")) direct += "CHAR.SYVIAL.GODKILLER_OVERRIDE"
        else if (hasAny(actionText, "godkiller")) direct += "CHAR.SYVIAL.GODKILLER"
        if (hasAny(actionText, "lucifer core")) direct += "CHAR.SYVIAL.LUCIFER_CORE"
      }
      if (hasAny(actionText, "devil trigger") && "syvial" in presentActors) {
        direct += "CHAR.SYVIAL.DEVIL_TRIGGER"
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
            // A player mentioning an Entity is NOT proof of an encounter.
            // Only the Core-confirmed combat/encounter key may expose its visual.
            if (r.domain == "ITEM" ||
              (r.domain == "ENTITY" && id == confirmedEntityVisualId())) {
              add(id, "explicit structured tag: $tag")
            }
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

    private fun confirmedRoll(key: String): Boolean =
      !rolls.optBoolean("meta", false) &&
        rolls.optInt("turn", -1) == state.optInt("turn", -2) &&
        rolls.optJSONObject(key)?.let {
          it.optBoolean("success", false) && it.optBoolean("eligible", false)
        } == true

    private fun confirmedEntityVisualId(): String? {
      val combat = state.optJSONObject("combat")
      val flags = state.optJSONObject("flags")
      val key = when {
        combat?.optBoolean("active", false) == true -> combat.optString("entityKey", "")
        confirmedRoll("entityEncounter") -> rolls.optString("selectedEntityKey", "")
        else -> flags?.optString("entityEncounterKey", "").orEmpty()
      }
      return key.takeIf { it.matches(Regex("[a-z0-9_-]{1,80}")) }
        ?.uppercase(Locale.ROOT)?.replace('-', '_')?.let { "ENTITY.$it.VISUAL" }
    }

    private fun addStateDrivenRecords() {
      if (confirmedRoll("lucTramEncounter")) {
        add("CHAR.LUC_TRAM.RUNTIME_CORE", "confirmed encounter roll")
        add("CHAR.LUC_TRAM.REVIVAL_R06", "post-revival amnesia firewall")
        add("CHAR.LUC_TRAM.OPEN_R06", "unresolved state firewall")
        add("REL.CAO_MINH.LUC_TRAM.HISTORY_R06", "known history, not current knowledge")
      }
      val flags = state.optJSONObject("flags")
      // A selected roll is already authoritative when the GM receives its
      // context, before startCombatState has persisted the encounter.
      if (confirmedRoll("luciaEncounter")) {
        add("CHAR.LUCIA.AUTHORED", "confirmed Lucia encounter roll")
      }
      val confirmedVisual = confirmedEntityVisualId()
      if (confirmedVisual != null) {
        add(confirmedVisual, "confirmed active Entity visual canon")
      }
      val confirmedEntities = flags?.optInt("entitiesConfirmedLocal", 0) ?: 0
      val entityRoll = confirmedRoll("entityEncounter")
      if (confirmedEntities > 0 || entityRoll || hasAny(sceneText, "entity", "thực thể", "quái", "hound", "smiler", "skin-stealer", "jeff")) {
        add("ENTITY.GLOBAL_HARD_LOCK", "entity state/scene requires entity rules")
      }
      if (hasAny(sceneText, "loot", "vật phẩm", "inventory", "almond", "liquid pain", "greek fire", "nước", "thuốc")) {
        add("ITEM.GLOBAL_HARD_LOCK", "item/resource state or action")
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
            if (id in selected) queue.add(target)
          }
        }
      }
    }

    private fun budgetedRecords(header: String): List<Record> {
      val mandatory = selected.values.filter { it.priority <= 30 }.sortedWith(compareBy<Record> { it.priority }.thenBy { it.id })
      val optional = selected.values.filter { it.priority > 30 }.sortedWith(compareBy<Record> { it.priority }.thenBy { it.id })
      val kept = mutableListOf<Record>()
      var tokens = estimateTokens(header + "[END_KNOWLEDGE_PACKET]")
      mandatory.forEach { r -> kept += r; tokens += estimateTokens(renderRecord(r)) }
      // A broken asset or oversized state must fail explicitly, never emit a
      // half hard-lock or silently omit mandatory Canon to satisfy a budget.
      require(tokens <= HARD_CONTEXT_CEILING) { "knowledge_mandatory_context_over_budget" }
      optional.forEach { r ->
        val ceiling = if (tokens < TARGET_CONTEXT_BUDGET) TARGET_CONTEXT_BUDGET else SOFT_CONTEXT_CEILING
        val cost = estimateTokens(renderRecord(r))
        if (tokens + cost <= ceiling) {
          kept += r
          tokens += cost
        }
      }
      return kept
    }

    private fun add(id: String, reason: String) {
      val record = db.records[id] ?: return
      if (!eligible(record)) return
      selected.putIfAbsent(id, record)
      reasons.putIfAbsent(id, reason)
    }

    // Every retrieval path, including references and affordances, obeys scene
    // evidence. Mentioning a name cannot bypass the same boundary.
    private fun eligible(record: Record): Boolean {
      if (record.authority == "WRITER-SECRET" || record.kind == "reference-only-level") return false
      val actor = when {
        record.id.startsWith("CHAR.IRIS.") -> "iris"
        record.id.startsWith("CHAR.SYVIAL.") -> "syvial"
        record.id.startsWith("CHAR.LUCIA.") -> "lucia"
        record.id.startsWith("CHAR.TRAC_LAM.") -> "trac_lam"
        record.id.startsWith("CHAR.LUC_TRAM.") || record.id == "REL.CAO_MINH.LUC_TRAM.HISTORY_R06" -> "luc_tram"
        else -> null
      }
      if (actor != null && actor !in presentActors) return false
      if (record.domain == "ENTITY" && record.id != "ENTITY.GLOBAL_HARD_LOCK") {
        val visual = confirmedEntityVisualId() ?: return false
        if (record.id == visual) return true
        // Legacy IDs are not uniformly derived from the entity key. Match the
        // confirmed card's exact tags instead of guessing an alias from a name.
        val tags = db.records[visual]?.tags.orEmpty()
        if (record.tags.none { it in tags }) return false
      }
      return true
    }

    private fun resolvePresence() {
      // State is the only evidence of who is actually in the scene.
      // Substring matching lets names like "iris_unknown" impersonate a Party member.
      fun addConfirmed(idOrName: String) {
        when (normalize(idOrName)) {
          "iris" -> presentActors += "iris"
          "syvial" -> presentActors += "syvial"
          "lucia", "lucia lục" -> presentActors += "lucia"
          "trac_lam", "trác lâm", "trac lam" -> presentActors += "trac_lam"
          "luc_tram", "lục trầm", "luc tram" -> presentActors += "luc_tram"
        }
      }
      fun activeMember(member: JSONObject): Boolean =
        member.optString("presence", "ACTIVE") == "ACTIVE"

      state.optJSONArray("party")?.let { party ->
        for (i in 0 until party.length()) {
          when (val value = party.opt(i)) {
            is JSONObject -> if (activeMember(value)) {
              addConfirmed(value.optString("id", value.optString("name", "")))
            }
            is String -> addConfirmed(value)
          }
        }
      }
      state.optJSONObject("partyDetails")?.optJSONArray("members")?.let { details ->
        for (i in 0 until details.length()) {
          val member = details.optJSONObject(i) ?: continue
          if (activeMember(member)) {
            addConfirmed(member.optString("id", member.optString("name", "")))
          }
        }
      }
      if (confirmedRoll("lucTramEncounter")) presentActors += "luc_tram"
      if (confirmedRoll("luciaEncounter")) presentActors += "lucia"
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

