package com.rabpit.backroom.core

object PhysiologyEngine {
  fun execute(state: GameState, command: PhysiologyCommand): ExecutionResult {
    val character = state.characters[command.targetId] ?: return invalid(state, "target_unknown")
    if (character.presence == CharacterPresence.DEAD) return invalid(state, "physiology_target_dead")

    val current = character.physiology
    val recovery = command.recoveryMinutes
    if (recovery != null && (recovery <= 0L || recovery > 10080L * 100L))
      return invalid(state, "physiology_recovery_invalid")
    val next = when (command.operation) {
      PhysiologyCommand.Operation.RECORD_FOOD -> current.copy(
        minutesSinceFood = current.minutesSinceFood?.let { (it - minOf(it, recovery ?: 720L)) })
      PhysiologyCommand.Operation.RECORD_WATER -> current.copy(
        minutesSinceWater = current.minutesSinceWater?.let { (it - minOf(it, recovery ?: 360L)) })
      PhysiologyCommand.Operation.RECORD_SLEEP -> current.copy(
        minutesAwake = current.minutesAwake?.let { (it - minOf(it, recovery ?: 60L)) })
      PhysiologyCommand.Operation.UPDATE_CONDITION -> {
        val pain = normalizedCondition(command.painState) ?: command.painState?.let { return invalid(state, "physiology_condition_blank") }
        val infection = normalizedCondition(command.infectionState) ?: command.infectionState?.let { return invalid(state, "physiology_condition_blank") }
        val thermal = normalizedCondition(command.thermalState) ?: command.thermalState?.let { return invalid(state, "physiology_condition_blank") }
        if (pain == null && infection == null && thermal == null) return invalid(state, "physiology_condition_required")
        current.copy(
          painState = pain ?: current.painState,
          infectionState = infection ?: current.infectionState,
          thermalState = thermal ?: current.thermalState
        )
      }
    }

    val event = when (command.operation) {
      PhysiologyCommand.Operation.RECORD_FOOD -> "physiology_food_recorded"
      PhysiologyCommand.Operation.RECORD_WATER -> "physiology_water_recorded"
      PhysiologyCommand.Operation.RECORD_SLEEP -> "physiology_sleep_recorded"
      PhysiologyCommand.Operation.UPDATE_CONDITION -> "physiology_condition_updated"
    }
    val updated = character.copy(physiology = next)
    return changed(state.copy(characters = state.characters + (character.id to updated)), event)
  }

  /** Applied once by TimeEngine for each committed interval, never by narration/UI. */
  fun advance(state: GameState, id: String, minutes: Long, activity: TimeActivity): GameState? {
    val c = state.characters[id] ?: return state
    if (c.presence == CharacterPresence.DEAD) return state
    val p = c.physiology
    val thermal = p.thermalState?.trim()?.lowercase()
    val harsh = thermal in setOf("hot", "heat", "overheated", "cold", "freezing")
    val isRest = activity == TimeActivity.REST || activity == TimeActivity.SLEEP
    val isSleep = activity == TimeActivity.SLEEP
    val foodRate = if (activity == TimeActivity.EXERTION) 2L else 1L
    val waterRate = (if (activity == TimeActivity.EXERTION) 2L else 1L) * (if (thermal in setOf("hot", "heat", "overheated")) 2L else 1L)
    fun add(value: Long?, amount: Long): Long? {
      if (value == null) return null
      if (value < 0L || amount < 0L || value > Long.MAX_VALUE - amount) throw ArithmeticException("physiology_time_overflow")
      return value + amount
    }
    val next = try {
      p.copy(
        minutesSinceFood = add(p.minutesSinceFood, minutes * foodRate),
        minutesSinceWater = add(p.minutesSinceWater, minutes * waterRate),
        minutesAwake = if (isSleep) p.minutesAwake?.let { (it - minOf(it, minutes * (if (harsh) 1L else 2L))) }
                       else add(p.minutesAwake, minutes * (if (activity == TimeActivity.EXERTION) 2L else 1L)),
        // Movement/combat pauses rest recovery; it must not erase actual rest already earned.
        restRecoveryMinutes = if (isRest) ((p.restRecoveryMinutes.toLong() + minutes) % 30L).toInt()
          else p.restRecoveryMinutes
      )
    } catch (_: ArithmeticException) { return null }
    var output = state.copy(characters = state.characters + (id to c.copy(physiology = next)))
    val multiplier = c.metadata["survivalMultiplier"]?.toDoubleOrNull() ?: 1.0
    val foodLoss = (PhysiologyStatusPolicy.hungerDamage(next.minutesSinceFood, multiplier) -
      PhysiologyStatusPolicy.hungerDamage(p.minutesSinceFood, multiplier)).coerceAtLeast(0L)
    val waterLoss = (PhysiologyStatusPolicy.thirstDamage(next.minutesSinceWater, multiplier) -
      PhysiologyStatusPolicy.thirstDamage(p.minutesSinceWater, multiplier)).coerceAtLeast(0L)
    val sleepLoss = (PhysiologyStatusPolicy.sleepDamage(next.minutesAwake, multiplier) -
      PhysiologyStatusPolicy.sleepDamage(p.minutesAwake, multiplier)).coerceAtLeast(0L)
    output = CharacterStatEngine.applySurvivalDamage(output, id, foodLoss, "FOOD")
    output = CharacterStatEngine.applySurvivalDamage(output, id, waterLoss, "WATER")
    output = CharacterStatEngine.applySurvivalDamage(output, id, sleepLoss, "SLEEP")
    if (!isRest || output.characters.getValue(id).presence == CharacterPresence.DEAD) return output

    val afterDamage = output.characters.getValue(id)
    val q = afterDamage.physiology
    val credits = (p.restRecoveryMinutes.toLong() + minutes) / 30L
    val foodReady = PhysiologyStatusPolicy.hungerBand(q.minutesSinceFood, multiplier) in
      setOf(PhysiologyBand.NORMAL, PhysiologyBand.MILD)
    val waterReady = PhysiologyStatusPolicy.thirstBand(q.minutesSinceWater, multiplier) in
      setOf(PhysiologyBand.NORMAL, PhysiologyBand.MILD)
    val sleepReady = PhysiologyStatusPolicy.awakeBand(q.minutesAwake, multiplier) in
      setOf(PhysiologyBand.NORMAL, PhysiologyBand.MILD)
    val maxHp = CharacterStatCore.effective(output, id).maxHp
    var allowance = minOf(credits, (maxHp - afterDamage.vitalState.currentHp).toLong()).toInt()
    val foodHeal = if (foodReady) minOf(allowance, q.recoverableFoodHp) else 0
    allowance -= foodHeal
    val waterHeal = if (waterReady) minOf(allowance, q.recoverableWaterHp) else 0
    allowance -= waterHeal
    val sleepHeal = if (sleepReady) minOf(allowance, q.recoverableSleepHp) else 0
    val totalHeal = foodHeal + waterHeal + sleepHeal
    if (totalHeal <= 0) return output
    // setCurrentHp applies ordinary-heal accounting; replace it with the exact
    // eligible source allocation so an unmet need cannot donate recovery credit.
    output = CharacterStatEngine.setCurrentHp(output, id, afterDamage.vitalState.currentHp + totalHeal)
    val healed = output.characters.getValue(id)
    return output.copy(characters = output.characters + (id to healed.copy(
      physiology = q.copy(
        recoverableFoodHp = q.recoverableFoodHp - foodHeal,
        recoverableWaterHp = q.recoverableWaterHp - waterHeal,
        recoverableSleepHp = q.recoverableSleepHp - sleepHeal
      )
    )))
  }

  private fun normalizedCondition(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
}
