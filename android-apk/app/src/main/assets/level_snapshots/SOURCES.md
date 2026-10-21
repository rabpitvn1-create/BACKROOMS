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
location, without extra network requests. At the time of that import, nodes 0.1, 0.5 and 0.7 had no dedicated
photos and fell back to `level_0.webp`. Level 7+ assets were not included.

## AI-generated supplemental snapshots (2026-10-14)

These 25 new images were generated for this task using the project Canon
and existing visual references, then uploaded to the same Novel / Backrooms
Level Drive folder. They are supplemental illustrations, not author-created
Canon or original Drive reference imagery. The APK copies the verified WebP
files byte-for-byte after upload. Every new file is 1280 × 720 and at most
300,000 bytes; existing reference files are unchanged.

The artwork for node IDs 0.1 and 0.7 follows the author Canon. The existing
runtime names for those IDs conflict with that Canon; this asset import does
not rename runtime nodes, change topology, or alter Canon authority.
Existing Level 6 reference discrepancies are outside this import.

Generation stopped at the user's request after these 25 verified images;
14 further missing slots remain. The current import does not claim complete
three-image coverage for every supported node.

| Asset | Drive source | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| `level_00p2__snapshot_002.webp` | [1vZDVwg3BcUHWE_fiVYyrG5m6d7upTizQ](https://drive.google.com/file/d/1vZDVwg3BcUHWE_fiVYyrG5m6d7upTizQ/view?usp=drivesdk) | 54032 | `a8f11d69afa07181d2cf43924b1529d811edebfcc1486277267171b202b9c5e1` |
| `level_00p1__snapshot_001.webp` | [182WNN1fxXOvcYI4jBafhhta3m--d7NJR](https://drive.google.com/file/d/182WNN1fxXOvcYI4jBafhhta3m--d7NJR/view?usp=drivesdk) | 119366 | `5c14301b88eb76c05e6046a02525cce08d1bea4c1e7bda44f9a6c800a941fd4d` |
| `level_00p1__snapshot_002.webp` | [11a2E35rrAT9FBpUh1s7xpQMAyeQJEQbl](https://drive.google.com/file/d/11a2E35rrAT9FBpUh1s7xpQMAyeQJEQbl/view?usp=drivesdk) | 140802 | `581c9e11f4b10c0d7e017de1d5e81d6b3d45557326495637cea312be24345c2a` |
| `level_00p1__snapshot_003.webp` | [1rm7rmkTrIYrA2LNKQkeaDLeyarRrjEWp](https://drive.google.com/file/d/1rm7rmkTrIYrA2LNKQkeaDLeyarRrjEWp/view?usp=drivesdk) | 111654 | `644eb253f04a3ee50c3ddbccbc389b8fc6aae490d46b189effcfd87063b2014c` |
| `level_00p2__snapshot_003.webp` | [1qQFnLoGiSLl9u0Zn4otW-aQdkT-_m2dG](https://drive.google.com/file/d/1qQFnLoGiSLl9u0Zn4otW-aQdkT-_m2dG/view?usp=drivesdk) | 88444 | `549d7772e360966c0990689bea9229ccdfd75ef77d6b7c8b62aa689ec5f9e301` |
| `level_00p5__snapshot_001.webp` | [1qlMhZTg5FmmWOPqfXrKlxt4Ooh-z9gLE](https://drive.google.com/file/d/1qlMhZTg5FmmWOPqfXrKlxt4Ooh-z9gLE/view?usp=drivesdk) | 124518 | `88f659efc0565aac826d6ab33de18828f1a150aeb0556ab819671b7a751fb93d` |
| `level_00p5__snapshot_002.webp` | [1IyNtAFD3Ie4Usr5vGChguM-e7AqLrzm-](https://drive.google.com/file/d/1IyNtAFD3Ie4Usr5vGChguM-e7AqLrzm-/view?usp=drivesdk) | 135938 | `056b0846eda4ea73fc35d8abb02c94bd51375f0eb02c7670b56ee691efe77915` |
| `level_00p5__snapshot_003.webp` | [1OyqTPUW49JhutJaXv09TB7ppag9MGAb9](https://drive.google.com/file/d/1OyqTPUW49JhutJaXv09TB7ppag9MGAb9/view?usp=drivesdk) | 145288 | `c5b9195003aebbc74b385035ff119a08aec50616704029860910f8904ded1424` |
| `level_00p7__snapshot_001.webp` | [1m6ppE265DrS5Ebx3m9nFHppmrv4_dgqL](https://drive.google.com/file/d/1m6ppE265DrS5Ebx3m9nFHppmrv4_dgqL/view?usp=drivesdk) | 107212 | `4c345e29725bd54dd47b8046234c3e9438252240508ec61d70dff7d58ec6777b` |
| `level_00p7__snapshot_002.webp` | [1-1YgSm7mA4Mu5Ec5pszCKpLcw_oZVX8W](https://drive.google.com/file/d/1-1YgSm7mA4Mu5Ec5pszCKpLcw_oZVX8W/view?usp=drivesdk) | 131146 | `c7f31e0f673b0022c43ba93ba5687e195d0593e55c2b04130c1aa65d1f8e500d` |
| `level_00p7__snapshot_003.webp` | [1ZgJI-rWBt1Az46PM173FSlr-XWOLF8-S](https://drive.google.com/file/d/1ZgJI-rWBt1Az46PM173FSlr-XWOLF8-S/view?usp=drivesdk) | 122968 | `36dfab7e26fe534d2f08f39bb898d3feabf3d15420b4ccf6ff77abd3c46606d5` |
| `area_00__manila_room__snapshot_002.webp` | [1ryTpTmOrpTxTOtP01Re0lueutRkIuZQQ](https://drive.google.com/file/d/1ryTpTmOrpTxTOtP01Re0lueutRkIuZQQ/view?usp=drivesdk) | 103024 | `1a29be4dfe825adb24b7d9c260c12cb108562b82b0bbe35434f1f1268daa23be` |
| `area_00__manila_room__snapshot_003.webp` | [1b_Oaeetb_TDdpfqsjONmHChYrHB_1gNJ](https://drive.google.com/file/d/1b_Oaeetb_TDdpfqsjONmHChYrHB_1gNJ/view?usp=drivesdk) | 79790 | `cf5a5dffc784783b5c09ff09a9d8a060095f9a9c28da1c8ff8d7bf8597f4de1e` |
| `area_00__the_torment__snapshot_002.webp` | [1Uqdi103l3BAby52YKZMJKMoeUdL2IL_9](https://drive.google.com/file/d/1Uqdi103l3BAby52YKZMJKMoeUdL2IL_9/view?usp=drivesdk) | 216118 | `676b31d0bf3dda77f525afddb4ebf10b973812e38570a586d8d962c218a8736a` |
| `area_00__the_torment__snapshot_003.webp` | [1t_A7Km_-QiG58LWcIMrYom3bwiWKQPWF](https://drive.google.com/file/d/1t_A7Km_-QiG58LWcIMrYom3bwiWKQPWF/view?usp=drivesdk) | 170058 | `4a315e2e081986353df33964a13c833bbd5988a4cbe6a68fa26535a1fd1e1fe7` |
| `area_00__red_rooms__snapshot_002.webp` | [1Afhdl8TEm6P-FRXaiwyWledebHQXyBkE](https://drive.google.com/file/d/1Afhdl8TEm6P-FRXaiwyWledebHQXyBkE/view?usp=drivesdk) | 114578 | `1c3ca32b80cddfbd1b7ae3dbcee3ebb51584feb96f6422cd4c80671380459380` |
| `area_00__red_rooms__snapshot_003.webp` | [1-JK128PQzierg9uXbZo4j_LHAJVJPGrR](https://drive.google.com/file/d/1-JK128PQzierg9uXbZo4j_LHAJVJPGrR/view?usp=drivesdk) | 126856 | `58cab8bffd58f3c86c87a5666bbbd3d095194f8c02d718eff6e0bde0213872d9` |
| `area_00__dullness__snapshot_002.webp` | [1miPJfuUmvp2Gv2YMzH7dzNwDMxAQ2WxT](https://drive.google.com/file/d/1miPJfuUmvp2Gv2YMzH7dzNwDMxAQ2WxT/view?usp=drivesdk) | 162482 | `82cc2b57d98041bc8eecb246aaa0fc2a296c1d005e18fdd743fac2194b2d6844` |
| `area_00__dullness__snapshot_003.webp` | [1zTd4J2nmygIBOwIHhId_l8PqYv0xN29g](https://drive.google.com/file/d/1zTd4J2nmygIBOwIHhId_l8PqYv0xN29g/view?usp=drivesdk) | 120734 | `b7149428c43614ac4e2d64b34e3cf4789bf5e1db1d007382a4f972f68ba7c6b6` |
| `area_00__epsilon__snapshot_002.webp` | [1Ne_piv2eq-hm6LfInR3iOFg2BGfVkvq_](https://drive.google.com/file/d/1Ne_piv2eq-hm6LfInR3iOFg2BGfVkvq_/view?usp=drivesdk) | 108178 | `ee79228dd86ed1110b6d6df113583702c8f087d3fb991dca35370d8e06401cb7` |
| `area_00__epsilon__snapshot_003.webp` | [1Azpx8kxonIV-Hvtg3cF2kFJ5Qs8Myl38](https://drive.google.com/file/d/1Azpx8kxonIV-Hvtg3cF2kFJ5Qs8Myl38/view?usp=drivesdk) | 105328 | `6d93a5b29103c98f4d32e1fed756b806f8675114b37f32c94bd97d79b2dadbdc` |
| `area_00__ls_2__reference_snapshot_002.webp` | [18003OLrn0rxzWrE0vz39mhGroBvOYLrg](https://drive.google.com/file/d/18003OLrn0rxzWrE0vz39mhGroBvOYLrg/view?usp=drivesdk) | 144430 | `5f85f24b26aa7bcd224a5a5ce69fe67e9ad239b1b047adbbe3fd612fee65bd09` |
| `area_00__ls_2__reference_snapshot_003.webp` | [1yRk1AzVH6CAXlwp3Xab1k7x8ysD7K6wd](https://drive.google.com/file/d/1yRk1AzVH6CAXlwp3Xab1k7x8ysD7K6wd/view?usp=drivesdk) | 133814 | `51ae37a6ed4c88e98d0eff293779dc02b2152c2029f79b2b3c283c16f35bc12f` |
| `level_01p2__snapshot_002.webp` | [1VRvtDp4aqopwQMrjYeL9NwTsMC35JzK3](https://drive.google.com/file/d/1VRvtDp4aqopwQMrjYeL9NwTsMC35JzK3/view?usp=drivesdk) | 299616 | `608dfb85817155df549e224fa518fcf9f21412d732003b6f4ef1efdb48b81f6e` |
| `level_01p2__snapshot_003.webp` | [1AUWDCWxIl9uNAq8qTJceOzQiVnATUEq1](https://drive.google.com/file/d/1AUWDCWxIl9uNAq8qTJceOzQiVnATUEq1/view?usp=drivesdk) | 246532 | `207995b83a4e978a794920a740ebf52d8d2786ed0d917f656b88867c7589ca79` |


## Exact AREA environments (2026-10-21, PR #654)

60 distinct generated backgrounds cover every environment in `dullness`,
`epsilon`, and `ls_2` (20 each). IDs, names, zones and descriptions come unchanged
from `Level0AreaEnvironments` in `Level0AreaWorldDefinition.kt`; LS-2 follows
`LS2_PROJECT_CANON_20261021.md`. These illustrations do not add Canon.

All WebPs were uploaded to [Novel / AREA environment snapshots PR654 20261021](https://drive.google.com/drive/folders/1qszG_s-QUWBRCTVF2nbh55GsgrTBi2y2)
in three node folders before transfer to GitHub. GitHub blobs use the raw Drive
contents; their Git object hashes must match the prepared assets before the
branch changes. `area-environments.json` records each Canon environment, Drive
source, dimensions, byte size, SHA-256 and Git blob SHA. Files retain their
generated aspect ratios and stay below 275,000 bytes. Existing images remain
unchanged.

Routing uses the exact node/environment identity for all 60 authored rooms.
Node-level frames remain only for input without a valid scene environment.
The existing Level 0 and seven satellite-node mappings are unchanged.
