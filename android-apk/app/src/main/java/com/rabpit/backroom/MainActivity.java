package com.rabpit.backroom;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebChromeClient;
import android.webkit.ConsoleMessage;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import com.rabpit.backroom.core.DiagnosticLog;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import com.rabpit.backroom.core.CombatChoiceEngine;
import com.rabpit.backroom.core.CommittedTurnNarrationEvidence;
import com.rabpit.backroom.core.GameCoreFacade;
import com.rabpit.backroom.core.GmChoiceContract;
import com.rabpit.backroom.core.GmNarrativePacket;
import com.rabpit.backroom.core.GmNarratorContract;
import com.rabpit.backroom.core.SceneContextCompiler;
import com.rabpit.backroom.core.OfflinePresenter;
import com.rabpit.backroom.core.NarrationProviderPolicy;
import com.rabpit.backroom.core.NarrationFutureBuffer;
import com.rabpit.backroom.core.NarrationProviderScheduler;
import com.rabpit.backroom.core.NarrationHttpTransport;
import com.rabpit.backroom.core.SafePresentationView;
import com.rabpit.backroom.core.NarrationGuard;
import com.rabpit.backroom.core.MilestoneCore;
import com.rabpit.backroom.core.ProviderRetryPolicy;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

public class MainActivity extends Activity {
  private static final String TAG = "BackroomMain";
  // CI baseline notes:
  // NARRATIVE VOICE:
  // Không kết mỗi reply bằng câu hỏi tu từ
  // Không tự thêm quyết định, ý định, lời nói hoặc hành động tiếp theo cho Cao Minh

  // Semantic highlight type note: type chỉ được là character, entity, item, skill, effect, location hoặc stat
  private static final String BACKGROUND_MUSIC_ASSET = "BackroomsBM.mp3";
  private static final float BACKGROUND_MUSIC_VOLUME = 0.18f;
  private WebView webView;
  private static final int EXPORT_LOG_REQUEST = 4107;
  private File pendingDiagnosticExport;
  private boolean diagnosticExportPending;
  private MediaPlayer backgroundMusic;
  private boolean backgroundMusicPrepared;
  private boolean activityResumed;
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private final ScheduledExecutorService narrationFutureIo = Executors.newSingleThreadScheduledExecutor();
  private final Object narrationFutureLock = new Object();
  private final NarrationFutureBuffer narrationBuffer = new NarrationFutureBuffer();
  private final NarrationProviderScheduler providerScheduler = new NarrationProviderScheduler();
  private final ThreadLocal<Long> providerRequestDeadline = new ThreadLocal<>();
  private boolean narrationFutureStopped;
  private boolean narrationFuturePending;
  private int narrationFutureFailures;
  private boolean narrationFutureRefillRunning = false;
  private GameCoreFacade gameCore;
  private MilestoneCore milestoneCore;
  private static final String GEMINI_MODEL = "gemini-3.6-flash";
  private static final String HAIKU_DEFAULT_BASE_URL = "https://api.anthropic.com/v1/messages";
  private static final String HAIKU_DEFAULT_MODEL = "claude-haiku-4-5-20251001";
  private static final long HAIKU_RETRY_DELAY_MS = 1_200L;
  private static final int[] RETRYABLE = {408, 429, 500, 502, 503, 504};

  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    DiagnosticLog.initialize(getFilesDir(), BuildConfig.GEMINI_API_KEY_1, BuildConfig.GEMINI_API_KEY_2,
        BuildConfig.GEMINI_API_KEY_3, BuildConfig.GEMINI_API_KEY_4, BuildConfig.GEMINI_API_KEY_5,
        BuildConfig.HAKU_API_KEY, BuildConfig.LUNA_API_KEY, BuildConfig.SOL_API_KEY, BuildConfig.GEHIHI_API_KEY);
    DiagnosticLog.record("app.start", "versionName", BuildConfig.VERSION_NAME,
        "versionCode", BuildConfig.VERSION_CODE, "sourceRevision", BuildConfig.SOURCE_REVISION, "androidApi", Build.VERSION.SDK_INT,
        "device", Build.MANUFACTURER + " " + Build.MODEL);
    gameCore = GameCoreFacade.create(getApplicationContext(), BuildConfig.DEBUG);
    try {
      milestoneCore = MilestoneCore.fromAssets(getApplicationContext());
    } catch (Exception error) {
      DiagnosticLog.record("app.error", "error", error);
      Log.e(TAG, "Milestone assets failed validation", error);
    }
    if (narrativeAuditEnabled()) deleteFile("narrative-audit.jsonl");
    if (savedInstanceState != null && savedInstanceState.getBoolean("diagnosticExportPending", false)) {
      File snapshot = new File(getCacheDir(), "backroom-diagnostic-export.jsonl");
      if (snapshot.exists()) { pendingDiagnosticExport = snapshot; diagnosticExportPending = true; }
    }
    webView = new WebView(this);
    WebSettings settings = webView.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setAllowFileAccess(true);
    webView.setWebViewClient(new WebViewClient() {
      @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        DiagnosticLog.record("webview.resource.error", "url", request.getUrl().toString(),
            "mainFrame", request.isForMainFrame(), "code", error.getErrorCode(), "description", error.getDescription().toString());
        super.onReceivedError(view, request, error);
      }
      @Override public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        DiagnosticLog.record("webview.ready", "url", url);
        installUiScripts();
        emitNarrationFutureStatus();
      }
    });
    webView.setWebChromeClient(new WebChromeClient() {
      @Override public boolean onConsoleMessage(ConsoleMessage message) {
        DiagnosticLog.record("webview.console", "level", message.messageLevel().toString(),
            "message", message.message(), "file", message.sourceId(), "line", message.lineNumber());
        return true;
      }
    });
    webView.addJavascriptInterface(new GameBridge(), "Android");
    setContentView(webView);
    initializeBackgroundMusic();
    safeApplyImmersiveFullscreen("onCreate");
    webView.loadUrl("file:///android_asset/index.html");
  }

  @Override protected void onResume() {
    super.onResume();
    activityResumed = true;
    safeApplyImmersiveFullscreen("onResume");
    resumeBackgroundMusic();
  }

  @Override protected void onPause() {
    activityResumed = false;
    pauseBackgroundMusic();
    super.onPause();
  }

  @Override public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) safeApplyImmersiveFullscreen("onWindowFocusChanged");
  }

  private void safeApplyImmersiveFullscreen(String source) {
    try {
      applyImmersiveFullscreen();
    } catch (Throwable error) {
      Log.w(TAG, "Immersive fullscreen failed in " + source + "; keeping app alive.", error);
      try {
        applyLegacyFullscreenFlags();
      } catch (Throwable fallbackError) {
        Log.w(TAG, "Legacy fullscreen fallback also failed; continuing without immersive mode.", fallbackError);
      }
    }
  }

  private void applyImmersiveFullscreen() {
    Window window = getWindow();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      WindowManager.LayoutParams attributes = window.getAttributes();
      attributes.layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
      window.setAttributes(attributes);

      // Android 15+ with targetSdk 35 already enforces edge-to-edge. Re-applying the
      // deprecated decor-fits path here has caused OEM launch crashes in this project before.
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        window.setDecorFitsSystemWindows(false);
      }

      WindowInsetsController controller = window.getInsetsController();
      if (controller == null) {
        applyLegacyFullscreenFlags();
        return;
      }
      controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
      controller.setSystemBarsBehavior(
          WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      WindowManager.LayoutParams attributes = window.getAttributes();
      attributes.layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
      window.setAttributes(attributes);
    }
    applyLegacyFullscreenFlags();
  }

  private void applyLegacyFullscreenFlags() {
    getWindow().getDecorView().setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
  }

  @Override protected void onDestroy() {
    releaseBackgroundMusic();
    synchronized (narrationFutureLock) {
      narrationFutureStopped = true;
      narrationBuffer.reset();
    }
    if (gameCore != null) gameCore.close();
    io.shutdownNow();
    narrationFutureIo.shutdownNow();
    if (webView != null) webView.destroy();
    super.onDestroy();
  }

  private void initializeBackgroundMusic() {
    if (backgroundMusic != null) return;
    MediaPlayer player = new MediaPlayer();
    try (AssetFileDescriptor descriptor = getAssets().openFd(BACKGROUND_MUSIC_ASSET)) {
      player.setDataSource(
          descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
      player.setLooping(true);
      player.setVolume(BACKGROUND_MUSIC_VOLUME, BACKGROUND_MUSIC_VOLUME);
      player.setOnPreparedListener(prepared -> {
        if (backgroundMusic != prepared) return;
        backgroundMusicPrepared = true;
        resumeBackgroundMusic();
      });
      player.setOnErrorListener((failed, what, extra) -> {
        Log.w(TAG, "Background music playback failed: what=" + what + " extra=" + extra);
        if (backgroundMusic == failed) releaseBackgroundMusic();
        return true;
      });
      backgroundMusic = player;
      player.prepareAsync();
    } catch (Exception error) {
      DiagnosticLog.record("app.error", "error", error);
      try {
        player.release();
      } catch (Exception ignored) {}
      backgroundMusic = null;
      backgroundMusicPrepared = false;
      Log.w(TAG, "Unable to initialize background music", error);
    }
  }

  private void resumeBackgroundMusic() {
    MediaPlayer player = backgroundMusic;
    if (!activityResumed || !backgroundMusicPrepared || player == null) return;
    try {
      if (!player.isPlaying()) player.start();
    } catch (IllegalStateException error) {
      Log.w(TAG, "Unable to resume background music", error);
    }
  }

  private void pauseBackgroundMusic() {
    MediaPlayer player = backgroundMusic;
    if (!backgroundMusicPrepared || player == null) return;
    try {
      if (player.isPlaying()) player.pause();
    } catch (IllegalStateException error) {
      Log.w(TAG, "Unable to pause background music", error);
    }
  }

  private void releaseBackgroundMusic() {
    MediaPlayer player = backgroundMusic;
    backgroundMusic = null;
    backgroundMusicPrepared = false;
    if (player == null) return;
    try {
      player.setOnPreparedListener(null);
      player.setOnErrorListener(null);
      player.release();
    } catch (Exception error) {
      DiagnosticLog.record("app.error", "error", error);
      Log.w(TAG, "Unable to release background music", error);
    }
  }

  private String readAssetText(String path) throws Exception {
    StringBuilder text = new StringBuilder();
    try (InputStream input = getAssets().open(path);
         BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
      String line;
      while ((line = reader.readLine()) != null) text.append(line).append('\n');
    }
    return text.toString();
  }

  private boolean narrativeAuditEnabled() {
    return BuildConfig.DEBUG && getIntent() != null
        && getIntent().getBooleanExtra("narrative_audit", false);
  }

  private void installNarrativeAudit() {
    if (!narrativeAuditEnabled()) return;
    try {
      webView.evaluateJavascript(readAssetText("narrative-audit.js"), null);
    } catch (Exception error) {
      DiagnosticLog.record("app.error", "error", error);
      Log.e(TAG, "Unable to install narrative audit driver", error);
    }
  }

  private void installUiScripts() {
    try {
      String snapshotUi = readAssetText("snapshot-ui.js");
      String gmChoiceUi = readAssetText("gm-choice-ui.js");
      String inventoryUi = readAssetText("inventory-ui.js");
      String partyUi = readAssetText("party-ui.js");
      String playerActionUi = readAssetText("player-action-ui.js");
      String managementUi = readAssetText("management-ui.js");
      webView.evaluateJavascript(snapshotUi, ignored ->
        webView.evaluateJavascript(gmChoiceUi, ignoredChoice ->
          webView.evaluateJavascript(inventoryUi, ignoredInventory ->
            webView.evaluateJavascript(partyUi, ignoredParty ->
              webView.evaluateJavascript(playerActionUi, ignoredPlayerAction ->
                webView.evaluateJavascript(managementUi, ignoredManagement ->
                  installNarrativeAudit()))))));
    } catch (Exception e) {
      DiagnosticLog.record("app.error", "error", e);
      Log.e(TAG, "Unable to install WebView UI scripts", e);
    }
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

  private void sleepBeforeNextGeminiKey(int keyIndex, int status) {
    if (status != 0 && !retryable(status)) return;
    long delayMs = Math.min(2_000L, 500L + (long)keyIndex * 350L);
    try {
      Thread.sleep(delayMs);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  private String postJson(String endpoint, String key, String authHeader, JSONObject payload) throws Exception {
    Map<String, String> headers = new HashMap<>();
    headers.put(authHeader, authHeader.equals("Authorization") ? "Bearer " + key : key);
    return diagnosticProviderPost(endpoint, headers, payload);
  }

  private String diagnosticProviderPost(String endpoint, Map<String, String> headers, JSONObject payload) throws Exception {
    String requestId = java.util.UUID.randomUUID().toString();
    long started = SystemClock.elapsedRealtime();
    DiagnosticLog.record("provider.request", "requestId", requestId, "endpoint", endpoint, "payload", payload);
    try {
      NarrationHttpTransport.Response response = NarrationHttpTransport.post(endpoint, headers,
          payload.toString(), requestDeadline());
      DiagnosticLog.record("provider.response", "requestId", requestId, "status", response.status,
          "retryAfter", response.retryAfter, "durationMs", SystemClock.elapsedRealtime() - started, "body", response.body);
      return providerResponse(response);
    } catch (Exception error) {
      DiagnosticLog.record("app.error", "error", error);
      DiagnosticLog.record("provider.error", "requestId", requestId, "durationMs", SystemClock.elapsedRealtime() - started, "error", error);
      throw error;
    }
  }

  private long requestDeadline() {
    Long deadline = providerRequestDeadline.get();
    return deadline == null ? NarrationHttpTransport.deadlineAfterMillis(20_000L) : deadline;
  }

  private String providerResponse(NarrationHttpTransport.Response response) throws HttpError {
    if (response.status < 200 || response.status >= 300) {
      throw new HttpError(response.status, "Provider HTTP " + response.status,
          response.body, response.retryAfter);
    }
    return response.body;
  }

  private String geminiText(String prompt) throws Exception {
    Exception last = null;
    String[] keys = geminiKeys();
    boolean configured = false;
    for (int keyIndex = 0; keyIndex < keys.length; keyIndex++) {
      String key = keys[keyIndex];
      if (key == null || key.trim().isEmpty()) continue;
      configured = true;
      try {
        return geminiTextOnce(prompt, key);
      } catch (Exception error) {
        DiagnosticLog.record("app.error", "error", error);
        last = error;
        int status = error instanceof HttpError ? ((HttpError)error).status : 0;
        if (!ProviderRetryPolicy.shouldRotateGeminiKey(status, error.getMessage())) throw error;
        if (keyIndex < keys.length - 1) sleepBeforeNextGeminiKey(keyIndex, status);
      }
    }
    if (!configured) throw new Exception("Không có Gemini API key trong APK.");
    throw last != null ? last : new Exception("Tất cả Gemini API key đều không khả dụng.");
  }

  private String geminiTextOnce(String prompt, String key) throws Exception {
    JSONObject part = new JSONObject().put("text", prompt);
    JSONObject contents = new JSONObject().put("role", "user").put("parts", new JSONArray().put(part));
    JSONObject config = new JSONObject()
        .put("responseMimeType", "application/json")
        .put("thinkingConfig", new JSONObject().put("thinkingLevel", "low"));
    JSONObject body = new JSONObject().put("contents", new JSONArray().put(contents)).put("generationConfig", config);
    String output = geminiResponseText(postJson(
        "https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent",
        key, "x-goog-api-key", body));
    parseModelJson(output);
    return output;
  }

  /** Count content requests; the initial writer and repair share one foreground deadline. */
  private String generateNarrationText(String prompt, int[] calls, boolean retry, long deadlineNanos) throws Exception {
    calls[retry ? 1 : 0]++;
    return generateScheduledText(prompt, false, deadlineNanos);
  }

  private String geminiResponseText(String raw) throws Exception {
    JSONObject result = new JSONObject(raw);
    JSONArray candidates = result.optJSONArray("candidates");
    StringBuilder text = new StringBuilder();
    if (candidates != null) for (int c = 0; c < candidates.length(); c++) {
      JSONObject candidate = candidates.optJSONObject(c);
      JSONObject content = candidate == null ? null : candidate.optJSONObject("content");
      JSONArray parts = content == null ? null : content.optJSONArray("parts");
      if (parts == null) continue;
      for (int p = 0; p < parts.length(); p++) {
        JSONObject part = parts.optJSONObject(p);
        String piece = part == null ? "" : part.optString("text", "").trim();
        if (!piece.isEmpty()) {
          if (text.length() > 0) text.append('\n');
          text.append(piece);
        }
      }
    }
    if (text.length() == 0) throw new Exception("Gemini không trả nội dung.");
    return text.toString();
  }

  private JSONObject branchSchema() throws Exception {
    JSONObject choice = new JSONObject().put("type", "OBJECT")
        .put("properties", new JSONObject().put("text", new JSONObject().put("type", "STRING")))
        .put("required", new JSONArray().put("text"));
    JSONObject branch = new JSONObject().put("type", "OBJECT")
        .put("properties", new JSONObject()
            .put("reply", new JSONObject().put("type", "STRING"))
            .put("choices", new JSONObject().put("type", "ARRAY").put("items", choice))
            .put("encounterDialogue", new JSONObject().put("type", "ARRAY")
                .put("items", new JSONObject().put("type", "STRING"))))
        .put("required", new JSONArray().put("reply").put("choices").put("encounterDialogue"));
    JSONObject branches = new JSONObject().put("type", "OBJECT")
        .put("properties", new JSONObject().put("A", branch).put("B", branch))
        .put("required", new JSONArray().put("A").put("B"));
    return new JSONObject().put("type", "OBJECT")
        .put("properties", new JSONObject().put("branches", branches))
        .put("required", new JSONArray().put("branches"));
  }

  /** Exactly one physical Gemini request; failures leave the normal turn path untouched. */
  private JSONObject geminiBranchBatch(String prompt) throws Exception {
    prompt = SafePresentationView.narrativeText(new JSONObject(gameCore.currentCoreState()), prompt);
    String key = "";
    for (String configured : geminiKeys()) if (configured != null && !configured.trim().isEmpty()) {
      key = configured;
      break;
    }
    if (key.isEmpty()) throw new Exception("Không có Gemini API key trong APK.");
    JSONObject config = new JSONObject().put("responseMimeType", "application/json")
        .put("responseSchema", branchSchema()).put("maxOutputTokens", 8192)
        .put("thinkingConfig", new JSONObject().put("thinkingLevel", "low"));
    JSONObject body = new JSONObject().put("contents", new JSONArray().put(
        new JSONObject().put("role", "user").put("parts", new JSONArray().put(
            new JSONObject().put("text", prompt)))))
        .put("generationConfig", config);
    return parseModelJson(geminiResponseText(postJson(
        "https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent",
        key, "x-goog-api-key", body)));
  }

  private boolean haikuConfigured() {
    return BuildConfig.HAKU_API_KEY != null && !BuildConfig.HAKU_API_KEY.trim().isEmpty();
  }

  private String haikuModel() {
    String configured = BuildConfig.HAKU_MODEL == null ? "" : BuildConfig.HAKU_MODEL.trim();
    return configured.isEmpty() ? HAIKU_DEFAULT_MODEL : configured;
  }

  private String haikuBaseUrl() throws Exception {
    String configured = BuildConfig.HAKU_BASE_URL == null ? "" : BuildConfig.HAKU_BASE_URL.trim();
    String base = configured.isEmpty() ? HAIKU_DEFAULT_BASE_URL : configured;
    if (!base.toLowerCase(java.util.Locale.ROOT).startsWith("https://")) {
      throw new Exception("HAKU_BASE_URL phải dùng HTTPS.");
    }
    while (base.endsWith("/") && base.length() > "https://".length()) {
      base = base.substring(0, base.length() - 1);
    }
    return base;
  }

  private String haikuEndpoint(String suffix) throws Exception {
    String base = haikuBaseUrl();
    if (base.endsWith(suffix)) return base;
    if (base.endsWith("/v1")) return base + suffix;
    if ("/messages".equals(suffix) && !base.contains("/v1")) return base + "/v1/messages";
    return base + suffix;
  }

  private String postJsonHaiku(String endpoint, JSONObject payload, boolean anthropic) throws Exception {
    Map<String, String> headers = new HashMap<>();
    if (anthropic) {
      headers.put("x-api-key", BuildConfig.HAKU_API_KEY);
      headers.put("anthropic-version", "2023-06-01");
    } else {
      headers.put("Authorization", "Bearer " + BuildConfig.HAKU_API_KEY);
    }
    return diagnosticProviderPost(endpoint, headers, payload);
  }

  private String haikuAnthropicText(String prompt) throws Exception {
    JSONObject body = new JSONObject()
        .put("model", haikuModel())
        .put("max_tokens", 4096)
        .put("temperature", 0.6)
        .put("messages", new JSONArray().put(
            new JSONObject().put("role", "user").put("content", prompt)));
    JSONObject result = new JSONObject(postJsonHaiku(haikuEndpoint("/messages"), body, true));
    JSONArray content = result.optJSONArray("content");
    StringBuilder text = new StringBuilder();
    if (content != null) {
      for (int i = 0; i < content.length(); i++) {
        JSONObject part = content.optJSONObject(i);
        String piece = part == null ? "" : part.optString("text", "").trim();
        if (!piece.isEmpty()) {
          if (text.length() > 0) text.append('\n');
          text.append(piece);
        }
      }
    }
    if (text.length() == 0) throw new Exception("Haiku không trả nội dung.");
    return text.toString();
  }

  private JSONObject openAiBody(String model, String prompt) throws Exception {
    return new JSONObject()
        .put("model", model)
        .put("temperature", 0.6)
        .put("max_tokens", 4096)
        .put("messages", new JSONArray().put(
            new JSONObject().put("role", "user").put("content", prompt)));
  }

  private String haikuOpenAiText(String prompt) throws Exception {
    return openAiResponseText(postJsonHaiku(haikuEndpoint("/chat/completions"),
        openAiBody(haikuModel(), prompt), false));
  }

  private String lunaText(String prompt) throws Exception {
    if (BuildConfig.LUNA_API_KEY == null || BuildConfig.LUNA_API_KEY.trim().isEmpty()) {
      throw new Exception("LUNA_API_KEY chưa được cấu hình.");
    }
    String model = BuildConfig.LUNA_MODEL == null ? "" : BuildConfig.LUNA_MODEL.trim();
    String base = BuildConfig.LUNA_BASE_URL == null ? "" : BuildConfig.LUNA_BASE_URL.trim();
    base = base.isEmpty() ? "https://api.apiz.vn/v1" : base;
    while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
    JSONObject body = openAiBody(model.isEmpty() ? "gpt-6-luna" : model, prompt);
    String output = openAiResponseText(postJson(base + "/chat/completions",
        BuildConfig.LUNA_API_KEY, "Authorization", body));
    parseModelJson(output);
    return output;
  }

  private String gehihiText(String prompt) throws Exception {
    if (!configured(BuildConfig.GEHIHI_API_KEY)) {
      throw new Exception("GEHIHI_API_KEY chưa được cấu hình.");
    }
    String model = BuildConfig.GEHIHI_MODEL == null ? "" : BuildConfig.GEHIHI_MODEL.trim();
    if (model.isEmpty()) throw new Exception("GEHIHI_MODEL chưa được cấu hình.");
    String base = BuildConfig.GEHIHI_BASE_URL == null ? "" : BuildConfig.GEHIHI_BASE_URL.trim();
    base = base.isEmpty() ? "https://api.vilao.ai/v1" : base;
    if (!base.toLowerCase(java.util.Locale.ROOT).startsWith("https://")) {
      throw new Exception("GEHIHI_BASE_URL phải dùng HTTPS.");
    }
    while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
    JSONObject body = openAiBody(model, prompt);
    String output = openAiResponseText(postJson(base + "/chat/completions",
        BuildConfig.GEHIHI_API_KEY, "Authorization", body));
    parseModelJson(output);
    return output;
  }

  private String solText(String prompt) throws Exception {
    if (BuildConfig.SOL_API_KEY == null || BuildConfig.SOL_API_KEY.trim().isEmpty()) {
      throw new Exception("SOL_API_KEY chưa được cấu hình.");
    }
    JSONObject body = openAiBody("vgpt/gpt-6.1-sol", prompt)
        .put("reasoning_effort", "low");
    String output = openAiResponseText(postJson("https://api.vilao.ai/v1/chat/completions",
        BuildConfig.SOL_API_KEY, "Authorization", body));
    parseModelJson(output);
    return output;
  }

  private String openAiResponseText(String raw) throws Exception {
    JSONObject result = new JSONObject(raw);
    JSONArray choices = result.optJSONArray("choices");
    if (choices == null || choices.length() == 0) throw new Exception("AI không trả nội dung.");
    JSONObject first = choices.optJSONObject(0);
    JSONObject message = first == null ? null : first.optJSONObject("message");
    Object rawContent = message == null ? null : message.opt("content");
    StringBuilder text = new StringBuilder();
    if (rawContent instanceof String) {
      text.append(((String)rawContent).trim());
    } else if (rawContent instanceof JSONArray) {
      JSONArray parts = (JSONArray)rawContent;
      for (int i = 0; i < parts.length(); i++) {
        JSONObject part = parts.optJSONObject(i);
        String piece = part == null ? "" : part.optString("text", "").trim();
        if (!piece.isEmpty()) {
          if (text.length() > 0) text.append('\n');
          text.append(piece);
        }
      }
    }
    if (text.length() == 0) throw new Exception("AI không trả nội dung.");
    return text.toString();
  }

  private boolean protocolMismatch(Exception error) {
    if (!(error instanceof HttpError)) return false;
    int status = ((HttpError)error).status;
    return status == 400 || status == 404 || status == 405 || status == 415 || status == 422;
  }

  private String haikuTextOnce(String prompt) throws Exception {
    String base = haikuBaseUrl();
    String output;
    if (base.endsWith("/chat/completions")) {
      output = haikuOpenAiText(prompt);
    } else if (base.endsWith("/messages") || base.contains("api.anthropic.com")) {
      output = haikuAnthropicText(prompt);
    } else {
      try {
        output = haikuOpenAiText(prompt);
      } catch (Exception openAiError) {
        if (!protocolMismatch(openAiError)) throw openAiError;
        output = haikuAnthropicText(prompt);
      }
    }
    parseModelJson(output);
    return output;
  }

  private String haikuText(String prompt) throws Exception {
    if (!haikuConfigured()) throw new Exception("HAKU_API_KEY chưa được cấu hình.");
    Exception last = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        return haikuTextOnce(prompt);
      } catch (Exception error) {
        DiagnosticLog.record("app.error", "error", error);
        last = error;
        int status = error instanceof HttpError ? ((HttpError)error).status : 0;
        if (attempt == 0 && ProviderRetryPolicy.shouldRetrySameProvider(status, error.getMessage())) {
          Log.w(TAG, "Haiku transport/server attempt failed; retrying once.");
          try {
            Thread.sleep(HAIKU_RETRY_DELAY_MS);
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
          }
          continue;
        }
        break;
      }
    }
    throw last != null ? last : new Exception("Haiku không khả dụng.");
  }

  private String providerErrorSummary(Exception error) {
    if (error == null) return "không xác định";
    String message = error.getMessage();
    if (message == null || message.trim().isEmpty()) return error.getClass().getSimpleName();
    return message.length() > 260 ? message.substring(0, 260) : message;
  }

  private boolean configured(String key) { return key != null && !key.trim().isEmpty(); }

  private String generateText(String prompt) throws Exception {
    return generateScheduledText(prompt, true, NarrationHttpTransport.deadlineAfterMillis(60_000L));
  }

  private String generateScheduledText(String prompt, boolean background, long deadlineNanos) throws Exception {
    String[] keys = geminiKeys();
    boolean[] configured = new boolean[NarrationProviderScheduler.SOURCE_COUNT];
    for (int i = 0; i < keys.length; i++) configured[i] = configured(keys[i]);
    configured[NarrationProviderScheduler.GEHIHI] = configured(BuildConfig.GEHIHI_API_KEY)
        && configured(BuildConfig.GEHIHI_MODEL);
    configured[NarrationProviderScheduler.HAKU] = haikuConfigured();
    configured[NarrationProviderScheduler.LUNA] = BuildConfig.LUNA_ENABLED && configured(BuildConfig.LUNA_API_KEY);
    configured[NarrationProviderScheduler.SOL] = configured(BuildConfig.SOL_API_KEY);
    boolean[] attempted = new boolean[configured.length];
    boolean urgent = background && narrationBuffer.readyCount() <= NarrationFutureBuffer.EMERGENCY;
    int geminiAttempts = 0;
    int limit = background ? 4 : 3;
    for (int attempt = 0; attempt < limit; attempt++) {
      long remaining = deadlineNanos - System.nanoTime();
      if (remaining <= 0L || Thread.currentThread().isInterrupted()) break;
      int source = providerScheduler.acquire(background, urgent, configured, attempted,
          geminiAttempts, SystemClock.elapsedRealtime());
      DiagnosticLog.record("provider.selection", "source", source, "attempt", attempt, "background", background, "remainingMs", TimeUnit.NANOSECONDS.toMillis(remaining));
      if (source < 0) break;
      attempted[source] = true;
      if (source < NarrationProviderScheduler.GEMINI_COUNT) geminiAttempts++;
      long started = SystemClock.elapsedRealtime();
      long slice = TimeUnit.MILLISECONDS.toNanos(background ? 20_000L : 10_000L);
      long requestStartedNanos = System.nanoTime();
      providerRequestDeadline.set(requestStartedNanos + Math.min(slice, deadlineNanos - requestStartedNanos));
      try {
        String output;
        if (source < NarrationProviderScheduler.GEMINI_COUNT) output = geminiTextOnce(prompt, keys[source]);
        else if (source == NarrationProviderScheduler.GEHIHI) output = gehihiText(prompt);
        else if (source == NarrationProviderScheduler.LUNA) output = lunaText(prompt);
        else if (source == NarrationProviderScheduler.HAKU) output = haikuTextOnce(prompt);
        else output = solText(prompt);
        DiagnosticLog.record("provider.success", "source", source, "background", background, "durationMs", SystemClock.elapsedRealtime() - started);
        providerScheduler.succeeded(source, background, SystemClock.elapsedRealtime() - started);
        if (BuildConfig.DEBUG) logDiagnostic("NARRATION PROVIDER: source=" + source
            + " background=" + background + " elapsed=" + (SystemClock.elapsedRealtime() - started));
        return output;
      } catch (Exception error) {
        DiagnosticLog.record("app.error", "error", error);
        HttpError http = error instanceof HttpError ? (HttpError)error : null;
        int status = http == null ? 0 : http.status;
        providerScheduler.failed(source, background, status, SystemClock.elapsedRealtime(),
            http == null ? 0L : http.retryAfterMs,
            http != null && http.dailyQuota ? NarrationProviderScheduler.nextPacificDailyResetDelay(
                System.currentTimeMillis()) : 0L, ThreadLocalRandom.current().nextLong(501L));
        if (BuildConfig.DEBUG) logDiagnostic("NARRATION PROVIDER unavailable: source=" + source
            + " status=" + status + " background=" + background);
      } finally {
        providerRequestDeadline.remove();
      }
    }
    throw new Exception("Các nguồn narration đang bận hoặc tạm không khả dụng.");
  }

  private JSONObject parseModelJson(String raw) throws Exception {
    DiagnosticLog.record("provider.parse", "raw", raw);
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

  private String encounterKey(JSONObject state) {
    JSONObject flags = state == null ? null : state.optJSONObject("flags");
    return flags == null ? "" : flags.optString("entityEncounterKey", "").trim().toLowerCase();
  }

  private int lastGmLogIndex(JSONObject state) {
    JSONArray log = state == null ? null : state.optJSONArray("log");
    if (log == null || log.length() == 0) return 0;
    for (int i = log.length() - 1; i >= 0; i--) {
      JSONObject entry = log.optJSONObject(i);
      if (entry != null && !"player".equals(entry.optString("role"))) return i;
    }
    return Math.max(0, log.length() - 1);
  }

  private String narrationPrompt(
      JSONObject state, String action, String turnId, String oracleContext) throws Exception {
    if (milestoneCore == null) {
      throw new IllegalStateException("Milestone runtime unavailable for narration.");
    }
    String prompt = GmNarrativePacket.buildScene(
        SceneContextCompiler.compile(gameCore, milestoneCore, state, action, turnId));
    return prompt + "\n" + oracleContext + "\n"
        + "ORACLE USE: đây là tri thức backstage của Game Master về world-step mặc định kế tiếp. "
        + "Dùng nó để chuẩn bị nhịp kể và viết đúng MỘT lựa chọn mặc định phong phú cho lượt kế tiếp. "
        + "Không được tiết lộ, ám chỉ hay cho nhân vật biết trước Entity, Rương, cuộc gặp nhân vật hoặc kết quả Explorer "
        + "chưa được Core commit trong reply hiện tại. Lựa chọn phải chỉ dựa trên affordance người chơi hiện có thể quan sát; "
        + "không quyết định suy nghĩ, cảm xúc hay động cơ của Cao Minh. "
        + "Khi không có combat/khóa gameplay, choices phải có đúng 1 phần tử. "
        + "OUTPUT chỉ cho scene HIỆN TẠI; không sinh future batch trong request đồng bộ này.";
  }

  private String currentOracleContext(JSONObject oracle) {
    JSONArray steps = oracle == null ? null : oracle.optJSONArray("steps");
    JSONObject step = steps == null ? null : steps.optJSONObject(0);
    if (step == null) return "CORE ORACLE NEXT STEP: unavailable. Do not invent future outcomes.";
    StringBuilder out = new StringBuilder("CORE ORACLE NEXT DEFAULT STEP — HIDDEN WRITER KNOWLEDGE\n");
    out.append("action=").append(step.optString("action", ""));
    String route = step.optString("routeResult", "").trim();
    if (!route.isEmpty()) out.append("; explorer=").append(route);
    String kind = step.optString("worldKind", "").trim();
    String payload = step.optString("payloadKey", "").trim();
    if (!kind.isEmpty()) out.append("; world=").append(kind);
    if (!payload.isEmpty()) out.append(":").append(payload);
    JSONArray events = step.optJSONArray("presentationEvents");
    if (events != null && events.length() > 0) {
      out.append("; presentationEvents=").append(events.toString());
    }
    out.append("\nUse this only to word the single next-action choice. Do not reveal it in the current reply.");
    return out.toString();
  }

  private void clearNarrationFutureCache() {
    DiagnosticLog.record("cache.reset");
    synchronized (narrationFutureLock) {
      narrationBuffer.reset();
      narrationFuturePending = true;
      narrationFutureFailures = 0;
    }
    emitNarrationFutureStatus();
  }

  private String committedWorldTurnId(JSONObject state) {
    JSONObject root = state.optJSONObject("emergent");
    return root == null ? "" : root.optString("lastCommittedTurnId", "");
  }

  private JSONObject pollNarrationFuture(JSONObject committedState) throws Exception {
    JSONObject cached = narrationBuffer.poll(GameCoreFacade.oracleAuthorityHash(committedState),
        committedWorldTurnId(committedState),
        slot -> GameCoreFacade.oracleCacheOutcomeMatches(committedState, slot));
    DiagnosticLog.record("cache.poll", "turnId", committedWorldTurnId(committedState), "hit", cached != null, "slot", cached);
    emitNarrationFutureStatus();
    return cached;
  }

  

  

  private String narrationFuturePrompt(NarrationFutureBuffer.Request request) throws Exception {
    StringBuilder scenes = new StringBuilder();
    for (int i = 0; i < request.steps.length(); i++) {
      JSONObject step = request.steps.getJSONObject(i);
      String turnId = step.optString("turnId", "");
      JSONObject stepState = step.optJSONObject("sceneState");
      if (turnId.isEmpty() || stepState == null) {
        throw new IllegalStateException("Narration future step is missing scene context.");
      }
      SceneContextCompiler.SceneContext scene = SceneContextCompiler.compile(
          gameCore, milestoneCore, stepState, step.optString("action", ""), turnId);
      scenes.append("\nPREPARED STEP ").append(i + 1).append(" — ").append(turnId).append("\n")
          .append("CURRENT LEVEL:\n").append(scene.levelScene).append("\n")
          .append("CURRENT CHARACTERS — present/pending; mentioned-only characters are not present:\n")
          .append(scene.characterScene).append("\n")
          .append("CURRENT STORY — milestone current node only:\n").append(scene.storyBoundary).append("\n")
          .append("RELEVANT CONTINUITY:\n").append(scene.relevantContinuity).append("\n")
          .append("COMMITTED SCENE FACTS:\n").append(scene.committedSceneFacts).append("\n")
          .append("RECENT:\n").append(scene.recentContext).append("\n")
          .append("PLAYER ACTION: [UNDECIDED — Cao Minh chooses at runtime]\n")
          .append("PREPARED OUTCOME: world=").append(step.optString("worldKind", "QUIET"))
          .append("; route=").append(step.optString("routeResult", ""))
          .append("; payload=").append(step.optString("payloadKey", ""))
          .append("; presentationEvents=").append(step.optJSONArray("presentationEvents")).append("\n");
    }
    return GmNarratorContract.promptContext() + "\n" + GmNarratorContract.caoMinhNarrativeCard()
        + "\nPREPARED SCENES:" + scenes
        + "\nALREADY READY PRECEDING REPLIES: " + request.precedingReplies
        + "\nPREFETCH CONTRACT: tác vụ nền, không phải lượt hiện tại. Chỉ viết "
        + request.steps.length() + " capsule cho PREPARED SCENES. "
        + "Mỗi capsule phải mang stepId bằng turnId của đúng scene. Sự kiện/kết quả của scene là cố định; "
        + "PLAYER ACTION chưa được quyết định và tuyệt đối không được tự viết thay Cao Minh. "
        + "Không tiết lộ outcome của scene sau trong capsule trước. Giữ mạch kể với preceding replies. "
        + "OUTPUT chỉ JSON: {\"future\":[{\"stepId\":\"turnId được giao\","
        + "\"reply\":\"...\",\"choices\":[{\"text\":\"...\"}],\"encounterDialogue\":[]}]}";
  }

  /** Record demand only; forecast construction and network calls never run on the turn thread. */
  private void scheduleNarrationFutureRefill(JSONObject baseState) {
    if (baseState == null || baseState.length() == 0) return;
    synchronized (narrationFutureLock) {
      if (narrationFutureStopped) return;
      narrationBuffer.requestRefill();
      if (narrationFutureRefillRunning) {
        narrationFuturePending = true;
        return;
      }
      if (!narrationBuffer.needsRefill()) return;
      narrationFutureRefillRunning = true;
      narrationFutureIo.execute(this::runNarrationFutureRefill);
    }
  }

  

  private void runNarrationFutureRefill() {
    long expectedEpoch = narrationBuffer.epoch();
    NarrationFutureBuffer.Request request = null;
    int accepted = 0;
    try {
      request = narrationBuffer.reserve();
      if (request == null && narrationBuffer.needsRefill()) {
        JSONObject current = new JSONObject(gameCore.currentCoreState());
        if (CombatChoiceEngine.isActive(current) || CombatChoiceEngine.isKnownEntity(encounterKey(current))) {
          return;
        }
        JSONObject oracle = new JSONObject(
            gameCore.oracleWindow(current.toString(), NarrationFutureBuffer.TARGET));
        JSONArray steps = oracle.optJSONArray("steps");
        if (steps == null || steps.length() == 0) return;
        if (!narrationBuffer.forecast(expectedEpoch, steps)) return;
        request = narrationBuffer.reserve();
      }
      if (request == null) return;

      String prompt = narrationFuturePrompt(request);
      JSONObject parsed = parseModelJson(generateText(prompt));
      accepted = narrationBuffer.accept(request, parsed.optJSONArray("future"));
      if (BuildConfig.DEBUG) logDiagnostic("NARRATION PREFETCH: ready=" + narrationBuffer.readyCount()
          + " requested=" + request.steps.length() + " accepted=" + accepted);
    } catch (Exception error) {
      DiagnosticLog.record("app.error", "error", error);
      logDiagnostic("Narration future prefetch unavailable: " + error.getClass().getSimpleName());
    } finally {
      if (request != null) narrationBuffer.finish(request);
      emitNarrationFutureStatus();
      synchronized (narrationFutureLock) {
        narrationFutureRefillRunning = false;
        boolean rerun = narrationFuturePending || narrationBuffer.epoch() != expectedEpoch
            || (request != null && narrationBuffer.needsRefill());
        narrationFuturePending = false;
        if (accepted > 0) narrationFutureFailures = 0;
        else if (request != null) narrationFutureFailures = Math.min(5, narrationFutureFailures + 1);
        if (!narrationFutureStopped && rerun) {
          // ponytail: one dependent narrative batch at a time; parallelize only independent packets.
          long delay = accepted > 0 ? 0L : Math.min(30_000L, 1_000L << narrationFutureFailures);
          narrationFutureRefillRunning = true;
          narrationFutureIo.schedule(this::runNarrationFutureRefill, delay, TimeUnit.MILLISECONDS);
        }
      }
    }
  }

  private void prefetchChoices(String choicesJson) {
    try {
      JSONObject current = new JSONObject(gameCore.currentCoreState());
      scheduleNarrationFutureRefill(current);
    } catch (Exception error) {
      DiagnosticLog.record("app.error", "error", error);
      logDiagnostic("Explorer narration prefetch skipped: " + error.getMessage());
    }
  }

  private String worldProposalPrompt(JSONObject selected) {
    String summary = selected == null ? "" : selected.optString("publicSummary", "");
    String canon = selected == null ? "" : selected.optString("capabilityContext", "");
    JSONArray allowed = selected == null ? null : selected.optJSONArray("allowedWorldActions");
    String allowedText = allowed == null ? "INTERCEPT, DIRECT_ATTACK, OBSERVE" : allowed.toString();
    return "Bạn đang đề xuất CÁCH một world situation đã được Java Core chọn sẽ được thực hiện. "
        + "Bạn không được đổi Entity/situation, không quyết outcome và không sửa state.\n"
        + "SITUATION: " + summary + "\n"
        + "CAPABILITY/CANON: " + canon + "\n"
        + "actionType chỉ được chọn từ ALLOWED_WORLD_ACTIONS: " + allowedText + ". "
        + "intentTag chỉ được aggressive, cautious hoặc opportunistic.\n"
        + "OUTPUT chỉ JSON: {\"actionType\":\"INTERCEPT\",\"intentTag\":\"opportunistic\"}";
  }


  private void logDiagnostic(String message) {
    DiagnosticLog.record("app.diagnostic", "message", message);
    Log.d(TAG, message);
  }

  private void emitNarrationFutureStatus() {
    if (webView == null) return;
    emit("backroomNarrationFutureStatus", "{\"ready\":" + narrationBuffer.readyCount()
        + ",\"target\":" + NarrationFutureBuffer.TARGET + "}");
  }

  private void emit(String function, String json) {
    DiagnosticLog.record("bridge.emit", "callback", function, "payload", json);
    String script = "window." + function + "(" + JSONObject.quote(json) + ")";
    runOnUiThread(() -> webView.evaluateJavascript(script, null));
  }

  @Override protected void onSaveInstanceState(Bundle savedState) {
    savedState.putBoolean("diagnosticExportPending", diagnosticExportPending);
    super.onSaveInstanceState(savedState);
  }

  private void prepareDiagnosticExport() {
    if (diagnosticExportPending) return;
    diagnosticExportPending = true;
    io.execute(() -> {
      try {
        File snapshot = new File(getCacheDir(), "backroom-diagnostic-export.jsonl");
        JSONObject metadata = new JSONObject().put("versionName", BuildConfig.VERSION_NAME)
            .put("versionCode", BuildConfig.VERSION_CODE)
            .put("sourceRevision", BuildConfig.SOURCE_REVISION.isEmpty() ? "unavailable (non-CI build)" : BuildConfig.SOURCE_REVISION)
            .put("providerSources", "0-4=Gemini keys 1-5; 5=Haku; 6=Luna; 7=Sol; 8=Gehihi")
            .put("androidApi", Build.VERSION.SDK_INT)
            .put("device", Build.MANUFACTURER + " " + Build.MODEL)
            .put("currentCoreState", new JSONObject(gameCore.currentCoreState()));
        DiagnosticLog.record("export.request");
        DiagnosticLog.snapshot(snapshot, metadata);
        runOnUiThread(() -> {
          pendingDiagnosticExport = snapshot;
          try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream")
                .putExtra(Intent.EXTRA_TITLE, "Backroom-LOG-" + System.currentTimeMillis() + ".jsonl");
            startActivityForResult(intent, EXPORT_LOG_REQUEST);
          } catch (Exception error) {
            diagnosticExportPending = false;
            snapshot.delete();
            pendingDiagnosticExport = null;
            DiagnosticLog.record("export.error", "error", error);
            emit("backroomLogExportStatus", "Không thể mở hộp thoại lưu LOG.");
          }
        });
      } catch (Exception error) {
        DiagnosticLog.record("export.error", "error", error);
        runOnUiThread(() -> diagnosticExportPending = false);
        emit("backroomLogExportStatus", "Không thể chuẩn bị LOG. Vui lòng thử lại.");
      }
    });
  }

  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode != EXPORT_LOG_REQUEST) return;
    final File snapshot = pendingDiagnosticExport;
    pendingDiagnosticExport = null;
    Uri target = data == null ? null : data.getData();
    if (resultCode != RESULT_OK || target == null || snapshot == null) {
      if (snapshot != null) snapshot.delete();
      diagnosticExportPending = false;
      emit("backroomLogExportStatus", "Đã hủy xuất LOG.");
      return;
    }
    io.execute(() -> {
      String message;
      try (InputStream input = new FileInputStream(snapshot);
           OutputStream output = getContentResolver().openOutputStream(target, "wt")) {
        if (output == null) throw new java.io.IOException("Document output unavailable");
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        output.flush();
        message = "Đã lưu LOG chẩn đoán (.jsonl).";
        DiagnosticLog.record("export.saved");
      } catch (Exception error) {
        DiagnosticLog.record("export.error", "error", error);
        message = "Không thể lưu LOG. Vui lòng thử lại.";
      } finally {
        snapshot.delete();
      }
      final String result = message;
      runOnUiThread(() -> {
        diagnosticExportPending = false;
        Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
        emit("backroomLogExportStatus", result);
      });
    });
  }

  private class GameBridge {
    @JavascriptInterface public void diagnosticEvent(String json) {
      DiagnosticLog.record("ui.event", "payload", json);
    }

    @JavascriptInterface public void exportDiagnosticLog() {
      runOnUiThread(() -> prepareDiagnosticExport());
    }

    @JavascriptInterface public void narrativeAuditRecord(String json) {
      if (!narrativeAuditEnabled()) return;
      try (FileOutputStream output =
          openFileOutput("narrative-audit.jsonl", MODE_APPEND)) {
        output.write((json + "\n").getBytes("UTF-8"));
        output.flush();
      } catch (Exception error) {
        DiagnosticLog.record("app.error", "error", error);
        Log.e(TAG, "Unable to write narrative audit record", error);
      }
    }

    @JavascriptInterface public void prefetchChoices(String choicesJson) {
      DiagnosticLog.record("bridge.prefetchChoices", "choicesJson", choicesJson);
      MainActivity.this.prefetchChoices(choicesJson);
    }

    @JavascriptInterface public String saveCheckpoint() {
      DiagnosticLog.record("bridge.saveCheckpoint");
      return gameCore.saveCheckpoint();
    }

    @JavascriptInterface public String loadCheckpoint() {
      DiagnosticLog.record("bridge.loadCheckpoint");
      clearNarrationFutureCache();
      String restored = gameCore.loadCheckpoint();
      clearNarrationFutureCache();
      try { scheduleNarrationFutureRefill(new JSONObject(gameCore.currentCoreState())); } catch (Exception ignored) {}
      return restored;
    }

    @JavascriptInterface public void clearCheckpoint() {
      DiagnosticLog.record("bridge.clearCheckpoint");
      gameCore.clearCheckpoint();
    }

    @JavascriptInterface public void submitTurn(String stateJson, String action) {
      DiagnosticLog.record("bridge.submitTurn", "stateJson", stateJson, "action", action);
      io.execute(() -> {
        DiagnosticLog.beginTrace("turn");
        DiagnosticLog.record("turn.begin", "action", action, "clientState", stateJson);
        JSONObject committedBeforeNarration = null;
        long tStart = SystemClock.elapsedRealtime();
        try {
          JSONObject clientSubmitted = new JSONObject(stateJson);
          String requestedDisplayAction = clientSubmitted.optString("__uiDisplayAction",
              action == null ? "" : action).trim();
          clientSubmitted.remove("__uiDisplayAction");
          JSONObject submitted = clientSubmitted;
          JSONObject persisted = new JSONObject(gameCore.currentCoreState());
          if (persisted.length() > 0) submitted = persisted;
          final String displayAction = requestedDisplayAction.isEmpty()
              ? (action == null ? "" : action.trim()) : requestedDisplayAction;
    
          if (CombatChoiceEngine.isActive(submitted)) {
            throw new Exception("Đang chiến đấu. Hãy dùng khung Poker Dice trong GAME MASTER.");
          }

          String existingEncounter = encounterKey(submitted);
          if (CombatChoiceEngine.isKnownEntity(existingEncounter)) {
            submitted = new JSONObject(
                gameCore.startCombatRuntime(existingEncounter, lastGmLogIndex(submitted)));
            emit("backroomCombatDiceState", submitted.toString());
            return;
          }

          long coreStart = SystemClock.elapsedRealtime();
          JSONObject prepared = new JSONObject(gameCore.processRule(submitted.toString(), action));
          if (prepared.optBoolean("handled", false)) {
            emit("backroomTurn", prepared.getJSONObject("state").toString());
            return;
          }
          if (!"turn_prepared".equals(prepared.optString("reason", ""))) {
            throw new Exception(prepared.optString("error", "Game State Core không thể chuẩn bị lượt."));
          }

          String turnId = prepared.getString("turnId");
          JSONObject selected = prepared.optJSONObject("selectedCandidate");
          // Core selects and commits all deterministic outcomes, independent of the experimental GM flag.
          JSONObject committed = new JSONObject(gameCore.completePreparedTurn(turnId, "{}"));
          if (!committed.optBoolean("handled", false)) {
            throw new Exception(committed.optString("error", "Game State Core từ chối COMMIT."));
          }
          DiagnosticLog.record("turn.committed", "turnId", turnId, "prepared", prepared, "result", committed);
          long coreMs = SystemClock.elapsedRealtime() - coreStart;

          JSONObject state = committed.getJSONObject("state");
          committedBeforeNarration = new JSONObject(state.toString());
          JSONObject narrationEvidence =
              CommittedTurnNarrationEvidence.fromState(state, turnId);
          if (!narrationEvidence.optBoolean("available", false)) {
            throw new Exception("Committed turn evidence unavailable after commit: "
                + narrationEvidence.optString("reason", "unknown"));
          }

          JSONArray safeEvents = gameCore.safePresentationEvents(state, narrationEvidence);
          JSONObject safeEvidence = SafePresentationView.evidence(state, narrationEvidence);
          String presentationBaseHash = GameCoreFacade.presentationBaseHash(state);
          final JSONObject narrationState = state;
          boolean coreOwnedEntityLifecycle =
              OfflinePresenter.isCoreOwnedEntityLifecycle(safeEvents);
          JSONObject cachedSlot = pollNarrationFuture(narrationState);
          JSONObject cachedGenerated = coreOwnedEntityLifecycle || cachedSlot == null
              ? null : cachedSlot.optJSONObject("payload");
          if (cachedGenerated != null
              && !NarrationGuard.validate(cachedGenerated, narrationState, safeEvidence).isEmpty()) {
            cachedSlot = null;
            cachedGenerated = null;
          }
          String convergenceTarget = "";
          if (cachedGenerated != null) {
            String expectedAction = cachedSlot.optString("action", "");
            String actualAction = action == null ? "" : action.trim();
            if (!expectedAction.equals(actualAction)) {
              convergenceTarget = cachedGenerated.optString("reply", "").trim();
              cachedGenerated = null;
            }
          }
          scheduleNarrationFutureRefill(narrationState);
          final String convergenceForProvider = convergenceTarget;
          final JSONObject cachedForProvider = cachedGenerated;
          final JSONObject[] freshGenerated = {null};
          int[] providerCalls = {0, 0};
          long[] promptMs = {0L, 0L};
          int[] promptChars = {0, 0};
          long[] providerMs = {0L, 0L};
          long[] validationMs = {0L};
          final long narrationDeadline = NarrationHttpTransport.deadlineAfterMillis(30_000L);
          JSONObject generated = NarrationProviderPolicy.present(safeEvents, rejection -> {
            if (cachedForProvider != null) return cachedForProvider;

            int timingIndex = rejection.isEmpty() ? 0 : 1;
            long promptStart = SystemClock.elapsedRealtime();
            JSONObject oracle = new JSONObject(gameCore.oracleWindow(narrationState.toString()));
            String prompt = narrationPrompt(narrationState, displayAction, turnId,
                currentOracleContext(oracle));
            if (!convergenceForProvider.isEmpty()) {
              prompt += "\nPLAYER ACTION CONVERGENCE: thực hiện PLAYER ACTION trước, đúng character/canon, "
                  + "rồi nối hợp lý vào CONVERGENCE TARGET. Người chơi chưa biết trước target. "
                  + "Giữ nguyên outcome; không chèn vật phẩm, năng lực hay hành vi không được scene/context hỗ trợ.\n"
                  + "CONVERGENCE TARGET: " + convergenceForProvider;
            }
            if (!rejection.isEmpty()) {
              prompt += "\nREPAIR REQUEST: the previous writer payload was rejected by the deterministic guard: "
                  + rejection
                  + " Generate one fresh complete JSON payload for the same committed scene. "
                  + "Correct the rejected condition, keep Core authority unchanged, and do not mention this diagnostic.";
            }
            promptChars[timingIndex] = prompt.length();
            promptMs[timingIndex] += SystemClock.elapsedRealtime() - promptStart;
            long providerRequestStart = SystemClock.elapsedRealtime();
            try {
              JSONObject parsed = parseModelJson(generateNarrationText(prompt, providerCalls,
                  !rejection.isEmpty(), narrationDeadline));
              parsed.remove("future");
              freshGenerated[0] = parsed;
              return parsed;
            } finally {
              providerMs[timingIndex] += SystemClock.elapsedRealtime() - providerRequestStart;
            }
          }, candidate -> {
            long validationStart = SystemClock.elapsedRealtime();
            try {
              String rejection = NarrationGuard.validate(candidate, narrationState, safeEvidence);
              DiagnosticLog.record("narration.guard", "turnId", turnId, "candidate", candidate, "evidence", safeEvidence, "rejection", rejection);
              return rejection;
            } finally {
              validationMs[0] += SystemClock.elapsedRealtime() - validationStart;
            }
          });
          if (freshGenerated[0] != null && generated == freshGenerated[0]) {
            scheduleNarrationFutureRefill(narrationState);
          }
          String reply = generated.optString("reply", "");
          logDiagnostic("PRESENTATION CONTENT ATTEMPTS: turnId=" + turnId
              + " initial=" + providerCalls[0] + " retry=" + providerCalls[1]
              + " total=" + (providerCalls[0] + providerCalls[1]));

          JSONArray encounterDialogue = generated.optJSONArray("encounterDialogue");
          if (encounterDialogue == null) encounterDialogue = new JSONArray();
          reply = GmChoiceContract.mergeEncounterDialogue(reply, encounterDialogue);

          JSONObject gmEntry = GmChoiceContract.gmEntry(reply, generated, state);
          gmEntry.put("sceneLevelKey", state.optString("currentLevelKey", String.valueOf(state.optInt("currentLevel", 0))));
          String newEncounter = encounterKey(state);
          if (CombatChoiceEngine.isKnownEntity(newEncounter)) gmEntry.remove("choices");
          JSONObject appended = new JSONObject(gameCore.commitPresentation(turnId,
              narrationEvidence.optInt("stateVersion", -1), presentationBaseHash,
              turnId + ":narration", displayAction, gmEntry.toString()));
          DiagnosticLog.record("presentation.commit", "turnId", turnId, "result", appended);
          state = appended.getJSONObject("state");
          if (!appended.optBoolean("handled", false)) {
            logDiagnostic("PRESENTATION DROP: " + appended.optString("reason", "unknown"));
            emit("backroomTurn", state.toString());
            return;
          }

          if (CombatChoiceEngine.isKnownEntity(newEncounter) && !CombatChoiceEngine.isActive(state)) {
            state = new JSONObject(
                gameCore.startCombatRuntime(newEncounter, lastGmLogIndex(state)));
            scheduleNarrationFutureRefill(state);
          }

          if (BuildConfig.DEBUG) {
            long totalMs = SystemClock.elapsedRealtime() - tStart;
            logDiagnostic("EMERGENT TURN TELEMETRY: total=" + totalMs
                + "ms core=" + coreMs
                + "ms prompt=" + (promptMs[0] + promptMs[1])
                + "ms provider=" + (providerMs[0] + providerMs[1])
                + "ms validation=" + validationMs[0]
                + "ms repair=" + (promptMs[1] + providerMs[1])
                + "ms providerInitial=" + providerMs[0]
                + "ms providerRepair=" + providerMs[1]
                + "ms promptCharsInitial=" + promptChars[0]
                + " promptCharsRepair=" + promptChars[1]
                + " repairCount=" + providerCalls[1]
                + " turnId=" + turnId
                + " authority=CORE_V2"
                + " situation=" + (selected == null ? "NONE" : selected.optString("situationKey", "NONE")));
          }
          emit("backroomTurn", state.toString());
        } catch (Exception e) {
          DiagnosticLog.record("app.error", "error", e);
          String message = e.getMessage() == null ? "Không thể xử lý lượt." : e.getMessage();
          if (committedBeforeNarration != null) {
            try {
              JSONObject payload = new JSONObject()
                  .put("state", new JSONObject(gameCore.currentCoreState()))
                  .put("message", message);
              emit("backroomCommittedError", payload.toString());
            } catch (Exception ignored) {
              emit("backroomError", message);
            }
          } else {
            emit("backroomError", message);
          }
        } finally {
          DiagnosticLog.record("turn.end", "durationMs", SystemClock.elapsedRealtime() - tStart);
          DiagnosticLog.endTrace();
        }
      });
    }

    @JavascriptInterface public void combatTarget(int entityIndex) {
      DiagnosticLog.record("bridge.combatTarget", "entityIndex", entityIndex);
      io.execute(() -> {
        try {
          JSONObject runtime = new JSONObject(gameCore.combatTargetRuntime(entityIndex));
          scheduleNarrationFutureRefill(runtime);
          emit("backroomCombatDiceState", runtime.toString());
        } catch (Exception e) {
          DiagnosticLog.record("app.error", "error", e);
          emit("backroomError", e.getMessage() == null ? "Không thể đổi mục tiêu." : e.getMessage());
        }
      });
    }

    @JavascriptInterface public void combatRoll(String stateJson) {
      DiagnosticLog.record("bridge.combatRoll", "stateJson", stateJson);
      io.execute(() -> {
        try {
          JSONObject runtime = new JSONObject(gameCore.combatRollRuntime());
          scheduleNarrationFutureRefill(runtime);
          emit("backroomCombatDiceState", runtime.toString());
        } catch (Exception e) {
          DiagnosticLog.record("app.error", "error", e);
          emit("backroomError", e.getMessage() == null ? "Không thể ROLL." : e.getMessage());
        }
      });
    }

    @JavascriptInterface public void combatHold(String stateJson, int dieIndex, boolean held) {
      DiagnosticLog.record("bridge.combatHold", "stateJson", stateJson, "dieIndex", dieIndex, "held", held);
      io.execute(() -> {
        try {
          JSONObject runtime = new JSONObject(gameCore.combatHoldRuntime(dieIndex, held));
          scheduleNarrationFutureRefill(runtime);
          emit("backroomCombatDiceState", runtime.toString());
        } catch (Exception e) {
          DiagnosticLog.record("app.error", "error", e);
          emit("backroomError", e.getMessage() == null ? "Không thể HOLD die." : e.getMessage());
        }
      });
    }

    @JavascriptInterface public void combatFinish(String stateJson) {
      DiagnosticLog.record("bridge.combatFinish", "stateJson", stateJson);
      io.execute(() -> {
        try {
          JSONObject runtime = new JSONObject(gameCore.combatFinishRuntime());
          scheduleNarrationFutureRefill(runtime);
          emit("backroomCombatDiceState", runtime.toString());
        } catch (Exception e) {
          DiagnosticLog.record("app.error", "error", e);
          emit("backroomError", e.getMessage() == null ? "Không thể FINISH hand." : e.getMessage());
        }
      });
    }

    @JavascriptInterface public void combatResolve(String stateJson) {
      DiagnosticLog.record("bridge.combatResolve", "stateJson", stateJson);
      io.execute(() -> {
        try {
          JSONObject result = new JSONObject(gameCore.processCombatResolution(stateJson));
          if (!result.optBoolean("handled", false)) {
            throw new Exception(result.optString("error", "Không thể resolve combat hand."));
          }
          scheduleNarrationFutureRefill(result.getJSONObject("state"));
          emit("backroomCombatTurn", result.getJSONObject("state").toString());
        } catch (Exception e) {
          DiagnosticLog.record("app.error", "error", e);
          emit("backroomError", e.getMessage() == null ? "Không thể resolve combat hand." : e.getMessage());
        }
      });
    }

    @JavascriptInterface public void restartAfterDeath() {
      DiagnosticLog.record("bridge.restartAfterDeath");
      io.execute(() -> {
        try {
          clearNarrationFutureCache();
          JSONObject result = new JSONObject(gameCore.restartAfterDeath());
          clearNarrationFutureCache();
          if (!result.optBoolean("handled", false)) {
            throw new Exception(result.optString("error", "Không thể bắt đầu lại từ đầu Level."));
          }
          emit("backroomTurn", result.getJSONObject("state").toString());
        } catch (Exception e) {
          DiagnosticLog.record("app.error", "error", e);
          emit("backroomError",
              e.getMessage() == null ? "Không thể bắt đầu lại từ đầu Level." : e.getMessage());
        }
      });
    }

    @JavascriptInterface public void coreUpgrade(String stateJson, String characterId, String stat) {
      DiagnosticLog.record("bridge.coreUpgrade", "stateJson", stateJson, "characterId", characterId, "stat", stat);
      io.execute(() -> emit("backroomCoreUpgrade",
          gameCore.processCoreUpgrade(stateJson, characterId, stat)));
    }

    @JavascriptInterface public void itemAction(String stateJson, String ownerId, String itemId,
                                                String operation, String targetId, int quantity) {
      DiagnosticLog.record("bridge.itemAction", "stateJson", stateJson, "ownerId", ownerId, "itemId", itemId, "operation", operation, "targetId", targetId, "quantity", quantity);
      io.execute(() -> {
        try {
          JSONObject submitted = new JSONObject(gameCore.currentCoreState());
          if (CombatChoiceEngine.isActive(submitted)) {
            JSONObject rejected = new JSONObject()
              .put("handled", false)
              .put("state", submitted)
              .put("reason", "combat_locked")
              .put("error", "Battle đang hoạt động. Hãy hoàn tất Poker Dice trước.");
            emit("backroomItemAction", rejected.toString());
            return;
          }
          emit("backroomItemAction",
              gameCore.processItemAction(stateJson, ownerId, itemId, operation, targetId, quantity));
        } catch (Exception e) {
          DiagnosticLog.record("app.error", "error", e);
          JSONObject rejected = new JSONObject();
          try {
            rejected.put("handled", false).put("state", new JSONObject(stateJson));
            rejected.put("error", e.getMessage() == null ? "Không thể xử lý vật phẩm." : e.getMessage());
          } catch (Exception ignored) {}
          emit("backroomItemAction", rejected.toString());
        }
      });
    }

    @JavascriptInterface public String levelSnapshot(String stateJson) {
      DiagnosticLog.record("bridge.levelSnapshot", "stateJson", stateJson);
      return gameCore.levelSnapshotDescriptor(stateJson);
    }

    @JavascriptInterface public String normalizeState(String stateJson) {
      DiagnosticLog.record("bridge.normalizeState", "stateJson", stateJson);
      return gameCore.normalizeState(stateJson);
    }

    @JavascriptInterface public String startNewGame(String initialJson) {
      DiagnosticLog.record("bridge.startNewGame", "initialJson", initialJson);
      clearNarrationFutureCache();
      String started = gameCore.startNewGame(initialJson);
      clearNarrationFutureCache();
      try { scheduleNarrationFutureRefill(new JSONObject(gameCore.currentCoreState())); } catch (Exception ignored) {}
      return started;
    }
  }

  private static class HttpError extends Exception {
    final int status;
    long retryAfterMs;
    boolean dailyQuota;
    HttpError(int status, String message) { super(message); this.status = status; }
    HttpError(int status, String message, String responseBody, String retryAfter) {
      this(status, message);
      try { retryAfterMs = Math.max(0L, Long.parseLong(retryAfter) * 1_000L); } catch (Exception ignored) {}
      try {
        JSONObject error = new JSONObject(responseBody).optJSONObject("error");
        JSONArray details = error == null ? null : error.optJSONArray("details");
        for (int i = 0; details != null && i < details.length(); i++) {
          JSONObject detail = details.optJSONObject(i);
          if (detail == null) continue;
          String delay = detail.optString("retryDelay", "");
          if (delay.endsWith("s")) retryAfterMs = Math.max(retryAfterMs,
              (long)(Double.parseDouble(delay.substring(0, delay.length() - 1)) * 1_000L));
          JSONArray violations = detail.optJSONArray("violations");
          for (int j = 0; violations != null && j < violations.length(); j++) {
            JSONObject violation = violations.optJSONObject(j);
            if (violation == null) continue;
            String quota = (violation.optString("quotaId", "") + " "
                + violation.optString("quotaMetric", "")).toLowerCase(java.util.Locale.ROOT);
            if (quota.contains("perday") || quota.contains("per_day")) dailyQuota = true;
          }
        }
      } catch (Exception ignored) {}
    }
  }
}
