# Explorer mini-map · #653

## Quyết định sản phẩm đã xác nhận

## Phạm vi triển khai hiện hành

- Chỉ **Level 0** (node `0`) sử dụng Explorer mini-map trong đợt này, gồm đúng **8 mini-map** đánh số 1–8. Không sinh outline hay mini-map cho sublevel, khu phụ hoặc Level khác.
- Mỗi mini-map là một phân đoạn giải đố/nhiệm vụ với 4–12 phòng/khu vực. Hoàn thành mục tiêu và tới phòng thoát mới mở lệnh `next` từ mini-map 1–7.
- **Sau mini-map 8 không có lệnh `next`, không có `node:...`, không chuyển Level và không kích hoạt kết thúc game.** Nội dung mini-map 8, tiến trình và dữ kiện đã xác nhận được giữ nguyên để tiếp tục lưu/tải.
- `LevelRouteRegistry` vẫn chứa dữ liệu Canon của các Level/khu khác cho những hệ thống không liên quan; **không** đồng nghĩa chúng đã có Explorer mini-map.
- Tính năng NPC vãng lai ngẫu nhiên vẫn bị tắt; NPC kịch bản và cuộc gặp nhân vật Canon tiếp tục do Core kiểm soát.

## Đường chạy

`prepareScene` → Gemini scenario transport (tối đa 2 lane/request, cooldown riêng 60s)
→ nếu các lượt Gemini được phép đều lỗi/quota/schema hoặc không có key khả dụng: thử **một lần Gehihi tạo kịch bản** (output cap 10000 tokens)
→ cả hai provider đều phải parse bounded JSON, validate cùng schema v4/outline/script + proof hữu hạn → chỉ kịch bản hợp lệ mới được lưu vào Core.
Gehihi dự phòng ở đây chỉ là **biên kịch outline/mini-map**, không đổi vai trò Gehihi làm GM duy nhất. Dự phòng không dùng chung giới hạn 2048 tokens của lời kể. Nếu cả hai cùng thất bại, state giữ nguyên và UI cho thử lại.

`submitSceneAction` → kiểm tra actionId/version/room/availableActions
→ stage movement/search/claim → roll tại exact node đích → reserve encounter
→ lưu pending → Gehihi nhận public event + Canon đã lọc → strict `{reply}`
→ commit time, turn, puzzle tiến trình hiện tại, room/node, deferred NPC và receipt bằng một Core save; xóa script/bằng chứng của nhiệm vụ vừa rời trong cùng commit.

Retry dùng nguyên command/encounter/public event đã lưu. Không gọi lại Gemini trên lượt chơi thường.
Provider lỗi giữ pending; process death đọc lại pending; không tự fallback sang provider GM khác.
Core không nhận world delta từ Gehihi. UI chỉ gửi ID, không có ô nhập hành động tự do.
Giao tiếp kết hợp NPC kịch bản (0–12 NPC độc lập trong một mini-map, không spawn ngẫu nhiên; chỉ NPC thuộc phòng hiện tại được công khai) và nhân vật Canon do Core xác nhận đang ACTIVE/đã gặp, cùng thành viên Party đang hiện diện. NPC kịch bản được lưu cố định theo script với danh tính, vị trí, vai trò, mục đích và ít nhất một tương tác TALK có dữ kiện; TALK phải gắn đúng npcId cùng phòng. Core công khai NPC đúng phòng (gồm tên, thời kỳ tự khai, vai trò và ngoại hình) cùng sự kiện giao tiếp, không rò rỉ mục đích nội bộ. Gehihi giữ sắc thái lời thoại theo thời kỳ, không tự sáng tác nhân vật mới. NPC Canon có các chủ đề riêng; TALK không roll spawn, không tự cấp item hoặc gia nhập Party. Nhân vật mất tích/tách nhóm/chết không được chọn.
Combat/Poker Dice tiếp tục dùng endpoint và state hiện có. Encounter chỉ hiện Battle Start sau narration commit.

## Ngôn ngữ scenario v4

Schema thực nằm ở `MiniMapScenario.kt`; prompt tương ứng ở `ScenarioGenerator.java`.

- Đúng 8 outline entries chỉ cho nodeAnchor `0`, index/node anchor được Core khóa; dependencies chỉ trỏ mini trước trong node. `levelNumber` chỉ là Level cha phục vụ scaling, không dùng làm khóa tiến trình. Schema scenario 4, không nhận outline gộp cũ.
- 4..12 room, graph liên thông, cạnh hai chiều, tối đa 6 cạnh/room. V3 không sinh phòng bí mật: chỉ trình bày phòng kề.
- NPC list bắt buộc trong schema nhưng có thể rỗng (`npcs: []`). **0–12 NPC là giới hạn tổng của mỗi mini-map, không phải số phải sinh, không phải số xuất hiện đồng thời.** Gemini chọn số lượng theo kịch bản, phân bổ `roomId` theo vị trí; Core chỉ hiển thị NPC ở phòng hiện tại khi người chơi đến đó. Mỗi NPC có `id`, `name` (tên riêng do Gemini sáng tác), `originPeriod` (thời kỳ nhân vật tự nhận xuất thân), `role`, `purpose`, `description`, `roomId` và ít nhất một TALK gắn đúng `npcId` ở cùng phòng. SEARCH không có `npcId`. NPC có thể cung cấp dữ kiện phụ hữu ích, không bắt buộc là chốt mở goal; không cho mạo danh Canon hoặc NPC vô nghĩa. Tên riêng không được là nhãn chung (ví dụ `NPC 1`, `Người gác 2`), không trùng trong cùng mini-map. Khi có ít nhất 2 NPC, phải có ít nhất 2 giá trị `originPeriod` khác nhau. Gemini có thể sáng tác lai lịch từ nhiều thế kỷ/giai đoạn, chọn tên và cách ứng xử phù hợp ngôn ngữ, văn hóa của thời kỳ; không biến lai lịch ấy thành tuyên bố Canon về du hành thời gian. 12 vẫn chỉ là giới hạn tổng, không phải số xuất hiện cùng lúc. Không áp đặt giới hạn đồng thời mới khi chưa có yêu cầu của chủ dự án.
- 2..12 fact với domain hữu hạn 2..5 giá trị. 4..36 interaction, ID giới hạn 40 ký tự.
- Predicate: `TRUE`, `HAS(interactionId)`, `AND`, `OR`; sâu tối đa 4, 8 nhánh.
- SEARCH: clue/vật chứng xác nhận từ fact; TALK: claim với truth policy TRUTH/LIE/MISTAKEN; ANSWER: lựa chọn khớp fact mới được ghi nhận thành công.
- Goal phải cần ít nhất hai fact được suy luận. Mỗi fact hỏi đáp cần chứng cứ đúng độc lập; **không bắt buộc NPC, không bắt buộc nói dối**. Puzzle môi trường thuần SEARCH hợp lệ; nếu có NPC thì lời khai TRUTH/LIE/MISTAKEN phải nhất quán với đáp án và kiểm chứng được.
- Validator forward-chain toàn bộ action có thể mở để chứng minh goal khả đạt. Graph hai chiều + predicate monotone + sai không tiêu mất đáp án đúng bảo đảm đường hồi phục trong ngôn ngữ này.
- Chọn sai chỉ tiêu option sai; search/talk đã thực hiện không lặp để farm. MOVE backtrack vẫn hợp lệ.
- Vật chứng là record nhiệm vụ, không phải Inventory gameplay: tối đa 36 interaction/map, không cân nặng, không tiêu hủy/chuyển thành loot.
- Tối đa 48.000 ký tự, độ sâu JSON 12, field/reference/operator ngoài schema bị từ chối. Không eval/code từ generator.

Đây là ngôn ngữ puzzle hữu hạn có thể kiểm tra, không phải validator cho mọi câu đố văn bản tự do. Kết nối đồ vật, điều kiện thứ tự và đối chiếu được biểu diễn qua predicate/action; không giả định AI tự chứng minh ngữ nghĩa lời mô tả. Chất lượng/độ khó/không mâu thuẫn Canon trong văn bản public vẫn cần review mẫu sinh thực tế.

## Save, continuity và projection

Save schema **7**, checkpoint riêng phiên bản hiện hành. Không migration/adapter/fallback đọc save cũ.
Chỉ script Mini-map đang chơi và outline của node hiện tại nằm tạm trong `world` để lưu/tải nhiệm vụ dở. Bằng chứng (`records`), hành động đã thử và lời khai chỉ có hiệu lực trong Mini-map hiện tại. Trên bước chuyển mini-map 1–7, Core xóa script cũ cùng `accepted/attempted/records`; mini-map 8 không có lệnh chuyển, nên giữ nguyên script, outline và tiến trình đã giải. Chỉ giữ index các mini-map đã hoàn thành trong Level 0; không có `history`, `arrival`, `scene.node.*` hay cache tiến trình node đã rời.
Generator chỉ nhận outline và index hoàn thành; không truyền `previousOutcome`, `dependencyOutcomes` hoặc vật chứng từ nhiệm vụ cũ. Chưa thay thế tầng xác minh puzzle hữu hạn bằng AI tự quyết định đáp án; Core hiện vẫn là nơi kiểm tra goal/ANSWER. Dữ liệu tạm được duy trì khi nhiệm vụ chưa kết thúc để lưu/tải và retry.
UI/Gehihi không nhận fact table, truth policy, answer key, graph đầy đủ hoặc mini tương lai. Các đoạn log câu đố gắn `sceneMap` và bị lọc khỏi presentation khi sang Mini-map mới; không xóa phần kể Canon/global.
NPC claim luôn có nhãn chưa xác minh; quan sát/vật chứng mới là evidence. Receipt append log chống lặp action.
Nhật ký khám phá (mô tả phòng, mục tiêu và manh mối/lời khai) nằm riêng trong Game Menu. Thanh hành động bên ngoài chỉ giữ MOVE/SEARCH/TALK và retry khi cần.
Snapshot chỉ thay ảnh, không điều khiển unlock. Hidden Exit Streak không còn trong luồng Explorer production.
Các API Core cũ còn test độc lập không được UI mới sử dụng; `beginAction`/`processValidatedCandidate` từ chối run có scene.

## Xác minh và giới hạn

- Test JVM mục tiêu: schema/graph/claim/proof; privacy; typed spawn gate; pending/retry/replay; đồng hồ một lần; restart; Entity/Battle Start; di chuyển nội bộ phòng; xóa script/bằng chứng/log sau khi hoàn thành; checkpoint; tuyến Level 0 gồm 8 mini-map, chặn chuyển mini-map 8 sang node khác và không kích hoạt kết thúc game.
- Regression mục tiêu: spawn baseline, Combat/Party, codec/checkpoint, route registry, provider contract.
- Test JS: popup không submit, versioned command, double tap, generation retry, pending retry, combat và textContent; verifier payload APK đổi sang Explorer controls.
- Compile Kotlin 2.3.0 và Java với Android API 35 bằng compiler độc lập; không đồng nghĩa Gradle assemble/APK đã thành công.
- Chưa gọi Gemini/Gehihi thực để kiểm tra khả năng sinh JSON dài qua endpoint Gehihi, chưa đo latency/token thực, chưa chạy APK/emulator. Test Core xác minh chọn provider và validator, không đảm bảo model Gehihi sẽ thực sự hoàn thành mini-map. Cần nghiệm thu mẫu puzzle sinh thực tế và nhiều đường đi trên Android trước phát hành.
- Chưa có browser smoke: tải Chromium của môi trường thất bại. Test JS chạy bằng Node VM, không thay thế render/WebView thực.
- Không merge/release/đóng issue trong tác vụ này. PR giữ draft cho đến khi các cổng Android/CI và nghiệm thu nội dung hoàn tất.
