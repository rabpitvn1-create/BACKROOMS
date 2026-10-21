package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class StatPointBookTest {
  private fun stocked(count: Int = 2): GameState {
    var state = CoreClientProjection.initialize(GameState.initial())
    repeat(count) { state = InventoryEngine.grantDrop(state, STAT_POINT_BOOK_ID, "DUNGEON_DROP")!! }
    return state
  }
  private fun use(stat: String?, quantity: Int = 1, actor: String = CAO_MINH_ID) = ItemCommand(
    "book", null, actor, source = CommandSource.UI, operation = ItemCommand.Operation.USE,
    itemId = STAT_POINT_BOOK_ID, itemName = "Sách + Stats", quantity = quantity,
    metadata = stat?.let { mapOf("targetStat" to it) } ?: emptyMap())

  @Test fun eachBookAddsExactlyOneSelectedBaseStatAndConsumesOneWithoutExpTimeOrHealing() {
    for (stat in StatPointBook.stats) {
      val state = stocked()
      val before = state.characters.getValue(CAO_MINH_ID)
      val result = InventoryEngine.execute(state, use(stat))
      assertTrue(result.applied)
      for (key in StatPointBook.stats) assertEquals(
        CharacterProgressionCore.baseStat(state, CAO_MINH_ID, key) + if (key == stat) 1 else 0,
        CharacterProgressionCore.baseStat(result.state, CAO_MINH_ID, key))
      val actor = result.state.characters.getValue(CAO_MINH_ID)
      assertEquals(before.progression, actor.progression)
      assertEquals(before.vitalState.currentHp, actor.vitalState.currentHp)
      assertEquals(state.time, result.state.time)
      assertEquals(state.turn, result.state.turn)
      assertEquals(1, result.state.inventories.getValue(CAO_MINH_ID).items.getValue(STAT_POINT_BOOK_ID).quantity)
      assertEquals(actor, GameStateCodec.decode(GameStateCodec.encode(result.state)).characters.getValue(CAO_MINH_ID))
    }
  }

  @Test fun invalidChoiceBatchUseCappedDeadAndMissingBookAreRejectedWithoutConsumption() {
    val state = stocked()
    for (command in listOf(use(null), use("ALL"), use("STR", 2), use("VIT").copy(source = CommandSource.GEMINI))) {
      val result = InventoryEngine.execute(state, command)
      assertFalse(result.applied);assertEquals(state, result.state)
    }
    val actor = state.characters.getValue(CAO_MINH_ID)
    val capped = state.copy(characters = state.characters + (CAO_MINH_ID to actor.copy(statProfile = actor.statProfile.copy(str = 999))))
    assertEquals("stat_book_stat_capped", InventoryEngine.execute(capped, use("STR")).validation.reason)
    val dead = CharacterStatEngine.setCurrentHp(state, CAO_MINH_ID, 0)
    assertFalse(InventoryEngine.execute(dead, use("STR")).applied)
    assertFalse(InventoryEngine.execute(GameState.initial(), use("STR")).applied)
    val single = InventoryEngine.execute(stocked(1), use("AGI"))
    assertTrue(single.applied)
    assertFalse(InventoryEngine.execute(single.state, use("AGI")).applied)
    assertEquals(single.state, InventoryEngine.execute(single.state, use("AGI")).state)
  }

  @Test fun vitIncreasesDerivedMaxHpButNotCurrentHpOrLevel() {
    val state = CharacterStatEngine.setCurrentHp(stocked(), CAO_MINH_ID, 10)
    val before = CharacterStatEngine.effective(state, CAO_MINH_ID).maxHp
    val result = InventoryEngine.execute(state, use("VIT")).state
    assertTrue(CharacterStatEngine.effective(result, CAO_MINH_ID).maxHp > before)
    assertEquals(10, result.characters.getValue(CAO_MINH_ID).vitalState.currentHp)
    assertEquals(state.characters.getValue(CAO_MINH_ID).progression, result.characters.getValue(CAO_MINH_ID).progression)
  }

  @Test fun followerCanUseOwnedBookAndOtherPartyMembersAreUntouched() {
    val state = stocked()
    val party = state.copy(party = PartyState(memberIds = listOf(CAO_MINH_ID, IRIS_ID)))
    val transfer = InventoryEngine.execute(party, use("STR").copy(operation = ItemCommand.Operation.TRANSFER, targetId = IRIS_ID))
    assertTrue(transfer.applied)
    val result = InventoryEngine.execute(transfer.state, use("DEX", actor = IRIS_ID))
    assertTrue(result.applied)
    assertEquals(party.characters.getValue(CAO_MINH_ID), result.state.characters.getValue(CAO_MINH_ID))
    assertEquals(6, result.state.characters.getValue(IRIS_ID).statProfile.dex)
  }

  @Test fun luciaKeepsEarnedStatsAndBookPointsThroughNormalizationAndSaveLoad() {
    var state = LuciaCanon.ensure(stocked())
    state = state.copy(party = PartyState(memberIds = listOf(CAO_MINH_ID, LUCIA_ID)))
    val book = state.inventories.getValue(CAO_MINH_ID).items.getValue(STAT_POINT_BOOK_ID)
    state = state.copy(inventories = state.inventories + (LUCIA_ID to InventoryState(LUCIA_ID, mapOf(STAT_POINT_BOOK_ID to book))))
    state = CharacterProgressionCore.grantExp(state, LUCIA_ID, 100, Random(4)).state
    val used = InventoryEngine.execute(state, use("VIT", actor = LUCIA_ID))
    assertTrue(used.applied)
    val actor = used.state.characters.getValue(LUCIA_ID)
    assertTrue(actor.statProfile.vit > 5)
    assertEquals(actor, GameStateCodec.decode(GameStateCodec.encode(used.state)).characters.getValue(LUCIA_ID))
    assertEquals(actor, CharacterProgressionCore.normalize(CharacterEquipmentSystem.normalize(used.state)).characters.getValue(LUCIA_ID))
  }

  @Test fun reachingCapByBooksDoesNotBlockNormalLevelUpOrWasteAllocatablePoints() {
    var state = stocked(1)
    val player = state.characters.getValue(CAO_MINH_ID)
    state = state.copy(characters = state.characters + (CAO_MINH_ID to player.copy(statProfile = player.statProfile.copy(str = 998))))
    state = InventoryEngine.execute(state, use("STR")).state
    val raised = CharacterProgressionCore.grantExp(state, CAO_MINH_ID, 10, Random(7)).state
    assertEquals(1, raised.characters.getValue(CAO_MINH_ID).progression.level)
    assertEquals(999, raised.characters.getValue(CAO_MINH_ID).statProfile.str)
    val others = listOf("DEX", "AGI", "VIT", "EVA")
    assertEquals(10, others.sumOf { CharacterProgressionCore.baseStat(raised, CAO_MINH_ID, it) - CharacterProgressionCore.baseStat(state, CAO_MINH_ID, it) })
    val p = raised.characters.getValue(CAO_MINH_ID)
    val allCapped = raised.copy(characters = raised.characters + (CAO_MINH_ID to p.copy(statProfile = p.statProfile.copy(str=999,dex=999,agi=999,vit=999,eva=999))))
    assertEquals(2, CharacterProgressionCore.grantExp(allCapped, CAO_MINH_ID, 18, Random(1)).state.characters.getValue(CAO_MINH_ID).progression.level)
  }

  @Test fun oneCombinedRollHasExactlyThreePercentStatBooksAndNoOrdinaryEntityPoolBooks() {
    assertEquals(300, (0 until 10000).count { StatPointBook.dropForRoll(it) == STAT_POINT_BOOK_ID })
    assertNull(StatPointBook.dropForRoll(300))
    assertEquals(STAT_POINT_BOOK_ID, StatPointBook.dropForRoll(299))
    repeat(1000) { assertNotEquals(STAT_POINT_BOOK_ID, OfflineItemRegistry.random(it.toLong()).id) }
    assertThrows(IllegalArgumentException::class.java) { StatPointBook.dropForRoll(10000) }
  }

  @Test fun fullInventoryQueuesBooksAndCollectsExactlyOnceAfterRoomIsFreed() {
    val state = CoreClientProjection.initialize(GameState.initial())
    val inventory = state.inventories.getValue(CAO_MINH_ID)
    val limit = InventoryPolicy.CAO_MINH.maxTypes
    val filler = (0 until limit).associate { "filler:$it" to ItemStack("filler:$it", "Đồ $it", 1) }
    val full = state.copy(inventories = state.inventories + (CAO_MINH_ID to inventory.copy(items = filler)),
      dungeon = DungeonState(pendingBooks = 3))
    assertEquals(full, StatPointBook.collectPending(full))
    val freed = full.copy(inventories = full.inventories + (CAO_MINH_ID to full.inventories.getValue(CAO_MINH_ID).copy(items = filler - "filler:0")))
    val collected = StatPointBook.collectPending(freed)
    assertEquals(0L, collected.dungeon.pendingBooks)
    assertEquals(3, collected.inventories.getValue(CAO_MINH_ID).items.getValue(STAT_POINT_BOOK_ID).quantity)
    assertEquals(collected, StatPointBook.collectPending(GameStateCodec.decode(GameStateCodec.encode(collected))))
  }

  private class MemorySave(var state: GameState) : SaveRepository {
    override fun save(state: GameState) { this.state = GameStateCodec.decode(GameStateCodec.encode(state)) }
    override fun load() = state
    override fun exists() = true
    override fun clear() = Unit
    override fun saveCheckpoint(state: GameState, presentation: JSONObject) = Unit
    override fun loadCheckpoint(): GameCheckpoint? = null
  }
  @Test fun nativeInventoryRequestRequiresExplicitStatAndUsesCanonicalPlusOne() {
    val repo = MemorySave(stocked(1))
    val ctor = GameCoreFacade::class.java.getDeclaredConstructor(SaveRepository::class.java, GamePipelineLogger::class.java)
    ctor.isAccessible = true
    val core = ctor.newInstance(repo, NoOpGamePipelineLogger)
    val request = JSONObject().put("operation", "USE").put("actorId", CAO_MINH_ID).put("itemId", STAT_POINT_BOOK_ID).put("quantity", 1)
    val view = core.currentClientState("{}")
    assertFalse(JSONObject(core.processItemAction(view, request.toString())).getBoolean("handled"))
    assertEquals(1, repo.state.inventories.getValue(CAO_MINH_ID).items.getValue(STAT_POINT_BOOK_ID).quantity)
    request.put("targetStat", "STR").put("amount", 999).put("targetId", IRIS_ID)
    val result = JSONObject(core.processItemAction(view, request.toString()))
    assertTrue(result.getBoolean("handled"))
    assertEquals(6, repo.state.characters.getValue(CAO_MINH_ID).statProfile.str)
    assertTrue(result.getString("reply").contains("+1 STR"))
  }
}
