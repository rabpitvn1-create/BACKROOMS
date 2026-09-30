from pathlib import Path

facade_path = Path('android-apk/app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.java')
facade = facade_path.read_text(encoding='utf-8')

replacements = [
    ('  private static final int CURRENT_SAVE_VERSION = 4;\n', '  private static final int CURRENT_SAVE_VERSION = 5;\n'),
    ('  private final LevelCore levelCore;\n', '  private final LevelCore levelCore;\n  private final EntityCore entityCore;\n'),
    ('    this.levelCore = new LevelCore(appContext);\n', '    this.levelCore = new LevelCore(appContext);\n    this.entityCore = new EntityCore(appContext);\n'),
    (
        '      return response(false, legacy, null, "fallback_required", null);\n    } catch (Exception e) {\n      debug("processRule failed: " + e.getMessage());\n',
        '      entityCore.prepareEncounter(legacy);\n      return response(false, legacy, null, "fallback_required", null);\n    } catch (Exception e) {\n      debug("processRule failed: " + e.getMessage());\n'
    ),
    (
        '      levelCore.validateAndApplyTransition(before, sanitized);\n      sanitized.put("saveVersion", CURRENT_SAVE_VERSION);\n',
        '      levelCore.validateAndApplyTransition(before, sanitized);\n      entityCore.validateAndApply(before, sanitized);\n      sanitized.put("saveVersion", CURRENT_SAVE_VERSION);\n'
    ),
    (
        '  public synchronized String levelSnapshotDescriptor(String stateJson) {\n',
        '  public synchronized String entityPromptContext(String stateJson) {\n'
        '    JSONObject state = parseState(stateJson);\n'
        '    try {\n'
        '      levelCore.normalizeState(state);\n'
        '      return entityCore.promptContext(state);\n'
        '    } catch (Exception e) {\n'
        '      return "ENTITY CORE: unavailable. Do not invent an Entity.";\n'
        '    }\n'
        '  }\n\n'
        '  public synchronized String levelSnapshotDescriptor(String stateJson) {\n'
    ),
]

for old, new in replacements:
    if old not in facade:
        raise SystemExit('GameCoreFacade marker not found: ' + old[:100])
    facade = facade.replace(old, new, 1)

facade_path.write_text(facade, encoding='utf-8')

main_path = Path('android-apk/app/src/main/java/com/rabpit/backroom/MainActivity.java')
main = main_path.read_text(encoding='utf-8')

old_state = '''          JSONObject state = localResult.optJSONObject("state");
          if (state == null) state = new JSONObject(stateJson);
          String levelContext = gameCore.levelPromptContext(state.toString());
          String prompt = "Bạn là Game Master của text game Backrooms. Xử lý đúng một lượt và trả DUY NHẤT JSON hợp lệ, không markdown. " +
'''
new_state = '''          JSONObject state = localResult.optJSONObject("state");
          if (state == null) state = new JSONObject(stateJson);
          String coreBeforeJson = state.toString();
          String levelContext = gameCore.levelPromptContext(coreBeforeJson);
          String entityContext = gameCore.entityPromptContext(coreBeforeJson);
          String prompt = "Bạn là Game Master của text game Backrooms. Xử lý đúng một lượt và trả DUY NHẤT JSON hợp lệ, không markdown. " +
'''
if old_state not in main:
    raise SystemExit('MainActivity state marker not found')
main = main.replace(old_state, new_state, 1)

old_contract = '''            "ENTITY OVERLAY STATE CONTRACT: flags.entityEncounterKey bắt buộc có ở mọi lượt AI. Nếu một Entity đang trực tiếp hiện diện hoặc đối đầu, đặt canonical key local tương ứng; nếu không còn Entity trực tiếp hiện diện thì đặt chuỗi rỗng. Canonical keys: hound, clump, duller, deathmoth, hostile_faceling, false_puddle, paintings, smiler, skin-stealer, predatory_window, biological_pipeline, wretch, cable_mimic, the_beast_of_level_5, hotel_corpse_lure, jeff_the_killer, jane_the_killer, slenderman, diep_minh. Không dùng đường dẫn, URL hoặc alias khác. " +
            "LEVEL CORE CONTRACT: currentLevel bắt buộc là số nguyên 0-6. Nếu chưa thực sự đi qua một route/boundary hợp lệ thì giữ nguyên currentLevel. Không được teleport sang Level không kết nối. " +
            levelContext + "\\n" +
'''
new_contract = '''            "ENTITY CORE CONTRACT: Main Game Core sở hữu toàn bộ spawn roll. Không được tự tạo, tự chọn, tự thay hoặc tự tăng tỉ lệ Entity. Giữ nguyên flags.entityEncounterKey do Core cung cấp. Nếu encounter đang hoạt động và thực sự kết thúc trong lượt này, chỉ đặt flags.entityEncounterResolved=true; nếu chưa kết thúc thì không đặt cờ resolved. " +
            "LEVEL CORE CONTRACT: currentLevel bắt buộc là số nguyên 0-6. Nếu chưa thực sự đi qua một route/boundary hợp lệ thì giữ nguyên currentLevel. Không được teleport sang Level không kết nối. " +
            levelContext + "\\n" + entityContext + "\\n" +
'''
if old_contract not in main:
    raise SystemExit('MainActivity Entity contract marker not found')
main = main.replace(old_contract, new_contract, 1)

old_commit = '          JSONObject coreCommit = new JSONObject(gameCore.processValidatedCandidate(stateJson, state.toString(), action));\n'
new_commit = '          JSONObject coreCommit = new JSONObject(gameCore.processValidatedCandidate(coreBeforeJson, state.toString(), action));\n'
if old_commit not in main:
    raise SystemExit('MainActivity core commit marker not found')
main = main.replace(old_commit, new_commit, 1)

main_path.write_text(main, encoding='utf-8')
