from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
PARTY = CORE / "PartyTurnCombat.kt"
CATALOG = CORE / "CompanionSkillCatalog.kt"
INTERLEAVED_TEST = TESTS / "PartyTurnCombatInterleavedTest.kt"
AP_TEST = TESTS / "PartyTurnCombatApSkillAuthorityTest.kt"
COMBAT_TEST = TESTS / "CombatRuntimeTest.kt"
VERIFIER = ROOT / "ci_verify_runtime_contracts.py"

NORMAL_AP_COST = 2
ULTIMATE_AP_COST = 3


def require(path: Path) -> str:
    if not path.is_file():
        raise RuntimeError(f"AP skill authority missing generated file: {path.name}")
    return path.read_text(encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def remove_test_function(text: str, name: str) -> str:
    marker = f"  @Test fun {name}() {{"
    start = text.find(marker)
    if start < 0:
        return text
    next_test = text.find("\n  @Test fun ", start + len(marker))
    class_end = text.rfind("\n}")
    end = next_test if next_test >= 0 else class_end
    if end < 0:
        raise RuntimeError(f"Could not bound obsolete test {name}")
    return text[:start] + text[end:]


catalog = require(CATALOG)
normal_skills = {
    "The Last Requiem", "Silent Lullaby", "Salvation", "Quick Step",
    "Twosome Time", "Rain Storm", "Honeycomb Fire", "Charged Shot", "Dead Angle",
    "Rift Sever", "Crimson Guillotine", "Lucifer Breaker", "Counterphase", "Spatial Dominion",
    "M4A1 Full Auto Burst", "Too Young To Die",
}
ultimate_skills = {
    "Guilty Crown Override", "ARGUS // Thousandfold Execution",
    "GodKiller Override // Twenty-Four Severance",
}
lines = catalog.splitlines()
seen = set()
for index, line in enumerate(lines):
    match = re.match(r'(\s*s\(\")([^\"]+)(\",\s*\")([^\"]+)(\",\s*\")([^\"]*)(\".*)', line)
    if not match:
        continue
    name = match.group(2)
    if name in normal_skills:
        lines[index] = (
            match.group(1) + name + match.group(3) + "SKILL" + match.group(5)
            + f"Kích hoạt bằng {NORMAL_AP_COST} AP trong lượt của nhân vật" + match.group(7)
        )
        seen.add(name)
    elif name in ultimate_skills:
        lines[index] = (
            match.group(1) + name + match.group(3) + "ULTIMATE" + match.group(5)
            + f"Kích hoạt bằng {ULTIMATE_AP_COST} AP trong lượt của nhân vật" + match.group(7)
        )
        seen.add(name)
missing = sorted((normal_skills | ultimate_skills) - seen)
if missing:
    raise RuntimeError("AP skill catalog entries missing: " + ", ".join(missing))
catalog = "\n".join(lines) + ("\n" if catalog.endswith("\n") else "")
for forbidden in (
    "38% ở mỗi lượt hợp lệ", "27% ở mỗi lượt hợp lệ", "26% ở mỗi lượt hợp lệ",
    "35% ở mỗi lượt hợp lệ", "20% mỗi 2", "15% mỗi",
):
    if forbidden in catalog:
        raise RuntimeError("Legacy combat skill proc text survived: " + forbidden)
CATALOG.write_text(catalog, encoding="utf-8")

combat = require(COMBAT)
actor_anchor = '  private const val PARTY_TURN_ACTOR_CONTEXT_KEY = "partyCombat.actorContext"\n'
actor_insert = actor_anchor + '''  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"

  private fun partyTurnSkillName(state: GameState): String? =
    state.metadata[PARTY_TURN_SKILL_CONTEXT_KEY]?.trim()?.takeIf { it.isNotEmpty() }

  fun partyTurnSkillRejection(state: GameState, characterId: String, skillName: String): String? {
    if (characterId != SYVIAL_ID) return null
    val devilTrigger = state.metadata["combat.syvialDevilTrigger"]?.equals("true", ignoreCase = true) == true
    return if (skillName in setOf("Spatial Dominion", "GodKiller Override // Twenty-Four Severance") && !devilTrigger) {
      "${skillName} cần Devil Trigger đang hoạt động."
    } else null
  }
'''
if 'PARTY_TURN_SKILL_CONTEXT_KEY' not in combat:
    combat = replace_once(combat, actor_anchor, actor_insert, "Party selected-skill context")

combat = replace_once(
    combat,
    '        if (!partyTurnActorMatches(resolvedState, KAI_ID)) {\n',
    '        if (partyTurnSkillName(resolvedState) != null || !partyTurnActorMatches(resolvedState, KAI_ID)) {\n',
    "Kai base attack suppression during skill",
)
combat = replace_once(
    combat,
    '        val luciaActive = partyTurnActorMatches(resolvedState, LUCIA_ID) &&\n',
    '        val luciaActive = partyTurnSkillName(resolvedState) == null &&\n          partyTurnActorMatches(resolvedState, LUCIA_ID) &&\n',
    "Lucia base attack suppression during skill",
)
combat = combat.replace(
    'activePartyCharacter(resolvedState, IRIS_ID)?.takeIf { partyTurnActorMatches(resolvedState, IRIS_ID) }',
    'activePartyCharacter(resolvedState, IRIS_ID)?.takeIf { partyTurnSkillName(resolvedState) == null && partyTurnActorMatches(resolvedState, IRIS_ID) }',
)
combat = combat.replace(
    'activePartyCharacter(resolvedState, SYVIAL_ID)?.takeIf { partyTurnActorMatches(resolvedState, SYVIAL_ID) }',
    'activePartyCharacter(resolvedState, SYVIAL_ID)?.takeIf { partyTurnSkillName(resolvedState) == null && partyTurnActorMatches(resolvedState, SYVIAL_ID) }',
)

combat = combat.replace(
    'c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0',
    'partyTurnSkillName(resolvedState) == "Guilty Crown Override"',
)
for old, name in (
    ('roll(c.copy(eventCounter = c.eventCounter + 101), 100) < KAI_LAST_REQUIEM_CHANCE_PERCENT', 'The Last Requiem'),
    ('roll(c.copy(eventCounter = c.eventCounter + 113), 100) < KAI_SILENT_LULLABY_CHANCE_PERCENT', 'Silent Lullaby'),
    ('roll(c.copy(eventCounter = c.eventCounter + 127), 100) < KAI_SALVATION_CHANCE_PERCENT', 'Salvation'),
    ('roll(c.copy(eventCounter = c.eventCounter + 139), 100) < KAI_QUICK_STEP_CHANCE_PERCENT', 'Quick Step'),
):
    if old not in combat:
        raise RuntimeError(f"Legacy Kai proc gate missing before AP conversion: {name}")
    combat = combat.replace(old, f'partyTurnSkillName(resolvedState) == "{name}"')

combat = combat.replace(
    'c.eventCounter % IRIS_ULTIMATE_INTERVAL_TURNS == 0',
    'partyTurnSkillName(resolvedState) == "ARGUS // Thousandfold Execution"',
)
combat = combat.replace(
    'c.eventCounter % SYVIAL_ULTIMATE_INTERVAL_TURNS == 0',
    'partyTurnSkillName(resolvedState) == "GodKiller Override // Twenty-Four Severance"',
)
for old, name in (
    ('roll(c.copy(eventCounter = c.eventCounter + 151), 100) < 30', 'Twosome Time'),
    ('roll(c.copy(eventCounter = c.eventCounter + 163), 100) < 20', 'Rain Storm'),
    ('roll(c.copy(eventCounter = c.eventCounter + 179), 100) < 20', 'Honeycomb Fire'),
    ('roll(c.copy(eventCounter = c.eventCounter + 191), 100) < 25', 'Charged Shot'),
    ('roll(c.copy(eventCounter = c.eventCounter + 211), 100) < 30', 'Rift Sever'),
    ('roll(c.copy(eventCounter = c.eventCounter + 223), 100) < 20', 'Crimson Guillotine'),
    ('roll(c.copy(eventCounter = c.eventCounter + 239), 100) < 20', 'Lucifer Breaker'),
    ('roll(c.copy(eventCounter = c.eventCounter + 251), 100) < 20', 'Spatial Dominion'),
):
    if old not in combat:
        raise RuntimeError(f"Legacy companion proc gate missing before AP conversion: {name}")
    combat = combat.replace(old, f'partyTurnSkillName(resolvedState) == "{name}"')

counter_start = combat.find(
    '        if (irisActive && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 281), 100) < 15) {'
)
counter_end = combat.find('    if (c.entityHp <= 0) {', counter_start)
if counter_start < 0 or counter_end < 0:
    raise RuntimeError("Iris/Syvial automatic counter block missing")
manual_counters = '''    if (partyTurnActorMatches(resolvedState, IRIS_ID) &&
        partyTurnSkillName(resolvedState) == "Dead Angle" && c.entityHp > 0) {
      val damage = companionSkillDamage(CharacterStatEngine.weaponDamage(resolvedState, IRIS_ID), 120, profile.armor)
      val hp = max(0, c.entityHp - damage)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Dead Angle: Iris chủ động phản kích bằng 2 AP, 120% sát thương vũ khí = -$damage HP."
    }
    if (partyTurnActorMatches(resolvedState, SYVIAL_ID) &&
        partyTurnSkillName(resolvedState) == "Counterphase" && c.entityHp > 0) {
      val percent = if (syvialDevilTrigger) 157 else 125
      val damage = companionSkillDamage(CharacterStatEngine.weaponDamage(resolvedState, SYVIAL_ID), percent, profile.armor)
      val hp = max(0, c.entityHp - damage)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Counterphase: Syvial chủ động Spatial Shift bằng 2 AP, -$damage HP."
    }

'''
combat = combat[:counter_start] + manual_counters + combat[counter_end:]

an_start = combat.find('    if (anNhienActive && c.entityHp > 0) {')
an_end = combat.find('    if (syvialDisorientTurns > 0)', an_start)
if an_start >= 0 and an_end >= 0:
    combat = combat[:an_start] + '    // An Nhiên is non-combat; no automatic combat skill activation.\n\n' + combat[an_end:]

combat = combat.replace(
    'c.eventCounter % LUCIA_FULL_AUTO_INTERVAL_TURNS == 0',
    'partyTurnSkillName(resolvedState) == "M4A1 Full Auto Burst"',
)
combat = combat.replace('      val luciaFullAutoProc = roll(c.copy(eventCounter = c.eventCounter + 601), 100)\n', '')
combat = combat.replace(
    '      if (luciaFullAutoProc < LUCIA_FULL_AUTO_CHANCE_PERCENT) {\n',
    '      if (partyTurnSkillName(resolvedState) == "M4A1 Full Auto Burst") {\n',
)
combat = combat.replace('tự kích hoạt M4A1 Full Auto Burst', 'kích hoạt M4A1 Full Auto Burst bằng AP')

combat = re.sub(
    r'\n  internal fun luciaTooYoungToDieTriggerChancePercent\(currentHp: Int, maxHp: Int\): Int \{.*?\n  \}\n',
    '\n', combat, count=1, flags=re.S,
)
for line in (
    '  private const val LUCIA_TOO_YOUNG_TO_DIE_BASE_CHANCE_PERCENT = 15\n',
    '  private const val LUCIA_TOO_YOUNG_TO_DIE_LOW_HP_STEP_PERCENT = 3\n',
    '  private const val LUCIA_TOO_YOUNG_TO_DIE_LOW_HP_BONUS_PERCENT = 5\n',
):
    combat = combat.replace(line, '')
combat = re.sub(
    r'^\s*val luciaTooYoungChance = luciaTooYoungToDieTriggerChancePercent\([^\n]+\)\n',
    '', combat, count=1, flags=re.M,
)
combat = combat.replace('      val luciaTooYoungProc = roll(c.copy(eventCounter = c.eventCounter + 641), 100)\n', '')
combat = combat.replace(
    '      if (luciaTooYoungProc < luciaTooYoungChance) {\n',
    '      if (partyTurnSkillName(resolvedState) == "Too Young To Die") {\n',
)
combat = combat.replace('tự kích hoạt Too Young To Die', 'kích hoạt Too Young To Die bằng AP')
combat = combat.replace('tỷ lệ proc hiện tại $luciaTooYoungChance%; ', '')

for pattern in (
    r'^\s*private const val KAI_LAST_REQUIEM_CHANCE_PERCENT = \d+\n',
    r'^\s*private const val KAI_SILENT_LULLABY_CHANCE_PERCENT = \d+\n',
    r'^\s*private const val KAI_SALVATION_CHANCE_PERCENT = \d+\n',
    r'^\s*private const val KAI_QUICK_STEP_CHANCE_PERCENT = \d+\n',
    r'^\s*private const val LUCIA_FULL_AUTO_CHANCE_PERCENT = \d+\n',
    r'^\s*private const val LUCIA_FULL_AUTO_INTERVAL_TURNS = \d+\n',
):
    combat = re.sub(pattern, '', combat, flags=re.M)

for forbidden in (
    'KAI_LAST_REQUIEM_CHANCE_PERCENT', 'KAI_SILENT_LULLABY_CHANCE_PERCENT',
    'KAI_SALVATION_CHANCE_PERCENT', 'KAI_QUICK_STEP_CHANCE_PERCENT',
    'luciaFullAutoProc <', 'luciaTooYoungProc <',
    'roll(c.copy(eventCounter = c.eventCounter + 151), 100) < 30',
    'roll(c.copy(eventCounter = c.eventCounter + 211), 100) < 30',
):
    if forbidden in combat:
        raise RuntimeError("Legacy percentage skill activation survived: " + forbidden)
for required in (
    'PARTY_TURN_SKILL_CONTEXT_KEY',
    'partyTurnSkillName(resolvedState) == "The Last Requiem"',
    'partyTurnSkillName(resolvedState) == "Guilty Crown Override"',
    'partyTurnSkillName(resolvedState) == "Twosome Time"',
    'partyTurnSkillName(resolvedState) == "Rift Sever"',
    'partyTurnSkillName(resolvedState) == "M4A1 Full Auto Burst"',
    'partyTurnSkillName(resolvedState) == "Too Young To Die"',
    'partyTurnSkillName(resolvedState) == "Dead Angle"',
    'partyTurnSkillName(resolvedState) == "Counterphase"',
):
    if required not in combat:
        raise RuntimeError("AP skill runtime contract missing: " + required)
COMBAT.write_text(combat, encoding="utf-8")

party = require(PARTY)
party = replace_once(
    party,
    '  private const val ACTOR_CONTEXT = "${PREFIX}actorContext"\n',
    '  private const val ACTOR_CONTEXT = "${PREFIX}actorContext"\n  private const val SKILL_CONTEXT = "${PREFIX}skillContext"\n',
    "Party skill context metadata",
)
skill_start = party.find('      action.startsWith("PARTY_TURN_SKILL::") -> {\n')
skill_end = party.find('\n      else -> CombatRuntime.Resolution(', skill_start)
if skill_start < 0 or skill_end < 0:
    raise RuntimeError("Final PartyTurnCombat skill branch missing")
skill_branch = f'''      action.startsWith("PARTY_TURN_SKILL::") -> {{
        val skillName = action.removePrefix("PARTY_TURN_SKILL::").trim()
        val skill = selectableSkills(actor.id).firstOrNull {{ it.name == skillName }}
          ?: return CombatRuntime.Resolution(
            state = state,
            handled = true,
            reply = "Kỹ năng không hợp lệ cho ${{actor.name}}. AP và lượt không thay đổi.",
            committed = false,
            rejectionReason = "skill_not_available"
          )
        val cost = skillCost(skill.kind)
        val currentAp = ap(state)
        if (currentAp < cost) {{
          return CombatRuntime.Resolution(
            state = state,
            handled = true,
            reply = "${{actor.name}} không đủ AP cho ${{skill.name}}: cần $cost, hiện có $currentAp/$MAX_AP.",
            committed = false,
            rejectionReason = "insufficient_ap"
          )
        }}
        if (locked) {{
          return CombatRuntime.Resolution(
            state = state,
            handled = true,
            reply = "${{actor.name}} đang bị choáng hoặc mất khả năng hành động. AP và lượt không thay đổi.",
            committed = false,
            rejectionReason = "actor_action_locked"
          )
        }}
        val prerequisite = CombatRuntime.partyTurnSkillRejection(state, actor.id, skill.name)
        if (prerequisite != null) {{
          return CombatRuntime.Resolution(
            state = state,
            handled = true,
            reply = prerequisite + " AP và lượt không thay đổi.",
            committed = false,
            rejectionReason = "skill_prerequisite_not_met"
          )
        }}
        val scoped = withSkillContext(withActorContext(state, actor.id), skill.name)
        val engine = CombatRuntime.resolve(scoped, "EXECUTE", "tấn công")
        val cleaned = withoutSkillContext(withoutActorContext(engine))
        finishValidAction(
          state, cleaned, actor,
          apDelta = -cost,
          requestKey = requestKey,
          displayAction = display,
          locked = false
        )
      }}
'''
party = party[:skill_start] + skill_branch + party[skill_end:]
party = party.replace(
    '              put("cost", JSONObject.NULL)\n',
    '              put("cost", skillCost(skill.kind))\n',
)
selector_start = party.find('  private fun selectableSkills(characterId: String): List<CharacterSkillDefinition>')
selector_next = party.find('\n\n  private fun ', selector_start + 10)
if selector_start < 0 or selector_next < 0:
    raise RuntimeError("Final PartyTurnCombat selectableSkills helper missing")
selector = f'''  private fun selectableSkills(characterId: String): List<CharacterSkillDefinition> =
    CompanionSkillCatalog.forCharacter(characterId).filter {{ skill ->
      val kind = skill.kind.uppercase()
      kind == "SKILL" || kind == "ULTIMATE"
    }}

  private fun skillCost(kind: String): Int =
    if (kind.uppercase() == "ULTIMATE") {ULTIMATE_AP_COST} else {NORMAL_AP_COST}'''
party = party[:selector_start] + selector + party[selector_next:]
helper_anchor = '  private fun withoutActorContext(result: CombatRuntime.Resolution): CombatRuntime.Resolution {\n'
skill_helpers = '''  private fun withSkillContext(state: GameState, skillName: String): GameState =
    state.copy(metadata = state.metadata + (SKILL_CONTEXT to skillName))

  private fun withoutSkillContext(result: CombatRuntime.Resolution): CombatRuntime.Resolution =
    result.copy(state = result.state.copy(metadata = result.state.metadata - SKILL_CONTEXT))

'''
if 'private fun withSkillContext(' not in party:
    party = replace_once(party, helper_anchor, skill_helpers + helper_anchor, "Party skill context helpers")
party = replace_once(
    party,
    '      val apLine = if (apDelta > 0) "AP của Party tăng từ $oldAp lên $newAp/$MAX_AP." else ""\n',
    '''      val apLine = when {
        apDelta > 0 -> "AP của Party tăng từ $oldAp lên $newAp/$MAX_AP."
        apDelta < 0 -> "AP của Party giảm từ $oldAp xuống $newAp/$MAX_AP."
        else -> ""
      }
''',
    "Party AP spend narration",
)
for required in (
    'rejectionReason = "insufficient_ap"',
    'rejectionReason = "skill_prerequisite_not_met"',
    'apDelta = -cost',
    'if (kind.uppercase() == "ULTIMATE") 3 else 2',
    'put("cost", skillCost(skill.kind))',
):
    if required not in party:
        raise RuntimeError("Party AP skill contract missing: " + required)
PARTY.write_text(party, encoding="utf-8")

interleaved = require(INTERLEAVED_TEST)
for name in (
    'invalidAutomaticSkillCommandDoesNotSpendApAdvanceActorOrTickCombat',
    'currentCatalogDoesNotExposeAutomaticCounterPassiveOrStateSkillsAsPayableManualActions',
):
    interleaved = remove_test_function(interleaved, name)
INTERLEAVED_TEST.write_text(interleaved, encoding="utf-8")

combat_test = require(COMBAT_TEST)
for name in (
    'kaiAutomaticGunSkillsExposeAllFourIndependentProcContracts',
    'guiltyCrownOverrideTriggersAutomaticallyOnEveryThirdCombatTurn',
    'irisAndSyvialAutomaticSkillsResolveWhenTheyAreActivePartyMembers',
    'luciaFullAutoBurstCanProcOnSecondAttackTurn',
    'luciaFullAutoBurstDoesNotRunOnFirstAttackTurn',
    'luciaTooYoungToDieChanceScalesOnlyAfterThreePercentLostBelowHalfHp',
    'luciaTooYoungToDieCanProcOnAnyCombatTurn',
):
    combat_test = remove_test_function(combat_test, name)
COMBAT_TEST.write_text(combat_test, encoding="utf-8")

AP_TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyTurnCombatApSkillAuthorityTest {
  private fun kaiCombat(): GameState =
    PartyTurnCombat.init(CombatRuntime.start(GameState.initial(), "diep_minh"))

  private fun gainAp(state: GameState, count: Int): GameState {
    var next = state
    repeat(count) { index ->
      val result = PartyTurnCombat.resolve(
        next, "EXECUTE", "PARTY_TURN_DEFEND",
        "gain-ap-$index-${PartyTurnCombat.actionSerial(next)}"
      )
      assertTrue(result.committed)
      next = result.state
    }
    return next
  }

  @Test fun normalSkillCostsTwoApAndAppliesDamageAndBleed() {
    val state = gainAp(kaiCombat(), 2)
    assertEquals(2, PartyTurnCombat.json(state)!!.getInt("ap"))
    val beforeHp = CombatRuntime.active(state)!!.entityHp
    val result = PartyTurnCombat.resolve(
      state, "EXECUTE", "PARTY_TURN_SKILL::The Last Requiem", "skill-normal"
    )
    assertTrue(result.handled)
    assertTrue(result.committed)
    assertTrue(CombatRuntime.active(result.state)!!.entityHp < beforeHp)
    assertEquals(0, PartyTurnCombat.json(result.state)!!.getInt("ap"))
    assertNotNull(result.state.metadata["combat.kaiBleedTurns"])
    assertTrue(result.reply.contains("The Last Requiem"))
  }

  @Test fun ultimateCostsThreeApAndDealsItsExistingAuthoritativeDamage() {
    val state = gainAp(kaiCombat(), 3)
    assertEquals(3, PartyTurnCombat.json(state)!!.getInt("ap"))
    val beforeHp = CombatRuntime.active(state)!!.entityHp
    val result = PartyTurnCombat.resolve(
      state, "EXECUTE", "PARTY_TURN_SKILL::Guilty Crown Override", "skill-ultimate"
    )
    assertTrue(result.committed)
    assertTrue(CombatRuntime.active(result.state)!!.entityHp < beforeHp)
    assertEquals(0, PartyTurnCombat.json(result.state)!!.getInt("ap"))
    assertTrue(result.reply.contains("Guilty Crown Override"))
  }

  @Test fun insufficientApRejectsWithoutDamageTurnAdvanceOrEntityResponse() {
    val state = kaiCombat()
    val beforeCombat = CombatRuntime.active(state)!!
    val beforeTurn = PartyTurnCombat.json(state)!!.toString()
    val result = PartyTurnCombat.resolve(
      state, "EXECUTE", "PARTY_TURN_SKILL::The Last Requiem", "skill-no-ap"
    )
    assertTrue(result.handled)
    assertFalse(result.committed)
    assertEquals("insufficient_ap", result.rejectionReason)
    assertEquals(beforeCombat.entityHp, CombatRuntime.active(result.state)!!.entityHp)
    assertEquals(beforeCombat.eventCounter, CombatRuntime.active(result.state)!!.eventCounter)
    assertEquals(beforeTurn, PartyTurnCombat.json(result.state)!!.toString())
  }

  @Test fun ordinaryAttackNeverAutoActivatesCharacterSkills() {
    val names = listOf(
      "The Last Requiem", "Silent Lullaby", "Salvation", "Quick Step", "Guilty Crown Override",
      "Twosome Time", "Rain Storm", "Honeycomb Fire", "Charged Shot",
      "Rift Sever", "Crimson Guillotine", "Lucifer Breaker", "Spatial Dominion",
      "M4A1 Full Auto Burst", "Too Young To Die"
    )
    var state = kaiCombat()
    repeat(12) { index ->
      val result = PartyTurnCombat.resolve(state, "EXECUTE", "PARTY_TURN_ATK", "plain-atk-$index")
      names.forEach { assertFalse("basic attack auto-fired $it", result.reply.contains(it)) }
      state = result.state
      if (CombatRuntime.active(state) == null) return
    }
  }

  @Test fun projectedSkillCostsAreTwoForNormalAndThreeForUltimate() {
    val turn = PartyTurnCombat.json(kaiCombat())!!
    val skills = turn.getJSONArray("skills")
    val costs = (0 until skills.length()).associate { index ->
      val skill = skills.getJSONObject(index)
      skill.getString("name") to skill.getInt("cost")
    }
    assertEquals(2, costs["The Last Requiem"])
    assertEquals(2, costs["Quick Step"])
    assertEquals(3, costs["Guilty Crown Override"])
  }
}
''', encoding="utf-8")

verifier = require(VERIFIER)
for fragment in (
    "    ('private const val LUCIA_FULL_AUTO_CHANCE_PERCENT = 20', combat),\n",
    "    ('private const val LUCIA_FULL_AUTO_INTERVAL_TURNS = 2', combat),\n",
    "    ('20% mỗi 2 combat turn hợp lệ khi Party chọn TẤN CÔNG', skill_catalog),\n",
):
    verifier = verifier.replace(fragment, '')
anchor = "    ('LUCIA_FULL_AUTO_BURST_V1', combat),\n"
new_checks = anchor + '''    ('PARTY_TURN_SKILL_CONTEXT_KEY', combat),
    ('partyTurnSkillName(resolvedState) == "The Last Requiem"', combat),
    ('partyTurnSkillName(resolvedState) == "Guilty Crown Override"', combat),
    ('Kích hoạt bằng 2 AP trong lượt của nhân vật', skill_catalog),
    ('Kích hoạt bằng 3 AP trong lượt của nhân vật', skill_catalog),
'''
if "PARTY_TURN_SKILL_CONTEXT_KEY" not in verifier:
    verifier = replace_once(verifier, anchor, new_checks, "Verifier AP skill checks")
VERIFIER.write_text(verifier, encoding="utf-8")

print(
    "AP skill authority final applied: manual skill activation only, normal skills cost 2 AP, "
    "ultimates cost 3 AP, and legacy combat proc gates are retired."
)
