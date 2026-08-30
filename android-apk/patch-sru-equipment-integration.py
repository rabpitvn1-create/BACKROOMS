from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"

GAME_STATE = CORE / "GameState.kt"
SPECIAL = CORE / "SpecialFollowersCanon.kt"
SYSTEM = CORE / "CharacterEquipmentSystem.kt"
OMNIVAULT = CORE / "OmnivaultEngine.kt"
CORE_TEST = TESTS / "GameStateCoreTest.kt"
NATURAL_TEST = TESTS / "OmnivaultNaturalFlowTest.kt"
IDENTITY_TEST = TESTS / "OmnivaultInstanceAuthorityTest.kt"
EQUIPMENT_TEST = TESTS / "SruEquipmentIntegrationTest.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


def replace_definition(text: str, id_expr: str, replacement: str) -> str:
    pattern = re.compile(
        r"    EquipmentDefinition\(\n      id = " + re.escape(id_expr) + r",.*?\n    \),",
        re.S,
    )
    updated, count = pattern.subn(replacement.rstrip(), text, count=1)
    if count != 1:
        raise RuntimeError(f"equipment definition {id_expr}: expected 1 block, found {count}")
    return updated


def replace_test(text: str, name: str, replacement: str) -> str:
    pattern = re.compile(
        r"  @Test fun " + re.escape(name) + r"\(\) \{.*?(?=\n  @Test fun |\n\})",
        re.S,
    )
    updated, count = pattern.subn(replacement.rstrip(), text, count=1)
    if count != 1:
        raise RuntimeError(f"test {name}: expected 1 function, found {count}")
    return updated


# ---------------------------------------------------------------------------
# 1) Current equipment IDs. Legacy public constant names remain aliases so old
# generated tests/call sites compile, while persisted literal IDs can migrate.
# ---------------------------------------------------------------------------
state = GAME_STATE.read_text(encoding="utf-8")
old_constants = '''const val KAI_WHITE_WRAITH_ID = "kai:white-wraith-magnum"
const val KAI_BLACKBLOOD_ARMOR_ID = "kai:blackblood-armor"
const val KAI_OMNIVAULT_RING_ID = "kai:omnivault-ring"
'''
new_constants = '''const val KAI_SRU_SG_ID = "kai:sru-sg"
const val KAI_SRU_MK20_ID = "kai:sru-mk20"
const val KAI_SRU_MK20_SENSOR_ID = "kai:sru-mk20-open-face-sensor"
const val KAI_SRU_MK20_ARMS_ID = "kai:sru-mk20-arm-module"
const val KAI_SRU_MK20_LEGS_ID = "kai:sru-mk20-leg-module"
const val KAI_OMNIVAULT_RING_ID = "kai:omnivault-ring"

// Compatibility aliases for generated code. These names no longer denote the retired equipment.
const val KAI_WHITE_WRAITH_ID = KAI_SRU_SG_ID
const val KAI_BLACKBLOOD_ARMOR_ID = KAI_SRU_MK20_ID
const val KAI_LEGACY_WHITE_WRAITH_ID = "kai:white-wraith-magnum"
const val KAI_LEGACY_BLACKBLOOD_ARMOR_ID = "kai:blackblood-armor"
const val KAI_LEGACY_DEMON_JAW_ID = "kai:demon-jaw-mask"
const val KAI_LEGACY_TALON_ID = "kai:talon-gauntlets"
const val KAI_LEGACY_PHANTOM_GREAVES_ID = "kai:phantom-greaves"
'''
state = replace_once(state, old_constants, new_constants, "Kai SRU equipment IDs")
state = state.replace('  const val WEAPON_NAME = "W.W Magnum"', '  const val WEAPON_NAME = "SRU-SG Shotgun"')
state = state.replace('  const val ARMOR_NAME = "Blackblood Armor & linked modules"', '  const val ARMOR_NAME = "SRU-MK20 Powered Armor"')
state = state.replace(
    '      key.contains("w.w magnum") || key.contains("white wraith") || key.contains("wraith magnum") -> "weapon"',
    '      key.contains("sru-sg") || key.contains("sru sg") || key.contains("w.w magnum") || key.contains("white wraith") || key.contains("wraith magnum") -> "weapon"',
)
state = state.replace(
    '      key.contains("blackblood armor") || key.contains("black blood armor") -> "armor"',
    '      key.contains("sru-mk20") || key.contains("sru mk20") || key.contains("blackblood armor") || key.contains("black blood armor") -> "armor"',
)
GAME_STATE.write_text(state, encoding="utf-8")


# ---------------------------------------------------------------------------
# 2) Iris current Project 07 ID and SRU metadata. Syvial already had the correct
# GodKiller/Lucifer Armor pair, but its generated metadata still used old roles.
# ---------------------------------------------------------------------------
special = SPECIAL.read_text(encoding="utf-8")
special = replace_once(
    special,
    'const val IRIS_RECON_FRAME_ID = "iris:blackblood-recon-frame-r03"\n',
    'const val IRIS_PROJECT_07_ID = "iris:project-07"\nconst val IRIS_LEGACY_RECON_FRAME_ID = "iris:blackblood-recon-frame-r03"\nconst val IRIS_RECON_FRAME_ID = IRIS_PROJECT_07_ID\n',
    "Iris Project 07 ID",
)
special = special.replace('"armor" to "Blackblood Recon Frame R03"', '"armor" to "Project 07"')
special = special.replace('"canonRef" to "IRIS-BELIAL-BLACKBLOOD-CODEX-20260817-R05"', '"canonRef" to "IRIS-BELIAL-SRU-CODEX-20260830-R06"')
special = special.replace('"role" to "High-level supernatural swordswoman"', '"role" to "SRU Deputy Leader / High-Speed Swordswoman"')
special = special.replace('"canonRef" to "SYVIAL-LUCIFER-CODEX-20260816-R03"', '"canonRef" to "SYVIAL-LUCIFER-CODEX-CURRENT"')
SPECIAL.write_text(special, encoding="utf-8")


# ---------------------------------------------------------------------------
# 3) Replace the visible/current equipment definitions while preserving the old
# normalized bonus totals. Arm/leg/head functions are now integrated MK20 modules,
# not retired standalone Black Blood gear.
# ---------------------------------------------------------------------------
system = SYSTEM.read_text(encoding="utf-8")
if 'private const val SCHEMA_VERSION = "2"' in system:
    system = system.replace('private const val SCHEMA_VERSION = "2"', 'private const val SCHEMA_VERSION = "3"', 1)
elif 'private const val SCHEMA_VERSION = "1"' in system:
    system = system.replace('private const val SCHEMA_VERSION = "1"', 'private const val SCHEMA_VERSION = "3"', 1)

# Generated legacy module constants become compatibility aliases to current integrated modules.
system = system.replace('const val KAI_DEMON_JAW_MASK_ID = "kai:demon-jaw-mask"', 'const val KAI_DEMON_JAW_MASK_ID = KAI_SRU_MK20_SENSOR_ID')
system = system.replace('const val KAI_TALON_GAUNTLETS_ID = "kai:talon-gauntlets"', 'const val KAI_TALON_GAUNTLETS_ID = KAI_SRU_MK20_ARMS_ID')
system = system.replace('const val KAI_PHANTOM_GREAVES_ID = "kai:phantom-greaves"', 'const val KAI_PHANTOM_GREAVES_ID = KAI_SRU_MK20_LEGS_ID')

system = replace_definition(system, "KAI_WHITE_WRAITH_ID", r'''    EquipmentDefinition(
      id = KAI_WHITE_WRAITH_ID, name = "SRU-SG Shotgun", type = "TACTICAL SHOTGUN", primarySlot = EquipmentSlot.WEAPON,
      bonuses = EquipmentBonuses(crit = 8),
      weapon = WeaponGameplayStats(32, "Demon Shell ∞ / Physical Shell finite", null, listOf("Physical Shell", "Demon Shell")),
      abilities = listOf(
        ability("Dual Shell System", "Dùng shell vật lý bình thường hoặc shell quỷ lực tùy tình huống.", "Shell vật lý là vật tư hữu hạn; shell quỷ lực hình thành trực tiếp từ nguồn sức mạnh của Kai."),
        ability("Demon Shell", "Shell quỷ lực vẫn gây sát thương vật lý nhưng mạnh hơn shell vật lý hàng chục lần theo canon.", "Gameplay DMG tiếp tục đi qua CombatRuntime normalization; không tự one-shot mọi Entity."),
        ability("Shotgun Mastery", "Kai kiểm soát độ tản, đường bắn, góc đặt chùm đạn và đổi mục tiêu ở cấp UR+."),
        ability("Core Self-Repair", "SRU-SG tự sửa chữa cấu trúc khi đang là trang bị của Kai.", "Sửa trang bị không phải hồi HP nhân vật."),
        ability("Guilty Crown Override", "Tương thích Guilty Crown Override với đúng 24 lần khai hỏa Demon Shell trong thời gian dừng hoàn toàn.", "Không đổi khóa 24 phát.")
      ),
      canonRef = "KAI-EQP-SRU-SG-01"
    ),''')

system = replace_definition(system, "KAI_BLACKBLOOD_ARMOR_ID", r'''    EquipmentDefinition(
      id = KAI_BLACKBLOOD_ARMOR_ID, name = "SRU-MK20 Powered Armor", type = "POWERED ARMOR / EXOSKELETON", primarySlot = EquipmentSlot.ARMOR,
      bonuses = EquipmentBonuses(hp = 25, str = 8, df = 18, agi = 6),
      abilities = listOf(
        ability("Powered Musculature", "Khuếch đại lực kéo, đẩy, nâng, giữ và phát lực lên nhiều lần so với người bình thường."),
        ability("Mobility Assistance", "Tăng hiệu suất chạy, đổi hướng, né, hạ trọng tâm và cận chiến mà không biến giáp thành khối power armor cồng kềnh."),
        ability("Impact Dispersion", "Hấp thụ và phân tán lực va chạm qua khung trợ lực và các phiến giáp."),
        ability("Environmental Protection", "Bảo vệ tác chiến trước nhiệt, lạnh, độc tố và môi trường khắc nghiệt ở mức phù hợp với SRU."),
        ability("Integrated Arm / Leg Systems", "Các chức năng tay và chân legacy đã được tích hợp trực tiếp vào SRU-MK20."),
        ability("Core Self-Repair", "Mọi phần SRU-MK20 đang trang bị tự sửa chữa bằng nguồn sức mạnh của Kai.", "Không tạo vật mới và không hồi HP tức thì."),
        ability("SRU Identification", "Nhận diện POLICE / SRU / SPECIAL RESPONSE UNIT và điểm báo trạng thái hệ thống màu xanh.")
      ),
      restrictions = listOf("Cấu hình hiện hành để lộ đầu và khuôn mặt; không có Demon Jaw Mask, sừng cơ khí, pauldron đầu rồng hoặc cape legacy."),
      canonRef = "KAI-EQP-SRU-MK20-01"
    ),''')

system = replace_definition(system, "KAI_DEMON_JAW_MASK_ID", r'''    EquipmentDefinition(
      id = KAI_DEMON_JAW_MASK_ID, name = "SRU-MK20 Open-Face Sensor Suite", type = "INTEGRATED SENSOR MODULE", primarySlot = EquipmentSlot.HEAD,
      bonuses = EquipmentBonuses(hp = 5, df = 6, crit = 6),
      abilities = listOf(
        ability("Open-Face Sensor Support", "Cảm biến và hỗ trợ tác chiến của SRU-MK20 hoạt động mà không che mặt Kai."),
        ability("Targeting Assistance", "Hỗ trợ xử lý dữ liệu mục tiêu và đường bắn hợp lệ.", "Hỗ trợ không tạo auto-hit."),
        ability("Encrypted SRU Communication", "Kết nối liên lạc mã hóa của SRU khi hạ tầng khả dụng.")
      ),
      restrictions = listOf("Đây là subsystem tích hợp của SRU-MK20, không phải helmet hoặc Demon Jaw Mask độc lập."),
      canonRef = "KAI-EQP-SRU-MK20-01"
    ),''')

system = replace_definition(system, "KAI_TALON_GAUNTLETS_ID", r'''    EquipmentDefinition(
      id = KAI_TALON_GAUNTLETS_ID, name = "SRU-MK20 Integrated Arm Module", type = "INTEGRATED ARM MODULE", primarySlot = EquipmentSlot.GAUNTLETS,
      bonuses = EquipmentBonuses(hp = 5, str = 12, df = 4),
      abilities = listOf(
        ability("Arm Assist", "Khung cánh tay tăng lực nắm, đẩy, kéo, phát lực và kiểm soát SRU-SG ở cự ly gần."),
        ability("Close-Quarters Control", "Hỗ trợ khóa, bám, leo và kiểm soát vật thể trong tầm với khi điều kiện vật lý cho phép."),
        ability("Core Self-Repair", "Module tay tự sửa chữa khi đang được Kai trang bị.")
      ),
      restrictions = listOf("Subsystem tích hợp SRU-MK20; không còn là Talon Gauntlets độc lập."),
      canonRef = "KAI-EQP-SRU-MK20-01"
    ),''')

system = replace_definition(system, "KAI_PHANTOM_GREAVES_ID", r'''    EquipmentDefinition(
      id = KAI_PHANTOM_GREAVES_ID, name = "SRU-MK20 Integrated Leg Module", type = "INTEGRATED LEG MODULE", primarySlot = EquipmentSlot.GREAVES,
      bonuses = EquipmentBonuses(hp = 5, str = 5, df = 3, agi = 14),
      abilities = listOf(
        ability("Leg Assist", "Khung chân tăng gia tốc, đổi hướng, chạy, nhảy và khả năng tiếp đất."),
        ability("Traversal Support", "Hỗ trợ vượt địa hình và điều chỉnh quỹ đạo cơ thể trong giới hạn vận động thực tế."),
        ability("Impact Reduction", "Giảm tải lên chân khi tiếp đất hoặc va chạm."),
        ability("Core Self-Repair", "Module chân tự sửa chữa khi đang được Kai trang bị.")
      ),
      restrictions = listOf("Subsystem tích hợp SRU-MK20; không còn là Phantom Greaves độc lập."),
      canonRef = "KAI-EQP-SRU-MK20-01"
    ),''')

system = replace_definition(system, "KAI_OMNIVAULT_RING_ID", r'''    EquipmentDefinition(
      id = KAI_OMNIVAULT_RING_ID, name = "Omnivault Ring", type = "UTILITY EQUIPMENT", primarySlot = EquipmentSlot.RING,
      abilities = listOf(
        ability("Infinite Physical Storage", "Lưu trữ và lấy lại vật vô tri đã cất với dung lượng không giới hạn theo canon.", "Không tác động lên sinh vật sống."),
        ability("Equipment Restoration", "Hoàn nguyên trang bị hiện hành của Kai đã bị mất hoặc hư hỏng.", "Mỗi trang bị có cooldown 24 giờ sau một lần hoàn nguyên thành công."),
        ability("Equipped Item Self-Repair Link", "Trang bị đang được Kai mang tiếp tục tự sửa chữa qua liên kết Core độc lập với cooldown Hoàn nguyên.")
      ),
      restrictions = listOf(
        "SCAN/COPY/CREATE/MARKED/UPGRADE đã bị loại khỏi canon hiện hành.",
        "Không tạo vật phẩm chưa từng thuộc bộ trang bị hiện hành của Kai.",
        "Không tác động lên sinh vật sống."
      ),
      canonRef = "KAI-EQP-OMNIVAULT-01"
    ),''')

system = replace_definition(system, "IRIS_RECON_FRAME_ID", r'''    EquipmentDefinition(
      id = IRIS_RECON_FRAME_ID, name = "Project 07", type = "SRU MECHANICAL COMBAT ARMOR", primarySlot = EquipmentSlot.ARMOR,
      bonuses = EquipmentBonuses(hp = 20, df = 14, agi = 10, crit = 4),
      abilities = listOf(
        ability("Recon Protection", "Bảo vệ Iris trước va đập, mảnh văng và nguy cơ môi trường ở mức phù hợp với trinh sát chiến đấu."),
        ability("Dual-Gun Stabilization", "Ổn định vai, cẳng tay, cổ tay, tư thế và phân bố lực khi dùng Ivory & Ebony."),
        ability("Local Sensor Suite", "Cảm biến khoảng cách, chuyển động và môi trường cung cấp dữ liệu tại khu vực Iris trực tiếp hoạt động."),
        ability("ARGUS Terrain Read Support", "Hỗ trợ đọc độ cao, vật che, đường ngắm, lối vào/rút và điểm nghẽn từ dữ liệu hiện trường."),
        ability("Mobile Firing Support", "Hỗ trợ cân bằng và đổi tư thế khi bắn từ góc khó hoặc đang di chuyển.")
      ),
      restrictions = listOf("Không có drone.", "Không có Command Slate/tablet.", "Không có launcher, pháo vai, tên lửa hoặc remote camera mesh."),
      canonRef = "IRIS-BELIAL-SRU-CODEX-20260830-R06"
    ),''')

system = system.replace('canonRef = "IRIS-BELIAL-BLACKBLOOD-CODEX-20260817-R05"', 'canonRef = "IRIS-BELIAL-SRU-CODEX-20260830-R06"')
system = system.replace('canonRef = "SYVIAL-LUCIFER-CODEX-20260816-R03"', 'canonRef = "SYVIAL-LUCIFER-CODEX-CURRENT"')

# Migrate persisted literal IDs before definition normalization and clear retired Omnivault scan state.
migration_anchor = '''      val slots = eq.slots.toMutableMap()

      // Collapse the historical two-slot Ivory/Ebony representation into one unique dual-weapon Item.
'''
migration_block = '''      val slots = eq.slots.toMutableMap()

      val equipmentMigrations = when (characterId) {
        KAI_ID -> linkedMapOf(
          KAI_LEGACY_WHITE_WRAITH_ID to KAI_SRU_SG_ID,
          KAI_LEGACY_BLACKBLOOD_ARMOR_ID to KAI_SRU_MK20_ID,
          KAI_LEGACY_DEMON_JAW_ID to KAI_SRU_MK20_SENSOR_ID,
          KAI_LEGACY_TALON_ID to KAI_SRU_MK20_ARMS_ID,
          KAI_LEGACY_PHANTOM_GREAVES_ID to KAI_SRU_MK20_LEGS_ID
        )
        IRIS_ID -> linkedMapOf(IRIS_LEGACY_RECON_FRAME_ID to IRIS_PROJECT_07_ID)
        else -> emptyMap()
      }
      equipmentMigrations.forEach { (oldId, newId) ->
        slots.entries.filter { it.value == oldId }.forEach { it.setValue(newId) }
        inv.items[oldId]?.let { legacy ->
          val canonical = EquipmentCatalog.stackFor(newId).copy(
            condition = legacy.condition ?: "READY",
            metadata = EquipmentCatalog.stackFor(newId).metadata + legacy.metadata + mapOf("migratedFrom" to oldId)
          )
          inv = inv.copy(items = (inv.items - oldId) + (newId to canonical))
        }
      }

      // Collapse the historical two-slot Ivory/Ebony representation into one unique dual-weapon Item.
'''
system = replace_once(system, migration_anchor, migration_block, "current equipment save migration")

old_next = '''    var next = input.copy(
      inventories = inventories,
      equipment = equipment,
      metadata = input.metadata + ("characterEquipmentSchemaVersion" to SCHEMA_VERSION)
    )
'''
new_next = '''    var next = input.copy(
      inventories = inventories,
      equipment = equipment,
      omnivault = input.omnivault.copy(scanSlots = emptyList(), markedSourceIds = emptySet()),
      metadata = input.metadata + ("characterEquipmentSchemaVersion" to SCHEMA_VERSION)
    )
'''
system = replace_once(system, old_next, new_next, "retired Omnivault template state cleanup")
SYSTEM.write_text(system, encoding="utf-8")


# ---------------------------------------------------------------------------
# 4) Current Omnivault behavior: STORE/WITHDRAW remain; SCAN/COPY are retired;
# RESTORE is real and carries a per-equipment 24-hour cooldown.
# ---------------------------------------------------------------------------
vault = OMNIVAULT.read_text(encoding="utf-8")
if 'RESTORE_COOLDOWN_MS' not in vault:
    vault = vault.replace('object OmnivaultEngine {\n', 'object OmnivaultEngine {\n  const val RESTORE_COOLDOWN_MS = 24L * 60L * 60L * 1000L\n', 1)
vault = vault.replace('      OmnivaultCommand.Operation.SCAN -> scan(state, command)', '      OmnivaultCommand.Operation.SCAN -> invalid(state, "omnivault_capability_retired")')
vault = vault.replace('      OmnivaultCommand.Operation.COPY -> copy(state, command)', '      OmnivaultCommand.Operation.COPY -> invalid(state, "omnivault_capability_retired")')
vault = vault.replace('      OmnivaultCommand.Operation.RESTORE -> invalid(state, "restore_narrative_only")', '      OmnivaultCommand.Operation.RESTORE -> restore(state, command)')

restore_anchor = '  private fun scan(state: GameState, c: OmnivaultCommand): ExecutionResult {'
restore_fn = r'''  private fun restore(state: GameState, c: OmnivaultCommand): ExecutionResult {
    val requested = when {
      EquipmentCatalog.definition(c.itemId)?.id in setOf(KAI_SRU_SG_ID, KAI_SRU_MK20_ID, KAI_OMNIVAULT_RING_ID) -> EquipmentCatalog.definition(c.itemId)!!.id
      c.itemName.contains("SRU-SG", true) -> KAI_SRU_SG_ID
      c.itemName.contains("SRU-MK20", true) -> KAI_SRU_MK20_ID
      c.itemName.contains("Omnivault", true) -> KAI_OMNIVAULT_RING_ID
      else -> return invalid(state, "omnivault_restore_noncurrent_equipment")
    }
    val definition = EquipmentCatalog.definition(requested) ?: return invalid(state, "omnivault_restore_unknown_equipment")
    val now = c.timestampEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis()
    val cooldownUntil = state.omnivault.restoreCooldownUntilEpochMs[requested] ?: 0L
    if (cooldownUntil > now) return invalid(state, "omnivault_restore_cooldown")

    val inventory = state.inventories[KAI_ID] ?: InventoryState(KAI_ID)
    val equipment = state.equipment[KAI_ID] ?: EquipmentState(KAI_ID)
    val existing = inventory.items[requested]
    val equipped = definition.occupiesSlots.all { equipment.slots[it.key] == requested }
    val ready = existing?.condition.equals("READY", true)
    if (existing != null && ready && equipped) return invalid(state, "omnivault_restore_not_needed")

    val restored = EquipmentCatalog.stackFor(requested).copy(
      condition = "READY",
      metadata = EquipmentCatalog.stackFor(requested).metadata + existing?.metadata.orEmpty() + mapOf("restoredByOmnivault" to "true")
    )
    val slots = equipment.slots.toMutableMap()
    definition.occupiesSlots.forEach { slots[it.key] = requested }
    val next = state.copy(
      inventories = state.inventories + (KAI_ID to inventory.copy(items = inventory.items + (requested to restored))),
      equipment = state.equipment + (KAI_ID to equipment.copy(slots = slots)),
      omnivault = state.omnivault.copy(
        restoreCooldownUntilEpochMs = state.omnivault.restoreCooldownUntilEpochMs + (requested to (now + RESTORE_COOLDOWN_MS))
      )
    )
    return changed(CharacterStatEngine.preserveMissingHp(state, next, KAI_ID), "omnivault_equipment_restored")
  }

'''
if restore_fn not in vault:
    if restore_anchor not in vault:
        raise RuntimeError("Omnivault restore insertion anchor missing")
    vault = vault.replace(restore_anchor, restore_fn + restore_anchor, 1)
OMNIVAULT.write_text(vault, encoding="utf-8")


# ---------------------------------------------------------------------------
# 5) Update only obsolete Omnivault regression cases and add direct current-
# equipment tests. Do not throw away unrelated test coverage.
# ---------------------------------------------------------------------------
if CORE_TEST.exists():
    test = CORE_TEST.read_text(encoding="utf-8")
    if 'omnivaultThreeSlotsAndCopyRemainGameplayMechanics' in test:
        test = replace_test(test, 'omnivaultThreeSlotsAndCopyRemainGameplayMechanics', r'''  @Test fun omnivaultScanAndCopyAreRetired() {
    val state = base()
    val scan = StateReducer.execute(state, OmnivaultCommand(
      "scan-retired", "TURN_1", KAI_ID, source = CommandSource.RULE,
      operation = OmnivaultCommand.Operation.SCAN, itemId = "scrap", itemName = "Scrap"
    ))
    assertFalse(scan.applied)
    assertEquals("omnivault_capability_retired", scan.validation.reason)
    val copy = StateReducer.execute(state, OmnivaultCommand(
      "copy-retired", "TURN_1", KAI_ID, source = CommandSource.RULE,
      operation = OmnivaultCommand.Operation.COPY, itemId = "scrap", itemName = "Scrap"
    ))
    assertFalse(copy.applied)
    assertEquals("omnivault_capability_retired", copy.validation.reason)
  }''')
    if 'restoreIsNarrativeOnlyAndCannotMutateInventoryState' in test:
        test = replace_test(test, 'restoreIsNarrativeOnlyAndCannotMutateInventoryState', r'''  @Test fun omnivaultRestoreRepairsCurrentEquipmentAndStartsCooldown() {
    var state = CharacterEquipmentSystem.normalize(GameState.initial())
    val inventory = state.inventories.getValue(KAI_ID)
    val damaged = inventory.items.getValue(KAI_SRU_SG_ID).copy(condition = "DAMAGED")
    state = state.copy(inventories = state.inventories + (KAI_ID to inventory.copy(items = inventory.items + (KAI_SRU_SG_ID to damaged))))
    val restored = StateReducer.execute(state, OmnivaultCommand(
      "restore", "TURN_1", KAI_ID, source = CommandSource.UI,
      operation = OmnivaultCommand.Operation.RESTORE,
      itemId = KAI_SRU_SG_ID, itemName = "SRU-SG Shotgun", timestampEpochMs = 1000L
    ))
    assertTrue(restored.applied)
    assertEquals("READY", restored.state.inventories.getValue(KAI_ID).items.getValue(KAI_SRU_SG_ID).condition)
    assertEquals(1000L + OmnivaultEngine.RESTORE_COOLDOWN_MS, restored.state.omnivault.restoreCooldownUntilEpochMs[KAI_SRU_SG_ID])
    val again = StateReducer.execute(restored.state, OmnivaultCommand(
      "restore-again", "TURN_1", KAI_ID, source = CommandSource.UI,
      operation = OmnivaultCommand.Operation.RESTORE,
      itemId = KAI_SRU_SG_ID, itemName = "SRU-SG Shotgun", timestampEpochMs = 2000L
    ))
    assertFalse(again.applied)
    assertEquals("omnivault_restore_cooldown", again.validation.reason)
  }''')
    CORE_TEST.write_text(test, encoding="utf-8")

current_vault_tests = r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class OmnivaultCurrentCanonTest {
  private fun fresh() = CharacterEquipmentSystem.normalize(SpecialFollowersCanon.ensure(AnNhienCanon.ensure(GameState.initial())))

  @Test fun storageAndWithdrawalRemainAvailable() {
    var state = fresh()
    val inv = state.inventories.getValue(KAI_ID)
    state = state.copy(inventories = state.inventories + (KAI_ID to inv.copy(items = inv.items + ("scrap" to ItemStack("scrap", "Scrap", 2)))))
    val stored = OmnivaultEngine.execute(state, OmnivaultCommand(
      "store", "TURN_1", KAI_ID, source=CommandSource.RULE, operation=OmnivaultCommand.Operation.STORE,
      itemId="scrap", itemName="Scrap", quantity=1
    ))
    assertTrue(stored.applied)
    val withdrawn = OmnivaultEngine.execute(stored.state, OmnivaultCommand(
      "withdraw", "TURN_1", KAI_ID, source=CommandSource.RULE, operation=OmnivaultCommand.Operation.WITHDRAW,
      itemId="scrap", itemName="Scrap", quantity=1
    ))
    assertTrue(withdrawn.applied)
    assertEquals(2, withdrawn.state.inventories.getValue(KAI_ID).items.getValue("scrap").quantity)
  }

  @Test fun scanAndCopyStayRetiredAndTemplateStateIsCleared() {
    val dirty = fresh().copy(omnivault = fresh().omnivault.copy(
      scanSlots = listOf(ScanSlot(1, "legacy", ItemStack("legacy", "Legacy"), 1L)),
      markedSourceIds = setOf("legacy")
    ))
    val normalized = CharacterEquipmentSystem.normalize(dirty)
    assertTrue(normalized.omnivault.scanSlots.isEmpty())
    assertTrue(normalized.omnivault.markedSourceIds.isEmpty())
    for (op in listOf(OmnivaultCommand.Operation.SCAN, OmnivaultCommand.Operation.COPY)) {
      val result = OmnivaultEngine.execute(normalized, OmnivaultCommand(
        "retired-$op", "TURN_1", KAI_ID, source=CommandSource.RULE, operation=op,
        itemId="legacy", itemName="Legacy"
      ))
      assertFalse(result.applied)
      assertEquals("omnivault_capability_retired", result.validation.reason)
    }
  }

  @Test fun restoreOnlyAcceptsCurrentKaiEquipmentAndCooldownIsPerItem() {
    var state = fresh()
    val inv = state.inventories.getValue(KAI_ID)
    state = state.copy(inventories = state.inventories + (KAI_ID to inv.copy(items = inv.items - KAI_SRU_SG_ID)))
    val restored = OmnivaultEngine.execute(state, OmnivaultCommand(
      "restore-sg", "TURN_1", KAI_ID, source=CommandSource.UI, operation=OmnivaultCommand.Operation.RESTORE,
      itemId=KAI_SRU_SG_ID, itemName="SRU-SG Shotgun", timestampEpochMs=10_000L
    ))
    assertTrue(restored.applied)
    assertTrue(restored.state.inventories.getValue(KAI_ID).items.containsKey(KAI_SRU_SG_ID))
    assertEquals(KAI_SRU_SG_ID, restored.state.equipment.getValue(KAI_ID).slots["weapon"])
    val wrong = OmnivaultEngine.execute(restored.state, OmnivaultCommand(
      "restore-wrong", "TURN_1", KAI_ID, source=CommandSource.UI, operation=OmnivaultCommand.Operation.RESTORE,
      itemId="flashlight", itemName="Flashlight", timestampEpochMs=10_001L
    ))
    assertFalse(wrong.applied)
    assertEquals("omnivault_restore_noncurrent_equipment", wrong.validation.reason)
  }
}
'''
for path in (NATURAL_TEST, IDENTITY_TEST):
    if path.exists():
        path.write_text(current_vault_tests, encoding="utf-8")

EQUIPMENT_TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class SruEquipmentIntegrationTest {
  private fun fresh() = CharacterEquipmentSystem.normalize(SpecialFollowersCanon.ensure(AnNhienCanon.ensure(GameState.initial())))

  @Test fun kaiUsesCurrentSruEquipmentWithoutRetiredNames() {
    val state = fresh()
    val slots = state.equipment.getValue(KAI_ID).slots
    assertEquals(KAI_SRU_SG_ID, slots["weapon"])
    assertEquals(KAI_SRU_MK20_ID, slots["armor"])
    assertEquals(KAI_OMNIVAULT_RING_ID, slots["ring"])
    val names = slots.values.mapNotNull(EquipmentCatalog::definition).map { it.name }
    assertTrue("SRU-SG Shotgun" in names)
    assertTrue("SRU-MK20 Powered Armor" in names)
    assertFalse(names.any { it.contains("White Wraith", true) || it.contains("Blackblood", true) || it.contains("Demon Jaw", true) || it.contains("Talon", true) || it.contains("Phantom Greaves", true) })
    assertEquals("Demon Shell ∞ / Physical Shell finite", EquipmentCatalog.definition(KAI_SRU_SG_ID)!!.weapon!!.ammoDisplay)
  }

  @Test fun irisUsesProject07AndIvoryEbony() {
    val state = fresh()
    assertEquals(IRIS_IVORY_EBONY_SET_ID, state.equipment.getValue(IRIS_ID).slots["weapon"])
    assertEquals(IRIS_PROJECT_07_ID, state.equipment.getValue(IRIS_ID).slots["armor"])
    val project = EquipmentCatalog.definition(IRIS_PROJECT_07_ID)!!
    assertEquals("Project 07", project.name)
    val text = (project.abilities.map { it.name + " " + it.description } + project.restrictions).joinToString(" ").lowercase()
    assertFalse(text.contains("drone bay"))
    assertFalse(text.contains("launcher"))
  }

  @Test fun syvialUsesGodKillerAndLuciferArmor() {
    val state = fresh()
    assertEquals(SYVIAL_GODKILLER_ID, state.equipment.getValue(SYVIAL_ID).slots["weapon"])
    assertEquals(SYVIAL_LUCIFER_ARMOR_ID, state.equipment.getValue(SYVIAL_ID).slots["armor"])
    assertEquals("GodKiller", EquipmentCatalog.definition(SYVIAL_GODKILLER_ID)!!.name)
    assertEquals("Lucifer Armor", EquipmentCatalog.definition(SYVIAL_LUCIFER_ARMOR_ID)!!.name)
  }

  @Test fun legacyEquipmentIdsMigrateToCurrentIds() {
    val fresh = fresh()
    val inv = fresh.inventories.getValue(KAI_ID)
    val eq = fresh.equipment.getValue(KAI_ID)
    val legacy = fresh.copy(
      inventories = fresh.inventories + (KAI_ID to inv.copy(items = (inv.items - KAI_SRU_SG_ID) + (KAI_LEGACY_WHITE_WRAITH_ID to ItemStack(KAI_LEGACY_WHITE_WRAITH_ID, "White Wraith Magnum")))),
      equipment = fresh.equipment + (KAI_ID to eq.copy(slots = eq.slots + ("weapon" to KAI_LEGACY_WHITE_WRAITH_ID))),
      metadata = fresh.metadata - "characterEquipmentSchemaVersion"
    )
    val migrated = CharacterEquipmentSystem.normalize(legacy)
    assertEquals(KAI_SRU_SG_ID, migrated.equipment.getValue(KAI_ID).slots["weapon"])
    assertTrue(migrated.inventories.getValue(KAI_ID).items.containsKey(KAI_SRU_SG_ID))
    assertFalse(migrated.inventories.getValue(KAI_ID).items.containsKey(KAI_LEGACY_WHITE_WRAITH_ID))
  }
}
''', encoding="utf-8")

combined = "\n".join(path.read_text(encoding="utf-8") for path in (GAME_STATE, SPECIAL, SYSTEM, OMNIVAULT, EQUIPMENT_TEST))
required = (
    'KAI_SRU_SG_ID = "kai:sru-sg"',
    'KAI_SRU_MK20_ID = "kai:sru-mk20"',
    'name = "SRU-SG Shotgun"',
    'name = "SRU-MK20 Powered Armor"',
    'IRIS_PROJECT_07_ID = "iris:project-07"',
    'name = "Project 07"',
    'name = "GodKiller"',
    'name = "Lucifer Armor"',
    'omnivault_capability_retired',
    'RESTORE_COOLDOWN_MS = 24L * 60L * 60L * 1000L',
    'omnivault_equipment_restored',
    'private const val SCHEMA_VERSION = "3"',
)
for marker in required:
    if marker not in combined:
        raise RuntimeError("SRU equipment integration contract missing: " + marker)
for retired_visible in (
    'name = "White Wraith Magnum"',
    'name = "Blackblood Armor"',
    'name = "Demon Jaw Mask"',
    'name = "Talon Gauntlets"',
    'name = "Phantom Greaves"',
    'name = "Blackblood Recon Frame R03"',
):
    if retired_visible in SYSTEM.read_text(encoding="utf-8"):
        raise RuntimeError("Retired visible equipment survived: " + retired_visible)

print("Current SRU equipment integrated: Kai SRU-SG/SRU-MK20/Omnivault restore, Iris Project 07/Ivory & Ebony, Syvial GodKiller/Lucifer Armor, save migration and regression tests.")
