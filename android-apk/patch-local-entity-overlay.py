from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)

helpers = r'''  private String normalizedEntityId(String raw) throws Exception {
    String id = raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.ROOT);
    if (!id.matches("ENT-[A-Z0-9]+(?:-[A-Z0-9]+)?")) throw new Exception("Entity ID khong hop le: " + id);
    return id;
  }

  private JSONObject resolveEntityOverlay(String rawEntityId) throws Exception {
    String entityId = normalizedEntityId(rawEntityId);
    String asset;
    String name;
    switch (entityId) {
      case "ENT-1A": case "ENT-2B": case "ENT-5D": asset = "hound.png"; name = "Hound"; break;
      case "ENT-1B": case "ENT-2A": asset = "clump.png"; name = "Clump"; break;
      case "ENT-1C": asset = "duller.png"; name = "Duller"; break;
      case "ENT-1D": case "ENT-3A": asset = "deathmoth.png"; name = "Deathmoth"; break;
      case "ENT-1E": asset = "hostile_faceling.png"; name = "Hostile Faceling"; break;
      case "ENT-1F": asset = "false_puddle.png"; name = "False Puddle"; break;
      case "ENT-1G": asset = "paintings.png"; name = "Paintings"; break;
      case "ENT-2C": asset = "smiler.png"; name = "Smiler"; break;
      case "ENT-2D": case "ENT-3C": case "ENT-5C": asset = "skin-stealer.png"; name = "Skin-Stealer"; break;
      case "ENT-2E": case "ENT-5B": asset = "predatory_window.png"; name = "Predatory Window"; break;
      case "ENT-2F": asset = "biological_pipeline.png"; name = "Biological Pipeline"; break;
      case "ENT-3B": asset = "wretch.png"; name = "Wretch"; break;
      case "ENT-3D": asset = "cable_mimic.png"; name = "Cable Mimic"; break;
      case "ENT-5A": asset = "the_beast_of_level_5.png"; name = "The Beast of Level 5"; break;
      case "ENT-5E": asset = "hotel_corpse_lure.png"; name = "Hotel Corpse Lure"; break;
      case "ENT-R01": asset = "jeff_the_killer.png"; name = "Jeff the Killer"; break;
      case "ENT-R02": asset = "jane_the_killer.png"; name = "Jane the Killer"; break;
      case "ENT-R03": asset = "slenderman.png"; name = "Slenderman"; break;
      default: throw new Exception("Khong co local asset cho " + entityId);
    }
    return new JSONObject()
      .put("entityId", entityId)
      .put("name", name)
      .put("revision", 1)
      .put("anchor", "left-bottom")
      .put("maxHeight", 0.97)
      .put("url", "file:///android_asset/entity/" + asset);
  }

'''
helper_anchor = '  private boolean retryable(int code) {\n'
if 'private JSONObject resolveEntityOverlay(' not in text:
    text = replace_once(text, helper_anchor, helpers + helper_anchor, "local Entity helpers")

writer_start = text.find('  private String writerPrompt(JSONObject before, String action, JSONObject rolls, JSONArray auditFeedback) throws Exception {')
writer_end = text.find('  private JSONArray localKnowledgeIssues(', writer_start)
if writer_start < 0 or writer_end < 0:
    raise RuntimeError("writerPrompt boundary not found for local Entity contract")
writer = text[writer_start:writer_end]
writer_marker = (
    '      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory. " +\n'
)
writer_rule = writer_marker + (
    '      "ENTITY ROAMING HARD LOCK: mọi Entity trong LOCAL ROAMING POOL đều có thể lang thang/incursion qua bất kỳ Level 0-6; Level gốc chỉ là habitat/canon baseline, KHÔNG khóa nơi encounter. Khi rolls.entityEncounter.success=true và rolls.roamingEntityId có giá trị, encounter thường bắt buộc dùng đúng ID đó. LOCAL ROAMING POOL: ENT-1A Hound, ENT-1B Clump, ENT-1C Duller, ENT-1D Deathmoth, ENT-1E Hostile Faceling, ENT-1F False Puddle, ENT-1G Paintings, ENT-2C Smiler, ENT-2D Skin-Stealer, ENT-2E Predatory Window, ENT-2F Biological Pipeline, ENT-3B Wretch, ENT-3D Cable Mimic, ENT-5A Beast of Level 5, ENT-5E Hotel Corpse Lure, ENT-R03 Slenderman. Jeff ENT-R01 và Jane ENT-R02 giữ roll độc lập riêng. " +\n'
    '      "ENTITY OVERLAY HARD LOCK: với Entity đã được xác nhận và đang trực tiếp xuất hiện hoặc đối đầu trong cảnh hiện tại, dùng flag_patch root=entityEncounterKey value=exact ID. Nếu Entity bị tiêu diệt, Kai chạy trốn/thoát, Entity rời cảnh hoặc không còn trực tiếp hiện diện, đặt entityEncounterKey thành chuỗi rỗng ngay trong lượt đó. Hình ảnh Entity chỉ lấy từ APK local assets/entity, tuyệt đối không tải mạng. " +\n'
)
if 'ENTITY ROAMING HARD LOCK:' not in writer:
    if writer_marker not in writer:
        raise RuntimeError("writerPrompt local Entity insertion marker not found")
    writer = writer.replace(writer_marker, writer_rule, 1)
    text = text[:writer_start] + writer + text[writer_end:]

roll_old = '    rolls.put("entityEncounter", thresholdRoll("entityEncounter", 10000, entityThresholds[level], physical && entityAllowed, entitySuffix));\n'
roll_new = '''    JSONObject normalEntityRoll = thresholdRoll("entityEncounter", 10000, entityThresholds[level], physical && entityAllowed, entitySuffix);\n    rolls.put("entityEncounter", normalEntityRoll);\n    if (normalEntityRoll.optBoolean("success", false)) {\n      String[] roamingPool = {"ENT-1A","ENT-1B","ENT-1C","ENT-1D","ENT-1E","ENT-1F","ENT-1G","ENT-2C","ENT-2D","ENT-2E","ENT-2F","ENT-3B","ENT-3D","ENT-5A","ENT-5E","ENT-R03"};\n      rolls.put("roamingEntityId", roamingPool[GAME_RNG.nextInt(roamingPool.length)]);\n    }\n'''
if 'rolls.put("roamingEntityId"' not in text:
    text = replace_once(text, roll_old, roll_new, "roaming Entity deterministic pick")

request_marker = (
    '      "var snapshotBusy=false;function requestSnapshot(){var s=document.getElementById(\'status\');if(s)s.textContent=\'Snapshot chưa được cấu hình.\';}" +\n'
)
entity_js = r'''      "var __baseRenderSnapshot=renderSnapshot,__entityOverlay={id:'',url:'',revision:0,anchor:'left-bottom',maxHeight:.97,loading:''};" +
      "function normalizeEntityId(v){if(typeof v!=='string')return '';var m=v.toUpperCase().match(/ENT-[A-Z0-9]+(?:-[A-Z0-9]+)?/);return m?m[0]:'';}" +
      "function activeEntityId(){var f=state&&state.flags||{};if(Object.prototype.hasOwnProperty.call(f,'entityEncounterKey'))return normalizeEntityId(f.entityEncounterKey);if(f.jeff&&(f.jeff.present===true||f.jeff.spawned===true))return 'ENT-R01';if(f.jane&&(f.jane.present===true||f.jane.spawned===true))return 'ENT-R02';return '';}" +
      "function requestEntityOverlay(id){if(!id||__entityOverlay.loading===id)return;if(!window.Android||typeof Android.requestEntityOverlay!=='function')return;__entityOverlay.loading=id;Android.requestEntityOverlay(id);}" +
      "function appendEntityOverlay(){var box=document.getElementById('snapshot');if(!box)return;var old=box.querySelector('.snapshot-entity');if(old)old.remove();var id=activeEntityId();if(!id){__entityOverlay={id:'',url:'',revision:0,anchor:'left-bottom',maxHeight:.97,loading:''};return;}if(__entityOverlay.id!==id){__entityOverlay.url='';__entityOverlay.id=id;}if(!__entityOverlay.url){requestEntityOverlay(id);return;}var img=document.createElement('img');img.className='snapshot-entity';img.src=__entityOverlay.url;img.alt=id;img.style.position='absolute';img.style.bottom='0';img.style.width='auto';img.style.maxWidth='55%';img.style.height=Math.round(Math.max(.2,Math.min(1,Number(__entityOverlay.maxHeight)||.97))*100)+'%';img.style.objectFit='contain';img.style.pointerEvents='none';img.style.zIndex='2';img.style.left='0';img.style.objectPosition='left bottom';box.appendChild(img);}" +
      "renderSnapshot=function(){__baseRenderSnapshot();appendEntityOverlay();};" +
      "window.backroomEntityOverlay=function(payload){try{var r=JSON.parse(payload);var id=normalizeEntityId(r.entityId);if(!id)return;__entityOverlay.loading='';if(id!==activeEntityId())return;__entityOverlay.id=id;__entityOverlay.url=String(r.url||'');__entityOverlay.revision=Number(r.revision||1);__entityOverlay.anchor=String(r.anchor||'left-bottom');__entityOverlay.maxHeight=Number(r.maxHeight||.97);renderSnapshot();}catch(e){__entityOverlay.loading='';}};" +
      "window.backroomEntityOverlayError=function(payload){__entityOverlay.loading='';};" +
'''
if 'window.backroomEntityOverlay=function(payload)' not in text:
    text = replace_once(text, request_marker, entity_js + request_marker, "local Entity Snapshot overlay renderer")

bridge_marker = r'''    @JavascriptInterface public void requestSnapshot(String stateJson) {
      imageIo.execute(() -> requestSnapshotInternal(stateJson));
    }
'''
bridge_new = bridge_marker + r'''
    @JavascriptInterface public void requestEntityOverlay(String entityId) {
      imageIo.execute(() -> {
        try {
          emit("backroomEntityOverlay", resolveEntityOverlay(entityId).toString());
        } catch (Exception error) {
          try {
            JSONObject payload = new JSONObject()
              .put("entityId", entityId == null ? "" : entityId)
              .put("message", error.getMessage() == null ? "Khong the nap Entity asset local." : error.getMessage());
            emit("backroomEntityOverlayError", payload.toString());
          } catch (Exception ignored) {
            emit("backroomEntityOverlayError", "{\"entityId\":\"\",\"message\":\"Local Entity asset error\"}");
          }
        }
      });
    }
'''
if '@JavascriptInterface public void requestEntityOverlay(String entityId)' not in text:
    text = replace_once(text, bridge_marker, bridge_new, "local Entity Android bridge")

required_assets = [
    "hound.png","clump.png","duller.png","deathmoth.png","hostile_faceling.png","false_puddle.png","paintings.png",
    "smiler.png","skin-stealer.png","predatory_window.png","biological_pipeline.png","wretch.png","cable_mimic.png",
    "the_beast_of_level_5.png","hotel_corpse_lure.png","jeff_the_killer.png","jane_the_killer.png","slenderman.png"
]
asset_dir = ROOT / "app/src/main/assets/entity"
missing = [name for name in required_assets if not (asset_dir / name).is_file()]
if missing:
    raise RuntimeError("Missing local Entity assets: " + ", ".join(missing))

for forbidden in ["drive.google.com", "ENTITY_MANIFEST_FILE_ID", "readEntityManifestRemote", "entityManifestUrl"]:
    if forbidden in text:
        raise RuntimeError("Remote Drive Entity dependency still present: " + forbidden)

for marker in [
    'file:///android_asset/entity/',
    'ENT-R03',
    'ENTITY ROAMING HARD LOCK:',
    'rolls.put("roamingEntityId"',
    'private JSONObject resolveEntityOverlay(',
    'window.backroomEntityOverlay=function(payload)',
    '@JavascriptInterface public void requestEntityOverlay(String entityId)',
]:
    if marker not in text:
        raise RuntimeError("Local Entity contract missing: " + marker)

MAIN.write_text(text, encoding="utf-8")
print("Local Entity runtime installed: APK assets/entity only; all roaming pool monsters may cross Levels 0-6.")
