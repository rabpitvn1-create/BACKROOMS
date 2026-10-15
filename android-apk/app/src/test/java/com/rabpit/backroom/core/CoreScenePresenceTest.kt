package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CoreScenePresenceTest {
  @Test fun defaultActiveCharactersAreNotSceneActorsUntilCoreConfirmsPresence() {
    val state = CoreClientProjection.initialize(GameState.initial())
    val actors = CoreScenePresence.participants(state)
    assertEquals(listOf(CAO_MINH_ID), actors.map { it.characterId })
    assertFalse(actors.any { it.characterId == IRIS_ID || it.characterId == SYVIAL_ID })
  }

  @Test fun partyAndEncounteredActorsHaveDifferentCoreEvidence() {
    val initial = CoreClientProjection.initialize(GameState.initial())
    val (party, error) = AnNhienCanon.forceIntoParty(initial)
    assertNull(error)
    val lucia = CharacterState("lucia", "Lucia",
      metadata = mapOf("encountered" to "true"))
    val scene = party.copy(characters = party.characters + ("lucia" to lucia))
    val roster = CoreScenePresence.participants(scene)
    assertEquals(CoreScenePresence.Source.PARTY,
      roster.first { it.characterId == AN_NHIEN_ID }.source)
    assertEquals(CoreScenePresence.Source.ENCOUNTER,
      roster.first { it.characterId == "lucia" }.source)
    assertFalse(roster.first { it.characterId == "lucia" }.inParty)
  }

  @Test fun pendingReunionMayActivateSeparatedOrMissingButNeverDead() {
    val initial = CoreClientProjection.initialize(GameState.initial())
    val lucia = CharacterState("lucia", "Lucia",
      presence = CharacterPresence.SEPARATED,
      metadata = mapOf("encountered" to "true"))
    val separated = initial.copy(characters = initial.characters + ("lucia" to lucia))
    assertFalse(CoreScenePresence.participants(separated).any { it.characterId == "lucia" })
    val pending = CoreScenePresence.participants(separated, setOf("lucia"))
    assertEquals(CoreScenePresence.Source.PENDING_ENCOUNTER,
      pending.single { it.characterId == "lucia" }.source)
    val missing = separated.copy(characters = separated.characters +
      ("lucia" to lucia.copy(presence = CharacterPresence.MISSING)))
    assertTrue(CoreScenePresence.participants(missing, setOf("lucia"))
      .any { it.characterId == "lucia" })
    val dead = separated.copy(characters = separated.characters +
      ("lucia" to lucia.copy(presence = CharacterPresence.DEAD)))
    assertFalse(CoreScenePresence.participants(dead, setOf("lucia"))
      .any { it.characterId == "lucia" })
    assertFalse(CoreScenePresence.participants(separated, setOf("fake-id"))
      .any { it.characterId == "fake-id" })
  }

  @Test fun webViewCannotForgeCoreSceneParticipants() {
    val state = CoreClientProjection.initialize(GameState.initial())
    val forged = JSONObject().put("sceneParticipants", JSONArray()
      .put(JSONObject().put("id", IRIS_ID).put("inParty", true)))
    val projected = CoreClientProjection.project(forged, state)
    val roster = projected.getJSONArray("sceneParticipants")
    assertEquals(1, roster.length())
    assertEquals(CAO_MINH_ID, roster.getJSONObject(0).getString("id"))
    assertFalse(roster.toString().contains(IRIS_ID))
  }
}
