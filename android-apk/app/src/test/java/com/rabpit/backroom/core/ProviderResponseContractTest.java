package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class ProviderResponseContractTest {
  @Test public void writerAcceptsUsableJsonReply() throws Exception {
    ProviderResponseContract.requireWriter(new JSONObject().put("reply", "Lượt hợp lệ").put("ops", new JSONArray()));
  }

  @Test public void sceneOnlyWriterReplyIsPassedToTheMilestoneGate() throws Exception {
    ProviderResponseContract.requireWriter(new JSONObject().put("reply", "")
      .put("eventNarrations", new JSONArray().put(new JSONObject().put("eventId", "event"))));
    assertThrows(IllegalArgumentException.class, () -> ProviderResponseContract.requireWriter(
      new JSONObject().put("reply", "").put("eventNarrations", new JSONArray())));
  }

  @Test public void writerRejectsMissingBlankAndNonStringReplies() throws Exception {
    for (JSONObject payload : new JSONObject[] {
        new JSONObject(),
        new JSONObject().put("reply", "   "),
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
