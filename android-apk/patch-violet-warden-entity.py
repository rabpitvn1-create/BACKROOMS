from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"
ASSET = ROOT / "app/src/main/assets/entity/Violet.png"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# ---------------------------------------------------------------------------
# CombatRuntime: The Violet Warden is a unique former-human Entity boss built as
# a single-target Control / Duelist / Counter specialist. It intentionally runs
# after every existing combat/balance layer so it extends final authority without
# rewriting Diệp Minh, SCP-173, Jeff/Jane, Party actions, or the shared schema.
# ---------------------------------------------------------------------------
combat = COMBAT.read_text(encoding="utf-8")

constants_anchor = '  private const val JANE_VENGEFUL_COOLDOWN = 4\n'
constants = '''  private const val VIOLET_WARDEN_KEY = "violet_warden"
  private const val VIOLET_WARDEN_MAX_HP = 3319
  private const val VIOLET_WARDEN_ATTACK_PERCENT = 11
  private const val VIOLET_WARDEN_REGEN_PER_TURN = 33
  private const val VIOLET_WARDEN_BLOCK_PERCENT = 60
  private const val VIOLET_WARDEN_BLOCK_REDUCTION_PERCENT = 30
  private const val VIOLET_WARDEN_CONTROL_INTERVAL_TURNS = 3
  private const val VIOLET_WARDEN_CONTROL_DAMAGE_PERCENT = 6
  private const val VIOLET_WARDEN_CONTROL_STUN_PERCENT = 50
  private const val VIOLET_WARDEN_ULTIMATE_INTERVAL_TURNS = 5
  private const val VIOLET_WARDEN_ULTIMATE_DAMAGE_PERCENT = 8
  private const val VIOLET_WARDEN_RIPOSTE_DAMAGE_PERCENT = 13
  private const val VIOLET_WARDEN_DUEL_TARGET_KEY = "combat.violetWardenDuelTargetId"
  private const val VIOLET_WARDEN_RIPOSTE_READY_KEY = "combat.violetWardenRiposteReady"
  private const val VIOLET_WARDEN_STATUS_PREFIX = "violet_warden:"
'''
if 'private const val VIOLET_WARDEN_KEY = "violet_warden"' not in combat:
    combat = replace_once(combat, constants_anchor, constants_anchor + constants, "Violet Warden constants")

profile_anchor = '    Profile(SCP_173_KEY, "SCP-173", SCP_173_MAX_HP, 0, 9, 10)\n'
profile_new = '    Profile(SCP_173_KEY, "SCP-173", SCP_173_MAX_HP, 0, 9, 10),\n    Profile(VIOLET_WARDEN_KEY, "The Violet Warden", VIOLET_WARDEN_MAX_HP, 0, 9, 9)\n'
combat = replace_once(combat, profile_anchor, profile_new, "Violet Warden combat profile")

# Final 1.1.71 HP tier adds +200 to base HP > 1000. Keep the base at 3319 so the
# effective encounter HP is exactly 3519, which is +10% over Diệp Minh's 3199.
hp_old = 'DIEP_MINH_KEY -> DIEP_MINH_MAX_HP; MONSTER_X_KEY -> MONSTER_X_MAX_HP; JOHN_DOE_KEY -> JOHN_DOE_MAX_HP; SCP_173_KEY -> SCP_173_MAX_HP; JEFF_KEY, JANE_KEY -> profile.maxHp; else -> profile.maxHp + ENTITY_HP_BONUS'
hp_new = 'DIEP_MINH_KEY -> DIEP_MINH_MAX_HP; MONSTER_X_KEY -> MONSTER_X_MAX_HP; JOHN_DOE_KEY -> JOHN_DOE_MAX_HP; SCP_173_KEY -> SCP_173_MAX_HP; VIOLET_WARDEN_KEY -> VIOLET_WARDEN_MAX_HP; JEFF_KEY, JANE_KEY -> profile.maxHp; else -> profile.maxHp + ENTITY_HP_BONUS'
if hp_new not in combat:
    count = combat.count(hp_old)
    if count != 2:
        raise RuntimeError(f"Violet Warden HP tier: expected 2 start/decode anchors, found {count}")
    combat = combat.replace(hp_old, hp_new)

helper_anchor = '  // ENTITY_PARTY_ACTION_BUDGET_V1: direct Entity targets only.\n'
helpers = r'''  // VIOLET_WARDEN_V1: unique duel/control helpers.
  private fun entityEvasionPercent(entityKey: String): Int =
    if (entityKey == VIOLET_WARDEN_KEY) 0 else ENTITY_EVASION_PERCENT

  private fun violetWardenMetadata(state: GameState, key: String, value: String?): GameState {
    val metadata = state.metadata.toMutableMap()
    if (value.isNullOrBlank()) metadata.remove(key) else metadata[key] = value
    return state.copy(metadata = metadata)
  }

  private fun violetWardenDuelTarget(state: GameState): String? {
    val candidates = entityCombatActionTargets(state)
    val locked = state.metadata[VIOLET_WARDEN_DUEL_TARGET_KEY].orEmpty()
    if (locked in candidates) return locked
    return candidates.maxWithOrNull(compareBy<String> { CharacterStatEngine.weaponDamage(state, it) }.thenBy { it })
  }

  private fun violetWardenApplyStun(state: GameState, characterId: String, eventCounter: Int): GameState {
    if (characterId !in state.characters) return state
    val id = VIOLET_WARDEN_STATUS_PREFIX + "stun:" + characterId
    val effect = StatusEffect(
      id = id,
      type = "STUN",
      source = VIOLET_WARDEN_KEY,
      startTurnId = state.turn.currentTurnId,
      durationTurns = 1,
      persistent = false,
      metadata = mapOf("combatEvent" to eventCounter.toString())
    )
    val operation = if (id in state.statuses) StatusCommand.Operation.UPDATE else StatusCommand.Operation.APPLY
    val result = StatusEngine.execute(state, StatusCommand(
      commandId = "VIOLET_WARDEN:STUN:$characterId:$eventCounter",
      turnId = state.turn.currentTurnId,
      actorId = KAI_ID,
      targetId = characterId,
      source = CommandSource.SYSTEM,
      operation = operation,
      effect = effect,
      statusId = id
    ))
    return if (result.applied) result.state else state
  }

'''
if 'VIOLET_WARDEN_V1' not in combat:
    combat = replace_once(combat, helper_anchor, helpers + helper_anchor, "Violet Warden helpers")

# Violet replaces shared 17% Entity evasion with its own Block identity.
combat = combat.replace('>= ENTITY_EVASION_PERCENT', '>= entityEvasionPercent(c.entityKey)')
combat = combat.replace('< ENTITY_EVASION_PERCENT', '< entityEvasionPercent(c.entityKey)')

# Apply one Block resolution to the complete ATTACK package before the final
# pre-response death gate. This preserves simultaneous Party attacks without
# adding hidden turns. A successful Block restores 30% of damage taken in that
# ATTACK event and primes one Riposte.
response_anchor = '    // Enemy response. Diệp Minh uses percentage damage; all other Entity behavior remains unchanged.\n'
response_pos = combat.find(response_anchor)
if response_pos < 0:
    raise RuntimeError("Violet Warden response anchor missing")
death_pos = combat.rfind('    if (c.entityHp <= 0) {\n', 0, response_pos)
if death_pos < 0:
    raise RuntimeError("Violet Warden pre-response death gate missing")
block_code = r'''    // VIOLET_WARDEN_BLOCK_V1: one directional guard resolution for the Party ATTACK package.
    if (c.entityKey == VIOLET_WARDEN_KEY && intent == Intent.ATTACK && c.entityHp > 0) {
      val damageTaken = max(0, current.entityHp - c.entityHp)
      if (damageTaken > 0 && roll(c.copy(eventCounter = c.eventCounter + 1201), 100) < VIOLET_WARDEN_BLOCK_PERCENT) {
        val restored = max(1, (damageTaken * VIOLET_WARDEN_BLOCK_REDUCTION_PERCENT + 99) / 100)
        val blockedHp = min(c.entityMaxHp, c.entityHp + restored)
        val actualRestored = blockedHp - c.entityHp
        c = c.copy(entityHp = blockedHp, entityCondition = condition(blockedHp, c.entityMaxHp))
        resolvedState = violetWardenMetadata(resolvedState, VIOLET_WARDEN_RIPOSTE_READY_KEY, "true")
        log += "Violet Guard: The Violet Warden Block thành công; giảm $VIOLET_WARDEN_BLOCK_REDUCTION_PERCENT% gói direct ATTACK (-$actualRestored damage được triệt tiêu) và chuẩn bị Violet Riposte."
      }
    }

'''
if 'VIOLET_WARDEN_BLOCK_V1' not in combat:
    combat = combat[:death_pos] + block_code + combat[death_pos:]

# Insert the boss-specific single-target response before the generic Party-sized
# Entity response. This guarantees one direct target per Entity turn.
generic_response = '''    } else if (c.entityKey != SCP_173_KEY &&
        !(c.entityKey == DIEP_MINH_KEY && c.eventCounter % DIEP_MINH_ULTIMATE_INTERVAL_TURNS == 0)) {
'''
if generic_response not in combat:
    raise RuntimeError("Violet Warden generic Entity response anchor missing")

violet_response = r'''    } else if (c.entityKey == VIOLET_WARDEN_KEY) {
      val duelTargetId = violetWardenDuelTarget(resolvedState)
      if (duelTargetId == null) {
        log += "The Violet Warden không còn Duel Target ACTIVE hợp lệ."
      } else {
        if (resolvedState.metadata[VIOLET_WARDEN_DUEL_TARGET_KEY] != duelTargetId) {
          resolvedState = violetWardenMetadata(resolvedState, VIOLET_WARDEN_DUEL_TARGET_KEY, duelTargetId)
          val locked = resolvedState.characters[duelTargetId]?.name ?: duelTargetId
          log += "Duelist's Decree: The Violet Warden khóa $locked làm Duel Target."
        }
        val target = resolvedState.characters[duelTargetId]
        if (target != null) {
          val targetMaxHp = CharacterStatEngine.effective(resolvedState, duelTargetId).maxHp
          val before = target.vitalState.currentHp.coerceIn(0, targetMaxHp)
          if (before > 0) {
            val personalEvasion = when {
              duelTargetId == KAI_ID -> {
                val quickStep = if (quickStepTurns > 0) KAI_QUICK_STEP_EVASION_BONUS_PERCENT else 0
                quickStep + DevilTriggerPassive.evasionBonus(kaiDevilTriggerActive)
              }
              duelTargetId == SYVIAL_ID && syvialDevilTrigger -> 20
              else -> 0
            }
            val partyDefense = when (intent) { Intent.EVADE -> 34; Intent.GUARD -> 30; Intent.MOVE -> 18; Intent.READ -> 12; else -> 0 } +
              when (c.cover) { Cover.HARD -> 22; Cover.PARTIAL -> 10; Cover.EXPOSED -> 0 } + max(0, c.momentum) * 4
            val enemyChance = (profile.aggression * 8 - partyDefense + max(0, -c.momentum) * 7 - companionEnemyAccuracyPenalty - personalEvasion).coerceIn(0, 88)
            val incomingRoll = roll(c.copy(eventCounter = c.eventCounter + 1301), 100)
            if (incomingRoll < enemyChance) {
              val ultimateTurn = c.eventCounter % VIOLET_WARDEN_ULTIMATE_INTERVAL_TURNS == 0
              val controlTurn = !ultimateTurn && c.eventCounter % VIOLET_WARDEN_CONTROL_INTERVAL_TURNS == 0
              val riposteReady = resolvedState.metadata[VIOLET_WARDEN_RIPOSTE_READY_KEY].equals("true", true)
              val percent = when {
                ultimateTurn -> VIOLET_WARDEN_ULTIMATE_DAMAGE_PERCENT
                controlTurn -> VIOLET_WARDEN_CONTROL_DAMAGE_PERCENT
                riposteReady -> VIOLET_WARDEN_RIPOSTE_DAMAGE_PERCENT
                else -> VIOLET_WARDEN_ATTACK_PERCENT
              }
              val damage = min(before, percentDamage(targetMaxHp, percent))
              resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, duelTargetId, before - damage)
              val after = resolvedState.characters[duelTargetId]?.vitalState?.currentHp ?: max(0, before - damage)
              if (!ultimateTurn && !controlTurn && riposteReady) {
                resolvedState = violetWardenMetadata(resolvedState, VIOLET_WARDEN_RIPOSTE_READY_KEY, null)
              }
              if (ultimateTurn && after > 0) {
                resolvedState = violetWardenApplyStun(resolvedState, duelTargetId, c.eventCounter)
                log += "King's Sentence: ${target.name} -$damage HP ($percent% Max HP), STUN 1 turn."
              } else if (controlTurn && after > 0) {
                val stun = roll(c.copy(eventCounter = c.eventCounter + 1327), 100) < VIOLET_WARDEN_CONTROL_STUN_PERCENT
                if (stun) resolvedState = violetWardenApplyStun(resolvedState, duelTargetId, c.eventCounter)
                log += "Pommel Break: ${target.name} -$damage HP ($percent% Max HP); ${if (stun) "STUN 1 turn" else "Stun không proc"}."
              } else if (riposteReady) {
                log += "Violet Riposte: ${target.name} -$damage HP ($percent% Max HP)."
              } else {
                log += "Violet Judgment: ${target.name} -$damage HP ($percent% Max HP)."
              }
              if (duelTargetId == KAI_ID) c = c.copy(playerHp = after, playerMaxHp = targetMaxHp)
              c = c.copy(momentum = max(-3, c.momentum - 1))
            } else {
              log += "${target.name} tránh/đỡ được đòn khóa mục tiêu của The Violet Warden."
            }
          }
        }
      }
'''
if 'Duelist\'s Decree: The Violet Warden' not in combat:
    combat = replace_once(combat, generic_response, violet_response + generic_response, "Violet Warden single-target response")

regen_old = 'SCP_173_KEY -> SCP_173_REGEN_PER_TURN; else -> ENTITY_REGEN_PER_TURN'
regen_new = 'SCP_173_KEY -> SCP_173_REGEN_PER_TURN; VIOLET_WARDEN_KEY -> VIOLET_WARDEN_REGEN_PER_TURN; else -> ENTITY_REGEN_PER_TURN'
if regen_new not in combat:
    if regen_old not in combat:
        raise RuntimeError("Violet Warden regeneration anchor missing")
    combat = combat.replace(regen_old, regen_new, 1)

# Project the boss identity/mechanics through the existing combat JSON without
# changing the Snapshot schema.
to_json_anchor = '    put("telegraphRevealed", c.telegraphRevealed)\n'
to_json = '''    if (c.entityKey == VIOLET_WARDEN_KEY) {
      put("entityType", "Unique Former-Human Entity Boss")
      put("combatRole", "Control / Single Target / Counter")
      put("weapon", "Violet Judgment")
      put("blockPercent", VIOLET_WARDEN_BLOCK_PERCENT)
      put("blockReductionPercent", VIOLET_WARDEN_BLOCK_REDUCTION_PERCENT)
      put("duelTargetId", state.metadata[VIOLET_WARDEN_DUEL_TARGET_KEY] ?: "")
      put("riposteReady", state.metadata[VIOLET_WARDEN_RIPOSTE_READY_KEY].equals("true", true))
      put("originEra", "15th century")
    }
'''
if 'put("combatRole", "Control / Single Target / Counter")' not in combat:
    combat = replace_once(combat, to_json_anchor, to_json_anchor + to_json, "Violet Warden JSON projection")

for marker in (
    'private const val VIOLET_WARDEN_MAX_HP = 3319',
    'private const val VIOLET_WARDEN_ATTACK_PERCENT = 11',
    'private const val VIOLET_WARDEN_REGEN_PER_TURN = 33',
    'private const val VIOLET_WARDEN_BLOCK_PERCENT = 60',
    'private const val VIOLET_WARDEN_BLOCK_REDUCTION_PERCENT = 30',
    'Profile(VIOLET_WARDEN_KEY, "The Violet Warden", VIOLET_WARDEN_MAX_HP, 0, 9, 9)',
    'VIOLET_WARDEN_KEY -> VIOLET_WARDEN_MAX_HP',
    'VIOLET_WARDEN_KEY -> VIOLET_WARDEN_REGEN_PER_TURN',
    'VIOLET_WARDEN_BLOCK_V1',
    'King\'s Sentence:',
    'Pommel Break:',
    'Violet Riposte:',
    'Violet Judgment:',
    'put("blockPercent", VIOLET_WARDEN_BLOCK_PERCENT)',
):
    if marker not in combat:
        raise RuntimeError("Violet Warden combat contract missing: " + marker)
COMBAT.write_text(combat, encoding="utf-8")


# ---------------------------------------------------------------------------
# Android encounter/overlay: independent 10% roll on every valid Entity encounter
# action, with no Level/sublevel restriction and outside the shared roaming pool.
# Existing priority remains: Diệp Minh > Monster X > John Doe > SCP-173 > Violet
# Warden > shared roaming Entity.
# ---------------------------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")

scp_roll_anchor = '    rolls.put("scp173Encounter", scp173Roll);\n'
violet_roll = '''    JSONObject violetWardenRoll = thresholdRoll("violetWardenEncounter", 10000, 1000,
      entityEncounterAction && entityAllowed && !diepMinhRoll.optBoolean("success", false) && !monsterXRoll.optBoolean("success", false) && !johnDoeRoll.optBoolean("success", false) && !scp173Roll.optBoolean("success", false),
      " Violet Warden unique roaming 10% all Levels/sublevels");
    rolls.put("violetWardenEncounter", violetWardenRoll);
'''
if 'rolls.put("violetWardenEncounter", violetWardenRoll);' not in main:
    main = replace_once(main, scp_roll_anchor, scp_roll_anchor + violet_roll, "Violet Warden independent 10 percent roll")

# Shared normal roll must lose to Violet when its independent channel succeeds.
normal_old = '&& !scp173Roll.optBoolean("success", false), entitySuffix);\n'
normal_new = '&& !scp173Roll.optBoolean("success", false) && !violetWardenRoll.optBoolean("success", false), entitySuffix);\n'
if normal_new not in main:
    if normal_old not in main:
        raise RuntimeError("Violet Warden shared roaming priority anchor missing")
    main = main.replace(normal_old, normal_new, 1)

main = replace_once(
    main,
    '      case "scp_173":\n        return key;\n',
    '      case "scp_173": case "violet_warden":\n        return key;\n',
    "Violet Warden canonical key",
)
main = replace_once(
    main,
    '      case "scp_173": name = "SCP-173"; break;\n',
    '      case "scp_173": name = "SCP-173"; break;\n      case "violet_warden": name = "The Violet Warden"; break;\n',
    "Violet Warden display name",
)

# Extend the finalized case-sensitive local asset mapping.
asset_old = '("scp_173".equals(entityKey) ? "SCP173.png" : entityKey + ".png")'
asset_new = '("scp_173".equals(entityKey) ? "SCP173.png" : ("violet_warden".equals(entityKey) ? "Violet.png" : entityKey + ".png"))'
if asset_new not in main:
    if asset_old not in main:
        raise RuntimeError("Violet Warden asset mapping anchor missing")
    main = main.replace(asset_old, asset_new, 1)

array_old = "'john_doe','scp_173'];"
array_new = "'john_doe','scp_173','violet_warden'];"
if array_new not in main:
    if array_old not in main:
        raise RuntimeError("Violet Warden overlay JS array anchor missing")
    main = main.replace(array_old, array_new, 1)

helper_start = main.find('  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {')
helper_end = main.find('\n  private JSONObject resolveEntityOverlay(String rawEntityKey) throws Exception {', helper_start)
if helper_start < 0 or helper_end < 0:
    raise RuntimeError("Violet Warden encounter helper boundary missing")
helper = main[helper_start:helper_end]
if 'JSONObject violetWarden = rolls.optJSONObject("violetWardenEncounter")' not in helper:
    helper = helper.replace(
        '    JSONObject scp173 = rolls.optJSONObject("scp173Encounter");\n',
        '    JSONObject scp173 = rolls.optJSONObject("scp173Encounter");\n    JSONObject violetWarden = rolls.optJSONObject("violetWardenEncounter");\n',
        1,
    )
    helper = helper.replace(
        '    } else {\n      JSONObject normal = rolls.optJSONObject("entityEncounter");\n',
        '    } else if (violetWarden != null && violetWarden.optBoolean("success", false)) {\n      entityKey = "violet_warden";\n    } else {\n      JSONObject normal = rolls.optJSONObject("entityEncounter");\n',
        1,
    )
    main = main[:helper_start] + helper + main[helper_end:]

for marker in (
    'thresholdRoll("violetWardenEncounter", 10000, 1000',
    'rolls.put("violetWardenEncounter", violetWardenRoll)',
    '!violetWardenRoll.optBoolean("success", false)',
    'case "violet_warden":',
    'case "violet_warden": name = "The Violet Warden"; break;',
    '"violet_warden".equals(entityKey) ? "Violet.png"',
    "'scp_173','violet_warden']",
    'JSONObject violetWarden = rolls.optJSONObject("violetWardenEncounter")',
    'entityKey = "violet_warden";',
):
    if marker not in main:
        raise RuntimeError("Violet Warden encounter/overlay contract missing: " + marker)
MAIN.write_text(main, encoding="utf-8")


# ---------------------------------------------------------------------------
# Regression coverage.
# ---------------------------------------------------------------------------
test = TEST.read_text(encoding="utf-8")
if 'violetWardenStartsAtExactlyTenPercentMoreHpThanCurrentDiepMinh' not in test:
    tests = r'''
  @Test fun violetWardenStartsAtExactlyTenPercentMoreHpThanCurrentDiepMinh() {
    val diep = CombatRuntime.active(CombatRuntime.start(GameState.initial(), "diep_minh"))!!
    val violet = CombatRuntime.active(CombatRuntime.start(GameState.initial(), "violet_warden"))!!
    assertEquals(3199, diep.entityMaxHp)
    assertEquals(3519, violet.entityMaxHp)
    assertEquals(diep.entityMaxHp * 110 / 100, violet.entityMaxHp)
  }

  @Test fun violetWardenProjectsDuelBlockAndFormerHumanIdentity() {
    val state = CombatRuntime.start(GameState.initial(), "violet_warden")
    val json = CombatRuntime.toJson(state)!!
    assertEquals("Control / Single Target / Counter", json.getString("combatRole"))
    assertEquals("Violet Judgment", json.getString("weapon"))
    assertEquals(60, json.getInt("blockPercent"))
    assertEquals(30, json.getInt("blockReductionPercent"))
    assertEquals("15th century", json.getString("originEra"))
  }

  @Test fun violetWardenUsesOneDuelTargetInsteadOfPartySizedDirectActions() {
    var state = CombatRuntime.start(GameState.initial(), "violet_warden")
    val result = CombatRuntime.resolve(state, "OTHER", "giữ vị trí")
    assertTrue(result.reply, result.reply.contains("Duelist's Decree") || result.reply.contains("The Violet Warden"))
    assertFalse(result.reply, result.reply.contains("ENTITY ACTION BUDGET: The Violet Warden"))
  }
'''
    close = test.rfind('\n}')
    if close < 0:
        raise RuntimeError("Violet Warden CombatRuntimeTest closing brace missing")
    test = test[:close] + "\n" + tests.rstrip() + test[close:]
TEST.write_text(test, encoding="utf-8")

if not ASSET.is_file() or ASSET.stat().st_size <= 0:
    raise RuntimeError("Violet Warden asset missing: android-apk/app/src/main/assets/entity/Violet.png")
raw = ASSET.read_bytes()
if raw[:8] != b'\x89PNG\r\n\x1a\n':
    raise RuntimeError("Violet.png is not a valid PNG asset")

print("The Violet Warden installed: 3519 effective HP, 10% all-Level independent encounter, Control/Single-Target duel AI, 60% Block, Violet.png asset.")
