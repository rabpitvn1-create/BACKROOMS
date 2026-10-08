# Thứ tự hành trình Level 0–13 (khu phụ nằm trong Level gốc)

**Trạng thái:** 67 điểm được sắp xếp trong code; **chưa mở các route gameplay mới**.

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

**Level 13:** Chặng cuối đang được đăng ký.

## Triển khai và ranh giới quyền hạn

- `WorldJourneyOrder.kt` dùng `GROUPS.orderedChildKeys` để ghi chính xác các sublevel và named areas **đan xen**. `STOPS`, `nextAfter`, `previousBefore`, `groupStops` chỉ dùng tra cứu. Hai view `numberedSublevelIds` và `namedSectionKeys` còn được giữ để tra cứu theo loại.
- `WorldContentCatalog.kt` là nơi xác lập Level mẹ, tên và `PROJECT_CANON` / `EXTERNAL_REFERENCE` / `OPEN`. Không tự nâng tên Wiki lên canon Project.
- `WorldProgressionCore.kt` vẫn khóa **50 node, các rank hiện có, và 42 legacy edges**. Không cấp WorldNodeId/rank giả cho các khu phụ có tên.
- Runtime vẫn dựa vào exit resolver hiện hành. Đây là **thiết kế thứ tự**, không phải hành trình đã chạy được trong APK. Mở route mới cần PR riêng để thẩm định exit gate, save, state, combat và các trường hợp skip/backtrack.

## Kiểm chứng

`WorldJourneyOrderTest.kt` chốt toàn bộ danh sách 67 bước, vị trí 17 khu phụ trong đúng Level mẹ, Red Rooms ngay trước Level 1, thứ tự rank của 36 sublevel, phép tra cứu trước/sau và sự bất biến của rank/exit.
