from pathlib import Path

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

facade = FACADE.read_text(encoding="utf-8")
main = MAIN.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global facade
    count = facade.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    facade = facade.replace(old, new, 1)


def replace_main_once(old: str, new: str, label: str) -> None:
    global main
    count = main.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    main = main.replace(old, new, 1)


if "private val levelCatalog: LevelCatalog" not in facade:
    replace_once(
        '''  private val levelRegistry: LevelRegistry,
  private val backroomsDirector: BackroomsDirector
) : AutoCloseable {''',
        '''  private val levelRegistry: LevelRegistry,
  private val levelCatalog: LevelCatalog,
  private val backroomsDirector: BackroomsDirector
) : AutoCloseable {''',
        "LevelCatalog facade dependency",
    )
    replace_once(
        '''        AndroidLevelRegistry.load(appContext),
        BackroomsDirector.liteRT(appContext)
''',
        '''        AndroidLevelRegistry.load(appContext),
        AndroidLevelCatalog.load(appContext),
        BackroomsDirector.liteRT(appContext)
''',
        "LevelCatalog facade construction",
    )

helper = r'''  private fun forwardProgressionError(state: GameState, requestedLevelId: String): String? {
    val current = state.levelInstance
    val decision = ForwardProgressionPolicy.evaluate(
      levelCatalog,
      current?.levelId,
      current?.completed ?: false,
      requestedLevelId
    )
    return decision.reason.takeUnless { decision.allowed }
  }

  fun handoffCompletedRegisteredLevel(targetLevelId: String): Boolean {
    val current = repository.load()
    val active = current.levelInstance ?: return false
    val target = targetLevelId.trim()
    if (!active.completed || target.isEmpty() || target == active.levelId || levelRegistry.contains(target)) return false
    val decision = ForwardProgressionPolicy.evaluate(levelCatalog, active.levelId, true, target)
    if (!decision.allowed) return false
    repository.save(current.copy(
      levelInstance = null,
      world = current.world + ("levelId" to target),
      metadata = current.metadata + ("lastCompletedRegisteredLevelId" to active.levelId)
    ))
    return true
  }

'''
if "private fun forwardProgressionError(state: GameState, requestedLevelId: String): String?" not in facade:
    replace_once(
        "  fun exportDirectorTelemetry(): String = backroomsDirector.exportTelemetry()\n",
        helper + "  fun exportDirectorTelemetry(): String = backroomsDirector.exportTelemetry()\n",
        "forward progression helper",
    )

resolver_anchor = '''    val exploration = legacy.optJSONObject("flags")?.optJSONObject("exploration")
    val legacyAreaId = exploration?.optString("areaId")?.takeIf(String::isNotBlank)
    val levelId = legacyAreaId
      ?: state.world["levelId"]?.takeIf(String::isNotBlank)
      ?: state.levelInstance?.levelId
    if (levelId.isNullOrBlank() || !levelRegistry.contains(levelId)) {
      return response(false, legacy, null, "registered_level_not_handled")
    }
'''
resolver_replacement = '''    val exploration = legacy.optJSONObject("flags")?.optJSONObject("exploration")
    val legacyAreaId = exploration?.optString("areaId")?.takeIf(String::isNotBlank)
    val activeLevel = state.levelInstance
    val levelId = activeLevel?.takeUnless { it.completed }?.levelId
      ?: legacyAreaId
      ?: state.world["levelId"]?.takeIf(String::isNotBlank)
      ?: activeLevel?.levelId

    if (activeLevel?.completed == true && !legacyAreaId.isNullOrBlank() &&
      legacyAreaId != activeLevel.levelId && !levelRegistry.contains(legacyAreaId)
    ) {
      if (!handoffCompletedRegisteredLevel(legacyAreaId)) {
        val error = "progression_handoff_rejected:${activeLevel.levelId}:$legacyAreaId"
        return response(true, legacy, error, "registered_level_rejected")
      }
      val handedOff = repository.load()
      return response(false, syncLegacy(legacy, handedOff, incrementTurn = false), null, "registered_level_handoff")
    }

    if (levelId.isNullOrBlank() || !levelRegistry.contains(levelId)) {
      return response(false, legacy, null, "registered_level_not_handled")
    }
'''
if "val levelId = activeLevel?.takeUnless { it.completed }?.levelId" not in facade:
    replace_once(resolver_anchor, resolver_replacement, "registered Level core identity precedence and placeholder handoff")

registered_anchor = '''    if (levelId.isNullOrBlank() || !levelRegistry.contains(levelId)) {
      return response(false, legacy, null, "registered_level_not_handled")
    }

    val runSeed = state.levelInstance?.takeIf { it.levelId == levelId }?.runSeed
'''
registered_replacement = '''    if (levelId.isNullOrBlank() || !levelRegistry.contains(levelId)) {
      return response(false, legacy, null, "registered_level_not_handled")
    }
    forwardProgressionError(state, levelId)?.let { error ->
      val reply = "[Warning] Level progression only moves forward; a completed Level cannot become current again."
      return response(true, legacy, error, "registered_level_rejected", reply)
    }

    val runSeed = state.levelInstance?.takeIf { it.levelId == levelId }?.runSeed
'''
if "Level progression only moves forward" not in facade:
    replace_once(registered_anchor, registered_replacement, "registered Level forward guard")

success_anchor = '''    return response(true, output, null, "registered_level_committed", reply)
'''
success_replacement = '''    return JSONObject(response(true, output, null, "registered_level_committed", reply))
      .put("progressed", result.progressed)
      .put("escaped", result.escaped)
      .toString()
'''
if '.put("escaped", result.escaped)' not in facade:
    replace_once(success_anchor, success_replacement, "registered Level completion signal")

candidate_core_anchor = '''    val core = loadOrMigrate(before)
    val turnId = nextTurnId(before, core)
'''
candidate_core_replacement = '''    val core = loadOrMigrate(before)
    val registeredNavigationLocked = core.levelInstance?.completed == false
    if (registeredNavigationLocked) {
      if (before.has("location")) candidate.put("location", before.opt("location")) else candidate.remove("location")
      if (before.has("title")) candidate.put("title", before.opt("title")) else candidate.remove("title")
      before.optJSONObject("level")?.let { candidate.put("level", JSONObject(it.toString())) } ?: candidate.remove("level")
      val candidateFlags = candidate.optJSONObject("flags") ?: JSONObject().also { candidate.put("flags", it) }
      val beforeFlags = before.optJSONObject("flags")
      beforeFlags?.optJSONObject("exploration")?.let {
        candidateFlags.put("exploration", JSONObject(it.toString()))
      }
      beforeFlags?.optJSONObject("currentLevel")?.let {
        candidateFlags.put("currentLevel", JSONObject(it.toString()))
      }
    }
    val turnId = nextTurnId(before, core)
'''
if "val registeredNavigationLocked = core.levelInstance?.completed == false" not in facade:
    replace_once(candidate_core_anchor, candidate_core_replacement, "Gemini registered-Level navigation freeze")

prepare_anchor = '''    if (state.levelInstance?.levelId == levelId) {
      return JSONObject().put("required", false).put("reason", "level_instance_exists").put("levelId", levelId).toString()
    }

    val runSeed = state.metadata["runSeed"]?.takeIf(String::isNotBlank)
'''
prepare_replacement = '''    if (state.levelInstance?.levelId == levelId) {
      return JSONObject().put("required", false).put("reason", "level_instance_exists").put("levelId", levelId).toString()
    }
    forwardProgressionError(state, levelId)?.let { error ->
      return JSONObject().put("required", false).put("reason", "progression_blocked")
        .put("levelId", levelId).put("error", error).toString()
    }

    val runSeed = state.metadata["runSeed"]?.takeIf(String::isNotBlank)
'''
if 'put("reason", "progression_blocked")' not in facade:
    replace_once(prepare_anchor, prepare_replacement, "procedural generation forward guard")

commit_anchor = '''    if (existing?.levelId == levelId && existing.runSeed == runSeed) {
      return JSONObject().put("accepted", true).put("reason", "already_committed")
        .put("generationId", existing.generationId)
        .put("fingerprint", existing.generationFingerprint ?: JSONObject.NULL).toString()
    }

    return try {
'''
commit_replacement = '''    if (existing?.levelId == levelId && existing.runSeed == runSeed) {
      return JSONObject().put("accepted", true).put("reason", "already_committed")
        .put("generationId", existing.generationId)
        .put("fingerprint", existing.generationFingerprint ?: JSONObject.NULL).toString()
    }
    forwardProgressionError(current, levelId)?.let { error ->
      return JSONObject().put("accepted", false).put("error", error).toString()
    }

    return try {
'''
if "forwardProgressionError(current, levelId)?.let { error ->" not in facade:
    replace_once(commit_anchor, commit_replacement, "generated candidate forward guard")

fallback_anchor = '''    return try {
      val current = repository.load()
      val installed = GenericLevelRuntime.install(
        current.copy(metadata = current.metadata + ("runSeed" to runSeed)),
'''
fallback_replacement = '''    return try {
      val current = repository.load()
      forwardProgressionError(current, levelId)?.let { error ->
        return JSONObject().put("accepted", false).put("error", error).toString()
      }
      val installed = GenericLevelRuntime.install(
        current.copy(metadata = current.metadata + ("runSeed" to runSeed)),
'''
if facade.count("forwardProgressionError(current, levelId)?.let { error ->") < 2:
    replace_once(fallback_anchor, fallback_replacement, "definition fallback forward guard")

install_anchor = '''  fun installRegisteredLevel(levelId: String, runSeed: String): String {
    val installed = GenericLevelRuntime.install(repository.load(), levelRegistry, levelId, runSeed)
    repository.save(installed)
    return GameStateCodec.encode(installed)
  }
'''
install_replacement = '''  fun installRegisteredLevel(levelId: String, runSeed: String): String {
    val current = repository.load()
    forwardProgressionError(current, levelId)?.let { throw IllegalStateException(it) }
    val installed = GenericLevelRuntime.install(current, levelRegistry, levelId, runSeed)
    repository.save(installed)
    return GameStateCodec.encode(installed)
  }
'''
if "forwardProgressionError(current, levelId)?.let { throw IllegalStateException(it) }" not in facade:
    replace_once(install_anchor, install_replacement, "direct registered install forward guard")

core_call = "requireGameCore()" if "requireGameCore().processRegisteredLevelAction(stateJson, actionKind, action)" in main else "gameCore"

java_guard_helpers = f'''  private boolean hasIncompleteRegisteredLevel() {{
    try {{
      JSONObject core = new JSONObject({core_call}.currentCoreState());
      JSONObject instance = core.optJSONObject("levelInstance");
      return instance != null && !instance.optBoolean("completed", false);
    }} catch (Exception ignored) {{
      return false;
    }}
  }}

  private String registeredLevelNarrativeLock() {{
    if (!hasIncompleteRegisteredLevel()) return "";
    return " REGISTERED LEVEL HARD LOCK: Core xác nhận Level hiện tại chưa hoàn tất. "
      + "Không mô tả người chơi đã sang Level hoặc khu của Level khác; không dẫn tới môi trường của Level khác; "
      + "không thay đổi title/location/level/flags điều hướng cho đến khi Core trả escaped=true.";
  }}

  private boolean attemptsRegisteredNavigation(JSONObject before, JSONObject generated) {{
    if (!hasIncompleteRegisteredLevel()) return false;
    JSONObject proposedLevel = generated.optJSONObject("level");
    if (proposedLevel != null && proposedLevel.optInt("number", currentLevel(before)) != currentLevel(before)) return true;
    if (generated.has("title") && !generated.optString("title", "").equals(before.optString("title", ""))) return true;
    return generated.has("location") && !generated.optString("location", "").equals(before.optString("location", ""));
  }}

'''
can_transition_sig = '''  private boolean canTransition(JSONObject before, JSONObject rolls) {
'''
if "private boolean hasIncompleteRegisteredLevel()" not in main:
    replace_main_once(can_transition_sig, java_guard_helpers + can_transition_sig, "registered Level legacy authority guard helpers")

if "if (hasIncompleteRegisteredLevel()) return false;" not in main:
    replace_main_once(
        can_transition_sig,
        can_transition_sig + '''    if (hasIncompleteRegisteredLevel()) return false;
''',
        "registered Level legacy transition gate",
    )

if "linearAreaPrompt(before) + registeredLevelNarrativeLock()" not in main:
    if main.count("linearAreaPrompt(before)") != 1:
        raise RuntimeError(f"registered Level narrative lock: expected exactly 1 linearAreaPrompt call, found {main.count('linearAreaPrompt(before)')}")
    main = main.replace("linearAreaPrompt(before)", "linearAreaPrompt(before) + registeredLevelNarrativeLock()", 1)

reply_anchor = '''          String reply = generated.optString("reply", "").trim();
          if (reply.isEmpty()) reply = "Kai giữ nguyên vị trí và quan sát thêm; chưa có kết quả đủ chắc chắn để thay đổi trạng thái.";
'''
reply_replacement = '''          String reply = generated.optString("reply", "").trim();
          if (reply.isEmpty()) reply = "Kai giữ nguyên vị trí và quan sát thêm; chưa có kết quả đủ chắc chắn để thay đổi trạng thái.";
          if (attemptsRegisteredNavigation(before, generated)) {
            reply = "Kai vẫn ở khu vực hiện tại. Hành động này không tạo ra chuyển dịch nào được Core xác nhận.";
          }
'''
if "Hành động này không tạo ra chuyển dịch nào được Core xác nhận." not in main:
    replace_main_once(reply_anchor, reply_replacement, "registered Level narration fail-closed")

location_anchor = '''            if (generated.has("location")) state.put("location", generated.optString("location"));
'''
location_replacement = '''            if (transitionAccepted && generated.has("location")) state.put("location", generated.optString("location"));
'''
if location_replacement.strip() not in main:
    replace_main_once(location_anchor, location_replacement, "legacy location transition authority")

registered_block_anchor = f'''          JSONObject registeredLevelResult = new JSONObject({core_call}.processRegisteredLevelAction(stateJson, actionKind, action));
          if (registeredLevelResult.optBoolean("handled", false)) {{
            emit("backroomTurn", registeredLevelResult.getJSONObject("state").toString());
            return;
          }}
'''
registered_block_replacement = f'''          JSONObject registeredLevelResult = new JSONObject({core_call}.processRegisteredLevelAction(stateJson, actionKind, action));
          if (registeredLevelResult.optBoolean("handled", false)) {{
            JSONObject registeredState = registeredLevelResult.getJSONObject("state");
            if (registeredLevelResult.optBoolean("escaped", false) && advanceLinearArea(new JSONObject(stateJson), registeredState)) {{
              JSONObject flags = registeredState.optJSONObject("flags");
              JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
              String nextAreaId = exploration != null ? exploration.optString("areaId", "") : "";
              if (!nextAreaId.isEmpty()) {core_call}.handoffCompletedRegisteredLevel(nextAreaId);
            }}
            emit("backroomTurn", registeredState.toString());
            return;
          }}
'''
if "handoffCompletedRegisteredLevel(nextAreaId)" not in main:
    replace_main_once(registered_block_anchor, registered_block_replacement, "registered escape atomic route advance")

for marker in (
    "private val levelCatalog: LevelCatalog",
    "AndroidLevelCatalog.load(appContext)",
    "ForwardProgressionPolicy.evaluate(",
    "fun handoffCompletedRegisteredLevel(targetLevelId: String): Boolean",
    '"lastCompletedRegisteredLevelId" to active.levelId',
    "val levelId = activeLevel?.takeUnless { it.completed }?.levelId",
    '"registered_level_handoff"',
    '.put("escaped", result.escaped)',
    "val registeredNavigationLocked = core.levelInstance?.completed == false",
    "seeded, levelRegistry, levelCatalog, kind, action, levelId, runSeed, backroomsDirector",
    "progression_blocked",
    "Level progression only moves forward",
    "forwardProgressionError(current, levelId)?.let { throw IllegalStateException(it) }",
):
    if marker not in facade:
        raise RuntimeError("forward progression runtime contract missing: " + marker)

for marker in (
    "private boolean hasIncompleteRegisteredLevel()",
    "if (hasIncompleteRegisteredLevel()) return false;",
    "linearAreaPrompt(before) + registeredLevelNarrativeLock()",
    "attemptsRegisteredNavigation(before, generated)",
    'if (transitionAccepted && generated.has("location"))',
    "advanceLinearArea(new JSONObject(stateJson), registeredState)",
    "handoffCompletedRegisteredLevel(nextAreaId)",
):
    if marker not in main:
        raise RuntimeError("registered Level/legacy synchronization contract missing: " + marker)

FACADE.write_text(facade, encoding="utf-8")
MAIN.write_text(main, encoding="utf-8")
print("Forward progression contract applied: Core owns incomplete registered Levels, legacy navigation is frozen, and registered escape advances exactly once to the catalog target.")
