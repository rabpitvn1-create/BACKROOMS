# TV snapshot / non-stretched frame rails — 19 October 2026

Baseline: `624cf18dedb0dcbc419a6be8c0f8568dd57aafc6`, PR #654. User evidence: `Screenshot_20261019-212342_Backroom.png` / `577.png`; TV/header target: `566.png`.

## Cause and correction

The first industrial theme preserved the four corners, but still used `border-image-repeat: stretch` on the intervening rails. Screws and hazard tape were baked into those rails. Their long dimension therefore followed the panel's aspect ratio: horizontal screws widened and vertical screws narrowed. Checking only corner shapes and the absence of overlapping content missed this visual defect.

Frame rails now use **repeat** at the scale determined by border thickness. Repetition clips/tiles the source strip rather than changing its length-to-thickness ratio. Corners remain nine-sliced. The fix covers GM/player/Core messages, menu/cards, Explorer dialogs and inherited inventory/combat frame consumers. Steel/hazard button skins keep their existing protected-corner treatment. The original five Image Gen assets and hashes are unchanged; there is no new raster artwork or recoloring.

Snapshot is now a rounded black TV bezel with restrained glass reflection instead of the rusted metal frame. CSS adds one low-opacity tracking band with an eight-second transform animation, no flashing or gameplay timer. `prefers-reduced-motion` stops the sweep. PLAY/REC, cassette/SP/CH 01 and the header camera/signal/battery symbols are explicitly decorative, hidden from accessibility output; they are not device telemetry, recording capability, a puzzle clue or game time. No fake elapsed-time counter is shown.

`ui/snapshot-tv.js` observes only direct children of the existing `#snapshot`. The native renderer clears that element before replacing scenery/characters, so the helper mounts exactly one HUD after a replacement. Its own insertion does not create another HUD. It reads no game state, calls no Core/provider bridge, wraps no gameplay callback and starts no JS timer. `#snapshot` stays a direct child of `.game`: combat's existing insertion of the Dice panel immediately after it continues to work.

## Files and validation

- `index.html`: decorative header symbols and TV helper script reference.
- `ui/industrial-crt-v1.css`: bezel/glass/OSD/tracking, narrow header layout and repeating frame rails.
- `ui/snapshot-tv.js`: presentation-only HUD mounting.
- Existing packaging verifier, extraction lists and regression fixture now require/compare the TV helper; regressions cover HUD uniqueness after repeated native replacements, state/callback preservation and a missing packaged helper.

Local focused JavaScript gate: **78 tests PASS**. Semantic and combat-layout checks pass. Browser verification uses the shipped HTML/CSS/JS plus the decoded MainActivity enhancement script with only local-server URL adaptation. At 390×844, 320×640 and 1280×800: actual snapshot background/character load, one HUD after two renders, changing tracking transform, stopped animation under reduced motion, repeated rail computed style, menu/Party/detail, Save/Load fixture, Prologue lifecycle, 45-node/eight-cluster MAP with actual touch pinch/wheel/buttons/node selection all pass; no page exceptions. Short/wide/tall frame screenshots were inspected. Browser Save/Load and graph/narration are fixtures, not a live Core/API playthrough.

The same [UI Drive folder](https://drive.google.com/drive/folders/1H13I34yN6FfIN4qdXpoN3j2InSmjZT60) receives versioned corrected previews and this note; original PNG/WebP files are preserved. Final CI/commit/APK links are recorded on the PR after one build. No Android Emulator, merge/release, Canon edits or save migration. Actual-device visual confirmation is still needed; this follow-up does not claim a real Android playtest.
