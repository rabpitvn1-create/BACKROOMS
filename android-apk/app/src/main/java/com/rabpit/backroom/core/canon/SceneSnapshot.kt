package com.rabpit.backroom.core.canon

/**
 * CR2 — normalized scene input for canon resolution (ARCHITECTURE §6.1, lowered to the
 * real inputs of this tree: legacy state JSON + raw action String + rolls JSON).
 *
 * Status discipline (hard rule of §6.1): an entity that is only named in prose is
 * [EncounterStatus.MENTIONED] (or [EncounterStatus.RUMORED] when it only appears in the
 * recent dialogue buffer). It may become [EncounterStatus.CONFIRMED_PRESENT] only from
 * state/roll evidence — never from a mention alone. `MENTIONED != CONFIRMED`.
 */
enum class EncounterStatus { CONFIRMED_PRESENT, RUMORED, MENTIONED }

data class EntityEncounter(
  val entityId: String,
  val status: EncounterStatus
)

/**
 * @param turnId derived as `TURN_<n>` from the legacy state's `turn` counter, the same
 *   derivation `GameCoreFacade.nextTurnId` uses (SceneResolver has no access to core
 *   `TurnState` from the MainActivity side).
 * @param gameStateRevision the legacy `turn` number; it is the only monotonic revision
 *   the legacy JSON carries, so it doubles as the state revision for this snapshot.
 * @param levelId record-id form `LEVEL.%02d`, matching the `LEVEL.*` records.
 * @param povActorId always `CAO_MINH_ID` in the current flow.
 * @param actorsPresent core actor ids: "cao-minh", "luc-tram", "syvial", "an-nhien".
 * @param mentionedUnconfirmed entity ids whose encounter status is not CONFIRMED_PRESENT
 *   (mirror of [entityEncounters] for consumers that only need the id set).
 * @param actionIntents affordance keys (data), e.g. "trace_analysis", "direct_threat",
 *   "field_medical", "field_food". These express eligibility only (CAN_DO), never that
 *   an actor will act (WILL_DO) — see ARCHITECTURE §6.3.
 * @param objectsInFocus item record ids (`ITEM.*`) from the POV inventory/equipment and
 *   items named in the action text.
 * @param knownFacts `DISC.*` ids from `flags.storyContinuity.discoveries`.
 * @param knownByByFact factId -> actor ids that know it, from
 *   `flags.storyContinuity.knowledge[].knownBy` (the `known_by` data gate 0 needs).
 * @param storyThreadsActive ids of `flags.storyContinuity.unresolvedThreads` whose
 *   status is "open" (or unset).
 * @param rollsLocked true when the rolls JSON parsed and carries a `turn` key, i.e. the
 *   rolls were produced and locked by `MainActivity.makeGameplayRolls` before the prompt.
 */
data class SceneSnapshot(
  val turnId: String,
  val gameStateRevision: Int,
  val universeId: String = CanonUniverse.BACKROOMS_MAIN,
  val levelId: String,
  val povActorId: String,
  val actorsPresent: Set<String>,
  val entityEncounters: List<EntityEncounter>,
  val mentionedUnconfirmed: Set<String>,
  val actionIntents: Set<String>,
  val actionTextNormalized: String,
  val objectsInFocus: Set<String>,
  val knownFacts: Set<String>,
  val knownByByFact: Map<String, Set<String>>,
  val storyThreadsActive: Set<String>,
  val rollsLocked: Boolean,
  val isMeta: Boolean,
  val locationNodeId: String? = null
)
