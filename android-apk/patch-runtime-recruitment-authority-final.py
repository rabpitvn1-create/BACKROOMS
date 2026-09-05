from pathlib import Path

ROOT = Path(__file__).resolve().parent
APP = ROOT / "app"
CORE = APP / "src/main/java/com/rabpit/backroom/core"
FACADE = CORE / "GameCoreFacade.kt"
INTENT = CORE / "IntentPipeline.kt"
COMMAND = CORE / "CommandPipeline.kt"
LUCIA = CORE / "LuciaCanon.kt"
TESTS = APP / "src/test/java/com/rabpit/backroom/core"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def replace_between(text: str, start: str, end: str, replacement: str, label: str) -> str:
    a = text.find(start)
    b = text.find(end, a + len(start)) if a >= 0 else -1
    if a < 0 or b < 0:
        raise RuntimeError(f"{label}: anchors not found")
    return text[:a] + replacement.rstrip() + "\n\n" + text[b:]


facade = FACADE.read_text(encoding="utf-8")
party_starts = [
    "    val desiredParty = candidate.optJSONArray(\"party\")",
    "    val desiredParty = jsonObjects(candidate.optJSONArray(\"party\"))",
]
world_command = "    commands += ValidatedLegacyStateCommand("
party_start = next((anchor for anchor in party_starts if anchor in facade), None)
if party_start is not None:
    facade = replace_between(
        facade,
        party_start,
        world_command,
        "    // Party membership is Runtime/Core authority. Candidate narrative cannot mutate Party.",
        "remove GM Party authority",
    )
if 'candidate.optJSONArray("party")' in facade:
    raise RuntimeError("GM Party candidate authority survived Runtime finalizer")

facade = facade.replace(
    '"kai" to KAI_ID, "iris" to "iris", "syvial" to "syvial", "lucia" to "lucia",',
    '"kai" to KAI_ID, "iris" to "iris", "syvial" to "syvial", "lucia" to LUCIA_ID, "lucia lục" to LUCIA_ID, "hứa thuý mai" to LUCIA_ID, "thuy mai" to LUCIA_ID,',
    1,
)
if '"lucia lục" to LUCIA_ID' not in facade:
    raise RuntimeError("Lucia canonical resolver aliases missing")
FACADE.write_text(facade, encoding="utf-8")

intent = INTENT.read_text(encoding="utf-8")
old_join_rule = 'Rule(GameIntent.PARTY_JOIN_REQUEST, Regex("(?:vào|gia nhập|tham gia)\\\\s+(?:party|đội|nhóm)|(?:mời|cho).*(?:gia nhập|tham gia)", RegexOption.IGNORE_CASE))'
new_join_rule = 'Rule(GameIntent.PARTY_JOIN_REQUEST, Regex("(?:\\\\b(?:mời|rủ)\\\\b.{0,48}\\\\b(?:gia nhập|tham gia|vào|đi cùng|đi theo)\\\\b|\\\\b(?:gia nhập|tham gia|vào)\\\\s+(?:party|đội|nhóm)\\\\b|\\\\b(?:đi cùng|đi theo|theo tôi|theo chúng tôi)\\\\b)", RegexOption.IGNORE_CASE))'
if new_join_rule not in intent:
    if old_join_rule not in intent:
        raise RuntimeError("Party join intent rule anchor missing")
    intent = intent.replace(old_join_rule, new_join_rule, 1)
INTENT.write_text(intent, encoding="utf-8")

command = COMMAND.read_text(encoding="utf-8")
old_join_command = '      GameIntent.PARTY_JOIN_REQUEST -> target?.let { PartyCommand(commandId, turnId, KAI_ID, it, source, PartyCommand.Operation.ADD) }'
new_join_command = '''      GameIntent.PARTY_JOIN_REQUEST -> target?.let { targetId ->
        val known = context.state.characters[targetId]
        val present = known?.presence == CharacterPresence.ACTIVE
        val eligible = known?.metadata?.get("joinEligible").equals("true", ignoreCase = true)
        PartyCommand(
          commandId, turnId, KAI_ID, targetId, source, PartyCommand.Operation.ADD,
          consentConfirmed = present && eligible,
          targetPresent = present
        )
      }'''
command = replace_once(command, old_join_command, new_join_command, "Runtime recruitment command")
COMMAND.write_text(command, encoding="utf-8")

lucia = LUCIA.read_text(encoding="utf-8")
if "import org.json.JSONObject" not in lucia:
    lucia = lucia.replace("package com.rabpit.backroom.core\n", "package com.rabpit.backroom.core\n\nimport org.json.JSONObject\n", 1)
if '"joinEligible" to "true"' not in lucia:
    lucia = replace_once(
        lucia,
        '      "inventoryProfile" to "lucia"\n',
        '      "inventoryProfile" to "lucia",\n      "joinEligible" to "true"\n',
        "Lucia join eligibility",
    )
old_ensure = '''  fun ensure(state: GameState): GameState {
    val character = state.characters[LUCIA_ID] ?: character()
    val inventory = state.inventories[LUCIA_ID] ?: inventory()
    val equipment = state.equipment[LUCIA_ID] ?: equipment()
    return state.copy(
      characters = state.characters + (LUCIA_ID to character),
      inventories = state.inventories + (LUCIA_ID to inventory),
      equipment = state.equipment + (LUCIA_ID to equipment)
    )
  }'''
new_ensure = '''  fun ensure(state: GameState): GameState {
    val seeded = state.characters[LUCIA_ID] ?: character()
    val flags = state.world["flagsJson"]?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
    val record = flags?.optJSONObject("lucia")
    val contactActive = LUCIA_ID in state.party.memberIds ||
      record?.optBoolean("present", false) == true ||
      record?.optBoolean("encountered", false) == true
    val resolvedPresence = if (contactActive && seeded.presence != CharacterPresence.DEAD) CharacterPresence.ACTIVE else seeded.presence
    val character = seeded.copy(presence = resolvedPresence)
    val inventory = state.inventories[LUCIA_ID] ?: inventory()
    val equipment = state.equipment[LUCIA_ID] ?: equipment()
    return state.copy(
      characters = state.characters + (LUCIA_ID to character),
      inventories = state.inventories + (LUCIA_ID to inventory),
      equipment = state.equipment + (LUCIA_ID to equipment)
    )
  }'''
lucia = replace_once(lucia, old_ensure, new_ensure, "Lucia authoritative contact projection")
LUCIA.write_text(lucia, encoding="utf-8")

TESTS.mkdir(parents=True, exist_ok=True)
(TESTS / "RuntimeAuthorityRegressionTest.kt").write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeAuthorityRegressionTest {
  private fun luciaActiveState(): GameState = LuciaCanon.ensure(GameState.initial().copy(
    world = mapOf("flagsJson" to "{\"lucia\":{\"encountered\":true,\"present\":true}}")
  ))

  @Test fun luciaContactBecomesAuthoritativelyActive() {
    val state = luciaActiveState()
    assertEquals(CharacterPresence.ACTIVE, state.characters.getValue(LUCIA_ID).presence)
    assertEquals("true", state.characters.getValue(LUCIA_ID).metadata["joinEligible"])
  }

  @Test fun naturalRecruitRequestResolvesToRuntimePartyCommand() {
    val state = luciaActiveState()
    val context = GameContext(state, mapOf("kai" to KAI_ID, "lucia" to LUCIA_ID, "lucia lục" to LUCIA_ID))
    val intent = RuleIntentInterpreter().interpretSync("Lucia đi cùng tôi", context).candidates.single()
    assertEquals(GameIntent.PARTY_JOIN_REQUEST, intent.intent)
    val command = CommandResolver().resolve(intent, 0, state.turn.currentTurnId, context) as? PartyCommand
    assertNotNull(command)
    assertTrue(command!!.consentConfirmed)
    assertTrue(command.targetPresent)
    val applied = PartyEngine.execute(state, command)
    assertTrue(applied.applied)
    assertTrue(LUCIA_ID in applied.state.party.memberIds)
  }

  @Test fun absentLuciaCannotBeFabricatedIntoParty() {
    val seeded = LuciaCanon.ensure(GameState.initial())
    val context = GameContext(seeded, mapOf("kai" to KAI_ID, "lucia" to LUCIA_ID))
    val intent = RuleIntentInterpreter().interpretSync("mời Lucia đi cùng tôi", context).candidates.single()
    val command = CommandResolver().resolve(intent, 0, seeded.turn.currentTurnId, context) as? PartyCommand
    assertNotNull(command)
    assertFalse(command!!.targetPresent)
    assertFalse(command.consentConfirmed)
    assertFalse(PartyEngine.execute(seeded, command).applied)
  }
}
''', encoding="utf-8")

if 'candidate.optJSONArray("party")' in FACADE.read_text(encoding="utf-8"):
    raise RuntimeError("GM candidate still controls Party")
print("Runtime recruitment authority applied: GM Party mutation removed and Lucia canonical recruitment is Core-owned.")
