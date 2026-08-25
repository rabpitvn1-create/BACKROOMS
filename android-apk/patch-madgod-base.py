from pathlib import Path

R=Path(__file__).resolve().parent
C=R/'app/src/main/java/com/rabpit/backroom/core'
S=C/'GameState.kt'; M=C/'MadGodCanon.kt'; Q=C/'CommandPipeline.kt'; E=C/'Engines.kt'; V=C/'OmnivaultEngine.kt'; F=C/'GameCoreFacade.kt'; A=R/'app/src/main/java/com/rabpit/backroom/MainActivity.java'; T=R/'app/src/test/java/com/rabpit/backroom/core/MadGodEquipmentTest.kt'

def one(s,a,b,n):
    if b in s:return s
    if s.count(a)!=1:raise RuntimeError(f'{n}: {s.count(a)}')
    return s.replace(a,b,1)

s=S.read_text(encoding='utf-8')
a='  const val RING_NAME = "Omnivault Ring"\n'
b=a+'  const val WW_MAGNUM_DMG = 500\n  const val BLACKBLOOD_DF = 500\n  const val BLACKBLOOD_STR = 100\n  const val BLACKBLOOD_AGI = 100\n  const val BLACKBLOOD_HP = 100\n  const val BLACKBLOOD_ENE = 100\n  const val BLACKBLOOD_CRIT = 100\n'
s=one(s,a,b,'baseline');S.write_text(s,encoding='utf-8')

M.write_text('''package com.rabpit.backroom.core

import org.json.JSONObject

const val MADGOD_MAGNUM_ID = "madgod:magnum"
const val MADGOD_ARMOR_ID = "madgod:armor"

object MadGodCanon {
  const val CHEAT_CODE = "/madgod"
  const val MULTIPLIER = 50
  const val SCALING_MODE = "BASELINE_ONCE"
  const val MAGNUM_RPM = 600
  const val MAGNUM_DMG = KaiStartingEquipment.WW_MAGNUM_DMG * MULTIPLIER
  const val ARMOR_DF = KaiStartingEquipment.BLACKBLOOD_DF * MULTIPLIER
  const val ARMOR_STR = KaiStartingEquipment.BLACKBLOOD_STR * MULTIPLIER
  const val ARMOR_AGI = KaiStartingEquipment.BLACKBLOOD_AGI * MULTIPLIER
  const val ARMOR_HP = KaiStartingEquipment.BLACKBLOOD_HP * MULTIPLIER
  const val ARMOR_ENE = KaiStartingEquipment.BLACKBLOOD_ENE * MULTIPLIER
  const val ARMOR_CRIT = KaiStartingEquipment.BLACKBLOOD_CRIT * MULTIPLIER
  data class Spawn(val state: GameState,val added:Boolean)
  fun cheat(x:String)=x.trim().equals(CHEAT_CODE,true)
  fun isId(x:String?)=x==MADGOD_MAGNUM_ID||x==MADGOD_ARMOR_ID
  fun isItem(x:ItemStack?)=x!=null&&(isId(x.itemId)||x.metadata["madGod"].equals("true",true))
  fun slot(id:String,name:String)=when { id==MADGOD_MAGNUM_ID||name.contains("MadGod Magnum",true)->"weapon"; id==MADGOD_ARMOR_ID||name.contains("MadGod Armor",true)->"armor"; else->null }
  fun weapon()=ItemStack(MADGOD_MAGNUM_ID,"MadGod Magnum",1,"PERFECT",mapOf(
    "category" to "weapon","slot" to "weapon","rarity" to "UR+ UNIQUE","madGod" to "true","kaiOnly" to "true","permanentAfterEquip" to "true","omnivaultCopyable" to "false",
    "baseDMG" to KaiStartingEquipment.WW_MAGNUM_DMG.toString(),"multiplier" to "50","scalingMode" to SCALING_MODE,"userStatMultiplier" to "false","stackMultiplier" to "false",
    "DMG" to MAGNUM_DMG.toString(),"ammo" to "infinite","ammoSource" to "Sparda Core","fireModes" to "single,full_auto","RPM" to MAGNUM_RPM.toString()))
  fun armor()=ItemStack(MADGOD_ARMOR_ID,"MadGod Armor",1,"PERFECT",mapOf(
    "category" to "armor","slot" to "armor","rarity" to "UR+ UNIQUE","madGod" to "true","kaiOnly" to "true","permanentAfterEquip" to "true","omnivaultCopyable" to "false",
    "baseDF" to KaiStartingEquipment.BLACKBLOOD_DF.toString(),"multiplier" to "50","scalingMode" to SCALING_MODE,"userStatMultiplier" to "false","stackMultiplier" to "false",
    "DF" to ARMOR_DF.toString(),"STR" to ARMOR_STR.toString(),"AGI" to ARMOR_AGI.toString(),"HP" to ARMOR_HP.toString(),"ENE" to ARMOR_ENE.toString(),"CRIT" to ARMOR_CRIT.toString(),"functions" to "Blackblood Armor equivalent functions"))
  fun spawn(s:GameState):Spawn {
    val ids=s.inventories.values.flatMap{it.items.keys}+s.omnivault.storedItems.keys+s.equipment.values.flatMap{it.slots.values}
    if(s.metadata["madGod.spawned"].equals("true",true)||ids.any{isId(it)})return Spawn(s.copy(metadata=s.metadata+("madGod.spawned" to "true")),false)
    val inv=s.inventories[KAI_ID]?:InventoryState(KAI_ID)
    return Spawn(s.copy(inventories=s.inventories+(KAI_ID to inv.copy(items=inv.items+mapOf(MADGOD_MAGNUM_ID to weapon(),MADGOD_ARMOR_ID to armor()))),metadata=s.metadata+mapOf("madGod.spawned" to "true","madGod.spawnSource" to "cheat","madGod.multiplierMode" to SCALING_MODE)),true)
  }
  fun legacy(s:GameState)=JSONObject().apply {
    val z=s.equipment[KAI_ID]?.slots.orEmpty()
    listOf("weapon","armor","ring").forEach { k-> val id=z[k]?:return@forEach; val st=JSONObject();
      when(id){MADGOD_MAGNUM_ID->st.put("DMG",MAGNUM_DMG).put("RPM",MAGNUM_RPM).put("ammo","infinite");MADGOD_ARMOR_ID->st.put("DF",ARMOR_DF).put("STR",ARMOR_STR).put("AGI",ARMOR_AGI).put("HP",ARMOR_HP).put("ENE",ARMOR_ENE).put("CRIT",ARMOR_CRIT)}
      put(k,JSONObject().put("id",id).put("name",when(id){MADGOD_MAGNUM_ID->"MadGod Magnum";MADGOD_ARMOR_ID->"MadGod Armor";else->KaiStartingEquipment.displayName(id)?:id}).put("permanent",isId(id)).put("stats",st).put("scalingMode",if(isId(id))SCALING_MODE else "BASE")) }
  }
}
''',encoding='utf-8')

q=Q.read_text(encoding='utf-8')
q=one(q,'      GameIntent.EQUIP_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.EQUIP, it, quantity, "weapon") }\n','      GameIntent.EQUIP_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.EQUIP, it, quantity, equipmentSlot(it)) }\n','equip')
q=one(q,'      GameIntent.UNEQUIP_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.UNEQUIP, it, quantity, "weapon") }\n','      GameIntent.UNEQUIP_ITEM -> item?.let { itemCommand(commandId, turnId, actor, target, source, ItemCommand.Operation.UNEQUIP, it, quantity, equipmentSlot(it)) }\n','unequip')
h='''  private fun equipmentSlot(item: Pair<String,String>): String = MadGodCanon.slot(item.first,item.second) ?: KaiStartingEquipment.slotFor(item.first,item.second) ?: if ((item.first+" "+item.second).contains("armor",true)||(item.first+" "+item.second).contains("giáp",true)) "armor" else "weapon"\n\n'''
q=one(q,'  private fun itemCommand(id: String, turn: String, actor: String, target: String?, source: CommandSource, operation: ItemCommand.Operation, item: Pair<String, String>, quantity: Int, slot: String? = null) =\n',h+'  private fun itemCommand(id: String, turn: String, actor: String, target: String?, source: CommandSource, operation: ItemCommand.Operation, item: Pair<String, String>, quantity: Int, slot: String? = null) =\n','slot helper');Q.write_text(q,encoding='utf-8')

e=E.read_text(encoding='utf-8')
e=one(e,'      ItemCommand.Operation.DROP -> {\n        val next = removeItem(source, command.itemId, command.quantity) ?: return invalid(state, "insufficient_item_quantity")\n','      ItemCommand.Operation.DROP -> {\n        if (MadGodCanon.isId(command.itemId) && state.equipment[command.actorId]?.slots?.values?.contains(command.itemId)==true) return invalid(state, "madgod_equipment_permanent")\n        val next = removeItem(source, command.itemId, command.quantity) ?: return invalid(state, "insufficient_item_quantity")\n','drop')
old='''      ItemCommand.Operation.EQUIP -> {
        if ((source.items[command.itemId]?.quantity ?: 0) < command.quantity) return invalid(state, "item_not_owned")
        val slot = command.slot ?: return invalid(state, "equipment_slot_required")
        val equipment = state.equipment[command.actorId] ?: EquipmentState(command.actorId)
        changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots + (slot to command.itemId)))), "item_equipped")
      }
      ItemCommand.Operation.UNEQUIP -> {
        val slot = command.slot ?: return invalid(state, "equipment_slot_required")
        val equipment = state.equipment[command.actorId] ?: return invalid(state, "equipment_missing")
        if (equipment.slots[slot] != command.itemId) return invalid(state, "item_not_equipped")
        changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots - slot))), "item_unequipped")
      }
'''
new='''      ItemCommand.Operation.EQUIP -> {
        if ((source.items[command.itemId]?.quantity ?: 0) < command.quantity) return invalid(state, "item_not_owned")
        val slot = command.slot ?: return invalid(state, "equipment_slot_required")
        val equipment = state.equipment[command.actorId] ?: EquipmentState(command.actorId)
        val current=equipment.slots[slot]
        if (MadGodCanon.isId(current) && current!=command.itemId) return invalid(state,"madgod_equipment_permanent")
        if (MadGodCanon.isId(command.itemId) && (command.actorId!=KAI_ID || MadGodCanon.slot(command.itemId,source.items[command.itemId]?.name.orEmpty())!=slot)) return invalid(state,"madgod_equipment_slot_mismatch")
        changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots + (slot to command.itemId)))), "item_equipped")
      }
      ItemCommand.Operation.UNEQUIP -> {
        val slot = command.slot ?: return invalid(state, "equipment_slot_required")
        val equipment = state.equipment[command.actorId] ?: return invalid(state, "equipment_missing")
        if (equipment.slots[slot] != command.itemId) return invalid(state, "item_not_equipped")
        if (MadGodCanon.isId(command.itemId)) return invalid(state,"madgod_equipment_permanent")
        changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots - slot))), "item_unequipped")
      }
'''
e=one(e,old,new,'equip engine');E.write_text(e,encoding='utf-8')

v=V.read_text(encoding='utf-8')
v=one(v,'    val source = state.inventories[c.actorId]?.items?.get(c.itemId) ?: state.omnivault.storedItems[c.itemId] ?: return invalid(state, "scan_source_missing")\n','    val source = state.inventories[c.actorId]?.items?.get(c.itemId) ?: state.omnivault.storedItems[c.itemId] ?: return invalid(state, "scan_source_missing")\n    if (MadGodCanon.isItem(source)) return invalid(state,"madgod_omnivault_copy_forbidden")\n','scan')
v=one(v,'    val template = state.omnivault.scanSlots.firstOrNull { it.templateItem.itemId == c.itemId }?.templateItem ?: return invalid(state, "scan_template_missing")\n','    val template = state.omnivault.scanSlots.firstOrNull { it.templateItem.itemId == c.itemId }?.templateItem ?: return invalid(state, "scan_template_missing")\n    if (MadGodCanon.isItem(template)) return invalid(state,"madgod_omnivault_copy_forbidden")\n','copy');V.write_text(v,encoding='utf-8')

f=F.read_text(encoding='utf-8')
f=one(f,'    val legacy = JSONObject(legacyStateJson)\n    val state = loadOrMigrate(legacy)\n    val turnId = nextTurnId(legacy, state)\n','    val legacy = JSONObject(legacyStateJson)\n    val state = loadOrMigrate(legacy)\n    if (MadGodCanon.cheat(action)) return applyMadGodCheat(legacy,state)\n    val turnId = nextTurnId(legacy, state)\n','cheat rule')
f=one(f,'    val legacy = JSONObject(legacyStateJson)\n    val state = loadOrMigrate(legacy)\n    val kind = enumValues<ActionKind>().firstOrNull { it.name == kindRaw.trim().uppercase() }\n','    val legacy = JSONObject(legacyStateJson)\n    val state = loadOrMigrate(legacy)\n    if (MadGodCanon.cheat(action)) return actionStartResponse(true,null,null)\n    val kind = enumValues<ActionKind>().firstOrNull { it.name == kindRaw.trim().uppercase() }\n','cheat begin')
handler='''  private fun applyMadGodCheat(legacy:JSONObject,state:GameState):String {\n    val x=MadGodCanon.spawn(state); repository.save(x.state); val out=syncLegacy(legacy,x.state,incrementTurn=false);\n    val flags=out.optJSONObject("flags")?:JSONObject().also{out.put("flags",it)}; flags.put("madGod",JSONObject().put("spawned",true).put("spawnSource","cheat").put("scalingMode",MadGodCanon.SCALING_MODE));\n    val msg=if(x.added) "MadGod Armor và MadGod Magnum đã xuất hiện trong Inventory. Sau khi trang bị, chúng khóa vĩnh viễn vào slot." else "MadGod Set đã tồn tại; /madgod không tạo bản sao thứ hai."; appendLog(out,MadGodCanon.CHEAT_CODE,msg); return response(true,out,null,"cheat_committed",msg)\n  }\n\n'''
f=one(f,'  fun beginAction(legacyStateJson: String, kindRaw: String, action: String): String {\n',handler+'  fun beginAction(legacyStateJson: String, kindRaw: String, action: String): String {\n','handler')
f=one(f,'    output.put("partyDetails", CharacterDetailJson.encodeParty(CharacterDetailProjector.projectParty(state)))\n','    output.put("partyDetails", CharacterDetailJson.encodeParty(CharacterDetailProjector.projectParty(state)))\n    output.put("equipment",MadGodCanon.legacy(state))\n','legacy equipment')
f=one(f,'      "scan_source_missing", "scan_template_missing" -> "There is no object available for scanning or multiplying."\n','      "madgod_equipment_permanent" -> "MadGod đã khóa vĩnh viễn sau khi trang bị; không thể tháo hoặc đổi."\n      "madgod_omnivault_copy_forbidden" -> "Omnivault không thể quét hoặc sao chép MadGod Set."\n      "madgod_equipment_slot_mismatch" -> "MadGod chỉ có thể trang bị cho Kai vào đúng slot."\n      "scan_source_missing", "scan_template_missing" -> "There is no object available for scanning or multiplying."\n','replies');F.write_text(f,encoding='utf-8')

a=A.read_text(encoding='utf-8')
a=one(a,'.snapshot-placeholder{display:none}', '.snapshot-placeholder{display:none}.snapshot .snapshot-equipment-badge{position:absolute;right:8px;top:8px;z-index:4;padding:6px 8px;border:1px solid rgba(218,180,88,.62);border-radius:8px;background:rgba(7,9,11,.78);color:#f2dfad;font-size:10px;pointer-events:none}.snapshot .snapshot-equipment-badge b{display:block}', 'css')
default='BestKai.png'
js="function equippedItem(s){try{return state&&state.equipment&&state.equipment[s]?state.equipment[s]:null}catch(e){return null}}function madGodEquipped(s){var x=equippedItem(s);return !!(x&&String(x.id||'').indexOf('madgod:')===0)}function kaiOverlaySource(){var a=madGodEquipped('armor'),w=madGodEquipped('weapon');if(a&&w)return 'kai_snapshot_overlay_madgod.webp';if(a)return 'kai_snapshot_overlay_madgod_armor.webp';if(w)return 'kai_snapshot_overlay_madgod_magnum.webp';return '"+default+"'}function appendEquipmentBadge(b){if(!madGodEquipped('armor')&&!madGodEquipped('weapon'))return;var d=document.createElement('div');d.className='snapshot-equipment-badge';var a=equippedItem('armor'),w=equippedItem('weapon');d.textContent=[a&&madGodEquipped('armor')?a.name:'',w&&madGodEquipped('weapon')?w.name:''].filter(Boolean).join(' • ');b.appendChild(d)}"
a=one(a,'function cachedSnapshot(){',js+'function cachedSnapshot(){','js')
for oldimg in ["kai.src='BestKai.png';kai.alt='Kai Akechi';box.appendChild(kai);","kai.src='file:///android_asset/BestKai.png';kai.alt='Kai Akechi';box.appendChild(kai);","kai.src='kai_snapshot_overlay.png';kai.alt='Kai Akechi';box.appendChild(kai);","kai.src='kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);"]:
    if oldimg in a:
        a=a.replace(oldimg,"kai.src=kaiOverlaySource();kai.onerror=function(){this.onerror=null;this.src='"+default+"'};kai.alt='Kai Akechi';box.appendChild(kai);appendEquipmentBadge(box);",1);break
else: raise RuntimeError('overlay image anchor')
A.write_text(a,encoding='utf-8')

T.parent.mkdir(parents=True,exist_ok=True)
T.write_text('''package com.rabpit.backroom.core\nimport kotlin.test.*\nclass MadGodEquipmentTest {\n@Test fun stats(){assertEquals(25000,MadGodCanon.MAGNUM_DMG);assertEquals(25000,MadGodCanon.ARMOR_DF);assertEquals(5000,MadGodCanon.ARMOR_STR);assertEquals("BASELINE_ONCE",MadGodCanon.SCALING_MODE);assertEquals("infinite",MadGodCanon.weapon().metadata["ammo"]);assertEquals("600",MadGodCanon.weapon().metadata["RPM"])}\n@Test fun unique(){val a=MadGodCanon.spawn(GameState.initial());assertTrue(a.added);assertFalse(MadGodCanon.spawn(a.state).added)}\n@Test fun locked(){val s=MadGodCanon.spawn(GameState.initial()).state;val e=InventoryEngine.execute(s,ItemCommand("e","TURN_1",KAI_ID,source=CommandSource.UI,operation=ItemCommand.Operation.EQUIP,itemId=MADGOD_ARMOR_ID,itemName="MadGod Armor",slot="armor"));assertTrue(e.applied);val u=InventoryEngine.execute(e.state,ItemCommand("u","TURN_1",KAI_ID,source=CommandSource.UI,operation=ItemCommand.Operation.UNEQUIP,itemId=MADGOD_ARMOR_ID,itemName="MadGod Armor",slot="armor"));assertFalse(u.applied)}\n}\n''',encoding='utf-8')

alltxt='\n'.join(p.read_text(encoding='utf-8') for p in [S,M,Q,E,V,F,A,T])
for x in ['/madgod','BASELINE_ONCE','MAGNUM_DMG','ammoSource" to "Sparda Core','madgod_equipment_permanent','madgod_omnivault_copy_forbidden','output.put("equipment",MadGodCanon.legacy(state))','kaiOverlaySource()']:
    if x not in alltxt:raise RuntimeError('contract '+x)
print('MadGod R2 patch applied')
