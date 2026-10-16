/* Portable, opt-in diagnostics. Read-only: never mutates gameplay or checkpoints. */
(function(global){
  "use strict";
  const MAX_EVENTS=250, events=[];
  const el=id=>document.getElementById(id);
  function note(type,detail){
    events.push({at:new Date().toISOString(),type:String(type),detail:String(detail==null?"":detail).slice(0,3000)});
    if(events.length>MAX_EVENTS)events.shift();
  }
  global.addEventListener("error",event=>note("javascript.error",(event.message||"Error")+" @ "+(event.filename||"unknown")+":"+(event.lineno||0)+":"+(event.colno||0)));
  global.addEventListener("unhandledrejection",event=>note("javascript.unhandledrejection",event.reason&&event.reason.stack||event.reason));
  ["warn","error"].forEach(level=>{
    const original=console[level];
    if(typeof original!=="function")return;
    console[level]=function(){
      try{note("console."+level,Array.from(arguments).map(value=>value&&value.stack||String(value)).join(" "));}catch(ignore){}
      return original.apply(console,arguments);
    };
  });
  function snapshot(){
    const result={schema:"backroom.diagnostics.v1",createdAt:new Date().toISOString(),instructions:"Diagnostic evidence, not a save file. Check error timeline, authoritative state, UI state and API HTTP status before inferring root cause.",
      javascriptEvents:events.slice(),environment:{userAgent:navigator.userAgent,online:navigator.onLine,location:location.protocol+"//"+location.host},
      runtime:{combatBusy:!!global.__combatBusy,
        busy:typeof busy!=="undefined"?!!busy:null,
        combatFeedbackBusy:!!global.__combatFeedbackBusy,combatDiceBusy:!!global.__combatDiceBusy,
        directCombatPreparing:!!global.__directCombatPreparing,directCombatResolving:!!global.__directCombatResolving,
        pokerDiceHidden:el("pokerDiceModal")?!!el("pokerDiceModal").hidden:null,
        actionReadOnly:el("action")?!!el("action").readOnly:null,
        executeDisabled:el("submit")?!!el("submit").disabled:null,
        uiStatus:el("status")&&el("status").textContent},
      uiLogText:el("log")&&el("log").innerText||""};
    try{result.presentation=typeof state==="undefined"?null:state;}catch(error){result.presentationError=String(error);}
    try{
      if(global.Android&&typeof Android.currentClientState==="function"&&result.presentation){
        const raw=Android.currentClientState(JSON.stringify(result.presentation));
        const core=JSON.parse(raw||"null");
        if(core&&core.error)result.coreError=core.error;
        else result.authoritativeCore=core;
      }else result.coreError="Android.currentClientState unavailable";
    }catch(error){result.coreError=String(error&&error.stack||error);}
    try{const value=localStorage.getItem("backroom-apk-state");result.localPresentation={exists:value!==null,bytes:value?value.length:0};}
    catch(error){result.storageError=String(error);}
    return result;
  }
  let armedUntil=0,armedFor="";
  function exportNow(){
    const source="menu";
    const check=el("diagnosticSensitive");
    const unsafe=!!(check&&check.checked);
    const button=el("exportDiagnosticButton");
    const status=el("diagnosticExportStatus");
    if(unsafe&&(Date.now()>armedUntil||armedFor!==source)){
      armedUntil=Date.now()+10000;armedFor=source;
      if(status)status.textContent="CẢNH BÁO: File sẽ chứa API key đang cấu hình. Nhấn Xuất log lần nữa trong 10 giây để xác nhận.";
      return;
    }
    armedUntil=0;armedFor="";
    if(!global.Android||typeof Android.exportDiagnosticLog!=="function"){
      if(status)status.textContent="Bản Android này chưa hỗ trợ xuất file.";return;
    }
    try{
      if(button)button.disabled=true;
      note("diagnostic.export_requested",unsafe?"sensitive opt-in":"redacted");
      const result=JSON.parse(Android.exportDiagnosticLog(JSON.stringify(snapshot()),unsafe)||"{}");
      if(status)status.textContent=result.ok?"Chọn nơi lưu file .json trong hộp thoại Android.":("Không xuất được: "+(result.error||"unknown"));
    }catch(error){note("diagnostic.export_error",error&&error.stack||error);if(status)status.textContent="Không mở được trình lưu file: "+String(error);}
    finally{if(button)button.disabled=false;}
  }
  global.backroomDiagnosticExportResult=function(raw){
    try{
      const result=JSON.parse(raw);
      const status=el("diagnosticExportStatus");
      if(status)status.textContent=result.saved?"Đã lưu file chẩn đoán JSON.":result.cancelled?"Đã hủy xuất log.":"Xuất log thất bại: "+(result.error||"unknown");
    }catch(error){note("diagnostic.result_error",error);}
  };
  document.addEventListener("DOMContentLoaded",()=>{
    const menu=el("exportDiagnosticButton");
    if(menu)menu.addEventListener("click",()=>exportNow());
    note("diagnostic.ready","UI diagnostics initialized");
  });
  global.BackroomDiagnostics={note,snapshot};
})(window);
