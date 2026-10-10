"""Port 1.1.93a damage presentation and publish per-hit, authoritative feedback."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / 'app/src/main/java/com/rabpit/backroom/core'
ASSETS = ROOT / 'app/src/main/assets'


def replace(text, old, new):
    if text.count(old) != 1:
        raise RuntimeError('Combat feedback anchor changed: ' + old[:100])
    return text.replace(old, new, 1)


p = CORE / 'CombatRuntime.kt'
s = p.read_text()
s = replace(s, '  data class Resolution(\n', '''  // COMBAT_FEEDBACK_1193A: transient output; never persisted in GameState/save metadata.
  enum class FeedbackStatus(val label: String) {
    BLEED("Chảy máu"), POISON("Trúng độc"), STUN("Choáng"),
    ARMOR("Xuyên giáp"), DISORIENT("Mất phương hướng")
  }

  data class DamageFeedback(
    val target: String,
    val amount: Int,
    val critical: Boolean = false,
    val status: FeedbackStatus? = null,
    val actorId: String = KAI_ID,
    val phase: String = "actor"
  ) {
    fun toJson(): JSONObject = JSONObject().apply {
      put("target", target); put("actorId", actorId); put("text", "-$amount HP")
      put("critical", critical); put("flash", true); put("phase", phase)
      status?.let { put("status", it.label) }
    }
  }

  private fun feedbackStatus(code: String): FeedbackStatus? = when (code) {
    "bleed" -> FeedbackStatus.BLEED
    "poison" -> FeedbackStatus.POISON
    "stun" -> FeedbackStatus.STUN
    "armorBreak" -> FeedbackStatus.ARMOR
    "disorient" -> FeedbackStatus.DISORIENT
    else -> null
  }

  data class Resolution(
''')
s = replace(s, '    val escaped: Boolean = false\n', '    val escaped: Boolean = false,\n    val feedback: List<DamageFeedback> = emptyList()\n')
s = replace(s, '    val log = mutableListOf<String>()\n', '''    val log = mutableListOf<String>()
    val feedback = mutableListOf<DamageFeedback>()
    fun recordFeedback(target: String, before: Int, after: Int,
                       critical: Boolean = false, status: FeedbackStatus? = null, phase: String = "actor") {
      val amount = (before - after).coerceAtLeast(0)
      if (amount > 0) feedback += DamageFeedback(target, amount, critical, status, phase = phase)
    }
''')
# Record each existing HP mutation without changing any combat arithmetic or ordering.
# Classification comes from its runtime branch, never from AI/GM text or net turn HP.
pattern = re.compile(r'(?m)^(\s*)c = c\.copy\(\s*(entityHp|playerHp) = ([A-Za-z][\w.]*)')
counts = {'damage': 0, 'regen': 0, 'critical': 0, 'status': 0}

def instrument(match):
    indent, field, after = match.groups()
    if after == 'entityHpAfterRegen':
        counts['regen'] += 1
        return match.group(0)
    tail = s[match.end():]
    if 'log +=' not in tail:
        raise RuntimeError('Unclassified HP mutation')
    following_log = tail.split('log +=', 1)[1].split('\n', 1)[0]
    options = ''
    if 'Đòn đánh trúng' in following_log:
        options = ', critical = critical'; counts['critical'] += 1
    elif '${spec[0]}' in following_log:
        options = ', status = feedbackStatus(spec[3])'; counts['status'] += 1
    else:
        status = next((kind for token, kind in [
            ('Bleeding từ', 'BLEED'), ('Huyết Ma Tứ Liên tự động', 'BLEED'),
            ('Ma Tâm Trấn Hồn tự động', 'STUN'), ('Honeycomb Fire', 'ARMOR'),
            ('Charged Shot', 'ARMOR'), ('Rift Sever', 'ARMOR'),
            ('Crimson Guillotine', 'BLEED'), ('Lucifer Breaker', 'STUN'),
            ('Spatial Dominion', 'DISORIENT')
        ] if token in following_log), None)
        if status:
            options = ', status = FeedbackStatus.' + status; counts['status'] += 1
    if field == 'playerHp' or 'Dead Angle' in following_log or 'Counterphase' in following_log:
        options += ', phase = "entity"'
    counts['damage'] += 1
    target = 'entity' if field == 'entityHp' else 'actor'
    return f'{indent}recordFeedback("{target}", c.{field}, {after}{options})\n' + match.group(0)

s = pattern.sub(instrument, s)
if counts != {'damage': 26, 'regen': 1, 'critical': 1, 'status': 12}:
    raise RuntimeError('Combat HP mutation coverage changed: ' + str(counts))
lines = s.splitlines()
returns = 0
for i, line in enumerate(lines):
    if line.lstrip().startswith('return Resolution(') and ', true,' in line:
        pos = line.rfind(')')
        lines[i] = line[:pos] + ', feedback = feedback.toList()' + line[pos:]
        returns += 1
if returns != 6:
    raise RuntimeError('Combat return coverage changed: ' + str(returns))
p.write_text('\n'.join(lines) + '\n')

p = CORE / 'GameCoreFacade.kt'
s = p.read_text()
s = replace(s, '    val output = JSONObject(legacy.toString())\n',
            '    val output = JSONObject(legacy.toString())\n    output.remove("combatFeedback") // Transient events must never be echoed from a legacy/client state.\n')
combat_reply_block = '''    val combatReply = if (coreReward > 0)
      resolution.reply + " +" + coreReward + " Core."
    else resolution.reply
'''
s = replace(s, combat_reply_block, combat_reply_block + '''    output.put("combatFeedback", JSONObject().apply {
      put("id", "${activeCombat.encounterId}:${activeCombat.eventCounter + 1}")
      put("encounterId", activeCombat.encounterId)
      put("summary", combatReply)
      put("events", JSONArray().apply { resolution.feedback.forEach { put(it.toJson()) } })
    })
''')
direct_log = '''    if (action == PokerDiceCore.DIRECT_COMBAT_ACTION) {
      val log = output.optJSONArray("log") ?: JSONArray().also { output.put("log", it) }
      log.put(JSONObject().put("role", "gm").put("text", combatReply))
    } else {
      appendLog(output, action, combatReply)
    }
'''
s = replace(s, direct_log, '''    if (action != PokerDiceCore.DIRECT_COMBAT_ACTION) {
      appendLog(output, action, combatReply)
    }
''')
p.write_text(s)

p = ASSETS / 'index.html'
s = p.read_text()
s, n = re.subn(r'<script>\s*/\* SNAPSHOT_COMBAT_DAMAGE_RUNTIME_R01 \*/.*?</script>', '', s, count=1, flags=re.S)
if n != 1:
    raise RuntimeError('Old aggregate-damage hook missing')
s = re.sub(r'/\* SNAPSHOT_COMBAT_DAMAGE_R01 \*/.*?(?=</style>)', '', s, count=1, flags=re.S)
s = replace(s, '  function show(){modal.hidden=false;', '  function show(){if(window.__combatFeedbackBusy)return;modal.hidden=false;')
s = replace(s, '  function ensureDirectCombatDice(){\n', '  function ensureDirectCombatDice(){\n    if(window.__combatFeedbackBusy){hide();return;}\n')
s = replace(s, '</head>', '<link rel="stylesheet" href="combat-feedback-1193a.css">\n</head>')
s = replace(s, '</body>', '<script src="combat-feedback-1193a.js"></script>\n</body>')
p.write_text(s)
print('Full 1.1.93a damage feedback port applied:', counts)
