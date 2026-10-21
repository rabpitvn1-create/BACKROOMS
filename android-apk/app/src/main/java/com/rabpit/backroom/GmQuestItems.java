package com.rabpit.backroom;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/**
 * Independent, GM-owned single-use puzzle-item ledger.
 *
 * No GameCore, Inventory, equipment, world graph or route dependencies. Its only
 * integration is a plain JSON snapshot carried alongside UI presentation in a
 * manual checkpoint. The Android bridge never accepts item mutations from JS.
 */
public final class GmQuestItems {
  private static final String STORAGE = "backroom_gm_quest_items";
  private static final String KEY = "ledger_v1";
  private static final String PENDING = "pending_v1";
  private static final int MAX_ITEMS = 256;
  private static final int MAX_OPERATIONS = 8;
  private static final int MAX_HISTORY = 2048;
  private final SharedPreferences preferences;

  public GmQuestItems(Context context) {
    preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
  }

  private static JSONObject empty() throws Exception {
    return new JSONObject().put("version", 1)
      .put("items", new JSONArray()).put("appliedActions", new JSONArray());
  }
  private JSONObject read() throws Exception {
    String raw = preferences.getString(KEY, null);
    if (raw == null) return empty();
    try { return validateSnapshot(new JSONObject(raw)); }
    catch (Exception error) { throw new IllegalStateException("gm_items_corrupt", error); }
  }
  public synchronized JSONObject snapshot() throws Exception { return new JSONObject(read().toString()); }

  /** Read-only UI data; the idempotency journal never leaves Android storage. */
  public synchronized JSONObject publicView() throws Exception {
    return new JSONObject().put("version", 1).put("items",
      new JSONArray(read().getJSONArray("items").toString()));
  }

  /** The GM only needs held quest props, not 2,048 historical receipt IDs. */
  public synchronized JSONObject narrativeView() throws Exception {
    JSONArray held = new JSONArray();
    JSONArray all = read().getJSONArray("items");
    for (int i = 0; i < all.length(); i++) {
      JSONObject item = all.getJSONObject(i);
      if ("held".equals(item.getString("status"))) held.put(new JSONObject(item.toString()));
    }
    return new JSONObject().put("items", held);
  }

  /** Preflight a native checkpoint before GameCoreFacade performs its restore. */
  public synchronized void validateRestore(JSONObject snapshot) throws Exception {
    validateSnapshot(snapshot);
  }

  public synchronized void restore(JSONObject snapshot) throws Exception {
    JSONObject checked = validateSnapshot(snapshot);
    if (!preferences.edit().putString(KEY, checked.toString()).remove(PENDING).commit())
      throw new IllegalStateException("gm_items_restore_failed");
  }
  public synchronized void reset() {
    if (!preferences.edit().remove(KEY).remove(PENDING).commit())
      throw new IllegalStateException("gm_items_reset_failed");
  }
  /** Durable outbox: a GM operation can only become visible after acknowledgement.
   * On process restart the UI bridge may supply a confirmed narration ID. */
  public synchronized void stage(String id, String nodeId, JSONArray operations) throws Exception {
    if (operations.length() == 0) return;
    if (id == null || id.length() < 3 || id.length() > 240 ||
      nodeId == null || !nodeId.matches("[a-z0-9_.-]{1,64}"))
      throw new IllegalArgumentException("gm_item_stage_scope");
    validateProposal(operations);
    JSONObject pending = pending();
    if (pending != null && !id.equals(pending.getString("id")))
      throw new IllegalStateException("gm_items_pending_unresolved");
    JSONObject next = new JSONObject().put("id", id).put("nodeId", nodeId)
      .put("operations", new JSONArray(operations.toString()));
    if (!preferences.edit().putString(PENDING, next.toString()).commit())
      throw new IllegalStateException("gm_item_stage_failed");
  }
  public synchronized JSONObject pending() throws Exception {
    String raw = preferences.getString(PENDING, null);
    if (raw == null) return null;
    JSONObject value = new JSONObject(raw);
    if (!keys(value).equals(set("id", "nodeId", "operations")))
      throw new IllegalStateException("gm_items_pending_corrupt");
    validateOperations(value.getJSONArray("operations"));
    return value;
  }
  public synchronized void confirmPending(String id) throws Exception {
    JSONObject pending = pending();
    if (pending == null) return;
    if (!id.equals(pending.getString("id")))
      throw new IllegalArgumentException("gm_item_confirmation_wrong_id");
    JSONObject after = applySnapshot(read(), pending.getString("id"),
      pending.getString("nodeId"), pending.getJSONArray("operations"));
    if (!preferences.edit().putString(KEY, after.toString()).remove(PENDING).commit())
      throw new IllegalStateException("gm_items_confirm_failed");
  }
  private static boolean validId(String id) {
    return id != null && id.matches("[a-z0-9][a-z0-9_-]{2,63}");
  }
  private static String field(JSONObject object, String key, int maxLength) throws Exception {
    if (!(object.opt(key) instanceof String))
      throw new IllegalArgumentException("gm_item_field_type");
    String value = object.getString(key).trim();
    if (value.isEmpty() || value.length() > maxLength || value.indexOf('<') >= 0 ||
      value.indexOf('>') >= 0 || value.indexOf((char) 0) >= 0)
      throw new IllegalArgumentException("gm_item_field_invalid");
    return value;
  }

  private static JSONObject validateSnapshot(JSONObject value) throws Exception {
    if (value == null || value.getInt("version") != 1 ||
      !keys(value).equals(set("version", "items", "appliedActions")))
      throw new IllegalArgumentException("gm_items_snapshot_schema");
    JSONArray items = value.getJSONArray("items"), applied = value.getJSONArray("appliedActions");
    if (items.length() > MAX_ITEMS || applied.length() > MAX_HISTORY)
      throw new IllegalArgumentException("gm_items_snapshot_size");
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < items.length(); i++) {
      JSONObject item = items.getJSONObject(i);
      if (!keys(item).equals(set("id", "name", "description", "nodeId", "status")) ||
        !(item.opt("id") instanceof String) || !(item.opt("status") instanceof String) ||
        !validId(item.getString("id")) || !ids.add(item.getString("id")) ||
        !set("held", "consumed").contains(item.getString("status")) ||
        !item.getString("nodeId").matches("[a-z0-9_.-]{1,64}"))
        throw new IllegalArgumentException("gm_items_snapshot_item");
      field(item, "name", 90); field(item, "description", 500); field(item, "nodeId", 64);
    }
    Set<String> seenActions = new HashSet<>();
    for (int i = 0; i < applied.length(); i++) {
      if (!(applied.opt(i) instanceof String))
        throw new IllegalArgumentException("gm_items_action_type");
      String action = applied.getString(i);
      if (action.length() < 3 || action.length() > 240 || !seenActions.add(action))
        throw new IllegalArgumentException("gm_items_snapshot_action");
    }
    return new JSONObject(value.toString());
  }

  private static Set<String> set(String... fields) {
    return new HashSet<>(java.util.Arrays.asList(fields));
  }

  private static Set<String> keys(JSONObject value) {
    Set<String> result = new HashSet<>();
    java.util.Iterator<String> cursor = value.keys();
    while (cursor.hasNext()) result.add(cursor.next());
    return result;
  }

  /**
   * A GM response stays outside the old Core writer contract. The reply itself
   * is checked here, without delegating any item authority to Game Core.
   */
  public static JSONObject parseEnvelope(String raw) throws Exception {
    if (raw == null || raw.trim().isEmpty() || raw.length() > 18000)
      throw new IllegalArgumentException("gm_writer_size");
    String text = raw.trim();
    if (text.startsWith("```")) {
      int nl = text.indexOf('\n'), end = text.lastIndexOf("```");
      if (nl < 0 || end <= nl || !text.substring(end + 3).trim().isEmpty() ||
        !set("", "json").contains(text.substring(3, nl).trim().toLowerCase(java.util.Locale.ROOT)))
        throw new IllegalArgumentException("gm_writer_envelope");
      text = text.substring(nl + 1, end).trim();
    }
    org.json.JSONTokener tokens = new org.json.JSONTokener(text);
    Object root = tokens.nextValue();
    if (!(root instanceof JSONObject) || tokens.nextClean() != 0)
      throw new IllegalArgumentException("gm_writer_json_trailing");
    JSONObject outer = (JSONObject) root;
    if (!keys(outer).equals(set("reply")) &&
        !keys(outer).equals(set("reply", "questItems")))
      throw new IllegalArgumentException("gm_writer_fields");
    Object rawReply = outer.opt("reply");
    if (!(rawReply instanceof String) || ((String) rawReply).trim().isEmpty() ||
      ((String) rawReply).length() > 12000)
      throw new IllegalArgumentException("gm_writer_reply_invalid");
    String reply = (String) rawReply;
    JSONArray operations = outer.optJSONArray("questItems");
    if (outer.has("questItems") && operations == null)
      throw new IllegalArgumentException("gm_item_ops_type");
    validateOperations(operations == null ? new JSONArray() : operations);
    return new JSONObject().put("reply", reply)
      .put("questItems", operations == null ? new JSONArray() : operations);
  }

  private static void validateOperations(JSONArray operations) throws Exception {
    if (operations.length() > MAX_OPERATIONS)
      throw new IllegalArgumentException("gm_item_ops_size");
    Set<String> used = new HashSet<>();
    for (int i = 0; i < operations.length(); i++) {
      JSONObject op = operations.getJSONObject(i);
      if (!(op.opt("type") instanceof String) || !(op.opt("id") instanceof String))
        throw new IllegalArgumentException("gm_item_op_field_type");
      String type = op.getString("type"), id = op.getString("id");
      if (!validId(id) || !used.add(id + ":" + type))
        throw new IllegalArgumentException("gm_item_op_id");
      switch (type) {
        case "create":
          if (!keys(op).equals(set("type", "id", "name", "description")))
            throw new IllegalArgumentException("gm_item_create_fields");
          field(op, "name", 90); field(op, "description", 500); break;
        case "consume":
          if (!keys(op).equals(set("type", "id")))
            throw new IllegalArgumentException("gm_item_consume_fields");
          break;
        default: throw new IllegalArgumentException("gm_item_op_type");
      }
    }
  }

  /** Verify all mutations as one unit. This does not interpret puzzle success. */
  public synchronized void validateProposal(JSONArray operations) throws Exception {
    validateOperations(operations);
    preview(read(), operations, "node-placeholder");
  }

  private static JSONObject preview(JSONObject base, JSONArray operations, String nodeId) throws Exception {
    JSONObject result = new JSONObject(base.toString());
    JSONArray items = result.getJSONArray("items");
    for (int i = 0; i < operations.length(); i++) {
      JSONObject op = operations.getJSONObject(i);
      String id = op.getString("id"), type = op.getString("type");
      JSONObject existing = null;
      for (int n = 0; n < items.length(); n++)
        if (id.equals(items.getJSONObject(n).getString("id"))) {
          existing = items.getJSONObject(n); break;
        }
      if ("create".equals(type)) {
        if (existing != null || items.length() >= MAX_ITEMS)
          throw new IllegalArgumentException("gm_item_duplicate_or_full");
        items.put(new JSONObject().put("id", id)
          .put("name", op.getString("name")).put("description", op.getString("description"))
          .put("nodeId", nodeId).put("status", "held"));
      } else {
        if (existing == null || !"held".equals(existing.getString("status")))
          throw new IllegalArgumentException("gm_item_unavailable");
        existing.put("status", "consumed");
      }
    }
    return result;
  }

  /**
   * Idempotent by the Core-confirmed narration identity, but does not request,
   * authorize, or inspect any Core state itself.
   */
  static JSONObject applySnapshot(JSONObject base, String narrationId, String nodeId, JSONArray operations) throws Exception {
    if (narrationId == null || narrationId.length() < 3 || narrationId.length() > 240 ||
      !nodeId.matches("[a-z0-9_.-]{1,64}"))
      throw new IllegalArgumentException("gm_item_confirmation_scope");
    validateOperations(operations);
    JSONObject current = validateSnapshot(base);
    JSONArray actions = current.getJSONArray("appliedActions");
    for (int i = 0; i < actions.length(); i++)
      if (narrationId.equals(actions.getString(i))) return current;
    JSONObject next = preview(current, operations, nodeId);
    JSONArray prior = next.getJSONArray("appliedActions");
    JSONArray latest = new JSONArray();
    for (int i = Math.max(0, prior.length() - MAX_HISTORY + 1); i < prior.length(); i++)
      latest.put(prior.getString(i));
    latest.put(narrationId);
    next.put("appliedActions", latest);
    return validateSnapshot(next);
  }

}
