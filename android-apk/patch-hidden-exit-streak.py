from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


roll_sig = "  private JSONObject makeGameplayRolls(JSONObject state, String actionKind, String action, boolean meta) throws Exception {\n"
roll_start = text.find(roll_sig)
roll_end = text.find("\n  private boolean rollSuccess(", roll_start)
if roll_start < 0 or roll_end < 0:
    raise RuntimeError("Final typed makeGameplayRolls boundary missing")
roll_method = text[roll_start:roll_end]
if 'thresholdRoll("_hiddenExitStreak", 100,' not in roll_method:
    return_pos = roll_method.rfind("    return rolls;\n")
    if return_pos < 0:
        raise RuntimeError("Final gameplay roll return missing")
    block = r'''    JSONObject hiddenExitRoll = thresholdRoll("_hiddenExitStreak", 100,
      com.rabpit.backroom.core.HiddenExitStreak.SUCCESS_PERCENT,
      exploreAction && !com.rabpit.backroom.core.HiddenExitStreak.ready(state), "");
    rolls.put(com.rabpit.backroom.core.HiddenExitStreak.ROLL_KEY, hiddenExitRoll);
    boolean hiddenExitReady = com.rabpit.backroom.core.HiddenExitStreak.projectedReady(state, rolls);
    JSONObject authoritativeExit = new JSONObject()
      .put("label", "levelExit")
      .put("dice", "none")
      .put("max", 100)
      .put("threshold", hiddenExitReady ? 100 : 0)
      .put("eligible", hiddenExitReady)
      .put("chancePercent", hiddenExitReady ? 100.0 : 0.0)
      .put("chance", hiddenExitReady ? "100.0000% route ready" : "0.0000% route locked")
      .put("roll", JSONObject.NULL)
      .put("success", hiddenExitReady);
    if (hiddenExitReady) authoritativeExit.put("guaranteedByState", true);
    rolls.put("levelExit", authoritativeExit);
    rolls.put("exitProbe", new JSONObject(authoritativeExit.toString()).put("label", "exitProbe"));

'''
    roll_method = roll_method[:return_pos] + block + roll_method[return_pos:]
    text = text[:roll_start] + roll_method + text[roll_end:]

transition_sig = "  private boolean canTransition(JSONObject before, JSONObject rolls) {\n"
transition_start = text.find(transition_sig)
transition_end = text.find("\n  private ", transition_start + len(transition_sig))
if transition_start < 0 or transition_end < 0:
    raise RuntimeError("canTransition boundary missing")
transition_method = '''  private boolean canTransition(JSONObject before, JSONObject rolls) {
    return com.rabpit.backroom.core.HiddenExitStreak.projectedReady(before, rolls);
  }
'''
text = text[:transition_start] + transition_method + text[transition_end:]

location_line = '        if (!value.isEmpty() && value.length() <= 700) state.put("location", value);\n'
location_new = '        if (!value.isEmpty() && value.length() <= 700 && !com.rabpit.backroom.core.HiddenExitStreak.failedThisTurn(rolls)) state.put("location", value);\n'
if location_new not in text:
    text = replace_once(text, location_line, location_new, "failed-route location lock")

old_progress_call = '            recordLevelProgress(state, oldLevel, newLevel);\n'
new_progress_call = '            com.rabpit.backroom.core.HiddenExitStreak.apply(before, state, rolls, oldLevel, newLevel);\n'
if new_progress_call not in text:
    text = replace_once(text, old_progress_call, new_progress_call, "hidden streak commit")

writer_sig = "  private String writerPrompt(JSONObject before, String action, JSONObject rolls, JSONArray auditFeedback) throws Exception {\n"
writer_start = text.find(writer_sig)
writer_end = text.find("\n  private ", writer_start + len(writer_sig))
if writer_start < 0 or writer_end < 0:
    raise RuntimeError("writerPrompt boundary missing")
writer = text[writer_start:writer_end]
if "String hiddenExitDirective =" not in writer:
    insert = '''    JSONObject visibleRolls = new JSONObject(rolls.toString());
    visibleRolls.remove(com.rabpit.backroom.core.HiddenExitStreak.ROLL_KEY);
    String hiddenExitDirective = com.rabpit.backroom.core.HiddenExitStreak.gmDirective(before, rolls);
'''
    writer = writer.replace(writer_sig, writer_sig + insert, 1)
    writer = writer.replace("rolls.toString()", "visibleRolls.toString()")
    directive_pos = writer.find("    String actionDirective = ")
    if directive_pos < 0:
        raise RuntimeError("writerPrompt actionDirective anchor missing")
    directive_end = writer.find(";\n", directive_pos)
    if directive_end < 0:
        raise RuntimeError("writerPrompt actionDirective terminator missing")
    directive_end += 2
    writer = writer[:directive_end] + '    actionDirective = actionDirective + "\\n" + hiddenExitDirective;\n' + writer[directive_end:]
    text = text[:writer_start] + writer + text[writer_end:]

old_six_turn = "Không hoàn tất cả Level trong 2–3 lượt: cần ít nhất 6 lượt gameplay trong Level và một Exit hợp lệ;"
new_hidden_rule = "Tiến trình Exit do cơ chế ẩn quyết định; không được nói số lượt, streak, bộ đếm hay xác suất cho người chơi;"
if old_six_turn in text:
    text = text.replace(old_six_turn, new_hidden_rule, 1)

for marker in (
    'thresholdRoll("_hiddenExitStreak", 100,',
    'HiddenExitStreak.SUCCESS_PERCENT',
    'HiddenExitStreak.projectedReady(before, rolls)',
    'HiddenExitStreak.failedThisTurn(rolls)',
    'HiddenExitStreak.apply(before, state, rolls, oldLevel, newLevel)',
    'visibleRolls.remove(com.rabpit.backroom.core.HiddenExitStreak.ROLL_KEY)',
    'String hiddenExitDirective =',
):
    if marker not in text:
        raise RuntimeError("Hidden Exit streak contract missing: " + marker)

for forbidden in (
    'return exitFound && progressionReady(before);',
    'recordLevelProgress(state, oldLevel, newLevel);',
    old_six_turn,
):
    if forbidden in text:
        raise RuntimeError("Retired Level-exit progression survived: " + forbidden)

MAIN.write_text(text, encoding="utf-8")
print("Installed hidden 5-streak Level exit authority: EXPLORE 50/50, fail resets without location rewind, no player-facing counter.")
