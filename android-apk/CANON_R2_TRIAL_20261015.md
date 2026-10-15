# Canon Retrieval R2 · Thử nghiệm độc lập Milestone

## Mục tiêu

- Game Master Gehihi tiếp tục kể trực tiếp như bản 1.2.0.0.29.
- Mỗi lượt tự truy xuất Canon theo Game State/Core-confirmed actors, hành động và Level/node chính xác.
- Không phục hồi Story Milestone Planner/Runtime, không ép lịch sự kiện, không cho GM thay Core gameplay.
- Nguồn tác giả: toàn bộ thư mục Google Drive `/Novel/Asset` đã được tác giả xác nhận ngày 2026-10-15. Việc tác giả xác nhận **nguồn** không biến trạng thái `OPEN`, `DYNAMIC` hoặc `WRITER-SECRET` bên trong tài liệu thành một FACT được công khai.

## Phạm vi thử nghiệm đầu tiên

`knowledge_db.json` (89 quick cards và Core hard locks) tiếp tục hoạt động độc lập. `canon_detail_db.json` bổ sung 145 đoạn chi tiết có định danh, đề mục nguồn và quyền sở hữu dữ kiện từ:

- `01_Character/CAO_MINH_CODEX.md`
- `01_Character/LUC_TRAM_CODEX.md`
- `01_Character/LUCIA_LUC_CODEX.md`
- `01_Character/TRAC_LAM_CODEX.md`
- `02_Level/BACKROOMS_WORLD.md`

Nguồn Entity đã có 22 visual authored cards trong quick database. `canon_support_db.json` bổ sung 140 đoạn R2 có thể tra cứu từ Syvial Codex (55), ASYNC (10), linh khí (13), ảnh hưởng linh khí đến tu sĩ (20) và hướng dẫn văn xuôi (42). Tổng ba kho: **89 + 145 + 143 = 377** bản ghi gồm 3 bản ghi chỉ dẫn nguồn.

Hai tư liệu lịch sử `Tang_Kiem_Coc_Huyet_Ma_Kiem_Tich_Quang.md` và `Huyet_Tay_Cao_Gia.md` được giữ **metadata nguồn, không đóng gói văn xuôi nguyên bản** vào public APK; phần sau `END OF CODEX` của Syvial cũng không tự nạp vào prompt vì có bản ghi trùng/cần phân giải. `CANON_REGISTRY_R01` là tài liệu quản trị nguồn, không phải tình tiết GM; `__TEMP_LUC_CANON_SYNC_20261011.md` là bản sao tạm, không cần nhân đôi Codex Lục Trầm.

**Không tuyên bố hoàn thành việc nhập toàn bộ Canon**: mỗi đề mục giới hạn số đoạn thử nghiệm; các đoạn không nằm trong allowlist và tác phẩm ngoài các nguồn trên vẫn cần bản biên dịch tiếp theo. Không suy diễn sự kiện chưa xảy ra, không kích hoạt nhân vật chỉ vì có Codex, không mở Level/Entity ngoài Game Core.

## Luồng runtime

`KnowledgeContextEngine.load` đọc quick cards, R2 character/world và support card index cùng lúc, cache trong tiến trình Android. Truy xuất diễn ra hoàn toàn offline, không gọi thêm AI/Drive/Internet mỗi lượt.

- R0: xác định nhân vật hiện diện từ Party/pending encounters do Core xác nhận; Level/node từ state.
- R1: bắt buộc các hard-lock, quick card phù hợp hiện có.
- R2: chọn thêm đoạn profile/dialogue/combat/visual/environment theo hành động; tối đa một đoạn cho mỗi chủ thể và chủ đề. Tài liệu bổ trợ chỉ kích hoạt khi hành động/bối cảnh có từ khóa phù hợp. Các mảnh archive hoặc chỉ dẫn nguồn không được trao cho GM.
- R3: đoạn hậu trường, writer-secret không được nhập vào chỉ mục gửi Gehihi; chưa có quyền dùng để làm người kể toàn tri.

Giữ ngưỡng token trong `KnowledgeContextEngine` hiện hành; việc chọn đoạn vẫn tùy thuộc ngân sách packet. Không bảo đảm mọi chi tiết được chọn đều có mặt khi các hard-lock đã chiếm hết ngân sách.

## Rủi ro và nghiệm thu

- Hai tên node 0.1/0.7 giữa bản tác giả và route Core vẫn chưa hòa giải. Không đưa các node mâu thuẫn này vào chi tiết tự động và không sửa route.
- Các đoạn thử nghiệm trích từ mục hợp lệ; các mục bí mật/tình tiết động chưa được cung cấp nguyên văn cho GM.
- Test chịu tải tạo thêm 1.200 record `TEST_ONLY` chỉ trong bộ nhớ JUnit, thử 48 packet theo 4 cảnh mà không làm lộ bản văn tác giả.
- Repository GitHub public. Lưu ý: dữ liệu bản thảo đã từng xuất hiện trong một commit trên nhánh PR trước khi được loại khỏi HEAD. Việc xóa trong HEAD **không xóa lịch sử Git**; phải được xử lý riêng qua quy trình chủ repo phê duyệt, không tự force-push hoặc xóa lịch sử.
- Các test này xác minh chỉ mục, ngân sách 3.400 token ước lượng và luồng chọn thẻ offline; chúng không đo trực tiếp chất lượng, độ trễ hay giới hạn request của Gehihi live.
- Nên nghiệm thu bằng test `CanonDetailRetrievalTrialTest`, `KnowledgeContextEngineTest`, `CanonGmIntegrationTest` rồi APK Debug qua CI PR, cuối cùng là thực hành trên thiết bị.
- Không merge, không phát hành chính thức trước khi test và review xong. Không hỗ trợ save cũ; không đổi schema save trong thử nghiệm này.
