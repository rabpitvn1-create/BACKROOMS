from pathlib import Path
import re
ROOT = Path(__file__).resolve().parent
CORE = ROOT / 'app/src/main/java/com/rabpit/backroom/core'
(CORE / 'ItemContent.kt').write_text(r'''package com.rabpit.backroom.core

enum class ContentState { NONE }

object ItemContentRules {
  private val forbiddenAmount = Regex("(?:\\b\\d+(?:[.,]\\d+)?\\s*(?:ml|l|lit|lít|g|gram|kg|%)\\b|một nửa|nửa chai|nửa hộp|phần trăm)", RegexOption.IGNORE_CASE)
  fun hasForbiddenPreciseAmount(text: String): Boolean = forbiddenAmount.containsMatchIn(text)

  fun normalize(item: ItemStack): ItemStack {
    HealingItems.normalize(item)?.let { return it }
    val name = item.name.lowercase()
    val consumable = name.contains("chai nước") || name.contains("hộp thức ăn") || name.contains("hộp đồ ăn") ||
      name.contains("bình nhiên liệu") || name.contains("can nhiên liệu") || name.contains("viên đạn") ||
      item.metadata["physiologyEffect"]?.split(',', ';', '|')?.any { it.trim().uppercase() in setOf("WATER", "FOOD") } == true
    val metadata = item.metadata - setOf("contentState", "remainingContent", "contentAmount", "contentPercent", "containerPersistent")
    return item.copy(contentState = ContentState.NONE, metadata = if (consumable) metadata + ("consumedOnUse" to "true") else metadata)
  }

  fun nextAfterUse(item: ItemStack): ItemStack? {
    val normalized = normalize(item)
    return if (normalized.metadata["consumedOnUse"].equals("true", true) || normalized.metadata["consumable"].equals("true", true)) null else normalized
  }

  fun sameStackState(left: ItemStack, right: ItemStack): Boolean {
    val a = normalize(left); val b = normalize(right)
    return a.itemId == b.itemId && a.archetypeId == b.archetypeId && a.condition == b.condition && stackMetadata(a.metadata) == stackMetadata(b.metadata)
  }

  private fun stackMetadata(metadata: Map<String, String>): Map<String, String> = metadata - setOf(
    "omnivaultCopyCount", "lastUsedAt", "physicalInstanceIds", "identitySeed", "worldInstanceId",
    "omnivaultOriginal", "omnivaultSourceInstanceId", "omnivaultTemplateId"
  )
}
''')
engines = CORE / 'Engines.kt'
s = engines.read_text()
start = s.find('  if (owned.contentState == ContentState.EMPTY)')
if start >= 0:
    end = s.index('  val consumedOnUse =', start)
    s = s[:start] + s[end:]
    engines.write_text(s)

facade = CORE / 'GameCoreFacade.kt'
s = facade.read_text()
gate = '''
  fun blocksTextItemAction(action: String): Boolean = rules.interpretSync(action, GameContext(GameState.initial())).candidates.any {
    it.intent in setOf(GameIntent.USE_ITEM, GameIntent.TRANSFER_ITEM, GameIntent.DROP_ITEM)
  }

'''
if 'fun blocksTextItemAction(' not in s:
    s = s.replace('  companion object {', gate + '  companion object {', 1)
    s = s.replace('    val state = loadOrMigrate(legacy)\n    if (MadGodCanon.cheat(action))', '''    val state = loadOrMigrate(legacy)
    if (blocksTextItemAction(action)) return response(true, syncLegacy(legacy, state, false), "item_ui_required", "item_ui_required", "Hãy nhấn vào item và chọn Dùng, Chuyển hoặc Bỏ.")
    if (MadGodCanon.cheat(action))''', 1)
    s = s.replace('    val core = loadOrMigrate(before)\n    val turnId', '''    val core = loadOrMigrate(before)
    if (blocksTextItemAction(action)) return response(false, syncLegacy(before, core, false), "item_ui_required", "item_ui_required")
    val turnId''', 1)
    facade.write_text(s)

main = ROOT / 'app/src/main/java/com/rabpit/backroom/MainActivity.java'
s = main.read_text()
anchor = '          JSONObject combatResult = new JSONObject(requireGameCore().processCombat(stateJson, actionKind, action));'
if 'if (requireGameCore().blocksTextItemAction(action))' not in s:
    if anchor not in s: raise RuntimeError('Item text gate anchor missing')
    s = s.replace(anchor, '''          if (requireGameCore().blocksTextItemAction(action)) {
            JSONObject blocked = new JSONObject(requireGameCore().processRule(stateJson, action));
            emit("backroomTurn", blocked.getJSONObject("state").toString());
            return;
          }
''' + anchor, 1)
    main.write_text(s)
s = main.read_text()
if 'ITEM WHOLE UNIT HARD LOCK:' not in s:
    anchor = '    return actionDirective + '
    if anchor not in s: raise RuntimeError('Whole item prompt anchor missing')
    directive = '    return "ITEM WHOLE UNIT HARD LOCK: Item tiêu hao chỉ tính theo số lượng nguyên; dùng bao nhiêu trừ bấy nhiêu. Không theo dõi hoặc tạo biến thể một nửa, còn ít, sắp hết hay rỗng; không tự tạo vỏ sau khi dùng. Dùng/Chuyển/Bỏ chỉ thực hiện bằng nút item và Core, không kể rằng đã thành công qua hành động văn bản.\\n" + actionDirective + '
    main.write_text(s.replace(anchor, directive, 1))
print('Text item actions blocked; consumables now use whole units without content variants.')
