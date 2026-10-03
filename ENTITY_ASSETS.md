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
- Standard roaming auto-spawn is currently disabled. The former standard roster is preserved intact under `legacyEntities` for possible future authored/reactivated use.
- `tam_ma_cao_minh` is currently the **only** random auto-spawn Entity. Its configured Treasure rate remains **4.00%**; `EntityCore` applies the current 3× runtime multiplier, for an effective **12.00%** candidate rate on eligible turns.
- Auto-spawn eligibility now applies only to `tam_ma_cao_minh` using its registry `levels`. Former roaming Entities are dormant legacy records and do not enter the random SituationCandidate pool.
- Entity canon still governs behavior, capabilities and encounter portrayal after the Core has spawned it.
- If an Entity encounter is already active, Core does not roll a replacement Entity.
- If multiple independent rolls succeed on the same turn, Core selects one of those successful rolls because runtime supports one active encounter overlay at a time.
- Gemini does not choose the spawned Entity and cannot replace `flags.entityEncounterKey`.
- `jane_the_killer`, `slenderman`, `diep_minh`, and the former roaming standard roster are legacy/local-only records. Their canon/presentation data is retained, and the former standard records also retain their old level/rate/skill metadata, but none of them are eligible for random spawning. `diep_minh` keeps its dedicated legacy/boss canon payload plus `android-apk/DIEP_MINH_CANON.md`.

Current fixed rates are stored only in `entity_encounters.json`; that file is the machine-readable encounter authority so documentation and runtime cannot quietly grow two different probability systems.

## Asset refresh source

Nguồn refresh ảnh hiện hành: Google Drive `Novel/ENTITY`, dùng WebP trực tiếp. Runtime không chuyển ngược về PNG.

Ngoại lệ tên nguồn: `Novel/ENTITY/hazmat_rifle_A_01.webp` được map sang canonical runtime asset `entity/async_rifleman.webp` vì game chỉ còn một Entity ASYNC Rifleman.

Snapshot overlay reads:

`file:///android_asset/entity/<canonical-key>.webp`

Treasure asset source mapping: Google Drive `Novel/ENTITY/Tam_Ma_Cao_Minh.webp` is packaged as canonical runtime asset `entity/tam_ma_cao_minh.webp`.

CopX asset source mapping: Google Drive `Novel/ENTITY/CopX.webp` is packaged as canonical runtime asset `entity/copx.webp`. CopX is currently dormant legacy-only: it no longer enters random spawn selection, while its combat/reward profile remains available if an authored encounter reactivates it later.
