from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatCore.kt"
SERIALIZER = CORE / "CharacterDetailJson.kt"
INDEX = ROOT / "app/src/main/assets/index.html"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/KaiSkillCombatGeneratedTest.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# 1) Combat math hooks used by Kai skills.
# ---------------------------------------------------------------------------
combat = COMBAT.read_text(encoding="utf-8")
combat = replace_once(
    combat,
    '''  fun finalDamage(rawDamage: Int, defend: Int): Int {
    if (rawDamage <= 0) return 0
    val reduced = rawDamage * 100.0 / (100.0 + defensePoints(defend))
    return reduced.roundToInt().coerceAtLeast(1)
  }''',
    '''  fun finalDamage(rawDamage: Int, defend: Int, defenseIgnore: Double = 0.0): Int {
    if (rawDamage <= 0) return 0
    val ignore = defenseIgnore.coerceIn(0.0, 0.90)
    val effectiveDefense = defensePoints(defend) * (1.0 - ignore)
    val reduced = rawDamage * 100.0 / (100.0 + effectiveDefense)
    return reduced.roundToInt().coerceAtLeast(1)
  }''',
    "combat defense-ignore formula",
)
combat = replace_once(
    combat,
    '''  fun tickDamage(stats: CombatStats, effects: Map<CombatEffectType, CombatEffect>): Pair<CombatStats, Map<CombatEffectType, CombatEffect>> {
    var hp = stats.currentHp
    var next = effects
    effects[CombatEffectType.BLEED]?.let { bleed ->
      hp = (hp - CombatRules.BLEED_DAMAGE).coerceAtLeast(0)
      next = decrement(next, bleed)
    }
    effects[CombatEffectType.POISON]?.let { poison ->
      hp = (hp - CombatRules.poisonDamage(stats.maxHp)).coerceAtLeast(0)
      next = decrement(next, poison)
    }
    return stats.copy(currentHp = hp) to next
  }''',
    '''  fun tickDamage(
    stats: CombatStats,
    effects: Map<CombatEffectType, CombatEffect>,
    poisonMaxHp: Int = stats.maxHp
  ): Pair<CombatStats, Map<CombatEffectType, CombatEffect>> {
    var hp = stats.currentHp
    var next = effects
    effects[CombatEffectType.BLEED]?.let { bleed ->
      hp = (hp - CombatRules.BLEED_DAMAGE).coerceAtLeast(0)
      next = decrement(next, bleed)
    }
    effects[CombatEffectType.POISON]?.let { poison ->
      hp = (hp - CombatRules.poisonDamage(poisonMaxHp)).coerceAtLeast(0)
      next = decrement(next, poison)
    }
    return stats.copy(currentHp = hp) to next
  }''',
    "combat poison effective HP",
)

if "KAI_PROC_SKILLS_PATCHED" not in combat:
    marker = "class AutoTurnCombatEngine(private val random: CombatRandom = DefaultCombatRandom()) {"
    start = combat.find(marker)
    if start < 0:
        raise RuntimeError("Kai skill engine: AutoTurnCombatEngine anchor not found")
    tail = r'''// KAI_PROC_SKILLS_PATCHED
enum class CombatSkillCategory { PASSIVE, ACTIVE }

data class KaiSkillDefinition(
  val id: String,
  val name: String,
  val category: CombatSkillCategory,
  val procChance: Double,
  val description: String,
  val attack: Boolean = false,
  val baseDamage: Int = 0,
  val bonusCritChance: Double = 0.0,
  val defenseIgnore: Double = 0.0,
  val canBeEvaded: Boolean = true,
  val ranged: Boolean = false,
  val statusType: CombatEffectType? = null,
  val statusChance: Double = 0.0,
  val statusTurns: Int = 0
) {
  val procPercent: Int get() = (procChance * 100.0).roundToInt()
}

object KaiSkillBook {
  const val DEVIL_TRIGGER_STAT_MULTIPLIER = 5
  const val DEVIL_TRIGGER_TURNS = 3
  const val GUILTY_CROWN_SHOTS = 24
  const val GUILTY_CROWN_DAMAGE_PER_SHOT = 4
  const val REGEN_HP = 4

  val COMBAT_ANALYSIS = KaiSkillDefinition(
    "kai.combat_analysis", "Combat Analysis", CombatSkillCategory.PASSIVE, 0.25,
    "Đầu lượt: +10% Crit và bỏ qua 30% DF cho mọi đòn của Kai trong lượt."
  )
  val DEMONIC_REGENERATION = KaiSkillDefinition(
    "kai.demonic_regeneration", "Demonic Regeneration", CombatSkillCategory.PASSIVE, 0.25,
    "Cuối lượt: hồi 4 HP; vẫn có thể proc khi Kai bị [Choáng]."
  )
  val SUPERNATURAL_RESISTANCE = KaiSkillDefinition(
    "kai.supernatural_resistance", "Supernatural Resistance", CombatSkillCategory.PASSIVE, 0.50,
    "Khi sắp nhận [Trúng Độc]: proc sẽ chặn Poison mới."
  )
  val BALLISTIC_PREDICTION = KaiSkillDefinition(
    "kai.ballistic_prediction", "Ballistic Prediction", CombatSkillCategory.PASSIVE, 0.20,
    "Roll riêng trước mỗi đòn có Evasion: giảm 50% Evasion mục tiêu cho đòn đó."
  )
  val RIFLE_MASTERY = KaiSkillDefinition(
    "kai.rifle_mastery", "SRU Assault Rifle Mastery", CombatSkillCategory.PASSIVE, 0.15,
    "Roll riêng trên từng đòn bắn: +20% Crit cho đòn đó."
  )

  val DEVIL_TRIGGER = KaiSkillDefinition(
    "kai.devil_trigger", "Devil Trigger", CombatSkillCategory.ACTIVE, 0.50,
    "Khi chưa active: x5 HP/DEFEND/AGI/CRIT hiện tại trong 3 lượt của Kai; không mana/cooldown."
  )
  val CONTROLLED_BURST = KaiSkillDefinition(
    "kai.controlled_burst", "Controlled Burst", CombatSkillCategory.ACTIVE, 0.35,
    "Đòn bắn 15 Base DMG; có Evasion và Critical.",
    attack = true, baseDamage = 15, ranged = true
  )
  val WEAK_POINT_SHOT = KaiSkillDefinition(
    "kai.weak_point_shot", "Weak Point Shot", CombatSkillCategory.ACTIVE, 0.22,
    "18 Base DMG, +20% Crit và bỏ qua 25% DF.",
    attack = true, baseDamage = 18, bonusCritChance = 0.20, defenseIgnore = 0.25, ranged = true
  )
  val CQC_BREAK = KaiSkillDefinition(
    "kai.cqc_break", "CQC Break", CombatSkillCategory.ACTIVE, 0.15,
    "14 Base DMG; khi trúng có 60% gây [Choáng] 1 lượt.",
    attack = true, baseDamage = 14, ranged = false,
    statusType = CombatEffectType.STUN, statusChance = 0.60, statusTurns = 1
  )
  val GUILTY_CROWN_OVERRIDE = KaiSkillDefinition(
    "kai.guilty_crown_override", "Guilty Crown Override", CombatSkillCategory.ACTIVE, 0.30,
    "Độc lập Devil Trigger; đúng 24 phát quỷ lực, 96 Raw DMG, bỏ qua 50% DF và không roll Evasion.",
    attack = true,
    baseDamage = GUILTY_CROWN_SHOTS * GUILTY_CROWN_DAMAGE_PER_SHOT,
    defenseIgnore = 0.50,
    canBeEvaded = false,
    ranged = true
  )

  val passiveSkills = listOf(
    COMBAT_ANALYSIS,
    DEMONIC_REGENERATION,
    SUPERNATURAL_RESISTANCE,
    BALLISTIC_PREDICTION,
    RIFLE_MASTERY
  )
  val activeSkills = listOf(
    DEVIL_TRIGGER,
    CONTROLLED_BURST,
    WEAK_POINT_SHOT,
    CQC_BREAK,
    GUILTY_CROWN_OVERRIDE
  )
  val activeAttackSkills = listOf(CONTROLLED_BURST, WEAK_POINT_SHOT, CQC_BREAK, GUILTY_CROWN_OVERRIDE)
  val allSkills = passiveSkills + activeSkills

  fun skillsFor(characterId: String): List<KaiSkillDefinition> =
    if (characterId.equals(KAI_ID, ignoreCase = true)) allSkills else emptyList()

  fun effectiveStats(base: CombatStats, devilTriggerActive: Boolean): CombatStats {
    if (!devilTriggerActive) return base
    return base.copy(
      hpStat = base.hpStat * DEVIL_TRIGGER_STAT_MULTIPLIER,
      defend = base.defend * DEVIL_TRIGGER_STAT_MULTIPLIER,
      agi = base.agi * DEVIL_TRIGGER_STAT_MULTIPLIER,
      crit = base.crit * DEVIL_TRIGGER_STAT_MULTIPLIER
    )
  }

  fun devilTriggerHpBonus(base: CombatStats): Int =
    CombatRules.maxHp(base.hpStat * DEVIL_TRIGGER_STAT_MULTIPLIER) - base.maxHp
}

class AutoTurnCombatEngine(private val random: CombatRandom = DefaultCombatRandom()) {
  private data class MutableFighter(
    val id: String,
    val name: String,
    val isEntity: Boolean,
    var stats: CombatStats,
    val baseDamage: Int,
    var effects: Map<CombatEffectType, CombatEffect>,
    var devilTriggerTurnsRemaining: Int = 0,
    var devilTriggerHpBonus: Int = 0
  ) {
    fun snapshot() = CombatantState(id, name, isEntity, stats, baseDamage, effects)
    fun alive(): Boolean = stats.currentHp > 0
    fun isKai(): Boolean = !isEntity && id.equals(KAI_ID, ignoreCase = true)
    fun devilTriggerActive(): Boolean = isKai() && devilTriggerTurnsRemaining > 0
    fun effectiveStats(): CombatStats = KaiSkillBook.effectiveStats(stats, devilTriggerActive())
    fun effectiveMaxHp(): Int = effectiveStats().maxHp
  }

  private data class KaiTurnBuff(
    var critBonus: Double = 0.0,
    var defenseIgnore: Double = 0.0
  )

  fun resolve(
    encounterId: String,
    partyInput: List<CombatantState>,
    entityIds: List<String>,
    level: Int
  ): CombatResolution {
    val queue = entityIds.distinct().filter { it.isNotBlank() }
    if (queue.isEmpty()) {
      return CombatResolution(encounterId, CombatOutcome.NO_ENCOUNTER, level, emptyList(), partyInput, emptyList(), emptyList())
    }

    val orderedParty = partyInput
      .filterNot { it.isEntity }
      .sortedWith(compareBy<CombatantState> { if (it.id.equals(KAI_ID, true)) 0 else 1 }.thenBy { partyInput.indexOf(it) })
      .map { MutableFighter(it.id, it.name, false, it.stats, it.baseDamage, it.effects) }
      .toMutableList()
    val kai = orderedParty.firstOrNull { it.id.equals(KAI_ID, true) }
      ?: return CombatResolution(encounterId, CombatOutcome.DEFEAT, level, queue, partyInput, emptyList(), emptyList())

    val timeline = mutableListOf<CombatTimelineEvent>()
    val defeated = mutableListOf<String>()
    var enemyIndex = 0
    var enemy = CombatProfiles.entity(queue[enemyIndex], kai.stats, level).toMutable()
    var partyCursor = 0
    var actions = 0

    timeline += CombatTimelineEvent("ENTITY_ENTER", enemyId = enemy.id, text = "${enemy.name} bước vào chiến đấu.")

    while (enemyIndex < queue.size && orderedParty.any { it.alive() } && actions < 10_000) {
      val actorIndex = nextLivingPartyIndex(orderedParty, partyCursor) ?: break
      val actor = orderedParty[actorIndex]
      timeline += CombatTimelineEvent("FOCUS", actorId = actor.id, enemyId = enemy.id, text = actor.name)

      val (stunned, actorEffects) = CombatEffects.consumeStun(actor.effects)
      actor.effects = actorEffects
      if (stunned) {
        timeline += CombatTimelineEvent("STATUS", actorId = actor.id, enemyId = enemy.id, text = "${actor.name} đang [Choáng] → bỏ lượt.")
      } else if (actor.isKai()) {
        resolveKaiAction(actor, enemy, timeline)
      } else {
        attack(actor, enemy, timeline)
      }

      if (actor.isKai() && actor.alive()) resolveKaiRegeneration(actor, timeline, enemy.id)
      tickDots(actor, timeline, enemy.id)
      if (actor.isKai()) finishKaiTurn(actor, timeline, enemy.id)
      actions++

      if (!enemy.alive()) {
        rewardKill(orderedParty)
        defeated += enemy.id
        timeline += CombatTimelineEvent("ENTITY_DOWN", actorId = actor.id, enemyId = enemy.id, text = "${enemy.name} bị tiêu diệt.")
        enemyIndex++
        partyCursor = (actorIndex + 1) % orderedParty.size
        if (enemyIndex >= queue.size) break
        enemy = CombatProfiles.entity(queue[enemyIndex], orderedParty.first { it.id.equals(KAI_ID, true) }.stats, level).toMutable()
        timeline += CombatTimelineEvent("ENTITY_ENTER", enemyId = enemy.id, text = "${enemy.name} bước vào chiến đấu.")
        continue
      }

      if (actor.alive()) {
        val (enemyStunned, enemyEffects) = CombatEffects.consumeStun(enemy.effects)
        enemy.effects = enemyEffects
        if (enemyStunned) {
          timeline += CombatTimelineEvent("STATUS", actorId = enemy.id, targetId = actor.id, enemyId = enemy.id, text = "${enemy.name} đang [Choáng] → bỏ lượt.")
        } else {
          attack(enemy, actor, timeline)
        }
        tickDots(enemy, timeline, enemy.id)
        actions++
      }

      if (!enemy.alive()) {
        rewardKill(orderedParty)
        defeated += enemy.id
        timeline += CombatTimelineEvent("ENTITY_DOWN", actorId = actor.id, enemyId = enemy.id, text = "${enemy.name} bị tiêu diệt.")
        enemyIndex++
        if (enemyIndex < queue.size) {
          enemy = CombatProfiles.entity(queue[enemyIndex], orderedParty.first { it.id.equals(KAI_ID, true) }.stats, level).toMutable()
          timeline += CombatTimelineEvent("ENTITY_ENTER", enemyId = enemy.id, text = "${enemy.name} bước vào chiến đấu.")
        }
      }

      partyCursor = (actorIndex + 1) % orderedParty.size
    }

    val outcome = when {
      actions >= 10_000 -> CombatOutcome.SAFETY_ABORT
      enemyIndex >= queue.size -> CombatOutcome.VICTORY
      orderedParty.none { it.alive() } -> CombatOutcome.DEFEAT
      else -> CombatOutcome.DEFEAT
    }

    orderedParty.filter { it.devilTriggerActive() }.forEach { deactivateDevilTrigger(it, timeline = null, enemyId = null) }

    timeline += CombatTimelineEvent(
      "COMBAT_END",
      text = when (outcome) {
        CombatOutcome.VICTORY -> "Toàn bộ Entity trong lượt chạm trán đã bị tiêu diệt."
        CombatOutcome.SAFETY_ABORT -> "Combat dừng bởi giới hạn an toàn của engine."
        CombatOutcome.NO_ENCOUNTER -> "Không có Entity để chiến đấu."
        CombatOutcome.DEFEAT -> "Party không còn thành viên có thể tiếp tục chiến đấu."
      }
    )

    return CombatResolution(encounterId, outcome, level, queue, orderedParty.map { it.snapshot() }, timeline, defeated)
  }

  private fun CombatantState.toMutable() = MutableFighter(id, name, isEntity, stats, baseDamage, effects)

  private fun nextLivingPartyIndex(party: List<MutableFighter>, start: Int): Int? {
    if (party.isEmpty()) return null
    repeat(party.size) { offset ->
      val index = (start + offset) % party.size
      if (party[index].alive()) return index
    }
    return null
  }

  private fun proc(skill: KaiSkillDefinition): Boolean = random.nextDouble() < skill.procChance

  private fun resolveKaiAction(
    actor: MutableFighter,
    target: MutableFighter,
    timeline: MutableList<CombatTimelineEvent>
  ) {
    val buff = KaiTurnBuff()
    if (proc(KaiSkillBook.COMBAT_ANALYSIS)) {
      buff.critBonus += 0.10
      buff.defenseIgnore += 0.30
      timeline += CombatTimelineEvent(
        "PASSIVE",
        actorId = actor.id,
        enemyId = target.id,
        text = "${actor.name} kích hoạt [Combat Analysis]."
      )
    }

    if (!actor.devilTriggerActive() && proc(KaiSkillBook.DEVIL_TRIGGER)) {
      activateDevilTrigger(actor, timeline, target.id)
    }

    // Every active attack proc is rolled before any of them resolves. Multiple successes form one combo.
    val triggered = KaiSkillBook.activeAttackSkills.filter { proc(it) }
    if (triggered.isEmpty()) {
      resolveKaiAttack(actor, target, null, buff, timeline)
      return
    }

    triggered.forEach { skill ->
      if (target.alive()) resolveKaiAttack(actor, target, skill, buff, timeline)
    }
  }

  private fun resolveKaiAttack(
    attacker: MutableFighter,
    target: MutableFighter,
    skill: KaiSkillDefinition?,
    buff: KaiTurnBuff,
    timeline: MutableList<CombatTimelineEvent>
  ) {
    if (!attacker.alive() || !target.alive()) return
    val displayName = skill?.name ?: "Tấn công"
    val baseDamage = skill?.baseDamage ?: attacker.baseDamage
    val ranged = skill?.ranged ?: true
    val canBeEvaded = skill?.canBeEvaded ?: true

    var targetEvasion = target.effectiveStats().evasionChance
    if (canBeEvaded && proc(KaiSkillBook.BALLISTIC_PREDICTION)) {
      targetEvasion *= 0.5
      timeline += CombatTimelineEvent(
        "PASSIVE",
        actorId = attacker.id,
        targetId = target.id,
        enemyId = target.id,
        text = "${attacker.name} kích hoạt [Ballistic Prediction]."
      )
    }

    var critBonus = buff.critBonus + (skill?.bonusCritChance ?: 0.0)
    if (ranged && proc(KaiSkillBook.RIFLE_MASTERY)) {
      critBonus += 0.20
      timeline += CombatTimelineEvent(
        "PASSIVE",
        actorId = attacker.id,
        targetId = target.id,
        enemyId = target.id,
        text = "${attacker.name} kích hoạt [SRU Assault Rifle Mastery]."
      )
    }

    if (canBeEvaded && random.nextDouble() < targetEvasion) {
      timeline += CombatTimelineEvent(
        if (skill == null) "EVADE" else "SKILL_EVADE",
        actorId = attacker.id,
        targetId = target.id,
        enemyId = target.id,
        text = "${attacker.name} dùng [$displayName] lên ${target.name} → ${target.name} né tránh thành công."
      )
      return
    }

    val critChance = (attacker.effectiveStats().criticalChance + critBonus).coerceAtMost(CombatRules.MAX_CRIT)
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
    val shotText = if (skill?.id == KaiSkillBook.GUILTY_CROWN_OVERRIDE.id) " ${KaiSkillBook.GUILTY_CROWN_SHOTS} phát," else ""
    timeline += CombatTimelineEvent(
      if (skill == null) "ATTACK" else "SKILL",
      actorId = attacker.id,
      targetId = target.id,
      enemyId = target.id,
      text = "${attacker.name} dùng [$displayName] lên ${target.name} →$criticalText$shotText ${target.name} -$damage HP$effectText."
    )
  }

  private fun applyEffect(
    target: MutableFighter,
    type: CombatEffectType,
    turns: Int,
    timeline: MutableList<CombatTimelineEvent>,
    sourceId: String
  ): Boolean {
    if (type == CombatEffectType.POISON && target.isKai() && proc(KaiSkillBook.SUPERNATURAL_RESISTANCE)) {
      timeline += CombatTimelineEvent(
        "PASSIVE",
        actorId = target.id,
        targetId = target.id,
        enemyId = if (sourceId.startsWith("ENTITY.")) sourceId else null,
        text = "${target.name} kích hoạt [Supernatural Resistance] → chặn [Trúng Độc]."
      )
      return false
    }
    target.effects = CombatEffects.apply(target.effects, type, turns)
    return true
  }

  private fun resolveKaiRegeneration(
    actor: MutableFighter,
    timeline: MutableList<CombatTimelineEvent>,
    enemyId: String?
  ) {
    if (!proc(KaiSkillBook.DEMONIC_REGENERATION)) return
    val before = actor.stats.currentHp
    val after = (before + KaiSkillBook.REGEN_HP).coerceAtMost(actor.effectiveMaxHp())
    actor.stats = actor.stats.copy(currentHp = after)
    val healed = after - before
    timeline += CombatTimelineEvent(
      "PASSIVE",
      actorId = actor.id,
      enemyId = enemyId,
      text = if (healed > 0) "${actor.name} kích hoạt [Demonic Regeneration] → +$healed HP."
      else "${actor.name} kích hoạt [Demonic Regeneration] → HP đã đầy."
    )
  }

  private fun activateDevilTrigger(
    actor: MutableFighter,
    timeline: MutableList<CombatTimelineEvent>,
    enemyId: String?
  ) {
    val bonus = KaiSkillBook.devilTriggerHpBonus(actor.stats)
    val boostedMax = CombatRules.maxHp(actor.stats.hpStat * KaiSkillBook.DEVIL_TRIGGER_STAT_MULTIPLIER)
    actor.devilTriggerTurnsRemaining = KaiSkillBook.DEVIL_TRIGGER_TURNS
    actor.devilTriggerHpBonus = bonus
    actor.stats = actor.stats.copy(currentHp = (actor.stats.currentHp + bonus).coerceAtMost(boostedMax))
    timeline += CombatTimelineEvent(
      "DEVIL_TRIGGER_ON",
      actorId = actor.id,
      enemyId = enemyId,
      text = "${actor.name} kích hoạt [Devil Trigger] → Base Stats ×${KaiSkillBook.DEVIL_TRIGGER_STAT_MULTIPLIER} trong ${KaiSkillBook.DEVIL_TRIGGER_TURNS} lượt."
    )
  }

  private fun finishKaiTurn(
    actor: MutableFighter,
    timeline: MutableList<CombatTimelineEvent>,
    enemyId: String?
  ) {
    if (!actor.devilTriggerActive()) return
    actor.devilTriggerTurnsRemaining--
    if (actor.devilTriggerTurnsRemaining <= 0) deactivateDevilTrigger(actor, timeline, enemyId)
  }

  private fun deactivateDevilTrigger(
    actor: MutableFighter,
    timeline: MutableList<CombatTimelineEvent>?,
    enemyId: String?
  ) {
    val baseMax = actor.stats.maxHp
    actor.stats = actor.stats.copy(
      currentHp = (actor.stats.currentHp - actor.devilTriggerHpBonus).coerceIn(0, baseMax)
    )
    actor.devilTriggerHpBonus = 0
    actor.devilTriggerTurnsRemaining = 0
    timeline?.add(
      CombatTimelineEvent(
        "DEVIL_TRIGGER_OFF",
        actorId = actor.id,
        enemyId = enemyId,
        text = "[Devil Trigger] của ${actor.name} kết thúc."
      )
    )
  }

  private fun refreshDevilTriggerHpBonus(actor: MutableFighter) {
    if (!actor.devilTriggerActive()) return
    val desired = KaiSkillBook.devilTriggerHpBonus(actor.stats)
    val delta = desired - actor.devilTriggerHpBonus
    actor.devilTriggerHpBonus = desired
    actor.stats = actor.stats.copy(currentHp = (actor.stats.currentHp + delta).coerceIn(0, actor.effectiveMaxHp()))
  }

  private fun attack(attacker: MutableFighter, target: MutableFighter, timeline: MutableList<CombatTimelineEvent>) {
    if (!attacker.alive() || !target.alive()) return
    if (random.nextDouble() < target.effectiveStats().evasionChance) {
      timeline += CombatTimelineEvent(
        "EVADE",
        actorId = attacker.id,
        targetId = target.id,
        enemyId = if (attacker.isEntity) attacker.id else if (target.isEntity) target.id else null,
        text = "${attacker.name} dùng [Tấn công] lên ${target.name} → ${target.name} né tránh thành công."
      )
      return
    }

    val critical = random.nextDouble() < attacker.effectiveStats().criticalChance
    val raw = attacker.baseDamage * if (critical) CombatRules.CRIT_MULTIPLIER else 1
    val damage = CombatRules.finalDamage(raw, target.effectiveStats().defend)
    target.stats = target.stats.copy(currentHp = (target.stats.currentHp - damage).coerceAtLeast(0))
    val criticalText = if (critical) " CRITICAL!" else ""
    timeline += CombatTimelineEvent(
      "ATTACK",
      actorId = attacker.id,
      targetId = target.id,
      enemyId = if (attacker.isEntity) attacker.id else if (target.isEntity) target.id else null,
      text = "${attacker.name} dùng [Tấn công] lên ${target.name} →$criticalText ${target.name} -$damage HP."
    )
  }

  private fun tickDots(fighter: MutableFighter, timeline: MutableList<CombatTimelineEvent>, enemyId: String?) {
    if (!fighter.alive()) return
    val before = fighter.stats.currentHp
    val hadBleed = fighter.effects.containsKey(CombatEffectType.BLEED)
    val hadPoison = fighter.effects.containsKey(CombatEffectType.POISON)
    var remainingForLog = before
    val bleedDamage = if (hadBleed) minOf(CombatRules.BLEED_DAMAGE, remainingForLog).also { remainingForLog -= it } else 0
    val poisonDamage = if (hadPoison) minOf(CombatRules.poisonDamage(fighter.effectiveMaxHp()), remainingForLog) else 0
    val (stats, effects) = CombatEffects.tickDamage(fighter.stats, fighter.effects, fighter.effectiveMaxHp())
    fighter.stats = stats
    fighter.effects = effects
    if (hadBleed) {
      timeline += CombatTimelineEvent("STATUS", actorId = fighter.id, enemyId = enemyId, text = "${fighter.name} chịu [Chảy máu] → -$bleedDamage HP.")
    }
    if (hadPoison) {
      timeline += CombatTimelineEvent("STATUS", actorId = fighter.id, enemyId = enemyId, text = "${fighter.name} chịu [Trúng Độc] → -$poisonDamage HP.")
    }
  }

  private fun effectLabel(type: CombatEffectType): String = when (type) {
    CombatEffectType.BLEED -> "Chảy máu"
    CombatEffectType.STUN -> "Choáng"
    CombatEffectType.POISON -> "Trúng Độc"
  }

  private fun rewardKill(party: List<MutableFighter>) {
    party.filter { it.alive() }.forEach { fighter ->
      fighter.stats = CombatProgression.awardEntityKill(fighter.id, fighter.stats)
      if (fighter.isKai()) refreshDevilTriggerHpBonus(fighter)
    }
  }
}
'''
    combat = combat[:start] + tail

COMBAT.write_text(combat, encoding="utf-8")

# ---------------------------------------------------------------------------
# 2) Expose Kai's authoritative skill list through the existing character-detail JSON.
# ---------------------------------------------------------------------------
serializer = SERIALIZER.read_text(encoding="utf-8")
serializer = replace_once(
    serializer,
    '    put("injuries", JSONArray(character.injuries))\n',
    '''    val skills = KaiSkillBook.skillsFor(character.id)
    if (skills.isNotEmpty()) {
      put("skills", JSONArray().apply {
        skills.forEach { skill -> put(JSONObject().apply {
          put("id", skill.id)
          put("name", skill.name)
          put("category", skill.category.name)
          put("procPercent", skill.procPercent)
          put("description", skill.description)
        }) }
      })
    }
    put("injuries", JSONArray(character.injuries))
''',
    "Kai skill character JSON",
)
SERIALIZER.write_text(serializer, encoding="utf-8")

# ---------------------------------------------------------------------------
# 3) Status screen skill table. Other characters keep the panel hidden until they get a skill book.
# ---------------------------------------------------------------------------
html = INDEX.read_text(encoding="utf-8")
html = replace_once(
    html,
    '<div class="character-status-list" id="characterStatusList"></div></div>',
    '<div class="character-status-list" id="characterStatusList"></div><div class="character-skill-panel" id="characterSkillPanel" hidden></div></div>',
    "Kai skill Status panel",
)
html = replace_once(
    html,
    "  const statusList=document.getElementById('characterStatusList');",
    "  const statusList=document.getElementById('characterStatusList');\n  const skillPanel=document.getElementById('characterSkillPanel');",
    "Kai skill panel reference",
)

skill_js = r'''  function renderSkillTable(member){
    if(!skillPanel)return;
    const skills=member&&Array.isArray(member.skills)?member.skills:[];
    if(!skills.length){skillPanel.hidden=true;skillPanel.innerHTML='';return}
    skillPanel.hidden=false;
    const rows=skills.map(skill=>{
      const type=String(skill.category||'').toUpperCase();
      const label=type==='PASSIVE'?'Passive':'Active';
      const cls=type==='PASSIVE'?'skill-passive':'skill-active';
      return '<tr><td><span class="skill-type '+cls+'">'+label+'</span></td><td><strong>'+esc(skill.name||skill.id||'—')+'</strong></td><td class="skill-proc">'+esc(String(skill.procPercent==null?'—':skill.procPercent))+'%</td><td>'+esc(skill.description||'')+'</td></tr>';
    }).join('');
    skillPanel.innerHTML='<div class="skill-panel-head"><strong>SKILLS</strong><span>'+skills.length+' kỹ năng</span></div><div class="skill-table-scroll"><table class="skill-table"><thead><tr><th>Loại</th><th>Skill</th><th>Proc</th><th>Tác dụng</th></tr></thead><tbody>'+rows+'</tbody></table></div>';
  }
'''
html = replace_once(
    html,
    "  function equipmentRows(member){",
    skill_js + "  function equipmentRows(member){",
    "Kai skill table renderer",
)
html = replace_once(
    html,
    "    statusList.innerHTML=statusRows(member).map(row=>'<div class=\"character-status-row '+row[2]+'\"><b>'+esc(row[0])+'</b><span>'+esc(row[1])+'</span></div>').join('');",
    "    statusList.innerHTML=statusRows(member).map(row=>'<div class=\"character-status-row '+row[2]+'\"><b>'+esc(row[0])+'</b><span>'+esc(row[1])+'</span></div>').join('');\n    renderSkillTable(member);",
    "Kai skill table render call",
)

if 'id="kai-skills-status-style"' not in html:
    style = r'''<style id="kai-skills-status-style">
.character-skill-panel{margin-top:14px;border-top:1px solid #303940;padding-top:12px}.skill-panel-head{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:8px}.skill-panel-head strong{font-size:12px;letter-spacing:.12em}.skill-panel-head span{color:#7e8a92;font-size:9px}.skill-table-scroll{overflow-x:auto;border:1px solid #303940}.skill-table{width:100%;border-collapse:collapse;min-width:620px;background:#0a0e11}.skill-table th,.skill-table td{padding:8px 9px;border-bottom:1px solid #252d33;text-align:left;vertical-align:top;font-size:10px;line-height:1.35}.skill-table th{color:#7f8b93;font-size:9px;letter-spacing:.09em;text-transform:uppercase;background:#0f1418}.skill-table tbody tr:last-child td{border-bottom:0}.skill-table td:nth-child(2){min-width:150px}.skill-table td:last-child{color:#bac3c9;min-width:270px}.skill-proc{font-weight:800;white-space:nowrap}.skill-type{display:inline-block;border:1px solid #45515a;padding:2px 5px;font-size:8px;letter-spacing:.07em;text-transform:uppercase;white-space:nowrap}.skill-passive{color:#9eb9c1}.skill-active{color:#d5b48e}@media(max-width:520px){.skill-table th,.skill-table td{padding:7px;font-size:9px}}
</style>'''
    if html.count("</head>") != 1:
        raise RuntimeError("Kai skill Status style: expected exactly one </head>")
    html = html.replace("</head>", style + "\n</head>", 1)

for token in [
    'id="characterSkillPanel"',
    'function renderSkillTable(member)',
    'class="skill-table"',
    'id="kai-skills-status-style"',
]:
    if token not in html:
        raise RuntimeError(f"Kai skill Status contract missing: {token}")
INDEX.write_text(html, encoding="utf-8")

# ---------------------------------------------------------------------------
# 4) Generated regression tests run only after the exact release patch chain has created the skill layer.
# ---------------------------------------------------------------------------
TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KaiSkillCombatGeneratedTest {
  private class SequenceRandom(
    values: List<Double>,
    private val fallback: Double = 0.99
  ) : CombatRandom {
    private val queue = ArrayDeque(values)
    override fun nextDouble(): Double = if (queue.isEmpty()) fallback else queue.removeFirst()
  }

  private fun kai() = CombatantState(
    KAI_ID,
    "Kai",
    false,
    CombatStats(),
    CombatProfiles.partyBaseDamage(KAI_ID)
  )

  @Test fun skillBookMatchesApprovedKaiProcContract() {
    assertEquals(10, KaiSkillBook.allSkills.size)
    assertEquals(50, KaiSkillBook.DEVIL_TRIGGER.procPercent)
    assertEquals(3, KaiSkillBook.DEVIL_TRIGGER_TURNS)
    assertEquals(30, KaiSkillBook.GUILTY_CROWN_OVERRIDE.procPercent)
    assertEquals(24, KaiSkillBook.GUILTY_CROWN_SHOTS)
    assertEquals(96, KaiSkillBook.GUILTY_CROWN_OVERRIDE.baseDamage)
  }

  @Test fun devilTriggerMultipliesCurrentBaseStatsByFive() {
    val base = CombatStats(hpStat = 9, defend = 9, agi = 9, crit = 9, currentHp = 54)
    val boosted = KaiSkillBook.effectiveStats(base, true)
    assertEquals(45, boosted.hpStat)
    assertEquals(45, boosted.defend)
    assertEquals(45, boosted.agi)
    assertEquals(45, boosted.crit)
    assertEquals(90, boosted.maxHp)
    assertEquals(36, KaiSkillBook.devilTriggerHpBonus(base))
  }

  @Test fun devilTriggerExpiresAfterExactlyThreeKaiTurns() {
    val result = AutoTurnCombatEngine(SequenceRandom(listOf(0.99, 0.0))).resolve(
      "DT_3_TURNS",
      listOf(kai()),
      listOf("ENTITY.HOUND"),
      0
    )
    val onIndex = result.timeline.indexOfFirst { it.kind == "DEVIL_TRIGGER_ON" }
    val offIndex = result.timeline.indexOfFirst { it.kind == "DEVIL_TRIGGER_OFF" }
    assertTrue(onIndex >= 0)
    assertTrue(offIndex > onIndex)
    val kaiTurnsThroughOff = result.timeline.take(offIndex + 1).count { it.kind == "FOCUS" && it.actorId == KAI_ID }
    assertEquals(3, kaiTurnsThroughOff)
  }

  @Test fun guiltyCrownCanProcWithoutDevilTrigger() {
    val rolls = listOf(
      0.99, // Combat Analysis
      0.99, // Devil Trigger
      0.99, // Controlled Burst
      0.99, // Weak Point Shot
      0.99, // CQC Break
      0.0   // Guilty Crown
    )
    val result = AutoTurnCombatEngine(SequenceRandom(rolls)).resolve(
      "GCO_INDEPENDENT",
      listOf(kai()),
      listOf("ENTITY.HOUND"),
      0
    )
    assertFalse(result.timeline.any { it.kind == "DEVIL_TRIGGER_ON" })
    assertTrue(result.timeline.any { it.kind == "SKILL" && it.text.contains("[Guilty Crown Override]") })
  }

  @Test fun multipleActiveAttackSkillsCanProcInOneKaiTurn() {
    val rolls = listOf(
      0.99, // Combat Analysis
      0.99, // Devil Trigger
      0.0,  // Controlled Burst proc
      0.0,  // Weak Point Shot proc
      0.99, // CQC Break
      0.99, // Guilty Crown
      0.99, 0.99, 0.99, 0.99, // Controlled: ballistic, mastery, evade, crit
      0.99, 0.99, 0.99, 0.99  // Weak: ballistic, mastery, evade, crit
    )
    val result = AutoTurnCombatEngine(SequenceRandom(rolls)).resolve(
      "MULTI_PROC",
      listOf(kai()),
      listOf("ENTITY.HOUND"),
      0
    )
    val firstFocus = result.timeline.indexOfFirst { it.kind == "FOCUS" && it.actorId == KAI_ID }
    val firstEntityReply = result.timeline.indexOfFirst { it.kind == "ATTACK" && it.actorId == "ENTITY.HOUND" }
    val slice = result.timeline.subList(firstFocus + 1, if (firstEntityReply > firstFocus) firstEntityReply else result.timeline.size)
    assertTrue(slice.any { it.kind == "SKILL" && it.text.contains("[Controlled Burst]") })
    assertTrue(slice.any { it.kind == "SKILL" && it.text.contains("[Weak Point Shot]") })
    assertFalse(slice.any { it.kind == "ATTACK" && it.actorId == KAI_ID && it.text.contains("[Tấn công]") })
  }

  @Test fun characterDetailJsonExposesKaiSkillsOnly() {
    val kaiProjection = CharacterDetailProjector.projectCharacter(GameState.initial(), KAI_ID)!!
    val kaiJson = CharacterDetailJson.encodeCharacter(kaiProjection)
    assertEquals(10, kaiJson.getJSONArray("skills").length())
    val first = kaiJson.getJSONArray("skills").getJSONObject(0)
    assertTrue(first.has("procPercent"))
    assertTrue(first.has("description"))
  }
}
''', encoding="utf-8")

for path, tokens in {
    COMBAT: [
        "KAI_PROC_SKILLS_PATCHED",
        "DEVIL_TRIGGER_STAT_MULTIPLIER = 5",
        "DEVIL_TRIGGER_TURNS = 3",
        '"kai.guilty_crown_override"',
        "activeAttackSkills.filter { proc(it) }",
        "deactivateDevilTrigger",
    ],
    SERIALIZER: ['put("skills"', 'put("procPercent"', 'KaiSkillBook.skillsFor(character.id)'],
    INDEX: ['id="characterSkillPanel"', 'function renderSkillTable(member)', 'skill-table'],
    TEST: ['multipleActiveAttackSkillsCanProcInOneKaiTurn', 'guiltyCrownCanProcWithoutDevilTrigger'],
}.items():
    text = path.read_text(encoding="utf-8")
    for token in tokens:
        if token not in text:
            raise RuntimeError(f"Kai skill final contract missing in {path.name}: {token}")

print("Kai proc skills applied: multi-proc actives, 3-turn x5 Devil Trigger, independent 30% Guilty Crown and Status skill table.")
