from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str):
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    text = text.replace(old, new, 1)

old_gate = '''        boolean confirmedMundanePickup = "gm_confirmed_pickup".equals(lower(op.optString("basis", ""))) && mundanePickupName(name);'''
new_gate = '''        String pickupBasis = lower(op.optString("basis", ""));
        boolean confirmedMundanePickup = ("gm_confirmed_pickup".equals(pickupBasis) || "omnivault_restore".equals(pickupBasis)) && mundanePickupName(name);'''
replace_once(old_gate, new_gate, "Omnivault restore authority basis")

old_ops = '''    JSONArray ops = generated.optJSONArray("ops");
    if (ops == null) ops = new JSONArray();
    boolean matched = false;
'''
new_ops = '''    JSONArray ops = generated.optJSONArray("ops");
    if (ops == null) ops = new JSONArray();
    if ("omnivault_restore".equals(basis) && !source.isEmpty() &&
        !(pickupTokenOverlapAndroid(source, finalName) && pickupTokenOverlapAndroid(finalName, source))) {
      for (int i = ops.length() - 1; i >= 0; i--) {
        JSONObject sourceOp = ops.optJSONObject(i);
        if (sourceOp == null || !"inventory_upsert".equalsIgnoreCase(sourceOp.optString("type", ""))) continue;
        JSONObject sourceItem = sourceOp.optJSONObject("item");
        String sourceName = sourceItem != null ? sourceItem.optString("name", "") : "";
        boolean sameSource = !sourceName.isEmpty() && pickupTokenOverlapAndroid(sourceName, source) && pickupTokenOverlapAndroid(source, sourceName);
        boolean sameTarget = !sourceName.isEmpty() && pickupTokenOverlapAndroid(sourceName, finalName) && pickupTokenOverlapAndroid(finalName, sourceName);
        if (sameSource && !sameTarget) ops.remove(i);
      }
    }
    boolean matched = false;
'''
replace_once(old_ops, new_ops, "drop pre-restore source operation")

old_prompt = '''      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory. " +'''
new_prompt = '''      "Inventory là sổ continuity/sở hữu của Kai, không phải cơ chế encumbrance hay survival scarcity để nerf Kai. Vật vô tri cất trong Omnivault vẫn phải hiện trong inventory. " +
      "Loot roll chỉ quyết định đồ mới do thế giới sinh ra; không được dùng loot roll để khóa Store/Restore/Copy hợp canon của Omnivault sau khi vật nguồn đã được cảnh xác nhận. " +
      "Khi player dùng Hoàn nguyên trên vật vô tri hợp lệ, phải mô tả đúng kết quả và inventory_upsert phải ghi vật SAU HOÀN NGUYÊN nếu player cất nó. Ví dụ: vỏ chai -> hoàn nguyên thành chai nước -> cất kho thì inventory có chai nước. " +
      "Không dựng thiếu đạn, nước, đồ ăn hay vật tư thông thường chỉ để cân bằng Kai; thử thách phải đến từ thông tin, mục tiêu bảo vệ, không gian, quan hệ, lựa chọn hoặc đối thủ hợp canon. " +
      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory. " +'''
replace_once(old_prompt, new_prompt, "Kai resource policy prompt")

for required in [
    '"omnivault_restore".equals(pickupBasis)',
    'if ("omnivault_restore".equals(basis) && !source.isEmpty()',
    "ops.remove(i)",
    "Inventory là sổ continuity/sở hữu của Kai",
    "vỏ chai -> hoàn nguyên thành chai nước -> cất kho",
    "Không dựng thiếu đạn, nước, đồ ăn hay vật tư thông thường",
]:
    if required not in text:
        raise RuntimeError(f"Kai resource policy marker missing: {required}")

MAIN.write_text(text, encoding="utf-8")
print("Kai overpower resource policy applied: Omnivault Restore replaces the source continuity record and is not loot scarcity.")
