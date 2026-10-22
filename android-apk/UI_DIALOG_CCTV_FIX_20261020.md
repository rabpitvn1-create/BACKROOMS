# Prologue / Player Action dialog and CCTV raster correction

Scope: UI only, based on PR #654 head `3c81f4577c2955dbd88e921b3e451265011b26fc`.

## Causes and behavior

- The industrial theme previously changed only the Prologue reader background; its border, header and buttons retained the older skin. The action composer had no industrial theme overrides. Both dialogs now reuse the existing repeating panel rails, CRT surface, steel close button and gold hazard action button. The one-time gold rounded Prologue offer, full reader text and consumption lifecycle remain intact.
- The old action submit selector contains two IDs. Composer overrides include the same parent ID so the themed button width and margins take effect without horizontal overflow.
- The action submit button was enabled before the first input event despite an empty textarea. Initialize it disabled; existing input validation and submission guards continue to apply. Closing/reopening preserves the draft and does not submit anything.
- A generated tracking texture was animated by both the base theme and CRT override. Remove both tracking definitions and their motion rules. One stationary 1px dark line per 3 CSS pixels now covers the snapshot; remove the duplicate scanline layers. The existing curved aperture, barrel optics, black monitor casing, OSD and native scene composition are preserved.

Changed runtime files: `explorer-controls.js`, `ui/industrial-crt-v1.css`, `ui/snapshot-crt.css`. Regression tests: `tests/explorer-controls.test.cjs`, `tests/packaged-explorer-controls.test.cjs`.

## Verification

- Focused Node tests cover Prologue lifecycle and prompt/save isolation, action drafts/submission/recovery, map graph/zoom, snapshot routing, diagnostic export, primary buttons and packaged CSS/assets. Two regressions cover initial empty submission and stationary scanlines without tracking texture references.
- Browser checks on actual HTML/CSS/controller assets: 390×844 DPR1 and DPR3, 320×640, 320×480 and 1280×800. All five passed: reader scroll/close/one-time behavior; composer frame, bounds, empty/enabled button states and multiline draft retention; stationary scanlines in normal and reduced-motion modes; curved native snapshot and single HUD after redraw; unchanged computed GM/header/font/control styles when CRT overrides are toggled; menu save/load and map zoom/pinch/wheel. Core bridge responses were fixtures.
- Semantic and combat layout checks passed. `git diff --check` passed.
- A pre-existing uncommitted replacement of `level_00__zone_01__sub_03__goc_khuat.webp` fails the routing test's asset-size bound in the working directory. It is deliberately excluded from this commit. Focused tests must also run on the clean committed tree.

No real-device, Android keyboard, live GM API or player experience validation is claimed. Final CI APK status and artifact are recorded in PR #654.
