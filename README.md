# BACKROOMS Android Text Game

Game chạy độc lập trong APK Android. Giao diện WebView, canon, ảnh Level, character data, Game State Core và save đều được đóng gói hoặc lưu cục bộ trên thiết bị; dự án không còn runtime web/Next.js.

## Runtime chính

- `android-apk/app/src/main/assets/index.html`: giao diện text game.
- `android-apk/app/src/main/java/com/rabpit/backroom/MainActivity.java`: Android/WebView bridge và AI orchestration.
- `android-apk/app/src/main/java/com/rabpit/backroom/core/`: Game State Core, inventory, party, continuity và save migration.
- `android-apk/app/src/main/assets/knowledge/knowledge_db.json`: knowledge database có provenance từ nguồn Drive.
- `.github/workflows/build-backroom-apk.yml`: test, build, emulator launch smoke test và phát hành APK.

## Canon Runtime 2.0

Canon của game được quản trị theo kiến trúc Canon Runtime 2.0 (truy xuất theo scene, record có provenance, ngân sách context trọn record). Ai — người hoặc mô hình AI — cần thêm/sửa Canon đều phải đọc trước [`android-apk/CANON_RUNTIME_2_README.md`](android-apk/CANON_RUNTIME_2_README.md) (hướng dẫn tích hợp + quy trình ingest bắt buộc + trạng thái theo Gate), và tài liệu thiết kế gốc tại [`docs/BACKROOMS_CANON_RUNTIME_2_ARCHITECTURE.md`](docs/BACKROOMS_CANON_RUNTIME_2_ARCHITECTURE.md).

## Build cục bộ

```bash
cd android-apk
gradle :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

APK được tạo tại `android-apk/app/build/outputs/apk/debug/app-debug.apk`.
