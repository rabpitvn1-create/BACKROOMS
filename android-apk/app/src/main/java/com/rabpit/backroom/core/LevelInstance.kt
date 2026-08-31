package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

enum class EvidenceSource { ENVIRONMENT, SEARCH, SURVIVOR, ANOMALY }

data class ZoneState(
  val id: String,
  val name: String,
  val connections: Set<String> = emptySet(),
  val tags: Set<String> = emptySet(),
  val properties: Map<String, String> = emptyMap()
)

data class WorldMutation(
  val id: String,
  val revision: Int,
  val kind: String,
  val targetId: String,
  val value: String
)

data class EvidenceState(
  val id: String,
  val supports: Set<String>,
  val sources: Set<EvidenceSource>,
  val zoneId: String? = null,
  val discoverConditions: Set<String> = emptySet(),
  val discovered: Boolean = false,
  val discoveredAtRevision: Int? = null
)

data class EscapeBlueprintState(
  val solutionId: String,
  val requiredFacts: Set<String>,
  val requiredActions: List<String>,
  val locked: Boolean = true
)

data class LevelInstanceState(
  val runSeed: String,
  val levelId: String,
  val generationId: String,
  val currentZoneId: String,
  val zones: Map<String, ZoneState>,
  val landmarks: Map<String, String> = emptyMap(),
  val environment: Map<String, String> = emptyMap(),
  val escapeBlueprint: EscapeBlueprintState,
  val evidence: Map<String, EvidenceState>,
  val npcKnowledge: Map<String, Set<String>> = emptyMap(),
  val discoveredFacts: Set<String> = emptySet(),
  val completedActions: List<String> = emptyList(),
  val mutations: List<WorldMutation> = emptyList(),
  val revision: Int = 1,
  val completed: Boolean = false
)

object LevelInstanceJson {
  fun encode(value: LevelInstanceState): JSONObject = JSONObject().apply {
    put("runSeed", value.runSeed)
    put("levelId", value.levelId)
    put("generationId", value.generationId)
    put("currentZoneId", value.currentZoneId)
    put("zones", JSONObject().apply { value.zones.forEach { (id, zone) -> put(id, zone(zone)) } })
    put("landmarks", stringMap(value.landmarks))
    put("environment", stringMap(value.environment))
    put("escapeBlueprint", blueprint(value.escapeBlueprint))
    put("evidence", JSONObject().apply { value.evidence.forEach { (id, evidence) -> put(id, evidence(evidence)) } })
    put("npcKnowledge", JSONObject().apply { value.npcKnowledge.forEach { (id, facts) -> put(id, JSONArray(facts.sorted())) } })
    put("discoveredFacts", JSONArray(value.discoveredFacts.sorted()))
    put("completedActions", JSONArray(value.completedActions))
    put("mutations", JSONArray().apply { value.mutations.forEach { put(mutation(it)) } })
    put("revision", value.revision)
    put("completed", value.completed)
  }

  fun decode(json: JSONObject): LevelInstanceState {
    val zonesJson = json.optJSONObject("zones") ?: JSONObject()
    val zones = linkedMapOf<String, ZoneState>()
    zonesJson.keys().forEach { id -> zonesJson.optJSONObject(id)?.let { zones[id] = decodeZone(it) } }

    val evidenceJson = json.optJSONObject("evidence") ?: JSONObject()
    val evidence = linkedMapOf<String, EvidenceState>()
    evidenceJson.keys().forEach { id -> evidenceJson.optJSONObject(id)?.let { evidence[id] = decodeEvidence(it) } }

    val knowledge = linkedMapOf<String, Set<String>>()
    json.optJSONObject("npcKnowledge")?.let { root ->
      root.keys().forEach { id -> knowledge[id] = root.optJSONArray(id).strings().toSet() }
    }

    return LevelInstanceState(
      runSeed = json.optString("runSeed"),
      levelId = json.optString("levelId"),
      generationId = json.optString("generationId"),
      currentZoneId = json.optString("currentZoneId"),
      zones = zones,
      landmarks = json.optJSONObject("landmarks").stringsMap(),
      environment = json.optJSONObject("environment").stringsMap(),
      escapeBlueprint = decodeBlueprint(json.optJSONObject("escapeBlueprint") ?: JSONObject()),
      evidence = evidence,
      npcKnowledge = knowledge,
      discoveredFacts = json.optJSONArray("discoveredFacts").strings().toSet(),
      completedActions = json.optJSONArray("completedActions").strings(),
      mutations = json.optJSONArray("mutations").objects().map(::decodeMutation),
      revision = json.optInt("revision", 1).coerceAtLeast(1),
      completed = json.optBoolean("completed", false)
    )
  }

  private fun zone(value: ZoneState) = JSONObject().apply {
    put("id", value.id)
    put("name", value.name)
    put("connections", JSONArray(value.connections.sorted()))
    put("tags", JSONArray(value.tags.sorted()))
    put("properties", stringMap(value.properties))
  }

  private fun decodeZone(json: JSONObject) = ZoneState(
    id = json.optString("id"),
    name = json.optString("name"),
    connections = json.optJSONArray("connections").strings().toSet(),
    tags = json.optJSONArray("tags").strings().toSet(),
    properties = json.optJSONObject("properties").stringsMap()
  )

  private fun blueprint(value: EscapeBlueprintState) = JSONObject().apply {
    put("solutionId", value.solutionId)
    put("requiredFacts", JSONArray(value.requiredFacts.sorted()))
    put("requiredActions", JSONArray(value.requiredActions))
    put("locked", value.locked)
  }

  private fun decodeBlueprint(json: JSONObject) = EscapeBlueprintState(
    solutionId = json.optString("solutionId"),
    requiredFacts = json.optJSONArray("requiredFacts").strings().toSet(),
    requiredActions = json.optJSONArray("requiredActions").strings(),
    locked = json.optBoolean("locked", true)
  )

  private fun evidence(value: EvidenceState) = JSONObject().apply {
    put("id", value.id)
    put("supports", JSONArray(value.supports.sorted()))
    put("sources", JSONArray(value.sources.map { it.name }.sorted()))
    put("zoneId", value.zoneId ?: JSONObject.NULL)
    put("discoverConditions", JSONArray(value.discoverConditions.sorted()))
    put("discovered", value.discovered)
    put("discoveredAtRevision", value.discoveredAtRevision ?: JSONObject.NULL)
  }

  private fun decodeEvidence(json: JSONObject) = EvidenceState(
    id = json.optString("id"),
    supports = json.optJSONArray("supports").strings().toSet(),
    sources = json.optJSONArray("sources").strings().mapNotNull { raw -> EvidenceSource.values().firstOrNull { it.name == raw } }.toSet(),
    zoneId = json.optString("zoneId").takeIf { it.isNotBlank() },
    discoverConditions = json.optJSONArray("discoverConditions").strings().toSet(),
    discovered = json.optBoolean("discovered", false),
    discoveredAtRevision = if (json.has("discoveredAtRevision") && !json.isNull("discoveredAtRevision")) json.optInt("discoveredAtRevision") else null
  )

  private fun mutation(value: WorldMutation) = JSONObject().apply {
    put("id", value.id)
    put("revision", value.revision)
    put("kind", value.kind)
    put("targetId", value.targetId)
    put("value", value.value)
  }

  private fun decodeMutation(json: JSONObject) = WorldMutation(
    id = json.optString("id"),
    revision = json.optInt("revision", 1).coerceAtLeast(1),
    kind = json.optString("kind"),
    targetId = json.optString("targetId"),
    value = json.optString("value")
  )

  private fun stringMap(values: Map<String, String>) = JSONObject().apply { values.forEach { (key, value) -> put(key, value) } }
  private fun JSONObject?.stringsMap(): Map<String, String> {
    if (this == null) return emptyMap()
    val result = linkedMapOf<String, String>()
    keys().forEach { key -> result[key] = optString(key) }
    return result
  }
  private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
  private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONObject)
}

data class BlueprintValidation(val valid: Boolean, val errors: List<String>)

object BlueprintValidator {
  fun validate(instance: LevelInstanceState): BlueprintValidation {
    val errors = mutableListOf<String>()
    if (instance.levelId.isBlank()) errors += "level_id_missing"
    if (instance.currentZoneId !in instance.zones) errors += "current_zone_missing"
    if (!instance.escapeBlueprint.locked) errors += "escape_blueprint_must_be_locked"
    if (instance.escapeBlueprint.requiredFacts.isEmpty()) errors += "required_facts_missing"
    if (instance.escapeBlueprint.requiredActions.isEmpty()) errors += "required_actions_missing"

    instance.zones.values.forEach { zone ->
      zone.connections.filterNot(instance.zones::containsKey).forEach { errors += "unknown_connection:${zone.id}:$it" }
    }

    instance.escapeBlueprint.requiredFacts.forEach { fact ->
      val supporting = instance.evidence.values.filter { fact in it.supports }
      if (supporting.size < 2) errors += "insufficient_evidence:$fact"
      if (supporting.flatMap { it.sources }.toSet().size < 2) errors += "insufficient_source_diversity:$fact"
    }

    val escapeZones = instance.zones.values.filter { "escape" in it.tags }.map { it.id }.toSet()
    if (escapeZones.isEmpty()) errors += "escape_zone_missing"
    else if (instance.currentZoneId in instance.zones && !reachable(instance, escapeZones)) errors += "escape_zone_unreachable"

    return BlueprintValidation(errors.isEmpty(), errors.distinct())
  }

  private fun reachable(instance: LevelInstanceState, targets: Set<String>): Boolean {
    val seen = mutableSetOf<String>()
    val queue = ArrayDeque<String>()
    queue.add(instance.currentZoneId)
    while (queue.isNotEmpty()) {
      val id = queue.removeFirst()
      if (!seen.add(id)) continue
      if (id in targets) return true
      instance.zones[id]?.connections.orEmpty().filterNot(seen::contains).forEach(queue::addLast)
    }
    return false
  }
}

object LevelOnePrototype {
  const val LEVEL_ID = "1"
  private const val FACT_POWER_OFF = "POWER_OFF_REQUIRED"
  private const val FACT_DOOR_LOOP = "DOOR_14_LOOP"
  private const val FACT_REVERSE_HUM = "REVERSE_HUM_ROUTE"

  fun create(seed: String = "level1-prototype"): LevelInstanceState {
    val zones = linkedMapOf(
      "parking_a" to ZoneState("parking_a", "Parking A", setOf("parking_loop"), setOf("entry"), mapOf("material" to "concrete")),
      "parking_loop" to ZoneState("parking_loop", "Parking Loop 14", setOf("maintenance"), setOf("loop"), mapOf("door" to "14")),
      "maintenance" to ZoneState("maintenance", "Maintenance Hall", setOf("parking_loop", "blackout_hall"), setOf("utility"), mapOf("breaker" to "main")),
      "blackout_hall" to ZoneState("blackout_hall", "Blackout Hall", setOf("service_elevator"), setOf("dark"), mapOf("machineHum" to "east")),
      "service_elevator" to ZoneState("service_elevator", "Service Elevator", emptySet(), setOf("escape"), mapOf("doorState" to "sealed"))
    )
    val evidence = listOf(
      EvidenceState("e-door-repeat", setOf(FACT_DOOR_LOOP), setOf(EvidenceSource.ENVIRONMENT), "parking_loop", setOf("visit:parking_loop:2")),
      EvidenceState("e-door-scratch", setOf(FACT_DOOR_LOOP), setOf(EvidenceSource.SEARCH), "parking_loop"),
      EvidenceState("e-power-panel", setOf(FACT_POWER_OFF), setOf(EvidenceSource.SEARCH), "maintenance"),
      EvidenceState("e-power-survivor", setOf(FACT_POWER_OFF), setOf(EvidenceSource.SURVIVOR), "maintenance", setOf("visit:maintenance:1")),
      EvidenceState("e-hum-anomaly", setOf(FACT_REVERSE_HUM), setOf(EvidenceSource.ANOMALY), "blackout_hall", setOf("power:off")),
      EvidenceState("e-hum-survivor", setOf(FACT_REVERSE_HUM), setOf(EvidenceSource.SURVIVOR), "blackout_hall", setOf("visit:blackout_hall:1"))
    ).associateBy { it.id }

    return LevelInstanceState(
      runSeed = seed,
      levelId = LEVEL_ID,
      generationId = "level1:$seed",
      currentZoneId = "parking_a",
      zones = zones,
      landmarks = mapOf("door14" to "A scratched service door marked 14"),
      environment = mapOf("power" to "on", "exploreStep" to "0"),
      escapeBlueprint = EscapeBlueprintState(
        solutionId = "level1-service-elevator",
        requiredFacts = setOf(FACT_POWER_OFF, FACT_DOOR_LOOP, FACT_REVERSE_HUM),
        requiredActions = listOf("cut_power", "return_door_14", "follow_against_hum", "enter_service_elevator"),
        locked = true
      ),
      evidence = evidence,
      npcKnowledge = mapOf(
        "survivor-maintenance" to setOf("e-power-survivor"),
        "survivor-blackout" to setOf("e-hum-survivor")
      )
    )
  }
}

data class LevelActionOutcome(
  val state: GameState,
  val reply: String,
  val progressed: Boolean,
  val escaped: Boolean = false,
  val evidenceIds: Set<String> = emptySet()
)

/** Deterministic vertical slice. It deliberately does not call Gemini or LiteRT. */
object LevelOneRuntime {
  fun install(state: GameState, seed: String = "level1-prototype"): GameState {
    if (state.levelInstance?.levelId == LevelOnePrototype.LEVEL_ID) return state
    val level = LevelOnePrototype.create(seed)
    require(BlueprintValidator.validate(level).valid) { "invalid_level_one_blueprint" }
    return state.copy(
      levelInstance = level,
      world = state.world + mapOf(
        "location" to "Level 1 / ${level.zones.getValue(level.currentZoneId).name}",
        "worldRevision" to "L1:${level.revision}"
      )
    )
  }

  fun apply(state: GameState, kind: ActionKind, input: String): LevelActionOutcome {
    val level = state.levelInstance
      ?: return LevelActionOutcome(state, "Level instance chưa được khởi tạo.", progressed = false)
    if (level.levelId != LevelOnePrototype.LEVEL_ID) return LevelActionOutcome(state, "Level này chưa dùng prototype runtime.", progressed = false)
    if (level.completed) return LevelActionOutcome(state, "Lối chuyển Level đã được mở.", progressed = false, escaped = true)
    return when (kind) {
      ActionKind.SEARCH -> search(state, level)
      ActionKind.EXPLORE -> explore(state, level)
      ActionKind.EXECUTE -> execute(state, level, input)
    }
  }

  private fun search(state: GameState, level: LevelInstanceState): LevelActionOutcome {
    val key = "searched:${level.currentZoneId}:${level.revision}"
    if (level.environment[key] == "true") {
      return LevelActionOutcome(state, "Kai kiểm tra lại khu vực nhưng điều kiện chưa thay đổi; không có dấu vết mới.", progressed = false)
    }

    val eligible = level.evidence.values
      .filter { !it.discovered && it.zoneId == level.currentZoneId && EvidenceSource.SEARCH in it.sources }
      .firstOrNull { conditionsMet(level, it.discoverConditions) }

    val environment = level.environment + (key to "true")
    if (eligible == null) {
      return LevelActionOutcome(state.copy(levelInstance = level.copy(environment = environment)), "Không có thêm chi tiết đáng kể trong trạng thái hiện tại.", progressed = false)
    }

    val discovered = discover(level.copy(environment = environment), eligible.id)
    return LevelActionOutcome(
      sync(state, discovered),
      evidenceReply(eligible.id),
      progressed = true,
      evidenceIds = setOf(eligible.id)
    )
  }

  private fun explore(state: GameState, level: LevelInstanceState): LevelActionOutcome {
    val step = level.environment["exploreStep"]?.toIntOrNull() ?: 0
    val route = listOf("parking_loop", "maintenance", "parking_loop", "blackout_hall", "service_elevator")
    if (step >= route.size) return LevelActionOutcome(state, "Các tuyến dễ tiếp cận ở khu vực này đã được khảo sát; đi tiếp chỉ đưa Kai quay lại những vùng cũ.", progressed = false)

    val nextZone = route[step]
    val visitsKey = "visits:$nextZone"
    val visits = (level.environment[visitsKey]?.toIntOrNull() ?: 0) + 1
    var next = mutateEnvironment(
      level.copy(currentZoneId = nextZone),
      mapOf("exploreStep" to (step + 1).toString(), visitsKey to visits.toString()),
      "move",
      nextZone,
      "visit:$visits"
    )

    val revealed = mutableSetOf<String>()
    next.evidence.values
      .filter { !it.discovered && it.zoneId == nextZone && EvidenceSource.SEARCH !in it.sources }
      .filter { conditionsMet(next, it.discoverConditions) }
      .forEach { evidence -> next = discover(next, evidence.id); revealed += evidence.id }

    val reply = if (revealed.isEmpty()) {
      "Kai đi sâu hơn vào ${next.zones.getValue(nextZone).name}."
    } else {
      "Kai đi sâu hơn vào ${next.zones.getValue(nextZone).name}. ${revealed.joinToString(" ") { evidenceReply(it) }}"
    }
    return LevelActionOutcome(sync(state, next), reply, progressed = true, evidenceIds = revealed)
  }

  private fun execute(state: GameState, level: LevelInstanceState, input: String): LevelActionOutcome {
    val actionId = canonicalAction(input)
      ?: return LevelActionOutcome(state, "Hành động đó không làm thay đổi quy luật đang chi phối khu vực này.", progressed = false)
    val expectedIndex = level.completedActions.size
    val expected = level.escapeBlueprint.requiredActions.getOrNull(expectedIndex)
      ?: return LevelActionOutcome(state, "Không còn bước Escape nào chưa hoàn thành trong blueprint đã khóa.", progressed = false)
    if (actionId != expected) return LevelActionOutcome(state, "Kai thực hiện thử nghiệm, nhưng trạng thái thế giới không tạo ra tiến triển mới.", progressed = false)

    val allowed = when (actionId) {
      "cut_power" -> level.currentZoneId == "maintenance"
      "return_door_14" -> level.environment["power"] == "off" && level.currentZoneId == "parking_loop"
      "follow_against_hum" -> level.environment["power"] == "off" && level.currentZoneId == "blackout_hall"
      "enter_service_elevator" -> level.currentZoneId == "service_elevator"
      else -> false
    }
    if (!allowed) return LevelActionOutcome(state, "Giả thuyết có thể có ý nghĩa, nhưng điều kiện hoặc vị trí hiện tại chưa đúng.", progressed = false)

    var next = level.copy(completedActions = level.completedActions + actionId)
    next = when (actionId) {
      "cut_power" -> mutateEnvironment(next, mapOf("power" to "off"), "environment", "main_power", "off")
      "return_door_14" -> next
      "follow_against_hum" -> next.copy(currentZoneId = "service_elevator")
      "enter_service_elevator" -> next.copy(completed = true)
      else -> next
    }

    if (actionId == "cut_power") {
      val revealable = next.evidence.values.filter { !it.discovered && conditionsMet(next, it.discoverConditions) }
      revealable.filter { EvidenceSource.ANOMALY in it.sources && it.zoneId == next.currentZoneId }.forEach { next = discover(next, it.id) }
    }

    val escaped = next.completed
    val reply = when (actionId) {
      "cut_power" -> "Nguồn điện chính tắt. Tiếng đèn huỳnh quang biến mất và môi trường bước sang một trạng thái mới."
      "return_door_14" -> "Kai quay lại cửa 14 trong điều kiện mất điện; dấu hiệu lặp vẫn còn nguyên."
      "follow_against_hum" -> "Kai đi ngược hướng tiếng máy và tới được khu thang máy dịch vụ."
      "enter_service_elevator" -> "Cửa thang máy dịch vụ nhận đúng chuỗi điều kiện và mở lối chuyển Level."
      else -> ""
    }
    return LevelActionOutcome(sync(state, next), reply, progressed = true, escaped = escaped)
  }

  private fun canonicalAction(input: String): String? {
    val text = input.lowercase()
    return when {
      listOf("tắt", "ngắt", "cắt").any(text::contains) && listOf("điện", "nguồn", "cầu dao").any(text::contains) -> "cut_power"
      text.contains("14") && listOf("quay", "trở", "trở lại").any(text::contains) -> "return_door_14"
      text.contains("ngược") && listOf("tiếng", "máy", "âm").any(text::contains) -> "follow_against_hum"
      listOf("thang máy", "elevator").any(text::contains) && listOf("vào", "mở", "đi").any(text::contains) -> "enter_service_elevator"
      else -> null
    }
  }

  private fun conditionsMet(level: LevelInstanceState, conditions: Set<String>): Boolean = conditions.all { condition ->
    when {
      condition.startsWith("visit:") -> {
        val parts = condition.split(':')
        val required = parts.getOrNull(2)?.toIntOrNull() ?: return@all false
        (level.environment["visits:${parts.getOrNull(1).orEmpty()}"]?.toIntOrNull() ?: 0) >= required
      }
      condition.startsWith("power:") -> level.environment["power"] == condition.substringAfter(':')
      else -> false
    }
  }

  private fun discover(level: LevelInstanceState, evidenceId: String): LevelInstanceState {
    val current = level.evidence[evidenceId] ?: return level
    if (current.discovered) return level
    val updated = current.copy(discovered = true, discoveredAtRevision = level.revision)
    return level.copy(
      evidence = level.evidence + (evidenceId to updated),
      discoveredFacts = level.discoveredFacts + current.supports
    )
  }

  private fun mutateEnvironment(level: LevelInstanceState, changes: Map<String, String>, kind: String, target: String, value: String): LevelInstanceState {
    val revision = level.revision + 1
    return level.copy(
      environment = level.environment + changes,
      revision = revision,
      mutations = level.mutations + WorldMutation("L1:$revision:${level.mutations.size + 1}", revision, kind, target, value)
    )
  }

  private fun sync(state: GameState, level: LevelInstanceState): GameState = state.copy(
    levelInstance = level,
    world = state.world + mapOf(
      "location" to "Level 1 / ${level.zones.getValue(level.currentZoneId).name}",
      "worldRevision" to "L1:${level.revision}"
    )
  )

  private fun evidenceReply(id: String): String = when (id) {
    "e-door-repeat" -> "Cửa số 14 xuất hiện lại với đúng vết xước cũ dù tuyến đường vừa đổi."
    "e-door-scratch" -> "Vết xước và lớp bụi ở cửa 14 cho thấy đây là cùng một cánh cửa, không phải hai cửa trùng số."
    "e-power-panel" -> "Sơ đồ cầu dao có một nhánh dịch vụ chỉ thay đổi trạng thái khi nguồn chính bị ngắt."
    "e-power-survivor" -> "Một dấu ghi chép của survivor nói rằng khi garage mất điện, có những cửa trước đó không tồn tại."
    "e-hum-anomaly" -> "Khi nguồn chính tắt, tiếng máy còn lại vọng rõ từ một phía nhưng dấu luồng khí lại chạy theo hướng ngược lại."
    "e-hum-survivor" -> "Dấu survivor cảnh báo đừng đi theo tiếng máy; người viết đã gạch chân chữ 'ngược'."
    else -> "Kai nhận ra một chi tiết bất thường."
  }
}
