from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
CATALOG = CORE / "ItemCatalog.kt"
FACADE = CORE / "GameCoreFacade.kt"
SERIALIZER = CORE / "CharacterDetailJson.kt"
INDEX = ROOT / "app/src/main/assets/index.html"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/ConsumableCatalogEffectsTest.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one anchor, found {count}")
    return text.replace(old, new, 1)


def replace_method(text: str, signature: str, replacement: str, label: str) -> str:
    start = text.find(signature)
    if start < 0:
        raise RuntimeError(f"{label}: signature not found")
    brace = text.find("{", start)
    if brace < 0:
        raise RuntimeError(f"{label}: opening brace not found")
    depth = 0
    for index in range(brace, len(text)):
        char = text[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                end = index + 1
                return text[:start] + replacement.rstrip() + text[end:]
    raise RuntimeError(f"{label}: closing brace not found")


# Legacy/pre-Inventory-V2 saves can contain a perfectly named official item without the
# catalog metadata that tells InventoryEngine whether it is consumable and what effects it has.
# Re-hydrate only the public catalog contract. Preserve unrelated provenance/loot metadata.
catalog = CATALOG.read_text(encoding="utf-8")
hydrator = r'''
  fun hydrateLegacyStack(stack: ItemStack): ItemStack {
    val definition = definitions[ItemDefinitionMetadata.definitionId(stack)]
      ?: definitions.values.firstOrNull { candidate ->
        candidate.name.equals(stack.name, ignoreCase = true) ||
          candidate.aliases.any { alias -> alias.equals(stack.name, ignoreCase = true) }
      }
      ?: return stack

    val catalogMetadata = linkedMapOf(
      "catalog.definitionId" to definition.id,
      "catalog.category" to definition.category.name,
      "catalog.stackMode" to definition.stackMode.name,
      "catalog.maxStack" to definition.maxStack.toString(),
      "catalog.transferable" to definition.transferable.toString(),
      "catalog.discardable" to definition.discardable.toString(),
      "catalog.effects" to definition.effects.sorted().joinToString(",")
    )
    definition.equipmentSlot?.let { catalogMetadata["catalog.equipmentSlot"] = it }
    definition.contentModel?.let { catalogMetadata["catalog.contentModel"] = it }
    definition.stateNameFull?.let { catalogMetadata["catalog.stateNameFull"] = it }
    definition.stateNameLow?.let { catalogMetadata["catalog.stateNameLow"] = it }
    definition.stateNameEmpty?.let { catalogMetadata["catalog.stateNameEmpty"] = it }
    definition.icon?.let { catalogMetadata["catalog.icon"] = it }

    return stack.copy(
      metadata = stack.metadata + catalogMetadata,
      archetypeId = definition.id
    )
  }
'''
anchor = '  fun definition(id: String): ItemDefinition? = definitions[id]\n'
if 'fun hydrateLegacyStack(stack: ItemStack)' not in catalog:
    if anchor not in catalog:
        raise RuntimeError("ItemCatalog hydration anchor not found")
    catalog = catalog.replace(anchor, anchor + hydrator, 1)
CATALOG.write_text(catalog, encoding="utf-8")

# The final Inventory V2 facade is authoritative after the patch chain. Hydrate official
# legacy stacks before intent resolution or item execution, and persist the one-time repair.
facade = FACADE.read_text(encoding="utf-8")
hydration_helpers = r'''  private fun hydrateCatalogState(state: GameState): GameState {
    val inventories = state.inventories.mapValues { (_, inventory) ->
      inventory.copy(items = inventory.items.mapValues { (_, stack) -> itemCatalog.hydrateLegacyStack(stack) })
    }
    val omnivault = state.omnivault.copy(
      storedItems = state.omnivault.storedItems.mapValues { (_, stack) -> itemCatalog.hydrateLegacyStack(stack) }
    )
    return state.copy(inventories = inventories, omnivault = omnivault)
  }

  private fun loadOrMigrate(legacy: JSONObject): GameState {
    val existed = repository.exists()
    val loaded = if (existed) repository.load() else GameStateCodec.decode(legacy)
    val hydrated = hydrateCatalogState(loaded)
    if (!existed || hydrated != loaded) repository.save(hydrated)
    return hydrated
  }'''
if 'private fun hydrateCatalogState(state: GameState)' not in facade:
    facade = replace_method(
        facade,
        '  private fun loadOrMigrate(legacy: JSONObject): GameState {',
        hydration_helpers,
        'GameCoreFacade catalog hydration',
    )
FACADE.write_text(facade, encoding="utf-8")

# Expose only a small UI-safe item contract. Raw metadata remains private.
serializer = SERIALIZER.read_text(encoding="utf-8")
old_item_tail = '''        stack.condition?.let { put("state", it) }
        put("contentState", stack.contentState.name)
'''
new_item_tail = '''        stack.condition?.let { put("state", it) }
        put("contentState", stack.contentState.name)
        put("definitionId", ItemDefinitionMetadata.definitionId(stack))
        stack.metadata["catalog.category"]?.let { put("category", it) }
        put("effects", JSONArray(ItemDefinitionMetadata.effects(stack).sorted()))
        stack.metadata["catalog.icon"]?.let { put("icon", it) }
        put("canUse", stack.metadata["catalog.category"].equals("CONSUMABLE", true) || ItemDefinitionMetadata.effects(stack).isNotEmpty())
'''
serializer = replace_once(serializer, old_item_tail, new_item_tail, "Character item UI projection")
SERIALIZER.write_text(serializer, encoding="utf-8")

# Make Character Detail inventory items interactive. The UI never mutates Inventory itself;
# DÙNG routes through the existing Android.submitTurn -> Game Core authority path.
html = INDEX.read_text(encoding="utf-8")
old_inventory_section = '<div class="character-section"><h3>Inventory</h3><div class="chips" id="characterInventoryItems"></div></div>\n</div>'
new_inventory_section = '''<div class="character-section"><h3>Inventory</h3><div class="chips" id="characterInventoryItems"></div></div>
  <div class="character-section character-item-detail" id="characterItemDetail" hidden>
    <div class="character-item-detail-head"><div><div class="eyebrow">ITEM DETAIL</div><h3 id="characterItemDetailName">Vật phẩm</h3></div><button type="button" id="characterItemDetailClose">Đóng</button></div>
    <img id="characterItemDetailIcon" class="character-item-detail-icon" alt="" hidden>
    <div class="character-item-detail-body" id="characterItemDetailBody"></div>
    <button type="button" id="characterItemUse">DÙNG</button>
  </div>
</div>'''
html = replace_once(html, old_inventory_section, new_inventory_section, "Character item detail panel")

css_marker = '/* ISSUE406_ITEM_INTERACTION */'
css = '''/* ISSUE406_ITEM_INTERACTION */
#characterInventoryItems .inventory-item-chip{border:1px solid #313940;background:#111519;color:#dce2e5;padding:9px;text-align:left;cursor:pointer;font-size:12px}
#characterInventoryItems .inventory-item-chip:active{transform:scale(.98)}
.character-item-detail[hidden]{display:none}.character-item-detail-head{display:flex;justify-content:space-between;align-items:flex-start;gap:12px}.character-item-detail-head h3{margin:4px 0 0}.character-item-detail-head button{width:auto;padding:8px 10px}.character-item-detail-icon{width:72px;height:72px;object-fit:contain;image-rendering:auto;margin:12px 0;border:1px solid #313940;background:#0b0e11}.character-item-detail-body{display:grid;gap:7px;margin:10px 0 12px}.character-item-detail-row{display:grid;grid-template-columns:100px 1fr;gap:8px;border-bottom:1px solid #22282d;padding:6px 0}.character-item-detail-row b{color:#78838c;font-weight:400}.character-item-detail #characterItemUse{width:100%}
'''
if css_marker not in html:
    if '</style>' not in html:
        raise RuntimeError("Character item CSS anchor not found")
    html = html.replace('</style>', css + '</style>', 1)

old_renderer = '''  function renderInventoryItems(inv){
    if(!Array.isArray(inv)||!inv.length)return '<span>Trống.</span>';
    return inv.map(x=>{
      const qty=x&&typeof x==='object'&&Number(x.quantity)>1?' ×'+Number(x.quantity):'';
      return '<span>'+esc(displayItemName(x)+qty)+'</span>';
    }).join('');
  }
'''
new_renderer = '''  function renderInventoryItems(inv){
    if(!Array.isArray(inv)||!inv.length)return '<span>Trống.</span>';
    return inv.map((x,index)=>{
      const qty=x&&typeof x==='object'&&Number(x.quantity)>1?' ×'+Number(x.quantity):'';
      return '<button type="button" class="inventory-item-chip" data-item-index="'+index+'">'+esc(displayItemName(x)+qty)+'</button>';
    }).join('');
  }
'''
html = replace_once(html, old_renderer, new_renderer, "Clickable inventory renderer")

interaction_js = r'''  function itemEffectLabel(raw){
    const effect=String(raw||'').toUpperCase();
    let m=effect.match(/^HP\+(\d+)$/);if(m)return 'Hồi '+m[1]+' HP';
    m=effect.match(/^FOOD\+(\d+)$/);if(m)return 'Hồi đói '+m[1]+'%';
    m=effect.match(/^WATER\+(\d+)$/);if(m)return 'Hồi khát '+m[1]+'%';
    m=effect.match(/^REST\+(\d+)$/);if(m)return 'Hồi thể lực/nghỉ '+m[1]+'%';
    if(effect==='CLEAR_BLEED')return 'Cầm chảy máu';
    if(effect==='CLEAR_MILD_SICKNESS')return 'Loại bỏ bệnh nhẹ';
    if(effect==='FOOD')return 'Ăn/uống: đặt lại bộ đếm đói';
    if(effect==='WATER')return 'Uống: đặt lại bộ đếm khát';
    return effect||'—';
  }
  function memberInventory(member){return Array.isArray(member&&member.inventory)?member.inventory:(member&&member.id==='kai'?kaiItems():[])}
  function showItemDetail(member,item){
    const panel=document.getElementById('characterItemDetail');
    const nameEl=document.getElementById('characterItemDetailName');
    const body=document.getElementById('characterItemDetailBody');
    const icon=document.getElementById('characterItemDetailIcon');
    const useButton=document.getElementById('characterItemUse');
    if(!panel||!nameEl||!body||!useButton||!item)return;
    nameEl.textContent=displayItemName(item);
    const effects=Array.isArray(item.effects)?item.effects:[];
    const rows=[
      ['Số lượng',String(Number(item.quantity)||1)],
      ['Loại',String(item.category||'Chưa xác định')],
      ['Trạng thái',String(item.contentState||item.state||'—')],
      ['Hiệu ứng',effects.length?effects.map(itemEffectLabel).join(' • '):'Core sẽ xác nhận khi dùng']
    ];
    body.innerHTML=rows.map(row=>'<div class="character-item-detail-row"><b>'+esc(row[0])+'</b><span>'+esc(row[1])+'</span></div>').join('');
    if(icon){if(item.icon){icon.src=String(item.icon);icon.alt=displayItemName(item);icon.hidden=false}else{icon.hidden=true;icon.removeAttribute('src')}}
    useButton.disabled=item.canUse===false;
    useButton.textContent=useButton.disabled?'KHÔNG THỂ DÙNG':'DÙNG';
    useButton.onclick=function(){
      if(useButton.disabled)return;
      if(!window.Android||typeof Android.submitTurn!=='function'){
        const status=document.getElementById('status');if(status)status.textContent='Không tìm thấy Android Game Core bridge.';return;
      }
      const actor=String((member&&member.name)||(member&&member.id)||'Kai');
      const itemName=String(item.name||item.displayName||item.id||item.itemId||'vật phẩm');
      useButton.disabled=true;
      const status=document.getElementById('status');if(status)status.textContent=actor+' đang dùng '+displayItemName(item)+'…';
      Android.submitTurn(JSON.stringify(state),actor+' dùng '+itemName);
    };
    panel.hidden=false;
  }
  if(items)items.addEventListener('click',function(event){
    const button=event.target.closest&&event.target.closest('.inventory-item-chip');
    if(!button)return;
    const member=memberById(selectedCharacterId);
    const inv=memberInventory(member);
    const index=Number(button.getAttribute('data-item-index'));
    if(Number.isInteger(index)&&index>=0&&index<inv.length)showItemDetail(member,inv[index]);
  });
  const itemDetailClose=document.getElementById('characterItemDetailClose');
  if(itemDetailClose)itemDetailClose.addEventListener('click',function(){const panel=document.getElementById('characterItemDetail');if(panel)panel.hidden=true});
'''
if 'function showItemDetail(member,item)' not in html:
    anchor = "  if(back)back.addEventListener('click',()=>{view.hidden=true});\n"
    if anchor not in html:
        raise RuntimeError("Character item interaction JS anchor not found")
    html = html.replace(anchor, interaction_js + anchor, 1)

for token in [
    'id="characterItemDetail"',
    'class="inventory-item-chip"',
    'function showItemDetail(member,item)',
    "Android.submitTurn(JSON.stringify(state),actor+' dùng '+itemName)",
    'ISSUE406_ITEM_INTERACTION',
]:
    if token not in html:
        raise RuntimeError(f"Issue #406 UI contract missing: {token}")
INDEX.write_text(html, encoding="utf-8")

# Regression: a metadata-free legacy Agrugua stack must be catalog-hydrated, consumed,
# and apply HP+30. This reproduces the user-visible failure instead of testing only fresh loot.
test = TEST.read_text(encoding="utf-8")
regression = r'''

  @Test fun legacyAgruguaHydrationRestoresConsumptionAndHpEffect() {
    val catalog = ItemCatalog.fromJson("""{
      "schemaVersion":1,
      "items":[{
        "id":"agrugua-fruit",
        "name":"Agrugua Fruit",
        "category":"CONSUMABLE",
        "stackMode":"STACK",
        "maxStack":9999,
        "transferable":true,
        "discardable":true,
        "effects":["HP+30"],
        "aliases":["agrugua","quả agrugua"]
      }]
    }""")
    val legacy = ItemStack(
      itemId = "agrugua-fruit",
      name = "AGRUGUA FRUIT",
      quantity = 1,
      metadata = mapOf("migrated" to "legacy-v0")
    )
    val hydrated = catalog.hydrateLegacyStack(legacy)
    assertEquals("CONSUMABLE", hydrated.metadata["catalog.category"])
    assertEquals("HP+30", hydrated.metadata["catalog.effects"])

    val base = GameState.initial()
    val kai = CombatProgression.write(
      base.characters.getValue(KAI_ID),
      CombatProgression.read(base.characters.getValue(KAI_ID)).copy(currentHp = 10)
    )
    val state = base.copy(
      characters = base.characters + (KAI_ID to kai),
      inventories = base.inventories + (KAI_ID to InventoryState(KAI_ID, mapOf(hydrated.itemId to hydrated)))
    )
    val result = use(state, hydrated.itemId)

    assertTrue(result.validation.reason, result.applied)
    assertEquals(40, CombatProgression.read(result.state.characters.getValue(KAI_ID)).currentHp)
    assertFalse(result.state.inventories.getValue(KAI_ID).items.containsKey("agrugua-fruit"))
  }
'''
if 'legacyAgruguaHydrationRestoresConsumptionAndHpEffect' not in test:
    closing = test.rfind('\n}')
    if closing < 0:
        raise RuntimeError("ConsumableCatalogEffectsTest closing brace not found")
    test = test[:closing] + regression + test[closing:]
TEST.write_text(test, encoding="utf-8")

for path, token in [
    (CATALOG, 'fun hydrateLegacyStack(stack: ItemStack)'),
    (FACADE, 'private fun hydrateCatalogState(state: GameState)'),
    (SERIALIZER, 'put("canUse"'),
    (TEST, 'legacyAgruguaHydrationRestoresConsumptionAndHpEffect'),
]:
    if token not in path.read_text(encoding="utf-8"):
        raise RuntimeError(f"Issue #406 final contract missing in {path.name}: {token}")

print("Issue #406 fixed: legacy official items regain catalog semantics, Agrugua consumes/applies HP+30, and Character Detail supports item inspection/use through Game Core.")
