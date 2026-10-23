# BACKROOMS Android Text Game

Game chạy độc lập trong APK Android. Giao diện WebView, canon, ảnh Level, character data, Game State Core và save đều được đóng gói hoặc lưu cục bộ trên thiết bị; dự án không còn runtime web/Next.js.

## Runtime chính

- `android-apk/app/src/main/assets/index.html`: giao diện text game.
- `android-apk/app/src/main/java/com/rabpit/backroom/MainActivity.java`: Android/WebView bridge và AI orchestration.
- `android-apk/app/src/main/java/com/rabpit/backroom/core/`: Game State Core, inventory, party, continuity và save migration.
- `android-apk/app/src/main/assets/knowledge/knowledge_db.json`: knowledge database có provenance từ nguồn Drive.
- `android-apk/prepare-runtime.py`: áp dụng chuỗi patch runtime của bản 1.1.63.0.3 trước khi build. Repository hiện không có workflow tự động.
- `android-apk/app/src/main/assets/ui/industrial-crt-v1.css`: bộ UI Industrial CRT từ Drive; khung snapshot giữ nguyên kích thước của runtime gốc.

## Build cục bộ

```bash
cd android-apk
python3 prepare-runtime.py
gradle :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

APK được tạo tại `android-apk/app/build/outputs/apk/debug/app-debug.apk`.

Chỉ chạy bước chuẩn bị runtime một lần trên checkout sạch; các patch gốc không hỗ trợ áp dụng lặp. Không build trực tiếp HTML/Java chưa patch vì chúng chưa phải runtime phát hành.
