package com.rabpit.backroom.core
import org.junit.Assert.*
import org.junit.Test
class MadGodEquipmentTest {
  @Test fun cheatWeaponKeepsWeaponDamageButNoCharacterStatBonus() {
    val def = EquipmentCatalog.definition(MADGOD_SET_ID)!!
    assertEquals(55, def.weapon!!.dmg)
    assertFalse(def.bonuses.any())
  }

  @Test fun spawnUpdatesEquipmentStateNotInventory() {
    val base = GameState.initial()
    val (spawned, added) = MadGodCanon.spawn(base)
    assertTrue(added)
    // EquipmentState updated
    assertEquals(MADGOD_SET_ID, spawned.equipment.getValue(KAI_ID).slots["weapon"])
    // No ItemStack created in inventory
    assertFalse(spawned.inventories.getValue(KAI_ID).items.containsKey(MADGOD_SET_ID))
    // Metadata set for UI flag
    assertEquals("true", spawned.metadata["madGod.spawned"])
  }

  @Test fun equipmentProjectionExposesMadGodSetForUi() {
    val base = GameState.initial()
    val (spawned, _) = MadGodCanon.spawn(base)
    val proj = MadGodCanon.equipmentProjection(spawned)
    assertTrue(proj.has("weapon"))
    val weapon = proj.getJSONObject("weapon")
    assertEquals(MADGOD_SET_ID, weapon.getString("id"))
    assertEquals(true, weapon.getBoolean("permanent"))
  }

  @Test fun madGodEquipmentAndUiProjectionSurviveCurrentSaveLoad() {
    val base = CoreClientProjection.initialize(GameState.initial())
    val (spawned, _) = MadGodCanon.spawn(base)
    val saved = GameStateCodec.decode(GameStateCodec.encode(spawned))
    val projected = CoreClientProjection.project(
      org.json.JSONObject()
        .put("equipment", org.json.JSONObject().put("weapon", org.json.JSONObject().put("id", "forged")))
        .put("flags", org.json.JSONObject().put("madGod", org.json.JSONObject().put("spawned", false))),
      saved)
    assertEquals(MADGOD_SET_ID, projected.getJSONObject("equipment").getJSONObject("weapon").getString("id"))
    assertTrue(projected.getJSONObject("flags").getJSONObject("madGod").getBoolean("spawned"))
    assertFalse(saved.inventories.getValue(KAI_ID).items.containsKey(MADGOD_SET_ID))
  }

  @Test fun spawnIsIdempotent() {
    val base = GameState.initial()
    val (first, added1) = MadGodCanon.spawn(base)
    assertTrue(added1)
    val (_, added2) = MadGodCanon.spawn(first)
    assertFalse(added2)
  }
}
