const {test}=require('node:test');
const assert=require('node:assert/strict');
const reset=require('./reset-release-catalog.cjs');
function fixture() {
  const sha='a'.repeat(40), oldSha='b'.repeat(40);
  const manifest={repository:'rabpitvn1-create/BACKROOMS',keepTag:'v1.0.0.0.0',oldReleases:[{id:1,tag:'v1.4.4.8',tagSha:oldSha}]};
  const context={repo:{owner:'rabpitvn1-create',repo:'BACKROOMS'},ref:'refs/heads/main',eventName:'push',sha};
  let releases=[{id:1,tag_name:'v1.4.4.8'}, {id:2,tag_name:manifest.keepTag,draft:false,prerelease:false,assets:[{name:'Backroom-1.0.0.0.0-debug.apk',state:'uploaded',size:100}]}];
  let tags=[{ref:'refs/tags/v1.4.4.8',object:{sha:oldSha}},{ref:`refs/tags/${manifest.keepTag}`,object:{sha}}];
  const calls=[];
  const repos={listReleases:()=>releases,deleteRelease:async x=>{calls.push(x);releases=releases.filter(r=>r.id!==x.release_id);}};
  const git={listMatchingRefs:()=>tags,deleteRef:async x=>{calls.push(x);tags=tags.filter(t=>t.ref!==`refs/${x.ref}`);}};
  return {github:{rest:{repos,git},paginate:async fn=>fn()},context,manifest,calls,releases,tags,pause:async()=>{}};
}
test('removes captured releases and tags, preserves replacement, and supports a retry',async()=>{
  const f=fixture();
  assert.deepEqual(await reset(f),{remainingReleases:1,remainingTags:1,keepTag:'v1.0.0.0.0'});
  assert.equal(f.calls.length,2);
  await reset(f); assert.equal(f.calls.length,2);
});
test('never removes an unlisted later release or tag',async()=>{
  const f=fixture();f.releases.push({id:3,tag_name:'v2.0'});f.tags.push({ref:'refs/tags/v2.0',object:{sha:'c'.repeat(40)}});
  const result=await reset(f);assert.equal(result.remainingReleases,2);assert.equal(result.remainingTags,2);
});
test('missing replacement APK blocks all deletion',async()=>{
  const f=fixture();f.releases[1].assets=[];
  await assert.rejects(reset(f),/replacement/);assert.equal(f.calls.length,0);
});
test('changed old tag blocks all deletion',async()=>{
  const f=fixture();f.tags[0].object.sha='c'.repeat(40);
  await assert.rejects(reset(f),/Tag moved/);assert.equal(f.calls.length,0);
});
test('pull requests and replacement tag in the manifest cannot delete anything',async()=>{
  const f=fixture();f.context.eventName='pull_request';
  await assert.rejects(reset(f),/restricted/);assert.equal(f.calls.length,0);
  f.context.eventName='push';f.manifest.oldReleases[0].tag=f.manifest.keepTag;
  await assert.rejects(reset(f),/Invalid/);assert.equal(f.calls.length,0);
});
