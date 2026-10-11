import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';

const html = readFileSync(fileURLToPath(new URL('./app/src/main/assets/index.html', import.meta.url)), 'utf8');
const functionText = html.split('\n').find(line => line.includes('function madGodSetEquipped(){'));
assert.ok(functionText, 'MadGod equipped state must be implemented');
const scope = vm.createContext({ state: {} });
vm.runInContext(functionText, scope, { timeout: 1000 });
const equipped = () => vm.runInContext('madGodSetEquipped()', scope, { timeout: 1000 });

assert.equal(equipped(), false, 'no equipment must not activate MadGod');
scope.state = { flags: { madGod: { spawned: true } } };
assert.equal(equipped(), false, 'presentation flags must not spoof equipment');
scope.state = { equipment: { weapon: { id: 'cao_minh:huyet-ma-kiem' } } };
assert.equal(equipped(), false, 'normal weapon remains normal');
scope.state = { equipment: { weapon: { id: 'madgod:set' } } };
assert.equal(equipped(), true, 'Core equipment projection activates MadGod avatar and skills');
scope.state = { equipment: { armor: { id: 'madgod:set' } } };
assert.equal(equipped(), false, 'only active weapon slot counts');
console.log('MadGod UI follows authoritative equipment projection');

const java = readFileSync(fileURLToPath(new URL('./app/src/main/java/com/rabpit/backroom/MainActivity.java', import.meta.url)), 'utf8');
assert.ok(java.includes('Cheat `/madgod` chỉ kích hoạt Bound Forever EquipmentState'), 'prompt must describe equipment, not inventory');
assert.ok(!java.includes('thêm MadGod trực tiếp vào Inventory'), 'obsolete inventory cheat promise removed');
assert.ok(!java.includes('MadGod discovery tự nhiên'), 'search must not unlock MadGod set');
