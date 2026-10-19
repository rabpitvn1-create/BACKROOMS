# CRT TV v3 — reference 566 follow-up

Baseline: bf86e1e07b0db4daeca040e9723503d46353d89d, PR #654. The previous TV treatment added a rounded outline but its subtle gradient did not reproduce the visible glass, fine scanlines and broken VHS tracking in reference 566. SP/CH labels also differed from the requested counter-style OSD. This note supersedes the v2 note on those details.

## Behavior

Two built-in Image Gen alpha assets provide the left glass reflection and broken white tracking texture. The glass output retained an unwanted bright central ring, so CSS masks the asset to its left reflection only; the central/right image remains clear. A CSS vignette and fine scanlines complete the optical treatment. The tracking strip is alpha-cropped, downsampled and converted to WebP without recoloring, then swept vertically by a transform. Reduced-motion preference stops the sweep. No shader, dependency, canvas or image processing runs in the app.

The TV retains a 1.38 aspect ratio across narrow, wide and short viewports rather than stretching its glass or scene. The existing repeated GM frame rails remain unchanged. PLAY/REC and the header camera/signal/battery remain decorative. Bottom counters show monotonic elapsed page-view time, not Core/game time or actual recording. They reset on page reload and never enter saves, diagnostics state or GM prompts. One interval and one direct-child observer survive repeated native snapshot replacements without multiplying HUDs or replaying callbacks. Snapshot remains a direct child of .game for combat Dice insertion.

## Assets and provenance

Runtime sizes/hashes and source dimensions/crop are in crt-tv-v3-assets.json. Total runtime addition: 122,436 bytes. Original generated PNGs, runtime WebPs and browser previews are archived in [CRT_TV_v3_20261019](https://drive.google.com/drive/folders/1fvgxpdWGRSkNwqSOMI-lc4xQnhvZ3T70). Source PNGs are preserved separately from conversion outputs. No Canon, scenery, portrait or original five industrial assets changed.

Glass generation revision requested a thin rounded black CRT bezel, a subtle left reflected-window glow, an almost clear rectangular opening, dark soft edge shadow and faint grain; it explicitly rejected the initial white ring, circular hole, scenes, characters, labels and opaque backdrops. Since the revision still contained unwanted central highlights, only the left reflection is composited.

Tracking generation requested an isolated thin horizontal broken white/grey VHS noise band on transparency: irregular short dashes, a few longer discontinuous traces and tiny grain, with no scene, text, frame or opaque rectangle. Packaging crops transparent margins before downsampling. Generated images were recovered after the interrupted session; the glass runtime hash matches the earlier prepared asset.

## Verification and limits

Focused JavaScript regressions cover one HUD/one timer after native redraws, elapsed-counter formatting, unchanged Core bridge/state and missing packaged textures. Semantic and combat layout checks are required before publication. Browser checks use actual shipped HTML/CSS/JS and decoded MainActivity renderer, with local image URL adaptation and synthetic Core/Save/Load fixtures. At 390x844 DPR1, 320x640 DPR1, 1280x800 DPR1 and 390x844 DPR3: images load, ratio holds, counters advance, tracking animates/stops under reduced motion, frame rails repeat, Prologue lifecycle and menu confirmations work, MAP renders 45 nodes/eight clusters with pinch/wheel/button zoom, and no page exceptions occur. Full and TV-crop screenshots were inspected. These are browser checks, not actual Android/Core/provider playtests or proof of an exact visual match. No new claims about live GM reliability. Final CI/APK links belong on the PR. No emulator, merge or release.
