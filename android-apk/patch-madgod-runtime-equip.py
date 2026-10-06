from pathlib import Path

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/MadGodEquipmentTest.kt"

facade = FACADE.read_text(encoding="utf-8")

# Engine-level equip already works. This patch closes the missing end-to-end path:
# player text -> processRule -> authoritative core state -> synchronized WebView state.
# MadGod equip is deterministic and must never be delegated to GM prose/fallback.
anchor = '''    val pending = TurnCoordinator.createPending(state, turnId, action)
    if (pending.error != null) return response(false, legacy, pending.error, "pending_rejected")
    val context = contextFor(pending.state)
'''
replacement = '''    val pending = TurnCoordinator.createPending(state, turnId, action)
    if (pending.error != null) return response(false, legacy, pending.error, "pending_rejected")
    if (isMadGodEquipRequest(action)) {
      val owned = pending.state.inventories[KAI_ID]?.items?.containsKey(MADGOD_SET_ID) == true
      if (!owned) {
        val result = syncLegacy(legacy, pending.state, incrementTurn = false)
        val reply = validationReply("item_not_owned")
        appendLog(result, action, reply)
        return response(true, result, "item_not_owned", "validation_rejected", reply)
      }
      val command = ItemCommand(
        commandId = "$turnId:MADGOD:EQUIP",
        turnId = turnId,
        actorId = KAI_ID,
        source = CommandSource.RULE,
        operation = ItemCommand.Operation.EQUIP,
        itemId = MADGOD_SET_ID,
        itemName = MadGodCanon.SET_NAME,
        quantity = 1,
        slot = "weapon"
      )
      val committed = commitActionRuntime(pending.state, mutableListOf(command), action, turnId)
      if (committed.error != null) {
        val rejected = TurnCoordinator.reject(pending.state, committed.error)
        repository.save(rejected.state)
        val result = syncLegacy(legacy, rejected.state, incrementTurn = true)
        val reply = validationReply(committed.error)
        appendLog(result, action, reply)
        return response(true, result, committed.error, "validation_rejected", reply)
      }
      repository.save(committed.state)
      val result = syncLegacy(legacy, committed.state, incrementTurn = true)
      val reply = "MadGod Set đã ghi đè White Wraith Magnum và Blackblood Armor của Kai. Omnivault Ring được giữ nguyên."
      appendLog(result, action, reply)
      return response(true, result, null, "madgod_equipped", reply)
    }
    val context = contextFor(pending.state)
'''
if replacement not in facade:
    if anchor not in facade:
        raise RuntimeError("MadGod runtime processRule anchor missing")
    facade = facade.replace(anchor, replacement, 1)

helper_anchor = '''  private fun isDirectPlayerPickupAction(action: String): Boolean {
'''
helper = '''  private fun isMadGodEquipRequest(action: String): Boolean {
    val text = action.trim()
    val equip = Regex("(?:^|\\\\s)(?:trang\\\\s+bị|equip|đeo|mặc|cầm\\\\s+làm\\\\s+vũ\\\\s+khí)(?:\\\\s|$)", RegexOption.IGNORE_CASE)
    val madGod = Regex("(?:mad\\\\s*god|madgod)(?:\\\\s+set)?", RegexOption.IGNORE_CASE)
    return equip.containsMatchIn(text) && madGod.containsMatchIn(text)
  }

'''
if helper not in facade:
    if helper_anchor not in facade:
        raise RuntimeError("MadGod runtime helper anchor missing")
    facade = facade.replace(helper_anchor, helper + helper_anchor, 1)

# syncLegacy must expose authoritative MadGod equipment to both overlay and character UI.
sync_marker = '    output.put("equipment",MadGodCanon.legacy(state))\n'
if sync_marker not in facade:
    party_marker = '    output.put("partyDetails", CharacterDetailJson.encodeParty(CharacterDetailProjector.projectParty(state)))\n'
    if party_marker not in facade:
        raise RuntimeError("MadGod runtime equipment sync anchor missing")
    facade = facade.replace(party_marker, party_marker + sync_marker, 1)

FACADE.write_text(facade, encoding="utf-8")

test = TEST.read_text(encoding="utf-8")
needle = '''  @Test fun omnivaultCannotCopyTheSet() {
'''
extra = r'''  @Test fun runtimeEquipStateProjectsMadGodInsteadOfKaiDefaultGear() {
    val spawned = MadGodCanon.spawn(GameState.initial()).state
    val equip = InventoryEngine.execute(
      spawned,
      ItemCommand(
        "runtime-equip", "TURN_1", KAI_ID,
        source = CommandSource.RULE,
        operation = ItemCommand.Operation.EQUIP,
        itemId = MADGOD_SET_ID,
        itemName = MadGodCanon.SET_NAME,
        slot = "weapon"
      )
    )
    assertTrue(equip.applied)
    val slots = equip.state.equipment.getValue(KAI_ID).slots
    assertEquals(MADGOD_SET_ID, slots["weapon"])
    assertEquals(MADGOD_SET_ID, slots["armor"])
    assertEquals(KAI_OMNIVAULT_RING_ID, slots["ring"])
    val legacy = MadGodCanon.legacy(equip.state)
    assertTrue(legacy.has("set"))
    assertEquals(MADGOD_SET_ID, legacy.getJSONObject("set").getString("id"))
  }

'''
if "runtimeEquipStateProjectsMadGodInsteadOfKaiDefaultGear" not in test:
    if needle not in test:
        raise RuntimeError("MadGod runtime regression test anchor missing")
    test = test.replace(needle, extra + needle, 1)
TEST.write_text(test, encoding="utf-8")

combined = FACADE.read_text(encoding="utf-8") + "\n" + TEST.read_text(encoding="utf-8")
for marker in (
    'if (isMadGodEquipRequest(action))',
    'commandId = "$turnId:MADGOD:EQUIP"',
    'itemId = MADGOD_SET_ID',
    'slot = "weapon"',
    '"madgod_equipped"',
    'output.put("equipment",MadGodCanon.legacy(state))',
    'runtimeEquipStateProjectsMadGodInsteadOfKaiDefaultGear',
):
    if marker not in combined:
        raise RuntimeError("MadGod runtime equip contract missing: " + marker)

print("MadGod runtime equip path installed: typed equip is authoritative, overwrites Kai weapon+armor, syncs overlay/UI state.")
