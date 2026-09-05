from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatCore.kt"
SERIALIZER = CORE / "CharacterDetailJson.kt"
CONTINUITY = CORE / "StoryCompanionContinuity.kt"
KCE = CORE / "knowledge/KnowledgeContextEngine.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
KNOWLEDGE = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Lucia proc finalizer {label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# 1) Story authority: Lucia remains the story-owned fixed Level-0 contact, but
#    five gameplay turns must complete first. Turn 1 is the initial state, so the
#    first eligible physical action is the action submitted on displayed Turn 6.
#    Level 0 cannot be exited before first contact is committed.
# ---------------------------------------------------------------------------
continuity = CONTINUITY.read_text(encoding="utf-8")
continuity = replace_once(
    continuity,
    '''  const val LUCIA_LEVEL = 0

  @JvmStatic fun isStoryOwned(characterId: String): Boolean = characterId.trim().lowercase() == LUCIA_ID
  @JvmStatic fun randomSpawnAllowed(characterId: String): Boolean = !isStoryOwned(characterId)
  @JvmStatic fun canMaterializeLucia(currentLevel: Int, alreadyEncountered: Boolean): Boolean =
    currentLevel == LUCIA_LEVEL && !alreadyEncountered
''',
    '''  const val LUCIA_LEVEL = 0
  const val LUCIA_MIN_COMPLETED_TURNS = 5

  @JvmStatic fun isStoryOwned(characterId: String): Boolean = characterId.trim().lowercase() == LUCIA_ID
  @JvmStatic fun randomSpawnAllowed(characterId: String): Boolean = !isStoryOwned(characterId)
  @JvmStatic fun canMaterializeLucia(currentLevel: Int, completedTurns: Int, alreadyEncountered: Boolean): Boolean =
    currentLevel == LUCIA_LEVEL && completedTurns >= LUCIA_MIN_COMPLETED_TURNS && !alreadyEncountered
''',
    "StoryCompanionContinuity turn gate",
)
CONTINUITY.write_text(continuity, encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")
main = replace_once(
    main,
    '    rolls.put("luciaEncounter", thresholdRoll("luciaEncounter", 1, 1, level == 0 && physical && !luciaSeen, " story-owned fixed Level 0 contact"));\n',
    '    rolls.put("luciaEncounter", thresholdRoll("luciaEncounter", 1, 1, level == 0 && physical && state.optInt("turn", 1) >= 6 && !luciaSeen, " mandatory after 5 completed turns; story-owned fixed Level 0 contact"));\n',
    "Lucia Turn-6 encounter gate",
)
main = replace_once(
    main,
    '    JSONObject exitProbe = thresholdRoll("exitProbe", 10000, exitThreshold, exitIntent && (physical || search), anNhienFollowing ? " discovery clue +2% An Nhiên" : " discovery clue");\n',
    '    JSONObject exitProbe = thresholdRoll("exitProbe", 10000, exitThreshold, exitIntent && (physical || search) && !(level == 0 && !luciaEncountered(state)), anNhienFollowing ? " discovery clue +2% An Nhiên" : " discovery clue");\n',
    "Level-0 exit probe lock before Lucia",
)
main = replace_once(
    main,
    '''  private boolean canTransition(JSONObject before, JSONObject rolls) {
    JSONObject exploration = before.optJSONObject("flags") != null ? before.optJSONObject("flags").optJSONObject("exploration") : null;
''',
    '''  private boolean canTransition(JSONObject before, JSONObject rolls) {
    if (currentLevel(before) == 0 && !luciaEncountered(before)) return false;
    JSONObject exploration = before.optJSONObject("flags") != null ? before.optJSONObject("flags").optJSONObject("exploration") : null;
''',
    "Level-0 transition lock before Lucia",
)

# Trinh sát chiến trường is Lucia's always-on utility Passive. It adds exactly
# five percentage points to the existing loot roll, preserving An Nhiên's bonus
# if the ultra-rare follower happens to be present. It never creates a second
# roll and never turns discovery into automatic pickup.
main = replace_once(
    main,
    '    rolls.put("loot", thresholdRoll("loot", 10000, Math.min(10000, lootThresholds[level] + (anNhienFollowing ? 1000 : 0)), search, anNhienFollowing ? " +10% An Nhiên" : ""));\n',
    '''    int lootThreshold = Math.min(10000, lootThresholds[level] + (anNhienFollowing ? 1000 : 0));
    boolean luciaScoutFollowing = partyHas(state, "Lucia") || partyHas(state, "Hứa Thuý Mai");
    if (luciaScoutFollowing) lootThreshold = Math.min(10000, lootThreshold + 500);
    String lootSuffix = anNhienFollowing ? " +10% An Nhiên" : "";
    if (luciaScoutFollowing) lootSuffix += " +5pp Lucia battlefield scout";
    rolls.put("loot", thresholdRoll("loot", 10000, lootThreshold, search, lootSuffix));
''',
    "Lucia battlefield-scout loot bonus",
)
MAIN.write_text(main, encoding="utf-8")


# ---------------------------------------------------------------------------
# 2) Lucia proc skill book. Reuse the generated KaiSkillDefinition DTO so the
#    existing Status skill-table serializer remains source-compatible. Lucia has
#    her own book/resolver and stays grounded at HUMAN_TRAINED power scale.
# ---------------------------------------------------------------------------
combat = COMBAT.read_text(encoding="utf-8")
if "KAI_PROC_SKILLS_PATCHED" not in combat:
    raise RuntimeError("Lucia proc finalizer requires the Kai proc skill layer first")

skill_block = r'''// LUCIA_PROC_SKILLS_PATCHED
object LuciaSkillBook {
  val BATTLEFIELD_SCOUT = KaiSkillDefinition(
    "lucia.battlefield_scout", "Trinh sát chiến trường", CombatSkillCategory.PASSIVE, 1.00,
    "Khi Lucia ở Party: +5 điểm phần trăm vào loot roll hiện có; không tạo roll thứ hai và không tự nhặt vật phẩm."
  )
  val FIRE_DISCIPLINE = KaiSkillDefinition(
    "lucia.fire_discipline", "Kỷ luật hỏa lực", CombatSkillCategory.PASSIVE, 0.30,
    "Đầu lượt Lucia: +10% Crit và bỏ qua 15% DF cho mọi đòn của Lucia trong lượt."
  )
  val FULL_AUTO_BURST = KaiSkillDefinition(
    "lucia.m4a1_full_auto_burst", "M4A1 Full Auto Burst", CombatSkillCategory.ACTIVE, 0.20,
    "Chỉ roll ở lượt Lucia thứ 2, 4, 6...; 18 Base DMG, +10% Crit, bỏ qua 10% DF và dùng một Evasion check.",
    attack = true, baseDamage = 18, bonusCritChance = 0.10, defenseIgnore = 0.10, ranged = true
  )
  val TOO_YOUNG_TO_DIE = KaiSkillDefinition(
    "lucia.too_young_to_die", "Too Young To Die", CombatSkillCategory.ACTIVE, 0.15,
    "Proc nền 15% mỗi lượt; dưới 50% HP, mỗi 3 điểm % HP mất thêm cộng +5 điểm % proc, tối đa 100%. Khi kích hoạt: 28 Base DMG, +15% Crit, bỏ qua 20% DF và dùng một Evasion check.",
    attack = true, baseDamage = 28, bonusCritChance = 0.15, defenseIgnore = 0.20, ranged = true
  )

  val passiveSkills = listOf(BATTLEFIELD_SCOUT, FIRE_DISCIPLINE)
  val activeSkills = listOf(FULL_AUTO_BURST, TOO_YOUNG_TO_DIE)
  val allSkills = passiveSkills + activeSkills

  fun skillsFor(characterId: String): List<KaiSkillDefinition> =
    if (characterId.equals("lucia", ignoreCase = true)) allSkills else emptyList()

  fun fullAutoEligible(luciaTurnsTaken: Int): Boolean = luciaTurnsTaken > 0 && luciaTurnsTaken % 2 == 0

  fun tooYoungToDieChance(stats: CombatStats): Double {
    if (stats.maxHp <= 0) return 0.15
    val hpPercent = stats.currentHp * 100.0 / stats.maxHp.toDouble()
    if (hpPercent >= 50.0) return 0.15
    val extraSteps = kotlin.math.floor((50.0 - hpPercent) / 3.0).toInt().coerceAtLeast(0)
    return (0.15 + extraSteps * 0.05).coerceAtMost(1.0)
  }
}

'''
if "LUCIA_PROC_SKILLS_PATCHED" not in combat:
    marker = "class AutoTurnCombatEngine(private val random: CombatRandom = DefaultCombatRandom()) {"
    if combat.count(marker) != 1:
        raise RuntimeError(f"Lucia skill book engine anchor expected once, found {combat.count(marker)}")
    combat = combat.replace(marker, skill_block + marker, 1)

combat = replace_once(
    combat,
    '''    var effects: Map<CombatEffectType, CombatEffect>,
    var devilTriggerTurnsRemaining: Int = 0,
    var devilTriggerHpBonus: Int = 0
''',
    '''    var effects: Map<CombatEffectType, CombatEffect>,
    var devilTriggerTurnsRemaining: Int = 0,
    var devilTriggerHpBonus: Int = 0,
    var luciaTurnsTaken: Int = 0
''',
    "Lucia combat-turn counter",
)
combat = replace_once(
    combat,
    '''    fun isKai(): Boolean = !isEntity && id.equals(KAI_ID, ignoreCase = true)
    fun devilTriggerActive(): Boolean = isKai() && devilTriggerTurnsRemaining > 0
''',
    '''    fun isKai(): Boolean = !isEntity && id.equals(KAI_ID, ignoreCase = true)
    fun isLucia(): Boolean = !isEntity && id.equals("lucia", ignoreCase = true)
    fun devilTriggerActive(): Boolean = isKai() && devilTriggerTurnsRemaining > 0
''',
    "Lucia fighter identity",
)
combat = replace_once(
    combat,
    '''  private data class KaiTurnBuff(
    var critBonus: Double = 0.0,
    var defenseIgnore: Double = 0.0
  )
''',
    '''  private data class KaiTurnBuff(
    var critBonus: Double = 0.0,
    var defenseIgnore: Double = 0.0
  )

  private data class LuciaTurnBuff(
    var critBonus: Double = 0.0,
    var defenseIgnore: Double = 0.0
  )
''',
    "Lucia turn buff",
)
combat = replace_once(
    combat,
    '''      } else if (actor.isKai()) {
        resolveKaiAction(actor, enemy, timeline)
      } else {
        attack(actor, enemy, timeline)
      }
''',
    '''      } else if (actor.isKai()) {
        resolveKaiAction(actor, enemy, timeline)
      } else if (actor.isLucia()) {
        resolveLuciaAction(actor, enemy, timeline)
      } else {
        attack(actor, enemy, timeline)
      }
''',
    "Lucia auto-turn resolver",
)

lucia_resolver = r'''  private fun resolveLuciaAction(
    actor: MutableFighter,
    target: MutableFighter,
    timeline: MutableList<CombatTimelineEvent>
  ) {
    actor.luciaTurnsTaken++
    val buff = LuciaTurnBuff()
    if (proc(LuciaSkillBook.FIRE_DISCIPLINE)) {
      buff.critBonus += 0.10
      buff.defenseIgnore += 0.15
      timeline += CombatTimelineEvent(
        "PASSIVE",
        actorId = actor.id,
        enemyId = target.id,
        text = "${actor.name} kích hoạt [Kỷ luật hỏa lực]."
      )
    }

    // Every eligible Active rolls independently before resolution. Multiple
    // successes can therefore form one Lucia combo, matching the current proc
    // contract. If no Active succeeds, Lucia falls back to Basic Attack.
    val triggered = mutableListOf<KaiSkillDefinition>()
    if (LuciaSkillBook.fullAutoEligible(actor.luciaTurnsTaken) && proc(LuciaSkillBook.FULL_AUTO_BURST)) {
      triggered += LuciaSkillBook.FULL_AUTO_BURST
    }
    if (random.nextDouble() < LuciaSkillBook.tooYoungToDieChance(actor.stats)) {
      triggered += LuciaSkillBook.TOO_YOUNG_TO_DIE
    }

    if (triggered.isEmpty()) {
      resolveLuciaAttack(actor, target, null, buff, timeline)
      return
    }
    triggered.forEach { skill ->
      if (target.alive()) resolveLuciaAttack(actor, target, skill, buff, timeline)
    }
  }

  private fun resolveLuciaAttack(
    attacker: MutableFighter,
    target: MutableFighter,
    skill: KaiSkillDefinition?,
    buff: LuciaTurnBuff,
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
    val criticalText = if (critical) " CRITICAL!" else ""
    timeline += CombatTimelineEvent(
      if (skill == null) "ATTACK" else "SKILL",
      actorId = attacker.id,
      targetId = target.id,
      enemyId = target.id,
      text = "${attacker.name} dùng [$displayName] lên ${target.name} →$criticalText ${target.name} -$damage HP."
    )
  }

'''
if lucia_resolver not in combat:
    marker = "  private fun resolveKaiAttack(\n"
    if combat.count(marker) != 1:
        raise RuntimeError(f"Lucia action insertion anchor expected once, found {combat.count(marker)}")
    combat = combat.replace(marker, lucia_resolver + marker, 1)

for marker in [
    "LUCIA_PROC_SKILLS_PATCHED",
    "object LuciaSkillBook",
    "fun isLucia()",
    "resolveLuciaAction(actor, enemy, timeline)",
    "LuciaSkillBook.fullAutoEligible(actor.luciaTurnsTaken)",
    "LuciaSkillBook.tooYoungToDieChance(actor.stats)",
]:
    if marker not in combat:
        raise RuntimeError(f"Lucia proc combat marker missing: {marker}")
COMBAT.write_text(combat, encoding="utf-8")


# ---------------------------------------------------------------------------
# 3) Existing character skill table is generic enough for Lucia. Extend the
#    generated serializer without changing UI or save schema.
# ---------------------------------------------------------------------------
serializer = SERIALIZER.read_text(encoding="utf-8")
serializer = replace_once(
    serializer,
    '    val skills = KaiSkillBook.skillsFor(character.id)\n',
    '    val skills = KaiSkillBook.skillsFor(character.id) + LuciaSkillBook.skillsFor(character.id)\n',
    "Lucia skill serialization",
)
SERIALIZER.write_text(serializer, encoding="utf-8")


# ---------------------------------------------------------------------------
# 4) Knowledge routing: record only the approved proc mechanics and fixed
#    encounter timing. Lucia remains a trained human, not a supernatural unit.
# ---------------------------------------------------------------------------
db = json.loads(KNOWLEDGE.read_text(encoding="utf-8"))
records = db.get("records")
if not isinstance(records, list):
    raise RuntimeError("Lucia proc finalizer: knowledge records missing")
by_id = {record.get("id"): record for record in records if isinstance(record, dict)}
story = by_id.get("STORY.LUCIA.LEVEL0_FIXED_ENCOUNTER")
if story is None:
    raise RuntimeError("Lucia proc finalizer: fixed encounter knowledge record missing")
story["text"] = (
    "Lucia is a mandatory fixed, story-owned Level 0 encounter. Five gameplay turns must complete first; "
    "the first eligible physical action is displayed Turn 6. It requires no quest and uses no random spawn. Level 0 exit discovery "
    "and transition remain locked until Core commits Lucia first contact. LiteRT/Gemini must not roll, teleport or invent her independently. "
    "First contact does not automatically add her to Party."
)

skill_record = {
    "id": "CHAR.LUCIA.SKILLS_PROC",
    "domain": "CHARACTER",
    "kind": "ability",
    "text": (
        "Lucia proc skill book is grounded human rifle combat. Trinh sát chiến trường is always-on while Lucia is in Party and adds "
        "+5 percentage points to the existing loot roll only. Kỷ luật hỏa lực has 30% proc at the start of Lucia's turn and gives +10% Crit "
        "plus 15% DF ignore for that turn. M4A1 Full Auto Burst has 20% proc only on Lucia turns 2/4/6/... and resolves as 18 Base DMG, "
        "+10% Crit, 10% DF ignore, one Evasion check. Too Young To Die has 15% baseline proc; below 50% HP, each additional 3 percentage "
        "points of HP lost adds +5 percentage points proc up to 100%; on activation it resolves as 28 Base DMG, +15% Crit, 20% DF ignore, one Evasion check. "
        "Eligible Active procs are independent and may combo; if none proc Lucia uses Basic Attack. No skill grants supernatural power."
    ),
    "source": {"document": "USER_RETCON_2026-09-05", "anchor": "Lucia proc Passive/Active + mandatory encounter after 5 turns"},
    "authority": "USER_RETCON",
    "mutability": "IMMUTABLE",
    "priority": 50,
    "tags": ["lucia", "skill", "proc", "m4a1", "too young to die", "battlefield scout"],
    "references": ["CHAR.LUCIA.RUNTIME_CORE", "CHAR.LUCIA.M4A1"],
    "affordances": ["direct_threat"]
}
if skill_record["id"] in by_id:
    by_id[skill_record["id"]].clear()
    by_id[skill_record["id"]].update(skill_record)
else:
    records.append(skill_record)
KNOWLEDGE.write_text(json.dumps(db, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

kce = KCE.read_text(encoding="utf-8")
kce = replace_once(
    kce,
    '      if ("lucia" in presentActors) add("CHAR.LUCIA.RUNTIME_CORE", "present actor runtime core")\n',
    '      if ("lucia" in presentActors) { add("CHAR.LUCIA.RUNTIME_CORE", "present actor runtime core"); add("CHAR.LUCIA.SKILLS_PROC", "Lucia proc skill book") }\n',
    "Lucia proc knowledge routing",
)
KCE.write_text(kce, encoding="utf-8")


# ---------------------------------------------------------------------------
# 5) Regression coverage: story turn gate, proc catalog/dynamic chance and actual
#    auto-turn execution. The engine test uses deterministic zero RNG: Lucia
#    procs always succeed while Evasion keeps the Entity alive long enough for
#    her second combat turn and Full Auto eligibility.
# ---------------------------------------------------------------------------
r07_test = TESTS / "CharacterCanonR07Test.kt"
r07 = r07_test.read_text(encoding="utf-8")
r07 = replace_once(
    r07,
    '''    assertTrue(StoryCompanionContinuity.canMaterializeLucia(0, false))
    assertFalse(StoryCompanionContinuity.canMaterializeLucia(1, false))
    assertFalse(StoryCompanionContinuity.canMaterializeLucia(0, true))
''',
    '''    assertFalse(StoryCompanionContinuity.canMaterializeLucia(0, 4, false))
    assertTrue(StoryCompanionContinuity.canMaterializeLucia(0, 5, false))
    assertFalse(StoryCompanionContinuity.canMaterializeLucia(1, 5, false))
    assertFalse(StoryCompanionContinuity.canMaterializeLucia(0, 5, true))
''',
    "R07 Lucia turn-gate test",
)
r07_test.write_text(r07, encoding="utf-8")

(TESTS / "LuciaProcSkillGeneratedTest.kt").write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuciaProcSkillGeneratedTest {
  private class SequenceRandom(
    values: List<Double>,
    private val fallback: Double = 0.99
  ) : CombatRandom {
    private val queue = ArrayDeque(values)
    override fun nextDouble(): Double = if (queue.isEmpty()) fallback else queue.removeFirst()
  }

  private fun kaiStunned() = CombatantState(
    KAI_ID,
    "Kai",
    false,
    CombatStats(),
    CombatProfiles.partyBaseDamage(KAI_ID),
    effects = mapOf(CombatEffectType.STUN to CombatEffect(CombatEffectType.STUN, 100))
  )

  private fun lucia() = CombatantState(
    "lucia",
    "Lucia Lục",
    false,
    CombatStats(currentHp = 20),
    CombatProfiles.partyBaseDamage("lucia")
  )

  @Test fun luciaSkillBookMatchesProcContract() {
    assertEquals(4, LuciaSkillBook.allSkills.size)
    assertEquals(2, LuciaSkillBook.passiveSkills.size)
    assertEquals(2, LuciaSkillBook.activeSkills.size)
    assertEquals(100, LuciaSkillBook.BATTLEFIELD_SCOUT.procPercent)
    assertEquals(30, LuciaSkillBook.FIRE_DISCIPLINE.procPercent)
    assertEquals(20, LuciaSkillBook.FULL_AUTO_BURST.procPercent)
    assertEquals(15, LuciaSkillBook.TOO_YOUNG_TO_DIE.procPercent)
  }

  @Test fun fullAutoOnlyBecomesEligibleEverySecondLuciaTurn() {
    assertFalse(LuciaSkillBook.fullAutoEligible(1))
    assertTrue(LuciaSkillBook.fullAutoEligible(2))
    assertFalse(LuciaSkillBook.fullAutoEligible(3))
    assertTrue(LuciaSkillBook.fullAutoEligible(4))
  }

  @Test fun tooYoungToDieUsesApprovedLowHpRamp() {
    fun stats(percent: Int) = CombatStats(hpStat = 55, currentHp = percent)
    assertEquals(0.15, LuciaSkillBook.tooYoungToDieChance(stats(49)), 0.0001)
    assertEquals(0.20, LuciaSkillBook.tooYoungToDieChance(stats(47)), 0.0001)
    assertEquals(0.25, LuciaSkillBook.tooYoungToDieChance(stats(44)), 0.0001)
    assertTrue(LuciaSkillBook.tooYoungToDieChance(stats(1)) <= 1.0)
  }

  @Test fun actualAutoTurnEngineCanProcLuciaPassiveAndBothActives() {
    val random = SequenceRandom(emptyList(), fallback = 0.0)
    val result = AutoTurnCombatEngine(random).resolve(
      encounterId = "TURN_6",
      partyInput = listOf(kaiStunned(), lucia()),
      entityIds = listOf("ENTITY.SLENDERMAN"),
      level = 0
    )
    val text = result.timeline.joinToString("\n") { it.text }
    assertTrue(text.contains("[Kỷ luật hỏa lực]"))
    assertTrue(text.contains("[Too Young To Die]"))
    assertTrue(text.contains("[M4A1 Full Auto Burst]"))
  }
}
''', encoding="utf-8")

for required in [
    'state.optInt("turn", 1) >= 6',
    '!(level == 0 && !luciaEncountered(state))',
    'currentLevel(before) == 0 && !luciaEncountered(before)',
    'lootThreshold + 500',
]:
    if required not in MAIN.read_text(encoding="utf-8"):
        raise RuntimeError(f"Lucia final runtime contract missing: {required}")

print("Lucia finalized on latest runtime: mandatory after five completed turns, +5pp scout Passive and proc-based rifle skills.")
