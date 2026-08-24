from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
FACADE = CORE / "GameCoreFacade.kt"
POLICY = CORE / "RestActionPolicy.kt"
TEST = TESTS / "RestActionPolicyTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
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

facade = FACADE.read_text(encoding="utf-8")
old = '''    commands += timeAdvanceCommand(turnId, action)
'''
new = '''    commands += timeAdvanceCommand(turnId, action)
    commands += restPhysiologyCommands(turnId, action, pending.state)
'''
if facade.count(new) != 2:
    if facade.count(old) != 2:
        raise RuntimeError(f"Rest physiology command wiring: expected two time-advance anchors, found {facade.count(old)}")
    facade = facade.replace(old, new)

helper_anchor = '''  private fun timeAdvanceCommand(turnId: String, action: String): TimeAdvanceCommand = TimeAdvanceCommand(
'''
helper = '''  private fun restPhysiologyCommands(turnId: String, action: String, state: GameState): List<PhysiologyCommand> =
    RestActionPolicy.targets(state, action).mapIndexed { index, targetId ->
      PhysiologyCommand(
        commandId = "$turnId:SYSTEM:REST:$index",
        turnId = turnId,
        actorId = KAI_ID,
        targetId = targetId,
        source = CommandSource.SYSTEM,
        operation = PhysiologyCommand.Operation.RECORD_SLEEP
      )
    }

'''
if helper not in facade:
    if helper_anchor not in facade:
        raise RuntimeError("Rest physiology helper anchor missing")
    facade = facade.replace(helper_anchor, helper + helper_anchor, 1)

for marker in (
    'commands += restPhysiologyCommands(turnId, action, pending.state)',
    'private fun restPhysiologyCommands(',
    'operation = PhysiologyCommand.Operation.RECORD_SLEEP',
):
    if marker not in facade:
        raise RuntimeError("Rest physiology runtime contract missing: " + marker)
if facade.count('commands += restPhysiologyCommands(turnId, action, pending.state)') != 2:
    raise RuntimeError("Rest physiology must run in both rule and validated-Gemini commit paths")
FACADE.write_text(facade, encoding="utf-8")

TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class RestActionPolicyTest {
  private fun partyState(): GameState {
    val base = GameState.initial()
    val lucia = CharacterState("lucia", "Lucia \"Lục\"", physiology = PhysiologyState.freshRunBaseline())
    return base.copy(
      characters = base.characters + (lucia.id to lucia),
      party = PartyState(memberIds = listOf(KAI_ID, lucia.id))
    )
  }

  @Test fun ordinaryKaiRestOnlyResetsKai() {
    val state = partyState()
    assertEquals(listOf(KAI_ID), RestActionPolicy.targets(state, "Tôi chợp mắt một lúc"))
  }

  @Test fun explicitShiftRestIncludesActiveParty() {
    val state = partyState()
    assertEquals(listOf(KAI_ID, "lucia"), RestActionPolicy.targets(state, "Cả hai chia ca nghỉ ngơi và chợp mắt"))
  }

  @Test fun unrelatedWaitingDoesNotFakeSleepRecovery() {
    val state = partyState()
    assertTrue(RestActionPolicy.targets(state, "Đứng chờ và quan sát hành lang").isEmpty())
  }

  @Test fun sleepCommitRunsAfterTimeAndActuallyRecoversRestCounter() {
    val base = GameState.initial()
    val tiredKai = base.characters.getValue(KAI_ID).copy(
      physiology = PhysiologyState(minutesSinceFood = 1000L, minutesSinceWater = 1000L, minutesAwake = 1900L)
    )
    val state = base.copy(characters = base.characters + (KAI_ID to tiredKai))
    val time = TimeAdvanceCommand("TURN_1:SYSTEM:TIME", "TURN_1", KAI_ID, source = CommandSource.SYSTEM, minutes = 60, reason = "player_action")
    val sleep = PhysiologyCommand("TURN_1:SYSTEM:REST:0", "TURN_1", KAI_ID, source = CommandSource.SYSTEM, operation = PhysiologyCommand.Operation.RECORD_SLEEP)

    val result = StateReducer.executeAll(state, listOf(time, sleep))

    assertTrue(result.applied)
    val p = result.state.characters.getValue(KAI_ID).physiology
    assertEquals(0L, p.minutesAwake)
    assertEquals(1060L, p.minutesSinceFood)
    assertEquals(1060L, p.minutesSinceWater)
    assertEquals(100, PhysiologyStatusPolicy.restPercent(p.minutesAwake))
  }
}
''', encoding="utf-8")

print("Rest physiology authority installed: sleep/rest actions now commit RECORD_SLEEP after time advancement, with party-wide recovery only for explicit shared/shift rest.")
