package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Core-owned loot and consumable item rules. AI is never allowed to create loot. */
final class ItemCore {
  static final int CHEST_SPAWN_RATE_PERCENT = 5;
  static final int ENTITY_DROP_MIN_PERCENT = 100;
  static final int ENTITY_DROP_MAX_PERCENT = 100;
  static final int CORE_CHEST_DROP_PERCENT = 100;
  static final String OPEN_CHEST_ACTION = "__loot:open_chest";

  static final String ALMOND_WATER_ID = "almond-water";
  static final String BANDAGE_ID = "bandage";
  static final String FIRST_AID_KIT_ID = "first-aid-kit";
  static final String LAVIE_WATER_ID = "lavie-water";
  static final String COCONUT_WATER_ID = "coconut-water";
  static final String BANH_MI_THIT_ID = "banh-mi-thit";
  static final String HOT_SOY_MILK_ID = "hot-soy-milk";
  static final String COM_TAM_SUON_BI_CHA_ID = "com-tam-suon-bi-cha";

  private static final String[] CHEST_POOL = {
      ALMOND_WATER_ID,
      BANDAGE_ID,
      FIRST_AID_KIT_ID,
      LAVIE_WATER_ID,
      COCONUT_WATER_ID,
      BANH_MI_THIT_ID,
      HOT_SOY_MILK_ID,
      COM_TAM_SUON_BI_CHA_ID
  };

  private static final String[] ENTITY_POOL = {
      ALMOND_WATER_ID,
      BANDAGE_ID
  };

  private static final String CHEST_PRESENT = "chestPresent";
  private static final String CHEST_SOURCE = "core_exploration_roll";

  interface IntRng {
    int nextInt(int bound);
  }

  private final CharacterProgressionCore progressionCore = new CharacterProgressionCore();
  private final SurvivalCore survivalCore = new SurvivalCore();
  private final IntRng rng;

  ItemCore() {
    this(bound -> {
      throw new IllegalStateException("Unscoped Item RNG is disabled; use the TurnRng-backed overload.");
    });
  }

  ItemCore(IntRng rng) {
    if (rng == null) throw new IllegalArgumentException("rng is required");
    this.rng = rng;
  }

  void normalizeInventory(JSONObject state) throws Exception {
    if (state == null) return;
    normalizeInventoryArray(state.optJSONArray("inventory"));

    JSONArray party = state.optJSONArray("party");
    if (party == null) return;
    for (int i = 0; i < party.length(); i++) {
      JSONObject member = party.optJSONObject(i);
      if (member == null) continue;
      normalizeInventoryArray(member.optJSONArray("inventory"));
    }
  }

  private void normalizeInventoryArray(JSONArray inventory) throws Exception {
    if (inventory == null) return;
    for (int i = 0; i < inventory.length(); i++) {
      JSONObject item = inventory.optJSONObject(i);
      if (item == null) continue;
      String id = normalizedItemId(item);
      if (!isConsumable(id)) continue;
      JSONObject definition = itemDefinition(id, Math.max(1, item.optInt("quantity", 1)));
      item.put("id", id);
      item.put("name", definition.getString("name"));
      item.put("kind", "consumable");
      item.put("category", definition.getString("category"));
      item.put("stackable", true);
      item.put("effects", definition.getJSONObject("effects"));
      item.put("quantity", definition.getInt("quantity"));
    }
  }

  void prepareExplorationLoot(JSONObject state) throws Exception {
    JSONObject flags = flags(state);
    if (flags.optBoolean(CHEST_PRESENT, false)) return;
    int roll = nextRoll(100);
    if (!shouldSpawnChest(roll)) return;
    flags.put(CHEST_PRESENT, true);
    flags.put("chestSource", CHEST_SOURCE);
    flags.put("chestSpawnRatePercent", CHEST_SPAWN_RATE_PERCENT);
    flags.put("chestSpawnTurn", Math.max(1, state.optInt("turn", 1)));
    state.put("flags", flags);
  }

  JSONObject explorationChestCandidate(JSONObject state) throws Exception {
    JSONObject currentFlags = flags(state);
    if (currentFlags.optBoolean(CHEST_PRESENT, false)) return null;
    String levelKey = state.optString("currentLevelKey", String.valueOf(state.optInt("currentLevel", 0)));
    return new JSONObject()
        .put("candidateId", "resource:chest:" + levelKey)
        .put("situationKey", "resource:chest:" + levelKey)
        .put("kind", "CHEST")
        .put("category", "RESOURCE")
        .put("chancePercent", CHEST_SPAWN_RATE_PERCENT)
        .put("payloadKey", levelKey)
        .put("source", "CANON")
        .put("publicSummary", "Cao Minh phát hiện một chiếc rương trong khu vực.")
        .put("proposalRequired", false)
        .put("eligibilityRuleId", "canon:exploration_chest")
        .put("tags", new JSONArray().put("RESOURCE").put("CHEST"))
        .put("keyRefs", new JSONArray().put(levelKey));
  }

  void activateExplorationChest(JSONObject state) throws Exception {
    JSONObject currentFlags = flags(state);
    if (currentFlags.optBoolean(CHEST_PRESENT, false)) return;
    currentFlags.put(CHEST_PRESENT, true);
    currentFlags.put("chestSource", "candidate_selector");
    currentFlags.put("chestSpawnRatePercent", CHEST_SPAWN_RATE_PERCENT);
    currentFlags.put("chestSpawnTurn", Math.max(1, state.optInt("turn", 1)));
    state.put("flags", currentFlags);
  }

  boolean isOpenChestAction(String action) {
    String value = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
    return OPEN_CHEST_ACTION.equals(action)
        || value.equals("mở rương")
        || value.equals("mo ruong")
        || value.equals("open chest");
  }

  String openChest(JSONObject state) throws Exception {
    return openChest(state, rng);
  }

  String openChest(JSONObject state, IntRng turnRng) throws Exception {
    if (turnRng == null) throw new IllegalArgumentException("turn RNG is required");
    JSONObject flags = flags(state);
    if (!flags.optBoolean(CHEST_PRESENT, false)) {
      throw new IllegalStateException("Không có rương để mở.");
    }
    String itemName = grantChestLootItem(state, nextRoll(turnRng, 100));
    int coreRoll = nextRoll(turnRng, 100);
    int coreReward = 0;
    if (shouldDropCore(coreRoll, CORE_CHEST_DROP_PERCENT)) {
      coreReward = progressionCore.grantCore(state,
          CharacterProgressionCore.bundleSize(LevelCore.stageIndex(state)));
    }
    flags.put("lastChestCoreRoll", coreRoll);
    flags.put("lastChestCoreReward", coreReward);
    flags.put(CHEST_PRESENT, false);
    flags.put("lastChestOpenedTurn", Math.max(1, state.optInt("turn", 1)));
    flags.remove("chestSource");
    flags.remove("chestSpawnRatePercent");
    flags.remove("chestSpawnTurn");
    state.put("flags", flags);
    return itemName;
  }

  String applyItemAction(JSONObject state, String itemId, String operation, String targetId,
                         int requestedQuantity) throws Exception {
    return applyItemAction(state, "cao_minh", itemId, operation, targetId, requestedQuantity);
  }

  String applyItemAction(JSONObject state, String ownerId, String itemId, String operation,
                         String targetId, int requestedQuantity) throws Exception {
    normalizeInventory(state);
    progressionCore.normalizeState(state);
    survivalCore.normalizeState(state);

    String ownerCharacterId = CharacterProgressionCore.normalizeCharacterId(ownerId);
    if (ownerCharacterId.isEmpty()) ownerCharacterId = "cao_minh";
    JSONArray inventory = inventoryFor(state, ownerCharacterId, false);
    if (inventory == null || inventory.length() == 0) {
      throw new IllegalArgumentException("Inventory của nhân vật đang trống.");
    }

    String op = operation == null ? "" : operation.trim().toLowerCase(Locale.ROOT);
    int index = findItemIndex(inventory, itemId);
    if (index < 0) throw new IllegalArgumentException("Không tìm thấy vật phẩm trong Inventory của nhân vật.");
    JSONObject stack = inventory.getJSONObject(index);
    String normalizedId = normalizedItemId(stack);
    int available = Math.max(1, stack.optInt("quantity", 1));
    int quantity = Math.max(1, Math.min(requestedQuantity, available));
    String name = stack.optString("name", itemName(normalizedId));

    if ("discard".equals(op)) {
      consumeStack(inventory, index, quantity);
      setInventoryFor(state, ownerCharacterId, inventory);
      return "Đã vứt bỏ " + name + " x" + quantity + ".";
    }

    if (!isConsumable(normalizedId)) {
      throw new IllegalArgumentException(name + " không phải vật phẩm tiêu hao.");
    }

    String targetCharacterId;
    String targetName;
    if ("share".equals(op)) {
      targetCharacterId = CharacterProgressionCore.normalizeCharacterId(targetId);
      if ("cao_minh".equals(targetCharacterId)) {
        JSONObject player = state.optJSONObject("player");
        targetName = player == null ? "Cao Minh" : player.optString("name", "Cao Minh");
      } else {
        JSONObject target = findPartyMember(state.optJSONArray("party"), targetId);
        if (target == null || !CharacterEncounterCore.isJoinedMember(target)) {
          throw new IllegalArgumentException("Không tìm thấy nhân vật trong party.");
        }
        targetCharacterId = CharacterProgressionCore.normalizeCharacterId(
            target.optString("id", target.optString("name", "")));
        targetName = target.optString("name", target.optString("id", "Đồng đội"));
      }
      if (targetCharacterId.equals(ownerCharacterId)) {
        throw new IllegalArgumentException("Nhân vật đang chọn chính là người giữ vật phẩm.");
      }
    } else if ("use".equals(op)) {
      targetCharacterId = ownerCharacterId;
      targetName = inventoryOwnerName(state, ownerCharacterId);
    } else {
      throw new IllegalArgumentException("Hành động vật phẩm không hợp lệ.");
    }

    JSONObject targetProfile = progressionCore.profile(state, targetCharacterId);
    if (!"cao_minh".equals(targetCharacterId) && targetProfile.optInt("currentHp", 0) <= 0) {
      throw new IllegalStateException("Nhân vật đang bị hạ và phải chờ đủ 10 Explorer Turn để hồi sinh.");
    }

    String effectText = applyEffects(state, targetCharacterId, normalizedId, quantity);
    consumeStack(inventory, index, quantity);
    setInventoryFor(state, ownerCharacterId, inventory);
    if ("share".equals(op)) {
      return inventoryOwnerName(state, ownerCharacterId) + " dùng " + name + " x" + quantity
          + " cho " + targetName + ". " + effectText;
    }
    return targetName + " sử dụng " + name + " x" + quantity + ". " + effectText;
  }

  String promptContext(JSONObject state) {
    JSONObject flags = state == null ? null : state.optJSONObject("flags");
    boolean chest = flags != null && flags.optBoolean(CHEST_PRESENT, false);
    if (chest) {
      return "ITEM CORE: một Rương loot thật đang hiện diện trong scene. Có thể mô tả Rương, nhưng tuyệt đối không tự mở, "
          + "không tự cho vật phẩm và không sửa inventory. UI/Core sẽ xử lý Mở Rương; rương luôn có vật phẩm 100%.";
    }
    return "ITEM CORE: không có loot rời trong scene. Không tự sinh, nhặt, trao hoặc thêm item vào inventory. "
        + "Đồ ăn/nước/y tế Frontrooms chỉ đến từ Rương/nguồn môi trường do Core xác nhận; Entity không rơi đồ ăn đời thường.";
  }

  static Map<String, String> semanticCatalog() {
    Map<String, String> output = new LinkedHashMap<>();
    for (String id : CHEST_POOL) output.put(itemName(id), "item");
    output.put("Bandage", "item");
    output.put("Rương", "item");
    return output;
  }

  static boolean shouldSpawnChest(int roll) {
    return roll >= 0 && roll < CHEST_SPAWN_RATE_PERCENT;
  }

  static int entityDropRatePercent(String entityKey) {
    String key = entityKey == null ? "" : entityKey.trim().toLowerCase(Locale.ROOT);
    int span = ENTITY_DROP_MAX_PERCENT - ENTITY_DROP_MIN_PERCENT + 1;
    return ENTITY_DROP_MIN_PERCENT + Math.floorMod(key.hashCode(), span);
  }

  static boolean shouldDropEntityLoot(int roll, int ratePercent) {
    int rate = Math.max(ENTITY_DROP_MIN_PERCENT, Math.min(ENTITY_DROP_MAX_PERCENT, ratePercent));
    return roll >= 0 && roll < rate;
  }

  static boolean shouldDropCore(int roll, int ratePercent) {
    int rate = Math.max(0, Math.min(100, ratePercent));
    return roll >= 0 && roll < rate;
  }

  static int itemEffectValue(String itemId, String stat) {
    String id = itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
    String key = stat == null ? "" : stat.trim().toLowerCase(Locale.ROOT);
    if (ALMOND_WATER_ID.equals(id)) {
      if ("hunger".equals(key)) return 50;
      if ("thirst".equals(key)) return 100;
    }
    if (BANDAGE_ID.equals(id) && "hp".equals(key)) return 15;
    if (FIRST_AID_KIT_ID.equals(id) && "hp".equals(key)) return 35;
    if (LAVIE_WATER_ID.equals(id) && "thirst".equals(key)) return 50;
    if (COCONUT_WATER_ID.equals(id)) {
      if ("hunger".equals(key)) return 10;
      if ("thirst".equals(key)) return 70;
    }
    if (BANH_MI_THIT_ID.equals(id) && "hunger".equals(key)) return 45;
    if (HOT_SOY_MILK_ID.equals(id)) {
      if ("hunger".equals(key)) return 25;
      if ("thirst".equals(key)) return 30;
    }
    if (COM_TAM_SUON_BI_CHA_ID.equals(id) && "hunger".equals(key)) return 80;
    return 0;
  }

  static String grantChestLootItem(JSONObject state, int selector) throws Exception {
    String id = CHEST_POOL[Math.floorMod(selector, CHEST_POOL.length)];
    addItem(state, id, 1);
    return itemName(id);
  }

  static String grantEntityLootItem(JSONObject state, int selector) throws Exception {
    String id = ENTITY_POOL[Math.floorMod(selector, ENTITY_POOL.length)];
    addItem(state, id, 1);
    return itemName(id);
  }

  static String grantLootItem(JSONObject state, int selector) throws Exception {
    return grantChestLootItem(state, selector);
  }

  private static boolean isConsumable(String id) {
    for (String known : CHEST_POOL) {
      if (known.equals(id)) return true;
    }
    return false;
  }

  private static String itemName(String id) {
    if (ALMOND_WATER_ID.equals(id)) return "Almond Water";
    if (BANDAGE_ID.equals(id)) return "Băng Gạc Y Tế";
    if (FIRST_AID_KIT_ID.equals(id)) return "Túi Sơ Cứu";
    if (LAVIE_WATER_ID.equals(id)) return "Nước Suối Lavie";
    if (COCONUT_WATER_ID.equals(id)) return "Nước Dừa";
    if (BANH_MI_THIT_ID.equals(id)) return "Bánh Mì Thịt";
    if (HOT_SOY_MILK_ID.equals(id)) return "Sữa Đậu Nành Nóng";
    if (COM_TAM_SUON_BI_CHA_ID.equals(id)) return "Cơm Tấm Sườn Bì Chả";
    return id == null || id.isEmpty() ? "Vật phẩm" : id;
  }

  private static String itemCategory(String id) {
    if (BANDAGE_ID.equals(id) || FIRST_AID_KIT_ID.equals(id)) return "HEALING";
    if (LAVIE_WATER_ID.equals(id)) return "DRINK";
    if (BANH_MI_THIT_ID.equals(id) || COM_TAM_SUON_BI_CHA_ID.equals(id)) return "FOOD";
    return "FOOD_DRINK";
  }

  private static JSONObject itemEffects(String id) throws Exception {
    JSONObject effects = new JSONObject();
    int hunger = itemEffectValue(id, "hunger");
    int thirst = itemEffectValue(id, "thirst");
    int hp = itemEffectValue(id, "hp");
    if (hunger > 0) effects.put("hunger", hunger);
    if (thirst > 0) effects.put("thirst", thirst);
    if (hp > 0) effects.put("hp", hp);
    return effects;
  }

  private static JSONObject itemDefinition(String id, int quantity) throws Exception {
    return new JSONObject()
        .put("id", id)
        .put("name", itemName(id))
        .put("quantity", Math.max(1, quantity))
        .put("kind", "consumable")
        .put("category", itemCategory(id))
        .put("stackable", true)
        .put("effects", itemEffects(id));
  }

  private static void addItem(JSONObject state, String id, int quantity) throws Exception {
    JSONArray inventory = state.optJSONArray("inventory");
    if (inventory == null) inventory = new JSONArray();
    for (int i = 0; i < inventory.length(); i++) {
      JSONObject item = inventory.optJSONObject(i);
      if (item == null || !id.equals(normalizedItemId(item))) continue;
      JSONObject definition = itemDefinition(id, 1);
      item.put("id", id);
      item.put("name", definition.getString("name"));
      item.put("kind", "consumable");
      item.put("category", definition.getString("category"));
      item.put("stackable", true);
      item.put("effects", definition.getJSONObject("effects"));
      item.put("quantity", Math.max(1, item.optInt("quantity", 1)) + Math.max(1, quantity));
      state.put("inventory", inventory);
      return;
    }
    inventory.put(itemDefinition(id, quantity));
    state.put("inventory", inventory);
  }

  private static JSONArray inventoryFor(JSONObject state, String rawOwnerId, boolean create)
      throws Exception {
    String ownerId = CharacterProgressionCore.normalizeCharacterId(rawOwnerId);
    if (ownerId.isEmpty() || "cao_minh".equals(ownerId)) {
      JSONArray inventory = state.optJSONArray("inventory");
      if (inventory == null && create) {
        inventory = new JSONArray();
        state.put("inventory", inventory);
      }
      return inventory;
    }

    JSONObject owner = findPartyMember(state.optJSONArray("party"), ownerId);
    if (owner == null || !CharacterEncounterCore.isJoinedMember(owner)) {
      throw new IllegalArgumentException("Nhân vật không có trong party.");
    }
    JSONArray inventory = owner.optJSONArray("inventory");
    if (inventory == null && create) {
      inventory = new JSONArray();
      owner.put("inventory", inventory);
    }
    return inventory;
  }

  private static void setInventoryFor(JSONObject state, String rawOwnerId, JSONArray inventory)
      throws Exception {
    String ownerId = CharacterProgressionCore.normalizeCharacterId(rawOwnerId);
    if (ownerId.isEmpty() || "cao_minh".equals(ownerId)) {
      state.put("inventory", inventory == null ? new JSONArray() : inventory);
      return;
    }
    JSONObject owner = findPartyMember(state.optJSONArray("party"), ownerId);
    if (owner == null || !CharacterEncounterCore.isJoinedMember(owner)) {
      throw new IllegalArgumentException("Nhân vật không có trong party.");
    }
    owner.put("inventory", inventory == null ? new JSONArray() : inventory);
  }

  private static String inventoryOwnerName(JSONObject state, String rawOwnerId) {
    String ownerId = CharacterProgressionCore.normalizeCharacterId(rawOwnerId);
    if (ownerId.isEmpty() || "cao_minh".equals(ownerId)) {
      JSONObject player = state == null ? null : state.optJSONObject("player");
      return player == null ? "Cao Minh" : player.optString("name", "Cao Minh");
    }
    JSONObject owner = findPartyMember(state == null ? null : state.optJSONArray("party"), ownerId);
    return owner == null ? ownerId : owner.optString("name", ownerId);
  }

  private static int findItemIndex(JSONArray inventory, String itemId) {
    String requested = itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
    for (int i = 0; i < inventory.length(); i++) {
      JSONObject item = inventory.optJSONObject(i);
      if (item == null) continue;
      String id = normalizedItemId(item);
      String name = item.optString("name", "").trim().toLowerCase(Locale.ROOT);
      if (requested.equals(id) || requested.equals(name)) return i;
    }
    return -1;
  }

  private static String normalizedItemId(JSONObject item) {
    String id = item == null ? "" : item.optString("id", "").trim();
    if (!id.isEmpty()) return id.toLowerCase(Locale.ROOT);
    return GameCoreRules.stableItemId(item == null ? "" : item.optString("name", ""));
  }

  private static void consumeStack(JSONArray inventory, int index, int quantity) throws Exception {
    JSONObject item = inventory.getJSONObject(index);
    int remaining = Math.max(1, item.optInt("quantity", 1)) - quantity;
    if (remaining <= 0) inventory.remove(index);
    else item.put("quantity", remaining);
  }

  private static JSONObject findPartyMember(JSONArray party, String targetId) {
    if (party == null) return null;
    String requested = targetId == null ? "" : targetId.trim().toLowerCase(Locale.ROOT);
    for (int i = 0; i < party.length(); i++) {
      JSONObject member = party.optJSONObject(i);
      if (member == null) continue;
      String id = member.optString("id", "").trim().toLowerCase(Locale.ROOT);
      String name = member.optString("name", "").trim().toLowerCase(Locale.ROOT);
      if (requested.equals(id) || requested.equals(name) || requested.equals(String.valueOf(i))) return member;
    }
    return null;
  }

  private String applyEffects(JSONObject state, String targetId, String itemId, int quantity) throws Exception {
    int hunger = survivalCore.restoreFood(
        state, targetId, itemEffectValue(itemId, "hunger") * quantity);
    int thirst = survivalCore.restoreWater(
        state, targetId, itemEffectValue(itemId, "thirst") * quantity);
    int hp = progressionCore.healCurrentHp(
        state, targetId, itemEffectValue(itemId, "hp") * quantity);

    StringBuilder result = new StringBuilder();
    if (hunger > 0) result.append("Đói +").append(hunger);
    if (thirst > 0) {
      if (result.length() > 0) result.append(", ");
      result.append("Khát +").append(thirst);
    }
    if (hp > 0) {
      if (result.length() > 0) result.append(", ");
      result.append("HP +").append(hp);
    }
    if (result.length() == 0) return "Không có chỉ số nào thay đổi.";
    return result.append('.').toString();
  }

  private JSONObject flags(JSONObject state) throws Exception {
    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) flags = new JSONObject();
    state.put("flags", flags);
    return flags;
  }

  private int nextRoll(int bound) {
    return nextRoll(rng, bound);
  }

  private static int nextRoll(IntRng source, int bound) {
    int value = source.nextInt(bound);
    if (value < 0 || value >= bound) throw new IllegalStateException("RNG returned an out-of-range value");
    return value;
  }

}
