package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NamedFollowerRuntimeTest {
  private fun plan(state: GameState, ticket: Int, action: String = "Khám phá căn phòng này",
    meta: Boolean = false, survivors: Boolean = true, entities: Boolean = true,
    turn: String = "TURN_1") = NamedFollowerRuntime.plan(state, turn, action, ticket, meta, survivors, entities)

  private fun proposal(id: String, consent: Boolean = true) = JSONArray().put(JSONObject()
    .put("id", id).put("joinConfirmed", consent).put("present", true))

  private fun encounter(id: String): GameState {
    val initial = GameState.initial()
    val ticket = if (id == NGUYEN_QUOC_NAM_ID) 5000 else 4500
    return NamedFollowerRuntime.apply(initial, plan(initial, ticket), JSONArray(), 0).state
  }

  @Test fun ticketWindowsHaveExactBoundariesAndNeverOverlapEntity() {
    val initial = GameState.initial()
    val expected = mapOf(3499 to null, 3500 to null, 4499 to null,
      4500 to HUA_TIEU_LAN_ID, 4999 to HUA_TIEU_LAN_ID,
      5000 to NGUYEN_QUOC_NAM_ID, 5499 to NGUYEN_QUOC_NAM_ID, 5500 to null)
    for ((ticket, id) in expected) {
      val p = plan(initial, ticket)
      assertEquals("ticket=$ticket", id, p.followerId)
      val rolls = NamedFollowerRuntime.rolls(p)
      val successes = listOf("entityEncounter", "huaTieuLanEncounter", "nguyenQuocNamEncounter")
        .count { rolls.getJSONObject(it).getBoolean("success") }
      assertTrue("disjoint ticket=$ticket", successes <= 1)
      assertEquals(ticket in 0..3499, rolls.getJSONObject("entityEncounter").getBoolean("success"))
    }
    assertEquals(5.0, NamedFollowerRuntime.rolls(plan(initial, 4500))
      .getJSONObject("huaTieuLanEncounter").getDouble("chancePercent"), 0.0)
  }

  @Test fun planningDoesNotCommitAnEncounterAndApplyDoesNotAutoJoin() {
    val initial = GameState.initial()
    val p = plan(initial, 5000)
    assertEquals("false", initial.characters.getValue(NGUYEN_QUOC_NAM_ID).metadata["encountered"])
    assertEquals(CharacterPresence.MISSING, initial.characters.getValue(NGUYEN_QUOC_NAM_ID).presence)
    val result = NamedFollowerRuntime.apply(initial, p, proposal(NGUYEN_QUOC_NAM_ID), 0)
    val nam = result.state.characters.getValue(NGUYEN_QUOC_NAM_ID)
    assertEquals("true", nam.metadata["encountered"])
    assertEquals(CharacterPresence.ACTIVE, nam.presence)
    assertFalse(NGUYEN_QUOC_NAM_ID in result.state.party.memberIds)
    assertEquals("false", initial.characters.getValue(NGUYEN_QUOC_NAM_ID).metadata["encountered"])
    assertNull(plan(result.state, 5000).followerId)
    assertFalse(NamedFollowerRuntime.rolls(plan(result.state, 5000))
      .getJSONObject("nguyenQuocNamEncounter").getBoolean("success"))
  }

  @Test fun metaCombatPendingAndDisabledNpcGatesPreventEncounters() {
    val initial = GameState.initial()
    assertNull(plan(initial, 5000, meta = true).followerId)
    assertNull(plan(initial, 5000, "Xem trạng thái và kiểm tra phòng").followerId)
    assertNull(plan(initial, 5000, "Tôi kiểm tra trạng thái Nguyễn Quốc Nam").followerId)
    val dead = initial.characters.getValue(NGUYEN_QUOC_NAM_ID).copy(presence = CharacterPresence.DEAD)
    assertNull(plan(initial.copy(characters = initial.characters + (dead.id to dead)), 5000).followerId)
    assertNull(plan(initial, 4500, "Mời Hứa Tiểu Lan vào Party").followerId)
    assertNull(plan(initial, 5000, survivors = false).followerId)
    val combat = CombatRuntime.start(initial, "hound")
    assertNull(plan(combat, 5000).followerId)
    val pending = initial.copy(turn = initial.turn.copy(
      pending = PendingTurn("TURN_1", "Khám phá", PendingTurnStatus.CREATED)))
    assertNull(plan(pending, 5000).followerId)
    assertFalse(NamedFollowerRuntime.rolls(plan(pending, 0))
      .getJSONObject("entityEncounter").getBoolean("success"))
  }

  @Test fun encountersCoverAllLevelsSublevelsAndAreasEvenWhenEntityRollIsOff() {
    for (location in listOf("Level 0", "Level 6", "Level 0.5", "Level 999", "AREA 77", "sub-level unknown")) {
      val state = GameState.initial().let { it.copy(world = it.world + ("location" to location)) }
      for ((ticket, id) in listOf(4500 to HUA_TIEU_LAN_ID, 5000 to NGUYEN_QUOC_NAM_ID)) {
        val p = plan(state, ticket, entities = false)
        assertEquals(location, id, p.followerId)
        assertFalse(NamedFollowerRuntime.rolls(p).getJSONObject("entityEncounter").getBoolean("eligible"))
      }
    }
  }

  @Test fun joiningRequiresPriorEncounterInvitationAndNpcConsentOnSeparateTurn() {
    val state = encounter(NGUYEN_QUOC_NAM_ID)
    val invite = plan(state, 7000, "Mời Nguyễn Quốc Nam vào Party", turn = "TURN_2")
    val denied = NamedFollowerRuntime.apply(state, invite, proposal(NGUYEN_QUOC_NAM_ID, false), 0)
    assertFalse(NGUYEN_QUOC_NAM_ID in denied.state.party.memberIds)
    val noInvitation = NamedFollowerRuntime.apply(state,
      plan(state, 7000, "Quan sát Nguyễn Quốc Nam", turn = "TURN_2"), proposal(NGUYEN_QUOC_NAM_ID), 0)
    assertFalse(NGUYEN_QUOC_NAM_ID in noInvitation.state.party.memberIds)
    val joined = NamedFollowerRuntime.apply(state, invite, proposal(NGUYEN_QUOC_NAM_ID), 0)
    assertTrue(NGUYEN_QUOC_NAM_ID in joined.state.party.memberIds)
    assertEquals(1, joined.state.party.memberIds.count { it == NGUYEN_QUOC_NAM_ID })
  }

  @Test fun wrongNamedInvitationCannotJoinTheOnlyPresentDifferentNpc() {
    val lan = encounter(HUA_TIEU_LAN_ID)
    val wrong = NamedFollowerRuntime.apply(lan,
      plan(lan, 7000, "Mời Nguyễn Quốc Nam vào Party", turn = "TURN_2"), proposal(HUA_TIEU_LAN_ID), 0)
    assertFalse(HUA_TIEU_LAN_ID in wrong.state.party.memberIds)
    assertFalse(NGUYEN_QUOC_NAM_ID in wrong.state.party.memberIds)
    assertNull(plan(GameState.initial(), 5000, "Mời Nguyễn Quốc Nam đi cùng").followerId)
  }

  @Test fun externalPartyCapacityIsCountedWithoutRemovingExistingCompanions() {
    val nam = encounter(NGUYEN_QUOC_NAM_ID)
    val result = NamedFollowerRuntime.apply(nam,
      plan(nam, 7000, "Mời Nguyễn Quốc Nam vào Party", turn = "TURN_2"), proposal(NGUYEN_QUOC_NAM_ID), 3)
    assertFalse(NGUYEN_QUOC_NAM_ID in result.state.party.memberIds)
    assertEquals(nam.party.memberIds, result.state.party.memberIds)
    assertTrue(result.notices.any { it.contains("Party đã đủ") })
    val room = NamedFollowerRuntime.apply(nam,
      plan(nam, 7000, "Mời Nguyễn Quốc Nam vào Party", turn = "TURN_2"), proposal(NGUYEN_QUOC_NAM_ID), 2)
    assertTrue(NGUYEN_QUOC_NAM_ID in room.state.party.memberIds)
  }

  @Test fun absentUnencounteredAndProtectedRelativesCannotJoinOrForceEncounter() {
    val initial = GameState.initial()
    val invite = plan(initial, 7000, "Mời Nguyễn Quốc Nam vào Party")
    val denied = NamedFollowerRuntime.apply(initial, invite, proposal(NGUYEN_QUOC_NAM_ID), 0)
    assertFalse(NGUYEN_QUOC_NAM_ID in denied.state.party.memberIds)
    assertEquals("false", denied.state.characters.getValue(NGUYEN_QUOC_NAM_ID).metadata["encountered"])
    val present = encounter(NGUYEN_QUOC_NAM_ID)
    val actor = present.characters.getValue(NGUYEN_QUOC_NAM_ID)
    val absent = present.copy(characters = present.characters + (actor.id to actor.copy(presence = CharacterPresence.SEPARATED)))
    val absentResult = NamedFollowerRuntime.apply(absent,
      plan(absent, 7000, "Mời Nguyễn Quốc Nam vào Party", turn = "TURN_2"), proposal(NGUYEN_QUOC_NAM_ID), 0)
    assertFalse(NGUYEN_QUOC_NAM_ID in absentResult.state.party.memberIds)
    val legacy = JSONObject().put("party", JSONArray().apply {
      put(JSONObject().put("id", "nguyen-hong-lan").put("name", "Nguyễn Hồng Lan"))
      put(JSONObject().put("id", "nguyen-thuy-duong").put("name", "Nguyễn Thùy Dương"))
      put(JSONObject().put("id", "hua-bon-hoa").put("name", "Hứa Bổn Hòa"))
      put(JSONObject().put("id", "anh-ty").put("name", "Anh Tý"))
      put(JSONObject().put("id", NGUYEN_QUOC_NAM_ID).put("name", "Nguyễn Quốc Nam"))
    })
    assertEquals(0, NamedFollowerRuntime.initialLegacy(legacy).getJSONArray("party").length())
    assertEquals(0, NamedFollowerRuntime.project(legacy, initial).getJSONArray("party").length())
  }

  @Test fun refusingTieuLanSeparatesHerAndSheDoesNotFollowOrRespawn() {
    val state = encounter(HUA_TIEU_LAN_ID)
    val refused = NamedFollowerRuntime.apply(state,
      plan(state, 7000, "Từ chối Hứa Tiểu Lan, đừng theo tôi", turn = "TURN_2"), proposal(HUA_TIEU_LAN_ID), 0).state
    assertEquals(CharacterPresence.SEPARATED, refused.characters.getValue(HUA_TIEU_LAN_ID).presence)
    assertFalse(HUA_TIEU_LAN_ID in refused.party.memberIds)
    assertNull(plan(refused, 4500).followerId)
    val projected = NamedFollowerRuntime.project(JSONObject(), refused)
    assertEquals(0, projected.getJSONArray("followerEncounters").length())
  }

  @Test fun projectionPreservesUnrelatedPartyInventoryAndCoreDetails() {
    val state = encounter(NGUYEN_QUOC_NAM_ID)
    val joined = NamedFollowerRuntime.apply(state,
      plan(state, 7000, "Mời Nguyễn Quốc Nam vào Party", turn = "TURN_2"), proposal(NGUYEN_QUOC_NAM_ID), 1).state
    val stranger = JSONObject().put("id", "stranger").put("name", "Người sống sót")
      .put("trust", "confirmed").put("custom", JSONObject().put("note", "keep"))
    val leader = JSONObject().put("id", CAO_MINH_ID).put("name", "Cao Minh").put("currentHp", 13)
    val old = JSONObject().put("party", JSONArray().put(stranger))
      .put("inventory", JSONArray().put(JSONObject().put("id", "note").put("quantity", 2)))
      .put("partyDetails", JSONObject().put("members", JSONArray().put(leader).put(stranger)))
      .put("flags", JSONObject().put("confirmedDoor", "west"))
    val out = NamedFollowerRuntime.project(old, joined)
    assertEquals(old.getJSONArray("inventory").toString(), out.getJSONArray("inventory").toString())
    assertEquals(stranger.toString(), out.getJSONArray("party").getJSONObject(0).toString())
    assertEquals(2, out.getJSONArray("party").length())
    assertEquals(13, out.getJSONObject("partyDetails").getJSONArray("members").getJSONObject(0).getInt("currentHp"))
    assertEquals("west", out.getJSONObject("flags").getString("confirmedDoor"))
    assertEquals(1, old.getJSONArray("party").length())
  }
}
