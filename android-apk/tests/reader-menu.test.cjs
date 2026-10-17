const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const html=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');
function menuRuntime(){
  const classes=new Set(),listeners={};
  const nodes={};
  for(const id of ['gameMenuModal','gameMenuOpen','gameMenuClose','gameMenuBackdrop'])nodes[id]={hidden:id==='gameMenuModal',attrs:{},events:{},setAttribute(k,v){this.attrs[k]=v},addEventListener(k,fn){this.events[k]=fn}};
  const document={getElementById:id=>nodes[id]||null,body:{classList:{add:x=>classes.add(x),remove:x=>classes.delete(x)}},addEventListener:(k,fn)=>{listeners[k]=fn}};
  const start=html.lastIndexOf('<script>',html.indexOf('var menu=document.getElementById("gameMenuModal")'))+8;
  const end=html.indexOf('</script>',start);
  vm.runInNewContext(html.slice(start,end),{document});
  return {nodes,classes,listeners};
}
test('Reader menu opens without the removed action input, exposing status and Party',()=>{
  const {nodes,classes}=menuRuntime();
  nodes.gameMenuOpen.events.click();
  assert.equal(nodes.gameMenuModal.hidden,false);
  assert.equal(nodes.gameMenuModal.attrs['aria-hidden'],'false');
  assert.equal(nodes.gameMenuOpen.attrs['aria-expanded'],'true');
  assert.equal(classes.has('game-menu-open'),true);
  const menu=html.slice(html.indexOf('id="gameMenuModal"'),html.indexOf('id="characterInventoryView"'));
  assert.match(menu,/Trạng thái/);assert.match(menu,/id="party"/);
});
test('Reader menu closes with close button, backdrop and Escape, and can reopen',()=>{
  const {nodes,classes,listeners}=menuRuntime();
  for(const close of [()=>nodes.gameMenuClose.events.click(),()=>nodes.gameMenuBackdrop.events.click(),()=>listeners.keydown({key:'Escape',preventDefault(){}})]){
    nodes.gameMenuOpen.events.click();close();
    assert.equal(nodes.gameMenuModal.hidden,true);
    assert.equal(nodes.gameMenuModal.attrs['aria-hidden'],'true');
    assert.equal(nodes.gameMenuOpen.attrs['aria-expanded'],'false');
    assert.equal(classes.has('game-menu-open'),false);
  }
});
