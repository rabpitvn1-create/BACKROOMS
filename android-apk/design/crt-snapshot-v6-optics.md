# Snapshot-only CRT optics refinement — 20 October 2026

Baseline f8e9215b1cb796aa9205fdda005466323be91b1c, PR #654. Scope remains exclusively the CRT snapshot. GM frame, header, fonts, buttons, Prologue, Core and save behavior must not change.

## Correction

V5 curved the visible aperture but left the native picture texture flat and used an overly grey outer surround. The existing aperture SVG now uses a black bezel and stronger curved window reflection. The isolated snapshot stylesheet applies a weak barrel displacement to the already composed snapshot: scenery, actors, OSD, scanlines, tracking and the frame share the same optical field. No native elements are wrapped, duplicated, hidden or replaced. No room/portrait selection or gameplay callbacks change.

crt-optics.svg embeds a small numeric RGBA displacement field, not scene artwork. For normalized x/y in [-1,1], r2=(x*x+y*y)/2; R=.5+.5*x*r2 and G=.5+.5*y*r2. SourceGraphic is sampled with RG at scale .024 in object bounding-box units. Thus displacement is zero at the center and grows toward the edge; maximum per-axis sampling displacement is 1.2 percent of snapshot size. The map is precomputed, so no pixel loops, generated canvases, render timers, shaders or added packages execute in application code. A tiny blur softens the final raster. The black surround and reflection change only the existing snapshot SVG; all selectors remain snapshot-prefixed.

The theme and HUD script still match pre-v4 fd7bc09 byte for byte. index.html still matches that baseline after removing its one isolated CRT stylesheet link. This explicitly preserves the original GM/player/Core frames, Play fonts, header/menu/icons, buttons, log rendering/spacing and one-time gold PROLOGUE overlay. No new font or UI redesign.

## Verification

83 focused JavaScript tests PASS; semantic/combat-layout PASS. New tests decode the embedded PNG field, verify centered/signed/opaque radial data and native SourceGraphic input, and reject missing packaged optics. APK contract extraction and verification cover the optics SVG in addition to the existing aperture and stylesheet.

Browser checks use actual packaged-source HTML/CSS/JS and the decoded native enhancement script with fixture Core/Save/Load and 45-room graph. At 390x844 DPR1, 320x640 DPR1, 1280x800 DPR1 and 390x844 DPR3: scenery/actors load, aspect ratio holds, HUD remains unique through redraws, counters advance, tracking animates or freezes under reduced-motion, Prologue lifecycle/menu confirmations/MAP zoom-pinch-wheel pass. Disabling the isolated CRT stylesheet leaves outside-UI computed fonts/colors/backgrounds/borders/padding and rectangles identical. To distinguish an effective optical filter from merely accepted CSS, the barrel URL is tested without blur against filter:none; peripheral scene pixels must change at every viewport. Peripheral sampling matters because the intended displacement approaches zero near the center. Full/TV screenshots are inspected; no page exceptions.

This is a bounded display effect, not a physical CRT simulation or proof of pixel identity. Android/WebView device appearance and performance remain unverified; no live GM API calls. Core/save/Canon/puzzle/graph/prompt are untouched. No Emulator, merge, release or repeated APK build. Final CI and artifact links are recorded on PR #654.

[Drive evidence](https://drive.google.com/drive/folders/1M3HYmefK3aDqP4d-N7EqQm6q17HdaX07) contains the three source display files, this note and actual full/TV browser previews. No new raster art or font is generated.
