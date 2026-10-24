package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** A roll is a proposal until the validated turn commits. Never trusts GM encounter flags. */
object NamedFollowerRuntime {
  data class Plan(val turnId: String, val action: String, val ticket: Int,
    val eligible: Boolean, val entityEligible: Boolean, val followerId: String?,
    val availableIds: Set<String>)
  data class Result(val state: GameState, val notices: List<String> = emptyList())

  fun plan(state: GameState, turnId: String, action: String, ticket: Int,
    meta: Boolean, survivorAllowed: Boolean, entityAllowed: Boolean): Plan {
    require(ticket in 0..9999)
    val dialogue = Regex("(?:^| )(?:moi|gia nhap|dong hanh|tu choi|theo toi|theo ta)(?: |$)")
      .containsMatchIn(normalize(action))
    val eligible = !meta && !isMetaAction(action) && !dialogue && physical(action) &&
      CombatRuntime.active(state) == null && state.turn.pending == null
    val available = VietnameseFollowersCanon.ids.filter { id ->
      state.characters[id]?.let { it.metadata["encountered"] != "true" &&
        it.presence != CharacterPresence.DEAD && it.vitalState.currentHp > 0 &&
        it.vitalState.condition != CharacterCondition.DEAD } == true
    }.toSet()
    val id = when (ticket) {
      in 4500..4999 -> HUA_TIEU_LAN_ID
      in 5000..5499 -> NGUYEN_QUOC_NAM_ID
      else -> null
    }?.takeIf { eligible && survivorAllowed && it in available }
    return Plan(turnId, action, ticket, eligible && survivorAllowed, eligible && entityAllowed, id, available)
  }

  fun rolls(plan: Plan): JSONObject {
    fun roll(key: String, eligible: Boolean, low: Int, high: Int) = JSONObject()
      .put("label", key).put("dice", "d10000").put("max", 10000)
      .put("eligible", eligible).put("roll", if (eligible) plan.ticket else JSONObject.NULL)
      .put("ticketMin", low).put("ticketMax", high).put("chancePercent", (high-low+1)/100.0)
      .put("success", eligible && plan.ticket in low..high)
    return JSONObject()
      .put("entityEncounter", roll("entityEncounter", plan.entityEligible, 0, 3499))
      .put("huaTieuLanEncounter", roll("huaTieuLanEncounter", plan.eligible && HUA_TIEU_LAN_ID in plan.availableIds, 4500, 4999))
      .put("nguyenQuocNamEncounter", roll("nguyenQuocNamEncounter", plan.eligible && NGUYEN_QUOC_NAM_ID in plan.availableIds, 5000, 5499))
  }

  /** Called only with a server-held plan after candidate validation, never client roll JSON. */
  fun apply(state: GameState, plan: Plan, proposedParty: JSONArray, externalPartySize: Int): Result {
    require(plan.turnId !in state.turn.completedTurnIds) { "turn_already_completed" }
    var next = state
    val notices = mutableListOf<String>()
    val encountered = plan.followerId
    if (encountered != null) {
      val c = next.characters.getValue(encountered)
      require(c.metadata["encountered"] != "true") { "follower_already_encountered" }
      next = next.copy(characters = next.characters + (encountered to c.copy(
        presence = CharacterPresence.ACTIVE,
        metadata = c.metadata + mapOf("encountered" to "true", "encounterTurn" to plan.turnId))))
    }
    val newlyJoined = mutableSetOf<String>()
    VietnameseFollowersCanon.ids.forEach { id ->
      val c = next.characters.getValue(id)
      val presentBefore = state.characters[id]?.presence == CharacterPresence.ACTIVE &&
        state.characters[id]?.metadata?.get("encountered") == "true"
      // Inviting an unencountered name cannot create an encounter or grant consent.
      if (presentBefore && id !in next.party.memberIds && invitation(plan.action, id, state)) {
        val proposal = (0 until proposedParty.length()).mapNotNull(proposedParty::optJSONObject)
          .firstOrNull { managedId(it) == id }
        if (proposal?.optBoolean("joinConfirmed", false) == true && c.vitalState.currentHp > 0 &&
          c.vitalState.condition != CharacterCondition.DEAD) {
          val others = next.party.memberIds.count { it !in VietnameseFollowersCanon.ids }
          val named = next.party.memberIds.count { it in VietnameseFollowersCanon.ids }
          if (maxOf(others, 1 + externalPartySize) + named >= next.party.maxMembers) {
            notices += "Party đã đủ bốn thành viên; ${c.name} chưa gia nhập."
          } else {
            val result = PartyEngine.execute(next, PartyCommand(
              "${plan.turnId}:NAMED_JOIN:$id", plan.turnId, CAO_MINH_ID, id,
              CommandSource.SYSTEM, PartyCommand.Operation.ADD,
              consentConfirmed = true, targetPresent = true))
            require(result.validation.valid) { result.validation.reason.orEmpty() }
            next = result.state
            newlyJoined += id
          }
        }
      }
      val leaves = id != encountered && id !in next.party.memberIds &&
        (moving(plan.action) || refusal(plan.action, id, state))
      if (leaves && next.characters[id]?.presence == CharacterPresence.ACTIVE && id !in newlyJoined) {
        next = next.copy(characters = next.characters + (id to next.characters.getValue(id)
          .copy(presence = CharacterPresence.SEPARATED)))
      }
    }
    return Result(next, notices)
  }

  /** Merge this subsystem's projection without erasing unrelated narrative inventory/companions. */
  fun project(legacy: JSONObject, state: GameState): JSONObject {
    val out = JSONObject(legacy.toString())
    val party = JSONArray()
    val original = legacy.optJSONArray("party") ?: JSONArray()
    for (i in 0 until original.length()) {
      val m = original.optJSONObject(i) ?: continue
      if (!managed(m) && !protectedRelative(m)) party.put(m)
    }
    val details = legacy.optJSONObject("partyDetails")?.let { JSONObject(it.toString()) }
      ?: CharacterDetailJson.encodeParty(CharacterDetailProjector.projectParty(state))
    val members = JSONArray()
    val oldMembers = details.optJSONArray("members") ?: JSONArray()
    for (i in 0 until oldMembers.length()) {
      val m = oldMembers.optJSONObject(i) ?: continue
      if (!managed(m) && !protectedRelative(m)) members.put(m)
    }
    val projectedIds = (0 until members.length()).mapNotNull(members::optJSONObject).map { it.optString("id") }.toSet()
    for (i in 0 until party.length()) {
      val member = party.optJSONObject(i) ?: continue
      if (member.optString("id") !in projectedIds) members.put(JSONObject(member.toString()))
    }
    val scene = JSONArray()
    val flags = out.optJSONObject("flags") ?: JSONObject()
    VietnameseFollowersCanon.ids.forEach { id ->
      val c = state.characters.getValue(id)
      val member = JSONObject().put("id", id).put("name", c.name).put("presence", c.presence.name)
        .put("avatar", c.avatarRef).put("present", c.presence == CharacterPresence.ACTIVE)
      if (id in state.party.memberIds) {
        party.put(member)
        CharacterDetailProjector.projectCharacter(state, id)?.let {
          members.put(CharacterDetailJson.encodeCharacter(it))
        }
      } else if (c.presence == CharacterPresence.ACTIVE && c.metadata["encountered"] == "true") scene.put(member)
      if (c.metadata["encountered"] == "true") {
        flags.put(id, JSONObject().put("encountered", true)
          .put("present", c.presence == CharacterPresence.ACTIVE).put("presence", c.presence.name)
          .put("inParty", id in state.party.memberIds))
      } else flags.remove(id)
    }
    details.put("members", members)
    return out.put("party", party).put("partyDetails", details).put("followerEncounters", scene).put("flags", flags)
  }

  @JvmStatic fun managed(member: JSONObject): Boolean = managedId(member) != null
  fun managedId(member: JSONObject): String? {
    val raw = normalize(member.optString("id") + " " + member.optString("name"))
    return VietnameseFollowersCanon.ids.firstOrNull { id -> aliases(id).any { raw.contains(it) } }
  }

  @JvmStatic fun protectedRelative(member: JSONObject): Boolean {
    val raw = normalize(member.optString("id") + " " + member.optString("name"))
    return listOf("nguyen hong lan", "nguyen thuy duong", "hua bon hoa", "anh ty").any(raw::contains)
  }

  fun initialLegacy(legacy: JSONObject): JSONObject {
    val out = JSONObject(legacy.toString())
    val party = legacy.optJSONArray("party") ?: JSONArray()
    out.put("party", JSONArray().apply { for (i in 0 until party.length()) {
      val member = party.optJSONObject(i) ?: continue
      if (!managed(member) && !protectedRelative(member)) put(member)
    } })
    out.remove("followerEncounters")
    return out
  }

  private fun invitation(action: String, id: String, state: GameState): Boolean {
    val a = normalize(action)
    return !Regex("(?:^| )(?:khong|dung|chua|tu choi)(?: |$)").containsMatchIn(a) &&
      Regex("(?:moi|gia nhap|vao (?:party|doi|nhom)|di cung|dong hanh|theo toi|theo ta)").containsMatchIn(a) &&
      addressed(a, id, state)
  }
  private fun refusal(action: String, id: String, state: GameState): Boolean =
    Regex("(?:tu choi|khong di cung|khong dong y|dung theo|roi di)").containsMatchIn(normalize(action)) &&
      addressed(normalize(action), id, state)
  private fun addressed(a: String, id: String, state: GameState): Boolean {
    val explicit = VietnameseFollowersCanon.ids.filter { aliases(it).any(a::contains) }
    if (explicit.isNotEmpty()) return id in explicit
    return VietnameseFollowersCanon.ids.filter { state.characters[it]?.presence == CharacterPresence.ACTIVE &&
      it !in state.party.memberIds }.let { it.size == 1 && it[0] == id }
  }
  private fun aliases(id: String): List<String> = when(id) {
    NGUYEN_QUOC_NAM_ID -> listOf("nguyen quoc nam", "quoc nam")
    else -> listOf("hua tieu lan", "tieu lan")
  }
  @JvmStatic fun isMetaAction(a: String) = Regex("(?:^| )(?:(?:xem|show|kiem tra) (?:trang thai|state|inventory|party|tui do|thuoc tinh|nhan vat)|status|save|load|luu game|tai game)(?: |$)")
    .containsMatchIn(normalize(a))
  private fun physical(a: String) = Regex("(?:^| )(?:di|buoc|chay|leo|mo|dong|cham|luc|tim|kiem tra|khao sat|kham pha|quet|scan|explore|search|day|keo|tien|lui|bo|nhay|dao|thao|vuot)(?: |$)")
    .containsMatchIn(normalize(a))
  private fun moving(a: String) = Regex("(?:^| )(?:di tiep|buoc tiep|roi khoi|chay khoi|sang (?:phong|khu|level)|tien len|tien vao|leo|bo qua)(?: |$)")
    .containsMatchIn(normalize(a))
  private fun normalize(raw: String): String = Normalizer.normalize(raw.lowercase(Locale.ROOT).replace('đ','d'), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
}
