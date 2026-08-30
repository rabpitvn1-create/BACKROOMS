from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"

SYSTEM = CORE / "CharacterEquipmentSystem.kt"
NATURAL_TEST = TESTS / "OmnivaultNaturalFlowTest.kt"
IDENTITY_TEST = TESTS / "OmnivaultInstanceAuthorityTest.kt"
EQUIPMENT_TEST = TESTS / "SruEquipmentIntegrationTest.kt"
GAME_STATE = CORE / "GameState.kt"
SPECIAL = CORE / "SpecialFollowersCanon.kt"
OMNIVAULT = CORE / "OmnivaultEngine.kt"

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
# SRU ID migration itself is unconditional inside normalizeInternal(), so a
# schema bump is not required to migrate persisted literal equipment IDs.
system = SYSTEM.read_text(encoding="utf-8")
system = system.replace('private const val SCHEMA_VERSION = "3"', 'private const val SCHEMA_VERSION = "2"', 1)
if restore_cleaned_metadata:
    system = system.replace(legacy_metadata_line, cleaned_metadata_line, 1)
SYSTEM.write_text(system, encoding="utf-8")

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

combined = "\n".join(path.read_text(encoding="utf-8") for path in (
    GAME_STATE, SPECIAL, SYSTEM, OMNIVAULT, EQUIPMENT_TEST, NATURAL_TEST, IDENTITY_TEST
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
    'private const val SCHEMA_VERSION = "2"',
    'class OmnivaultCurrentCanonNaturalFlowTest',
    'class OmnivaultCurrentCanonInstanceAuthorityTest',
    'class SruEquipmentIntegrationTest',
):
    if marker not in combined:
        raise RuntimeError("Final SRU equipment contract missing: " + marker)
if restore_cleaned_metadata and 'metadata = cleanedMetadata + ("characterEquipmentSchemaVersion" to SCHEMA_VERSION)' not in system:
    raise RuntimeError("MadGod cleaned metadata migration was not preserved")

print("Finalized current SRU equipment integration and current-canon Omnivault regression suites.")
