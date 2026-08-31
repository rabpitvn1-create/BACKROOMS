from pathlib import Path

ROOT = Path(__file__).resolve().parent
CATALOG = ROOT / "app/src/main/java/com/rabpit/backroom/core/CompanionSkillCatalog.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CompanionSkillCatalogTest.kt"

catalog = CATALOG.read_text(encoding="utf-8")

# Player-facing prose must be Vietnamese. Canonical proper names such as
# Devil Trigger, Spatial Shift, GodKiller, ARGUS, SRU-SG and M4A1 stay intact.
# Skill kind identifiers are runtime data and are deliberately not rewritten.
replacements = (
    ("Base DMG", "sát thương cơ bản"),
    ("DMG vũ khí", "sát thương vũ khí"),
    ("DMG", "sát thương"),
    ("Max HP", "Máu tối đa"),
    ("HP", "Máu"),
    ("Party", "đội"),
    ("Entity", "Thực thể"),
    ("SEARCH", "TÌM KIẾM"),
    ("Exit", "lối thoát"),
    ("Mana", "Ma lực"),
    ("Game Master", "Quản trò"),
    ("canon", "nguyên tác"),
    ("boss", "trùm"),
    ("ACTIVE", "đang hoạt động"),
)

# Only skill definition rows are player-facing. Keep compatibility comments and
# other non-UI markers byte-for-byte intact because final runtime contracts may
# intentionally probe those legacy markers.
localized_lines = []
for line in catalog.splitlines(keepends=True):
    if '    s("' in line:
        for old, new in replacements:
            line = line.replace(old, new)
    localized_lines.append(line)
catalog = ''.join(localized_lines)
CATALOG.write_text(catalog, encoding="utf-8")

# Tighten and align existing localization regressions with the final wording.
test = TEST.read_text(encoding="utf-8")
test = test.replace(
    'org.junit.Assert.assertTrue(all.any { it.effect.contains("DMG") })',
    'org.junit.Assert.assertFalse(all.any { listOfNotNull(it.trigger, it.effect, it.note).joinToString(" ").contains("DMG") })',
)
test = test.replace(
    'org.junit.Assert.assertTrue(all.any { it.effect.contains("HP") })',
    'org.junit.Assert.assertFalse(all.any { listOfNotNull(it.trigger, it.effect, it.note).joinToString(" ").contains("HP") })',
)
test = test.replace(
    'org.junit.Assert.assertTrue(skill.effect.contains("30 + Base DMG"))',
    'org.junit.Assert.assertTrue(skill.effect.contains("30 + sát thương cơ bản"))',
)
test = test.replace(
    'org.junit.Assert.assertTrue(skill.effect.contains("Entity Evasion"))',
    'org.junit.Assert.assertTrue(skill.effect.contains("Thực thể Evasion"))',
)
test = test.replace(
    'org.junit.Assert.assertTrue(skill.effect.contains("Base DMG +5%"))',
    'org.junit.Assert.assertTrue(skill.effect.contains("sát thương cơ bản +5%"))',
)

regression = r'''
  @org.junit.Test fun playerFacingSkillDescriptionsAreFullyVietnamese() {
    val all = listOf(KAI_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID, LUCIA_ID)
      .flatMap(CompanionSkillCatalog::forCharacter)
    val forbidden = listOf(
      "DMG", "HP", "Party", "Entity", "SEARCH", "Exit", "Mana",
      "Game Master", " canon", " boss", "ACTIVE", "Base DMG"
    )
    all.forEach { skill ->
      val prose = listOfNotNull(skill.trigger, skill.effect, skill.note).joinToString(" ")
      forbidden.forEach { token ->
        org.junit.Assert.assertFalse(
          "${skill.name} still contains mixed-English description token: $token | $prose",
          prose.contains(token, ignoreCase = false)
        )
      }
    }
  }
'''
if "playerFacingSkillDescriptionsAreFullyVietnamese" not in test:
    close = test.rfind("\n}")
    if close < 0:
        raise RuntimeError("Skill catalog test class closing brace not found")
    test = test[:close] + regression + test[close:]
TEST.write_text(test, encoding="utf-8")

# Fail the patch itself if a mixed-English token survives player-facing fields.
for line in catalog.splitlines():
    if '    s("' not in line:
        continue
    parts = line.split('"')
    prose = ' '.join(parts[5:]) if len(parts) >= 6 else line
    for token in ("DMG", "HP", "Party", "Entity", "SEARCH", "Exit", "Mana", "Game Master", " canon", " boss", "ACTIVE", "Base DMG"):
        if token in prose:
            raise RuntimeError(f"Mixed-English skill description token remains: {token}: {line}")

print("Player-facing skill descriptions finalized in Vietnamese; canonical proper names preserved.")
