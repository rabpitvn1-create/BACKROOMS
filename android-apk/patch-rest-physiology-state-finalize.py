from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
TURN = CORE / "TurnCoordinator.kt"
POLICY = CORE / "RestActionPolicy.kt"
TEST = TESTS / "RestActionPolicyTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


POLICY.write_text(r'''package com.rabpit.backroom.core

/** Deterministic authority for deciding whose sleep counter may be reset by a player rest action. */
object RestActionPolicy {
  private val restIntent = Regex(
    "(?:^|\\s)(?:ngủ|ngu|chợp\\s*mắt|chop\\s*mat|nghỉ\\s*ngơi|nghi\\s*ngoi|nghỉ\\s*tạm|nghi\\s*tam|sleep|nap|rest)(?:\\s|$)",
    RegexOption.IGNORE_CASE
  )
  private val partyRest = Regex(
    "(?:cả\\s*hai|ca\\s*hai|cả\\s*nhóm|ca\\s*nhom|cả\\s*party|ca\\s*party|mọi\\s*người|moi\\s*nguoi|luân\\s*phiên|luan\\s*phien|thay\\s*phiên|thay\\s*phien|chia\\s*ca)",
    RegexOption.IGNORE_CASE
  )

  fun targets(state: GameState, action: String): List<String> {
    if (!restIntent.containsMatchIn(action)) return emptyList()
    val activeParty = state.party.memberIds.distinct().filter { id ->
      state.characters[id]?.presence == CharacterPresence.ACTIVE
    }
    if (partyRest.containsMatchIn(action)) return activeParty

    val explicitFollowers = activeParty.filter { it != KAI_ID }.filter { id ->
      val character = state.characters[id] ?: return@filter false
      action.contains(id, ignoreCase = true) || action.contains(character.name, ignoreCase = true)
    }
    return (listOf(KAI_ID) + explicitFollowers).distinct().filter { it in activeParty }
  }
}
''', encoding="utf-8")

# Wire recovery at the transaction boundary rather than relying on the shape of GameCoreFacade.
# Several later authority patches rewrite the validated-Gemini path, but every committed gameplay
# turn still passes through TurnCoordinator. This keeps rest recovery atomic with elapsed time and
# guarantees the physiology state is updated before any caller can narrate a committed result.
turn = TURN.read_text(encoding="utf-8")
old_commit = '''    val executing = state.copy(turn = state.turn.copy(pending = pending.copy(
      status = PendingTurnStatus.EXECUTING,
      commandIds = commands.map { it.commandId }
    )))
    val execution = StateReducer.executeAll(executing, commands)
    if (!execution.applied && !commands.all { it is QueryCommand }) {
'''
new_commit = '''    val restCommands = if (
      commands.any { it is TimeAdvanceCommand } &&
      commands.none { it is PhysiologyCommand && it.operation == PhysiologyCommand.Operation.RECORD_SLEEP }
    ) {
      RestActionPolicy.targets(state, pending.input).mapIndexed { index, targetId ->
        PhysiologyCommand(
          commandId = "${pending.turnId}:SYSTEM:REST:$index",
          turnId = pending.turnId,
          actorId = KAI_ID,
          targetId = targetId,
          source = CommandSource.SYSTEM,
          operation = PhysiologyCommand.Operation.RECORD_SLEEP
        )
      }
    } else emptyList()
    val authoritativeCommands = commands + restCommands
    val executing = state.copy(turn = state.turn.copy(pending = pending.copy(
      status = PendingTurnStatus.EXECUTING,
      commandIds = authoritativeCommands.map { it.commandId }
    )))
    val execution = StateReducer.executeAll(executing, authoritativeCommands)
    if (!execution.applied && !authoritativeCommands.all { it is QueryCommand }) {
'''
turn = replace_once(turn, old_commit, new_commit, "Rest physiology transaction wiring")

for marker in (
    'RestActionPolicy.targets(state, pending.input)',
    'commands.any { it is TimeAdvanceCommand }',
    'operation = PhysiologyCommand.Operation.RECORD_SLEEP',
    'val authoritativeCommands = commands + restCommands',
    'commandIds = authoritativeCommands.map { it.commandId }',
    'StateReducer.executeAll(executing, authoritativeCommands)',
):
    if marker not in turn:
        raise RuntimeError("Rest physiology transaction contract missing: " + marker)
TURN.write_text(turn, encoding="utf-8")

TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class RestActionPolicyTest {
  private fun partyState(): GameState {
    val base = GameState.initial()
    val tiredKai = base.characters.getValue(KAI_ID).copy(
      physiology = PhysiologyState(minutesSinceFood = 1000L, minutesSinceWater = 1000L, minutesAwake = 1900L)
    )
    val lucia = CharacterState(
      "lucia",
      "Lucia \"Lục\"",
      physiology = PhysiologyState(minutesSinceFood = 1000L, minutesSinceWater = 1000L, minutesAwake = 1900L)
    )
    return base.copy(
      characters = base.characters + (KAI_ID to tiredKai) + (lucia.id to lucia),
      party = PartyState(memberIds = listOf(KAI_ID, lucia.id))
    )
  }

  @Test fun ordinaryKaiRestOnlyTargetsKai() {
    val state = partyState()
    assertEquals(listOf(KAI_ID), RestActionPolicy.targets(state, "Tôi chợp mắt một lúc"))
  }

  @Test fun explicitShiftRestTargetsActiveParty() {
    val state = partyState()
    assertEquals(listOf(KAI_ID, "lucia"), RestActionPolicy.targets(state, "Cả hai chia ca nghỉ ngơi và chợp mắt"))
  }

  @Test fun unrelatedWaitingDoesNotFakeSleepRecovery() {
    val state = partyState()
    assertTrue(RestActionPolicy.targets(state, "Đứng chờ và quan sát hành lang").isEmpty())
  }

  @Test fun coordinatorCommitsTimeThenKaiSleepRecoveryAtomically() {
    val state = partyState()
    val pending = TurnCoordinator.createPending(state, "TURN_REST", "Tôi ngủ một tiếng").state
    val time = TimeAdvanceCommand(
      "TURN_REST:SYSTEM:TIME",
      "TURN_REST",
      KAI_ID,
      source = CommandSource.SYSTEM,
      minutes = 60,
      reason = "player_action"
    )

    val result = TurnCoordinator.commit(pending, listOf(time))

    assertNull(result.error)
    val kai = result.state.characters.getValue(KAI_ID).physiology
    val lucia = result.state.characters.getValue("lucia").physiology
    assertEquals(0L, kai.minutesAwake)
    assertEquals(1960L, lucia.minutesAwake)
    assertEquals(1060L, kai.minutesSinceFood)
    assertEquals(1060L, kai.minutesSinceWater)
    assertEquals(100, PhysiologyStatusPolicy.restPercent(kai.minutesAwake))
    assertTrue("TURN_REST:SYSTEM:REST:0" in result.state.turn.executedCommandIds)
  }

  @Test fun coordinatorSharedShiftRestRecoversKaiAndLucia() {
    val state = partyState()
    val pending = TurnCoordinator.createPending(state, "TURN_SHIFT", "Cả hai chia ca nghỉ ngơi và chợp mắt một tiếng").state
    val time = TimeAdvanceCommand(
      "TURN_SHIFT:SYSTEM:TIME",
      "TURN_SHIFT",
      KAI_ID,
      source = CommandSource.SYSTEM,
      minutes = 60,
      reason = "player_action"
    )

    val result = TurnCoordinator.commit(pending, listOf(time))

    assertNull(result.error)
    assertEquals(0L, result.state.characters.getValue(KAI_ID).physiology.minutesAwake)
    assertEquals(0L, result.state.characters.getValue("lucia").physiology.minutesAwake)
    assertTrue("TURN_SHIFT:SYSTEM:REST:0" in result.state.turn.executedCommandIds)
    assertTrue("TURN_SHIFT:SYSTEM:REST:1" in result.state.turn.executedCommandIds)
  }
}
''', encoding="utf-8")

print("Rest physiology authority installed at TurnCoordinator: elapsed time commits first, then explicit sleep/rest resets authoritative sleep counters before the turn is exposed to narration.")
