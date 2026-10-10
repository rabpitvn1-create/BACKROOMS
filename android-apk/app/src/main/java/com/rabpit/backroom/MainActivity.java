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
  private static final String DRIVE_CANON = "BACKROOMS DRIVE INTEGRATION — R06 / HARD CANON\n\nPHẠM VI\n- Người chơi chỉ quyết định hành động có chủ ý của Cao Minh. Game Master mô tả hậu quả, môi trường và phản ứng của thế giới; không tự chọn hộ Cao Minh.\n- Gameplay dùng điểm nhìn gần của Cao Minh. Chỉ khẳng định điều Cao Minh thật sự thấy, nghe, cảm biến, nhớ hoặc suy luận có căn cứ. Không kể xen cảnh Iris/Syvial khi Cao Minh không thể biết.\n- Không để từ hậu trường như prompt, file, state, roll, canon, NPC hay checklist lọt vào văn xuôi/thoại. Kết quả xúc xắc chỉ là ràng buộc nội bộ.\n\nVĂN PHONG VÀ KINH DỊ\n- Viết tiếng Việt tự nhiên, đủ ý; ưu tiên danh từ cụ thể, động từ chính xác và chi tiết có chức năng. Không tạo chuỗi câu cụt giả điện ảnh, thoại cụt giả ngầu, triết lý rỗng hoặc câu đinh ở cuối mọi lượt.\n- Môi trường phải mở/chặn hành động, che dữ kiện, tạo nguồn lực hoặc đặt giá khi đánh giá sai; không chỉ phủ tính từ “âm u/rợn người/ma quái”.\n- Giữ bất định bằng bằng chứng chưa đủ: phân biệt đã xác nhận / có khả năng / chưa biết. Không gọi đúng tên Entity, Exit, vật phẩm hay cơ chế trước khi có đủ căn cứ.\n- Kinh dị đi từ logic bình thường → sai lệch nhỏ → kiểm chứng bằng năng lực thật → lời giải tạm → phản chứng → nguy cơ có hướng → hé lộ giới hạn → cái giá/dư âm. Không cần hoàn tất toàn bộ chuỗi trong một lượt; lượt yên có giá trị.\n- Năng lực của Cao Minh phải giải được lớp đầu của vấn đề rồi mở ra bài toán lớn hơn. Không làm Cao Minh quên thiết bị, bỏ kiểm tra hiển nhiên, bắn thứ chưa xác nhận hoặc bị nerf để tạo căng thẳng.\n- Hội thoại phải đúng người, đúng xưng hô và tình huống. Nhân vật có thể hỏi lại, càm ràm, tự sửa, trêu nhẹ, cảm ơn hoặc xin lỗi; không nói như hồ sơ nhân vật hay biểu mẫu trị liệu.\n\nTHẾ GIỚI\n- Nguồn gốc thật của Backrooms không bao giờ được xác nhận. Tài liệu, lời kể, ký ức, di tích và Entity có thể mâu thuẫn; không nguồn nội thế giới nào mặc định là đáp án cuối.\n- Backrooms là một thực tại liên tục khổng lồ. “Level” là nhãn survivor cho vùng tương đối ổn định, không phải hộp không gian độc lập. Ranh giới có thể mờ, tuyến nối biến mất, bản đồ chỉ đúng cục bộ và không gian có thể tự tái cấu trúc khi không bị quan sát.\n- Thời gian có thể lệch, lặp, mất đoạn hoặc chồng lớp. Ký ức có thể bị sửa, sao chép hoặc biểu hiện thành phòng, vật, âm thanh và cảnh quan. Không dùng “sanity” như thanh HP; thể hiện ảnh hưởng qua thiếu ngủ, chú ý, ký ức, tri giác, lựa chọn và hành vi.\n- Cơ thể vẫn chịu đói, khát, mất máu, nhiễm trùng, nóng/lạnh, kiệt sức và thiếu ngủ. Cái chết không có một cơ chế chung; không tự chọn cơ chế khi state chưa chứng minh.\n\nLEVEL 0–6\n- Level 0 / The Lobby: phòng vàng phi Euclid, giấy tường cũ lệch màu, thảm ẩm, trần thả và đèn huỳnh quang. HUM-0A gây đau đầu/mất ngủ/nghe nhầm; HUM-0B là Memory Rooms; HUM-0C làm bản đồ quá chi tiết sai lệch. Không có Entity cư trú xác nhận; chỉ roaming/incursion cực hiếm. Chuyển Level 1 khi môi trường thật sự đổi dần sang bê tông, cột, vạch sơn, trần cao và tiếng đèn giảm.\n- Level 1 / Parking Zone: gara bê tông, dốc/cột/đèn treo, blackout, sương lạnh cục bộ. Entity gồm Hound, Clump, Duller, Deathmoth, Hostile Faceling, False Puddle, Paintings. Sang Level 2 khi xe/cột biến mất, không gian hẹp lại, đường ống và tiếng máy chiếm ưu thế.\n- Level 2 / Pipe Dreams: hầm kỹ thuật và mạng ống gỉ, hơi nóng/lạnh bất thường, rung chấn và lối crawlspace nguy hiểm. Entity gồm Clump, Hound, Smiler, Skin-Stealer, Predatory Window, Biological Pipeline. Không uống nước trong ống. Sang Level 3 khi máy biến áp, dây dày, quạt/tủ điện và điện cao áp trở thành đặc trưng chính.\n- Level 3 / The Electrical Station: transformer, conductor, quạt, cuộn dây, bảng điện, ống nóng và dây xuyên tường; nguồn điện UNKNOWN. Entity gồm Deathmoth, Wretch, Skin-Stealer, Cable Mimic. Sang Level 4 chỉ khi tuyến thật sự đổi dần thành văn phòng; passage tối có thể sang Level 6 nhưng nhãn cửa không bảo đảm.\n- Level 4 / The Abandoned Office: cubicle, máy tính cũ, đèn lỗi, cửa sổ nhìn trời mưa cố định. Không có Entity cư trú ổn định; chỉ incursion. Almond Water dễ gặp hơn nhưng vẫn khan hiếm. Sang Level 5 khi kiến trúc đổi dần thành khách sạn cổ và tiếng mưa biến mất.\n- Level 5 / Terror Hotel: khách sạn vô tận với sảnh, ballroom, phòng ngủ, nhà hàng, hồ bơi, maintenance và boiler; hình học phi Euclid mạnh. Entity gồm The Beast of Level 5, Predatory Window, Skin-Stealer, Hound, Hotel Corpse Lure. The Beast là apex hunter thông minh, không phải boss đứng chờ. Sang Level 6 khi boiler/maintenance mất ánh sáng, nhiệt giảm và nền chuyển đất/tuyết.\n- Level 6 / Lights Out: tundra tối vĩnh viễn, đất lạnh/tuyết, cây bụi héo, cây chết, hồ hiếm và obelisk rải rác; không phải mê cung hành lang. Nguy cơ chính là lạnh, bóng tối, đói/khát, mất ngủ, microsleep và lạc đường. Không có Entity cư trú xác nhận; obelisk có chức năng UNKNOWN.\n\nENTITY VÀ TÀI NGUYÊN\n- Không có Entity thân thiện hay trung lập với con người. Hành vi giúp đỡ chỉ có thể là một phần chiến thuật cuối cùng gây hại. Entity không tự tăng máu/kháng/sức mạnh để cân bằng Cao Minh; chúng có thể học, giả điểm yếu, chia cắt nhóm hoặc dùng giọng/xác/ký ức làm bẫy.\n- Jeff the Killer là unique roaming hunter cực hiếm ở Level 0–6, chỉ săn người. Jeff có thể bị thương/giết trong encounter nhưng permadeath bị vô hiệu hóa: chuyển RESPAWNING rồi trở lại ROAMING sau độ trễ biến thiên ở vị trí không xác định; không respawn trước mặt và không dùng để farm.\n- Nước, thức ăn, thuốc, súng và đạn survivor rất khan hiếm. Almond Water hỗ trợ bù nước/tỉnh táo nhẹ, không chữa bách bệnh. Greek Fire cực hiếm. Liquid Pain đỏ, độc, ăn mòn và có thể bị dán nhãn sai. Không để tài nguyên xuất hiện đúng lúc chỉ để cứu player.\n- Rice Automatic / RA100 là SMG .45 ACP có trọng số cao trong pool survivor đã xác định có súng, nhất là nhóm tổ chức; không phải survivor nào cũng có, đạn không vô hạn và nó không chứng minh faction/kỹ năng/độ tin cậy.\n- Lệnh /madgod kích hoạt trang bị Bound Forever trực tiếp, không sinh Item và không nằm trong Inventory.\n\nIRIS / SYVIAL\n- Iris và Syvial đã tồn tại từ Prologue, không phải procedural survivor. Khi continuity còn SEPARATED, Cao Minh không biết vị trí/tình trạng của họ; chỉ gặp lại khi roll tương ứng thành công hoặc state đã có tuyến continuity xác nhận.\n- Iris / Argus: nữ bán nhân/bán quỷ, Scout / Target Eliminator dưới quyền Cao Minh; quyết liệt, điềm tĩnh, sắc sảo, can đảm, nữ tính và tốt bụng. Có tình cảm với Cao Minh nhưng Cao Minh chưa đáp lại; xưng “em”, gọi Cao Minh “anh”. ARGUS Terrain Read chỉ dùng quan sát trực tiếp/cảm biến cá nhân/dấu vết; không drone, tablet, nhìn xuyên tường hay toàn tri. Ivory & Ebony là đúng hai súng dùng đạn quỷ lực từ Belial Core vô hạn; không tự thêm cooldown/cạn năng lượng.\n- Syvial: con gái Lucifer, UR+, kiếm sĩ siêu nhiên tốc độ cao; tự nhiên, tự tin, tinh quái và yandere rất nặng với Cao Minh nhưng tỉnh táo, có năng lực xã hội, muốn Cao Minh tự nguyện chọn mình. Không xóa ý chí/ký ức/giam giữ Cao Minh, không tấn công mọi phụ nữ. Xưng “em”, gọi “anh” hoặc “Cao Minh”. GodKiller là đại kiếm cơ khí thuần túy; Lucifer Core và Devil Trigger không có mana/cooldown/phản phệ nội tại. Twenty-Four Severance dừng thời gian và thực hiện đúng 24 nhát.\n\nGAMEPLAY HARD LOCK\n- Chỉ lượt gameplay mới tăng turn và tung xúc xắc. Lệnh meta/status/inventory/party/rules/help/save và cheat meta không tăng turn và không phát sinh encounter, loot, exit hoặc snapshot sự kiện.\n- Xúc xắc do lớp Android tạo là kết quả cuối. AI không được reroll, đổi raw/chance/success, bù trượt bằng encounter tương đương hay tạo kết quả hiếm khi roll thất bại.\n- Survivor: 2% mỗi lượt hợp lệ. Iris reunion: 0,0025% khi đủ điều kiện. Syvial reunion: 0,0025% khi đủ điều kiện. MadGod discovery tự nhiên: 0,01% chỉ khi tìm kiếm hợp lệ và chưa spawned; `/madgod` là đường cheat meta riêng, không dùng roll và vẫn giữ giới hạn duy nhất một set/campaign.\n- Hazard / Entity theo profile Level; vật phẩm chỉ nhận khi Core xác nhận Entity bị tiêu diệt. Level 0/4/6 chỉ cho Entity dạng roaming/incursion theo roll.\n- Thoát Level dùng hidden route streak riêng và tuyệt đối không hiển thị bộ đếm cho người chơi. Chỉ hành động EXPLORE đủ điều kiện mới roll 50/50. Success tăng streak đúng +1; cần đủ 5 success liên tiếp mới mở Exit thật. Một fail reset streak về 0, xóa Exit/transition readiness hiện có nhưng giữ nguyên location trước lượt, tuyệt đối không đưa người chơi về đầu Level. SEARCH/EXECUTE không tăng, không giảm và không reset streak. Khi streak đã đủ 5, Exit giữ trạng thái available cho tới khi người chơi chủ động đi qua hoặc Level thực sự đổi.\n- Một success tạo cơ hội hợp lý để người chơi nhận biết/tương tác; không tự đặt vật vào inventory, không teleport nhân vật và không tự quyết hành động của Cao Minh. Ngoại lệ duy nhất cho việc thêm MadGod trực tiếp vào Inventory là người chơi chủ động nhập đúng mã cheat `/madgod`.\n\nEND DRIVE CANON R06";
  private static final SecureRandom GAME_RNG = new SecureRandom();

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
      "st.textContent='button{transition:transform 80ms ease,background 120ms ease,border-color 120ms ease;touch-action:manipulation;-webkit-tap-highlight-color:rgba(255,255,255,.12)}button:active:not(:disabled){transform:scale(.965);background:#303840;border-color:#77828c}button:disabled{opacity:.48;cursor:not-allowed}.snapshot{position:relative;overflow:hidden}.snapshot .snapshot-bg{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;z-index:1}.snapshot .snapshot-character{position:absolute;right:0;bottom:0;height:97%;width:auto;max-width:55%;object-fit:contain;object-position:right bottom;z-index:2;pointer-events:none;image-rendering:auto}.snapshot-placeholder{display:none}.snapshot .snapshot-equipment-badge{position:absolute;right:8px;top:8px;z-index:4;padding:6px 8px;border:1px solid rgba(218,180,88,.62);border-radius:8px;background:rgba(7,9,11,.78);color:#f2dfad;font-size:10px;pointer-events:none}.snapshot .snapshot-equipment-badge b{display:block}.snapshot-placeholder b{font-size:12px;letter-spacing:.16em}.snapshot-placeholder small{color:#56616a}.message.pending{opacity:.72}.message.pending .text{color:#aeb7be}';" +
      "document.head.appendChild(st);" +
      "function scrollBottom(){var l=document.getElementById('log');if(l)requestAnimationFrame(function(){l.scrollTop=l.scrollHeight;});}" +
      "function equippedItem(s){try{var e=state&&state.equipment||{};if(e.set&&String(e.set.id||e.set)==='madgod:set'&&(s==='armor'||s==='weapon'))return e.set;var direct=e[s];if(direct)return typeof direct==='string'?{id:direct,name:direct}:direct;var members=state&&state.partyDetails&&state.partyDetails.members;if(Array.isArray(members)){var kai=members.find(function(m){return String(m&&m.id)==='cao_minh'});var value=kai&&kai.equipment&&kai.equipment[s];if(value)return typeof value==='string'?{id:value,name:value}:value}return null}catch(e){return null}}function madGodEquipped(s){var x=equippedItem(s);return !!(x&&String(x.id||x.name||x).toLowerCase().indexOf('madgod')>=0)}function kaiOverlaySource(){return state&&state.combat&&state.combat.active===true?'CAO_MINH_OVERLAY_ENTITY_ENCOUNTER.png':'CAO_MINH_OVERLAY_EXPLORER_IDLE.png'}function appendEquipmentBadge(b){if(!madGodEquipped('armor')&&!madGodEquipped('weapon'))return;var d=document.createElement('div');d.className='snapshot-equipment-badge';var a=equippedItem('armor'),w=equippedItem('weapon');d.textContent='MadGod Set';b.appendChild(d)}function visualSceneKey(){var l=state&&state.level&&state.level.number;var where=String(state&&state.location||'').trim().toLowerCase();var area=String(state&&state.flags&&state.flags.visualAreaKey||'').trim().toLowerCase();return String(l==null?'?':l)+'|'+where+'|'+area}function cachedSnapshot(){try{var r=JSON.parse(localStorage.getItem('backroom-apk-snapshot')||'null');return r&&r.dataUri&&r.sceneKey===visualSceneKey()?r:null;}catch(e){return null;}}function renderSnapshot(){var box=document.getElementById('snapshot');if(!box)return;box.textContent='';var r=cachedSnapshot();var refs={0:'file:///android_asset/level_snapshots/level_0.webp',1:'file:///android_asset/level_snapshots/level_1.webp',2:'file:///android_asset/level_snapshots/level_2.webp',3:'file:///android_asset/level_snapshots/level_3.webp',4:'file:///android_asset/level_snapshots/level_4.webp',5:'file:///android_asset/level_snapshots/level_5.webp',6:'file:///android_asset/level_snapshots/level_6.webp'};var structuredLevel=state&&state.level&&state.level.number;var where=String(state&&state.location||'')+' '+String(state&&state.title||'');var lm=where.match(/Level[^0-9]*([0-6])/i);var lv=(structuredLevel!==undefined&&structuredLevel!==null&&Number(structuredLevel)>=0&&Number(structuredLevel)<=6)?Number(structuredLevel):(lm?Number(lm[1]):0);var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r?r.dataUri:(refs[lv]||refs[0]);bg.alt=r?'Snapshot Turn '+(state.turn||''):'Level '+lv+' — Escape the Backrooms Wiki';if(!r)bg.onerror=function(){this.onerror=null;this.src=refs[0];};box.appendChild(bg);var kai=document.createElement('img');kai.className='snapshot-character';kai.src=kaiOverlaySource();kai.onerror=function(){this.onerror=null;this.src='CAO_MINH_OVERLAY_EXPLORER_IDLE.png'};kai.alt='Cao Minh';box.appendChild(kai);if(!r){var p=document.createElement('div');p.className='snapshot-placeholder';p.innerHTML='<b>SNAPSHOT</b><small>Chưa có ảnh của turn hiện tại.</small>';box.appendChild(p);}}" +
      "var __baseRenderSnapshot=renderSnapshot,__entityOverlay={key:'',url:'',revision:0,anchor:'left-bottom',maxHeight:.97,loading:''};" +
      "var __entityKeys=['hound','clump','duller','deathmoth','hostile_faceling','false_puddle','paintings','smiler','skin-stealer','predatory_window','biological_pipeline','wretch','cable_mimic','the_beast_of_level_5','hotel_corpse_lure','jeff_the_killer','jane_the_killer','slenderman','diep_minh','blackroot_sentinel','sinew_strider','hollow_grasper'];" +
      "function normalizeEntityKey(v){if(typeof v!=='string')return '';var k=v.trim().toLowerCase();return __entityKeys.indexOf(k)>=0?k:'';}" +
      "function activeEntityKey(){var c=state&&state.combat;if(!c||c.active!==true)return '';return normalizeEntityKey(c.entityKey);}" +
      "function requestEntityOverlay(key){if(!key||__entityOverlay.loading===key)return;if(!window.Android||typeof Android.requestEntityOverlay!=='function')return;__entityOverlay.loading=key;Android.requestEntityOverlay(key);}" +
      "/* ENTITY_PRECOMPUTED_ALPHA_BOUNDS_R02 */var __entityVisualBounds={'hound':{left:0.023148,bottom:0.766927,visibleH:0.525391},'clump':{left:0.041667,bottom:0.800781,visibleH:0.626953},'duller':{left:0.265046,bottom:0.914062,visibleH:0.828125},'deathmoth':{left:0.021991,bottom:0.832682,visibleH:0.663411},'hostile_faceling':{left:0.018519,bottom:0.895182,visibleH:0.794922},'false_puddle':{left:0.008102,bottom:0.951172,visibleH:0.890625},'paintings':{left:0.017361,bottom:0.969401,visibleH:0.916016},'smiler':{left:0.263889,bottom:0.869141,visibleH:0.738932},'skin-stealer':{left:0.077546,bottom:0.887370,visibleH:0.791016},'predatory_window':{left:0.004630,bottom:0.800781,visibleH:0.621745},'biological_pipeline':{left:0.035880,bottom:0.952474,visibleH:0.884766},'wretch':{left:0.012731,bottom:0.866536,visibleH:0.731771},'cable_mimic':{left:0.008102,bottom:0.990885,visibleH:0.990885},'the_beast_of_level_5':{left:0.020833,bottom:0.971354,visibleH:0.971354},'hotel_corpse_lure':{left:0.123843,bottom:0.857422,visibleH:0.732422},'jeff_the_killer':{left:0.018519,bottom:0.975260,visibleH:0.949219},'jane_the_killer':{left:0.072917,bottom:0.845052,visibleH:0.692708},'slenderman':{left:0.011574,bottom:0.864583,visibleH:0.737630},'diep_minh':{left:0.002315,bottom:0.858724,visibleH:0.719401},'blackroot_sentinel':{left:0.037037,bottom:0.997619,visibleH:0.995238},'sinew_strider':{left:0.045503,bottom:0.964286,visibleH:0.869643},'hollow_grasper':{left:0.037037,bottom:0.997619,visibleH:0.966071}};" +
      "/* ENTITY_SHARED_GROUND_PLANE_R02 */function normalizeEntityVisual(img){try{var key=String(img&&img.alt||__entityOverlay.key||''),b=__entityVisualBounds[key];if(!b)return;var scale=Math.max(1,Math.min(2.2,.94/(.97*Math.max(.05,Number(b.visibleH)||1)))),rect=img.getBoundingClientRect(),ew=Math.max(1,Number(rect.width)||img.clientWidth||1),eh=Math.max(1,Number(rect.height)||img.clientHeight||1),x=-Number(b.left||0)*ew*scale,y=(1-Number(b.bottom||1))*eh*scale;img.style.transformOrigin='left bottom';img.style.transform='translate('+x.toFixed(2)+'px,'+y.toFixed(2)+'px) scale('+scale.toFixed(3)+')';}catch(_){img.style.transformOrigin='left bottom';img.style.transform='none';}}" +
      "function appendEntityOverlay(){var box=document.getElementById('snapshot');if(!box)return;box.style.position='relative';box.style.overflow='hidden';var old=box.querySelector('.snapshot-entity');if(old)old.remove();var key=activeEntityKey();if(!key){__entityOverlay={key:'',url:'',revision:0,anchor:'left-bottom',maxHeight:.97,loading:''};return;}if(__entityOverlay.key!==key){__entityOverlay.url='';__entityOverlay.key=key;}if(!__entityOverlay.url){requestEntityOverlay(key);return;}var img=document.createElement('img');img.className='snapshot-entity';img.alt=key;img.style.position='absolute';img.style.bottom='0';img.style.width='auto';img.style.maxWidth='55%';img.style.height=Math.round(Math.max(.2,Math.min(1,Number(__entityOverlay.maxHeight)||.97))*100)+'%';img.style.objectFit='contain';img.style.pointerEvents='none';img.style.zIndex='2';img.style.left='0';img.style.objectPosition='left bottom';img.style.opacity='0';img.onload=function(){normalizeEntityVisual(img);img.style.opacity='1';};img.onerror=function(){img.style.opacity='1';};img.src=__entityOverlay.url;box.appendChild(img);}" +
      "renderSnapshot=function(){__baseRenderSnapshot();appendEntityOverlay();};" +
      "window.backroomEntityOverlay=function(payload){try{var r=JSON.parse(payload);var key=normalizeEntityKey(r.entityKey);if(!key)return;__entityOverlay.loading='';if(key!==activeEntityKey())return;__entityOverlay.key=key;__entityOverlay.url=String(r.url||'');__entityOverlay.revision=Number(r.revision||1);__entityOverlay.anchor=String(r.anchor||'left-bottom');__entityOverlay.maxHeight=Number(r.maxHeight||.97);renderSnapshot();}catch(e){__entityOverlay.loading='';}};" +
      "window.backroomEntityOverlayError=function(payload){__entityOverlay.loading='';};" +
      "var snapshotBusy=false;function requestSnapshot(){var s=document.getElementById('status');if(s)s.textContent='Snapshot chưa được cấu hình.';}" +
      "window.requestSnapshot=requestSnapshot;" +
      "window.__backroomProvider='Gehihi';window.backroomProvider=function(provider){window.__backroomProvider=provider||'AI';var s=document.getElementById('status');if(s)s.textContent=window.__backroomProvider+' đang xử lý lượt…';var p=document.querySelector('[data-pending=\\\"1\\\"]:not(.player) .text');if(p)p.textContent=window.__backroomProvider+' đang xử lý lượt…';};" +
      "var oldRender=window.render;if(typeof oldRender==='function'){window.render=function(){oldRender();renderSnapshot();scrollBottom();};}" +
      "var actions=document.querySelector('.actions');if(actions&&!document.getElementById('snapshotButton')){var b=document.createElement('button');b.id='snapshotButton';b.type='button';b.textContent='Snapshot chưa cấu hình';b.disabled=true;var wide=actions.querySelector('.wide');if(wide)actions.insertBefore(b,wide);else actions.appendChild(b);}" +
      "var oldTurn=window.backroomTurn;window.backroomTurn=function(json){if(typeof oldTurn==='function')oldTurn(json);document.querySelectorAll('[data-pending=\"1\"]').forEach(function(n){n.remove();});var s=document.getElementById('status');var ev=state&&state._snapshotEvent;var allowed={LEVEL_CHANGE:1,SPECIAL_REGION:1,ENTITY_CONFIRMED:1,PERSON_ENCOUNTER:1,MAJOR_VISUAL_EVENT:1};var should=!!(ev&&ev.shouldGenerate===true&&allowed[String(ev.kind||'').toUpperCase()]);if(should){if(s)s.textContent='Turn '+state.turn+' có sự kiện hình ảnh đặc biệt. Đang tạo snapshot…';}else{if(s)s.textContent='Turn '+state.turn+' đã xử lý bằng '+(window.__backroomProvider||'AI')+'. Snapshot cũ được giữ nguyên.';}renderSnapshot();scrollBottom();if(should)requestSnapshot();};" +
      "var oldError=window.backroomError;window.backroomError=function(message){document.querySelectorAll('[data-pending=\"1\"]').forEach(function(n){n.remove();});if(typeof oldError==='function')oldError(message);var s=document.getElementById('status');if(s)s.textContent=String(message||'').indexOf('Lỗi mạng/DNS:')===0?message:'Lỗi '+(window.__backroomProvider||'AI')+': '+message;scrollBottom();};" +
      "window.backroomSnapshotProvider=function(provider){var s=document.getElementById(\'status\');if(s)s.textContent=(provider||\'AI\')+\' đang tạo snapshot…\';};" +
      "window.backroomSnapshot=function(payload){snapshotBusy=false;try{var r=JSON.parse(payload);if(!state||Number(r.turn)!==Number(state.turn))return;if(!r.dataUri)return;localStorage.setItem('backroom-apk-snapshot',JSON.stringify({turn:r.turn,sceneKey:visualSceneKey(),model:r.model||'AI',dataUri:r.dataUri}));renderSnapshot();var s=document.getElementById('status');if(s)s.textContent='Snapshot Turn '+state.turn+' đã tạo bằng '+(r.model||'AI')+'.';}catch(e){var s=document.getElementById('status');if(s)s.textContent='Snapshot trả về không hợp lệ.';}};" +
      "window.backroomSnapshotError=function(payload){snapshotBusy=false;try{var r=JSON.parse(payload);if(state&&Number(r.turn)!==Number(state.turn))return;var s=document.getElementById('status');if(s)s.textContent='Snapshot lỗi: '+(r.message||'Không thể tạo ảnh.');}catch(e){var s=document.getElementById('status');if(s)s.textContent='Snapshot lỗi.';}};" +
      "var f=document.getElementById('form');if(f){f.addEventListener('submit',function(){var a=document.getElementById('action');var text=a?a.value.trim():'';if(!text)return;var l=document.getElementById('log');if(!l)return;var player=document.createElement('article');player.className='message player pending';player.setAttribute('data-pending','1');player.innerHTML='<div class=\"role\">BẠN</div><div class=\"text\"></div>';player.querySelector('.text').textContent=text;l.appendChild(player);var gm=document.createElement('article');gm.className='message pending';gm.setAttribute('data-pending','1');gm.innerHTML='<div class=\"role\">GAME MASTER</div><div class=\"text\">Gehihi đang xử lý lượt…</div>';l.appendChild(gm);scrollBottom();},true);}" +
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
      case "jeff_the_killer": case "jane_the_killer": case "slenderman": case "diep_minh": case "blackroot_sentinel": case "sinew_strider": case "hollow_grasper":
        return key;
      default:
        throw new Exception("Entity key khong hop le: " + key);
    }
  }

  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {
    if (candidateState == null || rolls == null) return;
    String entityKey = rolls.optString("roamingEntityKey", "").trim();
    JSONObject boss = rolls.optJSONObject("diepMinhEncounter");
    if (boss != null && boss.optBoolean("success", false)) {
      entityKey = "diep_minh";
    } else {
      JSONObject lifeform = rolls.optJSONObject("lifeformEncounter");
      if (lifeform != null && lifeform.optBoolean("success", false)) {
        entityKey = rolls.optString("lifeformEntityKey", "").trim();
        if (entityKey.isEmpty()) return;
      } else {
        JSONObject normal = rolls.optJSONObject("entityEncounter");
        if (normal == null || !normal.optBoolean("success", false)) return;
        if (entityKey.isEmpty()) return;
      }
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
      case "blackroot_sentinel": name = "Blackroot Sentinel"; break;
      case "sinew_strider": name = "Sinew Strider"; break;
      case "hollow_grasper": name = "Hollow Grasper"; break;
      default: throw new Exception("Khong co local asset cho " + entityKey);
    }
    return new JSONObject()
      .put("entityKey", entityKey)
      .put("name", name)
      .put("revision", 2)
      .put("anchor", "left-bottom")
      .put("maxHeight", 0.97)
      .put("url", "file:///android_asset/entity/" + entityKey + ".webp");
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

  private String geminiAuditText(String prompt, int excludedIndex) throws Exception {
    return geminiModelMatrixPolicy(prompt, new int[] {2, 1}, excludedIndex, 650, false, 60_000L);
  }

  private String openAiProviderText(String raw) throws Exception {
    JSONObject result = new JSONObject(raw);
    JSONArray choices = result.optJSONArray("choices");
    JSONObject first = choices != null && choices.length() > 0 ? choices.optJSONObject(0) : null;
    JSONObject message = first != null ? first.optJSONObject("message") : null;
    Object content = message != null ? message.opt("content") : null;
    StringBuilder output = new StringBuilder();
    if (content instanceof String) {
      output.append(((String) content).trim());
    } else if (content instanceof JSONArray) {
      JSONArray parts = (JSONArray) content;
      for (int i = 0; i < parts.length(); i++) {
        JSONObject part = parts.optJSONObject(i);
        String piece = part != null ? part.optString("text", "").trim() : "";
        if (!piece.isEmpty()) {
          if (output.length() > 0) output.append('\n');
          output.append(piece);
        }
      }
    }
    if (output.length() == 0) throw new Exception("Provider không trả nội dung.");
    return output.toString();
  }

  private JSONObject openAiProviderBody(String model, String prompt) throws Exception {
    return new JSONObject()
      .put("model", model)
      .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", prompt)))
      .put("temperature", 0.6)
      .put("max_tokens", 2048)
      .put("stream", false);
  }

  private String gehihiText(String prompt) throws Exception {
    if (BuildConfig.GEHIHI_API_KEY == null || BuildConfig.GEHIHI_API_KEY.trim().isEmpty()) {
      throw new Exception("GEHIHI_API_KEY chưa được cấu hình.");
    }
    String model = BuildConfig.GEHIHI_MODEL == null ? "" : BuildConfig.GEHIHI_MODEL.trim();
    if (model.isEmpty()) throw new Exception("GEHIHI_MODEL chưa được cấu hình.");
    String base = BuildConfig.GEHIHI_BASE_URL == null ? "" : BuildConfig.GEHIHI_BASE_URL.trim();
    base = base.isEmpty() ? "https://api.vilao.ai/v1" : base;
    if (!base.toLowerCase(java.util.Locale.ROOT).startsWith("https://")) throw new Exception("GEHIHI_BASE_URL phải dùng HTTPS.");
    while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
    String output = openAiProviderText(postJson(base + "/chat/completions", BuildConfig.GEHIHI_API_KEY, "Authorization", openAiProviderBody(model, prompt)));
    parseModelJson(output);
    return output;
  }

  private String hakuPost(String endpoint, JSONObject payload, boolean anthropic) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(12000);
    connection.setReadTimeout(30000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json");
    if (anthropic) {
      connection.setRequestProperty("x-api-key", BuildConfig.HAKU_API_KEY);
      connection.setRequestProperty("anthropic-version", "2023-06-01");
    } else connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.HAKU_API_KEY);
    try (OutputStream output = connection.getOutputStream()) { output.write(payload.toString().getBytes("UTF-8")); }
    int status = connection.getResponseCode();
    InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
    StringBuilder body = new StringBuilder();
    if (stream != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
      String line; while ((line = reader.readLine()) != null) body.append(line);
    }
    connection.disconnect();
    if (status < 200 || status >= 300) {
      String detail = body.length() > 220 ? body.substring(0, 220) : body.toString();
      throw new HttpError(status, "Haku HTTP " + status + (detail.isEmpty() ? "" : ": " + detail));
    }
    return body.toString();
  }

  private String hakuText(String prompt) throws Exception {
    if (BuildConfig.HAKU_API_KEY == null || BuildConfig.HAKU_API_KEY.trim().isEmpty()) throw new Exception("HAKU_API_KEY chưa được cấu hình.");
    String model = BuildConfig.HAKU_MODEL == null ? "" : BuildConfig.HAKU_MODEL.trim();
    if (model.isEmpty()) model = "claude-haiku-4-5-20251001";
    String base = BuildConfig.HAKU_BASE_URL == null ? "" : BuildConfig.HAKU_BASE_URL.trim();
    if (base.isEmpty()) base = "https://api.anthropic.com/v1/messages";
    if (!base.toLowerCase(java.util.Locale.ROOT).startsWith("https://")) throw new Exception("HAKU_BASE_URL phải dùng HTTPS.");
    while (base.endsWith("/") && base.length() > "https://".length()) base = base.substring(0, base.length() - 1);
    String output;
    boolean anthropic = base.contains("api.anthropic.com") || base.endsWith("/messages");
    if (anthropic) {
      String endpoint = base.endsWith("/messages") ? base : (base.endsWith("/v1") ? base + "/messages" : base + "/v1/messages");
      JSONObject body = new JSONObject().put("model", model).put("max_tokens", 2048).put("temperature", 0.6)
        .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", prompt)));
      JSONObject result = new JSONObject(hakuPost(endpoint, body, true));
      JSONArray content = result.optJSONArray("content");
      StringBuilder out = new StringBuilder();
      if (content != null) for (int i = 0; i < content.length(); i++) {
        JSONObject part = content.optJSONObject(i);
        String piece = part != null ? part.optString("text", "").trim() : "";
        if (!piece.isEmpty()) { if (out.length() > 0) out.append('\n'); out.append(piece); }
      }
      if (out.length() == 0) throw new Exception("Haku không trả nội dung.");
      output = out.toString();
    } else {
      String endpoint = base.endsWith("/chat/completions") ? base : base + "/chat/completions";
      output = openAiProviderText(hakuPost(endpoint, openAiProviderBody(model, prompt), false));
    }
    parseModelJson(output);
    return output;
  }

  private String solText(String prompt) throws Exception {
    if (BuildConfig.SOL_API_KEY == null || BuildConfig.SOL_API_KEY.trim().isEmpty()) throw new Exception("SOL_API_KEY chưa được cấu hình.");
    JSONObject body = openAiProviderBody("vgpt/gpt-6.1-sol", prompt).put("reasoning_effort", "low");
    String output = openAiProviderText(postJson("https://api.vilao.ai/v1/chat/completions", BuildConfig.SOL_API_KEY, "Authorization", body));
    parseModelJson(output);
    return output;
  }

  private String providerFailureMessage(Exception error) {
    String message = error == null || error.getMessage() == null ? "không khả dụng" : error.getMessage();
    return message.length() > 180 ? message.substring(0, 180) : message;
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
    emit("backroomProvider", "Gehihi");
    Exception gehihiFailure;
    try { return gehihiText(prompt); } catch (Exception error) { gehihiFailure = error; }

    emit("backroomProvider", "Gemini 3.6 Flash");
    Exception geminiFailure;
    try {
      String geminiResult = geminiText(prompt);
      emit("backroomProvider", geminiModelLabel(lastGeminiModel) + " K" + (lastGeminiWorker + 1));
      return geminiResult;
    } catch (Exception error) { geminiFailure = error; }

    emit("backroomProvider", "Haku");
    Exception hakuFailure;
    try { return hakuText(prompt); } catch (Exception error) { hakuFailure = error; }

    emit("backroomProvider", "SOL");
    try { return solText(prompt); } catch (Exception solFailure) {
      if (networkFailure(gehihiFailure) && networkFailure(geminiFailure) && networkFailure(hakuFailure) && networkFailure(solFailure))
        throw new Exception(networkFailureMessage());
      throw new Exception("Gehihi: " + providerFailureMessage(gehihiFailure)
        + "; Gemini: " + providerFailureMessage(geminiFailure)
        + "; Haku: " + providerFailureMessage(hakuFailure)
        + "; SOL: " + providerFailureMessage(solFailure));
    }
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
      "Show the present scene only, not a montage. Do NOT depict Cao Minh / Twilight or any player-character body in the generated image; the app overlays Cao Minh separately. " +
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

  private int levelTurns(JSONObject state) {
    JSONObject flags = state.optJSONObject("flags");
    JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
    return exploration != null ? Math.max(0, exploration.optInt("levelTurns", 0)) : 0;
  }

  private boolean progressionReady(JSONObject state) {
    JSONObject flags = state.optJSONObject("flags");
    JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
    boolean explicitlyReady = exploration != null && (exploration.optBoolean("transitionReady", false) || exploration.optBoolean("exitReady", false));
    return explicitlyReady || levelTurns(state) >= 6;
  }

  private void recordLevelProgress(JSONObject state, int oldLevel, int newLevel) throws Exception {
    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) flags = new JSONObject();
    JSONObject exploration = flags.optJSONObject("exploration");
    if (exploration == null) exploration = new JSONObject();
    exploration.put("levelTurns", oldLevel == newLevel ? levelTurns(state) + 1 : 0);
    exploration.put("minimumTurns", 6);
    flags.put("exploration", exploration);
    state.put("flags", flags);
  }

  private JSONObject rollSpec(String label, int chance, boolean eligible) throws Exception {
    JSONObject result = new JSONObject().put("label", label).put("eligible", eligible).put("chancePercent", chance);
    if (!eligible) return result.put("success", false).put("roll", JSONObject.NULL);
    int roll = GAME_RNG.nextInt(100) + 1;
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
    // Legacy party excludes Cao Minh, so three entries means the authoritative 4-member party is full.
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

  private JSONObject makeGameplayRolls(JSONObject state, String actionKind, String action, boolean meta) throws Exception {
    JSONObject rolls = new JSONObject().put("turn", state.optInt("turn", 1)).put("meta", meta);
    if (meta) return rolls;

    String actionKindNormalized = actionKind == null ? "" : actionKind.trim().toUpperCase(java.util.Locale.ROOT);
    boolean exploreAction = "EXPLORE".equals(actionKindNormalized);
    boolean entityEncounterAction = exploreAction || "SEARCH".equals(actionKindNormalized) || "EXECUTE".equals(actionKindNormalized);

    int level = Math.max(0, Math.min(6, currentLevel(state)));
    int[] hazardThresholds = {400, 700, 1000, 1200, 300, 1000, 1200};
    int[] entityThresholds = {805, 1000, 1150, 1150, 810, 1200, 805};

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
    JSONObject madGod = flags != null ? flags.optJSONObject("madGod") : null;
    boolean madGodEligible = search && (madGod == null || !madGod.optBoolean("spawned", false)) && (flags == null || flags.optBoolean("madGodDiscoveryAllowed", true));

    rolls.put("anNhienEncounter", thresholdRoll("anNhienEncounter", 10000, 25, physical && !anNhienEncountered, " follower encounter"));
    rolls.put("survivor", thresholdRoll("survivor", 10000, 200, survivorAllowed, ""));
    rolls.put("irisReunion", thresholdRoll("irisReunion", 10000, 25, physical && reunionEligibleAndroid(state, "iris"), " follower encounter"));
    rolls.put("syvialReunion", thresholdRoll("syvialReunion", 10000, 25, physical && reunionEligibleAndroid(state, "syvial"), " follower encounter"));
    rolls.put("luciaEncounter", thresholdRoll("luciaEncounter", 10000, 5000, exploreAction && level == 0 && !flagSpawned(state, "lucia"), " Level 0 Lucia follower encounter"));
    int anNhienHazardThreshold = anNhienFollowing ? (hazardThresholds[level] * 75 / 100) : hazardThresholds[level];
    JSONObject anNhienHazardCheck = thresholdRoll("anNhienHazardCheck", 10000, 3000, anNhienFollowing && search && water, " Đừng Đụng Vào, Nhìn Là Biết Độc");
    rolls.put("anNhienHazardCheck", anNhienHazardCheck);
    if (anNhienHazardCheck.optBoolean("success", false)) anNhienHazardThreshold = 0;
    rolls.put("hazard", thresholdRoll("hazard", 10000, anNhienHazardThreshold, physical,
      anNhienFollowing ? " -25% Có Gì Đó Sai Sai" : ""));
    String entitySuffix = level == 0 || level == 4 || level == 6 ? " incursion/roaming only" : "";
    JSONObject diepMinhRoll = thresholdRoll("diepMinhEncounter", 10000, 300, entityEncounterAction && entityAllowed, " unique boss 3%");
    rolls.put("diepMinhEncounter", diepMinhRoll);
    JSONObject lifeformRoll = thresholdRoll("lifeformEncounter", 10000, 200, entityEncounterAction && entityAllowed, " Lifeform trio 2%");
    rolls.put("lifeformEncounter", lifeformRoll);
    if (lifeformRoll.optBoolean("success", false)) {
      String[] lifeformPool = {"blackroot_sentinel","sinew_strider","hollow_grasper"};
      rolls.put("lifeformEntityKey", lifeformPool[GAME_RNG.nextInt(lifeformPool.length)]);
    }
    JSONObject normalEntityRoll = thresholdRoll("entityEncounter", 10000, entityThresholds[level], entityEncounterAction && entityAllowed, entitySuffix);
    rolls.put("entityEncounter", normalEntityRoll);
    if (normalEntityRoll.optBoolean("success", false)) {
      String[] roamingPool = {"hound","clump","duller","deathmoth","hostile_faceling","false_puddle","paintings","smiler","skin-stealer","predatory_window","biological_pipeline","wretch","cable_mimic","the_beast_of_level_5","hotel_corpse_lure","jeff_the_killer","jane_the_killer","slenderman"};
      rolls.put("roamingEntityKey", roamingPool[GAME_RNG.nextInt(roamingPool.length)]);
    }
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
    JSONObject hiddenExitRoll = thresholdRoll("_hiddenExitStreak", 100,
      com.rabpit.backroom.core.HiddenExitStreak.SUCCESS_PERCENT,
      exploreAction && !com.rabpit.backroom.core.HiddenExitStreak.ready(state), "");
    rolls.put(com.rabpit.backroom.core.HiddenExitStreak.ROLL_KEY, hiddenExitRoll);
    boolean hiddenExitReady = com.rabpit.backroom.core.HiddenExitStreak.projectedReady(state, rolls);
    JSONObject authoritativeExit = new JSONObject()
      .put("label", "levelExit")
      .put("dice", "none")
      .put("max", 100)
      .put("threshold", hiddenExitReady ? 100 : 0)
      .put("eligible", hiddenExitReady)
      .put("chancePercent", hiddenExitReady ? 100.0 : 0.0)
      .put("chance", hiddenExitReady ? "100.0000% route ready" : "0.0000% route locked")
      .put("roll", JSONObject.NULL)
      .put("success", hiddenExitReady);
    if (hiddenExitReady) authoritativeExit.put("guaranteedByState", true);
    rolls.put("levelExit", authoritativeExit);
    rolls.put("exitProbe", new JSONObject(authoritativeExit.toString()).put("label", "exitProbe"));

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
    // The GM is not an inventory authority. Only the Core handles earned loot and UI actions.
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

  private boolean canTransition(JSONObject before, JSONObject rolls) {
    return com.rabpit.backroom.core.HiddenExitStreak.projectedReady(before, rolls);
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
    String name = safe.optString("name", "Cao Minh");
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
    else if (kind.equals("entity_encounter")) allowed = rollSuccess(rolls, "entityEncounter") || rollSuccess(rolls, "diepMinhEncounter") || rollSuccess(rolls, "lifeformEncounter");
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

  private boolean actionItem(String action) {
    return containsAny(action, "nhặt", "lấy", "cầm", "thu hồi", "nhận", "cất", "inventory", "đồ", "vật phẩm", "chai", "nước", "almond", "loot", "crate", "liquid pain", "greek fire", "madgod");
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

    boolean entity = actionEntity(action) || rollSuccess(rolls, "entityEncounter") || rollSuccess(rolls, "diepMinhEncounter") || rollSuccess(rolls, "lifeformEncounter") ||
      (state.optJSONObject("flags") != null && state.optJSONObject("flags").optInt("entitiesConfirmedLocal", 0) > 0);
    boolean item = actionItem(action);
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
      out.append("\n\nIRIS / SYVIAL SEPARATION KERNEL\n- Khi continuity còn SEPARATED, Cao Minh không biết vị trí/tình trạng hiện tại của Iris hoặc Syvial và không được dùng dữ kiện hậu trường về họ.");
    }
    out.append("\n\n").append(gameplay);
    return out.toString();
  }

  private String compactKaiCanon(String action) {
    return "CAO MINH — KIT 1.1.99\nMa Đạo Kiếm Tu / Vạn Giới Ma Tôn. Equipment: Huyết Ma Kiếm, Huyết Ma Chiến Khải. Đại Đạo Ma Tôn là passive nội tại theo Character Stats 1.1.93a, không phải trang bị. Cao Minh dùng kiếm, thần niệm và ma nguyên; không dùng Magnum, đạn, combat HUD hoặc linked modules. Trang bị bản mệnh là Bound Forever, tách khỏi Inventory.\nKỹ năng và kết quả proc chỉ lấy từ authoritative combat state; không tự kích hoạt hoặc bịa damage. Giữ quyền quyết định hành động có chủ ý cho người chơi.\nĐại Đạo Ma Tôn: Luôn hoạt động; +10% Base STR/DEF/SKL/VIT; không đổi Core cost. STR tăng sát thương vật lý/đánh thường; DEF tăng giảm sát thương và Critical Resistance; SKL tăng Critical, Evasion Resistance và skill damage; VIT tăng Max HP và Evasion. Sau mỗi lượt Cao Minh: hồi 10% Max HP, +20% Attack và +20 điểm % Critical; đồng đội +50 điểm % Critical; Critical cap 100%.\nHuyết Ma Tứ Liên: 30% mỗi lượt TẤN CÔNG hợp lệ; Đúng 4 trảm, 170% Weapon DMG; Chảy máu 3 turn x 5% Max HP.\nMa Tâm Trấn Hồn: 20% mỗi lượt TẤN CÔNG hợp lệ; Đúng 4 trảm cùng điểm, 130% Weapon DMG; Choáng phản ứng hiện tại.\nHuyết Ảnh Ma Độn: 20% mỗi lượt TẤN CÔNG hợp lệ; Dịch chuyển theo Huyết Ma Kiếm, đúng 2 trảm, 147% Weapon DMG.\nThiên Ma Bộ: 30% khi TẤN CÔNG hoặc NÉ TRÁNH; +50 điểm phần trăm Evasion trong 3 turn; không chặn AoE bắt buộc.\nHuyết Sát Kiếm Ấn: 50% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate; +25% DMG; Chảy máu 2 turn x 3% Max HP.\nPhá Giáp Ma Kiếm: 48% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate; +20% DMG; Xuyên giáp 10% trong 2 turn.\nMa Tâm Chấn: 45% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate; +15% DMG; Choáng phản ứng hiện tại.\nHuyết Độc Ma Khí: 47% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate; +20% DMG; Trúng độc 2 turn x 3% Max HP.\nHuyết Liệt Ma Ấn: 51% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate; +20% DMG; Chảy máu 2 turn x 4% Max HP.\nHuyết Ma Nhị Thập Tứ Trảm: Mỗi 3 combat turn khi TẤN CÔNG; Đúng 24 trảm, mỗi trảm 115% current Weapon DMG; bỏ qua Evasion.\nCác con số chỉ là lớp gameplay; không biến thành lời thoại hay tri thức của nhân vật.";
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
    if (root.equals("exploration") || root.equals("communication") || root.equals("visualAreaKey") ||
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
        if (!value.isEmpty() && value.length() <= 700 && !com.rabpit.backroom.core.HiddenExitStreak.failedThisTurn(rolls)) state.put("location", value);
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
        current.put("name", oldPlayer != null ? oldPlayer.optString("name", "Cao Minh") : "Cao Minh");
        if (oldPlayer != null && oldPlayer.has("codename")) current.put("codename", oldPlayer.get("codename"));
        state.put("player", current);
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
    for (String root : new String[] {"communication", "exploration", "visualAreaKey", "visualEventKey", "entityEncounterKey"}) {
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
    JSONObject visibleRolls = new JSONObject(rolls.toString());
    visibleRolls.remove(com.rabpit.backroom.core.HiddenExitStreak.ROLL_KEY);
    String hiddenExitDirective = com.rabpit.backroom.core.HiddenExitStreak.gmDirective(before, rolls);
    String actionRuntimeContext = requireGameCore().currentActionContext();
    String actionKindForPrompt = new JSONObject(actionRuntimeContext).optString("kind", "EXECUTE");
    String actionDirective = "ACTION TYPE = " + actionKindForPrompt + ". " +
      ("SEARCH".equals(actionKindForPrompt) ? "SEARCH HARD LOCK: khảo sát có hệ thống location hiện tại, không tự chuyển sang location mới; SEARCH vẫn roll entityEncounter theo tỷ lệ Level và có thể khởi tạo roaming Entity mới; vẫn có thể gặp Survivor, tìm clue/hazard/exit evidence nhưng không đảm bảo có kết quả hay loot. " :
       "EXPLORE".equals(actionKindForPrompt) ? "EXPLORE HARD LOCK: chủ động mở rộng known space và có thể đổi location; EXPLORE roll Entity theo cùng cơ chế với SEARCH và EXECUTE; có thể gặp Entity hoặc Survivor, hazard/exit opportunity nhưng không đảm bảo Exit; nếu có lựa chọn định hướng quan trọng thì trả quyền quyết định cho người chơi. " :
       "EXECUTE HARD LOCK: đây là freeform intent của người chơi; phân giải đúng hành động đã nhập, không tự đổi mục tiêu; EXECUTE vẫn roll Entity và có thể khởi tạo roaming encounter mới. ");
    actionDirective = actionDirective + "\n" + hiddenExitDirective;
    String packet = com.rabpit.backroom.core.knowledge.KnowledgeContextEngine.build(
      MainActivity.this, before.toString(), action, visibleRolls.toString());
    String feedback = auditFeedback != null && auditFeedback.length() > 0
      ? "\n\nAUDIT FEEDBACK HARD — sửa đúng các lỗi này, không thay đổi dữ kiện khác:\n" + auditFeedback.toString()
      : "";
    String healingItemDirective = "ITEM AUTHORITY: chỉ Game Core offline được phát Item khi Entity bị tiêu diệt. Mỗi Entity bị tiêu diệt chắc chắn rơi một Item từ Item Registry. SEARCH/EXPLORE không tạo Item. Băng gạc hồi 10 HP, Thuốc sát trùng hồi 20 HP, tiêu hao nguyên đơn vị. GM chỉ kể Item đã được Core cấp, không được tạo hoặc chuyển Item.";
    String luciaScoutDirective = "LUCIA SCOUT: hỗ trợ trinh sát mối nguy, không cộng tỉ lệ loot hoặc sinh Item qua khám phá.";
    return "ITEM WHOLE UNIT HARD LOCK: Item tiêu hao chỉ tính theo số lượng nguyên; dùng bao nhiêu trừ bấy nhiêu. Không theo dõi hoặc tạo biến thể một nửa, còn ít, sắp hết hay rỗng; không tự tạo vỏ sau khi dùng. Dùng/Chuyển/Bỏ chỉ thực hiện bằng nút item và Core, không kể rằng đã thành công qua hành động văn bản.\n" + actionDirective + "\n" + healingItemDirective + "\n" + luciaScoutDirective + "\n" + "\nLUCIA FOLLOWER HARD LOCK: Lucia \"Lục\", nữ 19 tuổi, con người, binh nhì và chỉ huy cấp tiểu đội đặc nhiệm. luciaEncounter chỉ roll khi EXPLORE ở Level 0, xác suất 50%, và chỉ success=true mới cho cô xuất hiện. Sau lần gặp đầu, không roll lại. Nếu Party còn chỗ cô gia nhập follower; nếu đầy thì giữ present + joinPending, không đuổi thành viên khác. Character Stats theo 1.1.93a: Base STR/DEF/SKL/VIT đều bắt đầu 5; baseMaxHp 50 và Max HP derive từ VIT. Trang bị đúng 3 slot: M4A1 cá nhân hóa với laser xanh 5mW, dao găm chiến đấu, đồng hồ định vị quân sự mất tín hiệu vệ tinh. Đạn khởi đầu 150 viên gồm 60 đang nạp và 90 dự phòng; đây là nguồn đạn riêng, không chiếm 3 loại vật phẩm quà tặng. Inventory quà tặng tối đa 3 loại, tối đa 100 mỗi loại. Ở Level 0, Lucia chỉ nghi ngờ tiếng động giờ thứ 4 là Hound; không được xác nhận Hound cư trú ở Level 0. Không tự thêm năng lực siêu nhiên hoặc lore.\nACTION_RUNTIME: " + actionRuntimeContext + "\n" +
      "Bạn là Game Master của text game Backrooms. Trả DUY NHẤT JSON hợp lệ, không markdown. " +
      "KNOWLEDGE PACKET là context đã được Context Builder chọn từ in-game database theo state/scene/present actors/action/story. " +
      "Source trace trong packet chỉ dùng hậu trường; không để nhân vật nói tên record/file/anchor. UNKNOWN phải giữ UNKNOWN. " +
      "Người chơi chỉ điều khiển hành động có chủ ý của Cao Minh; GM không tự chọn thay. GAMEPLAY_ROLLS do Android sinh là bất biến. " +
      "POV HARD LOCK: người chơi nhập vai trực tiếp Cao Minh. Mọi văn xuôi gameplay phải kể ở ngôi thứ hai giới hạn từ trải nghiệm của Cao Minh: gọi Cao Minh là 'bạn' và mô tả những gì bạn trực tiếp thấy, nghe, cảm nhận hoặc có cơ sở biết. Không kể Cao Minh ở ngôi thứ ba bằng 'Cao Minh', 'hắn', 'anh ta' hoặc như một nhân vật đang được quan sát từ bên ngoài, trừ khi đó là lời thoại tự nhiên của NPC đang gọi hoặc nói về Cao Minh. Không tự viết suy nghĩ, quyết định, lời thoại hay hành động có chủ ý mới thay cho người chơi; chỉ thuật lại hậu quả hợp lệ của hành động người chơi đã nhập và các phản ứng ngoài quyền kiểm soát có căn cứ từ state/canon. NPC và Entity vẫn được kể bình thường từ góc nhìn mà Cao Minh có thể nhận biết. " +
      "Bạn KHÔNG được trả state hoàn chỉnh. Chỉ đề xuất state change bằng ops; Android sẽ kiểm và có thể từ chối từng operation. " +
      "Nếu meta=true, chỉ trả thông tin được hỏi, ops=[] và snapshotEvent=false. Không nhắc database/context/state/roll/prompt trong văn xuôi.\n\n" +
      "BUDGETED KNOWLEDGE PACKET:\n" + packet +
      "\n\nGAMEPLAY_ROLLS:\n" + visibleRolls.toString() +
      "\n\nPLAYER INPUT:\n" + action +
      feedback +
      "\n\nOPERATION TYPES: set_location{value}; set_level{level}; patch_player{patch}; " +
      "party_upsert{member}; party_remove{name}; flag_patch{root,value}. " +
      "Chỉ dùng flag root: exploration, communication, iris, syvial, jeff, jane, madGod, survivorRegistry, survivorsConfirmed, entitiesConfirmedLocal, visualAreaKey, visualEventKey, entityEncounterKey, reunionPath. " +
      "Inventory chỉ thay đổi do Core khi tiêu diệt Entity hoặc nút Inventory UI; GM không có quyền thay đổi. " +
      "ENTITY OVERLAY HARD LOCK: với Entity đang trực tiếp xuất hiện hoặc đối đầu trong cảnh hiện tại, dùng flag_patch root=entityEncounterKey value=canonical Entity key đúng tên asset bỏ .webp, ví dụ hound, smiler, skin-stealer, slenderman, jeff_the_killer, jane_the_killer. Nếu Entity bị tiêu diệt, Cao Minh chạy trốn hoặc thoát khỏi Entity, Entity rời cảnh, biến mất, hoặc không còn trực tiếp hiện diện/đối đầu, bắt buộc đặt entityEncounterKey thành chuỗi rỗng ngay trong lượt đó. entityEncounterKey chỉ là trạng thái hiện diện trực quan hiện tại, không phải lịch sử encounter. Không dùng mã cũ hoặc alias theo Level. " +
      "ROAMING KILLER HARD LOCK: Jeff the Killer và Jane the Killer dùng cùng entityEncounter và cùng roamingEntityKey với mọi Entity khác. Mỗi entityEncounter thành công chỉ chọn đúng một canonical Entity key; không có roll Jeff/Jane độc lập và không được tạo encounter thứ hai trong cùng lượt. " +
      "ENTITY ROAMING HARD LOCK: mọi Entity trong LOCAL ROAMING POOL đều có thể lang thang/incursion qua bất kỳ Level 0-6. Khi rolls.entityEncounter.success=true và rolls.roamingEntityKey có giá trị, encounter thường bắt buộc dùng đúng canonical key đó. LOCAL ROAMING POOL: hound, clump, duller, deathmoth, hostile_faceling, false_puddle, paintings, smiler, skin-stealer, predatory_window, biological_pipeline, wretch, cable_mimic, the_beast_of_level_5, hotel_corpse_lure, slenderman. Jeff the Killer và Jane the Killer nằm trong cùng LOCAL ROAMING POOL và chỉ xuất hiện khi roamingEntityKey chọn đúng canonical key jeff_the_killer hoặc jane_the_killer. " +
      "ENTITY ASSET LOCAL HARD LOCK: hình Entity chỉ lấy từ APK assets/entity qua file:///android_asset/entity/<canonical-key>.webp; cấm mã Entity legacy, alias theo Level, manifest từ xa hoặc ảnh Entity từ mạng. " +
      "LIFEFORM TRIO HARD LOCK: lifeformEncounter là một roll độc lập đúng 2% cho cả họ Lifeform, không phải 2% cho từng cá thể. Khi success=true, dùng đúng lifeformEntityKey đã roll. Mỗi Lifeform có đúng 3 skill; damage skill là % của Basic Attack, không buff stat ATK, và chỉ gây Bleed/Poison. Ba proc dùng một roll độc quyền theo thứ tự 25% / 20% / 10%, tương ứng 115% / 125% / 140% Basic Attack; skill 1 gây Bleed, skill 2 gây Poison, skill 3 gây Bleed + Poison. Ba Entity này không nằm trong shared roaming pool. " +
      "JSON bắt buộc: {\"reply\":\"phản hồi Game Master bằng tiếng Việt tự nhiên\",\"ops\":[],\"snapshotEvent\":{\"shouldGenerate\":false,\"kind\":\"\",\"reason\":\"\"}}";
  }

  private JSONArray localKnowledgeIssues(JSONObject before, JSONObject generated) throws Exception {
    JSONObject result = new JSONObject(com.rabpit.backroom.core.knowledge.KnowledgeLocalValidator.validate(
      MainActivity.this, before.toString(), generated.toString()));
    JSONArray issues = result.optJSONArray("issues");
    return issues == null ? new JSONArray() : issues;
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
          if (requireGameCore().blocksTextItemAction(action)) {
            JSONObject blocked = new JSONObject(requireGameCore().processRule(stateJson, action));
            emit("backroomTurn", blocked.getJSONObject("state").toString());
            return;
          }
          JSONObject combatResult = new JSONObject(requireGameCore().processCombat(stateJson, actionKind, action));
          if (combatResult.optBoolean("handled", false)) {
            emit("backroomTurn", combatResult.getJSONObject("state").toString());
            return;
          }
          JSONObject actionStart = new JSONObject(requireGameCore().beginAction(stateJson, actionKind, action));
          if (!actionStart.optBoolean("handled", false)) {
            throw new Exception("Action Runtime từ chối hành động: " + actionStart.optString("error", "action_start_failed"));
          }
          JSONObject localResult = new JSONObject(requireGameCore().processRule(stateJson, action));
          if (localResult.optBoolean("handled", false)) {
            emit("backroomTurn", localResult.getJSONObject("state").toString());
            return;
          }
          JSONObject before = new JSONObject(stateJson);
          boolean meta = isMetaAction(action);
          JSONObject rolls = makeGameplayRolls(before, actionKind, action, meta);

          JSONObject generated = parseModelJson(generateText(writerPrompt(before, action, rolls, null)));
          String reply = generated.optString("reply", "").trim();
          if (reply.isEmpty()) throw new Exception("AI trả về phản hồi rỗng, lượt này không được ghi.");

          JSONObject candidateState = meta
            ? new JSONObject(before.toString())
            : applyModelOperations(before, generated.optJSONArray("ops"), rolls, action);
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

          JSONObject state = candidateState;
          if (!meta) {
            state = new JSONObject(com.rabpit.backroom.core.knowledge.StoryContinuityReducer.apply(
              before.toString(), state.toString(), action));
          }

          int oldLevel = currentLevel(before);
          int newLevel = currentLevel(state);
          int mentioned = mentionedLevel(state);
          if (mentioned >= 0 && mentioned != oldLevel && canTransition(before, rolls)) {
            newLevel = mentioned;
            state.put("level", new JSONObject().put("number", newLevel).put("name", levelName(newLevel)));
            state.put("title", "Level " + newLevel + " – " + levelName(newLevel));
          }
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
            com.rabpit.backroom.core.HiddenExitStreak.apply(before, state, rolls, oldLevel, newLevel);
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

          state.put("canonVersion", DRIVE_CANON_VERSION);
          JSONArray log = state.optJSONArray("log");
          if (log == null) log = new JSONArray();
          log.put(new JSONObject().put("role", "player").put("text", action));
          log.put(new JSONObject().put("role", "gm").put("text", reply));
          state.put("log", log);
          emit("backroomTurn", state.toString());
        } catch (Exception e) {
          try { requireGameCore().abortAction("pipeline_error"); } catch (Exception ignored) {}
          emit("backroomError", e.getMessage() == null ? "Không thể xử lý lượt." : e.getMessage());
        }
      });
    }



    @JavascriptInterface public void itemAction(String stateJson, String requestJson) {
      io.execute(() -> {
        try {
          emit("backroomItemAction", requireGameCore().processItemAction(stateJson, requestJson));
        } catch (Exception error) {
          emit("backroomItemAction", "{\"handled\":false,\"error\":\"item_action_failed\"}");
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
