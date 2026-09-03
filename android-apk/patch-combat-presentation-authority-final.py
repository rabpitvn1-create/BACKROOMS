"""Finalize compact combat presentation without changing authoritative combat math."""
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
PARTY = CORE / "PartyTurnCombat.kt"
COMBAT = CORE / "CombatRuntime.kt"
HTML = ROOT / "app/src/main/assets/index.html"
AP_TEST = TESTS / "PartyTurnCombatApSkillAuthorityTest.kt"


def require(path: Path) -> str:
    if not path.is_file():
        raise RuntimeError(f"Combat presentation authority missing generated file: {path.name}")
    return path.read_text(encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# Presentation-only cleanup. Gates, damage, status state, RNG and AP stay untouched.
combat = require(COMBAT)
manual_skill_names = (
    "The Last Requiem", "Silent Lullaby", "Salvation", "Quick Step", "Guilty Crown Override",
    "Twosome Time", "Rain Storm", "Honeycomb Fire", "Charged Shot", "Dead Angle",
    "ARGUS // Thousandfold Execution", "Rift Sever", "Crimson Guillotine", "Lucifer Breaker",
    "Counterphase", "Spatial Dominion", "GodKiller Override // Twenty-Four Severance",
    "M4A1 Full Auto Burst", "Too Young To Die",
)
cleaned = []
for line in combat.splitlines(keepends=True):
    if "log +=" in line and any(name in line for name in manual_skill_names):
        line = line.replace("tự động kích hoạt", "kích hoạt").replace("tự kích hoạt", "kích hoạt")
    cleaned.append(line)
COMBAT.write_text("".join(cleaned), encoding="utf-8")

party = require(PARTY)
party = replace_once(
    party,
    '''          displayAction = display,
          locked = false
        )''',
    '''          displayAction = display,
          locked = false,
          skillName = skill.name
        )''',
    "Selected skill forwarded to presentation",
)
party = replace_once(
    party,
    '''    displayAction: String,
    locked: Boolean
  ): CombatRuntime.Resolution {''',
    '''    displayAction: String,
    locked: Boolean,
    skillName: String? = null
  ): CombatRuntime.Resolution {''',
    "Presentation skill parameter",
)
party = replace_once(
    party,
    '''    var next = engine.state
    var reply = playerFacingReply(engine.reply)
''',
    '''    var next = engine.state
    var reply = if (skillName == null) playerFacingReply(engine.reply)
      else compactSkillReply(before, engine, actor, skillName)
    val passiveLines = passiveActivationLines(before, engine.state)
    if (passiveLines.isNotEmpty()) {
      reply = (listOf(reply) + passiveLines).filter { it.isNotBlank() }.joinToString(" ")
    }
''',
    "Compact combat reply selection",
)
party = replace_once(
    party,
    '''      val nextActor = currentActor(next)
      val apLine = when {
        apDelta > 0 -> "AP của Party tăng từ $oldAp lên $newAp/$MAX_AP."
        apDelta < 0 -> "AP của Party giảm từ $oldAp xuống $newAp/$MAX_AP."
        else -> ""
      }
      val turnLine = nextActor?.let { "Lượt ${it.name} · AP $newAp/$MAX_AP." }.orEmpty()
      reply = listOf(reply, apLine, turnLine).filter { it.isNotBlank() }.joinToString(" ")
''',
    '''      if (skillName == null) {
        val nextActor = currentActor(next)
        val apLine = when {
          apDelta > 0 -> "AP của Party tăng từ $oldAp lên $newAp/$MAX_AP."
          apDelta < 0 -> "AP của Party giảm từ $oldAp xuống $newAp/$MAX_AP."
          else -> ""
        }
        val turnLine = nextActor?.let { "Lượt ${it.name} · AP $newAp/$MAX_AP." }.orEmpty()
        reply = listOf(reply, apLine, turnLine).filter { it.isNotBlank() }.joinToString(" ")
      }
''',
    "Hide AP and next-turn prose from skill result",
)

display_start = party.find('  fun displayAction(state: GameState, action: String): String {\n')
display_end = party.find('\n\n  fun playerFacingReply(reply: String): String {', display_start)
if display_start < 0 or display_end < 0:
    raise RuntimeError("Party displayAction boundary missing")
party = party[:display_start] + '''  fun displayAction(state: GameState, action: String): String {
    val actor = currentActor(state)
    val actorName = actor?.let { combatDisplayName(it.id, it.name) } ?: "Party"
    val entityName = CombatRuntime.active(state)?.entityName ?: "Entity"
    return when {
      action == "PARTY_TURN_ATK" -> "$actorName tấn công $entityName."
      action == "PARTY_TURN_DEFEND" -> "$actorName vào thế phòng thủ trước $entityName."
      action == "PARTY_TURN_RUN" -> "$actorName tìm đường rút khỏi giao tranh với $entityName."
      action.startsWith("PARTY_TURN_SKILL::") -> {
        val selected = action.removePrefix("PARTY_TURN_SKILL::").trim()
        "$actorName sử dụng: [$selected] lên $entityName."
      }
      else -> "$actorName gửi một lệnh chiến đấu không hợp lệ."
    }
  }''' + party[display_end:]

reply_anchor = '    var text = reply\n'
reply_cleanup = '''    text = Regex("""DEVIL TRIGGER — (?:Sparda|Lucifer) Core kích hoạt trong \\d+ turn\\.\\s*""").replace(text, "")
    text = Regex("""DEVIL TRIGGER — (?:Sparda|Lucifer) Core hồi [^.]*\\.\\s*""").replace(text, "")
'''
if reply_cleanup not in party:
    party = replace_once(party, reply_anchor, reply_anchor + reply_cleanup, "Compact passive prose cleanup")

helper_anchor = '  private fun withoutActorContext(result: CombatRuntime.Resolution): CombatRuntime.Resolution {\n'
helpers = r'''  private fun combatDisplayName(characterId: String, fallback: String): String = when (characterId) {
    KAI_ID -> "Kai"
    IRIS_ID -> "Iris"
    SYVIAL_ID -> "Syvial"
    LUCIA_ID -> "Lucia"
    AN_NHIEN_ID -> "An Nhiên"
    else -> fallback
  }

  private fun compactSkillStatuses(skillName: String, after: GameState, entityDamage: Int, targetAlive: Boolean): List<String> {
    val metadata = after.metadata
    fun active(key: String): Boolean = (metadata[key]?.toIntOrNull() ?: 0) > 0
    if (!targetAlive && skillName != "Quick Step") return emptyList()
    return when (skillName) {
      "The Last Requiem" -> if (active("combat.kaiBleedTurns")) listOf("Bleed") else emptyList()
      "Silent Lullaby" -> if (entityDamage > 0) listOf("Stun") else emptyList()
      "Quick Step" -> if (active("combat.kaiQuickStepTurns")) listOf("Evasion") else emptyList()
      "Honeycomb Fire" -> if (active("combat.irisArmorBreakTurns")) listOf("Armor Break") else emptyList()
      "ARGUS // Thousandfold Execution" -> if (active("combat.irisExposedTurns")) listOf("Fully Exposed") else emptyList()
      "Crimson Guillotine" -> if (active("combat.syvialBleedTurns")) listOf("Bleed") else emptyList()
      "Lucifer Breaker" -> if (entityDamage > 0) listOf("Stun") else emptyList()
      "Spatial Dominion" -> if (active("combat.syvialDisorientTurns")) listOf("Disoriented") else emptyList()
      else -> emptyList()
    }
  }

  private fun compactSkillReply(before: GameState, engine: CombatRuntime.Resolution, actor: Actor, skillName: String): String {
    val targetBefore = CombatRuntime.active(before)
    val targetAfter = CombatRuntime.active(engine.state)
    val actorName = combatDisplayName(actor.id, actor.name)
    val selfSkill = skillName == "Quick Step"
    val remainingHp = when {
      targetBefore == null -> 0
      engine.entityDestroyed -> 0
      targetAfter != null -> targetAfter.entityHp
      else -> targetBefore.entityHp
    }
    val entityDamage = targetBefore?.let { (it.entityHp - remainingHp).coerceAtLeast(0) } ?: 0
    val targetAlive = targetAfter != null && targetAfter.entityHp > 0
    val pieces = mutableListOf<String>()
    if (selfSkill || targetBefore == null) pieces += "$actorName sử dụng: [$skillName]"
    else {
      pieces += "$actorName sử dụng: [$skillName] lên ${targetBefore.entityName}"
      pieces += "${targetBefore.entityName} -$entityDamage HP"
    }
    val statuses = compactSkillStatuses(skillName, engine.state, entityDamage, targetAlive)
    if (statuses.isNotEmpty()) pieces += "Kích hoạt " + statuses.joinToString(" ") { "[$it]" }
    before.characters.forEach { (id, character) ->
      val oldHp = character.vitalState.currentHp
      val newHp = engine.state.characters[id]?.vitalState?.currentHp ?: oldHp
      val lost = (oldHp - newHp).coerceAtLeast(0)
      if (lost > 0) pieces += "${combatDisplayName(id, character.name)} -$lost HP"
    }
    return pieces.joinToString(" • ")
  }

  private fun passiveActivationLines(before: GameState, after: GameState): List<String> {
    data class Passive(val name: String, val skill: String, val key: String, val turns: Int)
    val passives = listOf(
      Passive("Kai", "DEVIL TRIGGER — Sparda Core", "passive.devilTrigger.kai.activeTurns", 3),
      Passive("Syvial", "DEVIL TRIGGER — Lucifer Core", "passive.devilTrigger.syvial.activeTurns", 3),
    )
    return passives.mapNotNull { passive ->
      val oldTurns = before.metadata[passive.key]?.toIntOrNull() ?: 0
      val newTurns = after.metadata[passive.key]?.toIntOrNull() ?: 0
      if (oldTurns <= 0 && newTurns > 0) "${passive.name} kích hoạt: [${passive.skill}] • hiệu lực ${passive.turns} lượt."
      else null
    }
  }

'''
if 'private fun compactSkillReply(' not in party:
    party = replace_once(party, helper_anchor, helpers + helper_anchor, "Compact presentation helpers")

html = require(HTML)
old_pending = "if(String(action||'').indexOf('PARTY_TURN_SKILL::')===0)return actor+' yêu cầu dùng '+String(action).slice('PARTY_TURN_SKILL::'.length)+' lên '+entity+'.';"
new_pending = "if(String(action||'').indexOf('PARTY_TURN_SKILL::')===0)return actor+' sử dụng: ['+String(action).slice('PARTY_TURN_SKILL::'.length)+'] lên '+entity+'.';"
html = replace_once(html, old_pending, new_pending, "Compact pending skill action")
HTML.write_text(html, encoding="utf-8")

for marker in (
    'skillName: String? = null', 'compactSkillReply(before, engine, actor, skillName)',
    'private fun compactSkillStatuses(', 'private fun passiveActivationLines(',
    'Passive("Kai", "DEVIL TRIGGER — Sparda Core"', 'if (skillName == null) {',
    '"$actorName sử dụng: [$selected] lên $entityName."',
):
    if marker not in party:
        raise RuntimeError("Compact combat presentation contract missing: " + marker)
if "yêu cầu dùng" in party:
    raise RuntimeError("Legacy manual-skill request wording survived")
PARTY.write_text(party, encoding="utf-8")

ap_test = require(AP_TEST)
extra_tests = r'''
  @Test fun manualUltimateReplyIsCompactAndRejectsLegacyProcNarration() {
    val gained = gainAp(kaiCombat(), 3)
    val state = gained.copy(metadata = gained.metadata + mapOf(
      "combat.entityHp" to "100000", "combat.entityMaxHp" to "100000",
      "passive.devilTrigger.kai.cooldownTurns" to "5"
    ))
    val beforeHp = CombatRuntime.active(state)!!.entityHp
    val result = PartyTurnCombat.resolve(state, "EXECUTE", "PARTY_TURN_SKILL::Guilty Crown Override", "compact-gco")
    assertTrue(result.committed)
    val afterHp = CombatRuntime.active(result.state)!!.entityHp
    val damage = beforeHp - afterHp
    assertTrue(result.reply, result.reply.startsWith("Kai sử dụng: [Guilty Crown Override] lên Diệp Minh • Diệp Minh -$damage HP"))
    for (forbidden in listOf("tự động", "Accuracy", "%", "AP của Party", "Lượt ", "phát trúng")) {
      assertFalse(result.reply, result.reply.contains(forbidden))
    }
  }

  @Test fun manualStatusSkillShowsOnlyCompactAuthoritativeStatus() {
    val gained = gainAp(kaiCombat(), 2)
    val state = gained.copy(metadata = gained.metadata + mapOf(
      "combat.entityHp" to "100000", "combat.entityMaxHp" to "100000",
      "passive.devilTrigger.kai.cooldownTurns" to "5"
    ))
    val result = PartyTurnCombat.resolve(state, "EXECUTE", "PARTY_TURN_SKILL::The Last Requiem", "compact-bleed")
    assertTrue(result.committed)
    assertTrue(result.reply, result.reply.contains("Kích hoạt [Bleed]"))
    assertFalse(result.reply, result.reply.contains("tự động"))
    assertFalse(result.reply, result.reply.contains("%"))
  }
'''
if 'manualUltimateReplyIsCompactAndRejectsLegacyProcNarration' not in ap_test:
    close = ap_test.rfind("\n}")
    if close < 0:
        raise RuntimeError("AP skill authority test class end missing")
    ap_test = ap_test[:close] + extra_tests + ap_test[close:]
AP_TEST.write_text(ap_test, encoding="utf-8")

print("Combat presentation authority finalized without gameplay-math changes.")
runpy.run_path(str(ROOT / "ci_verify_combat_presentation.py"), run_name="__main__")
