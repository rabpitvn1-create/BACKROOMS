# Level Snapshot image sources

The APK packages Level Snapshot backgrounds locally so the Snapshot frame works offline and does not request an external image at runtime.

## Level 0 project snapshots

Level 0 uses the four user-provided 16-bit pixel backgrounds from the project Google Drive folder. `fetch-level0-drive-snapshots.py` downloads the exact PNGs during snapshot patch preparation, validates that each image is 768×448, and checks its locked SHA-256 before Android assets are packaged. The runtime rotates these four local images every three turns when no generated Gemini snapshot is available.

Source folder: `https://drive.google.com/drive/folders/1QsapoDnhAH69j0EIgdVQb7jV6sKc_IZe`

| Local asset | Google Drive file ID | SHA-256 |
| --- | --- | --- |
| `rotation/backrooms_level0_01_open_room_16bit.png` | `1zlwYYW1z4mOXT0d-ce-JZBLsB65vnvcD` | `d0a9d9fe641986b2c2121c89c3ae713b13f288160c8d6774aac8935099feba25` |
| `rotation/backrooms_level0_02_long_corridor_16bit.png` | `1Zd03AVNu4URBUG_FEi_D69yhCtJ_7-XU` | `cab2626d6c07a62b971cfa80a1d42ff96d3a3fad6422eea494f1b687eb1b6eb5` |
| `rotation/backrooms_level0_03_maze_junction_16bit.png` | `1M-r678UuEqU5w-24EAOOELU_Hb9th_vl` | `546abd384dc664ff0e43fad72c1c4021653b0d7463c0f72064c1eb964e3217ef` |
| `rotation/backrooms_level0_04_ceiling_corner_16bit.png` | `1R5S_ec0lm0TdOK0PoUA59B8dXzM3sTlq` | `6d1ca532d13254b86e76d4e59651154f0187f5aa91c63855cca6e6a84093663c` |

`level_0.webp` remains packaged as a last-resort legacy fallback if a Level 0 rotation asset cannot be decoded at runtime.

## Level 1–6 legacy fallbacks

These packaged fallbacks were retrieved from the Escape the Backrooms Wiki CDN on 2026-08-20. They are used only when a generated Gemini snapshot is not available.

| Local asset | Wiki page | Original CDN asset |
| --- | --- | --- |
| `level_1.webp` | https://escapethebackrooms.fandom.com/wiki/Level_1 | https://static.wikia.nocookie.net/escapethebackrooms/images/6/69/Level_1.png/revision/latest |
| `level_2.webp` | https://escapethebackrooms.fandom.com/wiki/Level_2 | https://static.wikia.nocookie.net/escapethebackrooms/images/c/cb/Level_2.jpg/revision/latest |
| `level_3.webp` | https://escapethebackrooms.fandom.com/wiki/Level_3 | https://static.wikia.nocookie.net/escapethebackrooms/images/e/ed/Level_3.png/revision/latest |
| `level_4.webp` | https://escapethebackrooms.fandom.com/wiki/Level_4 | https://static.wikia.nocookie.net/escapethebackrooms/images/2/29/Level_4.png/revision/latest |
| `level_5.webp` | https://escapethebackrooms.fandom.com/wiki/Level_5 | https://static.wikia.nocookie.net/escapethebackrooms/images/5/52/Level_5.png/revision/latest |
| `level_6.webp` | https://escapethebackrooms.fandom.com/wiki/Level_6 | https://static.wikia.nocookie.net/escapethebackrooms/images/8/88/Level_6.jpg/revision/latest |
