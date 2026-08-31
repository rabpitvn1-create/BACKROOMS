from pathlib import Path

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

facade = FACADE.read_text(encoding="utf-8")
main = MAIN.read_text(encoding="utf-8")

facade_methods = r'''  fun prepareLevelGeneration(legacyStateJson: String): String {
    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
    // Hidden escape mechanics are Core-owned. Gameplay models must never generate or inspect
    // escapeBlueprint, required facts/actions, conditions, effects or COMPLETE_LEVEL.
    return JSONObject().put("required", false).put("reason", "core_owned_hidden_blueprint").toString()
    @Suppress("UNREACHABLE_CODE")
    val legacyAreaId = legacy.optJSONObject("flags")?.optJSONObject("exploration")
      ?.optString("areaId")?.takeIf(String::isNotBlank)
    val levelId = legacyAreaId
      ?: state.world["levelId"]?.takeIf(String::isNotBlank)
      ?: state.levelInstance?.levelId
      ?: return JSONObject().put("required", false).put("reason", "level_unknown").toString()
    if (!levelRegistry.contains(levelId)) {
      return JSONObject().put("required", false).put("reason", "level_not_registered").put("levelId", levelId).toString()
    }
    if (state.levelInstance?.levelId == levelId) {
      return JSONObject().put("required", false).put("reason", "level_instance_exists").put("levelId", levelId).toString()
    }

    val runSeed = state.metadata["runSeed"]?.takeIf(String::isNotBlank)
      ?: "run-${System.currentTimeMillis()}"
    if (state.metadata["runSeed"].isNullOrBlank()) {
      repository.save(state.copy(metadata = state.metadata + ("runSeed" to runSeed)))
    }
    val definition = levelRegistry.require(levelId)
    return JSONObject().apply {
      put("required", true)
      put("levelId", levelId)
      put("runSeed", runSeed)
      put("request", LevelGenerationRequestFactory.build(definition, runSeed))
    }.toString()
  }

  fun commitGeneratedLevelCandidate(levelId: String, runSeed: String, candidateJson: String, generatorVersion: String): String {
    val definition = levelRegistry.get(levelId)
      ?: return JSONObject().put("accepted", false).put("error", "level_not_registered").toString()
    val current = repository.load()
    val existing = current.levelInstance
    if (existing?.levelId == levelId && existing.runSeed == runSeed) {
      return JSONObject().put("accepted", true).put("reason", "already_committed")
        .put("generationId", existing.generationId)
        .put("fingerprint", existing.generationFingerprint ?: JSONObject.NULL).toString()
    }

    return try {
      val candidate = LevelGenerationCandidateJson.decode(candidateJson)
      val instance = LevelInstanceGenerator.commitCandidate(definition, runSeed, candidate, generatorVersion)
      val zoneName = instance.zones[instance.currentZoneId]?.name ?: instance.currentZoneId
      val installed = current.copy(
        levelInstance = instance,
        metadata = current.metadata + ("runSeed" to runSeed),
        world = current.world + mapOf(
          "levelId" to definition.id,
          "location" to "Level ${definition.id} / $zoneName",
          "worldRevision" to "${definition.id}:${instance.revision}"
        )
      )
      repository.save(installed)
      JSONObject().put("accepted", true).put("reason", "candidate_committed")
        .put("generationId", instance.generationId)
        .put("fingerprint", instance.generationFingerprint ?: JSONObject.NULL).toString()
    } catch (error: Exception) {
      val message = (error.message ?: error::class.java.simpleName).take(1800)
      JSONObject().put("accepted", false).put("error", message).toString()
    }
  }

  fun installDefinitionLevelFallback(levelId: String, runSeed: String): String {
    if (!levelRegistry.contains(levelId)) {
      return JSONObject().put("accepted", false).put("error", "level_not_registered").toString()
    }
    return try {
      val current = repository.load()
      val installed = GenericLevelRuntime.install(
        current.copy(metadata = current.metadata + ("runSeed" to runSeed)),
        levelRegistry,
        levelId,
        runSeed
      )
      repository.save(installed)
      JSONObject().put("accepted", true).put("reason", "definition_fallback")
        .put("generationId", installed.levelInstance?.generationId ?: JSONObject.NULL).toString()
    } catch (error: Exception) {
      JSONObject().put("accepted", false).put("error", (error.message ?: "fallback_failed").take(1200)).toString()
    }
  }

'''

if "fun prepareLevelGeneration(legacyStateJson: String): String" not in facade:
    anchor = "  fun currentCoreState(): String = GameStateCodec.encode(repository.load())\n"
    if facade.count(anchor) != 1:
        raise RuntimeError("Gemini Level generation facade anchor missing")
    facade = facade.replace(anchor, facade_methods + anchor, 1)

provider_method = r'''  private String geminiLevelGenerationText(String prompt) throws Exception {
    return geminiModelMatrixPolicy(prompt, new int[] {0, 1, 2}, -1, 7000, true, 120_000L);
  }

  private String levelGenerationPrompt(JSONObject request, String rejection) {
    String correction = (rejection == null || rejection.trim().isEmpty()) ? "" :
      "\nCandidate trước bị engine từ chối. Sửa cấu trúc dựa trên lỗi này, không nới canon: " + rejection;
    return "Bạn là bộ sinh Level procedural cho text game Backrooms. Đây KHÔNG phải lượt gameplay. " +
      "Chỉ tạo world blueprint cho đúng một New Game và trả DUY NHẤT một JSON object LevelGenerationCandidate, không markdown, không giải thích. " +
      "Không tạo runtime progress như discoveredFacts, completedActions, mutations, revision, completed, runSeed, levelId hay generationId. " +
      "Không được sửa canon. environmentTags phải chứa toàn bộ canon environmentTags. phenomena chỉ lấy từ allowedPhenomena. canonClaims không được chứa forbiddenClaims. " +
      "Dùng runSeed như khóa biến thể để các New Game có topology, landmark, evidence và escape blueprint khác nhau trong giới hạn canon. " +
      "Tạo số zone trong giới hạn. Phải có zone tag entry và escape; mọi zone/connection/action/evidence reference phải tồn tại và đường tới escape phải khả dụng. " +
      "Không tạo hoặc yêu cầu escapeBlueprint, solutionId, requiredFacts, requiredActions, evidence ẩn, action ID, conditions, effects hay COMPLETE_LEVEL; toàn bộ puzzle truth do Core giữ riêng. " +
      "JSON root chỉ gồm: candidateSchemaVersion, initialZoneId, zones, landmarks, environment, environmentTags, phenomena, canonClaims, exploreRoute, replies. " +
      "Zone: {id,name,connections:[id],tags:[tag],properties:{}}. " +
      "Dữ liệu ràng buộc từ engine: " + request.toString() + correction;
  }

'''

if "private String geminiLevelGenerationText(String prompt)" not in main:
    anchor = "  private String geminiAuditText("
    index = main.find(anchor)
    if index < 0:
        raise RuntimeError("Gemini matrix provider anchor missing")
    main = main[:index] + provider_method + main[index:]

core_call = "requireGameCore()" if "requireGameCore().processRegisteredLevelAction(stateJson, actionKind, action)" in main else "gameCore"
turn_anchor = f'''          JSONObject registeredLevelResult = new JSONObject({core_call}.processRegisteredLevelAction(stateJson, actionKind, action));
'''
if "prepareLevelGeneration(stateJson)" not in main:
    if main.count(turn_anchor) != 1:
        raise RuntimeError(f"registered Level turn anchor expected once, got {main.count(turn_anchor)}")
    generation_block = f'''          JSONObject generationPlan = new JSONObject({core_call}.prepareLevelGeneration(stateJson));
          if (generationPlan.optBoolean("required", false)) {{
            String generationLevelId = generationPlan.getString("levelId");
            String generationRunSeed = generationPlan.getString("runSeed");
            JSONObject generationRequest = generationPlan.getJSONObject("request");
            JSONObject generationCommit = null;
            String generationRejection = null;
            try {{
              for (int generationAttempt = 0; generationAttempt < 2; generationAttempt++) {{
                String generatedRaw = geminiLevelGenerationText(levelGenerationPrompt(generationRequest, generationRejection));
                JSONObject generatedCandidate = parseModelJson(generatedRaw);
                String generatorVersion = "gemini-procedural-v1:" + geminiModelLabel(lastGeminiModel);
                generationCommit = new JSONObject({core_call}.commitGeneratedLevelCandidate(
                  generationLevelId, generationRunSeed, generatedCandidate.toString(), generatorVersion));
                if (generationCommit.optBoolean("accepted", false)) break;
                generationRejection = generationCommit.optString("error", "candidate_rejected");
              }}
            }} catch (Exception generationError) {{
              generationRejection = generationError.getMessage();
            }}
            if (generationCommit == null || !generationCommit.optBoolean("accepted", false)) {{
              JSONObject fallback = new JSONObject({core_call}.installDefinitionLevelFallback(generationLevelId, generationRunSeed));
              if (!fallback.optBoolean("accepted", false)) {{
                throw new Exception("Không thể khởi tạo Level procedural: " + fallback.optString("error", generationRejection == null ? "generation_failed" : generationRejection));
              }}
            }}
          }}
'''
    main = main.replace(turn_anchor, generation_block + turn_anchor, 1)

for marker in (
    "fun prepareLevelGeneration(legacyStateJson: String): String",
    "LevelGenerationRequestFactory.build(definition, runSeed)",
    "LevelGenerationCandidateJson.decode(candidateJson)",
    "LevelInstanceGenerator.commitCandidate(definition, runSeed, candidate, generatorVersion)",
    "fun installDefinitionLevelFallback(levelId: String, runSeed: String): String",
):
    if marker not in facade:
        raise RuntimeError("Gemini Level generation facade marker missing: " + marker)

for marker in (
    "private String geminiLevelGenerationText(String prompt)",
    "private String levelGenerationPrompt(JSONObject request, String rejection)",
    ".prepareLevelGeneration(stateJson)",
    ".commitGeneratedLevelCandidate(",
    ".installDefinitionLevelFallback(",
):
    if marker not in main:
        raise RuntimeError("Gemini Level generation runtime marker missing: " + marker)

FACADE.write_text(facade, encoding="utf-8")
MAIN.write_text(main, encoding="utf-8")
print("Gemini procedural Level generation wired: sanitized canon request -> bounded candidate -> Core validation/lock -> deterministic fallback.")
