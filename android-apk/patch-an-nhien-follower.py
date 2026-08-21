from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


# 1) Seed An Nhien in the authoritative core without putting her in Party before encounter.
path = CORE / "GameState.kt"
text = path.read_text(encoding="utf-8")
old = '''          metadata = mapOf("inventoryProfile" to "kai")
        )
      ),
      inventories = mapOf(KAI_ID to InventoryState(KAI_ID)),
      equipment = mapOf(KAI_ID to EquipmentState(KAI_ID, KaiStartingEquipment.slots))
'''
new = '''          metadata = mapOf("inventoryProfile" to "kai")
        ),
        AN_NHIEN_ID to AnNhienCanon.character()
      ),
      inventories = mapOf(
        KAI_ID to InventoryState(KAI_ID),
        AN_NHIEN_ID to AnNhienCanon.inventory()
      ),
      equipment = mapOf(
        KAI_ID to EquipmentState(KAI_ID, KaiStartingEquipment.slots),
        AN_NHIEN_ID to AnNhienCanon.equipment()
      )
'''
text = replace_once(text, old, new, "GameState initial An Nhien seed")
path.write_text(text, encoding="utf-8")

# 2) Backfill all loaded saves, including existing v3 saves, with the required follower definition.
path = CORE / "GameStateCodec.kt"
text = path.read_text(encoding="utf-8")
old = '''    return when {
      version >= CURRENT_SAVE_VERSION -> decodeCurrent(root)
      version == 2 && root.has("inventories") -> migrateV2Core(root)
      else -> LegacySaveMigration.migrate(root)
    }
'''
new = '''    val decoded = when {
      version >= CURRENT_SAVE_VERSION -> decodeCurrent(root)
      version == 2 && root.has("inventories") -> migrateV2Core(root)
      else -> LegacySaveMigration.migrate(root)
    }
    return AnNhienCanon.ensure(decoded)
'''
text = replace_once(text, old, new, "save backfill")
path.write_text(text, encoding="utf-8")

# 3) Reuse InventoryEngine but give An Nhien exactly two FOOD-only slots.
path = CORE / "InventoryPolicy.kt"
text = path.read_text(encoding="utf-8")
text = replace_once(
    text,
    '  val SPECIAL_COMPANION = InventoryProfile(maxTypes = 4, maxPerType = 20)\n  val NORMAL = InventoryProfile(maxTypes = 2, maxPerType = 2)\n',
    '  val SPECIAL_COMPANION = InventoryProfile(maxTypes = 4, maxPerType = 20)\n  val AN_NHIEN = InventoryProfile(maxTypes = 2, maxPerType = 20)\n  val NORMAL = InventoryProfile(maxTypes = 2, maxPerType = 2)\n',
    "An Nhien inventory profile"
)
text = replace_once(
    text,
    '''    if (characterId == KAI_ID) return KAI
    val character = state.characters[characterId]
''',
    '''    if (characterId == KAI_ID) return KAI
    if (characterId == AN_NHIEN_ID) return AN_NHIEN
    val character = state.characters[characterId]
''',
    "An Nhien profile routing"
)
text = replace_once(
    text,
    '''    val normalized = ItemContentRules.normalize(item)
    val profile = profileFor(state, ownerId)
''',
    '''    val normalized = ItemContentRules.normalize(item)
    if (ownerId == AN_NHIEN_ID && !AnNhienCanon.isFoodItem(normalized)) return "an_nhien_food_only"
    val profile = profileFor(state, ownerId)
''',
    "An Nhien food-only gate"
)
path.write_text(text, encoding="utf-8")

# 4) Apply her 0.70 survival capacity through the existing physiology thresholds.
path = CORE / "PhysiologyStatusPolicy.kt"
text = path.read_text(encoding="utf-8")
old = '''  fun derive(state: PhysiologyState): DerivedPhysiologyStatus = DerivedPhysiologyStatus(
    hunger = hungerBand(state.minutesSinceFood),
    thirst = thirstBand(state.minutesSinceWater),
    sleepDeprivation = awakeBand(state.minutesAwake),
    pain = state.painState?.trim()?.takeIf { it.isNotEmpty() },
    infection = state.infectionState?.trim()?.takeIf { it.isNotEmpty() },
    thermal = state.thermalState?.trim()?.takeIf { it.isNotEmpty() },
    foodPercent = remainingPercent(state.minutesSinceFood, FOOD_CRITICAL_MINUTES),
    waterPercent = remainingPercent(state.minutesSinceWater, WATER_CRITICAL_MINUTES),
    restPercent = remainingPercent(state.minutesAwake, REST_CRITICAL_MINUTES)
  )

  fun hungerBand(minutesSinceFood: Long?): PhysiologyBand = band(
    minutesSinceFood,
    mildAt = 12L * 60L,
    moderateAt = 24L * 60L,
    severeAt = 48L * 60L,
    criticalAt = FOOD_CRITICAL_MINUTES
  )

  fun thirstBand(minutesSinceWater: Long?): PhysiologyBand = band(
    minutesSinceWater,
    mildAt = 6L * 60L,
    moderateAt = 12L * 60L,
    severeAt = 24L * 60L,
    criticalAt = WATER_CRITICAL_MINUTES
  )

  fun awakeBand(minutesAwake: Long?): PhysiologyBand = band(
    minutesAwake,
    mildAt = 16L * 60L,
    moderateAt = 20L * 60L,
    severeAt = 24L * 60L,
    criticalAt = REST_CRITICAL_MINUTES
  )

  fun foodPercent(minutesSinceFood: Long?): Int? = remainingPercent(minutesSinceFood, FOOD_CRITICAL_MINUTES)
  fun waterPercent(minutesSinceWater: Long?): Int? = remainingPercent(minutesSinceWater, WATER_CRITICAL_MINUTES)
  fun restPercent(minutesAwake: Long?): Int? = remainingPercent(minutesAwake, REST_CRITICAL_MINUTES)
'''
new = '''  fun derive(state: PhysiologyState, survivalMultiplier: Double = 1.0): DerivedPhysiologyStatus = DerivedPhysiologyStatus(
    hunger = hungerBand(state.minutesSinceFood, survivalMultiplier),
    thirst = thirstBand(state.minutesSinceWater, survivalMultiplier),
    sleepDeprivation = awakeBand(state.minutesAwake, survivalMultiplier),
    pain = state.painState?.trim()?.takeIf { it.isNotEmpty() },
    infection = state.infectionState?.trim()?.takeIf { it.isNotEmpty() },
    thermal = state.thermalState?.trim()?.takeIf { it.isNotEmpty() },
    foodPercent = remainingPercent(state.minutesSinceFood, scaled(FOOD_CRITICAL_MINUTES, survivalMultiplier)),
    waterPercent = remainingPercent(state.minutesSinceWater, scaled(WATER_CRITICAL_MINUTES, survivalMultiplier)),
    restPercent = remainingPercent(state.minutesAwake, scaled(REST_CRITICAL_MINUTES, survivalMultiplier))
  )

  fun hungerBand(minutesSinceFood: Long?, survivalMultiplier: Double = 1.0): PhysiologyBand = band(
    minutesSinceFood,
    mildAt = scaled(12L * 60L, survivalMultiplier),
    moderateAt = scaled(24L * 60L, survivalMultiplier),
    severeAt = scaled(48L * 60L, survivalMultiplier),
    criticalAt = scaled(FOOD_CRITICAL_MINUTES, survivalMultiplier)
  )

  fun thirstBand(minutesSinceWater: Long?, survivalMultiplier: Double = 1.0): PhysiologyBand = band(
    minutesSinceWater,
    mildAt = scaled(6L * 60L, survivalMultiplier),
    moderateAt = scaled(12L * 60L, survivalMultiplier),
    severeAt = scaled(24L * 60L, survivalMultiplier),
    criticalAt = scaled(WATER_CRITICAL_MINUTES, survivalMultiplier)
  )

  fun awakeBand(minutesAwake: Long?, survivalMultiplier: Double = 1.0): PhysiologyBand = band(
    minutesAwake,
    mildAt = scaled(16L * 60L, survivalMultiplier),
    moderateAt = scaled(20L * 60L, survivalMultiplier),
    severeAt = scaled(24L * 60L, survivalMultiplier),
    criticalAt = scaled(REST_CRITICAL_MINUTES, survivalMultiplier)
  )

  fun foodPercent(minutesSinceFood: Long?, survivalMultiplier: Double = 1.0): Int? = remainingPercent(minutesSinceFood, scaled(FOOD_CRITICAL_MINUTES, survivalMultiplier))
  fun waterPercent(minutesSinceWater: Long?, survivalMultiplier: Double = 1.0): Int? = remainingPercent(minutesSinceWater, scaled(WATER_CRITICAL_MINUTES, survivalMultiplier))
  fun restPercent(minutesAwake: Long?, survivalMultiplier: Double = 1.0): Int? = remainingPercent(minutesAwake, scaled(REST_CRITICAL_MINUTES, survivalMultiplier))

  private fun scaled(minutes: Long, multiplier: Double): Long {
    val safe = if (multiplier.isFinite() && multiplier > 0.0) multiplier else 1.0
    return (minutes.toDouble() * safe).toLong().coerceAtLeast(1L)
  }
'''
text = replace_once(text, old, new, "survival multiplier")
path.write_text(text, encoding="utf-8")

path = CORE / "CharacterDetailProjection.kt"
text = path.read_text(encoding="utf-8")
text = replace_once(
    text,
    '      physiology = PhysiologyStatusPolicy.derive(character.physiology),\n',
    '      physiology = PhysiologyStatusPolicy.derive(character.physiology, AnNhienCanon.survivalMultiplierFor(character)),\n',
    "character physiology multiplier projection"
)
path.write_text(text, encoding="utf-8")

# 5) Make her follower/equipment locks authoritative in the existing engines.
path = CORE / "Engines.kt"
text = path.read_text(encoding="utf-8")
text = replace_once(
    text,
    '''      ItemCommand.Operation.EQUIP -> {
        if ((source.items[command.itemId]?.quantity ?: 0) < command.quantity) return invalid(state, "item_not_owned")
''',
    '''      ItemCommand.Operation.EQUIP -> {
        if (command.actorId == AN_NHIEN_ID) return invalid(state, "an_nhien_equipment_locked")
        if ((source.items[command.itemId]?.quantity ?: 0) < command.quantity) return invalid(state, "item_not_owned")
''',
    "An Nhien equip lock"
)
text = replace_once(
    text,
    '''      ItemCommand.Operation.UNEQUIP -> {
        val slot = command.slot ?: return invalid(state, "equipment_slot_required")
''',
    '''      ItemCommand.Operation.UNEQUIP -> {
        if (command.actorId == AN_NHIEN_ID) return invalid(state, "an_nhien_equipment_locked")
        val slot = command.slot ?: return invalid(state, "equipment_slot_required")
''',
    "An Nhien unequip lock"
)
text = replace_once(
    text,
    '''    PartyCommand.Operation.REMOVE -> {
      if (command.targetId == state.party.leaderId) return invalid(state, "cannot_remove_leader")
''',
    '''    PartyCommand.Operation.REMOVE -> {
      if (command.targetId == AN_NHIEN_ID) return invalid(state, "an_nhien_follower_locked")
      if (command.targetId == state.party.leaderId) return invalid(state, "cannot_remove_leader")
''',
    "An Nhien remove lock"
)
text = replace_once(
    text,
    '''    PartyCommand.Operation.SET_LEADER -> {
      if (command.targetId !in state.party.memberIds) return invalid(state, "leader_not_in_party")
''',
    '''    PartyCommand.Operation.SET_LEADER -> {
      if (command.targetId == AN_NHIEN_ID) return invalid(state, "an_nhien_cannot_lead")
      if (command.targetId !in state.party.memberIds) return invalid(state, "leader_not_in_party")
''',
    "An Nhien leader lock"
)
text = replace_once(
    text,
    '''    PartyCommand.Operation.SEPARATE -> {
      val character = state.characters[command.targetId] ?: return invalid(state, "target_unknown")
''',
    '''    PartyCommand.Operation.SEPARATE -> {
      if (command.targetId == AN_NHIEN_ID) return invalid(state, "an_nhien_follower_locked")
      val character = state.characters[command.targetId] ?: return invalid(state, "target_unknown")
''',
    "An Nhien separation lock"
)
path.write_text(text, encoding="utf-8")

# 6) Make the intent/core resolver know her identity; all actual party/inventory mutations still use existing engines.
path = CORE / "GameCoreFacade.kt"
text = path.read_text(encoding="utf-8")
old = '    val actors = state.characters.values.associate { it.name.lowercase() to it.id } + mapOf("kai" to KAI_ID, "iris" to "iris", "syvial" to "syvial")\n'
new = '    val actors = state.characters.values.associate { it.name.lowercase() to it.id } + mapOf("kai" to KAI_ID, "iris" to "iris", "syvial" to "syvial", "an nhiên" to AN_NHIEN_ID, "an nhien" to AN_NHIEN_ID)\n'
text = replace_once(text, old, new, "An Nhien actor aliases")
path.write_text(text, encoding="utf-8")

# 7) Final Android gameplay integration: deterministic Level 0 encounter, bonuses and exit gate.
main = MAIN.read_text(encoding="utf-8")
helper_anchor = '''  private boolean reunionEligibleAndroid(JSONObject state, String key) {
'''
helpers = r'''  private boolean anNhienFollowing(JSONObject state) {
    return partyHas(state, "An Nhiên") || partyHas(state, "an-nhien");
  }

  private boolean anNhienEncountered(JSONObject state) {
    if (anNhienFollowing(state)) return true;
    JSONObject flags = state.optJSONObject("flags");
    JSONObject record = flags != null ? flags.optJSONObject("anNhien") : null;
    return record != null && record.optBoolean("encountered", false);
  }

'''
if helpers not in main:
    if helper_anchor not in main:
        raise RuntimeError("An Nhien Java helper anchor missing")
    main = main.replace(helper_anchor, helpers + helper_anchor, 1)

main = replace_once(
    main,
    '''    boolean exitIntent = containsAny(a, "exit", "lối thoát", "thoát", "cửa trắng", "cánh cửa", "ngưỡng", "chuyển level", "sang level", "hành lang phía sau", "đường ra");

    JSONObject flags = state.optJSONObject("flags");
''',
    '''    boolean exitIntent = containsAny(a, "exit", "lối thoát", "thoát", "cửa trắng", "cánh cửa", "ngưỡng", "chuyển level", "sang level", "hành lang phía sau", "đường ra");
    boolean anNhienFollowing = anNhienFollowing(state);
    boolean anNhienEncountered = anNhienEncountered(state);

    JSONObject flags = state.optJSONObject("flags");
''',
    "An Nhien gameplay state"
)
main = replace_once(
    main,
    '''    rolls.put("survivor", thresholdRoll("survivor", 10000, 200, survivorAllowed, ""));
    rolls.put("irisReunion", thresholdRoll("irisReunion", 1000000, 25, reunionEligibleAndroid(state, "iris"), ""));
''',
    '''    rolls.put("anNhienEncounter", thresholdRoll("anNhienEncounter", 1, 1, level == 0 && physical && !anNhienEncountered, " mandatory Level 0 follower"));
    rolls.put("survivor", thresholdRoll("survivor", 10000, 200, survivorAllowed && !(level == 0 && !anNhienEncountered), ""));
    rolls.put("irisReunion", thresholdRoll("irisReunion", 1000000, 25, reunionEligibleAndroid(state, "iris"), ""));
''',
    "guaranteed An Nhien encounter roll"
)
main = replace_once(
    main,
    '    rolls.put("loot", thresholdRoll("loot", 10000, lootThresholds[level], search, ""));\n',
    '    rolls.put("loot", thresholdRoll("loot", 10000, Math.min(10000, lootThresholds[level] + (anNhienFollowing ? 1000 : 0)), search, anNhienFollowing ? " +10% An Nhiên" : ""));\n',
    "An Nhien loot bonus"
)
main = replace_once(
    main,
    '''    int exitThreshold = exitThresholdAndroid(state);
    JSONObject exitProbe = thresholdRoll("exitProbe", 10000, exitThreshold, exitIntent && (physical || search), " discovery clue");
''',
    '''    int exitThreshold = exitThresholdAndroid(state);
    if (anNhienFollowing) exitThreshold = Math.min(10000, exitThreshold + 200);
    JSONObject exitProbe = thresholdRoll("exitProbe", 10000, exitThreshold, exitIntent && (physical || search), anNhienFollowing ? " discovery clue +2% An Nhiên" : " discovery clue");
''',
    "An Nhien exit bonus"
)

old_transition = '''  private boolean canTransition(JSONObject before, JSONObject rolls) {
    JSONObject exploration = before.optJSONObject("flags") != null ? before.optJSONObject("flags").optJSONObject("exploration") : null;
    String confirmedExit = exploration != null ? exploration.optString("confirmedExit", "") : "";
    return (confirmedExit != null && !confirmedExit.trim().isEmpty()) || rollSuccess(rolls, "levelExit");
  }
'''
new_transition = '''  private boolean canTransition(JSONObject before, JSONObject rolls) {
    if (currentLevel(before) == 0 && !anNhienEncountered(before)) return false;
    JSONObject exploration = before.optJSONObject("flags") != null ? before.optJSONObject("flags").optJSONObject("exploration") : null;
    String confirmedExit = exploration != null ? exploration.optString("confirmedExit", "") : "";
    return (confirmedExit != null && !confirmedExit.trim().isEmpty()) || rollSuccess(rolls, "levelExit");
  }
'''
main = replace_once(main, old_transition, new_transition, "Level 0 exit gate")

old_allowed = '''  private boolean characterAddAllowed(JSONObject before, String name, JSONObject rolls) {
    String value = lower(name);
    if (value.contains("iris")) return presentCharacter(before, "iris") || rollSuccess(rolls, "irisReunion");
'''
new_allowed = '''  private boolean characterAddAllowed(JSONObject before, String name, JSONObject rolls) {
    String value = lower(name);
    if (value.contains("an nhiên") || value.contains("an nhien") || value.contains("an-nhien")) return anNhienEncountered(before) || rollSuccess(rolls, "anNhienEncounter");
    if (value.contains("iris")) return presentCharacter(before, "iris") || rollSuccess(rolls, "irisReunion");
'''
main = replace_once(main, old_allowed, new_allowed, "An Nhien party authority")

old_snapshot = '''    else if (kind.equals("character_encounter")) allowed = rollSuccess(rolls, "survivor") || rollSuccess(rolls, "irisReunion") || rollSuccess(rolls, "syvialReunion");
'''
new_snapshot = '''    else if (kind.equals("character_encounter")) allowed = rollSuccess(rolls, "anNhienEncounter") || rollSuccess(rolls, "survivor") || rollSuccess(rolls, "irisReunion") || rollSuccess(rolls, "syvialReunion");
'''
main = replace_once(main, old_snapshot, new_snapshot, "An Nhien snapshot event")

old_tail = '''    flags.put("madGod", madGod).put("lastRolls", rolls);
    state.put("flags", flags);
    return state;
  }
'''
new_tail = '''    flags.put("madGod", madGod).put("lastRolls", rolls);

    boolean anNhienNow = anNhienEncountered(before) || rollSuccess(rolls, "anNhienEncounter");
    if (anNhienNow) {
      JSONObject anNhien = flags.optJSONObject("anNhien");
      if (anNhien == null) anNhien = new JSONObject();
      anNhien.put("encountered", true)
        .put("present", true)
        .put("follower", true)
        .put("nonCombat", true)
        .put("levelEncountered", 0)
        .put("lootBonusPercent", 10)
        .put("exitBonusPercent", 2);
      flags.put("anNhien", anNhien);

      JSONArray party = state.optJSONArray("party");
      if (party == null) party = new JSONArray();
      if (!arrayHasName(party, "An Nhiên")) {
        if (party.length() >= 3) party.remove(party.length() - 1);
        party.put(new JSONObject()
          .put("id", "an-nhien")
          .put("name", "An Nhiên")
          .put("present", true)
          .put("joinConfirmed", true)
          .put("presence", "ACTIVE")
          .put("role", "follower")
          .put("nonCombat", true));
      }
      state.put("party", party);
    }
    state.put("flags", flags);
    return state;
  }
'''
main = replace_once(main, old_tail, new_tail, "deterministic An Nhien encounter commit")

prompt_marker = '"GAMEPLAY_ROLLS do Android sinh là bất biến: chỉ outcome success=true mới được xuất hiện. Không reroll, không tự đổi xác suất, không tự tạo encounter/item/reunion/level transition trái roll. " +\n'
prompt_extra = prompt_marker + '            "AN NHIÊN HARD LOCK: bé gái 7 tuổi, con người, không phải Entity. anNhienEncounter success=true là cuộc gặp bắt buộc ở Level 0 và phải được kể trong lượt đó; sau khi gặp cô bé luôn theo Kai, không chiến đấu, không dùng vũ khí, không tự tách nhóm. Cô chỉ có +10% loot chance và +2% exit chance khi đang theo Kai, đúng như GAMEPLAY_ROLLS. Không tự thêm năng lực hoặc lore. " +\n'
main = replace_once(main, prompt_marker, prompt_extra, "An Nhien GM hard lock")

for marker in [
    'thresholdRoll("anNhienEncounter", 1, 1',
    'lootThresholds[level] + (anNhienFollowing ? 1000 : 0)',
    'exitThreshold + 200',
    'currentLevel(before) == 0 && !anNhienEncountered(before)',
    '.put("id", "an-nhien")',
    'AN NHIÊN HARD LOCK',
]:
    if marker not in main:
        raise RuntimeError(f"An Nhien Java integration marker missing: {marker}")
MAIN.write_text(main, encoding="utf-8")

# 8) Existing per-character UI: show the actual two FOOD slots instead of generic 9-slot copy.
html = INDEX.read_text(encoding="utf-8")
old_capacity = "    capacity.textContent=inv.length+' / 9 loại vật phẩm';"
new_capacity = "    capacity.textContent=member&&member.id==='an-nhien'?inv.length+' / 2 slot thực phẩm':inv.length+' / 9 loại vật phẩm';"
html = replace_once(html, old_capacity, new_capacity, "An Nhien UI food capacity")
INDEX.write_text(html, encoding="utf-8")

print("An Nhiên integrated: mandatory Level 0 follower, food-only inventory, fixed equipment, 0.70 survival capacity, +10% loot and +2% exit bonuses.")
