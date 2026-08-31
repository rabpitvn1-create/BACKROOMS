from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
FACADE = CORE / "GameCoreFacade.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


facade = FACADE.read_text(encoding="utf-8")
main = MAIN.read_text(encoding="utf-8")

# PendingTurn already belongs to GameState and GameStateCodec. The missing piece was durability at the
# external Gemini boundary: processRule created a pending turn in memory and then returned to Java
# without saving it. Persist before every fallback that can leave Core for the provider pipeline.
omnivault_old = '''    if (interpreted.candidates.any { it.intent == GameIntent.OMNIVAULT_RESTORE }) {
      return response(false, legacy, null, "fallback_required")
    }
'''
omnivault_new = '''    if (interpreted.candidates.any { it.intent == GameIntent.OMNIVAULT_RESTORE }) {
      repository.save(pending.state)
      return response(false, legacy, null, "fallback_required")
    }
'''
if omnivault_new.strip() not in facade:
    facade = replace_once(facade, omnivault_old, omnivault_new, "persist Omnivault fallback PendingTurn")

fallback_old = '''    if (interpreted.candidates.any { it.intent == GameIntent.NO_ACTION || it.confidence != IntentConfidence.HIGH }) {
      return response(false, legacy, null, "fallback_required")
    }
'''
fallback_new = '''    if (interpreted.candidates.any { it.intent == GameIntent.NO_ACTION || it.confidence != IntentConfidence.HIGH }) {
      repository.save(pending.state)
      return response(false, legacy, null, "fallback_required")
    }
'''
if fallback_new.strip() not in facade:
    facade = replace_once(facade, fallback_old, fallback_new, "persist model fallback PendingTurn")

resolution_old = '''    if (resolvedCommands.size != interpreted.candidates.size || resolvedCommands.isEmpty()) return response(false, legacy, null, "resolution_incomplete")
'''
resolution_new = '''    if (resolvedCommands.size != interpreted.candidates.size || resolvedCommands.isEmpty()) {
      repository.save(pending.state)
      return response(false, legacy, null, "resolution_incomplete")
    }
'''
if resolution_new.strip() not in facade:
    facade = replace_once(facade, resolution_old, resolution_new, "persist unresolved PendingTurn")

# A completed turn may be replayed by a stale WebView after the commit succeeded but the response was
# lost. Refuse that replay before creating a fresh ActionRuntime session.
turn_anchor = '''    val turnId = nextTurnId(legacy, state)
    val sessionId = "$turnId:${kind.name}:${action.hashCode().toUInt()}"
'''
turn_guard = '''    val turnId = nextTurnId(legacy, state)
    if (turnId in state.turn.completedTurnIds) {
      return actionStartResponse(false, null, "turn_already_completed")
    }
    val sessionId = "$turnId:${kind.name}:${action.hashCode().toUInt()}"
'''
if 'return actionStartResponse(false, null, "turn_already_completed")' not in facade:
    facade = replace_once(facade, turn_anchor, turn_guard, "completed turn replay guard")

# The first requested random value creates a seed inside the already-persistent ActionRuntime. Save
# synchronously before returning the roll. Subsequent labels are pure functions of that saved seed,
# so retries cannot reroll. PendingTurn is required to prevent accidental use outside a live action.
roll_methods = r'''  fun lockedActionRoll(label: String, bound: Int): Int {
    if (bound <= 0) throw IllegalArgumentException("action_roll_bound_invalid")
    if (label.isBlank()) throw IllegalArgumentException("action_roll_label_required")
    val state = repository.load()
    val active = ActionRuntime.activeSession(state)
      ?: throw IllegalStateException("action_session_missing")
    val pending = TurnCoordinator.recover(state)
      ?: throw IllegalStateException("pending_turn_missing")
    if (pending.turnId != active.turnId || pending.input != active.input) {
      throw IllegalStateException("pending_action_mismatch")
    }
    val seeded = ActionRollRuntime.ensureSeed(state, active.sessionId)
    if (seeded.error != null) throw IllegalStateException(seeded.error)
    val lockedState = if (seeded.applied) {
      repository.save(seeded.state)
      seeded.state
    } else {
      state
    }
    return ActionRollRuntime.lockedRoll(lockedState, active.sessionId, label, bound)
  }

  /** Technical/provider failures remain retryable. Keep PendingTurn, ActionRuntime and its roll seed. */
  fun markActionRetryableFailure(reason: String): Boolean {
    if (!repository.exists()) return false
    val state = repository.load()
    val active = ActionRuntime.activeSession(state) ?: return false
    var next = state
    val pending = TurnCoordinator.recover(next)
    if (pending != null && pending.turnId == active.turnId) {
      val flagged = TurnCoordinator.updatePending(
        next,
        PendingTurnStatus.FAILED,
        pending.commandIds,
        reason.ifBlank { "pipeline_error" }
      )
      if (flagged.error == null) next = flagged.state
    }
    next = next.copy(metadata = next.metadata + (
      "actionRuntime.retryableFailure" to reason.ifBlank { "pipeline_error" }
    ))
    repository.save(next)
    return true
  }

'''
roll_anchor = "  fun proposeWorldPressure(kindRaw: String): String {\n"
if "fun lockedActionRoll(label: String, bound: Int): Int" not in facade:
    facade = replace_once(facade, roll_anchor, roll_methods + roll_anchor, "locked action roll facade")

# Registered-Level resolver failures are authoritative local failures, not provider retries. It may
# have created a PendingTurn before failing, so clear that pending record after interrupting the
# ActionRuntime. Do not mark the turn completed because the legacy UI does not advance it here.
registered_old = '''      ActionRuntime.activeSession(failed)?.let { active ->
        val interrupted = ActionRuntime.interrupt(failed, active.sessionId, result.error)
        if (interrupted.applied) failed = interrupted.state
      }
      repository.save(failed)
'''
registered_new = '''      ActionRuntime.activeSession(failed)?.let { active ->
        val interrupted = ActionRuntime.interrupt(failed, active.sessionId, result.error)
        if (interrupted.applied) failed = interrupted.state
      }
      TurnCoordinator.recover(failed)?.let { recoverable ->
        val abandoned = TurnCoordinator.abandon(failed, recoverable.turnId, result.error)
        if (abandoned.error == null) failed = abandoned.state
      }
      repository.save(failed)
'''
if "TurnCoordinator.abandon(failed, recoverable.turnId, result.error)" not in facade:
    facade = replace_once(facade, registered_old, registered_new, "registered Level pending cleanup")

# MainActivity must not own random state. Threshold rolls and random Entity selection both consume
# named Core-locked channels. The old rollSpec helper is also patched even though later runtime
# patches supersede it, preventing a dormant direct RNG path from being accidentally reused.
threshold_rng_old = "    int roll = GAME_RNG.nextInt(max) + 1;\n"
threshold_rng_new = "    int roll = requireGameCore().lockedActionRoll(label, max);\n"
if threshold_rng_new not in main:
    main = replace_once(main, threshold_rng_old, threshold_rng_new, "threshold roll Core lock")

legacy_rng_old = "    int roll = GAME_RNG.nextInt(100) + 1;\n"
legacy_rng_new = "    int roll = requireGameCore().lockedActionRoll(label, 100);\n"
if legacy_rng_new not in main:
    main = replace_once(main, legacy_rng_old, legacy_rng_new, "legacy rollSpec Core lock")

entity_pick_old = '      rolls.put("roamingEntityKey", roamingPool[GAME_RNG.nextInt(roamingPool.length)]);\n'
entity_pick_new = '      rolls.put("roamingEntityKey", roamingPool[requireGameCore().lockedActionRoll("roamingEntityKey", roamingPool.length) - 1]);\n'
if entity_pick_new not in main:
    main = replace_once(main, entity_pick_old, entity_pick_new, "roaming Entity selection Core lock")

# A provider exception used to destroy the ActionRuntime session, which also destroyed the exact RNG
# identity needed for a safe retry. Mark the persisted PendingTurn failed instead and retain the
# session/seed. Retrying the same action resumes it and receives the same outcomes.
abort_old = '          try { requireGameCore().abortAction("pipeline_error"); } catch (Exception ignored) {}\n'
retry_new = '          try { requireGameCore().markActionRetryableFailure("pipeline_error"); } catch (Exception ignored) {}\n'
if retry_new not in main:
    main = replace_once(main, abort_old, retry_new, "provider failure preserves pending action")

for marker in (
    "repository.save(pending.state)",
    "fun lockedActionRoll(label: String, bound: Int): Int",
    "ActionRollRuntime.ensureSeed(state, active.sessionId)",
    "ActionRollRuntime.lockedRoll(lockedState, active.sessionId, label, bound)",
    "fun markActionRetryableFailure(reason: String): Boolean",
    "PendingTurnStatus.FAILED",
    "TurnCoordinator.abandon(failed, recoverable.turnId, result.error)",
    'return actionStartResponse(false, null, "turn_already_completed")',
):
    if marker not in facade:
        raise RuntimeError("PendingTurn/idempotent RNG facade contract missing: " + marker)

for marker in (
    "requireGameCore().lockedActionRoll(label, max)",
    "requireGameCore().lockedActionRoll(label, 100)",
    'requireGameCore().lockedActionRoll("roamingEntityKey", roamingPool.length) - 1',
    'requireGameCore().markActionRetryableFailure("pipeline_error")',
):
    if marker not in main:
        raise RuntimeError("PendingTurn/idempotent RNG Android contract missing: " + marker)

for forbidden in (
    "GAME_RNG.nextInt(max) + 1",
    "GAME_RNG.nextInt(100) + 1",
    "roamingPool[GAME_RNG.nextInt(roamingPool.length)]",
    'requireGameCore().abortAction("pipeline_error")',
):
    if forbidden in main:
        raise RuntimeError("Direct reroll path remains in final Android runtime: " + forbidden)

FACADE.write_text(facade, encoding="utf-8")
MAIN.write_text(main, encoding="utf-8")
print("PendingTurn persistence and idempotent action RNG applied: provider retries retain the same Core-locked outcomes.")
