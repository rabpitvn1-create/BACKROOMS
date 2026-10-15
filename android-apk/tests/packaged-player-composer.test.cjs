const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {spawnSync}=require('node:child_process');
const source=path.join(__dirname,'../app/src/main/assets');
const verifier=path.join(__dirname,'../verify-player-composer.mjs');

function fixture(t){
  const assets=fs.mkdtempSync(path.join(os.tmpdir(),'packaged-composer-'));
  t.after(()=>fs.rmSync(assets,{recursive:true,force:true}));
  for(const file of ['index.html','player-composer.js','player-composer.css','ui/ornate-frame-v1.css']){
    fs.mkdirSync(path.dirname(path.join(assets,file)),{recursive:true});
    fs.copyFileSync(path.join(source,file),path.join(assets,file));
  }
  return assets;
}
const verify=assets=>spawnSync(process.execPath,[verifier,assets],{encoding:'utf8'});

test('APK composer contract accepts the current inline UI without the retired buttons/modal',t=>{
  const result=verify(fixture(t));
  assert.equal(result.status,0,result.stderr);
  assert.match(result.stdout,/Packaged inline player composer verified/);
});
test('APK composer contract rejects a missing external controller',t=>{
  const assets=fixture(t);fs.unlinkSync(path.join(assets,'player-composer.js'));
  const result=verify(assets);
  assert.notEqual(result.status,0);assert.match(result.stderr,/player-composer.js/);
});
test('APK composer contract rejects stale packaged styles',t=>{
  const assets=fixture(t);fs.appendFileSync(path.join(assets,'player-composer.css'),'\n/* stale payload */');
  const result=verify(assets);
  assert.notEqual(result.status,0);assert.match(result.stderr,/differs from source: player-composer.css/);
});
