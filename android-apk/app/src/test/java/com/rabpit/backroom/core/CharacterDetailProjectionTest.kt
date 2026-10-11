package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CharacterDetailProjectionTest {
  @Test fun caoMinhPortraitFollowsCanonicalCharacterAndEquipment() {
    val start = GameState.initial()
    val oldRef = start.characters.getValue(CAO_MINH_ID).copy(avatarRef = "avatars/custom_avatar.png")
    val state = start.copy(characters = start.characters + (CAO_MINH_ID to oldRef))
    assertEquals(CAO_MINH_AVATAR_REF, CharacterDetailProjector.projectCharacter(state, CAO_MINH_ID)?.avatarRef)
    val normalKit = state.equipment.getValue(CAO_MINH_ID)
    val equipped = state.copy(equipment = state.equipment + (CAO_MINH_ID to normalKit.copy(slots = normalKit.slots + ("weapon" to MADGOD_SET_ID))))
    assertEquals(MadGodCanon.AVATAR_ASSET, CharacterDetailProjector.projectCharacter(equipped, CAO_MINH_ID)?.avatarRef)
  }

  @Test fun partyProjectionKeepsPartyOrderLeaderAndSubjectiveTime() {
    val caoMinh = CharacterState(CAO_MINH_ID, "Cao Minh", avatarRef = "avatars/caoMinh.png")
    val iris = CharacterState("iris", "Iris", avatarRef = "avatars/iris.png")
    val state = GameState.initial().copy(
      characters = linkedMapOf(CAO_MINH_ID to caoMinh, "iris" to iris),
      party = PartyState(leaderId = CAO_MINH_ID, memberIds = listOf(CAO_MINH_ID, "iris"), maxMembers = 4),
      time = GameTimeState(elapsedSubjectiveMinutes = 845L)
    )

    val projected = CharacterDetailProjector.projectParty(state)

    assertEquals(CAO_MINH_ID, projected.leaderId)
    assertEquals(4, projected.maxMembers)
    assertEquals(845L, projected.elapsedSubjectiveMinutes)
    assertEquals(listOf(CAO_MINH_ID, "iris"), projected.members.map { it.id })
    assertTrue(projected.members[0].isLeader)
    assertFalse(projected.members[1].isLeader)
  }

  @Test fun characterProjectionUsesCharacterOwnedInventoryEquipmentStatusesAndPhysiology() {
    val injury = StatusEffect("iris-injury", "INJURY", "event", persistent = true)
    val unrelated = StatusEffect("caoMinh-effect", "BUFF", "event")
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
      characters = mapOf(CAO_MINH_ID to GameState.initial().characters.getValue(CAO_MINH_ID), "iris" to iris),
      party = PartyState(memberIds = listOf(CAO_MINH_ID, "iris")),
      inventories = mapOf(
        CAO_MINH_ID to InventoryState(CAO_MINH_ID, mapOf("caoMinh-item" to ItemStack("caoMinh-item", "Cao Minh Item"))),
        "iris-pack" to InventoryState("iris-pack", mapOf(
          "b" to ItemStack("b", "Zeta"),
          "a" to ItemStack("a", "Alpha")
        ))
      ),
      equipment = mapOf(
        CAO_MINH_ID to GameState.initial().equipment.getValue(CAO_MINH_ID),
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
