from pathlib import Path

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatCore.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/KaiSkillCombatGeneratedTest.kt"

combat = COMBAT.read_text(encoding="utf-8")
old = '''  private fun rewardKill(party: List<MutableFighter>) {
    party.filter { it.alive() }.forEach { fighter ->
      fighter.stats = CombatProgression.awardEntityKill(fighter.id, fighter.stats)
      if (fighter.isKai()) refreshDevilTriggerHpBonus(fighter)
    }
  }'''
new = '''  private fun rewardKill(party: List<MutableFighter>) {
    party.filter { it.alive() }.forEach { fighter ->
      if (fighter.isKai() && fighter.devilTriggerActive()) {
        // CombatProgression clamps currentHp to the base max. Strip the temporary DT HP first,
        // award Survival on the real base pool, then rebuild the temporary x5 HP envelope.
        val baseHp = (fighter.stats.currentHp - fighter.devilTriggerHpBonus)
          .coerceIn(0, fighter.stats.maxHp)
        val awarded = CombatProgression.awardEntityKill(
          fighter.id,
          fighter.stats.copy(currentHp = baseHp)
        )
        fighter.stats = awarded
        fighter.devilTriggerHpBonus = KaiSkillBook.devilTriggerHpBonus(awarded)
        val boostedMax = CombatRules.maxHp(
          awarded.hpStat * KaiSkillBook.DEVIL_TRIGGER_STAT_MULTIPLIER
        )
        fighter.stats = fighter.stats.copy(
          currentHp = (awarded.currentHp + fighter.devilTriggerHpBonus).coerceAtMost(boostedMax)
        )
      } else {
        fighter.stats = CombatProgression.awardEntityKill(fighter.id, fighter.stats)
      }
    }
  }'''
if new not in combat:
    if combat.count(old) != 1:
        raise RuntimeError(f"Kai DT progression compatibility: expected one rewardKill anchor, found {combat.count(old)}")
    combat = combat.replace(old, new, 1)
COMBAT.write_text(combat, encoding="utf-8")

# Add a regression test to the generated patched-source suite.
test = TEST.read_text(encoding="utf-8")
marker = "  @Test fun characterDetailJsonExposesKaiSkillsOnly() {"
extra = '''  @Test fun survivalGrowthDuringDevilTriggerDoesNotLoseTemporaryHpEnvelope() {
    // Trigger DT immediately and use a high-damage GCO to finish one Entity. The final persisted
    // HP must be back on the normal pool, never above maxHp and never corrupted by temporary DT HP.
    val rolls = listOf(
      0.99, // Combat Analysis
      0.0,  // Devil Trigger
      0.99, // Controlled Burst
      0.99, // Weak Point Shot
      0.99, // CQC Break
      0.0   // Guilty Crown
    )
    val result = AutoTurnCombatEngine(SequenceRandom(rolls)).resolve(
      "DT_SURVIVAL_COMPAT",
      listOf(kai()),
      listOf("ENTITY.HOUND"),
      0
    )
    val kaiAfter = result.party.first { it.id == KAI_ID }.stats
    assertTrue(kaiAfter.currentHp in 0..kaiAfter.maxHp)
    assertEquals(1, kaiAfter.survival)
  }

'''
if "survivalGrowthDuringDevilTriggerDoesNotLoseTemporaryHpEnvelope" not in test:
    if test.count(marker) != 1:
        raise RuntimeError("Kai DT progression test anchor missing")
    test = test.replace(marker, extra + marker, 1)
TEST.write_text(test, encoding="utf-8")

for token in [
    "Strip the temporary DT HP first",
    "awarded.currentHp + fighter.devilTriggerHpBonus",
]:
    if token not in COMBAT.read_text(encoding="utf-8"):
        raise RuntimeError(f"Kai DT progression compatibility token missing: {token}")

print("Kai Devil Trigger Survival progression compatibility applied.")
