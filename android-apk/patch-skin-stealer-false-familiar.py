from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"
KNOWLEDGE = ROOT / "app/src/main/assets/knowledge/knowledge_db.json"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


combat = COMBAT.read_text(encoding="utf-8")

# Random hourly Entity skill addition: Skin-Stealer had no dedicated combat skill
# in the current runtime/canon skill locks. False Familiar is intentionally a
# pressure tool instead of hard control: every fourth Entity turn it can exploit
# an unsafe player commitment for +12 percentage points to direct-action Accuracy.
# READ / EVADE / MOVE / GUARD explicitly counter it, so it cannot stun-lock,
# disable escape, or remove tactical counterplay.
constants_anchor = '  private const val JANE_VENGEFUL_COOLDOWN = 4\n'
constants_block = '''  private const val JANE_VENGEFUL_COOLDOWN = 4
  private const val SKIN_STEALER_KEY = "skin-stealer"
  private const val SKIN_STEALER_FALSE_FAMILIAR_INTERVAL_TURNS = 4
  private const val SKIN_STEALER_FALSE_FAMILIAR_ACCURACY_BONUS = 12
'''
combat = replace_once(combat, constants_anchor, constants_block, "Skin-Stealer False Familiar constants")

# The final runtime has more than one partyDefense block because boss-local AI
# (notably Violet Warden) owns a separate branch. Anchor False Familiar to the
# ordinary Entity action-budget block so only the shared roaming response gets it.
ordinary_prefix_variants = (
    '''      val entityTargets = entityDirectActionTargets(resolvedState)
      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityTargets.size}; one direct action per completed Party actor turn."
''',
    '''      val entityDirectTargets = entityDirectActionTargets(resolvedState)
      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityDirectTargets.size}; one direct action per completed Party actor turn."
''',
    '''      val entityTargets = entityCombatActionTargets(resolvedState)
      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityTargets.size}; one direct action per ACTIVE combatant, no repeated target."
''',
)
skill_state = '''      val skinStealerFalseFamiliarActive = c.entityKey == SKIN_STEALER_KEY &&
        c.eventCounter % SKIN_STEALER_FALSE_FAMILIAR_INTERVAL_TURNS == 0 &&
        intent in setOf(Intent.ATTACK, Intent.ESCAPE, Intent.OTHER)
      if (skinStealerFalseFamiliarActive) {
        log += "False Familiar: Skin-Stealer bắt chước cử chỉ/giọng người để dụ Party phản ứng sai; +$SKIN_STEALER_FALSE_FAMILIAR_ACCURACY_BONUS điểm % Accuracy trong Entity turn này. READ/EVADE/MOVE/GUARD vô hiệu kỹ năng."
      }
'''
if 'val skinStealerFalseFamiliarActive =' not in combat:
    matches = [prefix for prefix in ordinary_prefix_variants if prefix in combat]
    if len(matches) != 1:
        raise RuntimeError(f"Skin-Stealer ordinary response anchor: expected exactly 1 variant, found {len(matches)}")
    prefix = matches[0]
    combat = combat.replace(prefix, prefix + skill_state, 1)

killer_bonus_old = '''        val killerAccuracyBonus =
          (if (hunterMarked) JANE_HUNTER_MARK_ACCURACY_BONUS else 0) +
          (if (jeffSilentStalker && entityTargets.size == 1) JEFF_SILENT_STALKER_SOLO_ACCURACY_BONUS else 0)
'''
killer_bonus_new = '''        val killerAccuracyBonus =
          (if (hunterMarked) JANE_HUNTER_MARK_ACCURACY_BONUS else 0) +
          (if (jeffSilentStalker && entityTargets.size == 1) JEFF_SILENT_STALKER_SOLO_ACCURACY_BONUS else 0) +
          (if (skinStealerFalseFamiliarActive) SKIN_STEALER_FALSE_FAMILIAR_ACCURACY_BONUS else 0)
'''
combat = replace_once(combat, killer_bonus_old, killer_bonus_new, "Skin-Stealer False Familiar accuracy bonus")

for marker in (
    'private const val SKIN_STEALER_KEY = "skin-stealer"',
    'SKIN_STEALER_FALSE_FAMILIAR_INTERVAL_TURNS = 4',
    'SKIN_STEALER_FALSE_FAMILIAR_ACCURACY_BONUS = 12',
    'val skinStealerFalseFamiliarActive =',
    'intent in setOf(Intent.ATTACK, Intent.ESCAPE, Intent.OTHER)',
    'False Familiar: Skin-Stealer',
    '(if (skinStealerFalseFamiliarActive) SKIN_STEALER_FALSE_FAMILIAR_ACCURACY_BONUS else 0)',
):
    if marker not in combat:
        raise RuntimeError("Skin-Stealer False Familiar runtime contract missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")


test = TEST.read_text(encoding="utf-8")
if 'skinStealerFalseFamiliarTriggersOnlyOnUnsafeFourthTurnIntent' not in test:
    close = test.rfind("\n}")
    if close < 0:
        raise RuntimeError("CombatRuntimeTest closing brace missing")
    regression = r'''

  @Test fun skinStealerFalseFamiliarTriggersOnlyOnUnsafeFourthTurnIntent() {
    var unsafe = CombatRuntime.start(GameState.initial(), "skin-stealer")
    unsafe = unsafe.copy(metadata = unsafe.metadata + ("combat.eventCounter" to "3"))
    val unsafeResult = CombatRuntime.resolve(unsafe, "ATTACK", "bắn Skin-Stealer")
    assertTrue(unsafeResult.reply, unsafeResult.reply.contains("False Familiar: Skin-Stealer"))
    assertTrue(unsafeResult.reply, unsafeResult.reply.contains("+12 điểm % Accuracy"))

    var safe = CombatRuntime.start(GameState.initial(), "skin-stealer")
    safe = safe.copy(metadata = safe.metadata + ("combat.eventCounter" to "3"))
    val safeResult = CombatRuntime.resolve(safe, "EVADE", "né và đổi góc")
    assertFalse(safeResult.reply, safeResult.reply.contains("False Familiar: Skin-Stealer"))
  }
'''
    test = test[:close] + regression + test[close:]
TEST.write_text(test, encoding="utf-8")


knowledge = json.loads(KNOWLEDGE.read_text(encoding="utf-8"))
records = knowledge.get("records")
if not isinstance(records, list):
    raise RuntimeError("knowledge_db.json records array missing")
record_id = "ENTITY.SKIN_STEALER.FALSE_FAMILIAR"
if not any(item.get("id") == record_id for item in records if isinstance(item, dict)):
    records.append({
        "id": record_id,
        "domain": "ENTITY",
        "kind": "combat-skill-lock",
        "text": "Skin-Stealer gameplay skill lock: False Familiar is an active pressure skill. Every 4th Entity combat turn, if the player commits to ATTACK, ESCAPE, or OTHER, Skin-Stealer exploits copied human voice/behavior to gain +12 percentage points Accuracy for that Entity turn. READ, EVADE, MOVE, or GUARD prevent activation. The skill causes no direct damage, stun, hard-lock, or escape-progress loss by itself.",
        "source": {"document": "hourly Entity skill automation", "anchor": "Skin-Stealer / False Familiar"},
        "authority": "GAMEPLAY_SKILL_LOCK",
        "mutability": "MUTABLE_WITH_EXPLICIT_RETCON",
        "priority": 8,
        "tags": ["entity", "skin-stealer", "skill", "false familiar", "combat", "counterplay"],
        "references": ["ENTITY.GLOBAL_HARD_LOCK"],
        "affordances": ["direct_threat"]
    })
    KNOWLEDGE.write_text(json.dumps(knowledge, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

print("Skin-Stealer False Familiar installed: 4-turn active pressure skill with explicit defensive counterplay and regression coverage.")
