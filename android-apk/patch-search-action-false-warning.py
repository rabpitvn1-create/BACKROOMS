from pathlib import Path

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
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

# Regression contract for the screenshot report: planning/search language must fall through to
# the normal GM path even if LiteRT happens to classify it as PICKUP_ITEM.
required = [
    "if (isDirectPlayerPickupAction(action))",
    "player_pickup_unavailable",
    "Hành động này không khả dụng trong trạng thái hiện tại.",
]
for token in required:
    if token not in text:
        raise RuntimeError(f"Search-action warning fix missing contract: {token}")

FACADE.write_text(text, encoding="utf-8")
print("Search/planning actions no longer inherit LiteRT pickup false-positive warnings; explicit pickup rejection remains authoritative.")
