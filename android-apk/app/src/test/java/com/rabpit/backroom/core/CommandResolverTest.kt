package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CommandResolverTest {
  private val resolver = CommandResolver()
  private val context = GameContext(GameState.initial())
  
  @Test fun playerTextCannotDispatchInventoryOrEquipmentMutations() {
    for (intent in listOf(
      GameIntent.PICKUP_ITEM, GameIntent.DROP_ITEM, GameIntent.USE_ITEM,
      GameIntent.TRANSFER_ITEM, GameIntent.EQUIP_ITEM, GameIntent.UNEQUIP_ITEM
    )) {
      val candidate = IntentCandidate("Cao Minh lấy vật phẩm", intent,
        IntentConfidence.HIGH, 0.99f, CommandSource.RULE)
      assertNull(resolver.resolve(candidate, 0, "TURN_1", context))
    }
  }
}
