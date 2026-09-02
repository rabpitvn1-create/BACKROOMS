package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ItemIdentityAuthorityTest {
  private val localized = linkedMapOf(
    "Đèn pin" to ItemCatalog.FLASHLIGHT,
    "Bật lửa" to ItemCatalog.LIGHTER,
    "Nước Hạnh Nhân" to ItemCatalog.ALMOND_WATER,
    "Thực phẩm đóng hộp" to ItemCatalog.CANNED_FOOD,
    "Pin" to ItemCatalog.BATTERY,
    "Nhiên liệu bật lửa" to ItemCatalog.LIGHTER_FUEL,
    "Băng gạc" to ItemCatalog.BANDAGE,
    "Thuốc sát trùng" to ItemCatalog.ANTISEPTIC,
    "Thuốc giảm đau" to ItemCatalog.PAINKILLER,
    "Cá Mòi Ba Cô Gái" to ItemCatalog.SARDINES,
    "Nước suối La Vie" to ItemCatalog.LA_VIE,
    "Hộp cơm gà" to ItemCatalog.CHICKEN_RICE_BOX
  )

  private fun withTarget(): GameState {
    val base = GameState.initial()
    val target = CharacterState("target", "Target")
    return base.copy(
      characters = base.characters + (target.id to target),
      inventories = base.inventories + (target.id to InventoryState(target.id))
    )
  }

  @Test fun everyLocalizedOfficialNameHasOneCanonicalId() {
    assertEquals(ItemCatalog.items.size, localized.size)
    localized.forEach { (name, expected) ->
      assertEquals(name, expected, ItemCatalog.identityId(name = name))
      assertEquals(expected, ItemCatalog.resolveOfficial(null, name)?.id)
    }
  }

  private fun roundTripAndTransfer(localizedName: String, expectedId: String) {
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
    assertEquals(localizedName, 1, command.quantity)
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

  @Test fun gmGainWithLocalizedOfficialNameUsesCanonicalId() {
    val candidate = JSONArray().put(JSONObject().put("name", "Nước Hạnh Nhân").put("quantity", 1))
    val gains = GmItemGainPolicy.positiveDeltas(emptyMap(), candidate)
    assertEquals(1, gains.size)
    assertEquals(ItemCatalog.ALMOND_WATER, gains.single().itemId)
  }

  @Test fun localizedLegacyIdIsRekeyedDuringDecode() {
    val root = JSONObject(GameStateCodec.encode(withTarget()))
    val kai = root.getJSONObject("inventories").getJSONObject(KAI_ID)
    kai.put("items", JSONObject().put("nước-hạnh-nhân", JSONObject()
      .put("itemId", "nước-hạnh-nhân")
      .put("name", "Nước Hạnh Nhân")
      .put("quantity", 1)
      .put("metadata", JSONObject())
      .put("archetypeId", "nước-hạnh-nhân")
      .put("contentState", "NONE")))
    root.getJSONObject("metadata").put("lastReferencedItemId", "nước-hạnh-nhân")
    val decoded = GameStateCodec.decode(root)
    val items = decoded.inventories.getValue(KAI_ID).items
    assertTrue(items.containsKey(ItemCatalog.ALMOND_WATER))
    assertFalse(items.containsKey("nước-hạnh-nhân"))
    assertEquals(ItemCatalog.ALMOND_WATER, decoded.metadata["lastReferencedItemId"])

    val remembered = CommandResolver().resolve(
      IntentCandidate("Đưa cho Target", GameIntent.TRANSFER_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
      0, decoded.turn.currentTurnId,
      GameContext(decoded, mapOf("kai" to KAI_ID, "target" to "target"))
    ) as ItemCommand
    assertEquals(ItemCatalog.ALMOND_WATER, remembered.itemId)
  }

  @Test fun longestOfficialAliasWinsInsidePhrase() {
    assertEquals(
      listOf(ItemCatalog.FLASHLIGHT),
      ItemCatalog.officialMentions("Trên bàn có một Đèn pin.").map { it.id }
    )
  }

  @Test fun quantityWordsInsideOfficialNamesAreNotCounts() {
    val resolver = DefaultQuantityResolver()
    assertEquals(1, resolver.resolve("Đưa Cá Mòi Ba Cô Gái cho Target"))
    assertEquals(2, resolver.resolve("Đưa 2 Cá Mòi Ba Cô Gái cho Target"))
    assertEquals(3, resolver.resolve("Đưa ba Băng gạc cho Target"))
  }

  @Test fun futureExplicitIdIsPreserved() {
    assertEquals("future:field-kit", ItemCatalog.identityId("future:field-kit", "Future Field Kit"))
  }
}
