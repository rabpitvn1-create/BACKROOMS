package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

/** Checks provider payloads before a key/model is marked healthy or a GM turn is committed. */
public final class ProviderResponseContract {
  private ProviderResponseContract() {}

  /** Scene narration is strict JSON; no prose normalization or world-state operations. */
  public static JSONObject parseSceneWriter(String raw) throws Exception {
    if (raw == null || raw.length() > 12000) throw new IllegalArgumentException("scene_narration_size");
    JSONObject packet = MiniMapScenario.INSTANCE.parse(raw);
    requireWriter(packet);
    if (packet.length() != 1) throw new IllegalArgumentException("scene_narration_fields");
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
