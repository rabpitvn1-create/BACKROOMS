from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str):
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    text = text.replace(old, new, 1)

helpers = r'''  private boolean flagValueEqualsAndroid(Object left, Object right) {
    if (left instanceof JSONObject && right instanceof JSONObject) {
      JSONObject a = (JSONObject) left;
      JSONObject b = (JSONObject) right;
      if (a.length() != b.length()) return false;
      Iterator<String> keys = a.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        if (!b.has(key) || !flagValueEqualsAndroid(a.opt(key), b.opt(key))) return false;
      }
      return true;
    }
    if (left instanceof JSONArray && right instanceof JSONArray) {
      JSONArray a = (JSONArray) left;
      JSONArray b = (JSONArray) right;
      if (a.length() != b.length()) return false;
      for (int i = 0; i < a.length(); i++) {
        if (!flagValueEqualsAndroid(a.opt(i), b.opt(i))) return false;
      }
      return true;
    }
    // Do not confuse a string such as "false" with the boolean false.
    if (left == null || left == JSONObject.NULL) return right == null || right == JSONObject.NULL;
    if (left instanceof Number && right instanceof Number) return left.toString().equals(right.toString());
    return left.equals(right);
  }

  private boolean flagPatchSatisfiedAndroid(JSONObject flags, JSONObject op) {
    String root = op.optString("root", "").trim();
    if (root.isEmpty() || !op.has("value") || flags == null || !flags.has(root)) return false;
    Object actual = flags.opt(root);
    Object requested = op.opt("value");
    // Mirror applyModelOperations: object flag patches merge at the root; scalars replace.
    // Reapplying an already satisfied patch is a no-op, not evidence of rejection.
    if (actual instanceof JSONObject && requested instanceof JSONObject) {
      JSONObject current = (JSONObject) actual;
      JSONObject patch = (JSONObject) requested;
      Iterator<String> keys = patch.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        if (!current.has(key) || !flagValueEqualsAndroid(current.opt(key), patch.opt(key))) return false;
      }
      return true;
    }
    return flagValueEqualsAndroid(actual, requested);
  }

  private JSONArray rejectedOperationIssuesAndroid(JSONObject before, JSONObject candidate, JSONObject generated) throws Exception {
    JSONArray issues = new JSONArray();
    JSONArray proposed = generated.optJSONArray("ops");
    if (proposed == null) return issues;
    JSONObject beforeFlags = before.optJSONObject("flags");
    JSONObject afterFlags = candidate.optJSONObject("flags");
    for (int i = 0; i < Math.min(24, proposed.length()); i++) {
      JSONObject op = proposed.optJSONObject(i);
      if (op == null) continue;
      String type = lower(op.optString("type", ""));
      boolean rejected = false;
      if (type.equals("set_level")) rejected = currentLevel(before) == currentLevel(candidate);
      else if (type.equals("set_location")) {
        String requested = op.optString("value", "").trim();
        rejected = !requested.isEmpty() && !requested.equals(candidate.optString("location", ""));
      } else if (type.equals("party_upsert") || type.equals("party_remove")) {
        rejected = !jsonChanged(before.optJSONArray("party"), candidate.optJSONArray("party"));
      } else if (type.equals("inventory_upsert") || type.equals("inventory_remove")) {
        rejected = !jsonChanged(before.optJSONArray("inventory"), candidate.optJSONArray("inventory"));
      } else if (type.equals("patch_player")) {
        rejected = !jsonChanged(before.optJSONObject("player"), candidate.optJSONObject("player"));
      } else if (type.equals("flag_patch")) {
        String root = op.optString("root", "").trim();
        Object beforeRoot = beforeFlags != null ? beforeFlags.opt(root) : null;
        Object afterRoot = afterFlags != null ? afterFlags.opt(root) : null;
        rejected = !jsonChanged(beforeRoot, afterRoot) && !flagPatchSatisfiedAndroid(afterFlags, op);
      }
      if (rejected) {
        String operationDetail = "opIndex=" + i;
        if (type.equals("flag_patch")) {
          String root = op.optString("root", "").trim().replaceAll("[^A-Za-z0-9_]", "?");
          operationDetail += " root=" + root.substring(0, Math.min(64, root.length()));
        }
        issues.put(new JSONObject()
          .put("rule", "state_narrative_mismatch")
          .put("severity", "hard")
          .put("claim", type)
          .put("opIndex", i)
          .put("reason", "Android reducer rejected this proposed state operation. " + operationDetail
            + ". Rewrite reply without narrating the rejected change and omit the invalid op."));
      }
    }
    return issues;
  }

  private void appendIssues(JSONArray target, JSONArray source) throws Exception {
    if (target == null || source == null) return;
    for (int i = 0; i < source.length(); i++) target.put(source.get(i));
  }

'''
anchor = "  private String writerPrompt(JSONObject before, String action, JSONObject rolls, JSONArray auditFeedback) throws Exception {\n"
if helpers.strip() not in text:
    if anchor not in text:
        raise RuntimeError("writerPrompt anchor not found")
    text = text.replace(anchor, helpers + anchor, 1)

old_first = r'''          JSONArray audits = meta ? new JSONArray() : auditsForRisk(before, action, rolls, generated, risk, writerWorker);
          JSONArray hardIssues = hardAuditIssues(audits);
          boolean repaired = false;
'''
new_first = r'''          JSONArray audits = meta ? new JSONArray() : auditsForRisk(before, action, rolls, generated, risk, writerWorker);
          JSONArray hardIssues = hardAuditIssues(audits);
          if (!meta) appendIssues(hardIssues, rejectedOperationIssuesAndroid(before, candidateState, generated));
          boolean repaired = false;
'''
replace_once(old_first, new_first, "initial deterministic rejected-op issues")

old_repair = r'''            audits = auditsForRisk(before, action, rolls, generated, risk, writerWorker);
            hardIssues = hardAuditIssues(audits);
'''
new_repair = r'''            audits = auditsForRisk(before, action, rolls, generated, risk, writerWorker);
            hardIssues = hardAuditIssues(audits);
            appendIssues(hardIssues, rejectedOperationIssuesAndroid(before, candidateState, generated));
'''
replace_once(old_repair, new_repair, "repair deterministic rejected-op issues")

for marker in ["rejectedOperationIssuesAndroid", "state_narrative_mismatch", "appendIssues(hardIssues, rejectedOperationIssuesAndroid"]:
    if marker not in text:
        raise RuntimeError(f"rejected-op repair marker missing: {marker}")

MAIN.write_text(text, encoding="utf-8")
print("Android rejected state operations now force one repair and prevent commit if still rejected.")
