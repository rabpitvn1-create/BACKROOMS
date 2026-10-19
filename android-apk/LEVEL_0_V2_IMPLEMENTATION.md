# Level 0 V2 — trạng thái triển khai hiện hành

**Phạm vi:** PR [#654](https://github.com/rabpitvn1-create/BACKROOMS/pull/654), nhánh `feat/653-mini-map-explorer`, tiếp tục giữ **Draft**. Đây là tài liệu **hợp đồng đang chạy**, không phải bản đồ Canon, tuyên bố hoàn thành 10 giờ hoặc hồi ký các mốc cũ. Lịch sử chi tiết nằm trong commit/PR. Đối chiếu code trước khi sửa.

## Nguồn và ranh giới

- Đã đọc `MUST-READ-ME.md`; mô tả Canon được xác nhận nằm ở `LEVEL_0_EIGHT_ZONES_CANON.md`, `LEVEL_0_TWENTY_NINE_SUBZONES_CANON.md`, `Level0Environments.kt` và ba knowledge database của APK.
- **8 khu chính + 29 khu nhỏ = 37 định danh môi trường thuộc Level 0**, không phải 29 Sublevel. Môi trường Canon không xác định thứ tự đi, Entity/NPC thật, vị trí chìa khóa, kết cấu puzzle hay lối thoát.
- Bố cục gameplay, hai puzzle và lối nối tùy chọn là thiết kế tương tác theo yêu cầu triển khai, **không thay Canon vũ trụ**. Không chỉnh Google Drive. Không tự cho Rạn Nứt, Khe Tối hoặc Phòng Giao Thoa là lối thoát.
- Registry có quan hệ route `0 → 0.1` nhưng **exit readiness luôn khóa** vì chưa có quyết định Canon về vị trí/hiện tượng/điều kiện. Không thêm lối sang Level 1 và không ending bằng lời GM.

## Kiến trúc Core

- `WorldGraphRuntime.kt` là runtime chung cho graph, đi lại, khám phá, authorizing actions, MAP công khai, evidence, quy luật, thử nghiệm và xác minh save. Không tạo một Runtime cho mỗi Level.
- `WorldDefinition.kt` định nghĩa content/policy; `WorldDefinitions` chỉ đăng ký định nghĩa được phép; `Level0WorldDefinition.kt` cung cấp nội dung, bộ sinh seeded, các puzzle và chính sách Level 0. Đăng ký definition không mở Level route.
- **Graph version 3; Level 0 definition version 4; save schema 12; checkpoint `manual_checkpoint_v12`.** Save cũ không được hỗ trợ, không migration/adapter/fallback. Phiên bản hiện hành phải giữ đủ `worldGraph`, seed, phòng, khám phá, chứng cứ, dấu mốc, luật, pending/receipt, RNG đã reserve và toàn bộ combat/NPC/Party/Inventory.
- Seed 64-bit có thể tái hiện, tạo **45 phòng gameplay** trên đúng 37 môi trường (mỗi khu chính 2 phòng, mỗi khu nhỏ 1). Gameplay room ID khác environmentId; cùng môi trường vẫn dùng snapshot Canon giống nhau, phòng chỉ có nhãn vị trí sau khi được khám phá.
- Cấu trúc hành lang thường hai chiều với đường thay thế; validator kiểm connectivity và vẫn tới được tất cả phòng sau khi bỏ mỗi cạnh thường. Hai lối nối ẩn tùy chọn được lưu độc lập trạng thái phát hiện/mở khóa, gồm một chiều và hai chiều, không bắt buộc để trở về. Topology được so với bộ sinh từ seed khi load để từ chối chỉnh sửa graph.
- MAP hiển thị phòng đã đến và biên đã xác nhận; không công khai seed, danh tính phòng chưa đến, điều kiện, vị trí passage bí mật hoặc câu trả lời. Lối đã khảo sát nhưng chưa thể mở được giải thích bằng thông báo chung. ◆ là dấu MAP cá nhân, ✚ là vết khắc vật lý.
- `GameCoreFacade.beginSceneAction` xác minh version/node/room/actionId/target, stage Core, reserve encounter, ghi pending; `commitSceneAction` commit một lần sau GM. Retry dùng đúng lệnh pending đã lưu; không chạy lại hành động đã commit. Provider lỗi không giả mạo tiến độ và không reroll RNG. GameStateCodec kiểm progress và pending cùng graph, các record ghi chép phải khớp Core-authored evidence.

## Nội dung khám phá và puzzle

- Có **29 hoạt động field-study tùy chọn**, mỗi khu nhỏ một hướng khảo sát đã neo vào mô tả Canon. SEARCH `study` tạo đúng một FIELD_NOTE/phòng; ghi chép lặp không nhân đôi. Hai field note trong cùng khu chính có thể dùng INTERACT compare để tạo ghi chép đối chiếu đã xác minh. Đây không phải 29 puzzle bắt buộc, không tự tạo lore.
- Hai họ quy luật có Core state machine và bằng chứng thực tế: `PHYSICAL_CORRELATION` (Phòng Ngập/Thảm Sũng ↔ Khoang Kỹ Thuật/Trần Rỗng) và `SPATIAL_ANCHOR` (Hành Lang Lặp/Phòng Đảo, rồi lối bổ sung tới Hồi Ảnh). Có quan sát cụ thể, thử sai, vết khắc bền vững, điều kiện kiểm chứng và mở lối tùy chọn sau khi đủ proof.
- `lawInstances` đóng băng cùng graph, `evidenceState` lưu proof, `ruleFlags` chỉ Core được xác nhận, `environmentChanges` lưu dấu và phép thử sai, `records` là sổ tay. Việc combat/EXP/loot không được xóa các trường này. Các bằng chứng lưu phải có text/kind/site khớp nội dung Core cho phép, không chấp nhận thêm câu xác nhận giả trong save.
- `currentObjective` là mục tiêu ngắn hạn suy từ tiến độ đã xác minh: đánh dấu, quan sát, đối chiếu, thực nghiệm rồi khám phá phần chưa biết; không tiết lộ lối bí mật hay giả vờ Level đã kết thúc.
- **Sổ tay** trong Explorer hiển thị ghi chép Core qua `textContent` (không chèn HTML), có lối sang **danh sách action Core đang khả dụng** để chơi không cần Gemini planner. **Gợi ý ba cấp** chỉ xuất hiện sau khi người chơi chủ động yêu cầu, đọc trạng thái Core, không tốn turn/RNG/AI và chỉ tham chiếu bằng chứng hoặc môi trường đã biết. Phản hồi hint stale bị WebView loại bằng roomId + sceneVersion; không đưa hint vào scene/GM mặc định.
- Player Action ngôn ngữ tự nhiên vẫn do Gemini lập danh sách typed `MOVE/SEARCH/INTERACT/TALK`, tối đa **12 bước, không quá một lượt lập kế hoạch cho mỗi hành động đã đếm**, revalidate từng bước với Core. Gehihi chỉ kể public event đã stage, đúng phòng/Canon đích; không tự cấp bằng chứng, NPC/Entity, level route hay thưởng. Một số hành vi sáng tạo ngoài schema Core vẫn **chưa được hiện thực** và phải báo đúng là chưa hỗ trợ.

## RPG và hình ảnh

- **Tuyệt đối không đổi Entity:** tỷ lệ Entity cơ sở **3500/10000 = 35%** cho scene action đủ điều kiện theo implementation hiện hành; danh sách, rarity, điều kiện spawn, RNG/roll order đều giữ nguyên. Bản thử cân bằng theo khu `cdba82ae` đã được **hoàn tác bằng `4ae47c84`**, không có chính sách per-zone/revisit còn hoạt động.
- Giữ Cao Minh, Poker Dice/combat, retreat, HP, EXP/loot, Inventory/equipment, NPC/Party/followers trong Core. Quan sát thụ động và ghi dấu không roll encounter. Không bắt farm EXP hoặc gặp NPC ngẫu nhiên để được vào phòng/giải hai puzzle.
- 37 ảnh snapshot Level 0 có trong `app/src/main/assets/level_snapshots/` và được định tuyến theo environmentId thật; không xoay theo lượt, hash, đường đi hoặc fallback ảnh từ khu khác. Điều này cần kiểm tra UI thực tế ngoài kiểm thử ánh xạ asset.

## Bằng chứng nghiệm thu

- Các commit `df75d42a` (29 field studies) và `4ae47c84` (khôi phục Entity) đều đã PASS Verify Core Without Emulator + Build Backroom APK.
- `825a3735`: sổ tay, gợi ý, palette hành động qua Core, giới hạn planner; Verify Core và Build APK PASS.
- `4f570713`: mục tiêu điều tra động trên tiến độ thật; Verify Core và Build APK PASS.
- Các nhóm test hiện hành: `WorldGraphRuntimeTest`, `Level0WorldExplorationTest`, `Level0PuzzleSolverTest`, `Level0FieldStudyTest`, `WorldHintTest`, `Level0ObjectiveTest`, `WorldCasebookIntegrityTest`, `EntitySpawnPoolTest`, `GameStateCodecTest`, `GameCheckpointCodecTest`, `explorer-controls.test.cjs`, `level-snapshot-routing.test.cjs`. CI của commit mới nhất xem tab Checks của PR; không tự gán PASS trước khi có kết quả.
- Solver offline và 128-seed topology test chứng minh khả thi trong mô hình máy. Random explorer đo mức giải ngẫu nhiên trong phạm vi hành động được phát; đây **không phải playtest người thật**. CI build chứng minh APK được build, không chứng minh WebView chạy trơn tru trên thiết bị.

## Các phần chưa thể tuyên bố hoàn tất

- **Exit Level 0 → 0.1 khóa theo chỉ đạo người dùng.** Thiếu Canon/duyệt thì không xây điều kiện thoát hay mở Route.
- Playtest người thật, cân độ khó/độ dài gần 10 giờ, trải nghiệm combat/escape/HP cạn qua toàn bộ vòng đời, UX thực tế trên Android, live provider latency/tokens/network failures chưa có biên bản nghiệm thu.
- Hai puzzle kiểm chứng liên khu và các field study hiện tạo được gameplay độc lập nhưng chưa tương đương chiều sâu của một Level 0 mười giờ. Không tăng grind hoặc thay Entity để kéo dài thời gian.
- Pipeline mini-map cũ vẫn còn cho các node chưa chuyển sang WorldGraphRuntime; **không xóa khi vẫn còn caller**. Sau khi chuyển hết mới được dỡ bỏ. Không thêm framework/compatibility cho save cũ.

## Kiểm tra có mục tiêu

Chạy từ `android-apk/` trong môi trường đã có Android SDK/Gradle:

```sh
./gradlew :app:testDebugUnitTest --tests 'com.rabpit.backroom.core.Level0FieldStudyTest' --tests 'com.rabpit.backroom.core.Level0PuzzleSolverTest' --tests 'com.rabpit.backroom.core.WorldHintTest' --tests 'com.rabpit.backroom.core.Level0ObjectiveTest' --tests 'com.rabpit.backroom.core.WorldCasebookIntegrityTest' --tests 'com.rabpit.backroom.core.Level0CombatContinuityTest' --no-daemon
node --test tests/explorer-controls.test.cjs tests/level-snapshot-routing.test.cjs
```

Không tự chạy lại toàn suite/build mỗi commit; xem CI bắt buộc theo mốc quyết định. Không force-push/merge/release/đóng Issue.


## 2026-10-19 — Player Action nhiều bước, một đoạn lời kể

- **Quy tắc gameplay:** mỗi câu player là một yêu cầu có số hành động tường minh `totalActions` do Gemini planner xác định ở lượt phân tích đầu tiên (1–12); các lần phân tích sau phải giữ nguyên số đó. Core chỉ chấp nhận `MOVE/SEARCH/INTERACT/TALK` đã có trong `availableActions` của **đúng phòng và phiên bản scene hiện tại**. Không bổ sung hành động do AI tự suy diễn, không chạy khi thiếu thông tin, gặp Entity hoặc vượt ngân sách tối đa một lượt planner mỗi hành động đã đếm. Gemini là bộ phân tích intent, không được trực tiếp sửa Core; Gehihi chỉ kể các bước đã xác nhận.
- **Một câu nhập, một lượt hiển thị:** Core vẫn stage/commit từng bước và lưu từng pending/receipt để có thể retry, nhưng Android giữ các bản tiến độ đã commit ở phần presentation và **không phát render giữa chừng**. `PlayerActionPresentation.fold` giữ một dòng **BẠN** nguyên văn và gộp tất cả kết quả Gehihi thành **một đoạn GM không xuống dòng**. Chỉ khi hoàn thành/hủy một phần thì WebView nhận một `backroomTurn` hiển thị. Không đánh đồng số hành động với số đoạn văn.
- Mỗi trạng thái giữa chừng được lưu âm thầm vào presentation cache để giảm rủi ro mất lịch sử khi ứng dụng bị ngắt; Core save mới là nguồn xác minh gameplay. `sceneBatchActions` là marker trình bày để Core projection không nhân đôi receipt cuối, không phải trạng thái cho phép thao tác.
- Đầu vào người chơi luôn giữ nguyên trong `scene.pending.playerText` và `scene.receipt.playerText`; nhãn Core và reply GM không thay lời BẠN. Nếu planner thất bại từ đầu, không có bước nào được commit. Nếu đã commit một phần rồi gặp lỗi, giữ phần đã thực hiện và báo rõ phần chưa thực hiện, không tự replay.
- **Giới hạn nghiệm thu:** đây là điều kiện thực thi được kiểm soát bằng action count do model phân loại, không thể khẳng định model luôn phân tách tiếng Việt đúng mọi câu nhập. Cần playtest câu ghép, tham chiếu ngữ cảnh, câu không hành động và thao tác nhiều bước bị encounter xen ngang. Vẫn giữ nguyên Entity, Canon và khóa exit `0→0.1`.

## Regression combat và giới hạn provider (2026-10-19)

- `Level0CombatContinuityTest.kt` đi qua `CombatRuntime.start/resolve` thật, kiểm tra rút lui, kết thúc combat khi đánh bại Hound, rồi save/load. Mọi bước phải giữ nguyên `scene.progress`, dấu mốc, vết khắc và lịch sử điều tra; không thay xác suất Entity hoặc quy tắc loot.
- Player Action có giới hạn 12 bước; số lượt Gemini planner tối đa bằng số hành động hợp lệ đã đếm (không phát sinh lượt lập kế hoạch ngoài câu gốc). Trong một lượt, `geminiTextPolicy` giới hạn hai worker thử, ngân sách khoảng 30 giây; read/connect timeout của request được giảm theo thời gian còn lại. Đây là giới hạn **best-effort** do socket read timeout không phải deadline tuyệt đối của OS. Provider vẫn chỉ đưa đề xuất JSON; Core xác minh lại tất cả action. Không fallback sang Gehihi để lập kế hoạch.
- Kiểm thử WebView/Android thật và các lỗi mạng sống còn là nghiệm thu riêng, không suy từ test tĩnh hay CI APK xanh.


## Mixed freeform and Core actions (2026-10-19)

Player Action plans up to 12 semantic intentions as FREE (short-lived narration only) and Core MOVE/SEARCH/INTERACT/TALK (must match public offered actions). FREE props and passersby never create GameState, inventory items, NPCs, encounters, party, HP effects, persistent character memory, puzzle evidence or world travel. FREE items bearing the same names as real inventory items are STILL imaginary. Real USE/TRANSFER/DROP are authorized exclusively by the item detail UI and GameCoreFacade.processItemAction. Core actions commit factual results transactionally, then ONE Gehihi synthesis combines FREE intentions and Core events into a player turn. A synthesis failure must never re-execute committed actions; factual receipts remain available. Free-only turns are stored in scene-scoped presentation, not CharacterMemoryBags. GM narration is presentation-only and never changes authoritative GameState. Level 0 exit remains locked; Entity/spawn/combat remain unchanged.
