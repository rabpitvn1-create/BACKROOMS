from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
MARKER = "PERSISTENT_FOUNDATION_RUNTIME_R01"


def method_bounds(source: str, signature: str) -> tuple[int, int]:
    start = source.find(signature)
    if start < 0:
        raise RuntimeError(f"method signature missing: {signature}")
    brace = source.find("{", start)
    depth = 0
    for index in range(brace, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                end = index + 1
                while end < len(source) and source[end] in "\r\n":
                    end += 1
                return start, end
    raise RuntimeError(f"method closing brace missing: {signature}")


def replace_method(source: str, signature: str, replacement: str) -> str:
    start, end = method_bounds(source, signature)
    return source[:start] + replacement.rstrip() + "\n\n" + source[end:]


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return source.replace(old, new, 1)


core = CORE.read_text(encoding="utf-8")
if "fun foundationStateProjection()" not in core:
    projection = r'''  /** PERSISTENT_FOUNDATION_RUNTIME_R01: sanitized, Core-owned dependency projection. */
  fun foundationStateProjection(): String {
    val state = repository.load()
    return JSONObject().apply {
      put("projectionSchemaVersion", 1)
      put("saveVersion", state.saveVersion)
      put("level", JSONObject().apply {
        state.levelInstance?.let { level ->
          put("levelId", level.levelId)
          put("currentZoneId", level.currentZoneId)
          put("environmentTags", JSONArray(level.environmentTags.sorted()))
          put("phenomena", JSONArray(level.phenomena.sorted()))
          put("discoveredFacts", JSONArray(level.discoveredFacts.sorted()))
          put("completedActions", JSONArray(level.completedActions))
          put("revision", level.revision)
          put("completed", level.completed)
        }
      })
      put("world", JSONObject().apply {
        state.world.entries.sortedBy { it.key }.filter { it.key != "flagsJson" }.forEach { (key, value) -> put(key, value) }
      })
      put("story", JSONObject().apply {
        put("planId", state.story.planId)
        put("storyId", state.story.storyId)
        put("campaignId", state.story.campaignId)
        put("chapterId", state.story.chapterId ?: JSONObject.NULL)
        put("actId", state.story.actId ?: JSONObject.NULL)
        put("questId", state.story.questId ?: JSONObject.NULL)
        put("objectiveId", state.story.objectiveId ?: JSONObject.NULL)
        put("status", state.story.status.name)
        put("revision", state.story.revision)
      })
      put("party", JSONObject()
        .put("leaderId", state.party.leaderId)
        .put("memberIds", JSONArray(state.party.memberIds.sorted())))
      put("characters", JSONObject().apply {
        state.characters.entries.sortedBy { it.key }.forEach { (id, character) ->
          put(id, JSONObject()
            .put("name", character.name)
            .put("presence", character.presence.name)
            .put("healthState", character.healthState ?: JSONObject.NULL)
            .put("injuries", JSONArray(character.injuries))
            .put("statusIds", JSONArray(character.statusIds.sorted())))
        }
      })
      put("inventory", JSONObject().apply {
        state.inventories.entries.sortedBy { it.key }.forEach { (ownerId, inventory) ->
          put(ownerId, JSONArray().apply {
            inventory.items.values.sortedBy { it.itemId }.forEach { item ->
              put(JSONObject().put("itemId", item.itemId).put("quantity", item.quantity).put("contentState", item.contentState.name))
            }
          })
        }
      })
      put("equipment", JSONObject().apply {
        state.equipment.entries.sortedBy { it.key }.forEach { (ownerId, equipment) ->
          put(ownerId, JSONObject(equipment.slots.toSortedMap()))
        }
      })
      put("statuses", JSONArray(state.statuses.keys.sorted()))
    }.toString()
  }

'''
    core = replace_once(core, "  fun currentCoreState(): String = GameStateCodec.encode(repository.load())\n", projection + "  fun currentCoreState(): String = GameStateCodec.encode(repository.load())\n", "Core projection")
    CORE.write_text(core, encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")
if MARKER in main:
    raise SystemExit(0)

main = replace_once(
    main,
    "        gameCore = GameCoreFacade.create(getApplicationContext(), BuildConfig.DEBUG);\n",
    "        gameCore = GameCoreFacade.create(getApplicationContext(), BuildConfig.DEBUG);\n"
    "        com.rabpit.backroom.core.foundation.FoundationRuntime.warm(\n"
    "          getApplicationContext(), gameCore.foundationStateProjection());\n",
    "Foundation warm start",
)

generate_start, generate_end = method_bounds(main, "  private String generateText(String prompt) throws Exception ")
structured = r'''

  /* PERSISTENT_FOUNDATION_RUNTIME_R01 */
  private int providerTimeout(int preferredMs, int minimumMs) throws Exception {
    TurnBudget budget = activeTurnBudget.get();
    return budget == null ? preferredMs : budget.timeoutMs(preferredMs, minimumMs);
  }

  private String generateStructuredText(String prompt, AiResponseSchemas.Role role, TurnBudget budget) throws Exception {
    budget.throwIfExpired();
    activeTurnBudget.set(budget);
    try {
      return AiProviderRouter.route(
        prompt,
        this::hakuFallbackText,
        this::lunaText,
        this::providerFallbackEligible,
        (provider, response) -> AiResponseSchemas.validate(role, parseModelJson(response).toString()),
        new AiProviderRouter.Observer() {
          @Override public void onSelected(String provider) {
            emit("backroomProvider", "AI provider selected: " + provider);
          }

          @Override public void onFallback(String fromProvider, String toProvider, Exception error) {
            emit("backroomProvider", fromProvider + " failed; fallback to " + toProvider);
          }
        }
      );
    } finally {
      activeTurnBudget.remove();
    }
  }
'''
main = main[:generate_end] + structured + main[generate_end:]

field_anchor = "  private final AtomicInteger latestSnapshotTurn = new AtomicInteger(0);\n"
main = replace_once(
    main,
    field_anchor,
    field_anchor + "  private final ThreadLocal<TurnBudget> activeTurnBudget = new ThreadLocal<>();\n",
    "turn budget field",
)

for signature, replacements in (
    ("  private String postJsonHakuFallback(JSONObject payload) throws Exception ", {
        "connection.setConnectTimeout(5000);": "connection.setConnectTimeout(providerTimeout(5000, 250));",
        "connection.setReadTimeout(30000);": "connection.setReadTimeout(providerTimeout(30000, 500));",
    }),
    ("  private String postJsonLunaFast(String endpoint, String key, String authHeader, JSONObject payload) throws Exception ", {
        "connection.setConnectTimeout(5000);": "connection.setConnectTimeout(providerTimeout(5000, 250));",
        "connection.setReadTimeout(22000);": "connection.setReadTimeout(providerTimeout(22000, 500));",
    }),
):
    start, end = method_bounds(main, signature)
    method = main[start:end]
    for old, new in replacements.items():
        if method.count(old) != 1:
            raise RuntimeError(f"timeout anchor missing in {signature}: {old}")
        method = method.replace(old, new, 1)
    main = main[:start] + method + main[end:]

foundation_helper = r'''  private String foundationPacket(JSONObject before, String action, JSONObject rolls, String role, String turnId) throws Exception {
    String packet = com.rabpit.backroom.core.foundation.FoundationRuntime.buildSlice(
      MainActivity.this,
      turnId,
      requireGameCore().foundationStateProjection(),
      before.toString(),
      action,
      rolls.toString(),
      role);
    if (packet == null || packet.trim().isEmpty()) {
      return com.rabpit.backroom.core.knowledge.KnowledgeContextEngine.build(
        MainActivity.this, before.toString(), action, rolls.toString());
    }
    return packet;
  }

  private String auditScopeCanon(JSONObject before, String action, JSONObject rolls, String scope, String turnId) throws Exception {
    String role = "character".equals(scope) ? "character_audit" : "canon_audit";
    return foundationPacket(before, action, rolls, role, turnId);
  }'''
main = replace_method(
    main,
    "  private String auditScopeCanon(JSONObject before, String action, JSONObject rolls, String scope) ",
    foundation_helper,
)

run_signature = "  private JSONObject runAudit(JSONObject before, String action, JSONObject rolls, JSONObject generated, JSONObject candidateState, String scope, int excludedWorker) throws Exception "
run_start, run_end = method_bounds(main, run_signature)
run_method = main[run_start:run_end]
run_method = run_method.replace(
    run_signature.strip(),
    "private JSONObject runAudit(JSONObject before, String action, JSONObject rolls, JSONObject generated, JSONObject candidateState, String scope, int excludedWorker, String turnId, TurnBudget budget) throws Exception",
    1,
)
run_method = replace_once(run_method, "auditScopeCanon(before, action, rolls, scope)", "auditScopeCanon(before, action, rolls, scope, turnId)", "audit slice pin")
run_method = replace_once(
    run_method,
    "JSONObject result = parseModelJson(geminiAuditText(prompt, excludedWorker));",
    "JSONObject result = new JSONObject(generateStructuredText(prompt, AiResponseSchemas.Role.AUDIT, budget));",
    "strict audit schema",
)
main = main[:run_start] + run_method + main[run_end:]

audits = r'''  private JSONArray auditsForRisk(JSONObject before, String action, JSONObject rolls, JSONObject generated, JSONObject candidateState, int risk, int writerWorker, String turnId, TurnBudget budget) throws Exception {
    JSONArray audits = new JSONArray();
    if (risk < 4) return audits;
    if (risk < 7) {
      audits.put(runAudit(before, action, rolls, generated, candidateState, "canon", writerWorker, turnId, budget));
      return audits;
    }

    Future<JSONObject> canon = auditIo.submit(() -> runAudit(before, action, rolls, generated, candidateState, "canon", writerWorker, turnId, budget));
    Future<JSONObject> character = auditIo.submit(() -> runAudit(before, action, rolls, generated, candidateState, "character", writerWorker, turnId, budget));
    try {
      audits.put(canon.get(budget.futureTimeout(java.util.concurrent.TimeUnit.MILLISECONDS), java.util.concurrent.TimeUnit.MILLISECONDS));
      audits.put(character.get(budget.futureTimeout(java.util.concurrent.TimeUnit.MILLISECONDS), java.util.concurrent.TimeUnit.MILLISECONDS));
      return audits;
    } catch (Exception error) {
      canon.cancel(true);
      character.cancel(true);
      throw error;
    }
  }'''
main = replace_method(
    main,
    "  private JSONArray auditsForRisk(JSONObject before, String action, JSONObject rolls, JSONObject generated, JSONObject candidateState, int risk, int writerWorker) throws Exception ",
    audits,
)

writer_signature = "  private String writerPrompt(JSONObject before, String action, JSONObject rolls, JSONArray auditFeedback) throws Exception "
writer_start, writer_end = method_bounds(main, writer_signature)
writer = main[writer_start:writer_end]
writer = writer.replace(
    writer_signature.strip(),
    "private String writerPrompt(JSONObject before, String action, JSONObject rolls, JSONArray auditFeedback, String turnId) throws Exception",
    1,
)
old_packet = '''String packet = com.rabpit.backroom.core.knowledge.KnowledgeContextEngine.build(
      MainActivity.this, before.toString(), action, rolls.toString());'''
writer = replace_once(
    writer,
    old_packet,
    '''String packet = foundationPacket(before, action, rolls,
      auditFeedback != null && auditFeedback.length() > 0 ? "repair" : "writer", turnId);''',
    "writer Foundation slice",
)
main = main[:writer_start] + writer + main[writer_end:]

main = replace_once(
    main,
    "        try {\n          JSONObject combatResult = new JSONObject(requireGameCore().processCombat(stateJson, actionKind, action));",
    "        try {\n          TurnBudget turnBudget = TurnBudget.start(75000L);\n          JSONObject combatResult = new JSONObject(requireGameCore().processCombat(stateJson, actionKind, action));",
    "turn deadline creation",
)
main = replace_once(
    main,
    '''          if (!actionStart.optBoolean("handled", false)) {
            throw new Exception("Action Runtime từ chối hành động: " + actionStart.optString("error", "action_start_failed"));
          }
''',
    '''          if (!actionStart.optBoolean("handled", false)) {
            throw new Exception("Action Runtime từ chối hành động: " + actionStart.optString("error", "action_start_failed"));
          }
          final String foundationTurnId = actionStart.optString("turnId", "");
''',
    "pinned turn id",
)

main = replace_once(
    main,
    "parseModelJson(generateText(writerPrompt(before, action, rolls, null)))",
    "new JSONObject(generateStructuredText(writerPrompt(before, action, rolls, null, foundationTurnId), AiResponseSchemas.Role.WRITER, turnBudget))",
    "strict writer call",
)
main = replace_once(
    main,
    "parseModelJson(generateText(writerPrompt(before, action, rolls, hardIssues)))",
    "new JSONObject(generateStructuredText(writerPrompt(before, action, rolls, hardIssues, foundationTurnId), AiResponseSchemas.Role.REPAIR, turnBudget))",
    "strict repair call",
)
old_audit_call = "auditsForRisk(before, action, rolls, generated, candidateState, risk, writerWorker)"
if main.count(old_audit_call) != 2:
    raise RuntimeError(f"audit call sites: expected 2, found {main.count(old_audit_call)}")
main = main.replace(old_audit_call, old_audit_call[:-1] + ", foundationTurnId, turnBudget)")

main = replace_once(
    main,
    '''          state.put("log", log);
          emit("backroomTurn", state.toString());
''',
    '''          state.put("log", log);
          com.rabpit.backroom.core.foundation.FoundationRuntime.releaseTurn(MainActivity.this, foundationTurnId);
          com.rabpit.backroom.core.foundation.FoundationRuntime.warm(
            getApplicationContext(), requireGameCore().foundationStateProjection());
          emit("backroomTurn", state.toString());
''',
    "Foundation turn release",
)

MAIN.write_text(main, encoding="utf-8")
