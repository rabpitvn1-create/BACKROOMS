# NHỮNG ĐIỀU BẮT BUỘC KHÔNG ĐƯỢC LÀM KHI VIẾT TIỂU THUYẾT

**Mã tra cứu:** `RULES.FORBIDDEN`
**Phạm vi:** Bộ cổng cấm dùng chung khi viết và biên tập văn xuôi hư cấu.
**Mục đích:** Ngăn lỗi tri thức, phá năng lực nhân vật, ngôn ngữ hậu trường, xưng hô vô căn cứ, hội thoại máy móc, lời thoại bị bẻ vụn và văn phong giả sâu.
**Nguồn điều phối:** Dùng cùng `00_RULES_BOOT.md`; tài liệu này là cổng cấm, không phải công thức dựng mọi cảnh.
**Bản sửa tích hợp hội thoại:** `HUMAN-CONVERSATION R03`

**Khóa kiến trúc:** `RULES.FORBIDDEN` chỉ được nhận diện và chặn lỗi. Nó không được tạo Conversation Engine, không được chia lượt, không được buộc state change, không được bắt chọn chế độ hội thoại và không được dùng checklist cấm như công thức sinh câu. Thiết kế, sinh, hiện thực hóa và audit hội thoại thuộc `RULES.DIALOGUE`.

---

[GPT.READ_FIRST]

## 0. PHẠM VI HIỆU LỰC VÀ CÁCH TRA CỨU

Tài liệu này là cổng cấm bắt buộc khi viết hoặc biên tập tiểu thuyết trong tác phẩm. Các mã trong ngoặc vuông là điểm neo để ChatGPT tra đúng phần cần đọc.

### 0.1. Mã tối thiểu khi viết hoặc viết lại một chương

- `[NOVEL.KNOWLEDGE]`
- `[NOVEL.KNOWLEDGE.LEAK]`
- `[NOVEL.COMPETENCE.NO_SUDDEN_STUPIDITY]`
- `[NOVEL.META]`
- `[NOVEL.LANGUAGE]`
- `[NOVEL.ANON]`
- `[NOVEL.DIALOGUE.ADDRESS]`
- `[NOVEL.DIALOGUE.FRAGMENT]`
- `[NOVEL.DIALOGUE.HUMAN]`
- `[NOVEL.DIALOGUE.OVERDESIGNED]`
- `[NOVEL.STYLE.FALSE_DEPTH]`
- `[NOVEL.GATE]`

Khi tìm thấy một thẻ, phải đọc trọn phần nằm sau thẻ đó đến trước thẻ cùng cấp kế tiếp. Không lấy một câu rời khỏi ngữ cảnh rồi áp dụng độc lập.

### 0.2. Thứ tự ưu tiên nguồn

Khi có mâu thuẫn, ưu tiên theo thứ tự:

1. yêu cầu mới nhất của người dùng;
2. dữ kiện khóa trong dàn ý hiện tại;
3. dữ kiện đã khóa đang hiệu lực;
4. hồ sơ nhân vật và quan hệ đang hiệu lực;
5. chương hoàn chỉnh gần nhất còn hiệu lực.

Tài liệu này là quy tắc viết, không phải dữ kiện đã khóa nội thế giới. Nhân vật không tự biết các mã tra cứu, tên file, dàn ý, tài liệu hậu trường hay quy trình hậu trường.

---

[NOVEL.KNOWLEDGE]

## 1. KHÓA KIẾN THỨC CỦA TỪNG NHÂN VẬT

Tài liệu hậu trường, dàn ý và hồ sơ nguồn là kiến thức của người viết, không tự động là kiến thức của nhân vật.

Mỗi dữ kiện một nhân vật sử dụng trong suy nghĩ, lời nói hoặc quyết định phải có nguồn hợp lệ, chẳng hạn:

- nhân vật đã biết từ trước theo dữ kiện đã khóa;
- nhân vật trực tiếp quan sát hoặc trải nghiệm;
- nhân vật được người khác kể;
- nhân vật đã kiểm chứng bằng năng lực, thiết bị hoặc bằng chứng;
- nhân vật suy luận hợp lý từ những dữ kiện mình thật sự có.

Nếu chỉ được kể, thông tin vẫn có thể là lời khai chưa xác minh. Nếu chỉ suy luận, văn bản phải giữ đúng mức độ phỏng đoán, nghi ngờ hoặc xác suất. Không biến suy luận thành sự thật khách quan chỉ vì người viết biết đáp án.

---

[NOVEL.KNOWLEDGE.LEAK]

## 2. CẤM RÒ RỈ KIẾN THỨC HẬU TRƯỜNG GIỮA NHÂN VẬT

Một nhân vật không được tự biết tuổi, lịch sử, động cơ, cảm xúc, điểm yếu, năng lực, giới hạn năng lực, quan hệ, vật phẩm, bí mật hoặc kế hoạch của người khác chỉ vì dữ kiện đó có trong tài liệu hậu trường.

Việc chứng kiến một năng lực được sử dụng chỉ cho phép người quan sát biết những gì hiệu ứng thực tế đã chứng minh. Không được tự suy ra toàn bộ giới hạn, điều kiện kích hoạt, phạm vi, thời gian hồi phục hoặc điểm yếu nếu chưa có nguồn nhận biết hợp lệ.

### 2.1. Cổng kiểm nguồn kiến thức

Với mọi câu trong đó một nhân vật nói hoặc nghĩ về người khác, phải kiểm được:

1. Họ biết điều này bằng cách nào?
2. Dữ kiện đến từ cảnh nào hoặc nguồn nào?
3. Họ đã kiểm chứng hay chỉ nghe kể?
4. Đây là sự thật đã biết, suy luận hay nghi ngờ?
5. Nếu chỉ dùng những gì nhân vật đã thật sự biết trong truyện, họ còn có thể biết điều đó không?

Nếu không trả lời được, bắt buộc sửa, hạ mức chắc chắn hoặc loại bỏ.

---

[NOVEL.COMPETENCE.NO_SUDDEN_STUPIDITY]

## 3. CẤM NHÂN VẬT “NGU ĐỘT XUẤT” ĐỂ CỨU KỊCH BẢN

Nghiêm cấm làm nhân vật đột nhiên mất năng lực phán đoán, quên trang bị, quên kỹ năng, quên kinh nghiệm hoặc bỏ qua phương án mà họ chắc chắn phải nghĩ tới chỉ để tạo nguy hiểm hay kéo dài tình tiết.

Đặc biệt cấm:

- quên món đồ đang mang theo chỉ vì nó giải quyết vấn đề quá nhanh;
- quên kỹ năng hoặc quy trình mà nhân vật đã thành thạo;
- quên kinh nghiệm đã được xác lập;
- bỏ qua đường rút, công cụ, chế độ trang bị hoặc năng lực hiển nhiên mà nhân vật có lý do mạnh để nhớ;
- phạm sai lầm sơ đẳng trái hẳn mức năng lực đã khóa chỉ vì cốt truyện cần thất bại;
- dùng giận dữ, sợ hãi, ghen, tình cảm hoặc bị khiêu khích như cái cớ để xóa sạch trí nhớ và năng lực phán đoán khi dữ kiện đã khóa không hỗ trợ mức suy giảm đó.

Nhân vật giỏi vẫn có thể sai hoặc thất bại, nhưng nguyên nhân phải đến từ bài toán khó thật: thiếu thông tin, dữ liệu sai, thời gian gấp, mục tiêu xung đột, đối thủ đủ giỏi, môi trường khắc chế, thương tích, chi phí quá lớn hoặc giới hạn đã được thiết lập.

Nếu một năng lực hay trang bị không thể dùng, cảnh phải có nguyên nhân hợp lệ. Không được giả vờ nó không tồn tại.

---

[NOVEL.META]

## 4. CẤM NGÔN NGỮ META LỌT VÀO VĂN XUÔI

Nhân vật và lời kể nội thế giới không được nhắc trực tiếp những khái niệm hậu trường như:

- chương trước, chương sau, số chương;
- cảnh trước, cảnh kế tiếp;
- dàn ý, tài liệu hậu trường, tài liệu thế giới;
- tên file, mã tra cứu, checklist;
- “nhân vật phụ”, “nhân vật thứ ba”, “boss”, “plot”, “arc” nếu đó không phải thuật ngữ thật trong thế giới truyện.

Nếu một câu chỉ có nghĩa khi người đọc biết cấu trúc bản thảo hoặc tài liệu hậu trường, câu đó phải được viết lại thành ký ức, sự kiện, mốc thời gian, địa điểm, quan hệ hoặc trạng thái có thật trong thế giới truyện.

---

[NOVEL.LANGUAGE]

## 5. CẤM LẠM DỤNG TIẾNG ANH VÀ THUẬT NGỮ KỸ THUẬT

Văn xuôi chính ưu tiên tiếng Việt tự nhiên. Chỉ giữ tiếng Anh khi đó là:

- tên riêng dữ kiện đã khóa;
- tên tổ chức, trang bị, năng lực hoặc thuật ngữ đã được khóa bằng tiếng Anh;
- khái niệm không có cách Việt hóa hợp lý và thật sự cần thiết trong ngữ cảnh.

Không dùng thuật ngữ chuyên môn chỉ vì tài liệu nguồn có nó. Một thuật ngữ chỉ nên đi vào lời thoại hoặc điểm nhìn khi nhân vật có nền tảng để biết và có lý do tự nhiên để dùng.

Lời kể ưu tiên mô tả điều đang xảy ra hơn là dán nhãn kỹ thuật lên mọi hiện tượng.

### 5.1. CẤM BÊ NGUYÊN NHÃN KỸ THUẬT HOẶC TIẾNG ANH VÀO VĂN XUÔI

Không được chép nguyên các nhãn phân loại, cụm từ kỹ thuật hoặc cách gọi hậu trường bằng tiếng Anh vào văn xuôi chỉ vì tài liệu nguồn dùng chúng. Những cụm kiểu `original inanimate object` không được xuất hiện nguyên dạng trong lời kể, miêu tả, suy nghĩ hoặc lời thoại nếu nhân vật không có lý do tự nhiên để dùng chính thuật ngữ đó.

Khi ý nghĩa có thể diễn đạt bằng tiếng Việt, bắt buộc viết thành câu tiếng Việt rõ ràng, tự nhiên và đúng ngữ cảnh. Không dịch máy từng chữ thành một nhãn tiếng Việt vẫn cứng và kỹ thuật; phải nói thẳng điều người đọc cần hiểu. Ví dụ, thay vì `original inanimate object`, có thể viết theo ngữ cảnh như “một đồ vật không có sự sống, vốn đã tồn tại từ trước” hoặc mô tả trực tiếp món đồ cụ thể đang được nói tới.

Nguyên tắc bắt buộc: **ưu tiên cách nói mà một người đọc tiếng Việt có thể hiểu ngay, không cần biết thuật ngữ hậu trường, tên trường dữ liệu, nhãn phân loại hay tiếng Anh gốc.**

---

[NOVEL.ANON]

## 6. CẤM ĐÁNH SỐ NHÂN VẬT VÔ DANH NHƯ BIÊN BẢN

Không kéo dài cách gọi “người thứ nhất”, “người thứ hai”, “người thứ ba” hoặc các nhãn quản lý tương tự qua nhiều đoạn, nhiều cảnh.

Khi nhân vật chưa có tên, nhận diện họ bằng dữ kiện đang có trong cảnh, ví dụ:

- người đang bị thương;
- người giữ chiếc chìa khóa;
- người đứng gần cửa;
- người vừa lên tiếng phản đối;
- người đi cuối nhóm.

Các cách gọi đó chỉ phục vụ nhận diện trong ngữ cảnh, không tự động trở thành biệt danh hoặc danh xưng dữ kiện đã khóa.

---

[NOVEL.DIALOGUE.ADDRESS]

## 7. CẤM TỰ BỊA HOẶC TỰ ĐỔI XƯNG HÔ

Xưng hô phải lấy từ nguồn quan hệ và hồ sơ nhân vật đang có hiệu lực. Không được suy đoán từ tuổi biểu kiến, giới tính, chức vụ, mức độ hấp dẫn, thói quen của mô hình hoặc cảm giác rằng một cách gọi khác “nghe tự nhiên hơn”.

Không tự đổi đại từ chỉ vì:

- hấp dẫn hoặc thân thiết hơn;
- ghen, giận hoặc căng thẳng;
- vừa cứu mạng nhau;
- phối hợp chiến đấu tốt;
- một cảnh thân mật.

Chỉ thay đổi xưng hô khi quan hệ hoặc trạng thái tâm lý đã có nguyên nhân đủ mạnh và nguồn hiện hành cho phép thay đổi đó.

Nếu không xác định được hệ xưng hô từ tài liệu hiện hành, không được tự đoán.

---

[NOVEL.DIALOGUE.FRAGMENT]

## 8. CẤM BẺ VỤN LỜI THOẠI ĐỂ TẠO KHÍ CHẤT

Cổng cấm này chỉ xác định lỗi. Cách thiết kế, ngoại lệ và phương pháp sửa nằm trong `RULES.DIALOGUE`.

Bắt buộc viết lại khi lời thoại bị chặt thành mảnh, bỏ mối nối tự nhiên, nhiều nhân vật cùng nói cộc theo một nhịp hoặc dùng câu rất ngắn chỉ để tạo vẻ lạnh, quân nhân, bí ẩn, sắc bén hay “điện ảnh”.

Không đánh đồng câu ngắn hoàn chỉnh với câu cụt. Không nối thêm từ rỗng để vượt ngưỡng hình thức.

---

[NOVEL.DIALOGUE.HUMAN]

## 9. CẤM HỘI THOẠI MÁY MÓC VÀ HỘI THOẠI DÙNG ĐỂ ĐỌC DÀN Ý

Nguồn thẩm quyền chi tiết: `RULES.DIALOGUE`. Cổng này chỉ nhận diện lỗi đầu ra; không được dùng nó để lập sẵn đường đi của cuộc thoại.

Bắt buộc viết lại khi có một hoặc nhiều lỗi cấu trúc nổi bật:

- hỏi đúng → đáp đủ → hỏi tiếp → đáp tiếp như biểu mẫu;
- câu hỏi tồn tại chủ yếu để mở exposition mà người hỏi không có nhu cầu tự nhiên phải hỏi;
- bullet dàn ý, continuity hoặc canon bị chuyển trực tiếp thành lời;
- nhân vật trở thành giao diện canon, liên tục phát biểu giới hạn hậu trường hoặc mức xác minh chỉ để bảo vệ continuity;
- nhiều người lần lượt phát biểu phần thông tin hoặc “quan điểm” được chia sẵn cho mình như một cuộc họp;
- lời nói được phân phối vì “đến lượt” thay vì vì người đó đang sống trong tình huống;
- né → ép → né, hỏi ngược, cắt lời hoặc bẻ tiền đề được chèn theo mẫu chỉ để tạo cảm giác có tương tác;
- người nói liên tục diễn đạt phiên bản chính xác, tối ưu hoặc có tính chẩn đoán tâm lý hơn mức con người đó có khả năng nói ra;
- người nghe liên tục hiểu đúng tầng tâm lý sâu nhất của câu nói như thể mọi người đều nhìn thấy toàn bộ cấu trúc cuộc trò chuyện;
- phản hồi trung tính như `ừ`, `được`, `tốt`, `hiểu rồi`, `biết rồi` bị dùng lặp như token nối lượt hoặc dấu đóng nhịp ở nhiều nhân vật;
- một nhân vật liên tục hỏi đúng câu để phân loại cảm xúc, nhu cầu hoặc ranh giới, khiến họ thành người điều phối trị liệu của cảnh mà hồ sơ không thiết lập;
- nhiều nhân vật cùng có một kiểu bắt chữ, literalism, đối đáp dí dỏm hoặc câu phản bác sắc như thể cùng một giọng tác giả;
- câu trả lời đóng gói trọn một hồ sơ nền cho độc giả dù người nghe chỉ cần một phần;
- lời kể cuối cảnh tóm tắt sạch nhân vật “giờ đã hiểu gì” hoặc quan hệ “đã đổi gì” chỉ để đóng một state change.

**Không coi là lỗi chỉ vì một lượt không phản ứng trực tiếp với câu ngay trước.** Một người có thể còn mắc ở câu cũ, phản ứng với thái độ, với hành động đang diễn ra, với điều người thứ ba vừa nói hoặc với điều họ vừa tự nhận ra. Điều cần có là tính liên tục nhân quả của **toàn cuộc nói chuyện**, không phải chuỗi A → B → A bắt buộc.

Nếu lỗi thuộc cấu trúc, không được chữa bằng kéo dài câu, thêm cử chỉ, thêm từ đệm hoặc đổi vài từ. Phải dựng lại tương tác theo `RULES.DIALOGUE`.

---

[NOVEL.DIALOGUE.OVERDESIGNED]

## 9A. CẤM HỘI THOẠI QUÁ “ĐƯỢC THIẾT KẾ” ĐẾN MẤT ĐỜI

Nguồn thẩm quyền chi tiết: `RULES.DIALOGUE`.

Bắt buộc viết lại khi toàn cuộc thoại vận hành như màn trình diễn tính cách, subtext, đọc vị hoặc đấu quyền lực; khi gần như câu nào cũng cố sắc, sâu hoặc đáng trích dẫn; hoặc khi cảnh hợp tác/đời thường bị ép thành thương lượng mà không có nguyên nhân.

Cũng phải kiểm lại khi lời nói đã bị **văn viết hóa**: ai cũng diễn đạt sạch, gọn, đúng thuật ngữ, gọi tên cảm xúc quá chính xác hoặc nói ra trọn vẹn điều tác giả hiểu về họ.

Đặc biệt đáng nghi khi xung đột đi quá sạch theo đường `kích hoạt → gọi đúng cảm xúc → xác định vấn đề sâu → hỏi đúng nhu cầu → thương lượng ranh giới → chấp nhận → đùa → narrator chốt dư âm`. Đường này có thể tự nhiên trong một cảnh cụ thể, nhưng không được là cấu trúc mặc định được giải sẵn rồi phủ khẩu ngữ lên trên.

Cho phép lời bình thường, vòng nhẹ, lặp hợp lý, tự sửa, nói chưa hết ý tâm lý hoặc quay về việc thực tế nếu đó thật sự là cách người ấy nói trong tình huống. Không cố thêm những đặc điểm này để giả khẩu ngữ.

Giọng riêng được đánh giá trên toàn cuộc thoại và cách một người giao tiếp với những người khác nhau; không ép từng câu riêng phải mang một “dấu vân tay” phô diễn.

---

[NOVEL.STYLE.FALSE_DEPTH]

## 10. CẤM CÂU CHỮ MƠ HỒ, ẨN DỤ TRANG TRÍ VÀ “GIẢ SÂU”

Không dùng ẩn dụ, câu tối nghĩa, khẩu hiệu, kết luận nửa vời hoặc lời nói nghe có vẻ sâu sắc nhưng không truyền đạt nội dung thật chỉ để tạo khí chất.

Ẩn dụ chỉ được giữ khi nó làm rõ cảm xúc, hình ảnh hoặc ý nghĩa của cảnh. Nếu bỏ ẩn dụ mà nội dung trở nên rõ hơn và không mất giá trị, ưu tiên cách viết trực tiếp hơn.

Không để nhiều nhân vật cùng nói bằng một giọng lạnh, triết lý hoặc sắc cạnh giống nhau. Giọng nói phải xuất phát từ tuổi, xuất thân, học vấn, kinh nghiệm, tính cách, trạng thái và quan hệ.

---

[NOVEL.GATE]

## 11. CỔNG KIỂM ĐỊNH BẮT BUỘC TRƯỚC KHI XUẤT BẢN

Sau khi hoàn thành bản nháp, phải có một vòng kiểm riêng trước khi xuất bản.

### 11.1. Kiến thức nhân vật

- Mỗi thông tin nhân vật dùng về người khác có nguồn nhận biết hợp lệ không?
- Lời kể có biến lời khai hoặc suy luận thành sự thật chắc chắn không?
- Có nhân vật nào hiểu toàn bộ năng lực của người khác chỉ sau một lần chứng kiến không?

### 11.2. Năng lực và logic

- Nhân vật có quên trang bị, kỹ năng, kinh nghiệm hoặc phương án hiển nhiên để cốt truyện hoạt động không?
- Nếu năng lực không dùng được, nguyên nhân đã được thiết lập hợp lệ chưa?

### 11.3. Ngôn ngữ và meta

- Có từ ngữ hậu trường như chương, dàn ý, tài liệu hậu trường, tên file hoặc số thứ tự quản lý lọt vào văn xuôi không?
- Tiếng Anh và thuật ngữ chuyên môn còn lại có thật sự cần và phù hợp điểm nhìn không?

### 11.4. Hội thoại

Phải audit theo `RULES.DIALOGUE`; không duy trì một checklist hội thoại song song trong tài liệu này và không dùng các câu hỏi dưới đây để sinh từng lượt.

Ở cấp cổng cấm, tối thiểu phải xác nhận:

- canon, xưng hô và nguồn tri thức của người nói đúng;
- dàn ý/continuity không bị đọc thành lời;
- không có câu hỏi mồi, canon mouthpiece hoặc exposition chia vai;
- toàn cuộc nói chuyện còn có tính liên tục nhân quả dù có thể vòng, lặp, lệch trọng tâm hoặc chưa giải quyết xong;
- không bắt người kế tiếp phải phản ứng trực tiếp câu ngay trước chỉ để chứng minh nhân quả;
- nhân vật không liên tục sở hữu khả năng tự phân tích hoặc đọc người khác chính xác hơn dữ kiện, vốn từ và mức tự nhận thức của họ;
- lời nói không bị tối ưu thành văn viết sạch đến mức mất nhịp nói của con người cụ thể;
- với ba người trở lên, không chia lượt đều hoặc gán vai cố định như trọng tài, chuyên gia giải thích, comic relief hay người tổng kết nếu tình huống không tạo ra vai đó;
- không có cụm câu bị bẻ vụn, nhiều người cùng cộc hoặc cảnh bị chiến thuật hóa vô cớ;
- phản hồi trung tính không bị dùng như dấu câu để chuyển lượt;
- không có “therapist character” vô cớ liên tục hỏi đúng câu để sửa cuộc xung đột;
- các nhân vật không cùng dí dỏm theo một cơ chế của tác giả;
- câu trả lời không đóng gói exposition nhiều hơn người trong cảnh cần;
- narration cuối không giải thích sạch state change chỉ để đóng cảnh.

Cuộc thoại **không bắt buộc** phải giải quyết cảm xúc, đạt đồng thuận, tạo bước ngoặt hoặc kết thúc sạch. Hợp tác trở lại không đồng nghĩa hết giận; im chuyện không đồng nghĩa vấn đề đã xong.

Nếu lỗi là cấu trúc tương tác, phải dựng lại phần hỏng theo `RULES.DIALOGUE`, không vá bề mặt.

### 11.5. Văn phong

- Có chuỗi đoạn một câu liên tiếp hoặc câu cực ngắn dày đặc để giả nhịp không?
- Có cấu trúc, cử chỉ hoặc kiểu chốt câu lặp máy móc không?
- Có ẩn dụ hoặc khẩu hiệu nào không mang thêm nghĩa thực không?

Nếu bất kỳ câu kiểm nào cho thấy lỗi, phải sửa phần liên quan rồi đọc lại trong toàn cảnh. Phát hiện lỗi không được tính là đã biên tập.

Chỉ xuất bản khi bản cuối không còn lỗi nổi bật thuộc các nhóm trên.

---

[GPT.QUERY_RECIPES]

## 12. CÔNG THỨC TRA CỨU NHANH

| Tác vụ | Mã cần đọc |
|---|---|
| Viết hoặc biên tập toàn chương | `GPT.READ_FIRST` + `NOVEL.KNOWLEDGE` + `NOVEL.KNOWLEDGE.LEAK` + `NOVEL.COMPETENCE.NO_SUDDEN_STUPIDITY` + `NOVEL.META` + `NOVEL.LANGUAGE` + `NOVEL.ANON` + `NOVEL.DIALOGUE.ADDRESS` + `NOVEL.DIALOGUE.FRAGMENT` + `NOVEL.DIALOGUE.HUMAN` + `NOVEL.DIALOGUE.OVERDESIGNED` + `NOVEL.STYLE.FALSE_DEPTH` + `NOVEL.GATE` |
| Kiểm nhân vật biết quá nhiều | `NOVEL.KNOWLEDGE` + `NOVEL.KNOWLEDGE.LEAK` |
| Kiểm nhân vật quên năng lực/trang bị | `NOVEL.COMPETENCE.NO_SUDDEN_STUPIDITY` |
| Kiểm ngôn ngữ meta | `NOVEL.META` |
| Kiểm tiếng Anh và thuật ngữ | `NOVEL.LANGUAGE` |
| Kiểm nhân vật vô danh | `NOVEL.ANON` |
| Kiểm xưng hô | `NOVEL.DIALOGUE.ADDRESS` |
| Kiểm thoại cụt/một từ | `NOVEL.DIALOGUE.FRAGMENT` |
| Kiểm hỏi đáp máy móc / hội thoại quá được thiết kế | `NOVEL.DIALOGUE.HUMAN` + `NOVEL.DIALOGUE.OVERDESIGNED` |
| Kiểm câu giả sâu/ẩn dụ trang trí | `NOVEL.STYLE.FALSE_DEPTH` |
| Kiểm định cuối | `NOVEL.GATE` |

---

[GPT.RETRIEVAL_INDEX]

## 13. CHỈ MỤC MÃ TRA CỨU

| Mã | Nội dung |
|---|---|
| `RULES.FORBIDDEN` | Toàn bộ tài liệu |
| `GPT.READ_FIRST` | Cách dùng và thứ tự ưu tiên nguồn |
| `NOVEL.KNOWLEDGE` | Khóa kiến thức riêng của từng nhân vật |
| `NOVEL.KNOWLEDGE.LEAK` | Cấm rò rỉ kiến thức hậu trường giữa nhân vật |
| `NOVEL.COMPETENCE.NO_SUDDEN_STUPIDITY` | Cấm nhân vật quên năng lực, trang bị, kỹ năng hoặc kinh nghiệm để cứu kịch bản |
| `NOVEL.META` | Cấm ngôn ngữ hậu trường lọt vào văn xuôi |
| `NOVEL.LANGUAGE` | Kiểm soát tiếng Anh và thuật ngữ kỹ thuật |
| `NOVEL.ANON` | Cấm đánh số nhân vật vô danh như biên bản |
| `NOVEL.DIALOGUE.ADDRESS` | Cấm tự bịa hoặc tự đổi xưng hô |
| `NOVEL.DIALOGUE.FRAGMENT` | Cấm thoại cụt, một từ và câu lạnh giả sắc bén |
| `NOVEL.DIALOGUE.HUMAN` | Cấm hội thoại máy móc, hỏi đáp quá hoàn hảo |
| `NOVEL.DIALOGUE.OVERDESIGNED` | Cấm hội thoại quá được thiết kế, quá sắc hoặc quá nhiều subtext đến mất tính đời thường |
| `NOVEL.STYLE.FALSE_DEPTH` | Cấm câu mơ hồ, ẩn dụ trang trí và giả sâu |
| `NOVEL.GATE` | Cổng kiểm định trước khi xuất bản |
| `GPT.QUERY_RECIPES` | Công thức chọn mã theo tác vụ |
| `GPT.RETRIEVAL_INDEX` | Chỉ mục mã tra cứu |

## KHÓA TRỌNG TÂM

- Người viết biết không đồng nghĩa nhân vật biết.
- Một lần chứng kiến không đồng nghĩa hiểu toàn bộ năng lực.
- Lời khai không tự động là sự thật đã xác minh.
- Không làm nhân vật “ngu đột xuất” để ép tình tiết.
- Không để cấu trúc hậu trường lọt vào văn xuôi.
- Không tự bịa hoặc tự đổi xưng hô.
- Không bẻ lời thoại thành mảnh, nhưng cũng không coi mọi câu ngắn hoàn chỉnh là lỗi.
- Không viết hội thoại như hệ hỏi đáp dữ liệu hoặc trò chơi chiến thuật mặc định.
- Không biến mọi câu thành màn trình diễn tính cách, subtext hoặc câu đắt; ưu tiên lời nói bình thường, đời và trực tiếp khi hoàn cảnh cho phép.
- Kiểm tính liên tục trên toàn cuộc thoại; không bắt từng lượt phải tạo state change hoặc phản ứng trực tiếp lượt ngay trước.
- Người viết có thể hiểu nguyên nhân sâu hơn; nhân vật không tự động có ngôn ngữ hoặc mức tự nhận thức để nói nó ra chính xác.
- Không văn viết hóa lời nói, nhưng cũng không thêm từ đệm, lặp, câu dang dở hay vụng về ngẫu nhiên để giả tự nhiên.
- Không dùng ẩn dụ hoặc câu giả sâu nếu chúng không mang thêm nghĩa thật.

