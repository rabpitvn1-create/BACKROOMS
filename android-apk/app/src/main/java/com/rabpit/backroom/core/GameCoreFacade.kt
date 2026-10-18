package com.rabpit.backroom.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

class GameCoreFacade private constructor(
  private val repository: SaveRepository,
  private val logger: GamePipelineLogger
) : AutoCloseable {
  private val rules = RuleIntentInterpreter()
  private val resolver = CommandResolver()
  private val spawnRng = SecureRandom()
  /** One-shot exit authorization from determineExitDestination, consumed by processValidatedCandidate. */
  private var exitAuthorization: ExitAuthorization? = null

  private data class ExitAuthorization(val turn: Int, val fromId: String, val toId: String)

  companion object {
    /** Traversal intent keywords (Core-owned; mirrors client playerTraverses). */
    val TRAVERSAL_KEYWORDS = listOf(
      "đi qua", "bước qua", "chạy qua", "vượt qua", "băng qua", "chui qua", "leo qua",
      "đi vào", "bước vào", "tiến vào", "chạy vào", "nhảy vào", "chui vào",
      "đi sang", "bước sang", "tiến sang", "chuyển sang", "dịch chuyển đến",
      "no-clip", "noclip"
    )
    val NUMBERED_ID = Regex("[0-9]+(\\.[0-9]+)?")
    /**
     * P1 meta gating: Core-authoritative meta-action detection.
     * Mirrors MainActivity.isMetaAction() — meta turns (status/inventory/party checks)
     * must NEVER roll Entity/NPC, consume RNG, or create pending encounters.
     * Core determines this from action text; client classification is not trusted.
     */
    val META_KEYWORDS = listOf(
      "xem trạng thái", "trạng thái hiện tại", "xem state", "xem inventory", "xem túi",
      "kiểm tra inventory", "xem party", "xem nhân vật", "xem thuộc tính",
      "status", "show state", "show inventory", "show party"
    )
    fun isMetaAction(actionText: String): Boolean {
      val lower = actionText.lowercase()
      return META_KEYWORDS.any { kw -> lower.contains(kw.lowercase()) }
    }
    private const val SPAWN_ENCOUNTER_OWNER = "spawn.encounterOwnerSession"
    @JvmStatic fun create(context: Context, debugLogging: Boolean = false): GameCoreFacade = GameCoreFacade(
      SharedPreferencesSaveRepository(context.applicationContext), AndroidGamePipelineLogger(debugLogging)
    )
  }

  /** A hint request never starts an action, consumes RNG or modifies a save. */
  @Synchronized fun sceneHint(tier: Int): String =
    WorldGraphRuntime.hint(loadCurrentState(), tier).toString()

  @Synchronized fun sceneGenerationRequest(): String = MiniMapRuntime.generationRequest(loadCurrentState()).toString()

  @Synchronized fun acceptSceneGeneration(token: String, raw: String): String {
    val next = MiniMapRuntime.acceptGeneration(loadCurrentState(), token, raw)
    repository.save(next)
    return clientSnapshot(JSONObject(), next).toString()
  }

  /** The planner sees only the same explored map and Core-offered action labels
   * that the player can see. Never serialize private scenario rooms or SEARCH
   * interactions in rooms the player has not reached. */
  @Synchronized fun scenePlayerActionContext(): String {
    val state = loadCurrentState()
    val view = MiniMapRuntime.publicView(state)
    val map = view.optJSONObject("map") ?: error("scene_not_ready")
    val rooms = MiniMapScenario.objects(map.getJSONArray("rooms"))
    val discovered = rooms.filter { it.optBoolean("visited", false) }.map { it.getString("id") }.toSet()
    return JSONObject().put("currentRoomId", map.getString("currentRoomId"))
      .put("nodeId", view.getString("nodeId"))
      .put("miniIndex", view.getInt("miniIndex"))
      .put("rooms", map.getJSONArray("rooms"))
      .put("availableActions", JSONArray().apply {
        MiniMapScenario.objects(view.getJSONArray("availableActions")).forEach { action ->
          if (action.getString("kind") !in setOf("MOVE", "SEARCH", "INTERACT", "TALK")) return@forEach
          val targetId = action.getString("targetId")
          val label = if (action.getString("kind") == "MOVE" && targetId.startsWith("room:") &&
            targetId.removePrefix("room:") !in discovered) "Lối sang khu vực chưa khám phá"
            else action.getString("label")
          put(JSONObject().put("kind", action.getString("kind"))
            .put("targetId", targetId).put("label", label))
        }
      }).toString()
  }

  @Synchronized fun sceneNarrationContext(): String {
    val state = loadCurrentState()
    return JSONObject(state.world[MiniMapRuntime.PENDING] ?: error("scene_pending_missing")).getJSONObject("public").toString()
  }

  /** Validate -> stage -> reserve once -> persist. Gehihi failure leaves this intact. */
  /** The optional verbatim player utterance is presentation-only. It must never
   * modify the Core command, target, narration event or gameplay consequences. */
  @JvmOverloads
  @Synchronized fun beginSceneAction(raw: String, playerText: String? = null): String {
    require(raw.length <= 1200) { "scene_command_too_large" }
    require(playerText == null || (playerText.isNotBlank() && playerText.length <= 900)) {
      "scene_player_text_invalid"
    }
    val command = JSONObject(raw)
    var state = loadCurrentState()
    val actionId = command.getString("actionId")
    state.world[MiniMapRuntime.RECEIPT]?.let { receipt ->
      if (JSONObject(receipt).getString("actionId") == actionId)
        return JSONObject().put("committed", true).toString()
    }
    state.world[MiniMapRuntime.PENDING]?.let { pending ->
      val saved = JSONObject(pending)
      require(saved.getJSONObject("command").toString().let { savedCommand ->
        val c = JSONObject(savedCommand)
        c.keys().asSequence().toSet() == command.keys().asSequence().toSet() && c.keys().asSequence().all { c.get(it) == command.get(it) }
      }) { "scene_another_action_pending" }
      return JSONObject().put("committed", false).toString()
    }
    check(!MiniMapRuntime.blocked(state)) { "scene_busy" }
    val staged = MiniMapRuntime.stage(state, command) // no RNG before this point
    val deviceControl = MiniMapRuntime.isMechanismControl(state, command.getString("targetId"))
    val turnId = nextTurnId(state)
    val input = "${command.getString("kind")}:$actionId"
    val started = ActionRuntime.start(state, actionId, turnId, CAO_MINH_ID, ActionKind.EXECUTE,
      input, staged.nodeId, if (command.getString("kind") == "SEARCH" && !deviceControl) 5 else 1)
    check(started.applied) { "scene_session_failed" }
    val target = LevelRouteRegistry.node(staged.nodeId)
    val atTarget = started.state.copy(world = started.state.world + mapOf(
      "levelJson" to target.asJson().toString(), "location" to target.location(), "title" to target.title()))
    // One device sequence can require many taps; these control inputs must not
    // reroll an encounter on every switch flip. Other SEARCH/MOVE rolls stay intact.
    val worldObservation = WorldGraphRuntime.active(MiniMapRuntime.progress(state)) && command.getString("kind") == "SEARCH"
    val roll = if (deviceControl || worldObservation) SpawnResult(null, emptyList())
      else SpawnMechanism.rollSceneSpawns(atTarget, command.getString("kind")) { spawnRng.nextInt(it) }
    var reserved = SpawnMechanism.prepareSpawns(atTarget, roll)
    if (roll.entityKey != null) {
      reserved = CombatRuntime.prepareEncounter(reserved, roll.entityKey, "scene:$actionId:${roll.entityKey}")
      reserved = SpawnMechanism.discardPendingNpcEncounters(reserved)
    }
    // Pending reserves encounters, but committed world/room stay at the previous receipt.
    val reservedFlags = reserved.world["flagsJson"]
    reserved = reserved.copy(world = started.state.world + ("flagsJson" to (reservedFlags ?: "{}")),
      metadata = reserved.metadata + (SPAWN_ENCOUNTER_OWNER to actionId))
    val public = staged.event
    CombatRuntime.pending(reserved)?.let {
      public.put("encounter", JSONObject().put("entityKey", it.entityKey).put("entityName", CombatRuntime.entityName(it.entityKey)).put("encounterId", it.encounterId))
    }
    public.put("npcEncounters", JSONArray().apply {
      SpawnMechanism.pendingNpcEncounters(reserved).forEach { put(JSONObject().put("characterId", it.characterId).put("name", SpawnMechanism.npcDisplayName(it.characterId))) }
    })
    val pending = JSONObject().put("actionId", actionId).put("command", command)
      .put("progress", staged.progress).put("discardKeys", JSONArray(staged.discardKeys.toList()))
      .put("nodeId", staged.nodeId).put("label", staged.label)
      .put("public", public).apply {
        if (playerText != null) put("playerText", playerText)
      }
    repository.save(reserved.copy(world = reserved.world + (MiniMapRuntime.PENDING to pending.toString())))
    return JSONObject().put("committed", false).toString()
  }

  @Synchronized fun retrySceneAction(): String = JSONObject(loadCurrentState().world[MiniMapRuntime.PENDING]
    ?: error("scene_pending_missing")).getJSONObject("command").toString()

  /** Exactly one Core save commits scene/time/encounter/reward and narration receipt. */
  @Synchronized fun commitSceneAction(actionId: String, reply: String, clientJson: String): String {
    require(reply.isNotBlank() && reply.length <= 12000) { "scene_narration_invalid" }
    val core = loadCurrentState()
    core.world[MiniMapRuntime.RECEIPT]?.let { receipt ->
      if (JSONObject(receipt).getString("actionId") == actionId) return clientSnapshot(JSONObject(clientJson), core).toString()
    }
    val pending = JSONObject(core.world[MiniMapRuntime.PENDING] ?: error("scene_pending_missing"))
    require(pending.getString("actionId") == actionId) { "scene_action_mismatch" }
    val session = requireNotNull(ActionRuntime.activeSession(core))
    val turnId = nextTurnId(core)
    val turn = TurnCoordinator.createPending(core, turnId, session.input, true)
    check(turn.error == null) { turn.error ?: "scene_pending_failed" }
    val progressed = ActionRuntime.advance(turn.state, session.sessionId, "scene_commit", session.plannedMinutes ?: 1)
    check(progressed.applied || progressed.duplicate) { progressed.error ?: "scene_time_failed" }
    // Time was already advanced by ActionRuntime: do not execute a second TimeAdvanceCommand.
    val committed = TurnCoordinator.commit(progressed.state, emptyList())
    check(committed.error == null) { committed.error ?: "scene_commit_failed" }
    val finished = ActionRuntime.complete(committed.state, session.sessionId)
    check(finished.applied) { finished.error ?: "scene_session_failed" }
    val node = LevelRouteRegistry.node(pending.getString("nodeId"))
    val flags = JSONObject(finished.state.world["flagsJson"] ?: "{}").apply {
      remove("exploration"); remove("hiddenExitStreak"); remove("exitReady"); remove("lastRolls")
      put("currentLevel", node.asJson())
    }
    val receipt = JSONObject().put("actionId", actionId).put("label", pending.getString("label")).put("reply", reply).apply {
      // Persist exactly what the player submitted, including punctuation/case.
      // A retry uses the original saved utterance, never an AI paraphrase.
      if (pending.has("playerText")) put("playerText", pending.getString("playerText"))
    }
    // Finish a mini-map by atomically dropping its puzzle script and evidence.
    // A pending narration failure leaves the old state untouched for retry.
    val discarded = MiniMapScenario.strings(pending.getJSONArray("discardKeys")).toSet()
    require(discarded.all { it.startsWith("scene.script.") || it.startsWith("scene.outline.") }) { "scene_discard_keys" }
    var next = finished.state.copy(world = (finished.state.world - MiniMapRuntime.PENDING - discarded) + mapOf(
      MiniMapRuntime.STATE to pending.getJSONObject("progress").toString(),
      MiniMapRuntime.RECEIPT to receipt.toString(),
      "levelJson" to node.asJson().toString(), "title" to node.title(), "location" to node.location(), "flagsJson" to flags.toString()),
      metadata = finished.state.metadata - SPAWN_ENCOUNTER_OWNER)
    next = SpawnMechanism.commitPendingNpcEncounters(next)
    if (WorldGraphRuntime.active(MiniMapRuntime.progress(next))) {
      val location = MiniMapRuntime.progress(next).getString("roomId")
      next = next.copy(characters = next.characters.mapValues { (id, character) ->
        if (character.metadata["encountered"] == "true" && core.characters[id]?.metadata?.get("encountered") != "true")
          character.copy(metadata = character.metadata + mapOf("world.roomId" to location, "world.nodeId" to MiniMapRuntime.progress(next).getString("nodeId"))) else character
      })
    }
    next = separateNonPartyNpcsAfterParentTransition(core, next)
    CombatRuntime.pending(next)?.let {
      next = next.copy(metadata = next.metadata + ("combat.encounterNarrative" to JSONObject()
        .put("role", "gm").put("text", reply).put("encounterId", it.encounterId).toString()))
    }
    val beforeView = clientSnapshot(JSONObject(), core)
    val afterView = clientSnapshot(JSONObject(), next)
    val sceneKind = pending.getJSONObject("command").getString("kind")
    val deviceControl = pending.getJSONObject("public").optBoolean("mechanismStep", false)
    val actionText = (when (sceneKind) { "MOVE" -> "Di chuyển: "; "SEARCH" -> "Tìm kiếm: "; "INTERACT" -> "Thao tác: "; else -> "Giao tiếp: " }) + pending.getString("label")
    val continued = JSONObject(com.rabpit.backroom.core.knowledge.StoryContinuityReducer.apply(beforeView.toString(), afterView.toString(), actionText))
    val visualRolls = JSONObject()
    if (CombatRuntime.pending(next) != null) visualRolls.put("entityEncounter", JSONObject().put("eligible", true).put("success", true))
    // Core has already checked the MOVE command against the actual scene graph.
    // Do not infer room transitions from text like "Di chuyển: Kho hồ sơ".
    SnapshotSceneProgress.apply(beforeView, continued, visualRolls,
      if (deviceControl) "DEVICE" else if (sceneKind == "MOVE") "SCENE_MOVE" else "EXECUTE",
      if (deviceControl) "" else actionText)
    next = next.copy(world = next.world + ("flagsJson" to continued.getJSONObject("flags").toString()))
    next = CoreClientProjection.advance(CharacterMemoryBags.recordCommitted(core, next))
    repository.save(next)
    return clientSnapshot(JSONObject(clientJson), next).toString()
  }

  @Synchronized fun takeSceneAmbientEvent(): Boolean {
    val state = loadCurrentState()
    val rolled = SpawnMechanism.ambientNpcRolled(state)
    if (rolled) repository.save(SpawnMechanism.clearAmbientNpcMarker(state))
    return rolled
  }

  /** Fast deterministic pass. Gemini is never called from this method. */
  /** Handle explicit rule-only commands without bypassing Gehihi for normal Explorer turns. */
  fun isSpecialRuleAction(action: String): Boolean =
    MadGodCanon.cheat(action) ||
      AnNhienCanon.matchesPartyCheatCode(action) ||
      SpecialFollowersCanon.matchesPartyCheatCode(action) != null

  @Synchronized fun processRule(clientStateJson: String, action: String): String {
    val client = JSONObject(clientStateJson)
    val state = loadCurrentState()
    if (CombatRuntime.pending(state) != null) return response(false, clientSnapshot(client, state),
      "encounter_confirmation_required", "encounter_confirmation_required")
    if (blocksTextItemAction(action)) return response(true, clientSnapshot(client, state), "item_ui_required", "item_ui_required", "Hãy nhấn vào item và chọn Dùng, Chuyển hoặc Bỏ.")
    if (MadGodCanon.cheat(action)) return applyMadGodCheat(client,state)
    if (AnNhienCanon.matchesPartyCheatCode(action)) return applyAnNhienPartyCheat(client, state)
    SpecialFollowersCanon.matchesPartyCheatCode(action)?.let { targetId ->
      return applySpecialFollowerPartyCheat(client, state, targetId)
    }
    val turnId = nextTurnId(state)
    logger.log(PipelineLogEvent("INPUT", turnId = turnId, details = mapOf("length" to action.length.toString())))
    val pending = TurnCoordinator.createPending(state, turnId, action)
    if (pending.error != null) return response(false, client, pending.error, "pending_rejected")
    val context = contextFor(pending.state)
    val interpreted = rules.interpretSync(action, context)
    interpreted.candidates.forEach { logger.log(PipelineLogEvent("INTENT", turnId = turnId, source = it.source, intent = it.intent, confidence = it.score)) }

    // Player text never has authority to manufacture an acquisition event. Reject immediately,
    // do not call Gemini, do not advance the turn, and do not mutate Inventory.
    if (isDirectPlayerPickupAction(action) || interpreted.candidates.any { it.intent == GameIntent.PICKUP_ITEM }) {
      val result = clientSnapshot(client, state)
      val reply = validationReply("player_pickup_unavailable")
      appendLog(result, action, reply)
      logger.log(PipelineLogEvent("REJECT", turnId = turnId, details = mapOf("reason" to "player_pickup_unavailable")))
      return response(true, result, "player_pickup_unavailable", "validation_rejected", reply)
    }

    if (interpreted.candidates.any { isAuthoritativeItemIntent(it.intent) && it.confidence != IntentConfidence.HIGH }) {
      val result = clientSnapshot(client, state)
      val reply = validationReply("item_action_resolution_required")
      appendLog(result, action, reply)
      logger.log(PipelineLogEvent("REJECT", turnId = turnId, details = mapOf("reason" to "item_action_resolution_required")))
      return response(true, result, "item_action_resolution_required", "validation_rejected", reply)
    }
    if (interpreted.candidates.any { it.intent == GameIntent.NO_ACTION || it.confidence != IntentConfidence.HIGH }) {
      return response(false, client, null, "fallback_required")
    }
    val resolvedCommands = resolver.resolveSequence(interpreted.candidates, turnId, context).filterNotNull()
    if (resolvedCommands.size != interpreted.candidates.size || resolvedCommands.isEmpty()) {
      if (interpreted.candidates.any { isAuthoritativeItemIntent(it.intent) }) {
        val result = clientSnapshot(client, state)
        val reply = validationReply("item_action_resolution_required")
        appendLog(result, action, reply)
        return response(true, result, "item_action_resolution_required", "validation_rejected", reply)
      }
      return response(false, client, null, "resolution_incomplete")
    }
    val commands = resolvedCommands.toMutableList()
    commands.forEach { logger.log(PipelineLogEvent("COMMAND", turnId, it.commandId, it.source)) }
    val committed = commitActionRuntime(pending.state, commands, action, turnId)
    if (committed.error != null) {
      val rejected = TurnCoordinator.reject(pending.state, committed.error)
      val next = CoreClientProjection.advance(rejected.state)
      repository.save(next)
      val result = clientSnapshot(client, next)
      appendLog(result, action, validationReply(committed.error))
      return response(true, result, committed.error, "validation_rejected", validationReply(committed.error))
    }
    val next = CoreClientProjection.advance(CharacterMemoryBags.recordCommitted(state, committed.state))
    repository.save(next)
    val result = clientSnapshot(client, next)
    val reply = eventReply(committed.execution?.events.orEmpty())
    appendLog(result, action, reply)
    logger.log(PipelineLogEvent("COMMIT", turnId = turnId, details = mapOf("commands" to commands.size.toString())))
    return response(true, result, null, "committed", reply)
  }

  private fun applyAnNhienPartyCheat(client: JSONObject, state: GameState): String {
    val alreadyFollowing = AnNhienCanon.isFollowing(state)
    val (updated, error) = AnNhienCanon.forceIntoParty(state)
    val saved = if (error == null) CharacterMemoryBags.recordCommitted(state, updated) else updated
    val result = clientSnapshot(client, saved)
    val reply = when {
      error == "party_full" -> "Party đã đủ tối đa bốn thành viên; không thể thêm An Nhiên nếu chưa có chỗ trống."
      alreadyFollowing -> "An Nhiên đã ở trong Party."
      else -> "An Nhiên đã được thêm vào Party."
    }

    if (error == null) repository.save(saved)
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

  private fun applySpecialFollowerPartyCheat(client: JSONObject, state: GameState, targetId: String): String {
    val ensured = SpecialFollowersCanon.ensure(state)
    val displayName = ensured.characters[targetId]?.name ?: targetId
    val alreadyFollowing = targetId in ensured.party.memberIds
    val (updated, error) = SpecialFollowersCanon.forceIntoParty(ensured, targetId)
    val saved = if (error == null) CharacterMemoryBags.recordCommitted(state, updated) else updated
    val result = clientSnapshot(client, saved)
    val reply = when {
      error == "party_full" -> "Party đã đủ tối đa bốn thành viên; không thể thêm $displayName nếu chưa có chỗ trống."
      alreadyFollowing -> "$displayName đã ở trong Party."
      else -> "$displayName đã được thêm vào Party."
    }

    if (error == null) repository.save(saved)
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

  private fun applyMadGodCheat(client:JSONObject,state:GameState):String {
    val x=MadGodCanon.spawn(state); repository.save(x.state); val out=clientSnapshot(client,x.state);
    val msg=if(x.added) "MadGod Set đã kích hoạt dưới dạng trang bị Bound Forever trong Character Core, không chiếm Inventory." else "MadGod Set đã được kích hoạt; /madgod không tạo thêm trang bị."; appendLog(out,MadGodCanon.CHEAT_CODE,msg); return response(true,out,null,"cheat_committed",msg)
  }

  @Synchronized fun beginAction(clientStateJson: String, kindRaw: String, action: String): String {
    val client = JSONObject(clientStateJson)
    val state = loadCurrentState()
    if (MiniMapRuntime.STATE in state.world) return actionStartResponse(false, null, "scene_ui_required")
    val reserved = CombatRuntime.pending(state)
    if (reserved != null) {
      val active = ActionRuntime.activeSession(state)
      val owner = state.metadata[SPAWN_ENCOUNTER_OWNER]
      if (active != null && owner == active.sessionId &&
          active.input == action && active.kind.name == kindRaw.trim().uppercase()) {
        return actionStartResponse(true, active, null,
          SpawnMechanism.pendingNpcEncounters(state),
          Triple(reserved.entityKey, CombatRuntime.entityName(reserved.entityKey), reserved.encounterId))
      }
      return actionStartResponse(false, null, "encounter_confirmation_required")
    }
    if (MadGodCanon.cheat(action)) return actionStartResponse(true,null,null)
    val kind = enumValues<ActionKind>().firstOrNull { it.name == kindRaw.trim().uppercase() }
      ?: return actionStartResponse(false, null, "action_kind_invalid")
    val existing = ActionRuntime.activeSession(state)
    if (existing != null) {
      // Idempotent retry: the deferred NPC encounters belong to this session and are
      // still pending, so re-expose them for the GM narration prompt.
      return if (existing.kind == kind && existing.input == action) actionStartResponse(true, existing, null,
        SpawnMechanism.pendingNpcEncounters(state))
      else actionStartResponse(false, existing, "action_session_already_active")
    }
    val turnId = nextTurnId(state)
    val sessionId = "$turnId:${kind.name}:${action.hashCode().toUInt()}"
    val started = ActionRuntime.start(
      state = state,
      sessionId = sessionId,
      turnId = turnId,
      actorId = CAO_MINH_ID,
      kind = kind,
      input = action,
      locationKey = state.world["location"] ?: client.optString("location").takeIf(String::isNotBlank),
      plannedMinutes = TimeCostPolicy.estimateMinutes(action),
      searchDepth = if (kind == ActionKind.SEARCH) SearchDepth.NORMAL else null
    )
    if (!started.applied) return actionStartResponse(false, started.session, started.error ?: "action_start_failed")
    // Core-authoritative spawn rolls (not AI): entity + NPC first-meetings.
    // GM only narrates results; Core decides. See EntitySpawnPool.kt.
    // When entity roll succeeds, reserve a real PENDING_ENCOUNTER_CONFIRMATION via
    // CombatRuntime.prepareEncounter() so CoreClientProjection exports pendingEncounter.
    // Battle Start then calls confirmEncounter() to start Combat. GM cannot self-spawn.
    //
    // P0-2 NPC timing: NPC encounters are DEFERRED (prepareSpawns), not marked
    // encountered/spawned yet. The pending encounters are returned in the action-start
    // response so the GM narration prompt can include the meeting event; they are
    // committed only after processValidatedCandidate succeeds, and discarded on abort.
    val withSpawns = runCatching {
      // P1 meta gating: Core determines isMeta authoritatively from action text.
      // Meta turns never roll, never consume RNG, never create pending encounters.
      // Client's isMetaAction() is not trusted for spawn decisions.
      val coreIsMeta = isMetaAction(action)
      val spawnResult = SpawnMechanism.rollSpawns(
        started.state, kind, action,
        isMeta = coreIsMeta, inCombatOrPending = false
      ) { bound -> spawnRng.nextInt(bound) }
      var next = SpawnMechanism.prepareSpawns(
        SpawnMechanism.discardPendingNpcEncounters(started.state), spawnResult)
      // Reserve combat encounter if entity spawned (Core-authoritative, not AI).
      // The encounterId is persistent and flows: prepareEncounter -> pending() ->
      // CoreClientProjection.pendingEncounter -> UI Battle Start -> confirmEncounter().
      if (spawnResult.entityKey != null && CombatRuntime.pending(next) == null && CombatRuntime.active(next) == null) {
        val encounterId = "spawn:${turnId}:${spawnResult.entityKey}:${System.currentTimeMillis()}"
        next = CombatRuntime.prepareEncounter(next, spawnResult.entityKey, encounterId)
          .let { it.copy(metadata = it.metadata + (SPAWN_ENCOUNTER_OWNER to sessionId)) }
        // Entity combat takes precedence this turn: drop deferred NPC encounters.
        // Once-only is NOT consumed — the NPC may roll again on a later turn.
        next = SpawnMechanism.discardPendingNpcEncounters(next)
      }
      next
    }.getOrDefault(started.state)
    repository.save(withSpawns)
    // P0 Entity GM: extract the Core-reserved Entity encounter (if any) for the GM narration prompt.
    // beginAction() returns early if pending() != null, so any pending here was created this turn.
    // Public perception only: entityKey, display name, encounterId. No exit/Core secrets.
    val pendingEntity = CombatRuntime.pending(withSpawns)?.let { snap ->
      Triple(snap.entityKey, CombatRuntime.entityName(snap.entityKey), snap.encounterId)
    }
    return actionStartResponse(true, started.session, null,
      SpawnMechanism.pendingNpcEncounters(withSpawns), pendingEntity)
  }

  @Synchronized fun currentActionContext(): String {
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

  @Synchronized fun abortAction(reason: String): Boolean {
    // P0-3: clear any pending exit authorization (turn did not complete).
    exitAuthorization = null
    if (!repository.exists()) return false
    val state = repository.load()
    if (MiniMapRuntime.PENDING in state.world) return false
    val active = ActionRuntime.activeSession(state) ?: return false
    // P0-2: discard deferred NPC encounters. A failed/aborted turn must NOT consume
    // the once-only first meeting — the NPC may roll again on a later turn.
    val withoutPending = SpawnMechanism.discardPendingNpcEncounters(state)
    val reserved = CombatRuntime.pending(withoutPending)
    val cleaned = if (reserved != null && state.metadata[SPAWN_ENCOUNTER_OWNER] == active.sessionId) {
      val flags = JSONObject(withoutPending.world["flagsJson"] ?: "{}").put("entityEncounterKey", "")
      val cleared = CombatRuntime.clear(withoutPending)
      cleared.copy(
        metadata = cleared.metadata - SPAWN_ENCOUNTER_OWNER,
        world = cleared.world + ("flagsJson" to flags.toString()))
    } else withoutPending
    val interrupted = ActionRuntime.interrupt(SpawnMechanism.clearAmbientNpcMarker(cleaned),
      active.sessionId, reason.ifBlank { "pipeline_error" })
    if (!interrupted.applied) return false
    repository.save(interrupted.state)
    return true
  }

  /**
   * P0-2: exposes deferred NPC encounters so the GM narration prompt can include the
   * first-meeting event. Only characterId/flagKey/display name are exposed — no Core
   * secrets. MainActivity reads `pendingNpcEncounters` and merges it into writerPrompt.
   *
   * P0 Entity GM: also exposes the Core-reserved Entity encounter (if any) as
   * `pendingEntityEncounter: {entityKey, entityName, encounterId}` — public perception
   * only, no exit/Core secrets. MainActivity passes it to writerPrompt/KnowledgeContextEngine
   * so the GM narrates the correct Entity in the same turn.
   */
  private fun actionStartResponse(
    handled: Boolean,
    session: ActionSessionSnapshot?,
    error: String?,
    pendingNpc: List<NpcSpawnTable.NpcEncounter> = emptyList(),
    pendingEntity: Triple<String, String, String>? = null
  ): String = JSONObject().apply {
    put("handled", handled)
    if (session != null) {
      put("sessionId", session.sessionId)
      put("turnId", session.turnId)
      put("kind", session.kind.name)
    }
    if (error != null) put("error", error)
    if (pendingNpc.isNotEmpty()) {
      put("pendingNpcEncounters", JSONArray().apply {
        pendingNpc.forEach { e ->
          put(JSONObject()
            .put("characterId", e.characterId)
            .put("flagKey", e.flagKey)
            .put("name", SpawnMechanism.npcDisplayName(e.characterId)))
        }
      })
    }
    if (pendingEntity != null) {
      put("pendingEntityEncounter", JSONObject()
        .put("entityKey", pendingEntity.first)
        .put("entityName", pendingEntity.second)
        .put("encounterId", pendingEntity.third))
    }
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

  @Synchronized fun currentPartyDetails(clientStateJson: String? = null): String {
    val state = CharacterEquipmentSystem.normalize(loadCurrentState())
    return CharacterDetailJson.encodeParty(CharacterDetailProjector.projectParty(state)).toString()
  }

  @Synchronized fun resetNewGame(): String {
    repository.clear()
    val fresh = newStoryRun(CharacterEquipmentSystem.normalize(GameState.initial()))
    repository.save(fresh)
    return CharacterDetailJson.encodeParty(CharacterDetailProjector.projectParty(fresh)).toString()
  }


  @Synchronized fun characterProgression(characterId: String): String =
    PokerDiceCore.statsJson(loadCurrentState(), characterId).toString()

  @Synchronized fun prepareCombatDice(clientStateJson: String, action: String): String {
    val client = JSONObject(clientStateJson)
    val current = loadCurrentState()
    val combat = CombatRuntime.active(current)
      ?: return response(false, clientSnapshot(client, current),
        "combat_inactive", "combat_dice_inactive")
    return try {
      val next = PokerDiceCore.prepare(current, action, combat.encounterId)
      repository.save(next)
      response(true, clientSnapshot(client, next), null, "combat_dice_prepared")
    } catch (error: Exception) {
      response(false, clientSnapshot(client, current),
        error.message ?: "combat_dice_prepare_failed", "combat_dice_rejected")
    }
  }

  @Synchronized fun setCombatDiceHold(clientStateJson: String, dieIndex: Int, held: Boolean): String {
    val client = JSONObject(clientStateJson)
    val current = loadCurrentState()
    return try {
      val next = PokerDiceCore.setHold(current, dieIndex, held)
      repository.save(next)
      response(true, clientSnapshot(client, next), null, "combat_dice_hold")
    } catch (error: Exception) {
      response(false, clientSnapshot(client, current),
        error.message ?: "combat_dice_hold_failed", "combat_dice_rejected")
    }
  }

  @Synchronized fun rerollCombatDice(clientStateJson: String): String {
    val client = JSONObject(clientStateJson)
    val current = loadCurrentState()
    return try {
      val next = PokerDiceCore.reroll(current)
      repository.save(next)
      response(true, clientSnapshot(client, next), null, "combat_dice_rolled")
    } catch (error: Exception) {
      response(false, clientSnapshot(client, current),
        error.message ?: "combat_dice_roll_failed", "combat_dice_rejected")
    }
  }

  @Synchronized fun finishCombatDice(clientStateJson: String): String {
    val client = JSONObject(clientStateJson)
    val current = loadCurrentState()
    return try {
      val next = PokerDiceCore.finish(current)
      repository.save(next)
      response(true, clientSnapshot(client, next), null, "combat_dice_finalized")
    } catch (error: Exception) {
      response(false, clientSnapshot(client, current),
        error.message ?: "combat_dice_finish_failed", "combat_dice_rejected")
    }
  }

  @Synchronized fun currentCoreState(): String = GameStateCodec.encode(repository.load())

  /** Prompt-only memory for Core-confirmed actors in the current scene. */
  @JvmOverloads @Synchronized fun sceneMemoryContext(action: String = ""): String {
    val state = loadCurrentState()
    // A staged Core encounter is present for narration, even if not committed.
    // Never trust a caller-supplied character ID or activate an aborted event.
    val pending = if (ActionRuntime.activeSession(state) != null)
      SpawnMechanism.pendingNpcEncounters(state).map { it.characterId }.toSet()
    else emptySet()
    val memoryState = state.world[MiniMapRuntime.PENDING]?.let { raw ->
      val staged = JSONObject(raw).getJSONObject("progress")
      if (WorldGraphRuntime.active(staged)) state.copy(world = state.world + (MiniMapRuntime.STATE to staged.toString())) else state
    } ?: state
    val present = CharacterMemoryBags.sceneContext(memoryState, pending)
    // An explicit recollection can reference Cao Minh's own past events without
    // loading or activating any absent character's bag.
    val recalled = CharacterMemoryBags.playerRecallContext(state, action)
    return listOf(present, recalled).filter { it.isNotBlank() }.joinToString("\n")
  }

  /** A manual save is distinct from turn-by-turn Core persistence. */
  @Synchronized fun saveManualCheckpoint(clientStateJson: String): String {
    val current = loadCurrentState()
    if (current.metadata.containsKey("loadError")) return checkpointError("core_load_failed")
    if (current.turn.pending != null || ActionRuntime.activeSession(current) != null) return checkpointError("turn_in_progress")
    val client = JSONObject(clientStateJson)
    if (client.optInt("turn", -1) != CoreClientProjection.turnIndex(current)) return checkpointError("stale_client_state")
    val view = CoreClientProjection.project(client, current)
    repository.saveCheckpoint(current, view)
    return JSONObject().put("ok", true).put("state", view).toString()
  }

  /** Decode and validate the complete slot before changing the current Core state. */
  @Synchronized fun loadManualCheckpoint(): String {
    val current = repository.load()
    if (!current.metadata.containsKey("loadError") &&
      (current.turn.pending != null || ActionRuntime.activeSession(current) != null)) return checkpointError("turn_in_progress")
    val saved = repository.loadCheckpoint() ?: return checkpointError("checkpoint_missing")
    val loaded = saved.core
    repository.save(loaded)
    val restored = CoreClientProjection.project(saved.presentation, loaded)
    return JSONObject().put("ok", true).put("state", restored).toString()
  }

  private fun checkpointError(reason: String): String =
    JSONObject().put("ok", false).put("error", reason).toString()

  @Synchronized fun clear() = repository.clear()
  override fun close() = Unit

  /**
   * Core-authoritative exit destination decision.
   *
   * Core decides the destination node (never GM/AI, never client):
   * - Exit readiness: the COMMITTED hidden-exit streak (flags.exploration.hiddenExitStreak).
   *   The streak is advanced by exactly ONE roll per turn — the turn's gameplay roll,
   *   persisted through HiddenExitStreak.apply into the committed state. Core does NOT
   *   roll a second independent die here: a second roll could authorize a transition
   *   the GM was never told about (or deny one the GM already narrated as discovered),
   *   and would make determineExitDestination non-idempotent across retries.
   * - Traversal intent: parsed by Core from the action text (not client's playerTraverses).
   * - Destination: selected from LevelRouteRegistry valid edges. Single edge -> direct.
   *   Multiple edges -> must match player intent against destination names; ambiguous
   *   means no transition (GM prompts via gmRoutingContext). Core never guesses, and
   *   the GM never chooses secret paths.
   *
   * Three distinct states are enforced:
   *   1. Exit not found (committed streak < 3) -> "exit_not_ready".
   *   2. Exit discovered but not traversed (streak = 3, no traversal intent) ->
   *      "no_traversal_intent"; the discovered exit stays available in later turns
   *      until the player deliberately traverses or the node changes.
   *   3. Transition committed: only when the one-shot exitAuthorization created here
   *      is consumed by processValidatedCandidate. GM prose alone can never move the player.
   *
   * kindRaw is kept for signature compatibility; readiness is purely state-based.
   *
   * Returns {"destinationId": "<node>"} on authorization, or
   * {"destinationId": null, "reason": "..."} otherwise. An authorized destination is
   * stored as a one-shot exitAuthorization consumed by processValidatedCandidate.
   */
  @Synchronized fun determineExitDestination(clientStateJson: String, kindRaw: String, action: String): String {
    val client = JSONObject(clientStateJson)
    val turn = client.optInt("turn", -1)
    // A stale authorization from an aborted or failed turn must never leak into
    // this turn's decision; a fresh one is issued below only when fully authorized.
    exitAuthorization = null
    val core = loadCurrentState()
    val coreView = JSONObject().put("flags", JSONObject(core.world["flagsJson"] ?: "{}"))
    val currentNode = LevelRouteRegistry.requested(JSONObject(core.world.getValue("levelJson")))
      ?: return destResult(null, "no_current_node")

    // Single authoritative readiness source: the committed streak. No second RNG —
    // the GM was already told the outcome of the turn's single roll via levelExit/
    // exitProbe, so Core's decision here always agrees with the narration.
    if (!HiddenExitStreak.ready(coreView))
      return destResult(null, "exit_not_ready")

    if (!corePlayerTraverses(action))
      return destResult(null, "no_traversal_intent")

    val exits = LevelRouteRegistry.exits(currentNode.id)
    if (exits.isEmpty())
      return destResult(null, "no_exits")

    val destinationId = if (exits.size == 1) {
      exits[0]
    } else {
      matchDestinationIntent(action, exits) ?: return destResult(null, "destination_ambiguous")
    }

    // Defense in depth: destination must be a valid edge (never trust selection alone).
    if (!LevelRouteRegistry.canMove(currentNode.id, destinationId))
      return destResult(null, "invalid_edge")

    exitAuthorization = ExitAuthorization(turn, currentNode.id, destinationId)
    return destResult(destinationId, null)
  }

  private fun destResult(destinationId: String?, reason: String?): String =
    JSONObject().apply {
      put("destinationId", destinationId ?: JSONObject.NULL)
      if (reason != null) put("reason", reason)
    }.toString()

  /** Core-owned traversal intent parser (mirrors client playerTraverses; Core never trusts client). */
  private fun corePlayerTraverses(action: String): Boolean {
    val a = action.lowercase()
    return TRAVERSAL_KEYWORDS.any { a.contains(it) }
  }

  /**
   * Match player intent against named destinations. Returns the single matched node id,
   * or null when zero or multiple match (ambiguous -> no transition; GM asks via routing context).
   */
  private fun matchDestinationIntent(action: String, exits: List<String>): String? {
    val a = action.lowercase()
    val matched = exits.filter { id ->
      val node = LevelRouteRegistry.node(id) ?: return@filter false
      a.contains(id.lowercase()) ||
        a.contains(node.name.lowercase()) ||
        (id.matches(NUMBERED_ID) && a.contains("level $id"))
    }
    return matched.singleOrNull()
  }

  /**
   * Commits only the gameplay delta already accepted by the client canon/dice validator.
   * Candidate prose/JSON never becomes storage directly: inventory and party are rebuilt
   * from commands and then projected back onto the UI state.
   */
  @JvmOverloads @Synchronized fun processValidatedCandidate(
    beforeJson: String, candidateJson: String, action: String, narrative: String = ""
  ): String {
    val before = JSONObject(beforeJson)
    val candidate = JSONObject(candidateJson)
    val core = loadCurrentState()
    if (MiniMapRuntime.STATE in core.world) return response(false, clientSnapshot(before, core), "scene_ui_required", "scene_ui_required")
    val reserved = CombatRuntime.pending(core)
    val session = ActionRuntime.activeSession(core)
    val ownedNarration = reserved != null && session != null &&
      core.metadata[SPAWN_ENCOUNTER_OWNER] == session.sessionId &&
      session.input == action && session.turnId == nextTurnId(core)
    if (CombatRuntime.active(core) != null || (reserved != null && !ownedNarration))
      return response(false, clientSnapshot(before, core), "encounter_unresolved", "gemini_delta_rejected")
    if (blocksTextItemAction(action)) return response(false, clientSnapshot(before, core), "item_ui_required", "item_ui_required")
    if (before.optInt("turn", -1) != CoreClientProjection.turnIndex(core))
      return response(false, clientSnapshot(before, core), "stale_client_state", "gemini_delta_rejected")
    // P0-3: Core-authoritative transition validation. A level change is allowed ONLY
    // when determineExitDestination authorized it this turn (Core's own streak +
    // Core's own dice + Core-parsed intent + valid edge). candidate.flags.lastRolls
    // (client data) and before.flags.exploration (client data) are NEVER trusted.
    // GM words alone cannot authorize a move. Teleportation is blocked.
    val currentNode = requireNotNull(LevelRouteRegistry.requested(JSONObject(core.world.getValue("levelJson"))))
    val proposed = LevelRouteRegistry.requested(candidate.optJSONObject("level"))
    if (proposed == null)
      return response(false, clientSnapshot(before, core), "level_change_forbidden", "gemini_delta_rejected")
    if (proposed.id != currentNode.id) {
      val turn = before.optInt("turn", -1)
      val auth = exitAuthorization
      // One-shot: consume the authorization regardless of outcome.
      exitAuthorization = null
      val authorized = auth != null && auth.turn == turn &&
        auth.fromId == currentNode.id && auth.toId == proposed.id &&
        LevelRouteRegistry.canMove(currentNode.id, proposed.id)
      if (!authorized)
        return response(false, clientSnapshot(before, core), "level_change_forbidden", "gemini_delta_rejected")
    } else {
      // No level change: clear any stale authorization.
      exitAuthorization = null
    }
    // Anti-teleport: a location description or flagged level pointing to a third node
    // (neither current nor proposed) indicates an inconsistent or smuggled transition.
    val allowedIds = setOf(currentNode.id, proposed.id)
    val described = LevelRouteRegistry.fromLocation(candidate.optString("location"))
    val flagged = LevelRouteRegistry.requested(candidate.optJSONObject("flags")?.optJSONObject("currentLevel"))
    if ((described != null && described.id !in allowedIds) ||
      (flagged != null && flagged.id !in allowedIds))
      return response(false, clientSnapshot(before, core), "level_change_forbidden", "gemini_delta_rejected")
    if (blocksTextItemAction(action)) return response(false, clientSnapshot(before, core), "item_ui_required", "item_ui_required")
    val turnId = nextTurnId(core)
    val pending = TurnCoordinator.createPending(core, turnId, action, ownedNarration)
    if (pending.error != null) return response(false, before, pending.error, "pending_rejected")
    val commands = mutableListOf<GameCommand>()
    // Narrator/client JSON cannot spawn characters, reserve encounters or change Party.
    val authoritativeFlags = candidate.optJSONObject("flags") ?: JSONObject().also { candidate.put("flags", it) }
    val savedFlags = JSONObject(core.world["flagsJson"] ?: "{}")
    val encounterFields = listOf("anNhien", "iris", "syvial", "lucia", "lucTram", "survivorRegistry",
      "survivorsConfirmed", "entitiesConfirmedLocal", "entityEncounterKey", "jeff", "jane")
    encounterFields.forEach { key ->
      if (savedFlags.has(key)) authoritativeFlags.put(key, savedFlags.get(key)) else authoritativeFlags.remove(key)
    }
    authoritativeFlags.optJSONObject("lastRolls")?.let { rolls ->
      listOf("entityEncounter", "selectedEntityKey", "survivor", "anNhienEncounter", "irisReunion",
        "syvialReunion", "luciaEncounter", "lucTramEncounter").forEach(rolls::remove)
    }
    commands += ValidatedWorldStateCommand(
      commandId = "$turnId:GEMINI:VALIDATED_STATE", turnId = turnId, source = CommandSource.GEMINI,
      location = candidate.optString("location").takeIf(String::isNotBlank),
      title = candidate.optString("title").takeIf(String::isNotBlank),
      levelJson = candidate.optJSONObject("level")?.toString(),
      flagsJson = candidate.optJSONObject("flags")?.toString(),
      validatedByGameEngine = true
    )
    commands += timeAdvanceCommand(turnId, action)

    val committed = commitActionRuntime(pending.state, commands, action, turnId)
    if (committed.error != null) {
      logger.log(PipelineLogEvent("GEMINI_REJECTED", turnId = turnId, source = CommandSource.GEMINI, details = mapOf("reason" to committed.error)))
      // P0-2: turn rejected — leave deferred NPC encounters in place (session still
      // active). A retry of the same action reuses them via the idempotent beginAction
      // path; abortAction discards them. Once-only is NOT consumed by the failure.
      return response(false, before, committed.error, "gemini_delta_rejected")
    }
    // P0-2: turn validated — commit deferred NPC encounters now (mark
    // encountered/spawned/present + character metadata for recruitFollower).
    val withNpcCommitted = SpawnMechanism.commitPendingNpcEncounters(committed.state)
    val protectedState = CharacterProgressionCore.protectFromCandidate(pending.state, withNpcCommitted)
    var applied = protectedState.copy(metadata = protectedState.metadata - SPAWN_ENCOUNTER_OWNER)
    applied = separateNonPartyNpcsAfterParentTransition(core, applied)
    CombatRuntime.pending(applied)?.let { encounter ->
      applied = applied.copy(metadata = applied.metadata + ("combat.encounterNarrative" to
        JSONObject().put("role", "gm").put("text", narrative)
          .put("encounterId", encounter.encounterId).toString()))
    }
    // Persist factual memories atomically with the validated Core transition.
    val accepted = CoreClientProjection.advance(CharacterMemoryBags.recordCommitted(core, applied))
    // The ambient roll is a one-shot presentation event, not persistent game state.
    // Return it only for a successfully committed turn; never replay a stale marker.
    val ambientRolled = SpawnMechanism.ambientNpcRolled(accepted)
    val stored = SpawnMechanism.clearAmbientNpcMarker(accepted)
    repository.save(stored)
    val synchronized = clientSnapshot(candidate, stored)
    logger.log(PipelineLogEvent("GEMINI_COMMIT", turnId = turnId, source = CommandSource.GEMINI, details = mapOf("commands" to commands.size.toString())))
    return JSONObject(response(true, synchronized, null, "gemini_delta_committed"))
      .put("ambientNpcRolled", ambientRolled).toString()
  }

  /**
   * NPCs encountered in an earlier parent Level do not silently travel to the
   * next parent Level. Confirmed Party members continue with Cao Minh; local
   * AREA/SUBLEVEL detours inside the same parent do not cause separation.
   */
  private fun separateNonPartyNpcsAfterParentTransition(before: GameState, after: GameState): GameState {
    val oldNode = before.world["levelJson"]?.let { LevelRouteRegistry.requested(JSONObject(it)) }
      ?: return after
    val newNode = after.world["levelJson"]?.let { LevelRouteRegistry.requested(JSONObject(it)) }
      ?: return after
    if (oldNode.parent == newNode.parent) return after
    val away = after.characters.values.filter { character ->
      character.id != CAO_MINH_ID &&
        character.id !in after.party.memberIds &&
        character.presence == CharacterPresence.ACTIVE &&
        character.metadata["encountered"] == "true" &&
        // Actors introduced by a pending encounter in the destination Level
        // did not travel from the previous Level and must not be separated.
        before.characters[character.id]?.metadata?.get("encountered") == "true"
    }
    if (away.isEmpty()) return after
    val flags = JSONObject(after.world["flagsJson"] ?: "{}")
    val characters = after.characters.toMutableMap()
    away.forEach { character ->
      characters[character.id] = character.copy(presence = CharacterPresence.SEPARATED)
      (character.metadata["encounterFlagKey"] ?: characterFlagKey(character.id))?.let { key ->
        flags.optJSONObject(key)?.let { record ->
          record.put("present", false)
          if (character.id == IRIS_ID || character.id == SYVIAL_ID)
            record.put("continuity", "SEPARATED")
        }
      }
    }
    return after.copy(
      characters = characters,
      world = after.world + ("flagsJson" to flags.toString())
    )
  }

  @Synchronized fun confirmEncounter(clientStateJson: String, encounterId: String): String {
    val client = JSONObject(clientStateJson)
    val current = loadCurrentState()
    if (current.metadata.containsKey(SPAWN_ENCOUNTER_OWNER))
      return response(false, clientSnapshot(client, current), "encounter_narration_pending", "encounter_confirmation_rejected")
    if (client.optInt("turn", -1) != CoreClientProjection.turnIndex(current))
      return response(false, clientSnapshot(client, current), "stale_client_state", "encounter_confirmation_rejected")
    return try {
      val next = CombatRuntime.confirmEncounter(current, encounterId)
      if (next != current) repository.save(next)
      response(true, clientSnapshot(client, next), null, "encounter_confirmed")
    } catch (error: IllegalArgumentException) {
      response(false, clientSnapshot(client, current), error.message, "encounter_confirmation_rejected")
    }
  }

  /** Explicit invitation, separate from the saved meeting; no Explorer Turn is consumed. */
  @Synchronized fun recruitFollower(clientStateJson: String, characterId: String): String {
    val client = JSONObject(clientStateJson)
    val current = loadCurrentState()
    fun reject(reason: String): String = response(false, clientSnapshot(client, current), reason, "recruitment_rejected")
    if (client.optInt("turn", -1) != CoreClientProjection.turnIndex(current))
      return reject("stale_client_state")
    if (CombatRuntime.pending(current) != null || CombatRuntime.active(current) != null ||
      current.turn.pending != null || ActionRuntime.activeSession(current) != null) return reject("turn_in_progress")
    val character = current.characters[characterId] ?: return reject("target_unknown")
    if (character.metadata["encountered"] != "true") return reject("follower_not_encountered")
    if (characterId in current.party.memberIds)
      return response(true, clientSnapshot(client, current), null, "already_in_party", "${character.name} đã ở trong Party.")
    val command = PartyCommand("JOIN:${character.metadata["encounterId"]}:$characterId",
      current.turn.currentTurnId, CAO_MINH_ID, characterId, CommandSource.UI, PartyCommand.Operation.ADD,
      consentConfirmed = character.metadata["joinEligible"] == "true" && character.metadata["followsPlayer"] == "true",
      targetPresent = character.presence == CharacterPresence.ACTIVE &&
        (MiniMapRuntime.STATE !in current.world || !WorldGraphRuntime.active(MiniMapRuntime.progress(current)) ||
          WorldGraphRuntime.characterPresent(current, character)))
    val result = PartyEngine.execute(current, command)
    if (!result.applied) return reject(result.validation.reason ?: "recruitment_failed")
    var next = result.state
    characterFlagKey(characterId)?.let { key ->
      val flags = JSONObject(next.world["flagsJson"] ?: "{}")
      val record = flags.optJSONObject(key) ?: JSONObject()
      flags.put(key, record.put("follower", true).put("joinPending", false))
      next = next.copy(world = next.world + ("flagsJson" to flags.toString()))
    }
    next = CharacterMemoryBags.recordCommitted(current, next)
    repository.save(next)
    return response(true, clientSnapshot(client, next), null, "follower_recruited", "${character.name} đã gia nhập Party.")
  }

  @Synchronized fun processCombat(clientStateJson: String, actionKind: String, action: String): String {
    val client = JSONObject(clientStateJson)
    val current = loadCurrentState()
    val activeCombat = CombatRuntime.active(current)
      ?: return response(false, client, null, "combat_inactive")
    if (!PokerDiceCore.isFinalizedFor(current, action, activeCombat.encounterId)) {
      return response(true, clientSnapshot(client, current),
        "combat_dice_required", "combat_dice_required",
        "Hãy hoàn tất Poker Dice trước khi giải quyết hành động combat.")
    }

    val resolvedEntityKey = CombatRuntime.active(current)?.entityKey.orEmpty()
    var resolution = CombatRuntime.resolve(current, actionKind, action)
    if (!resolution.handled) return response(false, client, null, "combat_inactive")
    var next = resolution.state
    // EXP rewards are awarded after verified Entity defeat by the progression integration (part 4).
    next = PokerDiceCore.clearDice(next)
    val time = TimeEngine.execute(next, TimeAdvanceCommand(
      commandId = "COMBAT:${next.turn.currentTurnId}:${System.nanoTime()}",
      turnId = null,
      actorId = CAO_MINH_ID,
      source = CommandSource.SYSTEM,
      minutes = 1,
      reason = "combat_action"
    ))
    if (time.applied) next = time.state
    next = CharacterStatEngine.applyCompletedTurnRegen(next, "COMBAT_TURN_${CoreClientProjection.turnIndex(current)}")
    if (resolution.entityDestroyed || resolution.escaped) {
      val flags = next.world["flagsJson"]?.let { JSONObject(it) }
        ?: client.optJSONObject("flags")?.let { JSONObject(it.toString()) }
        ?: JSONObject()
      flags.put("entityEncounterKey", "")
      when (resolvedEntityKey) {
        "jeff_the_killer" -> flags.optJSONObject("jeff")?.put("present", false)
        "jane_the_killer" -> flags.optJSONObject("jane")?.put("present", false)
      }
      next = next.copy(world = next.world + ("flagsJson" to flags.toString()))
    }
    // Combat is a Core commit too. Capture any supported state deltas without
    // guessing at NPC witness knowledge or converting combat prose into facts.
    // Combat resolves its own rounds; Explorer Turn advances only on Explorer actions.
    repository.save(CharacterMemoryBags.recordCommitted(current, next))

    val output = clientSnapshot(client, next)
    val combatReply = resolution.reply
    output.put("combatFeedback", JSONObject().apply {
      put("id", "${activeCombat.encounterId}:${activeCombat.eventCounter + 1}")
      put("encounterId", activeCombat.encounterId)
      put("summary", combatReply)
      put("events", JSONArray().apply { resolution.feedback.forEach { put(it.toJson()) } })
      put("turns", JSONArray().apply { resolution.turnCues.forEach { put(it.toJson()) } })
    })
    if (resolution.entityDestroyed || resolution.escaped) {
      val flags = output.optJSONObject("flags") ?: JSONObject().also { output.put("flags", it) }
      flags.put("entityEncounterKey", "")
    }
    if (action != PokerDiceCore.DIRECT_COMBAT_ACTION) {
      appendLog(output, action, combatReply)
    } else if (resolution.completionNarrative.isNotBlank()) {
      // Keep turn-by-turn combat details transient, but retain the Core-confirmed
      // closing narrative after the Combat Log disappears.
      val log = output.optJSONArray("log") ?: JSONArray().also { output.put("log", it) }
      log.put(JSONObject().put("role", "gm").put("text", resolution.completionNarrative))
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

  /** New-run initialization: no Milestone planning, no story checkpoint seeding. */
  private fun newStoryRun(state: GameState): GameState {
    val ready = CoreClientProjection.initialize(state)
    val definition = WorldDefinitions.forNode(MiniMapRuntime.node(ready).id)
    return if (definition != null) WorldGraphRuntime.initialize(ready, definition = definition)
      else MiniMapRuntime.initialize(ready)
  }

  private fun characterFlagKey(id: String): String? = when (id) {
    AN_NHIEN_ID -> "anNhien"
    IRIS_ID -> "iris"
    SYVIAL_ID -> "syvial"
    LUCIA_ID -> "lucia"
    LUC_TRAM_ID -> "lucTram"
    else -> null
  }

  private fun loadCurrentState(): GameState {
    val existed = repository.exists()
    val loaded = if (existed) repository.load() else GameState.initial()
    check(!loaded.metadata.containsKey("loadError")) { "core_load_failed_restore_checkpoint" }
    val normalized = normalizeVisualPresence(CoreClientProjection.initialize(loaded))
    // New runs are initialized without any Milestone planning or story checkpoint seeding.
    val ready = if (existed) normalized else newStoryRun(normalized)
    if (!existed) repository.save(ready)
    return ready
  }

  /** UI state is presentation only; gameplay fields are always reprojected from Core. */
  @Synchronized fun currentClientState(clientStateJson: String): String =
    CoreClientProjection.project(JSONObject(clientStateJson), loadCurrentState()).toString()

  private fun contextFor(state: GameState): GameContext {
    val actors = state.characters.values.associate { it.name.lowercase() to it.id } + mapOf("cao minh" to CAO_MINH_ID, "cao_minh" to CAO_MINH_ID, "iris" to "iris", "syvial" to "syvial", "an nhiên" to AN_NHIEN_ID, "an nhien" to AN_NHIEN_ID, "lục trầm" to LUC_TRAM_ID, "luc tram" to LUC_TRAM_ID)
    val items = state.inventories.values.flatMap { it.items.values }.associate { it.name.lowercase() to it.itemId }
    return GameContext(state, actors, items)
  }

  private fun isAuthoritativeItemIntent(intent: GameIntent): Boolean = intent in setOf(
    GameIntent.PICKUP_ITEM,
    GameIntent.DROP_ITEM,
    GameIntent.USE_ITEM,
    GameIntent.TRANSFER_ITEM,
  )

  private fun isDirectPlayerPickupAction(action: String): Boolean {
    val text = action.trim()
    val directVerb = Regex("(?:^|\\s)(?:nhặt|lượm|cầm\\s+lên|lấy(?:\\s+lên)?|thu\\s+hồi|tịch\\s+thu|nhận(?:\\s+lấy)?|pick\\s+up|take|receive)(?:\\s|$)", RegexOption.IGNORE_CASE)
    val inventoryAssertion = Regex("(?:thêm|đưa).{0,80}(?:vào|trong)\\s+(?:inventory|kho đồ|túi đồ)", RegexOption.IGNORE_CASE)
    return directVerb.containsMatchIn(text) || inventoryAssertion.containsMatchIn(text)
  }

  private fun nextTurnId(state: GameState): String {
    val number = CoreClientProjection.turnIndex(state)
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

  private fun clientSnapshot(client: JSONObject, state: GameState): JSONObject =
    CoreClientProjection.project(client, state)


  private fun appendLog(state: JSONObject, action: String, reply: String) {
    val log = state.optJSONArray("log") ?: JSONArray().also { state.put("log", it) }
    log.put(JSONObject().put("role", "player").put("text", action))
    log.put(JSONObject().put("role", "gm").put("text", reply))
  }

  private fun response(handled: Boolean, state: JSONObject, error: String?, reason: String, reply: String? = null): String = JSONObject().apply {
    put("handled", handled); put("state", state); put("reason", reason)
    if (error != null) put("error", error); if (reply != null) put("reply", reply)
  }.toString()

  private fun eventReply(events: List<String>): String {
    val last = events.lastOrNull { it != "time_advanced" }
    return when {
      last == "item_consumed" -> "Đã sử dụng vật phẩm và áp dụng hiệu ứng có sẵn."
      last == "item_used" -> "Đã sử dụng vật phẩm."
      last == "inventory_remove" -> "Vật phẩm đã được loại khỏi Inventory theo hành động của Cao Minh."
      last == "inventory_transfer" -> "Vật phẩm đã được chuyển giao."
      last == "physiology_water_recorded" -> "Cơn khát đã dịu. Đồng hồ sinh học đã được đặt lại."
      last == "physiology_food_recorded" -> "Cơn đói đã dịu. Đồng hồ sinh học đã được đặt lại."
      last == "hp_already_full" -> "HP đã đầy, không hồi thêm."
      last != null && last.startsWith("hp_healed:") -> {
        val amount = last.substringAfter("hp_healed:").toIntOrNull()
        if (amount != null && amount > 0) "Hồi phục +$amount HP."
        else "Hành động đã được Game State Core xác nhận."
      }
      else -> "Hành động đã được Game State Core xác nhận."
    }
  }

  private fun validationReply(reason: String): String {
    val message = when (reason) {
      "player_pickup_unavailable" -> "Item chỉ được phát từ chiến lợi phẩm Entity do Game Core xác nhận, không thể tự nhặt bằng văn bản."
      "precise_content_amount_forbidden", "item_content_empty" -> "Vật phẩm này hiện không có nội dung khả dụng."
      "madgod_equipment_permanent" -> "MadGod đã khóa vĩnh viễn sau khi trang bị; không thể tháo hoặc đổi."
      "insufficient_item_quantity", "item_not_owned" -> "Cao Minh không có đủ vật phẩm cần thiết cho hành động này."
      "item_action_resolution_required" -> "Không thể xác thực hành động vật phẩm này từ state hiện tại; Inventory không thay đổi."
      "party_full" -> "Party đã đủ tối đa bốn thành viên."
      "join_not_confirmed" -> "Yêu cầu gia nhập chưa đủ điều kiện hoặc chưa được NPC xác nhận."
      else -> "Hành động này không khả dụng trong trạng thái hiện tại."
    }
    return "[Warning] $message"
  }


  @Synchronized fun processItemAction(clientStateJson: String, requestJson: String): String {
    val client = JSONObject(clientStateJson)
    val state = loadCurrentState()
    fun reject(reason: String): String = response(false, clientSnapshot(client, state), reason, "item_ui_rejected", validationReply(reason))
    if (CombatRuntime.pending(state) != null) return reject("encounter_confirmation_required")
    if (client.optInt("turn", -1) != CoreClientProjection.turnIndex(state)) return reject("stale_client_state")
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
    // Inventory commands are atomic Core actions, not Explorer Turns.
    val command = ItemCommand(commandId = "UI:ITEM:${java.util.UUID.randomUUID()}", turnId = null, actorId = actorId,
      targetId = if (operation == ItemCommand.Operation.TRANSFER) targetId else null, source = CommandSource.UI,
      operation = operation, itemId = item.itemId, itemName = item.name, quantity = quantity)
    val execution = StateReducer.execute(state, command)
    if (!execution.applied) return reject(execution.validation.reason ?: "item_action_resolution_required")
    val next = OfflineEntityLoot.collectPending(execution.state)
    repository.save(next)
    val result = clientSnapshot(client, next)
    val reply = eventReply(execution.events)
    appendLog(result, action, reply)
    return response(true, result, null, "item_ui_committed", reply)
  }


  /** Using Cao Minh's spiritual sense is not a UI Inventory operation. Keep real Item verbs blocked. */
  fun blocksTextItemAction(action: String): Boolean {
    val spiritualSense = Regex("(?:^|\\s)(?:dùng|sử dụng)\\s+thần\\s+thức(?:\\s|$)", RegexOption.IGNORE_CASE)
    val itemCommands = action.replace(spiritualSense, " ")
    return rules.interpretSync(itemCommands, GameContext(GameState.initial())).candidates.any {
      it.intent in setOf(GameIntent.USE_ITEM, GameIntent.TRANSFER_ITEM, GameIntent.DROP_ITEM)
    }
  }

}


