from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


# 1) Keep the cheat code and authoritative mutation beside An Nhien's canonical runtime definition.
canon_path = CORE / "AnNhienCanon.kt"
canon = canon_path.read_text(encoding="utf-8")
canon = replace_once(
    canon,
    '  const val FOOTWEAR_NAME = "Đôi dép màu hồng có hình Baby Tree"\n',
    '  const val FOOTWEAR_NAME = "Đôi dép màu hồng có hình Baby Tree"\n  const val PARTY_CHEAT_CODE = "/annhien1234"\n',
    "An Nhien cheat constant"
)
canon = replace_once(
    canon,
    '  fun isFollowing(state: GameState): Boolean = AN_NHIEN_ID in state.party.memberIds\n\n',
    '''  fun isFollowing(state: GameState): Boolean = AN_NHIEN_ID in state.party.memberIds\n\n  fun matchesPartyCheatCode(action: String): Boolean = action.trim() == PARTY_CHEAT_CODE\n\n  fun forceIntoParty(state: GameState): Pair<GameState, String?> {\n    val ensured = ensure(state)\n    if (AN_NHIEN_ID in ensured.party.memberIds) return ensured to null\n    if (ensured.party.memberIds.size >= ensured.party.maxMembers) return ensured to "party_full"\n    return ensured.copy(\n      party = ensured.party.copy(memberIds = ensured.party.memberIds + AN_NHIEN_ID)\n    ) to null\n  }\n\n''',
    "An Nhien cheat mutation"
)
canon_path.write_text(canon, encoding="utf-8")


# 2) Intercept the exact slash code before intent classification, AI fallback, turn advance or time advance.
facade_path = CORE / "GameCoreFacade.kt"
facade = facade_path.read_text(encoding="utf-8")
facade = replace_once(
    facade,
    '''    val legacy = JSONObject(legacyStateJson)\n    val state = loadOrMigrate(legacy)\n    val turnId = nextTurnId(legacy, state)\n''',
    '''    val legacy = JSONObject(legacyStateJson)\n    val state = loadOrMigrate(legacy)\n    if (AnNhienCanon.matchesPartyCheatCode(action)) return applyAnNhienPartyCheat(legacy, state)\n    val turnId = nextTurnId(legacy, state)\n''',
    "GameCoreFacade cheat intercept"
)
facade = replace_once(
    facade,
    '  fun currentCoreState(): String = GameStateCodec.encode(repository.load())\n',
    '''  private fun applyAnNhienPartyCheat(legacy: JSONObject, state: GameState): String {\n    val alreadyFollowing = AnNhienCanon.isFollowing(state)\n    val (updated, error) = AnNhienCanon.forceIntoParty(state)\n    val result = syncLegacy(legacy, updated, incrementTurn = false)\n    val reply = when {\n      error == "party_full" -> "Party đã đủ tối đa bốn thành viên; không thể thêm An Nhiên nếu chưa có chỗ trống."\n      alreadyFollowing -> "An Nhiên đã ở trong Party."\n      else -> "An Nhiên đã được thêm vào Party."\n    }\n\n    if (error == null) repository.save(updated)\n    val log = result.optJSONArray("log") ?: JSONArray().also { result.put("log", it) }\n    log.put(JSONObject().put("role", "gm").put("text", reply))\n    logger.log(PipelineLogEvent(\n      if (error == null) "CHEAT_COMMIT" else "CHEAT_REJECT",\n      details = mapOf("command" to "an_nhien_party", "reason" to (error ?: "committed"))\n    ))\n    return response(\n      handled = true,\n      state = result,\n      error = error,\n      reason = if (error == null) "cheat_committed" else "cheat_rejected",\n      reply = reply\n    )\n  }\n\n  fun currentCoreState(): String = GameStateCodec.encode(repository.load())\n''',
    "GameCoreFacade cheat handler"
)
facade_path.write_text(facade, encoding="utf-8")


# 3) Regression tests: exact code adds An Nhien immediately, is idempotent, and preserves the 4-member cap.
test_path = ROOT / "app/src/test/java/com/rabpit/backroom/core/AnNhienFollowerTest.kt"
test = test_path.read_text(encoding="utf-8")
test = replace_once(
    test,
    '''  @Test fun saveDecodeBackfillsAnNhienWithoutPuttingHerInParty() {\n''',
    '''  @Test fun slashCheatInstantlyAddsAnNhienAndIsIdempotent() {\n    val base = GameState.initial()\n    assertTrue(AnNhienCanon.matchesPartyCheatCode(" /annhien1234 "))\n    assertFalse(AnNhienCanon.matchesPartyCheatCode("/annhien123"))\n\n    val (added, error) = AnNhienCanon.forceIntoParty(base)\n    assertEquals(null, error)\n    assertTrue(AN_NHIEN_ID in added.party.memberIds)\n    assertEquals(base.party.memberIds.size + 1, added.party.memberIds.size)\n\n    val (again, againError) = AnNhienCanon.forceIntoParty(added)\n    assertEquals(null, againError)\n    assertEquals(added.party.memberIds, again.party.memberIds)\n  }\n\n  @Test fun slashCheatDoesNotSilentlyEvictPartyMembersWhenFull() {\n    val full = GameState.initial().copy(\n      characters = GameState.initial().characters + mapOf(\n        "a" to CharacterState("a", "A"),\n        "b" to CharacterState("b", "B"),\n        "c" to CharacterState("c", "C")\n      ),\n      party = PartyState(leaderId = KAI_ID, memberIds = listOf(KAI_ID, "a", "b", "c"), maxMembers = 4)\n    )\n    val (unchanged, error) = AnNhienCanon.forceIntoParty(full)\n    assertEquals("party_full", error)\n    assertFalse(AN_NHIEN_ID in unchanged.party.memberIds)\n    assertEquals(listOf(KAI_ID, "a", "b", "c"), unchanged.party.memberIds)\n  }\n\n  @Test fun saveDecodeBackfillsAnNhienWithoutPuttingHerInParty() {\n''',
    "An Nhien cheat tests"
)
test_path.write_text(test, encoding="utf-8")


required = [
    'PARTY_CHEAT_CODE = "/annhien1234"',
    "matchesPartyCheatCode(action)",
    "applyAnNhienPartyCheat(legacy, state)",
    'reason = if (error == null) "cheat_committed" else "cheat_rejected"',
    "slashCheatInstantlyAddsAnNhienAndIsIdempotent",
]
combined = canon + facade + test
for marker in required:
    if marker not in combined:
        raise RuntimeError(f"An Nhien cheat contract missing: {marker}")

print("/annhien1234 integrated: instant An Nhien Party add, no AI, no turn/time advance, idempotent and party-cap safe.")
