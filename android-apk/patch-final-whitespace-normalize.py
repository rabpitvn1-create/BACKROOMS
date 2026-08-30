from pathlib import Path

ROOT = Path(__file__).resolve().parent
TARGETS = (
    ROOT / "app/src/main/assets/index.html",
    ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java",
)

for path in TARGETS:
    source = path.read_text(encoding="utf-8")
    trailing_newline = source.endswith("\n")
    normalized = "\n".join(line.rstrip() for line in source.splitlines())
    if trailing_newline:
        normalized += "\n"
    path.write_text(normalized, encoding="utf-8")

print("Final generated HTML/Java trailing whitespace normalized.")
