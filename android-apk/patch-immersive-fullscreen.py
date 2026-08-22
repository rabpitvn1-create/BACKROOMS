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


# Investigation step 2: preserve the same lifecycle call sites but make immersive fullscreen a
# no-op. This changes one variable only: fullscreen/insets behavior.
on_create_anchor = "  @Override public void onCreate(Bundle savedInstanceState) {\n    super.onCreate(savedInstanceState);\n"
on_create_new = on_create_anchor + "    applyImmersiveFullscreen();\n"
if "    applyImmersiveFullscreen();\n" not in text:
    if text.count(on_create_anchor) != 1:
        raise RuntimeError("onCreate immersive anchor missing or ambiguous")
    text = text.replace(on_create_anchor, on_create_new, 1)

method_anchor = "\n  @Override protected void onDestroy() {\n"
method = '''
  private void applyImmersiveFullscreen() {
    // Investigation step 2: intentionally disabled. Keep this method and all call sites so the
    // only changed variable is WindowInsets/system-bar behavior.
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
    "applyImmersiveFullscreen();",
    "private void applyImmersiveFullscreen()",
    "onWindowFocusChanged(boolean hasFocus)",
    "@Override protected void onResume()",
    "Investigation step 2: intentionally disabled",
]
for marker in required:
    if marker not in text:
        raise RuntimeError(f"No-immersive investigation contract missing: {marker}")

for forbidden in [
    "WindowInsetsController",
    "WindowInsets.Type.statusBars()",
    "SYSTEM_UI_FLAG_IMMERSIVE_STICKY",
    "LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES",
    "setDecorFitsSystemWindows(false)",
]:
    if forbidden in text:
        raise RuntimeError(f"Fullscreen behavior still present during step 2: {forbidden}")

MAIN.write_text(text, encoding="utf-8")
print("Investigation step 2 applied: immersive fullscreen disabled while lifecycle call sites remain unchanged.")
