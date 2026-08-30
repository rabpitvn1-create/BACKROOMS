from pathlib import Path

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/KaiDevilWithinEntityTest.kt"
ASSET = ROOT / "app/src/main/assets/entity/Kai-TheDevilWithin.png"


def once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one anchor, found {count}")
    return text.replace(old, new, 1)


if not ASSET.is_file() or ASSET.stat().st_size <= 0:
    raise RuntimeError("Kai - The Devil Within display asset is missing or empty")

combat = COMBAT.read_text(encoding="utf-8")
main = MAIN.read_text(encoding="utf-8")

constants_anchor = '  private const val VIOLET_WARDEN_KEY = "violet_warden"\n'
constants = '''  private const val KAI_DEVIL_WITHIN_KEY = "kai_the_devil_within"
  private const val KAI_DEVIL_WITHIN_MAX_HP = 5678
  private const val KAI_DEVIL_WITHIN_STAT_PERCENT = 70
  private const val KAI_DEVIL_WITHIN_SPARDA_PROC_PERCENT = 20
  private const val KAI_DEVIL_WITHIN_SPARDA_COOLDOWN = 3
  private const val KAI_DEVIL_WITHIN_RED_ROSARY_PROC_PERCENT = 10
  private const val KAI_DEVIL_WITHIN_RED_ROSARY_COOLDOWN = 3
  private const val KAI_DEVIL_WITHIN_RED_ROSARY_ROUNDS = 13
  private const val KAI_DEVIL_WITHIN_RED_ROSARY_DAMAGE_PERCENT = 70
  private const val KAI_DEVIL_WITHIN_DEAD_SILENCE_PROC_PERCENT = 10
  private const val KAI_DEVIL_WITHIN_DEAD_SILENCE_COOLDOWN = 3
  private const val KAI_DEVIL_WITHIN_DEAD_SILENCE_BLEED_PERCENT = 3
  private const val KAI_DEVIL_WITHIN_DEAD_SILENCE_BLEED_TURNS = 3
  private const val KAI_DEVIL_WITHIN_GUNSLINGER_PROC_PERCENT = 10
  private const val KAI_DEVIL_WITHIN_GUNSLINGER_COOLDOWN = 5
  private const val KAI_DEVIL_WITHIN_GUNSLINGER_DAMAGE_PERCENT = 5
  private const val KAI_DEVIL_WITHIN_REGEN_PERCENT = 5
  private const val KAI_DEVIL_WITHIN_STUN_TARGET_KEY = "combat.kaiDevilWithin.stunTarget"
  private const val KAI_DEVIL_WITHIN_STUN_UNTIL_KEY = "combat.kaiDevilWithin.stunUntilEvent"
  private const val KAI_DEVIL_WITHIN_BLEED_TARGET_KEY = "combat.kaiDevilWithin.bleedTarget"
  private const val KAI_DEVIL_WITHIN_BLEED_TURNS_KEY = "combat.kaiDevilWithin.bleedTurns"
'''
combat = once(combat, constants_anchor, constants + constants_anchor, "Devil Within constants")

profile_anchor = '    Profile(VIOLET_WARDEN_KEY, "The Violet Warden", VIOLET_WARDEN_MAX_HP, 0, 9, 9)\n'
profile_new = '    Profile(VIOLET_WARDEN_KEY, "The Violet Warden", VIOLET_WARDEN_MAX_HP, 0, 9, 9),\n    Profile(KAI_DEVIL_WITHIN_KEY, "Kai - The Devil Within", KAI_DEVIL_WITHIN_MAX_HP, 0, 0, 0)\n'
combat = once(combat, profile_anchor, profile_new, "Devil Within profile")

profile_lookup = '    val profile = profiles[current.entityKey] ?: return Resolution(clear(state), handled = false)\n'
profile_dynamic = '''    val baseProfile = profiles[current.entityKey] ?: return Resolution(clear(state), handled = false)
    val profile = if (current.entityKey == KAI_DEVIL_WITHIN_KEY) {
      val kaiStats = CharacterStatEngine.effective(state, KAI_ID)
      baseProfile.copy(
        attack = max(1, CharacterStatEngine.weaponDamage(state, KAI_ID) * KAI_DEVIL_WITHIN_STAT_PERCENT / 100),
        armor = max(0, kaiStats.df * KAI_DEVIL_WITHIN_STAT_PERCENT / 100),
        aggression = max(1, kaiStats.agi * KAI_DEVIL_WITHIN_STAT_PERCENT / 100)
      )
    } else baseProfile
'''
combat = once(combat, profile_lookup, profile_dynamic, "dynamic 70 percent profile")

intent_anchor = '    val intent = if (monsterXPartyStunned || johnDoeTargetStunned || scp173TargetStunned) Intent.OTHER else requestedIntent\n'
intent_new = '    val devilWithinKaiStunned = current.entityKey == KAI_DEVIL_WITHIN_KEY && kaiDevilWithinActionLocked(state, KAI_ID)\n    val intent = if (monsterXPartyStunned || johnDoeTargetStunned || scp173TargetStunned || devilWithinKaiStunned) Intent.OTHER else requestedIntent\n'
combat = once(combat, intent_anchor, intent_new, "Devil Within Kai stun gate")

hp_anchor = 'val balancedEntityBaseHp = when (profile.key) { DIEP_MINH_KEY -> DIEP_MINH_MAX_HP; MONSTER_X_KEY -> MONSTER_X_MAX_HP; JOHN_DOE_KEY -> JOHN_DOE_MAX_HP; SCP_173_KEY -> SCP_173_MAX_HP; VIOLET_WARDEN_KEY -> VIOLET_WARDEN_MAX_HP; JEFF_KEY, JANE_KEY -> profile.maxHp; else -> profile.maxHp + ENTITY_HP_BONUS }'
hp_new = 'val balancedEntityBaseHp = when (profile.key) { DIEP_MINH_KEY -> DIEP_MINH_MAX_HP; MONSTER_X_KEY -> MONSTER_X_MAX_HP; JOHN_DOE_KEY -> JOHN_DOE_MAX_HP; SCP_173_KEY -> SCP_173_MAX_HP; VIOLET_WARDEN_KEY -> VIOLET_WARDEN_MAX_HP; KAI_DEVIL_WITHIN_KEY -> KAI_DEVIL_WITHIN_MAX_HP; JEFF_KEY, JANE_KEY -> profile.maxHp; else -> profile.maxHp + ENTITY_HP_BONUS }'
if combat.count(hp_anchor) != 2:
    raise RuntimeError(f"exact Devil Within HP: expected two anchors, found {combat.count(hp_anchor)}")
combat = combat.replace(hp_anchor, hp_new)
bonus_anchor = '    val enhancedEntityMaxHp = balancedEntityBaseHp + if (balancedEntityBaseHp > 1000) 200 else 0\n'
bonus_new = '    val enhancedEntityMaxHp = balancedEntityBaseHp + if (balancedEntityBaseHp > 1000 && profile.key != KAI_DEVIL_WITHIN_KEY) 200 else 0\n'
combat = once(combat, bonus_anchor, bonus_new, "disable high HP bonus")
canonical_bonus_anchor = '    val canonicalMaxHp = balancedEntityBaseHp + if (balancedEntityBaseHp > 1000) 200 else 0\n'
canonical_bonus_new = '    val canonicalMaxHp = balancedEntityBaseHp + if (balancedEntityBaseHp > 1000 && profile.key != KAI_DEVIL_WITHIN_KEY) 200 else 0\n'
combat = once(combat, canonical_bonus_anchor, canonical_bonus_new, "disable decoded high HP bonus")

resolve_state_anchor = '    var resolvedState = scp173PreparedState\n'
resolve_state_new = '''    var resolvedState = scp173PreparedState
    val devilWithinTurn = current.entityKey == KAI_DEVIL_WITHIN_KEY
    val devilWithinSpardaActive = devilWithinTurn &&
      killerSkillReady(resolvedState, "kai_devil_within.spardas_son", c.eventCounter) &&
      roll(c.copy(eventCounter = c.eventCounter + 1701), 100) < KAI_DEVIL_WITHIN_SPARDA_PROC_PERCENT
    if (devilWithinSpardaActive) {
      resolvedState = useKillerSkill(resolvedState, "kai_devil_within.spardas_son", c.eventCounter, KAI_DEVIL_WITHIN_SPARDA_COOLDOWN)
      log += "Sparda's Son kích hoạt: mọi đòn của Party trừ Kai chỉ còn 50% damage; Kai - The Devil Within nhận thêm một hành động trong Entity turn."
    }
    val devilWithinHpAtTurnStart = c.entityHp
    var devilWithinKaiDamage = 0
    var devilWithinSpardaRestored = 0
    fun applyDevilWithinSpardaReduction() {
      if (!devilWithinSpardaActive) return
      val rawOutgoingDamage = max(0, devilWithinHpAtTurnStart - c.entityHp + devilWithinSpardaRestored)
      val nonKaiDamage = max(0, rawOutgoingDamage - devilWithinKaiDamage)
      val desiredRestoration = (nonKaiDamage + 1) / 2
      val additionalRestoration = max(0, desiredRestoration - devilWithinSpardaRestored)
      if (additionalRestoration > 0) {
        val hp = min(c.entityMaxHp, c.entityHp + additionalRestoration)
        val applied = hp - c.entityHp
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
        devilWithinSpardaRestored += applied
        log += "Sparda's Son triệt tiêu $applied damage từ các thành viên không phải Kai."
      }
    }
    val devilWithinHpBeforeKaiAction = c.entityHp
'''
combat = once(combat, resolve_state_anchor, resolve_state_new, "Sparda turn setup")

lucia_anchor = '        // LUCIA_JOINT_ATTACK: process the follower when the player explicitly orders both attackers.\n'
lucia_new = '        if (devilWithinTurn) devilWithinKaiDamage += max(0, devilWithinHpBeforeKaiAction - c.entityHp)\n' + lucia_anchor
combat = once(combat, lucia_anchor, lucia_new, "track Kai direct damage")

bleed_anchor = '    if (c.entityHp > 0 && bleedTurns > 0) {\n'
bleed_new = '    val devilWithinHpBeforeKaiBleed = c.entityHp\n' + bleed_anchor
combat = once(combat, bleed_anchor, bleed_new, "track Kai bleed start")
syvial_bleed_anchor = '    if (c.entityHp > 0 && syvialBleedTurns > 0) {\n'
syvial_bleed_new = '    if (devilWithinTurn) devilWithinKaiDamage += max(0, devilWithinHpBeforeKaiBleed - c.entityHp)\n\n' + syvial_bleed_anchor
combat = once(combat, syvial_bleed_anchor, syvial_bleed_new, "track Kai bleed end")

gco_hp = '      val hp = max(0, c.entityHp - appliedTotalDamage)\n      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))\n'
gco_new = '      val hpBeforeGuiltyCrown = c.entityHp\n      val hp = max(0, c.entityHp - appliedTotalDamage)\n      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))\n      if (devilWithinTurn) devilWithinKaiDamage += max(0, hpBeforeGuiltyCrown - hp)\n'
combat = once(combat, gco_hp, gco_new, "track Guilty Crown damage")

kai_skills_anchor = '    if (c.entityHp > 0) {\n      val weaponDamage = CharacterStatEngine.weaponDamage(resolvedState, KAI_ID)\n'
kai_skills_new = '    val devilWithinHpBeforeKaiSkills = c.entityHp\n' + kai_skills_anchor
combat = once(combat, kai_skills_anchor, kai_skills_new, "track Kai skills start")
companion_anchor = '    // COMPANION_SKILLS_R01: Iris, Syvial and An Nhien wrap the finalized combat response.\n'
companion_new = '    if (devilWithinTurn) devilWithinKaiDamage += max(0, devilWithinHpBeforeKaiSkills - c.entityHp)\n\n' + companion_anchor
combat = once(combat, companion_anchor, companion_new, "track Kai skills end")

# Apply the reduction before every possible defeat exit, including early Party damage exits.
death_anchor = '    if (c.entityHp <= 0) {\n'
death_count = combat.count(death_anchor)
if death_count < 3:
    raise RuntimeError(f"Sparda defeat gates: expected at least three, found {death_count}")
combat = combat.replace(death_anchor, '    applyDevilWithinSpardaReduction()\n    if (c.entityHp <= 0) {\n')

# The generated runtime has a defeat gate immediately after Kai's automatic skill block. Move the
# Kai-damage checkpoint in front of that gate so Sparda never halves Kai's own skill damage.
kai_skill_tracker = '    if (devilWithinTurn) devilWithinKaiDamage += max(0, devilWithinHpBeforeKaiSkills - c.entityHp)\n\n'
tracker_index = combat.find(kai_skill_tracker)
if tracker_index < 0:
    raise RuntimeError("Kai skill damage tracker missing after defeat-gate insertion")
combat = combat[:tracker_index] + combat[tracker_index + len(kai_skill_tracker):]
gate_index = combat.rfind('    applyDevilWithinSpardaReduction()\n', 0, tracker_index)
if gate_index < 0:
    raise RuntimeError("Kai skill defeat gate missing before companion skills")
combat = combat[:gate_index] + kai_skill_tracker + combat[gate_index:]

enemy_anchor = '    } else if (c.entityKey == VIOLET_WARDEN_KEY) {\n'
devil_enemy = '''    } else if (c.entityKey == KAI_DEVIL_WITHIN_KEY) {
      val targets = entityCombatActionTargets(resolvedState)
      val bleedTargetId = resolvedState.metadata[KAI_DEVIL_WITHIN_BLEED_TARGET_KEY].orEmpty()
      var bleedTurnsLeft = resolvedState.metadata[KAI_DEVIL_WITHIN_BLEED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, KAI_DEVIL_WITHIN_DEAD_SILENCE_BLEED_TURNS) ?: 0
      if (bleedTargetId.isNotBlank() && bleedTurnsLeft > 0) {
        val target = resolvedState.characters[bleedTargetId]
        if (target != null && target.vitalState.currentHp > 0) {
          val maxHp = CharacterStatEngine.effective(resolvedState, bleedTargetId).maxHp
          val before = target.vitalState.currentHp.coerceIn(0, maxHp)
          val damage = min(before, percentDamage(maxHp, KAI_DEVIL_WITHIN_DEAD_SILENCE_BLEED_PERCENT))
          resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, bleedTargetId, before - damage)
          bleedTurnsLeft--
          resolvedState = withCombatCounter(resolvedState, KAI_DEVIL_WITHIN_BLEED_TURNS_KEY, bleedTurnsLeft)
          log += "Dead Silence Bleeding: ${target.name} -$damage HP (${KAI_DEVIL_WITHIN_DEAD_SILENCE_BLEED_PERCENT}% Max HP); còn $bleedTurnsLeft lượt."
        }
      }

      if (targets.isEmpty()) {
        log += "Kai - The Devil Within không còn mục tiêu chiến đấu hợp lệ."
      } else {
        val kaiStats = CharacterStatEngine.effective(resolvedState, KAI_ID)
        val baseDamage = max(1, CharacterStatEngine.weaponDamage(resolvedState, KAI_ID) * KAI_DEVIL_WITHIN_STAT_PERCENT / 100)
        val critChance = max(0, CombatStatMath.critChancePercent(kaiStats.crit) * KAI_DEVIL_WITHIN_STAT_PERCENT / 100)
        val primaryId = targets[(c.eventCounter + targets.size) % targets.size]

        fun hitTarget(targetId: String, requested: Int, label: String, seedOffset: Int) {
          val target = resolvedState.characters[targetId] ?: return
          val maxHp = CharacterStatEngine.effective(resolvedState, targetId).maxHp
          val before = target.vitalState.currentHp.coerceIn(0, maxHp)
          if (before <= 0) return
          val critical = roll(c.copy(eventCounter = c.eventCounter + seedOffset), 100) < critChance
          val damage = min(before, if (critical) requested * 3 / 2 else requested)
          resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, targetId, before - damage)
          val after = resolvedState.characters[targetId]?.vitalState?.currentHp ?: max(0, before - damage)
          if (targetId == KAI_ID) c = c.copy(playerHp = after, playerMaxHp = maxHp)
          log += "$label: ${target.name} -$damage HP${if (critical) " (CRITICAL)" else ""} ($after/$maxHp)."
        }

        val redRosary = killerSkillReady(resolvedState, "kai_devil_within.red_rosary", c.eventCounter) &&
          roll(c.copy(eventCounter = c.eventCounter + 1721), 100) < KAI_DEVIL_WITHIN_RED_ROSARY_PROC_PERCENT
        val deadSilence = !redRosary && killerSkillReady(resolvedState, "kai_devil_within.dead_silence", c.eventCounter) &&
          roll(c.copy(eventCounter = c.eventCounter + 1741), 100) < KAI_DEVIL_WITHIN_DEAD_SILENCE_PROC_PERCENT
        val gunslinger = !redRosary && !deadSilence && killerSkillReady(resolvedState, "kai_devil_within.gunslinger", c.eventCounter) &&
          roll(c.copy(eventCounter = c.eventCounter + 1753), 100) < KAI_DEVIL_WITHIN_GUNSLINGER_PROC_PERCENT

        when {
          redRosary -> {
            val perRound = max(1, baseDamage * KAI_DEVIL_WITHIN_RED_ROSARY_DAMAGE_PERCENT / 100)
            hitTarget(primaryId, perRound * KAI_DEVIL_WITHIN_RED_ROSARY_ROUNDS, "Red Rosary — ${KAI_DEVIL_WITHIN_RED_ROSARY_ROUNDS} viên x 0.7 Base DMG", 1777)
            resolvedState = withCombatText(resolvedState, KAI_DEVIL_WITHIN_STUN_TARGET_KEY, primaryId)
            resolvedState = withCombatCounter(resolvedState, KAI_DEVIL_WITHIN_STUN_UNTIL_KEY, c.eventCounter + 1)
            resolvedState = useKillerSkill(resolvedState, "kai_devil_within.red_rosary", c.eventCounter, KAI_DEVIL_WITHIN_RED_ROSARY_COOLDOWN)
            log += "Red Rosary gây STUN 1 lượt cho ${resolvedState.characters[primaryId]?.name ?: primaryId}."
          }
          deadSilence -> {
            hitTarget(primaryId, baseDamage, "Dead Silence bắn trúng điểm yếu", 1789)
            resolvedState = withCombatText(resolvedState, KAI_DEVIL_WITHIN_BLEED_TARGET_KEY, primaryId)
            resolvedState = withCombatCounter(resolvedState, KAI_DEVIL_WITHIN_BLEED_TURNS_KEY, KAI_DEVIL_WITHIN_DEAD_SILENCE_BLEED_TURNS)
            resolvedState = useKillerSkill(resolvedState, "kai_devil_within.dead_silence", c.eventCounter, KAI_DEVIL_WITHIN_DEAD_SILENCE_COOLDOWN)
            log += "Dead Silence gây Bleeding ${KAI_DEVIL_WITHIN_DEAD_SILENCE_BLEED_TURNS} lượt x ${KAI_DEVIL_WITHIN_DEAD_SILENCE_BLEED_PERCENT}% Max HP."
          }
          gunslinger -> {
            targets.forEachIndexed { index, targetId ->
              val maxHp = CharacterStatEngine.effective(resolvedState, targetId).maxHp
              hitTarget(targetId, percentDamage(maxHp, KAI_DEVIL_WITHIN_GUNSLINGER_DAMAGE_PERCENT), "Gunslinger", 1801 + index * 17)
            }
            resolvedState = useKillerSkill(resolvedState, "kai_devil_within.gunslinger", c.eventCounter, KAI_DEVIL_WITHIN_GUNSLINGER_COOLDOWN)
            log += "Gunslinger quét toàn bộ mục tiêu, mỗi mục tiêu chịu ${KAI_DEVIL_WITHIN_GUNSLINGER_DAMAGE_PERCENT}% Max HP."
          }
          else -> hitTarget(primaryId, baseDamage, "Kai - The Devil Within tấn công", 1831)
        }

        if (devilWithinSpardaActive) {
          val extraTargets = entityCombatActionTargets(resolvedState)
          val extraId = extraTargets.firstOrNull()
          if (extraId != null) hitTarget(extraId, baseDamage, "Sparda's Son — hành động thêm", 1861)
        }
      }

      if (c.entityHp > 0 && c.eventCounter % 2 == 0) {
        val heal = percentDamage(c.entityMaxHp, KAI_DEVIL_WITHIN_REGEN_PERCENT)
        val hp = min(c.entityMaxHp, c.entityHp + heal)
        log += "Nội tại Quỷ giới hồi +${hp - c.entityHp} HP (${KAI_DEVIL_WITHIN_REGEN_PERCENT}% Max HP; $hp/${c.entityMaxHp})."
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      }
'''
combat = once(combat, enemy_anchor, devil_enemy + enemy_anchor, "Devil Within enemy turn")

active_anchor = '    if (characterId !in state.party.memberIds) return null\n    if (violetWardenActionLocked(state, characterId)) return null\n'
active_new = '    if (characterId !in state.party.memberIds) return null\n    if (violetWardenActionLocked(state, characterId) || kaiDevilWithinActionLocked(state, characterId)) return null\n'
combat = once(combat, active_anchor, active_new, "Devil Within stun action lock")

helper_anchor = '  private fun companionSkillDamage(weaponDamage: Int, percent: Int, armor: Int): Int {\n'
helpers = '''  private fun kaiDevilWithinActionLocked(state: GameState, characterId: String): Boolean {
    if (state.metadata["combat.entityKey"] != KAI_DEVIL_WITHIN_KEY) return false
    if (state.metadata[KAI_DEVIL_WITHIN_STUN_TARGET_KEY] != characterId) return false
    val currentEvent = state.metadata["combat.eventCounter"]?.toIntOrNull() ?: 0
    return currentEvent < (state.metadata[KAI_DEVIL_WITHIN_STUN_UNTIL_KEY]?.toIntOrNull() ?: 0)
  }

'''
combat = once(combat, helper_anchor, helpers + helper_anchor, "Devil Within stun helper")

for marker in (
    'KAI_DEVIL_WITHIN_MAX_HP = 5678',
    'KAI_DEVIL_WITHIN_STAT_PERCENT = 70',
    'KAI_DEVIL_WITHIN_SPARDA_PROC_PERCENT = 20',
    'KAI_DEVIL_WITHIN_RED_ROSARY_ROUNDS = 13',
    'KAI_DEVIL_WITHIN_DEAD_SILENCE_BLEED_TURNS = 3',
    'KAI_DEVIL_WITHIN_GUNSLINGER_DAMAGE_PERCENT = 5',
    'KAI_DEVIL_WITHIN_REGEN_PERCENT = 5',
    'Profile(KAI_DEVIL_WITHIN_KEY, "Kai - The Devil Within"',
    'Sparda\'s Son — hành động thêm',
    'Red Rosary — ${KAI_DEVIL_WITHIN_RED_ROSARY_ROUNDS} viên x 0.7 Base DMG',
    'Dead Silence gây Bleeding',
    'Gunslinger quét toàn bộ mục tiêu',
):
    if marker not in combat:
        raise RuntimeError("Devil Within combat contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")

# Android-authoritative independent encounter roll, after established unique bosses and before pool.
normal_roll = '    JSONObject normalEntityRoll = thresholdRoll("entityEncounter", 10000, entityThresholds[level], entityEncounterAction && entityAllowed && !diepMinhRoll.optBoolean("success", false) && !monsterXRoll.optBoolean("success", false) && !johnDoeRoll.optBoolean("success", false) && !scp173Roll.optBoolean("success", false) && !violetWardenRoll.optBoolean("success", false), entitySuffix);\n'
devil_roll = '''    JSONObject kaiDevilWithinRoll = thresholdRoll("kaiDevilWithinEncounter", 10000, 200,
      entityEncounterAction && entityAllowed && !diepMinhRoll.optBoolean("success", false) && !monsterXRoll.optBoolean("success", false) && !johnDoeRoll.optBoolean("success", false) && !scp173Roll.optBoolean("success", false) && !violetWardenRoll.optBoolean("success", false),
      " Kai - The Devil Within secret form 2% all Levels/sublevels");
    rolls.put("kaiDevilWithinEncounter", kaiDevilWithinRoll);
    JSONObject normalEntityRoll = thresholdRoll("entityEncounter", 10000, entityThresholds[level], entityEncounterAction && entityAllowed && !diepMinhRoll.optBoolean("success", false) && !monsterXRoll.optBoolean("success", false) && !johnDoeRoll.optBoolean("success", false) && !scp173Roll.optBoolean("success", false) && !violetWardenRoll.optBoolean("success", false) && !kaiDevilWithinRoll.optBoolean("success", false), entitySuffix);
'''
main = once(main, normal_roll, devil_roll, "Devil Within 2 percent roll")

flag_anchor = '    JSONObject violetWarden = rolls.optJSONObject("violetWardenEncounter");\n'
main = once(main, flag_anchor, flag_anchor + '    JSONObject kaiDevilWithin = rolls.optJSONObject("kaiDevilWithinEncounter");\n', "Devil Within flag roll")
priority_anchor = '    } else if (violetWarden != null && violetWarden.optBoolean("success", false)) {\n      entityKey = "violet_warden";\n    } else {\n'
priority_new = '    } else if (violetWarden != null && violetWarden.optBoolean("success", false)) {\n      entityKey = "violet_warden";\n    } else if (kaiDevilWithin != null && kaiDevilWithin.optBoolean("success", false)) {\n      entityKey = "kai_the_devil_within";\n    } else {\n'
main = once(main, priority_anchor, priority_new, "Devil Within priority")

key_anchor = 'case "violet_warden":\n        return key;'
main = once(main, key_anchor, 'case "violet_warden": case "kai_the_devil_within":\n        return key;', "Devil Within canonical key")
name_anchor = '      case "violet_warden": name = "The Violet Warden"; break;\n'
main = once(main, name_anchor, name_anchor + '      case "kai_the_devil_within": name = "Kai - The Devil Within"; break;\n', "Devil Within display name")
url_anchor = '("violet_warden".equals(entityKey) ? "Newviolet.png" : entityKey + ".png")))))'
url_new = '("violet_warden".equals(entityKey) ? "Newviolet.png" : ("kai_the_devil_within".equals(entityKey) ? "Kai-TheDevilWithin.png" : entityKey + ".png"))))))'
main = once(main, url_anchor, url_new, "Devil Within image mapping")

for marker in (
    'thresholdRoll("kaiDevilWithinEncounter", 10000, 200',
    'entityKey = "kai_the_devil_within"',
    'case "kai_the_devil_within": name = "Kai - The Devil Within"',
    '"Kai-TheDevilWithin.png"',
):
    if marker not in main:
        raise RuntimeError("Devil Within Android contract missing: " + marker)

MAIN.write_text(main, encoding="utf-8")

TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class KaiDevilWithinEntityTest {
  @Test fun startsWithExactCanonicalHpAndName() {
    val started = CombatRuntime.start(GameState.initial(), "kai_the_devil_within")
    val combat = CombatRuntime.active(started)
    assertNotNull(combat)
    assertEquals("Kai - The Devil Within", combat!!.entityName)
    assertEquals(5678, combat.entityMaxHp)
    assertEquals(5678, combat.entityHp)
  }
}
''', encoding="utf-8")

print("Kai - The Devil Within installed: exact 5678 HP, independent 2% encounter, 70% Kai stats, four active skills and 5%/2-turn regeneration.")
