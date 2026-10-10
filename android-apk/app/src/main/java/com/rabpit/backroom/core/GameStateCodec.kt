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
    require(version == CURRENT_SAVE_VERSION) { "Unsupported save schema: $version" }
    val decoded = decodeCurrent(root)
    return CharacterProgressionCore.normalize(CharacterEquipmentSystem.normalize(SpecialFollowersCanon.ensure(AnNhienCanon.ensure(decoded))))
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
    putNullable("healthState", value.healthState)
    put("statProfile", characterStatProfile(value.statProfile))
    put("vitalState", characterVitalState(value.vitalState))
    put("injuries", JSONArray(value.injuries))
    put("presence", value.presence.name); put("inventoryId", value.inventoryId); put("equipmentId", value.equipmentId)
    put("statusIds", JSONArray(value.statusIds.toList())); put("physiology", physiology(value.physiology)); put("metadata", stringMap(value.metadata))
  }

  private fun decodeCharacter(json: JSONObject): CharacterState {
    val id = json.optString("id")
    val profile = decodeCharacterStatProfile(json.optJSONObject("statProfile"), id)
    val vital = decodeCharacterVitalState(json.optJSONObject("vitalState"), profile)
    return CharacterState(
    id = id, name = json.optString("name"),
    avatarRef = json.nullableString("avatarRef"), healthState = json.nullableString("healthState"),
    injuries = json.optJSONArray("injuries").strings(),
    presence = enumOr(CharacterPresence.ACTIVE, json.optString("presence")),
    inventoryId = json.optString("inventoryId", json.optString("id")),
    equipmentId = json.optString("equipmentId", json.optString("id")),
    statusIds = json.optJSONArray("statusIds").strings().toSet(),
    physiology = decodePhysiology(json.optJSONObject("physiology")),
    metadata = json.optJSONObject("metadata").stringsMap(),
    statProfile = profile,
    vitalState = vital
  )
  }

  private fun characterStatProfile(value: CharacterStatProfile) = JSONObject().apply {
    put("schema", CharacterProgressionCore.SCHEMA)
    put("baseMaxHp", CharacterProgressionCore.BASE_MAX_HP)
    put("STR", value.str)
    put("DEF", value.def)
    put("SKL", value.skl)
    put("VIT", value.vit)
  }

  private fun decodeCharacterStatProfile(json: JSONObject?, characterId: String): CharacterStatProfile {
    val fallback = CharacterStatProfiles.forId(characterId)
    if (json == null || json.optString("schema") != CharacterProgressionCore.SCHEMA) return fallback
    return fallback.copy(
      baseMaxHp = CharacterProgressionCore.BASE_MAX_HP,
      str = json.optInt("STR", CharacterProgressionCore.BASE_STAT).coerceIn(5, 999),
      def = json.optInt("DEF", CharacterProgressionCore.BASE_STAT).coerceIn(5, 999),
      skl = json.optInt("SKL", CharacterProgressionCore.BASE_STAT).coerceIn(5, 999),
      vit = json.optInt("VIT", CharacterProgressionCore.BASE_STAT).coerceIn(5, 999),
      schema = CharacterProgressionCore.SCHEMA
    )
  }

  private fun characterVitalState(value: CharacterVitalState) = JSONObject().apply {
    put("currentHp", value.currentHp)
    put("condition", value.condition.name)
  }

  private fun decodeCharacterVitalState(json: JSONObject?, profile: CharacterStatProfile): CharacterVitalState =
    CharacterVitalState(
      currentHp = json?.optInt("currentHp", CharacterProgressionCore.BASE_MAX_HP)?.coerceAtLeast(0)
        ?: CharacterProgressionCore.BASE_MAX_HP,
      condition = enumOr(CharacterCondition.HEALTHY, json?.optString("condition").orEmpty())
    )

  private fun physiology(value: PhysiologyState) = JSONObject().apply {
    putNullable("minutesSinceFood", value.minutesSinceFood)
    putNullable("minutesSinceWater", value.minutesSinceWater)
    putNullable("minutesAwake", value.minutesAwake)
    putNullable("painState", value.painState)
    putNullable("infectionState", value.infectionState)
    putNullable("thermalState", value.thermalState)
    put("metadata", stringMap(value.metadata))
  }

  private fun decodePhysiology(json: JSONObject?): PhysiologyState {
    if (json == null) return PhysiologyState()
    return PhysiologyState(
      minutesSinceFood = json.nullableLong("minutesSinceFood")?.coerceAtLeast(0L),
      minutesSinceWater = json.nullableLong("minutesSinceWater")?.coerceAtLeast(0L),
      minutesAwake = json.nullableLong("minutesAwake")?.coerceAtLeast(0L),
      painState = json.nullableString("painState"),
      infectionState = json.nullableString("infectionState"),
      thermalState = json.nullableString("thermalState"),
      metadata = json.optJSONObject("metadata").stringsMap()
    )
  }

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

private fun JSONObject.putNullable(key: String, value: Any?) { put(key, value ?: JSONObject.NULL) }
private fun JSONObject.nullableString(key: String): String? = if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
private fun JSONObject.nullableLong(key: String): Long? = if (!has(key) || isNull(key)) null else optLong(key)
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
