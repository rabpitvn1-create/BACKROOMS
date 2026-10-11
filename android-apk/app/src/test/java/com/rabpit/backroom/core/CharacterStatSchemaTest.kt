package com.rabpit.backroom.core
import org.junit.Assert.*
import org.junit.Test
class CharacterStatSchemaTest {
  @Test fun onlyCanonicalBaseStatsAreStored() {
    val p = CharacterStatProfiles.forId(KAI_ID)
    assertEquals(50, p.baseMaxHp)
    assertEquals(listOf(5,5,5,5), listOf(p.str,p.def,p.skl,p.vit))
  }
}
