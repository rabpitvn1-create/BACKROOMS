# Level 0–13: content coverage and deletion audit

Updated 2026-10-09. **39/39 currently registered world scene keys have canon environment prose** in `BACKROOMS_WORLD.md`: 14 main Levels, 8 approved numbered Sub-levels, and 17 named areas. **Only 24 locations have playable streak exits** (14 main Levels, 8 Sub-levels, Red Rooms and Base Alpha).

## Approved scope and removed sources

- Exactly **22 ranked Core nodes** remain, with their existing literal progression ranks unchanged: 14 main Levels and 8 Sub-levels (`0.2`, `1.2`, `1.5`, `5.1`, `6.1`, `7.7`, `10.1`, `11.3`).
- **28 rejected numbered Sub-levels** were removed from the gameplay registry, catalogue, editorial journey, their individual WebP snapshot files, their `BACKROOMS_WORLD.md` scene sections and the generated `NOVEL_ASSET.BACKROOMS_WORLD` knowledge index. Removed topic aliases were also retired from the asset manifest. Their historical versions remain recoverable in Git.
- All **17 named areas** remain in the metadata catalogue and world scenes. **15 have no gameplay exit**, while Red Rooms and Base Alpha retain their validated featured itinerary positions. Named areas do not receive synthetic ranks.
- Project world canon for all 14 main Levels and the Level 6 permanently dark tundra override remain intact. Character/Entity canon and their images are unchanged.
- The snapshot build maps **all 22 ranked locations and all 17 named areas** to their dedicated source images. The main-Level image pools may have multiple frames. Level 7–13 now have source WebPs; never substitute an image from another level.

## Authority and gameplay rules

- `WorldProgressionCore` retains 42 legacy Level 0–6 edges and adds 7 approved main-Level edges plus 15 deliberately declared featured Sub-level edges: **64 total**. No edge is generated from the catalogue or wiki naming.
- `FeaturedJourneyRoutes` pins 24 ordered playable stops, with Red Rooms following Level 0.2 and Base Alpha following Level 1. Gameplay uses 5 consecutive successful independent 50/50 ordinary turns; combat does not roll or reset exit streak.
- The Android Core validates every route against the declared edges and saves trusted `worldNodeId`, `journeyStopKey` and streak state; candidate AI content never authorizes new progress or nodes.
- `WorldJourneyOrder` retains 39 editorial keys, but 15 unselected named-area keys are **not playable routes**. This metadata remains only because the request removes insufficient numbered Sub-levels, not every named area.
- The canon-index checker demands one substantial scene for every active catalog key, no extra keys, and matching curated source excerpts. The snapshot asset checker demands one-to-one filename mapping and valid WebP bytes.

## Outstanding content work

Passing JVM tests, patch-chain guards and APK build establishes packaging and contract consistency, **not** live device playtest. Scene detail, balancing and distinctive assets may still require further review before any remaining named areas become playable.

Canonical source: `android-apk/app/src/main/assets/knowledge/novel_asset/BACKROOMS_WORLD.md`. Generated source index: `android-apk/app/src/main/assets/knowledge/knowledge_db.json`.
