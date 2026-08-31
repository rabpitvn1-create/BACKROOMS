package com.rabpit.backroom.core

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
