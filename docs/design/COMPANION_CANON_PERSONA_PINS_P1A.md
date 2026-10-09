# P1a: immutable canon persona source pins

Status: isolated descriptor/verification slice; no runtime brain or activation.
Parent: #476. Actor sources were read at PR #495 head
b95c1673d25d33ae2de04e3d7c7cc2c3b0f1286e.

## Source authority

| Actor | Current source | Source SHA-256 |
| --- | --- | --- |
| cao_minh | CAO_MINH_CODEX.md R17 | c78ba2978c4635b83c9cc64385a6e6b3ebeaf57dbfa75f576ff0a84dc4b5e61c |
| luc_tram | LUC_TRAM_CODEX.md R05 | 26e6a407f4dcccdf0e74c3f855d577609a803eacf524af4679cbf3752758f100 |

Lục Trầm's READ FIRST router is still named R04. It is not the whole Codex's
current revision. R05 dialogue/master overrides must remain effective alongside
the R04 source/claim firewall. Cao Minh's R16 life/principle override remains
effective under R17; do not restore a superseded universal nonviolence rule.

CompanionCanonPersonaRegistry uses fixed actor-owned asset paths and verifies
exact bytes on every load before returning an immutable descriptor. Missing,
edited, oversized, unsupported or another actor's source fails explicitly.
There is no fallback to an older persona, another character or a generic profile.
The deliberate cao_minh -> CHAR.KAI namespace is preserved; kai is not accepted
as the runtime actor ID by this new API.

## Descriptor boundary

The descriptor contains source pins and role-specific canonical reference IDs.
It contains no full Codex text, campaign events, beliefs, goals, relationship
state, equipment mutations, new capabilities or observed memories.
Collections are copied and unmodifiable. No provider, bridge, database write,
GameState mutation or GM KnowledgeContextEngine change is introduced.

These descriptors are source references, not permission to paste complete
sections into an actor prompt. A later private-context adapter must select only
reviewed self-canon persona/voice instructions and apply all source ownership,
continuity and knowledge locks. In particular:
- Writer secrets and another actor's private information remain excluded.
- Historical POV, hypothetical sacrifice scenarios and voice samples do not
  initialize campaign episodes, beliefs, goals or relationships.
- Current pronouns, injuries, presence and relationships come from validated
  campaign continuity, never a generic romance or reunion assumption.
- Backrooms knowledge requires the actor's valid provenance.
- Character-specific ability/equipment rules remain existing Core authority.

Reference IDs for voice samples are knowledge-lock provenance, not trait content
or retrieval requests. Immutable persona source pins remain outside future brain
reducer writable fields. A storage verifier must reconstruct/validate the pinned
registry descriptor; an arbitrary caller-built descriptor is not authorization.

## Validation and sequencing

At authoring, M1a Core/APK/generated parity, all ten exposure tests, Canon and
API24 SQLite/crash/1k checks have passed; API35 is still running. The owner asked
to proceed when the checked foundation is stable. This independent read-only
persona slice proceeds without claiming the remaining CI or M1b complete.

Eight tests load actual packaged post-chain sources, preserve namespaces/current
overrides, enforce fixed source paths, reject unknown/legacy actors and edited or
cross-actor source, bound input, propagate asset errors, preserve collection
immutability and revalidate after earlier successful loads.

CI must reproduce the full chain and check exact generated-source parity, full
Core tests/APK, real SQLite/crash regressions and the 1k backend regression.
No local compile is claimed while the terminal is offline.

This independent persona foundation does not declare M1b or S2.5 complete.
Runtime observation mapping/persistence, durable memory, genesis brain pins and
reducer/private-context qualification remain mandatory before decisions or UI.
No companion activation, merge or release is performed.
