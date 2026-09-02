package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CompanionConsumableOwnerTest {
  private fun state(): GameState {
    val initial = GameState.initial()
    val kai = initial.characters.getValue(KAI_ID)
    val lucia = CharacterState(
      LUCIA_ID, "Lucia \"Lục\"", metadata = mapOf("aliases" to "Lục"),
      physiology = PhysiologyState(minutesSinceFood = 240, minutesSinceWater = 180)
    )
    return initial.copy(characters = initial.characters + mapOf(
      KAI_ID to kai.copy(physiology = kai.physiology.copy(minutesSinceFood = 120, minutesSinceWater = 90)),
      LUCIA_ID to lucia
    ))
  }

  private fun grant(state: GameState, owner: String, id: String, quantity: Int = 1): GameState {
    val item = ItemCatalog.find(id)!!
    val result = StateReducer.execute(state, ItemCommand(
      commandId = "grant-$owner-$id", turnId = state.turn.currentTurnId, actorId = owner,
      source = CommandSource.SYSTEM, operation = ItemCommand.Operation.PICKUP,
      itemId = item.id, itemName = item.name, quantity = quantity, metadata = item.metadata
    ))
    assertTrue(result.validation.reason.orEmpty(), result.applied)
    return result.state
  }

  private fun resolve(state: GameState, input: String): ItemCommand {
    val context = GameContext(state)
    val intent = RuleIntentInterpreter().interpretSync(input, context)
    assertFalse(input, intent.requiresFallback)
    val command = CommandResolver().resolve(intent.candidates.single(), 0, state.turn.currentTurnId, context)
    assertNotNull(input, command)
    return command as ItemCommand
  }

  private fun quantity(state: GameState, owner: String, item: String): Int =
    state.inventories[owner]?.items?.get(item)?.quantity ?: 0

  @Test fun feedingLuciaConsumesOnlyKaisRiceAndAppliesFoodToLuciaOnce() {
    for (input in listOf("Kai cho Lucia ăn cơm gà", "Cho Lucia ăn cơm gà", "Bạn cho Lucia ăn hộp cơm gà", "Kai cho Lục ăn cơm gà")) {
      val before = grant(grant(state(), KAI_ID, ItemCatalog.CHICKEN_RICE_BOX, 2), LUCIA_ID, ItemCatalog.CHICKEN_RICE_BOX)
      val command = resolve(before, input)
      assertEquals(ItemCommand.Operation.USE, command.operation)
      assertEquals(KAI_ID, command.actorId)
      assertEquals(LUCIA_ID, command.targetId)
      assertEquals(ItemCatalog.CHICKEN_RICE_BOX, command.itemId)
      val result = StateReducer.execute(before, command)
      assertTrue("$input: ${result.validation.reason}", result.applied)
      assertEquals(1, quantity(result.state, KAI_ID, ItemCatalog.CHICKEN_RICE_BOX))
      assertEquals(1, quantity(result.state, LUCIA_ID, ItemCatalog.CHICKEN_RICE_BOX))
      assertEquals(0L, result.state.characters.getValue(LUCIA_ID).physiology.minutesSinceFood)
      assertEquals(before.characters.getValue(KAI_ID).physiology, result.state.characters.getValue(KAI_ID).physiology)
      val duplicate = StateReducer.execute(result.state, command)
      assertTrue(duplicate.duplicate)
      assertEquals(result.state, duplicate.state)
    }
  }

  @Test fun luciaEatingDirectlyStillUsesHerOwnInventory() {
    val before = grant(grant(state(), KAI_ID, ItemCatalog.CHICKEN_RICE_BOX), LUCIA_ID, ItemCatalog.CHICKEN_RICE_BOX)
    val command = resolve(before, "Lucia ăn cơm gà")
    assertEquals(LUCIA_ID, command.actorId)
    val result = StateReducer.execute(before, command)
    assertTrue(result.applied)
    assertEquals(1, quantity(result.state, KAI_ID, ItemCatalog.CHICKEN_RICE_BOX))
    assertEquals(0, quantity(result.state, LUCIA_ID, ItemCatalog.CHICKEN_RICE_BOX))
    assertEquals(0L, result.state.characters.getValue(LUCIA_ID).physiology.minutesSinceFood)
  }

  @Test fun luciaCanGiveKaiWaterWithoutTransferringItFirst() {
    val before = grant(state(), LUCIA_ID, ItemCatalog.LA_VIE)
    val command = resolve(before, "Lucia cho Kai uống nước suối La Vie")
    assertEquals(LUCIA_ID, command.actorId)
    assertEquals(KAI_ID, command.targetId)
    val result = StateReducer.execute(before, command)
    assertTrue(result.validation.reason.orEmpty(), result.applied)
    assertEquals(0, quantity(result.state, LUCIA_ID, ItemCatalog.LA_VIE))
    assertEquals(0L, result.state.characters.getValue(KAI_ID).physiology.minutesSinceWater)
    assertEquals(before.characters.getValue(LUCIA_ID).physiology, result.state.characters.getValue(LUCIA_ID).physiology)
  }

  @Test fun arbitraryCharacterNamesDoNotBecomeUseVerbs() {
    val initial = state()
    var before = initial.copy(characters = initial.characters + mapOf(
      "van" to CharacterState("van", "Trần Văn"),
      "mika" to CharacterState("mika", "Mika")
    ))
    before = CharacterStatEngine.setCurrentHp(grant(before, "van", ItemCatalog.BANDAGE), "mika", 20)
    val command = resolve(before, "Trần Văn cho Mika dùng băng gạc")
    assertEquals("van", command.actorId)
    assertEquals("mika", command.targetId)
    val result = StateReducer.execute(before, command)
    assertTrue(result.validation.reason.orEmpty(), result.applied)
    assertEquals(35, result.state.characters.getValue("mika").vitalState.currentHp)
    assertEquals(0, quantity(result.state, "van", ItemCatalog.BANDAGE))
  }

  @Test fun missingGiverStockDoesNotBorrowFromRecipient() {
    val before = grant(state(), LUCIA_ID, ItemCatalog.CHICKEN_RICE_BOX)
    val command = resolve(before, "Kai cho Lucia ăn cơm gà")
    val result = StateReducer.execute(before, command)
    assertFalse(result.applied)
    assertEquals("item_not_owned", result.validation.reason)
    assertEquals(before, result.state)
  }

  @Test fun unknownAmbiguousAndNegatedPartiesCannotBecomeSelfUse() {
    val initial = grant(state(), KAI_ID, ItemCatalog.CHICKEN_RICE_BOX)
    val before = initial.copy(characters = initial.characters + mapOf(
      "alex-a" to CharacterState("alex-a", "Alex A"),
      "alex-b" to CharacterState("alex-b", "Alex B")
    ))
    val context = GameContext(before)
    for (input in listOf(
      "Kai cho Người lạ ăn hộp cơm gà", "Người lạ cho Lucia ăn hộp cơm gà",
      "Kai cho Alex ăn hộp cơm gà", "Alex cho Lucia ăn hộp cơm gà",
      "Kai không cho Lucia ăn hộp cơm gà", "Đừng cho Lucia ăn hộp cơm gà"
    )) {
      val candidate = RuleIntentInterpreter().interpretSync(input, context).candidates.single()
      assertNull(input, CommandResolver().resolve(candidate, 0, before.turn.currentTurnId, context))
    }
  }

  @Test fun transferringThenEatingRemainsSupported() {
    val before = grant(state(), KAI_ID, ItemCatalog.CHICKEN_RICE_BOX)
    val transfer = StateReducer.execute(before, resolve(before, "Kai đưa hộp cơm gà cho Lucia"))
    assertTrue(transfer.applied)
    val eat = StateReducer.execute(transfer.state, resolve(transfer.state, "Lucia ăn cơm gà"))
    assertTrue(eat.validation.reason.orEmpty(), eat.applied)
    assertEquals(0, quantity(eat.state, KAI_ID, ItemCatalog.CHICKEN_RICE_BOX))
    assertEquals(0, quantity(eat.state, LUCIA_ID, ItemCatalog.CHICKEN_RICE_BOX))
    assertEquals(0L, eat.state.characters.getValue(LUCIA_ID).physiology.minutesSinceFood)
  }
}
