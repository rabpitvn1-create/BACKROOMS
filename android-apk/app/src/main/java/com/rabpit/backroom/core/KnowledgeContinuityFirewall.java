package com.rabpit.backroom.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.json.JSONObject;

/**
 * Phase-6E contract separating canon class, actor knowledge and live continuity authority.
 *
 * <p>This class is pure validation only. It does not read or mutate GameState.
 */
public final class KnowledgeContinuityFirewall {
  public static final int CONTRACT_VERSION = 1;

  public enum CanonClass {
    SELF_CANON("SELF-CANON"),
    CROSS_CANON("CROSS-CANON"),
    POV_BELIEF("POV/BELIEF"),
    WRITER_SECRET("WRITER-SECRET"),
    WORLD_CANON("WORLD-CANON"),
    DYNAMIC("DYNAMIC"),
    OPEN_UNKNOWN("OPEN/UNKNOWN");

    public final String wireName;
    CanonClass(String wireName) { this.wireName = wireName; }

    static CanonClass parse(String value) {
      for (CanonClass item : values()) if (item.wireName.equals(value)) return item;
      return null;
    }
  }

  public enum KnowledgeState {
    KNOWN_BEFORE("KNOWN-BEFORE"),
    OBSERVED("OBSERVED"),
    TOLD("TOLD"),
    VERIFIED("VERIFIED"),
    INFERRED("INFERRED"),
    UNKNOWN("UNKNOWN");

    public final String wireName;
    KnowledgeState(String wireName) { this.wireName = wireName; }

    static KnowledgeState parse(String value) {
      for (KnowledgeState item : values()) if (item.wireName.equals(value)) return item;
      return null;
    }
  }

  public enum OriginLayer {
    BASELINE_CANON,
    LIVE_STATE;

    static OriginLayer parse(String value) {
      try {
        return value == null ? null : OriginLayer.valueOf(value);
      } catch (IllegalArgumentException error) {
        return null;
      }
    }
  }

  private static final Set<String> FIELDS = new HashSet<>(Arrays.asList(
      "schemaVersion", "canonClass", "knowledgeState", "actorId", "originLayer", "evidenceRef"));

  private KnowledgeContinuityFirewall() {}

  /**
   * Returns an empty string when a per-actor knowledge binding is valid, otherwise a stable reason.
   */
  public static String validate(JSONObject binding) {
    if (binding == null) return "binding_missing";
    Iterator<String> keys = binding.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      if (!FIELDS.contains(key)) return "unknown_field:" + key;
    }

    if (binding.optInt("schemaVersion", -1) != CONTRACT_VERSION) {
      return "schema_version_invalid";
    }

    CanonClass canonClass = CanonClass.parse(binding.optString("canonClass", "").trim());
    if (canonClass == null) return "canon_class_invalid";

    KnowledgeState knowledgeState =
        KnowledgeState.parse(binding.optString("knowledgeState", "").trim());
    if (knowledgeState == null) return "knowledge_state_invalid";

    OriginLayer originLayer = OriginLayer.parse(binding.optString("originLayer", "").trim());
    if (originLayer == null) return "origin_layer_invalid";

    String actorId = binding.optString("actorId", "").trim();
    if (actorId.isEmpty()) return "actor_id_required";

    if (canonClass == CanonClass.DYNAMIC && originLayer != OriginLayer.LIVE_STATE) {
      return "dynamic_requires_live_state";
    }

    if (canonClass == CanonClass.OPEN_UNKNOWN && knowledgeState != KnowledgeState.UNKNOWN) {
      return "open_unknown_must_remain_unknown";
    }

    if (canonClass == CanonClass.WRITER_SECRET && knowledgeState != KnowledgeState.UNKNOWN) {
      return "writer_secret_not_actor_knowledge";
    }

    if (knowledgeState != KnowledgeState.UNKNOWN
        && binding.optString("evidenceRef", "").trim().isEmpty()) {
      return "evidence_ref_required";
    }

    return "";
  }

  /** Whether this binding may enter the named actor's epistemic view. */
  public static boolean canExposeToActor(JSONObject binding, String actorId) {
    if (!validate(binding).isEmpty()) return false;
    String expected = actorId == null ? "" : actorId.trim();
    if (expected.isEmpty() || !expected.equals(binding.optString("actorId", "").trim())) {
      return false;
    }

    CanonClass canonClass = CanonClass.parse(binding.optString("canonClass", "").trim());
    KnowledgeState state =
        KnowledgeState.parse(binding.optString("knowledgeState", "").trim());

    if (canonClass == CanonClass.WRITER_SECRET || canonClass == CanonClass.OPEN_UNKNOWN) {
      return false;
    }
    return state != KnowledgeState.UNKNOWN;
  }

  /**
   * Only live DYNAMIC bindings may assert current mutable continuity.
   * Baseline canon never overwrites injury, inventory, location, relationship or other live state.
   */
  public static boolean canSupplyCurrentState(JSONObject binding) {
    if (!validate(binding).isEmpty()) return false;
    CanonClass canonClass = CanonClass.parse(binding.optString("canonClass", "").trim());
    OriginLayer originLayer = OriginLayer.parse(binding.optString("originLayer", "").trim());
    KnowledgeState knowledgeState =
        KnowledgeState.parse(binding.optString("knowledgeState", "").trim());
    return canonClass == CanonClass.DYNAMIC
        && originLayer == OriginLayer.LIVE_STATE
        && knowledgeState != KnowledgeState.UNKNOWN;
  }
}
