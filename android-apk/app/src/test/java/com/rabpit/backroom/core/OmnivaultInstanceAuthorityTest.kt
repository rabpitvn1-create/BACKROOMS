package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OmnivaultInstanceAuthorityTest {
  private fun fresh(): GameState = CharacterEquipmentSystem.seedFresh(GameState.initial())

  private fun pickup(state: GameState, id: String, name: String, quantity: Int = 1, seed: String = id): GameState {
    val result = InventoryEngine.execute(state, ItemCommand(
      commandId = "pickup:$seed",
      turnId = state.turn.currentTurnId,
      actorId = KAI_ID,
      source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP,
      itemId = id,
      itemName = name,
      quantity = quantity
    ))
    assertTrue(result.validation.reason ?: "pickup failed", result.applied)
    return result.state
  }

  private fun scan(state: GameState, id: String, name: String, commandId: String): ExecutionResult =
    OmnivaultEngine.execute(state, OmnivaultCommand(
      commandId = commandId,
      turnId = state.turn.currentTurnId,
      actorId = KAI_ID,
      source = CommandSource.RULE,
      operation = OmnivaultCommand.Operation.SCAN,
      itemId = id,
      itemName = name,
      timestampEpochMs = commandId.hashCode().toLong()
    ))

  @Test fun twoPhysicalOriginalsOfSameItemCanBeScannedIndependently() {
    var state = pickup(fresh(), "scrap", "Mảnh kim loại", 2, "two-scrap")
    val ids = ItemIdentity.instanceIds(state.inventories.getValue(KAI_ID).items.getValue("scrap"))
    assertEquals(2, ids.size)
    val first = scan(state, "scrap", "Mảnh kim loại", "scan:first")
    assertTrue(first.applied)
    val second = scan(first.state, "scrap", "Mảnh kim loại", "scan:second")
    assertTrue(second.applied)
    assertEquals(2, second.state.omnivault.scanSlots.size)
    val sourceIds = second.state.omnivault.scanSlots.map { it.templateItem.metadata.getValue("omnivaultSourceInstanceId") }.toSet()
    assertEquals(ids.toSet(), sourceIds)
  }

  @Test fun samePhysicalOriginalCannotBeScannedTwiceEvenAfterTemplateOverwrite() {
    var state = pickup(fresh(), "a", "A")
    state = scan(state, "a", "A", "scan:a").state
    for (id in listOf("b", "c", "d")) {
      state = pickup(state, id, id.uppercase())
      state = scan(state, id, id.uppercase(), "scan:$id").state
    }
    assertEquals(3, state.omnivault.scanSlots.size)
    assertFalse(state.omnivault.scanSlots.any { it.sourceItemId == "a" })
    val retry = scan(state, "a", "A", "scan:a:retry")
    assertFalse(retry.applied)
    assertEquals("source_already_marked", retry.validation.reason)
  }

  @Test fun copiedObjectIsASeparateStackAndCanNeverBeScanned() {
    var state = pickup(fresh(), "scrap", "Mảnh kim loại")
    state = scan(state, "scrap", "Mảnh kim loại", "scan:scrap").state
    val copied = OmnivaultEngine.execute(state, OmnivaultCommand(
      commandId = "copy:scrap", turnId = state.turn.currentTurnId, actorId = KAI_ID, source = CommandSource.RULE,
      operation = OmnivaultCommand.Operation.COPY, itemId = "scrap", itemName = "Mảnh kim loại", quantity = 3
    ))
    assertTrue(copied.applied)
    val original = copied.state.inventories.getValue(KAI_ID).items.getValue("scrap")
    val copy = copied.state.inventories.getValue(KAI_ID).items.values.single { ItemIdentity.isOmnivaultCopy(it) }
    assertEquals(1, original.quantity)
    assertEquals(3, copy.quantity)
    assertNotEquals(original.itemId, copy.itemId)
    val rejected = scan(copied.state, copy.itemId, copy.name, "scan:copy")
    assertFalse(rejected.applied)
    assertEquals("copy_cannot_be_scanned", rejected.validation.reason)
  }

  @Test fun copyTargetTotalCountsOriginalAndExistingCopiesWithoutOvershoot() {
    var state = pickup(fresh(), "almond-water", "Almond Water")
    val scanned = scan(state, "almond-water", "Almond Water", "scan:almond")
    assertTrue(scanned.applied)
    state = scanned.state
    val slot = state.omnivault.scanSlots.single()
    val first = OmnivaultEngine.execute(state, OmnivaultCommand(
      commandId = "copy:to10", turnId = state.turn.currentTurnId, actorId = KAI_ID, source = CommandSource.RULE,
      operation = OmnivaultCommand.Operation.COPY, itemId = "almond-water", itemName = "Almond Water", quantity = 10,
      templateId = ItemIdentity.templateId(slot), targetTotal = 10
    ))
    assertTrue(first.applied)
    assertEquals(10, first.state.inventories.getValue(KAI_ID).items.values.filter { it.name == "Almond Water" }.sumOf { it.quantity })
    val second = OmnivaultEngine.execute(first.state, OmnivaultCommand(
      commandId = "copy:to10:again", turnId = first.state.turn.currentTurnId, actorId = KAI_ID, source = CommandSource.RULE,
      operation = OmnivaultCommand.Operation.COPY, itemId = "almond-water", itemName = "Almond Water", quantity = 10,
      templateId = ItemIdentity.templateId(slot), targetTotal = 10
    ))
    assertTrue(second.applied)
    assertTrue(second.events.contains("omnivault_copy_target_met"))
    assertEquals(10, second.state.inventories.getValue(KAI_ID).items.values.filter { it.name == "Almond Water" }.sumOf { it.quantity })
  }

  @Test fun copyQuantityIsNotBoundByNormal999PerTypeLimit() {
    var state = pickup(fresh(), "scrap", "Mảnh kim loại")
    state = scan(state, "scrap", "Mảnh kim loại", "scan:large-copy").state
    val copied = OmnivaultEngine.execute(state, OmnivaultCommand(
      commandId = "copy:1500", turnId = state.turn.currentTurnId, actorId = KAI_ID, source = CommandSource.RULE,
      operation = OmnivaultCommand.Operation.COPY, itemId = "scrap", itemName = "Mảnh kim loại", quantity = 1500
    ))
    assertTrue(copied.validation.reason ?: "copy failed", copied.applied)
    assertEquals(1500, copied.state.inventories.getValue(KAI_ID).items.values.single { ItemIdentity.isOmnivaultCopy(it) }.quantity)
  }

  @Test fun worldObjectCanBeScannedWithoutBeingPickedUp() {
    val worldItem = JSONObject()
      .put("id", "medical:bandage")
      .put("name", "Băng gạc")
      .put("quantity", 1)
      .put("instanceId", "world:bandage:alpha")
      .put("available", true)
    val flags = JSONObject().put("worldItems", JSONArray().put(worldItem))
    val state = fresh().copy(world = fresh().world + ("flagsJson" to flags.toString()))
    val result = scan(state, "medical:bandage", "Băng gạc", "scan:world-bandage")
    assertTrue(result.validation.reason ?: "world scan failed", result.applied)
    assertFalse(result.state.inventories.getValue(KAI_ID).items.containsKey(BANDAGE_ID))
    assertTrue("world:bandage:alpha" in result.state.omnivault.markedSourceIds)
    assertEquals("world:bandage:alpha", result.state.omnivault.scanSlots.single().templateItem.metadata["omnivaultSourceInstanceId"])
  }

  @Test fun authoritativeWorldLivingAndLargeFlagsOverrideCommandDefaults() {
    fun stateWith(flag: String): GameState {
      val item = JSONObject().put("id", "target").put("name", "Target").put("instanceId", "world:target").put("available", true).put(flag, true)
      val flags = JSONObject().put("worldItems", JSONArray().put(item))
      val base = fresh()
      return base.copy(world = base.world + ("flagsJson" to flags.toString()))
    }
    val living = scan(stateWith("isLiving"), "target", "Target", "scan:living")
    assertFalse(living.applied)
    assertEquals("living_target_forbidden", living.validation.reason)
    val large = scan(stateWith("isLargeAssembly"), "target", "Target", "scan:large")
    assertFalse(large.applied)
    assertEquals("large_assembly_forbidden", large.validation.reason)
  }

  @Test fun saveRoundTripPreservesTemplateAndPhysicalMarkIdentity() {
    var state = pickup(fresh(), "scrap", "Mảnh kim loại", 2, "save")
    state = scan(state, "scrap", "Mảnh kim loại", "scan:save").state
    val decoded = GameStateCodec.decode(GameStateCodec.encode(state))
    assertEquals(state.omnivault.markedSourceIds, decoded.omnivault.markedSourceIds)
    assertEquals(ItemIdentity.templateId(state.omnivault.scanSlots.single()), ItemIdentity.templateId(decoded.omnivault.scanSlots.single()))
    assertEquals(
      state.omnivault.scanSlots.single().templateItem.metadata["omnivaultSourceInstanceId"],
      decoded.omnivault.scanSlots.single().templateItem.metadata["omnivaultSourceInstanceId"]
    )
  }

  @Test fun resolverUnderstandsNpcToKaiAndKaiToNpcTransfers() {
    val iris = CharacterState("iris", "Iris")
    val state = fresh().copy(
      characters = fresh().characters + ("iris" to iris),
      inventories = fresh().inventories + ("iris" to InventoryState("iris"))
    )
    val context = GameContext(state, actorAliases = linkedMapOf("cao minh" to KAI_ID, "cao_minh" to KAI_ID, "iris" to "iris"))
    val resolver = CommandResolver()
    val npcCandidate = IntentCandidate("Iris đưa Cao Minh Băng gạc", GameIntent.TRANSFER_ITEM, IntentConfidence.HIGH, 1f, CommandSource.RULE)
    val npc = resolver.resolve(npcCandidate, 0, state.turn.currentTurnId, context) as ItemCommand
    assertEquals("iris", npc.actorId)
    assertEquals(KAI_ID, npc.targetId)
    val kaiCandidate = IntentCandidate("Cao Minh đưa Băng gạc cho Iris", GameIntent.TRANSFER_ITEM, IntentConfidence.HIGH, 1f, CommandSource.RULE)
    val kai = resolver.resolve(kaiCandidate, 1, state.turn.currentTurnId, context) as ItemCommand
    assertEquals(KAI_ID, kai.actorId)
    assertEquals("iris", kai.targetId)
  }
  @Test fun madGodItemsRemainForbiddenToOmnivault() {
    val base = fresh()
    val inventory = base.inventories[KAI_ID] ?: InventoryState(KAI_ID)
    val madGodSet = MadGodCanon.setItem()
    val state = base.copy(inventories = base.inventories + (KAI_ID to inventory.copy(items = inventory.items + (madGodSet.itemId to madGodSet))))

    val scanned = scan(state, madGodSet.itemId, madGodSet.name, "scan:madgod")
    assertFalse(scanned.applied)
    assertEquals("madgod_omnivault_copy_forbidden", scanned.validation.reason)

    val template = madGodSet.copy(metadata = madGodSet.metadata + mapOf(
      "omnivaultTemplateId" to "template:madgod",
      "omnivaultSourceInstanceId" to "instance:madgod:1",
      "omnivaultTemplate" to "true"
    ))
    val templated = state.copy(omnivault = state.omnivault.copy(scanSlots = listOf(ScanSlot(1, madGodSet.itemId, template, 1L))))
    val copied = OmnivaultEngine.execute(templated, OmnivaultCommand(
      commandId = "copy:madgod", turnId = templated.turn.currentTurnId, actorId = KAI_ID, source = CommandSource.RULE,
      operation = OmnivaultCommand.Operation.COPY, itemId = madGodSet.itemId, itemName = madGodSet.name, quantity = 1
    ))
    assertFalse(copied.applied)
    assertEquals("madgod_omnivault_copy_forbidden", copied.validation.reason)
  }

}
