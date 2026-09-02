# Level Snapshot image sources

The default APK Snapshot pool accepts imagery from these three Backrooms source sites:

1. https://backrooms-wiki.wikidot.com/
2. https://backrooms.fandom.com/wiki/Backrooms_Wiki
3. http://backrooms-vn.wikidot.com/

An explicit user-provided image may override an individual Snapshot slot. When the user explicitly requires original quality, the override is stored byte-for-byte at its source resolution with no crop, resize, recompression, or re-encoding. Raw overrides must pass exact packaged byte-count and SHA-256 checks. A user override does not expand the automatic source crawler to arbitrary sites.

The default approved-source Snapshot pool uses four 512x288 WebP backgrounds per campaign Level, sublevel, and special area. Explicit raw overrides may retain their source dimensions when original quality is requested. The builder does not AI-generate approved-source environment imagery.

Media bytes may be delivered by infrastructure used by those sites, such as `static.wikia.nocookie.net` or `*.wdfiles.com`; the manifest always retains the approved Backrooms page as provenance. Explicit user overrides retain the user-supplied source URL instead.

If a sub-area has no usable approved-source image, it uses its parent main Level source pool and records `parent_source_fallback`. A mixed area may retain parent fallback slots alongside explicit user overrides. Old Fandom snapshots, the rejected Pixel16 asset/manifest, and legacy Escape the Backrooms fallback images are removed only after the complete replacement set verifies successfully.

## Level 0 original-quality override

Level 0 uses `level_0_1.webp` through `level_0_4.webp` copied byte-for-byte from the project Google Drive folder `Backrooms Level`. All four are 1672x941 WebP originals. Their exact sizes and SHA-256 values are hard-locked in `patch-level-snapshot-backgrounds.py`. The former `area_00_0_trusted_01.webp` through `area_00_0_trusted_04.webp` assets are removed after replacement.

`fandom_manifest.json` still preserves the historical Level 0 source metadata for provenance, but runtime loading deliberately bypasses those legacy Level 0 file records. `ORIGINAL_QUALITY_OVERRIDES["0"]` is the authoritative packaged Level 0 pool.

## Epsilon original-quality override

Area `epsilon` (`Incessant Hum-Buzz`) uses `level_epsilon_1.webp` through `level_epsilon_4.webp` copied byte-for-byte from the project Google Drive folder `Backrooms Level`. All four are 1672x941 WebP originals. Their exact sizes and SHA-256 values are hard-locked in `patch-level-snapshot-backgrounds.py`. The former `area_01_epsilon_trusted_01.webp` through `area_01_epsilon_trusted_04.webp` parent-fallback assets are removed after replacement.
