# Backroom 1.4.0 Debug

Bản 1.4.0 đóng gói trạng thái `main` sau v1.3.9, tập trung vào Canon nhân vật SRU, hình ảnh Kai/Lucia, Snapshot epsilon, hợp đồng LiteRT Director và bản đồng bộ Kai R10 với SRU Assault Rifle MK19.

## Kai R10 và SRU Assault Rifle MK19

- Đồng bộ vũ khí hiện hành của Kai từ cách gọi SRU-SG cũ sang **SRU Assault Rifle MK19** trong Equipment, GM knowledge, input alias và Character Skill UI.
- Giữ internal item ID `kai:sru-sg` làm compatibility ID để save cũ không cần migration phá vỡ dữ liệu.
- Khóa thông số R10 hiện hành: 5.56×45 mm NATO / Sparda 5.56×45 mm, băng vật lý 30 viên, 700–950 viên/phút, 2,88 kg rỗng, khoảng 3,4 kg với băng đầy, nòng 368 mm và tầm hiệu quả khoảng 500–600 m.
- Gameplay áp hệ số **×3 số đạn** cho các kỹ năng bắn của Kai: The Last Requiem 4→12 viên, Silent Lullaby 4→12 viên, Salvation 2→6 viên, Guilty Crown Override 24→72 viên.
- The Last Requiem, Silent Lullaby và Salvation giữ nguyên proc rate, tổng %DMG, status effect và timing; chỉ số viên trong gameplay/narration tăng ×3.
- Guilty Crown Override tiếp tục dùng mô hình damage theo từng viên, nên 72 viên cho tổng damage cơ sở 720 HP trước modifier/mitigation. Codex base vẫn giữ thủ tục gốc 24 viên để tách lore khỏi gameplay balance.

## Lucia R03

- Cập nhật runtime canon Lucia Lục với danh tính quân sự **Hứa Thuý Mai**, quốc tịch Việt Nam, Hoa Kiều/Hứa-family background, một năm quân ngũ, Vietnam–US training và power scale `HUMAN_TRAINED`.
- Loại bỏ metadata chỉ huy tiểu đội cũ và prompt spawn ngẫu nhiên Level 0 đã lỗi thời; fixed story-owned encounter vẫn giữ nguyên.
- Thêm runtime knowledge card và Visual Lock R03 cho GM/writer mà không thay đổi combat math, inventory hay encounter mechanics.
- Thay avatar Lucia bằng ảnh Drive mới trong cùng đường dẫn runtime hiện hành.

## Kai visual và Snapshot

- Thay idle overlay của Kai bằng artwork SRU mới và đồng bộ compatibility asset để patch cũ không kéo lại hình đã nghỉ hưu.
- Thay Entity-combat aiming overlay bằng artwork SRU mới; khi combat kết thúc runtime quay lại idle overlay.
- Xóa các binary Kai portrait/overlay đã nghỉ hưu trong khi vẫn giữ string mapping cần thiết cho save migration.
- Thay bốn Snapshot epsilon bằng đúng byte WebP chất lượng gốc từ nguồn project, không resize hoặc recompress; snapshot patch dùng bảng override có kiểm tra kích thước/SHA-256.

## LiteRT Director và build hygiene

- Tách rõ hợp đồng `BackroomsDirector` evidence telemetry khỏi production `WorldDirector` pressure model.
- Production trainer chỉ chấp nhận bốn nhãn `WORLD_DIRECTOR_PRESSURE_V1`: `NONE`, `MAZE_PRESSURE`, `ENTITY_PRESSURE`, `ITEM_OPPORTUNITY`.
- Chặn việc suy diễn pressure label từ evidence telemetry vì không có causal authority; telemetry evidence chỉ còn là exporter an toàn cho nghiên cứu riêng.
- Registered-Level evidence selection vẫn deterministic trong Core; LiteRT chỉ đề xuất broad world pressure và không sở hữu gameplay state.
- Inlined R06 source marker check vào patch chính và loại bỏ self-modifying marker patch theo thứ tự build dễ vỡ.
- Hoàn thiện Kai R08 runtime knowledge cleanup để legacy White Wraith/Black Blood/Omnivault Scan-Copy không quay lại qua retrieval cũ.

## Phạm vi phát hành

- Gồm các thay đổi chính từ PR #198, #200, #202, #203, #204, #206, #207, #209, #210 và #211.
- Android `versionCode 95`, `versionName 1.4.0`.
- APK phát hành là **Debug APK** dành cho kiểm thử: `Backroom-1.4.0-debug.apk`.
- Workflow chạy Level validation, model contract checks, Luna smoke test, full runtime patch chain, runtime contracts, Kotlin/JUnit tests, debug APK build và packaged APK verification trước khi xuất bản.
- Sau khi tạo GitHub Release, workflow tải lại APK từ Release, đối chiếu SHA-256 và chạy lại packaged APK verification trước khi coi phát hành hoàn tất.
