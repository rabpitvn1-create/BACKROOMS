from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
CATALOG = CORE / "ItemCatalog.kt"
CONTENT = CORE / "ItemContent.kt"
INTENT = CORE / "IntentPipeline.kt"
LEDGER = CORE / "WorldItemLedger.kt"
GM_GAIN = CORE / "GmItemGainPolicy.kt"
TEST = TESTS / "ItemIdentityAuthorityTest.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def replace_regex(text: str, pattern: str, replacement: str, label: str) -> str:
    if replacement in text:
        return text
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one structural block, found {count}")
    return updated


# ---------------------------------------------------------------------------
# One authority for official identity. Unknown/custom IDs remain untouched.
# ---------------------------------------------------------------------------
catalog = CATALOG.read_text(encoding="utf-8")
if "ITEM_IDENTITY_AUTHORITY_V2" not in catalog:
    anchor = "  val ids: Set<String> = items.mapTo(linkedSetOf()) { it.id }\n"
    if anchor not in catalog:
        raise RuntimeError("ItemCatalog ids anchor missing")
    authority = r'''  // ITEM_IDENTITY_AUTHORITY_V2: official aliases resolve here and nowhere else.
  private fun identityKey(raw: String): String {
    val folded = java.text.Normalizer.normalize(raw.trim().lowercase(), java.text.Normalizer.Form.NFD)
      .replace(Regex("\\p{M}+"), "")
      .replace('đ', 'd')
    return folded.replace(Regex("[^\\p{L}\\p{N}]+"), " ")
      .replace(Regex("\\s+"), " ").trim()
  }

  private val explicitIdentityAliases: Map<String, String> = linkedMapOf(
    "đèn pin" to FLASHLIGHT, "den pin" to FLASHLIGHT,
    "bật lửa" to LIGHTER, "bat lua" to LIGHTER,
    "nước hạnh nhân" to ALMOND_WATER, "nuoc hanh nhan" to ALMOND_WATER,
    "water-bottle" to ALMOND_WATER, "almond_water" to ALMOND_WATER,
    "thực phẩm đóng hộp" to CANNED_FOOD, "thuc pham dong hop" to CANNED_FOOD,
    "đồ hộp" to CANNED_FOOD, "do hop" to CANNED_FOOD,
    "food-container" to CANNED_FOOD, "canned_food" to CANNED_FOOD,
    "pin" to BATTERY,
    "nhiên liệu bật lửa" to LIGHTER_FUEL, "nhien lieu bat lua" to LIGHTER_FUEL,
    "fuel-container" to LIGHTER_FUEL, "lighter_fuel" to LIGHTER_FUEL,
    "băng gạc" to BANDAGE, "bang gac" to BANDAGE,
    "cuộn băng" to BANDAGE, "cuon bang" to BANDAGE,
    "băng y tế" to BANDAGE, "bang y te" to BANDAGE,
    "medical:bandage" to BANDAGE,
    "thuốc sát trùng" to ANTISEPTIC, "thuoc sat trung" to ANTISEPTIC,
    "dung dịch sát trùng" to ANTISEPTIC, "dung dich sat trung" to ANTISEPTIC,
    "medical:antiseptic" to ANTISEPTIC,
    "thuốc giảm đau" to PAINKILLER, "thuoc giam dau" to PAINKILLER,
    "cá mòi ba cô gái" to SARDINES, "ca moi ba co gai" to SARDINES,
    "three lady cooks sardines" to SARDINES,
    "nước suối la vie" to LA_VIE, "nuoc suoi la vie" to LA_VIE,
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
    listOf(rawId, rawName).forEach { raw ->
      val key = identityKey(raw.orEmpty())
      if (key.isNotBlank()) {
        identityAliases.firstOrNull { it.first == key }?.second?.let { id ->
          find(id)?.let { return it }
        }
      }
    }
    return null
  }

  private data class IdentityMention(val start: Int, val end: Int, val aliasLength: Int, val itemId: String)

  fun officialMentions(text: String): List<OfficialItem> {
    val normalized = identityKey(text)
    if (normalized.isBlank()) return emptyList()
    val matches = mutableListOf<IdentityMention>()
    identityAliases.forEach { (alias, id) ->
      val regex = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(alias)}(?![\\p{L}\\p{N}])")
      regex.findAll(normalized).forEach { match ->
        matches += IdentityMention(match.range.first, match.range.last, alias.length, id)
      }
    }
    val selected = mutableListOf<IdentityMention>()
    matches.sortedByDescending { it.aliasLength }.forEach { candidate ->
      if (selected.none { existing -> candidate.start <= existing.end && existing.start <= candidate.end }) {
        selected += candidate
      }
    }
    return selected.sortedBy { it.start }.mapNotNull { find(it.itemId) }.distinctBy { it.id }
  }

  fun officialMention(text: String): OfficialItem? = officialMentions(text).firstOrNull()

  fun aliasTextsFor(rawId: String?, rawName: String?): Set<String> {
    val official = resolveOfficial(rawId, rawName)
      ?: return listOfNotNull(rawId, rawName).filter(String::isNotBlank).toSet()
    val aliases = linkedSetOf(official.id, official.name)
    official.metadata["englishAlias"]?.takeIf(String::isNotBlank)?.let(aliases::add)
    explicitIdentityAliases.filterValues { it == official.id }.keys.forEach(aliases::add)
    return aliases
  }

  fun stableCustomId(name: String): String = name.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-')
    .ifBlank { "item-${name.hashCode().toUInt()}" }

  fun identityId(rawId: String? = null, name: String? = null): String {
    resolveOfficial(rawId, name)?.let { return it.id }
    return rawId?.trim()?.takeIf(String::isNotBlank) ?: stableCustomId(name.orEmpty())
  }

  fun sameIdentity(leftId: String?, leftName: String?, rightId: String?, rightName: String?): Boolean =
    identityId(leftId, leftName) == identityId(rightId, rightName)

'''
    catalog = catalog.replace(anchor, authority + anchor, 1)
CATALOG.write_text(catalog, encoding="utf-8")


# Every persisted/owned official stack is re-keyed by the same authority.
content = CONTENT.read_text(encoding="utf-8")
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
content = replace_once(content, old_official, new_official, "ItemContent official identity")
CONTENT.write_text(content, encoding="utf-8")


# World items no longer own a second official-ID vocabulary.
ledger = LEDGER.read_text(encoding="utf-8")
ledger = replace_regex(
    ledger,
    r'''  private fun catalogFor\(id: String, name: String\): OfficialItem\? \{.*?\n  \}\n''',
    '''  private fun catalogFor(id: String, name: String): OfficialItem? = ItemCatalog.resolveOfficial(id, name)\n''',
    "WorldItemLedger catalog delegation",
)
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
ledger = replace_regex(
    ledger,
    r'''  private fun sameIdentity\(left: JSONObject, right: JSONObject\): Boolean \{.*?\n  \}\n''',
    '''  private fun sameIdentity(left: JSONObject, right: JSONObject): Boolean = ItemCatalog.sameIdentity(\n    left.optString("id", ""), left.optString("name", ""),\n    right.optString("id", ""), right.optString("name", "")\n  )\n''',
    "WorldItemLedger identity comparison",
)
ledger = replace_regex(
    ledger,
    r'''  private fun aliases\(record: JSONObject\): Set<String> \{.*?\n  \}\n''',
    '''  private fun aliases(record: JSONObject): Set<String> = ItemCatalog.aliasTextsFor(\n    record.optString("id", ""), record.optString("name", "")\n  ).map(::normalized).filter(String::isNotBlank).toSet()\n''',
    "WorldItemLedger alias delegation",
)
old_discovered = '''      val discovered = ItemCatalog.items.filter { item ->
        text.contains(normalized(item.name)) ||
          item.metadata["englishAlias"]?.let { text.contains(normalized(it)) } == true ||
          (item.id == ItemCatalog.BANDAGE && listOf("băng gạc", "băng y tế", "cuộn băng").any(text::contains)) ||
          (item.id == ItemCatalog.ANTISEPTIC && listOf("thuốc sát trùng", "dung dịch sát trùng").any(text::contains))
      }
'''
ledger = replace_once(ledger, old_discovered, '''      val discovered = ItemCatalog.officialMentions(text)\n''', "WorldItemLedger narrative identity")
LEDGER.write_text(ledger, encoding="utf-8")


# GM grants also use the same authority instead of slugging localized names.
gm = GM_GAIN.read_text(encoding="utf-8")
old_gm_id = '''      val byName = current.values.firstOrNull { it.name.equals(name, ignoreCase = true) }
      val explicitId = json.optString("id").trim()
      val id = explicitId.ifBlank { byName?.itemId ?: stableItemId(name) }
      val old = current[id] ?: byName
'''
new_gm_id = '''      val byName = current.values.firstOrNull { it.name.equals(name, ignoreCase = true) }
      val explicitId = json.optString("id").trim()
      val byIdentity = current.values.firstOrNull { ItemCatalog.sameIdentity(it.itemId, it.name, explicitId, name) }
      val id = byIdentity?.itemId ?: byName?.itemId ?: ItemCatalog.identityId(explicitId.takeIf(String::isNotBlank), name)
      val old = current[id] ?: byIdentity ?: byName
'''
gm = replace_once(gm, old_gm_id, new_gm_id, "GM gain canonical identity")
GM_GAIN.write_text(gm, encoding="utf-8")


# Command resolution drops its private official alias table and consults ItemCatalog.
intent = INTENT.read_text(encoding="utf-8")
intent, count = re.subn(
    r'''\n  private val officialVietnameseAliases = linkedMapOf\(.*?\n  \)\n''',
    '\n', intent, count=1, flags=re.S,
)
if count != 1:
    raise RuntimeError(f"DefaultItemResolver alias table removal expected one block, found {count}")
old_lookup = '''    officialVietnameseAliases.entries
      .firstOrNull { (alias, _) -> resolverAliasRegex(alias).containsMatchIn(itemClause) }
      ?.let { (alias, id) -> return id to (ItemCatalog.find(id)?.name ?: alias) }
'''
intent = replace_once(intent, old_lookup, '''    ItemCatalog.officialMention(itemClause)?.let { item -> return item.id to item.name }\n''', "resolver catalog lookup")
old_context = '''    context.itemAliases.entries.firstOrNull { itemClause.contains(it.key, true) }?.let { return it.value to it.key }
'''
new_context = '''    context.itemAliases.entries.firstOrNull { itemClause.contains(it.key, true) }?.let {
      val official = ItemCatalog.resolveOfficial(it.value, it.key)
      return (official?.id ?: ItemCatalog.identityId(it.value, it.key)) to (official?.name ?: it.key)
    }
'''
intent = replace_once(intent, old_context, new_context, "resolver context alias identity")
old_fallback = '''    val id = canonicalId(name)
    return id to name
'''
new_fallback = '''    val official = ItemCatalog.resolveOfficial(null, name)
    return (official?.id ?: ItemCatalog.identityId(name = name)) to (official?.name ?: name)
'''
intent = replace_once(intent, old_fallback, new_fallback, "resolver fallback identity")
# The old helper is now dead. Remove only its compact slug function, not the class closing brace.
intent, count = re.subn(
    r'''\n  private fun canonicalId\(name: String\): String = name\.lowercase\(\)\n    \.replace\(Regex\("\[\^\\\\p\{L\}\\\\p\{N\}\]\+"\), "-"\)\.trim\('-'\)\.ifBlank \{ "item-\$\{name\.hashCode\(\)\.toUInt\(\)\}" \}\n''',
    '\n', intent, count=1,
)
if count != 1:
    raise RuntimeError(f"DefaultItemResolver canonicalId helper removal expected one function, found {count}")
INTENT.write_text(intent, encoding="utf-8")


# End-to-end regression matrix across the entire official pool.
TEST.write_text(r'''package com.rabpit.backroom.core

import org.json.JSONArray
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

  @Test fun everyLocalizedOfficialNameHasOneCanonicalId() {
    assertEquals(ItemCatalog.items.size, localized.size)
    localized.forEach { (name, expected) ->
      assertEquals(name, expected, ItemCatalog.identityId(name = name))
      assertEquals(expected, ItemCatalog.resolveOfficial(null, name)?.id)
    }
  }

  @Test fun everyOfficialItemSurvivesWorldPickupSaveReloadAndTransfer() {
    localized.forEach { (localizedName, expectedId) ->
      val flags = requireNotNull(WorldItemLedger.record(
        null, "identity-room",
        JSONObject().put("name", localizedName).put("quantity", 1).toString()
      ))
      val recorded = JSONObject(flags).getJSONArray("worldItems").getJSONObject(0)
      assertEquals(localizedName, expectedId, recorded.getString("id"))

      val pickup = requireNotNull(WorldItemLedger.consume(flags, "identity-room", "Nhặt $localizedName"))
      val worldItem = pickup.items.single()
      assertEquals(localizedName, expectedId, worldItem.itemId)

      val initial = withTarget().copy(world = mapOf("location" to "identity-room", "flagsJson" to pickup.flagsJson))
      val acquired = StateReducer.execute(initial, ItemCommand(
        commandId = "pickup-$expectedId", turnId = initial.turn.currentTurnId,
        actorId = KAI_ID, source = CommandSource.SYSTEM, operation = ItemCommand.Operation.PICKUP,
        itemId = worldItem.itemId, itemName = worldItem.itemName, quantity = 1, metadata = worldItem.metadata
      ))
      assertTrue("pickup $localizedName: ${acquired.validation.reason}", acquired.applied)
      assertTrue(acquired.state.inventories.getValue(KAI_ID).items.containsKey(expectedId))
      assertEquals(expectedId, acquired.state.metadata["lastReferencedItemId"])

      val reloaded = GameStateCodec.decode(GameStateCodec.encode(acquired.state))
      assertTrue("reload $localizedName", reloaded.inventories.getValue(KAI_ID).items.containsKey(expectedId))

      val command = CommandResolver().resolve(
        IntentCandidate("Đưa $localizedName cho Target", GameIntent.TRANSFER_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
        0, reloaded.turn.currentTurnId,
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

  @Test fun gmGainWithLocalizedOfficialNameUsesCanonicalId() {
    val candidate = JSONArray().put(JSONObject().put("name", "Nước Hạnh Nhân").put("quantity", 1))
    val gains = GmItemGainPolicy.positiveDeltas(emptyMap(), candidate)
    assertEquals(1, gains.size)
    assertEquals(ItemCatalog.ALMOND_WATER, gains.single().itemId)
  }

  @Test fun localizedLegacyIdIsRekeyedDuringDecode() {
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
    assertEquals(setOf(ItemCatalog.ALMOND_WATER), decoded.inventories.getValue(KAI_ID).items.keys)
  }

  @Test fun longestOfficialAliasWinsInsidePhrase() {
    assertEquals(
      listOf(ItemCatalog.FLASHLIGHT),
      ItemCatalog.officialMentions("Trên bàn có một Đèn pin.").map { it.id }
    )
  }

  @Test fun futureExplicitIdIsPreserved() {
    assertEquals("future:field-kit", ItemCatalog.identityId("future:field-kit", "Future Field Kit"))
  }
}
''', encoding="utf-8")

combined = "\n".join(path.read_text(encoding="utf-8") for path in (CATALOG, CONTENT, INTENT, LEDGER, GM_GAIN, TEST))
for marker in (
    "ITEM_IDENTITY_AUTHORITY_V2",
    "fun resolveOfficial(rawId: String? = null, rawName: String? = null)",
    "fun officialMentions(text: String)",
    "ItemCatalog.resolveOfficial(item.itemId, item.name)",
    "ItemCatalog.identityId(requestedId.takeIf(String::isNotBlank), name)",
    "ItemCatalog.sameIdentity(it.itemId, it.name, explicitId, name)",
    "ItemCatalog.officialMention(itemClause)",
    "class ItemIdentityAuthorityTest",
    "everyOfficialItemSurvivesWorldPickupSaveReloadAndTransfer",
    "gmGainWithLocalizedOfficialNameUsesCanonicalId",
):
    if marker not in combined:
        raise RuntimeError("Item identity authority contract missing: " + marker)
if "officialVietnameseAliases" in INTENT.read_text(encoding="utf-8"):
    raise RuntimeError("DefaultItemResolver duplicated official alias table survived")

print("Item identity authority V2 applied across world acquisition, GM gains, persistence and command resolution.")
