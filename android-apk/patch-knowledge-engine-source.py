from pathlib import Path

ENGINE = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngine.kt"
text = ENGINE.read_text(encoding="utf-8")

old = '        references = strings(json.optJSONArray("references")),'
new = '        references = rawStrings(json.optJSONArray("references")),'
if old not in text:
    raise RuntimeError("Knowledge references parser anchor not found")
text = text.replace(old, new, 1)

old = '''      if (hasAny(actionText, "devil trigger")) {
        direct += "CHAR.KAI.DEVIL_TRIGGER"
        if ("syvial" in presentActors) direct += "CHAR.SYVIAL.DEVIL_TRIGGER"
      }
      direct.forEach { add(it, "direct structured lookup") }

      // Exact entity/item terms use tag indexes, not semantic retrieval.
      tokenizeTags(actionText).forEach { tag ->
        db.tagIndex[tag].orEmpty().forEach { id ->
          val r = db.records[id] ?: return@forEach
          if (r.domain == "ENTITY" || r.domain == "ITEM") add(id, "explicit structured tag: $tag")
        }
      }
'''
new = '''      if (hasAny(actionText, "devil trigger")) {
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

      // P1.2 shadow only: rebuild an independent alias index from the same reviewed
      // Entity/Item tag seeds and compare candidate sets. Never add alias candidates.
      trace?.let { sink ->
        val tagCandidates = linkedSetOf<String>()
        db.tagIndex.entries.asSequence()
          .filter { (tag, _) -> tag.length >= 3 && actionText.contains(tag) }
          .forEach { (_, ids) ->
            ids.forEach { id ->
              val r = db.records[id] ?: return@forEach
              if (r.domain == "ENTITY" || r.domain == "ITEM") tagCandidates += id
            }
          }

        val aliasIndex = linkedMapOf<String, MutableSet<String>>()
        db.records.values.asSequence()
          .filter { it.domain == "ENTITY" || it.domain == "ITEM" }
          .forEach { record ->
            record.tags.filter { it.length >= 3 }.forEach { alias ->
              aliasIndex.getOrPut(alias) { linkedSetOf() }.add(record.id)
            }
          }
        val aliasCandidates = linkedSetOf<String>()
        aliasIndex.entries.asSequence()
          .filter { (alias, _) -> actionText.contains(alias) }
          .forEach { (_, ids) -> aliasCandidates.addAll(ids) }

        (tagCandidates + aliasCandidates).toSortedSet().forEach { id ->
          val decision = when {
            id in tagCandidates && id in aliasCandidates -> "parity"
            id in aliasCandidates -> "alias_only"
            else -> "tag_only"
          }
          sink.add(KnowledgeTraceEvent(
            type = "alias_shadow",
            recordId = id,
            decision = decision,
            rule = "runtime_tags_seed"
          ))
        }
      }
'''
if old not in text:
    raise RuntimeError("Structured registry lookup anchor not found")
text = text.replace(old, new, 1)

# Deliberately do not retrieve Entity knowledge from historical entityRegistry state.
# Current Entity presence is resolved by the current encounter key only.
if 'flags?.opt("entityRegistry")' in text or 'current entity registry tag' in text:
    raise RuntimeError("Historical Entity registry retrieval must not exist")

old = '''      return iris.contains("separated") || syvial.contains("separated") ||
        (presentActors.size == 1 && state.optInt("turn", 1) <= 3)
'''
new = '''      return iris.contains("separated") || syvial.contains("separated")
'''
if old not in text:
    raise RuntimeError("Main campaign separation heuristic anchor not found")
text = text.replace(old, new, 1)

old = '''  private fun strings(array: JSONArray?): Set<String> {
    if (array == null) return emptySet()
    val out = linkedSetOf<String>()
    for (i in 0 until array.length()) {
      val value = array.optString(i, "").trim()
      if (value.isNotEmpty()) out += normalize(value)
    }
    return out
  }
'''
new = '''  private fun rawStrings(array: JSONArray?): Set<String> {
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
'''
if old not in text:
    raise RuntimeError("Knowledge strings helper anchor not found")
text = text.replace(old, new, 1)


# P1.1 shadow metadata: name the existing reference gate without changing its value or behavior.
old = '''  const val TARGET_CONTEXT_BUDGET = 2200
  const val SOFT_CONTEXT_CEILING = 2800
  const val HARD_CONTEXT_CEILING = 3400
'''
new = '''  const val TARGET_CONTEXT_BUDGET = 2200
  const val SOFT_CONTEXT_CEILING = 2800
  const val HARD_CONTEXT_CEILING = 3400
  const val LEGACY_REFERENCE_PRIORITY_GATE = 55
'''
if old not in text:
    raise RuntimeError("P1.1 legacy reference gate anchor not found")
text = text.replace(old, new, 1)

# P0 observability: add a test-only seam and passive trace to the final generated engine.
# Production build() keeps the same API and uses trace=null, so selection and packet bytes stay unchanged.
old = '''  data class SourceRef(val document: String, val anchor: String)

  data class Record(
'''
new = '''  data class SourceRef(val document: String, val anchor: String)

  data class KnowledgeTraceEvent(
    val type: String,
    val recordId: String = "",
    val reason: String = "",
    val fromId: String = "",
    val targetId: String = "",
    val decision: String = "",
    val rule: String = "",
    val shadowClass: String = "",
    val priority: Int = -1,
    val targetPriority: Int = -1,
    val tokensBefore: Int = -1,
    val recordTokens: Int = -1,
    val ceiling: Int = -1,
    val band: String = "",
    val field: String = "",
    val startChar: Int = -1,
    val endChar: Int = -1,
    val budgetEstimatedTokens: Int = -1,
    val serializedCharsBeforeClip: Int = -1,
    val serializedCharsAfterClip: Int = -1
  )

  data class TestBuildResult(
    val packet: String,
    val events: List<KnowledgeTraceEvent>
  )

  data class Record(
'''
if old not in text:
    raise RuntimeError("P0 trace data-class anchor not found")
text = text.replace(old, new, 1)

old = '''  @JvmStatic
  fun build(context: Context, stateJson: String, action: String, rollsJson: String): String {
    val state = runCatching { JSONObject(stateJson) }.getOrElse { JSONObject() }
    val rolls = runCatching { JSONObject(rollsJson) }.getOrElse { JSONObject() }
    return Builder(database(context.applicationContext), state, action, rolls).build()
  }

  @JvmStatic
  fun traceRecord(context: Context, id: String): String {
'''
new = '''  @JvmStatic
  fun build(context: Context, stateJson: String, action: String, rollsJson: String): String {
    val state = runCatching { JSONObject(stateJson) }.getOrElse { JSONObject() }
    val rolls = runCatching { JSONObject(rollsJson) }.getOrElse { JSONObject() }
    return Builder(database(context.applicationContext), state, action, rolls).build()
  }

  @JvmStatic
  fun buildForTest(dbJson: String, stateJson: String, action: String, rollsJson: String): String {
    val state = runCatching { JSONObject(stateJson) }.getOrElse { JSONObject() }
    val rolls = runCatching { JSONObject(rollsJson) }.getOrElse { JSONObject() }
    return Builder(parseDatabase(dbJson), state, action, rolls).build()
  }

  @JvmStatic
  fun buildForTestWithTrace(dbJson: String, stateJson: String, action: String, rollsJson: String): TestBuildResult {
    val state = runCatching { JSONObject(stateJson) }.getOrElse { JSONObject() }
    val rolls = runCatching { JSONObject(rollsJson) }.getOrElse { JSONObject() }
    val events = mutableListOf<KnowledgeTraceEvent>()
    val packet = Builder(parseDatabase(dbJson), state, action, rolls, events).build()
    return TestBuildResult(packet, events.toList())
  }

  @JvmStatic
  fun traceRecord(context: Context, id: String): String {
'''
if old not in text:
    raise RuntimeError("P0 test-seam anchor not found")
text = text.replace(old, new, 1)

old = '''  private fun load(context: Context): Database {
    val raw = context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
    val root = JSONObject(raw)
'''
new = '''  private fun load(context: Context): Database {
    val raw = context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
    return parseDatabase(raw)
  }

  private fun parseDatabase(raw: String): Database {
    val root = JSONObject(raw)
'''
if old not in text:
    raise RuntimeError("P0 database parser anchor not found")
text = text.replace(old, new, 1)

old = '''  private class Builder(
    private val db: Database,
    private val state: JSONObject,
    private val action: String,
    private val rolls: JSONObject
  ) {
'''
new = '''  private class Builder(
    private val db: Database,
    private val state: JSONObject,
    private val action: String,
    private val rolls: JSONObject,
    private val trace: MutableList<KnowledgeTraceEvent>? = null
  ) {
'''
if old not in text:
    raise RuntimeError("P0 Builder anchor not found")
text = text.replace(old, new, 1)

old = '''    private fun expandReferences() {
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
'''
new = '''    private fun expandReferences() {
      val queue = ArrayDeque(selected.values.toList())
      val visited = selected.keys.toMutableSet()
      while (queue.isNotEmpty()) {
        val record = queue.removeFirst()
        record.references.forEach { id ->
          if (!visited.add(id)) {
            trace?.add(KnowledgeTraceEvent(
              type = "reference_skipped", fromId = record.id, targetId = id,
              decision = "skipped", rule = "already_visited",
              shadowClass = referenceShadowClass(id)
            ))
            return@forEach
          }
          val target = db.records[id]
          if (target == null) {
            trace?.add(KnowledgeTraceEvent(
              type = "reference_skipped", fromId = record.id, targetId = id,
              decision = "skipped", rule = "missing_target",
              shadowClass = "MISSING"
            ))
            return@forEach
          }
          if (target.priority <= LEGACY_REFERENCE_PRIORITY_GATE) {
            trace?.add(KnowledgeTraceEvent(
              type = "reference_followed", fromId = record.id, targetId = id,
              decision = "followed", rule = "priority_gate_55",
              shadowClass = "LEGACY_FOLLOWED", targetPriority = target.priority
            ))
            add(id, "direct reference from ${record.id}")
            queue.add(target)
          } else {
            trace?.add(KnowledgeTraceEvent(
              type = "reference_skipped", fromId = record.id, targetId = id,
              decision = "skipped", rule = "priority_gate_55",
              shadowClass = "RELATED", targetPriority = target.priority
            ))
          }
        }
      }
    }

    private fun referenceShadowClass(id: String): String {
      val target = db.records[id] ?: return "MISSING"
      return if (target.priority <= LEGACY_REFERENCE_PRIORITY_GATE) "LEGACY_FOLLOWED" else "RELATED"
    }
'''
if old not in text:
    raise RuntimeError("P0 reference trace anchor not found")
text = text.replace(old, new, 1)

old = '''    private fun budgetedRecords(): List<Record> {
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
'''
new = '''    private fun budgetedRecords(): List<Record> {
      val mandatory = selected.values.filter { it.priority <= 30 }.sortedWith(compareBy<Record> { it.priority }.thenBy { it.id })
      val optional = selected.values.filter { it.priority > 30 }.sortedWith(compareBy<Record> { it.priority }.thenBy { it.id })
      val kept = mutableListOf<Record>()
      var tokens = baseEstimatedTokens()
      mandatory.forEach { r ->
        val before = tokens
        kept += r
        tokens += r.estimatedTokens
        trace?.add(KnowledgeTraceEvent(
          type = "budget_decision", recordId = r.id, decision = "kept",
          priority = r.priority, tokensBefore = before, recordTokens = r.estimatedTokens,
          band = "mandatory"
        ))
      }
      optional.forEach { r ->
        val before = tokens
        val ceiling = if (tokens < TARGET_CONTEXT_BUDGET) TARGET_CONTEXT_BUDGET else SOFT_CONTEXT_CEILING
        if (tokens + r.estimatedTokens <= ceiling) {
          kept += r
          tokens += r.estimatedTokens
          trace?.add(KnowledgeTraceEvent(
            type = "budget_decision", recordId = r.id, decision = "kept",
            priority = r.priority, tokensBefore = before, recordTokens = r.estimatedTokens,
            ceiling = ceiling, band = "optional"
          ))
        } else {
          trace?.add(KnowledgeTraceEvent(
            type = "budget_decision", recordId = r.id, decision = "dropped",
            priority = r.priority, tokensBefore = before, recordTokens = r.estimatedTokens,
            ceiling = ceiling, band = "optional"
          ))
        }
      }
      trace?.add(KnowledgeTraceEvent(
        type = "budget_summary", budgetEstimatedTokens = tokens
      ))
      return kept
    }
'''
if old not in text:
    raise RuntimeError("P0 budget trace anchor not found")
text = text.replace(old, new, 1)

old = '''    private fun add(id: String, reason: String) {
      val record = db.records[id] ?: return
      selected.putIfAbsent(id, record)
      reasons.putIfAbsent(id, reason)
    }
'''
new = '''    private fun add(id: String, reason: String) {
      trace?.add(KnowledgeTraceEvent(
        type = "candidate_reason_appended", recordId = id, reason = reason
      ))
      val record = db.records[id]
      if (record == null) {
        trace?.add(KnowledgeTraceEvent(
          type = "candidate_missing", recordId = id, reason = reason,
          decision = "skipped", rule = "missing_record"
        ))
        return
      }
      val isNew = id !in selected
      selected.putIfAbsent(id, record)
      reasons.putIfAbsent(id, reason)
      if (isNew) {
        trace?.add(KnowledgeTraceEvent(
          type = "candidate_added", recordId = id, reason = reason,
          priority = record.priority, recordTokens = record.estimatedTokens
        ))
      }
    }
'''
if old not in text:
    raise RuntimeError("P0 candidate trace anchor not found")
text = text.replace(old, new, 1)


old = r'''      val records = budgetedRecords()
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
'''
new = r'''      val records = budgetedRecords()
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
        val headerStart = packet.length
        packet.append("<").append(record.id).append("> ")
        val headerEnd = packet.length
        val textStart = packet.length
        packet.append(record.text.replace('\n', ' ')).append('\n')
        val textEnd = packet.length
        val metadataStart = packet.length
        packet.append("  source=").append(record.source.document)
          .append("#").append(record.source.anchor)
          .append("; authority=").append(record.authority)
          .append("; mutability=").append(record.mutability)
          .append("; why=").append(reasons[record.id].orEmpty()).append('\n')
        val metadataEnd = packet.length
        trace?.add(KnowledgeTraceEvent(
          type = "packet_span", recordId = record.id, field = "record_header",
          startChar = headerStart, endChar = headerEnd
        ))
        trace?.add(KnowledgeTraceEvent(
          type = "packet_span", recordId = record.id, field = "record_text",
          startChar = textStart, endChar = textEnd
        ))
        trace?.add(KnowledgeTraceEvent(
          type = "packet_span", recordId = record.id, field = "record_metadata",
          startChar = metadataStart, endChar = metadataEnd
        ))
      }
      packet.append("[END_KNOWLEDGE_PACKET]")
      val serialized = packet.toString()
      return hardClip(serialized, HARD_CONTEXT_CEILING, trace)
'''
if old not in text:
    raise RuntimeError("P0 packet span anchor not found")
text = text.replace(old, new, 1)

old = r'''  private fun hardClip(text: String, hardTokens: Int): String {
    val maxChars = hardTokens * 4
    if (text.length <= maxChars) return text
    val suffix = "\n[PACKET_CLIPPED_AT_HARD_CEILING]"
    return text.take((maxChars - suffix.length).coerceAtLeast(0)) + suffix
  }
'''
new = r'''  private fun hardClip(
    text: String,
    hardTokens: Int,
    trace: MutableList<KnowledgeTraceEvent>? = null
  ): String {
    val maxChars = hardTokens * 4
    if (text.length <= maxChars) {
      trace?.add(KnowledgeTraceEvent(
        type = "hard_clip", decision = "not_clipped",
        serializedCharsBeforeClip = text.length,
        serializedCharsAfterClip = text.length
      ))
      return text
    }
    val suffix = "\n[PACKET_CLIPPED_AT_HARD_CEILING]"
    val cutOffset = (maxChars - suffix.length).coerceAtLeast(0)
    val clipped = text.take(cutOffset) + suffix
    val cutSpan = trace?.lastOrNull {
      it.type == "packet_span" && cutOffset >= it.startChar && cutOffset < it.endChar
    }
    trace?.add(KnowledgeTraceEvent(
      type = "hard_clip", recordId = cutSpan?.recordId.orEmpty(),
      decision = "clipped", rule = "hard_ceiling_chars",
      field = cutSpan?.field.orEmpty(), startChar = cutOffset,
      serializedCharsBeforeClip = text.length,
      serializedCharsAfterClip = clipped.length
    ))
    return clipped
  }
'''
if old not in text:
    raise RuntimeError("P0 hard clip trace anchor not found")
text = text.replace(old, new, 1)

ENGINE.write_text(text, encoding="utf-8")

TEST = Path(__file__).resolve().parent / "app/src/test/java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngineP0Test.kt"
TEST.parent.mkdir(parents=True, exist_ok=True)
TEST.write_text(r'''package com.rabpit.backroom.core.knowledge

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class KnowledgeContextEngineP0Test {
  private data class Scenario(
    val name: String,
    val stateJson: String,
    val action: String,
    val rollsJson: String = "{}"
  )

  private val dbJson: String by lazy {
    val candidates = listOf(
      Path.of("src/main/assets/knowledge/knowledge_db.json"),
      Path.of("app/src/main/assets/knowledge/knowledge_db.json"),
      Path.of("android-apk/app/src/main/assets/knowledge/knowledge_db.json")
    )
    val path = candidates.firstOrNull { Files.isRegularFile(it) }
      ?: error("knowledge_db.json not found from ${System.getProperty("user.dir")}")
    path.toFile().readText(Charsets.UTF_8)
  }

  private val p03Scenarios: List<Scenario> by lazy {
    listOf(
      Scenario("quiet_exploration_L0", stateJson(0), "Quan sát hành lang yên tĩnh."),
      Scenario("iris_present_dialogue", stateJson(1, partyIds = arrayOf("iris")), "Tôi hỏi Iris về lối đi."),
      Scenario("syvial_present", stateJson(1, partyIds = arrayOf("syvial")), "Tiếp tục tiến về phía trước."),
      Scenario("iris_and_syvial_present", stateJson(1, partyIds = arrayOf("iris", "syvial")), "Cả nhóm dừng lại quan sát."),
      Scenario("absent_iris_argus", stateJson(1), "Iris dùng ARGUS để đọc địa hình."),
      Scenario("direct_item_almond_water", stateJson(1), "Kiểm tra almond water trong túi."),
      Scenario("dialogue_without_presence", stateJson(1), "Tôi hỏi về tình hình hiện tại."),
      Scenario("medical_affordance_with_iris", stateJson(1, partyIds = arrayOf("iris")), "Iris sơ cứu vết thương."),
      Scenario("food_affordance_without_iris", stateJson(1), "Nấu thức ăn trước khi đi tiếp."),
      Scenario(
        "runtime_entity_encounter",
        stateJson(1),
        "Tiếp tục đi.",
        JSONObject().put("entityEncounter", JSONObject().put("success", true)).toString()
      ),
      Scenario("cao_minh_uses_stable_kai_namespace", caoMinhStateJson(1), "Cao Minh kích hoạt Sparda Core."),
      Scenario("vietnamese_omnivault_lookup", caoMinhStateJson(1), "Cao Minh kiểm tra nhẫn vạn tàng.")
    )
  }

  private val p04Scenarios: List<Scenario> by lazy {
    val pressureAction = listOf(
      "nói", "ARGUS", "terrain read", "Sparda Core", "Devil Trigger",
      "White Wraith", "Guilty Crown", "Omnivault", "Godkiller", "Lucifer Core",
      "smiler", "hound", "skin-stealer", "almond water", "greek fire", "liquid pain",
      "dấu vết", "đe dọa", "tấn công", "sơ cứu", "nấu thức ăn"
    ).joinToString(" ")
    listOf(
      Scenario(
        "soft_ceiling_candidate_pressure",
        pressureStateJson(
          partyIds = arrayOf("iris"),
          playerConditionChars = 3400,
          logEntries = 1,
          logTextChars = 600
        ),
        pressureAction
      ),
      Scenario(
        "mandatory_over_soft_long_context",
        pressureStateJson(
          partyIds = emptyArray(),
          playerConditionChars = 7000,
          logEntries = 4,
          logTextChars = 1200
        ),
        "Quan sát hành lang yên tĩnh."
      ),
      Scenario(
        "hard_clip_mid_record",
        pressureStateJson(
          partyIds = arrayOf("iris", "syvial"),
          playerConditionChars = 7000,
          logEntries = 4,
          logTextChars = 1200
        ),
        "Cả nhóm dừng lại quan sát."
      )
    )
  }


  private val p05Scenarios: List<Scenario> by lazy {
    listOf(
      Scenario("separation_true", separationStateJson(true), "Tiếp tục tiến về phía trước."),
      Scenario("separation_false_early_turn", separationStateJson(false, turn = 1), "Tiếp tục tiến về phía trước."),
      Scenario("devil_trigger_without_syvial", stateJson(1), "Kích hoạt Devil Trigger."),
      Scenario("devil_trigger_with_syvial", stateJson(1, partyIds = arrayOf("syvial")), "Kích hoạt Devil Trigger."),
      Scenario("reference_followed_below_gate", stateJson(1), "Kích hoạt Guilty Crown Override."),
      Scenario("current_level_from_flags_fallback", currentLevelFlagsStateJson(5), "Quan sát khu vực."),
      Scenario("party_details_presence", partyDetailsStateJson("iris"), "Tiếp tục tiến về phía trước."),
      Scenario("case_insensitive_entity_tag", stateJson(2), "Kiểm tra SMILER ở phía trước."),
      Scenario("android_smoke_quiet", androidSmokeQuietStateJson(), "Observe the quiet hallway."),
      Scenario("android_smoke_hard_clip", androidSmokeHardClipStateJson(), "The group waits.")
    )
  }

  private val allP0Scenarios: List<Scenario> by lazy {
    p03Scenarios + p04Scenarios + p05Scenarios
  }

  @Test fun traceDoesNotChangePacketAcrossP03Corpus() {
    assertEquals("P0.3 corpus size changed unexpectedly", 12, p03Scenarios.size)
    p03Scenarios.forEach { scenario ->
      val plain = KnowledgeContextEngine.buildForTest(
        dbJson, scenario.stateJson, scenario.action, scenario.rollsJson
      )
      val traced = KnowledgeContextEngine.buildForTestWithTrace(
        dbJson, scenario.stateJson, scenario.action, scenario.rollsJson
      )
      assertEquals("${scenario.name} packet changed when trace enabled", sha256(plain), sha256(traced.packet))
      assertEquals("${scenario.name} packet bytes changed when trace enabled", plain, traced.packet)
    }
  }


  @Test fun traceDoesNotChangePacketAcrossP04PressureCorpus() {
    assertEquals("P0.4 pressure corpus size changed unexpectedly", 3, p04Scenarios.size)
    p04Scenarios.forEach { scenario ->
      val plain = KnowledgeContextEngine.buildForTest(
        dbJson, scenario.stateJson, scenario.action, scenario.rollsJson
      )
      val traced = KnowledgeContextEngine.buildForTestWithTrace(
        dbJson, scenario.stateJson, scenario.action, scenario.rollsJson
      )
      assertEquals("${scenario.name} packet changed when trace enabled", plain, traced.packet)
      assertEquals("${scenario.name} packet digest changed when trace enabled", sha256(plain), sha256(traced.packet))
    }
  }

  @Test fun softCeilingPressureKeepsAndDropsOptionalCandidatesDeterministically() {
    val result = tracedP04("soft_ceiling_candidate_pressure")
    val summary = result.events.last { it.type == "budget_summary" }
    assertTrue(
      "Pressure fixture should enter the soft-budget band: ${summary.budgetEstimatedTokens}",
      summary.budgetEstimatedTokens > KnowledgeContextEngine.TARGET_CONTEXT_BUDGET
    )
    assertTrue(
      "Budgeted records must remain at or below the soft ceiling: ${summary.budgetEstimatedTokens}",
      summary.budgetEstimatedTokens <= KnowledgeContextEngine.SOFT_CONTEXT_CEILING
    )

    val optional = result.events.filter { it.type == "budget_decision" && it.band == "optional" }
    assertTrue(
      "Expected at least one optional record kept under the soft ceiling",
      optional.any {
        it.decision == "kept" && it.ceiling == KnowledgeContextEngine.SOFT_CONTEXT_CEILING
      }
    )
    assertTrue(
      "Expected candidate pressure to drop at least one optional record",
      optional.any {
        it.decision == "dropped" && it.ceiling == KnowledgeContextEngine.SOFT_CONTEXT_CEILING
      }
    )

    val clip = result.events.last { it.type == "hard_clip" }
    assertEquals("Soft-ceiling fixture should not need hard clipping", "not_clipped", clip.decision)
  }

  @Test fun longStateAndDialogueCanPushMandatoryContextPastSoftCeilingWithoutChangingPolicy() {
    val result = tracedP04("mandatory_over_soft_long_context")
    val summary = result.events.last { it.type == "budget_summary" }
    assertTrue(
      "Mandatory context should exceed the soft budget in this characterization fixture",
      summary.budgetEstimatedTokens > KnowledgeContextEngine.SOFT_CONTEXT_CEILING
    )

    val currentLevel = result.events.firstOrNull {
      it.type == "budget_decision" && it.recordId == "LEVEL.01"
    }
    assertNotNull("Current Level candidate must still reach budgeting", currentLevel)
    assertEquals("dropped", currentLevel!!.decision)
    assertEquals(KnowledgeContextEngine.SOFT_CONTEXT_CEILING, currentLevel.ceiling)

    val clip = result.events.last { it.type == "hard_clip" }
    assertEquals(
      "This fixture isolates mandatory-over-soft behavior before hard clipping",
      "not_clipped",
      clip.decision
    )
  }

  @Test fun hardClipFixtureCutsInsideASerializedRecordAndReportsTheBoundary() {
    val result = tracedP04("hard_clip_mid_record")
    val clip = result.events.last { it.type == "hard_clip" }

    assertEquals("Fixture must exercise the legacy hardClip path", "clipped", clip.decision)
    assertTrue(
      "Serialized packet must exceed the character hard ceiling before clipping",
      clip.serializedCharsBeforeClip > KnowledgeContextEngine.HARD_CONTEXT_CEILING * 4
    )
    assertTrue(
      "Clipped packet must respect the character hard ceiling",
      clip.serializedCharsAfterClip <= KnowledgeContextEngine.HARD_CONTEXT_CEILING * 4
    )
    assertEquals(result.packet.length, clip.serializedCharsAfterClip)
    assertTrue("Hard clip must report a record id when it cuts inside a record", clip.recordId.isNotBlank())
    assertTrue(
      "Hard clip must identify record header/text/metadata, got ${clip.field}",
      clip.field in setOf("record_header", "record_text", "record_metadata")
    )
    assertTrue("Hard clip must report a concrete cut offset", clip.startChar >= 0)
    assertTrue(result.packet.endsWith("[PACKET_CLIPPED_AT_HARD_CEILING]"))
  }


  @Test fun traceDoesNotChangePacketAcrossCompleteP0Corpus() {
    assertEquals("P0 corpus size changed unexpectedly", 25, allP0Scenarios.size)
    assertEquals("P0 scenario names must stay unique", 25, allP0Scenarios.map { it.name }.toSet().size)
    allP0Scenarios.forEach { scenario ->
      val plain = KnowledgeContextEngine.buildForTest(
        dbJson, scenario.stateJson, scenario.action, scenario.rollsJson
      )
      val traced = KnowledgeContextEngine.buildForTestWithTrace(
        dbJson, scenario.stateJson, scenario.action, scenario.rollsJson
      )
      assertEquals("${scenario.name} packet changed when trace enabled", plain, traced.packet)
    }
  }

  @Test fun separationAndEarlyTurnHeuristicMatchFinalGeneratedRuntime() {
    val separated = tracedP05("separation_true")
    assertTrue(proposed(separated, "STORY.MAIN.OBJECTIVE", "active main-campaign objective"))
    assertTrue(proposed(separated, "STORY.MAIN.SEPARATION", "active separation continuity"))

    val early = tracedP05("separation_false_early_turn")
    assertFalse(
      "Final runtime must not reintroduce the removed first-three-turn separation heuristic",
      proposed(early, "STORY.MAIN.SEPARATION")
    )
  }

  @Test fun devilTriggerPresenceGateAndLegacyReferencesStayObservable() {
    val solo = tracedP05("devil_trigger_without_syvial")
    assertTrue(proposed(solo, "CHAR.KAI.DEVIL_TRIGGER", "direct structured lookup"))
    assertFalse(proposed(solo, "CHAR.SYVIAL.DEVIL_TRIGGER"))

    val withSyvial = tracedP05("devil_trigger_with_syvial")
    assertTrue(proposed(withSyvial, "CHAR.KAI.DEVIL_TRIGGER", "direct structured lookup"))
    assertTrue(proposed(withSyvial, "CHAR.SYVIAL.DEVIL_TRIGGER", "direct structured lookup"))

    val references = tracedP05("reference_followed_below_gate")
    assertTrue(
      references.events.any {
        it.type == "reference_followed" &&
          it.fromId == "CHAR.KAI.GUILTY_CROWN_OVERRIDE" &&
          it.targetId == "CHAR.KAI.DEVIL_TRIGGER" &&
          it.targetPriority == 48
      }
    )
    assertTrue(
      references.events.any {
        it.type == "reference_followed" &&
          it.fromId == "CHAR.KAI.GUILTY_CROWN_OVERRIDE" &&
          it.targetId == "CHAR.KAI.WHITE_WRAITH" &&
          it.targetPriority == 50
      }
    )
  }

  @Test fun finalRuntimeReadsLevelFallbackPartyDetailsAndNormalizedTags() {
    val levelFallback = tracedP05("current_level_from_flags_fallback")
    assertTrue(proposed(levelFallback, "LEVEL.05", "current level direct id"))

    val partyDetails = tracedP05("party_details_presence")
    assertTrue(proposed(partyDetails, "CHAR.IRIS.RUNTIME_CORE", "present actor runtime core"))
    assertTrue(proposed(partyDetails, "REL.KAI.IRIS.BASELINE", "present relationship edge"))

    val upperTag = tracedP05("case_insensitive_entity_tag")
    assertTrue(proposed(upperTag, "ENTITY.SMILER", "explicit structured tag: smiler"))
  }

  @Test fun writesDeterministicP0SnapshotReportAndFullPackets() {
    val reportRoot = Path.of("build", "reports", "canon-p0").toFile()
    val packetDir = reportRoot.resolve("packets")
    assertTrue("Could not create Canon P0 report directory", packetDir.mkdirs() || packetDir.isDirectory)

    val scenariosJson = JSONArray()
    allP0Scenarios.forEach { scenario ->
      val result = KnowledgeContextEngine.buildForTestWithTrace(
        dbJson, scenario.stateJson, scenario.action, scenario.rollsJson
      )
      val keptIds = result.events
        .filter { it.type == "budget_decision" && it.decision == "kept" }
        .map { it.recordId }
      val budget = result.events.last { it.type == "budget_summary" }
      val clip = result.events.last { it.type == "hard_clip" }
      val packetFile = packetDir.resolve("${scenario.name}.txt")
      packetFile.writeText(result.packet, Charsets.UTF_8)

      scenariosJson.put(
        JSONObject()
          .put("name", scenario.name)
          .put("packetSha256", sha256(result.packet))
          .put("budgetKeptRecordIds", JSONArray(keptIds))
          .put("budgetEstimatedTokens", budget.budgetEstimatedTokens)
          .put("serializedCharsBeforeClip", clip.serializedCharsBeforeClip)
          .put("serializedCharsAfterClip", clip.serializedCharsAfterClip)
          .put("hardClipDecision", clip.decision)
          .put("hardClipRecordId", clip.recordId)
          .put("hardClipField", clip.field)
          .put("hardClipCutOffset", clip.startChar)
          .put("packetFile", "packets/${scenario.name}.txt")
      )
    }

    val report = JSONObject()
      .put("schemaVersion", 1)
      .put("scenarioCount", allP0Scenarios.size)
      .put("targetBudget", KnowledgeContextEngine.TARGET_CONTEXT_BUDGET)
      .put("softCeiling", KnowledgeContextEngine.SOFT_CONTEXT_CEILING)
      .put("hardCeiling", KnowledgeContextEngine.HARD_CONTEXT_CEILING)
      .put("scenarios", scenariosJson)

    val reportFile = reportRoot.resolve("snapshot-report.json")
    reportFile.writeText(report.toString(2) + "\n", Charsets.UTF_8)
    assertTrue("Canon P0 snapshot report must exist", reportFile.isFile)
    assertEquals(25, report.getInt("scenarioCount"))
    assertTrue("Snapshot report must contain smoke baseline", reportFile.readText().contains("android_smoke_quiet"))
  }

  @Test fun presentActorsAddOnlyTheirRuntimeCardsAndRelationshipEdges() {
    val iris = traced("iris_present_dialogue")
    assertPacketHas(iris.packet, "CHAR.IRIS.RUNTIME_CORE")
    assertPacketHas(iris.packet, "REL.KAI.IRIS.BASELINE")
    assertPacketHas(iris.packet, "ADDR.IRIS.KAI")
    assertPacketLacks(iris.packet, "CHAR.SYVIAL.RUNTIME_CORE")

    val syvial = traced("syvial_present")
    assertPacketHas(syvial.packet, "CHAR.SYVIAL.RUNTIME_CORE")
    assertPacketHas(syvial.packet, "REL.KAI.SYVIAL.BASELINE")
    assertPacketHas(syvial.packet, "ADDR.SYVIAL.KAI")
    assertPacketLacks(syvial.packet, "CHAR.IRIS.RUNTIME_CORE")

    val both = traced("iris_and_syvial_present")
    assertPacketHas(both.packet, "CHAR.IRIS.RUNTIME_CORE")
    assertPacketHas(both.packet, "CHAR.SYVIAL.RUNTIME_CORE")
    assertPacketHas(both.packet, "REL.IRIS.SYVIAL.BASELINE")
  }

  @Test fun absentCharacterDirectLookupDoesNotInventPresence() {
    val result = traced("absent_iris_argus")
    assertTrue(
      "ARGUS should still be proposed by direct lookup when Iris is absent",
      proposed(result, "CHAR.IRIS.ARGUS", "direct structured lookup")
    )
    assertFalse(
      "Absent Iris must not gain runtime core",
      proposed(result, "CHAR.IRIS.RUNTIME_CORE")
    )
    assertFalse(
      "Absent Iris must not gain baseline relationship edge",
      proposed(result, "REL.KAI.IRIS.BASELINE")
    )
    assertFalse(
      "Absent Iris must not gain address lock",
      proposed(result, "ADDR.IRIS.KAI")
    )
  }

  @Test fun relationshipAddressAndDialogueRemainObservableAsSeparateCauses() {
    val result = traced("iris_present_dialogue")
    assertTrue(proposed(result, "REL.KAI.IRIS.BASELINE", "present relationship edge"))
    assertTrue(proposed(result, "ADDR.IRIS.KAI", "present address lock"))

    val dialogueReasons = reasons(result, "WRITING.DIALOGUE")
    assertTrue(
      "Dialogue should be proposed directly from speech intent: $dialogueReasons",
      dialogueReasons.any { it == "direct structured lookup" }
    )
    val alreadySelectedReference = result.events.firstOrNull {
      it.type == "reference_skipped" &&
        it.fromId == "ADDR.IRIS.KAI" &&
        it.targetId == "WRITING.DIALOGUE" &&
        it.rule == "already_visited"
    }
    assertNotNull(
      "Address -> dialogue reference should remain observable even when dialogue was already selected directly",
      alreadySelectedReference
    )
  }

  @Test fun registryDrivenItemLookupAndItemHardLockAreBothVisible() {
    val result = traced("direct_item_almond_water")
    assertTrue(proposed(result, "ITEM.ALMOND_WATER", "explicit structured tag: almond water"))
    assertTrue(proposed(result, "ITEM.GLOBAL_HARD_LOCK", "item/resource state or action"))
  }

  @Test fun sceneAffordanceRespectsActorPresence() {
    val medical = traced("medical_affordance_with_iris")
    assertTrue(
      "Present Iris should expose Field MedNet support from medical affordance",
      proposed(medical, "CHAR.IRIS.SUPPORT", "scene affordance: field_medical")
    )

    val foodWithoutIris = traced("food_affordance_without_iris")
    assertFalse(
      "Absent Iris support must be gated even when field_food fires",
      proposed(foodWithoutIris, "CHAR.IRIS.SUPPORT")
    )
  }

  @Test fun runtimeEncounterRollAddsEntityRulesWithoutEntityNameInAction() {
    val result = traced("runtime_entity_encounter")
    assertTrue(
      proposed(result, "ENTITY.GLOBAL_HARD_LOCK", "entity state/scene requires entity rules")
    )
    assertFalse("Fixture must not name an Entity", scenario("runtime_entity_encounter").action.contains("entity", ignoreCase = true))
  }

  @Test fun caoMinhRuntimeIdentityStillProjectsStableKaiKnowledgeIds() {
    val sparda = traced("cao_minh_uses_stable_kai_namespace")
    assertTrue(
      "Cao Minh action should project stable CHAR.KAI namespace for Sparda Core",
      proposed(sparda, "CHAR.KAI.SPARDA_CORE", "direct structured lookup")
    )

    val omnivault = traced("vietnamese_omnivault_lookup")
    assertTrue(
      "Vietnamese Omnivault phrase should project stable CHAR.KAI namespace",
      proposed(omnivault, "CHAR.KAI.OMNIVAULT", "direct structured lookup")
    )
  }

  @Test fun tracePreservesMultipleCandidateReasons() {
    val result = KnowledgeContextEngine.buildForTestWithTrace(
      dbJson, stateJson(2), "Kiểm tra dấu hiệu của smiler.", "{}"
    )
    val candidateReasons = reasons(result, "ENTITY.SMILER")
    assertTrue("Expected multiple reasons for ENTITY.SMILER, got $candidateReasons", candidateReasons.size >= 2)
  }

  @Test fun traceRecordsReferenceSkippedAboveLegacyPriorityGate() {
    val result = KnowledgeContextEngine.buildForTestWithTrace(
      dbJson, stateJson(1), "Kiểm tra lối đi phía trước.", "{}"
    )
    val skipped = result.events.firstOrNull {
      it.type == "reference_skipped" &&
        it.fromId == "LEVEL.01" &&
        it.targetId == "ENTITY.HOUND"
    }
    assertNotNull("Expected LEVEL.01 -> ENTITY.HOUND trace event", skipped)
    assertEquals("priority_gate_55", skipped!!.rule)
    assertEquals("skipped", skipped.decision)
    assertEquals(58, skipped.targetPriority)
  }

  @Test fun traceReportsSerializationAndHardClipMetricsWithoutChangingPacket() {
    val result = KnowledgeContextEngine.buildForTestWithTrace(
      dbJson, stateJson(2), "Kiểm tra dấu vết và mối đe dọa smiler.", "{}"
    )
    val budget = result.events.lastOrNull { it.type == "budget_summary" }
    assertNotNull("Expected budget summary", budget)
    assertTrue("Expected positive budget token estimate", budget!!.budgetEstimatedTokens > 0)

    val spans = result.events.filter { it.type == "packet_span" }
    assertTrue("Expected serialized record spans", spans.isNotEmpty())
    assertTrue(spans.all { it.startChar >= 0 && it.endChar > it.startChar })

    val clip = result.events.lastOrNull { it.type == "hard_clip" }
    assertNotNull("Expected hard-clip observation event", clip)
    assertTrue(clip!!.serializedCharsBeforeClip >= clip.serializedCharsAfterClip)
    assertEquals(result.packet.length, clip.serializedCharsAfterClip)
    if (clip.decision == "clipped") {
      assertTrue("Clipped packet must expose cut offset", clip.startChar >= 0)
    }
  }

  private fun scenario(name: String): Scenario =
    p03Scenarios.first { it.name == name }

  private fun scenarioP04(name: String): Scenario =
    p04Scenarios.first { it.name == name }

  private fun scenarioP05(name: String): Scenario =
    p05Scenarios.first { it.name == name }

  private fun tracedP05(name: String): KnowledgeContextEngine.TestBuildResult {
    val scenario = scenarioP05(name)
    return KnowledgeContextEngine.buildForTestWithTrace(
      dbJson, scenario.stateJson, scenario.action, scenario.rollsJson
    )
  }

  private fun tracedP04(name: String): KnowledgeContextEngine.TestBuildResult {
    val scenario = scenarioP04(name)
    return KnowledgeContextEngine.buildForTestWithTrace(
      dbJson, scenario.stateJson, scenario.action, scenario.rollsJson
    )
  }

  private fun traced(name: String): KnowledgeContextEngine.TestBuildResult {
    val scenario = scenario(name)
    return KnowledgeContextEngine.buildForTestWithTrace(
      dbJson, scenario.stateJson, scenario.action, scenario.rollsJson
    )
  }

  private fun proposed(
    result: KnowledgeContextEngine.TestBuildResult,
    id: String,
    reason: String? = null
  ): Boolean = result.events.any {
    it.type == "candidate_reason_appended" &&
      it.recordId == id &&
      (reason == null || it.reason == reason)
  }

  private fun reasons(
    result: KnowledgeContextEngine.TestBuildResult,
    id: String
  ): Set<String> = result.events
    .filter { it.type == "candidate_reason_appended" && it.recordId == id }
    .map { it.reason }
    .toSet()

  private fun assertPacketHas(packet: String, id: String) =
    assertTrue("Expected packet to contain $id", packet.contains("<$id>"))

  private fun assertPacketLacks(packet: String, id: String) =
    assertFalse("Expected packet to omit $id", packet.contains("<$id>"))


  @Test fun aliasShadowMatchesCurrentEntityItemTagLookupAcrossReviewedTagCorpus() {
    val root = JSONObject(dbJson)
    val records = root.getJSONArray("records")
    val runtimeTags = linkedSetOf<String>()
    var accentedSeedCount = 0
    for (i in 0 until records.length()) {
      val record = records.getJSONObject(i)
      val domain = record.getString("domain")
      if (domain != "ENTITY" && domain != "ITEM") continue
      val tags = record.optJSONArray("tags") ?: JSONArray()
      for (j in 0 until tags.length()) {
        val tag = tags.getString(j).trim().lowercase()
        if (tag.length < 3) continue
        runtimeTags += tag
        if (tag.any { it.code > 127 }) accentedSeedCount++
      }
    }
    assertTrue("Expected Entity/Item runtime tag seeds", runtimeTags.isNotEmpty())

    val cases = mutableListOf<JSONObject>()
    var parityCases = 0
    var tagOnlyCases = 0
    var aliasOnlyCases = 0

    runtimeTags.sorted().forEach { tag ->
      listOf(tag, tag.uppercase()).forEach { action ->
        val state = stateJson(1)
        val plain = KnowledgeContextEngine.buildForTest(dbJson, state, action, "{}")
        val traced = KnowledgeContextEngine.buildForTestWithTrace(dbJson, state, action, "{}")
        assertEquals("Alias shadow changed packet for action=$action", plain, traced.packet)

        val aliasEvents = traced.events.filter { it.type == "alias_shadow" }
        val tagHits = traced.events
          .filter {
            it.type == "candidate_reason_appended" &&
              it.reason.startsWith("explicit structured tag:")
          }
          .map { it.recordId }
          .toSortedSet()
        val aliasHits = aliasEvents
          .filter { it.decision == "parity" || it.decision == "alias_only" }
          .map { it.recordId }
          .toSortedSet()

        val tagOnly = tagHits - aliasHits
        val aliasOnly = aliasHits - tagHits
        if (tagOnly.isEmpty() && aliasOnly.isEmpty()) parityCases++ else {
          if (tagOnly.isNotEmpty()) tagOnlyCases++
          if (aliasOnly.isNotEmpty()) aliasOnlyCases++
        }

        cases += JSONObject()
          .put("seedTag", tag)
          .put("action", action)
          .put("tagHits", JSONArray(tagHits.toList()))
          .put("aliasHits", JSONArray(aliasHits.toList()))
          .put("tagOnly", JSONArray(tagOnly.toList()))
          .put("aliasOnly", JSONArray(aliasOnly.toList()))
      }
    }

    assertEquals("Current tag-seeded alias shadow must not miss runtime tag hits", 0, tagOnlyCases)
    assertEquals("Current tag-seeded alias shadow must not add candidates yet", 0, aliasOnlyCases)
    assertEquals(cases.size, parityCases)

    val report = JSONObject()
      .put("schemaVersion", 1)
      .put("mode", "shadow_only")
      .put("scope", "ENTITY_ITEM_RUNTIME_TAG_LOOKUP")
      .put("seedSource", "runtime_tags_only")
      .put("seedTagCount", runtimeTags.size)
      .put("accentedSeedCount", accentedSeedCount)
      .put("caseCount", cases.size)
      .put("parityCases", parityCases)
      .put("tagOnlyCases", tagOnlyCases)
      .put("aliasOnlyCases", aliasOnlyCases)
      .put("cases", JSONArray(cases))

    val reportDir = Path.of("build", "reports", "canon-p1").toFile()
    assertTrue("Could not create Canon P1 report directory", reportDir.mkdirs() || reportDir.isDirectory)
    val reportFile = reportDir.resolve("alias-shadow.json")
    reportFile.writeText(report.toString(2) + "\n", Charsets.UTF_8)
    assertTrue(reportFile.isFile)
  }


  @Test fun derivedBudgetTierShadowPreservesCompleteP0PacketCorpus() {
    val rawRecords = JSONObject(dbJson).getJSONArray("records")
    val priorities = linkedMapOf<String, Int>()
    val incoming = linkedMapOf<String, MutableList<String>>()
    for (i in 0 until rawRecords.length()) {
      val record = rawRecords.getJSONObject(i)
      val id = record.getString("id")
      assertFalse("Budget tier must be derived, not authored: " + id,
        record.has("tier") || record.has("budgetTier") || record.has("derivedBudgetTier"))
      priorities[id] = record.optInt("priority", 80)
      val refs = record.optJSONArray("references") ?: JSONArray()
      for (j in 0 until refs.length()) {
        incoming.getOrPut(refs.getString(j)) { mutableListOf() }.add(id)
      }
    }
    assertEquals("Registry IDs must be unique", rawRecords.length(), priorities.size)
    assertEquals("P0 corpus must stay fixed", 25, allP0Scenarios.size)

    val cases = JSONArray()
    allP0Scenarios.forEach { scenario ->
      val plain = KnowledgeContextEngine.buildForTest(
        dbJson, scenario.stateJson, scenario.action, scenario.rollsJson)
      val traced = KnowledgeContextEngine.buildForTestWithTrace(
        dbJson, scenario.stateJson, scenario.action, scenario.rollsJson)
      assertEquals("Shadow changed packet bytes: " + scenario.name, plain, traced.packet)

      val selectedIds = traced.events.filter { it.type == "candidate_added" }.map { it.recordId }
      val budgetEvents = traced.events.filter { it.type == "budget_decision" }
      val budgetById = budgetEvents.associateBy { it.recordId }
      assertEquals("Selected records must each have a budget decision: " + scenario.name,
        selectedIds.toSet(), budgetById.keys)
      assertEquals("Duplicate selection event: " + scenario.name, selectedIds.size, selectedIds.toSet().size)
      val keptIds = budgetEvents.filter { it.decision == "kept" }.map { it.recordId }
      val referenceEvents = traced.events
        .filter { it.type == "reference_followed" || it.type == "reference_skipped" }
        .groupBy { it.targetId }

      val rows = JSONArray()
      for ((id, priority) in priorities) {
        val derived = if (priority <= 30) "MANDATORY" else "OPTIONAL"
        val budgetEvent = budgetById[id]
        if (budgetEvent != null) {
          assertEquals("Derived tier disagrees with runtime budget band: " + id,
            derived.lowercase(), budgetEvent.band)
          if (derived == "MANDATORY") {
            assertEquals("Legacy mandatory record must be kept: " + id, "kept", budgetEvent.decision)
          }
        }
        val referencedFrom = incoming[id].orEmpty()
        val eligibility = when {
          referencedFrom.isEmpty() -> "NOT_REFERENCE_TARGET"
          priority <= KnowledgeContextEngine.LEGACY_REFERENCE_PRIORITY_GATE -> "ELIGIBLE"
          else -> "INELIGIBLE"
        }
        val referenceObservations = JSONArray()
        referenceEvents[id].orEmpty().forEach { event ->
          referenceObservations.put(JSONObject()
            .put("fromId", event.fromId)
            .put("decision", event.decision)
            .put("rule", event.rule)
            .put("shadowClass", event.shadowClass))
        }
        rows.put(JSONObject()
          .put("id", id)
          .put("priority", priority)
          .put("derivedBudgetTier", derived)
          .put("selected", id in budgetById)
          .put("budgetDecision", budgetEvent?.decision ?: "not_selected")
          .put("budgetBand", budgetEvent?.band ?: JSONObject.NULL)
          .put("budgetCeiling", budgetEvent?.ceiling?.takeIf { it >= 0 } ?: JSONObject.NULL)
          .put("referenceEligibility", eligibility)
          .put("referencedFrom", JSONArray(referencedFrom))
          .put("referenceObservations", referenceObservations))
      }
      val clip = traced.events.last { it.type == "hard_clip" }
      cases.put(JSONObject()
        .put("name", scenario.name)
        .put("recordCount", rows.length())
        .put("selectedIds", JSONArray(selectedIds))
        .put("budgetKeptRecordIds", JSONArray(keptIds))
        .put("packetSha256", sha256(plain))
        .put("shadowPacketSha256", sha256(traced.packet))
        .put("budgetEstimatedTokens",
          traced.events.last { it.type == "budget_summary" }.budgetEstimatedTokens)
        .put("serializedCharsBeforeClip", clip.serializedCharsBeforeClip)
        .put("serializedCharsAfterClip", clip.serializedCharsAfterClip)
        .put("hardClipDecision", clip.decision)
        .put("hardClipRecordId", clip.recordId)
        .put("hardClipCutOffset", clip.startChar)
        .put("records", rows))
    }

    val report = JSONObject()
      .put("schemaVersion", 1)
      .put("mode", "shadow_only")
      .put("derivedFrom", "final_generated_KnowledgeContextEngine")
      .put("mandatoryBudgetPriorityMax", 30)
      .put("legacyReferencePriorityGate", KnowledgeContextEngine.LEGACY_REFERENCE_PRIORITY_GATE)
      .put("recordCount", rawRecords.length())
      .put("scenarioCount", cases.length())
      .put("scenarios", cases)
    val reportDir = Path.of("build", "reports", "canon-p1").toFile()
    assertTrue(reportDir.mkdirs() || reportDir.isDirectory)
    val reportFile = reportDir.resolve("tier-shadow.json")
    reportFile.writeText(report.toString(2) + "\n", Charsets.UTF_8)
    assertTrue("Canon P1 tier shadow report must exist", reportFile.isFile)
  }

  private fun stateJson(level: Int, partyIds: Array<String> = emptyArray()): String {
    val party = JSONArray()
    partyIds.forEach { party.put(it) }
    return JSONObject()
      .put("turn", 10)
      .put("level", JSONObject().put("number", level))
      .put("party", party)
      .put("flags", JSONObject())
      .toString()
  }

  private fun caoMinhStateJson(level: Int): String = JSONObject(stateJson(level))
    .put("player", JSONObject().put("id", "cao_minh").put("hp", 100))
    .toString()

  private fun pressureStateJson(
    partyIds: Array<String>,
    playerConditionChars: Int,
    logEntries: Int,
    logTextChars: Int
  ): String {
    val state = JSONObject(stateJson(1, partyIds))
    state.put(
      "player",
      JSONObject()
        .put("id", "cao_minh")
        .put("hp", 100)
        .put("condition", "C".repeat(playerConditionChars))
    )
    val log = JSONArray()
    repeat(logEntries) { index ->
      log.put(
        JSONObject()
          .put("role", if (index % 2 == 0) "player" else "gm")
          .put("text", "${index}:" + "D".repeat(logTextChars))
      )
    }
    state.put("log", log)
    return state.toString()
  }


  private fun separationStateJson(separated: Boolean, turn: Int = 10): String {
    val state = JSONObject(stateJson(1))
    state.put("turn", turn)
    val flags = JSONObject()
    if (separated) flags.put("iris", JSONObject().put("continuity", "separated"))
    state.put("flags", flags)
    return state.toString()
  }

  private fun currentLevelFlagsStateJson(level: Int): String = JSONObject()
    .put("turn", 10)
    .put("party", JSONArray())
    .put("flags", JSONObject().put("currentLevel", JSONObject().put("number", level)))
    .toString()

  private fun partyDetailsStateJson(id: String): String = JSONObject(stateJson(1))
    .put(
      "partyDetails",
      JSONObject().put(
        "members",
        JSONArray().put(JSONObject().put("id", id).put("name", id))
      )
    )
    .toString()

  private fun androidSmokeQuietStateJson(): String =
    """{"turn":10,"level":{"number":0},"party":[],"flags":{}}"""

  private fun androidSmokeHardClipStateJson(): String {
    val condition = "C".repeat(7000)
    val log = (0 until 4).joinToString(",") { index ->
      val role = if (index % 2 == 0) "player" else "gm"
      """{"role":"${role}","text":"${index}:${"D".repeat(1200)}"}"""
    }
    return """{"turn":10,"level":{"number":1},"party":["iris","syvial"],"flags":{},"player":{"id":"cao_minh","hp":100,"condition":"${condition}"},"log":[${log}]}"""
  }

  private fun sha256(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }
}
''', encoding="utf-8")



P1_TEST = Path(__file__).resolve().parent / "app/src/test/java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngineP1ShadowTest.kt"
P1_TEST.parent.mkdir(parents=True, exist_ok=True)
P1_TEST.write_text(r'''package com.rabpit.backroom.core.knowledge

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class KnowledgeContextEngineP1ShadowTest {
  private val dbJson: String by lazy {
    val candidates = listOf(
      Path.of("src/main/assets/knowledge/knowledge_db.json"),
      Path.of("app/src/main/assets/knowledge/knowledge_db.json"),
      Path.of("android-apk/app/src/main/assets/knowledge/knowledge_db.json")
    )
    val path = candidates.firstOrNull { Files.isRegularFile(it) }
      ?: error("knowledge_db.json not found from ${System.getProperty("user.dir")}")
    path.toFile().readText(Charsets.UTF_8)
  }

  @Test fun shadowClassificationDoesNotChangePacketAndLabelsBothLegacyClasses() {
    val legacyState = stateJson(1)
    val legacyAction = "Kích hoạt Guilty Crown Override."
    val legacyPlain = KnowledgeContextEngine.buildForTest(dbJson, legacyState, legacyAction, "{}")
    val legacyTrace = KnowledgeContextEngine.buildForTestWithTrace(dbJson, legacyState, legacyAction, "{}")
    assertEquals(legacyPlain, legacyTrace.packet)
    assertTrue(
      legacyTrace.events.any {
        it.type == "reference_followed" &&
          it.fromId == "CHAR.KAI.GUILTY_CROWN_OVERRIDE" &&
          it.targetId == "CHAR.KAI.DEVIL_TRIGGER" &&
          it.shadowClass == "LEGACY_FOLLOWED"
      }
    )

    val relatedState = stateJson(1)
    val relatedPlain = KnowledgeContextEngine.buildForTest(dbJson, relatedState, "Quan sát lối đi.", "{}")
    val relatedTrace = KnowledgeContextEngine.buildForTestWithTrace(dbJson, relatedState, "Quan sát lối đi.", "{}")
    assertEquals(relatedPlain, relatedTrace.packet)
    assertTrue(
      relatedTrace.events.any {
        it.type == "reference_skipped" &&
          it.fromId == "LEVEL.01" &&
          it.targetId == "ENTITY.HOUND" &&
          it.shadowClass == "RELATED" &&
          it.targetPriority > KnowledgeContextEngine.LEGACY_REFERENCE_PRIORITY_GATE
      }
    )
  }

  @Test fun alreadyVisitedReferenceKeepsItsShadowClass() {
    val state = stateJson(1, arrayOf("iris"))
    val result = KnowledgeContextEngine.buildForTestWithTrace(dbJson, state, "Tôi hỏi Iris về lối đi.", "{}")
    val event = result.events.firstOrNull {
      it.type == "reference_skipped" &&
        it.fromId == "ADDR.IRIS.KAI" &&
        it.targetId == "WRITING.DIALOGUE" &&
        it.rule == "already_visited"
    }
    assertNotNull("Expected already-visited address -> dialogue edge", event)
    assertEquals("LEGACY_FOLLOWED", event!!.shadowClass)
  }

  @Test fun writesReferenceShadowReportForEntireRegistry() {
    val root = JSONObject(dbJson)
    val records = root.getJSONArray("records")
    val priorityById = linkedMapOf<String, Int>()
    for (i in 0 until records.length()) {
      val record = records.getJSONObject(i)
      priorityById[record.getString("id")] = record.optInt("priority", 80)
    }

    val edges = mutableListOf<JSONObject>()
    var legacyFollowed = 0
    var related = 0
    var missing = 0

    for (i in 0 until records.length()) {
      val record = records.getJSONObject(i)
      val fromId = record.getString("id")
      val references = record.optJSONArray("references") ?: JSONArray()
      for (j in 0 until references.length()) {
        val targetId = references.getString(j)
        val targetPriority = priorityById[targetId]
        val shadowClass = when {
          targetPriority == null -> "MISSING"
          targetPriority <= KnowledgeContextEngine.LEGACY_REFERENCE_PRIORITY_GATE -> "LEGACY_FOLLOWED"
          else -> "RELATED"
        }
        when (shadowClass) {
          "LEGACY_FOLLOWED" -> legacyFollowed++
          "RELATED" -> related++
          else -> missing++
        }
        edges += JSONObject()
          .put("from", fromId)
          .put("to", targetId)
          .put("targetExists", targetPriority != null)
          .put("targetPriority", targetPriority ?: JSONObject.NULL)
          .put("shadowClass", shadowClass)
      }
    }

    edges.sortWith(compareBy<JSONObject>({ it.getString("from") }, { it.getString("to") }))
    assertTrue("Expected current registry to contain reference edges", edges.isNotEmpty())

    val guiltyToDevil = edges.firstOrNull {
      it.getString("from") == "CHAR.KAI.GUILTY_CROWN_OVERRIDE" &&
        it.getString("to") == "CHAR.KAI.DEVIL_TRIGGER"
    }
    assertNotNull(guiltyToDevil)
    assertEquals("LEGACY_FOLLOWED", guiltyToDevil!!.getString("shadowClass"))

    val levelToHound = edges.firstOrNull {
      it.getString("from") == "LEVEL.01" && it.getString("to") == "ENTITY.HOUND"
    }
    assertNotNull(levelToHound)
    assertEquals("RELATED", levelToHound!!.getString("shadowClass"))

    val report = JSONObject()
      .put("schemaVersion", 1)
      .put("mode", "shadow_only")
      .put("legacyReferencePriorityGate", KnowledgeContextEngine.LEGACY_REFERENCE_PRIORITY_GATE)
      .put("edgeCount", edges.size)
      .put("counts", JSONObject()
        .put("legacyFollowed", legacyFollowed)
        .put("related", related)
        .put("missing", missing))
      .put("edges", JSONArray(edges))

    val reportDir = Path.of("build", "reports", "canon-p1").toFile()
    assertTrue("Could not create Canon P1 report directory", reportDir.mkdirs() || reportDir.isDirectory)
    val reportFile = reportDir.resolve("reference-shadow.json")
    reportFile.writeText(report.toString(2) + "\n", Charsets.UTF_8)
    assertTrue(reportFile.isFile)
    assertEquals(edges.size, legacyFollowed + related + missing)
  }

  private fun stateJson(level: Int, partyIds: Array<String> = emptyArray()): String {
    val party = JSONArray()
    partyIds.forEach { party.put(it) }
    return JSONObject()
      .put("turn", 10)
      .put("level", JSONObject().put("number", level))
      .put("party", party)
      .put("flags", JSONObject())
      .toString()
  }
}
''', encoding="utf-8")


ANDROID_TEST = Path(__file__).resolve().parent / "app/src/androidTest/java/com/rabpit/backroom/core/knowledge/KnowledgeContextSmokeInstrumentation.java"
ANDROID_TEST.parent.mkdir(parents=True, exist_ok=True)
legacy_android_test = ANDROID_TEST.with_name("KnowledgeContextAndroidSmokeTest.java")
if legacy_android_test.exists():
    legacy_android_test.unlink()
ANDROID_TEST.write_text(r'''package com.rabpit.backroom.core.knowledge;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class KnowledgeContextSmokeInstrumentation extends Instrumentation {
  private Bundle arguments;
  private final StringBuilder smokeDetails = new StringBuilder();

  @Override
  public void onCreate(Bundle arguments) {
    super.onCreate(arguments);
    this.arguments = arguments == null ? new Bundle() : new Bundle(arguments);
    start();
  }

  @Override
  public void onStart() {
    Bundle status = statusBundle();
    sendStatus(1, status);
    try {
      verifyAndroidRuntimeSerialization();
      status.putString("stream", ".");
      sendStatus(0, status);
      Bundle result = new Bundle();
      result.putString("stream", "\n" + smokeDetails + "OK (1 test)\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable error) {
      status.putString("stack", Log.getStackTraceString(error));
      status.putString("stream", "F");
      sendStatus(-2, status);
      Bundle result = new Bundle();
      result.putString("stream", "\nFAILURES!!!\n" + Log.getStackTraceString(error));
      finish(Activity.RESULT_CANCELED, result);
    }
  }

  private Bundle statusBundle() {
    Bundle status = new Bundle();
    status.putString("id", "InstrumentationTestRunner");
    status.putInt("numtests", 1);
    status.putString("class", KnowledgeContextSmokeInstrumentation.class.getName());
    status.putString("test", "androidRuntimeSerializationMatchesJvmSnapshot");
    status.putInt("current", 1);
    return status;
  }

  private void verifyAndroidRuntimeSerialization() throws Exception {
    Context context = getTargetContext();
    String dbJson = readAsset(context, "knowledge/knowledge_db.json");

    assertScenario(
      context,
      dbJson,
      "android_smoke_quiet",
      "{\"turn\":10,\"level\":{\"number\":0},\"party\":[],\"flags\":{}}",
      "Observe the quiet hallway.",
      arguments.getString("p0QuietSha")
    );

    String condition = repeat("C", 7000);
    StringBuilder log = new StringBuilder();
    for (int i = 0; i < 4; i++) {
      if (i > 0) log.append(',');
      String role = (i % 2 == 0) ? "player" : "gm";
      log.append("{\"role\":\"").append(role).append("\",\"text\":\"")
        .append(i).append(':').append(repeat("D", 1200)).append("\"}");
    }
    String hardClipState =
      "{\"turn\":10,\"level\":{\"number\":1},\"party\":[\"iris\",\"syvial\"],\"flags\":{},\"player\":{\"id\":\"cao_minh\",\"hp\":100,\"condition\":\""
      + condition + "\"},\"log\":[" + log + "]}";

    assertScenario(
      context,
      dbJson,
      "android_smoke_hard_clip",
      hardClipState,
      "The group waits.",
      arguments.getString("p0HardClipSha")
    );
  }

  private void assertScenario(
    Context context,
    String dbJson,
    String name,
    String stateJson,
    String action,
    String expectedSha
  ) throws Exception {
    if (expectedSha == null || expectedSha.isEmpty()) {
      throw new AssertionError("Missing JVM snapshot hash for " + name);
    }
    String production = KnowledgeContextEngine.build(context, stateJson, action, "{}");
    String seam = KnowledgeContextEngine.buildForTest(dbJson, stateJson, action, "{}");
    if (!production.equals(seam)) {
      throw new AssertionError(name + " production asset path differs from test seam on Android");
    }
    String androidSha = sha256(production);
    smokeDetails
      .append(name).append(".jvmSha=").append(expectedSha).append('\n')
      .append(name).append(".androidSha=").append(androidSha).append('\n')
      .append(name).append(".crossPlatformByteEqual=").append(expectedSha.equals(androidSha)).append('\n');
  }

  private static String readAsset(Context context, String path) throws Exception {
    BufferedReader reader = new BufferedReader(
      new InputStreamReader(context.getAssets().open(path), StandardCharsets.UTF_8)
    );
    StringBuilder out = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null) {
      out.append(line).append('\n');
    }
    reader.close();
    return out.toString();
  }

  private static String repeat(String value, int count) {
    StringBuilder out = new StringBuilder(value.length() * count);
    for (int i = 0; i < count; i++) out.append(value);
    return out.toString();
  }

  private static String sha256(String value) throws Exception {
    byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    StringBuilder out = new StringBuilder();
    for (byte b : bytes) out.append(String.format("%02x", b & 0xff));
    return out.toString();
  }
}
''', encoding="utf-8")

GRADLE = Path(__file__).resolve().parent / "app/build.gradle"
gradle = GRADLE.read_text(encoding="utf-8")
runner_anchor = "    versionName '1.1.63.0.1'\n"
runner_line = '    testInstrumentationRunner "com.rabpit.backroom.core.knowledge.KnowledgeContextSmokeInstrumentation"\n'
if runner_line not in gradle:
    if runner_anchor not in gradle:
        raise RuntimeError("P0 Android smoke runner anchor not found")
    gradle = gradle.replace(runner_anchor, runner_anchor + runner_line, 1)
    GRADLE.write_text(gradle, encoding="utf-8")

print("Knowledge P0 observability installed: final-runtime test seam, passive candidate/reference/budget trace, and three characterization fixtures.")
