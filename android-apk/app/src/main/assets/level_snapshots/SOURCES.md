# Level Snapshot image sources

The APK accepts Snapshot imagery only from these three Backrooms source sites:

1. https://backrooms-wiki.wikidot.com/
2. https://backrooms.fandom.com/wiki/Backrooms_Wiki
3. http://backrooms-vn.wikidot.com/

Every campaign Level, sublevel, and special area receives exactly four 512x288 WebP Snapshot backgrounds. The builder re-downloads imagery referenced by approved source pages and performs only crop, resize, and WebP encoding. It does not AI-generate environment imagery.

Media bytes may be delivered by infrastructure used by those sites, such as `static.wikia.nocookie.net` or `*.wdfiles.com`; the manifest always retains the approved Backrooms page as provenance.

If a sub-area has no usable approved-source image, it uses its parent main Level source pool and records `parent_source_fallback`. Old Fandom snapshots, the rejected Pixel16 asset/manifest, and legacy Escape the Backrooms fallback images are removed only after the complete replacement set verifies successfully.
