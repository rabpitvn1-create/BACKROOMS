# Level Snapshot image sources

The release build fetches 28 source images into `level_snapshots/rotation/`: four images for each Level 0–6. The Snapshot renderer changes image deterministically every three completed turns:

- turns 1–3 → slot 1
- turns 4–6 → slot 2
- turns 7–9 → slot 3
- turns 10–12 → slot 4
- turn 13 repeats slot 1

The source manifest is `android-apk/snapshot_sources.json`. `android-apk/fetch-level-snapshots.py` verifies that all 28 downloads are supported image files, stay below the build size limit, and do not duplicate another selected image byte-for-byte.

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
| 4 | Dora 1 - Interiør på bakkenivå (2013) | Municipal Archives of Trondheim | CC BY 2.0 | https://backrooms-wiki.wikidot.com/level-1 |

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

The previous Fandom `Special:Redirect/file` endpoints returned HTTP 403 from GitHub Actions, so they were removed from the build path. The replacement set uses direct Wikimedia Commons media URLs with explicit free-file attribution and licensing.

| Slot | Image | Author | License | Source |
| --- | --- | --- | --- | --- |
| 1 | Empty office | Ged Carroll | CC BY 2.0 | https://commons.wikimedia.org/wiki/File:Empty_office.jpg |
| 2 | Office cubicles | David R. Tribble | CC BY-SA 3.0 | https://commons.wikimedia.org/wiki/File:Office-Cubicals-5205.jpg |
| 3 | Empty Office Real Estate | Carl Lender | CC BY 2.0 | https://commons.wikimedia.org/wiki/File:Empty_Office_Real_Estate_(22789285225).jpg |
| 4 | Empty room of office | Np6824 | CC BY-SA 4.0 | https://commons.wikimedia.org/wiki/File:Empty_room_of_office.jpg |

## Level 5 — Terror Hotel

The three existing Backrooms Wikidot images remain because the CI runner has already fetched them successfully. Only the former Fandom ballroom slot was replaced with a stable Commons source.

| Slot | Image | Author | License | Source |
| --- | --- | --- | --- | --- |
| 1 | Main Hall | Steam Pipe Trunk Distribution Venue | CC BY 2.0 | https://backrooms-wiki.wikidot.com/level-5 |
| 2 | HMS Belfast boiler room | Les Chatfield | CC BY 2.0 | https://backrooms-wiki.wikidot.com/level-5 |
| 3 | Boiler room | Joshua Crauswell | CC BY-SA 2.0 | https://backrooms-wiki.wikidot.com/level-5 |
| 4 | Ballroom of the Corinthia Hotel London | Daniel X. O'Neil | CC BY 2.0 | https://commons.wikimedia.org/wiki/File:Ballroom_of_the_Corinthia_Hotel_London_2012-12-27.jpg |

## Level 6 — Lights Out

The current project canon treats the Level 6 baseline as a near-black outdoor tundra rather than the older dark-corridor interpretation. The replacement set therefore uses polar-night, snowfall, frozen-lake and snowy-night landscapes instead of Fandom corridor art.

| Slot | Image | Author | License | Source |
| --- | --- | --- | --- | --- |
| 1 | Polar Night | Stefan Rimaila | CC BY 3.0 | https://commons.wikimedia.org/wiki/File:Polar_Night_(94208663).jpeg |
| 2 | Blue hour and snowfall over Øvervatnet lake | Frankemann | CC BY-SA 4.0 | https://commons.wikimedia.org/wiki/File:Blue_hour_and_snowfall_over_%C3%98vervatnet_lake.jpg |
| 3 | Nordkinnhalvøya polar night | Algkalv | CC BY 3.0 | https://commons.wikimedia.org/wiki/File:Nordkinnhalvoya-polar-night.jpg |
| 4 | Mount Field National Park Snowy Night | Matheus Hobold Sovernigo | CC BY-SA 4.0 | https://commons.wikimedia.org/wiki/File:Mount_Field_National_Park_Snowy_Night.jpg |

## Source reliability notes

- Levels 0–3 and Level 5 slots 1–3 use the established Backrooms Wikidot/Wikidot file hosts.
- Level 4, Level 5 slot 4 and all Level 6 slots use direct `upload.wikimedia.org` files rather than Fandom or Commons redirect endpoints.
- Selected originals fit the fetcher's 12 MiB per-image ceiling.
- Build validation still fails closed if a source disappears, returns non-image content, exceeds the size limit, or duplicates another selected snapshot.
