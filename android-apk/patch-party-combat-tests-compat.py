from pathlib import Path

ROOT = Path(__file__).resolve().parent
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"
text = TEST.read_text(encoding="utf-8")

pairs = [
    (
        'val result = CombatRuntime.resolve(state, "SEARCH", "theo dõi nhịp phản công")',
        'val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")',
    ),
    (
        'val result = CombatRuntime.resolve(state, "SEARCH", "đổi góc quan sát")',
        'val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")',
    ),
]
for old, new in pairs:
    if new in text:
        continue
    if text.count(old) != 1:
        raise RuntimeError(f"Party combat test compatibility expected one anchor: {old}")
    text = text.replace(old, new, 1)

TEST.write_text(text, encoding="utf-8")
print("Party combat test compatibility applied for Silent Lullaby and Quick Step.")
