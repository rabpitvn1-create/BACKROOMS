package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CommandResolverTest {
  private val resolver = CommandResolver()
  private val base = GameState.initial()
  private val context = GameContext(
    base.copy(
      characters = base.characters + ("iris" to CharacterState("iris", "Iris")),
      inventories = base.inventories + (
        KAI_ID to InventoryState(
          KAI_ID,
          mapOf("almond-water" to ItemStack("almond-water", "chai nước", 3))
        )
      )
    ),
    actorAliases = mapOf("kai" to KAI_ID, "iris" to "iris"),
    itemAliases = mapOf("chai nước" to "almond-water")
  )

  @Test fun resolvesActorItemQuantityAndTargetDeterministically() {
    val candidate = IntentCandidate("Kai đưa Iris hai chai nước", GameIntent.TRANSFER_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE)
    val command = resolver.resolve(candidate, 0, "TURN_184", context) as ItemCommand
    assertEquals(KAI_ID, command.actorId)
    assertEquals("iris", command.targetId)
    assertEquals("almond-water", command.itemId)
    assertEquals(2, command.quantity)
    assertTrue(command.commandId.startsWith("TURN_184:"))
    assertEquals(command.commandId, (resolver.resolve(candidate, 0, "TURN_184", context) as ItemCommand).commandId)
  }
}
