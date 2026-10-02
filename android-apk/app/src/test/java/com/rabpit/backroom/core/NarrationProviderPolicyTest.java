package com.rabpit.backroom.core;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class NarrationProviderPolicyTest {
  @Test public void committedSpecialScenesUseWriterOnceAndKeepLocalTemplatesForFailure() throws Exception {
    for (String type : new String[] {"ENTITY_ENCOUNTER_STARTED", "COMBAT_VICTORY", "CHEST_SPAWNED",
        "CHEST_OPENED", "CHARACTER_ENCOUNTERED", "CHARACTER_REUNION"}) {
      JSONArray views = new JSONArray().put(SafePresentationView.event(new JSONObject(), "cao_minh",
          new JSONObject().put("eventType", type).put("targetRefs", new JSONArray().put("async_rifleman"))));
      int[] calls = {0};
      JSONObject result = NarrationProviderPolicy.present(views, rejection -> {
        calls[0]++;
        return new JSONObject().put("reply", "Writer presentation");
      }, generated -> "");
      assertEquals(1, calls[0]);
      assertEquals("Writer presentation", result.getString("reply"));
      result = NarrationProviderPolicy.present(views, rejection -> {
        throw new java.io.IOException("Provider unavailable");
      }, generated -> "");
      assertFalse(result.getString("reply").isEmpty());
    }
  }

  @Test public void creativeExploreUsesRealGuardAndOneProviderAttempt() throws Exception {
    JSONObject state = GameCoreFacade.newGameState(new JSONObject());
    JSONObject evidence = new JSONObject().put("available", true).put("claims", new JSONArray());
    int[] calls = {0};
    NarrationProviderPolicy.present(new JSONArray(), rejection -> {
      calls[0]++;
      return new JSONObject().put("reply", "Một vệt sáng mảnh như laser nằm trên thảm; tiếng đèn lắng xuống.");
    }, generated -> NarrationGuard.validate(generated, state, evidence));
    assertEquals(1, calls[0]);
  }

  @Test public void formatErrorDoesNotTriggerAuthorityRepair() throws Exception {
    JSONObject state = new JSONObject();
    JSONObject evidence = new JSONObject().put("available", true).put("claims", new JSONArray());
    int[] calls = {0};
    NarrationProviderPolicy.present(new JSONArray(), rejection -> {
      calls[0]++;
      return new JSONObject().put("reply", "");
    }, generated -> NarrationGuard.validate(generated, state, evidence));
    assertEquals(1, calls[0]);
  }

  @Test public void normalExploreHasOneInitialCallAndNoRetry() throws Exception {
    int[] calls = {0, 0};
    JSONObject result = NarrationProviderPolicy.present(new JSONArray(), rejection -> {
      calls[rejection.isEmpty() ? 0 : 1]++;
      return new JSONObject().put("reply", "Cao Minh đi tiếp.");
    }, generated -> "");
    assertEquals(1, calls[0]);
    assertEquals(0, calls[1]);
    assertEquals(1, calls[0] + calls[1]);
    assertEquals("Cao Minh đi tiếp.", result.getString("reply"));
  }

  @Test public void managedLeakGetsOneRetryThenSafeFallbackWithoutKnowledgeMutation() throws Exception {
    JSONObject state = new JSONObject();
    JSONObject evidence = new JSONObject().put("available", true).put("claims", new JSONArray());
    int[] calls = {0, 0};
    JSONObject result = NarrationProviderPolicy.present(new JSONArray(), rejection -> {
      calls[rejection.isEmpty() ? 0 : 1]++;
      return new JSONObject().put("reply", "Lucia dùng M4A1 có laser.").put("claims", new JSONArray());
    }, generated -> NarrationGuard.validate(generated, state, evidence));
    assertEquals(1, calls[0]);
    assertEquals(1, calls[1]);
    assertEquals(2, calls[0] + calls[1]);
    assertFalse(result.toString().contains("M4A1"));
    assertEquals("{}", state.toString());
  }

  @Test public void validRetryIsAcceptedWithinTheSameTwoCallBudget() throws Exception {
    int[] calls = {0, 0};
    JSONObject result = NarrationProviderPolicy.present(new JSONArray(), rejection -> {
      calls[rejection.isEmpty() ? 0 : 1]++;
      return new JSONObject().put("reply", rejection.isEmpty() ? "invalid" : "Cao Minh đi tiếp.");
    }, generated -> "invalid".equals(generated.optString("reply")) ? "AUTHORITY: hard rejection" : "");
    assertEquals(1, calls[0]);
    assertEquals(1, calls[1]);
    assertEquals(2, calls[0] + calls[1]);
    assertEquals("Cao Minh đi tiếp.", result.getString("reply"));
  }

  @Test public void transportErrorDoesNotStartAnUnboundedRetryOrProviderFallback() throws Exception {
    int[] calls = {0, 0};
    JSONObject result = NarrationProviderPolicy.present(new JSONArray(), rejection -> {
      calls[rejection.isEmpty() ? 0 : 1]++;
      throw new java.io.IOException("provider unavailable");
    }, generated -> "");
    assertEquals(1, calls[0]);
    assertEquals(0, calls[1]);
    assertEquals(1, calls[0] + calls[1]);
    assertFalse(result.getString("reply").isEmpty());
  }
}
