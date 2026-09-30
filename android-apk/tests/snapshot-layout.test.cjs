const {test}=require('node:test');
const assert=require('node:assert/strict');
const {bounds,envelope,layout,assetMetric}=require('../app/src/main/assets/snapshot-ui.js');
const near=(a,b)=>assert.ok(Math.abs(a-b)<1e-7,`${a} != ${b}`);
const metric=(w,h,pad=0)=>({width:w+pad*2,height:h+pad*2,body:{left:pad,top:pad,right:w+pad,bottom:h+pad},paint:{left:pad,top:pad,right:w+pad,bottom:h+pad}});
const standing=metric(60,100),aiming=metric(120,100),lucTram=metric(80,100);
const family=envelope([standing,aiming,lucTram]);
test('transparent padding does not change body scale or baseline',()=>{
 const a=layout(standing,360,250,'right','character',family);
 const padded=metric(60,100,90),b=layout(padded,360,250,'right','character',family);
 near(a.scale,b.scale);near(a.top+standing.body.bottom*a.scale,b.top+padded.body.bottom*b.scale);
 near(a.left+standing.paint.right*a.scale,b.left+padded.paint.right*b.scale);
});
test('standing, aiming and companion share height across aspect ratios',()=>{
 for(const [w,h] of [[320,250],[360,250],[390,250],[412,250],[620,450],[250,400],[800,250]]){
  const poses=[standing,aiming,lucTram].map(m=>layout(m,w,h,'right','character',family));
  poses.forEach((r,i)=>{
   near(r.bodyHeight,poses[0].bodyHeight);near(r.top+100*r.scale,h*.92);
   assert.ok(r.left>=w*.025-1e-7);assert.ok(r.left+[60,120,80][i]*r.scale<=w*.975+1e-7);
   assert.ok(r.top>=h*.02-1e-7);assert.ok(r.top+100*r.scale<=h);
  });
  if(w/h>=1.2)near(poses[0].bodyHeight,h*.84);
 }
});
test('entity keeps its own lane and aspect ratio',()=>{
 for(const m of [metric(160,100),metric(110,100)]){
  const r=layout(m,360,250,'left','entity',family);
  assert.ok(r.width<=360*.46+1e-7);near(r.width/r.height,m.width/m.height);near(r.baseline,230);
 }
});
test('CopX overlay has measured bounds for its refreshed Drive sprite',()=>{
 const m=assetMetric('file:///android_asset/entity/copx.webp');
 assert.equal(m.width,864);assert.equal(m.height,1536);
 assert.deepEqual(m.body,{left:17,top:194,right:857,bottom:1332});
 const r=layout(m,360,250,'left','entity',family);
 assert.ok(r.left>=0);assert.ok(r.left+r.width<=360);
});
test('alpha bounds distinguish faint residue, paint and solid body',()=>{
 const data=new Uint8ClampedArray(8*8*4);
 const alpha=(x,y,a)=>data[(y*8+x)*4+3]=a;
 alpha(0,0,1);alpha(1,1,20);alpha(2,2,255);alpha(5,5,255);alpha(6,7,40);
 const m=bounds(data,8,8);
 assert.deepEqual(m.paint,{left:1,top:1,right:7,bottom:8});
 assert.deepEqual(m.body,{left:2,top:2,right:6,bottom:6});
 const r=layout(m,320,250,'right','character',envelope([m]));
 near(r.top+m.body.bottom*r.scale,230);
 assert.ok(r.top+m.paint.bottom*r.scale<=250);
});
test('transparent sprite returns no geometry; translucent sprite has a fallback body',()=>{
 assert.equal(bounds(new Uint8ClampedArray(16),2,2),null);
 const data=new Uint8ClampedArray(16);data[3]=70;
 const m=bounds(data,2,2);assert.deepEqual(m.body,m.paint);
 assert.equal(layout(m,0,250,'right','character',envelope([m])),null);
});

