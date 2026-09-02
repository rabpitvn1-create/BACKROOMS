# Backroom 1.4.4 Debug

Bản 1.4.4 đóng gói trạng thái `main` mới nhất sau v1.4.3, gồm bước tiến cốt truyện theo giờ ở Level 0 và các cập nhật nội dung/codex liên quan, đồng thời giữ nguyên nhạc nền loop và các hotfix provider đã phát hành trước đó.

## Story và continuity

- Thêm `MAIN_STORY_HOURLY_EVOLUTION_R01` cho tuyến chính từ Prologue qua Level 0 và các sublevel hướng tới Level 1.
- Trạng thái hiện tại khóa `PROLOGUE` và `0` là đã hoàn thành, với `epsilon` là bước kế tiếp.
- Level 0 giữ trọng tâm sinh tồn, kiểm chứng môi trường, fixed encounter của Lucia và giai đoạn `SEED_OF_TRUST`; không tự biến tin đồn, hiện tượng hoặc NPC thành canon đã xác minh.
- Bổ sung entity codex supplement và cập nhật lớp knowledge context để phục vụ nội dung runtime hiện hành.

## Level snapshots

- Cập nhật nguồn snapshot và thay bộ ảnh cũ của area `0.01` bằng các snapshot `level_0.01_1.webp` đến `level_0.01_4.webp`.
- Giữ kiểm tra catalog/Level content hiện có trong preflight và packaged APK verification.

## Runtime giữ nguyên

- Giữ nhạc nền M4A đã khóa SHA-256, phát loop bằng Android `MediaPlayer` khi game ở foreground.
- Giữ provider timeout hotfix cho Luna/Haku và Gemini K1-K5 provider smoke.
- Không thay đổi version này như một lý do để nới lỏng canon, inventory, combat, progression, dialogue authority hoặc save contracts.

## Phiên bản và phát hành

- Android `versionCode 99`, `versionName 1.4.4`.
- APK debug: `Backroom-1.4.4-debug.apk`.
- Workflow chạy Level validation, Gemini/Luna/Haku health gates, runtime patch chain, runtime contracts, Kotlin/JUnit tests, APK build và packaged APK verification.
- Sau khi merge vào `main`, workflow chỉ tạo `v1.4.4` khi tag/release chưa tồn tại, sau đó tải lại APK, đối chiếu SHA-256 và chạy lại packaged verification.
