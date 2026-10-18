package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Gameplay notes are optional information, not mandatory keys to Level transitions. */
class Level0FieldStudyTest {
  private fun initial(seed: Long = 412L) =
    WorldGraphRuntime.initialize(CoreClientProjection.initialize(GameState.initial()), seed)
  private fun progress(state: GameState) = MiniMapRuntime.progress(state)
  private fun rooms(state: GameState) =
    MiniMapScenario.objects(progress(state).getJSONObject("worldGraph").getJSONArray("rooms"))
  private fun available(state: GameState) = MiniMapScenario.objects(MiniMapRuntime.actions(state))

  private fun doAction(initial: GameState, target: String): Pair<GameState, JSONObject> {
    val choice = available(initial).first { it.getString("targetId") == target }
    val command = JSONObject(choice.toString()).apply { remove("label") }
    val stage = MiniMapRuntime.stage(initial, command)
    return initial.copy(world = initial.world +
      (MiniMapRuntime.STATE to stage.progress.toString())) to stage.event
  }

  /** The route oracle deliberately uses only ordinary, ungated physical corridors. */
  private fun go(initial: GameState, destination: String): GameState {
    val adjacency = rooms(initial).associate {
      it.getString("id") to MiniMapScenario.strings(it.getJSONArray("adjacentRoomIds"))
    }
    val start = progress(initial).getString("roomId")
    val known = mutableMapOf(start to emptyList<String>())
    val queue = java.util.ArrayDeque<String>(); queue.add(start)
    while (destination !in known && queue.isNotEmpty()) {
      val current = queue.removeFirst()
      adjacency.getValue(current).forEach { id ->
        if (id !in known) { known[id] = known.getValue(current) + id; queue.add(id) }
      }
    }
    var state = initial
    known.getValue(destination).forEach { state = doAction(state, "room:$it").first }
    return state
  }

  private fun inEnvironment(state: GameState, id: String): GameState {
    val room = rooms(state).first { it.getString("environmentId") == id }
    return go(state, room.getString("id"))
  }

  @Test fun eachOf29SubzonesSupportsPersistentCanonGroundedInvestigation() {
    for (seed in listOf(0L, 13L, Long.MIN_VALUE)) {
      var state = initial(seed)
      val authored = Level0Environments.all.filter { it.parent != null }
      assertEquals(29, Level0WorldDefinition.fieldStudies.size)
      assertEquals(authored.map { it.id }.toSet(),
        Level0WorldDefinition.fieldStudies.map { it.environmentId }.toSet())
      for (location in authored) {
        state = inEnvironment(state, location.id)
        val current = progress(state).getString("roomId")
        val choice = available(state).single { it.getString("targetId") == "study" }
        assertEquals("SEARCH", choice.getString("kind"))
        assertFalse(choice.getString("label").contains("wet-ceiling"))
        val (next, event) = doAction(state, "study")
        state = next
        val notes = MiniMapScenario.objects(progress(state).getJSONArray("records")).filter {
          it.optString("kind") == "FIELD_NOTE" && it.optString("id") == current
        }
        assertEquals(1, notes.size)
        assertEquals(location.canon, notes.single().getString("text"))
        assertEquals(location.id, event.getJSONObject("room").getString("environmentId"))
        assertTrue(event.getJSONArray("observations").length() <= 6)
        assertTrue(MiniMapScenario.objects(event.getJSONArray("observations")).all {
          it.getString("id") == current
        })
        val count = progress(state).getJSONArray("records").length()
        state = doAction(state, "study").first
        assertEquals(count, progress(state).getJSONArray("records").length())
      }
      val visitedNotes = MiniMapScenario.objects(progress(state).getJSONArray("records"))
        .filter { it.optString("kind") == "FIELD_NOTE" }
      assertEquals(29, visitedNotes.size)
      assertFalse(MiniMapRuntime.complete(state))
      val loaded = GameStateCodec.decode(GameStateCodec.encode(state))
      assertEquals(progress(state).toString(), progress(loaded).toString())
    }
  }

  @Test fun comparisonsOnlyReferenceStudiedSameZoneRoomsAndPersistOnBacktracking() {
    var state = initial()
    state = inEnvironment(state, "phong_moc")
    val firstId = progress(state).getString("roomId")
    state = doAction(state, "study").first
    state = inEnvironment(state, "hanh_lang_dai")
    state = doAction(state, "study").first
    val secondId = progress(state).getString("roomId")
    assertTrue(available(state).any { it.getString("targetId") == "compare:$firstId" })
    state = doAction(state, "compare:$firstId").first
    val before = MiniMapScenario.objects(progress(state).getJSONArray("records"))
      .count { it.optString("kind") == "COMPARISON" }
    state = doAction(state, "compare:$firstId").first
    assertEquals(before, MiniMapScenario.objects(progress(state).getJSONArray("records"))
      .count { it.optString("kind") == "COMPARISON" })
    assertEquals(1, before)
    state = inEnvironment(state, "tram_dien")
    state = doAction(state, "study").first
    assertFalse(available(state).any { it.getString("targetId") == "compare:$firstId" })
    val legal = available(state).first { it.getString("targetId") == "study" }
    val forged = JSONObject(legal.toString()).apply {
      remove("label"); put("kind", "INTERACT"); put("targetId", "compare:$firstId")
    }
    assertThrows(Exception::class.java) { MiniMapRuntime.stage(state, forged) }
    val loaded = GameStateCodec.decode(GameStateCodec.encode(state))
    val comparisons = MiniMapScenario.objects(progress(loaded).getJSONArray("records"))
      .filter { it.optString("kind") == "COMPARISON" }
    assertEquals(secondId, comparisons.single().getString("id"))
    assertEquals(firstId, comparisons.single().getString("otherRoomId"))
  }

  @Test fun mutatedSeededTopologyAndForgedFieldEvidenceAreRejected() {
    val state = inEnvironment(initial(), "phong_moc")
    val noteState = doAction(state, "study").first
    val raw = JSONObject(GameStateCodec.encode(noteState))
    val world = raw.getJSONObject("world")
    val saved = JSONObject(world.getString(MiniMapRuntime.STATE))
    val graph = saved.getJSONObject("worldGraph")
    val catalog = MiniMapScenario.objects(graph.getJSONArray("rooms"))
    val a = catalog.first { it.getString("environmentId") == "phong_moc" }
    val b = catalog.first { it.getString("environmentId") == "goc_khuat" }
    a.put("environmentId", "goc_khuat"); b.put("environmentId", "phong_moc")
    assertEquals("world_seeded_topology",
      assertThrows(IllegalArgumentException::class.java) { WorldGraphRuntime.validate(graph) }.message)
    saved.put("worldGraph", Level0WorldDefinition.generate(412L))
    saved.getJSONArray("records").getJSONObject(0).put("text", "Dữ liệu giả do save tự gắn.")
    world.put(MiniMapRuntime.STATE, saved.toString())
    assertEquals("world_saved_field_notes",
      assertThrows(IllegalArgumentException::class.java) { GameStateCodec.decode(raw) }.message)
  }
}
