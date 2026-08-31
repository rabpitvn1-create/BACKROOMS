# Level Snapshot image sources

Only these three source sites are approved for packaged Backrooms Snapshot imagery:

1. https://backrooms-wiki.wikidot.com/
2. https://backrooms.fandom.com/wiki/Backrooms_Wiki
3. http://backrooms-vn.wikidot.com/

`prepare-trusted-level-snapshots.py` resolves the canonical 43-area Level 0–6 route, gathers images exposed by those pages, and produces exactly four 512x288 WebP snapshots for every Level/sublevel/special area. It performs only crop, resize, and WebP encoding; it never AI-generates scene content.

Wikidot and Fandom may serve attachment bytes from their own media/CDN hosts (for example `*.wdfiles.com` or `static.wikia.nocookie.net`). Those hosts are treated only as delivery infrastructure: every manifest record must retain an approved source-page URL from one of the three sites above.

If an area has fewer than four distinct source images, deterministic crops of the available approved-source images fill the remaining slots. If none of the three sites exposes a usable image for a sub-area, the generator may use its parent main Level source pool and records that explicitly as `parent_source_fallback`; it never searches any fourth website.

The generated files are committed into the APK so runtime remains offline. `fandom_manifest.json` keeps its historical filename only for renderer compatibility; its `source` and provenance fields are authoritative.
