from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent
REPO_ROOT = ROOT.parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
CHARACTER_CANON_TEST = TESTS / "CharacterCanonR07Test.kt"
text = FACADE.read_text(encoding="utf-8")

# Inventory V2 replaces contextFor late in the patch chain. Preserve the projection/turn helpers
# that originally lived between contextFor() and response(), with V2-compatible signatures.
helper_anchor = "  private fun response(handled: Boolean, state: JSONObject, error: String?, reason: String, reply: String? = null): String"
if helper_anchor not in text:
    raise RuntimeError("Inventory V2 compile fix: response anchor missing")

helpers = r'''  private fun nextTurnId(legacy: JSONObject, state: GameState): String {
    val number = legacy.optInt("turn", state.turn.currentTurnId.substringAfterLast('_').toIntOrNull() ?: 1)
    return "TURN_${number.coerceAtLeast(1)}"
  }

  private fun timeAdvanceCommand(
    turnId: String,
    action: String,
    source: CommandSource = CommandSource.SYSTEM
  ): TimeAdvanceCommand = TimeAdvanceCommand(
    commandId = "$turnId:SYSTEM:TIME",
    turnId = turnId,
    actorId = KAI_ID,
    source = source,
    minutes = TimeCostPolicy.estimateMinutes(action),
    reason = "player_action"
  )

  private fun jsonObjects(array: JSONArray?): List<JSONObject> {
    if (array == null) return emptyList()
    val result = mutableListOf<JSONObject>()
    for (index in 0 until array.length()) array.optJSONObject(index)?.let(result::add)
    return result
  }

  private fun clearPendingWithoutConsumingTurn(state: GameState): GameState =
    state.copy(turn = state.turn.copy(pending = null))

  private fun syncLegacy(legacy: JSONObject, state: GameState, incrementTurn: Boolean = true): JSONObject {
    val output = JSONObject(legacy.toString())
    if (incrementTurn) output.put("turn", output.optInt("turn", 1) + 1)
    output.put("saveVersion", CURRENT_SAVE_VERSION)
    output.put("gameTime", JSONObject().apply {
      put("elapsedSubjectiveMinutes", state.time.elapsedSubjectiveMinutes)
      put("lastAdvanceMinutes", state.time.lastAdvanceMinutes)
      state.time.lastAdvanceReason?.let { put("lastAdvanceReason", it) }
    })
    output.put("partyDetails", CharacterDetailJson.encodeParty(CharacterDetailProjector.projectParty(state)))
    val kaiInventory = state.inventories[KAI_ID]?.items?.values.orEmpty()
    output.put("inventory", JSONArray().apply {
      kaiInventory.forEach { stack ->
        put(JSONObject().apply {
          put("id", stack.itemId)
          put("name", stack.name)
          put("quantity", stack.quantity)
          stack.condition?.let { put("state", it) }
          put("metadata", JSONObject(stack.metadata))
        })
      }
    })
    output.put("party", JSONArray().apply {
      state.party.memberIds.filter { it != KAI_ID }.forEach { id ->
        state.characters[id]?.let { character ->
          put(JSONObject().apply {
            put("id", character.id)
            put("name", character.name)
            character.avatarRef?.let { put("avatar", it) }
            put("presence", character.presence.name)
          })
        }
      }
    })
    state.world["location"]?.let { output.put("location", it) }
    state.world["title"]?.let { output.put("title", it) }
    state.world["levelJson"]?.let { output.put("level", JSONObject(it)) }
    state.world["flagsJson"]?.let { output.put("flags", JSONObject(it)) }
    state.metadata["legacyPlayerJson"]?.let { output.put("player", JSONObject(it)) }
    return output
  }

'''

if "  private fun nextTurnId(" not in text:
    text = text.replace(helper_anchor, helpers + helper_anchor, 1)

text = text.replace(
    'candidate.optJSONArray("party").objects().mapNotNull { json ->',
    'jsonObjects(candidate.optJSONArray("party")).mapNotNull { json ->'
)
text = text.replace(
    "private fun response(handled: Boolean, state: JSONObject, error: String?, reason: String, reply: String? = null): String",
    'private fun response(handled: Boolean, state: JSONObject, error: String?, reason: String = error ?: "unhandled", reply: String? = null): String'
)
text = text.replace(
    "private fun validationReply(reason: String): String",
    "private fun validationReply(reason: String?): String"
)
text = text.replace(
    'committed.execution?.events?.joinToString(","), eventReply(committed.execution?.events.orEmpty()))',
    'committed.execution?.events?.joinToString(",") ?: "rule_committed", eventReply(committed.execution?.events.orEmpty()))'
)

# Entity loot must be exposed to WebView only when authoritative Core metadata proves that
# the exact grant committed. An absent marker is not success; this also protects rollback paths.
text = text.replace(
    '''      val marker = finalState.metadata["loot.processed.${grant.sourceId}"].orEmpty()
      if (!marker.startsWith("lost:")) {''',
    '''      if (LootEngine.wasGrantCommitted(finalState, grant.sourceId)) {'''
)

# Rejections that explicitly do not advance the displayed turn must not add that turn ID to
# completedTurnIds. Otherwise the next action reuses the same TURN_n and is rejected as already done.
text = text.replace(
    'val clean = TurnCoordinator.reject(created.state, hardNoAction.reason ?: "action_unavailable").state',
    'val clean = clearPendingWithoutConsumingTurn(created.state)'
)
text = text.replace(
    '''      val recovered = TurnCoordinator.reject(created.state, committed.error).state
      repository.save(recovered)
      return response(true, syncLegacy(legacy, recovered, incrementTurn = false), committed.error, "rule_rejected", validationReply(committed.error))''',
    '''      val recovered = clearPendingWithoutConsumingTurn(created.state)
      repository.save(recovered)
      return response(true, syncLegacy(legacy, recovered, incrementTurn = false), committed.error, "rule_rejected", validationReply(committed.error))'''
)
text = text.replace(
    '''      val recovered = TurnCoordinator.reject(created.state, committed.error).state
      repository.save(recovered)
      return response(false, before, committed.error)''',
    '''      return response(false, before, committed.error)'''
)

for marker in [
    "private fun nextTurnId(",
    "private fun timeAdvanceCommand(",
    "private fun syncLegacy(",
    "private fun jsonObjects(",
    "private fun clearPendingWithoutConsumingTurn(",
    'reason: String = error ?: "unhandled"',
    '?: "rule_committed"',
    "validationReply(reason: String?)",
    "LootEngine.wasGrantCommitted(finalState, grant.sourceId)",
]:
    if marker not in text:
        raise RuntimeError(f"Inventory V2 compile fix marker missing: {marker}")
if '.objects()' in text:
    raise RuntimeError("Inventory V2 compile fix: inaccessible JSONArray.objects() still referenced")
if "TurnCoordinator.reject(created.state" in text:
    raise RuntimeError("Inventory V2 compile fix: rejected action can still consume a turn")
if 'metadata["loot.processed.${grant.sourceId}"].orEmpty()' in text:
    raise RuntimeError("Inventory V2 compile fix: absent loot marker can still be reported as granted")

FACADE.write_text(text, encoding="utf-8")

# The legacy finalizer used to delete any tracked test file containing a retired command token.
# That can turn regressions green by removing coverage. All tracked tests must survive the patch
# chain; obsolete assertions are migrated in source instead of being silently unlinked.
tracked = subprocess.run(
    ["git", "ls-files", "android-apk/app/src/test/java/com/rabpit/backroom/core"],
    cwd=REPO_ROOT,
    check=True,
    text=True,
    capture_output=True,
).stdout.splitlines()
missing_tracked_tests = [
    path for path in tracked
    if path.endswith(".kt") and not (REPO_ROOT / path).is_file()
]
if missing_tracked_tests:
    raise RuntimeError(
        "Inventory V2 finalizer deleted tracked tests instead of migrating them: "
        + ", ".join(sorted(missing_tracked_tests))
    )

# CharacterCanonR07Test is generated earlier by the legacy character-canon patch. Inventory V2's
# explicit global companion rule is 8 item types and 99 per type, so align that generated assertion
# after the V2 policy has become final authority.
if CHARACTER_CANON_TEST.is_file():
    test = CHARACTER_CANON_TEST.read_text(encoding="utf-8")
    old = '''    assertEquals(8, profile.maxTypes)
    assertEquals(100, profile.maxPerType)'''
    new = '''    assertEquals(8, profile.maxTypes)
    assertEquals(99, profile.maxPerType)'''
    if new not in test:
        if test.count(old) != 1:
            raise RuntimeError(f"Inventory V2 generated Lucia capacity test anchor expected once, found {test.count(old)}")
        test = test.replace(old, new, 1)
    CHARACTER_CANON_TEST.write_text(test, encoding="utf-8")

print("Inventory V2 facade compile surface restored; loot reporting is commit-gated, tracked regression tests survive, rejected actions preserve turns, and generated companion limits match V2.")
