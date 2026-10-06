from pathlib import Path


MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

text = MAIN.read_text(encoding="utf-8")

old_inventory = r'''  private JSONArray sanitizedInventory(JSONArray current, JSONArray proposed, JSONObject rolls) throws Exception {
    if (proposed == null) return current == null ? new JSONArray() : new JSONArray(current.toString());
    JSONArray safe = new JSONArray();
    for (int i = 0; i < proposed.length(); i++) {
      Object item = proposed.opt(i);
      String name = itemName(item);
      boolean existing = arrayHasName(current, name);
      boolean madGod = lower(name).contains("madgod");
      boolean almond = lower(name).contains("almond water");
      boolean allowed = existing || (!madGod && almond && rollSuccess(rolls, "almondWater")) ||
        (!madGod && !almond && rollSuccess(rolls, "loot"));
      if (allowed) safe.put(item);
    }
    return safe;
  }
'''

new_inventory = r'''  private JSONArray sanitizedInventory(JSONArray current, JSONArray proposed, JSONObject rolls, String action) throws Exception {
    if (proposed == null) return current == null ? new JSONArray() : new JSONArray(current.toString());
    JSONArray safe = new JSONArray();
    JSONObject lootRoll = rolls.optJSONObject("loot");
    JSONObject waterRoll = rolls.optJSONObject("almondWater");
    boolean lootEligible = lootRoll != null && lootRoll.optBoolean("eligible", false);
    boolean waterEligible = waterRoll != null && waterRoll.optBoolean("eligible", false);
    boolean acquisitionIntent = containsAny(action,
      "nhặt", "lấy", "cầm", "thu hồi", "tịch thu", "nhận", "cất", "bỏ vào", "đưa vào omnivault", "store", "sao chép", "copy");

    for (int i = 0; i < proposed.length(); i++) {
      Object item = proposed.opt(i);
      String name = itemName(item);
      boolean existing = arrayHasName(current, name);
      boolean madGod = lower(name).contains("madgod");
      boolean almond = lower(name).contains("almond water");
      boolean allowed;

      if (existing) {
        // Existing ownership may change quantity/state, including Omnivault storage/copy/use.
        allowed = true;
      } else if (acquisitionIntent) {
        // The GM may add an item explicitly acquired from the established scene/state.
        // The prompt below remains responsible for rejecting nonexistent or invented objects.
        allowed = true;
      } else if (madGod) {
        allowed = rollSuccess(rolls, "madGodSet");
      } else if (almond) {
        // If this was not a water-discovery roll, do not delete established/passed-in water.
        allowed = !waterEligible || rollSuccess(rolls, "almondWater");
      } else {
        // New discovered loot still requires the loot roll when a search is actually happening.
        allowed = !lootEligible || rollSuccess(rolls, "loot");
      }

      if (allowed) safe.put(item);
    }
    return safe;
  }
'''

if text.count(old_inventory) != 1:
    raise RuntimeError(f"inventory gate patch expected 1 match, found {text.count(old_inventory)}")
text = text.replace(old_inventory, new_inventory, 1)

old_call = 'sanitizedInventory(before.optJSONArray("inventory"), generated.optJSONArray("inventory"), rolls)'
new_call = 'sanitizedInventory(before.optJSONArray("inventory"), generated.optJSONArray("inventory"), rolls, action)'
if text.count(old_call) != 1:
    raise RuntimeError(f"inventory call patch expected 1 match, found {text.count(old_call)}")
text = text.replace(old_call, new_call, 1)

prompt_anchor = (
    '            "Nếu meta=true, chỉ trả thông tin được hỏi; không tạo biến cố, không đổi state và snapshotEvent phải false. " +\n'
)
prompt_inventory = (
    prompt_anchor
    + '            "INVENTORY là sổ sở hữu/bảo quản bắt buộc của Kai. Bất kỳ vật vô tri nào Kai thực sự nhặt, lấy, nhận, thu hồi hoặc cất vào Omnivault phải được thêm/cập nhật inventory ngay trong lượt đó. Vật cất trong Omnivault vẫn nằm trong inventory với state lưu trữ phù hợp. Copy giữ lại phải tăng quantity; copy trao ngay cho người khác không tính vào tài sản cuối lượt. Chỉ giảm/xóa khi vật thực sự được tiêu thụ, phá hủy, mất, vứt hoặc trao đi. Không tự xóa đồ chỉ vì reply không nhắc tới. Chỉ thêm vật thực sự tồn tại/đã được xác nhận hoặc loot/water roll cho phép; nhìn thấy không đồng nghĩa sở hữu. " +\n'
)
if text.count(prompt_anchor) != 1:
    raise RuntimeError(f"inventory prompt patch expected 1 match, found {text.count(prompt_anchor)}")
text = text.replace(prompt_anchor, prompt_inventory, 1)

MAIN.write_text(text, encoding="utf-8")
print("Inventory ownership persistence patch applied")
