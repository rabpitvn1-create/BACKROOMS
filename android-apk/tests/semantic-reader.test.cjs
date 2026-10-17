const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');
function context(){const ctx={};vm.createContext(ctx);vm.runInContext(html.slice(html.indexOf('const SEMANTIC_KINDS='),html.indexOf('\nfunction levelHeader(')),ctx);return ctx}
test('sentence pronouns and capitalized ordinary words never become special names',()=>{
 const c=context();c.text='Hắn đứng lại quan sát. Nàng nhìn quanh. Nó bước tới. Giữa mê cung, hắn dừng lại. Người đàn ông Hắn nói.';
 assert.equal(vm.runInContext('semanticResolve(text,null,null).length',c),0);
});
test('confirmed names, equipment and skills retain exact spans, without contextual prefix expansion',()=>{
 const c=context();c.s={player:{name:'Cao Minh'},partyDetails:{members:[{name:'Iris',equipmentItems:[{name:'Huyết Ma Kiếm'}],skills:[{name:'Huyết Ma Tứ Liên'}]}]},combat:{entityName:'Hound'}};
 c.text='Hắn nhìn Cao Minh. Iris rút thanh kiếm Huyết Ma Kiếm, đánh Hound bằng Huyết Ma Tứ Liên.';
 const labels=JSON.parse(vm.runInContext('JSON.stringify(semanticResolve(text,null,s).map(x=>text.slice(x.start,x.end)))',c));
 assert.deepEqual(labels,['Cao Minh','Iris','Huyết Ma Kiếm','Hound','Huyết Ma Tứ Liên']);
});
test('old derived highlights are recomputed instead of keeping a bold pronoun',()=>{
 const c=context();c.s={log:[{role:'gm',text:'Hắn đứng lại.',semanticVersion:3,semanticSpans:[{start:0,end:3,kind:'character'}]}]};
 const result=JSON.parse(vm.runInContext('JSON.stringify(semanticPrepareNextState(null,JSON.stringify(s)))',c));
 assert.equal(result.log[0].semanticVersion,4);assert.equal(result.log[0].semanticSpans.length,0);
});
