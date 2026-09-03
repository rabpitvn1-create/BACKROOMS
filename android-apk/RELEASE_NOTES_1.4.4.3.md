# Backroom 1.4.4.3 Debug

Bản 1.4.4.3 đóng gói trạng thái `main` mới nhất sau v1.4.4.2, tập trung vào phản hồi combat/overlay, tiếp tục story Level 0 và giảm fragmentation trong runtime patch chain.

## Combat và Snapshot

- Lucia có overlay riêng khi Party thực sự đối đầu Entity, không thay đổi authority của combat hay Party membership.
- Character overlay có ground shadow nhẹ để bám nền tốt hơn mà không thay đổi sprite hoặc gameplay.
- Thêm hit feedback ngắn khi nhân vật/Entity nhận damage và chuyển actor mượt hơn khi Party hết lượt.
- Combat feedback chỉ phản chiếu kết quả authoritative đã có; damage, HP, AP, defeat và loot vẫn do Core/runtime quyết định.

## Story và continuity

- Hourly story tiếp tục đúng một bước sang `0.11 / Water Damage`, giữ route Level 0 hiện tại và các Core/RNG/quest/companion gate.
- Survival pressure ở Water Damage được thể hiện qua nước bẩn, đồ ướt, footing, nhiệt và hư hỏng môi trường; không tự sinh Almond Water, NPC, loot, evidence hay shortcut.

## Patch-chain maintenance

- Gộp chicken-rice test compatibility vào các finalizer sở hữu test tương ứng và loại standalone compatibility patch.
- Thêm patch reachability/orphan audit vào Preflight để đo script nào còn đường thực thi.
- Xóa `patch-item-identity-authority.py`, orphan lịch sử duy nhất được audit xác nhận tại thời điểm cleanup.

## Phiên bản và phát hành

- Android `versionCode 102`, `versionName 1.4.4.3`.
- APK debug: `Backroom-1.4.4.3-debug.apk`.
- Workflow chạy Level validation, provider health gates, runtime patch-chain/audit, runtime contracts, Kotlin/JUnit tests, APK build và packaged APK verification trước khi phát hành.
- Workflow chỉ tạo `v1.4.4.3` khi tag/release chưa tồn tại, sau đó tải lại APK, đối chiếu SHA-256 và chạy lại packaged verification.
