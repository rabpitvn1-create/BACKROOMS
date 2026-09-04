# WorldDirector V2 distillation result

This experiment distilled privacy-safe pacing decisions from six Gemini teacher workers plus Haku into an artifact-only LiteRT V2 candidate. Production runtime/model assets were intentionally left unchanged because the candidate did not satisfy promotion gates.

## Data contract

- deterministic simulation: 115,200 rows / 960 sessions / 120 turns per session
- unique V1 contexts: 413
- unique V2 contexts: 88,223
- V2 expansion versus V1: 213.615x
- V2 features add only observable pacing/history signals; hidden Level/zone/evidence IDs, escape/puzzle solutions, Entity/item identity, inventory, player text, private canon, and provider secrets remain excluded

## Teachers

Gemini V2 pack:
- 647 exact-ID labels reconstructed against the deterministic context universe
- ENTITY_PRESSURE 228, ITEM_OPPORTUNITY 134, MAZE_PRESSURE 76, NONE 209

Best retained Haku overlap run:
- 334 valid labels from 7 requests
- estimated Haku spend for this run: 223.4641 VND
- Gemini/Haku overlap: 334
- agreements: 107
- disagreements: 227
- agreement rate: 0.320359
- disagreements were excluded from training rather than silently resolved

The later micro-batch finalization also completed successfully with 200/200 Haku labels and zero failures, but its candidate was weaker, so it is retained only as secondary evidence.

## Best candidate

- selected training policy: `teacher_seed_anchor`
- feature count: 16,384
- model size: 66,800 bytes
- accepted teacher rows: 416 (332 train / 84 test)
- V1 baseline teacher-test accuracy: 0.523810
- V2 teacher-test accuracy: 0.738095
- gain over V1: +0.214286
- V2 seed-test accuracy: 1.000000
- high-confidence coverage: 0.821429
- accepted accuracy: 0.768116
- per-label recall: NONE 0.848485, MAZE_PRESSURE 0.333333, ENTITY_PRESSURE 0.777778, ITEM_OPPORTUNITY 0.611111
- agreement-test accuracy: 0.576923
- `promotionEligible`: false

The candidate improves substantially over V1 on the teacher test while preserving the deterministic seed anchor, but it misses the production gates, especially accepted accuracy, MAZE recall, and cross-teacher agreement accuracy. It must not replace the current production WorldDirector model.

## Evidence

- best recovery artifact: GitHub Actions artifact `9923021439`, digest `sha256:24ea8a414886ff651402618ff237e03b5061f2bac172dceadcb55f92bfe696e4`
- final micro-batch artifact: GitHub Actions artifact `9922983691`, digest `sha256:2588c8fb03d27da24d4fecd992ac5e7bc7fcac322381087711d19bd6756d3056`
- both artifacts were configured for 30-day retention

Automatic paid teacher workflows were removed after the one-shot experiment so synchronizing or merging this branch cannot spend additional Haku quota.
