package com.rabpit.backroom.core

const val CURRENT_SAVE_VERSION = 3
const val CAO_MINH_ID = "cao-minh"
const val CAO_MINH_HUYET_MA_KIEM_ID = "cao-minh:huyet-ma-kiem"
const val CAO_MINH_HUYET_MA_CHIEN_KHAI_ID = "cao-minh:huyet-ma-chien-khai"
const val CAO_MINH_HUYET_MA_GAUNTLETS_ID = "cao-minh:huyet-ma-gauntlets"
const val CAO_MINH_HUYET_MA_BOOTS_ID = "cao-minh:huyet-ma-boots"
const val CAO_MINH_VAN_KHO_GIOI_CHI_ID = "cao-minh:van-kho-gioi-chi"

object CaoMinhStartingEquipment {
  const val WEAPON_NAME = "Huyết Ma Kiếm"
  const val ARMOR_NAME = "Huyết Ma Chiến Khải"
  const val GAUNTLETS_NAME = "Găng Huyết Ma"
  const val BOOTS_NAME = "Giày Huyết Ma"
  const val RING_NAME = "Vạn Khố Giới Chỉ"

  val slots: Map<String, String> = linkedMapOf(
    "weapon" to CAO_MINH_HUYET_MA_KIEM_ID,
    "armor" to CAO_MINH_HUYET_MA_CHIEN_KHAI_ID,
    "gauntlets" to CAO_MINH_HUYET_MA_GAUNTLETS_ID,
    "greaves" to CAO_MINH_HUYET_MA_BOOTS_ID,
    "ring" to CAO_MINH_VAN_KHO_GIOI_CHI_ID
  )

  fun displayName(itemId: String): String? = when (itemId) {
    CAO_MINH_HUYET_MA_KIEM_ID -> WEAPON_NAME
    CAO_MINH_HUYET_MA_CHIEN_KHAI_ID -> ARMOR_NAME
    CAO_MINH_HUYET_MA_GAUNTLETS_ID -> GAUNTLETS_NAME
    CAO_MINH_HUYET_MA_BOOTS_ID -> BOOTS_NAME
    CAO_MINH_VAN_KHO_GIOI_CHI_ID -> RING_NAME
    else -> null
  }

  fun slotFor(itemId: String, itemName: String): String? {
    val key = "$itemId $itemName".lowercase()
    return when {
      key.contains("huyết ma kiếm") || key.contains("huyet ma kiem") -> "weapon"
      key.contains("huyết ma chiến khải") || key.contains("huyet ma chien khai") -> "armor"
      key.contains("huyết ma gauntlet") || key.contains("huyet ma gauntlet") || key.contains("găng huyết ma") || key.contains("gang huyet ma") -> "gauntlets"
      key.contains("huyết ma boot") || key.contains("huyet ma boot") || key.contains("giày huyết ma") || key.contains("giay huyet ma") -> "greaves"
      key.contains("vạn khố giới chỉ") || key.contains("nhẫn vạn khố") || key.contains("van kho gioi chi") || key.contains("nhẫn vạn tàng") || key.contains("van tang") -> "ring"
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

data class PartyState(val leaderId: String = CAO_MINH_ID, val memberIds: List<String> = listOf(CAO_MINH_ID), val maxMembers: Int = 4)

data class ScanSlot(val slot: Int, val sourceItemId: String, val templateItem: ItemStack, val scannedAtEpochMs: Long)

data class VanKhoGioiChiState(
  val ownerId: String = CAO_MINH_ID,
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
  val vanKhoGioiChi: VanKhoGioiChiState = VanKhoGioiChiState(),
  val turn: TurnState = TurnState(),
  val time: GameTimeState = GameTimeState(),
  val world: Map<String, String> = emptyMap(),
  val saveVersion: Int = CURRENT_SAVE_VERSION,
  val metadata: Map<String, String> = emptyMap()
) {
  companion object {
    fun initial(): GameState = CharacterEquipmentSystem.seedFresh(GameState(
      characters = mapOf(
        CAO_MINH_ID to CharacterState(
          CAO_MINH_ID,
          "Cao Minh",
          avatarRef = "avatars/CAO_MINH_AVATAR.jpg",
          physiology = PhysiologyState.freshRunBaseline(),
          metadata = mapOf("inventoryProfile" to "cao-minh")
        ),
        AN_NHIEN_ID to AnNhienCanon.character(),
        LUC_TRAM_ID to SpecialFollowersCanon.lucTramCharacter(),
        SYVIAL_ID to SpecialFollowersCanon.syvialCharacter(),
        NGUYEN_QUOC_NAM_ID to VietnameseFollowersCanon.character(NGUYEN_QUOC_NAM_ID),
        HUA_TIEU_LAN_ID to VietnameseFollowersCanon.character(HUA_TIEU_LAN_ID)
      ),
      inventories = mapOf(
        CAO_MINH_ID to InventoryState(CAO_MINH_ID),
        AN_NHIEN_ID to AnNhienCanon.inventory(),
        LUC_TRAM_ID to InventoryState(LUC_TRAM_ID),
        SYVIAL_ID to InventoryState(SYVIAL_ID),
        NGUYEN_QUOC_NAM_ID to InventoryState(NGUYEN_QUOC_NAM_ID),
        HUA_TIEU_LAN_ID to InventoryState(HUA_TIEU_LAN_ID)
      ),
      equipment = mapOf(
        CAO_MINH_ID to EquipmentState(CAO_MINH_ID, CaoMinhStartingEquipment.slots),
        AN_NHIEN_ID to AnNhienCanon.equipment(),
        LUC_TRAM_ID to EquipmentState(LUC_TRAM_ID, SpecialFollowersCanon.lucTramEquipmentSlots),
        SYVIAL_ID to EquipmentState(SYVIAL_ID, SpecialFollowersCanon.syvialEquipmentSlots),
        NGUYEN_QUOC_NAM_ID to EquipmentState(NGUYEN_QUOC_NAM_ID, VietnameseFollowersCanon.startingLoadout(NGUYEN_QUOC_NAM_ID).mapKeys { it.key.key }),
        HUA_TIEU_LAN_ID to EquipmentState(HUA_TIEU_LAN_ID, VietnameseFollowersCanon.startingLoadout(HUA_TIEU_LAN_ID).mapKeys { it.key.key })
      )
    ))
  }
}
