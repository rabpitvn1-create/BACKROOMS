# INVENTORY ICON HARD LOCK

**Mã:** `INVENTORY_ICON_HARD_LOCK_R01`  
**Trạng thái:** `ACTIVE / HARD LOCK`  
**Phạm vi:** Toàn bộ icon vật phẩm hiển thị trong Inventory của Android APK.

## Mục tiêu

Tạo icon nhẹ, dễ nhận diện ở kích thước nhỏ và bám đúng vật phẩm. Icon là asset tĩnh được tạo khi build; runtime không gọi AI tạo ảnh.

## Bắt buộc

- Mỗi icon chỉ có **một vật phẩm chính**.
- Hình dạng, vật liệu, màu sắc và đặc điểm nhận diện phải bám mô tả/canon hiện hành của item.
- Nền trong suốt hoàn toàn.
- Vật phẩm nằm giữa khung và có khoảng trống an toàn quanh mép.
- Silhouette phải đọc được khi hiển thị nhỏ.
- Kích thước chuẩn: **128×128 px**.
- Đầu ra: **WebP nhị phân** trong `app/src/main/assets/inventory-icons/`.
- Tên file lấy từ item ID, ví dụ `almond-water.webp`.
- Generator phải deterministic: cùng source + cùng recipe phải cho cùng cấu trúc hình học.
- Mọi item chính thức trong `ItemCatalog` phải có recipe. Item chính thức thiếu recipe làm CI thất bại.
- Item ngoài catalog dùng `generic.webp`; không tự bịa một icon đặc thù rồi coi là canon.

## Cấm tuyệt đối

- Không chữ.
- Không glyph/ký tự viết.
- Không số.
- Không tên item trên ảnh.
- Không nhãn có chữ.
- Không logo hoặc watermark.
- Không UI, khung inventory hoặc nền cảnh trong chính ảnh.
- Không nhân vật hoặc bàn tay cầm vật phẩm.
- Không vật thể phụ chỉ để trang trí.
- Không hiệu ứng che silhouette.
- Không Base64, data URI hoặc ảnh nhúng dạng chuỗi trong HTML/JSON/Kotlin.
- Không gọi image-generation API ở runtime.

Các hình học minh họa trực tiếp bản thân vật phẩm, ví dụ hình cá trên hộp cá hoặc phần chất lỏng nhìn xuyên qua chai, được phép nếu chúng không phải chữ, số, logo hay watermark.

## Thứ tự ưu tiên

1. Đúng vật phẩm.
2. Dễ nhận diện ở kích thước nhỏ.
3. Đúng canon.
4. Đồng nhất với bộ icon Inventory.
5. File nhẹ.

## Reject gate

Kết quả không hợp lệ nếu xảy ra một trong các trường hợp sau:

- có chữ, ký tự viết, số, logo hoặc watermark;
- vật phẩm bị crop hoặc chạm mép;
- sai loại vật phẩm;
- có vật thể phụ không cần thiết;
- nền không trong suốt;
- không phải WebP nhị phân;
- file lớn hơn 64 KiB;
- item chính thức không có recipe;
- HTML/runtime dùng Base64 thay vì đường dẫn asset.
