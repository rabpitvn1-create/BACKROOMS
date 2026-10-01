# BACKROOMS WORLD — SUBLEVELS LEVEL 1–6

**Trạng thái:** CURRENT / PROJECT CANON  
**Phạm vi:** Các mục con còn hoạt động trong danh sách Backrooms Wiki hiện hành cho Level 1 đến Level 6.  
**Mốc tra cứu:** 2026-09-28.  
**Nguyên tắc:** Chỉ lấy trang còn hoạt động làm canon nền. Trang bị đánh dấu `Trimmed; Open for Rewrite` không được đưa vào tuyến gameplay mặc định. Trang đang rewrite nhưng chưa trimmed chỉ giữ các dữ kiện ổn định, tránh khóa chi tiết sâu.

**HARD LOCK gameplay:** `level_graph.json` quyết định tuyến chuyển vùng; `EntityCore` quyết định Entity; `ItemCore` quyết định vật phẩm/tài nguyên. Canon môi trường không tự spawn Entity, không tự cấp loot, không tự mở route ngoài graph.

## Level 1.2 — Concrete Garden
<!-- canon: aliases=Concrete Garden,Garden Sector; core=true -->
- Một sub-section của Level 1 bị thực vật phủ dày: cỏ và hoa chiếm sàn, dây leo bám cột, tường và có thể lan lên trần.
- Ở lại khoảng một giờ có thể bắt đầu quá trình biến đổi cơ thể người thành thực vật; tác động tăng dần nếu tiếp tục lưu lại.
- Nguồn gốc thảm thực vật chưa xác định. Không tự suy diễn đây là Entity nếu Core chưa xác nhận.
- Gameplay: route theo graph; vật phẩm thực vật hoặc encounter chỉ xuất hiện khi Core cho phép.

## Level 1.3 — Malignance
<!-- canon: aliases=Malignance; core=true -->
- Không gian gồm các phòng và hành lang trắng chói, bề mặt lát vật liệu trơn gần như mềm; bề mặt khó bám bẩn và có khả năng tự phục hồi sau hư hại.
- Dữ liệu cũ từng xem đây là khu chăm sóc y tế đặc biệt, nhưng trạng thái hiện hành đã bị tái phân loại thành Dead Zone sau các bất thường và dấu hiệu bị nghi liên quan tới Decay.
- Không dùng nhãn Survival 0 cũ để mô tả nơi này là an toàn. Hiệu ứng chữa trị hay y tế không được tự kích hoạt nếu gameplay Core chưa xác nhận.
- Gameplay: route theo graph; trạng thái nguy hiểm hiện hành thắng mô tả an toàn cũ.

## Level 1.5 — Inverted
<!-- canon: aliases=Inverted; core=true -->
- Một vùng “ở giữa” các Level với thực tại bị đảo và phân mảnh: màu sắc có thể đảo, nguồn sáng có thể phát bóng tối, hướng trên/dưới và hình học không đáng tin.
- Giao tiếp đi qua khu vực này có thể bị xáo trộn, mất thứ tự hoặc biến dạng; báo cáo hiện trường vì thế phải được xem là bằng chứng không hoàn chỉnh.
- Dễ lọt vào hơn thoát ra; nguồn hiện hành không xác nhận lối ra bình thường ngoài no-clip.
- Gameplay: không dùng sự méo tín hiệu để sửa state, inventory hoặc kết quả Core đã khóa.

## Base Alpha
<!-- canon: aliases=Base Alpha,M.E.G. Base Alpha; core=true -->
- Một căn cứ lớn của M.E.G. nằm trong Level 1, được nguồn hiện hành mô tả là căn cứ lâu đời tập trung vào huấn luyện nhân sự thám hiểm và che chở người mới.
- Căn cứ được hình thành trong các phòng/hành lang Level 1 có thể cải tạo thành không gian ở và làm việc.
- Sự tồn tại của Base Alpha không đồng nghĩa mọi khu Level 1 đều an toàn, có người hoặc có tiếp tế.
- Gameplay: NPC, dịch vụ, vật phẩm và quyền truy cập tại căn cứ chỉ tồn tại khi Core/state tạo chúng.

## Traders Vault
<!-- canon: aliases=Traders Vault,Trader's Vault,Storage Room; core=true -->
- Một hành lang kho trong Level 1 với nhiều phòng chứa đóng bằng cửa kiểu garage; B.N.T.G. sử dụng nơi này làm kho tiếp tế.
- Trang nguồn đang trong quá trình rewrite, vì vậy chỉ khóa cấu trúc kho và vai trò lưu trữ ở mức nền; không khóa số lượng hàng, danh mục loot hay mức an toàn tuyệt đối.
- Các vật trong kho có thể được mô tả như dấu hiệu môi trường, nhưng không được tự thêm vào inventory.
- Gameplay: ItemCore quyết định mọi vật phẩm thực nhận; route theo graph.

## Level 2.1 — Locked
<!-- canon: aliases=Locked,The Lockout; core=true -->
- Một mạng hành lang Euclid phức tạp tương tự Level 2 nhưng kiến trúc mới hơn, các junction kém đều và nhiều khu mang chức năng lưu trữ/maintenance.
- Sau sự cố Lockout, các lối vào tự nhiên bị niêm kín trong nguồn tham khảo; thiếu thức ăn, nước và áp lực Entity khiến khu vực trở thành nơi sinh tồn rất khắc nghiệt.
- Hound và các Entity khác được nguồn ghi nhận, nhưng loại Entity thực sự xuất hiện trong game vẫn do EntityCore quyết định.
- Gameplay: graph là project override cho khả năng đi qua node này; lore nguồn không tự khóa hoặc mở đường.

## Level 3.5 — Electropolis
<!-- canon: aliases=Electropolis; core=true -->
- Một đô thị công nghiệp brutalist đơn sắc, được mô tả như trải dài không có điểm cuối đã biết.
- Bầu khí quyển chịu hoạt động điện liên tục; các đợt phóng điện cao áp đánh xuống công trình và người theo quy luật khó dự đoán.
- Không có khu định cư hoạt động được xác nhận trong nguồn hiện hành; vào tương đối dễ nhưng rời đi được đánh giá rất khó.
- Gameplay: nguy cơ điện là hazard môi trường; damage cụ thể và transition vẫn do Core quyết định.

## The Office Market
<!-- canon: aliases=The Office Market,Office Market; core=true -->
- Một khu chợ định kỳ bên trong Level 4, hình thành trong một phòng văn phòng lớn và được nhiều nhóm/wanderer dùng để trao đổi hàng hóa và gặp gỡ.
- Nguồn hiện hành mô tả chợ hoạt động vào thứ Sáu, 08:00–18:00 M.S.T.; đây là lịch của lore nguồn, không phải đồng hồ bắt buộc của gameplay nếu state không theo thời gian đó.
- Việc nguồn liệt kê nhiều loại hàng không tạo quyền sở hữu hay bảo đảm món nào đang có sẵn.
- Gameplay: ItemCore/economy quyết định giao dịch thực; NPC và quầy hàng phải có state tương ứng.

## Level 5.1 — GRAND OPENING OF THE TERROR HOTEL CASINO
<!-- canon: aliases=Terror Hotel Casino,Level 5.1 Casino; core=true -->
- Một casino lớn nằm trong The Beverly Room của Level 5, vận hành theo chu kỳ mở khoảng 16 giờ rồi đóng khoảng 8 giờ trong nguồn tham khảo.
- Casino dùng chip và các trò đỏ đen; nhiều báo cáo mô tả trò chơi có tính gây nghiện và có dấu hiệu thao túng kết quả. Không coi nhãn quảng cáo “safe/secure” trong tài liệu nội thế giới là sự thật khách quan.
- Nguồn gắn nơi này với The Beast of Level 5 và đội ngũ staff bị biến đổi. Project override giữ hành vi Entity theo EntityCore; không hạ The Beast thành NPC trung lập.
- Gameplay: không tự cấp chip, phần thưởng, thức ăn hay vật phẩm từ casino; economy/loot do Core quyết định.

## Level 5.2 — Scenic Views
<!-- canon: aliases=Scenic Views; core=true -->
- Một cấu trúc khách sạn có chiều cao và chiều rộng dường như vô hạn nhưng độ dày hữu hạn, với hai mặt hành lang xếp tầng và các phòng khách sạn dọc lối đi.
- Cầu thang giữa tầng hiếm nhưng là cách di chuyển an toàn hơn; rơi khỏi hành lang hoặc dùng các cột thẳng đứng là nguy cơ chết người.
- Cửa vào từ Level 5 tự đóng và khóa; lối ra theo nguồn phụ thuộc cửa được mở từ phía Level 5. Project graph vẫn là authority cho transition gameplay.
- Nguồn mô tả thức ăn và một số Entity, nhưng ItemCore/EntityCore thắng nếu có xung đột, đặc biệt với hành vi Faceling.

## Level 5.3 — Promethei Bibliotheca
<!-- canon: aliases=Promethei Bibliotheca,Promethean Library; core=true -->
- Một thư viện thanh lịch với hàng trăm hành lang giá sách kéo dài, bảy tầng và kiến trúc đá/cẩm thạch nằm trong phạm vi Level 5.
- Hiện tượng cốt lõi là tương tác với ký ức: sau khoảng một đến hai giờ, người ở bên trong có thể đau đầu rồi mất những ký ức được chuyển thành sách/notebook trong thư viện.
- Nguồn nghi ngờ một “Library Mind” có ý thức và trao đổi tri thức, nhưng bản chất thật chưa được xác nhận; không biến lời giải thích của nguồn thành toàn tri tuyệt đối.
- Gameplay: sách không tự cung cấp canon mà nhân vật chưa có quyền biết; knowledge boundary và state hiện hành vẫn áp dụng.

## Level 6.1 — The Snackrooms
<!-- canon: aliases=The Snackrooms,Snackrooms; core=true -->
- Một mê cung cafeteria/food court kiểu trung tâm thương mại với nhiều quầy ăn và nhà hàng thuộc nhiều thời kỳ; cửa ra của một quầy có thể dẫn sang quầy khác thay vì quay lại đúng hành lang.
- Nguồn mô tả thực phẩm và đồ uống có thể tái xuất hiện theo chu kỳ, nhưng Project không biến điều đó thành loot vô hạn hoặc nguồn hồi phục miễn phí.
- Các báo cáo sau này ghi nhận sự cố Entity và kiểm soát lối vào; vì vậy không xem đây là safe hub mặc định.
- Gameplay: ItemCore quyết định lượng thực phẩm thực nhận; EntityCore quyết định encounter; graph quyết định transition.

## Level 6.31 — Pierce the Veil
<!-- canon: aliases=Pierce the Veil,Vantawhite; core=true -->
- Một mạng hành lang đá vôi trắng được phủ chất phát sáng gọi là “vantawhite”; nguồn mô tả chất này chống lại Decay trên các bề mặt nó phủ.
- Trang hiện hành mô tả một cộng đồng lớn gần điểm vào, nhưng vật tư vẫn hạn chế và các vùng xa có kiến trúc ngày càng vô lý.
- Lối vào duy nhất trong nguồn đi qua vùng `Vantablack` của Level 6.3 và có thể mất nhiều ngày hoặc nhiều tuần; lối ra chưa được thử nghiệm đáng tin cậy.
- **PROJECT ROUTE LOCK:** Level 6.3 hiện bị trimmed, nên 6.31 được giữ làm canon/node nhận diện nhưng không có edge gameplay từ Level 6.1. Không tự bịa đường tắt trực tiếp.

# TRIMMED / KHÔNG ĐƯA VÀO TUYẾN MẶC ĐỊNH

Tại mốc tra cứu 2026-09-28, danh sách hiện hành đánh dấu các mục sau là `Trimmed; Open for Rewrite`:
- Level 1.1 — Corrupted Corridor.
- Office Space EL3A (nhánh Level 2).
- Level 6.2 — The Neon Maze.
- Level 6.3 — Vantablack.

Không tự khôi phục các bản cũ như Level 2.2, Level 3.1 hoặc Level 4.2–4.4 chỉ vì chúng còn xuất hiện trong archive/search cũ. Nếu Wiki đưa chúng trở lại danh sách hoạt động sau này, phải research lại trước khi retcon.

# NGUỒN VÀ ATTRIBUTION

Nội dung trong file này là project adaptation/tóm lược từ các trang Backrooms Wiki đang hoạt động; phần dẫn xuất từ các trang nguồn tuân theo CC BY-SA 3.0. Project canon và Core thắng khi có xung đột gameplay.

- Levels List: https://backrooms-wiki.wikidot.com/normal-levels-i/level-0
- Level 1.2 — Concrete Garden — Praetor3005: https://backrooms-wiki.wikidot.com/level-1-2
- Level 1.3 — Malignance — DivineAtlas: https://backrooms-wiki.wikidot.com/level-1-3
- Level 1.5 — Inverted — Stretchsterz: https://backrooms-wiki.wikidot.com/level-1-5
- Base Alpha — Praetor3005: https://backrooms-wiki.wikidot.com/base-alpha
- Traders Vault — Stretchsterz — UNDER REWRITE: https://backrooms-wiki.wikidot.com/traders-vault
- Level 2.1 — Locked — penutbuteraples: https://backrooms-wiki.wikidot.com/level-2-1
- Level 3.5 — Electropolis — exotichive: https://backrooms-wiki.wikidot.com/level-3-5
- The Office Market — Praetor3005: https://backrooms-wiki.wikidot.com/the-office-market
- Level 5.1 — Terror Hotel Casino — Natedagreat563: https://backrooms-wiki.wikidot.com/level-5-1
- Level 5.2 — Scenic Views — jan Jejasa: https://backrooms-wiki.wikidot.com/level-5-2
- Level 5.3 — Promethei Bibliotheca — Praetor3005: https://backrooms-wiki.wikidot.com/level-5-3
- Level 6.1 — The Snackrooms — Stretchsterz: https://backrooms-wiki.wikidot.com/level-6-1
- Level 6.31 — Pierce the Veil — r a t i f: https://backrooms-wiki.wikidot.com/level-6-31
