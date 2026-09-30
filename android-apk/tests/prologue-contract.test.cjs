const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');

const assets=path.join(__dirname,'..','app','src','main','assets');
const script=fs.readFileSync(path.join(assets,'prologue.js'),'utf8');
const index=fs.readFileSync(path.join(assets,'index.html'),'utf8');

test('standalone prologue restores the full Cao Minh opening without Story Core',()=>{
  const sandbox={window:{}};
  vm.runInNewContext(script,sandbox,{filename:'prologue.js'});
  const text=sandbox.window.BACKROOM_PROLOGUE;
  assert.equal(typeof text,'string');
  assert.ok(text.length>9000,'prologue was unexpectedly truncated');
  assert.match(text,/^PROLOGUE — NƠI KHÔNG CÓ TÊN/);
  assert.match(text,/Ma Sơn\. Không có đại chiến\. Không có thiên kiếp\./);
  assert.match(text,/Rượu ngon không thể phí\./);
  assert.match(text,/Với một nơi keo kiệt câu trả lời như thế này, một điều cũng đủ đáng giá\.$/);
  assert.doesNotMatch(script,/StoryCore|StoryRepository|state\.story/);
});

test('new game displays prologue before entering the free GM loop',()=>{
  assert.match(index,/<script src="prologue\.js"><\/script>/);
  assert.match(index,/const prologueText=window\.BACKROOM_PROLOGUE\|\|/);
  assert.match(index,/\{role:"gm",text:prologueText\}/);
  assert.doesNotMatch(index,/gmBootMessage|state\.story|StoryCore/);
});
