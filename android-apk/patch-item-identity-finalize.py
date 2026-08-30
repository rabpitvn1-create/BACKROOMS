from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
CATALOG = CORE / "ItemCatalog.kt"
INTENT = CORE / "IntentPipeline.kt"
CODEC = CORE / "GameStateCodec.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/ItemIdentityAuthorityTest.kt"


def once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# Quantity parsing must not interpret words inside a canonical item proper name.
# Example: "Cá Mòi Ba Cô Gái" contains "Ba", but that is not quantity=3.
# Keep explicit quantities outside the item mention intact.
# ---------------------------------------------------------------------------
catalog = CATALOG.read_text(encoding="utf-8")
mention_anchor = '''  fun officialMention(text: String): OfficialItem? = officialMentions(text).firstOrNull()

'''
mention_new = mention_anchor + r'''  fun withoutOfficialMentions(text: String): String {
    var normalized = identityKey(text)
    identityAliases.forEach { (alias, _) ->
      if (alias.isBlank()) return@forEach
      normalized = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(alias)}(?![\\p{L}\\p{N}])")
        .replace(normalized, " ")
    }
    return normalized.replace(Regex("\\s+"), " ").trim()
  }

'''
catalog = once(catalog, mention_anchor, mention_new, "quantity-safe item mention stripping")
CATALOG.write_text(catalog, encoding="utf-8")

intent = INTENT.read_text(encoding="utf-8")
old_quantity = r'''class DefaultQuantityResolver : QuantityResolver {
  private val words = mapOf("một" to 1, "hai" to 2, "ba" to 3, "bốn" to 4, "năm" to 5, "sáu" to 6, "bảy" to 7, "tám" to 8, "chín" to 9, "mười" to 10)
  override fun resolve(clause: String): Int {
    Regex("\\b(\\d+)\\b").find(clause)?.groupValues?.get(1)?.toIntOrNull()?.let { return it.coerceAtLeast(1) }
    if (Regex("\\bmột\\s+trăm\\b", RegexOption.IGNORE_CASE).containsMatchIn(clause)) return 100
    return words.entries.firstOrNull { Regex("\\b${it.key}\\b", RegexOption.IGNORE_CASE).containsMatchIn(clause) }?.value ?: 1
  }
}
'''
new_quantity = r'''class DefaultQuantityResolver : QuantityResolver {
  private val words = mapOf("mot" to 1, "hai" to 2, "ba" to 3, "bon" to 4, "nam" to 5, "sau" to 6, "bay" to 7, "tam" to 8, "chin" to 9, "muoi" to 10)
  override fun resolve(clause: String): Int {
    val quantityClause = ItemCatalog.withoutOfficialMentions(clause)
    Regex("\\b(\\d+)\\b").find(quantityClause)?.groupValues?.get(1)?.toIntOrNull()?.let { return it.coerceAtLeast(1) }
    if (Regex("\\bmot\\s+tram\\b", RegexOption.IGNORE_CASE).containsMatchIn(quantityClause)) return 100
    return words.entries.firstOrNull { Regex("\\b${it.key}\\b", RegexOption.IGNORE_CASE).containsMatchIn(quantityClause) }?.value ?: 1
  }
}
'''
intent = once(intent, old_quantity, new_quantity, "quantity resolver proper-name exclusion")
INTENT.write_text(intent, encoding="utf-8")


# ---------------------------------------------------------------------------
# Current-version saves may already contain an old localized item key in
# lastReferencedItemId. Inventory decode re-keys the ItemStack; keep the reference
# in lock-step so omitted-item commands ("Đưa cho Lucia") use the same identity.
# ---------------------------------------------------------------------------
codec = CODEC.read_text(encoding="utf-8")
helper_anchor = '''  private fun migrateV2Core(root: JSONObject): GameState {
'''
helper = '''  private fun canonicalizeItemReferences(metadata: Map<String, String>, inventories: Map<String, InventoryState>): Map<String, String> {
    val raw = metadata["lastReferencedItemId"]?.trim().orEmpty()
    if (raw.isBlank()) return metadata
    val owned = inventories[KAI_ID]?.items.orEmpty()
    if (raw in owned) return metadata
    val canonical = ItemCatalog.resolveOfficial(raw, raw)?.id ?: ItemCatalog.canonicalId(raw)
    return if (canonical in owned) metadata + ("lastReferencedItemId" to canonical) else metadata
  }

  private fun migrateV2Core(root: JSONObject): GameState {
'''
codec = once(codec, helper_anchor, helper, "save item reference canonicalizer")

old_migrate_metadata = '''      metadata = root.optJSONObject("metadata").stringsMap() + mapOf("migratedFromVersion" to "2", "equipmentSeparated" to "true")
'''
new_migrate_metadata = '''      metadata = canonicalizeItemReferences(
        root.optJSONObject("metadata").stringsMap() + mapOf("migratedFromVersion" to "2", "equipmentSeparated" to "true"),
        inventories
      )
'''
codec = once(codec, old_migrate_metadata, new_migrate_metadata, "v2 saved item reference migration")

old_current_metadata = '''      metadata = root.optJSONObject("metadata").stringsMap()
'''
new_current_metadata = '''      metadata = canonicalizeItemReferences(root.optJSONObject("metadata").stringsMap(), inventories)
'''
codec = once(codec, old_current_metadata, new_current_metadata, "current saved item reference migration")
CODEC.write_text(codec, encoding="utf-8")


# ---------------------------------------------------------------------------
# Regression evidence for both bugs: quantity words inside item proper names,
# and stale remembered IDs after a save created by older builds.
# ---------------------------------------------------------------------------
test = TEST.read_text(encoding="utf-8")
helper_assert = '''    assertEquals(localizedName, "target", command.targetId)
    val transferred = StateReducer.execute(reloaded, command)
'''
helper_assert_new = '''    assertEquals(localizedName, "target", command.targetId)
    assertEquals(localizedName, 1, command.quantity)
    val transferred = StateReducer.execute(reloaded, command)
'''
test = once(test, helper_assert, helper_assert_new, "round-trip command quantity assertion")

legacy_old = '''    val decoded = GameStateCodec.decode(root)
    val items = decoded.inventories.getValue(KAI_ID).items
    assertTrue(items.containsKey(ItemCatalog.ALMOND_WATER))
    assertFalse(items.containsKey("nước-hạnh-nhân"))
  }
'''
legacy_new = '''    root.getJSONObject("metadata").put("lastReferencedItemId", "nước-hạnh-nhân")
    val decoded = GameStateCodec.decode(root)
    val items = decoded.inventories.getValue(KAI_ID).items
    assertTrue(items.containsKey(ItemCatalog.ALMOND_WATER))
    assertFalse(items.containsKey("nước-hạnh-nhân"))
    assertEquals(ItemCatalog.ALMOND_WATER, decoded.metadata["lastReferencedItemId"])

    val remembered = CommandResolver().resolve(
      IntentCandidate("Đưa cho Target", GameIntent.TRANSFER_ITEM, IntentConfidence.HIGH, .99f, CommandSource.RULE),
      0, decoded.turn.currentTurnId,
      GameContext(decoded, mapOf("kai" to KAI_ID, "target" to "target"))
    ) as ItemCommand
    assertEquals(ItemCatalog.ALMOND_WATER, remembered.itemId)
  }
'''
test = once(test, legacy_old, legacy_new, "legacy remembered item identity assertion")

class_end = '''  @Test fun futureExplicitIdIsPreserved() {
    assertEquals("future:field-kit", ItemCatalog.identityId("future:field-kit", "Future Field Kit"))
  }
}
'''
class_new = '''  @Test fun quantityWordsInsideOfficialNamesAreNotCounts() {
    val resolver = DefaultQuantityResolver()
    assertEquals(1, resolver.resolve("Đưa Cá Mòi Ba Cô Gái cho Target"))
    assertEquals(2, resolver.resolve("Đưa 2 Cá Mòi Ba Cô Gái cho Target"))
    assertEquals(3, resolver.resolve("Đưa ba Băng gạc cho Target"))
  }

  @Test fun futureExplicitIdIsPreserved() {
    assertEquals("future:field-kit", ItemCatalog.identityId("future:field-kit", "Future Field Kit"))
  }
}
'''
test = once(test, class_end, class_new, "quantity collision regressions")
TEST.write_text(test, encoding="utf-8")

combined = "\n".join(path.read_text(encoding="utf-8") for path in (CATALOG, INTENT, CODEC, TEST))
for marker in (
    "fun withoutOfficialMentions(text: String)",
    "val quantityClause = ItemCatalog.withoutOfficialMentions(clause)",
    "fun canonicalizeItemReferences(metadata: Map<String, String>",
    '"lastReferencedItemId" to canonical',
    "quantityWordsInsideOfficialNamesAreNotCounts",
    'assertEquals(localizedName, 1, command.quantity)',
):
    if marker not in combined:
        raise RuntimeError("Item identity finalizer contract missing: " + marker)

print("Item identity finalized: proper-name quantity collisions removed and stale remembered item IDs canonicalized on load.")
