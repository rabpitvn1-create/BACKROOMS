package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/** JSON projection intended for the local WebView UI. Internal metadata is deliberately omitted. */
object CharacterDetailJson {
  fun encodeParty(projection: PartyDetailProjection): JSONObject = JSONObject().apply {
    put("leaderId", projection.leaderId)
    put("maxMembers", projection.maxMembers)
    put("elapsedSubjectiveMinutes", projection.elapsedSubjectiveMinutes)
    put("members", JSONArray().apply {
      projection.members.forEach { put(encodeCharacter(it)) }
    })
  }

  fun encodeCharacter(character: CharacterDetailProjection): JSONObject = JSONObject().apply {
    put("id", character.id)
    put("name", character.name)
    character.avatarRef?.let { put("avatar", it) }
    put("presence", character.presence.name)
    put("isLeader", character.isLeader)
    character.healthState?.let { put("healthState", it) }
    put("currentHp", character.currentHp)
    put("maxHp", character.maxHp)
    put("injuries", JSONArray(character.injuries))
    put("physiology", JSONObject().apply {
      put("hunger", character.physiology.hunger.name)
      put("thirst", character.physiology.thirst.name)
      put("sleepDeprivation", character.physiology.sleepDeprivation.name)
      character.physiology.foodPercent?.let { put("foodPercent", it) }
      character.physiology.waterPercent?.let { put("waterPercent", it) }
      character.physiology.restPercent?.let { put("restPercent", it) }
      character.physiology.pain?.let { put("pain", it) }
      character.physiology.infection?.let { put("infection", it) }
      character.physiology.thermal?.let { put("thermal", it) }
    })
    put("inventory", JSONArray().apply {
      character.inventory.forEach { stack -> put(JSONObject().apply {
        put("id", stack.itemId)
        put("name", stack.name)
        put("quantity", stack.quantity)
        stack.condition?.let { put("state", it) }
        put("contentState", stack.contentState.name)
      }) }
    })
    put("equipment", JSONObject(character.equipment))
    put("statuses", JSONArray().apply {
      character.statusEffects.forEach { effect -> put(JSONObject().apply {
        put("id", effect.id)
        put("type", effect.type)
        put("persistent", effect.persistent)
      }) }
    })
  }
}
