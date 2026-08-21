package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CharacterDetailJsonTest {
  @Test fun partyJsonContainsStableUiFieldsAndSubjectiveTime() {
    val member = CharacterDetailProjection(
      id = "iris",
      name = "Iris",
      avatarRef = "avatars/iris.png",
      presence = CharacterPresence.ACTIVE,
      isLeader = false,
      healthState = "INJURED",
      injuries = listOf("left_arm_cut"),
      physiology = DerivedPhysiologyStatus(
        hunger = PhysiologyBand.MILD,
        thirst = PhysiologyBand.MODERATE,
        sleepDeprivation = PhysiologyBand.SEVERE,
        pain = "moderate",
        infection = null,
        thermal = "cold"
      ),
      inventory = listOf(ItemStack(
        itemId = "water",
        name = "Almond Water",
        quantity = 2,
        condition = "sealed",
        metadata = mapOf("secret" to "do-not-expose", "physiologyEffect" to "WATER"),
        contentState = ContentState.FULL
      )),
      equipment = mapOf("weapon" to "ivory"),
      statusEffects = listOf(StatusEffect(
        id = "injury-1",
        type = "INJURY",
        source = "hidden-story-source",
        persistent = true,
        metadata = mapOf("private" to "value")
      ))
    )
    val json = CharacterDetailJson.encodeParty(PartyDetailProjection(
      leaderId = KAI_ID,
      maxMembers = 4,
      elapsedSubjectiveMinutes = 915L,
      members = listOf(member)
    ))

    assertEquals(KAI_ID, json.getString("leaderId"))
    assertEquals(4, json.getInt("maxMembers"))
    assertEquals(915L, json.getLong("elapsedSubjectiveMinutes"))
    val character = json.getJSONArray("members").getJSONObject(0)
    assertEquals("iris", character.getString("id"))
    assertEquals("avatars/iris.png", character.getString("avatar"))
    assertEquals("INJURED", character.getString("healthState"))
    assertEquals("MODERATE", character.getJSONObject("physiology").getString("thirst"))
    assertEquals("cold", character.getJSONObject("physiology").getString("thermal"))
    assertEquals("ivory", character.getJSONObject("equipment").getString("weapon"))
  }

  @Test fun uiJsonDoesNotExposeInventoryOrStatusInternalMetadata() {
    val member = CharacterDetailProjection(
      id = "kai",
      name = "Kai",
      avatarRef = null,
      presence = CharacterPresence.ACTIVE,
      isLeader = true,
      healthState = null,
      injuries = emptyList(),
      physiology = DerivedPhysiologyStatus(
        PhysiologyBand.UNKNOWN,
        PhysiologyBand.UNKNOWN,
        PhysiologyBand.UNKNOWN,
        null,
        null,
        null
      ),
      inventory = listOf(ItemStack(
        "item", "Item", metadata = mapOf("private" to "hidden")
      )),
      equipment = emptyMap(),
      statusEffects = listOf(StatusEffect(
        "status", "EFFECT", "classified-source", metadata = mapOf("private" to "hidden")
      ))
    )

    val json = CharacterDetailJson.encodeCharacter(member)
    val item = json.getJSONArray("inventory").getJSONObject(0)
    val status = json.getJSONArray("statuses").getJSONObject(0)

    assertFalse(item.has("metadata"))
    assertFalse(status.has("source"))
    assertFalse(status.has("metadata"))
    assertEquals("EFFECT", status.getString("type"))
  }

  @Test fun optionalUnknownFieldsRemainAbsentInsteadOfInvented() {
    val member = CharacterDetailProjection(
      id = "survivor",
      name = "Survivor",
      avatarRef = null,
      presence = CharacterPresence.ACTIVE,
      isLeader = false,
      healthState = null,
      injuries = emptyList(),
      physiology = DerivedPhysiologyStatus(
        PhysiologyBand.UNKNOWN,
        PhysiologyBand.UNKNOWN,
        PhysiologyBand.UNKNOWN,
        null,
        null,
        null
      ),
      inventory = emptyList(),
      equipment = emptyMap(),
      statusEffects = emptyList()
    )

    val json = CharacterDetailJson.encodeCharacter(member)
    val physiology = json.getJSONObject("physiology")

    assertFalse(json.has("avatar"))
    assertFalse(json.has("healthState"))
    assertFalse(physiology.has("pain"))
    assertFalse(physiology.has("infection"))
    assertFalse(physiology.has("thermal"))
    assertEquals("UNKNOWN", physiology.getString("hunger"))
  }
}
