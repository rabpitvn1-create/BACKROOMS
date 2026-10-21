package com.rabpit.backroom.core

import org.json.JSONObject
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

  @Test fun `current run stored with previous fifty HP profile rebalances all four on load`() {
    val legacyCurrent = JSONObject(GameStateCodec.encode(GameState.initial()))
    val characters = legacyCurrent.getJSONObject("characters")
    for (spec in roster) {
      val item = characters.getJSONObject(spec.id)
      val p = item.getJSONObject("statProfile")
      p.put("baseMaxHp", 50)
      for (key in listOf("STR", "DEX", "AGI", "VIT", "EVA")) p.put(key, 5)
      item.getJSONObject("vitalState").put("currentHp", 50)
    }
    val restored = GameStateCodec.decode(legacyCurrent.toString())
    for (spec in roster) {
      val actor = restored.characters.getValue(spec.id)
      assertEquals(spec.id, spec.stats, values(actor.statProfile))
      assertEquals(spec.id, spec.hp, actor.statProfile.baseMaxHp)
      assertEquals(spec.id, spec.hp, actor.vitalState.currentHp)
      assertEquals(spec.id, spec.hp, CharacterStatCore.effective(restored, spec.id).maxHp)
    }
    assertEquals(50, restored.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
    // The new baseline is serialized; a second load MUST NOT rebase twice.
    val second = GameStateCodec.decode(GameStateCodec.encode(restored))
    for (spec in roster) {
      assertEquals(spec.id, restored.characters.getValue(spec.id).statProfile,
        second.characters.getValue(spec.id).statProfile)
      assertEquals(spec.id, restored.characters.getValue(spec.id).vitalState.currentHp,
        second.characters.getValue(spec.id).vitalState.currentHp)
    }
  }

  @Test fun `current run rebalance preserves training levels experience and relative wounds`() {
    val snapshot = JSONObject(GameStateCodec.encode(GameState.initial()))
    val raw = snapshot.getJSONObject("characters").getJSONObject(JANET_ID)
    val p = raw.getJSONObject("statProfile")
      .put("baseMaxHp", 50)
      .put("STR", 7).put("DEX", 6).put("AGI", 7).put("VIT", 8).put("EVA", 6)
    raw.getJSONObject("vitalState").put("currentHp", 32)
    raw.getJSONObject("progression").put("level", 2).put("exp", 3)
    val loaded = GameStateCodec.decode(snapshot.toString())
    val janet = loaded.characters.getValue(JANET_ID)
    assertEquals(listOf(4, 2, 4, 5, 2), values(janet.statProfile))
    assertEquals(CharacterProgression(2, 3), janet.progression)
    assertEquals(30, janet.statProfile.baseMaxHp)
    assertEquals(45, CharacterStatCore.effective(loaded, JANET_ID).maxHp)
    // Old effective Max HP 65, current 32, so ceil(32/65 * 45) = 23.
    assertEquals(23, janet.vitalState.currentHp)
    assertEquals(23, GameStateCodec.decode(GameStateCodec.encode(loaded))
      .characters.getValue(JANET_ID).vitalState.currentHp)
    // Current-run normalization invoked directly must enforce the same policy.
    val directOld = GameState.initial()
    val actor = directOld.characters.getValue(JANET_ID)
    val direct = directOld.copy(characters = directOld.characters + (JANET_ID to actor.copy(
      statProfile = actor.statProfile.copy(baseMaxHp = 50, str = 5, dex = 5, agi = 5, vit = 5, eva = 5),
      vitalState = actor.vitalState.copy(currentHp = 50)
    )))
    val rebalanced = CharacterEquipmentSystem.normalize(direct)
    assertEquals(30, rebalanced.characters.getValue(JANET_ID).vitalState.currentHp)
    assertEquals(listOf(2, 1, 2, 2, 1), values(rebalanced.characters.getValue(JANET_ID).statProfile))
  }

  @Test fun `rebalance never resurrects a defeated Follower`() {
    val snapshot = JSONObject(GameStateCodec.encode(GameState.initial()))
    val raw = snapshot.getJSONObject("characters").getJSONObject(HUA_TIEU_LAN_ID)
    raw.getJSONObject("statProfile").put("baseMaxHp", 50)
    for (name in listOf("STR", "DEX", "AGI", "VIT", "EVA")) {
      raw.getJSONObject("statProfile").put(name, 5)
    }
    raw.getJSONObject("vitalState").put("currentHp", 0).put("condition", "DEFEATED")
    val decoded = GameStateCodec.decode(snapshot.toString())
    assertEquals(0, decoded.characters.getValue(HUA_TIEU_LAN_ID).vitalState.currentHp)
    assertEquals(25, CharacterStatCore.effective(decoded, HUA_TIEU_LAN_ID).maxHp)
  }
}
