from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
LUCIA = CORE / "LuciaCanon.kt"
STATS = CORE / "CharacterStats.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
KNOWLEDGE_DB = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"
KNOWLEDGE_ENGINE = CORE / "knowledge/KnowledgeContextEngine.kt"
LUCIA_TEST = TESTS / "LuciaFollowerTest.kt"
AVATAR = ROOT / "app/src/main/assets/avatars/lucia_avatar.jpg"

R03_VISUAL_DESCRIPTION = (
    "Gương mặt trẻ, cân đối, đường nét thanh; da sáng; mắt nâu ấm; tóc đen sẫm rất dài và dày, "
    "buộc đuôi ngựa cao với các lọn tóc mềm quanh mặt. Vóc dáng thon khỏe, thiên về cơ động. "
    "Quân phục ngụy trang hiện đại tông xanh-nâu/đất, plate carrier/tactical vest, pouch mô-đun, "
    "găng tay tác chiến, thắt lưng chiến thuật và bao súng đùi. Không đội mũ bảo hiểm trong Visual Lock R03. "
    "M4A1 màu đen có báng điều chỉnh, handguard rail, optic, tay cầm trước, suppressor và cụm đèn/laser. "
    "Có một súng ngắn màu đen trong bao đùi nhưng model, cỡ đạn, lượng đạn và gameplay của súng ngắn vẫn OPEN."
)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Lucia R03 {label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def require_file(path: Path, label: str) -> None:
    if not path.is_file() or path.stat().st_size <= 0:
        raise RuntimeError(f"Lucia R03 missing {label}: {path}")


for path, label in (
    (LUCIA, "generated LuciaCanon.kt"),
    (STATS, "generated CharacterStats.kt"),
    (MAIN, "generated MainActivity.java"),
    (KNOWLEDGE_DB, "knowledge database"),
    (KNOWLEDGE_ENGINE, "KnowledgeContextEngine.kt"),
    (LUCIA_TEST, "LuciaFollowerTest.kt"),
    (AVATAR, "Lucia avatar asset"),
):
    require_file(path, label)

# ---------------------------------------------------------------------------
# Character runtime canon. Display name remains Lucia Lục while legal identity,
# background, human-scale training and visual lock become explicit metadata.
# The existing avatar path remains stable; R03 is the authoritative visual
# description/codex reference and does not depend on image bytes in this patch.
# ---------------------------------------------------------------------------
lucia = LUCIA.read_text(encoding="utf-8")
lucia = replace_once(
    lucia,
    '  const val NAME = "Lucia \\"Lục\\""\n',
    '  const val NAME = "Lucia Lục"\n',
    "display name",
)

constants_anchor = '  const val AVATAR_REF = "avatars/lucia_avatar.jpg"\n'
constants_block = constants_anchor + '''  const val LEGAL_NAME = "Hứa Thuý Mai"
  const val MILITARY_ALIAS = "Lucia Lục"
  const val NATIONALITY = "Việt Nam"
  const val HERITAGE = "Hoa Kiều"
  const val FAMILY_LINEAGE = "Chít nội của Gia tộc Họ Hứa"
  const val SERVICE_LENGTH = "1 năm"
  const val TRAINING_PROGRAM = "Huấn luyện quân ngũ tại Việt Nam kết hợp với Hoa Kỳ"
  const val ENTRANCE_RESULT = "Xuất sắc"
  const val POWER_SCALE = "HUMAN_TRAINED"
  const val VISUAL_LOCK = "LUCIA_VISUAL_R03"
'''
if 'const val LEGAL_NAME = "Hứa Thuý Mai"' not in lucia:
    lucia = replace_once(lucia, constants_anchor, constants_block, "identity constants")

legacy_identity = '''        "age" to AGE.toString(),
        "species" to "human",
        "gender" to "female",
        "militaryRank" to "Binh nhì",
        "militaryRole" to "Tư lệnh cấp tiểu đội trong biên chế đặc nhiệm",
'''
r03_identity = '''        "age" to AGE.toString(),
        "species" to "human",
        "gender" to "female",
        "legalName" to LEGAL_NAME,
        "militaryAlias" to MILITARY_ALIAS,
        "nationality" to NATIONALITY,
        "heritage" to HERITAGE,
        "familyLineage" to FAMILY_LINEAGE,
        "serviceLength" to SERVICE_LENGTH,
        "trainingProgram" to TRAINING_PROGRAM,
        "entranceResult" to ENTRANCE_RESULT,
        "militaryRank" to "OPEN",
        "militaryRole" to "Binh sĩ quân sự bình thường",
        "commandAuthority" to "none",
        "powerScale" to POWER_SCALE,
        "supernaturalPower" to "false",
        "visualLock" to VISUAL_LOCK,
        "visualAsset" to AVATAR_REF,
        "visualReference" to "Lucia_Codex R03",
        "visualDescription" to "''' + R03_VISUAL_DESCRIPTION + '''",
        "visualSidearm" to "Súng ngắn màu đen trong bao đùi; visual-only, gameplay OPEN",
'''
lucia = replace_once(lucia, legacy_identity, r03_identity, "identity metadata")
LUCIA.write_text(lucia, encoding="utf-8")

# The legacy SQUAD LEADER label conflicts with the new human-scale soldier canon.
# Removing the label changes no combat math or skill resolution.
stats = STATS.read_text(encoding="utf-8")
stats = replace_once(
    stats,
    '    combatRole = "TACTICAL RIFLEWOMAN / SQUAD LEADER / FOLLOWER",\n',
    '    combatRole = "TACTICAL RIFLEWOMAN / FOLLOWER",\n',
    "combat-role label",
)
STATS.write_text(stats, encoding="utf-8")

# ---------------------------------------------------------------------------
# GM prompt: retire the stale 50% random-spawn / squad-command claim. Keep only
# a compact hard lock here; detailed canon is retrieved on demand from the
# knowledge packet when Lucia is present or explicitly mentioned.
# ---------------------------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")
main = main.replace('Lucia \\"Lục\\"', 'Lucia Lục')
lock_start = main.find("LUCIA FOLLOWER HARD LOCK:")
lock_end = main.find("ACTION_RUNTIME:", lock_start)
if lock_start < 0 or lock_end < 0:
    raise RuntimeError("Lucia R03 GM hard-lock span missing")
r03_lock = (
    "LUCIA R03 HARD LOCK: Lucia Lục là biệt danh quân đội của Hứa Thuý Mai, nữ 19 tuổi, quốc tịch Việt Nam, "
    "xuất thân Hoa Kiều, chít nội Gia tộc Họ Hứa. Cô có 1 năm quân ngũ theo chương trình Việt Nam-Hoa Kỳ và "
    "đạt loại xuất sắc đầu vào, nhưng power-scale vẫn HUMAN_TRAINED: con người được huấn luyện tốt, không có "
    "năng lực siêu nhiên và không ngang tầng Kai/Iris/Syvial. Lucia là fixed story encounter tại Level 0, "
    "requiresQuest=false, randomSpawn=false. Không tự gán quân hàm, quyền chỉ huy, quan hệ/xưng hô hay quá khứ "
    "chưa khóa. Khi Lucia hiện diện hoặc được nhắc tới, dùng CHAR.LUCIA.RUNTIME_CORE trong KNOWLEDGE_PACKET cho "
    "ngoại hình và chi tiết profile.\\n"
)
main = main[:lock_start] + r03_lock + main[lock_end:]
MAIN.write_text(main, encoding="utf-8")

# ---------------------------------------------------------------------------
# On-demand knowledge card. This is writer/GM context, not automatic character
# knowledge. It stays compact so Lucia does not consume prompt budget when absent.
# ---------------------------------------------------------------------------
data = json.loads(KNOWLEDGE_DB.read_text(encoding="utf-8"))
records = data.get("records")
if not isinstance(records, list):
    raise RuntimeError("Lucia R03 knowledge database records missing")
record_id = "CHAR.LUCIA.RUNTIME_CORE"
record = next((item for item in records if item.get("id") == record_id), None)
r03_record = {
    "id": record_id,
    "domain": "CHARACTER",
    "kind": "runtime-card",
    "text": (
        "Lucia Lục là biệt danh quân đội của Hứa Thuý Mai, nữ 19 tuổi, quốc tịch Việt Nam, xuất thân Hoa Kiều và là "
        "chít nội của Gia tộc Họ Hứa. Cô có một năm quân ngũ theo chương trình huấn luyện Việt Nam-Hoa Kỳ; đầu vào "
        "xếp loại xuất sắc. Đây là thành tích trong phạm vi con người: cô là binh sĩ được đào tạo tốt, không có quyền "
        "chỉ huy đã khóa và không có Core, Devil Trigger, time-stop, hồi phục siêu nhiên hay power-scale ngang Kai/Iris/Syvial. "
        "Visual Lock R03: gương mặt trẻ thanh, da sáng, mắt nâu ấm, tóc đen rất dài buộc đuôi ngựa cao; vóc dáng thon khỏe; "
        "quân phục camouflage hiện đại với plate carrier, pouch, găng, thắt lưng và bao súng đùi; M4A1 đen có optic, foregrip, "
        "suppressor và đèn/laser. Một súng ngắn đen trong bao đùi chỉ được khóa về thị giác, thông số gameplay vẫn OPEN. "
        "Lucia là fixed story encounter Level 0, không random spawn. Các dữ kiện này là writer/GM canon; Kai hoặc NPC chỉ biết "
        "những gì đã được Lucia tiết lộ hay quan sát hợp lệ trong truyện."
    ),
    "source": {"document": "02_CHARACTERS/Lucia_Codex.docx", "anchor": "R03; 00; 02; 03; 11; 12"},
    "authority": "CHARACTER_CANON",
    "mutability": "IMMUTABLE",
    "priority": 20,
    "tags": ["lucia", "lucia lục", "hứa thuý mai", "thuý mai", "visual lock r03"],
    "references": [],
    "affordances": [],
}
if record is None:
    records.append(r03_record)
else:
    record.clear()
    record.update(r03_record)
KNOWLEDGE_DB.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

engine = KNOWLEDGE_ENGINE.read_text(encoding="utf-8")
engine = replace_once(
    engine,
    '''    private fun addPresentRuntimeCards() {
      if ("iris" in presentActors) add("CHAR.IRIS.RUNTIME_CORE", "present actor runtime core")
      if ("syvial" in presentActors) add("CHAR.SYVIAL.RUNTIME_CORE", "present actor runtime core")
    }
''',
    '''    private fun addPresentRuntimeCards() {
      if ("iris" in presentActors) add("CHAR.IRIS.RUNTIME_CORE", "present actor runtime core")
      if ("syvial" in presentActors) add("CHAR.SYVIAL.RUNTIME_CORE", "present actor runtime core")
      if ("lucia" in presentActors) add("CHAR.LUCIA.RUNTIME_CORE", "present actor runtime core")
    }
''',
    "present runtime card",
)

direct_anchor = '      val direct = linkedSetOf<String>()\n'
direct_line = direct_anchor + '      if (hasAny(actionText, "lucia", "lucia lục", "hứa thuý mai", "thuý mai")) direct += "CHAR.LUCIA.RUNTIME_CORE"\n'
if 'direct += "CHAR.LUCIA.RUNTIME_CORE"' not in engine:
    engine = replace_once(engine, direct_anchor, direct_line, "direct knowledge lookup")

presence_anchor = '          if (id.contains("syvial")) presentActors += "syvial"\n'
presence_replacement = presence_anchor + '          if (id.contains("lucia")) presentActors += "lucia"\n'
if engine.count('if (id.contains("lucia")) presentActors += "lucia"') == 0:
    count = engine.count(presence_anchor)
    if count != 2:
        raise RuntimeError(f"Lucia R03 presence resolver: expected two actor anchors, found {count}")
    engine = engine.replace(presence_anchor, presence_replacement)

engine = replace_once(
    engine,
    '          "communication", "exploration", "iris", "syvial", "reunionPath",\n',
    '          "communication", "exploration", "iris", "syvial", "lucia", "reunionPath",\n',
    "compact continuity state",
)
KNOWLEDGE_ENGINE.write_text(engine, encoding="utf-8")

# ---------------------------------------------------------------------------
# Regression: identity/background/power-scale/visual metadata must survive the
# generated runtime chain without turning Lucia into a supernatural combatant.
# ---------------------------------------------------------------------------
test = LUCIA_TEST.read_text(encoding="utf-8")
test_marker = "luciaR03CanonIsExposedWithoutPromotingHerToSuperhuman"
if test_marker not in test:
    close = test.rfind("\n}")
    if close < 0:
        raise RuntimeError("Lucia R03 test class closing brace missing")
    regression = r'''

  @Test fun luciaR03CanonIsExposedWithoutPromotingHerToSuperhuman() {
    val state = LuciaCanon.ensure(GameState.initial())
    val lucia = state.characters.getValue(LUCIA_ID)
    assertEquals("Lucia Lục", lucia.name)
    assertEquals("Hứa Thuý Mai", lucia.metadata["legalName"])
    assertEquals("Lucia Lục", lucia.metadata["militaryAlias"])
    assertEquals("Việt Nam", lucia.metadata["nationality"])
    assertEquals("Hoa Kiều", lucia.metadata["heritage"])
    assertEquals("Chít nội của Gia tộc Họ Hứa", lucia.metadata["familyLineage"])
    assertEquals("1 năm", lucia.metadata["serviceLength"])
    assertEquals("Xuất sắc", lucia.metadata["entranceResult"])
    assertEquals("OPEN", lucia.metadata["militaryRank"])
    assertEquals("Binh sĩ quân sự bình thường", lucia.metadata["militaryRole"])
    assertEquals("none", lucia.metadata["commandAuthority"])
    assertEquals("HUMAN_TRAINED", lucia.metadata["powerScale"])
    assertEquals("false", lucia.metadata["supernaturalPower"])
    assertEquals("LUCIA_VISUAL_R03", lucia.metadata["visualLock"])
    assertEquals("Lucia_Codex R03", lucia.metadata["visualReference"])
    assertEquals("avatars/lucia_avatar.jpg", lucia.avatarRef)
    assertFalse(lucia.statProfile.combatRole.contains("SQUAD LEADER"))
  }
'''
    test = test[:close] + regression + test[close:]
LUCIA_TEST.write_text(test, encoding="utf-8")

# Final source-level audit. The patch fails during preflight if any stale canon
# survives in the generated runtime surfaces it owns.
final_lucia = LUCIA.read_text(encoding="utf-8")
final_stats = STATS.read_text(encoding="utf-8")
final_main = MAIN.read_text(encoding="utf-8")
final_engine = KNOWLEDGE_ENGINE.read_text(encoding="utf-8")
final_db = KNOWLEDGE_DB.read_text(encoding="utf-8")
final_test = LUCIA_TEST.read_text(encoding="utf-8")

required = {
    final_lucia: (
        'const val LEGAL_NAME = "Hứa Thuý Mai"',
        '"nationality" to NATIONALITY',
        '"heritage" to HERITAGE',
        '"familyLineage" to FAMILY_LINEAGE',
        '"powerScale" to POWER_SCALE',
        '"visualLock" to VISUAL_LOCK',
        '"visualReference" to "Lucia_Codex R03"',
    ),
    final_stats: ('combatRole = "TACTICAL RIFLEWOMAN / FOLLOWER"',),
    final_main: ("LUCIA R03 HARD LOCK:", "randomSpawn=false", "CHAR.LUCIA.RUNTIME_CORE"),
    final_engine: (
        'add("CHAR.LUCIA.RUNTIME_CORE", "present actor runtime core")',
        'direct += "CHAR.LUCIA.RUNTIME_CORE"',
        'presentActors += "lucia"',
    ),
    final_db: ("CHAR.LUCIA.RUNTIME_CORE", "Hứa Thuý Mai", "Visual Lock R03"),
    final_test: (test_marker,),
}
for source, markers in required.items():
    for marker in markers:
        if marker not in source:
            raise RuntimeError("Lucia R03 runtime contract missing: " + marker)

for stale in (
    'militaryRank" to "Binh nhì"',
    'militaryRole" to "Tư lệnh cấp tiểu đội trong biên chế đặc nhiệm"',
    'combatRole = "TACTICAL RIFLEWOMAN / SQUAD LEADER / FOLLOWER"',
    "LUCIA FOLLOWER HARD LOCK:",
):
    if stale in final_lucia + "\n" + final_stats + "\n" + final_main:
        raise RuntimeError("Lucia R03 stale canon survived: " + stale)

print(
    "Lucia R03 runtime canon applied: Hứa Thuý Mai identity/background, human-trained power scale, "
    "Visual Lock R03 knowledge retrieval and stable avatar path are authoritative without changing combat mechanics."
)
