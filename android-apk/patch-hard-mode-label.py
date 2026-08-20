from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"

MODE_LABEL = "Single Player: Hard Mode"
CANON_VERSION = "NOVEL-TEXTGAME-2026-08-20-DRIVE-INTEGRATION-R06"


def replace_first_available(text: str, candidates: list[str], new: str, label: str) -> str:
    for old in candidates:
        if old in text:
            return text.replace(old, new, 1)
    raise RuntimeError(f"{label}: none of the supported anchors were found")


main = MAIN.read_text(encoding="utf-8")
index = INDEX.read_text(encoding="utf-8")

# The orchestrator may use either the legacy R06 label or the routed-ops label.
# Keep this patch idempotent so rebuilding an already-patched tree is harmless.
desired_main = f'.put("mode", "{MODE_LABEL}")'
if desired_main not in main:
    main = replace_first_available(
        main,
        [
            '.put("mode", "ai · canon R06 · routed ops")',
            '.put("mode", "ai · canon R06")',
            '.put("mode", "local APK · canon R06")',
        ],
        desired_main,
        "Android gameplay mode label",
    )

# Initial/new-game state. The Drive patch owns this value before this patch runs.
desired_initial = f'mode:"{MODE_LABEL}",'
if desired_initial not in index:
    index = replace_first_available(
        index,
        [
            'mode:"local APK · canon R06",',
            'mode:"ai · canon R06 · routed ops",',
            'mode:"ai · canon R06",',
            'mode:"local APK",',
        ],
        desired_initial,
        "initial mode label",
    )

# Existing saves: inject migration exactly once immediately before the canon-version migration.
migration_anchor = f'state.canonVersion="{CANON_VERSION}";'
desired_migration = f'state.mode="{MODE_LABEL}";{migration_anchor}'
if desired_migration not in index:
    if migration_anchor not in index:
        raise RuntimeError("existing save mode migration: canon-version anchor not found")
    index = index.replace(migration_anchor, desired_migration, 1)

MAIN.write_text(main, encoding="utf-8")
INDEX.write_text(index, encoding="utf-8")
print(f"Game mode label set to: {MODE_LABEL}")
