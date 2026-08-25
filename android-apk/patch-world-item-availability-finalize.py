from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FACADE = CORE / "GameCoreFacade.kt"
LEDGER = CORE / "WorldItemLedger.kt"
TEST = TESTS / "WorldItemLedgerTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# Visible-but-unowned items need authoritative state separate from Inventory.
LEDGER.write_text(r'''package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

data class WorldItemRecord(
  val itemId: String,
  val itemName: String,
  val quantity: Int,
  val metadata: Map<String, String>
)

data class WorldItemPickup(
  val items: List<WorldItemRecord>,
  val flagsJson: String
)

object WorldItemLedger {
  private fun normalized(value: String?): String = value.orEmpty().trim().lowercase()

  private fun stableId(name: String): String = name.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
    .trim('-')
    .ifBlank { "world-item-${name.hashCode().toUInt()}" }

  private fun flags(raw: String?): JSONObject = runCatching {
    if (raw.isNullOrBlank()) JSONObject() else JSONObject(raw)
  }.getOrElse { JSONObject() }

  private fun strings(json: JSONObject?): Map<String, String> {
    if (json == null) return emptyMap()
    val result = linkedMapOf<String, String>()
    val keys = json.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      result[key] = json.optString(key, "")
    }
    return result
  }

  private fun catalogFor(id: String, name: String): OfficialItem? {
    val rawId = normalized(id)
    val rawName = normalized(name)
    return ItemCatalog.items.firstOrNull { item ->
      rawId == normalized(item.id) ||
        rawName == normalized(item.name) ||
        (rawName.isNotBlank() && normalized(item.name).isNotBlank() && rawName.contains(normalized(item.name)))
    }
  }

  private fun canonical(raw: JSONObject, location: String): JSONObject? {
    val requestedName = raw.optString("name", "").trim()
    val requestedId = raw.optString("id", "").trim()
    val catalog = catalogFor(requestedId, requestedName)
    val name = catalog?.name ?: requestedName.ifBlank { requestedId }
    if (name.isBlank()) return null
    val id = catalog?.id ?: requestedId.ifBlank { stableId(name) }
    val metadata = JSONObject()
    catalog?.metadata?.forEach { (key, value) -> metadata.put(key, value) }
    raw.optJSONObject("metadata")?.let { extra ->
      val keys = extra.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        metadata.put(key, extra.optString(key, ""))
      }
    }
    return JSONObject()
      .put("id", id)
      .put("name", name)
      .put("quantity", raw.optInt("quantity", 1).coerceIn(1, 999))
      .put("available", true)
      .put("locationKey", location.trim())
      .put("metadata", metadata)
  }

  private fun sameLocation(record: JSONObject, location: String): Boolean {
    val recorded = normalized(record.optString("locationKey", ""))
    val current = normalized(location)
    return recorded.isBlank() || current.isBlank() || recorded == current
  }

  private fun sameIdentity(left: JSONObject, right: JSONObject): Boolean {
    val leftId = normalized(left.optString("id", ""))
    val rightId = normalized(right.optString("id", ""))
    val leftName = normalized(left.optString("name", ""))
    val rightName = normalized(right.optString("name", ""))
    return (leftId.isNotBlank() && leftId == rightId) || (leftName.isNotBlank() && leftName == rightName)
  }

  fun record(flagsJson: String?, location: String?, itemJson: String): String? {
    val rawItem = runCatching { JSONObject(itemJson) }.getOrNull() ?: return null
    val currentLocation = location.orEmpty().trim()
    val record = canonical(rawItem, currentLocation) ?: return null
    val root = flags(flagsJson)
    val items = root.optJSONArray("worldItems") ?: JSONArray()
    var existing = -1
    for (index in 0 until items.length()) {
      val candidate = items.optJSONObject(index) ?: continue
      if (sameIdentity(candidate, record) && sameLocation(candidate, currentLocation)) {
        existing = index
        break
      }
    }
    if (existing >= 0) {
      val previous = items.optJSONObject(existing)
      val merged = JSONObject(record.toString())
      if (previous != null && previous.optBoolean("available", true)) {
        merged.put("quantity", maxOf(previous.optInt("quantity", 1), record.optInt("quantity", 1)))
      }
      items.put(existing, merged)
    } else {
      items.put(record)
    }
    root.put("worldItems", items)
    return root.toString()
  }

  private fun aliases(record: JSONObject): Set<String> {
    val result = linkedSetOf<String>()
    val id = record.optString("id", "")
    val name = record.optString("name", "")
    listOf(id, name).map(::normalized).filter(String::isNotBlank).forEach(result::add)
    val catalog = catalogFor(id, name)
    if (catalog != null) {
      result += normalized(catalog.id)
      result += normalized(catalog.name)
      catalog.metadata["englishAlias"]?.let { result += normalized(it) }
      when (catalog.id) {
        ItemCatalog.BANDAGE -> result += setOf("băng gạc", "cuộn băng", "băng y tế")
        ItemCatalog.ANTISEPTIC -> result += setOf("thuốc sát trùng", "dung dịch sát trùng")
      }
    }
    return result.filter(String::isNotBlank).toSet()
  }

  private fun matchesAction(record: JSONObject, action: String): Boolean {
    val text = normalized(action)
    return aliases(record).any { alias ->
      text.contains(alias) || alias.split(Regex("\\s+")).filter { it.length >= 4 }.any(text::contains)
    }
  }

  private fun genericPickup(action: String): Boolean {
    val text = normalized(action).replace(Regex("[.!?,]+$"), "").trim()
    if (text in setOf(
        "nhặt", "nhặt lấy", "lượm", "lấy", "nhặt vật phẩm", "nhặt lấy vật phẩm",
        "nhặt lấy vật phẩm trên bàn", "lấy vật phẩm trên bàn", "nhặt hết", "lấy hết",
        "nhặt tất cả", "lấy tất cả", "pick up", "pick up all", "take all"
      )) return true
    val pickupVerb = text.startsWith("nhặt") || text.startsWith("lượm") || text.startsWith("lấy") || text.startsWith("pick up") || text.startsWith("take")
    return pickupVerb && (
      text.contains("tất cả") || text.contains("cả hai") || text.contains("hết") ||
        text.contains("vật phẩm") || text.contains("đồ trên") || text.contains("trên bàn")
    )
  }

  private fun availabilityCue(text: String): Boolean = listOf(
    "nhìn thấy", "bạn thấy", "nằm ", "trên bàn", "trên mặt bàn", "bên trong", "đặt ",
    "lộ ra", "vật phẩm", "chưa qua sử dụng", "còn nguyên"
  ).any { normalized(text).contains(it) }

  private fun inferFromRecentNarrative(items: JSONArray, location: String, narratives: List<String>): Boolean {
    for (narrative in narratives.take(6)) {
      if (!availabilityCue(narrative)) continue
      val text = normalized(narrative)
      val discovered = ItemCatalog.items.filter { item ->
        text.contains(normalized(item.name)) ||
          item.metadata["englishAlias"]?.let { text.contains(normalized(it)) } == true ||
          (item.id == ItemCatalog.BANDAGE && listOf("băng gạc", "băng y tế", "cuộn băng").any(text::contains)) ||
          (item.id == ItemCatalog.ANTISEPTIC && listOf("thuốc sát trùng", "dung dịch sát trùng").any(text::contains))
      }
      if (discovered.isEmpty()) continue
      discovered.forEach { item ->
        val raw = JSONObject().put("id", item.id).put("name", item.name).put("quantity", 1).put("metadata", JSONObject(item.metadata))
        val record = canonical(raw, location) ?: return@forEach
        var duplicate = false
        for (index in 0 until items.length()) {
          val existing = items.optJSONObject(index) ?: continue
          if (sameIdentity(existing, record) && sameLocation(existing, location) && existing.optBoolean("available", true)) {
            duplicate = true
            break
          }
        }
        if (!duplicate) items.put(record)
      }
      return true
    }
    return false
  }

  private fun localAvailable(items: JSONArray, location: String): List<Pair<Int, JSONObject>> {
    val result = mutableListOf<Pair<Int, JSONObject>>()
    for (index in 0 until items.length()) {
      val item = items.optJSONObject(index) ?: continue
      if (!item.optBoolean("available", true) || item.optInt("quantity", 1) <= 0) continue
      if (!sameLocation(item, location)) continue
      result += index to item
    }
    return result
  }

  fun consume(
    flagsJson: String?,
    location: String?,
    action: String,
    recentNarratives: List<String> = emptyList()
  ): WorldItemPickup? {
    val currentLocation = location.orEmpty().trim()
    val root = flags(flagsJson)
    val items = root.optJSONArray("worldItems") ?: JSONArray()
    var available = localAvailable(items, currentLocation)
    if (available.isEmpty() && inferFromRecentNarrative(items, currentLocation, recentNarratives)) {
      available = localAvailable(items, currentLocation)
    }
    if (available.isEmpty()) return null

    val matching = available.filter { (_, item) -> matchesAction(item, action) }
    val selected = when {
      matching.isNotEmpty() -> matching
      available.size == 1 -> available
      genericPickup(action) -> available
      else -> emptyList()
    }
    if (selected.isEmpty()) return null

    val taken = mutableListOf<WorldItemRecord>()
    selected.forEach { (index, item) ->
      val quantity = item.optInt("quantity", 1).coerceAtLeast(1)
      val remaining = quantity - 1
      val id = item.optString("id", "").ifBlank { stableId(item.optString("name", "Item")) }
      val name = item.optString("name", "").ifBlank { id }
      val metadata = strings(item.optJSONObject("metadata")) + mapOf(
        "worldInstanceId" to item.optString("instanceId", "world:$id:$index").ifBlank { "world:$id:$index" },
        "itemOrigin" to "WORLD",
        "omnivaultOriginal" to "true"
      )
      taken += WorldItemRecord(id, name, 1, metadata)
      if (remaining <= 0) item.put("quantity", 0).put("available", false)
      else item.put("quantity", remaining)
      items.put(index, item)
    }
    root.put("worldItems", items)
    return WorldItemPickup(taken, root.toString())
  }
}
''', encoding="utf-8")

TEST.write_text(r'''package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WorldItemLedgerTest {
  @Test fun genericPickupCollectsBothVisibleItemsAtCurrentLocation() {
    var flags: String? = null
    flags = WorldItemLedger.record(flags, "Level 0 hall", JSONObject().put("id", ItemCatalog.BANDAGE).put("name", "Bandage").toString())
    flags = WorldItemLedger.record(flags, "Level 0 hall", JSONObject().put("id", ItemCatalog.ANTISEPTIC).put("name", "Antiseptic").toString())
    val result = WorldItemLedger.consume(flags, "Level 0 hall", "Nhặt lấy vật phẩm trên bàn")
    assertNotNull(result)
    assertEquals(setOf(ItemCatalog.BANDAGE, ItemCatalog.ANTISEPTIC), result!!.items.map { it.itemId }.toSet())
    val remaining = JSONObject(result.flagsJson).getJSONArray("worldItems")
    assertEquals(2, remaining.length())
    assertFalse(remaining.getJSONObject(0).getBoolean("available"))
    assertFalse(remaining.getJSONObject(1).getBoolean("available"))
  }

  @Test fun legacyNarrativeBackfillsBrokenExistingSave() {
    val narrative = "Trên mặt bàn phủ bụi, bạn nhìn thấy một hộp sơ cứu. Bên trong lộ ra Bandage cùng Antiseptic chưa qua sử dụng."
    val result = WorldItemLedger.consume(null, "Level 0 hall", "Nhặt lấy", listOf(narrative))
    assertNotNull(result)
    assertEquals(setOf(ItemCatalog.BANDAGE, ItemCatalog.ANTISEPTIC), result!!.items.map { it.itemId }.toSet())
  }

  @Test fun namedPickupDoesNotTakeUnrelatedVisibleItem() {
    var flags: String? = null
    flags = WorldItemLedger.record(flags, "room-a", JSONObject().put("id", ItemCatalog.BANDAGE).put("name", "Bandage").toString())
    flags = WorldItemLedger.record(flags, "room-a", JSONObject().put("id", ItemCatalog.ANTISEPTIC).put("name", "Antiseptic").toString())
    val result = WorldItemLedger.consume(flags, "room-a", "Nhặt Bandage")
    assertNotNull(result)
    assertEquals(listOf(ItemCatalog.BANDAGE), result!!.items.map { it.itemId })
    val remaining = JSONObject(result.flagsJson).getJSONArray("worldItems")
    val available = (0 until remaining.length()).map { remaining.getJSONObject(it) }.filter { it.optBoolean("available", true) }
    assertEquals(1, available.size)
    assertEquals(ItemCatalog.ANTISEPTIC, available.single().getString("id"))
  }

  @Test fun pickupCannotConsumeItemFromAnotherLocation() {
    val flags = WorldItemLedger.record(null, "room-a", JSONObject().put("id", ItemCatalog.BANDAGE).put("name", "Bandage").toString())
    assertNull(WorldItemLedger.consume(flags, "room-b", "Nhặt Bandage"))
  }
}
''', encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")
operation_old = '      "party_upsert{member}; party_remove{name}; flag_patch{root,value}. " +\n'
operation_new = '      "party_upsert{member}; party_remove{name}; world_item_upsert{item}; flag_patch{root,value}. " +\n'
main = replace_once(main, operation_old, operation_new, "world item operation schema")

inventory_prompt = '      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory. " +\n'
world_prompt = '      "WORLD ITEM HARD LOCK: khi reply mô tả một item vật lý đang hiện diện và có thể nhặt/tương tác trong môi trường nhưng Kai CHƯA sở hữu, bắt buộc kèm world_item_upsert{item:{id,name,quantity,metadata}} cho từng item. world_item_upsert chỉ ghi nhận vật đang ở hiện trường, tuyệt đối không tự thêm vào Inventory. Khi Kai thực sự nhặt ở lượt sau, Game State Core sẽ chuyển ledger sang Inventory. " +\n'
if world_prompt not in main:
    if inventory_prompt not in main:
        raise RuntimeError("World item prompt anchor missing")
    main = main.replace(inventory_prompt, inventory_prompt + world_prompt, 1)

inventory_handler = '      if (type.equals("inventory_upsert")) {\n'
world_handler = r'''      if (type.equals("world_item_upsert")) {
        JSONObject item = op.optJSONObject("item");
        if (item == null) continue;
        JSONObject currentFlags = state.optJSONObject("flags");
        String updatedFlags = com.rabpit.backroom.core.WorldItemLedger.INSTANCE.record(
          currentFlags != null ? currentFlags.toString() : null,
          state.optString("location", before.optString("location", "")),
          item.toString()
        );
        if (updatedFlags != null) state.put("flags", new JSONObject(updatedFlags));
        continue;
      }

'''
if 'type.equals("world_item_upsert")' not in main:
    if inventory_handler not in main:
        raise RuntimeError("World item reducer anchor missing")
    main = main.replace(inventory_handler, world_handler + inventory_handler, 1)

rejected_old = '''      } else if (type.equals("party_upsert") || type.equals("party_remove")) {
        rejected = !jsonChanged(before.optJSONArray("party"), candidate.optJSONArray("party"));
      } else if (type.equals("inventory_upsert") || type.equals("inventory_remove")) {
        rejected = !jsonChanged(before.optJSONArray("inventory"), candidate.optJSONArray("inventory"));
      } else if (type.equals("patch_player")) {
'''
rejected_new = '''      } else if (type.equals("party_upsert") || type.equals("party_remove")) {
        rejected = !jsonChanged(before.optJSONArray("party"), candidate.optJSONArray("party"));
      } else if (type.equals("inventory_upsert") || type.equals("inventory_remove")) {
        rejected = !jsonChanged(before.optJSONArray("inventory"), candidate.optJSONArray("inventory"));
      } else if (type.equals("world_item_upsert")) {
        Object beforeWorldItems = beforeFlags != null ? beforeFlags.opt("worldItems") : null;
        Object afterWorldItems = afterFlags != null ? afterFlags.opt("worldItems") : null;
        rejected = !jsonChanged(beforeWorldItems, afterWorldItems);
      } else if (type.equals("patch_player")) {
'''
main = replace_once(main, rejected_old, rejected_new, "world item rejected-op audit")
MAIN.write_text(main, encoding="utf-8")

facade = FACADE.read_text(encoding="utf-8")
world_start = facade.find("  private data class WorldPickup(")
world_end = facade.find("  private fun isAuthoritativeItemIntent(", world_start)
if world_start < 0 or world_end < 0:
    raise RuntimeError("Final GameCoreFacade world pickup helper anchors missing")
world_helpers = r'''  private fun recentWorldItemNarratives(legacy: JSONObject): List<String> {
    val log = legacy.optJSONArray("log") ?: return emptyList()
    val result = mutableListOf<String>()
    val first = maxOf(0, log.length() - 8)
    for (index in log.length() - 1 downTo first) {
      val entry = log.optJSONObject(index) ?: continue
      val role = entry.optString("role", "").lowercase()
      if (role == "player" || role == "gain") continue
      val text = entry.optString("text", "").trim()
      if (text.isBlank() || text.startsWith("[Warning]")) continue
      result += text
    }
    return result
  }

'''
facade = facade[:world_start] + world_helpers + facade[world_end:]

process_start = facade.find("  fun processRule(")
pickup_start = facade.find("    if (isDirectPlayerPickupAction(action)) {", process_start)
pickup_end_marker = "\n\n    // Restore is lore/narrative-only."
pickup_end = facade.find(pickup_end_marker, pickup_start)
if process_start < 0 or pickup_start < 0 or pickup_end < 0:
    raise RuntimeError("Final GameCoreFacade direct-pickup block anchors missing")
pickup_block = r'''    if (isDirectPlayerPickupAction(action)) {
      val worldPickup = WorldItemLedger.consume(
        pending.state.world["flagsJson"],
        pending.state.world["location"] ?: legacy.optString("location"),
        action,
        recentWorldItemNarratives(legacy)
      )
      if (worldPickup != null) {
        val commands = mutableListOf<GameCommand>()
        worldPickup.items.forEachIndexed { index, item ->
          commands += ItemCommand(
            commandId = "$turnId:SYSTEM:WORLD_PICKUP:$index",
            turnId = turnId,
            actorId = KAI_ID,
            source = CommandSource.SYSTEM,
            operation = ItemCommand.Operation.PICKUP,
            itemId = item.itemId,
            itemName = item.itemName,
            quantity = item.quantity,
            metadata = item.metadata
          )
        }
        commands += ValidatedLegacyStateCommand(
          commandId = "$turnId:SYSTEM:WORLD_PICKUP_FLAGS",
          turnId = turnId,
          source = CommandSource.SYSTEM,
          flagsJson = worldPickup.flagsJson,
          validatedByGameEngine = true
        )
        val committed = commitActionRuntime(pending.state, commands, action, turnId)
        if (committed.error != null) {
          val result = syncLegacy(legacy, state, incrementTurn = false)
          val reply = validationReply(committed.error)
          appendLog(result, action, reply)
          return response(true, result, committed.error, "validation_rejected", reply)
        }
        repository.save(committed.state)
        val result = syncLegacy(legacy, committed.state, incrementTurn = true)
        val names = worldPickup.items.joinToString(", ") { it.itemName }
        val reply = if (worldPickup.items.size == 1) "Đã nhặt $names và thêm vào Inventory."
          else "Đã nhặt các vật phẩm: $names và thêm vào Inventory."
        appendLog(result, action, reply)
        logger.log(PipelineLogEvent("COMMIT", turnId = turnId, details = mapOf(
          "worldPickup" to worldPickup.items.joinToString(",") { it.itemId }
        )))
        return response(true, result, null, "world_pickup_committed", reply)
      }
      val result = syncLegacy(legacy, state, incrementTurn = false)
      val reply = validationReply("player_pickup_unavailable")
      appendLog(result, action, reply)
      logger.log(PipelineLogEvent("REJECT", turnId = turnId, details = mapOf("reason" to "player_pickup_unavailable")))
      return response(true, result, "player_pickup_unavailable", "validation_rejected", reply)
    }'''
facade = facade[:pickup_start] + pickup_block + facade[pickup_end:]
FACADE.write_text(facade, encoding="utf-8")

combined = MAIN.read_text(encoding="utf-8") + "\n" + FACADE.read_text(encoding="utf-8") + "\n" + LEDGER.read_text(encoding="utf-8") + "\n" + TEST.read_text(encoding="utf-8")
for marker in (
    'world_item_upsert{item}',
    'WORLD ITEM HARD LOCK:',
    'WorldItemLedger.INSTANCE.record(',
    'type.equals("world_item_upsert")',
    'object WorldItemLedger',
    'fun consume(',
    'legacyNarrativeBackfillsBrokenExistingSave',
    'genericPickupCollectsBothVisibleItemsAtCurrentLocation',
    'WorldItemLedger.consume(',
    'commitActionRuntime(pending.state, commands, action, turnId)',
    '"world_pickup_committed"',
):
    if marker not in combined:
        raise RuntimeError("World item availability final contract missing: " + marker)

if 'private data class WorldPickup(' in FACADE.read_text(encoding="utf-8"):
    raise RuntimeError("Legacy single-world-item pickup helper survived finalization")

print("World item availability finalized: visible loot is structured, generic multi-item pickup is atomic, and legacy narrated loot can be recovered safely.")
