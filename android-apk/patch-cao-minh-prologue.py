"""Install the final Cao Minh prologue after identity and kit migration patches."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
HTML = ROOT / "app/src/main/assets/index.html"

PROLOGUE = r'''Ngày cuối cùng của Lôi Thiên Vực, Lục Trầm đứng bên trái Cao Minh.

Về sau sẽ chẳng còn ai trong thế giới này biết đó cũng là lần cuối hai người cùng đứng dưới một bầu trời. Khi ấy, họ chỉ nhìn thấy đại quân trải dài đến tận những dãy núi ngoài vực, nhìn thấy tiên chu chen kín tầng mây và những tầng trận pháp đã khóa cả bốn phương. Lôi vân vốn quanh năm cuộn trên đỉnh vực bị ánh sáng từ hàng vạn pháp khí chiếu thành một màu trắng nhợt, thỉnh thoảng mới có một đường sét tím xé qua rồi biến mất sau chiến kỳ.

Phía sau Cao Minh cũng còn người.

Không nhiều bằng đối phương, nhưng chẳng một ai có ý định rời đi.

Có những kẻ đã theo hắn từ rất lâu, có người chỉ gia nhập sau khi chiến tranh lan tới quê nhà. Ma tu, tán tu, những tông môn bị ép phải chọn một phía, cả những người chẳng quan tâm Chính hay Ma nhưng đã quyết định trận cuối của mình sẽ ở Lôi Thiên Vực. Nhiều gương mặt Lục Trầm đã quen. Có người từng cãi nhau với nàng vì nàng không cho uống rượu khi đang bị thương; có người trước kia cứ thấy nàng đi cạnh Cao Minh là tránh xa ba trượng vì sợ hai người lại đánh nhau giữa doanh trại. Hôm nay không ai còn tâm trạng nhắc những chuyện ấy.

Lục Trầm rút Tịch Quang khỏi vỏ.

Bên kia chiến tuyến có người nhận ra nàng.

Một tiếng gọi xuyên qua khoảng cách giữa hai quân.

“Lục sư tỷ!”

Lục Trầm khựng lại một thoáng.

Người gọi là một đệ tử Thiên Kiếm Môn. Nàng nhớ khuôn mặt ấy. Nhiều năm trước, khi người kia vừa nhập nội môn, nàng từng sửa cho hắn một thế kiếm ở sân phía đông. Bây giờ hắn mặc chiến giáp, đứng dưới đại kỳ của tông môn, nhưng ánh mắt nhìn nàng vẫn còn chút gì đó của thiếu niên ngày ấy.

“Lục sư tỷ, người trở về đi. Chưởng môn và các vị trưởng lão vẫn còn ở đây. Chỉ cần người rời khỏi Cao Minh…”

Một trưởng lão phía trên đã cắt ngang.

“Không cần gọi nàng là sư tỷ nữa. Đứng cùng Vạn Giới Ma Tôn đến hôm nay, nàng đã tự chọn đường.”

Lục Trầm nhìn qua.

“Ta vẫn tu Thiên Kiếm Đạo, dùng Tịch Quang, mặc Kiếm Khải của Thiên Kiếm Môn. Nếu các vị muốn hỏi tội ta, sau hôm nay ta sẽ tự trở về.”

Vị trưởng lão lạnh giọng: “Ngươi còn cho rằng mình có thể quay về?”

“Đó là việc sau hôm nay.”

Nàng không giải thích thêm.

Cao Minh đứng bên cạnh khẽ nghiêng đầu.

“Lục tiên tử vẫn còn nghĩ tới chuyện trở về chịu phạt sao?”

Lục Trầm không nhìn hắn. “Ngươi lo cho mình trước đi. Hôm nay người muốn lấy mạng ngươi còn nhiều hơn người muốn hỏi tội ta.”

“Ta chỉ thấy nàng tính hơi xa.”

“Còn sống thì mới có quyền tính xa.”

Cao Minh im một thoáng rồi bật cười.

“Câu này ta đồng ý.”

Đó là câu nói bình thường cuối cùng giữa hai người trước khi chiến trận bắt đầu.

Phía đối diện, Vô Lượng Đại Tôn Giả bước ra.

Ngay bên cạnh hắn là Diệp Minh.

Nụ cười trên môi Cao Minh mất đi.

Không có ai quanh hắn hỏi tại sao.

Ở đây chẳng còn mấy người cần được kể lại Diệp Minh là ai, càng không có ai cần nhắc cho Cao Minh nhớ món nợ giữa hai người. Lục Trầm chỉ nhìn thấy bàn tay Cao Minh buông thõng bên người khép lại rất chậm.

Huyết Ma Kiếm phản ứng nhanh hơn tất cả.

Thanh đại kiếm đen đỏ vốn lơ lửng sau vai Cao Minh lập tức xoay mũi. Ma Luân quanh huyết tinh chuyển động, những đường huyết văn trên kiếm thể đồng loạt sáng lên. Nó không hướng vào người vừa bước ra khiêu chiến.

Nó hướng thẳng về Diệp Minh.

Cao Minh không quay đầu. “Chưa phải lúc.”

Huyết Ma Kiếm phát ra một tiếng rung thấp.

“Ta biết.”

Nó vẫn không chuyển hướng.

Cao Minh nhìn Diệp Minh thêm một nhịp rồi mới nói: “Món nợ đó ta chưa quên. Ngươi cũng không cần nhắc.”

Thanh kiếm cuối cùng mới lùi về bên cạnh hắn.

Lục Trầm không hỏi thêm.

Ở phía đối diện, Vô Lượng Đại Tôn Giả đã bước ra khỏi quân trận. Đạo bào rộng theo gió trải phía sau, thiên quang từ đại trận soi xuống khiến thân ảnh hắn gần như đứng giữa một vầng sáng. Giọng nói truyền khắp Lôi Thiên Vực, vẫn là thứ giọng đường hoàng mà thiên hạ đã nghe không biết bao nhiêu lần trong những năm chiến tranh: “Cao Minh, ngươi lấy Ma Đạo làm loạn, khiến Vạn Giới sinh linh đồ thán. Hôm nay chư tông hội tụ nơi này, chính là để—”

“Vô Lượng.”

Cao Minh cắt ngang.

Vô Lượng nhìn hắn.

Cao Minh đưa mắt qua vô số chiến kỳ đang phủ kín chân trời. “Ngươi dẫn từng này người đến đây, ta còn tưởng cuối cùng cũng chịu đánh một trận cho xong. Nếu vẫn phải đọc lại những thứ các ngươi đã treo trên Tru Ma Lệnh suốt mấy năm thì thôi đi. Ta nghe đến thuộc rồi.”

Trong hàng Chính Đạo lập tức có tiếng quát mắng. Cao Minh chẳng buồn nhìn.

Vô Lượng chậm rãi rút thiên kiếm. “Ngươi vẫn cuồng vọng như cũ.”

“Ngươi vẫn nhiều lời như cũ.”

Lục Trầm thở dài. “Hai người các ngươi có thể đánh trước khi ta phải nghe thêm không?”

Cao Minh bật cười. “Nàng xem, vẫn là Lục tiên tử hiểu chuyện.”

Thiên kiếm của Vô Lượng giáng xuống ngay lúc ấy.

Lôi Thiên Vực nổ tung.

Trận chiến kéo dài đến mức không còn ai dưới chiến trường nhớ nổi mình đã chém bao nhiêu kiếm. Những người của Cao Minh không phải quân cờ đứng sau chờ hắn phân thắng bại. Họ giữ từng cửa trận, từng con đường vào nội vực, từng đoạn tường đã vỡ. Có người đánh đến pháp bảo nứt rồi cầm thẳng mảnh pháp bảo gãy lao vào đối thủ; có người linh lực gần cạn vẫn ở lại phía sau che cho đồng đội rút. Lục Trầm ở giữa bọn họ, Tịch Quang khi thì nằm trong tay, khi thì theo kiếm niệm rời khỏi người, đường kiếm sáng bạc liên tục cắt đứt những mũi công kích ép vào trận tuyến.

Một đệ tử Thiên Kiếm Môn từng được nàng chỉ kiếm pháp chặn trước mặt. Người kia cắn răng hỏi: “Lục sư tỷ, người thật sự muốn vì Cao Minh mà chém đồng môn sao?”

Tịch Quang vừa đè lưỡi kiếm của hắn xuống. Lục Trầm nhìn hắn, giọng vẫn bình tĩnh dù hơi thở đã nặng: “Nếu ta muốn chém ngươi, kiếm vừa rồi đã không dừng ở chuôi kiếm. Lùi xuống. Ta không muốn hôm nay phải nhặt xác người của Thiên Kiếm Môn.”

“Nhưng người đang đứng bên Ma Đạo!”

“Ta đang đứng ở nơi ta cho rằng mình nên đứng. Hai chuyện đó không giống nhau.”

Một luồng pháp lực khác đánh tới từ phía sau. Lục Trầm chưa cần quay đầu, một đường Huyết Sát Ma Khí đã từ trên cao quét xuống, đánh lệch pháp bảo kia trước khi nó chạm tới nàng. Huyết Ma Kiếm chỉ lướt qua đúng một lần rồi lập tức trở lại tầng chiến đấu phía trên.

Lục Trầm ngẩng lên theo bản năng.

Cao Minh và Vô Lượng đã đánh đến nơi mà phần lớn tu sĩ chỉ có thể nhìn thấy hai quầng sáng liên tục va chạm. Ba nghìn hiệp trôi qua giữa tiếng thiên kiếm và huyết quang xé trời. Không ai trong số họ giữ được nguyên vẹn. Huyết Ma Chiến Khải trên người Cao Minh đã vỡ một mảng lớn nơi sườn, máu theo mép Ma Kim nhỏ xuống. Vô Lượng cũng mất vẻ ung dung ban đầu, đạo bào bị kiếm khí cắt rách, một đường thương từ vai kéo xuống cánh tay vẫn chưa kịp khép.

Đến hiệp thứ ba nghìn, hai người cùng tách ra.

Vô Lượng siết thiên kiếm, khí tức vẫn dày nhưng hơi thở đã loạn. Cao Minh đứng đối diện, im lặng nhìn chín tầng quang ảnh như mặt gương bao quanh thân đối phương.

Cửu Giới Huyền Giám.

Pháp bảo hộ mệnh mà Vô Lượng chưa từng phải dùng đến mức này trong suốt ba nghìn hiệp.

Cao Minh bỗng thôi cười.

Ở dưới, Lục Trầm vừa đẩy lui một người thì cảm nhận được ma khí trên toàn chiến trường thay đổi.

Không phải bùng lên.

Ngược lại.

Nó đang rút về.

Tất cả.

Từng luồng Huyết Sát Ma Khí đang tản giữa trời, từng dòng ma nguyên còn vương trong những vết kiếm, tất cả bị kéo trở lại một điểm duy nhất.

Cao Minh.

Huyết Ma Kiếm lập tức bỏ đối thủ trước mặt.

Nó bay về bên hắn nhanh đến mức để lại một vệt đỏ dài trên tầng không.

Lục Trầm ngẩng lên.

Nàng biết hắn sắp làm gì.

Vô Lượng cũng biết.

Vạn Quỷ Ma Thân được giải phóng.

Và thời gian dừng lại.

Không ai trong Lôi Thiên Vực cảm nhận được khoảng khắc ấy.

Đối với Lục Trầm, nàng chỉ vừa nhìn thấy Huyết Ma Kiếm trở về bên Cao Minh.

Sau đó mọi thứ đã khác.

Nhưng đối với Cao Minh, thế giới trước mặt hoàn toàn đứng yên.

Gió dừng giữa tầng không. Một giọt máu vừa rời khỏi đầu ngón tay Vô Lượng treo bất động. Ánh chớp trên lôi vân giữ nguyên hình dạng. Phía dưới, hàng vạn người bị khóa trong tư thế của chính khoảnh khắc đó.

Cửu Giới Huyền Giám không kích hoạt.

Nó đã được kích hoạt từ trước.

Chín tầng giới bích vẫn bao quanh Vô Lượng, chồng lên nhau giữa một thế giới không còn thời gian.

Huyết Ma Kiếm lơ lửng cạnh Cao Minh.

Không đưa chuôi.

Cao Minh cũng không chạm vào nó.

Sau ba nghìn hiệp, hắn đã nhìn đủ.

Những điểm Vô Lượng quen dùng để chuyển lực. Những nơi thiên kiếm che chắn trước tiên. Vị trí từng lớp giới bích của Cửu Giới Huyền Giám chồng lên nhục thân và nguyên thần.

Hai mươi bốn quỹ đạo kiếm hiện ra.

Huyết Ma Kiếm rung lên.

Cao Minh chỉ truyền một ý.

Kết thúc.

Thanh kiếm biến mất.

Trảm đầu tiên rơi xuống.

Rồi trảm thứ hai.

Không có ai né.

Không ai kịp đỡ.

Không tồn tại cái gọi là “kịp”.

Những đường kiếm đầu tiên xé vào Cửu Giới Huyền Giám, không chém bừa vào từng tầng mà mở đúng những điểm liên kết giữa chúng. Huyết Ma Kiếm tự bẻ quỹ đạo khi có một góc tốt hơn, Huyết Sát Ma Khí kéo dài đường chém ở những nơi kiếm thể không cần trực tiếp đi qua.

Giới bích đầu tiên vỡ.

Rồi tầng thứ hai.

Thứ ba.

Cao Minh không hề vội.

Thời gian đang đứng yên.

Đến khi lớp cuối cùng của Cửu Giới Huyền Giám bị phá, những trảm còn lại mới đi vào người Vô Lượng.

Pháp thể.

Hộ thể.

Kinh mạch vận kiếm.

Nguyên thần.

Huyết Ma Kiếm tự chọn những góc cuối cùng.

Trảm thứ hai mươi ba kết thúc.

Thanh kiếm dừng lại trước Vô Lượng.

Đường cuối cùng đã được định sẵn.

Trảm thứ hai mươi bốn.

Thời gian trở lại.

Đối với tất cả những người còn lại trên chiến trường, Cao Minh chưa từng rời khỏi vị trí.

Chỉ có một tiếng nổ vang lên.

Sau đó toàn bộ hai mươi bốn kết quả cùng xuất hiện trên người Vô Lượng.

Cửu Giới Huyền Giám vỡ trước.

Không phải xuất hiện một vết nứt rồi từ từ hỏng. Chín tầng không gian chồng lên Vô Lượng cùng lúc sụp xuống, mặt kính trên người hắn nổ thành vô số mảnh sáng rồi hóa thành tro.

Sau đó mới tới thương tích.

Một đường kiếm mở từ vai xuống sườn. Một vết khác xuyên qua pháp thể. Hộ thể quanh nguyên thần bị chém thủng. Thiên kiếm trong tay Vô Lượng xuất hiện vết nứt. Máu phun khỏi miệng hắn trước khi những người xung quanh kịp hiểu chuyện gì vừa xảy ra.

Vô Lượng bị đánh bay.

Cả chiến trường không hiểu tại sao.

Trong mắt họ, Cao Minh chỉ vừa giải phóng ma khí.

Rồi ngay khoảnh khắc tiếp theo, pháp bảo hộ mệnh của Đại Tôn Giả đã vỡ, còn bản thân hắn quỳ một gối xuống đất.

Cao Minh vẫn đứng tại chỗ.

Huyết Ma Kiếm từ từ trở lại bên hắn.

Lúc này Vô Lượng mới thật sự hiểu một chuyện.

Nếu không có Cửu Giới Huyền Giám, hắn vừa chết.

Và pháp bảo ấy không còn lần thứ hai.

Vô Lượng không tiếp tục đánh.

Hắn giơ tay.

Những tầng kết giới trong đại quân mở ra.

Lục Trầm cảm thấy sống lưng mình lạnh đi.

Những người đầu tiên bị đưa ra là phàm nhân.

Không có chiến giáp. Không pháp bảo. Có người còn mặc quần áo phủ tro từ những thành trì vừa bị chiến hỏa quét qua. Trẻ nhỏ bị ép đi giữa người lớn, vài đứa khóc đến khản tiếng nhưng không dám kêu lớn. Người già không theo kịp bị kéo lê trên mặt đất.

Sau đó mới tới những người Lục Trầm quen.

Người của Cao Minh.

Những kẻ đã mất tích trong những trận chiến trước, những người bị bắt khi các chiến tuyến bên ngoài Lôi Thiên Vực lần lượt vỡ. Có người cụt tay. Có kẻ xích xuyên qua vai. Có người đã bị phong kinh mạch đến mức đứng không vững, nhưng khi nhìn thấy Cao Minh trên cao vẫn cố ngẩng đầu.

Cao Minh không nói gì.

Một người bị ép quỳ gần hàng đầu bỗng bật cười.

Máu trong miệng hắn chảy xuống cằm.

“Đại nhân.”

Cao Minh nhìn xuống.

Người kia dùng hết sức đứng lên.

Kẻ giữ phía sau lập tức đá vào đầu gối khiến hắn lại khuỵu xuống, nhưng tiếng nói đã lớn hơn.

“Đừng có làm chuyện ngu xuẩn.”

Vài người khác lập tức hiểu.

“Ma Tôn đại nhân!”

“Đánh tiếp!”

Một tiếng khác vang lên từ cuối hàng.

“Bọn ta đi theo ngài đến hôm nay không phải để ngài lấy mạng mình đổi cho bọn ta!”

Người của Chính Đạo bắt đầu quát họ im.

Không ai im.

Một kẻ bị đánh gãy mấy chiếc răng vẫn cười.

“Vạn Giới Ma Tôn!”

Tiếng gọi ấy được người bên cạnh tiếp lấy.

“Vạn Giới Ma Tôn!”

Rồi thêm một người nữa.

Chỉ vài nhịp, hàng người bị trói đã vang đầy danh hiệu mà thiên hạ dùng để nguyền rủa Cao Minh suốt bao nhiêu năm.

“Vạn Giới Ma Tôn!”

“Đừng đầu hàng!”

“Cao Minh, ngươi mà cúi đầu hôm nay thì ngày mai đừng xuống dưới gặp bọn ta!”

Có người bị đánh đến ngã xuống vẫn cố cười.

Lục Trầm nhìn Cao Minh.

Hắn đứng rất yên.

Quá yên.

Vô Lượng chờ cho tiếng gọi lan đủ xa rồi mới lên tiếng.

“Cao Minh. Phong Vạn Quỷ Ma Tâm, khóa thần niệm, không được phản kháng.”

Cao Minh nhìn xuống những người đang gọi mình.

“Còn họ?”

“Khi ngươi chịu trói, bản tôn sẽ thả.”

Huyết Ma Kiếm biến mất.

Không một lời báo trước.

Nó không lao về phía Vô Lượng.

Cũng không lao vào Diệp Minh.

Nó xuất hiện giữa hàng giữ con tin.

Một kiếm tu vừa đặt lưỡi kiếm lên cổ một đứa trẻ chỉ thấy huyết quang lóe qua. Cánh tay cầm kiếm rơi xuống trước khi hắn kịp kêu.

Huyết Ma Kiếm đã sang người thứ hai.

Không phải mọi đòn đều giết.

Có kẻ bị chém cổ tay, có người bị phá pháp bảo, một tên vừa kéo một đứa trẻ làm lá chắn bị thân kiếm đánh văng khỏi chỗ đứng rồi mới bị kiếm khí cắt vào chân.

Nó biết mục tiêu.

Không phải giết hết.

Là kéo lưỡi kiếm khỏi cổ con tin.

Lục Trầm chỉ mất một nhịp để hiểu.

Nàng lao xuống.

Tịch Quang chém ngang một thanh kiếm đang hạ xuống người phụ nữ gần nhất. Hai pháp bảo va nhau, người cầm kiếm nhận ra nàng thì sững lại.

“Lục Trầm!”

“Tránh ra.”

“Ngươi muốn bảo vệ người của Ma Đạo?”

“Đứa trẻ sau lưng ngươi cũng là Ma Đạo sao?”

Người kia khựng lại.

Lục Trầm không chờ.

Tịch Quang ép kiếm hắn lệch sang một bên, nàng xoay người kéo hai người phía sau khỏi vùng hành quyết.

Vô Lượng nhìn thấy.

Hắn không ra lệnh chặn Huyết Ma Kiếm.

Chỉ hạ tay.

Ở một nơi khác trên chiến trường, mười mấy thanh kiếm đồng thời rơi xuống.

Tiếng hét vang lên.

Cao Minh quay đầu.

Một cụm con tin ngã xuống.

Huyết Ma Kiếm đang ở quá xa.

Nó khựng giữa đường.

Vô Lượng hạ tay lần thứ hai.

Một nhóm khác bị chém.

Lần này có trẻ nhỏ.

Tiếng kiếm minh của Huyết Ma Kiếm xé toạc chiến trường.

Nó lao đi.

Nhanh hơn trước.

Một cụm được cứu.

Ở nơi khác lại có người chết.

Lục Trầm vừa chắn được ba kiếm, kéo một người ra khỏi vòng vây, thì phía sau đã có tiếng khóc thét.

Nàng quay lại.

Không kịp.

Máu chảy thành một đường trên mặt đất.

“Đại Tôn Giả!”

Lần đầu tiên trong ngày, Lục Trầm gọi Vô Lượng bằng giọng gần như không còn giữ được bình tĩnh.

“Dừng lại!”

Vô Lượng không nhìn nàng.

Hắn chỉ nhìn Cao Minh.

“Bản tôn không nói lần thứ ba.”

Huyết Ma Kiếm vẫn đang cứu người.

Diệp Minh động.

Sinh Mệnh Đạo tràn xuống đất. Rễ cây phá đá mọc lên thành từng tầng, không đủ để khóa Huyết Ma Kiếm nhưng đủ để ép nó vòng thêm một đoạn mỗi lần đổi mục tiêu.

Thanh kiếm chém đứt một tầng.

Tầng khác lại dựng lên.

Nó bẻ hướng.

Một nhóm con tin ở phía xa lại chết.

Huyết Ma Kiếm đột ngột xoay mũi về phía Diệp Minh.

Toàn bộ huyết văn sáng rực.

Trong một thoáng, sát ý dày đến mức ngay cả những người quanh Diệp Minh cũng lùi lại.

Nhưng ở nơi khác, một thanh kiếm đang được nâng lên trên đầu một đứa trẻ.

Huyết Ma Kiếm dừng.

Rồi quay đi cứu đứa trẻ.

Diệp Minh không đuổi.

Hắn chỉ tiếp tục dựng vật cản.

Lục Trầm nhìn cảnh đó mà cổ họng nghẹn lại.

Nàng chưa bao giờ thấy một thanh kiếm bị ép phải lựa chọn.

Nhưng hôm nay, Huyết Ma Kiếm phải chọn người nào được sống trước.

Cao Minh cũng nhìn thấy.

Hắn biết tuyệt kỹ vừa rồi có thể giết hai mươi bốn mục tiêu trong một miền thời gian đình chỉ.

Ở đây có nhiều hơn hai mươi bốn điểm hành quyết.

Rất nhiều.

Một lần dừng thời gian không thể đưa hắn tới tất cả.

Huyết Ma Kiếm cũng không thể có mặt ở tất cả.

Vô Lượng đã nhìn thấy Huyết Ma Nhị Thập Tứ Trảm.

Và hắn đã chuẩn bị bài toán này cho chính nó.

“Cao Minh!”

Một người bị trói gào lên.

“Đừng gọi kiếm về!”

Kẻ bên cạnh tiếp lời: “Mặc kệ bọn ta!”

Một lưỡi kiếm chém xuống.

Tiếng nói mất đi.

Người khác lập tức gào lớn hơn.

“Vạn Giới Ma Tôn!”

“Ngài mà đầu hàng thì bọn ta chết vô ích!”

“Đánh tiếp!”

Cao Minh nhìn họ.

Huyết Ma Kiếm vừa cứu được thêm một nhóm.

Vô Lượng hạ tay.

Một nhóm khác chết.

Cao Minh nhắm mắt trong một nhịp.

Rồi mở ra.

“Quay lại.”

Huyết Ma Kiếm không nghe.

Nó lao về phía nhóm tiếp theo.

“Quay lại.”

Kiếm thể rung dữ dội giữa không trung.

Cao Minh nhìn những người đang chết ở phía xa.

“Ngươi cứu tiếp, hắn sẽ tiếp tục giết ở chỗ khác.”

Huyết Ma Kiếm vẫn không quay đầu.

“Ta biết ngươi không muốn.”

Giọng Cao Minh thấp xuống.

“Ta cũng không muốn.”

Thanh kiếm cuối cùng dừng lại.

Nó lơ lửng giữa hai nhóm người, mũi kiếm lúc hướng trái, lúc hướng phải. Cả hai phía đều có người đang bị giữ.

Không có lựa chọn đúng.

Cao Minh gọi lần nữa.

“Trở về.”

Huyết Ma Kiếm quay lại rất chậm.

Đằng sau nó, những người bị trói vẫn gào.

“Đừng!”

“Ma Tôn đại nhân, đừng có nghe hắn!”

“Vạn Giới Ma Tôn!”

Huyết Ma Kiếm về tới bên Cao Minh.

Nó không đứng yên như thường ngày.

Toàn thân kiếm rung mạnh.

Cao Minh nhìn nó rồi nhìn xuống những người phía dưới.

Một người già trong số những tu sĩ đi theo hắn đã khóc.

Không phải vì sợ chết.

“Cao Minh, bọn ta không cần ngươi cứu theo cách này.”

Cao Minh nghe rất rõ.

Hắn mất một lúc mới trả lời.

“Ta biết.”

Người kia lắc đầu.

“Vậy thì đừng làm.”

Cao Minh nhìn ông.

“Các ngươi đã theo ta lâu như vậy mà vẫn chưa hiểu sao?”

Giọng hắn không lớn, nhưng người gần đó đều nghe được.

“Ta nghe các ngươi. Không có nghĩa là lần nào ta cũng làm theo.”

Người già cứng người.

Cao Minh quay sang Vô Lượng.

“Thả trẻ con trước.”

Vô Lượng im lặng.

Cao Minh nhíu mày.

“Ta đã nhượng một bước. Ngươi cũng nên có chút thành ý.”

Sau một khoảng ngắn, Vô Lượng ra hiệu.

Nhóm trẻ nhỏ cùng một số phàm nhân lớn tuổi được đưa khỏi hàng.

Không phải tất cả.

Nhưng ít nhất thanh kiếm đã rời khỏi cổ đứa trẻ kia.

Cao Minh nhìn người mẹ ôm con chạy về phía sau. Đến khi hai mẹ con khuất hẳn khỏi tầm mắt, hắn mới nói:

“Làm đi.”

Huyết Ma Kiếm phát ra một tiếng kiếm minh gay gắt.

Cao Minh quay đầu.

Thanh kiếm đã bay tới trước hắn.

“Ta nghe thấy rồi.”

Nó không lùi.

“Ta chưa chết.”

Huyết Ma Kiếm vẫn bất động.

Cao Minh nhìn nó, giọng nhỏ hơn.

“Nếu ngươi thật sự muốn giúp ta thì đừng khiến họ có lý do giết người.”

Huyết văn trên thân kiếm chớp tắt vài lần.

Cuối cùng nó mới chậm chạp lùi sang bên.

Phong ấn thứ nhất giáng xuống.

Cao Minh cảm thấy linh lực quanh kinh mạch bị ép ngừng lưu chuyển. Đến tầng thứ ba, Vạn Quỷ Ma Tâm bắt đầu chịu áp chế; một lực lạnh lẽo siết quanh Đạo Cơ, khiến ma nguyên vốn vận hành theo bản năng cũng trở nên nặng nề.

Đến tầng thứ sáu, chân hắn trượt nửa bước.

Huyết Ma Kiếm lập tức lao tới.

“Không.”

Nó dừng.

Phong ấn tiếp tục.

Đến tầng thứ chín, một bên đầu gối Cao Minh chạm đất trong khoảnh khắc trước khi hắn chống người đứng dậy trở lại.

Vô Lượng nhìn hắn.

“Quỳ xuống.”

Cao Minh ngẩng đầu, thở hơi nặng.

“Đừng được một tấc lại muốn một thước.”

Vô Lượng không nói.

Hắn chỉ nhìn người phía sau.

Những con tin vẫn chưa được thả.

Cao Minh thấy vậy, gương mặt chậm rãi lạnh xuống.

“Vô Lượng.”

Đối phương đã gọi thiên kiếm trở lại tay.

“Ngươi hứa thả người.”

“Bản tôn sẽ thả sau khi mọi chuyện kết thúc.”

Cao Minh nhìn hắn.

Không nói gì nữa.

Chính sự im lặng ấy khiến vài người phía sau Vô Lượng vô thức lui lại.

Lục Trầm đứng ở mép chiến trường, từ đầu tới cuối đều nhìn thấy.

Nàng đã biết Vô Lượng không đáng tin từ khi hắn dùng phàm nhân làm con tin. Nhưng vẫn có một phần trong nàng mong hắn ít nhất còn giữ giới hạn cuối cùng.

Phần đó chết khi nàng nhìn thấy hướng thiên kiếm.

Vô Lượng không định bắt Cao Minh.

Hắn định xóa hắn.

Kiếm ý không khóa nhục thân. Nó xuyên qua những tầng hộ thể còn lại, thẳng tới nguyên thần.

Lục Trầm lập tức tính khoảng cách.

Quá xa để Tịch Quang tới trước.

Nếu dùng Bạch Hồng Quán Nhật, nàng có thể rút ngắn phần lớn quãng đường, nhưng ba tầng cấm chế còn lại trên người sẽ kéo chậm ít nhất một nhịp.

Một nhịp đủ để kiếm rơi xuống.

Cao Minh có thể phá phong ấn.

Nàng biết hắn có thể thử.

Nàng cũng biết cái giá của lần cưỡng phá ấy sẽ rơi xuống đâu.

Phía sau Vô Lượng, kiếm vẫn đặt trên cổ những người bị giữ.

Lục Trầm siết chặt tay.

Nàng muốn tìm một phương án khác.

Thật sự muốn.

Nàng đã quen với việc trước khi chấp nhận một cái giá, phải biết còn đường nào chưa thử. Vì vậy nàng lại tính thêm một lần, rồi một lần nữa, nhanh đến mức những khả năng hiện ra và biến mất gần như cùng lúc.

Không có.

Thiên kiếm bắt đầu hạ xuống.

Lục Trầm bỗng nhận ra mình đang sợ đến mức các ngón tay lạnh ngắt.

Nàng từng nghĩ nếu một ngày thật sự phải đối diện cái chết, có lẽ mình sẽ bình tĩnh hơn. Ít nhất sẽ giống những tiền bối trong các câu chuyện mà nàng từng đọc, hiểu rõ điều cần làm rồi làm nó.

Hóa ra không phải.

Nàng không muốn chết.

Ý nghĩ ấy chen vào giữa tất cả tính toán một cách thô bạo.

Nàng còn rất nhiều chuyện chưa làm xong. Có những nơi chưa từng đi. Có những kiếm pháp mới chỉ hiểu được một nửa. Có một vò rượu Cao Minh giấu mà hắn chắc chắn vẫn tưởng nàng không biết. Có vài lần nàng đã định nói với hắn một chuyện rồi lại thôi, bởi chưa tìm được lúc nào thích hợp.

Nếu chết bây giờ, sẽ chẳng còn lúc nào thích hợp nữa.

Tịch Quang rung cạnh nàng.

Lục Trầm nhìn thanh kiếm của mình.

Thiên kiếm phía trước lại hạ thêm.

Nàng biết không còn thời gian.

“Ở đây.”

Tịch Quang không nhúc nhích.

Lục Trầm gần như muốn mắng nó.

Nhưng rồi nàng lại thôi.

Nàng hiểu cảm giác ấy.

Nếu đổi lại, nàng cũng sẽ không chịu đứng nhìn.

Lục Trầm đốt Thiên Kiếm Linh Tâm.

Đau đến sớm hơn nàng tưởng.

Không phải một cơn đau nằm ở một chỗ cụ thể, mà như thể toàn bộ những gì nàng đã tu luyện suốt nhiều năm cùng lúc bị kéo căng tới giới hạn. Cấm chế trên người vỡ tung. Lục Trầm lao đi, bước đầu tiên còn giữ được thân pháp, bước thứ hai đã nghe mùi máu trong miệng.

Đến bước thứ ba, nàng nhìn thấy Cao Minh quay đầu.

Gương mặt hắn ban đầu chỉ có ngạc nhiên.

Sau đó thiên kiếm xuyên qua người nàng.

Lục Trầm không nghe rõ âm thanh.

Cảm giác đầu tiên thậm chí không phải đau.

Chỉ là lạnh.

Rồi sức lực trong chân biến mất.

Nàng đổ về phía trước, được Cao Minh bắt lấy trước khi chạm đất.

Hắn nhìn xuống vết thương.

Ánh mắt dừng ở đó rất lâu.

“Lục Trầm?”

Nàng muốn trả lời ngay nhưng hơi thở không ra được. Phải đến lần thứ hai mới miễn cưỡng hít được một ít không khí.

Cao Minh đưa tay về phía mũi kiếm xuyên khỏi ngực nàng.

“Đừng rút.”

Hắn dừng lại.

Lục Trầm nhắm mắt trong một thoáng vì cơn đau vừa dội tới. Khi mở ra, bàn tay hắn vẫn còn ở đó.

“Rút bây giờ không giúp gì được.” Nàng cố nói hết câu. “Chỉ làm ta mất máu nhanh hơn.”

Cao Minh nhìn nàng như thể muốn nói gì đó, cuối cùng lại không nói.

Lục Trầm cúi xuống mới thấy tay hắn đang giữ vai mình quá chặt.

“Cao Minh, ngươi đang làm ta đau thêm.”

Hắn lập tức nới lực.

“Xin lỗi.”

Lục Trầm ngẩng lên.

Câu xin lỗi ấy làm nàng khó chịu hơn cả bàn tay vừa rồi.

Cao Minh rất ít khi xin lỗi nhanh như vậy.

Nàng nhìn kỹ hắn, rồi mới nhận ra sắc mặt hắn đã trắng hơn bình thường.

“Ta phá phong ấn.”

“Không được.”

“Ta không thể đứng đây nhìn nàng chết.”

Giọng Cao Minh thấp và rất bình thường, nhưng chính vì quá bình thường nên Lục Trầm nghe ra phần hắn đang cố giữ lại.

Nàng thở chậm hơn.

“Ta biết.”

“Vậy thì đừng cản ta.”

“Những người phía sau sẽ chết.”

“Ta sẽ cứu nàng trước.”

Lục Trầm nhắm mắt một lát.

Nàng đã biết câu trả lời này từ trước.

Vẫn đau khi nghe.

“Ta không muốn ngươi cứu ta bằng cách đó.”

Cao Minh nhìn nàng.

“Ta không quan tâm.”

“Nhưng ta có.”

Hắn im.

Lục Trầm không còn đủ hơi để tranh luận dài. Nàng cũng không muốn biến những giây cuối cùng thành một cuộc tranh biện về đúng sai.

Nàng chỉ nói điều cần nói.

“Ta sợ chết.”

Cao Minh chớp mắt.

“Ta biết.”

“Không, ngươi chưa biết.”

Giọng nàng run đi dù đã cố giữ.

“Ta thật sự rất sợ. Ta còn muốn sống.”

Cao Minh nhìn nàng lâu đến mức Lục Trầm gần như không chịu nổi.

“Vậy thì ở lại.”

Nàng bật cười rất khẽ, rồi lập tức phải dừng vì vết thương đau nhói.

“Ngươi nói cứ như ta chỉ cần đồng ý là được.”

“Ít nhất đừng tự làm mình chết nhanh hơn.”

Tịch Quang lúc ấy mới đuổi tới.

Thanh kiếm dừng bên cạnh Lục Trầm, rung mạnh đến mức kiếm quang liên tục tán ra.

Nàng nhìn nó.

Một lúc rất lâu.

“Ta xin lỗi.”

Tịch Quang tiến sát hơn.

Cao Minh nhìn thanh kiếm, rồi nhìn tay nàng bắt đầu kết ấn.

Hắn hiểu.

“Không.”

Lục Trầm tiếp tục.

“Đừng làm việc này.”

“Ta không còn cách khác.”

“Ta sẽ tìm.”

“Không kịp nữa.”

“Lục Trầm.”

Nàng vẫn không nhìn hắn.

Nếu nhìn, nàng biết mình sẽ dao động.

Thiên Kiếm Linh Tâm bắt đầu nứt.

Cơn đau lần này khiến hai bàn tay nàng run mạnh. Một ấn quyết làm sai, phải dừng lại bắt đầu từ đầu. Máu trào lên cổ họng, nàng nuốt xuống nhưng vẫn còn một ít chảy khỏi khóe môi.

Cao Minh thấy hết.

“Dừng lại một chút.”

Giọng hắn đã khàn.

“Chỉ một chút thôi. Để ta nghĩ.”

Lục Trầm suýt dừng thật.

Chính điều đó làm nàng không dám nghe thêm.

“Cao Minh, nếu ngươi còn nói nữa, ta sẽ không làm được.”

Hắn im bặt.

Nàng nhắm mắt.

Trong sự im lặng ấy, từng phần căn cơ của nàng bị tháo ra khỏi chính mình. Linh căn bị ép tan vào trận ấn. Huyết mạch trở thành đường dẫn. Đến khi nhục thân bắt đầu bị kéo vào cấu trúc trận pháp, Lục Trầm mới cảm thấy thứ đáng sợ nhất không còn là đau.

Mà là cảm giác mình đang biến mất.

Không gian phía sau Cao Minh méo đi.

Một khe nứt mở ra.

Không có tọa độ.

Không ai biết bên kia là đâu.

Nhưng có đường.

Chỉ thế đã đủ.

Huyết Ma Kiếm phát ra tiếng kiếm minh dữ dội.

Nó tự lao về phía Vô Lượng.

Lần này Cao Minh không gọi nó về.

Ba tầng hộ trận bị xé rách. Diệp Minh vừa bước lên, Huyết Ma Kiếm lập tức đổi hướng, kiếm quang đỏ quét ngang khiến hắn phải dừng lại.

Cao Minh không nhìn.

Hắn nắm lấy cổ tay Lục Trầm.

Nàng giật mình.

“Buông ra.”

“Ta đưa nàng đi cùng.”

“Không được. Thân thể ta đang giữ trận.”

“Ta biết.”

“Vậy ngươi phải hiểu kéo ta khỏi đây sẽ làm đường dẫn sụp.”

“Ta hiểu.”

Lục Trầm nhìn hắn, gần như tức đến bật khóc.

“Vậy tại sao ngươi vẫn làm?”

Cao Minh không trả lời ngay.

Bàn tay hắn không hề nới ra.

Cuối cùng hắn nói:

“Bởi vì ta không muốn đi một mình.”

Không phải lời giải thích hợp lý.

Cũng chẳng giống Cao Minh.

Chính vì vậy Lục Trầm không biết phải nói gì.

Nước mắt nàng rơi xuống trước khi kịp quay mặt đi.

“Ngươi đúng là…”

Nàng không tìm ra từ.

Cao Minh cười rất nhạt.

“Để sau hãy mắng.”

“Có thể không còn sau nữa.”

Câu vừa ra khỏi miệng, Lục Trầm đã hối hận.

Nụ cười trên mặt Cao Minh mất đi.

Nàng muốn sửa lại, nhưng không tìm được cách nào vừa thật vừa đỡ tàn nhẫn hơn.

Cuối cùng chỉ còn biết siết tay hắn.

“Ta không muốn bỏ ngươi lại.”

Cao Minh cúi nhìn bàn tay hai người.

“Vậy đừng.”

“Ta đã nói là ta không điều khiển được chuyện đó.”

“Ta cũng đã nói ta sẽ tìm cách.”

Nàng định cãi.

Không còn kịp.

Đại trận sụp.

Ánh sáng nuốt lấy cả hai.

Huyết Ma Kiếm ở ngoài trận lập tức bỏ Diệp Minh, xuyên qua tầng kiếm khí cuối cùng rồi lao theo liên kết bản mệnh.

Tịch Quang cũng bị cuốn vào.

Lôi Thiên Vực biến mất.

Khi Cao Minh tỉnh lại, hắn vẫn đang ôm Lục Trầm.

Trong vài nhịp đầu, hắn không nhớ nổi chuyện gì đã xảy ra sau khi đại trận vỡ. Chỉ cảm thấy người trong tay quá nhẹ.

Rồi nhận ra quá lạnh.

Cao Minh đặt tay lên cổ nàng.

Không có mạch.

Hắn vẫn giữ nguyên tay ở đó thêm một lúc, như thể chỉ là mình đặt sai vị trí. Sau đó chuyển sang cổ tay.

Vẫn không.

Thần niệm tràn vào cơ thể.

Lần đầu hắn tìm Thiên Kiếm Linh Tâm.

Không thấy.

Lần thứ hai tìm thần hồn.

Không thấy.

Cao Minh chậm lại.

Hắn kiểm tra từng nơi một, cẩn thận hơn mức cần thiết. Hắn biết thần niệm của mình không có lý do gì bỏ sót một thứ như vậy, nhưng vẫn quay lại những vị trí vừa xem qua.

Cuối cùng hắn dừng ở nơi đáng lẽ phải có nguyên thần.

Trống không.

Cao Minh ngồi rất lâu.

Huyết Ma Kiếm bay tới từ phía sau rồi dừng bên cạnh. Lần này nó không kêu.

Cao Minh cúi đầu nhìn Lục Trầm.

Một ít máu đã khô ở khóe môi nàng.

Hắn dùng ngón tay lau đi.

“Nàng nói muốn sống.”

Không ai trả lời.

Cao Minh chờ thêm một lúc rồi khẽ nói:

“Ta nghe rất rõ.”

Vẫn im lặng.

Hắn không biết mình đang mong đợi điều gì.

Có thể là một cái cau mày. Một câu bảo hắn nói nhỏ thôi. Hoặc đơn giản chỉ là một hơi thở đủ nhẹ để hắn có thể tự mắng mình vừa rồi kiểm tra quá vội.

Không có.

Cao Minh cúi xuống, trán chạm vào tóc nàng.

Hắn không khóc.

Ít nhất ban đầu không.

Chỉ ngồi như vậy, hai tay vẫn giữ nguyên, đến khi một giọt nóng rơi xuống mu bàn tay Lục Trầm hắn mới nhận ra mắt mình đã ướt.

Cao Minh nhìn giọt nước ấy một lúc.

Sau đó bật cười.

Tiếng cười nhỏ và đứt.

“Phiền thật.”

Huyết Ma Kiếm khẽ rung.

“Ta còn chưa hỏi nàng một chuyện.”

Hắn nói như thể Lục Trầm chỉ đang ngủ.

“Câu nàng định nói trước khi mở trận.”

Không có câu trả lời.

Cao Minh đợi.

Rồi lại dùng thần niệm kiểm tra nàng thêm một lần nữa.

Vẫn không có gì.

Lần này hắn không kiểm tra lại.

Bàn tay đặt lên ngực mình.

Vạn Quỷ Ma Tâm bắt đầu nghịch chuyển.

Huyết Ma Kiếm lập tức phát ra một tiếng rung mạnh.

“Ta biết.”

Thanh kiếm bay tới chắn trước mặt.

Cao Minh ngẩng lên.

“Ta biết mình đang làm gì.”

Nó không tránh.

Ma văn trên kiếm chớp tắt liên tục.

Cao Minh nhìn nó rất lâu, sau đó đưa tay đặt lên mặt kiếm. Hắn không nắm chuôi. Chỉ áp lòng bàn tay lên lớp Ma Kim lạnh.

Lần này Huyết Ma Kiếm không lùi khỏi tay hắn.

“Cho ta thử.”

Thanh kiếm rung nhẹ.

“Ta không bảo ngươi đồng ý.”

Huyết văn chậm dần.

Cao Minh thu tay lại.

Vạn Quỷ Ma Tâm tiếp tục nghịch chuyển.

Đạo hạnh tích lũy qua vô số năm bị hắn kéo khỏi căn cơ rồi đưa vào cơ thể Lục Trầm. Ban đầu chỉ là vài trăm năm, sau đó con số chẳng còn ý nghĩa. Ma nguyên đi qua kinh mạch nàng, tìm một thứ để giữ lại nhưng không tìm thấy nơi nào có thể đáp lại.

Hắn vẫn tiếp tục.

Căn cơ bắt đầu đau.

Đến khi máu chảy ra khỏi khóe mắt, Cao Minh mới nhận ra mình đã đi xa đến mức nào.

Huyết Ma Kiếm lại rung.

“Đừng.”

Cao Minh vẫn nhìn Lục Trầm.

“Lần này đừng ngăn ta.”

Hắn biết Vạn Quỷ Ma Tâm có thể chữa thần hồn bị thương khi bản nguyên còn tồn tại.

Nàng không còn bản nguyên để chữa.

Cao Minh hiểu.

Hiểu quá rõ.

Nhưng hiểu một việc không khiến hắn chấp nhận nó dễ hơn.

Hắn đã từng nghĩ nếu mình đủ mạnh thì rất nhiều chuyện trên đời sẽ trở nên đơn giản. Sau đó hắn thật sự bước lên nơi cao nhất, nhìn xuống những đối thủ từng khiến người khác sợ hãi, và nhận ra phần lớn thứ từng khó quả thật đã trở nên đơn giản.

Chỉ có một số chuyện không hỏi hắn mạnh đến đâu.

Chúng xảy ra.

Rồi lấy người đi.

Cao Minh không muốn chấp nhận thêm một lần nữa.

Nếu hồn nàng đã tán, hắn muốn tự mình tìm cho tới mảnh cuối cùng.

Nếu không còn gì, hắn muốn chính mình là người xác nhận.

Không phải thiên đạo nói.

Không phải Vô Lượng nói.

Cũng không phải bất kỳ quy luật nào trên đời thay hắn kết luận.

Đạo hạnh tiếp tục cháy.

Bàn tay Lục Trầm vẫn lạnh.

Cao Minh nắm lấy.

“Nàng nói mạng nàng là của nàng.”

Hắn dừng một lúc để thở.

“Ta nhớ.”

Khóe môi hắn cong lên rất nhẹ.

“Nhưng mạng ta cũng là của ta. Chuyện ta dùng nó vào đâu, lần này nàng không được quản.”

Huyết Ma Kiếm phát ra một tiếng rung thấp.

Cao Minh không quay lại.

“Nếu tỉnh dậy, nàng cứ mắng.”

Ma nguyên trong Vạn Quỷ Ma Tâm bắt đầu rơi khỏi trạng thái ổn định.

“Muốn đánh nhau cũng được.”

Ý thức của hắn mờ dần.

“Chỉ cần tự nàng làm.”

Hắn còn định nói tiếp.

Nhưng câu cuối cùng chỉ còn nằm trong đầu.

Đừng để ta lại một mình.

Rồi Cao Minh không còn biết gì nữa.

Khi ý thức trở lại, thứ đầu tiên hắn nghe thấy là một tiếng ù kéo dài.

Cao Minh mở mắt.

Trần nhà thấp hơn hắn tưởng. Trên đó là những ống sáng trắng lạnh, nối nhau thành một hàng dài, phát ra thứ âm thanh liên tục khiến người nghe khó chịu dù không lớn.

Dưới lưng hắn là thảm.

Ẩm.

Có mùi mốc.

Cao Minh nằm yên đúng một nhịp rồi lập tức quay sang bên cạnh.

Không có ai.

Hắn ngồi bật dậy.

“Lục Trầm?”

Tiếng gọi chạy qua những căn phòng màu vàng rồi vọng lại từ một góc nào đó.

Không trả lời.

Cao Minh đứng lên quá nhanh, cơ thể lập tức mất thăng bằng. Hắn phải chống tay lên tường mới đứng vững.

Đạo hạnh đã mất không phải ảo giác.

Vạn Quỷ Ma Tâm vẫn còn, nhưng suy yếu đến mức chính hắn cũng phải mất vài nhịp mới cảm nhận được dòng ma nguyên.

Cao Minh bỏ qua.

Hắn mở thần thức.

Rồi khựng lại.

Không gian nơi này không đúng.

Căn phòng trước mắt rõ ràng nối sang một hành lang ở bên trái, nhưng thần thức của hắn lại nhận được một khoảng cách không thể khớp với hình dạng đang thấy. Những góc tường đáng lẽ nằm sau lưng lại xuất hiện ở hướng khác. Hành lang nhìn chỉ dài vài chục trượng nhưng cảm nhận không tìm được một điểm cuối ổn định.

Bình thường, một nơi như vậy đủ khiến Cao Minh dừng lại nghiên cứu rất lâu.

Hôm nay hắn chỉ tìm một thứ.

Khí tức của Lục Trầm.

Tịch Quang.

Một dấu kiếm.

Bất cứ gì.

Không có.

Linh khí ở đây dày đến bất thường, nhiều hơn phần lớn những thánh địa hắn từng bước vào. Vạn Quỷ Ma Tâm vừa vận hành đã tự động kéo nguồn linh khí ấy vào cơ thể.

Cao Minh cũng không quan tâm.

Một tiếng kiếm minh vang lên phía sau.

Huyết Ma Kiếm vẫn còn.

Thanh kiếm lơ lửng giữa căn phòng, huyết văn mờ đi rất nhiều nhưng vẫn sáng. Nó không bay tới cạnh Cao Minh ngay như thường lệ mà dừng cách hắn vài bước.

Hai bên nhìn nhau.

Cao Minh không biết một thanh kiếm có thể buồn hay không.

Hắn cũng không muốn nghĩ về câu hỏi ấy.

Huyết Ma Kiếm chậm rãi xoay mũi về một hành lang.

Cao Minh nhìn theo.

“Ngươi cảm thấy gì?”

Thanh kiếm rung nhẹ.

Không phải câu trả lời mà hắn hiểu được.

Cao Minh hít vào, chờ cơn choáng qua bớt rồi mới rời tay khỏi tường.

“Vậy đi xem.”

Huyết Ma Kiếm bay trước một đoạn.

Cao Minh bước theo.

Không có lời thề.

Không có câu nói rằng hắn sẽ lật tung trời đất hay phá nát luân hồi.

Sau tất cả những gì vừa xảy ra, những lời ấy nghe quá dễ dàng.

Lục Trầm đã nói với hắn rằng nàng muốn sống.

Cao Minh nhớ từng chữ.

Chừng nào hắn còn chưa tận mắt tìm thấy câu trả lời khác, hắn sẽ coi nàng vẫn còn đâu đó.

Và hắn sẽ đi tìm.'''

html = HTML.read_text(encoding="utf-8")

start_token = "const prologue=`"
start = html.index(start_token)
end_match = re.search(r"`;\s*const initial=", html[start:])
if not end_match:
    raise RuntimeError("Missing prologue closing anchor")
end = start + end_match.start()
html = html[:start] + "const prologue=`" + PROLOGUE + "`;" + html[end + 2:]

old_turn = '{role:"gm",text:"LƯỢT 1\\n\\nKhông có liên lạc với Iris, Syvial hay Black Blood. Bạn điều khiển Cao Minh từ đây."}'
new_turn = '{role:"gm",text:"LƯỢT 1\\n\\nCao Minh tỉnh lại một mình tại Level 0. Không có dấu vết của Lục Trầm. Bạn điều khiển Cao Minh từ đây."}'
if old_turn not in html:
    raise RuntimeError("Missing Cao Minh Turn 1 prologue handoff anchor")
html = html.replace(old_turn, new_turn, 1)

signature_anchor = 'const oldPrologueSignature="Không beacon. Không telemetry.";'
if signature_anchor not in html:
    raise RuntimeError("Missing prologue migration signature anchor")
html = html.replace(
    signature_anchor,
    signature_anchor + '\nconst legacyCaoPrologueSignature="Bữa tối bắt đầu như bao lần khác.";',
    1,
)

old_condition = 'if(currentOpening.length<1000||currentOpening.includes(oldPrologueSignature)){'
new_condition = 'if(currentOpening.length<1000||currentOpening.includes(oldPrologueSignature)||currentOpening.includes(legacyCaoPrologueSignature)){'
if old_condition not in html:
    raise RuntimeError("Missing prologue migration condition anchor")
html = html.replace(old_condition, new_condition, 1)

old_turn_migration = 'if(state.log[1]&&String(state.log[1].text||"").startsWith("TURN 1")) state.log[1]=initial.log[1];'
new_turn_migration = 'if(state.log[1]&&(String(state.log[1].text||"").startsWith("TURN 1")||String(state.log[1].text||"").startsWith("LƯỢT 1"))) state.log[1]=initial.log[1];'
if old_turn_migration not in html:
    raise RuntimeError("Missing Turn 1 migration anchor")
html = html.replace(old_turn_migration, new_turn_migration, 1)

HTML.write_text(html, encoding="utf-8")

final = HTML.read_text(encoding="utf-8")
if not final.split("const prologue=`", 1)[1].startswith("Ngày cuối cùng của Lôi Thiên Vực"):
    raise RuntimeError("Cao Minh prologue was not installed")
if new_turn not in final:
    raise RuntimeError("Cao Minh Turn 1 handoff was not installed")
if "legacyCaoPrologueSignature" not in final:
    raise RuntimeError("Turn 1 prologue migration was not installed")

print("Cao Minh final-world prologue installed and Turn 1 migration updated.")
