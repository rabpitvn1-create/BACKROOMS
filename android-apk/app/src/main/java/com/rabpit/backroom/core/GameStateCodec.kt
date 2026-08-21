package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

object GameStateCodec {
  fun encode(state: GameState): String = JSONObject().apply {
    put("saveVersion", state.saveVersion)
    put("characters", JSONObject().apply { state.characters.forEach { (id, value) -> put(id, character(value)) } })
    put("party", JSONObject().apply {
      put("leaderId", state.party.leaderId)
      put("memberIds", JSONArray(state.party.memberIds))
      put("maxMembers", state.party.maxMembers)
    })
    put("inventories", JSONObject().apply { state.inventories.forEach { (id, value) -> put(id, inventory(value)) } })
    put("equipment", JSONObject().apply { state.equipment.forEach { (id, value) -> put(id, equipment(value)) } })
    put("statuses", JSONObject().apply { state.statuses.forEach { (id, value) -> put(id, status(value)) } })
    put("omnivault", omnivault(state.omnivault))
    put("turn", turn(state.turn))
    put("time", gameTime(state.time))
    put("world", stringMap(state.world))
    put("metadata", stringMap(state.metadata))
  }.toString()

  fun decode(raw: String): GameState = decode(JSONObject(raw))

  fun decode(root: JSONObject): GameState {
    val version = root.optInt("saveVersion", 0)
    return when {
      version >= CURRENT_SAVE_VERSION -> decodeCurrent(root)
      version == 2 && root.has("inventories") -> migrateV2Core(root)
      else -> LegacySaveMigration.migrate(root)
    }
  }

  private fun migrateV2Core(root: JSONObject): GameState {
    val characters = root.optJSONObject("characters").objectMap(::decodeCharacter)
    val inventories = root.optJSONObject("inventories").objectMap(::decodeInventory).toMutableMap()
    val equipment = root.optJSONObject("equipment").objectMap(::decodeEquipment).toMutableMap()
    val statuses = root.optJSONObject("statuses").objectMap(::decodeStatus)
    val partyJson = root.optJSONObject("party") ?: JSONObject()
    val party = PartyState(
      leaderId = partyJson.optString("leaderId", KAI_ID),
      memberIds = partyJson.optJSONArray("memberIds").strings().ifEmpty { listOf(KAI_ID) },
      maxMembers = partyJson.optInt("maxMembers", 4).coerceAtLeast(1)
    )

    val kaiInventory = inventories[KAI_ID] ?: InventoryState(KAI_ID)
    val cleanedItems = linkedMapOf<String, ItemStack>()
    val migratedSlots = LinkedHashMap(KaiStartingEquipment.slots)
    kaiInventory.items.values.forEach { item ->
      val slot = KaiStartingEquipment.slotFor(item.itemId, item.name)
      if (slot != null) migratedSlots[slot] = KaiStartingEquipment.itemIdForSlot(slot) ?: item.itemId
      else cleanedItems[item.itemId] = item
    }
    inventories[KAI_ID] = kaiInventory.copy(items = cleanedItems)
    equipment[KAI_ID] = EquipmentState(KAI_ID, migratedSlots)

    return GameState(
      characters = characters.ifEmpty { GameState.initial().characters },
      party = party,
      inventories = inventories.ifEmpty { GameState.initial().inventories },
      equipment = equipment.ifEmpty { GameState.initial().equipment },
      statuses = statuses,
      omnivault = decodeOmnivault(root.optJSONObject("omnivault") ?: JSONObject()),
      turn = decodeTurn(root.optJSONObject("turn") ?: JSONObject()),
      time = decodeGameTime(root.optJSONObject("time")),
      world = root.optJSONObject("world").stringsMap(),
      saveVersion = CURRENT_SAVE_VERSION,
      metadata = root.optJSONObject("metadata").stringsMap() + mapOf("migratedFromVersion" to "2", "equipmentSeparated" to "true")
    )
  }

  private fun decodeCurrent(root: JSONObject): GameState {
    val characters = root.optJSONObject("characters").objectMap(::decodeCharacter)
    val inventories = root.optJSONObject("inventories").objectMap(::decodeInventory)
    val equipment = root.optJSONObject("equipment").objectMap(::decodeEquipment)
    val statuses = root.optJSONObject("statuses").objectMap(::decodeStatus)
    val partyJson = root.optJSONObject("party") ?: JSONObject()
    val party = PartyState(
      leaderId = partyJson.optString("leaderId", KAI_ID),
      memberIds = partyJson.optJSONArray("memberIds").strings().ifEmpty { listOf(KAI_ID) },
      maxMembers = partyJson.optInt("maxMembers", 4).coerceAtLeast(1)
    )
    return GameState(
      characters = characters.ifEmpty { GameState.initial().characters },
      party = party,
      inventories = inventories.ifEmpty { GameState.initial().inventories },
      equipment = equipment.ifEmpty { GameState.initial().equipment },
      statuses = statuses,
      omnivault = decodeOmnivault(root.optJSONObject("omnivault") ?: JSONObject()),
      turn = decodeTurn(root.optJSONObject("turn") ?: JSONObject()),
      time = decodeGameTime(root.optJSONObject("time")),
      world = root.optJSONObject("world").stringsMap(),
      saveVersion = CURRENT_SAVE_VERSION,
      metadata = root.optJSONObject("metadata").stringsMap()
    )
  }

  private fun character(value: CharacterState) = JSONObject().apply {
    put("id", value.id); put("name", value.name); putNullable("avatarRef", value.avatarRef)
    putNullable("healthState", value.healthState); put("injuries", JSONArray(value.injuries))
    put("presence", value.presence.name); put("inventoryId", value.inventoryId); put("equipmentId", value.equipmentId)
    put("statusIds", JSONArray(value.statusIds.toList())); put("metadata", stringMap(value.metadata))
  }

  private fun decodeCharacter(json: JSONObject) = CharacterState(
    id = json.optString("id"), name = json.optString("name"),
    avatarRef = json.nullableString("avatarRef"), healthState = json.nullableString("healthState"),
    injuries = json.optJSONArray("injuries").strings(),
    presence = enumOr(CharacterPresence.ACTIVE, json.optString("presence")),
    inventoryId = json.optString("inventoryId", json.optString("id")),
    equipmentId = json.optString("equipmentId", json.optString("id")),
    statusIds = json.optJSONArray("statusIds").strings().toSet(), metadata = json.optJSONObject("metadata").stringsMap()
  )

  private fun item(value: ItemStack) = JSONObject().apply {
    val normalized = ItemContentRules.normalize(value)
    put("itemId", normalized.itemId); put("name", normalized.name); put("quantity", normalized.quantity)
    putNullable("condition", normalized.condition); put("metadata", stringMap(normalized.metadata))
    put("archetypeId", normalized.archetypeId); put("contentState", normalized.contentState.name)
  }

  private fun decodeItem(json: JSONObject): ItemStack = ItemContentRules.normalize(ItemStack(
    itemId = json.optString("itemId"),
    name = json.optString("name"),
    quantity = json.optInt("quantity", 1).coerceAtLeast(1),
    condition = json.nullableString("condition"),
    metadata = json.optJSONObject("metadata").stringsMap(),
    archetypeId = json.optString("archetypeId", json.optString("itemId")),
    contentState = enumOr(ContentState.NONE, json.optString("contentState"))
  ))

  private fun itemMap(json: JSONObject?): Map<String, ItemStack> {
    if (json == null) return emptyMap()
    val result = linkedMapOf<String, ItemStack>()
    json.keys().forEach { key ->
      val decoded = json.optJSONObject(key)?.let(::decodeItem) ?: return@forEach
      val old = result[decoded.itemId]
      result[decoded.itemId] = if (old != null && ItemContentRules.sameStackState(old, decoded)) old.copy(quantity = old.quantity + decoded.quantity) else decoded
    }
    return result
  }

  private fun inventory(value: InventoryState) = JSONObject().apply {
    put("ownerId", value.ownerId); put("items", JSONObject().apply { value.items.values.forEach { stack -> put(ItemContentRules.normalize(stack).itemId, item(stack)) } })
  }

  private fun decodeInventory(json: JSONObject) = InventoryState(json.optString("ownerId"), itemMap(json.optJSONObject("items")))

  private fun equipment(value: EquipmentState) = JSONObject().apply { put("ownerId", value.ownerId); put("slots", stringMap(value.slots)) }
  private fun decodeEquipment(json: JSONObject) = EquipmentState(json.optString("ownerId"), json.optJSONObject("slots").stringsMap())

  private fun status(value: StatusEffect) = JSONObject().apply {
    put("id", value.id); put("type", value.type); put("source", value.source); putNullable("startTurnId", value.startTurnId)
    putNullable("durationTurns", value.durationTurns); put("persistent", value.persistent); put("metadata", stringMap(value.metadata))
  }

  private fun decodeStatus(json: JSONObject) = StatusEffect(
    json.optString("id"), json.optString("type"), json.optString("source"), json.nullableString("startTurnId"),
    if (json.has("durationTurns") && !json.isNull("durationTurns")) json.optInt("durationTurns") else null,
    json.optBoolean("persistent"), json.optJSONObject("metadata").stringsMap()
  )

  private fun omnivault(value: OmnivaultState) = JSONObject().apply {
    put("ownerId", value.ownerId)
    put("storedItems", JSONObject().apply { value.storedItems.values.forEach { stack -> put(ItemContentRules.normalize(stack).itemId, item(stack)) } })
    put("scanSlots", JSONArray().apply { value.scanSlots.forEach { slot -> put(JSONObject().apply {
      put("slot", slot.slot); put("sourceItemId", ItemContentRules.normalize(slot.templateItem).itemId); put("templateItem", item(slot.templateItem)); put("scannedAtEpochMs", slot.scannedAtEpochMs)
    }) } })
    put("markedSourceIds", JSONArray(value.markedSourceIds.toList()))
    put("restoreCooldownUntilEpochMs", JSONObject().apply { value.restoreCooldownUntilEpochMs.forEach { (id, time) -> put(id, time) } })
  }

  private fun decodeOmnivault(json: JSONObject): OmnivaultState {
    val slots = json.optJSONArray("scanSlots").objects().map { slot ->
      val template = decodeItem(slot.optJSONObject("templateItem") ?: JSONObject())
      ScanSlot(slot.optInt("slot"), template.itemId, template, slot.optLong("scannedAtEpochMs"))
    }
    val cooldowns = mutableMapOf<String, Long>()
    json.optJSONObject("restoreCooldownUntilEpochMs")?.let { values -> values.keys().forEach { cooldowns[it] = values.optLong(it) } }
    return OmnivaultState(
      json.optString("ownerId", KAI_ID), itemMap(json.optJSONObject("storedItems")), slots,
      json.optJSONArray("markedSourceIds").strings().toSet(), cooldowns
    )
  }

  private fun turn(value: TurnState) = JSONObject().apply {
    put("currentTurnId", value.currentTurnId); putNullable("pending", value.pending?.let(::pending))
    put("completedTurnIds", JSONArray(value.completedTurnIds.toList())); put("executedCommandIds", JSONArray(value.executedCommandIds.toList()))
  }

  private fun pending(value: PendingTurn) = JSONObject().apply {
    put("turnId", value.turnId); put("input", value.input); put("status", value.status.name)
    put("commandIds", JSONArray(value.commandIds)); putNullable("error", value.error)
  }

  private fun decodeTurn(json: JSONObject): TurnState {
    val pendingJson = json.optJSONObject("pending")
    val pending = pendingJson?.let { PendingTurn(it.optString("turnId"), it.optString("input"), enumOr(PendingTurnStatus.CREATED, it.optString("status")), it.optJSONArray("commandIds").strings(), it.nullableString("error")) }
    return TurnState(json.optString("currentTurnId", "TURN_1"), pending, json.optJSONArray("completedTurnIds").strings().toSet(), json.optJSONArray("executedCommandIds").strings().toSet())
  }

  private fun gameTime(value: GameTimeState) = JSONObject().apply {
    put("elapsedSubjectiveMinutes", value.elapsedSubjectiveMinutes)
    put("lastAdvanceMinutes", value.lastAdvanceMinutes)
    putNullable("lastAdvanceReason", value.lastAdvanceReason)
  }

  private fun decodeGameTime(json: JSONObject?): GameTimeState {
    if (json == null) return GameTimeState()
    return GameTimeState(
      elapsedSubjectiveMinutes = json.optLong("elapsedSubjectiveMinutes", 0L).coerceAtLeast(0L),
      lastAdvanceMinutes = json.optInt("lastAdvanceMinutes", 0).coerceAtLeast(0),
      lastAdvanceReason = json.nullableString("lastAdvanceReason")
    )
  }

  private fun stringMap(values: Map<String, String>) = JSONObject().apply { values.forEach { (key, value) -> put(key, value) } }
}

object LegacySaveMigration {
  fun migrate(root: JSONObject): GameState {
    val initial = GameState.initial()
    val turnNumber = root.optInt("turn", 1).coerceAtLeast(1)
    val migrated = linkedMapOf<String, ItemStack>()
    val equipmentSlots = LinkedHashMap(KaiStartingEquipment.slots)
    root.optJSONArray("inventory").objects().mapIndexedNotNull { index, json ->
      val name = json.optString("name").trim()
      if (name.isEmpty()) null else {
        val id = json.optString("id").ifBlank { name.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').ifBlank { "legacy-item-$index" } }
        ItemContentRules.normalize(ItemStack(id, name, json.optInt("quantity", 1).coerceAtLeast(1), json.nullableString("state"), mapOf("migrated" to "legacy-v0")))
      }
    }.forEach { item ->
      val slot = KaiStartingEquipment.slotFor(item.itemId, item.name)
      if (slot != null) {
        equipmentSlots[slot] = KaiStartingEquipment.itemIdForSlot(slot) ?: item.itemId
      } else {
        val old = migrated[item.itemId]
        migrated[item.itemId] = if (old != null && ItemContentRules.sameStackState(old, item)) old.copy(quantity = old.quantity + item.quantity) else item
      }
    }
    val partyCharacters = root.optJSONArray("party").objects().mapIndexedNotNull { index, json ->
      val name = json.optString("name").trim()
      if (name.isEmpty()) null else {
        val id = json.optString("id").ifBlank { "legacy-party-$index" }
        id to CharacterState(id, name, avatarRef = json.nullableString("avatar"))
      }
    }.toMap()
    val characters = initial.characters + partyCharacters
    val partyIds = (listOf(KAI_ID) + partyCharacters.keys).distinct().take(4)
    return initial.copy(
      characters = characters,
      party = PartyState(memberIds = partyIds),
      inventories = initial.inventories + (KAI_ID to InventoryState(KAI_ID, migrated)) + partyCharacters.keys.associateWith { InventoryState(it) },
      equipment = initial.equipment + (KAI_ID to EquipmentState(KAI_ID, equipmentSlots)) + partyCharacters.keys.associateWith { EquipmentState(it) },
      turn = TurnState(currentTurnId = "TURN_$turnNumber"),
      world = mapOf("title" to root.optString("title"), "location" to root.optString("location")),
      metadata = mapOf("migratedFromVersion" to root.optInt("saveVersion", 0).toString(), "equipmentSeparated" to "true")
    )
  }
}

private fun JSONObject.putNullable(key: String, value: Any?) { put(key, value ?: JSONObject.NULL) }
private fun JSONObject.nullableString(key: String): String? = if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
private fun JSONObject?.stringsMap(): Map<String, String> {
  if (this == null) return emptyMap()
  val result = mutableMapOf<String, String>(); keys().forEach { result[it] = optString(it) }; return result
}
private fun <T> JSONObject?.objectMap(decode: (JSONObject) -> T): Map<String, T> {
  if (this == null) return emptyMap()
  val result = mutableMapOf<String, T>(); keys().forEach { key -> optJSONObject(key)?.let { result[key] = decode(it) } }; return result
}
private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONObject)
private inline fun <reified T : Enum<T>> enumOr(fallback: T, value: String): T = enumValues<T>().firstOrNull { it.name == value } ?: fallback
