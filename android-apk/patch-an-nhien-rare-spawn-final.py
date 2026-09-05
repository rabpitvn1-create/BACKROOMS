from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

text = MAIN.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"An Nhiên rare spawn {label}: expected exactly one anchor, found {count}")
    text = text.replace(old, new, 1)


# An Nhiên is no longer a mandatory Level-0 contact. One successful value on d40,000,000
# is exactly 0.0000025% per eligible physical Level-0 turn.
replace_once(
    '    rolls.put("anNhienEncounter", thresholdRoll("anNhienEncounter", 1, 1, level == 0 && physical && !anNhienEncountered, " mandatory Level 0 follower"));\n',
    '    JSONObject anNhienEncounterRoll = thresholdRoll("anNhienEncounter", 40000000, 1, level == 0 && physical && !anNhienEncountered, " rare Level 0 follower");\n'
    '    anNhienEncounterRoll.put("chancePercent", 0.0000025).put("chance", "0.0000025% rare Level 0 follower");\n'
    '    rolls.put("anNhienEncounter", anNhienEncounterRoll);\n',
    "encounter probability",
)

# Other survivor contacts must not be suppressed while waiting for an ultra-rare An Nhiên roll.
replace_once(
    '    rolls.put("survivor", thresholdRoll("survivor", 10000, 200, survivorAllowed && !(level == 0 && !anNhienEncountered), ""));\n',
    '    rolls.put("survivor", thresholdRoll("survivor", 10000, 200, survivorAllowed, ""));\n',
    "survivor independence",
)

# Leaving Level 0 must never depend on meeting An Nhiên.
forced_exit_gate = '    if (currentLevel(before) == 0 && !anNhienEncountered(before)) return false;\n'
if forced_exit_gate in text:
    if text.count(forced_exit_gate) != 1:
        raise RuntimeError("An Nhiên rare spawn exit gate: expected exactly one forced gate")
    text = text.replace(forced_exit_gate, "", 1)

# Lucia remains the story-owned fixed Level-0 contact and must not wait for An Nhiên.
replace_once(
    '    rolls.put("luciaEncounter", thresholdRoll("luciaEncounter", 1, 1, level == 0 && physical && anNhienEncountered && !luciaSeen, " story-owned fixed Level 0 contact"));\n',
    '    rolls.put("luciaEncounter", thresholdRoll("luciaEncounter", 1, 1, level == 0 && physical && !luciaSeen, " story-owned fixed Level 0 contact"));\n',
    "Lucia independence",
)

# Keep any surviving GM hard-lock prose aligned with the Android-owned probability.
old_prompt = (
    "AN NHIÊN HARD LOCK: bé gái 7 tuổi, con người, không phải Entity. "
    "anNhienEncounter success=true là cuộc gặp bắt buộc ở Level 0 và phải được kể trong lượt đó; "
)
new_prompt = (
    "AN NHIÊN HARD LOCK: bé gái 7 tuổi, con người, không phải Entity. "
    "Cô không phải nhân vật bắt buộc đầu game. Chỉ khi anNhienEncounter success=true mới được cho cô xuất hiện; "
    "xác suất Android khóa là đúng 0.0000025% trên mỗi lượt vật lý đủ điều kiện ở Level 0 và roll fail tuyệt đối không được ép gặp. "
)
if old_prompt in text:
    text = text.replace(old_prompt, new_prompt, 1)

# Regression guards for the final generated runtime.
required = [
    'thresholdRoll("anNhienEncounter", 40000000, 1',
    'anNhienEncounterRoll.put("chancePercent", 0.0000025)',
    'thresholdRoll("survivor", 10000, 200, survivorAllowed, "")',
    'thresholdRoll("luciaEncounter", 1, 1, level == 0 && physical && !luciaSeen',
]
for marker in required:
    if marker not in text:
        raise RuntimeError(f"An Nhiên rare spawn final marker missing: {marker}")

for forbidden in [
    'thresholdRoll("anNhienEncounter", 1, 1',
    'mandatory Level 0 follower',
    'survivorAllowed && !(level == 0 && !anNhienEncountered)',
    'currentLevel(before) == 0 && !anNhienEncountered(before)',
    'level == 0 && physical && anNhienEncountered && !luciaSeen',
]:
    if forbidden in text:
        raise RuntimeError(f"An Nhiên rare spawn legacy behavior still present: {forbidden}")

MAIN.write_text(text, encoding="utf-8")
print("An Nhiên is optional: exact 0.0000025% Level-0 physical-turn spawn, no exit/survivor/Lucia gate.")
