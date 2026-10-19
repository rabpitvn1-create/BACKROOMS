package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

/** Checks provider payloads before a key/model is marked healthy or a GM turn is committed. */
public final class ProviderResponseContract {
  private ProviderResponseContract() {}

  public static final class Failure extends IllegalArgumentException {
    public final String code;
    public final boolean retryable;
    public Failure(String code, boolean retryable) { super(code); this.code = code; this.retryable = retryable; }
  }

  public static String failureCode(Throwable error) {
    if (error instanceof Failure) return ((Failure) error).code;
    if (error instanceof java.net.SocketTimeoutException) return "writer_timeout";
    if (error instanceof java.io.IOException) return "writer_connection";
    String code = error.getMessage();
    if (code != null && code.matches("(?:scene|canon|narrative|player)_[a-z0-9_]{2,70}")) return code;
    return error instanceof IllegalArgumentException ? "writer_content_invalid" : "writer_processing_failed";
  }
  public static boolean retryable(Throwable error) {
    if (error instanceof Failure) return ((Failure) error).retryable;
    return error instanceof java.io.IOException;
  }

  /** Extract only a completed text response. Never log the provider body. */
  public static String completedText(String raw) {
    final JSONObject envelope;
    try { envelope = new JSONObject(raw); }
    catch (Exception error) { throw new Failure("writer_envelope_invalid", true); }
    JSONArray choices = envelope.optJSONArray("choices");
    JSONObject choice = choices == null ? null : choices.optJSONObject(0);
    if (choice == null) throw new Failure("writer_choice_missing", true);
    String finish = choice.optString("finish_reason");
    if ("length".equals(finish)) throw new Failure("writer_truncated", true);
    if ("content_filter".equals(finish)) throw new Failure("writer_filtered", false);
    if (!"stop".equals(finish)) throw new Failure("writer_finish_invalid", false);
    JSONObject message = choice.optJSONObject("message");
    if (message == null) throw new Failure("writer_message_missing", true);
    Object content = message.opt("content");
    StringBuilder text = new StringBuilder();
    if (content instanceof String) text.append((String) content);
    else if (content instanceof JSONArray) {
      JSONArray parts = (JSONArray) content;
      for (int i = 0; i < parts.length(); i++) {
        JSONObject part = parts.optJSONObject(i);
        if (part != null && part.opt("text") instanceof String) {
          if (text.length() > 0) text.append('\n');
          text.append(part.optString("text"));
        }
      }
    }
    if (text.toString().trim().isEmpty()) throw new Failure("writer_empty", true);
    return text.toString();
  }

  /** Scene narration is strict JSON; no prose normalization or world-state operations. */
  public static JSONObject parseSceneWriter(String raw) throws Exception {
    if (raw == null || raw.trim().isEmpty()) throw new Failure("writer_empty", true);
    if (raw.length() > 16000) throw new Failure("writer_size", false);
    final JSONObject packet;
    try { packet = MiniMapScenario.INSTANCE.parse(raw); }
    catch (Exception error) { throw new Failure("writer_json_invalid", false); }
    Object reply = packet.opt("reply");
    if (!(reply instanceof String) || ((String) reply).trim().isEmpty())
      throw new Failure("writer_reply_invalid", false);
    if (((String) reply).length() > 12000) throw new Failure("writer_reply_size", false);
    if (packet.length() != 1) throw new Failure("writer_fields_invalid", false);
    return packet;
  }

  public static void requireWriter(JSONObject packet) {
    Object reply = packet == null ? null : packet.opt("reply");
    if (!(reply instanceof String) || ((String) reply).trim().isEmpty()) {
      throw new IllegalArgumentException("GM provider JSON không có reply hợp lệ.");
    }
  }

  /** Normalize only direct GM narration; Core remains authoritative for effects. */
  public static JSONObject normalizeDirectWriter(String raw) throws org.json.JSONException {
    if (raw == null || raw.trim().isEmpty())
      throw new IllegalArgumentException("GM provider không trả nội dung.");
    String text = raw.trim();
    if (text.startsWith("```")) {
      int firstNewline = text.indexOf('\n');
      int closingFence = text.lastIndexOf("```");
      if (firstNewline < 0 || closingFence <= firstNewline)
        throw new IllegalArgumentException("GM provider trả code fence không hợp lệ.");
      text = text.substring(firstNewline + 1, closingFence).trim();
    }
    if (text.isEmpty()) throw new IllegalArgumentException("GM provider không trả lời kể.");
    if (text.startsWith("{") || text.startsWith("[") || text.startsWith("\"reply\"")) {
      // A structured response must be valid JSON with a non-empty reply.
      JSONObject packet = new JSONObject(text);
      requireWriter(packet);
      String reply = packet.optString("reply", "").trim();
      if (reply.isEmpty()) throw new IllegalArgumentException("GM provider JSON không có reply hợp lệ.");
      return new JSONObject().put("reply", reply);
    }
    // No game-state operations can originate from prose. Prose is GM narration only.
    JSONObject packet = new JSONObject().put("reply", text);
    requireWriter(packet);
    return packet;
  }

  public static void requireAudit(JSONObject packet) {
    Object pass = packet == null ? null : packet.opt("pass");
    JSONArray issues = packet == null ? null : packet.optJSONArray("issues");
    if (!(pass instanceof Boolean) || issues == null) {
      throw new IllegalArgumentException("Auditor JSON không có pass/issues hợp lệ.");
    }
    if (Boolean.FALSE.equals(pass) && issues.length() == 0) {
      throw new IllegalArgumentException("Auditor báo fail nhưng không cung cấp issue.");
    }
  }
}
