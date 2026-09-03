const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assets = path.join(__dirname, 'app/src/main/assets');
const context = {window:{}};
vm.runInNewContext(fs.readFileSync(path.join(assets, 'evidence-text.js'), 'utf8'), context);
const render = context.window.renderEvidenceText;

test('only resolved evidence in this GM entry is bold, including after save/load', () => {
  const entry = JSON.parse(JSON.stringify({role:'gm', text:'Thảm ẩm. Nền hơi dốc về một phía.', evidenceTexts:['Nền hơi dốc về một phía.']}));
  assert.equal(render(entry.text, entry), 'Thảm ẩm. <strong class="gm-evidence">Nền hơi dốc về một phía.</strong>');
  assert.equal(render(entry.text, {role:'gm', evidenceTexts:['cửa bí mật']}), entry.text);
  assert.equal(render(entry.text, {role:'player', evidenceTexts:entry.evidenceTexts}), entry.text);
  assert.equal(render(entry.text, {role:'gm'}), entry.text);
});

test('HTML and model markdown stay text; malformed evidence cannot create markup', () => {
  const text = '<img src=x onerror=alert(1)> **nước**';
  assert.equal(render(text, {role:'gm'}), '&lt;img src=x onerror=alert(1)&gt; **nước**');
  const result = render(text, {role:'gm', evidenceTexts:[null, {}, '', text]});
  assert.equal(result, '<strong class="gm-evidence">&lt;img src=x onerror=alert(1)&gt; **nước**</strong>');
});

test('overlapping and duplicate evidence produces valid, non-nested bold spans', () => {
  assert.equal(render('nền dốc; nền dốc', {role:'gm', evidenceTexts:['nền', 'nền dốc', 'nền dốc']}),
    '<strong class="gm-evidence">nền dốc</strong>; <strong class="gm-evidence">nền dốc</strong>');
});

test('actual RPG decorator preserves evidence bold across redraws and item overlaps', () => {
  class Node {
    constructor(tag='', text='') { this.tag=tag; this.value=text; this.children=[]; this.dataset={}; this.className=''; }
    get textContent() { return this.value + this.children.map(n=>n.textContent).join(''); }
    set textContent(value) { this.value=value; this.children=[]; }
    appendChild(node) { this.children.push(node); return node; }
    querySelectorAll() { return this.children.filter(n=>n.tag==='strong' && n.className==='gm-evidence'); }
  }
  const text = new Node('div');
  text.appendChild(new Node('', 'Bạn thấy '));
  const clue = text.appendChild(new Node('strong','mảnh giấy ghi số 4'));
  clue.className='gm-evidence';
  text.appendChild(new Node('', '.'));
  const article = {classList:{contains:()=>false}, querySelector:s=>s==='.text'?text:{textContent:'GAME MASTER'}};
  const dom = {
    state:{inventory:[{name:'mảnh giấy'}]},
    document:{querySelectorAll:()=>[article], getElementById:()=>null,
      createElement:tag=>new Node(tag), createTextNode:t=>new Node('',t), createDocumentFragment:()=>new Node('fragment')},
  };
  dom.window=dom;
  const html=fs.readFileSync(path.join(assets,'index.html'),'utf8');
  const script=[...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].find(m=>m[1].includes('window.__rpgCombatTextStyleV1'));
  assert.ok(script);
  vm.runInNewContext(script[1],dom);
  const fragment=text.children[0];
  assert.equal(fragment.children.filter(n=>n.tag==='strong').length,1);
  assert.equal(fragment.children.find(n=>n.tag==='strong').textContent,'mảnh giấy ghi số 4');
  assert.equal(text.textContent,'Bạn thấy mảnh giấy ghi số 4.');
  dom.decorateRpgText();
  assert.equal(text.children[0],fragment);
});

test('both writer paths and resolved-log projection use the generated contract', () => {
  const java=fs.readFileSync(path.join(__dirname,'app/src/main/java/com/rabpit/backroom/MainActivity.java'),'utf8');
  assert.match(java,/String prompt = GAMEPLAY_PROSE_RULE \+/);
  assert.match(java,/\n      GAMEPLAY_PROSE_RULE \+/);
  assert.match(java,/appendRegisteredNarrativeLog\(registeredState, action, registeredReply, registeredLevelResult.optJSONArray\("evidenceTexts"\)\)/);
  const html=fs.readFileSync(path.join(assets,'index.html'),'utf8');
  assert.ok(html.indexOf('src="evidence-text.js"') < html.indexOf('+renderEvidenceText(text,x)+'));
});
