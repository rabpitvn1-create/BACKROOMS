from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
ITEM_CATALOG = CORE / "ItemCatalog.kt"
ITEM_CONTENT = CORE / "ItemContent.kt"
INTENT = CORE / "IntentPipeline.kt"
LEDGER = CORE / "WorldItemLedger.kt"
TEST = TESTS / "ItemIdentityAuthorityTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# ---------------------------------------------------------------------------
# 1) ItemCatalog is the single authority for official item identity.
# Display names may remain localized, but every official item gets one stable ID.
# Unknown/future items keep their explicit ID and remain extensible.
# ---------------------------------------------------------------------------
catalog = ITEM_CATALOG.read_text(encoding="utf-8")
identity_marker = "ITEM_IDENTITY_AUTHORITY_V1"
if identity_marker not in catalog:
    anchor = "  val ids: Set<String> = items.mapTo(linkedSetOf()) { it.id }\n"
    if anchor not in catalog:
        raise RuntimeError("ItemCatalog identity insertion anchor missing")
    authority = r'''  // ITEM_IDENTITY_AUTHORITY_V1: all official-name/alias/legacy-ID resolution lives here.
  private fun identityKey(raw: String): String {
    val folded = java.text.Normalizer.normalize(raw.trim().lowercase(), java.text.Normalizer.Form.NFD)
      .replace(Regex("\\p{M}+"), "")
      .replace('đ', 'd')
    return folded.replace(Regex("[^\\p{L}\\p{N}]+"), " ").replace(Regex("\\s+"), " ").trim()
  }

  private val explicitIdentityAliases: Map<String, String> = linkedMapOf(
    "đèn pin" to FLASHLIGHT,
    "den pin" to FLASHLIGHT,
    "bật lửa" to LIGHTER,
    "bat lua" to LIGHTER,
    "nước hạnh nhân" to ALMOND_WATER,
    "nuoc hanh nhan" to ALMOND_WATER,
    "water-bottle" to ALMOND_WATER,
    "almond_water" to ALMOND_WATER,
    "thực phẩm đóng hộp" to CANNED_FOOD,
    "thuc pham dong hop" to CANNED_FOOD,
    "đồ hộp" to CANNED_FOOD,
    "do hop" to CANNED_FOOD,
    "food-container" to CANNED_FOOD,
    "canned_food" to CANNED_FOOD,
    "pin" to BATTERY,
    "nhiên liệu bật lửa" to LIGHTER_FUEL,
    "nhien lieu bat lua" to LIGHTER_FUEL,
    "fuel-container" to LIGHTER_FUEL,
    "lighter_fuel" to LIGHTER_FUEL,
    "băng gạc" to BANDAGE,
    "bang gac" to BANDAGE,
    "cuộn băng" to BANDAGE,
    "cuon bang" to BANDAGE,
    "băng y tế" to BANDAGE,
    "bang y te" to BANDAGE,
    "medical:bandage" to BANDAGE,
    "thuốc sát trùng" to ANTISEPTIC,
    "thuoc sat trung" to ANTISEPTIC,
    "dung dịch sát trùng" to ANTISEPTIC,
    "dung dich sat trung" to ANTISEPTIC,
    "medical:antiseptic" to ANTISEPTIC,
    "thuốc giảm đau" to PAINKILLER,
    "thuoc giam dau" to PAINKILLER,
    "cá mòi ba cô gái" to SARDINES,
    "ca moi ba co gai" to SARDINES,
    "three lady cooks sardines" to SARDINES,
    "nước suối la vie" to LA_VIE,
    "nuoc suoi la vie" to LA_VIE,
    "la vie spring water" to LA_VIE
  )

  private val identityAliases: List<Pair<String, String>> by lazy {
    val values = linkedMapOf<String, String>()
    fun register(alias: String?, id: String) {
      val key = identityKey(alias.orEmpty())
      if (key.isNotBlank()) values.putIfAbsent(key, id)
    }
    items.forEach { item ->
      register(item.id, item.id)
      register(item.name, item.id)
      register(item.metadata["englishAlias"], item.id)
    }
    explicitIdentityAliases.forEach { (alias, id) -> register(alias, id) }
    values.entries.map { it.key to it.value }.sortedByDescending { it.first.length }
  }

  fun resolveOfficial(rawId: String? = null, rawName: String? = null): OfficialItem? {
    for (raw in listOf(rawId, rawName)) {
      val key = identityKey(raw.orEmpty())
      if (key.isBlank()) continue
      identityAliases.firstOrNull { it.first == key }?.second?.let { id -> find(id)?.let { return it } }
    }
    return null
  }

  private data class IdentityMention(val start: Int, val end: Int, val aliasLength: Int, val itemId: String)

  fun officialMentions(text: String): List<OfficialItem> {
    val normalized = identityKey(text)
    if (normalized.isBlank()) return emptyList()
    val matches = mutableListOf<IdentityMention>()
    identityAliases.forEach { (alias, id) ->
      if (alias.isBlank()) return@forEach
      val pattern = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(alias)}(?![\\p{L}\\p{N}])")
      pattern.findAll(normalized).forEach { match ->
        matches += IdentityMention(match.range.first, match.range.last, alias.length, id)
      }
    }
    val selected = mutableListOf<IdentityMention>()
    matches.sortedByDescending { it.aliasLength }.forEach { candidate ->
      if (selected.none { existing -> candidate.start <= existing.end && existing.start <= candidate.end }) selected += candidate
    }
    return selected.sortedBy { it.start }.mapNotNull { find(it.itemId) }.distinctBy { it.id }
  }

  fun officialMention(text: String): OfficialItem? = officialMentions(text).firstOrNull()

  fun aliasTextsFor(rawId: String?, rawName: String?): Set<String> {
    val official = resolveOfficial(rawId, rawName) ?: return listOfNotNull(rawId, rawName).filter(String::isNotBlank).toSet()
    val result = linkedSetOf(official.id, official.name)
    official.metadata["englishAlias"]?.takeIf(String::isNotBlank)?.let(result::add)
    explicitIdentityAliases.filterValues { it == official.id }.keys.forEach(result::add)
    return result
  }

  fun stableCustomId(name: String): String = name.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
    .trim('-')
    .ifBlank { "item-${name.hashCode().toUInt()}" }

  fun identityId(rawId: String? = null, name: String? = null): String {
    resolveOfficial(rawId, name)?.let { return it.id }
    return rawId?.trim()?.takeIf(String::isNotBlank) ?: stableCustomId(name.orEmpty())
  }

  fun sameIdentity(leftId: String?, leftName: String?, rightId: String?, rightName: String?): Boolean =
    identityId(leftId, leftName) == identityId(rightId, rightName)

'''
    catalog = catalog.replace(anchor, authority + anchor, 1)

canonical_pattern = re.compile(r'''  fun canonicalId\(raw: String\): String \{\n.*?\n  \}\n''', re.S)
canonical_replacement = '''  fun canonicalId(raw: String): String = identityId(raw, raw)\n'''
catalog, count = canonical_pattern.subn(canonical_replacement, catalog, count=1)
if count != 1:
    raise RuntimeError(f"ItemCatalog canonicalId replacement expected 1 function, found {count}")
ITEM_CATALOG.write_text(catalog, encoding="utf-8")


# ---------------------------------------------------------------------------
# 2) Every ItemStack normalization uses the same catalog identity authority.
# Preserve localized display names while canonicalizing authoritative IDs.
# ---------------------------------------------------------------------------
content = ITEM_CONTENT.read_text(encoding="utf-8")
old_official = '''    val official = ItemCatalog.find(item.itemId) ?: ItemCatalog.items.firstOrNull {
      item.name.equals(it.name, true) || item.name.equals(it.metadata["englishAlias"], true)
    }
    if (official != null) {
      return official.stack(item.quantity).copy(
        condition = item.condition,
'''
new_official = '''    val official = ItemCatalog.resolveOfficial(item.itemId, item.name)
    if (official != null) {
      return official.stack(item.quantity).copy(
        name = item.name.takeIf(String::isNotBlank) ?: official.name,
        condition = item.condition,
'''
content = replace_once(content, old_official, new_official, "ItemContent canonical identity")
ITEM_CONTENT.write_text(content, encoding="utf-8")


# ---------------------------------------------------------------------------
# 3) World acquisition uses ItemCatalog instead of maintaining its own identity rules.
# ---------------------------------------------------------------------------
ledger = LEDGER.read_text(encoding="utf-8")
old_stable = '''  private fun stableId(name: String): String = name.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
    .trim('-')
    .ifBlank { "world-item-${name.hashCode().toUInt()}" }
'''
new_stable = '''  private fun stableId(name: String): String = ItemCatalog.stableCustomId(name)
'''
ledger = replace_once(ledger, old_stable, new_stable, "WorldItemLedger stable ID delegation")

catalog_for_pattern = re.compile(r'''  private fun catalogFor\(id: String, name: String\): OfficialItem\? \{.*?\n  \}\n''', re.S)
ledger, count = catalog_for_pattern.subn('''  private fun catalogFor(id: String, name: String): OfficialItem? = ItemCatalog.resolveOfficial(id, name)\n''', ledger, count=1)
if count != 1:
    raise RuntimeError(f"WorldItemLedger catalogFor replacement expected 1 function, found {count}")

old_canonical = '''    val catalog = catalogFor(requestedId, requestedName)
    val name = catalog?.name ?: requestedName.ifBlank { requestedId }
    if (name.isBlank()) return null
    val id = catalog?.id ?: requestedId.ifBlank { stableId(name) }
'''
new_canonical = '''    val catalog = catalogFor(requestedId, requestedName)
    val name = requestedName.ifBlank { catalog?.name ?: requestedId }
    if (name.isBlank()) return null
    val id = ItemCatalog.identityId(requestedId.takeIf(String::isNotBlank), name)
'''
ledger = replace_once(ledger, old_canonical, new_canonical, "WorldItemLedger canonical record")

same_identity_pattern = re.compile(r'''  private fun sameIdentity\(left: JSONObject, right: JSONObject\): Boolean \{.*?\n  \}\n''', re.S)
same_identity_new = '''  private fun sameIdentity(left: JSONObject, right: JSONObject): Boolean = ItemCatalog.sameIdentity(
    left.optString("id", ""), left.optString("name", ""),
    right.optString("id", ""), right.optString("name", "")
  )
'''
ledger, count = same_identity_pattern.subn(same_identity_new, ledger, count=1)
if count != 1:
    raise RuntimeError(f"WorldItemLedger sameIdentity replacement expected 1 function, found {count}")

aliases_pattern = re.compile(r'''  private fun aliases\(record: JSONObject\): Set<String> \{.*?\n  \}\n''', re.S)
aliases_new = '''  private fun aliases(record: JSONObject): Set<String> = ItemCatalog.aliasTextsFor(
    record.optString("id", ""), record.optString("name", "")
  ).map(::normalized).filter(String::isNotBlank).toSet()
'''
ledger, count = aliases_pattern.subn(aliases_new, ledger, count=1)
if count != 1:
    raise RuntimeError(f"WorldItemLedger aliases replacement expected 1 function, found {count}")

old_discovered = '''      val discovered = ItemCatalog.items.filter { item ->
        text.contains(normalized(item.name)) ||
          item.metadata["englishAlias"]?.let { text.contains(normalized(it)) } == true ||
          (item.id == ItemCatalog.BANDAGE && listOf("băng gạc", "băng y tế", "cuộn băng").any(text::contains)) ||
          (item.id == ItemCatalog.ANTISEPTIC && listOf("thuốc sát trùng", "dung dịch sát trùng").any(text::contains))
      }
'''
new_discovered = '''      val discovered = ItemCatalog.officialMentions(text)
'''
ledger = replace_once(ledger, old_discovered, new_discovered, "WorldItemLedger narrative identity")
LEDGER.write_text(ledger, encoding="utf-8")


# ---------------------------------------------------------------------------
# 4) Player command resolution uses the same ItemCatalog identity authority.
# Remove the duplicated Vietnamese alias table from DefaultItemResolver.
# ---------------------------------------------------------------------------
intent = INTENT.read_text(encoding="utf-8")
alias_map_pattern = re.compile(r'''\n  private val officialVietnameseAliases = linkedMapOf\(.*?\n  \)\n''', re.S)
intent, count = alias_map_pattern.subn('\n', intent, count=1)
if count != 1:
    raise RuntimeError(f"DefaultItemResolver duplicated alias map removal expected 1 block, found {count}")

old_lookup = '''    officialVietnameseAliases.entries
      .firstOrNull { (alias, _) -> resolverAliasRegex(alias).containsMatchIn(itemClause) }
      ?.let { (alias, id) -> return id to (ItemCatalog.find(id)?.name ?: alias) }
'''
new_lookup = '''    ItemCatalog.officialMention(itemClause)?.let { item -> return item.id to item.name }
'''
intent = replace_once(intent, old_lookup, new_lookup, "DefaultItemResolver catalog mention")

old_context_alias = '''    context.itemAliases.entries.firstOrNull { itemClause.contains(it.key, true) }?.let { return it.value to it.key }
'''
new_context_alias = '''    context.itemAliases.entries.firstOrNull { itemClause.contains(it.key, true) }?.let {
      val official = ItemCatalog.resolveOfficial(it.value, it.key)
      return (official?.id ?: ItemCatalog.identityId(it.value, it.key)) to (official?.name ?: it.key)
    }
'''
intent = replace_once(intent, old_context_alias, new_context_alias, "DefaultItemResolver context alias canonicalization")

old_fallback = '''    val id = canonicalId(name)
    return id to name
'''
new_fallback = '''    val official = ItemCatalog.resolveOfficial(null, name)
    return (official?.id ?: ItemCatalog.identityId(name = name)) to (official?.name ?: name)
'''
intent = replace_once(intent, old_fallback, new_fallback, "DefaultItemResolver fallback identity")

private_canonical_pattern = re.compile(r'''\n  private fun canonicalId\(name: String\): String = name\.lowercase\(\)\n    \.replace\(Regex\("\[\^\\\\p\{L\}\\\\p\{N\}\]\+"\), "-"\)\.trim\('-'\)\.ifBlank \{ "item-\$\{name\.hashCode\(\)\.toUInt\(\)\}" \}\n''')
intent, count = private_canonical_pattern.subn('\n', intent, count=1)
if count != 1:
    # Structural fallback for small formatting changes in preceding patches.
    intent, count = re.subn(r'''\n  private fun canonicalId\(name: String\): String = name\.lowercase\(\).*?\n\}''', '\n}', intent, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError("DefaultItemResolver private canonicalId removal failed")
INTENT.write_text(intent, encoding="utf-8")


# ---------------------------------------------------------------------------
# 5) Regression matrix: localized world acquisition -> reducer -> save/reload ->
# natural-language transfer must keep exactly one canonical ID for all official items.
# ---------------------------------------------------------------------------
TEST.write_text(r'''package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ItemIdentityAuthorityTest {
  private val localized = linkedMapOf(
    "Đèn pin" to ItemCatalog.FLASHLIGHT,
    "Bật lửa" to ItemCatalog.LIGHTER,
    "Nước Hạnh Nhân" to ItemCatalog.ALMOND_WATER,
    "Thực phẩm đóng hộp" to ItemCatalog.CANNED_FOOD,
    "Pin" to ItemCatalog.BATTERY,
    "Nhiên liệu bật lửa" to ItemCatalog.LIGHTER_FUEL,
    "Băng gạc" to ItemCatalog.BANDAGE,
    "Thuốc sát trùng" to ItemCatalog.ANTISEPTIC,
    "Thuốc giảm đau" to ItemCatalog.PAINKILLER,
    "Cá Mòi Ba Cô Gái" to ItemCatalog.SARDINES,
    "Nước suối La Vie" to ItemCatalog.LA_VIE
  )

  private fun withTarget(): GameState {
    val base = GameState.initial()
    val target = CharacterState("target", "Target")
    return base.copy(
      characters = base.characters + (target.id to target),
      inventories = base.inventories + (target.id to InventoryState(target.id))
    )
  }

  @Test fun everyLocalizedOfficialNameResolvesToOneCanonicalId() {
    assertEquals(ItemCatalog.items.size, localized.size)
    localized.forEach { (name, expected) ->
      assertEquals(name, expected, ItemCatalog.identityId(name = name))
      assertEquals(expected, ItemCatalog.resolveOfficial(null, name)?.id)
    }
  }

  @Test fun allOfficialItemsRoundTripWorldPickupSaveReloadAndTransfer() {
    localized.forEach { (localizedName, expectedId) ->
      val flags = requireNotNull(WorldItemLedger.record(
        null,
        "identity-room",
        JSONObject().put("name", localizedName).put("quantity", 1).toString()
      ))
      val record = JSONObject(flags).getJSONArray("worldItems").getJSONObject(0)
      assertEquals(localizedName, expectedId, record.getString("id"))

      val pickup = requireNotNull(WorldItemLedger.consume(flags, "identity-room", "Nhặt $localizedName"))
      assertEquals(expectedId, pickup.items.single().itemId)
      val worldItem = pickup.items.single()
      val initial = withTarget().copy(world = mapOf("location" to "identity-room", "flagsJson" to pickup.flagsJson))
      val acquired = StateReducer.execute(initial, ItemCommand(
        commandId = "pickup-$expectedId",
        turnId = initial.turn.currentTurnId,
        actorId = KAI_ID,
        source = CommandSource.SYSTEM,
        operation = ItemCommand.Operation.PICKUP,
        itemId = worldItem.itemId,
        itemName = worldItem.itemName,
        quantity = 1,
        metadata = worldItem.metadata
      ))
      assertTrue("pickup $localizedName: ${acquired.validation.reason}", acquired.applied)
      assertTrue(acquired.state.inventories.getValue(KAI_ID).items.containsKey(expectedId))
      assertEquals(expectedId, acquired.state.metadata["lastReferencedItemId"])

      val reloaded = GameStateCodec.decode(GameStateCodec.encode(acquired.state))
      assertTrue("reload $localizedName", reloaded.inventories.getValue(KAI_ID).items.containsKey(expectedId))

      val command = CommandResolver().resolve(
        IntentCandidate("Đưa $localizedName cho Target", GameIntent.TRANSFER_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
        0,
        reloaded.turn.currentTurnId,
        GameContext(reloaded, mapOf("kai" to KAI_ID, "target" to "target"))
      ) as ItemCommand
      assertEquals(localizedName, expectedId, command.itemId)
      assertEquals("target", command.targetId)
      val transferred = StateReducer.execute(reloaded, command)
      assertTrue("transfer $localizedName: ${transferred.validation.reason}", transferred.applied)
      assertFalse(transferred.state.inventories.getValue(KAI_ID).items.containsKey(expectedId))
      assertEquals(1, transferred.state.inventories.getValue("target").items.getValue(expectedId).quantity)
    }
  }

  @Test fun badLocalizedSaveIdIsRekeyedOnDecode() {
    val root = JSONObject(GameStateCodec.encode(GameState.initial()))
    val kai = root.getJSONObject("inventories").getJSONObject(KAI_ID)
    kai.put("items", JSONObject().put("nước-hạnh-nhân", JSONObject()
      .put("itemId", "nước-hạnh-nhân")
      .put("name", "Nước Hạnh Nhân")
      .put("quantity", 1)
      .put("metadata", JSONObject())
      .put("archetypeId", "nước-hạnh-nhân")
      .put("contentState", "NONE")))
    val decoded = GameStateCodec.decode(root)
    val items = decoded.inventories.getValue(KAI_ID).items
    assertEquals(setOf(ItemCatalog.ALMOND_WATER), items.keys)
    assertEquals("Nước Hạnh Nhân", items.getValue(ItemCatalog.ALMOND_WATER).name)
  }

  @Test fun flashlightMentionDoesNotAlsoBecomeBattery() {
    assertEquals(listOf(ItemCatalog.FLASHLIGHT), ItemCatalog.officialMentions("Trên bàn có một Đèn pin.").map { it.id })
  }

  @Test fun unknownFutureItemKeepsExplicitIdentity() {
    assertEquals("future:field-kit", ItemCatalog.identityId("future:field-kit", "Future Field Kit"))
  }
}
''', encoding="utf-8")

combined = "\n".join(path.read_text(encoding="utf-8") for path in (ITEM_CATALOG, ITEM_CONTENT, INTENT, LEDGER, TEST))
for marker in (
    "ITEM_IDENTITY_AUTHORITY_V1",
    "fun resolveOfficial(rawId: String? = null, rawName: String? = null)",
    "fun officialMentions(text: String)",
    "fun identityId(rawId: String? = null, name: String? = null)",
    "ItemCatalog.resolveOfficial(item.itemId, item.name)",
    "ItemCatalog.identityId(requestedId.takeIf(String::isNotBlank), name)",
    "ItemCatalog.officialMention(itemClause)",
    "class ItemIdentityAuthorityTest",
    "allOfficialItemsRoundTripWorldPickupSaveReloadAndTransfer",
    "badLocalizedSaveIdIsRekeyedOnDecode",
):
    if marker not in combined:
        raise RuntimeError("Item identity authority contract missing: " + marker)

if "officialVietnameseAliases" in intent:
    raise RuntimeError("Duplicated DefaultItemResolver official alias table survived")

print("Canonical item identity authority applied across catalog, world acquisition, save normalization and command resolution.")
