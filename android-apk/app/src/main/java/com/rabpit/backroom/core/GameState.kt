package com.rabpit.backroom.core

const val CURRENT_SAVE_VERSION = 15
const val STAT_POINT_BOOK_ID = "training:stat-point-book"
const val CAO_MINH_ID = "cao_minh"
const val CAO_MINH_AVATAR_REF = "avatars/CAO_MINH_AVATAR.jpg"
const val CAO_MINH_SWORD_ID = "cao_minh:huyet-ma-kiem"
const val CAO_MINH_ARMOR_ID = "cao_minh:huyet-ma-chien-khai"

object CaoMinhStartingEquipment {
  const val WEAPON_NAME = "Huyết Ma Kiếm"
  const val ARMOR_NAME = "Huyết Ma Chiến Khải"

  val slots: Map<String, String> = linkedMapOf(
    "weapon" to CAO_MINH_SWORD_ID,
    "armor" to CAO_MINH_ARMOR_ID,
  )


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
  val metadata: Map<String, String> = emptyMap(),
  /** Rest-eligible survival damage; only HP itself remains authoritative. */
  val recoverableFoodHp: Int = 0,
  val recoverableWaterHp: Int = 0,
  val recoverableSleepHp: Int = 0,
  val restRecoveryMinutes: Int = 0
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
  val vitalState: CharacterVitalState = CharacterStatProfiles.initialVitals(id),
  val progression: CharacterProgression = CharacterProgression()
)

data class PartyState(val leaderId: String = CAO_MINH_ID, val memberIds: List<String> = listOf(CAO_MINH_ID), val maxMembers: Int = 4)

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
  val metadata: Map<String, String> = emptyMap(),
  val dungeon: DungeonState = DungeonState()
) {
  companion object {
    fun initial(): GameState = CharacterEquipmentSystem.seedFresh(GameState(
      characters = mapOf(
        CAO_MINH_ID to CharacterState(
          CAO_MINH_ID,
          "Cao Minh",
          avatarRef = CAO_MINH_AVATAR_REF,
          physiology = PhysiologyState.freshRunBaseline(),
          metadata = mapOf("inventoryProfile" to "cao_minh")
        ),
        AN_NHIEN_ID to AnNhienCanon.character(),
        LUC_TRAM_ID to LucTramCanon.character(),
        IRIS_ID to SpecialFollowersCanon.irisCharacter(),
        SYVIAL_ID to SpecialFollowersCanon.syvialCharacter()
      ),
      inventories = mapOf(
        CAO_MINH_ID to InventoryState(CAO_MINH_ID),
        AN_NHIEN_ID to AnNhienCanon.inventory(),
        LUC_TRAM_ID to InventoryState(LUC_TRAM_ID),
        IRIS_ID to InventoryState(IRIS_ID),
        SYVIAL_ID to InventoryState(SYVIAL_ID)
      ),
      equipment = mapOf(
        CAO_MINH_ID to EquipmentState(CAO_MINH_ID, CaoMinhStartingEquipment.slots),
        AN_NHIEN_ID to AnNhienCanon.equipment(),
        LUC_TRAM_ID to EquipmentState(LUC_TRAM_ID),
        IRIS_ID to EquipmentState(IRIS_ID, SpecialFollowersCanon.irisEquipmentSlots),
        SYVIAL_ID to EquipmentState(SYVIAL_ID, SpecialFollowersCanon.syvialEquipmentSlots)
      )
    ))
  }
}
