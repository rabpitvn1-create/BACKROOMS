# Level Snapshot image sources

The release build fetches 28 source images into `level_snapshots/rotation/`: four images for each Level 0–6. The Snapshot renderer changes image deterministically every three completed turns:

- turns 1–3 → slot 1
- turns 4–6 → slot 2
- turns 7–9 → slot 3
- turns 10–12 → slot 4
- turn 13 repeats slot 1

The source manifest is `android-apk/snapshot_sources.json`. `android-apk/fetch-level-snapshots.py` verifies that all 28 downloads are supported image files and rejects duplicate bytes before the APK is built. The selected images are environment shots without a site logo/watermark in the image content; site UI and page chrome are never captured.

## Level 0 — The Lobby

| Slot | Image | Author | License | Source |
| --- | --- | --- | --- | --- |
| 1 | Backrooms / original Level 0 photo | Bob Mazza | CC0 1.0 | https://backrooms-wiki.wikidot.com/level-0 |
| 2 | Arches | Bob Mazza | CC0 1.0 | https://backrooms-wiki.wikidot.com/level-0 |
| 3 | Render “3” | Alfarex | CC BY-SA 4.0 | https://backrooms-wiki.wikidot.com/level-0 |
| 4 | Render “5” / blackout variation | Alfarex | CC BY-SA 4.0 | https://backrooms-wiki.wikidot.com/level-0 |

## Level 1 — Parking Zone / Habitable Zone

| Slot | Image | Author | License | Source |
| --- | --- | --- | --- | --- |
| 1 | 003 | SunCon Photos | CC BY 2.0 | https://backrooms-wiki.wikidot.com/level-1 |
| 2 | TQM Site Walk at Putra Place 057 | SunCon Photos | CC BY 2.0 | https://backrooms-wiki.wikidot.com/level-1 |
| 3 | 2015-01-12 TQM Site Walk Putra Place | SunCon Photos | CC BY 2.0 | https://backrooms-wiki.wikidot.com/level-1 |
| 4 | 2014-12-11 TQM Site Walk Putra Place | SunCon Photos | CC BY 2.0 | https://backrooms-wiki.wikidot.com/level-1 |

## Level 2 — Pipe Dreams / Abandoned Utility Halls

| Slot | Image | Author | License | Source |
| --- | --- | --- | --- | --- |
| 1 | Northbound Tunnel | taberandrew | CC BY 2.0 | https://backrooms-wiki.wikidot.com/level-2 |
| 2 | Ice Tunnel | Alan Light | CC BY 2.0 | https://backrooms-wiki.wikidot.com/level-2 |
| 3 | fluorescent lights | OiMax | CC BY 2.0 | https://backrooms-wiki.wikidot.com/level-2 |
| 4 | Fluorescent Light | uetchy | CC BY 2.0 | https://backrooms-wiki.wikidot.com/level-2 |

## Level 3 — The Electrical Station

| Slot | Image | Author | License | Source |
| --- | --- | --- | --- | --- |
| 1 | Prison Tour | Steve Mays | CC BY 2.0 | https://backrooms-wiki.wikidot.com/level-3 |
| 2 | sewers | Graeme Maclean | CC BY 2.0 | https://backrooms-wiki.wikidot.com/level-3 |
| 3 | Level3HallNew | Natedagreat563 | CC BY-SA 3.0 | https://backrooms-wiki.wikidot.com/level-3 |
| 4 | Prison Tour / dark hall | Steve Mays | CC BY 2.0 | https://backrooms-wiki.wikidot.com/level-3 |

## Level 4 — The Abandoned Office

The four workroom files are from the Backrooms Fandom Level 4 page. The Fandom file/source page remains authoritative for any file-specific attribution or license note beyond the community page license.

| Slot | Image | Source |
| --- | --- | --- |
| 1 | Level4Workrooms1.png | https://backrooms.fandom.com/wiki/Level_4 |
| 2 | Level4Workrooms2.png | https://backrooms.fandom.com/wiki/Level_4 |
| 3 | Level4Workrooms3.png | https://backrooms.fandom.com/wiki/Level_4 |
| 4 | Level4Workrooms4.png | https://backrooms.fandom.com/wiki/Level_4 |

## Level 5 — Terror Hotel

| Slot | Image | Author | License | Source |
| --- | --- | --- | --- | --- |
| 1 | Main Hall | Steam Pipe Trunk Distribution Venue | CC BY 2.0 | https://backrooms-wiki.wikidot.com/level-5 |
| 2 | HMS Belfast boiler room | Les Chatfield | CC BY 2.0 | https://backrooms-wiki.wikidot.com/level-5 |
| 3 | Boiler room | Joshua Crauswell | CC BY-SA 2.0 | https://backrooms-wiki.wikidot.com/level-5 |
| 4 | Level-5-Ballroom | See Fandom file/source page | See Fandom file/source page | https://backrooms.fandom.com/wiki/Level_5 |

## Level 6 — Lights Out

These four Level 6 files are referenced by the current Backrooms Fandom Level 6 article. The Fandom file/source page remains authoritative for file-specific attribution and licensing.

| Slot | Image | Source |
| --- | --- | --- |
| 1 | Level 6 Deviantart | https://backrooms.fandom.com/wiki/Level_6 |
| 2 | Untitled205 20240925204431 | https://backrooms.fandom.com/wiki/Level_6 |
| 3 | Lights Out | https://backrooms.fandom.com/wiki/Level_6 |
| 4 | Umbrallight | https://backrooms.fandom.com/wiki/Level_6 |

## Selection notes

The source pool considered for this snapshot refresh was the set supplied for the project: Backrooms Wikidot, Backrooms Fandom, Liminal Archives, Backrooms Freewriting, Backrooms Exploration, Backrooms Archives, Lost Souls, The Backrooms Canon, Backrooms Wiki 2021 Archival, Kane Pixels Backrooms Fandom, and Backrooms WikiOasis. Duplicate/mirrored files were not counted as separate candidates. Selection favors clean environment composition, recognizability at Snapshot-card size, and compatibility with the existing Kai foreground overlay.
