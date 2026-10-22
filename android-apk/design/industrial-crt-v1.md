# Industrial CRT UI v1 — 19 October 2026

Baseline: PR #654, `186398a33213610aac34c4d0293a2461d6d27338`.

User references: `570.png` (GAME MENU), `569.png` (gameplay). The five material assets were generated with **built-in Image Gen**, not CSS substitutes. [Drive archive: BACKROOMS_UI_Industrial_CRT_v1_20261019](https://drive.google.com/drive/folders/1H13I34yN6FfIN4qdXpoN3j2InSmjZT60) contains the untouched source PNGs and the optimized WebPs used in Git. Individual source/runtime URLs, exact dimensions, packaging crop bounds and SHA-256 hashes are in [the asset manifest](industrial-crt-v1-assets.json).

## Implementation

- Runtime assets live in `app/src/main/assets/ui/industrial-crt-v1/`. Total: **236,758 bytes** (about 237 KB). Only alpha-margin trimming, downsampling and WebP compression were applied after generation; the source artwork is preserved on Drive. Frame and button alpha is retained; the frame center is genuinely transparent. Runtime PNGs do not carry any baked text, status, NPC or scenery.
- `ui/industrial-crt-v1.css` adds a body-scoped theme after existing UI styles. Nine-slice borders retain corner screws/bevels as panels grow; textures are reused rather than stretching whole screenshots. Frame slice 80, button slice 70; visual frame widths 12–16 CSS px. The decorative viewfinder has no invented recording, battery, code or time telemetry. No heavy scanline layer is placed over the prose.
- Snapshot remains the existing native renderer with environment-specific scenery and live character/entity overlays. It reserves roughly 34% of portrait viewport height; the log scrolls independently and the Explorer controls remain at the bottom. Landscape uses a smaller snapshot. Generated decorations never receive pointer events.
- GAME MENU uses a paper survival dossier, readable CRT panels, metal buttons, red destructive actions with hazard borders and the existing party/diagnostics data. Added a live mirror of the existing status text inside the menu: confirmations/errors from Save/Load/New Game/delete were otherwise clipped in the hidden main status element. This changes presentation only; it does not approve or trigger any action.
- MAP retains its graph, opaque atlas identifiers, locked-node privacy, gold pin, pan/pinch/wheel/button zoom and default recenter. PROLOGUE keeps its rounded one-time offer and full reader. The 6,887-character GM history, provider logic, Core, save schema, equipment, combat commands, spawn rules and Canon are unchanged.
- The existing APK contract checker now compares the theme stylesheet and all five WebPs byte-for-byte with source. Both existing APK-extraction lists include them. No new dependency, background API request or Android Emulator job.

## Verification

Focused JavaScript gate includes Explorer/map/zoom, prologue, snapshot routing, diagnostics, primary controls and packaging. New packaging regressions reject a missing generated button and a stale theme. A menu-controller regression verifies visible confirmation/error mirroring and clearing without altering open/close or action handlers.

Browser QA uses the shipped HTML/CSS/JS and the decoded `installUiEnhancements()` JavaScript from MainActivity, with file URLs adapted only for the local HTTP test server. The existing environment resolver and actual Cao Minh PNG are used; the graph and narrative are explicit fixtures. Save/Load bridge calls are test doubles. It checks 390×844, 320×640 and 1280×800 layouts, actual input-driven pinch/wheel/buttons and node highlighting, 45 nodes/eight groups, the one-time Prologue lifecycle, viewport-contained Player Action, menu scrolling, Save/Load messages and no Core reset after the first destructive click. Screenshots of gameplay, menu and party were inspected; no page exceptions.

This is browser verification, **not an Android device or live GM/API playtest**. CI build and focused Core gate results, commit SHA and APK artifact are recorded on the PR after the one final build. No merge/release/Canon edits.

## Image Gen prompts

The first three calls use both user images as references and request true alpha transparency. The last two are new opaque textures. Generated output sizes may differ from the requested canvas; the manifest records actual runtime dimensions.

### panel-frame

Extract/reconstruct ONE isolated industrial rectangular metal panel frame from these reference UI screenshots, not the whole UI. Asset for a real game nine-slice border. Square 1024x1024 canvas, orthographic front view. Frame outer bounds almost flush to image edges (less than 8px transparent margin), metal band about 64px wide on all four sides, rounded outer corners radius24px. Entire enormous central opening is REAL alpha transparency, also transparent outside rounded outer corners. Match brushed oxidized steel, scattered rusty brown corrosion, recessed double bevel, corner screw heads, a few screws along straight rails, a small worn yellow/black diagonal hazard-tape patch on the lower right rail only. No cables, no hanging objects, no extra widgets, no environment, no lettering, no characters, no opaque/checkerboard fill inside. All ornament confined within the 64px frame band. Symmetrical usable geometry for scaling to wide or tall panels; restrained horror salvage equipment style like both references. This is an extraction/reconstruction of the frame component, no UI screenshot.

### button-steel

Extract/reconstruct ONE blank metal button skin matching the steel MAP / Area Log / Save buttons in the reference screenshots. Isolated horizontal rounded rectangular plaque, 1536x512 wide canvas. Orthographic front view, rounded corners radius55px, dark outer raised bevel, 40px solid outer band, aged warm grey brushed steel face, rusty brown edges, four recessed screw heads each 60px from corners. Large central 75% of face is flat quiet mid-dark desaturated warm grey metal for high contrast cream dynamic text added in code. Minimal scratches in center; dirt concentrated on edges. Actual alpha transparency only outside the button silhouette, no visible drop shadow more than 6px. No letters, labels, symbols, icon, watermark, scene, multiple buttons or UI layout. Keep button nearly flush to canvas edges, symmetric, sober tactile hardware from the references. Production UI background usable stretched horizontally.

### button-hazard

Extract/reconstruct ONE blank yellow industrial primary-action button skin from reference screenshots, especially Player Action. Isolated wide horizontal rounded rectangular plaque, 1536x512 canvas. Orthographic front view, thick dark beveled outer steel rim, worn diagonal black/yellow hazard tape confined to top/bottom/side border bands roughly 45px thick, muted amber ochre yellow enamel center, scraped chipped paint and rust concentrated around edges, center 75% calm and uniform to take dark dynamic text in code. Corners radius50px, realistic salvaged analog terminal hardware. Match reference palette and wear, legible warm gold center. Actual transparent alpha outside silhouette, almost flush to canvas. NO text, star, icons, characters, multiple components, background or watermark.

### surface-crt

Use case: stylized-concept. Production game UI background texture, square 1024x1024. Flat front-on close-up of very dark olive-black aged CRT/phosphor monitor glass, almost black charcoal #111715 with muted olive warmth #242519, very fine faint horizontal raster/scan lines, subtle analog grain, extremely restrained mottled grime at edges and soft reflection only near top edge. Center must remain calm, uniform and dark behind long cream-colored Vietnamese prose. Edge-to-edge full canvas, no bezel/frame, screws, text, letters, symbols, light bars, buttons, cables, vignette shapes, recognizable objects or static TV noise. Subtle texture not a screen illustration, no visible bloom or distracting patterns. Matches salvaged steel rusty Backrooms horror terminal with yellow/brown palette. Opaque full background.

### paper-dossier

Use case: stylized-concept. One production game UI texture background: aged cream-brown manila dossier paper, square1024x1024, flat front-on with fine fibrous paper grain, faint yellow water stains concentrated in corners, slightly darker worn edges, very subtle horizontal ruled lines spaced far apart. Center 80% clean and pale parchment so dark dynamic Vietnamese labels can be rendered in code. Full canvas filled with paper. No lettering, numbers, logos, tabs, folded corners, holes, frames, icons, stamps, shadows, objects or binding. Quiet realistic paper used in salvaged industrial Backrooms control terminal. Opaque full background.
