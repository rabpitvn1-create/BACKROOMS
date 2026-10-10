# LIFEFORM_R02_FINAL_AUTHORITY: Hound x1.3 base; exactly 3 ATK-only skills per Entity; Bleed/Poison only.
from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


combat = COMBAT.read_text(encoding="utf-8")
combat = replace_once(combat, "  // LIFEFORM_TRIO_R01\n", "  // LIFEFORM_TRIO_R02\n", "Lifeform revision marker")

for old, new, label in (
    ("  private const val LIFEFORM_BASE_MAX_HP = 24\n", "  private const val LIFEFORM_BASE_MAX_HP = 104\n", "Lifeform HP = Hound x1.3"),
    ("  private const val LIFEFORM_BASE_ATTACK = 5\n", "  private const val LIFEFORM_BASE_ATTACK = 20\n", "Lifeform ATK = Hound x1.3"),
    ("  private const val LIFEFORM_BASE_ARMOR = 1\n", "  private const val LIFEFORM_BASE_ARMOR = 3\n", "Lifeform Armor = Hound x1.3"),
    ("  private const val LIFEFORM_BASE_AGGRESSION = 2\n", "  private const val LIFEFORM_BASE_AGGRESSION = 10\n", "Lifeform Aggression = Hound x1.3"),
):
    combat = replace_once(combat, old, new, label)

for retired in (
    '  private const val BLACKROOT_ROOTBIND_PROC_PERCENT = 30\n',
    '  private const val SINEW_LONGSTEP_PROC_PERCENT = 25\n',
    '  private const val HOLLOW_REKNIT_PROC_PERCENT = 20\n',
    '  private const val HOLLOW_REKNIT_HEAL = 4\n',
):
    combat = replace_once(combat, retired, "", "retired Lifeform proc constant")

status_constants_anchor = '  private const val LIFEFORM_BASE_AGGRESSION = 10\n'
status_constants = '''  private const val LIFEFORM_BASE_AGGRESSION = 10
  private const val LIFEFORM_BLEED_TURNS = 3
  private const val LIFEFORM_BLEED_MAX_HP_PERCENT = 3
  private const val LIFEFORM_POISON_TURNS = 3
  private const val LIFEFORM_POISON_MAX_HP_PERCENT = 2
  private const val LIFEFORM_BLEED_TURNS_KEY = "combat.lifeformBleedTurns"
  private const val LIFEFORM_POISON_TURNS_KEY = "combat.lifeformPoisonTurns"
'''
combat = replace_once(combat, status_constants_anchor, status_constants, "Lifeform Bleed/Poison constants")

old_helper_start = combat.find("  private fun applyLifeformProc(c: Snapshot, log: MutableList<String>): Snapshot {\n")
old_helper_end = combat.find("  private fun encode(state: GameState, c: Snapshot): GameState {\n", old_helper_start)
if old_helper_start < 0 or old_helper_end < 0:
    raise RuntimeError("Old Lifeform proc helper boundary missing")

new_helpers = r'''  private data class LifeformSkillSpec(
    val name: String,
    val procPercent: Int,
    val attackBonusPercent: Int,
    val bleed: Boolean,
    val poison: Boolean
  )

  private fun lifeformSkills(entityKey: String): List<LifeformSkillSpec> = when (entityKey) {
    "blackroot_sentinel" -> listOf(
      LifeformSkillSpec("Thorned Hemorrhage", 25, 15, bleed = true, poison = false),
      LifeformSkillSpec("Blight Sap", 20, 25, bleed = false, poison = true),
      LifeformSkillSpec("Crimson Mycotoxin", 10, 40, bleed = true, poison = true)
    )
    "sinew_strider" -> listOf(
      LifeformSkillSpec("Tendon Ripper", 25, 15, bleed = true, poison = false),
      LifeformSkillSpec("Septic Thread", 20, 25, bleed = false, poison = true),
      LifeformSkillSpec("Venomous Flay", 10, 40, bleed = true, poison = true)
    )
    "hollow_grasper" -> listOf(
      LifeformSkillSpec("Hollow Laceration", 25, 15, bleed = true, poison = false),
      LifeformSkillSpec("Carrion Toxin", 20, 25, bleed = false, poison = true),
      LifeformSkillSpec("Necrotic Clutch", 10, 40, bleed = true, poison = true)
    )
    else -> emptyList()
  }

  private fun rollLifeformSkill(c: Snapshot): LifeformSkillSpec? {
    val specs = lifeformSkills(c.entityKey)
    if (specs.isEmpty()) return null
    val procRoll = roll(c.copy(eventCounter = c.eventCounter + 211), 100)
    var cursor = 0
    for (spec in specs) {
      cursor += spec.procPercent
      if (procRoll < cursor) return spec
    }
    return null
  }

  private fun lifeformAttack(baseAttack: Int, skill: LifeformSkillSpec?): Int =
    if (skill == null) baseAttack else max(1, baseAttack * (100 + skill.attackBonusPercent) / 100)

  private fun withLifeformCounter(state: GameState, key: String, value: Int): GameState {
    val metadata = state.metadata.toMutableMap()
    if (value > 0) metadata[key] = value.toString() else metadata.remove(key)
    return state.copy(metadata = metadata)
  }

'''
combat = combat[:old_helper_start] + new_helpers + combat[old_helper_end:]
combat = replace_once(combat, "    c = applyLifeformProc(c, log)\n\n", "", "remove retired Lifeform proc call")

resolve_start = combat.index('  fun resolve(state: GameState, actionKind: String, action: String): Resolution {\n')
resolve_end = combat.index('\n  fun toJson(state: GameState): JSONObject?', resolve_start)
resolve = combat[resolve_start:resolve_end]

resolve = replace_once(
    resolve,
    "    var resolvedState = state\n",
    '''    var resolvedState = state
    var lifeformBleedTurns = state.metadata[LIFEFORM_BLEED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, LIFEFORM_BLEED_TURNS) ?: 0
    var lifeformPoisonTurns = state.metadata[LIFEFORM_POISON_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, LIFEFORM_POISON_TURNS) ?: 0
''',
    "Lifeform persistent status counters",
)

status_ticks = r'''    if (c.playerHp > 0 && lifeformBleedTurns > 0) {
      val bleedDamage = percentDamage(c.playerMaxHp, LIFEFORM_BLEED_MAX_HP_PERCENT)
      val hp = max(0, c.playerHp - bleedDamage)
      recordFeedback("actor", c.playerHp, hp, status = FeedbackStatus.BLEED, phase = "entity")
      c = c.copy(playerHp = hp)
      lifeformBleedTurns = max(0, lifeformBleedTurns - 1)
      resolvedState = withLifeformCounter(resolvedState, LIFEFORM_BLEED_TURNS_KEY, lifeformBleedTurns)
      log += "Lifeform Bleed: Kai -" + bleedDamage + " HP (" + LIFEFORM_BLEED_MAX_HP_PERCENT + "% Max HP); còn " + lifeformBleedTurns + " turn."
    }
    if (c.playerHp > 0 && lifeformPoisonTurns > 0) {
      val poisonDamage = percentDamage(c.playerMaxHp, LIFEFORM_POISON_MAX_HP_PERCENT)
      val hp = max(0, c.playerHp - poisonDamage)
      recordFeedback("actor", c.playerHp, hp, status = FeedbackStatus.POISON, phase = "entity")
      c = c.copy(playerHp = hp)
      lifeformPoisonTurns = max(0, lifeformPoisonTurns - 1)
      resolvedState = withLifeformCounter(resolvedState, LIFEFORM_POISON_TURNS_KEY, lifeformPoisonTurns)
      log += "Lifeform Poison: Kai -" + poisonDamage + " HP (" + LIFEFORM_POISON_MAX_HP_PERCENT + "% Max HP); còn " + lifeformPoisonTurns + " turn."
    }

'''
resolve = replace_once(resolve, "    when (intent) {\n", status_ticks + "    when (intent) {\n", "Lifeform status ticks")

resolve = replace_once(
    resolve,
    "      if (incomingRoll < enemyChance) {\n",
    "      if (incomingRoll < enemyChance) {\n        val lifeformSkill = rollLifeformSkill(c)\n",
    "Lifeform skill selection on successful Entity hit",
)
resolve = replace_once(
    resolve,
    "EntityPowerScaling.scale(profile.attack, c.progressionRank)",
    "EntityPowerScaling.scale(lifeformAttack(profile.attack, lifeformSkill), c.progressionRank)",
    "Lifeform ATK bonus application",
)

skill_effect = r'''        if (lifeformSkill != null) {
          if (c.playerHp > 0 && lifeformSkill.bleed) {
            lifeformBleedTurns = LIFEFORM_BLEED_TURNS
            resolvedState = withLifeformCounter(resolvedState, LIFEFORM_BLEED_TURNS_KEY, lifeformBleedTurns)
          }
          if (c.playerHp > 0 && lifeformSkill.poison) {
            lifeformPoisonTurns = LIFEFORM_POISON_TURNS
            resolvedState = withLifeformCounter(resolvedState, LIFEFORM_POISON_TURNS_KEY, lifeformPoisonTurns)
          }
          val effects = when {
            lifeformSkill.bleed && lifeformSkill.poison -> "Bleed + Poison"
            lifeformSkill.bleed -> "Bleed"
            else -> "Poison"
          }
          log += lifeformSkill.name + " proc: +" + lifeformSkill.attackBonusPercent + "% ATK; " + effects + "."
        }
'''
resolve = replace_once(
    resolve,
    '        log += if (c.entityKey == DIEP_MINH_KEY) {\n',
    skill_effect + '        log += if (c.entityKey == DIEP_MINH_KEY) {\n',
    "Lifeform skill status application",
)

combat = combat[:resolve_start] + resolve + combat[resolve_end:]

for marker in (
    "LIFEFORM_TRIO_R02",
    "LIFEFORM_BASE_MAX_HP = 104",
    "LIFEFORM_BASE_ATTACK = 20",
    "LIFEFORM_BASE_ARMOR = 3",
    "LIFEFORM_BASE_AGGRESSION = 10",
    'LifeformSkillSpec("Thorned Hemorrhage", 25, 15, bleed = true, poison = false)',
    'LifeformSkillSpec("Blight Sap", 20, 25, bleed = false, poison = true)',
    'LifeformSkillSpec("Crimson Mycotoxin", 10, 40, bleed = true, poison = true)',
    'LifeformSkillSpec("Tendon Ripper", 25, 15, bleed = true, poison = false)',
    'LifeformSkillSpec("Septic Thread", 20, 25, bleed = false, poison = true)',
    'LifeformSkillSpec("Venomous Flay", 10, 40, bleed = true, poison = true)',
    'LifeformSkillSpec("Hollow Laceration", 25, 15, bleed = true, poison = false)',
    'LifeformSkillSpec("Carrion Toxin", 20, 25, bleed = false, poison = true)',
    'LifeformSkillSpec("Necrotic Clutch", 10, 40, bleed = true, poison = true)',
    "LIFEFORM_BLEED_MAX_HP_PERCENT = 3",
    "LIFEFORM_POISON_MAX_HP_PERCENT = 2",
    "FeedbackStatus.BLEED",
    "FeedbackStatus.POISON",
    "lifeformAttack(profile.attack, lifeformSkill)",
):
    if marker not in combat:
        raise RuntimeError("Lifeform R02 combat contract missing: " + marker)

for retired in ("Rootbind proc:", "Longstep Rupture proc:", "Reknit proc:", "applyLifeformProc("):
    if retired in combat:
        raise RuntimeError("Retired Lifeform behavior survived: " + retired)

COMBAT.write_text(combat, encoding="utf-8")


main = MAIN.read_text(encoding="utf-8")
lines = main.splitlines(keepends=True)
matches = [index for index, line in enumerate(lines) if "LIFEFORM TRIO HARD LOCK:" in line]
if len(matches) != 1:
    raise RuntimeError(f"Expected one Lifeform GM hard-lock line, found {len(matches)}")
lines[matches[0]] = (
    '      "LIFEFORM TRIO HARD LOCK: lifeformEncounter là một roll độc lập đúng 2% cho cả họ Lifeform, không phải 2% cho từng cá thể. '
    'Khi success=true, dùng đúng lifeformEntityKey đã roll. Mỗi Lifeform có đúng 3 skill; skill chỉ tăng %ATK và chỉ gây Bleed/Poison. '
    'Ba proc dùng một roll độc quyền theo thứ tự 25% / 20% / 10%, tương ứng +15% / +25% / +40% ATK; skill 1 gây Bleed, skill 2 gây Poison, skill 3 gây Bleed + Poison. '
    'Ba Entity này không nằm trong shared roaming pool. " +\n'
)
main = "".join(lines)
for retired in ("Rootbind 30%", "Longstep Rupture 25%", "Reknit 20%"):
    if retired in main:
        raise RuntimeError("Retired Lifeform GM rule survived: " + retired)
MAIN.write_text(main, encoding="utf-8")


test = TEST.read_text(encoding="utf-8")
old_test_start = test.find("  @Test fun lifeformTrioUsesThirtyPercentHoundBaseBeforeSharedDurability() {\n")
class_close = test.rfind("}\n")
if old_test_start < 0 or class_close < 0 or old_test_start >= class_close:
    raise RuntimeError("Old Lifeform test tail missing")

new_tests = r'''  @Test fun lifeformTrioUsesOnePointThreeHoundBaseBeforeSharedDurability() {
    for (key in listOf("blackroot_sentinel", "sinew_strider", "hollow_grasper")) {
      val active = CombatRuntime.active(CombatRuntime.start(GameState.initial(), key))!!
      assertEquals(134, active.entityMaxHp)
      assertEquals(134, active.entityHp)
    }
  }

  @Test fun lifeformTrioExposesThreeAttackStatusSkillsPerEntity() {
    val expected = mapOf(
      "blackroot_sentinel" to setOf("Thorned Hemorrhage", "Blight Sap", "Crimson Mycotoxin"),
      "sinew_strider" to setOf("Tendon Ripper", "Septic Thread", "Venomous Flay"),
      "hollow_grasper" to setOf("Hollow Laceration", "Carrion Toxin", "Necrotic Clutch")
    )
    expected.forEach { (key, wanted) ->
      val seen = mutableSetOf<String>()
      for (counter in 0..700) {
        var state = CombatRuntime.start(GameState.initial(), key)
        state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
        val result = CombatRuntime.resolve(state, "SEARCH", "quan sát")
        wanted.forEach { skill -> if (result.reply.contains(skill)) seen += skill }
        if (seen == wanted) break
      }
      assertEquals("All three skills should proc for " + key, wanted, seen)
    }
  }

  @Test fun lifeformBleedAndPoisonAreTheOnlyPersistentStatusDamage() {
    var state = CombatRuntime.start(GameState.initial(), "blackroot_sentinel")
    state = state.copy(metadata = state.metadata + mapOf(
      "combat.lifeformBleedTurns" to "1",
      "combat.lifeformPoisonTurns" to "1"
    ))
    val before = CombatRuntime.active(state)!!
    val result = CombatRuntime.resolve(state, "EVADE", "né đòn")
    val bleed = result.feedback.single { it.status == CombatRuntime.FeedbackStatus.BLEED && it.target == "actor" }
    val poison = result.feedback.single { it.status == CombatRuntime.FeedbackStatus.POISON && it.target == "actor" }
    assertEquals(maxOf(1, (before.playerMaxHp * 3 + 99) / 100), bleed.amount)
    assertEquals(maxOf(1, (before.playerMaxHp * 2 + 99) / 100), poison.amount)
  }
'''
test = test[:old_test_start] + new_tests + test[class_close:]
for marker in (
    "lifeformTrioUsesOnePointThreeHoundBaseBeforeSharedDurability",
    "assertEquals(134, active.entityMaxHp)",
    "lifeformTrioExposesThreeAttackStatusSkillsPerEntity",
    "lifeformBleedAndPoisonAreTheOnlyPersistentStatusDamage",
    '"Crimson Mycotoxin"',
    '"Venomous Flay"',
    '"Necrotic Clutch"',
):
    if marker not in test:
        raise RuntimeError("Lifeform R02 test contract missing: " + marker)
for retired in (
    "lifeformTrioUsesThirtyPercentHoundBaseBeforeSharedDurability",
    "eachLifeformProcCanTriggerDeterministically",
    '"Rootbind proc"',
    '"Longstep Rupture proc"',
    '"Reknit proc"',
):
    if retired in test:
        raise RuntimeError("Retired Lifeform test survived: " + retired)
TEST.write_text(test, encoding="utf-8")

print("Lifeform R02 rebalance applied: Hound x1.3 stats; three ATK-only skills per Entity; Bleed/Poison only.")
