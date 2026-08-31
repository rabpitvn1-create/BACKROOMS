from pathlib import Path

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"

facade = FACADE.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global facade
    count = facade.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    facade = facade.replace(old, new, 1)


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

'''
if "private fun forwardProgressionError(state: GameState, requestedLevelId: String): String?" not in facade:
    replace_once(
        "  fun exportDirectorTelemetry(): String = backroomsDirector.exportTelemetry()\n",
        helper + "  fun exportDirectorTelemetry(): String = backroomsDirector.exportTelemetry()\n",
        "forward progression helper",
    )

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

for marker in (
    "private val levelCatalog: LevelCatalog",
    "AndroidLevelCatalog.load(appContext)",
    "ForwardProgressionPolicy.evaluate(",
    "progression_blocked",
    "Level progression only moves forward",
    "forwardProgressionError(current, levelId)?.let { throw IllegalStateException(it) }",
):
    if marker not in facade:
        raise RuntimeError("forward progression runtime contract missing: " + marker)

FACADE.write_text(facade, encoding="utf-8")
print("Forward progression contract applied: registered/procedural Level entry is catalog-ordered, completion-gated, and backward transitions fail closed.")
