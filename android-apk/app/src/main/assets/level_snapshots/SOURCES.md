# Level Snapshot image sources

## Active source: Backrooms Wiki | Fandom

The Android patch chain builds local Snapshot pools for the canonical 43-area Level 0–6 campaign route from:

- https://backrooms.fandom.com/wiki/Backrooms_Wiki
- the matching Fandom page resolved for each main Level, sublevel, and special route area defined by `android-apk/patch-linear-sublevel-progression.py`.

`android-apk/prepare-fandom-level-snapshots.py` reads the campaign `ROUTE` literal without executing that patch, resolves each area's Fandom page, filters out badges/UI images, downloads up to three usable images per area, and writes `fandom_manifest.json`. The manifest records the route index, parent Level, area ID/name/type, resolved page, image source metadata, SHA-256, attribution/license fields exposed by Fandom, and a `missing_areas` report.

Generated files use names such as `area_02_0_01_fandom_01.jpg`. Runtime never requests Fandom: selected images are packaged inside the APK. Snapshot chooses the current `flags.exploration.areaId` pool and rotates it in five-minute buckets. Main Levels require at least two packaged images so they can act as a safe fallback. If a non-main route area has no resolvable page or no usable image, only that area falls back to its own parent main Level pool; it never borrows another sublevel's images.

This keeps visual selection aligned with the deterministic 43-area route while preserving old saves that only know the parent Level. Project canon still comes from repository World/Level sources; Fandom is only the visual source for Snapshot backgrounds.

## Legacy single-image fallbacks

The older `level_0.webp` through `level_6.webp` assets remain in the repository for compatibility with historical builds. They came from Escape the Backrooms Wiki and are no longer the active rotating Snapshot source unless both generated Fandom pools and their parent fallback are unavailable.

| Legacy asset | Historical page | Historical CDN asset |
| --- | --- | --- |
| `level_0.webp` | https://escapethebackrooms.fandom.com/wiki/Level_0 | https://static.wikia.nocookie.net/escapethebackrooms/images/3/33/Lobby.png/revision/latest |
| `level_1.webp` | https://escapethebackrooms.fandom.com/wiki/Level_1 | https://static.wikia.nocookie.net/escapethebackrooms/images/6/69/Level_1.png/revision/latest |
| `level_2.webp` | https://escapethebackrooms.fandom.com/wiki/Level_2 | https://static.wikia.nocookie.net/escapethebackrooms/images/c/cb/Level_2.jpg/revision/latest |
| `level_3.webp` | https://escapethebackrooms.fandom.com/wiki/Level_3 | https://static.wikia.nocookie.net/escapethebackrooms/images/e/ed/Level_3.png/revision/latest |
| `level_4.webp` | https://escapethebackrooms.fandom.com/wiki/Level_4 | https://static.wikia.nocookie.net/escapethebackrooms/images/2/29/Level_4.png/revision/latest |
| `level_5.webp` | https://escapethebackrooms.fandom.com/wiki/Level_5 | https://static.wikia.nocookie.net/escapethebackrooms/images/5/52/Level_5.png/revision/latest |
| `level_6.webp` | https://escapethebackrooms.fandom.com/wiki/Level_6 | https://static.wikia.nocookie.net/escapethebackrooms/images/8/88/Level_6.jpg/revision/latest |
