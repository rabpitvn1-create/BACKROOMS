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
      currentHp = 63,
      maxHp = 100,
      injuries = listOf("left_arm_cut"),
      physiology = DerivedPhysiologyStatus(
        hunger = PhysiologyBand.MILD,
        thirst = PhysiologyBand.MODERATE,
        sleepDeprivation = PhysiologyBand.SEVERE,
        pain = "moderate",
        infection = null,
        thermal = "cold",
        foodPercent = 81,
        waterPercent = 50,
        restPercent = 30
      ),
      inventory = listOf(ItemStack(
        itemId = "water",
        name = "Almond Water",
        quantity = 2,
        metadata = mapOf("secret" to "do-not-expose", "physiologyEffect" to "WATER")
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
      leaderId = CAO_MINH_ID,
      maxMembers = 4,
      elapsedSubjectiveMinutes = 915L,
      members = listOf(member)
    ))

    assertEquals(CAO_MINH_ID, json.getString("leaderId"))
    assertEquals(4, json.getInt("maxMembers"))
    assertEquals(915L, json.getLong("elapsedSubjectiveMinutes"))
    val character = json.getJSONArray("members").getJSONObject(0)
    assertEquals("iris", character.getString("id"))
    assertEquals("avatars/iris.png", character.getString("avatar"))
    assertEquals("INJURED", character.getString("healthState"))
    assertEquals(63, character.getInt("currentHp"))
    assertEquals(100, character.getInt("maxHp"))
    val physiology = character.getJSONObject("physiology")
    assertEquals("MODERATE", physiology.getString("thirst"))
    assertEquals("cold", physiology.getString("thermal"))
    assertEquals(81, physiology.getInt("foodPercent"))
    assertEquals(50, physiology.getInt("waterPercent"))
    assertEquals(30, physiology.getInt("restPercent"))
    assertEquals("ivory", character.getJSONObject("equipment").getString("weapon"))
  }

  @Test fun uiJsonDoesNotExposeInventoryOrStatusInternalMetadata() {
    val member = CharacterDetailProjection(
      id = "cao_minh",
      name = "Cao Minh",
      avatarRef = null,
      presence = CharacterPresence.ACTIVE,
      isLeader = true,
      healthState = null,
      currentHp = 100,
      maxHp = 100,
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
    assertEquals(100, json.getInt("currentHp"))
    assertEquals(100, json.getInt("maxHp"))
  }

  @Test fun optionalUnknownFieldsRemainAbsentInsteadOfInvented() {
    val member = CharacterDetailProjection(
      id = "survivor",
      name = "Survivor",
      avatarRef = null,
      presence = CharacterPresence.ACTIVE,
      isLeader = false,
      healthState = null,
      currentHp = 100,
      maxHp = 100,
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
    assertFalse(physiology.has("foodPercent"))
    assertFalse(physiology.has("waterPercent"))
    assertFalse(physiology.has("restPercent"))
    assertEquals("UNKNOWN", physiology.getString("hunger"))
  }
  @Test fun itemDetailsExposeAuthoritativeBonusesHealingAndDescriptions() {
    val initial = GameState.initial()
    val food = OfflineItemRegistry.get(BANH_TET_ID)!!
    val items = listOf(
      ItemStack(BANDAGE_ID, "Băng gạc"),
      ItemStack(food.id, food.name, metadata = food.metadata + ("private" to "hidden")),
      ItemStack("liquid-pain", "Liquid Pain")
    )
    val state = initial.copy(inventories = initial.inventories +
      (CAO_MINH_ID to InventoryState(CAO_MINH_ID, items.associateBy { it.itemId })))
    val json = CharacterDetailJson.encodeCharacter(CharacterDetailProjector.projectCharacter(state, CAO_MINH_ID)!!)
    val equipment = json.getJSONArray("equipmentItems")
    val armor = (0 until equipment.length()).map { equipment.getJSONObject(it) }
      .single { it.getString("id") == CAO_MINH_ARMOR_ID }
    assertTrue(armor.getBoolean("statItem"))
    assertEquals(100, armor.getJSONObject("bonuses").getInt("agiPct"))
    assertEquals(20, armor.getJSONObject("bonuses").getInt("strPct"))
    val inventory = json.getJSONArray("inventory")
    fun item(id: String) = (0 until inventory.length()).map { inventory.getJSONObject(it) }
      .single { it.getString("id") == id }
    assertTrue(item(BANDAGE_ID).getJSONArray("useEffects").toString().contains("Hồi 10 HP"))
    val foodJson = item(BANH_TET_ID)
    assertEquals(food.metadata.getValue("description"), foodJson.getString("description"))
    assertFalse(foodJson.getJSONArray("useEffects").toString().contains("Hồi 5 HP")) // Food reduces hunger; it does not heal HP.
    assertTrue(foodJson.getJSONArray("useEffects").toString().contains("240 phút thiếu thức ăn"))
    assertTrue(foodJson.getJSONArray("useEffects").toString().contains("thiếu thức ăn"))
    assertFalse(foodJson.has("metadata"))
    assertFalse(foodJson.toString().contains("hidden"))
    assertTrue(item(LIQUID_PAIN_ID).getJSONArray("useEffects").toString().contains("Hồi 15% Max HP"))
    assertEquals(HealingItems.LIQUID_PAIN_DESCRIPTION, item(LIQUID_PAIN_ID).getString("description"))
  }
}

