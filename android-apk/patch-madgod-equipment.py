from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
STATE = CORE / "GameState.kt"
MADGOD = CORE / "MadGodCanon.kt"
COMMAND = CORE / "CommandPipeline.kt"
ENGINES = CORE / "Engines.kt"
VAULT = CORE / "OmnivaultEngine.kt"
FACADE = CORE / "GameCoreFacade.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/MadGodEquipmentTest.kt"


def rep(text, old, new, label):
    n = text.count(old)
    if n != 1:
        raise RuntimeError(f"{label}: expected 1 anchor, found {n}")
    return text.replace(old, new, 1)


state = STATE.read_text(encoding="utf-8")
anchor = '  const val RING_NAME = "Omnivault Ring"\n'
block = anchor + '''\n  const val WW_MAGNUM_DMG = 500
  const val BLACKBLOOD_DF = 500
  const val BLACKBLOOD_STR = 100
  const val BLACKBLOOD_AGI = 100
  const val BLACKBLOOD_HP = 100
  const val BLACKBLOOD_ENE = 100
  const val BLACKBLOOD_CRIT = 100
'''
if "WW_MAGNUM_DMG = 500" not in state:
    state = rep(state, anchor, block, "baseline stats")
STATE.write_text(state, encoding="utf-8")

MADGOD.write_text(r'''package com.rabpit.backroom.core

import org.json.JSONObject

const val MADGOD_MAGNUM_ID = "madgod:magnum"
const val MADGOD_ARMOR_ID = "madgod:armor"

object MadGodCanon {
  const val CHEAT_CODE = "/madgod"
  const val MAGNUM_NAME = "MadGod Magnum"
  const val ARMOR_NAME = "MadGod Armor"
  const val RARITY = "UR+ UNIQUE"
  const val MULTIPLIER = 50
  const val SCALING_MODE = "BASELINE_ONCE"
  const val MAGNUM_RPM = 600

  const val WW_MAGNUM_DMG = KaiStartingEquipment.WW_MAGNUM_DMG
  const val BLACKBLOOD_DF = KaiStartingEquipment.BLACKBLOOD_DF
  const val BLACKBLOOD_STR = KaiStartingEquipment.BLACKBLOOD_STR
  const val BLACKBLOOD_AGI = KaiStartingEquipment.BLACKBLOOD_AGI
  const val BLACKBLOOD_HP = KaiStartingEquipment.BLACKBLOOD_HP
  const val BLACKBLOOD_ENE = KaiStartingEquipment.BLACKBLOOD_ENE
  const val BLACKBLOOD_CRIT = KaiStartingEquipment.BLACKBLOOD_CRIT

  const val MAGNUM_DMG = WW_MAGNUM_DMG * MULTIPLIER
  const val ARMOR_DF = BLACKBLOOD_DF * MULTIPLIER
  const val ARMOR_STR = BLACKBLOOD_STR * MULTIPLIER
  const val ARMOR_AGI = BLACKBLOOD_AGI * MULTIPLIER
  const val ARMOR_HP = BLACKBLOOD_HP * MULTIPLIER
  const val ARMOR_ENE = BLACKBLOOD_ENE * MULTIPLIER
  const val ARMOR_CRIT = BLACKBLOOD_CRIT * MULTIPLIER

  data class SpawnResult(val state: GameState, val applied: Boolean, val alreadySpawned: Boolean)

  fun matchesCheat(action: String) = action.trim().equals(CHEAT_CODE, true)
  fun isId(id: String?) = id == MADGOD_MAGNUM_ID || id == MADGOD_ARMOR_ID
  fun isItem(item: ItemStack?) = item != null && (isId(item.itemId) || item.metadata["madGod"].equals("true", true))
  fun isPermanentlyEquipped(state: GameState, actor: String, id: String) =
    actor == KAI_ID && isId(id) && state.equipment[KAI_ID]?.slots?.values?.contains(id) == true

  fun slotFor(id: String, name: String): String? {
    val key = "$id $name".lowercase()
    return when {
      id == MADGOD_MAGNUM_ID || key.contains("madgod magnum") -> "weapon"
      id == MADGOD_ARMOR_ID || key.contains("madgod armor") -> "armor"
      else -> null
    }
  }

  fun weapon() = ItemStack(MADGOD_MAGNUM_ID, MAGNUM_NAME, 1, "PERFECT", linkedMapOf(
    "category" to "weapon", "slot" to "weapon", "rarity" to RARITY, "unique" to "true",
    "madGod" to "true", "kaiOnly" to "true", "permanentAfterEquip" to "true",
    "omnivaultCopyable" to "false", "baseEquivalent" to KaiStartingEquipment.WEAPON_NAME,
    "baseDMG" to WW_MAGNUM_DMG.toString(), "multiplier" to MULTIPLIER.toString(),
    "scalingMode" to SCALING_MODE, "stackMultiplier" to "false", "userStatMultiplier" to "false",
    "DMG" to MAGNUM_DMG.toString(), "ammo" to "infinite", "ammoSource" to "Sparda Core",
    "fireModes" to "single,full_auto", "RPM" to MAGNUM_RPM.toString()
  ))

  fun armor() = ItemStack(MADGOD_ARMOR_ID, ARMOR_NAME, 1, "PERFECT", linkedMapOf(
    "category" to "armor", "slot" to "armor", "rarity" to RARITY, "unique" to "true",
    "madGod" to "true", "kaiOnly" to "true", "permanentAfterEquip" to "true",
    "omnivaultCopyable" to "false", "baseEquivalent" to KaiStartingEquipment.ARMOR_NAME,
    "baseDF" to BLACKBLOOD_DF.toString(), "baseSTR" to BLACKBLOOD_STR.toString(),
    "baseAGI" to BLACKBLOOD_AGI.toString(), "baseHP" to BLACKBLOOD_HP.toString(),
    "baseENE" to BLACKBLOOD_ENE.toString(), "baseCRIT" to BLACKBLOOD_CRIT.toString(),
    "multiplier" to MULTIPLIER.toString(), "scalingMode" to SCALING_MODE,
    "stackMultiplier" to "false", "userStatMultiplier" to "false",
    "DF" to ARMOR_DF.toString(), "STR" to ARMOR_STR.toString(), "AGI" to ARMOR_AGI.toString(),
    "HP" to ARMOR_HP.toString(), "ENE" to ARMOR_ENE.toString(), "CRIT" to ARMOR_CRIT.toString(),
    "functions" to "Blackblood Armor equivalent functions"
  ))

  fun canonicalize(item: ItemStack): ItemStack {
    val key = (item.itemId + " " + item.name).lowercase()
    return when {
      item.itemId == MADGOD_MAGNUM_ID || key.contains("madgod magnum") -> weapon().copy(quantity=item.quantity, condition=item.condition ?: "PERFECT")
      item.itemId == MADGOD_ARMOR_ID || key.contains("madgod armor") -> armor().copy(quantity=item.quantity, condition=item.condition ?: "PERFECT")
      else -> item
    }
  }

  fun spawn(state: GameState): SpawnResult {
    val ids = state.inventories.values.flatMap { it.items.keys }.toSet() +
      state.omnivault.storedItems.keys + state.equipment.values.flatMap { it.slots.values }
    val already = state.metadata["madGod.spawned"].equals("true", true) || MADGOD_MAGNUM_ID in ids || MADGOD_ARMOR_ID in ids
    if (already) return SpawnResult(
      if (state.metadata["madGod.spawned"].equals("true", true)) state else state.copy(metadata=state.metadata + ("madGod.spawned" to "true")),
      false, true
    )
    val inv = state.inventories[KAI_ID] ?: InventoryState(KAI_ID)
    val next = inv.copy(items=inv.items + mapOf(MADGOD_MAGNUM_ID to weapon(), MADGOD_ARMOR_ID to armor()))
    return SpawnResult(state.copy(
      inventories=state.inventories + (KAI_ID to next),
      metadata=state.metadata + mapOf("madGod.spawned" to "true", "madGod.spawnSource" to "cheat", "madGod.multiplierMode" to SCALING_MODE)
    ), true, false)
  }

  fun displayName(id: String) = when (id) {
    MADGOD_MAGNUM_ID -> MAGNUM_NAME
    MADGOD_ARMOR_ID -> ARMOR_NAME
    else -> KaiStartingEquipment.displayName(id)
  }

  private fun stats(id: String) = when (id) {
    KAI_WHITE_WRAITH_ID -> JSONObject().put("DMG", WW_MAGNUM_DMG)
    KAI_BLACKBLOOD_ARMOR_ID -> JSONObject().put("DF", BLACKBLOOD_DF).put("STR", BLACKBLOOD_STR)
      .put("AGI", BLACKBLOOD_AGI).put("HP", BLACKBLOOD_HP).put("ENE", BLACKBLOOD_ENE).put("CRIT", BLACKBLOOD_CRIT)
    MADGOD_MAGNUM_ID -> JSONObject().put("DMG", MAGNUM_DMG).put("RPM", MAGNUM_RPM).put("ammo", "infinite")
    MADGOD_ARMOR_ID -> JSONObject().put("DF", ARMOR_DF).put("STR", ARMOR_STR).put("AGI", ARMOR_AGI)
      .put("HP", ARMOR_HP).put("ENE", ARMOR_ENE).put("CRIT", ARMOR_CRIT)
    else -> JSONObject()
  }

  fun legacyEquipment(state: GameState) = JSONObject().apply {
    val slots = state.equipment[KAI_ID]?.slots.orEmpty()
    listOf("weapon", "armor", "ring").forEach { slot ->
      val id = slots[slot] ?: return@forEach
      put(slot, JSONObject().put("id", id).put("name", displayName(id) ?: id)
        .put("permanent", isId(id)).put("stats", stats(id))
        .put("