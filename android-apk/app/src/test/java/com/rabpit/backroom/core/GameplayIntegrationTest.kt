package com.rabpit.backroom.core

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GameplayIntegrationTest {
  private class MemorySave : SaveRepository {
    var raw: String? = null
    override fun exists() = raw != null
    override fun load() = raw?.let(GameStateCodec::decode) ?: GameState.initial()
    override fun save(state: GameState) { raw = GameStateCodec.encode(state) }
    override fun clear() { raw = null }
  }
  private data class Fixture(val save: MemorySave, val core: GameCoreFacade, val ui: JSONObject)
  private fun fixture(): Fixture {
    val policy = listOf("src/main/assets/knowledge", "app/src/main/assets/knowledge",
      "android-apk/app/src/main/assets/knowledge").map { File(it, "level_travel_policy.json") }
      .first { it.isFile }.readText()
    val save = MemorySave()
    val core = GameCoreFacade(save, NoOpGamePipelineLogger,
      LevelTravelEngine(LevelTravelPolicy(JSONObject(policy))) { 0.0 }, { 0 })
    val initial = core.initializeLevelGame("""{"turn":1,"party":[],"inventory":[],"flags":{}}""", true)
    val state = save.load()
    val owned = ItemStack("medical:bandage", "Băng gạc", 3,
      metadata = mapOf("consumable" to "true", "consumedOnUse" to "true"))
    save.save(state.copy(party = state.party.copy(memberIds = listOf(CAO_MINH_ID, LUC_TRAM_ID)),
      characters = state.characters + (LUC_TRAM_ID to state.characters.getValue(LUC_TRAM_ID).copy(presence = CharacterPresence.ACTIVE)),
      inventories = state.inventories + (CAO_MINH_ID to state.inventories.getValue(CAO_MINH_ID).copy(
        items = state.inventories.getValue(CAO_MINH_ID).items + (owned.itemId to owned)))))
    return Fixture(save, core, JSONObject(core.resumeLevelGame(initial, true)))
  }
  private fun request(op: String, item: String = "medical:bandage", quantity: Number = 1,
      target: String? = null) = JSONObject().put("operation", op).put("actorId", CAO_MINH_ID)
    .put("itemId", item).put("quantity", quantity).apply { target?.let { put("targetId", it) } }.toString()

  @Test fun itemUsePersistsOneUnitAndRejectsADuplicateBrowserCallback() {
    val f = fixture()
    val result = JSONObject(f.core.processItemAction(f.ui.toString(), request("USE")))
    assertTrue(result.toString(), result.getBoolean("handled"))
    val next = result.getJSONObject("state")
    assertEquals(2, next.getInt("turn"))
    assertEquals(2, f.save.load().inventories.getValue(CAO_MINH_ID).items.getValue("medical:bandage").quantity)
    val before = f.save.raw
    val duplicate = JSONObject(f.core.processItemAction(f.ui.toString(), request("USE")))
    assertFalse(duplicate.getBoolean("handled")); assertEquals(before, f.save.raw)
    val reopened = JSONObject(f.core.resumeLevelGame(next.toString(), true))
    assertEquals(2, reopened.getInt("turn"))
    assertEquals(next.getString("displayTitle"), reopened.getString("displayTitle"))
  }

  @Test fun transferDropAndEquipmentActionsRoundTripThroughTheFacade() {
    val f = fixture()
    val transferred = JSONObject(f.core.processItemAction(f.ui.toString(), request("TRANSFER", quantity = 2, target = LUC_TRAM_ID)))
    assertTrue(transferred.toString(), transferred.getBoolean("handled"))
    assertEquals(2, f.save.load().inventories.getValue(LUC_TRAM_ID).items.getValue("medical:bandage").quantity)
    var ui = transferred.getJSONObject("state")
    val dropped = JSONObject(f.core.processItemAction(ui.toString(), request("DROP")))
    assertTrue(dropped.toString(), dropped.getBoolean("handled")); ui = dropped.getJSONObject("state")
    assertFalse(f.save.load().inventories.getValue(CAO_MINH_ID).items.containsKey("medical:bandage"))
    val unequipped = JSONObject(f.core.processItemAction(ui.toString(), request("UNEQUIP", CAO_MINH_HUYET_MA_KIEM_ID)))
    assertTrue(unequipped.toString(), unequipped.getBoolean("handled")); ui = unequipped.getJSONObject("state")
    assertFalse(f.save.load().equipment.getValue(CAO_MINH_ID).slots.values.contains(CAO_MINH_HUYET_MA_KIEM_ID))
    val equipped = JSONObject(f.core.processItemAction(ui.toString(), request("EQUIP", CAO_MINH_HUYET_MA_KIEM_ID)))
    assertTrue(equipped.toString(), equipped.getBoolean("handled"))
    assertTrue(f.save.load().equipment.getValue(CAO_MINH_ID).slots.values.contains(CAO_MINH_HUYET_MA_KIEM_ID))
  }

  @Test fun invalidItemRequestsDoNotChangeSaveOrAdvanceTurn() {
    val f = fixture(); val before = f.save.raw
    listOf(request("USE", quantity = 0.5), request("USE", quantity = 4), request("PICKUP"),
      request("USE", item = "forged"), request("TRANSFER", target = "missing")).forEach {
      val rejected = JSONObject(f.core.processItemAction(f.ui.toString(), it))
      assertFalse(it, rejected.getBoolean("handled")); assertEquals(before, f.save.raw)
    }
  }

  @Test fun lockedEntityTicketStartsCombatAndDiceDamageSurviveSaveResume() {
    val f = fixture(); val action = "Khám phá"
    val prepared = JSONObject(f.core.prepareNamedFollowerTurn(f.ui.toString(), action,
      """{"turn":1,"meta":false}""", "EXPLORE"))
    val rolls = prepared.getJSONObject("rolls")
    assertTrue(rolls.getJSONObject("entityEncounter").getBoolean("success"))
    val key = rolls.getString("roamingEntityKey")
    val before = prepared.getJSONObject("state")
    val candidate = JSONObject(before.toString()).put("turn", 2)
    candidate.getJSONObject("flags").put("entityEncounterKey", "forged")
    val committed = JSONObject(f.core.commitNamedFollowerTurn(before.toString(), candidate.toString(), action, "[]"))
    assertEquals(key, committed.getJSONObject("flags").getString("entityEncounterKey"))
    val started = JSONObject(f.core.startCombatState(committed.toString(), key)).getJSONObject("state")
    var ui = JSONObject(f.core.finishLevelTurn(before.toString(), started.toString(), action))
    assertEquals(key, ui.getJSONObject("combat").getString("entityKey"))
    val attack = "Tấn công"
    ui = JSONObject(f.core.prepareCombatDice(ui.toString(), attack)).getJSONObject("state")
    ui = JSONObject(f.core.finishCombatDice(ui.toString())).getJSONObject("state")
    val roundBefore = ui.toString()
    val round = JSONObject(f.core.processCombat(roundBefore, "EXECUTE", attack))
    assertTrue(round.toString(), round.getBoolean("handled"))
    val after = round.getJSONObject("state")
    assertTrue(after.getJSONObject("combatFeedback").getJSONArray("events").length() > 0)
    val completed = JSONObject(f.core.finishLevelTurn(roundBefore, after.toString(), attack))
    val reopened = JSONObject(f.core.resumeLevelGame(completed.toString(), true))
    assertEquals(completed.getInt("turn"), reopened.getInt("turn"))
    assertEquals(CombatRuntime.active(f.save.load())?.entityHp,
      reopened.optJSONObject("combat")?.optInt("entityHp"))
  }

  @Test fun searchCannotStartANewEntityEncounter() {
    val f = fixture()
    val prepared = JSONObject(f.core.prepareNamedFollowerTurn(f.ui.toString(), "Tìm kiếm",
      """{"turn":1,"meta":false}""", "SEARCH"))
    assertFalse(prepared.getJSONObject("rolls").getJSONObject("entityEncounter").getBoolean("success"))
    assertFalse(prepared.getJSONObject("rolls").has("roamingEntityKey"))
  }
}
