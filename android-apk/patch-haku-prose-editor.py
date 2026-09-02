from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


main = MAIN.read_text(encoding="utf-8")

helper = r'''  private String postJsonHaku(JSONObject payload) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL("https://api.vilao.ai/v1/chat/completions").openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(30000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty("Accept", "application/json");
    connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.HAKU_API_KEY);
    try (OutputStream output = connection.getOutputStream()) {
      output.write(payload.toString().getBytes("UTF-8"));
    }

    int status = connection.getResponseCode();
    InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
    StringBuilder response = new StringBuilder();
    if (stream != null) {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
        String line;
        while ((line = reader.readLine()) != null) response.append(line);
      }
    }
    connection.disconnect();
    if (status < 200 || status >= 300) throw new HttpError(status, "Haku editor HTTP " + status);
    return response.toString();
  }

  private String hakuPolishReply(String original) {
    String source = original == null ? "" : original.trim();
    if (source.isEmpty()) return source;
    if (BuildConfig.HAKU_API_KEY == null || BuildConfig.HAKU_API_KEY.trim().isEmpty()) return source;

    try {
      String instruction =
        "Bạn là biên tập viên hậu kỳ tiếng Việt. INPUT đã chốt nội dung và sự kiện. " +
        "Nhiệm vụ duy nhất là sửa cách diễn đạt để tự nhiên, rõ, mạch lạc và dễ đọc hơn. " +
        "Sửa câu lủng củng, lặp từ/lặp ý không mang thông tin, câu cụt hoặc nhịp máy móc và dấu câu khi cần. " +
        "Giữ nguyên POV và xưng hô. Tuyệt đối không thêm sự kiện, thông tin, giải thích hay chi tiết mới; " +
        "không xóa thông tin có ý nghĩa; không đổi tên riêng, con số, vật phẩm, địa điểm, quan hệ, kết quả hoặc ý nghĩa lời thoại; " +
        "không tóm tắt, không tiếp tục câu chuyện, không bình luận về văn bản. Chỉ trả lại văn bản đã biên tập, không markdown.";

      JSONArray messages = new JSONArray()
        .put(new JSONObject().put("role", "system").put("content", instruction))
        .put(new JSONObject().put("role", "user").put("content", source));
      JSONObject body = new JSONObject()
        .put("model", "claude-haiku-4-5-20251001")
        .put("messages", messages)
        .put("temperature", 0.15)
        .put("max_tokens", 2200)
        .put("stream", false);

      JSONObject result = new JSONObject(postJsonHaku(body));
      JSONArray choices = result.optJSONArray("choices");
      JSONObject first = choices != null ? choices.optJSONObject(0) : null;
      JSONObject message = first != null ? first.optJSONObject("message") : null;
      String edited = message != null ? message.optString("content", "").trim() : "";
      if (edited.isEmpty()) return source;

      if (edited.startsWith("```") && edited.endsWith("```")) {
        int firstNewline = edited.indexOf('\n');
        if (firstNewline >= 0) edited = edited.substring(firstNewline + 1, edited.length() - 3).trim();
      }
      if (edited.isEmpty()) return source;
      int runawayLimit = Math.max(source.length() * 2, source.length() + 1200);
      if (edited.length() > runawayLimit) return source;
      return edited;
    } catch (Exception ignored) {
      // Editing is presentation-only. Provider failure must never fail or reroll a gameplay turn.
      return source;
    }
  }

'''

helper_anchor = "  private String geminiText(String prompt) throws Exception {\n"
if "private String hakuPolishReply(" not in main:
    main = replace_once(main, helper_anchor, helper + helper_anchor, "Haku prose editor helper")

# Standard GM path: all gameplay/canon validation has already completed. Only the
# final player-facing reply is polished; state/ops never pass through Haku.
standard_anchor = '''          log.put(new JSONObject().put("role", "player").put("text", action));
          log.put(new JSONObject().put("role", "gm").put("text", reply));
'''
standard_replacement = '''          log.put(new JSONObject().put("role", "player").put("text", action));
          reply = hakuPolishReply(reply);
          log.put(new JSONObject().put("role", "gm").put("text", reply));
'''
main = replace_once(main, standard_anchor, standard_replacement, "standard GM prose edit boundary")

# Registered-Level narration validates Gemini claims first. Polish only the free
# prose before exact surfaced evidence text is appended, so evidence highlighting
# and authoritative visible facts stay byte-for-byte untouched.
registered_anchor = '''      StringBuilder grounded = new StringBuilder(reply);
'''
registered_replacement = '''      reply = hakuPolishReply(reply);
      StringBuilder grounded = new StringBuilder(reply);
'''
main = replace_once(main, registered_anchor, registered_replacement, "registered narrative prose edit boundary")

for marker in [
    "BuildConfig.HAKU_API_KEY",
    '"claude-haiku-4-5-20251001"',
    "reply = hakuPolishReply(reply);",
    "setReadTimeout(30000)",
    "Editing is presentation-only",
]:
    if marker not in main:
        raise RuntimeError("Haku prose editor marker missing: " + marker)

if main.count("reply = hakuPolishReply(reply);") != 2:
    raise RuntimeError("Haku prose editor must run at exactly two final Gemini prose boundaries")

MAIN.write_text(main, encoding="utf-8")
print("Haku prose editor enabled after Gemini: presentation-only, fail-open, 30s read window, state/ops untouched.")
