from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"
KNOWLEDGE = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# ---------------------------------------------------------------------------
# CombatRuntime: John Doe is a unique roaming Entity with exact user-locked
# percentage damage, persistent poison, one-turn Party stun, and 30 HP regen.
# This patch runs near the very end of the chain so earlier generic +30 HP/+1
# regen rules cannot silently alter the exact John Doe values.
# ---------------------------------------------------------------------------
combat = COMBAT.read_text(encoding="utf-8")

constants_anchor = '  private const val DIEP_MINH_ULTIMATE_PERCENT = 5\n'
constants_block = '''  private const val DIEP_MINH_ULTIMATE_PERCENT = 5
  private const val JOHN_DOE_KEY = "john"
  private const val JOHN_DOE_MAX_HP = 1234
  private const val JOHN_DOE_ATTACK_PERCENT = 6
  private const val JOHN_DOE_REGEN_PER_TURN = 30
  private const val JOHN_DOE_POISON_AFTER_TURNS = 3
  private const val JOHN_DOE_POISON_CHANCE_PERCENT = 50
  private const val JOHN_DOE_POISON_DAMAGE_PERCENT = 4
  private const val JOHN_DOE_SPECIAL_INTERVAL_TURNS = 2
  private const val JOHN_DOE_SPECIAL_CHANCE_PERCENT = 30
  private const val JOHN_DOE_SPECIAL_DAMAGE_PERCENT = 20
  private const val JOHN_DOE_POISON_ROLLED_KEY = "combat.johnDoePoisonRolled"
  private const val JOHN_DOE_POISONED_KEY = "combat.johnDoePoisoned"
  private const val JOHN_DOE_PARTY_STUN_TURNS_KEY = "combat.johnDoePartyStunTurns"
'''
combat = replace_once(combat, constants_anchor, constants_block, "John Doe constants")

profile_anchor = '    Profile(DIEP_MINH_KEY, "Diệp Minh", DIEP_MINH_MAX_HP, 0, 8, 9)\n'
profile_new = '    Profile(DIEP_MINH_KEY, "Diệp Minh", DIEP_MINH_MAX_HP, 0, 8, 9),\n    Profile(JOHN_DOE_KEY, "John Doe", JOHN_DOE_MAX_HP, 0, 6, 9)\n'
combat = replace_once(combat, profile_anchor, profile_new, "John Doe combat profile")

combat = replace_once(
    combat,
    '    val enhancedEntityMaxHp = if (profile.key == DIEP_MINH_KEY) DIEP_MINH_MAX_HP else profile.maxHp + ENTITY_HP_BONUS\n',
    '''    val enhancedEntityMaxHp = when (profile.key) {
      DIEP_MINH_KEY -> DIEP_MINH_MAX_HP
      JOHN_DOE_KEY -> JOHN_DOE_MAX_HP
      else -> profile.maxHp + ENTITY_HP_BONUS
    }
''',
    "John Doe exact new-encounter HP",
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

intent_old = '''    val intent = classify(actionKind, action)
    var c = current.copy(eventCounter = current.eventCounter + 1)
    val log = mutableListOf<String>()
    var resolvedState = state
'''
intent_new = '''    var johnDoePartyStunTurns = state.metadata[JOHN_DOE_PARTY_STUN_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 1) ?: 0
    val johnDoePartyStunnedThisTurn = current.entityKey == JOHN_DOE_KEY && johnDoePartyStunTurns > 0
    val rawIntent = classify(actionKind, action)
    val intent = if (johnDoePartyStunnedThisTurn) Intent.OTHER else rawIntent
    var c = current.copy(eventCounter = current.eventCounter + 1)
    val log = mutableListOf<String>()
    var resolvedState = state
    if (johnDoePartyStunnedThisTurn) {
      johnDoePartyStunTurns = 0
      resolvedState = withCombatCounter(resolvedState, JOHN_DOE_PARTY_STUN_TURNS_KEY, 0)
      log += "John Doe: toàn bộ Party đang bị Stun, mất lượt hành động hiện tại."
    }
'''
combat = replace_once(combat, intent_old, intent_new, "John Doe Party stun action suppression")

helper_anchor = '''  private fun weaponSkillDamage(weaponDamage: Int, percent: Int, armor: Int): Int =
'''
helper = '''  private fun withCombatFlag(state: GameState, key: String, enabled: Boolean): GameState {
    val metadata = state.metadata.toMutableMap()
    if (enabled) metadata[key] = "true" else metadata.remove(key)
    return state.copy(metadata = metadata)
  }

'''
if 'private fun withCombatFlag(' not in combat:
    combat = replace_once(combat, helper_anchor, helper + helper_anchor, "John Doe persistent combat flag helper")

# Poison is checked once after three combat turns. If the 50% roll succeeds it
# remains active for the encounter and damages every ACTIVE Party member by 4%
# Max HP on each subsequent resolution, including the trigger resolution.
resolve_start = combat.index('  fun resolve(state: GameState, actionKind: String, action: String): Resolution {\n')
resolve_end = combat.index('\n  fun toJson(state: GameState): JSONObject?', resolve_start)
first_death = combat.find('    if (c.entityHp <= 0) {\n', resolve_start, resolve_end)
if first_death < 0:
    raise RuntimeError("John Doe poison insertion: first entity death gate missing")
if 'John Doe Poison kích hoạt' not in combat[resolve_start:resolve_end]:
    poison_block = '''    if (c.entityKey == JOHN_DOE_KEY && c.entityHp > 0) {
      var poisonRolled = resolvedState.metadata[JOHN_DOE_POISON_ROLLED_KEY]?.toBooleanStrictOrNull() ?: false
      var poisoned = resolvedState.metadata[JOHN_DOE_POISONED_KEY]?.toBooleanStrictOrNull() ?: false
      if (!poisonRolled && c.eventCounter >= JOHN_DOE_POISON_AFTER_TURNS) {
        poisonRolled = true
        poisoned = roll(c.copy(eventCounter = c.eventCounter + 503), 100) < JOHN_DOE_POISON_CHANCE_PERCENT
        resolvedState = withCombatFlag(resolvedState, JOHN_DOE_POISON_ROLLED_KEY, true)
        resolvedState = withCombatFlag(resolvedState, JOHN_DOE_POISONED_KEY, poisoned)
        log += if (poisoned) {
          "John Doe Poison kích hoạt sau ${JOHN_DOE_POISON_AFTER_TURNS} turn: roll ${JOHN_DOE_POISON_CHANCE_PERCENT}% thành công."
        } else {
          "John Doe Poison kiểm tra sau ${JOHN_DOE_POISON_AFTER_TURNS} turn: roll ${JOHN_DOE_POISON_CHANCE_PERCENT}% thất bại."
        }
      }
      if (poisoned) {
        val pulse = damageActivePartyByPercent(resolvedState, JOHN_DOE_POISON_DAMAGE_PERCENT)
        resolvedState = pulse.state
        c = c.copy(playerHp = pulse.kaiHp)
        log += "Poison của John Doe gây ${JOHN_DOE_POISON_DAMAGE_PERCENT}% Max HP cho toàn bộ Party ACTIVE: ${pulse.summary}."
      }
    }

'''
    combat = combat[:first_death] + poison_block + combat[first_death:]

# John Doe owns its response branch. Every normal response is exactly 6% Kai Max
# HP. Every second combat turn it first attempts the user-specified 30% special;
# on success that hit is 20% Max HP and schedules a one-turn Party stun.
response_old = '''    if (entityStunnedThisTurn) {
      log += "Silent Lullaby: ${c.entityName} bị Stun và mất lượt phản ứng hiện tại."
    } else if (c.entityKey == DIEP_MINH_KEY && c.eventCounter % DIEP_MINH_ULTIMATE_INTERVAL_TURNS == 0) {
'''
response_new = '''    if (entityStunnedThisTurn) {
      log += "Silent Lullaby: ${c.entityName} bị Stun và mất lượt phản ứng hiện tại."
    } else if (c.entityKey == JOHN_DOE_KEY) {
      val specialTriggered = c.eventCounter >= JOHN_DOE_SPECIAL_INTERVAL_TURNS &&
        c.eventCounter % JOHN_DOE_SPECIAL_INTERVAL_TURNS == 0 &&
        roll(c.copy(eventCounter = c.eventCounter + 557), 100) < JOHN_DOE_SPECIAL_CHANCE_PERCENT
      val percent = if (specialTriggered) JOHN_DOE_SPECIAL_DAMAGE_PERCENT else JOHN_DOE_ATTACK_PERCENT
      val damage = percentDamage(c.playerMaxHp, percent)
      resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, KAI_ID, c.playerHp - damage)
      val kaiHp = resolvedState.characters[KAI_ID]?.vitalState?.currentHp?.coerceIn(0, c.playerMaxHp) ?: max(0, c.playerHp - damage)
      c = c.copy(playerHp = kaiHp, momentum = max(-3, c.momentum - 1))
      if (specialTriggered) {
        resolvedState = withCombatCounter(resolvedState, JOHN_DOE_PARTY_STUN_TURNS_KEY, 1)
        log += "John Doe special kích hoạt: -$damage HP (${JOHN_DOE_SPECIAL_DAMAGE_PERCENT}% Max HP; ${c.playerHp}/${c.playerMaxHp}) và Stun toàn bộ Party 1 lượt."
      } else {
        log += "John Doe phản công: -$damage HP (${JOHN_DOE_ATTACK_PERCENT}% Max HP; ${c.playerHp}/${c.playerMaxHp})."
      }
    } else if (c.entityKey == DIEP_MINH_KEY && c.eventCounter % DIEP_MINH_ULTIMATE_INTERVAL_TURNS == 0) {
'''
combat = replace_once(combat, response_old, response_new, "John Doe percentage response branch")

regen_old = '    val entityRegen = if (c.entityKey == DIEP_MINH_KEY) DIEP_MINH_REGEN_PER_TURN else ENTITY_REGEN_PER_TURN\n'
regen_new = '''    val entityRegen = when (c.entityKey) {
      DIEP_MINH_KEY -> DIEP_MINH_REGEN_PER_TURN
      JOHN_DOE_KEY -> JOHN_DOE_REGEN_PER_TURN
      else -> ENTITY_REGEN_PER_TURN
    }
'''
combat = replace_once(combat, regen_old, regen_new, "John Doe 30 HP regeneration")

for marker in (
    'private const val JOHN_DOE_MAX_HP = 1234',
    'private const val JOHN_DOE_ATTACK_PERCENT = 6',
    'private const val JOHN_DOE_REGEN_PER_TURN = 30',
    'private const val JOHN_DOE_POISON_CHANCE_PERCENT = 50',
    'private const val JOHN_DOE_POISON_DAMAGE_PERCENT = 4',
    'private const val JOHN_DOE_SPECIAL_INTERVAL_TURNS = 2',
    'private const val JOHN_DOE_SPECIAL_CHANCE_PERCENT = 30',
    'private const val JOHN_DOE_SPECIAL_DAMAGE_PERCENT = 20',
    'Profile(JOHN_DOE_KEY, "John Doe", JOHN_DOE_MAX_HP, 0, 6, 9)',
    'JOHN_DOE_KEY -> JOHN_DOE_MAX_HP',
    'John Doe Poison kích hoạt',
    'damageActivePartyByPercent(resolvedState, JOHN_DOE_POISON_DAMAGE_PERCENT)',
    'John Doe special kích hoạt',
    'JOHN_DOE_KEY -> JOHN_DOE_REGEN_PER_TURN',
):
    if marker not in combat:
        raise RuntimeError("John Doe combat contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")


# ---------------------------------------------------------------------------
# MainActivity: John Doe uses its own independent 10% encounter roll and is
# eligible on raw Levels 0..999. Existing currentLevel() intentionally clamps to
# 0..6 for legacy per-Level arrays, so John gets a separate raw-level reader.
# Diệp Minh retains priority if both unique rolls succeed; John then outranks the
# shared roaming pool so one action can still start only one combat encounter.
# ---------------------------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")

level_helper_anchor = '  private JSONObject rollSpec(String label, int chance, boolean eligible) throws Exception {\n'
raw_level_helper = r'''  private int rawCurrentLevelForJohnDoe(JSONObject state) {
    JSONObject level = state.optJSONObject("level");
    if (level != null && level.has("number")) return level.optInt("number", -1);
    JSONObject flags = state.optJSONObject("flags");
    if (flags != null && flags.has("currentLevel")) return flags.optInt("currentLevel", -1);
    String title = state.optString("title", "");
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)Level\\s+(\\d{1,3})").matcher(title);
    if (matcher.find()) {
      try { return Integer.parseInt(matcher.group(1)); } catch (Exception ignored) {}
    }
    return 0;
  }

'''
if 'rawCurrentLevelForJohnDoe' not in main:
    main = replace_once(main, level_helper_anchor, raw_level_helper + level_helper_anchor, "John Doe raw Level helper")

boss_roll_anchor = '    rolls.put("diepMinhEncounter", diepMinhRoll);\n'
john_roll = '''    int johnDoeLevel = rawCurrentLevelForJohnDoe(state);
    boolean johnDoeLevelEligible = johnDoeLevel >= 0 && johnDoeLevel <= 999;
    JSONObject johnDoeRoll = thresholdRoll("johnDoeEncounter", 10000, 1000,
      entityEncounterAction && entityAllowed && johnDoeLevelEligible,
      " JOHN DOE roaming unique 10% Level 0-999");
    rolls.put("johnDoeEncounter", johnDoeRoll);
'''
if 'rolls.put("johnDoeEncounter", johnDoeRoll);' not in main:
    main = replace_once(main, boss_roll_anchor, boss_roll_anchor + john_roll, "John Doe independent 10% roll")

normalized_old = '      case "jeff_the_killer": case "jane_the_killer": case "slenderman": case "diep_minh":\n        return key;\n'
normalized_new = '      case "jeff_the_killer": case "jane_the_killer": case "slenderman": case "diep_minh": case "john":\n        return key;\n'
main = replace_once(main, normalized_old, normalized_new, "John Doe canonical overlay key")

name_anchor = '      case "diep_minh": name = "Diệp Minh"; break;\n'
name_new = name_anchor + '      case "john": name = "John Doe"; break;\n'
main = replace_once(main, name_anchor, name_new, "John Doe overlay display name")

js_old = "'slenderman','diep_minh'];"
js_new = "'slenderman','diep_minh','john'];"
main = replace_once(main, js_old, js_new, "John Doe overlay JavaScript key")

helper_start = main.find('  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {')
helper_end = main.find('\n  private JSONObject resolveEntityOverlay(String rawEntityKey) throws Exception {', helper_start)
if helper_start < 0 or helper_end < 0:
    raise RuntimeError("John Doe final encounter helper boundary missing")
helper = r'''  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {
    if (candidateState == null || rolls == null) return;
    String entityKey = rolls.optString("roamingEntityKey", "").trim();
    JSONObject boss = rolls.optJSONObject("diepMinhEncounter");
    JSONObject john = rolls.optJSONObject("johnDoeEncounter");
    if (boss != null && boss.optBoolean("success", false)) {
      entityKey = "diep_minh";
    } else if (john != null && john.optBoolean("success", false)) {
      entityKey = "john";
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

pool_lines = [line for line in main.splitlines() if 'String[] roamingPool =' in line]
if len(pool_lines) != 1:
    raise RuntimeError(f"John Doe: expected exactly one final shared roaming pool, found {len(pool_lines)}")
if 'john' in pool_lines[0]:
    raise RuntimeError("John Doe must remain outside the shared roaming pool because its 10% roll is independent")

for marker in (
    'rawCurrentLevelForJohnDoe(state)',
    'johnDoeLevel >= 0 && johnDoeLevel <= 999',
    'thresholdRoll("johnDoeEncounter", 10000, 1000',
    'rolls.put("johnDoeEncounter", johnDoeRoll)',
    'case "john":',
    'case "john": name = "John Doe"; break;',
    "'diep_minh','john']",
    'JSONObject john = rolls.optJSONObject("johnDoeEncounter")',
    'entityKey = "john";',
    'file:///android_asset/entity/',
):
    if marker not in main:
        raise RuntimeError("John Doe Android encounter/overlay contract missing: " + marker)

MAIN.write_text(main, encoding="utf-8")


# ---------------------------------------------------------------------------
# Runtime knowledge record: user instruction is the direct authority for this
# Project-original roaming Entity. Keep it separate from the shared pool record.
# ---------------------------------------------------------------------------
db = json.loads(KNOWLEDGE.read_text(encoding="utf-8"))
records = db.get("records", [])
john_record = {
    "id": "ENTITY.JOHN_DOE",
    "domain": "ENTITY",
    "kind": "unique-roaming-entity",
    "text": (
        "John Doe is a hostile unique roaming Entity with canonical runtime key john. Exact combat HP 1234; "
        "normal response damage is 6% target Max HP; after three combat turns a one-time 50% poison check may "
        "poison every ACTIVE Party member for 4% Max HP each combat turn; every second combat turn John Doe has "
        "a 30% special check which, when successful, deals 20% Max HP and stuns the whole Party for one turn; "
        "John Doe regenerates 30 HP after each surviving combat turn. It uses an independent 10% encounter roll "
        "on Levels 0 through 999 and is not part of the shared roamingEntityKey pool."
    ),
    "source": {"document": "latest explicit user instruction", "anchor": "John Doe"},
    "authority": "USER_OVERRIDE_ENTITY_CANON",
    "mutability": "IMMUTABLE",
    "priority": 24,
    "tags": ["john", "john doe", "roaming entity", "poison"],
    "references": ["ENTITY.GLOBAL_HARD_LOCK"],
    "affordances": ["direct_threat", "roaming_incursion", "poison", "stun"]
}
records = [r for r in records if r.get("id") != "ENTITY.JOHN_DOE"]
records.append(john_record)
db["records"] = records
KNOWLEDGE.write_text(json.dumps(db, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


# ---------------------------------------------------------------------------
# Regression tests are appended to the generated final CombatRuntimeTest so CI
# validates behavior after every earlier patch in the chain has run.
# ---------------------------------------------------------------------------
test = TEST.read_text(encoding="utf-8")
new_tests = r'''
  @Test fun johnDoeUsesExactHpSixPercentAttackAndThirtyRegen() {
    var state = CombatRuntime.start(GameState.initial(), "john")
    val start = CombatRuntime.active(state)!!
    assertEquals(1234, start.entityMaxHp)
    assertEquals(1234, start.entityHp)

    val kaiMaxHp = CharacterStatEngine.effective(state, KAI_ID).maxHp
    val expectedDamage = maxOf(1, (kaiMaxHp * 6 + 99) / 100)
    state = state.copy(metadata = state.metadata + ("combat.entityHp" to "1000"))
    val result = CombatRuntime.resolve(state, "SEARCH", "quan sát John Doe")
    assertTrue(result.handled)
    assertTrue(result.reply, result.reply.contains("John Doe phản công"))
    assertTrue(result.reply, result.reply.contains("6% Max HP"))
    assertEquals(kaiMaxHp - expectedDamage, result.state.characters.getValue(KAI_ID).vitalState.currentHp)
    assertEquals(1030, CombatRuntime.active(result.state)!!.entityHp)
  }

  @Test fun johnDoePoisonCanProcAfterThreeTurnsAndHitsWholeActivePartyForFourPercent() {
    var verified = false
    for (seed in 1L..240L) {
      if (verified) break
      val initial = LuciaCanon.ensure(GameState.initial())
      var state = initial.copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID)))
      state = CombatRuntime.start(state, "john")
      val luciaBefore = state.characters.getValue(LUCIA_ID).vitalState.currentHp
      val luciaMax = CharacterStatEngine.effective(state, LUCIA_ID).maxHp
      val poisonDamage = maxOf(1, (luciaMax * 4 + 99) / 100)
      state = state.copy(metadata = state.metadata + mapOf("combat.eventCounter" to "2", "combat.seed" to seed.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "quan sát John Doe")
      if (!result.reply.contains("John Doe Poison kích hoạt")) continue
      assertTrue(result.reply, result.reply.contains("50% thành công"))
      assertTrue(result.reply, result.reply.contains("4% Max HP cho toàn bộ Party ACTIVE"))
      assertEquals(luciaBefore - poisonDamage, result.state.characters.getValue(LUCIA_ID).vitalState.currentHp)
      assertEquals("true", result.state.metadata["combat.johnDoePoisoned"])
      verified = true
    }
    assertTrue("Expected at least one deterministic seed where John Doe poison procs", verified)
  }

  @Test fun johnDoeSpecialCanDealTwentyPercentAndStunNextPartyTurn() {
    var verified = false
    for (seed in 1L..240L) {
      if (verified) break
      var state = CombatRuntime.start(GameState.initial(), "john")
      state = state.copy(metadata = state.metadata + mapOf("combat.eventCounter" to "1", "combat.seed" to seed.toString()))
      val proc = CombatRuntime.resolve(state, "SEARCH", "quan sát John Doe")
      if (!proc.reply.contains("John Doe special kích hoạt")) continue
      assertTrue(proc.reply, proc.reply.contains("20% Max HP"))
      assertTrue(proc.reply, proc.reply.contains("Stun toàn bộ Party 1 lượt"))
      assertEquals("1", proc.state.metadata["combat.johnDoePartyStunTurns"])

      val stunned = CombatRuntime.resolve(proc.state, "EXECUTE", "Cả Party cùng tấn công")
      assertTrue(stunned.reply, stunned.reply.contains("toàn bộ Party đang bị Stun, mất lượt hành động hiện tại"))
      assertFalse(stunned.reply, stunned.reply.contains("PARTY ACTION TẤN CÔNG"))
      assertTrue(stunned.state.metadata["combat.johnDoePartyStunTurns"] == null)
      verified = true
    }
    assertTrue("Expected at least one deterministic seed where John Doe special procs", verified)
  }
'''
if 'johnDoeUsesExactHpSixPercentAttackAndThirtyRegen' not in test:
    close = test.rfind('}\n')
    if close < 0:
        raise RuntimeError("John Doe tests: CombatRuntimeTest class closing brace missing")
    test = test[:close] + new_tests + test[close:]

for marker in (
    'johnDoeUsesExactHpSixPercentAttackAndThirtyRegen',
    'assertEquals(1234, start.entityMaxHp)',
    'johnDoePoisonCanProcAfterThreeTurnsAndHitsWholeActivePartyForFourPercent',
    'johnDoeSpecialCanDealTwentyPercentAndStunNextPartyTurn',
):
    if marker not in test:
        raise RuntimeError("John Doe regression test missing: " + marker)
TEST.write_text(test, encoding="utf-8")

print("John Doe Entity installed: exact 1234 HP, 6% attack, 50% team poison after 3 turns (4% Max HP/turn), 30% two-turn special (20% + 1-turn Party stun), +30 HP/turn, independent 10% Level 0-999 encounter.")
