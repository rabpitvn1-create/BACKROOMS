package com.rabpit.backroom;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
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
import com.rabpit.backroom.core.GameCoreFacade;
import com.rabpit.backroom.core.GmChoiceContract;
import com.rabpit.backroom.core.GmNarrativePacket;
import com.rabpit.backroom.core.GmShadowPlanner;
import com.rabpit.backroom.core.CanonRetriever;
import com.rabpit.backroom.core.GmNarratorContract;
import com.rabpit.backroom.core.NarrationGuard;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.LinkedHashMap;
import java.util.Map;

public class MainActivity extends Activity {
  private static final String TAG = "BackroomMain";
  // CI baseline notes:
  // NARRATIVE VOICE:
  // Không kết mỗi reply bằng câu hỏi tu từ
  // Không tự thêm quyết định, ý định, lời nói hoặc hành động tiếp theo cho Cao Minh

  // Semantic highlight type note: type chỉ được là character, entity, item, skill, effect, location hoặc stat
  private WebView webView;
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private final ExecutorService prefetchIo = Executors.newSingleThreadExecutor();
  private final ExecutorService shadowPlannerIo = Executors.newSingleThreadExecutor();
  private final AtomicLong prefetchGeneration = new AtomicLong();
  private volatile PrefetchCache prefetchCache;
  private final Map<String, JSONObject> shadowPlannerCache = new LinkedHashMap<>();
  private GameCoreFacade gameCore;
  private CanonRetriever canonRetriever;
  private static final String GEMINI_MODEL = "gemini-3.8-flash";
  private static final String HAIKU_DEFAULT_BASE_URL = "https://api.anthropic.com/v1/messages";
  private static final String HAIKU_DEFAULT_MODEL = "claude-haiku-4-5-20251001";
  private static final long HAIKU_RETRY_DELAY_MS = 1_200L;
  private static final int[] RETRYABLE = {408, 429, 500, 502, 503, 504};
  private static final String GM_STYLE_EXAMPLES_ASSET = "knowledge/gm_style_examples.json";
  private String gmStyleExamplesCache;

  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    gameCore = GameCoreFacade.create(
        getApplicationContext(), BuildConfig.DEBUG, BuildConfig.GM_TRANSACTION_COMMIT_ENABLED);
    try {
      canonRetriever = CanonRetriever.fromAssets(getApplicationContext());
    } catch (Exception error) {
      Log.e(TAG, "Canon assets failed validation", error);
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
    safeApplyImmersiveFullscreen("onCreate");
    webView.loadUrl("file:///android_asset/index.html");
  }

  @Override protected void onResume() {
    super.onResume();
    safeApplyImmersiveFullscreen("onResume");
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
    if (gameCore != null) gameCore.close();
    prefetchGeneration.incrementAndGet();
    prefetchIo.shutdownNow();
    shadowPlannerIo.shutdownNow();
    io.shutdownNow();
    if (webView != null) webView.destroy();
    super.onDestroy();
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

  private String gmStyleExamplesContext() {
    if (gmStyleExamplesCache != null) return gmStyleExamplesCache;
    try {
      JSONObject root = new JSONObject(readAssetText(GM_STYLE_EXAMPLES_ASSET));
      StringBuilder output = new StringBuilder("GM STYLE FEW-SHOT EXAMPLES:\n");
      String instruction = root.optString("instruction", "").trim();
      if (!instruction.isEmpty()) output.append(instruction).append("\n");

      JSONArray examples = root.optJSONArray("goodExamples");
      if (examples != null) {
        for (int i = 0; i < examples.length(); i++) {
          JSONObject example = examples.optJSONObject(i);
          if (example == null) continue;
          String player = example.optString("player", "").trim();
          String gm = example.optString("gm", "").trim();
          if (player.isEmpty() || gm.isEmpty()) continue;
          output.append("\nGOOD EXAMPLE ").append(i + 1).append("\n");
          output.append("PLAYER: ").append(player).append("\n");
          output.append("GM: ").append(gm).append("\n");
        }
      }

      JSONObject bad = root.optJSONObject("badExample");
      if (bad != null) {
        String player = bad.optString("player", "").trim();
        String gm = bad.optString("gm", "").trim();
        String why = bad.optString("why", "").trim();
        if (!player.isEmpty() && !gm.isEmpty()) {
          output.append("\nBAD EXAMPLE — DO NOT IMITATE\n");
          output.append("PLAYER: ").append(player).append("\n");
          output.append("GM: ").append(gm).append("\n");
          if (!why.isEmpty()) output.append("WHY BAD: ").append(why).append("\n");
        }
      }

      output.append("\nUse these examples only as style references. Never copy their wording, events, imagery, locations, conclusions, or hidden outcomes into the current turn unless current state independently supports them.\n");
      gmStyleExamplesCache = output.toString();
    } catch (Exception error) {
      Log.w(TAG, "Unable to load GM style examples; using narrative contract only.", error);
      gmStyleExamplesCache = "";
    }
    return gmStyleExamplesCache;
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
        .put("max_tokens", 2048)
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

  private String haikuOpenAiText(String prompt) throws Exception {
    JSONObject body = new JSONObject()
        .put("model", haikuModel())
        .put("temperature", 0.6)
        .put("max_tokens", 2048)
        .put("messages", new JSONArray().put(
            new JSONObject().put("role", "user").put("content", prompt)));
    JSONObject result = new JSONObject(postJsonHaiku(haikuEndpoint("/chat/completions"), body, false));
    JSONArray choices = result.optJSONArray("choices");
    if (choices == null || choices.length() == 0) throw new Exception("Haiku không trả nội dung.");
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
    if (text.length() == 0) throw new Exception("Haiku không trả nội dung.");
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
    Exception geminiError;
    try {
      // geminiText() rotates through GEMINI_API_KEY_1..5 before it gives up.
      return geminiText(prompt);
    } catch (Exception error) {
      geminiError = error;
      Log.w(TAG, "All Gemini keys failed; falling back to Haiku.");
    }

    try {
      return haikuText(prompt);
    } catch (Exception haikuError) {
      throw new Exception(
          "Toàn bộ 5 Gemini key và Haiku fallback đều không khả dụng. Gemini: "
              + providerErrorSummary(geminiError)
              + " | Haiku: "
              + providerErrorSummary(haikuError));
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

  private String clipped(Object value, int max) {
    String text = value == null ? "" : String.valueOf(value);
    return text.length() > max ? text.substring(text.length() - max) : text;
  }

  private String recentContext(JSONObject state) {
    JSONArray log = state == null ? null : state.optJSONArray("log");
    if (log == null || log.length() == 0) return "(chưa có lượt trước)";

    java.util.ArrayList<String> visible = new java.util.ArrayList<>();
    for (int i = log.length() - 1; i >= 0 && visible.size() < 6; i--) {
      JSONObject entry = log.optJSONObject(i);
      if (entry == null) continue;
      String role = entry.optString("role", "");
      String text = entry.optString("text", "").trim();
      if (text.isEmpty()) continue;
      visible.add(0, ("player".equals(role) ? "PLAYER: " : "GM: ") + clipped(text, 680));
    }

    StringBuilder recent = new StringBuilder();
    for (String line : visible) {
      if (recent.length() > 0) recent.append('\n');
      if (recent.length() + line.length() > GmNarrativePacket.MAX_RECENT_CONTEXT_CHARS) break;
      recent.append(line);
    }
    return recent.length() == 0 ? "(chưa có lượt trước)" : recent.toString();
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

  private String narrationPrompt(JSONObject state, String action) throws Exception {
    String coreJson = state.toString();
    String levelContext = gameCore.levelPromptContext(coreJson, action);
    String entityContext = gameCore.entityPromptContext(coreJson);
    String itemContext = gameCore.itemPromptContext(coreJson);
    String characterContext = gameCore.characterPromptContext(coreJson);
    String levelName = levelContext.startsWith("CURRENT LEVEL NODE: ")
        ? levelContext.substring("CURRENT LEVEL NODE: ".length()).split("\\n", 2)[0] : "";
    CanonRetriever.CanonPacket canon = canonRetriever == null ? null
        : canonRetriever.retrieve(state, action, CanonRetriever.DEFAULT_BUDGET,
            BuildConfig.DEBUG, levelName);
    if (canon == null || canon.budgetExceeded) {
      Log.w(TAG, "Canon retrieval unavailable/over budget/missing refs: "
          + (canon == null ? "index unavailable" : "size=" + canon.charCount
              + " missing=" + canon.missingMandatoryRefs + " requires=" + canon.missingRefs));
      throw new IllegalStateException("Canon bắt buộc không khả dụng trong budget; không gọi AI narration.");
    }
    if (!canon.missingMandatoryRefs.isEmpty()) Log.w(TAG,
        "Markdown canon missing/conflicting; Core context remains authoritative: "
            + canon.missingMandatoryRefs);
    if (BuildConfig.DEBUG) Log.d(TAG, "CANON RETRIEVAL: " + canon.trace);
    return GmNarrativePacket.build(levelContext, entityContext, itemContext, characterContext,
        recentContext(state), state, action, gmStyleExamplesContext(), canon.promptText());
  }

  private String shadowPlannerPrompt(JSONObject plannerContext) throws Exception {
    if (plannerContext == null || !plannerContext.optBoolean("available", false)) {
      throw new IllegalStateException("Shadow planner context unavailable.");
    }
    JSONObject state = plannerContext.getJSONObject("executionBaseState");
    String action = plannerContext.getString("action");
    String coreJson = state.toString();
    String levelContext = gameCore.levelPromptContext(coreJson, action);
    String entityContext = gameCore.entityPromptContext(coreJson);
    String itemContext = gameCore.itemPromptContext(coreJson);
    String characterContext = gameCore.characterPromptContext(coreJson);
    String levelName = levelContext.startsWith("CURRENT LEVEL NODE: ")
        ? levelContext.substring("CURRENT LEVEL NODE: ".length()).split("\\n", 2)[0] : "";
    CanonRetriever.CanonPacket canon = canonRetriever == null ? null
        : canonRetriever.retrieve(state, action, CanonRetriever.DEFAULT_BUDGET, true, levelName);
    if (canon == null || canon.budgetExceeded || !canon.missingMandatoryRefs.isEmpty()) {
      throw new IllegalStateException("Shadow planner canon unavailable or incomplete.");
    }
    JSONObject knowledgeView = GmNarrativePacket.projectState(state);
    return GmShadowPlanner.buildPrompt(
        plannerContext, knowledgeView, levelContext, entityContext, itemContext,
        characterContext, gameCore.shadowCommandRegistry(), canon.promptText());
  }

  private JSONObject cachedShadowProposal(String plannerKey) throws Exception {
    synchronized (shadowPlannerCache) {
      JSONObject cached = shadowPlannerCache.get(plannerKey);
      return cached == null ? null : new JSONObject(cached.toString());
    }
  }

  private void cacheShadowProposal(String plannerKey, JSONObject proposal) throws Exception {
    synchronized (shadowPlannerCache) {
      if (!shadowPlannerCache.containsKey(plannerKey) && shadowPlannerCache.size() >= 16) {
        String oldest = shadowPlannerCache.keySet().iterator().next();
        shadowPlannerCache.remove(oldest);
      }
      shadowPlannerCache.put(plannerKey, new JSONObject(proposal.toString()));
    }
  }

  private JSONObject authoritativeGmProposal(
      JSONObject plannerContext, String prompt, String validationFeedback) throws Exception {
    if (plannerContext == null || prompt == null) {
      throw new IllegalStateException("GM planner context unavailable.");
    }
    String plannerKey = GmShadowPlanner.plannerKey(plannerContext, prompt);
    boolean retry = validationFeedback != null && !validationFeedback.trim().isEmpty();
    JSONObject proposal = retry ? null : cachedShadowProposal(plannerKey);

    if (proposal == null) {
      String effectivePrompt = prompt;
      if (retry) {
        effectivePrompt += "\nVALIDATION REJECTED: " + validationFeedback
            + "\nRepair only the rejected transaction. Keep the same turnId, baseStateHash and Core-selected candidate.";
      }
      JSONObject raw = parseModelJson(generateText(effectivePrompt));
      JSONObject validation = GmShadowPlanner.validateProposal(
          raw, plannerContext.getString("turnId"), plannerContext.getString("baseStateHash"));
      if (!validation.optBoolean("valid", false)) {
        throw new IllegalStateException(
            "GM proposal validation failed: " + validation.optString("reason", "invalid_proposal"));
      }
      proposal = validation.getJSONObject("proposal");
      if (!retry) cacheShadowProposal(plannerKey, proposal);
    } else {
      JSONObject validation = GmShadowPlanner.validateProposal(
          proposal, plannerContext.getString("turnId"), plannerContext.getString("baseStateHash"));
      if (!validation.optBoolean("valid", false)) {
        throw new IllegalStateException(
            "Cached GM proposal invalid: " + validation.optString("reason", "invalid_proposal"));
      }
    }
    return proposal;
  }

  private void scheduleShadowPlanner(
      JSONObject plannerContext, String prompt, JSONObject v2CommittedState, JSONObject selectedCandidate)
      throws Exception {
    if (!BuildConfig.DEBUG || plannerContext == null || prompt == null || v2CommittedState == null) return;
    final JSONObject contextCopy = new JSONObject(plannerContext.toString());
    final JSONObject afterCopy = new JSONObject(v2CommittedState.toString());
    final JSONObject selectedCopy = selectedCandidate == null
        ? new JSONObject() : new JSONObject(selectedCandidate.toString());
    final String promptCopy = prompt;

    shadowPlannerIo.execute(() -> {
      try {
        String plannerKey = GmShadowPlanner.plannerKey(contextCopy, promptCopy);
        JSONObject proposal = cachedShadowProposal(plannerKey);
        boolean cacheHit = proposal != null;
        String proposalFingerprint;

        if (proposal == null) {
          JSONObject raw = parseModelJson(generateText(promptCopy));
          JSONObject validation = GmShadowPlanner.validateProposal(
              raw, contextCopy.getString("turnId"), contextCopy.getString("baseStateHash"));
          if (!validation.optBoolean("valid", false)) {
            Log.w(TAG, "GM SHADOW REJECTED (fail-closed): "
                + validation.optString("reason", "invalid proposal"));
            return;
          }
          proposal = validation.getJSONObject("proposal");
          proposalFingerprint = validation.getString("proposalFingerprint");
          cacheShadowProposal(plannerKey, proposal);
        } else {
          proposalFingerprint = GmShadowPlanner.validateProposal(
              proposal, contextCopy.getString("turnId"), contextCopy.getString("baseStateHash"))
              .optString("proposalFingerprint", "");
        }

        JSONObject authority = new JSONObject(
            gameCore.validateShadowTransaction(contextCopy.toString(), proposal.toString()));
        JSONObject commitGate = new JSONObject(
            gameCore.plannerCommitGate(contextCopy.toString(), proposal.toString()));
        JSONObject resolved = authority.optJSONObject("resolvedTurn");
        JSONArray evidence = resolved == null ? null : resolved.optJSONArray("committedEvents");
        int evidenceCount = evidence == null ? 0 : evidence.length();
        JSONObject comparison = GmShadowPlanner.compareToV2(
            proposal, contextCopy.getJSONObject("executionBaseState"), afterCopy, selectedCopy);
        Log.d(TAG, "GM SHADOW TELEMETRY: key=" + plannerKey.substring(0, 12)
            + " proposal=" + (proposalFingerprint.length() >= 12
                ? proposalFingerprint.substring(0, 12) : proposalFingerprint)
            + " cache=" + cacheHit
            + " authorityValid=" + authority.optBoolean("valid", false)
            + " commitGateAllowed=" + commitGate.optBoolean("allowed", false)
            + " commitGateReason=" + commitGate.optString("reason", "")
            + " acceptedGroups=" + authority.optInt("acceptedGroups", 0)
            + " rejectedGroups=" + authority.optInt("rejectedGroups", 0)
            + " evidence=" + evidenceCount
            + " compare=" + comparison);
      } catch (Exception error) {
        Log.w(TAG, "GM shadow planner unavailable; V2 authoritative turn is unaffected: "
            + providerErrorSummary(error));
      }
    });
  }

  private static final class PrefetchBranch {
    final String action, outcomeHash;
    final JSONObject narration;
    PrefetchBranch(String action, String outcomeHash, JSONObject narration) {
      this.action = action;
      this.outcomeHash = outcomeHash;
      this.narration = narration;
    }
  }

  private static final class PrefetchCache {
    final String baseHash;
    final Map<String, PrefetchBranch> branches;
    PrefetchCache(String baseHash, Map<String, PrefetchBranch> branches) {
      this.baseHash = baseHash;
      this.branches = branches;
    }
    PrefetchBranch forAction(String action) {
      for (PrefetchBranch branch : branches.values()) if (branch.action.equals(action)) return branch;
      return null;
    }
  }

  private void invalidatePrefetch() {
    prefetchGeneration.incrementAndGet();
    prefetchCache = null;
  }

  private void prefetchChoices(String choicesJson) {
    if (BuildConfig.GM_TRANSACTION_COMMIT_ENABLED) {
      invalidatePrefetch();
      return;
    }
    final long generation = prefetchGeneration.incrementAndGet();
    prefetchCache = null;
    prefetchIo.execute(() -> {
      try {
        JSONArray choices = new JSONArray(choicesJson);
        if (choices.length() != 2) return;
        String baseHash = gameCore.currentStateHash();
        Map<String, String> actions = new LinkedHashMap<>();
        Map<String, JSONObject> previews = new LinkedHashMap<>();
        StringBuilder prompt = new StringBuilder(
            "Generate exactly one independent next-turn narration per branch A/B. "
                + "Each branch has its own hypothetical Core-committed outcome and canon. "
                + "Never transfer events, facts, entities, loot or future choices between branches. "
                + "Each reply must be at most 1800 characters, choices 0-3. "
                + "Return only JSON with branches A and B; each contains reply, choices and encounterDialogue.\n");
        for (int i = 0; i < 2; i++) {
          String id = String.valueOf((char) ('A' + i));
          JSONObject choice = choices.getJSONObject(i);
          String action = choice.optString("action", "").trim();
          if (!id.equals(choice.optString("id", "")) || action.isEmpty()
              || actions.containsValue(action)) return;
          JSONObject preview = new JSONObject(gameCore.previewTurn(action, baseHash));
          if (!preview.optBoolean("handled", false)) return;
          actions.put(id, action);
          previews.put(id, preview);
          prompt.append("\n=== BRANCH ").append(id).append(" ONLY ===\n")
              .append(narrationPrompt(preview.getJSONObject("state"), action)).append('\n');
        }
        if (generation != prefetchGeneration.get() || !baseHash.equals(gameCore.currentStateHash())) return;
        JSONObject output = geminiBranchBatch(prompt.toString());
        JSONObject branches = output.optJSONObject("branches");
        if (branches == null || branches.length() != 2 || output.length() != 1) return;
        Map<String, PrefetchBranch> valid = new LinkedHashMap<>();
        for (String id : actions.keySet()) {
          JSONObject generated = branches.optJSONObject(id);
          if (generated == null || generated.length() != 3
              || generated.optJSONArray("choices") == null
              || generated.optJSONArray("encounterDialogue") == null
              || generated.optString("reply", "").length() > 1800) continue;
          JSONObject preview = previews.get(id);
          if (!NarrationGuard.validate(generated, preview.getJSONObject("state")).isEmpty()) continue;
          valid.put(id, new PrefetchBranch(actions.get(id), preview.getString("outcomeHash"), generated));
        }
        if (generation == prefetchGeneration.get() && baseHash.equals(gameCore.currentStateHash())) {
          prefetchCache = new PrefetchCache(baseHash, valid);
        }
      } catch (Exception error) {
        Log.w(TAG, "Branch prefetch unavailable; normal turn path remains available: "
            + providerErrorSummary(error));
      }
    });
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

  private JSONObject narrationFallback(JSONObject state, String replyHint) {
    JSONObject generated = new JSONObject();
    try {
      String reply = replyHint == null ? "" : replyHint.trim();
      JSONObject emergent = state == null ? null : state.optJSONObject("emergent");
      JSONObject selection = emergent == null ? null : emergent.optJSONObject("lastSelection");
      if (reply.isEmpty() && selection != null && !selection.optBoolean("selectedNone", false)) {
        reply = selection.optString("publicSummary", "").trim();
      }
      if (reply.isEmpty()) {
        reply = "Không có biến cố mới. Cao Minh vẫn ở " + state.optString("location", "khu vực hiện tại") + ".";
      }
      generated.put("reply", reply)
          .put("choices", new JSONArray())
          .put("encounterDialogue", new JSONArray());
    } catch (Exception ignored) {}
    return generated;
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
      invalidatePrefetch();
      return gameCore.loadCheckpoint();
    }

    @JavascriptInterface public void clearCheckpoint() {
      invalidatePrefetch();
      gameCore.clearCheckpoint();
    }

    @JavascriptInterface public void submitTurn(String stateJson, String action) {
      io.execute(() -> {
        JSONObject committedBeforeNarration = null;
        long tStart = System.currentTimeMillis();
        try {
          JSONObject submitted = new JSONObject(stateJson);
          JSONObject persisted = new JSONObject(gameCore.currentCoreState());
          if (persisted.length() > 0) submitted = persisted;
          PrefetchCache ready = BuildConfig.GM_TRANSACTION_COMMIT_ENABLED ? null : prefetchCache;
          String baseHash = gameCore.currentStateHash();
          invalidatePrefetch();
          PrefetchBranch cached = ready != null && ready.baseHash.equals(baseHash)
              ? ready.forAction(action == null ? "" : action.trim()) : null;

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
          JSONObject shadowContext = null;
          String shadowPrompt = null;
          if (BuildConfig.GM_TRANSACTION_COMMIT_ENABLED || BuildConfig.DEBUG) {
            try {
              shadowContext = new JSONObject(gameCore.shadowPlannerContext(turnId));
              if (shadowContext.optBoolean("available", false)) {
                shadowPrompt = shadowPlannerPrompt(shadowContext);
              } else {
                Log.w(TAG, "GM shadow context unavailable: "
                    + shadowContext.optString("reason", "unknown"));
                shadowContext = null;
              }
            } catch (Exception shadowContextError) {
              Log.w(TAG, "GM transaction planner context unavailable: "
                  + providerErrorSummary(shadowContextError));
              shadowContext = null;
              shadowPrompt = null;
            }
          }
          JSONObject committed;
          if (BuildConfig.GM_TRANSACTION_COMMIT_ENABLED) {
            if (shadowContext == null || shadowPrompt == null) {
              throw new Exception("GM transaction planner unavailable before commit.");
            }

            JSONObject gmProposal = authoritativeGmProposal(shadowContext, shadowPrompt, "");
            committed = new JSONObject(
                gameCore.completePreparedTurnWithGmTransaction(turnId, gmProposal.toString()));

            if (!committed.optBoolean("handled", false)) {
              String reason = committed.optString("error",
                  committed.optString("reason", "GM transaction rejected."));
              gmProposal = authoritativeGmProposal(shadowContext, shadowPrompt, reason);
              committed = new JSONObject(
                  gameCore.completePreparedTurnWithGmTransaction(turnId, gmProposal.toString()));
            }

            if (!committed.optBoolean("handled", false)) {
              throw new Exception(committed.optString(
                  "error", "GM transaction bị Core từ chối sau bounded retry."));
            }
          } else {
            JSONObject proposal = new JSONObject();
            if (prepared.optBoolean("proposalRequired", false) && cached == null) {
              try {
                String proposalPrompt = worldProposalPrompt(selected);
                JSONObject rawProposal = parseModelJson(generateText(proposalPrompt));
                JSONObject validation = new JSONObject(
                    gameCore.validateWorldProposal(selected.toString(), rawProposal.toString()));

                if (!validation.optBoolean("valid", false)) {
                  String reason = validation.optString("reason", "proposal rejected");
                  rawProposal = parseModelJson(generateText(
                      proposalPrompt + "\nVALIDATION REJECTED: " + reason
                          + "\nRetry the SAME selected SituationCandidate. Do not change the situation or actor."));
                  validation = new JSONObject(
                      gameCore.validateWorldProposal(selected.toString(), rawProposal.toString()));
                }

                if (!validation.optBoolean("valid", false)) {
                  throw new Exception(validation.optString("reason", "World proposal validation failed."));
                }
                proposal = validation.getJSONObject("proposal");
              } catch (Exception proposalError) {
                Log.w(TAG, "World proposal unavailable/invalid after bounded retry; Core will use canonical fallback: "
                    + providerErrorSummary(proposalError));
                proposal = new JSONObject();
              }
            }

            committed = new JSONObject(
                gameCore.completePreparedTurn(turnId, proposal.toString()));
            if (!committed.optBoolean("handled", false)) {
              throw new Exception(committed.optString("error", "Game State Core từ chối COMMIT."));
            }
          }

          JSONObject state = committed.getJSONObject("state");
          committedBeforeNarration = new JSONObject(state.toString());
          String replyHint = committed.optString("replyHint", "");

          JSONObject generated;
          String reply;
          boolean narrationValidated = false;
          try {
            boolean hit = cached != null && cached.outcomeHash.equals(gameCore.currentStateHash());
            generated = hit ? new JSONObject(cached.narration.toString())
                : parseModelJson(generateText(narrationPrompt(state, action)));
            String narrationViolation = NarrationGuard.validate(generated, state);
            if (!narrationViolation.isEmpty()) {
              generated = parseModelJson(generateText(
                  narrationPrompt(state, action) + "\nVALIDATION REJECTED: " + narrationViolation
                      + "\nRegenerate narration only. Do not add or mutate world state."));
              narrationViolation = NarrationGuard.validate(generated, state);
              if (!narrationViolation.isEmpty()) {
                throw new Exception("Narration validation failed: " + narrationViolation);
              }
            }
            reply = generated.optString("reply", "").trim();
            narrationValidated = true;
          } catch (Exception narrationError) {
            Log.w(TAG, "Narration provider failed or contradicted committed authority; using deterministic template: "
                + providerErrorSummary(narrationError));
            generated = narrationFallback(state, replyHint);
            reply = generated.optString("reply", "");
          }

          JSONArray encounterDialogue = generated.optJSONArray("encounterDialogue");
          if (encounterDialogue == null) encounterDialogue = new JSONArray();
          reply = appendEncounterDialogue(reply, encounterDialogue);

          JSONArray log = state.optJSONArray("log");
          if (log == null) log = new JSONArray();
          log.put(new JSONObject().put("role", "player").put("text", action));
          JSONObject gmEntry = GmChoiceContract.gmEntry(reply, generated, state);
          String newEncounter = encounterKey(state);
          if (CombatChoiceEngine.isKnownEntity(newEncounter)) gmEntry.remove("choices");
          log.put(gmEntry);
          state.put("log", log);

          boolean acknowledgePendingIntro = narrationValidated
              && encounterDialogue.length() >= 2 && encounterDialogue.length() <= 5;
          state = new JSONObject(
              gameCore.commitNarration(state.toString(), acknowledgePendingIntro));

          if (CombatChoiceEngine.isKnownEntity(newEncounter) && !CombatChoiceEngine.isActive(state)) {
            state = new JSONObject(
                gameCore.startCombatRuntime(newEncounter, log.length() - 1));
          }

          if (BuildConfig.DEBUG && !BuildConfig.GM_TRANSACTION_COMMIT_ENABLED
              && shadowContext != null && shadowPrompt != null && committedBeforeNarration != null) {
            scheduleShadowPlanner(shadowContext, shadowPrompt, committedBeforeNarration, selected);
          }

          if (BuildConfig.DEBUG) {
            Log.d(TAG, "EMERGENT TURN TELEMETRY: total=" + (System.currentTimeMillis() - tStart)
                + "ms turnId=" + turnId
                + " authority=" + (BuildConfig.GM_TRANSACTION_COMMIT_ENABLED ? "GM_TRANSACTION" : "V2")
                + " situation=" + (selected == null ? "NONE" : selected.optString("situationKey", "NONE")));
          }
          emit("backroomTurn", state.toString());
        } catch (Exception e) {
          String message = e.getMessage() == null ? "Không thể xử lý lượt." : e.getMessage();
          if (committedBeforeNarration != null) {
            try {
              JSONObject payload = new JSONObject()
                  .put("state", committedBeforeNarration)
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
      invalidatePrefetch();
      return gameCore.startNewGame(initialJson);
    }
  }

  private static class HttpError extends Exception {
    final int status;
    HttpError(int status, String message) { super(message); this.status = status; }
  }
}
