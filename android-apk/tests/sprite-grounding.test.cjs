const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const scope={window:{},document:{getElementById(){return null;}}};
vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../app/src/main/assets/sprite-layout.js'),'utf8'),scope);
const geometry=scope.window.backroomSpriteLayout.geometry;
const actor=[1122,1402,1,0,1122,1385],entity=[941,1672,12,199,941,1556];
function close(actual,expected){assert.ok(Math.abs(actual-expected)<.00001,`${actual} != ${expected}`);}
test('transparent canvas padding cannot move either sprite off the common ground plane',()=>{
  for(const width of [320,407,430])for(const [b,side]of[[actor,'right'],[entity,'left']]){
    const h=298,g=geometry(b,width,h,side),scale=g.height/b[1];
    close((b[5]-b[3])*scale,h*.94);
    close(h-g.bottom-(b[1]-b[5])*scale,h*.97);
  }
});
test('narrow viewports never shrink sprites horizontally',()=>{
  for(const b of[actor,entity]){
    close(geometry(b,320,298,'right').height,geometry(b,430,298,'right').height);
    close(geometry(b,320,298,'right').width,geometry(b,430,298,'right').width);
  }
});
test('zero-size viewports wait for layout instead of generating invalid geometry',()=>{
  assert.equal(geometry(actor,0,298,'right'),null);
  assert.equal(geometry(actor,320,0,'right'),null);
});
