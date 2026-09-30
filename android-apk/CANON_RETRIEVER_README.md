# Canon Retriever contract

## Purpose and authority

`app/src/main/assets/canon/*.md` is the only Markdown canon source shipped in the APK. `CanonRetriever` reads these files at startup, indexes headings, and selects bounded, verbatim sections after Java Core commits the turn. It is read-only. It cannot create or edit Facts, Threads, NarrativeSkeleton, CampaignSkeleton, inventory, party, combat, level transitions, Entity spawn, outcomes, or plot. AI narration is never promoted to global canon.

The existing `LevelCore`, `EntityCore`, `ItemCore`, and `CharacterEncounterCore` prompt contexts still carry runtime mechanics and guards. Their pre-existing knowledge sources have not been migrated by this change. Markdown selection is a shared additional layer, not a second manually maintained JSON copy of the imported lore.

## Add a file

Copy the original UTF-8 `.md` into `app/src/main/assets/canon/`. Preserve its wording, OPEN/UNKNOWN markers, and line endings. There is no Java registration or generated index. The APK asset loader sorts filenames and indexes all direct `.md` children. Keep operational instructions outside this directory. Validate the source and inspect its heading structure; a file with no headings becomes one preamble section, which may be too large for retrieval. Explicit structural headings can be added only when editorially authorized; do not rewrite lore merely to make it fit.

The initial import copies ten lore/world/character/history Markdown files from `Google Drive/Novel/Asset`. `AI_NOVEL_GENERATION_INSTRUCTION.md` is a writing instruction, so it is not indexed as world canon. No Item or standalone Level Markdown was present in that folder at import time; these can be added later without Java changes.

## Parsing and IDs

ATX headings `#` through `######` at column zero start a section. Fenced blocks opened by triple backticks or tildes do not start sections. Text before the first heading is a `preamble` section. A parent section contains only its own text, not descendants. `headingPath` joins ancestor headings with ` / `. `rawText` retains the source bytes decoded as UTF-8, including the heading line, whitespace and optional metadata. Normalization removes diacritics and punctuation for matching only.

`sectionId` is normalized filename stem + `::` + normalized heading path; spaces and separators become `_`. Repeated identical paths gain `~2`, `~3`, etc. Example: `extra::extra_child`. Renaming a file or heading changes its ID, so update explicit references. A new heading does not require a manually written index.

Optional metadata lives inside the relevant Markdown section, for example:

```markdown
<!-- canon: aliases=Valley of Buried Swords; requires=world::world_core; refs=entity::entity_wretch; core=true -->
```

Fields are separated by `;`, values within `aliases`, `requires`, or `refs` by comma. `requires` and `refs` take exact section IDs. `requires` includes a dependency transitively; `refs` is an informational reference. `aliases` adds exact search phrases. `core=true` prioritizes that section for an authoritative subject. Omit metadata when headings and filenames suffice. Cycles terminate through section ID deduplication; missing required refs are reported. Metadata remains in raw Markdown, so do not write secrets there.

## Selection rules

`retrieve(state, action, budget, debug, levelDisplayName)` uses committed `currentLevelKey`, `flags.entityEncounterKey`, the player `cao_minh`, and party entries explicitly marked `present=true`. Party membership alone does not imply presence. For each authoritative subject, it selects one core section from the matching world Level heading, Entity heading, or character codex filename. `core=true` wins; otherwise identity/quick profile/overview headings win, then the smallest section. The runtime supplies the Core's Level display name and rejects a same-key Markdown heading with a different name, including from supplemental search. The imported Level 0.5 mismatch was reconciled against the current Backrooms Wiki page and the runtime Level name; other mismatches remain reportable as missing Markdown mandatory sections while existing `LevelCore` context stays authoritative. This is a generic fallback, not a guarantee that an arbitrary Markdown file has a meaningful core. Level keys lacking a corresponding heading or with a mismatched title need a reconciled canon source; do not silently equate them.

Supplemental retrieval scans every file. Priority: section ID, alias phrase, heading phrase, filename phrase, then normalized word overlap. It takes at most three sections. The raw player action is a search signal, never proof of a world fact. Required dependencies follow selected sections deterministically. The index does not infer dependencies through a model. No embeddings, vector store, or external library is used.

## Budget and packet

Default Markdown budget is 5,200 estimated characters (a conservative per-section wrapper allowance), independent of the Core's existing context. Mandatory and its required dependencies are kept whole and deduplicated. Supplemental and its dependencies enter only if the whole closure fits; otherwise they are skipped. If mandatory exceeds budget, `budgetExceeded=true`, supplemental is empty, and the caller aborts AI narration after commit with a visible error. The system does not silently drop mandatory or summarize canon as a replacement. The current Core context has its own limits and may still require separate tuning.

`CanonPacket` exposes `mandatory`, `dependencies`, `supplemental`, each with section and selection reason, plus `missingMandatoryRefs`, `missingRefs`, `charCount`, `budgetExceeded`, and DEBUG `trace`. Each Section exposes source filename, heading path, deterministic ID, raw text, normalized searchable text and optional metadata. `promptText()` emits only source, path, and raw canon; trace and search scores are not included. DEBUG logs IDs/reasons and missing refs, never full Markdown text.

## Runtime integration

`MainActivity.GameBridge.submitTurn()` calls `GameCoreFacade.processRule()`, optionally validates a world proposal, then calls `completePreparedTurn()` to persist the authoritative outcome. Only after that, it requests Core contexts and calls `CanonRetriever.retrieve()` on the committed state. `GmNarrativePacket.build()` appends the bounded Markdown packet to the existing Level/Entity/Item/Character contexts and an `EpistemicView` projection. The model supplies narration, which passes `NarrationGuard`; `commitNarration()` persists the log. Query-only turns and combat paths do not use this narration path.

`GameCoreFacade` remains authoritative. `LevelCore` owns level mechanics and its existing knowledge context; core arrays no longer rotate by turn, while variation arrays can rotate. `EntityCore` owns active encounter selection; `CharacterEncounterCore` owns party and encounter state. Facts and Threads belong to `EmergentTurnEngine`; NarrativeSkeleton and CampaignSkeleton are projections, never retriever outputs. The retriever does not use hidden Facts/Threads to assert public knowledge. Existing `EpistemicView` still filters the state passed to the narrator. **Review newly added Markdown for hidden/POV material:** this initial generic retriever does not infer visibility classifications from free text.

## Tests, debugging, failure modes

From `android-apk`, run `gradle :app:testDebugUnitTest :app:assembleDebug --no-daemon` with JDK 17, Gradle and Android SDK installed. `CanonRetrieverTest` covers parser, automatic file discovery at index construction, mandatory, supplemental, aliases, dependencies, cycles, budget and determinism. Inspect DEBUG `CANON RETRIEVAL` IDs and packet fields in tests; do not log raw canon. The Android bridge logs missing or oversized mandatory and refuses model narration after the Core commit.

Known limits: only direct assets are scanned; heading-only sections can be sparse; headings in imported Markdown may be irregular; no-headings files can exceed budget as a single section; Level keys without a matching heading/name report missing Markdown mandatory; generic text matching can return imperfect results; optional `refs` are informational; adding a long `core=true` section can exceed budget. Character and world canon may include hidden knowledge, so explicit visibility design is needed before exposing secret sections to narration.

## Standard extension process

Inspect source Markdown and this contract; copy the file verbatim; run parser and retrieval tests; check the new subject's mandatory match and size; add optional in-file aliases/core/requires only if needed; verify prompts and build. Keep gameplay authority and visibility rules intact.

## RULES FOR FUTURE AI / ENGINEERS

- Read this README and tests before changing retrieval.
- Do not create per-character, per-Entity, per-Level or per-file retrievers.
- Do not create parallel JSON canon when Markdown suffices, or hardcode Markdown lore in Java.
- Do not let retrieval mutate state or AI-generated lore become canon.
- Do not silently drop mandatory sections. Report missing and overflow explicitly.
- Do not add embeddings or a vector database without evidence that the current search fails.
- Do not rewrite Core, continuity or skeleton architecture to add a canon type.
- Prefer data-driven behavior when adding Markdown; test the resulting selection and budget.
