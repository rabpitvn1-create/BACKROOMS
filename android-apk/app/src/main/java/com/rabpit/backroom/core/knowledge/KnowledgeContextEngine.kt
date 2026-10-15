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
  // Offline author-confirmed cards; a detail card never changes Game Core state.
  private const val DETAIL_ASSET = "knowledge/canon_detail_db.json"
  private const val SUPPORT_ASSET = "knowledge/canon_support_db.json"
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
  ) {
    // IDs come from the author-approved Canon index, never from a mentioned name.
    val characterIds: Set<String> = records.keys.asSequence()
      .filter { it.startsWith("CHAR.") }
      .map { it.removePrefix("CHAR.").substringBefore('.').lowercase(Locale.ROOT) }
      .filter { it.isNotBlank() }
      .toSet()
  }

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
    val detailRaw = context.assets.open(DETAIL_ASSET)
      .bufferedReader(Charsets.UTF_8).use { it.readText() }
    val supportRaw = context.assets.open(SUPPORT_ASSET)
      .bufferedReader(Charsets.UTF_8).use { it.readText() }
    return parseDatabase(raw, detailRaw, supportRaw)
  }

  internal fun parseDatabase(
    raw: String,
    detailRaw: String? = null,
    supportRaw: String? = null
  ): Database {
    val arrays = listOf(JSONObject(raw).getJSONArray("records")) +
      listOfNotNull(
        detailRaw?.let { JSONObject(it).getJSONArray("records") },
        supportRaw?.let { JSONObject(it).getJSONArray("records") }
      )
    val records = linkedMapOf<String, Record>()
    for (array in arrays) for (i in 0 until array.length()) {
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
    // Precise per-actor relationship status for the GM packet.
    // Values: "IN_PARTY" | "PRESENT_NOT_IN_PARTY".
    // "PRESENT_NOT_IN_PARTY" is the critical case: Core confirmed the NPC is in
    // the scene (first meeting / temporary companion), but the player has NOT
    // recruited them. The GM must never narrate such an NPC as a Party member.
    private val npcStatus = linkedMapOf<String, String>()
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
      addAuthoredDetails()
      addRelationships()
      addDirectStructuredLookups()
      addSceneAffordances()
      addStateDrivenRecords()
      expandReferences()

      val mandatoryCost = selected.values.filter(::mandatory).sumOf { estimateTokens(renderRecord(it)) }
      var stateSummary = compactState()
      val dialogue = JSONArray(recentDialogue())
      var compacted = false
      fun header(): String = buildString {
        append("[KNOWLEDGE_PACKET v1]\n")
        append("Budget target=").append(TARGET_CONTEXT_BUDGET)
          .append(" soft=").append(SOFT_CONTEXT_CEILING)
          .append(" hard=").append(HARD_CONTEXT_CEILING).append('\n')
        append("Present actors: ").append(presentActors.joinToString(", ")).append('\n')
        val statusSection = npcStatusSection()
        if (statusSection.isNotEmpty()) append(statusSection)
        if (compacted) append("Optional state/dialogue compacted to preserve mandatory Canon.\n")
        append("Current state:\n").append(stateSummary).append('\n')
        append(npcBehaviorRules())
        append("Recent dialogue buffer:\n").append(dialogue.toString()).append('\n')
        append("Retrieved records:\n")
      }
      if (estimateTokens(header() + "[END_KNOWLEDGE_PACKET]") + mandatoryCost > HARD_CONTEXT_CEILING) {
        stateSummary = essentialState()
        compacted = true
      }
      while (dialogue.length() > 0 &&
        estimateTokens(header() + "[END_KNOWLEDGE_PACKET]") + mandatoryCost > HARD_CONTEXT_CEILING) {
        dialogue.remove(0)
        compacted = true
      }
      val packet = StringBuilder(header())
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
      // Core-confirmed stable actor IDs select their own cards, without adding
      // one new code branch for each future character in the Canon registry.
      presentActors.filter { it != "cao_minh" }.forEach { actor ->
        val prefix = "CHAR.${actor.uppercase(Locale.ROOT)}"
        add("$prefix.RUNTIME_CORE", "present actor runtime core")
        add("$prefix.AUTHORED", "present actor authored card")
      }
      if ("luc_tram" in presentActors) {
        add("CHAR.LUC_TRAM.REVIVAL_R06", "post-revival knowledge boundary")
        add("CHAR.LUC_TRAM.OPEN_R06", "unresolved encounter history")
      }
    }


    /**
     * Contextual R2 Canon. Actor presence comes from Core, never name matching.
     * Only one authored passage per subject/topic is selected within the existing
     * knowledge packet budget. Writer secrets are excluded during index assembly.
     */
    private fun addAuthoredDetails() {
      val topics = linkedSetOf("profile")
      // Supporting authored documents are indexed offline, but resolved only on
      // grounded scene topics. Historical narrative archives remain invisible.
      val supportText = normalize(listOf(
        action, state.optString("location", ""), state.optString("title", "")
      ).joinToString(" "))
      val activeSupport = linkedSetOf<String>()
      if (hasAny(supportText, "linh khí", "linh lực", "linh mạch", "tu luyện", "kinh mạch")) {
        activeSupport += "LINH_KHI"
        topics += "environment"
        if (hasAny(supportText, "hồi phục", "tu luyện", "chiến đấu", "vận công", "kinh mạch")) {
          activeSupport += "TU_SI"
          topics += "combat"
        }
      }
      if (hasAny(supportText, "async", "kv31", "viện nghiên cứu", "project kv31")) {
        activeSupport += "ASYNC"
        topics += "environment"
      }
      if (hasAny(supportText, "nói", "hỏi", "trò chuyện", "giao tiếp", "đối thoại")) {
        activeSupport += "WRITING"
        topics += "dialogue"
      }
      if (hasAny(actionText, "nói", "hỏi", "trò chuyện", "giao tiếp", "đối thoại", "tâm sự", "talk"))
        topics += "dialogue"
      if (hasAny(actionText, "đánh", "chiến đấu", "tấn công", "kiếm", "súng", "kỹ năng", "năng lực", "combat"))
        topics += "combat"
      if (hasAny(actionText, "khám phá", "tìm kiếm", "đi ", "quan sát", "nhìn", "môi trường", "search", "explore"))
        topics += "environment"
      if (hasAny(actionText, "nhìn", "ngoại hình", "quan sát", "trông như"))
        topics += "visual"
      if ((state.optJSONArray("pendingNpcEncounters")?.length() ?: 0) > 0) {
        topics += "dialogue"
        topics += "visual"
      }
      val node = state.optJSONObject("level")?.optString("id", "").orEmpty()
      val levelPrefix = "LEVEL.%02d.DETAIL.".format(Locale.ROOT, currentLevel())
      // Lightweight lexical ranking ensures a skill/realm question reaches the
      // relevant paragraph rather than always selecting paragraph number one.
      val terms = actionText.split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length >= 4 && it !in setOf("trong", "đang", "những", "người", "chuyện") }
        .distinct()
      val picked = linkedSetOf<String>()
      val candidates = db.records.values.asSequence()
        .filter { it.kind == "authored-detail" }
        .filter { record ->
          record.tags.any { it.startsWith("detail:") && it.removePrefix("detail:") in topics }
        }
        .filter { record ->
          when {
            record.id.startsWith("CHAR.") -> {
              val actorId = record.id.removePrefix("CHAR.").substringBefore('.').lowercase(Locale.ROOT)
              actorId in presentActors
            }
            record.id.startsWith("SUPPORT.ASYNC.DETAIL.") -> "ASYNC" in activeSupport
            record.id.startsWith("SUPPORT.LINH_KHI.DETAIL.") -> "LINH_KHI" in activeSupport
            record.id.startsWith("SUPPORT.TU_SI.DETAIL.") -> "TU_SI" in activeSupport
            record.id.startsWith("SUPPORT.WRITING.DETAIL.") -> "WRITING" in activeSupport
            record.id.startsWith("WORLD.DETAIL.") -> "environment" in topics
            record.id.startsWith(levelPrefix) -> "environment" in topics
            record.id.startsWith("LEVEL.NODE.") ->
              "environment" in topics && node.isNotBlank() &&
                record.id.startsWith("LEVEL.NODE.${node}.DETAIL.")
            else -> false
          }
        }
        .sortedWith(compareBy<Record> {
          if ("detail:dialogue" in it.tags && "dialogue" in topics) 0
          else if ("detail:combat" in it.tags && "combat" in topics) 1
          else if ("detail:environment" in it.tags && "environment" in topics) 2
          else if ("detail:visual" in it.tags && "visual" in topics) 3
          else 4
        }.thenByDescending { record ->
          val text = normalize(record.text)
          terms.count { term -> text.contains(term) }
        }.thenBy { it.priority }.thenBy { it.id })
      for (record in candidates) {
        val owner = when {
          record.id.startsWith("CHAR.") -> record.id.substringBefore(".DETAIL.")
          record.id.startsWith("SUPPORT.") -> record.id.substringBefore(".DETAIL.")
          record.id.startsWith("LEVEL.NODE.") -> "LEVEL.NODE"
          record.id.startsWith("LEVEL.") -> "LEVEL"
          else -> "WORLD"
        }
        val topic = record.tags.firstOrNull { it.startsWith("detail:") } ?: continue
        val quotaKey = "${owner}:${topic}"
        if (!picked.add(quotaKey)) continue
        add(record.id, "author-confirmed contextual R2 detail")
      }
    }

    private fun addRelationships() {
      if ("iris" in presentActors && "syvial" in presentActors) add("REL.IRIS.SYVIAL.BASELINE", "present relationship edge")
      if ("luc_tram" in presentActors) add("REL.CAO_MINH.LUC_TRAM.HISTORY_R06", "relationship history, not remembered current state")
    }

    private fun addDirectStructuredLookups() {
      val direct = linkedSetOf<String>()
      val lucTramConfirmed = "luc_tram" in presentActors
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

    private fun confirmedEntityVisualId(): String? {
      val combat = state.optJSONObject("combat")
      val flags = state.optJSONObject("flags")
      // P0 Entity GM: also check pendingEntityEncounter (Core-reserved this turn,
      // passed via MainActivity from beginAction response). This is the primary
      // source for the GM narration prompt; flags.entityEncounterKey is the fallback.
      val pendingEntity = state.optJSONObject("pendingEntityEncounter")
      val key = when {
        combat?.optBoolean("active", false) == true -> combat.optString("entityKey", "")
        pendingEntity != null -> pendingEntity.optString("entityKey", "")
        else -> flags?.optString("entityEncounterKey", "").orEmpty()
      }
      return key.takeIf { it.matches(Regex("[a-z0-9_-]{1,80}")) }
        ?.uppercase(Locale.ROOT)?.replace('-', '_')?.let { "ENTITY.$it.VISUAL" }
    }

    private fun addStateDrivenRecords() {
      // Milestone system removed: Core-confirmed NPC encounters are surfaced via
      // presentActors (resolvePresence) from pendingNpcEncounters / encounteredCharacters,
      // not from a Milestone schedule.
      val flags = state.optJSONObject("flags")
      val confirmedVisual = confirmedEntityVisualId()
      if (confirmedVisual != null) {
        add(confirmedVisual, "confirmed active Entity visual canon")
      }
      val confirmedEntities = flags?.optInt("entitiesConfirmedLocal", 0) ?: 0
      if (confirmedEntities > 0 || hasAny(sceneText, "entity", "thực thể", "quái", "hound", "smiler", "skin-stealer", "jeff")) {
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
      val mandatory = selected.values.filter(::mandatory).sortedWith(compareBy<Record> { it.priority }.thenBy { it.id })
      // Preserve R1 authored quick cards, then reserve budget for the scene's
      // selected R2 details before general low-priority optional material.
      // Never override the mandatory hard locks above or increase packet ceilings.
      val optional = selected.values.filterNot(::mandatory).sortedWith(compareBy<Record> {
        when {
          it.id == "CHAR.CAO_MINH.AUTHORED" -> 31
          it.id.endsWith(".AUTHORED") -> 32
          it.kind == "authored-detail" && it.id.startsWith("SUPPORT.") -> 33
          it.kind == "authored-detail" -> 34
          else -> it.priority
        }
      }.thenBy { it.id })
      val kept = mutableListOf<Record>()
      var tokens = estimateTokens(header + "[END_KNOWLEDGE_PACKET]")
      mandatory.forEach { r -> kept += r; tokens += estimateTokens(renderRecord(r)) }
      // A broken asset or oversized state must fail explicitly, never emit a
      // half hard-lock or silently omit mandatory Canon to satisfy a budget.
      require(tokens <= HARD_CONTEXT_CEILING) { "knowledge_mandatory_context_over_budget" }
      optional.forEach { r ->
        // A Core-confirmed scene may need a short R2 voice/detail card even after
        // compulsory locks and R1 cards occupy the soft budget. R2 may use the
        // existing hard ceiling, but can never displace compulsory hard locks.
        val ceiling = when {
          r.kind == "authored-detail" -> HARD_CONTEXT_CEILING
          tokens < TARGET_CONTEXT_BUDGET -> TARGET_CONTEXT_BUDGET
          else -> SOFT_CONTEXT_CEILING
        }
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

    /** The current encounter's identity/visual card cannot be dropped as optional context. */
    private fun mandatory(record: Record): Boolean = record.priority <= 30 ||
      record.id == confirmedEntityVisualId() ||
      (record.id == "CHAR.LUCIA.AUTHORED" && "lucia" in presentActors) ||
      (record.id == "CHAR.LUC_TRAM.AUTHORED" && "luc_tram" in presentActors) ||
      (record.id == "CHAR.LUC_TRAM.RUNTIME_CORE" && "luc_tram" in presentActors) ||
      (record.id == "CHAR.LUC_TRAM.REVIVAL_R06" && "luc_tram" in presentActors) ||
      (record.id == "CHAR.LUC_TRAM.OPEN_R06" && "luc_tram" in presentActors)

    // Every retrieval path, including references and affordances, obeys scene
    // evidence. Mentioning a name cannot bypass the same boundary.
    private fun eligible(record: Record): Boolean {
      if (record.authority == "WRITER-SECRET" || record.kind == "reference-only-level") return false
      val actor = when {
        record.id.startsWith("CHAR.") ->
          record.id.removePrefix("CHAR.").substringBefore('.').lowercase(Locale.ROOT)
        record.id == "REL.CAO_MINH.LUC_TRAM.HISTORY_R06" -> "luc_tram"
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
      // Status tracking: IN_PARTY vs PRESENT_NOT_IN_PARTY for scene-present actors only.
      // A present-but-not-recruited NPC (e.g. Lucia on first meeting) MUST stay
      // PRESENT_NOT_IN_PARTY — never promote to Party membership in narration.
      fun addConfirmed(idOrName: String, status: String) {
        val canonical = when (val id = normalize(idOrName)) {
          // Existing display-name aliases remain useful; canonical Core IDs
          // are otherwise resolved directly from registered Canon records.
          "lucia lục" -> "lucia"
          "trác lâm", "trac lam" -> "trac_lam"
          "lục trầm", "luc tram" -> "luc_tram"
          else -> id
        }.takeIf { it in db.characterIds } ?: return
        presentActors += canonical
        // IN_PARTY wins over PRESENT_NOT_IN_PARTY; never downgrade a Party member.
        val existing = npcStatus[canonical]
        if (existing == "IN_PARTY") return
        if (status == "IN_PARTY" || existing == null) {
          npcStatus[canonical] = status
        }
      }
      fun activeMember(member: JSONObject): Boolean =
        member.optString("presence", "ACTIVE") == "ACTIVE"

      // The Core projection now supplies a single confirmed scene roster.
      // Never reintroduce an excluded NPC from Party/history when it exists.
      // Pending encounters remain a separate Core-owned source for this turn.
      state.optJSONArray("sceneParticipants")?.let { roster ->
        for (i in 0 until roster.length()) {
          val actor = roster.optJSONObject(i) ?: continue
          val id = actor.optString("id")
          if (id.isBlank() || id == "cao_minh") continue
          addConfirmed(id, if (actor.optBoolean("inParty")) "IN_PARTY"
            else "PRESENT_NOT_IN_PARTY")
        }
        for (source in listOf(state.optJSONArray("pendingNpcEncounters"),
          rolls.optJSONArray("pendingNpcEncounters"))) {
          if (source == null) continue
          for (i in 0 until source.length()) {
            val event = source.optJSONObject(i) ?: continue
            addConfirmed(event.optString("characterId", event.optString("flagKey", "")),
              "PRESENT_NOT_IN_PARTY")
          }
        }
        return
      }

      // Pure retrieval tests without a Core projection still accept the older
      // explicit encounter/Party facts. Production projections use the roster.
      state.optJSONArray("party")?.let { party ->
        for (i in 0 until party.length()) {
          when (val value = party.opt(i)) {
            is JSONObject -> if (activeMember(value)) {
              addConfirmed(value.optString("id", value.optString("name", "")), "IN_PARTY")
            }
            is String -> addConfirmed(value, "IN_PARTY")
          }
        }
      }
      state.optJSONObject("partyDetails")?.optJSONArray("members")?.let { details ->
        for (i in 0 until details.length()) {
          val member = details.optJSONObject(i) ?: continue
          if (activeMember(member)) {
            addConfirmed(member.optString("id", member.optString("name", "")), "IN_PARTY")
          }
        }
      }
      // Core-confirmed present actors: NPCs Core spawned this turn (e.g. first
      // meeting, not yet in Party). MainActivity merges beginAction's
      // pendingNpcEncounters into the state or rolls JSON passed here.
      // Character IDs come from Core (LUCIA_ID="lucia", LUC_TRAM_ID="luc_tram", ...).
      fun addPendingEncounters(array: JSONArray?) {
        if (array == null) return
        for (i in 0 until array.length()) {
          val event = array.optJSONObject(i) ?: continue
          addConfirmed(
            event.optString("characterId", event.optString("flagKey", "")),
            "PRESENT_NOT_IN_PARTY"
          )
        }
      }
      addPendingEncounters(state.optJSONArray("pendingNpcEncounters"))
      addPendingEncounters(rolls.optJSONArray("pendingNpcEncounters"))
      // Already-encountered characters that Core marks as currently present.
      // "Has met before" (present=false) is NOT presence — only present=true joins.
      // inParty flag from Core decides IN_PARTY vs PRESENT_NOT_IN_PARTY.
      state.optJSONArray("encounteredCharacters")?.let { encountered ->
        for (i in 0 until encountered.length()) {
          val entry = encountered.optJSONObject(i) ?: continue
          val id = entry.optString("id", "")
          if (id.isBlank()) continue
          when {
            entry.optBoolean("present", false) && entry.optBoolean("inParty", false) ->
              addConfirmed(id, "IN_PARTY")
            entry.optBoolean("present", false) ->
              addConfirmed(id, "PRESENT_NOT_IN_PARTY")
            // An absent actor never enters either presence or per-turn status.
            else -> Unit
          }
        }
      }
    }

    /**
     * Renders the precise encountered/present/inParty status for each known NPC.
     * This is the GM's authoritative signal for relationship boundaries:
     * PRESENT_NOT_IN_PARTY means the NPC is in the scene but NOT a Party member.
     */
    private fun npcStatusSection(): String {
      if (npcStatus.isEmpty()) return ""
      return buildString {
        append("NPC relationship status (Core-authoritative):\n")
        // Deterministic order for stable packets.
        npcStatus.toSortedMap().forEach { (id, status) ->
          append("- ").append(id).append(": ")
          when (status) {
            "IN_PARTY" -> append("IN_PARTY — recruited Party member")
            "PRESENT_NOT_IN_PARTY" -> append(
              "PRESENT, inParty=false — Core-confirmed in scene, NOT recruited. " +
                "Narrate as present person, NEVER as Party member"
            )
            else -> append(status)
          }
          append('\n')
        }
      }
    }

    /**
     * NPC behavior + epistemic-boundary rules for the GM.
     * Diagnostic finding: NPCs mostly warn, rarely show initiative or react naturally.
     * These rules push the GM toward living NPCs while protecting canon and Core authority.
     */
    private fun npcBehaviorRules(): String = buildString {
      append("NPC_BEHAVIOR_RULES:\n")
      append("Present NPCs may ask, disagree, refuse, move and react according to Canon, ")
      append("even when inParty=false; do not reduce them to warnings or passive followers. ")
      append("Ordinary initiative cannot decide combat, damage, loot, EXP, spawns, exits, ")
      append("Level changes or recruitment: Core alone decides those.\n")
      append("Knowledge comes from Canon background, observation, testimony and supported inference; ")
      append("keep guesses uncertain. Names and intimacy require prior Canon knowledge or an ")
      append("in-scene introduction/source; merely walking together does not establish trust or Party membership.\n")
      if (npcStatus["lucia"] == "PRESENT_NOT_IN_PARTY") {
        append("Lucia is present, NOT recruited: preserve her Canon speech and tactical initiative; ")
        append("never auto-recruit or narrate her as a Party member.\n")
      }
      if ("luc_tram" in presentActors) {
        append("Luc Tram R06 cannot remember Cao Minh or their shared past before revival. ")
        append("New introductions/knowledge in this run are allowed; do not restore old memories or intimacy. ")
        append("Cao Minh does not automatically know she survived or lost her memory.\n")
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

    private fun essentialState(): String = JSONObject()
      .put("turn", state.optInt("turn", 1))
      .put("level", state.optJSONObject("level") ?: JSONObject().put("number", currentLevel()))
      .put("location", clipped(state.optString("location"), 200))
      .put("presentActors", JSONArray(presentActors.toList()))
      .apply {
        state.optJSONObject("player")?.let { player ->
          put("player", JSONObject().apply {
            if (player.has("hp")) put("hp", player.get("hp"))
            put("condition", clipped(player.optString("condition"), 100))
          })
        }
      }.toString()

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

