package com.rabpit.backroom.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class GameCoreFacade private constructor(
  private val repository: SaveRepository,
  private val logger: GamePipelineLogger,
  private val localModel: LiteRTIntentInterpreter,
  private val levelRegistry: LevelRegistry,
  private val backroomsDirector: BackroomsDirector
) : AutoCloseable {
  private val rules = RuleIntentInterpreter()
  private val resolver = CommandResolver()

  /** Fast deterministic pass. Gemini is never called from this method. */
  fun processRule(legacyStateJson: String, action: String): String {
    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
    if (AnNhienCanon.matchesPartyCheatCode(action)) return applyAnNhienPartyCheat(legacy, state)
    SpecialFollowersCanon.matchesPartyCheatCode(action)?.let { targetId ->
      return applySpecialFollowerPartyCheat(legacy, state, targetId)
    }
    val turnId = nextTurnId(legacy, state)
    logger.log(PipelineLogEvent("INPUT", turnId = turnId, details = mapOf("length" to action.length.toString())))
    val pending = TurnCoordinator.createPending(state, turnId, action)
    if (pending.error != null) return response(false, legacy, pending.error, "pending_rejected")
    val context = contextFor(pending.state)
    val ruleResult = rules.interpretSync(action, context)
    val candidates = ruleResult.candidates.map { candidate ->
      if (candidate.confidence == IntentConfidence.HIGH || candidate.intent == GameIntent.NO_ACTION) candidate
      else localModel.interpretSync(candidate.clause, context).candidates.singleOrNull() ?: candidate
    }
    val interpreted = IntentResult(candidates, candidates.any { it.confidence != IntentConfidence.HIGH && it.intent != GameIntent.NO_ACTION })
    interpreted.candidates.forEach { logger.log(PipelineLogEvent("INTENT", turnId = turnId, source = it.source, intent = it.intent, confidence = it.score)) }

    // Player text never has authority to manufacture an acquisition event. Reject immediately,
    // do not call Gemini, do not advance the turn, and do not mutate Inventory.
    if (isDirectPlayerPickupAction(action)) {
      val worldPickup = WorldItemLedger.consume(
        pending.state.world["flagsJson"],
        pending.state.world["location"] ?: legacy.optString("location"),
        action,
        recentWorldItemNarratives(legacy)
      )
      if (worldPickup != null) {
        val commands = mutableListOf<GameCommand>()
        worldPickup.items.forEachIndexed { index, item ->
          commands += ItemCommand(
            commandId = "$turnId:SYSTEM:WORLD_PICKUP:$index",
            turnId = turnId,
            actorId = KAI_ID,
            source = CommandSource.SYSTEM,
            operation = ItemCommand.Operation.PICKUP,
            itemId = item.itemId,
            itemName = item.itemName,
            quantity = item.quantity,
            metadata = item.metadata
          )
        }
        commands += ValidatedLegacyStateCommand(
          commandId = "$turnId:SYSTEM:WORLD_PICKUP_FLAGS",
          turnId = turnId,
          source = CommandSource.SYSTEM,
          flagsJson = worldPickup.flagsJson,
          validatedByGameEngine = true
        )
        val committed = commitActionRuntime(pending.state, commands, action, turnId)
        if (committed.error != null) {
          val result = syncLegacy(legacy, state, incrementTurn = false)
          val reply = validationReply(committed.error)
          appendLog(result, action, reply)
          return response(true, result, committed.error, "validation_rejected", reply)
        }
        repository.save(committed.state)
        val result = syncLegacy(legacy, committed.state, incrementTurn = true)
        val names = worldPickup.items.joinToString(", ") { it.itemName }
        val reply = if (worldPickup.items.size == 1) "Đã nhặt $names và thêm vào Inventory."
          else "Đã nhặt các vật phẩm: $names và thêm vào Inventory."
        appendLog(result, action, reply)
        logger.log(PipelineLogEvent("COMMIT", turnId = turnId, details = mapOf(
          "worldPickup" to worldPickup.items.joinToString(",") { it.itemId }
        )))
        return response(true, result, null, "world_pickup_committed", reply)
      }
      val result = syncLegacy(legacy, state, incrementTurn = false)
      val reply = validationReply("player_pickup_unavailable")
      appendLog(result, action, reply)
      logger.log(PipelineLogEvent("REJECT", turnId = turnId, details = mapOf("reason" to "player_pickup_unavailable")))
      return response(true, result, "player_pickup_unavailable", "validation_rejected", reply)
    }

    // Restore is lore/narrative-only. Route prose to the GM, but authoritative state mutation is
    // explicitly suppressed again in processValidatedCandidate().
    if (interpreted.candidates.any { it.intent == GameIntent.OMNIVAULT_RESTORE }) {
      return response(false, legacy, null, "fallback_required")
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

  fun beginAction(legacyStateJson: String, kindRaw: String, action: String): String {
    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
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

  fun processRegisteredLevelAction(legacyStateJson: String, kindRaw: String, action: String): String {
    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
    val kind = enumValues<ActionKind>().firstOrNull { it.name == kindRaw.trim().uppercase() }
      ?: return response(false, legacy, "action_kind_invalid", "registered_level_not_handled")
    val exploration = legacy.optJSONObject("flags")?.optJSONObject("exploration")
    val legacyAreaId = exploration?.optString("areaId")?.takeIf(String::isNotBlank)
    val levelId = legacyAreaId
      ?: state.world["levelId"]?.takeIf(String::isNotBlank)
      ?: state.levelInstance?.levelId
    if (levelId.isNullOrBlank() || !levelRegistry.contains(levelId)) {
      return response(false, legacy, null, "registered_level_not_handled")
    }

    val runSeed = state.levelInstance?.takeIf { it.levelId == levelId }?.runSeed
      ?: state.metadata["runSeed"]
      ?: "run-${System.currentTimeMillis()}"
    val seeded = if (state.metadata["runSeed"].isNullOrBlank()) {
      state.copy(metadata = state.metadata + ("runSeed" to runSeed))
    } else state

    val result = RegisteredLevelActionCoordinator.applyStarted(
      seeded, levelRegistry, kind, action, levelId, runSeed, backroomsDirector
    )
    if (!result.handled) return response(false, legacy, result.error, "registered_level_not_handled")

    if (result.error != null) {
      var failed = result.state
      ActionRuntime.activeSession(failed)?.let { active ->
        val interrupted = ActionRuntime.interrupt(failed, active.sessionId, result.error)
        if (interrupted.applied) failed = interrupted.state
      }
      repository.save(failed)
      val output = syncLegacy(legacy, failed, incrementTurn = false)
      val reply = "[Warning] Hành động Level không thể commit: ${result.error}."
      appendLog(output, action, reply)
      return response(true, output, result.error, "registered_level_rejected", reply)
    }

    repository.save(result.state)
    val output = syncLegacy(legacy, result.state, incrementTurn = true)
    val reply = result.reply ?: if (result.progressed) "Môi trường đã thay đổi." else "Không có tiến triển mới."
    appendLog(output, action, reply)
    logger.log(PipelineLogEvent(
      "REGISTERED_LEVEL_COMMIT",
      turnId = result.state.metadata["lastAction.turnId"],
      details = mapOf(
        "levelId" to levelId,
        "kind" to kind.name,
        "progressed" to result.progressed.toString(),
        "escaped" to result.escaped.toString()
      )
    ))
    return response(true, output, null, "registered_level_committed", reply)
  }

  fun restoreCoreState(raw: String): Boolean {
    if (raw.isBlank()) return false
    return try {
      val restored = GameStateCodec.decode(raw)
      if (restored.saveVersion != CURRENT_SAVE_VERSION) return false
      val level = restored.levelInstance
      if (level != null) {
        if (!levelRegistry.contains(level.levelId)) return false
        val definition = levelRegistry.require(level.levelId)
        if (!BlueprintValidator.validate(level, definition).valid) return false
      }
      repository.save(restored)
      true
    } catch (_: Exception) {
      false
    }
  }

  fun prepareLevelGeneration(legacyStateJson: String): String {
    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
    val legacyAreaId = legacy.optJSONObject("flags")?.optJSONObject("exploration")
      ?.optString("areaId")?.takeIf(String::isNotBlank)
    val levelId = legacyAreaId
      ?: state.world["levelId"]?.takeIf(String::isNotBlank)
      ?: state.levelInstance?.levelId
      ?: return JSONObject().put("required", false).put("reason", "level_unknown").toString()
    if (!levelRegistry.contains(levelId)) {
      return JSONObject().put("required", false).put("reason", "level_not_registered").put("levelId", levelId).toString()
    }
    if (state.levelInstance?.levelId == levelId) {
      return JSONObject().put("required", false).put("reason", "level_instance_exists").put("levelId", levelId).toString()
    }

    val runSeed = state.metadata["runSeed"]?.takeIf(String::isNotBlank)
      ?: "run-${System.currentTimeMillis()}"
    if (state.metadata["runSeed"].isNullOrBlank()) {
      repository.save(state.copy(metadata = state.metadata + ("runSeed" to runSeed)))
    }
    val definition = levelRegistry.require(levelId)
    return JSONObject().apply {
      put("required", true)
      put("levelId", levelId)
      put("runSeed", runSeed)
      put("request", LevelGenerationRequestFactory.build(definition, runSeed))
    }.toString()
  }

  fun commitGeneratedLevelCandidate(levelId: String, runSeed: String, candidateJson: String, generatorVersion: String): String {
    val definition = levelRegistry.get(levelId)
      ?: return JSONObject().put("accepted", false).put("error", "level_not_registered").toString()
    val current = repository.load()
    val existing = current.levelInstance
    if (existing?.levelId == levelId && existing.runSeed == runSeed) {
      return JSONObject().put("accepted", true).put("reason", "already_committed")
        .put("generationId", existing.generationId)
        .put("fingerprint", existing.generationFingerprint ?: JSONObject.NULL).toString()
    }

    return try {
      val candidate = LevelGenerationCandidateJson.decode(candidateJson)
      val instance = LevelInstanceGenerator.commitCandidate(definition, runSeed, candidate, generatorVersion)
      val zoneName = instance.zones[instance.currentZoneId]?.name ?: instance.currentZoneId
      val installed = current.copy(
        levelInstance = instance,
        metadata = current.metadata + ("runSeed" to runSeed),
        world = current.world + mapOf(
          "levelId" to definition.id,
          "location" to "Level ${definition.id} / $zoneName",
          "worldRevision" to "${definition.id}:${instance.revision}"
        )
      )
      repository.save(installed)
      JSONObject().put("accepted", true).put("reason", "candidate_committed")
        .put("generationId", instance.generationId)
        .put("fingerprint", instance.generationFingerprint ?: JSONObject.NULL).toString()
    } catch (error: Exception) {
      val message = (error.message ?: error::class.java.simpleName).take(1800)
      JSONObject().put("accepted", false).put("error", message).toString()
    }
  }

  fun installDefinitionLevelFallback(levelId: String, runSeed: String): String {
    if (!levelRegistry.contains(levelId)) {
      return JSONObject().put("accepted", false).put("error", "level_not_registered").toString()
    }
    return try {
      val current = repository.load()
      val installed = GenericLevelRuntime.install(
        current.copy(metadata = current.metadata + ("runSeed" to runSeed)),
        levelRegistry,
        levelId,
        runSeed
      )
      repository.save(installed)
      JSONObject().put("accepted", true).put("reason", "definition_fallback")
        .put("generationId", installed.levelInstance?.generationId ?: JSONObject.NULL).toString()
    } catch (error: Exception) {
      JSONObject().put("accepted", false).put("error", (error.message ?: "fallback_failed").take(1200)).toString()
    }
  }

  fun currentCoreState(): String = GameStateCodec.encode(repository.load())
  fun registeredLevelIds(): String = JSONArray(levelRegistry.ids()).toString()
  fun hasRegisteredLevel(levelId: String): Boolean = levelRegistry.contains(levelId)

  fun installRegisteredLevel(levelId: String, runSeed: String): String {
    val installed = GenericLevelRuntime.install(repository.load(), levelRegistry, levelId, runSeed)
    repository.save(installed)
    return GameStateCodec.encode(installed)
  }

  fun clear() = repository.clear()
  override fun close() {
    localModel.close()
    backroomsDirector.close()
  }

  /**
   * Commits only the gameplay delta already accepted by the legacy canon/dice validator.
   * Candidate prose/JSON never becomes storage directly: inventory and party are rebuilt
   * from commands and then projected back onto the UI state.
   */
  fun processValidatedCandidate(beforeJson: String, candidateJson: String, action: String): String {
    val before = JSONObject(beforeJson)
    val candidate = JSONObject(candidateJson)
    val core = loadOrMigrate(before)
    val turnId = nextTurnId(before, core)
    val pending = TurnCoordinator.createPending(core, turnId, action)
    if (pending.error != null) return response(false, before, pending.error, "pending_rejected")
    val commands = mutableListOf<GameCommand>()
    val current = pending.state.inventories[KAI_ID]?.items.orEmpty()
    val actionIntents = rules.interpretSync(action, contextFor(pending.state)).candidates.map { it.intent }.toSet()
    val inventoryLocked = true // INVENTORY_AUTHORITY: candidate snapshots are read-only
    val gmItemGains = GmItemGainPolicy.positiveDeltas(current, candidate.optJSONArray("inventory"))
    gmItemGains.forEachIndexed { index, gain ->
      commands += ItemCommand(
        commandId = "$turnId:GEMINI:GM_GAIN:$index",
        turnId = turnId,
        actorId = KAI_ID,
        source = CommandSource.GEMINI,
        operation = ItemCommand.Operation.PICKUP,
        itemId = gain.itemId,
        itemName = gain.itemName,
        quantity = gain.quantity,
        metadata = gain.metadata
      )
    }

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
        "$turnId:GEMINI:INV:$index", turnId, KAI_ID, source = CommandSource.GEMINI,
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
    repository.save(committed.state)
    val synchronized = syncLegacy(candidate, committed.state, incrementTurn = false)
    logger.log(PipelineLogEvent("GEMINI_COMMIT", turnId = turnId, source = CommandSource.GEMINI, details = mapOf("commands" to commands.size.toString(), "inventoryLocked" to inventoryLocked.toString())))
    val payload = JSONObject(response(true, synchronized, null, "gemini_delta_committed"))
    if (gmItemGains.isNotEmpty()) {
      payload.put("gainNotifications", JSONArray().apply {
        gmItemGains.forEach { gain -> put(JSONObject().put("name", gain.itemName).put("quantity", gain.quantity)) }
      })
    }
    return payload.toString()
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
    if (CombatRuntime.active(current) == null) return response(false, legacy, null, "combat_inactive")

    val resolvedEntityKey = CombatRuntime.active(current)?.entityKey.orEmpty()
    var resolution = CombatRuntime.resolve(current, actionKind, action)
    if (!resolution.handled) return response(false, legacy, null, "combat_inactive")
    var next = resolution.state
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

    val output = syncLegacy(legacy, next, incrementTurn = false)
    if (resolution.entityDestroyed || resolution.escaped) {
      val flags = output.optJSONObject("flags") ?: JSONObject().also { output.put("flags", it) }
      flags.put("entityEncounterKey", "")
    }
    appendLog(output, action, resolution.reply)
    return response(true, output, null, if (resolution.entityDestroyed) "combat_entity_destroyed" else if (resolution.escaped) "combat_escaped" else "combat_resolved", resolution.reply)
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
    val loaded = if (existed) repository.load() else CharacterEquipmentSystem.normalize(GameState.initial())
    val normalized = normalizeVisualPresence(loaded)
    if (!existed || normalized != loaded) repository.save(normalized)
    return normalized
  }

  private fun contextFor(state: GameState): GameContext {
    val actors = state.characters.values.associate { it.name.lowercase() to it.id } + mapOf("kai" to KAI_ID, "iris" to "iris", "syvial" to "syvial", "an nhiên" to AN_NHIEN_ID, "an nhien" to AN_NHIEN_ID)
    val items = (state.inventories.values.flatMap { it.items.values } + state.omnivault.storedItems.values).associate { it.name.lowercase() to it.itemId }
    return GameContext(state, actors, items)
  }

  private fun recentWorldItemNarratives(legacy: JSONObject): List<String> {
    val log = legacy.optJSONArray("log") ?: return emptyList()
    val result = mutableListOf<String>()
    val first = maxOf(0, log.length() - 8)
    for (index in log.length() - 1 downTo first) {
      val entry = log.optJSONObject(index) ?: continue
      val role = entry.optString("role", "").lowercase()
      if (role == "player" || role == "gain") continue
      val text = entry.optString("text", "").trim()
      if (text.isBlank() || text.startsWith("[Warning]")) continue
      result += text
    }
    return result
  }

  private fun isAuthoritativeItemIntent(intent: GameIntent): Boolean = intent in setOf(
    GameIntent.PICKUP_ITEM,
    GameIntent.DROP_ITEM,
    GameIntent.USE_ITEM,
    GameIntent.TRANSFER_ITEM,
    GameIntent.EQUIP_ITEM,
    GameIntent.UNEQUIP_ITEM,
    GameIntent.OMNIVAULT_STORE,
    GameIntent.OMNIVAULT_WITHDRAW,
    GameIntent.OMNIVAULT_SCAN,
    GameIntent.OMNIVAULT_COPY,
    GameIntent.OMNIVAULT_RESTORE
  )

  private fun isDirectPlayerPickupAction(action: String): Boolean {
    val text = action.trim()
    val omnivaultWithdrawal = Regex("(?:lấy|rút|triệu hồi).*(?:ra khỏi|khỏi|từ).*(?:omnivault|nhẫn|kho)", RegexOption.IGNORE_CASE).containsMatchIn(text)
    if (omnivaultWithdrawal) return false
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
    if (incrementTurn) output.put("turn", output.optInt("turn", 1) + 1)
    output.put("saveVersion", CURRENT_SAVE_VERSION)
    output.put("gameTime", JSONObject().apply {
      put("elapsedSubjectiveMinutes", state.time.elapsedSubjectiveMinutes)
      put("lastAdvanceMinutes", state.time.lastAdvanceMinutes)
      state.time.lastAdvanceReason?.let { put("lastAdvanceReason", it) }
    })
    output.put("partyDetails", CharacterDetailJson.encodeParty(CharacterDetailProjector.projectParty(state)))
    val kaiInventory = state.inventories[KAI_ID]?.items?.values.orEmpty()
    output.put("inventory", JSONArray().apply { kaiInventory.forEach { stack -> put(JSONObject().apply {
      put("id", stack.itemId); put("name", stack.name); put("quantity", stack.quantity)
      stack.condition?.let { put("state", it) }; put("metadata", JSONObject(stack.metadata))
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
    CombatRuntime.toJson(state)?.let { output.put("combat", it) } ?: output.remove("combat")
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
    "inventory_remove" -> "Vật phẩm đã được loại khỏi Inventory theo hành động của Kai."
    "inventory_transfer" -> "Vật phẩm đã được chuyển giao."
    "item_equipped" -> "Vật phẩm đã được trang bị."
    "item_unequipped" -> "Vật phẩm đã được tháo khỏi trang bị."
    "omnivault_stored" -> "Vật phẩm đã được cất vào Omnivault."
    "omnivault_withdrawn" -> "Vật phẩm đã được lấy ra khỏi Omnivault."
    "omnivault_scanned" -> "Omnivault đã ghi mẫu vào scan slot và đánh dấu bản gốc."
    "omnivault_copied" -> "Omnivault đã tạo bản sao từ mẫu còn hiệu lực."
    else -> "Hành động đã được Game State Core xác nhận."
  }

  private fun validationReply(reason: String): String {
    val message = when (reason) {
      "player_pickup_unavailable", "restore_narrative_only", "precise_content_amount_forbidden", "item_content_empty" -> "Vật phẩm này hiện không có nội dung khả dụng."
      "scan_source_missing", "scan_template_missing" -> "There is no object available for scanning or multiplying."
      "insufficient_item_quantity", "item_not_owned" -> "Kai không có đủ vật phẩm cần thiết cho hành động này."
      "player_pickup_unavailable" -> "Không thể tự thêm vật phẩm vào Inventory; hãy tìm kiếm hoặc tương tác với môi trường để game xác định kết quả."
      "item_action_resolution_required" -> "Không thể xác thực hành động vật phẩm này từ state hiện tại; Inventory không thay đổi."
      "party_full" -> "Party đã đủ tối đa bốn thành viên."
      "join_not_confirmed" -> "Yêu cầu gia nhập chưa đủ điều kiện hoặc chưa được NPC xác nhận."
      "living_target_forbidden" -> "Omnivault không thể tác động lên sinh vật sống."
      else -> "Hành động này không khả dụng trong trạng thái hiện tại."
    }
    return "[Warning] $message"
  }

  companion object {
    @JvmStatic fun create(context: Context, debugLogging: Boolean = false): GameCoreFacade {
      val appContext = context.applicationContext
      return GameCoreFacade(
        SharedPreferencesSaveRepository(appContext),
        AndroidGamePipelineLogger(debugLogging),
        LiteRTIntentInterpreter(appContext),
        AndroidLevelRegistry.load(appContext),
        BackroomsDirector.liteRT(appContext)
      )
    }
  }
}
