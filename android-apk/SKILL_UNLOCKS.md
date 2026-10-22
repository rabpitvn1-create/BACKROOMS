# Mở kỹ năng theo cấp nhân vật

- Áp dụng cho Cao Minh và toàn bộ Follower có skill trong CompanionSkillCatalog.
- PASSIVE luôn mở và không chiếm mốc. Các loại khác (AUTO, COMMAND, COUNTER, UTILITY, DEFENSE, STATE, ULTIMATE) đều theo cấp.
- Giữ thứ tự catalog, đưa Ultimate xuống cuối: skill đầu mở từ cấp 0, các skill tiếp mở ở 10, 20, 30… theo cấp của chính chủ sở hữu. Đánh thường vẫn hoạt động.
- Ultimate còn phải thỏa điều kiện Poker Dice/trang bị/trạng thái sẵn có; đủ cấp không tự kích hoạt Ultimate. An Nhiên giữ điều kiện chạy trốn mỗi 5 combat turn sau khi đủ cấp.

| Nhân vật | Skill đầu | Ultimate mở ở cấp |
|---|---|---:|
| Cao Minh | Huyết Ma Tứ Liên | 90 |
| Iris | Twosome Time | 50 |
| Syvial | Rift Sever | 60 |
| An Nhiên | Quăng Đại Cái Gì Đó | 40 |
| Lục Trầm | Nhất Tuyến Phá Vọng | 40 |
| Lucia | M4A1 Joint Attack | 60 |
| Catherine | Khúc Đạn Hồi Âm | 40 |
| Janet | Bayou Swing | 40 |
| Nguyễn Quốc Nam | Phát Bắn Kiềm Chế | 40 |
| Hứa Tiểu Lan | Một Phát Định Đoạt | 40 |

CharacterSkillUnlocks là nguồn chung của runtime và projection. Trạng thái khóa suy ra trực tiếp từ level, không có cờ mở khóa do UI/GM gửi lên hoặc cần migration/save schema mới. Lên cấp bằng EXP, Dungeon và save/load đều dùng cùng quy tắc. Effect combat của skill khóa bị loại trước resolve, tránh trạng thái cũ tiếp tục giữ buff/DoT bị khóa.

Giao diện danh sách skill hiển thị ổ khóa, làm mờ nội dung và ghi cấp yêu cầu. Passive luôn sáng. Projection mới cập nhật danh sách đang mở khi level thay đổi. Không áp quy tắc Follower cho Entity Lục Trầm Hắc Hóa hoặc kỹ năng Entity khác.

Các skill UTILITY chỉ có mô tả/narration từ trước (ví dụ Thôi Để Tôi Làm) vẫn là mô tả; bản này không tạo thêm cơ chế khám phá còn thiếu. Điều kiện mở vẫn xuất hiện trong projection.
