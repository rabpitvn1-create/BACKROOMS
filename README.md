# BACKROOMS Android Text Game

Game chạy độc lập trong APK Android. Giao diện WebView, canon, ảnh Level, character data, Game State Core và save đều được đóng gói hoặc lưu cục bộ trên thiết bị; dự án không còn runtime web/Next.js.

## Runtime chính

- `android-apk/app/src/main/assets/index.html`: giao diện text game.
- `android-apk/app/src/main/java/com/rabpit/backroom/MainActivity.java`: Android/WebView bridge và AI orchestration.
- `android-apk/app/src/main/java/com/rabpit/backroom/core/`: Game State Core, inventory, party, continuity và save migration.
- `android-apk/app/src/main/assets/knowledge/knowledge_db.json`: knowledge database có provenance từ nguồn Drive và Project canon đã khóa.
- `android-apk/sru-backrooms-async-canon-r01.txt`: canon nền hiện hành cho SRU, nhiệm vụ Backrooms năm 2299 và đầu mối Async.
- `.github/workflows/build-backroom-apk.yml`: test, build, emulator launch smoke test và phát hành APK.

## Build cục bộ

```bash
cd android-apk
gradle :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

APK được tạo tại `android-apk/app/build/outputs/apk/debug/app-debug.apk`.
