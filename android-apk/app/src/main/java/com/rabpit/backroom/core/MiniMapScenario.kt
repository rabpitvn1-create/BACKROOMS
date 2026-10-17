package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Bounded, monotone puzzle language. Generated strings are never executable predicates. */
object MiniMapScenario {
  const val SCHEMA = 4
  const val MAX_CHARS = 48000
  private val idPattern = Regex("[a-z][a-z0-9_]{0,39}")
  private val reservedNames = Regex("lucia|lục trầm|luc tram|iris|syvial|an nhiên|an nhien|diệp minh|cao minh", RegexOption.IGNORE_CASE)
  private val genericNpcNames = Regex(
    "^(?:npc(?:[ _-]*[0-9]+)?|người (?:gác|lạ|sống sót|kỹ thuật)(?: [0-9]+)?|nhân chứng(?: [0-9]+)?|guard(?: [0-9]+)?|witness(?: [0-9]+)?|survivor(?: [0-9]+)?)$",
    RegexOption.IGNORE_CASE
  )
  // Generated Vietnamese scenery is not an excuse to splice English directions
  // into room names (e.g. "Khu Trần Giả Vòng Down"). Canon proper names
  // such as The Lobby, Hollow Grasper and Zenith Station remain unchanged.
  private val mixedLanguageWords = Regex(
    """(?iu)\b(?:down|up|floor|ceiling|wall|corner|room|hall|hallway|door|window|exit|entrance|zone|area|loop|fake|false|wet|dark|light|left|right|passage|corridor|stairs|staircase|broken|hidden)\b"""
  )
  private val allowedEnglishCanonTitles = Regex(
    """(?iu)\b(?:manila room|parking zone|predatory window)\b"""
  )
  private fun vietnameseSceneLabel(value: String) {
    // Proper Canon level/entity titles can remain English inside Vietnamese prose.
    val proseWithoutCanonTitles = allowedEnglishCanonTitles.replace(value, " ")
    require(!mixedLanguageWords.containsMatchIn(proseWithoutCanonTitles)) { "scene_language_mixed" }
  }
  // Only reject unambiguous location-name mistakes. Grammar and meaning still
  // belong to the writer; a broad Vietnamese word blacklist would reject valid Canon.
  private val roomActionStart = Regex(
    """^(?:nghe|lắng nghe|nhìn|quan sát|tìm|kiểm tra|khám phá|chọn|bước vào|đi tới)\s+""",
    RegexOption.IGNORE_CASE
  )
  private val genericLocationStarts = setOf("phòng", "hành", "góc", "khu", "ngách",
    "buồng", "lối", "kho", "cầu", "đường")
  private fun naturalRoomName(name: String) {
    val words = name.trim().split(Regex("""\s+"""))
    // A whole Title-Case generic phrase is not how Vietnamese names ordinary rooms.
    // Canon titles and proper names embedded after lowercase connectors stay valid.
    val genericTitleCase = words.size >= 3 && words.first().lowercase() in genericLocationStarts &&
      words.all { word -> word.firstOrNull()?.isUpperCase() == true }
    require(!roomActionStart.containsMatchIn(name.trim()) && !genericTitleCase) { "scene_room_name" }
  }
  // Scene-local prose must not describe a later level before Core moves to that node.
  fun canonBoundary(nodeId: String, prose: String) {
    if (nodeId != "0") return
    val restricted = listOf("level 1", "level-1", "level one", "vùng bê tông",
      "khu bê tông", "giao thoa kiến trúc", "dầm thép lộ thiên",
      "hành lang công nghiệp", "rời khỏi level 0", "thoát khỏi level 0")
    require(restricted.none { prose.lowercase().contains(it) }) { "scene_canon_boundary" }
  }
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
  fun validateOutline(raw: JSONObject, nodeId: String): JSONObject {
    fields(raw, setOf("schemaVersion", "levelNumber", "nodeAnchor", "entries"))
    val node = requireNotNull(LevelRouteRegistry.node(nodeId)) { "scenario_node" }
    require(int(raw, "schemaVersion") == SCHEMA && int(raw, "levelNumber") == node.parent && raw.getString("nodeAnchor") == nodeId)
    val entries = objects(raw.getJSONArray("entries")); require(entries.size == 10)
    entries.forEachIndexed { i, e ->
      fields(e, setOf("index", "nodeAnchor", "beat", "objective", "dependencyRefs"))
      require(int(e, "index") == i + 1 && e.getString("nodeAnchor") == nodeId) { "outline_route" }
      canonBoundary(nodeId, text(e, "beat", 220)); canonBoundary(nodeId, text(e, "objective", 220))
      vietnameseSceneLabel(e.getString("beat")); vietnameseSceneLabel(e.getString("objective"))
      val deps = e.getJSONArray("dependencyRefs")
      require(deps.length() <= i && (0 until deps.length()).all { d -> deps.get(d) is Int && deps.getInt(d) in 1..i }) { "outline_dependency" }
    }
    return copy(raw)
  }
  fun validate(raw: JSONObject, nodeId: String, index: Int): JSONObject {
    require(raw.toString().length <= MAX_CHARS)
    fields(raw, setOf("schemaVersion", "id", "levelNumber", "index", "nodeAnchor", "objective", "entryRoomId", "exitRoomId", "rooms", "npcs", "facts", "interactions", "goal"))
    val node = requireNotNull(LevelRouteRegistry.node(nodeId)) { "scenario_node" }
    require(index in 1..10 && int(raw, "schemaVersion") == SCHEMA && int(raw, "levelNumber") == node.parent && int(raw, "index") == index)
    id(raw); canonBoundary(nodeId, text(raw, "objective", 400))
    vietnameseSceneLabel(raw.getString("objective"))
    require(raw.getString("nodeAnchor") == nodeId) { "script_route" }
    val rooms = list(raw, "rooms", 4, 12); val roomIds = rooms.map { it.getString("id") }.toSet()
    require(id(raw, "entryRoomId") in roomIds && id(raw, "exitRoomId") in roomIds)
    rooms.forEach { r ->
      fields(r, setOf("id", "name", "description", "adjacentRoomIds"))
      canonBoundary(nodeId, text(r, "name", 90)); canonBoundary(nodeId, text(r, "description", 600))
      vietnameseSceneLabel(r.getString("name")); vietnameseSceneLabel(r.getString("description"))
      naturalRoomName(r.getString("name"))
      val adj = strings(r.getJSONArray("adjacentRoomIds")); require(adj.size in 1..6 && adj.distinct().size == adj.size)
      require(adj.all { it in roomIds && it != r.getString("id") }) { "graph_reference" }
      // Every movement has an unconditional return edge. No irreversible softlocks.
      require(adj.all { to -> strings(rooms.first { it.getString("id") == to }.getJSONArray("adjacentRoomIds")).contains(r.getString("id")) }) { "graph_return_edge" }
    }
    val reached = mutableSetOf(raw.getString("entryRoomId"))
    repeat(rooms.size) { rooms.filter { it.getString("id") in reached }.forEach { reached.addAll(strings(it.getJSONArray("adjacentRoomIds"))) } }
    require(reached == roomIds) { "graph_disconnected" }
    // NPCs are authored actors in the scene, never ephemeral random passersby.
    // Zero NPCs is valid; each declared NPC must have a concrete puzzle interaction.
    val npcs = list(raw, "npcs", 0, 12)
    val npcIds = npcs.map { it.getString("id") }.toSet()
    npcs.forEach { npc ->
      fields(npc, setOf("id", "name", "originPeriod", "role", "purpose", "description", "roomId"))
      val name = text(npc, "name", 90).trim()
      text(npc, "originPeriod", 100)
      canonBoundary(nodeId, text(npc, "role", 120))
      canonBoundary(nodeId, text(npc, "purpose", 220))
      canonBoundary(nodeId, text(npc, "description", 350))
      require(id(npc, "roomId") in roomIds) { "npc_room" }
      require(!reservedNames.containsMatchIn(name)) { "scenario_canon_identity" }
      require(name.any(Char::isLetter) && !genericNpcNames.matches(name)) { "npc_name_required" }
    }
    require(npcs.map { it.getString("name").trim().lowercase() }.distinct().size == npcs.size) { "npc_duplicate_name" }
    // With multiple scripted characters, at least two origins must be distinct.
    // This compares era labels, not historical truth or a time-travel mechanic.
    require(npcs.size < 2 || npcs.map { it.getString("originPeriod").trim().lowercase() }.distinct().size >= 2) { "npc_period_diversity" }
    val facts = list(raw, "facts", 2, 12); val factIds = facts.map { it.getString("id") }.toSet()
    facts.forEach { f ->
      fields(f, setOf("id", "label", "value", "values"))
      canonBoundary(nodeId, text(f, "label", 90)); text(f, "value", 90)
      val values = strings(f.getJSONArray("values"))
      require(values.size in 2..5 && values.distinct().size == values.size &&
        f.getString("value") in values && values.all { it.isNotBlank() && it.length <= 90 }) { "fact_domain" }
      // These are private matching tokens, not prose shown to players.
      // Snake_case tokens are valid (the identifier schema uses them too);
      // human-readable labels and interaction observations are validated separately.
      require(values.none { it.any { c -> c < ' ' || c == '\u007f' } }) { "fact_human_value" }
    }
    val interactions = list(raw, "interactions", 4, 36); val actionIds = interactions.map { it.getString("id") }.toSet()
    interactions.forEach { a ->
      fields(a, setOf("id", "kind", "roomId", "label", "requires", "effect"),
        setOf("npcId", "observation"))
      val kind = a.getString("kind")
      require(kind in setOf("SEARCH", "TALK")); require(id(a, "roomId") in roomIds)
      if (kind == "TALK") {
        val speakerId = id(a, "npcId")
        require(speakerId in npcIds && npcs.first { it.getString("id") == speakerId }
          .getString("roomId") == a.getString("roomId")) { "npc_talk_binding" }
      } else require(!a.has("npcId")) { "npc_search_binding" }
      canonBoundary(nodeId, text(a, "label", 120))
      vietnameseSceneLabel(a.getString("label"))
      require(!reservedNames.containsMatchIn(a.getString("label"))) { "scenario_canon_identity" }
      if (a.has("observation")) {
        val observation = text(a, "observation", 320)
        require(observation.trim().length >= 35 && observation.trim() != a.getString("label").trim() &&
          !Regex("[a-z]+(?:_[a-z0-9]+)+").containsMatchIn(observation)) { "scene_observation_quality" }
        canonBoundary(nodeId, observation)
        vietnameseSceneLabel(observation)
      }
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
    val observations = interactions.filter { it.has("observation") }
      .map { it.getString("observation").trim().lowercase() }
    require(observations.distinct().size == observations.size) { "scene_duplicate_observation" }
    // Prevent decorative NPCs which cannot be spoken to or provide no quest data.
    npcs.forEach { npc ->
      require(interactions.any { it.getString("kind") == "TALK" &&
        it.getString("npcId") == npc.getString("id") }) { "npc_without_interaction" }
    }
    predicate(raw.getJSONObject("goal"), actionIds)
    // Forward chaining is complete for this finite monotone language: acquiring facts
    // never removes actions; wrong answers consume only that option, never the correct one.
    fun reachable(excluded: Set<String> = emptySet()): Set<String> {
      val visited = mutableSetOf<String>()
      repeat(interactions.size + 1) {
        interactions.filter { it.getString("id") !in excluded && holds(it.getJSONObject("requires"), visited) }.forEach { action ->
          val effect = action.getJSONObject("effect")
          if (effect.getString("type") != "ANSWER" ||
            facts.first { it.getString("id") == effect.getString("factId") }.getString("value") == effect.getString("value"))
            visited.add(action.getString("id"))
        }
      }
      return visited
    }
    val solved = reachable()
    require(holds(raw.getJSONObject("goal"), solved) && !holds(raw.getJSONObject("goal"), emptySet())) { "puzzle_unsolvable_or_empty" }
    // Each answer needs an observable, truthful clue; a lying NPC alone is not a proof.
    interactions.filter { it.getJSONObject("effect").getString("type") == "ANSWER" }.forEach { a ->
      val factId = a.getJSONObject("effect").getString("factId")
      val proofIds = interactions.filter {
        it.getString("id") in solved && it.getJSONObject("effect").getString("factId") == factId &&
          it.getJSONObject("effect").getString("type") in setOf("CLUE", "EVIDENCE")
      }.map { it.getString("id") }.toSet()
      require(proofIds.isNotEmpty()) { "answer_without_evidence" }
      // A correct answer may be offered only after observing at least one proof
      // for its own fact. Unrelated clues don't count.
      if (a.getJSONObject("effect").getString("value") == facts.first { it.getString("id") == factId }.getString("value")) {
        require(a.getString("id") !in reachable(proofIds)) { "answer_without_observed_proof" }
      }
    }
    val answerGroups = interactions.filter { it.getJSONObject("effect").getString("type") == "ANSWER" }
      .groupBy { it.getJSONObject("effect").getString("factId") }
    require(answerGroups.count { (_, options) ->
      !holds(raw.getJSONObject("goal"), solved - options.map { it.getString("id") }.toSet())
    } >= 2) { "puzzle_requires_two_inferences" }
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
    val observation = a.optString("observation").trim()
    val speaker = if (type == "CLAIM") objects(script.getJSONArray("npcs"))
      .firstOrNull { it.getString("id") == a.optString("npcId") }?.getString("name") ?: "Nhân chứng" else ""
    val line = when(type) {
      "CLAIM" -> if (observation.isNotEmpty()) "Lời khai của $speaker (chưa xác minh): $observation"
        else "Lời khai của $speaker (chưa xác minh) về ${fact.getString("label")}. Cần đối chiếu với vật chứng độc lập."
      "ANSWER" -> if (correct) "Đối chiếu chính xác: ${fact.getString("label")}." else "Giả thuyết không khớp; có thể chọn phương án khác."
      else -> if (observation.isNotEmpty()) "${if (type == "EVIDENCE") "Vật chứng" else "Quan sát"}: $observation"
        else "${if (type == "EVIDENCE") "Vật chứng" else "Quan sát"}: ${a.getString("label")}. Hãy ghi nhớ dấu vết này để đối chiếu."
    }
    return line to correct
  }
}
