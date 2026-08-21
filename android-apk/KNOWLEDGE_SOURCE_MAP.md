# The BACKROOMS — Drive Source Map for Runtime Knowledge

Status: SOURCE MAP / implementation prerequisite

This file maps runtime facts to their authoritative Google Drive source. It is intentionally independent from `drive-canon.txt`, current prompts, legacy compact canon, and old patches. Those implementation artifacts are audit inputs only.

## Authority order

1. Latest explicit user instruction.
2. Current Text Game rules and current campaign state for runtime facts.
3. Current Character Codex for immutable character identity, personality core, abilities, equipment, knowledge limits, relationship baseline, address rules, and hard locks.
4. Current World canon for world/level/entity/item facts.
5. Current story/continuity for mutable campaign state.
6. Writing rules for prose/dialogue/POV/knowledge boundaries.
7. Reference/benchmark material only where its own source says it applies.

`UNKNOWN`, `OPEN`, `CHƯA KHÓA`, or equivalent remains unknown. The runtime database must not reconcile it by inference.

## Drive audit notes

Directly opened from Google Drive before implementation:

- `00_RULES/00_RULES_BOOT.md`
- `05_TextGame/RULES/TEXT_GAME_RULES.md`
- `05_TextGame/RULES/GAME_MASTER_RULES.md`
- `05_TextGame/STORY/MAIN_STORY.md`
- `02_CHARACTERS/Kai_Codex.docx`
- `02_CHARACTERS/Iris_Codex.docx`
- `02_CHARACTERS/Syvial_Codex.docx`
- `01_WORLD/world.md`
- `01_WORLD/level.md`
- `01_WORLD/entity.md`
- `01_WORLD/items.md`
- `00_RULES/worldcodex.md`
- `00_RULES/CONVERSATION.MD`
- `00_RULES/WayOfWriting.md`
- `00_RULES/Vivid_Verbs_Guide.md`
- `00_RULES/Supernatural_Horror_Craft_Guide.md`
- `00_RULES/Intimacy_Writing_Guide.md`
- `00_RULES/Dialogue_Benchmarks.md`
- `00_RULES/Những điều bắt buộc không được làm khi viết tiểu thuyết.md`
- `00_RULES/HuongDan.txt`, because the boot/writing router delegates supplemental prose behavior to it.

The Drive folder `05_TextGame/SAVE` was opened directly and currently returned no files. Searches for the exact three required save names also returned no Drive result. Therefore no campaign state is imported from a guessed or legacy source. Runtime migration may preserve existing local APK save data, but the database seed treats the Drive save state as absent rather than inventing it.

## Record contract

Every important runtime record must expose:

- `id`: stable, namespaced identifier.
- `domain`: CHARACTER / RELATIONSHIP / WORLD / LEVEL / ENTITY / ITEM / WRITING / STORY / GAME_RULE.
- `kind`: runtime-card, identity, ability, equipment, hard-lock, level, entity, item, writing-rule, objective, event, discovery, promise, thread, knowledge-edge, etc.
- `text`: compact authoritative payload used in a context packet.
- `source.document`: exact Drive source path/title.
- `source.anchor`: source section/code when available.
- `authority`: authority class.
- `mutability`: IMMUTABLE / BASELINE / RUNTIME_MUTABLE.
- `priority`: hard-canon ordering for budget selection.
- `tags`: structured lookup tags.
- `references`: direct record IDs.
- `affordances`: scene-driven capability lookup tags.

Runtime traceability is therefore:

`GM context line -> KnowledgeRecord.id -> source.document + source.anchor`.

## Character source map

| Runtime ID | Source | Anchor | Mutability | Notes |
| --- | --- | --- | --- | --- |
| `CHAR.KAI.RUNTIME_CORE` | `Kai_Codex.docx` | `KAI-QUICK-01`, `KAI-PER-01`, `KAI-DLG-01`, `KAI-WEAK-01`, `KAI-ACTION-LOCK-01` | IMMUTABLE | Minimal present-character card, not full ability dump. |
| `CHAR.KAI.IDENTITY` | `Kai_Codex.docx` | `KAI-ID-01` | IMMUTABLE | Kai Akechi / Twilight; origin era 2299 is not birth year; true age unknown. |
| `CHAR.KAI.SPARDA_CORE` | `Kai_Codex.docx` | `KAI-CORE-SPARDA-01` | IMMUTABLE | Infinite demon power; do not add intrinsic depletion/cooldown. |
| `CHAR.KAI.DEVIL_TRIGGER` | `Kai_Codex.docx` | `KAI-DT-01` | IMMUTABLE | No invented berserk state, intrinsic duration cap, cooldown, or backlash. |
| `CHAR.KAI.GUILTY_CROWN_OVERRIDE` | `Kai_Codex.docx` | `KAI-ULT-GCO-01` | IMMUTABLE | Exact 24-shot override while external time is fully stopped, under the codex conditions. |
| `CHAR.KAI.WHITE_WRAITH` | `Kai_Codex.docx` | `KAI-EQP-WWM-01` | IMMUTABLE | Signature firearm; demonic ammunition comes from Kai. |
| `CHAR.KAI.ARMOR` | `Kai_Codex.docx` | `KAI-EQP-ARMOR-01` | IMMUTABLE | Blackblood Armor and linked modules. |
| `CHAR.KAI.OMNIVAULT` | `Kai_Codex.docx` | `KAI-EQP-OMNIVAULT-01`, `KAI-WEAK-01` | IMMUTABLE | Inanimate-only storage; 3 scan/copy slots and codex restore constraints. |
| `CHAR.IRIS.RUNTIME_CORE` | `Iris_Codex.docx` | `IRIS-QUICK-01`, `IRIS-PER-01`, `IRIS-REL-01`, `IRIS-CANON-GATE-01` | IMMUTABLE | Present-character card. Iris is a ranged combatant/scout, not a remote drone station. |
| `CHAR.IRIS.ARGUS` | `Iris_Codex.docx` | `IRIS-SCOUT-TERRAIN-01`, `IRIS-SCOUT-GROUND-01`, `IRIS-SCOUT-TARGET-01` | IMMUTABLE | Direct observation + armor sensors + terrain/route/cover/trace analysis. No omniscience, wall vision, remote cameras, or automatic true-form detection. |
| `CHAR.IRIS.THOUSANDFOLD` | `Iris_Codex.docx` | `IRIS-THOUSANDFOLD-01` | IMMUTABLE | Information processing up to 1:1000; does not accelerate the body 1000x. |
| `CHAR.IRIS.IVORY_EBONY` | `Iris_Codex.docx` | `IRIS-COMBAT-TWIN-01`, `IRIS-AMMO-DEMONIC-01` | IMMUTABLE | Mechanical twin pistols; ammunition formed from Iris's demon power; infinite source does not imply infinite ROF/durability/damage/accuracy. |
| `CHAR.IRIS.SUPPORT` | `Iris_Codex.docx` | Field Galley / Field MedNet sections | IMMUTABLE | Field MedNet is not magic healing; Field Galley does not create matter. |
| `CHAR.IRIS.UNKNOWN` | `Iris_Codex.docx` | `IRIS-UNKNOWN-01` | IMMUTABLE | Preserve current unknown fields, including exact combat tier and Iris↔Syvial address if not otherwise locked. |
| `CHAR.SYVIAL.RUNTIME_CORE` | `Syvial_Codex.docx` | `SYVIAL-QUICK-01`, `SYVIAL-OVERVIEW-01`, `SYVIAL-YANDERE-01`, `SYVIAL-REL-KAI-01`, `SYVIAL-ACTION-LOCK-01` | IMMUTABLE | UR+, lucid/socially capable, heavy yandere toward Kai without random murder or loss of tactical intelligence. |
| `CHAR.SYVIAL.LUCIFER_CORE` | `Syvial_Codex.docx` | `SYVIAL-CORE-LUCIFER-01` | IMMUTABLE | Infinite demon power; no invented intrinsic energy meter/cooldown. |
| `CHAR.SYVIAL.DEVIL_TRIGGER` | `Syvial_Codex.docx` | `SYVIAL-DT-01` | IMMUTABLE | Does not remove control or add an intrinsic duration/cooldown absent from codex. |
| `CHAR.SYVIAL.GODKILLER` | `Syvial_Codex.docx` | `SYVIAL-EQP-GODKILLER-01` | IMMUTABLE | Purely mechanical greatsword; do not turn into gun/gunblade. |
| `CHAR.SYVIAL.GODKILLER_OVERRIDE` | `Syvial_Codex.docx` | `SYVIAL-ULT-GKO-01` | IMMUTABLE | Exact 24 cuts in fully stopped external time under codex conditions. |
| `CHAR.SYVIAL.COMBAT` | `Syvial_Codex.docx` | `SYVIAL-COMBAT-01`, `SYVIAL-STYLE-COMBAT-01`, `SYVIAL-WEAK-01` | IMMUTABLE | High-tier sword combat, assault/control/counter/finish; do not competence-suppress. |

## Relationship and address source map

| Runtime ID | Source | Anchor | Mutability | Canon baseline |
| --- | --- | --- | --- | --- |
| `REL.KAI.IRIS.BASELINE` | `Iris_Codex.docx` | `IRIS-REL-KAI-01` | BASELINE | Iris has romantic feelings for Kai; Kai knows but has not reciprocated; official state remains teammate / commander-specialist. |
| `ADDR.IRIS.KAI` | `Iris_Codex.docx` | `IRIS-REL-KAI-01`, `IRIS-DLG-01` | IMMUTABLE | Iris uses the `anh–em` system with Kai. |
| `REL.IRIS.SYVIAL.BASELINE` | `Iris_Codex.docx` | `IRIS-REL-SYVIAL-01` | BASELINE | Friends and trusted teammates with romantic rivalry around Kai; not enemies. |
| `REL.KAI.SYVIAL.BASELINE` | `Syvial_Codex.docx` | `SYVIAL-REL-KAI-01` | BASELINE | Syvial's feelings are immutable; how far Kai reciprocates is continuity-controlled. |
| `ADDR.SYVIAL.KAI` | `Syvial_Codex.docx` | `SYVIAL-REL-KAI-01` | IMMUTABLE | Syvial -> Kai: `em`, calls `anh` or `Kai`; `Anh Kai` may be emphatic/teasing. Kai -> Syvial: `anh`, calls `Syvial` or `em`. `cục cưng` only if continuity has locked a long-term romantic relationship, not the default. |
| `REL.CAMPAIGN.*` | runtime continuity | relationship-change events | RUNTIME_MUTABLE | Campaign delta overlays baseline without mutating codex records. |

## World source map

| Runtime ID | Source | Anchor | Mutability |
| --- | --- | --- | --- |
| `WORLD.CORE` | `01_WORLD/world.md` | `BACKROOMS-WORLD-CORE-R2` / 0.1–0.5 | IMMUTABLE |
| `LEVEL.00` … `LEVEL.06` | `01_WORLD/level.md` | corresponding Level section | IMMUTABLE |
| `ENTITY.GLOBAL_HARD_LOCK` | `01_WORLD/entity.md` | 0.6 | IMMUTABLE |
| `ENTITY.*` | `01_WORLD/entity.md` | stable entity codes such as `ENT-1A`, `ENT-2C`, `ENT-R01` | IMMUTABLE |
| `ITEM.GLOBAL_HARD_LOCK` | `01_WORLD/items.md` | 0.7–0.8 | IMMUTABLE |
| `ITEM.ALMOND_WATER` | `01_WORLD/items.md` | 8.1 | IMMUTABLE |
| `ITEM.GREEK_FIRE` | `01_WORLD/items.md` | 8.2 | IMMUTABLE |
| `ITEM.LIQUID_PAIN` | `01_WORLD/items.md` | 8.3 | IMMUTABLE |
| `ITEM.*` | `01_WORLD/items.md` | stable item/resource section | IMMUTABLE |

Adding future levels/entities is append-only: add records and references/tags; the GM prompt must not require a code branch for each new Level/Entity.

## Game rules and story source map

| Runtime ID | Source | Anchor | Mutability |
| --- | --- | --- | --- |
| `GAME.TEXT.CORE` | `05_TextGame/RULES/TEXT_GAME_RULES.md` | 1–12 | IMMUTABLE |
| `GAME.GM.FAIRNESS` | `05_TextGame/RULES/GAME_MASTER_RULES.md` | 1–15 | IMMUTABLE |
| `STORY.MAIN.PREMISE` | `05_TextGame/STORY/MAIN_STORY.md` | 2 | IMMUTABLE |
| `STORY.MAIN.OBJECTIVE` | `05_TextGame/STORY/MAIN_STORY.md` | 3 | BASELINE |
| `STORY.MAIN.COMMS_INITIAL` | `05_TextGame/STORY/MAIN_STORY.md` | 4 | BASELINE |
| `STORY.MAIN.IRIS_EXISTENCE` | `05_TextGame/STORY/MAIN_STORY.md` | 5 | BASELINE |
| `STORY.MAIN.SYVIAL_EXISTENCE` | `05_TextGame/STORY/MAIN_STORY.md` | 5 | BASELINE |
| `STORY.CONTINUITY.EVENT.*` | runtime reducer | committed gameplay events | RUNTIME_MUTABLE |
| `STORY.CONTINUITY.OBJECTIVE.*` | runtime continuity | objective state | RUNTIME_MUTABLE |
| `STORY.CONTINUITY.DISCOVERY.*` | runtime continuity | deterministic discovery event | RUNTIME_MUTABLE |
| `STORY.CONTINUITY.PROMISE.*` | runtime continuity | explicit validated social event | RUNTIME_MUTABLE |
| `STORY.CONTINUITY.THREAD.*` | runtime continuity | unresolved-thread state | RUNTIME_MUTABLE |
| `STORY.CONTINUITY.KNOWLEDGE.*` | runtime continuity | character knowledge ownership edge | RUNTIME_MUTABLE |

The required Drive save files are currently absent from the Drive folder, so there is no source-authorized row that claims a current `ACTIVE_RUN` value or current campaign facts. Local saves are migration input only, not Drive canon.

## Writing source map

| Runtime ID | Source | Anchor/use |
| --- | --- | --- |
| `WRITING.DIALOGUE` | `00_RULES/CONVERSATION.MD` | `RULES.DIALOGUE`; sole authority for dialogue generation/audit. |
| `WRITING.ADDRESS` | `00_RULES/CONVERSATION.MD` + character relationship sections | Address is relationship canon, never guessed. |
| `WRITING.KNOWLEDGE_BOUNDARY` | `00_RULES/worldcodex.md`, forbidden rules, `CONVERSATION.MD` | No backstage/other-character knowledge leak. |
| `WRITING.COMPETENCE` | forbidden rules + `worldcodex.md` + GM rules | No sudden stupidity or hidden competence. |
| `WRITING.PROSE_POV` | `WayOfWriting.md` + relevant hard bans | POV/information discipline. |
| `WRITING.VIVID_VERBS` | `Vivid_Verbs_Guide.md` | prose-level verb selection only, not dialogue generation. |
| `WRITING.HORROR` | `Supernatural_Horror_Craft_Guide.md` | horror/suspense distribution; never withhold competence. |
| `WRITING.INTIMACY` | `Intimacy_Writing_Guide.md` | relationship/intimacy reference, cannot override canon/address/consent. |
| `WRITING.DIALOGUE_BENCHMARK` | `Dialogue_Benchmarks.md` | post-draft reference only, never a generation template. |
| `WRITING.GUIDE_SUPPLEMENT` | `HuongDan.txt` | supplemental emotion/action around dialogue; delegated dialogue authority remains `CONVERSATION.MD`. |

## Retrieval rules derived from sources

1. Direct stable IDs first.
2. Explicit relationship edges second.
3. Current state, party presence, level and structured tags third.
4. Scene affordance -> relevant present-character capability next.
5. Semantic retrieval is a last resort only when structured lookup is insufficient.
6. Present characters always receive a compact runtime core. They do not need to be named by player input.
7. Scene-driven capability examples:
   - trace/route/cover/ambush/target-identification problem + Iris present -> `CHAR.IRIS.ARGUS`.
   - direct combat/threat/control/frontline problem + Syvial present -> `CHAR.SYVIAL.COMBAT` and, only when relevant, weapon/override modules.
8. Capability retrieval never implies omniscience, invented limits, or automatic action. Followers retain autonomy.
9. Raw dialogue log is a small recency buffer only. Long-term memory is structured continuity.
10. Context budget priority: hard canon -> current state/scene -> active story -> present runtime cards -> relationship/address -> relevant ability/knowledge limits -> relevant Level/Entity/Item -> flavor.

## Baseline implementation audit link

The current build-time runtime is patch-generated. `patch-ai-orchestrator.py` extracts broad string sections from `drive-canon.txt` and `kai-codex.txt`; `compactStateForPrompt` copies most legacy state and retains six recent log entries; routing uses keyword helpers such as dialogue/combat/item/entity plus limited presence checks. `patch-conditional-audit.py` already skips the AI critic below a risk threshold, but critic calls duplicate a broad canon/state slice.

Those files are not a canon source for the new database. They are retained only as OLD-system input for benchmark comparison.
