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
import com.rabpit.backroom.core.SceneDirector;
import com.rabpit.backroom.core.NarrationProviderScheduler;
import com.rabpit.backroom.core.NarrationHttpTransport;
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
  private final NarrationProviderScheduler providerScheduler = new NarrationProviderScheduler();
  private final ThreadLocal<Long> providerRequestDeadline = new ThreadLocal<>();
  private GameCoreFacade gameCore;
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
    installImeInsetBridge();
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

  private void installImeInsetBridge() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || webView == null) return;
    webView.setOnApplyWindowInsetsListener((view, insets) -> {
      int bottom = 0;
      try {
        if (insets.isVisible(WindowInsets.Type.ime())) {
          bottom = Math.max(0, insets.getInsets(WindowInsets.Type.ime()).bottom);
        }
      } catch (Throwable error) {
        Log.w(TAG, "Unable to read IME insets; visualViewport fallback remains active.", error);
      }
      final int imeBottomPx = bottom;
      view.post(() -> {
        if (webView == null) return;
        webView.evaluateJavascript(
            "if(window.backroomSetImeInset){window.backroomSetImeInset(" + imeBottomPx + ");}",
            null);
      });
      return insets;
    });
    webView.requestApplyInsets();
  }

  @Override protected void onDestroy() {
    releaseBackgroundMusic();
    if (gameCore != null) gameCore.close();
    io.shutdownNow();
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

  private String generateNarrationText(String prompt, long deadlineNanos) throws Exception {
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
    boolean urgent = false;
    int geminiAttempts = 0;
    int limit = 4;
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

  private String narrationPrompt(JSONObject sceneFrame) {
    return "Bạn là GAME MASTER của Backroom The Game. Nhiệm vụ duy nhất của bạn là KỂ LẠI SCENE FRAME bằng tiếng Việt tự nhiên, giàu không khí và có nhịp như một đoạn truyện; không viết như log hay bản tóm tắt. "
        + "Bạn không quyết định gameplay, không tạo sự kiện và không sửa sự thật trong frame.\n"
        + "NGÔI KỂ: luôn kể Cao Minh ở ngôi thứ ba; không gọi Cao Minh là 'bạn'. Dùng 'Cao Minh' hoặc 'hắn'. Từ 'bạn' chỉ được giữ trong lời thoại trực tiếp của nhân vật khác.\n"
        + "NGÔN NGỮ: mọi danh từ/mô tả môi trường thông thường phải là tiếng Việt tự nhiên; chỉ giữ tiếng Anh nếu đó là tên riêng chính thức như Backrooms, Entity hoặc tên vật phẩm/nhân vật. Trước khi trả lời, tự rà chính tả và ngữ pháp; viết câu trọn nghĩa, đúng quan hệ chủ-vị, không dịch từng từ, không lặp từ vô ý và không dùng fragment kỹ thuật như một câu văn.\n"
        + "VĂN PHONG CAO MINH: Cao Minh là Vạn Giới Ma Tôn, một Ma Đạo Kiếm Tu đến từ thế giới tu tiên. Giọng kể phải giữ sắc thái tiên hiệp vừa phải và có thể dùng hệ quy chiếu quen thuộc của hắn như thần thức, ma khí, kiếm ý, linh khí, sát ý hoặc bản mệnh pháp bảo khi thật sự phù hợp với sự thật của cảnh. Đây chỉ là cách diễn đạt và cảm nhận; không tự cho Cao Minh vận công, phóng thần thức, xuất kiếm, dùng ma nguyên, pháp bảo hay bất kỳ năng lực nào nếu PLAYER INTENT hoặc SCENE FRAME không xác nhận. Không biến mọi câu thành cổ văn, thơ hay biền ngẫu.\n"
        + "SCENE FRAME — authoritative current-turn facts:\n" + (sceneFrame == null ? "{}" : sceneFrame.toString()) + "\n"
        + "PLAYER INTENT trong frame chỉ là điều người chơi MUỐN làm, không phải bằng chứng rằng vật thể/Entity/Character được nhắc tới thực sự tồn tại. "
        + "Chỉ coi worldFacts, environment, presentCharacters và pendingIntro là sự thật của cảnh. "
        + "Nếu intent nhắc tới thứ không tồn tại trong frame, hãy kể đó là một nỗ lực/kiểm tra không xác nhận được thứ đó; tuyệt đối không tự tạo nó. "
        + "Lượt thường phải thành một đoạn truyện hoàn chỉnh 1-2 đoạn, không được chỉ chép lại một motif hay một câu fact từ frame.\n"
        + "Nếu focus=ENTITY: bắt buộc dựng trọn cảnh chạm trán TRƯỚC khi combat bắt đầu, ưu tiên 2-3 đoạn ngắn. Dùng ngoại hình, chi tiết và cách tiếp cận có trong entityEvents; không được chỉ lặp một câu nhận diện ngắn. "
        + "Nếu focus=CHARACTER hoặc pendingIntro không rỗng: bắt buộc dựng khoảnh khắc gặp/tái ngộ trước khi nhân vật được xem là đang đồng hành trong lời kể, và phải có 2-5 câu thoại tự nhiên của nhân vật vừa gặp. Không tự viết lời đáp hay quyết định của Cao Minh.\n"
        + "Được phép làm văn phong hay hơn, thêm nhịp điệu, cảm giác và hội thoại của các nhân vật đang PRESENT, nhưng không thêm hoặc xóa Entity, Chest, Item, Character, route outcome hay special event. "
        + "Không quyết định thêm hành động, lời nói hay suy nghĩ tiếp theo cho Cao Minh ngoài PLAYER INTENT. Không tạo choices hay gợi ý hành động.\n"
        + "OUTPUT duy nhất JSON: {\"reply\":\"...\"}";
  }

  private void logDiagnostic(String message) {
    DiagnosticLog.record("app.diagnostic", "message", message);
    Log.d(TAG, message);
  }

  private void emit(String function, String json) {
    DiagnosticLog.record("bridge.emit", "callback", function, "payloadChars", json == null ? 0 : json.length());
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

    @JavascriptInterface public String saveCheckpoint() {
      DiagnosticLog.record("bridge.saveCheckpoint");
      return gameCore.saveCheckpoint();
    }

    @JavascriptInterface public String loadCheckpoint() {
      DiagnosticLog.record("bridge.loadCheckpoint");
      return gameCore.loadCheckpoint();
    }

    @JavascriptInterface public void clearCheckpoint() {
      DiagnosticLog.record("bridge.clearCheckpoint");
      gameCore.clearCheckpoint();
    }

    @JavascriptInterface public void submitTurn(String stateJson, String action) {
      DiagnosticLog.record("bridge.submitTurn", "action", action);
      io.execute(() -> {
        DiagnosticLog.beginTrace("turn");
        DiagnosticLog.record("turn.begin", "action", action);
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
          DiagnosticLog.record("turn.committed", "turnId", turnId,
              "situation", selected == null ? "NONE" : selected.optString("situationKey", "NONE"));
          long coreMs = SystemClock.elapsedRealtime() - coreStart;

          JSONObject state = committed.getJSONObject("state");
          committedBeforeNarration = new JSONObject(state.toString());
          JSONObject narrationEvidence = CommittedTurnNarrationEvidence.fromState(state, turnId);
          if (!narrationEvidence.optBoolean("available", false)) {
            throw new Exception("Committed turn evidence unavailable after commit: " + narrationEvidence.optString("reason", "unknown"));
          }
          String presentationBaseHash = GameCoreFacade.presentationBaseHash(state);
          JSONObject sceneFrame = new JSONObject(gameCore.sceneFrame(state, narrationEvidence, displayAction));
          long promptStart = SystemClock.elapsedRealtime();
          String prompt = narrationPrompt(sceneFrame);
          long promptMs = SystemClock.elapsedRealtime() - promptStart;
          long providerStart = SystemClock.elapsedRealtime();
          JSONObject generated;
          try {
            generated = parseModelJson(
                generateNarrationText(prompt, NarrationHttpTransport.deadlineAfterMillis(30_000L)));
          } catch (Exception providerError) {
            DiagnosticLog.record("narration.fallback", "turnId", turnId,
                "error", providerError.getMessage() == null
                    ? providerError.getClass().getSimpleName() : providerError.getMessage());
            generated = new JSONObject().put("reply", SceneDirector.fallbackNarration(sceneFrame));
          }
          long providerMs = SystemClock.elapsedRealtime() - providerStart;
          String reply = generated.optString("reply", "").trim();
          if (reply.isEmpty()) throw new Exception("AI và fallback đều trả về phản hồi rỗng.");
          JSONObject narrationOnly = new JSONObject().put("reply", reply);
          JSONObject gmEntry = GmChoiceContract.gmEntry(reply, narrationOnly, state);
          gmEntry.put("sceneLevelKey", state.optString("currentLevelKey", String.valueOf(state.optInt("currentLevel", 0))));
          String newEncounter = encounterKey(state);
          if (CombatChoiceEngine.isKnownEntity(newEncounter)) gmEntry.remove("choices");
          JSONObject appended = new JSONObject(gameCore.commitPresentation(turnId,
              narrationEvidence.optInt("stateVersion", -1), presentationBaseHash,
              turnId + ":narration", displayAction, gmEntry.toString()));
          DiagnosticLog.record("presentation.commit", "turnId", turnId,
              "handled", appended.optBoolean("handled", false), "reason", appended.optString("reason", ""));
          state = appended.getJSONObject("state");
          if (!appended.optBoolean("handled", false)) {
            emit("backroomTurn", state.toString());
            return;
          }
          if (CombatChoiceEngine.isKnownEntity(newEncounter) && !CombatChoiceEngine.isActive(state)) {
            state = new JSONObject(gameCore.startCombatRuntime(newEncounter, lastGmLogIndex(state)));
          }
          if (BuildConfig.DEBUG) {
            logDiagnostic("GM TURN: total=" + (SystemClock.elapsedRealtime() - tStart)
                + "ms core=" + coreMs + "ms prompt=" + promptMs + "ms provider=" + providerMs
                + "ms promptChars=" + prompt.length() + " turnId=" + turnId + " authority=SCENE_DIRECTOR_V1");
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
          JSONObject result = new JSONObject(gameCore.restartAfterDeath());
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
      DiagnosticLog.record("bridge.startNewGame", "initialChars", initialJson == null ? 0 : initialJson.length());
      return gameCore.startNewGame(initialJson);
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
