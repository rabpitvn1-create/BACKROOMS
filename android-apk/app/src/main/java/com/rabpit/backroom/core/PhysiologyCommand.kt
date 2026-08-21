package com.rabpit.backroom.core

data class PhysiologyCommand(
  override val commandId: String,
  override val turnId: String?,
  override val actorId: String,
  override val targetId: String = actorId,
  override val source: CommandSource,
  val operation: Operation,
  val painState: String? = null,
  val infectionState: String? = null,
  val thermalState: String? = null
) : GameCommand {
  enum class Operation {
    RECORD_FOOD,
    RECORD_WATER,
    RECORD_SLEEP,
    UPDATE_CONDITION
  }
}
