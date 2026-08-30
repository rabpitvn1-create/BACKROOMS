from pathlib import Path
import re
import runpy

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"

SYSTEM = CORE / "CharacterEquipmentSystem.kt"
STATE_REDUCER = CORE / "StateReducer.kt"
NATURAL_TEST = TESTS / "OmnivaultNaturalFlowTest.kt"
IDENTITY_TEST = TESTS / "OmnivaultInstanceAuthorityTest.kt"
EQUIPMENT_TEST = TESTS / "SruEquipmentIntegrationTest.kt"
EXTENSIBLE_TEST = TESTS / "ExtensibleItemSystemTest.kt"
INVENTORY_POLICY_TEST = TESTS / "InventoryPolicyTest.kt"
GAME_STATE = CORE / "GameState.kt"
SPECIAL = CORE / "SpecialFollowersCanon.kt"
OMNIVAULT = CORE / "OmnivaultEngine.kt"


def replace_test(text: str, name: str, replacement: str) -> str:
    pattern = re.compile(
        r"  @Test fun " + re.escape(name) + r"\(\) \{.*?(?=\n  @Test fun |\n\})",
        re.S,
    )
    updated, count = pattern.subn(replacement.rstrip(), text, count=1)
    if count != 1:
        raise RuntimeError(f"final SRU test {name}: expected 1 function, found {count}")
    return updated


# The MadGod retirement finalizer already rewrites this block to use
# cleanedMetadata. The SRU patch was originally authored against the immediately
# preceding form, so temporarily normalize only that anchor, then restore the
# cleaned metadata source after the SRU mutation. This keeps both migrations.
pre_system = SYSTEM.read_text(encoding="utf-8")
cleaned_metadata_line = '      metadata = cleanedMetadata + ("characterEquipmentSchemaVersion" to SCHEMA_VERSION)\n'
legacy_metadata_line = '      metadata = input.metadata + ("characterEquipmentSchemaVersion" to SCHEMA_VERSION)\n'
restore_cleaned_metadata = cleaned_metadata_line in pre_system
if restore_cleaned_metadata:
    pre_system = pre_system.replace(cleaned_metadata_line, legacy_metadata_line, 1)
    SYSTEM.write_text(pre_system, encoding="utf-8")

# Apply the current SRU equipment migration after every historical compatibility
# patch has finished mutating the generated runtime.
runpy.run_path(str(ROOT / "patch-sru-equipment-integration.py"), run_name="__main__")

# The repository's final workflow contract currently locks schema version 2.
# SRU literal-ID migration runs on every normalization, so a schema bump is not
# required for compatibility with persisted equipment IDs.
system = SYSTEM.read_text(encoding="utf-8")
system = system.replace('private const val SCHEMA_VERSION = "3"', 'private const val SCHEMA_VERSION = "2"', 1)
if restore_cleaned_metadata:
    system = system.replace(legacy_metadata_line, cleaned_metadata_line, 1)
SYSTEM.write_text(system, encoding="utf-8")

# RESTORE is gameplay-authoritative in the current Omnivault canon. Remove the
# old validator veto that prevented OmnivaultEngine.restore() from ever running.
reducer = STATE_REDUCER.read_text(encoding="utf-8")
legacy_restore_gate = '''    // Restore remains a narrative capability. It must never mutate authoritative gameplay state.
    if (command is OmnivaultCommand && command.operation == OmnivaultCommand.Operation.RESTORE) {
      return ValidationResult(false, "restore_narrative_only")
    }

'''
if legacy_restore_gate in reducer:
    reducer = reducer.replace(
        legacy_restore_gate,
        '    // Current canon: Omnivault RESTORE is validated and executed by OmnivaultEngine.\n\n',
        1,
    )
if 'return ValidationResult(false, "restore_narrative_only")' in reducer:
    raise RuntimeError("legacy narrative-only Omnivault RESTORE validator survived")
STATE_REDUCER.write_text(reducer, encoding="utf-8")

# patch-sru-equipment-integration.py intentionally replaces the two legacy
# Omnivault suites with current-canon coverage. Keep unique Kotlin class names
# so both files can coexist in the same package.
if NATURAL_TEST.exists():
    natural = NATURAL_TEST.read_text(encoding="utf-8")
    natural = natural.replace('class OmnivaultCurrentCanonTest {', 'class OmnivaultCurrentCanonNaturalFlowTest {', 1)
    NATURAL_TEST.write_text(natural, encoding="utf-8")
if IDENTITY_TEST.exists():
    identity = IDENTITY_TEST.read_text(encoding="utf-8")
    identity = identity.replace('class OmnivaultCurrentCanonTest {', 'class OmnivaultCurrentCanonInstanceAuthorityTest {', 1)
    IDENTITY_TEST.write_text(identity, encoding="utf-8")

# Legacy extensibility coverage assumed retired Scan/Copy mechanics. Preserve
# its actual purpose, future data-defined items flowing through generic item
# operations, while explicitly asserting that Scan/Copy remain retired.
if EXTENSIBLE_TEST.exists():
    extensible = EXTENSIBLE_TEST.read_text(encoding="utf-8")
    if 'futureContentCompletesPickupInspectScanCopyTransferUseAndDrop' in extensible:
        extensible = replace_test(extensible, 'futureContentCompletesPickupInspectScanCopyTransferUseAndDrop', r'''  @Test fun futureContentCompletesPickupInspectTransferUseAndDropWithRetiredVaultCopy() {
    val follower = CharacterState(
      "future:follower:alpha",
      "Follower Added Tomorrow",
      metadata = mapOf("inventoryMaxTypes" to "5", "inventoryMaxPerType" to "50")
    )
    val itemJson = JSONObject()
      .put("id", "future:field-kit")
      .put("name", "Future Field Kit")
      .put("quantity", 1)
      .put("metadata", JSONObject()
        .put("description", "A future data-defined multipurpose kit.")
        .put("itemType", "TOOL")
        .put("usable", "true"))
    val flags = WorldItemLedger.record(null, "future-world:1001", itemJson.toString())
    val worldPickup = requireNotNull(WorldItemLedger.consume(flags, "future-world:1001", "nhặt Future Field Kit"))

    var state = GameState.initial().copy(
      characters = GameState.initial().characters + (follower.id to follower),
      inventories = GameState.initial().inventories + (follower.id to InventoryState(follower.id)),
      world = mapOf("location" to "future-world:1001", "flagsJson" to worldPickup.flagsJson)
    )
    val record = worldPickup.items.single()
    val pickup = InventoryEngine.execute(state, ItemCommand(
      "future-pickup", state.turn.currentTurnId, KAI_ID, source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP, itemId = record.itemId, itemName = record.itemName,
      quantity = record.quantity, metadata = record.metadata
    ))
    assertTrue(pickup.applied)
    state = pickup.state
    val original = state.inventories.getValue(KAI_ID).items.getValue(record.itemId)
    assertEquals("A future data-defined multipurpose kit.", ItemSystem.inspect(original, KAI_ID).description)

    for (op in listOf(OmnivaultCommand.Operation.SCAN, OmnivaultCommand.Operation.COPY)) {
      val retired = OmnivaultEngine.execute(state, OmnivaultCommand(
        "future-retired-$op", state.turn.currentTurnId, KAI_ID, source = CommandSource.RULE,
        operation = op, itemId = original.itemId, itemName = original.name, timestampEpochMs = 1001L
      ))
      assertFalse(retired.applied)
      assertEquals("omnivault_capability_retired", retired.validation.reason)
    }

    val transferred = InventoryEngine.execute(state, ItemCommand(
      "future-transfer", state.turn.currentTurnId, KAI_ID, targetId = follower.id,
      source = CommandSource.RULE, operation = ItemCommand.Operation.TRANSFER,
      itemId = original.itemId, itemName = original.name, quantity = 1
    ))
    assertTrue(transferred.applied)
    state = transferred.state
    assertEquals(1, state.inventories.getValue(follower.id).items.getValue(original.itemId).quantity)

    val used = InventoryEngine.execute(state, ItemCommand(
      "future-use", state.turn.currentTurnId, follower.id, source = CommandSource.RULE,
      operation = ItemCommand.Operation.USE, itemId = original.itemId, itemName = original.name
    ))
    assertTrue(used.applied)
    state = used.state

    val dropped = InventoryEngine.execute(state, ItemCommand(
      "future-drop", state.turn.currentTurnId, follower.id, source = CommandSource.RULE,
      operation = ItemCommand.Operation.DROP, itemId = original.itemId, itemName = original.name
    ))
    assertTrue(dropped.applied)
    assertFalse(dropped.state.inventories.getValue(follower.id).items.containsKey(original.itemId))
    val worldItems = JSONObject(dropped.state.world.getValue("flagsJson")).getJSONArray("worldItems")
    assertTrue((0 until worldItems.length()).map { worldItems.getJSONObject(it) }.any {
      it.getString("id") == original.itemId && it.getBoolean("available")
    })
  }''')
    EXTENSIBLE_TEST.write_text(extensible, encoding="utf-8")

# Scan is globally retired before signature-item policy is relevant, so the old
# policy test now verifies the current public reason instead of an unreachable one.
if INVENTORY_POLICY_TEST.exists():
    policy_test = INVENTORY_POLICY_TEST.read_text(encoding="utf-8")
    policy_test = policy_test.replace(
        'assertEquals("signature_equipment_locked", result.validation.reason)',
        'assertEquals("omnivault_capability_retired", result.validation.reason)',
        1,
    )
    INVENTORY_POLICY_TEST.write_text(policy_test, encoding="utf-8")

# The word "launcher" is correctly present in Project 07 restrictions as a
# prohibition. Only ability/capability text must not grant one.
if EQUIPMENT_TEST.exists():
    equipment_test = EQUIPMENT_TEST.read_text(encoding="utf-8")
    equipment_test = equipment_test.replace(
        'val text = (project.abilities.map { it.name + " " + it.description } + project.restrictions).joinToString(" ").lowercase()',
        'val text = project.abilities.joinToString(" ") { it.name + " " + it.description }.lowercase()',
        1,
    )
    EQUIPMENT_TEST.write_text(equipment_test, encoding="utf-8")

combined = "\n".join(path.read_text(encoding="utf-8") for path in (
    GAME_STATE, SPECIAL, SYSTEM, STATE_REDUCER, OMNIVAULT, EQUIPMENT_TEST,
    NATURAL_TEST, IDENTITY_TEST, EXTENSIBLE_TEST, INVENTORY_POLICY_TEST
))
for marker in (
    'KAI_SRU_SG_ID = "kai:sru-sg"',
    'KAI_SRU_MK20_ID = "kai:sru-mk20"',
    'name = "SRU-SG Shotgun"',
    'name = "SRU-MK20 Powered Armor"',
    'IRIS_PROJECT_07_ID = "iris:project-07"',
    'name = "Project 07"',
    'name = "GodKiller"',
    'name = "Lucifer Armor"',
    'omnivault_capability_retired',
    'omnivault_equipment_restored',
    'Current canon: Omnivault RESTORE is validated and executed by OmnivaultEngine.',
    'private const val SCHEMA_VERSION = "2"',
    'class OmnivaultCurrentCanonNaturalFlowTest',
    'class OmnivaultCurrentCanonInstanceAuthorityTest',
    'class SruEquipmentIntegrationTest',
    'futureContentCompletesPickupInspectTransferUseAndDropWithRetiredVaultCopy',
):
    if marker not in combined:
        raise RuntimeError("Final SRU equipment contract missing: " + marker)
if restore_cleaned_metadata and 'metadata = cleanedMetadata + ("characterEquipmentSchemaVersion" to SCHEMA_VERSION)' not in system:
    raise RuntimeError("MadGod cleaned metadata migration was not preserved")
if 'return ValidationResult(false, "restore_narrative_only")' in STATE_REDUCER.read_text(encoding="utf-8"):
    raise RuntimeError("Omnivault RESTORE is still blocked by CommandValidator")

print("Finalized current SRU equipment, current Omnivault restore authority and regression compatibility.")
