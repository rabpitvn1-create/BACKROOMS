from pathlib import Path

base = Path(__file__).resolve().parent / "patch-madgod-base.py"
src = base.read_text(encoding="utf-8")

# The final three-action runtime rewrites GameCoreFacade immediately before MadGod is applied.
# Replace brittle exact-block patches in the historical base script with scoped method insertions.
lines = src.splitlines()
for i, line in enumerate(lines):
    if "'cheat rule')" in line and line.lstrip().startswith("f=one("):
        lines[i] = r'''process_marker='  fun processRule(legacyStateJson: String, action: String): String {\n'
process_pos=f.find(process_marker)
if process_pos < 0: raise RuntimeError('MadGod processRule method missing')
state_anchor='    val state = loadOrMigrate(legacy)\n'
state_pos=f.find(state_anchor,process_pos)
if state_pos < 0: raise RuntimeError('MadGod processRule state anchor missing')
insert_at=state_pos+len(state_anchor)
process_end=f.find('\n  fun ',insert_at)
if process_end < 0: process_end=len(f)
if 'MadGodCanon.cheat(action)' not in f[process_pos:process_end]:
    f=f[:insert_at]+'    if (MadGodCanon.cheat(action)) return applyMadGodCheat(legacy,state)\n'+f[insert_at:]'''
    if "'cheat begin')" in line and line.lstrip().startswith("f=one("):
        lines[i] = r'''begin_marker='  fun beginAction(legacyStateJson: String, kindRaw: String, action: String): String {\n'
begin_pos=f.find(begin_marker)
if begin_pos < 0: raise RuntimeError('MadGod beginAction method missing')
state_anchor='    val state = loadOrMigrate(legacy)\n'
begin_state=f.find(state_anchor,begin_pos)
if begin_state < 0: raise RuntimeError('MadGod beginAction state anchor missing')
insert_at=begin_state+len(state_anchor)
begin_end=f.find('\n  fun ',insert_at)
if begin_end < 0: begin_end=len(f)
if 'MadGodCanon.cheat(action)' not in f[begin_pos:begin_end]:
    f=f[:insert_at]+'    if (MadGodCanon.cheat(action)) return actionStartResponse(true,null,null)\n'+f[insert_at:]'''
src = "\n".join(lines) + "\n"

# The generated InventoryEngine is stable at the individual mutation lines, not as one giant block.
needle = "e=one(e,old,new,'equip engine');E.write_text(e,encoding='utf-8')"
replacement = r'''equip_line='        changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots + (slot to command.itemId)))), "item_equipped")\n'
equip_new=''' + "'''" + r'''        val current=equipment.slots[slot]
        if (MadGodCanon.isId(current) && current!=command.itemId) return invalid(state,"madgod_equipment_permanent")
        if (MadGodCanon.isId(command.itemId) && (command.actorId!=KAI_ID || MadGodCanon.slot(command.itemId,source.items[command.itemId]?.name.orEmpty())!=slot)) return invalid(state,"madgod_equipment_slot_mismatch")
        changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots + (slot to command.itemId)))), "item_equipped")
''' + "'''" + r'''
e=one(e,equip_line,equip_new,'equip engine line')
unequip_line='        changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots - slot))), "item_unequipped")\n'
unequip_new=''' + "'''" + r'''        if (MadGodCanon.isId(command.itemId)) return invalid(state,"madgod_equipment_permanent")
        changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots - slot))), "item_unequipped")
''' + "'''" + r'''
e=one(e,unequip_line,unequip_new,'unequip engine line')
E.write_text(e,encoding='utf-8')'''
if needle not in src:
    raise RuntimeError("MadGod base wrapper engine anchor missing")
src = src.replace(needle, replacement, 1)

exec(compile(src, str(base), "exec"), {"__name__": "__main__", "__file__": str(base)})

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
MADGOD = CORE / "MadGodCanon.kt"
ENGINES = CORE / "Engines.kt"
FACADE = CORE / "GameCoreFacade.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/MadGodEquipmentTest.kt"

# Canon correction: Armor + Magnum are components of ONE inventory/equipment set, never two items.
MADGOD.write_text(r'''package com.rabpit.backroom.core

import org.json.JSONObject

const val MADGOD_SET_ID = "madgod:set"

object MadGodCanon {
  const val CHEAT_CODE = "/madgod"
  const val SET_NAME = "MadGod Set"
  const val MULTIPLIER = 50
  const val SCALING_MODE = "BASELINE_ONCE"
  const val MAGNUM_RPM = 600
  const val AVATAR_ASSET = "avatars/MadGod.jpg"
  const val SNAPSHOT_OVERLAY_ASSET = "Kai_MadGod_snapshot_overlay.png"
  private const val LEGACY_MAGNUM_ID = "madgod:magnum"
  private const val LEGACY_ARMOR_ID = "madgod:armor"

  const val MAGNUM_DMG = KaiStartingEquipment.WW_MAGNUM_DMG * MULTIPLIER
  const val ARMOR_DF = KaiStartingEquipment.BLACKBLOOD_DF * MULTIPLIER
  const val ARMOR_STR = KaiStartingEquipment.BLACKBLOOD_STR * MULTIPLIER
  const val ARMOR_AGI = KaiStartingEquipment.BLACKBLOOD_AGI * MULTIPLIER
  const val ARMOR_HP = KaiStartingEquipment.BLACKBLOOD_HP * MULTIPLIER
  const val ARMOR_ENE = KaiStartingEquipment.BLACKBLOOD_ENE * MULTIPLIER
  const val ARMOR_CRIT = KaiStartingEquipment.BLACKBLOOD_CRIT * MULTIPLIER

  data class Spawn(val state: GameState, val added: Boolean)

  fun cheat(x: String) = x.trim().equals(CHEAT_CODE, true)
  fun isSetId(x: String?) = x == MADGOD_SET_ID
  fun isLegacyId(x: String?) = x == LEGACY_MAGNUM_ID || x == LEGACY_ARMOR_ID
  fun isId(x: String?) = isSetId(x) || isLegacyId(x)
  fun isItem(x: ItemStack?) = x != null && (isId(x.itemId) || x.metadata["madGod"].equals("true", true))
  fun slot(id: String, name: String) = if (isId(id) || name.contains(SET_NAME, true) || name.contains("MadGod Armor", true) || name.contains("MadGod Magnum", true)) "set" else null

  fun setItem() = ItemStack(MADGOD_SET_ID, SET_NAME, 1, "PERFECT", linkedMapOf(
    "category" to "equipment_set",
    "slot" to "set",
    "rarity" to "UR+ UNIQUE",
    "unique" to "true",
    "madGod" to "true",
    "kaiOnly" to "true",
    "permanentAfterEquip" to "true",
    "omnivaultCopyable" to "false",
    "components" to "MadGod Armor + MadGod Magnum",
    "avatarAsset" to AVATAR_ASSET,
    "snapshotOverlayAsset" to SNAPSHOT_OVERLAY_ASSET,
    "multiplier" to MULTIPLIER.toString(),
    "scalingMode" to SCALING_MODE,
    "userStatMultiplier" to "false",
    "stackMultiplier" to "false",
    "magnumBaseDMG" to KaiStartingEquipment.WW_MAGNUM_DMG.toString(),
    "magnumDMG" to MAGNUM_DMG.toString(),
    "magnumAmmo" to "infinite",
    "magnumAmmoSource" to "Sparda Core",
    "magnumFireModes" to "single,full_auto",
    "magnumRPM" to MAGNUM_RPM.toString(),
    "armorBaseDF" to KaiStartingEquipment.BLACKBLOOD_DF.toString(),
    "armorDF" to ARMOR_DF.toString(),
    "armorSTR" to ARMOR_STR.toString(),
    "armorAGI" to ARMOR_AGI.toString(),
    "armorHP" to ARMOR_HP.toString(),
    "armorENE" to ARMOR_ENE.toString(),
    "armorCRIT" to ARMOR_CRIT.toString(),
    "armorFunctions" to "Blackblood Armor equivalent functions"
  ))

  fun spawn(s: GameState): Spawn {
    val inv = s.inventories[KAI_ID] ?: InventoryState(KAI_ID)
    val equipment = s.equipment[KAI_ID] ?: EquipmentState(KAI_ID)
    val legacyInInventory = inv.items.keys.any(::isLegacyId)
    val legacyEquipped = equipment.slots.values.any(::isLegacyId)
    val setExists = inv.items.containsKey(MADGOD_SET_ID) || equipment.slots.values.any(::isSetId)

    if (legacyInInventory || legacyEquipped) {
      val migratedItems = (inv.items - LEGACY_MAGNUM_ID - LEGACY_ARMOR_ID) + (MADGOD_SET_ID to setItem())
      val migratedSlots = if (legacyEquipped) equipment.slots + mapOf("weapon" to MADGOD_SET_ID, "armor" to MADGOD_SET_ID) else equipment.slots
      return Spawn(s.copy(
        inventories = s.inventories + (KAI_ID to inv.copy(items = migratedItems)),
        equipment = s.equipment + (KAI_ID to equipment.copy(slots = migratedSlots)),
        metadata = s.metadata + mapOf("madGod.spawned" to "true", "madGod.form" to "set", "madGod.multiplierMode" to SCALING_MODE)
      ), false)
    }

    if (setExists || s.metadata["madGod.spawned"].equals("true", true)) {
      return Spawn(s.copy(metadata = s.metadata + mapOf("madGod.spawned" to "true", "madGod.form" to "set")), false)
    }

    return Spawn(s.copy(
      inventories = s.inventories + (KAI_ID to inv.copy(items = inv.items + (MADGOD_SET_ID to setItem()))),
      metadata = s.metadata + mapOf("madGod.spawned" to "true", "madGod.spawnSource" to "cheat", "madGod.form" to "set", "madGod.multiplierMode" to SCALING_MODE)
    ), true)
  }

  fun legacy(s: GameState) = JSONObject().apply {
    val slots = s.equipment[KAI_ID]?.slots.orEmpty()
    val setEquipped = slots["weapon"]?.let(::isId) == true || slots["armor"]?.let(::isId) == true
    if (setEquipped) {
      put("set", JSONObject()
        .put("id", MADGOD_SET_ID)
        .put("name", SET_NAME)
        .put("permanent", true)
        .put("avatar", AVATAR_ASSET)
        .put("snapshotOverlay", SNAPSHOT_OVERLAY_ASSET)
        .put("scalingMode", SCALING_MODE)
        .put("stats", JSONObject()
          .put("weapon", JSONObject().put("DMG", MAGNUM_DMG).put("RPM", MAGNUM_RPM).put("ammo", "infinite").put("ammoSource", "Sparda Core"))
          .put("armor", JSONObject().put("DF", ARMOR_DF).put("STR", ARMOR_STR).put("AGI", ARMOR_AGI).put("HP", ARMOR_HP).put("ENE", ARMOR_ENE).put("CRIT", ARMOR_CRIT))))
    } else {
      listOf("weapon", "armor").forEach { slot ->
        val id = slots[slot] ?: return@forEach
        put(slot, JSONObject().put("id", id).put("name", KaiStartingEquipment.displayName(id) ?: id).put("permanent", false).put("scalingMode", "BASE"))
      }
    }
    slots["ring"]?.let { id -> put("ring", JSONObject().put("id", id).put("name", KaiStartingEquipment.displayName(id) ?: id).put("permanent", false).put("scalingMode", "BASE")) }
  }
}
''', encoding="utf-8")

# A single Equip action on MadGod Set binds BOTH weapon and armor slots atomically.
engines = ENGINES.read_text(encoding="utf-8")
old_equip = '''        val current=equipment.slots[slot]
        if (MadGodCanon.isId(current) && current!=command.itemId) return invalid(state,"madgod_equipment_permanent")
        if (MadGodCanon.isId(command.itemId) && (command.actorId!=KAI_ID || MadGodCanon.slot(command.itemId,source.items[command.itemId]?.name.orEmpty())!=slot)) return invalid(state,"madgod_equipment_slot_mismatch")
        changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots + (slot to command.itemId)))), "item_equipped")
'''
new_equip = '''        if (MadGodCanon.isId(command.itemId)) {
          if (command.actorId != KAI_ID || slot != "set" || MadGodCanon.slot(command.itemId, source.items[command.itemId]?.name.orEmpty()) != "set") return invalid(state,"madgod_equipment_slot_mismatch")
          val weaponCurrent = equipment.slots["weapon"]
          val armorCurrent = equipment.slots["armor"]
          if ((MadGodCanon.isId(weaponCurrent) && weaponCurrent != command.itemId) || (MadGodCanon.isId(armorCurrent) && armorCurrent != command.itemId)) return invalid(state,"madgod_equipment_permanent")
          val boundSlots = equipment.slots + mapOf("weapon" to MADGOD_SET_ID, "armor" to MADGOD_SET_ID)
          changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = boundSlots))), "item_equipped")
        } else {
          val current = equipment.slots[slot]
          if (MadGodCanon.isId(current)) return invalid(state,"madgod_equipment_permanent")
          changed(state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = equipment.slots + (slot to command.itemId)))), "item_equipped")
        }
'''
if old_equip not in engines:
    raise RuntimeError("Unified MadGod equip anchor missing")
engines = engines.replace(old_equip, new_equip, 1)
old_unequip = '''        if (equipment.slots[slot] != command.itemId) return invalid(state, "item_not_equipped")
        if (MadGodCanon.isId(command.itemId)) return invalid(state,"madgod_equipment_permanent")
'''
new_unequip = '''        if (MadGodCanon.isId(command.itemId)) return invalid(state,"madgod_equipment_permanent")
        if (equipment.slots[slot] != command.itemId) return invalid(state, "item_not_equipped")
'''
if old_unequip not in engines:
    raise RuntimeError("Unified MadGod unequip anchor missing")
engines = engines.replace(old_unequip, new_unequip, 1)
ENGINES.write_text(engines, encoding="utf-8")

facade = FACADE.read_text(encoding="utf-8")
facade = facade.replace(
    "MadGod Armor và MadGod Magnum đã xuất hiện trong Inventory. Sau khi trang bị, chúng khóa vĩnh viễn vào slot.",
    "MadGod Set đã xuất hiện trong Inventory. Đây là một set duy nhất: trang bị một lần sẽ kích hoạt đồng thời MadGod Armor và MadGod Magnum, rồi khóa vĩnh viễn.",
)
facade = facade.replace(
    "MadGod chỉ có thể trang bị cho Kai vào đúng slot.",
    "MadGod Set chỉ có thể trang bị cho Kai; một lần Equip sẽ chiếm đồng thời slot vũ khí và giáp.",
)
FACADE.write_text(facade, encoding="utf-8")

# Snapshot: use the exact user-supplied set overlay asset. There are no partial Armor/Magnum visuals.
main = MAIN.read_text(encoding="utf-8")
old_equipped = "function equippedItem(s){try{return state&&state.equipment&&state.equipment[s]?state.equipment[s]:null}catch(e){return null}}"
new_equipped = "function equippedItem(s){try{var e=state&&state.equipment;if(!e)return null;if(e.set&&String(e.set.id||'')==='madgod:set'&&(s==='armor'||s==='weapon'))return e.set;return e[s]||null}catch(e){return null}}"
if old_equipped not in main:
    raise RuntimeError("MadGod Snapshot equipment helper anchor missing")
main = main.replace(old_equipped, new_equipped, 1)
for old_name in ("kai_snapshot_overlay_madgod.webp", "kai_snapshot_overlay_madgod_armor.webp", "kai_snapshot_overlay_madgod_magnum.webp"):
    main = main.replace(old_name, "Kai_MadGod_snapshot_overlay.png")
main = main.replace(
    "d.textContent=[a&&madGodEquipped('armor')?a.name:'',w&&madGodEquipped('weapon')?w.name:''].filter(Boolean).join(' • ');",
    "d.textContent='MadGod Set';",
)
MAIN.write_text(main, encoding="utf-8")

# Character UI: when the set is equipped, Kai's Party/detail avatar switches to avatars/MadGod.jpg.
html = INDEX.read_text(encoding="utf-8")
member_anchor = "  function detailMembers(){\n"
helper = "  function madGodSetEquipped(){try{const e=state&&state.equipment;return !!(e&&e.set&&String(e.set.id||'')==='madgod:set')}catch(ignore){return false}}\n"
if helper not in html:
    if member_anchor not in html:
        raise RuntimeError("MadGod avatar detailMembers anchor missing")
    html = html.replace(member_anchor, helper + member_anchor, 1)
old_members_return = "    if(members&&members.length)return members;"
new_members_return = "    if(members&&members.length)return members.map(m=>String(m.id)==='kai'&&madGodSetEquipped()?Object.assign({},m,{avatar:'avatars/MadGod.jpg',avatarRef:'avatars/MadGod.jpg'}):m);"
if old_members_return not in html:
    raise RuntimeError("MadGod avatar party member anchor missing")
html = html.replace(old_members_return, new_members_return, 1)
old_fallback = "const fallback=[{id:'kai',name:'Kai Akechi',avatar:'avatars/kai_avatar.png'"
new_fallback = "const fallback=[{id:'kai',name:'Kai Akechi',avatar:madGodSetEquipped()?'avatars/MadGod.jpg':'avatars/kai_avatar.png'"
if old_fallback not in html:
    raise RuntimeError("MadGod avatar fallback anchor missing")
html = html.replace(old_fallback, new_fallback, 1)
old_rows = "  function equipmentRows(member){\n    const eq=member&&member.equipment||{};"
new_rows = "  function equipmentRows(member){\n    if(member&&member.id==='kai'&&madGodSetEquipped())return ['MadGod Set','Omnivault Ring'];\n    const eq=member&&member.equipment||{};"
if old_rows not in html:
    raise RuntimeError("MadGod equipment UI rows anchor missing")
html = html.replace(old_rows, new_rows, 1)
INDEX.write_text(html, encoding="utf-8")

# Regression tests target the actual JUnit 4 classpath used by this APK.
TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class MadGodEquipmentTest {
  @Test fun oneSetContainsBothComponentsAtOnePassScaling() {
    val set = MadGodCanon.setItem()
    assertEquals(25_000, MadGodCanon.MAGNUM_DMG)
    assertEquals(25_000, MadGodCanon.ARMOR_DF)
    assertEquals(5_000, MadGodCanon.ARMOR_STR)
    assertEquals("BASELINE_ONCE", MadGodCanon.SCALING_MODE)
    assertEquals("MadGod Armor + MadGod Magnum", set.metadata["components"])
    assertEquals("infinite", set.metadata["magnumAmmo"])
    assertEquals("Sparda Core", set.metadata["magnumAmmoSource"])
    assertEquals("600", set.metadata["magnumRPM"])
    assertEquals("avatars/MadGod.jpg", set.metadata["avatarAsset"])
    assertEquals("Kai_MadGod_snapshot_overlay.png", set.metadata["snapshotOverlayAsset"])
  }

  @Test fun cheatSpawnsExactlyOneSetItem() {
    val one = MadGodCanon.spawn(GameState.initial())
    assertTrue(one.added)
    val inv = one.state.inventories.getValue(KAI_ID).items
    assertEquals(1, inv.getValue(MADGOD_SET_ID).quantity)
    assertFalse(inv.containsKey("madgod:armor"))
    assertFalse(inv.containsKey("madgod:magnum"))
    assertFalse(MadGodCanon.spawn(one.state).added)
  }

  @Test fun oneEquipBindsWeaponAndArmorAndCannotBeRemoved() {
    val s = MadGodCanon.spawn(GameState.initial()).state
    val equip = InventoryEngine.execute(s, ItemCommand("e", "TURN_1", KAI_ID, source=CommandSource.UI, operation=ItemCommand.Operation.EQUIP, itemId=MADGOD_SET_ID, itemName=MadGodCanon.SET_NAME, slot="set"))
    assertTrue(equip.applied)
    val slots = equip.state.equipment.getValue(KAI_ID).slots
    assertEquals(MADGOD_SET_ID, slots["weapon"])
    assertEquals(MADGOD_SET_ID, slots["armor"])
    val projected = MadGodCanon.legacy(equip.state)
    assertTrue(projected.has("set"))
    assertFalse(projected.has("weapon"))
    assertFalse(projected.has("armor"))
    val remove = InventoryEngine.execute(equip.state, ItemCommand("u", "TURN_1", KAI_ID, source=CommandSource.UI, operation=ItemCommand.Operation.UNEQUIP, itemId=MADGOD_SET_ID, itemName=MadGodCanon.SET_NAME, slot="set"))
    assertFalse(remove.applied)
    assertEquals("madgod_equipment_permanent", remove.validation.reason)
  }

  @Test fun omnivaultCannotCopyTheSet() {
    val s = MadGodCanon.spawn(GameState.initial()).state
    val scan = OmnivaultEngine.execute(s, OmnivaultCommand("s", "TURN_1", KAI_ID, source=CommandSource.UI, operation=OmnivaultCommand.Operation.SCAN, itemId=MADGOD_SET_ID, itemName=MadGodCanon.SET_NAME, timestampEpochMs=1L))
    assertFalse(scan.applied)
    assertEquals("madgod_omnivault_copy_forbidden", scan.validation.reason)
  }
}
''', encoding="utf-8")

combined = "\n".join(p.read_text(encoding="utf-8") for p in (MADGOD, ENGINES, FACADE, MAIN, INDEX, TEST))
for marker in (
    'const val MADGOD_SET_ID = "madgod:set"',
    '"components" to "MadGod Armor + MadGod Magnum"',
    'mapOf("weapon" to MADGOD_SET_ID, "armor" to MADGOD_SET_ID)',
    'Kai_MadGod_snapshot_overlay.png',
    'avatars/MadGod.jpg',
    'madGodSetEquipped()',
    "return ['MadGod Set','Omnivault Ring']",
    'cheatSpawnsExactlyOneSetItem',
):
    if marker not in combined:
        raise RuntimeError("Unified MadGod contract missing: " + marker)

print("MadGod R3 applied: one set item, atomic Armor+Magnum equip, MadGod avatar, and exact Snapshot overlay asset.")
