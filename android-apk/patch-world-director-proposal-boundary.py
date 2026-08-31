from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
DIRECTOR = CORE / "BackroomsDirector.kt"
WORLD = CORE / "WorldDirector.kt"
FACADE = CORE / "GameCoreFacade.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# Keep the WorldDirector source conservative across Kotlin compiler versions used by the Android
# build. The final runtime uses an explicit lambda for enum parsing rather than a static method ref.
world = WORLD.read_text(encoding="utf-8")
world = world.replace(
    ".map(WorldPressureProposal::valueOf).toList()",
    ".map { WorldPressureProposal.valueOf(it) }.toList()",
)
WORLD.write_text(world, encoding="utf-8")


# Evidence ownership moved back to deterministic Core selection. Keep the legacy class for source
# compatibility, but the app factory must no longer load the evidence-source LiteRT policy because
# the shared model/labels are now trained for broad world-pressure proposals.
director = DIRECTOR.read_text(encoding="utf-8")
old_factory = '''    fun liteRT(context: Context): BackroomsDirector {
      val appContext = context.applicationContext
      val policy = LiteRTBackroomsDirectorPolicy(appContext)
      return BackroomsDirector(
        policy,
        policy,
        SharedPreferencesBackroomsDirectorTelemetryStore(appContext)
      )
    }
'''
new_factory = '''    fun liteRT(context: Context): BackroomsDirector {
      val appContext = context.applicationContext
      return BackroomsDirector(
        telemetry = SharedPreferencesBackroomsDirectorTelemetryStore(appContext)
      )
    }
'''
if new_factory.strip() not in director:
    director = replace_once(director, old_factory, new_factory, "deterministic evidence director factory")
DIRECTOR.write_text(director, encoding="utf-8")


facade = FACADE.read_text(encoding="utf-8")
constructor_old = '''  private val levelRegistry: LevelRegistry,
  private val levelCatalog: LevelCatalog,
  private val backroomsDirector: BackroomsDirector
) : AutoCloseable {'''
constructor_new = '''  private val levelRegistry: LevelRegistry,
  private val levelCatalog: LevelCatalog,
  private val backroomsDirector: BackroomsDirector,
  private val worldDirector: WorldDirector
) : AutoCloseable {'''
if "private val worldDirector: WorldDirector" not in facade:
    facade = replace_once(facade, constructor_old, constructor_new, "WorldDirector facade dependency")

create_old = '''        AndroidLevelRegistry.load(appContext),
        AndroidLevelCatalog.load(appContext),
        BackroomsDirector.liteRT(appContext)
'''
create_new = '''        AndroidLevelRegistry.load(appContext),
        AndroidLevelCatalog.load(appContext),
        BackroomsDirector.liteRT(appContext),
        WorldDirector.liteRT(appContext)
'''
if "WorldDirector.liteRT(appContext)" not in facade:
    facade = replace_once(facade, create_old, create_new, "WorldDirector facade construction")

close_old = '''  override fun close() {
    localModel.close()
    backroomsDirector.close()
  }
'''
close_new = '''  override fun close() {
    localModel.close()
    backroomsDirector.close()
    worldDirector.close()
  }
'''
if "worldDirector.close()" not in facade:
    facade = replace_once(facade, close_old, close_new, "WorldDirector lifecycle")

proposal_method = r'''  fun proposeWorldPressure(kindRaw: String): String {
    val state = repository.load()
    val kind = enumValues<ActionKind>().firstOrNull { it.name == kindRaw.trim().uppercase() }
      ?: return JSONObject()
        .put("proposed", JSONObject.NULL)
        .put("accepted", WorldPressureProposal.NONE.name)
        .put("reason", "action_kind_invalid")
        .toString()
    val level = state.levelInstance
    val definition = level?.levelId?.let(levelRegistry::get)
    val decision = if (definition == null) {
      WorldDirectorDecision(null, WorldPressureProposal.NONE, "no_registered_level", "")
    } else {
      worldDirector.propose(state, definition, kind)
    }
    logger.log(PipelineLogEvent(
      "WORLD_DIRECTOR_PROPOSAL",
      turnId = state.metadata["lastAction.turnId"],
      details = mapOf(
        "kind" to kind.name,
        "proposed" to (decision.proposed?.name ?: "ABSTAIN"),
        "accepted" to decision.accepted.name,
        "reason" to decision.reason
      )
    ))
    return JSONObject()
      .put("proposed", decision.proposed?.name ?: JSONObject.NULL)
      .put("accepted", decision.accepted.name)
      .put("reason", decision.reason)
      .toString()
  }

'''
telemetry_anchor = "  fun exportDirectorTelemetry(): String = backroomsDirector.exportTelemetry()\n"
if "fun proposeWorldPressure(kindRaw: String): String" not in facade:
    facade = replace_once(facade, telemetry_anchor, proposal_method + telemetry_anchor, "WorldDirector proposal facade")

# Registered-Level actions now exercise LiteRT WorldDirector once per action, but the returned
# proposal is deliberately ignored by the resolver. This PR establishes the authority boundary and
# telemetry only; a later integration may consume accepted proposals through idempotent Core events.
registered_anchor = '''    if (levelId.isNullOrBlank() || !levelRegistry.contains(levelId)) {
      return response(false, legacy, null, "registered_level_not_handled")
    }
'''
registered_replacement = registered_anchor + '''    proposeWorldPressure(kind.name)
'''
if "    proposeWorldPressure(kind.name)\n" not in facade:
    facade = replace_once(facade, registered_anchor, registered_replacement, "registered WorldDirector proposal hook")

for marker in (
    "private val worldDirector: WorldDirector",
    "WorldDirector.liteRT(appContext)",
    "worldDirector.close()",
    "fun proposeWorldPressure(kindRaw: String): String",
    '"WORLD_DIRECTOR_PROPOSAL"',
    "worldDirector.propose(state, definition, kind)",
    "proposeWorldPressure(kind.name)",
):
    if marker not in facade:
        raise RuntimeError("WorldDirector runtime contract missing: " + marker)

for forbidden in (
    "val policy = LiteRTBackroomsDirectorPolicy(appContext)\n      return BackroomsDirector(",
):
    if forbidden in director:
        raise RuntimeError("LiteRT evidence ownership was not removed from app factory")

if "map { WorldPressureProposal.valueOf(it) }" not in world:
    raise RuntimeError("WorldDirector label parsing compatibility contract missing")

FACADE.write_text(facade, encoding="utf-8")
print("WorldDirector proposal boundary applied: LiteRT proposes broad world pressure, Core gates legality/liveness, evidence selection remains deterministic, and proposals do not mutate gameplay state.")
