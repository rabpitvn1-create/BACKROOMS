package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NamedFollowerFacadeTest {
  private class MemorySave : SaveRepository {
    var raw: String? = null
    override fun exists() = raw != null
    override fun load() = raw?.let(GameStateCodec::decode) ?: GameState.initial()
    override fun save(state: GameState) { raw = GameStateCodec.encode(state) }
    override fun clear() { raw = null }
  }
  private fun legacy(turn: Int = 1) = JSONObject().put("turn", turn)
    .put("party", JSONArray()).put("inventory", JSONArray()).put("flags", JSONObject())
  private fun prepare(core: GameCoreFacade, before: JSONObject, action: String) = JSONObject(
    core.prepareNamedFollowerTurn(before.toString(), action,
      JSONObject().put("turn", before.getInt("turn")).put("meta", false).toString()))

  @Test fun encounterCommitJoinAndReplayUseTheSameAuthoritativeSave() {
    val save = MemorySave()
    var calls = 0
    val core = GameCoreFacade(save, NoOpGamePipelineLogger) { calls++; if (calls == 1) 5000 else 7000 }
    val first = prepare(core, legacy(), "Khám phá căn phòng")
    assertTrue(first.getJSONObject("rolls").getJSONObject("nguyenQuocNamEncounter").getBoolean("success"))
    assertEquals("false", save.load().characters.getValue(NGUYEN_QUOC_NAM_ID).metadata["encountered"])
    val retry = prepare(core, legacy(), "Khám phá căn phòng")
    assertEquals(first.getJSONObject("rolls").toString(), retry.getJSONObject("rolls").toString())
    assertEquals(1, calls)
    val before = first.getJSONObject("state")
    val candidate = JSONObject(before.toString()).put("turn", 2)
    val encountered = JSONObject(core.commitNamedFollowerTurn(before.toString(), candidate.toString(), "Khám phá căn phòng", "[]"))
    assertEquals(1, encountered.getJSONArray("followerEncounters").length())
    assertEquals(0, encountered.getJSONArray("party").length())
    assertEquals("true", save.load().characters.getValue(NGUYEN_QUOC_NAM_ID).metadata["encountered"])
    val invite = "Mời Nguyễn Quốc Nam vào Party"
    val second = prepare(core, encountered, invite)
    val proposals = JSONArray().put(JSONObject().put("id", NGUYEN_QUOC_NAM_ID).put("joinConfirmed", true))
    val joined = JSONObject(core.commitNamedFollowerTurn(second.getJSONObject("state").toString(),
      JSONObject(second.getJSONObject("state").toString()).put("turn", 3).toString(), invite, proposals.toString()))
    assertEquals(NGUYEN_QUOC_NAM_ID, joined.getJSONArray("party").getJSONObject(0).getString("id"))
    assertEquals("avatars/NguyenQuocNam_avatar.webp", joined.getJSONArray("party").getJSONObject(0).getString("avatar"))
    assertTrue(save.load().party.memberIds.contains(NGUYEN_QUOC_NAM_ID))
    val persisted = save.raw
    assertThrows(IllegalStateException::class.java) {
      core.commitNamedFollowerTurn(second.getJSONObject("state").toString(), joined.toString(), invite, proposals.toString())
    }
    assertEquals(persisted, save.raw)
  }

  @Test fun failedGenerationLeavesNoCommittedEncounterAndResetDiscardsThePlan() {
    val save = MemorySave()
    val core = GameCoreFacade(save, NoOpGamePipelineLogger) { 4500 }
    prepare(core, legacy(), "Khám phá")
    val loaded = GameStateCodec.decode(save.raw!!)
    assertEquals(CharacterPresence.MISSING, loaded.characters.getValue(HUA_TIEU_LAN_ID).presence)
    assertEquals("false", loaded.characters.getValue(HUA_TIEU_LAN_ID).metadata["encountered"])
    assertFalse(loaded.turn.completedTurnIds.contains("TURN_1"))
    core.clear()
    assertFalse(save.exists())
    assertThrows(IllegalStateException::class.java) {
      core.commitNamedFollowerTurn(legacy().toString(), legacy(2).toString(), "Khám phá", "[]")
    }
  }

  @Test fun missingPlanAndClientProvidedRollsCannotCreateActorsOrFamilyMembers() {
    val save = MemorySave()
    val core = GameCoreFacade(save, NoOpGamePipelineLogger) { 9999 }
    val forged = legacy().put("party", JSONArray().put(JSONObject().put("id", NGUYEN_QUOC_NAM_ID)
      .put("name", "Nguyễn Quốc Nam").put("joinConfirmed", true)))
    val prepared = prepare(core, forged, "Tôi hỏi Nguyễn Quốc Nam")
    val candidate = JSONObject(prepared.getJSONObject("state").toString()).put("turn", 2)
      .put("flags", JSONObject().put(NGUYEN_QUOC_NAM_ID, JSONObject().put("encountered", true)))
    val output = JSONObject(core.commitNamedFollowerTurn(prepared.getJSONObject("state").toString(), candidate.toString(),
      "Tôi hỏi Nguyễn Quốc Nam", "[]"))
    assertEquals(0, output.getJSONArray("party").length())
    assertEquals(0, output.getJSONArray("followerEncounters").length())
    assertFalse(output.getJSONObject("flags").has(NGUYEN_QUOC_NAM_ID))
    assertEquals(CharacterPresence.MISSING, save.load().characters.getValue(NGUYEN_QUOC_NAM_ID).presence)
  }

  @Test fun itemRuleChangesRealEquipmentAndPreservesUnrelatedBrowserInventory() {
    val save = MemorySave()
    val initial = GameState.initial()
    val nam = initial.characters.getValue(NGUYEN_QUOC_NAM_ID)
    save.save(initial.copy(characters = initial.characters + (nam.id to nam.copy(
      presence = CharacterPresence.ACTIVE, metadata = nam.metadata + ("encountered" to "true"))),
      party = initial.party.copy(memberIds = initial.party.memberIds + nam.id)))
    val core = GameCoreFacade(save, NoOpGamePipelineLogger) { error("Item actions must not roll encounters") }
    val browser = legacy(4).put("party", JSONArray().put(JSONObject().put("id", nam.id).put("name", nam.name)))
      .put("inventory", JSONArray().put(JSONObject().put("id", "confirmed-note").put("name", "Ghi chú đã xác nhận")))
    val unequipped = JSONObject(core.processNamedFollowerRule(browser.toString(), "Nguyễn Quốc Nam tháo Hành Lang 01"))
    assertTrue(unequipped.toString(), unequipped.getBoolean("handled"))
    assertFalse(unequipped.toString(), unequipped.has("error"))
    assertFalse(save.load().equipment.getValue(nam.id).slots.containsKey("weapon"))
    val output = unequipped.getJSONObject("state")
    assertEquals(5, output.getInt("turn"))
    val transferred = JSONObject(core.processNamedFollowerRule(output.toString(), "Nguyễn Quốc Nam đưa Hành Lang 01 cho Cao Minh"))
    assertFalse(transferred.toString(), transferred.has("error"))
    val inventory = transferred.getJSONObject("state").getJSONArray("inventory")
    assertTrue((0 until inventory.length()).any { inventory.getJSONObject(it).getString("id") == "confirmed-note" })
    assertTrue((0 until inventory.length()).any { inventory.getJSONObject(it).getString("id") == "nam:corridor-pistol" })
    assertFalse(save.load().inventories.getValue(nam.id).items.containsKey("nam:corridor-pistol"))
  }
}
