from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"
ASSET = ROOT / "app/src/main/assets/entity/John.png"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# ---------------------------------------------------------------------------
# CombatRuntime: John Doe is a unique roaming Entity with exact, user-locked
# combat rules. This patch runs last so generic Entity durability never changes
# his exact HP or regeneration.
# ---------------------------------------------------------------------------
combat = COMBAT.read_text(encoding="utf-8")

constants_anchor = '  private const val DIEP_MINH_ULTIMATE_PERCENT = 5\n'
constants = '''  private const val JOHN_DOE_KEY = "john_doe"
  private const val JOHN_DOE_MAX_HP = 1234
  private const val JOHN_DOE_ATTACK_MAX_HP_PERCENT = 6
  private const val JOHN_DOE_REGEN_PER_TURN = 30
  private const val JOHN_DOE_POISON_INTERVAL_TURNS = 3
  private const val JOHN_DOE_POISON_CHANCE_PERCENT = 50
  private const val JOHN_DOE_POISON_MAX_HP_PERCENT = 4
  private const val JOHN_DOE_STUN_INTERVAL_TURNS = 2
  private const val JOHN_DOE_STUN_CHANCE_PERCENT = 30
  private const val JOHN_DOE_STUN_DAMAGE_MAX_HP_PERCENT = 20
  private const val JOHN_DOE_POISON_KEY = "combat.johnDoePoisonActive"
  private const val JOHN_DOE_PARTY_STUN_TURNS_KEY = "combat.johnDoePartyStunTurns"
'''
if 'private const val JOHN_DOE_KEY = "john_doe"' not in combat:
    combat = replace_once(combat, constants_anchor, constants_anchor + constants, "John Doe constants")

profile_anchor = '    Profile(DIEP_MINH_KEY, "Diệp Minh", DIEP_MINH_MAX_HP, 0, 8, 9)\n'
profile_new = '    Profile(DIEP_MINH_KEY, "Diệp Minh", DIEP_MINH_MAX_HP, 0, 8, 9),\n    Profile(JOHN_DOE_KEY, "John Doe", JOHN_DOE_MAX_HP, 0, 6, 9)\n'
combat = replace_once(combat, profile_anchor, profile_new, "John Doe profile")

combat = replace_once(
    combat,
    '    val enhancedEntityMaxHp = if (profile.key == DIEP_MINH_KEY) DIEP_MINH_MAX_HP else profile.maxHp + ENTITY_HP_BONUS\n',
    '''    val enhancedEntityMaxHp = when (profile.key) {
      DIEP_MINH_KEY -> DIEP_MINH_MAX_HP
      JOHN_DOE_KEY -> JOHN_DOE_MAX_HP
      else -> profile.maxHp + ENTITY_HP_BONUS
    }
''',
    "John Doe exact encounter HP",
)
combat = replace_once(
    combat,
    '    val canonicalMaxHp = if (profile.key == DIEP_MINH_KEY) DIEP_MINH_MAX_HP else profile.maxHp + ENTITY_HP_BONUS\n',
    '''    val canonicalMaxHp = when (profile.key) {
      DIEP_MINH_KEY -> DIEP_MINH_MAX_HP
      JOHN_DOE_KEY -> JOHN_DOE_MAX_HP
      else -> profile.maxHp + ENTITY_HP_BONUS
    }
''',
    "John Doe exact migrated HP",
)

locals_anchor = '    var syvialDevilTrigger = state.metadata[SYVIAL_DEVIL_TRIGGER_KEY]?.toBooleanStrictOrNull() ?: false\n'
locals = '''    var johnDoePoisonActive = state.metadata[JOHN_DOE_POISON_KEY]?.toBooleanStrictOrNull() ?: false
    var johnDoePartyStunTurns = state.metadata[JOHN_DOE_PARTY_STUN_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 1) ?: 0
    val johnDoePartyStunned = current.entityKey == JOHN_DOE_KEY && johnDoePartyStunTurns > 0
'''
if 'val johnDoePartyStunned =' not in combat:
    combat = replace_once(combat, locals_anchor, locals_anchor + locals, "John Doe persistent combat locals")

when_anchor = '    when (intent) {\n'
when_new = '''    if (johnDoePartyStunned) {
      johnDoePartyStunTurns = 0
      resolvedState = withCombatCounter(resolvedState, JOHN_DOE_PARTY_STUN_TURNS_KEY, 0)
      log += "Party bị John Doe Stun: mất lượt hành động hiện tại."
    } else when (intent) {
'''
combat = replace_once(combat, when_anchor, when_new, "John Doe one-turn Party stun gate")

poison_anchor = '    if (c.entityHp > 0 && bleedTurns > 0) {\n'
poison_tick = '''    if (c.entityKey == JOHN_DOE_KEY && johnDoePoisonActive) {
      val poison = damageActivePartyByPercent(resolvedState, JOHN_DOE_POISON_MAX_HP_PERCENT)
      resolvedState = poison.state
      c = c.copy(playerHp = poison.kaiHp)
      log += "Nhiễm độc John Doe gây ${JOHN_DOE_POISON_MAX_HP_PERCENT}% Max HP cho toàn bộ nhân vật ACTIVE: ${poison.summary}."
    }

'''
if 'Nhiễm độc John Doe gây' not in combat:
    combat = replace_once(combat, poison_anchor, poison_tick + poison_anchor, "John Doe poison tick")

response_anchor = '    } else if (c.entityKey == DIEP_MINH_KEY && c.eventCounter % DIEP_MINH_ULTIMATE_INTERVAL_TURNS == 0) {\n'
john_response = '''    } else if (c.entityKey == JOHN_DOE_KEY) {
      val kaiBefore = resolvedState.characters[KAI_ID]?.vitalState?.currentHp ?: c.playerHp
      val basicDamage = percentDamage(c.playerMaxHp, JOHN_DOE_ATTACK_MAX_HP_PERCENT)
      resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, KAI_ID, kaiBefore - basicDamage)
      val kaiAfter = resolvedState.characters[KAI_ID]?.vitalState?.currentHp ?: max(0, kaiBefore - basicDamage)
      c = c.copy(playerHp = kaiAfter, momentum = max(-3, c.momentum - 1))
      log += "John Doe tấn công: Kai -$basicDamage HP (${JOHN_DOE_ATTACK_MAX_HP_PERCENT}% Max HP; ${c.playerHp}/${c.playerMaxHp})."

      if (!johnDoePoisonActive && c.eventCounter % JOHN_DOE_POISON_INTERVAL_TURNS == 0 &&
          roll(c.copy(eventCounter = c.eventCounter + 401), 100) < JOHN_DOE_POISON_CHANCE_PERCENT) {
        johnDoePoisonActive = true
        resolvedState = resolvedState.copy(metadata = resolvedState.metadata + (JOHN_DOE_POISON_KEY to "true"))
        log += "John Doe phát tán độc sau ${JOHN_DOE_POISON_INTERVAL_TURNS} turn: toàn Party bị nhiễm độc; mỗi turn mất ${JOHN_DOE_POISON_MAX_HP_PERCENT}% Max HP."
      }

      if (c.eventCounter % JOHN_DOE_STUN_INTERVAL_TURNS == 0 &&
          roll(c.copy(eventCounter = c.eventCounter + 419), 100) < JOHN_DOE_STUN_CHANCE_PERCENT) {
        val shock = damageActivePartyByPercent(resolvedState, JOHN_DOE_STUN_DAMAGE_MAX_HP_PERCENT)
        resolvedState = shock.state
        c = c.copy(playerHp = shock.kaiHp)
        johnDoePartyStunTurns = 1
        resolvedState = withCombatCounter(resolvedState, JOHN_DOE_PARTY_STUN_TURNS_KEY, 1)
        log += "John Doe tung đòn áp chế: toàn Party -${JOHN_DOE_STUN_DAMAGE_MAX_HP_PERCENT}% Max HP và bị Stun 1 lượt. ${shock.summary}."
      }
'''
if 'John Doe tấn công: Kai -$basicDamage HP' not in combat:
    combat = replace_once(combat, response_anchor, john_response + response_anchor, "John Doe enemy response")

combat = replace_once(
    combat,
    '    val entityRegen = if (c.entityKey == DIEP_MINH_KEY) DIEP_MINH_REGEN_PER_TURN else ENTITY_REGEN_PER_TURN\n',
    '''    val entityRegen = when (c.entityKey) {
      DIEP_MINH_KEY -> DIEP_MINH_REGEN_PER_TURN
      JOHN_DOE_KEY -> JOHN_DOE_REGEN_PER_TURN
      else -> ENTITY_REGEN_PER_TURN
    }
''',
    "John Doe 30 HP regeneration",
)

for marker in (
    'private const val JOHN_DOE_MAX_HP = 1234',
    'private const val JOHN_DOE_ATTACK_MAX_HP_PERCENT = 6',
    'private const val JOHN_DOE_POISON_CHANCE_PERCENT = 50',
    'private const val JOHN_DOE_POISON_MAX_HP_PERCENT = 4',
    'private const val JOHN_DOE_STUN_CHANCE_PERCENT = 30',
    'private const val JOHN_DOE_STUN_DAMAGE_MAX_HP_PERCENT = 20',
    'private const val JOHN_DOE_REGEN_PER_TURN = 30',
    'Profile(JOHN_DOE_KEY, "John Doe", JOHN_DOE_MAX_HP, 0, 6, 9)',
    'Party bị John Doe Stun: mất lượt hành động hiện tại.',
    'Nhiễm độc John Doe gây',
    'John Doe tấn công: Kai -$basicDamage HP',
    'John Doe tung đòn áp chế',
    'JOHN_DOE_KEY -> JOHN_DOE_REGEN_PER_TURN',
):
    if marker not in combat:
        raise RuntimeError("John Doe combat contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")


# ---------------------------------------------------------------------------
# Android encounter and overlay: independent 10% roll on Levels 0-999. Diệp
# Minh keeps boss priority; John Doe then wins over the shared roaming pool.
# The asset path is case-sensitive and locked to entity/John.png.
# ---------------------------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")

helper_anchor = '  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {\n'
level_helper = r'''  private boolean johnDoeLevelEligible(JSONObject state) {
    if (state == null) return false;
    JSONObject level = state.optJSONObject("level");
    if (level != null && level.has("number")) {
      int number = level.optInt("number", -1);
      return number >= 0 && number <= 999;
    }
    String title = state.optString("title", "");
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)level\\s+(\\d{1,4})").matcher(title);
    if (matcher.find()) {
      try {
        int number = Integer.parseInt(matcher.group(1));
        return number >= 0 && number <= 999;
      } catch (Exception ignored) { }
    }
    return true;
  }

'''
if 'private boolean johnDoeLevelEligible(JSONObject state)' not in main:
    main = replace_once(main, helper_anchor, level_helper + helper_anchor, "John Doe Level 0-999 helper")

roll_anchor = '    rolls.put("diepMinhEncounter", diepMinhRoll);\n'
roll_block = '''    JSONObject johnDoeRoll = thresholdRoll("johnDoeEncounter", 10000, 1000,
      entityEncounterAction && entityAllowed && johnDoeLevelEligible(state),
      " JOHN DOE unique roaming 10% Levels 0-999");
    rolls.put("johnDoeEncounter", johnDoeRoll);
'''
if 'rolls.put("johnDoeEncounter", johnDoeRoll);' not in main:
    main = replace_once(main, roll_anchor, roll_anchor + roll_block, "John Doe independent 10% encounter roll")

normalizer_anchor = '      case "jeff_the_killer": case "jane_the_killer": case "slenderman": case "diep_minh":\n        return key;\n'
normalizer_new = '      case "jeff_the_killer": case "jane_the_killer": case "slenderman": case "diep_minh": case "john_doe":\n        return key;\n'
main = replace_once(main, normalizer_anchor, normalizer_new, "John Doe canonical key")

name_anchor = '      case "diep_minh": name = "Diệp Minh"; break;\n'
name_new = '      case "diep_minh": name = "Diệp Minh"; break;\n      case "john_doe": name = "John Doe"; break;\n'
main = replace_once(main, name_anchor, name_new, "John Doe overlay name")

url_anchor = '      .put("url", "file:///android_asset/entity/" + entityKey + ".png");\n'
url_new = '''      .put("url", "file:///android_asset/entity/" + (entityKey.equals("john_doe") ? "John.png" : entityKey + ".png"));
'''
main = replace_once(main, url_anchor, url_new, "John Doe exact case-sensitive asset path")

js_anchor = "'slenderman','diep_minh'];"
js_new = "'slenderman','diep_minh','john_doe'];"
main = replace_once(main, js_anchor, js_new, "John Doe overlay JS canonical key")

helper_start = main.find('  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {')
helper_end = main.find('\n  private JSONObject resolveEntityOverlay(String rawEntityKey) throws Exception {', helper_start)
if helper_start < 0 or helper_end < 0:
    raise RuntimeError("John Doe final encounter helper boundary missing")
force_helper = r'''  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {
    if (candidateState == null || rolls == null) return;
    String entityKey = rolls.optString("roamingEntityKey", "").trim();
    JSONObject boss = rolls.optJSONObject("diepMinhEncounter");
    if (boss != null && boss.optBoolean("success", false)) {
      entityKey = "diep_minh";
    } else {
      JSONObject john = rolls.optJSONObject("johnDoeEncounter");
      if (john != null && john.optBoolean("success", false)) {
        entityKey = "john_doe";
      } else {
        JSONObject normal = rolls.optJSONObject("entityEncounter");
        if (normal == null || !normal.optBoolean("success", false)) return;
        if (entityKey.isEmpty()) return;
      }
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
main = main[:helper_start] + force_helper + main[helper_end:]

for marker in (
    'thresholdRoll("johnDoeEncounter", 10000, 1000',
    'johnDoeLevelEligible(state)',
    'case "john_doe":',
    'case "john_doe": name = "John Doe"; break;',
    'entityKey.equals("john_doe") ? "John.png"',
    "'diep_minh','john_doe']",
    'JSONObject john = rolls.optJSONObject("johnDoeEncounter")',
    'entityKey = "john_doe";',
):
    if marker not in main:
        raise RuntimeError("John Doe Android runtime contract missing: " + marker)

MAIN.write_text(main, encoding="utf-8")


# ---------------------------------------------------------------------------
# Regression tests for exact HP, regeneration, base percentage attack, poison,
# and one-turn Party stun persistence.
# ---------------------------------------------------------------------------
test = TEST.read_text(encoding="utf-8")
new_tests = r'''
  @Test fun johnDoeStartsWithExactUserLockedHp() {
    val state = CombatRuntime.start(GameState.initial(), "john_doe")
    val combat = CombatRuntime.active(state)!!
    assertEquals(1234, combat.entityMaxHp)
    assertEquals(1234, combat.entityHp)
  }

  @Test fun johnDoeRegeneratesThirtyHpPerSurvivingCombatTurn() {
    var state = CombatRuntime.start(GameState.initial(), "john_doe")
    state = state.copy(metadata = state.metadata + ("combat.entityHp" to "1000"))
    val result = CombatRuntime.resolve(state, "SEARCH", "quan sát John Doe")
    val after = CombatRuntime.active(result.state)!!
    assertEquals(1030, after.entityHp)
  }

  @Test fun johnDoeBasicAttackUsesSixPercentMaxHp() {
    val state = CombatRuntime.start(GameState.initial(), "john_doe")
    val result = CombatRuntime.resolve(state, "SEARCH", "quan sát John Doe")
    assertTrue(result.reply, result.reply.contains("6% Max HP"))
  }

  @Test fun johnDoePoisonAndStunProcContractsAreReachable() {
    var poisonSeen = false
    var stunSeen = false
    for (counter in 0..600) {
      if (poisonSeen && stunSeen) break
      var state = CombatRuntime.start(GameState.initial(), "john_doe")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "quan sát John Doe")
      if (result.reply.contains("phát tán độc")) poisonSeen = true
      if (result.reply.contains("toàn Party -20% Max HP và bị Stun 1 lượt")) stunSeen = true
    }
    assertTrue("Expected reachable John Doe poison proc", poisonSeen)
    assertTrue("Expected reachable John Doe stun proc", stunSeen)
  }

  @Test fun johnDoeStoredStunConsumesExactlyOnePartyAction() {
    var state = CombatRuntime.start(GameState.initial(), "john_doe")
    state = state.copy(metadata = state.metadata + ("combat.johnDoePartyStunTurns" to "1"))
    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")
    assertTrue(result.reply, result.reply.contains("Party bị John Doe Stun: mất lượt hành động hiện tại."))
    assertFalse(result.state.metadata.containsKey("combat.johnDoePartyStunTurns"))
  }
'''
if 'johnDoeStartsWithExactUserLockedHp' not in test:
    close = test.rfind('}\n')
    if close < 0:
        raise RuntimeError("CombatRuntimeTest class closing brace missing")
    test = test[:close] + new_tests + test[close:]
TEST.write_text(test, encoding="utf-8")

# Do not generate, transform, inline, or Base64-encode the image. The source asset
# must be the exact user-provided binary at this exact case-sensitive path.
if ASSET.exists() and ASSET.stat().st_size <= 0:
    raise RuntimeError("John.png exists but is empty")

print("John Doe runtime patch installed: exact HP 1234, 6% basic attack, poison, 20% stun pulse, +30 HP/turn, independent 10% Levels 0-999, asset path entity/John.png.")
