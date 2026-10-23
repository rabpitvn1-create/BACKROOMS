# Industrial CRT trên nền 1.1.63.0.3

Asset lấy từ [bộ Industrial CRT trên Drive](https://drive.google.com/drive/folders/1H13I34yN6FfIN4qdXpoN3j2InSmjZT60), gồm năm texture ban đầu và khung màn hình đen ngày 20/10. Sáu WebP được giữ nguyên byte; đường dẫn Drive, kích thước, dung lượng và SHA-256 nằm trong `app/src/main/assets/ui/industrial-crt-v1/assets.json`. Tổng ảnh: 295.262 byte. Game nạp toàn bộ ảnh cục bộ, không gọi Drive khi chạy.

Theme thay nền CRT, khung log/panel, nút kim loại, nút thực hiện màu vàng và thẻ trạng thái giấy hồ sơ. Snapshot chỉ vẽ texture vào đường viền 1 px sẵn có bằng `border-image-width:1` (hệ số nhân độ dày viền gốc). Không có lớp bezel phủ vào ảnh. Không đặt lại width, height, aspect-ratio, margin, padding hay độ dày border của snapshot; ảnh cảnh, nhân vật và entity vẫn do renderer hiện hữu quản lý. Bề rộng khung theo viewport, chiều cao 300 px và vùng ảnh bên trong được bảo toàn, kể cả landscape.

Script giao diện chỉ phản chiếu nội dung `#status` vào menu và sheet hành động bằng `textContent`. Lưu/Tải và cơ chế nhấn hai lần để xác nhận thao tác phá hủy tiếp tục dùng handler cũ.

`prepare-runtime.py` cung cấp lại entry point chuẩn bị runtime đã mất khi các workflow bị gỡ: chạy đúng 39 patch của pipeline 1.1.63.0.3, theo thứ tự gốc, rồi dừng trước Gradle. Không thêm workflow. Có sửa regex trong `patch-character-stats-1193a-restore.py`: regex cũ dừng tại dấu `}` của callback bên trong `comparisonRows`, sinh JavaScript lỗi và làm hỏng script chi tiết trang bị. Regex mới lấy đủ hàm một dòng và báo lỗi nếu không tìm được đúng một hàm.

## Xác minh

Sau khi chuẩn bị runtime trên checkout sạch, với Playwright có sẵn trong môi trường phát triển:

```bash
python3 android-apk/prepare-runtime.py
node --test android-apk/tests/industrial-crt.browser.cjs android-apk/tests/snapshot-combat-damage.test.cjs
```

Có thể đặt `BACKROOMS_RUNTIME_ROOT` tới thư mục `android-apk` đã chuẩn bị riêng, `CHROMIUM_EXECUTABLE` tới Chromium có sẵn và `UI_QA_SCREENSHOTS` tới thư mục ghi screenshot.

Kiểm tra trình duyệt dùng HTML/CSS/JS thật sau patch và JavaScript giải mã từ `installUiEnhancements()`. Chỉ thay URL asset Android thành URL HTTP cục bộ; bridge Android là test double. Đã kiểm tra 320×640, 390×844 và 1280×800: snapshot có cùng bounding box, margin/padding/border khi bật/tắt theme; ảnh cảnh/nhân vật có cùng kích thước, vị trí và transform. So sánh pixel vùng ảnh (bỏ 8 px quanh mép để tránh góc bo 7 px gốc) cả trước và sau redraw; regression này bắt được bezel 12 px che ảnh dù bounding box không đổi. Thanh hành động nằm trong viewport; không tràn ngang; menu, Lưu/Tải, lần nhấn đầu của New Game, đóng sheet hành động và redraw snapshot hoạt động; không có page exception. Kiểm tra parse tất cả script inline để chặn lỗi regex tái phát. Bộ test combat feedback sẵn có cũng được chạy.

Đây là xác minh trình duyệt, không phải kiểm thử trên thiết bị Android hay gọi GM thật. Chưa build APK hoặc chạy Gradle trong môi trường này. Không thay Gameplay Core, MainActivity, Gradle, version, applicationId, canon hoặc save schema; không thêm tương thích save cũ. Release/APK mục tiêu vẫn là bản đã khôi phục, không bị ghi đè bởi thay đổi này.
