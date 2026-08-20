from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"

MODE_LABEL = "Single Player: Hard Mode"
CANON_VERSION = "NOVEL-TEXTGAME-2026-08-20-DRIVE-INTEGRATION-R06"


main = MAIN.read_text(encoding="utf-8")
index = INDEX.read_text(encoding="utf-8")

# Runtime mode is rewritten structurally because earlier orchestration patches are
# allowed to change their internal diagnostic label. Do not couple this patch to
# any exact intermediate mode string.
desired_main = f'.put("mode", "{MODE_LABEL}")'
if desired_main not in main:
    main, count = re.subn(
        r'\.put\("mode",\s*"[^"]*"\)',
        desired_main,
        main,
        count=1,
    )
    if count != 1:
        raise RuntimeError(f"Android gameplay mode label: expected exactly 1 structural mode setter, found {count}")

# Initial/new-game state. Keep this structural and idempotent as well.
desired_initial = f'mode:"{MODE_LABEL}",'
if desired_initial not in index:
    index, count = re.subn(
        r'mode:"[^"]*",',
        desired_initial,
        index,
        count=1,
    )
    if count != 1:
        raise RuntimeError(f"initial mode label: expected exactly 1 state mode field, found {count}")

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
