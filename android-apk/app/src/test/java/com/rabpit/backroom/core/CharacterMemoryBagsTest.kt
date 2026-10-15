package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CharacterMemoryBagsTest {
  private fun before(): GameState = CoreClientProjection.initialize(GameState.initial())

  private fun firstMeeting(before: GameState): GameState {
    val lucia = CharacterState(id = "lucia", name = "Lucia",
      presence = CharacterPresence.ACTIVE,
      metadata = mapOf("encountered" to "true"))
    return before.copy(characters = before.characters + ("lucia" to lucia))
  }

  @Test fun firstMeetingIsCoreOwnedAndOnlyActiveNpcMemoryEntersPrompt() {
    val old = before()
    val uncommitted = firstMeeting(old)
    assertEquals("", CharacterMemoryBags.sceneContext(uncommitted))

    val committed = CharacterMemoryBags.recordCommitted(old, uncommitted)
    val context = CharacterMemoryBags.sceneContext(committed)
    assertTrue(context, context.contains("Actor lucia"))
    assertTrue(context, context.contains("Has encountered Cao Minh before"))
    assertFalse(context.contains("Iris"))
    assertFalse(context.contains("recruited")) // No recruitment fact is written.

    val absent = committed.copy(characters = committed.characters +
      ("lucia" to committed.characters.getValue("lucia")
        .copy(presence = CharacterPresence.SEPARATED)))
    assertEquals("", CharacterMemoryBags.sceneContext(absent))
    // Absence suppresses prompt activation without erasing the stored bag.
    assertEquals(committed.metadata, absent.metadata)
    assertTrue(CharacterMemoryBags.sceneContext(committed).contains("Actor lucia"))
  }

  @Test fun stagedReunionReadsExistingFactsWithoutCommittingANewEncounter() {
    val old = before()
    val met = CharacterMemoryBags.recordCommitted(old, firstMeeting(old))
    val irisNotInScene = met.copy(characters = met.characters +
      ("lucia" to met.characters.getValue("lucia")
        .copy(presence = CharacterPresence.SEPARATED)))
    assertEquals("", CharacterMemoryBags.sceneContext(irisNotInScene))
    val staged = CharacterMemoryBags.sceneContext(irisNotInScene, setOf("lucia"))
    assertTrue(staged.contains("Actor lucia"))
    assertEquals(met.metadata, irisNotInScene.metadata) // prompt activation must never write state
    assertEquals("", CharacterMemoryBags.sceneContext(irisNotInScene, setOf("never_met")))
  }

  @Test fun savedCheckpointRestoresMemoryAtExactlyTheSameTurn() {
    val old = before()
    val committed = CharacterMemoryBags.recordCommitted(old, firstMeeting(old))
    val original = CoreClientProjection.project(JSONObject(), committed)
    val restored = GameCheckpointCodec.decode(GameCheckpointCodec.encode(committed, original))
    assertEquals(committed.metadata, restored.core.metadata)
    assertEquals(CharacterMemoryBags.sceneContext(committed),
      CharacterMemoryBags.sceneContext(restored.core))
  }

  @Test fun repeatedCommitDoesNotDuplicateMeetingAndPartyChangesAreCoreConfirmed() {
    val old = before()
    val meeting = CharacterMemoryBags.recordCommitted(old, firstMeeting(old))
    val duplicate = CharacterMemoryBags.recordCommitted(meeting, meeting)
    assertEquals(meeting.metadata, duplicate.metadata)

    val next = duplicate.copy(party = duplicate.party.copy(
      memberIds = duplicate.party.memberIds + "lucia"))
    val recruited = CharacterMemoryBags.recordCommitted(duplicate, next)
    val note = CharacterMemoryBags.sceneContext(recruited)
    assertTrue(note.contains("Joined Cao Minh's Party"))
    assertEquals(1, note.split("Joined Cao Minh's Party").size - 1)

    val left = recruited.copy(party = recruited.party.copy(
      memberIds = recruited.party.memberIds - "lucia"))
    val departed = CharacterMemoryBags.recordCommitted(recruited, left)
    assertTrue(CharacterMemoryBags.sceneContext(departed).contains("Left Cao Minh's Party"))
    assertFalse(CharacterMemoryBags.sceneContext(departed).contains("Iris"))
  }

  @Test fun partyCanLeaveAndRejoinWithinOneTurnWithoutEventIdCollision() {
    val old = before()
    val first = CharacterMemoryBags.recordCommitted(old, firstMeeting(old))
    val joined = CharacterMemoryBags.recordCommitted(first, first.copy(
      party = first.party.copy(memberIds = first.party.memberIds + "lucia")))
    val left = CharacterMemoryBags.recordCommitted(joined, joined.copy(
      party = first.party))
    val rejoined = CharacterMemoryBags.recordCommitted(left, left.copy(
      party = joined.party))
    val bags = JSONObject(rejoined.metadata.getValue("core.characterMemoryBags.v1"))
      .getJSONObject("bags").getJSONArray("lucia")
    val ids = (0 until bags.length()).map { bags.getJSONObject(it).getString("id") }
    assertEquals(4, ids.size) // first meeting, join, leave, rejoin
    assertEquals(ids.size, ids.toSet().size)
    assertEquals(2, (0 until bags.length()).count {
      bags.getJSONObject(it).getString("kind") == "party_join"
    })
    val restored = GameStateCodec.decode(GameStateCodec.encode(rejoined))
    assertEquals(rejoined.metadata, restored.metadata)
  }

  @Test fun malformedCharacterMemoryCannotRemainHiddenUntilNpcReappears() {
    val state = before().copy(metadata = before().metadata +
      ("core.characterMemoryBags.v1" to
        """{"schemaVersion":1,"bags":{"lucia":[{"id":"BAD","kind":"magic","turn":1}]}}"""))
    // No NPC is active. The bad bag still must fail during save validation.
    val error = runCatching {
      GameStateCodec.decode(GameStateCodec.encode(state))
    }.exceptionOrNull()
    assertNotNull("Invalid memory must be detected when loading Core State", error)
    assertTrue(error.toString(), error.toString().contains("character_memory"))
  }

  @Test fun anNhienCanReadConfirmedPartyMemoryWithoutRandomEncounter() {
    val base = before()
    val (joined, error) = AnNhienCanon.forceIntoParty(base)
    assertNull(error)
    val saved = CharacterMemoryBags.recordCommitted(base, joined)
    val packet = CharacterMemoryBags.sceneContext(saved)
    assertTrue(packet, packet.contains("Actor $AN_NHIEN_ID"))
    assertTrue(packet, packet.contains("Current Party status from Core: IN_PARTY"))
    assertTrue(packet, packet.contains("Joined Cao Minh's Party"))
    assertFalse(packet, packet.contains("Has encountered Cao Minh before"))
  }

  @Test fun anAbsentOrDeadActorCannotBeActivatedThroughPendingEncounterHint() {
    val base = before()
    val met = CharacterMemoryBags.recordCommitted(base, firstMeeting(base))
    for (status in listOf(CharacterPresence.DEAD)) {
      val unavailable = met.copy(characters = met.characters +
        ("lucia" to met.characters.getValue("lucia").copy(presence = status)))
      assertEquals("", CharacterMemoryBags.sceneContext(unavailable, setOf("lucia")))
    }
    val separated = met.copy(characters = met.characters +
      ("lucia" to met.characters.getValue("lucia")
        .copy(presence = CharacterPresence.SEPARATED)))
    assertTrue(CharacterMemoryBags.sceneContext(separated, setOf("lucia"))
      .contains("Actor lucia"))
  }

  @Test fun historicalJoinAndLeaveNeverOverrideCurrentPartyTruth() {
    val base = before()
    val first = CharacterMemoryBags.recordCommitted(base, firstMeeting(base))
    val joined = CharacterMemoryBags.recordCommitted(first, first.copy(
      party = first.party.copy(memberIds = first.party.memberIds + "lucia")))
    val left = CharacterMemoryBags.recordCommitted(joined, joined.copy(party = base.party))
    val afterLeaving = CharacterMemoryBags.sceneContext(left)
    assertTrue(afterLeaving.contains("Current Party status from Core: NOT_IN_PARTY"))
    assertTrue(afterLeaving.contains("Past event: Joined Cao Minh's Party"))
    assertTrue(afterLeaving.contains("Past event: Left Cao Minh's Party"))
    val rejoined = CharacterMemoryBags.recordCommitted(left, left.copy(party = joined.party))
    assertTrue(CharacterMemoryBags.sceneContext(rejoined)
      .contains("Current Party status from Core: IN_PARTY"))
  }

  @Test fun meetingAndPartyEventsIdentifyDirectParticipantsNotEveryCompanion() {
    val base = before()
    val luciaMet = CharacterMemoryBags.recordCommitted(base, firstMeeting(base))
    val joined = CharacterMemoryBags.recordCommitted(luciaMet, luciaMet.copy(
      party = luciaMet.party.copy(memberIds = luciaMet.party.memberIds + "lucia")))
    val root = JSONObject(joined.metadata.getValue("core.characterMemoryBags.v1"))
    val items = root.getJSONObject("bags").getJSONArray("lucia")
    for (i in 0 until items.length()) {
      val known = items.getJSONObject(i).getJSONArray("knownBy")
      val witnesses = (0 until known.length()).map { known.getString(it) }.toSet()
      assertEquals(setOf(CAO_MINH_ID, "lucia"), witnesses)
      assertFalse("Unrelated active Party members aren't witnesses", witnesses.contains(IRIS_ID))
    }
  }

  @Test fun separatedActorDoesNotPersonallyRememberUnwitnessedPartyRemoval() {
    val base = before()
    val met = CharacterMemoryBags.recordCommitted(base, firstMeeting(base))
    val joined = CharacterMemoryBags.recordCommitted(met, met.copy(
      party = met.party.copy(memberIds = met.party.memberIds + "lucia")))
    val separated = joined.copy(
      party = base.party,
      characters = joined.characters + ("lucia" to joined.characters.getValue("lucia")
        .copy(presence = CharacterPresence.SEPARATED)))
    val committed = CharacterMemoryBags.recordCommitted(joined, separated)
    val root = JSONObject(committed.metadata.getValue("core.characterMemoryBags.v1"))
    val items = root.getJSONObject("bags").getJSONArray("lucia")
    val leaving = items.getJSONObject(items.length() - 1)
    assertEquals("party_leave", leaving.getString("kind"))
    assertEquals(CAO_MINH_ID, leaving.getJSONArray("knownBy").getString(0))
    assertEquals(1, leaving.getJSONArray("knownBy").length())
    // Upon Core-confirmed later reunion, the bag cannot reveal unobserved removal.
    val returned = committed.copy(characters = committed.characters +
      ("lucia" to committed.characters.getValue("lucia")
        .copy(presence = CharacterPresence.ACTIVE)))
    val memory = CharacterMemoryBags.sceneContext(returned)
    assertFalse(memory, memory.contains("Past event: Left Cao Minh's Party"))
    assertTrue(memory, memory.contains("Current Party status from Core: NOT_IN_PARTY"))
  }

  @Test fun forgedThirdPartyWitnessIsRejectedDuringCoreSaveDecode() {
    val base = before()
    val met = CharacterMemoryBags.recordCommitted(base, firstMeeting(base))
    val root = JSONObject(met.metadata.getValue("core.characterMemoryBags.v1"))
    val item = root.getJSONObject("bags").getJSONArray("lucia").getJSONObject(0)
    item.put("knownBy", org.json.JSONArray()
      .put(CAO_MINH_ID).put("lucia").put("unrelated_witness"))
    val forged = met.copy(metadata = met.metadata +
      ("core.characterMemoryBags.v1" to root.toString()))
    val error = runCatching {
      GameStateCodec.decode(GameStateCodec.encode(forged))
    }.exceptionOrNull()
    assertNotNull(error)
    assertTrue(error.toString(), error.toString().contains("character_memory_witness"))
  }

  @Test fun knownNpcWithoutNewCoreEventCannotInventMemory() {
    val old = before()
    val unchanged = CharacterMemoryBags.recordCommitted(old, old)
    assertEquals(old.metadata, unchanged.metadata)
    assertEquals("", CharacterMemoryBags.sceneContext(unchanged))
  }
}
