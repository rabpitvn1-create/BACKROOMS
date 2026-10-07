from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
ASSETS = ROOT / "app/src/main/assets"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

SPECIAL = CORE / "SpecialFollowersCanon.kt"
STATS = CORE / "CharacterStats.kt"
EQUIPMENT = CORE / "CharacterEquipmentSystem.kt"
CATALOG = CORE / "CompanionSkillCatalog.kt"
COMBAT = CORE / "CombatRuntime.kt"
FACADE = CORE / "GameCoreFacade.kt"
INTENT = CORE / "IntentPipeline.kt"
KNOWLEDGE = ASSETS / "knowledge/knowledge_db.json"
KNOWLEDGE_ENGINE = CORE / "knowledge/KnowledgeContextEngine.kt"
KNOWLEDGE_VALIDATOR = CORE / "knowledge/KnowledgeLocalValidator.kt"
DRIVE_CANON = ROOT / "drive-canon.txt"

LUC_TRAM_ID = "luc_tram"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def replace_re_once(text: str, pattern: str, replacement: str, label: str) -> str:
    out, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one regex match, found {count}")
    return out


for asset in (
    ASSETS / "avatars/luctram_avatar.png",
    ASSETS / "luctram_overlay.png",
    ASSETS / "lucia_overlay.png",
    ASSETS / "canon/Lục_Trầm_Codex.md",
):
    if not asset.is_file() or asset.stat().st_size <= 0:
        raise RuntimeError(f"Missing Lục Trầm/Lucia asset: {asset}")


# ---------------------------------------------------------------------------
# 1) Canonical follower identity. Keep only a narrow deprecated IRIS_ID symbol
# for generated compatibility; its value is luc_tram and no Iris character is
# seeded or retained in save state.
# ---------------------------------------------------------------------------
special = SPECIAL.read_text(encoding="utf-8")
special = replace_once(
    special,
    'const val IRIS_ID = "iris"\nconst val SYVIAL_ID = "syvial"\n\nconst val IRIS_IVORY_ID = "iris:ivory"\nconst val IRIS_EBONY_ID = "iris:ebony"\nconst val IRIS_RECON_FRAME_ID = "iris:blackblood-recon-frame-r03"\n',
    'const val LUC_TRAM_ID = "luc_tram"\n@Deprecated("Legacy source compatibility only; runtime identity is Lục Trầm.")\nconst val IRIS_ID = LUC_TRAM_ID\nconst val SYVIAL_ID = "syvial"\n\nconst val LUC_TRAM_TICH_QUANG_ID = "luc_tram:tich-quang-kiem"\nconst val LUC_TRAM_KIEM_KHAI_ID = "luc_tram:thien-co-bach-kim-kiem-khai"\n@Deprecated("Legacy item migration only.") const val IRIS_IVORY_ID = "iris:ivory"\n@Deprecated("Legacy item migration only.") const val IRIS_EBONY_ID = "iris:ebony"\n@Deprecated("Legacy item migration only.") const val IRIS_RECON_FRAME_ID = "iris:blackblood-recon-frame-r03"\n',
    "Lục Trầm canonical ids",
)
special = replace_once(
    special,
    '  val irisEquipmentSlots: Map<String, String> = linkedMapOf(\n    "weapon" to IRIS_IVORY_EBONY_SET_ID,\n    "armor" to IRIS_RECON_FRAME_ID\n  )\n',
    '  val lucTramEquipmentSlots: Map<String, String> = linkedMapOf(\n    "weapon" to LUC_TRAM_TICH_QUANG_ID,\n    "armor" to LUC_TRAM_KIEM_KHAI_ID\n  )\n  @Deprecated("Legacy generated call sites only.")\n  val irisEquipmentSlots: Map<String, String> get() = lucTramEquipmentSlots\n',
    "Lục Trầm equipment slots",
)
special = special.replace('name = "Iris"', 'name = "Lục Trầm"')
special = special.replace('"role" to "Scout / Target Eliminator"', '"role" to "Chân truyền đệ tử Thiên Kiếm Môn / Chính Đạo Kiếm Tu"')
special = special.replace('"combatStyle" to "Gunslinger"', '"combatStyle" to "Thiên Kiếm Thất Thức / Tịch Quang kiếm pháp"')
special = special.replace('"signatureWeapons" to "Ivory & Ebony"', '"signatureWeapon" to "Tịch Quang Kiếm"')
special = special.replace('"armor" to "Blackblood Recon Frame R03"', '"armor" to "Thiên Cơ Bạch Kim Kiếm Khải"')
special = special.replace('"canonRef" to "IRIS-BELIAL-BLACKBLOOD-CODEX-20260817-R05"', '"canonRef" to "LUC-TRAM-CODEX-R05-VISUAL-R02"')
special = special.replace('"encounterLevels" to ENCOUNTER_LEVELS,', '"encounterLevels" to "1-6",', 1)

special = replace_once(
    special,
    '  const val IRIS_AVATAR_REF = "avatars/Iris_avatar.jpg"\n  const val SYVIAL_AVATAR_REF = "avatars/Syvial_avatar.jpg"\n  const val IRIS_PARTY_CHEAT_CODE = "/iris123"\n',
    '  const val LUC_TRAM_AVATAR_REF = "avatars/luctram_avatar.png"\n  @Deprecated("Legacy generated call sites only.") const val IRIS_AVATAR_REF = LUC_TRAM_AVATAR_REF\n  const val SYVIAL_AVATAR_REF = "avatars/Syvial_avatar.jpg"\n  const val LUC_TRAM_PARTY_CHEAT_CODE = "/luctram123"\n  @Deprecated("Legacy generated call sites only.") const val IRIS_PARTY_CHEAT_CODE = LUC_TRAM_PARTY_CHEAT_CODE\n',
    "Lục Trầm avatar and party cheat",
)
special = special.replace("avatarRef = IRIS_AVATAR_REF", "avatarRef = LUC_TRAM_AVATAR_REF", 1)

old_ensure = '''  fun ensure(state: GameState): GameState {
    val iris = irisCharacter(state.characters[IRIS_ID])
    val syvial = syvialCharacter(state.characters[SYVIAL_ID])
    val irisInventory = state.inventories[IRIS_ID] ?: InventoryState(IRIS_ID)
    val syvialInventory = state.inventories[SYVIAL_ID] ?: InventoryState(SYVIAL_ID)
    val irisEquipment = state.equipment[IRIS_ID]?.slots.orEmpty() + irisEquipmentSlots
    val syvialEquipment = state.equipment[SYVIAL_ID]?.slots.orEmpty() + syvialEquipmentSlots
    return state.copy(
      characters = state.characters + (IRIS_ID to iris) + (SYVIAL_ID to syvial),
      inventories = state.inventories + (IRIS_ID to irisInventory) + (SYVIAL_ID to syvialInventory),
      equipment = state.equipment +
        (IRIS_ID to EquipmentState(IRIS_ID, irisEquipment)) +
        (SYVIAL_ID to EquipmentState(SYVIAL_ID, syvialEquipment))
    )
  }
'''
new_ensure = '''  fun ensure(state: GameState): GameState {
    val legacyIris = state.characters["iris"]
    val lucTram = irisCharacter(state.characters[LUC_TRAM_ID] ?: legacyIris)
    val syvial = syvialCharacter(state.characters[SYVIAL_ID])
    val legacyInventory = state.inventories[LUC_TRAM_ID] ?: state.inventories["iris"] ?: InventoryState(LUC_TRAM_ID)
    val lucTramInventory = legacyInventory.copy(
      ownerId = LUC_TRAM_ID,
      items = legacyInventory.items.filterKeys { !it.startsWith("iris:") }
    )
    val syvialInventory = state.inventories[SYVIAL_ID] ?: InventoryState(SYVIAL_ID)
    val syvialEquipment = state.equipment[SYVIAL_ID]?.slots.orEmpty() + syvialEquipmentSlots
    val migratedParty = state.party.copy(
      leaderId = if (state.party.leaderId == "iris") LUC_TRAM_ID else state.party.leaderId,
      memberIds = state.party.memberIds.map { if (it == "iris") LUC_TRAM_ID else it }.distinct()
    )
    return state.copy(
      characters = (state.characters - "iris") + (LUC_TRAM_ID to lucTram) + (SYVIAL_ID to syvial),
      inventories = (state.inventories - "iris") + (LUC_TRAM_ID to lucTramInventory) + (SYVIAL_ID to syvialInventory),
      equipment = (state.equipment - "iris") +
        (LUC_TRAM_ID to EquipmentState(LUC_TRAM_ID, lucTramEquipmentSlots)) +
        (SYVIAL_ID to EquipmentState(SYVIAL_ID, syvialEquipment)),
      party = migratedParty
    )
  }
'''
special = replace_once(special, old_ensure, new_ensure, "legacy Iris save migration")
SPECIAL.write_text(special, encoding="utf-8")


# ---------------------------------------------------------------------------
# 2) 1.1.93a character stats and canonical Lục Trầm equipment.
# ---------------------------------------------------------------------------
stats = STATS.read_text(encoding="utf-8")
stats = stats.replace(
    '    "iris" -> "SCOUT / TARGET ELIMINATOR / DUAL-GUN MARKSMAN"',
    '    "luc_tram" -> "CHÂN TRUYỀN THIÊN KIẾM MÔN / CHÍNH ĐẠO KIẾM TU"',
)
stats = stats.replace(
    '    "cao_minh", "kai", "iris", "syvial" -> EnergyProfile.infinite()',
    '    "cao_minh", "kai", "syvial" -> EnergyProfile.infinite()',
)
STATS.write_text(stats, encoding="utf-8")

equipment = EQUIPMENT.read_text(encoding="utf-8")
equipment = replace_once(
    equipment,
    'const val IRIS_IVORY_EBONY_SET_ID = "iris:ivory-ebony-set"\n',
    '@Deprecated("Legacy item migration only.")\nconst val IRIS_IVORY_EBONY_SET_ID = "iris:ivory-ebony-set"\n',
    "legacy Iris set constant",
)
equipment = replace_re_once(
    equipment,
    r'''    EquipmentDefinition\(
      id = IRIS_RECON_FRAME_ID,.*?
    \),
    EquipmentDefinition\(
      id = IRIS_IVORY_EBONY_SET_ID,.*?
    \),
(?=    EquipmentDefinition\(
      id = SYVIAL_GODKILLER_ID)''',
    '''    EquipmentDefinition(
      id = LUC_TRAM_KIEM_KHAI_ID, name = "Thiên Cơ Bạch Kim Kiếm Khải", type = "CULTIVATION ARTIFACT ARMOR", primarySlot = EquipmentSlot.ARMOR,
      abilities = listOf(
        ability("Thiên Cơ Kiếm Khải", "Pháp khí hộ thể của Thiên Kiếm Môn, ổn định thân pháp và kiếm thế."),
        ability("Kiếm ý cộng hưởng", "Đồng bộ với Tịch Quang Kiếm trong cận chiến và phản kiếm.")
      ),
      restrictions = listOf("Không phải AI/mecha armor.", "Không có súng, missile, HUD, energy wing hoặc drone ẩn."),
      canonRef = "LUC-TRAM-CODEX-R05-VISUAL-R02"
    ),
    EquipmentDefinition(
      id = LUC_TRAM_TICH_QUANG_ID, name = "Tịch Quang Kiếm", type = "BONDED CULTIVATION GREATSWORD", primarySlot = EquipmentSlot.WEAPON,
      weapon = WeaponGameplayStats(24),
      abilities = listOf(
        ability("Thiên Kiếm Linh Tâm", "Đọc quỹ đạo, cân bằng, linh lực và bất ổn kiếm/thân pháp từ dữ kiện trực tiếp.", "Không đọc tâm trí, đạo đức, ký ức hoặc tự nhận diện Entity."),
        ability("Thiên Kiếm Thất Thức", "Kiếm pháp nền cho Nhất Tuyến Phá Vọng, Bạch Hồng Quán Nhật, Tịch Quang Phản Kiếm và Vạn Kiếm Quy Tâm.")
      ),
      restrictions = listOf("Đúng một Tịch Quang đại kiếm.", "Không biến thành súng hoặc vũ khí công nghệ."),
      canonRef = "LUC-TRAM-CODEX-R05-VISUAL-R02"
    ),
''',
    "replace Iris equipment with Lục Trầm",
)
equipment = replace_once(
    equipment,
    '''  fun definition(itemId: String): EquipmentDefinition? = when (itemId) {
    IRIS_IVORY_ID, IRIS_EBONY_ID -> definitions[IRIS_IVORY_EBONY_SET_ID]
    else -> definitions[itemId]
  }
''',
    '''  fun definition(itemId: String): EquipmentDefinition? = when (itemId) {
    IRIS_IVORY_ID, IRIS_EBONY_ID, IRIS_IVORY_EBONY_SET_ID -> definitions[LUC_TRAM_TICH_QUANG_ID]
    IRIS_RECON_FRAME_ID -> definitions[LUC_TRAM_KIEM_KHAI_ID]
    else -> definitions[itemId]
  }
''',
    "legacy equipment migration",
)
equipment = replace_once(
    equipment,
    '    IRIS_ID -> linkedMapOf(EquipmentSlot.WEAPON to IRIS_IVORY_EBONY_SET_ID, EquipmentSlot.ARMOR to IRIS_RECON_FRAME_ID)\n',
    '    LUC_TRAM_ID -> linkedMapOf(EquipmentSlot.WEAPON to LUC_TRAM_TICH_QUANG_ID, EquipmentSlot.ARMOR to LUC_TRAM_KIEM_KHAI_ID)\n',
    "Lục Trầm starting loadout",
)
EQUIPMENT.write_text(equipment, encoding="utf-8")


# ---------------------------------------------------------------------------
# 3) Companion skill projection and combat behavior. The target engine has one
# Party command rather than V2's skill picker, so the V2 proc percentages are
# projected as deterministic per-ATTACK companion procs.
# ---------------------------------------------------------------------------
catalog = CATALOG.read_text(encoding="utf-8")
catalog = replace_re_once(
    catalog,
    r'''  private val iris = listOf\(.*?\n  \)\n\n  private val syvial = listOf\(''',
    '''  private val lucTram = listOf(
    s("Tịch Quang Hợp Kích", "COMMAND", "Khi Lục Trầm thực hiện Party ATTACK", "Kiếm thế hợp kích bằng Tịch Quang; projection 150% Weapon DMG."),
    s("Tịch Quang Phản Kiếm", "PROC", "52% sau ATTACK hợp lệ", "+25% Weapon DMG."),
    s("Nhất Tuyến Phá Vọng", "PROC", "55% sau ATTACK hợp lệ", "+20% Weapon DMG; Xuyên giáp 10%."),
    s("Thiên Kiếm Chấn", "PROC", "46% sau ATTACK hợp lệ", "+15% Weapon DMG; Choáng phản ứng hiện tại."),
    s("Bạch Hồng Quán Nhật", "PROC", "49% sau ATTACK hợp lệ", "+20% Weapon DMG; Chảy máu projection 2 turn x 3% Max HP."),
    s("Vạn Kiếm Quy Tâm", "PROC", "51% sau ATTACK hợp lệ", "+20% Weapon DMG; Trúng độc projection 2 turn x 4% Max HP."),
    s("Thiên Kiếm Định Giới", "ULTIMATE", "Ultimate slot của Lục Trầm", "Đúng 60 hit; mỗi hit dùng current DMG +15% bonus.")
  )

  private val syvial = listOf(''',
    "Lục Trầm skill catalog",
)
catalog = catalog.replace("    IRIS_ID -> iris", "    LUC_TRAM_ID -> lucTram")
CATALOG.write_text(catalog, encoding="utf-8")

combat = COMBAT.read_text(encoding="utf-8")
combat = combat.replace("IRIS_ID", "LUC_TRAM_ID")
combat = combat.replace("IRIS_", "LUC_TRAM_")
combat = combat.replace('"combat.iris', '"combat.lucTram')
combat = combat.replace("Iris thực hiện lệnh TẤN CÔNG bằng Ivory & Ebony", "Lục Trầm thực hiện lệnh TẤN CÔNG bằng Tịch Quang Kiếm")
combat = combat.replace("Iris thực hiện lệnh TẤN CÔNG nhưng loạt bắn không trúng mục tiêu.", "Lục Trầm thực hiện lệnh TẤN CÔNG nhưng kiếm thế không trúng mục tiêu.")
combat = combat.replace("Iris/Syvial", "Lục Trầm/Syvial")

luc_start = combat.find("    if (irisActive && c.entityHp > 0) {\n")
syvial_start = combat.find("    if (syvialActive && c.entityHp > 0) {\n", luc_start)
if luc_start < 0 or syvial_start < 0:
    raise RuntimeError("Lục Trầm combat replacement: Iris block boundary missing")
luc_block = r'''    if (irisActive && c.entityHp > 0 && intent == Intent.ATTACK) {
      val lucTramWeapon = CharacterStatEngine.weaponDamage(resolvedState, LUC_TRAM_ID)
      val ultimate = c.eventCounter % LUC_TRAM_ULTIMATE_INTERVAL_TURNS == 0
      if (ultimate) {
        val perHit = companionSkillDamage(lucTramWeapon, 115, profile.armor)
        val damage = min(c.entityHp, perHit * 60)
        val hp = max(0, c.entityHp - damage)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 24))
        log += "Thiên Kiếm Định Giới: đúng 60 hit, mỗi hit current DMG +15% bonus; tổng -$damage HP."
      } else {
        if (roll(c.copy(eventCounter = c.eventCounter + 151), 100) < 52 && c.entityHp > 0) {
          val damage = min(c.entityHp, companionSkillDamage(lucTramWeapon, 125, profile.armor))
          c = c.copy(entityHp = max(0, c.entityHp - damage), entityCondition = condition(max(0, c.entityHp - damage), c.entityMaxHp))
          log += "Tịch Quang Phản Kiếm proc 52%: +25% Weapon DMG = -$damage HP."
        }
        if (roll(c.copy(eventCounter = c.eventCounter + 163), 100) < 55 && c.entityHp > 0) {
          val damage = min(c.entityHp, companionSkillDamage(lucTramWeapon, 120, armorAfterIgnore(profile.armor, 10)))
          c = c.copy(entityHp = max(0, c.entityHp - damage), entityCondition = condition(max(0, c.entityHp - damage), c.entityMaxHp))
          log += "Nhất Tuyến Phá Vọng proc 55%: +20% Weapon DMG, Xuyên giáp 10% = -$damage HP."
        }
        if (roll(c.copy(eventCounter = c.eventCounter + 179), 100) < 46 && c.entityHp > 0) {
          val damage = min(c.entityHp, companionSkillDamage(lucTramWeapon, 115, profile.armor))
          c = c.copy(entityHp = max(0, c.entityHp - damage), entityCondition = condition(max(0, c.entityHp - damage), c.entityMaxHp))
          entityStunnedThisTurn = true
          log += "Thiên Kiếm Chấn proc 46%: +15% Weapon DMG = -$damage HP; Entity bị Choáng trong phản ứng hiện tại."
        }
        if (roll(c.copy(eventCounter = c.eventCounter + 191), 100) < 49 && c.entityHp > 0) {
          val damage = min(c.entityHp, companionSkillDamage(lucTramWeapon, 120, profile.armor))
          c = c.copy(entityHp = max(0, c.entityHp - damage), entityCondition = condition(max(0, c.entityHp - damage), c.entityMaxHp))
          log += "Bạch Hồng Quán Nhật proc 49%: +20% Weapon DMG = -$damage HP; Chảy máu projection 2 turn x 3% Max HP."
        }
        if (roll(c.copy(eventCounter = c.eventCounter + 197), 100) < 51 && c.entityHp > 0) {
          val damage = min(c.entityHp, companionSkillDamage(lucTramWeapon, 120, profile.armor))
          c = c.copy(entityHp = max(0, c.entityHp - damage), entityCondition = condition(max(0, c.entityHp - damage), c.entityMaxHp))
          log += "Vạn Kiếm Quy Tâm proc 51%: +20% Weapon DMG = -$damage HP; Trúng độc projection 2 turn x 4% Max HP."
        }
      }
    }

'''
combat = combat[:luc_start] + luc_block + combat[syvial_start:]

# Retire the old Dead Angle counter; Lục Trầm's five procs are already resolved in ATTACK.
combat = re.sub(
    r'''        if \(intent == Intent\.ATTACK && irisActive && c\.entityHp > 0 && roll\(c\.copy\(eventCounter = c\.eventCounter \+ 281\), 100\) < 15\) \{.*?\n        \}\n''',
    "",
    combat,
    count=1,
    flags=re.S,
)
combat = combat.replace("ARGUS Terrain Read: Iris đánh dấu mục tiêu Analyzed trong 3 turn.", "Thiên Kiếm Linh Tâm: Lục Trầm khóa quỹ đạo và bất ổn kiếm/thân pháp từ dữ kiện trực tiếp.")
combat = combat.replace("Dead Angle: Iris phản kích tức thời 120% DMG", "Tịch Quang Phản Kiếm")
COMBAT.write_text(combat, encoding="utf-8")


# ---------------------------------------------------------------------------
# 4) Main runtime: post-Level-0 reunion, distinct Lucia identity, and snapshot
# overlays for Lục Trầm + Lucia Lục.
# ---------------------------------------------------------------------------
drive = DRIVE_CANON.read_text(encoding="utf-8")
section_start = drive.find("IRIS / SYVIAL")
section_end = drive.find("GAMEPLAY HARD LOCK", section_start)
if section_start < 0 or section_end < 0:
    raise RuntimeError("Drive canon Lục Trầm replacement section missing")
luc_section = '''LỤC TRẦM / SYVIAL
- Lục Trầm và Syvial đã tồn tại trước Backrooms; không phải procedural survivor. Khi continuity còn SEPARATED, Cao Minh không được biết vị trí/tình trạng của họ nếu state chưa xác nhận.
- Lục Trầm: chân truyền đệ tử Thiên Kiếm Môn, chính đạo kiếm tu. Cao Minh và Lục Trầm đã biết và từng giao chiến; reunion là tái ngộ căng thẳng/thù địch, tuyệt đối không kể như lần đầu gặp. Lục Trầm dùng đúng một Tịch Quang Kiếm và Thiên Cơ Bạch Kim Kiếm Khải. Thiên Kiếm Linh Tâm chỉ đọc quỹ đạo, cân bằng, linh lực/spatial/sword instability từ dữ kiện trực tiếp; không đọc tâm trí, đạo đức, ký ức, không tự nhận diện Entity và không cung cấp Backrooms knowledge.
- Visual Lock R02: nữ kiếm sĩ trẻ trưởng thành, cao và cân đối; tóc bạc-trắng rất dài buộc cao, mắt xanh-xám lạnh; kiếm quan vàng-đen có tinh thể xanh; kiếm khải trắng-bạc-vàng trên nền tối với điểm sapphire tiết chế; đúng một đại kiếm Tịch Quang trắng-bạc rất dài, thẳng và bản rộng. Đây là cultivation artifact, không phải AI/mecha tech; không hidden gun/missile/HUD/energy wing/halo/mask/overglow.
- Lucia Lục / Hứa Thuý Mai là nhân vật riêng với runtime id lucia. Không alias, rename, merge hoặc migrate Lucia Lục thành Lục Trầm.
- Syvial giữ canon hiện hành, runtime id syvial.

'''
drive = drive[:section_start] + luc_section + drive[section_end:]
drive = drive.replace("Không kể xen cảnh Iris/Syvial", "Không kể xen cảnh Lục Trầm/Syvial")
drive = drive.replace("Iris reunion: 0,0025% khi đủ điều kiện.", "Lục Trầm reunion: 0,25% mỗi lượt physical đủ điều kiện sau Level 0.")
DRIVE_CANON.write_text(drive, encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")
main = re.sub(
    r'  private static final String DRIVE_CANON = .*?;\n',
    '  private static final String DRIVE_CANON = ' + json.dumps(drive.strip(), ensure_ascii=False) + ';\n',
    main,
    count=1,
)
main = main.replace("irisReunion", "lucTramReunion")
main = main.replace('"iris"', '"luc_tram"')
main = main.replace("IRIS / SYVIAL", "LỤC TRẦM / SYVIAL")
main = main.replace("IRIS/SYVIAL", "LỤC TRẦM/SYVIAL")
main = main.replace("Iris", "Lục Trầm")
main = main.replace("Ivory & Ebony", "Tịch Quang Kiếm")
main = main.replace("Blackblood Recon Frame R03", "Thiên Cơ Bạch Kim Kiếm Khải")
main = main.replace("Scout / Target Eliminator", "Chân truyền Thiên Kiếm Môn / Chính Đạo Kiếm Tu")
main = main.replace("Gunslinger", "Kiếm tu")
main = main.replace(
    'thresholdRoll("lucTramReunion", 10000, 25, physical && reunionEligibleAndroid(state, "luc_tram")',
    'thresholdRoll("lucTramReunion", 10000, 25, level > 0 && physical && reunionEligibleAndroid(state, "luc_tram")',
)
main = main.replace(
    'if (value.contains("luc_tram")) return presentCharacter(before, "luc_tram") || rollSuccess(rolls, "lucTramReunion");',
    'if (value.contains("luc_tram") || value.contains("lục trầm") || value.contains("luc tram")) return presentCharacter(before, "luc_tram") || rollSuccess(rolls, "lucTramReunion");',
)
party_old = '''      String name = item instanceof JSONObject ? ((JSONObject)item).optString("name", "") : String.valueOf(item);
      if (lower(name).contains(lower(needle))) return true;
'''
party_new = '''      String name = item instanceof JSONObject ? ((JSONObject)item).optString("name", "") : String.valueOf(item);
      String id = item instanceof JSONObject ? ((JSONObject)item).optString("id", "") : "";
      if (lower(name).contains(lower(needle)) || lower(id).contains(lower(needle))) return true;
'''
if party_old in main:
    main = main.replace(party_old, party_new, 1)

if "LUC_TRAM_LUCIA_SNAPSHOT_OVERLAYS_R01" not in main:
    overlay_anchor = "box.appendChild(kai);"
    if main.count(overlay_anchor) != 1:
        raise RuntimeError(f"Snapshot companion overlay anchor count={main.count(overlay_anchor)}")
    overlay_js = r'''box.appendChild(kai);
        // LUC_TRAM_LUCIA_SNAPSHOT_OVERLAYS_R01
        var partyMembers=(state&&state.partyDetails&&Array.isArray(state.partyDetails.members))?state.partyDetails.members:[];
        [
          {id:'luc_tram',src:'file:///android_asset/luctram_overlay.png',alt:'Lục Trầm',side:'right'},
          {id:'lucia',src:'file:///android_asset/lucia_overlay.png',alt:'Lucia Lục',side:'left'}
        ].forEach(function(def){
          var member=partyMembers.find(function(m){return m&&String(m.id||'').toLowerCase()===def.id;});
          if(!member)return;
          var presence=String(member.presence||'ACTIVE').toUpperCase();
          if(member.present===false||presence==='DEAD'||presence==='MISSING'||presence==='SEPARATED')return;
          var img=document.createElement('img');
          img.className='snapshot-companion snapshot-companion-'+def.id;
          img.src=def.src;img.alt=def.alt;
          img.style.position='absolute';img.style.bottom='0';img.style.height='92%';img.style.maxWidth='46%';
          img.style.objectFit='contain';img.style.pointerEvents='none';img.style.zIndex='3';
          img.style[def.side]='1%';
          box.appendChild(img);
        });'''
    main = main.replace(overlay_anchor, overlay_js, 1)

distinct_lock = "Lucia Lục/Hứa Thuý Mai là nhân vật riêng với runtime id lucia; tuyệt đối không alias, rename, merge hoặc migrate Lucia Lục thành Lục Trầm."
if distinct_lock not in main:
    marker = "LỤC TRẦM / SYVIAL FOLLOWER LOCK:"
    idx = main.find(marker)
    if idx >= 0:
        line_end = main.find("\n", idx)
        main = main[:line_end] + " " + distinct_lock + main[line_end:]
    else:
        # Static prompt appendix near the already generated follower text.
        main = main.replace(
            "Không hạ năng lực hoặc bịa thêm canon để cân bằng gameplay.",
            "Không hạ năng lực hoặc bịa thêm canon để cân bằng gameplay. " + distinct_lock,
            1,
        )
MAIN.write_text(main, encoding="utf-8")


# ---------------------------------------------------------------------------
# 5) Runtime knowledge retrieval: delete active Iris records and inject compact
# Lục Trầm records sourced from the copied V2 codex.
# ---------------------------------------------------------------------------
db = json.loads(KNOWLEDGE.read_text(encoding="utf-8"))
records = db.get("records", [])
clean = []
for record in records:
    raw = json.dumps(record, ensure_ascii=False).lower()
    if "iris" in raw:
        continue
    clean.append(record)

def record(id_, kind, text_, priority, tags, affordances=()):
    return {
        "id": id_,
        "domain": "CHARACTER" if id_.startswith("CHAR.") else ("RELATIONSHIP" if id_.startswith("REL.") else "STORY"),
        "kind": kind,
        "text": text_,
        "source": {"document": "app/src/main/assets/canon/Lục_Trầm_Codex.md", "anchor": "R05 / Visual Lock R02"},
        "authority": "CHARACTER_CANON",
        "mutability": "IMMUTABLE" if not id_.startswith("STORY.") else "BASELINE",
        "priority": priority,
        "tags": list(tags),
        "references": [],
        "affordances": list(affordances),
    }

clean.extend([
    record("CHAR.LUC_TRAM.RUNTIME_CORE", "runtime-card",
           "Lục Trầm: chân truyền đệ tử Thiên Kiếm Môn, chính đạo kiếm tu. Cô và Cao Minh đã biết và từng giao chiến; reunion là tái ngộ căng thẳng, không phải lần đầu gặp. Giữ đúng một Tịch Quang Kiếm và Thiên Cơ Bạch Kim Kiếm Khải.",
           20, ["luc_tram", "lục trầm", "luc tram", "present core"]),
    record("CHAR.LUC_TRAM.THIEN_KIEM_LINH_TAM", "ability",
           "Thiên Kiếm Linh Tâm đọc quỹ đạo, cân bằng, linh lực/spatial/sword instability từ quan sát hợp lệ. Không đọc tâm trí, đạo đức, ký ức, không tự nhận diện Entity và không cấp Backrooms knowledge.",
           42, ["luc_tram", "lục trầm", "thiên kiếm linh tâm"], ["trace_analysis", "direct_threat"]),
    record("CHAR.LUC_TRAM.EQUIPMENT", "equipment",
           "Lục Trầm dùng đúng một đại kiếm Tịch Quang và Thiên Cơ Bạch Kim Kiếm Khải. Đây là cultivation artifacts; không AI/mecha tech, hidden gun/missile/HUD/energy wing.",
           45, ["luc_tram", "tịch quang", "thiên cơ bạch kim kiếm khải"], ["direct_threat"]),
    record("REL.CAO_MINH.LUC_TRAM.BASELINE", "baseline",
           "Cao Minh và Lục Trầm có lịch sử đối đầu trước Backrooms. Reunion phải giữ nhận thức đã biết nhau và căng thẳng hiện hữu; không reset thành stranger introduction.",
           38, ["cao minh", "luc_tram", "lục trầm"]),
    record("STORY.MAIN.SEPARATION", "baseline-continuity",
           "Cao Minh tỉnh lại một mình tại Level 0, không có dấu vết của Lục Trầm. Lục Trầm tồn tại trong continuity nhưng reunion chỉ hợp lệ sau Level 0 khi state/roll xác nhận; không dùng dữ kiện hậu trường để biết vị trí hiện tại.",
           38, ["main campaign", "separated", "luc_tram", "lục trầm"]),
])
db["records"] = clean
KNOWLEDGE.write_text(json.dumps(db, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

engine = KNOWLEDGE_ENGINE.read_text(encoding="utf-8")
engine = engine.replace('if ("iris" in presentActors) add("CHAR.IRIS.RUNTIME_CORE", "present actor runtime core")',
                        'if ("luc_tram" in presentActors) add("CHAR.LUC_TRAM.RUNTIME_CORE", "present actor runtime core")')
engine = re.sub(
    r'''      if \("iris" in presentActors\) \{
        add\("REL\.KAI\.IRIS\.BASELINE", "present relationship edge"\)
        add\("ADDR\.IRIS\.KAI", "present address lock"\)
      \}
''',
    '''      if ("luc_tram" in presentActors) {
        add("REL.CAO_MINH.LUC_TRAM.BASELINE", "present relationship edge")
      }
''',
    engine,
    count=1,
)
engine = engine.replace('      if ("iris" in presentActors && "syvial" in presentActors) add("REL.IRIS.SYVIAL.BASELINE", "present relationship edge")\n', '')
engine = engine.replace('if (hasAny(actionText, "argus", "terrain read")) direct += "CHAR.IRIS.ARGUS"',
                        'if (hasAny(actionText, "thiên kiếm linh tâm", "thien kiem linh tam")) direct += "CHAR.LUC_TRAM.THIEN_KIEM_LINH_TAM"')
engine = engine.replace('if (hasAny(actionText, "thousandfold")) direct += "CHAR.IRIS.THOUSANDFOLD"', '')
engine = engine.replace('if (hasAny(actionText, "ivory", "ebony")) direct += "CHAR.IRIS.IVORY_EBONY"',
                        'if (hasAny(actionText, "tịch quang", "tich quang", "thiên cơ bạch kim")) direct += "CHAR.LUC_TRAM.EQUIPMENT"')
engine = engine.replace('if (hasAny(actionText, "field mednet", "field galley")) direct += "CHAR.IRIS.SUPPORT"', '')
engine = engine.replace('if (id.startsWith("CHAR.IRIS.") && "iris" !in presentActors) return@forEach',
                        'if (id.startsWith("CHAR.LUC_TRAM.") && "luc_tram" !in presentActors) return@forEach')
engine = engine.replace('if (id.contains("iris")) presentActors += "iris"', 'if (id.contains("luc_tram") || id.contains("lục trầm") || id.contains("luc tram")) presentActors += "luc_tram"')
engine = engine.replace('"communication", "exploration", "iris", "syvial", "reunionPath",',
                        '"communication", "exploration", "luc_tram", "syvial", "reunionPath",')
engine = "\n".join(line.rstrip() for line in engine.splitlines()) + "\n"\nKNOWLEDGE_ENGINE.write_text(engine, encoding="utf-8")

validator = KNOWLEDGE_VALIDATOR.read_text(encoding="utf-8")
validator = re.sub(
    r'''    if \(mentionsAny\(reply, "argus nhìn xuyên tường".*?\n    \}\n    if \(mentionsAny\(reply, "thousandfold khiến cơ thể".*?\n    \}\n''',
    '''    if (mentionsAny(reply, "lục trầm đọc suy nghĩ", "thiên kiếm linh tâm đọc tâm trí", "lục trầm biết ký ức")) {
      issue("ability_overreach", "Thiên Kiếm Linh Tâm bị biến thành mind-reading", "Lục Trầm Codex giới hạn năng lực ở quỹ đạo/cân bằng/linh lực và dữ kiện trực tiếp.")
    }
    if (mentionsAny(reply, "tịch quang là súng", "kiếm khải có missile", "kiếm khải có drone", "lục trầm có hud")) {
      issue("ability_overreach", "Lục Trầm cultivation artifacts bị biến thành mecha tech", "Visual Lock R02 cấm hidden gun/missile/HUD/drone/energy-wing cho Lục Trầm.")
    }
''',
    validator,
    count=1,
    flags=re.S,
)
validator = validator.replace('"omnivault cất iris", "omnivault cất syvial", "omnivault cất người", "omnivault chứa người", "omnivault scan iris", "omnivault scan syvial"',
                              '"omnivault cất lục trầm", "omnivault cất syvial", "omnivault cất người", "omnivault chứa người", "omnivault scan lục trầm", "omnivault scan syvial"')
KNOWLEDGE_VALIDATOR.write_text(validator, encoding="utf-8")


# ---------------------------------------------------------------------------
# 6) Lightweight actor aliases + generated regression updates.
# ---------------------------------------------------------------------------
if FACADE.exists():
    facade = FACADE.read_text(encoding="utf-8")
    facade = facade.replace('"iris" to "iris"', '"lục trầm" to LUC_TRAM_ID, "luc tram" to LUC_TRAM_ID, "luc_tram" to LUC_TRAM_ID')
    FACADE.write_text(facade, encoding="utf-8")

if INTENT.exists():
    intent = INTENT.read_text(encoding="utf-8")
    intent = intent.replace("kai|iris|syvial", "kai|lục trầm|luc tram|luc_tram|syvial")
    INTENT.write_text(intent, encoding="utf-8")

for test_path in (
    TESTS / "SpecialFollowerShortcutTest.kt",
    TESTS / "CompanionSkillCatalogTest.kt",
    TESTS / "PartyCombatActionsTest.kt",
    TESTS / "CombatRuntimeTest.kt",
    TESTS / "InventoryCapacityNewGameTest.kt",
):
    if not test_path.exists():
        continue
    text = test_path.read_text(encoding="utf-8")
    text = text.replace("IRIS_ID", "LUC_TRAM_ID")
    text = text.replace('"iris"', '"luc_tram"')
    text = text.replace("Iris", "Lục Trầm")
    text = text.replace("avatars/Iris_avatar.jpg", "avatars/luctram_avatar.png")
    text = text.replace("/iris123", "/luctram123")
    text = text.replace("ARGUS // Thousandfold Execution", "Thiên Kiếm Định Giới")
    text = text.replace("Twosome Time tự động kích hoạt", "Tịch Quang Phản Kiếm")
    text = text.replace('assertEquals(8, CompanionSkillCatalog.forCharacter(LUC_TRAM_ID).size)', 'assertEquals(7, CompanionSkillCatalog.forCharacter(LUC_TRAM_ID).size)')
    text = text.replace('setOf("iris", "syvial")', 'setOf("luc_tram", "syvial")')
    text = text.replace('seen += "iris"', 'seen += "luc_tram"')
    test_path.write_text(text, encoding="utf-8")

replacement_test = TESTS / "LucTramReplacementTest.kt"
replacement_test.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LucTramReplacementTest {
  @Test fun freshStateUsesLucTramAndNeverSeedsIris() {
    val state = SpecialFollowersCanon.ensure(GameState.initial())
    assertTrue(state.characters.containsKey(LUC_TRAM_ID))
    assertFalse(state.characters.containsKey("iris"))
    val lucTram = state.characters.getValue(LUC_TRAM_ID)
    assertEquals("Lục Trầm", lucTram.name)
    assertEquals("avatars/luctram_avatar.png", lucTram.avatarRef)
  }

  @Test fun luciaAndLucTramAreDistinctCharacters() {
    val state = LuciaCanon.ensure(SpecialFollowersCanon.ensure(GameState.initial()))
    assertTrue(state.characters.containsKey(LUC_TRAM_ID))
    assertTrue(state.characters.containsKey(LUCIA_ID))
    assertNotEquals(LUC_TRAM_ID, LUCIA_ID)
    assertNotEquals(state.characters.getValue(LUC_TRAM_ID).name, state.characters.getValue(LUCIA_ID).name)
  }

  @Test fun lucTramUsesCanonicalSwordKit() {
    val state = CharacterEquipmentSystem.seedFresh(SpecialFollowersCanon.ensure(GameState.initial()))
    val slots = state.equipment.getValue(LUC_TRAM_ID).slots.values.toSet()
    assertTrue(LUC_TRAM_TICH_QUANG_ID in slots)
    assertTrue(LUC_TRAM_KIEM_KHAI_ID in slots)
    val skills = CompanionSkillCatalog.forCharacter(LUC_TRAM_ID)
    assertTrue(skills.any { it.name == "Tịch Quang Hợp Kích" })
    assertTrue(skills.any { it.name == "Thiên Kiếm Định Giới" })
  }
}
''', encoding="utf-8")

combined = "\n".join([
    SPECIAL.read_text(encoding="utf-8"),
    STATS.read_text(encoding="utf-8"),
    EQUIPMENT.read_text(encoding="utf-8"),
    CATALOG.read_text(encoding="utf-8"),
    COMBAT.read_text(encoding="utf-8"),
    MAIN.read_text(encoding="utf-8"),
])
for required in (
    'const val LUC_TRAM_ID = "luc_tram"',
    'LUC_TRAM_AVATAR_REF = "avatars/luctram_avatar.png"',
    'name = "Lục Trầm"',
    'LUC_TRAM_TICH_QUANG_ID',
    'Tịch Quang Hợp Kích',
    'Thiên Kiếm Định Giới',
    'LUC_TRAM_LUCIA_SNAPSHOT_OVERLAYS_R01',
    'file:///android_asset/luctram_overlay.png',
    'file:///android_asset/lucia_overlay.png',
    'lucTramReunion',
):
    if required not in combined:
        raise RuntimeError("Lục Trầm final contract missing: " + required)

for forbidden in (
    'name = "Iris"',
    'avatars/Iris_avatar.jpg',
    'Iris thực hiện lệnh TẤN CÔNG',
    'ARGUS // Thousandfold Execution',
):
    if forbidden in combined:
        raise RuntimeError("Active Iris runtime survived final replacement: " + forbidden)

print("Lục Trầm finalizer applied: Iris runtime replaced, Lucia kept distinct, V2 canon/skill projection and both companion snapshot overlays installed.")
