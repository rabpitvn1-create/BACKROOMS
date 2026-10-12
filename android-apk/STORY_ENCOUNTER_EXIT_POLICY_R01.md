# Bước 8/10 · Cô lập Encounter và Exit cũ khỏi Story Checkpoint

## Mục tiêu

Trong **Checkpoint đã có Milestone của Gemini**, Entity xuất hiện ở cuối theo FINAL_ENTITY đã khóa; gameplay không được sinh một Entity mới từ roll 45% hoặc mở Exit vì năm lần roll HiddenExitStreak. **Cơ chế Combat, Inventory, Party và ký ức truyện không được suy ra từ lời kể.**

Bước 8 là chính sách tách luồng, không sửa AI planner hoặc nội dung truyện. Không thêm sự kiện, Clue, Entity hay kết quả Canon mẫu vào runtime.

## Hành vi đã triển khai

| Đường thực thi | Có Milestone | Không có Milestone |
|---|---|---|
| EntityEncounterPool 45% | Không roll Entity ngẫu nhiên; Event FINAL_ENTITY của Core điều khiển Boss | Giữ cơ chế hiện hữu |
| `anNhienRead`, `exitProbe`, `_hiddenExitStreak` | Không đủ điều kiện tung xúc xắc, `levelExit` không được đánh dấu sẵn sàng | Giữ cơ chế hiện hữu |
| HiddenExitStreak áp dụng sau GM | Không áp dụng, xóa proof Exit cũ khỏi candidate | Giữ cơ chế hiện hữu |
| Di chuyển node | Chỉ được đến node kế bên đúng đồ thị `LevelRouteRegistry`, cùng main-Level, qua hành động đi qua chủ động | Dùng các cổng Exit hiện hành |
| Chuyển main-Level | Bị khóa, kể cả khi trạng thái Exit cũ có `exitReady=true` | Theo hệ thống route cũ |
| Party, Combat, Inventory, Story Continuity | Không đổi | Không đổi |

`MilestoneWorldGate` là chính sách dùng chung:
- Java Android kiểm tra yêu cầu di chuyển khi áp GM operations và `reconcileVisualWorldState`; không dùng `HiddenExitStreak.projectedReady` trong luồng có Milestone.
- **Game Core xác thực lại** node đích theo state `world["levelJson"]`, từ chối chuyển main-Level hoặc nhảy node, và gỡ các field chứng nhận Exit cơ học từ candidate trong cùng lần commit. Những field khác, ví dụ `exploration.snapshotStep`, `levelTurns`, `storyContinuity` và `entityEncounterKey`, vẫn được giữ.
- `SnapshotSceneProgress` tiếp tục quản lý ảnh môi trường độc lập Exit.

## Giới hạn quan trọng

- `CP-L00-20` không thể tự nhảy sang Level 1. Dự án **chưa định tổng Checkpoint và hợp đồng kết thúc main-Level 1**, nên Bước 8 không tự tạo gateway để vượt qua.
- **Checkpoint và node Level không phải một.** Di chuyển trong Level 0 không tự tăng CP; CP do Combat cuối và plan kế tiếp quyết định.
- Chưa vô hiệu hóa random **Survivor/follower/Party encounter** hoặc hazard vì thuộc các hợp đồng gameplay khác. Không đánh đồng chúng với Entity random. Nếu tác giả muốn Gemini kiểm soát những cuộc gặp đó, cần định nghĩa rõ trước khi gỡ mechanic hiện hành.
- `HiddenExitStreak` vẫn tồn tại cho những luồng gameplay **không có Milestone**; đây không phải adapter save cũ. Xóa hẳn hệ thống cần quyết định riêng về các luồng ngoài checkpoint hiện hữu.
- Không coi một `Clue` trong StoryFactLedger tự mở Exit gameplay. Clue là ký ức truyện, Exit phải có bằng chứng/contract gameplay riêng.

## Xác minh

Kiểm thử có mục tiêu: tuyến node cùng parent được phép nếu có cạnh, không nhảy node/qua parent; cấm red_rooms → Level 1 dù đồ thị có cạnh; gỡ exit proof giả mà giữ Clue và progress khác; Core candidate không thể tái đưa `exitReady` trở lại; không có Milestone thì giữ hành vi Exit hiện tại. Kiểm thử source contract khóa đúng Android wiring và luật vô hiệu roll. Build Backroom APK CI là cổng xác minh cuối.
