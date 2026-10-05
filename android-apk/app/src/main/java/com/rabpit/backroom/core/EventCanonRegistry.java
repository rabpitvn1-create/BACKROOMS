package com.rabpit.backroom.core;

import org.json.JSONArray;

import java.util.LinkedHashMap;
import java.util.Map;

/** Canon-owned semantics/impact registry for every committed DomainEvent type. */
final class EventCanonRegistry {
  static final class Spec {
    final String impactScope;
    final String[] semantics;

    Spec(String impactScope, String... semantics) {
      this.impactScope = impactScope;
      this.semantics = semantics;
    }

    JSONArray semanticsJson() {
      JSONArray values = new JSONArray();
      for (String semantic : semantics) values.put(semantic);
      return values;
    }
  }

  private static final Map<String, Spec> SPECS = new LinkedHashMap<>();
  static {
    register("PLAYER_ACTION_RESOLVED", "LOCAL", "STATE_CHANGE");
    register("ROUTE_SEARCH_PROGRESS", "LOCAL", "STATE_CHANGE");
    register("ROUTE_SEARCH_RESET", "LOCAL", "STATE_CHANGE");
    register("ROUTE_EXIT_AVAILABLE", "LOCAL", "STATE_CHANGE", "PERSISTENT_CONSEQUENCE");
    register("LEVEL_TRANSITIONED", "REGIONAL", "STATE_CHANGE", "PERSISTENT_CONSEQUENCE");
    register("ENTITY_ENCOUNTER_STARTED", "LOCAL", "STATE_CHANGE");
    register("CHEST_SPAWNED", "LOCAL", "STATE_CHANGE");
    register("CHEST_OPENED", "LOCAL", "STATE_CHANGE");
    register("CHARACTER_ENCOUNTERED", "SOCIAL", "STATE_CHANGE", "PERSISTENT_CONSEQUENCE");
    register("CHARACTER_REUNION", "SOCIAL", "STATE_CHANGE", "PERSISTENT_CONSEQUENCE");
    register("THREAD_RESOLUTION_CONDITION_MET", "LOCAL", "STATE_CHANGE");
    register("THREAD_DORMANCY_REACHED", "LOCAL", "STATE_CHANGE");
    register("COMBAT_STARTED", "LOCAL", "STATE_CHANGE");
    register("COMBAT_HAND_RESOLVED", "LOCAL", "STATE_CHANGE");
    register("COMBAT_VICTORY", "LOCAL", "STATE_CHANGE", "PERSISTENT_CONSEQUENCE");
    register("COMBAT_DEFEAT", "LOCAL", "STATE_CHANGE", "PERSISTENT_CONSEQUENCE");
    register("PLAYER_RESPAWNED", "LOCAL", "STATE_CHANGE");
    register("ITEM_ACTION_RESOLVED", "LOCAL", "STATE_CHANGE");
    register("CHARACTER_STAT_UPGRADED", "LOCAL", "STATE_CHANGE");
    // Test-only deterministic fixture.
    register("TEST_MOVED", "LOCAL", "STATE_CHANGE");
  }

  private EventCanonRegistry() {}

  static Spec require(String eventType) {
    Spec spec = SPECS.get(eventType == null ? "" : eventType.trim());
    if (spec == null) throw new IllegalStateException("Unknown canon event_type: " + eventType);
    return spec;
  }

  static boolean matchesSemantics(String eventType, JSONArray actual) {
    Spec spec = require(eventType);
    if (actual == null || actual.length() != spec.semantics.length) return false;
    for (int i = 0; i < spec.semantics.length; i++) {
      if (!spec.semantics[i].equals(actual.optString(i))) return false;
    }
    return true;
  }

  private static void register(String eventType, String impactScope, String... semantics) {
    SPECS.put(eventType, new Spec(impactScope, semantics));
  }
}
