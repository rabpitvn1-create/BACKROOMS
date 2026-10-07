package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/** Trusted adapter: the imported JSON engine never consumes player/GM-provided HP or inventory. */
object Combat93Runtime {
  private const val SAVE = "combat93.state"
  private const val REVISION = "combat93.revision"
  private const val TREASURE_KILLS = "combat93.treasureKills"

  data class Resolution(val state: GameState, val handled: Boolean, val reply: String = "")

  fun revision(state: GameState): Long = state.metadata[REVISION]?.toLongOrNull() ?: 0L
  fun active(state: GameState): Boolean = read(state)?.let(CombatChoiceEngine::isActive) == true

  fun start(state: GameState, entityKeys: List<String>, explorationTurn: Int, stageIndex: Int): GameState {
    if (active(state)) return state
    val boundary = boundary(state, explorationTurn, stageIndex)
    CombatChoiceEngine.start(boundary, JSONArray(entityKeys), 0,
      "${state.turn.currentTurnId}:${revision(state)}", 0)
    if (!CombatChoiceEngine.isActive(boundary)) return state
    boundary.getJSONObject("combat").put("encounterId",
      "${state.turn.currentTurnId}:${revision(state)}:${entityKeys.joinToString(",")}")
    return persist(state, boundary)
  }

  fun hold(state: GameState, dieIndex: Int, held: Boolean, expectedRevision: Long): GameState =
    mutate(state, expectedRevision) { CombatChoiceEngine.setHold(it, dieIndex, held) }

  fun roll(state: GameState, expectedRevision: Long): GameState =
    mutate(state, expectedRevision) { CombatChoiceEngine.roll(it) }

  fun finish(state: GameState, expectedRevision: Long): GameState =
    mutate(state, expectedRevision) { CombatChoiceEngine.finishHand(it) }

  fun target(state: GameState, entityIndex: Int, expectedRevision: Long): GameState =
    mutate(state, expectedRevision) { CombatChoiceEngine.setTargetEntity(it, entityIndex) }

  fun resolve(state: GameState, expectedRevision: Long): Resolution {
    // A repeated request from the previous actor must not finalize the next actor's hand.
    if (expectedRevision != revision(state)) return Resolution(state, false)
    val boundary = read(state) ?: return Resolution(state, false)
    if (!CombatChoiceEngine.isActive(boundary)) return Resolution(state, false)
    val combat = boundary.getJSONObject("combat")
    val dice = combat.getJSONObject("diceState")
    if (!dice.optBoolean("finalized") || dice.optBoolean("resolved")) return Resolution(state, false)
    boundary.put("log", JSONArray().put(JSONObject().put("role", "gm").put("text", "")))
    combat.put("logIndex", 0)
    CombatChoiceEngine.resolveFinalized(boundary)
    // 1.1.93a advances exploration once when the encounter ends, never per actor/hand.
    if (!CombatChoiceEngine.isActive(boundary)) boundary.put("turn", boundary.getInt("turn") + 1)
    val log = boundary.getJSONArray("log")
    val reply = (0 until log.length()).map { log.getJSONObject(it).optString("text") }
      .filter { it.isNotBlank() }.joinToString("\n")
    return Resolution(persist(state, boundary), true, reply)
  }

  /** Includes terminal feedback: the killing blow must remain visible after active becomes false. */
  fun toJson(state: GameState): JSONObject? {
    val boundary = read(state) ?: return null
    val combat = boundary.getJSONObject("combat")
    combat.put("revision", revision(state)).put("explorationTurn", boundary.getInt("turn"))
    combat.optJSONObject("diceState")?.put("maxRerolls", CombatChoiceEngine.MAX_REROLLS)
    return combat
  }

  private fun mutate(state: GameState, expectedRevision: Long, operation: (JSONObject) -> Unit): GameState {
    check(expectedRevision == revision(state)) { "combat_revision_mismatch" }
    val boundary = read(state) ?: error("combat_inactive")
    check(CombatChoiceEngine.isActive(boundary)) { "combat_inactive" }
    operation(boundary)
    return persist(state, boundary)
  }

  private fun read(state: GameState): JSONObject? = state.metadata[SAVE]?.let(::JSONObject)

  private fun boundary(state: GameState, explorationTurn: Int, stageIndex: Int): JSONObject {
    val profiles = JSONObject()
    val party = JSONArray()
    var player = JSONObject()
    state.party.memberIds.distinct().forEach { id ->
      val character = state.characters[id] ?: return@forEach
      if (character.presence != CharacterPresence.ACTIVE) return@forEach
      if (id == AN_NHIEN_ID) return@forEach // Protected non-combat follower keeps its current role.
      val effective = CharacterStatEngine.effective(state, id)
      val stats = JSONObject()
      listOf("STR", "DEF", "SKL", "VIT").forEach { key -> stats.put(key, PokerDiceCore.coreStat(state, id, key)) }
      profiles.put(id, JSONObject()
        .put("currentHp", character.vitalState.currentHp.coerceIn(0, effective.maxHp))
        .put("maxHp", effective.maxHp).put("stats", stats).put("statusEffects", JSONArray())
        .put("combatStatus", JSONObject()
          .put("criticalChancePercent", effective.criticalChancePercent)
          .put("evasionPercent", effective.evasionPercent)
          .put("resCriticalPercent", effective.resCriticalPercent).put("resEvasionPercent", effective.resEvasionPercent)))
      // Main's restored stat authority exposes raw weapon damage; the engine applies hand/stats.
      val attack = CharacterStatEngine.weaponDamage(state, id)
      val source = JSONObject().put("id", id).put("name", character.name)
        .put("presence", character.presence.name).put("attack", attack)
      if (id == KAI_ID) player = source else party.put(source)
    }
    check(profiles.has(KAI_ID)) { "combat_player_unavailable" }
    val resource = JSONObject().put("quantity", PokerDiceCore.coreCount(state))
      .put("treasureEntityStageKills", JSONObject(state.metadata[TREASURE_KILLS] ?: "{}"))
    return JSONObject().put("turn", explorationTurn.coerceAtLeast(1))
      .put("combatStageIndex", stageIndex.coerceAtLeast(0))
      .put("location", state.world["location"].orEmpty())
      .put("flags", JSONObject(state.world["flagsJson"] ?: "{}"))
      .put("player", player).put("party", party)
      .put("log", JSONArray().put(JSONObject().put("role", "gm").put("text", "")))
      .put("characterProgression", JSONObject().put("characters", profiles).put("coreResource", resource))
  }

  private fun persist(state: GameState, boundary: JSONObject): GameState {
    var next = state
    val progression = boundary.getJSONObject("characterProgression")
    val profiles = progression.getJSONObject("characters")
    profiles.keys().forEach { id ->
      check(id in next.characters) { "combat_character_unknown" }
      next = CharacterStatEngine.setCurrentHp(next, id, profiles.getJSONObject(id).getInt("currentHp"))
    }
    val resource = progression.getJSONObject("coreResource")
    next = next.copy(metadata = next.metadata + mapOf(
      CharacterProgressionCore.CORE_KEY to resource.getInt("quantity").coerceAtLeast(0).toString(),
      TREASURE_KILLS to (resource.optJSONObject("treasureEntityStageKills") ?: JSONObject()).toString()
    ))
    val loot = boundary.optJSONArray("combat93Loot") ?: JSONArray()
    for (index in 0 until loot.length()) {
      val drop = loot.getJSONObject(index)
      val bandage = drop.getString("id") == "bandage"
      val item = ItemContentRules.normalize(ItemStack(
        if (bandage) BANDAGE_ID else "almond-water",
        if (bandage) HealingItems.BANDAGE_NAME else "Almond Water",
        metadata = mapOf("itemOrigin" to "ENTITY", "omnivaultOriginal" to "true", "consumedOnUse" to "true") +
          if (bandage) emptyMap() else mapOf("physiologyEffect" to "WATER")))
      val inventory = next.inventories[KAI_ID] ?: InventoryState(KAI_ID)
      val error = InventoryPolicy.validateAddition(next, KAI_ID, inventory, item, 1)
      if (error == null) {
        val old = inventory.items[item.itemId]
        val stack = if (old == null) item else old.copy(quantity = old.quantity + 1)
        next = next.copy(inventories = next.inventories +
          (KAI_ID to inventory.copy(items = inventory.items + (item.itemId to stack))))
      } else {
        // Keep overflow loot available in the world instead of silently dropping the reward.
        val flags = boundary.getJSONObject("flags")
        val items = flags.optJSONArray("worldItems") ?: JSONArray()
        items.put(JSONObject().put("id", item.itemId).put("name", item.name).put("quantity", 1)
          .put("available", true).put("instanceId", "combat:${revision(state)}:$index"))
        flags.put("worldItems", items)
      }
    }
    boundary.remove("combat93Loot")
    boundary.remove("log")
    return next.copy(
      world = next.world + mapOf("flagsJson" to boundary.getJSONObject("flags").toString(),
        "location" to boundary.optString("location")),
      metadata = next.metadata + mapOf(SAVE to boundary.toString(),
        REVISION to Math.addExact(revision(state), 1L).toString())
    )
  }
}
