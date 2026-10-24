# Level 1–13: Canon Runtime 2.0 coverage

Nguồn ảnh: `canon_sources/Asset/02_Level/LEVEL_0_CLOUDINARY_MAPPING.json` (filename lịch sử chứa toàn bộ Level).

- 13 Level chính, 7 sublevel, 11 Area, tổng 31 node và 45 ảnh.
- 31 thẻ node có `INFERRED` / `PENDING_AUTHOR_REVIEW`; tên và ảnh xác minh được, lore riêng phần lớn chưa có.
- 7 Level nền `LEVEL.07`–`LEVEL.13` được thêm: Level 7–10 đọc World Canon; Level 11–13 chỉ có asset, điều kiện môi trường UNKNOWN.
- Giữ nguyên `LEVEL.01`–`LEVEL.06` của seed. Level 6 tuân thủ hard lock tundra tối vĩnh viễn.
- Không tạo liên kết di chuyển; `parent_node_key` chỉ phân cấp metadata. Không tạo sự kiện, spawn, loot, gây sát thương hoặc thay đổi state.

| Node | Tên | Ảnh | Parent |
|---|---|---:|---|
| `1` | Level 1 — Habitable Zone | 3 | Main |
| `1.2` | Level 1.2 | 3 | 1 |
| `1.5` | Level 1.5 | 1 | 1 |
| `1/base_alpha` | Level 1 / Base Alpha | 1 | 1 |
| `1/traders_vault` | Level 1 / Traders Vault | 1 | 1 |
| `2` | Level 2 — Abandoned Utility Halls / Pipe Dreams | 3 | Main |
| `2/office_space_el3a` | Level 2 / Office Space EL3A | 1 | 2 |
| `3` | Level 3 — Electrical Station | 3 | Main |
| `4` | Level 4 — Abandoned Office | 3 | Main |
| `4/the_office_market` | Level 4 / The Office Market | 1 | 4 |
| `5` | Level 5 — Terror Hotel | 3 | Main |
| `5.1` | Level 5.1 | 1 | 5 |
| `6` | Level 6 — Lights Out | 3 | Main |
| `6.1` | Level 6.1 | 1 | 6 |
| `7` | Level 7 — Thalassophobia | 1 | Main |
| `7.7` | Level 7.7 | 1 | 7 |
| `7/the_hadal_zone` | Level 7 / The Hadal Zone | 1 | 7 |
| `8` | Level 8 — Cave Systems | 1 | Main |
| `8/the_sanctum_subterraneous` | Level 8 / The Sanctum Subterraneous | 1 | 8 |
| `9` | Level 9 — The Suburbs | 1 | Main |
| `10` | Level 10 — Bumper Crop | 1 | Main |
| `10.1` | Level 10.1 | 1 | 10 |
| `11` | Level 11 — Main | 1 | Main |
| `11.3` | Level 11.3 | 1 | 11 |
| `11/after_hours` | Level 11 / After Hours | 1 | 11 |
| `11/asset_11p1` | Level 11 / Asset 11p1 | 1 | 11 |
| `11/radio_backrooms_studio` | Level 11 / Radio Backrooms Studio | 1 | 11 |
| `11/scene_01p2` | Level 11 / Scene 01p2 | 1 | 11 |
| `11/the_headquarters` | Level 11 / The Headquarters | 1 | 11 |
| `12` | Level 12 — Main | 1 | Main |
| `13` | Level 13 — Main | 1 | Main |

CR2 load theo scene; không bật feature flag hay triển khai node navigation trong đợt này.
