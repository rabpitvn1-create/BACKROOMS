from pathlib import Path

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
facade = FACADE.read_text(encoding="utf-8")

runtime_methods = r'''  fun beginAction(legacyStateJson: String, kindRaw: String, action: String): String {
    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
    val kind = enumValues<ActionKind>().firstOrNull { it.name == kindRaw.trim().uppercase() }
      ?: return actionStartResponse(false, null, "action_kind_invalid")
    val existing = ActionRuntime.activeSession(state)
    if (existing != null) {
      return if (existing.kind == kind && existing.input == action) actionStartResponse(true, existing, null)
      else actionStartResponse(false, existing, "action_session_already_active")
    }
    val turnId = nextTurnId(legacy, state)
    val sessionId = "$turnId:${kind.name}:${action.hashCode().toUInt()}"
    val started = ActionRuntime.start(
      state = state,
      sessionId = sessionId,
      turnId = turnId,
      actorId = KAI_ID,
      kind = kind,
      input = action,
      locationKey = state.world["location"] ?: legacy.optString("location").takeIf(String::isNotBlank),
      plannedMinutes = TimeCostPolicy.estimateMinutes(action),
      searchDepth = if (kind == ActionKind.SEARCH) SearchDepth.NORMAL else null
    )
    if (!started.applied) return actionStartResponse(false, started.session, started.error ?: "action_start_failed")
    repository.save(started.state)
    return actionStartResponse(true, started.session, null)
  }

  fun currentActionContext(): String {
    val state = repository.load()
    val active = ActionRuntime.activeSession(state)
    return JSONObject().apply {
      put("active", active != null)
      if (active != null) {
        put("sessionId", active.sessionId)
        put("turnId", active.turnId)
        put("kind", active.kind.name)
        put("phase", active.phase.name)
        put("location", active.locationKey ?: JSONObject.NULL)
        put("elapsedMinutes", active.elapsedMinutes)
        put("plannedMinutes", active.plannedMinutes ?: JSONObject.NULL)
        put("searchDepth", active.searchDepth?.name ?: JSONObject.NULL)
        if (active.kind == ActionKind.SEARCH && !active.locationKey.isNullOrBlank()) {
          put("searchCoverage", JSONArray(ActionRuntime.searchCoverage(state, active.locationKey).sorted()))
        }
      }
    }.toString()
  }

  fun abortAction(reason: String): Boolean {
    if (!repository.exists()) return false
    val state = repository.load()
    val active = ActionRuntime.activeSession(state) ?: return false
    val interrupted = ActionRuntime.interrupt(state, active.sessionId, reason.ifBlank { "pipeline_error" })
    if (!interrupted.applied) return false
    repository.save(interrupted.state)
    return true
  }

  private fun actionStartResponse(handled: Boolean, session: ActionSessionSnapshot?, error: String?): String = JSONObject().apply {
    put("handled", handled)
    if (session != null) {
      put("sessionId", session.sessionId)
      put("turnId", session.turnId)
      put("kind", session.kind.name)
    }
    if (error != null) put("error", error)
  }.toString()

  private fun commitActionRuntime(
    state: GameState,
    commands: MutableList<GameCommand>,
    action: String,
    turnId: String
  ): TurnResult {
    val active = ActionRuntime.activeSession(state)
    if (active == null) {
      commands += timeAdvanceCommand(turnId, action)
      return TurnCoordinator.commit(state, commands)
    }
    if (active.turnId != turnId) return TurnResult(state, error = "action_turn_mismatch")

    val minutes = active.plannedMinutes ?: TimeCostPolicy.estimateMinutes(action)
    val progressed = ActionRuntime.advance(state, active.sessionId, "resolve", minutes)
    if (!progressed.applied && !progressed.duplicate) {
      return TurnResult(state, error = progressed.error ?: "action_time_rejected")
    }
    val progressedState = if (progressed.duplicate) state else progressed.state
    val committed = TurnCoordinator.commit(progressedState, commands)
    if (committed.error != null) return committed

    var finalState = committed.state
    if (active.kind == ActionKind.SEARCH && !active.locationKey.isNullOrBlank()) {
      val depth = active.searchDepth ?: SearchDepth.NORMAL
      val coverage = ActionRuntime.markSearchCoverage(
        finalState,
        active.sessionId,
        setOf("depth:${depth.name.lowercase()}")
      )
      if (coverage.applied) finalState = coverage.state
    }

    val completed = ActionRuntime.complete(finalState, active.sessionId)
    if (!completed.applied) return TurnResult(finalState, committed.execution, completed.error ?: "action_complete_failed")
    return TurnResult(completed.state, committed.execution?.copy(state = completed.state))
  }

'''

if "fun beginAction(legacyStateJson: String, kindRaw: String, action: String)" not in facade:
    anchor = "  fun currentCoreState(): String = GameStateCodec.encode(repository.load())\n"
    if anchor not in facade:
        raise RuntimeError("GameCoreFacade currentCoreState anchor missing")
    facade = facade.replace(anchor, runtime_methods + anchor, 1)

# The full runtime patch chain has an unrelated additional time helper. Only pending-turn commit
# paths are converted to ActionRuntime, preserving other helpers unchanged.
time_line = "    commands += timeAdvanceCommand(turnId, action)\n"
commit_line = "    val committed = TurnCoordinator.commit(pending.state, commands)"
commit_count = facade.count(commit_line)
time_count = facade.count(time_line)
if commit_count < 2 or time_count < commit_count:
    raise RuntimeError(f"GameCoreFacade commit routing anchors invalid: time={time_count}, commit={commit_count}")
facade = facade.replace(time_line, "", commit_count)
facade = facade.replace(commit_line, "    val committed = commitActionRuntime(pending.state, commands, action, turnId)", commit_count)

for marker in (
    "fun beginAction(legacyStateJson: String, kindRaw: String, action: String)",
    "fun currentActionContext(): String",
    "fun abortAction(reason: String): Boolean",
    "private fun commitActionRuntime(",
    "ActionRuntime.advance(state, active.sessionId, \"resolve\", minutes)",
    "ActionRuntime.markSearchCoverage(",
    "ActionRuntime.complete(finalState, active.sessionId)",
):
    if marker not in facade:
        raise RuntimeError(f"ActionRuntime core bridge missing: {marker}")

FACADE.write_text(facade, encoding="utf-8")
print("Step 2 core bridge applied.")
