from pathlib import Path

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
CATALOG = ROOT / "app/src/main/java/com/rabpit/backroom/core/CompanionSkillCatalog.kt"
INDEX = ROOT / "app/src/main/assets/index.html"
COMBAT_TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"
COMPANION_TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CompanionSkillCatalogTest.kt"
PARTY_TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/PartyCombatActionsTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


def replace_test_function(source: str, name: str, replacement: str) -> str:
    marker = f"  @Test fun {name}() {{"
    start = source.find(marker)
    if start < 0:
        raise RuntimeError(f"Party combat test compatibility: missing {name}")
    next_test = source.find("\n  @Test fun ", start + len(marker))
    class_end = source.rfind("\n}")
    end = next_test if next_test >= 0 else class_end
    if end < 0:
        raise RuntimeError(f"Party combat test compatibility: could not bound {name}")
    return source[:start] + replacement.rstrip() + "\n" + source[end:]


# ---------------------------------------------------------------------------
# 1) The three combat buttons are Party commands. One click still creates one
# CombatRuntime eventCounter increment; every ACTIVE/living Party member acts in
# that same resolution instead of creating serialized hidden follower turns.
# ---------------------------------------------------------------------------
combat = COMBAT.read_text(encoding="utf-8")

helper_anchor = "  private fun activePartyCharacter(state: GameState, characterId: String): CharacterState? {\n"
party_helper = '''  // PARTY_ACTIONS_V1: authoritative roster for one simultaneous Party command.
  private fun activePartyNames(state: GameState): String =
    state.party.memberIds.distinct().mapNotNull { id ->
      state.characters[id]?.takeIf { character ->
        character.presence == CharacterPresence.ACTIVE && character.vitalState.currentHp > 0
      }?.name
    }.joinToString(", ")

'''
if "private fun activePartyNames(" not in combat:
    combat = replace_once(combat, helper_anchor, party_helper + helper_anchor, "party action active roster helper")

combat = replace_once(
    combat,
    '''      Intent.EVADE -> {
        val goodCounter = c.telegraph in setOf("LUNGE", "GRAB", "RUSH")
''',
    '''      Intent.EVADE -> {
        log += "PARTY ACTION NÉ TRÁNH: ${activePartyNames(resolvedState)} cùng thực hiện trong một combat turn."
        val goodCounter = c.telegraph in setOf("LUNGE", "GRAB", "RUSH")
''',
    "party evade banner",
)
combat = replace_once(
    combat,
    '''      Intent.ESCAPE -> {
        val gain = 20 + c.momentum.coerceAtLeast(0) * 5 + when (c.cover) { Cover.HARD -> 15; Cover.PARTIAL -> 8; Cover.EXPOSED -> 0 }
''',
    '''      Intent.ESCAPE -> {
        log += "PARTY ACTION BỎ CHẠY: ${activePartyNames(resolvedState)} cùng rút khỏi encounter trong một combat turn."
        val gain = 20 + c.momentum.coerceAtLeast(0) * 5 + when (c.cover) { Cover.HARD -> 15; Cover.PARTIAL -> 8; Cover.EXPOSED -> 0 }
''',
    "party escape banner",
)
combat = replace_once(
    combat,
    '''      Intent.ATTACK -> {
        val roll = roll(c, 100)
''',
    '''      Intent.ATTACK -> {
        log += "PARTY ACTION TẤN CÔNG: ${activePartyNames(resolvedState)} cùng khai triển đòn đánh trong một combat turn."
        val roll = roll(c, 100)
''',
    "party attack banner",
)

# Lucia's old opt-in joint-order parsing is obsolete. ATTACK itself is now the Party command.
old_joint = '''        val jointOrder = action.lowercase().let { raw ->
          raw.contains("cả 2") || raw.contains("cả hai") || raw.contains("hai người") ||
            raw.contains("cùng tấn công") || raw.contains("cùng bắn") ||
            ((raw.contains("lucia") || raw.contains("lục")) && (raw.contains("tấn công") || raw.contains("bắn")))
        }
'''
new_joint = '''        val jointOrder = true // PARTY_ACTIONS_V1: every ATTACK intent includes every ACTIVE Party member.
'''
combat = replace_once(combat, old_joint, new_joint, "Lucia party-wide attack order")
combat = replace_once(
    combat,
    "        if (jointOrder && luciaActive && c.entityHp > 0) {\n",
    "        if (jointOrder && luciaActive) {\n",
    "Lucia simultaneous attack even when another same-volley hit reaches zero first",
)
combat = replace_once(
    combat,
    '''            val luciaDamage = max(1, LUCIA_M4A1_COMBAT_DAMAGE - profile.armor)
            val luciaHp = max(0, c.entityHp - luciaDamage)
''',
    '''            val luciaPotentialDamage = max(1, LUCIA_M4A1_COMBAT_DAMAGE - profile.armor)
            val luciaDamage = min(c.entityHp, luciaPotentialDamage)
            val luciaHp = max(0, c.entityHp - luciaDamage)
''',
    "Lucia simultaneous overkill-safe damage",
)

# Iris and Syvial receive one ordinary weapon action in the same ATTACK resolution.
# An Nhiên obeys the command too, but her canon lock remains non-combat: her action is
# support/distraction only and never creates Weapon DMG.
attack_start = combat.find("      Intent.ATTACK -> {\n")
attack_end = combat.find("      Intent.OTHER -> {\n", attack_start)
if attack_start < 0 or attack_end < 0:
    raise RuntimeError("Party ATTACK block boundary missing")
attack = combat[attack_start:attack_end]
if "PARTY_FOLLOWER_BASE_ATTACKS_V1" not in attack:
    closing = attack.rfind("      }\n")
    if closing < 0:
        raise RuntimeError("Party ATTACK closing brace missing")
    follower_attack = r'''        // PARTY_FOLLOWER_BASE_ATTACKS_V1: all actors resolve inside this one ATTACK event.
        val irisPartyAttack = activePartyCharacter(resolvedState, IRIS_ID)
        if (irisPartyAttack != null) {
          val irisHitRoll = roll(c.copy(eventCounter = c.eventCounter + 307), 100)
          val irisEvasionRoll = roll(c.copy(eventCounter = c.eventCounter + 311), 100)
          if (irisHitRoll < hitChance && irisEvasionRoll >= ENTITY_EVASION_PERCENT) {
            val potential = companionSkillDamage(CharacterStatEngine.weaponDamage(resolvedState, IRIS_ID), 100, profile.armor)
            val damage = min(c.entityHp, potential)
            val hp = max(0, c.entityHp - damage)
            c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 10))
            log += "Iris thực hiện lệnh TẤN CÔNG bằng Ivory & Ebony: -$damage HP (${c.entityHp}/${c.entityMaxHp})."
          } else {
            log += "Iris thực hiện lệnh TẤN CÔNG nhưng loạt bắn không trúng mục tiêu."
          }
        }

        val syvialPartyAttack = activePartyCharacter(resolvedState, SYVIAL_ID)
        if (syvialPartyAttack != null) {
          val syvialHitRoll = roll(c.copy(eventCounter = c.eventCounter + 317), 100)
          val syvialEvasionRoll = roll(c.copy(eventCounter = c.eventCounter + 331), 100)
          if (syvialHitRoll < hitChance && syvialEvasionRoll >= ENTITY_EVASION_PERCENT) {
            val potential = companionSkillDamage(CharacterStatEngine.weaponDamage(resolvedState, SYVIAL_ID), 100, profile.armor)
            val damage = min(c.entityHp, potential)
            val hp = max(0, c.entityHp - damage)
            c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 12))
            log += "Syvial thực hiện lệnh TẤN CÔNG bằng GodKiller: -$damage HP (${c.entityHp}/${c.entityMaxHp})."
          } else {
            log += "Syvial thực hiện lệnh TẤN CÔNG nhưng Entity tránh được nhát chém."
          }
        }

        if (activePartyCharacter(resolvedState, AN_NHIEN_ID) != null) {
          log += "An Nhiên thực hiện lệnh TẤN CÔNG theo vai trò hỗ trợ: gây nhiễu/đánh lạc hướng, không dùng vũ khí và không gây damage."
        }
'''
    attack = attack[:closing] + follower_attack + attack[closing:]
    combat = combat[:attack_start] + attack + combat[attack_end:]

# ---------------------------------------------------------------------------
# 2) Automatic offensive skills must respect the selected Party command.
# ATTACK can fire offensive/ultimate skills. EVADE is movement/defense only.
# ESCAPE is withdrawal only. Persisted Bleeding/regeneration remain status effects.
# ---------------------------------------------------------------------------
# Preserve the exact inner GCO marker required by the established regression workflow.
gco_start = combat.find("    if (c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0) {\n")
gco_end = combat.find("    val isGuiltyCrownTurn =", gco_start)
if gco_start < 0 or gco_end < 0:
    raise RuntimeError("Guilty Crown section missing for Party action gate")
gco_section = combat[gco_start:gco_end]
if "PARTY_ATTACK_GCO_GATE_V1" not in gco_section:
    wrapped = "    // PARTY_ATTACK_GCO_GATE_V1\n    if (intent == Intent.ATTACK) {\n" + gco_section + "    }\n\n"
    combat = combat[:gco_start] + wrapped + combat[gco_end:]

combat = replace_once(
    combat,
    "    val isGuiltyCrownTurn = c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0\n",
    "    val isGuiltyCrownTurn = intent == Intent.ATTACK && c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0\n",
    "GCO attack-only priority flag",
)
combat = replace_once(
    combat,
    "    if (!isGuiltyCrownTurn && c.entityHp > 0) {\n",
    "    if (c.entityHp > 0) {\n",
    "Kai skill block command-aware outer gate",
)
combat = replace_once(
    combat,
    "      if (roll(c.copy(eventCounter = c.eventCounter + 101), 100) < KAI_LAST_REQUIEM_CHANCE_PERCENT) {\n",
    "      if (intent == Intent.ATTACK && !isGuiltyCrownTurn && roll(c.copy(eventCounter = c.eventCounter + 101), 100) < KAI_LAST_REQUIEM_CHANCE_PERCENT) {\n",
    "Last Requiem attack-only gate",
)
combat = replace_once(
    combat,
    "      if (c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 113), 100) < KAI_SILENT_LULLABY_CHANCE_PERCENT) {\n",
    "      if (intent == Intent.ATTACK && !isGuiltyCrownTurn && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 113), 100) < KAI_SILENT_LULLABY_CHANCE_PERCENT) {\n",
    "Silent Lullaby attack-only gate",
)
combat = replace_once(
    combat,
    "      if (c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 127), 100) < KAI_SALVATION_CHANCE_PERCENT) {\n",
    "      if (intent == Intent.ATTACK && !isGuiltyCrownTurn && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 127), 100) < KAI_SALVATION_CHANCE_PERCENT) {\n",
    "Salvation attack-only gate",
)
combat = replace_once(
    combat,
    "      if (c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 139), 100) < KAI_QUICK_STEP_CHANCE_PERCENT) {\n",
    "      if ((intent == Intent.ATTACK || intent == Intent.EVADE) && !isGuiltyCrownTurn && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 139), 100) < KAI_QUICK_STEP_CHANCE_PERCENT) {\n",
    "Quick Step attack/evade gate",
)

# Iris keeps ARGUS passive state on any turn, but offensive skills only fire on ATTACK.
iris_start = combat.find("    if (irisActive && c.entityHp > 0) {\n")
syvial_start = combat.find("    if (syvialActive && c.entityHp > 0) {\n", iris_start)
if iris_start < 0 or syvial_start < 0:
    raise RuntimeError("Iris/Syvial skill sections missing for Party command gating")
iris = combat[iris_start:syvial_start]
iris = replace_once(
    iris,
    "      val irisUltimate = c.eventCounter % IRIS_ULTIMATE_INTERVAL_TURNS == 0\n",
    "      val irisUltimate = intent == Intent.ATTACK && c.eventCounter % IRIS_ULTIMATE_INTERVAL_TURNS == 0\n",
    "Iris ultimate attack-only gate",
)
iris = replace_once(
    iris,
    "      } else {\n        if (roll(c.copy(eventCounter = c.eventCounter + 151), 100) < 30 && c.entityHp > 0) {\n",
    "      } else if (intent == Intent.ATTACK) {\n        if (roll(c.copy(eventCounter = c.eventCounter + 151), 100) < 30 && c.entityHp > 0) {\n",
    "Iris automatic offense attack-only gate",
)
combat = combat[:iris_start] + iris + combat[syvial_start:]

syvial_start = combat.find("    if (syvialActive && c.entityHp > 0) {\n")
an_start = combat.find("    if (anNhienActive && c.entityHp > 0) {\n", syvial_start)
if syvial_start < 0 or an_start < 0:
    raise RuntimeError("Syvial/An Nhien skill sections missing for Party command gating")
syvial = combat[syvial_start:an_start]
syvial = replace_once(
    syvial,
    "      val syvialUltimate = syvialDevilTrigger && c.eventCounter % SYVIAL_ULTIMATE_INTERVAL_TURNS == 0\n",
    "      val syvialUltimate = intent == Intent.ATTACK && syvialDevilTrigger && c.eventCounter % SYVIAL_ULTIMATE_INTERVAL_TURNS == 0\n",
    "Syvial ultimate attack-only gate",
)
syvial = replace_once(
    syvial,
    "      } else {\n        if (roll(c.copy(eventCounter = c.eventCounter + 211), 100) < 30 && c.entityHp > 0) {\n",
    "      } else if (intent == Intent.ATTACK) {\n        if (roll(c.copy(eventCounter = c.eventCounter + 211), 100) < 30 && c.entityHp > 0) {\n",
    "Syvial automatic offense attack-only gate",
)
combat = combat[:syvial_start] + syvial + combat[an_start:]

combat = replace_once(
    combat,
    "      if (roll(c.copy(eventCounter = c.eventCounter + 269), 100) < 25) {\n",
    "      if (intent == Intent.ATTACK && roll(c.copy(eventCounter = c.eventCounter + 269), 100) < 25) {\n",
    "An Nhien attack support gate",
)
combat = replace_once(
    combat,
    "      if (c.eventCounter % AN_NHIEN_ULTIMATE_INTERVAL_TURNS == 0) {\n",
    "      if (intent == Intent.ESCAPE && c.eventCounter % AN_NHIEN_ULTIMATE_INTERVAL_TURNS == 0) {\n",
    "An Nhien escape plan gate",
)
combat = replace_once(
    combat,
    "        if (irisActive && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 281), 100) < 15) {\n",
    "        if (intent == Intent.ATTACK && irisActive && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 281), 100) < 15) {\n",
    "Dead Angle respects Party command",
)
combat = replace_once(
    combat,
    "        if (syvialActive && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 293), 100) < 30) {\n",
    "        if (intent == Intent.ATTACK && syvialActive && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 293), 100) < 30) {\n",
    "Counterphase respects Party command",
)

for marker in (
    "PARTY_ACTIONS_V1",
    "PARTY ACTION TẤN CÔNG:",
    "PARTY ACTION NÉ TRÁNH:",
    "PARTY ACTION BỎ CHẠY:",
    "PARTY_FOLLOWER_BASE_ATTACKS_V1",
    "val jointOrder = true // PARTY_ACTIONS_V1",
    "Iris thực hiện lệnh TẤN CÔNG",
    "Syvial thực hiện lệnh TẤN CÔNG",
    "An Nhiên thực hiện lệnh TẤN CÔNG theo vai trò hỗ trợ",
    "PARTY_ATTACK_GCO_GATE_V1",
    "intent == Intent.ATTACK && !isGuiltyCrownTurn",
    "intent == Intent.ESCAPE && c.eventCounter % AN_NHIEN_ULTIMATE_INTERVAL_TURNS == 0",
):
    if marker not in combat:
        raise RuntimeError("Party combat runtime contract missing: " + marker)
COMBAT.write_text(combat, encoding="utf-8")


# ---------------------------------------------------------------------------
# 3) Skill descriptions follow the same command semantics shown by the UI.
# ---------------------------------------------------------------------------
catalog = CATALOG.read_text(encoding="utf-8")
replacements = (
    ("30% mỗi turn hợp lệ", "30% mỗi lượt TẤN CÔNG hợp lệ"),
    ("20% mỗi turn hợp lệ", "20% mỗi lượt TẤN CÔNG hợp lệ"),
    ("25% mỗi turn hợp lệ", "25% mỗi lượt TẤN CÔNG hợp lệ"),
    ("20% khi Devil Trigger", "20% khi Devil Trigger và Party chọn TẤN CÔNG"),
    ("Mỗi 3 combat turn khi Devil Trigger", "Mỗi 3 combat turn hợp lệ khi Party chọn TẤN CÔNG và Devil Trigger"),
    ("25% mỗi combat turn khi ACTIVE trong Party", "25% khi Party chọn TẤN CÔNG và An Nhiên ACTIVE"),
    ("Mỗi 5 combat turn khi ACTIVE trong Party", "Mỗi 5 combat turn hợp lệ khi Party chọn BỎ CHẠY và An Nhiên ACTIVE"),
    ("30% mỗi turn hợp lệ", "30% mỗi lượt TẤN CÔNG hợp lệ"),
    ("20% mỗi turn hợp lệ", "20% mỗi lượt TẤN CÔNG hợp lệ"),
    ("30% mỗi turn hợp lệ", "30% mỗi lượt TẤN CÔNG hợp lệ"),
    ("Mỗi 3 combat turn", "Mỗi 3 combat turn hợp lệ khi Party chọn TẤN CÔNG"),
    ("Khi người chơi ra lệnh cả Kai và Lucia cùng tấn công", "Khi Party chọn TẤN CÔNG"),
)
for old, new in replacements:
    catalog = catalog.replace(old, new)
if 's("M4A1 Joint Attack", "COMMAND", "Khi Party chọn TẤN CÔNG"' not in catalog:
    raise RuntimeError("Lucia skill catalog did not adopt Party ATTACK semantics")
CATALOG.write_text(catalog, encoding="utf-8")


# ---------------------------------------------------------------------------
# 4) Keep labels compact; only the hidden command payload becomes Party-wide.
# ---------------------------------------------------------------------------
html = INDEX.read_text(encoding="utf-8")
html = replace_once(html, "action:'Tấn công'", "action:'Cả Party cùng tấn công'", "Attack Party command payload")
html = replace_once(html, "action:'Né tránh'", "action:'Cả Party cùng né tránh'", "Evade Party command payload")
html = replace_once(html, "action:'Bỏ chạy'", "action:'Cả Party cùng bỏ chạy'", "Flee Party command payload")
for marker in (
    "label:'TẤN CÔNG'",
    "label:'NÉ TRÁNH'",
    "label:'BỎ CHẠY'",
    "action:'Cả Party cùng tấn công'",
    "action:'Cả Party cùng né tránh'",
    "action:'Cả Party cùng bỏ chạy'",
):
    if marker not in html:
        raise RuntimeError("Party combat action-bar contract missing: " + marker)
INDEX.write_text(html, encoding="utf-8")


# ---------------------------------------------------------------------------
# 5) Adapt earlier regression tests to the new command authority, then add
# explicit all-Party tests for ATTACK / EVADE / ESCAPE.
# ---------------------------------------------------------------------------
test = COMBAT_TEST.read_text(encoding="utf-8")

# Existing automatic-skill discovery now uses the only command that permits offensive procs.
test = test.replace(
    'CombatRuntime.resolve(state, "SEARCH", "giữ mục tiêu trong tầm quan sát")',
    'CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")',
)

new_gco_trigger = r'''  @Test fun guiltyCrownOverrideTriggersAutomaticallyOnEveryThirdCombatTurn() {
    var evadeState = CombatRuntime.start(GameState.initial(), "diep_minh")
    evadeState = evadeState.copy(metadata = evadeState.metadata + ("combat.eventCounter" to "2"))
    val evade = CombatRuntime.resolve(evadeState, "EXECUTE", "Cả Party cùng né tránh")
    assertTrue(evade.handled)
    assertFalse("Party EVADE must not secretly fire the attack-only Override", evade.reply.contains("Guilty Crown Override"))

    var attackState = CombatRuntime.start(GameState.initial(), "diep_minh")
    attackState = attackState.copy(metadata = attackState.metadata + mapOf(
      "combat.eventCounter" to "2",
      "combat.entityHp" to "2000",
      "combat.entityMaxHp" to "2999"
    ))
    val third = CombatRuntime.resolve(attackState, "EXECUTE", "Cả Party cùng tấn công")
    assertTrue(third.handled)
    assertTrue(third.reply.contains("Guilty Crown Override"))
    assertTrue(third.reply.contains("24/24 phát trúng liên tiếp"))
    assertTrue(third.reply.contains("Accuracy 200%"))
    assertTrue(third.reply.contains("bỏ qua toàn bộ hiệu ứng né"))
    assertTrue(third.reply.contains("mỗi phát -10 HP"))
    assertTrue(third.reply.contains("tổng -240 HP"))
  }
'''
test = replace_test_function(test, "guiltyCrownOverrideTriggersAutomaticallyOnEveryThirdCombatTurn", new_gco_trigger)

new_gco_damage = r'''  @Test fun guiltyCrownOverrideAppliesExactTwentyFourTimesTenHpBeforeNormalRegen() {
    var state = CombatRuntime.start(GameState.initial(), "diep_minh")
    state = state.copy(metadata = state.metadata + mapOf(
      "combat.entityHp" to "2000",
      "combat.entityMaxHp" to "2999",
      "combat.eventCounter" to "2"
    ))

    val third = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")
    assertTrue(third.handled)
    assertTrue(third.reply.contains("tổng -240 HP"))
    assertTrue(third.reply.contains("Accuracy 200%"))
    assertTrue(third.reply.contains("bỏ qua toàn bộ hiệu ứng né"))
  }
'''
test = replace_test_function(test, "guiltyCrownOverrideAppliesExactTwentyFourTimesTenHpBeforeNormalRegen", new_gco_damage)

# Lucia's previous Kai-only exception is intentionally inverted: ATTACK is now Party-wide.
if "luciaDoesNotAutoAttackOnKaiOnlyAttackOrder" in test:
    new_lucia = r'''  @Test fun luciaAttackIntentIsPartyWide() {
    val initial = LuciaCanon.ensure(GameState.initial())
    var state = initial.copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID)))
    state = CombatRuntime.start(state, "diep_minh")

    val result = CombatRuntime.resolve(state, "EXECUTE", "Kai tấn công")
    assertTrue(result.handled)
    assertTrue(result.reply.contains("Lucia \"Lục\""))
    assertTrue(
      result.reply.contains("bắn hỗ trợ bằng M4A1") ||
        result.reply.contains("cũng khai hỏa nhưng phát bắn không trúng mục tiêu")
    )
  }
'''
    test = replace_test_function(test, "luciaDoesNotAutoAttackOnKaiOnlyAttackOrder", new_lucia)
COMBAT_TEST.write_text(test, encoding="utf-8")

companion_test = COMPANION_TEST.read_text(encoding="utf-8")
companion_test = companion_test.replace(
    'CombatRuntime.resolve(state, "SEARCH", "giữ đội hình và quan sát mục tiêu")',
    'CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")',
)
companion_test = companion_test.replace(
    'CombatRuntime.resolve(state, "SEARCH", "tìm đường tránh giao tranh")',
    'CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")',
)
COMPANION_TEST.write_text(companion_test, encoding="utf-8")

PARTY_TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyCombatActionsTest {
  private fun fullParty(): GameState {
    var state = SpecialFollowersCanon.ensure(GameState.initial())
    state = LuciaCanon.ensure(state)
    state = AnNhienCanon.ensure(state)
    return state.copy(
      party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID))
    )
  }

  @Test fun attackButtonResolvesEveryActivePartyMemberInOneCombatEvent() {
    var state = CombatRuntime.start(fullParty(), "diep_minh")
    val before = CombatRuntime.active(state)!!
    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")
    assertTrue(result.handled)
    val after = CombatRuntime.active(result.state)
    assertTrue(result.reply.contains("PARTY ACTION TẤN CÔNG:"))
    assertTrue(result.reply.contains("Kai Akechi"))
    assertTrue(result.reply.contains("Lucia"))
    assertTrue(result.reply.contains("Iris"))
    assertTrue(result.reply.contains("Syvial"))
    assertTrue(result.reply.contains("An Nhiên"))
    assertTrue(result.reply.contains("Lucia \"Lục\""))
    assertTrue(result.reply.contains("Iris thực hiện lệnh TẤN CÔNG"))
    assertTrue(result.reply.contains("Syvial thực hiện lệnh TẤN CÔNG"))
    assertTrue(result.reply.contains("An Nhiên thực hiện lệnh TẤN CÔNG theo vai trò hỗ trợ"))
    assertFalse(result.reply.substringAfter("An Nhiên thực hiện lệnh TẤN CÔNG").contains("Weapon DMG"))
    assertTrue(after == null || after.eventCounter == before.eventCounter + 1)
  }

  @Test fun evadeButtonMovesTheWholePartyAndDoesNotFireOffensiveSkills() {
    var state = CombatRuntime.start(fullParty(), "diep_minh")
    state = state.copy(metadata = state.metadata + ("combat.eventCounter" to "2"))
    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
    assertTrue(result.handled)
    assertTrue(result.reply.contains("PARTY ACTION NÉ TRÁNH:"))
    assertTrue(result.reply.contains("Kai Akechi"))
    assertTrue(result.reply.contains("Lucia"))
    assertTrue(result.reply.contains("Iris"))
    assertTrue(result.reply.contains("Syvial"))
    assertTrue(result.reply.contains("An Nhiên"))
    for (forbidden in listOf(
      "Guilty Crown Override", "Lucia \"Lục\" bắn hỗ trợ", "Iris thực hiện lệnh TẤN CÔNG",
      "Syvial thực hiện lệnh TẤN CÔNG", "Twosome Time tự động kích hoạt", "Rift Sever tự động kích hoạt",
      "Dead Angle: Iris", "Counterphase: Syvial"
    )) assertFalse(forbidden, result.reply.contains(forbidden))
  }

  @Test fun fleeButtonWithdrawsTheWholePartyWithoutDroppingFollowersOrFiring() {
    var state = CombatRuntime.start(fullParty(), "diep_minh")
    state = state.copy(metadata = state.metadata + mapOf(
      "combat.eventCounter" to "2",
      "combat.escapeProgress" to "95"
    ))
    val beforeMembers = state.party.memberIds
    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng bỏ chạy")
    assertTrue(result.handled)
    assertTrue(result.escaped)
    assertTrue(result.reply.contains("PARTY ACTION BỎ CHẠY:"))
    assertEquals(beforeMembers, result.state.party.memberIds)
    assertFalse(result.reply.contains("Guilty Crown Override"))
    assertFalse(result.reply.contains("Lucia \"Lục\" bắn hỗ trợ"))
    assertFalse(result.reply.contains("Iris thực hiện lệnh TẤN CÔNG"))
    assertFalse(result.reply.contains("Syvial thực hiện lệnh TẤN CÔNG"))
  }
}
''', encoding="utf-8")

for path, markers in (
    (COMBAT_TEST, ("guiltyCrownOverrideTriggersAutomaticallyOnEveryThirdCombatTurn", "luciaAttackIntentIsPartyWide")),
    (COMPANION_TEST, ('"Cả Party cùng tấn công"',)),
    (PARTY_TEST, ("class PartyCombatActionsTest", "PARTY ACTION TẤN CÔNG:", "PARTY ACTION NÉ TRÁNH:", "PARTY ACTION BỎ CHẠY:")),
):
    content = path.read_text(encoding="utf-8")
    for marker in markers:
        if marker not in content:
            raise RuntimeError(f"Party combat regression marker missing in {path.name}: {marker}")

print("Party combat actions installed: Attack / Evade / Flee are single-turn Party-wide commands; offensive skills cannot leak into Evade/Flee, and An Nhiên remains non-combat support.")
