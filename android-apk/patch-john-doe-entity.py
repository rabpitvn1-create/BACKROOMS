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
# CombatRuntime
# ---------------------------------------------------------------------------
# This patch is deliberately last in the Entity patch chain. It extends the
# finalized Monster X shape instead of replacing any existing combat system.
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
'''
if 'private const val JOHN_DOE_KEY = "john_doe"' not in combat:
    combat = replace_once(combat, constants_anchor, constants_anchor + constants, "John Doe constants")

profile_anchor = '    Profile(MONSTER_X_KEY, "Monster X", MONSTER_X_MAX_HP, 0, 7, 9)\n'
profile_new = '    Profile(MONSTER_X_KEY, "Monster X", MONSTER_X_MAX_HP, 0, 7, 9),\n    Profile(JOHN_DOE_KEY, "John Doe", JOHN_DOE_MAX_HP, 0, 6, 9)\n'
combat = replace_once(combat, profile_anchor, profile_new, "John Doe profile")

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

# Poison membership is persisted per character ID, not as one Party-wide boolean.
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
    combat = replace_once(combat, helper_anchor, helpers + helper_anchor, "John Doe poison helpers")

# The current combat target is Kai in the authoritative Entity response model.
# A successful John Doe Stun therefore suppresses the next player action exactly
# once. It does not deal damage and is independent from Poison.
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
    val johnDoeTargetStunned = current.entityKey == JOHN_DOE_KEY && johnDoeStunTurns > 0
    val intent = if (monsterXPartyStunned || johnDoeTargetStunned) Intent.OTHER else requestedIntent
    var c = current.copy(eventCounter = current.eventCounter + 1)
    val log = mutableListOf<String>()
'''
combat = replace_once(combat, intent_old, intent_new, "John Doe one-turn Stun intent")

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
      log += "John Doe Stun: mục tiêu bị Stun và không thể thực hiện hành động trong lượt hiện tại."
    }
'''
combat = replace_once(combat, resolved_old, resolved_new, "John Doe Stun consumption")

# Poison ticks separately for every persisted affected member.
poison_anchor = '    if (c.entityKey == MONSTER_X_KEY && monsterXBleedTurns > 0) {\n'
poison_tick = '''    if (c.entityKey == JOHN_DOE_KEY && johnDoePoisonedIds(resolvedState).isNotEmpty()) {
      val poison = damageJohnDoePoisoned(resolvedState, JOHN_DOE_POISON_DAMAGE_PERCENT)
      resolvedState = poison.state
      c = c.copy(playerHp = poison.kaiHp)
      log += "Poison John Doe: từng mục tiêu đang bị ảnh hưởng mất ${JOHN_DOE_POISON_DAMAGE_PERCENT}% Max HP riêng biệt. ${poison.summary}."
    }

'''
if 'Poison John Doe: từng mục tiêu đang bị ảnh hưởng' not in combat:
    combat = replace_once(combat, poison_anchor, poison_tick + poison_anchor, "John Doe per-member poison tick")

# John Doe gets its own response branch so a landed attack is exactly 6% of the
# target's Max HP while retaining the existing defense/evasion mechanics.
response_anchor = '''    if (entityStunnedThisTurn) {
      log += "Silent Lullaby: ${c.entityName} bị Stun và mất lượt phản ứng hiện tại."
    } else if (c.entityKey == DIEP_MINH_KEY && c.eventCounter % DIEP_MINH_ULTIMATE_INTERVAL_TURNS == 0) {
'''
response_new = '''    if (entityStunnedThisTurn) {
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
        log += "John Doe không xuyên được thế phòng thủ/di chuyển của Kai."
      }
    } else if (c.entityKey == DIEP_MINH_KEY && c.eventCounter % DIEP_MINH_ULTIMATE_INTERVAL_TURNS == 0) {
'''
combat = replace_once(combat, response_anchor, response_new, "John Doe 6 percent response")

# Proc scheduling happens after the current response. Poison starts ticking on
# the following combat turn; Stun suppresses exactly the following action.
regen_anchor = '    val entityHpBeforeRegen = c.entityHp\n'
status_procs = '''    if (c.entityKey == JOHN_DOE_KEY && c.eventCounter % JOHN_DOE_POISON_INTERVAL_TURNS == 0 &&
        roll(c.copy(eventCounter = c.eventCounter + 607), 100) < JOHN_DOE_POISON_CHANCE_PERCENT) {
      val affected = johnDoePoisonedIds(resolvedState) + johnDoeActivePartyIds(resolvedState)
      resolvedState = withJohnDoePoisoned(resolvedState, affected)
      log += "John Doe gây Poison sau ${JOHN_DOE_POISON_INTERVAL_TURNS} lượt: proc ${JOHN_DOE_POISON_CHANCE_PERCENT}% thành công; đánh dấu riêng ${affected.size} thành viên ACTIVE."
    }
    if (c.entityKey == JOHN_DOE_KEY && c.eventCounter % JOHN_DOE_STUN_INTERVAL_TURNS == 0 &&
        roll(c.copy(eventCounter = c.eventCounter + 619), 100) < JOHN_DOE_STUN_GATE_PERCENT &&
        roll(c.copy(eventCounter = c.eventCounter + 631), 100) < JOHN_DOE_STUN_PROC_PERCENT) {
      resolvedState = withCombatCounter(resolvedState, JOHN_DOE_STUN_TURNS_KEY, 1)
      log += "John Doe Stun check: cổng ${JOHN_DOE_STUN_GATE_PERCENT}% và proc ${JOHN_DOE_STUN_PROC_PERCENT}% cùng thành công; mục tiêu bị Stun 1 lượt kế tiếp."
    }

    val entityHpBeforeRegen = c.entityHp
'''
if 'John Doe Stun check: cổng' not in combat:
    combat = replace_once(combat, regen_anchor, status_procs, "John Doe proc scheduling")

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
    'John Doe Stun: mục tiêu bị Stun',
    'John Doe tấn công: Kai -$damage HP',
    'JOHN_DOE_KEY -> JOHN_DOE_REGEN_PER_TURN',
):
    if marker not in combat:
        raise RuntimeError("John Doe combat contract missing: " + marker)
if 'JOHN_DOE_STUN_DAMAGE' in combat:
    raise RuntimeError("John Doe 20% Stun value must remain a proc chance, not damage")
COMBAT.write_text(combat, encoding="utf-8")


# ---------------------------------------------------------------------------
# Android encounter + overlay
# ---------------------------------------------------------------------------
# Independent 10% encounter on raw Levels 0..999. Existing unique priority is
# preserved: Diệp Minh > Monster X > John Doe > shared roaming pool.
main = MAIN.read_text(encoding="utf-8")

monster_roll_anchor = '    rolls.put("monsterXEncounter", monsterXRoll);\n'
john_roll = '''    int johnDoeLevel = rawLevelNumber(state);
    JSONObject johnDoeRoll = thresholdRoll("johnDoeEncounter", 10000, 1000,
      entityEncounterAction && entityAllowed && johnDoeLevel >= 0 && johnDoeLevel <= 999,
      " John Doe unique roaming 10% Level 0-999");
    rolls.put("johnDoeEncounter", johnDoeRoll);
'''
if 'rolls.put("johnDoeEncounter", johnDoeRoll);' not in main:
    main = replace_once(main, monster_roll_anchor, monster_roll_anchor + john_roll, "John Doe independent encounter roll")

main = replace_once(
    main,
    '      case "jeff_the_killer": case "jane_the_killer": case "slenderman": case "diep_minh": case "monster_x":\n        return key;\n',
    '      case "jeff_the_killer": case "jane_the_killer": case "slenderman": case "diep_minh": case "monster_x": case "john_doe":\n        return key;\n',
    "John Doe canonical key",
)
main = replace_once(
    main,
    '      case "monster_x": name = "Monster X"; break;\n',
    '      case "monster_x": name = "Monster X"; break;\n      case "john_doe": name = "John Doe"; break;\n',
    "John Doe display name",
)
main = replace_once(
    main,
    '      .put("url", "file:///android_asset/entity/" + ("monster_x".equals(entityKey) ? "X.png" : entityKey + ".png"));\n',
    '      .put("url", "file:///android_asset/entity/" + ("monster_x".equals(entityKey) ? "X.png" : ("john_doe".equals(entityKey) ? "John.png" : entityKey + ".png")));\n',
    "John Doe direct John.png asset",
)
main = replace_once(
    main,
    "'jeff_the_killer','jane_the_killer','slenderman','diep_minh','monster_x'];",
    "'jeff_the_killer','jane_the_killer','slenderman','diep_minh','monster_x','john_doe'];",
    "John Doe overlay JS key",
)

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


# ---------------------------------------------------------------------------
# Regression coverage
# ---------------------------------------------------------------------------
test = TEST.read_text(encoding="utf-8")
if 'johnDoeHasExactHpAndThirtyRegen' not in test:
    tests = r'''
  @Test fun johnDoeHasExactHpAndThirtyRegen() {
    var state = CombatRuntime.start(GameState.initial(), "john_doe")
    assertEquals(1234, CombatRuntime.active(state)!!.entityMaxHp)
    state = state.copy(metadata = state.metadata + ("combat.entityHp" to "1100"))
    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
    assertEquals(1130, CombatRuntime.active(result.state)!!.entityHp)
    assertTrue(result.reply, result.reply.contains("hồi +30 HP"))
  }

  @Test fun johnDoeAttackUsesSixPercentTargetMaxHp() {
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

  @Test fun johnDoePoisonTracksAffectedMembersSeparatelyAndTicksFourPercent() {
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

  @Test fun johnDoeStunUsesThirtyThenTwentyPercentAndBlocksOneTurnWithoutDamage() {
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
      triggered = result.state
    }
    assertNotNull("Expected deterministic search to reach the 30% x 20% Stun proc", triggered)
    val stunned = triggered!!
    val before = CombatRuntime.active(stunned)!!.entityHp
    val next = CombatRuntime.resolve(stunned, "EXECUTE", "Cả Party cùng tấn công")
    assertTrue(next.reply, next.reply.contains("không thể thực hiện hành động trong lượt hiện tại"))
    assertFalse(next.reply, next.reply.contains("PARTY ACTION TẤN CÔNG"))
    assertNull(next.state.metadata["combat.johnDoeStunTurns"])
    assertEquals(minOf(1234, before + 30), CombatRuntime.active(next.state)!!.entityHp)
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

print("John Doe installed: exact 1234 HP, 6% Max-HP target attack, per-member 4% Poison, two-stage 30% x 20% one-turn Stun, +30 HP regen, independent 10% Level 0-999 encounter, direct John.png asset.")
