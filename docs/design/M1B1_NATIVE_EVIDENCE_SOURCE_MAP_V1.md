# M1b.1: Native observation evidence — source map V1 (rev.2)

Status: contract-preparation slice; **runtime observation production remains disabled**.
Parent: #476, issue #498. First pass: Muse A.I; reviewer: Ponytail.
Base branch: `test/companion-routine-benchmark-300` @ `4ec2ae6f67ddf8b0dc333a8240ea529000a775c6`
(stacks #495 M1a @ `b95c167` → #496 P1a @ `fbfaccd` → #497 routine300 @ `4ec2ae6`).
Inspected main: `a1b6e2f1c12620dc4b8bb80529cbe58b15e81505`.
Rev.2 addresses Ponytail review R1–R6 on PR #519 (comment 6078761454).

## Method and provenance tiers

"Effective source" = the post-patch-chain working tree. The 46-patch chain was
**not reproduced** in this environment. Every claim below carries one tier:

- [SOURCE-TRACED raw] — read from the pre-chain tree at the base head (file:line).
- [POST-CHAIN TRACED] — derived by reading a named patch script (script:line);
  the script's transform is quoted, the post-chain bytes are not.
- [VERIFIED ARTIFACT] — CI artifact or generated test with exact provenance.
  **None available in this environment** (no CI artifacts fetched); this tier is
  empty by construction here, not by omission.
- [NOT VERIFIED] — post-chain file bytes not reproduced; absence conclusions
  drawn from raw alone are marked as such and must not be treated as proven.

A patch-script grep over all 111 `android-apk/patch-*.py` for
`conscious|inReach|in_reach|sceneMember` (perception senses, excluding UI
`View.VISIBLE` hits) returns **zero** perception-producer matches
[POST-CHAIN TRACED]: no chain script adds a native reach/consciousness/
visibility/audibility/scene-membership producer. One script DOES generate code
with a GENERATED marker: `patch-companion-native-roll-capture.py:86`
(`/** GENERATED from final MainActivity policy. … */`) — the rev.1 claim that
"all companion files are hand-written" was wrong and is retracted.

## Identity (R1 correction)

Rev.1 described a dual runtime identity (Core `KAI_ID="kai"` vs companion
`"cao_minh"`). **That was raw pre-chain and is wrong for the effective runtime.**
[POST-CHAIN TRACED] `android-apk/patch-cao-minh-identity-overlay.py:34` rewrites
whole-quoted `"kai"`→`"cao_minh"`; `:57` asserts `const val KAI_ID = "cao_minh"`
post-chain; the script-generated `CaoMinhIdentityTest` asserts
`assertEquals("cao_minh", KAI_ID)` and `assertFalse(state.characters.containsKey("kai"))`
(`:77–83`). **Effective runtime: `KAI_ID = "cao_minh"`, actorId = `"cao_minh"` —
single identity.** `KAI_ID` is a retained symbol; `CHAR.KAI.*` is a retained
knowledge namespace (P1a). No alias/dual runtime identity is constructed here.
The raw value `"kai"` below is marked [PRE-CHAIN/RETIRED] wherever it appears.

## Evidence source table

| # | Evidence dimension | Source (tier) | What it proves | What is UNKNOWN / not verified |
| --- | --- | --- | --- | --- |
| 1 | Actor existence | `core/GameState.kt` — `CharacterState`, `GameState.characters` [SOURCE-TRACED raw] | An actor record with stable `id` exists in a decoded snapshot. | Whether the actor is at the event's scene. Existence ≠ scene presence. |
| 2 | Presence flag | `core/GameState.kt:41` — `CharacterPresence { ACTIVE, SEPARATED, MISSING, DEAD }` [SOURCE-TRACED raw] | The stored presence enum value per snapshot (codec-serialized as enum name). | Mapping from this flag to "can perceive". Never use ACTIVE (or HP>0) as proof of consciousness/seeing/hearing (issue #498). |
| 3 | Scene keys | `core/GameState.kt` — `GameState.world: Map<String,String>` (`location`, `journeyStopKey`, `worldNodeId`, `levelJson`, `flagsJson`) [SOURCE-TRACED raw]; `CompanionWaitAuthorizer.preflight()` consistency checks [SOURCE-TRACED raw] | Scene/location keys exist per snapshot; WAIT preflight checks their consistency vs `FeaturedJourneyRoutes`. | **Actor↔scene membership**: no registry maps actors to scenes [SOURCE-TRACED raw + POST-CHAIN TRACED (no script adds one)]. Same node/party insufficient (M1a). |
| 4 | Reach | — | — | [UNKNOWN] No Core model [SOURCE-TRACED raw + POST-CHAIN TRACED]. Only `NativeFacts.inReach: Fact` (policy-shaped, no adapter). |
| 5 | Consciousness | `core/GameState.kt:68` — `PhysiologyState`; `PhysiologyEngine.execute()` [SOURCE-TRACED raw] | Physiology counters exist and update via `PhysiologyCommand`s. | [UNKNOWN] Consciousness itself [SOURCE-TRACED raw + POST-CHAIN TRACED]. `minutesSinceAwake` is not a consciousness proof. Only `NativeFacts.conscious: Fact` (policy-shaped, no adapter). |
| 6 | Visibility / audibility | — | — | [UNKNOWN] No Core model [SOURCE-TRACED raw + POST-CHAIN TRACED]. Only `NativeFacts.visible`/`audible: Fact` (policy-shaped, no adapter). |
| 7 | Event position & timing | `TurnState`, `GameTimeState` [SOURCE-TRACED raw]; WAIT envelope `companion_wait_capture.v2` (revision, turn) [SOURCE-TRACED raw] | Turn ordering + subjective elapsed time; WAIT capture binds revision+turn. | Wall-clock vs Core-turn across sessions; event→turn binding for non-WAIT kinds [NOT VERIFIED]. |
| 8 | Public projection | `CompanionWaitCapture.project()` (`:116–122`) [SOURCE-TRACED raw] | Projection exposes turn, levelJson, flagsJson, and party members **excluding KAI_ID** — i.e. in the effective runtime, the *other* party members besides the protagonist `cao_minh` (id, name, presence). | Projection ≠ observation. Grants no SEEN/HEARD and no payload read right. |
| 9 | Actual speaker / listener | — | — | [UNKNOWN] No native communication-event type [SOURCE-TRACED raw + POST-CHAIN TRACED]. TOLD-per-Rule-Table needs one; contract P-C proposes it (not implemented). |
| 10 | Payload & outcome binding | `CompanionWaitCapture` envelope digests; `CompanionWaitBatch.verify()` exact-match rebuild; `CompanionRollTape.verifyRoute()` [SOURCE-TRACED raw] | Byte-level outcome↔snapshot binding; `replay()` needs exact envelope equality; graph edge alone never authorizes completion. | Payload **read rights**: `Eligible` grants neither payload access nor truth (policy docstring). Defined in producer contract §2b, not in code. |
| 11 | Post-chain drift | `apply-release-patch-chain.py` (46 scripts) [SOURCE-TRACED raw] | The chain definition exists and is ordered. | Byte-level post-chain contents of the cited files [NOT VERIFIED] — chain not reproduced here. Re-verify parity before any runtime PR. |
