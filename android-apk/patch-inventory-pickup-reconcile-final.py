from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str):
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    text = text.replace(old, new, 1)


helpers = r'''  private String cleanPickupCandidateAndroid(String value) {
    if (value == null) return "";
    String candidate = value.trim().replaceAll("^[\\s\\\"'“”‘’]+|[\\s\\\"'“”‘’]+$", "");
    candidate = candidate.replaceFirst("(?iu)^(?:một|cái|chiếc|con|quyển)\\s+", "").trim();
    candidate = candidate.replaceAll("\\s+", " ");
    if (candidate.length() < 2 || candidate.length() > 120) return "";
    String lowered = lower(candidate);
    if (lowered.equals("nó") || lowered.equals("vật đó") || lowered.equals("thứ đó") || lowered.equals("đồ đó") ||
        lowered.equals("cái đó") || lowered.equals("chiếc đó") || lowered.equals("it") || lowered.equals("that")) return "";
    return candidate;
  }

  private String pickupRegexCandidateAndroid(String action, String regex) {
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE).matcher(action == null ? "" : action);
    if (!matcher.find() || matcher.groupCount() < 1) return "";
    return cleanPickupCandidateAndroid(matcher.group(1));
  }

  private String pickupCandidateAndroid(String action) {
    if (!acquisitionIntent(action)) return "";
    String[] direct = new String[] {
      "(?:^|[^\\p{L}\\p{N}_])(?:nhặt|lấy|cầm|thu hồi|tịch thu|nhận)\\s+(?:(?:một|cái|chiếc|con|quyển)\\s+)?(.{2,120}?)(?=\\s+(?:lên|ra|rồi|và|để|bỏ|cất|đưa|vào|cho|hoàn nguyên)(?:\\s|[,.;!?]|$)|[,.;!?]|$)",
      "(?:^|[^\\p{L}\\p{N}_])(?:bỏ|cất|đưa|thu)\\s+(?:(?:một|cái|chiếc|con|quyển)\\s+)?(.{2,120}?)\\s+(?:vào|trong)\\s+(?:kho|omnivault|nhẫn vạn tàng|không gian lưu trữ)(?=[,.;!?]|$)"
    };
    for (String regex : direct) {
      String candidate = pickupRegexCandidateAndroid(action, regex);
      if (!candidate.isEmpty()) return candidate;
    }
    String[] introduced = new String[] {
      "(?:^|[^\\p{L}\\p{N}_])(?:thấy|nhìn thấy|phát hiện)\\s+(?:(?:một|cái|chiếc|con|quyển)\\s+)?(.{2,120}?)(?=\\s+(?:dưới|trên|bên|cạnh|ở|nằm|trong|ngay)(?:\\s|[,.;!?]|$)|[,.;!?]|$)",
      "(?:^|[^\\p{L}\\p{N}_])(?:có)\\s+(?:(?:một|cái|chiếc|con|quyển)\\s+)?(.{2,120}?)(?=\\s+(?:dưới|trên|bên|cạnh|ở|nằm|trong|ngay)(?:\\s|[,.;!?]|$)|[,.;!?]|$)"
    };
    for (String regex : introduced) {
      String candidate = pickupRegexCandidateAndroid(action, regex);
      if (!candidate.isEmpty()) return candidate;
    }
    return "";
  }

  private String omnivaultRestoreTargetAndroid(String action) {
    if (!containsAny(action, "hoàn nguyên", "restore")) return "";
    String[] patterns = new String[] {
      "(?:^|[^\\p{L}\\p{N}_])(?:hoàn nguyên|restore)(?:\\s+(?:lại|nó|vật đó|thứ đó|cái đó|chiếc đó))*\\s+(?:thành|về)\\s+(?:(?:một|cái|chiếc)\\s+)?(.{2,120}?)(?=\\s+(?:rồi|và|để|bỏ|cất|đưa|vào|trong)(?:\\s|[,.;!?]|$)|[,.;!?]|$)"
    };
    for (String regex : patterns) {
      String candidate = pickupRegexCandidateAndroid(action, regex);
      if (!candidate.isEmpty()) return candidate;
    }
    return "";
  }

  private java.util.HashSet<String> pickupTokensAndroid(String value) {
    java.util.HashSet<String> tokens = new java.util.HashSet<>();
    for (String token : lower(value == null ? "" : value).split("[^\\p{L}\\p{N}_]+")) {
      token = token.trim();
      if (token.length() < 2 || token.equals("một") || token.equals("cái") || token.equals("chiếc") || token.equals("con") || token.equals("quyển") || token.equals("the") || token.equals("an")) continue;
      tokens.add(token);
    }
    return tokens;
  }

  private boolean pickupTokenOverlapAndroid(String candidate, String text) {
    java.util.HashSet<String> candidateTokens = pickupTokensAndroid(candidate);
    if (candidateTokens.isEmpty()) return false;
    java.util.HashSet<String> textTokens = pickupTokensAndroid(text);
    int matches = 0;
    for (String token : candidateTokens) if (textTokens.contains(token)) matches++;
    int required = candidateTokens.size() == 1 ? 1 : Math.min(2, candidateTokens.size());
    return matches >= required;
  }

  private boolean mundanePickupName(String name) {
    String value = cleanPickupCandidateAndroid(name);
    if (value.isEmpty()) return false;
    return !containsAny(value,
      "almond water", "madgod", "liquid pain", "greek fire", "royal ration", "firesalt", "memory juice", "super almond",
      "súng", "gun", "pistol", "rifle", "shotgun", "magnum", "revolver", "đạn", "ammo", "lựu đạn", "grenade", "bom", "bomb",
      "dao", "knife", "kiếm", "sword", "blade", "giáp", "armor", "helmet", "mũ bảo hộ", "module", "core", "nhẫn", "ring",
      "chìa", "key", "thẻ", "card", "artifact", "cổ vật", "thuốc", "medkit", "ration", "khẩu phần");
  }

  private int inventoryFuzzyIndexAndroid(JSONObject state, String candidate) {
    JSONArray inventory = state != null ? state.optJSONArray("inventory") : null;
    if (inventory == null) return -1;
    for (int i = 0; i < inventory.length(); i++) {
      String name = itemName(inventory.opt(i));
      if (!name.isEmpty() && pickupTokenOverlapAndroid(name, candidate) && pickupTokenOverlapAndroid(candidate, name)) return i;
    }
    return -1;
  }

  private boolean replyDeniesPickupAndroid(String reply) {
    return containsAny(reply,
      "không thể nhặt", "không nhặt được", "không thể lấy", "không lấy được", "không thể cất", "không cất được",
      "không thể hoàn nguyên", "hoàn nguyên thất bại", "cannot pick", "cannot take", "cannot store", "cannot restore", "restore failed");
  }

  private boolean replyConfirmsPickupAndroid(String candidate, String reply) {
    if (candidate == null || candidate.isEmpty() || reply == null || reply.trim().isEmpty() || replyDeniesPickupAndroid(reply)) return false;
    if (!containsAny(reply, "nhặt", "nhấc", "lấy", "cầm", "thu hồi", "cất", "bỏ vào", "đưa vào", "thu vào", "lưu trữ", "kho", "omnivault", "nhẫn vạn tàng", "store", "stored")) return false;
    return pickupTokenOverlapAndroid(candidate, reply);
  }

  private boolean replyConfirmsRestoreAndroid(String target, String reply) {
    if (target == null || target.isEmpty() || reply == null || reply.trim().isEmpty() || replyDeniesPickupAndroid(reply)) return false;
    if (!containsAny(reply, "hoàn nguyên", "restore", "cất", "bỏ vào", "thu vào", "lưu trữ", "omnivault", "nhẫn vạn tàng")) return false;
    return pickupTokenOverlapAndroid(target, reply);
  }

  private JSONArray pickupNarrativeIssuesAndroid(String action, JSONObject generated) throws Exception {
    JSONArray issues = new JSONArray();
    String target = omnivaultRestoreTargetAndroid(action);
    if (target.isEmpty() || !mundanePickupName(target)) return issues;
    String reply = generated != null ? generated.optString("reply", "") : "";
    if (!replyConfirmsRestoreAndroid(target, reply)) {
      issues.put(new JSONObject()
        .put("rule", "omnivault_action_lock")
        .put("severity", "hard")
        .put("claim", "Omnivault restore -> " + target)
        .put("reason", "Kai explicitly used Omnivault Restore on an ordinary inanimate object. Confirm the successful Restore into " + target + " and store it if requested; do not manufacture a resource-scarcity failure."));
    }
    return issues;
  }

  private void reconcileConfirmedPickupOpsAndroid(JSONObject before, JSONObject generated, String action) throws Exception {
    String source = pickupCandidateAndroid(action);
    String restoreTarget = omnivaultRestoreTargetAndroid(action);
    String reply = generated != null ? generated.optString("reply", "") : "";
    String finalName = "";
    String basis = "";
    if (!restoreTarget.isEmpty() && mundanePickupName(restoreTarget) && replyConfirmsRestoreAndroid(restoreTarget, reply)) {
      finalName = restoreTarget;
      basis = "omnivault_restore";
    } else if (!source.isEmpty() && mundanePickupName(source) && replyConfirmsPickupAndroid(source, reply)) {
      finalName = source;
      basis = "gm_confirmed_pickup";
    }
    if (finalName.isEmpty()) return;

    JSONArray inventory = before != null ? before.optJSONArray("inventory") : null;
    int existingIndex = inventoryFuzzyIndexAndroid(before, finalName);
    int finalQuantity = 1;
    if (existingIndex >= 0 && inventory != null) {
      JSONObject previous = inventory.optJSONObject(existingIndex);
      if (previous != null) finalQuantity = Math.max(1, previous.optInt("quantity", 1)) + 1;
    }

    JSONArray ops = generated.optJSONArray("ops");
    if (ops == null) ops = new JSONArray();
    boolean matched = false;
    for (int i = 0; i < ops.length(); i++) {
      JSONObject op = ops.optJSONObject(i);
      if (op == null || !"inventory_upsert".equalsIgnoreCase(op.optString("type", ""))) continue;
      JSONObject item = op.optJSONObject("item");
      String name = item != null ? item.optString("name", "") : "";
      if (!name.isEmpty() && pickupTokenOverlapAndroid(name, finalName)) {
        item.put("name", finalName).put("quantity", Math.max(finalQuantity, item.optInt("quantity", 1))).put("state", item.optString("state", "STORED"));
        op.put("basis", basis);
        matched = true;
        break;
      }
    }
    if (!matched) {
      ops.put(new JSONObject()
        .put("type", "inventory_upsert")
        .put("item", new JSONObject().put("name", finalName).put("quantity", finalQuantity).put("state", "STORED"))
        .put("basis", basis));
    }
    generated.put("ops", ops);
  }

'''

anchor = "  private JSONArray rejectedOperationIssuesAndroid(JSONObject before, JSONObject candidate, JSONObject generated) throws Exception {\n"
if "private void reconcileConfirmedPickupOpsAndroid(" not in text:
    if anchor not in text:
        raise RuntimeError("rejectedOperationIssuesAndroid anchor not found for pickup reconciliation")
    text = text.replace(anchor, helpers + anchor, 1)

old_first = r'''          if (reply.isEmpty()) throw new Exception("AI trả về phản hồi rỗng, lượt này không được ghi.");

          JSONObject candidateState = meta
'''
new_first = r'''          if (reply.isEmpty()) throw new Exception("AI trả về phản hồi rỗng, lượt này không được ghi.");
          if (!meta) reconcileConfirmedPickupOpsAndroid(before, generated, action);

          JSONObject candidateState = meta
'''
replace_once(old_first, new_first, "initial pickup reconciliation")

old_initial_issues = r'''          JSONArray hardIssues = hardAuditIssues(audits);
          if (!meta) appendIssues(hardIssues, rejectedOperationIssuesAndroid(before, candidateState, generated));
          boolean repaired = false;
'''
new_initial_issues = r'''          JSONArray hardIssues = hardAuditIssues(audits);
          if (!meta) {
            appendIssues(hardIssues, rejectedOperationIssuesAndroid(before, candidateState, generated));
            appendIssues(hardIssues, pickupNarrativeIssuesAndroid(action, generated));
          }
          boolean repaired = false;
'''
replace_once(old_initial_issues, new_initial_issues, "initial Omnivault narrative issue")

old_repair = r'''            if (reply.isEmpty()) throw new Exception("AI repair trả phản hồi rỗng; state không được thay đổi.");
            reconcileConfirmedPickupOpsAndroid(before, generated, action);
            repaired = true;
            candidateState = applyModelOperations(before, generated.optJSONArray("ops"), rolls, action);
'''
new_repair = r'''            if (reply.isEmpty()) throw new Exception("AI repair trả phản hồi rỗng; state không được thay đổi.");
            reconcileConfirmedPickupOpsAndroid(before, generated, action);
            repaired = true;
            candidateState = applyModelOperations(before, generated.optJSONArray("ops"), rolls, action);
'''
replace_once(old_repair, new_repair, "repair pickup reconciliation")

old_repair_issues = r'''            hardIssues = hardAuditIssues(audits);
            appendIssues(hardIssues, rejectedOperationIssuesAndroid(before, candidateState, generated));
'''
new_repair_issues = r'''            hardIssues = hardAuditIssues(audits);
            appendIssues(hardIssues, rejectedOperationIssuesAndroid(before, candidateState, generated));
            appendIssues(hardIssues, pickupNarrativeIssuesAndroid(action, generated));
'''
replace_once(old_repair_issues, new_repair_issues, "repair Omnivault narrative issue")

for required in [
    "pickupCandidateAndroid",
    "omnivaultRestoreTargetAndroid",
    "replyConfirmsRestoreAndroid",
    "pickupNarrativeIssuesAndroid",
    "mundanePickupName",
    "gm_confirmed_pickup",
    "omnivault_restore",
    "reconcileConfirmedPickupOpsAndroid(before, generated, action)",
]:
    if required not in text:
        raise RuntimeError(f"pickup reconciliation marker missing: {required}")

MAIN.write_text(text, encoding="utf-8")
print("Android inventory reconciliation enabled: mundane pickup and Omnivault Restore outcomes are authoritative continuity records; resource scarcity cannot silently erase them.")
