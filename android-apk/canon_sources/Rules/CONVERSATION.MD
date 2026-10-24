# CONVERSATION — HỘI THOẠI, GIAO TIẾP VÀ XƯNG HÔ

**Mã tra cứu chính:** `RULES.CONVERSATION`  
**Alias tương thích:** `RULES.DIALOGUE`, `RULES.COMMUNICATION`, `RULES.ADDRESS`  
**Loại tài liệu:** Nguồn thẩm quyền duy nhất về thiết kế, viết, xưng hô và kiểm định hội thoại hư cấu  
**Phạm vi:** Dùng chung cho mọi tác phẩm văn xuôi hư cấu  
**Trạng thái:** ACTIVE / AUTHORITATIVE  
**Bản sửa core:** HUMAN-CONVERSATION R05-ANTI-AI-VOICE  
**Thay thế hoàn toàn:** `Phong Cách hội thoại.md`, `Phong Cách Giao Tiếp.md`, `XungHoNhanVat.md`

> Khi có khác biệt về hội thoại, giao tiếp hoặc xưng hô, dùng tài liệu này sau yêu cầu mới nhất của người dùng và nguồn nội dung đang có hiệu lực của tác phẩm. Các alias cũ chỉ tồn tại để các tài liệu chưa đổi mã tra cứu vẫn trỏ về cùng nguồn này.

---

# 0. LỆNH ĐỌC VÀ NGUYÊN TẮC GỐC

Trước một cảnh có hội thoại quan trọng:

1. Đọc `00_RULES_BOOT.md` và `CONVERSATION.MD`.
2. Đọc trực tiếp nguồn nội dung cần thiết: hồ sơ nhân vật, quan hệ, trạng thái hiện tại, cảnh trước, tri thức hiện có, xưng hô và ràng buộc liên quan.
3. Khóa điều phải đúng hoặc phải xảy ra, nhưng không khóa trước câu thoại cụ thể trừ khi người dùng yêu cầu giữ nguyên lời.
4. Khóa con người và tình huống đủ để bắt đầu; không lập sẵn một bản đồ lượt nói.
5. Viết cuộc thoại trong cảnh như một phần của đời sống đang tiếp diễn.
6. Chỉ sau bản nháp mới audit cấu trúc, nhân quả, giọng, ngôn ngữ và điểm dừng.

Nếu thiếu dữ kiện quyết định xưng hô, quan hệ, tri thức hoặc động cơ đã được canon khóa, không được tự biến suy đoán thành sự thật.

## 0.1. Mục tiêu mặc định — bình thường hơn, đời hơn

Ưu tiên cách khiến nhân vật nghe như người thật đang sống trong tình huống, không như các vai đang thay nhau trình diễn thông minh, bí ẩn, chiều sâu hoặc canon.

Cho phép:

- trả lời thẳng khi không có lý do để né;
- câu đơn giản khi ý đơn giản;
- xác nhận, xin phép, cảm ơn, hỏi lại, phàn nàn nhỏ, tự sửa, trêu nhẹ hoặc quay lại công việc;
- những khoảng không có subtext lớn;
- một người chưa hiểu rõ chính cảm xúc hoặc nhu cầu của mình;
- một người hiểu gần đúng nhưng không hoàn toàn điều người kia nói;
- một câu hơi vòng, hơi lặp hoặc chưa tối ưu nếu đó thật sự là cách người ấy nói trong khoảnh khắc đó;
- vấn đề thực tế được xử lý trong khi cảm xúc vẫn chưa xong.

“Đời” không có nghĩa cố ý viết vụn, nhét từ đệm, lạm dụng dấu ba chấm, tiếng lóng, chửi thề hoặc lỗi ngữ pháp.

**Phép thử nền:** nếu không có độc giả để gây ấn tượng hoặc cần được giải thích, người này có thật sự nói điều này với người trước mặt vào lúc này không?

---

# I. CORE GENERATION — HỘI THOẠI PHÁT SINH TỪ CON NGƯỜI

## 1. HỘI THOẠI LÀ HÀNH VI CỦA NHỮNG CON NGƯỜI CÓ GÓC NHÌN HẠN CHẾ

Nhân vật không nhìn thấy toàn bộ cấu trúc cuộc trò chuyện.

Mỗi người chỉ có:

- những gì họ thật sự biết;
- những gì họ vừa nhìn thấy hoặc nghe thấy;
- ký ức và kinh nghiệm riêng;
- quan hệ của họ với những người đang có mặt;
- cảm giác hiện tại, kể cả khi họ chưa hiểu chính xác cảm giác đó;
- cách quen thuộc của họ khi thoải mái, khó chịu, bị trách, bị hiểu sai, lo lắng, xấu hổ, tức giận hoặc không biết phải làm gì.

Không bắt buộc nhân vật phải hiểu rõ:

- mình đang muốn gì;
- mình đang sợ gì;
- vì sao mình đang giận;
- người kia thật sự có ý gì;
- cuộc nói chuyện đang đi về đâu.

Một người có thể chỉ biết rằng mình đang khó chịu. Một người khác có thể biết mình muốn sửa chuyện vừa xảy ra nhưng không biết người kia muốn được sửa theo cách nào. Một người có thể nghĩ mình đang giúp trong khi người kia thấy mình đang can thiệp.

Đó không phải lỗi cần sửa nếu phù hợp với con người và tình huống.

### 1.1. Người viết biết nhiều hơn nhân vật

Người viết có thể hiểu nguyên nhân sâu hơn của hành vi, nhưng kiến thức đó không tự động trở thành khả năng tự phân tích của nhân vật.

**Người viết biết nguyên nhân sâu hơn ≠ nhân vật biết nguyên nhân ấy.**  
**Người đọc có thể nhìn thấy một mẫu ≠ nhân vật nhìn thấy mẫu đó.**

Không cho nhân vật phát biểu một phân tích tâm lý chỉ vì người viết đã hiểu chính xác điều đang xảy ra.

## 2. KHÓA TỐI THIỂU TRƯỚC KHI VIẾT — KHÔNG DÙNG CONVERSATION ENGINE BẮT BUỘC

Trước một cuộc thoại đáng kể, chỉ cần khóa đủ để biết những ai đang ở trong tình huống nào:

- ai hiện diện;
- quan hệ và xưng hô đã có hiệu lực;
- điều vừa xảy ra;
- mỗi người biết gì và chưa biết gì;
- trạng thái hoặc áp lực hiện tại ở mức cần thiết;
- việc họ đang làm trong không gian;
- ràng buộc canon hoặc kết quả bắt buộc nếu có.

Không bắt buộc lập các trường:

- mục tiêu giao tiếp;
- nỗi sợ;
- điều giấu;
- hiểu sai;
- rủi ro giao tiếp;
- chiến thuật;
- phản ứng dự kiến nếu người kia không hợp tác.

Những yếu tố này chỉ được dùng khi **thật sự có trong cảnh**. Không tự tạo chúng để làm cuộc thoại có chiều sâu.

### 2.1. Có nguyên nhân không đồng nghĩa có chiến lược

Một câu có thể phát sinh vì người ta:

- muốn hỏi thật;
- cần phối hợp;
- đang bực;
- buột miệng;
- tự vệ quá sớm;
- phản đối;
- muốn sửa chuyện vừa làm hỏng;
- muốn được yên;
- thấy một điều vô lý;
- nhớ ra một chuyện;
- không nhịn được;
- muốn nói tiếp dù chưa biết nói thế nào;
- chỉ đang sống qua một tình huống bình thường.

Không ép mọi lời nói phải có một “mục tiêu giao tiếp” đủ rõ để ghi vào bảng.

## 3. KHÔNG THIẾT KẾ TRƯỚC TỪNG LƯỢT NÓI

Không giao cho từng câu một chức năng như:

- câu này thăm dò;
- câu này phản bác;
- câu này tiết lộ;
- câu này thay đổi quyền lực;
- câu này làm dịu;
- câu này chốt cảm xúc.

Những chức năng ấy có thể được nhận ra **sau khi đoạn thoại đã tồn tại**, nhưng không được dùng như bộ khung bắt nhân vật thực hiện lần lượt.

Không viết câu tiếp theo vì “đến lượt B”.

Không viết câu tiếp theo chỉ vì cấu trúc cảnh cần thêm một phản ứng.

Viết tiếp vì tình huống hiện tại khiến một người cụ thể có lý do tự nhiên để nói hoặc làm điều gì đó.

### 3.1. Tình huống thay đổi trong khi nói

Mỗi lời nói hoặc hành động có thể làm tình huống trở thành một thứ khác đối với những người đang có mặt.

Một người giải thích có thể vô tình khiến người kia thấy mình bị xem thường. Một người tự bảo vệ mình có thể làm lộ ra rằng hai người đã bàn chuyện sau lưng người thứ ba. Một lời đề nghị sửa chữa có thể bị nghe như một lần can thiệp mới.

Không cần người nói dự đoán được hậu quả ấy.

**Hậu quả giao tiếp không cần trùng với ý định giao tiếp.**

### 3.2. Không giải xong cuộc nói chuyện trong đầu người viết trước khi viết

Trước bản nháp, không biến tình huống thành một đường sửa quan hệ đã hoàn tất kiểu:

`kích hoạt cảm xúc → gọi đúng cảm xúc → làm rõ vấn đề thật → hỏi đúng nhu cầu → đặt ranh giới → chấp nhận → đùa nhẹ → còn dư âm`.

Đường như vậy **có thể** xuất hiện nếu chính con người và tình huống dẫn tới nó, nhưng không được dùng làm đích ngầm rồi chỉ thay câu chữ cho tự nhiên hơn.

Nếu trước khi viết người viết đã biết chính xác:

- ai sẽ hiểu ra điều gì;
- ai sẽ nói đúng nguyên nhân sâu;
- câu hỏi nào sẽ mở được “vấn đề thật”;
- ranh giới nào sẽ được thương lượng;
- ai sẽ chấp nhận;
- câu đùa nào sẽ xả áp;
- trạng thái cảm xúc nào sẽ được narrator chốt ở cuối;

thì phải kiểm xem những thứ đó có thật sự bị khóa bởi nhiệm vụ/canon hay chỉ là **một cuộc thoại đã được giải sẵn**. Nếu không bị khóa, bỏ đường giải sẵn và quay về điều các nhân vật thật sự biết ở thời điểm bắt đầu.

**Không dùng R02/R03 như nội dung để nhân vật phát biểu.** Một nhân vật không được nói thành câu chính xác các nguyên tắc như “em chưa biết mình cần gì”, “anh đang cố hiểu ranh giới”, “em chỉ cần được báo trước chứ không cần xin phép” chỉ vì đó là phân tích đúng của người viết. Những câu ấy chỉ giữ khi chính người đó, với giọng và mức tự nhận thức đã thiết lập, thật sự có khả năng nói như vậy lúc ấy.

## 4. TÍNH LIÊN TỤC KHÔNG ĐỒNG NGHĨA PHẢN ỨNG TRỰC TIẾP TỪNG LƯỢT

Một người có thể:

- trả lời câu vừa nghe;
- vẫn mắc ở một câu từ vài lượt trước;
- phản ứng với cách người kia vừa nói hơn là nội dung;
- nhận ra một điều mới từ cuộc trao đổi giữa hai người khác;
- quay lại một chi tiết cũ;
- bỏ qua một phần;
- tiếp tục việc đang làm;
- không nói gì vì chưa có gì họ thật sự muốn nói.

Khi đọc toàn bộ cuộc thoại, phải thấy được vì sao nó đi từ trạng thái đầu tới trạng thái hiện tại. Đường đi có thể vòng, lệch, lặp, dừng giữa chừng hoặc chưa giải quyết xong.

## 5. KHÔNG BẮT BUỘC HỘI THOẠI PHẢI ĐẠT MỤC TIÊU BAN ĐẦU

Một cuộc nói chuyện có thể:

- giải quyết vấn đề thực tế nhưng không giải quyết cảm xúc;
- làm rõ một phần nhưng khiến một phần khác tệ hơn;
- kết thúc vì mọi người phải tiếp tục công việc;
- bị bỏ lại vì không ai biết nói thêm gì;
- chuyển sang chuyện khác trong khi dư âm vẫn còn;
- khiến hai người vẫn hợp tác dù đang giận nhau.

**Hợp tác trở lại không đồng nghĩa hòa giải.**  
**Im chuyện không đồng nghĩa hết cảm xúc.**  
**Xin lỗi không phải nút reset.**

---

# II. SPOKEN REALIZATION — BIẾN CON NGƯỜI THÀNH LỜI NÓI

## 6. LỜI THOẠI KHÔNG PHẢI Ý NGHĨ ĐÃ ĐƯỢC BIÊN TẬP THÀNH VĂN

Sau khi biết một người đang phản ứng thế nào, không được biến phản ứng đó thành phiên bản ngôn ngữ tối ưu nhất.

**Không viết điều nhân vật “nên truyền đạt”. Viết điều con người ấy thực sự có khả năng nói ra vào đúng khoảnh khắc đó.**

### 6.1. Lời nói không phải văn viết được đọc thành tiếng

Trong giao tiếp tự nhiên, nhân vật có thể:

- lặp lại một từ hoặc một phần ý;
- nói hơi vòng;
- thêm một ý sau khi câu đã bắt đầu;
- tự sửa;
- bắt lại đúng từ người kia vừa dùng;
- nói dư một chút khi đang cố làm người kia hiểu;
- dùng những từ nối quen thuộc như “thì”, “mà”, “chứ”, “vậy”, “đâu”, “rồi”, “còn”, “thế” nếu đó là cách họ đang nói;
- diễn đạt chưa hoàn toàn chính xác cảm xúc;
- nói một điều hợp lý nhưng sai thời điểm;
- trả lời theo cách không tối ưu về logic nhưng người đối diện vẫn hiểu được trong ngữ cảnh.

Không tự động biên tập những đặc điểm này thành một câu ngắn hơn, sắc hơn hoặc đẹp hơn.

Nếu một người đang bực và nói vòng vì đang cố tìm cách diễn đạt, không được rút câu chỉ vì phiên bản rút gọn nghe “hay” hơn.

### 6.2. Nhưng không được giả khẩu ngữ

Không cố tình:

- thêm từ đệm;
- lặp từ;
- viết sai cú pháp;
- bỏ chủ ngữ;
- chèn tiếng lóng;
- dùng dấu ba chấm;
- tạo câu dang dở;

chỉ để văn bản có vẻ đời thường.

Nếu một người bình tĩnh và có thể nói rõ, hãy để họ nói rõ. Nếu một ý đơn giản thì một câu đơn giản hoàn toàn hợp lệ.

**Mục tiêu không phải làm lời thoại lộn xộn. Mục tiêu là không làm sạch nó quá mức.**

### 6.3. Không dùng phản hồi mặc định như dấu câu hội thoại

Các từ như `ừ`, `được`, `tốt`, `biết rồi`, `vậy thôi`, `thế nhé`, `anh hiểu`, `em hiểu`, `được rồi` đều có thể hoàn toàn tự nhiên. Lỗi xuất hiện khi chúng được dùng lặp lại như một **token nối lượt** hoặc **dấu đóng nhịp** thay vì là điều người này thật sự muốn nói.

Không cấm từ. Kiểm chức năng.

Nếu nhiều nhân vật liên tục dùng cùng một phản hồi trung tính để:

- xác nhận đã nhận thông tin;
- đóng một tiểu nhịp;
- nhường lượt lại;
- báo rằng tác giả muốn chuyển sang ý kế tiếp;

thì bỏ phản hồi đó nếu không cần, hoặc viết lại thành điều cụ thể mà người ấy sẽ nói/làm.

**Không thay `ừ` bằng `được`, rồi thay `được` bằng `tốt` và coi là đã sửa.**

### 6.4. Một câu trả lời không phải gói thông tin hoàn chỉnh cho độc giả

Khi nhân vật bị hỏi một điều, họ chỉ cần trả lời ở mức người đối diện và khoảnh khắc đó đòi hỏi.

Cảnh đáng nghi khi một câu trả lời tiện tay chứa đủ:

- người kia là ai;
- gặp từ khi nào;
- vì sao gặp;
- quan hệ hiện tại;
- mục đích cuộc gặp;
- trạng thái đạo đức hoặc tình cảm;

chỉ vì độc giả đang cần nền.

Không làm câu trả lời cố ý thiếu dữ kiện. Chỉ không biến nó thành **gói exposition vừa khít**. Người nói có thể trả lời phần bị hỏi, phần họ cho là quan trọng, hoặc phần họ nghĩ người kia đang thật sự muốn biết.

### 6.5. ANTI-AI COMPRESSION — KHÔNG NÉN CON NGƯỜI THÀNH CÁC GÓI CÂU TỐI ƯU

Một lỗi riêng cần chặn là **AI compression**: người viết hiểu trọn ý rồi nén nó thành phiên bản ngắn, sạch, đối xứng và hiệu suất cao hơn lời một con người cụ thể có khả năng nói ra. Lỗi này thường tạo cảm giác “AI” dù từng câu riêng lẻ vẫn đúng ngữ pháp.

Dấu hiệu đáng nghi khi xuất hiện thành cụm:

- nhiều lượt liên tiếp chỉ còn kết luận, mệnh lệnh hoặc nhãn: `Không đủ. / Chưa chắc. / Tiếp tục. / Không cần.`;
- quan hệ nguyên nhân, điều kiện, đối chiếu hoặc mức chắc chắn đã có trong ý nghĩ nhưng bị cắt khỏi lời nói, khiến câu giống ghi chú tác chiến hoặc phụ đề;
- một ý có thể nói thành một câu tự nhiên nhưng bị tách thành hai đến bốn dòng để tạo nhịp sắc/lạnh;
- người chuyên nghiệp, quân nhân, cảnh sát, nhà khoa học, sát thủ hoặc người kín tiếng bị mặc định nói bằng **telegraphese** chỉ vì nghề nghiệp/tính cách;
- nhiều nhân vật cùng có xu hướng trả lời bằng câu tối ưu cực ngắn, sau đó chuyển sang ý kế tiếp không có mối nối;
- câu nào cũng gói đúng “một đơn vị thông tin”, không có phần giải thích thêm, tự sửa, nối ý hoặc phản ứng với cách người kia vừa nói;
- người nói liên tục phát biểu như đang xuất log: `Xác nhận.`, `Giả thuyết.`, `Rủi ro cao.`, `Không khuyến nghị.` dù bối cảnh không phải giao tiếp kỹ thuật/radio thật sự.

**Không sửa bằng cách kéo dài cơ học.** Thêm `tôi nghĩ`, `có lẽ`, `thì`, `mà`, `chứ`, từ đệm hoặc dấu ba chấm không tự làm câu thành lời người. Phải phục hồi đúng cấu trúc mà người đó đang thật sự cần truyền đạt: họ đang trả lời điều gì, phần nào cần nối với phần trước, nguyên nhân nào họ có lý do nói ra, và mức chi tiết nào thuộc về quan hệ/tình huống này.

Một người ít lời có thể vẫn nói câu hoàn chỉnh. Một người lạnh có thể vẫn giải thích. Một người chuyên nghiệp có thể nói rất tự nhiên. **Tiết chế là số điều họ chọn nói; thoại cụt là cách lời bị bẻ gãy. Hai thứ không đồng nghĩa.**

Ngoại lệ thật sự gồm: khẩu lệnh dưới nguy hiểm tức thời, radio cần tiết kiệm băng thông, bị ngắt lời, thiếu hơi, đau nặng, cần phản ứng trong dưới vài giây hoặc shorthand đã được thiết lập rõ giữa những người đang phối hợp. Khi nguyên nhân đó hết, không giữ telegraphese như giọng mặc định.

## 7. NHÂN VẬT KHÔNG TỰ ĐỘNG CÓ TỪ VỰNG HOÀN HẢO VỀ CẢM XÚC

Không mặc định mọi nhân vật đều có khả năng gọi chính xác cơ chế tâm lý đang xảy ra.

Các câu kiểu:

- “Anh đang phủ nhận cảm xúc của em.”
- “Tôi cảm thấy quyền tự quyết của mình bị xâm phạm.”
- “Cậu đang phản ứng phòng vệ.”
- “Tôi đang thiết lập ranh giới.”

chỉ hợp lệ khi vốn sống, cách nói và mức tự nhận thức của nhân vật thật sự hỗ trợ.

Một người có thể chỉ biết nói:

- “Tôi không thích cậu làm thế.”
- “Cậu cứ làm như tôi không tự lo được.”
- “Tôi không biết. Tôi chỉ đang bực.”
- “Tôi không muốn lại thấy cậu như lần trước. Nhìn rất khó chịu.”

Người viết có thể hiểu chính xác cảm xúc. Nhân vật không bắt buộc phải hiểu hoặc gọi đúng tên nó.

### 7.1. Không biến một nhân vật thành người điều phối trị liệu mặc định

Một người có thể giao tiếp rất tốt. Nhưng nếu họ liên tục hỏi đúng những câu như:

- “Vậy điều em cần ở anh là gì?”
- “Vấn đề là chuyện này hay cách anh làm?”
- “Em muốn anh báo trước hay muốn anh xin phép?”
- “Anh nên làm gì để em thấy ổn hơn?”

và mỗi câu hỏi đều mở chính xác tầng tâm lý kế tiếp, phải kiểm lại.

Đó có thể là giọng thật của một người đã được thiết lập là rất giỏi giao tiếp, nhưng không được dùng như **cơ chế sửa hội thoại mặc định**. Người đang bị trách có thể hỏi sai, hỏi quá sớm, hiểu hẹp, tự vệ, chỉ trả lời phần dễ trả lời, hoặc không nghĩ ra câu hỏi hoàn hảo nào cả.

Không cố tình làm nhân vật giao tiếp tệ để tạo drama. Chỉ không nâng họ lên thành chuyên gia điều phối cảm xúc của cảnh nếu canon không hỗ trợ.

## 8. CẢM XÚC PHẢI THAM GIA VÀO GIAO TIẾP, NHƯNG KHÔNG CẦN ĐƯỢC GỌI TÊN

Cảm xúc có thể làm một người:

- nghe sai trọng tâm;
- nghe đúng nội dung nhưng phản ứng với thái độ;
- tự vệ quá sớm;
- giải thích khi không được hỏi;
- nói quá lời;
- lặp lại;
- cố sửa bằng cách không phù hợp;
- hành động trái với điều có lợi ngay lúc đó rồi tự sửa;
- tiếp tục hợp tác trong khi vẫn còn giận;
- không biết mình muốn người kia nói gì.

Không bắt buộc phải có hiểu sai. Không bắt buộc phải có bí mật. Không bắt buộc cảm xúc mạnh nào cũng làm người ta mất lý trí.

Không dùng cau mày, siết tay, thở dài, ánh mắt, im lặng hoặc dấu ba chấm như lớp phủ lên một chuỗi thoại vốn không bị cảm xúc tác động.

## 9. GIỌNG RIÊNG NẰM CẢ TRONG CÁCH TÌM LỜI

Giọng xuất phát từ tuổi, thời đại, xuất thân, học vấn, nghề nghiệp, kinh nghiệm, tính cách, trạng thái và quan hệ.

Phân biệt bằng:

- vốn từ và cấu trúc câu;
- mức trực tiếp;
- họ giải thích trước hay nhận lỗi trước;
- khi bị trách họ hỏi lại, im, cãi hay cố sửa bằng hành động;
- họ bắt chữ hay bỏ qua câu chữ để nói về hậu quả;
- họ nói kết luận trước hay nguyên nhân trước;
- họ có nói dài hơn khi lo lắng hay ngắn hơn;
- họ có dễ gọi tên cảm xúc hay thường chỉ nói về việc xảy ra;
- họ có tiếp tục nói khi người kia khó chịu hay biết dừng;
- họ có cố giải quyết vấn đề ngay hay chịu được việc vấn đề chưa được giải quyết;
- cách giọng đổi theo người đối diện.

Không dùng những đặc điểm này như một bộ công thức bất biến. Cùng một người vẫn có thể nói khác khi hoàn cảnh khác.

Không phân biệt chỉ bằng câu cửa miệng, chửi thề, phương ngữ phóng đại, dấu ba chấm hoặc mức độ cộc.

### 9.1. Cấm một giọng dí dỏm của tác giả chạy qua nhiều nhân vật

Hai nhân vật có thể cùng hài hước nhưng **không mặc định hài theo cùng cơ chế**.

Cảnh đáng nghi khi nhiều người đều thường xuyên:

- bắt đúng một chữ của người kia rồi bẻ lại;
- đáp bằng literalism khô;
- đưa ra một câu phản bác vừa chính xác vừa dí dỏm;
- dùng cùng nhịp `mệnh đề nghiêm túc → cú lật nhẹ`;
- có câu “đắt” đúng lúc căng thẳng cần hạ;
- thắng một lượt bằng cùng loại thông minh ngôn ngữ.

Đó thường là **wit của người viết phân phối cho nhiều cái miệng**, không phải voice.

Khi audit, đừng chỉ hỏi “câu này có đúng tính cách không?”. Hỏi thêm: **người khác trong cảnh có đang liên tục được viết thông minh theo đúng cùng một cách không?**

---

# III. HAI NGƯỜI VÀ NHIỀU NGƯỜI — KHÔNG CHIA LƯỢT

## 10. VỚI HAI NGƯỜI

Không mặc định:

`A nói → B phản ứng → A phản ứng → B phản ứng`.

Một người có thể nói hai lần gần nhau nếu người kia vừa làm hoặc nói một điều khiến họ cần tiếp tục. Một người có thể không đáp câu hỏi mà quay lại một câu trước đó. Một người có thể tiếp tục việc đang làm thay vì nói. Một người có thể nghe đúng nội dung nhưng phản ứng với thái độ.

Không ép lượt nói luân phiên chỉ để văn bản trông cân bằng.

## 11. VỚI BA NGƯỜI TRỞ LÊN

Không coi cuộc thoại là:

`A → B → C → A`

hoặc:

`A → B → C → D → A`.

Không có khái niệm “đã lâu rồi nhân vật này chưa nói nên cần cho họ một câu”.

Một người lên tiếng khi họ có lý do thực tế để lên tiếng. Lý do có thể đơn giản:

- tên họ vừa bị nhắc;
- họ bị kéo vào trách nhiệm;
- điều vừa nói ảnh hưởng trực tiếp tới họ;
- họ biết một chi tiết mà những người khác đang bỏ sót;
- họ thấy ai đó đang đi quá xa;
- họ muốn bảo vệ mình;
- họ không đồng ý;
- họ muốn giúp;
- họ cần tiếp tục công việc;
- họ nghe một điều khiến cách họ hiểu tình huống thay đổi.

Người thứ ba không mặc định là:

- trọng tài;
- người hòa giải;
- người nói điều hợp lý nhất;
- người giải thích một người cho người kia;
- comic relief;
- người tổng kết.

Vai trò của họ có thể thay đổi ngay trong cùng một cuộc nói chuyện vì tình huống thay đổi.

### 11.1. Một câu có nhiều người nhận

A có thể nói với B nhưng C cũng nghe thấy.

B có thể bị xúc phạm. C có thể nhận ra một dữ kiện mới. D có thể chỉ thấy kế hoạch vừa có vấn đề.

Không đồng bộ phản ứng của cả nhóm. Không bắt cả nhóm cùng hiểu một điều ở cùng một thời điểm.

### 11.2. Người im lặng vẫn tồn tại

Nếu một người không có lý do nói, để họ không nói.

Không chèn ánh mắt, cái nhíu mày, sự im lặng hoặc động tác nhỏ sau mỗi câu chỉ để chứng minh họ vẫn còn trong phòng.

Chỉ đưa họ trở lại mặt chữ khi việc họ làm hoặc không làm có ý nghĩa trong tình huống.

---

# IV. GUARDRAILS — CỔNG CẤM KHI SINH THOẠI

## 12. DÀN Ý KHÔNG PHẢI KỊCH BẢN HỘI THOẠI

Dàn ý, continuity, codex, knowledge state và danh sách sự kiện xác định điều phải đúng hoặc phải xảy ra. Chúng không xác định nhân vật phải phát biểu các bullet đó.

Nghiêm cấm:

`bullet dàn ý → chọn một nhân vật hỏi → nhân vật khác phát biểu bullet → chuyển sang bullet kế tiếp`.

Sự kiện khóa là đích. Những con người trong cảnh tự tạo đường đi.

## 13. CẤM CÂU HỎI MỒI THÔNG TIN

Không cho nhân vật hỏi chỉ để người khác có cớ giải thích cho độc giả.

Một câu hỏi chỉ cần tự nhiên với người hỏi và tình huống. Khi nghi ngờ nó là exposition, kiểm:

- người hỏi có thật sự chưa biết không;
- họ có lý do thật để hỏi lúc này không;
- câu trả lời có ý nghĩa đối với họ không;
- cách hỏi có thuộc về người này không.

Không bắt mọi câu hỏi phải “mua” một quyết định lớn. Một câu hỏi đời thường có thể chỉ để biết, xác nhận, nghe lại hoặc tiếp tục một việc nhỏ.

## 14. NHÂN VẬT KHÔNG ĐƯỢC TRỞ THÀNH GIAO DIỆN CANON

Knowledge state, continuity, nhãn VERIFIED/INFERENCE, tên hệ thống và giới hạn nguồn là công cụ hậu trường. Chúng không tự động trở thành cách nói của nhân vật.

Nhân vật chỉ nói giới hạn tri thức khi một con người cụ thể trong cảnh thật sự có lý do cần nói điều đó. Nếu giới hạn có thể được giữ bằng POV, trần thuật hoặc đơn giản là không hành động quá mức, không ép nó vào lời thoại.

## 15. THÔNG TIN KHÔNG TỰ ĐỘNG TRỞ THÀNH LỜI

Nhân vật không tự động nói hết điều họ biết.

Không được:

- giải thích điều đôi bên đều biết chỉ cho độc giả;
- kể lại lịch sử chung cho nhau;
- tự thú toàn bộ suy nghĩ khi không có lý do;
- phân chia báo cáo thành nhiều người nói luân phiên;
- nói với độ chắc chắn cao hơn mức họ thật sự biết.

Thông tin có thể xuất hiện vì cảnh báo, phối hợp, phản bác, tò mò, xin phép, kiểm tra, tự vệ, sửa hiểu lầm, kể chuyện, than phiền, giải thích hoặc một nhu cầu thực tế khác. Không giới hạn hội thoại vào danh sách chức năng cứng.

## 16. XƯNG HÔ, QUAN HỆ VÀ KHOẢNG CÁCH GIAO TIẾP

Xưng hô là dữ kiện quan hệ, không phải đồ trang trí câu văn. Đúng đại từ không bảo đảm câu thoại tự nhiên; sai đại từ vẫn là lỗi continuity/quan hệ dù câu thoại nghe hay.

### 16.1. Thẩm quyền và nguồn xác định

Khi xác định một cặp xưng hô, ưu tiên:

1. yêu cầu mới nhất của người dùng;
2. hồ sơ nhân vật hoặc khóa CURRENT/HARD LOCK đang có hiệu lực;
3. quan hệ, tuổi, vai vế, văn hóa, chức vụ và hoàn cảnh đã được canon hóa;
4. continuity/chương gần nhất nếu cách gọi đã được thiết lập trong truyện;
5. quy tắc ngôn ngữ và xã hội hợp lý của bối cảnh.

Không dùng tri thức hậu trường để chọn cách gọi mà nhân vật chưa có cơ sở biết. Nếu dữ kiện quyết định chưa đủ, không tự gán một cặp cố định rồi biến nó thành canon; có thể dùng câu không cần đại từ hoặc giữ trạng thái `OPEN` cho tới khi đủ căn cứ.

### 16.2. Giữ ổn định và điều kiện đổi xưng hô

Khi hai nhân vật đã xác lập cách xưng hô trong một cảnh, mặc định giữ ổn định. Không đổi chỉ để tránh lặp, làm câu lạnh hơn, tăng căng thẳng, tạo vẻ bí ẩn/ngầu, thể hiện giận/ghen/bất đồng hoặc làm quan hệ có vẻ thân hơn thực tế.

Chỉ đổi khi có nguyên nhân quan hệ thật, chẳng hạn:

- biết được tuổi hoặc vai vế trước đó chưa rõ;
- phát hiện quan hệ gia đình;
- một người chủ động đề nghị cách gọi mới;
- quan hệ thân thiết hoặc xa cách thay đổi rõ;
- chức vụ, địa vị hoặc bối cảnh nghi lễ thay đổi;
- một biến cố làm cách hai người nhìn nhau thực sự thay đổi.

Nếu đổi, cảnh phải có cầu nối đủ để người đọc hiểu nguyên nhân. Giận dữ, ghen, cứu mạng, chiến đấu ăn ý, tán tỉnh, hôn hoặc tiếp xúc cơ thể không tự động tạo một hệ xưng hô mới.

### 16.3. Chọn xưng hô khi chưa có khóa riêng

Cân nhắc đồng thời:

- tuổi biểu kiến mà người nói có thể quan sát;
- giới tính nếu thực sự nhận biết được và có ý nghĩa trong ngôn ngữ sử dụng;
- vai trò hoặc chức danh đã biết;
- mức thân sơ;
- quan hệ gia đình hoặc thứ bậc;
- thời đại, vùng miền và nền văn hóa;
- chênh lệch quyền lực trong tình huống;
- cách người kia vừa tự xưng hoặc gọi mình.

Không dùng một đại từ duy nhất cho mọi đối tượng. Cách gọi phải phản ánh quan hệ cụ thể, không phải thói quen của người viết.

### 16.4. Tên riêng và khẩu lệnh

Gọi tên trực tiếp là bình thường, nhưng tên riêng không thay thế hệ xưng hô. Khi câu có đại từ, đại từ vẫn phải đúng quan hệ.

Trong nguy hiểm tức thời, nhân vật có thể lược đại từ để truyền lệnh nhanh. Đây là rút gọn do tình huống, không phải thay đổi quan hệ. Khi áp lực qua đi, lời nói trở lại nhịp và hệ xưng hô tự nhiên đã có hiệu lực.

### 16.5. Người lạ và nhân vật vô danh

Không biết tên không có nghĩa là không có quan hệ xã hội. Có thể dùng cách gọi theo tuổi/vai vế như `anh / chị / cô / chú / bác / ông / bà`, chức danh hoặc vai trò đã biết, tên sau khi được giới thiệu, hoặc câu không cần đại từ nếu tuổi, giới tính hay vai vế chưa chắc chắn.

Không dùng nhãn hậu trường như `NPC`, `nhân vật phụ`, `người số một`, `người số hai` trong lời thoại. Trong lời kể, nhận diện người vô danh bằng vai trò hoặc dấu hiệu cảnh khi cần.

### 16.6. Gia đình, vai vế, văn hóa và thời đại

Trong quan hệ gia đình, vai vế thường quan trọng hơn tuổi biểu kiến. Không áp một hệ xưng hô hiện đại duy nhất cho mọi thời đại, vùng miền hoặc cộng đồng. Nếu bối cảnh có hệ xưng hô đặc thù, phải tra cứu và khóa hệ phù hợp trước khi viết.

Không rắc từ cổ, phương ngữ hoặc từ ngoại ngữ chỉ để tạo màu. Cách gọi phải thuộc về nhân vật và cộng đồng cụ thể.

### 16.7. Thân mật, tình cảm và khoảng cách

Thân mật không chỉ thể hiện bằng biệt danh. Nó có thể nằm trong điều hai người dám nói với nhau, mức họ cho phép người kia can thiệp, cách họ tranh cãi, việc họ nhớ chi tiết về nhau, khoảng riêng tư được chia sẻ và mức dễ tổn thương họ chấp nhận bộc lộ.

Không tự nâng quan hệ chỉ vì một cảnh có cứu mạng, tán tỉnh, ghen, hôn hoặc tiếp xúc cơ thể. Cách gọi thân mật chỉ xuất hiện khi quan hệ thực tế đã đạt tới mức đó hoặc canon khóa rõ. Không dùng biệt danh tình cảm như dấu câu; mật độ phải phù hợp tính cách, thói quen và nhịp cảnh.

### 16.8. Nhóm và số nhiều

Khi nói với nhiều người, ưu tiên cách gọi tự nhiên theo tình huống như `mọi người`, `các anh/chị`, `bên anh`, `nhóm của chị`, `chúng ta`, `chúng tôi` hoặc cách tương đương phù hợp bối cảnh. Không liệt kê từng đại từ của từng người nếu người thật trong hoàn cảnh đó sẽ nói gọn hơn.

### 16.9. Khóa một cặp mới thành canon

Trước khi ghi một cặp thành canon, xác định:

- A tự xưng gì với B và gọi B bằng gì;
- B tự xưng gì với A và gọi A bằng gì;
- cặp này là mặc định, mềm hay khóa cứng;
- điều kiện nào cho phép đổi;
- có ngoại lệ trong nghi lễ, tác chiến, gia đình hoặc nơi công cộng không;
- cặp này đã thực sự xuất hiện trong nội dung hay mới là đề xuất hậu trường.

Nếu chưa đủ dữ kiện, giữ `OPEN` thay vì đoán.

## 17. CẤM THOẠI CỤT ĐỂ TẠO KHÍ CHẤT

Không đánh đồng câu ngắn với câu cụt.

Phải viết lại khi:

- một ý đáng lẽ nói liền bị chặt thành nhiều mẩu;
- chủ ngữ hoặc mối nối bị lược khiến câu giống phụ đề hơn lời người;
- nhiều nhân vật cùng nói cộc theo một nhịp;
- chuỗi câu cực ngắn tồn tại chỉ để tạo vẻ lạnh, quân nhân, sát thủ, bí ẩn hoặc “điện ảnh”;
- cử chỉ, dấu ba chấm hoặc xuống dòng đang che một câu thiếu ý;
- câu nửa vời, khẩu hiệu hoặc tối nghĩa chỉ để nghe sắc.

Câu rất ngắn có thể đúng khi có nguyên nhân trực tiếp: khẩu lệnh, cảnh báo tức thời, bị ngắt lời, khó thở, bị thương, hoảng loạn, gọi tên, xác nhận tốc độ cao hoặc lối nói tắt đã được thiết lập.

Khi áp lực kết thúc, lời nói trở lại nhịp tự nhiên.

### 17.1. KÍN TIẾNG / LẠNH / CHUYÊN NGHIỆP KHÔNG ĐỒNG NGHĨA “NÓI NHƯ ĐIỆN TÍN”

Không dùng các nhãn tính cách hoặc nghề nghiệp như `kín tiếng`, `lạnh`, `ít lời`, `quân nhân`, `cảnh sát`, `đặc vụ`, `nhà nghiên cứu`, `sát thủ`, `lãnh đạo` làm giấy phép để mặc định cắt lời thành mảnh.

Ví dụ lỗi cơ chế:

`— Không đủ dữ kiện.`  
`— Chưa kết luận.`  
`— Tiếp tục quan sát.`

Ba câu trên có thể hợp trong radio hoặc một nhịp thao tác rất gấp. Nếu đây là trao đổi bình thường, chúng dễ biến người nói thành bảng trạng thái. Tùy con người và tình huống, một lời nói tự nhiên hơn có thể giữ nguyên sự chính xác mà vẫn có cấu trúc:

`— Chưa đủ để kết luận. Mình mới biết nó đổi vị trí sau khi mất tầm nhìn; còn nguyên nhân thì chưa có bằng chứng.`

Đây **không phải mẫu câu để sao chép**. Điều cần học là một người chuyên nghiệp vẫn có thể nối quan sát, giới hạn và lý do thành một phát ngôn sống; không cần bẻ ý thành các thẻ thông tin.

Khi một Character Codex ghi “nói ít”, phải hiểu mặc định là **không phát biểu phần không cần thiết**, không phải `rút mọi phát ngôn xuống số từ tối thiểu`. Nếu codex thật sự muốn một lối nói điện tín, nó phải khóa rõ đó là đặc trưng riêng và chỉ ra phạm vi sử dụng.

## 18. CẤM HỘI THOẠI QUÁ ĐƯỢC THIẾT KẾ

Không biến mọi câu thành câu đắt, subtext, đấu quyền lực, đọc vị tâm lý hoặc một dấu vân tay phô diễn tính cách.

Cảnh đời thường có thể đơn giản. Cảnh hợp tác có thể trả lời thẳng. Người nghe không cần luôn né, hỏi ngược hay bẻ tiền đề.

Không chèn hiểu sai, bí mật, ghen, quyền lực, banter hoặc căng thẳng chỉ vì một checklist nói rằng hội thoại cần những thứ ấy.

Đặc biệt cảnh xung đột/thân mật phải bị kiểm lại nếu nó quá đều theo đường:

`phát hiện vấn đề → thừa nhận cảm xúc → xác định vấn đề sâu hơn → hỏi đúng nhu cầu → thương lượng ranh giới → chấp nhận → một câu đùa → narrator xác nhận dư âm`.

Đường này không bị cấm. Nhưng nếu từng bước đến đúng lúc và từng người đều nói đúng phần cần thiết để bước kế tiếp xảy ra, cảnh có thể đang là **một phiên xử lý quan hệ được dàn dựng**, không phải cuộc nói chuyện.

Một cuộc thoại không cần tự tạo lỗi để “lộn xộn hơn”. Chỉ cần không ép nó phải hoàn thành một đường chữa trị sạch.

## 19. CẤM ẨN DỤ RỖNG, TRIẾT LÝ GIẢ VÀ CÂU MƠ HỒ

Không giữ câu chỉ vì nghe sâu, lạnh hoặc đáng trích dẫn.

Ẩn dụ chỉ nên tồn tại khi nó làm rõ cảm xúc, gắn với trải nghiệm riêng, tạo hình ảnh cần thiết hoặc nén một ý mà người nghe thực sự có thể hiểu.

---

# V. DIALOGUE AUDIT — CHỈ DÙNG SAU KHI CUỘC THOẠI ĐÃ TỒN TẠI

## 20. NGUYÊN TẮC AUDIT

Audit không phải công thức sinh thoại.

Không quay ngược các câu hỏi audit thành các ô bắt nhân vật phải điền khi viết.

Không yêu cầu từng câu:

- phải có mục tiêu;
- phải đổi trạng thái;
- phải có subtext;
- phải thể hiện giọng riêng rõ rệt;
- phải khiến người nghe phản ứng trực tiếp ngay lượt sau.

Đọc cả cuộc nói chuyện và hỏi xem những con người này có thật sự đang sống qua cùng một tình huống hay không.

## 21. DIALOGUE ISOLATION AUDIT

Tạm sao chép phần hội thoại của cảnh thành dạng gần như kịch bản:

- giữ nhãn người nói;
- giữ gián đoạn thật sự cần thiết như bị cắt lời, khó thở, nguy hiểm tức thời hoặc hành động làm thay đổi trực tiếp nghĩa câu;
- tạm bỏ phần lớn cử chỉ, ánh mắt, thẻ cảm xúc, trạng từ và lớp văn xuôi trang trí khỏi bản kiểm.

Sau đó đọc riêng xương lời và hỏi:

1. Khi không còn cử chỉ che đỡ, câu có còn là lời người này có thể phát ra không?
2. Có đoạn nào biến thành báo cáo, biểu mẫu hoặc chuỗi xác nhận không?
3. Có câu nào chỉ nghe hay vì narration đang gánh phần nghĩa bị thiếu không?
4. Có câu hỏi nào lộ rõ là câu hỏi mồi không?
5. Bỏ tên người nói, cả đoạn còn cho cảm giác những người khác nhau không?

Isolation Audit không yêu cầu lời thoại tự chứa toàn bộ bối cảnh.

## 22. WHO-KNOWS-WHAT TEST

Kiểm mọi câu trong đó một người phát biểu về nội tâm, kiến thức, ý định hoặc giới hạn của người khác.

Hỏi:

- họ biết điều này bằng cách nào;
- họ đang biết, đoán, tin hay chỉ cảm thấy;
- họ có đang nói với độ chắc chắn cao hơn dữ kiện cho phép không;
- tác giả có đang đưa phân tích của mình vào miệng nhân vật không.

Nếu người viết biết nhiều hơn nhân vật, giữ khoảng cách đó.

## 23. HUMAN CONTINUITY TEST

Không bắt từng lượt phản ứng trực tiếp lượt ngay trước.

Đọc toàn đoạn và kiểm:

- điều vừa xảy ra có thật sự ảnh hưởng đến cách những người trong cảnh tiếp tục sống qua tình huống không;
- có ai đang nói vì đến lượt không;
- có ai đột nhiên hiểu hoàn toàn bản thân hoặc người khác chỉ vì tác giả cần cảnh tiến nhanh không;
- một người có thể vẫn phản ứng với câu cũ, thái độ, hành động hoặc điều vừa nhận ra không;
- cảm xúc có bị reset khi chủ đề chuyển không.

Nếu toàn đoạn có thể tráo nhiều lượt mà hầu như không đổi trải nghiệm, cuộc thoại có nguy cơ bị lắp ghép.

## 24. SPOKEN-LANGUAGE TEST

Đọc thành tiếng.

Không tự sửa chỉ vì câu:

- hơi dài;
- hơi lặp;
- có từ nối;
- không tối ưu;
- có thể rút ngắn.

Chỉ sửa nếu nó:

- không thuộc về người đang nói;
- khó hiểu ngoài ý muốn;
- bị văn viết hóa;
- bị làm lộn xộn giả tạo;
- hoặc không còn giống tiếng Việt mà người này có thể nói trong hoàn cảnh đó.

## 24A. DEFAULT-RESPONSE TEST

Quét các phản hồi ngắn hoặc trung tính lặp lại, đặc biệt `ừ / được / tốt / biết rồi / hiểu rồi / vậy thôi / thế nhé` và các biến thể tương đương.

Không đếm từ để cấm. Hỏi:

- từ này có mang sắc thái hoặc nội dung riêng của người nói không;
- nếu xóa nó, cảnh có mất gì ngoài việc báo “đã nhận lượt” không;
- nhiều nhân vật có đang dùng cùng một từ để đóng nhịp không;
- người viết có đang dùng nó như dấu chấm câu cho Conversation Engine vô hình không.

Nếu đúng, sửa **chức năng nối lượt**, không chỉ thay bằng một từ đồng nghĩa.

## 24B. EXPOSITION-PACKAGE TEST

Với một câu trả lời chứa nhiều nền, hỏi:

- người đối diện vừa hỏi thật sự điều gì;
- người nói cần trả lời tới đâu;
- phần thông tin còn lại có phải chủ yếu dành cho độc giả không;
- người nói có lý do tự nhiên để gom mọi mảnh nền vào một lượt không.

Nếu câu trả lời vừa khít như một hồ sơ tóm tắt, viết lại từ nhu cầu của người trong cảnh.

## 24C. RELATIONSHIP-PROCESSING TEST

Trong xung đột/thân mật, kiểm xem một hoặc cả hai người có đang giao tiếp như họ đã đọc bản phân tích của cảnh không.

Dấu hiệu:

- gọi tên đúng cảm xúc quá sớm rồi tiếp tục gọi đúng mọi lớp sau;
- hỏi đúng câu làm rõ ranh giới ở mỗi bước;
- diễn đạt chính xác khác biệt giữa `xin phép / báo trước / quyền tự quyết / nhu cầu được biết`;
- lời xin lỗi hoặc trấn an được cấu trúc hoàn chỉnh hơn mức người đó thường nói;
- người kia tiếp nhận đúng và trả lời đúng phần cần thiết để cuộc thương lượng tiến tiếp.

Không làm họ ngu hoặc cố hiểu sai. Chỉ trả mức tự nhận thức và kỹ năng giao tiếp về đúng con người đã được thiết lập.

## 25. MULTI-PARTY TEST

Với ba người trở lên, kiểm:

- có đang chia lượt đều không;
- người thứ ba có bị biến thành trọng tài/comic relief/máy tổng kết không;
- một câu có thể tác động khác nhau tới nhiều người không;
- người im lặng có được phép im mà không bị chèn cử chỉ liên tục không;
- người lên tiếng có lý do từ tình huống chứ không phải vì “đến lượt”.

## 26. VOICE SWAP TEST

Bỏ tên người nói và thử đổi lời cho nhân vật khác.

Nếu phần lớn cuộc thoại vẫn chạy mà chỉ cần đổi đại từ, giọng chưa đủ riêng.

Không sửa bằng câu cửa miệng. Sửa bằng cách mỗi người chú ý, tìm lời, tự vệ, giải thích, xin lỗi, từ chối, bắt chữ, bỏ qua, chịu đựng khoảng chưa giải quyết và thay đổi theo quan hệ.

## 26A. SHARED-AUTHORIAL-WIT TEST

Tạm bỏ tên và nội dung riêng của tình huống, chỉ nhìn **cơ chế tạo câu**.

Nếu nhiều nhân vật cùng:

- bắt chữ theo cùng cách;
- phản bác bằng cùng kiểu literalism;
- tạo joke bằng cùng nhịp;
- đưa ra câu cân đối, sắc và dễ trích dẫn giống nhau;

thì voice có thể đang bị một giọng tác giả phủ lên.

Không chữa bằng từ lóng hoặc câu cửa miệng. Sửa cách mỗi người chú ý, chịu áp lực, tìm từ và quyết định có đáng nói hay không.

## 27. FRAGMENT CLUSTER TEST

Số từ không phải tiêu chuẩn chất lượng độc lập.

Chỉ coi là lỗi khi có cụm dấu hiệu: ý bị bẻ vụn, mối nối biến mất, nhiều người cùng cộc, câu chỉ để tạo khí chất hoặc áp lực đã hết nhưng lời vẫn như khẩu lệnh.

Không nối thêm từ rỗng để vượt ngưỡng đếm.

## 27A. TELEGRAPHIC / AI-COMPRESSION TEST

Sau khi đã có bản thoại, quét riêng các cụm có ba lượt trở lên và hỏi:

1. Có phải mỗi lượt đang bị nén thành một kết luận hoặc một đơn vị thông tin quá sạch không?
2. Có mối nối tự nhiên nào đã bị bỏ chỉ để câu nghe lạnh, nhanh hoặc “đúng chất” hơn không?
3. Người này ít lời vì họ **không muốn nói thêm**, hay vì người viết đang cắt bỏ cấu trúc lời nói?
4. Nếu ghép các mảnh lại thành một câu bình thường, ý có tự nhiên hơn mà vẫn đúng giọng không? Nếu có, bản hiện tại có thể đang bị fragment hóa.
5. Khi đổi sang cảnh không khẩn cấp, nhân vật có vẫn nói cùng nhịp điện tín không? Nếu có mà codex không khóa rõ, phải kiểm lại.

Không dùng số từ làm ngưỡng. Một câu hai từ có thể hoàn toàn đúng; một câu mười từ vẫn có thể là telegraphese nếu nó chỉ là nhãn thông tin được kéo dài.

Cách sửa: quay về phát ngôn mà người này thật sự muốn thực hiện trong khoảnh khắc đó, phục hồi mối nối và lượng giải thích cần thiết; **không chèn filler để kéo dài câu**.

## 28. STOPPING TEST

Cuộc thoại có thể dừng khi:

- không còn điều gì người trong cảnh thật sự muốn nói lúc này;
- công việc hoặc hoàn cảnh chen vào;
- vấn đề thực tế đã đủ để hành động;
- hai bên chưa giải quyết được cảm xúc nhưng không tiếp tục nữa;
- một người từ chối nói thêm;
- mọi người chuyển sang làm việc khác.

Không cần câu chốt.

Không được:

- cắt ngay sau một câu nặng trước khi người nghe có bất kỳ phản ứng hợp lý nào;
- kéo thêm vòng giải thích chỉ để chứng minh hai bên đã hiểu;
- ép hòa giải;
- kết bằng câu đinh không phục vụ người trong cảnh.

## 28A. ENDING-SUMMARY TEST

Sau câu thoại cuối hoặc hành động cuối, kiểm phần narration kết cảnh.

Đáng nghi khi narrator lập tức chốt hộ:

- nhân vật giờ đã hiểu mình đang cảm thấy gì;
- vấn đề thật sự là gì;
- quan hệ đã thay đổi ra sao;
- họ đã đạt một state change nào;

trong khi cảnh vừa đủ cho người đọc tự cảm nhận.

Không cấm nội tâm trực tiếp ở cuối cảnh. Chỉ bỏ **bản tổng kết luận đề** được thêm để chứng minh cảnh đã có tiến triển. Dư âm có thể nằm trong một việc nhỏ chưa xong, một lựa chọn tiếp tục, một câu còn mắc lại hoặc đơn giản là trạng thái chưa được giải thích sạch.

## 29. BENCHMARK OBSERVATION TEST

Benchmark là dữ liệu quan sát, không phải template sinh câu.

Không học benchmark bằng:

- số lượt;
- thứ tự phản ứng;
- vị trí lời xin lỗi;
- thời điểm trêu;
- thời điểm chuyển chủ đề;
- cách kết thúc;
- cấu trúc sự kiện.

Sau khi đọc benchmark, chỉ quan sát:

- mỗi người đã sống cùng tình huống khác nhau thế nào;
- điều gì vô tình làm cuộc nói chuyện đổi hướng;
- ai hiểu gì trước hoặc sau;
- ai vẫn chưa hiểu khi cuộc thoại dừng;
- cách lời nói thực tế khác với phân tích của người viết;
- cách nhiều trạng thái cảm xúc cùng tồn tại.

Không lấy một hiện tượng xuất hiện trong một benchmark rồi biến nó thành quy tắc bắt buộc.

---

# VI. REJECT GATE — LỖI CẤU TRÚC THẬT SỰ

Một cuộc thoại không đạt nếu có một hoặc nhiều lỗi sau ở mức đáng kể:

1. Dàn ý hoặc continuity bị chuyển trực tiếp thành hỏi–đáp.
2. Có câu hỏi mồi chỉ để mở exposition.
3. Nhân vật trở thành giao diện canon hoặc nói bằng phân tích hậu trường mà họ không có.
4. Chuỗi hỏi đúng → đáp đủ → hỏi tiếp → đáp tiếp vận hành như biểu mẫu.
5. Nhân vật nói vì đến lượt, đặc biệt trong hội thoại nhóm.
6. Người thứ ba bị cố định thành trọng tài, comic relief hoặc máy tổng kết mà không có nguyên nhân từ con người ấy.
7. Cảm xúc chỉ nằm ở cử chỉ, còn cách nghe, nói và hành động không bị tác động.
8. Nhân vật liên tục phát biểu phiên bản chính xác nhất, gọn nhất, hợp lý nhất của suy nghĩ dù không phù hợp con người hoặc trạng thái.
9. Lời thoại bị văn viết hóa hoặc cố làm khẩu ngữ bằng từ đệm, lỗi cú pháp, tiếng lóng và câu vụn giả tạo.
10. Nhiều nhân vật có cùng nhịp cộc hoặc cùng giọng sắc/lạnh.
11. Ý bị bẻ thành mảnh phụ đề để tạo khí chất.
12. Nhân vật nói điều họ không có nguồn biết hoặc xưng hô sai canon.
13. Cuộc thoại cố quá mức để mọi câu đều sắc, có subtext, đọc vị hoặc “đúng tính cách”.
14. Vấn đề cảm xúc bị reset chỉ vì vấn đề thực tế đã được xử lý.
15. Điểm kết bị cắt cụt hoặc kéo dài chỉ để có câu chốt.
16. Phản hồi trung tính như `ừ / được / tốt / hiểu rồi` bị dùng lặp như token nối lượt hoặc dấu đóng nhịp ở nhiều nhân vật.
17. Một nhân vật liên tục hỏi đúng câu để phân loại cảm xúc, nhu cầu và ranh giới, khiến họ thành người điều phối trị liệu của cảnh mà canon không hỗ trợ.
18. Nhiều nhân vật chia sẻ cùng một kiểu dí dỏm, bắt chữ, literalism hoặc câu phản bác sắc đến mức nghe như cùng một người viết đang nói.
19. Câu trả lời chứa đúng một gói nền hoàn chỉnh cho độc giả dù người đối diện không cần toàn bộ thông tin đó.
20. Narration cuối cảnh tóm tắt “nhân vật đã hiểu gì / quan hệ đã đổi gì” chỉ để đóng state change mà cảnh không cần nói hộ.
21. Người kín tiếng/chuyên nghiệp bị biến thành chuỗi phát ngôn kiểu log, phụ đề hoặc điện tín; mối nối và lượng giải thích tự nhiên bị cắt chỉ để câu nghe lạnh, nhanh hoặc “AI-gọn”.

### 30. Cách sửa khi lỗi là cấu trúc

Không sửa bằng:

- kéo dài câu;
- thêm đại từ;
- thêm từ đệm;
- chèn cử chỉ;
- đổi vài từ;
- cho người nghe né hoặc hỏi ngược ngẫu nhiên;
- thêm một hiểu lầm chỉ để làm cảnh có cảm xúc.

Nếu lỗi nằm ở việc người viết đã phân lượt, phát biểu thay nhân vật hoặc biến nội tâm thành câu tối ưu, phải quay về **con người + tình huống hiện tại** rồi viết lại.

Không quay về một Interaction Map bắt buộc.

---

# VII. TRÌNH BÀY VÀ CỔNG CUỐI

## 31. THẺ THOẠI VÀ TRÌNH BÀY

- Ưu tiên “nói”, “hỏi”, “đáp” hoặc bỏ thẻ khi người nói đã rõ.
- Chỉ dùng động từ phát âm mạnh khi hành vi thật sự phù hợp.
- Không gắn trạng từ cảm xúc nếu nội dung và phản ứng đã đủ.
- Giữ quy ước trình bày của bản thảo.
- Không xuống dòng liên tục chỉ để giả nhịp điện ảnh.

## 32. CỔNG CUỐI TRƯỚC KHI XUẤT BẢN

Chỉ giữ bản cuối khi:

- con người và tình huống đã được đọc đúng nguồn;
- nhân vật không nói vì nhu cầu giải thích của tác giả;
- dàn ý không bị đọc thành lời;
- không có câu hỏi mồi hoặc canon mouthpiece nổi bật;
- người viết không phát biểu thay mức tự nhận thức của nhân vật;
- lời nói nghe như lời người ấy có thể thực sự thốt ra, không như ý nghĩ đã được biên tập;
- câu ngắn là câu tự nhiên, không phải mảnh phụ đề;
- nhân vật ít lời/chuyên nghiệp không bị mặc định nói như log hoặc điện tín; nếu nhịp bị nén, phải có nguyên nhân tình huống hoặc khóa giọng riêng;
- cảm xúc tham gia vào cách nghe, nói hoặc hành động nhưng không bị ép thành hiểu sai hoặc subtext;
- hội thoại nhiều người không bị chia lượt hoặc phân vai chức năng;
- một vấn đề có thể còn mở nếu con người trong cảnh chưa giải quyết được;
- phản hồi trung tính không bị dùng như dấu câu hoặc nhịp chuyển lượt;
- không có một nhân vật vô cớ trở thành người liên tục hỏi đúng câu để xử lý quan hệ;
- các nhân vật không cùng dí dỏm theo một cơ chế của tác giả;
- câu trả lời không đóng gói exposition vượt nhu cầu của người trong cảnh;
- narration cuối không giải thích sạch điều cảnh vừa cho thấy chỉ để chốt state change;
- điểm dừng thuộc về trạng thái của người trong cảnh, không thuộc về nhu cầu tạo câu đinh;
- audit được dùng để phát hiện lỗi sau bản nháp, không dùng ngược thành công thức sinh thoại.

**Nguyên tắc cuối:** nhân vật không tồn tại để thực hiện chức năng của lượt thoại. Họ tồn tại trong tình huống; lời thoại là thứ phát sinh khi những con người không hoàn toàn hiểu nhau cố sống, làm việc và phản ứng với nhau trong tình huống ấy.
