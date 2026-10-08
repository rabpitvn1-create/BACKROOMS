# Item Creation Authority

**Bắt buộc đọc file này trước khi tạo, spawn, drop, grant hoặc thêm bất kỳ Item mới nào vào runtime.**

Game State Core là nguồn sự thật duy nhất cho danh tính Item. Tên hiển thị, lời kể của GM, output AI, generic loot hoặc text người chơi không có quyền tự tạo Item ID. Nếu một Item runtime không có ID được Core công nhận thì Item đó không hợp lệ.

## Điểm vào bắt buộc

Item thường phải được khai báo trong:

`android-apk/app/src/main/java/com/rabpit/backroom/core/ItemRegistry.kt`

Equipment đã có trong `EquipmentCatalog` được `ItemRegistry.definition()` nhận diện và không cần khai báo trùng trong `coreDefinitions`.

Luồng đúng:

```text
Item definition in Core
        ↓
stable itemId
        ↓
ENTITY drop hoặc CHEST contents
        ↓
ItemRegistry
        ↓
authoritative ItemStack / Inventory
```

Không được tạo luồng khác để lách registry.

## Quy tắc ID

1. Mỗi Item type phải có một ID ổn định và duy nhất.
2. ID là identity. `displayName` chỉ để hiển thị và có thể khác text do AI gửi vào.
3. Không sinh ID từ tên bằng slug, hash, `stableItemId(name)` hoặc logic tương tự cho runtime acquisition.
4. Không dùng text do GM/AI sinh làm ID.
5. Không đổi ID của Item đã được dùng trong save/runtime nếu chưa có migration tương ứng.

Ví dụ hiện tại:

```kotlin
const val ITEM_BANDAGE_ID = "medical:bandage"

ITEM_BANDAGE_ID to CoreItemDefinition(
  ITEM_BANDAGE_ID,
  "Băng gạc",
  "medical",
  defaultMetadata = mapOf(
    "consumable" to "true",
    "consumedOnUse" to "true"
  )
)
```

Khi cần tạo stack authoritative, ưu tiên:

```kotlin
ItemRegistry.stack(
  ItemRegistry.ITEM_BANDAGE_ID,
  quantity = 1,
  metadata = mapOf("itemOrigin" to "ENTITY")
)
```

Đừng vá bằng cách tạo `ItemStack("id-tu-bia", ...)` ở một code path khác rồi hy vọng Core sẽ coi như hợp lệ. Nó không nên làm vậy.

## Nguồn runtime được phép

Runtime acquisition hiện chỉ chấp nhận hai origin authoritative:

- `ENTITY`: drop do code Entity/combat tạo.
- `CHEST`: Item đã tồn tại trong authoritative Chest contents. Bắt buộc có `metadata.chestId` ổn định.

Ví dụ Chest:

```json
{
  "id": "medical:bandage",
  "name": "Băng gạc",
  "quantity": 1,
  "available": true,
  "metadata": {
    "itemOrigin": "CHEST",
    "chestId": "chest:medical:001"
  }
}
```

Các nguồn sau **không được phép tạo Item runtime mới**:

- GM/Gemini `inventory_upsert`;
- story/narration grant;
- generic loot tạo vật phẩm rời;
- `WORLD` loose item không chứng minh được provenance `ENTITY` hoặc `CHEST`;
- inventory snapshot diff;
- tên vật phẩm do AI/người chơi tự nêu.

Nếu `ENTITY` hoặc `CHEST` đưa một `itemId` không có trong Core registry, runtime phải reject với `unknown_item_id`. Chest thiếu `chestId` phải reject với `chest_source_missing`.

## Checklist khi thêm Item mới

Trước khi commit, phải kiểm tra đủ các điểm sau:

- thêm stable ID và definition vào `ItemRegistry.kt`, hoặc dùng ID đã có trong `EquipmentCatalog`;
- mọi Entity/Chest producer dùng đúng ID đó;
- display name không được dùng làm identity;
- metadata mặc định nằm ở Core khi nó thuộc bản chất Item;
- không mở thêm origin ngoài `ENTITY` / `CHEST`;
- thêm hoặc cập nhật regression test cho ID, canonical name và source authority;
- chạy test liên quan trước, sau đó chạy build/test theo `README.md`.

Các test hiện có cần được giữ xanh gồm ít nhất:

`ItemRegistryTest`

`ItemSourceAuthorityFinalTest`

Nếu thay đổi ảnh hưởng Omnivault hoặc physical instance identity, phải kiểm tra thêm:

`OmnivaultInstanceAuthorityTest`

## Luật cuối

**Không có ID trong Core thì không có Item.**

Nếu một feature cần tạo Item nhưng không thể đi qua `ItemRegistry` / `EquipmentCatalog`, feature đó phải được sửa. Không thêm exception chỉ để code chạy qua. Exception kiểu đó chính là cách registry biến thành đồ trang trí, một truyền thống phần mềm khá đáng chán.
