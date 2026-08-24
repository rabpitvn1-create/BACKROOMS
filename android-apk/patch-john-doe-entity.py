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


# John Doe runs after the finalized shared pool, Diệp Minh and Monster X layers.
# Keep his user-locked values outside generic Entity durability/scaling rules.
combat = COMBAT.read_text(encoding="utf-8")

constants_anchor = '  private const val MONSTER_X_STUN_TURNS_KEY = "combat.monsterXStunTurns"\n'
constants = '''  private const val JOHN_DOE_KEY = "john_doe"
  private const val JOHN_DOE_MAX_HP = 1234
  private const val JOHN_DOE_ATTACK_PERCENT = 6
  private const val JOHN_DOE_REGEN_PER_TURN = 30
  private const val JOHN_DOE_POISON_INTERVAL_TURNS = 3
  private const val JOHN_DOE_POISON_CHANCE_PERCENT = 50
  private const val JOHN_DOE_POISON_DAMAGE_PERCENT = 4
  private const val JOHN_DOE_POISONED_PREFIX = "combat.johnDoePoisoned."
  private const val JOHN_DOE_STUN_INTERVAL_TURNS = 2
  private const val JOHN_DOE_STUN_GATE_PERCENT = 30
  private const val JOHN_DOE_STUN_PROC_PERCENT = 20
  private const val JOHN_DOE_STUN_TURNS_KEY = "combat.johnDoeStunTurns"
  private const val JOHN_DOE_STUN_TARGET_ID_KEY = "combat.johnDoeStunTargetId"
'''
if 'private const val JOHN_DOE_KEY = "john_doe"' not in combat:
    combat = replace_once(combat, constants_anchor, constants_anchor + constants, "John Doe constants")

profile_anchor = '    Profile(MONSTER_X_KEY, "Monster X", MONSTER_X_MAX_HP, 0, 7, 9)\n'
profile_new = '    Profile(MONSTER_X_KEY, "Monster X", MONSTER_X_MAX_HP, 0, 7, 9),\n    Profile(JOHN_DOE_KEY, "John Doe", JOHN_DOE_MAX_HP, 0, 6, 9)\n'
combat = replace_once(combat, profile_anchor, profile_new, "John Doe combat profile")

combat = replace_once(
    combat,
    '    val enhancedEntityMaxHp = when (profile.key) { DIEP_MINH_KEY -> DIEP_MINH_MAX_HP; MONSTER_X_KEY -> MONSTER_X_MAX_HP; else -> profile.maxHp + ENTITY_HP_BONUS }\n',
    '    val enhancedEntityMaxHp = when (profile.key) { DIEP_MINH_KEY -> DIEP_MINH_MAX_HP; MONSTER_X_KEY -> MONSTER_X_MAX_HP; JOHN_DOE_KEY -> JOHN_DOE_MAX_HP; else -> profile.maxHp + ENTITY_HP_BONUS }\n',
    "John Doe exact encounter HP",
)
combat = replace_once(
    combat,
    '    val canonicalMaxHp = when (profile.key) { DIEP_MINH_KEY -> DIEP_MINH_MAX_HP; MONSTER_X_KEY -> MONSTER_X_MAX_HP; else -> profile.maxHp + ENTITY_HP_BONUS }\n',
    '    val canonicalMaxHp = when (profile.key) { DIEP_MINH_KEY -> DIEP_MINH_MAX_HP; MONSTER_X_KEY -> MONSTER_X_MAX_HP; JOHN_DOE_KEY -> JOHN_DOE_MAX_HP; else -> profile.maxHp + ENTITY_HP_BONUS }\n',
    "John Doe exact migrated HP",
)

helper_anchor = '  private fun damageActivePartyByPercent(state: GameState, percent: Int): PartyPercentDamage {\n'
helpers = r'''  private fun johnDoePoisonedIds(state: GameState): Set<String> =
    state.metadata.entries.asSequence()
      .filter { (key, value) -> key.startsWith(JOHN_DOE_POISONED_PREFIX) && value == "true" }
      .map { (key, _) -> key.removePrefix(JOHN_DOE_POISONED_PREFIX) }
      .filter { it.isNotBlank() }
      .toSet()

  private fun johnDoeActivePartyIds(state: GameState): Set<String> =
    state.party.memberIds.distinct().filter { characterId ->
      val character = state.characters[characterId]
      character != null && character.presence == CharacterPresence.ACTIVE && character.vitalState.currentHp > 0
    }.toSet()

  private fun withJohnDoePoisoned(state: GameState, characterIds: Set<String>): GameState {
    val metadata = state.metadata.filterKeys { !it.startsWith(JOHN_DOE_POISONED_PREFIX) }.toMutableMap()
    characterIds.forEach { characterId -> metadata[JOHN_DOE_POISONED_PREFIX + characterId] = "true" }
    return state.copy(metadata = metadata)
  }

  private fun damageJohnDoePoisoned(state: GameState, percent: Int): PartyPercentDamage {
    var next = state
    val lines = mutableListOf<String>()
    johnDoePoisonedIds(state).forEach { characterId ->
      val character = next.characters[characterId] ?: return@forEach
      if (character.presence != CharacterPresence.ACTIVE || character.vitalState.currentHp <= 0) return@forEach
      val maxHp = CharacterStatEngine.effective(next, characterId).maxHp
      val damage = percentDamage(maxHp, percent)
      val before = character.vitalState.currentHp
      next = CharacterStatEngine.setCurrentHp(next, characterId, before - damage)
      val after = next.characters[characterId]?.vitalState?.currentHp ?: max(0, before - damage)
      lines += "${character.name} -$damage HP ($after/$maxHp)"
    }
    val kaiMaxHp = CharacterStatEngine.effective(next, KAI_ID).maxHp
    val kaiHp = next.characters[KAI_ID]?.vitalState?.currentHp?.coerceIn(0, kaiMaxHp) ?: kaiMaxHp
    return PartyPercentDamage(
      state = next,
      kaiHp = kaiHp,
      summary = if (lines.isEmpty()) "không có mục tiêu Poison ACTIVE hợp lệ" else lines.joinToString("; ")
    )
  }

'''
if 'private fun johnDoePoisonedIds(' not in combat:
    combat = replace_once(combat, helper_anchor, helpers + helper_anchor, "John Doe per-character Poison helpers")

intent_old = '''    val requestedIntent = classify(actionKind, action)
    val monsterXStunTurns = state.metadata[MONSTER_X_STUN_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 1) ?: 0
    val monsterXPartyStunned = current.entityKey == MONSTER_X_KEY && monsterXStunTurns > 0
    val intent = if (monsterXPartyStunned) Intent.OTHER else requestedIntent
    var c = current.copy(eventCounter = current.eventCounter + 1)
    val log = mutableListOf<String>()
'''
intent_new = '''    val requestedIntent = classify(actionKind, action)
    val monsterXStunTurns = state.metadata[MONSTER_X_STUN_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 1) ?: 0
    val monsterXPartyStunned = current.entityKey == MONSTER_X_KEY && monsterXStunTurns > 0
    val johnDoeStunTurns = state.metadata[JOHN_DOE_STUN_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 1) ?: 0
    val johnDoeStunTargetId = state.metadata[JOHN_DOE_STUN_TARGET_ID_KEY].orEmpty()
    val johnDoeTargetStunned = current.entityKey == JOHN_DOE_KEY && johnDoeStunTurns > 0 && johnDoeStunTargetId == KAI_ID
    val johnDoeHasActiveTeammate = state.party.memberIds.distinct().any { characterId ->
      characterId != KAI_ID && state.characters[characterId]?.let { character ->
        character.presence == CharacterPresence.ACTIVE && character.vitalState.currentHp > 0
      } == true
    }
    val intent = if (monsterXPartyStunned || (johnDoeTargetStunned && !johnDoeHasActiveTeammate)) Intent.OTHER else requestedIntent
    var c = current.copy(eventCounter = current.eventCounter + 1)
    val log = mutableListOf<String>()
'''
combat = replace_once(combat, intent_old, intent_new, "John Doe target-specific Stun locals")

resolved_old = '''    var resolvedState = state
    var monsterXBleedTurns = state.metadata[MONSTER_X_BLEED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, MONSTER_X_BLEED_DURATION_TURNS) ?: 0
    if (monsterXPartyStunned) {
      resolvedState = withCombatCounter(resolvedState, MONSTER_X_STUN_TURNS_KEY, 0)
      log += "Monster X Stun: toàn bộ Party mất lượt hành động hiện tại."
    }
'''
resolved_new = '''    var resolvedState = state
    var monsterXBleedTurns = state.metadata[MONSTER_X_BLEED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, MONSTER_X_BLEED_DURATION_TURNS) ?: 0
    if (monsterXPartyStunned) {
      resolvedState = withCombatCounter(resolvedState, MONSTER_X_STUN_TURNS_KEY, 0)
      log += "Monster X Stun: toàn bộ Party mất lượt hành động hiện tại."
    }
    if (johnDoeTargetStunned) {
      resolvedState = withCombatCounter(resolvedState, JOHN_DOE_STUN_TURNS_KEY, 0)
      log += "John Doe Stun: Kai bị Stun và không thể thực hiện hành động trong lượt hiện tại."
    }
'''
combat = replace_once(combat, resolved_old, resolved_new, "John Doe one-turn target Stun consumption")

# If teammates are present they may still execute the Party ATTACK command. Only Kai's
# own base attack and automatic gun skills are suppressed by John Doe's target Stun.
resolve_start = combat.index('  fun resolve(state: GameState, actionKind: String, action: String): Resolution {\n')
resolve_end = combat.index('\n  fun toJson(state: GameState): JSONObject?', resolve_start)
attack_start = combat.find('      Intent.ATTACK -> {\n', resolve_start, resolve_end)
attack_end = combat.find('      Intent.OTHER -> {\n', attack_start, resolve_end)
if attack_start < 0 or attack_end < 0:
    raise RuntimeError("John Doe Stun: final Party ATTACK block missing")
attack = combat[attack_start:attack_end]
if 'John Doe Stun: Kai không thể thực hiện đòn tấn công' not in attack:
    attack = replace_once(
        attack,
        '        if (roll < hitChance) {\n',
        '''        if (johnDoeTargetStunned) {
          log += "John Doe Stun: Kai không thể thực hiện đòn tấn công trong lượt này."
        } else if (roll < hitChance) {
''',
        "John Doe Kai base-attack Stun gate",
    )
    combat = combat[:attack_start] + attack + combat[attack_end:]

kai_skill_replacements = (
    ('    if (intent == Intent.ATTACK) {\n', '    if (intent == Intent.ATTACK && !johnDoeTargetStunned) {\n', "John Doe GCO Stun gate"),
    ('    val isGuiltyCrownTurn = intent == Intent.ATTACK && c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0\n', '    val isGuiltyCrownTurn = intent == Intent.ATTACK && !johnDoeTargetStunned && c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0\n', "John Doe GCO priority Stun gate"),
    ('      if (intent == Intent.ATTACK && !isGuiltyCrownTurn && roll(c.copy(eventCounter = c.eventCounter + 101), 100) < KAI_LAST_REQUIEM_CHANCE_PERCENT) {\n', '      if (intent == Intent.ATTACK && !johnDoeTargetStunned && !isGuiltyCrownTurn && roll(c.copy(eventCounter = c.eventCounter + 101), 100) < KAI_LAST_REQUIEM_CHANCE_PERCENT) {\n', "John Doe Last Requiem Stun gate"),
    ('      if (intent == Intent.ATTACK && !isGuiltyCrownTurn && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 113), 100) < KAI_SILENT_LULLABY_CHANCE_PERCENT) {\n', '      if (intent == Intent.ATTACK && !johnDoeTargetStunned && !isGuiltyCrownTurn && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 113), 100) < KAI_SILENT_LULLABY_CHANCE_PERCENT) {\n', "John Doe Silent Lullaby Stun gate"),
    ('      if (intent == Intent.ATTACK && !isGuiltyCrownTurn && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 127), 100) < KAI_SALVATION_CHANCE_PERCENT) {\n', '      if (intent == Intent.ATTACK && !johnDoeTargetStunned && !isGuiltyCrownTurn && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 127), 100) < KAI_SALVATION_CHANCE_PERCENT) {\n', "John Doe Salvation Stun gate"),
    ('      if ((intent == Intent.ATTACK || intent == Intent.EVADE) && !isGuiltyCrownTurn && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 139), 100) < KAI_QUICK_STEP_CHANCE_PERCENT) {\n', '      if ((intent == Intent.ATTACK || intent == Intent.EVADE) && !johnDoeTargetStunned && !isGuiltyCrownTurn && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 139), 100) < KAI_QUICK_STEP_CHANCE_PERCENT) {\n', "John Doe Quick Step Stun gate"),
)
for old, new, label in kai_skill_replacements:
    combat = replace_once(combat, old, new, label)

poison_anchor = '    if (c.entityKey == MONSTER_X_KEY && monsterXBleedTurns > 0) {\n'
poison_tick = '''    if (c.entityKey == JOHN_DOE_KEY && johnDoePoisonedIds(resolvedState).isNotEmpty()) {
      val poison = damageJohnDoePoisoned(resolvedState, JOHN_DOE_POISON_DAMAGE_PERCENT)
      resolvedState = poison.state
      c = c.copy(playerHp = poison.kaiHp)
      log += "Poison John Doe: từng mục tiêu đang bị ảnh hưởng mất ${JOHN_DOE_POISON_DAMAGE_PERCENT}% Max HP riêng biệt. ${poison.summary}."
    }

'''
if 'Poison John Doe: từng mục tiêu đang bị ảnh hưởng' not in combat:
    combat = replace_once(combat, poison_anchor, poison_tick + poison_anchor, "John Doe per-character Poison tick")

response_anchor = '''    if (entityStunnedThisTurn) {
      log += "Silent Lullaby: ${c.entityName} bị Stun và mất lượt phản ứng hiện tại."
    } else if (c.entityKey == DIEP_MINH_KEY && c.eventCounter % DIEP_MINH_ULTIMATE_INTERVAL_TURNS == 0) {
'''
john_response = '''    if (entityStunnedThisTurn) {
      log += "Silent Lullaby: ${c.entityName} bị Stun và mất lượt phản ứng hiện tại."
    } else if (c.entityKey == JOHN_DOE_KEY) {
      val incomingRoll = roll(c.copy(eventCounter = c.eventCounter + 31), 100)
      val defense = when (intent) { Intent.EVADE -> 34; Intent.GUARD -> 30; Intent.MOVE -> 18; Intent.READ -> 12; else -> 0 } +
        when (c.cover) { Cover.HARD -> 22; Cover.PARTIAL -> 10; Cover.EXPOSED -> 0 } + max(0, c.momentum) * 4
      val quickStepEvasion = if (quickStepTurns > 0) KAI_QUICK_STEP_EVASION_BONUS_PERCENT else 0
      val enemyChance = (profile.aggression * 8 - defense + max(0, -c.momentum) * 7 - quickStepEvasion - companionEnemyAccuracyPenalty).coerceIn(0, 88)
      if (incomingRoll < enemyChance) {
        val damage = percentDamage(c.playerMaxHp, JOHN_DOE_ATTACK_PERCENT)
        val before = resolvedState.characters[KAI_ID]?.vitalState?.currentHp ?: c.playerHp
        resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, KAI_ID, before - damage)
        val hp = resolvedState.characters[KAI_ID]?.vitalState?.currentHp?.coerceIn(0, c.playerMaxHp) ?: max(0, before - damage)
        c = c.copy(playerHp = hp, momentum = max(-3, c.momentum - 1))
        log += "John Doe tấn công: Kai -$damage HP (${JOHN_DOE_ATTACK_PERCENT}% Max HP; ${c.playerHp}/${c.playerMaxHp})."
      } else {
        log += if (quickStepTurns > 0) {
          "Quick Step khiến John Doe hụt đòn; +${KAI_QUICK_STEP_EVASION_BONUS_PERCENT}% Evasion đang hoạt động."
        } else {
          "John Doe không xuyên được thế phòng thủ/di chuyển của Kai."
        }
      }
    } else if (c.entityKey == DIEP_MINH_KEY && c.eventCounter % DIEP_MINH_ULTIMATE_INTERVAL_TURNS == 0) {
'''
combat = replace_once(combat, response_anchor, john_response, "John Doe 6 percent authoritative target attack")

regen_anchor = '    val entityHpBeforeRegen = c.entityHp\n'
status_procs = '''    if (c.entityKey == JOHN_DOE_KEY && c.eventCounter % JOHN_DOE_POISON_INTERVAL_TURNS == 0 &&
        roll(c.copy(eventCounter = c.eventCounter + 607), 100) < JOHN_DOE_POISON_CHANCE_PERCENT) {
      val affected = johnDoePoisonedIds(resolvedState) + johnDoeActivePartyIds(resolvedState)
      resolvedState = withJohnDoePoisoned(resolvedState, affected)
      log += "John Doe gây Poison sau mỗi ${JOHN_DOE_POISON_INTERVAL_TURNS} lượt: proc ${JOHN_DOE_POISON_CHANCE_PERCENT}% thành công; đánh dấu riêng ${affected.size} thành viên ACTIVE."
    }
    if (c.entityKey == JOHN_DOE_KEY && c.eventCounter % JOHN_DOE_STUN_INTERVAL_TURNS == 0 &&
        roll(c.copy(eventCounter = c.eventCounter + 619), 100) < JOHN_DOE_STUN_GATE_PERCENT &&
        roll(c.copy(eventCounter = c.eventCounter + 631), 100) < JOHN_DOE_STUN_PROC_PERCENT) {
      resolvedState = withCombatCounter(resolvedState, JOHN_DOE_STUN_TURNS_KEY, 1)
      resolvedState = resolvedState.copy(metadata = resolvedState.metadata + (JOHN_DOE_STUN_TARGET_ID_KEY to KAI_ID))
      log += "John Doe Stun check: cổng ${JOHN_DOE_STUN_GATE_PERCENT}% và proc ${JOHN_DOE_STUN_PROC_PERCENT}% cùng thành công; Kai bị Stun 1 lượt kế tiếp."
    }

    val entityHpBeforeRegen = c.entityHp
'''
if 'John Doe Stun check: cổng' not in combat:
    combat = replace_once(combat, regen_anchor, status_procs, "John Doe Poison/Stun scheduling")

regen_old = '    val entityRegen = when (c.entityKey) { DIEP_MINH_KEY -> DIEP_MINH_REGEN_PER_TURN; MONSTER_X_KEY -> MONSTER_X_REGEN_PER_TURN; else -> ENTITY_REGEN_PER_TURN }\n'
regen_new = '    val entityRegen = when (c.entityKey) { DIEP_MINH_KEY -> DIEP_MINH_REGEN_PER_TURN; MONSTER_X_KEY -> MONSTER_X_REGEN_PER_TURN; JOHN_DOE_KEY -> JOHN_DOE_REGEN_PER_TURN; else -> ENTITY_REGEN_PER_TURN }\n'
combat = replace_once(combat, regen_old, regen_new, "John Doe 30 HP regeneration")

for marker in (
    'private const val JOHN_DOE_MAX_HP = 1234',
    'private const val JOHN_DOE_ATTACK_PERCENT = 6',
    'private const val JOHN_DOE_REGEN_PER_TURN = 30',
    'private const val JOHN_DOE_POISON_INTERVAL_TURNS = 3',
    'private const val JOHN_DOE_POISON_CHANCE_PERCENT = 50',
    'private const val JOHN_DOE_POISON_DAMAGE_PERCENT = 4',
    'private const val JOHN_DOE_STUN_INTERVAL_TURNS = 2',
    'private const val JOHN_DOE_STUN_GATE_PERCENT = 30',
    'private const val JOHN_DOE_STUN_PROC_PERCENT = 20',
    'Profile(JOHN_DOE_KEY, "John Doe", JOHN_DOE_MAX_HP, 0, 6, 9)',
    'damageJohnDoePoisoned(resolvedState, JOHN_DOE_POISON_DAMAGE_PERCENT)',
    'John Doe Stun: Kai bị Stun',
    'John Doe tấn công: Kai -$damage HP',
    'JOHN_DOE_KEY -> JOHN_DOE_REGEN_PER_TURN',
):
    if marker not in combat:
        raise RuntimeError("John Doe combat contract missing: " + marker)
if 'JOHN_DOE_STUN_DAMAGE' in combat:
    raise RuntimeError("John Doe Stun must not deal an invented 20% damage hit")
COMBAT.write_text(combat, encoding="utf-8")


# Independent 10% encounter on raw Levels 0..999. Preserve existing priority:
# Diệp Minh unique boss > Monster X unique roaming > John Doe > shared pool.
main = MAIN.read_text(encoding="utf-8")

monster_roll_anchor = '    rolls.put("monsterXEncounter", monsterXRoll);\n'
john_roll = '''    int johnDoeLevel = rawLevelNumber(state);
    JSONObject johnDoeRoll = thresholdRoll("johnDoeEncounter", 10000, 1000,
      entityEncounterAction && entityAllowed && johnDoeLevel >= 0 && johnDoeLevel <= 999,
      " John Doe unique roaming 10% Level 0-999");
    rolls.put("johnDoeEncounter", johnDoeRoll);
'''
if 'rolls.put("johnDoeEncounter", johnDoeRoll);' not in main:
    main = replace_once(main, monster_roll_anchor, monster_roll_anchor + john_roll, "John Doe independent 10 percent roll")

normalized_old = '      case "jeff_the_killer": case "jane_the_killer": case "slenderman": case "diep_minh": case "monster_x":\n        return key;\n'
normalized_new = '      case "jeff_the_killer": case "jane_the_killer": case "slenderman": case "diep_minh": case "monster_x": case "john_doe":\n        return key;\n'
main = replace_once(main, normalized_old, normalized_new, "John Doe canonical key")

name_anchor = '      case "monster_x": name = "Monster X"; break;\n'
name_new = '      case "monster_x": name = "Monster X"; break;\n      case "john_doe": name = "John Doe"; break;\n'
main = replace_once(main, name_anchor, name_new, "John Doe overlay display name")

asset_old = '      .put("url", "file:///android_asset/entity/" + ("monster_x".equals(entityKey) ? "X.png" : entityKey + ".png"));\n'
asset_new = '      .put("url", "file:///android_asset/entity/" + ("monster_x".equals(entityKey) ? "X.png" : ("john_doe".equals(entityKey) ? "John.png" : entityKey + ".png")));\n'
main = replace_once(main, asset_old, asset_new, "John Doe exact local John.png asset")

js_old = "'jeff_the_killer','jane_the_killer','slenderman','diep_minh','monster_x'];"
js_new = "'jeff_the_killer','jane_the_killer','slenderman','diep_minh','monster_x','john_doe'];"
main = replace_once(main, js_old, js_new, "John Doe overlay JavaScript key")

helper_start = main.find('  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {')
helper_end = main.find('\n  private JSONObject resolveEntityOverlay(String rawEntityKey) throws Exception {', helper_start)
if helper_start < 0 or helper_end < 0:
    raise RuntimeError("John Doe final encounter helper boundary missing")
helper = r'''  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {
    if (candidateState == null || rolls == null) return;
    String entityKey = rolls.optString("roamingEntityKey", "").trim();
    JSONObject boss = rolls.optJSONObject("diepMinhEncounter");
    JSONObject monsterX = rolls.optJSONObject("monsterXEncounter");
    JSONObject johnDoe = rolls.optJSONObject("johnDoeEncounter");
    if (boss != null && boss.optBoolean("success", false)) {
      entityKey = "diep_minh";
    } else if (monsterX != null && monsterX.optBoolean("success", false)) {
      entityKey = "monster_x";
    } else if (johnDoe != null && johnDoe.optBoolean("success", false)) {
      entityKey = "john_doe";
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
    'thresholdRoll("johnDoeEncounter", 10000, 1000',
    'johnDoeLevel >= 0 && johnDoeLevel <= 999',
    'rolls.put("johnDoeEncounter", johnDoeRoll)',
    'case "john_doe":',
    'case "john_doe": name = "John Doe"; break;',
    '"john_doe".equals(entityKey) ? "John.png"',
    "'monster_x','john_doe']",
    'JSONObject johnDoe = rolls.optJSONObject("johnDoeEncounter")',
    'entityKey = "john_doe";',
):
    if marker not in main:
        raise RuntimeError("John Doe encounter/overlay contract missing: " + marker)
MAIN.write_text(main, encoding="utf-8")


# Regression coverage is appended after all earlier combat compatibility rewrites.
test = TEST.read_text(encoding="utf-8")
if 'johnDoeHasExactHpAndThirtyRegen' not in test:
    tests = r'''
  @Test fun johnDoeHasExactHpAndThirtyRegen() {
    var state = CombatRuntime.start(GameState.initial(), "john_doe")
    val started = CombatRuntime.active(state)!!
    assertEquals(1234, started.entityMaxHp)
    assertEquals(1234, started.entityHp)
    state = state.copy(metadata = state.metadata + ("combat.entityHp" to "1100"))
    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
    assertEquals(1130, CombatRuntime.active(result.state)!!.entityHp)
    assertTrue(result.reply, result.reply.contains("hồi +30 HP"))
  }

  @Test fun johnDoeAttackUsesSixPercentTargetMaxHpAndPersistsVitals() {
    var verified = false
    for (counter in 0..600) {
      if (verified) break
      val turn = counter + 1
      if (turn % 2 == 0 || turn % 3 == 0) continue
      var state = CombatRuntime.start(GameState.initial(), "john_doe")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val maxHp = CharacterStatEngine.effective(state, KAI_ID).maxHp
      val before = state.characters.getValue(KAI_ID).vitalState.currentHp
      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
      if (!result.reply.contains("John Doe tấn công:")) continue
      val expected = maxOf(1, (maxHp * 6 + 99) / 100)
      assertTrue(result.reply, result.reply.contains("6% Max HP"))
      assertEquals(before - expected, result.state.characters.getValue(KAI_ID).vitalState.currentHp)
      verified = true
    }
    assertTrue("Expected John Doe to land an exact 6% Max-HP attack", verified)
  }

  @Test fun johnDoePoisonTracksAffectedPartyMembersSeparatelyAndTicksFourPercent() {
    val initial = GameState.initial()
    val iris = CharacterState(
      id = "iris",
      name = "Iris",
      statProfile = CharacterStatProfiles.forId("iris"),
      vitalState = CharacterStatProfiles.initialVitals("iris")
    )
    var triggered: GameState? = null
    for (counter in 0..900) {
      if (triggered != null) break
      val turn = counter + 1
      if (turn % 3 != 0) continue
      var state = initial.copy(
        characters = initial.characters + ("iris" to iris),
        party = PartyState(memberIds = listOf(KAI_ID, "iris"))
      )
      state = CombatRuntime.start(state, "john_doe")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
      if (!result.reply.contains("John Doe gây Poison")) continue
      assertEquals("true", result.state.metadata["combat.johnDoePoisoned.$KAI_ID"])
      assertEquals("true", result.state.metadata["combat.johnDoePoisoned.iris"])
      triggered = result.state
    }
    assertNotNull("Expected deterministic search to reach the 50% Poison proc", triggered)
    val poisoned = triggered!!
    val irisBefore = poisoned.characters.getValue("iris").vitalState.currentHp
    val irisMax = CharacterStatEngine.effective(poisoned, "iris").maxHp
    val tick = CombatRuntime.resolve(poisoned, "EXECUTE", "Cả Party cùng né tránh")
    val expected = maxOf(1, (irisMax * 4 + 99) / 100)
    assertTrue(tick.reply, tick.reply.contains("Poison John Doe"))
    assertTrue(tick.reply, tick.reply.contains("Iris -$expected HP"))
    assertEquals(irisBefore - expected, tick.state.characters.getValue("iris").vitalState.currentHp)
  }

  @Test fun johnDoeStunUsesThirtyThenTwentyPercentAndBlocksOnlyTargetForOneTurn() {
    var triggered: GameState? = null
    for (counter in 0..1800) {
      if (triggered != null) break
      val turn = counter + 1
      if (turn % 2 != 0 || turn % 3 == 0) continue
      var state = CombatRuntime.start(GameState.initial(), "john_doe")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
      if (!result.reply.contains("John Doe Stun check:")) continue
      assertTrue(result.reply, result.reply.contains("cổng 30%"))
      assertTrue(result.reply, result.reply.contains("proc 20%"))
      assertFalse(result.reply, result.reply.contains("20% Max HP"))
      assertEquals("1", result.state.metadata["combat.johnDoeStunTurns"])
      assertEquals(KAI_ID, result.state.metadata["combat.johnDoeStunTargetId"])
      triggered = result.state
    }
    assertNotNull("Expected deterministic search to reach the 30% x 20% Stun proc", triggered)
    val stunned = triggered!!
    val beforeEntityHp = CombatRuntime.active(stunned)!!.entityHp
    val next = CombatRuntime.resolve(stunned, "EXECUTE", "Cả Party cùng tấn công")
    assertTrue(next.reply, next.reply.contains("Kai bị Stun và không thể thực hiện hành động"))
    assertFalse(next.reply, next.reply.contains("PARTY ACTION TẤN CÔNG"))
    assertFalse(next.reply, next.reply.contains("The Last Requiem tự động kích hoạt"))
    assertFalse(next.reply, next.reply.contains("Silent Lullaby tự động kích hoạt"))
    val after = CombatRuntime.active(next.state)!!
    assertEquals(minOf(1234, beforeEntityHp + 30), after.entityHp)
    assertNull(next.state.metadata["combat.johnDoeStunTurns"])
  }
'''
    close = test.rfind('\n}')
    if close < 0:
        raise RuntimeError("CombatRuntimeTest closing brace missing")
    test = test[:close] + tests + test[close:]
    TEST.write_text(test, encoding="utf-8")

if not ASSET.is_file() or ASSET.stat().st_size <= 0:
    raise RuntimeError("John Doe asset missing: android-apk/app/src/main/assets/entity/John.png")
raw = ASSET.read_bytes()
if raw[:8] != b'\x89PNG\r\n\x1a\n':
    raise RuntimeError("John.png is not a valid PNG asset")

print("John Doe installed: 1234 HP, 6% target Max-HP attack, per-member 4% Poison every 3-turn 50% proc, two-stage 30% x 20% one-target Stun, +30 HP regen, independent 10% Level 0-999 encounter, direct John.png asset.")
