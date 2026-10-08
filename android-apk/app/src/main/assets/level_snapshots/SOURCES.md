# Level Snapshot image sources

The APK packages these images locally so Snapshot works offline and never depends on Google Drive at runtime.

Source: project Google Drive folder `Novel / Backrooms Level`, imported byte-for-byte on 2026-10-08.

The first snapshot for each Level keeps the historical APK filename because other UI code already references it:

- `level_0.webp` <- `level_00_snapshot_001.webp`
- `level_1.webp` <- `level_01_snapshot_001.webp`
- `level_2.webp` <- `level_02_snapshot_001.webp`
- `level_3.webp` <- `level_03_snapshot_001.webp`
- `level_4.webp` <- `level_04_snapshot_001.webp`
- `level_5.webp` <- `level_05_snapshot_001.webp`
- `level_6.webp` <- `level_06_snapshot_001.webp`

Additional Drive frames retain their original names. Level 0 also includes `level_00_liminal_hall.webp`.

`patch-level-snapshot-backgrounds.py` selects a deterministic local frame from the current Level's pool using Level progress, with the global turn as a fallback. AI-generated scene snapshots can still replace the local fallback when a valid cached scene image exists.

Only these eight numbered Sub-levels retain dedicated local snapshot files:
`level-0.2`, `level-1.2`, `level-1.5`, `level-5.1`, `level-6.1`, `level-7.7`, `level-10.1`, and `level-11.3`.
The 28 retired numbered Sub-level WebPs were deleted alongside their active canon scenes and registry entries. All 14 main-Level images and 17 named-area images remain. The build verifies every retained image maps to exactly one current catalogue entry; no history was rewritten.
