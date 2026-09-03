"""Compact ordinary combat ATTACK output into authoritative player-facing event lines."""
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
PARTY = CORE / "PartyTurnCombat.kt"
AP_TEST = TESTS / "PartyTurnCombatApSkillAuthorityTest.kt"


def require(path: Path) -> str:
    if not path.is_file():
        raise RuntimeError(f"Combat summary finalizer missing generated file: {path.name}")
    return path.read_text(encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


party = require(PARTY)

reply_old = '''    var next = engine.state
    var reply = if (skillName == null) playerFacingReply(engine.reply)
      else compactSkillReply(before, engine, actor, skillName)
    val passiveLines = passiveActivationLines(before, engine.state)
    if (passiveLines.isNotEmpty()) {
      reply = (listOf(reply) + passiveLines).filter { it.isNotBlank() }.joinToString(" ")
    }
'''
reply_new = '''    var next = engine.state
    val compactAttack = skillName == null && !locked && displayAction.contains(" tấn công ")
    var reply = when {
      compactAttack -> compactAttackReply(before, engine, actor)
      skillName == null -> playerFacingReply(engine.reply)
      else -> compactSkillReply(before, engine, actor, skillName)
    }
    val passiveLines = passiveActivationLines(before, engine.state)
    if (!compactAttack && passiveLines.isNotEmpty()) {
      reply = (listOf(reply) + passiveLines).filter { it.isNotBlank() }.joinToString(" ")
    }
'''
party = replace_once(party, reply_old, reply_new, "Compact ordinary ATTACK selection")

ap_old = '''      if (skillName == null) {
        val nextActor = currentActor(next)
        val apLine = when {
          apDelta > 0 -> "AP của Party tăng từ $oldAp lên $newAp/$MAX_AP."
          apDelta < 0 -> "AP của Party giảm từ $oldAp xuống $newAp/$MAX_AP."
          else -> ""
        }
        val turnLine = nextActor?.let { "Lượt ${it.name} · AP $newAp/$MAX_AP." }.orEmpty()
        reply = listOf(reply, apLine, turnLine).filter { it.isNotBlank() }.joinToString(" ")
      }
'''
ap_new = '''      if (skillName == null) {
        if (compactAttack) {
          reply = listOf(reply, "• Action Point : $newAp/$MAX_AP.")
            .filter { it.isNotBlank() }
            .joinToString("\\n")
        } else {
          val nextActor = currentActor(next)
          val apLine = when {
            apDelta > 0 -> "AP của Party tăng từ $oldAp lên $newAp/$MAX_AP."
            apDelta < 0 -> "AP của Party giảm từ $oldAp xuống $newAp/$MAX_AP."
            else -> ""
          }
          val turnLine = nextActor?.let { "Lượt ${it.name} · AP $newAp/$MAX_AP." }.orEmpty()
          reply = listOf(reply, apLine, turnLine).filter { it.isNotBlank() }.joinToString(" ")
        }
      }
'''
party = replace_once(party, ap_old, ap_new, "Compact ATTACK AP footer")

passive_old = '      if (oldTurns <= 0 && newTurns > 0) "${passive.name} kích hoạt: [${passive.skill}] • hiệu lực ${passive.turns} lượt."\n'
passive_new = '      if (oldTurns <= 0 && newTurns > 0) "${passive.name} kích hoạt: [DEVIL TRIGGER] trong ${passive.turns} lượt."\n'
party = replace_once(party, passive_old, passive_new, "Compact passive display label")

helper_anchor = '  private fun compactSkillStatuses(skillName: String, after: GameState, entityDamage: Int, targetAlive: Boolean): List<String> {\n'
helpers = r'''  private fun bulletCombatLines(lines: List<String>): String = lines
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .joinToString("\n") { "• $it" }

  private fun passiveStartLines(before: GameState, after: GameState): List<String> {
    data class Passive(val skill: String, val key: String, val turns: Int)
    val passives = listOf(
      Passive("DEVIL TRIGGER — Sparda Core", "passive.devilTrigger.kai.activeTurns", 3),
      Passive("DEVIL TRIGGER — Lucifer Core", "passive.devilTrigger.syvial.activeTurns", 3),
    )
    return passives.mapNotNull { passive ->
      val oldTurns = before.metadata[passive.key]?.toIntOrNull() ?: 0
      val newTurns = after.metadata[passive.key]?.toIntOrNull() ?: 0
      if (oldTurns <= 0 && newTurns > 0) "${passive.skill} kích hoạt trong ${passive.turns} lượt."
      else null
    }
  }

  private fun compactAttackReply(before: GameState, engine: CombatRuntime.Resolution, actor: Actor): String {
    val targetBefore = CombatRuntime.active(before) ?: return bulletCombatLines(listOf(playerFacingReply(engine.reply)))
    val targetAfter = CombatRuntime.active(engine.state)
    val actorName = combatDisplayName(actor.id, actor.name)
    val entityName = targetBefore.entityName
    val raw = engine.reply
    val lines = mutableListOf<String>()

    lines += passiveStartLines(before, engine.state)

    val escapedEntity = Regex.escape(entityName)
    val regenMatch = Regex("""$escapedEntity hồi \\+(\\d+) HP""").find(raw)
    val regen = regenMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    val directHit = Regex("""Đòn đánh trúng $escapedEntity: -(\\d+) HP""").find(raw)
    when {
      directHit != null -> lines += "$actorName tấn công trúng $entityName: -${directHit.groupValues[1]} HP."
      raw.contains("$entityName né đòn") || raw.contains("Đòn đánh trượt") ->
        lines += "$actorName tấn công trượt $entityName."
      else -> {
        val hpAfter = when {
          engine.entityDestroyed -> 0
          targetAfter != null -> targetAfter.entityHp
          else -> targetBefore.entityHp
        }
        val inferredDamage = (targetBefore.entityHp - hpAfter + regen).coerceAtLeast(0)
        if (inferredDamage > 0) lines += "$actorName tấn công trúng $entityName: -$inferredDamage HP."
        else lines += "$actorName tấn công $entityName."
      }
    }

    if (engine.entityDestroyed) {
      lines += "$entityName đã bị tiêu diệt."
    } else {
      val entityHit = Regex("""$escapedEntity phản công: [^.]*?-(\\d+) HP""").find(raw)
      when {
        entityHit != null -> lines += "$entityName tấn công trúng $actorName: -${entityHit.groupValues[1]} HP."
        raw.contains("$entityName không xuyên được") || raw.contains("$entityName hụt đòn") ->
          lines += "$entityName tấn công trượt."
      }
      if (regen > 0) lines += "$entityName hồi +$regen HP."
    }

    lines += passiveActivationLines(before, engine.state)
    return bulletCombatLines(lines)
  }

'''
if 'private fun compactAttackReply(' not in party:
    party = replace_once(party, helper_anchor, helpers + helper_anchor, "Compact ATTACK helpers")

for marker in (
    'val compactAttack = skillName == null && !locked && displayAction.contains(" tấn công ")',
    'private fun compactAttackReply(',
    'private fun passiveStartLines(',
    'joinToString("\\n") { "• $it" }',
    '"• Action Point : $newAp/$MAX_AP."',
    '"${passive.name} kích hoạt: [DEVIL TRIGGER] trong ${passive.turns} lượt."',
):
    if marker not in party:
        raise RuntimeError("Compact combat summary contract missing: " + marker)
PARTY.write_text(party, encoding="utf-8")

ap_test = require(AP_TEST)
extra_test = r'''
  @Test fun baseAttackReplyUsesCompactBulletEventsAndSingleApFooter() {
    val state = kaiCombat()
    val result = PartyTurnCombat.resolve(state, "EXECUTE", "PARTY_TURN_ATK", "compact-base-attack")
    assertTrue(result.committed)
    val lines = result.reply.lines().filter { it.isNotBlank() }
    assertTrue(result.reply, lines.isNotEmpty())
    assertTrue(result.reply, lines.all { it.startsWith("• ") })
    assertTrue(result.reply, lines.any { it.contains("Kai tấn công") })
    assertTrue(result.reply, lines.any { it == "• Action Point : 1/7." })
    for (forbidden in listOf("HÀNH ĐỘNG CỦA ĐỘI", "evasion", "giành lại áp lực", "AP của Party tăng", "Lượt Kai", "Lượt Lucia", "Lượt Iris", "Lượt Syvial")) {
      assertFalse(result.reply, result.reply.contains(forbidden))
    }
  }
'''
if 'baseAttackReplyUsesCompactBulletEventsAndSingleApFooter' not in ap_test:
    close = ap_test.rfind("\n}")
    if close < 0:
        raise RuntimeError("AP skill authority test class end missing for compact ATTACK regression")
    ap_test = ap_test[:close] + extra_test + ap_test[close:]
AP_TEST.write_text(ap_test, encoding="utf-8")

print("Compact ordinary ATTACK summary finalized without changing combat authority.")
runpy.run_path(str(ROOT / "ci_verify_combat_presentation.py"), run_name="__main__")
