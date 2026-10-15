const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');

const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');
const from=html.indexOf('function renderSemanticText(');
const to=html.indexOf('\n\nfunction levelHeader',from);
assert.ok(from>=0&&to>from,'GM story renderer must be present');
const escapeHtml=text=>String(text||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
const renderers=vm.runInNewContext(
  '(function(esc,normalizedSemanticSpans){'+html.slice(from,to)+';return {renderGmStory,renderSemanticText};})',
)(escapeHtml,entry=>entry.semanticSpans||[]);

test('a GM reply has one narrative block and retains semantic highlighting',()=>{
  const entry={role:'gm',text:'Cao Minh bước vào bóng tối.\n\nHắn dừng lại.',semanticSpans:[{start:0,end:8,kind:'character'}]};
  const result=renderers.renderGmStory(entry);
  assert.match(result,/^<div class='gm-narrative'>/);
  assert.match(result,/gm-semantic-character/);
  assert.equal((result.match(/gm-narrative/g)||[]).length,1);
  assert.match(result,/Hắn dừng lại\./);
});

test('opening and turn headings are outside the drop-cap narrative',()=>{
  for(const heading of ['MỞ ĐẦU','LƯỢT 1']){
    const prefix=heading+'\n\n';
    const entry={role:'gm',text:prefix+'Cao Minh nhìn lên.',semanticSpans:[{start:prefix.length,end:prefix.length+8,kind:'character'}]};
    const result=renderers.renderGmStory(entry);
    assert.ok(result.startsWith("<div class='gm-story-heading'>"+heading+"</div><div class='gm-narrative'>"));
    assert.match(result,/gm-semantic-character/);
    assert.doesNotMatch(result,/gm-narrative'>LƯỢT/);
  }
});

test('only normal GM prose receives the CSS initial, not alerts or player actions',()=>{
  assert.match(html,/\.log \.message\.gm \.gm-narrative::first-letter\{/);
  assert.match(html,/float:left;/);
  assert.match(html,/x\.role==="player"\|\|w\?renderSemanticText\(x\):renderGmStory\(x\)/);
  assert.match(html,/gm\.className="message gm pending"/);
  assert.match(html,/row\.className="message gm warning"/);
});

test('story text is escaped rather than treated as HTML',()=>{
  const entry={role:'gm',text:'<Nguy hiểm> & bóng tối',semanticSpans:[]};
  const result=renderers.renderGmStory(entry);
  assert.match(result,/&lt;Nguy hiểm&gt; &amp; bóng tối/);
});
