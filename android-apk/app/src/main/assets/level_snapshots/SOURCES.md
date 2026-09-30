# Level Snapshot image sources

The APK packages the user-provided Google Drive snapshot set locally so Snapshot fallback images work offline.

Source folder:
https://drive.google.com/drive/folders/1QsapoDnhAH69j0EIgdVQb7jV6sKc_IZe

Imported: 2026-09-18; Level 0 expansion: 2026-09-27

Runtime policy:
- Levels 1 through 6 use the original three local WebP snapshots; Level 0 is being expanded one image at a time.
- Original imported snapshots are 351 × 250 px; added Level 0 backgrounds keep their source-native dimensions and are rendered with `object-fit: cover`.
- Turn-based selection rotates through every manifest asset for the current Level.
- All previous Escape the Backrooms / Wikidot snapshot image files were removed from the APK.
- Gemini-generated per-turn snapshots remain a separate runtime path and are not part of this local fallback set.

| Level | Slot | Drive file | SHA-256 |
| ---: | ---: | --- | --- |
| 0 | 1 | level_00_snapshot_001.webp | `90a6dbb84d5851244df7c67a747fe0b3bb30d3a1a3dd7c672a5a9d3648e729e7` |
| 0 | 2 | level_00_snapshot_002.webp | `8e018d014b75005873251c35fc5690c7b3f735367d8572de776a572b3650fb6c` |
| 0 | 3 | level_00_snapshot_003.webp | `404eb9fccbe6f786dc6c2489ce28e54572d6442b906d2eb0c7ff96ce15fb7e04` |
| 0 | 4 | level_00_snapshot_004.webp | `f2aa5978a104c2d33ef87a00b24fe1dbf62fda3b36e47ba25f8e2952b0656c54` |
| 0 | 5 | level_00_snapshot_005.webp | `44fca432075659edeabbc21d721416eb584d56fce86737d53b71cecda5792fb3` |
| 0 | 6 | level_00_snapshot_006.webp | `86aeff71c4a884933e20bfbea3f20da5034b618e2feee1a8a683a02143d36877` |
| 0 | 7 | level_00_snapshot_007.webp | `6539bbaafb6ffa6bb9ace63d36e3b710623222c4769489b2498ba77f5ebb1219` |
| 0 | 8 | level_00_liminal_hall.webp | `cddb26e24621b5065050f181b5a7f580407649ba33ff39cd6f257368cc140918` |
| 1 | 1 | level_01_snapshot_001.webp | `72d8ffe6e70cf95d8872692bdeccf69811a11df5fd8894251afee80258283ae6` |
| 1 | 2 | level_01_snapshot_002.webp | `3de86a8c83038b6b37adc861959068e571b6c07712be46a8eca0494595ea33b1` |
| 1 | 3 | level_01_snapshot_003.webp | `3a7cbab5918e358ccbe1b30269f16137cbb2be9674397f9d5f5421dfc856911c` |
| 2 | 1 | level_02_snapshot_001.webp | `cd57035944e38a36cc6445a5781e727a861443b79edf0573ba74851deee1021a` |
| 2 | 2 | level_02_snapshot_002.webp | `6f17d73ed36b976408945209af4367b38fd64a688e368674c134f027133ed504` |
| 2 | 3 | level_02_snapshot_003.webp | `44c9fe754a9a30554b8d7bd0251d9dc3517c01d1adeef089e537ba9cf6213f7d` |
| 3 | 1 | level_03_snapshot_001.webp | `c6d88327ec63e5520537b9c4857304503564446c2dd13183c5ed297fc957f58f` |
| 3 | 2 | level_03_snapshot_002.webp | `ce1d505c3713ff56992f2c5a93819f83555e5f4d80c88029ca345aee55489b7f` |
| 3 | 3 | level_03_snapshot_003.webp | `3981f88b24201784b56816d9b431a20f884756fe206c01859e0eaf2bd9ea4cbf` |
| 4 | 1 | level_04_snapshot_001.webp | `7f93e14df9d33812d0dc9b40255fd5ee6471ec0b2f6be93e684de430dcad2dc0` |
| 4 | 2 | level_04_snapshot_002.webp | `c31ce7e80bbd4d07fd1df39ebfda43bd1f0c5e1e335ea88fe41bfbb6ea7e35b6` |
| 4 | 3 | level_04_snapshot_003.webp | `892817bd4bd9dad095a8a0d7978d0342c249171a725d00073c04c764a0452ff3` |
| 5 | 1 | level_05_snapshot_001.webp | `76dae35c2ef2f99ccc519504146856471091ba4512545ae8088c78f601aa52ae` |
| 5 | 2 | level_05_snapshot_002.webp | `65f09a9e4256d4dd368201fdcbbbe6438d6487209645369637e19e5e5d132e3a` |
| 5 | 3 | level_05_snapshot_003.webp | `e670ab2ab0f18fe9f97f40162e04b8ce2ae8503aa99878c1a8501d2c93cdcdb0` |
| 6 | 1 | level_06_snapshot_001.webp | `8666070ac10a2ca4f7adb922fcac887a58b8c3d5d492f7a4f13bf932de54c78b` |
| 6 | 2 | level_06_snapshot_002.webp | `502dcf6964b863320afac376cf8e1db009efa8c11d5aa182d45d4c7c63d25d17` |
| 6 | 3 | level_06_snapshot_003.webp | `6e31d8b54c9d30c1552ac5b6f7edbe2a7ed6fc8d10be5c4f0a438e33a0303e10` |
