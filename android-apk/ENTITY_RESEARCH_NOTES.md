# ENTITY RESEARCH NOTES

Purpose: provenance for the Entity records added to the Android runtime. External sources are research references only. Project canon and the user-requested hard lock win on conflict: **every Entity is hostile to humans; no Entity is a neutral/friendly ally, trader, rescuer, or beneficial symbiote.**

## Existing entities researched for runtime

### Hound
- Backrooms Wiki Entity 8 describes Hounds as aggressive humanoids adapted to moving on all fours. Their limbs support quadrupedal travel; long black hair covers much of the head, and close observation reveals sharp claws plus an abnormally large mouth with sharp teeth. The page also reports that direct eye contact may intimidate them briefly.
- Source: https://backrooms-wiki.wikidot.com/entity-8
- Project use: preserve `ENT-1A / ENT-2B` pack pressure, pursuit and retreat/return behavior. Eye-contact intimidation is contextual external reference, not a guarantee that overrides combat state or the hostile-to-humans hard lock.

### Clump
- Backrooms Wiki Entity 5 describes Clumps as dangerous tangled bundles of human-like limbs with exceptional speed and agility. It reports large specimens around 3 feet across, a longest limb used to seize prey, and a central mouth with razor-sharp teeth.
- Source: https://backrooms-wiki.wikidot.com/entity-5
- Source note: the current page is explicitly marked outdated by the wiki, so only stable morphology/behavioral basics are used as external reference.
- Project use: preserve the existing `ENT-1B / ENT-2A` hunting behavior from `01_WORLD/entity.md`; all instances hunt humans.

### Duller
- Backrooms Wiki Entity 6 describes a tall dark-grey faceless and earless humanoid with a frail skeletal frame, unusually long/extensible arms, wobbly stance and unnatural gait. It also reports high speed/strength and a hunting method that can no-clip an arm through a wall to seize prey from a neighboring hall.
- Source: https://backrooms-wiki.wikidot.com/entity-6
- Project use: combine that reference with the existing `ENT-1C` blind-corner/watch behavior; apparent retreat never means neutrality.

### Hostile Faceling
- Backrooms Wiki Entity 9 describes humanoid beings without normal facial features that imitate human practices and routines. The external article includes peaceful/helpful behavior.
- Source: https://backrooms-wiki.wikidot.com/entity-9
- **Project override:** the peaceful/helpful portion is rejected. `ENT-1E` is always hostile to humans and uses human-like behavior only to reduce vigilance or hunt.

### Paintings
- Fandom-derived Level 1 material describes living paintings/drawings whose depicted organisms can reach out, grab nearby wanderers, and drag them into the image; moving eyes are also reported.
- Reference: https://backrooms-liminality.fandom.com/wiki/Level_1
- Supporting game reference: https://roblox-bi-game.fandom.com/wiki/Paintings
- Project use: keep `ENT-1G` as a hostile visual lure/grab predator. These sources are lower-authority inspiration, not Project canon.

### Predatory Window
- Current Backrooms Wiki Entity 2 describes anomalous Windows as predatory manifestations that show misleading perceived landscapes and can physically seize human prey.
- Source: https://backrooms-wiki.wikidot.com/entity-2
- Project use: retain the simpler `ENT-2E / ENT-5B` implementation: impossible window placement, deceptive silhouette/scene, unreliable reach, and a grab/pull attack. Every instance is hostile.

### Hotel Corpse Lure
- No authoritative external source was found for this exact Project entity.
- **Project-original:** content comes only from `01_WORLD/entity.md` `ENT-5E`. Classification remains OPEN between a separate Entity and a mechanism of the Beast of Level 5. Any apparent breathing, voice, warmth, movement, pointing, or useful item is bait.

## ENT-R02 — JANE THE KILLER

Internet folklore does not provide one stable Jane continuity. Public summaries distinguish incompatible versions such as Jane Arkensaw and Jane Richardson.
- Reference overview: https://en.wikipedia.org/wiki/Jeff_the_Killer

Project decision:
- `Jane the Killer` is a survivor-assigned name for a roaming humanoid predator.
- Origin, Frontrooms identity, burn history, supernatural power set, and any relationship to Jeff remain UNKNOWN instead of importing one fan continuity.
- Jane stalks humans, uses misdirection and close-range ambush, and may use a blade.
- Jane is never friendly or neutral.
- Separate-sighting identity and post-death persistence remain OPEN.

## ENT-R03 — SLENDERMAN

The original Slender Man was created by Victor Surge / Eric Knudsen on the Something Awful forum in June 2009. The original concept established a tall, thin, faceless suited figure, with non-human appendage/tentacle elements appearing in the early image work.
- Original forum thread: https://forums.somethingawful.com/showthread.php?threadid=3150591
- Background overview: https://en.wikipedia.org/wiki/Slender_Man

Project decision:
- `Slenderman` is a survivor-assigned name for a roaming anomalous humanoid predator.
- Origin and identity remain UNKNOWN.
- It stalks humans, isolates targets, and closes for abduction or lethal attack.
- It is never friendly or neutral.
- Later fan powers are **not automatically imported**. In particular, the GM may not grant free teleportation, omniscience, automatic equipment failure, or retroactive threat placement. Movement and positioning still obey the Game Master fairness/path/time rules.

## ENT-R04 — ASYNC MEMBER

This entry is a direct user-retcon combat definition, not imported external Async lore.
- Classification: human hostile combatant when selected through the Entity encounter roster.
- This does **not** make every Async employee hostile and does not override the Foundation Canon rule that Async as an organization is not pre-labeled as the villain.
- Base HP pool: `60/60`.
- Base stats: `HP Stat 7 / Defend 8 / Agi 9 / Crit 10`.
- Active `[Let's catch you]`: 20% proc on each Async Member combat turn; on proc the attack uses current BaseDMG +20%, then a successful hit applies `[Choáng]` for 2 turns.
- Passive `[Anysc Evade]`: always active; Evasion is fixed at 25%.
- Overlay source: Drive file `1QLdwXwXNcFNrGLgr-qJl0cBuNBnUw0WI`, retained byte-for-byte as `async_member.png`.

## Runtime encounter policy

All 19 Entity entries are roaming-capable across every playable Level. Each Entity receives its own independent 3.0000% appearance roll on an eligible physical gameplay turn. The roll is not restricted by Level or environment. Multiple Entity rolls may succeed on the same turn. Existing continuity can keep an Entity present without requiring another appearance roll.
