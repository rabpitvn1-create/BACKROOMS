from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
OLD_ENGINE = CORE / "ExitDiscoveryEngine.kt"
OLD_TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/ExitDiscoveryEngineTest.kt"

java = MAIN.read_text(encoding="utf-8")
html = INDEX.read_text(encoding="utf-8")

def replace_once(source, old, new, description):
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{description}: expected one anchor, found {count}")
    return source.replace(old, new, 1)

def replace_span(source, start, end, replacement, label):
    if source.count(start) != 1:
        raise RuntimeError(f"{label}: expected one start marker, found {source.count(start)}")
    i = source.index(start)
    if end not in source[i:]:
        raise RuntimeError(f"{label}: missing end marker")
    j = source.index(end, i)
    return source[:i] + replacement + source[j:]

# The 14 main Levels use an explicit, Core-validated forward itinerary.
# Named areas and Sub-levels are not silently assigned gameplay edges.
helpers = r'''  // EXIT_STREAK_V1: Android Core exclusively owns progress and level transitions.
  private String currentFeaturedStop(JSONObject state) {
    JSONObject level = state.optJSONObject("level");
    String stopKey = level == null ? "" : level.optString("stopKey", "");
    if (!com.rabpit.backroom.core.progression.FeaturedJourneyRoutes.contains(stopKey)) {
      return "level-" + currentLevel(state); // upgrade existing main-Level saves
    }
    return stopKey;
  }

  private JSONObject normalizedStreakState(JSONObject original) throws Exception {
    JSONObject state = new JSONObject(original.toString());
    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) { flags = new JSONObject(); state.put("flags", flags); }
    // Retire ALL prior exit flags (including old discovered exits) in old saves.
    for (String key : new String[] {"exitProgress", "exitCandidate", "confirmedExit", "exitChanceThreshold"}) {
      flags.remove(key);
    }
    JSONObject exploration = flags.optJSONObject("exploration");
    if (exploration == null) { exploration = new JSONObject(); flags.put("exploration", exploration); }
    for (String key : new String[] {"levelExit", "exitProgress", "exitCandidate", "confirmedExit",
        "confirmedExitMigrated", "exitReady", "transitionReady", "lastExitDiscard",
        "commandId", "minimumTurns"}) {
      exploration.remove(key);
    }
    String node = currentFeaturedStop(state);
    int streak = exploration.optInt("exitStreak", 0);
    if (!node.equals(exploration.optString("exitStreakNode", "")) || streak < 0 || streak >= 5) streak = 0;
    exploration.put("exitStreakNode", node);
    exploration.put("exitStreak", streak);
    return state;
  }

  private JSONObject withExitStreak(JSONObject original, int streak) throws Exception {
    JSONObject state = new JSONObject(original.toString());
    JSONObject flags = state.optJSONObject("flags");
    if (flags == null) { flags = new JSONObject(); state.put("flags", flags); }
    JSONObject exploration = flags.optJSONObject("exploration");
    if (exploration == null) { exploration = new JSONObject(); flags.put("exploration", exploration); }
    exploration.put("exitStreakNode", currentFeaturedStop(state));
    exploration.put("exitStreak", streak);
    return state;
  }

  /** Returns null when the existing content graph has no outbound route. */
  private JSONObject applyStreakTransition(JSONObject original) throws Exception {
    int fromLevel = currentLevel(original);
    com.rabpit.backroom.core.progression.FeaturedJourneyRoute route =
      com.rabpit.backroom.core.progression.FeaturedJourneyRoutes.next(currentFeaturedStop(original));
    if (route == null) return null;
    int toLevel = route.getTargetLevelNumber();
    JSONObject state = new JSONObject(original.toString());
    JSONObject level = state.optJSONObject("level");
    if (level == null) { level = new JSONObject(); state.put("level", level); }
    level.put("number", toLevel);
    level.put("nodeId", route.getTargetNodeId());
    level.put("stopKey", route.getTargetStopKey());
    String stopName = route.getTargetStopKey().startsWith("area:")
      ? "Level " + toLevel + " / " + route.getTargetTitle()
      : "Level " + route.getTargetStopKey().substring(6) + " - " + route.getTargetTitle();
    level.put("name", stopName);
    state.put("title", stopName);
    state.put("journeyStopKey", route.getTargetStopKey());
    state.put("worldNodeId", route.getTargetNodeId());
    state.put("location", "");
    state = withExitStreak(state, 0);
    return state;
  }

'''
# Derive the exact injected helper string from the legacy generator itself.
# Never erase unrelated follower/gameplay helpers that happen to follow this block.
import ast
generator_tree = ast.parse((ROOT / "patch-exit-discovery-engine.py").read_text(encoding="utf-8"))
legacy_glue = next(
    node.value.value
    for node in generator_tree.body
    if isinstance(node, ast.Assign)
    and any(isinstance(target, ast.Name) and target.id == "new_glue" for target in node.targets)
)
java = replace_once(java, legacy_glue, helpers, "retire exact legacy helper block")

# Old Java name lookup clamps Level 7–13 to Level 6. Keep original Level 0–6
# labels stable; resolve higher main-Level titles from the Core content catalog.
java = replace_once(java,
    '''  private String levelName(int number) {
    String[] names = {"The Lobby", "Parking Zone", "Pipe Dreams", "The Electrical Station", "The Abandoned Office", "Terror Hotel", "Lights Out"};
    int safe = Math.max(0, Math.min(6, number));
    return names[safe];
  }''',
    '''  private String levelName(int number) {
    String[] legacyNames = {"The Lobby", "Parking Zone", "Pipe Dreams", "The Electrical Station", "The Abandoned Office", "Terror Hotel", "Lights Out"};
    if (number >= 0 && number < legacyNames.length) return legacyNames[number];
    String title = com.rabpit.backroom.core.progression.MainLevelExitRoutes.titleFor(number);
    return title != null ? title : "Unknown Level";
  }''',
    "Core main-level titles 7–13")


# The old conditional level gate was a legacy exit-discovery dependency.
java = replace_span(java,
    "  private boolean canTransition(JSONObject before, JSONObject rolls) {",
    "\n  private ",
    """  private boolean canTransition(JSONObject before, JSONObject rolls) {
    // The AI cannot authorize a level transition, regardless of its text or ops.
    return false;
  }
""",
    "remove old exit gate implementation")

# The original generator created eleven imports solely for the retired exit engine.
for obsolete in (
    "DiscoveryEligibility", "ExitDiscoveryEngine", "ExitDiscoveryInput",
    "ExitDiscoveryOutcome", "ExitRecord", "ExitStatus", "IntRoller",
    "LinearWorldRouteResolver", "TraverseInput", "TraverseValidation",
    "WorldRouteResolver",
):
    java = java.replace("import com.rabpit.backroom.core." + obsolete + ";\n", "")
java = replace_once(java,
    "import com.rabpit.backroom.core.GameCoreFacade;\n",
    "import com.rabpit.backroom.core.GameCoreFacade;\n"
    "import com.rabpit.backroom.core.ExitStreakEngine;\n"
    "import com.rabpit.backroom.core.ExitStreakOutcome;\n",
    "streak imports")

# Old per-action exit rolls and follower exit bonuses are removed, not blended with 50/50.
java = replace_span(java,
    "    // EXIT_AUTHORITY_V1: exit discovery is owned by ExitDiscoveryEngine",
    "    return rolls;\n",
    "    // EXIT_STREAK_V1: a single independent fair coin is rolled only in submitAction.\n",
    "retire old exit probe and companion bonus")

# All local/game-rule and combat intercepts still run first. A combat turn returns
# before the streak engine. Its text length is not restricted by ordinary-turn rules.
dispatch = r'''          // EXIT_STREAK_V1: combat, local commands, and UI meta are NOT streak turns.
          JSONObject originalInput = new JSONObject(stateJson);
          boolean combatTurnForStreak = com.rabpit.backroom.core.CombatChoiceEngine.isActive(originalInput);
          if (!combatTurnForStreak && !isMetaAction(action)
              && !ExitStreakEngine.hasMinimumInput(action)) {
            emit("backroomError", "Hành động không hợp lệ: Nội dung phải có ít nhất 15 ký tự.");
            return;
          }
          if (requireGameCore().blocksTextItemAction(action)) {
            JSONObject blocked = new JSONObject(requireGameCore().processRule(stateJson, action));
            emit("backroomTurn", blocked.getJSONObject("state").toString());
            return;
          }
          JSONObject combatResult = new JSONObject(requireGameCore().processCombat(stateJson, actionKind, action));
          if (combatResult.optBoolean("handled", false)) {
            emit("backroomTurn", combatResult.getJSONObject("state").toString());
            return;
          }
          if (combatTurnForStreak) throw new Exception("Combat chưa kết thúc: không xử lý lượt khám phá.");
          JSONObject actionStart = new JSONObject(requireGameCore().beginAction(stateJson, actionKind, action));
          if (!actionStart.optBoolean("handled", false)) {
            throw new Exception("Action Runtime từ chối hành động: " + actionStart.optString("error", "action_start_failed"));
          }
          JSONObject localResult = new JSONObject(requireGameCore().processRule(stateJson, action));
          if (localResult.optBoolean("handled", false)) {
            emit("backroomTurn", localResult.getJSONObject("state").toString());
            return;
          }

          JSONObject before = new JSONObject(stateJson);
          boolean meta = isMetaAction(action);
          int streakFromLevel = currentLevel(before);
          String streakFromStopKey = currentFeaturedStop(before);
          boolean streakLevelCompleted = false;
          JSONObject rolls;
          if (meta) {
            rolls = makeGameplayRolls(before, actionKind, action, true);
          } else {
            before = normalizedStreakState(before);
            int previousStreak = before.getJSONObject("flags").getJSONObject("exploration").optInt("exitStreak", 0);
            ExitStreakOutcome progress = ExitStreakEngine.advance(
              previousStreak, action, false, bound -> GAME_RNG.nextInt(bound));
            if (!progress.getAccepted() || !progress.getEvaluated()) {
              throw new Exception("Không thể xử lý streak cho lượt hợp lệ.");
            }
            JSONObject newState = withExitStreak(before, progress.getStreak());
            if (progress.getCompleted()) {
              JSONObject transitioned = applyStreakTransition(newState);
              if (transitioned != null) {
                newState = transitioned;
                streakLevelCompleted = true;
              } else {
                // No authorized outbound route: do not persist an impossible 5/5 state.
                newState = withExitStreak(newState, 0);
              }
            }
            before = newState;
            rolls = streakLevelCompleted
              ? new JSONObject().put("turn", before.optInt("turn", 1)).put("meta", false)
              : makeGameplayRolls(before, actionKind, action, false);
            rolls.put("exitStreak", new JSONObject()
              .put("evaluated", true)
              .put("success", Boolean.TRUE.equals(progress.getSuccess()))
              .put("streak", before.getJSONObject("flags").getJSONObject("exploration").optInt("exitStreak", 0))
              .put("target", ExitStreakEngine.REQUIRED_WINS)
              .put("completed", streakLevelCompleted)
              .put("fromLevel", streakFromLevel)
              .put("fromStopKey", streakFromStopKey)
              .put("toStopKey", currentFeaturedStop(before))
              .put("toLevel", currentLevel(before))
              .put("chance", "50/50")
              .put("reason", progress.getCompleted() && !streakLevelCompleted ? "no_route" : "ok"));
          }
'''
java = replace_span(java,
    "          // EXIT_AUTHORITY_V1: TraverseExitCommand is a system command",
    "            if (!meta) before = applyExitDiscoveryOutcome(before, rolls);\n          }\n",
    dispatch,
    "replace old traverse/discovery dispatch with automatic streak")
java = replace_once(java,
    "            if (!meta) before = applyExitDiscoveryOutcome(before, rolls);\n          }\n",
    "",
    "remove obsolete discovery tail")

# Preserve the route chosen by Core if AI attempts to forge a different Level.
java = replace_once(java,
    "          if (traverseTurn) {\n            // EXIT_AUTHORITY_V1: the traverse transition is already committed and validated.",
    "          if (streakLevelCompleted) {\n            // EXIT_STREAK_V1: the transition is already committed by Android Core.",
    "post-commit authoritative transition")
java = replace_once(java,
    "          boolean transitionAccepted = traverseTurn || !levelChanged || canTransition(before, rolls);",
    "          boolean transitionAccepted = streakLevelCompleted || !levelChanged || canTransition(before, rolls);",
    "AI transition guard")
java = replace_once(java,
    "recordLevelProgress(state, traverseTurn && traverseFromLevel >= 0 ? traverseFromLevel : oldLevel, newLevel)",
    "recordLevelProgress(state, streakLevelCompleted ? streakFromLevel : oldLevel, newLevel)",
    "level-progress reset on streak completion")

# Pass native Core-owned streak completion into the atomic validated-state commit.
java = replace_once(java,
    "requireGameCore().processValidatedCandidate(before.toString(), candidateState.toString(), action)",
    "requireGameCore().processValidatedCandidateWithStreak(before.toString(), candidateState.toString(), action, streakFromLevel, streakFromStopKey, streakLevelCompleted)",
    "native streak completion to Core commit")

# Never accept streak or streak-node changes from AI ops or candidate-state merges.
java = replace_once(java,
    '          patchValue.remove("levelExit");',
    '          patchValue.remove("levelExit");\n'
    '          patchValue.remove("exitStreak");\n'
    '          patchValue.remove("exitStreakNode");',
    "ops anti-forgery")
java = replace_once(java,
    '      patchExploration.remove("levelExit");',
    '      patchExploration.remove("levelExit");\n'
    '      patchExploration.remove("exitStreak");\n'
    '      patchExploration.remove("exitStreakNode");',
    "candidate anti-forgery")

# No obsolete six-turn gate should be written back to the state.
java = replace_once(java,
    '    exploration.put("minimumTurns", 6);',
    '    // EXIT_STREAK_V1: no minimum-turn exit gate.',
    "remove six-turn gate")

# Level 7–13 do not have packaged local level snapshots. Never silently
# substitute a Level 0 image for a different Level. Gemini snapshots, when
# available, remain authoritative for the current scene.
for previous, replacement in (
    ("var pool=refs[lv]||refs[0];", "var pool=refs[lv]||[];"),
    ("var fallback=pool[frame%pool.length]||refs[0][0];",
     "var fallback=pool.length?pool[frame%pool.length]:'';"),
    ("var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r?r.dataUri:fallback;",
     "var bg=document.createElement('img');bg.className='snapshot-bg';if(r||fallback)bg.src=r?r.dataUri:fallback;else bg.style.display='none';"),
    ("if(!r)bg.onerror=function(){this.onerror=null;this.src=refs[lv]?refs[lv][0]:refs[0][0];};",
     "if(!r)bg.onerror=function(){this.onerror=null;this.style.display='none';};"),
):
    java = replace_once(java, previous, replacement, "no fake local snapshot for Level 7-13")

# Keep meta labels, but make short-input errors explicit rather than "Gemini errors".
html = html.replace('statusEl.textContent="Lỗi Gemini: "+message',
                    'statusEl.textContent=String(message).startsWith("Hành động không hợp lệ:")?message:"Lỗi Gemini: "+message')

# Update Core persistence in the SAME candidate commit, not as a second save.
# Validated state remains protected against candidate-forged stats.
facade_path = CORE / "GameCoreFacade.kt"
facade = facade_path.read_text(encoding="utf-8")
facade = replace_once(facade,
    "  fun processValidatedCandidate(beforeJson: String, candidateJson: String, action: String): String {",
    """  fun processValidatedCandidate(beforeJson: String, candidateJson: String, action: String): String =
    processValidatedCandidateInternal(beforeJson, candidateJson, action, -1, "", false)

  fun processValidatedCandidateWithStreak(
    beforeJson: String, candidateJson: String, action: String,
    streakFromLevel: Int, streakFromStopKey: String, streakLevelCompleted: Boolean
  ): String = processValidatedCandidateInternal(
    beforeJson, candidateJson, action, streakFromLevel, streakFromStopKey, streakLevelCompleted
  )

  private fun processValidatedCandidateInternal(
    beforeJson: String, candidateJson: String, action: String,
    streakFromLevel: Int, streakFromStopKey: String, streakLevelCompleted: Boolean
  ): String {""",
    "expose native-only streak-gated candidate commit")

old_save = """    val protectedState = CharacterProgressionCore.protectFromCandidate(pending.state, committed.state)
    repository.save(protectedState)
    val synchronized = syncLegacy(candidate, protectedState, incrementTurn = false)"""
new_save = """    val protectedState = CharacterProgressionCore.protectFromCandidate(pending.state, committed.state)
    // Source stop is stored in Core world, not accepted from Gemini.
    val storedLevelJson = pending.state.world["levelJson"]
      ?: return response(false, before, "streak_missing_saved_level", "streak_transition_rejected")
    val storedLevel = try { JSONObject(storedLevelJson).optInt("number", -1) } catch (_: Exception) { -1 }
    val storedStopKey = pending.state.world["journeyStopKey"].orEmpty().ifBlank {
      try { JSONObject(storedLevelJson).optString("stopKey", "") } catch (_: Exception) { "" }
    }.ifBlank { "level-$storedLevel" }
    val routes = com.rabpit.backroom.core.progression.FeaturedJourneyRoutes
    if (!routes.contains(storedStopKey) ||
        routes.stopLevelNumber(storedStopKey) != storedLevel) {
      return response(false, before, "streak_unrecognized_saved_stop", "streak_transition_rejected")
    }
    val trustedNode = pending.state.world["worldNodeId"].orEmpty()
    val requiredSourceNode = routes.nodeIdAt(storedStopKey).orEmpty()
    val registeredNode = com.rabpit.backroom.core.progression.WorldProgressionCore
      .rankOf(com.rabpit.backroom.core.progression.WorldNodeId(trustedNode))
      is com.rabpit.backroom.core.progression.RankLookup.Known
    // Older main-Level saves may contain a stale registered full-Level worldNodeId.
    // Never apply this exception to an already entered Sub-level or named area.
    val legacyReconciled = trustedNode != requiredSourceNode &&
      storedStopKey == "level-$storedLevel" &&
      Regex("^level-[0-9]+$").matches(trustedNode) && registeredNode
    if ((trustedNode != requiredSourceNode && !legacyReconciled) || !registeredNode) {
      return response(false, before, "streak_core_node_mismatch", "streak_transition_rejected")
    }
    val nativeLevel = before.optJSONObject("level")
      ?: return response(false, before, "streak_missing_native_level", "streak_transition_rejected")
    val expectedRoute = if (streakLevelCompleted) routes.next(storedStopKey) else null
    val checkedWorld = if (streakLevelCompleted) {
      val route = expectedRoute
        ?: return response(false, before, "streak_no_authorized_route", "streak_transition_rejected")
      if (streakFromStopKey != storedStopKey || streakFromLevel != storedLevel ||
          nativeLevel.optString("stopKey") != route.targetStopKey ||
          nativeLevel.optString("nodeId") != route.targetNodeId ||
          nativeLevel.optInt("number", -1) != route.targetLevelNumber ||
          candidate.optJSONObject("level")?.optString("stopKey") != route.targetStopKey ||
          candidate.optJSONObject("level")?.optInt("number", -1) != route.targetLevelNumber) {
        return response(false, before, "streak_target_mismatch", "streak_transition_rejected")
      }
      val title = if (route.targetIsNamedArea)
        "Level " + route.targetLevelNumber + " / " + route.targetTitle
      else "Level " + route.targetStopKey.removePrefix("level-") + " - " + route.targetTitle
      val targetLevelJson = JSONObject()
        .put("number", route.targetLevelNumber)
        .put("nodeId", route.targetNodeId)
        .put("stopKey", route.targetStopKey)
        .put("name", title)
      protectedState.world + mapOf(
        "worldNodeId" to route.targetNodeId,
        "journeyStopKey" to route.targetStopKey,
        "levelJson" to targetLevelJson.toString(),
        "title" to title,
        "location" to ""
      )
    } else {
      // No earned exit: a model's candidate cannot change node or stop key.
      if (nativeLevel.optInt("number", -1) != storedLevel ||
          nativeLevel.optString("stopKey", storedStopKey) != storedStopKey) {
        return response(false, before, "streak_source_mismatch", "streak_transition_rejected")
      }
      val safeLevel = JSONObject(storedLevelJson).put("stopKey", storedStopKey)
      protectedState.world + mapOf(
        "worldNodeId" to requiredSourceNode,
        "journeyStopKey" to storedStopKey,
        "levelJson" to safeLevel.toString()
      )
    }
    // Native roll output, not Gemini ops, owns streak across a save/load.
    val safeFlags = JSONObject(checkedWorld["flagsJson"] ?: "{}")
    val safeExploration = safeFlags.optJSONObject("exploration") ?: JSONObject().also {
      safeFlags.put("exploration", it)
    }
    val nativeExploration = before.optJSONObject("flags")?.optJSONObject("exploration")
    if (nativeExploration != null) {
      for (key in listOf("exitStreak", "exitStreakNode")) {
        if (nativeExploration.has(key)) safeExploration.put(key, nativeExploration.get(key))
      }
    }
    val authoritative = protectedState.copy(
      world = checkedWorld + ("flagsJson" to safeFlags.toString()),
      metadata = if (legacyReconciled) protectedState.metadata +
        ("progression.legacyNodeReconciled" to (trustedNode + "->" + requiredSourceNode))
      else protectedState.metadata
    )
    repository.save(authoritative)
    val synchronized = syncLegacy(candidate, authoritative, incrementTurn = false)"""
facade = replace_once(facade, old_save, new_save, "atomic Core worldNodeId persistence")
for required in ("processValidatedCandidateWithStreak", "FeaturedJourneyRoutes",
                 '"worldNodeId" to route.targetNodeId', "repository.save(authoritative)"):
    if required not in facade:
        raise RuntimeError("Core streak authority missing: " + required)
facade_path.write_text(facade, encoding="utf-8")

# The original patch script may continue to exist in Git history, but its runtime
# outputs must not survive in the shipped APK or compiled test source.
OLD_ENGINE.unlink(missing_ok=True)
OLD_TEST.unlink(missing_ok=True)

for marker in (
    "EXIT_STREAK_V1", "ExitStreakEngine.advance(", "ExitStreakEngine.hasMinimumInput(action)",
    "FeaturedJourneyRoutes.next(currentFeaturedStop(original))",
    "streakFromStopKey", 'level.put("stopKey", route.getTargetStopKey())',
    "FeaturedJourneyRoutes.next(currentFeaturedStop(original))", "MainLevelExitRoutes.titleFor(number)",
    "streakLevelCompleted", 'patchValue.remove("exitStreak")',
    'patchExploration.remove("exitStreak")', 'put("exitStreak"', "combatTurnForStreak",
):
    if marker not in java:
        raise RuntimeError("Missing streak integration marker: " + marker)
# Some older patches leave descriptive comments mentioning the retired engine.
# Remove stale comments, but NEVER mask executable references to it.
for line in java.splitlines():
    if "ExitDiscoveryEngine" not in line:
        continue
    if line.lstrip().startswith(("//", "*")):
        java = java.replace(line, line.replace("ExitDiscoveryEngine", "retired-exit-system"))
    else:
        raise RuntimeError("Executable legacy engine reference: " + line.strip()[:240])

for banned in (
    "ExitDiscoveryEngine", "TraverseExitCommand", "migrateLegacyExitTurnState",
    "applyExitDiscoveryOutcome", "isTraverseExitCommand(", "private int exitThresholdAndroid",
    'rolls.put("exitDiscovery"', '"traverse_exit"',
):
    if banned in java:
        raise RuntimeError("Legacy exit runtime survives: " + banned)
if OLD_ENGINE.exists() or OLD_TEST.exists():
    raise RuntimeError("Legacy exit engine/test files were not retired")
MAIN.write_text(java, encoding="utf-8")
INDEX.write_text(html, encoding="utf-8")
print("EXIT_STREAK_V1 active: every accepted non-combat turn rolls one 50/50 streak, five wins transition automatically.")
