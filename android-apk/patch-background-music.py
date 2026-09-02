from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
BGM = ROOT / "app/src/main/res/raw/backroom_bgm.ogg"
EXPECTED_BGM_BYTES = 476_879


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


if not BGM.is_file() or BGM.stat().st_size != EXPECTED_BGM_BYTES:
    raise RuntimeError("Background music asset is missing or has unexpected size")

main = MAIN.read_text(encoding="utf-8")

if "import android.media.MediaPlayer;" not in main:
    main = replace_once(
        main,
        "import android.app.Activity;\n",
        "import android.app.Activity;\nimport android.media.MediaPlayer;\n",
        "MediaPlayer import",
    )

if "private MediaPlayer backgroundMusic;" not in main:
    main = replace_once(
        main,
        "  private WebView webView;\n",
        "  private WebView webView;\n  private MediaPlayer backgroundMusic;\n",
        "background music field",
    )

helper = r'''
  private void startBackgroundMusic() {
    try {
      if (backgroundMusic == null) {
        backgroundMusic = MediaPlayer.create(this, R.raw.backroom_bgm);
        if (backgroundMusic == null) return;
        backgroundMusic.setLooping(true);
        backgroundMusic.setVolume(0.40f, 0.40f);
        backgroundMusic.setOnErrorListener((player, what, extra) -> {
          if (backgroundMusic == player) backgroundMusic = null;
          try { player.release(); } catch (RuntimeException ignored) {}
          return true;
        });
      }
      if (!backgroundMusic.isPlaying()) backgroundMusic.start();
    } catch (RuntimeException ignored) {
      releaseBackgroundMusic();
    }
  }

  private void pauseBackgroundMusic() {
    if (backgroundMusic == null) return;
    try {
      if (backgroundMusic.isPlaying()) backgroundMusic.pause();
    } catch (RuntimeException ignored) {}
  }

  private void releaseBackgroundMusic() {
    MediaPlayer player = backgroundMusic;
    backgroundMusic = null;
    if (player == null) return;
    try { player.release(); } catch (RuntimeException ignored) {}
  }

  @Override protected void onPause() {
    pauseBackgroundMusic();
    super.onPause();
  }
'''

on_destroy_anchor = "\n  @Override protected void onDestroy() {\n"
if "private void startBackgroundMusic()" not in main:
    main = replace_once(main, on_destroy_anchor, helper + on_destroy_anchor, "background music lifecycle helpers")

resume_anchor = '''  @Override protected void onResume() {
    super.onResume();
    applyImmersiveFullscreen();
  }
'''
resume_replacement = '''  @Override protected void onResume() {
    super.onResume();
    applyImmersiveFullscreen();
    startBackgroundMusic();
  }
'''
if "    startBackgroundMusic();\n" not in main:
    main = replace_once(main, resume_anchor, resume_replacement, "background music resume hook")

on_destroy_line = "  @Override protected void onDestroy() {\n"
release_destroy = "  @Override protected void onDestroy() {\n    releaseBackgroundMusic();\n"
if "  @Override protected void onDestroy() {\n    releaseBackgroundMusic();\n" not in main:
    main = replace_once(main, on_destroy_line, release_destroy, "background music destroy hook")

for marker in [
    "import android.media.MediaPlayer;",
    "private MediaPlayer backgroundMusic;",
    "MediaPlayer.create(this, R.raw.backroom_bgm)",
    "backgroundMusic.setLooping(true);",
    "backgroundMusic.setVolume(0.40f, 0.40f);",
    "startBackgroundMusic();",
    "pauseBackgroundMusic();",
    "releaseBackgroundMusic();",
    "@Override protected void onPause()",
]:
    if marker not in main:
        raise RuntimeError("Background music runtime marker missing: " + marker)

if main.count("@Override protected void onResume()") != 1:
    raise RuntimeError("Background music expects exactly one Activity onResume override")
if main.count("@Override protected void onPause()") != 1:
    raise RuntimeError("Background music expects exactly one Activity onPause override")
if main.count("releaseBackgroundMusic();") < 2:
    raise RuntimeError("Background music must release on failure and Activity destruction")

MAIN.write_text(main, encoding="utf-8")
print("Native background music enabled: local Ogg/Opus asset, continuous loop, foreground pause/resume lifecycle.")
