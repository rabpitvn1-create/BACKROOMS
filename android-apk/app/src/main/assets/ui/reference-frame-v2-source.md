# Reference frame v2

- Source: user-provided `454.png`.
- Tool: built-in ImageGen, background-extraction edit, 17 October 2026.
- Original orange Drive archive: https://drive.google.com/file/d/1-rztfeKYxS4i4nmbfXY4dWmCqJiLd22n/view
- Runtime PNG: `backrooms-reference-frame-v2.png`, 1024 × 1536 RGBA.
- Original orange Git blob: `e2696a1d310b9062c9c583a0bef3f070524a5dc7`. This identifies the original orange version before the recolor below.
- Prompt: preserve the supplied frame geometry, four mirrored interlocking square corners, stepped outline and warm orange-gold stroke; remove all white/gray checkerboard pixels to genuine alpha transparency; no redesign, simplification, text, glow, bevel or shadow.
- CSS slices: 210px vertically / 205px horizontally in source coordinates, including original outer margin. The centre is omitted.
- CSS URL is relative to each consuming stylesheet; Explorer explicitly uses `ui/backrooms-reference-frame-v2.png`.
- Validation: 14 focused Explorer/packaging tests; Chromium 153 at 430 × 932 with sample scene, controls, choice popup and Game Menu. APK/WebView not yet verified.

## Transparent margin alignment

The generated PNG starts its stroke about 52px from the top and 40px from the left. Painting the entire nine-slice inside the panel inset the gold line and exposed a background rim. CSS now uses outsets of 10px / 8px for 40px corners, scaled proportionally for compact 26px and 18px corners. The PNG is unchanged. Narrative/card padding is reduced to 32px (30px on narrow screens).

Combat backgrounds use border-box origin. The retired inset box shadow and padding-box scanline pseudo-element are disabled so no second rectangle remains inside the decorated edge.

## Antique gold recolor

- Requested hue: yellow-gold with a slight brown cast, reduced orange; ImageGen color-only edit, target #B49A54.
- Current Drive archive: https://drive.google.com/file/d/1jfLLsMDiTKD2-9Kjcx_iv6qpzyYjpW3y/view
- Current runtime filename remains `backrooms-reference-frame-v2.png` so packaging contracts stay unchanged.
- Current Git blob: `d5f8de0a5ee1088411484b97bc3f3ebe0a309eba`. Drive and GitHub use identical generated PNG bytes.
- Verified RGBA 1024 × 1536 with a transparent centre; inspected in Chromium. No change to CSS slices or outsets.
