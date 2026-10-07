# BACKROOMS Entity Assets

Toàn bộ sprite Entity dùng trong APK nằm trực tiếp tại:

`android-apk/app/src/main/assets/entity/`

Runtime chỉ dùng canonical Entity key trùng chính xác với tên file bỏ phần mở rộng `.webp`. Không có alias theo Level, không có mã Entity cũ, không có manifest từ xa và không tải ảnh mạng.

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
| `jane_the_killer` | `jane_the_killer.webp` |
| `slenderman` | `slenderman.webp` |
| `diep_minh` | `diep_minh.webp` |

`diep_minh` là boss unique dùng roll xuất hiện độc lập 3%, không nằm trong shared roaming Entity pool.

Snapshot đọc trực tiếp bằng đường dẫn:

`file:///android_asset/entity/<canonical-key>.webp`

Gameplay runtime không được suy ra Entity từ Level hoặc từ registry lịch sử. Một Entity hiện tại chỉ được nhận diện bằng canonical key đang hoạt động trong state.
