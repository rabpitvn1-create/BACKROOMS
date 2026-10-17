package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/** Reader orchestration uses the existing Core state/codec/checkpoint, never WebView storage.
 * Tickets survive replanning: changed eligibility cannot be used to fish for a better roll.
 * A step is one Core-approved EXECUTE exploration plan; prose and choice selection roll nothing.
 */
object ReaderRuntime {
  const val KEY = "reader.runtime"
  private val rng = SecureRandom()
  val tones = linkedMapOf(
    "HORROR" to "Kinh dị: bất an, âm thanh, cảm giác bị săn đuổi",
    "BODY_HORROR" to "Body Horror: dị thể và cảm giác sinh học đúng Canon",
    "SURVIVAL" to "Sinh tồn: địa hình, tài nguyên và áp lực chiến thuật",
    "BRIGHT" to "Tươi sáng: hy vọng, kết nối và hài hước phù hợp; nguy hiểm vẫn thật",
    "DARK" to "Tăm tối: mất mát, bi kịch, mâu thuẫn tâm lý",
    "CINEMATIC" to "Điện ảnh: tiết tấu, hình ảnh và cao trào rõ ràng"
  )
  enum class Difficulty(val hpPercent: Int, val attackPercent: Int) {
    NORMAL(100, 100), HARD(125, 125), CAO_MINH_MUST_DIE(150, 175)
  }
  fun data(state: GameState): JSONObject = JSONObject(state.metadata[KEY] ?: "{}")
  fun store(state: GameState, data: JSONObject): GameState = state.copy(metadata = state.metadata + (KEY to data.toString()))
  fun node(state: GameState): String = LevelRouteRegistry.requested(JSONObject(state.world.getValue("levelJson")))!!.id
  fun difficulty(state: GameState): Difficulty = Difficulty.valueOf(data(state).optString("difficulty", "NORMAL"))
  fun scale(value: Int, percent: Int): Int = ((value.toLong() * percent + 50) / 100).coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
  fun active(state: GameState): JSONObject? = data(state).optJSONObject("active")
  fun requiresSettings(state: GameState): Boolean = data(state).optString("configuredNode") != node(state)
  fun configure(state: GameState, tone: String, difficulty: String): GameState {
    require(idle(state) && active(state) == null) { "turn_in_progress" }
    require(requiresSettings(state)) { "level_settings_locked" }
    require(tone in tones) { "tone_invalid" }
    Difficulty.valueOf(difficulty)
    return store(state, data(state).put("tone", tone).put("difficulty", difficulty).put("configuredNode", node(state)))
  }
  private fun idle(state: GameState) = CombatRuntime.pending(state) == null && CombatRuntime.active(state) == null &&
    state.turn.pending == null && ActionRuntime.activeSession(state) == null
  fun requestId(state: GameState) = "PAGE:${CoreClientProjection.turnIndex(state)}"

  /** At most two independent tickets, not future events. Re-evaluate against current Core facts. */
  fun reserve(state: GameState, nextInt: (Int) -> Int = { rng.nextInt(it) }): GameState {
    val d = data(state)
    val tickets = d.optJSONArray("tickets") ?: JSONArray()
    while (tickets.length() < 2) tickets.put(JSONObject()
      .put("entity", nextInt(10000)).put("streak", nextInt(100))
      .put("npcRolls", JSONArray().apply { repeat(6) { put(nextInt(10000)) } }))
    return store(state, d.put("tickets", tickets))
  }
  private fun spawns(state: GameState, ticket: JSONObject, action: String): SpawnResult {
    val npcRolls = ticket.getJSONArray("npcRolls")
    var npcIndex = 0
    var entityDraw = JSONObject(state.world.getValue("flagsJson")).optBoolean("entityEncountersAllowed", true)
    return SpawnMechanism.rollSpawns(state, ActionKind.EXECUTE, action) { bound ->
      require(bound == 10000) { "spawn_ticket_bound_changed" }
      if (entityDraw) { entityDraw = false; ticket.getInt("entity") }
      else { require(npcIndex < npcRolls.length()) { "spawn_ticket_budget_exceeded" }; npcRolls.getInt(npcIndex++) }
    }
  }
  fun spawnForActive(state: GameState): SpawnResult {
    val a = requireNotNull(active(state))
    return spawns(state, data(state).getJSONArray("tickets").getJSONObject(0), a.getString("action"))
  }
  fun rolls(state: GameState, ticket: JSONObject, action: String, spawn: SpawnResult): JSONObject {
    val before = JSONObject().put("flags", JSONObject(state.world.getValue("flagsJson")))
    val eligible = PhysicalActionDetector.isSceneFacing(action) && spawn.entityKey == null && !HiddenExitStreak.ready(before)
    return JSONObject().put(HiddenExitStreak.ROLL_KEY, JSONObject().put("eligible", eligible)
      .put("success", eligible && ticket.getInt("streak") < HiddenExitStreak.SUCCESS_PERCENT))
      .apply { put("levelExit", JSONObject().put("eligible", HiddenExitStreak.projectedReady(before, this))
        .put("success", HiddenExitStreak.projectedReady(before, this))) }
  }
  fun activeRolls(state: GameState): JSONObject = requireNotNull(active(state)).getJSONObject("rolls")

  /** Choice semantics are whitelisted intents, never model-authored commands or mutations. */
  private fun action(intent: String, destination: String = ""): String = when (intent) {
    "EXPLORE" -> "Cao Minh bước tiếp và khảo sát khu vực hiện tại."
    "INVESTIGATE" -> "Cao Minh quan sát kỹ và khảo sát dấu vết trong khu vực hiện tại."
    "AVOID" -> "Cao Minh bước theo hướng khác và thăm dò trong khu vực hiện tại."
    "TRAVERSE" -> "Cao Minh đi qua lối thoát đến $destination."
    else -> error("decision_intent_invalid")
  }
  fun prepare(state: GameState, request: JSONObject): GameState {
    val d = data(state)
    val id = request.getString("requestId")
    active(state)?.let {
      require(it.getString("requestId") == id && listOf("requestId", "gateId", "optionId").all { key ->
        it.getJSONObject("request").optString(key) == request.optString(key)
      }) { "another_page_pending" }
      return state
    }
    require(id == requestId(state)) { "stale_page_request" }
    require(idle(state)) { "encounter_or_turn_pending" }
    require(!requiresSettings(state)) { "level_settings_required" }
    val gate = d.optJSONObject("gate")
    var intent = "EXPLORE"
    var destination = ""
    var selected: JSONObject? = null
    if (gate != null) {
      require(request.optString("gateId") == gate.getString("id")) { "decision_required" }
      selected = (0 until gate.getJSONArray("options").length()).map { gate.getJSONArray("options").getJSONObject(it) }
        .singleOrNull { it.getString("id") == request.optString("optionId") } ?: error("decision_option_invalid")
      intent = selected.getString("intent")
      destination = selected.optString("destination")
      if (intent == "TRAVERSE") require(exitReady(state) && LevelRouteRegistry.canMove(node(state), destination)) { "exit_not_ready" }
    } else require(!request.has("optionId") && !request.has("gateId")) { "decision_stale" }
    val plan = JSONObject().put("requestId", id).put("request", request).put("intent", intent)
      .put("action", action(intent, destination)).put("destination", destination)
    if (selected != null) plan.put("selected", selected).put("gateId", gate!!.getString("id"))
    val reserved = reserve(store(state, d.put("active", plan)))
    val result = spawnForActive(reserved)
    // Traversal is a validated plan but an Entity can interrupt it: no bypass of Battle Start.
    if (result.entityKey != null) plan.put("destination", "")
    plan.put("rolls", rolls(reserved, data(reserved).getJSONArray("tickets").getJSONObject(0), plan.getString("action"), result))
    return store(reserved, data(reserved).put("active", plan))
  }
  private fun exitReady(state: GameState) = HiddenExitStreak.ready(JSONObject().put("flags", JSONObject(state.world.getValue("flagsJson"))))

  /** No future events are applied. Hypothetical states exist only within this function. */
  fun forecast(state: GameState): JSONArray {
    if (CombatRuntime.pending(state) != null || CombatRuntime.active(state) != null) return JSONArray()
    val tickets = data(state).optJSONArray("tickets") ?: return JSONArray()
    var projected = state
    val out = JSONArray()
    for (index in 0 until minOf(2, tickets.length())) {
      val a = if (index == 0) active(state)?.optString("action") ?: action("EXPLORE") else action("EXPLORE")
      val spawn = spawns(projected, tickets.getJSONObject(index), a)
      val rolls = rolls(projected, tickets.getJSONObject(index), a, spawn)
      val before = JSONObject().put("flags", JSONObject(projected.world.getValue("flagsJson")))
      out.put(JSONObject().put("step", index + 1).put("nextEntityEncounter", spawn.entityKey != null)
        .put("streakEligible", HiddenExitStreak.rollEligible(rolls))
        .put("streakResult", if (HiddenExitStreak.rollEligible(rolls)) HiddenExitStreak.rollSucceeded(rolls) else JSONObject.NULL)
        .put("streakProjection", HiddenExitStreak.projectedStreak(before, rolls))
        .put("exitUnlockOnThisStep", HiddenExitStreak.newlyReady(before, rolls)))
      if (spawn.entityKey != null || spawn.npcEncounters.isNotEmpty()) break // presence/decision may invalidate the next plan
      val after = JSONObject(before.toString())
      HiddenExitStreak.apply(before, after, rolls, node(state), node(state))
      projected = projected.copy(world = projected.world + ("flagsJson" to after.getJSONObject("flags").toString()))
    }
    return out
  }
  fun context(state: GameState): String {
    val d = data(state)
    return """
      GM AUTO NARRATIVE: Viết một trang tiểu thuyết, tự sáng tác hành động và lời thoại thường của Cao Minh/NPC hiện diện.
      Trang phải thể hiện kế hoạch Core đã khóa. Đối thoại/quãng nghỉ ngắn xen kẽ không tự thêm thời gian, roll hoặc hồi phục.
      Dừng trước quyết định lớn, quan hệ/niềm tin, cứu/bỏ, chia nhóm hoặc đánh đổi khó đảo ngược; trả decision với 2–3 options.
      Mỗi option gồm label (ý định, không bảo đảm kết quả) và intent: INVESTIGATE, AVOID hoặc EXPLORE. Không dùng label để ban item/NPC/Exit.
      Không tạo decision khi Entity xuất hiện. Core tự tạo gate đi qua Exit khi đã unlock; không tự viết TRAVERSE.
      Không quyết Combat, HP, loot, Party hay chuyển Level. Chỉ kể Entity khi CORE_CONFIRMED_ENCOUNTER_EVENTS xác nhận.
      FORESHADOW_READ_ONLY chỉ là dự báo có điều kiện, không phải sự kiện đã xảy ra. Chỉ gieo dấu hiệu; không lộ RNG/streak/số roll/metadata.
      Không nhắc nhân vật vắng mặt như đang hiện diện. Phân biệt nghi vấn và sự thật. Không biến điều tra thành chắc chắn tìm được người.
      Có thể thêm continuity: tối đa 3 ghi chú {"kind":"OBSERVATION|SUSPICION|PROMISE","text":"trích nguyên một chi tiết quan trọng trong reply, tối đa 240 ký tự"}.
      Ghi chú chỉ là ký ức kể chuyện của Cao Minh, không cấp tri thức cho NPC và không xác lập sự thật gameplay.
      OUTPUT: một JSON object {"reply":"trang truyện", "decision":null} hoặc decision={"options":[{"label":"...","intent":"INVESTIGATE"},{"label":"...","intent":"AVOID"}]}.
      TONE_PRESENTATION_ONLY: ${tones.getValue(d.optString("tone", "HORROR"))}. Không sửa Canon/tính cách/quy tắc vì đổi giọng.
      CORE_STEP_PLAN: ${active(state)?.let { JSONObject().put("intent", it.getString("intent")).put("action", it.getString("action"))
        .put("authorizedDestination", it.optString("destination")).put("selected", it.optJSONObject("selected")) }}
      Nếu authorizedDestination rỗng, không được kể đã đi qua Exit dù intent là TRAVERSE: Encounter có thể đã chặn đường.
      FORESHADOW_READ_ONLY: ${forecast(state)}
      Các quyết định là ký ức Cao Minh; không cấp tri thức cho NPC vắng mặt. NPC chỉ phản ứng khi đã chứng kiến hoặc được kể rõ trong truyện.
      PRIOR_DECISIONS_READ_ONLY: ${d.optJSONArray("decisions") ?: JSONArray()}
      NARRATIVE_MEMORY_READ_ONLY (chỉ ký ức Cao Minh; không phải fact Core hay tri thức NPC): ${memoryNotes(state)}
      RECENT_NARRATIVE_READ_ONLY (không phải authority gameplay, nghi vấn vẫn là nghi vấn): ${recentPages(d)}
    """.trimIndent()
  }
  private fun recentPages(data: JSONObject): JSONArray {
    val journal = data.optJSONArray("journal") ?: return JSONArray()
    return JSONArray().apply { for (i in maxOf(0, journal.length() - 4) until journal.length()) put(journal.get(i)) }
  }
  private fun memoryNotes(state: GameState): JSONArray {
    val all = JSONObject(state.world.getValue("flagsJson")).optJSONObject("storyContinuity")?.optJSONArray("narrativeNotes") ?: return JSONArray()
    val words = (active(state)?.optJSONObject("selected")?.optString("label").orEmpty() + " " +
      state.characters.values.filter { it.presence == CharacterPresence.ACTIVE && (it.id in state.party.memberIds || it.metadata["encountered"] == "true") }.joinToString(" ") { it.name })
      .lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 }.toSet()
    val relevant = (0 until maxOf(0, all.length() - 8)).sortedByDescending { index ->
      val text = all.getJSONObject(index).getString("text").lowercase()
      words.count { it in text }
    }.take(8)
    val indices = (relevant + (maxOf(0, all.length() - 8) until all.length()).toList()).distinct().sorted()
    return JSONArray().apply { indices.forEach { put(all.get(it)) } }
  }
  private fun validateNotes(page: JSONObject) {
    if (page.has("continuity") && !page.isNull("continuity")) {
      val notes = page.getJSONArray("continuity")
      require(notes.length() <= 3) { "narrative_notes_limit" }
      for (i in 0 until notes.length()) {
        val note = notes.getJSONObject(i)
        require(note.getString("kind") in setOf("OBSERVATION", "SUSPICION", "PROMISE")) { "narrative_note_kind_invalid" }
        val text = note.getString("text")
        require(text.length in 1..240 && page.getString("reply").contains(text)) { "narrative_note_not_in_page" }
        if (Regex("(?iu)có lẽ|có thể|dường như|lời đồn|nghi vấn").containsMatchIn(text))
          require(note.getString("kind") == "SUSPICION") { "narrative_uncertainty_required" }
      }
    }
  }
  fun validatePage(state: GameState, generated: JSONObject): JSONObject? {
    require(generated.getString("reply").isNotBlank()) { "empty_page" }
    validateNotes(generated)
    val decision = if (generated.has("decision") && !generated.isNull("decision")) generated.getJSONObject("decision") else return null
    require(CombatRuntime.pending(state) == null) { "decision_during_encounter" }
    val raw = decision.getJSONArray("options")
    require(raw.length() in 2..3) { "decision_options_invalid" }
    val options = JSONArray()
    for (i in 0 until raw.length()) {
      val option = raw.getJSONObject(i)
      val intent = option.getString("intent")
      require(intent in setOf("EXPLORE", "INVESTIGATE", "AVOID")) { "decision_intent_invalid" }
      val label = option.getString("label").trim()
      require(label.length in 1..240) { "decision_label_invalid" }
      options.put(JSONObject().put("id", ('A'.code + i).toChar().toString()).put("intent", intent).put("label", label))
    }
    require((0 until options.length()).map { options.getJSONObject(it).getString("label") }.distinct().size == options.length()) { "decision_options_duplicate" }
    return JSONObject().put("id", active(state)!!.getString("requestId")).put("options", options)
  }
  /** Called inside the existing Core commit, before the single durable save. */
  fun complete(before: GameState, after: GameState, page: JSONObject, gate: JSONObject?, presentation: JSONObject): GameState {
    val d = data(before)
    val a = requireNotNull(active(before))
    val journal = d.optJSONArray("journal") ?: JSONArray().also { list ->
      val log = presentation.optJSONArray("log") ?: JSONArray()
      for (i in 0 until log.length()) if (log.optJSONObject(i)?.optString("role") != "player") list.put(log.get(i))
    }
    val decisions = d.optJSONArray("decisions") ?: JSONArray()
    a.optJSONObject("selected")?.let { selected ->
      decisions.put(JSONObject().put("gateId", a.getString("gateId")).put("option", selected)
        .put("status", "confirmed_intent_not_guaranteed_outcome").put("ownerId", CAO_MINH_ID))
      journal.put(JSONObject().put("role", "gm").put("text", "Lựa chọn: " + selected.getString("label")))
    }
    journal.put(JSONObject().put("role", "gm").put("pageId", a.getString("requestId")).put("text", page.getString("reply"))
      .apply { CombatRuntime.pending(after)?.let { put("encounterId", it.encounterId) } })
    d.put("journal", journal).put("decisions", decisions).put("completedRequest", a.getString("requestId"))
    d.remove("active"); d.remove("gate")
    // Node boundaries, combat and new decisions replan eligibility, retaining unused tickets.
    val tickets = d.getJSONArray("tickets")
    tickets.remove(0)
    if (node(before) != node(after)) d.remove("configuredNode")
    var nextGate = gate
    if (nextGate == null && node(before) == node(after) && exitReady(after) && CombatRuntime.pending(after) == null) {
      val exits = LevelRouteRegistry.exits(node(after))
      if (exits.isNotEmpty()) {
        val options = JSONArray().put(JSONObject().put("id", "STAY").put("intent", "EXPLORE").put("label", "Tiếp tục khám phá tại đây"))
        exits.forEach { id -> options.put(JSONObject().put("id", "EXIT:$id").put("intent", "TRAVERSE").put("destination", id)
          .put("label", "Đi qua lối thoát đến " + LevelRouteRegistry.node(id)!!.name)) }
        nextGate = JSONObject().put("id", a.getString("requestId") + ":EXIT").put("options", options)
      }
    }
    if (nextGate != null && CombatRuntime.pending(after) == null) d.put("gate", nextGate)
    val flags = JSONObject(after.world.getValue("flagsJson"))
    val continuity = flags.optJSONObject("storyContinuity") ?: JSONObject().also { flags.put("storyContinuity", it) }
    val notes = continuity.optJSONArray("narrativeNotes") ?: JSONArray().also { continuity.put("narrativeNotes", it) }
    page.optJSONArray("continuity")?.let { incoming ->
      for (i in 0 until incoming.length()) notes.put(JSONObject().put("kind", incoming.getJSONObject(i).getString("kind"))
        .put("text", incoming.getJSONObject(i).getString("text")).put("pageId", a.getString("requestId"))
        .put("ownerId", CAO_MINH_ID).put("authority", "NARRATIVE_ONLY"))
    }
    return reserve(store(after.copy(world = after.world + ("flagsJson" to flags.toString())), d))
  }
  fun appendReceipt(state: GameState, text: String, presentation: JSONObject? = null): GameState {
    if (text.isBlank() || !state.metadata.containsKey(KEY)) return state
    val d = data(state)
    val journal = d.optJSONArray("journal") ?: presentation?.optJSONArray("log")?.let { log ->
      JSONArray().apply { for (i in 0 until log.length()) if (log.optJSONObject(i)?.optString("role") != "player") put(log.get(i)) }
    } ?: return state
    journal.put(JSONObject().put("role", "gm").put("text", text))
    return store(state, d.put("journal", journal))
  }
  fun project(out: JSONObject, state: GameState) {
    val d = data(state)
    out.remove("reader")
    val a = active(state)
    out.put("reader", JSONObject().put("requestId", requestId(state))
      .put("settingsRequired", requiresSettings(state) && idle(state))
      .put("tone", d.optString("tone", "HORROR")).put("difficulty", d.optString("difficulty", "NORMAL"))
      .put("pendingPage", a != null)
      .apply {
        d.optJSONObject("gate")?.let { put("gate", it) }
        a?.getJSONObject("request")?.let { put("retryRequest", it) }
      })
    d.optJSONArray("journal")?.let { out.put("log", JSONArray(it.toString())) }
  }
}
