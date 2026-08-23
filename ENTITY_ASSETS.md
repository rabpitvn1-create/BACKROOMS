# BACKROOMS Entity Assets

Toàn bộ sprite Entity dùng trong APK nằm trực tiếp tại:

`android-apk/app/src/main/assets/entity/`

Runtime chỉ dùng canonical Entity key trùng chính xác với tên file bỏ phần mở rộng `.png`. Không có alias theo Level, không có mã Entity cũ, không có manifest từ xa và không tải ảnh mạng.

| Canonical Entity key | Local asset |
|---|---|
| `hound` | `hound.png` |
| `clump` | `clump.png` |
| `duller` | `duller.png` |
| `deathmoth` | `deathmoth.png` |
| `hostile_faceling` | `hostile_faceling.png` |
| `false_puddle` | `false_puddle.png` |
| `paintings` | `paintings.png` |
| `smiler` | `smiler.png` |
| `skin-stealer` | `skin-stealer.png` |
| `predatory_window` | `predatory_window.png` |
| `biological_pipeline` | `biological_pipeline.png` |
| `wretch` | `wretch.png` |
| `cable_mimic` | `cable_mimic.png` |
| `the_beast_of_level_5` | `the_beast_of_level_5.png` |
| `hotel_corpse_lure` | `hotel_corpse_lure.png` |
| `jeff_the_killer` | `jeff_the_killer.png` |
| `jane_the_killer` | `jane_the_killer.png` |
| `slenderman` | `slenderman.png` |

Snapshot đọc trực tiếp bằng đường dẫn:

`file:///android_asset/entity/<canonical-key>.png`

Gameplay runtime không được suy ra Entity từ Level hoặc từ registry lịch sử. Một Entity hiện tại chỉ được nhận diện bằng canonical key đang hoạt động trong state.
