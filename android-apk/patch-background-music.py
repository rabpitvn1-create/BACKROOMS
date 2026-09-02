from pathlib import Path
import hashlib

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
BGM = ROOT / "app/src/main/res/raw/backroom_bgm.m4a"
EXPECTED_BGM_BYTES = 4_063_885
EXPECTED_BGM_SHA256 = "f9eca6ee4c8618d310296b19b0f919c50b0e6c85b6d55f73753cce83bef3cce2"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


if not BGM.is_file():
    raise RuntimeError("Background music asset is missing; run fetch-background-music.py first")
if BGM.stat().st_size != EXPECTED_BGM_BYTES or sha256(BGM) != EXPECTED_BGM_SHA256:
    raise RuntimeError("Background music asset does not match the pinned Drive source")

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
print("Native background music enabled: pinned local M4A asset, continuous loop, foreground pause/resume lifecycle.")
