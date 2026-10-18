# BACKROOMS Android Text Game

Game chạy độc lập trong APK Android. Giao diện WebView, canon, ảnh Level, character data, Game State Core và save đều được đóng gói hoặc lưu cục bộ trên thiết bị; dự án không còn runtime web/Next.js.

## Runtime chính

- `android-apk/app/src/main/assets/index.html`: giao diện text game.
- `android-apk/app/src/main/java/com/rabpit/backroom/MainActivity.java`: Android/WebView bridge và AI orchestration.
- `android-apk/app/src/main/java/com/rabpit/backroom/core/`: Game State Core, inventory, party, continuity và save phiên bản hiện hành.
- `android-apk/app/src/main/assets/knowledge/`: ba kho Canon offline (`knowledge_db.json`, `canon_detail_db.json`, `canon_support_db.json`), có provenance nguồn.
- `.github/workflows/build-backroom-apk.yml`: test, build, emulator launch smoke test và phát hành APK.

## MUST READ: thêm Canon sau này

**Bắt buộc đọc [MUST-READ-ME.md](MUST-READ-ME.md) trước khi thêm, cập nhật hoặc nhập lại Canon.** Tài liệu ghi rõ nguồn, ba kho JSON, schema, điều kiện retrieval theo Core, ranh giới dữ liệu bí mật, giới hạn prompt và checklist kiểm thử.

## Build cục bộ

```bash
cd android-apk
gradle :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

APK được tạo tại `android-apk/app/build/outputs/apk/debug/app-debug.apk`.

## Canon môi trường Level 0

8 khu chính và 29 khu nhỏ đã được nhập vào kho Canon đóng gói (`android-apk/app/src/main/assets/knowledge/`). Tóm tắt 8 khu được truy xuất khi Core xác nhận Level 0; mô tả chi tiết chỉ được chọn nếu Core xác nhận `scene.zoneId`/`scene.subzoneId`. Hiện chưa có định danh khu runtime nên không ánh xạ ngầm từ `miniIndex` sang khu; không thay đổi tuyến di chuyển. Nguồn: [8 khu chính](android-apk/LEVEL_0_EIGHT_ZONES_CANON.md) và [29 khu nhỏ](android-apk/LEVEL_0_TWENTY_NINE_SUBZONES_CANON.md).

## Explorer mini-map (#653)

Explorer dùng Di chuyển / Tìm kiếm / Giao tiếp với lựa chọn do Core cấp. Hiện chỉ Level 0 có 8 mini-map; sau mini-map 8 chưa có chuyển Level hay kết thúc game; Nhật ký phòng, mục tiêu và manh mối nằm trong Game Menu. Gemini sinh outline/script, Gehihi chỉ kể sự kiện public đã stage. Save schema 7, không đọc/chuyển đổi save cũ. Xem [hợp đồng và nghiệm thu](android-apk/SCENE_RUNTIME_653.md).
