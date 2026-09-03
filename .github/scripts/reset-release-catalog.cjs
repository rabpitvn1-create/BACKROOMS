// One-time, user-requested reset. Only the captured release IDs and tag SHAs
// may be removed; unrelated or subsequently-created releases are untouched.
module.exports = async function resetCatalog({github, context, manifest, pause = () => new Promise(resolve=>setTimeout(resolve,1100))}) {
  const {owner, repo} = context.repo;
  if (`${owner}/${repo}` !== manifest.repository || context.ref !== 'refs/heads/main' || context.eventName !== 'push') {
    throw new Error('Release reset is restricted to the approved repository main push');
  }
  const old = manifest.oldReleases;
  if (!old.length || new Set(old.map(r=>r.id)).size !== old.length || new Set(old.map(r=>r.tag)).size !== old.length ||
      old.some(r=>!Number.isSafeInteger(r.id) || !/^v[0-9]+(?:\.[0-9]+)+$/.test(r.tag) || !/^[0-9a-f]{40}$/.test(r.tagSha) || r.tag===manifest.keepTag)) {
    throw new Error('Invalid captured release catalog');
  }
  const releases = await github.paginate(github.rest.repos.listReleases,{owner,repo,per_page:100});
  const tags = await github.paginate(github.rest.git.listMatchingRefs,{owner,repo,ref:'tags/',per_page:100});
  const keep = releases.find(r=>r.tag_name===manifest.keepTag);
  const keepRef = tags.find(r=>r.ref===`refs/tags/${manifest.keepTag}`);
  if (!keep || keep.draft || keep.prerelease || !keepRef || keepRef.object.sha!==context.sha ||
      !keep.assets.some(a=>a.name==='Backroom-1.0.0.0.0-debug.apk' && a.state==='uploaded' && a.size>0)) {
    throw new Error('Verified replacement release for this commit is required before deletion');
  }
  // Validate the entire plan before making the first destructive request.
  for (const row of old) {
    const release = releases.find(r=>r.id===row.id);
    if (release && release.tag_name!==row.tag) throw new Error(`Release identity changed: ${row.id}`);
    if (releases.some(r=>r.tag_name===row.tag && r.id!==row.id)) throw new Error(`Release recreated: ${row.tag}`);
    const tag = tags.find(t=>t.ref===`refs/tags/${row.tag}`);
    if (tag && tag.object.sha!==row.tagSha) throw new Error(`Tag moved: ${row.tag}`);
  }
  for (const row of old) {
    if (releases.some(r=>r.id===row.id)) {
      await github.rest.repos.deleteRelease({owner,repo,release_id:row.id});
      await pause();
    }
    if (tags.some(t=>t.ref===`refs/tags/${row.tag}`)) {
      await github.rest.git.deleteRef({owner,repo,ref:`tags/${row.tag}`});
      await pause();
    }
  }
  const remaining = await github.paginate(github.rest.repos.listReleases,{owner,repo,per_page:100});
  const remainingTags = await github.paginate(github.rest.git.listMatchingRefs,{owner,repo,ref:'tags/',per_page:100});
  if (!remaining.some(r=>r.id===keep.id) || !remainingTags.some(t=>t.ref===`refs/tags/${manifest.keepTag}`) ||
      remaining.some(r=>old.some(o=>o.id===r.id || o.tag===r.tag_name)) ||
      remainingTags.some(t=>old.some(o=>t.ref===`refs/tags/${o.tag}`))) {
    throw new Error('Release catalog reset verification failed');
  }
  return {remainingReleases:remaining.length,remainingTags:remainingTags.length,keepTag:manifest.keepTag};
};
