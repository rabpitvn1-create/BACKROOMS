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
      if (value instanceof JSONObject) output.put(key, new JSONObject(value.toString()));
      else if (value instanceof JSONArray) output.put(key, new JSONArray(value.toString()));
      else output.put(key, value);
    }

    JSONObject combat = state.optJSONObject("combat");
    if (combat != null && CanonVisibilityRegistry.rootVisibility("combat")
        != CanonVisibilityRegistry.Visibility.EPISTEMIC) {
      output.put("combat", visibleCombat(combat));
    }

    JSONArray beliefs = actorBeliefs(state, actorId);
    if (beliefs.length() > 0) output.put("beliefs", beliefs);
    return output;
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
      output.put(new JSONObject(belief.toString()));
    }
    return output;
  }

  private static void copy(JSONObject source, JSONObject target, String key) throws Exception {
    if (source.has(key)) target.put(key, source.get(key));
  }
}
