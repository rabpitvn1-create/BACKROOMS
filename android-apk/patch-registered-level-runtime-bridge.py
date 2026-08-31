from pathlib import Path

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

facade = FACADE.read_text(encoding="utf-8")
main = MAIN.read_text(encoding="utf-8")

facade_methods = r'''  fun processRegisteredLevelAction(legacyStateJson: String, kindRaw: String, action: String): String {
    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
    val kind = enumValues<ActionKind>().firstOrNull { it.name == kindRaw.trim().uppercase() }
      ?: return response(false, legacy, "action_kind_invalid", "registered_level_not_handled")
    val exploration = legacy.optJSONObject("flags")?.optJSONObject("exploration")
    val legacyAreaId = exploration?.optString("areaId")?.takeIf(String::isNotBlank)
    val levelId = state.levelInstance?.levelId
      ?: state.world["levelId"]?.takeIf(String::isNotBlank)
      ?: legacyAreaId
    if (levelId.isNullOrBlank() || !levelRegistry.contains(levelId)) {
      return response(false, legacy, null, "registered_level_not_handled")
    }

    val runSeed = state.levelInstance?.runSeed
      ?: state.metadata["runSeed"]
      ?: "run-${System.currentTimeMillis()}"
    val seeded = if (state.metadata["runSeed"].isNullOrBlank()) {
      state.copy(metadata = state.metadata + ("runSeed" to runSeed))
    } else state

    val result = RegisteredLevelActionCoordinator.applyStarted(
      seeded, levelRegistry, kind, action, levelId, runSeed
    )
    if (!result.handled) return response(false, legacy, result.error, "registered_level_not_handled")

    if (result.error != null) {
      var failed = result.state
      ActionRuntime.activeSession(failed)?.let { active ->
        val interrupted = ActionRuntime.interrupt(failed, active.sessionId, result.error)
        if (interrupted.applied) failed = interrupted.state
      }
      repository.save(failed)
      val output = syncLegacy(legacy, failed, incrementTurn = false)
      val reply = "[Warning] Hành động Level không thể commit: ${result.error}."
      appendLog(output, action, reply)
      return response(true, output, result.error, "registered_level_rejected", reply)
    }

    repository.save(result.state)
    val output = syncLegacy(legacy, result.state, incrementTurn = true)
    val reply = result.reply ?: if (result.progressed) "Môi trường đã thay đổi." else "Không có tiến triển mới."
    appendLog(output, action, reply)
    logger.log(PipelineLogEvent(
      "REGISTERED_LEVEL_COMMIT",
      turnId = result.state.metadata["lastAction.turnId"],
      details = mapOf(
        "levelId" to levelId,
        "kind" to kind.name,
        "progressed" to result.progressed.toString(),
        "escaped" to result.escaped.toString()
      )
    ))
    return response(true, output, null, "registered_level_committed", reply)
  }

  fun restoreCoreState(raw: String): Boolean {
    if (raw.isBlank()) return false
    return try {
      val restored = GameStateCodec.decode(raw)
      if (restored.saveVersion != CURRENT_SAVE_VERSION) return false
      val level = restored.levelInstance
      if (level != null) {
        if (!levelRegistry.contains(level.levelId)) return false
        val definition = levelRegistry.require(level.levelId)
        if (!BlueprintValidator.validate(level, definition).valid) return false
      }
      repository.save(restored)
      true
    } catch (_: Exception) {
      false
    }
  }

'''

if "fun processRegisteredLevelAction(legacyStateJson: String, kindRaw: String, action: String)" not in facade:
    anchor = "  fun currentCoreState(): String = GameStateCodec.encode(repository.load())\n"
    if facade.count(anchor) != 1:
        raise RuntimeError("registered Level facade anchor missing")
    facade = facade.replace(anchor, facade_methods + anchor, 1)

core_call = "requireGameCore()" if "requireGameCore().processRule(stateJson, action)" in main else "gameCore"
local_line = f'''          JSONObject localResult = new JSONObject({core_call}.processRule(stateJson, action));
'''
if "processRegisteredLevelAction(stateJson, actionKind, action)" not in main:
    if main.count(local_line) != 1:
        raise RuntimeError("registered Level processRule anchor missing")
    level_block = f'''          JSONObject registeredLevelResult = new JSONObject({core_call}.processRegisteredLevelAction(stateJson, actionKind, action));
          if (registeredLevelResult.optBoolean("handled", false)) {{
            emit("backroomTurn", registeredLevelResult.getJSONObject("state").toString());
            return;
          }}
'''
    main = main.replace(local_line, level_block + local_line, 1)

bridge_methods = f'''    @JavascriptInterface public String exportCoreState() {{
      try {{ return {core_call}.currentCoreState(); }}
      catch (Exception ignored) {{ return ""; }}
    }}

    @JavascriptInterface public boolean restoreCoreState(String coreJson) {{
      try {{ return {core_call}.restoreCoreState(coreJson); }}
      catch (Exception ignored) {{ return false; }}
    }}

'''
if "@JavascriptInterface public String exportCoreState()" not in main:
    anchor = "  private class GameBridge {\n"
    if main.count(anchor) != 1:
        raise RuntimeError("registered Level GameBridge anchor missing")
    main = main.replace(anchor, anchor + bridge_methods, 1)

for marker in (
    "fun processRegisteredLevelAction(legacyStateJson: String, kindRaw: String, action: String)",
    "RegisteredLevelActionCoordinator.applyStarted(",
    "legacyAreaId",
    "fun restoreCoreState(raw: String): Boolean",
    "BlueprintValidator.validate(level, definition).valid",
    ".processRegisteredLevelAction(stateJson, actionKind, action)",
    '@JavascriptInterface public String exportCoreState()',
    '@JavascriptInterface public boolean restoreCoreState(String coreJson)',
):
    source = facade if marker.startswith("fun ") or marker in ("RegisteredLevelActionCoordinator.applyStarted(", "legacyAreaId", "BlueprintValidator.validate(level, definition).valid") else main
    if marker not in source:
        raise RuntimeError("registered Level runtime bridge missing: " + marker)

FACADE.write_text(facade, encoding="utf-8")
MAIN.write_text(main, encoding="utf-8")
print("Registered Level runtime bridge applied: typed Level actions commit locally before legacy dice/Gemini, with canon-validated private core save/restore.")
