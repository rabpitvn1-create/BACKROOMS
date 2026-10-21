package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Only the starting values of these four Followers are 1..2; training still raises stats. */
class RecentFollowerStartingBalanceTest {
  private data class Starting(
    val id: String,
    val hp: Int,
    val str: Int, val dex: Int, val agi: Int, val vit: Int, val eva: Int
  ) {
    val stats get() = listOf(str, dex, agi, vit, eva)
  }

  private val roster = listOf(
    Starting(CATHERINE_ID, 26, 1, 2, 2, 1, 2),
    Starting(JANET_ID, 30, 2, 1, 2, 2, 1),
    Starting(NGUYEN_QUOC_NAM_ID, 28, 1, 2, 1, 2, 1),
    Starting(HUA_TIEU_LAN_ID, 25, 1, 2, 1, 1, 2)
  )

  private fun values(p: CharacterStatProfile) = listOf(p.str, p.dex, p.agi, p.vit, p.eva)

  @Test fun `all four Followers start at 25 to 30 HP and one to two base points per stat`() {
    val state = GameState.initial()
    for (spec in roster) {
      val actor = state.characters.getValue(spec.id)
      val effective = CharacterStatCore.effective(state, spec.id)
      assertTrue(spec.id, spec.hp in 25..30)
      assertEquals(spec.id, spec.hp, actor.statProfile.baseMaxHp)
      assertEquals(spec.id, spec.hp, actor.vitalState.currentHp)
      assertEquals(spec.id, spec.hp, effective.maxHp)
      assertEquals(spec.id, spec.stats, values(actor.statProfile))
      assertTrue(spec.id, values(actor.statProfile).all { it in 1..2 })
      assertEquals(spec.id, spec.stats, listOf(
        effective.str, effective.dex, effective.agi, effective.vit, effective.eva
      ))
      val detail = CharacterDetailProjector.projectCharacter(state, spec.id)!!
      assertEquals(spec.id, spec.hp, detail.currentHp)
      assertEquals(spec.id, spec.hp, detail.maxHp)
      assertEquals(spec.id, spec.str, detail.str.base)
      assertEquals(spec.id, spec.dex, detail.dex.base)
      assertEquals(spec.id, spec.agi, detail.agi.base)
      assertEquals(spec.id, spec.vit, detail.vit.base)
      assertEquals(spec.id, spec.eva, detail.eva.base)
    }
  }

  @Test fun `new follower stats survive normalization and encode decode unchanged`() {
    val fresh = GameState.initial()
    val normalized = CharacterProgressionCore.normalize(CharacterEquipmentSystem.normalize(fresh))
    val restored = GameStateCodec.decode(GameStateCodec.encode(normalized))
    for (spec in roster) {
      val value = restored.characters.getValue(spec.id)
      assertEquals(spec.id, spec.stats, values(value.statProfile))
      assertEquals(spec.id, spec.hp, value.statProfile.baseMaxHp)
      assertEquals(spec.id, spec.hp, value.vitalState.currentHp)
      assertEquals(spec.id, spec.hp, CharacterStatCore.effective(restored, spec.id).maxHp)
      for (name in listOf("STR", "DEX", "AGI", "VIT", "EVA")) {
        assertTrue(spec.id, CharacterProgressionCore.baseStat(restored, spec.id, name) in 1..2)
      }
    }
  }

  @Test fun `leveling a low-stat Follower raises max HP without resetting progress or missing HP`() {
    val source = GameState.initial()
    for (spec in roster) {
      val hurt = CharacterStatEngine.setCurrentHp(source, spec.id, spec.hp - 7)
      val old = hurt.characters.getValue(spec.id)
      val advanced = CharacterProgressionCore.grantExp(hurt, spec.id, 10, Random(17)).state
      val leveled = advanced.characters.getValue(spec.id)
      val raisedVit = leveled.statProfile.vit - old.statProfile.vit
      val newMax = CharacterStatCore.effective(advanced, spec.id).maxHp
      assertEquals(spec.id, 1, leveled.progression.level)
      assertTrue(spec.id, raisedVit >= 1)
      assertEquals(spec.id, spec.hp + 5 * raisedVit, newMax)
      assertEquals(spec.id, newMax - 7, leveled.vitalState.currentHp)
      val restored = GameStateCodec.decode(GameStateCodec.encode(advanced))
      assertEquals(spec.id, values(leveled.statProfile),
        values(restored.characters.getValue(spec.id).statProfile))
      assertEquals(spec.id, newMax, CharacterStatCore.effective(restored, spec.id).maxHp)
    }
  }

  @Test fun `existing five-stat fifty-HP profiles stay unchanged`() {
    val state = GameState.initial()
    for (id in listOf(CAO_MINH_ID, LUCIA_ID, LUC_TRAM_ID, IRIS_ID, SYVIAL_ID)) {
      val actor = state.characters.getValue(id)
      assertEquals(id, listOf(5, 5, 5, 5, 5), values(actor.statProfile))
      assertEquals(id, 50, actor.statProfile.baseMaxHp)
      assertEquals(id, 50, actor.vitalState.currentHp)
      assertEquals(id, 5, CharacterStatProfiles.minimumStat(id))
    }
  }

  @Test fun `bad low-stat inputs clamp to one without affecting other characters`() {
    val state = GameState.initial()
    val actor = state.characters.getValue(HUA_TIEU_LAN_ID)
    val invalid = state.copy(characters = state.characters + (HUA_TIEU_LAN_ID to actor.copy(
      statProfile = actor.statProfile.copy(str = -20, dex = 0, vit = -5)
    )))
    val normalized = CharacterProgressionCore.normalize(invalid).characters.getValue(HUA_TIEU_LAN_ID)
    assertEquals(1, normalized.statProfile.str)
    assertEquals(1, normalized.statProfile.dex)
    assertEquals(1, normalized.statProfile.vit)
    assertEquals(25, normalized.statProfile.baseMaxHp)
    assertEquals(5, state.characters.getValue(CAO_MINH_ID).statProfile.str)
  }
}
