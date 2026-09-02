# Backroom 1.4.3 Debug

Bản 1.4.3 đóng gói nhạc nền mới vào APK và phát loop liên tục trong lúc game đang ở foreground.

## Background music

- Dùng đúng file nhạc do người dùng cung cấp trên Google Drive, khóa bằng kích thước và SHA-256 để CI từ chối file sai hoặc trang HTML giả danh audio.
- Nhạc được tải ở build/preflight, sau đó đóng gói cục bộ thành `res/raw/backroom_bgm.m4a`; APK không cần tải nhạc qua mạng khi chạy.
- Android `MediaPlayer` phát nhạc với `setLooping(true)` và gain 40%.
- Nhạc pause khi Activity vào background, resume khi quay lại và release khi Activity bị hủy để tránh nhiều player chồng nhau.
- Packaged APK verifier kiểm tra asset M4A đúng SHA-256 và tìm helper playback trên toàn bộ `classes*.dex` của APK multidex.

## Không thay đổi gameplay

- Không thay đổi canon, AI routing, provider order, dialogue authority, gameplay state, inventory, combat, dice, progression hoặc save behavior.
- Giữ nguyên provider timeout hotfix và Gemini provider smoke từ 1.4.2.

## Phiên bản và phát hành

- Bao gồm background-music integration từ PR #220.
- Android `versionCode 98`, `versionName 1.4.3`.
- APK debug: `Backroom-1.4.3-debug.apk`.
- Workflow chạy Level validation, Gemini/Luna/Haku health gates, runtime patches, runtime contracts, Kotlin/JUnit tests, APK build và packaged APK verification trước khi merge/release.
- Sau khi vào `main`, workflow tạo `v1.4.3`, tải lại APK đã phát hành, đối chiếu SHA-256 và chạy lại packaged verification.
