from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")

method_start = '  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})\n  @Override public void onCreate(Bundle savedInstanceState) {\n'
immersive_anchor = "\n  private void applyImmersiveFullscreen() {\n"
on_destroy_anchor = "\n  @Override protected void onDestroy() {\n"
start = text.find(method_start)
if start < 0:
    raise RuntimeError("Step 4 onCreate start not found")
end = text.find(immersive_anchor, start)
if end < 0:
    end = text.find(on_destroy_anchor, start)
if end < 0:
    raise RuntimeError("Step 4 onCreate end not found")

on_create = '''  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    try {
      applyImmersiveFullscreen();
    } catch (Throwable error) {
      Log.w("BackroomStartup", "Immersive fullscreen unavailable; continuing normally.", error);
    }

    TextView nativeProbe = new TextView(this);
    nativeProbe.setTextSize(18f);
    nativeProbe.setPadding(36, 48, 36, 48);
    nativeProbe.setText("BACKROOM STEP 4 NATIVE STARTUP PROBE\\n\\nWebView bootstrap intentionally disabled for Android 16 isolation.");
    setContentView(nativeProbe);
  }
'''
text = text[:start] + on_create + text[end:]

final_start = text.find(method_start)
final_end = text.find(immersive_anchor, final_start)
if final_end < 0:
    final_end = text.find(on_destroy_anchor, final_start)
segment = text[final_start:final_end]

required = [
    "BACKROOM STEP 4 NATIVE STARTUP PROBE",
    "setContentView(nativeProbe);",
    "applyImmersiveFullscreen();",
]
for marker in required:
    if marker not in segment:
        raise RuntimeError(f"Step 4 native startup contract missing: {marker}")

for forbidden in [
    "new WebView(this)",
    "webView.loadUrl(",
    "webView.addJavascriptInterface(",
]:
    if forbidden in segment:
        raise RuntimeError(f"WebView bootstrap still present in step 4 onCreate: {forbidden}")

MAIN.write_text(text, encoding="utf-8")
print("Investigation step 4 applied: native-only startup probe, WebView bootstrap disabled in onCreate.")
