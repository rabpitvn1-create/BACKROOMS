package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReaderRuntimeTest {
  private class MemorySave(var state: GameState) : SaveRepository {
    var slot: String? = null
    override fun save(state: GameState) { this.state = GameStateCodec.decode(GameStateCodec.encode(state)) }
    override fun load() = state
    override fun exists() = true
    override fun clear() { state = CoreClientProjection.initialize(GameState.initial()); slot = null }
    override fun saveCheckpoint(state: GameState, presentation: JSONObject) { slot = GameCheckpointCodec.encode(state, presentation) }
    override fun loadCheckpoint() = slot?.let(GameCheckpointCodec::decode)
  }
  private fun configured(): GameState = ReaderRuntime.configure(CoreClientProjection.initialize(GameState.initial()), listOf("HORROR"), "NORMAL")
  private fun ticket(entity: Int = 9999, streak: Int = 0) = JSONObject().put("entity", entity).put("streak", streak).put("npcRolls", JSONArray(List(6) { 9999 }))
  private fun fixed(state: GameState = configured(), entity: Int = 9999, streak: Int = 0): GameState =
    ReaderRuntime.store(state, ReaderRuntime.data(state).put("tickets", JSONArray().put(ticket(entity, streak)).put(ticket())))
  private fun request(state: GameState) = JSONObject().put("requestId", ReaderRuntime.requestId(state))
  private fun view(facade: GameCoreFacade) = facade.currentClientState("{\"log\":[]}")
  private fun page(reply: String = "Cao Minh quan sát hành lang.") = JSONObject().put("reply", reply)
  private fun gate() = JSONObject().put("options", JSONArray()
    .put(JSONObject().put("label", "Điều tra dấu vết có thể liên quan Lục Trầm").put("intent", "INVESTIGATE"))
    .put(JSONObject().put("label", "Tránh hướng có dấu vết").put("intent", "AVOID")))
  private fun commit(f: GameCoreFacade, req: JSONObject, generated: JSONObject = page()): JSONObject {
    val before = view(f)
    f.beginReaderPage(before, req.toString())
    return JSONObject(f.commitReaderPage(before, req.getString("requestId"), generated.toString())).getJSONObject("state")
  }
  @Test fun reservationIsTwoTicketsAndSurvivesCodecForecastAndRetriesWithoutRng() {
    var draws = 0
    val state = ReaderRuntime.reserve(configured()) { draws++; it - 1 }
    assertEquals(16, draws)
    val restored = GameStateCodec.decode(GameStateCodec.encode(state))
    ReaderRuntime.reserve(restored) { error("must not draw again") }
    assertEquals(2, ReaderRuntime.forecast(restored).length())
    val req = request(restored)
    val pending = ReaderRuntime.prepare(restored, req)
    assertEquals(ReaderRuntime.data(pending).toString(), ReaderRuntime.data(ReaderRuntime.prepare(pending, req)).toString())
    assertEquals(ReaderRuntime.data(restored).getJSONArray("tickets").toString(), ReaderRuntime.data(pending).getJSONArray("tickets").toString())
    assertFalse(ReaderRuntime.activeRolls(pending).getJSONObject(HiddenExitStreak.ROLL_KEY).getBoolean("success"))
    assertFalse(JSONObject(CoreClientProjection.project(JSONObject(), pending).toString()).getJSONObject("reader").has("tickets"))
  }
  @Test fun spawnAndStreakThresholdsAreExactIndependentBaselineTickets() {
    for (entity in listOf(3499, 3500)) for (streak in listOf(49, 50)) {
      val pending = ReaderRuntime.prepare(fixed(entity = entity, streak = streak), request(configured()))
      val spawn = ReaderRuntime.spawnForActive(pending)
      assertEquals(entity == 3499, spawn.entityKey != null)
      val rolls = ReaderRuntime.activeRolls(pending)
      assertEquals(entity != 3499, HiddenExitStreak.rollEligible(rolls))
      assertEquals(entity != 3499 && streak == 49, HiddenExitStreak.rollSucceeded(rolls))
    }
  }
  @Test fun projectedUnlockCannotMutateRealStreakAndFailureResetsIt() {
    val state = fixed().let { base ->
      val flags = JSONObject(base.world.getValue("flagsJson")).put("exploration", JSONObject().put("hiddenExitStreak", 2))
      base.copy(world = base.world + ("flagsJson" to flags.toString()))
    }
    assertTrue(ReaderRuntime.forecast(state).getJSONObject(0).getBoolean("exitUnlockOnThisStep"))
    assertEquals(2, HiddenExitStreak.current(CoreClientProjection.project(JSONObject(), state)))
    val repo = MemorySave(fixed(state, streak = 99)); val f = GameCoreFacade.forTests(repo)
    val out = commit(f, request(repo.state))
    assertEquals(0, HiddenExitStreak.current(out)); assertEquals("0", ReaderRuntime.node(repo.state))
  }
  @Test fun providerFailureProcessRestartAndDuplicateCommitDoNotRerollOrAdvanceTwice() {
    val repo = MemorySave(fixed(entity = 0)); var f = GameCoreFacade.forTests(repo)
    val before = view(f); val req = request(repo.state)
    val start = JSONObject(f.beginReaderPage(before, req.toString()))
    val id = start.getJSONObject("pendingEntityEncounter").getString("encounterId")
    assertFalse(f.abortAction("provider_error"))
    f = GameCoreFacade.forTests(repo)
    val retry = JSONObject(f.beginReaderPage(before, req.toString()))
    assertEquals(id, retry.getJSONObject("pendingEntityEncounter").getString("encounterId"))
    assertEquals(1, CoreClientProjection.turnIndex(repo.state))
    assertThrows(IllegalArgumentException::class.java) { f.commitReaderPage(before, req.getString("requestId"), page().put("decision", gate()).toString()) }
    val out = JSONObject(f.commitReaderPage(before, req.getString("requestId"), page().toString())).getJSONObject("state")
    assertEquals(2, out.getInt("turn")); assertEquals(id, out.getJSONArray("log").getJSONObject(0).getString("encounterId"))
    assertEquals(id, out.getJSONObject("pendingEncounter").getString("encounterId"))
    val replay = JSONObject(f.beginReaderPage(before, req.toString()))
    assertTrue(replay.getBoolean("replayed")); assertEquals(2, CoreClientProjection.turnIndex(repo.state))
    assertFalse(HiddenExitStreak.rollEligible(start.getJSONObject("rolls")))
  }
  @Test fun choiceGateValidatesOneBranchAndPersistsIntentionNotACanonReward() {
    for (optionId in listOf("A", "B")) {
      val repo = MemorySave(fixed()); val f = GameCoreFacade.forTests(repo)
      val first = commit(f, request(repo.state), page().put("decision", gate()))
      val gateId = first.getJSONObject("reader").getJSONObject("gate").getString("id")
      assertThrows(IllegalArgumentException::class.java) { f.beginReaderPage(first.toString(), request(repo.state).toString()) }
      val invalid = request(repo.state).put("gateId", gateId).put("optionId", "Z")
      assertThrows(IllegalStateException::class.java) { f.beginReaderPage(first.toString(), invalid.toString()) }
      val req = request(repo.state).put("gateId", gateId).put("optionId", optionId)
      f.beginReaderPage(first.toString(), req.toString())
      val switched = request(repo.state).put("gateId", gateId).put("optionId", if (optionId == "A") "B" else "A")
      assertThrows(IllegalArgumentException::class.java) { f.beginReaderPage(first.toString(), switched.toString()) }
      f.commitReaderPage(first.toString(), req.getString("requestId"), page().toString())
      val d = ReaderRuntime.data(repo.state)
      assertEquals(1, d.getJSONArray("decisions").length())
      assertEquals(optionId, d.getJSONArray("decisions").getJSONObject(0).getJSONObject("option").getString("id"))
      assertEquals("0", ReaderRuntime.node(repo.state)); assertFalse(repo.state.characters[LUC_TRAM_ID]?.metadata?.get("encountered") == "true")
      assertTrue(JSONObject(f.saveManualCheckpoint(view(f))).getBoolean("ok"))
      val saved = ReaderRuntime.data(repo.state).toString()
      f.loadManualCheckpoint(); assertEquals(saved, ReaderRuntime.data(repo.state).toString())
    }
  }
  @Test fun exitUnlockRequiresSeparateExplicitChoiceAndNewLevelSettingsWithoutLosingHistory() {
    val initial = fixed().let { state -> state.copy(world = state.world + ("flagsJson" to
      JSONObject(state.world.getValue("flagsJson")).put("exploration", JSONObject().put("hiddenExitStreak", 2)).toString())) }
    val repo = MemorySave(initial); val f = GameCoreFacade.forTests(repo)
    val out = commit(f, request(repo.state))
    assertTrue(HiddenExitStreak.ready(out)); assertEquals("0", ReaderRuntime.node(repo.state))
    val gate = out.getJSONObject("reader").getJSONObject("gate")
    val option = gate.getJSONArray("options").getJSONObject(1)
    val next = request(repo.state).put("gateId", gate.getString("id")).put("optionId", option.getString("id"))
    val transitioned = commit(f, next)
    assertEquals(option.getString("destination"), ReaderRuntime.node(repo.state))
    assertTrue(transitioned.getJSONObject("reader").getBoolean("settingsRequired"))
    assertTrue(ReaderRuntime.data(repo.state).getJSONArray("journal").length() >= 3)
    assertEquals(0, HiddenExitStreak.current(transitioned))
  }
  @Test fun checkpointBeforeFirstPageFreezesItsReservationAndPendingPagesCannotBeSaved() {
    val repo = MemorySave(configured()); val f = GameCoreFacade.forTests(repo)
    assertTrue(JSONObject(f.saveManualCheckpoint(view(f))).getBoolean("ok"))
    val frozen = ReaderRuntime.data(repo.state).getJSONArray("tickets").toString()
    val req = request(repo.state)
    f.beginReaderPage(view(f), req.toString())
    val first = ReaderRuntime.activeRolls(repo.state).toString()
    assertFalse(JSONObject(f.saveManualCheckpoint(view(f))).getBoolean("ok"))
    // A completed page may be deliberately rewound; the checkpoint's unused tickets
    // are restored exactly rather than drawn again.
    f.commitReaderPage(view(f), req.getString("requestId"), page().toString())
    assertTrue(JSONObject(f.loadManualCheckpoint()).getBoolean("ok"))
    assertEquals(frozen, ReaderRuntime.data(repo.state).getJSONArray("tickets").toString())
    f.beginReaderPage(view(f), req.toString())
    assertEquals(first, ReaderRuntime.activeRolls(repo.state).toString())
  }
  @Test fun narrativeNotesPreserveUncertaintyAndCannotGrantWorldStateOrNpcKnowledge() {
    val repo = MemorySave(fixed()); val f = GameCoreFacade.forTests(repo)
    val req = request(repo.state); val before = view(f)
    f.beginReaderPage(before, req.toString())
    val text = "Dấu vết có thể thuộc về Lục Trầm."
    val note = JSONObject().put("kind", "OBSERVATION").put("text", text)
    f.commitReaderPage(before, req.getString("requestId"), page(text).put("continuity", JSONArray().put(note)).toString())
    val saved = JSONObject(repo.state.world.getValue("flagsJson")).getJSONObject("storyContinuity").getJSONArray("narrativeNotes").getJSONObject(0)
    assertEquals("NARRATIVE_ONLY", saved.getString("authority")); assertEquals(CAO_MINH_ID, saved.getString("ownerId"))
    assertEquals("SUSPICION", saved.getString("kind")); assertEquals("0", ReaderRuntime.node(repo.state))
    assertTrue(ReaderRuntime.context(repo.state).contains(text))
    assertFalse(repo.state.characters.getValue(LUC_TRAM_ID).metadata["encountered"] == "true")
  }
  @Test fun invalidOptionalNotesCannotRejectASelectedBranchOrRerollItsStep() {
    val repo = MemorySave(fixed()); val f = GameCoreFacade.forTests(repo)
    val first = commit(f, request(repo.state), page().put("decision", gate()))
    val gateId = first.getJSONObject("reader").getJSONObject("gate").getString("id")
    val req = request(repo.state).put("gateId", gateId).put("optionId", "B")
    val before = view(f); f.beginReaderPage(before, req.toString())
    val nextTicket = ReaderRuntime.data(repo.state).getJSONArray("tickets").getJSONObject(1).toString()
    val generated = page().put("continuity", JSONArray().put(JSONObject().put("kind", "OBSERVATION").put("text", "Một chi tiết không xuất hiện trong trang.")))
    val result = JSONObject(f.commitReaderPage(before, req.getString("requestId"), generated.toString()))
    assertTrue(result.getBoolean("handled")); assertEquals(1, result.getInt("readerNotesRejected"))
    assertEquals(3, CoreClientProjection.turnIndex(repo.state))
    assertEquals(nextTicket, ReaderRuntime.data(repo.state).getJSONArray("tickets").getJSONObject(0).toString())
    assertEquals("B", ReaderRuntime.data(repo.state).getJSONArray("decisions").getJSONObject(0).getJSONObject("option").getString("id"))
    assertEquals(0, JSONObject(repo.state.world.getValue("flagsJson")).getJSONObject("storyContinuity").getJSONArray("narrativeNotes").length())
    assertTrue(JSONObject(f.beginReaderPage(before, req.toString())).getBoolean("replayed"))
    assertEquals(3, CoreClientProjection.turnIndex(repo.state))
  }
  @Test fun groundedNotesNormalizeTypographyButRejectParaphraseAndMalformedMetadata() {
    val text = "Cao Minh nghe “tiếng gọi”. Dấu vết có thể là giả."
    val notes = JSONArray()
      .put(JSONObject().put("kind", "OBSERVATION").put("text", "Cao Minh nghe \"tiếng gọi\"."))
      .put(JSONObject().put("kind", "OBSERVATION").put("text", "Dấu vết có thể là giả."))
      .put(JSONObject().put("kind", "OBSERVATION").put("text", "Một âm thanh bí ẩn vang lên."))
      .put("bad note")
    val grounded = ReaderRuntime.groundedNotes(page(text).put("continuity", notes))
    assertEquals(2, grounded.accepted.length()); assertEquals(2, grounded.rejected)
    assertEquals("SUSPICION", grounded.accepted.getJSONObject(1).getString("kind"))
    assertEquals(1, ReaderRuntime.groundedNotes(page(text).put("continuity", "bad container")).rejected)
  }
  @Test fun multipleStylesRoundTripAndNeverAlterGameplayRules() {
    val base = CoreClientProjection.initialize(GameState.initial())
    val configured = ReaderRuntime.configure(base, listOf("HORROR", "BODY_HORROR", "SURVIVAL"), "HARD")
    val restored = GameStateCodec.decode(GameStateCodec.encode(configured))
    assertEquals(listOf("HORROR", "BODY_HORROR", "SURVIVAL"), ReaderRuntime.selectedStyles(restored))
    val view = CoreClientProjection.project(JSONObject(), restored)
    assertEquals(3, view.getJSONObject("reader").getJSONArray("styles").length())
    assertTrue(ReaderRuntime.context(restored).contains("Body Horror"))
    assertEquals(ReaderRuntime.Difficulty.HARD, ReaderRuntime.difficulty(restored))
    assertEquals(base.world, configured.world); assertEquals(base.characters, configured.characters)
    for (styles in listOf(emptyList(), listOf("BAD"), listOf("HORROR", "HORROR")))
      assertThrows(IllegalArgumentException::class.java) { ReaderRuntime.configure(base, styles, "NORMAL") }
  }
  @Test fun difficultyIncreasesRealCounterattackDamageAndDoesNotForceLoss() {
    val base = CoreClientProjection.initialize(GameState.initial())
    fun combat(difficulty: String, seed: Int): GameState {
      val configured = ReaderRuntime.configure(base, listOf("HORROR"), difficulty)
      val state = CombatRuntime.start(configured, "hound")
      return state.copy(metadata = state.metadata + ("combat.seed" to seed.toString()))
    }
    var observedStrongerHit = false
    for (seed in 1..40) {
      val normal = CombatRuntime.resolve(combat("NORMAL", seed), "EXECUTE", "phòng thủ")
      val hard = CombatRuntime.resolve(combat("HARD", seed), "EXECUTE", "phòng thủ")
      val cmd = CombatRuntime.resolve(combat("CAO_MINH_MUST_DIE", seed), "EXECUTE", "phòng thủ")
      val hpNormal = normal.state.characters.getValue(CAO_MINH_ID).vitalState.currentHp
      val hpHard = hard.state.characters.getValue(CAO_MINH_ID).vitalState.currentHp
      val hpCmd = cmd.state.characters.getValue(CAO_MINH_ID).vitalState.currentHp
      assertTrue(hpHard <= hpNormal); assertTrue(hpCmd <= hpHard)
      if (hpHard < hpNormal && hpCmd < hpHard) observedStrongerHit = true
    }
    assertTrue("Hard/CMD must cause measurable Core damage", observedStrongerHit)
    for (difficulty in listOf("NORMAL", "HARD", "CAO_MINH_MUST_DIE")) {
      val canWin = (1..40).any { seed ->
        val state = combat(difficulty, seed).let { it.copy(metadata = it.metadata + ("combat.entityHp" to "1")) }
        CombatRuntime.resolve(state, "EXECUTE", "tấn công").entityDestroyed
      }
      assertTrue("A legal finishing attack can win on $difficulty", canWin)
    }
  }
  @Test fun toneNeverChangesGameplayAndDifficultyChangesActualCombatWithoutEditingSkills() {
    val baseline = CoreClientProjection.initialize(GameState.initial())
    val normal = ReaderRuntime.configure(baseline, listOf("HORROR"), "NORMAL")
    val bright = ReaderRuntime.configure(baseline, listOf("BRIGHT"), "NORMAL")
    val hard = ReaderRuntime.configure(baseline, listOf("HORROR"), "HARD")
    val cmd = ReaderRuntime.configure(baseline, listOf("HORROR"), "CAO_MINH_MUST_DIE")
    val baseCombat = CombatRuntime.active(CombatRuntime.start(baseline, "hound"))!!
    val normalCombat = CombatRuntime.active(CombatRuntime.start(normal, "hound"))!!
    assertEquals(baseCombat, normalCombat)
    assertEquals(normalCombat, CombatRuntime.active(CombatRuntime.start(bright, "hound")))
    assertEquals(ReaderRuntime.scale(baseCombat.entityMaxHp, 125), CombatRuntime.active(CombatRuntime.start(hard, "hound"))!!.entityMaxHp)
    assertEquals(ReaderRuntime.scale(baseCombat.entityMaxHp, 150), CombatRuntime.active(CombatRuntime.start(cmd, "hound"))!!.entityMaxHp)
    assertEquals(baseline.characters, cmd.characters); assertEquals(baseline.equipment, cmd.equipment)
    for (state in listOf(normal, hard, cmd)) {
      val combat = CombatRuntime.start(state, "hound")
      assertEquals(CombatRuntime.active(combat), CombatRuntime.active(GameStateCodec.decode(GameStateCodec.encode(combat))))
      assertTrue(CombatRuntime.active(combat)!!.playerHp > 0)
    }
  }
}
