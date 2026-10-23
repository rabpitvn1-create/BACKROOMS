package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CommandResolverTest {
  private val resolver = CommandResolver()
  private val context = GameContext(
    GameState.initial().copy(characters = GameState.initial().characters + ("luc-tram" to CharacterState("luc-tram", "Lục Trầm"))),
    actorAliases = mapOf("cao-minh" to CAO_MINH_ID, "luc-tram" to "luc-tram"),
    itemAliases = mapOf("chai nước" to "almond-water")
  )

  @Test fun resolvesActorItemQuantityAndTargetDeterministically() {
    val candidate = IntentCandidate("Cao Minh đưa Lục Trầm hai chai nước", GameIntent.TRANSFER_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE)
    val command = resolver.resolve(candidate, 0, "TURN_184", context) as ItemCommand
    assertEquals(CAO_MINH_ID, command.actorId)
    assertEquals("luc-tram", command.targetId)
    assertEquals("almond-water", command.itemId)
    assertEquals(2, command.quantity)
    assertTrue(command.commandId.startsWith("TURN_184:"))
    assertEquals(command.commandId, (resolver.resolve(candidate, 0, "TURN_184", context) as ItemCommand).commandId)
  }
}
