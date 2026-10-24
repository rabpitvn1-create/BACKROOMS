package com.rabpit.backroom.core.canon

import com.rabpit.backroom.core.CAO_MINH_ID
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * CR2 — turns the exact input triple of `KnowledgeContextEngine.build`
 * (legacy state JSON, raw action, rolls JSON) into a [SceneSnapshot].
 *
 * The design (§14) sketches `resolve(state: GameState, action: PlayerAction,
 * rolls: LockedRolls)`, but neither `PlayerAction` nor `LockedRolls` exists in this
 * tree and the live integration point (MainActivity.GameBridge.submitTurn) only holds
 * legacy JSON — so this resolver keeps the engine's input shape for a 1:1 swap.
 */
fun interface SceneResolver {
  fun resolve(stateJson: String, action: String, rollsJson: String, isMeta: Boolean): SceneSnapshot
}

/**
 * The only implementation this round. Pure `org.json`, no Android Context, no I/O.
 *
 * Presence/level/affordance semantics are ported from
 * `KnowledgeContextEngine.Builder` (`resolvePresence`, `currentLevel`,
 * `addSceneAffordances`, `addStateDrivenRecords`) so CR2 sees the same scene the old
 * engine saw; where the port had to be extended (An Nhiên presence, diacritic name
 * forms, `presence` field) the extension is marked inline.
 */
class LegacyJsonSceneResolver : SceneResolver {

  override fun resolve(stateJson: String, action: String, rollsJson: String, isMeta: Boolean): SceneSnapshot {
    val state = runCatching { JSONObject(stateJson) }.getOrElse { JSONObject() }
    val rollsResult = runCatching { JSONObject(rollsJson) }
    val rolls = rollsResult.getOrElse { JSONObject() }
    val flags = state.optJSONObject("flags")

    val actionText = normalize(action)
    // Narrative text: what the player/scene prose actually says. Structural state
    // (flags) is deliberately NOT part of this — a name inside flags is data, not a
    // mention, and a mention is not a confirmation.
    val narrativeText = normalize(
      listOf(action, state.optString("location", ""), state.optString("title", "")).joinToString(" ")
    )
    // Scene text exactly as the old engine built it (action + location + title + the
    // whole flags object) — used only for affordance intent detection, as before.
    val sceneText = normalize(
      listOf(
        action,
        state.optString("location", ""),
        state.optString("title", ""),
        flags?.toString().orEmpty()
      ).joinToString(" ")
    )

    val turn = state.optInt("turn", 1).coerceAtLeast(1)
    val actors = resolvePresence(state)
    val encounters = resolveEncounters(state, flags, rolls, narrativeText)
    val continuity = flags?.optJSONObject("storyContinuity")

    return SceneSnapshot(
      turnId = "TURN_$turn",
      gameStateRevision = turn,
      levelId = state.optJSONObject("level")?.optString("canonScope")?.takeIf { it.isNotBlank() }
        ?: "LEVEL.%02d".format(Locale.ROOT, currentLevel(state)),
      povActorId = CAO_MINH_ID,
      actorsPresent = actors,
      entityEncounters = encounters,
      mentionedUnconfirmed = encounters
        .filter { it.status != EncounterStatus.CONFIRMED_PRESENT }
        .map { it.entityId }
        .toSet(),
      actionIntents = resolveActionIntents(sceneText),
      actionTextNormalized = actionText,
      objectsInFocus = resolveObjectsInFocus(state, narrativeText),
      knownFacts = resolveKnownFacts(continuity),
      knownByByFact = resolveKnownBy(continuity),
      storyThreadsActive = resolveActiveThreads(continuity),
      rollsLocked = rollsResult.isSuccess && rolls.has("turn"),
      isMeta = isMeta,
      locationNodeId = state.optJSONObject("levelTravel")?.optString("nodeId")?.takeIf { it.isNotBlank() }
    )
  }

  // ---------------------------------------------------------------- presence

  /**
   * Port of `Builder.resolvePresence()` (party[] + partyDetails.members[]), extended:
   *  - also recognises "an-nhien" (the old engine was blind to her) and the diacritic
   *    name forms the facade alias map uses ("lục trầm", "an nhiên");
   *  - honours an explicit `presence` field on a member object when present:
   *    separated/missing/dead/absent members are not present.
   * Cao Minh is always present (he is the POV actor).
   */
  private fun resolvePresence(state: JSONObject): Set<String> {
    val present = linkedSetOf(CAO_MINH_ID)
    fun visit(idRaw: String, nameRaw: String, presenceRaw: String?) {
      if (presenceRaw != null && normalize(presenceRaw) in INACTIVE_PRESENCE) return
      val haystack = normalize("$idRaw $nameRaw")
      ACTOR_PATTERNS.forEach { (actorId, needles) ->
        if (needles.any { haystack.contains(it) }) present += actorId
      }
    }
    val party = state.optJSONArray("party")
    if (party != null) {
      for (i in 0 until party.length()) {
        when (val value = party.opt(i)) {
          is JSONObject -> visit(
            value.optString("id", ""),
            value.optString("name", ""),
            if (value.has("presence")) value.optString("presence") else null
          )
          else -> visit(value?.toString().orEmpty(), "", null)
        }
      }
    }
    val members = state.optJSONObject("partyDetails")?.optJSONArray("members")
    if (members != null) {
      for (i in 0 until members.length()) {
        val member = members.optJSONObject(i) ?: continue
        visit(
          member.optString("id", ""),
          member.optString("name", ""),
          if (member.has("presence")) member.optString("presence") else null
        )
      }
    }
    return present
  }

  // ---------------------------------------------------------------- level

  /** Port of `Builder.currentLevel()`: level.number, else flags.currentLevel.number. */
  private fun currentLevel(state: JSONObject): Int {
    state.optJSONObject("level")?.let { return it.optInt("number", 0).coerceIn(0, 999) }
    state.optJSONObject("flags")?.optJSONObject("currentLevel")?.let { return it.optInt("number", 0).coerceIn(0, 999) }
    return 0
  }

  // ---------------------------------------------------------------- entities

  /**
   * Confirmation sources, in order of strength (ARCHITECTURE §6.1):
   *  1. the entity id is registered in `flags.entityRegistry` — the same registry
   *     StoryContinuityReducer turns into `DISC.ENTITY.*` discoveries → CONFIRMED.
   *  2. a generic encounter signal exists (`flags.entitiesConfirmedLocal > 0` or the
   *     locked `rolls.entityEncounter.success`) AND exactly one entity is mentioned in
   *     the narrative text — the locked roll confirms this turn's encounter and the
   *     text names the only candidate → CONFIRMED for that one entity only.
   * Anything else named in the action/location/title is MENTIONED; anything named only
   * in the recent dialogue buffer is RUMORED. A mention alone never confirms.
   */
  private fun resolveEncounters(
    state: JSONObject,
    flags: JSONObject?,
    rolls: JSONObject,
    narrativeText: String
  ): List<EntityEncounter> {
    val confirmed = linkedSetOf<String>()
    registryEntityIds(flags?.opt("entityRegistry")).forEach { confirmed += it }

    val mentioned = linkedSetOf<String>()
    entityTerms().forEach { term ->
      if (narrativeText.contains(term)) mentioned += entityIdForTerm(term)
    }

    val genericConfirmation = (flags?.optInt("entitiesConfirmedLocal", 0) ?: 0) > 0 ||
      (rolls.optJSONObject("entityEncounter")?.optBoolean("success", false) ?: false)
    if (genericConfirmation && mentioned.size == 1) confirmed += mentioned.single()

    val rumored = linkedSetOf<String>()
    val logText = recentLogText(state)
    if (logText.isNotEmpty()) {
      entityTerms().forEach { term ->
        val id = entityIdForTerm(term)
        if (id !in mentioned && logText.contains(term)) rumored += id
      }
    }

    val out = mutableListOf<EntityEncounter>()
    confirmed.forEach { out += EntityEncounter(it, EncounterStatus.CONFIRMED_PRESENT) }
    mentioned.filter { it !in confirmed }.forEach { out += EntityEncounter(it, EncounterStatus.MENTIONED) }
    rumored.filter { it !in confirmed && it !in mentioned }
      .forEach { out += EntityEncounter(it, EncounterStatus.RUMORED) }
    return out
  }

  /** Entity registry as kept in flags: a JSONObject keyed by id, or a JSONArray of ids/objects. */
  private fun registryEntityIds(registry: Any?): Set<String> {
    val out = linkedSetOf<String>()
    when (registry) {
      is JSONObject -> registry.keys().forEach { key -> entityIdForRegistryKey(key)?.let { out += it } }
      is JSONArray -> for (i in 0 until registry.length()) {
        val item = registry.opt(i)
        val raw = if (item is JSONObject) item.optString("id", item.optString("name", "")) else item?.toString().orEmpty()
        if (raw.isNotBlank()) entityIdForRegistryKey(raw)?.let { out += it }
      }
    }
    return out
  }

  private fun entityIdForRegistryKey(raw: String): String? {
    val key = normalize(raw)
    if (key.isEmpty()) return null
    if (key.startsWith("entity.")) {
      return "ENTITY." + key.removePrefix("entity.").uppercase(Locale.ROOT)
        .replace(Regex("[^A-Z0-9]+"), "_").trim('_')
    }
    entityTerms().firstOrNull { term -> key == term || key.contains(term) }?.let { return entityIdForTerm(it) }
    return "ENTITY." + key.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]+"), "_").trim('_')
  }

  /**
   * Entity terms: the entity half of the old engine's `tokenizeTags()` list, ported
   * verbatim. This adapter has no store access (it must stay a pure function of the
   * legacy JSON triple, exactly like the old Builder), so the scene vocabulary lives
   * here as a port; the store side derives its terms from record data instead
   * ([CanonDirectTerms.buildTermIndex]). The three resource terms of the legacy list
   * are items, not entities — they surface via objectsInFocus (see ITEM_TERMS).
   */
  private fun entityTerms(): List<String> = ENTITY_TERMS

  /**
   * Term -> entity record id. knowledge_db entity ids follow `ENTITY.<UPPER_SNAKE>` of
   * the tag, with two irregular ids that cannot be derived mechanically
   * (verified against knowledge_db.json: ENTITY.JEFF, ENTITY.BEAST_LEVEL_5).
   * Terms without a record (clump, duller, faceling, paintings in the current seed)
   * still yield their would-be id: the scene snapshot describes the scene, and the
   * store lookup downstream simply finds no record for them.
   */
  private fun entityIdForTerm(term: String): String {
    IRREGULAR_ENTITY_IDS[term]?.let { return it }
    return "ENTITY." + term.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]+"), "_").trim('_')
  }

  /** Last few log entries' text (the old engine's recency buffer size), for RUMORED. */
  private fun recentLogText(state: JSONObject): String {
    val log = state.optJSONArray("log") ?: return ""
    val parts = mutableListOf<String>()
    val start = (log.length() - RECENT_LOG_ENTRIES).coerceAtLeast(0)
    for (i in start until log.length()) {
      val entry = log.optJSONObject(i) ?: continue
      parts += entry.optString("text", "")
    }
    return normalize(parts.joinToString(" "))
  }

  // ---------------------------------------------------------------- intents

  /**
   * Port of the keyword tables in `Builder.addSceneAffordances()`, but returning the
   * affordance KEYS as data. Loading records for these keys is CanonResolver's job
   * (via `CanonStore.queryAffordances`); an intent only says the scene makes an
   * ability eligible (CAN_DO) — it never decides that an actor uses it (WILL_DO).
   */
  private fun resolveActionIntents(sceneText: String): Set<String> {
    val intents = linkedSetOf<String>()
    if (hasAny(sceneText, "dấu vết", "trace", "vết chân", "đường đi", "route", "góc chết", "vật che", "phục kích", "ambush", "địa hình", "target", "mục tiêu thật")) {
      intents += "trace_analysis"
    }
    if (hasAny(sceneText, "đe dọa", "threat", "tấn công", "attack", "giao chiến", "combat", "entity", "hound", "smiler", "wretch", "skin-stealer", "jeff")) {
      intents += "direct_threat"
    }
    if (hasAny(sceneText, "bị thương", "injury", "vết thương", "sơ cứu", "medical")) intents += "field_medical"
    if (hasAny(sceneText, "thức ăn", "nấu", "food", "cooking")) intents += "field_food"
    return intents
  }

  // ---------------------------------------------------------------- objects

  /**
   * Item record ids in focus: the legacy resource terms found in the narrative text,
   * in the POV inventory item names, or in the player weapon/armor fields.
   */
  private fun resolveObjectsInFocus(state: JSONObject, narrativeText: String): Set<String> {
    val out = linkedSetOf<String>()
    fun scan(text: String) {
      val normalized = normalize(text)
      ITEM_TERMS.forEach { (term, id) -> if (normalized.contains(term)) out += id }
    }
    scan(narrativeText)
    val inventory = state.optJSONArray("inventory")
    if (inventory != null) {
      for (i in 0 until inventory.length()) {
        val item = inventory.opt(i)
        scan(if (item is JSONObject) item.optString("name", "") else item?.toString().orEmpty())
      }
    }
    state.optJSONObject("player")?.let { player ->
      scan(player.optString("weapon", ""))
      scan(player.optString("armor", ""))
    }
    return out
  }

  // ---------------------------------------------------------------- continuity

  /** `flags.storyContinuity.discoveries[].id` — facts the reducer proved from state. */
  private fun resolveKnownFacts(continuity: JSONObject?): Set<String> {
    val out = linkedSetOf<String>()
    val discoveries = continuity?.optJSONArray("discoveries") ?: return out
    for (i in 0 until discoveries.length()) {
      val id = discoveries.optJSONObject(i)?.optString("id", "").orEmpty()
      if (id.isNotEmpty()) out += id
    }
    return out
  }

  /** `flags.storyContinuity.knowledge[]`: factId -> knownBy[] (who provably knows it). */
  private fun resolveKnownBy(continuity: JSONObject?): Map<String, Set<String>> {
    val out = linkedMapOf<String, Set<String>>()
    val knowledge = continuity?.optJSONArray("knowledge") ?: return out
    for (i in 0 until knowledge.length()) {
      val entry = knowledge.optJSONObject(i) ?: continue
      val factId = entry.optString("factId", "")
      if (factId.isEmpty()) continue
      val knowers = linkedSetOf<String>()
      val knownBy = entry.optJSONArray("knownBy")
      if (knownBy != null) {
        for (j in 0 until knownBy.length()) {
          val who = knownBy.optString(j, "").trim()
          if (who.isNotEmpty()) knowers += who
        }
      }
      if (knowers.isNotEmpty()) out[factId] = knowers
    }
    return out
  }

  /** `flags.storyContinuity.unresolvedThreads[]` with status "open" (or unset). */
  private fun resolveActiveThreads(continuity: JSONObject?): Set<String> {
    val out = linkedSetOf<String>()
    val threads = continuity?.optJSONArray("unresolvedThreads") ?: return out
    for (i in 0 until threads.length()) {
      val thread = threads.optJSONObject(i) ?: continue
      val status = thread.optString("status", "open")
      if (status == "open" || status.isEmpty()) {
        val id = thread.optString("id", "")
        if (id.isNotEmpty()) out += id
      }
    }
    return out
  }

  // ---------------------------------------------------------------- helpers
  // normalize/hasAny are byte-for-byte the old engine's idiom (lowercase Locale.ROOT,
  // en/em dash -> '-', whitespace collapsed). Do not "improve" them here: alias and
  // tag matching across the old engine and CR2 must agree on the same normalization.

  private fun hasAny(text: String, vararg needles: String): Boolean = needles.any { text.contains(normalize(it)) }

  private fun normalize(text: String): String = text.lowercase(Locale.ROOT)
    .replace('–', '-')
    .replace('—', '-')
    .replace(Regex("\\s+"), " ")
    .trim()

  private companion object {
    const val RECENT_LOG_ENTRIES = 4

    val INACTIVE_PRESENCE = setOf("separated", "missing", "dead", "absent")

    /** Normalized id/name fragments per core actor id. */
    val ACTOR_PATTERNS: List<Pair<String, List<String>>> = listOf(
      "luc-tram" to listOf("luc-tram", "luc tram", "lục trầm"),
      "syvial" to listOf("syvial"),
      "an-nhien" to listOf("an-nhien", "an nhien", "an nhiên"),
      CAO_MINH_ID to listOf("cao-minh", "cao minh")
    )

    /** Entity half of `KnowledgeContextEngine.tokenizeTags()`, ported verbatim. */
    val ENTITY_TERMS: List<String> = listOf(
      "hound", "clump", "duller", "deathmoth", "faceling", "false puddle", "paintings",
      "smiler", "skin-stealer", "skin stealer", "predatory window", "biological pipeline",
      "wretch", "cable mimic", "beast of level 5", "jeff the killer"
    )

    /** Legacy resource terms of `tokenizeTags()` that are items, with their record ids. */
    val ITEM_TERMS: Map<String, String> = linkedMapOf(
      "almond water" to "ITEM.ALMOND_WATER",
      "greek fire" to "ITEM.GREEK_FIRE",
      "liquid pain" to "ITEM.LIQUID_PAIN"
    )

    /** Entity ids in knowledge_db that the mechanical UPPER_SNAKE derivation misses. */
    val IRREGULAR_ENTITY_IDS: Map<String, String> = mapOf(
      "jeff" to "ENTITY.JEFF",
      "jeff the killer" to "ENTITY.JEFF",
      "beast of level 5" to "ENTITY.BEAST_LEVEL_5",
      "the beast" to "ENTITY.BEAST_LEVEL_5"
    )
  }
}
