from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
CATALOG = CORE / "CompanionSkillCatalog.kt"
COMBAT_TEST = TESTS / "CombatRuntimeTest.kt"
CATALOG_TEST = TESTS / "CompanionSkillCatalogTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# ---------------------------------------------------------------------------
# Kai R08 shotgun skill sync.
# Keep damage/status behavior intact, but remove the old handgun/revolver motion
# language and raise each independent AUTO proc by a fixed +5..+10 percentage
# points as requested. Guilty Crown keeps its deterministic 3-turn priority.
# ---------------------------------------------------------------------------
combat = COMBAT.read_text(encoding="utf-8")

for old, new, label in (
    (
        "  private const val KAI_LAST_REQUIEM_CHANCE_PERCENT = 30\n",
        "  private const val KAI_LAST_REQUIEM_CHANCE_PERCENT = 38\n",
        "Kai Last Requiem proc 30 -> 38",
    ),
    (
        "  private const val KAI_SILENT_LULLABY_CHANCE_PERCENT = 20\n",
        "  private const val KAI_SILENT_LULLABY_CHANCE_PERCENT = 27\n",
        "Kai Silent Lullaby proc 20 -> 27",
    ),
    (
        "  private const val KAI_SALVATION_CHANCE_PERCENT = 20\n",
        "  private const val KAI_SALVATION_CHANCE_PERCENT = 26\n",
        "Kai Salvation proc 20 -> 26",
    ),
    (
        "  private const val KAI_QUICK_STEP_CHANCE_PERCENT = 30\n",
        "  private const val KAI_QUICK_STEP_CHANCE_PERCENT = 35\n",
        "Kai Quick Step proc 30 -> 35",
    ),
):
    combat = replace_once(combat, old, new, label)

combat = replace_once(
    combat,
    '        log += "The Last Requiem tự động kích hoạt: 4 phát vào khớp vai, ${KAI_LAST_REQUIEM_DAMAGE_PERCENT}% DMG = -$damage HP; Bleeding ${KAI_LAST_REQUIEM_BLEED_TURNS} turn, ${KAI_LAST_REQUIEM_BLEED_MAX_HP_PERCENT}% Max HP/turn."\n',
    '        log += "The Last Requiem tự động kích hoạt: Kai ghì SRU-SG bằng hai tay và khai hỏa 4 shell quỷ lực theo nhịp giật kiểm soát, đặt chùm đạn cắt qua các điểm neo vận động ở vai; ${KAI_LAST_REQUIEM_DAMAGE_PERCENT}% DMG = -$damage HP; Bleeding ${KAI_LAST_REQUIEM_BLEED_TURNS} turn, ${KAI_LAST_REQUIEM_BLEED_MAX_HP_PERCENT}% Max HP/turn."\n',
    "Kai Last Requiem shotgun narration",
)
combat = replace_once(
    combat,
    '        log += "Silent Lullaby tự động kích hoạt: Kai bật lên cao, 4 viên ghim cùng điểm trên ngực, ${KAI_SILENT_LULLABY_DAMAGE_PERCENT}% DMG = -$damage HP; Stun 1 turn."\n',
    '        log += "Silent Lullaby tự động kích hoạt: Kai bật lên cao, hạ nòng SRU-SG và khai hỏa 4 shell quỷ lực theo nhịp giật kiểm soát vào cùng vùng trọng yếu trên ngực; ${KAI_SILENT_LULLABY_DAMAGE_PERCENT}% DMG = -$damage HP; Stun 1 turn."\n',
    "Kai Silent Lullaby shotgun narration",
)
combat = replace_once(
    combat,
    '        log += "Salvation tự động kích hoạt: Kai ném súng ra sau mục tiêu, dịch chuyển tức thời tới vị trí súng và bắn nhanh 2 phát, ${KAI_SALVATION_DAMAGE_PERCENT}% DMG = -$damage HP."\n',
    '        log += "Salvation tự động kích hoạt: Kai bứt tốc qua góc chết, ghì SRU-SG bằng hai tay ở cự ly gần và khai hỏa nhanh 2 shell quỷ lực, ${KAI_SALVATION_DAMAGE_PERCENT}% DMG = -$damage HP."\n',
    "Kai Salvation shotgun narration",
)
combat = replace_once(
    combat,
    '        log += "Quick Step tự động kích hoạt: dịch chuyển ngắn liên tục, +${KAI_QUICK_STEP_EVASION_BONUS_PERCENT}% Evasion trong ${KAI_QUICK_STEP_DURATION_TURNS} turn."\n',
    '        log += "Quick Step tự động kích hoạt: Kai đổi góc bằng các pha bứt tốc ngắn trong khi giữ SRU-SG ở tư thế sẵn bắn, +${KAI_QUICK_STEP_EVASION_BONUS_PERCENT}% Evasion trong ${KAI_QUICK_STEP_DURATION_TURNS} turn."\n',
    "Kai Quick Step shotgun-ready narration",
)


# ---------------------------------------------------------------------------
# Lucia: Too Young To Die.
# Base proc = 15% every combat turn while Lucia is ACTIVE/alive. Once her HP is
# below 50%, every full 3 percentage points lost below that threshold adds +5pp.
# Example: 49% -> 15%, 47% -> 20%, 44% -> 25%.
# ---------------------------------------------------------------------------
constants_anchor = "  private const val LUCIA_FULL_AUTO_INTERVAL_TURNS = 2\n"
constants = """  private const val LUCIA_TOO_YOUNG_TO_DIE_ROUNDS = 60
  private const val LUCIA_TOO_YOUNG_TO_DIE_BASE_CHANCE_PERCENT = 15
  private const val LUCIA_TOO_YOUNG_TO_DIE_LOW_HP_STEP_PERCENT = 3
  private const val LUCIA_TOO_YOUNG_TO_DIE_LOW_HP_BONUS_PERCENT = 5
"""
if "LUCIA_TOO_YOUNG_TO_DIE_ROUNDS" not in combat:
    combat = replace_once(combat, constants_anchor, constants_anchor + constants, "Lucia Too Young To Die constants")

helper_anchor = "  private fun activePartyCharacter(state: GameState, characterId: String): CharacterState? {\n"
helper = '''  internal fun luciaTooYoungToDieTriggerChancePercent(currentHp: Int, maxHp: Int): Int {
    val safeMaxHp = max(1, maxHp)
    val hp = currentHp.coerceIn(0, safeMaxHp)
    val hpPercent = (hp * 100) / safeMaxHp
    val percentLostBelowHalf = max(0, 50 - hpPercent)
    val lowHpSteps = percentLostBelowHalf / LUCIA_TOO_YOUNG_TO_DIE_LOW_HP_STEP_PERCENT
    return min(
      100,
      LUCIA_TOO_YOUNG_TO_DIE_BASE_CHANCE_PERCENT +
        lowHpSteps * LUCIA_TOO_YOUNG_TO_DIE_LOW_HP_BONUS_PERCENT
    )
  }

'''
if "luciaTooYoungToDieTriggerChancePercent" not in combat:
    combat = replace_once(combat, helper_anchor, helper + helper_anchor, "Lucia Too Young To Die trigger helper")

burst_marker = "    // LUCIA_FULL_AUTO_BURST_V1:"
burst_pos = combat.find(burst_marker)
if burst_pos < 0:
    raise RuntimeError("Lucia Too Young To Die: existing Lucia full-auto layer missing")
response_pos = combat.find("    // Enemy response. Diệp Minh uses percentage damage; all other Entity behavior remains unchanged.\n", burst_pos)
if response_pos < 0:
    raise RuntimeError("Lucia Too Young To Die: finalized enemy response anchor missing")
death_pos = combat.find("    if (c.entityHp <= 0) {\n", burst_pos, response_pos)
if death_pos < 0:
    raise RuntimeError("Lucia Too Young To Die: pre-response death gate missing")

too_young_block = r'''    // LUCIA_TOO_YOUNG_TO_DIE_V1: independent AUTO check every combat turn.
    // The 60-round magazine resolves as one skill event and therefore uses one shared Entity
    // Evasion gate, matching Lucia's existing full-auto resolution contract.
    val luciaTooYoungCharacter = activePartyCharacter(resolvedState, LUCIA_ID)
    if (luciaTooYoungCharacter != null && c.entityHp > 0) {
      val luciaTooYoungMaxHp = CharacterStatEngine.effective(resolvedState, LUCIA_ID).maxHp
      val luciaTooYoungCurrentHp = luciaTooYoungCharacter.vitalState.currentHp.coerceIn(0, luciaTooYoungMaxHp)
      val luciaTooYoungChance = luciaTooYoungToDieTriggerChancePercent(luciaTooYoungCurrentHp, luciaTooYoungMaxHp)
      val luciaTooYoungProc = roll(c.copy(eventCounter = c.eventCounter + 641), 100)
      if (luciaTooYoungProc < luciaTooYoungChance) {
        val luciaTooYoungEvasionRoll = roll(c.copy(eventCounter = c.eventCounter + 647), 100)
        if (luciaTooYoungEvasionRoll >= ENTITY_EVASION_PERCENT) {
          val luciaBaseDamage = max(
            LUCIA_M4A1_COMBAT_DAMAGE,
            CharacterStatEngine.weaponDamage(resolvedState, LUCIA_ID)
          )
          val luciaRawPerBullet = (luciaBaseDamage * 105 + 99) / 100
          val luciaPerBulletDamage = companionSkillDamage(luciaBaseDamage, 105, profile.armor)
          val luciaRawBurstDamage = LUCIA_TOO_YOUNG_TO_DIE_ROUNDS * luciaPerBulletDamage
          val luciaResolvedBurstDamage = if (c.entityKey == SCP_173_KEY) {
            scp173DirectDamage(luciaRawBurstDamage, scp173ObservedNow)
          } else {
            luciaRawBurstDamage
          }
          val luciaBurstDamage = min(c.entityHp, luciaResolvedBurstDamage)
          val luciaBurstHp = max(0, c.entityHp - luciaBurstDamage)
          c = c.copy(
            entityHp = luciaBurstHp,
            entityCondition = condition(luciaBurstHp, c.entityMaxHp),
            noise = min(100, c.noise + 60)
          )
          log += "Lucia \"Lục\" tự kích hoạt Too Young To Die: " +
            "$LUCIA_TOO_YOUNG_TO_DIE_ROUNDS viên liên tục, mỗi viên Base DMG +5% = $luciaRawPerBullet trước Armor/buff ngoài kỹ năng; " +
            "tỷ lệ proc hiện tại $luciaTooYoungChance%; tổng -$luciaBurstDamage HP (${c.entityHp}/${c.entityMaxHp})."
        } else {
          log += "${c.entityName} né Too Young To Die của Lucia (${ENTITY_EVASION_PERCENT}% Evasion)."
        }
      }
    }

'''
if "LUCIA_TOO_YOUNG_TO_DIE_V1" not in combat:
    combat = combat[:death_pos] + too_young_block + combat[death_pos:]

for marker in (
    "private const val KAI_LAST_REQUIEM_CHANCE_PERCENT = 38",
    "private const val KAI_SILENT_LULLABY_CHANCE_PERCENT = 27",
    "private const val KAI_SALVATION_CHANCE_PERCENT = 26",
    "private const val KAI_QUICK_STEP_CHANCE_PERCENT = 35",
    "SRU-SG bằng hai tay",
    "LUCIA_TOO_YOUNG_TO_DIE_ROUNDS = 60",
    "LUCIA_TOO_YOUNG_TO_DIE_BASE_CHANCE_PERCENT = 15",
    "luciaTooYoungToDieTriggerChancePercent",
    "LUCIA_TOO_YOUNG_TO_DIE_V1",
    "LUCIA_TOO_YOUNG_TO_DIE_ROUNDS * luciaPerBulletDamage",
    "companionSkillDamage(luciaBaseDamage, 105, profile.armor)",
):
    if marker not in combat:
        raise RuntimeError("Kai/Lucia skill runtime contract missing: " + marker)
COMBAT.write_text(combat, encoding="utf-8")


# ---------------------------------------------------------------------------
# Character Detail skill catalog. Iris and Syvial are deliberately untouched.
# ---------------------------------------------------------------------------
catalog = CATALOG.read_text(encoding="utf-8")
for old, new, label in (
    (
        '    s("The Last Requiem", "AUTO", "30% mỗi turn hợp lệ", "4 phát vào khớp vai, 170% Weapon DMG; Bleeding 3 turn x 5% Max HP."),\n',
        '    s("The Last Requiem", "AUTO", "38% mỗi turn hợp lệ", "SRU-SG: 4 shell quỷ lực theo nhịp giật kiểm soát, đặt chùm đạn vào điểm neo vận động ở vai; 170% Weapon DMG; Bleeding 3 turn x 5% Max HP."),\n',
        "Kai Last Requiem catalog",
    ),
    (
        '    s("Silent Lullaby", "AUTO", "20% mỗi turn hợp lệ", "4 phát cùng điểm ngực, 130% Weapon DMG; Stun 1 turn."),\n',
        '    s("Silent Lullaby", "AUTO", "27% mỗi turn hợp lệ", "SRU-SG: 4 shell quỷ lực vào cùng vùng trọng yếu trên ngực, kiểm soát độ giật và độ tản; 130% Weapon DMG; Stun 1 turn."),\n',
        "Kai Silent Lullaby catalog",
    ),
    (
        '    s("Salvation", "AUTO", "20% mỗi turn hợp lệ", "Dịch chuyển ngắn theo vị trí súng, 2 phát, 147% Weapon DMG."),\n',
        '    s("Salvation", "AUTO", "26% mỗi turn hợp lệ", "Bứt tốc qua góc chết, ghì SRU-SG bằng hai tay ở cự ly gần và khai hỏa 2 shell quỷ lực; 147% Weapon DMG."),\n',
        "Kai Salvation catalog",
    ),
    (
        '    s("Quick Step", "AUTO", "30% mỗi turn hợp lệ", "+50 điểm % Evasion trong 3 turn đối với phản công thường."),\n',
        '    s("Quick Step", "AUTO", "35% mỗi turn hợp lệ", "Đổi góc bằng các pha bứt tốc ngắn trong khi giữ SRU-SG sẵn bắn; +50 điểm % Evasion trong 3 turn đối với phản công thường."),\n',
        "Kai Quick Step catalog",
    ),
):
    catalog = replace_once(catalog, old, new, label)

if 's("Too Young To Die", "AUTO"' not in catalog:
    lines = catalog.splitlines()
    full_auto_index = next((i for i, line in enumerate(lines) if 's("M4A1 Full Auto Burst", "AUTO"' in line), -1)
    if full_auto_index < 0:
        raise RuntimeError("Too Young To Die catalog: M4A1 Full Auto Burst anchor missing")
    lines[full_auto_index] = lines[full_auto_index].rstrip().rstrip(",") + ","
    lines.insert(
        full_auto_index + 1,
        '    s("Too Young To Die", "AUTO", "15% mỗi combat turn; khi HP < 50%, +5 điểm % mỗi 3 điểm % HP mất thêm dưới ngưỡng 50%", "Xả hết băng 60 viên; mỗi viên gây Base DMG +5% trước Armor và các buff ngoài kỹ năng; toàn loạt chịu một Entity Evasion gate.", "Ví dụ: 49% HP = 15%, 47% = 20%, 44% = 25%; tỷ lệ tối đa 100%.")',
    )
    catalog = "\n".join(lines) + ("\n" if catalog.endswith("\n") else "")

for marker in (
    '"38% mỗi turn hợp lệ"',
    '"27% mỗi turn hợp lệ"',
    '"26% mỗi turn hợp lệ"',
    '"35% mỗi turn hợp lệ"',
    's("Too Young To Die", "AUTO"',
    '"Xả hết băng 60 viên; mỗi viên gây Base DMG +5%',
):
    if marker not in catalog:
        raise RuntimeError("Kai/Lucia catalog contract missing: " + marker)
CATALOG.write_text(catalog, encoding="utf-8")


# ---------------------------------------------------------------------------
# Focused regression tests for the new probability and projection contracts.
# ---------------------------------------------------------------------------
combat_test = COMBAT_TEST.read_text(encoding="utf-8")
combat_regression = r'''
  @org.junit.Test fun luciaTooYoungToDieChanceScalesOnlyAfterThreePercentLostBelowHalfHp() {
    org.junit.Assert.assertEquals(15, CombatRuntime.luciaTooYoungToDieTriggerChancePercent(50, 100))
    org.junit.Assert.assertEquals(15, CombatRuntime.luciaTooYoungToDieTriggerChancePercent(49, 100))
    org.junit.Assert.assertEquals(15, CombatRuntime.luciaTooYoungToDieTriggerChancePercent(48, 100))
    org.junit.Assert.assertEquals(20, CombatRuntime.luciaTooYoungToDieTriggerChancePercent(47, 100))
    org.junit.Assert.assertEquals(25, CombatRuntime.luciaTooYoungToDieTriggerChancePercent(44, 100))
    org.junit.Assert.assertEquals(40, CombatRuntime.luciaTooYoungToDieTriggerChancePercent(35, 100))
  }

  @org.junit.Test fun luciaTooYoungToDieCanProcOnAnyCombatTurn() {
    var sawSkill = false
    for (seed in 1L..500L) {
      var state = LuciaCanon.ensure(GameState.initial())
      state = state.copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID)))
      state = CombatRuntime.start(state, "scp_173")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to "0",
        "combat.seed" to seed.toString(),
        "passive.devilTrigger.kai.cooldownTurns" to "5"
      ))
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ vị trí và quan sát")
      if (result.reply.contains("Too Young To Die")) {
        sawSkill = true
        break
      }
    }
    org.junit.Assert.assertTrue("Lucia 15% Too Young To Die proc should be reachable on a non-ATTACK combat turn", sawSkill)
  }
'''
if "luciaTooYoungToDieChanceScalesOnlyAfterThreePercentLostBelowHalfHp" not in combat_test:
    close = combat_test.rfind("\n}")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest class closing brace missing")
    combat_test = combat_test[:close] + "\n" + combat_regression.rstrip() + combat_test[close:]
COMBAT_TEST.write_text(combat_test, encoding="utf-8")

catalog_test = CATALOG_TEST.read_text(encoding="utf-8")
catalog_regression = r'''
  @org.junit.Test fun kaiCatalogUsesShotgunLanguageAndRaisedProcRates() {
    val skills = CompanionSkillCatalog.forCharacter(KAI_ID).associateBy { it.name }
    org.junit.Assert.assertTrue(skills.getValue("The Last Requiem").trigger.contains("38%"))
    org.junit.Assert.assertTrue(skills.getValue("Silent Lullaby").trigger.contains("27%"))
    org.junit.Assert.assertTrue(skills.getValue("Salvation").trigger.contains("26%"))
    org.junit.Assert.assertTrue(skills.getValue("Quick Step").trigger.contains("35%"))
    for (name in listOf("The Last Requiem", "Silent Lullaby", "Salvation", "Quick Step")) {
      org.junit.Assert.assertTrue(name, skills.getValue(name).effect.contains("SRU-SG"))
    }
  }

  @org.junit.Test fun luciaCatalogProjectsTooYoungToDieContract() {
    val skill = CompanionSkillCatalog.forCharacter(LUCIA_ID).first { it.name == "Too Young To Die" }
    org.junit.Assert.assertEquals("AUTO", skill.kind)
    org.junit.Assert.assertTrue(skill.trigger.contains("15%"))
    org.junit.Assert.assertTrue(skill.trigger.contains("+5 điểm %"))
    org.junit.Assert.assertTrue(skill.trigger.contains("3 điểm %"))
    org.junit.Assert.assertTrue(skill.effect.contains("60 viên"))
    org.junit.Assert.assertTrue(skill.effect.contains("Base DMG +5%"))
  }
'''
if "kaiCatalogUsesShotgunLanguageAndRaisedProcRates" not in catalog_test:
    close = catalog_test.rfind("\n}")
    if close < 0:
        raise RuntimeError("CompanionSkillCatalogTest class closing brace missing")
    catalog_test = catalog_test[:close] + "\n" + catalog_regression.rstrip() + catalog_test[close:]
CATALOG_TEST.write_text(catalog_test, encoding="utf-8")

print(
    "Kai/Lucia skill update applied: Kai AUTO skills synchronized to SRU-SG with 38/27/26/35% proc rates; "
    "Lucia gains Too Young To Die (60 rounds, Base DMG +5% each, 15% base proc with low-HP scaling)."
)
