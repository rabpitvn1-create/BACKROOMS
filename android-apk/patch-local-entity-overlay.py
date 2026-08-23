from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)

helpers = r'''  private String normalizedEntityKey(String raw) throws Exception {
    String key = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
    switch (key) {
      case "hound": case "clump": case "duller": case "deathmoth":
      case "hostile_faceling": case "false_puddle": case "paintings": case "smiler":
      case "skin-stealer": case "predatory_window": case "biological_pipeline": case "wretch":
      case "cable_mimic": case "the_beast_of_level_5": case "hotel_corpse_lure":
      case "jeff_the_killer": case "jane_the_killer": case "slenderman":
        return key;
      default:
        throw new Exception("Entity key khong hop le: " + key);
    }
  }

  private JSONObject resolveEntityOverlay(String rawEntityKey) throws Exception {
    String entityKey = normalizedEntityKey(rawEntityKey);
    String name;
    switch (entityKey) {
      case "hound": name = "Hound"; break;
      case "clump": name = "Clump"; break;
      case "duller": name = "Duller"; break;
      case "deathmoth": name = "Deathmoth"; break;
      case "hostile_faceling": name = "Hostile Faceling"; break;
      case "false_puddle": name = "False Puddle"; break;
      case "paintings": name = "Paintings"; break;
      case "smiler": name = "Smiler"; break;
      case "skin-stealer": name = "Skin-Stealer"; break;
      case "predatory_window": name = "Predatory Window"; break;
      case "biological_pipeline": name = "Biological Pipeline"; break;
      case "wretch": name = "Wretch"; break;
      case "cable_mimic": name = "Cable Mimic"; break;
      case "the_beast_of_level_5": name = "The Beast of Level 5"; break;
      case "hotel_corpse_lure": name = "Hotel Corpse Lure"; break;
      case "jeff_the_killer": name = "Jeff the Killer"; break;
      case "jane_the_killer": name = "Jane the Killer"; break;
      case "slenderman": name = "Slenderman"; break;
      default: throw new Exception("Khong co local asset cho " + entityKey);
    }
    return new JSONObject()
      .put("entityKey", entityKey)
      .put("name", name)
      .put("revision", 1)
      .put("anchor", "left-bottom")
      .put("maxHeight", 0.97)
      .put("url", "file:///android_asset/entity/" + entityKey + ".png");
  }

'''
helper_anchor = '  private boolean retryable(int code) {\n'
if 'private JSONObject resolveEntityOverlay(' not in text:
    text = replace_once(text, helper_anchor, helpers + helper_anchor, "local Entity helpers")

# Entity visual state is current-presence state only. Historical registries do not authorize rendering.
flag_old = r'''    if (root.equals("jeff") || root.equals("entityRegistry") || root.equals("entitiesConfirmedLocal") || root.equals("entityEncounterKey")) {
      JSONObject flags = before.optJSONObject("flags");
      return rollSuccess(rolls, "entityEncounter") || (flags != null && flags.optInt("entitiesConfirmedLocal", 0) > 0);
    }
'''
flag_new = r'''    if (root.equals("jeff") || root.equals("entitiesConfirmedLocal") || root.equals("entityEncounterKey")) {
      JSONObject flags = before.optJSONObject("flags");
      if (root.equals("entityEncounterKey") && flags != null) {
        if (!flags.optString("entityEncounterKey", "").trim().isEmpty()) return true;
        JSONObject jeff = flags.optJSONObject("jeff");
        if (jeff != null && (jeff.optBoolean("present", false) || jeff.optBoolean("spawned", false))) return true;
      }
      return rollSuccess(rolls, "entityEncounter") || (flags != null && flags.optInt("entitiesConfirmedLocal", 0) > 0);
    }
'''
if flag_new not in text:
    text = replace_once(text, flag_old, flag_new, "Entity visual-state clear gate")

writer_start = text.find('  private String writerPrompt(JSONObject before, String action, JSONObject rolls, JSONArray auditFeedback) throws Exception {')
writer_end = text.find('  private JSONArray localKnowledgeIssues(', writer_start)
if writer_start < 0 or writer_end < 0:
    raise RuntimeError("writerPrompt boundary not found for local Entity contract")
writer = text[writer_start:writer_end]
writer_marker = '      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory. " +\n'
overlay_rule = '      "ENTITY OVERLAY HARD LOCK: với Entity đang trực tiếp xuất hiện hoặc đối đầu trong cảnh hiện tại, dùng flag_patch root=entityEncounterKey value=canonical Entity key đúng tên asset bỏ .png, ví dụ hound, smiler, skin-stealer, slenderman. Nếu Entity bị tiêu diệt, Kai chạy trốn hoặc thoát khỏi Entity, Entity rời cảnh, biến mất, hoặc không còn trực tiếp hiện diện/đối đầu, bắt buộc đặt entityEncounterKey thành chuỗi rỗng ngay trong lượt đó. entityEncounterKey chỉ là trạng thái hiện diện trực quan hiện tại, không phải lịch sử encounter. Không dùng mã ENT-* hoặc alias theo Level. " +\n'
roaming_rule = '      "ENTITY ROAMING HARD LOCK: mọi Entity trong LOCAL ROAMING POOL đều có thể lang thang/incursion qua bất kỳ Level 0-6. Khi rolls.entityEncounter.success=true và rolls.roamingEntityKey có giá trị, encounter thường bắt buộc dùng đúng canonical key đó. LOCAL ROAMING POOL: hound, clump, duller, deathmoth, hostile_faceling, false_puddle, paintings, smiler, skin-stealer, predatory_window, biological_pipeline, wretch, cable_mimic, the_beast_of_level_5, hotel_corpse_lure, slenderman. Jeff the Killer và Jane the Killer tạm giữ roll độc lập riêng ở bước hiện tại nhưng dùng key jeff_the_killer và jane_the_killer. " +\n'
local_rule = '      "ENTITY ASSET LOCAL HARD LOCK: hình Entity chỉ lấy từ APK assets/entity qua file:///android_asset/entity/<canonical-key>.png; cấm mã ENT-*, alias theo Level, manifest từ xa hoặc ảnh Entity từ mạng. " +\n'
if 'ENTITY ROAMING HARD LOCK:' not in writer:
    if writer_marker not in writer:
        raise RuntimeError("writerPrompt local Entity insertion marker not found")
    writer = writer.replace(writer_marker, writer_marker + overlay_rule + roaming_rule + local_rule, 1)
    text = text[:writer_start] + writer + text[writer_end:]

roll_old = '    rolls.put("entityEncounter", thresholdRoll("entityEncounter", 10000, entityThresholds[level], physical && entityAllowed, entitySuffix));\n'
roll_new = '''    JSONObject normalEntityRoll = thresholdRoll("entityEncounter", 10000, entityThresholds[level], physical && entityAllowed, entitySuffix);\n    rolls.put("entityEncounter", normalEntityRoll);\n    if (normalEntityRoll.optBoolean("success", false)) {\n      String[] roamingPool = {"hound","clump","duller","deathmoth","hostile_faceling","false_puddle","paintings","smiler","skin-stealer","predatory_window","biological_pipeline","wretch","cable_mimic","the_beast_of_level_5","hotel_corpse_lure","slenderman"};\n      rolls.put("roamingEntityKey", roamingPool[GAME_RNG.nextInt(roamingPool.length)]);\n    }\n'''
if 'rolls.put("roamingEntityKey"' not in text:
    text = replace_once(text, roll_old, roll_new, "roaming Entity deterministic pick")

request_marker = '      "var snapshotBusy=false;function requestSnapshot(){var s=document.getElementById(\'status\');if(s)s.textContent=\'Snapshot chưa được cấu hình.\';}" +\n'
entity_js = r'''      "var __baseRenderSnapshot=renderSnapshot,__entityOverlay={key:'',url:'',revision:0,anchor:'left-bottom',maxHeight:.97,loading:''};" +
      "var __entityKeys=['hound','clump','duller','deathmoth','hostile_faceling','false_puddle','paintings','smiler','skin-stealer','predatory_window','biological_pipeline','wretch','cable_mimic','the_beast_of_level_5','hotel_corpse_lure','jeff_the_killer','jane_the_killer','slenderman'];" +
      "function normalizeEntityKey(v){if(typeof v!=='string')return '';var k=v.trim().toLowerCase();return __entityKeys.indexOf(k)>=0?k:'';}" +
      "function activeEntityKey(){var f=state&&state.flags||{};var direct=normalizeEntityKey(f.entityEncounterKey);if(direct)return direct;if(f.jeff&&f.jeff.present===true)return 'jeff_the_killer';if(f.jane&&f.jane.present===true)return 'jane_the_killer';return '';}" +
      "function requestEntityOverlay(key){if(!key||__entityOverlay.loading===key)return;if(!window.Android||typeof Android.requestEntityOverlay!=='function')return;__entityOverlay.loading=key;Android.requestEntityOverlay(key);}" +
      "function appendEntityOverlay(){var box=document.getElementById('snapshot');if(!box)return;var old=box.querySelector('.snapshot-entity');if(old)old.remove();var key=activeEntityKey();if(!key){__entityOverlay={key:'',url:'',revision:0,anchor:'left-bottom',maxHeight:.97,loading:''};return;}if(__entityOverlay.key!==key){__entityOverlay.url='';__entityOverlay.key=key;}if(!__entityOverlay.url){requestEntityOverlay(key);return;}var img=document.createElement('img');img.className='snapshot-entity';img.src=__entityOverlay.url;img.alt=key;img.style.position='absolute';img.style.bottom='0';img.style.width='auto';img.style.maxWidth='55%';img.style.height=Math.round(Math.max(.2,Math.min(1,Number(__entityOverlay.maxHeight)||.97))*100)+'%';img.style.objectFit='contain';img.style.pointerEvents='none';img.style.zIndex='2';img.style.left='0';img.style.objectPosition='left bottom';box.appendChild(img);}" +
      "renderSnapshot=function(){__baseRenderSnapshot();appendEntityOverlay();};" +
      "window.backroomEntityOverlay=function(payload){try{var r=JSON.parse(payload);var key=normalizeEntityKey(r.entityKey);if(!key)return;__entityOverlay.loading='';if(key!==activeEntityKey())return;__entityOverlay.key=key;__entityOverlay.url=String(r.url||'');__entityOverlay.revision=Number(r.revision||1);__entityOverlay.anchor=String(r.anchor||'left-bottom');__entityOverlay.maxHeight=Number(r.maxHeight||.97);renderSnapshot();}catch(e){__entityOverlay.loading='';}};" +
      "window.backroomEntityOverlayError=function(payload){__entityOverlay.loading='';};" +
'''
if 'window.backroomEntityOverlay=function(payload)' not in text:
    text = replace_once(text, request_marker, entity_js + request_marker, "local Entity Snapshot overlay renderer")

bridge_marker = r'''    @JavascriptInterface public void requestSnapshot(String stateJson) {
      imageIo.execute(() -> requestSnapshotInternal(stateJson));
    }
'''
bridge_new = bridge_marker + r'''
    @JavascriptInterface public void requestEntityOverlay(String entityKey) {
      imageIo.execute(() -> {
        try {
          emit("backroomEntityOverlay", resolveEntityOverlay(entityKey).toString());
        } catch (Exception error) {
          try {
            JSONObject payload = new JSONObject()
              .put("entityKey", entityKey == null ? "" : entityKey)
              .put("message", error.getMessage() == null ? "Khong the nap Entity asset local." : error.getMessage());
            emit("backroomEntityOverlayError", payload.toString());
          } catch (Exception ignored) {
            emit("backroomEntityOverlayError", "{\"entityKey\":\"\",\"message\":\"Local Entity asset error\"}");
          }
        }
      });
    }
'''
if '@JavascriptInterface public void requestEntityOverlay(String entityKey)' not in text:
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

for forbidden in ["drive.google.com", "ENTITY_MANIFEST_FILE_ID", "readEntityManifestRemote", "entityManifestUrl", "ENT-"]:
    if forbidden in text:
        raise RuntimeError("Legacy/remote Entity dependency still present: " + forbidden)

for marker in [
    'file:///android_asset/entity/', 'ENTITY ROAMING HARD LOCK:',
    'rolls.put("roamingEntityKey"', 'private JSONObject resolveEntityOverlay(',
    'window.backroomEntityOverlay=function(payload)', '@JavascriptInterface public void requestEntityOverlay(String entityKey)',
    'function activeEntityKey()', 'return \'jane_the_killer\''
]:
    if marker not in text:
        raise RuntimeError("Local Entity contract missing: " + marker)

MAIN.write_text(text, encoding="utf-8")
print("Local Entity runtime installed with canonical asset keys only; no legacy ENT identifiers or registry rendering fallback.")
