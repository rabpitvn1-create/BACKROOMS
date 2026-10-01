from pathlib import Path

path = Path("android-apk/app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.java")
source = path.read_text(encoding="utf-8")

old = '''  public synchronized String commitNarration(String stateJson, boolean acknowledgePendingIntro) {
    JSONObject submitted = parseState(stateJson);
    JSONObject state = parseState(liveStateJson);
    try {
      normalizeCoreState(state);
      JSONArray log = submitted.optJSONArray("log");
      if (log != null) state.put("log", new JSONArray(log.toString()));
      if (acknowledgePendingIntro) characterEncounterCore.acknowledgePendingIntro(state);
      persist(state);
      return clientSafeState(state).toString();
    } catch (Exception e) {
      throw new IllegalStateException("Không thể lưu narration.", e);
    }
  }
'''

new = '''  public synchronized String commitNarration(String stateJson, String turnId) {
    JSONObject submitted = parseState(stateJson);
    JSONObject state = parseState(liveStateJson);
    try {
      normalizeCoreState(state);
      JSONObject evidence = CommittedTurnNarrationEvidence.fromState(state, turnId);
      if (!evidence.optBoolean("available", false)) {
        throw new IllegalStateException(
            "Committed narration evidence unavailable: " + evidence.optString("reason", "unknown"));
      }
      JSONArray log = submitted.optJSONArray("log");
      if (log != null) state.put("log", new JSONArray(log.toString()));
      if (shouldAcknowledgePendingIntro(evidence)) {
        characterEncounterCore.acknowledgePendingIntro(state);
      }
      persist(state);
      return clientSafeState(state).toString();
    } catch (Exception e) {
      throw new IllegalStateException("Không thể lưu narration.", e);
    }
  }

  /** Compatibility overload: model output no longer controls encounter acknowledgement. */
  public synchronized String commitNarration(String stateJson, boolean ignoredModelSignal) {
    JSONObject state = parseState(liveStateJson);
    JSONObject root = state.optJSONObject(EmergentTurnEngine.ROOT_KEY);
    String turnId = root == null ? "" : root.optString("lastCommittedTurnId", "");
    return commitNarration(stateJson, turnId);
  }

  static boolean shouldAcknowledgePendingIntro(JSONObject evidence) {
    return CommittedTurnNarrationEvidence.hasClaim(evidence, "CHARACTER_ENCOUNTERED", "")
        || CommittedTurnNarrationEvidence.hasClaim(evidence, "CHARACTER_REUNION", "");
  }
'''

if old not in source:
    raise SystemExit("GameCoreFacade commitNarration marker not found")
source = source.replace(old, new, 1)

chest_old = '''                .put("factValue", itemName)
                .put("causedBy", "player")
'''
chest_new = '''                .put("factValue", itemName)
                .put("coreReward", coreReward)
                .put("causedBy", "player")
'''
if chest_old not in source:
    raise SystemExit("GameCoreFacade chest evidence marker not found")
source = source.replace(chest_old, chest_new, 1)

combat_old = '''              .put("factValue", outcome.isEmpty() ? "ongoing" : outcome)
              .put("resolvedActor", resolvedActor)
              .put("causedBy", "player")
'''
combat_new = '''              .put("factValue", outcome.isEmpty() ? "ongoing" : outcome)
              .put("resolvedActor", resolvedActor)
              .put("droppedItem", combat == null ? "" : combat.optString("droppedItem", ""))
              .put("coreReward", combat == null ? 0 : Math.max(0, combat.optInt("coreDropReward", 0)))
              .put("causedBy", "player")
'''
if combat_old not in source:
    raise SystemExit("GameCoreFacade combat evidence marker not found")
source = source.replace(combat_old, combat_new, 1)

path.write_text(source, encoding="utf-8")
