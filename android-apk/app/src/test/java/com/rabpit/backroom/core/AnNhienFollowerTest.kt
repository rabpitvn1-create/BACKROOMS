package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnNhienFollowerTest {
  @Test fun initialStateSeedsAnNhienOutsidePartyWithFixedEquipment() {
    val state = GameState.initial()
    val an = state.characters[AN_NHIEN_ID]
    assertNotNull(an)
    assertEquals("An Nhiên", an!!.name)
    assertEquals("7", an.metadata["age"])
    assertEquals("human", an.metadata["species"])
    assertEquals("true", an.metadata["nonCombat"])
    assertEquals("0.7", an.metadata["survivalMultiplier"])
    assertFalse(AN_NHIEN_ID in state.party.memberIds)
    assertEquals(4, state.party.maxMembers)
    assertEquals(AnNhienCanon.equipmentSlots, state.equipment[AN_NHIEN_ID]!!.slots)
    assertEquals(2, AnNhienCanon.equipmentSlots.size)
  }

  @Test fun inventoryAcceptsOnlyFoodAndHasSevenTypeSlots() {
    val state = GameState.initial()
    val inventory = state.inventories[AN_NHIEN_ID]!!
    val food = ItemStack("food-1", "Lương khô", metadata = mapOf("category" to "FOOD"))
    val tool = ItemStack("tool-1", "Cờ lê", metadata = mapOf("category" to "TOOL"))
    assertEquals(null, InventoryPolicy.validateAddition(state, AN_NHIEN_ID, inventory, food, 1))
    assertEquals("an_nhien_food_only", InventoryPolicy.validateAddition(state, AN_NHIEN_ID, inventory, tool, 1))

    val sevenFoods = InventoryState(AN_NHIEN_ID, (1..7).associate { index ->
      val id = "food-$index"
      id to ItemStack(id, "Food $index", metadata = mapOf("category" to "FOOD"))
    })
    assertEquals(
      "inventory_slot_limit",
      InventoryPolicy.validateAddition(
        state,
        AN_NHIEN_ID,
        sevenFoods,
        ItemStack("food-8", "Kẹo", metadata = mapOf("category" to "FOOD")),
        1
      )
    )
  }

  @Test fun survivalCapacityIsThirtyPercentLowerThroughExistingPhysiologyPolicy() {
    val awakeMinutes = 26L * 60L
    val normal = PhysiologyStatusPolicy.awakeBand(awakeMinutes)
    val anNhien = PhysiologyStatusPolicy.awakeBand(awakeMinutes, AnNhienCanon.SURVIVAL_MULTIPLIER)
    assertEquals(PhysiologyBand.SEVERE, normal)
    assertEquals(PhysiologyBand.CRITICAL, anNhien)
    assertTrue(
      PhysiologyStatusPolicy.restPercent(awakeMinutes, AnNhienCanon.SURVIVAL_MULTIPLIER)!! <
        PhysiologyStatusPolicy.restPercent(awakeMinutes)!!
    )
  }

  @Test fun followerCannotBeRemovedSeparatedOrMadeLeader() {
    val base = GameState.initial()
    val state = base.copy(party = base.party.copy(memberIds = listOf(KAI_ID, AN_NHIEN_ID)))

    val remove = PartyEngine.execute(state, PartyCommand(
      "remove-an", state.turn.currentTurnId, KAI_ID, AN_NHIEN_ID, CommandSource.SYSTEM, PartyCommand.Operation.REMOVE
    ))
    assertFalse(remove.applied)
    assertEquals("an_nhien_follower_locked", remove.validation.reason)

    val separate = PartyEngine.execute(state, PartyCommand(
      "separate-an", state.turn.currentTurnId, KAI_ID, AN_NHIEN_ID, CommandSource.SYSTEM, PartyCommand.Operation.SEPARATE
    ))
    assertFalse(separate.applied)
    assertEquals("an_nhien_follower_locked", separate.validation.reason)

    val leader = PartyEngine.execute(state, PartyCommand(
      "leader-an", state.turn.currentTurnId, KAI_ID, AN_NHIEN_ID, CommandSource.SYSTEM, PartyCommand.Operation.SET_LEADER
    ))
    assertFalse(leader.applied)
    assertEquals("an_nhien_cannot_lead", leader.validation.reason)
  }

  @Test fun fixedEquipmentCannotBeChanged() {
    val state = GameState.initial().copy(
      inventories = GameState.initial().inventories + (
        AN_NHIEN_ID to InventoryState(AN_NHIEN_ID, mapOf(
          "fake-weapon" to ItemStack("fake-weapon", "Vũ khí thử", metadata = mapOf("category" to "FOOD"))
        ))
      )
    )
    val result = InventoryEngine.execute(state, ItemCommand(
      commandId = "equip-an",
      turnId = state.turn.currentTurnId,
      actorId = AN_NHIEN_ID,
      source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.EQUIP,
      itemId = "fake-weapon",
      itemName = "Vũ khí thử",
      slot = "weapon"
    ))
    assertFalse(result.applied)
    assertEquals("an_nhien_equipment_locked", result.validation.reason)
  }

  @Test fun slashCheatInstantlyAddsAnNhienAndIsIdempotent() {
    val base = GameState.initial()
    assertTrue(AnNhienCanon.matchesPartyCheatCode(" /annhien1234 "))
    assertFalse(AnNhienCanon.matchesPartyCheatCode("/annhien123"))

    val (added, error) = AnNhienCanon.forceIntoParty(base)
    assertEquals(null, error)
    assertTrue(AN_NHIEN_ID in added.party.memberIds)
    assertEquals(base.party.memberIds.size + 1, added.party.memberIds.size)

    val (again, againError) = AnNhienCanon.forceIntoParty(added)
    assertEquals(null, againError)
    assertEquals(added.party.memberIds, again.party.memberIds)
  }

  @Test fun slashCheatDoesNotSilentlyEvictPartyMembersWhenFull() {
    val full = GameState.initial().copy(
      characters = GameState.initial().characters + mapOf(
        "a" to CharacterState("a", "A"),
        "b" to CharacterState("b", "B"),
        "c" to CharacterState("c", "C")
      ),
      party = PartyState(leaderId = KAI_ID, memberIds = listOf(KAI_ID, "a", "b", "c"), maxMembers = 4)
    )
    val (unchanged, error) = AnNhienCanon.forceIntoParty(full)
    assertEquals("party_full", error)
    assertFalse(AN_NHIEN_ID in unchanged.party.memberIds)
    assertEquals(listOf(KAI_ID, "a", "b", "c"), unchanged.party.memberIds)
  }

  @Test fun saveDecodeBackfillsAnNhienWithoutPuttingHerInParty() {
    val old = GameState.initial().copy(
      characters = GameState.initial().characters - AN_NHIEN_ID,
      inventories = GameState.initial().inventories - AN_NHIEN_ID,
      equipment = GameState.initial().equipment - AN_NHIEN_ID,
      world = mapOf("location" to "Level 0 / The Lobby")
    )
    val decoded = GameStateCodec.decode(GameStateCodec.encode(old))
    assertTrue(AN_NHIEN_ID in decoded.characters)
    assertTrue(AN_NHIEN_ID in decoded.inventories)
    assertTrue(AN_NHIEN_ID in decoded.equipment)
    assertFalse(AN_NHIEN_ID in decoded.party.memberIds)
    assertEquals("Level 0 / The Lobby", decoded.world["location"])
  }
}
