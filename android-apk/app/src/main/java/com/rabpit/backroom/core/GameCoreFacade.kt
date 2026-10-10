package com.rabpit.backroom.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class GameCoreFacade private constructor(
  private val repository: SaveRepository,
  private val logger: GamePipelineLogger
) : AutoCloseable {
  private val rules = RuleIntentInterpreter()
  private val resolver = CommandResolver()

  /** Fast deterministic pass. Gemini is never called from this method. */
  fun processRule(legacyStateJson: String, action: String): String {
    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
    if (blocksTextItemAction(action)) return response(true, syncLegacy(legacy, state, false), "item_ui_required", "item_ui_required", "Hãy nhấn vào item và chọn Dùng, Chuyển hoặc Bỏ.")
    if (MadGodCanon.cheat(action)) return applyMadGodCheat(legacy,state)
    if (AnNhienCanon.matchesPartyCheatCode(action)) return applyAnNhienPartyCheat(legacy, state)
    SpecialFollowersCanon.matchesPartyCheatCode(action)?.let { targetId ->
      return applySpecialFollowerPartyCheat(legacy, state, targetId)
    }
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

    if (interpreted.candidates.any { isAuthoritativeItemIntent(it.intent) && it.confidence != IntentConfidence.HIGH }) {
      val result = syncLegacy(legacy, state, incrementTurn = false)
      val reply = validationReply("item_action_resolution_required")
      appendLog(result, action, reply)
      logger.log(PipelineLogEvent("REJECT", turnId = turnId, details = mapOf("reason" to "item_action_resolution_required")))
      return response(true, result, "item_action_resolution_required", "validation_rejected", reply)
    }
    if (interpreted.candidates.any { it.intent == GameIntent.NO_ACTION || it.confidence != IntentConfidence.HIGH }) {
      return response(false, legacy, null, "fallback_required")
    }
    val resolvedCommands = resolver.resolveSequence(interpreted.candidates, turnId, context).filterNotNull()
    if (resolvedCommands.size != interpreted.candidates.size || resolvedCommands.isEmpty()) {
      if (interpreted.candidates.any { isAuthoritativeItemIntent(it.intent) }) {
        val result = syncLegacy(legacy, state, incrementTurn = false)
        val reply = validationReply("item_action_resolution_required")
        appendLog(result, action, reply)
        return response(true, result, "item_action_resolution_required", "validation_rejected", reply)
      }
      return response(false, legacy, null, "resolution_incomplete")
    }
    val commands = resolvedCommands.toMutableList()
    commands.forEach { logger.log(PipelineLogEvent("COMMAND", turnId, it.commandId, it.source)) }
    val committed = commitActionRuntime(pending.state, commands, action, turnId)
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

  private fun applyAnNhienPartyCheat(legacy: JSONObject, state: GameState): String {
    val alreadyFollowing = AnNhienCanon.isFollowing(state)
    val (updated, error) = AnNhienCanon.forceIntoParty(state)
    val result = syncLegacy(legacy, updated, incrementTurn = false)
    val reply = when {
      error == "party_full" -> "Party đã đủ tối đa bốn thành viên; không thể thêm An Nhiên nếu chưa có chỗ trống."
      alreadyFollowing -> "An Nhiên đã ở trong Party."
      else -> "An Nhiên đã được thêm vào Party."
    }

    if (error == null) repository.save(updated)
    val log = result.optJSONArray("log") ?: JSONArray().also { result.put("log", it) }
    log.put(JSONObject().put("role", "gm").put("text", reply))
    logger.log(PipelineLogEvent(
      if (error == null) "CHEAT_COMMIT" else "CHEAT_REJECT",
      details = mapOf("command" to "an_nhien_party", "reason" to (error ?: "committed"))
    ))
    return response(
      handled = true,
      state = result,
      error = error,
      reason = if (error == null) "cheat_committed" else "cheat_rejected",
      reply = reply
    )
  }

  private fun applySpecialFollowerPartyCheat(legacy: JSONObject, state: GameState, targetId: String): String {
    val ensured = SpecialFollowersCanon.ensure(state)
    val displayName = ensured.characters[targetId]?.name ?: targetId
    val alreadyFollowing = targetId in ensured.party.memberIds
    val (updated, error) = SpecialFollowersCanon.forceIntoParty(ensured, targetId)
    val result = syncLegacy(legacy, updated, incrementTurn = false)
    val reply = when {
      error == "party_full" -> "Party đã đủ tối đa bốn thành viên; không thể thêm $displayName nếu chưa có chỗ trống."
      alreadyFollowing -> "$displayName đã ở trong Party."
      else -> "$displayName đã được thêm vào Party."
    }

    if (error == null) repository.save(updated)
    val log = result.optJSONArray("log") ?: JSONArray().also { result.put("log", it) }
    log.put(JSONObject().put("role", "gm").put("text", reply))
    logger.log(PipelineLogEvent(
      if (error == null) "CHEAT_COMMIT" else "CHEAT_REJECT",
      details = mapOf(
        "command" to if (targetId == IRIS_ID) "iris_party" else "syvial_party",
        "reason" to (error ?: "committed")
      )
    ))
    return response(
      handled = true,
      state = result,
      error = error,
      reason = if (error == null) "cheat_committed" else "cheat_rejected",
      reply = reply
    )
  }

  private fun applyMadGodCheat(legacy:JSONObject,state:GameState):String {
    val x=MadGodCanon.spawn(state); repository.save(x.state); val out=syncLegacy(legacy,x.state,incrementTurn=false);
    val flags=out.optJSONObject("flags")?:JSONObject().also{out.put("flags",it)}; flags.put("madGod",JSONObject().put("spawned",true).put("spawnSource","cheat").put("scalingMode",MadGodCanon.SCALING_MODE));
    val msg=if(x.added) "MadGod Set đã xuất hiện trong Inventory. Đây là một set duy nhất: trang bị một lần sẽ kích hoạt vũ khí; phần giáp đã chuyển thành passive Ma Tôn Vạn Giới, rồi khóa vĩnh viễn." else "MadGod Set đã tồn tại; /madgod không tạo bản sao thứ hai."; appendLog(out,MadGodCanon.CHEAT_CODE,msg); return response(true,out,null,"cheat_committed",msg)
  }

  fun beginAction(legacyStateJson: String, kindRaw: String, action: String): String {
    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
    if (MadGodCanon.cheat(action)) return actionStartResponse(true,null,null)
    val kind = enumValues<ActionKind>().firstOrNull { it.name == kindRaw.trim().uppercase() }
      ?: return actionStartResponse(false, null, "action_kind_invalid")
    val existing = ActionRuntime.activeSession(state)
    if (existing != null) {
      return if (existing.kind == kind && existing.input == action) actionStartResponse(true, existing, null)
      else actionStartResponse(false, existing, "action_session_already_active")
    }
    val turnId = nextTurnId(legacy, state)
    val sessionId = "$turnId:${kind.name}:${action.hashCode().toUInt()}"
    val started = ActionRuntime.start(
      state = state,
      sessionId = sessionId,
      turnId = turnId,
      actorId = KAI_ID,
      kind = kind,
      input = action,
      locationKey = state.world["location"] ?: legacy.optString("location").takeIf(String::isNotBlank),
      plannedMinutes = TimeCostPolicy.estimateMinutes(action),
      searchDepth = if (kind == ActionKind.SEARCH) SearchDepth.NORMAL else null
    )
    if (!started.applied) return actionStartResponse(false, started.session, started.error ?: "action_start_failed")
    repository.save(started.state)
    return actionStartResponse(true, started.session, null)
  }

  fun currentActionContext(): String {
    val state = repository.load()
    val active = ActionRuntime.activeSession(state)
    return JSONObject().apply {
      put("active", active != null)
      if (active != null) {
        put("sessionId", active.sessionId)
        put("turnId", active.turnId)
        put("kind", active.kind.name)
        put("phase", active.phase.name)
        put("location", active.locationKey ?: JSONObject.NULL)
        put("elapsedMinutes", active.elapsedMinutes)
        put("plannedMinutes", active.plannedMinutes ?: JSONObject.NULL)
        put("searchDepth", active.searchDepth?.name ?: JSONObject.NULL)
        if (active.kind == ActionKind.SEARCH && !active.locationKey.isNullOrBlank()) {
          put("searchCoverage", JSONArray(ActionRuntime.searchCoverage(state, active.locationKey).sorted()))
        }
      }
    }.toString()
  }

  fun abortAction(reason: String): Boolean {
    if (!repository.exists()) return false
    val state = repository.load()
    val active = ActionRuntime.activeSession(state) ?: return false
    val interrupted = ActionRuntime.interrupt(state, active.sessionId, reason.ifBlank { "pipeline_error" })
    if (!interrupted.applied) return false
    repository.save(interrupted.state)
    return true
  }

  private fun actionStartResponse(handled: Boolean, session: ActionSessionSnapshot?, error: String?): String = JSONObject().apply {
    put("handled", handled)
    if (session != null) {
      put("sessionId", session.sessionId)
      put("turnId", session.turnId)
      put("kind", session.kind.name)
    }
    if (error != null) put("error", error)
  }.toString()

  private fun commitActionRuntime(
    state: GameState,
    commands: MutableList<GameCommand>,
    action: String,
    turnId: String
  ): TurnResult {
    val active = ActionRuntime.activeSession(state)
    if (active == null) {
        return TurnCoordinator.commit(state, commands)
    }
    if (active.turnId != turnId) return TurnResult(state, error = "action_turn_mismatch")

    val minutes = active.plannedMinutes ?: TimeCostPolicy.estimateMinutes(action)
    val progressed = ActionRuntime.advance(state, active.sessionId, "resolve", minutes)
    if (!progressed.applied && !progressed.duplicate) {
      return TurnResult(state, error = progressed.error ?: "action_time_rejected")
    }
    val progressedState = if (progressed.duplicate) state else progressed.state
    val committed = TurnCoordinator.commit(progressedState, commands)
    if (committed.error != null) return committed

    var finalState = committed.state
    if (active.kind == ActionKind.SEARCH && !active.locationKey.isNullOrBlank()) {
      val depth = active.searchDepth ?: SearchDepth.NORMAL
      val coverage = ActionRuntime.markSearchCoverage(
        finalState,
        active.sessionId,
        setOf("depth:${depth.name.lowercase()}")
      )
      if (coverage.applied) finalState = coverage.state
    }

    val completed = ActionRuntime.complete(finalState, active.sessionId)
    if (!completed.applied) return TurnResult(finalState, committed.execution, completed.error ?: "action_complete_failed")
    return TurnResult(completed.state, committed.execution?.copy(state = completed.state))
  }

  fun currentPartyDetails(legacyStateJson: String? = null): String {
    val source = if (repository.exists()) {
      repository.load()
    } else if (!legacyStateJson.isNullOrBlank()) {
      runCatching { GameStateCodec.decode(legacyStateJson) }.getOrElse { GameState.initial() }
    } else {
      GameState.initial()
    }
    val state = CharacterEquipmentSystem.normalize(source)
    if (!repository.exists()) repository.save(state)
    return CharacterDetailJson.encodeParty(CharacterDetailProjector.projectParty(state)).toString()
  }

  fun resetNewGame(): String {
    repository.clear()
    val fresh = CharacterEquipmentSystem.normalize(GameState.initial())
    repository.save(fresh)
    return CharacterDetailJson.encodeParty(CharacterDetailProjector.projectParty(fresh)).toString()
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

  fun prepareCombatDice(legacyStateJson: String, action: String): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    val combat = CombatRuntime.active(current)
      ?: return response(false, syncLegacy(legacy, current, incrementTurn = false),
        "combat_inactive", "combat_dice_inactive")
    return try {
      val next = PokerDiceCore.prepare(current, action, combat.encounterId)
      repository.save(next)
      response(true, syncLegacy(legacy, next, incrementTurn = false), null, "combat_dice_prepared")
    } catch (error: Exception) {
      response(false, syncLegacy(legacy, current, incrementTurn = false),
        error.message ?: "combat_dice_prepare_failed", "combat_dice_rejected")
    }
  }

  fun setCombatDiceHold(legacyStateJson: String, dieIndex: Int, held: Boolean): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    return try {
      val next = PokerDiceCore.setHold(current, dieIndex, held)
      repository.save(next)
      response(true, syncLegacy(legacy, next, incrementTurn = false), null, "combat_dice_hold")
    } catch (error: Exception) {
      response(false, syncLegacy(legacy, current, incrementTurn = false),
        error.message ?: "combat_dice_hold_failed", "combat_dice_rejected")
    }
  }

  fun rerollCombatDice(legacyStateJson: String): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    return try {
      val next = PokerDiceCore.reroll(current)
      repository.save(next)
      response(true, syncLegacy(legacy, next, incrementTurn = false), null, "combat_dice_rolled")
    } catch (error: Exception) {
      response(false, syncLegacy(legacy, current, incrementTurn = false),
        error.message ?: "combat_dice_roll_failed", "combat_dice_rejected")
    }
  }

  fun finishCombatDice(legacyStateJson: String): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    return try {
      val next = PokerDiceCore.finish(current)
      repository.save(next)
      response(true, syncLegacy(legacy, next, incrementTurn = false), null, "combat_dice_finalized")
    } catch (error: Exception) {
      response(false, syncLegacy(legacy, current, incrementTurn = false),
        error.message ?: "combat_dice_finish_failed", "combat_dice_rejected")
    }
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
    val candidate = JSONObject(candidateJson)
    val core = loadOrMigrate(before)
    if (blocksTextItemAction(action)) return response(false, syncLegacy(before, core, false), "item_ui_required", "item_ui_required")
    val turnId = nextTurnId(before, core)
    val pending = TurnCoordinator.createPending(core, turnId, action)
    if (pending.error != null) return response(false, before, pending.error, "pending_rejected")
    val commands = mutableListOf<GameCommand>()
    val desiredParty = mutableMapOf<String, JSONObject>()
    val partyJson = candidate.optJSONArray("party") ?: JSONArray()
    for (index in 0 until partyJson.length()) {
      val member = partyJson.optJSONObject(index) ?: continue
      val id = member.optString("id").ifBlank { member.optString("name").trim().lowercase() }
      if (id.isNotBlank()) desiredParty[id] = member
    }
    val currentFollowers = pending.state.party.memberIds.filter { it != KAI_ID }.toSet()
    (currentFollowers - desiredParty.keys).sorted().forEachIndexed { index, id ->
      commands += PartyCommand("$turnId:GEMINI:PARTY_REMOVE:$index", turnId, KAI_ID, id, CommandSource.GEMINI, PartyCommand.Operation.REMOVE)
    }
    (desiredParty.keys - currentFollowers).sorted().forEachIndexed { index, id ->
      val member = desiredParty.getValue(id)
      val known = pending.state.characters[id]
      commands += PartyCommand(
        "$turnId:GEMINI:PARTY_ADD:$index", turnId, KAI_ID, id, CommandSource.GEMINI, PartyCommand.Operation.ADD,
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

    val committed = commitActionRuntime(pending.state, commands, action, turnId)
    if (committed.error != null) {
      logger.log(PipelineLogEvent("GEMINI_REJECTED", turnId = turnId, source = CommandSource.GEMINI, details = mapOf("reason" to committed.error)))
      return response(false, before, committed.error, "gemini_delta_rejected")
    }
    val protectedState = CharacterProgressionCore.protectFromCandidate(pending.state, committed.state)
    repository.save(protectedState)
    val synchronized = syncLegacy(candidate, protectedState, incrementTurn = false)
    logger.log(PipelineLogEvent("GEMINI_COMMIT", turnId = turnId, source = CommandSource.GEMINI, details = mapOf("commands" to commands.size.toString())))
    return response(true, synchronized, null, "gemini_delta_committed")
  }


  fun startCombatState(legacyStateJson: String, entityKey: String): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    val next = CombatRuntime.start(current, entityKey)
    repository.save(next)
    return syncLegacy(legacy, next, incrementTurn = false).toString()
  }

  fun processCombat(legacyStateJson: String, actionKind: String, action: String): String {
    val legacy = JSONObject(legacyStateJson)
    val current = loadOrMigrate(legacy)
    val activeCombat = CombatRuntime.active(current)
      ?: return response(false, legacy, null, "combat_inactive")
    if (!PokerDiceCore.isFinalizedFor(current, action, activeCombat.encounterId)) {
      return response(true, syncLegacy(legacy, current, incrementTurn = false),
        "combat_dice_required", "combat_dice_required",
        "Hãy hoàn tất Poker Dice trước khi giải quyết hành động combat.")
    }

    val resolvedEntityKey = CombatRuntime.active(current)?.entityKey.orEmpty()
    var resolution = CombatRuntime.resolve(current, actionKind, action)
    if (!resolution.handled) return response(false, legacy, null, "combat_inactive")
    var next = resolution.state
    val coreReward = if (resolution.entityDestroyed) PokerDiceCore.ENTITY_VICTORY_CORE else 0
    if (coreReward > 0) next = PokerDiceCore.grantCore(next, coreReward)
    next = PokerDiceCore.clearDice(next)
    val time = TimeEngine.execute(next, TimeAdvanceCommand(
      commandId = "COMBAT:${next.turn.currentTurnId}:${System.nanoTime()}",
      turnId = null,
      actorId = KAI_ID,
      source = CommandSource.SYSTEM,
      minutes = 1,
      reason = "combat_action"
    ))
    if (time.applied) next = time.state
    next = CharacterStatEngine.applyCompletedTurnRegen(next, "COMBAT_TURN_${legacy.optInt("turn", 1)}")
    if (resolution.entityDestroyed || resolution.escaped) {
      val flags = next.world["flagsJson"]?.let { JSONObject(it) }
        ?: legacy.optJSONObject("flags")?.let { JSONObject(it.toString()) }
        ?: JSONObject()
      flags.put("entityEncounterKey", "")
      when (resolvedEntityKey) {
        "jeff_the_killer" -> flags.optJSONObject("jeff")?.put("present", false)
        "jane_the_killer" -> flags.optJSONObject("jane")?.put("present", false)
      }
      next = next.copy(world = next.world + ("flagsJson" to flags.toString()))
    }
    repository.save(next)

    val output = syncLegacy(legacy, next, incrementTurn = true)
    val combatReply = if (coreReward > 0)
      resolution.reply + " +" + coreReward + " Core."
    else resolution.reply
    output.put("combatFeedback", JSONObject().apply {
      put("id", "${activeCombat.encounterId}:${activeCombat.eventCounter + 1}")
      put("encounterId", activeCombat.encounterId)
      put("summary", combatReply)
      put("events", JSONArray().apply { resolution.feedback.forEach { put(it.toJson()) } })
    })
    if (resolution.entityDestroyed || resolution.escaped) {
      val flags = output.optJSONObject("flags") ?: JSONObject().also { output.put("flags", it) }
      flags.put("entityEncounterKey", "")
    }
    if (action != PokerDiceCore.DIRECT_COMBAT_ACTION) {
      appendLog(output, action, combatReply)
    }
    return response(true, output, null, if (resolution.entityDestroyed) "combat_entity_destroyed" else if (resolution.escaped) "combat_escaped" else "combat_resolved", combatReply)
  }

  private fun normalizeVisualPresence(state: GameState): GameState {
    if (CombatRuntime.active(state) != null) return state
    val rawFlags = state.world["flagsJson"] ?: return state
    val flags = runCatching { JSONObject(rawFlags) }.getOrNull() ?: return state
    if (flags.optString("entityEncounterKey", "").isBlank()) return state
    flags.put("entityEncounterKey", "")
    return state.copy(world = state.world + ("flagsJson" to flags.toString()))
  }

  private fun loadOrMigrate(legacy: JSONObject): GameState {
    val existed = repository.exists()
    val loaded = if (existed) repository.load() else GameState.initial()
    val normalized = normalizeVisualPresence(loaded)
    if (!existed || normalized != loaded) repository.save(normalized)
    return normalized
  }

  private fun contextFor(state: GameState): GameContext {
    val actors = state.characters.values.associate { it.name.lowercase() to it.id } + mapOf("cao minh" to KAI_ID, "cao_minh" to KAI_ID, "iris" to "iris", "syvial" to "syvial", "an nhiên" to AN_NHIEN_ID, "an nhien" to AN_NHIEN_ID)
    val items = state.inventories.values.flatMap { it.items.values }.associate { it.name.lowercase() to it.itemId }
    return GameContext(state, actors, items)
  }

  private fun isAuthoritativeItemIntent(intent: GameIntent): Boolean = intent in setOf(
    GameIntent.PICKUP_ITEM,
    GameIntent.DROP_ITEM,
    GameIntent.USE_ITEM,
    GameIntent.TRANSFER_ITEM,
    GameIntent.EQUIP_ITEM,
    GameIntent.UNEQUIP_ITEM,
  )

  private fun isDirectPlayerPickupAction(action: String): Boolean {
    val text = action.trim()
    val directVerb = Regex("(?:^|\\s)(?:nhặt|lượm|cầm\\s+lên|lấy(?:\\s+lên)?|thu\\s+hồi|tịch\\s+thu|nhận(?:\\s+lấy)?|pick\\s+up|take|receive)(?:\\s|$)", RegexOption.IGNORE_CASE)
    val inventoryAssertion = Regex("(?:thêm|đưa).{0,80}(?:vào|trong)\\s+(?:inventory|kho đồ|túi đồ)", RegexOption.IGNORE_CASE)
    return directVerb.containsMatchIn(text) || inventoryAssertion.containsMatchIn(text)
  }

  private fun nextTurnId(legacy: JSONObject, state: GameState): String {
    val number = legacy.optInt("turn", state.turn.currentTurnId.substringAfterLast('_').toIntOrNull() ?: 1)
    return "TURN_${number.coerceAtLeast(1)}"
  }

  private fun timeAdvanceCommand(turnId: String, action: String): TimeAdvanceCommand = TimeAdvanceCommand(
    commandId = "$turnId:SYSTEM:TIME",
    turnId = turnId,
    actorId = KAI_ID,
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

  private fun syncLegacy(legacy: JSONObject, state: GameState, incrementTurn: Boolean): JSONObject {
    val output = JSONObject(legacy.toString())
    output.remove("combatFeedback") // Transient events must never be echoed from a legacy/client state.
    if (incrementTurn) output.put("turn", output.optInt("turn", 1) + 1)
    output.put("saveVersion", CURRENT_SAVE_VERSION)
    output.put("gameTime", JSONObject().apply {
      put("elapsedSubjectiveMinutes", state.time.elapsedSubjectiveMinutes)
      put("lastAdvanceMinutes", state.time.lastAdvanceMinutes)
      state.time.lastAdvanceReason?.let { put("lastAdvanceReason", it) }
    })
    output.put("partyDetails", CharacterDetailJson.encodeParty(CharacterDetailProjector.projectParty(state)))
    output.put("equipment",MadGodCanon.legacy(state))
    val kaiInventory = state.inventories[KAI_ID]?.items?.values.orEmpty()
    output.put("inventory", JSONArray().apply { kaiInventory.forEach { stack -> put(JSONObject().apply {
      put("id", stack.itemId); put("name", stack.name); put("quantity", stack.quantity)
      put("metadata", JSONObject(stack.metadata))
    }) } })
    output.put("party", JSONArray().apply { state.party.memberIds.filter { it != KAI_ID }.forEach { id ->
      state.characters[id]?.let { character -> put(JSONObject().apply {
        put("id", character.id); put("name", character.name); character.avatarRef?.let { put("avatar", it) }
        put("presence", character.presence.name)
      }) }
    } })
    state.world["location"]?.let { output.put("location", it) }
    state.world["title"]?.let { output.put("title", it) }
    state.world["levelJson"]?.let { output.put("level", JSONObject(it)) }
    state.world["flagsJson"]?.let { output.put("flags", JSONObject(it)) }
    state.metadata["legacyPlayerJson"]?.let { output.put("player", JSONObject(it)) }
    CombatRuntime.toJson(state)?.let { combat ->
      PokerDiceCore.diceJson(state)?.let { combat.put("diceState", it) }
      output.put("combat", combat)
    } ?: output.remove("combat")
    output.put("coreSystem", PokerDiceCore.statsJson(state, KAI_ID))
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
    else -> "Hành động đã được Game State Core xác nhận."
  }

  private fun validationReply(reason: String): String {
    val message = when (reason) {
      "player_pickup_unavailable", "precise_content_amount_forbidden", "item_content_empty" -> "Vật phẩm này hiện không có nội dung khả dụng."
      "madgod_equipment_permanent" -> "MadGod đã khóa vĩnh viễn sau khi trang bị; không thể tháo hoặc đổi."
      "madgod_equipment_slot_mismatch" -> "MadGod Set chỉ có thể trang bị cho Cao Minh; chỉ chiếm slot vũ khí; giáp MadGod đã chuyển thành passive Ma Tôn Vạn Giới."
      "insufficient_item_quantity", "item_not_owned" -> "Cao Minh không có đủ vật phẩm cần thiết cho hành động này."
      "player_pickup_unavailable" -> "Không thể tự thêm vật phẩm vào Inventory; hãy tìm kiếm hoặc tương tác với môi trường để game xác định kết quả."
      "item_action_resolution_required" -> "Không thể xác thực hành động vật phẩm này từ state hiện tại; Inventory không thay đổi."
      "party_full" -> "Party đã đủ tối đa bốn thành viên."
      "join_not_confirmed" -> "Yêu cầu gia nhập chưa đủ điều kiện hoặc chưa được NPC xác nhận."
      else -> "Hành động này không khả dụng trong trạng thái hiện tại."
    }
    return "[Warning] $message"
  }


  @Synchronized fun processItemAction(legacyStateJson: String, requestJson: String): String {
    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
    fun reject(reason: String): String = response(false, syncLegacy(legacy, state, false), reason, "item_ui_rejected", validationReply(reason))
    if (CombatRuntime.active(state) != null) return reject("combat_active")
    if (ActionRuntime.activeSession(state) != null || state.turn.pending != null) return reject("another_turn_pending")
    val request = runCatching { JSONObject(requestJson) }.getOrNull() ?: return reject("invalid_item_request")
    val operation = when (request.optString("operation")) {
      "USE" -> ItemCommand.Operation.USE
      "TRANSFER" -> ItemCommand.Operation.TRANSFER
      "DROP" -> ItemCommand.Operation.DROP
      else -> return reject("invalid_item_operation")
    }
    val actorId = request.optString("actorId")
    fun available(id: String): Boolean = id in state.party.memberIds && state.characters[id]?.presence == CharacterPresence.ACTIVE
    if (!available(actorId)) return reject("actor_unavailable")
    val itemId = request.optString("itemId")
    val item = state.inventories[actorId]?.items?.get(itemId) ?: return reject("item_not_owned")
    val rawQuantity = request.opt("quantity")
    val quantity = (rawQuantity as? Number)?.toInt() ?: return reject("quantity_must_be_positive")
    if (rawQuantity.toDouble() != quantity.toDouble() || quantity <= 0) return reject("quantity_must_be_positive")
    if (quantity > item.quantity) return reject("insufficient_item_quantity")
    val targetId = if (operation == ItemCommand.Operation.TRANSFER) request.optString("targetId").takeIf { it.isNotBlank() } else null
    if (operation == ItemCommand.Operation.TRANSFER && (targetId == null || targetId == actorId || !available(targetId))) return reject("target_unavailable")
    val verb = when (operation) { ItemCommand.Operation.USE -> "Dùng"; ItemCommand.Operation.TRANSFER -> "Chuyển"; else -> "Bỏ" }
    val action = "$verb $quantity ${item.name} (${state.characters.getValue(actorId).name})" + (targetId?.let { " cho ${state.characters.getValue(it).name}" } ?: "")
    val turnId = nextTurnId(legacy, state)
    val pending = TurnCoordinator.createPending(state, turnId, action)
    if (pending.error != null) return reject(pending.error)
    val command = ItemCommand(commandId = "$turnId:UI:ITEM", turnId = turnId, actorId = actorId,
      targetId = if (operation == ItemCommand.Operation.TRANSFER) targetId else null, source = CommandSource.UI,
      operation = operation, itemId = item.itemId, itemName = item.name, quantity = quantity)
    val committed = TurnCoordinator.commit(pending.state, listOf(command, timeAdvanceCommand(turnId, action)))
    if (committed.error != null) return reject(committed.error)
    val reconciled = OfflineEntityLoot.collectPending(committed.state)
    repository.save(reconciled)
    val result = syncLegacy(legacy, reconciled, true)
    val reply = eventReply(committed.execution?.events.orEmpty())
    appendLog(result, action, reply)
    return response(true, result, null, "item_ui_committed", reply)
  }


  fun blocksTextItemAction(action: String): Boolean = rules.interpretSync(action, GameContext(GameState.initial())).candidates.any {
    it.intent in setOf(GameIntent.USE_ITEM, GameIntent.TRANSFER_ITEM, GameIntent.DROP_ITEM)
  }

  companion object {
    @JvmStatic fun create(context: Context, debugLogging: Boolean = false): GameCoreFacade = GameCoreFacade(
      SharedPreferencesSaveRepository(context.applicationContext), AndroidGamePipelineLogger(debugLogging)
    )
  }
}
