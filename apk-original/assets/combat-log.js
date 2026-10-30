/* Presentation from typed Core events only. Never infer damage/HP from narration. */
(function () {
  'use strict';
  const part=(kind,text)=>({kind,text:String(text)});
  function format(event) {
    if (!event || !['actor','entity'].includes(event.target)) return null;
    const source=String(event.sourceName||''),target=String(event.targetName||'');
    if (!target || (!source && event.phase!=='status')) return null;
    const targetKind=event.target==='entity'?'entity':'character';
    const sourceKind=event.target==='entity'?'character':'entity';
    let parts;
    if(event.outcome==='miss') parts=[part(sourceKind,source),' tấn công trượt ',part(targetKind,target)];
    else {
      const amount=event.amount;
      if(!Number.isSafeInteger(amount)||amount<=0)return null;
      if(event.phase==='status') parts=[part(targetKind,target),' chịu [',part('skill',event.status||event.skill||'Hiệu ứng'),']: '];
      else {
        parts=[part(sourceKind,source)];
        if(event.skill)parts.push(' dùng tuyệt kĩ [',part('skill',event.skill),']');
        else parts.push(' tấn công');
        parts.push(' gây ');
      }
      parts.push(part('damage','-'+amount+' DMG'));
      if(event.phase!=='status')parts.push(' lên ',part(targetKind,target));
      if(event.critical)parts.push(' · Chí mạng');
      if(event.status&&event.phase!=='status')parts.push(' · ',part('skill',event.status));
    }
    if(Number.isSafeInteger(event.remainingHp)&&Number.isSafeInteger(event.maxHp)&&event.maxHp>0)
      parts.push(' · ',part(targetKind,target),' ',part('hp',event.remainingHp+'/'+event.maxHp+' HP'));
    parts.push('.');
    return parts;
  }
  window.backroomCombatLog=Object.freeze({format});
})();
