/* Host regex adapter for the APK's Kotlin Regex calls, including Unicode lookbehind. */
(() => {
  const cache=new Map();
  const escape=s=>String(s).replace(/[.*+?^${}()|[\]\\]/g,'\\$&');
  function compile(source,options){
    let pattern=source.replace(/\\Q([\s\S]*?)\\E/g,(_,value)=>escape(value));
    let flags='gud';
    if((options&2)||/\(\?i[:)]/.test(pattern))flags+='i';
    if(options&8)flags+='m';
    if(options&32)flags+='s';
    pattern=pattern.replace(/\(\?i\)/g,'').replace(/\(\?i:/g,'(?:').replace(/\\A/g,'^').replace(/\\z/g,'$');
    const id=flags+'\0'+pattern;
    if(!cache.has(id))cache.set(id,new RegExp(pattern,flags));
    return cache.get(id);
  }
  globalThis.BackroomsRegex={
    escape,
    find(source,options,input,start){
      const re=compile(source,options);re.lastIndex=start;
      const match=re.exec(input);
      if(!match)return null;
      return {index:match.index,value:match[0],groups:Array.from(match,x=>x===undefined?null:x),ranges:match.indices.map(x=>x||null)};
    },
  };
})();
