package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CharacterDetailProjectionTest {
  @Test fun partyProjectionKeepsPartyOrderLeaderAndSubjectiveTime() {
    val kai = CharacterState(KAI_ID, "Kai Akechi", avatarRef = "avatars/kai.png")
    val iris = CharacterState("iris", "Iris", avatarRef = "avatars/iris.png")
    val state = GameState.initial().copy(
      characters = linkedMapOf(KAI_ID to kai, "iris" to iris),
      party = PartyState(leaderId = KAI_ID, memberIds = listOf(KAI_ID, "iris"), maxMembers = 4),
      time = GameTimeState(elapsedSubjectiveMinutes = 845L)
    )

    val projected = CharacterDetailProjector.projectParty(state)

    assertEquals(KAI_ID, projected.leaderId)
    assertEquals(4, projected.maxMembers)
    assertEquals(845L, projected.elapsedSubjectiveMinutes)
    assertEquals(listOf(KAI_ID, "iris"), projected.members.map { it.id })
    assertTrue(projected.members[0].isLeader)
    assertFalse(projected.members[1].isLeader)
  }

  @Test fun characterProjectionUsesCharacterOwnedInventoryEquipmentStatusesAndPhysiology() {
    val injury = StatusEffect("iris-injury", "INJURY", "event", persistent = true)
    val unrelated = StatusEffect("kai-effect", "BUFF", "event")
    val iris = CharacterState(
      id = "iris",
      name = "Iris",
      avatarRef = "avatars/iris.png",
      healthState = "INJURED",
      injuries = listOf("left_arm_cut"),
      inventoryId = "iris-pack",
      equipmentId = "iris-kit",
      statusIds = setOf(injury.id),
      physiology = PhysiologyState(
        minutesSinceFood = 800L,
        minutesSinceWater = 400L,
        minutesAwake = 1300L,
        painState = "moderate"
      )
    )
    val state = GameState.initial().copy(
      characters = mapOf(KAI_ID to GameState.initial().characters.getValue(KAI_ID), "iris" to iris),
      party = PartyState(memberIds = listOf(KAI_ID, "iris")),
      inventories = mapOf(
        KAI_ID to InventoryState(KAI_ID, mapOf("kai-item" to ItemStack("kai-item", "Kai Item"))),
        "iris-pack" to InventoryState("iris-pack", mapOf(
          "b" to ItemStack("b", "Zeta"),
          "a" to ItemStack("a", "Alpha")
        ))
      ),
      equipment = mapOf(
        KAI_ID to GameState.initial().equipment.getValue(KAI_ID),
        "iris-kit" to EquipmentState("iris-kit", mapOf("weapon" to "ivory", "armor" to "argus"))
      ),
      statuses = mapOf(injury.id to injury, unrelated.id to unrelated)
    )

    val projected = CharacterDetailProjector.projectCharacter(state, "iris")!!

    assertEquals("Iris", projected.name)
    assertEquals("INJURED", projected.healthState)
    assertEquals(listOf("left_arm_cut"), projected.injuries)
    assertEquals(listOf("Alpha", "Zeta"), projected.inventory.map { it.name })
    assertEquals(mapOf("armor" to "argus", "weapon" to "ivory"), projected.equipment)
    assertEquals(listOf(injury), projected.statusEffects)
    assertEquals(PhysiologyBand.MILD, projected.physiology.hunger)
    assertEquals(PhysiologyBand.MILD, projected.physiology.thirst)
    assertEquals(PhysiologyBand.MODERATE, projected.physiology.sleepDeprivation)
    assertEquals("moderate", projected.physiology.pain)
  }

  @Test fun projectionDoesNotInventMissingData() {
    val unknown = CharacterState("survivor", "Survivor")
    val state = GameState.initial().copy(
      characters = mapOf(KAI_ID to GameState.initial().characters.getValue(KAI_ID), "survivor" to unknown),
      party = PartyState(memberIds = listOf(KAI_ID, "survivor"))
    )

    val projected = CharacterDetailProjector.projectCharacter(state, "survivor")!!

    assertTrue(projected.inventory.isEmpty())
    assertTrue(projected.equipment.isEmpty())
    assertTrue(projected.statusEffects.isEmpty())
    assertEquals(PhysiologyBand.UNKNOWN, projected.physiology.hunger)
    assertEquals(PhysiologyBand.UNKNOWN, projected.physiology.thirst)
    assertEquals(PhysiologyBand.UNKNOWN, projected.physiology.sleepDeprivation)
    assertNull(projected.physiology.pain)
    assertNull(projected.physiology.infection)
    assertNull(projected.physiology.thermal)
  }

  @Test fun unknownCharacterProjectionReturnsNull() {
    assertNull(CharacterDetailProjector.projectCharacter(GameState.initial(), "missing"))
  }
}
