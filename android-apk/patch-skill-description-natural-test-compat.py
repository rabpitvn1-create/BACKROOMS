from pathlib import Path

ROOT = Path(__file__).resolve().parent
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CompanionSkillCatalogTest.kt"

test = TEST.read_text(encoding="utf-8")
replacements = (
    (
        'org.junit.Assert.assertTrue(skill.trigger.contains("+5 điểm %"))',
        'org.junit.Assert.assertTrue(skill.trigger.contains("5 điểm phần trăm"))',
    ),
    (
        'org.junit.Assert.assertTrue(skill.trigger.contains("3 điểm %"))',
        'org.junit.Assert.assertTrue(skill.trigger.contains("3 điểm phần trăm"))',
    ),
)
for old, new in replacements:
    if new in test:
        continue
    count = test.count(old)
    if count != 1:
        raise RuntimeError(f"Issue #126 test compatibility: expected one assertion anchor, found {count}: {old}")
    test = test.replace(old, new, 1)

TEST.write_text(test, encoding="utf-8")
print("Issue #126 regression expectations aligned with natural Vietnamese wording.")
