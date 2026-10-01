package com.rabpit.backroom.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class KnowledgeContinuityFirewallTest {
  @Test public void dynamicCurrentStateRequiresLiveStateOrigin() throws Exception {
    JSONObject baseline = binding("DYNAMIC", "OBSERVED", "luc_tram", "BASELINE_CANON");

    assertEquals("dynamic_requires_live_state",
        KnowledgeContinuityFirewall.validate(baseline));
    assertFalse(KnowledgeContinuityFirewall.canSupplyCurrentState(baseline));
  }

  @Test public void liveDynamicStateMaySupplyCurrentContinuityOnlyToItsActor() throws Exception {
    JSONObject live = binding("DYNAMIC", "OBSERVED", "luc_tram", "LIVE_STATE")
        .put("evidenceRef", "turn-42:e3");

    assertTrue(KnowledgeContinuityFirewall.validate(live).isEmpty());
    assertTrue(KnowledgeContinuityFirewall.canSupplyCurrentState(live));
    assertTrue(KnowledgeContinuityFirewall.canExposeToActor(live, "luc_tram"));
    assertFalse(KnowledgeContinuityFirewall.canExposeToActor(live, "cao_minh"));
  }

  @Test public void openUnknownCannotBePromotedIntoKnownCanon() throws Exception {
    JSONObject open = binding("OPEN/UNKNOWN", "VERIFIED", "cao_minh", "BASELINE_CANON");

    assertEquals("open_unknown_must_remain_unknown",
        KnowledgeContinuityFirewall.validate(open));
    assertFalse(KnowledgeContinuityFirewall.canExposeToActor(open, "cao_minh"));
  }

  @Test public void writerSecretNeverBecomesAutomaticCharacterKnowledge() throws Exception {
    JSONObject secret = binding("WRITER-SECRET", "KNOWN-BEFORE", "luc_tram", "BASELINE_CANON");

    assertEquals("writer_secret_not_actor_knowledge",
        KnowledgeContinuityFirewall.validate(secret));
    assertFalse(KnowledgeContinuityFirewall.canExposeToActor(secret, "luc_tram"));
  }

  @Test public void povBeliefIsActorScopedAndNeverCurrentStateAuthority() throws Exception {
    JSONObject belief = binding("POV/BELIEF", "INFERRED", "luc_tram", "LIVE_STATE");

    assertTrue(KnowledgeContinuityFirewall.validate(belief).isEmpty());
    assertTrue(KnowledgeContinuityFirewall.canExposeToActor(belief, "luc_tram"));
    assertFalse(KnowledgeContinuityFirewall.canExposeToActor(belief, "cao_minh"));
    assertFalse(KnowledgeContinuityFirewall.canSupplyCurrentState(belief));
  }

  @Test public void baselineSelfCanonMayBeKnownButCannotOverwriteLiveState() throws Exception {
    JSONObject baseline = binding("SELF-CANON", "KNOWN-BEFORE", "cao_minh", "BASELINE_CANON");

    assertTrue(KnowledgeContinuityFirewall.validate(baseline).isEmpty());
    assertTrue(KnowledgeContinuityFirewall.canExposeToActor(baseline, "cao_minh"));
    assertFalse(KnowledgeContinuityFirewall.canSupplyCurrentState(baseline));
  }

  private static JSONObject binding(
      String canonClass, String knowledgeState, String actorId, String originLayer) throws Exception {
    return new JSONObject()
        .put("schemaVersion", KnowledgeContinuityFirewall.CONTRACT_VERSION)
        .put("canonClass", canonClass)
        .put("knowledgeState", knowledgeState)
        .put("actorId", actorId)
        .put("originLayer", originLayer)
        .put("evidenceRef", "");
  }
}
