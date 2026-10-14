package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

/** Checks provider payloads before a key/model is marked healthy or a GM turn is committed. */
public final class ProviderResponseContract {
  private ProviderResponseContract() {}

  public static void requireWriter(JSONObject packet) {
    Object reply = packet == null ? null : packet.opt("reply");
    JSONArray scenes = packet == null ? null : packet.optJSONArray("eventNarrations");
    // A scene-only response has no prelude. Content is validated by Core before committing.
    if (!(reply instanceof String) || (((String) reply).trim().isEmpty() &&
        (scenes == null || scenes.length() == 0))) {
      throw new IllegalArgumentException("GM provider JSON không có reply hợp lệ.");
    }
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
