from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# ---------------------------------------------------------------------------
# CombatRuntime: Monster X is a unique roaming Entity with exact HP, percentage
# damage, persistent party Bleeding, delayed Stun and 50 HP regeneration.
# ---------------------------------------------------------------------------
combat = COMBAT.read_text(encoding="utf-8")

constants_anchor = '  private const val DIEP_MINH_ULTIMATE_PERCENT = 5\n'
constants = '''  private const val DIEP_MINH_ULTIMATE_PERCENT = 5
  private const val MONSTER_X_KEY = "monster_x"
  private const val MONSTER_X_MAX_HP = 3456
  private const val MONSTER_X_ATTACK_PERCENT = 6
  private const val MONSTER_X_REGEN_PER_TURN = 50
  private const val MONSTER_X_BLEED_INTERVAL_TURNS = 3
  private const val MONSTER_X_BLEED_PROC_PERCENT = 50
  private const val MONSTER_X_BLEED_MAX_HP_PERCENT = 3
  private const val MONSTER_X_BLEED_DURATION_TURNS = 5
  private const val MONSTER_X_STUN_INTERVAL_TURNS = 2
  private const val MONSTER_X_STUN_GATE_PERCENT = 30
  private const val MONSTER_X_STUN_PROC_PERCENT = 20
  private const val MONSTER_X_BLEED_TURNS_KEY = "combat.monsterXBleedTurns"
  private const val MONSTER_X_STUN_TURNS_KEY = "combat.monsterXStunTurns"
'''
combat = replace_once(combat, constants_anchor, constants, "Monster X constants")

profile_anchor = '    Profile(DIEP_MINH_KEY, "Diệp Minh", DIEP_MINH_MAX_HP, 0, 8, 9)\n'
profile_new = '    Profile(DIEP_MINH_KEY, "Diệp Minh", DIEP_MINH_MAX_HP, 0, 8, 9),\n    Profile(MONSTER_X_KEY, "Monster X", MONSTER_X_MAX_HP, 0, 7, 9)\n'
combat = replace_once(combat, profile_anchor, profile_new, "Monster X combat profile")

combat = replace_once(
    combat,
    '    val enhancedEntityMaxHp = if (profile.key == DIEP_MINH_KEY) DIEP_MINH_MAX_HP else profile.maxHp + ENTITY_HP_BONUS\n',
    '    val enhancedEntityMaxHp = when (profile.key) { DIEP_MINH_KEY -> DIEP_MINH_MAX_HP; MONSTER_X_KEY -> MONSTER_X_MAX_HP; else -> profile.maxHp + ENTITY_HP_BONUS }\n',
    "Monster X exact encounter HP",
)
combat = replace_once(
    combat,
    '    val canonicalMaxHp = if (profile.key == DIEP_MINH_KEY) DIEP_MINH_MAX_HP else profile.maxHp + ENTITY_HP_BONUS\n',
    '    val canonicalMaxHp = when (profile.key) { DIEP_MINH_KEY -> DIEP_MINH_MAX_HP; MONSTER_X_KEY -> MONSTER_X_MAX_HP; else -> profile.maxHp + ENTITY_HP_BONUS }\n',
    "Monster X exact migrated HP",
)

intent_old = '    val intent = classify(actionKind, action)\n    var c = current.copy(eventCounter = current.eventCounter + 1)\n    val log = mutableListOf<String>()\n'
intent_new = '''    val requestedIntent = classify(actionKind, action)
    val monsterXStunTurns = state.metadata[MONSTER_X_STUN_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 1) ?: 0
    val monsterXPartyStunned = current.entityKey == MONSTER_X_KEY && monsterXStunTurns > 0
    val intent = if (monsterXPartyStunned) Intent.OTHER else requestedIntent
    var c = current.copy(eventCounter = current.eventCounter + 1)
    val log = mutableListOf<String>()
'''
combat = replace_once(combat, intent_old, intent_new, "Monster X delayed party stun intent")

resolved_anchor = '    var resolvedState = state\n'
resolved_new = '''    var resolvedState = state
    var monsterXBleedTurns = state.metadata[MONSTER_X_BLEED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, MONSTER_X_BLEED_DURATION_TURNS) ?: 0
    if (monsterXPartyStunned) {
      resolvedState = withCombatCounter(resolvedState, MONSTER_X_STUN_TURNS_KEY, 0)
      log += "Monster X Stun: toàn bộ Party mất lượt hành động hiện tại."
    }
'''
combat = replace_once(combat, resolved_anchor, resolved_new, "Monster X persistent combat state")

# Persistent Bleeding ticks on the next five combat turns after a successful proc.
response_anchor = '    // Enemy response. Diệp Minh uses percentage damage; all other Entity behavior remains unchanged.\n'
bleed_tick = '''    if (c.entityKey == MONSTER_X_KEY && monsterXBleedTurns > 0) {
      val bleed = damageActivePartyByPercent(resolvedState, MONSTER_X_BLEED_MAX_HP_PERCENT)
      resolvedState = bleed.state
      c = c.copy(playerHp = bleed.kaiHp)
      monsterXBleedTurns = max(0, monsterXBleedTurns - 1)
      resolvedState = withCombatCounter(resolvedState, MONSTER_X_BLEED_TURNS_KEY, monsterXBleedTurns)
      log += "Monster X Bleeding: toàn bộ nhân vật ACTIVE -${MONSTER_X_BLEED_MAX_HP_PERCENT}% Max HP; còn $monsterXBleedTurns lượt. ${bleed.summary}."
    }

    // Enemy response. Diệp Minh uses percentage damage; all other Entity behavior remains unchanged.
'''
combat = replace_once(combat, response_anchor, bleed_tick, "Monster X party Bleeding tick")

# Monster X attacks for exactly 6% of the current target's Max HP whenever its response lands.
damage_old = '''        val damage = if (c.entityKey == DIEP_MINH_KEY) {
          percentDamage(c.playerMaxHp, DIEP_MINH_ATTACK_PERCENT)
        } else {
'''
damage_new = '''        val damage = if (c.entityKey == DIEP_MINH_KEY) {
          percentDamage(c.playerMaxHp, DIEP_MINH_ATTACK_PERCENT)
        } else if (c.entityKey == MONSTER_X_KEY) {
          percentDamage(c.playerMaxHp, MONSTER_X_ATTACK_PERCENT)
        } else {
'''
combat = replace_once(combat, damage_old, damage_new, "Monster X 6 percent Max HP attack")

log_old = '''        log += if (c.entityKey == DIEP_MINH_KEY) {
          "Diệp Minh phản công: Kai -$damage HP (${DIEP_MINH_ATTACK_PERCENT}% Max HP; ${c.playerHp}/${c.playerMaxHp})."
        } else {
'''
log_new = '''        log += if (c.entityKey == DIEP_MINH_KEY) {
          "Diệp Minh phản công: Kai -$damage HP (${DIEP_MINH_ATTACK_PERCENT}% Max HP; ${c.playerHp}/${c.playerMaxHp})."
        } else if (c.entityKey == MONSTER_X_KEY) {
          "Monster X tấn công: Kai -$damage HP (${MONSTER_X_ATTACK_PERCENT}% Max HP; ${c.playerHp}/${c.playerMaxHp})."
        } else {
'''
combat = replace_once(combat, log_old, log_new, "Monster X percentage attack log")

regen_anchor = '    val entityHpBeforeRegen = c.entityHp\n'
status_procs = '''    if (c.entityKey == MONSTER_X_KEY && c.eventCounter % MONSTER_X_BLEED_INTERVAL_TURNS == 0 &&
        roll(c.copy(eventCounter = c.eventCounter + 401), 100) < MONSTER_X_BLEED_PROC_PERCENT) {
      monsterXBleedTurns = MONSTER_X_BLEED_DURATION_TURNS
      resolvedState = withCombatCounter(resolvedState, MONSTER_X_BLEED_TURNS_KEY, monsterXBleedTurns)
      log += "Monster X gây Bleeding cho toàn bộ Party: ${MONSTER_X_BLEED_DURATION_TURNS} lượt, ${MONSTER_X_BLEED_MAX_HP_PERCENT}% Max HP/lượt."
    }
    if (c.entityKey == MONSTER_X_KEY && c.eventCounter % MONSTER_X_STUN_INTERVAL_TURNS == 0 &&
        roll(c.copy(eventCounter = c.eventCounter + 419), 100) < MONSTER_X_STUN_GATE_PERCENT &&
        roll(c.copy(eventCounter = c.eventCounter + 431), 100) < MONSTER_X_STUN_PROC_PERCENT) {
      resolvedState = withCombatCounter(resolvedState, MONSTER_X_STUN_TURNS_KEY, 1)
      log += "Monster X chuẩn bị Stun: cổng ${MONSTER_X_STUN_GATE_PERCENT}% và proc ${MONSTER_X_STUN_PROC_PERCENT}% thành công; Party sẽ mất 1 lượt kế tiếp."
    }

    val entityHpBeforeRegen = c.entityHp
'''
combat = replace_once(combat, regen_anchor, status_procs, "Monster X Bleeding/Stun proc scheduling")

regen_old = '    val entityRegen = if (c.entityKey == DIEP_MINH_KEY) DIEP_MINH_REGEN_PER_TURN else ENTITY_REGEN_PER_TURN\n'
regen_new = '    val entityRegen = when (c.entityKey) { DIEP_MINH_KEY -> DIEP_MINH_REGEN_PER_TURN; MONSTER_X_KEY -> MONSTER_X_REGEN_PER_TURN; else -> ENTITY_REGEN_PER_TURN }\n'
combat = replace_once(combat, regen_old, regen_new, "Monster X 50 HP regeneration")

for marker in (
    'private const val MONSTER_X_MAX_HP = 3456',
    'private const val MONSTER_X_ATTACK_PERCENT = 6',
    'private const val MONSTER_X_REGEN_PER_TURN = 50',
    'private const val MONSTER_X_BLEED_PROC_PERCENT = 50',
    'private const val MONSTER_X_BLEED_MAX_HP_PERCENT = 3',
    'private const val MONSTER_X_BLEED_DURATION_TURNS = 5',
    'private const val MONSTER_X_STUN_GATE_PERCENT = 30',
    'private const val MONSTER_X_STUN_PROC_PERCENT = 20',
    'Profile(MONSTER_X_KEY, "Monster X", MONSTER_X_MAX_HP, 0, 7, 9)',
    'Monster X Bleeding:',
    'Monster X Stun:',
    'MONSTER_X_REGEN_PER_TURN',
):
    if marker not in combat:
        raise RuntimeError("Monster X combat contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")


# ---------------------------------------------------------------------------
# Android encounter/overlay: independent 10% roll on Levels 0..999. Preserve
# Diệp Minh's existing higher-priority unique-boss gate, then Monster X, then the
# shared roaming pool. Monster X uses the pre-existing local asset x.png.
# ---------------------------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")

level_helper_anchor = '  private int currentLevel(JSONObject state) {\n'
raw_level_helper = '''  private int rawLevelNumber(JSONObject state) {
    JSONObject level = state.optJSONObject("level");
    if (level != null) return Math.max(0, level.optInt("number", 0));
    String title = state.optString("title", "");
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("Level\\s+(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(title);
    if (matcher.find()) return Math.max(0, Integer.parseInt(matcher.group(1)));
    return 0;
  }

'''
if 'private int rawLevelNumber(JSONObject state)' not in main:
    main = replace_once(main, level_helper_anchor, raw_level_helper + level_helper_anchor, "Monster X raw Level helper")

normal_roll = '    JSONObject normalEntityRoll = thresholdRoll("entityEncounter", 10000, entityThresholds[level], entityEncounterAction && entityAllowed, entitySuffix);\n'
monster_roll = '''    int monsterXLevel = rawLevelNumber(state);
    JSONObject monsterXRoll = thresholdRoll("monsterXEncounter", 10000, 1000,
      entityEncounterAction && monsterXLevel >= 0 && monsterXLevel <= 999, " Monster X unique roaming 10% Level 0-999");
    rolls.put("monsterXEncounter", monsterXRoll);
'''
if 'rolls.put("monsterXEncounter", monsterXRoll);' not in main:
    main = replace_once(main, normal_roll, monster_roll + normal_roll, "Monster X independent 10 percent roll")

normalized_old = '      case "jeff_the_killer": case "jane_the_killer": case "slenderman": case "diep_minh":\n        return key;\n'
normalized_new = '      case "jeff_the_killer": case "jane_the_killer": case "slenderman": case "diep_minh": case "monster_x":\n        return key;\n'
main = replace_once(main, normalized_old, normalized_new, "Monster X canonical key")

name_anchor = '      case "diep_minh": name = "Diệp Minh"; break;\n'
name_new = '      case "diep_minh": name = "Diệp Minh"; break;\n      case "monster_x": name = "Monster X"; break;\n'
main = replace_once(main, name_anchor, name_new, "Monster X overlay display name")

# Local asset filename differs from canonical key.
asset_anchor = '    String assetPath = "file:///android_asset/entity/" + key + ".png";\n'
asset_new = '    String assetFile = "monster_x".equals(key) ? "x.png" : key + ".png";\n    String assetPath = "file:///android_asset/entity/" + assetFile;\n'
main = replace_once(main, asset_anchor, asset_new, "Monster X x.png overlay asset")

js_old = "'jeff_the_killer','jane_the_killer','slenderman','diep_minh'];"
js_new = "'jeff_the_killer','jane_the_killer','slenderman','diep_minh','monster_x'];"
main = replace_once(main, js_old, js_new, "Monster X overlay JS key")

helper_start = main.find('  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {')
helper_end = main.find('\n  private JSONObject resolveEntityOverlay(String rawEntityKey) throws Exception {', helper_start)
if helper_start < 0 or helper_end < 0:
    raise RuntimeError("Monster X final encounter helper boundary missing")
helper = r'''  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {
    if (candidateState == null || rolls == null) return;
    String entityKey = rolls.optString("roamingEntityKey", "").trim();
    JSONObject boss = rolls.optJSONObject("diepMinhEncounter");
    JSONObject monsterX = rolls.optJSONObject("monsterXEncounter");
    if (boss != null && boss.optBoolean("success", false)) {
      entityKey = "diep_minh";
    } else if (monsterX != null && monsterX.optBoolean("success", false)) {
      entityKey = "monster_x";
    } else {
      JSONObject normal = rolls.optJSONObject("entityEncounter");
      if (normal == null || !normal.optBoolean("success", false)) return;
      if (entityKey.isEmpty()) return;
    }
    JSONObject flags = candidateState.optJSONObject("flags");
    if (flags == null) {
      flags = new JSONObject();
      candidateState.put("flags", flags);
    }
    String canonicalKey = normalizedEntityKey(entityKey);
    flags.put("entityEncounterKey", canonicalKey);
    requireGameCore().startCombatState(candidateState.toString(), canonicalKey);
  }
'''
main = main[:helper_start] + helper + main[helper_end:]

for marker in (
    'private int rawLevelNumber(JSONObject state)',
    'thresholdRoll("monsterXEncounter", 10000, 1000',
    'monsterXLevel >= 0 && monsterXLevel <= 999',
    'rolls.put("monsterXEncounter", monsterXRoll)',
    'case "monster_x":',
    'case "monster_x": name = "Monster X"; break;',
    '"monster_x".equals(key) ? "x.png"',
    "'diep_minh','monster_x']",
    'JSONObject monsterX = rolls.optJSONObject("monsterXEncounter")',
    'entityKey = "monster_x";',
):
    if marker not in main:
        raise RuntimeError("Monster X encounter/overlay contract missing: " + marker)

MAIN.write_text(main, encoding="utf-8")


# ---------------------------------------------------------------------------
# Regression coverage is appended after all prior compatibility rewrites.
# ---------------------------------------------------------------------------
test = TEST.read_text(encoding="utf-8")
if 'monsterXHasExactHpAndFiftyRegen' not in test:
    tests = r'''
  @Test fun monsterXHasExactHpAndFiftyRegen() {
    var state = CombatRuntime.start(GameState.initial(), "monster_x")
    var combat = CombatRuntime.active(state)!!
    assertEquals(3456, combat.entityMaxHp)
    assertEquals(3456, combat.entityHp)
    state = state.copy(metadata = state.metadata + ("combat.entityHp" to "3000"))
    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
    combat = CombatRuntime.active(result.state)!!
    assertEquals(3050, combat.entityHp)
  }

  @Test fun monsterXAttackUsesSixPercentMaxHp() {
    var verified = false
    for (counter in 0..240) {
      if (verified) break
      var state = CombatRuntime.start(GameState.initial(), "monster_x")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")
      if (!result.reply.contains("Monster X tấn công:")) continue
      assertTrue(result.reply, result.reply.contains("6% Max HP"))
      verified = true
    }
    assertTrue("Expected Monster X to land a 6% Max HP attack", verified)
  }

  @Test fun monsterXBleedingAndStunContractsArePersistent() {
    var sawBleed = false
    var sawStun = false
    for (counter in 0..600) {
      if (sawBleed && sawStun) break
      var state = CombatRuntime.start(GameState.initial(), "monster_x")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
      if (result.reply.contains("gây Bleeding cho toàn bộ Party")) {
        assertEquals("5", result.state.metadata["combat.monsterXBleedTurns"])
        sawBleed = true
      }
      if (result.reply.contains("Monster X chuẩn bị Stun:")) {
        assertEquals("1", result.state.metadata["combat.monsterXStunTurns"])
        val next = CombatRuntime.resolve(result.state, "EXECUTE", "Cả Party cùng tấn công")
        assertTrue(next.reply, next.reply.contains("toàn bộ Party mất lượt hành động hiện tại"))
        sawStun = true
      }
    }
    assertTrue("Expected deterministic search to reach Monster X Bleeding proc", sawBleed)
    assertTrue("Expected deterministic search to reach Monster X Stun proc", sawStun)
  }
'''
    close = test.rfind('\n}')
    if close < 0:
        raise RuntimeError("CombatRuntimeTest closing brace missing")
    test = test[:close] + tests + test[close:]
    TEST.write_text(test, encoding="utf-8")

print("Monster X installed: exact 3456 HP, 6% Max-HP attack, 50 HP regen, party Bleeding, delayed Stun, independent 10% Level 0-999 encounter, x.png overlay binding.")
