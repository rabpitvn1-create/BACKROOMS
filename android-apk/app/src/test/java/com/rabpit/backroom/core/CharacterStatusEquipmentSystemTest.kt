package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CharacterStatusEquipmentSystemTest {
  private fun state() = CharacterEquipmentSystem.seedFresh(GameState.initial())
  private fun cmd(op: ItemCommand.Operation, item: String, slot: String? = null) = ItemCommand("T", null, KAI_ID, source = CommandSource.UI, operation = op, itemId = item, itemName = EquipmentCatalog.definition(item)?.name ?: item, slot = slot)

  @Test fun noEquipmentMeans100Over100() {
    val raw = GameState.initial()
    val kai = raw.characters.getValue(KAI_ID)
    val stripped = raw.copy(equipment = raw.equipment + (KAI_ID to EquipmentState(KAI_ID)), characters = raw.characters + (KAI_ID to kai.copy(vitalState = CharacterVitalState(100))))
    assertEquals(100, CharacterStatEngine.effective(stripped, KAI_ID).maxHp)
    assertEquals(100, stripped.characters.getValue(KAI_ID).vitalState.currentHp)
  }

  @Test fun blackbloodAdds25MaxHpAndMissingHpIsPreservedBothWays() {
    var s = state()
    val kai = s.characters.getValue(KAI_ID)
    s = s.copy(equipment = s.equipment + (KAI_ID to EquipmentState(KAI_ID)), characters = s.characters + (KAI_ID to kai.copy(vitalState = CharacterVitalState(70))))
    val equip = EquipmentEngine.equip(s, cmd(ItemCommand.Operation.EQUIP, KAI_BLACKBLOOD_ARMOR_ID, "armor"))
    assertTrue(equip.applied); assertEquals(125, CharacterStatEngine.effective(equip.state, KAI_ID).maxHp); assertEquals(95, equip.state.characters.getValue(KAI_ID).vitalState.currentHp)
    val unequip = EquipmentEngine.unequip(equip.state, cmd(ItemCommand.Operation.UNEQUIP, KAI_BLACKBLOOD_ARMOR_ID, "armor"))
    assertTrue(unequip.applied); assertEquals(100, CharacterStatEngine.effective(unequip.state, KAI_ID).maxHp); assertEquals(70, unequip.state.characters.getValue(KAI_ID).vitalState.currentHp)
  }

  @Test fun bonusesApplyOnceAndSaveLoadDoesNotMultiply() {
    val s = state(); val e = CharacterStatEngine.effective(s, KAI_ID)
    assertEquals(140, e.maxHp); assertEquals(107, e.str); assertEquals(109, e.df); assertEquals(112, e.agi); assertEquals(109, e.crit)
    val loaded = GameStateCodec.decode(GameStateCodec.encode(s)); val e2 = CharacterStatEngine.effective(loaded, KAI_ID)
    assertEquals(e, e2)
  }

  @Test fun inventoryOwnsSameItemReferencedByEquipment() {
    val s = state(); val id = s.equipment.getValue(KAI_ID).slots.getValue("armor")
    assertSame(s.inventories.getValue(KAI_ID).items.getValue(id), s.inventories.getValue(KAI_ID).items[id])
    assertEquals(KAI_BLACKBLOOD_ARMOR_ID, id)
  }

  @Test fun energyAndRegenProfilesAreCorrect() {
    val s = state()
    listOf(KAI_ID, IRIS_ID, SYVIAL_ID).forEach { id -> assertEquals(EnergyMode.INFINITE, s.characters.getValue(id).statProfile.energy.mode); assertEquals(4, s.characters.getValue(id).statProfile.regen.amountPerCompletedTurn) }
    assertEquals(EnergyMode.NOT_APPLICABLE, s.characters.getValue(AN_NHIEN_ID).statProfile.energy.mode); assertFalse(s.characters.getValue(AN_NHIEN_ID).statProfile.regen.enabled)
  }

  @Test fun regenRunsExactlyOnceAndZeroHpCannotBeRescued() {
    var s = state(); s = CharacterStatEngine.setCurrentHp(s, KAI_ID, 50)
    val once = CharacterStatEngine.applyCompletedTurnRegen(s, "TURN_X"); assertEquals(54, once.characters.getValue(KAI_ID).vitalState.currentHp)
    val twice = CharacterStatEngine.applyCompletedTurnRegen(once, "TURN_X"); assertEquals(54, twice.characters.getValue(KAI_ID).vitalState.currentHp)
    val zero = CharacterStatEngine.setCurrentHp(s, KAI_ID, 0); val after = CharacterStatEngine.applyCompletedTurnRegen(zero, "TURN_Z")
    assertEquals(0, after.characters.getValue(KAI_ID).vitalState.currentHp); assertEquals(CharacterCondition.DEFEATED, after.characters.getValue(KAI_ID).vitalState.condition)
  }

  @Test fun omnivaultMayHaveZeroCombatStatsWithAbilities() {
    val d = EquipmentCatalog.definition(KAI_OMNIVAULT_RING_ID)!!; assertFalse(d.bonuses.any()); assertTrue(d.abilities.isNotEmpty())
  }

  @Test fun reconFrameHasNoForbiddenWeapons() {
    val text = EquipmentCatalog.definition(IRIS_RECON_FRAME_ID)!!.abilities.joinToString(" ") { it.name + " " + it.description }.lowercase()
    assertFalse(text.contains("drone")); assertFalse(text.contains("launcher")); assertFalse(text.contains("missile")); assertFalse(text.contains("shoulder cannon"))
  }

  @Test fun irisInfiniteEnergyDoesNotCreateInfiniteWeaponDamageOrRpm() {
    val d = EquipmentCatalog.definition(IRIS_IVORY_EBONY_SET_ID)!!; assertEquals(24, d.weapon!!.dmg); assertNull(d.weapon!!.rpmCapability)
  }

  @Test fun godKillerRemainsMechanicalGreatsword() {
    val d = EquipmentCatalog.definition(SYVIAL_GODKILLER_ID)!!; assertEquals("MECHANICAL GREATSWORD", d.type); assertFalse(d.restrictions.joinToString().lowercase().contains("gunblade allowed"))
  }

  @Test fun projectionAfterReloadEqualsBasePlusEquippedItems() {
    val s = GameStateCodec.decode(GameStateCodec.encode(state())); val p = CharacterDetailProjector.projectCharacter(s, KAI_ID)!!
    assertEquals(107, p.str.effective); assertEquals(25, p.str.equipment); assertEquals(82, p.str.base)
    assertEquals(CharacterStatEngine.effective(s, KAI_ID).maxHp, p.maxHp)
  }

  @Test fun futureCharacterGetsSafeFallback() {
    val x = CharacterState("future", "Future"); assertEquals(100, x.statProfile.baseMaxHp); assertEquals(10, x.statProfile.str); assertEquals(EnergyMode.NOT_APPLICABLE, x.statProfile.energy.mode)
  }

  @Test fun itemDetailDataOrdersStatsAbilitiesRestrictionsAndSharedUiExists() {
    val s = state(); val p = CharacterDetailProjector.projectCharacter(s, KAI_ID)!!; val armor = p.inventoryDetails.first { it.id == KAI_BLACKBLOOD_ARMOR_ID }
    assertEquals(25, armor.bonuses.hp); assertTrue(armor.abilities.isNotEmpty()); assertTrue(armor.equipped)
    val html = File("src/main/assets/index.html").readText()
    assertTrue(html.contains("equipmentDetailModal")); assertTrue(html.contains("SPECIAL ABILITIES")); assertTrue(html.contains("CANON / RESTRICTIONS")); assertTrue(html.contains("data-item-id"))
  }

  @Test fun ivoryEbonyIsOneDualWeaponSetAndGodKillerHas38Damage() {
    val s = state(); assertEquals(IRIS_IVORY_EBONY_SET_ID, s.equipment.getValue(IRIS_ID).slots["weapon"]); assertEquals(24, EquipmentCatalog.definition(IRIS_IVORY_EBONY_SET_ID)!!.weapon!!.dmg); assertEquals(38, EquipmentCatalog.definition(SYVIAL_GODKILLER_ID)!!.weapon!!.dmg)
  }

  @Test fun derivedCacheIsRecalculatedInsteadOfTrusted() {
    val s = state(); val kai = s.characters.getValue(KAI_ID); val corrupt = s.copy(characters = s.characters + (KAI_ID to kai.copy(metadata = kai.metadata + mapOf("derived.equipmentHp" to "9999", "derived.effectiveMaxHp" to "9999"))))
    val fixed = CharacterEquipmentSystem.normalize(corrupt); assertEquals("40", fixed.characters.getValue(KAI_ID).metadata["derived.equipmentHp"]); assertEquals("140", fixed.characters.getValue(KAI_ID).metadata["derived.effectiveMaxHp"])
  }
}
