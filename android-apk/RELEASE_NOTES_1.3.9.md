# Backroom 1.3.9 Debug

Bản 1.3.9 sửa cách hiển thị manh mối, cảnh chuyển tiếp của Level 0 và lỗi dùng vật phẩm cho đồng đội.

## Manh mối và lộ trình Level 0

- Bỏ nhãn “BẰNG CHỨNG”, viền vàng và nền nổi bật trong log. Manh mối được trình bày như quan sát, không kết luận hộ người chơi.
- Sửa dữ liệu Level 0 đang mô tả đường sang bãi đỗ xe bê tông. Điểm đến đầu tiên của campaign là epsilon, sau đó đi theo các sublevel/vùng đặc biệt đã khai báo rồi mới tới Level 1.
- Lệnh di chuyển không khớp hành động hợp lệ được xử lý trong runtime của Level, tránh để Gemini kể vượt khu dù Core chưa xác nhận.
- Kiểm tra cảnh bãi đỗ xe xuất hiện sai tại Level 0/epsilon trong lời kể thực tế, kể cả kết quả sau bước sửa lời kể.
- Save dùng bộ dữ liệu Level 0 có sẵn được cập nhật phần mô tả, giữ seed, vật phẩm, manh mối đã khám phá và tiến độ giải đố. Các topology tự sinh khác được giữ nguyên.

## Vật phẩm cho đồng đội

- “Kai cho Lucia ăn cơm gà” lấy hộp cơm từ túi Kai và áp dụng tác dụng cho Lucia; không cần chuyển đồ trước.
- “Lucia ăn cơm gà” vẫn lấy đồ từ túi Lucia. Chiều ngược lại như “Lucia cho Kai uống nước suối La Vie” cũng nhận đúng người cho và người nhận.
- Tên nhân vật không tồn tại hoặc bị trùng không được tự chuyển thành dùng đồ cho bản thân. Khi người cho thiếu đồ, game không lấy bù từ túi người nhận.
- Sửa nhận diện động từ để phần tên như “Văn” không bị hiểu nhầm là “ăn”.
- Thêm test cho các luồng trên, cách chuyển đồ rồi ăn đang dùng, và việc gửi lặp lại cùng một lệnh.

## Phiên bản và kiểm tra

- Gồm các bản sửa trong PR #195 và PR #196.
- Android `versionCode 94`, `versionName 1.3.9`.
- APK debug dành cho kiểm thử, theo quy trình phát hành hiện có.
- Workflow kiểm tra dữ liệu, API, runtime patch, test Kotlin và APK trước khi xuất bản; sau đó tải APK từ Release về đối chiếu SHA-256 và kiểm tra lại gói cài đặt.
