# Backroom 1.3.6 Debug

Bản 1.3.6 đóng gói trạng thái `main` sau v1.3.5, tập trung vào ranh giới authority của AI, progression cốt truyện Level 0 → Level 1, continuity nhân vật SRU và bộ snapshot Backrooms đã chuẩn hóa lại.

## Thay đổi chính

- Hoàn thiện ranh giới gameplay/narration: Core giải quyết kết quả authoritative trước, Gemini chỉ kể lại dữ kiện đã được phép thấy; prose người chơi không còn lộ các thuật ngữ nội bộ như Core/engine/commit/blueprint.
- Discovery chỉ chiếu evidence đã thực sự được phát hiện; evidence quan trọng được đánh dấu trực quan trong log mà không làm lộ hidden escape solution.
- Campaign route dùng `campaignOrder` và transition graph làm authority; PendingTurn và RNG của action được khóa để retry/provider failure không reroll cùng một lượt.
- WorldDirector/LiteRT chỉ được đề xuất áp lực cục bộ trong legal sandbox, không sở hữu evidence semantics, hidden solution, inventory, combat hay story progression.
- Khóa canon campaign hiện hành ở năm 2299: Kai, Iris và Syvial thuộc SRU, cùng điều tra Async, vào Backrooms qua cùng một spatial gate rồi bị phân tán.
- Lucia Lục trở thành fixed encounter ở Level 0; Syvial và Iris không còn random spawn, reunion được khóa theo cốt truyện ở Level 37 và Level 94.
- Thêm `StoryState` / `StoryQuestEngine` Core-owned theo cấu trúc `Chapter → Act → Quest → Objective` cho tuyến Level 0 → Level 1. Gemini và LiteRT không thể tự advance quest, mỗi committed signal hoàn thành tối đa một objective.
- Quest Level 0 → Level 1 sử dụng evidence thật của registered Level, Level completion và các area milestone thay vì suy ra quest hoàn tất chỉ từ narration.
- Việt hóa thêm prose gameplay và mô tả kỹ năng hiển thị, giữ nguyên tên riêng/canonical skill names và gameplay values.
- Jane Doe được hoàn thiện thành Entity riêng với combat kit/Lilith Core; Kai - The Devil Within được sửa Snapshot và tăng encounter rate theo contract hiện hành.
- Bộ background Level 0–6 được rebuild từ nguồn Backrooms được chấp thuận: 43 area, mỗi area 4 ảnh WebP 512×288, tổng 172 asset, không dùng ảnh AI-generated cho environment trong bộ này.
- APK vẫn đi qua Level validation, runtime patch contracts, Kotlin tests, build, packaged APK verification, artifact upload; khi merge vào `main`, workflow tạo Release rồi tải APK ngược lại để đối chiếu SHA-256.

## Phạm vi hợp nhất từ v1.3.5

- PR #165: Giữ văn kể gameplay hoàn toàn bằng tiếng Việt.
- PR #166: Highlight discovered evidence in gameplay.
- PR #167: Hide internal runtime terms from gameplay prose.
- PR #168: Separate registered Level outcomes from narration.
- PR #169: Replace John Doe with Jane Doe entity.
- PR #171: Fix Kai Devil Within snapshot and 10% encounter rate.
- PR #172: Make campaign-order route authoritative.
- PR #173: Separate discovery semantics from narrator knowledge.
- PR #174: Introduce Core-gated World Director proposals.
- PR #175: Persist pending turns and lock action RNG.
- PR #176: Integrate Level 0 to Level 1 story arc.
- PR #177: Việt hóa hoàn toàn mô tả kỹ năng hiển thị.
- PR #178: Intermediate Async mission retcon, later superseded by the final 2299/SRU continuity in #181/#182.
- PR #179: Intermediate Level 0 pixel-art background experiment, later replaced by the trusted-source snapshot rebuild in #180.
- PR #180: Rebuild Backrooms snapshots from approved sources.
- PR #181: Make companion encounters story-owned.
- PR #182: Add Core quest state for Level 0 to Level 1.

## Phiên bản Android

- `versionCode 91`
- `versionName 1.3.6`

Đây là APK debug dành cho kiểm thử.
