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

Ba Lifeform dùng **một roll độc lập 2% cho cả nhóm**. Khi roll thành công, runtime chọn đúng một trong ba key `blackroot_sentinel`, `sinew_strider`, `hollow_grasper`; chúng không nằm trong shared roaming pool.

Base profile của cả ba bằng **Hound × 1.3**, làm tròn về stat nguyên: **104 HP / 20 ATK / 3 Armor / 10 Aggression** trước shared Entity durability và Level scaling. Ở Level 0, shared +30 HP khiến Max HP thực tế là **134**.

Mỗi Entity có đúng 3 skill. Skill chỉ tăng %ATK và chỉ dùng hai hiệu ứng trạng thái **Bleed** và **Poison**. Ba proc dùng chung một roll độc quyền: **25% / 20% / 10%**, tương ứng **+15% / +25% / +40% ATK**; skill 1 gây Bleed, skill 2 gây Poison, skill 3 gây cả Bleed + Poison. Bleed kéo dài 3 turn, gây 3% Max HP/turn; Poison kéo dài 3 turn, gây 2% Max HP/turn; tái kích hoạt làm mới thời lượng, không cộng stack song song.

- **Blackroot Sentinel**: Thorned Hemorrhage; Blight Sap; Crimson Mycotoxin.
- **Sinew Strider**: Tendon Ripper; Septic Thread; Venomous Flay.
- **Hollow Grasper**: Hollow Laceration; Carrion Toxin; Necrotic Clutch.

Overlay nguồn vẫn dùng ba asset Drive tương ứng `the_lifeform_bacteria_01.webp`, `the_lifeform_bacteria_02.webp`, `the_lifeform_bacteria_03.webp`.

Snapshot đọc trực tiếp bằng đường dẫn:

`file:///android_asset/entity/<canonical-key>.webp`

Gameplay runtime không được suy ra Entity từ Level hoặc từ registry lịch sử. Một Entity hiện tại chỉ được nhận diện bằng canonical key đang hoạt động trong state.
