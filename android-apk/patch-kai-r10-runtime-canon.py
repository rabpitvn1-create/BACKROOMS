from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"

GAME_STATE = CORE / "GameState.kt"
EQUIPMENT = CORE / "CharacterEquipmentSystem.kt"
COMBAT = CORE / "CombatRuntime.kt"
CATALOG = CORE / "CompanionSkillCatalog.kt"
KNOWLEDGE_DB = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
KNOWLEDGE_ENGINE = CORE / "knowledge/KnowledgeContextEngine.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
COMBAT_TEST = TESTS / "CombatRuntimeTest.kt"
SKILL_TEST = TESTS / "CompanionSkillCatalogTest.kt"
EQUIPMENT_TEST = TESTS / "SruEquipmentIntegrationTest.kt"

R10_WEAPON_NAME = "SRU Assault Rifle MK19"
R10_WEAPON_ID = "CHAR.KAI.SRU_AR_MK19"
R10_CODEX_BASE_GCO_ROUNDS = 24
R10_GAMEPLAY_AMMO_MULTIPLIER = 3
R10_GCO_ROUNDS = R10_CODEX_BASE_GCO_ROUNDS * R10_GAMEPLAY_AMMO_MULTIPLIER
R10_LAST_REQUIEM_ROUNDS = 4 * R10_GAMEPLAY_AMMO_MULTIPLIER
R10_SILENT_LULLABY_ROUNDS = 4 * R10_GAMEPLAY_AMMO_MULTIPLIER
R10_SALVATION_ROUNDS = 2 * R10_GAMEPLAY_AMMO_MULTIPLIER


def require_file(path: Path) -> None:
    if not path.is_file() or path.stat().st_size <= 0:
        raise RuntimeError(f"Kai R10 missing generated file: {path}")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Kai R10 {label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def replace_regex_once(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.M)
    if count != 1:
        raise RuntimeError(f"Kai R10 {label}: expected exactly one regex match, found {count}")
    return updated


for path in (
    GAME_STATE, EQUIPMENT, COMBAT, CATALOG, KNOWLEDGE_DB, KNOWLEDGE_ENGINE, MAIN,
    COMBAT_TEST, SKILL_TEST, EQUIPMENT_TEST,
):
    require_file(path)

# ---------------------------------------------------------------------------
# Persisted identity compatibility.
# Keep the existing kai:sru-sg item ID so old saves remain valid, but expose an
# explicit R10 alias and current visible name/recognition terms.
# ---------------------------------------------------------------------------
state = GAME_STATE.read_text(encoding="utf-8")
if "const val KAI_SRU_AR_MK19_ID = KAI_SRU_SG_ID" not in state:
    state = replace_once(
        state,
        'const val KAI_SRU_SG_ID = "kai:sru-sg"\n',
        'const val KAI_SRU_SG_ID = "kai:sru-sg"\nconst val KAI_SRU_AR_MK19_ID = KAI_SRU_SG_ID // R10 compatibility alias; persisted ID stays stable.\n',
        "MK19 compatibility ID",
    )
state = state.replace('const val WEAPON_NAME = "SRU-SG Shotgun"', f'const val WEAPON_NAME = "{R10_WEAPON_NAME}"')
state = state.replace(
    'key.contains("sru-sg") || key.contains("sru sg") ||',
    'key.contains("sru assault rifle mk19") || key.contains("sru-mk19") || key.contains("mk19") || key.contains("assault rifle") || key.contains("sru-sg") || key.contains("sru sg") ||',
)
GAME_STATE.write_text(state, encoding="utf-8")

# ---------------------------------------------------------------------------
# Current equipment projection. Damage remains 32 because R10 specifies weapon
# dimensions/ammunition/fire-rate, not a new normalized gameplay DMG value.
# rpmCapability stores the upper end of the locked theoretical 700-950 RPM band;
# the full band remains explicit in the visible technical-spec ability.
# ---------------------------------------------------------------------------
equipment = EQUIPMENT.read_text(encoding="utf-8")
weapon_pattern = re.compile(
    r'    EquipmentDefinition\(\n      id = KAI_WHITE_WRAITH_ID,.*?\n    \),',
    re.S,
)
weapon_replacement = r'''    EquipmentDefinition(
      id = KAI_WHITE_WRAITH_ID, name = "SRU Assault Rifle MK19", type = "ASSAULT RIFLE", primarySlot = EquipmentSlot.WEAPON,
      bonuses = EquipmentBonuses(crit = 8),
      weapon = WeaponGameplayStats(32, "30 viên 5.56×45 NATO / Sparda 5.56×45 ∞", 950, listOf("Semi", "Burst", "Full Auto")),
      abilities = listOf(
        ability("Dual Ammunition System", "Dùng đạn vật lý 5.56×45 mm NATO từ băng 30 viên hoặc đạn quỷ lực Sparda 5.56×45 mm hình thành trực tiếp từ Sparda Core.", "Đạn vật lý hữu hạn; đạn Sparda không tiêu hao băng vật lý."),
        ability("Assault Rifle Mastery", "Kai kiểm soát điểm ngắm, độ rơi, độ dẫn mục tiêu, bù giật, chuyển mục tiêu, phát đơn, loạt ngắn và full-auto ở cấp UR+."),
        ability("Technical Spec R10", "2,88 kg rỗng; khoảng 3,4 kg với băng 30 viên đầy; 700–950 viên/phút; nòng 368 mm (14.5 inch); dài 838 mm kéo báng / 756 mm thu báng; tầm hiệu quả khoảng 500–600 m."),
        ability("Core Self-Repair", "SRU Assault Rifle MK19 tự sửa chữa cấu trúc khi đang là trang bị của Kai.", "Sửa trang bị không phải hồi HP nhân vật."),
        ability("Guilty Crown Override", "Codex base dùng 24 viên Sparda 5.56×45 mm; gameplay R10 áp hệ số số đạn ×3 thành đúng 72 viên.", "Gameplay dùng 72 viên; không đổi Accuracy/evasion/time-stop contract.")
      ),
      canonRef = "KAI-EQP-SRU-AR-MK19-01"
    ),'''
equipment, count = weapon_pattern.subn(weapon_replacement, equipment, count=1)
if count != 1:
    raise RuntimeError(f"Kai R10 equipment definition: expected 1 current Kai weapon block, found {count}")
equipment = equipment.replace("SRU-SG", R10_WEAPON_NAME)
equipment = equipment.replace("Shotgun Mastery", "Assault Rifle Mastery")
EQUIPMENT.write_text(equipment, encoding="utf-8")

# ---------------------------------------------------------------------------
# Combat runtime.
# Three AUTO gun skills keep their existing total %DMG/status math; only their
# shot counts are tripled. Guilty Crown already computes total damage per shot,
# so 24 -> 72 naturally triples its total damage without changing per-shot DMG.
# ---------------------------------------------------------------------------
combat = COMBAT.read_text(encoding="utf-8")
combat = replace_once(
    combat,
    "  private const val KAI_GUILTY_CROWN_SHOTS = 24\n",
    f"  private const val KAI_GUILTY_CROWN_SHOTS = {R10_GCO_ROUNDS}\n",
    "Guilty Crown gameplay shot count",
)

round_constants_anchor = "  private const val KAI_LAST_REQUIEM_CHANCE_PERCENT = 38\n"
if "KAI_LAST_REQUIEM_ROUNDS" not in combat:
    combat = replace_once(
        combat,
        round_constants_anchor,
        round_constants_anchor
        + f"  private const val KAI_LAST_REQUIEM_ROUNDS = {R10_LAST_REQUIEM_ROUNDS}\n"
        + f"  private const val KAI_SILENT_LULLABY_ROUNDS = {R10_SILENT_LULLABY_ROUNDS}\n"
        + f"  private const val KAI_SALVATION_ROUNDS = {R10_SALVATION_ROUNDS}\n",
        "Kai R10 AUTO shot constants",
    )

combat = replace_regex_once(
    combat,
    r'^\s*log \+= "The Last Requiem tự động kích hoạt:.*$',
    '        log += "The Last Requiem tự động kích hoạt: $KAI_LAST_REQUIEM_ROUNDS viên đạn quỷ lực Sparda 5.56×45 mm từ SRU Assault Rifle MK19 theo loạt bắn kiểm soát vào các điểm neo vận động ở vai; ${KAI_LAST_REQUIEM_DAMAGE_PERCENT}% DMG = -$damage HP; Bleeding ${KAI_LAST_REQUIEM_BLEED_TURNS} turn, ${KAI_LAST_REQUIEM_BLEED_MAX_HP_PERCENT}% Max HP/turn."',
    "Last Requiem R10 narration",
)
combat = replace_regex_once(
    combat,
    r'^\s*log \+= "Silent Lullaby tự động kích hoạt:.*$',
    '        log += "Silent Lullaby tự động kích hoạt: $KAI_SILENT_LULLABY_ROUNDS viên đạn quỷ lực Sparda 5.56×45 mm từ SRU Assault Rifle MK19 hội tụ vào cùng vùng trọng yếu trên ngực; ${KAI_SILENT_LULLABY_DAMAGE_PERCENT}% DMG = -$damage HP; Stun 1 turn."',
    "Silent Lullaby R10 narration",
)
combat = replace_regex_once(
    combat,
    r'^\s*log \+= "Salvation tự động kích hoạt:.*$',
    '        log += "Salvation tự động kích hoạt: Kai bứt tốc qua góc chết, giữ SRU Assault Rifle MK19 ở tư thế kiểm soát và khai hỏa nhanh $KAI_SALVATION_ROUNDS viên đạn quỷ lực Sparda 5.56×45 mm; ${KAI_SALVATION_DAMAGE_PERCENT}% DMG = -$damage HP."',
    "Salvation R10 narration",
)
combat = combat.replace(
    'Kai đổi góc bằng các pha bứt tốc ngắn trong khi giữ SRU-SG ở tư thế sẵn bắn',
    'Kai đổi góc bằng các pha bứt tốc ngắn trong khi giữ SRU Assault Rifle MK19 ở tư thế sẵn bắn',
)
combat = combat.replace("SRU-SG", R10_WEAPON_NAME)
COMBAT.write_text(combat, encoding="utf-8")

# ---------------------------------------------------------------------------
# Player-facing skill sheet. The AUTO skill damage percentages stay unchanged;
# the explicit shot counts and weapon handling language move to R10 AR canon.
# ---------------------------------------------------------------------------
catalog = CATALOG.read_text(encoding="utf-8")
kai_start = catalog.index("  private val kai = listOf(")
lucia_start = catalog.index("  private val lucia = listOf(", kai_start)
kai_block = catalog[kai_start:lucia_start]
kai_block = kai_block.replace("SRU-SG", R10_WEAPON_NAME)
kai_block = kai_block.replace(
    "4 viên đạn quỷ lực theo nhịp giật kiểm soát, đặt chùm đạn vào điểm neo vận động ở vai",
    f"{R10_LAST_REQUIEM_ROUNDS} viên đạn quỷ lực Sparda 5.56×45 mm theo loạt bắn kiểm soát vào điểm neo vận động ở vai",
)
kai_block = kai_block.replace(
    "4 viên đạn quỷ lực vào cùng vùng trọng yếu trên ngực, kiểm soát độ giật và độ tản",
    f"{R10_SILENT_LULLABY_ROUNDS} viên đạn quỷ lực Sparda 5.56×45 mm vào cùng vùng trọng yếu trên ngực, kiểm soát độ giật và độ dẫn",
)
kai_block = kai_block.replace(
    "khai hỏa 2 viên đạn quỷ lực",
    f"khai hỏa {R10_SALVATION_ROUNDS} viên đạn quỷ lực Sparda 5.56×45 mm",
)
kai_block = kai_block.replace(
    "Đúng 24 phát x 10 HP, Độ chính xác 200%, bỏ qua Né tránh.",
    f"Gameplay R10: đúng {R10_GCO_ROUNDS} viên x 10 HP cơ sở; Codex base 24 viên, hệ số số đạn ×3; Độ chính xác 200%, bỏ qua Né tránh.",
)
catalog = catalog[:kai_start] + kai_block + catalog[lucia_start:]
CATALOG.write_text(catalog, encoding="utf-8")

# ---------------------------------------------------------------------------
# Writer/GM knowledge. R10 preserves the codex base value (24) while making the
# gameplay projection explicit (72), preventing model prose from correcting the
# game back to 24 during a committed combat skill event.
# ---------------------------------------------------------------------------
data = json.loads(KNOWLEDGE_DB.read_text(encoding="utf-8"))
records = data.get("records")
if not isinstance(records, list):
    raise RuntimeError("Kai R10 knowledge database records missing")
by_id = {record.get("id"): record for record in records}

weapon = by_id.get("CHAR.KAI.SRU_SG")
if weapon is None:
    weapon = by_id.get(R10_WEAPON_ID)
if weapon is None:
    raise RuntimeError("Kai R10 current weapon knowledge record missing")
weapon["id"] = R10_WEAPON_ID
weapon["text"] = (
    "SRU Assault Rifle MK19 is Kai's current signature firearm. R10 locks ordinary ammunition to 5.56×45 mm NATO "
    "from detachable 30-round magazines; empty mass 2.88 kg, about 3.4 kg with a full 30-round magazine; theoretical "
    "rate of fire 700–950 rounds/minute with full-auto; overall length 838 mm stock extended / 756 mm collapsed; "
    "barrel 368 mm (14.5 inch); effective range about 500–600 m. Sparda 5.56×45 mm demon-power rounds form directly "
    "from Sparda Core, do not consume the physical magazine, and are tens of times more physically destructive than "
    "ordinary rounds in current canon. Kai uses assault-rifle recoil control, target lead, transitions, semi/burst/full-auto "
    "discipline and close-quarters weapon retention. The weapon self-repairs while equipped."
)
weapon["source"]["anchor"] = "KAI-EQP-SRU-AR-MK19-01; KAI-COMBAT-01; R10"
weapon["tags"] = [
    "kai", "sru assault rifle mk19", "mk19", "assault rifle", "5.56x45",
    "30-round magazine", "sparda 5.56", "sru-sg legacy",
]
weapon["references"] = ["CHAR.KAI.SPARDA_CORE"]

sparda = by_id.get("CHAR.KAI.SPARDA_CORE")
if sparda is None:
    raise RuntimeError("Kai R10 Sparda Core knowledge record missing")
sparda["text"] = sparda.get("text", "").replace("SRU-SG", R10_WEAPON_NAME)
if R10_WEAPON_NAME not in sparda["text"]:
    sparda["text"] += f" It powers and self-repairs {R10_WEAPON_NAME} while equipped."

override = by_id.get("CHAR.KAI.GUILTY_CROWN_OVERRIDE")
if override is None:
    raise RuntimeError("Kai R10 Guilty Crown knowledge record missing")
override["text"] = (
    "Guilty Crown Override codex base remains exactly 24 Sparda 5.56×45 mm rounds fired through SRU Assault Rifle MK19 "
    "while external time is completely stopped and Kai is in Devil Trigger. GAMEPLAY R10 deliberately applies a ×3 shot-count "
    f"multiplier, so a committed in-game Guilty Crown event resolves exactly {R10_GCO_ROUNDS} rounds. Preserve the codex/base distinction: "
    "do not rewrite the source codex to 72, but narrate the actual gameplay event as 72 rounds. Accuracy/evasion/time-stop behavior "
    "remains governed by CombatRuntime."
)
override["source"]["anchor"] = "KAI-ULT-GCO-01; R10 gameplay multiplier"
override["references"] = ["CHAR.KAI.DEVIL_TRIGGER", R10_WEAPON_ID]

runtime_core = by_id.get("CHAR.KAI.RUNTIME_CORE")
if runtime_core is None:
    raise RuntimeError("Kai R10 runtime core knowledge record missing")
runtime_core["text"] = runtime_core.get("text", "").replace("R08 visual", "R09 visual")
runtime_core["text"] = runtime_core["text"].replace("SRU-SG", R10_WEAPON_NAME)
runtime_core["tags"] = list(dict.fromkeys(runtime_core.get("tags", []) + ["r09 visual", "r10 weapon"]))

KNOWLEDGE_DB.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

engine = KNOWLEDGE_ENGINE.read_text(encoding="utf-8")
old_route = '      if (hasAny(actionText, "sru-sg", "sru sg", "shotgun", "white wraith", "magnum")) direct += "CHAR.KAI.SRU_SG"'
new_route = '      if (hasAny(actionText, "sru assault rifle mk19", "sru-mk19", "mk19", "assault rifle", "sru-sg", "sru sg", "shotgun", "white wraith", "magnum")) direct += "CHAR.KAI.SRU_AR_MK19"'
if new_route not in engine:
    engine = replace_once(engine, old_route, new_route, "MK19 knowledge direct route")
engine = engine.replace('"CHAR.KAI.SRU_SG"', f'"{R10_WEAPON_ID}"')
KNOWLEDGE_ENGINE.write_text(engine, encoding="utf-8")

# Compact hard prompt still contains older R08 weapon wording after early patch layers.
main = MAIN.read_text(encoding="utf-8")
main = main.replace("KAI-SRU-R08-RUNTIME-20260830", "KAI-SRU-R10-RUNTIME-20260902")
main = main.replace("SRU-SG Shotgun", R10_WEAPON_NAME)
main = main.replace("SRU-SG", R10_WEAPON_NAME)
main = main.replace("Shotgun Mastery", "Assault Rifle Mastery")
MAIN.write_text(main, encoding="utf-8")

# ---------------------------------------------------------------------------
# Regression compatibility + new R10 assertions.
# Older generated tests encoded the 24-round gameplay projection. Update only
# Kai GCO expectations; Syvial's independent Twenty-Four Severance stays 24.
# ---------------------------------------------------------------------------
combat_test = COMBAT_TEST.read_text(encoding="utf-8")
combat_test = combat_test.replace("24/24 phát trúng liên tiếp", "72/72 phát trúng liên tiếp")
combat_test = combat_test.replace("500 - (24 * 10)", "500 - (72 * 10)")
combat_test = combat_test.replace("assertEquals(261, after.entityHp)", "assertEquals(1, after.entityHp)")
# Tests that explicitly assert Kai's inactive/base GCO total now use 72 * 10 = 720.
combat_test = combat_test.replace('contains("tổng -240 HP")', 'contains("tổng -720 HP")')
if "kaiR10GameplayTriplesGunSkillRoundCounts" not in combat_test:
    close = combat_test.rfind("\n}")
    if close < 0:
        raise RuntimeError("Kai R10 CombatRuntimeTest closing brace missing")
    regression = r'''

  @Test fun kaiR10GameplayTriplesGunSkillRoundCounts() {
    val source = java.io.File("src/main/java/com/rabpit/backroom/core/CombatRuntime.kt").readText()
    assertTrue(source.contains("KAI_GUILTY_CROWN_SHOTS = 72"))
    assertTrue(source.contains("KAI_LAST_REQUIEM_ROUNDS = 12"))
    assertTrue(source.contains("KAI_SILENT_LULLABY_ROUNDS = 12"))
    assertTrue(source.contains("KAI_SALVATION_ROUNDS = 6"))
  }
'''
    combat_test = combat_test[:close] + regression + combat_test[close:]
COMBAT_TEST.write_text(combat_test, encoding="utf-8")

skill_test = SKILL_TEST.read_text(encoding="utf-8")
if "kaiR10SkillSheetUsesMk19AndTripledRounds" not in skill_test:
    close = skill_test.rfind("\n}")
    if close < 0:
        raise RuntimeError("Kai R10 CompanionSkillCatalogTest closing brace missing")
    regression = r'''

  @org.junit.Test fun kaiR10SkillSheetUsesMk19AndTripledRounds() {
    val skills = CompanionSkillCatalog.forCharacter(KAI_ID).associateBy { it.name }
    org.junit.Assert.assertTrue(skills.getValue("The Last Requiem").effect.contains("12 viên"))
    org.junit.Assert.assertTrue(skills.getValue("Silent Lullaby").effect.contains("12 viên"))
    org.junit.Assert.assertTrue(skills.getValue("Salvation").effect.contains("6 viên"))
    org.junit.Assert.assertTrue(skills.getValue("Guilty Crown Override").effect.contains("72 viên"))
    org.junit.Assert.assertTrue(skills.values.any { it.effect.contains("SRU Assault Rifle MK19") })
  }
'''
    skill_test = skill_test[:close] + regression + skill_test[close:]
SKILL_TEST.write_text(skill_test, encoding="utf-8")

equipment_test = EQUIPMENT_TEST.read_text(encoding="utf-8")
equipment_test = equipment_test.replace('"SRU-SG Shotgun"', f'"{R10_WEAPON_NAME}"')
equipment_test = equipment_test.replace('"Shotgun Mastery"', '"Assault Rifle Mastery"')
if "kaiR10WeaponProjectionExposesTechnicalSpec" not in equipment_test:
    close = equipment_test.rfind("\n}")
    if close < 0:
        raise RuntimeError("Kai R10 SruEquipmentIntegrationTest closing brace missing")
    regression = r'''

  @Test fun kaiR10WeaponProjectionExposesTechnicalSpec() {
    val weapon = EquipmentCatalog.definition(KAI_SRU_AR_MK19_ID)!!
    assertEquals("SRU Assault Rifle MK19", weapon.name)
    assertEquals("ASSAULT RIFLE", weapon.type)
    assertEquals(950, weapon.weapon!!.rpmCapability)
    assertTrue(weapon.weapon!!.ammoDisplay!!.contains("30 viên"))
    assertTrue(weapon.abilities.any { it.name == "Assault Rifle Mastery" })
    assertTrue(weapon.abilities.any { it.name == "Technical Spec R10" && it.effect.contains("700–950") })
  }
'''
    equipment_test = equipment_test[:close] + regression + equipment_test[close:]
EQUIPMENT_TEST.write_text(equipment_test, encoding="utf-8")

# ---------------------------------------------------------------------------
# Fail closed if stale current-canon claims survive.
# ---------------------------------------------------------------------------
final_combat = COMBAT.read_text(encoding="utf-8")
final_catalog = CATALOG.read_text(encoding="utf-8")
final_equipment = EQUIPMENT.read_text(encoding="utf-8")
verified = json.loads(KNOWLEDGE_DB.read_text(encoding="utf-8"))
verified_by_id = {record.get("id"): record for record in verified.get("records", [])}

for marker in (
    "KAI_GUILTY_CROWN_SHOTS = 72",
    "KAI_LAST_REQUIEM_ROUNDS = 12",
    "KAI_SILENT_LULLABY_ROUNDS = 12",
    "KAI_SALVATION_ROUNDS = 6",
    R10_WEAPON_NAME,
):
    if marker not in final_combat:
        raise RuntimeError("Kai R10 final combat contract missing: " + marker)

for marker in ("12 viên", "6 viên", "72 viên", R10_WEAPON_NAME):
    if marker not in final_catalog:
        raise RuntimeError("Kai R10 final skill catalog missing: " + marker)

for marker in (R10_WEAPON_NAME, "ASSAULT RIFLE", "Technical Spec R10", "700–950", "500–600"):
    if marker not in final_equipment:
        raise RuntimeError("Kai R10 final equipment projection missing: " + marker)

if R10_WEAPON_ID not in verified_by_id:
    raise RuntimeError("Kai R10 weapon knowledge ID missing")
if "CHAR.KAI.SRU_SG" in verified_by_id:
    raise RuntimeError("Kai R10 stale SRU-SG current knowledge ID survived")
if str(R10_GCO_ROUNDS) not in verified_by_id["CHAR.KAI.GUILTY_CROWN_OVERRIDE"].get("text", ""):
    raise RuntimeError("Kai R10 gameplay GCO round count missing from knowledge")

print(
    "Kai R10 runtime applied: SRU Assault Rifle MK19 canon/specs are current; "
    "Kai gameplay gun-skill round counts are x3 (12/12/6/72) while AUTO %DMG/proc values remain unchanged."
)
