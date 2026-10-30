APK Android debug cá nhân, phiên bản 1.0.0.0, versionCode 90000001.

* Cấu hình hiện tại được lấy từ GitHub Secret `Agron` trong lúc đóng gói. Không dùng lại key cũ.
* Đóng gói lại mã Android đã phục hồi từ APK 1.0.0.0.8. Giữ nguyên mã gameplay, giao diện và tài nguyên; chỉ thay cấu hình, bộ kiểm tra cấu hình cũ và phiên bản.
* APK được ký bằng chứng thư debug tạo trong runner. Chữ ký mới so với APK phục hồi; Android có thể yêu cầu gỡ bản cũ trước khi cài. Hãy xuất save cần giữ trước khi gỡ.
* Workflow kiểm tra yêu cầu sinh JSON thực tế với key mới, tính toàn vẹn của các file không thay đổi, chữ ký APK, cờ debuggable, cài đặt và khởi chạy trên Android 15.
* Chưa kiểm thử đầy đủ gameplay hoặc xác minh trên Moto G86 Power 5G thực tế. Đây là bản đóng gói từ bytecode phục hồi, không phải bản build từ mã nguồn Android gốc.
