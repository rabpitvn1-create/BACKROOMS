from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")

method_start = '  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})\n  @Override public void onCreate(Bundle savedInstanceState) {\n'
immersive_anchor = "\n  private void applyImmersiveFullscreen() {\n"
on_destroy_anchor = "\n  @Override protected void onDestroy() {\n"

start = text.find(method_start)
if start < 0:
    raise RuntimeError("Step 5 onCreate start not found")
end = text.find(immersive_anchor, start)
if end < 0:
    end = text.find(on_destroy_anchor, start)
if end < 0:
    raise RuntimeError("Step 5 onCreate end not found")

on_create = '''  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    try {
      applyImmersiveFullscreen();
    } catch (Throwable error) {
      Log.w("BackroomStartup", "Immersive fullscreen unavailable; continuing normally.", error);
    }
    try {
      // INVESTIGATION_STEP5_WEBVIEW_SHELL
      // Construct and attach WebView only. Do not load assets, add JS bridge, or inject UI.
      webView = new WebView(this);
      setContentView(webView);
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
    "INVESTIGATION_STEP5_WEBVIEW_SHELL",
    "webView = new WebView(this);",
    "setContentView(webView);",
    "showStartupFallback(error);",
]
for marker in required:
    if marker not in segment:
        raise RuntimeError(f"Step 5 contract missing: {marker}")

for forbidden in [
    "loadUrl(",
    "addJavascriptInterface(",
    "installUiEnhancements(",
    "setJavaScriptEnabled(",
    "setDomStorageEnabled(",
]:
    if forbidden in segment:
        raise RuntimeError(f"Step 5 isolation violated by: {forbidden}")

MAIN.write_text(text, encoding="utf-8")
print("Investigation step 5 applied: WebView constructed and attached without page load, JS bridge, or enhancement injection.")
