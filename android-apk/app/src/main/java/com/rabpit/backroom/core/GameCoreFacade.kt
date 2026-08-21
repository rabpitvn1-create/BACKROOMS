package com.rabpit.backroom.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class GameCoreFacade private constructor(
  private val repository: SaveRepository,
  private val logger: GamePipelineLogger,
  private val localModel: LiteRTIntentInterpreter
) : AutoCloseable {
  private val rules = RuleIntentInterpreter()
  private val resolver = CommandResolver()

  /** Fast deterministic pass. Gemini is never called from this method. */
  fun processRule(legacyStateJson: String, action: String): String {
    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
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
    if (interpreted.candidates.any { it.intent == GameIntent.PICKUP_ITEM }) {
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

    if (interpreted.candidates.any { it.intent == GameIntent.NO_ACTION || it.confidence != IntentConfidence.HIGH }) {
      return response(false, legacy, null, "fallback_required")
    }
    val commands = interpreted.candidates.mapIndexedNotNull { index, candidate -> resolver.resolve(candidate, index, turnId, context) }
    if (commands.size != interpreted.candidates.size || commands.isEmpty()) return response(false, legacy, null, "resolution_incomplete")
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

  fun currentCoreState(): String = GameStateCodec.encode(repository.load())
  fun clear() = repository.clear()
  override fun close() = localModel.close()

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
    val inventoryLocked = GameIntent.PICKUP_ITEM in actionIntents || GameIntent.OMNIVAULT_RESTORE in actionIntents

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

    (current.keys + desiredById.keys).sorted().forEachIndexed { index, id ->
      val old = current[id]?.quantity ?: 0; val desired = desiredById[id]?.quantity ?: 0
      if (desired == old) return@forEachIndexed
      val stack = desiredById[id] ?: current.getValue(id)
      commands += ItemCommand(
        "$turnId:GEMINI:INV:$index", turnId, KAI_ID, source = CommandSource.GEMINI,
        operation = if (desired > old) ItemCommand.Operation.PICKUP else ItemCommand.Operation.DROP,
        itemId = id, itemName = stack.name, quantity = kotlin.math.abs(desired - old)
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

    val committed = TurnCoordinator.commit(pending.state, commands)
    if (committed.error != null) {
      logger.log(PipelineLogEvent("GEMINI_REJECTED", turnId = turnId, source = CommandSource.GEMINI, details = mapOf("reason" to committed.error)))
      return response(false, before, committed.error, "gemini_delta_rejected")
    }
    repository.save(committed.state)
    val synchronized = syncLegacy(candidate, committed.state, incrementTurn = false)
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
    val actors = state.characters.values.associate { it.name.lowercase() to it.id } + mapOf("kai" to KAI_ID, "iris" to "iris", "syvial" to "syvial")
    val items = (state.inventories.values.flatMap { it.items.values } + state.omnivault.storedItems.values).associate { it.name.lowercase() to it.itemId }
    return GameContext(state, actors, items)
  }

  private fun nextTurnId(legacy: JSONObject, state: GameState): String {
    val number = legacy.optInt("turn", state.turn.currentTurnId.substringAfterLast('_').toIntOrNull() ?: 1)
    return "TURN_${number.coerceAtLeast(1)}"
  }

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

  private fun eventReply(events: List<String>): String = when (events.lastOrNull()) {
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
      "player_pickup_unavailable", "restore_narrative_only", "precise_content_amount_forbidden", "item_content_empty" -> "This action is not available."
      "scan_source_missing", "scan_template_missing" -> "There is no object available for scanning or multiplying."
      "insufficient_item_quantity", "item_not_owned" -> "This action is not available."
      "party_full" -> "Party đã đủ tối đa bốn thành viên."
      "join_not_confirmed" -> "Yêu cầu gia nhập chưa đủ điều kiện hoặc chưa được NPC xác nhận."
      "living_target_forbidden" -> "Omnivault không thể tác động lên sinh vật sống."
      else -> "This action is not available."
    }
    return "[Warning] $message"
  }

  companion object {
    @JvmStatic fun create(context: Context, debugLogging: Boolean = false): GameCoreFacade = GameCoreFacade(
      SharedPreferencesSaveRepository(context.applicationContext), AndroidGamePipelineLogger(debugLogging), LiteRTIntentInterpreter(context.applicationContext)
    )
  }
}
