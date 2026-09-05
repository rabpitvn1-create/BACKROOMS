from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatCore.kt"
SERIALIZER = CORE / "CharacterDetailJson.kt"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Syvial/Iris skill finalizer {label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


combat = COMBAT.read_text(encoding="utf-8")
for dependency in ("KAI_PROC_SKILLS_PATCHED", "LUCIA_PROC_SKILLS_PATCHED", "ASYNC_MEMBER_ENTITY_PATCHED"):
    if dependency not in combat:
        raise RuntimeError(f"Syvial/Iris skill finalizer requires prior layer: {dependency}")

skill_block = r'''// SYVIAL_IRIS_SKILLS_CYCLE_1_PATCHED
object SyvialSkillBook {
  val LUCIFER_ARMOR_COMBAT_SYNC = KaiSkillDefinition(
    "syvial.lucifer_armor_combat_sync", "Lucifer Armor Combat Sync", CombatSkillCategory.PASSIVE, 0.29,
    "Đầu lượt Syvial: Lucifer Armor đồng bộ nhịp GodKiller, +10% Crit và bỏ qua 20% DF cho mọi đòn trong lượt."
  )
  val GODKILLER_CRIMSON_CLEAVE = KaiSkillDefinition(
    "syvial.godkiller_crimson_cleave", "GodKiller Crimson Cleave", CombatSkillCategory.ACTIVE, 0.33,
    "25 Base DMG, +10% Crit, bỏ qua 25% DF; khi trúng có 36% gây [Chảy máu] 2 lượt.",
    attack = true,
    baseDamage = 25,
    bonusCritChance = 0.10,
    defenseIgnore = 0.25,
    ranged = false,
    statusType = CombatEffectType.BLEED,
    statusChance = 0.36,
    statusTurns = 2
  )
  val LUCIFER_BREAKLINE = KaiSkillDefinition(
    "syvial.lucifer_breakline", "Lucifer Breakline", CombatSkillCategory.ACTIVE, 0.27,
    "22 Base DMG, +15% Crit, bỏ qua 20% DF; khi trúng có 30% gây [Choáng] 1 lượt.",
    attack = true,
    baseDamage = 22,
    bonusCritChance = 0.15,
    defenseIgnore = 0.20,
    ranged = false,
    statusType = CombatEffectType.STUN,
    statusChance = 0.30,
    statusTurns = 1
  )

  val passiveSkills = listOf(LUCIFER_ARMOR_COMBAT_SYNC)
  val activeSkills = listOf(GODKILLER_CRIMSON_CLEAVE, LUCIFER_BREAKLINE)
  val allSkills = passiveSkills + activeSkills

  fun skillsFor(characterId: String): List<KaiSkillDefinition> =
    if (characterId.equals("syvial", ignoreCase = true)) allSkills else emptyList()
}

object IrisSkillBook {
  val THOUSANDFOLD_FIRING_SOLUTION = KaiSkillDefinition(
    "iris.thousandfold_firing_solution", "Thousandfold Firing Solution", CombatSkillCategory.PASSIVE, 0.31,
    "Đầu lượt Iris: Thousandfold Cognition hoàn tất firing solution, +15% Crit và bỏ qua 20% DF cho mọi đòn trong lượt."
  )
  val ARGUS_CROSSFIRE = KaiSkillDefinition(
    "iris.argus_crossfire", "ARGUS Crossfire", CombatSkillCategory.ACTIVE, 0.36,
    "18 Base DMG, +15% Crit và bỏ qua 20% DF; khai thác đường ngắm do ARGUS Terrain Read xác định.",
    attack = true,
    baseDamage = 18,
    bonusCritChance = 0.15,
    defenseIgnore = 0.20,
    ranged = true
  )
  val IVORY_EBONY_KILLBOX = KaiSkillDefinition(
    "iris.ivory_ebony_killbox", "Ivory & Ebony Killbox", CombatSkillCategory.ACTIVE, 0.24,
    "21 Base DMG, +20% Crit và bỏ qua 25% DF; hai góc bắn của Ivory & Ebony ép mục tiêu vào killbox.",
    attack = true,
    baseDamage = 21,
    bonusCritChance = 0.20,
    defenseIgnore = 0.25,
    ranged = true
  )

  val passiveSkills = listOf(THOUSANDFOLD_FIRING_SOLUTION)
  val activeSkills = listOf(ARGUS_CROSSFIRE, IVORY_EBONY_KILLBOX)
  val allSkills = passiveSkills + activeSkills

  fun skillsFor(characterId: String): List<KaiSkillDefinition> =
    if (characterId.equals("iris", ignoreCase = true)) allSkills else emptyList()
}

'''
if "SYVIAL_IRIS_SKILLS_CYCLE_1_PATCHED" not in combat:
    marker = "class AutoTurnCombatEngine(private val random: CombatRandom = DefaultCombatRandom()) {"
    if combat.count(marker) != 1:
        raise RuntimeError(f"skill-book insertion anchor expected once, found {combat.count(marker)}")
    combat = combat.replace(marker, skill_block + marker, 1)

combat = replace_once(
    combat,
    '''    fun isKai(): Boolean = !isEntity && id.equals(KAI_ID, ignoreCase = true)
    fun isLucia(): Boolean = !isEntity && id.equals("lucia", ignoreCase = true)
    fun isAsyncMember(): Boolean = isEntity && id.equals(AsyncMemberCombat.ENTITY_ID, ignoreCase = true)
    fun devilTriggerActive(): Boolean = isKai() && devilTriggerTurnsRemaining > 0
''',
    '''    fun isKai(): Boolean = !isEntity && id.equals(KAI_ID, ignoreCase = true)
    fun isLucia(): Boolean = !isEntity && id.equals("lucia", ignoreCase = true)
    fun isSyvial(): Boolean = !isEntity && id.equals("syvial", ignoreCase = true)
    fun isIris(): Boolean = !isEntity && id.equals("iris", ignoreCase = true)
    fun isAsyncMember(): Boolean = isEntity && id.equals(AsyncMemberCombat.ENTITY_ID, ignoreCase = true)
    fun devilTriggerActive(): Boolean = isKai() && devilTriggerTurnsRemaining > 0
''',
    "party fighter identities",
)

combat = replace_once(
    combat,
    '''  private data class LuciaTurnBuff(
    var critBonus: Double = 0.0,
    var defenseIgnore: Double = 0.0
  )
''',
    '''  private data class LuciaTurnBuff(
    var critBonus: Double = 0.0,
    var defenseIgnore: Double = 0.0
  )

  private data class SruCompanionTurnBuff(
    var critBonus: Double = 0.0,
    var defenseIgnore: Double = 0.0
  )
''',
    "SRU companion turn buff",
)

combat = replace_once(
    combat,
    '''      } else if (actor.isLucia()) {
        resolveLuciaAction(actor, enemy, timeline)
      } else {
        attack(actor, enemy, timeline)
      }
''',
    '''      } else if (actor.isLucia()) {
        resolveLuciaAction(actor, enemy, timeline)
      } else if (actor.isSyvial()) {
        resolveSyvialAction(actor, enemy, timeline)
      } else if (actor.isIris()) {
        resolveIrisAction(actor, enemy, timeline)
      } else {
        attack(actor, enemy, timeline)
      }
''',
    "SRU companion auto-turn routing",
)

resolver_block = r'''  private fun resolveSyvialAction(
    actor: MutableFighter,
    target: MutableFighter,
    timeline: MutableList<CombatTimelineEvent>
  ) {
    val buff = SruCompanionTurnBuff()
    if (proc(SyvialSkillBook.LUCIFER_ARMOR_COMBAT_SYNC)) {
      buff.critBonus += 0.10
      buff.defenseIgnore += 0.20
      timeline += CombatTimelineEvent(
        "PASSIVE",
        actorId = actor.id,
        enemyId = target.id,
        text = "${actor.name} kích hoạt [Lucifer Armor Combat Sync]."
      )
    }

    val triggered = SyvialSkillBook.activeSkills.filter { proc(it) }
    if (triggered.isEmpty()) {
      resolveSruCompanionAttack(actor, target, null, buff, timeline)
      return
    }
    triggered.forEach { skill ->
      if (target.alive()) resolveSruCompanionAttack(actor, target, skill, buff, timeline)
    }
  }

  private fun resolveIrisAction(
    actor: MutableFighter,
    target: MutableFighter,
    timeline: MutableList<CombatTimelineEvent>
  ) {
    val buff = SruCompanionTurnBuff()
    if (proc(IrisSkillBook.THOUSANDFOLD_FIRING_SOLUTION)) {
      buff.critBonus += 0.15
      buff.defenseIgnore += 0.20
      timeline += CombatTimelineEvent(
        "PASSIVE",
        actorId = actor.id,
        enemyId = target.id,
        text = "${actor.name} kích hoạt [Thousandfold Firing Solution]."
      )
    }

    val triggered = IrisSkillBook.activeSkills.filter { proc(it) }
    if (triggered.isEmpty()) {
      resolveSruCompanionAttack(actor, target, null, buff, timeline)
      return
    }
    triggered.forEach { skill ->
      if (target.alive()) resolveSruCompanionAttack(actor, target, skill, buff, timeline)
    }
  }

  private fun resolveSruCompanionAttack(
    attacker: MutableFighter,
    target: MutableFighter,
    skill: KaiSkillDefinition?,
    buff: SruCompanionTurnBuff,
    timeline: MutableList<CombatTimelineEvent>
  ) {
    if (!attacker.alive() || !target.alive()) return
    val displayName = skill?.name ?: "Tấn công"
    val baseDamage = skill?.baseDamage ?: attacker.baseDamage
    val canBeEvaded = skill?.canBeEvaded ?: true

    if (canBeEvaded && random.nextDouble() < target.effectiveStats().evasionChance) {
      timeline += CombatTimelineEvent(
        if (skill == null) "EVADE" else "SKILL_EVADE",
        actorId = attacker.id,
        targetId = target.id,
        enemyId = target.id,
        text = "${attacker.name} dùng [$displayName] lên ${target.name} → ${target.name} né tránh thành công."
      )
      return
    }

    val critChance = (
      attacker.effectiveStats().criticalChance +
        buff.critBonus +
        (skill?.bonusCritChance ?: 0.0)
      ).coerceAtMost(CombatRules.MAX_CRIT)
    val critical = random.nextDouble() < critChance
    val raw = baseDamage * if (critical) CombatRules.CRIT_MULTIPLIER else 1
    val defenseIgnore = (buff.defenseIgnore + (skill?.defenseIgnore ?: 0.0)).coerceAtMost(0.90)
    val damage = CombatRules.finalDamage(raw, target.effectiveStats().defend, defenseIgnore)
    target.stats = target.stats.copy(currentHp = (target.stats.currentHp - damage).coerceAtLeast(0))

    var effectText = ""
    val effectType = skill?.statusType
    if (effectType != null && target.alive() && random.nextDouble() < skill.statusChance) {
      if (applyEffect(target, effectType, skill.statusTurns, timeline, attacker.id)) {
        effectText = " và nhận [${effectLabel(effectType)}] ${skill.statusTurns} lượt"
      }
    }

    val criticalText = if (critical) " CRITICAL!" else ""
    timeline += CombatTimelineEvent(
      if (skill == null) "ATTACK" else "SKILL",
      actorId = attacker.id,
      targetId = target.id,
      enemyId = target.id,
      text = "${attacker.name} dùng [$displayName] lên ${target.name} →$criticalText ${target.name} -$damage HP$effectText."
    )
  }

'''
if resolver_block not in combat:
    marker = "  private fun resolveLuciaAction(\n"
    if combat.count(marker) != 1:
        raise RuntimeError(f"SRU companion resolver insertion anchor expected once, found {combat.count(marker)}")
    combat = combat.replace(marker, resolver_block + marker, 1)

for marker in [
    "SYVIAL_IRIS_SKILLS_CYCLE_1_PATCHED",
    "object SyvialSkillBook",
    "object IrisSkillBook",
    "fun isSyvial()",
    "fun isIris()",
    "resolveSyvialAction(actor, enemy, timeline)",
    "resolveIrisAction(actor, enemy, timeline)",
]:
    if marker not in combat:
        raise RuntimeError(f"generated combat marker missing: {marker}")
COMBAT.write_text(combat, encoding="utf-8")

serializer = SERIALIZER.read_text(encoding="utf-8")
serializer = replace_once(
    serializer,
    '    val skills = KaiSkillBook.skillsFor(character.id) + LuciaSkillBook.skillsFor(character.id)\n',
    '    val skills = KaiSkillBook.skillsFor(character.id) + LuciaSkillBook.skillsFor(character.id) + SyvialSkillBook.skillsFor(character.id) + IrisSkillBook.skillsFor(character.id)\n',
    "skill serialization",
)
SERIALIZER.write_text(serializer, encoding="utf-8")

(TESTS / "SyvialIrisSkillGeneratedTest.kt").write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyvialIrisSkillGeneratedTest {
  @Test fun cycleOneAddsTwoActiveAndOnePassivePerCharacter() {
    assertEquals(2, SyvialSkillBook.activeSkills.size)
    assertEquals(1, SyvialSkillBook.passiveSkills.size)
    assertEquals(2, IrisSkillBook.activeSkills.size)
    assertEquals(1, IrisSkillBook.passiveSkills.size)
    assertEquals(3, SyvialSkillBook.skillsFor("Syvial").size)
    assertEquals(3, IrisSkillBook.skillsFor("IRIS").size)
  }

  @Test fun everyRandomProcStaysInsideTwentyToFortyPercent() {
    val randomProcSkills = SyvialSkillBook.allSkills + IrisSkillBook.allSkills
    assertTrue(randomProcSkills.all { it.procChance in 0.20..0.40 })
    assertEquals(36, SyvialSkillBook.GODKILLER_CRIMSON_CLEAVE.statusChance.times(100).toInt())
    assertEquals(30, SyvialSkillBook.LUCIFER_BREAKLINE.statusChance.times(100).toInt())
  }

  @Test fun skillsKeepTheCharactersDistinctCombatRoles() {
    assertTrue(SyvialSkillBook.activeSkills.all { !it.ranged })
    assertTrue(IrisSkillBook.activeSkills.all { it.ranged })
    assertEquals(CombatEffectType.BLEED, SyvialSkillBook.GODKILLER_CRIMSON_CLEAVE.statusType)
    assertEquals(CombatEffectType.STUN, SyvialSkillBook.LUCIFER_BREAKLINE.statusType)
    assertTrue(IrisSkillBook.IVORY_EBONY_KILLBOX.bonusCritChance > IrisSkillBook.ARGUS_CROSSFIRE.bonusCritChance)
  }
}
''', encoding="utf-8")

print("Syvial/Iris cycle 1 finalized: each has 2 Active + 1 Passive, with every random proc/status chance kept within 20-40%.")
