from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")

method_start = '  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})\n  @Override public void onCreate(Bundle savedInstanceState) {\n'
immersive_anchor = "\n  private void applyImmersiveFullscreen() {\n"
on_destroy_anchor = "\n  @Override protected void onDestroy() {\n"

start = text.find(method_start)
if start < 0:
    raise RuntimeError("Step 7 onCreate start not found")
end = text.find(immersive_anchor, start)
if end < 0:
    end = text.find(on_destroy_anchor, start)
if end < 0:
    raise RuntimeError("Step 7 onCreate end not found")
segment = text[start:end]

if "INVESTIGATION_STEP6_LOAD_INDEX" not in segment:
    raise RuntimeError("Step 7 requires the verified step 6 index-load baseline")
if "addJavascriptInterface(" in segment:
    raise RuntimeError("Step 7 baseline unexpectedly already contains a JavaScript bridge")
if "installUiEnhancements(" in segment:
    raise RuntimeError("Step 7 baseline unexpectedly contains UI enhancement injection")

old = '''      // INVESTIGATION_STEP6_LOAD_INDEX
      // Construct WebView and load the packaged page only. No Android JS bridge or UI enhancement injection.
      webView = new WebView(this);
      WebSettings settings = webView.getSettings();
      settings.setJavaScriptEnabled(true);
      settings.setDomStorageEnabled(true);
      settings.setAllowFileAccess(true);
      setContentView(webView);
      webView.loadUrl("file:///android_asset/index.html");
'''
new = '''      // INVESTIGATION_STEP7_JS_BRIDGE
      // Same verified index-load path as step 6, with only the Android JavaScript bridge restored.
      // UI enhancement injection remains disabled so bridge registration is the sole new startup variable.
      webView = new WebView(this);
      WebSettings settings = webView.getSettings();
      settings.setJavaScriptEnabled(true);
      settings.setDomStorageEnabled(true);
      settings.setAllowFileAccess(true);
      webView.addJavascriptInterface(new GameBridge(), "Android");
      setContentView(webView);
      webView.loadUrl("file:///android_asset/index.html");
'''

if old not in text:
    raise RuntimeError("Step 7 index-load anchor not found")
text = text.replace(old, new, 1)

final_start = text.find(method_start)
final_end = text.find(immersive_anchor, final_start)
if final_end < 0:
    final_end = text.find(on_destroy_anchor, final_start)
final_segment = text[final_start:final_end]

required = [
    "INVESTIGATION_STEP7_JS_BRIDGE",
    "webView = new WebView(this);",
    'webView.addJavascriptInterface(new GameBridge(), "Android");',
    'webView.loadUrl("file:///android_asset/index.html");',
    "settings.setJavaScriptEnabled(true);",
    "settings.setDomStorageEnabled(true);",
    "setContentView(webView);",
]
for marker in required:
    if marker not in final_segment:
        raise RuntimeError(f"Step 7 contract missing: {marker}")

if final_segment.count("addJavascriptInterface(") != 1:
    raise RuntimeError("Step 7 must register exactly one JavaScript bridge")
if "installUiEnhancements(" in final_segment:
    raise RuntimeError("Step 7 isolation violated by UI enhancement injection")

MAIN.write_text(text, encoding="utf-8")
print("Investigation step 7 applied: Android JavaScript bridge restored on the verified index-load baseline; UI enhancement injection remains disabled.")
