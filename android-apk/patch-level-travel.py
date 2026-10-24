"""Final travel-authority wiring. Run after generated runtime patches; safe to rerun."""
from pathlib import Path
ROOT = Path(__file__).resolve().parent
CORE = ROOT / 'app/src/main/java/com/rabpit/backroom/core'
MAIN = ROOT / 'app/src/main/java/com/rabpit/backroom/MainActivity.java'

def once(s, old, new):
    if s.count(old) != 1: raise RuntimeError('Travel patch anchor missing/ambiguous: '+old[:100])
    return s.replace(old,new,1)

f=CORE/'GameCoreFacade.kt'
s=f.read_text()
if 'fun initializeLevelGame(' not in s:
    s=once(s,'  private val logger: GamePipelineLogger\n','  private val logger: GamePipelineLogger,\n  private val levelEngine: LevelTravelEngine\n')
    methods=r'''  /** Only the explicit New Game path may draw a starting Level. No old-save conversion. */
  @Synchronized fun initializeLevelGame(legacyStateJson: String, fresh: Boolean): String {
    val legacy = JSONObject(legacyStateJson)
    var core = if (fresh) levelEngine.start(GameState.initial().copy(world = mapOf(
      "flagsJson" to (legacy.optJSONObject("flags") ?: JSONObject()).toString()
    ))) else {
      val snapshot = legacy.getJSONObject("coreSnapshot")
      require(snapshot.getInt("saveVersion") == CURRENT_SAVE_VERSION) { "save_version_unsupported" }
      val saved = GameStateCodec.decode(snapshot).also { LevelTravelEngine.read(it) }
      val persisted = if (repository.exists()) repository.load() else null
      if (persisted != null && persisted.world[LevelTravelEngine.KEY] != null &&
        LevelTravelEngine.read(persisted).getString("runId") == LevelTravelEngine.read(saved).getString("runId") &&
        (persisted.world["levelTravelUiTurn"]?.toIntOrNull() ?: 1) >= (saved.world["levelTravelUiTurn"]?.toIntOrNull() ?: 1)) persisted else saved
    }
    val recovered = core.world["levelTravelUi"]?.let(::JSONObject) ?: legacy
    val output = LevelTravelEngine.project(core, if (fresh) legacy else recovered)
    output.put("turn", core.world["levelTravelUiTurn"]?.toIntOrNull() ?: 1)
    if (fresh) {
      output.put("turn", 1)
      output.put("log", JSONArray().put(JSONObject().put("role", "gm").put("text",
        "Cao Minh xuất hiện tại " + output.getString("location") + ". Liên lạc với Lục Trầm và Syvial vẫn gián đoạn.")))
    }
    output.remove("coreSnapshot")
    core = core.copy(world = core.world + ("levelTravelUi" to output.toString()))
    repository.save(core)
    output.put("coreSnapshot", JSONObject(GameStateCodec.encode(core)))
    return output.toString()
  }

  /** App startup resumes the committed Core even if the WebView autosave never completed. */
  @Synchronized fun resumeLevelGame(legacyStateJson: String, hasBrowserSave: Boolean): String {
    if (repository.exists()) {
      val core = repository.load()
      if (LevelTravelEngine.KEY in core.world) {
        val legacy = core.world["levelTravelUi"]?.let(::JSONObject) ?: JSONObject(legacyStateJson)
        val output = LevelTravelEngine.project(core, legacy)
        output.put("turn", core.world["levelTravelUiTurn"]?.toIntOrNull() ?: 1)
          .put("coreSnapshot", JSONObject(GameStateCodec.encode(core)))
        return output.toString()
      }
      error("save_version_unsupported")
    }
    return initializeLevelGame(legacyStateJson, !hasBrowserSave)
  }

  /** Runs before ALL local/GM action handlers; a valid exit is fully resolved locally. */
  @Synchronized fun processLevelAction(legacyStateJson: String, action: String): String {
    val legacy = JSONObject(legacyStateJson)
    val current = repository.load()
    val expected = legacy.getJSONObject(LevelTravelEngine.KEY)
    check(LevelTravelEngine.read(current).getString("runId") == expected.getString("runId")) { "stale_game_run" }
    if (!LevelTravelEngine.isExitAction(action)) {
      check(legacy.getInt("turn") == (current.world["levelTravelUiTurn"]?.toIntOrNull() ?: 1)) { "stale_level_turn" }
      return response(false, LevelTravelEngine.project(current, legacy).apply { remove("exitResult") }, null, "not_exit_action")
    }
    return try {
      val runId = legacy.getJSONObject(LevelTravelEngine.KEY).getString("runId")
      val turn = legacy.getInt("turn")
      val outcome = levelEngine.attempt(current, runId, turn, action)
      if (outcome.state == current) {
        val latest = LevelTravelEngine.project(current, current.world["levelTravelUi"]?.let(::JSONObject) ?: legacy)
        latest.put("turn", current.world["levelTravelUiTurn"]?.toIntOrNull() ?: turn + 1)
        latest.put("exitResult", outcome.receipt).put("coreSnapshot", JSONObject(GameStateCodec.encode(current)))
        return response(true, latest, null, "core_exit_duplicate")
      }
      val output = LevelTravelEngine.project(outcome.state, legacy)
      output.put("turn", turn + 1).put("exitResult", outcome.receipt)
      output.put("flags", JSONObject(outcome.state.world["flagsJson"] ?: "{}"))
      val r = outcome.receipt
      val reply = when {
        r.getBoolean("completed") -> "Thoát thành công 3/3. Cao Minh tới " + output.getString("location") + ". Chuỗi thoát mới: 0/3."
        r.getBoolean("success") -> "Lần thử thoát thành công: " + r.getInt("streak") + "/3. Cao Minh vẫn ở địa điểm hiện tại."
        else -> "Lần thử thoát thất bại. Chuỗi trở về 0/3; Cao Minh vẫn ở địa điểm hiện tại."
      }
      appendLog(output, action, reply)
      output.remove("coreSnapshot")
      val committed = outcome.state.copy(world = outcome.state.world + ("levelTravelUi" to output.toString()))
      if (outcome.state != current) repository.save(committed)
      output.put("coreSnapshot", JSONObject(GameStateCodec.encode(if (outcome.state != current) committed else current)))
      response(true, output, null, "core_exit_committed", reply)
    } catch (error: Exception) {
      // Never route a rejected/failed exit to the GM to invent a different result.
      throw IllegalStateException(error.message ?: "exit_transaction_failed", error)
    }
  }

  /** Persist validated local movement; the GM never owns Level identity or streak. */
  @Synchronized fun finishLevelTurn(beforeJson: String, candidateJson: String, action: String): String {
    val current = repository.load()
    val before = JSONObject(beforeJson)
    val travel = LevelTravelEngine.read(current)
    val expected = before.getJSONObject(LevelTravelEngine.KEY)
    check(travel.getString("runId") == expected.getString("runId") &&
      travel.getInt("revision") == expected.getInt("revision") &&
      before.getInt("turn") == (current.world["levelTravelUiTurn"]?.toIntOrNull() ?: 1)) { "stale_level_turn" }
    val candidate = LevelTravelEngine.protectCandidate(current, JSONObject(candidateJson), action)
    candidate.remove("coreSnapshot")
    val next = current.copy(world = current.world + mapOf(
      "location" to candidate.getString("location"), "flagsJson" to candidate.getJSONObject("flags").toString(),
      "levelTravelUiTurn" to candidate.getInt("turn").toString(), "levelTravelUi" to candidate.toString()
    ))
    repository.save(next)
    candidate.put("coreSnapshot", JSONObject(GameStateCodec.encode(next)))
    return candidate.toString()
  }

'''
    s=once(s,'  /** Fast deterministic pass.',methods+'  /** Fast deterministic pass.')
    s=once(s,'SharedPreferencesSaveRepository(context.applicationContext), AndroidGamePipelineLogger(debugLogging)',
       'SharedPreferencesSaveRepository(context.applicationContext), AndroidGamePipelineLogger(debugLogging),\n      LevelTravelEngine(LevelTravelPolicy(JSONObject(context.assets.open("knowledge/level_travel_policy.json").bufferedReader().use { it.readText() })))')
    s=once(s,'    val candidate = JSONObject(candidateJson)','    val candidate = LevelTravelEngine.protectCandidate(loadOrMigrate(before), JSONObject(candidateJson), action)')
    s=once(s,'    state.world["flagsJson"]?.let { output.put("flags", JSONObject(it)) }','    state.world["flagsJson"]?.let { output.put("flags", JSONObject(it)) }\n    if (LevelTravelEngine.KEY in state.world) {\n      val projected = LevelTravelEngine.project(state, output)\n      projected.keys().forEach { output.put(it, projected.get(it)) }\n      output.put("coreSnapshot", JSONObject(GameStateCodec.encode(state)))\n    }')
    f.write_text(s)

# Guard even direct reducer calls from GM-originated spatial state changes.
f=CORE/'StateReducer.kt';s=f.read_text()
if '// LEVEL_TRAVEL_REDUCER_AUTHORITY' not in s:
    s=once(s,'        val worldPatch = mapOfNotNull(','''        // LEVEL_TRAVEL_REDUCER_AUTHORITY: validation is not permission to teleport.
        val spatial = if (LevelTravelEngine.KEY in state.world) LevelTravelEngine.protectCandidate(state,
          JSONObject().put("location", command.location ?: state.world["location"])
            .put("flags", JSONObject(protectedFlags ?: "{}")), state.turn.pending?.input.orEmpty()) else null
        val worldPatch = mapOfNotNull(''')
    s=once(s,'"location" to command.location,','"location" to (spatial?.getString("location") ?: command.location),')
    s=once(s,'"title" to command.title,','"title" to (spatial?.getString("title") ?: command.title),')
    s=once(s,'"levelJson" to command.levelJson,','"levelJson" to (spatial?.getJSONObject("level")?.toString() ?: command.levelJson),')
    s=once(s,'"flagsJson" to protectedFlags','"flagsJson" to (spatial?.getJSONObject("flags")?.toString() ?: protectedFlags)')
    f.write_text(s)

# SharedPreferences.commit reports failure; do not report/emit a successful partial transaction.
f=CORE/'SaveRepository.kt';s=f.read_text()
s=s.replace('    preferences.edit().putString(KEY_STATE, GameStateCodec.encode(state)).commit()',
'''    check(preferences.edit().putString(KEY_STATE, GameStateCodec.encode(state)).commit()) { "core_save_failed" }''')
f.write_text(s)

s=MAIN.read_text()
if '// LEVEL_TRAVEL_BRIDGE' not in s:
    s=once(s,'  private class GameBridge {','''  private class GameBridge {
    // LEVEL_TRAVEL_BRIDGE: synchronous startup/restore; never invoked by rendering.
    @JavascriptInterface public String initializeLevelGame(String stateJson, boolean fresh) {
      return requireGameCore().initializeLevelGame(stateJson, fresh);
    }
    @JavascriptInterface public String levelCoreSnapshot() {
      return requireGameCore().currentCoreState();
    }
''')
    # Both base submitTurn and generated submitTurnInternal have this exact input anchor.
    s=once(s,'          JSONObject before = new JSONObject(stateJson);','''          JSONObject levelAction = new JSONObject(requireGameCore().processLevelAction(stateJson, action));
          if (levelAction.optBoolean("handled", false)) {
            emit("backroomTurn", levelAction.getJSONObject("state").toString());
            return;
          }
          JSONObject before = levelAction.getJSONObject("state");''')
    # Final reconciliation applies after model operations AND any repair passes, just before emit.
    anchor='          emit("backroomTurn", state.toString());'
    s=once(s,anchor,'''          if (!meta) state = new JSONObject(requireGameCore().finishLevelTurn(before.toString(), state.toString(), action));
'''+anchor)

# Eliminate the old RNG path rather than ignoring a draw after it happened.
start=s.index('    int exitThreshold = exitThresholdAndroid(state);',s.index('  private JSONObject makeGameplayRolls(')) if '    int exitThreshold = exitThresholdAndroid(state);' in s else -1
if start>=0:
    end=s.index('    rolls.put("levelExit",',start);end=s.index('\n',end)+1
    s=s[:start]+'''    // Exit attempts are resolved atomically by Core before this turn/GM pipeline.
    rolls.put("exitProbe", new JSONObject().put("eligible", false).put("success", false));
    rolls.put("levelExit", new JSONObject().put("eligible", false).put("success", false));
'''+s[end:]
# Direct JSON proposals cannot alter Level/title/location before narration validators run.
if 'boolean transitionAccepted = !levelChanged || canTransition(before, rolls);' in s:
    start=s.index('            JSONObject proposedLevel = generated.optJSONObject("level");')
    end=s.index('            state.put("player",',start)
    s=s[:start]+'''            boolean levelChanged = false;
            boolean transitionAccepted = false;
'''+s[end:]
# Avoid 0-6 truncation for scene identity; the old encounter tables stay bounded separately.
s=s.replace('return Math.max(0, Math.min(6, level.optInt("number", 0)));','return Math.max(0, level.optInt("number", 0));')
# Keep Canon scoped even with feature flag OFF; do not dump all Level cards to GM.
s=s.replace('"DRIVE CANON:\\n" + DRIVE_CANON + "\\n\\n" +','"DRIVE CANON:\\n" + auditScopeCanon(before, action) + "\\n\\n" +') if 'auditScopeCanon(JSONObject state, String action)' in s else s
# Persistent travel context is a Core result, not an extra RNG or a narrator-authorized transition.
directive='LEVEL TRAVEL HARD LOCK: Core alone selects Level and exit result. Ordinary turns preserve Level and exitStreak. Never narrate escape, a different Level, or a successful exit roll; explicit exit actions are handled locally at 50% and 3 consecutive successes. Describe only current scene; local movement must follow the player action.'
if 'LEVEL TRAVEL HARD LOCK:' not in s:
    if '  private String writerPrompt(' in s:
        start=s.index('  private String writerPrompt(');end=s.index('\n  private ',start+1)
        method=s[start:end];idx=method.rfind('    return ')
        if idx<0:raise RuntimeError('writerPrompt return missing')
        method=method[:idx]+method[idx:].replace('    return ','    return "'+directive+'\\n" + ',1)
        s=s[:start]+method+s[end:]
    else:
        s=once(s,'          JSONObject generated = parseModelJson(','          prompt = "'+directive+'\\n" + prompt;\n          JSONObject generated = parseModelJson(')
MAIN.write_text(s)

# Ensure generated HTML still loads the UI hook after all its state/render definitions.
f=ROOT/'app/src/main/assets/index.html';s=f.read_text()
# Historical HTML patches may append handlers after this tag. Move it last so
# generated reset/load wrappers cannot bypass the Core startup contract.
import re
s=re.sub(r'<script src="level-travel\.js"></script>\r?\n?', '', s)
s=once(s,'</body>','<script src="level-travel.js"></script>\n</body>')
f.write_text(s)
print('Level travel: Core start/exit transaction, no GM transition, old exit RNG removed.')

# Scene identity preserves decimal/special IDs; parent scope is only for record visibility.
f=CORE/'canon/SceneSnapshot.kt';s=f.read_text()
s=s.replace('  val isMeta: Boolean\n)', '  val isMeta: Boolean,\n  val locationNodeId: String? = null\n)');f.write_text(s)
f=CORE/'canon/SceneResolver.kt';s=f.read_text()
s=s.replace('levelId = "LEVEL.%02d".format(Locale.ROOT, currentLevel(state)),','levelId = state.optJSONObject("level")?.optString("canonScope")?.takeIf { it.isNotBlank() }\n        ?: "LEVEL.%02d".format(Locale.ROOT, currentLevel(state)),')
s=s.replace('      isMeta = isMeta\n','      isMeta = isMeta,\n      locationNodeId = state.optJSONObject("levelTravel")?.optString("nodeId")?.takeIf { it.isNotBlank() }\n');f.write_text(s)
f=CORE/'canon/CanonResolver.kt';s=f.read_text()
if '// LEVEL_NODE_EXACT_SCOPE' not in s:
    s=once(s,'      val levelScope = record.activation.levelScope','''      // LEVEL_NODE_EXACT_SCOPE: mentioning another node cannot load its environment.
      if (scene.locationNodeId != null && record.id.startsWith("LEVEL.NODE.") && record.id != scene.locationNodeId) {
        return GateVerdict.Deny("gate0: not the current location node")
      }
      val levelScope = record.activation.levelScope''')
    s=once(s,'    hardReasons[scene.levelId] = "current level direct id"','''    val nodeId = scene.locationNodeId
    val isMainLevel = nodeId?.removePrefix("LEVEL.NODE.")?.matches(Regex("[0-9]+")) ?: true
    if (isMainLevel) hardReasons[scene.levelId] = "current level direct id"
    if (nodeId != null) hardReasons[nodeId] = "current location node direct id"''')
f.write_text(s)
f=CORE/'canon/CanonRuntime2.kt';s=f.read_text()
s=s.replace('if (!CanonRuntime2Flags.isEnabled(context)) {','if (!CanonRuntime2Flags.isEnabled(context) && !JSONObject(stateJson).has("levelTravel")) {')
# Travel never falls back to a prompt without its mandatory current-scene facts.
s=s.replace('return CanonOutcome.FallbackToLegacy("$REASON_STORE_UNAVAILABLE:${e.javaClass.simpleName}")',
 'return if (JSONObject(stateJson).has("levelTravel")) CanonOutcome.SafeLocalResolution("$REASON_STORE_UNAVAILABLE:${e.javaClass.simpleName}", emptyList())\n      else CanonOutcome.FallbackToLegacy("$REASON_STORE_UNAVAILABLE:${e.javaClass.simpleName}")')
if 'import org.json.JSONObject' not in s:s=s.replace('import android.content.Context','import org.json.JSONObject\nimport android.content.Context')
f.write_text(s)
s=MAIN.read_text()
s=s.replace('if (CanonRuntime2Flags.isEnabled(MainActivity.this)) {','if (CanonRuntime2Flags.isEnabled(MainActivity.this) || before.has("levelTravel")) {')
s=s.replace('"DRIVE CANON:\\n" + DRIVE_CANON + "\\n\\n" +','"DRIVE CANON:\\n" + compactDriveCanon(before, action, rolls) + "\\n\\n" +')
if 'compact.remove("coreSnapshot")' not in s:
    s=once(s,'    compact.remove("snapshotUrl");','''    compact.remove("snapshotUrl");
    compact.remove("coreSnapshot");
    JSONObject travel = compact.optJSONObject("levelTravel");
    if (travel != null) travel.remove("receipts");''')
MAIN.write_text(s)
s=MAIN.read_text()
start=s.find('      if (type.equals("set_level")) {')
if start>=0:
    end=s.index('      if (type.equals("patch_player")) {',start)
    s=s[:start]+'      if (type.equals("set_level")) continue; // GM has no Level authority.\n\n'+s[end:]
start=s.find('  private boolean canTransition(')
if start>=0:
    end=s.index('\n  private ',start+1)
    s=s[:start]+s[end:]
s=s.replace('rollSuccess(rolls, "levelExit")','false').replace('canTransition(before, rolls)','false')
s=s.replace('Chuyển Level chỉ khi exitProbe success hoặc state đã khóa transitionReady/exitReady.',
 'Chỉ LevelTravelEngine được chuyển Level khi Core hoàn tất exit streak 3/3; cờ khám phá và GM không có quyền chuyển Level.')
s=s.replace('    rolls.put("exitProbe", new JSONObject().put("eligible", false).put("success", false));\n','')
s=s.replace('    rolls.put("levelExit", new JSONObject().put("eligible", false).put("success", false));\n','')
MAIN.write_text(s)
# Minimal pre-existing Kotlin compile blocker: early returns require a block body.
f=CORE/'Engines.kt';s=f.read_text()
old='  fun execute(state: GameState, command: PartyCommand): ExecutionResult = when (command.operation) {'
if old in s:
    s=once(s,old,'  fun execute(state: GameState, command: PartyCommand): ExecutionResult {\n    return when (command.operation) {')
    s=once(s,'    PartyCommand.Operation.FOLLOW, PartyCommand.Operation.QUERY -> ExecutionResult(state, applied = false)\n  }\n}',
     '    PartyCommand.Operation.FOLLOW, PartyCommand.Operation.QUERY -> ExecutionResult(state, applied = false)\n    }\n  }\n}')
f.write_text(s)
# CR2 infrastructure errors cannot reopen a Level-unaware narration path for travel games.
f=CORE/'canon/CanonRuntime2.kt';s=f.read_text()
s=s.replace('        is ContextPacketResult.PackError ->\n          CanonOutcome.FallbackToLegacy("$REASON_PACK_ERROR:${result.message}")',
 '        is ContextPacketResult.PackError ->\n          if (JSONObject(stateJson).has("levelTravel")) CanonOutcome.SafeLocalResolution("$REASON_PACK_ERROR:${result.message}", emptyList())\n          else CanonOutcome.FallbackToLegacy("$REASON_PACK_ERROR:${result.message}")')
s=s.replace('    } catch (e: Exception) {\n      CanonOutcome.FallbackToLegacy("$REASON_INTERNAL_ERROR:${e.javaClass.simpleName}")',
 '    } catch (e: Exception) {\n      if (JSONObject(stateJson).has("levelTravel")) CanonOutcome.SafeLocalResolution("$REASON_INTERNAL_ERROR:${e.javaClass.simpleName}", emptyList())\n      else CanonOutcome.FallbackToLegacy("$REASON_INTERNAL_ERROR:${e.javaClass.simpleName}")')
f.write_text(s)
# Move the exit transaction ahead of generated local interpreters / ActionRuntime sessions.
s=MAIN.read_text()
start=s.index('          JSONObject levelAction = new JSONObject(requireGameCore().processLevelAction(stateJson, action));')
end=s.index('          JSONObject before = levelAction.getJSONObject("state");',start)
block=s[start:end];s=s[:start]+s[end:]
method=s.find('    private void submitTurnInternal(')
if method<0:method=s.index('    @JavascriptInterface public void submitTurn(String stateJson, String action)')
insert=s.index('        try {\n',method)+len('        try {\n')
s=s[:insert]+block+s[insert:]
local_emit='            emit("backroomTurn", localResult.getJSONObject("state").toString());'
if local_emit in s and '// LEVEL_TRAVEL_LOCAL_COMMIT' not in s:
    s=s.replace(local_emit,'''            // LEVEL_TRAVEL_LOCAL_COMMIT: local item/query paths keep UI and Core positions in sync.
            localResult.put("state", new JSONObject(requireGameCore().finishLevelTurn(
              levelAction.getJSONObject("state").toString(), localResult.getJSONObject("state").toString(), action)));
'''+local_emit)
combat_emit='            emit("backroomTurn", result.getJSONObject("state").toString());'
if combat_emit in s and '// LEVEL_TRAVEL_COMBAT_COMMIT' not in s:
    s=s.replace(combat_emit,'''            // LEVEL_TRAVEL_COMBAT_COMMIT: escape from combat is never escape from a Level.
            result.put("state", new JSONObject(requireGameCore().finishLevelTurn(
              stateJson, result.getJSONObject("state").toString(), action)));
'''+combat_emit)
# Proposed local movement is still subject to Core's action/same-Level validation at final commit.
anchor='            boolean transitionAccepted = false;\n'
if anchor in s and 'if (generated.has("location")) state.put("location", generated.getString("location"));' not in s:
    s=once(s,anchor,anchor+'            if (generated.has("location")) state.put("location", generated.getString("location"));\n')
MAIN.write_text(s)
s=MAIN.read_text()
if 'compact.remove("exitResult")' not in s:
    s=once(s,'    compact.remove("coreSnapshot");','    compact.remove("coreSnapshot");\n    compact.remove("exitResult");')
MAIN.write_text(s)
f=CORE/'canon/CanonResolver.kt';s=f.read_text()
anchor='      // LEVEL_NODE_EXACT_SCOPE: mentioning another node cannot load its environment.'
if '// SUBLEVEL_ENVIRONMENT_SCOPE' not in s:
    s=once(s,anchor,'''      // SUBLEVEL_ENVIRONMENT_SCOPE: the parent's room description is not the sublevel's environment.
      if (scene.locationNodeId != null && record.id == scene.levelId &&
        scene.locationNodeId.removePrefix("LEVEL.NODE.") != scene.levelId.removePrefix("LEVEL.").toIntOrNull()?.toString()) {
        return GateVerdict.Deny("gate0: parent environment is not the current sublevel")
      }
'''+anchor)
f.write_text(s)
f=CORE/'canon/ContextCompiler.kt';s=f.read_text()
if 'appendLine("Location node=' not in s:
    s=once(s,'    appendLine("Actors=${scene.actorsPresent.joinToString(", ")}")',
     '    scene.locationNodeId?.let { appendLine("Location node=$it") }\n    appendLine("Actors=${scene.actorsPresent.joinToString(", ")}")')
f.write_text(s)
s=MAIN.read_text()
if 'memory.remove("levelTravelEvents")' not in s:
    s=once(s,'    compact.remove("exitResult");','''    compact.remove("exitResult");
    JSONObject flagsForPrompt = compact.optJSONObject("flags");
    JSONObject memory = flagsForPrompt == null ? null : flagsForPrompt.optJSONObject("storyContinuity");
    if (memory != null) memory.remove("levelTravelEvents");''')
MAIN.write_text(s)

# Boot must resume an already committed New Game, including before the first browser autosave.
s=MAIN.read_text()
if 'public String resumeLevelGame(' not in s:
    s=once(s,'    @JavascriptInterface public String levelCoreSnapshot() {',
     '    @JavascriptInterface public String resumeLevelGame(String stateJson, boolean hasBrowserSave) {\n      return requireGameCore().resumeLevelGame(stateJson, hasBrowserSave);\n    }\n    @JavascriptInterface public String levelCoreSnapshot() {')
MAIN.write_text(s)
