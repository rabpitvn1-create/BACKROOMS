# BACKROOMS Text Game

Text game Next.js dùng state phía server, Gemini cho Game Master và snapshot, cùng canon được tích hợp từ bộ nguồn Drive của dự án.

## Các lớp canon đã tích hợp

- `lib/canon.js`: Prologue, Kai Akechi, loadout và state New Game.
- `lib/world-canon.js`: bản chất Backrooms, Level 0–6, chuyển vùng, Entity và tài nguyên.
- `lib/character-canon.js`: Iris và Syvial; codex đầy đủ chỉ được nạp khi encounter/continuity cần dùng.
- `lib/writing-canon.js`: điểm nhìn Kai, văn xuôi tiếng Việt, hội thoại tự nhiên và kinh dị dựa trên bằng chứng.
- `lib/gameplay.js`: phân loại gameplay/meta, eligibility và dice server cho survivor, reunion, hazard, Entity, loot, Almond Water và Exit.

Backend giữ quyền phán quyết đối với dice, reunion, encounter và chuyển Level. Model không được tự tăng xác suất, reroll, tạo tài nguyên hoặc thay đổi Level nếu điều kiện server chưa cho phép.

## APK độc lập

- APK nạp giao diện từ `android-apk/app/src/main/assets/index.html` và lưu save bằng bộ nhớ riêng trên thiết bị.
- Chuỗi build giữ Kai overlay, snapshot theo sự kiện, ảnh nền Level 0–6, fallback Game Master và Kai R05 codex.
- `android-apk/patch-drive-canon-gameplay.py` nạp Drive canon R06 cùng xúc xắc/gate gameplay vào bản Android sau các patch nền.
- Workflow phát hành hiện tạo `Backroom-1.1.40.apk` từ runtime độc lập này.

## Chạy cục bộ

```bash
npm install
npm test
npm run dev
```

Các biến môi trường chính:

- `GEMINI_API_KEY_1` (có thể thêm `_2`, `_3`)
- `GEMINI_MODEL`
- `GEMINI_IMAGE_MODEL`
- `DATABASE_URL` / `POSTGRES_URL` / `NEON_DATABASE_URL` (không bắt buộc; thiếu thì dùng bộ nhớ cục bộ của tiến trình)

Trong ô lệnh, dùng `/status`, `/inventory`, `/party`, `/rules` hoặc `/meta ...` để hỏi/kiểm tra mà không tăng Turn hay kích hoạt dice.
