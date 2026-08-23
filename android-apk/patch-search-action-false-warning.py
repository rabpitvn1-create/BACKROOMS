from pathlib import Path

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
KNOWLEDGE_BUILDER = ROOT / "patch-knowledge-context-builder.py"
text = FACADE.read_text(encoding="utf-8")

# LiteRT is allowed to suggest an intent, but a classifier guess must never turn ordinary
# exploration/planning prose into an authoritative pickup rejection. Only explicit pickup
# language may take the deterministic rejection path.
old_guard = "    if (isDirectPlayerPickupAction(action) || interpreted.candidates.any { it.intent == GameIntent.PICKUP_ITEM }) {\n"
new_guard = "    if (isDirectPlayerPickupAction(action)) {\n"
if new_guard not in text:
    count = text.count(old_guard)
    if count != 1:
        raise RuntimeError(f"Pickup false-positive guard expected one legacy match, found {count}")
    text = text.replace(old_guard, new_guard, 1)

if old_guard in text:
    raise RuntimeError("LiteRT PICKUP_ITEM classification can still reject non-pickup prose")

# A classifier guess must not silently discard an inventory delta already validated by the
# Android reducer. Only an explicit player pickup assertion or restore attempt locks Inventory.
old_inventory_lock = "    val inventoryLocked = isDirectPlayerPickupAction(action) || GameIntent.PICKUP_ITEM in actionIntents || GameIntent.OMNIVAULT_RESTORE in actionIntents\n"
new_inventory_lock = "    val inventoryLocked = isDirectPlayerPickupAction(action) || GameIntent.OMNIVAULT_RESTORE in actionIntents\n"
if new_inventory_lock not in text:
    count = text.count(old_inventory_lock)
    if count != 1:
        raise RuntimeError(f"Validated inventory lock expected one legacy match, found {count}")
    text = text.replace(old_inventory_lock, new_inventory_lock, 1)

# "bỏ ... vào kho đồ" is storage/reorganization language, not proof that a player magically
# acquired a missing item. Keep explicit "thêm/đưa vào Inventory" assertions blocked.
old_inventory_assertion = r'''    val inventoryAssertion = Regex("(?:thêm|bỏ|đưa).{0,80}(?:vào|trong)\\s+(?:inventory|kho đồ|túi đồ)", RegexOption.IGNORE_CASE)
'''
new_inventory_assertion = r'''    val inventoryAssertion = Regex("(?:thêm|đưa).{0,80}(?:vào|trong)\\s+(?:inventory|kho đồ|túi đồ)", RegexOption.IGNORE_CASE)
'''
if new_inventory_assertion not in text:
    count = text.count(old_inventory_assertion)
    if count != 1:
        raise RuntimeError(f"Inventory assertion expected one legacy match, found {count}")
    text = text.replace(old_inventory_assertion, new_inventory_assertion, 1)

# Keep genuine validation feedback understandable instead of the generic English warning that
# made a normal search/planning turn look like a broken action.
translations = {
    '"precise_content_amount_forbidden" -> "This action is not available."': '"precise_content_amount_forbidden" -> "Hành động này không khả dụng với lượng nội dung được chỉ định."',
    '"item_content_empty" -> "This action is not available."': '"item_content_empty" -> "Vật phẩm này hiện không có nội dung khả dụng."',
    '"insufficient_item_quantity", "item_not_owned" -> "This action is not available."': '"insufficient_item_quantity", "item_not_owned" -> "Kai không có đủ vật phẩm cần thiết cho hành động này."',
    'else -> "This action is not available."': 'else -> "Hành động này không khả dụng trong trạng thái hiện tại."',
}
for old, new in translations.items():
    if old in text:
        text = text.replace(old, new)

pickup_line = '      "player_pickup_unavailable" -> "Không thể tự thêm vật phẩm vào Inventory; hãy tìm kiếm hoặc tương tác với môi trường để game xác định kết quả."\n'
party_anchor = '      "party_full" -> "Party đã đủ tối đa bốn thành viên."\n'
if pickup_line not in text:
    if party_anchor not in text:
        raise RuntimeError("validationReply party anchor missing")
    text = text.replace(party_anchor, pickup_line + party_anchor, 1)

FACADE.write_text(text, encoding="utf-8")

# The Android reducer must accept a world-generated handoff only when the operation explicitly
# identifies itself as a validated world consequence and the structured state/roll supports it.
java = MAIN.read_text(encoding="utf-8")
old_world_inventory = r'''        boolean allowedNew = false;
        JSONObject beforeFlagsForItem = before.optJSONObject("flags");
        JSONObject beforeMadGodForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("madGod") : null;
        JSONObject explorationForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("exploration") : null;
        JSONObject omnivaultForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("omnivault") : null;
        boolean establishedStructured = false;
        if (explorationForItem != null) establishedStructured = lower(explorationForItem.toString()).contains(lower(name));
        if (!establishedStructured && omnivaultForItem != null) establishedStructured = lower(omnivaultForItem.toString()).contains(lower(name));
        if (!establishedStructured && beforeMadGodForItem != null) establishedStructured = lower(beforeMadGodForItem.toString()).contains(lower(name));
        boolean madGodAlreadySpawned = beforeMadGodForItem != null && beforeMadGodForItem.optBoolean("spawned", false);
        if (existing >= 0) allowedNew = true;
        else if (acquisitionIntent(action)) {
          if (madGod) allowedNew = madGodAlreadySpawned && establishedStructured;
          else if (almond) allowedNew = establishedStructured || rollSuccess(rolls, "almondWater");
          else if (containsAny(action, "copy", "sao chép")) allowedNew = establishedStructured;
          else allowedNew = establishedStructured || rollSuccess(rolls, "loot");
        }
'''
new_world_inventory = r'''        boolean allowedNew = false;
        JSONObject beforeFlagsForItem = before.optJSONObject("flags");
        JSONObject beforeMadGodForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("madGod") : null;
        JSONObject explorationForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("exploration") : null;
        JSONObject omnivaultForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("omnivault") : null;
        boolean establishedStructured = false;
        if (explorationForItem != null) establishedStructured = lower(explorationForItem.toString()).contains(lower(name));
        if (!establishedStructured && omnivaultForItem != null) establishedStructured = lower(omnivaultForItem.toString()).contains(lower(name));
        if (!establishedStructured && beforeMadGodForItem != null) establishedStructured = lower(beforeMadGodForItem.toString()).contains(lower(name));
        boolean madGodAlreadySpawned = beforeMadGodForItem != null && beforeMadGodForItem.optBoolean("spawned", false);
        String acquisitionBasis = lower(op.optString("basis", "")).trim();
        boolean worldAcquisition = acquisitionBasis.equals("world_consequence");
        boolean directAcquisition = acquisitionIntent(action);
        boolean copyIntent = containsAny(action, "copy", "sao chép", "nhân bản", "tạo thêm", "tạo ra thêm", "nhân thêm");
        boolean almondRoll = rollSuccess(rolls, "almondWater");
        boolean lootRoll = rollSuccess(rolls, "loot");
        if (existing >= 0) allowedNew = true;
        else if (madGod) allowedNew = directAcquisition && madGodAlreadySpawned && establishedStructured;
        else if (copyIntent) allowedNew = directAcquisition && establishedStructured;
        else if (almond) allowedNew = (directAcquisition || worldAcquisition) && (establishedStructured || almondRoll);
        else allowedNew = (directAcquisition || worldAcquisition) && (establishedStructured || lootRoll);
'''
if new_world_inventory not in java:
    count = java.count(old_world_inventory)
    if count != 1:
        raise RuntimeError(f"World acquisition reducer expected one legacy match, found {count}")
    java = java.replace(old_world_inventory, new_world_inventory, 1)
MAIN.write_text(java, encoding="utf-8")

# patch-knowledge-context-builder.py runs after this script in the final patch chain. Harden its
# writer prompt now so prose can never claim a handoff without proposing the matching state op.
builder = KNOWLEDGE_BUILDER.read_text(encoding="utf-8")
prompt_anchor = '      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory. " +\n'
world_prompt_line = r'''      "Khi GAMEPLAY_ROLLS hợp lệ tạo loot/Almond Water và reply xác nhận môi trường hoặc NPC thực sự giao vật đó cho Kai, bắt buộc kèm inventory_upsert với basis:\"world_consequence\" trong cùng response; nếu không có op hợp lệ thì không được kể rằng Kai đã nhận hoặc sở hữu vật. " +
'''
if world_prompt_line not in builder:
    if prompt_anchor not in builder:
        raise RuntimeError("Knowledge writer Inventory prompt anchor missing")
    builder = builder.replace(prompt_anchor, prompt_anchor + world_prompt_line, 1)
KNOWLEDGE_BUILDER.write_text(builder, encoding="utf-8")

# Regression contracts for bug #3.
final_facade = FACADE.read_text(encoding="utf-8")
final_java = MAIN.read_text(encoding="utf-8")
final_builder = KNOWLEDGE_BUILDER.read_text(encoding="utf-8")
for token in (
    "if (isDirectPlayerPickupAction(action))",
    new_inventory_lock.strip(),
    '(?:thêm|đưa).{0,80}(?:vào|trong)\\\\s+(?:inventory|kho đồ|túi đồ)',
    "player_pickup_unavailable",
    "Hành động này không khả dụng trong trạng thái hiện tại.",
):
    if token not in final_facade:
        raise RuntimeError(f"Search/world-acquisition facade contract missing: {token}")
for token in (
    'String acquisitionBasis = lower(op.optString("basis", "")).trim();',
    'boolean worldAcquisition = acquisitionBasis.equals("world_consequence");',
    '(directAcquisition || worldAcquisition)',
):
    if token not in final_java:
        raise RuntimeError(f"World acquisition reducer contract missing: {token}")
if 'basis:\\"world_consequence\\"' not in final_builder:
    raise RuntimeError("Writer prompt does not require world_consequence Inventory handoff")
if old_inventory_lock in final_facade or old_inventory_assertion in final_facade:
    raise RuntimeError("Legacy inventory false-positive lock survived")

print("World item handoffs now synchronize GM narrative, Android reducer and Core Inventory without weakening explicit pickup or Omnivault copy rules.")