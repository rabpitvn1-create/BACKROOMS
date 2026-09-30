# The BACKROOMS — Drive Source Map for Runtime Knowledge

## Authority order

1. User instruction trong lượt hiện tại.
2. Text Game / Game Master hard rules.
3. Live save/campaign state.
4. Explicit committed USER_RETCON canon (currently includes `DIEP_MINH_CANON.md` and the Lucia Lục ≠ Lục Trầm separation lock).
5. Current machine-readable character canon.
6. On-demand world/level/entity canon.
7. Legacy logs hoặc mirrors chỉ dùng khi không xung đột với nguồn mới hơn.

## Current machine-readable character canon

- `app/src/main/assets/knowledge/characters_current.json`
- `app/src/main/assets/knowledge/knowledge_db.json`

Hai file trên đã được đồng bộ sang **Cao Minh R15**, **Lucia Lục scoped restore R01** và **Lục Trầm R05 / Visual R02**. Lucia (`lucia`) và Lục Trầm (`luc_tram`) là hai nhân vật riêng; không được dùng mirror/save cũ để nhập hai id.

## Character Drive audit

- `Cao_Minh_Codex.docx` — Drive ID `1TDBphEo1wxrdlRXTI9WUOJPinmWv1PHq` — R15.
- `Syvial_Codex.docx` — nguồn riêng của Syvial.
- `Lucia_Codex.docx` — nguồn lịch sử của Lucia; hiện connector không truy cập được. Scoped source hiện hành: `app/src/main/assets/canon/Lucia_Codex.md`.
- `Lục_Trầm_Codex` — Drive ID `1TCyLifr0usajTt6Gp3f2o_nWsAdrwqtfd4N9t0KeK3s` — nguồn hiện hành của Lục Trầm R05.

### Cao Minh source map

| Domain | Anchor / runtime key | Current lock |
| --- | --- | --- |
| Identity | `CAO-QUICK-01` | Cao Minh / Vạn Giới Ma Tôn; Ma Đạo Kiếm Tu thuần tiên hiệp; không có thân phận quân sự/công nghệ hiện đại hoặc tương lai. |
| Backrooms entry | `CAO-BACKROOMS-01` | Đã đứng ở đỉnh cao thế giới nguyên sinh, rơi vào Level 0 ngoài ý muốn, rồi chủ động khám phá vì Backrooms là một thế giới chưa hiểu. |
| Vạn Quỷ Ma Tâm | `CAO-CORE-VQM-01` | Ma nguyên gần như vô tận; hồi phục và tự phục hồi trang bị bản mệnh; không tự thêm mana/cooldown/tha hóa. |
| Vạn Quỷ Ma Thân | `CAO-VQMT-01` | Giải phóng toàn diện, không mất lý trí hoặc quyền kiểm soát. |
| Huyết Ma Kiếm | `CAO-EQP-HUYET-MA-KIEM-01` | Đại kiếm bản mệnh, kiếm thể + Huyết Sát Ma Khí + ngự kiếm. |
| Huyết Ma Chiến Khải | `CAO-EQP-HUYET-MA-KHAI-01` | Ma khải từ Ma Kim/ma văn/tinh huyết/ma nguyên, không phải công nghệ. |
| Vạn Tàng Giới | `CAO-EQP-VAN-TANG-01` | Kho tiểu không gian vật vô tri + Phản Bổn; không copy/create sinh linh hoặc vật mới. |
| Ultimate | `CAO-ULT-HUYETMA24-01` | Huyết Ma Nhị Thập Tứ Trảm: đúng 24 trảm, ngoại giới dừng hoàn toàn. |
| Diệp Minh | `DIEP-MINH-CAO-REL-01` | Tử địch huyết hải thâm cừu; Diệp Minh góp phần thảm sát Cao gia, biến cố góp phần quyết định đẩy Cao Minh vào ma đạo. Chủ mưu/động cơ/chi tiết sát hại/kết cục vẫn OPEN. |


### Lucia Lục source map

| Domain | Anchor / runtime key | Current lock |
| --- | --- | --- |
| Identity | `LUCIA-LUC-RUNTIME-RESTORE-R01` / `lucia` | Lucia Lục / Hứa Thuý Mai là nhân vật riêng, không phải Lục Trầm. |
| Equipment | `01 · TRANG BỊ RUNTIME ĐÃ XÁC NHẬN` | M4A1 cá nhân hóa, dao găm chiến đấu, đồng hồ định vị quân sự. |
| Gameplay | `02 · GAMEPLAY PROJECTION` | M4A1 Joint Attack 150%; 5 proc súng lịch sử; Too Young To Die đúng 60 phát, current DMG +15% bonus mỗi phát. |
| Encounter | `03 · ENCOUNTER CONTRACT` | Core-owned; 10% candidate trên Level 0; first contact nếu continuity chưa xác lập khác. |
| Relationship | `04 · OPEN` | Quan hệ/xưng hô với Cao Minh OPEN; không nhập quan hệ Lục Trầm. |

### Lục Trầm source map

| Domain | Anchor / runtime key | Current lock |
| --- | --- | --- |
| Identity | `LUC-TRAM-THIEN-KIEM-CODEX-R05` | Chân truyền đệ tử Thiên Kiếm Môn; Chính Đạo Kiếm Tu; cùng thế giới nguyên sinh với Cao Minh. |
| Power scale | `01 · ĐỊNH DANH VÀ VỊ TRÍ` | Thiên tài hàng đầu thế hệ trẻ nhưng không ngang Cao Minh. |
| Visual | `04 · NGOẠI HÌNH · VISUAL LOCK R02` | Tóc bạc–trắng cực dài, mắt lam xám, kiếm quan vàng–đen gắn tinh thể lam, Kiếm Khải trắng–bạc–vàng trên nền tối điểm sapphire, chiến bào phân mảnh và đúng một Tịch Quang đại kiếm cực dài trắng–bạc. |
| Core ability | `07 · THIÊN KIẾM LINH TÂM` | Đọc quỹ đạo/trọng tâm/linh lực/điểm bất ổn; không toàn tri, không tự biết Entity/Level/quy luật. |
| Weapon | `08 · TỊCH QUANG KIẾM` | Bản mệnh linh kiếm; kiếm niệm triệu hồi; không tự tái sinh vô hạn. |
| Armor | `09 · THIÊN CƠ BẠCH KIM KIẾM KHẢI` | Pháp bảo tiên hiệp, không phải công nghệ/mecha thuần khoa học. |
| Relationship | `13-20` | Cao Minh và Lục Trầm từng giao chiến nhiều lần; Backrooms là tái ngộ, không first contact; romance phải phát triển chậm. |
| Backrooms | `14-15, 21` | Rơi vào Level 0 khác vị trí; runtime encounter chỉ sau Level 0; không có knowledge preload. |
| Gameplay id | `luc_tram` | Runtime id hiện hành của Lục Trầm. |
| Ultimate | `12 · THIÊN KIẾM ĐỊNH GIỚI` | Runtime giữ 60-hit Ultimate cũ như gameplay projection, đổi presentation sang Thiên Kiếm Định Giới. |

### Relationship locks

- Cao Minh ↔ Syvial: không có quan hệ có sẵn; first contact nếu gặp.
- Cao Minh ↔ Lucia Lục: quan hệ/xưng hô OPEN; encounter mặc định first contact nếu live continuity chưa xác lập khác; tuyệt đối không nhập quan hệ Cao Minh ↔ Lục Trầm.
- Cao Minh ↔ Lục Trầm: đã là tử địch/đối thủ từ thế giới nguyên sinh; trong Backrooms quan hệ phát triển chậm từ đối đầu đến hợp tác, tín nhiệm rồi mới có khả năng nảy sinh tình cảm. Không phải first contact.
- Cao Minh ↔ Diệp Minh: tử địch không đội trời chung. Diệp Minh trực tiếp góp phần vào đại kiếp thảm sát Cao gia; biến cố là một nguyên nhân quyết định khiến Cao Minh bước vào ma đạo. Không mặc định Diệp Minh là chủ mưu duy nhất; các chi tiết sâu hơn vẫn OPEN. Canon nguồn: `DIEP_MINH_CANON.md`.

## Diệp Minh R01 source map

| Domain | Anchor / runtime key | Current lock |
| --- | --- | --- |
| Identity | `DIEP-MINH-ID-01` | Tử địch không đội trời chung của Cao Minh; tông môn/cảnh giới/xuất thân chi tiết vẫn OPEN. |
| Visual | `DIEP-MINH-VIS-01` | Nam tử tóc đen; pháp giáp đen–vàng ở nửa phải, ma thể đen–đỏ dị hóa ở nửa trái; mắt trái đỏ; trường kiếm kim quang; phù vàng và biểu tượng đạo gia. |
| Blood feud | `DIEP-MINH-CAO-REL-01` | Góp phần thảm sát Cao gia; biến cố đẩy Cao Minh vào tuyệt vọng và là nguyên nhân quyết định dẫn tới ma đạo. |
| Backrooms ontology | `DIEP-MINH-BACKROOMS-01` | Bản thể cũ đã chết; manifestation Backrooms giữ ký ức/huyết cừu nhưng bản chất tàn hồn/bản sao/tái tạo vẫn OPEN. |
| Runtime | `diep_minh` / `DIEP-MINH-RUNTIME-01` | Legacy/boss-only; không auto-spawn, không có ratePercent. |

## Runtime notes

- Internal player id: `cao_minh`.
- Combat skills: Huyết Ma Tứ Liên, Ma Tâm Trấn Hồn, Huyết Ảnh Ma Độn, Thiên Ma Bộ, Huyết Ma Nhị Thập Tứ Trảm.
- Default equipment set: Huyết Ma Kiếm, Huyết Ma Chiến Khải, Vạn Tàng Giới.
- CharacterEncounterCore sở hữu spawn/join character.
- Gameplay numbers không tự biến thành lore.
- New-game canon version: `cao-minh-r15`; save thuộc canon cũ được reset về baseline R15 thay vì trộn hai continuity.
