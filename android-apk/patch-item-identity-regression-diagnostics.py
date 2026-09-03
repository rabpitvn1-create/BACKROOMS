from pathlib import Path

ROOT = Path(__file__).resolve().parent
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/ItemIdentityAuthorityTest.kt"

text = TEST.read_text(encoding="utf-8")
old_loop = '''  @Test fun everyOfficialItemSurvivesWorldPickupSaveReloadAndTransfer() {
    localized.forEach { (localizedName, expectedId) ->
      val flags = requireNotNull(WorldItemLedger.record(
        null, "identity-room",
        JSONObject().put("name", localizedName).put("quantity", 1).toString()
      ))
      val recorded = JSONObject(flags).getJSONArray("worldItems").getJSONObject(0)
      assertEquals(localizedName, expectedId, recorded.getString("id"))

      val pickup = requireNotNull(WorldItemLedger.consume(flags, "identity-room", "Nhặt $localizedName"))
      val worldItem = pickup.items.single()
      assertEquals(localizedName, expectedId, worldItem.itemId)

      val initial = withTarget().copy(world = mapOf("location" to "identity-room", "flagsJson" to pickup.flagsJson))
      val acquired = StateReducer.execute(initial, ItemCommand(
        commandId = "pickup-$expectedId", turnId = initial.turn.currentTurnId,
        actorId = KAI_ID, source = CommandSource.SYSTEM, operation = ItemCommand.Operation.PICKUP,
        itemId = worldItem.itemId, itemName = worldItem.itemName, quantity = 1, metadata = worldItem.metadata
      ))
      assertTrue("pickup $localizedName: ${acquired.validation.reason}", acquired.applied)
      assertTrue(acquired.state.inventories.getValue(KAI_ID).items.containsKey(expectedId))
      assertEquals(expectedId, acquired.state.metadata["lastReferencedItemId"])

      val reloaded = GameStateCodec.decode(GameStateCodec.encode(acquired.state))
      assertTrue("reload $localizedName", reloaded.inventories.getValue(KAI_ID).items.containsKey(expectedId))

      val command = CommandResolver().resolve(
        IntentCandidate("Đưa $localizedName cho Target", GameIntent.TRANSFER_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
        0, reloaded.turn.currentTurnId,
        GameContext(reloaded, mapOf("kai" to KAI_ID, "target" to "target"))
      ) as ItemCommand
      assertEquals(localizedName, expectedId, command.itemId)
      assertEquals("target", command.targetId)
      val transferred = StateReducer.execute(reloaded, command)
      assertTrue("transfer $localizedName: ${transferred.validation.reason}", transferred.applied)
      assertFalse(transferred.state.inventories.getValue(KAI_ID).items.containsKey(expectedId))
      assertEquals(1, transferred.state.inventories.getValue("target").items.getValue(expectedId).quantity)
    }
  }
'''
new_cases = '''  private fun roundTripAndTransfer(localizedName: String, expectedId: String) {
    val flags = requireNotNull(WorldItemLedger.record(
      null, "identity-room",
      JSONObject().put("name", localizedName).put("quantity", 1).toString()
    ))
    val recorded = JSONObject(flags).getJSONArray("worldItems").getJSONObject(0)
    assertEquals(localizedName, expectedId, recorded.getString("id"))

    val pickup = requireNotNull(WorldItemLedger.consume(flags, "identity-room", "Nhặt $localizedName"))
    val worldItem = pickup.items.single()
    assertEquals(localizedName, expectedId, worldItem.itemId)

    val initial = withTarget().copy(world = mapOf("location" to "identity-room", "flagsJson" to pickup.flagsJson))
    val acquired = StateReducer.execute(initial, ItemCommand(
      commandId = "pickup-$expectedId", turnId = initial.turn.currentTurnId,
      actorId = KAI_ID, source = CommandSource.SYSTEM, operation = ItemCommand.Operation.PICKUP,
      itemId = worldItem.itemId, itemName = worldItem.itemName, quantity = 1, metadata = worldItem.metadata
    ))
    assertTrue("pickup $localizedName: ${acquired.validation.reason}", acquired.applied)
    assertTrue(acquired.state.inventories.getValue(KAI_ID).items.containsKey(expectedId))
    assertEquals(expectedId, acquired.state.metadata["lastReferencedItemId"])

    val reloaded = GameStateCodec.decode(GameStateCodec.encode(acquired.state))
    assertTrue("reload $localizedName", reloaded.inventories.getValue(KAI_ID).items.containsKey(expectedId))

    val command = CommandResolver().resolve(
      IntentCandidate("Đưa $localizedName cho Target", GameIntent.TRANSFER_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
      0, reloaded.turn.currentTurnId,
      GameContext(reloaded, mapOf("kai" to KAI_ID, "target" to "target"))
    ) as ItemCommand
    assertEquals(localizedName, KAI_ID, command.actorId)
    assertEquals(localizedName, expectedId, command.itemId)
    assertEquals(localizedName, "target", command.targetId)
    val transferred = StateReducer.execute(reloaded, command)
    assertTrue("transfer $localizedName: ${transferred.validation.reason}", transferred.applied)
    assertFalse(transferred.state.inventories.getValue(KAI_ID).items.containsKey(expectedId))
    assertEquals(1, transferred.state.inventories.getValue("target").items.getValue(expectedId).quantity)
  }

  @Test fun transferFlashlight() = roundTripAndTransfer("Đèn pin", ItemCatalog.FLASHLIGHT)
  @Test fun transferLighter() = roundTripAndTransfer("Bật lửa", ItemCatalog.LIGHTER)
  @Test fun transferAlmondWater() = roundTripAndTransfer("Nước Hạnh Nhân", ItemCatalog.ALMOND_WATER)
  @Test fun transferCannedFood() = roundTripAndTransfer("Thực phẩm đóng hộp", ItemCatalog.CANNED_FOOD)
  @Test fun transferBattery() = roundTripAndTransfer("Pin", ItemCatalog.BATTERY)
  @Test fun transferLighterFuel() = roundTripAndTransfer("Nhiên liệu bật lửa", ItemCatalog.LIGHTER_FUEL)
  @Test fun transferBandage() = roundTripAndTransfer("Băng gạc", ItemCatalog.BANDAGE)
  @Test fun transferAntiseptic() = roundTripAndTransfer("Thuốc sát trùng", ItemCatalog.ANTISEPTIC)
  @Test fun transferPainkiller() = roundTripAndTransfer("Thuốc giảm đau", ItemCatalog.PAINKILLER)
  @Test fun transferSardines() = roundTripAndTransfer("Cá Mòi Ba Cô Gái", ItemCatalog.SARDINES)
  @Test fun transferLaVie() = roundTripAndTransfer("Nước suối La Vie", ItemCatalog.LA_VIE)
'''
if old_loop not in text:
    raise RuntimeError("Identity round-trip loop anchor missing")
text = text.replace(old_loop, new_cases, 1)
old_legacy = '''    val decoded = GameStateCodec.decode(root)
    assertEquals(setOf(ItemCatalog.ALMOND_WATER), decoded.inventories.getValue(KAI_ID).items.keys)
'''
new_legacy = '''    val decoded = GameStateCodec.decode(root)
    val items = decoded.inventories.getValue(KAI_ID).items
    assertTrue(items.containsKey(ItemCatalog.ALMOND_WATER))
    assertFalse(items.containsKey("nước-hạnh-nhân"))
'''
if old_legacy not in text:
    raise RuntimeError("Legacy re-key assertion anchor missing")
text = text.replace(old_legacy, new_legacy, 1)

chicken_old = '''    "Nước suối La Vie" to ItemCatalog.LA_VIE
  )'''
chicken_new = '''    "Nước suối La Vie" to ItemCatalog.LA_VIE,
    "Hộp cơm gà" to ItemCatalog.CHICKEN_RICE_BOX
  )'''
if chicken_new not in text:
    count = text.count(chicken_old)
    if count != 1:
        raise RuntimeError(f"Chicken rice identity regression anchor count={count}")
    text = text.replace(chicken_old, chicken_new, 1)

TEST.write_text(text, encoding="utf-8")
print("Item identity regressions split by official item; legacy assertion checks canonical re-key without assuming an otherwise empty final inventory; chicken rice coverage follows the final 12-item catalog.")
