const assert = require('node:assert/strict');
require('../pages/providers.js');
const config = {
 keys: Array.from({length:6},(_,i)=>'fixture-'+(i+1)), models:['fixture-gemini'],
 argon:{apiKey:'fixture-argon',baseUrl:'https://argon.example/v1/',model:'private-argon-model'}
};
const reply = {reply:'Cao Minh tiếp tục quan sát khu vực.'};
const openai = () => new Response(JSON.stringify({choices:[{message:{content:JSON.stringify(reply)}}]}));
const gemini = () => new Response(JSON.stringify({candidates:[{content:{parts:[{text:JSON.stringify(reply)}]}}]}));
function router() {
 const events=[];
 return {events,generate:BackroomsProviders.create(config,{emit(){},record:e=>events.push(e)})};
}
(async()=>{
 let calls=[];
 globalThis.fetch=async(url,options)=>{
  calls.push(url);
  assert.equal(url,'https://argon.example/v1/chat/completions');
  assert.equal(options.headers.Authorization,'Bearer fixture-argon');
  const body=JSON.parse(options.body);
  assert.equal(body.model,'private-argon-model');
  assert.equal(body.messages[0].content,'prompt');assert.equal(body.stream,false);
  return openai();
 };
 let r=router();
 assert.deepEqual(await r.generate('prompt'),reply);
 assert.equal(calls.length,1);assert.equal(r.events[0].alias,'Argon');
 console.log('PASS: Argon is first, using its own URL, model and Bearer key; Gemini is not called');

 calls=[];
 globalThis.fetch=async(url,options)=>{
  if(url.includes('argon.example')){calls.push('Argon');return new Response('{}',{status:503});}
  const key=options.headers['x-goog-api-key'];calls.push(key);
  return key==='fixture-1'?new Response('{}',{status:401}):gemini();
 };
 r=router();
 assert.deepEqual(await r.generate('prompt'),reply);
 assert.deepEqual(calls,['Argon','fixture-1','fixture-2']);
 calls=[];await r.generate('prompt');assert.deepEqual(calls,['fixture-2']);
 console.log('PASS: Argon failure falls through to K1, then K2; cooldown prevents repeated failed requests');

 for(const failure of ['invalid_json','network']){
  globalThis.fetch=async url=>{
   if(url.includes('argon.example')){
    if(failure==='network')throw new TypeError('fixture network error');
    return new Response(JSON.stringify({choices:[{message:{content:'not JSON'}}]}));
   }
   return gemini();
  };
  r=router();assert.deepEqual(await r.generate('prompt'),reply);
  assert.equal(r.events.at(-1).alias,'K1');
 }
 console.log('PASS: malformed Argon output and network failure both fall back to Gemini');

 calls=[];
 globalThis.fetch=async(url,options)=>{
  calls.push(url.includes('argon.example')?'Argon':options.headers['x-goog-api-key']);
  return new Response('{}',{status:401});
 };
 r=router();await assert.rejects(r.generate('prompt'),/Các API/);
 assert.deepEqual(calls,['Argon',...config.keys]);
 console.log('PASS: exhausting providers attempts Argon and all six Gemini keys in order');

 config.argon.baseUrl='https://argon.example/v1/chat/completions';
 globalThis.fetch=async url=>{assert.equal(url,config.argon.baseUrl);return openai();};
 r=router();assert.deepEqual(await r.generate('prompt'),reply);
 console.log('PASS: an explicit chat/completions endpoint is preserved');
})().catch(error=>{console.error(error);process.exit(1);});
