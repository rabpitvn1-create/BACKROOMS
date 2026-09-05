package com.rabpit.backroom.core

sealed interface GameCommand {
  val commandId: String
  val turnId: String?
  val actorId: String
  val targetId: String?
  val source: CommandSource
}

data class ItemCommand(
  override val commandId: String,
  override val turnId: String?,
  override val actorId: String,
  override val targetId: String? = null,
  override val source: CommandSource,
  val operation: Operation,
  val itemId: String,
  val itemName: String,
  val quantity: Int = 1,
  val slot: String? = null
) : GameCommand {
  enum class Operation { DISCARD, USE, TRANSFER, EQUIP, UNEQUIP }
}

data class GiveAndUseItemCommand(
  override val commandId: String,
  override val turnId: String?,
  override val actorId: String,
  override val targetId: String,
  override val source: CommandSource,
  val itemId: String,
  val itemName: String,
  val quantity: Int = 1
) : GameCommand

data class LootGrantCommand(
  override val commandId: String,
  override val turnId: String?,
  override val actorId: String,
  override val targetId: String = actorId,
  override val source: CommandSource = CommandSource.SYSTEM,
  val origin: LootOrigin,
  val sourceId: String,
  val item: ItemStack,
  val quantity: Int = item.quantity
) : GameCommand

data class OmnivaultCommand(
  override val commandId: String,
  override val turnId: String?,
  override val actorId: String,
  override val targetId: String? = null,
  override val source: CommandSource,
  val operation: Operation,
  val itemId: String,
  val itemName: String,
  val quantity: Int = 1,
  val timestampEpochMs: Long = 0L
) : GameCommand {
  enum class Operation { STORE, WITHDRAW, RESTORE, QUERY }
}

data class PartyCommand(
  override val commandId: String,
  override val turnId: String?,
  override val actorId: String,
  override val targetId: String,
  override val source: CommandSource,
  val operation: Operation,
  val consentConfirmed: Boolean = false,
  val targetPresent: Boolean = false
) : GameCommand {
  enum class Operation { ADD, REMOVE, SET_LEADER, FOLLOW, SEPARATE, QUERY }
}

data class StatusCommand(
  override val commandId: String,
  override val turnId: String?,
  override val actorId: String,
  override val targetId: String = actorId,
  override val source: CommandSource,
  val operation: Operation,
  val effect: StatusEffect? = null,
  val statusId: String? = effect?.id
) : GameCommand {
  enum class Operation { APPLY, REMOVE, UPDATE, QUERY }
}

data class TimeAdvanceCommand(
  override val commandId: String,
  override val turnId: String?,
  override val actorId: String,
  override val targetId: String? = null,
  override val source: CommandSource,
  val minutes: Int,
  val reason: String
) : GameCommand

data class QueryCommand(
  override val commandId: String,
  override val turnId: String?,
  override val actorId: String,
  override val targetId: String? = null,
  override val source: CommandSource,
  val type: Type
) : GameCommand {
  enum class Type { CHARACTER, INVENTORY, PARTY, STATUS, OMNIVAULT }
}

data class ValidatedLegacyStateCommand(
  override val commandId: String,
  override val turnId: String?,
  override val actorId: String = KAI_ID,
  override val targetId: String? = null,
  override val source: CommandSource,
  val location: String? = null,
  val title: String? = null,
  val levelJson: String? = null,
  val playerJson: String? = null,
  val flagsJson: String? = null,
  val validatedByGameEngine: Boolean
) : GameCommand

data class ValidationResult(val valid: Boolean, val reason: String? = null)
data class ExecutionResult(
  val state: GameState,
  val applied: Boolean,
  val duplicate: Boolean = false,
  val validation: ValidationResult = ValidationResult(true),
  val events: List<String> = emptyList()
)
