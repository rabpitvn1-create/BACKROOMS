package com.rabpit.backroom;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import com.rabpit.backroom.core.GameCoreFacade;
import com.rabpit.backroom.core.StoryCompanionContinuity;
import com.rabpit.backroom.core.EntityEncounterPolicy;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {
  private WebView webView;
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private final ExecutorService imageIo = Executors.newSingleThreadExecutor();
  private final ExecutorService auditIo = Executors.newFixedThreadPool(2);
  private final AtomicInteger latestSnapshotTurn = new AtomicInteger(0);
  private final Object geminiHealthLock = new Object();
  private final long[] geminiCooldownUntil = new long[5];
  private final int[] geminiFailures = new int[5];
  private final long[] geminiLatencyEma = new long[] {1500, 1500, 1500, 1500, 1500};
  private final int[] geminiInFlight = new int[5];
  private int geminiRotation = 0;
  private volatile int lastGeminiWorker = -1;
  private final Object geminiMatrixLock = new Object();
  private final long[] geminiCredentialDisabledUntilMatrix = new long[5];
  private final long[][] geminiLaneCooldownUntilMatrix = new long[3][5];
  private final int[][] geminiLaneFailuresMatrix = new int[3][5];
  private final long[][] geminiLaneLatencyMatrix = new long[3][5];
  private final int[][] geminiLaneInFlightMatrix = new int[3][5];
  private final long[] geminiModelCircuitUntilMatrix = new long[3];
  private final int[] geminiModelTransientMaskMatrix = new int[3];
  private long geminiHostCircuitUntilMatrix = 0L;
  private int geminiTransportMaskMatrix = 0;
  private volatile int lastGeminiModel = -1;
  private GameCoreFacade gameCore;
  private volatile boolean gameCoreUnavailable;
  private static final String GEMINI_MODEL = "gemini-3.6-flash";
  private static final String[] GEMINI_IMAGE_MODELS = {"gemini-3.1-flash-image", "gemini-3.1-flash-lite-image"};
  private static final int[] RETRYABLE = {408, 429, 500, 502, 503, 504};
  private static final int MAX_SNAPSHOT_BASE64 = 1_500_000;
  private static final String DRIVE_CANON_VERSION = "NOVEL-TEXTGAME-2026-08-20-DRIVE-INTEGRATION-R06";
  private static final String DRIVE_CANON = "BACKROOMS DRIVE INTEGRATION — R06 / HARD CANON\n\nPHẠM VI\n- Người chơi chỉ quyết định hành động có chủ ý của Kai Akechi. Game Master mô tả hậu quả, môi trường và phản ứng của thế giới; không tự chọn hộ Kai.\n- Gameplay dùng điểm nhìn gần của Kai. Chỉ khẳng định điều Kai thật sự thấy, nghe, cảm biến, nhớ hoặc suy luận có căn cứ. Không kể xen cảnh Iris/Syvial khi Kai không thể biết.\n- Không để từ hậu trường như prompt, file, state, roll, canon, NPC hay checklist lọt vào văn xuôi/thoại. Kết quả xúc xắc chỉ là ràng buộc nội bộ.\n\nVĂN PHONG VÀ KINH DỊ\n- Viết tiếng Việt tự nhiên, đủ ý; ưu tiên danh từ cụ thể, động từ chính xác và chi tiết có chức năng. Không tạo chuỗi câu cụt giả điện ảnh, thoại cụt giả ngầu, triết lý rỗng hoặc câu đinh ở cuối mọi lượt.\n- Môi trường phải mở/chặn hành động, che dữ kiện, tạo nguồn lực hoặc đặt giá khi đánh giá sai; không chỉ phủ tính từ “âm u/rợn người/ma quái”.\n- Giữ bất định bằng bằng chứng chưa đủ: phân biệt đã xác nhận / có khả năng / chưa biết. Không gọi đúng tên Entity, Exit, vật phẩm hay cơ chế trước khi có đủ căn cứ.\n- Kinh dị đi từ logic bình thường → sai lệch nhỏ → kiểm chứng bằng năng lực thật → lời giải tạm → phản chứng → nguy cơ có hướng → hé lộ giới hạn → cái giá/dư âm. Không cần hoàn tất toàn bộ chuỗi trong một lượt; lượt yên có giá trị.\n- Năng lực của Kai phải giải được lớp đầu của vấn đề rồi mở ra bài toán lớn hơn. Không làm Kai quên thiết bị, bỏ kiểm tra hiển nhiên, bắn thứ chưa xác nhận hoặc bị nerf để tạo căng thẳng.\n- Hội thoại phải đúng người, đúng xưng hô và tình huống. Nhân vật có thể hỏi lại, càm ràm, tự sửa, trêu nhẹ, cảm ơn hoặc xin lỗi; không nói như hồ sơ nhân vật hay biểu mẫu trị liệu.\n\nTHẾ GIỚI\n- Nguồn gốc thật của Backrooms không bao giờ được xác nhận. Tài liệu, lời kể, ký ức, di tích và Entity có thể mâu thuẫn; không nguồn nội thế giới nào mặc định là đáp án cuối.\n- Backrooms là một thực tại liên tục khổng lồ. “Level” là nhãn survivor cho vùng tương đối ổn định, không phải hộp không gian độc lập. Ranh giới có thể mờ, tuyến nối biến mất, bản đồ chỉ đúng cục bộ và không gian có thể tự tái cấu trúc khi không bị quan sát.\n- Thời gian có thể lệch, lặp, mất đoạn hoặc chồng lớp. Ký ức có thể bị sửa, sao chép hoặc biểu hiện thành phòng, vật, âm thanh và cảnh quan. Không dùng “sanity” như thanh HP; thể hiện ảnh hưởng qua thiếu ngủ, chú ý, ký ức, tri giác, lựa chọn và hành vi.\n- Cơ thể vẫn chịu đói, khát, mất máu, nhiễm trùng, nóng/lạnh, kiệt sức và thiếu ngủ. Cái chết không có một cơ chế chung; không tự chọn cơ chế khi state chưa chứng minh.\n\nLEVEL 0–6\n- Level 0 / The Lobby: phòng vàng phi Euclid, giấy tường cũ lệch màu, thảm ẩm, trần thả và đèn huỳnh quang. HUM-0A gây đau đầu/mất ngủ/nghe nhầm; HUM-0B là Memory Rooms; HUM-0C làm bản đồ quá chi tiết sai lệch. Không có Entity cư trú xác nhận; chỉ roaming/incursion cực hiếm. Trong campaign hiện tại, Level 0 dẫn vào epsilon trước; vẫn là giấy tường vàng, thảm ẩm và tiếng đèn chồng lớp. Phải đi hết các sublevel/vùng đặc biệt do Core xác nhận rồi mới tới Level 1. Không dùng kiến trúc bãi đỗ xe để kể việc rời Level 0.\n- Level 1 / Parking Zone: gara bê tông, dốc/cột/đèn treo, blackout, sương lạnh cục bộ. Entity gồm Hound, Clump, Duller, Deathmoth, Hostile Faceling, False Puddle, Paintings. Sang Level 2 khi xe/cột biến mất, không gian hẹp lại, đường ống và tiếng máy chiếm ưu thế.\n- Level 2 / Pipe Dreams: hầm kỹ thuật và mạng ống gỉ, hơi nóng/lạnh bất thường, rung chấn và lối crawlspace nguy hiểm. Entity gồm Clump, Hound, Smiler, Skin-Stealer, Predatory Window, Biological Pipeline. Không uống nước trong ống. Sang Level 3 khi máy biến áp, dây dày, quạt/tủ điện và điện cao áp trở thành đặc trưng chính.\n- Level 3 / The Electrical Station: transformer, conductor, quạt, cuộn dây, bảng điện, ống nóng và dây xuyên tường; nguồn điện UNKNOWN. Entity gồm Deathmoth, Wretch, Skin-Stealer, Cable Mimic. Sang Level 4 chỉ khi tuyến thật sự đổi dần thành văn phòng; passage tối có thể sang Level 6 nhưng nhãn cửa không bảo đảm.\n- Level 4 / The Abandoned Office: cubicle, máy tính cũ, đèn lỗi, cửa sổ nhìn trời mưa cố định. Không có Entity cư trú ổn định; chỉ incursion. Almond Water dễ gặp hơn nhưng vẫn khan hiếm. Sang Level 5 khi kiến trúc đổi dần thành khách sạn cổ và tiếng mưa biến mất.\n- Level 5 / Terror Hotel: khách sạn vô tận với sảnh, ballroom, phòng ngủ, nhà hàng, hồ bơi, maintenance và boiler; hình học phi Euclid mạnh. Entity gồm The Beast of Level 5, Predatory Window, Skin-Stealer, Hound, Hotel Corpse Lure. The Beast là apex hunter thông minh, không phải boss đứng chờ. Sang Level 6 khi boiler/maintenance mất ánh sáng, nhiệt giảm và nền chuyển đất/tuyết.\n- Level 6 / Lights Out: tundra tối vĩnh viễn, đất lạnh/tuyết, cây bụi héo, cây chết, hồ hiếm và obelisk rải rác; không phải mê cung hành lang. Nguy cơ chính là lạnh, bóng tối, đói/khát, mất ngủ, microsleep và lạc đường. Không có Entity cư trú xác nhận; obelisk có chức năng UNKNOWN.\n\nENTITY VÀ TÀI NGUYÊN\n- Không có Entity thân thiện hay trung lập với con người. Hành vi giúp đỡ chỉ có thể là một phần chiến thuật cuối cùng gây hại. Entity không tự tăng máu/kháng/sức mạnh để cân bằng Kai; chúng có thể học, giả điểm yếu, chia cắt nhóm hoặc dùng giọng/xác/ký ức làm bẫy.\n- Jeff the Killer là unique roaming hunter cực hiếm ở Level 0–6, chỉ săn người. Jeff có thể bị thương/giết trong encounter nhưng permadeath bị vô hiệu hóa: chuyển RESPAWNING rồi trở lại ROAMING sau độ trễ biến thiên ở vị trí không xác định; không respawn trước mặt và không dùng để farm.\n- Nước, thức ăn, thuốc, súng và đạn survivor rất khan hiếm. Almond Water hỗ trợ bù nước/tỉnh táo nhẹ, không chữa bách bệnh. Greek Fire cực hiếm. Liquid Pain đỏ, độc, ăn mòn và có thể bị dán nhãn sai. Không để tài nguyên xuất hiện đúng lúc chỉ để cứu player.\n- Rice Automatic / RA100 là SMG .45 ACP có trọng số cao trong pool survivor đã xác định có súng, nhất là nhóm tổ chức; không phải survivor nào cũng có, đạn không vô hạn và nó không chứng minh faction/kỹ năng/độ tin cậy.\n- MadGod Set là UR+ UNIQUE gồm MadGod Armor + MadGod Magnum, tối đa đúng 1 bộ/campaign. Discovery chance tự nhiên vẫn đúng 0,01% (1 trên d10000) ở hành động/vị trí đủ điều kiện, không pity; success chỉ mở vị trí/đường tiếp cận hợp lý, không đặt thẳng vào tay. Mã meta `/retired-command-disabled` bỏ qua discovery roll và đưa đúng một MadGod Armor + một MadGod Magnum vào Inventory của Kai mà không tăng turn/time; nhập lại không tạo duplicate. Quy tắc x50 chỉ tính một lần từ baseline của trang bị nguồn, tuyệt đối không nhân stat hiện tại của Kai và không dùng kết quả đã nhân làm đầu vào lần nữa: WW Magnum DMG 500 → MadGod Magnum DMG 25.000; Blackblood Armor DF 500 → MadGod Armor DF 25.000; Blackblood STR/AGI/HP/ENE/CRIT +100 mỗi stat → MadGod +5.000 mỗi stat. MadGod Magnum dùng đạn quỷ lực hình thành trực tiếp từ Sparda Core, gameplay ammo vô hạn, có single shot + full-auto 600 viên/phút. MadGod Armor kế thừa các chức năng của Blackblood Armor theo baseline hiện hành. Omnivault không được scan/copy MadGod Armor hoặc MadGod Magnum. Sau khi một món MadGod được equip vào đúng slot của Kai, món đó permanent-bound: không unequip, không swap, không drop, không transfer và không store khỏi slot bằng đường khác.\n\nIRIS / SYVIAL\n- Iris và Syvial đã tồn tại từ Prologue, không phải procedural survivor. Khi continuity còn SEPARATED, Kai không biết vị trí/tình trạng của họ; chỉ gặp lại khi roll tương ứng thành công hoặc state đã có tuyến continuity xác nhận.\n- Iris / Argus: nữ bán nhân/bán quỷ, Scout / Target Eliminator dưới quyền Kai; quyết liệt, điềm tĩnh, sắc sảo, can đảm, nữ tính và tốt bụng. Có tình cảm với Kai nhưng Kai chưa đáp lại; xưng “em”, gọi Kai “anh”. ARGUS Terrain Read chỉ dùng quan sát trực tiếp/cảm biến cá nhân/dấu vết; không drone, tablet, nhìn xuyên tường hay toàn tri. Ivory & Ebony là đúng hai súng dùng đạn quỷ lực từ Belial Core vô hạn; không tự thêm cooldown/cạn năng lượng.\n- Syvial: con gái Lucifer, UR+, kiếm sĩ siêu nhiên tốc độ cao; tự nhiên, tự tin, tinh quái và yandere rất nặng với Kai nhưng tỉnh táo, có năng lực xã hội, muốn Kai tự nguyện chọn mình. Không xóa ý chí/ký ức/giam giữ Kai, không tấn công mọi phụ nữ. Xưng “em”, gọi “anh” hoặc “Kai”. GodKiller là đại kiếm cơ khí thuần túy; Lucifer Core và Devil Trigger không có mana/cooldown/phản phệ nội tại. Twenty-Four Severance dừng thời gian và thực hiện đúng 24 nhát.\n\nGAMEPLAY HARD LOCK\n- Chỉ lượt gameplay mới tăng turn và tung xúc xắc. Lệnh meta/status/inventory/party/rules/help/save và cheat meta không tăng turn và không phát sinh encounter, loot, exit hoặc snapshot sự kiện.\n- Xúc xắc do lớp Android tạo là kết quả cuối. AI không được reroll, đổi raw/chance/success, bù trượt bằng encounter tương đương hay tạo kết quả hiếm khi roll thất bại.\n- Survivor: 2% mỗi lượt hợp lệ. Iris reunion: 0,0025% khi đủ điều kiện. Syvial reunion: 0,0025% khi đủ điều kiện. MadGod discovery tự nhiên: 0,01% chỉ khi tìm kiếm hợp lệ và chưa spawned; `/retired-command-disabled` là đường cheat meta riêng, không dùng roll và vẫn giữ giới hạn duy nhất một set/campaign.\n- Hazard / Entity / Loot / Almond Water theo profile Level. Level 0/4/6 chỉ cho Entity dạng roaming/incursion theo roll. Chuyển Level chỉ khi exitProbe success hoặc state đã khóa transitionReady/exitReady.\n- Một success tạo cơ hội hợp lý để người chơi nhận biết/tương tác; không tự đặt vật vào inventory, không teleport nhân vật và không tự quyết hành động của Kai. Ngoại lệ duy nhất cho việc thêm MadGod trực tiếp vào Inventory là người chơi chủ động nhập đúng mã cheat `/retired-command-disabled`.\n\nEND DRIVE CANON R06";
  private static final SecureRandom GAME_RNG = new SecureRandom();
  private static final String KAI_CANON = "CURRENT CHARACTER CANON R08 — HARD OVERRIDE\nRuntime marker: KAI-SRU-R08-RUNTIME-20260830\n\nKAI / STORY & KNOWLEDGE LOCK\n- Kai Akechi / Twilight hiện là Đội trưởng SRU — Special Response Unit / Lực lượng Phản ứng Đặc biệt — thuộc lực lượng Cảnh Sát chống hiện tượng dị thường. Mọi continuity tổ chức cũ của Kai dưới Vatican / Black Blood đã hết hiệu lực.\n- Hồ sơ SRU công khai coi Kai là con người. Sự thật Kai là bán nhân / bán quỷ, con trai của Sparda và Eve, là TUYỆT MẬT / KNOWLEDGE LOCK dành cho người viết. Không nhân vật nào được mặc định biết hoặc tự suy ra bí mật này chỉ từ năng lực, Devil Trigger, đạn quỷ lực, hồi phục hay cơ chế tự sửa chữa.\n- Kai đến từ năm 2299; đây là niên đại xuất thân, không phải năm sinh. Tuổi thật vẫn không rõ.\n\nKAI / KHÓA THỊ GIÁC R08\n- Ngoại hình tương ứng người đàn ông khoảng 30 tuổi: cao, cân đối, thân hình săn chắc, vai rộng vừa phải và thiên về khả năng vận động hơn khối cơ bắp quá đồ sộ.\n- Ảnh tham chiếu hiện hành để lộ đầu và khuôn mặt. Kai có tóc đen dày, hơi dài, rối tự nhiên; mắt xanh lạnh. Không dùng helmet kín đầu, Demon Jaw Mask che mặt, sừng cơ khí, pauldron đầu rồng, áo choàng hay dải vải rách của thiết kế cũ.\n- SRU-MK20 là powered armor / exoskeleton đen–gunmetal, có nhận diện POLICE / SRU / SPECIAL RESPONSE UNIT. Cổ giáp cao bảo vệ cổ và hàm dưới nhưng không che mặt; các mảng giáp và cơ cấu trợ lực tập trung ở thân trên, vai, cánh tay, đầu gối, cẳng chân và bàn chân; vùng hông, đùi và thân dưới giữ vải chiến thuật đen để bảo toàn độ cơ động. Các đường sáng xanh nhỏ chỉ là điểm báo trạng thái hệ thống.\n- Silhouette phải đọc như sĩ quan phản ứng đặc biệt được tăng cường cơ học, không phải hiệp sĩ fantasy.\n\nIRIS / STORY & VISUAL\n- Iris / ARGUS thuộc SRU, là thành viên đội Kai với vai trò Scout / Target Eliminator. Kai giữ quyền chỉ huy tổng thể; Syvial là Đội phó. ARGUS là callsign SRU, không còn là callsign Black Blood.\n- Iris là con gái Belial và một người mẹ loài người. Mẹ cô đã qua đời; danh tính, nghề nghiệp, xuất thân và nguyên nhân qua đời của người mẹ vẫn UNKNOWN. Kai là người đầu tiên của SRU phát hiện Iris trong một sự cố siêu nhiên tại khu dân cư; sau đó SRU xác minh Belial Core, Thousandfold Cognition và chuẩn hóa Project 07.\n- Ngoại hình hiện hành: nữ trẻ khoảng 18 tuổi, tóc bob đen ngắn quanh xương hàm/gáy, mắt nâu hổ phách; giáp cơ khí toàn thân đen–gunmetal với điểm sáng đỏ cam. Không có helmet che mặt, Command Slate, tablet hay drone trong cấu hình chính thức. Ivory & Ebony là cặp súng đặc trưng.\n- Iris có tình cảm với Kai nhưng Kai chưa đáp lại; xưng em và gọi Kai là anh. Với Syvial, quan hệ là bạn bè/đồng đội có cạnh tranh tình cảm, không phải thù địch.\n\nSYVIAL / STORY & VISUAL\n- Syvial là con gái Lucifer và một người mẹ loài người chưa khóa danh tính. Cô đến từ năm 2299; đây là niên đại xuất thân, không phải năm sinh. Tuổi thật chưa rõ.\n- Syvial thuộc SRU và giữ chức Đội phó trong đội Kai Akechi / Twilight. Cô ở cấp UR+ và cùng tầng sức mạnh tổng thể với Kai nhưng không có cùng bộ kỹ năng hay phong cách chiến đấu.\n- Ngoại hình hiện hành: nữ trẻ trưởng thành, cao, chân dài, cân đối; tóc bạc trắng pha tím rất nhạt, cực dài, phần lớn buộc đuôi ngựa cao; mắt đỏ hồng / magenta. Headgear đen–gunmetal quanh thái dương và hai module nhọn là thiết bị cơ khí/cảm biến của Lucifer Armor, không phải sừng sinh học; gương mặt để lộ trong cấu hình tham chiếu.\n- Syvial yandere rất nặng với Kai nhưng vẫn tỉnh táo, thông minh và có năng lực xã hội; cô muốn Kai tự nguyện chọn mình, không mặc định xóa ý chí, giam giữ hay tấn công mọi phụ nữ tiếp cận Kai.\n\nNếu bất kỳ dữ kiện legacy nào bên dưới mâu thuẫn với phần CURRENT CHARACTER CANON R08 này về tổ chức, story, knowledge boundary hoặc ngoại hình, phần R08 ở trên thắng tuyệt đối.\n\nKAI AKECHI / TWILIGHT — OPERATIONAL CODEX\nSOURCE CANON: KAI-AKECHI-TWILIGHT-CODEX-20260829-R06\nSTATUS: HARD CANON / CHARACTER LOCK\n\nMỤC ĐÍCH\nTệp này là bản canon vận hành của Kai dùng cho Game Master trong APK. Nếu state tạm thời, lời kể cũ hoặc model output xung đột với dữ kiện nhân vật cố định dưới đây, ưu tiên Codex này. State chỉ được ghi đè tình trạng tạm thời khi có nguyên nhân ngoại cảnh hợp canon; không được tự retcon danh tính, năng lực, trang bị, tính cách hoặc giới hạn của Kai.\n\n1. ĐỊNH DANH / STORY R08\n- Tên: Kai Akechi (カイ・アケチ). Mật danh: Twilight. Giới tính: nam.\n- Kai là Đội trưởng SRU — Special Response Unit / Lực lượng Phản ứng Đặc biệt — thuộc lực lượng Cảnh Sát chống hiện tượng dị thường. Mọi continuity tổ chức cũ dưới Vatican / Black Blood đã bị loại khỏi canon hiện hành.\n- Hồ sơ SRU công khai phân loại Kai là con người. Sự thật hắn là bán nhân / bán quỷ, con trai Sparda và Eve, là TUYỆT MẬT / KNOWLEDGE LOCK; NPC không được tự biết hoặc suy ra nếu không có nguồn tri thức hợp lệ.\n- Tuổi thật: không rõ. Tuổi biểu kiến: khoảng 30. Niên đại xuất thân: năm 2299, không phải năm sinh.\n- Tôn giáo: Công Giáo như lựa chọn cá nhân, không phải tư cách thành viên của một tổ chức tôn giáo.\n- Phân cấp chiến lực: UR+.\n- Vai trò: chỉ huy hiện trường, xạ thủ chủ lực, chuyên gia xử lý mục tiêu dị thường cấp cao.\n\n2. NGOẠI HÌNH / KHÓA THỊ GIÁC R08\n- Ngoại hình tương ứng một người đàn ông khoảng 30 tuổi: cao, cân đối, thân hình săn chắc, vai rộng vừa phải, thiên về khả năng vận động hơn khối cơ quá đồ sộ.\n- Ảnh tham chiếu chính thức hiện hành để lộ đầu và khuôn mặt. Kai có tóc đen dày, hơi dài, rối tự nhiên; mắt xanh lạnh. Không dùng mũ kín đầu, mặt nạ hàm hoặc sừng cơ khí của hình tham chiếu cũ.\n- SRU-MK20 có nền đen–gunmetal, cấu trúc powered armor / exoskeleton nhiều lớp ở thân trên, vai, cánh tay, đầu gối, cẳng chân và bàn chân. Các vùng hông, đùi và thân dưới chừa vải chiến thuật đen để giữ độ cơ động; các đường sáng xanh nhỏ chỉ là tín hiệu trạng thái.\n- Ngực và vai mang nhận diện POLICE / SRU / SPECIAL RESPONSE UNIT. Cổ giáp dựng cao bảo vệ cổ và hàm dưới nhưng không che khuôn mặt.\n- Silhouette phải đọc như sĩ quan phản ứng đặc biệt tăng cường cơ học: torso bọc giáp, vai có module cứng, cẳng tay cơ khí lớn, đùi dùng dây đai/túi chiến thuật, đầu gối–ống chân–bàn chân có khung trợ lực.\n- HARD VISUAL LOCK R08: không áo choàng, không dải vải rách, không pauldron đầu rồng và không chi tiết fantasy của bộ giáp cũ.\n\n3. TÍNH CÁCH / NGUYÊN TẮC\n- Tự tin nhưng không mù quáng; biết mình mạnh và không cần giả khiêm tốn.\n- Đời thường phóng túng, lười, hay châm chọc, ghét thủ tục và nghi thức vô nghĩa.\n- Khi nguy hiểm thật xuất hiện, chuyển rất nhanh sang quan sát có kỷ luật, quyết định dứt khoát và ưu tiên bảo vệ đồng đội/dân thường.\n- Không tự xem mình là anh hùng; cứu người vì tự chọn làm vậy, không vì cần được ca tụng.\n- Không chủ động làm hại người vô tội.\n- Không bỏ lại đồng đội nếu vẫn còn phương án thực tế để đưa họ về.\n- Không để mệnh lệnh vô trách nhiệm giết người của mình chỉ để giữ thể diện cấp trên.\n- Có thể tha người bị ép buộc, bị điều khiển hoặc đã hết khả năng gây hại; không trao “cơ hội thứ hai” mang tính nghi thức cho kẻ vẫn đang chủ động sát hại dân thường.\n- Có thể tán tỉnh/trêu đùa nhưng không cưỡng ép, không lợi dụng vị thế và tôn trọng lời từ chối.\n- Thích rượu mạnh, phụ nữ đẹp và headshot khi headshot là phương án hiệu quả.\n\n4. PHONG CÁCH GIAO TIẾP\n- Nói tự nhiên, đủ ý. Hài hước/châm chọc dùng để giảm áp lực hoặc phá nhịp đối phương.\n- KHÔNG viết Kai bằng thoại cụt giả ngầu, bỏ đại từ, tối nghĩa, lạnh lùng máy móc hoặc “triết lý” rỗng.\n- Khi nghiêm túc, Kai bỏ phần đùa và nói thẳng điều người nghe cần hiểu hoặc làm; không biến thành người nói từng từ.\n- Với phụ nữ trẻ hơn trong hoàn cảnh thân thiện, thường xưng “anh”, gọi “em”, tên riêng, “tiểu thư” hoặc “quý cô” tùy ngữ cảnh.\n- Với đồng đội, thường gọi tên/họ/biệt danh; khi tác chiến, mệnh lệnh phải rõ và đủ nghĩa.\n- Với Syvial, mặc định xưng “anh”, gọi “Syvial” hoặc “em”. Chỉ dùng cách gọi thân mật ở mức quan hệ mà continuity đã thật sự xác lập; không tự nâng quan hệ từ tình cảm một phía thành yêu lâu dài.\n- Với kẻ thù, có thể chế giễu để phá nhịp nhưng không lạm dụng đe dọa rập khuôn.\n\n5. NĂNG LỰC CHIẾN ĐẤU\n- Thiện xạ UR+: bắn chính xác mà không luôn cần nhìn trực tiếp, tính góc nảy/đường đạn thời gian thực, đánh mục tiêu ngoài tầm nhìn khi có dữ kiện hợp lệ, bắn chặn vật thể bay, vô hiệu hóa vũ khí, xử lý nhiều mục tiêu ở nhiều góc và nhận diện điểm yếu sinh học/cơ học/siêu nhiên cực nhanh.\n- Bản năng xạ thủ: đọc nhịp thở, hướng nhìn, đồng tử, căng cơ, trọng tâm, dấu hiệu rút vũ khí và biến thiên năng lượng để dự đoán hành động kế tiếp.\n- Cận chiến: quyền cước, khóa khớp, dùng súng như vũ khí va đập, Talon Gauntlets và sức mạnh quỷ. Không phụ thuộc vào khoảng cách.\n- Thể chất bán quỷ: sức mạnh, tốc độ, gia tốc, phản xạ, sức bền, chịu tổn thương và hồi phục vượt xa người; kháng độc/bệnh và nhiều môi trường khắc nghiệt; cảm nhận quỷ lực/thực thể siêu nhiên; thị giác tốt trong ánh sáng thấp.\n- KHÔNG cho Kai bắn trượt trừ khi cố ý hoặc tình huống có nguyên nhân đủ mạnh.\n\n6. SPARDA CORE\n- Nằm trong lồng ngực, liên kết trực tiếp với máu, linh hồn và hệ thần kinh.\n- Là nguồn quỷ lực VÔ HẠN và bộ xử lý/đồng bộ chiến đấu trung tâm.\n- Tăng sức mạnh, tốc độ, phản xạ, giác quan, nhận thức và xử lý thông tin.\n- Hỗ trợ tái tạo cơ thể; cấp năng lượng và tự sửa chữa cho Blackblood Armor cùng module; đồng bộ và tự sửa chữa White Wraith Magnum; phân tích cấu trúc/năng lượng/điểm yếu.\n- HARD LOCK: không có pin, thanh năng lượng, cạn quỷ lực, số lần dùng, cooldown, quá tải, giới hạn công suất hoặc suy kiệt do Core. Core có thể vận hành công suất cao vô thời hạn theo canon hiện tại.\n\n7. DEVIL TRIGGER\n- Chế độ giải phóng toàn diện sức mạnh, không phải trạng thái liều mạng hay cơ chế đốt tài nguyên.\n- Tăng mạnh sức mạnh quỷ, tinh thần, thể chất, tốc độ, gia tốc, tốc độ tư duy và phản xạ.\n- Giữ nguyên ý thức, ký ức, tính cách và phán đoán.\n- HARD LOCK: không thời lượng tối đa, không cooldown bắt buộc, không giới hạn số lần, không phản phệ, không berserk, không tha hóa, không suy kiệt vì chính Devil Trigger.\n- Nếu một tình huống ngăn Kai dùng Devil Trigger, nguyên nhân phải là hiệu ứng/điều kiện ngoại cảnh đã được canon hóa, không phải “giới hạn ẩn” tự bịa.\n\n8. GUILTY CROWN OVERRIDE\n- Kai kích hoạt trong Devil Trigger.\n- Thời gian ngoại giới DỪNG HOÀN TOÀN trong toàn bộ quá trình thi hành.\n- Kai phân tích mục tiêu và khai hỏa LIÊN TIẾP ĐÚNG 24 PHÁT đạn quỷ lực.\n- Sau phát thứ 24, Override kết thúc và thời gian ngoại giới tiếp tục.\n- HARD LOCK: luôn đúng 24 phát; không rút thành “một loạt đạn”, không đổi số phát và không thay cơ chế dừng thời gian nếu chưa có retcon trực tiếp.\n\n9. WHITE WRAITH MAGNUM\n- Vũ khí đặc trưng, đồng bộ trực tiếp với Sparda Core.\n- KHÓA THỊ GIÁC R06: White Wraith Magnum có hình thái HANDCANNON revolver cỡ lớn, thân đen–gunmetal, cụm ổ quay cơ khí rõ, nòng cực dài và dày; tỷ lệ đủ lớn để đọc như một khẩu đại pháo cầm tay nhưng Kai vẫn sử dụng như súng ngắn. Khóa này chỉ xác định silhouette thị giác; cơ chế đạn, chế độ khai hỏa và tự sửa chữa vẫn do các quy tắc dưới đây quyết định.\n- CHỈ có MỘT loại đạn: đạn hình thành trực tiếp từ quỷ lực của Kai.\n- Vì Sparda Core cung cấp quỷ lực vô hạn, súng gần như không rơi vào trạng thái hết đạn trong vận hành thông thường; không dùng ammo count vật lý truyền thống.\n- Single Shot: mỗi lần khai hỏa phát ra MỘT viên đạn quỷ lực.\n- Full Auto: bắn liên thanh tối đa xấp xỉ 600 viên/phút.\n- Chuyển mode không đổi loại đạn.\n- Súng tự sửa chữa cấu trúc bằng quỷ lực từ Sparda Core, không cần kho năng lượng riêng hoặc vật tư sửa chữa thông thường.\n- CẤM tự thêm các loại đạn riêng như xuyên giáp, điện từ, phân rã, phong ấn, trừ tà, truy dấu... nếu chưa có canon mới. Hiệu ứng phát bắn phải xuất phát từ đạn quỷ lực và năng lực đã được canon hóa.\n\n10. BLACKBLOOD ARMOR & MODULES\nBlackblood Armor:\n- KHÓA THỊ GIÁC R06: giáp toàn thân đen–gunmetal và thép bạc, cấu trúc phân mảnh/faceted sắc cạnh theo giải phẫu người; helmet kín đầu có crest/sống giáp và sừng/mũi nhọn; một pauldron đầu rồng cơ khí bất đối xứng; chi tiết khớp bronze. Không áo choàng, không dải vải rách và không có ánh sáng xanh mặc định. Đây là powered armor mặc ngoài cơ thể người, không biến Kai thành robot hoặc cyborg.\n- Tăng sức mạnh/tốc độ; hấp thụ và phân tán va chạm; giảm tiếng bước chân; thích nghi môi trường khắc nghiệt; bảo vệ trước độc, nhiệt, lạnh, áp suất; theo dõi mục tiêu/phân tích chiến trường; nối Omnivault Ring.\n- Là phần mở rộng của cơ thể, không phải giáp nặng làm Kai chậm đi.\n- Tự sửa chữa bằng Sparda Core.\n- Đầu rồng ở vai là thiết kế thị giác của giáp; không tự cấp thêm năng lực không được canon hóa.\nDemon Jaw Mask:\n- Ở cấu hình chiến đấu đầy đủ, bao kín và bảo vệ toàn bộ đầu, khuôn mặt, hàm và cổ; visor tối che mắt. Hình thái hiện hành là helmet/faceplate kín đầu chứ không phải mặt nạ chỉ che phần dưới khuôn mặt.\n- Lọc khí độc; tăng thị giác; theo dõi chuyển động; phân tích sinh học/quỷ lực; liên lạc mã hóa; hiển thị dữ liệu chiến trường và hỗ trợ khóa mục tiêu.\nTalon Gauntlets:\n- Móng vuốt cơ khí; tăng lực đấm; hỗ trợ bám/móc; tạo trường điện từ và tác động vật kim loại cự ly ngắn; giật/khóa/nghiền vũ khí khi đủ gần.\nPhantom Greaves:\n- Bứt tốc/gia tốc tức thời; nhảy cao; đổi hướng giữa không trung; chạy tường; giảm tác động tiếp đất; tăng lực đá và khả năng truy đuổi.\n\n11. OMNIVAULT RING / NHẪN VẠN TÀNG\n- Chỉ tác động vật VÔ TRI. Tuyệt đối không lưu trữ, hoàn nguyên, nâng cấp, quét/sao chép, tái tạo hoặc triệu hồi sinh vật sống bằng cách dựng lại cơ thể.\n- Lưu trữ: không giới hạn số lượng, kích thước, khối lượng và quy tắc không gian thông thường đối với vật vô tri hợp lệ.\n- Hoàn nguyên: nhẫn phát luồng ánh sáng khóa mục tiêu; phần hợp lệ trở về trạng thái tốt nhất từng tồn tại của chính nó. Sau mỗi lần Hoàn nguyên thành công, CHÍNH vật phẩm đó cooldown đúng 24 giờ; cooldown tính riêng từng vật, không khóa vật khác.\n- Nâng cấp: có thể cải thiện vật liệu, độ bền, công suất, hiệu suất, độ chính xác, tương thích, năng lượng, chức năng và thuộc tính siêu nhiên trong phạm vi canon.\n- Quét: chỉ vật vô tri BẢN GỐC chưa Marked làm nguồn. Mỗi mẫu chiếm 1 trong đúng 3 slot. Khi đủ 3 slot, mẫu mới ghi đè một mẫu cũ. Bản gốc bị Marked ngay sau lần quét thành công và không thể làm nguồn quét lại.\n- Sao chép: từ mỗi mẫu còn trong 3 slot, tạo số bản sao vô tri không giới hạn. Bản sao do Omnivault tạo KHÔNG thể dùng làm nguồn quét. Khi slot bị ghi đè, bản sao cũ vẫn tồn tại nhưng không thể tạo thêm từ mẫu đã mất.\n- Muốn tạo lại mẫu đã bị ghi đè, Kai phải có một BẢN GỐC KHÁC chưa Marked.\n- Triệu hồi: vật đã lưu hoặc mẫu hợp lệ có thể xuất hiện tức thời ở vị trí được chỉ định.\n- Tái trang bị: khôi phục/thay thế vũ khí, giáp, công cụ và phương tiện gần như tức thời khi chúng là mục tiêu hợp lệ.\n- Lan theo khối liên tục: với Scan và Hoàn nguyên, vùng ánh sáng ban đầu không giới hạn phạm vi cuối. Nếu điểm khóa thuộc cùng một khối vật chất liên tục, hiệu ứng có thể lan qua toàn bộ khối bất kể kích thước/diện tích/khối lượng.\n- Vật phẩm hoàn chỉnh hợp lệ: bộ giáp của Kai, giáp chống đạn và súng cá nhân có thể được hệ thống công nhận là một đơn vị hoàn chỉnh dù gồm nhiều chi tiết.\n- Hệ lớn/lắp ghép: KHÔNG Scan hoặc Hoàn nguyên nguyên khối một hệ lớn đã lắp thành tổng thể như căn nhà thông thường, xe máy, xe tăng hoặc pháo/cannon hoàn chỉnh. Chỉ phần riêng đủ điều kiện hoặc khối vật chất liên tục hợp lệ được xử lý.\n- KHÔNG mặc định 3 slot đang EMPTY nếu state/canon hiện hành chưa xác nhận. Số slot là canon; nội dung slot là state.\n\n12. PHONG CÁCH CHIẾN ĐẤU\n- Tầm xa: độ chính xác + tốc độ xử lý để phá nhịp trước khi đối thủ dựng thế.\n- Tầm trung: Single Shot, Full Auto và di chuyển tốc độ cao để ép mất không gian.\n- Cận chiến: súng như vũ khí va đập, Talon Gauntlets, khóa khớp và sức mạnh bán quỷ.\n- Khi có dân thường/đồng đội cần bảo vệ, bỏ động tác thừa và ưu tiên kết thúc nhanh.\n- Với mục tiêu cấp cao cần kết liễu, có thể dùng Devil Trigger + Guilty Crown Override đúng thủ tục canon.\n\n13. GIỚI HẠN THỰC SỰ\n- Kai KHÔNG có giới hạn nội tại từ huyết thống quỷ, Sparda Core hoặc Devil Trigger theo canon hiện tại.\n- Độ khó hợp lệ đến từ thiếu thông tin, mục tiêu bảo vệ, không gian tác chiến, hậu quả, điều kiện ngoại cảnh và trách nhiệm với người khác; không đến từ việc nerf Kai.\n- Có xu hướng tự nhận phần nguy hiểm nhất để giảm rủi ro cho đồng đội.\n- Có thể giấu thương tổn/kế hoạch nếu tin nói ra sẽ khiến người khác tự đặt mình vào nguy hiểm.\n- Người Kai thật sự quan tâm có thể trở thành đòn bẩy chiến thuật.\n- Các giới hạn Omnivault ở mục 11 là giới hạn thật và phải được giữ chính xác.\n\n14. ACTION LOCKS / CẤM MODEL TỰ BỊA\n- Không bỏ lại đồng đội chỉ vì cứu họ bất tiện.\n- Không chủ động làm hại người vô tội.\n- Không tha thứ dễ dàng cho kẻ phản bội đồng đội để cầu sống nếu đó là lựa chọn có ý thức.\n- Không cho đối thủ đang chủ động tấn công dân thường cơ hội tiếp tục bóp cò.\n- Không tự phát sinh giới hạn năng lượng, số đạn, thời lượng Devil Trigger, cooldown, phản phệ hoặc berserk để tạo kịch tính.\n- Không tự tạo loại đạn White Wraith mới.\n- Không tự biến Omnivault thành kho chứa sinh vật sống hoặc công cụ hồi sinh.\n- Không tự quyết hành động có chủ ý thay Kai; người chơi điều khiển Kai.\n\nCANON CLEANUP R06\n- Mọi dữ kiện thị giác legacy xung đột với khóa R06 — áo choàng/dải vải rách, ánh sáng xanh mặc định hoặc Demon Jaw chỉ che nửa dưới khuôn mặt — không còn thuộc hồ sơ hiện hành.\n- Tên nhân vật và mật danh chuẩn: Kai Akechi / Twilight.\n\nEND OF KAI OPERATIONAL CODEX\n\nKAI AKECHI / TWILIGHT — AUTOMATIC SHOTGUN SKILLS ADDENDUM\nSTATUS: HARD CANON / GAMEPLAY LOCK\n\nCác kỹ năng dưới đây là kỹ năng xạ thủ tự động của Kai trong CombatRuntime sau khi đồng bộ sang SRU-SG Shotgun. Mỗi kỹ năng có roll kích hoạt riêng theo mỗi combat turn hợp lệ. Guilty Crown Override giữ quyền ưu tiên ở turn thứ 3 và các bội số của 3; trong turn Override, bộ kỹ năng tự động này không roll để không chồng cơ chế lên Ultimate 24 shell quỷ lực.\n\nToàn bộ chuyển động, nhịp khai hỏa, độ giật, cự ly và cách đặt chùm đạn phải phù hợp với shotgun. Không mô tả Kai sử dụng SRU-SG như súng lục, magnum hoặc revolver một tay; các pha bắn chủ động dùng tư thế ghì súng hai tay trừ khi một tình huống đặc biệt có lý do hợp canon.\n\n1. THE LAST REQUIEM\n- Kai ghì SRU-SG bằng hai tay và khai hỏa liên tiếp đúng 4 shell quỷ lực theo nhịp giật được kiểm soát, đặt chùm đạn cắt qua các điểm neo vận động ở vùng vai.\n- Tự động kích hoạt với xác suất 38% mỗi combat turn hợp lệ.\n- Gây tổng cộng 170% DMG của SRU-SG / weapon damage hiện tại; 170% là tổng damage của kỹ năng, không nhân thêm lần nữa theo 4 shell.\n- Khi kích hoạt thành công, mục tiêu nhận Bleeding trong 3 combat turn tiếp theo.\n- Mỗi turn Bleeding gây damage bằng 5% Max HP của mục tiêu.\n- Bleeding tái kích hoạt sẽ làm mới thời lượng về 3 turn, không cộng dồn nhiều stack song song.\n\n2. SILENT LULLABY\n- Kai bật lên cao, hạ nòng SRU-SG và khai hỏa liên tiếp đúng 4 shell quỷ lực theo nhịp giật kiểm soát vào cùng một vùng trọng yếu trên ngực mục tiêu.\n- Tự động kích hoạt với xác suất 27% mỗi combat turn hợp lệ.\n- Gây tổng cộng 130% DMG của SRU-SG / weapon damage hiện tại.\n- Gây Stun 1 turn. Mục tiêu bị Stun mất phản ứng / phản công của chính combat turn đó.\n\n3. SALVATION\n- Kai không ném SRU-SG như handgun. Hắn bứt tốc qua góc chết của đối thủ, ghì shotgun bằng hai tay ở cự ly gần rồi khai hỏa nhanh đúng 2 shell quỷ lực.\n- Tự động kích hoạt với xác suất 26% mỗi combat turn hợp lệ.\n- Gây tổng cộng 147% DMG của SRU-SG / weapon damage hiện tại.\n\n4. QUICK STEP\n- Kai liên tục bứt tốc cự ly ngắn để phá khóa mục tiêu và đổi góc né trong khi vẫn giữ SRU-SG ở tư thế sẵn khai hỏa.\n- Tự động kích hoạt với xác suất 35% mỗi combat turn hợp lệ.\n- Khi kích hoạt, Kai nhận +50 điểm phần trăm Evasion đối với đòn phản công thông thường của Entity.\n- Hiệu lực kéo dài 3 combat turn tính cả turn kích hoạt; tái kích hoạt làm mới thời lượng về 3 turn.\n- Quick Step không vô hiệu hóa sát thương diện rộng bắt buộc của Devils And Gold; Silent Lullaby vẫn có thể Stun để chặn phản ứng của turn hiện tại.\n\nHARD LOCK GAMEPLAY\n- Các roll The Last Requiem, Silent Lullaby, Salvation và Quick Step độc lập với nhau trong một turn hợp lệ, vì vậy nhiều kỹ năng có thể cùng kích hoạt nếu từng roll đều thành công.\n- Kỹ năng đã kích hoạt không thực hiện thêm accuracy/evasion roll riêng; proc thành công đồng nghĩa kỹ năng trúng mục tiêu.\n- Không thay đổi Guilty Crown Override: đúng 24 lần khai hỏa SRU-SG bằng shell quỷ lực, mỗi phát 10 HP trong gameplay hiện tại, Accuracy 200%, bỏ qua toàn bộ hiệu ứng né, tự động mỗi 3 combat turn.";

  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    try {
      applyImmersiveFullscreen();
    } catch (Throwable error) {
      Log.w("BackroomStartup", "Immersive fullscreen unavailable; continuing normally.", error);
    }
    try {
      webView = new WebView(this);
      WebSettings settings = webView.getSettings();
      settings.setJavaScriptEnabled(true);
      settings.setDomStorageEnabled(true);
      settings.setAllowFileAccess(true);
    settings.setTextZoom(100);
    settings.setSupportZoom(false);
    settings.setBuiltInZoomControls(false);
    settings.setDisplayZoomControls(false);
    settings.setUseWideViewPort(true);
    settings.setLoadWithOverviewMode(false);
      webView.setWebViewClient(new WebViewClient() {
        @Override public void onPageFinished(WebView view, String url) {
          super.onPageFinished(view, url);
          try {
            installUiEnhancements();
          } catch (Throwable error) {
            Log.e("BackroomStartup", "UI enhancement injection failed; base game remains usable.", error);
          }
        }
      });
      webView.addJavascriptInterface(new GameBridge(), "Android");
      setContentView(webView);
      webView.loadUrl("file:///android_asset/index.html");
    } catch (Throwable error) {
      showStartupFallback(error);
    }
  }

  private void applyImmersiveFullscreen() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      WindowManager.LayoutParams attributes = getWindow().getAttributes();
      attributes.layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
      getWindow().setAttributes(attributes);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      getWindow().setDecorFitsSystemWindows(false);
      WindowInsetsController controller = getWindow().getInsetsController();
      if (controller != null) {
        controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        controller.setSystemBarsBehavior(
            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
      }
    } else {
      getWindow().getDecorView().setSystemUiVisibility(
          View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
              | View.SYSTEM_UI_FLAG_FULLSCREEN
              | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
              | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
  }

  @Override public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) applyImmersiveFullscreen();
  }

  @Override protected void onResume() {
    super.onResume();
    applyImmersiveFullscreen();
  }

  @Override protected void onDestroy() {
    if (gameCore != null) gameCore.close();
    io.shutdownNow();
    imageIo.shutdownNow();
    auditIo.shutdownNow();
    if (webView != null) webView.destroy();
    super.onDestroy();
  }


  private GameCoreFacade gameCoreOrNull() {
    if (gameCore != null) return gameCore;
    if (gameCoreUnavailable) return null;
    synchronized (this) {
      if (gameCore != null) return gameCore;
      if (gameCoreUnavailable) return null;
      try {
        gameCore = GameCoreFacade.create(getApplicationContext(), BuildConfig.DEBUG);
      } catch (Throwable error) {
        gameCoreUnavailable = true;
        Log.e("BackroomStartup", "Game State Core unavailable; keeping app alive.", error);
      }
      return gameCore;
    }
  }

  private GameCoreFacade requireGameCore() throws Exception {
    GameCoreFacade core = gameCoreOrNull();
    if (core == null) {
      throw new Exception("Game State Core không khởi tạo được trên thiết bị này. Ứng dụng vẫn đang chạy; hãy thử lại sau khi khởi động lại app.");
    }
    return core;
  }

  private void showStartupFallback(Throwable error) {
    Log.e("BackroomStartup", "WebView bootstrap failed; showing in-process fallback.", error);
    TextView fallback = new TextView(this);
    fallback.setTextSize(16f);
    fallback.setPadding(36, 48, 36, 48);
    fallback.setText(
        "BACKROOM KHÔNG THỂ KHỞI ĐỘNG GIAO DIỆN WEBVIEW.\n\n"
            + "Ứng dụng vẫn đang chạy thay vì tự thoát.\n"
            + "Lỗi: " + error.getClass().getSimpleName()
            + (error.getMessage() == null ? "" : " — " + error.getMessage()));
    setContentView(fallback);
  }

  private void installUiEnhancements() {
    String script =
      "(function(){" +
      "if(window.__backroomEnhancements)return;window.__backroomEnhancements=true;" +
      "var st=document.createElement('style');" +
      "st.textContent='button{transition:transform 80ms ease,background 120ms ease,border-color 120ms ease;touch-action:manipulation;-webkit-tap-highlight-color:rgba(255,255,255,.12)}button:active:not(:disabled){transform:scale(.965);background:#303840;border-color:#77828c}button:disabled{opacity:.48;cursor:not-allowed}.snapshot{position:relative;overflow:hidden;height:230px}.snapshot .snapshot-bg{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;z-index:1}.snapshot .snapshot-character{position:absolute;right:0;bottom:0;height:97%;width:auto;max-width:55%;object-fit:contain;object-position:right bottom;z-index:2;pointer-events:none;image-rendering:auto}.snapshot-placeholder{display:none}.snapshot .snapshot-equipment-badge{position:absolute;right:8px;top:8px;z-index:4;padding:6px 8px;border:1px solid rgba(218,180,88,.62);border-radius:8px;background:rgba(7,9,11,.78);color:#f2dfad;font-size:10px;pointer-events:none}.snapshot .snapshot-equipment-badge b{display:block}.snapshot-placeholder b{font-size:12px;letter-spacing:.16em}.snapshot-placeholder small{color:#56616a}.message.pending{opacity:.72}.message.pending .text{color:#aeb7be}';" +
      "document.head.appendChild(st);" +
      "function scrollBottom(){var l=document.getElementById('log');if(l)requestAnimationFrame(function(){l.scrollTop=l.scrollHeight;});}" +
      "function equippedItem(s){try{var e=state&&state.equipment||{};var direct=e[s];if(direct)return typeof direct==='string'?{id:direct,name:direct}:direct;var members=state&&state.partyDetails&&state.partyDetails.members;if(Array.isArray(members)){var kai=members.find(function(m){return String(m&&m.id)==='kai'});var value=kai&&kai.equipment&&kai.equipment[s];if(value)return typeof value==='string'?{id:value,name:value}:value}return null}catch(e){return null}}function kaiCombatActive(){var c=state&&state.combat;return !!(c&&c.active===true)}function kaiOverlaySource(){if(kaiCombatActive())return 'SRU_AIM.png';return 'SRU_IDLE.png'}function appendEquipmentBadge(b){if(!madGodEquipped('armor')&&!madGodEquipped('weapon'))return;var d=document.createElement('div');d.className='snapshot-equipment-badge';var a=equippedItem('armor'),w=equippedItem('weapon');d.textContent='MadGod Set';b.appendChild(d)}function visualSceneKey(){var level=state&&state.level;var l=level&&((level.id!==undefined&&level.id!==null)?level.id:level.number);var where=String(state&&state.location||'').trim().toLowerCase();var area=String(state&&state.flags&&state.flags.visualAreaKey||'').trim().toLowerCase();return String(l==null?'?':l)+'|'+where+'|'+area}function cachedSnapshot(){try{var r=JSON.parse(localStorage.getItem('backroom-apk-snapshot')||'null');return r&&r.dataUri&&r.sceneKey===visualSceneKey()?r:null;}catch(e){return null;}}function renderSnapshot(){var box=document.getElementById('snapshot');if(!box)return;box.textContent='';var r=cachedSnapshot();var exploration=state&&state.flags&&state.flags.exploration;var structuredLevel=state&&state.level&&((state.level.id!==undefined&&state.level.id!==null)?state.level.id:state.level.number);var where=String(state&&state.location||'')+' '+String(state&&state.title||'');var lm=where.match(/Level +([^ ]+)/i);var parsedLevel=lm?String(lm[1]).trim():'';var areaId=(exploration&&exploration.areaId!==undefined&&exploration.areaId!==null&&String(exploration.areaId).trim())?String(exploration.areaId).trim():((structuredLevel!==undefined&&structuredLevel!==null&&String(structuredLevel).trim())?String(structuredLevel).trim():(parsedLevel||'0'));var pools={'0':['file:///android_asset/level_snapshots/level_0_1.webp','file:///android_asset/level_snapshots/level_0_2.webp','file:///android_asset/level_snapshots/level_0_3.webp','file:///android_asset/level_snapshots/level_0_4.webp'],'0.01':['file:///android_asset/level_snapshots/area_02_0_01_trusted_01.webp','file:///android_asset/level_snapshots/area_02_0_01_trusted_02.webp','file:///android_asset/level_snapshots/area_02_0_01_trusted_03.webp','file:///android_asset/level_snapshots/area_02_0_01_trusted_04.webp'],'0.1':['file:///android_asset/level_snapshots/area_03_0_1_trusted_01.webp','file:///android_asset/level_snapshots/area_03_0_1_trusted_02.webp','file:///android_asset/level_snapshots/area_03_0_1_trusted_03.webp','file:///android_asset/level_snapshots/area_03_0_1_trusted_04.webp'],'0.11':['file:///android_asset/level_snapshots/area_04_0_11_trusted_01.webp','file:///android_asset/level_snapshots/area_04_0_11_trusted_02.webp','file:///android_asset/level_snapshots/area_04_0_11_trusted_03.webp','file:///android_asset/level_snapshots/area_04_0_11_trusted_04.webp'],'0.22':['file:///android_asset/level_snapshots/area_05_0_22_trusted_01.webp','file:///android_asset/level_snapshots/area_05_0_22_trusted_02.webp','file:///android_asset/level_snapshots/area_05_0_22_trusted_03.webp','file:///android_asset/level_snapshots/area_05_0_22_trusted_04.webp'],'0.23':['file:///android_asset/level_snapshots/area_06_0_23_trusted_01.webp','file:///android_asset/level_snapshots/area_06_0_23_trusted_02.webp','file:///android_asset/level_snapshots/area_06_0_23_trusted_03.webp','file:///android_asset/level_snapshots/area_06_0_23_trusted_04.webp'],'0.41':['file:///android_asset/level_snapshots/area_07_0_41_trusted_01.webp','file:///android_asset/level_snapshots/area_07_0_41_trusted_02.webp','file:///android_asset/level_snapshots/area_07_0_41_trusted_03.webp','file:///android_asset/level_snapshots/area_07_0_41_trusted_04.webp'],'0.5':['file:///android_asset/level_snapshots/area_08_0_5_trusted_01.webp','file:///android_asset/level_snapshots/area_08_0_5_trusted_02.webp','file:///android_asset/level_snapshots/area_08_0_5_trusted_03.webp','file:///android_asset/level_snapshots/area_08_0_5_trusted_04.webp'],'0.66':['file:///android_asset/level_snapshots/area_09_0_66_trusted_01.webp','file:///android_asset/level_snapshots/area_09_0_66_trusted_02.webp','file:///android_asset/level_snapshots/area_09_0_66_trusted_03.webp','file:///android_asset/level_snapshots/area_09_0_66_trusted_04.webp'],'0.7':['file:///android_asset/level_snapshots/area_10_0_7_trusted_01.webp','file:///android_asset/level_snapshots/area_10_0_7_trusted_02.webp','file:///android_asset/level_snapshots/area_10_0_7_trusted_03.webp','file:///android_asset/level_snapshots/area_10_0_7_trusted_04.webp'],'0.8':['file:///android_asset/level_snapshots/area_11_0_8_trusted_01.webp','file:///android_asset/level_snapshots/area_11_0_8_trusted_02.webp','file:///android_asset/level_snapshots/area_11_0_8_trusted_03.webp','file:///android_asset/level_snapshots/area_11_0_8_trusted_04.webp'],'0.99':['file:///android_asset/level_snapshots/area_12_0_99_trusted_01.webp','file:///android_asset/level_snapshots/area_12_0_99_trusted_02.webp','file:///android_asset/level_snapshots/area_12_0_99_trusted_03.webp','file:///android_asset/level_snapshots/area_12_0_99_trusted_04.webp'],'1':['file:///android_asset/level_snapshots/area_16_1_trusted_01.webp','file:///android_asset/level_snapshots/area_16_1_trusted_02.webp','file:///android_asset/level_snapshots/area_16_1_trusted_03.webp','file:///android_asset/level_snapshots/area_16_1_trusted_04.webp'],'1.01':['file:///android_asset/level_snapshots/area_17_1_01_trusted_01.webp','file:///android_asset/level_snapshots/area_17_1_01_trusted_02.webp','file:///android_asset/level_snapshots/area_17_1_01_trusted_03.webp','file:///android_asset/level_snapshots/area_17_1_01_trusted_04.webp'],'1.1':['file:///android_asset/level_snapshots/area_18_1_1_trusted_01.webp','file:///android_asset/level_snapshots/area_18_1_1_trusted_02.webp','file:///android_asset/level_snapshots/area_18_1_1_trusted_03.webp','file:///android_asset/level_snapshots/area_18_1_1_trusted_04.webp'],'1.5':['file:///android_asset/level_snapshots/area_19_1_5_trusted_01.webp','file:///android_asset/level_snapshots/area_19_1_5_trusted_02.webp','file:///android_asset/level_snapshots/area_19_1_5_trusted_03.webp','file:///android_asset/level_snapshots/area_19_1_5_trusted_04.webp'],'1.618033988749894...':['file:///android_asset/level_snapshots/area_20_1_618033988749894_trusted_01.webp','file:///android_asset/level_snapshots/area_20_1_618033988749894_trusted_02.webp','file:///android_asset/level_snapshots/area_20_1_618033988749894_trusted_03.webp','file:///android_asset/level_snapshots/area_20_1_618033988749894_trusted_04.webp'],'2':['file:///android_asset/level_snapshots/area_21_2_trusted_01.webp','file:///android_asset/level_snapshots/area_21_2_trusted_02.webp','file:///android_asset/level_snapshots/area_21_2_trusted_03.webp','file:///android_asset/level_snapshots/area_21_2_trusted_04.webp'],'2.1':['file:///android_asset/level_snapshots/area_22_2_1_trusted_01.webp','file:///android_asset/level_snapshots/area_22_2_1_trusted_02.webp','file:///android_asset/level_snapshots/area_22_2_1_trusted_03.webp','file:///android_asset/level_snapshots/area_22_2_1_trusted_04.webp'],'2.2':['file:///android_asset/level_snapshots/area_24_2_2_trusted_01.webp','file:///android_asset/level_snapshots/area_24_2_2_trusted_02.webp','file:///android_asset/level_snapshots/area_24_2_2_trusted_03.webp','file:///android_asset/level_snapshots/area_24_2_2_trusted_04.webp'],'2.71828182845...':['file:///android_asset/level_snapshots/area_23_2_71828182845_trusted_01.webp','file:///android_asset/level_snapshots/area_23_2_71828182845_trusted_02.webp','file:///android_asset/level_snapshots/area_23_2_71828182845_trusted_03.webp','file:///android_asset/level_snapshots/area_23_2_71828182845_trusted_04.webp'],'3':['file:///android_asset/level_snapshots/area_25_3_trusted_01.webp','file:///android_asset/level_snapshots/area_25_3_trusted_02.webp','file:///android_asset/level_snapshots/area_25_3_trusted_03.webp','file:///android_asset/level_snapshots/area_25_3_trusted_04.webp'],'3.14159265358...':['file:///android_asset/level_snapshots/area_26_3_14159265358_trusted_01.webp','file:///android_asset/level_snapshots/area_26_3_14159265358_trusted_02.webp','file:///android_asset/level_snapshots/area_26_3_14159265358_trusted_03.webp','file:///android_asset/level_snapshots/area_26_3_14159265358_trusted_04.webp'],'3.53':['file:///android_asset/level_snapshots/area_27_3_53_trusted_01.webp','file:///android_asset/level_snapshots/area_27_3_53_trusted_02.webp','file:///android_asset/level_snapshots/area_27_3_53_trusted_03.webp','file:///android_asset/level_snapshots/area_27_3_53_trusted_04.webp'],'4':['file:///android_asset/level_snapshots/area_28_4_trusted_01.webp','file:///android_asset/level_snapshots/area_28_4_trusted_02.webp','file:///android_asset/level_snapshots/area_28_4_trusted_03.webp','file:///android_asset/level_snapshots/area_28_4_trusted_04.webp'],'4.11':['file:///android_asset/level_snapshots/area_31_4_11_trusted_01.webp','file:///android_asset/level_snapshots/area_31_4_11_trusted_02.webp','file:///android_asset/level_snapshots/area_31_4_11_trusted_03.webp','file:///android_asset/level_snapshots/area_31_4_11_trusted_04.webp'],'4.3':['file:///android_asset/level_snapshots/area_29_4_3_trusted_01.webp','file:///android_asset/level_snapshots/area_29_4_3_trusted_02.webp','file:///android_asset/level_snapshots/area_29_4_3_trusted_03.webp','file:///android_asset/level_snapshots/area_29_4_3_trusted_04.webp'],'4.4':['file:///android_asset/level_snapshots/area_30_4_4_trusted_01.webp','file:///android_asset/level_snapshots/area_30_4_4_trusted_02.webp','file:///android_asset/level_snapshots/area_30_4_4_trusted_03.webp','file:///android_asset/level_snapshots/area_30_4_4_trusted_04.webp'],'5':['file:///android_asset/level_snapshots/area_32_5_trusted_01.webp','file:///android_asset/level_snapshots/area_32_5_trusted_02.webp','file:///android_asset/level_snapshots/area_32_5_trusted_03.webp','file:///android_asset/level_snapshots/area_32_5_trusted_04.webp'],'5.1':['file:///android_asset/level_snapshots/area_33_5_1_trusted_01.webp','file:///android_asset/level_snapshots/area_33_5_1_trusted_02.webp','file:///android_asset/level_snapshots/area_33_5_1_trusted_03.webp','file:///android_asset/level_snapshots/area_33_5_1_trusted_04.webp'],'5.2':['file:///android_asset/level_snapshots/area_34_5_2_trusted_01.webp','file:///android_asset/level_snapshots/area_34_5_2_trusted_02.webp','file:///android_asset/level_snapshots/area_34_5_2_trusted_03.webp','file:///android_asset/level_snapshots/area_34_5_2_trusted_04.webp'],'5.55':['file:///android_asset/level_snapshots/area_35_5_55_trusted_01.webp','file:///android_asset/level_snapshots/area_35_5_55_trusted_02.webp','file:///android_asset/level_snapshots/area_35_5_55_trusted_03.webp','file:///android_asset/level_snapshots/area_35_5_55_trusted_04.webp'],'6':['file:///android_asset/level_snapshots/area_36_6_trusted_01.webp','file:///android_asset/level_snapshots/area_36_6_trusted_02.webp','file:///android_asset/level_snapshots/area_36_6_trusted_03.webp','file:///android_asset/level_snapshots/area_36_6_trusted_04.webp'],'6.1':['file:///android_asset/level_snapshots/area_37_6_1_trusted_01.webp','file:///android_asset/level_snapshots/area_37_6_1_trusted_02.webp','file:///android_asset/level_snapshots/area_37_6_1_trusted_03.webp','file:///android_asset/level_snapshots/area_37_6_1_trusted_04.webp'],'6.2':['file:///android_asset/level_snapshots/area_38_6_2_trusted_01.webp','file:///android_asset/level_snapshots/area_38_6_2_trusted_02.webp','file:///android_asset/level_snapshots/area_38_6_2_trusted_03.webp','file:///android_asset/level_snapshots/area_38_6_2_trusted_04.webp'],'6.28318530718...':['file:///android_asset/level_snapshots/area_39_6_28318530718_trusted_01.webp','file:///android_asset/level_snapshots/area_39_6_28318530718_trusted_02.webp','file:///android_asset/level_snapshots/area_39_6_28318530718_trusted_03.webp','file:///android_asset/level_snapshots/area_39_6_28318530718_trusted_04.webp'],'6.5':['file:///android_asset/level_snapshots/area_40_6_5_trusted_01.webp','file:///android_asset/level_snapshots/area_40_6_5_trusted_02.webp','file:///android_asset/level_snapshots/area_40_6_5_trusted_03.webp','file:///android_asset/level_snapshots/area_40_6_5_trusted_04.webp'],'6.66':['file:///android_asset/level_snapshots/area_41_6_66_trusted_01.webp','file:///android_asset/level_snapshots/area_41_6_66_trusted_02.webp','file:///android_asset/level_snapshots/area_41_6_66_trusted_03.webp','file:///android_asset/level_snapshots/area_41_6_66_trusted_04.webp'],'6.99':['file:///android_asset/level_snapshots/area_42_6_99_trusted_01.webp','file:///android_asset/level_snapshots/area_42_6_99_trusted_02.webp','file:///android_asset/level_snapshots/area_42_6_99_trusted_03.webp','file:///android_asset/level_snapshots/area_42_6_99_trusted_04.webp'],'Dullness':['file:///android_asset/level_snapshots/area_14_dullness_trusted_01.webp','file:///android_asset/level_snapshots/area_14_dullness_trusted_02.webp','file:///android_asset/level_snapshots/area_14_dullness_trusted_03.webp','file:///android_asset/level_snapshots/area_14_dullness_trusted_04.webp'],'LS-2':['file:///android_asset/level_snapshots/area_13_ls_2_trusted_01.webp','file:///android_asset/level_snapshots/area_13_ls_2_trusted_02.webp','file:///android_asset/level_snapshots/area_13_ls_2_trusted_03.webp','file:///android_asset/level_snapshots/area_13_ls_2_trusted_04.webp'],'Red Rooms':['file:///android_asset/level_snapshots/area_15_red_rooms_trusted_01.webp','file:///android_asset/level_snapshots/area_15_red_rooms_trusted_02.webp','file:///android_asset/level_snapshots/area_15_red_rooms_trusted_03.webp','file:///android_asset/level_snapshots/area_15_red_rooms_trusted_04.webp'],'epsilon':['file:///android_asset/level_snapshots/level_epsilon_1.webp','file:///android_asset/level_snapshots/level_epsilon_2.webp','file:///android_asset/level_snapshots/level_epsilon_3.webp','file:///android_asset/level_snapshots/level_epsilon_4.webp']};var parentByArea={'0.01':'0','0.1':'0','0.11':'0','0.22':'0','0.23':'0','0.41':'0','0.5':'0','0.66':'0','0.7':'0','0.8':'0','0.99':'0','1.01':'1','1.1':'1','1.5':'1','1.618033988749894...':'1','2.1':'2','2.2':'2','2.71828182845...':'2','3.14159265358...':'3','3.53':'3','4.11':'4','4.3':'4','4.4':'4','5.1':'5','5.2':'5','5.55':'5','6.1':'6','6.2':'6','6.28318530718...':'6','6.5':'6','6.66':'6','6.99':'6','Dullness':'0','LS-2':'0','Red Rooms':'0','epsilon':'0'};var genericFallbackKey='0';var genericFallbackRef='file:///android_asset/level_snapshots/level_0_1.webp';function resolveSnapshotPool(id){var requested=String(id||'');var cursor=requested;var seen={};while(cursor&&!seen[cursor]){seen[cursor]=true;var own=pools[cursor];if(own&&own.length)return {key:cursor,choices:own,dedicated:cursor===requested};cursor=(parentByArea[cursor]!==undefined&&parentByArea[cursor]!==null)?String(parentByArea[cursor]):'';}var fallback=pools[genericFallbackKey];return {key:genericFallbackKey,choices:(fallback&&fallback.length)?fallback:[genericFallbackRef],dedicated:false};}var resolvedSnapshot=resolveSnapshotPool(areaId);var choices=resolvedSnapshot.choices;var dedicated=resolvedSnapshot.dedicated;var resolvedParent=resolvedSnapshot.key;var bucket=Math.floor(Date.now()/300000);var areaSeed=0;for(var ai=0;ai<areaId.length;ai++)areaSeed=(areaSeed*33+areaId.charCodeAt(ai))%2147483647;var seed=(bucket*17+areaSeed*31+Number(state&&state.turn||0)*7);var pick=choices[Math.abs(seed)%choices.length]||genericFallbackRef;var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=pick;bg.alt='Area '+areaId+' — Backrooms Wiki Fandom snapshot';bg.setAttribute('data-fandom-area',areaId);bg.setAttribute('data-fandom-parent-level',String(resolvedParent));bg.setAttribute('data-fandom-dedicated',dedicated?'true':'false');bg.onerror=function(){this.onerror=null;this.src=genericFallbackRef;};box.appendChild(bg);var kai=document.createElement('img');kai.className='snapshot-character';kai.src=kaiOverlaySource();kai.onerror=function(){this.onerror=null;this.src='Kai_new_overlay.png'};kai.alt='Kai Akechi';box.appendChild(kai);if(!r){var p=document.createElement('div');p.className='snapshot-placeholder';p.innerHTML='<b>SNAPSHOT</b><small>Chưa có ảnh của turn hiện tại.</small>';box.appendChild(p);}}" +
      "var __baseRenderSnapshot=renderSnapshot,__entityOverlay={key:'',url:'',revision:0,anchor:'left-bottom',maxHeight:.97,loading:''};" +
      "var __entityKeys=['hound','clump','duller','deathmoth','hostile_faceling','false_puddle','paintings','smiler','skin-stealer','predatory_window','biological_pipeline','wretch','cable_mimic','the_beast_of_level_5','hotel_corpse_lure','jeff_the_killer','jane_the_killer','slenderman','diep_minh','monster_x','john_doe','scp_173','violet_warden','kai_the_devil_within'];" +
      "function normalizeEntityKey(v){if(typeof v!=='string')return '';var k=v.trim().toLowerCase();return __entityKeys.indexOf(k)>=0?k:'';}" +
      "function activeEntityKey(){var c=state&&state.combat;if(!c||c.active!==true)return '';return normalizeEntityKey(c.entityKey);}" +
      "function requestEntityOverlay(key){if(!key||__entityOverlay.loading===key)return;if(!window.Android||typeof Android.requestEntityOverlay!=='function')return;__entityOverlay.loading=key;Android.requestEntityOverlay(key);}" +
      "function appendEntityOverlay(){var box=document.getElementById('snapshot');if(!box)return;box.style.position='relative';box.style.overflow='hidden';box.querySelectorAll('.snapshot-entity,.snapshot-entity-shadow').forEach(function(node){node.remove()});var key=activeEntityKey();if(!key){__entityOverlay={key:'',url:'',revision:0,anchor:'left-bottom',maxHeight:.97,loading:''};return;}if(__entityOverlay.key!==key){__entityOverlay.url='';__entityOverlay.key=key;}if(!__entityOverlay.url){requestEntityOverlay(key);return;}var shadow=document.createElement('span');shadow.className='snapshot-entity-shadow';shadow.setAttribute('aria-hidden','true');shadow.style.cssText='position:absolute;left:2%;bottom:1%;width:48%;height:10%;border-radius:50%;background:radial-gradient(ellipse,rgba(0,0,0,.78) 0%,rgba(0,0,0,.48) 48%,rgba(0,0,0,0) 78%);filter:blur(5px);pointer-events:none;z-index:1';box.appendChild(shadow);var img=document.createElement('img');img.className='snapshot-entity';img.src=__entityOverlay.url;img.alt=key;img.style.position='absolute';img.style.bottom='0';img.style.width='auto';img.style.maxWidth='55%';img.style.height=Math.round(Math.max(.2,Math.min(1,Number(__entityOverlay.maxHeight)||.97))*100)+'%';img.style.objectFit='contain';img.style.pointerEvents='none';img.style.zIndex='2';img.style.left='0';img.style.objectPosition='left bottom';box.appendChild(img);}" +
      "renderSnapshot=function(){__baseRenderSnapshot();appendEntityOverlay();};" +
      "window.backroomEntityOverlay=function(payload){try{var r=JSON.parse(payload);var key=normalizeEntityKey(r.entityKey);if(!key)return;__entityOverlay.loading='';if(key!==activeEntityKey())return;__entityOverlay.key=key;__entityOverlay.url=String(r.url||'');__entityOverlay.revision=Number(r.revision||1);__entityOverlay.anchor=String(r.anchor||'left-bottom');__entityOverlay.maxHeight=Number(r.maxHeight||.97);renderSnapshot();}catch(e){__entityOverlay.loading='';}};" +
      "window.backroomEntityOverlayError=function(payload){__entityOverlay.loading='';};" +
      "var snapshotBusy=false;function requestSnapshot(){var s=document.getElementById('status');if(s)s.textContent='Snapshot chưa được cấu hình.';}" +
      "window.requestSnapshot=requestSnapshot;" +
      "window.__backroomProvider='Gemini';window.backroomProvider=function(provider){window.__backroomProvider=provider||'AI';var s=document.getElementById('status');if(s)s.textContent=window.__backroomProvider+' đang xử lý lượt…';var p=document.querySelector('[data-pending=\\\"1\\\"]:not(.player) .text');if(p)p.textContent=window.__backroomProvider+' đang xử lý lượt…';};" +
      "var oldRender=window.render;if(typeof oldRender==='function'){window.render=function(){oldRender();renderSnapshot();scrollBottom();};}" +
      "var actions=document.querySelector('.actions');if(actions&&!document.getElementById('snapshotButton')){var b=document.createElement('button');b.id='snapshotButton';b.type='button';b.textContent='Snapshot chưa cấu hình';b.disabled=true;var wide=actions.querySelector('.wide');if(wide)actions.insertBefore(b,wide);else actions.appendChild(b);}" +
      "var oldTurn=window.backroomTurn;window.backroomTurn=function(json){if(typeof oldTurn==='function')oldTurn(json);document.querySelectorAll('[data-pending=\"1\"]').forEach(function(n){n.remove();});var s=document.getElementById('status');var ev=state&&state._snapshotEvent;var allowed={LEVEL_CHANGE:1,SPECIAL_REGION:1,ENTITY_CONFIRMED:1,PERSON_ENCOUNTER:1,MAJOR_VISUAL_EVENT:1};var should=!!(ev&&ev.shouldGenerate===true&&allowed[String(ev.kind||'').toUpperCase()]);if(should){if(s)s.textContent='Turn '+state.turn+' có sự kiện hình ảnh đặc biệt. Đang tạo snapshot…';}else{if(s)s.textContent='Turn '+state.turn+' đã xử lý bằng '+(window.__backroomProvider||'AI')+'. Snapshot cũ được giữ nguyên.';}renderSnapshot();scrollBottom();if(should)requestSnapshot();};" +
      "var oldError=window.backroomError;window.backroomError=function(message){document.querySelectorAll('[data-pending=\"1\"]').forEach(function(n){n.remove();});if(typeof oldError==='function')oldError(message);var s=document.getElementById('status');if(s)s.textContent=String(message||'').indexOf('Lỗi mạng/DNS:')===0?message:'Lỗi '+(window.__backroomProvider||'AI')+': '+message;scrollBottom();};" +
      "window.backroomSnapshotProvider=function(provider){var s=document.getElementById(\'status\');if(s)s.textContent=(provider||\'AI\')+\' đang tạo snapshot…\';};" +
      "window.backroomSnapshot=function(payload){snapshotBusy=false;try{var r=JSON.parse(payload);if(!state||Number(r.turn)!==Number(state.turn))return;if(!r.dataUri)return;localStorage.setItem('backroom-apk-snapshot',JSON.stringify({turn:r.turn,sceneKey:visualSceneKey(),model:r.model||'AI',dataUri:r.dataUri}));renderSnapshot();var s=document.getElementById('status');if(s)s.textContent='Snapshot Turn '+state.turn+' đã tạo bằng '+(r.model||'AI')+'.';}catch(e){var s=document.getElementById('status');if(s)s.textContent='Snapshot trả về không hợp lệ.';}};" +
      "window.backroomSnapshotError=function(payload){snapshotBusy=false;try{var r=JSON.parse(payload);if(state&&Number(r.turn)!==Number(state.turn))return;var s=document.getElementById('status');if(s)s.textContent='Snapshot lỗi: '+(r.message||'Không thể tạo ảnh.');}catch(e){var s=document.getElementById('status');if(s)s.textContent='Snapshot lỗi.';}};" +
      "var f=document.getElementById('form');if(f){f.addEventListener('submit',function(){var a=document.getElementById('action');var text=a?a.value.trim():'';if(!text)return;var l=document.getElementById('log');if(!l)return;var player=document.createElement('article');player.className='message player pending';player.setAttribute('data-pending','1');player.innerHTML='<div class=\"role\">BẠN</div><div class=\"text\"></div>';player.querySelector('.text').textContent=text;l.appendChild(player);var gm=document.createElement('article');gm.className='message pending';gm.setAttribute('data-pending','1');gm.innerHTML='<div class=\"role\">GAME MASTER</div><div class=\"text\">Gemini đang xử lý lượt…</div>';l.appendChild(gm);scrollBottom();},true);}" +
      "renderSnapshot();scrollBottom();" +
      "})();";
    webView.evaluateJavascript(script, null);
  }

  private String lower(String value) {
    return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
  }

  private String normalizedEntityKey(String raw) throws Exception {
    String key = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
    switch (key) {
      case "hound": case "clump": case "duller": case "deathmoth":
      case "hostile_faceling": case "false_puddle": case "paintings": case "smiler":
      case "skin-stealer": case "predatory_window": case "biological_pipeline": case "wretch":
      case "cable_mimic": case "the_beast_of_level_5": case "hotel_corpse_lure":
      case "jeff_the_killer": case "jane_the_killer": case "slenderman": case "diep_minh": case "monster_x": case "john_doe": case "scp_173": case "violet_warden": case "kai_the_devil_within":
        return key;
      default:
        throw new Exception("Entity key khong hop le: " + key);
    }
  }

  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {
    if (candidateState == null || rolls == null) return;
    String entityKey = rolls.optString("roamingEntityKey", "").trim();
    JSONObject boss = rolls.optJSONObject("diepMinhEncounter");
    JSONObject monsterX = rolls.optJSONObject("monsterXEncounter");
    JSONObject johnDoe = rolls.optJSONObject("johnDoeEncounter");
    JSONObject scp173 = rolls.optJSONObject("scp173Encounter");
    JSONObject violetWarden = rolls.optJSONObject("violetWardenEncounter");
    JSONObject kaiDevilWithin = rolls.optJSONObject("kaiDevilWithinEncounter");
    if (boss != null && boss.optBoolean("success", false)) {
      entityKey = "diep_minh";
    } else if (monsterX != null && monsterX.optBoolean("success", false)) {
      entityKey = "monster_x";
    } else if (johnDoe != null && johnDoe.optBoolean("success", false)) {
      entityKey = "john_doe";
    } else if (scp173 != null && scp173.optBoolean("success", false)) {
      entityKey = "scp_173";
    } else if (violetWarden != null && violetWarden.optBoolean("success", false)) {
      entityKey = "violet_warden";
    } else if (kaiDevilWithin != null && kaiDevilWithin.optBoolean("success", false)) {
      entityKey = "kai_the_devil_within";
    } else {
      JSONObject normal = rolls.optJSONObject("entityEncounter");
      if (normal == null || !normal.optBoolean("success", false)) return;
      if (entityKey.isEmpty()) return;
    }
    JSONObject flags = candidateState.optJSONObject("flags");
    if (flags == null) {
      flags = new JSONObject();
      candidateState.put("flags", flags);
    }
    String canonicalKey = normalizedEntityKey(entityKey);
    flags.put("entityEncounterKey", canonicalKey);
    requireGameCore().startCombatState(candidateState.toString(), canonicalKey);
  }

  private JSONObject resolveEntityOverlay(String rawEntityKey) throws Exception {
    String entityKey = normalizedEntityKey(rawEntityKey);
    String name;
    switch (entityKey) {
      case "hound": name = "Hound"; break;
      case "clump": name = "Clump"; break;
      case "duller": name = "Duller"; break;
      case "deathmoth": name = "Deathmoth"; break;
      case "hostile_faceling": name = "Hostile Faceling"; break;
      case "false_puddle": name = "False Puddle"; break;
      case "paintings": name = "Paintings"; break;
      case "smiler": name = "Smiler"; break;
      case "skin-stealer": name = "Skin-Stealer"; break;
      case "predatory_window": name = "Predatory Window"; break;
      case "biological_pipeline": name = "Biological Pipeline"; break;
      case "wretch": name = "Wretch"; break;
      case "cable_mimic": name = "Cable Mimic"; break;
      case "the_beast_of_level_5": name = "The Beast of Level 5"; break;
      case "hotel_corpse_lure": name = "Hotel Corpse Lure"; break;
      case "jeff_the_killer": name = "Jeff the Killer"; break;
      case "jane_the_killer": name = "Jane the Killer"; break;
      case "slenderman": name = "Slenderman"; break;
      case "diep_minh": name = "Diệp Minh"; break;
      case "monster_x": name = "Monster X"; break;
      case "john_doe": name = "Jane Doe"; break;
      case "scp_173": name = "SCP-173"; break;
      case "violet_warden": name = "The Violet Warden"; break;
      case "kai_the_devil_within": name = "Kai - The Devil Within"; break;
      default: throw new Exception("Khong co local asset cho " + entityKey);
    }
    return new JSONObject()
      .put("entityKey", entityKey)
      .put("name", name)
      .put("revision", 1)
      .put("anchor", "left-bottom")
      .put("maxHeight", 0.97)
      .put("url", "file:///android_asset/entity/" + ("monster_x".equals(entityKey) ? "X.png" : ("john_doe".equals(entityKey) ? "Jane.png" : ("scp_173".equals(entityKey) ? "SCP173.png" : ("violet_warden".equals(entityKey) ? "Newviolet.png" : ("kai_the_devil_within".equals(entityKey) ? "Kai-TheDevilWithin.png" : entityKey + ".png"))))));
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

  private java.util.List<String> lunaModelCandidates(String baseUrl) {
    java.util.LinkedHashSet<String> models = new java.util.LinkedHashSet<>();
    String configured = BuildConfig.LUNA_MODEL == null ? "" : BuildConfig.LUNA_MODEL.trim();
    // The model selected in the GitHub Secret is authoritative and always gets first attempt.
    // Provider discovery remains a fallback so a stale/inactive configured model can recover.
    if (!configured.isEmpty()) models.add(configured);
    try {
      HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/models").openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(8000);
      connection.setReadTimeout(8000);
      connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.LUNA_API_KEY);
      int status = connection.getResponseCode();
      InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
      StringBuilder body = new StringBuilder();
      if (stream != null) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
          String line;
          while ((line = reader.readLine()) != null) body.append(line);
        }
      }
      connection.disconnect();
      if (status >= 200 && status < 300) {
        JSONObject root = new JSONObject(body.toString());
        JSONArray data = root.optJSONArray("data");
        java.util.ArrayList<String> active = new java.util.ArrayList<>();
        if (data != null) {
          for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            String id = item != null ? item.optString("id", "").trim() : "";
            if (id.isEmpty()) continue;
            String lower = id.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("embedding") || lower.contains("image") || lower.contains("tts") || lower.contains("whisper") || lower.contains("audio")) continue;
            active.add(id);
          }
        }
        for (String id : active) {
          String lower = id.toLowerCase(java.util.Locale.ROOT);
          if (lower.contains("gpt-5.6") || lower.contains("gpt-5") || lower.contains("claude") || lower.contains("gemini")) models.add(id);
        }
        for (String id : active) models.add(id);
      }
    } catch (Exception ignored) {}

    models.add("gpt-5.6-sol");
    return new java.util.ArrayList<>(models);
  }

  private boolean lunaInactiveModel(Exception error) {
    String message = error != null && error.getMessage() != null ? error.getMessage() : "";
    String lower = message.toLowerCase(java.util.Locale.ROOT);
    int code = error instanceof HttpError ? ((HttpError)error).status : 0;
    return (code == 400 || code == 404 || code == 503) &&
      (lower.contains("model_inactive") || lower.contains("model") && (lower.contains("inactive") || lower.contains("not found") || lower.contains("unavailable")));
  }

  private String lunaText(String prompt) throws Exception {
    if (BuildConfig.LUNA_API_KEY == null || BuildConfig.LUNA_API_KEY.trim().isEmpty()) {
      throw new HttpError(401, "Luna API key chưa được cấu hình.");
    }
    String baseUrl = BuildConfig.LUNA_BASE_URL == null ? "" : BuildConfig.LUNA_BASE_URL.trim();
    if (baseUrl.isEmpty()) throw new Exception("Luna Base URL chưa được cấu hình.");
    while (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

    Exception last = null;
    java.util.List<String> models = lunaModelCandidates(baseUrl);
    for (String model : models) {
      if (model == null || model.trim().isEmpty()) continue;
      JSONObject message = new JSONObject().put("role", "user").put("content", prompt);
      JSONObject body = new JSONObject()
        .put("model", model.trim())
        .put("messages", new JSONArray().put(message))
        .put("temperature", 0.75)
        .put("max_tokens", 1800)
        .put("stream", false);
      try {
        JSONObject result = new JSONObject(postJsonLunaFast(
          baseUrl + "/chat/completions",
          BuildConfig.LUNA_API_KEY,
          "Authorization",
          body
        ));
        JSONArray choices = result.optJSONArray("choices");
        JSONObject first = choices != null ? choices.optJSONObject(0) : null;
        JSONObject responseMessage = first != null ? first.optJSONObject("message") : null;
        String responseText = responseMessage != null ? responseMessage.optString("content", "").trim() : "";
        if (responseText.isEmpty()) throw new Exception("Luna không trả nội dung.");
        emit("backroomProvider", "Luna fallback / " + model.trim());
        return responseText;
      } catch (Exception error) {
        last = error;
        if (lunaInactiveModel(error)) continue;
        int code = error instanceof HttpError ? ((HttpError)error).status : 0;
        boolean transport = networkFailure(error) || error instanceof java.net.SocketException || error instanceof java.io.IOException;
        if (transport || code == 408 || code == 429 || code == 500 || code == 502 || code == 503 || code == 504) continue;
        break;
      }
    }
    throw last != null ? last : new Exception("Luna không có model chat khả dụng.");
  }

  private String postJsonLunaFast(String endpoint, String key, String authHeader, JSONObject payload) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(12000);
    connection.setReadTimeout(12000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty(authHeader, authHeader.equals("Authorization") ? "Bearer " + key : key);
    try (OutputStream output = connection.getOutputStream()) {
      output.write(payload.toString().getBytes("UTF-8"));
    }
    int status = connection.getResponseCode();
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

  private String postJsonFast(String endpoint, String key, String authHeader, JSONObject payload) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(18000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty(authHeader, authHeader.equals("Authorization") ? "Bearer " + key : key);
    try (OutputStream output = connection.getOutputStream()) {
      output.write(payload.toString().getBytes("UTF-8"));
    }
    int status = connection.getResponseCode();
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

  private long geminiWorkerScoreUnsafe(int index) {
    long now = System.currentTimeMillis();
    if (index < 0 || index >= geminiCooldownUntil.length) return Long.MAX_VALUE;
    if (geminiCooldownUntil[index] > now) return 1_000_000L + (geminiCooldownUntil[index] - now);
    int rotationBias = ((index - geminiRotation + 5) % 5) * 5;
    return geminiInFlight[index] * 10_000L + geminiFailures[index] * 2_000L + geminiLatencyEma[index] + rotationBias;
  }

  private void noteGeminiSuccess(int index, long latency) {
    synchronized (geminiHealthLock) {
      geminiFailures[index] = Math.max(0, geminiFailures[index] - 1);
      geminiCooldownUntil[index] = 0;
      geminiLatencyEma[index] = Math.max(1, (geminiLatencyEma[index] * 7 + latency * 3) / 10);
    }
  }

  private void noteGeminiFailure(int index, Exception error) {
    synchronized (geminiHealthLock) {
      geminiFailures[index] += 1;
      int code = error instanceof HttpError ? ((HttpError) error).status : 0;
      long now = System.currentTimeMillis();
      if (code == 401 || code == 403) {
        geminiCooldownUntil[index] = now + 30L * 60_000L;
      } else if (code == 429) {
        geminiCooldownUntil[index] = now + 60_000L;
      } else if (retryable(code) || code == 0) {
        geminiCooldownUntil[index] = now + Math.min(30_000L, 2_000L * geminiFailures[index]);
      }
    }
  }

  private int chooseGeminiWorker(String[] keys, boolean[] attempted, int excludedIndex) {
    synchronized (geminiHealthLock) {
      long now = System.currentTimeMillis();
      int best = -1;
      long bestScore = Long.MAX_VALUE;
      for (int i = 0; i < keys.length && i < 5; i++) {
        if (i == excludedIndex || attempted[i] || keys[i] == null || keys[i].trim().isEmpty()) continue;
        if (geminiCooldownUntil[i] > now) continue;
        long score = geminiWorkerScoreUnsafe(i);
        if (score < bestScore) {
          best = i;
          bestScore = score;
        }
      }
      if (best >= 0) geminiInFlight[best] += 1;
      return best;
    }
  }

  private void releaseGeminiWorker(int index) {
    synchronized (geminiHealthLock) {
      if (index >= 0 && index < geminiInFlight.length) geminiInFlight[index] = Math.max(0, geminiInFlight[index] - 1);
    }
  }

  private String geminiTextPolicy(String prompt, int excludedIndex, double temperature, int maxOutputTokens, boolean rememberWorker) throws Exception {
    String[] keys = geminiKeys();
    Exception last = null;
    if (rememberWorker) lastGeminiWorker = -1;
    synchronized (geminiHealthLock) {
      geminiRotation = (geminiRotation + 1) % 5;
    }

    for (int phase = 0; phase < (excludedIndex >= 0 ? 2 : 1); phase++) {
      int activeExclude = phase == 0 ? excludedIndex : -1;
      boolean[] attempted = new boolean[Math.min(5, keys.length)];
      for (int workerAttempt = 0; workerAttempt < attempted.length; workerAttempt++) {
        int index = chooseGeminiWorker(keys, attempted, activeExclude);
        if (index < 0) break;
        attempted[index] = true;
        String key = keys[index];
        try {
          for (int attempt = 0; attempt < 1; attempt++) {
            long started = System.currentTimeMillis();
            try {
              JSONObject part = new JSONObject().put("text", prompt);
              JSONObject contents = new JSONObject().put("role", "user").put("parts", new JSONArray().put(part));
              JSONObject config = new JSONObject()
                .put("responseMimeType", "application/json")
                .put("thinkingConfig", new JSONObject().put("thinkingLevel", "low"));
              if (maxOutputTokens > 0) config.put("maxOutputTokens", maxOutputTokens);
              JSONObject body = new JSONObject().put("contents", new JSONArray().put(contents)).put("generationConfig", config);
              JSONObject result = new JSONObject(postJsonFast(
                "https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent",
                key,
                "x-goog-api-key",
                body
              ));
              JSONArray candidates = result.optJSONArray("candidates");
              StringBuilder responseText = new StringBuilder();
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
                      if (responseText.length() > 0) responseText.append('\n');
                      responseText.append(piece);
                    }
                  }
                }
              }
              if (responseText.length() == 0) throw new Exception("Gemini không trả nội dung.");
              noteGeminiSuccess(index, System.currentTimeMillis() - started);
              if (rememberWorker) lastGeminiWorker = index;
              return responseText.toString();
            } catch (Exception e) {
              last = e;
              noteGeminiFailure(index, e);
              int code = e instanceof HttpError ? ((HttpError)e).status : 0;
              boolean retry = false;
              if (retry) {
                try { Thread.sleep(250); } catch (InterruptedException ignored) {}
                continue;
              }
              break;
            }
          }
        } finally {
          releaseGeminiWorker(index);
        }
      }
    }

    throw last != null ? last : new Exception("Không có Gemini worker khỏe trong APK.");
  }

  private String[] geminiModelChain() {
    return new String[] {"gemini-3.6-flash", "gemini-3.5-flash", "gemini-3.5-flash-lite"};
  }

  private String geminiModelLabel(int modelIndex) {
    if (modelIndex == 0) return "Gemini 3.6 Flash";
    if (modelIndex == 1) return "Gemini 3.5 Flash";
    if (modelIndex == 2) return "Gemini 3.5 Flash-Lite";
    return "Gemini";
  }

  private String geminiThinkingLevel(int modelIndex) {
    return modelIndex == 2 ? "minimal" : "low";
  }

  private int geminiModelTimeoutMs(int modelIndex) {
    if (modelIndex == 0) return 45000;
    if (modelIndex == 1) return 35000;
    return 25000;
  }

  private boolean geminiModelCircuitOpenMatrix(int modelIndex) {
    synchronized (geminiMatrixLock) {
      return modelIndex < 0 || modelIndex >= 3 || geminiModelCircuitUntilMatrix[modelIndex] > System.currentTimeMillis();
    }
  }

  private long geminiLaneScoreMatrix(int modelIndex, int keyIndex) {
    synchronized (geminiMatrixLock) {
      long now = System.currentTimeMillis();
      if (geminiCredentialDisabledUntilMatrix[keyIndex] > now) return Long.MAX_VALUE;
      if (geminiLaneCooldownUntilMatrix[modelIndex][keyIndex] > now) {
        return 1_000_000L + (geminiLaneCooldownUntilMatrix[modelIndex][keyIndex] - now);
      }
      long latency = geminiLaneLatencyMatrix[modelIndex][keyIndex] > 0 ? geminiLaneLatencyMatrix[modelIndex][keyIndex] : 1500L;
      int rotationBias = ((keyIndex - geminiRotation + 5) % 5) * 5;
      return geminiLaneInFlightMatrix[modelIndex][keyIndex] * 10_000L
        + geminiLaneFailuresMatrix[modelIndex][keyIndex] * 2_000L
        + latency + rotationBias;
    }
  }

  private int chooseGeminiMatrixWorker(String[] keys, int modelIndex, boolean[] attempted, int excludedIndex) {
    synchronized (geminiMatrixLock) {
      long now = System.currentTimeMillis();
      if (geminiHostCircuitUntilMatrix > now || geminiModelCircuitUntilMatrix[modelIndex] > now) return -1;
      int best = -1;
      long bestScore = Long.MAX_VALUE;
      for (int i = 0; i < Math.min(5, keys.length); i++) {
        if (i == excludedIndex || attempted[i] || keys[i] == null || keys[i].trim().isEmpty()) continue;
        if (geminiCredentialDisabledUntilMatrix[i] > now || geminiLaneCooldownUntilMatrix[modelIndex][i] > now) continue;
        long score = geminiLaneScoreMatrix(modelIndex, i);
        if (score < bestScore) { best = i; bestScore = score; }
      }
      if (best >= 0) geminiLaneInFlightMatrix[modelIndex][best] += 1;
      return best;
    }
  }

  private void releaseGeminiMatrixWorker(int modelIndex, int keyIndex) {
    synchronized (geminiMatrixLock) {
      if (modelIndex >= 0 && modelIndex < 3 && keyIndex >= 0 && keyIndex < 5) {
        geminiLaneInFlightMatrix[modelIndex][keyIndex] = Math.max(0, geminiLaneInFlightMatrix[modelIndex][keyIndex] - 1);
      }
    }
  }

  private boolean geminiHostNetworkFailureMatrix(Exception error) {
    Throwable cause = error;
    while (cause != null) {
      if (cause instanceof java.net.SocketTimeoutException) return false;
      if (cause instanceof java.net.UnknownHostException ||
          cause instanceof java.net.ConnectException ||
          cause instanceof java.net.SocketException ||
          cause instanceof java.io.IOException) return true;
      cause = cause.getCause();
    }
    return false;
  }

  private void noteGeminiMatrixSuccess(int modelIndex, int keyIndex, long latency) {
    synchronized (geminiMatrixLock) {
      geminiLaneFailuresMatrix[modelIndex][keyIndex] = Math.max(0, geminiLaneFailuresMatrix[modelIndex][keyIndex] - 1);
      geminiLaneCooldownUntilMatrix[modelIndex][keyIndex] = 0L;
      long oldLatency = geminiLaneLatencyMatrix[modelIndex][keyIndex];
      geminiLaneLatencyMatrix[modelIndex][keyIndex] = oldLatency > 0 ? Math.max(1L, (oldLatency * 7L + latency * 3L) / 10L) : Math.max(1L, latency);
      geminiModelCircuitUntilMatrix[modelIndex] = 0L;
      geminiModelTransientMaskMatrix[modelIndex] = 0;
      geminiTransportMaskMatrix &= ~(1 << keyIndex);
      if (Integer.bitCount(geminiTransportMaskMatrix) < 3) geminiHostCircuitUntilMatrix = 0L;
    }
  }

  private String noteGeminiMatrixFailure(int modelIndex, int keyIndex, Exception error) {
    synchronized (geminiMatrixLock) {
      long now = System.currentTimeMillis();
      int code = error instanceof HttpError ? ((HttpError)error).status : 0;
      boolean transport = geminiHostNetworkFailureMatrix(error);
      geminiLaneFailuresMatrix[modelIndex][keyIndex] += 1;

      if (code == 401 || code == 403) {
        geminiCredentialDisabledUntilMatrix[keyIndex] = Math.max(geminiCredentialDisabledUntilMatrix[keyIndex], now + 30L * 60_000L);
        return "auth";
      }
      if (code == 429) {
        geminiLaneCooldownUntilMatrix[modelIndex][keyIndex] = Math.max(geminiLaneCooldownUntilMatrix[modelIndex][keyIndex], now + 60_000L);
        return "quota";
      }
      if (code == 400 || code == 404) {
        geminiModelCircuitUntilMatrix[modelIndex] = Math.max(geminiModelCircuitUntilMatrix[modelIndex], now + 5L * 60_000L);
        return "model";
      }
      if (transport) {
        geminiLaneCooldownUntilMatrix[modelIndex][keyIndex] = Math.max(geminiLaneCooldownUntilMatrix[modelIndex][keyIndex], now + 5_000L);
        geminiTransportMaskMatrix |= (1 << keyIndex);
        if (Integer.bitCount(geminiTransportMaskMatrix) >= 3) geminiHostCircuitUntilMatrix = Math.max(geminiHostCircuitUntilMatrix, now + 30_000L);
        return "transport";
      }
      if (code == 408 || code == 500 || code == 502 || code == 503 || code == 504 || code == 0) {
        geminiLaneCooldownUntilMatrix[modelIndex][keyIndex] = Math.max(geminiLaneCooldownUntilMatrix[modelIndex][keyIndex], now + 5_000L);
        geminiModelTransientMaskMatrix[modelIndex] |= (1 << keyIndex);
        if (Integer.bitCount(geminiModelTransientMaskMatrix[modelIndex]) >= 5) {
          geminiModelCircuitUntilMatrix[modelIndex] = Math.max(geminiModelCircuitUntilMatrix[modelIndex], now + 45_000L);
        }
        return "transient";
      }
      geminiLaneCooldownUntilMatrix[modelIndex][keyIndex] = Math.max(geminiLaneCooldownUntilMatrix[modelIndex][keyIndex], now + 30_000L);
      return "lane";
    }
  }

  private String postJsonGeminiMatrix(String endpoint, String key, JSONObject payload, int timeoutMs) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(timeoutMs);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty("x-goog-api-key", key);
    try (OutputStream output = connection.getOutputStream()) {
      output.write(payload.toString().getBytes("UTF-8"));
    }
    int status = connection.getResponseCode();
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
      throw new HttpError(status, "Gemini HTTP " + status + (detail.isEmpty() ? "" : ": " + detail));
    }
    return body.toString();
  }

  private String geminiMatrixRequest(String prompt, int modelIndex, int keyIndex, int maxOutputTokens, long deadlineMs) throws Exception {
    String[] keys = geminiKeys();
    String[] models = geminiModelChain();
    long remaining = deadlineMs - System.currentTimeMillis();
    if (remaining < 500L) throw new java.net.SocketTimeoutException("Gemini matrix deadline exhausted");
    int timeout = (int)Math.min((long)geminiModelTimeoutMs(modelIndex), remaining);

    JSONObject part = new JSONObject().put("text", prompt);
    JSONObject contents = new JSONObject().put("role", "user").put("parts", new JSONArray().put(part));
    JSONObject config = new JSONObject()
      .put("responseMimeType", "application/json")
      .put("thinkingConfig", new JSONObject().put("thinkingLevel", geminiThinkingLevel(modelIndex)));
    if (maxOutputTokens > 0) config.put("maxOutputTokens", maxOutputTokens);
    JSONObject body = new JSONObject().put("contents", new JSONArray().put(contents)).put("generationConfig", config);

    JSONObject result = new JSONObject(postJsonGeminiMatrix(
      "https://generativelanguage.googleapis.com/v1beta/models/" + models[modelIndex] + ":generateContent",
      keys[keyIndex], body, timeout));
    JSONArray candidates = result.optJSONArray("candidates");
    StringBuilder responseText = new StringBuilder();
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
            if (responseText.length() > 0) responseText.append('\n');
            responseText.append(piece);
          }
        }
      }
    }
    if (responseText.length() == 0) throw new Exception("Gemini không trả nội dung.");
    return responseText.toString();
  }

  private String geminiModelMatrixPolicy(String prompt, int[] modelOrder, int excludedKeyIndex, int maxOutputTokens, boolean rememberWorker, long totalBudgetMs) throws Exception {
    String[] keys = geminiKeys();
    Exception last = null;
    long deadlineMs = System.currentTimeMillis() + totalBudgetMs;
    if (rememberWorker) { lastGeminiWorker = -1; lastGeminiModel = -1; }
    synchronized (geminiMatrixLock) { geminiRotation = (geminiRotation + 1) % 5; }

    for (int phase = 0; phase < (excludedKeyIndex >= 0 ? 2 : 1); phase++) {
      int activeExclude = phase == 0 ? excludedKeyIndex : -1;
      boolean onlyExcluded = phase == 1;
      for (int modelPos = 0; modelPos < modelOrder.length; modelPos++) {
        int modelIndex = modelOrder[modelPos];
        synchronized (geminiMatrixLock) {
          if (geminiHostCircuitUntilMatrix > System.currentTimeMillis()) break;
          if (geminiModelCircuitUntilMatrix[modelIndex] > System.currentTimeMillis()) continue;
        }
        boolean[] attempted = new boolean[Math.min(5, keys.length)];
        for (int workerAttempt = 0; workerAttempt < attempted.length; workerAttempt++) {
          if (System.currentTimeMillis() >= deadlineMs) break;
          int keyIndex;
          if (onlyExcluded) {
            keyIndex = excludedKeyIndex;
            if (keyIndex < 0 || keyIndex >= attempted.length || attempted[keyIndex]) break;
            synchronized (geminiMatrixLock) {
              long now = System.currentTimeMillis();
              if (geminiCredentialDisabledUntilMatrix[keyIndex] > now || geminiLaneCooldownUntilMatrix[modelIndex][keyIndex] > now ||
                  geminiModelCircuitUntilMatrix[modelIndex] > now || geminiHostCircuitUntilMatrix > now) break;
              geminiLaneInFlightMatrix[modelIndex][keyIndex] += 1;
            }
          } else {
            keyIndex = chooseGeminiMatrixWorker(keys, modelIndex, attempted, activeExclude);
            if (keyIndex < 0) break;
          }
          attempted[keyIndex] = true;
          long started = System.currentTimeMillis();
          try {
            String result = geminiMatrixRequest(prompt, modelIndex, keyIndex, maxOutputTokens, deadlineMs);
            noteGeminiMatrixSuccess(modelIndex, keyIndex, System.currentTimeMillis() - started);
            if (rememberWorker) { lastGeminiWorker = keyIndex; lastGeminiModel = modelIndex; }
            return result;
          } catch (Exception error) {
            last = error;
            String failureClass = noteGeminiMatrixFailure(modelIndex, keyIndex, error);
            if (failureClass.equals("model")) break;
            synchronized (geminiMatrixLock) {
              if (geminiHostCircuitUntilMatrix > System.currentTimeMillis() || geminiModelCircuitUntilMatrix[modelIndex] > System.currentTimeMillis()) break;
            }
          } finally {
            releaseGeminiMatrixWorker(modelIndex, keyIndex);
          }
        }
      }
    }
    throw last != null ? last : new Exception("Không có Gemini model/key lane khỏe trong APK.");
  }

  private String geminiText(String prompt) throws Exception {
    return geminiModelMatrixPolicy(prompt, new int[] {0, 1, 2}, -1, 1800, true, 120_000L);
  }

  private String geminiLevelGenerationText(String prompt) throws Exception {
    return geminiModelMatrixPolicy(prompt, new int[] {0, 1, 2}, -1, 7000, true, 120_000L);
  }

  private String levelGenerationPrompt(JSONObject request, String rejection) {
    String correction = (rejection == null || rejection.trim().isEmpty()) ? "" :
      "\nCandidate trước bị engine từ chối. Sửa cấu trúc dựa trên lỗi này, không nới canon: " + rejection;
    return "Bạn là bộ sinh Level procedural cho text game Backrooms. Đây KHÔNG phải lượt gameplay. " +
      "Chỉ tạo world blueprint cho đúng một New Game và trả DUY NHẤT một JSON object LevelGenerationCandidate, không markdown, không giải thích. " +
      "Không tạo runtime progress như discoveredFacts, completedActions, mutations, revision, completed, runSeed, levelId hay generationId. " +
      "Không được sửa canon. environmentTags phải chứa toàn bộ canon environmentTags. phenomena chỉ lấy từ allowedPhenomena. canonClaims không được chứa forbiddenClaims. " +
      "Dùng runSeed như khóa biến thể để các New Game có topology, landmark, evidence và escape blueprint khác nhau trong giới hạn canon. " +
      "Tạo số zone trong giới hạn. Phải có zone tag entry và escape; mọi zone/connection/action/evidence reference phải tồn tại và đường tới escape phải khả dụng. " +
      "Không tạo hoặc yêu cầu escapeBlueprint, solutionId, requiredFacts, requiredActions, evidence ẩn, action ID, conditions, effects hay COMPLETE_LEVEL; toàn bộ puzzle truth do Core giữ riêng. " +
      "JSON root chỉ gồm: candidateSchemaVersion, initialZoneId, zones, landmarks, environment, environmentTags, phenomena, canonClaims, exploreRoute, replies. " +
      "Zone: {id,name,connections:[id],tags:[tag],properties:{}}. " +
      "Dữ liệu ràng buộc từ engine: " + request.toString() + correction;
  }

  private String geminiAuditText(String prompt, int excludedIndex) throws Exception {
    return geminiModelMatrixPolicy(prompt, new int[] {2, 1}, excludedIndex, 650, false, 60_000L);
  }

  private boolean networkFailure(Exception error) {
    Throwable cause = error;
    while (cause != null) {
      if (cause instanceof java.net.UnknownHostException ||
          cause instanceof java.net.ConnectException ||
          cause instanceof java.net.SocketTimeoutException ||
          cause instanceof java.net.SocketException ||
          cause instanceof java.io.IOException) return true;
      cause = cause.getCause();
    }
    return false;
  }

  private String networkFailureMessage() {
    return "Lỗi mạng/DNS: không thể kết nối tới máy chủ AI. Kiểm tra Wi-Fi/4G, Private DNS hoặc VPN.";
  }

  private String generateText(String prompt) throws Exception {
    emit("backroomProvider", "Gemini 3.6 Flash");
    Exception geminiFailure;
    try {
      String geminiResult = geminiText(prompt);
      emit("backroomProvider", geminiModelLabel(lastGeminiModel) + " K" + (lastGeminiWorker + 1));
      return geminiResult;
    } catch (Exception error) {
      geminiFailure = error;
    }

    emit("backroomProvider", "Luna fallback");
    Exception lunaFailure;
    try {
      return lunaText(prompt);
    } catch (Exception error) {
      lunaFailure = error;
    }

    if (networkFailure(geminiFailure) && networkFailure(lunaFailure)) {
      throw new Exception(networkFailureMessage());
    }
    String geminiMessage = geminiFailure != null && geminiFailure.getMessage() != null ? geminiFailure.getMessage() : "Gemini không khả dụng";
    String lunaMessage = lunaFailure != null && lunaFailure.getMessage() != null ? lunaFailure.getMessage() : "Luna không khả dụng";
    throw new Exception("Gemini: " + geminiMessage + "; Luna fallback: " + lunaMessage);
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

  private SnapshotImage geminiImageModel(String prompt, String model) throws Exception {
    Exception last = null;
    boolean hasKey = false;
    for (String key : geminiKeys()) {
      if (key == null || key.isEmpty()) continue;
      hasKey = true;
      for (int attempt = 0; attempt < 2; attempt++) {
        try {
          JSONObject input = new JSONObject().put("type", "text").put("text", prompt);
          JSONObject format = new JSONObject()
            .put("type", "image")
            .put("mime_type", "image/jpeg")
            .put("aspect_ratio", "16:9");
          if ("gemini-3.1-flash-image".equals(model)) format.put("image_size", "512");
          else format.put("image_size", "1K");
          JSONObject body = new JSONObject()
            .put("model", model)
            .put("input", new JSONArray().put(input))
            .put("response_format", format);
          JSONObject result = new JSONObject(postJson("https://generativelanguage.googleapis.com/v1beta/interactions", key, "x-goog-api-key", body));
          SnapshotImage image = findSnapshotImage(result);
          if (image == null || image.data.isEmpty()) throw new Exception("Gemini image không trả ảnh.");
          if (image.data.length() > MAX_SNAPSHOT_BASE64) throw new Exception("Snapshot Gemini quá lớn để hiển thị trong APK.");
          return new SnapshotImage(image.data, image.mimeType, model, "Gemini");
        } catch (Exception e) {
          last = e;
          if (networkFailure(e)) throw e;
          int code = e instanceof HttpError ? ((HttpError)e).status : 0;
          if (code == 429) break;
          if (attempt == 0 && (code == 0 || retryable(code))) {
            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
            continue;
          }
          break;
        }
      }
    }
    if (!hasKey) throw new Exception("Gemini chưa có API key.");
    throw last != null ? last : new Exception("Gemini không tạo được ảnh.");
  }

  private String compactProviderDetail(Exception error) {
    if (error == null || error.getMessage() == null) return "";
    String message = error.getMessage().replace('\n', ' ').replace('\r', ' ').trim();
    if (message.startsWith("Provider HTTP ")) {
      int colon = message.indexOf(": ");
      if (colon >= 0 && colon + 2 < message.length()) message = message.substring(colon + 2);
    }
    if (message.length() > 180) message = message.substring(0, 180) + "…";
    return message;
  }

  private String friendlyImageFailure(String provider, Exception error) {
    if (error == null) return provider + ": không khả dụng";
    if (networkFailure(error)) return provider + ": lỗi mạng/DNS";
    int code = error instanceof HttpError ? ((HttpError)error).status : 0;
    if (code == 429) return provider + ": hết quota hoặc đang bị giới hạn tốc độ";
    if (code == 401) return provider + ": API key không hợp lệ hoặc chưa được cấu hình";
    if (code == 403) return provider + ": API key chưa có quyền dùng model ảnh";
    if (code == 404) return provider + ": model ảnh không khả dụng";
    String message = error.getMessage() == null ? "" : error.getMessage();
    String lower = message.toLowerCase();
    if (code == 400) {
      if (lower.contains("verif")) return provider + ": tổ chức/tài khoản chưa được xác minh để dùng model ảnh";
      if (lower.contains("billing") || lower.contains("credit") || lower.contains("payment")) return provider + ": billing/credit không cho phép tạo ảnh";
      if (lower.contains("model") && (lower.contains("access") || lower.contains("not found") || lower.contains("does not exist"))) return provider + ": tài khoản chưa có quyền dùng model ảnh";
      String detail = compactProviderDetail(error);
      return provider + ": HTTP 400" + (detail.isEmpty() ? "" : " - " + detail);
    }
    if (message.contains("chưa có API key")) return provider + ": chưa có API key";
    if (message.contains("quá lớn")) return provider + ": ảnh trả về quá lớn";
    return provider + ": không tạo được ảnh";
  }

  private SnapshotImage snapshotImage(String prompt) throws Exception {
    Exception geminiFailure = null;
    for (String model : GEMINI_IMAGE_MODELS) {
      emit("backroomSnapshotProvider", "Gemini");
      try {
        return geminiImageModel(prompt, model);
      } catch (Exception e) {
        geminiFailure = e;
        if (networkFailure(e)) break;
      }
    }

    if (networkFailure(geminiFailure)) throw new Exception(networkFailureMessage());
    throw new Exception(friendlyImageFailure("Gemini", geminiFailure));
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
      "Show the present scene only, not a montage. Do NOT depict Kai Akechi / Twilight or any player-character body in the generated image; the app overlays Kai separately. " +
      "Compose the environment for a fixed character overlay: keep the right 40% visually open and place key environmental details, threats and exits in the left or center. " +
      "Do not invent NPCs, monsters, exits, loot, injuries, weapons, text, HUD, blood or props that are not explicitly present in the state. " +
      "If party is empty, do not add any other person or humanoid companion. Level 0 uses stale yellow wallpaper, damp carpet, fluorescent ceiling panels and oppressive empty office-like geometry. " +
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
      JSONObject state = new JSONObject(stateJson);
      int turn = state.optInt("turn", 1);
      JSONObject payload = new JSONObject()
        .put("turn", turn)
        .put("message", "Snapshot chưa được cấu hình.");
      emit("backroomSnapshotError", payload.toString());
    } catch (Exception ignored) {
      emit("backroomSnapshotError", "{\"turn\":0,\"message\":\"Snapshot chưa được cấu hình.\"}");
    }
  }

  private void emit(String function, String json) {
    String script = "window." + function + "(" + JSONObject.quote(json) + ")";
    runOnUiThread(() -> { if (webView != null) webView.evaluateJavascript(script, null); });
  }

  private int rawLevelNumber(JSONObject state) {
    JSONObject level = state.optJSONObject("level");
    if (level != null) return Math.max(0, level.optInt("number", 0));
    String title = state.optString("title", "");
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("Level\\s+(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(title);
    if (matcher.find()) return Math.max(0, Integer.parseInt(matcher.group(1)));
    return 0;
  }

  private int currentLevel(JSONObject state) {
    JSONObject level = state.optJSONObject("level");
    if (level != null) return Math.max(0, Math.min(6, level.optInt("number", 0)));
    String title = state.optString("title", "");
    for (int n = 0; n <= 6; n++) if (title.contains("Level " + n)) return n;
    return 0;
  }

  private void reconcileVisualWorldState(JSONObject before, JSONObject candidateState, JSONObject rolls) throws Exception {
    int oldLevel = currentLevel(before);
    int structuredLevel = currentLevel(candidateState);
    int describedLevel = mentionedLevel(candidateState);
    int requestedLevel = structuredLevel != oldLevel ? structuredLevel : (describedLevel >= 0 ? describedLevel : oldLevel);
    boolean levelChange = requestedLevel != oldLevel;

    if (levelChange && !canTransition(before, rolls)) {
      JSONObject oldStructured = before.optJSONObject("level");
      candidateState.put("level", oldStructured != null
        ? new JSONObject(oldStructured.toString())
        : new JSONObject().put("number", oldLevel).put("name", levelName(oldLevel)));
      if (before.has("title")) candidateState.put("title", before.optString("title", ""));
      if (before.has("location")) candidateState.put("location", before.optString("location", ""));
      return;
    }

    if (levelChange) {
      candidateState.put("level", new JSONObject().put("number", requestedLevel).put("name", levelName(requestedLevel)));
      candidateState.put("title", "Level " + requestedLevel + " – " + levelName(requestedLevel));

      String candidateLocation = candidateState.optString("location", "").trim();
      int locationLevel = -1;
      if (!candidateLocation.isEmpty()) {
        JSONObject locationProbe = new JSONObject().put("location", candidateLocation);
        locationLevel = mentionedLevel(locationProbe);
      }
      if (candidateLocation.isEmpty() || (locationLevel >= 0 && locationLevel != requestedLevel)) {
        candidateState.put("location", "Level " + requestedLevel + " / " + levelName(requestedLevel));
      }
    } else {
      candidateState.put("level", new JSONObject().put("number", oldLevel).put("name", levelName(oldLevel)));
    }
  }

  private int mentionedLevel(JSONObject state) {
    String location = state.optString("location", "").toLowerCase(java.util.Locale.ROOT);
    String title = state.optString("title", "").toLowerCase(java.util.Locale.ROOT);
    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("level\\s*([0-6])", java.util.regex.Pattern.CASE_INSENSITIVE);
    java.util.regex.Matcher explicit = pattern.matcher(location);
    if (explicit.find()) return Integer.parseInt(explicit.group(1));
    String[] names = {"the lobby", "parking zone", "pipe dreams", "the electrical station", "the abandoned office", "terror hotel", "lights out"};
    for (int n = 0; n < names.length; n++) if (location.contains(names[n])) return n;
    explicit = pattern.matcher(title);
    if (explicit.find()) return Integer.parseInt(explicit.group(1));
    for (int n = 0; n < names.length; n++) if (title.contains(names[n])) return n;
    return -1;
  }

  private static final String LINEAR_SUBLEVEL_ROUTE_VERSION = "BACKROOMS_FANDOM_LEVELS_0_6_R01";
  private static final String[] LINEAR_AREA_IDS = { "0", "epsilon", "0.01", "0.1", "0.11", "0.22", "0.23", "0.41", "0.5", "0.66", "0.7", "0.8", "0.99", "LS-2", "Dullness", "Red Rooms", "1", "1.01", "1.1", "1.5", "1.618033988749894...", "2", "2.1", "2.71828182845...", "2.2", "3", "3.14159265358...", "3.53", "4", "4.3", "4.4", "4.11", "5", "5.1", "5.2", "5.55", "6", "6.1", "6.2", "6.28318530718...", "6.5", "6.66", "6.99" };
  private static final String[] LINEAR_AREA_NAMES = { "The Lobby", "Incessant Hum-Buzz", "The Exit ?", "Deep Emptiness", "Water Damage", "Fully Remodeled", "Half Finished", "Disease", "Chaotic Structure", "The Lobby Went COLD", "Claustrophobia", "Inundation", "Deeper Regions", "LS-2", "Dullness", "Red Rooms", "Parking Zone", "The Basement of Level 1", "Fallen Vehicle", "Lurking Danger", "Midas’ Touch", "Pipe Dreams", "The Subterranean Complex", "Euler’s Imagination", "The Red Flood", "Electrical Station", "satuЯation", "The Cacophony of Corrosion", "The Abandoned Office", "The Cubicles", "Intrusive Configuration", "Insubstantial Skywalks", "Terror Hotel", "Summer Resort", "The Gilded Atrium", "Can’t Stop Watching", "Lights Out", "Silva Subterraneus", "Eyes On The Road", "Amaxophobia", "Blinding Lights", "Cryophobia", "Umbral Light" };
  private static final String[] LINEAR_AREA_TYPES = { "MAIN", "SPECIAL", "SUBLEVEL", "SUBLEVEL", "SUBLEVEL", "SUBLEVEL", "SUBLEVEL", "SUBLEVEL", "SUBLEVEL", "SUBLEVEL", "SUBLEVEL", "SUBLEVEL", "SUBLEVEL", "SPECIAL", "SPECIAL", "SPECIAL", "MAIN", "SUBLEVEL", "SUBLEVEL", "SUBLEVEL", "SUBLEVEL", "MAIN", "SUBLEVEL", "SUBLEVEL", "SUBLEVEL", "MAIN", "SUBLEVEL", "SUBLEVEL", "MAIN", "SUBLEVEL", "SUBLEVEL", "SUBLEVEL", "MAIN", "SUBLEVEL", "SUBLEVEL", "SUBLEVEL", "MAIN", "SUBLEVEL", "SUBLEVEL", "SUBLEVEL", "SUBLEVEL", "SUBLEVEL", "SUBLEVEL" };
  private static final int[] LINEAR_AREA_LEVELS = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 6, 6, 6, 6, 6, 6, 6 };
  private static final String[] LINEAR_AREA_PARENT_NAMES = { "The Lobby", "The Lobby", "The Lobby", "The Lobby", "The Lobby", "The Lobby", "The Lobby", "The Lobby", "The Lobby", "The Lobby", "The Lobby", "The Lobby", "The Lobby", "The Lobby", "The Lobby", "The Lobby", "Parking Zone", "Parking Zone", "Parking Zone", "Parking Zone", "Parking Zone", "Pipe Dreams", "Pipe Dreams", "Pipe Dreams", "Pipe Dreams", "Electrical Station", "Electrical Station", "Electrical Station", "The Abandoned Office", "The Abandoned Office", "The Abandoned Office", "The Abandoned Office", "Terror Hotel", "Terror Hotel", "Terror Hotel", "Terror Hotel", "Lights Out", "Lights Out", "Lights Out", "Lights Out", "Lights Out", "Lights Out", "Lights Out" };
  private static final String[] LINEAR_AREA_NEXT_IDS = { "epsilon", "0.01", "0.1", "0.11", "0.22", "0.23", "0.41", "0.5", "0.66", "0.7", "0.8", "0.99", "LS-2", "Dullness", "Red Rooms", "1", "1.01", "1.1", "1.5", "1.618033988749894...", "2", "2.1", "2.71828182845...", "2.2", "3", "3.14159265358...", "3.53", "4", "4.3", "4.4", "4.11", "5", "5.1", "5.2", "5.55", "6", "6.1", "6.2", "6.28318530718...", "6.5", "6.66", "6.99", "" };

  private int mainRouteIndex(int level) {
    for (int i = 0; i < LINEAR_AREA_IDS.length; i++) {
      if (LINEAR_AREA_LEVELS[i] == level && "MAIN".equals(LINEAR_AREA_TYPES[i])) return i;
    }
    return -1;
  }

  private int linearAreaIndex(JSONObject state) {
    JSONObject flags = state.optJSONObject("flags");
    JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
    if (exploration != null) {
      int stored = exploration.optInt("routeIndex", -1);
      String storedId = exploration.optString("areaId", "");
      if (stored >= 0 && stored < LINEAR_AREA_IDS.length &&
          (storedId.isEmpty() || LINEAR_AREA_IDS[stored].equals(storedId))) return stored;
      if (!storedId.isEmpty()) {
        for (int i = 0; i < LINEAR_AREA_IDS.length; i++) if (LINEAR_AREA_IDS[i].equals(storedId)) return i;
      }
    }
    int fallback = mainRouteIndex(Math.max(0, currentLevel(state)));
    return fallback >= 0 ? fallback : 0;
  }

  private boolean hasNextLinearArea(JSONObject state) {
    return !LINEAR_AREA_NEXT_IDS[linearAreaIndex(state)].isEmpty();
  }

  private String linearAreaLabel(int index) {
    if (index < 0 || index >= LINEAR_AREA_IDS.length) return "Unknown Area";
    String id = LINEAR_AREA_IDS[index];
    String name = LINEAR_AREA_NAMES[index];
    String type = LINEAR_AREA_TYPES[index];
    if ("MAIN".equals(type)) return "Level " + id + " – " + name;
    if ("epsilon".equals(id)) return "Level ε – " + name;
    if ("SPECIAL".equals(type)) return id.equals(name) ? id : id + " – " + name;
    return "Level " + id + " – " + name;
  }

  private void stampLinearArea(JSONObject state, int index, boolean resetProgress, boolean relocate) throws Exception {
    if (index < 0 || index >= LINEAR_AREA_IDS.length) throw new Exception("linear_area_index_out_of_range");
    int parentLevel = LINEAR_AREA_LEVELS[index];
    String parentName = LINEAR_AREA_PARENT_NAMES[index];
    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) flags = new JSONObject();
    JSONObject exploration = flags.optJSONObject("exploration");
    if (exploration == null) exploration = new JSONObject();

    exploration.put("routeVersion", LINEAR_SUBLEVEL_ROUTE_VERSION);
    exploration.put("routeIndex", index);
    exploration.put("areaId", LINEAR_AREA_IDS[index]);
    exploration.put("areaName", LINEAR_AREA_NAMES[index]);
    exploration.put("areaType", LINEAR_AREA_TYPES[index]);
    exploration.put("parentLevel", parentLevel);
    exploration.put("minimumTurns", 6);
    if (resetProgress) {
      exploration.put("levelTurns", 0);
      for (String key : new String[] {"confirmedExit", "transitionReady", "exitReady", "exitProgress", "exitCandidate", "exitChanceThreshold"}) {
        exploration.remove(key);
      }
    }

    flags.put("exploration", exploration);
    flags.put("currentLevel", new JSONObject().put("number", parentLevel).put("name", parentName));
    state.put("flags", flags);
    state.put("level", new JSONObject().put("number", parentLevel).put("name", parentName));
    if (relocate) {
      String label = linearAreaLabel(index);
      state.put("title", label);
      state.put("location", label);
    }
  }

  private boolean advanceLinearArea(JSONObject before, JSONObject state) throws Exception {
    int current = linearAreaIndex(before);
    String targetId = LINEAR_AREA_NEXT_IDS[current];
    if (targetId.isEmpty()) return false;
    int target = -1;
    for (int i = 0; i < LINEAR_AREA_IDS.length; i++) if (LINEAR_AREA_IDS[i].equals(targetId)) target = i;
    if (target < 0) throw new Exception("declared_transition_target_missing");
    stampLinearArea(state, target, true, true);
    return true;
  }

  private String linearAreaPrompt(JSONObject state) {
    int current = linearAreaIndex(state);
    String currentLabel = linearAreaLabel(current);
    String targetId = LINEAR_AREA_NEXT_IDS[current];
    if (targetId.isEmpty()) {
      return "LINEAR SUBLEVEL HARD LOCK: khu hiện tại = " + currentLabel + ". Đây là cuối campaign route đã khai báo; không tự tạo khu kế tiếp.\n" + campaignStoryBeatPrompt(state);
    }
    int target = -1;
    for (int i = 0; i < LINEAR_AREA_IDS.length; i++) if (LINEAR_AREA_IDS[i].equals(targetId)) target = i;
    String nextLabel = linearAreaLabel(target);
    return "TRANSITION GRAPH HARD LOCK: khu hiện tại = " + currentLabel + ". Target authoritative đã khai báo là " + nextLabel + ". Model không được tự chọn target ngoài graph.\n" + campaignStoryBeatPrompt(state);
  }

  private JSONObject loadLevel01Story() throws Exception {
    StringBuilder content = new StringBuilder();
    try (InputStream stream = getAssets().open("campaign_story/level0-to-level1.json");
         BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
      String line;
      while ((line = reader.readLine()) != null) content.append(line).append('\n');
    }
    return new JSONObject(content.toString());
  }

  private String currentStoryAreaId(JSONObject state) {
    JSONObject flags = state != null ? state.optJSONObject("flags") : null;
    JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
    String areaId = exploration != null ? exploration.optString("areaId", "").trim() : "";
    if (!areaId.isEmpty()) return areaId;
    return String.valueOf(currentLevel(state == null ? new JSONObject() : state));
  }

  private String campaignStoryBeatPrompt(JSONObject state) {
    try {
      JSONObject root = loadLevel01Story();
      String areaId = currentStoryAreaId(state);
      JSONArray beats = root.optJSONArray("beats");
      JSONObject beat = null;
      if (beats != null) {
        for (int i = 0; i < beats.length(); i++) {
          JSONObject candidate = beats.optJSONObject(i);
          if (candidate != null && areaId.equals(candidate.optString("areaId", ""))) { beat = candidate; break; }
        }
      }
      if (beat == null) return "MAIN STORY HARD LOCK: giữ nhiệm vụ SRU điều tra Async và continuity hiện tại; không tự bịa cốt truyện, vị trí hay reunion của đồng đội.";

      JSONObject entry = root.optJSONObject("entryEvent");
      JSONObject mission = root.optJSONObject("officialMission");
      if (entry == null) entry = new JSONObject();
      if (mission == null) mission = new JSONObject();

      JSONObject missionVisible = new JSONObject()
        .put("year", mission.optInt("year", entry.optInt("year", 2299)))
        .put("unit", mission.optString("unit", "SRU"))
        .put("subject", mission.optString("subject", "Async"))
        .put("objective", mission.optString("objective", ""))
        .put("entryMethod", mission.optString("entryMethod", "SPATIAL_GATE"))
        .put("backroomsOriginKnown", false);

      JSONObject questVisible = state != null ? state.optJSONObject("storyQuest") : null;
      String coreObjective = questVisible != null ? questVisible.optString("objectiveTitle", "").trim() : "";
      String visibleObjective = coreObjective.isEmpty() ? beat.optString("visibleObjective", "") : coreObjective;

      JSONObject visible = new JSONObject()
        .put("storyId", root.optString("storyId", "MAIN_LEVEL0_TO_LEVEL1_R01"))
        .put("areaId", areaId)
        .put("phase", beat.optString("phase", ""))
        .put("storyPurpose", beat.optString("storyPurpose", ""))
        .put("visibleObjective", visibleObjective)
        .put("discoveryThemes", beat.optJSONArray("discoveryThemes") == null ? new JSONArray() : beat.optJSONArray("discoveryThemes"))
        .put("characterThread", beat.optString("characterThread", ""))
        .put("officialMission", missionVisible);
      if (questVisible != null) visible.put("quest", questVisible);

      return "MAIN STORY HARD LOCK: năm 2299 Kai, Iris và Syvial thuộc SRU chủ động đi qua cùng một cổng không gian để điều tra Async rồi bị phân tán tới các Level khác nhau. "
        + "Kai không biết vị trí Iris hoặc Syvial cho tới khi story continuity xác nhận reunion. "
        + "Mission brief, story beat và quest text KHÔNG phải discovery evidence: không tự tạo dấu vết Async, hồ sơ Async, giọng nói, vật chứng hay vị trí đồng đội. "
        + "Chỉ bằng chứng đã được Core/Discovery surfacing mới được dùng để xác nhận. Gemini không được advance quest, không tự teleport reunion, không tự khôi phục liên lạc, "
        + "không tiết lộ reunion level tương lai, transition hoặc hidden escape data, không sửa Core/RNG/campaign route. CURRENT_STORY_BEAT=" + visible.toString();
    } catch (Exception ignored) {
      return "MAIN STORY HARD LOCK: năm 2299 đội SRU của Kai, Iris và Syvial đi qua cùng một cổng để điều tra Async rồi bị phân tán tới các Level khác nhau; "
        + "giữ vị trí Iris và Syvial chưa xác định cho tới khi Core story continuity xác nhận.";
    }
  }

  private int levelTurns(JSONObject state) {
    JSONObject flags = state.optJSONObject("flags");
    JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
    return exploration != null ? Math.max(0, exploration.optInt("levelTurns", 0)) : 0;
  }

  private boolean progressionReady(JSONObject state) {
    return hasNextLinearArea(state) && levelTurns(state) >= 6;
  }

  private void recordLevelProgress(JSONObject state, JSONObject before, int oldLevel, int newLevel, boolean areaAdvanced) throws Exception {
    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) flags = new JSONObject();
    JSONObject exploration = flags.optJSONObject("exploration");
    if (exploration == null) exploration = new JSONObject();
    int trustedIndex = linearAreaIndex(state);
    exploration.put("routeVersion", LINEAR_SUBLEVEL_ROUTE_VERSION);
    exploration.put("routeIndex", trustedIndex);
    exploration.put("areaId", LINEAR_AREA_IDS[trustedIndex]);
    exploration.put("areaName", LINEAR_AREA_NAMES[trustedIndex]);
    exploration.put("areaType", LINEAR_AREA_TYPES[trustedIndex]);
    exploration.put("parentLevel", LINEAR_AREA_LEVELS[trustedIndex]);
    exploration.put("levelTurns", (areaAdvanced || oldLevel != newLevel) ? 0 : levelTurns(before) + 1);
    exploration.put("minimumTurns", 6);
    flags.put("exploration", exploration);
    state.put("flags", flags);
  }

  private JSONObject rollSpec(String label, int chance, boolean eligible) throws Exception {
    JSONObject result = new JSONObject().put("label", label).put("eligible", eligible).put("chancePercent", chance);
    if (!eligible) return result.put("success", false).put("roll", JSONObject.NULL);
    int roll = requireGameCore().lockedActionRoll(label, 100);
    return result.put("roll", roll).put("success", roll <= chance);
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
    int roll = requireGameCore().lockedActionRoll(label, max);
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

  private JSONObject authoritativeEnvironmentLootRoll(boolean eligible) throws Exception {
    JSONObject result = new JSONObject()
      .put("label", "loot")
      .put("dice", "d10000")
      .put("max", 10000)
      .put("threshold", 0)
      .put("eligible", false)
      .put("chancePercent", 0.0)
      .put("chance", "0.0000%")
      .put("roll", JSONObject.NULL)
      .put("success", false);
    if (!eligible) return result;

    JSONObject runtime = new JSONObject(requireGameCore().currentActionContext());
    JSONObject loot = runtime.optJSONObject("loot");
    if (loot == null || !loot.optBoolean("eligible", false)) return result;
    int threshold = Math.max(0, Math.min(10000, loot.optInt("threshold", 0)));
    double percent = threshold / 100.0;
    boolean guaranteed = threshold >= 10000;
    result.put("dice", guaranteed ? "none" : "d10000")
      .put("threshold", threshold)
      .put("eligible", true)
      .put("chancePercent", percent)
      .put("chance", String.format(java.util.Locale.ROOT, "%.4f%% pity turn %d", percent, loot.optInt("pityTurn", 1)))
      .put("roll", guaranteed || loot.isNull("roll") ? JSONObject.NULL : loot.optInt("roll", 0))
      .put("success", loot.optBoolean("success", false));
    return result;
  }

  private JSONObject makeGameplayRolls(JSONObject state, String actionKind, String action, boolean meta) throws Exception {
    JSONObject rolls = new JSONObject().put("turn", state.optInt("turn", 1)).put("meta", meta);
    if (meta) return rolls;

    String actionKindNormalized = actionKind == null ? "" : actionKind.trim().toUpperCase(java.util.Locale.ROOT);
    boolean exploreAction = "EXPLORE".equals(actionKindNormalized);
    boolean entityEncounterAction = exploreAction || "SEARCH".equals(actionKindNormalized) || "EXECUTE".equals(actionKindNormalized);

    int level = Math.max(0, Math.min(6, currentLevel(state)));
    int[] hazardThresholds = {400, 700, 1000, 1200, 300, 1000, 1200};
    int[] entityThresholds = {1805, 2000, 2150, 2150, 1810, 2200, 1805};
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
    JSONObject runtimeContext = new JSONObject(requireGameCore().currentActionContext());
    JSONObject proceduralEntityContext = runtimeContext.optJSONObject("entityEncounter");
    boolean proceduralEntitiesAllowed = proceduralEntityContext == null || proceduralEntityContext.optBoolean("allowed", true);
    boolean entityAllowed = (flags == null || flags.optBoolean("entityEncountersAllowed", true)) && proceduralEntitiesAllowed;
    JSONObject madGod = flags != null ? flags.optJSONObject("madGod") : null;
    boolean madGodEligible = search && (madGod == null || !madGod.optBoolean("spawned", false)) && (flags == null || flags.optBoolean("madGodDiscoveryAllowed", true));

    rolls.put("anNhienEncounter", thresholdRoll("anNhienEncounter", 10000, 25, physical && !anNhienEncountered, " follower encounter"));
    rolls.put("survivor", thresholdRoll("survivor", 10000, 200, survivorAllowed, ""));
    boolean irisStoryGate = StoryCompanionContinuity.canMaterialize("iris", level, partyHas(state, "iris") || flagSpawned(state, "iris"));
    boolean syvialStoryGate = StoryCompanionContinuity.canMaterialize("syvial", level, partyHas(state, "syvial") || flagSpawned(state, "syvial"));
    boolean luciaStoryGate = StoryCompanionContinuity.canMaterialize("lucia", level, partyHas(state, "lucia") || flagSpawned(state, "lucia"));
    rolls.put("irisReunion", new JSONObject().put("label", "irisReunion").put("storyOwned", true).put("eligible", irisStoryGate).put("success", irisStoryGate).put("roll", JSONObject.NULL));
    rolls.put("syvialReunion", new JSONObject().put("label", "syvialReunion").put("storyOwned", true).put("eligible", syvialStoryGate).put("success", syvialStoryGate).put("roll", JSONObject.NULL));
    rolls.put("luciaEncounter", new JSONObject().put("label", "luciaEncounter").put("storyOwned", true).put("requiresQuest", false).put("eligible", luciaStoryGate).put("success", luciaStoryGate).put("roll", JSONObject.NULL));
    int anNhienHazardThreshold = anNhienFollowing ? (hazardThresholds[level] * 75 / 100) : hazardThresholds[level];
    JSONObject anNhienHazardCheck = thresholdRoll("anNhienHazardCheck", 10000, 3000, anNhienFollowing && search && water, " Đừng Đụng Vào, Nhìn Là Biết Độc");
    rolls.put("anNhienHazardCheck", anNhienHazardCheck);
    if (anNhienHazardCheck.optBoolean("success", false)) anNhienHazardThreshold = 0;
    rolls.put("hazard", thresholdRoll("hazard", 10000, anNhienHazardThreshold, physical,
      anNhienFollowing ? " -25% Có Gì Đó Sai Sai" : ""));
    String entitySuffix = level == 0 || level == 4 || level == 6 ? " incursion/roaming only" : "";
    JSONObject diepMinhRoll = thresholdRoll("diepMinhEncounter", 10000, EntityEncounterPolicy.scaledThreshold(300), entityEncounterAction && entityAllowed, " unique boss 3%");
    rolls.put("diepMinhEncounter", diepMinhRoll);
    int monsterXLevel = rawLevelNumber(state);
    JSONObject monsterXRoll = thresholdRoll("monsterXEncounter", 10000, EntityEncounterPolicy.scaledThreshold(1000),
      entityEncounterAction && entityAllowed && !diepMinhRoll.optBoolean("success", false) && monsterXLevel >= 0 && monsterXLevel <= 999, " Monster X unique roaming 10% Level 0-999");
    rolls.put("monsterXEncounter", monsterXRoll);
    int johnDoeLevel = rawLevelNumber(state);
    JSONObject johnDoeRoll = thresholdRoll("johnDoeEncounter", 10000, 1000,
      entityEncounterAction && entityAllowed && !diepMinhRoll.optBoolean("success", false) && !monsterXRoll.optBoolean("success", false) && johnDoeLevel >= 0 && johnDoeLevel <= 999,
      " Jane Doe unique roaming 10% Level 0-999");
    rolls.put("johnDoeEncounter", johnDoeRoll);
    JSONObject scp173Roll = thresholdRoll("scp173Encounter", 10000, EntityEncounterPolicy.scaledThreshold(500),
      entityEncounterAction && entityAllowed && !diepMinhRoll.optBoolean("success", false) && !monsterXRoll.optBoolean("success", false) && !johnDoeRoll.optBoolean("success", false),
      " SCP-173 independent 5% valid encounter");
    rolls.put("scp173Encounter", scp173Roll);
    JSONObject violetWardenRoll = thresholdRoll("violetWardenEncounter", 10000, EntityEncounterPolicy.scaledThreshold(1000),
      entityEncounterAction && entityAllowed && !diepMinhRoll.optBoolean("success", false) && !monsterXRoll.optBoolean("success", false) && !johnDoeRoll.optBoolean("success", false) && !scp173Roll.optBoolean("success", false),
      " Violet Warden unique roaming 10% all Levels/sublevels");
    rolls.put("violetWardenEncounter", violetWardenRoll);
    JSONObject kaiDevilWithinRoll = thresholdRoll("kaiDevilWithinEncounter", 10000, 1000,
      entityEncounterAction && entityAllowed && !diepMinhRoll.optBoolean("success", false) && !monsterXRoll.optBoolean("success", false) && !johnDoeRoll.optBoolean("success", false) && !scp173Roll.optBoolean("success", false) && !violetWardenRoll.optBoolean("success", false),
      " Kai - The Devil Within secret form 10% all Levels/sublevels");
    rolls.put("kaiDevilWithinEncounter", kaiDevilWithinRoll);
    JSONObject normalEntityRoll = thresholdRoll("entityEncounter", 10000, EntityEncounterPolicy.scaledThreshold(entityThresholds[level]), entityEncounterAction && entityAllowed && !diepMinhRoll.optBoolean("success", false) && !monsterXRoll.optBoolean("success", false) && !johnDoeRoll.optBoolean("success", false) && !scp173Roll.optBoolean("success", false) && !violetWardenRoll.optBoolean("success", false) && !kaiDevilWithinRoll.optBoolean("success", false), entitySuffix);
    rolls.put("entityEncounter", normalEntityRoll);
    if (normalEntityRoll.optBoolean("success", false)) {
      String[] roamingPool = {"hound","clump","duller","deathmoth","hostile_faceling","false_puddle","paintings","smiler","skin-stealer","predatory_window","biological_pipeline","wretch","cable_mimic","the_beast_of_level_5","hotel_corpse_lure","jeff_the_killer","jane_the_killer","slenderman"};
      rolls.put("roamingEntityKey", roamingPool[requireGameCore().lockedActionRoll("roamingEntityKey", roamingPool.length) - 1]);
    }
    boolean lootAction = "SEARCH".equals(actionKindNormalized) || "EXPLORE".equals(actionKindNormalized);
    rolls.put("loot", authoritativeEnvironmentLootRoll(lootAction));
    rolls.put("madGodSet", thresholdRoll("madGodSet", 10000, 1, madGodEligible, " UR+ UNIQUE discovery"));
    rolls.put("almondWater", thresholdRoll("almondWater", 10000, waterThresholds[level], search && water, ""));

    int exitThreshold = exitThresholdAndroid(state);
    if (anNhienFollowing) exitThreshold = Math.min(10000, exitThreshold + 200);
    JSONObject anNhienRead = thresholdRoll("anNhienRead", 10000, 2000, anNhienFollowing && search && exitIntent, " Khoan, Để Tôi Đọc Cái Này");
    rolls.put("anNhienRead", anNhienRead);
    if (anNhienRead.optBoolean("success", false)) exitThreshold = Math.min(10000, exitThreshold + 2000);
    JSONObject exitProbe = thresholdRoll("exitProbe", 10000, exitThreshold, exitIntent && (physical || search),
      anNhienRead.optBoolean("success", false) ? " discovery clue +2% An Nhiên +20% đọc dấu Exit" : (anNhienFollowing ? " discovery clue +2% An Nhiên" : " discovery clue"));
    rolls.put("exitProbe", exitProbe);
    // Compatibility alias for the older Android reducer. Both keys point to the exact same locked result; no reroll occurs.
    rolls.put("levelExit", new JSONObject(exitProbe.toString()).put("label", "levelExit"));
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

  private JSONArray sanitizedInventory(JSONArray current, JSONArray proposed, JSONObject rolls, String action) throws Exception {
    if (proposed == null) return current == null ? new JSONArray() : new JSONArray(current.toString());
    JSONArray safe = new JSONArray();
    JSONObject lootRoll = rolls.optJSONObject("loot");
    JSONObject waterRoll = rolls.optJSONObject("almondWater");
    boolean lootEligible = lootRoll != null && lootRoll.optBoolean("eligible", false);
    boolean waterEligible = waterRoll != null && waterRoll.optBoolean("eligible", false);
    boolean acquisitionIntent = containsAny(action,
      "nhặt", "lấy", "cầm", "thu hồi", "tịch thu", "nhận", "cất", "bỏ vào", "đưa vào omnivault", "store", "sao chép", "copy");

    for (int i = 0; i < proposed.length(); i++) {
      Object item = proposed.opt(i);
      String name = itemName(item);
      boolean existing = arrayHasName(current, name);
      boolean madGod = lower(name).contains("madgod");
      boolean almond = lower(name).contains("almond water");
      boolean allowed;

      if (existing) {
        // Existing ownership may change quantity/state, including Omnivault storage/copy/use.
        allowed = true;
      } else if (acquisitionIntent) {
        // The GM may add an item explicitly acquired from the established scene/state.
        // The prompt below remains responsible for rejecting nonexistent or invented objects.
        allowed = true;
      } else if (madGod) {
        allowed = rollSuccess(rolls, "madGodSet");
      } else if (almond) {
        // If this was not a water-discovery roll, do not delete established/passed-in water.
        allowed = !waterEligible || rollSuccess(rolls, "almondWater");
      } else {
        // New discovered loot still requires the loot roll when a search is actually happening.
        allowed = !lootEligible || rollSuccess(rolls, "loot");
      }

      if (allowed) safe.put(item);
    }
    return safe;
  }

  private JSONArray sanitizedParty(JSONArray current, JSONArray proposed, JSONObject rolls) throws Exception {
    if (proposed == null) return current == null ? new JSONArray() : new JSONArray(current.toString());
    JSONArray safe = current == null ? new JSONArray() : new JSONArray(current.toString());
    for (int i = 0; i < proposed.length(); i++) {
      Object member = proposed.opt(i);
      String name = itemName(member);
      if (arrayHasName(safe, name)) continue;
      String lowered = lower(name);
      boolean allowed = (lowered.contains("iris") && rollSuccess(rolls, "irisReunion")) ||
        (lowered.contains("syvial") && rollSuccess(rolls, "syvialReunion")) ||
        (!lowered.contains("iris") && !lowered.contains("syvial") && rollSuccess(rolls, "survivor"));
      if (allowed) safe.put(member);
    }
    return safe;
  }

  private void mergeObjectDeep(JSONObject target, JSONObject patch) throws Exception {
    if (patch == null) return;
    Iterator<String> keys = patch.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      Object value = patch.opt(key);
      if (value instanceof JSONObject && target.opt(key) instanceof JSONObject) {
        mergeObjectDeep(target.optJSONObject(key), (JSONObject)value);
      } else {
        target.put(key, value);
      }
    }
  }

  private boolean sameStringSet(JSONArray left, JSONArray right) {
    java.util.HashSet<String> a = new java.util.HashSet<>();
    java.util.HashSet<String> b = new java.util.HashSet<>();
    if (left != null) for (int i = 0; i < left.length(); i++) a.add(left.optString(i, "").trim());
    if (right != null) for (int i = 0; i < right.length(); i++) b.add(right.optString(i, "").trim());
    a.remove(""); b.remove("");
    return a.equals(b);
  }

  private boolean containsInternalNarrativeTerm(String reply) {
    String text = reply == null ? "" : reply.toLowerCase(java.util.Locale.ROOT);
    String[] forbidden = {
      "core", "inventoryengine", "engine", "blueprint", "commit", "registered level",
      "level instance", "validator", "action rule", "escape blueprint", "internal id"
    };
    for (String term : forbidden) if (text.contains(term)) return true;
    return false;
  }

  private String stripVisibleEvidence(String cue, JSONArray evidenceTexts) {
    String result = cue == null ? "" : cue.trim();
    if (evidenceTexts != null) {
      for (int i = 0; i < evidenceTexts.length(); i++) {
        String evidence = evidenceTexts.optString(i, "").trim();
        if (!evidence.isEmpty()) result = result.replace(evidence, " ");
      }
    }
    return result.replaceAll("\\s+", " ").trim();
  }

  private String registeredNarrativeFallback(JSONObject resolved) {
    // The Core reply already contains this turn's observations exactly once.
    String cue = resolved.optString("reply", "").trim();
    if (!cue.isEmpty()) return cue.replace("Kai", "Bạn");
    StringBuilder grounded = new StringBuilder("Bạn vẫn ở nguyên khu vực.");
    JSONArray evidenceTexts = resolved.optJSONArray("evidenceTexts");
    if (evidenceTexts != null) {
      for (int i = 0; i < evidenceTexts.length(); i++) {
        String evidence = evidenceTexts.optString(i, "").trim();
        if (evidence.isEmpty()) continue;
        if (grounded.length() > 0) grounded.append(' ');
        grounded.append(evidence);
      }
    }
    return grounded.toString().trim();
  }

  private String narrateRegisteredOutcome(String actionKind, String action, JSONObject state, JSONObject resolved) {
    String fallback = registeredNarrativeFallback(resolved);
    // A failed experiment must not become successful movement in free-form prose.
    if (!resolved.optBoolean("progressed", false)) return fallback;
    try {
      boolean progressed = resolved.optBoolean("progressed", false);
      boolean escaped = resolved.optBoolean("escaped", false);
      String location = state.optString("location", "").trim();
      JSONArray evidenceIds = resolved.optJSONArray("evidenceIds");
      if (evidenceIds == null) evidenceIds = new JSONArray();
      JSONArray evidenceTexts = resolved.optJSONArray("evidenceTexts");
      if (evidenceTexts == null) evidenceTexts = new JSONArray();
      String cue = stripVisibleEvidence(resolved.optString("reply", ""), evidenceTexts);
      String storyContext = campaignStoryBeatPrompt(state);

      JSONObject visible = new JSONObject()
        .put("actionType", actionKind == null ? "" : actionKind)
        .put("playerAction", action == null ? "" : action)
        .put("progressed", progressed)
        .put("escaped", escaped)
        .put("location", location)
        .put("narrativeCue", cue)
        .put("storyContext", storyContext)
        .put("evidenceIds", evidenceIds)
        .put("evidenceTexts", evidenceTexts)
        .put("discoveryProjection", resolved.optJSONObject("discoveryProjection") != null
          ? resolved.optJSONObject("discoveryProjection") : new JSONObject());

      String prompt = "Bạn là Narrative Engine của một text game Backrooms. "
        + "Kết quả gameplay bên dưới đã được xác định trước và là sự thật duy nhất của lượt này. "
        + "Chỉ kể lại kết quả đó bằng tiếng Việt tự nhiên, giàu hình ảnh nhưng gọn, tối đa 4 câu. "
        + "POV HARD LOCK: người chơi nhập vai trực tiếp Kai Akechi. Mọi văn xuôi gameplay phải dùng ngôi thứ hai giới hạn và gọi Kai là 'bạn'. "
        + "Không được gọi nhân vật người chơi là 'Kai', 'hắn', 'anh ta' hoặc chuyển sang ngôi thứ nhất 'tôi', trừ lời thoại trực tiếp có người nói rõ ràng. "
        + "Không tự viết suy nghĩ, quyết định, lời thoại hoặc hành động có chủ ý mới thay người chơi. "
        + "storyContext là khóa cốt truyện cũ/canon dùng ở hậu trường: phải bám vào nhưng không được nhắc tên prompt, state, canon, Core hay hệ thống trong lời kể. "
        + "Giữ đúng giọng nhân vật, quan hệ và cách xưng hô đã có; không biến lời kể thành báo cáo kỹ thuật hoặc câu xác nhận máy móc. "
        + "Không được tạo thêm vật phẩm, Entity/NPC, thương tích, combat outcome, cửa/lối đi, vị trí hay chuyển Level không có trong dữ liệu. "
        + "Không được thay đổi progressed/escaped/location/evidenceIds. "
        + "Khi areaId là 0 hoặc epsilon, cảnh vẫn là giấy tường vàng, thảm ẩm và đèn huỳnh quang; không dùng gara hay bãi đỗ xe của Level 1. "
        + "Manh mối chỉ là chi tiết cảm giác: không gọi là bằng chứng, dấu hiệu đúng đường hay kết luận cần làm gì để thoát. "
        + "Không nhắc Core, Engine, blueprint, commit, validator, rule, prompt hoặc ID nội bộ trong reply. "
        + "Không diễn giải hoặc chép lại evidenceTexts trong reply vì ứng dụng sẽ gắn nguyên văn chúng sau phần kể. "
        + "Chỉ trả JSON: {\"reply\":\"...\",\"claims\":{\"progressed\":true|false,\"escaped\":true|false,\"location\":\"...\",\"evidenceIds\":[],\"introducedItem\":false,\"introducedEntity\":false}}.\n"
        + "DISCOVERY_SEMANTIC_POLICY: Treat discoveryProjection evidence as observation only; never infer an escape route, required action, or hidden fact from it. "
        + "NPC puzzle statements are limited to discoveryProjection.allowedNpcStatements; when that array is empty, reveal no puzzle information through NPC dialogue. "
        + "MAIN_STORY_CONTEXT=" + campaignStoryBeatPrompt(state) + "\n"
        + "VISIBLE_RESOLVED_OUTCOME=" + visible.toString();

      JSONObject generated = parseModelJson(generateText(prompt));
      String reply = generated.optString("reply", "").trim();
      JSONObject claims = generated.optJSONObject("claims");
      if (reply.isEmpty() || claims == null) throw new Exception("registered_narrative_shape_invalid");
      if (containsInternalNarrativeTerm(reply)) throw new Exception("registered_narrative_internal_term");
      if (com.rabpit.backroom.core.LevelNarrativePolicy.contradictsArea(currentStoryAreaId(state), reply))
        throw new Exception("registered_narrative_area_scenery_mismatch");
      if (claims.optBoolean("progressed", !progressed) != progressed) throw new Exception("registered_narrative_progress_mismatch");
      if (claims.optBoolean("escaped", !escaped) != escaped) throw new Exception("registered_narrative_escape_mismatch");
      if (!claims.optString("location", "").trim().equals(location)) throw new Exception("registered_narrative_location_mismatch");
      if (!sameStringSet(claims.optJSONArray("evidenceIds"), evidenceIds)) throw new Exception("registered_narrative_evidence_mismatch");
      if (claims.optBoolean("introducedItem", true) || claims.optBoolean("introducedEntity", true)) throw new Exception("registered_narrative_ungrounded_claim");
      for (int i = 0; i < evidenceIds.length(); i++) {
        String id = evidenceIds.optString(i, "").trim();
        if (!id.isEmpty() && reply.contains(id)) throw new Exception("registered_narrative_internal_id");
      }

      StringBuilder grounded = new StringBuilder(reply);
      for (int i = 0; i < evidenceTexts.length(); i++) {
        String evidence = evidenceTexts.optString(i, "").trim();
        if (evidence.isEmpty()) continue;
        if (grounded.length() > 0) grounded.append(' ');
        grounded.append(evidence);
      }
      return grounded.toString().trim();
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private void appendRegisteredNarrativeLog(JSONObject state, String action, String reply) throws Exception {
    JSONArray log = state.optJSONArray("log");
    if (log == null) { log = new JSONArray(); state.put("log", log); }
    log.put(new JSONObject().put("role", "player").put("text", action == null ? "" : action));
    log.put(new JSONObject().put("role", "gm").put("text", reply == null ? "" : reply));
  }

  private boolean hasIncompleteRegisteredLevel() {
    try {
      JSONObject core = new JSONObject(requireGameCore().currentCoreState());
      JSONObject instance = core.optJSONObject("levelInstance");
      return instance != null && !instance.optBoolean("completed", false);
    } catch (Exception ignored) {
      return false;
    }
  }

  private String registeredLevelNarrativeLock() {
    if (!hasIncompleteRegisteredLevel()) return "";
    return " REGISTERED LEVEL HARD LOCK: Core xác nhận Level hiện tại chưa hoàn tất. "
      + "Không mô tả người chơi đã sang Level hoặc khu của Level khác; không dẫn tới môi trường của Level khác; "
      + "không thay đổi title/location/level/flags điều hướng cho đến khi Core trả escaped=true.";
  }

  private boolean attemptsRegisteredNavigation(JSONObject before, JSONObject generated) {
    if (!hasIncompleteRegisteredLevel()) return false;
    JSONArray ops = generated.optJSONArray("ops");
    if (ops != null) {
      for (int i = 0; i < ops.length(); i++) {
        JSONObject op = ops.optJSONObject(i);
        if (op == null) continue;
        String type = lower(op.optString("type", "")).trim();
        if (type.equals("set_level")) {
          JSONObject proposed = op.optJSONObject("level");
          if (proposed != null && proposed.optInt("number", currentLevel(before)) != currentLevel(before)) return true;
        } else if (type.equals("set_location")) {
          String value = op.optString("value", "");
          if (!value.equals(before.optString("location", ""))) return true;
        } else if (type.equals("flag_patch")) {
          String root = lower(op.optString("root", "")).trim();
          if (root.equals("exploration") || root.equals("currentlevel") || root.equals("visualareakey") || root.equals("visualeventkey")) return true;
        }
      }
    }
    JSONObject proposedLevel = generated.optJSONObject("level");
    if (proposedLevel != null && proposedLevel.optInt("number", currentLevel(before)) != currentLevel(before)) return true;
    if (generated.has("title") && !generated.optString("title", "").equals(before.optString("title", ""))) return true;
    return generated.has("location") && !generated.optString("location", "").equals(before.optString("location", ""));
  }

  private boolean canTransition(JSONObject before, JSONObject rolls) {
    if (hasIncompleteRegisteredLevel()) return false;
    if (!hasNextLinearArea(before)) return false;
    JSONObject exploration = before.optJSONObject("flags") != null ? before.optJSONObject("flags").optJSONObject("exploration") : null;
    String confirmedExit = exploration != null ? exploration.optString("confirmedExit", "") : "";
    JSONObject currentExitRoll = rolls.optJSONObject("levelExit");
    boolean currentExitIntent = currentExitRoll != null && currentExitRoll.optBoolean("eligible", false);
    boolean exitFound = rollSuccess(rolls, "levelExit") || (currentExitIntent && confirmedExit != null && !confirmedExit.trim().isEmpty());
    return exitFound && progressionReady(before);
  }

  private JSONObject sanitizedFlags(JSONObject current, JSONObject proposed, JSONObject rolls, boolean transitionAccepted) throws Exception {
    JSONObject safe = current == null ? new JSONObject() : new JSONObject(current.toString());
    if (proposed == null) return safe;
    JSONObject patch = new JSONObject(proposed.toString());
    patch.remove("lastRolls");
    if (!transitionAccepted) patch.remove("currentLevel");

    if (patch.optJSONObject("madGod") != null) {
      JSONObject oldMadGod = safe.optJSONObject("madGod");
      JSONObject newMadGod = patch.optJSONObject("madGod");
      if ((oldMadGod == null || !oldMadGod.optBoolean("spawned", false)) && newMadGod.optBoolean("spawned", false) && !rollSuccess(rolls, "madGodSet")) {
        patch.remove("madGod");
      }
    }
    if (patch.optJSONObject("iris") != null) {
      JSONObject oldIris = safe.optJSONObject("iris");
      JSONObject newIris = patch.optJSONObject("iris");
      if ((oldIris == null || !oldIris.optBoolean("present", false)) && newIris.optBoolean("present", false) && !rollSuccess(rolls, "irisReunion")) patch.remove("iris");
    }
    if (patch.optJSONObject("syvial") != null) {
      JSONObject oldSyvial = safe.optJSONObject("syvial");
      JSONObject newSyvial = patch.optJSONObject("syvial");
      if ((oldSyvial == null || !oldSyvial.optBoolean("present", false)) && newSyvial.optBoolean("present", false) && !rollSuccess(rolls, "syvialReunion")) patch.remove("syvial");
    }
    mergeObjectDeep(safe, patch);
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
    else if (kind.equals("character_encounter")) allowed = rollSuccess(rolls, "anNhienEncounter") || rollSuccess(rolls, "survivor") || rollSuccess(rolls, "irisReunion") || rollSuccess(rolls, "syvialReunion");
    else if (kind.equals("major_event")) allowed = rollSuccess(rolls, "madGodSet");
    else if (kind.equals("special_area")) allowed = true;
    if (!allowed) return safe;
    return new JSONObject().put("shouldGenerate", true).put("kind", kind).put("reason", event.optString("reason", ""));
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
    return actionOmnivault(action) || containsAny(action, "nhặt", "lấy", "cầm", "thu hồi", "nhận", "cất", "inventory", "đồ", "vật phẩm", "chai", "nước", "almond", "loot", "crate", "liquid pain", "greek fire", "madgod");
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
    boolean item = actionItem(action) || rollSuccess(rolls, "loot") || rollSuccess(rolls, "almondWater") || rollSuccess(rolls, "madGodSet");
    if (entity || item) {
      String resources = canonSection(DRIVE_CANON, "ENTITY VÀ TÀI NGUYÊN", "IRIS / SYVIAL");
      if (!resources.isEmpty()) out.append("\n\n").append(resources);
    }

    boolean character = actionDialogue(action) || presentCharacter(state, "iris") || presentCharacter(state, "syvial") ||
      rollSuccess(rolls, "irisReunion") || rollSuccess(rolls, "syvialReunion");
    if (character) {
      String characterCanon = canonSection(DRIVE_CANON, "IRIS / SYVIAL", "GAMEPLAY HARD LOCK");
      if (!characterCanon.isEmpty()) out.append("\n\n").append(characterCanon);
    } else {
      out.append("\n\nIRIS / SYVIAL SEPARATION KERNEL\n- Khi continuity còn SEPARATED, Kai không biết vị trí/tình trạng hiện tại của Iris hoặc Syvial và không được dùng dữ kiện hậu trường về họ.");
    }
    out.append("\n\n").append(gameplay);
    return out.toString();
  }

  private String compactKaiCanon(String action) {
    StringBuilder out = new StringBuilder();
    out.append(canonSection(KAI_CANON, "1. ĐỊNH DANH", "2. NGOẠI HÌNH"));
    out.append("\n\n").append(canonSection(KAI_CANON, "3. TÍNH CÁCH / NGUYÊN TẮC", "4. PHONG CÁCH GIAO TIẾP"));
    out.append("\n\n").append(canonSection(KAI_CANON, "4. PHONG CÁCH GIAO TIẾP", "5. NĂNG LỰC CHIẾN ĐẤU"));
    out.append("\n\n").append(canonSection(KAI_CANON, "5. NĂNG LỰC CHIẾN ĐẤU", "6. SPARDA CORE"));
    out.append("\n\n").append(canonSection(KAI_CANON, "6. SPARDA CORE", "7. DEVIL TRIGGER"));
    out.append("\n\n").append(canonSection(KAI_CANON, "10. BLACKBLOOD ARMOR & MODULES", "11. OMNIVAULT RING / NHẪN VẠN TÀNG"));
    out.append("\n\n").append(canonSection(KAI_CANON, "13. GIỚI HẠN THỰC SỰ", "14. ACTION LOCKS / CẤM MODEL TỰ BỊA"));
    out.append("\n\n").append(canonSection(KAI_CANON, "14. ACTION LOCKS / CẤM MODEL TỰ BỊA", "END OF KAI OPERATIONAL CODEX"));
    if (actionCombat(action)) {
      out.append("\n\n").append(canonSection(KAI_CANON, "7. DEVIL TRIGGER", "10. BLACKBLOOD ARMOR & MODULES"));
      out.append("\n\n").append(canonSection(KAI_CANON, "12. PHONG CÁCH CHIẾN ĐẤU", "13. GIỚI HẠN THỰC SỰ"));
    }
    if (actionOmnivault(action) || actionItem(action)) {
      out.append("\n\n").append(canonSection(KAI_CANON, "11. OMNIVAULT RING / NHẪN VẠN TÀNG", "12. PHONG CÁCH CHIẾN ĐẤU"));
    }
    return out.toString();
  }

  private JSONObject compactStateForPrompt(JSONObject state) throws Exception {
    JSONObject compact = new JSONObject(state.toString());
    compact.remove("snapshotUrl");
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
    if (value.contains("iris")) return presentCharacter(before, "iris") || rollSuccess(rolls, "irisReunion");
    if (value.contains("syvial")) return presentCharacter(before, "syvial") || rollSuccess(rolls, "syvialReunion");
    return rollSuccess(rolls, "survivor");
  }

  private boolean flagRootAllowed(JSONObject before, String root, JSONObject rolls) {
    if (root == null) return false;
    if (root.equals("exploration") || root.equals("communication") || root.equals("omnivault") || root.equals("visualAreaKey") ||
        root.equals("visualEventKey") || root.equals("reunionPath")) return true;
    if (root.equals("iris")) return presentCharacter(before, "iris") || rollSuccess(rolls, "irisReunion");
    if (root.equals("syvial")) return presentCharacter(before, "syvial") || rollSuccess(rolls, "syvialReunion");
    if (root.equals("jeff")) {
      JSONObject flags = before.optJSONObject("flags");
      JSONObject jeff = flags != null ? flags.optJSONObject("jeff") : null;
      boolean established = jeff != null && (jeff.optBoolean("present", false) || jeff.optBoolean("spawned", false));
      return established || (rollSuccess(rolls, "entityEncounter") && "jeff_the_killer".equals(rolls.optString("roamingEntityKey", "")));
    }
    if (root.equals("jane")) {
      JSONObject flags = before.optJSONObject("flags");
      JSONObject jane = flags != null ? flags.optJSONObject("jane") : null;
      boolean established = jane != null && (jane.optBoolean("present", false) || jane.optBoolean("spawned", false));
      return established || (rollSuccess(rolls, "entityEncounter") && "jane_the_killer".equals(rolls.optString("roamingEntityKey", "")));
    }
    if (root.equals("entitiesConfirmedLocal") || root.equals("entityEncounterKey")) {
      JSONObject flags = before.optJSONObject("flags");
      if (root.equals("entityEncounterKey")) {
        if ((rollSuccess(rolls, "entityEncounter") && "jeff_the_killer".equals(rolls.optString("roamingEntityKey", ""))) || (rollSuccess(rolls, "entityEncounter") && "jane_the_killer".equals(rolls.optString("roamingEntityKey", "")))) return true;
        if (flags != null) {
          if (!flags.optString("entityEncounterKey", "").trim().isEmpty()) return true;
          JSONObject jeff = flags.optJSONObject("jeff");
          if (jeff != null && (jeff.optBoolean("present", false) || jeff.optBoolean("spawned", false))) return true;
          JSONObject jane = flags.optJSONObject("jane");
          if (jane != null && (jane.optBoolean("present", false) || jane.optBoolean("spawned", false))) return true;
        }
      }
      return rollSuccess(rolls, "entityEncounter") || (flags != null && flags.optInt("entitiesConfirmedLocal", 0) > 0);
    }
    if (root.equals("survivorRegistry") || root.equals("survivorsConfirmed")) {
      JSONObject flags = before.optJSONObject("flags");
      return rollSuccess(rolls, "survivor") || (flags != null && flags.optInt("survivorsConfirmed", 0) > 0);
    }
    if (root.equals("madGod")) {
      JSONObject flags = before.optJSONObject("flags");
      JSONObject madGod = flags != null ? flags.optJSONObject("madGod") : null;
      return rollSuccess(rolls, "madGodSet") || (madGod != null && madGod.optBoolean("spawned", false));
    }
    return false;
  }

  private void reconcileNarratedWorldItems(JSONObject state, String reply) throws Exception {
    JSONObject flags = state.optJSONObject("flags");
    JSONArray inventory = state.optJSONArray("inventory");
    String updatedFlags = com.rabpit.backroom.core.WorldItemLedger.INSTANCE.reconcileNarrative(
      flags != null ? flags.toString() : null,
      state.optString("location", ""),
      reply,
      inventory != null ? inventory.toString() : "[]"
    );
    state.put("flags", new JSONObject(updatedFlags));
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

      if (type.equals("set_level")) {
        JSONObject level = op.optJSONObject("level");
        if (level == null || !canTransition(before, rolls)) continue;
        int number = Math.max(0, Math.min(6, level.optInt("number", currentLevel(before))));
        if (number == currentLevel(before)) continue;
        JSONObject safeLevel = new JSONObject().put("number", number).put("name", levelName(number));
        state.put("level", safeLevel).put("title", "Level " + number + " – " + levelName(number));
        continue;
      }

      if (type.equals("patch_player")) {
        JSONObject patch = op.optJSONObject("patch");
        if (patch == null) continue;
        JSONObject current = state.optJSONObject("player");
        if (current == null) current = new JSONObject();
        boolean worldConsequence = rollSuccess(rolls, "hazard") || rollSuccess(rolls, "entityEncounter");
        boolean recoveryIntent = containsAny(action, "ăn", "uống", "nghỉ", "ngủ", "băng bó", "chữa", "hồi phục", "eat", "drink", "rest", "sleep", "heal");
        boolean gearIntent = containsAny(action, "rút", "cất", "trang bị", "mặc", "cởi", "tháo", "đeo", "draw", "equip", "unequip", "wear");
        if (patch.has("hp") && current.has("hp") && !current.isNull("hp")) {
          double beforeHp = current.optDouble("hp", Double.NaN);
          double afterHp = patch.optDouble("hp", Double.NaN);
          if (!Double.isNaN(beforeHp) && !Double.isNaN(afterHp) && afterHp >= 0 &&
              ((afterHp < beforeHp && worldConsequence) || (afterHp >= beforeHp && recoveryIntent))) current.put("hp", afterHp);
        }
        if (patch.has("condition") && (worldConsequence || recoveryIntent)) current.put("condition", patch.optString("condition", current.optString("condition", "")));
        if (patch.optJSONObject("needs") != null && recoveryIntent) {
          JSONObject needs = current.optJSONObject("needs");
          if (needs == null) needs = new JSONObject();
          for (String needKey : new String[] {"thirst", "hunger", "fatigue", "sleepDeprivation"}) {
            if (patch.optJSONObject("needs").has(needKey)) needs.put(needKey, patch.optJSONObject("needs").get(needKey));
          }
          current.put("needs", needs);
        }
        JSONArray ownedGear = state.optJSONArray("inventory");
        for (String key : new String[] {"weapon", "armor"}) {
          if (!patch.has(key) || !gearIntent) continue;
          String proposedGear = patch.optString(key, "").trim();
          boolean owned = false;
          if (ownedGear != null) for (int gearIndex = 0; gearIndex < ownedGear.length(); gearIndex++) {
            String ownedName = itemName(ownedGear.opt(gearIndex));
            if (!ownedName.isEmpty() && lower(proposedGear).contains(lower(ownedName))) { owned = true; break; }
          }
          if (owned) current.put(key, proposedGear);
        }
        JSONObject oldPlayer = before.optJSONObject("player");
        current.put("name", oldPlayer != null ? oldPlayer.optString("name", "Kai Akechi") : "Kai Akechi");
        if (oldPlayer != null && oldPlayer.has("codename")) current.put("codename", oldPlayer.get("codename"));
        state.put("player", current);
        continue;
      }

      if (type.equals("world_item_upsert")) {
        JSONObject item = op.optJSONObject("item");
        if (item == null) continue;
        JSONObject currentFlags = state.optJSONObject("flags");
        String updatedFlags = com.rabpit.backroom.core.WorldItemLedger.INSTANCE.record(
          currentFlags != null ? currentFlags.toString() : null,
          state.optString("location", before.optString("location", "")),
          item.toString()
        );
        if (updatedFlags != null) state.put("flags", new JSONObject(updatedFlags));
        continue;
      }

      if (type.equals("inventory_upsert")) {
        JSONObject item = op.optJSONObject("item");
        if (item == null) continue;
        String name = item.optString("name", "").trim();
        if (name.isEmpty()) continue;
        JSONArray inventory = state.optJSONArray("inventory");
        if (inventory == null) inventory = new JSONArray();
        int existing = arrayIndexByName(inventory, name);
        boolean madGod = lower(name).contains("madgod");
        boolean almond = lower(name).contains("almond water");
        boolean allowedNew = false;
        JSONObject beforeFlagsForItem = before.optJSONObject("flags");
        JSONObject beforeMadGodForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("madGod") : null;
        JSONObject explorationForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("exploration") : null;
        JSONObject omnivaultForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("omnivault") : null;
        boolean establishedStructured = false;
        if (explorationForItem != null) establishedStructured = lower(explorationForItem.toString()).contains(lower(name));
        if (!establishedStructured && omnivaultForItem != null) establishedStructured = lower(omnivaultForItem.toString()).contains(lower(name));
        if (!establishedStructured && beforeMadGodForItem != null) establishedStructured = lower(beforeMadGodForItem.toString()).contains(lower(name));
        boolean madGodAlreadySpawned = beforeMadGodForItem != null && beforeMadGodForItem.optBoolean("spawned", false);
        String acquisitionBasis = lower(op.optString("basis", "")).trim();
        boolean worldAcquisition = acquisitionBasis.equals("world_consequence");
        boolean gmGain = acquisitionBasis.equals("gm_gain");
        boolean directAcquisition = acquisitionIntent(action);
        boolean copyIntent = containsAny(action, "copy", "sao chép", "nhân bản", "tạo thêm", "tạo ra thêm", "nhân thêm");
        boolean almondRoll = rollSuccess(rolls, "almondWater");
        boolean lootRoll = rollSuccess(rolls, "loot");
        if (existing >= 0) allowedNew = true;
        else if (madGod) allowedNew = (directAcquisition || gmGain) && madGodAlreadySpawned && establishedStructured;
        else if (copyIntent) allowedNew = gmGain || (directAcquisition && establishedStructured);
        else if (almond) allowedNew = gmGain || ((directAcquisition || worldAcquisition) && (establishedStructured || almondRoll));
        else allowedNew = gmGain || ((directAcquisition || worldAcquisition) && (establishedStructured || lootRoll));
        int requestedQuantity = Math.max(1, Math.min(999, item.optInt("quantity", 1)));
        if (existing >= 0) {
          if (gmGain) {
            JSONObject previousItem = inventory.optJSONObject(existing);
            JSONObject mergedItem = new JSONObject(item.toString());
            int previousQuantity = previousItem != null ? Math.max(1, previousItem.optInt("quantity", 1)) : 1;
            mergedItem.put("quantity", Math.min(999, previousQuantity + requestedQuantity));
            if (!mergedItem.has("id") && previousItem != null && previousItem.has("id")) mergedItem.put("id", previousItem.get("id"));
            inventory.put(existing, mergedItem);
          } else {
            inventory.put(existing, new JSONObject(item.toString()));
          }
        } else if (allowedNew) {
          JSONObject addedItem = new JSONObject(item.toString());
          if (gmGain) addedItem.put("quantity", requestedQuantity);
          inventory.put(addedItem);
        }
        state.put("inventory", inventory);
        continue;
      }

      if (type.equals("inventory_remove")) {
        String name = op.optString("name", "").trim();
        JSONArray inventory = state.optJSONArray("inventory");
        int existing = arrayIndexByName(inventory, name);
        boolean consequence = "world_consequence".equals(lower(op.optString("basis", ""))) &&
          (rollSuccess(rolls, "hazard") || rollSuccess(rolls, "entityEncounter"));
        // A semantic inference alone never authorizes deletion of owned inventory.
        if (inventory != null && existing >= 0 && (removalIntent(action) || consequence)) inventory.remove(existing);
        continue;
      }

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
        if (root.equals("jeff") && value instanceof JSONObject) {
          JSONObject jeffPatch = (JSONObject)value;
          boolean proposedPresent = jeffPatch.optBoolean("present", false) || jeffPatch.optBoolean("spawned", false);
          JSONObject beforeJeff = before.optJSONObject("flags") != null ? before.optJSONObject("flags").optJSONObject("jeff") : null;
          boolean alreadyPresent = beforeJeff != null && (beforeJeff.optBoolean("present", false) || beforeJeff.optBoolean("spawned", false));
          if (!alreadyPresent && proposedPresent && !(rollSuccess(rolls, "entityEncounter") && "jeff_the_killer".equals(rolls.optString("roamingEntityKey", "")))) continue;
        }
        if (root.equals("jane") && value instanceof JSONObject) {
          JSONObject janePatch = (JSONObject)value;
          boolean proposedPresent = janePatch.optBoolean("present", false) || janePatch.optBoolean("spawned", false);
          JSONObject beforeJane = before.optJSONObject("flags") != null ? before.optJSONObject("flags").optJSONObject("jane") : null;
          boolean alreadyPresent = beforeJane != null && (beforeJane.optBoolean("present", false) || beforeJane.optBoolean("spawned", false));
          if (!alreadyPresent && proposedPresent && !(rollSuccess(rolls, "entityEncounter") && "jane_the_killer".equals(rolls.optString("roamingEntityKey", "")))) continue;
        }
        if (root.equals("exploration") && value instanceof JSONObject) {
          JSONObject patchValue = new JSONObject(value.toString());
          JSONObject beforeExploration = before.optJSONObject("flags") != null ? before.optJSONObject("flags").optJSONObject("exploration") : null;
          String beforeProgress = beforeExploration != null ? beforeExploration.optString("exitProgress", "") : "";
          String afterProgress = patchValue.optString("exitProgress", beforeProgress);
          boolean exitMutation = !afterProgress.equals(beforeProgress) || patchValue.has("exitCandidate");
          if (exitMutation && !rollSuccess(rolls, "levelExit")) continue;
          if (containsAny(afterProgress, "READY", "GUARANTEED", "CONDITION MET", "TRANSITION AVAILABLE") &&
              !containsAny(beforeProgress, "NEAR", "ALMOST", "VERY STRONG")) continue;
          value = patchValue;
        }
        if (root.equals("reunionPath") && value instanceof JSONObject) {
          JSONObject pathPatch = (JSONObject)value;
          if (pathPatch.has("iris") && containsAny(pathPatch.optString("iris", ""), "CONFIRMED", "DIRECT", "ARRIVED", "CONTACT ESTABLISHED") && !rollSuccess(rolls, "irisReunion")) continue;
          if (pathPatch.has("syvial") && containsAny(pathPatch.optString("syvial", ""), "CONFIRMED", "DIRECT", "ARRIVED", "CONTACT ESTABLISHED") && !rollSuccess(rolls, "syvialReunion")) continue;
        }
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
    JSONObject oldFlags = before.optJSONObject("flags");
    JSONObject oldMadGod = oldFlags != null ? oldFlags.optJSONObject("madGod") : null;
    JSONObject madGod = flags.optJSONObject("madGod");
    if (madGod == null) madGod = oldMadGod == null ? new JSONObject() : new JSONObject(oldMadGod.toString());
    if (oldMadGod != null && oldMadGod.optBoolean("spawned", false)) madGod.put("spawned", true);
    else if (rollSuccess(rolls, "madGodSet")) madGod.put("spawned", true).put("discoveryRouteRevealed", true).put("acquired", false);
    flags.put("madGod", madGod).put("lastRolls", rolls);

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
    if (rollSuccess(rolls, "irisReunion")) {
      JSONObject iris = flags.optJSONObject("iris");
      if (iris == null) iris = new JSONObject();
      boolean irisJoined = ensureSpecialFollowerInLegacyParty(state, "iris", "Iris", false);
      iris.put("exists", true)
        .put("encountered", true)
        .put("present", true)
        .put("spawned", true)
        .put("follower", true)
        .put("reunionEligible", false)
        .put("continuity", "REUNITED")
        .put("levelEncountered", currentLevel(before))
        .put("joinPending", !irisJoined);
      flags.put("iris", iris);
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

    if (rollSuccess(rolls, "luciaEncounter")) {
      JSONObject lucia = flags.optJSONObject("lucia");
      if (lucia == null) lucia = new JSONObject();
      boolean luciaJoined = ensureSpecialFollowerInLegacyParty(state, "lucia", "Lucia \"Lục\"", false);
      lucia.put("exists", true)
        .put("encountered", true)
        .put("present", true)
        .put("spawned", true)
        .put("follower", true)
        .put("reunionEligible", false)
        .put("continuity", "RECRUITED_LEVEL_0")
        .put("levelEncountered", 0)
        .put("joinPending", !luciaJoined);
      flags.put("lucia", lucia);
    }

    state.put("flags", flags);
    return state;
  }

  private boolean jsonChanged(Object before, Object after) {
    String left = before == null || before == JSONObject.NULL ? "null" : String.valueOf(before);
    String right = after == null || after == JSONObject.NULL ? "null" : String.valueOf(after);
    return !left.equals(right);
  }

  private int validatedTurnRisk(JSONObject before, JSONObject candidate, JSONObject generated) {
    int score = 0;
    if (currentLevel(before) != currentLevel(candidate)) score += 4;
    if (jsonChanged(before.optJSONArray("party"), candidate.optJSONArray("party"))) score += 3;
    if (jsonChanged(before.optJSONArray("inventory"), candidate.optJSONArray("inventory"))) score += 1;
    if (jsonChanged(before.optJSONObject("player"), candidate.optJSONObject("player"))) score += 1;

    JSONObject beforeFlags = before.optJSONObject("flags");
    JSONObject afterFlags = candidate.optJSONObject("flags");
    if (beforeFlags == null) beforeFlags = new JSONObject();
    if (afterFlags == null) afterFlags = new JSONObject();
    for (String root : new String[] {"iris", "syvial", "survivorRegistry", "entityRegistry", "survivorsConfirmed", "entitiesConfirmedLocal", "madGod", "reunionPath"}) {
      if (jsonChanged(beforeFlags.opt(root), afterFlags.opt(root))) score += 3;
    }
    for (String root : new String[] {"omnivault", "communication", "exploration", "visualAreaKey", "visualEventKey", "entityEncounterKey"}) {
      if (jsonChanged(beforeFlags.opt(root), afterFlags.opt(root))) score += 1;
    }

    String reply = generated.optString("reply", "");
    JSONArray party = before.optJSONArray("party");
    boolean hasParty = party != null && party.length() > 0;
    if (hasParty && containsAny(reply, "biết", "nhớ", "nhận ra", "hiểu rằng", "tiết lộ", "bí mật", "nguồn gốc", "thật ra", "kể rằng", "knows", "knew", "secret", "origin")) score += 2;
    if (hasParty && containsAny(reply, "yêu", "thích", "ghen", "tin tưởng", "phản bội", "người yêu", "hẹn hò", "quan hệ", "love", "trust", "betray", "relationship")) score += 2;
    JSONArray proposed = generated.optJSONArray("ops");
    if (proposed != null && proposed.length() > 0) {
      for (int i = 0; i < Math.min(24, proposed.length()); i++) {
        JSONObject op = proposed.optJSONObject(i);
        if (op == null) continue;
        String type = lower(op.optString("type", ""));
        if (type.equals("set_level") && currentLevel(before) == currentLevel(candidate)) score = Math.max(score, 4);
        if ((type.equals("party_upsert") || type.equals("party_remove")) && !jsonChanged(before.optJSONArray("party"), candidate.optJSONArray("party"))) score = Math.max(score, 4);
        if ((type.equals("inventory_upsert") || type.equals("inventory_remove")) && !jsonChanged(before.optJSONArray("inventory"), candidate.optJSONArray("inventory"))) score = Math.max(score, 4);
        if (type.equals("patch_player") && !jsonChanged(before.optJSONObject("player"), candidate.optJSONObject("player"))) score = Math.max(score, 4);
        if (type.equals("flag_patch")) {
          String root = op.optString("root", "");
          JSONObject beforeFlagsLocal = before.optJSONObject("flags");
          JSONObject afterFlagsLocal = candidate.optJSONObject("flags");
          Object beforeRoot = beforeFlagsLocal != null ? beforeFlagsLocal.opt(root) : null;
          Object afterRoot = afterFlagsLocal != null ? afterFlagsLocal.opt(root) : null;
          if (!jsonChanged(beforeRoot, afterRoot)) score = Math.max(score, 4);
        }
      }
    }
    return score;
  }

  private String auditScopeCanon(JSONObject before, String action, JSONObject rolls, String scope) {
    return com.rabpit.backroom.core.knowledge.KnowledgeContextEngine.build(
      MainActivity.this, before.toString(), action, rolls.toString());
  }

  private JSONObject runAudit(JSONObject before, String action, JSONObject rolls, JSONObject generated, String scope, int excludedWorker) throws Exception {
    String reply = generated.optString("reply", "");
    if (reply.length() > 7000) reply = reply.substring(0, 7000);
    String packet = auditScopeCanon(before, action, rolls, scope);
    String prompt = "Bạn là auditor độc lập cho một lượt text game Backrooms. Không viết lại truyện, không tạo state, không thêm canon. " +
      "Chỉ báo HARD khi có xung đột cụ thể chứng minh được từ KNOWLEDGE PACKET hoặc dice. Không báo lỗi vì sở thích văn phong. Trả DUY NHẤT JSON.\n\n" +
      "AUDIT SCOPE: " + scope + "\n\n" +
      "BUDGETED KNOWLEDGE PACKET:\n" + packet + "\n\n" +
      "LOCKED DICE:\n" + rolls.toString() + "\n\n" +
      "PROPOSED OPS:\n" + (generated.optJSONArray("ops") == null ? "[]" : generated.optJSONArray("ops").toString()) + "\n\n" +
      "PROPOSED REPLY:\n" + reply + "\n\n" +
      "Rule hợp lệ: canon_conflict, knowledge_leak, state_narrative_mismatch, unsupported_claim, character_voice, address_error, competence_suppression, ability_overreach. " +
      "JSON: {\"pass\":true,\"issues\":[]} hoặc {\"pass\":false,\"issues\":[{\"rule\":\"knowledge_leak\",\"severity\":\"hard\",\"claim\":\"...\",\"reason\":\"...\"}]}";
    JSONObject result = parseModelJson(geminiAuditText(prompt, excludedWorker));
    JSONArray issues = result.optJSONArray("issues");
    if (issues == null) issues = new JSONArray();
    return new JSONObject().put("scope", scope).put("issues", issues);
  }

  private JSONArray hardAuditIssues(JSONArray audits) throws Exception {
    JSONArray hard = new JSONArray();
    if (audits == null) return hard;
    for (int i = 0; i < audits.length(); i++) {
      JSONObject audit = audits.optJSONObject(i);
      JSONArray issues = audit != null ? audit.optJSONArray("issues") : null;
      if (issues == null) continue;
      for (int j = 0; j < issues.length(); j++) {
        JSONObject issue = issues.optJSONObject(j);
        if (issue != null && "hard".equalsIgnoreCase(issue.optString("severity", ""))) hard.put(issue);
      }
    }
    return hard;
  }

  private JSONArray auditsForRisk(JSONObject before, String action, JSONObject rolls, JSONObject generated, int risk, int writerWorker) throws Exception {
    JSONArray audits = new JSONArray();
    if (risk < 4) return audits;
    if (risk < 7) {
      audits.put(runAudit(before, action, rolls, generated, "canon", writerWorker));
      return audits;
    }

    Future<JSONObject> canon = auditIo.submit(() -> runAudit(before, action, rolls, generated, "canon", writerWorker));
    Future<JSONObject> character = auditIo.submit(() -> runAudit(before, action, rolls, generated, "character", writerWorker));
    audits.put(canon.get());
    audits.put(character.get());
    return audits;
  }

  private JSONArray rejectedOperationIssuesAndroid(JSONObject before, JSONObject candidate, JSONObject generated) throws Exception {
    JSONArray issues = new JSONArray();
    JSONArray proposed = generated.optJSONArray("ops");
    if (proposed == null) return issues;
    JSONObject beforeFlags = before.optJSONObject("flags");
    JSONObject afterFlags = candidate.optJSONObject("flags");
    for (int i = 0; i < Math.min(24, proposed.length()); i++) {
      JSONObject op = proposed.optJSONObject(i);
      if (op == null) continue;
      String type = lower(op.optString("type", ""));
      boolean rejected = false;
      if (type.equals("set_level")) rejected = currentLevel(before) == currentLevel(candidate);
      else if (type.equals("set_location")) {
        String requested = op.optString("value", "").trim();
        rejected = !requested.isEmpty() && !requested.equals(candidate.optString("location", ""));
      } else if (type.equals("party_upsert") || type.equals("party_remove")) {
        rejected = !jsonChanged(before.optJSONArray("party"), candidate.optJSONArray("party"));
      } else if (type.equals("inventory_upsert") || type.equals("inventory_remove")) {
        rejected = !jsonChanged(before.optJSONArray("inventory"), candidate.optJSONArray("inventory"));
      } else if (type.equals("world_item_upsert")) {
        Object beforeWorldItems = beforeFlags != null ? beforeFlags.opt("worldItems") : null;
        Object afterWorldItems = afterFlags != null ? afterFlags.opt("worldItems") : null;
        rejected = !jsonChanged(beforeWorldItems, afterWorldItems);
      } else if (type.equals("patch_player")) {
        rejected = !jsonChanged(before.optJSONObject("player"), candidate.optJSONObject("player"));
      } else if (type.equals("flag_patch")) {
        String root = op.optString("root", "");
        Object beforeRoot = beforeFlags != null ? beforeFlags.opt(root) : null;
        Object afterRoot = afterFlags != null ? afterFlags.opt(root) : null;
        rejected = !jsonChanged(beforeRoot, afterRoot);
      }
      if (rejected) {
        issues.put(new JSONObject()
          .put("rule", "state_narrative_mismatch")
          .put("severity", "hard")
          .put("claim", type)
          .put("reason", "Android reducer rejected this proposed state operation. Rewrite reply without narrating the rejected change and omit the invalid op."));
      }
    }
    return issues;
  }

  private void appendIssues(JSONArray target, JSONArray source) throws Exception {
    if (target == null || source == null) return;
    for (int i = 0; i < source.length(); i++) target.put(source.get(i));
  }

  private String writerPrompt(JSONObject before, String action, JSONObject rolls, JSONArray auditFeedback) throws Exception {
    String actionRuntimeContext = requireGameCore().currentActionContext();
    String actionKindForPrompt = new JSONObject(actionRuntimeContext).optString("kind", "EXECUTE");
    String actionDirective = "ACTION TYPE = " + actionKindForPrompt + ". " +
      ("SEARCH".equals(actionKindForPrompt) ? "SEARCH HARD LOCK: khảo sát có hệ thống location hiện tại, không tự chuyển sang location mới; SEARCH vẫn roll entityEncounter theo tỷ lệ Level và có thể khởi tạo roaming Entity mới; vẫn có thể gặp Survivor, tìm resource/clue/hazard/exit evidence nhưng không đảm bảo có kết quả hay loot. " :
       "EXPLORE".equals(actionKindForPrompt) ? "EXPLORE HARD LOCK: chủ động mở rộng known space và có thể đổi location; EXPLORE roll Entity theo cùng cơ chế với SEARCH và EXECUTE; có thể gặp Entity hoặc Survivor, resource/hazard/exit opportunity nhưng không đảm bảo Exit; nếu có lựa chọn định hướng quan trọng thì trả quyền quyết định cho người chơi. " :
       "EXECUTE HARD LOCK: đây là freeform intent của người chơi; phân giải đúng hành động đã nhập, không tự đổi mục tiêu; EXECUTE vẫn roll Entity và có thể khởi tạo roaming encounter mới. ");
    String packet = com.rabpit.backroom.core.knowledge.KnowledgeContextEngine.build(
      MainActivity.this, before.toString(), action, rolls.toString());
    String feedback = auditFeedback != null && auditFeedback.length() > 0
      ? "\n\nAUDIT FEEDBACK HARD — sửa đúng các lỗi này, không thay đổi dữ kiện khác:\n" + auditFeedback.toString()
      : "";
    String healingItemDirective = "HEALING ITEM HARD LOCK: Bandage hồi đúng 15 HP và xử lý Bleeding nhẹ; Antiseptic hồi đúng 10 HP và giảm Infection 50%. Cả hai thuộc chung official 11-item Level loot pool, không có roll riêng, không pity và không tăng/giảm lootThresholds. Chỉ khi loot.success=true mới được tạo cơ hội phát hiện mới; loot thất bại không được bù bằng hai vật phẩm này. Loot success chỉ mở cơ hội nhận biết/tương tác, không tự đặt vật phẩm vào Inventory. Nếu reply xác nhận Kai phát hiện một vật thể hữu hình còn nằm trong môi trường và có thể lấy hoặc quét, bắt buộc ghi vật đó vào flags.worldItems với id, name, quantity, instanceId duy nhất, available=true và metadata cần thiết; chỉ phát hiện không được thêm Inventory. Khi vật là sinh vật hoặc cấu kiện lớn, phải ghi isLiving/isLargeAssembly tương ứng để Omnivault từ chối Scan đúng luật. Khi dùng, hồi không vượt Effective Max HP và không hồi sinh nhân vật 0 HP/DEAD.";
    String luciaScoutDirective = "LUCIA SCOUT PASSIVE HARD LOCK: Khi Lucia \"Lục\" đang ở trong Party, passive Trinh sát chiến trường cộng đúng +5 điểm phần trăm vào generic loot roll hiện có. Không tạo roll vật phẩm riêng, không bỏ qua search eligibility, không tự nhặt vật phẩm và không vượt InventoryPolicy.";
    return actionDirective + "\n" + linearAreaPrompt(before) + registeredLevelNarrativeLock() + "\n" + healingItemDirective + "\n" + luciaScoutDirective + "\n" + "\nLUCIA FOLLOWER HARD LOCK: Lucia \"Lục\", nữ 19 tuổi, con người, binh nhì và chỉ huy cấp tiểu đội đặc nhiệm. luciaEncounter chỉ roll khi EXPLORE ở Level 0, xác suất 50%, và chỉ success=true mới cho cô xuất hiện. Sau lần gặp đầu, không roll lại. Nếu Party còn chỗ cô gia nhập follower; nếu đầy thì giữ present + joinPending, không đuổi thành viên khác. HP nền 100; STR 7, DF 7, AGI 8, CRIT 7. Trang bị đúng 3 slot: M4A1 cá nhân hóa với laser xanh 5mW, dao găm chiến đấu, đồng hồ định vị quân sự mất tín hiệu vệ tinh. Đạn khởi đầu 150 viên gồm 60 đang nạp và 90 dự phòng; đây là nguồn đạn riêng, không chiếm 3 loại vật phẩm quà tặng. Inventory quà tặng tối đa 3 loại, tối đa 100 mỗi loại. Ở Level 0, Lucia chỉ nghi ngờ tiếng động giờ thứ 4 là Hound; không được xác nhận Hound cư trú ở Level 0. Không tự thêm năng lực siêu nhiên hoặc lore.\nACTION_RUNTIME: " + actionRuntimeContext + "\n" +
      "Bạn là Game Master của text game Backrooms. Trả DUY NHẤT JSON hợp lệ, không markdown. " +
      "KNOWLEDGE PACKET là context đã được Context Builder chọn từ in-game database theo state/scene/present actors/action/story. " +
      "Source trace trong packet chỉ dùng hậu trường; không để nhân vật nói tên record/file/anchor. UNKNOWN phải giữ UNKNOWN. " +
      "Người chơi chỉ điều khiển hành động có chủ ý của Kai; GM không tự chọn thay. GAMEPLAY_ROLLS do Android sinh là bất biến. " +
      "POV HARD LOCK: người chơi nhập vai trực tiếp Kai Akechi. Mọi văn xuôi gameplay phải kể ở ngôi thứ hai giới hạn từ trải nghiệm của Kai: gọi Kai là 'bạn' và mô tả những gì bạn trực tiếp thấy, nghe, cảm nhận hoặc có cơ sở biết. Không kể Kai ở ngôi thứ ba bằng 'Kai', 'hắn', 'anh ta' hoặc như một nhân vật đang được quan sát từ bên ngoài, trừ khi đó là lời thoại tự nhiên của NPC đang gọi hoặc nói về Kai. Không tự viết suy nghĩ, quyết định, lời thoại hay hành động có chủ ý mới thay cho người chơi; chỉ thuật lại hậu quả hợp lệ của hành động người chơi đã nhập và các phản ứng ngoài quyền kiểm soát có căn cứ từ state/canon. NPC và Entity vẫn được kể bình thường từ góc nhìn mà Kai có thể nhận biết. " +
      "Bạn KHÔNG được trả state hoàn chỉnh. Chỉ đề xuất state change bằng ops; Android sẽ kiểm và có thể từ chối từng operation. " +
      "Nếu meta=true, chỉ trả thông tin được hỏi, ops=[] và snapshotEvent=false. Không nhắc database/context/state/roll/prompt trong văn xuôi.\n\n" +
      "PROSE RULE: Văn xuôi gameplay phải là tiếng Việt tự nhiên, cụ thể và bám vào trải nghiệm hiện tại; ưu tiên quan sát, hành động và hậu quả hơn câu xác nhận trừu tượng hoặc kiểu hệ thống. Tránh chuỗi câu cụt điện ảnh, lặp ý, giải thích lại điều vừa thể hiện và exposition không cần thiết. Không dùng văn phong để thêm hoặc đổi dữ kiện gameplay/canon đã xác định.\n\n" +
      "BUDGETED KNOWLEDGE PACKET:\n" + packet +
      "\n\nGAMEPLAY_ROLLS:\n" + rolls.toString() +
      "\n\nPLAYER INPUT:\n" + action +
      feedback +
      "\n\nOPERATION TYPES: set_location{value}; set_level{level}; patch_player{patch}; inventory_upsert{item,basis}; inventory_remove{name,basis}; " +
      "party_upsert{member}; party_remove{name}; world_item_upsert{item}; flag_patch{root,value}. " +
      "Chỉ dùng flag root: exploration, communication, iris, syvial, jeff, jane, madGod, omnivault, survivorRegistry, survivorsConfirmed, entitiesConfirmedLocal, visualAreaKey, visualEventKey, entityEncounterKey, reunionPath. " +
      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory. " +
      "WORLD ITEM HARD LOCK: khi reply mô tả một item vật lý đang hiện diện và có thể nhặt/tương tác trong môi trường nhưng Kai CHƯA sở hữu, bắt buộc kèm world_item_upsert{item:{id,name,quantity,metadata}} cho từng item. world_item_upsert chỉ ghi nhận vật đang ở hiện trường, tuyệt đối không tự thêm vào Inventory. Khi Kai thực sự nhặt ở lượt sau, Game State Core sẽ chuyển ledger sang Inventory. " +
      "GM ITEM GAIN HARD LOCK: khi reply xác nhận Kai thực sự nhận, nhặt, loot, được trao hoặc được thưởng một item trong chính lượt này, bắt buộc kèm inventory_upsert với basis:\"gm_gain\". Với gm_gain, item.quantity là số lượng vừa Gain trong lượt, mặc định 1, không phải tổng tồn kho. Không dùng gm_gain cho vật chỉ được nhìn thấy hoặc nhắc tới nhưng chưa thuộc quyền sở hữu của Kai. " +
      "ENTITY OVERLAY HARD LOCK: với Entity đang trực tiếp xuất hiện hoặc đối đầu trong cảnh hiện tại, dùng flag_patch root=entityEncounterKey value=canonical Entity key đúng tên asset bỏ .png, ví dụ hound, smiler, skin-stealer, slenderman, jeff_the_killer, jane_the_killer. Nếu Entity bị tiêu diệt, Kai chạy trốn hoặc thoát khỏi Entity, Entity rời cảnh, biến mất, hoặc không còn trực tiếp hiện diện/đối đầu, bắt buộc đặt entityEncounterKey thành chuỗi rỗng ngay trong lượt đó. entityEncounterKey chỉ là trạng thái hiện diện trực quan hiện tại, không phải lịch sử encounter. Không dùng mã cũ hoặc alias theo Level. " +
      "ROAMING KILLER HARD LOCK: Jeff the Killer và Jane the Killer dùng cùng entityEncounter và cùng roamingEntityKey với mọi Entity khác. Mỗi entityEncounter thành công chỉ chọn đúng một canonical Entity key; không có roll Jeff/Jane độc lập và không được tạo encounter thứ hai trong cùng lượt. " +
      "ENTITY ROAMING HARD LOCK: mọi Entity trong LOCAL ROAMING POOL đều có thể lang thang/incursion qua bất kỳ Level 0-6. Khi rolls.entityEncounter.success=true và rolls.roamingEntityKey có giá trị, encounter thường bắt buộc dùng đúng canonical key đó. LOCAL ROAMING POOL: hound, clump, duller, deathmoth, hostile_faceling, false_puddle, paintings, smiler, skin-stealer, predatory_window, biological_pipeline, wretch, cable_mimic, the_beast_of_level_5, hotel_corpse_lure, slenderman. Jeff the Killer và Jane the Killer nằm trong cùng LOCAL ROAMING POOL và chỉ xuất hiện khi roamingEntityKey chọn đúng canonical key jeff_the_killer hoặc jane_the_killer. " +
      "ENTITY ASSET LOCAL HARD LOCK: hình Entity chỉ lấy từ APK assets/entity qua file:///android_asset/entity/<canonical-key>.png; cấm mã Entity legacy, alias theo Level, manifest từ xa hoặc ảnh Entity từ mạng. " +
      "Khi GAMEPLAY_ROLLS hợp lệ tạo loot/Almond Water và reply xác nhận môi trường hoặc NPC thực sự giao vật đó cho Kai, bắt buộc kèm inventory_upsert với basis:\"world_consequence\" trong cùng response; nếu không có op hợp lệ thì không được kể rằng Kai đã nhận hoặc sở hữu vật. " +
      "JSON bắt buộc: {\"reply\":\"phản hồi Game Master bằng tiếng Việt tự nhiên\",\"ops\":[],\"snapshotEvent\":{\"shouldGenerate\":false,\"kind\":\"\",\"reason\":\"\"}}";
  }

  private JSONArray localKnowledgeIssues(JSONObject before, JSONObject generated) throws Exception {
    JSONObject result = new JSONObject(com.rabpit.backroom.core.knowledge.KnowledgeLocalValidator.validate(
      MainActivity.this, before.toString(), generated.toString()));
    JSONArray issues = result.optJSONArray("issues");
    return issues == null ? new JSONArray() : issues;
  }

  private class GameBridge {
    @JavascriptInterface public String exportCoreState() {
      try { return requireGameCore().currentCoreState(); }
      catch (Exception ignored) { return ""; }
    }

    @JavascriptInterface public boolean restoreCoreState(String coreJson) {
      try { return requireGameCore().restoreCoreState(coreJson); }
      catch (Exception ignored) { return false; }
    }

    @JavascriptInterface public String exportDirectorTelemetry() {
      try { return requireGameCore().exportDirectorTelemetry(); }
      catch (Exception ignored) { return ""; }
    }

    @JavascriptInterface public boolean clearDirectorTelemetry() {
      try { return requireGameCore().clearDirectorTelemetry(); }
      catch (Exception ignored) { return false; }
    }

    @JavascriptInterface public String exportWorldDirectorTelemetry() {
      try { return requireGameCore().exportWorldDirectorTelemetry(); }
      catch (Exception ignored) { return ""; }
    }

    @JavascriptInterface public boolean clearWorldDirectorTelemetry() {
      try { return requireGameCore().clearWorldDirectorTelemetry(); }
      catch (Exception ignored) { return false; }
    }

    @JavascriptInterface public String getPartyDetails(String stateJson) {
      try {
        return new JSONObject()
          .put("ok", true)
          .put("data", new JSONObject(requireGameCore().currentPartyDetails(stateJson)))
          .toString();
      } catch (Exception e) {
        String message = e.getMessage() == null ? "Core unavailable" : e.getMessage();
        return "{\"ok\":false,\"error\":\"CORE_UNAVAILABLE\",\"message\":" + JSONObject.quote(message) + "}";
      }
    }

    @JavascriptInterface public String resetNewGameCore() {
      try {
        return new JSONObject()
          .put("ok", true)
          .put("data", new JSONObject(requireGameCore().resetNewGame()))
          .toString();
      } catch (Exception e) {
        String message = e.getMessage() == null ? "New Game core reset failed" : e.getMessage();
        return "{\"ok\":false,\"error\":\"NEW_GAME_CORE_FAILED\",\"message\":" + JSONObject.quote(message) + "}";
      }
    }

    @JavascriptInterface public void clearCoreState() {
      GameCoreFacade core = gameCoreOrNull();
      if (core != null) core.clear();
    }

    @JavascriptInterface public void submitTurn(String stateJson, String action) {
      submitAction(stateJson, "EXECUTE", action);
    }

    @JavascriptInterface public void submitAction(String stateJson, String actionKind, String action) {
      submitTurnInternal(stateJson, actionKind, action);
    }

    private void submitTurnInternal(String stateJson, String actionKind, String action) {
      io.execute(() -> {
        try {
          JSONObject combatResult = new JSONObject(requireGameCore().processCombat(stateJson, actionKind, action));
          if (combatResult.optBoolean("handled", false)) {
            emit("backroomTurn", combatResult.getJSONObject("state").toString());
            return;
          }
          JSONObject actionStart = new JSONObject(requireGameCore().beginAction(stateJson, actionKind, action));
          if (!actionStart.optBoolean("handled", false)) {
            throw new Exception("Action Runtime từ chối hành động: " + actionStart.optString("error", "action_start_failed"));
          }
          JSONObject generationPlan = new JSONObject(requireGameCore().prepareLevelGeneration(stateJson));
          if (generationPlan.optBoolean("required", false)) {
            String generationLevelId = generationPlan.getString("levelId");
            String generationRunSeed = generationPlan.getString("runSeed");
            JSONObject generationRequest = generationPlan.getJSONObject("request");
            JSONObject generationCommit = null;
            String generationRejection = null;
            try {
              for (int generationAttempt = 0; generationAttempt < 2; generationAttempt++) {
                String generatedRaw = geminiLevelGenerationText(levelGenerationPrompt(generationRequest, generationRejection));
                JSONObject generatedCandidate = parseModelJson(generatedRaw);
                String generatorVersion = "gemini-procedural-v1:" + geminiModelLabel(lastGeminiModel);
                generationCommit = new JSONObject(requireGameCore().commitGeneratedLevelCandidate(
                  generationLevelId, generationRunSeed, generatedCandidate.toString(), generatorVersion));
                if (generationCommit.optBoolean("accepted", false)) break;
                generationRejection = generationCommit.optString("error", "candidate_rejected");
              }
            } catch (Exception generationError) {
              generationRejection = generationError.getMessage();
            }
            if (generationCommit == null || !generationCommit.optBoolean("accepted", false)) {
              JSONObject fallback = new JSONObject(requireGameCore().installDefinitionLevelFallback(generationLevelId, generationRunSeed));
              if (!fallback.optBoolean("accepted", false)) {
                throw new Exception("Không thể khởi tạo Level procedural: " + fallback.optString("error", generationRejection == null ? "generation_failed" : generationRejection));
              }
            }
          }
          JSONObject registeredLevelResult = new JSONObject(requireGameCore().processRegisteredLevelAction(stateJson, actionKind, action));
          if (registeredLevelResult.optBoolean("handled", false)) {
            JSONObject registeredState = registeredLevelResult.getJSONObject("state");
            if (registeredLevelResult.optBoolean("escaped", false) && advanceLinearArea(new JSONObject(stateJson), registeredState)) {
              JSONObject flags = registeredState.optJSONObject("flags");
              JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
              String nextAreaId = exploration != null ? exploration.optString("areaId", "") : "";
              if (!nextAreaId.isEmpty()) requireGameCore().handoffCompletedRegisteredLevel(nextAreaId);
            }
            String registeredReply = narrateRegisteredOutcome(actionKind, action, registeredState, registeredLevelResult);
            appendRegisteredNarrativeLog(registeredState, action, registeredReply);
            emit("backroomTurn", registeredState.toString());
            return;
          }
          JSONObject localResult = new JSONObject(requireGameCore().processRule(stateJson, action));
          if (localResult.optBoolean("handled", false)) {
            try { requireGameCore().abortAction("local_terminal"); } catch (Exception ignored) {}
            emit("backroomTurn", localResult.getJSONObject("state").toString());
            return;
          }
          JSONObject before = new JSONObject(stateJson);
          boolean meta = isMetaAction(action);
          JSONObject rolls = makeGameplayRolls(before, actionKind, action, meta);

          JSONObject generated = parseModelJson(generateText(writerPrompt(before, action, rolls, null)));
          String reply = generated.optString("reply", "").trim();
          if (reply.isEmpty()) throw new Exception("AI trả về phản hồi rỗng, lượt này không được ghi.");
          if (attemptsRegisteredNavigation(before, generated)) {
            reply = "Kai vẫn ở nguyên khu vực. Lối đi trước mặt không thay đổi sau những gì vừa thử.";
          }

          JSONObject candidateState = meta
            ? new JSONObject(before.toString())
            : applyModelOperations(before, generated.optJSONArray("ops"), rolls, action);
          if (!meta) reconcileNarratedWorldItems(candidateState, reply);
          int risk = meta ? 0 : validatedTurnRisk(before, candidateState, generated);
          int writerWorker = lastGeminiWorker;
          JSONArray audits = meta ? new JSONArray() : auditsForRisk(before, action, rolls, generated, risk, writerWorker);
          JSONArray hardIssues = hardAuditIssues(audits);
          if (!meta) appendIssues(hardIssues, rejectedOperationIssuesAndroid(before, candidateState, generated));
          if (!meta) appendIssues(hardIssues, localKnowledgeIssues(before, generated));
          boolean repaired = false;

          if (hardIssues.length() > 0) {
            generated = parseModelJson(generateText(writerPrompt(before, action, rolls, hardIssues)));
            reply = generated.optString("reply", "").trim();
            if (reply.isEmpty()) throw new Exception("AI repair trả phản hồi rỗng; state không được thay đổi.");
            repaired = true;
            candidateState = applyModelOperations(before, generated.optJSONArray("ops"), rolls, action);
            reconcileNarratedWorldItems(candidateState, reply);
            risk = validatedTurnRisk(before, candidateState, generated);
            writerWorker = lastGeminiWorker;
            audits = auditsForRisk(before, action, rolls, generated, risk, writerWorker);
            hardIssues = hardAuditIssues(audits);
            appendIssues(hardIssues, rejectedOperationIssuesAndroid(before, candidateState, generated));
            appendIssues(hardIssues, localKnowledgeIssues(before, generated));
          }

          if (hardIssues.length() > 0) {
            throw new Exception("Lượt chơi không vượt qua kiểm tra canon; state không được thay đổi.");
          }

          reconcileVisualWorldState(before, candidateState, rolls);
          forceEntityEncounterFlag(candidateState, rolls);
          JSONObject coreCommit = new JSONObject(requireGameCore().processValidatedCandidate(before.toString(), candidateState.toString(), action));
          if (!coreCommit.optBoolean("handled", false)) {
            throw new Exception("Game State Core từ chối Gemini delta: " + coreCommit.optString("error", "invalid_delta"));
          }
          candidateState = coreCommit.getJSONObject("state");
          JSONArray gainNotifications = coreCommit.optJSONArray("gainNotifications");

          JSONObject state = candidateState;
          if (!meta) {
            state = new JSONObject(com.rabpit.backroom.core.knowledge.StoryContinuityReducer.apply(
              before.toString(), state.toString(), action));
          }

          int oldLevel = currentLevel(before);
          int proposedLevel = currentLevel(state);
          boolean areaAdvanced = false;
          if (canTransition(before, rolls)) areaAdvanced = advanceLinearArea(before, state);
          if (!areaAdvanced) {
            int currentArea = linearAreaIndex(before);
            stampLinearArea(state, currentArea, false, proposedLevel != oldLevel);
          }
          int newLevel = currentLevel(state);
          boolean levelChanged = oldLevel != newLevel;
          boolean transitionAccepted = !levelChanged || canTransition(before, rolls);
          if (!transitionAccepted) {
            if (before.optJSONObject("level") != null) state.put("level", new JSONObject(before.optJSONObject("level").toString()));
            state.put("title", before.optString("title", "Level " + oldLevel + " – " + levelName(oldLevel)));
            newLevel = oldLevel;
            levelChanged = false;
          }

          if (!meta) {
            state.put("turn", before.optInt("turn", 1) + 1).put("mode", "Single Player: Hard Mode");
            JSONObject flags = state.optJSONObject("flags");
            if (flags == null) flags = new JSONObject();
            flags.put("currentLevel", new JSONObject().put("number", newLevel).put("name", levelName(newLevel)));
            state.put("flags", flags);
            recordLevelProgress(state, before, oldLevel, newLevel, areaAdvanced);
            flags = state.optJSONObject("flags");
            flags.put("lastAudit", new JSONObject()
              .put("risk", risk)
              .put("count", audits.length())
              .put("repaired", repaired));
            state.put("flags", flags);
            state.put("_snapshotEvent", sanitizedSnapshotEvent(generated, rolls, transitionAccepted, levelChanged, false));
          } else {
            state.put("_snapshotEvent", new JSONObject().put("shouldGenerate", false).put("kind", "").put("reason", ""));
          }

          if (com.rabpit.backroom.core.LevelNarrativePolicy.contradictsArea(currentStoryAreaId(state), reply)) {
            reply = "Quanh bạn vẫn là giấy tường vàng, thảm ẩm và tiếng đèn huỳnh quang. Lối đi chưa có thay đổi nào khác.";
          }
          state.put("canonVersion", DRIVE_CANON_VERSION);
          JSONArray log = state.optJSONArray("log");
          if (log == null) log = new JSONArray();
          log.put(new JSONObject().put("role", "player").put("text", action));
          log.put(new JSONObject().put("role", "gm").put("text", reply));
          if (gainNotifications != null) {
            for (int gainIndex = 0; gainIndex < gainNotifications.length(); gainIndex++) {
              JSONObject gain = gainNotifications.optJSONObject(gainIndex);
              if (gain == null) continue;
              String gainName = gain.optString("name", "Item").trim();
              int gainQuantity = Math.max(1, gain.optInt("quantity", 1));
              log.put(new JSONObject().put("role", "gain").put("text", "Gain " + gainName + " ×" + gainQuantity + " Item"));
            }
          }
          state.put("log", log);
          emit("backroomTurn", state.toString());
        } catch (Exception e) {
          try { requireGameCore().markActionRetryableFailure("pipeline_error"); } catch (Exception ignored) {}
          emit("backroomError", e.getMessage() == null ? "Không thể xử lý lượt." : e.getMessage());
        }
      });
    }

    @JavascriptInterface public void requestSnapshot(String stateJson) {
      imageIo.execute(() -> requestSnapshotInternal(stateJson));
    }

    @JavascriptInterface public void requestEntityOverlay(String entityKey) {
      imageIo.execute(() -> {
        try {
          emit("backroomEntityOverlay", resolveEntityOverlay(entityKey).toString());
        } catch (Exception error) {
          try {
            JSONObject payload = new JSONObject()
              .put("entityKey", entityKey == null ? "" : entityKey)
              .put("message", error.getMessage() == null ? "Khong the nap Entity asset local." : error.getMessage());
            emit("backroomEntityOverlayError", payload.toString());
          } catch (Exception ignored) {
            emit("backroomEntityOverlayError", "{\"entityKey\":\"\",\"message\":\"Local Entity asset error\"}");
          }
        }
      });
    }
  }

  private static class SnapshotImage {
    final String data;
    final String mimeType;
    final String model;
    final String provider;
    SnapshotImage(String data, String mimeType) {
      this(data, mimeType, "AI", "AI");
    }
    SnapshotImage(String data, String mimeType, String model, String provider) {
      this.data = data;
      this.mimeType = mimeType == null || mimeType.isEmpty() ? "image/jpeg" : mimeType;
      this.model = model == null || model.isEmpty() ? "AI" : model;
      this.provider = provider == null || provider.isEmpty() ? "AI" : provider;
    }
  }

  private static class HttpError extends Exception {
    final int status;
    HttpError(int status, String message) { super(message); this.status = status; }
  }
}
