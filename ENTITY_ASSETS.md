# BACKROOMS Entity Assets

Toàn bộ sprite Entity dùng trong APK nằm trực tiếp tại:

`android-apk/app/src/main/assets/entity/`

Runtime chỉ dùng canonical Entity key trùng chính xác với tên file bỏ phần mở rộng `.webp`. Không có alias theo Level, không có mã Entity cũ, không có manifest từ xa và không tải ảnh mạng.

| Canonical Entity key | Local asset |
|---|---|
| `hound` | `hound.webp` |
| `926` | `926.webp` (Whistlers, fan-created) |
| `927` | `927.webp` (Entity 927, fan-created) |
| `LuckyBear` | `LuckyBear.webp` (fan-created) |
| `Brownies` | `Brownies.webp` (fan-created) |
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
| `luc_tram_hac_hoa` | `luc_tram_hac_hoa.webp` (Lục Trầm Hắc Hóa, enemy-only) |
| `blackroot_sentinel` | `blackroot_sentinel.webp` |
| `sinew_strider` | `sinew_strider.webp` |
| `hollow_grasper` | `hollow_grasper.webp` |

Cả **27 Entity** thuộc Registry/asset catalog. Core tự roll encounter với xác suất **35% mỗi lượt EXECUTE đủ điều kiện**, dùng resident pool theo exact Level ID và roaming pool khi Level không có danh sách resident. Meta action, Combat đang diễn ra hoặc encounter chờ xác nhận không kích hoạt lượt roll. Xem [ENTITY_SPAWNS.md](ENTITY_SPAWNS.md) và `EntitySpawnPool.kt` để biết điều kiện và danh sách cụ thể.

Entity do Core chọn được giữ ở `PENDING_ENCOUNTER_CONFIRMATION`; chỉ Battle Start mới kích hoạt Combat và hiển thị ảnh. Tên/chỉ số lấy từ Registry, encounter ID lấy từ Core, không dò câu chữ GM. Không có quota hoặc Boss bắt buộc do Story Milestone cũ quyết định.

Base profile của cả ba bằng **Hound × 1.3**, làm tròn về stat nguyên: **104 HP / 20 ATK / 3 Armor / 10 Aggression** trước shared Entity durability và Level scaling. Ở Level 0, shared +30 HP khiến Max HP thực tế là **134**.

Mỗi Entity có đúng 3 skill. Skill chỉ tăng %ATK và chỉ dùng hai hiệu ứng trạng thái **Bleed** và **Poison**. Ba proc dùng chung một roll độc quyền: **25% / 20% / 10%**, tương ứng **115% / 125% / 140% Basic Attack**. Đây là hệ số damage của đòn đánh thường sau phòng thủ, **không phải buff stat ATK**; skill 1 gây Bleed, skill 2 gây Poison, skill 3 gây cả Bleed + Poison. Bleed kéo dài 3 turn, gây 3% Max HP/turn; Poison kéo dài 3 turn, gây 2% Max HP/turn; tái kích hoạt làm mới thời lượng, không cộng stack song song.

- **Blackroot Sentinel**: Thorned Hemorrhage; Blight Sap; Crimson Mycotoxin.
- **Sinew Strider**: Tendon Ripper; Septic Thread; Venomous Flay.
- **Hollow Grasper**: Hollow Laceration; Carrion Toxin; Necrotic Clutch.

**Lục Trầm Hắc Hóa (Entity):** profile 2999 HP / 0 ATK gốc / 8 Armor / 9 Aggression của Diệp Minh; HP sau scale vẫn theo boss và hồi **30 HP/turn**. Không dùng đòn 10% HP, chiêu 5% AoE hay EXP boss của Diệp Minh. Sáu kiếm kỹ Follower được chuyển thành skill đối thủ: Thiên Kiếm Linh Tâm tích kiếm ý, Bạch Hồng Quán Nhật đột tiến và chém, Nhất Tuyến Phá Vọng chém bỏ qua 20% DEF, Tịch Quang Phản Kiếm phản kích sau Party hụt đòn, Vạn Kiếm Quy Tâm phòng thủ khi Party EVADE/GUARD mỗi 3 turn, Thiên Kiếm Định Giới dựng Kiếm Vực từ turn thứ 5 với kiếm ý >=2 (một lần mỗi wave, chiếm lượt tấn công). Khi Ward/Domain còn hiệu lực: +8 Armor và +20 điểm % Evasion trong 2 lượt kế. Sword base DMG 20 theo Level scale là hệ số enemy gameplay riêng, không lấy trang bị hay trạng thái nhân vật Follower. Follower Lục Trầm và bộ skill gốc giữ nguyên. Asset nén 820×1457, 247244 bytes trên Google Drive `Novel/ENTITY/luc_tram_hac_hoa.webp` từ `LUC_TRAM_HAC_HOA_OVERLAY.webp` gốc.

**Brownies:** tương tự LuckyBear với stat Hound gốc **80 HP / 0 ATK / 2 Armor / 8 Aggression**, không phản công, roaming, thưởng cố định **100 EXP** cho mỗi thành viên Party tham chiến còn sống sau khi tiêu diệt, một lần theo encounter instance. Asset `Brownies.webp` dùng canonical key `Brownies`.

**LuckyBear:** stat Hound gốc **80 HP / 0 ATK / 2 Armor / 8 Aggression**, 220 Max HP combat tại Level 0. Không phản công và không gây sát thương dù cơ chế thông thường có floor 1 damage. Tiêu diệt LuckyBear thưởng cố định **200 EXP cho mỗi thành viên Party đang chiến đấu còn sống**, một lần theo encounter instance kể cả qua save/load; không phụ thuộc Level, không thay EXP hay cơ chế loot của các Entity khác. Asset `LuckyBear.webp` đặt tên phân biệt hoa thường trùng canonical key; Java/JS chuyển đầu vào `luckybear` thành đúng key đó.

Entity 927 có profile **320 HP / 60 ATK / 8 Armor / 32 Aggression**. Máu combat bằng đúng 2× Entity 926 ở cùng cấp (Level 0: 880 HP), không nhân đôi các cơ chế chung như evasion/regen hay xác suất chạm trán.

Overlay nguồn vẫn dùng ba asset Drive tương ứng `the_lifeform_bacteria_01.webp`, `the_lifeform_bacteria_02.webp`, `the_lifeform_bacteria_03.webp`.

Snapshot đọc trực tiếp bằng đường dẫn:

`file:///android_asset/entity/<canonical-key>.webp`

Core dùng exact Level ID để chọn pool hợp lệ; phía UI không được tự suy ra Entity từ Level, ảnh hoặc lời kể. Một encounter hiện tại chỉ được nhận diện bằng canonical Entity key và encounter ID do Core cung cấp.
