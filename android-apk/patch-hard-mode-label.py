from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"

MODE_LABEL = "Single Player: Hard Mode"
CANON_VERSION = "NOVEL-TEXTGAME-2026-08-20-DRIVE-INTEGRATION-R06"


def replace_required(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count < 1:
        raise RuntimeError(f"{label}: expected at least 1 match, found {count}")
    return text.replace(old, new)


main = MAIN.read_text(encoding="utf-8")
index = INDEX.read_text(encoding="utf-8")

# Keep the Android-authoritative state value clean after every gameplay turn.
main = replace_required(
    main,
    '.put("mode", "ai · canon R06")',
    f'.put("mode", "{MODE_LABEL}")',
    "Android gameplay mode label",
)

# Fix the initial/new-game value produced by the Drive R06 patch.
index = replace_required(
    index,
    'mode:"local APK · canon R06",',
    f'mode:"{MODE_LABEL}",',
    "initial mode label",
)

# Migrate existing local saves immediately on app load so old labels never remain visible.
migration_anchor = f'state.canonVersion="{CANON_VERSION}";'
index = replace_required(
    index,
    migration_anchor,
    f'state.mode="{MODE_LABEL}";{migration_anchor}',
    "existing save mode migration",
)

MAIN.write_text(main, encoding="utf-8")
INDEX.write_text(index, encoding="utf-8")
print(f"Game mode label set to: {MODE_LABEL}")
