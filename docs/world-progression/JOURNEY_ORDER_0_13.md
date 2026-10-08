# Thứ tự hành trình Level 0–13: registry đã tinh gọn

**Cập nhật 09/10/2026.** Chỉ giữ **39 địa điểm** trong canon cảnh quan hoạt động: **14 Level chính + 8 Sub-level đã duyệt + 17 khu phụ có tên**. Trong đó có **24 điểm đi được theo tuyến exit**: 14 Level, 8 Sub-level và 2 khu phụ Red Rooms, Base Alpha. **15 khu phụ còn lại chỉ lưu metadata/scene, không có tuyến thoát.**

## Danh sách Sub-level được giữ

- Level 0.2 — Remodeled Mess
- Level 1.2 — Concrete Garden; Level 1.5 — Inverted
- Level 5.1 — Terror Hotel Casino
- Level 6.1 — The Snackrooms
- Level 7.7 — The Forsaken Debris
- Level 10.1 — Corpse Lake
- Level 11.3 — The Red Light District

**28 Sub-level còn lại đã bị loại khỏi** `WorldProgressionCore.NODES`, `WorldContentCatalog.sublevels`, `WorldJourneyOrder.GROUPS`, ảnh snapshot WebP riêng, hồ sơ cảnh trong `BACKROOMS_WORLD.md` và các bản ghi/chủ đề liên quan của `knowledge_db.json` / manifest. Git history vẫn giữ bản cũ để có thể khôi phục có kiểm soát.

## Hành trình metadata theo Level mẹ

Bảng này thể hiện **cả khu phụ chỉ làm bối cảnh**. Không tự suy ra rằng mọi điểm trong bảng đã mở gameplay.

| Level chính | Các điểm còn trong danh mục theo thứ tự |
| --- | --- |
| Level 0 | Level ε → LS-2 → Manila Room → The Torment → **Level 0.2** → Dullness → **Red Rooms** |
| Level 1 | **Base Alpha** → Traders Vault → **Level 1.2** → **Level 1.5** |
| Level 2 | Office Space EL3A |
| Level 3 | Không có Sub-level hoặc khu phụ trong danh mục |
| Level 4 | The Office Market |
| Level 5 | **Level 5.1** |
| Level 6 | **Level 6.1** |
| Level 7 | The Hadal Zone → **Level 7.7** |
| Level 8 | The Sanctum Subterraneous |
| Level 9 | Không có Sub-level hoặc khu phụ trong danh mục |
| Level 10 | **Level 10.1** |
| Level 11 | Asset 11.1 → Scene-01.2 → AFTER HOURS → The Headquarters → Radio Backrooms' Studio → **Level 11.3** |
| Level 12–13 | Không có Sub-level hoặc khu phụ trong danh mục |

Các khu phụ có tên không được gán WorldNodeId hay progressionRank riêng. Red Rooms dùng rank của Level 0.2; Base Alpha dùng rank của Level 1. Những khu phụ chưa được duyệt không được tự động đưa vào tuyến chơi.

## Tuyến exit gameplay duy nhất đang kích hoạt

```text
Level 0 -> Level 0.2 -> Red Rooms
-> Level 1 -> Base Alpha -> Level 1.2 -> Level 1.5
-> Level 2 -> Level 3 -> Level 4
-> Level 5 -> Level 5.1
-> Level 6 -> Level 6.1
-> Level 7 -> Level 7.7
-> Level 8 -> Level 9
-> Level 10 -> Level 10.1
-> Level 11 -> Level 11.3
-> Level 12 -> Level 13 (kết thúc)
```

Mỗi chặng cần 5 lần RNG thắng liên tiếp (50/50) ở lượt ngoài combat; thất bại reset streak, combat giữ nguyên. Chuyển chặng reset streak và lưu `journeyStopKey` + `worldNodeId` qua Android Core. Tên Wiki, thứ tự danh mục và văn bản AI không mở thêm exit.

## Kiểm tra và ranh giới

- `WorldProgressionCore` có **22 node**, vẫn giữ rank nguyên bản của tất cả node được giữ; **64 cạnh đã duyệt** (42 legacy, 7 tuyến main-Level, 15 cạnh bổ sung cho Sub-level).
- `WorldJourneyOrder` có 39 điểm, gồm 22 node có rank và 17 khu phụ không có rank; `FeaturedJourneyRoutes` chỉ mở đúng 24 điểm.
- Bộ ảnh và `BACKROOMS_WORLD.md` được đối chiếu với registry mỗi lần build; `knowledge_db.json` tái tạo từ canon đã cắt.
- Không sửa lore nhân vật, EntityCore, luật chiến đấu, những khu phụ chưa được chọn hoặc ảnh của 14 Level chính.
