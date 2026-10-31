APK Android debug cá nhân, phiên bản 1.0.0.1, versionCode 90000002.

* Tích hợp bộ bốn ảnh Cao Minh mới gồm avatar, overlay chiến đấu, overlay khám phá và ma kiếm.
* Bốn tài nguyên được đăng ký bằng URL Cloudinary có phiên bản trong assets/characters/cao-minh-assets.json, dùng cùng nguồn với GitHub Pages.
* WebView lưu Blob ảnh qua IndexedDB sau lần đồng bộ trực tuyến thành công, để lần mở sau vẫn truy xuất được khi ngoại tuyến. Nếu chưa đồng bộ hoặc không được cấp quyền lưu trữ, cần có mạng để tải ảnh.
* Không nhúng ảnh vào repository hoặc đóng gói chúng dưới dạng Base64. Các thành phần Android còn lại được giữ nguyên ngoài cấu hình và thay đổi đã liệt kê.
* Dùng sáu key mới GEMINI_API_KEY_1 đến GEMINI_API_KEY_6. Argon vẫn tạm tắt trong APK debug này.
* Workflow kiểm tra các bản ghi hình ảnh, cấu hình, APK, chữ ký debug và thử khởi chạy trên Android 15.
* Đây là bản APK xây dựng lại từ bytecode đã phục hồi. Chưa xác minh trên Moto G86 Power 5G thực tế.
