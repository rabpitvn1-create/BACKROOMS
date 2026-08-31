package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WorldItemLedgerTest {
  @Test fun genericPickupCollectsBothVisibleItemsAtCurrentLocation() {
    var flags: String? = null
    flags = WorldItemLedger.record(flags, "Level 0 hall", JSONObject().put("id", ItemCatalog.BANDAGE).put("name", "Bandage").toString())
    flags = WorldItemLedger.record(flags, "Level 0 hall", JSONObject().put("id", ItemCatalog.ANTISEPTIC).put("name", "Antiseptic").toString())
    val result = WorldItemLedger.consume(flags, "Level 0 hall", "Nhặt lấy vật phẩm trên bàn")
    assertNotNull(result)
    assertEquals(setOf(ItemCatalog.BANDAGE, ItemCatalog.ANTISEPTIC), result!!.items.map { it.itemId }.toSet())
    val remaining = JSONObject(result.flagsJson).getJSONArray("worldItems")
    assertEquals(2, remaining.length())
    assertFalse(remaining.getJSONObject(0).getBoolean("available"))
    assertFalse(remaining.getJSONObject(1).getBoolean("available"))
  }

  @Test fun legacyNarrativeBackfillsBrokenExistingSave() {
    val narrative = "Trên mặt bàn phủ bụi, bạn nhìn thấy một hộp sơ cứu. Bên trong lộ ra Bandage cùng Antiseptic chưa qua sử dụng."
    val result = WorldItemLedger.consume(null, "Level 0 hall", "Nhặt lấy", listOf(narrative))
    assertNotNull(result)
    assertEquals(setOf(ItemCatalog.BANDAGE, ItemCatalog.ANTISEPTIC), result!!.items.map { it.itemId }.toSet())
  }

  @Test fun namedPickupDoesNotTakeUnrelatedVisibleItem() {
    var flags: String? = null
    flags = WorldItemLedger.record(flags, "room-a", JSONObject().put("id", ItemCatalog.BANDAGE).put("name", "Bandage").toString())
    flags = WorldItemLedger.record(flags, "room-a", JSONObject().put("id", ItemCatalog.ANTISEPTIC).put("name", "Antiseptic").toString())
    val result = WorldItemLedger.consume(flags, "room-a", "Nhặt Bandage")
    assertNotNull(result)
    assertEquals(listOf(ItemCatalog.BANDAGE), result!!.items.map { it.itemId })
    val remaining = JSONObject(result.flagsJson).getJSONArray("worldItems")
    val available = (0 until remaining.length()).map { remaining.getJSONObject(it) }.filter { it.optBoolean("available", true) }
    assertEquals(1, available.size)
    assertEquals(ItemCatalog.ANTISEPTIC, available.single().getString("id"))
  }

  @Test fun pickupCannotConsumeItemFromAnotherLocation() {
    val flags = WorldItemLedger.record(null, "room-a", JSONObject().put("id", ItemCatalog.BANDAGE).put("name", "Bandage").toString())
    assertNull(WorldItemLedger.consume(flags, "room-b", "Nhặt Bandage"))
  }
}
