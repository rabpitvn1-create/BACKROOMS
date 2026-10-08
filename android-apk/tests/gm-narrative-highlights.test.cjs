/* Run: node --test android-apk/tests/gm-narrative-highlights.test.cjs */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const src = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/gm-narrative-highlights.js'), 'utf8');
const browser = {window:{},document:{readyState:'loading',addEventListener(){}}};
vm.runInNewContext(src,browser,{filename:'gm-narrative-highlights.js'});
const {tokenize,termsFromState} = browser.window.BackroomGMHighlights;

test('marks only character, location and item',()=>{
  const terms = termsFromState({player:{name:'Cao Minh'},location:'Level 0',inventory:[{name:'Almond Water'}]});
  const line = 'Cao Minh tìm thấy Almond Water tại Level 0.';
  const parts = tokenize(line,terms);
  assert.equal(parts.map(t=>t.text).join(''),line);
  assert.deepEqual(Array.from(parts.filter(t=>t.kind),t=>t.kind),['character','item','location']);
});
test('does not match fragments of other words',()=>{
  const parts = tokenize('Level 01, Cao MinhXYZ và Greek Fireball.',[
    {name:'Level 0',kind:'location'},{name:'Cao Minh',kind:'character'},{name:'Greek Fire',kind:'item'}]);
  assert.equal(parts.filter(t=>t.kind).length,0);
});
test('longer valid term wins and keeps every character of GM prose',()=>{
  const line = 'Blackblood Armor & linked modules cùng Blackblood Armor.';
  const parts = tokenize(line,[{name:'Blackblood Armor',kind:'item'},{name:'Blackblood Armor & linked modules',kind:'item'}]);
  assert.equal(parts.map(t=>t.text).join(''),line);
  assert.deepEqual(Array.from(parts.filter(t=>t.kind),t=>t.text),['Blackblood Armor & linked modules','Blackblood Armor']);
});
test('unknown and HTML-like text remain literal',()=>{
  const line = '<script>alert("x")</script>\nGM vẫn kể chuyện.';
  const parts = tokenize(line,[{name:'Cao Minh',kind:'character'}]);
  assert.equal(parts.map(t=>t.text).join(''),line);
  assert.equal(parts.filter(t=>t.kind).length,0);
});
test('uses names from live state without creating game objects',()=>{
  const terms = termsFromState({level:{number:4},party:[{name:'Hứa Tiểu Lan'}],inventory:[{name:'Chìa khóa đồng'}]});
  const parts = tokenize('Hứa Tiểu Lan đến Level 4 với Chìa khóa đồng.',terms);
  assert.deepEqual(Array.from(parts.filter(t=>t.kind),t=>t.kind),['character','location','item']);
});
