package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StoryFactLedgerTest {
  private fun initial(): GameState =
    StoryCheckpointCore.seedLevelZero(CoreClientProjection.initialize(GameState.initial()))

  private fun add(kind: String, subject: String, fact: String): String =
    JSONArray().put(JSONObject().put("op", "ADD").put("kind", kind)
      .put("subject", subject).put("fact", fact)).toString()

  private fun resolve(id: String, fact: String): String =
    JSONArray().put(JSONObject().put("op", "RESOLVE")
      .put("referenceId", id).put("fact", fact)).toString()

  @Test fun luciaLosesWeaponInStoryButCombatAndInventoryRemainIndependent() {
    val baseline = LuciaCanon.ensure(initial())
    val context = "Lucia đánh mất khẩu M4A1 ở Level 0."
    val remembered = StoryFactLedger.recordTurn(baseline, emptyList(), add(
      "EVENT", "lucia.m4a1", context))
    assertEquals(baseline.characters, remembered.characters)
    assertEquals(baseline.equipment, remembered.equipment)
    assertEquals(baseline.inventories, remembered.inventories)
    assertEquals(baseline.statuses, remembered.statuses)
    assertEquals(baseline.metadata, remembered.metadata)
    assertTrue(StoryFactLedger.context(remembered).contains(context))
    assertEquals(LUCIA_M4A1_ID, remembered.equipment.getValue(LUCIA_ID).slots["weapon"])
    val levelSix = remembered.copy(world = remembered.world +
      ("levelJson" to JSONObject().put("id", "6").put("number", 6).toString()))
    val reloaded = GameStateCodec.decode(GameStateCodec.encode(levelSix))
    assertTrue(StoryFactLedger.context(reloaded).contains(context))
    assertEquals(LUCIA_M4A1_ID, reloaded.equipment.getValue(LUCIA_ID).slots["weapon"])
  }

  @Test fun structuredMilestoneMemoryIsCommittedOnlyWithAcceptedReceipt() {
    val spec = JSONObject().put("kind", "EVENT")
      .put("subject", "cao_minh.poisoned")
      .put("fact", "Cao Minh trúng độc trong câu chuyện tại Level 0")
    val plan = JSONObject().put("schemaVersion", 1).put("checkpointId", "CP-L00-01")
      .put("events", JSONArray().put(JSONObject()
        .put("id", "poison").put("turn", 1).put("kind", "STORY")
        .put("event", "Cao Minh bị trúng độc").put("outcome", "Dấu độc xuất hiện")
        .put("storyFacts", JSONArray().put(spec))).put(JSONObject()
        .put("id", "boss").put("turn", 8).put("kind", "FINAL_ENTITY")
        .put("event", "Hound xuất hiện").put("outcome", "Combat bắt đầu")
        .put("entityKey", "hound").put("hpMultiplier", 9).put("expMultiplier", 9)))
    val installed = StoryMilestonePlan.installCurrent(initial(), plan.toString())
    assertTrue(StoryFactLedger.context(installed).contains("\"open\":[]"))
    assertThrows(IllegalArgumentException::class.java) {
      StoryMilestoneRuntime.acceptTurn(installed, "[]")
    }
    val accepted = StoryMilestoneRuntime.acceptTurn(installed, "[\"poison\"]")
    assertTrue(StoryFactLedger.context(accepted).contains("cao_minh.poisoned"))
    assertEquals(installed.characters.getValue(CAO_MINH_ID).vitalState.currentHp,
      accepted.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
    assertEquals(installed.statuses, accepted.statuses)
    assertEquals(installed.inventories, accepted.inventories)
    assertEquals(installed.equipment, accepted.equipment)
    val next = CoreClientProjection.advance(accepted)
    assertEquals(emptyList<String>(), StoryMilestoneRuntime.due(next).map { it.id })
    assertTrue(StoryFactLedger.context(GameStateCodec.decode(GameStateCodec.encode(next)))
      .contains("cao_minh.poisoned"))
  }

  @Test fun openFactsSurviveMoreThanTwelveTurnsAndLevelTransitions() {
    var state = StoryFactLedger.recordTurn(initial(), emptyList(),
      add("EVENT", "lucia.m4a1", "Lucia mất M4A1 ở Level 0"))
    for (i in 1..25) {
      state = CoreClientProjection.advance(state)
      state = StoryFactLedger.recordTurn(state, emptyList(),
        add("CLUE", "clue.$i", "Manh mối $i được ghi nhận"))
    }
    val advanced = state.copy(world = state.world + ("levelJson" to JSONObject()
      .put("id", "6").put("number", 6).toString()))
    val memory = StoryFactLedger.context(GameStateCodec.decode(GameStateCodec.encode(advanced)))
    assertTrue(memory.contains("Lucia mất M4A1 ở Level 0"))
    assertTrue(memory.contains("clue.25"))
  }

  @Test fun resolutionClosesOnlyReferencedStoryFactWithoutChangingEquipment() {
    val state = LuciaCanon.ensure(initial())
    val lost = StoryFactLedger.recordTurn(state, emptyList(),
      add("EVENT", "lucia.m4a1", "Lucia mất M4A1"))
    val open = JSONObject(StoryFactLedger.context(lost)).getJSONArray("open")
    val id = open.getJSONObject(0).getString("id")
    val closed = StoryFactLedger.recordTurn(CoreClientProjection.advance(lost), emptyList(),
      resolve(id, "Lucia tìm lại M4A1 nhờ một sự kiện có thật trong lời kể"))
    val memory = JSONObject(StoryFactLedger.context(closed))
    assertEquals(0, memory.getJSONArray("open").length())
    assertTrue(memory.getJSONArray("recent").toString().contains("tìm lại M4A1"))
    assertEquals(lost.equipment, closed.equipment)
    assertEquals(lost.inventories, closed.inventories)
    assertThrows(IllegalArgumentException::class.java) {
      StoryFactLedger.recordTurn(closed, emptyList(), resolve(id, "resolve twice"))
    }
  }

  @Test fun unknownEntityAndConflictingFactsAreRejectedWithoutMutation() {
    val state = initial()
    assertThrows(IllegalArgumentException::class.java) {
      StoryFactLedger.recordTurn(state, emptyList(),
        add("ENTITY", "fake_entity", "An entity appeared"))
    }
    assertThrows(IllegalArgumentException::class.java) {
      StoryFactLedger.recordTurn(state, emptyList(),
        add("ENTITY", "hound", "Hound appeared without encounter confirmation"))
    }
    val after = StoryFactLedger.recordTurn(state, emptyList(),
      add("CLUE", "door.unknown", "Cánh cửa có hoa văn đỏ"))
    assertThrows(IllegalArgumentException::class.java) {
      StoryFactLedger.recordTurn(after, emptyList(),
        add("CLUE", "door.unknown", "Cánh cửa thực ra màu trắng"))
    }
    assertEquals(state.characters, after.characters)
    assertTrue(StoryFactLedger.context(after).contains("Cánh cửa có hoa văn đỏ"))
  }

  @Test fun entityMemoryRequiresAlreadyConfirmedEncounter() {
    val state = initial()
    val flagged = state.copy(world = state.world + ("flagsJson" to JSONObject()
      .put("entityEncounterKey", "hound").toString()))
    val remembered = StoryFactLedger.recordTurn(flagged, emptyList(),
      add("ENTITY", "hound", "Hound xuất hiện ở hành lang"))
    assertTrue(StoryFactLedger.context(remembered).contains("Hound xuất hiện"))
    assertNull(CombatRuntime.active(remembered))
    assertEquals(flagged.characters, remembered.characters)
  }
}
