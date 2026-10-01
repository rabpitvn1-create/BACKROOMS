package com.rabpit.backroom.core;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Typed command registry and deterministic Core authority adapters.
 *
 * <p>The authority owns command-to-Core mapping only. Phase 4B.1 execution is delegated to
 * {@link GmTransactionExecutor}, which supplies private state copies and atomic causal groups.
 * This class has no persistence path and unknown commands fail closed.
 */
public final class GmCommandAuthority {
  private interface Adapter {
    JSONObject apply(JSONObject state, JSONObject payload) throws Exception;
  }

  private static final class Spec {
    final String type;
    final String owner;
    final String status;
    final String payloadShape;
    final Adapter adapter;

    Spec(String type, String owner, String status, String payloadShape, Adapter adapter) {
      this.type = type;
      this.owner = owner;
      this.status = status;
      this.payloadShape = payloadShape;
      this.adapter = adapter;
    }
  }

  private final ItemCore itemCore;
  private final LevelCore levelCore;
  private final EntityCore entityCore;
  private final CharacterEncounterCore characterEncounterCore;
  private final CharacterProgressionCore progressionCore;
  private final Map<String, Spec> specs = new LinkedHashMap<>();

  GmCommandAuthority(
      ItemCore itemCore,
      LevelCore levelCore,
      EntityCore entityCore,
      CharacterEncounterCore characterEncounterCore,
      CharacterProgressionCore progressionCore) {
    this.itemCore = itemCore;
    this.levelCore = levelCore;
    this.entityCore = entityCore;
    this.characterEncounterCore = characterEncounterCore;
    this.progressionCore = progressionCore;

    register("USE_ITEM", "ItemCore", "ACTIVE",
        "{ownerId?,itemId,quantity?}", this::useItem);
    register("SHARE_ITEM", "ItemCore", "ACTIVE",
        "{ownerId?,itemId,targetId,quantity?}", this::shareItem);
    register("DISCARD_ITEM", "ItemCore", "ACTIVE",
        "{ownerId?,itemId,quantity?}", this::discardItem);
    register("MOVE_LEVEL", "LevelCore", "ACTIVE",
        "{targetLevelKey}", this::moveLevel);
    register("UPGRADE_STAT", "CharacterProgressionCore", "ACTIVE",
        "{characterId,stat}", this::upgradeStat);
    register("START_COMBAT", "CombatChoiceEngine", "ACTIVE",
        "{entityKey}", this::startCombat);

    // These mutation families already have deterministic Core owners, but their current V2 path
    // also requires candidate/RNG selection. Phase 3 registers ownership while refusing to bypass
    // that missing gate. Phase 4 may enable them only after the gate is explicit.
    register("START_ENTITY_ENCOUNTER", "EntityCore", "PHASE4_SELECTION_GATE",
        "{entityKey}", this::selectionGateRequired);
    register("START_CHARACTER_ENCOUNTER", "CharacterEncounterCore", "PHASE4_SELECTION_GATE",
        "{characterId}", this::selectionGateRequired);
    register("DISCOVER_CHEST", "ItemCore", "PHASE4_SELECTION_GATE",
        "{}", this::selectionGateRequired);
  }

  private void register(String type, String owner, String status, String payloadShape, Adapter adapter) {
    if (specs.put(type, new Spec(type, owner, status, payloadShape, adapter)) != null) {
      throw new IllegalStateException("Duplicate GM command type: " + type);
    }
  }

  public String promptContext() {
    StringBuilder out = new StringBuilder(
        "TYPED COMMAND REGISTRY. Chỉ dùng command type trong danh sách này. "
            + "Owner Core quyết định hợp lệ; Planner không được bypass owner.\n");
    for (Spec spec : specs.values()) {
      out.append("- ").append(spec.type)
          .append(" owner=").append(spec.owner)
          .append(" status=").append(spec.status)
          .append(" payload=").append(spec.payloadShape).append('\n');
    }
    out.append("PHASE4_SELECTION_GATE có thể được đề xuất trong shadow mode nhưng hiện luôn bị "
        + "authority từ chối để không bypass candidate/RNG selection của V2.");
    return out.toString();
  }

  public JSONArray registryDescriptor() throws JSONException {
    JSONArray result = new JSONArray();
    for (Spec spec : specs.values()) {
      result.put(new JSONObject()
          .put("type", spec.type)
          .put("owner", spec.owner)
          .put("status", spec.status)
          .put("payloadShape", spec.payloadShape));
    }
    return result;
  }

  public JSONObject validate(
      JSONObject beforeState, JSONObject proposal, String expectedTurnId, String expectedBaseHash)
      throws JSONException {
    JSONObject draft = new GmTransactionExecutor(this).execute(
        beforeState, proposal, expectedTurnId, expectedBaseHash);
    draft.remove("afterState");
    return draft;
  }

  JSONObject evaluateCommand(JSONObject groupState, JSONObject command) throws JSONException {
    String commandId = command.optString("commandId", "");
    String type = command.optString("type", "");
    Spec spec = specs.get(type);
    if (spec == null) {
      return rejected(commandId, type, "UNOWNED", "unknown_command_type");
    }

    JSONObject payload = command.optJSONObject("payload");
    if (payload == null) return rejected(commandId, type, spec.owner, "command_payload_missing");

    try {
      JSONObject event = spec.adapter.apply(groupState, payload);
      if (event == null) return rejected(commandId, type, spec.owner, "core_event_missing");
      event.put("owner", spec.owner).put("commandType", type);
      return new JSONObject()
          .put("commandId", commandId)
          .put("type", type)
          .put("owner", spec.owner)
          .put("accepted", true)
          .put("reason", "")
          .put("event", event);
    } catch (Exception error) {
      return rejected(commandId, type, spec.owner, stableReason(error));
    }
  }

  private JSONObject useItem(JSONObject state, JSONObject payload) throws Exception {
    requireOnly(payload, "ownerId", "itemId", "quantity");
    String ownerId = optional(payload, "ownerId", "cao_minh");
    String itemId = required(payload, "itemId");
    int quantity = positiveInt(payload, "quantity", 1);
    itemCore.applyItemAction(state, ownerId, itemId, "use", "", quantity);
    return event("ITEM_USED", itemId)
        .put("actorId", ownerId)
        .put("quantity", quantity);
  }

  private JSONObject shareItem(JSONObject state, JSONObject payload) throws Exception {
    requireOnly(payload, "ownerId", "itemId", "targetId", "quantity");
    String ownerId = optional(payload, "ownerId", "cao_minh");
    String itemId = required(payload, "itemId");
    String targetId = required(payload, "targetId");
    int quantity = positiveInt(payload, "quantity", 1);
    itemCore.applyItemAction(state, ownerId, itemId, "share", targetId, quantity);
    return event("ITEM_SHARED", itemId)
        .put("actorId", ownerId)
        .put("targetId", targetId)
        .put("quantity", quantity);
  }

  private JSONObject discardItem(JSONObject state, JSONObject payload) throws Exception {
    requireOnly(payload, "ownerId", "itemId", "quantity");
    String ownerId = optional(payload, "ownerId", "cao_minh");
    String itemId = required(payload, "itemId");
    int quantity = positiveInt(payload, "quantity", 1);
    itemCore.applyItemAction(state, ownerId, itemId, "discard", "", quantity);
    return event("ITEM_DISCARDED", itemId)
        .put("actorId", ownerId)
        .put("quantity", quantity);
  }

  private JSONObject moveLevel(JSONObject state, JSONObject payload) throws Exception {
    requireOnly(payload, "targetLevelKey");
    if (levelCore == null) throw new IllegalStateException("owner_unavailable");
    String target = required(payload, "targetLevelKey");
    String before = state.optString(LevelCore.LEVEL_KEY,
        String.valueOf(state.optInt("currentLevel", 0)));
    boolean moved = levelCore.applyPlayerTransitionIfRequested(state, "đi vào level " + target);
    String after = state.optString(LevelCore.LEVEL_KEY,
        String.valueOf(state.optInt("currentLevel", 0)));
    if (!moved || before.equals(after) || !target.equals(after)) {
      throw new IllegalStateException("level_transition_not_allowed");
    }
    return event("LEVEL_TRANSITIONED", after).put("fromLevelKey", before);
  }

  private JSONObject upgradeStat(JSONObject state, JSONObject payload) throws Exception {
    requireOnly(payload, "characterId", "stat");
    String characterId = required(payload, "characterId");
    String stat = required(payload, "stat");
    JSONObject result = progressionCore.upgradeStat(state, characterId, stat);
    return event("CHARACTER_STAT_UPGRADED", result.getString("characterId"))
        .put("stat", result.getString("stat"))
        .put("value", result.getInt("value"))
        .put("cost", result.getInt("cost"));
  }

  private JSONObject startCombat(JSONObject state, JSONObject payload) throws Exception {
    requireOnly(payload, "entityKey");
    String entityKey = required(payload, "entityKey").toLowerCase();
    JSONObject flags = state.optJSONObject("flags");
    String active = flags == null ? "" : flags.optString("entityEncounterKey", "").trim().toLowerCase();
    if (!entityKey.equals(active)) throw new IllegalStateException("entity_encounter_not_active");
    if (CombatChoiceEngine.isActive(state)) throw new IllegalStateException("combat_already_active");
    CombatChoiceEngine.start(state, entityKey, 0);
    if (!CombatChoiceEngine.isActive(state)) throw new IllegalStateException("combat_start_rejected");
    return event("COMBAT_STARTED", entityKey);
  }

  private JSONObject selectionGateRequired(JSONObject state, JSONObject payload) {
    throw new IllegalStateException("phase4_selection_gate_required");
  }

  private static JSONObject event(String eventType, String subjectKey) throws JSONException {
    return new JSONObject()
        .put("eventType", eventType)
        .put("subjectKey", subjectKey == null ? "" : subjectKey);
  }

  private static JSONObject rejected(
      String commandId, String type, String owner, String reason) throws JSONException {
    return new JSONObject()
        .put("commandId", commandId == null ? "" : commandId)
        .put("type", type == null ? "" : type)
        .put("owner", owner == null ? "" : owner)
        .put("accepted", false)
        .put("reason", reason == null ? "core_rejected" : reason);
  }

  private static void requireOnly(JSONObject payload, String... allowedKeys) {
    Set<String> allowed = new LinkedHashSet<>(Arrays.asList(allowedKeys));
    java.util.Iterator<String> keys = payload.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      if (!allowed.contains(key)) {
        throw new IllegalArgumentException("payload_unknown_field:" + key);
      }
    }
  }

  private static String required(JSONObject payload, String key) {
    String value = payload.optString(key, "").trim();
    if (value.isEmpty()) throw new IllegalArgumentException("payload_required:" + key);
    return value;
  }

  private static String optional(JSONObject payload, String key, String fallback) {
    String value = payload.optString(key, "").trim();
    return value.isEmpty() ? fallback : value;
  }

  private static int positiveInt(JSONObject payload, String key, int fallback) {
    int value = payload.has(key) ? payload.optInt(key, -1) : fallback;
    if (value <= 0) throw new IllegalArgumentException("payload_positive_int:" + key);
    return value;
  }

  private static String stableReason(Exception error) {
    if (error == null || error.getMessage() == null || error.getMessage().trim().isEmpty()) {
      return "core_rejected";
    }
    String message = error.getMessage().trim();
    if (message.startsWith("payload_") || message.startsWith("phase4_")
        || message.startsWith("level_") || message.startsWith("entity_")
        || message.startsWith("combat_") || message.equals("owner_unavailable")) {
      return message;
    }
    return "core_rejected:" + error.getClass().getSimpleName();
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }
}
