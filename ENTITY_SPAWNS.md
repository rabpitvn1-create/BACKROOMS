# Entity spawn routing

Core rolls once per eligible EXECUTE turn on d10000: tickets 1–3500 create an Entity encounter; 3501–10000 do not. This is 35% per eligible turn at every node, regardless of the number of species in its pool. It is a probability, not a guarantee of exactly 35 encounters in every 100 played turns. Meta actions, active/pending combat and the explicit entityEncountersAllowed=false switch retain their existing exclusions.

| Exact level ID | Resident Entity keys |
| --- | --- |
| 1 | hound, clump, duller, deathmoth, hostile_faceling, false_puddle, paintings |
| 2 | clump, hound, smiler, skin-stealer, predatory_window, biological_pipeline |
| 3 | deathmoth, wretch, skin-stealer, cable_mimic |
| 5 | the_beast_of_level_5, predatory_window, skin-stealer, hound, hotel_corpse_lure |

Resident placement follows Project Entity Codex R4, sections ENT-1A–G, ENT-2A–F, ENT-3A–D and ENT-5A–E: https://drive.google.com/file/d/1hZKhEn31XWhQFyXcUgjh66KivSDNnNhh/view . The visual catalog does not establish habitats: https://drive.google.com/file/d/1XkkV7v1T4-dkODVUEmZ64QtRRe3e6e0B/view .

Nodes with no explicit resident roster use the roaming pool: jeff_the_killer, jane_the_killer, slenderman, diep_minh, blackroot_sentinel, sinew_strider, hollow_grasper, 926 (Whistlers, fan-created), 927 (Entity 927, fan-created), LuckyBear (fan-created). This includes Levels 0, 4, 6 and sublevels/areas without a separately confirmed roster. Exact node IDs are used: Level 1.2 does not inherit Level 1's residents.

Owner decision on 2026-10-16: entities without confirmed current habitats (Slenderman, Diệp Minh and the three Lifeforms) join roaming alongside Jeff/Jane. This is a gameplay allocation, not a new claim about their lore. Diệp Minh's past cultivation at Level 0 is not treated as confirmation of his current residence. No additional Entity profile is invented for a lore-only reference. Entities 926 (Whistlers), 927 (Entity 927), and LuckyBear are author-requested gameplay Entities, placed in roaming without claiming Canon habitats.

Resident pools contain only their assigned species; roaming is the fallback for empty resident pools. Every existing runtime Entity remains reachable. Existing common/lifeform/rare/killer weights (250/200/100/50) are relative species-selection weights within the chosen pool; they are no longer absolute per-species spawn percentages. The successful 3500-ticket range is normalized into those local weights without another random roll, preserving NPC roll order.

Regression tests enumerate all 10,000 tickets at every registered route node and verify exactly 3500 encounters and only eligible species. They also cover pool membership, roaming fallback, exact sublevels, location resolution, eligibility exclusions and weighted selection boundaries.
