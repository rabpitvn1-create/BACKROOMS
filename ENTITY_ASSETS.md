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
| `blackroot_sentinel` | `blackroot_sentinel.webp` |
| `sinew_strider` | `sinew_strider.webp` |
| `hollow_grasper` | `hollow_grasper.webp` |

`diep_minh` là boss unique dùng roll xuất hiện độc lập 3%, không nằm trong shared roaming Entity pool.

Ba Lifeform mới dùng **một roll độc lập 2% cho cả nhóm**. Khi roll thành công, runtime chọn đúng một trong ba key `blackroot_sentinel`, `sinew_strider`, `hollow_grasper`; chúng không nằm trong shared roaming pool.

Base profile của cả ba lấy từ Hound × 0.3 và làm tròn về stat nguyên: **24 HP / 5 ATK / 1 Armor / 2 Aggression** trước shared Entity durability và Level scaling. Ở Level 0, shared +30 HP khiến Max HP thực tế là 54.

- **Blackroot Sentinel** — Rootbind, proc 30%: giảm 15 escape progress và 1 Momentum.
- **Sinew Strider** — Longstep Rupture, proc 25%: áp sát một range band, phá một bậc Cover và giảm 1 Opening.
- **Hollow Grasper** — Reknit, proc 20%: hồi 4 HP nếu đang bị thương và giảm 1 Opening.

Overlay nguồn được đóng gói cục bộ từ ba asset Drive tương ứng `the_lifeform_bacteria_01.webp`, `the_lifeform_bacteria_02.webp`, `the_lifeform_bacteria_03.webp`.

Snapshot đọc trực tiếp bằng đường dẫn:

`file:///android_asset/entity/<canonical-key>.webp`

Gameplay runtime không được suy ra Entity từ Level hoặc từ registry lịch sử. Một Entity hiện tại chỉ được nhận diện bằng canonical key đang hoạt động trong state.
