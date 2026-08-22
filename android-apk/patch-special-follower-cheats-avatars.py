from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
AVATARS = ROOT / "app/src/main/assets/avatars"

IRIS_AVATAR = "avatars/Iris_avatar.jpg"
SYVIAL_AVATAR = "avatars/Syvial_avatar.jpg"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


# The assets were uploaded directly to the APK asset tree. Fail the build rather than silently
# shipping follower records that point at missing portraits.
for filename in ("Iris_avatar.jpg", "Syvial_avatar.jpg"):
    path = AVATARS / filename
    if not path.is_file() or path.stat().st_size <= 0:
        raise RuntimeError(f"Missing uploaded follower avatar: {path}")


# 1) Make both portraits authoritative character avatarRefs and define exact slash shortcuts.
canon_path = CORE / "SpecialFollowersCanon.kt"
canon = canon_path.read_text(encoding="utf-8")
canon = replace_once(
    canon,
    '  const val ENCOUNTER_CHANCE = "0.25%"\n  const val ENCOUNTER_LEVELS = "0-6"\n',
    '  const val ENCOUNTER_CHANCE = "0.25%"\n  const val ENCOUNTER_LEVELS = "0-6"\n  const val IRIS_AVATAR_REF = "avatars/Iris_avatar.jpg"\n  const val SYVIAL_AVATAR_REF = "avatars/Syvial_avatar.jpg"\n  const val IRIS_PARTY_CHEAT_CODE = "/iris123"\n  const val SYVIAL_PARTY_CHEAT_CODE = "/Syv123"\n',
    "special follower avatar and shortcut constants",
)
canon = replace_once(
    canon,
    '''    return base.copy(\n      id = IRIS_ID,\n      name = "Iris",\n      inventoryId = IRIS_ID,\n''',
    '''    return base.copy(\n      id = IRIS_ID,\n      name = "Iris",\n      avatarRef = IRIS_AVATAR_REF,\n      inventoryId = IRIS_ID,\n''',
    "Iris avatarRef",
)
canon = replace_once(
    canon,
    '''    return base.copy(\n      id = SYVIAL_ID,\n      name = "Syvial",\n      inventoryId = SYVIAL_ID,\n''',
    '''    return base.copy(\n      id = SYVIAL_ID,\n      name = "Syvial",\n      avatarRef = SYVIAL_AVATAR_REF,\n      inventoryId = SYVIAL_ID,\n''',
    "Syvial avatarRef",
)

shortcut_helpers = '''  fun matchesPartyCheatCode(action: String): String? = when (action.trim()) {\n    IRIS_PARTY_CHEAT_CODE -> IRIS_ID\n    SYVIAL_PARTY_CHEAT_CODE -> SYVIAL_ID\n    else -> null\n  }\n\n  fun forceIntoParty(state: GameState, targetId: String): Pair<GameState, String?> {\n    if (targetId != IRIS_ID && targetId != SYVIAL_ID) return state to "unknown_follower"\n    val ensured = ensure(state)\n    if (targetId in ensured.party.memberIds) return ensured to null\n    if (ensured.party.memberIds.size >= ensured.party.maxMembers) return ensured to "party_full"\n    return ensured.copy(\n      party = ensured.party.copy(memberIds = ensured.party.memberIds + targetId)\n    ) to null\n  }\n\n'''
ensure_anchor = '  fun ensure(state: GameState): GameState {\n'
if shortcut_helpers not in canon:
    if ensure_anchor not in canon:
        raise RuntimeError("SpecialFollowersCanon ensure anchor missing")
    canon = canon.replace(ensure_anchor, shortcut_helpers + ensure_anchor, 1)

canon_path.write_text(canon, encoding="utf-8")


# 2) Intercept /iris123 and /Syv123 beside /annhien1234, before any intent classification,
# AI fallback, dice, turn advance or subjective-time advance.
facade_path = CORE / "GameCoreFacade.kt"
facade = facade_path.read_text(encoding="utf-8")
facade = replace_once(
    facade,
    '''    if (AnNhienCanon.matchesPartyCheatCode(action)) return applyAnNhienPartyCheat(legacy, state)\n    val turnId = nextTurnId(legacy, state)\n''',
    '''    if (AnNhienCanon.matchesPartyCheatCode(action)) return applyAnNhienPartyCheat(legacy, state)\n    SpecialFollowersCanon.matchesPartyCheatCode(action)?.let { targetId ->\n      return applySpecialFollowerPartyCheat(legacy, state, targetId)\n    }\n    val turnId = nextTurnId(legacy, state)\n''',
    "special follower shortcut intercept",
)

handler = '''  private fun applySpecialFollowerPartyCheat(legacy: JSONObject, state: GameState, targetId: String): String {\n    val ensured = SpecialFollowersCanon.ensure(state)\n    val displayName = ensured.characters[targetId]?.name ?: targetId\n    val alreadyFollowing = targetId in ensured.party.memberIds\n    val (updated, error) = SpecialFollowersCanon.forceIntoParty(ensured, targetId)\n    val result = syncLegacy(legacy, updated, incrementTurn = false)\n    val reply = when {\n      error == "party_full" -> "Party đã đủ tối đa bốn thành viên; không thể thêm $displayName nếu chưa có chỗ trống."\n      alreadyFollowing -> "$displayName đã ở trong Party."\n      else -> "$displayName đã được thêm vào Party."\n    }\n\n    if (error == null) repository.save(updated)\n    val log = result.optJSONArray("log") ?: JSONArray().also { result.put("log", it) }\n    log.put(JSONObject().put("role", "gm").put("text", reply))\n    logger.log(PipelineLogEvent(\n      if (error == null) "CHEAT_COMMIT" else "CHEAT_REJECT",\n      details = mapOf(\n        "command" to if (targetId == IRIS_ID) "iris_party" else "syvial_party",\n        "reason" to (error ?: "committed")\n      )\n    ))\n    return response(\n      handled = true,\n      state = result,\n      error = error,\n      reason = if (error == null) "cheat_committed" else "cheat_rejected",\n      reply = reply\n    )\n  }\n\n'''
current_state_anchor = '  fun currentCoreState(): String = GameStateCodec.encode(repository.load())\n'
if handler not in facade:
    if current_state_anchor not in facade:
        raise RuntimeError("GameCoreFacade currentCoreState anchor missing")
    facade = facade.replace(current_state_anchor, handler + current_state_anchor, 1)

facade_path.write_text(facade, encoding="utf-8")


# 3) Regression coverage: uploaded portrait links, exact commands, idempotence and Party cap.
test_path = TESTS / "SpecialFollowerShortcutTest.kt"
test_path.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialFollowerShortcutTest {
  @Test fun uploadedAvatarsAreLinkedToFollowerCharacters() {
    val state = GameState.initial()
    assertEquals("avatars/Iris_avatar.jpg", state.characters.getValue(IRIS_ID).avatarRef)
    assertEquals("avatars/Syvial_avatar.jpg", state.characters.getValue(SYVIAL_ID).avatarRef)
  }

  @Test fun exactSlashCodesResolveToTheRequestedFollowers() {
    assertEquals(IRIS_ID, SpecialFollowersCanon.matchesPartyCheatCode(" /iris123 "))
    assertEquals(SYVIAL_ID, SpecialFollowersCanon.matchesPartyCheatCode(" /Syv123 "))
    assertNull(SpecialFollowersCanon.matchesPartyCheatCode("/syv123"))
    assertNull(SpecialFollowersCanon.matchesPartyCheatCode("/iris12"))
  }

  @Test fun slashCodesAddFollowersImmediatelyAndIdempotently() {
    val base = GameState.initial()
    val (withIris, irisError) = SpecialFollowersCanon.forceIntoParty(base, IRIS_ID)
    assertNull(irisError)
    assertTrue(IRIS_ID in withIris.party.memberIds)

    val (withBoth, syvialError) = SpecialFollowersCanon.forceIntoParty(withIris, SYVIAL_ID)
    assertNull(syvialError)
    assertTrue(SYVIAL_ID in withBoth.party.memberIds)

    val (again, againError) = SpecialFollowersCanon.forceIntoParty(withBoth, SYVIAL_ID)
    assertNull(againError)
    assertEquals(withBoth.party.memberIds, again.party.memberIds)
  }

  @Test fun shortcutsNeverEvictAnExistingMemberWhenPartyIsFull() {
    val base = GameState.initial()
    val full = base.copy(
      characters = base.characters + mapOf(
        "a" to CharacterState("a", "A"),
        "b" to CharacterState("b", "B"),
        "c" to CharacterState("c", "C")
      ),
      party = PartyState(KAI_ID, listOf(KAI_ID, "a", "b", "c"), 4)
    )
    val (unchanged, error) = SpecialFollowersCanon.forceIntoParty(full, IRIS_ID)
    assertEquals("party_full", error)
    assertFalse(IRIS_ID in unchanged.party.memberIds)
    assertEquals(listOf(KAI_ID, "a", "b", "c"), unchanged.party.memberIds)
  }
}
''', encoding="utf-8")


required = [
    'IRIS_AVATAR_REF = "avatars/Iris_avatar.jpg"',
    'SYVIAL_AVATAR_REF = "avatars/Syvial_avatar.jpg"',
    'IRIS_PARTY_CHEAT_CODE = "/iris123"',
    'SYVIAL_PARTY_CHEAT_CODE = "/Syv123"',
    'avatarRef = IRIS_AVATAR_REF',
    'avatarRef = SYVIAL_AVATAR_REF',
    'applySpecialFollowerPartyCheat(legacy, state, targetId)',
    'incrementTurn = false',
]
combined = canon + facade + test_path.read_text(encoding="utf-8")
for marker in required:
    if marker not in combined:
        raise RuntimeError(f"Special follower shortcut/avatar contract missing: {marker}")

print("Iris/Syvial avatars linked; /iris123 and /Syv123 add followers instantly without AI, dice or turn/time advance.")
