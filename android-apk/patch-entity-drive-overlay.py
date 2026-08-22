from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


# The Drive manifest is the only remote location baked into the APK. Individual
# Entity image IDs remain data-driven so new/revised images do not require a new APK.
field_anchor = '  private static final int MAX_SNAPSHOT_BASE64 = 1_500_000;\n'
field_addition = field_anchor + (
    '  private static final String ENTITY_MANIFEST_FILE_ID = "1hJ506mvF4SJ84449ktLW6Js0PmSvUTGc";\n'
    '  private static final long ENTITY_MANIFEST_TTL_MS = 60_000L;\n'
    '  private volatile JSONObject entityManifestCache;\n'
    '  private volatile long entityManifestFetchedAtMs;\n'
)
if 'ENTITY_MANIFEST_FILE_ID' not in text:
    text = replace_once(text, field_anchor, field_addition, "Entity manifest fields")

# Explicitly let WebView use its normal HTTP cache for Drive-hosted PNG/WebP assets.
cache_anchor = '      settings.setDomStorageEnabled(true);\n'
cache_line = cache_anchor + '      settings.setCacheMode(WebSettings.LOAD_DEFAULT);\n'
if 'settings.setCacheMode(WebSettings.LOAD_DEFAULT);' not in text:
    text = replace_once(text, cache_anchor, cache_line, "WebView entity image cache")

helpers = r'''  private String entityManifestUrl() {
    return "https://drive.google.com/uc?export=download&id=" + ENTITY_MANIFEST_FILE_ID;
  }

  private String readEntityManifestRemote() throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(entityManifestUrl()).openConnection();
    connection.setRequestMethod("GET");
    connection.setInstanceFollowRedirects(true);
    connection.setConnectTimeout(8000);
    connection.setReadTimeout(8000);
    connection.setRequestProperty("Accept", "application/json,text/plain,*/*");
    connection.setRequestProperty("User-Agent", "BACKROOMS-EntityAssets/1.0");
    int status = connection.getResponseCode();
    InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
    StringBuilder body = new StringBuilder();
    if (stream != null) {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (body.length() > 262144) throw new Exception("Entity manifest vượt giới hạn 256 KiB.");
          body.append(line).append('\n');
        }
      }
    }
    connection.disconnect();
    if (status < 200 || status >= 300) throw new HttpError(status, "Entity manifest HTTP " + status);
    String raw = body.toString().trim();
    if (raw.isEmpty() || raw.charAt(0) != '{') throw new Exception("Entity manifest từ Drive không phải JSON hợp lệ.");
    return raw;
  }

  private JSONObject entityManifest() throws Exception {
    long now = System.currentTimeMillis();
    JSONObject cached = entityManifestCache;
    if (cached != null && now - entityManifestFetchedAtMs < ENTITY_MANIFEST_TTL_MS) {
      return new JSONObject(cached.toString());
    }
    JSONObject remote = new JSONObject(readEntityManifestRemote());
    if (remote.optJSONObject("entities") == null) throw new Exception("Entity manifest thiếu object entities.");
    entityManifestCache = new JSONObject(remote.toString());
    entityManifestFetchedAtMs = now;
    return remote;
  }

  private String normalizedEntityId(String raw) throws Exception {
    String id = raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.ROOT);
    if (!id.matches("ENT-[A-Z0-9]+(?:-[A-Z0-9]+)?")) throw new Exception("Entity ID không hợp lệ: " + id);
    return id;
  }

  private JSONObject resolveEntityOverlay(String rawEntityId) throws Exception {
    String entityId = normalizedEntityId(rawEntityId);
    JSONObject manifest = entityManifest();
    JSONObject entry = manifest.optJSONObject("entities").optJSONObject(entityId);
    if (entry == null) throw new Exception("Drive chưa có asset cho " + entityId + ".");

    String fileId = entry.optString("fileId", "").trim();
    if (!fileId.matches("[A-Za-z0-9_-]{10,}")) throw new Exception("Drive fileId không hợp lệ cho " + entityId + ".");
    int revision = Math.max(1, entry.optInt("revision", 1));
    String format = entry.optString("format", "png").trim().toLowerCase(java.util.Locale.ROOT);
    if (!(format.equals("png") || format.equals("webp") || format.equals("jpg") || format.equals("jpeg"))) {
      throw new Exception("Định dạng Entity không được hỗ trợ: " + format);
    }
    String anchor = entry.optString("anchor", "left-bottom").trim().toLowerCase(java.util.Locale.ROOT);
    double maxHeight = entry.optDouble("maxHeight", 0.97);
    if (Double.isNaN(maxHeight)) maxHeight = 0.97;
    maxHeight = Math.max(0.20, Math.min(1.0, maxHeight));

    String imageUrl = "https://drive.google.com/uc?export=view&id=" +
      java.net.URLEncoder.encode(fileId, "UTF-8") + "&rev=" + revision;
    return new JSONObject()
      .put("entityId", entityId)
      .put("name", entry.optString("name", entityId))
      .put("revision", revision)
      .put("format", format)
      .put("anchor", anchor)
      .put("maxHeight", maxHeight)
      .put("url", imageUrl);
  }

'''
helper_anchor = '  private boolean retryable(int code) {\n'
if 'private JSONObject resolveEntityOverlay(' not in text:
    text = replace_once(text, helper_anchor, helpers + helper_anchor, "Entity Drive helpers")

# Allow a previously-active entityEncounterKey to be cleared on a later turn even
# when no new encounter roll succeeds. Otherwise an old monster could haunt the UI
# forever, which is impressive but not the requested feature.
flag_old = r'''    if (root.equals("jeff") || root.equals("entityRegistry") || root.equals("entitiesConfirmedLocal") || root.equals("entityEncounterKey")) {
      JSONObject flags = before.optJSONObject("flags");
      return rollSuccess(rolls, "entityEncounter") || (flags != null && flags.optInt("entitiesConfirmedLocal", 0) > 0);
    }
'''
flag_new = r'''    if (root.equals("jeff") || root.equals("entityRegistry") || root.equals("entitiesConfirmedLocal") || root.equals("entityEncounterKey")) {
      JSONObject flags = before.optJSONObject("flags");
      if (root.equals("entityEncounterKey") && flags != null && !flags.optString("entityEncounterKey", "").trim().isEmpty()) return true;
      return rollSuccess(rolls, "entityEncounter") || (flags != null && flags.optInt("entitiesConfirmedLocal", 0) > 0);
    }
'''
if flag_new not in text:
    text = replace_once(text, flag_old, flag_new, "Entity encounter visual-state clear gate")

# Teach the writer that entityEncounterKey is CURRENT visual presence, not a history log.
writer_start = text.find('  private String writerPrompt(JSONObject before, String action, JSONObject rolls, JSONArray auditFeedback) throws Exception {')
writer_end = text.find('  private JSONArray localKnowledgeIssues(', writer_start)
if writer_start < 0 or writer_end < 0:
    raise RuntimeError("writerPrompt boundary not found for Entity overlay contract")
writer = text[writer_start:writer_end]
writer_marker = (
    '      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory. " +\n'
)
writer_rule = writer_marker + (
    '      "ENTITY OVERLAY HARD LOCK: với Entity thường đã được xác nhận và đang trực tiếp hiện diện trong cảnh hiện tại, dùng flag_patch root=entityEncounterKey value=exact canon ID dạng ENT-1A/ENT-2C. Khi Entity đó rời cảnh, biến mất hoặc không còn trực tiếp hiện diện, đặt entityEncounterKey thành chuỗi rỗng. Jeff the Killer có thể được renderer nhận trực tiếp từ flag jeff present/spawned. Không dùng tên thường thay cho Entity ID. " +\n'
)
if 'ENTITY OVERLAY HARD LOCK:' not in writer:
    if writer_marker not in writer:
        raise RuntimeError("writerPrompt Entity overlay insertion marker not found")
    writer = writer.replace(writer_marker, writer_rule, 1)
    text = text[:writer_start] + writer + text[writer_end:]

# Layer a Drive-backed Entity on the LEFT while preserving the existing Kai overlay
# on the RIGHT and the existing Snapshot background beneath both.
request_marker = (
    '      "var snapshotBusy=false;function requestSnapshot(){var s=document.getElementById(\'status\');if(s)s.textContent=\'Snapshot chưa được cấu hình.\';}" +\n'
)
entity_js = r'''      "var __baseRenderSnapshot=renderSnapshot,__entityOverlay={id:'',url:'',revision:0,anchor:'left-bottom',maxHeight:.97,loading:''};" +
      "function normalizeEntityId(v){if(typeof v!=='string')return '';var m=v.toUpperCase().match(/ENT-[A-Z0-9]+(?:-[A-Z0-9]+)?/);return m?m[0]:'';}" +
      "function activeEntityId(){var f=state&&state.flags||{};if(f.jeff&&(f.jeff.present===true||f.jeff.spawned===true))return 'ENT-R01';var direct=normalizeEntityId(f.entityEncounterKey);if(direct)return direct;var reg=f.entityRegistry;if(Array.isArray(reg)){for(var i=reg.length-1;i>=0;i--){var r=reg[i],id=normalizeEntityId(r&&typeof r==='object'?(r.entityId||r.id||r.code||''):r);if(id&&(!r||typeof r!=='object'||(r.present!==false&&r.active!==false&&r.departed!==true)))return id;}}else if(reg&&typeof reg==='object'){var keys=Object.keys(reg);for(var j=keys.length-1;j>=0;j--){var id2=normalizeEntityId(keys[j])||normalizeEntityId(reg[keys[j]]&&reg[keys[j]].entityId);var rec=reg[keys[j]];if(id2&&(!rec||typeof rec!=='object'||(rec.present!==false&&rec.active!==false&&rec.departed!==true)))return id2;}}return '';}" +
      "function requestEntityOverlay(id){if(!id||__entityOverlay.loading===id)return;if(!window.Android||typeof Android.requestEntityOverlay!=='function')return;__entityOverlay.loading=id;Android.requestEntityOverlay(id);}" +
      "function appendEntityOverlay(){var box=document.getElementById('snapshot');if(!box)return;var old=box.querySelector('.snapshot-entity');if(old)old.remove();var id=activeEntityId();if(!id){__entityOverlay={id:'',url:'',revision:0,anchor:'left-bottom',maxHeight:.97,loading:''};return;}if(__entityOverlay.id!==id){__entityOverlay.url='';__entityOverlay.id=id;}if(!__entityOverlay.url){requestEntityOverlay(id);return;}var img=document.createElement('img');img.className='snapshot-entity';img.src=__entityOverlay.url;img.alt=id;img.style.position='absolute';img.style.bottom='0';img.style.width='auto';img.style.maxWidth='55%';img.style.height=Math.round(Math.max(.2,Math.min(1,Number(__entityOverlay.maxHeight)||.97))*100)+'%';img.style.objectFit='contain';img.style.pointerEvents='none';img.style.zIndex='2';img.style.imageRendering='auto';if(String(__entityOverlay.anchor||'').indexOf('right')===0){img.style.right='0';img.style.objectPosition='right bottom';}else{img.style.left='0';img.style.objectPosition='left bottom';}box.appendChild(img);}" +
      "renderSnapshot=function(){__baseRenderSnapshot();appendEntityOverlay();};" +
      "window.backroomEntityOverlay=function(payload){try{var r=JSON.parse(payload);var id=normalizeEntityId(r.entityId);if(!id)return;__entityOverlay.loading='';if(id!==activeEntityId())return;__entityOverlay.id=id;__entityOverlay.url=String(r.url||'');__entityOverlay.revision=Number(r.revision||1);__entityOverlay.anchor=String(r.anchor||'left-bottom');__entityOverlay.maxHeight=Number(r.maxHeight||.97);renderSnapshot();}catch(e){__entityOverlay.loading='';}};" +
      "window.backroomEntityOverlayError=function(payload){try{var r=JSON.parse(payload);if(normalizeEntityId(r.entityId)===__entityOverlay.id)__entityOverlay.loading='';}catch(e){__entityOverlay.loading='';}};" +
'''
if 'window.backroomEntityOverlay=function(payload)' not in text:
    text = replace_once(text, request_marker, entity_js + request_marker, "Entity Snapshot overlay renderer")

# Native bridge resolves only manifest metadata; the WebView loads the public image URL
# directly from Drive and may cache it using its normal HTTP cache.
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
              .put("message", error.getMessage() == null ? "Không thể tải Entity asset." : error.getMessage());
            emit("backroomEntityOverlayError", payload.toString());
          } catch (Exception ignored) {
            emit("backroomEntityOverlayError", "{\"entityId\":\"\",\"message\":\"Không thể tải Entity asset.\"}");
          }
        }
      });
    }
'''
if '@JavascriptInterface public void requestEntityOverlay(String entityId)' not in text:
    text = replace_once(text, bridge_marker, bridge_new, "Entity Android bridge")

required = [
    'ENTITY_MANIFEST_FILE_ID = "1hJ506mvF4SJ84449ktLW6Js0PmSvUTGc"',
    'settings.setCacheMode(WebSettings.LOAD_DEFAULT);',
    'private JSONObject resolveEntityOverlay(',
    'ENTITY OVERLAY HARD LOCK:',
    'window.backroomEntityOverlay=function(payload)',
    "img.className='snapshot-entity'",
    "img.style.left='0'",
    '@JavascriptInterface public void requestEntityOverlay(String entityId)',
]
for marker in required:
    if marker not in text:
        raise RuntimeError(f"Entity Drive overlay contract missing: {marker}")

MAIN.write_text(text, encoding="utf-8")
print("Drive-backed Entity overlay installed: manifest lookup, direct Drive image URL, WebView cache, left Snapshot layer, Kai preserved on right.")
