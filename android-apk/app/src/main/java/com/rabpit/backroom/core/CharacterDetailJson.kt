package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

object CharacterDetailJson {
  fun encodeParty(projection: PartyDetailProjection): JSONObject = JSONObject().apply {
    put("leaderId", projection.leaderId); put("maxMembers", projection.maxMembers); put("elapsedSubjectiveMinutes", projection.elapsedSubjectiveMinutes)
    put("members", JSONArray().apply { projection.members.forEach { put(encodeCharacter(it)) } })
  }

  fun encodeCharacter(c: CharacterDetailProjection): JSONObject = JSONObject().apply {
    put("id", c.id); put("name", c.name); c.avatarRef?.let { put("avatar", it) }; put("presence", c.presence.name); put("isLeader", c.isLeader)
    c.healthState?.let { put("healthState", it) }; put("currentHp", c.currentHp); put("maxHp", c.maxHp)
    put("role", c.role); put("energy", c.energyDisplay); put("hpRegen", c.regenPerCompletedTurn); put("condition", c.condition.name)
    put("stats", JSONObject().apply { put("STR", stat(c.str)); put("DF", stat(c.df)); put("AGI", stat(c.agi)); put("CRIT", stat(c.crit)) })
    put("injuries", JSONArray(c.injuries))
    put("physiology", JSONObject().apply {
      put("hunger", c.physiology.hunger.name); put("thirst", c.physiology.thirst.name); put("sleepDeprivation", c.physiology.sleepDeprivation.name)
      c.physiology.foodPercent?.let { put("foodPercent", it) }; c.physiology.waterPercent?.let { put("waterPercent", it) }; c.physiology.restPercent?.let { put("restPercent", it) }
      c.physiology.pain?.let { put("pain", it) }; c.physiology.infection?.let { put("infection", it) }; c.physiology.thermal?.let { put("thermal", it) }
    })
    put("inventory", JSONArray().apply {
      if (c.inventoryDetails.isNotEmpty()) c.inventoryDetails.forEach { put(item(it)) }
      else c.inventory.forEach { stack -> put(JSONObject().apply {
        val inspection = ItemSystem.inspect(stack, ownerId = c.id)
        put("id", stack.itemId); put("name", stack.name); put("quantity", stack.quantity)
        put("description", inspection.description); put("itemType", inspection.itemType); put("ownerId", c.id)
        put("capabilities", JSONArray(inspection.capabilities.toList()))
        stack.condition?.let { put("state", it) }; put("contentState", stack.contentState.name)
      }) }
    })
    put("inventoryCapacity", JSONObject().put("used", c.inventoryCapacityUsed).put("max", c.inventoryCapacityMax))
    put("skills", JSONArray().apply {
      CompanionSkillCatalog.forCharacter(c.id).forEach { skill -> put(JSONObject().apply {
        put("name", skill.name)
        put("kind", skill.kind)
        put("trigger", skill.trigger)
        put("effect", skill.effect)
        skill.note?.let { put("note", it) }
      }) }
    })
    put("equipment", JSONObject(c.equipment))
    put("equipmentItems", JSONArray().apply { c.equipmentDetails.forEach { put(item(it)) } })
    put("statuses", JSONArray().apply { c.statusEffects.forEach { e -> put(JSONObject().put("id", e.id).put("type", e.type).put("persistent", e.persistent)) } })
  }

  private fun stat(x: StatLineProjection) = JSONObject().put("base", x.base).put("equipment", x.equipment).put("effective", x.effective)
  private fun comparison(x: StatComparisonProjection) = JSONObject().put("before", x.before).put("after", x.after).put("delta", x.delta)
  private fun itemComparison(x: ItemComparisonProjection) = JSONObject().apply {
    put("maxHp", comparison(x.maxHp)); put("STR", comparison(x.str)); put("DF", comparison(x.df)); put("AGI", comparison(x.agi)); put("CRIT", comparison(x.crit))
  }
  private fun bonuses(x: EquipmentBonuses) = JSONObject().put("HP", x.hp).put("STR", x.str).put("DF", x.df).put("AGI", x.agi).put("CRIT", x.crit)
  private fun weapon(x: WeaponGameplayStats) = JSONObject().apply {
    put("DMG", x.dmg); x.ammoDisplay?.let { put("ammo", it) }; x.rpmCapability?.let { put("rpm", it) }; put("fireModes", JSONArray(x.fireModes))
  }
  private fun component(x: EquipmentComponent) = JSONObject().apply { put("name", x.name); put("bonuses", bonuses(x.bonuses)); x.weapon?.let { put("weapon", weapon(it)) } }
  private fun item(x: ItemDetailProjection) = JSONObject().apply {
    val inspection = x.inspection
    put("id", x.id); put("name", x.name); put("quantity", x.quantity); x.type?.let { put("type", it) }; x.slot?.let { put("slot", it) }; x.rarity?.let { put("rarity", it) }
    put("description", inspection.description); put("itemType", inspection.itemType); inspection.ownerId?.let { put("ownerId", it) }
    put("capabilities", JSONArray(inspection.capabilities.toList()))
    put("equipped", x.equipped); put("equippedSlots", JSONArray(x.equippedSlots)); put("statItem", x.statItem); x.classification?.let { put("classification", it) }
    put("consumesInventorySlot", !x.equipped)
    put("bonuses", bonuses(x.bonuses)); x.weapon?.let { put("weapon", weapon(it)) }
    put("abilities", JSONArray().apply { x.abilities.forEach { a -> put(JSONObject().put("name", a.name).put("description", a.description).also { o -> a.importantLimit?.let { o.put("limit", it) } }) } })
    put("restrictions", JSONArray(x.restrictions)); put("components", JSONArray().apply { x.components.forEach { put(component(it)) } })
    x.comparison?.let { put("comparison", itemComparison(it)) }; x.baseItemEffect?.let { put("baseItemEffect", itemComparison(it)) }
  }
}
