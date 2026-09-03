from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FACADE = CORE / "GameCoreFacade.kt"
CONTINUITY = CORE / "StoryCompanionContinuity.kt"
CONTINUITY_TEST = TESTS / "StoryCompanionContinuityTest.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# 1) A fixed Level-0 encounter only establishes FIRST CONTACT.
#    Encounter != recruitment. Lucia must not be inserted into the legacy party
#    before identity has been established and Kai explicitly accepts/invites her.
# ---------------------------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")
start = main.find('    if (rollSuccess(rolls, "luciaEncounter")) {')
end_anchor = '\n    }\n\n    state.put("flags", flags);'
end = main.find(end_anchor, start)
if start < 0 or end < 0:
    raise RuntimeError("Lucia first-contact commit block missing")
old_block = main[start : end + len('\n    }')]
if 'ensureSpecialFollowerInLegacyParty(state, "lucia"' not in old_block:
    raise RuntimeError("Lucia encounter no longer contains the expected premature party join")

new_block = '''    if (rollSuccess(rolls, "luciaEncounter")) {
      JSONObject lucia = flags.optJSONObject("lucia");
      if (lucia == null) lucia = new JSONObject();
      lucia.put("exists", true)
        .put("encountered", true)
        .put("present", true)
        .put("spawned", true)
        .put("follower", false)
        .put("followerCandidate", true)
        .put("identityKnown", false)
        .put("joinConfirmed", false)
        .put("reunionEligible", false)
        .put("continuity", "FIRST_CONTACT_LEVEL_0")
        .put("levelEncountered", 0)
        .put("joinPending", true);
      flags.put("lucia", lucia);
    }'''
main = main[:start] + new_block + main[end + len('\n    }'):]

prompt_old = (
    "requiresQuest=false, randomSpawn=false. Không tự gán quân hàm, quyền chỉ huy, quan hệ/xưng hô hay quá khứ "
)
prompt_new = (
    "requiresQuest=false, randomSpawn=false. Fixed encounter chỉ tạo FIRST CONTACT, không tự đưa Lucia vào Party. "
    "Trước khi flags.lucia.identityKnown=true, Kai chỉ biết cô là một nữ binh trẻ chưa xác minh danh tính; không biến writer canon thành tri thức của Kai. "
    "Chỉ đặt identityKnown=true sau khi Lucia thực sự tự giới thiệu một tên dùng được hoặc danh tính được xác lập trong hội thoại. "
    "Không add Party trong cùng lượt identityKnown chuyển từ false sang true. Party ADD chỉ hợp lệ ở lượt sau khi player chủ động mời/chấp nhận Lucia đi cùng. "
    "Không tự gán quân hàm, quyền chỉ huy, quan hệ/xưng hô hay quá khứ "
)
main = replace_once(main, prompt_old, prompt_new, "Lucia first-contact GM hard lock")
MAIN.write_text(main, encoding="utf-8")


# ---------------------------------------------------------------------------
# 2) Keep the join decision deterministic and testable. For Lucia, fixed-level
#    materialization is necessary but not sufficient for Party membership.
# ---------------------------------------------------------------------------
continuity = CONTINUITY.read_text(encoding="utf-8")
helper_anchor = '''  @JvmStatic
  fun canMaterialize(characterId: String, currentLevel: Int, alreadyPresent: Boolean): Boolean {
    if (alreadyPresent) return false
    return fixedLevel(characterId) == currentLevel
  }
'''
helper_replacement = helper_anchor + '''
  @JvmStatic
  fun hasExplicitJoinIntent(characterId: String, action: String): Boolean {
    if (characterId.trim().lowercase() != LUCIA_ID) return true
    val text = action.trim().lowercase()
    if (text.isBlank()) return false
    val rejectionPhrases = listOf(
      "không đi cùng", "không cần đi cùng", "đừng đi cùng", "không gia nhập", "đừng gia nhập",
      "đừng theo", "không theo", "từ chối", "do not join", "don't join", "do not come", "don't come"
    )
    if (rejectionPhrases.any(text::contains)) return false
    val joinPhrases = listOf(
      "đi cùng", "cùng đi", "đồng hành", "gia nhập", "vào đội", "vào nhóm", "theo tôi", "theo ta",
      "join us", "join me", "come with me", "travel with me"
    )
    return joinPhrases.any(text::contains)
  }

  @JvmStatic
  fun canJoinAfterFirstContact(
    characterId: String,
    currentLevel: Int,
    alreadyPresent: Boolean,
    identityKnown: Boolean,
    action: String
  ): Boolean {
    if (characterId.trim().lowercase() != LUCIA_ID) {
      return canMaterialize(characterId, currentLevel, alreadyPresent)
    }
    return identityKnown &&
      canMaterialize(characterId, currentLevel, alreadyPresent) &&
      hasExplicitJoinIntent(characterId, action)
  }
'''
if "fun canJoinAfterFirstContact(" not in continuity:
    continuity = replace_once(continuity, helper_anchor, helper_replacement, "Lucia join-state helper")
CONTINUITY.write_text(continuity, encoding="utf-8")


# ---------------------------------------------------------------------------
# 3) Gemini candidate state cannot smuggle Lucia into Party on the encounter
#    turn. Unauthorized Lucia additions are ignored rather than rejecting the
#    entire narrated turn. Syvial/Iris fixed reunion behavior is untouched.
# ---------------------------------------------------------------------------
facade = FACADE.read_text(encoding="utf-8")
followers_anchor = '''    val currentFollowers = pending.state.party.memberIds.filter { it != KAI_ID }.toSet()
    val candidateLevel = candidate.optJSONObject("level")?.optInt("number", -1) ?: -1
'''
followers_replacement = followers_anchor + '''    val luciaIdentityKnownBefore =
      before.optJSONObject("flags")?.optJSONObject("lucia")?.optBoolean("identityKnown", false) == true
'''
if "val luciaIdentityKnownBefore" not in facade:
    facade = replace_once(facade, followers_anchor, followers_replacement, "Lucia prior identity state")

party_old = '''      val known = pending.state.characters[id]
      val storyJoin = StoryCompanionContinuity.canMaterialize(id, candidateLevel, id in currentFollowers)
      commands += PartyCommand(
        "$turnId:GEMINI:PARTY_ADD:$index", turnId, KAI_ID, id, CommandSource.GEMINI, PartyCommand.Operation.ADD,
        consentConfirmed = member.optBoolean("joinConfirmed", false) && known?.metadata?.get("joinEligible") == "true",
        targetPresent = member.optBoolean("present", false) && (known?.presence == CharacterPresence.ACTIVE || storyJoin)
      )
'''
party_new = '''      val known = pending.state.characters[id]
      val storyJoin = StoryCompanionContinuity.canMaterialize(id, candidateLevel, id in currentFollowers)
      val luciaJoinAuthorized = id != StoryCompanionContinuity.LUCIA_ID ||
        StoryCompanionContinuity.canJoinAfterFirstContact(
          id,
          candidateLevel,
          id in currentFollowers,
          luciaIdentityKnownBefore,
          action
        )
      if (id == StoryCompanionContinuity.LUCIA_ID && !luciaJoinAuthorized) return@forEachIndexed
      commands += PartyCommand(
        "$turnId:GEMINI:PARTY_ADD:$index", turnId, KAI_ID, id, CommandSource.GEMINI, PartyCommand.Operation.ADD,
        consentConfirmed = member.optBoolean("joinConfirmed", false) && known?.metadata?.get("joinEligible") == "true" && luciaJoinAuthorized,
        targetPresent = member.optBoolean("present", false) && (known?.presence == CharacterPresence.ACTIVE || (storyJoin && luciaJoinAuthorized))
      )
'''
facade = replace_once(facade, party_old, party_new, "Lucia validated Party ADD gate")
FACADE.write_text(facade, encoding="utf-8")


# ---------------------------------------------------------------------------
# 4) Focused regression: exploration/identity questions are not recruitment;
#    a known Lucia plus an explicit non-negated invitation at Level 0 is.
# ---------------------------------------------------------------------------
test = CONTINUITY_TEST.read_text(encoding="utf-8")
test_anchor = '''  @Test fun materializationOnlyOccursAtTheFixedLevelOnce() {
'''
new_test = '''  @Test fun luciaJoinRequiresPriorIdentityAndExplicitPlayerIntent() {
    assertFalse(StoryCompanionContinuity.hasExplicitJoinIntent("lucia", "Khám phá hành lang"))
    assertFalse(StoryCompanionContinuity.hasExplicitJoinIntent("lucia", "Cô là ai?"))
    assertFalse(StoryCompanionContinuity.hasExplicitJoinIntent("lucia", "Lucia, không đi cùng tôi"))
    assertTrue(StoryCompanionContinuity.hasExplicitJoinIntent("lucia", "Lucia, đi cùng tôi"))

    assertFalse(StoryCompanionContinuity.canJoinAfterFirstContact("lucia", 0, false, false, "Lucia, đi cùng tôi"))
    assertFalse(StoryCompanionContinuity.canJoinAfterFirstContact("lucia", 0, false, true, "Khám phá tiếp"))
    assertFalse(StoryCompanionContinuity.canJoinAfterFirstContact("lucia", 1, false, true, "Lucia, đi cùng tôi"))
    assertTrue(StoryCompanionContinuity.canJoinAfterFirstContact("lucia", 0, false, true, "Lucia, đi cùng tôi"))
  }

'''
if "luciaJoinRequiresPriorIdentityAndExplicitPlayerIntent" not in test:
    test = replace_once(test, test_anchor, new_test + test_anchor, "Lucia first-contact regression test")
CONTINUITY_TEST.write_text(test, encoding="utf-8")


# Final fail-closed audit for this regression.
main_final = MAIN.read_text(encoding="utf-8")
start = main_final.find('    if (rollSuccess(rolls, "luciaEncounter")) {')
end = main_final.find(end_anchor, start)
if start < 0 or end < 0:
    raise RuntimeError("Lucia final first-contact block missing")
lucia_block = main_final[start:end]
for forbidden in (
    'ensureSpecialFollowerInLegacyParty(state, "lucia"',
    'continuity", "RECRUITED_LEVEL_0"',
):
    if forbidden in lucia_block:
        raise RuntimeError("Lucia premature Party contract survived: " + forbidden)
for required in (
    'identityKnown", false',
    'joinConfirmed", false',
    'continuity", "FIRST_CONTACT_LEVEL_0"',
    'joinPending", true',
):
    if required not in lucia_block:
        raise RuntimeError("Lucia first-contact state missing: " + required)

facade_final = FACADE.read_text(encoding="utf-8")
for required in (
    "val luciaIdentityKnownBefore",
    "StoryCompanionContinuity.canJoinAfterFirstContact(",
    "return@forEachIndexed",
):
    if required not in facade_final:
        raise RuntimeError("Lucia Party gate missing: " + required)

if "Fixed encounter chỉ tạo FIRST CONTACT, không tự đưa Lucia vào Party." not in main_final:
    raise RuntimeError("Lucia first-contact prompt hard lock missing")

print("Lucia first-contact Party gate applied: fixed encounter surfaces an unidentified contact; Party ADD requires prior identity + explicit later player invitation.")
