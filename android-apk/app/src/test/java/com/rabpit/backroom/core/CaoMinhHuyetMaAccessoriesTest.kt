package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class CaoMinhHuyetMaAccessoriesTest {
  private fun withGear(vararg added: Pair<String, String>): GameState {
    val original = GameState.initial()
    val equipped = linkedMapOf(
      "weapon" to CAO_MINH_SWORD_ID,
      "armor" to CAO_MINH_ARMOR_ID
    )
    added.forEach { (slot, id) -> equipped[slot] = id }
    return original.copy(equipment = original.equipment +
      (CAO_MINH_ID to EquipmentState(CAO_MINH_ID, equipped)))
  }

  @Test fun `both exclusive bound accessories have correct slots and 100 percent AGI DEX`() {
    val boots = EquipmentCatalog.definition(CAO_MINH_BOOTS_ID)!!
    val gloves = EquipmentCatalog.definition(CAO_MINH_GAUNTLETS_ID)!!
    assertEquals("Giày Huyết Ma", boots.name)
    assertEquals("Găng tay Huyết Ma", gloves.name)
    assertEquals(EquipmentSlot.FOOTWEAR, boots.primarySlot)
    assertEquals(EquipmentSlot.GAUNTLETS, gloves.primarySlot)
    for (def in listOf(boots, gloves)) {
      assertEquals(100, def.bonuses.agiPct)
      assertEquals(100, def.bonuses.dexPct)
      assertEquals(3, def.skillDamageMultiplier)
      assertTrue(def.abilities.any { it.description.contains("×3") })
      assertFalse(EquipmentCatalog.isUsableBy(def.id, JANET_ID))
      assertTrue(EquipmentCatalog.isUsableBy(def.id, CAO_MINH_ID))
    }
    assertEquals(CAO_MINH_BOOTS_ID, CaoMinhStartingEquipment.slots["footwear"])
    assertEquals(CAO_MINH_GAUNTLETS_ID, CaoMinhStartingEquipment.slots["gauntlets"])
    val fresh = GameState.initial().equipment.getValue(CAO_MINH_ID).slots
    assertEquals(CAO_MINH_BOOTS_ID, fresh["footwear"])
    assertEquals(CAO_MINH_GAUNTLETS_ID, fresh["gauntlets"])
  }

  @Test fun `accessory bonuses are additive and both skill multipliers compound to nine`() {
    val base = withGear()
    val boots = withGear("footwear" to CAO_MINH_BOOTS_ID)
    val gloves = withGear("gauntlets" to CAO_MINH_GAUNTLETS_ID)
    val full = withGear("footwear" to CAO_MINH_BOOTS_ID, "gauntlets" to CAO_MINH_GAUNTLETS_ID)
    val raw = base.characters.getValue(CAO_MINH_ID).statProfile
    assertEquals(raw, full.characters.getValue(CAO_MINH_ID).statProfile)
    val baseStats = CharacterStatEngine.effective(base, CAO_MINH_ID)
    val shoeStats = CharacterStatEngine.effective(boots, CAO_MINH_ID)
    val gloveStats = CharacterStatEngine.effective(gloves, CAO_MINH_ID)
    val fullStats = CharacterStatEngine.effective(full, CAO_MINH_ID)

    assertEquals(13, baseStats.dex)
    assertEquals(12, baseStats.agi)
    assertEquals(19, shoeStats.dex)
    assertEquals(18, shoeStats.agi)
    assertEquals(19, gloveStats.dex)
    assertEquals(18, gloveStats.agi)
    assertEquals(25, fullStats.dex)
    assertEquals(24, fullStats.agi)
    assertEquals(listOf(baseStats.str, baseStats.vit, baseStats.eva),
      listOf(fullStats.str, fullStats.vit, fullStats.eva))

    assertEquals(1, EquipmentCatalog.equippedSkillDamageMultiplier(base, CAO_MINH_ID))
    assertEquals(3, EquipmentCatalog.equippedSkillDamageMultiplier(boots, CAO_MINH_ID))
    assertEquals(3, EquipmentCatalog.equippedSkillDamageMultiplier(gloves, CAO_MINH_ID))
    assertEquals(9, EquipmentCatalog.equippedSkillDamageMultiplier(full, CAO_MINH_ID))

    val baseSkill = CharacterStatEngine.skillBaseDamage(base, CAO_MINH_ID)
    assertEquals(baseSkill * 3, CharacterStatEngine.skillBaseDamage(boots, CAO_MINH_ID))
    assertEquals(baseSkill * 3, CharacterStatEngine.skillBaseDamage(gloves, CAO_MINH_ID))
    assertEquals(baseSkill * 9, CharacterStatEngine.skillBaseDamage(full, CAO_MINH_ID))
    assertEquals(baseStats.attackPercent, fullStats.attackPercent)
    assertEquals(baseStats.skillDamagePercent * 9, fullStats.skillDamagePercent)
    assertEquals(CharacterStatEngine.weaponDamage(base, CAO_MINH_ID),
      CharacterStatEngine.weaponDamage(full, CAO_MINH_ID))
    assertEquals(
      CharacterStatCore.basicDamage(CharacterStatEngine.weaponDamage(base, CAO_MINH_ID), baseStats.str),
      CharacterStatCore.basicDamage(CharacterStatEngine.weaponDamage(full, CAO_MINH_ID), fullStats.str)
    )
  }

  @Test fun `existing current run gains two missing bound pieces without erasing equipment or inventory`() {
    val state = withGear()
    val original = state.characters.getValue(CAO_MINH_ID)
    val restored = GameStateCodec.decode(GameStateCodec.encode(state))
    val slots = restored.equipment.getValue(CAO_MINH_ID).slots
    assertEquals(CAO_MINH_SWORD_ID, slots["weapon"])
    assertEquals(CAO_MINH_ARMOR_ID, slots["armor"])
    assertEquals(CAO_MINH_BOOTS_ID, slots["footwear"])
    assertEquals(CAO_MINH_GAUNTLETS_ID, slots["gauntlets"])
    assertEquals(original.progression, restored.characters.getValue(CAO_MINH_ID).progression)
    assertEquals(original.statProfile, restored.characters.getValue(CAO_MINH_ID).statProfile)
    val repeated = GameStateCodec.decode(GameStateCodec.encode(restored))
    assertEquals(slots, repeated.equipment.getValue(CAO_MINH_ID).slots)
  }

  @Test fun `misplaced or copied accessories cannot grant another party member bonuses`() {
    val state = GameState.initial()
    val janet = state.copy(equipment = state.equipment + (JANET_ID to EquipmentState(
      JANET_ID, mapOf("footwear" to CAO_MINH_BOOTS_ID, "gauntlets" to CAO_MINH_GAUNTLETS_ID)
    )))
    assertEquals(1, EquipmentCatalog.equippedSkillDamageMultiplier(janet, JANET_ID))
    val baseline = state.copy(equipment = state.equipment + (JANET_ID to EquipmentState(JANET_ID)))
    assertEquals(CharacterStatEngine.effective(baseline, JANET_ID).dex,
      CharacterStatEngine.effective(janet, JANET_ID).dex)
    assertEquals(CharacterStatEngine.effective(baseline, JANET_ID).agi,
      CharacterStatEngine.effective(janet, JANET_ID).agi)
    val misplaced = withGear("ring" to CAO_MINH_GAUNTLETS_ID)
    assertEquals(1, EquipmentCatalog.equippedSkillDamageMultiplier(misplaced, CAO_MINH_ID))
    val valid = withGear()
    assertEquals(CharacterStatEngine.effective(valid, CAO_MINH_ID).agi,
      CharacterStatEngine.effective(misplaced, CAO_MINH_ID).agi)
    assertEquals(CharacterStatEngine.effective(valid, CAO_MINH_ID).dex,
      CharacterStatEngine.effective(misplaced, CAO_MINH_ID).dex)
  }
  @Test fun `skill gear stays nine times STR scaled damage at every sampled STR`() {
    for (str in listOf(5, 6, 15, 50, 165)) {
      val state = withGear()
      val actor = state.characters.getValue(CAO_MINH_ID)
      val base = state.copy(characters = state.characters + (CAO_MINH_ID to actor.copy(
        statProfile = actor.statProfile.copy(str = str)
      )))
      val full = base.copy(equipment = base.equipment + (CAO_MINH_ID to EquipmentState(
        CAO_MINH_ID, base.equipment.getValue(CAO_MINH_ID).slots + mapOf(
          "footwear" to CAO_MINH_BOOTS_ID, "gauntlets" to CAO_MINH_GAUNTLETS_ID
        )
      )))
      assertEquals(9, EquipmentCatalog.equippedSkillDamageMultiplier(full, CAO_MINH_ID))
      assertEquals(CharacterStatEngine.skillBaseDamage(base, CAO_MINH_ID) * 9,
        CharacterStatEngine.skillBaseDamage(full, CAO_MINH_ID))
    }
  }

}
