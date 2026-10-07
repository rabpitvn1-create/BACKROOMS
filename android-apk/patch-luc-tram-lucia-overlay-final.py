from pathlib import Path
import re
import struct

ROOT = Path(__file__).resolve().parent
APP = ROOT / "app/src"
MAIN_SRC = APP / "main"
CORE = MAIN_SRC / "java/com/rabpit/backroom/core"
TESTS = APP / "test/java/com/rabpit/backroom/core"
ASSETS = MAIN_SRC / "assets"
MAIN = MAIN_SRC / "java/com/rabpit/backroom/MainActivity.java"
INDEX = ASSETS / "index.html"
SPECIAL = CORE / "SpecialFollowersCanon.kt"

LUC_TRAM_OVERLAY = "luctram_overlay.png"
LUC_TRAM_AVATAR = "avatars/luctram_avatar.png"
LUCIA_OVERLAY = "lucia_overlay.png"


def validate_png(relative: str) -> None:
    path = ASSETS / relative
    raw = path.read_bytes()
    if len(raw) < 33 or raw[:8] != b"\x89PNG\r\n\x1a\n":
        raise RuntimeError(f"invalid PNG asset: {relative}")
    width, height = struct.unpack(">II", raw[16:24])
    if width < 256 or height < 256 or raw[25] not in (4, 6):
        raise RuntimeError(f"overlay/avatar must be full-size alpha PNG: {relative}")


for asset in (LUC_TRAM_OVERLAY, LUC_TRAM_AVATAR, LUCIA_OVERLAY):
    validate_png(asset)


def migrate_iris_identity(text: str) -> str:
    # Preserve legacy storage compatibility through one explicit "iris" migration key
    # in SpecialFollowersCanon; all active runtime identity becomes luc_tram.
    text = text.replace("avatars/Iris_avatar.jpg", LUC_TRAM_AVATAR)
    text = text.replace("/iris123", "/luctram123")
    text = text.replace("irisReunion", "lucTramReunion")
    text = text.replace("irisEquipmentSlots", "lucTramEquipmentSlots")
    text = text.replace("irisCharacter", "lucTramCharacter")
    text = text.replace("IRIS_", "LUC_TRAM_")
    text = text.replace('"iris:', '"luc_tram:')
    text = text.replace("'iris:", "'luc_tram:")
    text = text.replace('"iris"', '"luc_tram"')
    text = text.replace("'iris'", "'luc_tram'")
    text = re.sub(r"\biris(?=[A-Z])", "lucTram", text)
    text = re.sub(r"\bIris(?=[A-Z])", "LucTram", text)
    text = re.sub(r"\bIris\b", "Lục Trầm", text)
    text = re.sub(r"\bIRIS\b", "LỤC TRẦM", text)
    text = re.sub(r"\biris\b", "lucTram", text)

    # User-facing canon comes from BACKROOMsV2; keep legacy item IDs only as
    # storage-compatible implementation details.
    text = text.replace("Ivory & Ebony", "Tịch Quang Kiếm")
    text = text.replace("Blackblood Recon Frame R03", "Thiên Cơ Bạch Kim Kiếm Khải")
    text = text.replace("Scout / Target Eliminator", "Chân truyền Thiên Kiếm Môn")
    text = text.replace("SCOUT / TARGET ELIMINATOR / DUAL-GUN MARKSMAN", "CHÂN TRUYỀN THIÊN KIẾM MÔN / KIẾM TU")
    text = text.replace("Gunslinger", "Kiếm tu")

    # Existing combat hooks remain mechanically stable, but their visible move names
    # must not keep Iris' firearm identity after the character replacement.
    replacements = {
        "Twosome Time": "Tịch Quang Hợp Kích",
        "Rain Storm": "Thiên Kiếm Chấn",
        "Honeycomb Fire": "Nhất Tuyến Phá Vọng",
        "Charged Shot": "Bạch Hồng Quán Nhật",
        "Dead Angle": "Tịch Quang Phản Kiếm",
        "ARGUS // Thousandfold Execution": "Thiên Kiếm Định Giới",
        "ARGUS Terrain Read": "Kiếm thế phân tích",
        "Thousandfold Cognition": "Kiếm tâm phản ứng",
    }
    for old, new in replacements.items():
        text = text.replace(old, new)
    return text


targets = list(CORE.glob("*.kt")) + list(TESTS.glob("*.kt")) + [MAIN, INDEX]
for path in targets:
    if not path.exists():
        continue
    before = path.read_text(encoding="utf-8")
    after = migrate_iris_identity(before)
    if path == INDEX:
        # Generated web state used bare JS properties for the old Iris flag.
        # Align those properties with the canonical luc_tram JSON key.
        after = re.sub(r"\blucTram\s*:", '"luc_tram":', after)
        after = after.replace(".lucTram", '["luc_tram"]')
    if after != before:
        path.write_text(after, encoding="utf-8")


# Replace the generated special-follower authority with a clean Lục Trầm record.
# Existing saves migrate the old Iris state/inventory/equipment into luc_tram once,
# preserving player-owned items instead of silently discarding them.
SPECIAL.write_text(r'''package com.rabpit.backroom.core

const val LUC_TRAM_ID = "luc_tram"
const val SYVIAL_ID = "syvial"

const val LUC_TRAM_IVORY_ID = "luc_tram:ivory"
const val LUC_TRAM_EBONY_ID = "luc_tram:ebony"
const val LUC_TRAM_RECON_FRAME_ID = "luc_tram:blackblood-recon-frame-r03"
const val SYVIAL_GODKILLER_ID = "syvial:godkiller"
const val SYVIAL_LUCIFER_ARMOR_ID = "syvial:lucifer-armor"

object SpecialFollowersCanon {
  const val ENCOUNTER_CHANCE = "0.25%"
  const val LUC_TRAM_ENCOUNTER_LEVELS = "1-6"
  const val SYVIAL_ENCOUNTER_LEVELS = "0-6"

  // Legacy constant names stay internal so old equipment references remain loadable.
  // User-facing equipment is the BACKROOMsV2 Lục Trầm canon.
  val lucTramEquipmentSlots: Map<String, String> = linkedMapOf(
    "weapon" to LUC_TRAM_IVORY_ID,
    "armor" to LUC_TRAM_RECON_FRAME_ID
  )

  val syvialEquipmentSlots: Map<String, String> = linkedMapOf(
    "weapon" to SYVIAL_GODKILLER_ID,
    "armor" to SYVIAL_LUCIFER_ARMOR_ID
  )

  fun lucTramCharacter(existing: CharacterState? = null): CharacterState {
    val base = existing ?: CharacterState(
      id = LUC_TRAM_ID,
      name = "Lục Trầm",
      physiology = PhysiologyState.freshRunBaseline()
    )
    return base.copy(
      id = LUC_TRAM_ID,
      name = "Lục Trầm",
      avatarRef = "avatars/luctram_avatar.png",
      inventoryId = LUC_TRAM_ID,
      equipmentId = LUC_TRAM_ID,
      metadata = base.metadata + mapOf(
        "npcType" to "follower",
        "joinEligible" to "true",
        "followsPlayer" to "true",
        "encounterChance" to ENCOUNTER_CHANCE,
        "encounterLevels" to LUC_TRAM_ENCOUNTER_LEVELS,
        "combatant" to "true",
        "role" to "Chân truyền Thiên Kiếm Môn",
        "combatStyle" to "Kiếm tu",
        "signatureWeapon" to "Tịch Quang Kiếm",
        "armor" to "Thiên Cơ Bạch Kim Kiếm Khải",
        "sourceRepo" to "rabpitvn1-create/BACKROOMsV2",
        "inventoryProfile" to "special_companion"
      )
    )
  }

  fun syvialCharacter(existing: CharacterState? = null): CharacterState {
    val base = existing ?: CharacterState(
      id = SYVIAL_ID,
      name = "Syvial",
      physiology = PhysiologyState.freshRunBaseline()
    )
    return base.copy(
      id = SYVIAL_ID,
      name = "Syvial",
      inventoryId = SYVIAL_ID,
      equipmentId = SYVIAL_ID,
      metadata = base.metadata + mapOf(
        "npcType" to "follower",
        "joinEligible" to "true",
        "followsPlayer" to "true",
        "encounterChance" to ENCOUNTER_CHANCE,
        "encounterLevels" to SYVIAL_ENCOUNTER_LEVELS,
        "combatant" to "true",
        "combatTier" to "UR+",
        "role" to "High-level supernatural swordswoman",
        "signatureWeapon" to "GodKiller",
        "armor" to "Lucifer Armor",
        "canonRef" to "SYVIAL-LUCIFER-CODEX-20260816-R03",
        "inventoryProfile" to "special_companion"
      )
    )
  }

  fun ensure(state: GameState): GameState {
    val legacyCharacter = state.characters["iris"]
    val legacyInventory = state.inventories["iris"]
    val legacyEquipment = state.equipment["iris"]

    val lucTram = lucTramCharacter(state.characters[LUC_TRAM_ID] ?: legacyCharacter)
    val syvial = syvialCharacter(state.characters[SYVIAL_ID])
    val lucTramInventory = state.inventories[LUC_TRAM_ID]
      ?: legacyInventory?.copy(ownerId = LUC_TRAM_ID)
      ?: InventoryState(LUC_TRAM_ID)
    val syvialInventory = state.inventories[SYVIAL_ID] ?: InventoryState(SYVIAL_ID)
    val lucTramEquipment = (
      state.equipment[LUC_TRAM_ID]
        ?: legacyEquipment?.copy(ownerId = LUC_TRAM_ID)
        ?: EquipmentState(LUC_TRAM_ID)
      ).slots + lucTramEquipmentSlots
    val syvialEquipment = state.equipment[SYVIAL_ID]?.slots.orEmpty() + syvialEquipmentSlots

    return state.copy(
      characters = (state.characters - "iris") + (LUC_TRAM_ID to lucTram) + (SYVIAL_ID to syvial),
      inventories = (state.inventories - "iris") + (LUC_TRAM_ID to lucTramInventory) + (SYVIAL_ID to syvialInventory),
      equipment = (state.equipment - "iris") +
        (LUC_TRAM_ID to EquipmentState(LUC_TRAM_ID, lucTramEquipment)) +
        (SYVIAL_ID to EquipmentState(SYVIAL_ID, syvialEquipment))
    )
  }
}
''', encoding="utf-8")


# Lục Trầm is the post-Level-0 reunion from BACKROOMsV2, not a Level-0 first contact.
main = MAIN.read_text(encoding="utf-8")
old_roll = 'thresholdRoll("lucTramReunion", 10000, 25, physical && reunionEligibleAndroid(state, "luc_tram"), " follower encounter")'
new_roll = 'thresholdRoll("lucTramReunion", 10000, 25, level > 0 && physical && reunionEligibleAndroid(state, "luc_tram"), " follower encounter")'
if old_roll in main:
    main = main.replace(old_roll, new_roll, 1)
elif new_roll not in main:
    raise RuntimeError("Lục Trầm reunion roll anchor missing")

main = main.replace(
    "LỤC TRẦM / SYVIAL FOLLOWER LOCK: lucTramReunion và syvialReunion là hai roll độc lập 0.2500% trên mỗi lượt physical đủ điều kiện ở Level 0–6.",
    "LỤC TRẦM / SYVIAL FOLLOWER LOCK: lucTramReunion là reunion 0.2500% chỉ sau Level 0; syvialReunion giữ 0.2500% trên Level 0–6.",
)
main = main.replace(
    "Lục Trầm giữ canon Chân truyền Thiên Kiếm Môn, Kiếm tu với Tịch Quang Kiếm và Thiên Cơ Bạch Kim Kiếm Khải;",
    "Lục Trầm giữ canon BACKROOMsV2: Chân truyền Thiên Kiếm Môn, Tịch Quang Kiếm và Thiên Cơ Bạch Kim Kiếm Khải;",
)

# Snapshot actor overlay: Cao Minh keeps his existing selector; Lucia uses the Drive PNG,
# and Lục Trầm uses the exact BACKROOMsV2 overlay.
helper = (
    "function activePartyOverlay(){"
    "var c=state&&state.combat||{};"
    "if(c.active!==true)return {src:kaiOverlaySource(),alt:'Cao Minh'};"
    "var a=String(c.currentActor||'').trim().toLowerCase();"
    "if(a.indexOf('lucia')>=0||a.indexOf('hứa thuý mai')>=0||a.indexOf('hứa thúy mai')>=0||a.indexOf('hua thuy mai')>=0)"
    "return {src:'lucia_overlay.png',alt:'Lucia Lục'};"
    "if(a.indexOf('lục trầm')>=0||a.indexOf('luc tram')>=0||a.indexOf('luc_tram')>=0)"
    "return {src:'luctram_overlay.png',alt:'Lục Trầm'};"
    "return {src:kaiOverlaySource(),alt:'Cao Minh'};"
    "}"
)
if "function activePartyOverlay()" not in main:
    marker = "function cachedSnapshot(){"
    if marker not in main:
        raise RuntimeError("Snapshot overlay helper insertion anchor missing")
    main = main.replace(marker, helper + marker, 1)

if "var actorOverlay=activePartyOverlay();kai.src=actorOverlay.src;" not in main:
    source_anchor = "kai.src=kaiOverlaySource();"
    if main.count(source_anchor) != 1:
        raise RuntimeError(f"Snapshot actor source anchor count={main.count(source_anchor)}")
    main = main.replace(source_anchor, "var actorOverlay=activePartyOverlay();kai.src=actorOverlay.src;", 1)

if "kai.alt=actorOverlay.alt;" not in main:
    alt_anchor = "kai.alt='Cao Minh';"
    if main.count(alt_anchor) != 1:
        raise RuntimeError(f"Snapshot actor alt anchor count={main.count(alt_anchor)}")
    main = main.replace(alt_anchor, "kai.alt=actorOverlay.alt;", 1)

MAIN.write_text(main, encoding="utf-8")


# Focused regression guards. Legacy "iris" is permitted only inside the one-time migration
# in SpecialFollowersCanon; active generated runtime must not expose Iris as a character.
for path in list(CORE.glob("*.kt")) + [MAIN, INDEX]:
    text = path.read_text(encoding="utf-8")
    if path != SPECIAL and re.search(r"\bIRIS_ID\b|\bIris\b|avatars/Iris_avatar\.jpg|/iris123", text):
        raise RuntimeError(f"active Iris identity remains in {path.name}")

special = SPECIAL.read_text(encoding="utf-8")
for marker in (
    'const val LUC_TRAM_ID = "luc_tram"',
    'name = "Lục Trầm"',
    'avatarRef = "avatars/luctram_avatar.png"',
    '"signatureWeapon" to "Tịch Quang Kiếm"',
    '"armor" to "Thiên Cơ Bạch Kim Kiếm Khải"',
    'state.characters["iris"]',
    'legacyInventory?.copy(ownerId = LUC_TRAM_ID)',
):
    if marker not in special:
        raise RuntimeError("Lục Trầm authority marker missing: " + marker)

main = MAIN.read_text(encoding="utf-8")
for marker in (
    'level > 0 && physical && reunionEligibleAndroid(state, "luc_tram")',
    "function activePartyOverlay()",
    "src:'lucia_overlay.png',alt:'Lucia Lục'",
    "src:'luctram_overlay.png',alt:'Lục Trầm'",
):
    if marker not in main:
        raise RuntimeError("final character/overlay contract missing: " + marker)

lucia = CORE / "LuciaCanon.kt"
if not lucia.exists() or 'const val LUCIA_ID = "lucia"' not in lucia.read_text(encoding="utf-8"):
    raise RuntimeError("Lucia must remain a distinct runtime character")

print("Final character migration applied: Iris -> Lục Trầm (BACKROOMsV2), legacy saves preserved, Lucia/Lục Trầm Snapshot overlays installed.")
