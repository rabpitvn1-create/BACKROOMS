# BACKROOMS Entity Assets

Toàn bộ sprite Entity dùng trong APK nằm trực tiếp tại:

`android-apk/app/src/main/assets/entity/`

Runtime ưu tiên canonical Entity key trùng chính xác với tên file bỏ phần mở rộng `.png`. Các unique Entity có asset tên khác canonical key phải có mapping cục bộ, case-sensitive, được khóa rõ trong runtime. Không có alias theo Level, không có mã Entity cũ, không có manifest từ xa và không tải ảnh mạng.

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
| `diep_minh` | `diep_minh.png` |
| `monster_x` | `X.png` |
| `john_doe` | `John.png` |
| `scp_173` | `SCP173.png` |
| `violet_warden` | `Newviolet.png` |

`diep_minh` là boss unique dùng roll xuất hiện độc lập 3%, không nằm trong shared roaming Entity pool.

`monster_x`, `john_doe`, `scp_173` và `violet_warden` là unique Entity dùng roll độc lập. `john_doe` có đúng 10% encounter chance trên Level 0–999. `scp_173` có đúng 5% encounter chance trên mỗi roll encounter Entity hợp lệ. `violet_warden` có đúng 10% encounter chance trên mọi Level/sublevel hiện tại và tương lai, không có lãnh địa cố định và không được đưa vào shared roaming pool.

Runtime tham chiếu trực tiếp các asset case-sensitive:

`file:///android_asset/entity/John.png`

`file:///android_asset/entity/SCP173.png`

`file:///android_asset/entity/Newviolet.png`

Không chuyển `John.png`, `SCP173.png` hoặc `Newviolet.png` sang Base64, Data URI hoặc nội dung nhúng. APK phải đóng gói file PNG thô trong assets. File `173.png` cũ được giữ nguyên để tránh phá vỡ lịch sử/compatibility của repository nhưng runtime SCP-173 hiện tại không dùng nó để hiển thị. File `Violet.png` cũ được giữ lại cho compatibility; runtime Violet hiện tại dùng `Newviolet.png` làm ảnh hiển thị.

Snapshot đọc Entity thường trực tiếp bằng đường dẫn:

`file:///android_asset/entity/<canonical-key>.png`

Gameplay runtime không được suy ra Entity từ Level hoặc từ registry lịch sử. Một Entity hiện tại chỉ được nhận diện bằng canonical key đang hoạt động trong state.
