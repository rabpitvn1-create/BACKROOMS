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

path.write_text(source.replace(old, new, 1), encoding="utf-8")
