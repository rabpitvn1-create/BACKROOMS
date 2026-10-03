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
| `the_lifeform_bacteria_01` | `the_lifeform_bacteria_01.webp` |
| `the_lifeform_bacteria_02` | `the_lifeform_bacteria_02.webp` |
| `the_lifeform_bacteria_03` | `the_lifeform_bacteria_03.webp` |
| `hotel_corpse_lure` | `hotel_corpse_lure.webp` |
| `jeff_the_killer` | `jeff_the_killer.webp` |
| `async_rifleman` | `async_rifleman.webp` |
| `async_member_rifle_aim_right_01` | `async_member_rifle_aim_right_01.webp` |
| `copx` | `copx.webp` |
| `tam_ma_cao_minh` | `tam_ma_cao_minh.webp` |
| `jane_the_killer` | `jane_the_killer.webp` |
| `slenderman` | `slenderman.webp` |
| `diep_minh` | `diep_minh.webp` |

## Encounter runtime

Main Game Core owns Entity spawning through `EntityCore` and `app/src/main/assets/knowledge/entity_encounters.json`.

- There is no shared spawn-rate pool.
- The former roaming standard roster remains dormant under `legacyEntities` for possible future authored/reactivated use.
- Active random auto-spawn consists of `tam_ma_cao_minh`, the three Bacterial Lifeform variants, and `Research Async Member`.
- Each Bacterial variant is configured at **5.00%** and receives the existing 3× runtime multiplier, so each independently contributes an effective **15.00%** candidate chance on an eligible world-advancing turn.
- Bacterial eligibility is exact by `levelKeys`: `0`, `0.1`, `0.2`, `the_torment`, `red_rooms`, `4`, and `6`. These are the conservative nodes where current project lore does not define a characteristic resident hostile Entity and the active registry has no ordinary resident pool. Safe/social hubs and nodes with a characteristic/source-reported Entity are excluded.
- `tam_ma_cao_minh` remains a roaming Treasure Entity: configured **4.00%**, effective **12.00%** after the same multiplier.
- Entity canon still governs behavior, capabilities and encounter portrayal after the Core has spawned it.
- If an Entity encounter is already active, Core does not roll a replacement Entity.
- If multiple independent rolls succeed on the same turn, Core selects one of those successful rolls because runtime supports one active encounter overlay at a time.
- Gemini does not choose the spawned Entity and cannot replace `flags.entityEncounterKey`.
- `jane_the_killer`, `slenderman`, `diep_minh`, and the former roaming standard roster are legacy/local-only records. Their canon/presentation data is retained, and the former standard records also retain their old level/rate/skill metadata, but none of them are eligible for random spawning. `diep_minh` keeps its dedicated legacy/boss canon payload plus `android-apk/DIEP_MINH_CANON.md`.

Current configured rates and exact Bacterial LevelKey eligibility are stored only in `entity_encounters.json`; that file remains the machine-readable encounter authority.

## Asset refresh source

Nguồn refresh ảnh hiện hành: Google Drive `Novel/ENTITY`, dùng WebP trực tiếp. Runtime không chuyển ngược về PNG.

Ngoại lệ tên nguồn: `Novel/ENTITY/hazmat_rifle_A_01.webp` được map sang canonical runtime asset `entity/async_rifleman.webp` vì game chỉ còn một Entity ASYNC Rifleman.

Snapshot overlay reads:

`file:///android_asset/entity/<canonical-key>.webp`

Treasure asset source mapping: Google Drive `Novel/ENTITY/Tam_Ma_Cao_Minh.webp` is packaged as canonical runtime asset `entity/tam_ma_cao_minh.webp`.

CopX asset source mapping: Google Drive `Novel/ENTITY/CopX.webp` is packaged as canonical runtime asset `entity/copx.webp`. CopX is currently dormant legacy-only: it no longer enters random spawn selection, while its combat/reward profile remains available if an authored encounter reactivates it later.


## Bacterial Lifeform trio

The three Bacterial sprites are committed visual references and map one-to-one to the runtime keys above.

- **Bacterial Stalker** (`the_lifeform_bacteria_01`): frontal, extremely tall filament body, hollow rib lattice, root-like feet and cable-hook fingers. Its unique fourth proc is **Rib-Cage Clamp**, a two-hit constriction.
- **Bacterial Strider** (`the_lifeform_bacteria_02`): narrower side-profile morphology with a forward-set blank head, long stride and hooked hands. Its unique fourth proc is **Longstep Skewer**, which pierces 60% of damage prevented by DEF.
- **Bacterial Weaver** (`the_lifeform_bacteria_03`): denser braided shoulder/back mesh with open voids through the torso and looped hands. Its unique fourth proc is **Black-Mesh Feeding**, which restores 50% of the actual damage it deals.

All three use base **173 HP / 17 damage**, the integer combat representation of Hound's 150 HP / 15 damage raised by 15% with normal rounding. They keep the existing global Stage scaling.


## Research Async Member

`async_member_rifle_aim_right_01.webp` is the authoritative visual reference for **Research Async Member**. The sprite shows a sealed yellow ASYNC hazmat operator with dark visor, black gloves and boots, a rear breathing tank/air hose, and a black rifle already shouldered toward the right. Presentation deliberately leans into clinical horror: identity remains visually obscured behind the visor and the firing posture is controlled and impersonal, but canon does not claim mutation, infection or supernatural anatomy.

Runtime profile: **190 HP / 20 damage** before normal Stage scaling. Its three independent proc skills are **Containment Burst** (110% / 33%), **Visor-Line Double Tap** (115% / 28%), and **Specimen Suppression** (120% / 23%).

Spawn policy: configured **3.3333333333333335%**, multiplied by the existing EntityCore 3× policy for an effective **10.00%** independent roll. The registry lists parent Levels 0–6 and intentionally omits exact `levelKeys`, so eligibility propagates to every current Sublevel/special node through its parent Level.
