<!-- Bản trích xuất văn bản từ Google Doc gốc trên Drive (Novel/Asset/CANON_REGISTRY_R01 - Characters Levels Entities) — snapshot 2026-10-24 qua Google Docs connector. Chỉ phần chữ; định dạng/bảng giữ ở mức văn bản thuần. -->

CANON REGISTRY R01 · BACKROOMS THE GAME

Mục tiêu: hệ thống ba tầng Canon có mã truy xuất ổn định, dùng hai nhóm nguồn biên soạn duy nhất: (1) Google Drive /Novel/Asset, (2) Internet công khai có liên kết truy nguyên.

Ngày lập: 2026-10-12. Trạng thái: INDEX ĐÃ TỔ CHỨC, CANON CHƯA ĐẦY ĐỦ. Không thay đổi Game Core hoặc GM trong tài liệu này.



1. PHẠM VI VÀ THẨM QUYỀN

• Ba miền duy nhất: CHARACTER (nhân vật), LEVEL (môi trường và cấu trúc tầng), ENTITY (thực thể).

• Google Drive /Novel/Asset chứa thiết lập của tác phẩm, có ưu tiên cao hơn tư liệu Internet khi có khác biệt được tác giả chủ định.

• Internet là nguồn tham khảo Backrooms bên ngoài, không có quyền tự bổ sung tính cách, lịch sử riêng, quan hệ hay bí mật tác phẩm. Ghi URL khi trích nguồn; thiếu nguồn thì OPEN/UNKNOWN.

• Game State lưu trạng thái đã xảy ra (HP, vị trí, party, ký ức, kết quả milestone); KHÔNG sử dụng hồ sơ baseline để reset trạng thái động.

• Source tài liệu không phải cơ chế runtime thứ ba: Game Core vẫn quyết định gameplay. Mã dưới đây là contract ánh xạ để kết nối với KnowledgeContextEngine hiện hữu.

• Không tự biến WRITER-SECRET thành tri thức nhân vật; PO​V/BELIEF không đồng nghĩa FACT. “Được nhắc tên” không đồng nghĩa “đang ở trong cảnh”.

• Chỉ biên dịch các thẻ canon ngắn vào APK. Không gọi Google Drive/Internet ở mỗi lượt GM; các URL phục vụ cập nhật nguồn khi biên soạn.



2. CẤU TRÚC /NOVEL/ASSET

/Novel/Asset/

  01_Character/ : CAO_MINH_CODEX.md, LUC_TRAM_CODEX.md, LUCIA_LUC_CODEX.md, TRAC_LAM_CODEX.md

  02_Level/     : BACKROOMS_WORLD.md

  03_Entity/    : Entity.md

  [root]        : tài liệu hỗ trợ nền truyện, lịch sử, linh khí, bản tạm; không tự nâng thành canon riêng của một chủ thể.

Chuyển vị trí không đổi file ID hoặc URL của các tài liệu gốc. Không sửa nội dung tài liệu gốc ở đợt này.



3. MÃ TRUY XUẤT TẦNG CHARACTER

Mã Canon             Runtime key       Tài liệu gốc / tình trạng

CHAR.CAO_MINH        cao_minh          CAO_MINH_CODEX.md · CURRENT (R18 addendum)

CHAR.LUC_TRAM        luc_tram          LUC_TRAM_CODEX.md · CURRENT (R06 addendum)

CHAR.LUCIA           lucia             LUCIA_LUC_CODEX.md · DRAFT PARTIAL; UNKNOWN giữ nguyên

CHAR.TRAC_LAM        chưa xác nhận     TRAC_LAM_CODEX.md · có trong Asset; chưa kết luận đã đăng ký Core

CHAR.IRIS            iris              CHƯA CÓ hồ sơ chuyên biệt trong /Asset/01_Character

CHAR.SYVIAL          syvial            CHƯA CÓ hồ sơ chuyên biệt trong /Asset/01_Character

CHAR.AN_NHIEN        an_nhien          CHƯA CÓ hồ sơ chuyên biệt trong /Asset/01_Character

Quy tắc ánh xạ: CHAR.<ID> là mã tra cứu khái niệm; không tự đổi các Knowledge Record ID hiện hành (ví dụ CHAR.CAO_MINH.RUNTIME_CORE) trước khi triển khai code. Lucia và Lục Trầm tuyệt đối không được hợp nhất.

Các nhãn truy xuất nội bộ: SELF-CANON / CROSS-CANON / POV-BELIEF / WRITER-SECRET / DYNAMIC / OPEN-UNKNOWN.



4. MÃ TRUY XUẤT TẦNG LEVEL

Mã định danh: LEVEL.00 … LEVEL.10 (Level chính); LEVEL.NODE.<exact-route-id> cho node/sublevel chính xác.

Nguồn Drive: BACKROOMS_WORLD.md (phần 2A cho Level 0–10, phần 2B cho sublevel Level 0, phần 6-7 cho quy tắc và ưu tiên nguồn).

Tên Level trong nguồn: 00 Threshold/The Lobby; 01 Habitable Zone; 02 Abandoned Utility Halls; 03 Electrical Station; 04 Abandoned Office; 05 Terror Hotel; 06 Lights Out; 07 Thalassophobia; 08 Cave Systems; 09 The Suburbs; 10 Bumper Crop.

Phạm vi gameplay đã kiểm tra: Core hiện có knowledge record LEVEL.00 … LEVEL.06; các Level 07–10 trong tài liệu Drive KHÔNG mặc nhiên là gameplay đã hỗ trợ.

ID node phải là key chính xác từ LevelRouteRegistry, ví dụ 0, 0.1, 0.2, 0.5, 0.7, manila_room, the_torment, red_rooms; không dùng mỗi số parent Level để phân biệt các khu vực.

Các sublevel chỉ được mô tả trong Drive nhưng chưa xác minh route runtime phải mang trạng thái REFERENCE_ONLY.

Nguồn Internet đối chiếu: https://backrooms-wiki.wikidot.com/normal-levels-i và https://backrooms-wiki.wikidot.com/level-0

Nếu tên hoặc quy luật khác nhau (ví dụ Level 0.7), giữ bản tác phẩm trong Drive; gắn nhãn SOURCE_CONFLICT khi nghiên cứu, không tự sửa tên theo Internet.



5. MÃ TRUY XUẤT TẦNG ENTITY

Mã định danh: ENTITY.<UPPER_CASE_ID>, runtime key lấy nguyên văn từ EntityEncounterPool (không suy từ tên hiển thị).

Pool 22 key đã có trong game:

hound, clump, duller, deathmoth, hostile_faceling, false_puddle, paintings, smiler, skin-stealer, predatory_window, biological_pipeline, wretch, cable_mimic, hotel_corpse_lure, blackroot_sentinel, sinew_strider, hollow_grasper, the_beast_of_level_5, slenderman, diep_minh, jeff_the_killer, jane_the_killer.

Ví dụ mapping: ENTITY.HOUND -> hound; ENTITY.SKIN_STEALER -> skin-stealer; ENTITY.DIEP_MINH -> diep_minh.

Nguồn Drive /03_Entity/Entity.md đã có mô tả ngoại hình cho 22/22 Entity của Encounter Pool (cập nhật 2026-10-12); vẫn chưa đủ hồ sơ hành vi, môi trường hoặc cơ chế chiến đấu.

Ba Lifeform blackroot_sentinel, sinew_strider, hollow_grasper đã được bổ sung mô tả riêng trong Entity.md từ ba ảnh the_lifeform_bacteria_01/02/03.webp tại /Novel/ENTITY. Trạng thái: VISUAL_CANON_PRESENT.

Entity.md có thêm ASYNC Rifleman, CopX, Tâm Ma Cao Minh; không tự đưa các tên đó vào pool 22 runtime.

Nguồn Internet tiêu biểu: Hound https://backrooms-wiki.wikidot.com/entity-8 ; danh mục Entity https://backrooms-wiki.wikidot.com/entities

Trường record chuẩn: id, runtimeKey, names, visual, behavior, restrictions, sourceUrl, authority, knowledgeVisibility, status. Trường chưa xác minh để null/UNKNOWN; không dùng mô tả hình ảnh để suy năng lực.



6. TẦNG TRUY XUẤT CỦA GM (KHÔNG PHẢI THÊM TẦNG CANON)

R0 · RESOLVE: từ Game State + Milestone lấy exact level node, nhân vật thực sự liên quan, entity được kích hoạt hợp lệ.

R1 · QUICK CARD: cấp thẻ ngắn về danh tính, đặc trưng hành vi, môi trường và entity hiện diện.

R2 · CONTEXT DETAIL: chỉ lấy quy tắc/hồ sơ sâu khi hành động và tình huống đòi hỏi, trong giới hạn ngân sách KnowledgeContextEngine.

R3 · AUTHOR ONLY: dữ kiện hậu trường để bảo vệ tính nhất quán, không tự phơi ra trong lời kể hoặc kiến thức nhân vật.

Ví dụ truy xuất: turn=3, actorIds=[cao_minh,lucia], node=0, milestone=hound_encounter -> CHAR.CAO_MINH + CHAR.LUCIA + LEVEL.00 + ENTITY.HOUND; không tải LUC_TRAM_CODEX.md chỉ vì nhân vật được nhắc tên trong lịch sử.



7. DỮ LIỆU NGẮN CHUẨN CHO MỖI BẢN GHI

id | category | runtimeKey | name | sourceFileId/sourceUrl | scope | authority | visibility | status | conciseFacts | references

authority: DRIVE_AUTHORED, WEB_REFERENCE hoặc VERIFIED_GAME_ID (chỉ là ID ánh xạ, không nguồn viết lore).

visibility: GM_SAFE, POV_LIMITED, WRITER_SECRET.

status: CURRENT, DRAFT, REFERENCE_ONLY, SOURCE_CONFLICT, OPEN_UNKNOWN.

Nếu trùng ID hoặc xung đột, dừng biên dịch bản ghi đó và đưa vào danh sách cần xác nhận. Không ưu tiên ngày sửa file thay cho trạng thái CURRENT/OVERRIDE cụ thể.



8. QUY TẮC THỰC THI VÀ ĐƯỜNG BIÊN GAME

• Bản gốc Canon giữ trên Drive; Internet là tài liệu tham khảo có URL; bản rút gọn đóng gói offline trong APK.

• Milestone chỉ cần ID Character/Level/Entity; GM không phải đọc trọn hồ sơ hay trang web.

• Nếu encounter cuối Milestone áp dụng HP×9, EXP×9 thì modifier thuộc trạng thái Combat Instance, KHÔNG ghi đè BASE ENTITY CANON.

• Trạng thái SaveStats, cảm xúc đang tiến triển, Event đã xảy ra thuộc Game State/continuity, không sửa Codex baseline.

• Giữ nguyên hợp đồng GM hiện tại; việc đổi retrieval trong code cần triển khai và kiểm tra riêng.



9. ĐIỂM THIẾU / CÔNG VIỆC CẦN XÁC NHẬN

P0: Chưa có hồ sơ Character chuyên biệt cho Iris, Syvial, An Nhiên trong /Novel/Asset/01_Character.

P0: Entity.md đã đủ mô tả thị giác cho 22/22 Entity; còn thiếu hồ sơ hành vi và nhiều trường Entity theo runtime (bao gồm ba Lifeform).

P1: Level 0–10 có World baseline nhưng chưa có thẻ runtime ngắn theo từng node; 07–10 chưa có Knowledge Record tương ứng trong APK đang kiểm tra.

P1: Tên một số sublevel trong Drive có thể khác Internet; xác nhận nguồn AUTHOR OVERRIDE, không tự hợp nhất.

P1: Mã tra cứu trong registry chưa được triển khai vào APK; KnowledgeContextEngine đang dùng record ID cũ.

P2: Các tài liệu hỗ trợ ở Asset root cần được gán cross-reference theo chủ thể, chưa di chuyển để tránh phá ngữ cảnh.

P2: Có bản __TEMP_LUC_CANON_SYNC_20261011.md; không xóa hoặc dùng làm bản CURRENT khi đã có LUC_TRAM_CODEX.md.



10. BƯỚC KẾ TIẾP (CHƯA TRIỂN KHAI)

Hoàn thiện dữ liệu thiếu của 3 miền và biên dịch thành các runtime card offline tương thích KnowledgeContextEngine (không gọi Internet hoặc Drive mỗi turn). Kiểm thử resolver chọn đúng Character/Level/Entity cho scene, guard bí mật, không phá GM.





11. BƯỚC 2/10 · R02 · TIẾN ĐỘ CHUẨN HÓA OFFLINE CANON (2026-10-12)

Phạm vi đã triển khai trên nhánh GitHub canon-step2-drive-authored-cards-20261012 (chưa merge vào main):

- 41 thẻ Authored / Visual mới trong knowledge_db.json: 22 ENTITY.<KEY>.VISUAL từ Entity.md; 11 LEVEL.<NUMBER>.AUTHORED từ BACKROOMS_WORLD.md (07–10 là reference-only, chưa mở gameplay); 4 LEVEL.NODE.<exact-id> cho 0.5, epsilon, dullness, red_rooms; 4 CHARACTER.AUTHORED cho Cao Minh, Lục Trầm, Lucia, Trác Lâm.

- 6 bản ghi Syvial cũ đã được đổi nguồn tham chiếu sang bản sao Codex có xác minh trong /Novel/Asset/01_Character/Syvial_Codex.docx, ID 1MKOdp9XS7QIWvUwPIWayEw3vGqrTFYSJ; bản trong /Novel/Characters không bị thay đổi.

- KnowledgeContextEngine được cập nhật để truy xuất Character theo hiện diện trong Party, Level theo parent number và exact node ID, Entity theo khóa Combat active hoặc entityEncounterKey đã xác nhận. Không nạp toàn bộ Codex mỗi turn.

- Đã bổ sung regression test kiểm kê mã, nguồn Drive và các điểm truy xuất. Test mới chưa có kết quả thực thi cho đến khi CI chạy.

Điểm chưa khép kín (không được tự bịa Canon):

- 42 bản ghi legacy vẫn tham chiếu các nguồn cũ của repository, chưa kiểm chứng/chuyển sang đúng hai nguồn được duyệt. Giữ nguyên để bảo vệ game hiện tại; không nói đã đạt single-source hoàn toàn.

- Không tìm thấy Character Codex tác giả cho Iris và An Nhiên trong /Novel/Asset; dữ kiện tiểu sử, năng lực riêng chưa được tự bổ sung. Hồ sơ Syvial đã có bản sao trong Asset.

- Entity.md đã đủ ngoại hình 22/22 nhưng chưa đủ hồ sơ hành vi cho mọi loài. Tư liệu Internet là nguồn tham khảo, không tự đặt nó ngang hard lock tác phẩm hoặc biến đặc điểm chưa được xác minh thành gameplay.

- Sublevel 0.1 (Drive: Deep Emptiness, runtime: Zenith Station) và 0.7 (Drive: Claustrophobia, runtime: Reminiscence District) bất đồng. Không tạo mapping Canon giả hoặc thay tên Game Core nếu tác giả chưa xác nhận.

- Chưa tích hợp thay đổi vào main/APK; chỉ có nhánh triển khai và tài liệu Drive.

Tiêu chí trước khi tuyên bố bước 2 hoàn tất: tác giả xác nhận các xung đột và Codex còn thiếu (hoặc chấp thuận UNKNOWN chính thức); truy nguyên 42 record legacy mà không làm mất gameplay; CI và kiểm tra truy xuất mục tiêu xanh.





12. QUYẾT ĐỊNH CHỐT BƯỚC 2 · DEFER MISSING CANON (2026-10-12)

Theo chỉ đạo tác giả: các hồ sơ Canon chưa tồn tại hoặc không có nguồn xác thực được phép bổ sung ở giai đoạn sau, KHÔNG ngăn hoàn thiện runtime retrieval hiện có. Không sáng tác tiểu sử, hành vi hoặc route để lấp chỗ trống. Những bản ghi legacy được giữ nguyên để bảo vệ gameplay, ghi nhận là khoản nợ đối chiếu nguồn, không tuyên bố đạt chuẩn hai nguồn tuyệt đối.

Đã cập nhật PR #590 thêm truy xuất Entity theo rolls.selectedEntityKey khi roll thành công (trước khi Combat State được tạo), truy xuất Lucia khi luciaEncounter thành công; vẫn ưu tiên Entity Combat đang ACTIVE trước các key khác. Bổ sung regression check cho hai luồng này.

Tiêu chí chốt công việc Bước 2 (giới hạn theo nguồn hiện có): 22/22 Entity visual, 11 Level authored, 4 exact node, 4 Character authored, 6 Syvial legacy cards đã có provenance Asset; mã truy xuất theo state; CI xanh. Các hạng mục hoãn: Iris, An Nhiên, hành vi Entity chưa có nguồn, 42 legacy provenance, xung đột Level 0.1/0.7, dữ kiện Internet chưa được kiểm chứng. Chưa merge nếu không có yêu cầu rõ.


