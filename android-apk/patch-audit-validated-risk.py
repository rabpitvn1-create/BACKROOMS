from pathlib import Path


MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


text = MAIN.read_text(encoding="utf-8")

old_risk_start = text.index("  private int proposedTurnRisk(JSONObject before, JSONObject generated) {\n")
old_risk_end = text.index("\n  private String auditScopeCanon", old_risk_start)
new_risk = r'''  private boolean jsonChanged(Object before, Object after) {
    String left = before == null || before == JSONObject.NULL ? "null" : String.valueOf(before);
    String right = after == null || after == JSONObject.NULL ? "null" : String.valueOf(after);
    return !left.equals(right);
  }

  private int validatedTurnRisk(JSONObject before, JSONObject candidate, JSONObject generated) {
    int score = 0;
    if (currentLevel(before) != currentLevel(candidate)) score += 4;
    if (jsonChanged(before.optJSONArray("party"), candidate.optJSONArray("party"))) score += 3;
    if (jsonChanged(before.optJSONArray("inventory"), candidate.optJSONArray("inventory"))) score += 1;
    if (jsonChanged(before.optJSONObject("player"), candidate.optJSONObject("player"))) score += 1;

    JSONObject beforeFlags = before.optJSONObject("flags");
    JSONObject afterFlags = candidate.optJSONObject("flags");
    if (beforeFlags == null) beforeFlags = new JSONObject();
    if (afterFlags == null) afterFlags = new JSONObject();
    for (String root : new String[] {"iris", "syvial", "survivorRegistry", "entityRegistry", "survivorsConfirmed", "entitiesConfirmedLocal", "madGod", "reunionPath"}) {
      if (jsonChanged(beforeFlags.opt(root), afterFlags.opt(root))) score += 3;
    }
    for (String root : new String[] {"omnivault", "communication", "exploration", "visualAreaKey", "visualEventKey", "entityEncounterKey"}) {
      if (jsonChanged(beforeFlags.opt(root), afterFlags.opt(root))) score += 1;
    }

    String reply = generated.optString("reply", "");
    JSONArray party = before.optJSONArray("party");
    boolean hasParty = party != null && party.length() > 0;
    if (hasParty && containsAny(reply, "biết", "nhớ", "nhận ra", "hiểu rằng", "tiết lộ", "bí mật", "nguồn gốc", "thật ra", "kể rằng", "knows", "knew", "secret", "origin")) score += 2;
    if (hasParty && containsAny(reply, "yêu", "thích", "ghen", "tin tưởng", "phản bội", "người yêu", "hẹn hò", "quan hệ", "love", "trust", "betray", "relationship")) score += 2;
    return score;
  }
'''
text = text[:old_risk_start] + new_risk + text[old_risk_end:]

old_first = r'''          int risk = meta ? 0 : proposedTurnRisk(before, generated);
          int writerWorker = lastGeminiWorker;
          JSONArray audits = meta ? new JSONArray() : auditsForRisk(before, action, rolls, generated, risk, writerWorker);
          JSONArray hardIssues = hardAuditIssues(audits);
          boolean repaired = false;
'''
new_first = r'''          JSONObject candidateState = meta
            ? new JSONObject(before.toString())
            : applyModelOperations(before, generated.optJSONArray("ops"), rolls, action);
          int risk = meta ? 0 : validatedTurnRisk(before, candidateState, generated);
          int writerWorker = lastGeminiWorker;
          JSONArray audits = meta ? new JSONArray() : auditsForRisk(before, action, rolls, generated, risk, writerWorker);
          JSONArray hardIssues = hardAuditIssues(audits);
          boolean repaired = false;
'''
text = replace_once(text, old_first, new_first, "first validated risk")

old_repair = r'''            repaired = true;
            risk = proposedTurnRisk(before, generated);
            writerWorker = lastGeminiWorker;
            audits = auditsForRisk(before, action, rolls, generated, risk, writerWorker);
            hardIssues = hardAuditIssues(audits);
'''
new_repair = r'''            repaired = true;
            candidateState = applyModelOperations(before, generated.optJSONArray("ops"), rolls, action);
            risk = validatedTurnRisk(before, candidateState, generated);
            writerWorker = lastGeminiWorker;
            audits = auditsForRisk(before, action, rolls, generated, risk, writerWorker);
            hardIssues = hardAuditIssues(audits);
'''
text = replace_once(text, old_repair, new_repair, "repair validated risk")

old_apply = r'''          JSONObject state = meta
            ? new JSONObject(before.toString())
            : applyModelOperations(before, generated.optJSONArray("ops"), rolls, action);
'''
new_apply = r'''          JSONObject state = candidateState;
'''
text = replace_once(text, old_apply, new_apply, "reuse validated candidate state")

MAIN.write_text(text, encoding="utf-8")
print("APK audit risk now derives from validated candidate state, so rejected model ops do not trigger unnecessary auditors.")
