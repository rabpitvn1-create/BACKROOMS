from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
INTENT = CORE / "IntentPipeline.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/ItemIdentityAuthorityTest.kt"

intent = INTENT.read_text(encoding="utf-8")

old_blank = '''    if (name.isBlank()) return null
'''
new_blank = '''    if (name.isBlank()) {
      val rawRemembered = context.lastReferencedItemId ?: return null
      val rememberedId = ItemCatalog.identityId(rawRemembered, rawRemembered)
      val known = context.state.inventories.values.asSequence().mapNotNull { it.items[rememberedId] }.firstOrNull()
        ?: context.state.omnivault.storedItems[rememberedId]
        ?: context.state.omnivault.scanSlots.firstOrNull { it.templateItem.itemId == rememberedId }?.templateItem
        ?: return null
      return rememberedId to known.name
    }
'''
if new_blank not in intent:
    count = intent.count(old_blank)
    if count != 1:
        raise RuntimeError(f"Remembered item fallback anchor expected exactly one blank-name branch, found {count}")
    intent = intent.replace(old_blank, new_blank, 1)

old_known_pair = '''  private fun knownPair(id: String, context: GameContext): Pair<String, String> {
    val known = context.state.inventories.values.asSequence().mapNotNull { it.items[id] }.firstOrNull()
      ?: context.state.omnivault.storedItems[id]
      ?: context.state.omnivault.scanSlots.firstOrNull { it.templateItem.itemId == id }?.templateItem
    return id to (known?.name ?: id)
  }
'''
new_known_pair = '''  private fun knownPair(id: String, context: GameContext): Pair<String, String> {
    val canonicalId = ItemCatalog.identityId(id, id)
    val known = context.state.inventories.values.asSequence().mapNotNull { it.items[canonicalId] }.firstOrNull()
      ?: context.state.omnivault.storedItems[canonicalId]
      ?: context.state.omnivault.scanSlots.firstOrNull { it.templateItem.itemId == canonicalId }?.templateItem
    return canonicalId to (known?.name ?: canonicalId)
  }
'''
if new_known_pair not in intent:
    count = intent.count(old_known_pair)
    if count != 1:
        raise RuntimeError(f"knownPair canonicalization anchor expected exactly one function, found {count}")
    intent = intent.replace(old_known_pair, new_known_pair, 1)

INTENT.write_text(intent, encoding="utf-8")

test = TEST.read_text(encoding="utf-8")
required = (
    'IntentCandidate("Đưa cho Target", GameIntent.TRANSFER_ITEM',
    'assertEquals(ItemCatalog.ALMOND_WATER, remembered.itemId)',
)
for marker in required:
    if marker not in test:
        raise RuntimeError("Remembered item regression contract missing: " + marker)

for marker in (
    'val rememberedId = ItemCatalog.identityId(rawRemembered, rawRemembered)',
    'return rememberedId to known.name',
    'val canonicalId = ItemCatalog.identityId(id, id)',
):
    if marker not in intent:
        raise RuntimeError("Remembered item runtime contract missing: " + marker)

print("Remembered item fallback finalized: omitted-item commands reuse only a still-owned canonical item identity.")
