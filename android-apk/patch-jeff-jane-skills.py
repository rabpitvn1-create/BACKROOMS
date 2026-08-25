from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
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


combat = COMBAT.read_text(encoding="utf-8")

# Final authority for Jeff/Jane combat stats and skills. This intentionally runs
# after the Entity party-action budget so it extends the finalized response path
# instead of replacing any existing Entity, companion, boss, or SCP mechanics.
constants_anchor = '  private const val ENTITY_REGEN_PER_TURN = 1\n'
constants_block = '''  private const val ENTITY_REGEN_PER_TURN = 1
  private const val JEFF_KEY = "jeff_the_killer"
  private const val JANE_KEY = "jane_the_killer"
  private const val UNIQUE_KILLER_MAX_HP = 947

  private const val JEFF_GO_TO_SLEEP_NORMAL_PERCENT = 12
  private const val JEFF_GO_TO_SLEEP_LOW_HP_PERCENT = 17
  private const val JEFF_GO_TO_SLEEP_COOLDOWN = 3
  private const val JEFF_SILENT_STALKER_DAMAGE_PERCENT = 140
  private const val JEFF_SILENT_STALKER_SOLO_ACCURACY_BONUS = 10
  private const val JEFF_SILENT_STALKER_COOLDOWN = 5
  private const val JEFF_NO_SAFE_ROUTE_ESCAPE_PENALTY = 20
  private const val JEFF_NO_SAFE_ROUTE_DURATION_TURNS = 3
  private const val JEFF_NO_SAFE_ROUTE_RETALIATION_PERCENT = 7
  private const val JEFF_NO_SAFE_ROUTE_COOLDOWN = 7

  private const val JANE_DONT_WAKE_UP_HIT_PERCENT = 6
  private const val JANE_DONT_WAKE_UP_SECOND_HIT_ACCURACY = 70
  private const val JANE_DONT_WAKE_UP_COOLDOWN = 3
  private const val JANE_BLEED_PERCENT = 2
  private const val JANE_BLEED_TURNS = 2
  private const val JANE_HUNTER_MARK_ACCURACY_BONUS = 15
  private const val JANE_HUNTER_MARK_ESCAPE_PENALTY = 15
  private const val JANE_HUNTER_MARK_DURATION_TURNS = 4
  private const val JANE_HUNTER_MARK_COOLDOWN = 6
  private const val JANE_VENGEFUL_TRIGGER_PERCENT = 20
  private const val JANE_VENGEFUL_PROC_PERCENT = 35
  private const val JANE_VENGEFUL_DAMAGE_PERCENT = 8
  private const val JANE_VENGEFUL_COOLDOWN = 4

  private const val JEFF_NO_SAFE_UNTIL_KEY = "combat.jeff.noSafeRouteUntil"
  private const val JEFF_NO_SAFE_RETALIATE_TURN_KEY = "combat.jeff.noSafeRouteRetaliateTurn"
  private const val JANE_MARK_TARGET_KEY = "combat.jane.hunterMarkTarget"
  private const val JANE_MARK_UNTIL_KEY = "combat.jane.hunterMarkUntil"
'''
combat = replace_once(combat, constants_anchor, constants_block, "Jeff/Jane skill constants")

profiles_old = '''    Profile("jeff_the_killer", "Jeff the Killer", 120, 20, 4, 9),
    Profile("jane_the_killer", "Jane the Killer", 120, 20, 4, 9),
'''
profiles_new = '''    Profile("jeff_the_killer", "Jeff the Killer", UNIQUE_KILLER_MAX_HP, 20, 4, 9),
    Profile("jane_the_killer", "Jane the Killer", UNIQUE_KILLER_MAX_HP, 20, 4, 9),
'''
combat = replace_once(combat, profiles_old, profiles_new, "Jeff/Jane canonical 947 HP profiles")

# v1.1.71 adds +30 HP to normal Entities. Jeff/Jane are explicit overrides at
# exactly 947 Max HP, so they bypass the generic bonus in both new encounters and
# active-save migration.
hp_old = '    val balancedEntityBaseHp = when (profile.key) { DIEP_MINH_KEY -> DIEP_MINH_MAX_HP; MONSTER_X_KEY -> MONSTER_X_MAX_HP; JOHN_DOE_KEY -> JOHN_DOE_MAX_HP; SCP_173_KEY -> SCP_173_MAX_HP; else -> profile.maxHp + ENTITY_HP_BONUS }\n'
hp_new = '    val balancedEntityBaseHp = when (profile.key) { DIEP_MINH_KEY -> DIEP_MINH_MAX_HP; MONSTER_X_KEY -> MONSTER_X_MAX_HP; JOHN_DOE_KEY -> JOHN_DOE_MAX_HP; SCP_173_KEY -> SCP_173_MAX_HP; JEFF_KEY, JANE_KEY -> profile.maxHp; else -> profile.maxHp + ENTITY_HP_BONUS }\n'
hp_count = combat.count(hp_old)
if hp_count != 2 and hp_new not in combat:
    raise RuntimeError(f"Jeff/Jane 947 HP balance: expected 2 start/decode anchors, found {hp_count}")
if hp_new not in combat:
    combat = combat.replace(hp_old, hp_new)

helper_anchor = '  // ENTITY_PARTY_ACTION_BUDGET_V1: direct Entity targets only.\n'
helpers = r'''  // JEFF_JANE_SKILLS_V1: save-persistent cooldown/status helpers.
  private fun killerCooldownKey(skill: String): String = "${PREFIX}killer.$skill.nextReadyTurn"

  private fun killerSkillReady(state: GameState, skill: String, eventCounter: Int): Boolean =
    eventCounter >= (state.metadata[killerCooldownKey(skill)]?.toIntOrNull() ?: 0)

  private fun useKillerSkill(state: GameState, skill: String, eventCounter: Int, cooldownTurns: Int): GameState =
    withCombatCounter(state, killerCooldownKey(skill), eventCounter + cooldownTurns + 1)

  private fun withCombatText(state: GameState, key: String, value: String): GameState {
    val metadata = state.metadata.toMutableMap()
    if (value.isBlank()) metadata.remove(key) else metadata[key] = value
    return state.copy(metadata = metadata)
  }

  private fun janeBleedKey(characterId: String): String = "${PREFIX}jane.bleed.$characterId"

'''
if 'JEFF_JANE_SKILLS_V1' not in combat:
    combat = replace_once(combat, helper_anchor, helpers + helper_anchor, "Jeff/Jane skill helpers")

escape_old = '''      Intent.ESCAPE -> {
        log += "PARTY ACTION BỎ CHẠY: ${activePartyNames(resolvedState)} cùng rút khỏi encounter trong một combat turn."
        val gain = 20 + c.momentum.coerceAtLeast(0) * 5 + when (c.cover) { Cover.HARD -> 15; Cover.PARTIAL -> 8; Cover.EXPOSED -> 0 }
        c = c.copy(escapeProgress = min(100, c.escapeProgress + gain), momentum = min(3, c.momentum + 1))
        log += "Kai dồn ưu thế vào đường thoát (${c.escapeProgress}%)."
      }
'''
escape_new = '''      Intent.ESCAPE -> {
        log += "PARTY ACTION BỎ CHẠY: ${activePartyNames(resolvedState)} cùng rút khỏi encounter trong một combat turn."
        val baseGain = 20 + c.momentum.coerceAtLeast(0) * 5 + when (c.cover) { Cover.HARD -> 15; Cover.PARTIAL -> 8; Cover.EXPOSED -> 0 }
        var escapePenalty = 0

        if (c.entityKey == JEFF_KEY) {
          val activeUntil = resolvedState.metadata[JEFF_NO_SAFE_UNTIL_KEY]?.toIntOrNull() ?: -1
          var noSafeRouteActive = activeUntil >= c.eventCounter
          if (!noSafeRouteActive && killerSkillReady(resolvedState, "jeff.no_safe_route", c.eventCounter)) {
            noSafeRouteActive = true
            resolvedState = withCombatCounter(resolvedState, JEFF_NO_SAFE_UNTIL_KEY, c.eventCounter + JEFF_NO_SAFE_ROUTE_DURATION_TURNS - 1)
            resolvedState = useKillerSkill(resolvedState, "jeff.no_safe_route", c.eventCounter, JEFF_NO_SAFE_ROUTE_COOLDOWN)
            log += "No Safe Route: Jeff khóa tuyến rút trong $JEFF_NO_SAFE_ROUTE_DURATION_TURNS turn; -$JEFF_NO_SAFE_ROUTE_ESCAPE_PENALTY điểm Escape."
          }
          if (noSafeRouteActive) escapePenalty += JEFF_NO_SAFE_ROUTE_ESCAPE_PENALTY
        }

        if (c.entityKey == JANE_KEY) {
          val markTarget = resolvedState.metadata[JANE_MARK_TARGET_KEY].orEmpty()
          val markUntil = resolvedState.metadata[JANE_MARK_UNTIL_KEY]?.toIntOrNull() ?: -1
          if (markTarget == KAI_ID && markUntil >= c.eventCounter) {
            escapePenalty += JANE_HUNTER_MARK_ESCAPE_PENALTY
            log += "Hunter's Mark: Kai chịu -$JANE_HUNTER_MARK_ESCAPE_PENALTY điểm Escape."
          }
        }

        val gain = max(0, baseGain - escapePenalty)
        c = c.copy(escapeProgress = min(100, c.escapeProgress + gain), momentum = min(3, c.momentum + 1))
        if (c.entityKey == JEFF_KEY && escapePenalty >= JEFF_NO_SAFE_ROUTE_ESCAPE_PENALTY && c.escapeProgress < 100) {
          resolvedState = withCombatCounter(resolvedState, JEFF_NO_SAFE_RETALIATE_TURN_KEY, c.eventCounter)
        }
        log += "Kai dồn ưu thế vào đường thoát (${c.escapeProgress}%)."
      }
'''
combat = replace_once(combat, escape_old, escape_new, "Jeff No Safe Route and Jane Hunter Mark escape penalties")

response_start_old = '''      val entityTargets = entityCombatActionTargets(resolvedState)
      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityTargets.size}; one direct action per ACTIVE combatant, no repeated target."
'''
response_start_new = r'''      val entityTargets = entityCombatActionTargets(resolvedState)

      // Jane bleed ticks once per combat turn before her direct actions.
      if (c.entityKey == JANE_KEY) {
        entityTargets.forEach { bleedTargetId ->
          val turns = resolvedState.metadata[janeBleedKey(bleedTargetId)]?.toIntOrNull()?.coerceIn(0, JANE_BLEED_TURNS) ?: 0
          if (turns > 0) {
            val target = resolvedState.characters[bleedTargetId] ?: return@forEach
            val targetMaxHp = CharacterStatEngine.effective(resolvedState, bleedTargetId).maxHp
            val beforeBleed = target.vitalState.currentHp.coerceIn(0, targetMaxHp)
            if (beforeBleed > 0) {
              val bleedDamage = min(beforeBleed, percentDamage(targetMaxHp, JANE_BLEED_PERCENT))
              resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, bleedTargetId, beforeBleed - bleedDamage)
              resolvedState = withCombatCounter(resolvedState, janeBleedKey(bleedTargetId), turns - 1)
              val afterBleed = resolvedState.characters[bleedTargetId]?.vitalState?.currentHp ?: max(0, beforeBleed - bleedDamage)
              log += "Bleed: ${target.name} -$bleedDamage HP ($JANE_BLEED_PERCENT% Max HP); còn ${turns - 1} turn ($afterBleed/$targetMaxHp)."
            } else {
              resolvedState = withCombatCounter(resolvedState, janeBleedKey(bleedTargetId), 0)
            }
          }
        }
      }

      // No Safe Route punishes an escape attempt only when the reduced escape did not resolve.
      if (c.entityKey == JEFF_KEY &&
          resolvedState.metadata[JEFF_NO_SAFE_RETALIATE_TURN_KEY]?.toIntOrNull() == c.eventCounter) {
        val targetId = if (KAI_ID in entityTargets) KAI_ID else entityTargets.firstOrNull()
        if (targetId != null) {
          val target = resolvedState.characters[targetId]
          if (target != null) {
            val targetMaxHp = CharacterStatEngine.effective(resolvedState, targetId).maxHp
            val beforeRetaliation = target.vitalState.currentHp.coerceIn(0, targetMaxHp)
            if (beforeRetaliation > 0) {
              val damage = min(beforeRetaliation, percentDamage(targetMaxHp, JEFF_NO_SAFE_ROUTE_RETALIATION_PERCENT))
              resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, targetId, beforeRetaliation - damage)
              val afterRetaliation = resolvedState.characters[targetId]?.vitalState?.currentHp ?: max(0, beforeRetaliation - damage)
              log += "No Safe Route retaliation: ${target.name} -$damage HP ($JEFF_NO_SAFE_ROUTE_RETALIATION_PERCENT% Max HP; $afterRetaliation/$targetMaxHp)."
            }
          }
        }
        resolvedState = withCombatCounter(resolvedState, JEFF_NO_SAFE_RETALIATE_TURN_KEY, 0)
      }

      var hunterMarkTargetId = resolvedState.metadata[JANE_MARK_TARGET_KEY].orEmpty()
      var hunterMarkUntil = resolvedState.metadata[JANE_MARK_UNTIL_KEY]?.toIntOrNull() ?: -1
      if (c.entityKey == JANE_KEY &&
          hunterMarkUntil < c.eventCounter &&
          killerSkillReady(resolvedState, "jane.hunters_mark", c.eventCounter)) {
        hunterMarkTargetId = if (KAI_ID in entityTargets) KAI_ID else entityTargets.firstOrNull().orEmpty()
        if (hunterMarkTargetId.isNotBlank()) {
          hunterMarkUntil = c.eventCounter + JANE_HUNTER_MARK_DURATION_TURNS - 1
          resolvedState = withCombatText(resolvedState, JANE_MARK_TARGET_KEY, hunterMarkTargetId)
          resolvedState = withCombatCounter(resolvedState, JANE_MARK_UNTIL_KEY, hunterMarkUntil)
          resolvedState = useKillerSkill(resolvedState, "jane.hunters_mark", c.eventCounter, JANE_HUNTER_MARK_COOLDOWN)
          val marked = resolvedState.characters[hunterMarkTargetId]?.name ?: hunterMarkTargetId
          log += "Hunter's Mark: Jane đánh dấu $marked trong $JANE_HUNTER_MARK_DURATION_TURNS turn; +$JANE_HUNTER_MARK_ACCURACY_BONUS% Accuracy và -$JANE_HUNTER_MARK_ESCAPE_PENALTY điểm Escape."
        }
      }

      if (c.entityKey == JANE_KEY && entityTargets.isNotEmpty()) {
        val damageTakenThisTurn = max(0, current.entityHp - c.entityHp)
        val triggerThreshold = percentDamage(c.entityMaxHp, JANE_VENGEFUL_TRIGGER_PERCENT)
        if (damageTakenThisTurn >= triggerThreshold &&
            killerSkillReady(resolvedState, "jane.vengeful_reflex", c.eventCounter)) {
          resolvedState = useKillerSkill(resolvedState, "jane.vengeful_reflex", c.eventCounter, JANE_VENGEFUL_COOLDOWN)
          if (roll(c.copy(eventCounter = c.eventCounter + 947), 100) < JANE_VENGEFUL_PROC_PERCENT) {
            val targetId = if (KAI_ID in entityTargets) KAI_ID else entityTargets.first()
            val target = resolvedState.characters[targetId]
            if (target != null) {
              val targetMaxHp = CharacterStatEngine.effective(resolvedState, targetId).maxHp
              val beforeCounter = target.vitalState.currentHp.coerceIn(0, targetMaxHp)
              if (beforeCounter > 0) {
                val damage = min(beforeCounter, percentDamage(targetMaxHp, JANE_VENGEFUL_DAMAGE_PERCENT))
                resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, targetId, beforeCounter - damage)
                val afterCounter = resolvedState.characters[targetId]?.vitalState?.currentHp ?: max(0, beforeCounter - damage)
                log += "Vengeful Reflex: Jane phản kích ${target.name} -$damage HP ($JANE_VENGEFUL_DAMAGE_PERCENT% Max HP; $afterCounter/$targetMaxHp)."
              }
            }
          } else {
            log += "Vengeful Reflex: điều kiện phản kích đạt nhưng proc $JANE_VENGEFUL_PROC_PERCENT% không thành công."
          }
        }
      }

      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityTargets.size}; one direct action per ACTIVE combatant, no repeated target."
'''
combat = replace_once(combat, response_start_old, response_start_new, "Jeff/Jane pre-response skill state")

enemy_chance_old = '''        val enemyChance = (profile.aggression * 8 - partyDefense + max(0, -c.momentum) * 7 -
          companionEnemyAccuracyPenalty - personalEvasion).coerceIn(0, 88)
        val incomingRoll = roll(c.copy(eventCounter = c.eventCounter + 31 + actionIndex * 53), 100)

        if (incomingRoll < enemyChance) {
          val requestedDamage = when (c.entityKey) {
'''
enemy_chance_new = r'''        val hunterMarked = c.entityKey == JANE_KEY &&
          targetId == hunterMarkTargetId && hunterMarkUntil >= c.eventCounter
        val jeffSilentStalker = c.entityKey == JEFF_KEY && actionIndex == 0 &&
          killerSkillReady(resolvedState, "jeff.silent_stalker", c.eventCounter)
        val jeffGoToSleep = c.entityKey == JEFF_KEY && actionIndex == 0 &&
          killerSkillReady(resolvedState, "jeff.go_to_sleep", c.eventCounter)
        val janeDontWakeUp = c.entityKey == JANE_KEY && actionIndex == 0 &&
          killerSkillReady(resolvedState, "jane.dont_wake_up", c.eventCounter)

        if (jeffSilentStalker) {
          resolvedState = useKillerSkill(resolvedState, "jeff.silent_stalker", c.eventCounter, JEFF_SILENT_STALKER_COOLDOWN)
        }
        if (jeffGoToSleep) {
          resolvedState = useKillerSkill(resolvedState, "jeff.go_to_sleep", c.eventCounter, JEFF_GO_TO_SLEEP_COOLDOWN)
        }
        if (janeDontWakeUp) {
          resolvedState = useKillerSkill(resolvedState, "jane.dont_wake_up", c.eventCounter, JANE_DONT_WAKE_UP_COOLDOWN)
        }

        val killerAccuracyBonus =
          (if (hunterMarked) JANE_HUNTER_MARK_ACCURACY_BONUS else 0) +
          (if (jeffSilentStalker && entityTargets.size == 1) JEFF_SILENT_STALKER_SOLO_ACCURACY_BONUS else 0)
        val enemyChance = (profile.aggression * 8 - partyDefense + max(0, -c.momentum) * 7 -
          companionEnemyAccuracyPenalty - personalEvasion + killerAccuracyBonus).coerceIn(0, 95)
        val incomingRoll = roll(c.copy(eventCounter = c.eventCounter + 31 + actionIndex * 53), 100)

        if (incomingRoll < enemyChance) {
          var requestedDamage = when (c.entityKey) {
'''
combat = replace_once(combat, enemy_chance_old, enemy_chance_new, "Jeff/Jane skill accuracy and cooldown activation")

damage_cases_old = '''            JOHN_DOE_KEY -> percentDamage(targetMaxHp, JOHN_DOE_ATTACK_PERCENT)
            else -> {
'''
damage_cases_new = '''            JOHN_DOE_KEY -> percentDamage(targetMaxHp, JOHN_DOE_ATTACK_PERCENT)
            JEFF_KEY -> if (actionIndex == 0 && jeffGoToSleep) {
              percentDamage(targetMaxHp, if (before * 2 < targetMaxHp) JEFF_GO_TO_SLEEP_LOW_HP_PERCENT else JEFF_GO_TO_SLEEP_NORMAL_PERCENT)
            } else {
              val baseMonsterDamage = max(1, profile.attack + roll(c.copy(eventCounter = c.eventCounter + 47 + actionIndex * 59), 7) -
                when (c.cover) { Cover.HARD -> 8; Cover.PARTIAL -> 4; Cover.EXPOSED -> 0 })
              if (c.entityMaxHp < 1000) max(1, (baseMonsterDamage * 110 + 99) / 100) else baseMonsterDamage
            }
            JANE_KEY -> if (actionIndex == 0 && janeDontWakeUp) {
              percentDamage(targetMaxHp, JANE_DONT_WAKE_UP_HIT_PERCENT)
            } else {
              val baseMonsterDamage = max(1, profile.attack + roll(c.copy(eventCounter = c.eventCounter + 47 + actionIndex * 59), 7) -
                when (c.cover) { Cover.HARD -> 8; Cover.PARTIAL -> 4; Cover.EXPOSED -> 0 })
              if (c.entityMaxHp < 1000) max(1, (baseMonsterDamage * 110 + 99) / 100) else baseMonsterDamage
            }
            else -> {
'''
combat = replace_once(combat, damage_cases_old, damage_cases_new, "Jeff Go to Sleep and Jane Don't Wake Up base damage")

damage_apply_old = '''          val damage = min(before, requestedDamage)
          resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, targetId, before - damage)
'''
damage_apply_new = r'''          var killerSkillDetail = ""
          if (c.entityKey == JEFF_KEY && actionIndex == 0 && jeffGoToSleep) {
            val basePercent = if (before * 2 < targetMaxHp) JEFF_GO_TO_SLEEP_LOW_HP_PERCENT else JEFF_GO_TO_SLEEP_NORMAL_PERCENT
            killerSkillDetail = "Go to Sleep: $basePercent% Max HP"
          }
          if (c.entityKey == JEFF_KEY && actionIndex == 0 && jeffSilentStalker) {
            requestedDamage = max(1, (requestedDamage * JEFF_SILENT_STALKER_DAMAGE_PERCENT + 99) / 100)
            killerSkillDetail = (if (killerSkillDetail.isBlank()) "" else "$killerSkillDetail; ") +
              "Silent Stalker: damage x1.40${if (entityTargets.size == 1) ", +$JEFF_SILENT_STALKER_SOLO_ACCURACY_BONUS% Accuracy khi mục tiêu đi một mình" else ""}"
          }
          if (c.entityKey == JANE_KEY && actionIndex == 0 && janeDontWakeUp) {
            val secondHitChance = (JANE_DONT_WAKE_UP_SECOND_HIT_ACCURACY +
              if (hunterMarked) JANE_HUNTER_MARK_ACCURACY_BONUS else 0).coerceAtMost(95)
            val secondHit = roll(c.copy(eventCounter = c.eventCounter + 1091), 100) < secondHitChance
            if (secondHit) {
              requestedDamage += percentDamage(targetMaxHp, JANE_DONT_WAKE_UP_HIT_PERCENT)
              resolvedState = withCombatCounter(resolvedState, janeBleedKey(targetId), JANE_BLEED_TURNS)
              killerSkillDetail = "Don't Wake Up: 2 hit x $JANE_DONT_WAKE_UP_HIT_PERCENT% Max HP; Bleed $JANE_BLEED_PERCENT% Max HP/turn x $JANE_BLEED_TURNS"
            } else {
              killerSkillDetail = "Don't Wake Up: hit đầu $JANE_DONT_WAKE_UP_HIT_PERCENT% Max HP; hit hai ($secondHitChance% Accuracy) trượt"
            }
          }

          val damage = min(before, requestedDamage)
          resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, targetId, before - damage)
'''
combat = replace_once(combat, damage_apply_old, damage_apply_new, "Jeff/Jane special damage resolution")

log_anchor = '''          log += "ENTITY ACTION ${actionIndex + 1}/${entityTargets.size} -> ${target.name}: HIT. $legacyHit"
'''
log_replacement = '''          if (killerSkillDetail.isNotBlank()) log += killerSkillDetail
          log += "ENTITY ACTION ${actionIndex + 1}/${entityTargets.size} -> ${target.name}: HIT. $legacyHit"
'''
combat = replace_once(combat, log_anchor, log_replacement, "Jeff/Jane skill narration")

# Update the older durability regression so it reflects the explicit 947 HP override.
test = TEST.read_text(encoding="utf-8")
test = test.replace('"jeff_the_killer" to 150, "jane_the_killer" to 150', '"jeff_the_killer" to 947, "jane_the_killer" to 947')

new_tests = r'''
  @Test fun jeffAndJaneUseExact947MaxHp() {
    val jeff = CombatRuntime.active(CombatRuntime.start(GameState.initial(), "jeff_the_killer"))!!
    val jane = CombatRuntime.active(CombatRuntime.start(GameState.initial(), "jane_the_killer"))!!
    assertEquals(947, jeff.entityMaxHp)
    assertEquals(947, jeff.entityHp)
    assertEquals(947, jane.entityMaxHp)
    assertEquals(947, jane.entityHp)
  }

  @Test fun jeffNoSafeRouteAppliesEscapePenaltyAndFailedEscapeRetaliation() {
    val started = CombatRuntime.start(GameState.initial(), "jeff_the_killer")
    val result = CombatRuntime.resolve(started, "EXECUTE", "chạy thoát khỏi encounter")
    assertTrue(result.reply, result.reply.contains("No Safe Route"))
    val active = CombatRuntime.active(result.state)
    if (active != null) assertEquals(0, active.escapeProgress)
    assertTrue(result.reply, result.reply.contains("No Safe Route retaliation"))
  }

  @Test fun janeDontWakeUpCanApplyTwoPercentBleedForTwoTurns() {
    var verified = false
    for (counter in 0..320) {
      if (verified) break
      var state = CombatRuntime.start(GameState.initial(), "jane_the_killer")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val first = CombatRuntime.resolve(state, "OTHER", "...")
      if (first.reply.contains("Don't Wake Up: 2 hit")) {
        val second = CombatRuntime.resolve(first.state, "SEARCH", "quan sát Jane")
        assertTrue(second.reply, second.reply.contains("Bleed:"))
        assertTrue(second.reply, second.reply.contains("2% Max HP"))
        verified = true
      }
    }
    assertTrue("Jane must expose a deterministic Don't Wake Up + Bleed case", verified)
  }

  @Test fun janeVengefulReflexCanProcAfterLosingTwentyPercentMaxHpInOneTurn() {
    var verified = false
    for (counter in 0..360) {
      if (verified) break
      var state = CombatRuntime.start(GameState.initial(), "jane_the_killer")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "EXECUTE", "bắn Jane bằng Magnum")
      if (result.reply.contains("Vengeful Reflex: Jane phản kích")) verified = true
    }
    assertTrue("Jane Vengeful Reflex 35% proc must be reachable after a qualifying damage turn", verified)
  }
'''
if "jeffAndJaneUseExact947MaxHp" not in test:
    close = test.rfind("}\n")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest class closing brace missing")
    test = test[:close] + new_tests + test[close:]

for marker in (
    'Profile("jeff_the_killer", "Jeff the Killer", UNIQUE_KILLER_MAX_HP',
    'Profile("jane_the_killer", "Jane the Killer", UNIQUE_KILLER_MAX_HP',
    'private const val UNIQUE_KILLER_MAX_HP = 947',
    'JEFF_JANE_SKILLS_V1',
    'Go to Sleep:',
    'Silent Stalker:',
    'No Safe Route:',
    "Hunter's Mark:",
    "Don't Wake Up:",
    'Vengeful Reflex:',
    'JANE_BLEED_PERCENT = 2',
):
    if marker not in combat:
        raise RuntimeError("Jeff/Jane final combat contract missing: " + marker)

for marker in (
    'jeffAndJaneUseExact947MaxHp',
    'jeffNoSafeRouteAppliesEscapePenaltyAndFailedEscapeRetaliation',
    'janeDontWakeUpCanApplyTwoPercentBleedForTwoTurns',
    'janeVengefulReflexCanProcAfterLosingTwentyPercentMaxHpInOneTurn',
):
    if marker not in test:
        raise RuntimeError("Jeff/Jane regression test missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")
TEST.write_text(test, encoding="utf-8")

# Give the Game Master the same canonical numbers as the deterministic combat layer.
db = json.loads(KNOWLEDGE.read_text(encoding="utf-8"))
records = db.get("records", [])
records = [r for r in records if r.get("id") not in {"ENTITY.JEFF_SKILLS_R01", "ENTITY.JANE_SKILLS_R01"}]
records.extend([
    {
        "id": "ENTITY.JEFF_SKILLS_R01",
        "domain": "ENTITY",
        "kind": "combat-skill-lock",
        "text": "Jeff the Killer gameplay lock: Max HP 947. Go to Sleep deals 12% target Max HP, or 17% below 50% HP, cooldown 3 turns. Silent Stalker gives the next resolved direct strike x1.40 damage and +10% Accuracy when the target is alone, cooldown 5 turns. No Safe Route lasts 3 turns, applies -20 escape points, and a failed escape takes 7% Max HP retaliation; cooldown 7 turns.",
        "source": {"document": "latest explicit user instruction", "anchor": "Jeff/Jane skill rebalance"},
        "authority": "USER_OVERRIDE_ENTITY_COMBAT",
        "mutability": "IMMUTABLE",
        "priority": 24,
        "tags": ["jeff", "jeff the killer", "skills", "947 hp"]
    },
    {
        "id": "ENTITY.JANE_SKILLS_R01",
        "domain": "ENTITY",
        "kind": "combat-skill-lock",
        "text": "Jane the Killer gameplay lock: Max HP 947. Don't Wake Up has two 6% Max HP hits; second-hit base Accuracy is 70%; two hits apply Bleed 2% Max HP per turn for 2 turns; cooldown 3 turns. Hunter's Mark lasts 4 turns with +15% Jane Accuracy and -15 escape points on the marked target; cooldown 6 turns. Vengeful Reflex checks when Jane loses at least 20% Max HP in one turn, has a 35% proc, retaliates for 8% target Max HP, at most once per turn, cooldown 4 turns, and is not triggered by damage-over-time.",
        "source": {"document": "latest explicit user instruction", "anchor": "Jeff/Jane skill rebalance"},
        "authority": "USER_OVERRIDE_ENTITY_COMBAT",
        "mutability": "IMMUTABLE",
        "priority": 24,
        "tags": ["jane", "jane the killer", "skills", "bleed", "947 hp"]
    }
])
db["records"] = records
KNOWLEDGE.write_text(json.dumps(db, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

print("Jeff/Jane skills finalized: 947 HP each, percentage-based damage, Jane Bleed 2% Max HP x2.")
