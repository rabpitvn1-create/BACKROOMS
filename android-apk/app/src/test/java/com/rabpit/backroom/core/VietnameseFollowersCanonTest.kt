package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VietnameseFollowersCanonTest {
  @Test fun freshRegistrySeedsTwoUnencounteredHumansWithoutAddingToParty() {
    val state = GameState.initial()
    assertEquals(listOf(CAO_MINH_ID), state.party.memberIds)
    VietnameseFollowersCanon.ids.forEach { id ->
      val character = state.characters.getValue(id)
      assertEquals(CharacterPresence.MISSING, character.presence)
      assertEquals("false", character.metadata["encountered"])
      assertEquals("human", character.metadata["species"])
      assertEquals("false", character.metadata["automaticJoin"])
      assertEquals("true", character.metadata["joinEligible"])
      assertEquals(4, state.equipment.getValue(id).slots.size)
      assertEquals(4, state.inventories.getValue(id).items.size)
      assertEquals(0, InventoryCapacityPolicy.usedSlots(state, id))
      assertEquals(CharacterStatEngine.effective(state, id).maxHp, character.vitalState.currentHp)
      assertEquals(EnergyMode.NOT_APPLICABLE, character.statProfile.energy.mode)
    }
    assertEquals("Nguyễn Quốc Nam", state.characters.getValue(NGUYEN_QUOC_NAM_ID).name)
    assertEquals("Hứa Tiểu Lan", state.characters.getValue(HUA_TIEU_LAN_ID).name)
    assertFalse(state.characters.keys.any { it in setOf("nguyen_hong_lan", "nguyen_thuy_duong", "anh_ty") })
  }

  @Test fun loadoutsHaveExactWeaponDamageAndSourceArtwork() {
    val state = GameState.initial()
    assertEquals(19, CharacterStatEngine.weaponDamage(state, NGUYEN_QUOC_NAM_ID))
    assertEquals(25, CharacterStatEngine.weaponDamage(state, HUA_TIEU_LAN_ID))
    assertEquals("avatars/NguyenQuocNam_avatar.webp", state.characters.getValue(NGUYEN_QUOC_NAM_ID).avatarRef)
    assertEquals("avatars/HuaTieuLan_avatar.webp", state.characters.getValue(HUA_TIEU_LAN_ID).avatarRef)
    assertEquals("8 loaded / one spare magazine, reserve unknown", EquipmentCatalog.definition("nam:corridor-pistol")!!.weapon!!.ammoDisplay)
    assertEquals("1 loaded / finite reserve", EquipmentCatalog.definition("tieu-lan:truong-xuan")!!.weapon!!.ammoDisplay)
    VietnameseFollowersCanon.definitions.forEach { definition ->
      assertTrue(definition.iconRef!!.startsWith("item-icons/"))
      assertTrue(definition.iconRef!!.endsWith(".webp"))
      assertEquals(ItemClassification.CANONICAL, definition.classification)
    }
    assertEquals(8, VietnameseFollowersCanon.definitions.map { it.id }.toSet().size)
    assertEquals(EquipmentSlot.WRIST, EquipmentSlot.fromRaw("wrist"))
    assertFalse(state.inventories.values.flatMap { it.items.values }.any { it.name in setOf("Nét Vẽ Của Dương", "Thiệp Mời Sinh Nhật") })
  }

  @Test fun ensureNeverHealsReequipsRefillsOrRejoinsExistingFollower() {
    val initial = GameState.initial()
    val id = NGUYEN_QUOC_NAM_ID
    val char = initial.characters.getValue(id).copy(
      presence = CharacterPresence.SEPARATED,
      vitalState = CharacterVitalState(9, CharacterCondition.CRITICAL),
      metadata = initial.characters.getValue(id).metadata + ("encountered" to "true")
    )
    val state = initial.copy(
      characters = initial.characters + (id to char),
      inventories = initial.inventories + (id to InventoryState(id)),
      equipment = initial.equipment + (id to EquipmentState(id))
    )
    assertEquals(state, VietnameseFollowersCanon.ensure(state))
    assertEquals(state, VietnameseFollowersCanon.ensure(VietnameseFollowersCanon.ensure(state)))
  }

  @Test fun ensureCanSeedRegistryForCoreBootstrapWithoutJoining() {
    val initial = GameState.initial()
    val stripped = initial.copy(
      characters = initial.characters - VietnameseFollowersCanon.ids.toSet(),
      inventories = initial.inventories - VietnameseFollowersCanon.ids.toSet(),
      equipment = initial.equipment - VietnameseFollowersCanon.ids.toSet()
    )
    val seeded = VietnameseFollowersCanon.ensure(stripped)
    assertEquals(initial.characters.keys, seeded.characters.keys)
    assertEquals(stripped.party, seeded.party)
    assertEquals(4, seeded.inventories.getValue(NGUYEN_QUOC_NAM_ID).items.size)
    assertEquals(seeded, VietnameseFollowersCanon.ensure(seeded))
  }

  @Test fun gearModifiersUseCurrentStatsAndOnlyApplyWhileOwnedAndEquipped() {
    var state = GameState.initial()
    val id = NGUYEN_QUOC_NAM_ID
    val nam = state.characters.getValue(id)
    state = state.copy(characters = state.characters + (id to nam.copy(statProfile = nam.statProfile.copy(def = 10, vit = 10))))
    val effective = CharacterStatCore.effective(state, id)
    assertEquals(11, effective.def)
    assertEquals(11, effective.vit)
    assertEquals(88, CharacterStatEngine.attackAccuracy(state, id, 80))
    assertEquals(99, CharacterStatEngine.attackAccuracy(state, id, 99))
    val bare = state.copy(equipment = state.equipment + (id to EquipmentState(id)))
    assertEquals(10, CharacterStatCore.effective(bare, id).def)
    assertEquals(10, CharacterStatCore.effective(bare, id).vit)
    assertEquals(80, CharacterStatEngine.attackAccuracy(bare, id, 80))
    val unowned = state.copy(inventories = state.inventories + (id to InventoryState(id)))
    assertEquals(10, CharacterStatCore.effective(unowned, id).def)
    assertEquals(80, CharacterStatEngine.attackAccuracy(unowned, id, 80))
  }

  @Test fun lanOutfitMultipliesEvasionWithoutReintroducingAgiOrDexStats() {
    val initial = GameState.initial()
    val id = HUA_TIEU_LAN_ID
    val lan = initial.characters.getValue(id)
    val state = initial.copy(characters = initial.characters + (id to lan.copy(statProfile = lan.statProfile.copy(vit = 10))))
    assertEquals(11, CharacterStatCore.effective(state, id).evasionPercent)
    val noOutfit = state.copy(equipment = state.equipment + (id to state.equipment.getValue(id).copy(
      slots = state.equipment.getValue(id).slots - "outfit"
    )))
    assertEquals(10, CharacterStatCore.effective(noOutfit, id).evasionPercent)
    val json = JSONObject(GameStateCodec.encode(state)).getJSONObject("characters").getJSONObject(id).getJSONObject("statProfile")
    assertEquals(setOf("schema", "baseMaxHp", "STR", "DEF", "SKL", "VIT"), json.keys().asSequence().toSet())
  }

  @Test fun currentSaveRoundTripKeepsDamageConsentPresenceAndRemovedEquipment() {
    val initial = GameState.initial()
    val id = NGUYEN_QUOC_NAM_ID
    val nam = initial.characters.getValue(id).copy(
      presence = CharacterPresence.ACTIVE,
      metadata = initial.characters.getValue(id).metadata + ("encountered" to "true") + ("consentConfirmed" to "true")
    )
    var state = initial.copy(
      characters = initial.characters + (id to nam),
      party = initial.party.copy(memberIds = listOf(CAO_MINH_ID, id)),
      equipment = initial.equipment + (id to initial.equipment.getValue(id).copy(
        slots = initial.equipment.getValue(id).slots - "wrist"
      ))
    )
    state = CharacterStatEngine.setCurrentHp(state, id, 13)
    val decoded = GameStateCodec.decode(GameStateCodec.encode(state))
    assertEquals(13, decoded.characters.getValue(id).vitalState.currentHp)
    assertEquals("true", decoded.characters.getValue(id).metadata["consentConfirmed"])
    assertEquals(CharacterPresence.ACTIVE, decoded.characters.getValue(id).presence)
    assertEquals(state.party, decoded.party)
    assertFalse("wrist" in decoded.equipment.getValue(id).slots)
    assertEquals(80, CharacterStatEngine.attackAccuracy(decoded, id, 80))
    assertEquals(decoded, GameStateCodec.decode(GameStateCodec.encode(decoded)))
  }

  @Test fun detailProjectionExposesIconsModifiersAndGearSeparatelyFromPassives() {
    val state = GameState.initial()
    val projected = CharacterDetailProjector.projectCharacter(state, NGUYEN_QUOC_NAM_ID)!!
    assertEquals(1, projected.def.equipment)
    assertEquals(0, projected.def.passive)
    assertEquals(1, projected.vit.equipment)
    assertEquals(0, projected.vit.passive)
    val json = CharacterDetailJson.encodeCharacter(projected)
    val equipment = json.getJSONArray("equipmentItems")
    for (i in 0 until equipment.length()) {
      val item = equipment.getJSONObject(i)
      assertTrue(item.getString("icon").startsWith("item-icons/"))
      assertFalse(item.getBoolean("consumesInventorySlot"))
    }
    val watch = (0 until equipment.length()).map { equipment.getJSONObject(it) }.first { it.getString("id") == "nam:promise-watch" }
    assertTrue(watch.getBoolean("statItem"))
    assertEquals(10, watch.getJSONObject("equipmentModifiers").getInt("Accuracy"))
  }
}
