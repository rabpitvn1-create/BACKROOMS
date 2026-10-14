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
