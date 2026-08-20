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

old_direct_candidate = '''    for (String regex : direct) {
      String candidate = pickupRegexCandidateAndroid(action, regex);
      if (!candidate.isEmpty()) return candidate;
    }
    String[] introduced = new String[] {'''
new_direct_candidate = '''    for (String regex : direct) {
      String candidate = pickupRegexCandidateAndroid(action, regex);
      String weak = lower(candidate);
      boolean directionalOnly = weak.equals("lên") || weak.equals("ra") || weak.equals("vào") || weak.equals("xuống") ||
        weak.equals("lại") || weak.equals("qua") || weak.equals("đi") || weak.equals("đến") || weak.equals("nó") || weak.equals("đó") ||
        weak.equals("up") || weak.equals("out") || weak.equals("in") || weak.equals("down") || weak.equals("back") || weak.equals("it") || weak.equals("that");
      if (!candidate.isEmpty() && !directionalOnly) return candidate;
    }
    String[] introduced = new String[] {'''
replace_once(old_direct_candidate, new_direct_candidate, "directional pickup candidate rejection")

old_ops = '''    JSONArray ops = generated.optJSONArray("ops");
    if (ops == null) ops = new JSONArray();
    boolean matched = false;
'''
new_ops = '''    JSONArray ops = generated.optJSONArray("ops");
    if (ops == null) ops = new JSONArray();
    boolean matched = false;
'''
replace_once(old_ops, new_ops, "keep restore reconciliation resource-conserving")

old_prompt = '''      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory. " +'''
new_prompt = '''      "Inventory là sổ continuity/sở hữu của Kai, không phải cơ chế encumbrance. Vật vô tri cất trong Omnivault vẫn phải hiện trong inventory. " +
      "KHÓA CỨNG HOÀN NGUYÊN: Restore chỉ đưa CHÍNH VẬT PHẨM ĐÓ về trạng thái vật lý tốt nhất có thể; giữ nguyên identity và số lượng object. Restore tuyệt đối không tạo vật phẩm mới, không đổi vật chứa thành một item tài nguyên khác, không refill và không tăng nước/thức ăn/đạn/nhiên liệu/thuốc/điện tích/nguyên liệu đã tiêu hao. " +
      "Với vật chứa, chỉ cấu trúc vỏ/nắp/niêm phong được phục hồi; lượng vật chất bên trong sau Restore phải bằng lượng còn sót lại trước Restore. Ví dụ vỏ chai nước rỗng có thể trở về chai nguyên vẹn/đóng nắp nhưng vẫn 0 nước; hộp thức ăn rỗng có thể về hộp nguyên vẹn nhưng vẫn 0 thức ăn. " +
      "Copy là chức năng duy nhất trong nhóm này có thể tạo thêm object từ scan template; không được dùng Restore như Copy trá hình. " +
      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory. " +'''
replace_once(old_prompt, new_prompt, "Kai resource policy prompt")

for required in [
    '"omnivault_restore".equals(pickupBasis)',
    'boolean directionalOnly = weak.equals("lên")',
    'if (!candidate.isEmpty() && !directionalOnly) return candidate;',
    "KHÓA CỨNG HOÀN NGUYÊN",
    "vẫn 0 nước",
    "vẫn 0 thức ăn",
    "Copy là chức năng duy nhất",
]:
    if required not in text:
        raise RuntimeError(f"Kai resource policy marker missing: {required}")

MAIN.write_text(text, encoding="utf-8")
print("Kai resource policy applied: Omnivault Restore repairs the same object while conserving all consumed resources; Restore can never behave like Copy/refill.")
