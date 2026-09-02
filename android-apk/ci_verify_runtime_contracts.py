from pathlib import Path

root = Path(__file__).resolve().parent
core = root / 'app/src/main/java/com/rabpit/backroom/core'
tests = root / 'app/src/test/java/com/rabpit/backroom/core'
html = (root / 'app/src/main/assets/index.html').read_text(encoding='utf-8')
java = (root / 'app/src/main/java/com/rabpit/backroom/MainActivity.java').read_text(encoding='utf-8')
facade = (core / 'GameCoreFacade.kt').read_text(encoding='utf-8')
gradle = (root / 'app/build.gradle').read_text(encoding='utf-8')
madgod = (core / 'MadGodCanon.kt').read_text(encoding='utf-8')
engines = (core / 'Engines.kt').read_text(encoding='utf-8')
intent = (core / 'IntentPipeline.kt').read_text(encoding='utf-8')
world_ledger = (core / 'WorldItemLedger.kt').read_text(encoding='utf-8')
knowledge_context = (core / 'knowledge/KnowledgeContextEngine.kt').read_text(encoding='utf-8')
semantic_mapper = (core / 'SemanticActionMapper.kt').read_text(encoding='utf-8')
equipment_system = (core / 'CharacterEquipmentSystem.kt').read_text(encoding='utf-8')
item_catalog = (core / 'ItemCatalog.kt').read_text(encoding='utf-8')
item_system = (core / 'ItemSystem.kt').read_text(encoding='utf-8')
removal_tests = (tests / 'MadGodRemovalLootCapacityTest.kt').read_text(encoding='utf-8')
status_tests = (tests / 'CharacterStatusEquipmentSystemTest.kt').read_text(encoding='utf-8')
save_repo = (core / 'SaveRepository.kt').read_text(encoding='utf-8')
combat = (core / 'CombatRuntime.kt').read_text(encoding='utf-8')
skill_catalog = (core / 'CompanionSkillCatalog.kt').read_text(encoding='utf-8')
detail_json = (core / 'CharacterDetailJson.kt').read_text(encoding='utf-8')
an_nhien = (core / 'AnNhienCanon.kt').read_text(encoding='utf-8')
skill_tests = (tests / 'CompanionSkillCatalogTest.kt').read_text(encoding='utf-8')
item_interaction_tests = (tests / 'ItemInteractionCoherenceTest.kt').read_text(encoding='utf-8')
passive_skill_tests = (tests / 'PassiveSkillVisibilityTest.kt').read_text(encoding='utf-8')
runtime_chain = (root / 'ci_apply_runtime_patches.py').read_text(encoding='utf-8')
chain = (root / 'patch-inventory-capacity-final-fix.py').read_text(encoding='utf-8')
scp_finalize = (root / 'patch-scp-173-compat-finalize.py').read_text(encoding='utf-8')
entity_dir = root / 'app/src/main/assets/entity'

required = [
    ('object SemanticActionMapper', semantic_mapper),
    ('SemanticActionDescriptor("candidate-$index", rule.semanticDescriptions)', (core / 'RegisteredLevelActionCoordinator.kt').read_text(encoding='utf-8')),
    ('resolvedExecuteActionId = resolvedExecuteActionId', (core / 'RegisteredLevelActionCoordinator.kt').read_text(encoding='utf-8')),
    ('put("reason", "core_owned_hidden_blueprint")', facade),
    ("versionCode 100", gradle), ("versionName '1.4.4.1'", gradle),
    ('devilBlessingEvasionBonus', combat), ('fun partyBlessing(value: Int)', equipment_system),
    ('return maxOf(1, (base * 5 + 99) / 100)', equipment_system),
    ('KAI_DEVIL_WITHIN_MAX_HP = 5678', combat), ('KAI_DEVIL_WITHIN_STAT_PERCENT = 70', combat),
    ('KAI_DEVIL_WITHIN_SPARDA_PROC_PERCENT = 20', combat), ('KAI_DEVIL_WITHIN_RED_ROSARY_ROUNDS = 13', combat),
    ('KAI_DEVIL_WITHIN_DEAD_SILENCE_BLEED_TURNS = 3', combat), ('KAI_DEVIL_WITHIN_GUNSLINGER_DAMAGE_PERCENT = 5', combat),
    ('KAI_DEVIL_WITHIN_REGEN_PERCENT = 5', combat), ('kai_the_devil_within', java), ('Kai-TheDevilWithin.png', java),
    ('id="searchActionButton"', html), ('id="exploreActionButton"', html), ('STEP2_THREE_ACTIONS', html),
    ('id="equipmentDetailModal"', html), ('window.renderCharacterStatusEquipment=render;', html),
    ('id="characterSkillsModal"', html), ("button.id='characterSkillsButton'", html), ('function openSkills()', html),
    ('const val CHEAT_CODE = ""', madgod), ('fun cheat(x: String): Boolean = false', madgod),
    ('ItemCommand.Operation.EQUIP -> EquipmentEngine.equip(state, command)', engines),
    ('ItemCommand.Operation.UNEQUIP -> EquipmentEngine.unequip(state, command)', engines),
    ('private const val SCHEMA_VERSION = "2"', equipment_system),
    ('private fun retiredMadGodId(', equipment_system),
    ('CharacterStatEngine.preserveMissingHp', equipment_system),
    ('fun applyCompletedTurnRegen', equipment_system),
    ('regenRunsExactlyOnceAndZeroHpCannotBeRescued', status_tests),
    ('inventoryOwnsSameItemReferencedByEquipment', status_tests),
    ('const val DROP_CHANCE_PERCENT = 100', item_catalog),
    ('WorldLootAcquisition.acquire(selected, lootId, KAI_ID)', item_catalog),
    ('"acquisitionSource" to parts[3]', item_catalog),
    ('const val BASE_EXPLORATION_BONUS_BASIS_POINTS = 500', item_catalog),
    ('base + BASE_EXPLORATION_BONUS_BASIS_POINTS + pity + follower', item_catalog),
    ('"kai" to ItemCapacity(14, 999)', item_system),
    ('"special_companion" to ItemCapacity(11, 20)', item_system),
    ('"lucia_gift_inventory" to ItemCapacity(8, 100)', item_system),
    ('"an_nhien_food_only" to ItemCapacity(7, 20)', item_system),
    ('"normal" to ItemCapacity(7, 2)', item_system),
    ('class MadGodRemovalLootCapacityTest', removal_tests),
    ('assertEquals(100, EntityLootEngine.dropChancePercent(state))', removal_tests),
    ('assertEquals(635, preview.threshold)', removal_tests),
    ('sceneKey:visualSceneKey()', java), ('r.sceneKey===visualSceneKey()', java),
    ('private int mentionedLevel(JSONObject state)', java),
    ('return exitFound && progressionReady(before);', java),
    ('exploration.put("minimumTurns", 6)', java),
    ('BACKROOMS_FANDOM_LEVELS_0_6_R01', java),
    ('return hasNextLinearArea(state) && levelTurns(state) >= 6;', java),
    ('recordLevelProgress(state, before, oldLevel, newLevel, areaAdvanced)', java),
    ('linearAreaPrompt(before)', java),
    ('partyDetails&&state.partyDetails.members', java),
    ('rolls.put("roamingEntityKey"', java), ('file:///android_asset/entity/', java),
    ('"hotel_corpse_lure","jeff_the_killer","jane_the_killer","slenderman"', java),
    ('String entityKey = rolls.optString("roamingEntityKey", "").trim();', java),
    ('requireGameCore().startCombatState(candidateState.toString(), canonicalKey);', java),
    ("function activeEntityKey(){var c=state&&state.combat;if(!c||c.active!==true)return '';return normalizeEntityKey(c.entityKey);}", java),
    ('private JSONObject resolveEntityOverlay(', java), ('window.backroomEntityOverlay=function(payload)', java),
    ('private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls)', java),
    ('forceEntityEncounterFlag(candidateState, rolls);', java),
    ('boolean exploreAction = "EXPLORE".equals(actionKindNormalized);', java),
    ('makeGameplayRolls(before, actionKind, action, meta)', java),
    ("box.style.position='relative';box.style.overflow='hidden'", java),
    ('Kai_new_overlay.png', java), ('Newviolet.png', java),
    ('class SharedPreferencesSaveRepository', save_repo),
    ('private fun normalizeVisualPresence(state: GameState): GameState', facade),
    ('val resolvedEntityKey = CombatRuntime.active(current)?.entityKey.orEmpty()', facade),
    ('flags.put("entityEncounterKey", "")', facade),
    ('next = next.copy(world = next.world + ("flagsJson" to flags.toString()))', facade),
    ('thresholdRoll("diepMinhEncounter", 10000, EntityEncounterPolicy.scaledThreshold(300),', java),
    ('EntityEncounterPolicy.scaledThreshold(entityThresholds[level])', java),
    ('import com.rabpit.backroom.core.EntityEncounterPolicy;', java),
    ('proceduralEntitiesAllowed', java),
    ('entityKey = "diep_minh";', java),
    ('ARGUS // Thousandfold Execution', skill_catalog), ('Twosome Time', skill_catalog), ('Honeycomb Fire', skill_catalog),
    ('GodKiller Override // Twenty-Four Severance', skill_catalog), ('Crimson Guillotine', skill_catalog), ('Spatial Dominion', skill_catalog),
    ('Kế Hoạch Không Có Trong Kế Hoạch', skill_catalog), ('Quăng Đại Cái Gì Đó', skill_catalog),
    ('CompanionSkillCatalog.forCharacter(c.id)', detail_json), ('COMPANION_SKILLS_R01', combat), ('24 * 10', combat),
    ('hazardThresholds[level] * 75 / 100', java), ('thresholdRoll("anNhienRead", 10000, 2000', java),
    ('class CompanionSkillCatalogTest', skill_tests),
    ('runpy.run_path(str(ROOT / "patch-companion-skills-ui-finalize.py"), run_name="__main__")', chain),
    ('private const val ENTITY_EVASION_PERCENT = 17', combat),
    ('private const val LUCIA_FULL_AUTO_ROUNDS = 30', combat),
    ('private const val LUCIA_FULL_AUTO_BONUS_DAMAGE = 30', combat),
    ('private const val LUCIA_FULL_AUTO_CHANCE_PERCENT = 20', combat),
    ('private const val LUCIA_FULL_AUTO_INTERVAL_TURNS = 2', combat),
    ('LUCIA_FULL_AUTO_BURST_V1', combat),
    ('LUCIA_FULL_AUTO_BONUS_DAMAGE + luciaBaseDamage', combat),
    ('M4A1 Full Auto Burst', skill_catalog),
    ('20% mỗi 2 combat turn hợp lệ khi Party chọn TẤN CÔNG', skill_catalog),
    ('runpy.run_path(str(ROOT / "patch-v1-1-69-balance.py"), run_name="__main__")', scp_finalize),
    ('runpy.run_path(str(ROOT / "patch-linear-sublevel-progression.py"), run_name="__main__")', scp_finalize),
    ('"patch-item-interaction-coherence.py"', runtime_chain),
    ('fun reconcileNarrative(', world_ledger),
    ('reconcileNarratedWorldItems(candidateState, reply);', java),
    ('worldItemNames().concat(ownedItemNames())', html),
    ('item.available===false', html),
    ('withoutCharacterAliases', intent),
    ('recipientAfterVerb', intent),
    ('OfficialItemEffects.apply(state, beneficiaryId', engines),
    ('out.put("partyVitals", vitals)', knowledge_context),
    ('class ItemInteractionCoherenceTest', item_interaction_tests),
    ('"Đưa cho Lucia"', item_interaction_tests),
    ('"Dùng băng gạc cho Lucia"', item_interaction_tests),
    ('"patch-passive-skill-visibility.py"', runtime_chain),
    ('s("Devil Blessing", "PASSIVE"', skill_catalog),
    ('class PassiveSkillVisibilityTest', passive_skill_tests),
    ('passiveSkillsAreExposedForEveryPlayablePartyCharacter', passive_skill_tests),
    ('POV HARD LOCK: người chơi nhập vai trực tiếp Kai Akechi.', java),
    ('private String registeredNarrativeFallback(JSONObject resolved)', java),
    ('String storyContext = campaignStoryBeatPrompt(state);', java),
    ('.put("storyContext", storyContext)', java),
    ("Không được gọi nhân vật người chơi là 'Kai', 'hắn', 'anh ta'", java),
]
for marker, source in required:
    assert marker in source, marker

entity_context_start = facade.index('put("entityEncounter", JSONObject().apply {')
entity_context_end = facade.index('LevelLootEngine.preparedPreview', entity_context_start)
entity_context = facade[entity_context_start:entity_context_end]
for hidden in ['escapeBlueprint', 'solutionId', 'requiredFacts', 'requiredActions', 'completedActions', 'evidence']:
    assert hidden not in entity_context, hidden

assert 'return explicitlyReady || levelTurns(state) >= 6;' not in java
assert 'newLevel = mentioned;' not in java
assert 'private const val ENTITY_EVASION_PERCENT = 25' not in combat
assert '"nonCombat" to "true"' in an_nhien
assert '"canUseWeapons" to "false"' in an_nhien
an_start = skill_catalog.index('private val anNhien = listOf(')
an_end = skill_catalog.index('private val kai = listOf(', an_start)
assert 'Weapon DMG' not in skill_catalog[an_start:an_end]
assert skill_catalog.count('s("Devil Blessing", "PASSIVE"') == 1
assert 's("DEVIL BLESSING"' not in skill_catalog
assert html.count("button.id='characterSkillsButton'") == 1
assert html.count('id="characterSkillsModal"') == 1
assert 'rolls.put("roamingEntityId"' not in java
assert 'makeGameplayRolls(before, action, meta)' not in java
assert 'appendEquipmentBadge(box)' not in java
assert 'Kai quan sát kết quả của hành động vừa thực hiện.' not in java
assert 'jeffEncounter' not in java
assert 'janeEncounter' not in java

final_runtime = '\n'.join((html, java, facade, engines, equipment_system))
for retired in [
    'madGodSetEquipped', 'if (isMadGodEquipRequest(action))', 'applyMadGodCheat(',
    'commandId = "$turnId:MADGOD:EQUIP"', '"madgod_equipped"',
    'Kai_MadGod_snapshot_overlay.png', 'avatars/MadGod.jpg', 'id = MADGOD_SET_ID'
]:
    assert retired not in final_runtime, retired

required_assets = [
    'hound.png','clump.png','duller.png','deathmoth.png','hostile_faceling.png','false_puddle.png','paintings.png',
    'smiler.png','skin-stealer.png','predatory_window.png','biological_pipeline.png','wretch.png','cable_mimic.png',
    'the_beast_of_level_5.png','hotel_corpse_lure.png','jeff_the_killer.png','jane_the_killer.png','slenderman.png','diep_minh.png',
    'SCP173.png','Newviolet.png','Kai-TheDevilWithin.png'
]
for name in required_assets:
    p = entity_dir / name
    assert p.is_file() and p.stat().st_size > 0, name
for p in [root / 'app/src/main/assets/Kai_new_overlay.png', root / 'app/src/main/assets/BESTKAIV2.png']:
    assert p.is_file() and p.stat().st_size > 0, str(p)
for p in [root / 'app/src/main/assets/Kai_MadGod_snapshot_overlay.png', root / 'app/src/main/assets/avatars/MadGod.jpg']:
    assert not p.exists(), str(p)

combined = html + java + gradle + facade + save_repo
for forbidden in ['drive.google.com','www.googleapis.com/auth/drive','DriveOnlineSaveManager','com.google.android.gms:play-services-auth','ENTITY_MANIFEST_FILE_ID','readEntityManifestRemote']:
    assert forbidden not in combined, forbidden

print('Runtime contracts verified.')
