# BACKROOMS 1.4.4.8 — Automatic Background Light Flicker

BACKROOMS 1.4.4.8 adds automatic light-source animation for static Level snapshot backgrounds while preserving the existing gameplay, combat, Entity, and character overlay layers.

## Highlights

- **Automatic light-source detection**
  - Each new `.snapshot-bg` is downsampled and analyzed once for luminance, local contrast, and light-like color characteristics.
  - Neutral-white, warm-yellow, and cool-white fixtures can be detected without editing individual Level images.
  - Large uniformly bright regions are rejected so walls and broad scenery do not flicker as false lights.

- **Background-only visual effect**
  - Detected light regions receive a slow, subtle flicker/glow through a dedicated canvas layer.
  - The effect stays below Kai, companion, and Entity overlays.
  - Snapshot source files are not modified.

- **Performance and accessibility**
  - Image analysis runs only when a new background loads and is cached by source/geometry.
  - There is no AI call and no per-frame image analysis.
  - Animation pauses while the document is hidden and respects `prefers-reduced-motion`.
  - Canvas or image-analysis failures fail closed, leaving the original static background intact.

- **Regression coverage**
  - Dark scenes do not invent light sources.
  - Small fluorescent and warm lamp fixtures are detected.
  - Large uniformly bright areas are rejected.
  - `object-fit: cover` crop mapping is tested.
  - Runtime injection and packaged APK asset/script contracts are verified in preflight.

## Verification

The release workflow verifies:

- Android source version is exactly `versionCode 107` / `versionName '1.4.4.8'`.
- The full Android runtime patch chain applies cleanly.
- Final runtime and provider-routing contracts pass.
- Auto Light Flicker Node regressions pass.
- Kotlin unit tests pass.
- A fresh debug APK builds successfully.
- The packaged APK reports `versionName=1.4.4.8` and `versionCode=107`.
- The published release asset is downloaded again and verified byte-for-byte by SHA-256 before the workflow completes.
