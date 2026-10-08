# Level 0–13 content coverage and completion gates

Status: **67/67 Level/Sublevel/khu phụ có mô tả môi trường dựng cảnh trong BACKROOMS_WORLD.md (bao gồm nguồn cũ và nguồn cộng đồng có nhãn); gameplay rollout vẫn chưa hoàn tất**.

## Source inventory

- Core registers 14 full Level nodes (0–13) and 36 numeric Sub-level nodes with immutable ranks.
- `WorldContentCatalog` supplies source-labelled titles for those 50 nodes and 17 additional named sections.
- `BACKROOMS_WORLD.md` supplies the authoritative Project environment baselines for Level 0–10 (including the Level 6 permanent-dark tundra override) and external *candidate* environment summaries for Level 11–13.
- **Scene baseline coverage: 67/67** keys resolved against the existing `WorldContentCatalog`: 50 registered Level/Sublevel nodes and 17 unranked named sections. Each has a unique `<!-- scene-key:... -->` marker and a nonempty prose scene in the **same** world canon file. Validated during `build-novel-asset-canon-index.py --check`, not tracked in a parallel canon file.
- User allows provisional prose from outdated / rewrite Wikidot pages and an unofficial Fandom EX-1 narrative for LS-2. They are explicitly labelled **source candidate/unstable**, never verified gameplay facts. Current count of locations with *no* environmental scene: **0**. This does not certify source completeness, playable routes or assets.
- Project overrides prevail: Level 0.1 **Deep Emptiness** and Level 0.7 **Claustrophobia**; wiki names are not permitted to silently replace them.
- Level 4, 12 and 13 have no numbered Sub-level listed by the selected Wikidot source; do not invent one merely to fill the list.
- An `OPEN`/trimmed/rewrite location now **does have scene-setting prose where a short/older/community source is available**, but it remains **OPEN for mechanics and unverified environmental details**. Named areas without fixed ranks do not receive synthetic progression nodes.
- Canon source: `android-apk/app/src/main/assets/knowledge/novel_asset/BACKROOMS_WORLD.md`.
- Internet references are normalized **directly inside this canonical asset** (individual source URLs/credits by section in 2C.1, previous citations in 2D); packaged `knowledge_db.json` is a deterministic derived index, not a second authority. No staging registry was created.
- External list: https://backrooms-wiki.wikidot.com/normal-levels-i

## Runtime safety invariant

A known title/node is **not** a playable/connected Level by itself.

- The legacy exit graph still has only 42 routes for full Level 0–6, and this is intentional.
- `LinearWorldRouteResolver` still configures a Level 0–6 exit boundary. Adding names must not bypass the route gate.
- The AI/narrative layer may read canon but must never authoritative-write progression rank, exit route, Entity stats, encounter, drops or player location.
- Do not add auto-connect logic based on Level numbers, sublevel suffixes, catalog order or a description.
- Existing save data and active combat must not be silently remapped or rescaled.
- The local snapshot asset pool currently covers Level 0–6 only; no Level 7–13 image should be claimed as available.

## Follow-on requirements before marking Level 7–13 playable

1. Review and explicitly approve Core edges and exit discovery resolution for each target Level/Sub-level using Project canon; check entry and exit conditions and preserve legacy routes.
2. Wire authoritative world-node transitions to state persistence/migration and encounter scaling; test unknown-node failures, combat-in-progress preservation and no Gemini write-through.
3. Add Level-specific hazard/loot/Entity tables only from approved canon and preserve EntityCore encounter authority.
4. Produce and verify appropriate local WebP snapshots and UI fallbacks. Do not disguise Level 0 visuals as a new Level.
5. Add playable traversal, save/load, location display and content-retrieval tests per Level and Sub-level.
6. Run the exact production patch chain, Android JVM tests and APK verification; wait for required GitHub CI GREEN before squash-merging each reviewed increment.

The backlog above is explicit: passing a catalog/index test does **not** certify a fully playable expansion.
