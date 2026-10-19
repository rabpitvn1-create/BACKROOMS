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
  private String envelope(String finish, Object content) throws Exception {
    return new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("finish_reason", finish)
      .put("message", new JSONObject().put("content", content)))).toString();
  }
  @Test public void sceneWriterAcceptsShortOrMultilineValidJsonButRejectsBrokenEmptyOrGameplayFields() throws Exception {
    assertEquals("Đèn tắt.", ProviderResponseContract.parseSceneWriter("{\"reply\":\"Đèn tắt.\"}").getString("reply"));
    String prose = "Tiếng gọi vang xa.\n\nKhông có lời đáp.";
    assertEquals(prose, ProviderResponseContract.parseSceneWriter(new JSONObject().put("reply", prose).toString()).getString("reply"));
    for (String raw : new String[]{"", "{}", "{\"reply\":42}", "{\"reply\":\" \"}",
        "{\"reply\":\"chưa khép", "{\"reply\":\"Mở cửa\",\"ops\":[]}"})
      assertThrows(ProviderResponseContract.Failure.class, () -> ProviderResponseContract.parseSceneWriter(raw));
  }
  @Test public void sceneWriterAcceptsOnlyExactlyFencedJsonWithoutAdditionalFields() throws Exception {
    String fence = "```";
    assertEquals("Cửa khép lại.",
      ProviderResponseContract.parseSceneWriter(fence + "json\n{\"reply\":\"Cửa khép lại.\"}\n" + fence)
        .getString("reply"));
    for (String invalid : new String[] {
      fence + "json\n{\"reply\":\"OK\"}\n" + fence + " extra",
      fence + "python\n{\"reply\":\"OK\"}\n" + fence,
      fence + "json\n{\"reply\":\"OK\",\"ops\":[]}\n" + fence,
      fence + "json\n{\"reply\":\"unterminated\n" + fence
    }) assertThrows(ProviderResponseContract.Failure.class,
      () -> ProviderResponseContract.parseSceneWriter(invalid));
  }

  @Test public void envelopeSeparatesTruncationFilterMalformedAndEmptyFromValidShortReply() throws Exception {
    assertEquals("Gọn nhưng hợp lệ.", ProviderResponseContract.completedText(envelope("stop", "Gọn nhưng hợp lệ.")));
    ProviderResponseContract.Failure cut = assertThrows(ProviderResponseContract.Failure.class,
      () -> ProviderResponseContract.completedText(envelope("length", "{\"reply\":\"dở")));
    assertEquals("writer_truncated", cut.code); assertTrue(cut.retryable);
    ProviderResponseContract.Failure filtered = assertThrows(ProviderResponseContract.Failure.class,
      () -> ProviderResponseContract.completedText(envelope("content_filter", "")));
    assertFalse(filtered.retryable);
    for (String raw : new String[]{"not JSON", "{}", envelope("stop", ""), envelope("tool_calls", "text")})
      assertThrows(ProviderResponseContract.Failure.class, () -> ProviderResponseContract.completedText(raw));
  }
  @Test public void transportAndContractFailuresAreDistinctWithoutExposingProviderMessages() {
    Exception socket = new java.net.SocketException("private endpoint / key must not leak");
    assertEquals("writer_connection", ProviderResponseContract.failureCode(socket));
    assertTrue(ProviderResponseContract.retryable(socket));
    assertEquals("writer_timeout", ProviderResponseContract.failureCode(new java.net.SocketTimeoutException()));
    assertEquals("writer_content_invalid", ProviderResponseContract.failureCode(new IllegalArgumentException("sensitive text")));
    assertFalse(ProviderResponseContract.retryable(new IllegalArgumentException("sensitive text")));
  }
}
