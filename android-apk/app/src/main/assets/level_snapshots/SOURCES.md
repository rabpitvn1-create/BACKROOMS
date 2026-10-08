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
