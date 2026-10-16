package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Regression tests for P0 level transition fixes in GameCoreFacade.
 *
 * P0 (original): processValidatedCandidate rejected ALL cross-parent moves, but valid
 * LevelRouteRegistry edges CAN change parent (e.g. red_rooms -> 1, 1 -> 2).
 *
 * P0-3: Core-authoritative destination decision. Core decides the destination node
 * via determineExitDestination (Core's own streak + Core's own dice + Core-parsed
 * intent + valid edges). GM/AI never chooses. processValidatedCandidate only accepts
 * a level change when determineExitDestination authorized it this turn (one-shot).
 * candidate.flags.lastRolls (client data) is never trusted.
 */
class LevelTransitionValidationTest {
  private class MemorySave(var state: GameState) : SaveRepository {
    override fun save(state: GameState) { this.state = state }
    override fun load() = state
    private var checkpoint: GameCheckpoint? = null
    override fun saveCheckpoint(state: GameState, presentation: JSONObject) {
      checkpoint = GameCheckpoint(state, JSONObject(presentation.toString()))
    }
    override fun loadCheckpoint(): GameCheckpoint? = checkpoint
    override fun exists() = true
    override fun clear() { checkpoint = null }
  }

  private fun facade(save: MemorySave): GameCoreFacade {
    val constructor = GameCoreFacade::class.java.getDeclaredConstructor(SaveRepository::class.java, GamePipelineLogger::class.java)
    constructor.isAccessible = true
    return constructor.newInstance(save, NoOpGamePipelineLogger)
  }

  /** Core state at a node, with the AUTHORITATIVE streak in Core's flagsJson. */
  private fun freshAt(nodeId: String, streak: Int = 0): GameState {
    val node = LevelRouteRegistry.node(nodeId)!!
    val base = CoreClientProjection.initialize(GameState.initial())
    val flags = JSONObject()
      .put("exploration", JSONObject().put(HiddenExitStreak.STREAK_KEY, streak))
    return base.copy(world = base.world +
      ("levelJson" to node.asJson().toString()) +
      ("flagsJson" to flags.toString()))
  }

  private fun beforeJson(turn: Int): String =
    JSONObject().put("turn", turn).toString()

  private fun candidateJson(levelId: String): String {
    val node = LevelRouteRegistry.node(levelId)!!
    return JSONObject()
      .put("level", node.asJson())
      .put("location", node.location())
      .put("title", node.title())
      .put("flags", JSONObject().put("currentLevel", node.asJson()))
      .toString()
  }

  /** Run the real flow: Core authorizes destination, then candidate is validated. */
  private fun authorize(core: GameCoreFacade, turn: Int, kind: String, action: String): JSONObject =
    JSONObject(core.determineExitDestination(beforeJson(turn), kind, action))

  // --- P0-3: determineExitDestination — Core decides ---

  @Test fun coreAuthorizesLevel0ToNextArea() {
    // P0-3 required: Level 0 -> next area. Core at "0", streak=3 (ready).
    // Action names the destination ("Zenith Station" = node "0.1").
    val save = MemorySave(freshAt("0", streak = HiddenExitStreak.REQUIRED_STREAK))
    val core = facade(save)
    val dest = authorize(core, turn = 1, kind = "EXECUTE", action = "bước qua cửa đến Zenith Station")
    assertEquals("0.1", dest.optString("destinationId"))
  }

  @Test fun coreAuthorizesCrossLevelTransition() {
    // P0-3 required: cross-Level transition. Core at "red_rooms" (parent 0),
    // single forward edge to "1" (parent 1). Generic traverse suffices.
    val save = MemorySave(freshAt("red_rooms", streak = HiddenExitStreak.REQUIRED_STREAK))
    val core = facade(save)
    val dest = authorize(core, turn = 1, kind = "EXECUTE", action = "bước qua cửa đỏ")
    assertEquals("1", dest.optString("destinationId"))
  }

  @Test fun coreRejectsReturnToPreviousParentLevel() {
    val saved = MemorySave(freshAt("2", streak = HiddenExitStreak.REQUIRED_STREAK))
    val core = facade(saved)
    val action = "đi qua cửa trở lại Level 1"
    val decision = authorize(core, turn = 1, kind = "EXECUTE", action = action)
    assertTrue(decision.toString(), decision.isNull("destinationId"))
    val rejected = JSONObject(core.processValidatedCandidate(
      beforeJson(turn = 1), candidateJson("1"), action))
    assertFalse(rejected.toString(), rejected.getBoolean("handled"))
    assertEquals("level_change_forbidden", rejected.getString("error"))
  }

  @Test fun coreCanReturnFromSublevelToAreaWithinSameParentLevel() {
    val saved = MemorySave(freshAt("1.2", streak = HiddenExitStreak.REQUIRED_STREAK))
    val core = facade(saved)
    val decision = authorize(core, turn = 1, kind = "EXECUTE",
      action = "đi qua cửa về Level 1")
    assertEquals("1", decision.optString("destinationId"))
  }

  @Test fun parentLevelExitSeparatesEncounteredNpcButKeepsHerMemory() {
    val initial = freshAt("red_rooms", streak = HiddenExitStreak.REQUIRED_STREAK)
    val lucia = CharacterState(id = "lucia", name = "Lucia",
      presence = CharacterPresence.ACTIVE,
      metadata = mapOf("encountered" to "true"))
    val encountered = initial.copy(characters = initial.characters + ("lucia" to lucia))
    val flagged = JSONObject(encountered.world.getValue("flagsJson"))
      .put("lucia", JSONObject().put("present", true).put("spawned", true))
    val withBag = CharacterMemoryBags.recordCommitted(initial, encountered).copy(
      world = encountered.world + ("flagsJson" to flagged.toString()))
    assertTrue(CharacterMemoryBags.sceneContext(withBag).contains("Actor lucia"))

    val save = MemorySave(withBag)
    val core = facade(save)
    val action = "bước qua cửa đỏ"
    assertEquals("1", authorize(core, 1, "EXECUTE", action).optString("destinationId"))
    val result = JSONObject(core.processValidatedCandidate(beforeJson(1), candidateJson("1"), action))
    assertTrue(result.toString(), result.getBoolean("handled"))
    assertEquals(CharacterPresence.SEPARATED, save.state.characters.getValue("lucia").presence)
    assertFalse(JSONObject(save.state.world.getValue("flagsJson"))
      .getJSONObject("lucia").getBoolean("present"))
    assertEquals("", CharacterMemoryBags.sceneContext(save.state))
    assertEquals(withBag.metadata["core.characterMemoryBags.v1"],
      save.state.metadata["core.characterMemoryBags.v1"])
  }

  @Test fun newlyMetNpcOnDestinationLevelIsNotSeparatedAsPreviousLevelCompanion() {
    val initial = freshAt("red_rooms", streak = HiddenExitStreak.REQUIRED_STREAK)
    val staged = SpawnMechanism.prepareNpcEncounters(initial,
      listOf(NpcSpawnTable.NpcEncounter(LUCIA_ID, "lucia")))
    val save = MemorySave(staged)
    val core = facade(save)
    val action = "bước qua cửa đỏ"
    assertEquals("1", authorize(core, 1, "EXECUTE", action).optString("destinationId"))
    val result = JSONObject(core.processValidatedCandidate(beforeJson(1), candidateJson("1"), action))
    assertTrue(result.toString(), result.getBoolean("handled"))
    assertEquals(CharacterPresence.ACTIVE, save.state.characters.getValue(LUCIA_ID).presence)
    assertTrue(JSONObject(save.state.world.getValue("flagsJson"))
      .getJSONObject("lucia").getBoolean("present"))
    assertTrue(CharacterMemoryBags.sceneContext(save.state).contains("Actor lucia"))
  }

  @Test fun confirmedPartyMemberStaysActiveAcrossParentLevelExit() {
    val initial = freshAt("red_rooms", streak = HiddenExitStreak.REQUIRED_STREAK)
    val lucia = CharacterState(id = "lucia", name = "Lucia",
      presence = CharacterPresence.ACTIVE,
      metadata = mapOf("encountered" to "true", "encounterFlagKey" to "lucia"))
    val current = initial.copy(
      characters = initial.characters + ("lucia" to lucia),
      party = initial.party.copy(memberIds = initial.party.memberIds + "lucia"))
    val save = MemorySave(current)
    val core = facade(save)
    val action = "bước qua cửa đỏ"
    assertEquals("1", authorize(core, 1, "EXECUTE", action).optString("destinationId"))
    val result = JSONObject(core.processValidatedCandidate(beforeJson(1), candidateJson("1"), action))
    assertTrue(result.toString(), result.getBoolean("handled"))
    assertEquals(CharacterPresence.ACTIVE, save.state.characters.getValue("lucia").presence)
  }

  @Test fun coreRejectsWhenExitNotReady() {
    // P0-3 required: insufficient-condition case. Streak=0, without a ready Exit
    // -> Core's roll ineligible -> deterministic "exit_not_ready".
    val save = MemorySave(freshAt("0", streak = 0))
    val core = facade(save)
    val dest = authorize(core, turn = 1, kind = "SEARCH", action = "bước qua cửa đến Zenith Station")
    assertTrue(dest.isNull("destinationId"))
    assertEquals("exit_not_ready", dest.optString("reason"))
  }

  @Test fun coreRejectsWithoutTraversalIntent() {
    // Exit ready but the player is not traversing -> no destination.
    // GM must not move the player on a non-traversal action.
    val save = MemorySave(freshAt("0", streak = HiddenExitStreak.REQUIRED_STREAK))
    val core = facade(save)
    val dest = authorize(core, turn = 1, kind = "EXECUTE", action = "quan sát xung quanh căn phòng")
    assertTrue(dest.isNull("destinationId"))
    assertEquals("no_traversal_intent", dest.optString("reason"))
  }

  @Test fun coreMatchesNamedDestinationAmongMultipleExits() {
    // Core at "1": exits = [2, 1.2, 1.5, traders_vault, base_alpha].
    // Player names "Trader's Vault" -> Core picks it (not GM's choice).
    val save = MemorySave(freshAt("1", streak = HiddenExitStreak.REQUIRED_STREAK))
    val core = facade(save)
    val dest = authorize(core, turn = 1, kind = "EXECUTE", action = "đi vào Trader's Vault")
    assertEquals("traders_vault", dest.optString("destinationId"))
  }

  @Test fun coreRejectsAmbiguousDestination() {
    // Core at "1" with multiple exits and a generic traverse (no named destination).
    // Core never guesses -> null; GM prompts via gmRoutingContext.
    val save = MemorySave(freshAt("1", streak = HiddenExitStreak.REQUIRED_STREAK))
    val core = facade(save)
    val dest = authorize(core, turn = 1, kind = "EXECUTE", action = "bước qua cửa")
    assertTrue(dest.isNull("destinationId"))
    assertEquals("destination_ambiguous", dest.optString("reason"))
  }

  // --- processValidatedCandidate: accepts only Core-authorized transitions ---

  @Test fun validTransitionViaValidEdgeWithExitReadyIsAllowed() {
    // Full flow: Core authorizes 0 -> 0.1, candidate carries it, Core commits.
    val save = MemorySave(freshAt("0", streak = HiddenExitStreak.REQUIRED_STREAK))
    val core = facade(save)
    val dest = authorize(core, turn = 1, kind = "EXECUTE", action = "bước qua cửa đến Zenith Station")
    assertEquals("0.1", dest.optString("destinationId"))
    val result = JSONObject(core.processValidatedCandidate(
      beforeJson(turn = 1),
      candidateJson("0.1"),
      "bước qua cửa đến Zenith Station"))
    assertNotEquals("level_change_forbidden", result.optString("error"))
  }

  @Test fun crossParentValidEdgeIsAllowed() {
    // Full flow: Core authorizes red_rooms -> 1 (parent 0 -> parent 1).
    val save = MemorySave(freshAt("red_rooms", streak = HiddenExitStreak.REQUIRED_STREAK))
    val core = facade(save)
    val dest = authorize(core, turn = 1, kind = "EXECUTE", action = "bước qua cửa đỏ")
    assertEquals("1", dest.optString("destinationId"))
    val result = JSONObject(core.processValidatedCandidate(
      beforeJson(turn = 1),
      candidateJson("1"),
      "bước qua cửa đỏ"))
    assertNotEquals("level_change_forbidden", result.optString("error"))
  }

  @Test fun teleportToNonAdjacentNodeIsRejected() {
    // Candidate proposes "5" from "0" with NO Core authorization.
    // Even with exit ready, this teleport must be blocked.
    val save = MemorySave(freshAt("0", streak = HiddenExitStreak.REQUIRED_STREAK))
    val core = facade(save)
    // Note: no authorize() call — simulates a client bypassing determineExitDestination.
    val result = JSONObject(core.processValidatedCandidate(
      beforeJson(turn = 1),
      candidateJson("5"),
      "đi qua cửa"))
    assertFalse(result.getBoolean("handled"))
    assertEquals("level_change_forbidden", result.getString("error"))
  }

  @Test fun transitionWithoutExitReadyIsRejected() {
    // Streak=0 in Core, without a ready Exit -> determineExitDestination refuses.
    // A candidate that changes level anyway must be rejected.
    val save = MemorySave(freshAt("0", streak = 0))
    val core = facade(save)
    val dest = authorize(core, turn = 1, kind = "SEARCH", action = "đi qua cửa đến Zenith Station")
    assertTrue(dest.isNull("destinationId"))
    val result = JSONObject(core.processValidatedCandidate(
      beforeJson(turn = 1),
      candidateJson("0.1"),
      "đi qua cửa đến Zenith Station"))
    assertFalse(result.getBoolean("handled"))
    assertEquals("level_change_forbidden", result.getString("error"))
  }

  @Test fun levelRetentionWithoutChangeIsAllowed() {
    // Core at "0", candidate stays at "0" — no transition, must not be blocked.
    val save = MemorySave(freshAt("0", streak = 0))
    val core = facade(save)
    val result = JSONObject(core.processValidatedCandidate(
      beforeJson(turn = 1),
      candidateJson("0"),
      "quan sát xung quanh"))
    assertNotEquals("level_change_forbidden", result.optString("error"))
  }

  @Test fun inconsistentLocationDescriptionIsRejected() {
    // Core at "0" authorizes nothing for this turn; candidate proposes "0.1"
    // but location text describes "Level 5". The smuggled teleport must be blocked.
    val save = MemorySave(freshAt("0", streak = HiddenExitStreak.REQUIRED_STREAK))
    val core = facade(save)
    val node01 = LevelRouteRegistry.node("0.1")!!
    val candidate = JSONObject()
      .put("level", node01.asJson())
      .put("location", "Level 5 / Terror Hotel") // contradicts level "0.1"
      .put("title", node01.title())
      .put("flags", JSONObject().put("currentLevel", node01.asJson()))
    val result = JSONObject(core.processValidatedCandidate(
      beforeJson(turn = 1),
      candidate.toString(),
      "đi qua cửa"))
    assertFalse(result.getBoolean("handled"))
    assertEquals("level_change_forbidden", result.getString("error"))
  }

  @Test fun authorizationIsOneShot() {
    // Commit the authorized red_rooms -> Level 1 transition in turn 1.
    // A second transition from Level 1 -> Level 2 requires a new authorization.
    val save = MemorySave(freshAt("red_rooms", streak = HiddenExitStreak.REQUIRED_STREAK))
    val core = facade(save)
    val action = "bước qua cửa đỏ"
    val dest = authorize(core, turn = 1, kind = "EXECUTE", action = action)
    assertEquals("1", dest.optString("destinationId"))
    val first = JSONObject(core.processValidatedCandidate(
      beforeJson(turn = 1), candidateJson("1"), action))
    assertTrue(first.toString(), first.getBoolean("handled"))
    assertEquals("1", LevelRouteRegistry.currentId(CoreClientProjection.project(JSONObject(), save.state)))
    // Use Core's NEW turn to avoid confusing stale-client rejection with
    // authorization reuse; no determineExitDestination call for turn 2.
    val turn2 = CoreClientProjection.turnIndex(save.state)
    val replay = JSONObject(core.processValidatedCandidate(
      beforeJson(turn = turn2), candidateJson("2"), "bước qua cửa đến Level 2"))
    assertFalse(replay.getBoolean("handled"))
    assertEquals("level_change_forbidden", replay.getString("error"))
  }
}
