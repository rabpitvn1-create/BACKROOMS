package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class SruEquipmentIntegrationTest {
  private fun fresh() = CharacterEquipmentSystem.normalize(SpecialFollowersCanon.ensure(AnNhienCanon.ensure(GameState.initial())))

  @Test fun kaiUsesCurrentSruEquipmentWithoutRetiredNames() {
    val state = fresh()
    val slots = state.equipment.getValue(KAI_ID).slots
    assertEquals(KAI_SRU_SG_ID, slots["weapon"])
    assertEquals(KAI_SRU_MK20_ID, slots["armor"])
    assertEquals(KAI_OMNIVAULT_RING_ID, slots["ring"])
    val names = slots.values.mapNotNull(EquipmentCatalog::definition).map { it.name }
    assertTrue("SRU-SG Shotgun" in names)
    assertTrue("SRU-MK20 Powered Armor" in names)
    assertFalse(names.any { it.contains("White Wraith", true) || it.contains("Blackblood", true) || it.contains("Demon Jaw", true) || it.contains("Talon", true) || it.contains("Phantom Greaves", true) })
    assertEquals("Demon Shell ∞ / Physical Shell finite", EquipmentCatalog.definition(KAI_SRU_SG_ID)!!.weapon!!.ammoDisplay)
  }

  @Test fun irisUsesProject07AndIvoryEbony() {
    val state = fresh()
    assertEquals(IRIS_IVORY_EBONY_SET_ID, state.equipment.getValue(IRIS_ID).slots["weapon"])
    assertEquals(IRIS_PROJECT_07_ID, state.equipment.getValue(IRIS_ID).slots["armor"])
    val project = EquipmentCatalog.definition(IRIS_PROJECT_07_ID)!!
    assertEquals("Project 07", project.name)
    val text = project.abilities.joinToString(" ") { it.name + " " + it.description }.lowercase()
    assertFalse(text.contains("drone bay"))
    assertFalse(text.contains("launcher"))
  }

  @Test fun syvialUsesGodKillerAndLuciferArmor() {
    val state = fresh()
    assertEquals(SYVIAL_GODKILLER_ID, state.equipment.getValue(SYVIAL_ID).slots["weapon"])
    assertEquals(SYVIAL_LUCIFER_ARMOR_ID, state.equipment.getValue(SYVIAL_ID).slots["armor"])
    assertEquals("GodKiller", EquipmentCatalog.definition(SYVIAL_GODKILLER_ID)!!.name)
    assertEquals("Lucifer Armor", EquipmentCatalog.definition(SYVIAL_LUCIFER_ARMOR_ID)!!.name)
  }

  @Test fun legacyEquipmentIdsMigrateToCurrentIds() {
    val fresh = fresh()
    val inv = fresh.inventories.getValue(KAI_ID)
    val eq = fresh.equipment.getValue(KAI_ID)
    val legacy = fresh.copy(
      inventories = fresh.inventories + (KAI_ID to inv.copy(items = (inv.items - KAI_SRU_SG_ID) + (KAI_LEGACY_WHITE_WRAITH_ID to ItemStack(KAI_LEGACY_WHITE_WRAITH_ID, "White Wraith Magnum")))),
      equipment = fresh.equipment + (KAI_ID to eq.copy(slots = eq.slots + ("weapon" to KAI_LEGACY_WHITE_WRAITH_ID))),
      metadata = fresh.metadata - "characterEquipmentSchemaVersion"
    )
    val migrated = CharacterEquipmentSystem.normalize(legacy)
    assertEquals(KAI_SRU_SG_ID, migrated.equipment.getValue(KAI_ID).slots["weapon"])
    assertTrue(migrated.inventories.getValue(KAI_ID).items.containsKey(KAI_SRU_SG_ID))
    assertFalse(migrated.inventories.getValue(KAI_ID).items.containsKey(KAI_LEGACY_WHITE_WRAITH_ID))
  }
}
