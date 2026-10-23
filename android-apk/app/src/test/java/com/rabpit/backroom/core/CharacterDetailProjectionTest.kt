package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CharacterDetailProjectionTest {
  @Test fun partyProjectionKeepsPartyOrderLeaderAndSubjectiveTime() {
    val caoMinh = CharacterState(CAO_MINH_ID, "Cao Minh", avatarRef = "avatars/cao-minh.png")
    val lucTram = CharacterState("luc-tram", "Lục Trầm", avatarRef = "avatars/luc-tram.png")
    val state = GameState.initial().copy(
      characters = linkedMapOf(CAO_MINH_ID to caoMinh, "luc-tram" to lucTram),
      party = PartyState(leaderId = CAO_MINH_ID, memberIds = listOf(CAO_MINH_ID, "luc-tram"), maxMembers = 4),
      time = GameTimeState(elapsedSubjectiveMinutes = 845L)
    )

    val projected = CharacterDetailProjector.projectParty(state)

    assertEquals(CAO_MINH_ID, projected.leaderId)
    assertEquals(4, projected.maxMembers)
    assertEquals(845L, projected.elapsedSubjectiveMinutes)
    assertEquals(listOf(CAO_MINH_ID, "luc-tram"), projected.members.map { it.id })
    assertTrue(projected.members[0].isLeader)
    assertFalse(projected.members[1].isLeader)
  }

  @Test fun characterProjectionUsesCharacterOwnedInventoryEquipmentStatusesAndPhysiology() {
    val injury = StatusEffect("luc-tram-injury", "INJURY", "event", persistent = true)
    val unrelated = StatusEffect("cao-minh-effect", "BUFF", "event")
    val lucTram = CharacterState(
      id = "luc-tram",
      name = "Lục Trầm",
      avatarRef = "avatars/luc-tram.png",
      healthState = "INJURED",
      injuries = listOf("left_arm_cut"),
      inventoryId = "luc-tram-pack",
      equipmentId = "luc-tram-kit",
      statusIds = setOf(injury.id),
      physiology = PhysiologyState(
        minutesSinceFood = 800L,
        minutesSinceWater = 400L,
        minutesAwake = 1300L,
        painState = "moderate"
      )
    )
    val state = GameState.initial().copy(
      characters = mapOf(CAO_MINH_ID to GameState.initial().characters.getValue(CAO_MINH_ID), "luc-tram" to lucTram),
      party = PartyState(memberIds = listOf(CAO_MINH_ID, "luc-tram")),
      inventories = mapOf(
        CAO_MINH_ID to InventoryState(CAO_MINH_ID, mapOf("cao-minh-item" to ItemStack("cao-minh-item", "Cao Minh Item"))),
        "luc-tram-pack" to InventoryState("luc-tram-pack", mapOf(
          "b" to ItemStack("b", "Zeta"),
          "a" to ItemStack("a", "Alpha")
        ))
      ),
      equipment = mapOf(
        CAO_MINH_ID to GameState.initial().equipment.getValue(CAO_MINH_ID),
        "luc-tram-kit" to EquipmentState("luc-tram-kit", mapOf("weapon" to "tich-quang", "armor" to "thien-co-kiem-khai"))
      ),
      statuses = mapOf(injury.id to injury, unrelated.id to unrelated)
    )

    val projected = CharacterDetailProjector.projectCharacter(state, "luc-tram")!!

    assertEquals("Lục Trầm", projected.name)
    assertEquals("INJURED", projected.healthState)
    assertEquals(listOf("left_arm_cut"), projected.injuries)
    assertEquals(listOf("Alpha", "Zeta"), projected.inventory.map { it.name })
    assertEquals(mapOf("armor" to "thien-co-kiem-khai", "weapon" to "tich-quang"), projected.equipment)
    assertEquals(listOf(injury), projected.statusEffects)
    assertEquals(PhysiologyBand.MILD, projected.physiology.hunger)
    assertEquals(PhysiologyBand.MILD, projected.physiology.thirst)
    assertEquals(PhysiologyBand.MODERATE, projected.physiology.sleepDeprivation)
    assertEquals("moderate", projected.physiology.pain)
  }

  @Test fun projectionDoesNotInventMissingData() {
    val unknown = CharacterState("survivor", "Survivor")
    val state = GameState.initial().copy(
      characters = mapOf(CAO_MINH_ID to GameState.initial().characters.getValue(CAO_MINH_ID), "survivor" to unknown),
      party = PartyState(memberIds = listOf(CAO_MINH_ID, "survivor"))
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
