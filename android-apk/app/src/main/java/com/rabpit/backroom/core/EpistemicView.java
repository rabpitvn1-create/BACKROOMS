package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

/** Builds a narrator-safe actor view. Objective hidden state is never copied by default. */
final class EpistemicView {
  private EpistemicView() {}

  static JSONObject forActor(JSONObject state, String actorId) throws Exception {
    JSONObject output = new JSONObject();
    if (state == null) return output;

    String[] simpleRoots = {
        "title", "turn", "currentLevel", "currentLevelKey", "location",
        "player", "party", "inventory", "gameTime", "partyDetails", "characterProgression"
    };
    for (String key : simpleRoots) {
      if (CanonVisibilityRegistry.rootVisibility(key) == CanonVisibilityRegistry.Visibility.EPISTEMIC) {
        continue;
      }
      if (!state.has(key)) continue;
      Object value = state.get(key);
      Object visible = visibleValue(value, actorId);
      if (visible != null) output.put(key, visible);
    }

    JSONObject combat = state.optJSONObject("combat");
    if (combat != null && CanonVisibilityRegistry.rootVisibility("combat")
        != CanonVisibilityRegistry.Visibility.EPISTEMIC) {
      output.put("combat", visibleValue(visibleCombat(combat), actorId));
    }

    JSONArray beliefs = actorBeliefs(state, actorId);
    if (beliefs.length() > 0) output.put("beliefs", beliefs);
    return (JSONObject) SafePresentationView.value(state, actorId, output);
  }

  private static Object visibleValue(Object value, String actorId) throws Exception {
    if (value instanceof JSONObject) {
      JSONObject source = (JSONObject) value;
      if (source.has("knowledgeBinding") && !KnowledgeContinuityFirewall.canExposeToActor(
          source.optJSONObject("knowledgeBinding"), actorId)) return null;
      JSONObject copy = new JSONObject();
      java.util.Iterator<String> keys = source.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        if ("knowledgeLock".equals(key) || "writerSecret".equals(key)) continue;
        Object visible = visibleValue(source.get(key), actorId);
        if (visible != null) copy.put(key, visible);
      }
      return copy;
    }
    if (value instanceof JSONArray) {
      JSONArray source = (JSONArray) value, copy = new JSONArray();
      for (int i = 0; i < source.length(); i++) {
        Object visible = visibleValue(source.get(i), actorId);
        if (visible != null) copy.put(visible);
      }
      return copy;
    }
    return value;
  }

  private static JSONObject visibleCombat(JSONObject combat) throws Exception {
    JSONObject visible = new JSONObject();
    copy(combat, visible, "active");
    copy(combat, visible, "outcome");
    copy(combat, visible, "round");
    copy(combat, visible, "actorIndex");
    copy(combat, visible, "resolvedActorName");
    copy(combat, visible, "resolvedRound");
    JSONObject entity = combat.optJSONObject("entity");
    if (entity != null) {
      JSONObject publicEntity = new JSONObject();
      copy(entity, publicEntity, "key");
      copy(entity, publicEntity, "name");
      copy(entity, publicEntity, "hp");
      copy(entity, publicEntity, "maxHp");
      visible.put("entity", publicEntity);
    }
    JSONArray participants = combat.optJSONArray("participants");
    if (participants != null) {
      JSONArray publicParticipants = new JSONArray();
      for (int i = 0; i < participants.length(); i++) {
        JSONObject p = participants.optJSONObject(i);
        if (p == null) continue;
        JSONObject publicP = new JSONObject();
        copy(p, publicP, "id");
        copy(p, publicP, "name");
        copy(p, publicP, "hp");
        copy(p, publicP, "maxHp");
        publicParticipants.put(publicP);
      }
      visible.put("participants", publicParticipants);
    }
    return visible;
  }

  private static JSONArray actorBeliefs(JSONObject state, String actorId) throws Exception {
    JSONArray output = new JSONArray();
    JSONObject emergent = state.optJSONObject(EmergentTurnEngine.ROOT_KEY);
    JSONArray beliefs = emergent == null ? null : emergent.optJSONArray("beliefs");
    if (beliefs == null) return output;
    String expected = actorId == null ? "" : actorId.trim();
    for (int i = 0; i < beliefs.length(); i++) {
      JSONObject belief = beliefs.optJSONObject(i);
      if (belief == null || !expected.equals(belief.optString("actorId", ""))) continue;
      JSONObject view = KnowledgeContinuityFirewall.actorBeliefView(belief, expected);
      if (view != null) output.put(view);
    }
    return output;
  }

  private static void copy(JSONObject source, JSONObject target, String key) throws Exception {
    if (source.has(key)) target.put(key, source.get(key));
  }
}
