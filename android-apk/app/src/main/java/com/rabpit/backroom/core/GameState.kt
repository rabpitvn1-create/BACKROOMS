package com.rabpit.backroom.core

const val CURRENT_SAVE_VERSION = 5
const val KAI_ID = "cao_minh"
const val KAI_WHITE_WRAITH_ID = "cao_minh:huyet-ma-kiem"
const val KAI_BLACKBLOOD_ARMOR_ID = "cao_minh:huyet-ma-chien-khai"

object KaiStartingEquipment {
  const val WEAPON_NAME = "Huyết Ma Kiếm"
  const val ARMOR_NAME = "Huyết Ma Chiến Khải"
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
  )

  fun displayName(itemId: String): String? = when (itemId) {
    KAI_WHITE_WRAITH_ID -> WEAPON_NAME
    KAI_BLACKBLOOD_ARMOR_ID -> ARMOR_NAME
    KAI_DEMON_JAW_MASK_ID -> "Demon Jaw Mask"
    KAI_TALON_GAUNTLETS_ID -> "Talon Gauntlets"
    KAI_PHANTOM_GREAVES_ID -> "Phantom Greaves"
    else -> null
  }

  fun slotFor(itemId: String, itemName: String): String? {
    val key = "$itemId $itemName".lowercase()
    return when {
      key.contains("huyết ma kiếm") || key.contains("huyet ma kiem") || key.contains("w.w magnum") || key.contains("white wraith") || key.contains("wraith magnum") -> "weapon"
      key.contains("huyết ma chiến khải") || key.contains("huyet ma chien khai") || key.contains("blackblood armor") || key.contains("black blood armor") -> "armor"
      key.contains("demon jaw") -> "head"
      key.contains("talon gauntlet") -> "gauntlets"
      key.contains("phantom greave") -> "greaves"
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
  val metadata: Map<String, String> = emptyMap()
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
  val turn: TurnState = TurnState(),
  val time: GameTimeState = GameTimeState(),
  val world: Map<String, String> = emptyMap(),
  val saveVersion: Int = CURRENT_SAVE_VERSION,
  val metadata: Map<String, String> = emptyMap()
) {
  companion object {
    fun initial(): GameState = CharacterEquipmentSystem.seedFresh(GameState(
      characters = mapOf(
        KAI_ID to CharacterState(
          KAI_ID,
          "Cao Minh",
          avatarRef = "avatars/kai_avatar.png",
          physiology = PhysiologyState.freshRunBaseline(),
          metadata = mapOf("inventoryProfile" to "cao_minh")
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
