from pathlib import Path


ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
CATALOG = CORE / "CompanionSkillCatalog.kt"
DETAIL_JSON = CORE / "CharacterDetailJson.kt"
INDEX = ROOT / "app/src/main/assets/index.html"
KAI_TEST = TESTS / "KaiDevilBlessingTest.kt"
TEST = TESTS / "PassiveSkillVisibilityTest.kt"


catalog = CATALOG.read_text(encoding="utf-8")

# Issue #131: Devil Blessing is a real runtime passive, but the previous final
# patch deliberately removed its catalog row. The Character Skill sheet reads
# CompanionSkillCatalog, so that made the passive impossible to see even though
# its gameplay effect remained active. Append it to Kai's existing list so old
# order-sensitive regressions and player-facing ordering remain stable.
devil_blessing = '    s("Devil Blessing", "PASSIVE", "Khi Kai đang ACTIVE trong Party và giao tranh đang diễn ra", "Các đồng đội ACTIVE trong Party nhận thêm 5% Tấn Công, Phòng Thủ, Né tránh và Max HP. Kai không nhận hiệu ứng từ chính kỹ năng này.", "Hiệu ứng chỉ áp dụng khi Kai và đồng đội mục tiêu vẫn còn khả năng chiến đấu.")'
if 's("Devil Blessing", "PASSIVE"' not in catalog:
    anchor = "  private val kai = listOf(\n"
    if catalog.count(anchor) != 1:
        raise RuntimeError(f"Issue #131 Kai skill catalog anchor: expected one, found {catalog.count(anchor)}")
    kai_start = catalog.index(anchor)
    kai_end = catalog.index("\n  )", kai_start)
    kai_body = catalog[kai_start:kai_end]
    separator = "\n" if kai_body.rstrip().endswith(",") else ",\n"
    catalog = catalog[:kai_end] + separator + devil_blessing + catalog[kai_end:]

CATALOG.write_text(catalog, encoding="utf-8")

# patch-kai-devil-blessing.py previously encoded the intentional hidden state as
# a regression. Replace only that obsolete expectation; the runtime blessing
# behavior tests remain untouched.
kai_test = KAI_TEST.read_text(encoding="utf-8")
hidden_test = '''  @Test fun blessingIsHiddenFromSkillTable() {
    val source = File("src/main/java/com/rabpit/backroom/core/CompanionSkillCatalog.kt").readText()
    assertFalse(source.contains("s(\\\"Devil Blessing\\\""))
    assertFalse(source.contains("s(\\\"DEVIL BLESSING\\\""))
  }
'''
visible_test = '''  @Test fun blessingIsVisibleAsPassiveSkill() {
    val source = File("src/main/java/com/rabpit/backroom/core/CompanionSkillCatalog.kt").readText()
    assertEquals(1, source.split("s(\\\"Devil Blessing\\\", \\\"PASSIVE\\\"").size - 1)
    assertFalse(source.contains("s(\\\"DEVIL BLESSING\\\""))
  }
'''
if "blessingIsVisibleAsPassiveSkill" not in kai_test:
    if kai_test.count(hidden_test) != 1:
        raise RuntimeError("Issue #131 obsolete hidden Devil Blessing regression was not found exactly once")
    kai_test = kai_test.replace(hidden_test, visible_test, 1)
KAI_TEST.write_text(kai_test, encoding="utf-8")

# Regression coverage checks both the catalog and the two projection layers used
# by the WebView. Existing passives for Iris, Syvial, An Nhien and Lucia must not
# silently disappear while fixing Kai.
TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PassiveSkillVisibilityTest {
  @Test fun passiveSkillsAreExposedForEveryPlayablePartyCharacter() {
    val expected = linkedMapOf(
      KAI_ID to "Devil Blessing",
      IRIS_ID to "ARGUS Terrain Read",
      SYVIAL_ID to "Lucifer Core",
      AN_NHIEN_ID to "Có Gì Đó Sai Sai",
      LUCIA_ID to "Trinh sát chiến trường"
    )

    expected.forEach { (characterId, passiveName) ->
      val passives = CompanionSkillCatalog.forCharacter(characterId).filter { it.kind == "PASSIVE" }
      assertTrue("$characterId must expose at least one PASSIVE skill", passives.isNotEmpty())
      assertTrue("$characterId is missing PASSIVE skill $passiveName", passives.any { it.name == passiveName })
    }

    val kaiBlessing = CompanionSkillCatalog.forCharacter(KAI_ID).single { it.name == "Devil Blessing" }
    assertEquals("PASSIVE", kaiBlessing.kind)
    assertTrue(kaiBlessing.effect.contains("5%"))
    assertTrue(kaiBlessing.effect.contains("Kai không nhận"))
  }

  @Test fun characterDetailProjectionAndSkillSheetKeepPassiveRows() {
    val detail = File("src/main/java/com/rabpit/backroom/core/CharacterDetailJson.kt").readText()
    assertTrue(detail.contains("CompanionSkillCatalog.forCharacter(c.id).forEach"))
    assertFalse(detail.contains("filter { it.kind != \"PASSIVE\" }"))

    val html = File("src/main/assets/index.html").readText()
    assertTrue(html.contains("function skills(){"))
    assertTrue(html.contains("current.map(skill=>"))
    assertTrue(html.contains("skill.kind||'SKILL'"))
    assertFalse(html.contains("filter(skill=>skill.kind!=='PASSIVE')"))
  }
}
''', encoding="utf-8")

required = {
    CATALOG: ('s("Devil Blessing", "PASSIVE"',),
    DETAIL_JSON: ("CompanionSkillCatalog.forCharacter(c.id).forEach",),
    INDEX: ("current.map(skill=>", "skill.kind||'SKILL'"),
    KAI_TEST: ("blessingIsVisibleAsPassiveSkill",),
    TEST: ("passiveSkillsAreExposedForEveryPlayablePartyCharacter", "Devil Blessing"),
}
for path, markers in required.items():
    source = path.read_text(encoding="utf-8")
    for marker in markers:
        if marker not in source:
            raise RuntimeError(f"Issue #131 passive-skill contract {marker!r} missing in {path.name}")

print("Issue #131 applied: PASSIVE skills remain visible in Character Skill tables, including Kai's Devil Blessing.")
