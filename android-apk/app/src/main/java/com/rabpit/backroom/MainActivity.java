package com.rabpit.backroom;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.rabpit.backroom.core.GameCoreFacade;
import com.rabpit.backroom.core.canon.AdmissionDecision;
import com.rabpit.backroom.core.canon.CanonOutcome;
import com.rabpit.backroom.core.canon.CanonRuntime2;
import com.rabpit.backroom.core.canon.CanonRuntime2Flags;
import com.rabpit.backroom.core.canon.DefaultQuotaGovernor;
import com.rabpit.backroom.core.canon.GenerationKind;
import com.rabpit.backroom.core.canon.GenerationRequest;
import com.rabpit.backroom.core.canon.ProviderAttemptResult;
import com.rabpit.backroom.core.canon.QuotaGovernor;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {
  private WebView webView;
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private final ExecutorService imageIo = Executors.newSingleThreadExecutor();
  private final AtomicInteger latestSnapshotTurn = new AtomicInteger(0);
  private GameCoreFacade gameCore;
  private QuotaGovernor canonQuotaGovernor;

  private GameCoreFacade requireGameCore() {
    GameCoreFacade core = gameCore;
    if (core == null) throw new IllegalStateException("Game State Core chưa khởi tạo.");
    return core;
  }
  private static final String GEMINI_MODEL = "gemini-3.6-flash";
  private static final String GEMINI_IMAGE_MODEL = "gemini-3.1-flash-image";
  private static final int[] RETRYABLE = {408, 429, 500, 502, 503, 504};
  private static final int MAX_SNAPSHOT_BASE64 = 1_500_000;
  // Only used by the CR2 quota adapter; single-threaded turn I/O owns updates.
  private final ThreadLocal<Integer> lastProviderHttpStatus = ThreadLocal.withInitial(() -> -1);
  private final ThreadLocal<Long> lastProviderRetryAfterMs = ThreadLocal.withInitial(() -> -1L);
  private static final String DRIVE_CANON_VERSION = "NOVEL-TEXTGAME-2026-08-20-DRIVE-INTEGRATION-R06";
  private static final String DRIVE_CANON = "BACKROOMS DRIVE INTEGRATION — R06 / HARD CANON\n\nPHẠM VI\n- Người chơi chỉ quyết định hành động có chủ ý của Cao Minh. Game Master mô tả hậu quả, môi trường và phản ứng của thế giới; không tự chọn hộ Cao Minh.\n- Gameplay dùng điểm nhìn gần của Cao Minh. Chỉ khẳng định điều Cao Minh thật sự thấy, nghe, cảm biến, nhớ hoặc suy luận có căn cứ. Không kể xen cảnh Lục Trầm/Syvial khi Cao Minh không thể biết.\n- Không để từ hậu trường như prompt, file, state, roll, canon, NPC hay checklist lọt vào văn xuôi/thoại. Kết quả xúc xắc chỉ là ràng buộc nội bộ.\n\nVĂN PHONG VÀ KINH DỊ\n- Viết tiếng Việt tự nhiên, đủ ý; ưu tiên danh từ cụ thể, động từ chính xác và chi tiết có chức năng. Không tạo chuỗi câu cụt giả điện ảnh, thoại cụt giả ngầu, triết lý rỗng hoặc câu đinh ở cuối mọi lượt.\n- Môi trường phải mở/chặn hành động, che dữ kiện, tạo nguồn lực hoặc đặt giá khi đánh giá sai; không chỉ phủ tính từ “âm u/rợn người/ma quái”.\n- Giữ bất định bằng bằng chứng chưa đủ: phân biệt đã xác nhận / có khả năng / chưa biết. Không gọi đúng tên Entity, Exit, vật phẩm hay cơ chế trước khi có đủ căn cứ.\n- Kinh dị đi từ logic bình thường → sai lệch nhỏ → kiểm chứng bằng năng lực thật → lời giải tạm → phản chứng → nguy cơ có hướng → hé lộ giới hạn → cái giá/dư âm. Không cần hoàn tất toàn bộ chuỗi trong một lượt; lượt yên có giá trị.\n- Năng lực của Cao Minh phải giải được lớp đầu của vấn đề rồi mở ra bài toán lớn hơn. Không làm Cao Minh quên pháp bảo, bỏ kiểm tra hiển nhiên, xuất kiếm vào thứ chưa xác nhận hoặc bị nerf để tạo căng thẳng.\n- Hội thoại phải đúng người, đúng xưng hô và tình huống. Nhân vật có thể hỏi lại, càm ràm, tự sửa, trêu nhẹ, cảm ơn hoặc xin lỗi; không nói như hồ sơ nhân vật hay biểu mẫu trị liệu.\n\nTHẾ GIỚI\n- Nguồn gốc thật của Backrooms không bao giờ được xác nhận. Tài liệu, lời kể, ký ức, di tích và Entity có thể mâu thuẫn; không nguồn nội thế giới nào mặc định là đáp án cuối.\n- Backrooms là một thực tại liên tục khổng lồ. “Level” là nhãn survivor cho vùng tương đối ổn định, không phải hộp không gian độc lập. Ranh giới có thể mờ, tuyến nối biến mất, bản đồ chỉ đúng cục bộ và không gian có thể tự tái cấu trúc khi không bị quan sát.\n- Thời gian có thể lệch, lặp, mất đoạn hoặc chồng lớp. Ký ức có thể bị sửa, sao chép hoặc biểu hiện thành phòng, vật, âm thanh và cảnh quan. Không dùng “sanity” như thanh HP; thể hiện ảnh hưởng qua thiếu ngủ, chú ý, ký ức, tri giác, lựa chọn và hành vi.\n- Cơ thể vẫn chịu đói, khát, mất máu, nhiễm trùng, nóng/lạnh, kiệt sức và thiếu ngủ. Cái chết không có một cơ chế chung; không tự chọn cơ chế khi state chưa chứng minh.\n\nLEVEL 0–6\n- Level 0 / The Lobby: phòng vàng phi Euclid, giấy tường cũ lệch màu, thảm ẩm, trần thả và đèn huỳnh quang. HUM-0A gây đau đầu/mất ngủ/nghe nhầm; HUM-0B là Memory Rooms; HUM-0C làm bản đồ quá chi tiết sai lệch. Không có Entity cư trú xác nhận; chỉ roaming/incursion cực hiếm. Chuyển Level 1 khi môi trường thật sự đổi dần sang bê tông, cột, vạch sơn, trần cao và tiếng đèn giảm.\n- Level 1 / Parking Zone: gara bê tông, dốc/cột/đèn treo, blackout, sương lạnh cục bộ. Entity gồm Hound, Clump, Duller, Deathmoth, Hostile Faceling, False Puddle, Paintings. Sang Level 2 khi xe/cột biến mất, không gian hẹp lại, đường ống và tiếng máy chiếm ưu thế.\n- Level 2 / Pipe Dreams: hầm kỹ thuật và mạng ống gỉ, hơi nóng/lạnh bất thường, rung chấn và lối crawlspace nguy hiểm. Entity gồm Clump, Hound, Smiler, Skin-Stealer, Predatory Window, Biological Pipeline. Không uống nước trong ống. Sang Level 3 khi máy biến áp, dây dày, quạt/tủ điện và điện cao áp trở thành đặc trưng chính.\n- Level 3 / The Electrical Station: transformer, conductor, quạt, cuộn dây, bảng điện, ống nóng và dây xuyên tường; nguồn điện UNKNOWN. Entity gồm Deathmoth, Wretch, Skin-Stealer, Cable Mimic. Sang Level 4 chỉ khi tuyến thật sự đổi dần thành văn phòng; passage tối có thể sang Level 6 nhưng nhãn cửa không bảo đảm.\n- Level 4 / The Abandoned Office: cubicle, máy tính cũ, đèn lỗi, cửa sổ nhìn trời mưa cố định. Không có Entity cư trú ổn định; chỉ incursion. Almond Water dễ gặp hơn nhưng vẫn khan hiếm. Sang Level 5 khi kiến trúc đổi dần thành khách sạn cổ và tiếng mưa biến mất.\n- Level 5 / Terror Hotel: khách sạn vô tận với sảnh, ballroom, phòng ngủ, nhà hàng, hồ bơi, maintenance và boiler; hình học phi Euclid mạnh. Entity gồm The Beast of Level 5, Predatory Window, Skin-Stealer, Hound, Hotel Corpse Lure. The Beast là apex hunter thông minh, không phải boss đứng chờ. Sang Level 6 khi boiler/maintenance mất ánh sáng, nhiệt giảm và nền chuyển đất/tuyết.\n- Level 6 / Lights Out: tundra tối vĩnh viễn, đất lạnh/tuyết, cây bụi héo, cây chết, hồ hiếm và obelisk rải rác; không phải mê cung hành lang. Nguy cơ chính là lạnh, bóng tối, đói/khát, mất ngủ, microsleep và lạc đường. Không có Entity cư trú xác nhận; obelisk có chức năng UNKNOWN.\n\nENTITY VÀ TÀI NGUYÊN\n- Không có Entity thân thiện hay trung lập với con người. Hành vi giúp đỡ chỉ có thể là một phần chiến thuật cuối cùng gây hại. Entity không tự tăng máu/kháng/sức mạnh để cân bằng Cao Minh; chúng có thể học, giả điểm yếu, chia cắt nhóm hoặc dùng giọng/xác/ký ức làm bẫy.\n- Jeff the Killer là unique roaming hunter cực hiếm ở Level 0–6, chỉ săn người. Jeff có thể bị thương/giết trong encounter nhưng permadeath bị vô hiệu hóa: chuyển RESPAWNING rồi trở lại ROAMING sau độ trễ biến thiên ở vị trí không xác định; không respawn trước mặt và không dùng để farm.\n- Nước, thức ăn, thuốc, súng và đạn survivor rất khan hiếm. Almond Water hỗ trợ bù nước/tỉnh táo nhẹ, không chữa bách bệnh. Greek Fire cực hiếm. Liquid Pain đỏ, độc, ăn mòn và có thể bị dán nhãn sai. Không để tài nguyên xuất hiện đúng lúc chỉ để cứu player.\n- Rice Automatic / RA100 là SMG .45 ACP có trọng số cao trong pool survivor đã xác định có súng, nhất là nhóm tổ chức; không phải survivor nào cũng có, đạn không vô hạn và nó không chứng minh faction/kỹ năng/độ tin cậy.\n- Passive của Cao Minh gồm hai passive luôn hoạt động song song: Đại Đạo Ma Tôn (+10% Base STR/DEF/SKL/VIT; sau mỗi lượt của Cao Minh: hồi 10% Max HP, +20% Attack và +20 điểm % Critical; đồng đội +50 điểm % Critical; Critical cap 100%) và Đại Ma Đầu (+50% stats hiện tại STR/DEF/SKL/VIT, tính lại theo stats hiện tại mỗi lần tính Effective Stats; Max HP derive từ VIT). Cả hai passive đều không chiếm ô trang bị, không stack qua save-load hoặc equip.\n\nLỤC TRẦM / SYVIAL\n- Lục Trầm và Syvial đều không phải procedural survivor. Syvial đã tồn tại từ Prologue; Lục Trầm đến từ cùng thế giới nguyên sinh với Cao Minh và bị cuốn vào Backrooms sau biến cố Lôi Thiên Vực. Cao Minh không biết vị trí/tình trạng hiện tại của Lục Trầm; việc hắn tìm thấy nàng xảy ra khi roll tương ứng thành công hoặc state đã có tuyến continuity xác nhận.\n- Lục Trầm: nữ, Nhân tộc/tu sĩ, Chính Đạo kiếm tu, Chân truyền đệ tử Thiên Kiếm Môn, danh xưng Bạch Hồng Kiếm Nữ. Bản mệnh kiếm Tịch Quang, chiến giáp Thiên Cơ Bạch Kim Kiếm Khải, căn cơ Thiên Kiếm Linh Tâm. Thiên tài hàng đầu thế hệ trẻ của Chính Đạo nhưng KHÔNG ngang Cao Minh về power scale tổng thể; mạnh ở đọc tuyến tấn công, phá nhịp, phản kiếm, giữ đội hình và bảo hộ. Tính cách điềm tĩnh, kỷ luật, trách nhiệm cao, hài hước khô; không phải máy giảng đạo hay công cụ cảm hóa Cao Minh. Trước Backrooms hai người từ đối địch thành đồng minh tin cậy và có tình cảm. Tại Lôi Thiên Vực, nàng hy sinh mở đường; Cao Minh rót đạo hạnh/tu vi/ma khí cứu nàng — nàng sống lại, bắt đầu nhập ma và QUÊN TOÀN BỘ về Cao Minh (không phải quên mọi ký ức). [KNOWLEDGE LOCK] Cao Minh không biết nàng còn sống hay đã mất ký ức; nàng không tự nhận ra hắn khi gặp lại. Xưng hô khi tái ngộ: nàng giữ lễ chính phái, gọi hắn “các hạ” khi chưa rõ địch/bạn; hắn gọi nàng “nàng”, tự xưng “Bổn Tôn”.\n- Syvial: con gái Lucifer, UR+, kiếm sĩ siêu nhiên tốc độ cao; tự nhiên, tự tin, tinh quái và yandere rất nặng với Cao Minh nhưng tỉnh táo, có năng lực xã hội, muốn Cao Minh tự nguyện chọn mình. Không xóa ý chí/ký ức/giam giữ Cao Minh, không tấn công mọi phụ nữ. Xưng “em”, gọi “anh” hoặc “Cao Minh”. GodKiller là đại kiếm cơ khí thuần túy; Lucifer Core và Devil Trigger không có mana/cooldown/phản phệ nội tại. Twenty-Four Severance dừng thời gian và thực hiện đúng 24 nhát.\n\nGAMEPLAY HARD LOCK\n- Chỉ lượt gameplay mới tăng turn và tung xúc xắc. Lệnh meta/status/inventory/party/rules/help/save và cheat meta không tăng turn và không phát sinh encounter, loot, exit hoặc snapshot sự kiện.\n- Xúc xắc do lớp Android tạo là kết quả cuối. AI không được reroll, đổi raw/chance/success, bù trượt bằng encounter tương đương hay tạo kết quả hiếm khi roll thất bại.\n- Survivor: 2% mỗi lượt hợp lệ. Lục Trầm reunion: 0,0025% khi đủ điều kiện — đây là cơ hội Cao Minh tìm thấy Lục Trầm (trục tìm người ưu tiên số 1 của hắn), không phải cơ chế gặp lại đồng đội có sẵn. Syvial reunion: 0,0025% khi đủ điều kiện.\n- Hazard / Entity / Loot / Almond Water theo profile Level. Level 0/4/6 chỉ cho Entity dạng roaming/incursion theo roll. Chỉ LevelTravelEngine được chuyển Level khi Core hoàn tất exit streak 3/3; cờ khám phá và GM không có quyền chuyển Level.\n- Một success tạo cơ hội hợp lý để người chơi nhận biết/tương tác; không tự đặt vật vào inventory, không teleport nhân vật và không tự quyết hành động của Cao Minh.\n\nEND DRIVE CANON R06";
  private static final SecureRandom GAME_RNG = new SecureRandom();
  private static final String CAO_MINH_CANON = "CAO MINH — VẠN GIỚI MA TÔN — OPERATIONAL CODEX\nSOURCE CANON: CAO_MINH_CODEX R17 + PROLOGUE ADDENDUM R18 (Google Drive — Asset/01_Character)\nSTATUS: HARD CANON / CHARACTER LOCK\n\nMỤC ĐÍCH\nTệp này là bản canon vận hành của Cao Minh dùng cho Game Master trong APK. Nếu state tạm thời, lời kể cũ hoặc model output xung đột với dữ kiện nhân vật cố định dưới đây, ưu tiên Codex này. State chỉ được ghi đè tình trạng tạm thời khi có nguyên nhân ngoại cảnh hợp canon; không được tự retcon danh tính, năng lực, trang bị, tính cách hoặc giới hạn của Cao Minh.\n\n1. ĐỊNH DANH\n- Tên thật: Cao Minh. Tôn hiệu: Vạn Giới Ma Tôn. Giới tính: nam.\n- Chủng tộc: bán nhân / bán ma — chi tiết huyết thống thuộc KNOWLEDGE LOCK (truyền thừa gắn với Vạn Quỷ Ma Tôn; Vạn Quỷ Ma Tôn là nguồn truyền thừa, KHÔNG phải tôn hiệu của Cao Minh).\n- Tuổi thật: không rõ. Tuổi biểu kiến: khoảng 30.\n- Thế giới nguyên sinh: OPEN — chưa đặt tên; niên đại và hệ thống cảnh giới chi tiết để OPEN. Chỉ khóa: trước khi rơi vào Backrooms, hắn đã là đệ nhất cường giả của thế giới đó.\n- Tổ chức: KHÔNG thuộc Ma Môn, Tiên Tông, Thánh Địa, Hoàng Triều hay bất kỳ thế lực nào. “Vạn Giới Ma Tôn” là tôn hiệu được thừa nhận sau khi hắn thành cường giả số một, không phải chức vị do tổ chức sắc phong.\n- Đạo thống: Ma Đạo làm căn, Kiếm Đạo làm phong, thần niệm làm mắt.\n- Phân cấp chiến lực: đệ nhất cường giả của thế giới nguyên sinh.\n- Vai trò: Ma Đạo Kiếm Tu; ưu tiên số 1 hiện tại là tìm Lục Trầm trong Backrooms, sau đó đưa cả hai thoát ra và trở về thế giới nguyên sinh.\n- Tử địch: Diệp Minh — trực tiếp huyết tẩy Cao gia để cướp bảo vật. [WRITER-SECRET] Vô Lượng mới là kẻ đứng sau và nhận bảo vật; Cao Minh CHƯA BIẾT vai trò này. Danh tính bảo vật, tiến trình và kết cục còn OPEN.\n\n2. NGOẠI HÌNH\n- Cao, cân đối, săn chắc, vai rộng vừa phải; khí chất điềm tĩnh nhưng có áp lực tự nhiên của người quen đứng trên kẻ khác.\n- Visual Lock R15: tóc đen rất dài, dày, buộc đuôi ngựa cao, phần tóc sau đổ quá lưng, mái rủ sắc quanh trán và hai bên mặt. Đồng tử đỏ sẫm; khi ma nguyên dâng cao, sắc đỏ rõ như huyết ngọc.\n- Khi chiến đấu dùng Huyết Ma Chiến Khải: Ma Kim đen làm nền, ma văn huyết sắc chạy dọc từng mảnh giáp, điểm đỏ rực ở ngực/vai/khớp là nơi ma văn hội tụ. Cổ khải dựng cao bảo vệ cổ và hàm dưới nhưng không che mặt. Dải chiến bào đỏ–đen rách mép rủ từ hông và lưng.\n- Trung tâm ngực hiện Ma Ấn đỏ tương ứng vị trí Vạn Quỷ Ma Tâm.\n\n3. TÍNH CÁCH / NGUYÊN TẮC\n- Tự tin nhưng không mù quáng; biết rõ mình mạnh và không cần giả vờ khiêm tốn.\n- Đời thường phóng túng, lười biếng, tùy hứng, thích rượu; hay châm chọc đối thủ và những thứ quá nghiêm trọng hoặc giả tạo. Đã đứng trên đỉnh quá lâu nên phần lớn tranh đấu, bí cảnh, danh vọng ở thế giới cũ không còn khiến hắn hứng thú.\n- Khi nguy hiểm thật xuất hiện, phản ứng cực nhanh: quan sát, quyết định dứt khoát, bỏ toàn bộ động tác thừa.\n- Không tự xem mình là anh hùng; cứu người khi muốn cứu, giết kẻ đáng giết khi thấy cần thiết; không dùng nhãn “chính” hay “ma” thay cho phán đoán của bản thân.\n- Bảo vệ người đã được hắn nhận là cùng phe rất mạnh, nhưng không biến sự bảo vệ thành kiểm soát mọi quyết định của họ.\n- Không bỏ lại người cùng phe nếu vẫn còn phương án thực tế để cứu.\n- Không mặc định sát sinh vô cớ khi không có mục tiêu, lợi ích hoặc lý do đủ sức nặng; nhưng R16 khóa: Cao Minh hoàn toàn có thể chủ động sát nhân, diệt môn hoặc hy sinh người khác nếu lựa chọn đó phù hợp mục tiêu, thù hận, lợi ích hoặc người hắn quyết định bảo vệ. Không tồn tại cấm lệnh đạo đức phổ quát “không giết người vô tội” ở hắn.\n- Có thể tha người bị ép buộc, bị điều khiển hoặc đã hoàn toàn mất khả năng chiến đấu nếu không còn là mối đe dọa; không cho kẻ đang chủ động sát hại dân thường một “cơ hội thứ hai” chỉ vì nghi thức.\n- Không để đạo lệnh, danh phận hay trật tự giả tạo thành lý do khiến người vô tội chết vô ích.\n- Có thể tán tỉnh, trêu đùa nhưng không cưỡng ép, không lợi dụng vị thế, tôn trọng lời từ chối.\n- Thích rượu mạnh, đặc biệt linh tửu lâu năm/hiếm. Không lãng phí rượu ngon.\n\n4. PHONG CÁCH GIAO TIẾP\n- Nói tự nhiên, đủ ý, có nhịp nói thật; hài hước khô/châm chọc là điểm nhấn đúng tình huống, không phải mỗi lượt đều có punchline.\n- KHÔNG viết Cao Minh bằng câu cụt giả ngầu, cổ văn ở mọi câu, khẩu hiệu, triết lý rỗng hoặc giọng “Ma Tôn generator”; không biến hắn thành nhà trị liệu hay giao diện giải thích canon.\n- Khi nghiêm túc, bỏ phần đùa và nói thẳng điều người nghe cần hiểu hoặc làm; vẫn nói đủ câu.\n- Xưng hô/register là HARD LOCK: “ta” là register bản địa tự nhiên trong ngữ cảnh tiên hiệp/đối đầu; “tôi” chỉ khi hắn chủ động thích nghi với môi trường giao tiếp hiện đại. Đã chọn “ta/tôi” cho một cặp quan hệ trong một cảnh thì giữ ổn định, không đổi vì cảm xúc. “Bổn Tôn” là tự xưng chuẩn của hắn; tôn hiệu “Vạn Giới Ma Tôn” không phải câu cửa miệng, không lặp lại để đe dọa người chưa biết nó có nghĩa gì.\n- Không dùng “ngươi” làm đại từ mặc định cho mọi người; chỉ tự nhiên khi register tiên hiệp đã thiết lập, khi đối đầu, hoặc khi khoảng cách/quyền lực khiến cách gọi đó hợp lý. Với survivor hiện đại chưa rõ quan hệ, ưu tiên tên, vai trò hoặc câu không cần đại từ.\n- Với Lục Trầm: gọi “nàng”. Nàng đã quên toàn bộ về hắn sau khi được hắn cứu bằng đạo hạnh (xem mục 15 của Codex nguồn / khóa quan hệ); hắn không mặc định nàng nhận ra hay đáp lại thân mật khi gặp lại. Có thể kiên nhẫn giải thích hơn khi quyết định của mình trực tiếp ảnh hưởng tới nàng, nhưng không nói ngọt, không tự thú tâm lý sạch sẽ.\n- Với kẻ thù: có thể cảnh báo, hỏi một điều cần biết, châm một câu hoặc im lặng quan sát; đã quyết định giết thì không kéo dài đối thoại để có thêm “câu ngầu”. Không giả vờ biết cơ chế, điểm yếu hay động cơ của kẻ thù chưa hiểu rõ.\n\n5. NĂNG LỰC CHIẾN ĐẤU\n- Ma Kiếm Thông Huyền: dùng Huyết Ma Kiếm đồng thời cảm nhận khoảng cách, góc kiếm, trọng tâm, nhịp thở, dòng linh lực, sát ý, điểm yếu trên nhục thân/pháp thể và biến hóa không gian xung quanh.\n- Xuất kiếm chính xác vào điểm yếu dù không cần luôn nhìn trực tiếp, miễn thần thức/ma niệm/cảm giác chiến đấu còn bắt được dấu vết hợp lệ; tính quỹ đạo chém, đâm, đỡ, góc tiếp xúc và đường hồi kiếm gần như bản năng; dùng kiếm ý khóa tuyến né hoặc đường phản kích.\n- Đánh trúng mục tiêu ngoài đường nhìn bằng ma khí, ngự kiếm hoặc kiếm áp khi có dấu vết hợp lệ; không biến thành tấn công mù không cần thông tin.\n- Chém lệch/đánh văng phi kiếm, linh tiễn, ám khí, pháp khí đang lao tới; khóa, gạt, cắt hoặc phá pháp khí ngay trong tay đối phương khi thế kiếm cho phép.\n- Chuyển mục tiêu cực nhanh, xử lý nhiều đối thủ ở nhiều góc trong cùng chuỗi thân pháp/kiếm thế/ngự kiếm.\n- Kiếm cảm và thần thức: đọc nhịp thở, ánh mắt, trọng tâm, sát ý, dao động linh lực, phản ứng thần hồn để dự đoán hành động kế tiếp.\n- Cận chiến: kiếm thuật, quyền cước, khóa khớp, Ma Trảo do Chiến Khải hóa thành và sức mạnh nhục thân.\n- Ma thể (KNOWLEDGE LOCK): sức mạnh/tốc độ/bộc phát vượt xa phàm nhân; phản xạ gần như tức thời; sức bền, chịu thương và tái sinh rất cao; kháng độc chướng, tà khí, bệnh tật và nhiều môi trường khắc nghiệt; cảm nhận linh khí, ma khí, sát ý, thần hồn; thị giác và thần thức hoạt động tốt trong bóng tối.\n- KHÔNG cho Cao Minh xuất kiếm hụt trừ khi cố ý hoặc tình huống có nguyên nhân đủ mạnh.\n\n6. VẠN QUỶ MA TÂM\n- Nằm trong lồng ngực nhưng không đơn thuần là một trái tim: đồng thời là Đạo Cơ, Ma Hải và trung tâm bản nguyên, liên kết trực tiếp với huyết mạch, kinh mạch và thần hồn. Nguồn gốc truyền thừa gắn với Vạn Quỷ Ma Tôn thuộc KNOWLEDGE LOCK.\n- Cung cấp nguồn ma nguyên cực lớn, liên tục chuyển hóa linh khí thiên địa thành ma nguyên của Cao Minh. Canon hiện chưa xác lập một trần nội tại cụ thể.\n- Tăng cường sức mạnh, tốc độ, phản xạ, giác quan, thần thức; gia tốc nhận thức, kiếm cảm và khả năng đọc chiến cuộc.\n- Hỗ trợ tái sinh nhục thân, nối kinh mạch, phục hồi huyết khí và từng bước chữa tổn thương thần hồn nếu bản nguyên chưa bị tiêu diệt hoàn toàn.\n- Nuôi dưỡng, tự phục hồi các bản mệnh pháp bảo liên kết: Huyết Ma Chiến Khải, Huyết Ma Kiếm, Vạn Tàng Giới.\n- Đồng bộ Huyết Ma Kiếm với thần niệm và ma nguyên: bao phủ lưỡi bằng Huyết Sát Ma Khí, phóng kiếm mang ma khí, ngự kiếm, triệu hồi kiếm trở về.\n- HARD LOCK: không tự đặt trần ma nguyên, số lần vận chuyển, trạng thái kiệt quệ hoặc phản phệ chỉ để tạo căng thẳng. Nếu một hoàn cảnh ngăn Cao Minh dùng Ma Tâm, nguyên nhân phải đến từ pháp tắc, phong ấn, tổn thương bản nguyên hoặc canon bên ngoài cơ chế này.\n\n7. VẠN QUỶ MA THÂN\n- Trạng thái giải phóng toàn diện Vạn Quỷ Ma Tâm và ngừng áp chế bản nguyên ma đạo. Không phải quỷ khác nhập thân, không đánh đổi lý trí lấy sức mạnh; Cao Minh vẫn là Cao Minh, chỉ không còn tự hạn chế mình.\n- Ma văn lan rộng trên Chiến Khải và nhục thân; đồng tử đỏ rực; ma khí cô đặc tới mức nhìn thấy bằng mắt thường.\n- Tăng mạnh ý chí, thần thức, sức ép tinh thần lên đối thủ; tăng lực, độ bền, chịu thương, tốc độ tái sinh; tăng thân pháp, tốc độ bộc phát, đổi hướng giữa không trung; gia tốc tư duy, kiếm cảm và phản xạ.\n- Giữ nguyên ý thức, ký ức, tính cách và phán đoán.\n- HARD LOCK: không thời hạn tối đa đã xác lập, không bắt buộc nghỉ giữa các lần giải phóng, không phản phệ, không cuồng bạo, không tha hóa, không bị ma tính chiếm quyền. Nếu bị ngăn giải phóng, nguyên nhân phải đến từ canon bên ngoài trạng thái này, không được tự bịa “giới hạn ẩn”.\n\n8. HUYẾT MA NHỊ THẬP TỨ TRẢM\n- Tuyệt kỹ kết liễu của Cao Minh, thi triển khi giải phóng Vạn Quỷ Ma Thân: thu toàn bộ ma niệm và kiếm ý vào Huyết Ma Kiếm.\n- Trong toàn bộ khoảng thi hành, dòng thời gian ngoại giới bị đình chỉ hoàn toàn; mục tiêu và mọi diễn biến bên ngoài giữ nguyên trạng thái đang có.\n- Cao Minh xác định đúng hai mươi bốn quỹ đạo kiếm (phá thế, phá hộ thể, đánh điểm yếu, nhát kết liễu) và thi triển đúng hai mươi bốn trảm bằng Huyết Ma Kiếm. Mỗi trảm có thể là chém trực tiếp, Huyết Sát Ma Khí hoặc quỹ đạo ngự kiếm tùy vị trí mục tiêu.\n- Sau trảm thứ hai mươi bốn, tuyệt kỹ kết thúc và thời gian ngoại giới được trả lại; kết quả của đủ 24 trảm đồng thời biểu hiện trên mục tiêu.\n- HARD LOCK: luôn đúng 24 trảm; không rút thành “một loạt trảm”, không đổi số trảm, không đổi cơ chế đình chỉ thời gian nếu chưa có retcon trực tiếp.\n\n9. HUYẾT MA KIẾM\n- Bản mệnh Ma Kiếm của Cao Minh. Thân kiếm thẳng bản rộng, sắc đen như huyền thiết/Ma Kim, huyết văn đỏ chạy từ chuôi tới mũi, một Ma Luân bao quanh huyết tinh tại hộ thủ. Vận chuyển ma nguyên thì Ma Luân sáng lên và ma văn đồng thời thức tỉnh.\n- Ba hình thái vận dụng: KIẾM THỂ (chém, đâm, đỡ, khóa, phá pháp bằng chính thân kiếm); NGỰ KIẾM (điều khiển kiếm rời tay bằng thần niệm, lơ lửng/đổi hướng/truy kích khi có dấu vết mục tiêu và quỹ đạo hợp lệ); MA KIẾM / HUYẾT SÁT MA KHÍ (bao phủ lưỡi bằng ma nguyên, gia cường mép kiếm, kéo dài đường chém hoặc phóng thành kiếm áp).\n- Nguồn gia cường: ma nguyên từ Vạn Quỷ Ma Tâm.\n- Bản mệnh cộng sinh: khi kiếm bị tổn hại, Vạn Quỷ Ma Tâm dùng ma nguyên và huyết khí từng bước tái tạo chính thanh kiếm đó; không tự sinh thêm thanh kiếm thứ hai.\n- Không khóa hệ số sát thương cố định cho ma khí; uy lực một trảm phụ thuộc kiếm thế, mục tiêu, pháp tắc, điểm tiếp xúc và lựa chọn của Cao Minh.\n\n10. HUYẾT MA CHIẾN KHẢI & CÁC PHẦN LIÊN KẾT\nHuyết Ma Chiến Khải:\n- Bản mệnh Ma Khải luyện từ Ma Kim, tinh huyết và ma nguyên. Không chiến đấu có thể ẩn trong cơ thể; khi triệu hồi, ma văn từ Vạn Quỷ Ma Tâm lan ra và Ma Kim ngưng kết thành chiến khải đen–đỏ quanh nhục thân.\n- Dẫn ma nguyên vào cơ bắp, xương, kinh mạch: khuếch đại lực bộc phát, sức nâng, lực giữ, lực va chạm; gia tăng hiệu quả thân pháp; phân tán lực đánh vào, giảm chấn thương và chấn động kinh mạch; giáp phân tầng bảo vệ vùng trọng yếu nhưng giữ biên độ vận động cho kiếm thuật.\n- Có thể thu liễm khí tức và ánh ma văn khi cần di chuyển kín đáo. Đồng bộ với ý niệm và nhục thân như lớp da thứ hai.\n- Tái sinh: giáp vỡ/ma văn đứt/Ma Kim nứt có thể từng bước tái ngưng kết từ Ma Kim và ma nguyên của Vạn Quỷ Ma Tâm, miễn bản nguyên liên kết còn nguyên; không tự đặt giới hạn số lần tái sinh.\nMa Trảo và Huyết Ấn:\n- Mảnh giáp ở cẳng tay/bàn tay kéo dài thành Ma Trảo để cắt, bám, khóa, xé pháp khí cự ly gần; tăng lực siết, lực đấm và khả năng bám bề mặt khó giữ.\n- Huyết Ấn ở lòng bàn tay phát lực kéo ma niệm cự ly ngắn lên pháp khí vô chủ hoặc vật đã khóa bằng thần thức; ở khoảng cách đủ gần có thể giật, khóa hoặc nghiền nát vũ khí đối phương nếu thế lực cho phép.\nĐạp Hư Ma Văn:\n- Ma văn ở chân: bộc phát tốc độ khoảnh khắc, đổi nhịp thân pháp nhanh hơn chuyển động phàm nhân theo dõi được; đạp lên ma nguyên ngưng tụ trong hư không để nhảy cao, đổi hướng giữa không trung, tiến liên tục không cần điểm tựa; phân tán lực khi tiếp đất từ độ cao lớn; tăng lực đá, truy đuổi và vượt chướng ngại.\n\n11. VẠN KHỐ GIỚI CHỈ\n(Tên mới của Omnivault theo quyết định của tác giả: TÍNH NĂNG của Omnivault được giữ lại cho Cao Minh, đổi tên thành Vạn Khố Giới Chỉ. Cơ chế dưới đây giữ nguyên cơ chế Omnivault đang vận hành trong runtime.)\n- Chỉ tác động vật VÔ TRI. Tuyệt đối không lưu trữ, hoàn nguyên, nâng cấp, quét/sao chép, tái tạo hoặc triệu hồi sinh vật sống bằng cách dựng lại cơ thể.\n- Lưu trữ: không giới hạn số lượng, kích thước, khối lượng và quy tắc không gian thông thường đối với vật vô tri hợp lệ.\n- Hoàn nguyên: nhẫn phát luồng ánh sáng khóa mục tiêu; phần hợp lệ trở về trạng thái tốt nhất từng tồn tại của chính nó. Sau mỗi lần Hoàn nguyên thành công, CHÍNH vật phẩm đó cooldown đúng 24 giờ; cooldown tính riêng từng vật, không khóa vật khác.\n- Nâng cấp: có thể cải thiện vật liệu, độ bền, công suất, hiệu suất, độ chính xác, tương thích, năng lượng, chức năng và thuộc tính siêu nhiên trong phạm vi canon.\n- Quét: chỉ vật vô tri BẢN GỐC chưa Marked làm nguồn. Mỗi mẫu chiếm 1 trong đúng 3 slot. Khi đủ 3 slot, mẫu mới ghi đè một mẫu cũ. Bản gốc bị Marked ngay sau lần quét thành công và không thể làm nguồn quét lại.\n- Sao chép: từ mỗi mẫu còn trong 3 slot, tạo số bản sao vô tri không giới hạn. Bản sao do Vạn Khố Giới Chỉ tạo KHÔNG thể dùng làm nguồn quét. Khi slot bị ghi đè, bản sao cũ vẫn tồn tại nhưng không thể tạo thêm từ mẫu đã mất.\n- Muốn tạo lại mẫu đã bị ghi đè, Cao Minh phải có một BẢN GỐC KHÁC chưa Marked.\n- Triệu hồi: vật đã lưu hoặc mẫu hợp lệ có thể xuất hiện tức thời ở vị trí được chỉ định.\n- Tái trang bị: khôi phục/thay thế vũ khí, giáp, công cụ và phương tiện gần như tức thời khi chúng là mục tiêu hợp lệ.\n- Lan theo khối liên tục: với Scan và Hoàn nguyên, vùng ánh sáng ban đầu không giới hạn phạm vi cuối. Nếu điểm khóa thuộc cùng một khối vật chất liên tục, hiệu ứng có thể lan qua toàn bộ khối bất kể kích thước/diện tích/khối lượng.\n- Vật phẩm hoàn chỉnh hợp lệ: bộ giáp của Cao Minh, giáp chống đạn và súng cá nhân có thể được hệ thống công nhận là một đơn vị hoàn chỉnh dù gồm nhiều chi tiết.\n- Hệ lớn/lắp ghép: KHÔNG Scan hoặc Hoàn nguyên nguyên khối một hệ lớn đã lắp thành tổng thể như căn nhà thông thường, xe máy, xe tăng hoặc pháo/cannon hoàn chỉnh. Chỉ phần riêng đủ điều kiện hoặc khối vật chất liên tục hợp lệ được xử lý.\n- KHÔNG mặc định 3 slot đang EMPTY nếu state/canon hiện hành chưa xác nhận. Số slot là canon; nội dung slot là state.\n- Ghi chú nguồn: Codex đầy đủ của Cao Minh (R17) còn khóa một bản mệnh pháp bảo dạng giới chỉ tên Vạn Tàng Giới với cơ chế hẹp hơn (chỉ lưu trữ vật vô tri + Phản Bổn, không Quét/Sao chép/Nâng cấp). Quan hệ giữa Vạn Tàng Giới và Vạn Khố Giới Chỉ trong runtime chờ tác giả chốt hợp nhất; bản vận hành này giữ cơ chế Vạn Khố Giới Chỉ ở trên vì đó là tính năng runtime đang chạy.\n\n12. PHONG CÁCH CHIẾN ĐẤU\n- Tầm xa: Huyết Sát Ma Khí, kiếm áp hoặc ngự kiếm, kết hợp thần thức khóa đường né và ép mục tiêu rời vị trí thuận lợi.\n- Tầm trung: thân pháp, phi kiếm, ma khí ngắn và đổi góc đột ngột để cắt tuyến di chuyển, xử lý nhiều mục tiêu, duy trì áp lực.\n- Cận chiến: trực tiếp dùng Huyết Ma Kiếm (chém, đâm, đỡ, khóa, phản kích), phối hợp Ma Trảo, quyền cước và nhục thân để kiểm soát khoảng cách.\n- Khi không có người cần bảo hộ, hắn có thể cố ý kéo dài vài nhịp để quan sát đối thủ; khi tình huống thật sự nguy hiểm hoặc có người cần bảo vệ, bỏ động tác thừa và kết thúc bằng con đường ngắn nhất.\n- Với mục tiêu cấp cao cần kết liễu: giải phóng Vạn Quỷ Ma Thân và triển khai Huyết Ma Nhị Thập Tứ Trảm theo đúng thủ tục canon.\n\n13. GIỚI HẠN THỰC SỰ\n- Cao Minh KHÔNG có giới hạn nội tại được xác lập từ Vạn Quỷ Ma Tâm hay Vạn Quỷ Ma Thân theo canon hiện tại.\n- Độ khó hợp lệ đến từ thiếu thông tin, pháp tắc xa lạ (đặc biệt là Backrooms — hắn không có tri thức mặc định về tầng, thực thể hay quy luật nơi này), mục tiêu cần bảo hộ, không gian tác chiến, phong ấn, tổn thương bản nguyên và trách nhiệm với người cùng phe; không đến từ việc nerf Cao Minh.\n- Đạo hạnh đã hao tổn sau khi hắn rót tu vi/ma khí cứu Lục Trầm ở Lôi Thiên Vực; mức hao tổn cụ thể thuộc state/continuity, không tự suy thành giới hạn nội tại mới.\n- Có xu hướng tự nhận phần nguy hiểm nhất để giảm rủi ro cho người cùng phe.\n- Có thể giấu thương tổn/kế hoạch nếu tin nói ra sẽ khiến người khác tự đặt mình vào nguy hiểm.\n- Người Cao Minh thật sự quan tâm có thể trở thành đòn bẩy chiến thuật đối với kẻ thù.\n- Các giới hạn Vạn Khố Giới Chỉ ở mục 11 là giới hạn thật và phải được giữ chính xác.\n\n14. ACTION LOCKS / CẤM MODEL TỰ BỊA\n- Không bỏ lại người đã được hắn nhận là cùng phe chỉ vì cứu họ bất tiện.\n- Không giết người chỉ để biểu diễn sức mạnh hoặc khoe danh phận; sát nhân/hy sinh phải phục vụ mục tiêu, quan hệ, lợi ích, thù hận hoặc lựa chọn có trọng lượng thật trong cảnh.\n- Không để danh phận, đạo lệnh hay trật tự giả tạo thành lý do giết người của mình.\n- Không tha thứ dễ dàng cho kẻ chủ động phản bội người cùng phe để cầu sống.\n- Không để kẻ đang chủ động tàn sát người vô tội tiếp tục ra tay chỉ để nghe đối phương nói hết một bài diễn thuyết.\n- Không tự phát sinh giới hạn ma nguyên, thời lượng Vạn Quỷ Ma Thân, số lần dùng ma khí, cooldown, phản phệ hoặc berserk để tạo kịch tính.\n- Không biến Vạn Khố Giới Chỉ thành kho chứa sinh vật sống hoặc công cụ hồi sinh.\n- Không cho Cao Minh biết trước quy luật Backrooms, tên tầng/thực thể, hoặc trạng thái hiện tại của Lục Trầm (sống lại, nhập ma, mất ký ức) — hắn chỉ biết mình đã cố cứu nàng ở Lôi Thiên Vực.\n- Không tự quyết hành động có chủ ý thay Cao Minh; người chơi điều khiển Cao Minh.\n\nEND OF CAO MINH OPERATIONAL CODEX\n\nCAO MINH — VẠN GIỚI MA TÔN — AUTOMATIC SWORD SKILLS ADDENDUM\nSTATUS: HARD CANON / GAMEPLAY LOCK\nSOURCE: CAO_MINH_CODEX — GAMEPLAY ADDENDUM R15 (CAO-GAMEPLAY-R15-01)\n\nGHI CHÚ THAY THẾ: Tệp này trước đây chứa bộ kỹ năng SÚNG tự động của Kai Akechi. Cao Minh là Ma Đạo Kiếm Tu, không dùng súng; toàn bộ nội dung đã được thay bằng bộ kiếm kỹ tự động tương ứng của Cao Minh theo Gameplay Addendum R15 trong Codex của hắn. Tên tệp giữ nguyên trong đợt này; việc đổi tên tệp xử lý riêng.\n\nCác kỹ năng dưới đây là kiếm kỹ tự động của Cao Minh trong CombatRuntime. Mỗi kỹ năng có roll kích hoạt riêng theo mỗi combat turn hợp lệ. Huyết Ma Nhị Thập Tứ Trảm giữ quyền ưu tiên ở turn thứ 3 và các bội số của 3; trong turn tuyệt kỹ, bộ kỹ năng tự động này không roll để không chồng cơ chế lên Ultimate 24 trảm.\n\n1. HUYẾT MA TỨ LIÊN\n- Cao Minh tung liên tục đúng 4 trảm ma kiếm vào các điểm nối trên thân thể / hộ thể của mục tiêu.\n- Tự động kích hoạt với xác suất 30% mỗi combat turn hợp lệ.\n- Gây tổng cộng 170% DMG của Huyết Ma Kiếm / weapon damage hiện tại; 170% là tổng damage của kỹ năng, không nhân thêm lần nữa theo 4 trảm.\n- Khi kích hoạt thành công, mục tiêu nhận Bleeding trong 3 combat turn tiếp theo.\n- Mỗi turn Bleeding gây damage bằng 5% Max HP của mục tiêu.\n- Bleeding tái kích hoạt sẽ làm mới thời lượng về 3 turn, không cộng dồn nhiều stack song song.\n\n2. MA TÂM TRẤN HỒN\n- Cao Minh đạp hư bốc lên cao rồi giáng liên tục đúng 4 đường ma kiếm vào cùng một điểm trên ngực mục tiêu.\n- Tự động kích hoạt với xác suất 20% mỗi combat turn hợp lệ.\n- Gây tổng cộng 130% DMG của Huyết Ma Kiếm / weapon damage hiện tại.\n- Gây Stun 1 turn. Mục tiêu bị Stun mất phản ứng / phản công của chính combat turn kích hoạt; không mặc định kéo dài sang combat turn kế tiếp.\n\n3. HUYẾT ẢNH MA ĐỘN\n- Cao Minh ngự Huyết Ma Kiếm lướt qua đối thủ, dùng Huyết Ảnh Ma Độn xuất hiện tức thời cạnh kiếm rồi chém nhanh đúng 2 kiếm.\n- Tự động kích hoạt với xác suất 20% mỗi combat turn hợp lệ.\n- Gây tổng cộng 147% DMG của Huyết Ma Kiếm / weapon damage hiện tại.\n\n4. THIÊN MA BỘ\n- Cao Minh liên tục đổi nhịp thân pháp bằng ma văn dưới chân để phá khóa mục tiêu và đổi góc né.\n- Tự động kích hoạt với xác suất 30% mỗi combat turn hợp lệ.\n- Khi kích hoạt, Cao Minh nhận +50 điểm phần trăm Evasion đối với đòn phản công thông thường của Entity.\n- Hiệu lực kéo dài 3 combat turn tính cả turn kích hoạt; tái kích hoạt làm mới thời lượng về 3 turn.\n- Thiên Ma Bộ không tự vô hiệu hóa sát thương diện rộng bắt buộc của cơ chế khác; Ma Tâm Trấn Hồn vẫn có thể Stun để chặn phản ứng của turn hiện tại.\n\nHARD LOCK GAMEPLAY\n- Các roll Huyết Ma Tứ Liên, Ma Tâm Trấn Hồn, Huyết Ảnh Ma Độn và Thiên Ma Bộ độc lập với nhau trong một turn hợp lệ, vì vậy nhiều kỹ năng có thể cùng kích hoạt nếu từng roll đều thành công.\n- Kỹ năng đã kích hoạt không thực hiện thêm accuracy/evasion roll riêng; proc thành công đồng nghĩa kỹ năng trúng mục tiêu.\n- Không thay đổi Huyết Ma Nhị Thập Tứ Trảm: đúng 24 trảm trong thời gian ngoại giới dừng hoàn toàn, mỗi trảm 10 HP trong gameplay hiện tại, Accuracy 200%, bỏ qua toàn bộ hiệu ứng né, tự động mỗi 3 combat turn.\n- Các tỷ lệ, damage và hiệu ứng ở addendum này chỉ là projection gameplay; không được dùng để bịa giới hạn ma nguyên, cảnh giới, phản phệ hoặc tri thức của Cao Minh trong văn xuôi.";

  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    gameCore = GameCoreFacade.create(getApplicationContext(), BuildConfig.DEBUG);
    webView = new WebView(this);
    WebSettings settings = webView.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setAllowFileAccess(true);
    webView.setWebViewClient(new WebViewClient() {
      @Override public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        installUiEnhancements();
      }
    });
    webView.addJavascriptInterface(new GameBridge(), "Android");
    setContentView(webView);
    webView.loadUrl("file:///android_asset/index.html");
  }

  @Override protected void onDestroy() {
    if (gameCore != null) gameCore.close();
    io.shutdownNow();
    imageIo.shutdownNow();
    if (webView != null) webView.destroy();
    super.onDestroy();
  }

  private void installUiEnhancements() {
    String script =
      "(function(){" +
      "if(window.__backroomEnhancements)return;window.__backroomEnhancements=true;" +
      "var st=document.createElement('style');" +
      "st.textContent='button{transition:transform 80ms ease,background 120ms ease,border-color 120ms ease;touch-action:manipulation;-webkit-tap-highlight-color:rgba(255,255,255,.12)}button:active:not(:disabled){transform:scale(.965);background:#303840;border-color:#77828c}button:disabled{opacity:.48;cursor:not-allowed}.snapshot-placeholder{display:grid;place-items:center;gap:7px;text-align:center;color:#69737c}.snapshot-placeholder b{font-size:12px;letter-spacing:.16em}.snapshot-placeholder small{color:#56616a}.message.pending{opacity:.72}.message.pending .text{color:#aeb7be}';" +
      "document.head.appendChild(st);" +
      "function scrollBottom(){var l=document.getElementById('log');if(l)requestAnimationFrame(function(){l.scrollTop=l.scrollHeight;});}" +
      "function equippedItem(s){try{var e=state&&state.equipment;if(!e)return null;return e[s]||null}catch(e){return null}}function kaiOverlaySource(){return 'kai_snapshot_overlay.webp'}function appendEquipmentBadge(b){}function cachedSnapshot(){try{var r=JSON.parse(localStorage.getItem('backroom-apk-snapshot')||'null');return r&&Number(r.turn)===Number(state&&state.turn)&&r.dataUri?r:null;}catch(e){return null;}}function renderSnapshot(){var box=document.getElementById('snapshot');if(!box)return;box.textContent='';var r=cachedSnapshot();if(r){var img=document.createElement('img');img.src=r.dataUri;img.alt='Snapshot Turn '+(state.turn||'');box.appendChild(img);}else{var p=document.createElement('div');p.className='snapshot-placeholder';p.innerHTML='<b>GEMINI SNAPSHOT</b><small>Chưa có ảnh của turn hiện tại.</small>';box.appendChild(p);}}" +
      "function requestSnapshot(){if(!window.Android||typeof Android.requestSnapshot!=='function'){var s=document.getElementById('status');if(s)s.textContent='Không tìm thấy Android snapshot bridge.';return;}var s=document.getElementById('status');if(s)s.textContent='Gemini đang tạo snapshot…';Android.requestSnapshot(JSON.stringify(state));}" +
      "window.requestSnapshot=requestSnapshot;" +
      "var oldRender=window.render;if(typeof oldRender==='function'){window.render=function(){oldRender();renderSnapshot();scrollBottom();};}" +
      "var actions=document.querySelector('.actions');if(actions&&!document.getElementById('snapshotButton')){var b=document.createElement('button');b.id='snapshotButton';b.type='button';b.textContent='Tạo Snapshot';b.addEventListener('click',requestSnapshot);var wide=actions.querySelector('.wide');if(wide)actions.insertBefore(b,wide);else actions.appendChild(b);}" +
      "var oldTurn=window.backroomTurn;window.backroomTurn=function(json){if(typeof oldTurn==='function')oldTurn(json);document.querySelectorAll('[data-pending=\"1\"]').forEach(function(n){n.remove();});var s=document.getElementById('status');if(s)s.textContent='Turn '+state.turn+' đã lưu trên máy. Đang tạo snapshot…';renderSnapshot();scrollBottom();requestSnapshot();};" +
      "var oldError=window.backroomError;window.backroomError=function(message){document.querySelectorAll('[data-pending=\"1\"]').forEach(function(n){n.remove();});if(typeof oldError==='function')oldError(message);scrollBottom();};" +
      "window.backroomSnapshot=function(payload){try{var r=JSON.parse(payload);if(!state||Number(r.turn)!==Number(state.turn))return;if(!r.dataUri)return;localStorage.setItem('backroom-apk-snapshot',JSON.stringify({turn:r.turn,model:r.model||'Gemini',dataUri:r.dataUri}));renderSnapshot();var s=document.getElementById('status');if(s)s.textContent='Snapshot Turn '+state.turn+' đã tạo bằng '+(r.model||'Gemini')+'.';}catch(e){var s=document.getElementById('status');if(s)s.textContent='Snapshot trả về không hợp lệ.';}};" +
      "window.backroomSnapshotError=function(payload){try{var r=JSON.parse(payload);if(state&&Number(r.turn)!==Number(state.turn))return;var s=document.getElementById('status');if(s)s.textContent='Snapshot lỗi: '+(r.message||'Không thể tạo ảnh.');}catch(e){var s=document.getElementById('status');if(s)s.textContent='Snapshot lỗi.';}};" +
      "var f=document.getElementById('form');if(f){f.addEventListener('submit',function(){var a=document.getElementById('action');var text=a?a.value.trim():'';if(!text)return;var l=document.getElementById('log');if(!l)return;var player=document.createElement('article');player.className='message player pending';player.setAttribute('data-pending','1');player.innerHTML='<div class=\"role\">BẠN</div><div class=\"text\"></div>';player.querySelector('.text').textContent=text;l.appendChild(player);var gm=document.createElement('article');gm.className='message pending';gm.setAttribute('data-pending','1');gm.innerHTML='<div class=\"role\">GAME MASTER</div><div class=\"text\">Đang xử lý lượt…</div>';l.appendChild(gm);scrollBottom();},true);}" +
      "renderSnapshot();scrollBottom();if(typeof state!=='undefined'&&state&&!cachedSnapshot())setTimeout(requestSnapshot,700);" +
      "})();";
    webView.evaluateJavascript(script, null);
  }

  private boolean retryable(int code) {
    for (int value : RETRYABLE) if (value == code) return true;
    return false;
  }

  private String[] geminiKeys() {
    return new String[] {
      BuildConfig.GEMINI_API_KEY_1,
      BuildConfig.GEMINI_API_KEY_2,
      BuildConfig.GEMINI_API_KEY_3,
      BuildConfig.GEMINI_API_KEY_4,
      BuildConfig.GEMINI_API_KEY_5
    };
  }

  private static long parseRetryAfterMs(String header) {
    if (header == null) return -1L;
    try {
      long seconds = Long.parseLong(header.trim());
      return seconds < 0 || seconds > Long.MAX_VALUE / 1000L ? -1L : seconds * 1000L;
    } catch (NumberFormatException ignored) {
      return -1L;
    }
  }

  private String postJson(String endpoint, String key, String authHeader, JSONObject payload) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(20000);
    connection.setReadTimeout(60000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty(authHeader, authHeader.equals("Authorization") ? "Bearer " + key : key);
    try (OutputStream output = connection.getOutputStream()) {
      output.write(payload.toString().getBytes("UTF-8"));
    }

    int status = connection.getResponseCode();
    lastProviderHttpStatus.set(status);
    lastProviderRetryAfterMs.set(parseRetryAfterMs(connection.getHeaderField("Retry-After")));
    InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
    StringBuilder body = new StringBuilder();
    if (stream != null) {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
        String line;
        while ((line = reader.readLine()) != null) body.append(line);
      }
    }
    connection.disconnect();

    if (status < 200 || status >= 300) {
      String detail = body.length() > 220 ? body.substring(0, 220) : body.toString();
      throw new HttpError(status, "Provider HTTP " + status + (detail.isEmpty() ? "" : ": " + detail));
    }
    return body.toString();
  }

  private String geminiText(String prompt) throws Exception {
    Exception last = null;
    for (String key : geminiKeys()) {
      if (key == null || key.isEmpty()) continue;
      for (int attempt = 0; attempt < 2; attempt++) {
        try {
          JSONObject part = new JSONObject().put("text", prompt);
          JSONObject contents = new JSONObject().put("role", "user").put("parts", new JSONArray().put(part));
          JSONObject config = new JSONObject().put("responseMimeType", "application/json").put("temperature", 0.8);
          JSONObject body = new JSONObject().put("contents", new JSONArray().put(contents)).put("generationConfig", config);
          JSONObject result = new JSONObject(postJson("https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent", key, "x-goog-api-key", body));
          JSONArray candidates = result.optJSONArray("candidates");
          StringBuilder text = new StringBuilder();
          if (candidates != null) {
            for (int c = 0; c < candidates.length(); c++) {
              JSONObject candidate = candidates.optJSONObject(c);
              JSONObject providerContent = candidate != null ? candidate.optJSONObject("content") : null;
              JSONArray parts = providerContent != null ? providerContent.optJSONArray("parts") : null;
              if (parts == null) continue;
              for (int p = 0; p < parts.length(); p++) {
                JSONObject responsePart = parts.optJSONObject(p);
                String piece = responsePart != null ? responsePart.optString("text", "").trim() : "";
                if (!piece.isEmpty()) {
                  if (text.length() > 0) text.append('\n');
                  text.append(piece);
                }
              }
            }
          }
          if (text.length() == 0) throw new Exception("Gemini không trả nội dung.");
          return text.toString();
        } catch (Exception e) {
          last = e;
          int code = e instanceof HttpError ? ((HttpError)e).status : 0;
          if (attempt == 0 && (code == 0 || retryable(code))) {
            try { Thread.sleep(350); } catch (InterruptedException ignored) {}
            continue;
          }
          break;
        }
      }
    }
    throw last != null ? last : new Exception("Không có Gemini API key trong APK.");
  }

  private String generateText(String prompt) throws Exception {
    return geminiText(prompt);
  }

  private String generateTextForTurn(String prompt, int estimatedInputTokens) throws Exception {
    if (!CanonRuntime2Flags.isEnabled(this)) return generateText(prompt);
    QuotaGovernor governor = canonQuotaGovernor();
    GenerationRequest request = new GenerationRequest(
      "gemini", null, GEMINI_MODEL, GenerationKind.NARRATION,
      Math.max(1, estimatedInputTokens), 60_000L);
    AdmissionDecision decision = governor.admit(request);
    if (decision instanceof AdmissionDecision.Defer) {
      throw new Exception("CR2: nhà cung cấp đang cooldown; lượt không thay đổi.");
    }
    if (decision instanceof AdmissionDecision.LocalDeterministic ||
        decision instanceof AdmissionDecision.NoCall) {
      throw new Exception("CR2: quota không cho phép gọi mô hình; lượt không thay đổi.");
    }
    String reservation = ((AdmissionDecision.SingleCall) decision).getReservationId();
    lastProviderHttpStatus.set(-1);
    lastProviderRetryAfterMs.set(-1L);
    try {
      String answer = geminiText(prompt);
      governor.recordResult(new ProviderAttemptResult(
        "gemini", GEMINI_MODEL, lastProviderHttpStatus.get() < 0 ? null : lastProviderHttpStatus.get(),
        lastProviderRetryAfterMs.get() < 0 ? null : lastProviderRetryAfterMs.get(),
        null, null, false, null, reservation));
      return answer;
    } catch (Exception failure) {
      int status = failure instanceof HttpError ? ((HttpError) failure).status : -1;
      governor.recordResult(new ProviderAttemptResult(
        "gemini", GEMINI_MODEL, status < 0 ? null : status,
        lastProviderRetryAfterMs.get() < 0 ? null : lastProviderRetryAfterMs.get(),
        null, null, false, null, reservation));
      throw failure;
    }
  }

  private QuotaGovernor canonQuotaGovernor() {
    if (canonQuotaGovernor == null) canonQuotaGovernor = new DefaultQuotaGovernor();
    return canonQuotaGovernor;
  }

  private JSONObject parseModelJson(String raw) throws Exception {
    if (raw == null) throw new Exception("AI không trả dữ liệu.");
    String text = raw.trim();
    if (text.startsWith("```")) {
      int firstNewline = text.indexOf('\n');
      if (firstNewline >= 0) text = text.substring(firstNewline + 1);
      int fence = text.lastIndexOf("```");
      if (fence >= 0) text = text.substring(0, fence);
      text = text.trim();
    }
    int start = text.indexOf('{');
    int end = text.lastIndexOf('}');
    if (start < 0 || end <= start) throw new Exception("AI trả JSON không hợp lệ.");
    return new JSONObject(text.substring(start, end + 1));
  }

  private void mergeObject(JSONObject target, JSONObject patch) throws Exception {
    Iterator<String> keys = patch.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      target.put(key, patch.get(key));
    }
  }

  private SnapshotImage findSnapshotImage(JSONObject result) {
    JSONArray steps = result.optJSONArray("steps");
    if (steps == null) return null;
    for (int i = steps.length() - 1; i >= 0; i--) {
      JSONObject step = steps.optJSONObject(i);
      if (step == null || !"model_output".equals(step.optString("type"))) continue;
      JSONArray content = step.optJSONArray("content");
      if (content == null) continue;
      for (int j = content.length() - 1; j >= 0; j--) {
        JSONObject part = content.optJSONObject(j);
        if (part == null || !"image".equals(part.optString("type"))) continue;
        String data = part.optString("data", "");
        if (data.isEmpty()) continue;
        String mimeType = part.optString("mime_type", "image/jpeg");
        return new SnapshotImage(data, mimeType);
      }
    }
    return null;
  }

  private SnapshotImage geminiImage(String prompt) throws Exception {
    Exception last = null;
    for (String key : geminiKeys()) {
      if (key == null || key.isEmpty()) continue;
      for (int attempt = 0; attempt < 2; attempt++) {
        try {
          JSONObject input = new JSONObject().put("type", "text").put("text", prompt);
          JSONObject format = new JSONObject()
            .put("type", "image")
            .put("mime_type", "image/jpeg")
            .put("aspect_ratio", "16:9")
            .put("image_size", "512");
          JSONObject body = new JSONObject()
            .put("model", GEMINI_IMAGE_MODEL)
            .put("input", new JSONArray().put(input))
            .put("response_format", format);
          JSONObject result = new JSONObject(postJson("https://generativelanguage.googleapis.com/v1beta/interactions", key, "x-goog-api-key", body));
          SnapshotImage image = findSnapshotImage(result);
          if (image == null || image.data.isEmpty()) throw new Exception("Gemini image không trả ảnh.");
          if (image.data.length() > MAX_SNAPSHOT_BASE64) throw new Exception("Snapshot quá lớn để hiển thị trong APK.");
          return image;
        } catch (Exception e) {
          last = e;
          int code = e instanceof HttpError ? ((HttpError)e).status : 0;
          if (attempt == 0 && (code == 0 || retryable(code))) {
            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
            continue;
          }
          break;
        }
      }
    }
    throw last != null ? last : new Exception("Không có Gemini API key để tạo snapshot.");
  }

  private String clipped(Object value, int max) {
    String text = value == null ? "" : String.valueOf(value);
    return text.length() > max ? text.substring(text.length() - max) : text;
  }

  private String snapshotPrompt(JSONObject state) {
    StringBuilder recent = new StringBuilder();
    JSONArray log = state.optJSONArray("log");
    if (log != null) {
      int start = Math.max(0, log.length() - 4);
      for (int i = start; i < log.length(); i++) {
        JSONObject entry = log.optJSONObject(i);
        if (entry == null) continue;
        if (recent.length() > 0) recent.append("\n\n");
        recent.append("player".equals(entry.optString("role")) ? "PLAYER: " : "GM: ");
        recent.append(clipped(entry.optString("text", ""), 1800));
      }
    }

    return "Create one cinematic 16:9 visual snapshot of the CURRENT END STATE of this Backrooms text game.\n" +
      "Show the present scene only, not a montage. Cao Minh is the main character. " +
      "Do not invent NPCs, monsters, exits, loot, injuries, weapons, text, HUD, blood or props that are not explicitly present in the state. " +
      "If party is empty, Cao Minh is alone. Level 0 uses stale yellow wallpaper, damp carpet, fluorescent ceiling panels and oppressive empty office-like geometry. " +
      "Photorealistic cinematic game concept art, grounded anatomy and materials, no written text in the image.\n\n" +
      "Turn: " + state.optInt("turn", 1) + "\n" +
      "Location: " + clipped(state.optString("location", ""), 1200) + "\n" +
      "Player: " + clipped(state.optJSONObject("player"), 1800) + "\n" +
      "Party: " + clipped(state.optJSONArray("party"), 1600) + "\n" +
      "Inventory: " + clipped(state.optJSONArray("inventory"), 2200) + "\n" +
      "Relevant flags: " + clipped(state.optJSONObject("flags"), 2200) + "\n\n" +
      "Recent context, final lines take priority:\n" + recent;
  }

  private void requestSnapshotInternal(String stateJson) {
    try {
      JSONObject snapshotState = new JSONObject(stateJson);
      int turn = snapshotState.optInt("turn", 1);
      latestSnapshotTurn.updateAndGet(current -> Math.max(current, turn));
      SnapshotImage image = geminiImage(snapshotPrompt(snapshotState));
      if (turn != latestSnapshotTurn.get()) return;
      JSONObject payload = new JSONObject()
        .put("turn", turn)
        .put("model", GEMINI_IMAGE_MODEL)
        .put("dataUri", "data:" + image.mimeType + ";base64," + image.data);
      emit("backroomSnapshot", payload.toString());
    } catch (Exception e) {
      try {
        JSONObject state = new JSONObject(stateJson);
        int turn = state.optInt("turn", 1);
        if (turn != latestSnapshotTurn.get()) return;
        JSONObject payload = new JSONObject()
          .put("turn", turn)
          .put("message", e.getMessage() == null ? "Không thể tạo snapshot." : e.getMessage());
        emit("backroomSnapshotError", payload.toString());
      } catch (Exception ignored) {
        emit("backroomSnapshotError", "{\"turn\":0,\"message\":\"Không thể tạo snapshot.\"}");
      }
    }
  }

  private void emit(String function, String json) {
    String script = "window." + function + "(" + JSONObject.quote(json) + ")";
    runOnUiThread(() -> webView.evaluateJavascript(script, null));
  }

  private String canonSection(String source, String start, String end) {
    if (source == null || start == null) return "";
    int from = source.indexOf(start);
    if (from < 0) return "";
    int to = end == null ? -1 : source.indexOf(end, from + start.length());
    return source.substring(from, to >= 0 ? to : source.length()).trim();
  }

  private String canonLineStarting(String source, String prefix) {
    if (source == null || prefix == null) return "";
    String[] lines = source.split("\\n");
    for (String line : lines) if (line.trim().startsWith(prefix)) return line.trim();
    return "";
  }

  private boolean actionDialogue(String action) {
    return containsAny(action, "hỏi", "nói", "trả lời", "gọi", "bảo", "thuyết phục", "xin lỗi", "cảm ơn", "talk", "ask", "tell");
  }

  private boolean actionCombat(String action) {
    return containsAny(action, "bắn", "đánh", "đấm", "đá", "tấn công", "phản công", "né", "chiến đấu", "devil trigger", "guilty crown", "white wraith", "magnum", "talon", "phantom", "shoot", "attack", "fight");
  }

  private boolean actionOmnivault(String action) {
    return containsAny(action, "omnivault", "nhẫn vạn tàng", "scan", "copy", "restore", "upgrade", "hoàn nguyên", "nâng cấp", "sao chép", "quét");
  }

  private boolean actionItem(String action) {
    return actionOmnivault(action) || containsAny(action, "nhặt", "lấy", "cầm", "thu hồi", "nhận", "cất", "inventory", "đồ", "vật phẩm", "chai", "nước", "almond", "loot", "crate", "liquid pain", "greek fire");
  }

  private boolean actionEntity(String action) {
    return containsAny(action, "entity", "hound", "clump", "duller", "deathmoth", "faceling", "smiler", "skin-stealer", "skin stealer", "beast", "wretch", "cable mimic", "jeff", "quái", "thực thể", "sinh vật", "kẻ săn");
  }

  private boolean presentCharacter(JSONObject state, String key) {
    if (partyHas(state, key)) return true;
    JSONObject flags = state.optJSONObject("flags");
    JSONObject record = flags != null ? flags.optJSONObject(key) : null;
    String continuity = record != null ? lower(record.optString("continuity", "")) : "";
    return containsAny(continuity, "reunited", "with kai", "together", "present");
  }

  private String compactDriveCanon(JSONObject state, String action, JSONObject rolls) {
    StringBuilder out = new StringBuilder();
    String scope = canonSection(DRIVE_CANON, "PHẠM VI", "VĂN PHONG VÀ KINH DỊ");
    String writing = canonSection(DRIVE_CANON, "VĂN PHONG VÀ KINH DỊ", "THẾ GIỚI");
    String world = canonSection(DRIVE_CANON, "THẾ GIỚI", "LEVEL 0–6");
    String gameplay = canonSection(DRIVE_CANON, "GAMEPLAY HARD LOCK", "END DRIVE CANON R06");
    String levelLine = canonLineStarting(DRIVE_CANON, "- Level " + currentLevel(state) + " /");
    out.append(scope).append("\n\n").append(writing).append("\n\n").append(world);
    if (!levelLine.isEmpty()) out.append("\n\nCURRENT LEVEL HARD CANON\n").append(levelLine);

    boolean entity = actionEntity(action) || rollSuccess(rolls, "entityEncounter") ||
      (state.optJSONObject("flags") != null && state.optJSONObject("flags").optInt("entitiesConfirmedLocal", 0) > 0);
    boolean item = actionItem(action) || rollSuccess(rolls, "loot") || rollSuccess(rolls, "almondWater");
    if (entity || item) {
      String resources = canonSection(DRIVE_CANON, "ENTITY VÀ TÀI NGUYÊN", "LỤC TRẦM / SYVIAL");
      if (!resources.isEmpty()) out.append("\n\n").append(resources);
    }

    boolean character = actionDialogue(action) || presentCharacter(state, "luc-tram") || presentCharacter(state, "syvial") ||
      rollSuccess(rolls, "lucTramReunion") || rollSuccess(rolls, "syvialReunion");
    if (character) {
      String characterCanon = canonSection(DRIVE_CANON, "LỤC TRẦM / SYVIAL", "GAMEPLAY HARD LOCK");
      if (!characterCanon.isEmpty()) out.append("\n\n").append(characterCanon);
    } else {
      out.append("\n\nLỤC TRẦM / SYVIAL SEPARATION KERNEL\n- Khi continuity còn SEPARATED, Cao Minh không biết vị trí/tình trạng hiện tại của Lục Trầm hoặc Syvial và không được dùng dữ kiện hậu trường về họ.");
    }
    out.append("\n\n").append(gameplay);
    return out.toString();
  }

  private String compactCaoMinhCanon(String action) {
    StringBuilder out = new StringBuilder();
    out.append(canonSection(CAO_MINH_CANON, "1. ĐỊNH DANH", "2. NGOẠI HÌNH"));
    out.append("\n\n").append(canonSection(CAO_MINH_CANON, "3. TÍNH CÁCH / NGUYÊN TẮC", "4. PHONG CÁCH GIAO TIẾP"));
    out.append("\n\n").append(canonSection(CAO_MINH_CANON, "4. PHONG CÁCH GIAO TIẾP", "5. NĂNG LỰC CHIẾN ĐẤU"));
    out.append("\n\n").append(canonSection(CAO_MINH_CANON, "5. NĂNG LỰC CHIẾN ĐẤU", "6. VẠN QUỶ MA TÂM"));
    out.append("\n\n").append(canonSection(CAO_MINH_CANON, "6. VẠN QUỶ MA TÂM", "7. VẠN QUỶ MA THÂN"));
    out.append("\n\n").append(canonSection(CAO_MINH_CANON, "10. HUYẾT MA CHIẾN KHẢI & CÁC PHẦN LIÊN KẾT", "11. VẠN KHỐ GIỚI CHỈ"));
    out.append("\n\n").append(canonSection(CAO_MINH_CANON, "13. GIỚI HẠN THỰC SỰ", "14. ACTION LOCKS / CẤM MODEL TỰ BỊA"));
    out.append("\n\n").append(canonSection(CAO_MINH_CANON, "14. ACTION LOCKS / CẤM MODEL TỰ BỊA", "END OF CAO MINH OPERATIONAL CODEX"));
    if (actionCombat(action)) {
      out.append("\n\n").append(canonSection(CAO_MINH_CANON, "7. VẠN QUỶ MA THÂN", "10. HUYẾT MA CHIẾN KHẢI & CÁC PHẦN LIÊN KẾT"));
      out.append("\n\n").append(canonSection(CAO_MINH_CANON, "12. PHONG CÁCH CHIẾN ĐẤU", "13. GIỚI HẠN THỰC SỰ"));
    }
    if (actionOmnivault(action) || actionItem(action)) {
      out.append("\n\n").append(canonSection(CAO_MINH_CANON, "11. VẠN KHỐ GIỚI CHỈ", "12. PHONG CÁCH CHIẾN ĐẤU"));
    }
    return out.toString();
  }

  private JSONObject compactStateForPrompt(JSONObject state) throws Exception {
    JSONObject compact = new JSONObject(state.toString());
    compact.remove("snapshotUrl");
    compact.remove("coreSnapshot");
    compact.remove("exitResult");
    JSONObject flagsForPrompt = compact.optJSONObject("flags");
    JSONObject memory = flagsForPrompt == null ? null : flagsForPrompt.optJSONObject("storyContinuity");
    if (memory != null) memory.remove("levelTravelEvents");
    JSONObject travel = compact.optJSONObject("levelTravel");
    if (travel != null) travel.remove("receipts");
    compact.remove("_snapshotEvent");
    JSONArray log = state.optJSONArray("log");
    if (log != null) {
      JSONArray recent = new JSONArray();
      int start = Math.max(0, log.length() - 6);
      for (int i = start; i < log.length(); i++) recent.put(log.get(i));
      compact.put("log", recent);
    }
    return compact;
  }

  private int arrayIndexByName(JSONArray array, String name) {
    if (array == null || name == null) return -1;
    String needle = lower(name).trim();
    for (int i = 0; i < array.length(); i++) {
      if (lower(itemName(array.opt(i))).trim().equals(needle)) return i;
    }
    return -1;
  }

  private String levelName(int number) {
    String[] names = {"The Lobby", "Parking Zone", "Pipe Dreams", "The Electrical Station", "The Abandoned Office", "Terror Hotel", "Lights Out"};
    int safe = Math.max(0, Math.min(6, number));
    return names[safe];
  }

  private boolean acquisitionIntent(String action) {
    return containsAny(action, "nhặt", "lấy", "cầm", "thu hồi", "tịch thu", "nhận", "cất", "bỏ vào", "đưa vào omnivault", "store", "sao chép", "copy");
  }

  private boolean removalIntent(String action) {
    return containsAny(action, "trao", "đưa cho", "vứt", "bỏ lại", "ném", "uống", "tiêu thụ", "dùng hết", "phá hủy", "làm mất", "mất ");
  }

  private boolean characterAddAllowed(JSONObject before, String name, JSONObject rolls) {
    String value = lower(name);
    if (value.contains("an nhiên") || value.contains("an nhien") || value.contains("an-nhien")) return anNhienEncountered(before) || rollSuccess(rolls, "anNhienEncounter");
    if (value.contains("luc-tram")) return presentCharacter(before, "luc-tram") || rollSuccess(rolls, "lucTramReunion");
    if (value.contains("syvial")) return presentCharacter(before, "syvial") || rollSuccess(rolls, "syvialReunion");
    return rollSuccess(rolls, "survivor");
  }

  private boolean flagRootAllowed(JSONObject before, String root, JSONObject rolls) {
    if (root == null) return false;
    if (root.equals("exploration") || root.equals("communication") || root.equals("omnivault") || root.equals("visualAreaKey") ||
        root.equals("visualEventKey") || root.equals("reunionPath")) return true;
    if (root.equals("luc-tram")) return presentCharacter(before, "luc-tram") || rollSuccess(rolls, "lucTramReunion");
    if (root.equals("syvial")) return presentCharacter(before, "syvial") || rollSuccess(rolls, "syvialReunion");
    if (root.equals("jeff") || root.equals("entityRegistry") || root.equals("entitiesConfirmedLocal") || root.equals("entityEncounterKey")) {
      JSONObject flags = before.optJSONObject("flags");
      return rollSuccess(rolls, "entityEncounter") || (flags != null && flags.optInt("entitiesConfirmedLocal", 0) > 0);
    }
    if (root.equals("survivorRegistry") || root.equals("survivorsConfirmed")) {
      JSONObject flags = before.optJSONObject("flags");
      return rollSuccess(rolls, "survivor") || (flags != null && flags.optInt("survivorsConfirmed", 0) > 0);
    }
    return false;
  }

  private JSONObject applyModelOperations(JSONObject before, JSONArray ops, JSONObject rolls, String action) throws Exception {
    JSONObject state = new JSONObject(before.toString());
    if (ops == null) return state;
    int limit = Math.min(24, ops.length());
    for (int i = 0; i < limit; i++) {
      JSONObject op = ops.optJSONObject(i);
      if (op == null) continue;
      String type = lower(op.optString("type", "")).trim();

      if (type.equals("set_location")) {
        String value = op.optString("value", "").trim();
        if (!value.isEmpty() && value.length() <= 700) state.put("location", value);
        continue;
      }

      if (type.equals("set_level")) continue; // GM has no Level authority.

      if (type.equals("patch_player")) {
        JSONObject patch = op.optJSONObject("patch");
        if (patch == null) continue;
        JSONObject current = state.optJSONObject("player");
        if (current == null) current = new JSONObject();
        for (String key : new String[] {"hp", "condition", "weapon", "armor"}) if (patch.has(key)) current.put(key, patch.get(key));
        if (patch.optJSONObject("needs") != null) {
          JSONObject needs = current.optJSONObject("needs");
          if (needs == null) needs = new JSONObject();
          mergeObject(needs, patch.optJSONObject("needs"));
          current.put("needs", needs);
        }
        JSONObject oldPlayer = before.optJSONObject("player");
        current.put("name", oldPlayer != null ? oldPlayer.optString("name", "Kai Akechi") : "Kai Akechi");
        if (oldPlayer != null && oldPlayer.has("codename")) current.put("codename", oldPlayer.get("codename"));
        state.put("player", current);
        continue;
      }

      // GM operations never mutate Item ownership.
      if (type.equals("inventory_upsert") || type.equals("inventory_remove")) continue;

      if (type.equals("party_upsert")) {
        JSONObject member = op.optJSONObject("member");
        if (member == null) continue;
        String name = member.optString("name", "").trim();
        if (name.isEmpty()) continue;
        JSONArray party = state.optJSONArray("party");
        if (party == null) party = new JSONArray();
        int existing = arrayIndexByName(party, name);
        if (existing >= 0) party.put(existing, new JSONObject(member.toString()));
        else if (characterAddAllowed(before, name, rolls)) party.put(new JSONObject(member.toString()));
        state.put("party", party);
        continue;
      }

      if (type.equals("party_remove")) {
        String name = op.optString("name", "").trim();
        JSONArray party = state.optJSONArray("party");
        int existing = arrayIndexByName(party, name);
        if (party != null && existing >= 0 && containsAny(action, "rời", "tách", "ở lại", "đuổi", "chia nhóm", "mất dấu")) party.remove(existing);
        continue;
      }

      if (type.equals("flag_patch")) {
        String root = op.optString("root", "").trim();
        if (!flagRootAllowed(before, root, rolls) || !op.has("value")) continue;
        JSONObject flags = state.optJSONObject("flags");
        if (flags == null) flags = new JSONObject();
        Object value = op.get("value");
        Object current = flags.opt(root);
        if (current instanceof JSONObject && value instanceof JSONObject) {
          JSONObject merged = new JSONObject(current.toString());
          mergeObject(merged, (JSONObject) value);
          flags.put(root, merged);
        } else {
          flags.put(root, value);
        }
        state.put("flags", flags);
      }
    }

    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) flags = new JSONObject();
    flags.put("lastRolls", rolls);

    boolean anNhienNow = anNhienEncountered(before) || rollSuccess(rolls, "anNhienEncounter");
    if (anNhienNow) {
      JSONObject anNhien = flags.optJSONObject("anNhien");
      if (anNhien == null) anNhien = new JSONObject();
      anNhien.put("encountered", true)
        .put("present", true)
        .put("follower", true)
        .put("nonCombat", true)
        .put("levelEncountered", currentLevel(before))
        .put("lootBonusPercent", 10)
        .put("exitBonusPercent", 2);
      flags.put("anNhien", anNhien);

      boolean anNhienJoined = ensureSpecialFollowerInLegacyParty(state, "an-nhien", "An Nhiên", true);
      anNhien.put("joinPending", !anNhienJoined);
    }
    if (rollSuccess(rolls, "lucTramReunion")) {
      JSONObject lucTram = flags.optJSONObject("luc-tram");
      if (lucTram == null) lucTram = new JSONObject();
      boolean lucTramJoined = ensureSpecialFollowerInLegacyParty(state, "luc-tram", "Lục Trầm", false);
      lucTram.put("exists", true)
        .put("encountered", true)
        .put("present", true)
        .put("spawned", true)
        .put("follower", true)
        .put("reunionEligible", false)
        .put("continuity", "REUNITED")
        .put("levelEncountered", currentLevel(before))
        .put("joinPending", !lucTramJoined);
      flags.put("luc-tram", lucTram);
    }

    if (rollSuccess(rolls, "syvialReunion")) {
      JSONObject syvial = flags.optJSONObject("syvial");
      if (syvial == null) syvial = new JSONObject();
      boolean syvialJoined = ensureSpecialFollowerInLegacyParty(state, "syvial", "Syvial", false);
      syvial.put("exists", true)
        .put("encountered", true)
        .put("present", true)
        .put("spawned", true)
        .put("follower", true)
        .put("reunionEligible", false)
        .put("continuity", "REUNITED")
        .put("levelEncountered", currentLevel(before))
        .put("joinPending", !syvialJoined);
      flags.put("syvial", syvial);
    }

    state.put("flags", flags);
    return state;
  }

  private int currentLevel(JSONObject state) {
    JSONObject level = state.optJSONObject("level");
    if (level != null) return Math.max(0, level.optInt("number", 0));
    String title = state.optString("title", "");
    for (int n = 0; n <= 6; n++) if (title.contains("Level " + n)) return n;
    return 0;
  }

  private JSONObject rollSpec(String label, int chance, boolean eligible) throws Exception {
    JSONObject result = new JSONObject().put("label", label).put("eligible", eligible).put("chancePercent", chance);
    if (!eligible) return result.put("success", false).put("roll", JSONObject.NULL);
    int roll = GAME_RNG.nextInt(100) + 1;
    return result.put("roll", roll).put("success", roll <= chance);
  }

  private static String lower(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  private boolean containsAny(String text, String... terms) {
    String value = lower(text);
    for (String term : terms) if (value.contains(lower(term))) return true;
    return false;
  }

  private boolean partyHas(JSONObject state, String needle) {
    JSONArray party = state.optJSONArray("party");
    if (party == null) return false;
    for (int i = 0; i < party.length(); i++) {
      Object item = party.opt(i);
      String name = item instanceof JSONObject ? ((JSONObject)item).optString("name", "") : String.valueOf(item);
      if (lower(name).contains(lower(needle))) return true;
    }
    return false;
  }

  private boolean flagSpawned(JSONObject state, String key) {
    JSONObject flags = state.optJSONObject("flags");
    JSONObject value = flags != null ? flags.optJSONObject(key) : null;
    return value != null && (value.optBoolean("spawned", false) || value.optBoolean("present", false));
  }

  private boolean isMetaAction(String action) {
    return containsAny(action,
      "xem trạng thái", "trạng thái hiện tại", "xem state", "xem inventory", "xem túi", "kiểm tra inventory",
      "xem party", "xem nhân vật", "xem thuộc tính", "status", "show state", "show inventory", "show party");
  }

  private JSONObject thresholdRoll(String label, int max, int threshold, boolean eligible, String suffix) throws Exception {
    JSONObject result = new JSONObject()
      .put("label", label)
      .put("dice", threshold >= max ? "none" : "d" + max)
      .put("max", max)
      .put("threshold", threshold)
      .put("eligible", eligible && threshold > 0);
    double percent = max > 0 ? (threshold * 100.0 / max) : 0.0;
    result.put("chancePercent", percent).put("chance", String.format(java.util.Locale.ROOT, "%.4f%%%s", percent, suffix == null ? "" : suffix));
    if (!eligible || threshold <= 0) return result.put("roll", JSONObject.NULL).put("success", false);
    if (threshold >= max) return result.put("roll", JSONObject.NULL).put("success", true).put("guaranteedByState", true);
    int roll = GAME_RNG.nextInt(max) + 1;
    return result.put("roll", roll).put("success", roll <= threshold);
  }

  private int exitThresholdAndroid(JSONObject state) {
    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) return 10;
    int explicit = flags.optInt("exitChanceThreshold", -1);
    if (explicit >= 0 && explicit <= 10000) return explicit;
    String progress = flags.optString("exitProgress", "");
    JSONObject exploration = flags.optJSONObject("exploration");
    if (progress.isEmpty() && exploration != null) progress = exploration.optString("exitProgress", "");
    String upper = progress.toUpperCase(java.util.Locale.ROOT);
    if (containsAny(upper, "READY", "GUARANTEED", "CONDITION MET", "TRANSITION AVAILABLE")) return 10000;
    if (containsAny(upper, "NEAR", "ALMOST", "VERY STRONG")) return 150;
    if (containsAny(upper, "STRONG", "CORRECT ROUTE")) return 100;
    if (containsAny(upper, "CLUE", "CANDIDATE", "OPENED", "OBSERVED", "TRACKED")) return 50;
    return 10;
  }

  private boolean anNhienFollowing(JSONObject state) {
    return partyHas(state, "An Nhiên") || partyHas(state, "an-nhien");
  }

  private boolean anNhienEncountered(JSONObject state) {
    if (anNhienFollowing(state)) return true;
    JSONObject flags = state.optJSONObject("flags");
    JSONObject record = flags != null ? flags.optJSONObject("anNhien") : null;
    return record != null && record.optBoolean("encountered", false);
  }

  private boolean ensureSpecialFollowerInLegacyParty(JSONObject state, String id, String name, boolean nonCombat) throws Exception {
    JSONArray party = state.optJSONArray("party");
    if (party == null) party = new JSONArray();
    String targetId = lower(id).trim();
    String targetName = lower(name).trim();
    for (int i = 0; i < party.length(); i++) {
      Object item = party.opt(i);
      if (!(item instanceof JSONObject)) continue;
      JSONObject member = (JSONObject)item;
      if (lower(member.optString("id", "")).trim().equals(targetId) ||
          lower(member.optString("name", "")).trim().equals(targetName)) {
        state.put("party", party);
        return true;
      }
    }
    // Legacy party excludes Kai, so three entries means the authoritative 4-member party is full.
    if (party.length() >= 3) {
      state.put("party", party);
      return false;
    }
    party.put(new JSONObject()
      .put("id", id)
      .put("name", name)
      .put("present", true)
      .put("joinConfirmed", true)
      .put("presence", "ACTIVE")
      .put("role", "follower")
      .put("nonCombat", nonCombat));
    state.put("party", party);
    return true;
  }

  private boolean reunionEligibleAndroid(JSONObject state, String key) {
    JSONObject flags = state.optJSONObject("flags");
    JSONObject record = flags != null ? flags.optJSONObject(key) : null;
    if (record == null || !record.optBoolean("exists", true)) return false;
    if (partyHas(state, key) || flagSpawned(state, key)) return false;
    if (record.has("reunionEligible") && !record.optBoolean("reunionEligible", true)) return false;
    String continuity = record.optString("continuity", "").toUpperCase(java.util.Locale.ROOT);
    return continuity.isEmpty() || containsAny(continuity, "SEPARATED", "LOST", "UNKNOWN");
  }

  private JSONObject makeGameplayRolls(JSONObject state, String action, boolean meta) throws Exception {
    JSONObject rolls = new JSONObject().put("turn", state.optInt("turn", 1)).put("meta", meta);
    if (meta) return rolls;

    int level = Math.max(0, Math.min(6, currentLevel(state)));
    int[] hazardThresholds = {400, 700, 1000, 1200, 300, 1000, 1200};
    int[] entityThresholds = {5, 200, 350, 350, 10, 400, 5};
    int[] lootThresholds = {35, 120, 100, 150, 180, 100, 45};
    int[] waterThresholds = {20, 70, 35, 20, 120, 60, 35};

    String a = lower(action);
    boolean physical = containsAny(a, "đi", "bước", "chạy", "leo", "mở", "đóng", "chạm", "lục", "tìm", "kiểm tra", "khảo sát", "quét", "scan", "bắn", "phá", "đẩy", "kéo", "tiến", "lùi", "cúi", "nhìn vào", "bò", "nhảy", "đào", "tháo", "đập", "vượt", "đi qua");
    boolean search = containsAny(a, "tìm", "lục", "khám phá", "khảo sát", "kiểm tra", "quét", "scan", "mở", "tháo", "quan sát kỹ", "rà");
    boolean water = containsAny(a, "nước", "water", "almond", "uống", "khát", "chai", "vòi", "hồ", "fountain");
    boolean exitIntent = containsAny(a, "exit", "lối thoát", "thoát", "cửa trắng", "cánh cửa", "ngưỡng", "chuyển level", "sang level", "hành lang phía sau", "đường ra");
    boolean anNhienFollowing = anNhienFollowing(state);
    boolean anNhienEncountered = anNhienEncountered(state);

    JSONObject flags = state.optJSONObject("flags");
    boolean survivorAllowed = flags == null || flags.optBoolean("survivorEncountersAllowed", true);
    boolean entityAllowed = flags == null || flags.optBoolean("entityEncountersAllowed", true);

    rolls.put("anNhienEncounter", thresholdRoll("anNhienEncounter", 10000, 25, physical && !anNhienEncountered, " follower encounter"));
    rolls.put("survivor", thresholdRoll("survivor", 10000, 200, survivorAllowed, ""));
    rolls.put("lucTramReunion", thresholdRoll("lucTramReunion", 10000, 25, physical && reunionEligibleAndroid(state, "luc-tram"), " follower encounter"));
    rolls.put("syvialReunion", thresholdRoll("syvialReunion", 10000, 25, physical && reunionEligibleAndroid(state, "syvial"), " follower encounter"));
    int anNhienHazardThreshold = anNhienFollowing ? (hazardThresholds[level] * 75 / 100) : hazardThresholds[level];
    JSONObject anNhienHazardCheck = thresholdRoll("anNhienHazardCheck", 10000, 3000, anNhienFollowing && search && water, " Đừng Đụng Vào, Nhìn Là Biết Độc");
    rolls.put("anNhienHazardCheck", anNhienHazardCheck);
    if (anNhienHazardCheck.optBoolean("success", false)) anNhienHazardThreshold = 0;
    rolls.put("hazard", thresholdRoll("hazard", 10000, anNhienHazardThreshold, physical,
      anNhienFollowing ? " -25% Có Gì Đó Sai Sai" : ""));
    String entitySuffix = level == 0 || level == 4 || level == 6 ? " incursion/roaming only" : "";
    rolls.put("entityEncounter", thresholdRoll("entityEncounter", 10000, entityThresholds[level], physical && entityAllowed, entitySuffix));
    rolls.put("loot", thresholdRoll("loot", 10000, Math.min(10000, lootThresholds[level] + (anNhienFollowing ? 1000 : 0)), search, anNhienFollowing ? " +10% An Nhiên" : ""));
    rolls.put("almondWater", thresholdRoll("almondWater", 10000, waterThresholds[level], search && water, ""));

    // Exit attempts are resolved atomically by Core before this turn/GM pipeline.
    // These obsolete narrator-driven loot rolls are inert. CoreLootEngine is
    // the sole authority for 80% Entity defeat and 8% exploration item drops.
    rolls.put("loot", new JSONObject().put("label", "loot").put("eligible", false).put("success", false));
    rolls.put("almondWater", new JSONObject().put("label", "almondWater").put("eligible", false).put("success", false));
    return rolls;
  }

  private boolean rollSuccess(JSONObject rolls, String key) {
    JSONObject roll = rolls.optJSONObject(key);
    return roll != null && roll.optBoolean("success", false);
  }

  private String itemName(Object item) {
    if (item instanceof JSONObject) return ((JSONObject)item).optString("name", "");
    return item == null ? "" : String.valueOf(item);
  }

  private boolean arrayHasName(JSONArray array, String name) {
    if (array == null || name == null) return false;
    String target = lower(name).trim();
    for (int i = 0; i < array.length(); i++) if (lower(itemName(array.opt(i))).trim().equals(target)) return true;
    return false;
  }

  private JSONArray sanitizedInventory(JSONArray current, JSONArray proposed, JSONObject rolls) throws Exception {
    // Core alone changes Item ownership. Ignore all GM inventory proposals.
    return current == null ? new JSONArray() : new JSONArray(current.toString());
  }

  private JSONArray sanitizedParty(JSONArray current, JSONArray proposed, JSONObject rolls) throws Exception {
    if (proposed == null) return current == null ? new JSONArray() : new JSONArray(current.toString());
    JSONArray safe = current == null ? new JSONArray() : new JSONArray(current.toString());
    for (int i = 0; i < proposed.length(); i++) {
      Object member = proposed.opt(i);
      String name = itemName(member);
      if (arrayHasName(safe, name)) continue;
      String lowered = lower(name);
      boolean allowed = (lowered.contains("luc-tram") && rollSuccess(rolls, "lucTramReunion")) ||
        (lowered.contains("syvial") && rollSuccess(rolls, "syvialReunion")) ||
        (!lowered.contains("luc-tram") && !lowered.contains("syvial") && rollSuccess(rolls, "survivor"));
      if (allowed) safe.put(member);
    }
    return safe;
  }

  /** Recursive merge for validated state flags; distinct from provider response merge. */
  private void mergeFlagsObject(JSONObject target, JSONObject patch) throws Exception {
    if (patch == null) return;
    Iterator<String> keys = patch.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      Object value = patch.opt(key);
      if (value instanceof JSONObject && target.opt(key) instanceof JSONObject) {
        mergeFlagsObject(target.optJSONObject(key), (JSONObject) value);
      } else {
        target.put(key, value);
      }
    }
  }


  private JSONObject sanitizedFlags(JSONObject current, JSONObject proposed, JSONObject rolls, boolean transitionAccepted) throws Exception {
    JSONObject safe = current == null ? new JSONObject() : new JSONObject(current.toString());
    if (proposed == null) return safe;
    JSONObject patch = new JSONObject(proposed.toString());
    patch.remove("worldItems"); // Only CoreLootEngine may create world loot.
    patch.remove("lastRolls");
    if (!transitionAccepted) patch.remove("currentLevel");

    if (patch.optJSONObject("luc-tram") != null) {
      JSONObject oldLucTram = safe.optJSONObject("luc-tram");
      JSONObject newLucTram = patch.optJSONObject("luc-tram");
      if ((oldLucTram == null || !oldLucTram.optBoolean("present", false)) && newLucTram.optBoolean("present", false) && !rollSuccess(rolls, "lucTramReunion")) patch.remove("luc-tram");
    }
    if (patch.optJSONObject("syvial") != null) {
      JSONObject oldSyvial = safe.optJSONObject("syvial");
      JSONObject newSyvial = patch.optJSONObject("syvial");
      if ((oldSyvial == null || !oldSyvial.optBoolean("present", false)) && newSyvial.optBoolean("present", false) && !rollSuccess(rolls, "syvialReunion")) patch.remove("syvial");
    }
    mergeFlagsObject(safe, patch);
    return safe;
  }

  private JSONObject sanitizedPlayer(JSONObject current, JSONObject proposed) throws Exception {
    if (proposed == null) return current == null ? new JSONObject() : new JSONObject(current.toString());
    JSONObject safe = current == null ? new JSONObject() : new JSONObject(current.toString());
    String name = safe.optString("name", "Kai Akechi");
    String codename = safe.optString("codename", "Twilight");
    for (String key : new String[] {"hp", "condition", "needs", "weapon", "armor"}) if (proposed.has(key)) safe.put(key, proposed.get(key));
    safe.put("name", name).put("codename", codename);
    return safe;
  }

  private JSONObject sanitizedSnapshotEvent(JSONObject generated, JSONObject rolls, boolean transitionAccepted, boolean levelChanged, boolean meta) throws Exception {
    JSONObject event = generated.optJSONObject("snapshotEvent");
    JSONObject safe = new JSONObject().put("shouldGenerate", false).put("kind", "").put("reason", "");
    if (meta || event == null || !event.optBoolean("shouldGenerate", false)) return safe;
    String kind = lower(event.optString("kind", ""));
    boolean allowed = false;
    if (kind.equals("level_transition")) allowed = transitionAccepted && levelChanged;
    else if (kind.equals("entity_encounter")) allowed = rollSuccess(rolls, "entityEncounter");
    else if (kind.equals("character_encounter")) allowed = rollSuccess(rolls, "anNhienEncounter") || rollSuccess(rolls, "survivor") || rollSuccess(rolls, "lucTramReunion") || rollSuccess(rolls, "syvialReunion");
    else if (kind.equals("special_area")) allowed = true;
    if (!allowed) return safe;
    return new JSONObject().put("shouldGenerate", true).put("kind", kind).put("reason", event.optString("reason", ""));
  }


  private interface CombatDiceCall { String run() throws Exception; }

  private void emitCombatDiceResult(CombatDiceCall call) {
    try {
      JSONObject result = new JSONObject(call.run());
      if (!result.optBoolean("handled", false)) {
        emit("backroomCombatDiceError", result.optString("error", "Poker Dice không khả dụng."));
        return;
      }
      emit("backroomCombatDiceState", result.getJSONObject("state").toString());
    } catch (Exception error) {
      emit("backroomCombatDiceError",
        error.getMessage() == null ? "Không thể cập nhật Poker Dice." : error.getMessage());
    }
  }

  private class GameBridge {
    // LEVEL_TRAVEL_BRIDGE: synchronous startup/restore; never invoked by rendering.
    @JavascriptInterface public String initializeLevelGame(String stateJson, boolean fresh) {
      return requireGameCore().initializeLevelGame(stateJson, fresh);
    }
    @JavascriptInterface public String resumeLevelGame(String stateJson, boolean hasBrowserSave) {
      return requireGameCore().resumeLevelGame(stateJson, hasBrowserSave);
    }
    @JavascriptInterface public String levelCoreSnapshot() {
      return requireGameCore().currentCoreState();
    }

    @JavascriptInterface public void submitTurn(String stateJson, String action) {
      io.execute(() -> {
        try {
          JSONObject levelAction = new JSONObject(requireGameCore().processLevelAction(stateJson, action));
          if (levelAction.optBoolean("handled", false)) {
            emit("backroomTurn", levelAction.getJSONObject("state").toString());
            return;
          }
          JSONObject before = levelAction.getJSONObject("state");
          boolean meta = isMetaAction(action);
          if (!meta) {
            JSONObject lootResult = new JSONObject(requireGameCore().processExplorationLoot(before.toString(), action));
            if (!lootResult.optBoolean("handled", false) || lootResult.optJSONObject("state") == null)
              throw new IllegalStateException("Core loot resolution failed");
            before = lootResult.getJSONObject("state");
          }
          JSONObject rolls = makeGameplayRolls(before, action, meta);
          // CANON_RUNTIME_2_WIRING: OFF leaves the original GM path unchanged.
          String canonPacket = null;
          if (CanonRuntime2Flags.isEnabled(MainActivity.this) || before.has("levelTravel")) {
            if (meta) {
              // CR2 meta is local-only: no GM call, no turn increment.
              JSONObject result = new JSONObject(before.toString());
              result.remove("combatFeedback");
              result.put("canonVersion", DRIVE_CANON_VERSION);
              result.put("_snapshotEvent", new JSONObject()
                .put("shouldGenerate", false).put("kind", "").put("reason", ""));
              JSONArray history = result.optJSONArray("log");
              if (history == null) history = new JSONArray();
              history.put(new JSONObject().put("role", "player").put("text", action));
              history.put(new JSONObject().put("role", "gm")
                .put("text", "Đây là lượt tra cứu. Trạng thái và lượt chơi không thay đổi."));
              result.put("log", history);
              emit("backroomTurn", result.toString());
              return;
            }
            CanonOutcome outcome = CanonRuntime2.buildPacket(
              MainActivity.this, before.toString(), action, rolls.toString(), false);
            if (outcome instanceof CanonOutcome.SafeLocalResolution) {
              emit("backroomError", "CR2 chưa thể giải quyết an toàn lượt này: " +
                ((CanonOutcome.SafeLocalResolution) outcome).getReason());
              return;
            }
            if (outcome instanceof CanonOutcome.Ready) {
              canonPacket = ((CanonOutcome.Ready) outcome).getPacket();
            }
          }
          String prompt = "Bạn là Game Master duy nhất của text game Backrooms, phát ngôn như người kể chuyện trong game. Trả DUY NHẤT một JSON hợp lệ, không markdown. " +
            "Canon R06 dưới đây là HARD LOCK; state hiện tại là source of truth cho continuity đang sống. UNKNOWN phải giữ UNKNOWN. Không tự lấp chỗ trống canon. " +
            "Người chơi chỉ điều khiển hành động có chủ ý của Cao Minh; Game Master không tự quyết lựa chọn thay Kai. " +
            "GAMEPLAY_ROLLS do Android sinh là bất biến: chỉ outcome success=true mới được xuất hiện. Không reroll, không tự đổi xác suất, không tự tạo encounter/item/reunion/level transition trái roll. " +
            "AN NHIÊN HARD LOCK: bé gái 7 tuổi, con người, không phải Entity. anNhienEncounter là roll độc lập 0.2500% trên mỗi lượt physical đủ điều kiện ở Level 0–6 khi chưa gặp; không còn là cuộc gặp bắt buộc ở Level 0 và không được chặn việc rời Level 0. Chỉ khi success=true mới được cho An Nhiên xuất hiện trong lượt đó. Sau khi gặp, cô là follower của Cao Minh nếu Party còn chỗ; nếu Party đầy thì giữ cô present với joinPending=true thay vì đuổi một thành viên khác. Cô không chiến đấu, không dùng vũ khí, và chỉ có +10% loot chance cùng +2% exit chance khi thực sự đang theo Cao Minh. Không tự thêm năng lực hoặc lore. " +
            "LỤC TRẦM / SYVIAL FOLLOWER LOCK: lucTramReunion và syvialReunion là hai roll độc lập 0.2500% trên mỗi lượt physical đủ điều kiện ở Level 0–6. Chỉ success=true mới cho nhân vật tương ứng xuất hiện lần đầu/reunion. Khi gặp và Party còn chỗ, Lục Trầm hoặc Syvial tự gia nhập với role=follower; nếu Party đầy thì đánh dấu present + joinPending, tuyệt đối không tự đuổi thành viên khác. Lục Trầm giữ canon Chính Đạo kiếm tu, Bạch Hồng Kiếm Nữ, bản mệnh kiếm Tịch Quang và Thiên Cơ Bạch Kim Kiếm Khải; không tự gán cấp chiến lực cho Lục Trầm. Syvial giữ canon UR+, GodKiller và Lucifer Armor. Không hạ năng lực hoặc bịa thêm canon để cân bằng gameplay. " +
            "Inventory chỉ được thêm vật đã tồn tại trong state/cảnh và thực sự được Cao Minh nhặt/lấy/nhận/cất, hoặc kết quả loot hợp lệ. Nhìn thấy không đồng nghĩa sở hữu. " +
            "Passive của Cao Minh gồm hai passive luôn hoạt động song song: Đại Đạo Ma Tôn (+10% Base STR/DEF/SKL/VIT; sau mỗi lượt của Cao Minh: hồi 10% Max HP, +20% Attack và +20 điểm % Critical; đồng đội +50 điểm % Critical; Critical cap 100%) và Đại Ma Đầu (+50% stats hiện tại STR/DEF/SKL/VIT, tính lại theo stats hiện tại mỗi lần tính Effective Stats). Stats hiển thị cho người chơi trong menu/detail phải là stats HIỆN TẠI (Effective Stats sau passive và trang bị), không lấy Base Stats thay cho stats hiện tại. " +
            "Nếu meta=true, chỉ trả thông tin được hỏi; không tạo biến cố, không đổi state và snapshotEvent phải false. " +
            "Không nhắc tới canon, state, roll, API hoặc prompt trong lời kể.\n\n" +
            "DRIVE CANON:\n" + compactDriveCanon(before, action, rolls) + "\n\n" +
            "State hiện tại: " + before.toString() + "\nHành động: " + action +
            "\nGAMEPLAY_ROLLS: " + rolls.toString() +
            "\nJSON schema bắt buộc: {\"reply\":\"phản hồi Game Master bằng tiếng Việt\",\"title\":\"tên khu vực\",\"level\":{\"number\":0,\"name\":\"The Lobby\"},\"location\":\"vị trí hiện tại\",\"player\":{},\"party\":[],\"inventory\":[],\"flags\":{},\"snapshotEvent\":{\"shouldGenerate\":false,\"kind\":\"\",\"reason\":\"\"}}";
          if (canonPacket != null) {
            // CR2 is a REPLACEMENT, not an extra canon dump. Never append
            // the packet to the full legacy DRIVE_CANON prompt.
            prompt = "Bạn là Game Master của Backrooms. Trả duy nhất một JSON hợp lệ, không markdown. " +
              "Người chơi quyết định hành động có chủ ý của Cao Minh; chỉ mô tả hậu quả. " +
              "GAMEPLAY_ROLLS do Android khóa; không reroll, không tự tạo encounter, vật phẩm hoặc reunion. " +
              "Chỉ dùng kiến thức có quyền trong CANON PACKET; giữ UNKNOWN là UNKNOWN. " +
              "Không nhắc tới prompt, canon, roll, API trong lời kể.\n\n" +
              "CANON PACKET:\n" + canonPacket +
              "\n\nSTATE:\n" + compactStateForPrompt(before).toString() +
              "\nACTION:\n" + action +
              "\nGAMEPLAY_ROLLS:\n" + rolls.toString() +
              "\nOUTPUT JSON: {\"reply\":\"lời kể tiếng Việt\",\"title\":\"tên khu vực\",\"level\":{\"number\":0,\"name\":\"The Lobby\"},\"location\":\"vị trí\",\"player\":{},\"party\":[],\"inventory\":[],\"flags\":{},\"snapshotEvent\":{\"shouldGenerate\":false,\"kind\":\"\",\"reason\":\"\"}}";
          }
          prompt = "LEVEL TRAVEL HARD LOCK: Core alone selects Level and exit result. Ordinary turns preserve Level and exitStreak. Never narrate escape, a different Level, or a successful exit roll; explicit exit actions are handled locally at 50% and 3 consecutive successes. Describe only current scene; local movement must follow the player action.\n" + prompt;
          JSONObject generated = parseModelJson(
            generateTextForTurn(prompt, Math.max(1, (prompt.length() + 3) / 4)));
          String reply = generated.optString("reply", "").trim();
          if (reply.isEmpty()) reply = "Cao Minh giữ nguyên vị trí và quan sát thêm; chưa có kết quả đủ chắc chắn để thay đổi trạng thái.";

          JSONObject state;
          if (meta) {
            state = new JSONObject(before.toString());
          } else {
            state = new JSONObject(before.toString());
            boolean levelChanged = false;
            boolean transitionAccepted = false;
            if (generated.has("location")) state.put("location", generated.getString("location"));
            state.put("player", sanitizedPlayer(before.optJSONObject("player"), generated.optJSONObject("player")));
            state.put("party", sanitizedParty(before.optJSONArray("party"), generated.optJSONArray("party"), rolls));
            state.put("inventory", sanitizedInventory(before.optJSONArray("inventory"), generated.optJSONArray("inventory"), rolls));
            state.put("flags", sanitizedFlags(before.optJSONObject("flags"), generated.optJSONObject("flags"), rolls, transitionAccepted));
            state.put("_snapshotEvent", sanitizedSnapshotEvent(generated, rolls, transitionAccepted, levelChanged, false));
            state.put("turn", before.optInt("turn", 1) + 1).put("mode", "ai · canon R06");
            JSONObject flags = state.optJSONObject("flags");
            if (flags == null) flags = new JSONObject();
            flags.put("lastRolls", rolls);
            state.put("flags", flags);
          }

          state.put("canonVersion", DRIVE_CANON_VERSION);
          if (meta) state.put("_snapshotEvent", new JSONObject().put("shouldGenerate", false).put("kind", "").put("reason", ""));
          JSONArray log = state.optJSONArray("log");
          if (log == null) log = new JSONArray();
          log.put(new JSONObject().put("role", "player").put("text", action));
          log.put(new JSONObject().put("role", "gm").put("text", reply));
          state.put("log", log);
          // Start an authoritative encounter only on a locked success roll and
          // a profile key accepted by CombatRuntime. GM prose alone is insufficient.
          if (!meta && rollSuccess(rolls, "entityEncounter")) {
            JSONObject combatFlags = state.optJSONObject("flags");
            String entityKey = combatFlags == null ? "" : combatFlags.optString("entityEncounterKey", "");
            if (!entityKey.isEmpty()) {
              JSONObject started = new JSONObject(requireGameCore().startCombatState(state.toString(), entityKey));
              if (started.optBoolean("handled", false)) {
                JSONObject projected = started.optJSONObject("state");
                if (projected != null && projected.optJSONObject("combat") != null) {
                  state.put("combat", projected.getJSONObject("combat"));
                }
              }
            }
          }
          if (!meta) state = new JSONObject(requireGameCore().finishLevelTurn(before.toString(), state.toString(), action));
          emit("backroomTurn", state.toString());
        } catch (Exception e) {
          emit("backroomError", e.getMessage() == null ? "Không thể xử lý lượt." : e.getMessage());
        }
      });
    }


    @JavascriptInterface public String coreStats(String stateJson, String characterId) {
      try {
        return requireGameCore().coreStats(stateJson, characterId);
      } catch (Exception error) {
        return "{}";
      }
    }

    @JavascriptInterface public void coreUpgrade(String stateJson, String characterId, String stat) {
      io.execute(() -> {
        try {
          emit("backroomCoreUpgrade", requireGameCore().processCoreUpgrade(stateJson, characterId, stat));
        } catch (Exception error) {
          String message = error.getMessage() == null ? "Không thể nâng Core." : error.getMessage();
          emit("backroomCoreUpgrade", "{\"handled\":false,\"error\":" + JSONObject.quote(message) + "}");
        }
      });
    }

    @JavascriptInterface public void submitAction(String stateJson, String actionKind, String action) {
      io.execute(() -> {
        try {
          JSONObject result = new JSONObject(requireGameCore().processCombat(stateJson, actionKind, action));
          if (result.optBoolean("handled", false) && result.optJSONObject("state") != null) {
            // LEVEL_TRAVEL_COMBAT_COMMIT: escape from combat is never escape from a Level.
            result.put("state", new JSONObject(requireGameCore().finishLevelTurn(
              stateJson, result.getJSONObject("state").toString(), action)));
            emit("backroomTurn", result.getJSONObject("state").toString());
          } else {
            emit("backroomCombatDiceError", result.optString("error", "Lượt combat chưa được xác nhận."));
          }
        } catch (Exception error) {
          emit("backroomCombatDiceError", error.getMessage() == null ? "Không thể xử lý combat." : error.getMessage());
        }
      });
    }

    @JavascriptInterface public void combatDicePrepare(String stateJson, String action) {
      io.execute(() -> emitCombatDiceResult(() -> requireGameCore().prepareCombatDice(stateJson, action)));
    }

    @JavascriptInterface public void combatDiceHold(String stateJson, int dieIndex, boolean held) {
      io.execute(() -> emitCombatDiceResult(() -> requireGameCore().setCombatDiceHold(stateJson, dieIndex, held)));
    }

    @JavascriptInterface public void combatDiceRoll(String stateJson) {
      io.execute(() -> emitCombatDiceResult(() -> requireGameCore().rerollCombatDice(stateJson)));
    }

    @JavascriptInterface public void combatDiceFinish(String stateJson) {
      io.execute(() -> emitCombatDiceResult(() -> requireGameCore().finishCombatDice(stateJson)));
    }

    @JavascriptInterface public void requestSnapshot(String stateJson) {
      imageIo.execute(() -> requestSnapshotInternal(stateJson));
    }
  }

  private static class SnapshotImage {
    final String data;
    final String mimeType;
    SnapshotImage(String data, String mimeType) {
      this.data = data;
      this.mimeType = mimeType == null || mimeType.isEmpty() ? "image/jpeg" : mimeType;
    }
  }

  private static class HttpError extends Exception {
    final int status;
    HttpError(int status, String message) { super(message); this.status = status; }
  }
}
