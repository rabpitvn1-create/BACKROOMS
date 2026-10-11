import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';

const html = readFileSync(fileURLToPath(new URL('./app/src/main/assets/index.html', import.meta.url)), 'utf8');
assert.ok(!html.includes('SEMANTIC_COMPATIBILITY_TERMS'), 'no compatibility dictionary');
const start = html.indexOf('const SEMANTIC_KINDS=');
const end = html.indexOf('\nfunction levelHeader(', start);
assert.ok(start >= 0 && end > start, 'semantic functions present');
const ctx = vm.createContext({});
vm.runInContext(html.slice(start, end), ctx, { timeout: 1000 });
const state = {
  player: { name: 'Cao Minh' },
  partyDetails: { members: [{ name: 'Iris', inventory: [{ name: 'Băng gạc' }], skills: [{ name: 'Huyết Ma Tứ Liên' }] }] },
  combat: { entityName: 'Hound' }
};
ctx.active = state;
function labels(script) {
  return JSON.parse(vm.runInContext('JSON.stringify(' + script + ')', ctx, { timeout: 1000 }));
}
const active = labels("semanticResolve('Iris dùng Băng gạc, đánh Hound bằng Huyết Ma Tứ Liên',null,active).map(x=>({name:'Iris dùng Băng gạc, đánh Hound bằng Huyết Ma Tứ Liên'.slice(x.start,x.end),kind:x.kind}))");
for (const [name, kind] of [['Iris','character'],['Băng gạc','item'],['Hound','entity'],['Huyết Ma Tứ Liên','skill']]) {
  assert.ok(active.some(x=>x.name===name&&x.kind===kind), 'missing active '+name+' '+kind);
}
assert.equal(labels("semanticResolve('Hound',null,null)").length, 0, 'unencountered entity is not in a universal compatibility list');
ctx.before = { log: [] };
ctx.current = { ...state, log: [{role:'gm',text:'Iris nhận ra Hound',semanticVersion:2,semanticSpans:[{start:0,end:4,kind:'item'}]}] };
const next = labels('semanticPrepareNextState(before, JSON.stringify(current))');
assert.equal(next.log[0].semanticVersion,3);
assert.equal(next.log[0].semanticSpans[0].kind,'character');
console.log('Active-state semantic recognition verified');
