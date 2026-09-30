# Narrative Architecture v1 — FROZEN

Status: **DONE / FROZEN**

This is the authority boundary for Narrative Architecture v1. Bug fixes and regression fixes
may repair behavior inside this boundary, but content/features must not bypass or silently widen it.

## Frozen contracts

1. **CampaignSkeleton and NarrativeSkeleton are separate projections.**
   - `CampaignSkeleton` owns coarse campaign pressure used by Director category weighting.
   - `NarrativeSkeleton` owns long-horizon narrative continuity and keyRef attention.
   - Neither projection owns authoritative gameplay state.

2. **NarrativeSkeleton is derived, read-only state.**
   - Inputs are committed CurrentState, HistoricalFacts, Threads and ArchivedThreadResidue.
   - It may summarize narrative relevance and expose advisory attention hints.
   - It must not create DomainEvents, Facts, Threads, candidates, outcomes, eligibility,
     schedules, chapter/scene/beat progression, phase gates, plot cursors or fixed endings.

3. **Director is WEIGHT_ONLY.**
   - Candidate builders and Scheduler decide which candidates are eligible.
   - Director may only weight candidates already present in the eligible pool.
   - Narrative keyRef weighting may only modify an already-eligible candidate whose keyRefs
     overlap advisory NarrativeSkeleton hints.
   - Empty eligible pool must still resolve to NONE.

4. **Narrative relevance is required.**
   - Mechanical/local facts do not enter the long-horizon narrative skeleton merely because
     they were committed.
   - Relationships, unresolved route/mystery threads, relevant persistent consequences,
     archived residue and explicitly committed ending possibilities may enter when supported
     by committed data.

5. **Verification gate.**
   - Narrative Architecture v1 changes require unit/integration coverage.
   - The repository build gate remains `:app:testDebugUnitTest :app:assembleDebug`.

## Allowed after freeze

- Regression fixes that preserve the contracts above.
- Gameplay smoke tests and observability.
- Content and features implemented through existing Canon/Engine authority.
- A deliberate Architecture v2 change only when explicitly requested and tested as such.
