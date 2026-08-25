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


# ---------------------------------------------------------------------------
# Entity action economy finalizer.
#
# One player Party command is still one CombatRuntime turn. The Entity now gets
# one direct action for every ACTIVE/living combat-capable Party member present
# at the start of its response, and direct actions are distributed one-per-
# target without repeats. An Nhien remains a protected NON-COMBAT support and
# never creates an action slot or a direct-attack target.
#
# Existing special mechanics remain authoritative:
# - Entity Stun still suppresses the response it already suppressed.
# - Diep Minh's Devils And Gold remains one Party-wide ultimate response.
# - SCP-173 remains unable to attack while OBSERVED. When UNOBSERVED, its first
#   target uses the existing special priority and remaining combatants receive
#   one Snap Strike each so its action budget also scales with the Party.
# - Monster X / John Doe status schedules remain once per combat turn, not once
#   per action slot.
# ---------------------------------------------------------------------------
combat = COMBAT.read_text(encoding="utf-8")

helper_anchor = '  private fun scp173LivePartyIds(state: GameState): List<String> =\n'
helper = r'''  // ENTITY_PARTY_ACTION_BUDGET_V1: direct Entity targets only.
  private fun entityCombatActionTargets(state: GameState): List<String> =
    state.party.memberIds.distinct().filter { characterId ->
      val character = state.characters[characterId]
      character != null &&
        character.presence == CharacterPresence.ACTIVE &&
        character.vitalState.currentHp > 0 &&
        characterId != AN_NHIEN_ID &&
        !character.statProfile.combatRole.uppercase().contains("NON-COMBAT")
    }

'''
if 'private fun entityCombatActionTargets(' not in combat:
    combat = replace_once(combat, helper_anchor, helper + helper_anchor, "Entity combat target helper")

# SCP-173 must not select the non-combat support as its direct target when Kai is
# absent. Observation can still include every living observer; only direct
# attack targeting uses the combat-capable roster.
scp_target_old = '''  private fun scp173TargetId(state: GameState): String? {
    val live = scp173LivePartyIds(state)
    return if (KAI_ID in live) KAI_ID else live.firstOrNull()
  }
'''
scp_target_new = '''  private fun scp173TargetId(state: GameState): String? {
    val live = entityCombatActionTargets(state)
    return if (KAI_ID in live) KAI_ID else live.firstOrNull()
  }
'''
combat = replace_once(combat, scp_target_old, scp_target_new, "SCP-173 combat-capable target selection")

# Capture the UNOBSERVED target roster before the primary strike. A primary
# execution must not shrink the number of action slots that were already earned
# for this Entity response.
scp_roster_old = '        val targetId = scp173TargetId(resolvedState)\n'
scp_roster_new = '''        val scp173ActionTargets = entityCombatActionTargets(resolvedState)
        val targetId = scp173TargetId(resolvedState)
'''
combat = replace_once(combat, scp_roster_old, scp_roster_new, "SCP-173 action roster capture")

scp_tail_old = '''            log += "Snap Strike: ${target.name} -$damage HP ($totalPercent% Max HP); ${if (stunned) "Stun 1 lượt (${SCP_173_SNAP_STRIKE_STUN_PERCENT}% proc)" else "Stun không proc"}."
          }
        }
      }
    } else if (c.entityKey == JOHN_DOE_KEY) {
'''
scp_tail_new = '''            log += "Snap Strike: ${target.name} -$damage HP ($totalPercent% Max HP); ${if (stunned) "Stun 1 lượt (${SCP_173_SNAP_STRIKE_STUN_PERCENT}% proc)" else "Stun không proc"}."
          }

          log += "ENTITY ACTION 1/${scp173ActionTargets.size} -> ${target.name}: SCP-173 primary UNOBSERVED action resolved."
          val scp173ExtraTargets = scp173ActionTargets.filter { it != targetId }
          scp173ExtraTargets.forEachIndexed { extraIndex, extraTargetId ->
            val extraTarget = resolvedState.characters[extraTargetId] ?: return@forEachIndexed
            val extraMaxHp = CharacterStatEngine.effective(resolvedState, extraTargetId).maxHp
            val extraBefore = extraTarget.vitalState.currentHp.coerceIn(0, extraMaxHp)
            if (extraBefore <= 0) return@forEachIndexed
            val extraDamage = min(extraBefore, percentDamage(extraMaxHp, SCP_173_SNAP_STRIKE_PERCENT))
            resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, extraTargetId, extraBefore - extraDamage)
            val extraAfter = resolvedState.characters[extraTargetId]?.vitalState?.currentHp ?: max(0, extraBefore - extraDamage)
            var extraStunned = false
            if (extraAfter > 0 && roll(c.copy(eventCounter = c.eventCounter + 823 + extraIndex * 19), 100) < SCP_173_SNAP_STRIKE_STUN_PERCENT) {
              resolvedState = scp173ApplyTransientStatus(resolvedState, extraTargetId, "STUN", c.eventCounter + 1)
              extraStunned = true
            }
            log += "ENTITY ACTION ${extraIndex + 2}/${scp173ActionTargets.size} -> ${extraTarget.name}: HIT. SCP-173 Snap Strike -$extraDamage HP (${SCP_173_SNAP_STRIKE_PERCENT}% Max HP); ${if (extraStunned) "Stun 1 lượt" else "Stun không proc"}."
          }
          log += "ENTITY ACTION BUDGET: SCP-173 UNOBSERVED = ${scp173ActionTargets.size}; mỗi combatant nhận tối đa một direct action trong Entity turn."
        }
      }
    } else if (c.entityKey == JOHN_DOE_KEY) {
'''
combat = replace_once(combat, scp_tail_old, scp_tail_new, "SCP-173 multi-target UNOBSERVED actions")

# Insert a party-sized direct-response branch before John Doe's old single-Kai
# branch. The historical branch is deliberately retained but becomes unreachable
# for all non-SCP direct responses; this keeps older patch anchors intact while
# the new final authority owns target distribution.
john_anchor = '    } else if (c.entityKey == JOHN_DOE_KEY) {\n'
multi_branch = r'''    } else if (c.entityKey != SCP_173_KEY &&
        !(c.entityKey == DIEP_MINH_KEY && c.eventCounter % DIEP_MINH_ULTIMATE_INTERVAL_TURNS == 0)) {
      val entityTargets = entityCombatActionTargets(resolvedState)
      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityTargets.size}; one direct action per ACTIVE combatant, no repeated target."
      val partyDefense = when (intent) { Intent.EVADE -> 34; Intent.GUARD -> 30; Intent.MOVE -> 18; Intent.READ -> 12; else -> 0 } +
        when (c.cover) { Cover.HARD -> 22; Cover.PARTIAL -> 10; Cover.EXPOSED -> 0 } + max(0, c.momentum) * 4
      var landedActions = 0
      var missedIris = false
      var missedSyvial = false

      entityTargets.forEachIndexed { actionIndex, targetId ->
        val target = resolvedState.characters[targetId] ?: return@forEachIndexed
        val targetMaxHp = CharacterStatEngine.effective(resolvedState, targetId).maxHp
        val before = target.vitalState.currentHp.coerceIn(0, targetMaxHp)
        if (before <= 0) return@forEachIndexed

        val personalEvasion = when {
          targetId == KAI_ID -> {
            val quickStep = if (quickStepTurns > 0) KAI_QUICK_STEP_EVASION_BONUS_PERCENT else 0
            quickStep + DevilTriggerPassive.evasionBonus(kaiDevilTriggerActive)
          }
          targetId == SYVIAL_ID && syvialDevilTrigger -> 20
          else -> 0
        }
        val enemyChance = (profile.aggression * 8 - partyDefense + max(0, -c.momentum) * 7 -
          companionEnemyAccuracyPenalty - personalEvasion).coerceIn(0, 88)
        val incomingRoll = roll(c.copy(eventCounter = c.eventCounter + 31 + actionIndex * 53), 100)

        if (incomingRoll < enemyChance) {
          val requestedDamage = when (c.entityKey) {
            DIEP_MINH_KEY -> percentDamage(targetMaxHp, DIEP_MINH_ATTACK_PERCENT)
            MONSTER_X_KEY -> percentDamage(targetMaxHp, MONSTER_X_ATTACK_PERCENT)
            JOHN_DOE_KEY -> percentDamage(targetMaxHp, JOHN_DOE_ATTACK_PERCENT)
            else -> {
              val baseMonsterDamage = max(1, profile.attack + roll(c.copy(eventCounter = c.eventCounter + 47 + actionIndex * 59), 7) -
                when (c.cover) { Cover.HARD -> 8; Cover.PARTIAL -> 4; Cover.EXPOSED -> 0 })
              if (c.entityMaxHp < 1000) max(1, (baseMonsterDamage * 110 + 99) / 100) else baseMonsterDamage
            }
          }
          val damage = min(before, requestedDamage)
          resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, targetId, before - damage)
          val after = resolvedState.characters[targetId]?.vitalState?.currentHp ?: max(0, before - damage)
          landedActions += 1

          val legacyHit = when {
            targetId == KAI_ID && c.entityKey == DIEP_MINH_KEY ->
              "Diệp Minh phản công: Kai -$damage HP (${DIEP_MINH_ATTACK_PERCENT}% Max HP; $after/$targetMaxHp)."
            targetId == KAI_ID && c.entityKey == MONSTER_X_KEY ->
              "Monster X tấn công: Kai -$damage HP (${MONSTER_X_ATTACK_PERCENT}% Max HP; $after/$targetMaxHp)."
            targetId == KAI_ID && c.entityKey == JOHN_DOE_KEY ->
              "John Doe tấn công: Kai -$damage HP (${JOHN_DOE_ATTACK_PERCENT}% Max HP; $after/$targetMaxHp)."
            targetId == KAI_ID ->
              "${c.entityName} phản công: Kai -$damage HP ($after/$targetMaxHp)."
            c.entityKey == DIEP_MINH_KEY ->
              "Diệp Minh tấn công ${target.name}: -$damage HP (${DIEP_MINH_ATTACK_PERCENT}% Max HP; $after/$targetMaxHp)."
            c.entityKey == MONSTER_X_KEY ->
              "Monster X tấn công ${target.name}: -$damage HP (${MONSTER_X_ATTACK_PERCENT}% Max HP; $after/$targetMaxHp)."
            c.entityKey == JOHN_DOE_KEY ->
              "John Doe tấn công ${target.name}: -$damage HP (${JOHN_DOE_ATTACK_PERCENT}% Max HP; $after/$targetMaxHp)."
            else -> "${c.entityName} tấn công ${target.name}: -$damage HP ($after/$targetMaxHp)."
          }
          log += "ENTITY ACTION ${actionIndex + 1}/${entityTargets.size} -> ${target.name}: HIT. $legacyHit"
        } else {
          if (targetId == IRIS_ID) missedIris = true
          if (targetId == SYVIAL_ID) missedSyvial = true
          val missDetail = when {
            targetId == KAI_ID && quickStepTurns > 0 ->
              "Quick Step khiến ${c.entityName} hụt đòn; +${KAI_QUICK_STEP_EVASION_BONUS_PERCENT}% Evasion đang hoạt động."
            targetId == KAI_ID -> "${c.entityName} không xuyên được thế phòng thủ/di chuyển của Kai."
            else -> "${c.entityName} hụt direct action vào ${target.name}."
          }
          log += "ENTITY ACTION ${actionIndex + 1}/${entityTargets.size} -> ${target.name}: MISS. $missDetail"
        }
      }

      if (landedActions > 0) c = c.copy(momentum = max(-3, c.momentum - 1))
      val kaiMaxHpAfter = CharacterStatEngine.effective(resolvedState, KAI_ID).maxHp
      val kaiHpAfter = resolvedState.characters[KAI_ID]?.vitalState?.currentHp?.coerceIn(0, kaiMaxHpAfter) ?: c.playerHp
      c = c.copy(playerHp = kaiHpAfter, playerMaxHp = kaiMaxHpAfter)

      // Counters are now personal: each companion may counter only if the direct
      // action aimed at that companion missed. They still trigger at most once
      // each per Entity turn and do not create extra Entity actions.
      if (missedIris && irisActive && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 281), 100) < 15) {
        val damage = companionSkillDamage(CharacterStatEngine.weaponDamage(resolvedState, IRIS_ID), 120, profile.armor)
        val hp = max(0, c.entityHp - damage)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
        log += "Dead Angle: Iris phản kích tức thời 120% DMG = -$damage HP."
      }
      if (missedSyvial && syvialActive && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 293), 100) < 30) {
        val baseDamage = companionSkillDamage(CharacterStatEngine.weaponDamage(resolvedState, SYVIAL_ID), 125, profile.armor)
        val damage = DevilTriggerPassive.damage(baseDamage, syvialDevilTrigger)
        val hp = max(0, c.entityHp - damage)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
        log += "Counterphase: Syvial Spatial Shift vào góc chết và phản chém -$damage HP."
      }
'''
if 'ENTITY_PARTY_ACTION_BUDGET_V1' in combat and 'ENTITY ACTION BUDGET: ${c.entityName}' not in combat:
    combat = replace_once(combat, john_anchor, multi_branch + john_anchor, "party-sized Entity response branch")

COMBAT.write_text(combat, encoding="utf-8")


# ---------------------------------------------------------------------------
# Regression coverage. Tests use response narration as the stable action ledger
# and CharacterVitalState as the authoritative damage source.
# ---------------------------------------------------------------------------
test = TEST.read_text(encoding="utf-8")
if 'entityActionBudgetTargetsEachCombatantOnceAndExcludesAnNhien' not in test:
    tests = r'''
  @Test fun entityActionBudgetTargetsEachCombatantOnceAndExcludesAnNhien() {
    val initial = GameState.initial()
    val iris = CharacterState(
      id = IRIS_ID, name = "Iris",
      statProfile = CharacterStatProfiles.forId(IRIS_ID),
      vitalState = CharacterStatProfiles.initialVitals(IRIS_ID)
    )
    val lucia = CharacterState(
      id = LUCIA_ID, name = "Lucia",
      statProfile = CharacterStatProfiles.forId(LUCIA_ID),
      vitalState = CharacterStatProfiles.initialVitals(LUCIA_ID)
    )
    val anNhien = CharacterState(
      id = AN_NHIEN_ID, name = "An Nhiên",
      statProfile = CharacterStatProfiles.forId(AN_NHIEN_ID),
      vitalState = CharacterStatProfiles.initialVitals(AN_NHIEN_ID)
    )
    var state = initial.copy(
      characters = initial.characters + mapOf(IRIS_ID to iris, LUCIA_ID to lucia, AN_NHIEN_ID to anNhien),
      party = PartyState(memberIds = listOf(KAI_ID, IRIS_ID, LUCIA_ID, AN_NHIEN_ID), maxMembers = 4)
    )
    state = CombatRuntime.start(state, "slenderman")
    val result = CombatRuntime.resolve(state, "OTHER", "...")

    assertTrue(result.reply, result.reply.contains("ENTITY ACTION BUDGET: Slenderman = 3"))
    assertEquals(1, result.reply.split("-> Kai Akechi:").size - 1)
    assertEquals(1, result.reply.split("-> Iris:").size - 1)
    assertEquals(1, result.reply.split("-> Lucia:").size - 1)
    assertFalse(result.reply, result.reply.contains("-> An Nhiên:"))
  }

  @Test fun entityActionBudgetSkipsDefeatedCombatantWithoutRetargetingSomeoneTwice() {
    val initial = GameState.initial()
    val iris = CharacterState(
      id = IRIS_ID, name = "Iris",
      statProfile = CharacterStatProfiles.forId(IRIS_ID),
      vitalState = CharacterVitalState(currentHp = 0, condition = CharacterCondition.DEFEATED)
    )
    val lucia = CharacterState(
      id = LUCIA_ID, name = "Lucia",
      statProfile = CharacterStatProfiles.forId(LUCIA_ID),
      vitalState = CharacterStatProfiles.initialVitals(LUCIA_ID)
    )
    var state = initial.copy(
      characters = initial.characters + mapOf(IRIS_ID to iris, LUCIA_ID to lucia),
      party = PartyState(memberIds = listOf(KAI_ID, IRIS_ID, LUCIA_ID))
    )
    state = CombatRuntime.start(state, "slenderman")
    val result = CombatRuntime.resolve(state, "OTHER", "...")

    assertTrue(result.reply, result.reply.contains("ENTITY ACTION BUDGET: Slenderman = 2"))
    assertEquals(1, result.reply.split("-> Kai Akechi:").size - 1)
    assertEquals(1, result.reply.split("-> Lucia:").size - 1)
    assertFalse(result.reply, result.reply.contains("-> Iris:"))
  }

  @Test fun entityDirectActionWritesDamageToCompanionVitalState() {
    var verified = false
    for (counter in 0..120) {
      if (verified) break
      val initial = GameState.initial()
      val lucia = CharacterState(
        id = LUCIA_ID, name = "Lucia",
        statProfile = CharacterStatProfiles.forId(LUCIA_ID),
        vitalState = CharacterStatProfiles.initialVitals(LUCIA_ID)
      )
      var state = initial.copy(
        characters = initial.characters + (LUCIA_ID to lucia),
        party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID))
      )
      state = CombatRuntime.start(state, "slenderman")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val before = state.characters.getValue(LUCIA_ID).vitalState.currentHp
      val result = CombatRuntime.resolve(state, "OTHER", "...")
      if (!result.reply.contains("-> Lucia: HIT")) continue
      assertTrue(result.state.characters.getValue(LUCIA_ID).vitalState.currentHp < before)
      verified = true
    }
    assertTrue("Expected deterministic search to find a landed Entity direct action on Lucia", verified)
  }

  @Test fun scp173UnobservedDistributesDirectActionsAcrossCombatants() {
    val initial = GameState.initial()
    val blindKai = initial.characters.getValue(KAI_ID).copy(metadata = mapOf("blind" to "true"))
    val iris = CharacterState(
      id = IRIS_ID, name = "Iris",
      metadata = mapOf("blind" to "true"),
      statProfile = CharacterStatProfiles.forId(IRIS_ID),
      vitalState = CharacterStatProfiles.initialVitals(IRIS_ID)
    )
    var state = initial.copy(
      characters = initial.characters + mapOf(KAI_ID to blindKai, IRIS_ID to iris),
      party = PartyState(memberIds = listOf(KAI_ID, IRIS_ID))
    )
    state = CombatRuntime.start(state, "scp_173")
    assertEquals("UNOBSERVED", CombatRuntime.toJson(state)!!.getString("observationState"))
    val irisBefore = state.characters.getValue(IRIS_ID).vitalState.currentHp
    val result = CombatRuntime.resolve(state, "OTHER", "...")

    assertTrue(result.reply, result.reply.contains("ENTITY ACTION BUDGET: SCP-173 UNOBSERVED = 2"))
    assertTrue(result.reply, result.reply.contains("ENTITY ACTION 1/2 -> Kai Akechi"))
    assertTrue(result.reply, result.reply.contains("ENTITY ACTION 2/2 -> Iris: HIT"))
    assertTrue(result.state.characters.getValue(IRIS_ID).vitalState.currentHp < irisBefore)
  }
'''
    close = test.rfind('\n}')
    if close < 0:
        raise RuntimeError("CombatRuntimeTest closing brace missing")
    test = test[:close] + tests + test[close:]
    TEST.write_text(test, encoding="utf-8")

combined = COMBAT.read_text(encoding="utf-8") + "\n" + TEST.read_text(encoding="utf-8")
for marker in (
    'ENTITY_PARTY_ACTION_BUDGET_V1',
    'private fun entityCombatActionTargets(state: GameState): List<String>',
    'characterId != AN_NHIEN_ID',
    'ENTITY ACTION BUDGET: ${c.entityName}',
    'one direct action per ACTIVE combatant, no repeated target',
    'targetId == SYVIAL_ID && syvialDevilTrigger -> 20',
    'ENTITY ACTION BUDGET: SCP-173 UNOBSERVED',
    'entityActionBudgetTargetsEachCombatantOnceAndExcludesAnNhien',
    'entityActionBudgetSkipsDefeatedCombatantWithoutRetargetingSomeoneTwice',
    'entityDirectActionWritesDamageToCompanionVitalState',
    'scp173UnobservedDistributesDirectActionsAcrossCombatants',
):
    if marker not in combined:
        raise RuntimeError("Entity action-budget contract missing: " + marker)

print("Entity action budget installed: one direct action per ACTIVE combat-capable Party member, unique targets, An Nhien excluded, SCP-173 UNOBSERVED distributed across combatants.")
