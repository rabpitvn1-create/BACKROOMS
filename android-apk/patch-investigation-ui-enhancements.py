from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")

method_start = '  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})\n  @Override public void onCreate(Bundle savedInstanceState) {\n'
immersive_anchor = "\n  private void applyImmersiveFullscreen() {\n"
on_destroy_anchor = "\n  @Override protected void onDestroy() {\n"

start = text.find(method_start)
if start < 0:
    raise RuntimeError("Step 8 onCreate start not found")
end = text.find(immersive_anchor, start)
if end < 0:
    end = text.find(on_destroy_anchor, start)
if end < 0:
    raise RuntimeError("Step 8 onCreate end not found")
segment = text[start:end]

if "INVESTIGATION_STEP7_JS_BRIDGE" not in segment:
    raise RuntimeError("Step 8 requires the verified step 7 bridge baseline")
if 'webView.addJavascriptInterface(new GameBridge(), "Android");' not in segment:
    raise RuntimeError("Step 8 baseline is missing the Android JavaScript bridge")
if "installUiEnhancements(" in segment:
    raise RuntimeError("Step 8 baseline unexpectedly already contains enhancement injection")

old = '''      // INVESTIGATION_STEP7_JS_BRIDGE
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
new = '''      // INVESTIGATION_STEP8_UI_ENHANCEMENTS
      // Same verified step 7 startup path, with only the existing page-finished UI enhancement hook restored.
      webView = new WebView(this);
      WebSettings settings = webView.getSettings();
      settings.setJavaScriptEnabled(true);
      settings.setDomStorageEnabled(true);
      settings.setAllowFileAccess(true);
      webView.setWebViewClient(new WebViewClient() {
        @Override public void onPageFinished(WebView view, String url) {
          super.onPageFinished(view, url);
          installUiEnhancements();
        }
      });
      webView.addJavascriptInterface(new GameBridge(), "Android");
      setContentView(webView);
      webView.loadUrl("file:///android_asset/index.html");
'''

if old not in text:
    raise RuntimeError("Step 8 bridge baseline anchor not found")
text = text.replace(old, new, 1)

final_start = text.find(method_start)
final_end = text.find(immersive_anchor, final_start)
if final_end < 0:
    final_end = text.find(on_destroy_anchor, final_start)
final_segment = text[final_start:final_end]

required = [
    "INVESTIGATION_STEP8_UI_ENHANCEMENTS",
    "webView = new WebView(this);",
    "webView.setWebViewClient(new WebViewClient()",
    "@Override public void onPageFinished(WebView view, String url)",
    "installUiEnhancements();",
    'webView.addJavascriptInterface(new GameBridge(), "Android");',
    'webView.loadUrl("file:///android_asset/index.html");',
]
for marker in required:
    if marker not in final_segment:
        raise RuntimeError(f"Step 8 contract missing: {marker}")

if final_segment.count("installUiEnhancements();") != 1:
    raise RuntimeError("Step 8 must invoke UI enhancements exactly once from startup wiring")
if final_segment.count("addJavascriptInterface(") != 1:
    raise RuntimeError("Step 8 must preserve exactly one Android JavaScript bridge")

MAIN.write_text(text, encoding="utf-8")
print("Investigation step 8 applied: page-finished UI enhancement injection restored on verified step 7 baseline.")
