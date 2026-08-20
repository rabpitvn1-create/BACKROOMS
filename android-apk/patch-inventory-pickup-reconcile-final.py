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
    candidate = candidate.replaceFirst("(?iu)^(?:một|cái|chiếc|con|quyển|lọ|hộp)\\s+", "").trim();
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
      "(?:^|[^\\p{L}\\p{N}_])(?:nhặt|lấy|cầm|thu hồi|tịch thu|nhận)\\s+(?:(?:một|cái|chiếc|con|quyển|chai|lọ|hộp)\\s+)?(.{2,120}?)(?=\\s+(?:lên|ra|rồi|và|để|bỏ|cất|đưa|vào|cho)(?:\\s|[,.;!?]|$)|[,.;!?]|$)",
      "(?:^|[^\\p{L}\\p{N}_])(?:bỏ|cất|đưa|thu)\\s+(?:(?:một|cái|chiếc|con|quyển)\\s+)?(.{2,120}?)\\s+(?:vào|trong)\\s+(?:kho|omnivault|nhẫn vạn tàng|không gian lưu trữ)(?=[,.;!?]|$)"
    };
    for (String regex : direct) {
      String candidate = pickupRegexCandidateAndroid(action, regex);
      if (!candidate.isEmpty()) return candidate;
    }
    String[] introduced = new String[] {
      "(?:^|[^\\p{L}\\p{N}_])(?:thấy|nhìn thấy|phát hiện)\\s+(?:(?:một|cái|chiếc|con|quyển|chai|lọ|hộp)\\s+)?(.{2,120}?)(?=\\s+(?:dưới|trên|bên|cạnh|ở|nằm|trong|ngay)(?:\\s|[,.;!?]|$)|[,.;!?]|$)",
      "(?:^|[^\\p{L}\\p{N}_])(?:có)\\s+(?:(?:một|cái|chiếc|con|quyển|chai|lọ|hộp)\\s+)?(.{2,120}?)(?=\\s+(?:dưới|trên|bên|cạnh|ở|nằm|trong|ngay)(?:\\s|[,.;!?]|$)|[,.;!?]|$)"
    };
    for (String regex : introduced) {
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

  private boolean inventoryFuzzyHasAndroid(JSONObject state, String candidate) {
    JSONArray inventory = state != null ? state.optJSONArray("inventory") : null;
    if (inventory == null) return false;
    for (int i = 0; i < inventory.length(); i++) {
      String name = itemName(inventory.opt(i));
      if (!name.isEmpty() && pickupTokenOverlapAndroid(name, candidate) && pickupTokenOverlapAndroid(candidate, name)) return true;
    }
    return false;
  }

  private boolean replyConfirmsPickupAndroid(String candidate, String reply) {
    if (candidate == null || candidate.isEmpty() || reply == null || reply.trim().isEmpty()) return false;
    if (containsAny(reply, "không thể nhặt", "không nhặt được", "không thể lấy", "không lấy được", "không thể cất", "không cất được", "không tồn tại", "không có vật", "cannot pick", "cannot take", "cannot store")) return false;
    if (!containsAny(reply, "nhặt", "nhấc", "lấy", "cầm", "thu hồi", "cất", "bỏ vào", "đưa vào", "thu vào", "lưu trữ", "kho", "omnivault", "nhẫn vạn tàng", "store", "stored")) return false;
    return pickupTokenOverlapAndroid(candidate, reply);
  }

  private void reconcileConfirmedPickupOpsAndroid(JSONObject before, JSONObject generated, String action) throws Exception {
    String candidate = pickupCandidateAndroid(action);
    String reply = generated != null ? generated.optString("reply", "") : "";
    if (candidate.isEmpty() || !mundanePickupName(candidate) || !replyConfirmsPickupAndroid(candidate, reply) || inventoryFuzzyHasAndroid(before, candidate)) return;

    JSONArray ops = generated.optJSONArray("ops");
    if (ops == null) ops = new JSONArray();
    boolean matched = false;
    for (int i = 0; i < ops.length(); i++) {
      JSONObject op = ops.optJSONObject(i);
      if (op == null || !"inventory_upsert".equalsIgnoreCase(op.optString("type", ""))) continue;
      JSONObject item = op.optJSONObject("item");
      String name = item != null ? item.optString("name", "") : "";
      if (!name.isEmpty() && pickupTokenOverlapAndroid(name, candidate)) {
        op.put("basis", "gm_confirmed_pickup");
        matched = true;
        break;
      }
    }
    if (!matched) {
      ops.put(new JSONObject()
        .put("type", "inventory_upsert")
        .put("item", new JSONObject().put("name", candidate).put("quantity", 1).put("state", "STORED"))
        .put("basis", "gm_confirmed_pickup"));
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

old_repair = r'''            if (reply.isEmpty()) throw new Exception("AI repair trả phản hồi rỗng; state không được thay đổi.");
            repaired = true;
            candidateState = applyModelOperations(before, generated.optJSONArray("ops"), rolls, action);
'''
new_repair = r'''            if (reply.isEmpty()) throw new Exception("AI repair trả phản hồi rỗng; state không được thay đổi.");
            reconcileConfirmedPickupOpsAndroid(before, generated, action);
            repaired = true;
            candidateState = applyModelOperations(before, generated.optJSONArray("ops"), rolls, action);
'''
replace_once(old_repair, new_repair, "repair pickup reconciliation")

for required in [
    "pickupCandidateAndroid",
    "replyConfirmsPickupAndroid",
    "mundanePickupName",
    "gm_confirmed_pickup",
    "reconcileConfirmedPickupOpsAndroid(before, generated, action)",
]:
    if required not in text:
        raise RuntimeError(f"pickup reconciliation marker missing: {required}")

MAIN.write_text(text, encoding="utf-8")
print("Android pickup reconciliation enabled: GM-confirmed mundane pickups are converted into authoritative inventory ops; restricted loot remains gated.")
