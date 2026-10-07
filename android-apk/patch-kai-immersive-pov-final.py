from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")

anchor = (
    '      "Người chơi chỉ điều khiển hành động có chủ ý của Kai; GM không tự chọn thay. GAMEPLAY_ROLLS do Android sinh là bất biến. " +\n'
)
rule = (
    '      "POV HARD LOCK: người chơi nhập vai trực tiếp Kai Akechi. Mọi văn xuôi gameplay phải kể ở ngôi thứ ba giới hạn theo trải nghiệm của Kai: gọi nhân vật chính là \'Kai\' hoặc \'hắn\'; tuyệt đối không dùng \'bạn\' để gọi hoặc thay thế Kai trong lời kể. Chỉ mô tả những gì Kai trực tiếp thấy, nghe, cảm nhận hoặc có cơ sở biết. Không tự viết suy nghĩ, quyết định, lời thoại hay hành động có chủ ý mới thay cho người chơi; chỉ thuật lại hậu quả hợp lệ của hành động người chơi đã nhập và các phản ứng ngoài quyền kiểm soát có căn cứ từ state/canon. NPC và Entity vẫn được kể bình thường trong phạm vi Kai có thể nhận biết. " +\n'
)

if rule not in text:
    count = text.count(anchor)
    if count != 1:
        raise RuntimeError(f"Kai immersive POV anchor: expected exactly 1 match, found {count}")
    text = text.replace(anchor, anchor + rule, 1)

required = (
    "POV HARD LOCK: người chơi nhập vai trực tiếp Kai Akechi.",
    "Mọi văn xuôi gameplay phải kể ở ngôi thứ ba giới hạn theo trải nghiệm của Kai",
    "gọi nhân vật chính là 'Kai' hoặc 'hắn'",
    "tuyệt đối không dùng 'bạn' để gọi hoặc thay thế Kai trong lời kể",
    "Không tự viết suy nghĩ, quyết định, lời thoại hay hành động có chủ ý mới thay cho người chơi",
)
for marker in required:
    if marker not in text:
        raise RuntimeError("Kai immersive POV contract missing: " + marker)

MAIN.write_text(text, encoding="utf-8")
print("Kai immersive POV installed: gameplay narration is third-person limited through Kai; GM uses Kai/hắn instead of bạn and cannot choose for him.")
