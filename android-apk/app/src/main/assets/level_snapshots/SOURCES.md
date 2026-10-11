# Level Snapshot image sources

The APK packages these images locally so the Snapshot frame works offline and
does not request an external image at runtime. The files were retrieved from
the Escape the Backrooms Wiki CDN on 2026-08-20.

| Local asset | Wiki page | Original CDN asset |
| --- | --- | --- |
| `level_0.webp` | https://escapethebackrooms.fandom.com/wiki/Level_0 | https://static.wikia.nocookie.net/escapethebackrooms/images/3/33/Lobby.png/revision/latest |
| `level_1.webp` | https://escapethebackrooms.fandom.com/wiki/Level_1 | https://static.wikia.nocookie.net/escapethebackrooms/images/6/69/Level_1.png/revision/latest |
| `level_2.webp` | https://escapethebackrooms.fandom.com/wiki/Level_2 | https://static.wikia.nocookie.net/escapethebackrooms/images/c/cb/Level_2.jpg/revision/latest |
| `level_3.webp` | https://escapethebackrooms.fandom.com/wiki/Level_3 | https://static.wikia.nocookie.net/escapethebackrooms/images/e/ed/Level_3.png/revision/latest |
| `level_4.webp` | https://escapethebackrooms.fandom.com/wiki/Level_4 | https://static.wikia.nocookie.net/escapethebackrooms/images/2/29/Level_4.png/revision/latest |
| `level_5.webp` | https://escapethebackrooms.fandom.com/wiki/Level_5 | https://static.wikia.nocookie.net/escapethebackrooms/images/5/52/Level_5.png/revision/latest |
| `level_6.webp` | https://escapethebackrooms.fandom.com/wiki/Level_6 | https://static.wikia.nocookie.net/escapethebackrooms/images/8/88/Level_6.jpg/revision/latest |

## Project Drive asset snapshots (2026-10-11)

The 41 additional WebP files for main Levels 0–6, the five illustrated
sublevels, and ten illustrated named areas are copied byte-for-byte from
[Novel / Backrooms Level on Google Drive](https://drive.google.com/drive/folders/1QsapoDnhAH69j0EIgdVQb7jV6sKc_IZe).
The original Drive filenames are preserved. They are indexed in
`snapshot-mapping.js` using the authoritative environment node IDs.
Multiple images for one main level are selected deterministically by
location, without extra network requests. Nodes 0.1, 0.5 and 0.7 have
no dedicated photos yet and explicitly fall back to the existing
`level_0.webp` reference. Level 7+ assets are not included in this import.
