from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
CATALOG = CORE / "CompanionSkillCatalog.kt"
COMBAT_TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"
CATALOG_TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CompanionSkillCatalogTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# ---------------------------------------------------------------------------
# 1.1.69 final balance authority.
# The older durability layer installs the historical 25% Entity evasion value;
# this final release layer intentionally reduces that by 8 percentage points.
# Because Lucia and the Party attack code read the shared constant, the new 17%
# value applies consistently instead of introducing a second evasion mechanic.
# ---------------------------------------------------------------------------
combat = COMBAT.read_text(encoding="utf-8")
combat = replace_once(
    combat,
    "  private const val ENTITY_EVASION_PERCENT = 25\n",
    "  private const val ENTITY_EVASION_PERCENT = 17\n",
    "1.1.69 Entity evasion reduction",
)
combat = combat.replace(
    '"${c.entityName} né đòn (25% evasion) và giành lại áp lực."',
    '"${c.entityName} né đòn (17% evasion) và giành lại áp lực."',
)

# Lucia's full-auto skill is deliberately installed after the complete Entity,
# Party, Devil Trigger and SCP-173 chains. This prevents later compatibility
# patches from silently deleting it and lets the burst respect SCP-173's final
# Concrete Body / OBSERVED direct-damage mitigation.
constants_anchor = "  private const val LUCIA_M4A1_COMBAT_DAMAGE = 26\n"
constants = """  private const val LUCIA_FULL_AUTO_ROUNDS = 30
  private const val LUCIA_FULL_AUTO_BONUS_DAMAGE = 30
  private const val LUCIA_FULL_AUTO_CHANCE_PERCENT = 20
  private const val LUCIA_FULL_AUTO_INTERVAL_TURNS = 2
"""
if "private const val LUCIA_FULL_AUTO_ROUNDS = 30" not in combat:
    combat = replace_once(combat, constants_anchor, constants_anchor + constants, "Lucia full-auto constants")

response_anchor = "    // Enemy response. Diệp Minh uses percentage damage; all other Entity behavior remains unchanged.\n"
response_pos = combat.find(response_anchor)
if response_pos < 0:
    raise RuntimeError("Lucia full-auto: finalized Entity response anchor missing")
companion_pos = combat.rfind("    // COMPANION_SKILLS_R01:", 0, response_pos)
if companion_pos < 0:
    raise RuntimeError("Lucia full-auto: finalized companion skill layer missing")
death_pos = combat.rfind("    if (c.entityHp <= 0) {\n", companion_pos, response_pos)
if death_pos < 0:
    raise RuntimeError("Lucia full-auto: final pre-response Entity death gate missing")

burst = r'''    // LUCIA_FULL_AUTO_BURST_V1: every second ATTACK turn gets one 20% proc check.
    // A successful proc expends a 30-round burst as one skill event. Entity Evasion gates the
    // burst once, matching the existing companion-skill resolution model rather than rolling a
    // second hidden combat turn for every bullet.
    val luciaFullAutoActive = activePartyCharacter(resolvedState, LUCIA_ID) != null
    val luciaFullAutoEligible = intent == Intent.ATTACK &&
      c.eventCounter % LUCIA_FULL_AUTO_INTERVAL_TURNS == 0
    if (luciaFullAutoActive && luciaFullAutoEligible && c.entityHp > 0) {
      val luciaFullAutoProc = roll(c.copy(eventCounter = c.eventCounter + 601), 100)
      if (luciaFullAutoProc < LUCIA_FULL_AUTO_CHANCE_PERCENT) {
        val luciaFullAutoEvasionRoll = roll(c.copy(eventCounter = c.eventCounter + 607), 100)
        if (luciaFullAutoEvasionRoll >= ENTITY_EVASION_PERCENT) {
          val luciaBaseDamage = max(
            LUCIA_M4A1_COMBAT_DAMAGE,
            CharacterStatEngine.weaponDamage(resolvedState, LUCIA_ID)
          )
          val luciaRawPerBullet = LUCIA_FULL_AUTO_BONUS_DAMAGE + luciaBaseDamage
          val luciaPerBulletAfterArmor = max(1, luciaRawPerBullet - profile.armor)
          val luciaRawBurstDamage = LUCIA_FULL_AUTO_ROUNDS * luciaPerBulletAfterArmor
          val luciaResolvedBurstDamage = if (c.entityKey == SCP_173_KEY) {
            scp173DirectDamage(luciaRawBurstDamage, scp173ObservedNow)
          } else {
            luciaRawBurstDamage
          }
          val luciaBurstDamage = min(c.entityHp, luciaResolvedBurstDamage)
          val luciaBurstHp = max(0, c.entityHp - luciaBurstDamage)
          c = c.copy(
            entityHp = luciaBurstHp,
            entityCondition = condition(luciaBurstHp, c.entityMaxHp),
            noise = min(100, c.noise + 45)
          )
          log += "Lucia \"Lục\" tự kích hoạt M4A1 Full Auto Burst: " +
            "$LUCIA_FULL_AUTO_ROUNDS viên, mỗi viên $LUCIA_FULL_AUTO_BONUS_DAMAGE + Base DMG ($luciaBaseDamage) trước Armor; " +
            "tổng -$luciaBurstDamage HP (${c.entityHp}/${c.entityMaxHp})."
        } else {
          log += "${c.entityName} né M4A1 Full Auto Burst của Lucia (${ENTITY_EVASION_PERCENT}% Evasion)."
        }
      }
    }

'''
if "LUCIA_FULL_AUTO_BURST_V1" not in combat:
    combat = combat[:death_pos] + burst + combat[death_pos:]

for marker in (
    "private const val ENTITY_EVASION_PERCENT = 17",
    "private const val LUCIA_FULL_AUTO_ROUNDS = 30",
    "private const val LUCIA_FULL_AUTO_BONUS_DAMAGE = 30",
    "private const val LUCIA_FULL_AUTO_CHANCE_PERCENT = 20",
    "private const val LUCIA_FULL_AUTO_INTERVAL_TURNS = 2",
    "LUCIA_FULL_AUTO_BURST_V1",
    "c.eventCounter % LUCIA_FULL_AUTO_INTERVAL_TURNS == 0",
    "luciaFullAutoProc < LUCIA_FULL_AUTO_CHANCE_PERCENT",
    "luciaFullAutoEvasionRoll >= ENTITY_EVASION_PERCENT",
    "LUCIA_FULL_AUTO_BONUS_DAMAGE + luciaBaseDamage",
    "LUCIA_FULL_AUTO_ROUNDS * luciaPerBulletAfterArmor",
    "scp173DirectDamage(luciaRawBurstDamage, scp173ObservedNow)",
):
    if marker not in combat:
        raise RuntimeError("1.1.69 combat contract missing: " + marker)
if "private const val ENTITY_EVASION_PERCENT = 25" in combat:
    raise RuntimeError("Legacy 25% Entity Evasion survived the 1.1.69 final balance layer")
COMBAT.write_text(combat, encoding="utf-8")


# ---------------------------------------------------------------------------
# Character Detail skill projection.
# ---------------------------------------------------------------------------
catalog = CATALOG.read_text(encoding="utf-8")
if 's("M4A1 Full Auto Burst", "AUTO"' not in catalog:
    lines = catalog.splitlines()
    joint_index = next((i for i, line in enumerate(lines) if 's("M4A1 Joint Attack", "COMMAND"' in line), -1)
    if joint_index < 0:
        raise RuntimeError("Lucia full-auto catalog: M4A1 Joint Attack anchor missing")
    lines[joint_index] = lines[joint_index].rstrip().rstrip(",") + ","
    lines.insert(
        joint_index + 1,
        '    s("M4A1 Full Auto Burst", "AUTO", "20% mỗi 2 combat turn hợp lệ khi Party chọn TẤN CÔNG", "Xả đúng 30 viên; mỗi viên gây 30 + Base DMG trước Armor; toàn loạt chịu Entity Evasion gate.")',
    )
    catalog = "\n".join(lines) + ("\n" if catalog.endswith("\n") else "")
if '"20% mỗi 2 combat turn hợp lệ khi Party chọn TẤN CÔNG"' not in catalog:
    raise RuntimeError("Lucia full-auto catalog trigger missing")
if '"Xả đúng 30 viên; mỗi viên gây 30 + Base DMG trước Armor; toàn loạt chịu Entity Evasion gate."' not in catalog:
    raise RuntimeError("Lucia full-auto catalog effect missing")
CATALOG.write_text(catalog, encoding="utf-8")


# ---------------------------------------------------------------------------
# Regression coverage. The runtime proc test varies only the persisted combat
# seed while keeping the event counter on turn 2, so it proves the 20% gate can
# actually trigger without making the test depend on one magic production seed.
# ---------------------------------------------------------------------------
combat_test = COMBAT_TEST.read_text(encoding="utf-8")
combat_regression = r'''
  @org.junit.Test fun luciaFullAutoBurstCanProcOnSecondAttackTurn() {
    var sawBurst = false
    for (seed in 1L..500L) {
      var state = LuciaCanon.ensure(GameState.initial())
      state = state.copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID)))
      state = CombatRuntime.start(state, "scp_173")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to "1",
        "combat.seed" to seed.toString(),
        "passive.devilTrigger.kai.cooldownTurns" to "5"
      ))
      val result = CombatRuntime.resolve(state, "EXECUTE", "TẤN CÔNG")
      if (result.reply.contains("M4A1 Full Auto Burst")) {
        sawBurst = true
        break
      }
    }
    org.junit.Assert.assertTrue("Lucia 20% full-auto proc should be reachable on an eligible second turn", sawBurst)
  }

  @org.junit.Test fun luciaFullAutoBurstDoesNotRunOnFirstAttackTurn() {
    var state = LuciaCanon.ensure(GameState.initial())
    state = state.copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID)))
    state = CombatRuntime.start(state, "scp_173")
    state = state.copy(metadata = state.metadata + mapOf(
      "combat.eventCounter" to "0",
      "combat.seed" to "7",
      "passive.devilTrigger.kai.cooldownTurns" to "5"
    ))
    val result = CombatRuntime.resolve(state, "EXECUTE", "TẤN CÔNG")
    org.junit.Assert.assertFalse(result.reply.contains("M4A1 Full Auto Burst"))
  }
'''
if "luciaFullAutoBurstCanProcOnSecondAttackTurn" not in combat_test:
    close = combat_test.rfind("\n}")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest class closing brace missing")
    combat_test = combat_test[:close] + "\n" + combat_regression.rstrip() + combat_test[close:]
for marker in (
    "luciaFullAutoBurstCanProcOnSecondAttackTurn",
    '"combat.eventCounter" to "1"',
    "luciaFullAutoBurstDoesNotRunOnFirstAttackTurn",
):
    if marker not in combat_test:
        raise RuntimeError("Lucia full-auto combat regression missing: " + marker)
COMBAT_TEST.write_text(combat_test, encoding="utf-8")

catalog_test = CATALOG_TEST.read_text(encoding="utf-8")
catalog_regression = r'''
  @org.junit.Test fun luciaProjectsFullAutoBurstContract() {
    val skill = CompanionSkillCatalog.forCharacter(LUCIA_ID).first { it.name == "M4A1 Full Auto Burst" }
    org.junit.Assert.assertEquals("AUTO", skill.kind)
    org.junit.Assert.assertTrue(skill.trigger.contains("20%"))
    org.junit.Assert.assertTrue(skill.trigger.contains("2 combat turn"))
    org.junit.Assert.assertTrue(skill.effect.contains("30 viên"))
    org.junit.Assert.assertTrue(skill.effect.contains("30 + Base DMG"))
    org.junit.Assert.assertTrue(skill.effect.contains("Entity Evasion"))
  }
'''
if "luciaProjectsFullAutoBurstContract" not in catalog_test:
    close = catalog_test.rfind("\n}")
    if close < 0:
        raise RuntimeError("CompanionSkillCatalogTest class closing brace missing")
    catalog_test = catalog_test[:close] + "\n" + catalog_regression.rstrip() + catalog_test[close:]
CATALOG_TEST.write_text(catalog_test, encoding="utf-8")

print(
    "Backroom 1.1.69 balance applied: Entity Evasion 25% -> 17%; Lucia M4A1 Full Auto Burst "
    "fires 30 rounds at 30 + Base DMG with a 20% proc check every second ATTACK turn."
)
