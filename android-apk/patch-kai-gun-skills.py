from pathlib import Path

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


combat = COMBAT.read_text(encoding="utf-8")

# This patch runs after the unique Diệp Minh authority. It extends the final generated CombatRuntime
# instead of replacing earlier combat systems, so Guilty Crown, Entity durability and boss behavior
# remain intact. The four passive rolls are deterministic and independent on eligible non-Override turns.
constants_old = '  private const val DIEP_MINH_ULTIMATE_PERCENT = 5\n'
constants_new = '''  private const val DIEP_MINH_ULTIMATE_PERCENT = 5
  private const val KAI_LAST_REQUIEM_CHANCE_PERCENT = 30
  private const val KAI_LAST_REQUIEM_DAMAGE_PERCENT = 170
  private const val KAI_LAST_REQUIEM_BLEED_TURNS = 3
  private const val KAI_LAST_REQUIEM_BLEED_MAX_HP_PERCENT = 5
  private const val KAI_SILENT_LULLABY_CHANCE_PERCENT = 20
  private const val KAI_SILENT_LULLABY_DAMAGE_PERCENT = 130
  private const val KAI_SALVATION_CHANCE_PERCENT = 20
  private const val KAI_SALVATION_DAMAGE_PERCENT = 147
  private const val KAI_QUICK_STEP_CHANCE_PERCENT = 30
  private const val KAI_QUICK_STEP_EVASION_BONUS_PERCENT = 50
  private const val KAI_QUICK_STEP_DURATION_TURNS = 3
  private const val KAI_BLEED_TURNS_KEY = "combat.kaiBleedTurns"
  private const val KAI_QUICK_STEP_TURNS_KEY = "combat.kaiQuickStepTurns"
'''
combat = replace_once(combat, constants_old, constants_new, "Kai automatic gun-skill constants")

locals_old = '''    val log = mutableListOf<String>()
    var resolvedState = state
'''
locals_new = '''    val log = mutableListOf<String>()
    var resolvedState = state
    var bleedTurns = state.metadata[KAI_BLEED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, KAI_LAST_REQUIEM_BLEED_TURNS) ?: 0
    var quickStepTurns = state.metadata[KAI_QUICK_STEP_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, KAI_QUICK_STEP_DURATION_TURNS) ?: 0
    var entityStunnedThisTurn = false
'''
combat = replace_once(combat, locals_old, locals_new, "Kai automatic gun-skill combat state")

helper_anchor = '''  private data class PartyPercentDamage(
'''
helper_block = r'''  private fun withCombatCounter(state: GameState, key: String, value: Int): GameState {
    val metadata = state.metadata.toMutableMap()
    if (value > 0) metadata[key] = value.toString() else metadata.remove(key)
    return state.copy(metadata = metadata)
  }

  private fun weaponSkillDamage(weaponDamage: Int, percent: Int, armor: Int): Int =
    max(1, ((max(1, weaponDamage) * percent + 99) / 100) - armor)

'''
if 'private fun weaponSkillDamage(' not in combat:
    combat = replace_once(combat, helper_anchor, helper_block + helper_anchor, "Kai automatic gun-skill helpers")

# Existing Bleeding ticks before this turn's passive rolls. Re-applying Last Requiem later in the turn
# refreshes the future duration to exactly three turns instead of stacking parallel Bleeding instances.
first_death_anchor = '''    if (c.entityHp <= 0) {
'''
bleed_block = '''    if (c.entityHp > 0 && bleedTurns > 0) {
      val bleedDamage = percentDamage(c.entityMaxHp, KAI_LAST_REQUIEM_BLEED_MAX_HP_PERCENT)
      val hp = max(0, c.entityHp - bleedDamage)
      bleedTurns = max(0, bleedTurns - 1)
      resolvedState = withCombatCounter(resolvedState, KAI_BLEED_TURNS_KEY, bleedTurns)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Bleeding từ The Last Requiem gây -$bleedDamage HP (${KAI_LAST_REQUIEM_BLEED_MAX_HP_PERCENT}% Max HP; ${c.entityHp}/${c.entityMaxHp}); còn $bleedTurns turn."
    }

'''
if 'Bleeding từ The Last Requiem gây' not in combat:
    combat = replace_once(combat, first_death_anchor, bleed_block + first_death_anchor, "The Last Requiem Bleeding tick")

response_anchor = '''    // Enemy response. Diệp Minh uses percentage damage; all other Entity behavior remains unchanged.
'''
skills_block = r'''    val isGuiltyCrownTurn = c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0
    if (!isGuiltyCrownTurn && c.entityHp > 0) {
      val weaponDamage = CharacterStatEngine.weaponDamage(resolvedState, KAI_ID)

      if (roll(c.copy(eventCounter = c.eventCounter + 101), 100) < KAI_LAST_REQUIEM_CHANCE_PERCENT) {
        val damage = weaponSkillDamage(weaponDamage, KAI_LAST_REQUIEM_DAMAGE_PERCENT, profile.armor)
        val hp = max(0, c.entityHp - damage)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 22))
        bleedTurns = KAI_LAST_REQUIEM_BLEED_TURNS
        resolvedState = withCombatCounter(resolvedState, KAI_BLEED_TURNS_KEY, bleedTurns)
        log += "The Last Requiem tự động kích hoạt: 4 phát vào khớp vai, ${KAI_LAST_REQUIEM_DAMAGE_PERCENT}% DMG = -$damage HP; Bleeding ${KAI_LAST_REQUIEM_BLEED_TURNS} turn, ${KAI_LAST_REQUIEM_BLEED_MAX_HP_PERCENT}% Max HP/turn."
      }

      if (c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 113), 100) < KAI_SILENT_LULLABY_CHANCE_PERCENT) {
        val damage = weaponSkillDamage(weaponDamage, KAI_SILENT_LULLABY_DAMAGE_PERCENT, profile.armor)
        val hp = max(0, c.entityHp - damage)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 18))
        entityStunnedThisTurn = true
        log += "Silent Lullaby tự động kích hoạt: Kai bật lên cao, 4 viên ghim cùng điểm trên ngực, ${KAI_SILENT_LULLABY_DAMAGE_PERCENT}% DMG = -$damage HP; Stun 1 turn."
      }

      if (c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 127), 100) < KAI_SALVATION_CHANCE_PERCENT) {
        val damage = weaponSkillDamage(weaponDamage, KAI_SALVATION_DAMAGE_PERCENT, profile.armor)
        val hp = max(0, c.entityHp - damage)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 16))
        log += "Salvation tự động kích hoạt: Kai ném súng ra sau mục tiêu, dịch chuyển tức thời tới vị trí súng và bắn nhanh 2 phát, ${KAI_SALVATION_DAMAGE_PERCENT}% DMG = -$damage HP."
      }

      if (c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 139), 100) < KAI_QUICK_STEP_CHANCE_PERCENT) {
        quickStepTurns = KAI_QUICK_STEP_DURATION_TURNS
        resolvedState = withCombatCounter(resolvedState, KAI_QUICK_STEP_TURNS_KEY, quickStepTurns)
        log += "Quick Step tự động kích hoạt: dịch chuyển ngắn liên tục, +${KAI_QUICK_STEP_EVASION_BONUS_PERCENT}% Evasion trong ${KAI_QUICK_STEP_DURATION_TURNS} turn."
      }
    }

    if (c.entityHp <= 0) {
      val persisted = encode(resolvedState, c.copy(phase = Phase.RESOLVED, entityCondition = EntityCondition.DESTROYED))
      val cleared = clearCombatOnly(persisted)
      return Resolution(cleared, true, log.joinToString(" ") + " ${c.entityName} đã bị tiêu diệt.", entityDestroyed = true)
    }

'''
if 'The Last Requiem tự động kích hoạt' not in combat:
    combat = replace_once(combat, response_anchor, skills_block + response_anchor, "Kai automatic gun-skill proc block")

# Silent Lullaby suppresses the entire current-turn Entity response, including a boss response.
response_if_old = '''    if (c.entityKey == DIEP_MINH_KEY && c.eventCounter % DIEP_MINH_ULTIMATE_INTERVAL_TURNS == 0) {
'''
response_if_new = '''    if (entityStunnedThisTurn) {
      log += "Silent Lullaby: ${c.entityName} bị Stun và mất lượt phản ứng hiện tại."
    } else if (c.entityKey == DIEP_MINH_KEY && c.eventCounter % DIEP_MINH_ULTIMATE_INTERVAL_TURNS == 0) {
'''
combat = replace_once(combat, response_if_old, response_if_new, "Silent Lullaby stun enemy response")

# Quick Step is +50 percentage points of Evasion against ordinary targeted responses. It deliberately
# does not rewrite Devils And Gold's party-wide Max-HP pulse; Stun can still suppress that turn entirely.
enemy_chance_old = '''      val enemyChance = (profile.aggression * 8 - defense + max(0, -c.momentum) * 7).coerceIn(8, 88)
'''
enemy_chance_new = '''      val quickStepEvasion = if (quickStepTurns > 0) KAI_QUICK_STEP_EVASION_BONUS_PERCENT else 0
      val enemyChance = (profile.aggression * 8 - defense + max(0, -c.momentum) * 7 - quickStepEvasion).coerceIn(0, 88)
'''
combat = replace_once(combat, enemy_chance_old, enemy_chance_new, "Quick Step evasion bonus")

miss_old = '''        log += "${c.entityName} không xuyên được thế phòng thủ/di chuyển của Kai."
'''
miss_new = '''        log += if (quickStepTurns > 0) {
          "Quick Step khiến ${c.entityName} hụt đòn; +${KAI_QUICK_STEP_EVASION_BONUS_PERCENT}% Evasion đang hoạt động."
        } else {
          "${c.entityName} không xuyên được thế phòng thủ/di chuyển của Kai."
        }
'''
combat = replace_once(combat, miss_old, miss_new, "Quick Step evasion combat log")

regen_anchor = '''    val entityHpBeforeRegen = c.entityHp
'''
quick_step_countdown = '''    if (quickStepTurns > 0) {
      quickStepTurns = max(0, quickStepTurns - 1)
      resolvedState = withCombatCounter(resolvedState, KAI_QUICK_STEP_TURNS_KEY, quickStepTurns)
    }

'''
if 'resolvedState = withCombatCounter(resolvedState, KAI_QUICK_STEP_TURNS_KEY, quickStepTurns)\n    }\n\n    val entityHpBeforeRegen' not in combat:
    combat = replace_once(combat, regen_anchor, quick_step_countdown + regen_anchor, "Quick Step three-turn countdown")

for marker in (
    'private const val KAI_LAST_REQUIEM_CHANCE_PERCENT = 30',
    'private const val KAI_LAST_REQUIEM_DAMAGE_PERCENT = 170',
    'private const val KAI_LAST_REQUIEM_BLEED_TURNS = 3',
    'private const val KAI_LAST_REQUIEM_BLEED_MAX_HP_PERCENT = 5',
    'private const val KAI_SILENT_LULLABY_CHANCE_PERCENT = 20',
    'private const val KAI_SILENT_LULLABY_DAMAGE_PERCENT = 130',
    'private const val KAI_SALVATION_CHANCE_PERCENT = 20',
    'private const val KAI_SALVATION_DAMAGE_PERCENT = 147',
    'private const val KAI_QUICK_STEP_CHANCE_PERCENT = 30',
    'private const val KAI_QUICK_STEP_EVASION_BONUS_PERCENT = 50',
    'private const val KAI_QUICK_STEP_DURATION_TURNS = 3',
    'The Last Requiem tự động kích hoạt',
    'Silent Lullaby tự động kích hoạt',
    'Salvation tự động kích hoạt',
    'Quick Step tự động kích hoạt',
    'entityStunnedThisTurn',
    'val quickStepEvasion = if (quickStepTurns > 0) KAI_QUICK_STEP_EVASION_BONUS_PERCENT else 0',
    'val isGuiltyCrownTurn = c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0',
):
    if marker not in combat:
        raise RuntimeError("Kai automatic gun-skill runtime contract missing: " + marker)

# Guilty Crown must stay untouched: exact 24 x 10 HP and no accuracy/evasion RNG.
ultimate_start = combat.find('    if (c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0)')
ultimate_end = combat.find('    val isGuiltyCrownTurn =', ultimate_start)
if ultimate_start < 0 or ultimate_end < 0:
    raise RuntimeError("Guilty Crown priority section missing after Kai gun skills")
ultimate_section = combat[ultimate_start:ultimate_end]
for marker in ('KAI_GUILTY_CROWN_SHOTS * KAI_GUILTY_CROWN_DAMAGE_PER_SHOT', 'Accuracy $KAI_GUILTY_CROWN_ACCURACY_PERCENT%', 'bỏ qua toàn bộ hiệu ứng né'):
    if marker not in ultimate_section:
        raise RuntimeError("Guilty Crown contract changed by Kai gun skills: " + marker)
if 'roll(' in ultimate_section or 'ENTITY_EVASION_PERCENT' in ultimate_section:
    raise RuntimeError("Kai gun skills introduced RNG/evasion into Guilty Crown")

COMBAT.write_text(combat, encoding="utf-8")

# Focused regression coverage against the fully generated runtime. Tests search deterministic turn
# counters rather than assuming one hard-coded proc outcome from the stable-seed mixer.
test = TEST.read_text(encoding="utf-8")
new_tests = r'''
  @Test fun kaiAutomaticGunSkillsExposeAllFourIndependentProcContracts() {
    val seen = mutableSetOf<String>()
    for (counter in 0..240) {
      if (seen.size == 4) break
      var state = CombatRuntime.start(GameState.initial(), "diep_minh")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ mục tiêu trong tầm quan sát")
      if (result.reply.contains("The Last Requiem tự động kích hoạt")) seen += "requiem"
      if (result.reply.contains("Silent Lullaby tự động kích hoạt")) seen += "lullaby"
      if (result.reply.contains("Salvation tự động kích hoạt")) seen += "salvation"
      if (result.reply.contains("Quick Step tự động kích hoạt")) seen += "quick_step"
    }
    assertEquals(setOf("requiem", "lullaby", "salvation", "quick_step"), seen)
  }

  @Test fun lastRequiemBleedingPersistsAndTicksFivePercentMaxHp() {
    var verified = false
    for (counter in 0..240) {
      if (verified) break
      var state = CombatRuntime.start(GameState.initial(), "diep_minh")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.entityHp" to "2000",
        "combat.kaiBleedTurns" to "3"
      ))
      val result = CombatRuntime.resolve(state, "SEARCH", "theo dõi mục tiêu")
      if (result.reply.contains("The Last Requiem tự động kích hoạt")) continue
      val after = CombatRuntime.active(result.state) ?: continue
      assertTrue(result.reply.contains("Bleeding từ The Last Requiem gây -150 HP"))
      assertEquals("2", result.state.metadata["combat.kaiBleedTurns"])
      assertTrue(after.entityHp <= 1880) // 2000 - 150 Bleeding + at most the boss's +30 regen, before any other passive damage.
      verified = true
    }
    assertTrue("Expected a deterministic turn without Last Requiem refresh", verified)
  }

  @Test fun silentLullabyStunSuppressesCurrentEnemyResponse() {
    var verified = false
    for (counter in 0..240) {
      if (verified) break
      var state = CombatRuntime.start(GameState.initial(), "diep_minh")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "theo dõi nhịp phản công")
      if (!result.reply.contains("Silent Lullaby tự động kích hoạt")) continue
      assertTrue(result.reply.contains("bị Stun và mất lượt phản ứng hiện tại"))
      assertFalse(result.reply.contains("Diệp Minh phản công:"))
      assertFalse(result.reply.contains("Devils And Gold kích hoạt"))
      verified = true
    }
    assertTrue("Expected a deterministic Silent Lullaby proc", verified)
  }

  @Test fun quickStepGrantsFiftyEvasionForThreeTurnsAndCountsDown() {
    var verified = false
    for (counter in 0..240) {
      if (verified) break
      var state = CombatRuntime.start(GameState.initial(), "diep_minh")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "đổi góc quan sát")
      if (!result.reply.contains("Quick Step tự động kích hoạt")) continue
      assertTrue(result.reply.contains("+50% Evasion trong 3 turn"))
      assertEquals("2", result.state.metadata["combat.kaiQuickStepTurns"])
      verified = true
    }
    assertTrue("Expected a deterministic Quick Step proc", verified)
  }

  @Test fun guiltyCrownTurnKeepsPriorityOverAutomaticGunSkillRolls() {
    var state = CombatRuntime.start(GameState.initial(), "diep_minh")
    state = state.copy(metadata = state.metadata + ("combat.eventCounter" to "2"))
    val result = CombatRuntime.resolve(state, "SEARCH", "giữ mục tiêu trong tầm quan sát")
    assertTrue(result.reply.contains("Guilty Crown Override"))
    assertFalse(result.reply.contains("The Last Requiem tự động kích hoạt"))
    assertFalse(result.reply.contains("Silent Lullaby tự động kích hoạt"))
    assertFalse(result.reply.contains("Salvation tự động kích hoạt"))
    assertFalse(result.reply.contains("Quick Step tự động kích hoạt"))
  }
'''
if 'kaiAutomaticGunSkillsExposeAllFourIndependentProcContracts' not in test:
    close = test.rfind("}\n")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest class closing brace missing")
    test = test[:close] + new_tests + test[close:]

for marker in (
    'kaiAutomaticGunSkillsExposeAllFourIndependentProcContracts',
    'lastRequiemBleedingPersistsAndTicksFivePercentMaxHp',
    'silentLullabyStunSuppressesCurrentEnemyResponse',
    'quickStepGrantsFiftyEvasionForThreeTurnsAndCountsDown',
    'guiltyCrownTurnKeepsPriorityOverAutomaticGunSkillRolls',
    'assertEquals("2", result.state.metadata["combat.kaiQuickStepTurns"])',
):
    if marker not in test:
        raise RuntimeError("Kai automatic gun-skill regression contract missing: " + marker)

TEST.write_text(test, encoding="utf-8")
print("Kai automatic gun skills applied: Last Requiem, Silent Lullaby, Salvation and Quick Step with persistent Bleeding/Evasion state.")
