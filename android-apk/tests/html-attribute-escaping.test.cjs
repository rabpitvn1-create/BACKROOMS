const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');

const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');
const helper=html.match(/function esc\(s\)\{[^\r\n]+\}/)?.[0];
if(!helper)throw new Error('Main game HTML escape helper missing');
const scope={document:{createElement(){
 let text='';
 return {
  set textContent(value){text=String(value)},
  get innerHTML(){return text.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')}
 };
}}};
const escape=vm.runInNewContext('('+helper.replace('function esc','function')+')',scope);

test('HTML helper escapes attribute quotes without changing visible text',()=>{
 assert.equal(escape('Trác Lâm "Đội trưởng"'), 'Trác Lâm &quot;Đội trưởng&quot;');
 assert.equal(escape("Seth's equipment"), 'Seth&#39;s equipment');
 assert.equal(escape('<img src=x>&'), '&lt;img src=x&gt;&amp;');
 assert.equal(escape(0),'0');
});

test('malicious quote cannot terminate a generated double-quoted attribute',()=>{
 const input='" onfocus="alert(1)';
 const escaped=escape(input);
 assert.equal(escaped,'&quot; onfocus=&quot;alert(1)');
 assert.equal(escaped.includes('"'),false);
});
