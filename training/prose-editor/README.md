# Local Prose Editor Training

This directory stores training material for the local prose-only editor.

The editor is downstream of the Game Master. It must not decide or repair canon, codex, continuity, event results, speaker identity, relationships, knowledge boundaries, or other game truth. Its only job is to preserve meaning while improving Vietnamese prose when improvement is actually necessary.

## Dataset policy

- PASS examples teach the model to leave already-good prose unchanged.
- LIGHT_EDIT examples permit only small surface edits.
- REWRITE examples permit structural prose repair while preserving all facts and dialogue content.
- Character names, item names, numbers, directions, negation, speaker identity, quoted dialogue and action results must remain unchanged.
- Held-out benchmarks must never be copied into training data.

## Pilot v1

`data/editor_dataset_v1_240_train.jsonl` contains 240 samples:

- 96 PASS (40%)
- 72 LIGHT_EDIT (30%)
- 72 REWRITE (30%)

The V6 held-out benchmark is intentionally not stored in the training data directory.
