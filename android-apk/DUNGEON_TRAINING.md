# Dungeon training

Dungeon lives in GAME MENU and has five fixed slots: 15, 30, 60, 120 and 180 minutes. Each occupied slot trains one known, living Follower. Cao Minh is rejected. One completed real minute represents one Entity and awards 4 EXP, matching an ordinary Level-0 Entity's existing reward. There is no combat damage, world spawn, ordinary loot or Explorer-time advance.

A Follower leaves Party and becomes SEPARATED while assigned, so combat and physiology cannot also run them. Normal recruitment and the An Nhiên/Iris/Syvial debug recruitment helpers reject assigned Followers. The slot stops at its duration and stays occupied until the player brings the Follower back. Early return keeps only fully elapsed minutes. Returning does not displace anyone in Party; the player can invite the Follower again. A Core-owned `dungeon.unlocked` fact preserves availability even for a previously recruited debug Follower without manufacturing a Canon first meeting.

## Persistence and time

`DungeonState` is part of GameState/save schema 15; no old-save migration or compatibility path. Each run stores a unique serial, Follower, slot, fixed environment, native start timestamp and credited-minute cursor. The native clock supplies time; the browser sends only operation and IDs. Each accrued minute and the resulting EXP/stat rolls commit with the same GameState. A per-run/per-minute seed makes polling and offline catch-up identical. Slots do not restart automatically.

Offline training is lazy settlement when Core next reads state, including app reopen and checkpoint restore. No background Android service or per-minute wake lock is required. A native clock high-water mark prevents clock rollback from reducing progress or paying the same minute again. Fully local wall-clock time is not a trusted server clock; moving the device clock forward is not an anti-cheat boundary. Restoring a manual checkpoint restores character EXP and its training cursor together before catching up, rather than stacking rewards from two different snapshots.

The random Level-0 environment is picked once per assignment from existing authored identities. Dungeon uses the corresponding packaged snapshot and the Follower's existing avatar. It never alters campaign location, graph progress or the campaign snapshot selector.

## Books: Stat Book phase

One independent deterministic per-minute book roll covers **300/10,000 = 3% total for the book pool**. Only Sách + Stats exists at present, so a successful roll awards that type. Ordinary Entity loot excludes this item. Future Skill Books must share this same 3% pool and be mutually exclusive with Stat Books; do not add a second independent roll or silently assume equal category weights.

Books go to Cao Minh's normal Inventory. Full inventories/stack caps retain a typed pending-book count in the same GameState. Freed space collects pending books without replaying RNG. The run's credited-minute cursor, book count, pending count and inventory commit together. Already-credited minutes are never rerolled. There is no retroactive book debt.

Using a Stat Book opens STR/DEX/AGI/VIT/EVA selection. Confirming consumes one owned book and grants exactly +1 permanent base stat to its user, capped at 999. Cancel, invalid selection, unavailable actor or a capped stat consume nothing. There is no Level/EXP reward or HP healing from book use. Normal Inventory transfer/drop rules remain in force. Stat growth at level-up redistributes points to stats with remaining capacity so book-raised caps cannot block progression. Lucia identity normalization preserves earned stats/vitals.

## Verification

- `DungeonRuntimeTest`: duration caps, minute rate, five Followers, offline/polling equivalence, survival exclusion, no player EXP/world/loot changes, early return, stale-run rejection, clock rollback, image identity, strict save validation and recruitment guards.
- `DungeonFacadeTest`: restart, checkpoint catch-up without double EXP, recruitment after returning and rejection of forged client state/busy assignment.
- `dungeon.test.cjs`: five slots, bridge payload, double-click protection, stale callbacks, display-only progress, safe image/text rendering and packaging references.
- APK verification compares `dungeon.js` and `dungeon.css` byte-for-byte with source. The Core CI gate includes the Dungeon suites.
