/* Static offline snapshot mapping for the game environment registry. */
(function(root){
"use strict";
const images={
  "0": [
    "level_00__liminal_hall.webp",
    "level_00__snapshot_001.webp",
    "level_00__snapshot_002.webp",
    "level_00__snapshot_003.webp",
    "level_00__snapshot_004.webp",
    "level_00__snapshot_005.webp",
    "level_00__snapshot_006.webp",
    "level_00__snapshot_007.webp"
  ],
  "1": [
    "level_01__snapshot_001.webp",
    "level_01__snapshot_002.webp",
    "level_01__snapshot_003.webp"
  ],
  "2": [
    "level_02__snapshot_001.webp",
    "level_02__snapshot_002.webp",
    "level_02__snapshot_003.webp"
  ],
  "3": [
    "level_03__snapshot_001.webp",
    "level_03__snapshot_002.webp",
    "level_03__snapshot_003.webp"
  ],
  "4": [
    "level_04__snapshot_001.webp",
    "level_04__snapshot_002.webp",
    "level_04__snapshot_003.webp"
  ],
  "5": [
    "level_05__snapshot_001.webp",
    "level_05__snapshot_002.webp",
    "level_05__snapshot_003.webp"
  ],
  "6": [
    "level_06__snapshot_001.webp",
    "level_06__snapshot_002.webp",
    "level_06__snapshot_003.webp"
  ],
  "0.1": [],
  "0.2": [
    "level_00p2__snapshot_001.webp"
  ],
  "0.5": [],
  "0.7": [],
  "manila_room": [
    "area_00__manila_room__snapshot_001.webp"
  ],
  "the_torment": [
    "area_00__the_torment__snapshot_001.webp"
  ],
  "red_rooms": [
    "area_00__red_rooms__snapshot_001.webp"
  ],
  "dullness": [
    "area_00__dullness__snapshot_001.webp"
  ],
  "epsilon": [
    "area_00__epsilon__snapshot_001.webp"
  ],
  "ls_2": [
    "area_00__ls_2__reference_snapshot_001.webp"
  ],
  "1.2": [
    "level_01p2__snapshot_001.webp"
  ],
  "1.5": [
    "level_01p5__snapshot_001.webp"
  ],
  "traders_vault": [
    "area_01__traders_vault__reference_snapshot_001.webp"
  ],
  "base_alpha": [
    "area_01__base_alpha__snapshot_001.webp"
  ],
  "office_space_el3a": [
    "area_02__office_space_el3a__reference_snapshot_001.webp"
  ],
  "office_market": [
    "area_04__the_office_market__snapshot_001.webp"
  ],
  "5.1": [
    "level_05p1__snapshot_001.webp"
  ],
  "6.1": [
    "level_06p1__snapshot_001.webp"
  ]
};
const specialParents={
  "manila_room": 0,
  "the_torment": 0,
  "red_rooms": 0,
  "dullness": 0,
  "epsilon": 0,
  "ls_2": 0,
  "traders_vault": 1,
  "base_alpha": 1,
  "office_space_el3a": 2,
  "office_market": 4
};
const assetPrefix="file:///android_asset/level_snapshots/";
const owns=(record,key)=>Object.prototype.hasOwnProperty.call(record,key);
function nodeId(state){
  const level=state&&state.level;
  if(level&&level.id!=null&&owns(images,String(level.id).trim()))return String(level.id).trim();
  if(level&&level.number!=null&&owns(images,String(level.number).trim()))return String(level.number).trim();
  return "0";
}
function sceneKey(state){
  const location=String(state&&state.location||"").trim().toLocaleLowerCase();
  const area=String(state&&state.flags&&state.flags.visualAreaKey||"").trim().toLocaleLowerCase();
  return nodeId(state)+"|"+location+"|"+area;
}
function stableHash(value){
  let hash=2166136261;
  for(let i=0;i<value.length;i++)hash=Math.imul(hash^value.charCodeAt(i),16777619)>>>0;
  return hash;
}
function resolve(state){
  const id=nodeId(state);
  const parent=owns(specialParents,id)?specialParents[id]:parseInt(id,10);
  const variants=images[id];
  const dedicated=variants.length>0;
  const index=dedicated?stableHash(sceneKey(state))%variants.length:-1;
  const file=dedicated?variants[index]:"level_"+parent+".webp";
  return {nodeId:id,parent:parent,file:file,src:assetPrefix+file,
    hasDedicatedImage:dedicated,fallback:!dedicated,variantIndex:index,variantCount:variants.length};
}
const api=Object.freeze({nodeId:nodeId,sceneKey:sceneKey,resolve:resolve,
  knownIds:function(){return Object.keys(images);},
  filesFor:function(id){return owns(images,id)?images[id].slice():[];}});
root.BackroomSnapshotAssets=api;
if(typeof module==="object"&&module&&module.exports)module.exports=api;
})(typeof window!=="undefined"?window:globalThis);
