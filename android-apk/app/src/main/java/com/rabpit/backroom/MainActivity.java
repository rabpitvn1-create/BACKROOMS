package com.rabpit.backroom;

import android.annotation.SuppressLint;
import android.app.Activity;
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
import com.rabpit.backroom.core.SceneContextCompiler;
import com.rabpit.backroom.core.OfflinePresenter;
import com.rabpit.backroom.core.NarrationProviderPolicy;
import com.rabpit.backroom.core.SafePresentationView;
import com.rabpit.backroom.core.NarrationGuard;
import com.rabpit.backroom.core.MilestoneCore;
import com.rabpit.backroom.core.ProviderRetryPolicy;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
  private MediaPlayer backgroundMusic;
  private boolean backgroundMusicPrepared;
  private boolean activityResumed;
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private final ExecutorService narrationFutureIo = Executors.newSingleThreadExecutor();
  private final Object narrationFutureLock = new Object();
  private long narrationFutureEpoch = 0L;
  private GameCoreFacade gameCore;
  private MilestoneCore milestoneCore;
  private JSONArray narrationFutureCache = new JSONArray();
  private static final String GEMINI_MODEL = "gemini-3.8-flash";
  private static final String HAIKU_DEFAULT_BASE_URL = "https://api.anthropic.com/v1/messages";
  private static final String HAIKU_DEFAULT_MODEL = "claude-haiku-4-5-20251001";
  private static final long HAIKU_RETRY_DELAY_MS = 1_200L;
  private static final int[] RETRYABLE = {408, 429, 500, 502, 503, 504};

  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    gameCore = GameCoreFacade.create(getApplicationContext(), BuildConfig.DEBUG);
    try {
      milestoneCore = MilestoneCore.fromAssets(getApplicationContext());
    } catch (Exception error) {
      Log.e(TAG, "Milestone assets failed validation", error);
    }
    webView = new WebView(this);
    WebSettings settings = webView.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setAllowFileAccess(true);
    webView.setWebViewClient(new WebViewClient() {
      @Override public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        installUiScripts();
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
                webView.evaluateJavascript(managementUi, null))))));
    } catch (Exception e) {
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

  /** Count content requests; key rotation and transport retries stay inside the existing provider chain. */
  private String generateNarrationText(String prompt, int[] calls, boolean retry) throws Exception {
    calls[retry ? 1 : 0]++;
    return generateText(prompt);
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
    HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(20_000);
    connection.setReadTimeout(60_000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json");
    if (anthropic) {
      connection.setRequestProperty("x-api-key", BuildConfig.HAKU_API_KEY);
      connection.setRequestProperty("anthropic-version", "2023-06-01");
    } else {
      connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.HAKU_API_KEY);
    }
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
      throw new HttpError(status, "Haiku HTTP " + status + (detail.isEmpty() ? "" : ": " + detail));
    }
    return body.toString();
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

  private String generateText(String prompt) throws Exception {
    long providerStart = SystemClock.elapsedRealtime();
    try {
      String output = lunaText(prompt);
      if (BuildConfig.DEBUG) Log.d(TAG, "NARRATION PROVIDER: LUNA success "
          + (SystemClock.elapsedRealtime() - providerStart) + "ms");
      return output;
    } catch (Exception error) {
      if (BuildConfig.DEBUG) Log.d(TAG, "NARRATION PROVIDER: LUNA unavailable "
          + (SystemClock.elapsedRealtime() - providerStart) + "ms");
    }

    Exception geminiError;
    providerStart = SystemClock.elapsedRealtime();
    try {
      String output = geminiText(prompt);
      if (BuildConfig.DEBUG) Log.d(TAG, "NARRATION PROVIDER: Gemini success "
          + (SystemClock.elapsedRealtime() - providerStart) + "ms");
      return output;
    } catch (Exception error) {
      geminiError = error;
      if (BuildConfig.DEBUG) Log.d(TAG, "NARRATION PROVIDER: Gemini failed "
          + (SystemClock.elapsedRealtime() - providerStart) + "ms");
      Log.w(TAG, "All Gemini keys failed; falling back to SOL.");
    }

    Exception solError;
    providerStart = SystemClock.elapsedRealtime();
    try {
      String output = solText(prompt);
      if (BuildConfig.DEBUG) Log.d(TAG, "NARRATION PROVIDER: SOL success "
          + (SystemClock.elapsedRealtime() - providerStart) + "ms");
      return output;
    } catch (Exception error) {
      solError = error;
      if (BuildConfig.DEBUG) Log.d(TAG, "NARRATION PROVIDER: SOL failed "
          + (SystemClock.elapsedRealtime() - providerStart) + "ms");
      Log.w(TAG, "SOL failed; falling back to Haiku.");
    }

    providerStart = SystemClock.elapsedRealtime();
    try {
      String output = haikuText(prompt);
      if (BuildConfig.DEBUG) Log.d(TAG, "NARRATION PROVIDER: Haiku success "
          + (SystemClock.elapsedRealtime() - providerStart) + "ms");
      return output;
    } catch (Exception haikuError) {
      if (BuildConfig.DEBUG) Log.d(TAG, "NARRATION PROVIDER: Haiku failed "
          + (SystemClock.elapsedRealtime() - providerStart) + "ms");
      throw new Exception(
          "Toàn bộ 5 Gemini key, SOL và Haiku fallback đều không khả dụng. Gemini: "
              + providerErrorSummary(geminiError)
              + " | SOL: " + providerErrorSummary(solError)
              + " | Haiku: " + providerErrorSummary(haikuError));
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

  private String appendEncounterDialogue(String reply, JSONArray dialogue) {
    if (dialogue == null || dialogue.length() == 0) return reply;
    StringBuilder output = new StringBuilder(reply == null ? "" : reply.trim());
    for (int i = 0; i < dialogue.length(); i++) {
      String line = dialogue.optString(i, "").trim();
      if (line.isEmpty()) continue;
      if (output.length() > 0) output.append("\n\n");
      output.append(line);
    }
    return output.toString();
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
        + "ORACLE USE: đây là tri thức backstage của Game Master về sáu world-step mặc định kế tiếp. "
        + "Dùng nó để chuẩn bị nhịp kể và viết đúng MỘT lựa chọn mặc định phong phú cho lượt kế tiếp. "
        + "Không được tiết lộ, ám chỉ hay cho nhân vật biết trước Entity, Rương, cuộc gặp nhân vật hoặc kết quả Explorer "
        + "chưa được Core commit trong reply hiện tại. Lựa chọn phải chỉ dựa trên affordance người chơi hiện có thể quan sát; "
        + "không quyết định suy nghĩ, cảm xúc hay động cơ của Cao Minh. "
        + "Khi không có combat/khóa gameplay, choices phải có đúng 1 phần tử.\n"
        + "BATCH OUTPUT: ngoài reply/choices/encounterDialogue của scene HIỆN TẠI, trả thêm field future là mảng 6 capsule "
        + "theo đúng thứ tự STEP +1 đến STEP +6 của CORE ORACLE WINDOW. Mỗi capsule có dạng "
        + "{\\\"reply\\\":\\\"...\\\",\\\"choices\\\":[{\\\"text\\\":\\\"...\\\"}],\\\"encounterDialogue\\\":[]}. "
        + "Mỗi capsule chỉ được dùng outcome của chính step đó và các step trước nó, không được leak step sau. "
        + "Viết capsule gọn, tự nhiên; đây chỉ là presentation cache, Core sẽ bỏ capsule nếu state hoặc Player Action lệch oracle.";
  }

  private void clearNarrationFutureCache() {
    synchronized (narrationFutureLock) {
      narrationFutureCache = new JSONArray();
      narrationFutureEpoch++;
    }
  }

  private JSONObject pollNarrationFuture(JSONObject committedState) {
    synchronized (narrationFutureLock) {
      // Every submitted turn invalidates any refill still being generated for the previous state.
      narrationFutureEpoch++;
      if (narrationFutureCache == null || narrationFutureCache.length() == 0) return null;
      try {
        JSONObject slot = narrationFutureCache.optJSONObject(0);
        if (slot == null) {
          narrationFutureCache = new JSONArray();
          return null;
        }
        String expectedHash = slot.optString("authorityHash", "");
        String actualHash = GameCoreFacade.oracleAuthorityHash(committedState);
        if (!expectedHash.equals(actualHash)) {
          narrationFutureCache = new JSONArray();
          return null;
        }
        narrationFutureCache.remove(0);
        if ("CHARACTER".equals(slot.optString("worldKind", ""))) {
          narrationFutureCache = new JSONArray();
          return null;
        }
        JSONObject payload = slot.optJSONObject("payload");
        return payload == null ? null : new JSONObject(slot.toString());
      } catch (Exception error) {
        narrationFutureCache = new JSONArray();
        return null;
      }
    }
  }

  private void replaceNarrationFutureLocked(JSONArray future, JSONArray oracleSteps) throws Exception {
    narrationFutureCache = new JSONArray();
    narrationFutureEpoch++;
    if (future == null || oracleSteps == null) return;
    int count = Math.min(future.length(), oracleSteps.length());
    for (int i = 0; i < count; i++) {
      JSONObject payload = future.optJSONObject(i);
      JSONObject step = oracleSteps.optJSONObject(i);
      if (payload == null || step == null || payload.optString("reply", "").trim().isEmpty()) break;
      JSONObject clean = new JSONObject(payload.toString());
      clean.remove("future");
      narrationFutureCache.put(new JSONObject()
          .put("action", step.optString("action", ""))
          .put("authorityHash", step.optString("authorityHash", ""))
          .put("worldKind", step.optString("worldKind", ""))
          .put("payload", clean));
    }
  }

  private void captureNarrationFuture(JSONArray future, JSONArray oracleSteps) {
    synchronized (narrationFutureLock) {
      try {
        replaceNarrationFutureLocked(future, oracleSteps);
      } catch (Exception error) {
        narrationFutureCache = new JSONArray();
        narrationFutureEpoch++;
      }
    }
  }

  private void captureNarrationFutureIfEpoch(JSONArray future, JSONArray oracleSteps, long expectedEpoch) {
    synchronized (narrationFutureLock) {
      if (narrationFutureEpoch != expectedEpoch) return;
      try {
        replaceNarrationFutureLocked(future, oracleSteps);
      } catch (Exception error) {
        narrationFutureCache = new JSONArray();
        narrationFutureEpoch++;
      }
    }
  }

  private void scheduleNarrationFutureRefill(JSONObject committedState, String action, String turnId) {
    try {
      final long expectedEpoch;
      synchronized (narrationFutureLock) {
        expectedEpoch = narrationFutureEpoch;
      }
      JSONObject oracle = new JSONObject(gameCore.oracleWindow(committedState.toString()));
      JSONArray steps = oracle.optJSONArray("steps");
      if (steps == null || steps.length() == 0) return;
      final JSONArray oracleSteps = new JSONArray(steps.toString());
      final String prompt = narrationPrompt(committedState, action, turnId + ":rolling-refill",
          oracle.optString("context", "CORE ORACLE WINDOW: unavailable."));
      narrationFutureIo.execute(() -> {
        try {
          JSONObject parsed = parseModelJson(generateText(prompt));
          JSONArray future = parsed.optJSONArray("future");
          if (future == null || future.length() == 0) return;
          captureNarrationFutureIfEpoch(new JSONArray(future.toString()), oracleSteps, expectedEpoch);
        } catch (Exception error) {
          Log.d(TAG, "Rolling narration refill skipped: " + error.getMessage());
        }
      });
    } catch (Exception error) {
      Log.d(TAG, "Unable to schedule rolling narration refill: " + error.getMessage());
    }
  }

  private void prefetchChoices(String choicesJson) {
    // Intentionally no-op: Explorer outcomes are resolved only after explicit submission.
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


  private void emit(String function, String json) {
    String script = "window." + function + "(" + JSONObject.quote(json) + ")";
    runOnUiThread(() -> webView.evaluateJavascript(script, null));
  }

  private class GameBridge {
    @JavascriptInterface public void prefetchChoices(String choicesJson) {
      MainActivity.this.prefetchChoices(choicesJson);
    }

    @JavascriptInterface public String saveCheckpoint() {
      return gameCore.saveCheckpoint();
    }

    @JavascriptInterface public String loadCheckpoint() {
      return gameCore.loadCheckpoint();
    }

    @JavascriptInterface public void clearCheckpoint() {
      gameCore.clearCheckpoint();
    }

    @JavascriptInterface public void submitTurn(String stateJson, String action) {
      io.execute(() -> {
        JSONObject committedBeforeNarration = null;
        long tStart = SystemClock.elapsedRealtime();
        try {
          JSONObject submitted = new JSONObject(stateJson);
          JSONObject persisted = new JSONObject(gameCore.currentCoreState());
          if (persisted.length() > 0) submitted = persisted;
    
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
          JSONObject cachedSlot = pollNarrationFuture(narrationState);
          JSONObject cachedGenerated =
              cachedSlot == null ? null : cachedSlot.optJSONObject("payload");
          if (cachedGenerated != null
              && !NarrationGuard.validate(cachedGenerated, narrationState, safeEvidence).isEmpty()) {
            clearNarrationFutureCache();
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
          final String convergenceForProvider = convergenceTarget;
          final JSONObject cachedForProvider = cachedGenerated;
          final JSONObject[] freshGenerated = {null};
          final JSONArray[] freshFuture = {null};
          final JSONArray[] freshOracleSteps = {null};
          int[] providerCalls = {0, 0};
          long[] promptMs = {0L, 0L};
          int[] promptChars = {0, 0};
          long[] providerMs = {0L, 0L};
          long[] validationMs = {0L};
          JSONObject generated = NarrationProviderPolicy.present(safeEvents, rejection -> {
            if (cachedForProvider != null) return cachedForProvider;

            int timingIndex = rejection.isEmpty() ? 0 : 1;
            long promptStart = SystemClock.elapsedRealtime();
            JSONObject oracle = new JSONObject(gameCore.oracleWindow(narrationState.toString()));
            String prompt = narrationPrompt(narrationState, action, turnId,
                oracle.optString("context", "CORE ORACLE WINDOW: unavailable."));
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
                  !rejection.isEmpty()));
              JSONArray future = parsed.optJSONArray("future");
              if (future != null) freshFuture[0] = new JSONArray(future.toString());
              JSONArray steps = oracle.optJSONArray("steps");
              if (steps != null) freshOracleSteps[0] = new JSONArray(steps.toString());
              parsed.remove("future");
              freshGenerated[0] = parsed;
              return parsed;
            } finally {
              providerMs[timingIndex] += SystemClock.elapsedRealtime() - providerRequestStart;
            }
          }, candidate -> {
            long validationStart = SystemClock.elapsedRealtime();
            try {
              return NarrationGuard.validate(candidate, narrationState, safeEvidence);
            } finally {
              validationMs[0] += SystemClock.elapsedRealtime() - validationStart;
            }
          });
          if (cachedForProvider != null && generated != cachedForProvider) {
            clearNarrationFutureCache();
          }
          if (freshGenerated[0] != null && generated == freshGenerated[0]) {
            captureNarrationFuture(freshFuture[0], freshOracleSteps[0]);
          }
          if (cachedForProvider != null && generated == cachedForProvider) {
            scheduleNarrationFutureRefill(narrationState, action, turnId);
          }
          String reply = generated.optString("reply", "");
          Log.d(TAG, "PRESENTATION CONTENT ATTEMPTS: turnId=" + turnId
              + " initial=" + providerCalls[0] + " retry=" + providerCalls[1]
              + " total=" + (providerCalls[0] + providerCalls[1]));

          JSONArray encounterDialogue = generated.optJSONArray("encounterDialogue");
          if (encounterDialogue == null) encounterDialogue = new JSONArray();
          reply = appendEncounterDialogue(reply, encounterDialogue);

          JSONObject gmEntry = GmChoiceContract.gmEntry(reply, generated, state);
          gmEntry.put("sceneLevelKey", state.optString("currentLevelKey", String.valueOf(state.optInt("currentLevel", 0))));
          String newEncounter = encounterKey(state);
          if (CombatChoiceEngine.isKnownEntity(newEncounter)) gmEntry.remove("choices");
          JSONObject appended = new JSONObject(gameCore.commitPresentation(turnId,
              narrationEvidence.optInt("stateVersion", -1), presentationBaseHash,
              turnId + ":narration", action, gmEntry.toString()));
          state = appended.getJSONObject("state");
          if (!appended.optBoolean("handled", false)) {
            Log.d(TAG, "PRESENTATION DROP: " + appended.optString("reason", "unknown"));
            emit("backroomTurn", state.toString());
            return;
          }

          if (CombatChoiceEngine.isKnownEntity(newEncounter) && !CombatChoiceEngine.isActive(state)) {
            state = new JSONObject(
                gameCore.startCombatRuntime(newEncounter, lastGmLogIndex(state)));
          }

          if (BuildConfig.DEBUG) {
            long totalMs = SystemClock.elapsedRealtime() - tStart;
            Log.d(TAG, "EMERGENT TURN TELEMETRY: total=" + totalMs
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
        }
      });
    }

    @JavascriptInterface public void combatRoll(String stateJson) {
      io.execute(() -> {
        try {
          JSONObject runtime = new JSONObject(gameCore.combatRollRuntime());
          emit("backroomCombatDiceState", runtime.toString());
        } catch (Exception e) {
          emit("backroomError", e.getMessage() == null ? "Không thể ROLL." : e.getMessage());
        }
      });
    }

    @JavascriptInterface public void combatHold(String stateJson, int dieIndex, boolean held) {
      io.execute(() -> {
        try {
          JSONObject runtime = new JSONObject(gameCore.combatHoldRuntime(dieIndex, held));
          emit("backroomCombatDiceState", runtime.toString());
        } catch (Exception e) {
          emit("backroomError", e.getMessage() == null ? "Không thể HOLD die." : e.getMessage());
        }
      });
    }

    @JavascriptInterface public void combatFinish(String stateJson) {
      io.execute(() -> {
        try {
          JSONObject runtime = new JSONObject(gameCore.combatFinishRuntime());
          emit("backroomCombatDiceState", runtime.toString());
        } catch (Exception e) {
          emit("backroomError", e.getMessage() == null ? "Không thể FINISH hand." : e.getMessage());
        }
      });
    }

    @JavascriptInterface public void combatResolve(String stateJson) {
      io.execute(() -> {
        try {
          JSONObject result = new JSONObject(gameCore.processCombatResolution(stateJson));
          if (!result.optBoolean("handled", false)) {
            throw new Exception(result.optString("error", "Không thể resolve combat hand."));
          }
          emit("backroomCombatTurn", result.getJSONObject("state").toString());
        } catch (Exception e) {
          emit("backroomError", e.getMessage() == null ? "Không thể resolve combat hand." : e.getMessage());
        }
      });
    }

    @JavascriptInterface public void restartAfterDeath() {
      io.execute(() -> {
        try {
          JSONObject result = new JSONObject(gameCore.restartAfterDeath());
          if (!result.optBoolean("handled", false)) {
            throw new Exception(result.optString("error", "Không thể bắt đầu lại từ đầu Level."));
          }
          emit("backroomTurn", result.getJSONObject("state").toString());
        } catch (Exception e) {
          emit("backroomError",
              e.getMessage() == null ? "Không thể bắt đầu lại từ đầu Level." : e.getMessage());
        }
      });
    }

    @JavascriptInterface public void coreUpgrade(String stateJson, String characterId, String stat) {
      io.execute(() -> emit("backroomCoreUpgrade",
          gameCore.processCoreUpgrade(stateJson, characterId, stat)));
    }

    @JavascriptInterface public void itemAction(String stateJson, String ownerId, String itemId,
                                                String operation, String targetId, int quantity) {
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
      return gameCore.levelSnapshotDescriptor(stateJson);
    }

    @JavascriptInterface public String normalizeState(String stateJson) {
      return gameCore.normalizeState(stateJson);
    }

    @JavascriptInterface public String startNewGame(String initialJson) {
      return gameCore.startNewGame(initialJson);
    }
  }

  private static class HttpError extends Exception {
    final int status;
    HttpError(int status, String message) { super(message); this.status = status; }
  }
}
