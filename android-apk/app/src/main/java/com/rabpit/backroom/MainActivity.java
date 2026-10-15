package com.rabpit.backroom;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
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
import com.rabpit.backroom.core.LevelRouteRegistry;
import com.rabpit.backroom.core.ProviderResponseContract;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {
  private static final int DIAGNOSTIC_EXPORT_REQUEST = 7401;
  private final Object diagnosticExportLock = new Object();
  private String pendingDiagnosticExport;
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
  private static final String KNOWLEDGE_CANON_VERSION = "KNOWLEDGE-DB-SCHEMA-1";
  private static final SecureRandom GAME_RNG = new SecureRandom();

  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    DiagnosticLog.initialize(getApplicationContext());
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
      DiagnosticLog.error("startup.webview", error);
      showStartupFallback(error);
    }
  }


  private String requestDiagnosticExport(String webJson, boolean includeSensitive) {
    try {
      String report = DiagnosticLog.build(webJson, includeSensitive, "",
        0, lastGeminiWorker, lastGeminiModel);
      synchronized (diagnosticExportLock) {
        if (pendingDiagnosticExport != null) return "{\"ok\":false,\"error\":\"export_already_open\"}";
        pendingDiagnosticExport = report;
      }
      runOnUiThread(() -> {
        try {
          Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
          intent.addCategory(Intent.CATEGORY_OPENABLE);
          intent.setType("application/json");
          String date = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(new java.util.Date());
          intent.putExtra(Intent.EXTRA_TITLE, "Backroom-Diagnostic-" + date + ".json");
          startActivityForResult(intent, DIAGNOSTIC_EXPORT_REQUEST);
        } catch (Exception error) {
          synchronized (diagnosticExportLock) { pendingDiagnosticExport = null; }
          DiagnosticLog.error("diagnostic.picker_error", error);
          emit("backroomDiagnosticExportResult", "{\"saved\":false,\"error\":\"document_picker_unavailable\"}");
        }
      });
      return "{\"ok\":true}";
    } catch (Exception error) {
      DiagnosticLog.error("diagnostic.build_error", error);
      return "{\"ok\":false,\"error\":\"diagnostic_build_failed\"}";
    }
  }

  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode != DIAGNOSTIC_EXPORT_REQUEST) return;
    String report;
    synchronized (diagnosticExportLock) {
      report = pendingDiagnosticExport;
      pendingDiagnosticExport = null;
    }
    if (resultCode != RESULT_OK || data == null || data.getData() == null) {
      emit("backroomDiagnosticExportResult", "{\"saved\":false,\"cancelled\":true}");
      return;
    }
    try {
      Uri uri = data.getData();
      if (report == null) throw new IllegalStateException("missing_diagnostic_report");
      try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
        if (output == null) throw new java.io.IOException("Could not open selected file");
        output.write(report.getBytes(StandardCharsets.UTF_8));
      }
      DiagnosticLog.record("diagnostic.saved", "Saved to user-selected document");
      emit("backroomDiagnosticExportResult", "{\"saved\":true}");
    } catch (Exception error) {
      DiagnosticLog.error("diagnostic.write_error", error);
      emit("backroomDiagnosticExportResult", "{\"saved\":false,\"error\":\"file_write_failed\"}");
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
        DiagnosticLog.error("startup.core", error);
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
      "st.textContent='button{transition:transform 80ms ease,background 120ms ease,border-color 120ms ease;touch-action:manipulation;-webkit-tap-highlight-color:rgba(255,255,255,.12)}button:active:not(:disabled){transform:scale(.965);background:#303840;border-color:#77828c}button:disabled{opacity:.48;cursor:not-allowed}.snapshot{position:relative;overflow:hidden}.snapshot .snapshot-bg{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;z-index:1}.snapshot .snapshot-character{position:absolute;right:0;bottom:0;height:97%;width:auto;max-width:100%;object-fit:contain;object-position:right bottom;z-index:2;pointer-events:none;image-rendering:auto}.snapshot-placeholder{display:none}.snapshot .snapshot-equipment-badge{position:absolute;right:8px;top:8px;z-index:4;padding:6px 8px;border:1px solid rgba(218,180,88,.62);border-radius:8px;background:rgba(7,9,11,.78);color:#f2dfad;font-size:10px;pointer-events:none}.snapshot .snapshot-equipment-badge b{display:block}.snapshot-placeholder b{font-size:12px;letter-spacing:.16em}.snapshot-placeholder small{color:#56616a}.message.pending{opacity:.72}.message.pending .text{color:#aeb7be}';" +
      "document.head.appendChild(st);" +
      "function scrollBottom(){var l=document.getElementById('log');if(l)requestAnimationFrame(function(){l.scrollTop=l.scrollHeight;});}" +
      "function equippedItem(s){try{var e=state&&state.equipment||{};if(e.set&&String(e.set.id||e.set)==='madgod:set'&&(s==='armor'||s==='weapon'))return e.set;var direct=e[s];if(direct)return typeof direct==='string'?{id:direct,name:direct}:direct;var members=state&&state.partyDetails&&state.partyDetails.members;if(Array.isArray(members)){var caoMinh=members.find(function(m){return String(m&&m.id)==='cao_minh'});var value=caoMinh&&caoMinh.equipment&&caoMinh.equipment[s];if(value)return typeof value==='string'?{id:value,name:value}:value}return null}catch(e){return null}}function madGodEquipped(s){var x=equippedItem(s);return !!(x&&String(x.id||x.name||x).toLowerCase().indexOf('madgod')>=0)}function caoMinhOverlaySource(){return state&&state.combat&&state.combat.active===true?'CAO_MINH_OVERLAY_ENTITY_ENCOUNTER.png':'CAO_MINH_OVERLAY_EXPLORER_IDLE.png'}function appendEquipmentBadge(b){if(!madGodEquipped('armor')&&!madGodEquipped('weapon'))return;var d=document.createElement('div');d.className='snapshot-equipment-badge';var a=equippedItem('armor'),w=equippedItem('weapon');d.textContent='MadGod Set';b.appendChild(d)}" +
      "function visualSceneKey(){var a=window.BackroomSnapshotAssets;return a?a.sceneKey(state):'0|'+String(state&&state.location||'');}function renderSnapshot(){var box=document.getElementById('snapshot');if(!box)return;box.textContent='';var a=window.BackroomSnapshotAssets;var scene=a?a.resolve(state):null;var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=scene?scene.src:'file:///android_asset/level_snapshots/level_0.webp';bg.alt=scene?(scene.fallback?'Ảnh tham chiếu Level '+scene.parent+'; chưa có ảnh riêng cho '+scene.nodeId:'Ảnh '+scene.nodeId+', mẫu '+(scene.variantIndex+1)+'/'+scene.variantCount):'Ảnh Level 0';bg.onerror=function(){this.onerror=null;this.src='file:///android_asset/level_snapshots/level_0.webp';};box.appendChild(bg);var caoMinh=document.createElement('img');caoMinh.className='snapshot-character';caoMinh.src=caoMinhOverlaySource();caoMinh.onerror=function(){this.onerror=null;this.src='CAO_MINH_OVERLAY_EXPLORER_IDLE.png';};caoMinh.alt='Cao Minh';box.appendChild(caoMinh);}" +
      "var __baseRenderSnapshot=renderSnapshot,__entityOverlay={key:'',url:'',revision:0,anchor:'left-bottom',maxHeight:.97,loading:''};" +
      "var __entityKeys=['hound','clump','duller','deathmoth','hostile_faceling','false_puddle','paintings','smiler','skin-stealer','predatory_window','biological_pipeline','wretch','cable_mimic','the_beast_of_level_5','hotel_corpse_lure','jeff_the_killer','jane_the_killer','slenderman','diep_minh','blackroot_sentinel','sinew_strider','hollow_grasper'];" +
      "function normalizeEntityKey(v){if(typeof v!=='string')return '';var k=v.trim().toLowerCase();return __entityKeys.indexOf(k)>=0?k:'';}" +
      "function activeEntityKey(){var c=state&&state.combat;if(!c||c.active!==true)return '';if(window.backroomVisualEncounterId!==c.encounterId){window.backroomVisualEncounterId=c.encounterId;window.backroomCombatEntityVisualKey='';}return normalizeEntityKey(window.backroomCombatEntityVisualKey||c.entityKey);}" +
      "function requestEntityOverlay(key){if(!key||__entityOverlay.loading===key)return;if(!window.Android||typeof Android.requestEntityOverlay!=='function')return;__entityOverlay.loading=key;Android.requestEntityOverlay(key);}" +
      "/* ENTITY_PRECOMPUTED_ALPHA_BOUNDS_R02 */var __entityVisualBounds={'hound':{left:0.023148,bottom:0.766927,visibleH:0.525391},'clump':{left:0.041667,bottom:0.800781,visibleH:0.626953},'duller':{left:0.265046,bottom:0.914062,visibleH:0.828125},'deathmoth':{left:0.021991,bottom:0.832682,visibleH:0.663411},'hostile_faceling':{left:0.018519,bottom:0.895182,visibleH:0.794922},'false_puddle':{left:0.008102,bottom:0.951172,visibleH:0.890625},'paintings':{left:0.017361,bottom:0.969401,visibleH:0.916016},'smiler':{left:0.263889,bottom:0.869141,visibleH:0.738932},'skin-stealer':{left:0.077546,bottom:0.887370,visibleH:0.791016},'predatory_window':{left:0.004630,bottom:0.800781,visibleH:0.621745},'biological_pipeline':{left:0.035880,bottom:0.952474,visibleH:0.884766},'wretch':{left:0.012731,bottom:0.866536,visibleH:0.731771},'cable_mimic':{left:0.008102,bottom:0.990885,visibleH:0.990885},'the_beast_of_level_5':{left:0.020833,bottom:0.971354,visibleH:0.971354},'hotel_corpse_lure':{left:0.123843,bottom:0.857422,visibleH:0.732422},'jeff_the_killer':{left:0.018519,bottom:0.975260,visibleH:0.949219},'jane_the_killer':{left:0.072917,bottom:0.845052,visibleH:0.692708},'slenderman':{left:0.011574,bottom:0.864583,visibleH:0.737630},'diep_minh':{left:0.002315,bottom:0.858724,visibleH:0.719401},'blackroot_sentinel':{left:0.037037,bottom:0.997619,visibleH:0.995238},'sinew_strider':{left:0.045503,bottom:0.964286,visibleH:0.869643},'hollow_grasper':{left:0.037037,bottom:0.997619,visibleH:0.966071}};" +
      "/* ENTITY_SHARED_GROUND_PLANE_R02 */function normalizeEntityVisual(img){try{var key=String(img&&img.alt||__entityOverlay.key||''),b=__entityVisualBounds[key];if(!b)return;var scale=Math.max(1,Math.min(2.2,.94/(.97*Math.max(.05,Number(b.visibleH)||1)))),rect=img.getBoundingClientRect(),ew=Math.max(1,Number(rect.width)||img.clientWidth||1),eh=Math.max(1,Number(rect.height)||img.clientHeight||1),x=-Number(b.left||0)*ew*scale,y=(1-Number(b.bottom||1))*eh*scale;img.style.transformOrigin='left bottom';img.style.transform='translate('+x.toFixed(2)+'px,'+y.toFixed(2)+'px) scale('+scale.toFixed(3)+')';}catch(_){img.style.transformOrigin='left bottom';img.style.transform='none';}}" +
      "function appendEntityOverlay(){var box=document.getElementById('snapshot');if(!box)return;box.style.position='relative';box.style.overflow='hidden';var old=box.querySelector('.snapshot-entity');if(old)old.remove();var key=activeEntityKey();if(!key){__entityOverlay={key:'',url:'',revision:0,anchor:'left-bottom',maxHeight:.97,loading:''};return;}if(__entityOverlay.key!==key){__entityOverlay.url='';__entityOverlay.key=key;}if(!__entityOverlay.url){__entityOverlay.url='file:///android_asset/entity/'+key+'.webp';}var img=document.createElement('img');img.className='snapshot-entity'+(window.backroomAnimateEntityWave?' snapshot-entity--wave-enter':'');window.backroomAnimateEntityWave=false;img.alt=key;img.style.position='absolute';img.style.bottom='0';img.style.width='auto';img.style.maxWidth='55%';img.style.height=Math.round(Math.max(.2,Math.min(1,Number(__entityOverlay.maxHeight)||.97))*100)+'%';img.style.objectFit='contain';img.style.pointerEvents='none';img.style.zIndex='2';img.style.left='0';img.style.objectPosition='left bottom';img.style.opacity='0';img.onload=function(){if(img.isConnected===false||activeEntityKey()!==key)return;normalizeEntityVisual(img);img.dataset.entityStatus='ready';img.style.opacity='1';if(typeof window!=='undefined'&&typeof window.backroomSyncCombatPortrait==='function')window.backroomSyncCombatPortrait();};img.onerror=function(){if(img.isConnected===false||activeEntityKey()!==key)return;img.dataset.entityStatus='error';img.style.opacity='0';var notice=document.createElement('span');notice.className='snapshot-entity-error';notice.textContent=key.replace(/_/g,' ');notice.style.cssText='position:absolute;left:3%;bottom:9%;z-index:3;color:#f4d9a2';box.appendChild(notice);};img.src=__entityOverlay.url;box.appendChild(img);}" +
      "renderSnapshot=function(){__baseRenderSnapshot();appendEntityOverlay();};window.backroomSwitchCombatEntityWave=function(rawKey,index){var c=state&&state.combat;if(!c||c.active!==true)return;var key=normalizeEntityKey(rawKey);if(!key)return;var identity=String(c.encounterId||'')+':'+String(index||0)+':'+key;if(window.backroomVisualEntityInstance===identity)return;window.backroomVisualEntityInstance=identity;window.backroomCombatEntityVisualKey=key;window.backroomAnimateEntityWave=true;renderSnapshot();};" +
      "window.backroomEntityOverlay=function(payload){try{var r=JSON.parse(payload);var key=normalizeEntityKey(r.entityKey);if(!key)return;__entityOverlay.loading='';if(key!==activeEntityKey())return;__entityOverlay.key=key;__entityOverlay.url=String(r.url||'');__entityOverlay.revision=Number(r.revision||1);__entityOverlay.anchor=String(r.anchor||'left-bottom');__entityOverlay.maxHeight=Number(r.maxHeight||.97);renderSnapshot();}catch(e){__entityOverlay.loading='';}};" +
      "window.backroomEntityOverlayError=function(payload){__entityOverlay.loading='';};" +
      "/* LUCIA_COMBAT_OVERLAY_R01 */var __luciaBaseRenderSnapshot=renderSnapshot;" +
      "function luciaCombatVisible(){var c=state&&state.combat;if(!c||c.active!==true)return false;var members=state&&state.partyDetails&&Array.isArray(state.partyDetails.members)?state.partyDetails.members:null;if(members){for(var i=0;i<members.length;i++){var m=members[i];if(String(m&&m.id||'').toLowerCase()==='lucia'&&String(m&&m.presence||'ACTIVE').toUpperCase()==='ACTIVE')return true;}return false;}var party=state&&state.party;if(Array.isArray(party)){for(var j=0;j<party.length;j++){var p=party[j],id=typeof p==='string'?p:(p&&p.id);if(String(id||'').toLowerCase()==='lucia')return true;}}return false;}" +
      "function appendLuciaCombatOverlay(){var box=document.getElementById('snapshot');if(!box)return;var old=box.querySelector('.snapshot-lucia');if(old)old.remove();if(!luciaCombatVisible())return;var img=document.createElement('img');img.className='snapshot-lucia';img.alt='Lucia \"Lục\"';img.src='file:///android_asset/LUCIA_LUC_OVERLAY.png';img.style.position='absolute';img.style.right='0';img.style.bottom='0';img.style.height='97%';img.style.width='auto';img.style.maxWidth='100%';img.style.objectFit='contain';img.style.objectPosition='right bottom';img.style.pointerEvents='none';img.style.zIndex='2';img.style.filter='drop-shadow(0 4px 8px rgba(0,0,0,.58))';box.appendChild(img);}" +
      "renderSnapshot=function(){if(typeof window!=='undefined'&&window.backroomExplorationTransition)window.backroomExplorationTransition.cancel();__luciaBaseRenderSnapshot();appendLuciaCombatOverlay();if(typeof window!=='undefined'&&typeof window.backroomSyncCombatPortrait==='function')window.backroomSyncCombatPortrait();};" +
      "var snapshotBusy=false;function requestSnapshot(){var s=document.getElementById('status');if(s)s.textContent='Snapshot chưa được cấu hình.';}" +
      "window.requestSnapshot=requestSnapshot;" +
      "window.__backroomProvider='Gehihi';window.backroomProvider=function(provider){window.__backroomProvider=provider||'AI';var s=document.getElementById('status');if(s)s.textContent=window.__backroomProvider+' đang xử lý lượt…';};" +
      "var oldRender=window.render;if(typeof oldRender==='function'){window.render=function(){oldRender();renderSnapshot();scrollBottom();};}" +
      "var actions=document.querySelector('.actions');if(actions&&!document.getElementById('snapshotButton')){var b=document.createElement('button');b.id='snapshotButton';b.type='button';b.textContent='Snapshot chưa cấu hình';b.disabled=true;var wide=actions.querySelector('.wide');if(wide)actions.insertBefore(b,wide);else actions.appendChild(b);}" +
      "var oldTurn=window.backroomTurn;window.backroomTurn=function(json){var cinema=window.backroomExplorationTransition;var previousScene=cinema?cinema.capture(state):null;if(typeof oldTurn==='function')oldTurn(json);document.querySelectorAll('[data-pending=\"1\"]').forEach(function(n){n.remove();});var s=document.getElementById('status');var ev=state&&state._snapshotEvent;var allowed={LEVEL_CHANGE:1,SPECIAL_REGION:1,ENTITY_CONFIRMED:1,PERSON_ENCOUNTER:1,MAJOR_VISUAL_EVENT:1};var should=!!(ev&&ev.shouldGenerate===true&&allowed[String(ev.kind||'').toUpperCase()]);if(should){if(s)s.textContent='Turn '+state.turn+' có sự kiện hình ảnh đặc biệt. Đang tạo snapshot…';}else{if(s)s.textContent='Turn '+state.turn+' đã xử lý bằng '+(window.__backroomProvider||'AI')+'. Snapshot cũ được giữ nguyên.';}renderSnapshot();scrollBottom();if(cinema&&previousScene)cinema.play(previousScene,state);if(should)requestSnapshot();};" +
      "var oldError=window.backroomError;window.backroomError=function(message){document.querySelectorAll('[data-pending=\"1\"]').forEach(function(n){n.remove();});if(typeof oldError==='function')oldError(message);var s=document.getElementById('status');if(s)s.textContent=String(message||'').indexOf('Lỗi mạng/DNS:')===0?message:'Lỗi '+(window.__backroomProvider||'AI')+': '+message;scrollBottom();};" +
      "window.backroomSnapshotProvider=function(provider){var s=document.getElementById(\'status\');if(s)s.textContent=(provider||\'AI\')+\' đang tạo snapshot…\';};" +
      "window.backroomSnapshot=function(payload){snapshotBusy=false;try{var r=JSON.parse(payload);if(!state||Number(r.turn)!==Number(state.turn))return;if(!r.dataUri)return;localStorage.setItem('backroom-apk-snapshot',JSON.stringify({turn:r.turn,sceneKey:visualSceneKey(),model:r.model||'AI',dataUri:r.dataUri}));renderSnapshot();var s=document.getElementById('status');if(s)s.textContent='Snapshot Turn '+state.turn+' đã tạo bằng '+(r.model||'AI')+'.';}catch(e){var s=document.getElementById('status');if(s)s.textContent='Snapshot trả về không hợp lệ.';}};" +
      "window.backroomSnapshotError=function(payload){snapshotBusy=false;try{var r=JSON.parse(payload);if(state&&Number(r.turn)!==Number(state.turn))return;var s=document.getElementById('status');if(s)s.textContent='Snapshot lỗi: '+(r.message||'Không thể tạo ảnh.');}catch(e){var s=document.getElementById('status');if(s)s.textContent='Snapshot lỗi.';}};" +
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
    return postJson(endpoint, key, authHeader, payload, 60000);
  }

  private String postJson(String endpoint, String key, String authHeader, JSONObject payload, int readTimeoutMs) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(20000);
    connection.setReadTimeout(readTimeoutMs);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty(authHeader, authHeader.equals("Authorization") ? "Bearer " + key : key);
    try (OutputStream output = connection.getOutputStream()) {
      output.write(payload.toString().getBytes("UTF-8"));
    }

    int status = connection.getResponseCode();
    if (status < 200 || status >= 300) DiagnosticLog.record("provider.http_error", "status=" + status);
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
    if (status < 200 || status >= 300) DiagnosticLog.record("provider.http_error", "status=" + status);
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
    if (status < 200 || status >= 300) DiagnosticLog.record("provider.http_error", "status=" + status);
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

  private String geminiMatrixRequest(String prompt, int modelIndex, int keyIndex, int maxOutputTokens, long deadlineMs, boolean auditPacket) throws Exception {
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
    String response = responseText.toString();
    // A valid HTTP response is not a successful lane until the payload can be consumed.
    JSONObject packet = parseModelJson(response);
    if (auditPacket) ProviderResponseContract.requireAudit(packet);
    else ProviderResponseContract.requireWriter(packet);
    return response;
  }

  private String geminiModelMatrixPolicy(String prompt, int[] modelOrder, int excludedKeyIndex, int maxOutputTokens, boolean rememberWorker, long totalBudgetMs, boolean auditPacket) throws Exception {
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
            String result = geminiMatrixRequest(prompt, modelIndex, keyIndex, maxOutputTokens, deadlineMs, auditPacket);
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
    return geminiModelMatrixPolicy(prompt, new int[] {0, 1, 2}, -1, 1800, true, 120_000L, false);
  }

  private String geminiAuditText(String prompt, int excludedIndex) throws Exception {
    return geminiModelMatrixPolicy(prompt, new int[] {2, 1}, excludedIndex, 650, false, 60_000L, true);
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
    String providerJson;
    long apiStartNs = System.nanoTime();
    try {
      providerJson = postJson(base + "/chat/completions", BuildConfig.GEHIHI_API_KEY,
        "Authorization", openAiProviderBody(model, prompt));
    } finally {
      DiagnosticLog.timing(MainActivity.this, "gm.http",
        java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - apiStartNs),
        prompt.length());
    }
    // Never commit a truncated response as a completed story turn.
    JSONObject envelope = new JSONObject(providerJson);
    JSONArray choices = envelope.optJSONArray("choices");
    JSONObject choice = choices != null && choices.length() > 0 ? choices.optJSONObject(0) : null;
    String finish = choice != null ? choice.optString("finish_reason", "") : "";
    if ("length".equals(finish)) throw new Exception("Gehihi đã cắt ngắn phản hồi (giới hạn output tokens). Thử lại lượt.");
    if ("content_filter".equals(finish)) throw new Exception("Gehihi đã chặn nội dung phản hồi.");
    // Gehihi GM is a prose-only writer: normalize at the validated reply boundary.
    return openAiProviderText(providerJson);
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
    if (status < 200 || status >= 300) DiagnosticLog.record("provider.http_error", "status=" + status);
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
    return solText(prompt, 2048, 0.6);
  }

  private String solText(String prompt, int maxOutputTokens, double temperature) throws Exception {
    if (BuildConfig.SOL_API_KEY == null || BuildConfig.SOL_API_KEY.trim().isEmpty()) throw new Exception("SOL_API_KEY chưa được cấu hình.");
    JSONObject body = openAiProviderBody("vgpt/gpt-6.1-sol", prompt)
      .put("reasoning_effort", "low")
      .put("max_tokens", maxOutputTokens)
      .put("temperature", temperature);
    String response = postJson("https://api.vilao.ai/v1/chat/completions",
      BuildConfig.SOL_API_KEY, "Authorization", body, 60000);
    String output = openAiProviderText(response);
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

  private String verifiedWriterReply(String response) throws Exception {
    return ProviderResponseContract.normalizeDirectWriter(response).toString();
  }

  /** Gehihi narrates the turn directly from the writer prompt. */
  private String generateText(String prompt) throws Exception {
    DiagnosticLog.timing(MainActivity.this, "gm.prompt", 0L, prompt.length());
    emit("backroomProvider", "Gehihi");
    return verifiedWriterReply(gehihiText(prompt));
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
      "Show the present scene only, not a montage. Do NOT depict Cao Minh or any player-character body in the generated image; the app overlays Cao Minh separately. " +
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
    if (function.startsWith("backroom"))
      DiagnosticLog.record("bridge." + function, "payloadChars=" + (json == null ? 0 : json.length()));
    String script = "window." + function + "(" + JSONObject.quote(json) + ")";
    runOnUiThread(() -> { if (webView != null) webView.evaluateJavascript(script, null); });
  }

  private String currentNodeId(JSONObject state) {
    return LevelRouteRegistry.currentId(state);
  }

  private int currentLevel(JSONObject state) {
    LevelRouteRegistry.Node node = LevelRouteRegistry.node(currentNodeId(state));
    return node == null ? 0 : node.parent;
  }

  private boolean playerTraverses(String action) {
    return containsAny(action,
      "đi qua", "bước qua", "chạy qua", "vượt qua", "băng qua", "chui qua", "leo qua",
      "đi vào", "bước vào", "tiến vào", "chạy vào", "nhảy vào", "chui vào",
      "đi sang", "bước sang", "tiến sang", "chuyển sang", "dịch chuyển đến",
      "no-clip", "noclip");
  }

  /**
   * P0-3: Enforces Core's exit destination decision. When Core authorized a destination
   * (via determineExitDestination), it is applied verbatim; AI prose/location text can
   * never smuggle a different node. When Core authorized nothing, the level MUST NOT
   * change — any AI-smuggled delta is reverted.
   */
  private void reconcileVisualWorldState(JSONObject before, JSONObject candidateState,
      JSONObject rolls, String action, String coreDestinationId) throws Exception {
    String oldId = currentNodeId(before);
    LevelRouteRegistry.Node previous = LevelRouteRegistry.node(oldId);
    if (previous == null) throw new Exception("Current environment node is invalid.");

    if (coreDestinationId != null) {
      LevelRouteRegistry.Node dest = LevelRouteRegistry.node(coreDestinationId);
      if (dest == null) throw new Exception("Core destination node is invalid.");
      // Defense in depth: Core's destination must be a valid edge from the old node.
      if (!LevelRouteRegistry.canMove(oldId, dest.id))
        throw new Exception("Core destination is not a valid edge.");
      candidateState.put("level", dest.asJson());
      candidateState.put("title", dest.title());
      candidateState.put("location", dest.location());
      return;
    }

    // No Core authorization: level must not change. Revert any AI-smuggled delta.
    candidateState.put("level", before.optJSONObject("level") == null
      ? previous.asJson() : new JSONObject(before.optJSONObject("level").toString()));
    candidateState.put("title", before.optString("title", previous.title()));
    candidateState.put("location", before.optString("location", previous.location()));
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

  private JSONObject makeGameplayRolls(JSONObject state, String actionKind, String action, boolean meta) throws Exception {
    JSONObject rolls = new JSONObject().put("turn", state.optInt("turn", 1)).put("meta", meta);
    if (meta) return rolls;

    String actionKindNormalized = actionKind == null ? "" : actionKind.trim().toUpperCase(java.util.Locale.ROOT);
    boolean exploreAction = "EXPLORE".equals(actionKindNormalized);

    int level = Math.max(0, Math.min(6, currentLevel(state)));
    int[] hazardThresholds = {400, 700, 1000, 1200, 300, 1000, 1200};

    String a = lower(action);
    boolean physical = containsAny(a, "đi", "bước", "chạy", "leo", "mở", "đóng", "chạm", "lục", "tìm", "kiểm tra", "khảo sát", "quét", "scan", "bắn", "phá", "đẩy", "kéo", "tiến", "lùi", "cúi", "nhìn vào", "bò", "nhảy", "đào", "tháo", "đập", "vượt", "đi qua");
    boolean search = containsAny(a, "tìm", "lục", "khám phá", "khảo sát", "kiểm tra", "quét", "scan", "mở", "tháo", "quan sát kỹ", "rà");
    boolean water = containsAny(a, "nước", "water", "almond", "uống", "khát", "chai", "vòi", "hồ", "fountain");
    boolean exitIntent = containsAny(a, "exit", "lối thoát", "thoát", "cửa trắng", "cánh cửa", "ngưỡng", "chuyển level", "sang level", "hành lang phía sau", "đường ra");
    boolean anNhienFollowing = anNhienFollowing(state);

    int anNhienHazardThreshold = anNhienFollowing ? (hazardThresholds[level] * 75 / 100) : hazardThresholds[level];
    JSONObject anNhienHazardCheck = thresholdRoll("anNhienHazardCheck", 10000, 3000, anNhienFollowing && search && water, " Đừng Đụng Vào, Nhìn Là Biết Độc");
    rolls.put("anNhienHazardCheck", anNhienHazardCheck);
    if (anNhienHazardCheck.optBoolean("success", false)) anNhienHazardThreshold = 0;
    rolls.put("hazard", thresholdRoll("hazard", 10000, anNhienHazardThreshold, physical,
      anNhienFollowing ? " -25% Có Gì Đó Sai Sai" : ""));
    int exitThreshold = exitThresholdAndroid(state);
    if (anNhienFollowing) exitThreshold = Math.min(10000, exitThreshold + 200);
    JSONObject anNhienRead = thresholdRoll("anNhienRead", 10000, 2000, anNhienFollowing && search && exitIntent, " Khoan, Để Tôi Đọc Cái Này");
    rolls.put("anNhienRead", anNhienRead);
    if (anNhienRead.optBoolean("success", false)) exitThreshold = Math.min(10000, exitThreshold + 2000);
    JSONObject exitProbe = thresholdRoll("exitProbe", 10000, exitThreshold, exitIntent && (physical || search),
      anNhienRead.optBoolean("success", false) ? " discovery clue +2% An Nhiên +20% đọc dấu Exit" : (anNhienFollowing ? " discovery clue +2% An Nhiên" : " discovery clue"));
    rolls.put("exitProbe", exitProbe);
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

  private JSONArray sanitizedInventory(JSONArray current, JSONArray proposed, JSONObject rolls, String action) throws Exception {
    // The GM is not an inventory authority. Only the Core handles earned loot and UI actions.
    return current == null ? new JSONArray() : new JSONArray(current.toString());
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

  private boolean canTransition(JSONObject before, JSONObject rolls, LevelRouteRegistry.Node requested) throws Exception {
    return com.rabpit.backroom.core.HiddenExitStreak.projectedReady(before, rolls);
  }

  private JSONObject sanitizedPlayer(JSONObject current, JSONObject proposed) throws Exception {
    if (proposed == null) return current == null ? new JSONObject() : new JSONObject(current.toString());
    JSONObject safe = current == null ? new JSONObject() : new JSONObject(current.toString());
    String name = safe.optString("name", "Cao Minh");
    String codename = safe.optString("codename", "");
    for (String key : new String[] {"hp", "condition", "needs", "weapon", "armor"}) if (proposed.has(key)) safe.put(key, proposed.get(key));
    safe.put("name", name).put("codename", codename);
    return safe;
  }

  private boolean presentCharacter(JSONObject state, String key) {
    if (partyHas(state, key)) return true;
    JSONObject flags = state.optJSONObject("flags");
    JSONObject record = flags != null ? flags.optJSONObject(key) : null;
    String continuity = record != null ? lower(record.optString("continuity", "")) : "";
    return containsAny(continuity, "reunited", "with cao minh", "together", "present");
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

  private boolean jsonChanged(Object before, Object after) {
    String left = before == null || before == JSONObject.NULL ? "null" : String.valueOf(before);
    String right = after == null || after == JSONObject.NULL ? "null" : String.valueOf(after);
    return !left.equals(right);
  }

  /** Gehihi narrates the turn directly from player action and Core rolls. Core alone updates game state. */
  private String writerPrompt(JSONObject before, String action, JSONObject rolls, JSONArray pendingNpcEncounters, JSONObject pendingEntityEncounter) throws Exception {
    JSONObject visibleRolls = new JSONObject(rolls.toString());
    visibleRolls.remove(com.rabpit.backroom.core.HiddenExitStreak.ROLL_KEY);
    // P0-A: Pass Core-confirmed pending NPC encounters to the knowledge retriever via a
    // dedicated channel (NOT by faking Party membership). These NPCs are present in the
    // scene this turn but have NOT yet joined the Party. The retriever loads their Canon
    // (personality, speech, relations) so the GM narrates the first meeting correctly.
    // Contract: pendingNpcEncounters = [{characterId, flagKey, name}] from beginAction — Core data only.
    //
    // P0 Entity GM: Pass Core-reserved Entity encounter via pendingEntityEncounter =
    // {entityKey, entityName, encounterId} from beginAction — Core data only, public
    // perception (no exit/Core secrets). The GM must narrate THIS specific Entity.
    JSONObject stateForKnowledge = new JSONObject(before.toString());
    StringBuilder encounterEvents = new StringBuilder();
    if (pendingNpcEncounters != null && pendingNpcEncounters.length() > 0) {
      stateForKnowledge.put("pendingNpcEncounters", pendingNpcEncounters);
      encounterEvents.append("\nCORE_CONFIRMED_ENCOUNTER_EVENTS: ");
      for (int i = 0; i < pendingNpcEncounters.length(); i++) {
        JSONObject enc = pendingNpcEncounters.optJSONObject(i);
        if (enc == null) continue;
        String cid = enc.optString("characterId", "").trim();
        String name = enc.optString("name", cid).trim();
        if (cid.isEmpty()) continue;
        if (i > 0) encounterEvents.append("; ");
        encounterEvents.append(name).append(" (").append(cid).append(")");
      }
      encounterEvents.append(" — HIỆN DIỆN trong cảnh lượt này, CHƯA gia nhập Party. ");
      encounterEvents.append("Kể cuộc gặp đầu tiên tự nhiên; KHÔNG coi họ là thành viên Party trước khi recruit.");
    }
    if (pendingEntityEncounter != null) {
      String entityKey = pendingEntityEncounter.optString("entityKey", "").trim();
      String entityName = pendingEntityEncounter.optString("entityName", entityKey).trim();
      String encounterId = pendingEntityEncounter.optString("encounterId", "").trim();
      if (!entityKey.isEmpty() && !encounterId.isEmpty()) {
        // Merge entityEncounterKey into state copy's flags so KnowledgeContextEngine
        // confirmedEntityVisualId() picks up ENTITY.<KEY>.VISUAL for the knowledge packet.
        JSONObject flags = stateForKnowledge.optJSONObject("flags");
        if (flags == null) {
          flags = new JSONObject();
          stateForKnowledge.put("flags", flags);
        }
        flags.put("entityEncounterKey", entityKey);
        // Also pass as dedicated field for explicit retriever support.
        stateForKnowledge.put("pendingEntityEncounter", pendingEntityEncounter);
        if (encounterEvents.length() == 0) encounterEvents.append("\nCORE_CONFIRMED_ENCOUNTER_EVENTS: ");
        else encounterEvents.append("; ");
        encounterEvents.append("ENTITY ").append(entityName).append(" (").append(entityKey).append(")");
        encounterEvents.append(" — ĐÃ được Core xác nhận chạm trán lượt này (ID: ").append(encounterId).append("). ");
        encounterEvents.append("Kể cảnh đối đầu, DỪNG ở thế giằng co; chưa kể đòn đánh/sát thương trước Battle Start.");
      }
    }
    long canonStartNs = System.nanoTime();
    String canon;
    try {
      canon = com.rabpit.backroom.core.knowledge.KnowledgeContextEngine.build(
        MainActivity.this, stateForKnowledge.toString(), action, visibleRolls.toString());
    } finally {
      DiagnosticLog.timing(MainActivity.this, "canon.build",
        java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - canonStartNs),
        0);
    }
    DiagnosticLog.timing(MainActivity.this, "canon.packet", 0L, canon.length());
    // AGENT 5 — GM Integration: per-turn voice guidance (Agent 1), confirmed-state
    // continuity (Agent 2, defensive), Core exit directive, and event/agency guards.
    String actionKindNormalized;
    try {
      actionKindNormalized = new JSONObject(requireGameCore().currentActionContext()).optString("kind", "EXECUTE");
    } catch (Exception ignored) { actionKindNormalized = "EXECUTE"; }
    if (actionKindNormalized == null) actionKindNormalized = "EXECUTE";
    actionKindNormalized = actionKindNormalized.trim().toUpperCase(java.util.Locale.ROOT);
    String voiceActionKind = mapVoiceActionKind(before, action, actionKindNormalized,
      pendingNpcEncounters, pendingEntityEncounter, rolls);
    String voiceEventKind = mapVoiceEventKind(before,
      pendingNpcEncounters, pendingEntityEncounter, rolls);
    return "Bạn là Gehihi, Game Master kể chuyện Backrooms bằng tiếng Việt. " +
      "Kể diễn biến tự nhiên theo hành động người chơi, giữ đúng Entity, nhân vật và kết quả đã khóa. " +
      "Được tự do sáng tác chi tiết, lời thoại, không khí, tương tác phụ và phản ứng khác nhau " +
      "giữa các lượt, đặc biệt ở cảnh gặp Entity/NPC/Follower; tránh lặp câu chữ, mô-típ " +
      "và diễn biến của các lần gặp trước. " +
      "Khám Phá và Thực Hiện đều là một Explorer Turn; phải kết hợp hành động người chơi " +
      "với diễn biến trong CHÍNH Turn này, không dời, hủy hay cho nhân vật tự chọn thay người chơi. " +
      "Không tự quyết định Combat, HP, EXP, Item, tạo Entity hay chuyển Level; gameplay Core xử lý. " +
      "Gặp FOLLOWER/NPC phải mô tả cuộc gặp thực tế; nhân vật thể hiện theo đúng hồ sơ Canon trong KNOWLEDGE PACKET (tính cách, cách nói, xưng hô, quan hệ với Cao Minh), không ép một khuôn thoại cố định. " +
      "Nếu Canon không xác nhận họ quen biết từ trước, nhân vật mới gặp (đặc biệt Lucia) tuyệt đối không gọi tên Cao Minh, nhận ra anh hay tỏ ra thân quen trước khi có lời giới thiệu hoặc nguồn thông tin được kể rõ trong cảnh. " +
      "ENTITY và FINAL_ENTITY: kể cảnh Entity chạm trán, dừng ở thế đối đầu; " +
      "chưa kể đòn đánh, sát thương hay kết quả Combat trước khi người chơi nhấn Battle Start. " +
      "Không đưa event ID, ops, chứng cứ, kiểm định hay state vào lời kể. " +
      "\nOUTPUT CONTRACT: Chỉ trả về MỘT JSON object hợp lệ với khóa reply (chuỗi không rỗng). " +
      "Ví dụ: {\"reply\":\"Cao Minh bước vào hành lang tối.\"}. " +
      "Toàn bộ lời kể và lời thoại nằm trong reply; escape dấu ngoặc kép và xuống dòng theo chuẩn JSON. " +
      "Không thêm Markdown, code fence, lời mở đầu, operations, state hoặc khóa khác. " +
      com.rabpit.backroom.core.knowledge.GmNarrativeVoicePolicy.prompt() +
      voiceGuidanceSection(voiceActionKind, voiceEventKind) +
      continuityNotesSection(stateForKnowledge.toString(), rolls.toString()) +
      exitDirectiveSection(before, rolls) +
      eventSemanticsAndAgency() +
      corePersonalMemorySection() +
      "\nACTION_TYPE: " + actionKindNormalized +
      "\nPLAYER_ACTION: " + action +
      "\nCORE_ROLLS_READ_ONLY: " + visibleRolls.toString() +
      encounterEvents.toString() +
      "\nCANON_READ_ONLY: " + canon;
  }

  // AGENT 5 — GM Integration helpers. Entity/NPC action kinds come ONLY from
  // Core-confirmed events (beginAction response), never from GM prose or speculation.
  private String mapVoiceActionKind(JSONObject before, String action, String actionKindNormalized,
      JSONArray pendingNpcEncounters, JSONObject pendingEntityEncounter, JSONObject rolls) {
    if (pendingEntityEncounter != null
        && !pendingEntityEncounter.optString("entityKey", "").trim().isEmpty())
      return com.rabpit.backroom.core.GmNarrativeVoicePolicy.ENTITY_SPAWN;
    if (pendingNpcEncounters != null && pendingNpcEncounters.length() > 0)
      return com.rabpit.backroom.core.GmNarrativeVoicePolicy.NPC_MEETING;
    if (com.rabpit.backroom.core.HiddenExitStreak.newlyReady(before, rolls))
      return com.rabpit.backroom.core.GmNarrativeVoicePolicy.TRACE_FOUND;
    String a = lower(action == null ? "" : action);
    if (containsAny(a, "nói", "hỏi", "trả lời", "đáp", "chào", "gọi", "thì thầm", "hét lên", "kêu lên"))
      return com.rabpit.backroom.core.GmNarrativeVoicePolicy.DIALOGUE;
    if (containsAny(a, "nghỉ ngơi", "ngồi nghỉ", "nằm nghỉ", "ngủ", "hồi sức"))
      return com.rabpit.backroom.core.GmNarrativeVoicePolicy.REST;
    if ("EXPLORE".equals(actionKindNormalized))
      return com.rabpit.backroom.core.GmNarrativeVoicePolicy.EXPLORE_QUIET;
    return com.rabpit.backroom.core.GmNarrativeVoicePolicy.OTHER;
  }

  private String mapVoiceEventKind(JSONObject before, JSONArray pendingNpcEncounters,
      JSONObject pendingEntityEncounter, JSONObject rolls) {
    if (pendingEntityEncounter != null
        && !pendingEntityEncounter.optString("entityKey", "").trim().isEmpty())
      return com.rabpit.backroom.core.GmNarrativeVoicePolicy.MAJOR;
    if (pendingNpcEncounters != null && pendingNpcEncounters.length() > 0)
      return com.rabpit.backroom.core.GmNarrativeVoicePolicy.MAJOR;
    if (com.rabpit.backroom.core.HiddenExitStreak.newlyReady(before, rolls))
      return com.rabpit.backroom.core.GmNarrativeVoicePolicy.MAJOR;
    JSONObject hazard = rolls.optJSONObject("hazard");
    if (hazard != null && hazard.optBoolean("success", false))
      return com.rabpit.backroom.core.GmNarrativeVoicePolicy.MINOR;
    return com.rabpit.backroom.core.GmNarrativeVoicePolicy.NONE;
  }

  private String voiceGuidanceSection(String actionKind, String eventKind) {
    try {
      String guidance = com.rabpit.backroom.core.GmNarrativeVoicePolicy.INSTANCE
        .voiceGuidance(actionKind, eventKind);
      if (guidance == null || guidance.trim().isEmpty()) return "";
      return "\nHƯỚNG DẪN VĂN PHONG LƯỢT NÀY:\n" + guidance.trim();
    } catch (Throwable ignored) { return ""; }
  }

  /** The Core activates only factual bags of encountered, scene-present NPCs. */
  private String corePersonalMemorySection() {
    String memory = requireGameCore().sceneMemoryContext();
    return memory.isEmpty() ? "" : "\\nPERSONAL_MEMORY_CORE_READ_ONLY:\\n" + memory;
  }

  private String continuityNotesSection(String stateJson, String rollsJson) {
    String notes = com.rabpit.backroom.core.StoryContinuityReducer
      .continuityNotes(stateJson, rollsJson);
    if (notes.trim().isEmpty()) return "";
    return "\nTRẠNG THÁI ĐÃ XÁC NHẬN (Core — GM không được kể trái các sự thật này):\n" + notes;
  }

  // Core-authoritative exit directive. Distinguishes: exit available (may reveal, never
  // auto-transition) vs no change (do not invent) vs failed exploration (reset, stay silent).
  // The directive itself forbids mentioning streaks, counters, rolls or probabilities,
  // so no Core secrets leak into the prompt.
  private String exitDirectiveSection(JSONObject before, JSONObject rolls) {
    try {
      String directive = com.rabpit.backroom.core.HiddenExitStreak.gmDirective(before, rolls);
      if (directive == null || directive.trim().isEmpty()) return "";
      return "\nEXIT (Core xác nhận):\n" + directive.trim();
    } catch (Throwable ignored) { return ""; }
  }

  // Unifies event semantics across Exit/NPC/Entity and hard-locks player agency over
  // Cao Minh plus the bounds of Khám Phá / natural actions. Static text: no state,
  // no RNG, no secrets.
  private String eventSemanticsAndAgency() {
    return "\nPHÂN BIỆT SỰ KIỆN (bắt buộc):" +
      "\n- ĐÃ XẢY RA: chỉ những gì Core xác nhận trong CORE_CONFIRMED_ENCOUNTER_EVENTS," +
      " mục EXIT và TRẠNG THÁI ĐÃ XÁC NHẬN. Chỉ kể những sự kiện này như sự thật." +
      "\n- CÓ THỂ XẢY RA: những gì CORE_ROLLS_READ_ONLY gợi ý — được phép gieo mầm," +
      " nhưng TUYỆT ĐỐI KHÔNG kết luận kết quả của hành động Core chưa xử lý." +
      "\n- SUY ĐOÁN CỦA NHÂN VẬT: Cao Minh/NPC được phép phỏng đoán, nhưng phải thể hiện" +
      " rõ đó là suy đoán (\"có lẽ\", \"dường như\"), không viết như sự thật đã khóa." +
      "\n- Một phát hiện đã kể ở lượt trước thì các lượt sau phải tiếp nối đúng trạng thái" +
      " của nó; KHÔNG được kể lại như thể vừa xuất hiện lần đầu, KHÔNG tự tạo thêm một" +
      " lối thoát / sự kiện mới bằng lời kể." +
      "\nQUYỀN CỦA NGƯỜI CHƠI VỚI CAO MINH (tuyệt đối): GM không tự chọn chiến thuật quan" +
      " trọng, không tự cho Cao Minh dùng vật phẩm, không tự thay đổi quan hệ với NPC," +
      " không tự cho gia nhập tổ chức, không tự bước qua lối thoát hay chuyển Level thay" +
      " người chơi. Khi ý định chưa rõ, dừng ở thế giằng co và để người chơi quyết định." +
      "\nGIỚI HẠN LỆNH KHÁM PHÁ: Khám Phá chỉ quan sát/khảo sát khu vực HIỆN TẠI. Không dịch" +
      " chuyển tức thời, không tự tìm thấy lối thoát khi Core chưa xác nhận, không tự kích" +
      " hoạt chuyển Level. Hành động tự nhiên (đi, mở, chạm...) chỉ tác động trong phạm vi" +
      " gần, đúng vật lý của cảnh hiện tại.";
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
    @JavascriptInterface public String exportDiagnosticLog(String webJson, boolean includeSensitive) {
      return requestDiagnosticExport(webJson, includeSensitive);
    }

    @JavascriptInterface public String currentClientState(String stateJson) {
      try {
        return requireGameCore().currentClientState(stateJson);
      } catch (Exception error) {
        return "{\"error\":\"core_state_unavailable\"}";
      }
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

    @JavascriptInterface public String saveGameCheckpoint(String presentationJson) {
      try {
        return requireGameCore().saveManualCheckpoint(presentationJson);
      } catch (Exception error) {
        return "{\"ok\":false,\"error\":\"checkpoint_save_failed\"}";
      }
    }

    @JavascriptInterface public String loadGameCheckpoint() {
      try {
        return requireGameCore().loadManualCheckpoint();
      } catch (Exception error) {
        return "{\"ok\":false,\"error\":\"checkpoint_load_failed\"}";
      }
    }

    @JavascriptInterface public boolean clearCoreState() {
      try {
        requireGameCore().clear();
        return true;
      } catch (Exception error) {
        return false;
      }
    }

    @JavascriptInterface public void submitTurn(String stateJson, String action) {
      submitAction(stateJson, "EXECUTE", action);
    }

    @JavascriptInterface public void submitAction(String stateJson, String actionKind, String action) {
      submitTurnInternal(stateJson, actionKind, action);
    }

    private void submitTurnInternal(String stateJson, String actionKind, String action) {
      io.execute(() -> {
        long turnStartNs = System.nanoTime();
        try {
          // The WebView sends presentation JSON; refresh gameplay fields from Core before any rolls or validation.
          final String currentView = requireGameCore().currentClientState(stateJson);
          DiagnosticLog.record("turn.input", "kind=" + actionKind + "; actionChars=" + action.length());
          if (new JSONObject(currentView).has("pendingEncounter")) {
            emit("backroomError", "Hãy nhấn Battle Start ở cuối lời kể để bắt đầu chiến đấu.");
            return;
          }
          if (requireGameCore().blocksTextItemAction(action)) {
            JSONObject blocked = new JSONObject(requireGameCore().processRule(currentView, action));
            DiagnosticLog.record("turn.core_short_circuit", "route=item; reason=" + blocked.optString("reason"));
            if (blocked.has("error")) {
              emit("backroomError", blocked.optString("reply", blocked.optString("error")));
            } else {
              emit("backroomTurn", blocked.getJSONObject("state").toString());
            }
            return;
          }
          JSONObject combatResult = new JSONObject(requireGameCore().processCombat(currentView, actionKind, action));
          if (combatResult.optBoolean("handled", false)) {
            DiagnosticLog.record("turn.core_short_circuit", "route=combat; reason=" + combatResult.optString("reason"));
            if (combatResult.has("error")) {
              emit("backroomError", combatResult.optString("reply", combatResult.optString("error")));
            } else {
              emit("backroomTurn", combatResult.getJSONObject("state").toString());
            }
            return;
          }
          if (requireGameCore().isSpecialRuleAction(action)) {
            JSONObject special = new JSONObject(requireGameCore().processRule(currentView, action));
            emit("backroomTurn", special.getJSONObject("state").toString());
            return;
          }
          JSONObject actionStart = new JSONObject(requireGameCore().beginAction(currentView, actionKind, action));
          if (!actionStart.optBoolean("handled", false)) {
            throw new Exception("Action Runtime từ chối hành động: " + actionStart.optString("error", "action_start_failed"));
          }
          // P0-A: Extract Core-confirmed pending NPC encounters from beginAction response.
          // These are first-meeting events the GM must narrate; Core deferred the commit
          // until the turn succeeds. Contract: [{characterId, flagKey, name}], no secrets.
          JSONArray pendingNpcEncounters = actionStart.optJSONArray("pendingNpcEncounters");
          if (pendingNpcEncounters == null) pendingNpcEncounters = new JSONArray();
          // P0 Entity GM: Extract Core-reserved Entity encounter (if any) from beginAction response.
          // Contract: {entityKey, entityName, encounterId} — public perception only, no secrets.
          // The GM must narrate the correct Entity in this turn; Core reserved the encounter.
          JSONObject pendingEntityEncounter = actionStart.optJSONObject("pendingEntityEncounter");
          JSONObject before = new JSONObject(currentView);
          boolean meta = isMetaAction(action);
          JSONObject rolls = makeGameplayRolls(before, actionKind, action, meta);

          JSONObject generated = parseModelJson(generateText(writerPrompt(before, action, rolls, pendingNpcEncounters, pendingEntityEncounter)));
          String reply = generated.optString("reply", "").trim();
          if (reply.isEmpty()) throw new Exception("Gehihi không trả lời kể chuyện; lượt này không được ghi.");
          if (meta) {
            requireGameCore().abortAction("meta_action");
            JSONObject view = new JSONObject(requireGameCore().currentClientState(currentView));
            JSONArray log = view.optJSONArray("log");
            if (log == null) log = new JSONArray();
            log.put(new JSONObject().put("role", "player").put("text", action));
            log.put(new JSONObject().put("role", "gm").put("text", reply));
            view.put("log", log);
            emit("backroomTurn", view.toString());
            return;
          }
          // Only deterministic Core rolls.
          // P0-3 FIX: Core-authoritative exit destination. Core decides the destination
          // node (HiddenExitStreak + LevelRouteRegistry + Core-parsed intent). The GM/AI
          // never chooses the destination; its words cannot move the player.
          JSONObject destResult = new JSONObject(requireGameCore().determineExitDestination(
            currentView, actionKind, action));
          String destinationId = destResult.isNull("destinationId") ? null
            : destResult.optString("destinationId", null);
          if (destinationId != null && destinationId.isEmpty()) destinationId = null;

          JSONObject candidateState = new JSONObject(before.toString());
          JSONObject candidateFlags = candidateState.optJSONObject("flags");
          if (candidateFlags == null) { candidateFlags = new JSONObject(); candidateState.put("flags", candidateFlags); }
          candidateFlags.put("lastRolls", rolls);

          // Apply Core's destination decision to the candidate (not AI's words).
          if (destinationId != null) {
            LevelRouteRegistry.Node dest = LevelRouteRegistry.node(destinationId);
            if (dest != null) {
              candidateState.put("level", dest.asJson());
              candidateState.put("title", dest.title());
              candidateState.put("location", dest.location());
            } else {
              destinationId = null; // Unknown node — treat as no transition.
            }
          }

          // reconcileVisualWorldState now enforces Core's decision (no AI smuggling).
          reconcileVisualWorldState(before, candidateState, rolls, action, destinationId);
          JSONObject state = candidateState;
          String oldNodeId = currentNodeId(before);
          String newNodeId = currentNodeId(state);
          boolean levelChanged = !oldNodeId.equals(newNodeId);
          // P0-3: Core's destination is authoritative. The old client-side gate
          // (canTransition + playerTraverses + canMove) is replaced: Core already
          // confirmed exit readiness, intent, and edge validity when authorizing.
          boolean transitionAccepted = !levelChanged ||
            (destinationId != null && destinationId.equals(newNodeId));
          if (!transitionAccepted) {
            LevelRouteRegistry.Node previous = LevelRouteRegistry.node(oldNodeId);
            state.put("level", before.optJSONObject("level") == null
              ? previous.asJson() : new JSONObject(before.optJSONObject("level").toString()));
            state.put("title", before.optString("title", previous.title()));
            state.put("location", before.optString("location", previous.location()));
            newNodeId = oldNodeId;
            levelChanged = false;
          }

          state.put("turn", before.optInt("turn", 1) + 1).put("mode", "Single Player: Hard Mode");
          JSONObject flags = state.optJSONObject("flags");
          if (flags == null) flags = new JSONObject();
          flags.put("currentLevel", LevelRouteRegistry.node(newNodeId).asJson());
          flags.remove("lastAudit");
          state.put("flags", flags);
          com.rabpit.backroom.core.HiddenExitStreak.apply(before, state, rolls, oldNodeId, newNodeId);
          state.put("_snapshotEvent", new JSONObject()
            .put("shouldGenerate", false).put("kind", "").put("reason", ""));

          if (!meta) {
            state = new JSONObject(com.rabpit.backroom.core.knowledge.StoryContinuityReducer.apply(
              before.toString(), state.toString(), action));
            // Persist scene progression with the validated Core delta, never as WebView-only state.
            com.rabpit.backroom.core.SnapshotSceneProgress.apply(before, state, rolls, actionKind);
          }

          JSONArray log = state.optJSONArray("log");
          if (log == null) log = new JSONArray();
          log.put(new JSONObject().put("role", "player").put("text", action));
          state.put("log", log);
          JSONObject coreCommit = new JSONObject(requireGameCore().processValidatedCandidate(
            before.toString(), state.toString(), action, reply, generated.toString()));
          if (!coreCommit.optBoolean("handled", false)) {
            throw new Exception("Game State Core từ chối lượt: " + coreCommit.optString("error", "invalid_delta"));
          }
          state = coreCommit.getJSONObject("state");

          state.put("canonVersion", KNOWLEDGE_CANON_VERSION);
          log = state.optJSONArray("log");
          if (log == null) log = new JSONArray();
          if (!state.has("pendingEncounter"))
            log.put(new JSONObject().put("role", "gm").put("text", reply));
          state.put("log", log);
          emit("backroomTurn", state.toString());
          DiagnosticLog.timing(MainActivity.this, "turn.total",
            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - turnStartNs),
            reply.length());
        } catch (Exception e) {
          DiagnosticLog.timing(MainActivity.this, "turn.failed",
            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - turnStartNs),
            0);
          DiagnosticLog.error("turn.pipeline_error", e);
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

    @JavascriptInterface public void battleStart(String stateJson, String encounterId) {
      io.execute(() -> {
        try {
          String result = requireGameCore().confirmEncounter(stateJson, encounterId);
          emit("backroomBattleStart", result);
        } catch (Exception error) {
          emit("backroomBattleStart", "{\"handled\":false,\"error\":\"encounter_confirmation_failed\"}");
        }
      });
    }

    @JavascriptInterface public void recruitFollower(String stateJson, String characterId) {
      io.execute(() -> {
        try { emit("backroomRecruitment", requireGameCore().recruitFollower(stateJson, characterId)); }
        catch (Exception error) { emit("backroomRecruitment", "{\"handled\":false,\"error\":\"recruitment_failed\"}"); }
      });
    }

    @JavascriptInterface public String characterProgression(String characterId) {
      try {
        return requireGameCore().characterProgression(characterId);
      } catch (Exception error) {
        return "{}";
      }
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


