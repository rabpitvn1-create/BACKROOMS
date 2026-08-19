package com.rabpit.backroom;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
  private WebView webView;
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private static final String OPENAI_MODEL = "gpt-5.4-mini";
  private static final String GEMINI_MODEL = "gemini-3.6-flash";
  private static final int[] RETRYABLE = {408, 429, 500, 502, 503, 504};

  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    webView = new WebView(this);
    WebSettings settings = webView.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setAllowFileAccess(true);
    webView.setWebViewClient(new WebViewClient());
    webView.addJavascriptInterface(new GameBridge(), "Android");
    setContentView(webView);
    webView.loadUrl("file:///android_asset/index.html");
  }

  private boolean retryable(int code) {
    for (int value : RETRYABLE) if (value == code) return true;
    return false;
  }

  private String postJson(String endpoint, String key, String authHeader, JSONObject payload) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(20000);
    connection.setReadTimeout(55000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty(authHeader, authHeader.equals("Authorization") ? "Bearer " + key : key);
    try (OutputStream output = connection.getOutputStream()) {
      output.write(payload.toString().getBytes("UTF-8"));
    }
    int status = connection.getResponseCode();
    BufferedReader reader = new BufferedReader(new InputStreamReader(
      status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream(), "UTF-8"));
    StringBuilder body = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null) body.append(line);
    reader.close();
    if (status < 200 || status >= 300) throw new HttpError(status, "Provider HTTP " + status);
    return body.toString();
  }

  private String openAiText(String prompt) throws Exception {
    if (BuildConfig.OPENAI_API_KEY == null || BuildConfig.OPENAI_API_KEY.isEmpty()) throw new HttpError(401, "OpenAI key chưa được cấu hình.");
    JSONObject body = new JSONObject().put("model", OPENAI_MODEL).put("input", prompt);
    JSONObject result = new JSONObject(postJson("https://api.openai.com/v1/responses", BuildConfig.OPENAI_API_KEY, "Authorization", body));
    JSONArray output = result.optJSONArray("output");
    if (output != null) {
      for (int i = 0; i < output.length(); i++) {
        JSONArray parts = output.optJSONObject(i) != null ? output.optJSONObject(i).optJSONArray("content") : null;
        if (parts == null) continue;
        for (int j = 0; j < parts.length(); j++) {
          JSONObject part = parts.optJSONObject(j);
          if (part != null && "output_text".equals(part.optString("type")) && !part.optString("text").trim().isEmpty()) return part.optString("text");
        }
      }
    }
    throw new Exception("OpenAI không trả nội dung.");
  }

  private String geminiText(String prompt) throws Exception {
    String[] keys = {BuildConfig.GEMINI_API_KEY_1, BuildConfig.GEMINI_API_KEY_2, BuildConfig.GEMINI_API_KEY_3, BuildConfig.GEMINI_API_KEY_4};
    Exception last = null;
    for (String key : keys) {
      if (key == null || key.isEmpty()) continue;
      for (int attempt = 0; attempt < 2; attempt++) {
        try {
          JSONObject part = new JSONObject().put("text", prompt);
          JSONObject contents = new JSONObject().put("role", "user").put("parts", new JSONArray().put(part));
          JSONObject config = new JSONObject().put("responseMimeType", "application/json").put("temperature", 0.8);
          JSONObject body = new JSONObject().put("contents", new JSONArray().put(contents)).put("generationConfig", config);
          JSONObject result = new JSONObject(postJson("https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent", key, "x-goog-api-key", body));
          JSONArray candidates = result.optJSONArray("candidates");
          JSONObject candidate = candidates != null ? candidates.optJSONObject(0) : null;
          JSONObject providerContent = candidate != null ? candidate.optJSONObject("content") : null;
          JSONArray parts = providerContent != null ? providerContent.optJSONArray("parts") : null;
          String text = parts != null && parts.optJSONObject(0) != null ? parts.optJSONObject(0).optString("text", "") : "";
          if (text.trim().isEmpty()) throw new Exception("Gemini không trả nội dung.");
          return text;
        } catch (Exception e) {
          last = e;
          int code = e instanceof HttpError ? ((HttpError)e).status : 0;
          if (code != 0 && !retryable(code)) throw e;
          if (attempt == 0) try { Thread.sleep(350); } catch (InterruptedException ignored) {}
        }
      }
    }
    throw last != null ? last : new Exception("Không có Gemini API key trong APK.");
  }

  private String generateText(String prompt) throws Exception {
    try {
      return openAiText(prompt);
    } catch (HttpError error) {
      if (!retryable(error.status)) throw error;
      try { Thread.sleep(350); } catch (InterruptedException ignored) {}
      try {
        return openAiText(prompt);
      } catch (HttpError retryError) {
        if (!retryable(retryError.status)) throw retryError;
        return geminiText(prompt);
      }
    }
  }

  private void emit(String function, String json) {
    String script = "window." + function + "(" + JSONObject.quote(json) + ")";
    runOnUiThread(() -> webView.evaluateJavascript(script, null));
  }

  private class GameBridge {
    @JavascriptInterface public void submitTurn(String stateJson, String action) {
      io.execute(() -> {
        try {
          JSONObject state = new JSONObject(stateJson);
          String prompt = "Bạn là Game Master của text game Backrooms. Xử lý đúng một lượt. Trả DUY NHẤT JSON hợp lệ, không markdown. " +
            "Viết tiếng Việt tự nhiên. Không thay đổi dữ kiện chưa có căn cứ. Người chơi chỉ điều khiển Kai Akechi. " +
            "State hiện tại: " + state.toString() + "\nHành động: " + action +
            "\nJSON: {\\\"reply\\\":\\\"...\\\",\\\"title\\\":\\\"...\\\",\\\"location\\\":\\\"...\\\",\\\"player\\\":{},\\\"party\\\":[],\\\"inventory\\\":[],\\\"flags\\\":{}}";
          JSONObject generated = new JSONObject(generateText(prompt));
          state.put("turn", state.optInt("turn", 1) + 1).put("mode", "ai");
          if (generated.has("title")) state.put("title", generated.optString("title"));
          if (generated.has("location")) state.put("location", generated.optString("location"));
          if (generated.optJSONObject("player") != null) state.put("player", generated.optJSONObject("player"));
          if (generated.optJSONArray("party") != null) state.put("party", generated.optJSONArray("party"));
          if (generated.optJSONArray("inventory") != null) state.put("inventory", generated.optJSONArray("inventory"));
          if (generated.optJSONObject("flags") != null) state.put("flags", generated.optJSONObject("flags"));
          JSONArray log = state.optJSONArray("log"); if (log == null) log = new JSONArray();
          log.put(new JSONObject().put("role", "player").put("text", action));
          log.put(new JSONObject().put("role", "gm").put("text", generated.optString("reply")));
          state.put("log", log);
          emit("backroomTurn", state.toString());
        } catch (Exception e) {
          emit("backroomError", e.getMessage() == null ? "Không thể xử lý lượt." : e.getMessage());
        }
      });
    }
  }

  private static class HttpError extends Exception {
    final int status;
    HttpError(int status, String message) { super(message); this.status = status; }
  }
}
