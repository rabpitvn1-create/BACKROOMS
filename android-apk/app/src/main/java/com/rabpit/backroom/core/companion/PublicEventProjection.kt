package com.rabpit.backroom.core.companion

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/** Strict field projection, never an event or observer authorization. */
internal object PublicEventProjection {
  fun project(eventType: String, payload: JSONObject): JSONObject? {
    if (CompanionWaitCapture.canonical(payload).toByteArray(StandardCharsets.UTF_8).size>131072) return null
    return when(eventType) {
      "ACTOR_ACTION_COMPLETED" -> {
        val actor=text(payload,"actor") ?: return null
        if (actor != "cao_minh") return null
        val intent=text(payload,"intent") ?: return null
        val minutes=integer(payload,"minutes")?.takeIf { it in 1..10 } ?: return null
        val location=text(payload,"location") ?: return null
        val scene=text(payload,"scene") ?: return null
        val toStop=text(payload,"toStop") ?: return null
        if (intent !in setOf("TALK","MOVE","SEARCH","INSPECT") || scene != location)
          return null
        val public=JSONObject().put("actor",actor).put("intent",intent).put("minutes",minutes)
          .put("location",location).put("scene",scene).put("toStop",toStop)
        if (payload.has("utterance")) {
          if (intent != "TALK") return null
          val quote=text(payload,"utterance") ?: return null
          public.put("utterance",quote)
        } else if (intent == "TALK") return null
        public
      }
      "WAIT_COMPLETED" -> {
        val actor=text(payload,"actor") ?: return null
        val minutes=integer(payload,"minutes")?.takeIf { it>0 } ?: return null
        val location=text(payload,"location") ?: return null
        val elapsed=integer(payload,"elapsedMinutes")?.takeIf { it>=0 } ?: return null
        JSONObject().put("actor",actor).put("minutes",minutes).put("location",location).put("elapsedMinutes",elapsed)
      }
      "EXIT_STREAK_RESOLVED" -> {
        val completed=payload.opt("completed") as? Boolean ?: return null
        val source=text(payload,"source") ?: return null
        val out=JSONObject().put("completed",completed).put("source",source)
        if(completed) out.put("target",text(payload,"target") ?: return null)
        out
      }
      "WORLD_TRANSITION" -> {
        val source=text(payload,"source") ?: return null
        val target=text(payload,"target") ?: return null
        JSONObject().put("source",source).put("target",target)
      }
      "COMBAT_STARTED" -> {
        val entities=payload.opt("entities") as? JSONArray ?: return null
        // Entity presence/names are not public merely because they are in combat.
        // No native per-entity visibility producer exists yet: deny enumeration.
        if(entities.length()!=0) return null
        JSONObject().put("entities",JSONArray())
      }
      else -> null
    }
  }
  private fun text(json: JSONObject,key:String):String? = (json.opt(key) as? String)?.takeIf {
    it.isNotBlank() && it.toByteArray(StandardCharsets.UTF_8).size<=256 && it.none { c -> c.isISOControl() } &&
      wellFormed(it)
  }
  private fun integer(json:JSONObject,key:String):Long? = when(val v=json.opt(key)) {
    is Int -> v.toLong()
    is Long -> v
    is Short -> v.toLong()
    is Byte -> v.toLong()
    else -> null
  }
  private fun wellFormed(text:String):Boolean {
    var i=0
    while(i<text.length) {
      val c=text[i++]
      if(Character.isHighSurrogate(c)) {
        if(i==text.length || !Character.isLowSurrogate(text[i++])) return false
      } else if(Character.isLowSurrogate(c)) return false
    }
    return true
  }
}
