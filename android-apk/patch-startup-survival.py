from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def ensure_after(source: str, anchor: str, addition: str, label: str) -> str:
    if addition.strip() in source:
        return source
    if source.count(anchor) != 1:
        raise RuntimeError(f"{label}: expected one anchor, found {source.count(anchor)}")
    return source.replace(anchor, anchor + addition, 1)


# Android 16 must never be able to kill the Activity merely because an optional runtime
# component failed to initialise. Log failures and keep the UI process alive.
text = ensure_after(text, "import android.os.Bundle;\n", "import android.util.Log;\n", "Log import")
text = ensure_after(text, "import android.webkit.WebViewClient;\n", "import android.widget.TextView;\n", "TextView import")

# Game State Core is useful, but it is not allowed to be a synchronous app-start dependency.
# Initialising it lazily also defers LiteRT/runtime class loading until gameplay actually needs it.
eager_core = "    gameCore = GameCoreFacade.create(getApplicationContext(), BuildConfig.DEBUG);\n"
text = text.replace(eager_core, "")

field_anchor = "  private GameCoreFacade gameCore;\n"
field_addition = "  private volatile boolean gameCoreUnavailable;\n"
text = ensure_after(text, field_anchor, field_addition, "Game Core availability field")

# Existing bridge calls execute inside submitTurn's exception boundary. Route those calls through
# a lazy accessor so a device-specific Core failure becomes a visible turn error, not an app crash.
text = text.replace("gameCore.processRule(", "requireGameCore().processRule(")
text = text.replace("gameCore.processValidatedCandidate(", "requireGameCore().processValidatedCandidate(")
text = text.replace(
    "      if (gameCore != null) gameCore.clear();\n",
    "      GameCoreFacade core = gameCoreOrNull();\n      if (core != null) core.clear();\n",
)

# Rebuild onCreate before inserting helpers. This prevents the helper block itself from being
# accidentally swallowed when the old onCreate body is replaced.
method_start = '  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})\n  @Override public void onCreate(Bundle savedInstanceState) {\n'
helper_anchor = "\n  @Override protected void onDestroy() {\n"
start = text.find(method_start)
end = text.find(helper_anchor, start)
if start < 0 or end < 0:
    raise RuntimeError("onCreate startup boundary not found")

on_create = '''  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
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
'''
text = text[:start] + on_create + text[end:]

helpers = '''
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
        "BACKROOM KHÔNG THỂ KHỞI ĐỘNG GIAO DIỆN WEBVIEW.\\n\\n"
            + "Ứng dụng vẫn đang chạy thay vì tự thoát.\\n"
            + "Lỗi: " + error.getClass().getSimpleName()
            + (error.getMessage() == null ? "" : " — " + error.getMessage()));
    setContentView(fallback);
  }
'''
if "private GameCoreFacade gameCoreOrNull()" not in text:
    if text.count(helper_anchor) != 1:
        raise RuntimeError("Startup helper insertion anchor missing or ambiguous")
    text = text.replace(helper_anchor, "\n" + helpers + helper_anchor, 1)

# A fallback view has no WebView. Avoid teardown/emit races from producing a secondary crash.
text = text.replace(
    '    runOnUiThread(() -> webView.evaluateJavascript(script, null));',
    '    runOnUiThread(() -> { if (webView != null) webView.evaluateJavascript(script, null); });',
)

required = [
    "private volatile boolean gameCoreUnavailable;",
    "private GameCoreFacade gameCoreOrNull()",
    "private GameCoreFacade requireGameCore() throws Exception",
    "showStartupFallback(Throwable error)",
    "requireGameCore().processRule(",
    "requireGameCore().processValidatedCandidate(",
    'Log.w("BackroomStartup", "Immersive fullscreen unavailable; continuing normally."',
    'Log.e("BackroomStartup", "UI enhancement injection failed; base game remains usable."',
    "GameCoreFacade core = gameCoreOrNull();",
]
for marker in required:
    if marker not in text:
        raise RuntimeError(f"Startup survival contract missing: {marker}")
if eager_core in text:
    raise RuntimeError("Eager Game State Core startup dependency still present")

MAIN.write_text(text, encoding="utf-8")
print("Android startup survival hardened: lazy Game Core, guarded fullscreen/WebView and in-process fallback UI.")
