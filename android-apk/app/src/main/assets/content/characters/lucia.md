# LUCIA LỤC / HỨA THUÝ MAI

**Mã Codex:** `LUCIA-LUC-RUNTIME-RESTORE-R01`  
**Trạng thái:** `CURRENT / SCOPED USER RETCON`  
**Nguồn:** yêu cầu người dùng ngày 2026-09-28 + dữ kiện runtime/canon đã tồn tại trong repository trước commit retcon `4cbbb178281ed32fb3c8c940cffb50e550c16f85`.  
**Giới hạn nguồn:** tài liệu Drive cũ `Lucia_Codex.docx` hiện không truy cập được; mọi dữ kiện không được xác nhận bên dưới tiếp tục `OPEN`.

## 00 · CORE LOCK
<!-- canon: core=true; aliases=Lucia Lục,Hứa Thuý Mai,Hứa Thúy Mai,Lucia -->

- **Lucia Lục / Hứa Thuý Mai và Lục Trầm là hai nhân vật khác nhau.**
- Runtime id của Lucia là `lucia`; runtime id của Lục Trầm là `luc_tram`. Không alias, rename, migrate hoặc merge hai id này.
- Lucia là **con người bình thường được huấn luyện tốt**, thuộc nhánh chiến đấu hiện đại/tactical riflewoman đã có trong runtime cũ; cô **không phải** Chính Đạo Kiếm Tu, không thuộc Thiên Kiếm Môn và không sở hữu Tịch Quang hay Thiên Cơ Bạch Kim Kiếm Khải.
- **Nguồn gốc Milestone:** Lucia đến từ một thế giới khác với Cao Minh/Lục Trầm, ở tương lai xa hơn về công nghệ và xã hội, và là quân nhân được huấn luyện bài bản.
- Lucia không mặc định biết linh khí, thần thức, pháp bảo hay kinh mạch. Cô phải học các khái niệm này từ quan sát, giải thích và continuity; ngược lại Cao Minh/Lục Trầm không tự hiểu công nghệ tương lai chỉ vì họ mạnh.
- Ngoài tình huống tác chiến khẩn cấp, Lucia nói bằng câu hoàn chỉnh, giải thích dữ kiện/rủi ro/độ bất định và có cảm xúc tự nhiên; không viết cô như radio chiến thuật hoặc commando caricature.
- Quan hệ, lịch sử và xưng hô giữa Lucia với Cao Minh hiện `OPEN` cho tới khi continuity/save xác lập. Không nhập quan hệ Cao Minh ↔ Lục Trầm sang Lucia.
- CharacterEncounterCore sở hữu việc xuất hiện và gia nhập Party. Gemini/narration không được tự spawn Lucia hoặc tự sửa Party.

## 01 · TRANG BỊ RUNTIME ĐÃ XÁC NHẬN

Bộ mặc định được phục hồi từ runtime trước retcon:

- **M4A1 cá nhân hóa**;
- **Dao găm chiến đấu**;
- **Đồng hồ định vị quân sự**.

Các món trên không được đổi thành Tịch Quang Kiếm, Kiếm Khải hoặc pháp bảo của Lục Trầm.

## 02 · GAMEPLAY PROJECTION

Các con số sau là cơ chế Poker Dice/runtime, không tự biến thành lore siêu nhiên:

- **M4A1 Joint Attack — Skill:** 150% damage.
- **Toxic Burst:** 52% proc, +25% damage, Trúng độc 2 turn, value 3.
- **Armor-Piercing Burst:** 55% proc, +20% damage, Xuyên giáp 2 turn, value 10.
- **Concussive Burst:** 46% proc, +15% damage, Choáng 1 turn.
- **Rending Burst:** 49% proc, +20% damage, Chảy máu 2 turn, value 3.
- **Corrosive Burst:** 51% proc, +20% damage, Trúng độc 2 turn, value 4.
- **Too Young To Die — Ultimate:** đúng **60 phát M4A1**. Mỗi phát dùng **100% DMG hiện tại của Lucia + 15% Bonus DMG** theo projection hiện hành.

Lucia vẫn là con người được huấn luyện tốt; các proc/status gameplay không cấp cho cô năng lực siêu nhiên mới.

## 03 · ENCOUNTER CONTRACT

- Lucia dùng candidate `character:lucia`.
- Baseline runtime được phục hồi: **10% trên Level 0**.
- Đây là **first contact với Cao Minh trừ khi live continuity/save đã xác lập khác**.
- Lục Trầm dùng candidate riêng `character:luc_tram`, với luật tái ngộ riêng sau Level 0. Hai candidate không thay thế nhau.

## 04 · OPEN / KHÔNG ĐƯỢC TỰ LẤP

Ngoài các dữ kiện đã khóa ở trên, không tự dựng:

- tuổi, quê quán, đơn vị quân đội, cấp bậc hoặc lịch sử phục vụ chi tiết;
- ngoại hình hiện hành nếu không có nguồn thị giác CURRENT;
- quan hệ hoặc xưng hô có sẵn với Cao Minh;
- năng lực, trang bị hoặc bí mật mới;
- bất kỳ mối liên hệ bản thể nào với Lục Trầm.

Nếu nguồn Lucia CURRENT mới xuất hiện, nguồn đó được dùng để mở rộng/thay thế phạm vi tương ứng; **khóa phân biệt Lucia ≠ Lục Trầm vẫn giữ cho tới khi người dùng retcon trực tiếp**.
