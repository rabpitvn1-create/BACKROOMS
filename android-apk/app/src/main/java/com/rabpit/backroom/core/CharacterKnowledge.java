package com.rabpit.backroom.core;

import org.json.JSONObject;

/** Core-owned, per-actor name/effect flags. Prose and HUD never grant knowledge. */
final class CharacterKnowledge {
  static final String ROOT = "characterKnowledge";

  private CharacterKnowledge() {}

  static void normalize(JSONObject state) throws Exception {
    JSONObject root = state.optJSONObject(ROOT);
    if (root == null) root = new JSONObject();
    state.put(ROOT, root);
    for (String actor : new String[] {"cao_minh", "luc_tram", "lucia", "syvial"}) {
      if (root.optJSONObject(actor) == null) root.put(actor, new JSONObject());
      seed(state, actor, actor);
    }
    // Current reunion canon: these two knew each other before Backrooms.
    seed(state, "cao_minh", "luc_tram");
    seed(state, "luc_tram", "cao_minh");
    for (String actor : new String[] {"cao_minh", "luc_tram"}) {
      seed(state, actor, "cultivation");
    }
    seed(state, "cao_minh", "cao_minh_title");
    seed(state, "lucia", "lucia_m4a1");
    seed(state, "lucia", "firearm");
    seed(state, "lucia", "laser");
  }

  private static boolean priorName(String actor, String subject) {
    return (("cao_minh".equals(actor) || "luc_tram".equals(actor)
        || "lucia".equals(actor) || "syvial".equals(actor)) && actor.equals(subject))
        || (("cao_minh".equals(actor) || "luc_tram".equals(actor))
            && ("cao_minh".equals(subject) || "luc_tram".equals(subject)
                || "cultivation".equals(subject)))
        || ("cao_minh".equals(actor) && "cao_minh_title".equals(subject))
        || ("lucia".equals(actor) && ("lucia_m4a1".equals(subject)
            || "firearm".equals(subject) || "laser".equals(subject)));
  }

  static boolean knows(JSONObject state, String actor, String subject, String field) {
    if (actor == null || subject == null) return false;
    JSONObject root = state == null ? null : state.optJSONObject(ROOT);
    JSONObject owner = root == null ? null : root.optJSONObject(actor);
    JSONObject knowledge = owner == null ? null : owner.optJSONObject(subject);
    return knowledge != null ? knowledge.optBoolean(field, false)
        : "knownName".equals(field) && priorName(actor, subject);
  }

  static void mark(JSONObject state, String actor, String subject, String field, String sourceEvent)
      throws Exception {
    if (actor == null || actor.trim().isEmpty() || subject == null || subject.trim().isEmpty()
        || sourceEvent == null || sourceEvent.trim().isEmpty()
        || !("knownName".equals(field) || "knownEffect".equals(field))) {
      throw new IllegalArgumentException("Knowledge update requires actor, subject and Core source event");
    }
    JSONObject root = state.optJSONObject(ROOT);
    if (root == null) root = new JSONObject();
    JSONObject owner = root.optJSONObject(actor);
    if (owner == null) owner = new JSONObject();
    JSONObject knowledge = owner.optJSONObject(subject);
    if (knowledge == null) knowledge = new JSONObject();
    knowledge.put(field, true).put("sourceEventId", sourceEvent);
    owner.put(subject, knowledge);
    root.put(actor, owner);
    state.put(ROOT, root);
  }

  private static void seed(JSONObject state, String actor, String subject) throws Exception {
    JSONObject owner = state.getJSONObject(ROOT).getJSONObject(actor);
    if (owner.has(subject)) return;
    mark(state, actor, subject, "knownName", "canon:known-before:" + actor + ":" + subject);
  }
}
