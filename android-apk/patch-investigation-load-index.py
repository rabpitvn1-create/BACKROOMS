from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")

method_start = '  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})\n  @Override public void onCreate(Bundle savedInstanceState) {\n'
immersive_anchor = "\n  private void applyImmersiveFullscreen() {\n"
on_destroy_anchor = "\n  @Override protected void onDestroy() {\n"

start = text.find(method_start)
if start < 0:
    raise RuntimeError("Step 6 onCreate start not found")
end = text.find(immersive_anchor, start)
if end < 0:
    end = text.find(on_destroy_anchor, start)
if end < 0:
    raise RuntimeError("Step 6 onCreate end not found")

on_create = '''  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    try {
      applyImmersiveFullscreen();
    } catch (Throwable error) {
      Log.w("BackroomStartup", "Immersive fullscreen unavailable; continuing normally.", error);
    }
    try {
      // INVESTIGATION_STEP6_LOAD_INDEX
      // Construct WebView and load the packaged page only. No Android JS bridge or UI enhancement injection.
      webView = new WebView(this);
      WebSettings settings = webView.getSettings();
      settings.setJavaScriptEnabled(true);
      settings.setDomStorageEnabled(true);
      settings.setAllowFileAccess(true);
      setContentView(webView);
      webView.loadUrl("file:///android_asset/index.html");
    } catch (Throwable error) {
      showStartupFallback(error);
    }
  }
'''

text = text[:start] + on_create + text[end:]

final_start = text.find(method_start)
final_end = text.find(immersive_anchor, final_start)
if final_end < 0:
    final_end = text.find(on_destroy_anchor, final_start)
segment = text[final_start:final_end]

required = [
    "INVESTIGATION_STEP6_LOAD_INDEX",
    "webView = new WebView(this);",
    'webView.loadUrl("file:///android_asset/index.html");',
    "settings.setJavaScriptEnabled(true);",
    "settings.setDomStorageEnabled(true);",
    "setContentView(webView);",
]
for marker in required:
    if marker not in segment:
        raise RuntimeError(f"Step 6 contract missing: {marker}")

for forbidden in [
    "addJavascriptInterface(",
    "installUiEnhancements(",
]:
    if forbidden in segment:
        raise RuntimeError(f"Step 6 isolation violated by: {forbidden}")

MAIN.write_text(text, encoding="utf-8")
print("Investigation step 6 applied: packaged index.html loads with JavaScript enabled, without Android bridge or UI enhancement injection.")
