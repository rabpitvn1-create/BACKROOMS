# M1b.2 native-bound adapter (#499)

`NativeObservationSource.VerifiedEvent.read` is the authoritative read entry point. It reads CompanionSlotStore under inspectNative, reconstructs the native WAIT batch from the validated reservation and exact input, and selects an event by exact identity. It accepts no caller facts, event JSON, claimed revision, scene or eligibility grant. The scoped snapshot is copied and never exposed mutably. A named journey stop takes precedence over its shared WorldNodeId.

Projection checks required fields, exact numeric/boolean types, nonnegative time and bounded scene/identity strings; absent values are denied rather than defaulted to empty/zero. Combat entity enumeration is denied because no native per-entity visibility producer exists. A field whitelist alone never grants perception or payload access. Unsupported events are denied. INFERRED/TOLD are not manufactured: TOLD awaits a real native communication event.

The adapter uses only the facts actually present in Core. DEAD/MISSING/SEPARATED can deny; ACTIVE, party membership and HP do not grant senses. Scene membership, reach, consciousness, visibility and audibility remain UNKNOWN wherever Core has no authoritative field. This is an intentional safe partial producer, not a claim that positive sensory modeling has been implemented. No new seed or sensory boolean field is adopted in this change.

`candidates` checks bounded/distinct actors, calls the existing exposure policy and returns immutable candidates; with the current Core fields it returns none. It consumes no RNG and performs no storage/UI/provider write. Persistence remains #500.

Validation: malformed/missing payload and undisclosed combat entity tests; real Android slot/batch fixture checks exact request/input/revision/event, scoped scene, immutable projection, no invented perception and no candidates from party membership. Exact-head workflow reproduces 46 patches, full Core/APK and API24/API35 tests. CI completion is recorded on the issue before acceptance.
