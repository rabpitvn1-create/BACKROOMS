from pathlib import Path

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

facade = FACADE.read_text(encoding="utf-8")
main = MAIN.read_text(encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# A deterministic/local handled result is terminal for the current ActionRuntime session.
# abortAction must therefore clear the matching PendingTurn too. Provider failures do NOT call
# abortAction; patch-pending-turn-idempotent-rng keeps those sessions retryable on purpose.
old_abort = '''  fun abortAction(reason: String): Boolean {
    if (!repository.exists()) return false
    val state = repository.load()
    val active = ActionRuntime.activeSession(state) ?: return false
    val interrupted = ActionRuntime.interrupt(state, active.sessionId, reason.ifBlank { "pipeline_error" })
    if (!interrupted.applied) return false
    repository.save(interrupted.state)
    return true
  }
'''
new_abort = '''  fun abortAction(reason: String): Boolean {
    if (!repository.exists()) return false
    var next = repository.load()
    val active = ActionRuntime.activeSession(next) ?: return false
    val terminalReason = reason.ifBlank { "pipeline_error" }
    val interrupted = ActionRuntime.interrupt(next, active.sessionId, terminalReason)
    if (!interrupted.applied) return false
    next = interrupted.state
    TurnCoordinator.recover(next)?.let { pending ->
      if (pending.turnId == active.turnId) {
        val abandoned = TurnCoordinator.abandon(next, pending.turnId, terminalReason)
        if (abandoned.error == null) next = abandoned.state
      }
    }
    repository.save(next)
    return true
  }
'''
if new_abort.strip() not in facade:
    facade = replace_once(facade, old_abort, new_abort, "abortAction pending cleanup")


# Self-heal saves produced by older APKs where a local rejection returned to the UI but left an
# ActionRuntime lock behind. A genuine provider retry always still owns a recoverable PendingTurn
# with the same turn id, so it is deliberately preserved.
begin_sig = '  fun beginAction(legacyStateJson: String, kindRaw: String, action: String): String {\n'
begin_start = facade.find(begin_sig)
begin_end = facade.find('\n  fun currentActionContext()', begin_start)
if begin_start < 0 or begin_end < 0:
    raise RuntimeError("beginAction block not found")
begin_block = facade[begin_start:begin_end]
if 'stale_terminal_session' not in begin_block:
    begin_block = replace_once(
        begin_block,
        '    val state = loadOrMigrate(legacy)\n',
        '    var state = loadOrMigrate(legacy)\n',
        "beginAction mutable state",
    )
    existing_anchor = '    val existing = ActionRuntime.activeSession(state)\n'
    stale_guard = '''    val staleSession = ActionRuntime.activeSession(state)
    if (staleSession != null) {
      val recoverable = TurnCoordinator.recover(state)
      val terminalOrOrphaned = staleSession.turnId in state.turn.completedTurnIds ||
        recoverable == null || recoverable.turnId != staleSession.turnId
      if (terminalOrOrphaned && abortAction("stale_terminal_session")) {
        state = repository.load()
      }
    }
    val existing = ActionRuntime.activeSession(state)
'''
    begin_block = replace_once(begin_block, existing_anchor, stale_guard, "beginAction stale-session self-heal")
    facade = facade[:begin_start] + begin_block + facade[begin_end:]


# processRule handled=true means the deterministic/local path has finished and control returns to
# the player. If that path rejected before commit, it may still have an ActionRuntime session. Clear
# it before emitting the turn. Provider fallback uses handled=false and therefore never enters here.
submit_start = main.find('    private void submitTurnInternal(String stateJson, String actionKind, String action) {')
if submit_start < 0:
    raise RuntimeError("submitTurnInternal block not found")
handled_anchor = '          if (localResult.optBoolean("handled", false)) {\n'
handled_pos = main.find(handled_anchor, submit_start)
if handled_pos < 0:
    raise RuntimeError("local handled branch not found")
if 'abortAction("local_terminal")' not in main[handled_pos:handled_pos + 500]:
    core_call = 'requireGameCore()' if 'requireGameCore().processRule(stateJson, action)' in main else 'gameCore'
    handled_replacement = handled_anchor + f'            try {{ {core_call}.abortAction("local_terminal"); }} catch (Exception ignored) {{}}\n'
    main = main[:handled_pos] + main[handled_pos:].replace(handled_anchor, handled_replacement, 1)


for marker in (
    'abortAction("stale_terminal_session")',
    'recoverable == null || recoverable.turnId != staleSession.turnId',
    'TurnCoordinator.abandon(next, pending.turnId, terminalReason)',
):
    if marker not in facade:
        raise RuntimeError("ActionRuntime terminal cleanup facade marker missing: " + marker)

if 'abortAction("local_terminal")' not in main:
    raise RuntimeError("ActionRuntime local terminal cleanup Android marker missing")

# Do not regress the provider retry contract. Provider failures must retain PendingTurn + RNG state.
if 'markActionRetryableFailure("pipeline_error")' not in main:
    raise RuntimeError("provider retry preservation marker missing")

FACADE.write_text(facade, encoding="utf-8")
MAIN.write_text(main, encoding="utf-8")
print("ActionRuntime terminal cleanup applied: local handled paths release stale locks; provider retries remain resumable.")
