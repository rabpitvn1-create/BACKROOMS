package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

object CharacterDetailJson {
  fun encodeParty(projection: PartyDetailProjection): JSONObject = JSONObject().apply {
    put("leaderId", projection.leaderId)
    put("maxMembers", projection.maxMembers)
    put("elapsedSubjectiveMinutes", projection.elapsedSubjectiveMinutes)
    put("members", JSONArray().apply { projection.members.forEach { put(encodeCharacter(it)) } })
  }

  fun encodeCharacter(c: CharacterDetailProjection): JSONObject = JSONObject().apply {
    put("id", c.id); put("name", c.name); c.avatarRef?.let { put("avatar", it) }
    put("presence", c.presence.name); put("isLeader", c.isLeader); c.healthState?.let { put("healthState", it) }
    put("currentHp", c.currentHp); put("maxHp", c.maxHp); put("role", c.role)
    put("energy", c.energyDisplay); put("hpRegen", c.regenPerCompletedTurn); put("condition", c.condition.name)
    put("stats", JSONObject().apply {
      put("STR", stat(c.str)); put("DEF", stat(c.def)); put("SKL", stat(c.skl)); put("VIT", stat(c.vit))
    })
    put("combatStatus", JSONObject()
      .put("criticalChancePercent", c.criticalChancePercent)
      .put("criticalDamagePercent", c.criticalDamagePercent)
      .put("evasionPercent", c.evasionPercent)
      .put("criticalResistancePercent", c.criticalResistancePercent)
      .put("evasionResistancePercent", c.evasionResistancePercent))
    put("injuries", JSONArray(c.injuries))
    put("physiology", JSONObject().apply {
      put("hunger", c.physiology.hunger.name); put("thirst", c.physiology.thirst.name); put("sleepDeprivation", c.physiology.sleepDeprivation.name)
      c.physiology.foodPercent?.let { put("foodPercent", it) }; c.physiology.waterPercent?.let { put("waterPercent", it) }; c.physiology.restPercent?.let { put("restPercent", it) }
      c.physiology.pain?.let { put("pain", it) }; c.physiology.infection?.let { put("infection", it) }; c.physiology.thermal?.let { put("thermal", it) }
    })
    val details = if (c.inventoryDetails.isNotEmpty()) c.inventoryDetails else c.inventory.map { raw ->
      ItemDetailProjection(raw.itemId, raw.name, raw.quantity)
    }
    put("inventory", JSONArray().apply { details.forEach { put(item(it)) } })
    put("inventoryCapacity", JSONObject().put("used", c.inventoryCapacityUsed).put("max", c.inventoryCapacityMax))
    put("equipment", JSONObject(c.equipment))
    put("equipmentItems", JSONArray().apply { c.equipmentDetails.forEach { put(item(it)) } })
    put("skills", JSONArray().apply {
      CompanionSkillCatalog.forCharacter(c.id).forEach { skill -> put(JSONObject().apply {
        put("name", skill.name)
        put("kind", skill.kind)
        put("trigger", skill.trigger)
        put("effect", skill.effect)
        skill.note?.let { put("note", it) }
      }) }
    })
    put("statuses", JSONArray().apply { c.statusEffects.forEach { e -> put(JSONObject().put("id", e.id).put("type", e.type).put("persistent", e.persistent)) } })
  }

  private fun stat(x: StatLineProjection) = JSONObject()
    .put("base", x.base).put("passive", x.passive).put("effective", x.effective)

  private fun weapon(x: WeaponGameplayStats) = JSONObject().apply {
    put("DMG", x.dmg); x.ammoDisplay?.let { put("ammo", it) }; x.rpmCapability?.let { put("rpm", it) }; put("fireModes", JSONArray(x.fireModes))
  }

  private fun item(x: ItemDetailProjection) = JSONObject().apply {
    put("id", x.id); put("name", x.name); put("quantity", x.quantity)
    x.type?.let { put("type", it) }; x.slot?.let { put("slot", it) }; x.rarity?.let { put("rarity", it) }
    put("equipped", x.equipped); put("equippedSlots", JSONArray(x.equippedSlots)); put("statItem", x.weapon != null)
    put("consumesInventorySlot", !x.equipped)
    x.classification?.let { put("classification", it) }; x.weapon?.let { put("weapon", weapon(it)) }
    put("abilities", JSONArray().apply { x.abilities.forEach { a -> put(JSONObject().put("name", a.name).put("description", a.description).also { o -> a.importantLimit?.let { o.put("limit", it) } }) } })
    put("restrictions", JSONArray(x.restrictions))
  }
}
