from pathlib import Path

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
text = FACADE.read_text(encoding="utf-8")

# Inventory V2 replaces contextFor late in the patch chain. Preserve the legacy projection/turn
# helpers that live between contextFor() and response(), with V2-compatible signatures.
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

for marker in [
    "private fun nextTurnId(",
    "private fun timeAdvanceCommand(",
    "private fun syncLegacy(",
    "private fun jsonObjects(",
    'reason: String = error ?: "unhandled"',
    "validationReply(reason: String?)",
]:
    if marker not in text:
        raise RuntimeError(f"Inventory V2 compile fix marker missing: {marker}")
if '.objects()' in text:
    raise RuntimeError("Inventory V2 compile fix: inaccessible JSONArray.objects() still referenced")

FACADE.write_text(text, encoding="utf-8")
print("Inventory V2 facade compile surface restored without reviving legacy pickup authority.")
