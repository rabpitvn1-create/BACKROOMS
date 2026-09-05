from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatCore.kt"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Async Member finalizer {label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


combat = COMBAT.read_text(encoding="utf-8")
if "KAI_PROC_SKILLS_PATCHED" not in combat or "LUCIA_PROC_SKILLS_PATCHED" not in combat:
    raise RuntimeError("Async Member finalizer must run after Kai and Lucia combat skill layers")

# Entity-specific overrides let the requested 60/60 HP pool and fixed 25% Evasion coexist
# with the existing HP/DEFEND/AGI/CRIT stat model without lying about HP Stat 7 or AGI 9.
combat = replace_once(
    combat,
    '''  val survival: Int = 0,
  val survivalTarget: Int = CombatRules.FIRST_SURVIVAL_TARGET
) {
  val maxHp: Int get() = CombatRules.maxHp(hpStat)
  val defensePoints: Int get() = CombatRules.defensePoints(defend)
  val evasionChance: Double get() = CombatRules.evasionChance(agi)
  val criticalChance: Double get() = CombatRules.criticalChance(crit)
}''',
    '''  val survival: Int = 0,
  val survivalTarget: Int = CombatRules.FIRST_SURVIVAL_TARGET,
  val maxHpOverride: Int? = null,
  val evasionOverride: Double? = null
) {
  val maxHp: Int get() = maxHpOverride?.coerceAtLeast(1) ?: CombatRules.maxHp(hpStat)
  val defensePoints: Int get() = CombatRules.defensePoints(defend)
  val evasionChance: Double get() = evasionOverride?.coerceIn(0.0, 1.0) ?: CombatRules.evasionChance(agi)
  val criticalChance: Double get() = CombatRules.criticalChance(crit)
}''',
    "CombatStats overrides",
)

profile_constants = r'''// ASYNC_MEMBER_ENTITY_PATCHED
object AsyncMemberCombat {
  const val ENTITY_ID = "ENTITY.ASYNC_MEMBER"
  const val DISPLAY_NAME = "Async Member"
  const val BASE_HP_POOL = 60
  const val BASE_HP_STAT = 7
  const val BASE_DEFEND = 8
  const val BASE_AGI = 9
  const val BASE_CRIT = 10
  const val EVADE_CHANCE = 0.25
  const val LETS_CATCH_YOU_PROC = 0.20
  const val LETS_CATCH_YOU_DAMAGE_MULTIPLIER = 1.20
  const val LETS_CATCH_YOU_STUN_TURNS = 2

  fun letsCatchYouBaseDamage(baseDamage: Int): Int =
    (baseDamage.coerceAtLeast(0) * LETS_CATCH_YOU_DAMAGE_MULTIPLIER).roundToInt().coerceAtLeast(1)
}

'''
if "ASYNC_MEMBER_ENTITY_PATCHED" not in combat:
    marker = "object CombatProfiles {"
    if combat.count(marker) != 1:
        raise RuntimeError(f"Async Member profile insertion anchor expected once, found {combat.count(marker)}")
    combat = combat.replace(marker, profile_constants + marker, 1)

combat = replace_once(
    combat,
    '    "ENTITY.SLENDERMAN" to "Slenderman"\n',
    '    "ENTITY.SLENDERMAN" to "Slenderman",\n    AsyncMemberCombat.ENTITY_ID to AsyncMemberCombat.DISPLAY_NAME\n',
    "entity display name",
)

combat = replace_once(
    combat,
    '''  fun entity(entityId: String, kaiStats: CombatStats, level: Int): CombatantState {
    val growth = enemyGrowth(kaiStats, level)
    val stat = CombatRules.BASE_STAT + growth
    val hp = CombatRules.maxHp(stat)
    return CombatantState(
      id = entityId,
      name = entityName(entityId),
      isEntity = true,
      stats = CombatStats(
        hpStat = stat,
        defend = stat,
        agi = stat,
        crit = stat,
        currentHp = hp
      ),
      baseDamage = 8 + growth / 2
    )
  }''',
    '''  fun entity(entityId: String, kaiStats: CombatStats, level: Int): CombatantState {
    val growth = enemyGrowth(kaiStats, level)
    if (entityId.equals(AsyncMemberCombat.ENTITY_ID, ignoreCase = true)) {
      val hp = AsyncMemberCombat.BASE_HP_POOL + growth
      return CombatantState(
        id = AsyncMemberCombat.ENTITY_ID,
        name = AsyncMemberCombat.DISPLAY_NAME,
        isEntity = true,
        stats = CombatStats(
          hpStat = AsyncMemberCombat.BASE_HP_STAT + growth,
          defend = AsyncMemberCombat.BASE_DEFEND + growth,
          agi = AsyncMemberCombat.BASE_AGI + growth,
          crit = AsyncMemberCombat.BASE_CRIT + growth,
          currentHp = hp,
          maxHpOverride = hp,
          evasionOverride = AsyncMemberCombat.EVADE_CHANCE
        ),
        baseDamage = 8 + growth / 2
      )
    }
    val stat = CombatRules.BASE_STAT + growth
    val hp = CombatRules.maxHp(stat)
    return CombatantState(
      id = entityId,
      name = entityName(entityId),
      isEntity = true,
      stats = CombatStats(
        hpStat = stat,
        defend = stat,
        agi = stat,
        crit = stat,
        currentHp = hp
      ),
      baseDamage = 8 + growth / 2
    )
  }''',
    "Async Member combat profile",
)

skill_book = r'''object AsyncMemberSkillBook {
  val ANYSC_EVADE = KaiSkillDefinition(
    "async_member.anysc_evade", "Anysc Evade", CombatSkillCategory.PASSIVE, 1.00,
    "Always active: Async Member Evasion is fixed at 25%."
  )
  val LETS_CATCH_YOU = KaiSkillDefinition(
    "async_member.lets_catch_you", "Let's catch you", CombatSkillCategory.ACTIVE,
    AsyncMemberCombat.LETS_CATCH_YOU_PROC,
    "20% proc on each Async Member combat turn. On proc: current BaseDMG +20%; a successful hit applies [Choáng] for 2 turns.",
    attack = true,
    statusType = CombatEffectType.STUN,
    statusChance = 1.00,
    statusTurns = AsyncMemberCombat.LETS_CATCH_YOU_STUN_TURNS
  )
  val allSkills = listOf(ANYSC_EVADE, LETS_CATCH_YOU)
}

'''
if "object AsyncMemberSkillBook" not in combat:
    marker = "class AutoTurnCombatEngine(private val random: CombatRandom = DefaultCombatRandom()) {"
    if combat.count(marker) != 1:
        raise RuntimeError(f"Async Member skill-book insertion anchor expected once, found {combat.count(marker)}")
    combat = combat.replace(marker, skill_book + marker, 1)

combat = replace_once(
    combat,
    '''    fun isKai(): Boolean = !isEntity && id.equals(KAI_ID, ignoreCase = true)
    fun isLucia(): Boolean = !isEntity && id.equals("lucia", ignoreCase = true)
    fun devilTriggerActive(): Boolean = isKai() && devilTriggerTurnsRemaining > 0
''',
    '''    fun isKai(): Boolean = !isEntity && id.equals(KAI_ID, ignoreCase = true)
    fun isLucia(): Boolean = !isEntity && id.equals("lucia", ignoreCase = true)
    fun isAsyncMember(): Boolean = isEntity && id.equals(AsyncMemberCombat.ENTITY_ID, ignoreCase = true)
    fun devilTriggerActive(): Boolean = isKai() && devilTriggerTurnsRemaining > 0
''',
    "Async Member fighter identity",
)

combat = replace_once(
    combat,
    '''        } else {
          attack(enemy, actor, timeline)
        }
        tickDots(enemy, timeline, enemy.id)
''',
    '''        } else if (enemy.isAsyncMember()) {
          resolveAsyncMemberAction(enemy, actor, timeline)
        } else {
          attack(enemy, actor, timeline)
        }
        tickDots(enemy, timeline, enemy.id)
''',
    "Async Member enemy-turn resolver",
)

resolver = r'''  private fun resolveAsyncMemberAction(
    attacker: MutableFighter,
    target: MutableFighter,
    timeline: MutableList<CombatTimelineEvent>
  ) {
    if (!proc(AsyncMemberSkillBook.LETS_CATCH_YOU)) {
      attack(attacker, target, timeline)
      return
    }
    if (!attacker.alive() || !target.alive()) return

    val skill = AsyncMemberSkillBook.LETS_CATCH_YOU
    if (random.nextDouble() < target.effectiveStats().evasionChance) {
      timeline += CombatTimelineEvent(
        "SKILL_EVADE",
        actorId = attacker.id,
        targetId = target.id,
        enemyId = attacker.id,
        text = "${attacker.name} dùng [${skill.name}] lên ${target.name} → ${target.name} né tránh thành công."
      )
      return
    }

    val critical = random.nextDouble() < attacker.effectiveStats().criticalChance
    val skillBaseDamage = AsyncMemberCombat.letsCatchYouBaseDamage(attacker.baseDamage)
    val raw = skillBaseDamage * if (critical) CombatRules.CRIT_MULTIPLIER else 1
    val damage = CombatRules.finalDamage(raw, target.effectiveStats().defend)
    target.stats = target.stats.copy(currentHp = (target.stats.currentHp - damage).coerceAtLeast(0))

    var effectText = ""
    if (target.alive() && applyEffect(
        target,
        CombatEffectType.STUN,
        AsyncMemberCombat.LETS_CATCH_YOU_STUN_TURNS,
        timeline,
        attacker.id
      )) {
      effectText = " và nhận [Choáng] ${AsyncMemberCombat.LETS_CATCH_YOU_STUN_TURNS} lượt"
    }
    val criticalText = if (critical) " CRITICAL!" else ""
    timeline += CombatTimelineEvent(
      "SKILL",
      actorId = attacker.id,
      targetId = target.id,
      enemyId = attacker.id,
      text = "${attacker.name} dùng [${skill.name}] lên ${target.name} →$criticalText ${target.name} -$damage HP$effectText."
    )
  }

'''
if resolver not in combat:
    marker = "  private fun resolveLuciaAction(\n"
    if combat.count(marker) != 1:
        raise RuntimeError(f"Async Member resolver insertion anchor expected once, found {combat.count(marker)}")
    combat = combat.replace(marker, resolver + marker, 1)

for marker in [
    "ASYNC_MEMBER_ENTITY_PATCHED",
    "object AsyncMemberSkillBook",
    "fun isAsyncMember()",
    "resolveAsyncMemberAction(enemy, actor, timeline)",
    "maxHpOverride = hp",
    "evasionOverride = AsyncMemberCombat.EVADE_CHANCE",
    "LETS_CATCH_YOU_STUN_TURNS = 2",
]:
    if marker not in combat:
        raise RuntimeError(f"Async Member generated combat marker missing: {marker}")
COMBAT.write_text(combat, encoding="utf-8")

(TESTS / "AsyncMemberEntityGeneratedTest.kt").write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AsyncMemberEntityGeneratedTest {
  private class SequenceRandom(
    values: List<Double>,
    private val fallback: Double = 0.99
  ) : CombatRandom {
    private val queue = ArrayDeque(values)
    override fun nextDouble(): Double = if (queue.isEmpty()) fallback else queue.removeFirst()
  }

  @Test fun profileMatchesRequestedHumanEntityBaseStats() {
    val profile = CombatProfiles.entity(AsyncMemberCombat.ENTITY_ID, CombatStats(), 0)
    assertEquals("Async Member", profile.name)
    assertEquals(60, profile.stats.currentHp)
    assertEquals(60, profile.stats.maxHp)
    assertEquals(7, profile.stats.hpStat)
    assertEquals(8, profile.stats.defend)
    assertEquals(9, profile.stats.agi)
    assertEquals(10, profile.stats.crit)
    assertEquals(0.25, profile.stats.evasionChance, 0.0001)
  }

  @Test fun skillBookMatchesRequestedProcDamageAndStunContract() {
    assertEquals(100, AsyncMemberSkillBook.ANYSC_EVADE.procPercent)
    assertEquals(20, AsyncMemberSkillBook.LETS_CATCH_YOU.procPercent)
    assertEquals(2, AsyncMemberSkillBook.LETS_CATCH_YOU.statusTurns)
    assertEquals(10, AsyncMemberCombat.letsCatchYouBaseDamage(8))
  }

  @Test fun autoTurnCanProcLetsCatchYouAndConsumeTwoStunnedTurns() {
    val values = MutableList(11) { 0.99 }.apply { add(0.0) }
    val kai = CombatantState(
      KAI_ID,
      "Kai",
      false,
      CombatStats(hpStat = 55, currentHp = 100),
      baseDamage = 0
    )
    val result = AutoTurnCombatEngine(SequenceRandom(values)).resolve(
      encounterId = "ASYNC_MEMBER_TEST",
      partyInput = listOf(kai),
      entityIds = listOf(AsyncMemberCombat.ENTITY_ID),
      level = 0
    )
    val text = result.timeline.joinToString("\n") { it.text }
    assertTrue(text.contains("[Let's catch you]"))
    assertTrue(text.contains("[Choáng] 2 lượt"))
    assertTrue(result.timeline.count { it.text.contains("Kai đang [Choáng] → bỏ lượt.") } >= 2)
  }
}
''', encoding="utf-8")

print("Async Member finalized: human Entity, 60/60 HP, base stats 7/8/9/10, 25% Evasion passive, 20% Let's catch you proc with +20% BaseDMG and 2-turn Stun.")
