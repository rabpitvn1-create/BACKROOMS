# Backroom 1.3.7 Debug

Bản 1.3.7 phát hành lại từ `main` sau khi sửa dứt điểm phần mở đầu cốt truyện còn sót từ bản cũ trong APK 1.3.6.

## Sửa cốt truyện mở đầu

- Prologue trong APK giờ bắt đầu đúng ở **năm 2299** với nhiệm vụ của **SRU** điều tra **Async**.
- Kai, Iris và Syvial **chủ động đi qua cùng một cổng không gian** theo nhiệm vụ, không còn mở đầu nhà hàng và không còn no-clip ngoài ý muốn.
- Sau khi vượt cổng, Backrooms phân tán cả ba tới các Level khác nhau; Kai bắt đầu một mình tại Level 0 và chưa biết vị trí Iris hay Syvial.
- Loại bỏ khỏi cốt truyện đang chạy các dấu vết cũ: nhà hàng, shared no-clip premise, Black Blood, mốc 2267 và Hứa Thuý Lan.
- Đồng bộ `STORY.MAIN.OBJECTIVE` và `STORY.MAIN.SEPARATION` để Gemini nhận đúng continuity 2299 / SRU / Async thay vì baseline cũ.
- Runtime story authority sẽ fail-closed nếu các marker của prologue cũ quay trở lại trong bản build.

## Continuity nhân vật

- Lucia Lục vẫn là fixed encounter tại Level 0, không cần quest và không dùng random spawn.
- Syvial không random spawn; reunion được khóa theo cốt truyện tại Level 37.
- Iris không random spawn; reunion được khóa theo cốt truyện tại Level 94.

## Nguồn phát hành

Bản này được chuẩn bị từ `main` sau PR #184, tại nền commit `698bf692a126364359a1dd5524c2333ecd890433`, cùng toàn bộ thay đổi gameplay đã có trong 1.3.6.

## Phiên bản Android

- `versionCode 92`
- `versionName 1.3.7`

Đây là APK debug dành cho kiểm thử.
