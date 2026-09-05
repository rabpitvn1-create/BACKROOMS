from pathlib import Path
import json
import re
import runpy

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
TEMPLATES = ROOT / "inventory-v2/templates"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
CODEC = CORE / "GameStateCodec.kt"
STATE = CORE / "GameState.kt"
FACADE = CORE / "GameCoreFacade.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one anchor, found {count}")
    return text.replace(old, new, 1)


def replace_between(text: str, start: str, end: str, replacement: str, label: str) -> str:
    a = text.find(start)
    b = text.find(end, a + len(start)) if a >= 0 else -1
    if a < 0 or b < 0:
        raise RuntimeError(f"{label}: anchors not found")
    return text[:a] + replacement.rstrip() + "\n\n" + text[b:]


# Validate catalog/loot data before touching runtime sources.
runpy.run_path(str(ROOT / "validate-item-content.py"), run_name="__main__")

# Legacy patches need the old source anchors. Inventory V2 becomes the last runtime authority.
for name in [
    "GameCommand.kt",
    "IntentPipeline.kt",
    "CommandPipeline.kt",
    "ItemContent.kt",
    "InventoryPolicy.kt",
    "Engines.kt",
    "StateReducer.kt",
    "OmnivaultEngine.kt",
]:
    source = (TEMPLATES / name).read_text(encoding="utf-8")
    if name == "CommandPipeline.kt":
        source = source.replace(
            'context.state.party.memberIds.firstOrNull { it != KAI_ID && ownsDefinition(context.state, it, it = it, resolved = item) }',
            'context.state.party.memberIds.firstOrNull { ownerId -> ownerId != KAI_ID && ownsDefinition(context.state, ownerId, item) }'
        ).replace(
            'private fun ownsDefinition(state: GameState, ownerId: String, it: String, resolved: Pair<String, String>?): Boolean {',
            'private fun ownsDefinition(state: GameState, ownerId: String, resolved: Pair<String, String>?): Boolean {'
        )
    if name == "Engines.kt":
        source = source.replace(
            'changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots - slot)), "item_unequipped")',
            'changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots - slot))), "item_unequipped")'
        )
    (CORE / name).write_text(source, encoding="utf-8")

# Save schema V4 retires Omnivault scan/copy state. V3 remains directly decodable.
game = STATE.read_text(encoding="utf-8")
game, count = re.subn(r"const val CURRENT_SAVE_VERSION = \d+", "const val CURRENT_SAVE_VERSION = 4", game, count=1)
if count != 1:
    raise RuntimeError("save version anchor missing")
start = game.find("data class ScanSlot(")
end = game.find("data class PendingTurn(", start)
if start < 0 or end < 0:
    raise RuntimeError("legacy Omnivault state anchors missing")
game = game[:start] + '''data class OmnivaultState(
  val ownerId: String = KAI_ID,
  val storedItems: Map<String, ItemStack> = emptyMap(),
  val restoreCooldownUntilEpochMs: Map<String, Long> = emptyMap()
)

''' + game[end:]
STATE.write_text(game, encoding="utf-8")

codec = CODEC.read_text(encoding="utf-8")
codec = codec.replace("version >= CURRENT_SAVE_VERSION -> decodeCurrent(root)", "version >= 3 -> decodeCurrent(root)")
codec = replace_between(
    codec,
    "  private fun omnivault(value: OmnivaultState)",
    "  private fun turn(value: TurnState)",
    '''  private fun omnivault(value: OmnivaultState) = JSONObject().apply {
    put("ownerId", value.ownerId)
    put("storedItems", JSONObject().apply {
      value.storedItems.values.forEach { stack -> put(ItemContentRules.normalize(stack).itemId, item(stack)) }
    })
    put("restoreCooldownUntilEpochMs", JSONObject().apply {
      value.restoreCooldownUntilEpochMs.forEach { (id, time) -> put(id, time) }
    })
  }

  private fun decodeOmnivault(json: JSONObject): OmnivaultState {
    val cooldowns = mutableMapOf<String, Long>()
    json.optJSONObject("restoreCooldownUntilEpochMs")?.let { values ->
      values.keys().forEach { cooldowns[it] = values.optLong(it) }
    }
    return OmnivaultState(
      ownerId = json.optString("ownerId", KAI_ID),
      storedItems = itemMap(json.optJSONObject("storedItems")),
      restoreCooldownUntilEpochMs = cooldowns
    )
  }''',
    "Omnivault codec"
)
CODEC.write_text(codec, encoding="utf-8")

# GameCoreFacade: catalog-backed aliases, no Gemini inventory reconciliation, only two loot origins.
facade = FACADE.read_text(encoding="utf-8")
old_ctor = '''class GameCoreFacade private constructor(
  private val repository: SaveRepository,
  private val logger: GamePipelineLogger,
  private val localModel: LiteRTIntentInterpreter
) : AutoCloseable {'''
new_ctor = '''class GameCoreFacade private constructor(
  private val repository: SaveRepository,
  private val logger: GamePipelineLogger,
  private val localModel: LiteRTIntentInterpreter,
  private val itemCatalog: ItemCatalog,
  private val lootTables: LootTables
) : AutoCloseable {'''
facade = replace_once(facade, old_ctor, new_ctor, "facade constructor")

process_rule = r'''  fun processRule(legacyStateJson: String, input: String): String {
    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
    CombatRuntime.pendingJson(state)?.let { pendingCombat ->
      val locked = syncLegacy(legacy, state, incrementTurn = false)
      locked.remove("combat")
      locked.put("pendingCombat", pendingCombat)
      return response(true, locked, "combat_pending", "combat_pending", "Entity encounter đang chờ BẮT ĐẦU COMBAT.")
    }
    val turnId = nextTurnId(legacy, state)
    val created = TurnCoordinator.createPending(state, turnId, input)
    if (created.error != null) return response(false, legacy, created.error)
    val context = contextFor(created.state)
    val rule = RuleIntentInterpreter().interpretSync(input, context)
    val hardNoAction = rule.candidates.firstOrNull {
      it.intent == GameIntent.NO_ACTION && it.reason in setOf("world_item_unavailable", "omnivault_creation_removed")
    }
    if (hardNoAction != null) {
      val clean = TurnCoordinator.reject(created.state, hardNoAction.reason ?: "action_unavailable").state
      repository.save(clean)
      return response(true, syncLegacy(legacy, clean, incrementTurn = false), hardNoAction.reason,
        "inventory_v2_rejected", validationReply(hardNoAction.reason ?: "action_unavailable"))
    }

    val finalCandidates = mutableListOf<IntentCandidate>()
    for (candidate in rule.candidates) {
      if (candidate.confidence == IntentConfidence.HIGH || candidate.intent == GameIntent.NO_ACTION) {
        finalCandidates += candidate
        continue
      }
      val local = localModel.interpretSync(candidate.clause, context).candidates.singleOrNull()
      finalCandidates += if (local != null && local.confidence == IntentConfidence.HIGH) local else candidate
    }
    if (finalCandidates.any { it.intent == GameIntent.UNKNOWN || it.intent == GameIntent.NO_ACTION || it.confidence != IntentConfidence.HIGH }) {
      return response(false, legacy, "semantic_fallback_required")
    }
    val resolver = CommandResolver()
    val commands = finalCandidates.mapIndexedNotNull { index, candidate -> resolver.resolve(candidate, index, turnId, context) }.toMutableList<GameCommand>()
    if (commands.size != finalCandidates.size) return response(false, legacy, "command_resolution_incomplete")
    commands += timeAdvanceCommand(turnId, input, CommandSource.RULE)
    val committed = TurnCoordinator.commit(created.state, commands)
    if (committed.error != null) {
      val recovered = TurnCoordinator.reject(created.state, committed.error).state
      repository.save(recovered)
      return response(true, syncLegacy(legacy, recovered, incrementTurn = false), committed.error, "rule_rejected", validationReply(committed.error))
    }
    repository.save(committed.state)
    return response(true, syncLegacy(legacy, committed.state), null, committed.execution?.events?.joinToString(","), eventReply(committed.execution?.events.orEmpty()))
  }'''
end_marker = "  fun startPendingCombat(" if "  fun startPendingCombat(" in facade else "  fun currentCoreState()"
facade = replace_between(facade, "  fun processRule(", end_marker, process_rule, "processRule")

process_candidate = r'''  fun processValidatedCandidate(beforeJson: String, candidateJson: String, action: String): String {
    val before = JSONObject(beforeJson)
    val candidate = JSONObject(candidateJson)
    val core = loadOrMigrate(before)
    CombatRuntime.pendingJson(core)?.let { pendingCombat ->
      val locked = syncLegacy(before, core, incrementTurn = false)
      locked.remove("combat")
      locked.put("pendingCombat", pendingCombat)
      return response(true, locked, "combat_pending", "combat_pending", "Entity encounter đang chờ BẮT ĐẦU COMBAT.")
    }
    val turnId = nextTurnId(before, core)
    val created = TurnCoordinator.createPending(core, turnId, action)
    if (created.error != null) return response(false, before, created.error)
    val commands = mutableListOf<GameCommand>()

    val desiredParty = candidate.optJSONArray("party").objects().mapNotNull { json ->
      val name = json.optString("name").trim()
      if (name.isEmpty()) null else json.optString("id").ifBlank { name.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-') } to name
    }
    val existingParty = created.state.party.memberIds.toSet()
    desiredParty.filter { it.first !in existingParty }.forEachIndexed { index, (id, _) ->
      if (created.state.characters.containsKey(id)) commands += PartyCommand(
        "$turnId:GEMINI:PARTY:$index", turnId, KAI_ID, id, CommandSource.GEMINI,
        PartyCommand.Operation.ADD, consentConfirmed = true, targetPresent = true
      )
    }

    commands += ValidatedLegacyStateCommand(
      commandId = "$turnId:GEMINI:WORLD", turnId = turnId, source = CommandSource.GEMINI,
      location = candidate.optString("location").takeIf { it.isNotBlank() },
      title = candidate.optString("title").takeIf { it.isNotBlank() },
      levelJson = candidate.optJSONObject("level")?.toString(),
      playerJson = candidate.optJSONObject("player")?.toString(),
      flagsJson = candidate.optJSONObject("flags")?.toString(),
      validatedByGameEngine = true
    )

    LootEngine.exploreGrant(candidate, turnId, itemCatalog, lootTables)?.let { grant ->
      commands += LootEngine.commandFor(grant, turnId, itemCatalog)
    }
    commands += timeAdvanceCommand(turnId, action, CommandSource.GEMINI)

    val committed = TurnCoordinator.commit(created.state, commands)
    if (committed.error != null) {
      val recovered = TurnCoordinator.reject(created.state, committed.error).state
      repository.save(recovered)
      return response(false, before, committed.error)
    }
    val combatRuntime = CombatRuntime.resolveEncounter(committed.state, candidate, turnId)
    repository.save(combatRuntime.state)
    val synchronized = syncLegacy(candidate, combatRuntime.state, incrementTurn = false)
    synchronized.remove("combat")
    synchronized.remove("pendingCombat")
    CombatRuntime.pendingJson(combatRuntime.state)?.let { synchronized.put("pendingCombat", it) }
    return response(true, synchronized, null, "validated_candidate_committed")
  }'''
facade = replace_between(facade, "  fun processValidatedCandidate(", "  private fun loadOrMigrate(", process_candidate, "processValidatedCandidate")

if "  fun startPendingCombat(" in facade:
    start_combat = r'''  fun startPendingCombat(legacyStateJson: String): String {
    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
    val runtime = CombatRuntime.startPendingEncounter(state)
    val resolution = runtime.resolution
      ?: return response(false, legacy, "no_pending_combat", "combat_start_rejected")
    val grants = LootEngine.entityGrants(resolution.defeatedEntities, resolution.encounterId, itemCatalog, lootTables)
    val lootCommands = grants.map { LootEngine.commandFor(it, null, itemCatalog) }
    val lootResult = StateReducer.executeAll(runtime.state, lootCommands)
    val finalState = if (lootCommands.isEmpty() || lootResult.applied || lootResult.duplicate) lootResult.state else runtime.state
    repository.save(finalState)
    val synchronized = syncLegacy(legacy, finalState, incrementTurn = false)
    synchronized.remove("pendingCombat")
    synchronized.put("combat", CombatJson.encode(resolution))
    val gained = JSONArray()
    grants.forEach { grant ->
      val marker = finalState.metadata["loot.processed.${grant.sourceId}"].orEmpty()
      if (!marker.startsWith("lost:")) {
        itemCatalog.definition(grant.definitionId)?.let { definition ->
          gained.put(JSONObject().put("id", definition.id).put("name", definition.name).put("quantity", grant.quantity))
        }
      }
    }
    if (gained.length() > 0) synchronized.put("entityLoot", gained)
    return response(true, synchronized, null, "combat_started")
  }'''
    facade = replace_between(facade, "  fun startPendingCombat(", "  fun freshGameState(", start_combat, "startPendingCombat")

context_method = r'''  private fun contextFor(state: GameState): GameContext {
    val actors = state.characters.values.associate { it.name.lowercase() to it.id } + mapOf(
      "kai" to KAI_ID, "iris" to "iris", "syvial" to "syvial", "lucia" to "lucia",
      "an nhiên" to AN_NHIEN_ID, "an nhien" to AN_NHIEN_ID
    )
    val catalogAliases = itemCatalog.aliases()
    val equipmentAliases = state.equipment.values.flatMap { it.slots.values }.mapNotNull { id ->
      KaiStartingEquipment.displayName(id)?.let { it.lowercase() to id }
    }.toMap()
    val ownedAliases = (state.inventories.values.flatMap { it.items.values } + state.omnivault.storedItems.values)
      .associate { it.name.lowercase() to it.itemId }
    return GameContext(state, actors, catalogAliases + equipmentAliases + ownedAliases, state.metadata["lastReferencedItemId"])
  }'''
facade = replace_between(facade, "  private fun contextFor(", "  private fun response(", context_method, "contextFor")

old_companion = '''  companion object {
    @JvmStatic fun create(context: Context, debugLogging: Boolean = false): GameCoreFacade = GameCoreFacade(
      SharedPreferencesSaveRepository(context.applicationContext), AndroidGamePipelineLogger(debugLogging), LiteRTIntentInterpreter(context.applicationContext)
    )
  }'''
new_companion = '''  companion object {
    @JvmStatic fun create(context: Context, debugLogging: Boolean = false): GameCoreFacade {
      val app = context.applicationContext
      val catalog = app.assets.open("items/item_catalog.json").bufferedReader().use { ItemCatalog.fromJson(it.readText()) }
      val loot = app.assets.open("items/loot_tables.json").bufferedReader().use { LootTables.fromJson(it.readText()) }
      return GameCoreFacade(
        SharedPreferencesSaveRepository(app), AndroidGamePipelineLogger(debugLogging), LiteRTIntentInterpreter(app), catalog, loot
      )
    }
  }'''
facade = replace_once(facade, old_companion, new_companion, "facade factory")

# Remove dead legacy acquisition helpers when still present.
for helper_start, helper_end in [
    ("  private fun isDirectPlayerPickupAction(", "  private fun stableItemId("),
    ("  private fun stableItemId(", "  private fun response("),
]:
    if helper_start in facade and helper_end in facade:
        facade = replace_between(facade, helper_start, helper_end, "", helper_start)

facade = facade.replace('"inventory_pickup" -> "Vật phẩm đã được thêm vào Inventory."\n', '')
facade = facade.replace('"inventory_remove" -> "Vật phẩm đã được bỏ khỏi Inventory."\n', '"item_discarded" -> "Vật phẩm đã bị vứt bỏ."\n')
facade = facade.replace('"omnivault_scanned" -> "Omnivault đã ghi mẫu vào scan slot."\n', '')
facade = facade.replace('"omnivault_copied" -> "Omnivault đã tạo bản sao từ mẫu đã quét."\n', '')
FACADE.write_text(facade, encoding="utf-8")

# Android gameplay keeps one explore-loot roll. Item identity now comes only from LootTables.
main = MAIN.read_text(encoding="utf-8")
main = "\n".join(line for line in main.splitlines() if not any(token in line for token in [
    "int[] waterThresholds =", "boolean water = containsAny", 'rolls.put("almondWater"'
])) + "\n"
main = main.replace("inventory_upsert{item,basis}; inventory_remove{name,basis}; ", "")
main = main.replace("inventory_upsert{item,basis}; inventory_remove{name,basis};", "")
policy = "INVENTORY V2 HARD LOCK: vật phẩm mới chỉ được Core cấp từ Explore Loot hoặc Entity Drop; mô tả vật phẩm trong world không tạo quyền sở hữu; AI/UI/LiteRT không được tạo, copy hay reconcile Inventory; Omnivault không Scan/Copy/tạo vật phẩm. "
anchor = '"GAMEPLAY_ROLLS do Android sinh là bất biến:'
if policy not in main and anchor in main:
    pos = main.find(anchor)
    line_start = main.rfind("\n", 0, pos) + 1
    main = main[:line_start] + '            "' + policy + '" +\n' + main[line_start:]
MAIN.write_text(main, encoding="utf-8")

html = INDEX.read_text(encoding="utf-8")
html = html.replace('id="characterInventoryCapacity">0 / 9 loại vật phẩm', 'id="characterInventoryCapacity">0 / 14 ô vật phẩm')
html = html.replace("capacity.textContent=inv.length+' / 9 loại vật phẩm';", "capacity.textContent=inv.length+' / '+(member&&member.id==='kai'?14:8)+' ô vật phẩm';")
html = html.replace("0 / 9 loại vật phẩm", "0 / 14 ô vật phẩm")
INDEX.write_text(html, encoding="utf-8")

# Remove compile-time tests that encode the retired pickup/copy command surface; replace with V2 contracts.
for path in TESTS.glob("*.kt"):
    text = path.read_text(encoding="utf-8")
    if any(token in text for token in [
        "Operation.PICKUP", "Operation.DROP", "OMNIVAULT_SCAN", "OMNIVAULT_COPY", "Operation.SCAN", "Operation.COPY", "scanSlots", "markedSourceIds"
    ]):
        path.unlink()

(TESTS / "InventoryV2GeneratedTest.kt").write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class InventoryV2GeneratedTest {
  private fun characterState(): GameState {
    val base = GameState.initial()
    val iris = CharacterState("iris", "Iris")
    return base.copy(
      characters = base.characters + ("iris" to iris),
      party = base.party.copy(memberIds = listOf(KAI_ID, "iris")),
      inventories = base.inventories + ("iris" to InventoryState("iris")),
      equipment = base.equipment + ("iris" to EquipmentState("iris"))
    )
  }

  private fun lootItem(id: String, name: String, quantity: Int = 1, category: String = "MATERIAL"): ItemStack = ItemStack(
    itemId = id,
    name = name,
    quantity = quantity,
    metadata = mapOf(
      "catalog.definitionId" to id,
      "catalog.category" to category,
      "catalog.stackMode" to "STACK",
      "catalog.maxStack" to "99",
      "catalog.transferable" to "true",
      "catalog.discardable" to "true",
      "catalog.effects" to "",
      "loot.origin" to LootOrigin.EXPLORE_LOOT.name,
      "loot.sourceId" to "explore:test",
      "loot.turnId" to "TURN_1"
    )
  )

  private fun grant(state: GameState, item: ItemStack): GameState {
    val result = StateReducer.execute(state, LootGrantCommand(
      commandId = "loot-${item.itemId}", turnId = "TURN_1", actorId = KAI_ID,
      origin = LootOrigin.EXPLORE_LOOT, sourceId = "explore:test", item = item, quantity = item.quantity
    ))
    assertTrue(result.applied)
    return result.state
  }

  @Test fun onlyAuthoritativeLootCommandCanIncreaseQuantity() {
    val item = lootItem("bandage", "Bandage", 2)
    val rejected = StateReducer.execute(characterState(), LootGrantCommand(
      commandId = "bad-loot", turnId = "TURN_1", actorId = KAI_ID, source = CommandSource.UI,
      origin = LootOrigin.EXPLORE_LOOT, sourceId = "explore:test", item = item, quantity = 2
    ))
    assertFalse(rejected.applied)
    assertEquals("loot_source_not_authoritative", rejected.validation.reason)
    val granted = grant(characterState(), item)
    assertEquals(2, granted.inventories.getValue(KAI_ID).items.getValue("bandage").quantity)
  }

  @Test fun transferMovesRealQuantityWithoutCopying() {
    val granted = grant(characterState(), lootItem("bandage", "Bandage", 3))
    val result = StateReducer.execute(granted, ItemCommand(
      "transfer", "TURN_1", KAI_ID, "iris", CommandSource.RULE,
      ItemCommand.Operation.TRANSFER, "bandage", "Bandage", 1
    ))
    assertTrue(result.applied)
    assertEquals(2, result.state.inventories.getValue(KAI_ID).items.getValue("bandage").quantity)
    assertEquals(1, result.state.inventories.getValue("iris").items.getValue("bandage").quantity)
  }

  @Test fun giveAndUseTransfersThenConsumesAtRecipientAtomically() {
    val granted = grant(characterState(), lootItem("ration", "Ration", 1, "CONSUMABLE"))
    val result = StateReducer.execute(granted, GiveAndUseItemCommand(
      "give-use", "TURN_1", KAI_ID, "iris", CommandSource.RULE, "ration", "Ration", 1
    ))
    assertTrue(result.applied)
    assertFalse(result.state.inventories.getValue(KAI_ID).items.containsKey("ration"))
    assertFalse(result.state.inventories.getValue("iris").items.containsKey("ration"))
  }

  @Test fun requestDirectionIsJustARealReverseTransfer() {
    var state = characterState()
    val irisItem = lootItem("battery", "Battery", 2).copy(metadata = lootItem("battery", "Battery", 2).metadata + ("loot.sourceId" to "entity:test"))
    state = state.copy(inventories = state.inventories + ("iris" to InventoryState("iris", mapOf("battery" to irisItem))))
    val result = StateReducer.execute(state, ItemCommand(
      "request-transfer", "TURN_1", "iris", KAI_ID, CommandSource.RULE,
      ItemCommand.Operation.TRANSFER, "battery", "Battery", 1
    ))
    assertTrue(result.applied)
    assertEquals(1, result.state.inventories.getValue("iris").items.getValue("battery").quantity)
    assertEquals(1, result.state.inventories.getValue(KAI_ID).items.getValue("battery").quantity)
  }

  @Test fun discardIsDestructiveAndNeverCreatesWorldItem() {
    val granted = grant(characterState(), lootItem("scrap", "Scrap", 2))
    val result = StateReducer.execute(granted, ItemCommand(
      "discard", "TURN_1", KAI_ID, source = CommandSource.RULE,
      operation = ItemCommand.Operation.DISCARD, itemId = "scrap", itemName = "Scrap", quantity = 1
    ))
    assertTrue(result.applied)
    assertEquals(1, result.state.inventories.getValue(KAI_ID).items.getValue("scrap").quantity)
    assertFalse(result.state.world.keys.any { it.contains("item", true) || it.contains("loot", true) })
  }

  @Test fun finalCapacityContractIsKai14x9999AndOthers8x99() {
    val state = characterState()
    assertEquals(InventoryProfile(14, 9999), InventoryPolicy.profileFor(state, KAI_ID))
    assertEquals(InventoryProfile(8, 99), InventoryPolicy.profileFor(state, "iris"))
  }
}
''', encoding="utf-8")

# Runtime hard-lock verification. Historical patch scripts/docs may mention retired behavior; shipped runtime may not.
forbidden_runtime = [
    "PICKUP_ITEM", "Operation.PICKUP", "Operation.DROP", "OMNIVAULT_SCAN", "OMNIVAULT_COPY",
    "Operation.SCAN", "Operation.COPY", "scanSlots", "markedSourceIds", "omnivault_copied", "omnivault_scanned"
]
for path in [
    CORE / "GameCommand.kt", CORE / "IntentPipeline.kt", CORE / "CommandPipeline.kt",
    CORE / "StateReducer.kt", CORE / "Engines.kt", CORE / "OmnivaultEngine.kt", CORE / "GameState.kt"
]:
    text = path.read_text(encoding="utf-8")
    for token in forbidden_runtime:
        if token in text:
            raise RuntimeError(f"Inventory V2 retired runtime token in {path.name}: {token}")

for required in [
    CORE / "ItemCatalog.kt", CORE / "LootEngine.kt",
    ROOT / "app/src/main/assets/items/item_catalog.json", ROOT / "app/src/main/assets/items/loot_tables.json"
]:
    if not required.is_file():
        raise RuntimeError(f"Inventory V2 required file missing: {required}")

print("Inventory V2 final authority applied: catalog-driven items, Explore/Entity-only acquisition, real transfer/use/request/discard, and creation-free Omnivault.")
