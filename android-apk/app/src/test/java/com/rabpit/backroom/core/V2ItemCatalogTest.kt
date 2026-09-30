package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V2ItemCatalogTest {
  @Test fun portsExactV2ConsumablePools() {
    assertEquals(
      listOf("almond-water","bandage","first-aid-kit","lavie-water","coconut-water","banh-mi-thit","hot-soy-milk","com-tam-suon-bi-cha"),
      V2ItemCatalog.chestPool
    )
    assertEquals(listOf("almond-water","bandage"), V2ItemCatalog.entityPool)
  }

  @Test fun portsV2EffectValues() {
    assertEquals(50, V2ItemCatalog.effectValue("almond-water", "hunger"))
    assertEquals(100, V2ItemCatalog.effectValue("almond-water", "thirst"))
    assertEquals(15, V2ItemCatalog.effectValue("bandage", "hp"))
    assertEquals(35, V2ItemCatalog.effectValue("first-aid-kit", "hp"))
    assertEquals(70, V2ItemCatalog.effectValue("coconut-water", "thirst"))
    assertEquals(80, V2ItemCatalog.effectValue("com-tam-suon-bi-cha", "hunger"))
  }

  @Test fun itemContentNormalizationRecognizesV2Aliases() {
    val firstAid = ItemContentRules.normalize(ItemStack("legacy", "First Aid Kit"))
    assertEquals("first-aid-kit", firstAid.itemId)
    assertEquals("Túi Sơ Cứu", firstAid.name)
    assertEquals("35", firstAid.metadata["healHp"])
    assertEquals("true", firstAid.metadata["consumedOnUse"])

    val soy = ItemContentRules.normalize(ItemStack("legacy-soy", "Sữa Đậu Nành Nóng"))
    assertEquals("hot-soy-milk", soy.itemId)
    assertTrue(soy.metadata["physiologyEffect"]!!.contains("FOOD"))
    assertTrue(soy.metadata["physiologyEffect"]!!.contains("WATER"))
  }
}
