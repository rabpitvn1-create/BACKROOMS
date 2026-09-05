from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def ensure_after(source: str, anchor: str, addition: str, label: str) -> str:
    if addition.strip() in source:
        return source
    count = source.count(anchor)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(anchor, anchor + addition, 1)


text = ensure_after(text, "import android.os.Bundle;\n", "import android.os.Build;\n", "Build import")
text = ensure_after(text, "import android.os.Build;\n", "import android.view.View;\nimport android.view.WindowInsets;\nimport android.view.WindowInsetsController;\nimport android.view.WindowManager;\n", "immersive imports")

# Android 11+ can throw from Window#getInsetsController before the DecorView exists.
# Remove the legacy early call immediately after super.onCreate(), then apply
# immersive flags only after setContentView() has attached the window decor.
on_create_anchor = "  @Override public void onCreate(Bundle savedInstanceState) {\n    super.onCreate(savedInstanceState);\n"
unsafe_on_create = on_create_anchor + "    applyImmersiveFullscreen();\n"
if unsafe_on_create in text:
    text = text.replace(unsafe_on_create, on_create_anchor, 1)

content_view_anchor = "    setContentView(webView);\n"
safe_on_create = content_view_anchor + "    applyImmersiveFullscreen();\n"
if safe_on_create not in text:
    if text.count(content_view_anchor) != 1:
        raise RuntimeError("setContentView immersive anchor missing or ambiguous")
    text = text.replace(content_view_anchor, safe_on_create, 1)

method_anchor = "\n  @Override protected void onDestroy() {\n"
method = '''
  private void applyImmersiveFullscreen() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      getWindow().getAttributes().layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      getWindow().setDecorFitsSystemWindows(false);
      WindowInsetsController controller = getWindow().getInsetsController();
      if (controller != null) {
        controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        controller.setSystemBarsBehavior(
            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
      }
    } else {
      getWindow().getDecorView().setSystemUiVisibility(
          View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
              | View.SYSTEM_UI_FLAG_FULLSCREEN
              | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
              | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
  }

  @Override public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) applyImmersiveFullscreen();
  }

  @Override protected void onResume() {
    super.onResume();
    applyImmersiveFullscreen();
  }
'''
if "private void applyImmersiveFullscreen()" not in text:
    if text.count(method_anchor) != 1:
        raise RuntimeError("immersive method insertion anchor missing or ambiguous")
    text = text.replace(method_anchor, "\n" + method + method_anchor, 1)

required = [
    safe_on_create,
    "WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE",
    "WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars()",
    "View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY",
    "LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES",
    "onWindowFocusChanged(boolean hasFocus)",
]
for marker in required:
    if marker not in text:
        raise RuntimeError(f"Immersive fullscreen contract missing: {marker}")
if unsafe_on_create in text:
    raise RuntimeError("Immersive fullscreen must not run before setContentView")

MAIN.write_text(text, encoding="utf-8")
print("Immersive fullscreen enabled after content attachment with Android 11+ startup safety.")
