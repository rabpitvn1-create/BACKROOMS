package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Bounded, monotone puzzle language. Generated strings are never executable predicates. */
object MiniMapScenario {
  const val SCHEMA = 1
  const val MAX_CHARS = 48000
  private val idPattern = Regex("[a-z][a-z0-9_]{0,39}")
  private val reservedNames = Regex("lucia|lục trầm|luc tram|iris|syvial|an nhiên|an nhien|diệp minh|cao minh", RegexOption.IGNORE_CASE)
  fun objects(a: JSONArray): List<JSONObject> = (0 until a.length()).map { a.getJSONObject(it) }
  fun strings(a: JSONArray): List<String> = (0 until a.length()).map { a.get(it) as String }
  fun copy(o: JSONObject) = JSONObject(o.toString())
  private fun fields(o: JSONObject, required: Set<String>, optional: Set<String> = emptySet()) {
    require(o.keys().asSequence().toSet().let { it.containsAll(required) && (it - required - optional).isEmpty() }) { "scenario_fields" }
  }
  private fun id(o: JSONObject, key: String = "id"): String = o.get(key).let {
    require(it is String && idPattern.matches(it)) { "scenario_id" }; it
  }
  private fun text(o: JSONObject, key: String, max: Int = 600): String = o.get(key).let {
    require(it is String && it.isNotBlank() && it.length <= max && !it.any { c -> c < ' ' && c != '\n' }) { "scenario_text" }; it
  }
  private fun list(o: JSONObject, key: String, min: Int, max: Int): List<JSONObject> = objects(o.getJSONArray(key)).also {
    require(it.size in min..max) { "scenario_count" }
    val ids = it.map { v -> id(v) }; require(ids.distinct().size == ids.size) { "scenario_duplicate_id" }
  }
  private fun int(o: JSONObject, key: String): Int {
    val value = o.get(key); require(value is Int) { "scenario_integer" }; return value
  }
  fun anchor(level: Int, index: Int): String {
    require(level in 0..6 && index in 1..10)
    return if (level == 0) listOf("0", "0", "0.1", "0.2", "0.5", "0.7", "manila_room", "the_torment", "red_rooms", "red_rooms")[index - 1] else level.toString()
  }
  /** Size/depth limits apply before JSONObject parsing to avoid recursive-parser exhaustion. */
  fun parse(raw: String): JSONObject {
    require(raw.length <= MAX_CHARS) { "scenario_too_large" }
    var depth = 0; var quoted = false; var escaped = false
    for (c in raw) {
      if (quoted) { if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false }
      else when(c) { '"' -> quoted = true; '{', '[' -> { depth++; require(depth <= 12) { "scenario_too_deep" } }; '}', ']' -> { depth--; require(depth >= 0) } }
    }
    require(!quoted && depth == 0 && raw.trim().startsWith("{") && raw.trim().endsWith("}")) { "scenario_json" }
    val tokens = JSONTokener(raw)
    val parsed = tokens.nextValue()
    require(parsed is JSONObject && tokens.nextClean() == '\u0000') { "scenario_trailing_json" }
    return parsed
  }
  fun validateOutline(raw: JSONObject, level: Int): JSONObject {
    fields(raw, setOf("schemaVersion", "levelNumber", "entries"))
    require(int(raw, "schemaVersion") == SCHEMA && int(raw, "levelNumber") == level)
    val entries = objects(raw.getJSONArray("entries")); require(entries.size == 10)
    entries.forEachIndexed { i, e ->
      fields(e, setOf("index", "nodeAnchor", "beat", "objective", "dependencyRefs"))
      require(int(e, "index") == i + 1 && e.getString("nodeAnchor") == anchor(level, i + 1)) { "outline_route" }
      text(e, "beat", 220); text(e, "objective", 220)
      val deps = e.getJSONArray("dependencyRefs")
      require(deps.length() <= i && (0 until deps.length()).all { d -> deps.get(d) is Int && deps.getInt(d) in 1..i }) { "outline_dependency" }
    }
    return copy(raw)
  }
  fun validate(raw: JSONObject, level: Int, index: Int): JSONObject {
    require(raw.toString().length <= MAX_CHARS)
    fields(raw, setOf("schemaVersion", "id", "levelNumber", "index", "nodeAnchor", "objective", "entryRoomId", "exitRoomId", "rooms", "facts", "interactions", "goal"))
    require(int(raw, "schemaVersion") == SCHEMA && int(raw, "levelNumber") == level && int(raw, "index") == index)
    id(raw); text(raw, "objective", 400)
    require(raw.getString("nodeAnchor") == anchor(level, index)) { "script_route" }
    val rooms = list(raw, "rooms", 3, 12); val roomIds = rooms.map { it.getString("id") }.toSet()
    require(id(raw, "entryRoomId") in roomIds && id(raw, "exitRoomId") in roomIds)
    rooms.forEach { r ->
      fields(r, setOf("id", "name", "description", "adjacentRoomIds"))
      text(r, "name", 90); text(r, "description", 600)
      val adj = strings(r.getJSONArray("adjacentRoomIds")); require(adj.size in 1..6 && adj.distinct().size == adj.size)
      require(adj.all { it in roomIds && it != r.getString("id") }) { "graph_reference" }
      // Every movement has an unconditional return edge. No irreversible softlocks.
      require(adj.all { to -> strings(rooms.first { it.getString("id") == to }.getJSONArray("adjacentRoomIds")).contains(r.getString("id")) }) { "graph_return_edge" }
    }
    val reached = mutableSetOf(raw.getString("entryRoomId"))
    repeat(rooms.size) { rooms.filter { it.getString("id") in reached }.forEach { reached.addAll(strings(it.getJSONArray("adjacentRoomIds"))) } }
    require(reached == roomIds) { "graph_disconnected" }
    val facts = list(raw, "facts", 2, 12); val factIds = facts.map { it.getString("id") }.toSet()
    facts.forEach { f ->
      fields(f, setOf("id", "label", "value", "values")); text(f, "label", 90); text(f, "value", 90)
      val values = strings(f.getJSONArray("values")); require(values.size in 2..5 && values.distinct().size == values.size && f.getString("value") in values && values.all { it.isNotBlank() && it.length <= 90 }) { "fact_domain" }
    }
    val interactions = list(raw, "interactions", 4, 36); val actionIds = interactions.map { it.getString("id") }.toSet()
    interactions.forEach { a ->
      fields(a, setOf("id", "kind", "roomId", "label", "requires", "effect"))
      require(a.getString("kind") in setOf("SEARCH", "TALK")); require(id(a, "roomId") in roomIds)
      text(a, "label", 120); require(!reservedNames.containsMatchIn(a.getString("label"))) { "scenario_canon_identity" }
      predicate(a.getJSONObject("requires"), actionIds)
      val effect = a.getJSONObject("effect")
      fields(effect, setOf("type", "factId"), setOf("value", "policy"))
      val type = effect.getString("type"); require(type in setOf("CLUE", "EVIDENCE", "CLAIM", "ANSWER"))
      require(id(effect, "factId") in factIds)
      val fact = facts.first { it.getString("id") == effect.getString("factId") }
      if (type in setOf("CLAIM", "ANSWER")) {
        val value = text(effect, "value", 90); require(value in strings(fact.getJSONArray("values")))
        if (type == "CLAIM") {
          require(a.getString("kind") == "TALK")
          val policy = effect.getString("policy"); require(policy in setOf("TRUTH", "LIE", "MISTAKEN"))
          require((value == fact.getString("value")) == (policy == "TRUTH")) { "claim_truth_policy" }
        }
      } else require(a.getString("kind") == "SEARCH")
      require(effect.has("value") == (type in setOf("CLAIM", "ANSWER")) && effect.has("policy") == (type == "CLAIM"))
    }
    predicate(raw.getJSONObject("goal"), actionIds)
    // Forward chaining is complete for this finite monotone language: acquiring facts
    // never removes actions; wrong answers consume only that option, never the correct one.
    val solved = mutableSetOf<String>()
    repeat(interactions.size + 1) {
      interactions.filter { holds(it.getJSONObject("requires"), solved) }.forEach { a ->
        val e = a.getJSONObject("effect")
        if (e.getString("type") != "ANSWER" || facts.first { it.getString("id") == e.getString("factId") }.getString("value") == e.getString("value")) solved.add(a.getString("id"))
      }
    }
    require(holds(raw.getJSONObject("goal"), solved) && !holds(raw.getJSONObject("goal"), emptySet())) { "puzzle_unsolvable_or_empty" }
    // Each answer needs an observable, truthful clue; a lying NPC alone is not a proof.
    interactions.filter { it.getJSONObject("effect").getString("type") == "ANSWER" }.forEach { a ->
      val factId = a.getJSONObject("effect").getString("factId")
      require(interactions.any { it.getString("id") in solved && it.getJSONObject("effect").getString("factId") == factId && it.getJSONObject("effect").getString("type") in setOf("CLUE", "EVIDENCE") }) { "answer_without_evidence" }
    }
    val answerGroups = interactions.filter { it.getJSONObject("effect").getString("type") == "ANSWER" }
      .groupBy { it.getJSONObject("effect").getString("factId") }
    require(answerGroups.count { (_, options) ->
      !holds(raw.getJSONObject("goal"), solved - options.map { it.getString("id") }.toSet())
    } >= 2) { "puzzle_requires_two_inferences" }
    require(interactions.any { it.getJSONObject("effect").optString("policy") in setOf("LIE", "MISTAKEN") }) { "puzzle_requires_verifiable_claim" }
    return copy(raw)
  }
  private fun predicate(p: JSONObject, ids: Set<String>, depth: Int = 0) {
    require(depth <= 4)
    when (p.getString("op")) {
      "TRUE" -> fields(p, setOf("op"))
      "HAS" -> { fields(p, setOf("op", "id")); require(id(p) in ids) { "predicate_reference" } }
      "AND", "OR" -> { fields(p, setOf("op", "args")); val args = objects(p.getJSONArray("args")); require(args.size in 1..8); args.forEach { predicate(it, ids, depth + 1) } }
      else -> error("predicate_operator")
    }
  }
  fun holds(p: JSONObject, accepted: Set<String>): Boolean = when(p.getString("op")) {
    "TRUE" -> true
    "HAS" -> p.getString("id") in accepted
    "AND" -> objects(p.getJSONArray("args")).all { holds(it, accepted) }
    "OR" -> objects(p.getJSONArray("args")).any { holds(it, accepted) }
    else -> false
  }
  fun effectText(script: JSONObject, a: JSONObject): Pair<String, Boolean> {
    val e = a.getJSONObject("effect"); val fact = objects(script.getJSONArray("facts")).first { it.getString("id") == e.getString("factId") }
    val type = e.getString("type")
    val correct = type != "ANSWER" || fact.getString("value") == e.getString("value")
    val line = when(type) {
      "CLAIM" -> "Lời khai chưa xác minh: ${fact.getString("label")} = ${e.getString("value")}."
      "ANSWER" -> if (correct) "Đối chiếu chính xác: ${fact.getString("label")} = ${e.getString("value")}." else "Giả thuyết không khớp; có thể chọn phương án khác."
      else -> "${if (type == "EVIDENCE") "Vật chứng nhiệm vụ" else "Quan sát xác nhận"}: ${fact.getString("label")} = ${fact.getString("value")}."
    }
    return line to correct
  }
}
