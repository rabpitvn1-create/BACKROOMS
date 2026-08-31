package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class OmnivaultCurrentCanonNaturalFlowTest {
  private fun fresh() = CharacterEquipmentSystem.normalize(SpecialFollowersCanon.ensure(AnNhienCanon.ensure(GameState.initial())))

  @Test fun storageAndWithdrawalRemainAvailable() {
    var state = fresh()
    val inv = state.inventories.getValue(KAI_ID)
    state = state.copy(inventories = state.inventories + (KAI_ID to inv.copy(items = inv.items + ("scrap" to ItemStack("scrap", "Scrap", 2)))))
    val stored = OmnivaultEngine.execute(state, OmnivaultCommand(
      "store", "TURN_1", KAI_ID, source=CommandSource.RULE, operation=OmnivaultCommand.Operation.STORE,
      itemId="scrap", itemName="Scrap", quantity=1
    ))
    assertTrue(stored.applied)
    val withdrawn = OmnivaultEngine.execute(stored.state, OmnivaultCommand(
      "withdraw", "TURN_1", KAI_ID, source=CommandSource.RULE, operation=OmnivaultCommand.Operation.WITHDRAW,
      itemId="scrap", itemName="Scrap", quantity=1
    ))
    assertTrue(withdrawn.applied)
    assertEquals(2, withdrawn.state.inventories.getValue(KAI_ID).items.getValue("scrap").quantity)
  }

  @Test fun scanAndCopyStayRetiredAndTemplateStateIsCleared() {
    val dirty = fresh().copy(omnivault = fresh().omnivault.copy(
      scanSlots = listOf(ScanSlot(1, "legacy", ItemStack("legacy", "Legacy"), 1L)),
      markedSourceIds = setOf("legacy")
    ))
    val normalized = CharacterEquipmentSystem.normalize(dirty)
    assertTrue(normalized.omnivault.scanSlots.isEmpty())
    assertTrue(normalized.omnivault.markedSourceIds.isEmpty())
    for (op in listOf(OmnivaultCommand.Operation.SCAN, OmnivaultCommand.Operation.COPY)) {
      val result = OmnivaultEngine.execute(normalized, OmnivaultCommand(
        "retired-$op", "TURN_1", KAI_ID, source=CommandSource.RULE, operation=op,
        itemId="legacy", itemName="Legacy"
      ))
      assertFalse(result.applied)
      assertEquals("omnivault_capability_retired", result.validation.reason)
    }
  }

  @Test fun restoreOnlyAcceptsCurrentKaiEquipmentAndCooldownIsPerItem() {
    var state = fresh()
    val inv = state.inventories.getValue(KAI_ID)
    state = state.copy(inventories = state.inventories + (KAI_ID to inv.copy(items = inv.items - KAI_SRU_SG_ID)))
    val restored = OmnivaultEngine.execute(state, OmnivaultCommand(
      "restore-sg", "TURN_1", KAI_ID, source=CommandSource.UI, operation=OmnivaultCommand.Operation.RESTORE,
      itemId=KAI_SRU_SG_ID, itemName="SRU-SG Shotgun", timestampEpochMs=10_000L
    ))
    assertTrue(restored.applied)
    assertTrue(restored.state.inventories.getValue(KAI_ID).items.containsKey(KAI_SRU_SG_ID))
    assertEquals(KAI_SRU_SG_ID, restored.state.equipment.getValue(KAI_ID).slots["weapon"])
    val wrong = OmnivaultEngine.execute(restored.state, OmnivaultCommand(
      "restore-wrong", "TURN_1", KAI_ID, source=CommandSource.UI, operation=OmnivaultCommand.Operation.RESTORE,
      itemId="flashlight", itemName="Flashlight", timestampEpochMs=10_001L
    ))
    assertFalse(wrong.applied)
    assertEquals("omnivault_restore_noncurrent_equipment", wrong.validation.reason)
  }
}
