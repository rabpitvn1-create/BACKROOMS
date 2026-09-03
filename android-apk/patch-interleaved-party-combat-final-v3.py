from pathlib import Path

ROOT = Path(__file__).resolve().parent
TEST = ROOT / "test-combat-action-protocol.cjs"

text = TEST.read_text(encoding="utf-8")
old = "fs.readFileSync('app/src/main/assets/index.html', 'utf8')"
new = "fs.readFileSync(__dirname + '/app/src/main/assets/index.html', 'utf8')"

if new not in text:
    if text.count(old) != 1:
        raise RuntimeError(f"Combat protocol regression path: expected one relative-path anchor, found {text.count(old)}")
    text = text.replace(old, new, 1)

TEST.write_text(text, encoding="utf-8")

if old in text or new not in text:
    raise RuntimeError("Combat protocol regression path was not finalized")

print("Interleaved combat V3 applied: Node protocol regression resolves final HTML relative to the test file, independent of CI cwd.")
