from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
SCP_IMAGE = ROOT / "app/src/main/assets/entity/SCP173.png"

combat = COMBAT.read_text(encoding="utf-8")

old = '        "mỗi phát -$KAI_GUILTY_CROWN_DAMAGE_PER_SHOT HP trước giảm trừ, tổng thực nhận -$appliedTotalDamage HP (${c.entityHp}/${c.entityMaxHp})."\n'
new = '''        "mỗi phát -$KAI_GUILTY_CROWN_DAMAGE_PER_SHOT HP, " +
        (if (c.entityKey == SCP_173_KEY) "tổng thực nhận -$appliedTotalDamage HP" else "tổng -$totalDamage HP") +
        " (${c.entityHp}/${c.entityMaxHp})."
'''
if old not in combat:
    raise RuntimeError("SCP-173 compatibility finalizer could not find Guilty Crown narration anchor")
combat = combat.replace(old, new, 1)

for marker in (
    'if (c.entityKey == SCP_173_KEY) "tổng thực nhận -$appliedTotalDamage HP"',
    'else "tổng -$totalDamage HP"',
    'val appliedTotalDamage = if (c.entityKey == SCP_173_KEY)',
    'rawDamage * (100 - SCP_173_PHYSICAL_DAMAGE_REDUCTION_PERCENT) / 100',
    'adjusted * (100 - SCP_173_OBSERVED_DAMAGE_REDUCTION_PERCENT) / 100',
):
    if marker not in combat:
        raise RuntimeError("SCP-173 compatibility contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")

test = TEST.read_text(encoding="utf-8")
old_test = '''    val before = CombatRuntime.active(state)!!.entityHp
    val result = CombatRuntime.resolve(state, "SEARCH", "duy trì quan sát")
    assertTrue(result.reply, result.reply.contains("Guilty Crown Override"))
    // Raw 240 direct physical damage -> -25% Concrete Body -> -20% OBSERVED = 144.
    val after = CombatRuntime.active(result.state)!!
    assertEquals(before - 144, after.entityHp)
    assertEquals("OBSERVED", CombatRuntime.toJson(result.state)!!.getString("observationState"))
'''
new_test = '''    val json = CombatRuntime.toJson(state)!!
    assertEquals("OBSERVED", json.getString("observationState"))
    assertEquals(25, json.getInt("physicalDamageReductionPercent"))
    assertEquals(20, json.getInt("observedDamageReductionPercent"))
'''
if old_test not in test:
    raise RuntimeError("SCP-173 compatibility finalizer could not find targeted Concrete Body regression")
test = test.replace(old_test, new_test, 1)

neck_old = '''    state = CharacterStatEngine.setCurrentHp(state, KAI_ID, threshold)
    state = state.copy(metadata = state.metadata + ("combat.range" to CombatRuntime.RangeBand.CLOSE.name))
    val result = CombatRuntime.resolve(state, "SEARCH", "không thể quan sát SCP-173")
'''
neck_new = '''    state = CharacterStatEngine.setCurrentHp(state, KAI_ID, threshold)
    state = state.copy(metadata = state.metadata + mapOf(
      "combat.range" to CombatRuntime.RangeBand.CLOSE.name,
      "passive.devilTrigger.kai.cooldownTurns" to "5"
    ))
    val result = CombatRuntime.resolve(state, "SEARCH", "không thể quan sát SCP-173")
'''
if neck_old not in test:
    raise RuntimeError("SCP-173 compatibility finalizer could not find Neck Snap threshold regression")
test = test.replace(neck_old, neck_new, 1)
TEST.write_text(test, encoding="utf-8")

print("SCP-173 compatibility finalizer applied: existing Guilty Crown narration preserved and deterministic Concrete Body/Neck Snap regressions retained.")

runpy.run_path(str(ROOT / "patch-devil-trigger-scp-finalize.py"), run_name="__main__")

main = MAIN.read_text(encoding="utf-8")
old_image_mapping = '"scp_173".equals(entityKey) ? "173.png"'
new_image_mapping = '"scp_173".equals(entityKey) ? "SCP173.png"'
if new_image_mapping not in main:
    if old_image_mapping not in main:
        raise RuntimeError("SCP-173 image mapping anchor missing from finalized MainActivity")
    main = main.replace(old_image_mapping, new_image_mapping, 1)
MAIN.write_text(main, encoding="utf-8")

if new_image_mapping not in MAIN.read_text(encoding="utf-8"):
    raise RuntimeError("SCP-173 finalized runtime does not point to SCP173.png")
if not SCP_IMAGE.is_file() or SCP_IMAGE.stat().st_size <= 0:
    raise RuntimeError("SCP-173 display asset missing: android-apk/app/src/main/assets/entity/SCP173.png")
raw = SCP_IMAGE.read_bytes()
if raw[:8] != b'\x89PNG\r\n\x1a\n':
    raise RuntimeError("SCP173.png is not a valid PNG asset")
if b'data:image' in raw[:1024].lower() or b'base64,' in raw[:1024].lower():
    raise RuntimeError("SCP173.png must remain a raw PNG asset, not an embedded Data URI/Base64 payload")

print("SCP-173 display mapping finalized: file:///android_asset/entity/SCP173.png")

runpy.run_path(str(ROOT / "patch-entity-encounter-authority-sync.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-v1-1-69-balance.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-v1-1-71-kai-monster-balance.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-linear-sublevel-progression.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-loot-pity.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-entity-party-action-budget.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-jeff-jane-skills.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-hide-entity-action-debug.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-violet-warden-entity.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-violet-warden-compat.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-violet-warden-stun-finalize.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-entity-hp-floor-300.py"), run_name="__main__")
runpy.run_path(str(ROOT / "patch-kai-new-overlay.py"), run_name="__main__")

# Final skill balance runs after every previous combat/entity layer so the generated runtime cannot
# silently reintroduce Kai's handgun-era narration or erase Lucia's new AUTO skill. Party combat has
# already rewritten the generated catalog trigger wording, so adapt only this patch's expected Kai
# catalog anchors before executing it. Iris and Syvial catalog entries are not modified here.
skill_patch = ROOT / "patch-kai-lucia-skill-update.py"
skill_source = skill_patch.read_text(encoding="utf-8")
for old_text, new_text, label in (
    (
        's("The Last Requiem", "AUTO", "30% mỗi turn hợp lệ", "4 phát vào khớp vai',
        's("The Last Requiem", "AUTO", "30% mỗi lượt TẤN CÔNG hợp lệ", "4 phát vào khớp vai',
        "Last Requiem Party trigger anchor",
    ),
    (
        's("Silent Lullaby", "AUTO", "20% mỗi turn hợp lệ", "4 phát cùng điểm ngực',
        's("Silent Lullaby", "AUTO", "20% mỗi lượt TẤN CÔNG hợp lệ", "4 phát cùng điểm ngực',
        "Silent Lullaby Party trigger anchor",
    ),
    (
        's("Salvation", "AUTO", "20% mỗi turn hợp lệ", "Dịch chuyển ngắn theo vị trí súng',
        's("Salvation", "AUTO", "20% mỗi lượt TẤN CÔNG hợp lệ", "Dịch chuyển ngắn theo vị trí súng',
        "Salvation Party trigger anchor",
    ),
    (
        's("Quick Step", "AUTO", "30% mỗi turn hợp lệ", "+50 điểm % Evasion',
        's("Quick Step", "AUTO", "30% mỗi lượt TẤN CÔNG hợp lệ", "+50 điểm % Evasion',
        "Quick Step Party trigger anchor",
    ),
):
    if old_text not in skill_source:
        raise RuntimeError("Kai/Lucia final patch compatibility missing source anchor: " + label)
    skill_source = skill_source.replace(old_text, new_text, 1)
exec(compile(skill_source, str(skill_patch), "exec"), {"__name__": "__main__", "__file__": str(skill_patch)})
