package com.rabpit.backroom.core;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class SafePresentationViewTest {
  @Test public void unboundEquipmentAndHudNeverBecomeActorKnowledge() throws Exception {
    JSONObject state = new JSONObject().put("party", new JSONArray().put(new JSONObject()
        .put("id", "lucia").put("name", "Lucia Lục")
        .put("equipment", new JSONObject().put("lucia_m4a1", "M4A1 firearm laser"))))
        .put("combat", new JSONObject().put("entity", new JSONObject()
            .put("key", "async_rifleman").put("name", "ASYNC Rifleman")));
    String before = state.toString();
    String view = GmNarrativePacket.projectState(state).toString();
    assertFalse(view, view.contains("M4A1"));
    assertFalse(view, view.contains("lucia_m4a1"));
    assertFalse(view, view.contains("ASYNC"));
    assertTrue(view.contains("vật kim loại dài"));
    assertEquals(before, state.toString());
    assertFalse(CharacterKnowledge.knows(state, "cao_minh", "lucia_m4a1", "knownName"));
  }

  @Test public void onlyCoreKnowledgeUpdatesPermitExactNameOrObservedEffect() throws Exception {
    JSONObject state = new JSONObject();
    CharacterKnowledge.normalize(state);
    assertEquals("một vật kim loại dài", SafePresentationView.label(state, "cao_minh", "lucia_m4a1"));
    CharacterKnowledge.mark(state, "cao_minh", "lucia_m4a1", "knownEffect", "combat:observed");
    assertTrue(SafePresentationView.label(state, "cao_minh", "lucia_m4a1").contains("tốc độ rất cao"));
    CharacterKnowledge.mark(state, "cao_minh", "lucia_m4a1", "knownName", "core:disclosure");
    JSONObject loaded = new JSONObject(state.toString());
    CharacterKnowledge.normalize(loaded);
    assertEquals("M4A1", SafePresentationView.label(loaded, "cao_minh", "lucia_m4a1"));
    assertEquals("M4A1", SafePresentationView.text(loaded, "cao_minh", "M4A1"));
    assertFalse(SafePresentationView.text(loaded, "cao_minh", "lucia_m4a1").contains("lucia_m4a1"));
  }

  @Test public void knownBeforeIsActorSpecificAndDoesNotImportWikiOrForeignHistory() throws Exception {
    JSONObject state = new JSONObject();
    CharacterKnowledge.normalize(state);
    assertEquals("Lục Trầm", SafePresentationView.label(state, "cao_minh", "luc_tram"));
    assertEquals("Cao Minh", SafePresentationView.label(state, "luc_tram", "cao_minh"));
    assertFalse(CharacterKnowledge.knows(state, "luc_tram", "cao_minh_title", "knownName"));
    assertFalse(SafePresentationView.text(state, "lucia", "Đại Đạo Ma Tôn").contains("Đại Đạo Ma Tôn"));
    assertFalse(SafePresentationView.text(state, "syvial", "Đại Đạo Ma Tôn tu tiên").contains("tu tiên"));
    assertFalse(CharacterKnowledge.knows(state, "syvial", "cultivation", "knownName"));
  }

  @Test public void finalPacketFiltersEveryComposedChannelAndGuardRejectsLeaks() throws Exception {
    JSONObject state = new JSONObject().put("party", new JSONArray().put(new JSONObject().put("id", "lucia")));
    String leak = "M4A1 lucia_m4a1 laser firearm riflewoman gun Đại Đạo Ma Tôn";
    JSONObject evidence = new JSONObject().put("available", true).put("claims", new JSONArray())
        .put("events", new JSONArray().put(new JSONObject().put("evidenceText", leak)));
    String request = GmNarrativePacket.build(leak, leak, leak, leak, leak, state, leak, leak, leak, evidence);
    for (String term : new String[] {"M4A1", "lucia_m4a1", "laser", "firearm", "riflewoman", "gun", "Đại Đạo Ma Tôn"}) {
      assertFalse(term + " leaked", request.contains(term));
    }
    JSONObject generated = new JSONObject().put("reply", "Lucia gọi hắn là Đại Đạo Ma Tôn.")
        .put("choices", new JSONArray()).put("encounterDialogue", new JSONArray()).put("claims", new JSONArray());
    assertFalse(NarrationGuard.validate(generated, state, evidence).isEmpty());
    state.put("party", new JSONArray());
    generated.put("reply", "Cao Minh quan sát.").put("choices",
        new JSONArray().put(new JSONObject().put("text", "Cầm M4A1 lên")));
    assertEquals("Managed knowledge term in choice.", NarrationGuard.validate(generated, state, evidence));
    generated.put("choices", new JSONArray());
    generated.put("reply", "Một M4A1 nằm trước mặt.");
    assertFalse(NarrationGuard.validate(generated, state, evidence).isEmpty());
    CharacterKnowledge.mark(state, "cao_minh", "lucia_m4a1", "knownName", "core:disclosure");
    assertEquals("", NarrationGuard.validate(generated, state, evidence));
    assertTrue(GmNarrativePacket.build("", "", "", "", "M4A1", state, "", "").contains("M4A1"));
    assertFalse(state.toString().contains("knownEffect"));
  }

  @Test public void knownNamesAreStableUnderRepeatedProjection() throws Exception {
    JSONObject state = new JSONObject();
    CharacterKnowledge.normalize(state);
    assertEquals("Lucia Lục", SafePresentationView.text(state, "lucia", "Lucia Lục"));
    String view = SafePresentationView.text(state, "cao_minh", "lucia_m4a1 M4A1 ASYNC Rifleman");
    assertEquals(view, SafePresentationView.text(state, "cao_minh", view));
  }

  @Test public void eventProjectionIsReadOnlyAndPreservesRangedApproach() throws Exception {
    JSONObject state = new JSONObject().put("currentLevelKey", "1");
    JSONObject event = new JSONObject().put("eventId", "t:e1")
        .put("eventType", "ENTITY_ENCOUNTER_STARTED")
        .put("targetRefs", new JSONArray().put("async_rifleman"));
    String before = state.toString();
    JSONObject view = SafePresentationView.event(state, "cao_minh", event);
    assertEquals("hold_distance", view.getString("approachStyle"));
    assertFalse(view.toString().contains("async_rifleman"));
    assertEquals(before, state.toString());
  }
}
