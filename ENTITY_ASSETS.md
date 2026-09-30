# BACKROOMS Entity Assets

Toàn bộ sprite Entity dùng trong APK nằm trực tiếp tại:

`android-apk/app/src/main/assets/entity/`

Runtime overlay dùng canonical Entity key trùng chính xác với tên file bỏ phần mở rộng `.webp`. Không có manifest từ xa và không tải ảnh Entity từ mạng.

| Canonical Entity key | Local asset |
|---|---|
| `hound` | `hound.webp` |
| `clump` | `clump.webp` |
| `duller` | `duller.webp` |
| `deathmoth` | `deathmoth.webp` |
| `hostile_faceling` | `hostile_faceling.webp` |
| `false_puddle` | `false_puddle.webp` |
| `paintings` | `paintings.webp` |
| `smiler` | `smiler.webp` |
| `skin-stealer` | `skin-stealer.webp` |
| `predatory_window` | `predatory_window.webp` |
| `biological_pipeline` | `biological_pipeline.webp` |
| `wretch` | `wretch.webp` |
| `cable_mimic` | `cable_mimic.webp` |
| `the_beast_of_level_5` | `the_beast_of_level_5.webp` |
| `hotel_corpse_lure` | `hotel_corpse_lure.webp` |
| `jeff_the_killer` | `jeff_the_killer.webp` |
| `async_rifleman` | `async_rifleman.webp` |
| `copx` | `copx.webp` |
| `tam_ma_cao_minh` | `tam_ma_cao_minh.webp` |
| `jane_the_killer` | `jane_the_killer.webp` |
| `slenderman` | `slenderman.webp` |
| `diep_minh` | `diep_minh.webp` |

## Encounter runtime

Main Game Core owns Entity spawning through `EntityCore` and `app/src/main/assets/knowledge/entity_encounters.json`.

- There is no shared spawn-rate pool.
- Standard auto-spawn Entities keep their own fixed independent roll between **3.00% and 3.50%** on each eligible world-advancing gameplay turn.
- Treasure Entities use a separate priority roll capped at **4.00%** before the standard collision pool. `tam_ma_cao_minh` is currently the only Treasure Entity and therefore has an effective **4.00%** encounter rate.
- Every registered auto-spawn Entity is now **roaming on every valid Backrooms Level**. Original canon habitat/Level restrictions are reference metadata only and do not block runtime spawning.
- Entity canon still governs behavior, capabilities and encounter portrayal after the Core has spawned it.
- If an Entity encounter is already active, Core does not roll a replacement Entity.
- If multiple independent rolls succeed on the same turn, Core selects one of those successful rolls because runtime supports one active encounter overlay at a time.
- Gemini does not choose the spawned Entity and cannot replace `flags.entityEncounterKey`.
- `jane_the_killer`, `slenderman` and `diep_minh` remain legacy/local overlay keys and are not in the auto-spawn registry because no fixed encounter rate is defined for them. `diep_minh` now has a dedicated legacy/boss canon payload in `entity_encounters.json` plus `android-apk/DIEP_MINH_CANON.md`; `EntityCore` exposes that canon only when the legacy encounter is already active. This does **not** make Diệp Minh eligible for random spawning.

Current fixed rates are stored only in `entity_encounters.json`; that file is the machine-readable encounter authority so documentation and runtime cannot quietly grow two different probability systems.

## Asset refresh source

Nguồn refresh ảnh hiện hành: Google Drive `Novel/ENTITY`, dùng WebP trực tiếp. Runtime không chuyển ngược về PNG.

Ngoại lệ tên nguồn: `Novel/ENTITY/hazmat_rifle_A_01.webp` được map sang canonical runtime asset `entity/async_rifleman.webp` vì game chỉ còn một Entity ASYNC Rifleman.

Snapshot overlay reads:

`file:///android_asset/entity/<canonical-key>.webp`

Treasure asset source mapping: Google Drive `Novel/ENTITY/Tam_Ma_Cao_Minh.webp` is packaged as canonical runtime asset `entity/tam_ma_cao_minh.webp`.

CopX asset source mapping: Google Drive `Novel/ENTITY/CopX.webp` is packaged as canonical runtime asset `entity/copx.webp`. CopX is an ordinary roaming Entity with a 3.00% independent roll; a victory grants 5 Core at stage 0, scaled by the existing stage reward curve (100% drop chance, once per encounter).
