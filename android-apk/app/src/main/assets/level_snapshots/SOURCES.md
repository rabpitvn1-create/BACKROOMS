# Level Snapshot image sources

The default APK Snapshot pool accepts imagery from these three Backrooms source sites:

1. https://backrooms-wiki.wikidot.com/
2. https://backrooms.fandom.com/wiki/Backrooms_Wiki
3. http://backrooms-vn.wikidot.com/

An explicit user-provided image may override an individual Snapshot slot. Such overrides must keep their original provenance in `fandom_manifest.json`, must be normalized to 512x288 WebP, and must pass the same packaged byte-count and SHA-256 checks as the default source pool. A user override does not expand the automatic source crawler to arbitrary sites.

Every campaign Level, sublevel, and special area receives exactly four 512x288 WebP Snapshot backgrounds. The builder re-downloads imagery referenced by approved source pages and performs only crop, resize, and WebP encoding. It does not AI-generate environment imagery.

Media bytes may be delivered by infrastructure used by those sites, such as `static.wikia.nocookie.net` or `*.wdfiles.com`; the manifest always retains the approved Backrooms page as provenance. Explicit user overrides retain the user-supplied source URL instead.

If a sub-area has no usable approved-source image, it uses its parent main Level source pool and records `parent_source_fallback`. A mixed area may retain parent fallback slots alongside explicit user overrides. Old Fandom snapshots, the rejected Pixel16 asset/manifest, and legacy Escape the Backrooms fallback images are removed only after the complete replacement set verifies successfully.
