from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"
KEYS = ("blackroot_sentinel", "sinew_strider", "hollow_grasper")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


combat = COMBAT.read_text(encoding="utf-8")
if "  // LIFEFORM_TRIO_R01\n" not in combat:
    combat = combat.replace(
        "object CombatRuntime {\n",
        "object CombatRuntime {\n  // LIFEFORM_TRIO_R01\n",
        1,
    )

constants_anchor = '  private const val DIEP_MINH_ULTIMATE_PERCENT = 5\n'
constants = '''  private const val DIEP_MINH_ULTIMATE_PERCENT = 5
  private const val LIFEFORM_BASE_MAX_HP = 24
  private const val LIFEFORM_BASE_ATTACK = 5
  private const val LIFEFORM_BASE_ARMOR = 1
  private const val LIFEFORM_BASE_AGGRESSION = 2
  private const val BLACKROOT_ROOTBIND_PROC_PERCENT = 30
  private const val SINEW_LONGSTEP_PROC_PERCENT = 25
  private const val HOLLOW_REKNIT_PROC_PERCENT = 20
  private const val HOLLOW_REKNIT_HEAL = 4
'''
combat = replace_once(combat, constants_anchor, constants, "Lifeform combat constants")

profiles_start = combat.find("  private val profiles = listOf(\n")
profiles_end = combat.find("  ).associateBy { it.key }\n", profiles_start)
if profiles_start < 0 or profiles_end < 0:
    raise RuntimeError("Combat profile list boundary missing")
profiles_block = combat[profiles_start:profiles_end]
if 'Profile("blackroot_sentinel"' not in profiles_block:
    insertion = '''    Profile("blackroot_sentinel", "Blackroot Sentinel", LIFEFORM_BASE_MAX_HP, LIFEFORM_BASE_ATTACK, LIFEFORM_BASE_ARMOR, LIFEFORM_BASE_AGGRESSION),
    Profile("sinew_strider", "Sinew Strider", LIFEFORM_BASE_MAX_HP, LIFEFORM_BASE_ATTACK, LIFEFORM_BASE_ARMOR, LIFEFORM_BASE_AGGRESSION),
    Profile("hollow_grasper", "Hollow Grasper", LIFEFORM_BASE_MAX_HP, LIFEFORM_BASE_ATTACK, LIFEFORM_BASE_ARMOR, LIFEFORM_BASE_AGGRESSION),
'''
    before_close = combat[:profiles_end]
    after_close = combat[profiles_end:]
    trimmed = before_close.rstrip("\n")
    if not trimmed.rstrip().endswith(","):
        last_newline = trimmed.rfind("\n")
        last_line = trimmed[last_newline + 1:]
        if "Profile(" not in last_line:
            raise RuntimeError("Final combat profile line not found")
        before_close = trimmed[:last_newline + 1] + last_line.rstrip() + ",\n"
    combat = before_close + insertion + after_close

proc_helper = r'''  private fun applyLifeformProc(c: Snapshot, log: MutableList<String>): Snapshot {
    return when (c.entityKey) {
      "blackroot_sentinel" -> {
        val procRoll = roll(c.copy(eventCounter = c.eventCounter + 211), 100)
        if (procRoll < BLACKROOT_ROOTBIND_PROC_PERCENT) {
          log += "Rootbind proc: Blackroot Sentinel khóa chân, giảm 15% tiến độ thoát và 1 Momentum."
          c.copy(
            escapeProgress = max(0, c.escapeProgress - 15),
            momentum = max(-3, c.momentum - 1)
          )
        } else c
      }
      "sinew_strider" -> {
        val procRoll = roll(c.copy(eventCounter = c.eventCounter + 223), 100)
        if (procRoll < SINEW_LONGSTEP_PROC_PERCENT) {
          val nextRange = when (c.range) {
            RangeBand.FAR -> RangeBand.NEAR
            RangeBand.NEAR -> RangeBand.CLOSE
            RangeBand.CLOSE -> RangeBand.CLOSE
          }
          val nextCover = when (c.cover) {
            Cover.HARD -> Cover.PARTIAL
            Cover.PARTIAL -> Cover.EXPOSED
            Cover.EXPOSED -> Cover.EXPOSED
          }
          log += "Longstep Rupture proc: Sinew Strider áp sát, phá một bậc Cover và xóa 1 Opening."
          c.copy(range = nextRange, cover = nextCover, opening = max(0, c.opening - 1))
        } else c
      }
      "hollow_grasper" -> {
        val procRoll = roll(c.copy(eventCounter = c.eventCounter + 239), 100)
        if (procRoll < HOLLOW_REKNIT_PROC_PERCENT && c.entityHp < c.entityMaxHp) {
          val healed = min(c.entityMaxHp, c.entityHp + HOLLOW_REKNIT_HEAL)
          log += "Reknit proc: Hollow Grasper tái kết mô, hồi \${healed - c.entityHp} HP và xóa 1 Opening."
          c.copy(
            entityHp = healed,
            entityCondition = condition(healed, c.entityMaxHp),
            opening = max(0, c.opening - 1)
          )
        } else c
      }
      else -> c
    }
  }

'''
encode_anchor = "  private fun encode(state: GameState, c: Snapshot): GameState {\n"
if "private fun applyLifeformProc(" not in combat:
    combat = replace_once(combat, encode_anchor, proc_helper + encode_anchor, "Lifeform proc helper")

regen_anchor = "    val entityHpBeforeRegen = c.entityHp\n"
if "    c = applyLifeformProc(c, log)\n" not in combat:
    combat = replace_once(
        combat,
        regen_anchor,
        "    c = applyLifeformProc(c, log)\n\n" + regen_anchor,
        "Lifeform proc resolution",
    )

for required in (
    "LIFEFORM_TRIO_R01",
    "LIFEFORM_BASE_MAX_HP = 24",
    "LIFEFORM_BASE_ATTACK = 5",
    "LIFEFORM_BASE_ARMOR = 1",
    "LIFEFORM_BASE_AGGRESSION = 2",
    'Profile("blackroot_sentinel", "Blackroot Sentinel"',
    'Profile("sinew_strider", "Sinew Strider"',
    'Profile("hollow_grasper", "Hollow Grasper"',
    "BLACKROOT_ROOTBIND_PROC_PERCENT = 30",
    "SINEW_LONGSTEP_PROC_PERCENT = 25",
    "HOLLOW_REKNIT_PROC_PERCENT = 20",
    "Rootbind proc:",
    "Longstep Rupture proc:",
    "Reknit proc:",
    "c = applyLifeformProc(c, log)",
):
    if required not in combat:
        raise RuntimeError("Lifeform combat contract missing: " + required)

COMBAT.write_text(combat, encoding="utf-8")


main = MAIN.read_text(encoding="utf-8")

roll_anchor = '    rolls.put("diepMinhEncounter", diepMinhRoll);\n'
roll_block = '''    rolls.put("diepMinhEncounter", diepMinhRoll);
    JSONObject lifeformRoll = thresholdRoll("lifeformEncounter", 10000, 200, entityEncounterAction && entityAllowed, " Lifeform trio 2%");
    rolls.put("lifeformEncounter", lifeformRoll);
    if (lifeformRoll.optBoolean("success", false)) {
      String[] lifeformPool = {"blackroot_sentinel","sinew_strider","hollow_grasper"};
      rolls.put("lifeformEntityKey", lifeformPool[GAME_RNG.nextInt(lifeformPool.length)]);
    }
'''
main = replace_once(main, roll_anchor, roll_block, "Lifeform independent 2 percent roll")

canonical_anchor = 'case "diep_minh":\n        return key;'
canonical_new = 'case "diep_minh": case "blackroot_sentinel": case "sinew_strider": case "hollow_grasper":\n        return key;'
main = replace_once(main, canonical_anchor, canonical_new, "Lifeform canonical keys")

name_anchor = '      case "diep_minh": name = "Diệp Minh"; break;\n'
name_block = '''      case "diep_minh": name = "Diệp Minh"; break;
      case "blackroot_sentinel": name = "Blackroot Sentinel"; break;
      case "sinew_strider": name = "Sinew Strider"; break;
      case "hollow_grasper": name = "Hollow Grasper"; break;
'''
main = replace_once(main, name_anchor, name_block, "Lifeform overlay names")

keys_marker = "var __entityKeys=["
keys_start = main.find(keys_marker)
keys_end = main.find("];\" +", keys_start)
if keys_start < 0 or keys_end < 0:
    raise RuntimeError("Entity JS key-list boundary missing")
keys_block = main[keys_start:keys_end]
if "'blackroot_sentinel'" not in keys_block:
    main = main[:keys_end] + ",'blackroot_sentinel','sinew_strider','hollow_grasper'" + main[keys_end:]

bounds_marker = "/* ENTITY_PRECOMPUTED_ALPHA_BOUNDS_R02 */var __entityVisualBounds={"
bounds_start = main.find(bounds_marker)
bounds_end = main.find("};\" +", bounds_start)
if bounds_start < 0 or bounds_end < 0:
    raise RuntimeError("Entity visual-bounds boundary missing")
bounds_block = main[bounds_start:bounds_end]
if "'blackroot_sentinel':" not in bounds_block:
    bounds = (
        ",'blackroot_sentinel':{left:0.037037,bottom:0.997619,visibleH:0.995238}"
        ",'sinew_strider':{left:0.045503,bottom:0.964286,visibleH:0.869643}"
        ",'hollow_grasper':{left:0.037037,bottom:0.997619,visibleH:0.966071}"
    )
    main = main[:bounds_end] + bounds + main[bounds_end:]

helper_start = main.find('  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {')
helper_end = main.find('\n  private JSONObject resolveEntityOverlay(String rawEntityKey) throws Exception {', helper_start)
if helper_start < 0 or helper_end < 0:
    raise RuntimeError("Final Entity encounter helper boundary missing")
helper = r'''  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {
    if (candidateState == null || rolls == null) return;
    String entityKey = rolls.optString("roamingEntityKey", "").trim();
    JSONObject boss = rolls.optJSONObject("diepMinhEncounter");
    if (boss != null && boss.optBoolean("success", false)) {
      entityKey = "diep_minh";
    } else {
      JSONObject lifeform = rolls.optJSONObject("lifeformEncounter");
      if (lifeform != null && lifeform.optBoolean("success", false)) {
        entityKey = rolls.optString("lifeformEntityKey", "").trim();
        if (entityKey.isEmpty()) return;
      } else {
        JSONObject normal = rolls.optJSONObject("entityEncounter");
        if (normal == null || !normal.optBoolean("success", false)) return;
        if (entityKey.isEmpty()) return;
      }
    }
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
main = main[:helper_start] + helper + main[helper_end:]

snapshot_old = '    else if (kind.equals("entity_encounter")) allowed = rollSuccess(rolls, "entityEncounter");\n'
snapshot_new = '    else if (kind.equals("entity_encounter")) allowed = rollSuccess(rolls, "entityEncounter") || rollSuccess(rolls, "diepMinhEncounter") || rollSuccess(rolls, "lifeformEncounter");\n'
if snapshot_new not in main:
    main = replace_once(main, snapshot_old, snapshot_new, "Lifeform snapshot authority")

context_old = '    boolean entity = actionEntity(action) || rollSuccess(rolls, "entityEncounter") ||\n'
context_new = '    boolean entity = actionEntity(action) || rollSuccess(rolls, "entityEncounter") || rollSuccess(rolls, "diepMinhEncounter") || rollSuccess(rolls, "lifeformEncounter") ||\n'
if context_new not in main:
    main = replace_once(main, context_old, context_new, "Lifeform knowledge-context gate")

prompt_anchor = "ENTITY ASSET LOCAL HARD LOCK:"
prompt_pos = main.find(prompt_anchor)
if prompt_pos < 0:
    raise RuntimeError("Entity local-asset prompt anchor missing")
if "LIFEFORM TRIO HARD LOCK:" not in main:
    line_end = main.find("\n", prompt_pos)
    if line_end < 0:
        raise RuntimeError("Entity local-asset prompt line boundary missing")
    prompt_line = (
        '      "LIFEFORM TRIO HARD LOCK: lifeformEncounter là một roll độc lập đúng 2% cho cả họ Lifeform, không phải 2% cho từng cá thể. '
        'Khi success=true, dùng đúng lifeformEntityKey đã roll: blackroot_sentinel = Blackroot Sentinel (Rootbind 30%), '
        'sinew_strider = Sinew Strider (Longstep Rupture 25%), hollow_grasper = Hollow Grasper (Reknit 20%). '
        'Ba Entity này không nằm trong shared roaming pool. " +\n'
    )
    main = main[:line_end + 1] + prompt_line + main[line_end + 1:]

for required in (
    'thresholdRoll("lifeformEncounter", 10000, 200, entityEncounterAction && entityAllowed',
    'rolls.put("lifeformEncounter", lifeformRoll)',
    'String[] lifeformPool = {"blackroot_sentinel","sinew_strider","hollow_grasper"}',
    'rolls.put("lifeformEntityKey"',
    'case "blackroot_sentinel":',
    'case "sinew_strider":',
    'case "hollow_grasper":',
    'name = "Blackroot Sentinel"',
    'name = "Sinew Strider"',
    'name = "Hollow Grasper"',
    "'blackroot_sentinel'",
    "'sinew_strider'",
    "'hollow_grasper'",
    'JSONObject lifeform = rolls.optJSONObject("lifeformEncounter")',
    'rollSuccess(rolls, "lifeformEncounter")',
    'rollSuccess(rolls, "diepMinhEncounter") || rollSuccess(rolls, "lifeformEncounter")',
    'LIFEFORM TRIO HARD LOCK:',
):
    if required not in main:
        raise RuntimeError("Lifeform Android contract missing: " + required)

pool_lines = [line for line in main.splitlines() if "String[] roamingPool =" in line]
if len(pool_lines) != 1:
    raise RuntimeError(f"Expected exactly one final roaming pool, found {len(pool_lines)}")
for key in KEYS:
    if key in pool_lines[0]:
        raise RuntimeError("Lifeform trio must remain outside shared roaming pool: " + key)

MAIN.write_text(main, encoding="utf-8")


test = TEST.read_text(encoding="utf-8")
new_tests = r'''
  @Test fun lifeformTrioUsesThirtyPercentHoundBaseBeforeSharedDurability() {
    for (key in listOf("blackroot_sentinel", "sinew_strider", "hollow_grasper")) {
      val active = CombatRuntime.active(CombatRuntime.start(GameState.initial(), key))!!
      assertEquals(54, active.entityMaxHp)
      assertEquals(54, active.entityHp)
    }
  }

  @Test fun eachLifeformProcCanTriggerDeterministically() {
    val expected = mapOf(
      "blackroot_sentinel" to "Rootbind proc",
      "sinew_strider" to "Longstep Rupture proc",
      "hollow_grasper" to "Reknit proc"
    )
    expected.forEach { (key, marker) ->
      var observed = false
      for (counter in 0..400) {
        var state = CombatRuntime.start(GameState.initial(), key)
        state = state.copy(metadata = state.metadata + mapOf(
          "combat.eventCounter" to counter.toString(),
          "combat.entityHp" to "40"
        ))
        val result = CombatRuntime.resolve(state, "SEARCH", "quan sát")
        if (result.reply.contains(marker)) {
          observed = true
          break
        }
      }
      assertTrue("$marker should be reachable for $key", observed)
    }
  }
'''
if "lifeformTrioUsesThirtyPercentHoundBaseBeforeSharedDurability" not in test:
    close = test.rfind("}\n")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest closing brace missing")
    test = test[:close] + new_tests + test[close:]

for required in (
    "lifeformTrioUsesThirtyPercentHoundBaseBeforeSharedDurability",
    "assertEquals(54, active.entityMaxHp)",
    "eachLifeformProcCanTriggerDeterministically",
    '"Rootbind proc"',
    '"Longstep Rupture proc"',
    '"Reknit proc"',
):
    if required not in test:
        raise RuntimeError("Lifeform test contract missing: " + required)

TEST.write_text(test, encoding="utf-8")


asset_dir = ROOT / "app/src/main/assets/entity"
for key in KEYS:
    asset = asset_dir / f"{key}.webp"
    if not asset.is_file() or asset.stat().st_size <= 0:
        raise RuntimeError("Missing Lifeform asset: " + asset.name)
    raw = asset.read_bytes()
    if len(raw) < 12 or raw[:4] != b"RIFF" or raw[8:12] != b"WEBP":
        raise RuntimeError("Lifeform asset is not WebP: " + asset.name)
    if int.from_bytes(raw[4:8], "little") + 8 != len(raw):
        raise RuntimeError("Lifeform WebP size header mismatch: " + asset.name)

print("Lifeform trio installed: one independent 2% family roll, Hound x0.3 base profiles, unique procs, local WebP overlays.")
