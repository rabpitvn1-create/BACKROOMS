# Nguyễn Quốc Nam và Hứa Tiểu Lan — tích hợp runtime

Nguồn tác giả: `Novel/Characters/NGUYEN_QUOC_NAM_CODEX.md` R01 và
`HUA_TIEU_LAN_CODEX.md` R02. Bản raw, hash, heading và nguồn → thẻ nằm trong
`canon_sources/Characters/`, `canon_source_manifest.json` và `KNOWLEDGE_SOURCE_MAP.md`.
APK cũng đóng gói hai tài liệu nguyên văn trong `assets/canon/`; GM đọc thẻ được
chọn cho cảnh, không đọc toàn bộ tài liệu.

## Luồng chơi

- ID: `nguyen_quoc_nam`, `hua_tieu_lan`. Registry khởi đầu MISSING, không ở Party.
- Explorer vật lý hợp lệ dùng một vé d10000 phía Core. Entity chiếm 0–3499 (35%);
  3500–4499 được dành cho các nhân vật chưa thuộc đợt tích hợp này; Tiểu Lan
  4500–4999 (5%), Quốc Nam 5000–5499 (5%). Vé không đổi theo Level/sub-level/AREA.
- Meta, lời mời, combat, lượt pending và nhân vật đã gặp không tạo cuộc gặp mới.
  Khi Entity bị tắt, cửa sổ NPC vẫn hoạt động nếu NPC encounter được phép.
- Roll chỉ là kế hoạch. Lỗi mạng, hủy lượt hoặc generation thất bại không đánh
  dấu encountered. Sau candidate validation, cùng transaction mới ghi cuộc gặp,
  thời gian và completedTurnIds. Plan phía Core không lấy từ JSON của browser/GM.
- Sau cuộc gặp, người chơi cần mời đúng người và NPC xác nhận đồng ý bằng
  `joinConfirmed`. Party đầy không đuổi người cũ. Từ chối Tiểu Lan cho nàng rời
  cảnh, không lén theo hoặc tự xuất hiện lại. Gia đình không được đưa vào Party.
- Ví dụ lời mời: “Mời Nguyễn Quốc Nam vào Party” hoặc “Mời Hứa Tiểu Lan đi cùng”.
- Tháo/đeo/chuyển/dùng/bỏ đồ đã sở hữu của hai nhân vật đi qua Core, không gọi GM
  hoặc sinh đồ. Ví dụ: “Nguyễn Quốc Nam tháo Hành Lang 01”, rồi “Nguyễn Quốc Nam
  đưa Hành Lang 01 cho Cao Minh”. Nhân vật phải đã gia nhập và còn ACTIVE/sống.
- Save hiện hành giữ encountered, Party, đồ thật, HP và hạn mức sơ cứu. Bắt đầu
  lại/xóa save trong giao diện cũng xóa Core hiện hành. Không thêm migration save cũ.

## Chiến đấu và hiển thị

Mỗi người có năm kỹ năng thường và một Ultimate thực thi trong Core. Nam có sơ
cứu tối đa 12% Max HP, một lần mỗi encounter, cho người sống bị thương nhiều nhất
theo tỷ lệ HP. Ultimate dùng SSF/FSF đã chốt đúng action/encounter, Nam 285% và Lan
320% current Weapon DMG; không Critical/proc bồi. Vũ khí phải thật sự owned+equipped.

Poker Dice cho chọn Tấn công / Phòng thủ / Né tránh / Quan sát **trước khi** chuẩn
bị hand. Sau khi hand được khóa, không đổi action hoặc gieo lại bằng cách đổi nút.
Khôi phục hand đang khóa tiếp tục đúng action đó. Nhờ vậy sơ cứu, né và Opening
có đường kích hoạt từ giao diện, không chỉ từ unit test.

Đủ hai avatar, hai overlay và tám icon WebP nguyên bản trên Drive. Overlay nhận
hiện diện từ Core (Party hoặc cuộc gặp đã commit), xóa khi rời cảnh và phục hồi sau
Snapshot redraw. Thẻ đồ và modal hiển thị icon/modifier. `named-follower-assets.json`
và `item-icons/manifest.json` ghi nguồn/hash và ánh xạ item ID → icon.

Schema giữ STR/DEF/SKL/VIT hiện hành. DEF/VIT của trang bị tác động chỉ số hiện tại;
DEX/AGI trong nguồn được ánh xạ thành multiplier Accuracy/Evasion tương ứng, không
khôi phục field stat đã nghỉ. Hệ thống chưa mô phỏng tiêu hao từng viên đạn như
codex đã ghi; narration vẫn giữ đạn hữu hạn và nhịp nạp súng cổ của Tiểu Lan.

## Canon và cổng xác minh

30 thẻ mới đưa corpus lên 96 thẻ; helper đường OFF có trần 1400 token, giữ nguyên
từng thẻ. CR2 ON truy xuất cùng dữ liệu qua resolver, không append packet hai lần.
Tên được nhắc không tạo hiện diện. Chuyển sinh Anh Tý → Cao Minh giữ
RESTRICTED/NEVER_SHOW; Cao Minh không có ký ức và vẫn do người chơi điều khiển.
Thông tin chưa chốt giữ UNKNOWN; tuyến tình cảm không tự thêm đồng thuận hay spawn.

Module Kotlin/JS mới là nguồn trực tiếp. `patch-nam-lan-followers.py` tái tạo các
điểm nối trên baseline PR #669 đã kiểm, chạy lại là no-op; baseline lệch thì dừng
trước khi sửa. Đã replay trên checkout baseline riêng và đối chiếu byte của 17
file tích hợp. Không tuyên bố chuỗi patch cũ không có manifest sẽ áp được mọi thứ.

Đã xác minh cục bộ:

- Kotlin Core biên dịch bằng Kotlin 2.3.0, target JVM 17; 101 JUnit có mục tiêu đạt.
- 13 Node tests đạt: avatar/overlay, redraw, icon hash, bốn command UI và khôi phục
  hand phòng thủ. Script inline và JavaScript do Android chèn parse hợp lệ.
- MainActivity biên dịch riêng bằng ECJ với Android API 35 (Java 8 language mode,
  platform bootclasspath; BuildConfig kiểm thử không có khóa thật). Đây không phải
  kết quả Gradle/đóng gói APK.
- Hai benchmark bắt buộc exit 0: gate v1 p95 3400 token; CR2 8/8 PASS. Test JVM
  riêng xác minh hai nhân vật/cảnh gặp đầu đóng packet CR2 trong ngân sách 6000.
- Kiểm chữ ký ảnh/hash raw, provenance, patch replay/no-op và `git diff --check` cho code/config đạt. Hai bản raw codex Tiểu Lan giữ nguyên
  Markdown hard-break spaces và dòng trống cuối tệp để bảo toàn byte/hash nguồn.

Chưa xác minh hiển thị trên thiết bị Android. CI của PR giữ cổng Gradle unit tests
và assembleDebug, đồng thời chạy smoke patch và các Node tests liên quan.
