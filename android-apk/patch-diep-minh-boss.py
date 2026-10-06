from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
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
# CombatRuntime: Diệp Minh is a unique boss. This patch deliberately runs after
# Entity durability and Kai's Guilty Crown Override so its exact HP/regen and
# special attacks are the final authority without disturbing older mechanics.
# ---------------------------------------------------------------------------
combat = COMBAT.read_text(encoding="utf-8")

constants_old = '''  private const val KAI_GUILTY_CROWN_DAMAGE_PER_SHOT = 10
'''
constants_new = '''  private const val KAI_GUILTY_CROWN_DAMAGE_PER_SHOT = 10
  private const val DIEP_MINH_KEY = "diep_minh"
  private const val DIEP_MINH_MAX_HP = 2999
  private const val DIEP_MINH_ATTACK_PERCENT = 10
  private const val DIEP_MINH_REGEN_PER_TURN = 30
  private const val DIEP_MINH_ULTIMATE_INTERVAL_TURNS = 5
  private const val DIEP_MINH_ULTIMATE_PERCENT = 5
'''
combat = replace_once(combat, constants_old, constants_new, "Diệp Minh combat constants")

profile_anchor = '    Profile("slenderman", "Slenderman", 160, 23, 8, 10)\n'
profile_new = '    Profile("slenderman", "Slenderman", 160, 23, 8, 10),\n    Profile(DIEP_MINH_KEY, "Diệp Minh", DIEP_MINH_MAX_HP, 0, 8, 9)\n'
combat = replace_once(combat, profile_anchor, profile_new, "Diệp Minh profile")

combat = replace_once(
    combat,
    '    val enhancedEntityMaxHp = profile.maxHp + ENTITY_HP_BONUS\n',
    '    val enhancedEntityMaxHp = if (profile.key == DIEP_MINH_KEY) DIEP_MINH_MAX_HP else profile.maxHp + ENTITY_HP_BONUS\n',
    "Diệp Minh exact encounter HP",
)
combat = replace_once(
    combat,
    '    val canonicalMaxHp = profile.maxHp + ENTITY_HP_BONUS\n',
    '    val canonicalMaxHp = if (profile.key == DIEP_MINH_KEY) DIEP_MINH_MAX_HP else profile.maxHp + ENTITY_HP_BONUS\n',
    "Diệp Minh exact migrated HP",
)

# Persist follower/AoE damage through the same state that CombatRuntime encodes.
resolve_start = combat.index('  fun resolve(state: GameState, actionKind: String, action: String): Resolution {\n')
resolve_end = combat.index('\n  fun toJson(state: GameState): JSONObject?', resolve_start)
resolve = combat[resolve_start:resolve_end]
log_anchor = '    val log = mutableListOf<String>()\n'
if '    var resolvedState = state\n' not in resolve:
    resolve = replace_once(resolve, log_anchor, log_anchor + '    var resolvedState = state\n', "combat resolved-state accumulator")
resolve = resolve.replace('encode(state,', 'encode(resolvedState,')
combat = combat[:resolve_start] + resolve + combat[resolve_end:]

helper_anchor = '  private fun encode(state: GameState, c: Snapshot): GameState {\n'
helper_block = r'''  private data class PartyPercentDamage(
    val state: GameState,
    val kaiHp: Int,
    val summary: String
  )

  private fun percentDamage(maxHp: Int, percent: Int): Int =
    max(1, (maxHp * percent + 99) / 100)

  private fun damageActivePartyByPercent(state: GameState, percent: Int): PartyPercentDamage {
    var next = state
    val lines = mutableListOf<String>()
    state.party.memberIds.distinct().forEach { characterId ->
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
      summary = if (lines.isEmpty()) "không có nhân vật ACTIVE hợp lệ để nhận sát thương" else lines.joinToString("; ")
    )
  }

'''
if 'private data class PartyPercentDamage(' not in combat:
    combat = replace_once(combat, helper_anchor, helper_block + helper_anchor, "Diệp Minh party-percent helper")

response_start = combat.index('    // Enemy response. READ/guard/evasion reduce expected incoming damage; attacking blindly is riskier.\n')
regen_start = combat.index('    val entityHpBeforeRegen = c.entityHp\n', response_start)
response_new = r'''    // Enemy response. Diệp Minh uses percentage damage; all other Entity behavior remains unchanged.
    if (c.entityKey == DIEP_MINH_KEY && c.eventCounter % DIEP_MINH_ULTIMATE_INTERVAL_TURNS == 0) {
      val pulse = damageActivePartyByPercent(resolvedState, DIEP_MINH_ULTIMATE_PERCENT)
      resolvedState = pulse.state
      c = c.copy(playerHp = pulse.kaiHp, momentum = max(-3, c.momentum - 1))
      log += "Devils And Gold kích hoạt ở combat turn ${c.eventCounter}: toàn bộ nhân vật ACTIVE đang ra trận nhận ${DIEP_MINH_ULTIMATE_PERCENT}% Max HP. ${pulse.summary}."
    } else {
      val incomingRoll = roll(c.copy(eventCounter = c.eventCounter + 31), 100)
      val defense = when (intent) { Intent.EVADE -> 34; Intent.GUARD -> 30; Intent.MOVE -> 18; Intent.READ -> 12; else -> 0 } +
        when (c.cover) { Cover.HARD -> 22; Cover.PARTIAL -> 10; Cover.EXPOSED -> 0 } + max(0, c.momentum) * 4
      val enemyChance = (profile.aggression * 8 - defense + max(0, -c.momentum) * 7).coerceIn(8, 88)
      if (incomingRoll < enemyChance) {
        val damage = if (c.entityKey == DIEP_MINH_KEY) {
          percentDamage(c.playerMaxHp, DIEP_MINH_ATTACK_PERCENT)
        } else {
          max(1, profile.attack + roll(c.copy(eventCounter = c.eventCounter + 47), 7) - when (c.cover) { Cover.HARD -> 8; Cover.PARTIAL -> 4; Cover.EXPOSED -> 0 })
        }
        val hp = max(0, c.playerHp - damage)
        c = c.copy(playerHp = hp, momentum = max(-3, c.momentum - 1))
        log += if (c.entityKey == DIEP_MINH_KEY) {
          "Diệp Minh phản công: Kai -$damage HP (${DIEP_MINH_ATTACK_PERCENT}% Max HP; ${c.playerHp}/${c.playerMaxHp})."
        } else {
          "${c.entityName} phản công: Kai -$damage HP (${c.playerHp}/${c.playerMaxHp})."
        }
      } else {
        log += "${c.entityName} không xuyên được thế phòng thủ/di chuyển của Kai."
      }
    }

'''
combat = combat[:response_start] + response_new + combat[regen_start:]

regen_old = '''    val entityHpBeforeRegen = c.entityHp
    val entityHpAfterRegen = min(c.entityMaxHp, c.entityHp + ENTITY_REGEN_PER_TURN)
    if (entityHpAfterRegen > entityHpBeforeRegen) {
      c = c.copy(entityHp = entityHpAfterRegen, entityCondition = condition(entityHpAfterRegen, c.entityMaxHp))
      log += "${c.entityName} hồi +$ENTITY_REGEN_PER_TURN HP (${c.entityHp}/${c.entityMaxHp})."
    }
'''
regen_new = '''    val entityHpBeforeRegen = c.entityHp
    val entityRegen = if (c.entityKey == DIEP_MINH_KEY) DIEP_MINH_REGEN_PER_TURN else ENTITY_REGEN_PER_TURN
    val entityHpAfterRegen = min(c.entityMaxHp, c.entityHp + entityRegen)
    if (entityHpAfterRegen > entityHpBeforeRegen) {
      c = c.copy(entityHp = entityHpAfterRegen, entityCondition = condition(entityHpAfterRegen, c.entityMaxHp))
      log += "${c.entityName} hồi +$entityRegen HP (${c.entityHp}/${c.entityMaxHp})."
    }
'''
combat = replace_once(combat, regen_old, regen_new, "Diệp Minh 30 HP regeneration")

for marker in (
    'private const val DIEP_MINH_MAX_HP = 2999',
    'private const val DIEP_MINH_ATTACK_PERCENT = 10',
    'private const val DIEP_MINH_REGEN_PER_TURN = 30',
    'private const val DIEP_MINH_ULTIMATE_INTERVAL_TURNS = 5',
    'private const val DIEP_MINH_ULTIMATE_PERCENT = 5',
    'Profile(DIEP_MINH_KEY, "Diệp Minh", DIEP_MINH_MAX_HP, 0, 8, 9)',
    'if (profile.key == DIEP_MINH_KEY) DIEP_MINH_MAX_HP',
    'damageActivePartyByPercent(resolvedState, DIEP_MINH_ULTIMATE_PERCENT)',
    'Devils And Gold kích hoạt',
    'percentDamage(c.playerMaxHp, DIEP_MINH_ATTACK_PERCENT)',
    'val entityRegen = if (c.entityKey == DIEP_MINH_KEY) DIEP_MINH_REGEN_PER_TURN else ENTITY_REGEN_PER_TURN',
):
    if marker not in combat:
        raise RuntimeError("Diệp Minh combat contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")


# ---------------------------------------------------------------------------
# Android encounter/overlay: independent 3% boss roll, deliberately excluded
# from the shared roaming pool. If both rolls succeed, the boss roll wins so
# only one authoritative CombatRuntime encounter starts in that turn.
# ---------------------------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")

normal_roll = '    JSONObject normalEntityRoll = thresholdRoll("entityEncounter", 10000, entityThresholds[level], entityEncounterAction && entityAllowed, entitySuffix);\n'
boss_roll = '    JSONObject diepMinhRoll = thresholdRoll("diepMinhEncounter", 10000, 300, entityEncounterAction && entityAllowed, " unique boss 3%");\n    rolls.put("diepMinhEncounter", diepMinhRoll);\n'
if 'rolls.put("diepMinhEncounter", diepMinhRoll);' not in main:
    main = replace_once(main, normal_roll, boss_roll + normal_roll, "Diệp Minh independent 3% roll")

normalized_old = '      case "jeff_the_killer": case "jane_the_killer": case "slenderman":\n        return key;\n'
normalized_new = '      case "jeff_the_killer": case "jane_the_killer": case "slenderman": case "diep_minh":\n        return key;\n'
main = replace_once(main, normalized_old, normalized_new, "Diệp Minh canonical key")

name_old = '      case "slenderman": name = "Slenderman"; break;\n'
name_new = '      case "slenderman": name = "Slenderman"; break;\n      case "diep_minh": name = "Diệp Minh"; break;\n'
main = replace_once(main, name_old, name_new, "Diệp Minh overlay display name")

keys_old = "'jeff_the_killer','jane_the_killer','slenderman'];"
keys_new = "'jeff_the_killer','jane_the_killer','slenderman','diep_minh'];"
main = replace_once(main, keys_old, keys_new, "Diệp Minh overlay JS key")

helper_start = main.find('  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {')
helper_end = main.find('\n  private JSONObject resolveEntityOverlay(String rawEntityKey) throws Exception {', helper_start)
if helper_start < 0 or helper_end < 0:
    raise RuntimeError("Final forceEntityEncounterFlag boundary missing")
helper = r'''  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {
    if (candidateState == null || rolls == null) return;
    String entityKey;
    JSONObject boss = rolls.optJSONObject("diepMinhEncounter");
    if (boss != null && boss.optBoolean("success", false)) {
      entityKey = "diep_minh";
    } else {
      JSONObject normal = rolls.optJSONObject("entityEncounter");
      if (normal == null || !normal.optBoolean("success", false)) return;
      entityKey = rolls.optString("roamingEntityKey", "").trim();
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

prompt_anchor = 'ROAMING KILLER HARD LOCK: Jeff the Killer và Jane the Killer dùng cùng entityEncounter'
if 'DIỆP MINH BOSS HARD LOCK:' not in main:
    line_start = main.rfind('\n', 0, main.find(prompt_anchor)) + 1
    if line_start <= 0:
        raise RuntimeError("Entity roaming prompt insertion anchor missing")
    boss_prompt = '      "DIỆP MINH BOSS HARD LOCK: Diệp Minh dùng roll độc lập diepMinhEncounter đúng 3% trên mỗi action gameplay hợp lệ. Boss không nằm trong roamingEntityKey pool chung. Khi boss roll success, encounter Diệp Minh ưu tiên và chỉ một CombatRuntime encounter được khởi tạo. " +\n'
    main = main[:line_start] + boss_prompt + main[line_start:]

pool_lines = [line for line in main.splitlines() if 'String[] roamingPool =' in line]
if len(pool_lines) != 1:
    raise RuntimeError(f"Expected exactly one final roaming pool, found {len(pool_lines)}")
if 'diep_minh' in pool_lines[0]:
    raise RuntimeError("Diệp Minh must remain outside the shared roaming pool")

for marker in (
    'thresholdRoll("diepMinhEncounter", 10000, 300, entityEncounterAction && entityAllowed',
    'rolls.put("diepMinhEncounter", diepMinhRoll)',
    'case "diep_minh":',
    'case "diep_minh": name = "Diệp Minh"; break;',
    "'slenderman','diep_minh']",
    'JSONObject boss = rolls.optJSONObject("diepMinhEncounter")',
    'entityKey = "diep_minh";',
    'DIỆP MINH BOSS HARD LOCK:',
    'file:///android_asset/entity/',
):
    if marker not in main:
        raise RuntimeError("Diệp Minh Android runtime contract missing: " + marker)

MAIN.write_text(main, encoding="utf-8")


# ---------------------------------------------------------------------------
# Focused regressions against the fully generated runtime.
# ---------------------------------------------------------------------------
test = TEST.read_text(encoding="utf-8")
new_tests = r'''
  @Test fun diepMinhHasExact2999HpAndRegeneratesThirtyPerSurvivingTurn() {
    var state = CombatRuntime.start(GameState.initial(), "diep_minh")
    val started = CombatRuntime.active(state)!!
    assertEquals(2999, started.entityMaxHp)
    assertEquals(2999, started.entityHp)

    state = state.copy(metadata = state.metadata + ("combat.entityHp" to "2900"))
    val result = CombatRuntime.resolve(state, "SEARCH", "quan sát Diệp Minh")
    val after = CombatRuntime.active(result.state)!!
    assertEquals(2930, after.entityHp)
    assertTrue(result.reply.contains("hồi +30 HP"))
  }

  @Test fun diepMinhDevilsAndGoldHitsEveryActivePartyMemberForFivePercentMaxHpOnTurnFive() {
    val initial = GameState.initial()
    val iris = CharacterState(
      id = "iris",
      name = "Iris",
      statProfile = CharacterStatProfiles.forId("iris"),
      vitalState = CharacterStatProfiles.initialVitals("iris")
    )
    var state = initial.copy(
      characters = initial.characters + ("iris" to iris),
      party = PartyState(memberIds = listOf(KAI_ID, "iris"))
    )
    state = CombatRuntime.start(state, "diep_minh")
    state = state.copy(metadata = state.metadata + ("combat.eventCounter" to "4"))

    val kaiBefore = state.characters.getValue(KAI_ID).vitalState.currentHp
    val irisBefore = state.characters.getValue("iris").vitalState.currentHp
    val kaiMax = CharacterStatEngine.effective(state, KAI_ID).maxHp
    val irisMax = CharacterStatEngine.effective(state, "iris").maxHp
    val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")

    assertTrue(result.reply.contains("Devils And Gold"))
    assertEquals(kaiBefore - maxOf(1, (kaiMax * 5 + 99) / 100), result.state.characters.getValue(KAI_ID).vitalState.currentHp)
    assertEquals(irisBefore - maxOf(1, (irisMax * 5 + 99) / 100), result.state.characters.getValue("iris").vitalState.currentHp)
  }
'''
if 'diepMinhHasExact2999HpAndRegeneratesThirtyPerSurvivingTurn' not in test:
    close = test.rfind('}\n')
    if close < 0:
        raise RuntimeError("CombatRuntimeTest class closing brace missing")
    test = test[:close] + new_tests + test[close:]

for marker in (
    'diepMinhHasExact2999HpAndRegeneratesThirtyPerSurvivingTurn',
    'assertEquals(2999, started.entityMaxHp)',
    'assertEquals(2930, after.entityHp)',
    'diepMinhDevilsAndGoldHitsEveryActivePartyMemberForFivePercentMaxHpOnTurnFive',
    'result.reply.contains("Devils And Gold")',
):
    if marker not in test:
        raise RuntimeError("Diệp Minh regression contract missing: " + marker)
TEST.write_text(test, encoding="utf-8")

asset = ROOT / "app/src/main/assets/entity/diep_minh.png"
if not asset.is_file() or asset.stat().st_size <= 0:
    raise RuntimeError("Original Diệp Minh PNG is missing from assets/entity/diep_minh.png")
if asset.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
    raise RuntimeError("Diệp Minh asset is not a PNG")

print("Diệp Minh boss installed: 2999 HP, 10% Max-HP attack, +30 HP/turn, Devils And Gold every 5 turns for 5% party Max HP, independent 3% encounter roll.")
