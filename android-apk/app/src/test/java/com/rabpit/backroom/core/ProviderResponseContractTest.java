package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class ProviderResponseContractTest {
  @Test public void writerAcceptsUsableJsonReply() throws Exception {
    ProviderResponseContract.requireWriter(new JSONObject().put("reply", "Lượt hợp lệ").put("ops", new JSONArray()));
  }

  @Test public void writerRejectsMissingBlankAndNonStringReplies() throws Exception {
    for (JSONObject payload : new JSONObject[] {
        new JSONObject(),
        new JSONObject().put("reply", "   "),
        new JSONObject().put("reply", "").put("eventNarrations", new JSONArray().put(new JSONObject().put("eventId", "retired"))),
        new JSONObject().put("reply", 42),
        new JSONObject().put("reply", JSONObject.NULL)
    }) {
      assertThrows(IllegalArgumentException.class, () -> ProviderResponseContract.requireWriter(payload));
    }
  }

  @Test public void directWriterAcceptsPlainNarration() throws Exception {
    JSONObject packet = ProviderResponseContract.normalizeDirectWriter("Cao Minh tiến vào hành lang tối.");
    assertEquals("Cao Minh tiến vào hành lang tối.", packet.getString("reply"));
    assertEquals(1, packet.length());
  }

  @Test public void directWriterAcceptsJsonAndFencedJson() throws Exception {
    JSONObject packet = ProviderResponseContract.normalizeDirectWriter("{\"reply\":\"Đèn chớp tắt.\"}");
    assertEquals("Đèn chớp tắt.", packet.getString("reply"));
    String fence = new String(new char[] {96, 96, 96});
    JSONObject fenced = ProviderResponseContract.normalizeDirectWriter(fence + "json\n{\"reply\":\"Cửa khép lại.\"}\n" + fence);
    assertEquals("Cửa khép lại.", fenced.getString("reply"));
  }

  @Test public void directWriterRejectsBlankAndBrokenJson() throws Exception {
    for (String text : new String[] {
      null, "", " ", "{}", "{\"reply\":\"chưa đóng", "{\"reply\":42}",
      "{\"reply\":\" \"}", "[{\"reply\":\"không phải object\"}]"
    }) {
      assertThrows(RuntimeException.class, () -> ProviderResponseContract.normalizeDirectWriter(text));
    }
  }

  @Test public void directWriterPreservesDecisionsAndNarrativeNotesButNeverStateOperations() throws Exception {
    JSONObject input = new JSONObject().put("reply", "Dấu vết có thể thuộc về một thanh kiếm.")
      .put("decision", new JSONObject().put("options", new JSONArray()
        .put(new JSONObject().put("label", "Điều tra").put("intent", "INVESTIGATE"))
        .put(new JSONObject().put("label", "Tránh xa").put("intent", "AVOID"))))
      .put("continuity", new JSONArray().put(new JSONObject().put("kind", "SUSPICION").put("text", "Dấu vết")))
      .put("state", new JSONObject().put("hp", 999)).put("ops", new JSONArray().put("spawn"));
    JSONObject out = ProviderResponseContract.normalizeDirectWriter(input.toString());
    assertEquals(2, out.getJSONObject("decision").getJSONArray("options").length());
    assertEquals(1, out.getJSONArray("continuity").length());
    assertFalse(out.has("state")); assertFalse(out.has("ops"));
    assertThrows(IllegalArgumentException.class, () -> ProviderResponseContract.normalizeDirectWriter(
      new JSONObject().put("reply", "Trang").put("decision", "invalid").toString()));
  }

  @Test public void malformedOptionalNotesReachCoreWithoutRejectingValidNarration() throws Exception {
    JSONObject packet = ProviderResponseContract.normalizeDirectWriter(
      new JSONObject().put("reply", "Cao Minh bước tiếp.").put("continuity", "invalid optional metadata").toString());
    assertEquals("Cao Minh bước tiếp.", packet.getString("reply"));
    assertEquals("invalid optional metadata", packet.getString("continuity"));
  }

  @Test public void auditUsesItsOwnPassAndIssuesContract() throws Exception {
    ProviderResponseContract.requireAudit(new JSONObject().put("pass", true).put("issues", new JSONArray()));
    ProviderResponseContract.requireAudit(new JSONObject().put("pass", false).put("issues",
        new JSONArray().put(new JSONObject().put("rule", "knowledge_leak"))));
  }

  @Test public void auditRejectsPayloadThatWouldSilentlyPassWithoutIssues() throws Exception {
    for (JSONObject payload : new JSONObject[] {
        new JSONObject(),
        new JSONObject().put("pass", true),
        new JSONObject().put("pass", "true").put("issues", new JSONArray()),
        new JSONObject().put("pass", false).put("issues", new JSONArray())
    }) {
      assertThrows(IllegalArgumentException.class, () -> ProviderResponseContract.requireAudit(payload));
    }
  }
}
