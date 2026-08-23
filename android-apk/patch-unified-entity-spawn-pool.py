from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
INDEX = ROOT / "app/src/main/assets/index.html"
HEALTHBAR = ROOT / "patch-character-healthbar.py"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


def schedule_after_healthbar() -> bool:
    # patch-progression invokes this script before the health/status stack. Those later patches still
    # expect the pre-unification anchors, so schedule the real unification at the very end instead.
    # Diệp Minh must then run once more after that final unification because the unified-pool pass
    # rewrites forceEntityEncounterFlag and would otherwise erase the boss-priority encounter gate.
    html = INDEX.read_text(encoding="utf-8")
    if 'id="characterHpFill"' in html:
        return False
    healthbar = HEALTHBAR.read_text(encoding="utf-8")
    marker = 'runpy.run_path(str(ROOT / "patch-unified-entity-spawn-pool.py"), run_name="__main__")'
    boss_marker = 'runpy.run_path(str(ROOT / "patch-diep-minh-boss.py"), run_name="__main__")'
    if marker not in healthbar:
        healthbar = healthbar.rstrip() + (
            '\n\n# Final Entity authority pass. Run after status/equipment/visual-state patches so their anchors remain intact.\n'
            + marker + '\n'
            + '# Final unique-boss authority must follow the unified pool because that pass rewrites the encounter helper.\n'
            + boss_marker + '\n'
        )
        HEALTHBAR.write_text(healthbar, encoding="utf-8")
    elif boss_marker not in healthbar:
        healthbar = healthbar.rstrip() + (
            '\n# Final unique-boss authority must follow the unified pool because that pass rewrites the encounter helper.\n'
            + boss_marker + '\n'
        )
        HEALTHBAR.write_text(healthbar, encoding="utf-8")
    print("Unified Entity spawn pool scheduled after the final health/status/visual patch stack; Diệp Minh scheduled immediately after it.")
    return True


if not schedule_after_healthbar():
    text = MAIN.read_text(encoding="utf-8")

    # Jeff and Jane are Entity choices, not extra encounter dice. One successful Entity roll may
    # produce exactly one canonical roaming key, so concurrent independent rolls can never overwrite
    # one another or create hidden second encounters.
    old_pool = '      String[] roamingPool = {"hound","clump","duller","deathmoth","hostile_faceling","false_puddle","paintings","smiler","skin-stealer","predatory_window","biological_pipeline","wretch","cable_mimic","the_beast_of_level_5","hotel_corpse_lure","slenderman"};\n'
    new_pool = '      String[] roamingPool = {"hound","clump","duller","deathmoth","hostile_faceling","false_puddle","paintings","smiler","skin-stealer","predatory_window","biological_pipeline","wretch","cable_mimic","the_beast_of_level_5","hotel_corpse_lure","jeff_the_killer","jane_the_killer","slenderman"};\n'
    if new_pool not in text:
        text = replace_once(text, old_pool, new_pool, "shared Entity roaming pool")

    for label in ("jeffEncounter", "janeEncounter"):
        lines = text.splitlines(keepends=True)
        matches = [line for line in lines if f'rolls.put("{label}"' in line]
        if len(matches) != 1:
            raise RuntimeError(f"{label} roll removal: expected exactly 1 line, found {len(matches)}")
        text = "".join(line for line in lines if f'rolls.put("{label}"' not in line)

    # Any remaining authorization that used the old unique roll now derives from the single selected
    # roamingEntityKey. Existing flags remain readable for save compatibility, but cannot create a new
    # Jeff/Jane encounter without the shared Entity roll selecting that key.
    text = text.replace(
        'rollSuccess(rolls, "jeffEncounter")',
        '(rollSuccess(rolls, "entityEncounter") && "jeff_the_killer".equals(rolls.optString("roamingEntityKey", "")))'
    )
    text = text.replace(
        'rollSuccess(rolls, "janeEncounter")',
        '(rollSuccess(rolls, "entityEncounter") && "jane_the_killer".equals(rolls.optString("roamingEntityKey", "")))'
    )

    # The final overlay bridge must never overwrite the selected normal Entity with a second unique roll.
    # Preserve Pressure Combat startup so the selected key immediately owns an authoritative combat session.
    helper_start = text.find('  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {')
    helper_end = text.find('\n  private JSONObject resolveEntityOverlay(String rawEntityKey) throws Exception {', helper_start)
    if helper_start < 0 or helper_end < 0:
        raise RuntimeError("forceEntityEncounterFlag boundary missing")
    unified_helper = r'''  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {
    if (candidateState == null || rolls == null) return;
    JSONObject normal = rolls.optJSONObject("entityEncounter");
    if (normal == null || !normal.optBoolean("success", false)) return;
    String entityKey = rolls.optString("roamingEntityKey", "").trim();
    if (entityKey.isEmpty()) return;
    JSONObject flags = candidateState.optJSONObject("flags");
    if (flags == null) {
      flags = new JSONObject();
      candidateState.put("flags", flags);
    }
    String canonicalKey = normalizedEntityKey(entityKey);
    flags.put("entityEncounterKey", canonicalKey);
    requireGameCore().startCombatState(candidateState.toString(), canonicalKey);
  }
'''
    text = text[:helper_start] + unified_helper + text[helper_end:]

    # Visual-state-sync V3 already made active CombatRuntime the sole source of Entity pixels. Keep
    # that stronger rule: stale entityEncounterKey / Jeff / Jane flags cannot resurrect an overlay.
    combat_visual = "function activeEntityKey(){var c=state&&state.combat;if(!c||c.active!==true)return '';return normalizeEntityKey(c.entityKey);}"
    if combat_visual not in text:
        raise RuntimeError("CombatRuntime visual authority missing after visual-state sync")

    # Rewrite temporary Step-1 prompt language so the GM cannot reason about removed rolls.
    text = text.replace(
        'Jeff the Killer và Jane the Killer tạm giữ roll độc lập riêng ở bước hiện tại nhưng dùng key jeff_the_killer và jane_the_killer.',
        'Jeff the Killer và Jane the Killer nằm trong cùng LOCAL ROAMING POOL và chỉ xuất hiện khi roamingEntityKey chọn đúng canonical key jeff_the_killer hoặc jane_the_killer.'
    )
    text = text.replace(
        'SEARCH không được khởi tạo encounter Entity mới và entityEncounter/jeffEncounter/janeEncounter phải ineligible;',
        'SEARCH không được khởi tạo encounter Entity mới và entityEncounter phải ineligible;'
    )

    lines = []
    for line in text.splitlines(keepends=True):
        if '"JEFF THE KILLER HARD LOCK:' in line:
            lines.append('            "JEFF THE KILLER HARD LOCK: Jeff là roaming Entity trong pool chung. Không dùng roll riêng cho Jeff; chỉ khi entityEncounter success=true và roamingEntityKey=jeff_the_killer mới được khởi tạo encounter Jeff mới. Jeff chỉ săn con người, không phải đồng minh hay NPC trung lập. " +\n')
        elif '"ROAMING KILLER HARD LOCK:' in line:
            lines.append('      "ROAMING KILLER HARD LOCK: Jeff the Killer và Jane the Killer dùng cùng entityEncounter và cùng roamingEntityKey với mọi Entity khác. Mỗi entityEncounter thành công chỉ chọn đúng một canonical Entity key; không có roll Jeff/Jane độc lập và không được tạo encounter thứ hai trong cùng lượt. " +\n')
        else:
            lines.append(line)
    text = "".join(lines)

    # The old snapshot condition becomes redundant after unique-roll replacement; normalize it to one
    # source of truth so later patches cannot mistake Jeff/Jane for separate encounter channels.
    old_snapshot = '    else if (kind.equals("entity_encounter")) allowed = rollSuccess(rolls, "entityEncounter") || (rollSuccess(rolls, "entityEncounter") && "jeff_the_killer".equals(rolls.optString("roamingEntityKey", ""))) || (rollSuccess(rolls, "entityEncounter") && "jane_the_killer".equals(rolls.optString("roamingEntityKey", "")));\n'
    new_snapshot = '    else if (kind.equals("entity_encounter")) allowed = rollSuccess(rolls, "entityEncounter");\n'
    if old_snapshot in text:
        text = text.replace(old_snapshot, new_snapshot, 1)

    if "jeffEncounter" in text or "janeEncounter" in text:
        raise RuntimeError("Independent Jeff/Jane encounter channel remains in final MainActivity")
    for marker in (
        '"hotel_corpse_lure","jeff_the_killer","jane_the_killer","slenderman"',
        'rolls.put("roamingEntityKey"',
        'String entityKey = rolls.optString("roamingEntityKey", "").trim();',
        'requireGameCore().startCombatState(candidateState.toString(), canonicalKey);',
        combat_visual,
        'ROAMING KILLER HARD LOCK: Jeff the Killer và Jane the Killer dùng cùng entityEncounter',
    ):
        if marker not in text:
            raise RuntimeError("Unified Entity pool contract missing: " + marker)

    MAIN.write_text(text, encoding="utf-8")

    # Visual-state-sync V3 already performs targeted persistent cleanup inside processCombat after
    # all HP/regen transformations. Reuse that established implementation rather than rewriting the
    # same method a second time and fighting the patch chain over anchors.
    facade = FACADE.read_text(encoding="utf-8")
    for marker in (
        'private fun normalizeVisualPresence(state: GameState): GameState',
        'val resolvedEntityKey = CombatRuntime.active(current)?.entityKey.orEmpty()',
        'flags.put("entityEncounterKey", "")',
        '"jeff_the_killer" -> flags.optJSONObject("jeff")?.put("present", false)',
        '"jane_the_killer" -> flags.optJSONObject("jane")?.put("present", false)',
        'next = next.copy(world = next.world + ("flagsJson" to flags.toString()))',
    ):
        if marker not in facade:
            raise RuntimeError("Visual-state persistent combat cleanup contract missing: " + marker)

    print("Unified Entity spawn pool installed after final visual/status patches: Jeff/Jane share entityEncounter + roamingEntityKey; existing CombatRuntime cleanup remains authoritative.")
