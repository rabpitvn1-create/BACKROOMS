package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CharacterItemTransferUseTest {
  private fun luciaPartyState(): GameState {
    val state = LuciaCanon.ensure(GameState.initial())
    return state.copy(party = state.party.copy(memberIds = (state.party.memberIds + LUCIA_ID).distinct()))
  }

  private fun genericPartyState(): GameState {
    val initial = GameState.initial()
    val mikaId = "future:mika"
    val reinaId = "future:reina"
    val mika = CharacterState(
      mikaId,
      "Mika Sol",
      metadata = mapOf("aliases" to "mika,mika sol,msol", "inventoryProfile" to "normal")
    )
    val reina = CharacterState(
      reinaId,
      "Reina Kuroha",
      metadata = mapOf("aliases" to "reina,reina kuroha,kuroha", "inventoryProfile" to "normal")
    )
    return initial.copy(
      characters = initial.characters + (mikaId to mika) + (reinaId to reina),
      inventories = initial.inventories + (mikaId to InventoryState(mikaId)) + (reinaId to InventoryState(reinaId)),
      party = initial.party.copy(memberIds = listOf(KAI_ID, mikaId, reinaId))
    )
  }

  private fun context(state: GameState) = GameContext(
    state = state,
    actorAliases = mapOf("kai" to KAI_ID),
    itemAliases = mapOf("bandage" to ItemCatalog.BANDAGE)
  )

  private fun grant(state: GameState, ownerId: String, itemId: String): GameState {
    val item = ItemCatalog.find(itemId)!!
    val result = InventoryEngine.execute(state, ItemCommand(
      commandId = "grant-$ownerId-$itemId",
      turnId = null,
      actorId = ownerId,
      source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP,
      itemId = item.id,
      itemName = item.name,
      quantity = 1,
      metadata = item.metadata
    ))
    assertTrue(result.validation.reason.orEmpty(), result.applied)
    return result.state
  }

  @Test fun originalLuciaTransferAndUseCommandsResolveCorrectly() {
    val state = luciaPartyState()
    val resolver = CommandResolver()
    val localContext = GameContext(
      state = state,
      actorAliases = mapOf("kai" to KAI_ID, "lucia" to LUCIA_ID),
      itemAliases = mapOf("bandage" to ItemCatalog.BANDAGE)
    )

    val transfer = resolver.resolve(
      IntentCandidate("đưa băng gạc cho Lucia", GameIntent.TRANSFER_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
      0, "TURN_1", localContext
    ) as ItemCommand
    assertEquals(KAI_ID, transfer.actorId)
    assertEquals(LUCIA_ID, transfer.targetId)
    assertEquals(ItemCatalog.BANDAGE, transfer.itemId)

    val use = resolver.resolve(
      IntentCandidate("Lucia dùng băng gạc", GameIntent.USE_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
      0, "TURN_1", localContext
    ) as ItemCommand
    assertEquals(LUCIA_ID, use.actorId)
    assertEquals(ItemCatalog.BANDAGE, use.itemId)

    val reverse = resolver.resolve(
      IntentCandidate("Lucia đưa băng gạc cho Kai", GameIntent.TRANSFER_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
      0, "TURN_1", localContext
    ) as ItemCommand
    assertEquals(LUCIA_ID, reverse.actorId)
    assertEquals(KAI_ID, reverse.targetId)
  }

  @Test fun futureCharactersResolveFromStateAndMetadataWithoutBranches() {
    val state = genericPartyState()
    val resolver = CommandResolver()

    val transfer = resolver.resolve(
      IntentCandidate("Mika đưa băng gạc cho Kuroha", GameIntent.TRANSFER_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
      0, "TURN_1", context(state)
    ) as ItemCommand
    assertEquals("future:mika", transfer.actorId)
    assertEquals("future:reina", transfer.targetId)
    assertEquals(ItemCatalog.BANDAGE, transfer.itemId)

    val use = resolver.resolve(
      IntentCandidate("msol dùng băng gạc", GameIntent.USE_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
      0, "TURN_1", context(state)
    ) as ItemCommand
    assertEquals("future:mika", use.actorId)
  }

  @Test fun ambiguousCharacterAliasFailsClosed() {
    val initial = genericPartyState()
    val alexOne = CharacterState("future:alex-1", "Alex One", metadata = mapOf("aliases" to "alex"))
    val alexTwo = CharacterState("future:alex-2", "Alex Two", metadata = mapOf("aliases" to "alex"))
    val state = initial.copy(
      characters = initial.characters + (alexOne.id to alexOne) + (alexTwo.id to alexTwo),
      inventories = initial.inventories + (alexOne.id to InventoryState(alexOne.id)) + (alexTwo.id to InventoryState(alexTwo.id))
    )
    val resolved = CommandResolver().resolve(
      IntentCandidate("Alex dùng băng gạc", GameIntent.USE_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
      0, "TURN_1", context(state)
    )
    assertNull(resolved)
  }

  @Test fun genericCharactersCanTransferAndUseHealingItems() {
    val mikaId = "future:mika"
    val reinaId = "future:reina"
    var state = grant(genericPartyState(), mikaId, ItemCatalog.BANDAGE)

    val transfer = InventoryEngine.execute(state, ItemCommand(
      commandId = "mika-to-reina-bandage", turnId = null, actorId = mikaId, targetId = reinaId,
      source = CommandSource.RULE, operation = ItemCommand.Operation.TRANSFER,
      itemId = ItemCatalog.BANDAGE, itemName = "Bandage", quantity = 1
    ))
    assertTrue(transfer.validation.reason.orEmpty(), transfer.applied)
    assertFalse(transfer.state.inventories.getValue(mikaId).items.containsKey(ItemCatalog.BANDAGE))
    assertEquals(1, transfer.state.inventories.getValue(reinaId).items.getValue(ItemCatalog.BANDAGE).quantity)

    state = CharacterStatEngine.setCurrentHp(transfer.state, reinaId, 50)
    val kaiHpBefore = state.characters.getValue(KAI_ID).vitalState.currentHp
    val used = InventoryEngine.execute(state, ItemCommand(
      commandId = "reina-use-bandage", turnId = null, actorId = reinaId,
      source = CommandSource.RULE, operation = ItemCommand.Operation.USE,
      itemId = ItemCatalog.BANDAGE, itemName = "Bandage", quantity = 1
    ))
    assertTrue(used.validation.reason.orEmpty(), used.applied)
    assertEquals(65, used.state.characters.getValue(reinaId).vitalState.currentHp)
    assertEquals(kaiHpBefore, used.state.characters.getValue(KAI_ID).vitalState.currentHp)
    assertFalse(used.state.inventories.getValue(reinaId).items.containsKey(ItemCatalog.BANDAGE))
  }

  @Test fun genericCharacterFoodUseUpdatesOnlyItsPhysiology() {
    val reinaId = "future:reina"
    var state = genericPartyState()
    val reina = state.characters.getValue(reinaId)
    state = state.copy(characters = state.characters + (reinaId to reina.copy(
      physiology = reina.physiology.copy(minutesSinceFood = 180L)
    )))
    state = grant(state, reinaId, ItemCatalog.CANNED_FOOD)

    val used = InventoryEngine.execute(state, ItemCommand(
      commandId = "reina-eat", turnId = null, actorId = reinaId,
      source = CommandSource.RULE, operation = ItemCommand.Operation.USE,
      itemId = ItemCatalog.CANNED_FOOD, itemName = "Canned Food", quantity = 1
    ))
    assertTrue(used.validation.reason.orEmpty(), used.applied)
    assertEquals(0L, used.state.characters.getValue(reinaId).physiology.minutesSinceFood)
  }

  @Test fun zeroHpFutureCharacterCannotConsumeHealingItem() {
    val reinaId = "future:reina"
    var state = grant(genericPartyState(), reinaId, ItemCatalog.BANDAGE)
    state = CharacterStatEngine.setCurrentHp(state, reinaId, 0)
    val used = InventoryEngine.execute(state, ItemCommand(
      commandId = "reina-zero-bandage", turnId = null, actorId = reinaId,
      source = CommandSource.RULE, operation = ItemCommand.Operation.USE,
      itemId = ItemCatalog.BANDAGE, itemName = "Bandage", quantity = 1
    ))
    assertFalse(used.applied)
    assertEquals("healing_target_defeated", used.validation.reason)
    assertEquals(1, used.state.inventories.getValue(reinaId).items.getValue(ItemCatalog.BANDAGE).quantity)
  }
}
