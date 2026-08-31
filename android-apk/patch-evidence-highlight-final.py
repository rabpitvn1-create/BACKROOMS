from pathlib import Path

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
INDEX = ROOT / "app/src/main/assets/index.html"
MARKER = "EVIDENCE_HIGHLIGHT_V1"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


facade = FACADE.read_text(encoding="utf-8")
if 'outputFlags.put("evidenceHighlights", highlightArray)' not in facade:
    anchor = '''    val output = syncLegacy(legacy, result.state, incrementTurn = true)
    val reply = result.reply ?: if (result.progressed) "Môi trường đã thay đổi." else "Không có tiến triển mới."
    appendLog(output, action, reply)
'''
    replacement = '''    val output = syncLegacy(legacy, result.state, incrementTurn = true)
    val reply = result.reply ?: if (result.progressed) "Môi trường đã thay đổi." else "Không có tiến triển mới."

    // Only evidence that GenericLevelRuntime actually surfaced may reach this player-facing ledger.
    // Hidden/undiscovered evidence never enters result.evidenceIds and therefore cannot leak here.
    val evidenceHighlights = linkedSetOf<String>()
    legacy.optJSONObject("flags")?.optJSONArray("evidenceHighlights")?.let { existing ->
      for (index in 0 until existing.length()) {
        existing.optString(index, "").trim().takeIf(String::isNotEmpty)?.let(evidenceHighlights::add)
      }
    }
    if (result.evidenceIds.isNotEmpty()) {
      val instanceReplies = result.state.levelInstance?.replies.orEmpty()
      val definitionReplies = levelRegistry.require(levelId).replies
      result.evidenceIds.sorted().forEach { evidenceId ->
        val text = instanceReplies["evidence:$evidenceId"] ?: definitionReplies["evidence:$evidenceId"]
        text?.trim()?.takeIf(String::isNotEmpty)?.let(evidenceHighlights::add)
      }
    }
    val highlightArray = JSONArray()
    evidenceHighlights.toList().takeLast(256).forEach(highlightArray::put)
    val outputFlags = output.optJSONObject("flags") ?: JSONObject().also { output.put("flags", it) }
    outputFlags.put("evidenceHighlights", highlightArray)
    appendLog(output, action, reply)
'''
    facade = replace_once(facade, anchor, replacement, "registered evidence highlight ledger")
    FACADE.write_text(facade, encoding="utf-8")

html = INDEX.read_text(encoding="utf-8")
if MARKER not in html:
    if html.count("</body>") != 1:
        raise RuntimeError("evidence highlight UI expected exactly one closing body tag")
    payload = r'''
<style id="evidenceHighlightStyle">
/* EVIDENCE_HIGHLIGHT_V1 */
#log .message.rpg-evidence-message .text{
  border-left:3px solid #f4c95d;
  padding-left:.65em;
  background:rgba(244,201,93,.08);
}
#log .rpg-evidence-badge{
  display:inline-block;
  margin:.15rem 0 .35rem 0;
  padding:.18rem .48rem;
  border:1px solid rgba(244,201,93,.72);
  border-radius:4px;
  color:#ffe08a;
  background:rgba(244,201,93,.12);
  font-size:.72em;
  font-weight:800;
  letter-spacing:.12em;
  line-height:1.2;
}
</style>
<script>
(function(){
  if(window.__evidenceHighlightV1)return;
  window.__evidenceHighlightV1=true;
  var decorating=false;

  function currentState(){
    try{return typeof state!=='undefined'&&state?state:null}catch(_){return null}
  }
  function normalizeEvidenceText(value){
    return String(value||'').replace(/\s+/g,' ').trim().toLocaleLowerCase('vi-VN');
  }
  function evidenceTexts(){
    var current=currentState(),flags=current&&current.flags;
    var values=flags&&Array.isArray(flags.evidenceHighlights)?flags.evidenceHighlights:[];
    var seen=new Set(),out=[];
    values.forEach(function(value){
      var normalized=normalizeEvidenceText(value);
      if(!normalized||seen.has(normalized))return;
      seen.add(normalized);out.push(normalized);
    });
    return out;
  }
  function decorateEvidence(){
    if(decorating)return;
    decorating=true;
    try{
      var evidence=evidenceTexts();
      if(!evidence.length)return;
      document.querySelectorAll('#log .message').forEach(function(article){
        if(article.classList.contains('player')||article.classList.contains('rpg-evidence-message'))return;
        var textEl=article.querySelector('.text');
        if(!textEl)return;
        var text=normalizeEvidenceText(textEl.textContent);
        if(!text||!evidence.some(function(item){return text.indexOf(item)!==-1}))return;
        article.classList.add('rpg-evidence-message');
        if(!article.querySelector('.rpg-evidence-badge')){
          var badge=document.createElement('span');
          badge.className='rpg-evidence-badge';
          badge.textContent='BẰNG CHỨNG';
          textEl.parentNode.insertBefore(badge,textEl);
        }
      });
    }finally{decorating=false}
  }

  var log=document.getElementById('log');
  if(log)new MutationObserver(function(){if(!decorating)requestAnimationFrame(decorateEvidence)}).observe(log,{childList:true,subtree:true});
  window.decorateEvidence=decorateEvidence;
  decorateEvidence();
})();
</script>
'''
    html = html.replace("</body>", payload + "\n</body>", 1)
    INDEX.write_text(html, encoding="utf-8")

final_facade = FACADE.read_text(encoding="utf-8")
final_html = INDEX.read_text(encoding="utf-8")
for marker in (
    'result.evidenceIds.isNotEmpty()',
    'outputFlags.put("evidenceHighlights", highlightArray)',
    'takeLast(256)',
):
    if marker not in final_facade:
        raise RuntimeError("evidence highlight facade contract missing: " + marker)
for marker in (
    MARKER,
    'flags.evidenceHighlights',
    'rpg-evidence-message',
    'rpg-evidence-badge',
    'BẰNG CHỨNG',
):
    if marker not in final_html:
        raise RuntimeError("evidence highlight UI contract missing: " + marker)

print("Evidence highlight applied: only Core-surfaced evidence is persisted and visually marked in the gameplay log.")
