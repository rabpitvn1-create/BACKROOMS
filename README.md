# BACKROOMS Android Text Game

Game chạy độc lập trong APK Android. Giao diện vẫn dùng WebView/HTML/JavaScript vì đó là UI của game; phần Android native, Game State Core và đường build APK đã được chuẩn hóa sang Java 17, không có Kotlin, Python hay LiteRT trong runtime APK. Python chỉ phục vụ patch/audit tooling của build và CI.

## Runtime chính

Thiết kế và quy trình mở rộng Markdown canon: [Canon Retriever contract](android-apk/CANON_RETRIEVER_README.md).

- `android-apk/app/src/main/assets/index.html`: giao diện text game chạy trong WebView.
- `android-apk/app/src/main/java/com/rabpit/backroom/MainActivity.java`: Android/WebView bridge và AI orchestration.
- `android-apk/app/src/main/java/com/rabpit/backroom/core/`: Game State Core thuần Java, kiểm tra state/inventory/party và save cục bộ.
- `android-apk/app/src/main/assets/knowledge/knowledge_db.json`: knowledge database đóng gói trong APK.
- `.github/workflows/release-version.yml`: kiểm tra Java-only baseline, test, build và phát hành APK.

## Phiên bản hiện tại

**Backroom 1.1.73** (`versionCode 117`). Entity, rương và hook gặp nhân vật dùng presentation offline; bổ sung knowledge projection an toàn và chặn narration cũ/duplicate tại append.

APK debug và ghi chú phiên bản được phát hành tại [GitHub Releases](https://github.com/rabpitvn1-create/BACKROOMS/releases). Xem [ghi chú 1.1.73](android-apk/RELEASE_NOTES_1.1.73.txt) để biết các thay đổi.

## Build cục bộ

Yêu cầu JDK 17 và Gradle 8.10.2 hoặc tương thích với Android Gradle Plugin đang cấu hình.

```bash
python3 android-apk/tools/patchctl.py validate
python3 android-apk/tools/patchctl.py apply full
python3 android-apk/tools/canon_audit.py
cd android-apk
gradle :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

APK được tạo tại `android-apk/app/build/outputs/apk/debug/app-debug.apk`.
