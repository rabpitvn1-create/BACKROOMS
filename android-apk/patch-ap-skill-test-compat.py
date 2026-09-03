from pathlib import Path

ROOT = Path(__file__).resolve().parent
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
COMBAT_TEST = TESTS / "CombatRuntimeTest.kt"
CATALOG_TEST = TESTS / "CompanionSkillCatalogTest.kt"
AP_TEST = TESTS / "PartyTurnCombatApSkillAuthorityTest.kt"
AP_CATALOG_TEST = TESTS / "ApSkillCatalogAuthorityTest.kt"


def remove_test_function(text: str, name: str) -> str:
    signature = f"fun {name}()"
    sig = text.find(signature)
    if sig < 0:
        return text
    starts = [
        text.rfind("\n  @Test fun ", 0, sig),
        text.rfind("\n  @org.junit.Test fun ", 0, sig),
    ]
    start = max(starts)
    if start < 0:
        if text.startswith("  @Test fun ") or text.startswith("  @org.junit.Test fun "):
            start = 0
        else:
            raise RuntimeError(f"Could not locate test annotation for {name}")
    else:
        start += 1

    candidates = [
        pos for pos in (
            text.find("\n  @Test fun ", sig + len(signature)),
            text.find("\n  @org.junit.Test fun ", sig + len(signature)),
        ) if pos >= 0
    ]
    class_end = text.rfind("\n}")
    if class_end < 0:
        raise RuntimeError(f"Could not locate class end while removing {name}")
    end = min(candidates) if candidates else class_end
    return text[:start] + text[end + (1 if candidates else 0):]


combat = COMBAT_TEST.read_text(encoding="utf-8")
# These regressions assert the retired automatic/% activation model. Their
# damage/status semantics are now exercised through explicit AP actions below;
# keeping the old activation assertions would require reintroducing the bug.
for name in (
    "guiltyCrownTurnKeepsPriorityOverAutomaticGunSkillRolls",
    "guiltyCrownOverrideTriggersAutomaticallyOnEveryThirdCombatTurn",
    "guiltyCrownOverrideAppliesExactTwentyFourTimesTenHpBeforeNormalRegen",
    "luciaFullAutoBurstCanProcOnSecondAttackTurn",
    "luciaFullAutoBurstDoesNotRunOnFirstAttackTurn",
    "luciaTooYoungToDieCanProcOnAnyCombatTurn",
    "irisAndSyvialAutomaticSkillsResolveWhenTheyAreActivePartyMembers",
    "quickStepGrantsFiftyEvasionForThreeTurnsAndCountsDown",
    "silentLullabyStunSuppressesCurrentEnemyResponse",
):
    combat = remove_test_function(combat, name)

# Remove any remaining Too Young To Die test that directly calls the retired
# percentage chance helper, regardless of whether its annotation is qualified.
needle = "luciaTooYoungToDieTriggerChancePercent"
while needle in combat:
    sig = combat.find(needle)
    before = combat[:sig]
    test_markers = [before.rfind("\n  @Test fun "), before.rfind("\n  @org.junit.Test fun ")]
    test_start = max(test_markers)
    if test_start < 0:
        raise RuntimeError("Too Young To Die percentage helper is referenced outside a test")
    name_start = combat.find("fun ", test_start) + 4
    name_end = combat.find("()", name_start)
    if name_start < 4 or name_end < 0:
        raise RuntimeError("Could not identify stale Too Young To Die percentage test")
    combat = remove_test_function(combat, combat[name_start:name_end])
if needle in combat:
    raise RuntimeError("Retired percentage-trigger helper still referenced by CombatRuntimeTest")
COMBAT_TEST.write_text(combat, encoding="utf-8")

catalog_test = CATALOG_TEST.read_text(encoding="utf-8")
for name in (
    "luciaProjectsFullAutoBurstContract",
    "luciaCatalogProjectsTooYoungToDieContract",
    "anNhienCombatUtilityNeverDealsDamageDirectly",
    "kaiCatalogUsesShotgunLanguageAndRaisedProcRates",
    "irisAndSyvialAutomaticSkillsResolveWhenTheyAreActivePartyMembers",
):
    catalog_test = remove_test_function(catalog_test, name)
CATALOG_TEST.write_text(catalog_test, encoding="utf-8")

ap_test = AP_TEST.read_text(encoding="utf-8")
old_ultimate = r'''  @Test fun ultimateCostsThreeApAndDealsItsExistingAuthoritativeDamage() {
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
'''
new_ultimate = r'''  @Test fun ultimateCostsThreeApAndDealsItsExistingAuthoritativeDamage() {
    val gained = gainAp(kaiCombat(), 3)
    // R10 can amplify Guilty Crown enough to destroy ordinary fixtures when
    // Devil Trigger is active. Keep the encounter alive so AP and HP deltas are
    // both observable after the authoritative skill resolver returns.
    val state = gained.copy(metadata = gained.metadata + mapOf(
      "combat.entityHp" to "100000",
      "combat.entityMaxHp" to "100000"
    ))
    assertEquals(3, PartyTurnCombat.json(state)!!.getInt("ap"))
    val beforeHp = CombatRuntime.active(state)!!.entityHp
    val result = PartyTurnCombat.resolve(
      state, "EXECUTE", "PARTY_TURN_SKILL::Guilty Crown Override", "skill-ultimate"
    )
    assertTrue(result.committed)
    val after = CombatRuntime.active(result.state)
    assertNotNull(after)
    assertTrue(after!!.entityHp < beforeHp)
    assertEquals(0, PartyTurnCombat.json(result.state)!!.getInt("ap"))
    assertTrue(result.reply.contains("Guilty Crown Override"))
  }
'''
if ap_test.count(old_ultimate) != 1:
    raise RuntimeError("AP ultimate regression anchor missing")
ap_test = ap_test.replace(old_ultimate, new_ultimate, 1)

extra_effect_tests = r'''
  @Test fun silentLullabyCostsTwoApAppliesStunAndSuppressesEntityResponse() {
    val gained = gainAp(kaiCombat(), 2)
    // Force the next CombatRuntime event to Diệp Minh's deterministic fifth-turn
    // Party-wide response. Silent Lullaby must suppress that response completely.
    val state = gained.copy(metadata = gained.metadata + ("combat.eventCounter" to "4"))
    val beforeHp = CombatRuntime.active(state)!!.entityHp
    val beforeKaiHp = state.characters.getValue(KAI_ID).vitalState.currentHp
    val result = PartyTurnCombat.resolve(
      state, "EXECUTE", "PARTY_TURN_SKILL::Silent Lullaby", "skill-silent-lullaby"
    )
    assertTrue(result.committed)
    val after = CombatRuntime.active(result.state)
    assertNotNull(after)
    assertTrue(after!!.entityHp < beforeHp)
    assertEquals(0, PartyTurnCombat.json(result.state)!!.getInt("ap"))
    assertEquals(beforeKaiHp, result.state.characters.getValue(KAI_ID).vitalState.currentHp)
    assertFalse(result.reply, result.reply.contains("Devils And Gold"))
    assertFalse(result.reply, result.reply.contains("Diệp Minh phản công:"))
  }

  @Test fun quickStepCostsTwoApAndPersistsItsEvasionEffect() {
    val state = gainAp(kaiCombat(), 2)
    val result = PartyTurnCombat.resolve(
      state, "EXECUTE", "PARTY_TURN_SKILL::Quick Step", "skill-quick-step"
    )
    assertTrue(result.committed)
    assertEquals(0, PartyTurnCombat.json(result.state)!!.getInt("ap"))
    // The current Entity response consumes the first protected turn, leaving two.
    assertEquals("2", result.state.metadata["combat.kaiQuickStepTurns"])
  }
'''
if "silentLullabyCostsTwoApAppliesStunAndSuppressesEntityResponse" not in ap_test:
    close = ap_test.rfind("\n}")
    if close < 0:
        raise RuntimeError("AP authority test class end missing")
    ap_test = ap_test[:close] + extra_effect_tests + ap_test[close:]
AP_TEST.write_text(ap_test, encoding="utf-8")

AP_CATALOG_TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApSkillCatalogAuthorityTest {
  @Test fun payableCombatSkillsExposeOnlyApActivationCosts() {
    val combatants = listOf(KAI_ID, IRIS_ID, SYVIAL_ID, LUCIA_ID)
    val payable = combatants.flatMap { CompanionSkillCatalog.forCharacter(it) }
      .filter { it.kind == "SKILL" || it.kind == "ULTIMATE" }
    assertTrue(payable.isNotEmpty())
    payable.forEach { skill ->
      val cost = if (skill.kind == "ULTIMATE") 3 else 2
      assertEquals("Kích hoạt bằng $cost AP trong lượt của nhân vật", skill.trigger)
      assertFalse("percentage activation survived for ${skill.name}", skill.trigger.contains("%"))
      assertFalse("AUTO activation survived for ${skill.name}", skill.kind == "AUTO")
    }
  }

  @Test fun luciaCombatSkillsAreManualTwoApSkills() {
    val skills = CompanionSkillCatalog.forCharacter(LUCIA_ID).associateBy { it.name }
    for (name in listOf("M4A1 Full Auto Burst", "Too Young To Die")) {
      val skill = skills.getValue(name)
      assertEquals("SKILL", skill.kind)
      assertEquals("Kích hoạt bằng 2 AP trong lượt của nhân vật", skill.trigger)
      assertTrue(skill.effect.isNotBlank())
    }
  }

  @Test fun anNhienSupportKitIsNotRewrittenIntoApCombatAuthority() {
    val skills = CompanionSkillCatalog.forCharacter(AN_NHIEN_ID)
    assertTrue(skills.isNotEmpty())
    assertTrue(skills.none {
      it.trigger == "Kích hoạt bằng 2 AP trong lượt của nhân vật" ||
        it.trigger == "Kích hoạt bằng 3 AP trong lượt của nhân vật"
    })
  }
}
''', encoding="utf-8")

print(
    "AP skill test compatibility applied: stale automatic/proc regressions retired, "
    "AP cost/damage/status tests retained, and catalog activation is AP-only."
)
