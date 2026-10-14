'use strict';
// Offline template assembly + nine *manual* live API calls (no emulator).
// NEVER persist prompts, raw model responses, API keys or user saves.
const fs=require('node:fs');
const path=require('node:path');
const root=path.resolve(__dirname,'../..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const main='android-apk/app/src/main/java/com/rabpit/backroom/MainActivity.java';
const milestone='android-apk/app/src/main/java/com/rabpit/backroom/core/StoryMilestonePlannerPrompt.kt';
const narrator='android-apk/app/src/main/java/com/rabpit/backroom/core/knowledge/GmNarrativeVoicePolicy.kt';
const validator='android-apk/app/src/main/java/com/rabpit/backroom/core/MilestoneNarrationValidator.kt';
const dbfile='android-apk/app/src/main/assets/knowledge/knowledge_db.json';
function template(file){
  const source=read(file),m=source.match(/(?:return |: String = )"""([\s\S]*?)"""\.trimIndent\(\)/);
  if(!m)throw Error('Missing Kotlin prompt template: '+file);
  const lines=m[1].split('\n');
  while(lines.length&&!lines[0].trim())lines.shift();
  while(lines.length&&!lines.at(-1).trim())lines.pop();
  const n=Math.min(...lines.filter(x=>x.trim()).map(x=>x.match(/^\s*/)[0].length));
  return lines.map(x=>x.slice(Math.min(n,x.match(/^\s*/)[0].length))).join('\n');
}
const javaExpressions=new Map([
  ['due.toString()','due'],
  ['com.rabpit.backroom.core.MilestoneNarrationValidator.prompt()','validator'],
  ['com.rabpit.backroom.core.knowledge.GmNarrativeVoicePolicy.prompt()','narrator'],
  ['new JSONObject(requireGameCore().currentActionContext()).optString("kind", "EXECUTE")','actionType'],
  ['action','action'],['visibleRolls.toString()','rolls'],
  ['requireGameCore().milestoneHistoryContext()','history'],['canon','canon']
]);
const escaped=Array.from(javaExpressions.keys()).sort((a,b)=>b.length-a.length)
  .map(s=>s.replace(/[.*+?^$\u007b\u007d()|[\]\\]/g,'\\$&')).join('|');
const javaTokens=new RegExp('"(?:\\\\.|[^"\\\\])*"|'+escaped,'g');
function gmPrompt(ctx){
  const found=read(main).match(/return "Bạn là Gehihi, Game Master[\s\S]*?"\\nCANON_READ_ONLY: " \+ canon;/);
  if(!found)throw Error('GM production entry point not found');
  const body=found[0];
  const residue=body.replace(javaTokens,'').replace(/\breturn\b|[\s+;]/g,'');
  if(residue)throw Error('GM prompt structure changed: '+residue.slice(0,80));
  const vals=Object.assign({},ctx,{validator:template(validator),narrator:template(narrator)});
  return Array.from(body.matchAll(javaTokens),m=>{
    const x=m[0];if(x.startsWith('"'))return JSON.parse(x);
    const k=javaExpressions.get(x);
    if(!k||typeof vals[k]!=='string')throw Error('Unknown GM dynamic input');
    return vals[k];
  }).join('');
}
function milestonePrompt(ctx){
  const map={
    checkpointId:ctx.checkpointId,absoluteStartTurn:ctx.turn,levelNode:'0',
    'position.index':ctx.index,checkpointTotal:40,'position.isLast':false,
    characters:'lucia=Lucia (FOLLOWER, Level 0); an-nhien=An Nhiên (NPC, Level 0)',
    'StoryMilestonePlan.CHECKPOINT_TURNS':12,
    'StoryMilestonePlan.MIN_ENTITY_ENCOUNTERS':3,
    'StoryCheckpointCore.CHECKPOINTS_PER_LEVEL':40,
    entityKeys:'hound, clump, duller, deathmoth, smiler, skin-stealer, hollow_grasper',
    completionSchema:'{"type":"NEXT_CHECKPOINT","outcome":"<authored completion>"}',
    supportedTargets:'1, 2, 3, 4, 5, 6',narrativeContinuity:ctx.history,
    canonPacket:ctx.canon,currentSituation:ctx.scene
  };
  return template(milestone).replace(/\$\u007b([^}]+)\u007d|\$([a-zA-Z_]\w*)/g,(_,exp,id)=>{
    const k=exp||id;
    if(!(k in map))throw Error('Unknown Milestone dynamic input: '+k);
    return String(map[k]);
  });
}
// Synthesizes 3 turns using real packaged Canon records, without copying user data.
function canonFixture(turn){
  const records=JSON.parse(read(dbfile)).records, lookup=new Map(records.map(r=>[r.id,r]));
  const mandatory=['GAME.TEXT.CORE','GAME.GM.FAIRNESS','WORLD.CORE',
    'WRITING.KNOWLEDGE_BOUNDARY','WRITING.COMPETENCE','WRITING.PLAYER_AGENCY',
    'CHAR.CAO_MINH.RUNTIME_CORE','CHAR.CAO_MINH.AUTHORED',
    'LEVEL.00','LEVEL.00.AUTHORED','CHAR.LUCIA.AUTHORED'];
  const optional=records.filter(r=>!mandatory.includes(r.id)&&r.priority>=30).slice(0,18).map(r=>r.id);
  const state={turn,level:{id:'0',number:0,name:'The Lobby',kind:'MAIN'},
    location:'Level 0 / The Lobby',player:{hp:55,condition:'HEALTHY'},
    party:turn<3?[]:[{id:'lucia',presence:'ACTIVE'}]};
  let out='[KNOWLEDGE_PACKET v1]\nBudget target=2200 soft=2800 hard=3400\n'+
    'Present actors: '+(turn<3?'':'lucia')+'\nCurrent state:\n'+JSON.stringify(state)+
    '\nRecent dialogue buffer:\n'+JSON.stringify([
      {role:'player',text:'Khám Phá'},
      {role:'gm',text:'Cao Minh bước đi trong Level 0 giữa những hành lang ẩm mốc.'}
    ])+'\nRetrieved records:\n';
  for(const id of mandatory.concat(optional)){
    const r=lookup.get(id);
    if(!r)throw Error('Missing Canon record: '+id);
    const doc=r.source.document;
    const provenance=doc.startsWith('Google Drive /Novel/Asset/')||doc.startsWith('https://')?'TRACEABLE':'LEGACY_UNVERIFIED';
    const text='<'+r.id+'> '+r.text.replace(/\n/g,' ')+'\n  source='+doc+'#'+r.source.anchor+
      '; provenance='+provenance+'; authority='+r.authority+'; mutability='+r.mutability+
      '; why='+(mandatory.includes(id)?'mandatory Canon context':'optional Canon')+'\n';
    if(Math.ceil((out.length+text.length+22)/4)>3400)break;
    out+=text;
  }
  return out+'[END_KNOWLEDGE_PACKET]';
}
function input(provider,n){
  const canon=canonFixture(n);
  const history=JSON.stringify({recentCommittedEvents:n===1?[]:[
    {checkpointId:'CP-L00-01',kind:'STORY',action:'Explore',event:'Corridor shifts',outcome:'Continue'}]});
  if(provider==='gehihi'){
    const due=n===2?[{id:'follower1',kind:'FOLLOWER',characterId:'lucia',turn:2}]:
      n===3?[{id:'encounter1',kind:'ENTITY',entityKey:'hound',turn:3}]:[];
    return gmPrompt({due:JSON.stringify(due),actionType:'EXPLORE',action:'Khám Phá',
      rolls:'{}',history,canon});
  }
  const scene=JSON.stringify({location:'Level 0 / The Lobby',level:{id:'0',number:0},
    party:[],player:{name:'Cao Minh',hp:55}});
  return milestonePrompt({checkpointId:'CP-L00-0'+n,turn:(n-1)*12+1,index:n,
    canon,history,scene});
}
function usage(provider,body){
  const u=provider==='gemini'?body.usageMetadata:body.usage;
  const inp=provider==='gemini'?u?.promptTokenCount:u?.prompt_tokens??u?.input_tokens;
  const cached=provider==='gemini'?u?.cachedContentTokenCount:
    u?.prompt_tokens_details?.cached_tokens??u?.input_tokens_details?.cached_tokens??u?.cached_tokens;
  const output=provider==='gemini'?u?.candidatesTokenCount:u?.completion_tokens??u?.output_tokens;
  const num=x=>Number.isFinite(x)?x:null;
  return {inputTokens:num(inp),cachedTokens:num(cached),outputTokens:num(output),
    cachePercent:Number.isFinite(inp)&&inp>0&&Number.isFinite(cached)?
      Math.round(10000*cached/inp)/100:null};
}
function settings(p){
  if(p==='gemini')return {key:process.env.GEMINI_API_KEY_1,model:'gemini-3.6-flash',
    url:'https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent'};
  if(p==='sol')return {key:process.env.SOL_API_KEY,model:'vgpt/gpt-6.1-sol',
    url:'https://api.vilao.ai/v1/chat/completions'};
  const base=(process.env.GEHIHI_BASE_URL||'https://api.vilao.ai/v1').replace(/\/+$/,'');
  if(new URL(base).protocol!=='https:')throw Error('GEHIHI_BASE_URL must be HTTPS');
  return {key:process.env.GEHIHI_API_KEY,model:process.env.GEHIHI_MODEL,
    url:base+'/chat/completions'};
}
async function invoke(p,conf,prompt,turn){
  const row={provider:p,model:conf.model,turn,inputChars:prompt.length,
    inputBytes:Buffer.byteLength(prompt,'utf8'),inputTokens:null,cachedTokens:null,
    outputTokens:null,cachePercent:null,httpStatus:null,elapsedMs:null};
  const body=p==='gemini'?{contents:[{role:'user',parts:[{text:prompt}]}],
    generationConfig:{responseMimeType:'application/json',thinkingConfig:{thinkingLevel:'low'},
      maxOutputTokens:96}}:
    {model:conf.model,messages:[{role:'user',content:prompt}],max_tokens:96,
      stream:false,temperature:p==='sol'?0.5:0.6,
      ...(p==='sol'?{reasoning_effort:'low'}:{})};
  const headers={'Content-Type':'application/json'};
  if(p==='gemini')headers['x-goog-api-key']=conf.key;
  else headers.Authorization='Bearer '+conf.key;
  const start=Date.now();
  try{
    const resp=await fetch(conf.url,{method:'POST',headers,body:JSON.stringify(body),
      signal:AbortSignal.timeout(60000)});
    row.httpStatus=resp.status;
    if(resp.ok)Object.assign(row,usage(p,await resp.json()));
    else row.error='provider_http_error';// Never print provider error bodies.
  }catch(error){row.error=error?.name==='TimeoutError'?'timeout':'network_or_parse_error';}
  row.elapsedMs=Date.now()-start;
  return row;
}
function summary(rows){
  let s='## Backrooms cache benchmark without Emulator\n\n';
  s+='**9 calls maximum (3/provider)**. Production prompt templates and packaged Canon; synthetic turn state. ';
  s+='No raw prompts or responses saved. Missing cache metadata is N/A, not 0%.\n\n';
  s+='| Provider | Turn | Input chars | UTF-8 bytes | Input tokens | Cached tokens | Cache hit | Output tokens | ms | HTTP |\n';
  s+='|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n';
  for(const r of rows)s+='| '+[r.provider,r.turn,r.inputChars,r.inputBytes,
    r.inputTokens??'N/A',r.cachedTokens??'N/A',
    r.cachePercent==null?'N/A':r.cachePercent+'%',r.outputTokens??'N/A',
    r.elapsedMs,r.httpStatus??r.error].join(' | ')+' |\n';
  return s;
}
async function mainRun(){
  if(process.env.CACHE_BENCH_CONFIRM!=='RUN')throw Error('Explicit RUN confirmation required');
  const providers=['gemini','gehihi','sol'];
  const configs=Object.fromEntries(providers.map(p=>[p,settings(p)]));
  for(const p of providers)if(!configs[p].key||!configs[p].model)
    throw Error('Missing GitHub Secret/variable for '+p+' (0 calls sent)');
  const results=[];
  for(const p of providers){
    for(let n=1;n<=3;n++){
      const row=await invoke(p,configs[p],input(p,n),n);results.push(row);
      console.log(p+' turn='+n+' inputChars='+row.inputChars+' inputTokens='+
        (row.inputTokens??'N/A')+' cachedTokens='+(row.cachedTokens??'N/A')+
        ' HTTP='+(row.httpStatus??row.error)+' ms='+row.elapsedMs);
      if(row.httpStatus!==200)break; // No retry or automatic fallback.
    }
  }
  fs.writeFileSync('provider-cache-metadata.json',JSON.stringify({kind:'synthetic-app-input',results},null,2)+'\n');
  const report=summary(results);
  if(process.env.GITHUB_STEP_SUMMARY)fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY,report);
  else console.log(report);
  if(results.some(r=>r.httpStatus!==200))process.exitCode=1;
}
module.exports={template,gmPrompt,milestonePrompt,canonFixture,input,usage,summary};
if(require.main===module)mainRun().catch(e=>{console.error(e.message);process.exitCode=1;});
