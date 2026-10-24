package com.rabpit.backroom;

import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Test;
import static org.junit.Assert.*;

public class AppDiagnosticLogTest {
  @Test public void onlySafeMachineErrorCodesAreExported() {
    assertEquals("core_save_decode_failed",
      AppDiagnosticLog.safeCode("core_save_decode_failed"));
    assertEquals("details_redacted",
      AppDiagnosticLog.safeCode("API_KEY=private-content"));
    assertEquals("details_redacted",
      AppDiagnosticLog.safeCode("https://provider.example/v1?key=secret"));
  }

  @Test public void uiSummaryOmitsNarrativeAndApiKeysAndCapsEvents() throws Exception {
    JSONObject input = new JSONObject()
      .put("hasState", true)
      .put("combatActive", false)
      .put("turn", 14)
      .put("levelNodeId", "LEVEL.NODE.0")
      .put("apiKey", "very-secret-value")
      .put("rawSave", "{very-secret-value}")
      .put("chatLog", "very-secret-value")
      .put("startupErrorCode", "invalid input with secret");
    JSONArray events = new JSONArray();
    for (int i = 0; i < 95; i++) {
      events.put(new JSONObject().put("stage", "javascript").put("code", "uncaught_exception")
        .put("source", "index.html").put("line", i).put("message", "very-secret-value"));
    }
    input.put("events", events);
    JSONObject output = AppDiagnosticLog.safeUiSummary(input.toString());
    assertEquals(14, output.getInt("turn"));
    assertEquals("LEVEL.NODE.0", output.getString("levelNodeId"));
    assertEquals("details_redacted", output.getString("startupErrorCode"));
    assertEquals(80, output.getJSONArray("events").length());
    assertEquals(15, output.getJSONArray("events").getJSONObject(0).getInt("line"));
    assertEquals("index.html",
      output.getJSONArray("events").getJSONObject(0).getString("script"));
    assertFalse(output.toString().contains("very-secret-value"));
    assertFalse(output.has("rawSave"));
    assertFalse(output.has("chatLog"));
  }

  @Test public void coreSummaryExportsStructureAndNotRawGameState() throws Exception {
    JSONObject core = new JSONObject()
      .put("saveVersion", 3)
      .put("world", new JSONObject()
        .put("levelTravel", new JSONObject().put("nodeId", "LEVEL.NODE.3")
          .put("runId", "secret-run-id").toString())
        .put("levelTravelUiTurn", "12")
        .put("flagsJson", "secret internal narrative"))
      .put("party", new JSONObject().put("memberIds", new JSONArray().put("cao-minh")))
      .put("characters", new JSONObject()
        .put("cao-minh", new JSONObject().put("description", "secret character info")))
      .put("statuses", new JSONObject());
    JSONObject safe = AppDiagnosticLog.safeCoreSummary(core.toString());
    assertEquals(3, safe.getInt("saveVersion"));
    assertEquals(12, safe.getInt("levelTurn"));
    assertEquals("LEVEL.NODE.3", safe.getString("levelNodeId"));
    assertEquals(1, safe.getInt("characterCount"));
    assertEquals(1, safe.getInt("partySize"));
    assertFalse(safe.toString().contains("secret"));
  }
}
