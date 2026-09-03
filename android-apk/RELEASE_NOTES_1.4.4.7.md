# BACKROOMS 1.4.4.7 — AP Skill Activation Authority

BACKROOMS 1.4.4.7 fixes combat skill activation after the Party AP migration. Active combat skills are now explicitly selected and paid for with Party AP instead of being activated by legacy percentage or turn-interval proc gates.

## Highlights

- **AP is the authoritative skill trigger**
  - Normal combat skills cost 2 AP.
  - Ultimate skills cost 3 AP.
  - Active skills no longer activate from percentage or combat-turn proc gates.

- **Skill effects execute through authoritative combat state**
  - Existing damage, status, buff, debuff, healing, and special-effect bodies remain in `CombatRuntime`.
  - Selected skills execute against authoritative Entity and Party state rather than consuming AP without applying their effects.
  - A selected skill does not also grant a free basic attack.

- **Safe rejection semantics**
  - Insufficient AP rejects the skill without spending AP, advancing the Party actor, or allowing an Entity response tick.
  - Unmet skill prerequisites are rejected before the action commits.

- **Combat RNG scope is preserved**
  - Hit, accuracy, and evasion RNG remain part of combat resolution.
  - Only RNG that decided whether an active skill itself activated has been retired.
  - Passive and STATE behavior remains automatic where defined by the existing runtime.

- **Regression coverage**
  - AP costs are verified for normal and ultimate skills.
  - Damage and status/effect application are exercised through manual AP actions.
  - Ordinary attacks are checked to ensure active skills do not auto-proc.
  - Runtime patch-chain, Kotlin tests, debug APK build, and packaged APK contracts are required before publication.

## Verification

The release workflow verifies:

- Android source version is exactly `versionCode 106` / `versionName '1.4.4.7'`.
- The full Android runtime patch chain applies cleanly.
- Final runtime and provider-routing contracts pass.
- Kotlin unit tests pass.
- A fresh debug APK builds successfully.
- The packaged APK reports `versionName=1.4.4.7` and `versionCode=106`.
- The published release asset is downloaded again and verified byte-for-byte by SHA-256 before the workflow completes.
