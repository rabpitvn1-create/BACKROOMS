from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FACADE = CORE / "GameCoreFacade.kt"
LEVEL_RUNTIME = CORE / "GenericLevelRuntime.kt"
COMBAT = CORE / "CombatRuntime.kt"


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one player-facing anchor, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# Internal authority and validation vocabulary must never be surfaced as gameplay prose.
replace_once(
    MAIN,
    "Kai vẫn ở khu vực hiện tại. Hành động này không tạo ra chuyển dịch nào được Core xác nhận.",
    "Kai vẫn ở nguyên khu vực. Lối đi trước mặt không thay đổi sau những gì vừa thử.",
    "registered navigation rejection narration",
)

replace_once(
    FACADE,
    'val reply = "[Warning] Hành động Level không thể commit: ${result.error}."',
    'val reply = "Những gì Kai vừa thử không tạo ra thay đổi nào có thể tiếp tục từ đây."',
    "registered action rejection narration",
)

replace_once(
    FACADE,
    'val reply = "[Warning] Level progression only moves forward; a completed Level cannot become current again."',
    'val reply = "Con đường vừa rời khỏi không còn mở lại theo cách cũ."',
    "forward progression rejection narration",
)

for old, new, label in (
    (
        '"Level instance chưa được khởi tạo."',
        '"Kai chưa thể xác định được một lối đi ổn định từ vị trí hiện tại."',
        "missing registered level instance",
    ),
    (
        '"Không tìm thấy Level definition cho ${stored.levelId}."',
        '"Không gian quanh Kai không khớp với bất kỳ khu vực quen thuộc nào."',
        "missing registered level definition",
    ),
    (
        '"Lối chuyển Level đã được mở."',
        '"Một lối chuyển tiếp đã hiện ra trước mặt Kai."',
        "completed level narration",
    ),
    (
        '"Level instance tham chiếu một vùng không tồn tại."',
        '"Lối đi trước mặt đột ngột khép lại như thể chưa từng tồn tại."',
        "invalid zone narration",
    ),
    (
        '"Không còn bước Escape nào chưa hoàn thành trong blueprint đã khóa."',
        '"Kai không tìm thấy thêm bước nào có thể tiếp tục theo hướng vừa thử."',
        "completed escape sequence narration",
    ),
    (
        '"Level instance thiếu action rule đã khóa: $actionId."',
        '"Hành động đó không tạo ra phản ứng nào có thể tiếp tục."',
        "missing action rule narration",
    ),
    (
        '"Hành động làm trạng thái Level thay đổi."',
        '"Không gian quanh Kai thay đổi sau hành động đó."',
        "generic registered action narration",
    ),
):
    replace_once(LEVEL_RUNTIME, old, new, label)

replace_once(
    COMBAT,
    'else " $itemName đã được Core khóa cho lần nhận lại qua InventoryEngine."',
    'else " $itemName vẫn nằm lại tại chỗ sau lần nhặt tự động không thành công."',
    "entity loot retry narration",
)

# Fail closed if a future patch reintroduces the known player-facing implementation vocabulary.
final_main = MAIN.read_text(encoding="utf-8")
final_facade = FACADE.read_text(encoding="utf-8")
final_level = LEVEL_RUNTIME.read_text(encoding="utf-8")
final_combat = COMBAT.read_text(encoding="utf-8")
combined = "\n".join((final_main, final_facade, final_level, final_combat))

for forbidden in (
    "Hành động này không tạo ra chuyển dịch nào được Core xác nhận.",
    "[Warning] Hành động Level không thể commit:",
    "[Warning] Level progression only moves forward",
    "Level instance chưa được khởi tạo.",
    "Không tìm thấy Level definition",
    "Không còn bước Escape nào chưa hoàn thành trong blueprint đã khóa.",
    "Level instance thiếu action rule đã khóa:",
    "đã được Core khóa cho lần nhận lại qua InventoryEngine.",
):
    if forbidden in combined:
        raise RuntimeError("player-facing internal authority leak remains: " + forbidden)

for required in (
    "Kai vẫn ở nguyên khu vực. Lối đi trước mặt không thay đổi sau những gì vừa thử.",
    "Những gì Kai vừa thử không tạo ra thay đổi nào có thể tiếp tục từ đây.",
    "Con đường vừa rời khỏi không còn mở lại theo cách cũ.",
    "Một lối chuyển tiếp đã hiện ra trước mặt Kai.",
):
    if required not in combined:
        raise RuntimeError("player-facing authority boundary missing: " + required)

print("Player-facing authority boundary applied: gameplay prose no longer exposes Core, engine, commit, blueprint, or registered-level implementation terms.")
