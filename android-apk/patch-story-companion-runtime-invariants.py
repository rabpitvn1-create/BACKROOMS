from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
SPECIAL = CORE / "SpecialFollowersCanon.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# Party membership is the authoritative co-location result after a story encounter.
# If a manually assembled/test state contains a story companion in Party while its
# pre-reunion presence is still SEPARATED/MISSING, normalize that inconsistency at
# combat start. DEAD is never revived.
combat = COMBAT.read_text(encoding="utf-8")
start_anchor = '  fun start(state: GameState, entityKey: String): GameState {\n'
if 'private fun startWithStoryPartyPresence' not in combat:
    start_wrapper = '''  fun start(state: GameState, entityKey: String): GameState {
    val synchronized = state.copy(
      characters = state.characters.mapValues { (id, character) ->
        if (
          id in state.party.memberIds &&
          StoryCompanionContinuity.isStoryOwned(id) &&
          (character.presence == CharacterPresence.SEPARATED || character.presence == CharacterPresence.MISSING)
        ) character.copy(presence = CharacterPresence.ACTIVE) else character
      }
    )
    return startWithStoryPartyPresence(synchronized, entityKey)
  }

  private fun startWithStoryPartyPresence(state: GameState, entityKey: String): GameState {
'''
    combat = replace_once(combat, start_anchor, start_wrapper, "story companion party-presence combat invariant")
COMBAT.write_text(combat, encoding="utf-8")


# Fixed reunion continuity must not have a hidden slash-command bypass. Keep the
# old method signatures so downstream code compiles, but make the commands inert.
special = SPECIAL.read_text(encoding="utf-8")
shortcut_pattern = re.compile(
    r'  fun matchesPartyCheatCode\(action: String\): String\? = when \(action\.trim\(\)\) \{.*?\n  \}\n\n'
    r'  fun forceIntoParty\(state: GameState, targetId: String\): Pair<GameState, String\?> \{.*?\n  \}\n\n',
    re.S,
)
replacement = '''  @Suppress("UNUSED_PARAMETER")
  fun matchesPartyCheatCode(action: String): String? = null

  @Suppress("UNUSED_PARAMETER")
  fun forceIntoParty(state: GameState, targetId: String): Pair<GameState, String?> = state to "story_owned"

'''
special, shortcut_count = shortcut_pattern.subn(replacement, special, count=1)
if shortcut_count != 1:
    raise RuntimeError(f"disable Iris/Syvial story bypass shortcuts: expected one block, found {shortcut_count}")
if 'fun matchesPartyCheatCode(action: String): String? = null' not in special:
    raise RuntimeError("story-owned shortcut lock missing")
SPECIAL.write_text(special, encoding="utf-8")


# Align generated regressions with the new story-owned encounter contract.
special_test = TESTS / "SpecialFollowersTest.kt"
text = special_test.read_text(encoding="utf-8")
text = replace_once(
    text,
    '    assertEquals("0.25%", iris.metadata["encounterChance"])\n    assertEquals("0-6", iris.metadata["encounterLevels"])\n',
    '    assertEquals("0%", iris.metadata["encounterChance"])\n    assertEquals("STORY_ONLY", iris.metadata["encounterLevels"])\n    assertEquals(CharacterPresence.SEPARATED, iris.presence)\n    assertEquals("94", iris.metadata["fixedEncounterLevel"])\n',
    "Iris story-owned regression",
)
text = replace_once(
    text,
    '    assertEquals("0.25%", syvial.metadata["encounterChance"])\n    assertEquals("UR+", syvial.metadata["combatTier"])\n',
    '    assertEquals("UR+", syvial.metadata["combatTier"])\n    assertEquals("0%", syvial.metadata["encounterChance"])\n    assertEquals("STORY_ONLY", syvial.metadata["encounterLevels"])\n    assertEquals(CharacterPresence.SEPARATED, syvial.presence)\n    assertEquals("37", syvial.metadata["fixedEncounterLevel"])\n',
    "Syvial story-owned regression",
)
special_test.write_text(text, encoding="utf-8")

lucia_test = TESTS / "LuciaFollowerTest.kt"
text = lucia_test.read_text(encoding="utf-8")
text = replace_once(
    text,
    '    assertEquals("50%", lucia.metadata["encounterChance"])\n    assertEquals("0", lucia.metadata["encounterLevels"])\n    assertEquals("EXPLORE", lucia.metadata["encounterAction"])\n',
    '    assertEquals("0%", lucia.metadata["encounterChance"])\n    assertEquals("0", lucia.metadata["encounterLevels"])\n    assertEquals("STORY", lucia.metadata["encounterAction"])\n    assertEquals(CharacterPresence.MISSING, lucia.presence)\n    assertEquals("0", lucia.metadata["fixedEncounterLevel"])\n    assertEquals("false", lucia.metadata["requiresQuest"])\n    assertEquals("false", lucia.metadata["randomSpawn"])\n',
    "Lucia fixed Level-0 regression",
)
lucia_test.write_text(text, encoding="utf-8")

shortcut_test = TESTS / "SpecialFollowerShortcutTest.kt"
shortcut_test.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpecialFollowerShortcutTest {
  @Test fun uploadedAvatarsRemainLinkedToStoryCharacters() {
    val state = GameState.initial()
    assertEquals("avatars/Iris_avatar.jpg", state.characters.getValue(IRIS_ID).avatarRef)
    assertEquals("avatars/Syvial_avatar.jpg", state.characters.getValue(SYVIAL_ID).avatarRef)
  }

  @Test fun retiredSlashCodesCannotBypassFixedReunionLevels() {
    assertNull(SpecialFollowersCanon.matchesPartyCheatCode("/iris123"))
    assertNull(SpecialFollowersCanon.matchesPartyCheatCode("/Syv123"))
    assertNull(SpecialFollowersCanon.matchesPartyCheatCode(" /iris123 "))
  }

  @Test fun directLegacyForceHelperFailsClosedForStoryOwnedFollowers() {
    val base = GameState.initial()
    val (irisState, irisError) = SpecialFollowersCanon.forceIntoParty(base, IRIS_ID)
    assertEquals("story_owned", irisError)
    assertEquals(base.party.memberIds, irisState.party.memberIds)

    val (syvialState, syvialError) = SpecialFollowersCanon.forceIntoParty(base, SYVIAL_ID)
    assertEquals("story_owned", syvialError)
    assertEquals(base.party.memberIds, syvialState.party.memberIds)
  }
}
''', encoding="utf-8")

# Final safety audit for the fixed-character contract.
combined = COMBAT.read_text(encoding="utf-8") + "\n" + SPECIAL.read_text(encoding="utf-8")
for marker in (
    "startWithStoryPartyPresence",
    "StoryCompanionContinuity.isStoryOwned(id)",
    'fun matchesPartyCheatCode(action: String): String? = null',
    'state to "story_owned"',
):
    if marker not in combined:
        raise RuntimeError("story companion runtime invariant missing: " + marker)

print("Story companion runtime invariants finalized: Party co-location normalizes presence, fixed reunions cannot be bypassed by legacy slash shortcuts, regressions aligned.")
