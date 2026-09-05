from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / 'app/src/main/java/com/rabpit/backroom/MainActivity.java'
FACADE = ROOT / 'app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt'


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise RuntimeError('Lucia recruitment anchor mismatch: ' + old[:100])
    return text.replace(old, new, 1)


main = MAIN.read_text()
main = replace_once(main,
    '    String value = lower(name);\n    if (value.contains("an nhiên")',
    '''    String value = lower(name);
    if (value.contains("lucia")) return luciaContactPresent(before) &&
      before.optJSONObject("flags").optJSONObject("lucia").optBoolean("identityKnown", false);
    if (value.contains("an nhiên")''')
main = replace_once(main,
    '  private boolean flagRootAllowed(JSONObject before, String root, JSONObject rolls) {',
    '''  private boolean luciaContactPresent(JSONObject before) {
    JSONObject flags = before.optJSONObject("flags");
    JSONObject lucia = flags != null ? flags.optJSONObject("lucia") : null;
    return currentLevel(before) == 0 && lucia != null &&
      lucia.optBoolean("encountered", false) && lucia.optBoolean("present", false);
  }

  private boolean luciaContactEstablishedOrEncountering(JSONObject before, JSONObject rolls) {
    return luciaContactPresent(before) ||
      (currentLevel(before) == 0 && rollSuccess(rolls, "luciaEncounter"));
  }

  private boolean flagRootAllowed(JSONObject before, String root, JSONObject rolls) {''')
main = replace_once(main,
    '    if (root == null) return false;',
    '    if (root == null) return false;\n    if (root.equals("lucia")) return luciaContactEstablishedOrEncountering(before, rolls);')
# Only dialogue facts may be written: presence and encounter remain engine-owned.
main = replace_once(main,
    '        Object value = op.get("value");',
    '''        Object value = op.get("value");
        if (root.equals("lucia")) {
          if (!(value instanceof JSONObject)) continue;
          JSONObject proposed = (JSONObject)value;
          JSONObject dialogue = new JSONObject();
          if (Boolean.TRUE.equals(proposed.opt("identityKnown"))) dialogue.put("identityKnown", true);
          value = dialogue;
        }''')
# The story-owned encounter commit runs after model ops in applyModelOperations(). If Lucia
# introduces herself on that first-contact turn, preserve the already validated dialogue fact
# instead of resetting identityKnown to false at the deterministic encounter tail.
main = replace_once(main,
    '''      if (lucia == null) lucia = new JSONObject();
      lucia.put("exists", true)
        .put("encountered", true)
        .put("present", true)
        .put("spawned", true)
        .put("follower", false)
        .put("followerCandidate", true)
        .put("identityKnown", false)
        .put("joinConfirmed", false)''',
    '''      if (lucia == null) lucia = new JSONObject();
      boolean luciaIdentityKnownFromDialogue = lucia.optBoolean("identityKnown", false);
      lucia.put("exists", true)
        .put("encountered", true)
        .put("present", true)
        .put("spawned", true)
        .put("follower", false)
        .put("followerCandidate", true)
        .put("identityKnown", luciaIdentityKnownFromDialogue)
        .put("joinConfirmed", false)''')
main = replace_once(main,
    'Chỉ dùng flag root: exploration, communication, iris, syvial,',
    'Chỉ dùng flag root: exploration, communication, lucia, iris, syvial,')
main = replace_once(main,
    'Chỉ đặt identityKnown=true sau khi Lucia thực sự tự giới thiệu một tên dùng được hoặc danh tính được xác lập trong hội thoại. ',
    'Khi Lucia tự giới thiệu trong hội thoại, bắt buộc xuất op type=flag_patch, root=lucia, value={identityKnown:true}; chỉ kể trong reply không cập nhật trạng thái. ')
main = replace_once(main,
    'Party ADD chỉ hợp lệ ở lượt sau khi player chủ động mời/chấp nhận Lucia đi cùng. ',
    'Party ADD chỉ hợp lệ ở lượt sau khi player chủ động mời/chấp nhận Lucia đi cùng. Khi flags.lucia.identityKnown đã true từ lượt trước và Lucia đồng ý lời mời, bắt buộc xuất op type=party_upsert, member={id:lucia,name:Lucia Lục,present:true,joinConfirmed:true}; không được chỉ kể đã gia nhập mà thiếu op. Nếu danh tính chưa được lưu, xác lập danh tính bằng flag_patch trước và chưa kể đã gia nhập. ')
MAIN.write_text(main)

facade = FACADE.read_text()
# Invitations require NPC consent. Let the existing writer/validated delta path
# resolve it instead of executing the default unconfirmed PartyCommand locally.
facade = replace_once(facade,
    '    val resolvedCommands = resolver.resolveSequence(interpreted.candidates, turnId, context).filterNotNull()',
    '''    if (interpreted.candidates.any { it.intent == GameIntent.PARTY_JOIN_REQUEST }) {
      repository.save(pending.state)
      return response(false, legacy, null, "party_consent_required")
    }
    val resolvedCommands = resolver.resolveSequence(interpreted.candidates, turnId, context).filterNotNull()''')
facade = replace_once(facade,
    '''    val luciaIdentityKnownBefore =
      before.optJSONObject("flags")?.optJSONObject("lucia")?.optBoolean("identityKnown", false) == true''',
    '''    val luciaBefore = before.optJSONObject("flags")?.optJSONObject("lucia")
    val luciaIdentityKnownBefore = luciaBefore?.optBoolean("identityKnown", false) == true &&
      luciaBefore.optBoolean("encountered", false) && luciaBefore.optBoolean("present", false) &&
      before.optJSONObject("level")?.optInt("number", -1) == 0''')
FACADE.write_text(facade)
print('Lucia dialogue flags, first-contact identity persistence, story recruitment and invitation routing repaired.')
