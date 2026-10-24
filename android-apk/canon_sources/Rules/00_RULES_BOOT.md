# 00_RULES_BOOT.md — ĐIỂM KHỞI ĐỘNG CỦA BỘ QUY TẮC

**Mã tra cứu:** `RULES.BOOT`  
**Trạng thái:** ACTIVE / AUTHORITATIVE ROUTER  
**Phạm vi:** Viết, viết tiếp, viết lại, mở rộng, biên tập, kiểm định văn xuôi hư cấu và định tuyến Text Game.  
**Bản sửa router:** `HUMAN-CONVERSATION R04 / TEXT-GAME ROUTER R01`

---

## 1. MỤC ĐÍCH

Tệp này điều phối các tài liệu trong `00_RULES`. Nó chỉ quy định **cách truy xuất, thứ tự thẩm quyền, tuyến đọc và quy trình áp dụng**, không chứa dữ kiện riêng của bất kỳ tác phẩm nào.

Nguồn nội dung của tác phẩm quyết định sự thật nội thế giới. Bộ `00_RULES` quyết định cách biến sự thật đó thành văn xuôi.

Đối với hội thoại, boot này **không tự tạo một Conversation Engine riêng**. Toàn bộ nguyên tắc sinh, hiện thực hóa và audit hội thoại phải lấy từ `CONVERSATION.MD` — `RULES.DIALOGUE`.

### 1.1. ROUTER RIÊNG CHO TEXT GAME

Khi người dùng yêu cầu **bắt đầu, chơi, tiếp tục hoặc resume Text Game**, không áp dụng tuyến đọc toàn chương/toàn cảnh như một lượt viết tiểu thuyết thông thường.

Phải route sang `05_TextGame/RULES/TEXT_GAME_RULES.md` — `TEXT.GAME.RULES`.

Trình tự khởi động Text Game:

1. đọc `05_TextGame/RULES/TEXT_GAME_RULES.md`;
2. đọc `05_TextGame/RULES/GAME_MASTER_RULES.md`;
3. đọc `05_TextGame/SAVE/TEXT_GAME_STATE.md`;
4. đọc `05_TextGame/SAVE/TEXT_GAME_SURVIVORS.md`;
5. đọc `05_TextGame/SAVE/TEXT_GAME_ITEMS.md`;
6. nếu có ván đang hoạt động, đọc các source được `REQUIRED_ON_RESUME` chỉ định;
6. sau đó chỉ tải canon khác **theo nhu cầu của hành động hiện tại**.

Trong gameplay thông thường, **không được quét toàn bộ cây `Novel`** chỉ để xử lý một lượt. Việc audit/reload toàn bộ chỉ thực hiện khi người dùng yêu cầu hoặc khi phạm vi canon rộng thật sự bắt buộc.

`05_TextGame/RULES/TEXT_GAME_RULES.md` quyết định engine và dice; `05_TextGame/RULES/GAME_MASTER_RULES.md` khóa fairness, persistence và anti-cheating của GM. Ba file trong `05_SAVE` quyết định state đang sống. Không dùng trí nhớ hội thoại để ghi đè save.


---

## 2. THỨ TỰ THẨM QUYỀN

Khi có mâu thuẫn thật sự, ưu tiên:

1. Yêu cầu mới nhất và rõ ràng nhất của người dùng.
2. Dữ kiện và kết quả đã được khóa cho nhiệm vụ hiện tại.
3. Nguồn nội dung đang có hiệu lực của tác phẩm.
4. `CONVERSATION.MD` — `RULES.DIALOGUE` đối với thiết kế, sinh, hiện thực hóa và audit hội thoại.
5. `Những điều bắt buộc không được làm khi viết tiểu thuyết.md` — `RULES.FORBIDDEN` đối với cổng cấm.
6. `HuongDan.txt` — `RULES.GUIDE` đối với hướng dẫn bổ trợ không xung đột.
7. `WayOfWriting.md` — `RULES.WRITING` đối với quy trình viết tổng quát.
8. `Vivid_Verbs_Guide.md` — `RULES.VIVID_VERBS` đối với lựa chọn động từ, hành động, mô tả và biên tập câu văn.
9. `Intimacy_Writing_Guide.md` — `RULES.INTIMACY` đối với cảnh thân mật, gắn kết, dễ tổn thương, quan hệ và tiếp xúc.
10. `Supernatural_Horror_Craft_Guide.md` — `RULES.SUPERNATURAL_HORROR` đối với bất an, suspense, điểm nhìn và trải nghiệm kinh dị siêu nhiên.
11. `Dialogue_Benchmarks.md` — `RULES.DIALOGUE.BENCHMARKS` chỉ là nguồn tham chiếu cơ chế sau khi bản thoại đã tồn tại.

### 2.1. Một nguồn thẩm quyền duy nhất cho hội thoại

`RULES.DIALOGUE` là nguồn thẩm quyền duy nhất về **cách dựng, sinh, hiện thực hóa và audit hội thoại**.

Các tệp khác được phép:

- dẫn chiếu tới `RULES.DIALOGUE`;
- đặt cổng cấm tổng quát;
- mô tả cách hội thoại nối với POV, cảnh, nhịp, quan hệ hoặc quy trình biên tập;
- bổ sung yêu cầu chuyên biệt của một loại cảnh mà không tạo công thức sinh câu thoại riêng.

Các tệp khác **không được tạo một bộ Conversation Engine song song**, không được bắt buộc các trường `mục tiêu giao tiếp / nỗi sợ / bí mật / hiểu sai / rủi ro / chiến thuật` cho mọi cuộc thoại, và không được biến checklist audit thành công thức sinh từng lượt.

Nếu một quy tắc hội thoại xuất hiện ở nhiều nơi với mức chi tiết khác nhau, dùng bản trong `RULES.DIALOGUE`.

### 2.2. Người viết biết nhiều hơn nhân vật

Nguồn hậu trường có thể cho người viết biết nguyên nhân, động cơ, cảm xúc hoặc cấu trúc sâu của cảnh. Kiến thức đó **không tự động trở thành khả năng tự phân tích của nhân vật**.

Boot phải giữ nguyên hai khóa sau khi route mọi cảnh có hội thoại:

**Người viết biết nguyên nhân sâu hơn ≠ nhân vật biết nguyên nhân ấy.**  
**Người đọc có thể nhìn thấy một mẫu ≠ nhân vật nhìn thấy mẫu đó.**

---

## 3. NGUỒN CHUYÊN BIỆT VÀ ROUTER THEO LOẠI CẢNH

Các source chuyên biệt là **ACTIVE REFERENCES**. Chúng không thay thế canon, continuity, `RULES.DIALOGUE`, `RULES.FORBIDDEN` hoặc `RULES.WRITING`.

### 3.1. `RULES.VIVID_VERBS`

Mở khi sinh hoặc biên tập văn xuôi có hành động, miêu tả, chuyển động, combat, dẫn thoại hoặc cần kiểm độ chính xác của động từ.

Không dùng như từ điển thay từ cơ học. Không đổi một từ quen thuộc chỉ để câu nghe “mạnh” hoặc “văn” hơn.

### 3.2. `RULES.INTIMACY`

Mở khi cảnh có thân mật tình cảm, gắn kết sâu, dễ tổn thương, chăm sóc, ham muốn, tiếp xúc cơ thể, tình dục hoặc thay đổi quan hệ dựa trên mức độ cho phép người kia tiếp cận phần riêng tư của bản thân.

`RULES.INTIMACY` không được ghi đè:

- canon quan hệ;
- xưng hô;
- đồng thuận;
- giới hạn tri thức;
- giọng nhân vật;
- `RULES.DIALOGUE` về cách con người thật sự nói với nhau.

Không dùng intimacy như lý do để biến mọi tương tác thành lãng mạn hoặc ép nhân vật thú nhận điều họ chưa có lý do để nói.

### 3.3. `RULES.SUPERNATURAL_HORROR`

Mở khi cảnh có ma, linh thể, oán khí, hiện tượng siêu nhiên, bất an, suspense, truy đuổi siêu nhiên, không gian sai lệch hoặc kinh dị tâm lý.

Không được dùng thủ pháp kinh dị để:

- tự tạo canon;
- làm nhân vật quên năng lực hoặc trang bị;
- khiến nhân vật ngu đi để tạo sợ hãi;
- kéo dài mơ hồ sau khi nhân vật đã có đủ bằng chứng hợp lý để hành động.

### 3.4. Khi nhiều source cùng phù hợp

Dùng đồng thời nhưng phân quyền rõ:

- canon/continuity quyết định **điều gì là thật**;
- `RULES.DIALOGUE` quyết định **con người nói với nhau thế nào**;
- `RULES.INTIMACY` quyết định **độ mở, rủi ro và sự gần gũi của quan hệ vận hành thế nào**;
- `RULES.SUPERNATURAL_HORROR` quyết định **bất an và thông tin kinh dị được phân phối thế nào**;
- `RULES.VIVID_VERBS` quyết định **hành động và câu văn ở cấp từ/câu được thực hiện chính xác thế nào**;
- `RULES.WRITING` điều phối **cấu trúc văn xuôi tổng thể**.

Không cho một source chuyên biệt tự sinh lời thoại trái với `RULES.DIALOGUE`.

---

## 4. TUYẾN ĐỌC BẮT BUỘC

### 4.1. Viết hoặc viết lại toàn chương / toàn cảnh

Đọc trực tiếp:

- boot này;
- `RULES.FORBIDDEN`;
- `RULES.DIALOGUE` nếu cảnh có hội thoại;
- phần cần thiết của `RULES.GUIDE`;
- phần phù hợp của `RULES.WRITING`;
- source chuyên biệt được trigger bởi loại cảnh;
- benchmark phù hợp **chỉ khi cần hậu kiểm hoặc so sánh cơ chế**;
- đầy đủ nguồn nội dung quyết định của tác phẩm.

Không dùng tên file, đoạn trích tìm kiếm, tóm tắt cũ hoặc trí nhớ thay cho việc mở nguồn khi nhiệm vụ yêu cầu đọc trực tiếp.

### 4.2. Chỉ viết hoặc viết lại hội thoại

Đọc:

- boot;
- `RULES.DIALOGUE`;
- nguồn nhân vật, quan hệ, xưng hô, tri thức, trạng thái và cảnh ngay trước có liên quan;
- source chuyên biệt nếu tình huống trigger;
- benchmark chỉ sau khi đã có bản thoại để đối chiếu hoặc khi người dùng yêu cầu học từ một benchmark cụ thể.

### 4.3. Chỉ kiểm định hội thoại

Đọc:

- boot;
- `RULES.DIALOGUE`;
- nguyên cảnh cần kiểm định;
- nguồn nhân vật/quan hệ/xưng hô/tri thức cần thiết;
- benchmark phù hợp nếu việc so sánh giúp phát hiện cơ chế lỗi.

Không được audit một chuỗi câu tách khỏi toàn bộ tình huống nếu lỗi cần hiểu quan hệ, hoạt động, kiến thức hoặc cảm xúc quanh nó.

---

## 5. QUY TRÌNH VIẾT BẮT BUỘC

Với cảnh hoặc chương có hội thoại đáng kể, quy trình là:

**TRA CỨU → KHÓA CANON VÀ TÌNH HUỐNG → BẢN NHÁP CẢNH HOÀN CHỈNH → DIALOGUE ISOLATION AUDIT → ĐỌC LẠI TOÀN CUỘC NÓI CHUYỆN NHƯ TƯƠNG TÁC GIỮA NGƯỜI THẬT → VIẾT LẠI PHẦN HỎNG → ĐẶT LẠI VÀO CẢNH → ADVERSARIAL / CONTINUITY PASS → QUÉT TOÀN SẢN PHẨM → BẢN CUỐI.**

**Không còn `PASS A: SƠ ĐỒ TƯƠNG TÁC` bắt buộc.**  
**Không còn `Conversation Engine` bắt buộc trước khi viết.**

Lý do: việc biết trước mục tiêu, chức năng và thay đổi trạng thái của từng lượt dễ khiến nhân vật trở thành bộ xử lý hội thoại. Trước khi viết chỉ khóa đủ con người và tình huống để bắt đầu; đường đi cụ thể của cuộc nói chuyện được phép hình thành trong lúc nhân vật phản ứng với những gì vừa xảy ra.

### 5.1. Khóa canon và tình huống trước cảnh

Khóa những gì thực sự cần để không làm sai tác phẩm:

- trạng thái đầu;
- điểm nhìn;
- sự kiện bắt buộc và kết quả đã khóa;
- người hiện diện;
- quan hệ và xưng hô;
- tri thức mỗi người thật sự có;
- năng lực, vật phẩm, thương tích và giới hạn;
- việc đang diễn ra trong không gian;
- áp lực hoặc cảm xúc đã được xác lập ở mức cần thiết.

Không bắt buộc phải xác định trước cho mọi nhân vật:

- mục tiêu giao tiếp;
- nỗi sợ;
- điều giấu;
- hiểu sai;
- đòn bẩy;
- rủi ro giao tiếp;
- chiến thuật;
- phản ứng nếu người kia không hợp tác.

Nếu những yếu tố đó thật sự có trong cảnh hoặc canon, dùng chúng. Nếu không, không tự tạo để làm cảnh “có chiều sâu”.

### 5.2. Trong khi viết hội thoại

Không viết câu tiếp theo vì:

- đến lượt một nhân vật;
- cần hoàn thành một chức năng trong sơ đồ;
- cần một `state change` sau vài lượt;
- cần một câu đắt;
- cần một câu chứng minh giọng riêng;
- cần né hỏi–đáp bằng một hành vi lạ.

Viết tiếp từ **tình huống hiện tại mà mỗi người đang trải nghiệm**.

Một lời hoặc hành động vừa xảy ra có thể trở thành một dữ kiện mới đối với nhiều người. Mỗi người có thể hiểu nó khác nhau. Hậu quả giao tiếp không cần trùng với ý định của người nói.

Một người có thể:

- trả lời trực tiếp;
- chưa hiểu mình đang muốn gì;
- nói quá trong lúc tự vệ;
- nhớ ra một chuyện giữa chừng;
- hiểu gần đúng nhưng không hoàn toàn;
- phản ứng với cách người kia nói hơn là nội dung;
- vẫn mắc ở điều được nói từ vài lượt trước;
- tiếp tục công việc;
- hoặc không nói nếu họ không có gì tự nhiên để nói.

Đây không phải checklist phải phân phối đều. `RULES.DIALOGUE` quyết định chi tiết.

### 5.3. Hiện thực hóa lời nói

Lời thoại **không phải ý nghĩ đã được biên tập thành văn viết tối ưu**.

Không tự động:

- rút câu vì có phiên bản ngắn hơn;
- sửa mọi lặp hoặc vòng;
- thay từ quen bằng từ sắc hơn;
- cho nhân vật gọi đúng tên cảm xúc chỉ vì người viết biết;
- biến một suy nghĩ mơ hồ thành một phát biểu tâm lý hoàn chỉnh.

Đồng thời không được cố tình thêm lặp, từ đệm, tiếng lóng, lỗi cú pháp, dấu ba chấm hoặc câu dang dở để giả khẩu ngữ.

Mục tiêu là **đúng người, đúng khoảnh khắc**, không phải “văn nói hóa” mọi câu.

---

## 6. AUDIT HỘI THOẠI — CHỈ SAU KHI BẢN THOẠI ĐÃ TỒN TẠI

Không được coi việc vừa viết vừa tự nhắc quy tắc là audit.

Không dùng audit như một bộ công thức sinh ngược câu thoại.

### 6.1. Dialogue Isolation Audit

Với cuộc thoại đáng kể, có thể tách phần lời ra khỏi cử chỉ, thẻ giọng và mô tả trang trí để kiểm xem lời nói có còn giống những con người đang giao tiếp hay không.

Mục đích không phải làm mọi câu tự đứng hoàn hảo khi tách khỏi cảnh. Mục đích là phát hiện những chuỗi mà cử chỉ/ánh mắt/dấu ba chấm đang che cho:

- hỏi–đáp dữ liệu;
- câu cụt giả ngầu;
- canon mouthpiece;
- nhân vật nói vì đến lượt;
- lời thoại quá sạch và quá chính xác;
- nhiều người cùng một giọng.

### 6.2. Đơn vị audit chính là toàn cuộc nói chuyện trong cảnh

Không yêu cầu từng câu phải chứng minh:

- một mục tiêu giao tiếp;
- một thay đổi trạng thái;
- một lớp subtext;
- một dấu vân tay giọng riêng;
- một chức năng quan hệ;
- một bước tiến của cốt truyện.

Đọc toàn cuộc và hỏi:

- Có nhìn thấy bàn tay người viết đang chia lượt không?
- Có ai hiểu chính mình hoặc người khác quá hoàn hảo chỉ vì tác giả hiểu không?
- Có ai liên tục nói phiên bản chính xác nhất, gọn nhất và hợp lý nhất của suy nghĩ không?
- Cảm xúc có thực sự ảnh hưởng cách nghe, nói và hành động khi nó đáng kể không?
- Người ta có được phép chưa giải quyết xong không?
- Trong nhóm, người thứ ba/thứ tư đang tồn tại như con người hay như vai trò `trọng tài / comic relief / người tổng kết`?
- Việc giải quyết vấn đề thực tế có vô tình reset cảm xúc không?
- Có phản hồi trung tính nào đang được dùng như token nối lượt hoặc dấu đóng nhịp không?
- Có một nhân vật vô cớ trở thành người liên tục hỏi đúng câu để xử lý cảm xúc và ranh giới không?
- Nhiều nhân vật có đang cùng dí dỏm, bắt chữ hoặc phản bác theo một cơ chế của tác giả không?
- Có câu trả lời nào đang đóng gói context cho độc giả nhiều hơn người nghe thật sự cần không?
- Narration cuối có đang tóm tắt state change chỉ để chứng minh cảnh đã tiến triển không?
- Nếu bỏ mục đích “làm cảnh hay”, những con người này có còn nói gần như vậy không?

Các test chi tiết và Reject Gate nằm trong `RULES.DIALOGUE`.

### 6.3. Nhịp hội thoại chỉ là công cụ hậu kiểm

Có thể chia một cuộc thoại thành các cụm hoặc nhịp để tìm lỗi lặp, đổi trọng tâm, đoạn bị kéo dài hoặc chỗ mất nhân quả.

**Không quy định nhịp phải dài 2–6 lượt.**  
**Không bắt buộc mỗi nhịp phải tạo một state change.**  
**Không dùng nhịp như đơn vị sinh trước lời thoại.**

Nếu một đoạn chỉ gồm những trao đổi bình thường nhưng đúng người, đúng quan hệ và cần cho hoạt động đang diễn ra, nó không thất bại chỉ vì không tạo một biến chuyển lớn.

### 6.4. Benchmark chỉ dùng sau sinh

Benchmark là **dữ liệu quan sát**, không phải template.

Không lấy từ benchmark:

- số lượt;
- thứ tự phản ứng;
- vị trí lời xin lỗi;
- thời điểm đùa;
- cách chuyển chủ đề;
- điểm kết;
- dạng hiểu sai;

rồi tái tạo ở cảnh mới.

Chỉ quan sát cách những con người cụ thể trong benchmark nghe, hiểu, nói và làm trong tình huống riêng của họ.

---

## 7. CỔNG THEO CẢNH VÀ QUÉT TOÀN CHƯƠNG

Một cảnh có hội thoại đáng kể chỉ được coi là hoàn tất sau khi:

1. bản nháp cảnh đã tồn tại;
2. hội thoại được đọc lại độc lập và trong toàn cảnh;
3. phần máy móc, giả sâu, cụt, văn viết hóa hoặc sai người đã được viết lại thật sự;
4. phần sửa được đặt trở lại cảnh để kiểm nhân quả, xưng hô, hành động, trạng thái và dư âm;
5. canon/continuity vẫn đúng.

Không cần audit nặng cho từng câu xã giao nhỏ.

Sau khi hoàn thành toàn chương, vẫn phải chạy full-product sweep để phát hiện lỗi tích lũy:

- giọng bị đồng hóa;
- nhiều cảnh cùng cấu trúc hội thoại;
- người thứ ba liên tục làm trọng tài;
- mọi bất đồng đều đi tới cùng kiểu nhận lỗi/hòa giải;
- nhân vật ngày càng nói sạch và chính xác như tài liệu;
- câu cụt hoặc câu đinh tăng dần;
- canon mouthpiece quay lại;
- cảm xúc bị reset khi topic đổi;
- các từ xác nhận mặc định (`ừ / được / tốt / hiểu rồi...`) trở thành dấu câu chung của cả cast;
- một nhân vật lặp vai “người giao tiếp giỏi” và luôn hỏi đúng câu để giải xung đột;
- nhiều nhân vật cùng một kiểu dí dỏm/bắt chữ của tác giả;
- các câu trả lời ngày càng đóng gói exposition vừa khít;
- cảnh thường kết bằng narrator giải thích nhân vật vừa hiểu ra điều gì.

---

## 8. NHỮNG CÁCH SỬA BỊ CẤM

- Thêm nhiều lớp “nghiêm cấm” trùng nhau thay cho sửa kiến trúc.
- Kéo câu dài bằng từ rỗng để tránh thoại cụt.
- Rút câu chỉ để câu sắc, lạnh hoặc điện ảnh hơn.
- Chèn cử chỉ, ánh mắt, im lặng hoặc dấu ba chấm vào một chuỗi thoại phẳng.
- Cho người nghe né, hỏi ngược hoặc bẻ tiền đề ngẫu nhiên chỉ để tránh hỏi–đáp.
- Ép mọi cảnh thành đấu trí, thương lượng, trauma disclosure hoặc subtext.
- Gán cho mọi nhân vật `want / fear / secret / misread / leverage` để điền đủ một engine.
- Dùng checklist như công thức sinh từng câu.
- Lập trước chức năng cho từng lượt rồi chỉ đổi thành lời nói.
- Chuyển bullet dàn ý/canon trực tiếp thành lời thoại rồi chỉ sửa văn phong bề mặt.
- Biến nhân vật thành người phân tích tâm lý hoàn hảo chỉ vì tác giả hiểu vấn đề.
- “Làm sạch” lời thoại tự nhiên cho tới khi nó thành văn viết.
- Cố tình làm câu lộn xộn để giả tiếng nói tự nhiên.
- Sao chép câu hoặc đường đi bề mặt từ benchmark vào tác phẩm.
- Giải sẵn đường tâm lý của cả cuộc thoại rồi chỉ “làm tự nhiên” bằng lặp nhẹ, cử chỉ, joke và khẩu ngữ.
- Thay một phản hồi mặc định bằng phản hồi mặc định khác mà không sửa chức năng nối lượt.
- Dùng một nhân vật như therapist/mediator mặc định để hỏi đúng nhu cầu và ranh giới ở từng bước.

---

## 9. CỔNG CUỐI

Bản thảo chưa được xuất bản nếu còn lỗi nổi bật về:

- canon, tri thức, năng lực, vật phẩm hoặc trạng thái;
- POV;
- xưng hô và quan hệ;
- hội thoại theo `RULES.DIALOGUE`;
- văn phong máy móc, giả sâu hoặc giả nhịp;
- lời thoại bị văn viết hóa hoặc tối ưu hóa quá mức;
- nhân vật nói vì đến lượt;
- người thứ ba/bốn bị dùng như chức năng của cảnh;
- vấn đề thực tế được giải quyết rồi cảm xúc bị reset vô cớ;
- phản hồi mặc định bị lặp như dấu nối lượt;
- một giọng dí dỏm/đối đáp của tác giả phủ lên nhiều nhân vật;
- một nhân vật giao tiếp quá hoàn hảo và liên tục mở đúng tầng tâm lý tiếp theo;
- exposition được đóng gói trong câu trả lời vượt nhu cầu của người nghe;
- narration chốt sạch state change chỉ để kết cảnh;
- độ dài hoặc định dạng theo yêu cầu.

Câu kiểm cuối cùng cho hội thoại:

**Nếu không có độc giả cần gây ấn tượng hoặc cần được giải thích, những con người này có thật sự nói và phản ứng gần như vậy với nhau trong hoàn cảnh này không?**

**Mệnh lệnh cuối:** ưu tiên con người cụ thể đang sống qua một tình huống cụ thể. Không tối ưu văn bản để vượt checklist bề mặt.
