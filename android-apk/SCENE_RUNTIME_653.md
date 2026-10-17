# Explorer mini-map · #653

## Quyết định sản phẩm đã xác nhận trong phiên triển khai

- Quyết định cập nhật sau nghiệm thu APK: **mỗi exact Canon node có 10 mini-map riêng**, thay mapping gộp 10 mini/parent trước đó.
- Level 0 mini 1..10 đều ở node `0`; hoàn tất mini 10 và đến đúng exit room mới mở `0.1`/Dullness/Epsilon/LS-2 theo registry.
- Mỗi sublevel và khu phụ cũng có outline, script, tiến trình 1..10 riêng. Mọi cạnh chuyển node, kể cả lối quay lại, đều dùng gate hoàn thành node. Quay lại node đã chơi thì khôi phục tiến trình/script đã đóng băng, không khởi tạo lại.
- Chuỗi bắt buộc `0 → 0.1 → 0.2 → 0.5 → 0.7 → Manila → Torment → Red Rooms → 1` không thay cạnh hoặc bỏ node.
- Level 3: cả 10 mini neo node `3`; người chơi chọn Exit sang `4` hoặc `6` sau hoàn thành.
- Level 6 mini 10 hoàn thành: chờ ending, không thêm Level 7 hoặc tuyên bố thoát Backrooms.
- Ambient NPC cũng chỉ roll sau MOVE/SEARCH hợp lệ, giữ xác suất và ưu tiên hiện hữu.

## Đường chạy

`prepareScene` → Gemini scenario transport (tối đa 2 lane/request, cooldown riêng 60s)
→ parse bounded JSON → validate outline/script + proof hữu hạn → lưu cùng Core.

`submitSceneAction` → kiểm tra actionId/version/room/availableActions
→ stage movement/search/claim → roll tại exact node đích → reserve encounter
→ lưu pending → Gehihi nhận public event + Canon đã lọc → strict `{reply}`
→ commit time, turn, quest facts, room/node, deferred NPC và receipt bằng một Core save.

Retry dùng nguyên command/encounter/public event đã lưu. Không gọi lại Gemini trên lượt chơi thường.
Provider lỗi giữ pending; process death đọc lại pending; không tự fallback sang provider GM khác.
Core không nhận world delta từ Gehihi. UI chỉ gửi ID, không có ô nhập hành động tự do.
Giao tiếp kết hợp NPC kịch bản với nhân vật Canon do Core xác nhận đang ACTIVE/đã gặp và thành viên Party đang hiện diện. Mỗi nhân vật có các chủ đề có cấu trúc (giới thiệu, tình hình, manh mối đã biết); không dựa vào NPC sinh bởi Gemini. TALK không roll spawn, không tự cấp clue/item hoặc gia nhập Party. Nhân vật mất tích/tách nhóm/chết không được chọn.
Combat/Poker Dice tiếp tục dùng endpoint và state hiện có. Encounter chỉ hiện Battle Start sau narration commit.

## Ngôn ngữ scenario v2

Schema thực nằm ở `MiniMapScenario.kt`; prompt tương ứng ở `ScenarioGenerator.java`.

- Đúng 10 outline entries cho cùng nodeAnchor, index/node anchor được Core khóa; dependencies chỉ trỏ mini trước trong node. `levelNumber` chỉ là Level cha phục vụ scaling, không dùng làm khóa tiến trình. Schema scenario 2, không nhận outline gộp cũ.
- 3..12 room, graph liên thông, cạnh hai chiều, tối đa 6 cạnh/room. V2 không sinh phòng bí mật: chỉ trình bày phòng kề.
- 2..12 fact với domain hữu hạn 2..5 giá trị. 4..36 interaction, ID giới hạn 40 ký tự.
- Predicate: `TRUE`, `HAS(interactionId)`, `AND`, `OR`; sâu tối đa 4, 8 nhánh.
- SEARCH: clue/vật chứng xác nhận từ fact; TALK: claim với truth policy TRUTH/LIE/MISTAKEN; ANSWER: lựa chọn khớp fact mới được ghi nhận thành công.
- Goal phải cần ít nhất hai fact được suy luận. Mỗi fact hỏi đáp cần chứng cứ đúng độc lập; có ít nhất một claim sai kiểm chứng được.
- Validator forward-chain toàn bộ action có thể mở để chứng minh goal khả đạt. Graph hai chiều + predicate monotone + sai không tiêu mất đáp án đúng bảo đảm đường hồi phục trong ngôn ngữ này.
- Chọn sai chỉ tiêu option sai; search/talk đã thực hiện không lặp để farm. MOVE backtrack vẫn hợp lệ.
- Vật chứng là record nhiệm vụ, không phải Inventory gameplay: tối đa 36 interaction/map, không cân nặng, không tiêu hủy/chuyển thành loot.
- Tối đa 48.000 ký tự, độ sâu JSON 12, field/reference/operator ngoài schema bị từ chối. Không eval/code từ generator.

Đây là ngôn ngữ puzzle hữu hạn có thể kiểm tra, không phải validator cho mọi câu đố văn bản tự do. Kết nối đồ vật, điều kiện thứ tự và đối chiếu được biểu diễn qua predicate/action; không giả định AI tự chứng minh ngữ nghĩa lời mô tả. Chất lượng/độ khó/không mâu thuẫn Canon trong văn bản public vẫn cần review mẫu sinh thực tế.

## Save, continuity và projection

Save schema **7**, checkpoint riêng phiên bản hiện hành. Không migration/adapter/fallback đọc save cũ.
Script/outline immutable theo run + exact node/index, lưu trong Core `world`; room/progress, observations, completed maps, history, pending và receipt cùng codec/checkpoint với gameplay.
History giữ observations đã commit qua các mini; generator chỉ nhận outcome của dependency được chọn (tối đa 6 observation/dependency) và mini trước, không full save.
UI/Gehihi không nhận fact table, truth policy, answer key, graph đầy đủ hoặc mini tương lai.
NPC claim luôn có nhãn chưa xác minh; quan sát/vật chứng mới là evidence. Receipt append log chống lặp action.
Nhật ký khám phá (mô tả phòng, mục tiêu và manh mối/lời khai) nằm riêng trong Game Menu. Thanh hành động bên ngoài chỉ giữ MOVE/SEARCH/TALK và retry khi cần.
Snapshot chỉ thay ảnh, không điều khiển unlock. Hidden Exit Streak không còn trong luồng Explorer production.
Các API Core cũ còn test độc lập không được UI mới sử dụng; `beginAction`/`processValidatedCandidate` từ chối run có scene.

## Xác minh và giới hạn

- Test JVM mục tiêu: schema/graph/claim/proof; privacy; typed spawn gate; pending/retry/replay; đồng hồ một lần; restart; Entity/Battle Start; backtrack/sai/quest evidence; immutable script; checkpoint; nhánh Level 3, Level 6 và detour.
- Regression mục tiêu: spawn baseline, Combat/Party, codec/checkpoint, route registry, provider contract.
- Test JS: popup không submit, versioned command, double tap, generation retry, pending retry, combat và textContent; verifier payload APK đổi sang Explorer controls.
- Compile Kotlin 2.3.0 và Java với Android API 35 bằng compiler độc lập; không đồng nghĩa Gradle assemble/APK đã thành công.
- Chưa gọi Gemini/Gehihi thực, chưa đo latency/token thực, chưa chạy APK/emulator. Cần nghiệm thu mẫu puzzle sinh thực tế và nhiều đường đi trên Android trước phát hành.
- Chưa có browser smoke: tải Chromium của môi trường thất bại. Test JS chạy bằng Node VM, không thay thế render/WebView thực.
- Không merge/release/đóng issue trong tác vụ này. PR giữ draft cho đến khi các cổng Android/CI và nghiệm thu nội dung hoàn tất.
