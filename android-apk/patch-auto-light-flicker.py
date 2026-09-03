"""Install automatic background light flicker with a native sampler for packaged WebView assets."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent
HTML = ROOT / "app/src/main/assets/index.html"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
ENGINE = ROOT / "app/src/main/assets/auto-light-flicker.js"

if not ENGINE.is_file() or ENGINE.stat().st_size <= 0:
    raise RuntimeError("Auto light flicker engine asset is missing")

html = HTML.read_text(encoding="utf-8")
main = MAIN.read_text(encoding="utf-8")
if "className='snapshot-bg'" not in main:
    raise RuntimeError("Auto light flicker requires the final layered snapshot background renderer")

# Packaged level snapshots are file:///android_asset URLs. Modern Android WebView deliberately
# keeps file-to-file origin access locked down, so JavaScript canvas readback can fail even though
# the image itself renders. Sample those trusted packaged assets natively and return only a tiny
# 128px RGBA frame to the existing JavaScript detector. This preserves one detector algorithm
# without weakening WebView file-origin security.
helper = r'''  private String autoLightPixelSample(String source, int boxWidth, int boxHeight) {
    final String prefix = "file:///android_asset/";
    if (source == null || !source.startsWith(prefix)) return "";
    String assetPath = source.substring(prefix.length());
    if (!assetPath.startsWith("level_snapshots/") || assetPath.contains("..") || assetPath.indexOf('\\') >= 0) return "";

    int safeWidth = Math.max(1, boxWidth);
    int safeHeight = Math.max(1, boxHeight);
    int detectWidth = 128;
    int detectHeight = Math.max(48, Math.min(96, Math.round(detectWidth * (safeHeight / (float)safeWidth))));
    android.graphics.Bitmap bitmap = null;
    android.graphics.Bitmap crop = null;
    android.graphics.Bitmap scaled = null;
    try (InputStream stream = getAssets().open(assetPath)) {
      bitmap = android.graphics.BitmapFactory.decodeStream(stream);
      if (bitmap == null) return "";

      int imageWidth = bitmap.getWidth();
      int imageHeight = bitmap.getHeight();
      float imageRatio = imageWidth / (float)Math.max(1, imageHeight);
      float boxRatio = safeWidth / (float)safeHeight;
      int cropX = 0;
      int cropY = 0;
      int cropWidth = imageWidth;
      int cropHeight = imageHeight;
      if (imageRatio > boxRatio) {
        cropWidth = Math.max(1, Math.round(imageHeight * boxRatio));
        cropX = Math.max(0, (imageWidth - cropWidth) / 2);
      } else {
        cropHeight = Math.max(1, Math.round(imageWidth / boxRatio));
        cropY = Math.max(0, (imageHeight - cropHeight) / 2);
      }

      crop = android.graphics.Bitmap.createBitmap(bitmap, cropX, cropY, cropWidth, cropHeight);
      scaled = android.graphics.Bitmap.createScaledBitmap(crop, detectWidth, detectHeight, true);
      int[] colors = new int[detectWidth * detectHeight];
      scaled.getPixels(colors, 0, detectWidth, 0, 0, detectWidth, detectHeight);
      byte[] rgba = new byte[colors.length * 4];
      for (int i = 0; i < colors.length; i++) {
        int color = colors[i];
        int offset = i * 4;
        rgba[offset] = (byte)android.graphics.Color.red(color);
        rgba[offset + 1] = (byte)android.graphics.Color.green(color);
        rgba[offset + 2] = (byte)android.graphics.Color.blue(color);
        rgba[offset + 3] = (byte)android.graphics.Color.alpha(color);
      }

      JSONObject out = new JSONObject();
      out.put("width", detectWidth);
      out.put("height", detectHeight);
      out.put("rgba", android.util.Base64.encodeToString(rgba, android.util.Base64.NO_WRAP));
      return out.toString();
    } catch (Exception ignored) {
      return "";
    } finally {
      if (scaled != null && scaled != crop && !scaled.isRecycled()) scaled.recycle();
      if (crop != null && crop != bitmap && !crop.isRecycled()) crop.recycle();
      if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }
  }

'''
bridge_anchor = "  private class GameBridge {\n"
if "private String autoLightPixelSample(" not in main:
    if main.count(bridge_anchor) != 1:
        raise RuntimeError(f"Auto light native helper anchor expected once, found {main.count(bridge_anchor)}")
    main = main.replace(bridge_anchor, helper + bridge_anchor, 1)

bridge_method = '''  private class GameBridge {
    @JavascriptInterface public String sampleAutoLightPixels(String source, int boxWidth, int boxHeight) {
      return autoLightPixelSample(source, boxWidth, boxHeight);
    }

'''
if "@JavascriptInterface public String sampleAutoLightPixels(" not in main:
    if main.count(bridge_anchor) != 1:
        raise RuntimeError(f"Auto light GameBridge anchor expected once, found {main.count(bridge_anchor)}")
    main = main.replace(bridge_anchor, bridge_method, 1)

style = r'''<style id="autoLightFlickerStyle">
.snapshot .snapshot-auto-light-layer{position:absolute;inset:0;width:100%;height:100%;z-index:1;pointer-events:none;transform:translateZ(0);will-change:opacity}
.snapshot .snapshot-auto-light-glow{mix-blend-mode:screen;opacity:.1;filter:blur(1.8px);animation:snapshotAutoLightGlow var(--auto-light-period,5.2s) ease-in-out var(--auto-light-delay,0ms) infinite}
.snapshot .snapshot-auto-light-dim{mix-blend-mode:multiply;opacity:.18;filter:blur(.6px);animation:snapshotAutoLightDim var(--auto-light-period,5.2s) ease-in-out var(--auto-light-delay,0ms) infinite}
@keyframes snapshotAutoLightGlow{0%,100%{opacity:.08}16%{opacity:.32}34%{opacity:.12}52%{opacity:.55}69%{opacity:.14}84%{opacity:.38}}
@keyframes snapshotAutoLightDim{0%,100%{opacity:.34}16%{opacity:.12}34%{opacity:.28}52%{opacity:.04}69%{opacity:.24}84%{opacity:.09}}
.auto-light-paused .snapshot-auto-light-layer{animation-play-state:paused}
@media(prefers-reduced-motion:reduce){.snapshot .snapshot-auto-light-glow{animation:none!important;opacity:.18!important}.snapshot .snapshot-auto-light-dim{animation:none!important;opacity:.08!important}}
</style>'''
script = '<script src="auto-light-flicker.js"></script>'

if 'id="autoLightFlickerStyle"' not in html:
    if html.count("</head>") != 1:
        raise RuntimeError(f"Auto light flicker head anchor expected once, found {html.count('</head>')}")
    html = html.replace("</head>", style + "\n</head>", 1)

if script not in html:
    if html.count("</body>") != 1:
        raise RuntimeError(f"Auto light flicker body anchor expected once, found {html.count('</body>')}")
    html = html.replace("</body>", script + "\n</body>", 1)

for marker in (
    'id="autoLightFlickerStyle"',
    'snapshotAutoLightGlow',
    'snapshotAutoLightDim',
    'snapshot-auto-light-glow',
    'snapshot-auto-light-dim',
    'src="auto-light-flicker.js"',
):
    if marker not in html:
        raise RuntimeError("Auto light flicker runtime marker missing: " + marker)
for marker in (
    "private String autoLightPixelSample(",
    "@JavascriptInterface public String sampleAutoLightPixels(",
    "level_snapshots/",
    "android.util.Base64.NO_WRAP",
):
    if marker not in main:
        raise RuntimeError("Auto light native sampler marker missing: " + marker)

HTML.write_text(html, encoding="utf-8")
MAIN.write_text(main, encoding="utf-8")
print("Automatic snapshot light flicker installed: packaged assets use native pixel sampling; detected lights pulse and dim without weakening WebView file security.")
