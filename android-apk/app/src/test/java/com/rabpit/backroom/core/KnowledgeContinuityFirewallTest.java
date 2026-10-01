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

  @Test public void knownProvenanceRequiresReferenceAndUnknownGrantsNoKnowledge() throws Exception {
    for (KnowledgeContinuityFirewall.KnowledgeState kind
        : KnowledgeContinuityFirewall.KnowledgeState.values()) {
      JSONObject claim = binding("POV/BELIEF", kind.wireName, "luc_tram", "LIVE_STATE")
          .put("evidenceRef", "");
      if (kind == KnowledgeContinuityFirewall.KnowledgeState.UNKNOWN) {
        assertTrue(KnowledgeContinuityFirewall.validate(claim).isEmpty());
      } else {
        assertEquals("evidence_ref_required", KnowledgeContinuityFirewall.validate(claim));
      }
      assertFalse(KnowledgeContinuityFirewall.canExposeToActor(claim, "luc_tram"));
      assertFalse(KnowledgeContinuityFirewall.canSupplyCurrentState(claim));
    }
  }

  @Test public void actorProjectionRejectsHiddenAndForeignBindingsWithoutChangingSave() throws Exception {
    JSONObject belief = new JSONObject().put("actorId", "lucia").put("claimId", "c1")
        .put("beliefValue", "subjective").put("confidence", "CONFIRMED")
        .put("writerSecret", "hidden").put("confirmedFactId", "unvalidated");
    String before = belief.toString();
    JSONObject view = KnowledgeContinuityFirewall.actorBeliefView(belief, "lucia");
    assertEquals("ACTOR_BELIEF", view.getString("truthRole"));
    assertEquals("UNKNOWN", view.getJSONObject("knowledgeBinding").getString("knowledgeState"));
    assertFalse(view.has("writerSecret"));
    assertFalse(view.has("confirmedFactId"));
    assertEquals(before, belief.toString());
    assertTrue(KnowledgeContinuityFirewall.actorBeliefView(belief, "luc_tram") == null);
    for (String kind : new String[] {"WRITER-SECRET", "OPEN/UNKNOWN"}) {
      belief.put("knowledgeBinding", binding(kind, "UNKNOWN", "lucia", "BASELINE_CANON"));
      assertTrue(KnowledgeContinuityFirewall.actorBeliefView(belief, "lucia") == null);
    }
    belief.put("knowledgeBinding", binding("POV/BELIEF", "TOLD", "luc_tram", "LIVE_STATE"));
    assertTrue(KnowledgeContinuityFirewall.actorBeliefView(belief, "lucia") == null);
  }

  @Test public void structuredBeliefPayloadCannotSmuggleWriterKnowledge() throws Exception {
    JSONObject belief = new JSONObject().put("actorId", "cao_minh")
        .put("beliefValue", new JSONObject().put("writerSecret", "hidden"));
    assertTrue(KnowledgeContinuityFirewall.actorBeliefView(belief, "cao_minh") == null);
    JSONObject forged = binding("POV/BELIEF", "VERIFIED", "cao_minh", "LIVE_STATE")
        .put("evidenceRef", new JSONObject().put("secret", "hidden"));
    assertEquals("evidence_ref_invalid", KnowledgeContinuityFirewall.validate(forged));
    assertFalse(KnowledgeContinuityFirewall.canExposeToActor(forged, "cao_minh"));
  }

  private static JSONObject binding(
      String canonClass, String knowledgeState, String actorId, String originLayer) throws Exception {
    return new JSONObject()
        .put("schemaVersion", KnowledgeContinuityFirewall.CONTRACT_VERSION)
        .put("canonClass", canonClass)
        .put("knowledgeState", knowledgeState)
        .put("actorId", actorId)
        .put("originLayer", originLayer)
        .put("evidenceRef", "source:test");
  }
}
