# Thứ tự hành trình Level 0–13 (khu phụ nằm trong Level gốc)

**Trạng thái (2026-10-09):** 67 điểm được sắp xếp trong code; **24 điểm có tuyến gameplay được duyệt**: 14 Level chính + 8 Sub-level được chọn + 2 khu phụ (Red Rooms và Base Alpha). 28 Sub-level và 15 khu phụ còn lại chưa có route. Tất cả điểm chơi được dùng 5 streak liên tiếp (50/50) ngoài combat.

## Quy tắc sắp xếp

1. Giữ **14 Level chính theo thứ tự 0 → 13** và **36 sublevel đánh số tăng theo cấp Level**, không thay rank đã chốt.
2. **17 khu phụ có tên được đưa vào chính nhóm Level gốc**, thay vì bị gom thành một nhóm sau mọi sublevel đánh số. Một nhóm có thể đan xen sublevel và khu phụ.
3. Khi canon chỉ xác lập Level mẹ mà không xác lập vị trí trước/sau sublevel đánh số, dùng **quy ước biên tập của game:** đặt khu phụ **ngay sau Level mẹ**, không tuyên bố đó là đường đi tự nhiên hay thứ tự phân bố địa lý của Wiki.
4. Ngoại lệ có cơ sở trong Project: tài liệu `BACKROOMS_WORLD.md` liệt kê **Level ε** trước dãy 0.x và **Dullness** sau Level 0.99. Theo lệnh tác giả, **Red Rooms là điểm cuối của Level 0 trước Level 1**. LS-2, Manila Room, The Torment ở sát Level 0 vì nguồn chưa xác lập vị trí riêng của chúng.
5. **Asset 11.1** và **Scene-01.2** vẫn là `NAMED_SECTION`, không phải Level 11.1 hay sublevel đánh số; chúng chỉ nằm trong nhóm Level 11.
6. `OPEN` là placeholder để bổ sung canon về sau. **Thứ tự không tạo exit, spawn, loot, rank, hazard, hoặc khả năng NPC tự biết vị trí.**

## Bảng hành trình tích hợp

Các bước trong cột phải diễn ra **sau Level ở cột trái** và **trước Level mẹ tiếp theo**, theo thiết kế tuyến đề xuất:

| Level bắt đầu | Thứ tự các sublevel và khu phụ bên trong Level gốc |
| --- | --- |
| **Level 0** | Level ε → LS-2 → Manila Room → The Torment → Level 0.01 → Level 0.1 → Level 0.11 → Level 0.2 → Level 0.22 → Level 0.23 → Level 0.3 → Level 0.41 → Level 0.5 → Level 0.66 → Level 0.7 → Level 0.8 → Level 0.99 → Dullness → **Red Rooms** |
| **Level 1** | Base Alpha → Traders Vault → Level 1.1 → Level 1.2 → Level 1.3 → Level 1.5 |
| **Level 2** | Office Space EL3A → Level 2.1 |
| **Level 3** | Level 3.5 |
| **Level 4** | The Office Market |
| **Level 5** | Level 5.1 → Level 5.2 → Level 5.3 |
| **Level 6** | Level 6.1 → Level 6.2 → Level 6.3 → Level 6.31 |
| **Level 7** | The Hadal Zone → Level 7.6 → Level 7.7 → Level 7.8 |
| **Level 8** | The Sanctum Subterraneous → Level 8.1 |
| **Level 9** | Level 9.2 → Level 9.3 → Level 9.5 |
| **Level 10** | Level 10.1 → Level 10.2 |
| **Level 11** | Asset 11.1 — Private Enterprise → Scene-01.2 — The Refuge → AFTER HOURS → The Headquarters → Radio Backrooms' Studio → Level 11.3 |
| **Level 12** | Đi thẳng đến Level kế tiếp (riêng Level 13 là điểm kết) |
| **Level 13** | Đi thẳng đến Level kế tiếp (riêng Level 13 là điểm kết) |

**Level 0 đầy đủ:**
Level 0 → Level ε → LS-2 → Manila Room → The Torment → Level 0.01 → Level 0.1 → Level 0.11 → Level 0.2 → Level 0.22 → Level 0.23 → Level 0.3 → Level 0.41 → Level 0.5 → Level 0.66 → Level 0.7 → Level 0.8 → Level 0.99 → Dullness → **Red Rooms** → Level 1.

**Level 13:** Chặng cuối của tuyến 24 điểm, không sinh Level 14.

## Tuyến gameplay 24 điểm được duyệt

Thứ tự chính thức của `FeaturedJourneyRoutes.kt` (chỉ 10 địa điểm được tác giả chọn, không tự lấy tất cả điểm từ bảng biên tập):

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
-> Level 12 -> Level 13
```

Mỗi chặng chỉ thoát khi đủ 5 streak thắng liên tiếp, RNG 50/50; combat không làm thay đổi streak. `Red Rooms` và `Base Alpha` được lưu qua `journeyStopKey`, giữ rank của node đã ghé gần nhất thay vì cấp node/rank giả. Mỗi lần chuyển chặng reset streak về 0.

**Quan trọng:** Ví dụ `Level 0 -> Level 0.1 -> Red Rooms -> Level 1` chỉ giải thích cách đặt khu phụ theo Level mẹ. Level 0.1 không nằm trong 10 địa điểm được chọn; tuyến thực tế dùng Level 0.2.

## Triển khai và ranh giới quyền hạn

- `WorldJourneyOrder.kt` dùng `GROUPS.orderedChildKeys` để ghi chính xác các sublevel và named areas **đan xen**. `STOPS`, `nextAfter`, `previousBefore`, `groupStops` chỉ dùng tra cứu. Hai view `numberedSublevelIds` và `namedSectionKeys` còn được giữ để tra cứu theo loại.
- `WorldContentCatalog.kt` là nơi xác lập Level mẹ, tên và `PROJECT_CANON` / `EXTERNAL_REFERENCE` / `OPEN`. Không tự nâng tên Wiki lên canon Project.
- `WorldProgressionCore.kt` giữ nguyên **50 node, rank bất biến và 42 legacy edges**; thêm **7 cạnh chính** và **15 cạnh có chủ đích** cho 8 Sub-level, tổng cộng **64 cạnh**. Không cấp WorldNodeId/rank mới cho khu phụ.
- Runtime hiện dùng `FeaturedJourneyRoutes.next(stopKey)` cho **24 điểm được chọn**, thay vì nhảy thẳng mọi Level chính. Core kiểm tra từng cạnh, save lưu `journeyStopKey`, `worldNodeId`, `levelJson` và streak; Level 13 là điểm cuối, không tạo Level 14. `MainLevelExitRoutes` là API tương thích, không phải đường thoát runtime được kích hoạt. Đây **không** chứng minh toàn bộ 67 điểm đều đi qua được.
- Level 7–13 vẫn cần hoàn thiện balance Entity/hazard/loot và ảnh nền chuyên biệt; chưa tuyên bố đã đạt đủ tiêu chí playable theo `CONTENT_0_13_STATUS.md`.

## Kiểm chứng

`WorldJourneyOrderTest.kt` chốt toàn bộ danh sách 67 bước, vị trí 17 khu phụ trong đúng Level mẹ, Red Rooms ngay trước Level 1, thứ tự rank của 36 sublevel, phép tra cứu trước/sau và sự bất biến của rank/exit.
