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
              decision = "skipped", rule = "already_visited"
            ))
            return@forEach
          }
          val target = db.records[id]
          if (target == null) {
            trace?.add(KnowledgeTraceEvent(
              type = "reference_skipped", fromId = record.id, targetId = id,
              decision = "skipped", rule = "missing_target"
            ))
            return@forEach
          }
          if (target.priority <= 55) {
            trace?.add(KnowledgeTraceEvent(
              type = "reference_followed", fromId = record.id, targetId = id,
              decision = "followed", rule = "priority_gate_55",
              targetPriority = target.priority
            ))
            add(id, "direct reference from ${record.id}")
            queue.add(target)
          } else {
            trace?.add(KnowledgeTraceEvent(
              type = "reference_skipped", fromId = record.id, targetId = id,
              decision = "skipped", rule = "priority_gate_55",
              targetPriority = target.priority
            ))
          }
        }
      }
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
  private data class Fixture(val name: String, val level: Int, val action: String)

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

  @Test fun traceDoesNotChangePacketForFirstThreeFixtures() {
    val fixtures = listOf(
      Fixture("quiet_exploration_L0", 0, "Quan sát hành lang yên tĩnh."),
      Fixture("direct_entity_lookup_smiler", 2, "Kiểm tra dấu hiệu của smiler."),
      Fixture("reference_above_gate", 1, "Kiểm tra lối đi phía trước.")
    )

    fixtures.forEach { fixture ->
      val state = stateJson(fixture.level)
      val plain = KnowledgeContextEngine.buildForTest(dbJson, state, fixture.action, "{}")
      val traced = KnowledgeContextEngine.buildForTestWithTrace(dbJson, state, fixture.action, "{}")
      assertEquals("${fixture.name} packet changed when trace enabled", sha256(plain), sha256(traced.packet))
      assertEquals("${fixture.name} packet bytes changed when trace enabled", plain, traced.packet)
    }
  }

  @Test fun tracePreservesMultipleCandidateReasons() {
    val traced = KnowledgeContextEngine.buildForTestWithTrace(
      dbJson, stateJson(2), "Kiểm tra dấu hiệu của smiler.", "{}"
    )
    val reasons = traced.events
      .filter { it.type == "candidate_reason_appended" && it.recordId == "ENTITY.SMILER" }
      .map { it.reason }
      .toSet()
    assertTrue("Expected tag and affordance reasons for ENTITY.SMILER, got $reasons", reasons.size >= 2)
  }

  @Test fun traceRecordsReferenceSkippedAboveLegacyPriorityGate() {
    val traced = KnowledgeContextEngine.buildForTestWithTrace(
      dbJson, stateJson(1), "Kiểm tra lối đi phía trước.", "{}"
    )
    val skipped = traced.events.firstOrNull {
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
    val traced = KnowledgeContextEngine.buildForTestWithTrace(
      dbJson, stateJson(2), "Kiểm tra dấu vết và mối đe dọa smiler.", "{}"
    )
    val budget = traced.events.lastOrNull { it.type == "budget_summary" }
    assertNotNull("Expected budget summary", budget)
    assertTrue("Expected positive budget token estimate", budget!!.budgetEstimatedTokens > 0)

    val spans = traced.events.filter { it.type == "packet_span" }
    assertTrue("Expected serialized record spans", spans.isNotEmpty())
    assertTrue(spans.all { it.startChar >= 0 && it.endChar > it.startChar })

    val clip = traced.events.lastOrNull { it.type == "hard_clip" }
    assertNotNull("Expected hard-clip observation event", clip)
    assertTrue(clip!!.serializedCharsBeforeClip >= clip.serializedCharsAfterClip)
    assertEquals(traced.packet.length, clip.serializedCharsAfterClip)
    if (clip.decision == "clipped") {
      assertTrue("Clipped packet must expose cut offset", clip.startChar >= 0)
    }
  }

  private fun stateJson(level: Int): String = JSONObject()
    .put("turn", 10)
    .put("level", JSONObject().put("number", level))
    .put("party", JSONArray())
    .put("flags", JSONObject())
    .toString()

  private fun sha256(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }
}
''', encoding="utf-8")

print("Knowledge P0 observability installed: final-runtime test seam, passive candidate/reference/budget trace, and three characterization fixtures.")
