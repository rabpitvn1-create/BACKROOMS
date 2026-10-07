from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / 'app/src/main/java/com/rabpit/backroom/core'
facade = CORE / 'GameCoreFacade.kt'
method = r'''
  @Synchronized fun processItemAction(legacyStateJson: String, requestJson: String): String {
    val legacy = JSONObject(legacyStateJson)
    val state = loadOrMigrate(legacy)
    fun reject(reason: String): String = response(false, syncLegacy(legacy, state, false), reason, "item_ui_rejected", validationReply(reason))
    if (CombatRuntime.active(state) != null) return reject("combat_active")
    if (ActionRuntime.activeSession(state) != null || state.turn.pending != null) return reject("another_turn_pending")
    val request = runCatching { JSONObject(requestJson) }.getOrNull() ?: return reject("invalid_item_request")
    val operation = when (request.optString("operation")) {
      "USE" -> ItemCommand.Operation.USE
      "TRANSFER" -> ItemCommand.Operation.TRANSFER
      "DROP" -> ItemCommand.Operation.DROP
      else -> return reject("invalid_item_operation")
    }
    val actorId = request.optString("actorId")
    fun available(id: String): Boolean = id in state.party.memberIds && state.characters[id]?.presence == CharacterPresence.ACTIVE
    if (!available(actorId)) return reject("actor_unavailable")
    val itemId = request.optString("itemId")
    val item = state.inventories[actorId]?.items?.get(itemId) ?: return reject("item_not_owned")
    val rawQuantity = request.opt("quantity")
    val quantity = (rawQuantity as? Number)?.toInt() ?: return reject("quantity_must_be_positive")
    if (rawQuantity.toDouble() != quantity.toDouble() || quantity <= 0) return reject("quantity_must_be_positive")
    if (quantity > item.quantity) return reject("insufficient_item_quantity")
    val targetId = if (operation == ItemCommand.Operation.TRANSFER) request.optString("targetId").takeIf { it.isNotBlank() } else null
    if (operation == ItemCommand.Operation.TRANSFER && (targetId == null || targetId == actorId || !available(targetId))) return reject("target_unavailable")
    val verb = when (operation) { ItemCommand.Operation.USE -> "Dùng"; ItemCommand.Operation.TRANSFER -> "Chuyển"; else -> "Bỏ" }
    val action = "$verb $quantity ${item.name} (${state.characters.getValue(actorId).name})" + (targetId?.let { " cho ${state.characters.getValue(it).name}" } ?: "")
    val turnId = nextTurnId(legacy, state)
    val pending = TurnCoordinator.createPending(state, turnId, action)
    if (pending.error != null) return reject(pending.error)
    val command = ItemCommand(commandId = "$turnId:UI:ITEM", turnId = turnId, actorId = actorId,
      targetId = if (operation == ItemCommand.Operation.TRANSFER) targetId else null, source = CommandSource.UI,
      operation = operation, itemId = item.itemId, itemName = item.name, quantity = quantity)
    val committed = TurnCoordinator.commit(pending.state, listOf(command, timeAdvanceCommand(turnId, action)))
    if (committed.error != null) return reject(committed.error)
    repository.save(committed.state)
    val result = syncLegacy(legacy, committed.state, true)
    val reply = eventReply(committed.execution?.events.orEmpty())
    appendLog(result, action, reply)
    return response(true, result, null, "item_ui_committed", reply)
  }
'''
text = facade.read_text()
if 'fun processItemAction(' not in text:
    text = text.replace('  companion object {', method + '\n  companion object {', 1)
    facade.write_text(text)

main = ROOT / 'app/src/main/java/com/rabpit/backroom/MainActivity.java'
bridge = r'''
    @JavascriptInterface public void itemAction(String stateJson, String requestJson) {
      io.execute(() -> {
        try {
          emit("backroomItemAction", requireGameCore().processItemAction(stateJson, requestJson));
        } catch (Exception error) {
          emit("backroomItemAction", "{\"handled\":false,\"error\":\"item_action_failed\"}");
        }
      });
    }

'''
text = main.read_text()
if 'public void itemAction(' not in text:
    text = text.replace('    @JavascriptInterface public String coreStats(', bridge + '    @JavascriptInterface public String coreStats(', 1)
    main.write_text(text)

html = ROOT / 'app/src/main/assets/index.html'
text = html.read_text()
if 'src="item-detail-actions.js"' not in text:
    text = text.replace('</body>', '<script src="item-detail-actions.js"></script>\n</body>', 1)
    html.write_text(text)
print('Item detail actions installed: structured UI -> Core commands.')
