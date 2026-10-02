package com.rabpit.backroom.core;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class NarrationProviderPolicyTest {
  @Test public void entityLifecycleBypassesWriterEntirely() throws Exception {
    for (String type : new String[] {"ENTITY_ENCOUNTER_STARTED", "COMBAT_VICTORY"}) {
      JSONArray views = new JSONArray().put(SafePresentationView.event(new JSONObject(), "cao_minh",
          new JSONObject().put("eventType", type).put("targetRefs", new JSONArray().put("async_rifleman"))));
      int[] calls = {0};
      JSONObject result = NarrationProviderPolicy.present(views, rejection -> {
        calls[0]++;
        throw new AssertionError("writer must not run for Core-owned Entity lifecycle");
      }, generated -> "");
      assertEquals(0, calls[0]);
      assertFalse(result.getString("reply").isEmpty());
    }
  }

  @Test public void nonEntitySpecialScenesUseWriterAndKeepLocalTemplatesForTransportFailure() throws Exception {
    for (String type : new String[] {"CHEST_SPAWNED", "CHEST_OPENED",
        "CHARACTER_ENCOUNTERED", "CHARACTER_REUNION"}) {
      JSONArray views = new JSONArray().put(SafePresentationView.event(new JSONObject(), "cao_minh",
          new JSONObject().put("eventType", type).put("targetRefs", new JSONArray().put("async_rifleman"))));
      int[] calls = {0};
      JSONObject result = NarrationProviderPolicy.present(views, rejection -> {
        calls[0]++;
        return new JSONObject().put("reply", "Writer presentation");
      }, generated -> "");
      assertEquals(1, calls[0]);
      assertEquals("Writer presentation", result.getString("reply"));

      calls[0] = 0;
      result = NarrationProviderPolicy.present(views, rejection -> {
        calls[0]++;
        throw new java.io.IOException("Provider unavailable");
      }, generated -> "");
      assertEquals(1, calls[0]);
      assertFalse(result.getString("reply").isEmpty());
    }
  }

  @Test public void creativeExploreUsesRealGuardAndOneProviderAttemptWhenValid() throws Exception {
    JSONObject state = GameCoreFacade.newGameState(new JSONObject());
    JSONObject evidence = new JSONObject().put("available", true).put("claims", new JSONArray());
    int[] calls = {0};
    NarrationProviderPolicy.present(new JSONArray(), rejection -> {
      calls[0]++;
      return new JSONObject().put("reply", "Một vệt sáng mảnh như laser nằm trên thảm; tiếng đèn lắng xuống.");
    }, generated -> NarrationGuard.validate(generated, state, evidence));
    assertEquals(1, calls[0]);
  }

  @Test public void formatErrorGetsOneBoundedRepair() throws Exception {
    JSONObject state = new JSONObject();
    JSONObject evidence = new JSONObject().put("available", true).put("claims", new JSONArray());
    int[] calls = {0, 0};
    String[] repairReason = {""};
    JSONObject result = NarrationProviderPolicy.present(new JSONArray(), rejection -> {
      calls[rejection.isEmpty() ? 0 : 1]++;
      if (rejection.isEmpty()) return new JSONObject().put("reply", "");
      repairReason[0] = rejection;
      return new JSONObject().put("reply", "Cao Minh dừng lại nghe tiếng đèn rung nhẹ.");
    }, generated -> NarrationGuard.validate(generated, state, evidence));
    assertEquals(1, calls[0]);
    assertEquals(1, calls[1]);
    assertTrue(repairReason[0].startsWith("FORMAT:"));
    assertEquals("Cao Minh dừng lại nghe tiếng đèn rung nhẹ.", result.getString("reply"));
  }

  @Test public void normalExploreHasOneInitialCallAndNoRepair() throws Exception {
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

  @Test public void authorityLeakCanRecoverWithOneRepairWithoutKnowledgeMutation() throws Exception {
    JSONObject state = new JSONObject();
    JSONObject evidence = new JSONObject().put("available", true).put("claims", new JSONArray());
    int[] calls = {0, 0};
    JSONObject result = NarrationProviderPolicy.present(new JSONArray(), rejection -> {
      calls[rejection.isEmpty() ? 0 : 1]++;
      if (rejection.isEmpty()) {
        return new JSONObject().put("reply", "Lucia dùng M4A1 có laser.").put("claims", new JSONArray());
      }
      assertTrue(rejection.startsWith("AUTHORITY:"));
      return new JSONObject().put("reply", "Hành lang vẫn im, chỉ còn tiếng điện rè.");
    }, generated -> NarrationGuard.validate(generated, state, evidence));
    assertEquals(1, calls[0]);
    assertEquals(1, calls[1]);
    assertFalse(result.toString().contains("M4A1"));
    assertEquals("{}", state.toString());
  }

  @Test public void secondValidationRejectionFallsBackAfterSingleRepair() throws Exception {
    int[] calls = {0, 0};
    JSONObject result = NarrationProviderPolicy.present(new JSONArray(), rejection -> {
      calls[rejection.isEmpty() ? 0 : 1]++;
      return new JSONObject().put("reply", "invalid");
    }, generated -> "AUTHORITY: hard rejection");
    assertEquals(1, calls[0]);
    assertEquals(1, calls[1]);
    assertEquals(2, calls[0] + calls[1]);
    assertFalse("invalid".equals(result.getString("reply")));
  }

  @Test public void transportErrorDoesNotStartContentRepairOrUnboundedRetry() throws Exception {
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
