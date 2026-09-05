from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
APP = ROOT / "app"
CORE = APP / "src/main/java/com/rabpit/backroom/core"
TESTS = APP / "src/test/java/com/rabpit/backroom/core"
MAIN = APP / "src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = APP / "src/main/assets/index.html"
KNOWLEDGE = APP / "src/main/assets/knowledge/knowledge_db.json"
KCE = CORE / "knowledge/KnowledgeContextEngine.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Character Canon R07 {label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# Kai R10 runtime equipment. Keep legacy aliases only for save/import matching;
# current equipment IDs and display names are SRU-MK20 + SRU Assault Rifle MK19.
# ---------------------------------------------------------------------------
game_state_path = CORE / "GameState.kt"
game = game_state_path.read_text(encoding="utf-8")
game = game.replace('KAI_WHITE_WRAITH_ID', 'KAI_SRU_MK19_ID')
game = game.replace('KAI_BLACKBLOOD_ARMOR_ID', 'KAI_SRU_MK20_ID')
game = game.replace('"kai:white-wraith-magnum"', '"kai:sru-assault-rifle-mk19"')
game = game.replace('"kai:blackblood-armor"', '"kai:sru-mk20"')
game = game.replace('const val WEAPON_NAME = "W.W Magnum"', 'const val WEAPON_NAME = "SRU Assault Rifle MK19"')
game = game.replace('const val ARMOR_NAME = "Blackblood Armor & linked modules"', 'const val ARMOR_NAME = "SRU-MK20"')
game = game.replace(
    'key.contains("w.w magnum") || key.contains("white wraith") || key.contains("wraith magnum") -> "weapon"',
    'key.contains("sru assault rifle mk19") || key.contains("sru-assault-rifle-mk19") || key.contains("white wraith") || key.contains("w.w magnum") || key.contains("wraith magnum") -> "weapon"'
)
game = game.replace(
    'key.contains("blackblood armor") || key.contains("black blood armor") -> "armor"',
    'key.contains("sru-mk20") || key.contains("sru mk20") || key.contains("blackblood armor") || key.contains("black blood armor") -> "armor"'
)
if 'KAI_SRU_MK19_ID' not in game or 'SRU Assault Rifle MK19' not in game or 'SRU-MK20' not in game:
    raise RuntimeError('Kai R10 equipment migration failed')

# Lucia is a known story character but does not begin in Party. The CharacterState is
# seeded as MISSING so the Core can persist identity/equipment without pretending first
# contact already happened.
lucia_seed = '''        LUCIA_ID to LuciaCanon.character(),\n'''
if lucia_seed not in game:
    anchor = '        AN_NHIEN_ID to AnNhienCanon.character()\n'
    if anchor not in game:
        raise RuntimeError('Lucia seed anchor missing after An Nhiên finalizer')
    game = game.replace(anchor, '        AN_NHIEN_ID to AnNhienCanon.character(),\n' + lucia_seed, 1)
if 'LUCIA_ID to LuciaCanon.inventory()' not in game:
    anchor = '        AN_NHIEN_ID to AnNhienCanon.inventory()\n'
    if anchor not in game:
        raise RuntimeError('Lucia inventory seed anchor missing')
    game = game.replace(anchor, '        AN_NHIEN_ID to AnNhienCanon.inventory(),\n        LUCIA_ID to LuciaCanon.inventory()\n', 1)
if 'LUCIA_ID to LuciaCanon.equipment()' not in game:
    anchor = '        AN_NHIEN_ID to AnNhienCanon.equipment()\n'
    if anchor not in game:
        raise RuntimeError('Lucia equipment seed anchor missing')
    game = game.replace(anchor, '        AN_NHIEN_ID to AnNhienCanon.equipment(),\n        LUCIA_ID to LuciaCanon.equipment()\n', 1)
game_state_path.write_text(game, encoding="utf-8")

# Preserve old-save scan/copy fields in the schema, but retire the legacy R05 actions.
omni_path = CORE / "OmnivaultEngine.kt"
omni = omni_path.read_text(encoding="utf-8")
omni = replace_once(
    omni,
    '''      OmnivaultCommand.Operation.SCAN -> scan(state, command)\n      OmnivaultCommand.Operation.COPY -> copy(state, command)\n''',
    '''      OmnivaultCommand.Operation.SCAN -> scan(state, command)\n      OmnivaultCommand.Operation.COPY -> invalid(state, "omnivault_copy_retired_r10")\n''',
    'Omnivault copy retirement',
)
omni = replace_once(
    omni,
    '''    if (InventoryPolicy.isKaiSignatureEquipment(state, source)) return invalid(state, "signature_equipment_locked")\n    val slots = state.omnivault.scanSlots.toMutableList()\n''',
    '''    if (InventoryPolicy.isKaiSignatureEquipment(state, source)) return invalid(state, "signature_equipment_locked")\n    return invalid(state, "omnivault_scan_retired_r10")\n    @Suppress("UNREACHABLE_CODE")\n    val slots = state.omnivault.scanSlots.toMutableList()\n''',
    'Omnivault scan retirement',
)
omni_path.write_text(omni, encoding="utf-8")

# ---------------------------------------------------------------------------
# Lucia R03 core definition and save backfill.
# ---------------------------------------------------------------------------
lucia_path = CORE / "LuciaCanon.kt"
lucia_path.write_text('''package com.rabpit.backroom.core

const val LUCIA_ID = "lucia"

object LuciaCanon {
  const val NAME = "Lucia Lục"
  const val LEGAL_NAME = "Hứa Thuý Mai"
  const val AGE = 19
  const val M4A1_ID = "lucia:m4a1"
  const val KNIFE_ID = "lucia:combat-knife"
  const val WATCH_ID = "lucia:military-positioning-watch"
  const val AMMO_ID = "lucia:5.56x45-reserve"

  fun character(): CharacterState = CharacterState(
    id = LUCIA_ID,
    name = NAME,
    avatarRef = null,
    healthState = "100/100",
    presence = CharacterPresence.MISSING,
    physiology = PhysiologyState.freshRunBaseline(),
    metadata = mapOf(
      "legalName" to LEGAL_NAME,
      "militaryAlias" to NAME,
      "age" to AGE.toString(),
      "species" to "human",
      "gender" to "female",
      "nationality" to "Việt Nam",
      "heritage" to "Hoa Kiều",
      "familyLineage" to "Chít nội Gia tộc Họ Hứa",
      "serviceLength" to "1 năm",
      "trainingProgram" to "Việt Nam + Hoa Kỳ",
      "entranceResult" to "Xuất sắc",
      "combatRole" to "TACTICAL RIFLEWOMAN / COMBAT FOLLOWER",
      "powerScale" to "HUMAN_TRAINED",
      "supernaturalPower" to "false",
      "baseHp" to "100",
      "str" to "7",
      "df" to "7",
      "agi" to "8",
      "crit" to "7",
      "fixedEncounterLevel" to "0",
      "storyOwned" to "true",
      "requiresQuest" to "false",
      "randomSpawn" to "false",
      "relationshipKai" to "OPEN",
      "addressKai" to "OPEN",
      "inventoryProfile" to "lucia"
    )
  )

  fun inventory(): InventoryState = InventoryState(
    LUCIA_ID,
    mapOf(
      AMMO_ID to ItemStack(
        itemId = AMMO_ID,
        name = "5.56×45 mm NATO reserve",
        quantity = 90,
        metadata = mapOf("physicalAmmo" to "true", "loadedAtStart" to "60", "totalAtStart" to "150")
      )
    )
  )

  fun equipment(): EquipmentState = EquipmentState(
    LUCIA_ID,
    linkedMapOf(
      "weapon" to M4A1_ID,
      "knife" to KNIFE_ID,
      "watch" to WATCH_ID
    )
  )

  fun ensure(state: GameState): GameState {
    val character = state.characters[LUCIA_ID] ?: character()
    val inventory = state.inventories[LUCIA_ID] ?: inventory()
    val equipment = state.equipment[LUCIA_ID] ?: equipment()
    return state.copy(
      characters = state.characters + (LUCIA_ID to character),
      inventories = state.inventories + (LUCIA_ID to inventory),
      equipment = state.equipment + (LUCIA_ID to equipment)
    )
  }
}
''', encoding="utf-8")

continuity_path = CORE / "StoryCompanionContinuity.kt"
continuity_path.write_text('''package com.rabpit.backroom.core

/** Story-owned fixed contact gates. Models may narrate a committed event but may not roll or teleport it. */
object StoryCompanionContinuity {
  const val LUCIA_LEVEL = 0

  @JvmStatic fun isStoryOwned(characterId: String): Boolean = characterId.trim().lowercase() == LUCIA_ID
  @JvmStatic fun randomSpawnAllowed(characterId: String): Boolean = !isStoryOwned(characterId)
  @JvmStatic fun canMaterializeLucia(currentLevel: Int, alreadyEncountered: Boolean): Boolean =
    currentLevel == LUCIA_LEVEL && !alreadyEncountered
}
''', encoding="utf-8")

codec_path = CORE / "GameStateCodec.kt"
codec = codec_path.read_text(encoding="utf-8")
codec = replace_once(codec, '    return AnNhienCanon.ensure(decoded)\n', '    return LuciaCanon.ensure(AnNhienCanon.ensure(decoded))\n', 'Lucia save backfill')
codec_path.write_text(codec, encoding="utf-8")

inventory_policy_path = CORE / "InventoryPolicy.kt"
policy = inventory_policy_path.read_text(encoding="utf-8")
if 'val LUCIA = InventoryProfile(maxTypes = 8, maxPerType = 100)' not in policy:
    policy = replace_once(
        policy,
        '  val AN_NHIEN = InventoryProfile(maxTypes = 2, maxPerType = 20)\n',
        '  val AN_NHIEN = InventoryProfile(maxTypes = 2, maxPerType = 20)\n  val LUCIA = InventoryProfile(maxTypes = 8, maxPerType = 100)\n',
        'Lucia inventory profile',
    )
    policy = replace_once(
        policy,
        '    if (characterId == AN_NHIEN_ID) return AN_NHIEN\n',
        '    if (characterId == AN_NHIEN_ID) return AN_NHIEN\n    if (characterId == LUCIA_ID) return LUCIA\n',
        'Lucia inventory routing',
    )
inventory_policy_path.write_text(policy, encoding="utf-8")

facade_path = CORE / "GameCoreFacade.kt"
facade = facade_path.read_text(encoding="utf-8")
facade = facade.replace('"an nhien" to AN_NHIEN_ID)', '"an nhien" to AN_NHIEN_ID, "lucia" to LUCIA_ID, "lucia lục" to LUCIA_ID, "hứa thuý mai" to LUCIA_ID, "thuy mai" to LUCIA_ID)')
facade_path.write_text(facade, encoding="utf-8")

# ---------------------------------------------------------------------------
# Final Android story-owned Lucia contact. Existing An Nhiên mandatory encounter keeps
# priority, then Lucia is guaranteed on a later physical Level-0 turn. This avoids two
# fixed contacts being materialized in one turn without making Lucia random.
# ---------------------------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")
helper_anchor = '''  private boolean anNhienEncountered(JSONObject state) {\n'''
if 'private boolean luciaEncountered(JSONObject state)' not in main:
    idx = main.find(helper_anchor)
    if idx < 0:
        raise RuntimeError('Lucia Java helper anchor missing')
    # Insert the helper before the An Nhiên helper so both remain independent.
    helper = '''  private boolean luciaEncountered(JSONObject state) {
    if (partyHas(state, "Lucia") || partyHas(state, "lucia") || partyHas(state, "Hứa Thuý Mai")) return true;
    JSONObject flags = state.optJSONObject("flags");
    JSONObject record = flags != null ? flags.optJSONObject("lucia") : null;
    return record != null && record.optBoolean("encountered", false);
  }

'''
    main = main[:idx] + helper + main[idx:]

if 'boolean luciaSeen = luciaEncountered(state);' not in main:
    anchor = '    boolean anNhienEncountered = anNhienEncountered(state);\n'
    if anchor not in main:
        raise RuntimeError('Lucia gameplay-state anchor missing')
    main = main.replace(anchor, anchor + '    boolean luciaSeen = luciaEncountered(state);\n', 1)

if 'thresholdRoll("luciaEncounter", 1, 1' not in main:
    anchor = '    rolls.put("anNhienEncounter", thresholdRoll("anNhienEncounter", 1, 1, level == 0 && physical && !anNhienEncountered, " mandatory Level 0 follower"));\n'
    if anchor not in main:
        raise RuntimeError('Lucia fixed encounter roll anchor missing')
    main = main.replace(
        anchor,
        anchor + '    rolls.put("luciaEncounter", thresholdRoll("luciaEncounter", 1, 1, level == 0 && physical && anNhienEncountered && !luciaSeen, " story-owned fixed Level 0 contact"));\n',
        1,
    )

# Surface the fixed contact as a valid character encounter snapshot.
old_snapshot = '    else if (kind.equals("character_encounter")) allowed = rollSuccess(rolls, "anNhienEncounter") || rollSuccess(rolls, "survivor") || rollSuccess(rolls, "irisReunion") || rollSuccess(rolls, "syvialReunion");\n'
new_snapshot = '    else if (kind.equals("character_encounter")) allowed = rollSuccess(rolls, "anNhienEncounter") || rollSuccess(rolls, "luciaEncounter") || rollSuccess(rolls, "survivor") || rollSuccess(rolls, "irisReunion") || rollSuccess(rolls, "syvialReunion");\n'
if new_snapshot not in main:
    main = replace_once(main, old_snapshot, new_snapshot, 'Lucia snapshot authority')

# Commit only first contact/presence. Lucia is NOT auto-added to Party and relationship/address remain OPEN.
commit_marker = '    state.put("flags", flags);\n    return state;\n  }\n'
commit_idx = main.rfind(commit_marker)
if commit_idx < 0:
    raise RuntimeError('Lucia encounter commit tail missing')
preceding = main[max(0, commit_idx - 7000):commit_idx]
if 'boolean anNhienNow' not in preceding:
    raise RuntimeError('Lucia encounter commit did not resolve the authoritative state tail')
if 'lucia.put("encountered", true)' not in main:
    lucia_commit = '''    boolean luciaNow = luciaEncountered(before) || rollSuccess(rolls, "luciaEncounter");
    if (luciaNow) {
      JSONObject lucia = flags.optJSONObject("lucia");
      if (lucia == null) lucia = new JSONObject();
      lucia.put("exists", true)
        .put("encountered", true)
        .put("present", true)
        .put("spawned", true)
        .put("storyOwned", true)
        .put("fixedEncounterLevel", 0)
        .put("requiresQuest", false)
        .put("randomSpawn", false)
        .put("follower", false)
        .put("followerCandidate", true)
        .put("identityKnown", lucia.optBoolean("identityKnown", false))
        .put("joinConfirmed", false);
      flags.put("lucia", lucia);
    }
'''
    main = main[:commit_idx] + lucia_commit + main[commit_idx:]

# Writer contract: fixed first contact is engine-owned, not a random survivor result.
prompt_anchor = 'ENTITY ROAMING HARD LOCK:'
if prompt_anchor not in main:
    raise RuntimeError('Lucia final writer prompt anchor missing')
if 'LUCIA R03 HARD LOCK:' not in main:
    prompt_insert = (
        'LUCIA R03 HARD LOCK: Lucia Lục là biệt danh quân đội của Hứa Thuý Mai, nữ 19 tuổi, con người Việt Nam, Hoa Kiều, '
        'một năm quân ngũ Việt Nam-Hoa Kỳ và đầu vào xuất sắc nhưng power-scale vẫn HUMAN_TRAINED. Lucia là fixed story-owned '
        'Level 0 encounter: không quest, không random spawn, không thuộc nhóm SRU ban đầu. luciaEncounter success=true là first contact '
        'được Core commit; không tự thêm Lucia vào Party trong cùng lượt. Quan hệ và xưng hô Lucia-Kai vẫn OPEN. '
    )
    pos = main.find('"ENTITY ROAMING HARD LOCK:')
    if pos < 0:
        raise RuntimeError('Lucia prompt insertion point missing')
    line_start = main.rfind('      ', 0, pos)
    main = main[:line_start] + f'      "{prompt_insert}" +\n' + main[line_start:]

MAIN.write_text(main, encoding="utf-8")

# ---------------------------------------------------------------------------
# Knowledge DB R07: update Kai/Iris/Syvial and add Lucia without leaking OPEN fields.
# ---------------------------------------------------------------------------
db = json.loads(KNOWLEDGE.read_text(encoding="utf-8"))
records = db.get("records")
if not isinstance(records, list):
    raise RuntimeError('knowledge_db.json records missing')
by_id = {r.get('id'): r for r in records if isinstance(r, dict)}

def put_record(record):
    rid = record['id']
    old = by_id.get(rid)
    if old is None:
        records.append(record)
        by_id[rid] = record
    else:
        old.clear(); old.update(record)

kai_runtime = by_id.get('CHAR.KAI.RUNTIME_CORE')
if not kai_runtime:
    raise RuntimeError('Kai runtime knowledge record missing')
kai_runtime['text'] = (
    'Kai Akechi / Twilight: Đội trưởng SRU thuộc Cảnh Sát chống hiện tượng dị thường, origin era 2299, true age unknown, apparent ~30, Catholic, UR+. '
    'Hồ sơ SRU công khai phân loại Kai là con người. Writer-side TUYỆT MẬT / KNOWLEDGE LOCK: hắn thực sự là bán nhân/bán quỷ, con Sparda và Eve; '
    'không nhân vật nào tự biết hoặc suy ra bí mật này từ sức mạnh. Kai là chỉ huy tác chiến và xạ thủ tối thượng; ngoài nguy hiểm có thể lười, trêu/châm chọc, '
    'nhưng khi nguy hiểm thật thì quan sát có kỷ luật và quyết định dứt khoát. Current signature gear: SRU-MK20, SRU Assault Rifle MK19, Omnivault Ring.'
)
kai_runtime['source'] = {'document':'02_CHARACTERS/Kai_Codex.docx','anchor':'KAI-QUICK-01; KAI-ID-01; KAI-PER-01; KAI-SECRET-01'}
kai_runtime['tags'] = ['kai','twilight','sru','r10','knowledge lock']

for rid, text, anchor, priority, tags, refs in [
    ('CHAR.KAI.SPARDA_CORE', 'Writer-side KNOWLEDGE LOCK: Sparda Core provides Kai infinite demon power and supports physical ability, cognition, regeneration, demonic ammunition and self-repair of currently equipped gear. No intrinsic energy cap/cooldown/depletion. Observers do not automatically know its name, lineage or mechanics.', 'KAI-CORE-SPARDA-01', 44, ['kai','sparda core','knowledge lock'], []),
    ('CHAR.KAI.DEVIL_TRIGGER', 'Kai Devil Trigger releases his existing power without a second personality, berserk state, corruption, intrinsic duration cap, cooldown or backlash. Kai keeps memory, judgment and control.', 'KAI-DT-01', 48, ['kai','devil trigger'], []),
    ('CHAR.KAI.GUILTY_CROWN_OVERRIDE', 'Guilty Crown Override: while in Devil Trigger, external time is completely stopped and Kai fires exactly 24 demonic 5.56×45 mm rounds with SRU Assault Rifle MK19. After shot 24, external time resumes. Do not change the count or restore White Wraith as the current weapon.', 'KAI-ULT-GCO-01', 54, ['kai','guilty crown override','override'], ['CHAR.KAI.DEVIL_TRIGGER','CHAR.KAI.SRU_AR_MK19']),
    ('CHAR.KAI.ARMOR', 'SRU-MK20 is Kai current SRU powered armor, replacing legacy Blackblood equipment. It amplifies movement/strength and integrates the old limb-module functions into the armor itself. Currently equipped gear self-repairs from Sparda Core. Demon Jaw/Talon/Phantom are not current separate equipment.', 'KAI-EQP-SRU-MK20-01', 48, ['kai','sru-mk20','armor'], []),
    ('CHAR.KAI.OMNIVAULT', 'Omnivault Ring R10 only has unlimited storage for inanimate objects and Restore/Hoàn nguyên of an existing item. Living beings are forbidden. Scan, Copy, duplicate/item creation, Marked and Upgrade are retired. Successful Restore has a per-item 24-hour cooldown; currently equipped Kai gear already self-repairs through Sparda Core.', 'KAI-EQP-OMNIVAULT-01', 46, ['kai','omnivault','nhẫn vạn tàng','restore','hoàn nguyên'], []),
]:
    put_record({'id':rid,'domain':'CHARACTER','kind':'ability' if 'CORE' in rid or 'TRIGGER' in rid else ('ultimate' if 'GUILTY' in rid else 'equipment'),'text':text,'source':{'document':'02_CHARACTERS/Kai_Codex.docx','anchor':anchor},'authority':'CHARACTER_CANON','mutability':'IMMUTABLE','priority':priority,'tags':tags,'references':refs,'affordances':['direct_threat'] if rid != 'CHAR.KAI.OMNIVAULT' else ['item_manipulation']})

# Retire the old White Wraith record ID and replace it with current MK19.
old_ww = by_id.pop('CHAR.KAI.WHITE_WRAITH', None)
if old_ww is not None:
    records.remove(old_ww)
put_record({
    'id':'CHAR.KAI.SRU_AR_MK19','domain':'CHARACTER','kind':'equipment',
    'text':'SRU Assault Rifle MK19 is Kai current signature firearm: physical 5.56×45 mm NATO, 30-round magazine, 2.88 kg empty / about 3.4 kg loaded, 700–950 rpm with full-auto, 368 mm barrel, 838/756 mm length, effective range about 500–600 m. Sparda demonic 5.56 ammunition forms directly from Sparda Core, does not consume physical stock and has physical destructive power tens of times above physical ammunition under R10.',
    'source':{'document':'02_CHARACTERS/Kai_Codex.docx','anchor':'KAI-EQP-MK19-01'},'authority':'CHARACTER_CANON','mutability':'IMMUTABLE','priority':48,
    'tags':['kai','sru assault rifle mk19','mk19','5.56x45'],'references':[],'affordances':['direct_threat']
})

iris = by_id.get('CHAR.IRIS.RUNTIME_CORE')
if iris:
    iris['text'] = ('Iris / ARGUS: SRU Scout / Target Eliminator under Kai, with Syvial as deputy. Half-human/half-demon daughter of Belial and a human mother; true age and origin era UNKNOWN, apparent ~18. She is a real ranged Gunslinger/Scout Marksman, not a remote drone intelligence station. Calm, decisive, sharp, brave and caring. Belial Core provides infinite demon power; exact combat tier must not be inferred as UR+ merely from Kai/Syvial. Iris has feelings for Kai, Kai knows, current baseline remains teammates. Preserve UNKNOWN fields.')
    iris['source'] = {'document':'02_CHARACTERS/Iris_Codex.docx','anchor':'IRIS-QUICK-01; IRIS-ID-01; IRIS-PER-01; IRIS-UNKNOWN-01'}
    iris['tags'] = ['iris','argus','sru','r06','present core']
argus = by_id.get('CHAR.IRIS.ARGUS')
if argus:
    argus['text'] = ('ARGUS Terrain Read combines Iris direct observation, current armor sensors and analysis of terrain, landmarks, approach/escape routes, lines of sight, cover, ambush points and abnormal traces. It is not drones/tablet, omniscience, wall vision, remote-camera generation or automatic supernatural true-form detection. Distorted geometry and bad sensor data can mislead it.')
    argus['source'] = {'document':'02_CHARACTERS/Iris_Codex.docx','anchor':'IRIS-SCOUT-TERRAIN-01; IRIS-CANON-GATE-01'}
rel_is = by_id.get('REL.IRIS.SYVIAL.BASELINE')
if rel_is:
    rel_is['text'] = ('Iris and Syvial are friends and trusted SRU teammates with romantic rivalry around Kai. Jealousy/bickering does not turn the baseline into hostility or mission sabotage. Exact Iris↔Syvial address remains UNKNOWN unless continuity locks it.')
    rel_is['source'] = {'document':'02_CHARACTERS/Iris_Codex.docx','anchor':'IRIS-REL-SYVIAL-01; IRIS-UNKNOWN-01'}

syvial = by_id.get('CHAR.SYVIAL.RUNTIME_CORE')
if syvial:
    syvial['text'] = ('Syvial: SRU deputy under Kai; half-human/half-demon daughter of Lucifer and a human mother whose identity is not locked; origin era 2299, true age unknown. UR+, same overall power tier as Kai, high-tier supernatural swordswoman using purely mechanical GodKiller, Lucifer Core and Devil Trigger. Outside combat she is natural/social/teasing; in danger focused and precise. Her yandere feelings toward Kai are extremely strong but she remains lucid, intelligent and socially capable, values Kai freely choosing her, and jealousy never deletes tactical competence.')
    syvial['source'] = {'document':'02_CHARACTERS/Syvial_Codex.docx','anchor':'SYVIAL-QUICK-01; SYVIAL-ID-01; SYVIAL-YANDERE-01; SYVIAL-ACTION-LOCK-01'}
    syvial['tags'] = ['syvial','sru','deputy','ur+','r04','present core']

lucia_records = [
  {'id':'CHAR.LUCIA.RUNTIME_CORE','domain':'CHARACTER','kind':'runtime-card','text':'Hứa Thuý Mai, military alias/callsign Lucia Lục: female, 19, human, Vietnamese, Hoa Kiều, chít nội Gia tộc Họ Hứa. One year military service under a Vietnam+US training program; entrance result excellent. Power scale is HUMAN_TRAINED: a well-trained normal human, never UR+/supernatural and never granted Core, Devil Trigger, time-stop, supernatural healing or SRU powered armor. Current role: Tactical Riflewoman / combat follower and battlefield scout. Relationship/address with Kai, Iris and Syvial remain OPEN unless continuity locks them.','source':{'document':'02_CHARACTERS/Lucia_Codex.docx','anchor':'00; 01; 02; 04'},'authority':'CHARACTER_CANON','mutability':'IMMUTABLE','priority':20,'tags':['lucia','lucia lục','hứa thuý mai','thuy mai','human trained'],'references':['STORY.LUCIA.LEVEL0_FIXED_ENCOUNTER','REL.KAI.LUCIA.OPEN'],'affordances':[]},
  {'id':'CHAR.LUCIA.M4A1','domain':'CHARACTER','kind':'equipment','text':'Lucia uses a customized black M4A1 with adjustable stock, modular rail handguard, optic, foregrip, suppressor and support light/laser; the navigation laser is green 5 mW and is not a supernatural sensor. Starting physical ammunition is 150 total: 60 in main magazines + 90 reserve. A black thigh-holstered pistol is visually locked but its model/caliber/ammo/damage/runtime use remain OPEN. She also carries a combat knife and military positioning/watch equipment.','source':{'document':'02_CHARACTERS/Lucia_Codex.docx','anchor':'00; 03; 05'},'authority':'CHARACTER_CANON','mutability':'IMMUTABLE','priority':48,'tags':['lucia','m4a1','green laser','5mw'],'references':[],'affordances':['direct_threat','trace_analysis']},
  {'id':'CHAR.LUCIA.GAMEPLAY','domain':'CHARACTER','kind':'runtime-card','text':'Lucia gameplay baseline: HP 100, STR 7, DF 7, AGI 8, CRIT 7. Gameplay recovery is +2 HP after every 3 distinct completed turns and must not be narrated as supernatural healing without retcon. Inventory capacity is 8 item types, up to 100 units per type; Equipment is separate. These are gameplay locks and do not raise her beyond normal human physiology.','source':{'document':'02_CHARACTERS/Lucia_Codex.docx','anchor':'00; 06; 07'},'authority':'CHARACTER_CANON','mutability':'IMMUTABLE','priority':46,'tags':['lucia','hp 100','str 7','df 7','agi 8','crit 7'],'references':[],'affordances':[]},
  {'id':'CHAR.LUCIA.NAVIGATION','domain':'CHARACTER','kind':'ability','text':'At Level 0 Lucia uses a wall as an anchor, expands in a spiral, marks with chalk and uses a green 5 mW laser across nearby surfaces. Because Level 0 geometry/Layout Resistance is nonlinear, this method is evidence gathering rather than an absolute map. Around hour four she may suspect a Hound from sound, but she does not know an Entity is present as fact without verification; Level 0 has no confirmed resident Entity baseline.','source':{'document':'02_CHARACTERS/Lucia_Codex.docx','anchor':'05; 09; KNOWLEDGE LOCK'},'authority':'CHARACTER_CANON','mutability':'IMMUTABLE','priority':44,'tags':['lucia','navigation','chalk','laser','level 0'],'references':['LEVEL.00'],'affordances':['trace_analysis']},
  {'id':'STORY.LUCIA.LEVEL0_FIXED_ENCOUNTER','domain':'STORY','kind':'continuity','text':'Lucia is a fixed, story-owned Level 0 encounter. It requires no quest and uses no random spawn. Core decides materialization; LiteRT/Gemini must not roll, teleport or invent her independently. Initial Backrooms entry participants remain Kai, Iris and Syvial only. Meeting Lucia at Level 0 does not make her SRU, Async-affiliated or from 2299. First contact does not automatically add her to Party.','source':{'document':'02_CHARACTERS/Lucia_Codex.docx','anchor':'00; 01; 10; 12'},'authority':'CHARACTER_CANON','mutability':'IMMUTABLE','priority':18,'tags':['lucia','fixed encounter','level 0','story owned'],'references':['CHAR.LUCIA.RUNTIME_CORE'],'affordances':[]},
  {'id':'REL.KAI.LUCIA.OPEN','domain':'RELATIONSHIP','kind':'open-edge','text':'Kai↔Lucia relationship and address system are OPEN in Lucia R03. Do not guess anh-em, tôi-anh, romance, command hierarchy or intimacy from age/gender/first contact. A later continuity event may lock these fields.','source':{'document':'02_CHARACTERS/Lucia_Codex.docx','anchor':'00; OPEN; 10'},'authority':'CHARACTER_CANON','mutability':'OPEN','priority':24,'tags':['kai','lucia','relationship','address open'],'references':[],'affordances':['dialogue']},
]
for r in lucia_records:
    put_record(r)
KNOWLEDGE.write_text(json.dumps(db, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

# ---------------------------------------------------------------------------
# Knowledge router R07: current Kai weapon, Lucia presence/contact and OPEN relation.
# ---------------------------------------------------------------------------
kce = KCE.read_text(encoding='utf-8')
kce = kce.replace('if (hasAny(actionText, "white wraith", "magnum")) direct += "CHAR.KAI.WHITE_WRAITH"', 'if (hasAny(actionText, "sru assault rifle mk19", "mk19", "white wraith", "magnum")) direct += "CHAR.KAI.SRU_AR_MK19"')
if 'if ("lucia" in presentActors) add("CHAR.LUCIA.RUNTIME_CORE"' not in kce:
    kce = replace_once(kce,
      '      if ("syvial" in presentActors) add("CHAR.SYVIAL.RUNTIME_CORE", "present actor runtime core")\n',
      '      if ("syvial" in presentActors) add("CHAR.SYVIAL.RUNTIME_CORE", "present actor runtime core")\n      if ("lucia" in presentActors) add("CHAR.LUCIA.RUNTIME_CORE", "present actor runtime core")\n',
      'Lucia runtime card routing')
if 'REL.KAI.LUCIA.OPEN' not in kce:
    anchor = '      if ("iris" in presentActors && "syvial" in presentActors) add("REL.IRIS.SYVIAL.BASELINE", "present relationship edge")\n'
    kce = replace_once(kce, anchor, anchor + '      if ("lucia" in presentActors) add("REL.KAI.LUCIA.OPEN", "present OPEN relationship edge")\n', 'Lucia relationship routing')
if 'direct += "CHAR.LUCIA.RUNTIME_CORE"' not in kce:
    anchor = '      val direct = linkedSetOf<String>()\n'
    kce = replace_once(kce, anchor, anchor + '      if (hasAny(actionText, "lucia", "lucia lục", "hứa thuý mai", "thuy mai", "thuý mai")) direct += "CHAR.LUCIA.RUNTIME_CORE"\n', 'Lucia direct lookup')
# Resolve Lucia from both supported party shapes.
kce = kce.replace('if (id.contains("syvial")) presentActors += "syvial"\n', 'if (id.contains("syvial")) presentActors += "syvial"\n              if (id.contains("lucia") || id.contains("thuý mai") || id.contains("thuy mai")) presentActors += "lucia"\n')
kce = kce.replace('          "communication", "exploration", "iris", "syvial", "reunionPath",\n', '          "communication", "exploration", "iris", "syvial", "lucia", "reunionPath",\n')
if 'STORY.LUCIA.LEVEL0_FIXED_ENCOUNTER' not in kce:
    anchor = '      val flags = state.optJSONObject("flags")\n'
    # use the occurrence inside addStateDrivenRecords, selected by method boundary
    method = kce.find('    private fun addStateDrivenRecords() {')
    pos = kce.find(anchor, method)
    if method < 0 or pos < 0:
        raise RuntimeError('Lucia story routing anchor missing')
    insert_at = pos + len(anchor)
    kce = kce[:insert_at] + '      if (currentLevel() == 0) add("STORY.LUCIA.LEVEL0_FIXED_ENCOUNTER", "Level 0 story-owned fixed contact")\n' + kce[insert_at:]
# Do not pull Lucia affordance records into scenes where Lucia is absent.
if 'id.startsWith("CHAR.LUCIA.")' not in kce:
    anchor = '          if (id.startsWith("CHAR.SYVIAL.") && "syvial" !in presentActors) return@forEach\n'
    kce = replace_once(kce, anchor, anchor + '          if (id.startsWith("CHAR.LUCIA.") && "lucia" !in presentActors) return@forEach\n', 'Lucia affordance presence gate')
KCE.write_text(kce, encoding='utf-8')

# Final UI equipment names follow Kai R10. Legacy names may still exist in migration fixtures,
# but they must not be the player-facing current Equipment labels.
html = INDEX.read_text(encoding='utf-8')
html = html.replace('<span>W.W Magnum</span>', '<span>SRU Assault Rifle MK19</span>')
html = html.replace('<span>Blackblood Armor & linked modules</span>', '<span>SRU-MK20</span>')
INDEX.write_text(html, encoding='utf-8')

# Existing tests must use current Kai constants while legacy string fixtures stay available for migration coverage.
for test in TESTS.rglob('*.kt'):
    text = test.read_text(encoding='utf-8')
    text = text.replace('KAI_WHITE_WRAITH_ID', 'KAI_SRU_MK19_ID').replace('KAI_BLACKBLOOD_ARMOR_ID', 'KAI_SRU_MK20_ID')
    test.write_text(text, encoding='utf-8')

# Replace the one legacy Omnivault behavior regression with the R10 retired-actions contract.
core_test = TESTS / 'GameStateCoreTest.kt'
text = core_test.read_text(encoding='utf-8')
start_marker = '  @Test fun omnivaultThreeSlotsAndCopyRemainGameplayMechanics() {'
end_marker = '\n  @Test fun restoreIsNarrativeOnlyAndCannotMutateInventoryState() {'
if start_marker in text:
    start = text.index(start_marker)
    end = text.index(end_marker, start)
    replacement = '''  @Test fun omnivaultScanAndCopyAreRetiredByR10() {
    val withItem = StateReducer.execute(base(), item("original-1", ItemCommand.Operation.PICKUP)).state
    val scanned = StateReducer.execute(withItem, OmnivaultCommand(
      "scan", "TURN_1", KAI_ID, source = CommandSource.RULE,
      operation = OmnivaultCommand.Operation.SCAN, itemId = "original-1", itemName = "Item 1"
    ))
    assertFalse(scanned.applied)
    assertEquals("omnivault_scan_retired_r10", scanned.validation.reason)
    assertTrue(scanned.state.omnivault.scanSlots.isEmpty())

    val copied = StateReducer.execute(withItem, OmnivaultCommand(
      "copy", "TURN_1", KAI_ID, source = CommandSource.RULE,
      operation = OmnivaultCommand.Operation.COPY, itemId = "original-1", itemName = "Item 1"
    ))
    assertFalse(copied.applied)
    assertEquals("omnivault_copy_retired_r10", copied.validation.reason)
  }
'''
    text = text[:start] + replacement + text[end:]
core_test.write_text(text, encoding='utf-8')

# Focused regression tests created in the generated source tree.
(TESTS / 'CharacterCanonR07Test.kt').write_text('''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CharacterCanonR07Test {
  @Test fun kaiStartsWithR10SruEquipment() {
    val state = GameState.initial()
    assertEquals(KAI_SRU_MK19_ID, state.equipment.getValue(KAI_ID).slots["weapon"])
    assertEquals(KAI_SRU_MK20_ID, state.equipment.getValue(KAI_ID).slots["armor"])
    assertEquals("SRU Assault Rifle MK19", KaiStartingEquipment.displayName(KAI_SRU_MK19_ID))
    assertEquals("SRU-MK20", KaiStartingEquipment.displayName(KAI_SRU_MK20_ID))
  }

  @Test fun luciaIsKnownButNotInitiallyInParty() {
    val state = GameState.initial()
    val lucia = state.characters.getValue(LUCIA_ID)
    assertEquals("Lucia Lục", lucia.name)
    assertEquals(CharacterPresence.MISSING, lucia.presence)
    assertFalse(LUCIA_ID in state.party.memberIds)
    assertEquals("HUMAN_TRAINED", lucia.metadata["powerScale"])
    assertEquals("OPEN", lucia.metadata["addressKai"])
    assertEquals(90, state.inventories.getValue(LUCIA_ID).items.getValue(LuciaCanon.AMMO_ID).quantity)
    assertEquals(LuciaCanon.M4A1_ID, state.equipment.getValue(LUCIA_ID).slots["weapon"])
  }

  @Test fun luciaEncounterIsStoryOwnedAndNeverRandom() {
    assertTrue(StoryCompanionContinuity.isStoryOwned(LUCIA_ID))
    assertFalse(StoryCompanionContinuity.randomSpawnAllowed(LUCIA_ID))
    assertTrue(StoryCompanionContinuity.canMaterializeLucia(0, false))
    assertFalse(StoryCompanionContinuity.canMaterializeLucia(1, false))
    assertFalse(StoryCompanionContinuity.canMaterializeLucia(0, true))
  }

  @Test fun luciaInventoryCapacityMatchesR03() {
    val state = GameState.initial()
    val profile = InventoryPolicy.profileFor(state, LUCIA_ID)
    assertEquals(8, profile.maxTypes)
    assertEquals(100, profile.maxPerType)
  }
}
''', encoding='utf-8')

# Final fail-closed audit.
final_game = game_state_path.read_text(encoding='utf-8')
final_omni = omni_path.read_text(encoding='utf-8')
final_db = KNOWLEDGE.read_text(encoding='utf-8')
final_main = MAIN.read_text(encoding='utf-8')
for marker in ['KAI_SRU_MK19_ID', 'KAI_SRU_MK20_ID', 'SRU Assault Rifle MK19', 'SRU-MK20', 'LUCIA_ID to LuciaCanon.character()']:
    if marker not in final_game:
        raise RuntimeError(f'Character Canon R07 GameState marker missing: {marker}')
for marker in ['omnivault_scan_retired_r10', 'omnivault_copy_retired_r10']:
    if marker not in final_omni:
        raise RuntimeError(f'Character Canon R07 Omnivault marker missing: {marker}')
for marker in ['CHAR.KAI.SRU_AR_MK19', 'CHAR.LUCIA.RUNTIME_CORE', 'STORY.LUCIA.LEVEL0_FIXED_ENCOUNTER', 'REL.KAI.LUCIA.OPEN']:
    if marker not in final_db:
        raise RuntimeError(f'Character Canon R07 knowledge marker missing: {marker}')
for marker in ['thresholdRoll("luciaEncounter", 1, 1', 'lucia.put("encountered", true)', 'LUCIA R03 HARD LOCK:']:
    if marker not in final_main:
        raise RuntimeError(f'Character Canon R07 Android marker missing: {marker}')
if 'CHAR.KAI.WHITE_WRAITH"' in final_db:
    raise RuntimeError('Retired White Wraith knowledge record survived R10')

print('Character Canon R07 applied: Kai R10, Iris R06, Syvial R04, Lucia R03 fixed Level-0 contact.')
