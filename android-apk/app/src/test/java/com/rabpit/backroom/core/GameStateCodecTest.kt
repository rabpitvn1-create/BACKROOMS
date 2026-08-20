package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class GameStateCodecTest {
  @Test fun roundTripPreservesStructuredStateAndPendingTurn() {
    val effect = StatusEffect("s1", "INJURY", "event", "TURN_9", persistent = true)
    val state = GameState.initial().copy(
      inventories = mapOf(KAI_ID to InventoryState(KAI_ID, mapOf("water" to ItemStack("water", "Almond Water", 2)))),
      statuses = mapOf(effect.id to effect),
      characters = mapOf(KAI_ID to CharacterState(KAI_ID, "Kai Akechi", statusIds = setOf(effect.id))),
      omnivault = OmnivaultState(scanSlots = listOf(ScanSlot(1, "water", ItemStack("water", "Almond Water"), 10)), markedSourceIds = setOf("water")),
      turn = TurnState("TURN_9", PendingTurn("TURN_9", "Kai nhặt nước", PendingTurnStatus.INTERPRETING))
    )
    val decoded = GameStateCodec.decode(GameStateCodec.encode(state))
    assertEquals(state, decoded)
  }

  @Test fun legacyWebViewSaveMigratesWithoutLosingInventoryOrParty() {
    val legacy = """{
      "turn":184,
      "title":"BACKROOMS",
      "location":"Level 0",
      "inventory":[{"name":"Almond Water","quantity":2,"state":"sealed"}],
      "party":[{"id":"iris","name":"Iris","avatar":"iris.png"}]
    }"""
    val migrated = GameStateCodec.decode(legacy)
    assertEquals(CURRENT_SAVE_VERSION, migrated.saveVersion)
    assertEquals("TURN_184", migrated.turn.currentTurnId)
    assertEquals(2, migrated.inventories.getValue(KAI_ID).items.values.single().quantity)
    assertEquals(listOf(KAI_ID, "iris"), migrated.party.memberIds)
    assertEquals("Level 0", migrated.world["location"])
  }
}
