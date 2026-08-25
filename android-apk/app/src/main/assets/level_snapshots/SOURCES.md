# Level Snapshot image sources

## Active source: Backrooms Wiki | Fandom

The Android patch chain now builds a local rotating image pool for Levels 0–6 from:

- https://backrooms.fandom.com/wiki/Backrooms_Wiki
- the matching `Level_0` through `Level_6` pages on that wiki.

`android-apk/prepare-fandom-level-snapshots.py` queries the Fandom MediaWiki API during the Debug build, selects multiple large non-UI images from each matching Level page, downloads them into this directory, and writes `fandom_manifest.json` with the original file title, description URL, download URL, dimensions, SHA-256, and any attribution/license metadata exposed by Fandom.

The generated files use names such as `level_0_fandom_01.jpg`. At least two images are required for every Level and up to six are packaged per Level. Runtime does not request Fandom: all selected images are inside the APK, and the Snapshot renderer rotates among the current Level's pool in five-minute buckets.

Images are never mixed between Level pages. Project canon still comes from the repository's own World/Level sources; Fandom is only the visual source for these Snapshot backgrounds.

## Legacy single-image fallbacks

The older `level_0.webp` through `level_6.webp` assets remain in the repository for compatibility with historical builds. They came from Escape the Backrooms Wiki and are no longer the active rotating Snapshot source.

| Legacy asset | Historical page | Historical CDN asset |
| --- | --- | --- |
| `level_0.webp` | https://escapethebackrooms.fandom.com/wiki/Level_0 | https://static.wikia.nocookie.net/escapethebackrooms/images/3/33/Lobby.png/revision/latest |
| `level_1.webp` | https://escapethebackrooms.fandom.com/wiki/Level_1 | https://static.wikia.nocookie.net/escapethebackrooms/images/6/69/Level_1.png/revision/latest |
| `level_2.webp` | https://escapethebackrooms.fandom.com/wiki/Level_2 | https://static.wikia.nocookie.net/escapethebackrooms/images/c/cb/Level_2.jpg/revision/latest |
| `level_3.webp` | https://escapethebackrooms.fandom.com/wiki/Level_3 | https://static.wikia.nocookie.net/escapethebackrooms/images/e/ed/Level_3.png/revision/latest |
| `level_4.webp` | https://escapethebackrooms.fandom.com/wiki/Level_4 | https://static.wikia.nocookie.net/escapethebackrooms/images/2/29/Level_4.png/revision/latest |
| `level_5.webp` | https://escapethebackrooms.fandom.com/wiki/Level_5 | https://static.wikia.nocookie.net/escapethebackrooms/images/5/52/Level_5.png/revision/latest |
| `level_6.webp` | https://escapethebackrooms.fandom.com/wiki/Level_6 | https://static.wikia.nocookie.net/escapethebackrooms/images/8/88/Level_6.jpg/revision/latest |
