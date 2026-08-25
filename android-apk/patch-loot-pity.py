from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
ITEMS = CORE / "ItemCatalog.kt"
ACTION = CORE / "ActionRuntime.kt"
FACADE = CORE / "GameCoreFacade.kt"
COMBAT = CORE / "CombatRuntime.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
OFFICIAL_TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/OfficialItemSystemTest.kt"
ACTION_TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/ActionRuntimeTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# ---------------------------------------------------------------------------
# One authoritative loot system.
# Environment loot: every completed SEARCH/EXPLORE action gets +1 percentage
# point of pity, beginning at +1% on the first eligible turn and reaching a
# guaranteed result no later than eligible turn 100. The existing per-Level
# base chance and An Nhien/Lucia bonuses are preserved and added to pity.
# Entity loot: each consecutive defeated Entity without a drop adds +2 points,
# beginning at 2% and guaranteeing the 50th consecutive kill at the latest.
# Both counters live in GameState.metadata, so save/load and sublevel changes do
# not erase unlucky streaks. A successful drop resets only its own counter.
# ---------------------------------------------------------------------------
items = ITEMS.read_text(encoding="utf-8")
entity_start = items.find("object EntityLootEngine {\n")
world_start = items.find("object WorldLootAcquisition {\n", entity_start)
if entity_start < 0 or world_start < 0:
    raise RuntimeError("Loot pity could not locate EntityLootEngine/WorldLootAcquisition boundary")

loot_engines = r'''data class EnvironmentLootPreview(
  val eligible: Boolean,
  val baseThreshold: Int,
  val pityTurn: Int,
  val pityThreshold: Int,
  val followerThreshold: Int,
  val threshold: Int,
  val roll: Int?,
  val success: Boolean
) {
  val chancePercent: Double get() = threshold / 100.0
}

object EntityLootEngine {
  const val DROP_STEP_PERCENT = 2
  const val DROP_CHANCE_PERCENT = DROP_STEP_PERCENT
  const val PITY_KEY = "lootPity.entityKillsWithoutDrop"
  const val GUARANTEED_KILL = 50

  fun killsWithoutDrop(state: GameState): Int =
    state.metadata[PITY_KEY]?.toIntOrNull()?.coerceIn(0, GUARANTEED_KILL - 1) ?: 0

  fun dropChancePercent(state: GameState): Int =
    ((killsWithoutDrop(state) + 1) * DROP_STEP_PERCENT).coerceIn(DROP_STEP_PERCENT, 100)

  private fun withPity(state: GameState, failures: Int): GameState {
    val metadata = state.metadata.toMutableMap()
    if (failures > 0) metadata[PITY_KEY] = failures.coerceAtMost(GUARANTEED_KILL - 1).toString()
    else metadata.remove(PITY_KEY)
    return state.copy(metadata = metadata)
  }

  fun onDefeat(state: GameState, defeatId: String, rng: LootRng): GameState {
    val marker = "entityLootRolled:$defeatId"
    if (defeatId.isBlank() || state.world[marker] != null) return state

    val failures = killsWithoutDrop(state)
    val chance = ((failures + 1) * DROP_STEP_PERCENT).coerceAtMost(100)
    var next = state.copy(world = state.world + (marker to "NONE"))
    val success = chance >= 100 || rng.nextInt(100) < chance
    if (!success) return withPity(next, failures + 1)

    val item = ItemCatalog.items[rng.nextInt(ItemCatalog.items.size)].stack()
    next = withPity(next, 0)
    val lootId = "entityLoot:$defeatId"
    return next.copy(world = next.world + mapOf(
      marker to item.itemId,
      lootId to "${item.itemId}|${item.name}|1|ENTITY_DROP"
    ))
  }
}

object LevelLootEngine {
  const val ENVIRONMENT_PITY_KEY = "lootPity.environmentFailures"
  const val PITY_STEP_BASIS_POINTS = 100
  const val GUARANTEED_TURN = 100
  private const val PREVIEW_PREFIX = "actionRuntime.loot."
  private val BASE_THRESHOLDS = intArrayOf(35, 120, 100, 150, 180, 100, 45)

  private fun eligible(kind: ActionKind): Boolean = kind == ActionKind.SEARCH || kind == ActionKind.EXPLORE

  private fun parentLevel(state: GameState): Int {
    val levelJson = state.world["levelJson"].orEmpty()
    val structured = Regex("\\\"number\\\"\\s*:\\s*(\\d+)").find(levelJson)
      ?.groupValues?.getOrNull(1)?.toIntOrNull()
    if (structured != null) return structured.coerceIn(0, 6)
    val fallback = Regex("Level\\s+(\\d+)", RegexOption.IGNORE_CASE)
      .find(state.world["title"].orEmpty() + " " + state.world["location"].orEmpty())
      ?.groupValues?.getOrNull(1)?.toIntOrNull()
    return (fallback ?: 0).coerceIn(0, 6)
  }

  fun environmentFailures(state: GameState): Int =
    state.metadata[ENVIRONMENT_PITY_KEY]?.toIntOrNull()?.coerceIn(0, GUARANTEED_TURN - 1) ?: 0

  private fun followerBonusThreshold(state: GameState): Int {
    var bonus = 0
    if (AN_NHIEN_ID in state.party.memberIds) bonus += 1000
    if (LUCIA_ID in state.party.memberIds) bonus += 500
    return bonus
  }

  private fun stablePositiveHash(value: String): Long {
    var hash = 1469598103934665603L
    value.forEach { ch -> hash = (hash xor ch.code.toLong()) * 1099511628211L }
    return hash and Long.MAX_VALUE
  }

  private fun calculate(state: GameState, sessionId: String, kind: ActionKind, location: String?): EnvironmentLootPreview {
    if (!eligible(kind)) return EnvironmentLootPreview(false, 0, 0, 0, 0, 0, null, false)
    val failures = environmentFailures(state)
    val pityTurn = (failures + 1).coerceIn(1, GUARANTEED_TURN)
    val base = BASE_THRESHOLDS[parentLevel(state)]
    val pity = pityTurn * PITY_STEP_BASIS_POINTS
    val follower = followerBonusThreshold(state)
    val threshold = (base + pity + follower).coerceAtMost(10000)
    if (threshold >= 10000) {
      return EnvironmentLootPreview(true, base, pityTurn, pity, follower, 10000, null, true)
    }
    val seed = stablePositiveHash("$sessionId|${kind.name}|${location.orEmpty()}|$pityTurn")
    val roll = (seed % 10000L).toInt() + 1
    return EnvironmentLootPreview(true, base, pityTurn, pity, follower, threshold, roll, roll <= threshold)
  }

  fun prepareAction(state: GameState, sessionId: String, kind: ActionKind, location: String?): GameState {
    if (!eligible(kind)) return state
    val preview = calculate(state, sessionId, kind, location)
    val metadata = state.metadata + mapOf(
      "${PREVIEW_PREFIX}sessionId" to sessionId,
      "${PREVIEW_PREFIX}eligible" to preview.eligible.toString(),
      "${PREVIEW_PREFIX}baseThreshold" to preview.baseThreshold.toString(),
      "${PREVIEW_PREFIX}pityTurn" to preview.pityTurn.toString(),
      "${PREVIEW_PREFIX}pityThreshold" to preview.pityThreshold.toString(),
      "${PREVIEW_PREFIX}followerThreshold" to preview.followerThreshold.toString(),
      "${PREVIEW_PREFIX}threshold" to preview.threshold.toString(),
      "${PREVIEW_PREFIX}roll" to (preview.roll?.toString() ?: ""),
      "${PREVIEW_PREFIX}success" to preview.success.toString()
    )
    return state.copy(metadata = metadata)
  }

  fun preparedPreview(state: GameState): EnvironmentLootPreview? {
    if (state.metadata["${PREVIEW_PREFIX}eligible"] != "true") return null
    val threshold = state.metadata["${PREVIEW_PREFIX}threshold"]?.toIntOrNull() ?: return null
    return EnvironmentLootPreview(
      eligible = true,
      baseThreshold = state.metadata["${PREVIEW_PREFIX}baseThreshold"]?.toIntOrNull() ?: 0,
      pityTurn = state.metadata["${PREVIEW_PREFIX}pityTurn"]?.toIntOrNull() ?: 1,
      pityThreshold = state.metadata["${PREVIEW_PREFIX}pityThreshold"]?.toIntOrNull() ?: 100,
      followerThreshold = state.metadata["${PREVIEW_PREFIX}followerThreshold"]?.toIntOrNull() ?: 0,
      threshold = threshold.coerceIn(0, 10000),
      roll = state.metadata["${PREVIEW_PREFIX}roll"]?.toIntOrNull(),
      success = state.metadata["${PREVIEW_PREFIX}success"].toBoolean()
    )
  }

  private fun withEnvironmentFailures(state: GameState, failures: Int): GameState {
    val metadata = state.metadata.toMutableMap()
    if (failures > 0) metadata[ENVIRONMENT_PITY_KEY] = failures.coerceAtMost(GUARANTEED_TURN - 1).toString()
    else metadata.remove(ENVIRONMENT_PITY_KEY)
    return state.copy(metadata = metadata)
  }

  fun commitPrepared(state: GameState, sessionId: String, kind: ActionKind, location: String?): GameState {
    if (!eligible(kind)) return state
    val marker = "levelLootRolled:$sessionId"
    if (state.world[marker] != null) return state
    if (state.metadata["${PREVIEW_PREFIX}sessionId"] != sessionId) return state
    val preview = preparedPreview(state) ?: return state

    var next = state.copy(world = state.world + (marker to "NONE"))
    if (!preview.success) return withEnvironmentFailures(next, environmentFailures(state) + 1)

    next = withEnvironmentFailures(next, 0)
    val itemSeed = stablePositiveHash("$sessionId|${kind.name}|${location.orEmpty()}|item")
    val item = ItemCatalog.items[(itemSeed % ItemCatalog.items.size.toLong()).toInt()].stack()
    return next.copy(world = next.world + mapOf(
      marker to item.itemId,
      "levelLoot:$sessionId" to "${item.itemId}|${item.name}|1|SEARCH"
    ))
  }

  /** Compatibility entrypoint retained for older callers/tests. */
  fun onSearchCompleted(state: GameState, sessionId: String, location: String?): GameState {
    val prepared = prepareAction(state, sessionId, ActionKind.SEARCH, location)
    return commitPrepared(prepared, sessionId, ActionKind.SEARCH, location)
  }
}

'''
items = items[:entity_start] + loot_engines + items[world_start:]
ITEMS.write_text(items, encoding="utf-8")


# ActionRuntime prepares the exact roll at action start, then commits that same
# roll only when the action completes. Pipeline failure/interruption therefore
# neither consumes nor advances pity.
action = ACTION.read_text(encoding="utf-8")
action = replace_once(
    action,
    "    val next = state.copy(metadata = metadata)\n",
    "    val next = LevelLootEngine.prepareAction(state.copy(metadata = metadata), sessionId, kind, locationKey)\n",
    "prepare environment loot with action session",
)
finish_old = '''    val cleared = state.metadata.filterKeys { !it.startsWith(PREFIX) }.toMutableMap()
    cleared["lastAction.sessionId"] = session.sessionId
    cleared["lastAction.turnId"] = session.turnId
    cleared["lastAction.actorId"] = session.actorId
    cleared["lastAction.kind"] = session.kind.name
    cleared["lastAction.phase"] = phase.name
    cleared["lastAction.elapsedMinutes"] = session.elapsedMinutes.toString()
    cleared["lastAction.reason"] = reason
    var next = state.copy(metadata = cleared)
    if (phase == ActionPhase.COMPLETED && session.kind == ActionKind.SEARCH) {
      next = LevelLootEngine.onSearchCompleted(next, session.sessionId, session.locationKey)
    }
    return ActionRuntimeResult(next, session.copy(phase = phase), applied = true)
'''
finish_new = '''    var preparedState = state
    if (phase == ActionPhase.COMPLETED && (session.kind == ActionKind.SEARCH || session.kind == ActionKind.EXPLORE)) {
      preparedState = LevelLootEngine.commitPrepared(preparedState, session.sessionId, session.kind, session.locationKey)
    }
    val cleared = preparedState.metadata.filterKeys { !it.startsWith(PREFIX) }.toMutableMap()
    cleared["lastAction.sessionId"] = session.sessionId
    cleared["lastAction.turnId"] = session.turnId
    cleared["lastAction.actorId"] = session.actorId
    cleared["lastAction.kind"] = session.kind.name
    cleared["lastAction.phase"] = phase.name
    cleared["lastAction.elapsedMinutes"] = session.elapsedMinutes.toString()
    cleared["lastAction.reason"] = reason
    val next = preparedState.copy(metadata = cleared)
    return ActionRuntimeResult(next, session.copy(phase = phase), applied = true)
'''
action = replace_once(action, finish_old, finish_new, "commit prepared SEARCH/EXPLORE loot")
ACTION.write_text(action, encoding="utf-8")


# Expose the prepared authoritative result to the writer. This is a projection,
# not a second roll, so the Game Master and world-item engine cannot disagree.
facade = FACADE.read_text(encoding="utf-8")
context_anchor = '        put("searchDepth", active.searchDepth?.name ?: JSONObject.NULL)\n'
context_insert = '''        put("searchDepth", active.searchDepth?.name ?: JSONObject.NULL)
        LevelLootEngine.preparedPreview(state)?.let { loot ->
          put("loot", JSONObject().apply {
            put("eligible", loot.eligible)
            put("baseThreshold", loot.baseThreshold)
            put("pityTurn", loot.pityTurn)
            put("pityBonusPercent", loot.pityThreshold / 100.0)
            put("followerBonusPercent", loot.followerThreshold / 100.0)
            put("threshold", loot.threshold)
            put("chancePercent", loot.chancePercent)
            if (loot.roll == null) put("roll", JSONObject.NULL) else put("roll", loot.roll)
            put("success", loot.success)
          })
        }
'''
facade = replace_once(facade, context_anchor, context_insert, "project authoritative loot preview")
FACADE.write_text(facade, encoding="utf-8")


# MainActivity's historical generic loot dice becomes a view of the Core roll.
# Do not use thresholdRoll here: that would reroll and recreate the mismatch this
# patch exists to remove.
main = MAIN.read_text(encoding="utf-8")
roll_signature = "  private JSONObject makeGameplayRolls(JSONObject state, String actionKind, String action, boolean meta) throws Exception {\n"
helper = r'''  private JSONObject authoritativeEnvironmentLootRoll(boolean eligible) throws Exception {
    JSONObject result = new JSONObject()
      .put("label", "loot")
      .put("dice", "d10000")
      .put("max", 10000)
      .put("threshold", 0)
      .put("eligible", false)
      .put("chancePercent", 0.0)
      .put("chance", "0.0000%")
      .put("roll", JSONObject.NULL)
      .put("success", false);
    if (!eligible) return result;

    JSONObject runtime = new JSONObject(requireGameCore().currentActionContext());
    JSONObject loot = runtime.optJSONObject("loot");
    if (loot == null || !loot.optBoolean("eligible", false)) return result;
    int threshold = Math.max(0, Math.min(10000, loot.optInt("threshold", 0)));
    double percent = threshold / 100.0;
    boolean guaranteed = threshold >= 10000;
    result.put("dice", guaranteed ? "none" : "d10000")
      .put("threshold", threshold)
      .put("eligible", true)
      .put("chancePercent", percent)
      .put("chance", String.format(java.util.Locale.ROOT, "%.4f%% pity turn %d", percent, loot.optInt("pityTurn", 1)))
      .put("roll", guaranteed || loot.isNull("roll") ? JSONObject.NULL : loot.optInt("roll", 0))
      .put("success", loot.optBoolean("success", false));
    return result;
  }

'''
if "private JSONObject authoritativeEnvironmentLootRoll(boolean eligible)" not in main:
    if roll_signature not in main:
        raise RuntimeError("Loot pity could not find typed makeGameplayRolls signature")
    main = main.replace(roll_signature, helper + roll_signature, 1)

loot_old = '''    int luciaScoutBonus = (partyHas(state, "lucia") || partyHas(state, "lục")) ? 500 : 0;
    int lootThreshold = Math.min(10000, lootThresholds[level] + (anNhienFollowing ? 1000 : 0) + luciaScoutBonus);
    String lootSuffix = (anNhienFollowing ? " +10% An Nhiên" : "") +
      (luciaScoutBonus > 0 ? " + Lucia Trinh sát chiến trường 5%" : "");
    rolls.put("loot", thresholdRoll("loot", 10000, lootThreshold, search, lootSuffix));
'''
loot_new = '''    boolean lootAction = "SEARCH".equals(actionKindNormalized) || "EXPLORE".equals(actionKindNormalized);
    rolls.put("loot", authoritativeEnvironmentLootRoll(lootAction));
'''
main = replace_once(main, loot_old, loot_new, "replace duplicate Java loot RNG with Core projection")
MAIN.write_text(main, encoding="utf-8")


# Every finalized death path eventually calls clearCombatOnly(). Put Entity drop
# authority there so kills caused by Kai, followers, Bleeding, ultimates or boss
# compatibility layers all get exactly one idempotent drop evaluation.
combat = COMBAT.read_text(encoding="utf-8")
clear_anchor = '''  private fun clearCombatOnly(state: GameState): GameState {
    val metadata = state.metadata.filterKeys { !it.startsWith(PREFIX) }
'''
clear_new = '''  private fun clearCombatOnly(state: GameState): GameState {
    val defeated = state.metadata["${PREFIX}entityCondition"] == EntityCondition.DESTROYED.name
    val defeatId = state.metadata["${PREFIX}encounterId"].orEmpty()
    val lootResolvedState = if (defeated && defeatId.isNotBlank()) EntityLootEngine.onDefeat(state, defeatId, lootRng) else state
    val metadata = lootResolvedState.metadata.filterKeys { !it.startsWith(PREFIX) }
'''
combat = replace_once(combat, clear_anchor, clear_new, "centralize Entity drop on combat cleanup")
# The finalized body may still reference `state.copy` after the replaced header.
# Restrict this correction to clearCombatOnly's method body only.
clear_start = combat.index("  private fun clearCombatOnly(state: GameState): GameState {\n")
clear_end = combat.find("\n  private fun ", clear_start + 1)
if clear_end < 0:
    raise RuntimeError("Could not bound clearCombatOnly after Entity drop insertion")
clear_body = combat[clear_start:clear_end]
clear_body = clear_body.replace("state.copy(metadata = metadata)", "lootResolvedState.copy(metadata = metadata)")
combat = combat[:clear_start] + clear_body + combat[clear_end:]
COMBAT.write_text(combat, encoding="utf-8")


# Regression coverage: exact +2% Entity pity, 50th-kill guarantee/reset, exact
# +1% environment turn contribution, 100th-turn guarantee and EXPLORE support.
official = OFFICIAL_TEST.read_text(encoding="utf-8")
official = official.replace(
    "  @Test fun entityLootIsOnePercentTotalOneItemAndIdempotent() {\n",
    "  @Test fun entityLootStartsAtTwoPercentTotalOneItemAndIdempotent() {\n",
    1,
)
entity_tests = r'''
  @Test fun entityLootPityAddsTwoPointsPerMissAndGuaranteesKillFifty() {
    var state = GameState.initial()
    val alwaysHigh = LootRng { bound -> if (bound == 100) 99 else 0 }
    repeat(49) { index ->
      state = EntityLootEngine.onDefeat(state, "pity-miss-$index", alwaysHigh)
      assertFalse(state.world.keys.any { it == "entityLoot:pity-miss-$index" })
    }
    assertEquals(49, EntityLootEngine.killsWithoutDrop(state))
    assertEquals(100, EntityLootEngine.dropChancePercent(state))

    val guaranteed = EntityLootEngine.onDefeat(state, "pity-guaranteed-50", alwaysHigh)
    assertTrue(guaranteed.world.containsKey("entityLoot:pity-guaranteed-50"))
    assertEquals(0, EntityLootEngine.killsWithoutDrop(guaranteed))
    assertEquals(2, EntityLootEngine.dropChancePercent(guaranteed))
  }
'''
if "entityLootPityAddsTwoPointsPerMissAndGuaranteesKillFifty" not in official:
    close = official.rfind("\n}")
    if close < 0:
        raise RuntimeError("OfficialItemSystemTest closing brace missing")
    official = official[:close] + "\n" + entity_tests.rstrip() + official[close:]
OFFICIAL_TEST.write_text(official, encoding="utf-8")

action_test = ACTION_TEST.read_text(encoding="utf-8")
environment_tests = r'''
  @Test fun environmentLootStartsWithBasePlusOnePercentAndGuaranteesTurnHundred() {
    val level0 = stateAt().copy(world = stateAt().world + ("levelJson" to "{\"number\":0}"))
    val first = ActionRuntime.start(level0, "LOOT-1", "TURN_1", KAI_ID, ActionKind.SEARCH, "search").state
    val firstPreview = requireNotNull(LevelLootEngine.preparedPreview(first))
    assertEquals(35, firstPreview.baseThreshold)
    assertEquals(1, firstPreview.pityTurn)
    assertEquals(135, firstPreview.threshold)

    val unlucky = level0.copy(metadata = level0.metadata + (LevelLootEngine.ENVIRONMENT_PITY_KEY to "99"))
    val hundredth = ActionRuntime.start(unlucky, "LOOT-100", "TURN_100", KAI_ID, ActionKind.EXPLORE, "explore").state
    val hundredthPreview = requireNotNull(LevelLootEngine.preparedPreview(hundredth))
    assertEquals(100, hundredthPreview.pityTurn)
    assertEquals(10000, hundredthPreview.threshold)
    assertTrue(hundredthPreview.success)
    assertNull(hundredthPreview.roll)

    val completed = ActionRuntime.complete(hundredth, "LOOT-100")
    assertTrue(completed.applied)
    assertTrue(completed.state.world.containsKey("levelLoot:LOOT-100"))
    assertEquals(0, LevelLootEngine.environmentFailures(completed.state))
  }
'''
if "environmentLootStartsWithBasePlusOnePercentAndGuaranteesTurnHundred" not in action_test:
    close = action_test.rfind("\n}")
    if close < 0:
        raise RuntimeError("ActionRuntimeTest closing brace missing")
    action_test = action_test[:close] + "\n" + environment_tests.rstrip() + action_test[close:]
ACTION_TEST.write_text(action_test, encoding="utf-8")


# Final contracts are intentionally explicit because this patch runs at the end
# of a long historical transform chain.
contracts = {
    ITEMS: [
        'const val DROP_STEP_PERCENT = 2',
        'const val GUARANTEED_KILL = 50',
        'const val ENVIRONMENT_PITY_KEY = "lootPity.environmentFailures"',
        'const val PITY_STEP_BASIS_POINTS = 100',
        'const val GUARANTEED_TURN = 100',
        'bonus += 1000',
        'bonus += 500',
        'fun prepareAction(',
        'fun commitPrepared(',
    ],
    ACTION: [
        'LevelLootEngine.prepareAction(',
        'session.kind == ActionKind.SEARCH || session.kind == ActionKind.EXPLORE',
        'LevelLootEngine.commitPrepared(',
    ],
    FACADE: [
        'LevelLootEngine.preparedPreview(state)',
        'put("pityTurn", loot.pityTurn)',
        'put("success", loot.success)',
    ],
    MAIN: [
        'private JSONObject authoritativeEnvironmentLootRoll(boolean eligible)',
        'boolean lootAction = "SEARCH".equals(actionKindNormalized) || "EXPLORE".equals(actionKindNormalized);',
        'rolls.put("loot", authoritativeEnvironmentLootRoll(lootAction));',
    ],
    COMBAT: [
        'EntityLootEngine.onDefeat(state, defeatId, lootRng)',
        'val lootResolvedState =',
    ],
}
for path, markers in contracts.items():
    source = path.read_text(encoding="utf-8")
    for marker in markers:
        if marker not in source:
            raise RuntimeError(f"Loot pity final contract missing in {path.name}: {marker}")

if 'rolls.put("loot", thresholdRoll("loot", 10000, lootThreshold' in MAIN.read_text(encoding="utf-8"):
    raise RuntimeError("Historical independent Java loot RNG survived authoritative pity finalizer")

print("Loot pity installed: environment +1 percentage point per completed SEARCH/EXPLORE turn (guaranteed by 100), Entity drops +2 points per consecutive kill (guaranteed by 50).")
