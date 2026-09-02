package com.rabpit.backroom.core

const val CURRENT_SAVE_VERSION = 5
const val KAI_ID = "kai"
const val KAI_SRU_SG_ID = "kai:sru-sg"
const val KAI_SRU_MK20_ID = "kai:sru-mk20"
const val KAI_SRU_MK20_SENSOR_ID = "kai:sru-mk20-open-face-sensor"
const val KAI_SRU_MK20_ARMS_ID = "kai:sru-mk20-arm-module"
const val KAI_SRU_MK20_LEGS_ID = "kai:sru-mk20-leg-module"
const val KAI_OMNIVAULT_RING_ID = "kai:omnivault-ring"

// Compatibility aliases for generated code. These names no longer denote the retired equipment.
const val KAI_WHITE_WRAITH_ID = KAI_SRU_SG_ID
const val KAI_BLACKBLOOD_ARMOR_ID = KAI_SRU_MK20_ID
const val KAI_LEGACY_WHITE_WRAITH_ID = "kai:white-wraith-magnum"
const val KAI_LEGACY_BLACKBLOOD_ARMOR_ID = "kai:blackblood-armor"
const val KAI_LEGACY_DEMON_JAW_ID = "kai:demon-jaw-mask"
const val KAI_LEGACY_TALON_ID = "kai:talon-gauntlets"
const val KAI_LEGACY_PHANTOM_GREAVES_ID = "kai:phantom-greaves"

object KaiStartingEquipment {
  const val WEAPON_NAME = "SRU-SG Shotgun"
  const val ARMOR_NAME = "SRU-MK20 Powered Armor"
  const val RING_NAME = "Omnivault Ring"
  const val WW_MAGNUM_DMG = 500
  const val BLACKBLOOD_DF = 500
  const val BLACKBLOOD_STR = 100
  const val BLACKBLOOD_AGI = 100
  const val BLACKBLOOD_HP = 100
  const val BLACKBLOOD_ENE = 100
  const val BLACKBLOOD_CRIT = 100

  val slots: Map<String, String> = linkedMapOf(
    "weapon" to KAI_WHITE_WRAITH_ID,
    "armor" to KAI_BLACKBLOOD_ARMOR_ID,
    "head" to KAI_DEMON_JAW_MASK_ID,
    "gauntlets" to KAI_TALON_GAUNTLETS_ID,
    "greaves" to KAI_PHANTOM_GREAVES_ID,
    "ring" to KAI_OMNIVAULT_RING_ID
  )

  fun displayName(itemId: String): String? = when (itemId) {
    KAI_WHITE_WRAITH_ID -> WEAPON_NAME
    KAI_BLACKBLOOD_ARMOR_ID -> ARMOR_NAME
    KAI_DEMON_JAW_MASK_ID -> "Demon Jaw Mask"
    KAI_TALON_GAUNTLETS_ID -> "Talon Gauntlets"
    KAI_PHANTOM_GREAVES_ID -> "Phantom Greaves"
    KAI_OMNIVAULT_RING_ID -> RING_NAME
    else -> null
  }

  fun slotFor(itemId: String, itemName: String): String? {
    val key = "$itemId $itemName".lowercase()
    return when {
      key.contains("sru-sg") || key.contains("sru sg") || key.contains("w.w magnum") || key.contains("white wraith") || key.contains("wraith magnum") -> "weapon"
      key.contains("sru-mk20") || key.contains("sru mk20") || key.contains("blackblood armor") || key.contains("black blood armor") -> "armor"
      key.contains("demon jaw") -> "head"
      key.contains("talon gauntlet") -> "gauntlets"
      key.contains("phantom greave") -> "greaves"
      key.contains("omnivault ring") || key.contains("nhẫn omnivault") || key.contains("nhẫn vạn tàng") || key.contains("van tang") -> "ring"
      else -> null
    }
  }

  fun itemIdForSlot(slot: String): String? = slots[slot]
  fun isSignature(itemId: String, itemName: String): Boolean = slotFor(itemId, itemName) != null || itemId in slots.values
}

enum class CharacterPresence { ACTIVE, SEPARATED, MISSING, DEAD }
enum class CommandSource { RULE, LITERT, GEMINI, UI, SYSTEM }
enum class PendingTurnStatus { CREATED, INTERPRETING, VALIDATING, EXECUTING, COMMITTED, FAILED }

data class ItemStack(
  val itemId: String,
  val name: String,
  val quantity: Int = 1,
  val condition: String? = null,
  val metadata: Map<String, String> = emptyMap(),
  val archetypeId: String = itemId,
  val contentState: ContentState = ContentState.NONE
)

data class InventoryState(val ownerId: String, val items: Map<String, ItemStack> = emptyMap())
data class EquipmentState(val ownerId: String, val slots: Map<String, String> = emptyMap())

data class StatusEffect(
  val id: String,
  val type: String,
  val source: String,
  val startTurnId: String? = null,
  val durationTurns: Int? = null,
  val persistent: Boolean = false,
  val metadata: Map<String, String> = emptyMap()
)

data class PhysiologyState(
  val minutesSinceFood: Long? = null,
  val minutesSinceWater: Long? = null,
  val minutesAwake: Long? = null,
  val painState: String? = null,
  val infectionState: String? = null,
  val thermalState: String? = null,
  val metadata: Map<String, String> = emptyMap()
) {
  companion object {
    /** Simulation baseline for a fresh run: needs begin satisfied at Backrooms entry. */
    fun freshRunBaseline(): PhysiologyState = PhysiologyState(
      minutesSinceFood = 0L,
      minutesSinceWater = 0L,
      minutesAwake = 0L,
      metadata = mapOf("baseline" to "fresh_run_entry")
    )
  }
}

data class CharacterState(
  val id: String,
  val name: String,
  val avatarRef: String? = null,
  val healthState: String? = null,
  val injuries: List<String> = emptyList(),
  val presence: CharacterPresence = CharacterPresence.ACTIVE,
  val inventoryId: String = id,
  val equipmentId: String = id,
  val statusIds: Set<String> = emptySet(),
  val physiology: PhysiologyState = PhysiologyState(),
  val metadata: Map<String, String> = emptyMap(),
  // Appended to preserve all existing positional CharacterState constructor call sites.
  val statProfile: CharacterStatProfile = CharacterStatProfiles.forId(id),
  val vitalState: CharacterVitalState = CharacterStatProfiles.initialVitals(id)
)

data class PartyState(val leaderId: String = KAI_ID, val memberIds: List<String> = listOf(KAI_ID), val maxMembers: Int = 4)

data class ScanSlot(val slot: Int, val sourceItemId: String, val templateItem: ItemStack, val scannedAtEpochMs: Long)

data class OmnivaultState(
  val ownerId: String = KAI_ID,
  val storedItems: Map<String, ItemStack> = emptyMap(),
  val scanSlots: List<ScanSlot> = emptyList(),
  val markedSourceIds: Set<String> = emptySet(),
  val restoreCooldownUntilEpochMs: Map<String, Long> = emptyMap()
)

data class PendingTurn(
  val turnId: String,
  val input: String,
  val status: PendingTurnStatus = PendingTurnStatus.CREATED,
  val commandIds: List<String> = emptyList(),
  val error: String? = null
)

data class TurnState(
  val currentTurnId: String = "TURN_1",
  val pending: PendingTurn? = null,
  val completedTurnIds: Set<String> = emptySet(),
  val executedCommandIds: Set<String> = emptySet()
)

data class GameTimeState(
  val elapsedSubjectiveMinutes: Long = 0L,
  val lastAdvanceMinutes: Int = 0,
  val lastAdvanceReason: String? = null
)

data class GameState(
  val characters: Map<String, CharacterState>,
  val party: PartyState = PartyState(),
  val inventories: Map<String, InventoryState> = emptyMap(),
  val equipment: Map<String, EquipmentState> = emptyMap(),
  val statuses: Map<String, StatusEffect> = emptyMap(),
  val omnivault: OmnivaultState = OmnivaultState(),
  val turn: TurnState = TurnState(),
  val time: GameTimeState = GameTimeState(),
  val world: Map<String, String> = emptyMap(),
  val levelInstance: LevelInstanceState? = null,
  val story: StoryState = StoryState.initial(),
  val saveVersion: Int = CURRENT_SAVE_VERSION,
  val metadata: Map<String, String> = emptyMap()
) {
  companion object {
    fun initial(): GameState = CharacterEquipmentSystem.seedFresh(GameState(
      characters = mapOf(
        KAI_ID to CharacterState(
          KAI_ID,
          "Kai Akechi",
          avatarRef = "avatars/SRU_AVATAR.jpg",
          physiology = PhysiologyState.freshRunBaseline(),
          metadata = mapOf("inventoryProfile" to "kai")
        ),
        AN_NHIEN_ID to AnNhienCanon.character(),
        IRIS_ID to SpecialFollowersCanon.irisCharacter(),
        SYVIAL_ID to SpecialFollowersCanon.syvialCharacter()
      ),
      inventories = mapOf(
        KAI_ID to InventoryState(KAI_ID),
        AN_NHIEN_ID to AnNhienCanon.inventory(),
        IRIS_ID to InventoryState(IRIS_ID),
        SYVIAL_ID to InventoryState(SYVIAL_ID)
      ),
      equipment = mapOf(
        KAI_ID to EquipmentState(KAI_ID, KaiStartingEquipment.slots),
        AN_NHIEN_ID to AnNhienCanon.equipment(),
        IRIS_ID to EquipmentState(IRIS_ID, SpecialFollowersCanon.irisEquipmentSlots),
        SYVIAL_ID to EquipmentState(SYVIAL_ID, SpecialFollowersCanon.syvialEquipmentSlots)
      )
    ))
  }
}
