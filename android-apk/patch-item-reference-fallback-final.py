from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
INTENT = CORE / "IntentPipeline.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/ItemIdentityAuthorityTest.kt"

intent = INTENT.read_text(encoding="utf-8")
marker = "ITEM_REFERENCE_FALLBACK_FINAL_R02"

# The generated resolver has accumulated several compatibility layers, so do not
# depend on an obsolete exact `if (name.isBlank()) return null` line. Insert the
# remembered-item path immediately after the final item-name extraction instead.
# This keeps explicit item resolution untouched and preserves fail-closed behavior
# when the remembered item is no longer actually owned/stored/scanned.
if marker not in intent:
    lines = intent.splitlines(keepends=True)
    anchors = [
        index for index, line in enumerate(lines)
        if "val name = itemClause.replace(noise" in line
    ]
    if len(anchors) != 1:
        resolver_start = intent.find("class DefaultItemResolver : ItemResolver {")
        resolver_end = intent.find("class DefaultContainerResolver", resolver_start)
        snippet = intent[resolver_start:resolver_end] if resolver_start >= 0 and resolver_end > resolver_start else "<resolver unavailable>"
        raise RuntimeError(
            f"Remembered item fallback expected one final item-name extraction, found {len(anchors)}. "
            f"Generated resolver:\n{snippet}"
        )
    insertion = '''    // ITEM_REFERENCE_FALLBACK_FINAL_R02
    if (name.isBlank()) {
      val rawRemembered = context.lastReferencedItemId ?: return null
      val rememberedId = ItemCatalog.identityId(rawRemembered, rawRemembered)
      val known = context.state.inventories.values.asSequence().mapNotNull { it.items[rememberedId] }.firstOrNull()
        ?: context.state.omnivault.storedItems[rememberedId]
        ?: context.state.omnivault.scanSlots.firstOrNull { it.templateItem.itemId == rememberedId }?.templateItem
        ?: return null
      return rememberedId to known.name
    }
'''
    anchor = anchors[0]
    lines.insert(anchor + 1, insertion)
    intent = "".join(lines)

# Pronoun references use knownPair directly. Canonicalize that path too, but do
# it structurally so formatting changes in earlier patches do not break preflight.
known_pair_pattern = re.compile(
    r'''  private fun knownPair\(id: String, context: GameContext\): Pair<String, String> \{.*?\n  \}\n''',
    re.DOTALL,
)
known_pair = '''  private fun knownPair(id: String, context: GameContext): Pair<String, String> {
    val canonicalId = ItemCatalog.identityId(id, id)
    val known = context.state.inventories.values.asSequence().mapNotNull { it.items[canonicalId] }.firstOrNull()
      ?: context.state.omnivault.storedItems[canonicalId]
      ?: context.state.omnivault.scanSlots.firstOrNull { it.templateItem.itemId == canonicalId }?.templateItem
    return canonicalId to (known?.name ?: canonicalId)
  }
'''
current = known_pair_pattern.search(intent)
if current is None:
    raise RuntimeError("Remembered item fallback could not find DefaultItemResolver.knownPair")
if "val canonicalId = ItemCatalog.identityId(id, id)" not in current.group(0):
    intent = intent[:current.start()] + known_pair + intent[current.end():]

INTENT.write_text(intent, encoding="utf-8")

test = TEST.read_text(encoding="utf-8")

# The save-migration regression must use a real recipient. The generic resolver
# deliberately ignores actorAliases whose IDs are absent from GameState, otherwise
# arbitrary words could masquerade as characters. The previous fixture encoded
# GameState.initial() but then issued "Đưa cho Target", so "Target" correctly
# remained an item candidate and the test measured an impossible command instead
# of remembered-item migration.
legacy_fixture_old = '''  @Test fun localizedLegacyIdIsRekeyedDuringDecode() {
    val root = JSONObject(GameStateCodec.encode(GameState.initial()))
'''
legacy_fixture_new = '''  @Test fun localizedLegacyIdIsRekeyedDuringDecode() {
    val root = JSONObject(GameStateCodec.encode(withTarget()))
'''
if legacy_fixture_new not in test:
    count = test.count(legacy_fixture_old)
    if count != 1:
        raise RuntimeError(f"Remembered item regression fixture expected exactly one legacy setup, found {count}")
    test = test.replace(legacy_fixture_old, legacy_fixture_new, 1)
TEST.write_text(test, encoding="utf-8")

required_test = (
    'GameStateCodec.encode(withTarget())',
    'IntentCandidate("Đưa cho Target", GameIntent.TRANSFER_ITEM',
    'assertEquals(ItemCatalog.ALMOND_WATER, remembered.itemId)',
)
for required in required_test:
    if required not in test:
        raise RuntimeError("Remembered item regression contract missing: " + required)

for required in (
    marker,
    'val rememberedId = ItemCatalog.identityId(rawRemembered, rawRemembered)',
    'return rememberedId to known.name',
    'val canonicalId = ItemCatalog.identityId(id, id)',
):
    if required not in intent:
        raise RuntimeError("Remembered item runtime contract missing: " + required)

print("Remembered item fallback R03 finalized: canonical remembered IDs plus a valid save-migration recipient fixture.")
