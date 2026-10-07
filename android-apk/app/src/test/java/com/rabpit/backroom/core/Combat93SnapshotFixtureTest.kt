package com.rabpit.backroom.core

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Browser fixtures come from the same Core engine and codec as Android. */
class Combat93SnapshotFixtureTest {
  @Test fun exportAuthoritativeCombatPresentationStates() {
    var state = GameState.initial()
    state = state.copy(party = state.party.copy(memberIds = listOf(KAI_ID, IRIS_ID, SYVIAL_ID)),
      characters = state.characters.mapValues { (_, c) -> c.copy(statProfile = c.statProfile.copy(vit = 100)) })
    state = CharacterProgressionCore.normalize(state)
    state = state.copy(characters = state.characters.mapValues { (id, c) ->
      c.copy(vitalState = c.vitalState.copy(currentHp = CharacterStatEngine.effective(state, id).maxHp))
    })
    val folder = File("build/combat93-preview").apply { mkdirs() }
    fun export(name: String, value: GameState) {
      File(folder, "$name.json").writeText(Combat93Runtime.toJson(GameStateCodec.decode(GameStateCodec.encode(value))).toString())
    }
    fun hand(value: GameState, lethal: Boolean = false): GameState {
      val boundary = JSONObject(value.metadata.getValue("combat93.state"))
      val combat = boundary.getJSONObject("combat")
      combat.getJSONObject("diceState").put("values", JSONArray(listOf(1, 1, 2, 3, 4))).put("hasRolled", true)
      if (lethal) combat.getJSONArray("entities").getJSONObject(0).put("hp", 1)
      val next = value.copy(metadata = value.metadata + ("combat93.state" to boundary.toString()))
      return Combat93Runtime.finish(next, Combat93Runtime.revision(next))
    }
    state = Combat93Runtime.start(state, listOf("diep_minh", "hound"), 7, 0)
    export("before", state)
    state = hand(state)
    export("finalized", state)
    val resolved = Combat93Runtime.resolve(state, Combat93Runtime.revision(state))
    assertTrue(resolved.handled)
    assertEquals(2, Combat93Runtime.toJson(resolved.state)!!.getJSONArray("resolvedEntityTurns").length())
    export("after", resolved.state)
    val terminal = Combat93Runtime.start(GameState.initial(), listOf("diep_minh"), 8, 0)
    val finalHand = hand(terminal, true)
    val victory = Combat93Runtime.resolve(finalHand, Combat93Runtime.revision(finalHand)).state
    assertFalse(Combat93Runtime.active(victory))
    assertTrue(Combat93Runtime.toJson(victory)!!.getJSONArray("feedbackEvents").length() > 0)
    export("victory", victory)
  }
}
