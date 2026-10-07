from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / 'app/src/main/java/com/rabpit/backroom/core'
ITEM_CONTENT = CORE / 'ItemContent.kt'
ENGINES = CORE / 'Engines.kt'
FACADE = CORE / 'GameCoreFacade.kt'
MAIN = ROOT / 'app/src/main/java/com/rabpit/backroom/MainActivity.java'

item_content = ITEM_CONTENT.read_text(encoding='utf-8')
for marker in (
    'enum class ContentState { NONE }',
    'metadata + ("consumedOnUse" to "true")',
    'fun nextAfterUse(item: ItemStack): ItemStack?',
):
    if marker not in item_content:
        raise RuntimeError('Whole-unit item contract missing from ItemContent.kt: ' + marker)
for legacy in ('ContentState.FULL', 'ContentState.LOW', 'ContentState.EMPTY', 'ContentProfile(', 'variantId('):
    if legacy in item_content:
        raise RuntimeError('Legacy partial-content model survived in ItemContent.kt: ' + legacy)

healing_hook = '    HealingItems.normalize(item)?.let { return it }\n'
if healing_hook not in item_content:
    anchor = '  fun normalize(item: ItemStack): ItemStack {\n'
    if item_content.count(anchor) != 1:
        raise RuntimeError('Whole-unit normalize anchor missing or ambiguous')
    item_content = item_content.replace(anchor, anchor + healing_hook, 1)
    ITEM_CONTENT.write_text(item_content, encoding='utf-8')

engines = ENGINES.read_text(encoding='utf-8')
for legacy in ('ContentState.FULL', 'ContentState.LOW', 'ContentState.EMPTY', 'item_content_reduced', 'item_content_emptied'):
    if legacy in engines:
        raise RuntimeError('Legacy partial-content branch survived in Engines.kt: ' + legacy)
if 'val consumedOnUse = owned.metadata["consumedOnUse"]' not in engines:
    raise RuntimeError('Whole-unit USE path missing from Engines.kt')

facade = FACADE.read_text(encoding='utf-8')
gate = '''
  fun blocksTextItemAction(action: String): Boolean = rules.interpretSync(action, GameContext(GameState.initial())).candidates.any {
    it.intent in setOf(GameIntent.USE_ITEM, GameIntent.TRANSFER_ITEM, GameIntent.DROP_ITEM)
  }

'''
if 'fun blocksTextItemAction(' not in facade:
    facade = facade.replace('  companion object {', gate + '  companion object {', 1)
    facade = facade.replace('    val state = loadOrMigrate(legacy)\n    if (MadGodCanon.cheat(action))', '''    val state = loadOrMigrate(legacy)
    if (blocksTextItemAction(action)) return response(true, syncLegacy(legacy, state, false), "item_ui_required", "item_ui_required", "Hãy nhấn vào item và chọn Dùng, Chuyển hoặc Bỏ.")
    if (MadGodCanon.cheat(action))''', 1)
    facade = facade.replace('    val core = loadOrMigrate(before)\n    val turnId', '''    val core = loadOrMigrate(before)
    if (blocksTextItemAction(action)) return response(false, syncLegacy(before, core, false), "item_ui_required", "item_ui_required")
    val turnId''', 1)
    FACADE.write_text(facade, encoding='utf-8')

main = MAIN.read_text(encoding='utf-8')
combat_anchor = '          JSONObject combatResult = new JSONObject(requireGameCore().processCombat(stateJson, actionKind, action));'
if 'if (requireGameCore().blocksTextItemAction(action))' not in main:
    if combat_anchor not in main:
        raise RuntimeError('Item text gate anchor missing')
    main = main.replace(combat_anchor, '''          if (requireGameCore().blocksTextItemAction(action)) {
            JSONObject blocked = new JSONObject(requireGameCore().processRule(stateJson, action));
            emit("backroomTurn", blocked.getJSONObject("state").toString());
            return;
          }
''' + combat_anchor, 1)

if 'ITEM WHOLE UNIT HARD LOCK:' not in main:
    prompt_anchor = '    return actionDirective + '
    if prompt_anchor not in main:
        raise RuntimeError('Whole item prompt anchor missing')
    directive = '    return "ITEM WHOLE UNIT HARD LOCK: Item tiêu hao chỉ tính theo số lượng nguyên; dùng bao nhiêu trừ bấy nhiêu. Không theo dõi hoặc tạo biến thể một nửa, còn ít, sắp hết hay rỗng; không tự tạo vỏ sau khi dùng. Dùng/Chuyển/Bỏ chỉ thực hiện bằng nút item và Core, không kể rằng đã thành công qua hành động văn bản.\\n" + actionDirective + '
    main = main.replace(prompt_anchor, directive, 1)

MAIN.write_text(main, encoding='utf-8')
print('Whole-unit item contract verified; build-time item UI routing and writer lock applied.')
