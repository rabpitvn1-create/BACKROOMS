# Explorer mini-map · #653

## Quyết định sản phẩm đã xác nhận trong phiên triển khai

- Quyết định cập nhật sau nghiệm thu APK: **mỗi exact Canon node có 10 mini-map riêng**, thay mapping gộp 10 mini/parent trước đó.
- Level 0 mini 1..10 đều ở node `0`; hoàn tất mini 10 và đến đúng exit room mới mở **duy nhất `0.1`**. Không thể nhảy thẳng sang Dullness/Epsilon/LS-2.
- **Tất cả** Canon node đều có đúng 10 mini-map độc lập: Level chính, mọi sublevel, mọi khu phụ (AREA) và khu đặc biệt (SPECIAL), bao gồm Dullness/Epsilon/LS-2, Manila Room, The Torment và Red Rooms. Mỗi mini-map là một phân đoạn cần giải khóa, bên trong có 4–12 khu vực/phòng phục vụ giải đố hoặc nhiệm vụ. Mọi cạnh chuyển node đều phải hoàn thành mini 10. Cả 25 Canon node tạo thành **một tuyến duy nhất một chiều**, không quay lại được Mini-map hay node đã hoàn thành.
- Chuỗi bắt buộc đã chốt: `0 → 0.1 → 0.2 → 0.5 → 0.7 → Dullness → Epsilon → LS-2 → Manila Room → The Torment → Red Rooms → 1`; không có nhánh tắt, không đường về.
- Level 1–6 có tuyến bắt buộc: `1 → 1.2 → 1.5 → Trader’s Vault → Base Alpha → 2 → Office Space EL3A → 3 → 4 → Office Market → 5 → 5.1 → 6 → 6.1`; không có nhánh tắt `3 → 6` và không có bất kỳ cạnh ngược nào.
- Level `6` mini 10 chỉ dẫn tới `6.1`; chỉ khi hoàn thành mini 10 của `6.1` mới `awaitingEnding`. Không có Level 7.
- Cơ chế **NPC tạm thời ngẫu nhiên (ambient NPC)** đã lưu trữ/ngừng chạy ở cả lượt chữ cũ và Explorer: không roll, không gọi Gemini ambient, không khôi phục NPC vãng lai từ cache. Các cuộc gặp nhân vật Canon do Core kiểm soát vẫn độc lập.

## Đường chạy

`prepareScene` → Gemini scenario transport (tối đa 2 lane/request, cooldown riêng 60s)
→ parse bounded JSON → validate outline/script + proof hữu hạn → lưu cùng Core.

`submitSceneAction` → kiểm tra actionId/version/room/availableActions
→ stage movement/search/claim → roll tại exact node đích → reserve encounter
→ lưu pending → Gehihi nhận public event + Canon đã lọc → strict `{reply}`
→ commit time, turn, puzzle tiến trình hiện tại, room/node, deferred NPC và receipt bằng một Core save; xóa script/bằng chứng của nhiệm vụ vừa rời trong cùng commit.

Retry dùng nguyên command/encounter/public event đã lưu. Không gọi lại Gemini trên lượt chơi thường.
Provider lỗi giữ pending; process death đọc lại pending; không tự fallback sang provider GM khác.
Core không nhận world delta từ Gehihi. UI chỉ gửi ID, không có ô nhập hành động tự do.
Giao tiếp kết hợp NPC kịch bản (0–12 NPC độc lập trong một Mini-map, không spawn ngẫu nhiên) và nhân vật Canon do Core xác nhận đang ACTIVE/đã gặp, cùng thành viên Party đang hiện diện. NPC kịch bản được lưu cố định theo script với danh tính, vị trí, vai trò, mục đích và ít nhất một tương tác TALK có dữ kiện; TALK phải gắn đúng npcId cùng phòng. Core công khai NPC đúng phòng và sự kiện giao tiếp, không rò rỉ mục đích nội bộ. NPC Canon có các chủ đề riêng; TALK không roll spawn, không tự cấp item hoặc gia nhập Party. Nhân vật mất tích/tách nhóm/chết không được chọn.
Combat/Poker Dice tiếp tục dùng endpoint và state hiện có. Encounter chỉ hiện Battle Start sau narration commit.

## Ngôn ngữ scenario v3

Schema thực nằm ở `MiniMapScenario.kt`; prompt tương ứng ở `ScenarioGenerator.java`.

- Đúng 10 outline entries cho cùng nodeAnchor, index/node anchor được Core khóa; dependencies chỉ trỏ mini trước trong node. `levelNumber` chỉ là Level cha phục vụ scaling, không dùng làm khóa tiến trình. Schema scenario 3, không nhận outline gộp cũ.
- 4..12 room, graph liên thông, cạnh hai chiều, tối đa 6 cạnh/room. V3 không sinh phòng bí mật: chỉ trình bày phòng kề.
- NPC list bắt buộc trong schema nhưng có thể rỗng (`npcs: []`). Nếu có NPC thì được phép nhiều người (tối đa 12), mỗi người có `id`, `name`, `role`, `purpose`, `description`, `roomId` và ít nhất một TALK gắn `npcId` ở đúng phòng. SEARCH không có `npcId`. Không cho mạo danh Canon hoặc NPC chỉ để trang trí.
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
Chỉ script Mini-map đang chơi và outline của node hiện tại nằm tạm trong `world` để lưu/tải nhiệm vụ dở. Bằng chứng (`records`), hành động đã thử và lời khai chỉ có hiệu lực trong Mini-map hiện tại. Trên bước chuyển Mini-map, Core xóa script cũ cùng `accepted/attempted/records`; khi rời node xóa cả outline. Chỉ giữ index các Mini-map đã hoàn thành trong node hiện tại để mở khóa một chiều; không có `history`, `arrival`, `scene.node.*` hay cache tiến trình node đã rời.
Generator chỉ nhận outline và index hoàn thành; không truyền `previousOutcome`, `dependencyOutcomes` hoặc vật chứng từ nhiệm vụ cũ. Chưa thay thế tầng xác minh puzzle hữu hạn bằng AI tự quyết định đáp án; Core hiện vẫn là nơi kiểm tra goal/ANSWER. Dữ liệu tạm được duy trì khi nhiệm vụ chưa kết thúc để lưu/tải và retry.
UI/Gehihi không nhận fact table, truth policy, answer key, graph đầy đủ hoặc mini tương lai. Các đoạn log câu đố gắn `sceneMap` và bị lọc khỏi presentation khi sang Mini-map mới; không xóa phần kể Canon/global.
NPC claim luôn có nhãn chưa xác minh; quan sát/vật chứng mới là evidence. Receipt append log chống lặp action.
Nhật ký khám phá (mô tả phòng, mục tiêu và manh mối/lời khai) nằm riêng trong Game Menu. Thanh hành động bên ngoài chỉ giữ MOVE/SEARCH/TALK và retry khi cần.
Snapshot chỉ thay ảnh, không điều khiển unlock. Hidden Exit Streak không còn trong luồng Explorer production.
Các API Core cũ còn test độc lập không được UI mới sử dụng; `beginAction`/`processValidatedCandidate` từ chối run có scene.

## Xác minh và giới hạn

- Test JVM mục tiêu: schema/graph/claim/proof; privacy; typed spawn gate; pending/retry/replay; đồng hồ một lần; restart; Entity/Battle Start; di chuyển nội bộ phòng; xóa script/bằng chứng/log sau khi hoàn thành; checkpoint; tuyến một chiều đủ 25 node, chỉ kết thúc sau 6.1.
- Regression mục tiêu: spawn baseline, Combat/Party, codec/checkpoint, route registry, provider contract.
- Test JS: popup không submit, versioned command, double tap, generation retry, pending retry, combat và textContent; verifier payload APK đổi sang Explorer controls.
- Compile Kotlin 2.3.0 và Java với Android API 35 bằng compiler độc lập; không đồng nghĩa Gradle assemble/APK đã thành công.
- Chưa gọi Gemini/Gehihi thực, chưa đo latency/token thực, chưa chạy APK/emulator. Cần nghiệm thu mẫu puzzle sinh thực tế và nhiều đường đi trên Android trước phát hành.
- Chưa có browser smoke: tải Chromium của môi trường thất bại. Test JS chạy bằng Node VM, không thay thế render/WebView thực.
- Không merge/release/đóng issue trong tác vụ này. PR giữ draft cho đến khi các cổng Android/CI và nghiệm thu nội dung hoàn tất.
