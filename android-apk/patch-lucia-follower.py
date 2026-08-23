from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

GAME_STATE = CORE / "GameState.kt"
STATS = CORE / "CharacterStats.kt"
SYSTEM = CORE / "CharacterEquipmentSystem.kt"
POLICY = CORE / "InventoryPolicy.kt"
LUCIA = CORE / "LuciaCanon.kt"
TEST = TESTS / "LuciaFollowerTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# ---------------------------------------------------------------------------
# Canon: Lucia is a human combat follower from Level 0. Her gifted-item
# backpack is intentionally separate from the ammunition she entered with.
# ---------------------------------------------------------------------------
LUCIA.write_text(r'''package com.rabpit.backroom.core

const val LUCIA_ID = "lucia"
const val LUCIA_M4A1_ID = "lucia:m4a1-custom"
const val LUCIA_KNIFE_ID = "lucia:combat-knife"
const val LUCIA_WATCH_ID = "lucia:military-watch"

object LuciaCanon {
  const val NAME = "Lucia \"Lục\""
  const val AGE = 19
  const val HOME_LEVEL = 0
  const val ENCOUNTER_CHANCE = "50%"
  const val AVATAR_REF = "avatars/lucia_avatar.jpg"

  val equipmentSlots: Map<String, String> = linkedMapOf(
    "weapon" to LUCIA_M4A1_ID,
    "blade" to LUCIA_KNIFE_ID,
    "wrist" to LUCIA_WATCH_ID
  )

  fun character(existing: CharacterState? = null): CharacterState {
    val base = existing ?: CharacterState(
      id = LUCIA_ID,
      name = NAME,
      physiology = PhysiologyState.freshRunBaseline(),
      statProfile = CharacterStatProfiles.forId(LUCIA_ID),
      vitalState = CharacterVitalState(currentHp = 100)
    )
    return base.copy(
      id = LUCIA_ID,
      name = NAME,
      avatarRef = AVATAR_REF,
      inventoryId = LUCIA_ID,
      equipmentId = LUCIA_ID,
      healthState = base.healthState ?: "HEALTHY",
      statProfile = CharacterStatProfiles.forId(LUCIA_ID),
      vitalState = base.vitalState.copy(currentHp = base.vitalState.currentHp.coerceIn(0, 100)),
      metadata = base.metadata + mapOf(
        "age" to AGE.toString(),
        "species" to "human",
        "gender" to "female",
        "militaryRank" to "Binh nhì",
        "militaryRole" to "Tư lệnh cấp tiểu đội trong biên chế đặc nhiệm",
        "npcType" to "follower",
        "entity" to "false",
        "combatant" to "true",
        "joinEligible" to "true",
        "followsPlayer" to "true",
        "homeLevel" to HOME_LEVEL.toString(),
        "encounterLevels" to "0",
        "encounterChance" to ENCOUNTER_CHANCE,
        "encounterAction" to "EXPLORE",
        "inventoryProfile" to "lucia_gift_inventory",
        "startingLoadedAmmo" to "60",
        "startingReserveAmmo" to "90",
        "startingTotalAmmo" to "150",
        "ammoNote" to "60 viên trong M4A1 + 3 băng dự phòng 30 viên; không tính vào 3 loại vật phẩm quà tặng",
        "tacticalDoctrine" to "Kỷ luật hỏa lực; chỉ nổ súng khi bắt buộc phải đột phá hoặc xác định chính xác cổng ra",
        "level0Method" to "Điểm tựa bức tường; mở rộng xoắn ốc; đánh dấu đường bằng phấn; laser quét mặt sàn",
        "level0EntityKnowledge" to "Tiếng động giờ thứ 4 chỉ bị Lucia nghi là Hound; Level 0 không xác nhận Hound cư trú",
        "goal" to "Tìm lối sang Level 1"
      )
    )
  }

  fun inventory(existing: InventoryState? = null): InventoryState =
    InventoryState(LUCIA_ID, existing?.items.orEmpty())

  fun equipment(existing: EquipmentState? = null): EquipmentState =
    EquipmentState(LUCIA_ID, existing?.slots.orEmpty() + equipmentSlots)

  fun ensure(state: GameState): GameState {
    val character = character(state.characters[LUCIA_ID])
    return state.copy(
      characters = state.characters + (LUCIA_ID to character),
      inventories = state.inventories + (LUCIA_ID to inventory(state.inventories[LUCIA_ID])),
      equipment = state.equipment + (LUCIA_ID to equipment(state.equipment[LUCIA_ID]))
    )
  }
}
''', encoding="utf-8")


# ---------------------------------------------------------------------------
# Stats: HP remains on the game's 100-point scale. Every requested numeric
# attribute is capped at 10.
# ---------------------------------------------------------------------------
stats = STATS.read_text(encoding="utf-8")
if 'private val lucia = CharacterStatProfile(' not in stats:
    anchor = '''  private val anNhien = CharacterStatProfile(
'''
    lucia_profile = '''  private val lucia = CharacterStatProfile(
    baseMaxHp = 100,
    energy = EnergyProfile.notApplicable(),
    regen = HpRegenRule(),
    str = 7,
    df = 7,
    agi = 8,
    crit = 7,
    combatRole = "TACTICAL RIFLEWOMAN / SQUAD LEADER / FOLLOWER",
    statSource = StatSource.GAMEPLAY_NORMALIZED
  )

'''
    if anchor not in stats:
        raise RuntimeError("Lucia stat insertion anchor missing")
    stats = stats.replace(anchor, lucia_profile + anchor, 1)

stats = replace_once(
    stats,
    '''    "an-nhien", "an_nhien", "annhien" -> anNhien
    else -> fallback
''',
    '''    "an-nhien", "an_nhien", "annhien" -> anNhien
    "lucia", "luc", "lucia-luc" -> lucia
    else -> fallback
''',
    "Lucia stat resolver",
)
STATS.write_text(stats, encoding="utf-8")


# ---------------------------------------------------------------------------
# Equipment: exactly three equipped slots for Lucia. BLADE and WRIST are added
# as first-class slots rather than pretending a knife is armor or a watch is a ring.
# ---------------------------------------------------------------------------
system = SYSTEM.read_text(encoding="utf-8")
system = replace_once(
    system,
    '''  RING("ring"), SPECIAL("special"), OUTFIT("outfit"), FOOTWEAR("footwear");
''',
    '''  RING("ring"), SPECIAL("special"), BLADE("blade"), WRIST("wrist"), OUTFIT("outfit"), FOOTWEAR("footwear");
''',
    "Lucia equipment slot enum",
)
system = replace_once(
    system,
    '''        "special" -> SPECIAL
        "outfit" -> OUTFIT
''',
    '''        "special" -> SPECIAL
        "blade", "knife" -> BLADE
        "wrist", "watch" -> WRIST
        "outfit" -> OUTFIT
''',
    "Lucia equipment slot resolver",
)

if 'id = LUCIA_M4A1_ID' not in system:
    insert_anchor = '''    EquipmentDefinition(
      id = AN_NHIEN_OUTFIT_ID'''
    lucia_defs = '''    EquipmentDefinition(
      id = LUCIA_M4A1_ID, name = "M4A1 cá nhân hóa", type = "ASSAULT RIFLE", primarySlot = EquipmentSlot.WEAPON,
      weapon = WeaponGameplayStats(26, "60 / 90 reserve", 800, listOf("Semi", "Burst", "Auto")),
      abilities = listOf(
        ability("Green Laser 5mW", "Laser xanh chỉnh điểm danh 5mW hỗ trợ chỉ thị và quét bề mặt ở cự ly gần.", "Không biến laser thành cảm biến siêu nhiên."),
        ability("60-Round Main Magazine", "Băng chính mang 60 viên khi Lucia bắt đầu Level 0."),
        ability("Fire Discipline", "Lucia ưu tiên điểm xạ và tiết kiệm đạn trong môi trường chưa xác định.")
      ),
      restrictions = listOf("Đạn vật lý hữu hạn: 60 viên nạp + 90 viên dự phòng lúc bắt đầu."),
      canonRef = "LUCIA-LUC-FOLLOWER-20260823"
    ),
    EquipmentDefinition(
      id = LUCIA_KNIFE_ID, name = "Dao găm chiến đấu", type = "COMBAT KNIFE", primarySlot = EquipmentSlot.BLADE,
      weapon = WeaponGameplayStats(16),
      canonRef = "LUCIA-LUC-FOLLOWER-20260823"
    ),
    EquipmentDefinition(
      id = LUCIA_WATCH_ID, name = "Đồng hồ định vị quân sự", type = "MILITARY WATCH", primarySlot = EquipmentSlot.WRIST,
      abilities = listOf(
        ability("Local Time Reference", "Giữ mốc thời gian cục bộ để Lucia ghi chép hành trình."),
        ability("Navigation Hardware", "Phần cứng định vị vẫn tồn tại nhưng đã mất tín hiệu vệ tinh trong Backrooms.", "Không cung cấp GPS hoặc la bàn tuyệt đối ở Level 0.")
      ),
      canonRef = "LUCIA-LUC-FOLLOWER-20260823"
    ),
'''
    if insert_anchor not in system:
        raise RuntimeError("Lucia equipment definition anchor missing")
    system = system.replace(insert_anchor, lucia_defs + insert_anchor, 1)

system = replace_once(
    system,
    '''    AN_NHIEN_ID -> linkedMapOf(EquipmentSlot.OUTFIT to AN_NHIEN_OUTFIT_ID, EquipmentSlot.FOOTWEAR to AN_NHIEN_FOOTWEAR_ID)
    else -> emptyMap()
''',
    '''    AN_NHIEN_ID -> linkedMapOf(EquipmentSlot.OUTFIT to AN_NHIEN_OUTFIT_ID, EquipmentSlot.FOOTWEAR to AN_NHIEN_FOOTWEAR_ID)
    LUCIA_ID -> linkedMapOf(
      EquipmentSlot.WEAPON to LUCIA_M4A1_ID,
      EquipmentSlot.BLADE to LUCIA_KNIFE_ID,
      EquipmentSlot.WRIST to LUCIA_WATCH_ID
    )
    else -> emptyMap()
''',
    "Lucia starting loadout",
)

# Lucia is guaranteed to exist after every normalization/load without forcing her into Party.
if 'val input = LuciaCanon.ensure(source)' not in system:
    system = replace_once(
        system,
        '''  private fun normalizeInternal(input: GameState, seedStarting: Boolean): GameState {
    val inventories = input.inventories.toMutableMap()
''',
        '''  private fun normalizeInternal(source: GameState, seedStarting: Boolean): GameState {
    val input = LuciaCanon.ensure(source)
    val inventories = input.inventories.toMutableMap()
''',
        "Lucia save normalization",
    )
SYSTEM.write_text(system, encoding="utf-8")


# ---------------------------------------------------------------------------
# Inventory: exactly 3 carried/gift item types, maximum 100 units per type.
# Equipped items remain owned but consume zero backpack slots under the existing
# InventoryCapacityPolicy.
# ---------------------------------------------------------------------------
policy = POLICY.read_text(encoding="utf-8")
if 'val LUCIA = InventoryProfile(maxTypes = 3, maxPerType = 100)' not in policy:
    policy = replace_once(
        policy,
        '''  val SPECIAL_COMPANION = InventoryProfile(maxTypes = 6, maxPerType = 20)
  val NORMAL = InventoryProfile(maxTypes = 2, maxPerType = 2)
''',
        '''  val SPECIAL_COMPANION = InventoryProfile(maxTypes = 6, maxPerType = 20)
  val LUCIA = InventoryProfile(maxTypes = 3, maxPerType = 100)
  val NORMAL = InventoryProfile(maxTypes = 2, maxPerType = 2)
''',
        "Lucia inventory profile",
    )
policy = replace_once(
    policy,
    '''    if (characterId == KAI_ID) return KAI
    val character = state.characters[characterId]
''',
    '''    if (characterId == KAI_ID) return KAI
    if (characterId == LUCIA_ID) return LUCIA
    val character = state.characters[characterId]
''',
    "Lucia inventory resolver",
)
POLICY.write_text(policy, encoding="utf-8")


# ---------------------------------------------------------------------------
# Android runtime: 50% encounter only while EXPLORE is active in Level 0.
# Once encountered, Lucia is persisted and the roll becomes ineligible.
# ---------------------------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")
if 'rolls.put("luciaEncounter"' not in main:
    roll_anchor = '    rolls.put("syvialReunion", thresholdRoll("syvialReunion", 10000, 25, physical && reunionEligibleAndroid(state, "syvial"), " follower encounter"));\n'
    roll_line = roll_anchor + '    rolls.put("luciaEncounter", thresholdRoll("luciaEncounter", 10000, 5000, exploreAction && level == 0 && !flagSpawned(state, "lucia"), " Level 0 Lucia follower encounter"));\n'
    if roll_anchor not in main:
        raise RuntimeError("Lucia encounter roll anchor missing")
    main = main.replace(roll_anchor, roll_line, 1)

if 'ensureSpecialFollowerInLegacyParty(state, "lucia", "Lucia \\"Lục\\"", false)' not in main:
    tail_anchor = '''      flags.put("syvial", syvial);
    }

    state.put("flags", flags);
'''
    lucia_commit = '''      flags.put("syvial", syvial);
    }

    if (rollSuccess(rolls, "luciaEncounter")) {
      JSONObject lucia = flags.optJSONObject("lucia");
      if (lucia == null) lucia = new JSONObject();
      boolean luciaJoined = ensureSpecialFollowerInLegacyParty(state, "lucia", "Lucia \\"Lục\\"", false);
      lucia.put("exists", true)
        .put("encountered", true)
        .put("present", true)
        .put("spawned", true)
        .put("follower", true)
        .put("reunionEligible", false)
        .put("continuity", "RECRUITED_LEVEL_0")
        .put("levelEncountered", 0)
        .put("joinPending", !luciaJoined);
      flags.put("lucia", lucia);
    }

    state.put("flags", flags);
'''
    if tail_anchor not in main:
        raise RuntimeError("Lucia encounter commit anchor missing")
    main = main.replace(tail_anchor, lucia_commit, 1)

if 'LUCIA FOLLOWER HARD LOCK:' not in main:
    return_anchor = '    return actionDirective + "\\nACTION_RUNTIME: " + actionRuntimeContext + "\\n" +\n'
    return_new = '    return actionDirective + "\\nLUCIA FOLLOWER HARD LOCK: Lucia \\\"Lục\\\", nữ 19 tuổi, con người, binh nhì và chỉ huy cấp tiểu đội đặc nhiệm. luciaEncounter chỉ roll khi EXPLORE ở Level 0, xác suất 50%, và chỉ success=true mới cho cô xuất hiện. Sau lần gặp đầu, không roll lại. Nếu Party còn chỗ cô gia nhập follower; nếu đầy thì giữ present + joinPending, không đuổi thành viên khác. HP nền 100; STR 7, DF 7, AGI 8, CRIT 7. Trang bị đúng 3 slot: M4A1 cá nhân hóa với laser xanh 5mW, dao găm chiến đấu, đồng hồ định vị quân sự mất tín hiệu vệ tinh. Đạn khởi đầu 150 viên gồm 60 đang nạp và 90 dự phòng; đây là nguồn đạn riêng, không chiếm 3 loại vật phẩm quà tặng. Inventory quà tặng tối đa 3 loại, tối đa 100 mỗi loại. Ở Level 0, Lucia chỉ nghi ngờ tiếng động giờ thứ 4 là Hound; không được xác nhận Hound cư trú ở Level 0. Không tự thêm năng lực siêu nhiên hoặc lore.\\nACTION_RUNTIME: " + actionRuntimeContext + "\\n" +\n'
    if return_anchor not in main:
        raise RuntimeError("Lucia GM prompt anchor missing")
    main = main.replace(return_anchor, return_new, 1)

MAIN.write_text(main, encoding="utf-8")


# ---------------------------------------------------------------------------
# Regression tests: stats cap, HP scale, three equipment slots, and 3x100 gift
# inventory policy. Runtime roll contract is verified textually below and in CI.
# ---------------------------------------------------------------------------
TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class LuciaFollowerTest {
  @Test fun luciaUsesHundredHpAndAllRequestedStatsAreAtMostTen() {
    val state = GameState.initial()
    val lucia = state.characters.getValue(LUCIA_ID)
    val profile = lucia.statProfile
    assertEquals(100, profile.baseMaxHp)
    assertEquals(100, lucia.vitalState.currentHp)
    assertTrue(profile.str in 0..10)
    assertTrue(profile.df in 0..10)
    assertTrue(profile.agi in 0..10)
    assertTrue(profile.crit in 0..10)
    assertEquals(listOf(7, 7, 8, 7), listOf(profile.str, profile.df, profile.agi, profile.crit))
  }

  @Test fun luciaHasExactlyThreeCanonicalEquipmentSlots() {
    val state = GameState.initial()
    val slots = state.equipment.getValue(LUCIA_ID).slots
    assertEquals(3, slots.size)
    assertEquals(LUCIA_M4A1_ID, slots["weapon"])
    assertEquals(LUCIA_KNIFE_ID, slots["blade"])
    assertEquals(LUCIA_WATCH_ID, slots["wrist"])
    slots.values.forEach { id -> assertTrue(state.inventories.getValue(LUCIA_ID).items.containsKey(id)) }
    assertEquals(0, InventoryCapacityPolicy.usedSlots(state, LUCIA_ID))
  }

  @Test fun luciaGiftInventoryAllowsThreeTypesAndOneHundredEach() {
    val state = GameState.initial()
    val profile = InventoryPolicy.profileFor(state, LUCIA_ID)
    assertEquals(3, profile.maxTypes)
    assertEquals(100, profile.maxPerType)

    val three = InventoryState(LUCIA_ID, mapOf(
      "a" to ItemStack("a", "A", 100),
      "b" to ItemStack("b", "B", 1),
      "c" to ItemStack("c", "C", 1)
    ))
    assertEquals("inventory_slot_limit", InventoryPolicy.validateAddition(state, LUCIA_ID, three, ItemStack("d", "D", 1), 1))

    val ninetyNine = InventoryState(LUCIA_ID, mapOf("a" to ItemStack("a", "A", 99)))
    assertNull(InventoryPolicy.validateAddition(state, LUCIA_ID, ninetyNine, ItemStack("a", "A", 1), 1))
    assertEquals("inventory_stack_limit", InventoryPolicy.validateAddition(state, LUCIA_ID, ninetyNine, ItemStack("a", "A", 2), 2))
  }

  @Test fun luciaStartsOutsidePartyAndKeepsCanonAmmoSeparateFromGiftSlots() {
    val state = GameState.initial()
    val lucia = state.characters.getValue(LUCIA_ID)
    assertFalse(LUCIA_ID in state.party.memberIds)
    assertEquals("50%", lucia.metadata["encounterChance"])
    assertEquals("0", lucia.metadata["encounterLevels"])
    assertEquals("EXPLORE", lucia.metadata["encounterAction"])
    assertEquals("60", lucia.metadata["startingLoadedAmmo"])
    assertEquals("90", lucia.metadata["startingReserveAmmo"])
    assertEquals("150", lucia.metadata["startingTotalAmmo"])
  }
}
''', encoding="utf-8")

combined = (
    LUCIA.read_text(encoding="utf-8") + "\n" + STATS.read_text(encoding="utf-8") + "\n" +
    SYSTEM.read_text(encoding="utf-8") + "\n" + POLICY.read_text(encoding="utf-8") + "\n" +
    MAIN.read_text(encoding="utf-8") + "\n" + TEST.read_text(encoding="utf-8")
)
for marker in (
    'const val LUCIA_ID = "lucia"',
    'baseMaxHp = 100',
    'str = 7', 'df = 7', 'agi = 8', 'crit = 7',
    'BLADE("blade")', 'WRIST("wrist")',
    'LUCIA_ID -> linkedMapOf(',
    'val LUCIA = InventoryProfile(maxTypes = 3, maxPerType = 100)',
    'thresholdRoll("luciaEncounter", 10000, 5000, exploreAction && level == 0 && !flagSpawned(state, "lucia")',
    'LUCIA FOLLOWER HARD LOCK:',
    'assertEquals(3, profile.maxTypes)', 'assertEquals(100, profile.maxPerType)',
):
    if marker not in combined:
        raise RuntimeError("Lucia follower contract missing: " + marker)

print("Lucia follower installed: Level 0 EXPLORE 50%, HP 100, stats <= 10, 3 equipment slots, 3x100 gift inventory.")
