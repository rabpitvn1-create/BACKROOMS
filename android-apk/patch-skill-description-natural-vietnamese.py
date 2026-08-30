from pathlib import Path

ROOT = Path(__file__).resolve().parent
CATALOG = ROOT / "app/src/main/java/com/rabpit/backroom/core/CompanionSkillCatalog.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CompanionSkillCatalogTest.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Issue #126 {label}: expected one anchor, found {count}")
    return text.replace(old, new, 1)


catalog = CATALOG.read_text(encoding="utf-8")

replacements = [
    (
        '    s("ARGUS Terrain Read", "PASSIVE", "Bắt đầu chiến đấu / tự làm mới", "Phân tích trong 3 lượt: Iris khai thác góc bắn và điểm hở của mục tiêu.", "Không nhìn xuyên tường, không tự biết bản thể thật."),',
        '    s("ARGUS Terrain Read", "PASSIVE", "Khi giao tranh bắt đầu hoặc dữ liệu được làm mới", "Trong 3 lượt tiếp theo, Iris liên tục đọc địa hình, góc bắn và những sơ hở trong cách mục tiêu di chuyển.", "Khả năng này không cho phép Iris nhìn xuyên tường hay tự biết bản thể thật của mục tiêu."),',
        "Iris ARGUS Terrain Read",
    ),
    (
        '    s("Thousandfold Cognition", "PASSIVE", "Khi Iris bị nhắm", "Tăng tốc xử lý thông tin tối đa 1:1.000 để đọc quỹ đạo và phản ứng.", "Không làm cơ thể hoặc súng nhanh hơn 1.000 lần."),',
        '    s("Thousandfold Cognition", "PASSIVE", "Khi Iris trở thành mục tiêu của một đòn tấn công", "Iris tăng tốc xử lý thông tin lên tối đa 1:1.000 để đọc quỹ đạo và chọn phản ứng phù hợp.", "Chỉ tốc độ nhận thức được gia tốc; cơ thể và súng của Iris không nhanh hơn 1.000 lần."),',
        "Iris Thousandfold Cognition",
    ),
    (
        '    s("Twosome Time", "AUTO", "30% mỗi lượt hợp lệ", "2 phát chéo góc, 155% DMG vũ khí; 170% nếu mục tiêu đang được phân tích."),',
        '    s("Twosome Time", "AUTO", "30% ở mỗi lượt hợp lệ", "Iris bắn hai phát từ hai góc chéo nhau. Kỹ năng gây 155% DMG vũ khí, tăng lên 170% nếu mục tiêu đang được ARGUS phân tích."),',
        "Iris Twosome Time",
    ),
    (
        '    s("Rain Storm", "AUTO", "20% mỗi lượt hợp lệ", "6 phát khi đổi góc trên không, tổng 145% DMG vũ khí."),',
        '    s("Rain Storm", "AUTO", "20% ở mỗi lượt hợp lệ", "Iris đổi góc tấn công trên không rồi bắn liên tiếp 6 phát, gây tổng cộng 145% DMG vũ khí."),',
        "Iris Rain Storm",
    ),
    (
        '    s("Honeycomb Fire", "AUTO", "20% mỗi lượt hợp lệ", "8 phát tập trung, 185% DMG vũ khí; Phá Giáp 20% trong 2 lượt."),',
        '    s("Honeycomb Fire", "AUTO", "20% ở mỗi lượt hợp lệ", "Iris dồn 8 phát vào cùng một vùng mục tiêu, gây 185% DMG vũ khí. Mục tiêu bị Phá Giáp 20% trong 2 lượt."),',
        "Iris Honeycomb Fire",
    ),
    (
        '    s("Charged Shot", "AUTO", "25% mỗi lượt hợp lệ", "175% DMG vũ khí, bỏ qua 35% Giáp."),',
        '    s("Charged Shot", "AUTO", "25% ở mỗi lượt hợp lệ", "Iris nạp lực cho một phát bắn xuyên phá, gây 175% DMG vũ khí và bỏ qua 35% Giáp của mục tiêu."),',
        "Iris Charged Shot",
    ),
    (
        '    s("Dead Angle", "COUNTER", "15% sau khi Entity phản công hụt", "Ivory & Ebony phản kích tức thời, 120% DMG vũ khí; không chiếm lượt chính."),',
        '    s("Dead Angle", "COUNTER", "15% sau khi Entity phản công hụt", "Iris lập tức dùng Ivory & Ebony bắn trả từ góc chết, gây 120% DMG vũ khí mà không tiêu tốn lượt chính."),',
        "Iris Dead Angle",
    ),
    (
        '    s("ARGUS // Thousandfold Execution", "ULTIMATE", "Tự động mỗi 4 lượt chiến đấu", "12 phát luân phiên, 300% DMG vũ khí; trạng thái Lộ hoàn toàn kéo dài 2 lượt, giảm 25% Né tránh và 20% Giáp.", "Không tự phát hiện mục tiêu hoặc bản thể khi chưa có dữ liệu."),',
        '    s("ARGUS // Thousandfold Execution", "ULTIMATE", "Tự động sau mỗi 4 lượt chiến đấu", "Iris khóa toàn bộ dữ liệu đã phân tích rồi bắn 12 phát luân phiên, gây 300% DMG vũ khí. Mục tiêu bị Lộ hoàn toàn trong 2 lượt, mất 25% Né tránh và 20% Giáp.", "Kỹ năng chỉ khai thác dữ liệu Iris đã có; nó không tự phát hiện mục tiêu hay bản thể chưa từng được nhận diện."),',
        "Iris ultimate",
    ),
    (
        '    s("Lucifer Core", "PASSIVE", "Luôn hoạt động khi nhân vật còn khả năng chiến đấu", "Không bị giới hạn bởi cơ chế cạn Mana, Năng lượng hoặc Quá nhiệt nội tại; hồi 2% Max HP mỗi lượt, tăng lên 4% khi Devil Trigger.", "Không hồi từ 0 HP."),',
        '    s("Lucifer Core", "PASSIVE", "Luôn hoạt động khi Syvial còn khả năng chiến đấu", "Lucifer Core không cạn vì Mana, Năng lượng hay Quá nhiệt nội tại. Syvial hồi 2% Max HP mỗi lượt; khi Devil Trigger đang hoạt động, mức hồi tăng lên 4% Max HP.", "Lucifer Core không thể kéo Syvial trở lại từ 0 HP."),',
        "Syvial Lucifer Core",
    ),
    (
        '    s("Killing Intent Read", "PASSIVE", "Khi đối thủ để lộ ý định", "Đọc chuyển động và chuẩn bị phản đòn; hỗ trợ Counterphase."),',
        '    s("Killing Intent Read", "PASSIVE", "Khi đối thủ để lộ ý định tấn công", "Syvial đọc chuyển động chuẩn bị của đối thủ để chọn thời điểm phản đòn, đồng thời tạo điều kiện cho Counterphase."),',
        "Syvial Killing Intent Read",
    ),
    (
        '    s("Rift Sever", "AUTO", "30% mỗi lượt hợp lệ", "Spatial Shift làm lệch trục phòng thủ rồi chém, 175% DMG vũ khí, bỏ qua 20% Giáp."),',
        '    s("Rift Sever", "AUTO", "30% ở mỗi lượt hợp lệ", "Syvial dùng Spatial Shift làm lệch hướng phòng thủ rồi chém bằng GodKiller, gây 175% DMG vũ khí và bỏ qua 20% Giáp."),',
        "Syvial Rift Sever",
    ),
    (
        '    s("Crimson Guillotine", "AUTO", "20% mỗi lượt hợp lệ", "190% DMG vũ khí; Chảy máu trong 3 lượt, mỗi lượt 4% Max HP."),',
        '    s("Crimson Guillotine", "AUTO", "20% ở mỗi lượt hợp lệ", "Syvial tung một nhát chém nặng gây 190% DMG vũ khí. Vết thương tiếp tục Chảy máu trong 3 lượt, mỗi lượt mất 4% Max HP."),',
        "Syvial Crimson Guillotine",
    ),
    (
        '    s("Lucifer Breaker", "AUTO", "20% mỗi lượt hợp lệ", "Chuỗi cận chiến kết hợp GodKiller gây 155% DMG vũ khí; làm gián đoạn phản ứng hiện tại của Entity bằng Choáng."),',
        '    s("Lucifer Breaker", "AUTO", "20% ở mỗi lượt hợp lệ", "Syvial áp sát bằng một chuỗi đòn cận chiến rồi kết thúc bằng GodKiller, gây 155% DMG vũ khí và Choáng để cắt phản ứng hiện tại của Entity."),',
        "Syvial Lucifer Breaker",
    ),
    (
        '    s("Counterphase", "COUNTER", "30% sau khi Entity phản công hụt", "Spatial Shift vào góc chết và phản chém 125% DMG vũ khí; không chiếm lượt chính."),',
        '    s("Counterphase", "COUNTER", "30% sau khi Entity phản công hụt", "Syvial dùng Spatial Shift lướt vào góc chết rồi phản chém, gây 125% DMG vũ khí mà không tiêu tốn lượt chính."),',
        "Syvial Counterphase",
    ),
    (
        '    s("GodKiller Recall", "PASSIVE", "Khi bị tước vũ khí hợp lệ", "Gọi GodKiller trở lại ở đầu lượt kế tiếp nếu không có luật của boss khóa khả năng triệu hồi."),',
        '    s("GodKiller Recall", "PASSIVE", "Khi GodKiller bị tước khỏi Syvial", "Đầu lượt kế tiếp, Syvial gọi GodKiller trở lại tay. Hiệu ứng không xảy ra nếu luật riêng của boss đang khóa khả năng triệu hồi."),',
        "Syvial GodKiller Recall",
    ),
    (
        '    s("Devil Trigger", "STATE", "HP <= 50% hoặc đối đầu Diệp Minh", "+25% DMG gây ra, +20% Né tránh, -20% DMG nhận vào theo vai trò cá nhân; hồi phục từ Lucifer Core tăng lên 4% Max HP mỗi lượt.", "Không có hồi chiêu nội tại, không giới hạn thời gian theo canon."),',
        '    s("Devil Trigger", "STATE", "Khi HP còn 50% trở xuống hoặc khi đối đầu Diệp Minh", "Syvial gây thêm 25% DMG, nhận thêm 20% Né tránh và giảm 20% DMG phải chịu. Trong trạng thái này, Lucifer Core hồi 4% Max HP mỗi lượt.", "Devil Trigger không có hồi chiêu nội tại và không bị giới hạn thời gian theo canon."),',
        "Syvial Devil Trigger",
    ),
    (
        '    s("Spatial Dominion", "AUTO", "20% khi Devil Trigger", "Chuỗi Spatial Shift kết hợp GodKiller gây 210% DMG vũ khí; Mất phương hướng làm giảm 25% Độ chính xác trong 2 lượt."),',
        '    s("Spatial Dominion", "AUTO", "20% khi Devil Trigger đang hoạt động", "Syvial liên tục đổi vị trí bằng Spatial Shift rồi tấn công bằng GodKiller, gây 210% DMG vũ khí. Mục tiêu bị Mất phương hướng và giảm 25% Độ chính xác trong 2 lượt."),',
        "Syvial Spatial Dominion",
    ),
    (
        '    s("GodKiller Override // Twenty-Four Severance", "ULTIMATE", "Mỗi 3 lượt chiến đấu khi Devil Trigger", "Dừng thời gian ngoại giới, đúng 24 nhát chém x 10 HP = 240 HP; bỏ qua Né tránh.", "Không phải đòn kết liễu tuyệt đối."),',
        '    s("GodKiller Override // Twenty-Four Severance", "ULTIMATE", "Sau mỗi 3 lượt chiến đấu khi Devil Trigger đang hoạt động", "Syvial dừng hoàn toàn thời gian ngoại giới và tung đúng 24 nhát chém. Mỗi nhát gây 10 HP, tổng cộng 240 HP, đồng thời bỏ qua Né tránh.", "Đây không phải đòn kết liễu tuyệt đối; các luật phòng thủ riêng của mục tiêu vẫn được áp dụng nếu có."),',
        "Syvial ultimate",
    ),
    (
        '    s("Có Gì Đó Sai Sai", "PASSIVE", "Khi An Nhiên theo Party", "Giảm 25% xác suất gặp nguy hiểm trong hành động vật lý hợp lệ."),',
        '    s("Có Gì Đó Sai Sai", "PASSIVE", "Khi An Nhiên đang đi cùng Party", "Bản năng cảnh giác của An Nhiên giúp Party giảm 25% xác suất gặp nguy hiểm trong các hành động vật lý hợp lệ."),',
        "An Nhien danger",
    ),
    (
        '    s("Nhặt Có Chọn Lọc", "PASSIVE", "Khi SEARCH", "+10 điểm phần trăm vào tỷ lệ rơi vật phẩm chung hiện có.", "Không tạo lần kiểm tra rơi vật phẩm thứ hai."),',
        '    s("Nhặt Có Chọn Lọc", "PASSIVE", "Khi Party thực hiện SEARCH", "An Nhiên giúp nhận ra thứ đáng lấy, cộng 10 điểm phần trăm vào tỷ lệ rơi vật phẩm chung của lần SEARCH đó.", "Kỹ năng chỉ tăng tỷ lệ hiện có, không tạo thêm một lần kiểm tra rơi vật phẩm."),',
        "An Nhien loot",
    ),
    (
        '    s("Không Phải Tôi Nhát, Tôi Có Chiến Thuật", "PASSIVE", "Khi tình huống xấu", "Ưu tiên vị trí an toàn; không biến An Nhiên thành nhân vật chiến đấu."),',
        '    s("Không Phải Tôi Nhát, Tôi Có Chiến Thuật", "PASSIVE", "Khi tình huống trở nên nguy hiểm", "An Nhiên chủ động tìm vị trí an toàn và tránh chen vào tuyến giao tranh, nhờ đó giữ đúng vai trò hỗ trợ sinh tồn thay vì trở thành nhân vật chiến đấu."),',
        "An Nhien safe position",
    ),
    (
        '    s("Quăng Đại Cái Gì Đó", "UTILITY", "25% mỗi lượt chiến đấu khi đang ở trong Party", "Ném vật vô hại để đánh lạc hướng, Entity giảm 25 điểm % Độ chính xác trong phản ứng hiện tại.", "Không gây sát thương, không dùng vũ khí."),',
        '    s("Quăng Đại Cái Gì Đó", "UTILITY", "25% ở mỗi lượt chiến đấu khi An Nhiên đang trong Party", "An Nhiên quăng một vật vô hại để làm Entity mất tập trung. Phản ứng hiện tại của Entity bị giảm 25 điểm phần trăm Độ chính xác.", "Đòn đánh lạc hướng không gây sát thương và không được tính là sử dụng vũ khí."),',
        "An Nhien distraction",
    ),
    (
        '    s("Khoan, Để Tôi Đọc Cái Này", "UTILITY", "20% khi SEARCH một Exit", "Nếu kích hoạt, +20 điểm phần trăm cho lần kiểm tra Exit của hành động đó."),',
        '    s("Khoan, Để Tôi Đọc Cái Này", "UTILITY", "20% khi Party SEARCH một Exit", "Nếu An Nhiên nhận ra dấu hiệu hữu ích, lần kiểm tra Exit của chính hành động đó được cộng 20 điểm phần trăm."),',
        "An Nhien exit",
    ),
    (
        '    s("Đừng Đụng Vào, Nhìn Là Biết Độc", "UTILITY", "30% khi kiểm tra nước hoặc chất lỏng khả nghi", "Nếu kích hoạt, chặn lần kiểm tra nguy hiểm của hành động đó.", "Chỉ là kiểm tra nguy cơ, không tự biết toàn bộ bản chất vật thể."),',
        '    s("Đừng Đụng Vào, Nhìn Là Biết Độc", "UTILITY", "30% khi Party kiểm tra nước hoặc chất lỏng khả nghi", "Nếu An Nhiên phát hiện dấu hiệu bất thường, lần kiểm tra nguy hiểm của hành động đó bị chặn trước khi Party tiếp xúc trực tiếp.", "An Nhiên chỉ nhận ra dấu hiệu nguy cơ; cô không tự biết toàn bộ bản chất của chất lỏng."),',
        "An Nhien liquid",
    ),
    (
        '    s("Thôi Để Tôi Làm", "UTILITY", "Khi xử lý thao tác sinh tồn", "Đại diện lợi thế thực dụng trong lời kể của Game Master; không áp dụng cho hack, phép thuật hoặc công nghệ ngoài khả năng."),',
        '    s("Thôi Để Tôi Làm", "UTILITY", "Khi Party xử lý một thao tác sinh tồn phù hợp", "An Nhiên trực tiếp phụ trách những việc thực dụng mà cô làm tốt, giúp Game Master thể hiện lợi thế sinh tồn của cô trong kết quả hành động.", "Kỹ năng không áp dụng cho hack, phép thuật hay công nghệ vượt ngoài khả năng của An Nhiên."),',
        "An Nhien survival",
    ),
    (
        '    s("Kế Hoạch Không Có Trong Kế Hoạch", "ULTIMATE", "Mỗi 5 lượt chiến đấu khi đang ở trong Party", "Tận dụng địa hình: +30 Tiến độ thoát và Entity giảm 20 điểm % Độ chính xác trong phản ứng hiện tại.", "Không gây sát thương."),',
        '    s("Kế Hoạch Không Có Trong Kế Hoạch", "ULTIMATE", "Sau mỗi 5 lượt chiến đấu khi An Nhiên đang trong Party", "An Nhiên tận dụng địa hình để mở đường rút: Party nhận +30 Tiến độ thoát, còn phản ứng hiện tại của Entity bị giảm 20 điểm phần trăm Độ chính xác.", "Kỹ năng chỉ hỗ trợ thoát thân và không gây sát thương."),',
        "An Nhien ultimate",
    ),
    (
        '    s("The Last Requiem", "AUTO", "38% mỗi lượt hợp lệ", "SRU-SG: 4 viên đạn quỷ lực theo nhịp giật kiểm soát, đặt chùm đạn vào điểm neo vận động ở vai; 170% DMG vũ khí; Chảy máu 3 lượt x 5% Max HP."),',
        '    s("The Last Requiem", "AUTO", "38% ở mỗi lượt hợp lệ", "Kai ghìm nhịp giật của SRU-SG và bắn 4 viên đạn quỷ lực vào vùng vai để phá nhịp vận động của mục tiêu. Kỹ năng gây 170% DMG vũ khí và Chảy máu trong 3 lượt, mỗi lượt mất 5% Max HP."),',
        "Kai Last Requiem",
    ),
    (
        '    s("Silent Lullaby", "AUTO", "27% mỗi lượt hợp lệ", "SRU-SG: 4 viên đạn quỷ lực vào cùng vùng trọng yếu trên ngực, kiểm soát độ giật và độ tản; 130% DMG vũ khí; Choáng 1 lượt."),',
        '    s("Silent Lullaby", "AUTO", "27% ở mỗi lượt hợp lệ", "Kai giữ chặt SRU-SG và dồn 4 viên đạn quỷ lực vào cùng một vùng trọng yếu trên ngực. Kỹ năng gây 130% DMG vũ khí và Choáng mục tiêu trong 1 lượt."),',
        "Kai Silent Lullaby",
    ),
    (
        '    s("Salvation", "AUTO", "26% mỗi lượt hợp lệ", "Bứt tốc qua góc chết, ghì SRU-SG bằng hai tay ở cự ly gần và khai hỏa 2 viên đạn quỷ lực; 147% DMG vũ khí."),',
        '    s("Salvation", "AUTO", "26% ở mỗi lượt hợp lệ", "Kai bứt qua góc chết, áp sát rồi ghì SRU-SG bằng hai tay để bắn 2 viên đạn quỷ lực ở cự ly gần, gây 147% DMG vũ khí."),',
        "Kai Salvation",
    ),
    (
        '    s("Quick Step", "AUTO", "35% mỗi lượt hợp lệ", "Đổi góc bằng các pha bứt tốc ngắn trong khi giữ SRU-SG sẵn bắn; +50 điểm % Né tránh trong 3 lượt đối với phản công thường."),',
        '    s("Quick Step", "AUTO", "35% ở mỗi lượt hợp lệ", "Kai liên tục đổi góc bằng những pha bứt tốc ngắn nhưng vẫn giữ SRU-SG ở tư thế sẵn bắn. Trong 3 lượt, Kai nhận thêm 50 điểm phần trăm Né tránh trước các phản công thông thường."),',
        "Kai Quick Step",
    ),
    (
        '    s("Guilty Crown Override", "ULTIMATE", "Mỗi 3 lượt chiến đấu", "Đúng 24 phát x 10 HP, Độ chính xác 200%, bỏ qua Né tránh."),',
        '    s("Guilty Crown Override", "ULTIMATE", "Tự động sau mỗi 3 lượt chiến đấu", "Kai tung đúng 24 phát liên tiếp. Mỗi phát gây 10 HP, tổng cộng 240 HP; Độ chính xác của kỹ năng là 200% và đòn bắn bỏ qua Né tránh."),',
        "Kai ultimate",
    ),
    (
        '    s("Trinh sát chiến trường", "PASSIVE", "Khi Lucia ở trong Party", "+5 điểm phần trăm vào tỷ lệ rơi vật phẩm chung hiện có."),',
        '    s("Trinh sát chiến trường", "PASSIVE", "Khi Lucia đang ở trong Party", "Khả năng quan sát chiến trường của Lucia cộng 5 điểm phần trăm vào tỷ lệ rơi vật phẩm chung hiện có của Party."),',
        "Lucia scout",
    ),
    (
        '    s("M4A1 Joint Attack", "COMMAND", "Khi người chơi ra lệnh cả Kai và Lucia cùng tấn công", "Lucia có lượt xử lý bắn M4A1 riêng và vẫn phải qua kiểm tra Né tránh của Entity."),',
        '    s("M4A1 Joint Attack", "COMMAND", "Khi người chơi ra lệnh cho Kai và Lucia cùng tấn công", "Lucia thực hiện phần tấn công bằng M4A1 như một hành động riêng trong lượt của Party. Loạt bắn của cô vẫn phải vượt qua kiểm tra Né tránh của Entity."),',
        "Lucia joint attack",
    ),
    (
        '    s("M4A1 Full Auto Burst", "AUTO", "20% mỗi 2 lượt chiến đấu hợp lệ khi Party chọn TẤN CÔNG", "Xả đúng 30 viên; mỗi viên gây 30 + Base DMG trước Giáp; toàn loạt chỉ thực hiện một lần kiểm tra Né tránh của Entity."),',
        '    s("M4A1 Full Auto Burst", "AUTO", "20% sau mỗi 2 lượt chiến đấu hợp lệ khi Party chọn TẤN CÔNG", "Lucia xả đúng 30 viên từ M4A1. Mỗi viên gây 30 + Base DMG trước Giáp; toàn bộ loạt bắn chỉ thực hiện một lần kiểm tra Né tránh của Entity."),',
        "Lucia full auto",
    ),
    (
        '    s("Too Young To Die", "AUTO", "15% mỗi lượt chiến đấu; khi HP < 50%, +5 điểm % mỗi 3 điểm % HP mất thêm dưới ngưỡng 50%", "Xả hết băng 60 viên; mỗi viên gây Base DMG +5% trước Giáp và các hiệu ứng tăng cường ngoài kỹ năng; toàn loạt chỉ thực hiện một lần kiểm tra Né tránh của Entity.", "Ví dụ: 49% HP = 15%, 47% = 20%, 44% = 25%; tỷ lệ tối đa 100%."),',
        '    s("Too Young To Die", "AUTO", "15% ở mỗi lượt chiến đấu; khi HP dưới 50%, cứ mất thêm 3 điểm phần trăm HP thì tỷ lệ kích hoạt tăng thêm 5 điểm phần trăm", "Lucia xả hết băng 60 viên. Mỗi viên gây Base DMG +5% trước Giáp và trước các hiệu ứng tăng cường ngoài kỹ năng; toàn bộ loạt bắn chỉ thực hiện một lần kiểm tra Né tránh của Entity.", "Ví dụ: ở 49% HP tỷ lệ là 15%, ở 47% là 20%, ở 44% là 25%. Tỷ lệ kích hoạt tối đa là 100%."),',
        "Lucia Too Young To Die",
    ),
]

for old, new, label in replacements:
    catalog = replace_once(catalog, old, new, label)

CATALOG.write_text(catalog, encoding="utf-8")

# Regression coverage for the player-facing writing pass. The exact combat values
# stay untouched; this test only prevents the old translation-like fragments from
# silently returning in a later patch layer.
test = TEST.read_text(encoding="utf-8")
regression = r'''
  @org.junit.Test fun issue126SkillDescriptionsReadAsNaturalVietnamese() {
    val all = listOf(KAI_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID, LUCIA_ID)
      .flatMap(CompanionSkillCatalog::forCharacter)
    val prose = all.flatMap { listOfNotNull(it.trigger, it.effect, it.note) }.joinToString("\n")
    val retiredFragments = listOf(
      "Phân tích trong 3 lượt:",
      "2 phát chéo góc, 155% DMG",
      "Spatial Shift làm lệch trục phòng thủ rồi chém",
      "Đại diện lợi thế thực dụng trong lời kể của Game Master",
      "SRU-SG: 4 viên đạn quỷ lực theo nhịp giật kiểm soát",
      "Xả đúng 30 viên; mỗi viên gây"
    )
    retiredFragments.forEach { fragment ->
      org.junit.Assert.assertFalse("Old translation-like skill prose returned: $fragment", prose.contains(fragment))
    }
    org.junit.Assert.assertTrue(CompanionSkillCatalog.forCharacter(IRIS_ID).first().effect.contains("Trong 3 lượt tiếp theo"))
    org.junit.Assert.assertTrue(CompanionSkillCatalog.forCharacter(KAI_ID).first().effect.contains("Kai ghìm nhịp giật của SRU-SG"))
    org.junit.Assert.assertTrue(CompanionSkillCatalog.forCharacter(LUCIA_ID).last().note.orEmpty().contains("Tỷ lệ kích hoạt tối đa là 100%"))
  }
'''
if "issue126SkillDescriptionsReadAsNaturalVietnamese" not in test:
    close = test.rfind("\n}")
    if close < 0:
        raise RuntimeError("Issue #126 CompanionSkillCatalogTest class terminator missing")
    test = test[:close] + "\n" + regression.rstrip() + test[close:]
TEST.write_text(test, encoding="utf-8")

print("Issue #126 applied: character skill descriptions rewritten as natural Vietnamese without changing gameplay values.")
