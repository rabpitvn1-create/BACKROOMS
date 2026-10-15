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

  @Test fun knownNpcWithoutNewCoreEventCannotInventMemory() {
    val old = before()
    val unchanged = CharacterMemoryBags.recordCommitted(old, old)
    assertEquals(old.metadata, unchanged.metadata)
    assertEquals("", CharacterMemoryBags.sceneContext(unchanged))
  }
}
