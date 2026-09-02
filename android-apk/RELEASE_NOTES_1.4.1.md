# Backroom 1.4.1 Debug

Bản 1.4.1 đóng gói các thay đổi sau v1.4.0, tập trung vào sửa định tuyến hội thoại/LiteRT và bổ sung lớp biên tập prose Haku sau Game Master.

## LiteRT dialogue intent routing

- Sửa false-positive khiến một số câu hỏi về súng/đạn hoặc lệnh chiến thuật cho companion bị hiểu nhầm thành hành động `USE_ITEM`.
- Thêm deterministic safety guards cho các câu hỏi vũ khí/đạn không làm thay đổi state và các lệnh chiến thuật companion.
- Mở rộng corpus LiteRT với hard-negative samples và regression cases để giữ các câu hội thoại/tactical request ngoài item authority khi không có hành động vật phẩm thực sự.
- Giữ nguyên positive controls cho thao tác dùng/chuyển vật phẩm hợp lệ; không làm yếu inventory authority, combat, canon, progression hay WorldDirector.

## Haku prose-only editor

- Thêm `HAKU_API_KEY` vào Android `BuildConfig` và CI, dùng endpoint Vilao `/chat/completions` với model `claude-haiku-4-5-20251001`.
- Pipeline player-facing prose: Gemini GM -> validation/audit/state commit -> Haku prose edit -> log/UI.
- Haku chỉ được sửa cách diễn đạt tiếng Việt: câu lủng củng, lặp từ/lặp ý không mang thông tin, nhịp câu cụt/máy móc và dấu câu.
- Haku không được thay đổi gameplay state, ops, canon validation, dice, tên riêng, con số, vật phẩm, địa điểm, quan hệ, kết quả, POV, xưng hô hay ý nghĩa lời thoại.
- Registered-Level narration chỉ gửi phần prose tự do qua Haku sau khi claim đã được xác thực; exact surfaced evidence được nối lại sau đó và không đi qua editor.
- Editor fail-open: timeout, provider error hoặc output không hợp lệ sẽ giữ nguyên prose Gemini, không fail turn và không reroll gameplay.
- CI có Haku API smoke test trước runtime patch chain để phát hiện sớm lỗi secret/provider.

## Phiên bản và phát hành

- Gồm các thay đổi chính từ PR #213 và PR #214.
- Android `versionCode 96`, `versionName 1.4.1`.
- APK debug dành cho kiểm thử: `Backroom-1.4.1-debug.apk`.
- Workflow chạy Level validation, LiteRT checks, Luna smoke test, Haku smoke test, full runtime patch chain, runtime contracts, Kotlin/JUnit tests, APK build và packaged APK verification trước khi xuất bản.
- Sau khi merge vào `main`, workflow chỉ tạo `v1.4.1` nếu tag/release chưa tồn tại; sau đó tải lại APK, đối chiếu SHA-256 và chạy lại packaged APK verification.
