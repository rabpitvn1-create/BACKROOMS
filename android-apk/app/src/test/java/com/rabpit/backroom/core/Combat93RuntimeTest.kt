package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class Combat93RuntimeTest {
  private fun party(): GameState = GameState.initial().let {
    var state = it.copy(party = it.party.copy(memberIds = listOf(KAI_ID, IRIS_ID, SYVIAL_ID, AN_NHIEN_ID)),
      characters = it.characters.mapValues { (_, c) -> c.copy(statProfile = c.statProfile.copy(vit = 999)) })
    state.characters.keys.forEach { id -> state = CharacterStatEngine.setCurrentHp(state, id, CharacterStatEngine.effective(state, id).maxHp) }
    state
  }

  private fun finalized(state: GameState, values: List<Int>): GameState {
    val boundary = JSONObject(state.metadata.getValue("combat93.state"))
    boundary.getJSONObject("combat").getJSONObject("diceState")
      .put("values", JSONArray(values)).put("hasRolled", true)
    val prepared = state.copy(metadata = state.metadata + ("combat93.state" to boundary.toString()))
    return Combat93Runtime.finish(prepared, Combat93Runtime.revision(prepared))
  }

  @Test fun authoritativeVitalsAndWeaponArePreservedWithoutMultiplyingStatsTwice() {
    var state = party()
    state = CharacterStatEngine.setCurrentHp(state, KAI_ID, 23)
    state = state.copy(characters = state.characters + (KAI_ID to state.characters.getValue(KAI_ID).let { it.copy(statProfile = it.statProfile.copy(str = 8)) }))
    state = Combat93Runtime.start(state, listOf("diep_minh"), 7, 0)
    val combat = Combat93Runtime.toJson(state)!!
    val actors = combat.getJSONArray("participants")
    assertEquals(3, actors.length())
    val player = actors.getJSONObject(0)
    assertEquals(23, player.getInt("hp"))
    assertEquals(CharacterStatEngine.effective(state, KAI_ID).maxHp, player.getInt("maxHp"))
    val weapon = state.equipment.getValue(KAI_ID).slots.getValue("weapon")
    assertEquals(EquipmentCatalog.definition(weapon)!!.weapon!!.dmg, player.getInt("baseAttack"))
    assertEquals(9, player.getInt("STR")) // 8 Core STR + source 1.1.93a passive bonus.
    assertEquals(8, PokerDiceCore.coreStat(state, KAI_ID, "STR"))
    assertEquals(7, combat.getInt("explorationTurn"))
  }

  @Test fun saveReloadKeepsRerollsAndStartingAgainCannotResetEncounter() {
    var state = Combat93Runtime.start(party(), listOf("diep_minh"), 7, 2)
    state = Combat93Runtime.hold(state, 0, false, Combat93Runtime.revision(state))
    state = Combat93Runtime.roll(state, Combat93Runtime.revision(state))
    val before = Combat93Runtime.toJson(state)!!
    val loaded = GameStateCodec.decode(GameStateCodec.encode(state))
    val restarted = Combat93Runtime.start(loaded, listOf("hound"), 99, 0)
    assertEquals(before.toString(), Combat93Runtime.toJson(restarted).toString())
    assertEquals(1, before.getJSONObject("diceState").getInt("rerollsUsed"))
    assertEquals(2, before.getInt("stageIndex"))
  }

  @Test fun combatHandDoesNotConsumeExplorerTurnAndStaleRequestCannotResolveNextActor() {
    var state = Combat93Runtime.start(party(), listOf("diep_minh"), 7, 0)
    state = finalized(state, listOf(1, 2, 3, 4, 6))
    val revision = Combat93Runtime.revision(state)
    val result = Combat93Runtime.resolve(state, revision)
    assertTrue(result.handled)
    val combat = Combat93Runtime.toJson(result.state)!!
    assertEquals(7, combat.getInt("explorationTurn"))
    assertEquals(0, combat.getInt("resolvedActorIndex"))
    assertEquals(1, combat.getInt("actorIndex"))
    assertEquals("diep_minh", combat.getJSONArray("resolvedEntityTurns").getJSONObject(0).getString("entityKey"))
    val repeated = Combat93Runtime.resolve(result.state, revision)
    assertFalse(repeated.handled)
    assertEquals(result.state, repeated.state)
    val loaded = GameStateCodec.decode(GameStateCodec.encode(result.state))
    assertEquals(Combat93Runtime.toJson(result.state).toString(), Combat93Runtime.toJson(loaded).toString())
    assertEquals(result.state.characters.getValue(KAI_ID).vitalState.currentHp,
      loaded.characters.getValue(KAI_ID).vitalState.currentHp)
  }

  @Test fun terminalDamageAndRewardsSurviveReloadWithoutBeingGrantedTwice() {
    var state = Combat93Runtime.start(party(), listOf("hound"), 7, 0)
    state = finalized(state, listOf(2, 2, 2, 2, 2))
    val result = Combat93Runtime.resolve(state, Combat93Runtime.revision(state))
    assertTrue(result.handled)
    val terminal = Combat93Runtime.toJson(result.state)!!
    assertFalse(terminal.getBoolean("active"))
    assertEquals("victory", terminal.getString("outcome"))
    assertEquals(8, terminal.getInt("explorationTurn"))
    assertTrue(terminal.getJSONArray("feedbackEvents").length() > 0)
    assertEquals(2, PokerDiceCore.coreCount(result.state))
    val drops = result.state.inventories.getValue(KAI_ID).items.values.filter { it.metadata["itemOrigin"] == "ENTITY" }
    assertEquals(1, drops.sumOf { it.quantity })
    assertNull(ItemContentRules.nextAfterUse(drops.single()))
    assertTrue(HealingItems.healAmount(drops.single()) > 0 || drops.single().metadata["physiologyEffect"] == "WATER")
    val loaded = GameStateCodec.decode(GameStateCodec.encode(result.state))
    val repeated = Combat93Runtime.resolve(loaded, Combat93Runtime.revision(loaded))
    assertFalse(repeated.handled)
    assertEquals(loaded, repeated.state)
    assertEquals(2, PokerDiceCore.coreCount(repeated.state))
  }

  @Test fun fullInventoryRetainsLootInWorld() {
    var state = party()
    val inventory = state.inventories.getValue(KAI_ID)
    val filler = (1..InventoryPolicy.KAI.maxTypes).associate { "filler-$it" to ItemStack("filler-$it", "Item $it") }
    state = state.copy(inventories = state.inventories + (KAI_ID to inventory.copy(items = inventory.items + filler)))
    state = Combat93Runtime.start(state, listOf("hound"), 7, 0)
    state = finalized(state, listOf(2, 2, 2, 2, 2))
    val result = Combat93Runtime.resolve(state, Combat93Runtime.revision(state))
    assertEquals(1, JSONObject(result.state.world.getValue("flagsJson")).getJSONArray("worldItems").length())
    assertEquals(state.inventories, result.state.inventories)
  }
}
