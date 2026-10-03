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
    JSONObject current = parseState(liveStateJson);
    try {
      JSONArray incoming = submitted.optJSONArray("log");
      if (incoming == null || incoming.length() < 2) return clientSafeState(current).toString();
      int size = incoming.length();
      JSONObject player = incoming.optJSONObject(size - 2);
      JSONObject gm = incoming.optJSONObject(size - 1);
      if (player == null || gm == null || !"player".equals(player.optString("role", ""))) {
        return clientSafeState(current).toString();
      }
      JSONArray prefix = new JSONArray();
      for (int i = 0; i < size - 2; i++) prefix.put(incoming.get(i));
      submitted.put("log", prefix);
      if (prefix.length() == 0 && !current.has("log")) submitted.remove("log");
      JSONObject root = submitted.optJSONObject(EmergentTurnEngine.ROOT_KEY);
      JSONObject result = new JSONObject(commitPresentation(turnId,
          root == null ? -1 : root.optInt("stateVersion", -1), fingerprint(submitted),
          turnId + ":narration", player.optString("text", ""), gm.toString()));
      return result.getJSONObject("state").toString();
    } catch (Exception error) {
      throw new IllegalStateException("Không thể lưu narration.", error);
    }
  }

  /** Compatibility overload: use the submitted turn, never rebind a late response to live state. */
  public synchronized String commitNarration(String stateJson, boolean ignoredModelSignal) {
    JSONObject submitted = parseState(stateJson);
    JSONObject root = submitted.optJSONObject(EmergentTurnEngine.ROOT_KEY);
    return commitNarration(stateJson, root == null ? "" : root.optString("lastCommittedTurnId", ""));
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
              .put("entityRefs", new JSONArray(entityRefs.toString()))
              .put("entityDeaths", deaths)
              .put("causedBy", "player")
'''
combat_new = '''              .put("factValue", outcome.isEmpty() ? "ongoing" : outcome)
              .put("resolvedActor", resolvedActor)
              .put("entityRefs", new JSONArray(entityRefs.toString()))
              .put("entityDeaths", deaths)
              .put("droppedItem", combat == null ? "" : combat.optString("droppedItem", ""))
              .put("coreReward", combat == null ? 0 : Math.max(0, combat.optInt("coreDropReward", 0)))
              .put("causedBy", "player")
'''
if combat_old not in source:
    raise SystemExit("GameCoreFacade combat evidence marker not found")
source = source.replace(combat_old, combat_new, 1)

path.write_text(source, encoding="utf-8")
