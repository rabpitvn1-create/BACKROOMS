package com.rabpit.backroom.core
import org.junit.Assert.*
import org.junit.Test
class MadGodEquipmentTest {
  @Test fun cheatWeaponKeepsWeaponDamageButNoCharacterStatBonus() {
    val def = EquipmentCatalog.definition(MADGOD_SET_ID)!!
    assertEquals(55, def.weapon!!.dmg)
    assertFalse(def.bonuses.any())
  }

  @Test fun spawnUpdatesEquipmentStateNotInventory() {
    val base = GameState.initial()
    val (spawned, added) = MadGodCanon.spawn(base)
    assertTrue(added)
    // EquipmentState updated
    assertEquals(MADGOD_SET_ID, spawned.equipment.getValue(KAI_ID).slots["weapon"])
    // No ItemStack created in inventory
    assertFalse(spawned.inventories.getValue(KAI_ID).items.containsKey(MADGOD_SET_ID))
    // Metadata set for UI flag
    assertEquals("true", spawned.metadata["madGod.spawned"])
  }

  @Test fun equipmentProjectionExposesMadGodSetForUi() {
    val base = GameState.initial()
    val (spawned, _) = MadGodCanon.spawn(base)
    val proj = MadGodCanon.equipmentProjection(spawned)
    assertTrue(proj.has("weapon"))
    val weapon = proj.getJSONObject("weapon")
    assertEquals(MADGOD_SET_ID, weapon.getString("id"))
    assertEquals(true, weapon.getBoolean("permanent"))
  }

  @Test fun spawnIsIdempotent() {
    val base = GameState.initial()
    val (first, added1) = MadGodCanon.spawn(base)
    assertTrue(added1)
    val (_, added2) = MadGodCanon.spawn(first)
    assertFalse(added2)
  }
}
