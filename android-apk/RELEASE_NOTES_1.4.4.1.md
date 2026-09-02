# Backroom 1.4.4.1 Debug

Bản 1.4.4.1 đóng gói trạng thái `main` mới nhất sau v1.4.4, tập trung vào các cập nhật Entity Codex và hotfix knowledge runtime gần nhất mà không thay đổi gameplay balance.

## Entity Codex và knowledge runtime

- Giữ các bổ sung codex hiện hành cho Clump, Duller, Hound và False Puddle.
- Giữ hotfix loại bỏ record trùng của Deathmoth và Smiler khỏi `entity_codex_supplement.json`, tránh lỗi `Duplicate knowledge record id` khi `KnowledgeContextEngine` nạp nhiều nguồn knowledge.
- Duy trì kiểm tra uniqueness của knowledge record ID; không nới lỏng fail-fast duplicate guard.

## Runtime giữ nguyên

- Không thay đổi encounter rate, combat balance, inventory, progression, save contract hay dialogue authority chỉ vì bump version.
- Giữ provider health gates Gemini/Luna/Haku, runtime patch chain, Kotlin/JUnit tests và packaged APK verification.

## Phiên bản và phát hành

- Android `versionCode 100`, `versionName 1.4.4.1`.
- APK debug: `Backroom-1.4.4.1-debug.apk`.
- Workflow chỉ tạo `v1.4.4.1` khi tag/release chưa tồn tại, sau đó tải lại APK, đối chiếu SHA-256 và chạy lại packaged verification.
