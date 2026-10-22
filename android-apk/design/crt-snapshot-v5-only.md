# CRT snapshot only; revert unsolicited UI redesign — 20 October 2026

Baseline: 72568dcbc2c2ac8cdd908391d601929cb6249f45. User rejected the v4 scope: only the snapshot should resemble curved CRT glass; GM frame, header and fonts were not requested. This note supersedes v4 as the current visual contract.

## Scope correction

Restore industrial-crt-v1.css and snapshot-tv.js byte for byte from pre-v4 fd7bc09a333865de43906efc986c8e96fdf8e309. Restore the complete index.html from that same baseline, adding only a link to the isolated snapshot-crt.css. This restores the original GM/player/Core frames, Play fonts, header/menu/icons, gameplay buttons, GM text rendering/whitespace, log alignment and one-time gold PROLOGUE overlay lifecycle. Delete the unsolicited VT323 font/license and three v4 skins. Earlier version documents and Drive archives remain historical evidence, not active themes.

## Actual curved opening

A small native SVG defines a cubic Bezier aperture with all four edges bowing outward between the corner shoulders. Top/bottom edges are not straight rails connected by rounded corners. The opaque even-odd matte covers the outside of that same path above the native room/actor composite, OSD and tracking; a dark inner lip, gradient rim and soft window reflection provide the glass depth. The center stays transparent and live. CSS adds fine scanlines/edge shading; the broken tracking band drifts inside the visible picture, and reduced-motion preference freezes it.

The curve is deterministic vector geometry, so no regenerated scenery, screenshot wallpaper, new font, bitmap extraction or shader framework is necessary. It changes the aperture and light treatment; it does not simulate physical CRT electron optics or warp the underlying image texture. Room and character asset selection, native renderer callbacks and Core remain authoritative. Snapshot remains a direct child of game for Dice insertion; every decoration ignores pointer events. Existing single-HUD/single-clock behavior is unchanged.

Only snapshot-prefixed selectors occur in the new stylesheet. Unit regression evaluates all four Bezier edge midpoints to distinguish a bowed aperture from a rounded rectangle, checks the matte shares the same path and rejects packaged APKs missing the SVG. Packaging compares both new files byte for byte and requires the stylesheet link.

## Verification

81 focused JS tests PASS, semantic/combat-layout checks PASS. Source comparison confirms the original theme/HUD/Prologue regression is restored byte for byte and index.html differs from pre-v4 only by the CRT CSS link. Chromium 390x844 DPR1, 320x640 DPR1, 1280x800 DPR1 and 390x844 DPR3 PASS: native background/actor load, 1.38 ratio, one HUD across redraws, counter advancement, moving/frozen tracking, MAP 45 nodes/eight clusters with zoom/pinch/wheel, Prologue lifecycle and menu Save/Load confirmations. At each viewport, disabling/re-enabling the CRT stylesheet leaves computed fonts/colors/backgrounds/borders/padding and rectangles of the header, GM, controls and Prologue identical. No page exceptions. Full screenshots and TV crops were visually inspected.

Browser Core/Save/Load/graph are fixtures; this does not establish real-device WebView appearance, live GM quality or pixel identity with reference 566. No Android Emulator, Canon changes, save changes, merge or release. Final CI and one new APK artifact are recorded on PR #654.

[Versioned Drive evidence](https://drive.google.com/drive/folders/1f1vTeH4-uIvXPFa-AP-32CiLkm5WV1d-): SVG/CSS, this note and full/TV previews. The SVG source is also included in Git; no new generated raster art is used by this correction.
