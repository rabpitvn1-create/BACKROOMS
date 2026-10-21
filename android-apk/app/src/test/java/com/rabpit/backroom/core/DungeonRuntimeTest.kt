package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DungeonRuntimeTest {
  private val start = 1_790_000_000_000L
  private fun fresh(): GameState {
    val base = LuciaCanon.ensure(CoreClientProjection.initialize(GameState.initial()))
    return base.copy(characters = base.characters.mapValues { (id, actor) ->
      if (id == CAO_MINH_ID) actor else actor.copy(presence = CharacterPresence.ACTIVE,
        metadata = actor.metadata + mapOf("encountered" to "true", "followsPlayer" to "true"))
    }, party = PartyState(memberIds = listOf(CAO_MINH_ID, IRIS_ID, SYVIAL_ID)))
  }
  private fun begin(slot: Int = 0) = DungeonRuntime.start(fresh(), slot, IRIS_ID, start)

  @Test fun fiveDurationsEachCreditExactlyOneEntityPerMinuteAndStopAtTheCap() {
    assertEquals(listOf(15, 30, 60, 120, 180), DungeonRuntime.durations)
    for ((slot, duration) in DungeonRuntime.durations.withIndex()) {
      val original = begin(slot)
      val finished = DungeonRuntime.settle(original, start + 7 * 24 * 60 * DungeonRuntime.MINUTE_MS)
      assertEquals(duration, finished.dungeon.runs.single().creditedMinutes)
      assertEquals(duration * 4, DungeonRuntime.view(finished, finished.dungeon.clockMs)
        .getJSONArray("slots").getJSONObject(slot).getInt("earnedExp"))
      val again = DungeonRuntime.settle(finished, finished.dungeon.clockMs)
      assertEquals(finished, again)
      assertEquals(finished.characters, DungeonRuntime.settle(finished, finished.dungeon.clockMs + 999999).characters)
    }
  }

  @Test fun offlineAndMinuteByMinuteGiveIdenticalStatsAndCannotDuplicateExp() {
    val run = begin(4)
    val offline = DungeonRuntime.settle(run, start + 180 * DungeonRuntime.MINUTE_MS)
    var online = run
    for (minute in 1..180) online = DungeonRuntime.settle(online, start + minute * DungeonRuntime.MINUTE_MS)
    assertEquals(offline, online)
    val saved = GameStateCodec.decode(GameStateCodec.encode(offline))
    assertEquals(saved, DungeonRuntime.settle(saved, start + 180 * DungeonRuntime.MINUTE_MS))
    assertTrue(saved.characters.getValue(IRIS_ID).progression.level > 0)
    assertEquals(run.characters.getValue(CAO_MINH_ID), saved.characters.getValue(CAO_MINH_ID))
  }

  @Test fun trainingNeverRunsCombatTimeInjuriesOrOrdinaryLoot() {
    val initial = fresh()
    val hp = initial.characters.getValue(IRIS_ID).vitalState.currentHp
    val trained = DungeonRuntime.settle(DungeonRuntime.start(initial, 4, IRIS_ID, start),
      start + 180 * DungeonRuntime.MINUTE_MS)
    assertEquals(initial.time, trained.time)
    assertEquals(initial.turn, trained.turn)
    assertEquals(initial.world, trained.world)
    assertEquals(initial.inventories - CAO_MINH_ID, trained.inventories - CAO_MINH_ID)
    assertEquals(initial.inventories.getValue(CAO_MINH_ID).items,
      trained.inventories.getValue(CAO_MINH_ID).items - STAT_POINT_BOOK_ID)
    assertEquals(initial.statuses, trained.statuses)
    assertEquals(initial.characters.getValue(IRIS_ID).injuries, trained.characters.getValue(IRIS_ID).injuries)
    assertTrue(trained.characters.getValue(IRIS_ID).vitalState.currentHp >= hp)
    assertEquals(CharacterPresence.SEPARATED, trained.characters.getValue(IRIS_ID).presence)
    assertFalse(IRIS_ID in trained.party.memberIds)
    assertFalse(IRIS_ID in PartyCombatHealth.actingMembers(trained))
    assertNull(CombatRuntime.active(trained))
    assertNull(CombatRuntime.pending(trained))
    val view = DungeonRuntime.view(trained, start)
    assertTrue(view.getBoolean("booksEnabled"))
    assertEquals(300, view.getInt("bookDropBasisPoints"))
    val afterClock = TimeEngine.execute(trained, TimeAdvanceCommand(commandId = "time", turnId = null, actorId = CAO_MINH_ID,
      source = CommandSource.SYSTEM, minutes = 360, reason = "explore", activity = TimeActivity.EXERTION)).state
    assertEquals(trained.characters.getValue(IRIS_ID), afterClock.characters.getValue(IRIS_ID))
  }

  @Test fun fiveDifferentFollowersCanTrainButPlayerUnknownDeadAndDuplicateCannot() {
    var state = fresh()
    val ids = listOf(IRIS_ID, SYVIAL_ID, AN_NHIEN_ID, LUC_TRAM_ID, LUCIA_ID)
    ids.forEachIndexed { slot, id -> state = DungeonRuntime.start(state, slot, id, start) }
    assertEquals(5, state.dungeon.runs.size)
    assertEquals(listOf(CAO_MINH_ID), state.party.memberIds)
    DungeonRuntime.validate(state)
    assertThrows(IllegalArgumentException::class.java) { DungeonRuntime.start(fresh(), 0, CAO_MINH_ID, start) }
    assertThrows(IllegalArgumentException::class.java) { DungeonRuntime.start(begin(), 1, IRIS_ID, start) }
    assertThrows(IllegalArgumentException::class.java) { DungeonRuntime.start(begin(), 0, SYVIAL_ID, start) }
    assertThrows(IllegalArgumentException::class.java) { DungeonRuntime.start(fresh(), 5, IRIS_ID, start) }
    val unknown = GameState.initial()
    assertThrows(IllegalArgumentException::class.java) { DungeonRuntime.start(unknown, 0, IRIS_ID, start) }
    val dead = CharacterStatEngine.setCurrentHp(fresh(), IRIS_ID, 0)
    assertThrows(IllegalArgumentException::class.java) { DungeonRuntime.start(dead, 0, IRIS_ID, start) }
  }

  @Test fun earlyReturnCreditsFullMinutesOnlyAndStaleRequestsCannotStopReplacement() {
    val run = begin()
    assertEquals(run.characters, DungeonRuntime.settle(run, start + 59999).characters)
    val returned = DungeonRuntime.release(run, 0, run.dungeon.runs.single().id, start + 119999)
    assertTrue(returned.dungeon.runs.isEmpty())
    assertEquals(CharacterProgression(0, 4), returned.characters.getValue(IRIS_ID).progression)
    assertEquals(CharacterPresence.ACTIVE, returned.characters.getValue(IRIS_ID).presence)
    assertFalse(IRIS_ID in returned.party.memberIds)
    assertTrue(DungeonRuntime.eligible(returned, returned.characters.getValue(IRIS_ID)))
    val replacement = DungeonRuntime.start(returned, 0, IRIS_ID, start + 120000)
    assertThrows(IllegalArgumentException::class.java) {
      DungeonRuntime.release(replacement, 0, run.dungeon.runs.single().id, start + 180000)
    }
    assertEquals(returned, DungeonRuntime.settle(returned, start + 999999))
  }

  @Test fun bookDropsAreChunkInvariantAndNeverRepeatOnRestoreOrReread() {
    var base = fresh()
    for ((slot, id) in listOf(IRIS_ID, SYVIAL_ID, AN_NHIEN_ID, LUC_TRAM_ID, LUCIA_ID).withIndex())
      base = DungeonRuntime.start(base, slot, id, start)
    val end = start + 180 * DungeonRuntime.MINUTE_MS
    val offline = DungeonRuntime.settle(base, end)
    var online = base
    for (minute in 1..180) online = DungeonRuntime.settle(online, start + minute * DungeonRuntime.MINUTE_MS)
    assertEquals(offline, online)
    val found = offline.dungeon.runs.sumOf { it.booksFound }
    assertTrue(found > 0)
    val owned = offline.inventories.getValue(CAO_MINH_ID).items[STAT_POINT_BOOK_ID]?.quantity ?: 0
    assertEquals(found.toLong(), owned.toLong() + offline.dungeon.pendingBooks)
    assertEquals(offline, DungeonRuntime.settle(GameStateCodec.decode(GameStateCodec.encode(offline)), end))
  }

  @Test fun clockRollbackNeverDecreasesProgressOrReawardsMinutes() {
    val credited = DungeonRuntime.settle(begin(), start + 120000)
    assertEquals(credited, DungeonRuntime.settle(credited, start - 100000))
    assertEquals(credited, DungeonRuntime.settle(credited, start + 120000))
    val next = DungeonRuntime.settle(credited, start + 180000)
    assertEquals(3, next.dungeon.runs.single().creditedMinutes)
  }

  @Test fun imageIsStableAcrossPollingAndRoundTripAndDoesNotChangeWorld() {
    val run = begin()
    val restored = GameStateCodec.decode(GameStateCodec.encode(run))
    assertEquals(run.dungeon, restored.dungeon)
    assertEquals(run.dungeon.runs.single().environmentId,
      DungeonRuntime.settle(restored, start + 900000).dungeon.runs.single().environmentId)
    assertEquals(fresh().world, restored.world)
    assertEquals(5, DungeonRuntime.view(restored, start).getJSONArray("slots").length())
  }

  @Test fun normalAndCheatRecruitmentCannotPullFollowerFromDungeon() {
    val state = begin()
    val command = PartyCommand("join", "TURN_1", CAO_MINH_ID, IRIS_ID, CommandSource.UI,
      PartyCommand.Operation.ADD, consentConfirmed = true, targetPresent = true)
    assertEquals("follower_in_dungeon", PartyEngine.execute(state, command).validation.reason)
    assertEquals("follower_in_dungeon", SpecialFollowersCanon.forceIntoParty(state, IRIS_ID).second)
    val anNhien = DungeonRuntime.start(fresh(), 0, AN_NHIEN_ID, start)
    assertEquals("follower_in_dungeon", AnNhienCanon.forceIntoParty(anNhien).second)
  }

  @Test fun codecRejectsOldSchemaDuplicatesPlayerAssignmentsAndImpossibleCredit() {
    val raw = JSONObject(GameStateCodec.encode(begin()))
    fun reject(change: (JSONObject) -> Unit) {
      val copy = JSONObject(raw.toString());change(copy)
      assertThrows(IllegalArgumentException::class.java) { GameStateCodec.decode(copy) }
    }
    reject { it.put("saveVersion", CURRENT_SAVE_VERSION - 1) }
    reject { it.getJSONObject("dungeon").getJSONArray("runs").put(it.getJSONObject("dungeon").getJSONArray("runs").getJSONObject(0)) }
    reject { it.getJSONObject("dungeon").getJSONArray("runs").getJSONObject(0).put("followerId", CAO_MINH_ID) }
    reject { it.getJSONObject("dungeon").getJSONArray("runs").getJSONObject(0).put("creditedMinutes", 1) }
    reject { it.getJSONObject("dungeon").getJSONArray("runs").getJSONObject(0).put("environmentId", "fake") }
  }
}
