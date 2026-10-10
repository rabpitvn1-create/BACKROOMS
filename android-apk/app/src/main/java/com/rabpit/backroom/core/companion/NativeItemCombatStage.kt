package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

/**
 * Detached native mutations, not a durable turn/receipt or a source of sensory evidence.
 * The store adapter must authorize the pinned snapshot and persist the reservation before
 * publishing anything. Ordinary item time/exit capture and typed combat decision locking
 * belong to that adapter. No caller charges, HP, dice, metadata or legal-target booleans enter Core.
 */
internal object NativeItemCombatStage {
  private const val VERSION = "native_item_combat_stage.v1"
  private const val MAX_DRAWS = 4096
  private const val MAX_BYTES = 1048576

  enum class Operation { USE_ITEM, HOLD, ROLL, FINISH, TARGET, RESOLVE }
  data class Command(
    val operation: Operation,
    val actorId: String,
    val commandId: String,
    val expectedSnapshotDigest: String,
    val itemId: String? = null,
    val expectedCombatRevision: Long? = null,
    val dieIndex: Int? = null,
    val held: Boolean? = null,
    val entityIndex: Int? = null
  )
  data class Draw(val bound: Int, val value: Int)
  class Staged internal constructor(
    val beforeSnapshot: String,
    val afterSnapshot: String,
    val command: Command,
    draws: List<Draw>,
    events: List<String>
  ) {
    val draws: List<Draw> = Collections.unmodifiableList(ArrayList(draws))
    val events: List<String> = Collections.unmodifiableList(ArrayList(events))
    fun encode(): String = envelope(this)
  }

  /** Calls the existing scoped producer. Never creates, seeds or substitutes an RNG. */
  fun capture(state: GameState, command: Command): Staged {
    val before = validate(state, command)
    val draws = ArrayList<Draw>()
    val result = CompanionCombatRngBridge.capture({ apply(GameStateCodec.decode(before), command) },
      { bound, value ->
        require(draws.size < MAX_DRAWS) { "native_stage_draw_limit" }
        require(bound > 0 && value in 0 until bound) { "native_stage_draw_invalid" }
        draws.add(Draw(bound, value))
      })
    return finish(before, command, result, draws)
  }

  /** No RNG input; verifies the complete binding, native result and all original draws. */
  fun replay(state: GameState, command: Command, reserved: String): Staged {
    val before = validate(state, command)
    require(reserved.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "native_stage_size" }
    val json = JSONObject(reserved)
    require(json.getString("version") == VERSION) { "native_stage_version" }
    require(json.getString("before") == before &&
      CompanionWaitCapture.canonical(json.getJSONObject("command")) ==
      CompanionWaitCapture.canonical(commandJson(command))) { "native_stage_binding" }
    val records = json.getJSONArray("draws")
    require(records.length() <= MAX_DRAWS) { "native_stage_draw_limit" }
    val draws = (0 until records.length()).map { index ->
      val row = records.getJSONArray(index)
      require(row.length() == 2) { "native_stage_draw_fields" }
      // getInt coerces strings/floats; canonical envelope equality below rejects those forms.
      val bound = row.getInt(0); val value = row.getInt(1)
      require(bound > 0 && value in 0 until bound) { "native_stage_draw_invalid" }
      Draw(bound, value)
    }
    var cursor = 0
    val result = CompanionCombatRngBridge.replay({ apply(GameStateCodec.decode(before), command) }, { bound ->
      require(cursor < draws.size) { "native_stage_missing_draw" }
      val row = draws[cursor++]
      require(row.bound == bound) { "native_stage_draw_bound" }
      row.value
    })
    require(cursor == draws.size) { "native_stage_extra_draw" }
    val staged = finish(before, command, result, draws)
    require(staged.encode() == reserved) { "native_stage_result_or_encoding" }
    return staged
  }

  private fun validate(state: GameState, command: Command): String {
    require(state.saveVersion == CURRENT_SAVE_VERSION && state.turn.pending == null &&
      ActionRuntime.activeSession(state) == null) { "native_stage_snapshot_busy" }
    val before = GameStateCodec.encode(state)
    require(GameStateCodec.decode(before) == state) { "native_stage_snapshot_unstable" }
    require(CompanionDigests.sha256(before) == command.expectedSnapshotDigest) { "native_stage_snapshot_mismatch" }
    require(command.commandId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))) { "native_stage_command_id" }
    require(command.commandId !in state.turn.executedCommandIds) { "native_stage_command_reused" }
    val actor = state.characters[command.actorId] ?: error("native_stage_actor_unknown")
    require(actor.presence == CharacterPresence.ACTIVE) { "native_stage_actor_unavailable" }
    if (command.operation == Operation.USE_ITEM) {
      require(command.itemId != null && command.expectedCombatRevision == null && command.dieIndex == null &&
        command.held == null && command.entityIndex == null) { "native_stage_item_arguments" }
      require(!Combat93Runtime.active(state)) { "native_stage_item_combat_unsupported" }
    } else {
      require(command.itemId == null && command.expectedCombatRevision != null &&
        command.expectedCombatRevision >= 0 &&
        command.expectedCombatRevision == Combat93Runtime.revision(state)) { "native_stage_combat_revision" }
      require(Combat93Runtime.active(state)) { "native_stage_combat_inactive" }
      val combat = Combat93Runtime.toJson(state) ?: error("native_stage_combat_missing")
      val participant = combat.getJSONArray("participants").getJSONObject(combat.getInt("actorIndex"))
      require(participant.getString("id") == command.actorId) { "native_stage_combat_actor" }
      // Unscoped legacy deterministic encounters need their original migration path, not a new RNG.
      require(combat.optString("rngTurnId").isNotBlank()) { "native_stage_combat_unscoped" }
      val dice = combat.getJSONObject("diceState")
      val held = dice.getJSONArray("held")
      when (command.operation) {
        Operation.HOLD -> require(command.dieIndex != null && command.held != null && command.entityIndex == null &&
          command.dieIndex in 0 until held.length()) { "native_stage_hold_arguments" }
        Operation.TARGET -> require(command.entityIndex != null && command.dieIndex == null && command.held == null) {
          "native_stage_target_arguments"
        }
        else -> require(command.dieIndex == null && command.held == null && command.entityIndex == null)
      }
      when (command.operation) {
        Operation.HOLD -> require(dice.getBoolean("hasRolled") && !dice.getBoolean("finalized") &&
          held.getBoolean(command.dieIndex!!) != command.held) { "native_stage_hold_noop" }
        Operation.ROLL -> require(!dice.getBoolean("finalized") &&
          (!dice.getBoolean("hasRolled") || (dice.getInt("rerollsUsed") < dice.getInt("maxRerolls") &&
            (0 until held.length()).any { !held.getBoolean(it) }))) { "native_stage_roll_noop" }
        Operation.FINISH -> require(dice.getBoolean("hasRolled") && !dice.getBoolean("finalized")) {
          "native_stage_finish_noop"
        }
        Operation.RESOLVE -> require(dice.getBoolean("finalized") && !dice.getBoolean("resolved")) {
          "native_stage_resolve_noop"
        }
        Operation.TARGET -> require(command.entityIndex != combat.getInt("targetEntityIndex")) { "native_stage_target_noop" }
        Operation.USE_ITEM -> error("native_stage_operation")
      }
    }
    return before
  }

  private fun apply(state: GameState, command: Command): Pair<GameState, List<String>> {
    if (command.operation == Operation.USE_ITEM) {
      val stack = state.inventories[command.actorId]?.items?.get(command.itemId)
        ?: error("native_stage_item_not_owned")
      require(stack.quantity > 0) { "native_stage_item_empty" }
      val native = ItemCommand(command.commandId, state.turn.currentTurnId, command.actorId,
        source = CommandSource.SYSTEM, operation = ItemCommand.Operation.USE,
        itemId = stack.itemId, itemName = stack.name, quantity = 1)
      val result = StateReducer.execute(state, native)
      require(result.applied && !result.duplicate && result.validation.valid) {
        result.validation.reason ?: "native_stage_item_rejected"
      }
      return result.state to result.events
    }
    val revision = command.expectedCombatRevision!!
    val next = when (command.operation) {
      Operation.HOLD -> Combat93Runtime.hold(state, command.dieIndex!!, command.held!!, revision)
      Operation.ROLL -> Combat93Runtime.roll(state, revision)
      Operation.FINISH -> Combat93Runtime.finish(state, revision)
      Operation.TARGET -> Combat93Runtime.target(state, command.entityIndex!!, revision)
      Operation.RESOLVE -> {
        val result = Combat93Runtime.resolve(state, revision)
        require(result.handled) { "native_stage_combat_unhandled" }
        result.state
      }
      Operation.USE_ITEM -> error("native_stage_operation")
    }
    require(next != state) { "native_stage_combat_noop" }
    // Duplicate binding is committed with the native snapshot, never as a separate side write.
    return next.copy(turn = next.turn.copy(executedCommandIds = next.turn.executedCommandIds + command.commandId)) to
      listOf("combat_native_" + command.operation.name.lowercase())
  }

  private fun finish(before: String, command: Command, result: Pair<GameState, List<String>>, draws: List<Draw>): Staged {
    val after = GameStateCodec.encode(result.first)
    require(GameStateCodec.decode(after) == result.first) { "native_stage_result_unstable" }
    return Staged(before, after, command, draws, result.second).also { it.encode() }
  }
  private fun commandJson(command: Command): JSONObject = JSONObject()
    .put("operation", command.operation.name).put("actor", command.actorId).put("id", command.commandId)
    .put("snapshot", command.expectedSnapshotDigest).put("item", command.itemId ?: JSONObject.NULL)
    .put("combatRevision", command.expectedCombatRevision ?: JSONObject.NULL)
    .put("die", command.dieIndex ?: JSONObject.NULL).put("held", command.held ?: JSONObject.NULL)
    .put("entity", command.entityIndex ?: JSONObject.NULL)
  private fun envelope(staged: Staged): String {
    val json = JSONObject().put("version", VERSION).put("command", commandJson(staged.command))
      .put("before", staged.beforeSnapshot).put("after", staged.afterSnapshot)
      .put("draws", JSONArray().apply { staged.draws.forEach { put(JSONArray(listOf(it.bound, it.value))) } })
      .put("events", JSONArray(staged.events))
    return CompanionWaitCapture.canonical(json).also {
      require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "native_stage_size" }
    }
  }
}
