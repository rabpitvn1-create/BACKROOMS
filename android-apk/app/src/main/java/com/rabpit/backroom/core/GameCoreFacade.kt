package com.rabpit.backroom.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class GameCoreFacade private constructor(
  private val repository: SaveRepository,
  private val logger: GamePipelineLogger,
  private val levelEngine: LevelTravelEngine
) : AutoCloseable {
  private val rules = RuleIntentInterpreter()
  private val resolver = CommandResolver()

  /** Only the explicit New Game path may draw a starting Level. No old-save conversion. */
  @Synchronized fun initializeLevelGame(legacyStateJson: String, fresh: Boolean): String {
    val legacy = JSONObject(legacyStateJson)
    var core = if (fresh) levelEngine.start(GameState.initial().copy(world = mapOf(
      "flagsJson" to (legacy.optJSONObject("flags") ?: JSONObject()).toString()
    ))) else {
      val snapshot = legacy.getJSONObject("coreSnapshot")
      require(snapshot.getInt("saveVersion") == CURRENT_SAVE_VERSION) { "save_version_unsupported" }
      val saved = GameStateCodec.decode(snapshot).also { LevelTravelEngine.read(it) }
      val persisted = if (repository.exists()) repository.load() else null
      if (persisted != null && persisted.world[LevelTravelEngine.KEY] != null &&
        LevelTravelEngine.read(persisted).getString("runId") == LevelTravelEngine.read(saved).getString("runId") &&
        (persisted.world["levelTravelUiTurn"]?.toIntOrNull() ?: 1) >= (saved.world["levelTravelUiTurn"]?.toIntOrNull() ?: 1)) persisted else saved
    }
    val recovered = core.world["levelTravelUi"]?.let(::JSONObject) ?: legacy
    val output = LevelTravelEngine.project(core, if (fresh) legacy else recovered)
    output.put("turn", core.world["levelTravelUiTurn"]?.toIntOrNull() ?: 1)
    if (fresh) {
      output.put("turn", 1)
      output.put("log", JSONArray().put(JSONObject().put("role", "gm").put("text",
        "Cao Minh xuất hiện tại " + output.getString("location") + ". Liên lạc với Lục Trầm và Syvial vẫn gián đoạn.")))
    }
    output.remove("coreSnapshot")
    core = core.copy(world = core.world + ("levelTravelUi" to output.toString()))
    repository.save(core)
    output.put("coreSnapshot", JSONObject(GameStateCodec.encode(core)))
    return output.toString()
  }

  /** App startup resumes the committed Core even if the WebView autosave never completed. */
  @Synchronized fun resumeLevelGame(legacyStateJson: String, hasBrowserSave: Boolean): String {
    if (repository.exists()) {
      val core = repository.load()
      if (LevelTravelEngine.KEY in core.world) {
        val legacy = core.world["levelTravelUi"]?.let(::JSONObject) ?: JSONObject(legacyStateJson)
        val output = LevelTravelEngine.project(core, legacy)
        output.put("turn", core.world["levelTravelUiTurn"]?.toIntOrNull() ?: 1)
          .put("coreSnapshot", JSONObject(GameStateCodec.encode(core)))
        return output.toString()
      }
      error("save_version_unsupported")
    }
    return initializeLevelGame(legacyStateJson, !hasBrowserSave)
  }

  /** Runs before ALL local/GM action handlers; a valid exit is fully resolved locally. */
  @Synchronized fun processLevelAction(legacyStateJson: String, action: String): String {
    val legacy = JSONObject(legacyStateJson)
    val current = repository.load()
    val expected = legacy.getJSONObject(LevelTravelEngine.KEY)
    check(LevelTravelEngine.read(current).getString("runId") == expected.getString("runId")) { "stale_game_run" }
    if (!LevelTravelEngine.isExitAction(action)) {
      check(legacy.getInt("turn") == (current.world["levelTravelUiTurn"]?.toIntOrNull() ?: 1)) { "stale_level_turn" }
      return response(false, LevelTravelEngine.project(current, legacy).apply { remove("exitResult") }, null, "not_exit_action")
    }
    return try {
      val runId = legacy.getJSONObject(LevelTravelEngine.KEY).getString("runId")
      val turn = legacy.getInt("turn")
      val outcome = levelEngine.attempt(current, runId, turn, action)
      if (outcome.state == current) {
        val latest = LevelTravelEngine.project(current, current.world["levelTravelUi"]?.let(::JSONObject) ?: legacy)
        latest.put("turn", current.world["levelTravelUiTurn"]?.toIntOrNull() ?: turn + 1)
        latest.put("exitResult", outcome.receipt).put("coreSnapshot", JSONObject(GameStateCodec.encode(current)))
        return response(true, latest, null, "core_exit_duplicate")
      }
      val output = LevelTravelEngine.project(outcome.state, legacy)
      output.put("turn", turn + 1).put("exitResult", outcome.receipt)
      output.put("flags", JSONObject(outcome.state.world["flagsJson"] ?: "{}"))
      val r = outcome.receipt
      val reply = when {
        r.getBoolean("completed") -> "Thoát thành công 3/3. Cao Minh tới " + output.getString("location") + ". Chuỗi thoát mới: 0/3."
        r.getBoolean("success") -> "Lần thử thoát thành công: " + r.getInt("streak") + "/3. Cao Minh vẫn ở địa điểm hiện tại."
        else -> "Lần thử thoát thất bại. Chuỗi trở về 0/3; Cao Minh vẫn ở địa điểm hiện tại."
      }
      appendLog(output, action, reply)
      output.remove("coreSnapshot")
      val committed = outcome.state.copy(world = outcome.state.world + ("levelTravelUi" to output.toString()))
      if (outcome.state != current) repository.save(committed)
      output.put("coreSnapshot", JSONObject(GameStateCodec.encode(if (outcome.state != current) committed else current)))
      response(true, output, null, "core_exit_committed", reply)
    } catch (error: Exception) {
      // Never route a rejected/failed exit to the GM to invent a different result.
      throw IllegalStateException(error.message ?: "exit_transaction_failed", error)
    }
  }

  /** Persist validated local movement; the GM never owns Level identity or streak. */
  @Synchronized fun finishLevelTurn(beforeJson: String, candidateJson: String, action: String): String {
    val current = repository.load()
    val before = JSONObject(beforeJson)
    val travel = LevelTravelEngine.read(current)
    val expected = before.getJSONObject(LevelTravelEngine.KEY)
    check(travel.getString("runId") == expected.getString("runId") &&
      travel.getInt("revision") == expected.getInt("revision") &&
      before.getInt("turn") == (current.world["levelTravelUiTurn"]?.toIntOrNull() ?: 1)) { "stale_level_turn" }
    val candidate = LevelTravelEngine.protectCandidate(current, JSONObject(candidateJson), action)
    candidate.remove("coreSnapshot")
    val next = current.copy(world = current.world + mapOf(
      "location" to candidate.getString("location"), "flagsJson" to candidate.getJSONObject("flags").toString(),
      "levelTravelUiTurn" to candidate.getInt("turn").toString(), "levelTravelUi" to candidate.toString()
    ))
    repository.save(next)
    candidate.put("coreSnapshot", JSONObject(GameStateCodec.encode(next)))
    return candidate.toString()
  }

  /** Fast deterministic pass. Gemini is never called from this method. */
  fun processRule(legacyStateJson: String, action: String): String {
    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
    val turnId = nextTurnId(legacy, state)
    logger.log(PipelineLogEvent("INPUT", turnId = turnId, details = mapOf("length" to action.length.toString())))
    val pending = TurnCoordinator.createPending(state, turnId, action)
    if (pending.error != null) return response(false, legacy, pending.error, "pending_rejected")
    val context = contextFor(pending.state)
    val interpreted = rules.interpretSync(action, context)
    interpreted.candidates.forEach { logger.log(PipelineLogEvent("INTENT", turnId = turnId, source = it.source, intent = it.intent, confidence = it.score)) }

    // Player text never has authority to manufacture an acquisition event. Reject immediately,
    // do not call Gemini, do not advance the turn, and do not mutate Inventory.
    if (isDirectPlayerPickupAction(action) || interpreted.candidates.any { it.intent == GameIntent.PICKUP_ITEM }) {
      val result = syncLegacy(legacy, state, incrementTurn = false)
      val reply = validationReply("player_pickup_unavailable")
      appendLog(result, action, reply)
      logger.log(PipelineLogEvent("REJECT", turnId = turnId, details = mapOf("reason" to "player_pickup_unavailable")))
      return response(true, result, "player_pickup_unavailable", "validation_rejected", reply)
    }

    // Restore is lore/narrative-only. Route prose to the GM, but authoritative state mutation is
    // explicitly suppressed again in processValidatedCandidate().
    if (interpreted.candidates.any { it.intent == GameIntent.VAN_KHO_GIOI_CHI_RESTORE }) {
      return response(false, legacy, null, "fallback_required")
    }

    if (interpreted.candidates.any { it.intent == GameIntent.NO_ACTION || it.confidence != IntentConfidence.HIGH }) {
      return response(false, legacy, null, "fallback_required")
    }
    val resolvedCommands = interpreted.candidates.mapIndexedNotNull { index, candidate -> resolver.resolve(candidate, index, turnId, context) }
    if (resolvedCommands.size != interpreted.candidates.size || resolvedCommands.isEmpty()) return response(false, legacy, null, "resolution_incomplete")
    val commands = resolvedCommands.toMutableList()
    commands += timeAdvanceCommand(turnId, action)
    commands.forEach { logger.log(PipelineLogEvent("COMMAND", turnId, it.commandId, it.source)) }
    val committed = TurnCoordinator.commit(pending.state, commands)
    if (committed.error != null) {
      val rejected = TurnCoordinator.reject(pending.state, committed.error)
      repository.save(rejected.state)
      val result = syncLegacy(legacy, rejected.state, incrementTurn = true)
      appendLog(result, action, validationReply(committed.error))
      return response(true, result, committed.error, "validation_rejected", validationReply(committed.error))
    }
    repository.save(committed.state)
    val result = syncLegacy(legacy, committed.state, incrementTurn = true)
    val reply = eventReply(committed.execution?.events.orEmpty())
    appendLog(result, action, reply)
    logger.log(PipelineLogEvent("COMMIT", turnId = turnId, details = mapOf("commands" to commands.size.toString())))
    return response(true, result, null, "committed", reply)
  }


  fun coreStats(legacyStateJson: String, characterId: String): String {
    val state = loadOrMigrate(JSONObject(legacyStateJson))
    return PokerDiceCore.statsJson(state, characterId).toString()
  }

  fun processCoreUpgrade(legacyStateJson: String, characterId: String, stat: String): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    if (CombatRuntime.active(current) != null) {
      return response(false, syncLegacy(legacy, current, incrementTurn = false),
        "combat_active", "core_upgrade_locked", "Không thể nâng Core khi combat đang hoạt động.")
    }
    return try {
      val upgraded = PokerDiceCore.upgrade(current, characterId, stat)
      repository.save(upgraded.state)
      val projected = syncLegacy(legacy, upgraded.state, incrementTurn = false)
      response(true, projected, null, "core_upgrade_committed",
        upgraded.stat + " +" + "1 · -" + upgraded.cost + " Core · còn " + upgraded.coreRemaining + " Core.")
    } catch (error: Exception) {
      response(false, syncLegacy(legacy, current, incrementTurn = false),
        error.message ?: "core_upgrade_failed", "core_upgrade_rejected")
    }
  }

  /**
   * Dice operations only change Core combat/dice state. Preserve already
   * validated narrative Party and Inventory from WebView between dice taps.
   */
  private fun projectCombatState(legacy: JSONObject, core: GameState): JSONObject {
    val output = JSONObject(legacy.toString())
    output.put("saveVersion", CURRENT_SAVE_VERSION)
    val combat = CombatRuntime.toJson(core)
    if (combat == null) output.remove("combat") else {
      PokerDiceCore.diceJson(core)?.let { combat.put("diceState", it) }
      output.put("combat", combat)
    }
    output.put("coreSystem", PokerDiceCore.statsJson(core, CAO_MINH_ID))
    return output
  }

  fun prepareCombatDice(legacyStateJson: String, action: String): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    val combat = CombatRuntime.active(current)
      ?: return response(false, projectCombatState(legacy, current),
        "combat_inactive", "combat_dice_inactive")
    return try {
      val next = PokerDiceCore.prepare(current, action, combat.encounterId)
      repository.save(next)
      response(true, projectCombatState(legacy, next), null, "combat_dice_prepared")
    } catch (error: Exception) {
      response(false, projectCombatState(legacy, current),
        error.message ?: "combat_dice_prepare_failed", "combat_dice_rejected")
    }
  }

  fun setCombatDiceHold(legacyStateJson: String, dieIndex: Int, held: Boolean): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    return try {
      val next = PokerDiceCore.setHold(current, dieIndex, held)
      repository.save(next)
      response(true, projectCombatState(legacy, next), null, "combat_dice_hold")
    } catch (error: Exception) {
      response(false, projectCombatState(legacy, current),
        error.message ?: "combat_dice_hold_failed", "combat_dice_rejected")
    }
  }

  fun rerollCombatDice(legacyStateJson: String): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    return try {
      val next = PokerDiceCore.reroll(current)
      repository.save(next)
      response(true, projectCombatState(legacy, next), null, "combat_dice_rolled")
    } catch (error: Exception) {
      response(false, projectCombatState(legacy, current),
        error.message ?: "combat_dice_roll_failed", "combat_dice_rejected")
    }
  }

  fun finishCombatDice(legacyStateJson: String): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    return try {
      val next = PokerDiceCore.finish(current)
      repository.save(next)
      response(true, projectCombatState(legacy, next), null, "combat_dice_finalized")
    } catch (error: Exception) {
      response(false, projectCombatState(legacy, current),
        error.message ?: "combat_dice_finish_failed", "combat_dice_rejected")
    }
  }

  /** Start combat only after Android's locked entity-encounter roll and key validation. */
  fun startCombatState(legacyStateJson: String, entityKey: String): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    val started = CombatRuntime.start(current, entityKey.trim().lowercase())
    val active = CombatRuntime.active(started)
      ?: return response(false, projectCombatState(legacy, current), "entity_profile_unknown", "combat_start_rejected")
    repository.save(started)
    return response(true, projectCombatState(legacy, started), null, "combat_started", active.entityName)
  }

  /** Combat is authoritative and consumes exactly one finalized Poker Dice session. */
  fun processCombat(legacyStateJson: String, actionKind: String, action: String): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    val active = CombatRuntime.active(current)
      ?: return response(false, projectCombatState(legacy, current), "combat_inactive", "combat_rejected")
    if (!PokerDiceCore.isFinalizedFor(current, action, active.encounterId)) {
      return response(false, projectCombatState(legacy, current), "combat_dice_not_finalized", "combat_rejected")
    }
    val resolution = CombatRuntime.resolve(current, actionKind, action)
    if (!resolution.handled) return response(false, projectCombatState(legacy, current), "combat_unhandled", "combat_rejected")
    var completed = PokerDiceCore.clearDice(resolution.state)
    val loot = if (resolution.entityDestroyed) {
      CoreLootEngine.roll(completed, CoreLootEngine.Source.ENTITY, active.encounterId)
    } else null
    if (loot != null) completed = loot.state
    repository.save(completed)
    // Combat changes HP and the combat snapshot, not the already validated
    // narrative inventory/party. The GM path is not yet wired into Core's
    // candidate reducer; projecting all Core inventories here would erase
    // unrelated objects/companions from the browser state.
    val output = projectCombatState(legacy, completed)
    completed.world["flagsJson"]?.let { output.put("flags", JSONObject(it)) }
    output.put("turn", legacy.optInt("turn", 1) + 1)
    completed.characters[CAO_MINH_ID]?.let { caoMinh ->
      val player = output.optJSONObject("player") ?: JSONObject()
      player.put("hp", caoMinh.vitalState.currentHp)
      output.put("player", player)
      output.optJSONObject("partyDetails")?.optJSONArray("members")?.let { members ->
        for (i in 0 until members.length()) {
          val member = members.optJSONObject(i) ?: continue
          if (member.optString("id") == CAO_MINH_ID) {
            member.put("currentHp", caoMinh.vitalState.currentHp)
            member.put("maxHp", CharacterStatEngine.effective(completed, CAO_MINH_ID).maxHp)
          }
        }
      }
    }
    output.put("coreSystem", PokerDiceCore.statsJson(completed, CAO_MINH_ID))
    output.put("combatFeedback", JSONObject().apply {
      put("id", "${active.encounterId}:${active.eventCounter + 1}")
      put("encounterId", active.encounterId)
      put("events", JSONArray().apply { resolution.feedback.forEach { put(it.toJson()) } })
    })
    val lootReply = loot?.dropped?.let { " ${it.name} xuất hiện tại vị trí Entity bị tiêu diệt." }.orEmpty()
    val reply = resolution.reply + lootReply
    appendLog(output, action, reply)
    return response(true, output, null, "combat_committed", reply)
  }

  /**
   * Roll a discovery independently of GM output, before the narration request.
   * The roll is idempotent for the current UI turn even if the request is retried.
   */
  @JvmOverloads @Synchronized fun processExplorationLoot(legacyStateJson: String, action: String, actionKind: String = ""): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    val eligible = actionKind.uppercase() in setOf("SEARCH", "EXPLORE") || CoreLootEngine.isExplorationAction(action)
    val outcome = if (eligible && CombatRuntime.active(current) == null) {
      val eventKey = "TURN_" + legacy.optInt("turn", 1).coerceAtLeast(1)
      CoreLootEngine.roll(current, CoreLootEngine.Source.EXPLORE, eventKey)
    } else CoreLootEngine.Outcome(current, false)
    if (outcome.rolled) repository.save(outcome.state)

    // This Core hook only owns world loot. Projecting the entire Core snapshot
    // here would overwrite other narrative fields/party and unrelated inventory
    // before the turn's actual action is validated.
    val output = JSONObject(legacy.toString())
    val flags = output.optJSONObject("flags")?.let { JSONObject(it.toString()) } ?: JSONObject()
    flags.remove("worldItems")
    val coreFlags = runCatching { JSONObject(outcome.state.world["flagsJson"] ?: "{}") }.getOrNull()
    coreFlags?.optJSONArray("worldItems")?.let { flags.put("worldItems", JSONArray(it.toString())) }
    output.put("flags", flags)
    return response(true, output, null,
      when {
        !eligible -> "core_loot_ineligible"
        outcome.dropped != null -> "core_loot_discovered"
        else -> "core_loot_checked"
      })
  }

  fun currentCoreState(): String = GameStateCodec.encode(repository.load())
  fun clear() = repository.clear()
  override fun close() = Unit

  /**
   * Commits only the gameplay delta already accepted by the legacy canon/dice validator.
   * Candidate prose/JSON never becomes storage directly: inventory and party are rebuilt
   * from commands and then projected back onto the UI state.
   */
  fun processValidatedCandidate(beforeJson: String, candidateJson: String, action: String): String {
    val before = JSONObject(beforeJson)
    val candidate = LevelTravelEngine.protectCandidate(loadOrMigrate(before), JSONObject(candidateJson), action)
    val core = loadOrMigrate(before)
    val turnId = nextTurnId(before, core)
    val pending = TurnCoordinator.createPending(core, turnId, action)
    if (pending.error != null) return response(false, before, pending.error, "pending_rejected")
    val commands = mutableListOf<GameCommand>()
    val current = pending.state.inventories[CAO_MINH_ID]?.items.orEmpty()
    val actionIntents = rules.interpretSync(action, contextFor(pending.state)).candidates.map { it.intent }.toSet()
    val inventoryLocked = true // INVENTORY_AUTHORITY: candidate snapshots are read-only

    val desiredById = mutableMapOf<String, ItemStack>()
    if (inventoryLocked) {
      desiredById.putAll(current)
    } else {
      val desiredInventory = candidate.optJSONArray("inventory") ?: JSONArray()
      for (index in 0 until desiredInventory.length()) {
        val json = desiredInventory.optJSONObject(index) ?: continue
        val name = json.optString("name").trim(); if (name.isEmpty()) continue
        val id = json.optString("id").ifBlank { stableItemId(name) }
        val currentStack = current[id]
        val metadata = currentStack?.metadata.orEmpty() + jsonObjectStrings(json.optJSONObject("metadata"))
        desiredById[id] = ItemStack(
          id,
          name,
          json.optInt("quantity", 1).coerceAtLeast(1),
          json.optString("state").takeIf(String::isNotBlank) ?: currentStack?.condition,
          metadata,
          currentStack?.archetypeId ?: id,
          currentStack?.contentState ?: ContentState.NONE
        )
      }
    }

    current.filterKeys { EquipmentCatalog.definition(it) != null }.forEach { (id, stack) -> desiredById[id] = stack }

    (current.keys + desiredById.keys).sorted().forEachIndexed { index, id ->
      val old = current[id]?.quantity ?: 0; val desired = desiredById[id]?.quantity ?: 0
      if (desired == old) return@forEachIndexed
      val stack = desiredById[id] ?: current.getValue(id)
      commands += ItemCommand(
        "$turnId:GEMINI:INV:$index", turnId, CAO_MINH_ID, source = CommandSource.GEMINI,
        operation = if (desired > old) ItemCommand.Operation.PICKUP else ItemCommand.Operation.DROP,
        itemId = id, itemName = stack.name, quantity = kotlin.math.abs(desired - old), metadata = stack.metadata
      )
    }

    val desiredParty = mutableMapOf<String, JSONObject>()
    val partyJson = candidate.optJSONArray("party") ?: JSONArray()
    for (index in 0 until partyJson.length()) {
      val member = partyJson.optJSONObject(index) ?: continue
      val id = member.optString("id").ifBlank { member.optString("name").trim().lowercase() }
      if (id.isNotBlank()) desiredParty[id] = member
    }
    val currentFollowers = pending.state.party.memberIds.filter { it != CAO_MINH_ID }.toSet()
    (currentFollowers - desiredParty.keys).sorted().forEachIndexed { index, id ->
      commands += PartyCommand("$turnId:GEMINI:PARTY_REMOVE:$index", turnId, CAO_MINH_ID, id, CommandSource.GEMINI, PartyCommand.Operation.REMOVE)
    }
    (desiredParty.keys - currentFollowers).sorted().forEachIndexed { index, id ->
      val member = desiredParty.getValue(id)
      val known = pending.state.characters[id]
      commands += PartyCommand(
        "$turnId:GEMINI:PARTY_ADD:$index", turnId, CAO_MINH_ID, id, CommandSource.GEMINI, PartyCommand.Operation.ADD,
        consentConfirmed = member.optBoolean("joinConfirmed", false) && known?.metadata?.get("joinEligible") == "true",
        targetPresent = member.optBoolean("present", false) && known?.presence == CharacterPresence.ACTIVE
      )
    }
    commands += ValidatedLegacyStateCommand(
      commandId = "$turnId:GEMINI:VALIDATED_STATE", turnId = turnId, source = CommandSource.GEMINI,
      location = candidate.optString("location").takeIf(String::isNotBlank),
      title = candidate.optString("title").takeIf(String::isNotBlank),
      levelJson = candidate.optJSONObject("level")?.toString(),
      playerJson = candidate.optJSONObject("player")?.toString(),
      flagsJson = candidate.optJSONObject("flags")?.toString(),
      validatedByGameEngine = true
    )
    commands += timeAdvanceCommand(turnId, action)

    val committed = TurnCoordinator.commit(pending.state, commands)
    if (committed.error != null) {
      logger.log(PipelineLogEvent("GEMINI_REJECTED", turnId = turnId, source = CommandSource.GEMINI, details = mapOf("reason" to committed.error)))
      return response(false, before, committed.error, "gemini_delta_rejected")
    }
    val protectedState = CharacterProgressionCore.protectFromCandidate(pending.state, committed.state)
    repository.save(protectedState)
    val synchronized = syncLegacy(candidate, protectedState, incrementTurn = false)
    logger.log(PipelineLogEvent("GEMINI_COMMIT", turnId = turnId, source = CommandSource.GEMINI, details = mapOf("commands" to commands.size.toString(), "inventoryLocked" to inventoryLocked.toString())))
    return response(true, synchronized, null, "gemini_delta_committed")
  }

  private fun loadOrMigrate(legacy: JSONObject): GameState {
    if (repository.exists()) return repository.load()
    val migrated = GameStateCodec.decode(legacy)
    repository.save(migrated)
    return migrated
  }

  private fun contextFor(state: GameState): GameContext {
    val actors = state.characters.values.associate { it.name.lowercase() to it.id } + mapOf("cao minh" to CAO_MINH_ID, "lục trầm" to "luc-tram", "syvial" to "syvial", "an nhiên" to AN_NHIEN_ID, "an nhien" to AN_NHIEN_ID)
    val items = (state.inventories.values.flatMap { it.items.values } + state.vanKhoGioiChi.storedItems.values).associate { it.name.lowercase() to it.itemId }
    return GameContext(state, actors, items)
  }

  private fun isDirectPlayerPickupAction(action: String): Boolean {
    val text = action.trim()
    val vanKhoGioiChiWithdrawal = Regex("(?:lấy|rút|triệu hồi).*(?:ra khỏi|khỏi|từ).*(?:vạn khố giới chỉ|giới chỉ|nhẫn|kho)", RegexOption.IGNORE_CASE).containsMatchIn(text)
    if (vanKhoGioiChiWithdrawal) return false
    val directVerb = Regex("(?:^|\\s)(?:nhặt|lượm|cầm\\s+lên|lấy(?:\\s+lên)?|thu\\s+hồi|tịch\\s+thu|nhận(?:\\s+lấy)?|pick\\s+up|take|receive)(?:\\s|$)", RegexOption.IGNORE_CASE)
    val inventoryAssertion = Regex("(?:thêm|bỏ|đưa).{0,80}(?:vào|trong)\\s+(?:inventory|kho đồ|túi đồ)", RegexOption.IGNORE_CASE)
    return directVerb.containsMatchIn(text) || inventoryAssertion.containsMatchIn(text)
  }

  private fun nextTurnId(legacy: JSONObject, state: GameState): String {
    val number = legacy.optInt("turn", state.turn.currentTurnId.substringAfterLast('_').toIntOrNull() ?: 1)
    return "TURN_${number.coerceAtLeast(1)}"
  }

  private fun timeAdvanceCommand(turnId: String, action: String): TimeAdvanceCommand = TimeAdvanceCommand(
    commandId = "$turnId:SYSTEM:TIME",
    turnId = turnId,
    actorId = CAO_MINH_ID,
    source = CommandSource.SYSTEM,
    minutes = TimeCostPolicy.estimateMinutes(action),
    reason = "player_action"
  )

  private fun stableItemId(name: String): String = name.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').ifBlank { "item-${name.hashCode().toUInt()}" }

  private fun jsonObjectStrings(json: JSONObject?): Map<String, String> {
    if (json == null) return emptyMap()
    val result = mutableMapOf<String, String>()
    json.keys().forEach { key -> result[key] = json.optString(key) }
    return result
  }

  private fun legacyEquipment(state: GameState): JSONObject = JSONObject().apply {
    val slots = state.equipment[CAO_MINH_ID]?.slots.orEmpty()
    listOf("weapon", "armor").forEach { slot ->
      val id = slots[slot] ?: return@forEach
      put(slot, JSONObject().put("id", id).put("name", CaoMinhStartingEquipment.displayName(id) ?: id).put("permanent", false).put("scalingMode", "BASE"))
    }
    slots["ring"]?.let { id -> put("ring", JSONObject().put("id", id).put("name", CaoMinhStartingEquipment.displayName(id) ?: id).put("permanent", false).put("scalingMode", "BASE")) }
  }

  private fun syncLegacy(legacy: JSONObject, state: GameState, incrementTurn: Boolean): JSONObject {
    val output = JSONObject(legacy.toString())
    if (incrementTurn) output.put("turn", output.optInt("turn", 1) + 1)
    output.put("saveVersion", CURRENT_SAVE_VERSION)
    output.put("gameTime", JSONObject().apply {
      put("elapsedSubjectiveMinutes", state.time.elapsedSubjectiveMinutes)
      put("lastAdvanceMinutes", state.time.lastAdvanceMinutes)
      state.time.lastAdvanceReason?.let { put("lastAdvanceReason", it) }
    })
    output.put("partyDetails", CharacterDetailJson.encodeParty(CharacterDetailProjector.projectParty(state)))
    output.put("coreSystem", PokerDiceCore.statsJson(state, CAO_MINH_ID))
    // The browser must receive combat/dice from Core, never from forged client JSON.
    val combat = CombatRuntime.toJson(state)
    if (combat != null) {
      PokerDiceCore.diceJson(state)?.let { combat.put("diceState", it) }
      output.put("combat", combat)
    } else {
      output.remove("combat")
      output.remove("combatFeedback")
    }
    output.put("equipment",legacyEquipment(state))
    val caoMinhInventory = state.inventories[CAO_MINH_ID]?.items?.values.orEmpty()
    output.put("inventory", JSONArray().apply { caoMinhInventory.forEach { stack -> put(JSONObject().apply {
      put("id", stack.itemId); put("name", stack.name); put("quantity", stack.quantity)
      stack.condition?.let { put("state", it) }; put("metadata", JSONObject(stack.metadata))
    }) } })
    output.put("party", JSONArray().apply { state.party.memberIds.filter { it != CAO_MINH_ID }.forEach { id ->
      state.characters[id]?.let { character -> put(JSONObject().apply {
        put("id", character.id); put("name", character.name); character.avatarRef?.let { put("avatar", it) }
        put("presence", character.presence.name)
      }) }
    } })
    state.world["location"]?.let { output.put("location", it) }
    state.world["title"]?.let { output.put("title", it) }
    state.world["levelJson"]?.let { output.put("level", JSONObject(it)) }
    state.world["flagsJson"]?.let { output.put("flags", JSONObject(it)) }
    if (LevelTravelEngine.KEY in state.world) {
      val projected = LevelTravelEngine.project(state, output)
      projected.keys().forEach { output.put(it, projected.get(it)) }
      output.put("coreSnapshot", JSONObject(GameStateCodec.encode(state)))
    }
    state.metadata["legacyPlayerJson"]?.let { output.put("player", JSONObject(it)) }
    return output
  }

  private fun appendLog(state: JSONObject, action: String, reply: String) {
    val log = state.optJSONArray("log") ?: JSONArray().also { state.put("log", it) }
    log.put(JSONObject().put("role", "player").put("text", action))
    log.put(JSONObject().put("role", "gm").put("text", reply))
  }

  private fun response(handled: Boolean, state: JSONObject, error: String?, reason: String, reply: String? = null): String = JSONObject().apply {
    put("handled", handled); put("state", state); put("reason", reason)
    if (error != null) put("error", error); if (reply != null) put("reply", reply)
  }.toString()

  private fun eventReply(events: List<String>): String = when (events.lastOrNull { it != "time_advanced" }) {
    "inventory_pickup" -> "Inventory đã được cập nhật bởi một sự kiện vật phẩm hợp lệ."
    "inventory_remove" -> "Vật phẩm đã được loại khỏi Inventory theo hành động của Cao Minh."
    "inventory_transfer" -> "Vật phẩm đã được chuyển giao."
    "item_equipped" -> "Vật phẩm đã được trang bị."
    "item_unequipped" -> "Vật phẩm đã được tháo khỏi trang bị."
    "vanKhoGioiChi_stored" -> "Vật phẩm đã được cất vào Vạn Khố Giới Chỉ."
    "vanKhoGioiChi_withdrawn" -> "Vật phẩm đã được lấy ra khỏi Vạn Khố Giới Chỉ."
    "vanKhoGioiChi_scanned" -> "Vạn Khố Giới Chỉ đã ghi mẫu vào scan slot và đánh dấu bản gốc."
    "vanKhoGioiChi_copied" -> "Vạn Khố Giới Chỉ đã tạo bản sao từ mẫu còn hiệu lực."
    else -> "Hành động đã được Game State Core xác nhận."
  }

  private fun validationReply(reason: String): String {
    val message = when (reason) {
      "player_pickup_unavailable", "restore_narrative_only", "precise_content_amount_forbidden", "item_content_empty" -> "This action is not available."
      "special_cheat_equipment_restricted" -> "Trang bị đặc biệt này chỉ có thể trang bị cho Cao Minh."
      "scan_source_missing", "scan_template_missing" -> "There is no object available for scanning or multiplying."
      "insufficient_item_quantity", "item_not_owned" -> "This action is not available."
      "party_full" -> "Party đã đủ tối đa bốn thành viên."
      "join_not_confirmed" -> "Yêu cầu gia nhập chưa đủ điều kiện hoặc chưa được NPC xác nhận."
      "living_target_forbidden" -> "Vạn Khố Giới Chỉ không thể tác động lên sinh vật sống."
      else -> "This action is not available."
    }
    return "[Warning] $message"
  }

  companion object {
    @JvmStatic fun create(context: Context, debugLogging: Boolean = false): GameCoreFacade = GameCoreFacade(
      SharedPreferencesSaveRepository(context.applicationContext), AndroidGamePipelineLogger(debugLogging),
      LevelTravelEngine(LevelTravelPolicy(JSONObject(context.assets.open("knowledge/level_travel_policy.json").bufferedReader().use { it.readText() })))
    )
  }
}
