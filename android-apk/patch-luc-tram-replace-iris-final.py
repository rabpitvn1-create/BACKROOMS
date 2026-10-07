from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
ASSETS = ROOT / "app/src/main/assets"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ASSETS / "index.html"

LUC_TRAM_ID = "luc_tram"
LUC_TRAM_NAME = "Lục Trầm"
LUC_TRAM_AVATAR = "avatars/luctram_avatar.png"
LUC_TRAM_OVERLAY = "luctram_overlay.png"
LUCIA_OVERLAY = "lucia_luc_overlay.png"

# This is deliberately a final authority patch. Earlier historical patches still
# generate Iris-shaped compatibility surfaces; this patch converts the generated
# runtime after every upstream finalizer has completed.
required_assets = [
    ASSETS / LUC_TRAM_AVATAR,
    ASSETS / LUC_TRAM_OVERLAY,
    ASSETS / LUCIA_OVERLAY,
]
for path in required_assets:
    if not path.is_file() or path.stat().st_size <= 0:
        raise RuntimeError("Missing character visual asset: " + str(path))

def rewrite(path: Path, replacements):
    if not path.exists():
        return
    text = path.read_text(encoding="utf-8")
    for old, new in replacements:
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")

# Core identity and save/runtime keys. Keep Lucia distinct: lucia != luc_tram.
for path in list(CORE.glob("*.kt")) + list(TESTS.glob("*.kt")):
    rewrite(path, [
        ("IRIS_ID", "LUC_TRAM_ID"),
        ("IRIS_", "LUC_TRAM_"),
        ("irisReunion", "lucTramReunion"),
        ("Iris", "Lục Trầm"),
        ("iris", "luc_tram"),
        ("avatars/Iris_avatar.jpg", LUC_TRAM_AVATAR),
        ("/luc_tram123", "/luctram123"),
        ("Ivory & Ebony", "Tịch Quang"),
        ("Blackblood Recon Frame R03", "Thiên Cơ Bạch Kim Kiếm Khải"),
        ("Scout / Target Eliminator", "Chính Đạo Kiếm Tu / Thiên Kiếm Môn"),
        ("Gunslinger", "Kiếm Tu"),
    ])

# Final canonical stat identity.
stats = CORE / "CharacterStats.kt"
if stats.exists():
    text = stats.read_text(encoding="utf-8")
    text = text.replace(
        '"luc_tram" -> "SCOUT / TARGET ELIMINATOR / DUAL-GUN MARKSMAN"',
        '"luc_tram" -> "CHÍNH ĐẠO KIẾM TU / THIÊN KIẾM MÔN / CHÂN TRUYỀN ĐỆ TỬ"'
    )
    text = text.replace(
        '"cao_minh", "kai", "luc_tram", "syvial" -> EnergyProfile.infinite()',
        '"cao_minh", "kai", "syvial" -> EnergyProfile.infinite()'
    )
    stats.write_text(text, encoding="utf-8")

# Replace the retired Iris skill presentation with the locked Lục Trầm projection.
catalog = CORE / "CompanionSkillCatalog.kt"
if catalog.exists():
    text = catalog.read_text(encoding="utf-8")
    start = text.find("  private val luc_tram = listOf(")
    end = text.find("\n\n  private val syvial =", start)
    if start < 0 or end < 0:
        raise RuntimeError("Lục Trầm skill catalog boundary missing after compatibility rewrite")
    block = r'''  private val luc_tram = listOf(
    s("Thiên Kiếm Linh Tâm", "PASSIVE", "Luôn hoạt động khi ACTIVE", "Đọc quỹ đạo, trọng tâm, biến đổi linh lực và điểm bất ổn trong thế kiếm/phòng thủ.", "Không đọc suy nghĩ, không tự nhận dạng Entity hay luật Backrooms."),
    s("Tịch Quang Hợp Kích", "AUTO", "Lượt TẤN CÔNG hợp lệ", "Đòn kiếm hợp kích giữ projection 150% damage của slot active đã nghỉ hưu."),
    s("Tịch Quang Phản Kiếm", "AUTO", "Proc theo slot gameplay hiện hành", "Phản kiếm bằng Tịch Quang; giữ nguyên tỷ lệ và damage projection của slot runtime."),
    s("Nhất Tuyến Phá Vọng", "AUTO", "Proc theo slot gameplay hiện hành", "Đánh vào điểm bất ổn; giữ nguyên tỷ lệ/status projection của slot runtime."),
    s("Thiên Kiếm Chấn", "AUTO", "Proc theo slot gameplay hiện hành", "Kiếm chấn áp chế mục tiêu; giữ nguyên tỷ lệ/status projection của slot runtime."),
    s("Bạch Hồng Quán Nhật", "AUTO", "Proc theo slot gameplay hiện hành", "Kiếm thế xuyên phá; giữ nguyên tỷ lệ/armor-pierce projection của slot runtime."),
    s("Vạn Kiếm Quy Tâm", "AUTO", "Proc theo slot gameplay hiện hành", "Kiếm ý hội tụ; giữ nguyên tỷ lệ/damage projection của slot runtime."),
    s("Thiên Kiếm Định Giới", "ULTIMATE", "Theo chu kỳ Ultimate hiện hành", "Đúng 60 hit; mỗi hit giữ current-DMG +15% bonus projection.", "Backrooms có thể phá anchor/geometry; không phải instant-kill tuyệt đối.")
  )'''
    text = text[:start] + block + text[end:]
    text = text.replace("LUC_TRAM_ID -> luc_tram", "LUC_TRAM_ID -> luc_tram")
    catalog.write_text(text, encoding="utf-8")

# Combat presentation: remove firearm language from Lục Trầm while preserving the
# established deterministic slot percentages/damage sequencing.
combat = CORE / "CombatRuntime.kt"
if combat.exists():
    text = combat.read_text(encoding="utf-8")
    mapping = {
        "ARGUS Terrain Read": "Thiên Kiếm Linh Tâm",
        "Thousandfold Cognition": "Tịch Quang Phản Kiếm",
        "Twosome Time": "Nhất Tuyến Phá Vọng",
        "Rain Storm": "Thiên Kiếm Chấn",
        "Honeycomb Fire": "Bạch Hồng Quán Nhật",
        "Charged Shot": "Vạn Kiếm Quy Tâm",
        "Dead Angle": "Tịch Quang Phản Kiếm",
        "ARGUS // Thousandfold Execution": "Thiên Kiếm Định Giới",
        "Ivory & Ebony": "Tịch Quang",
        "loạt bắn": "kiếm thế",
        "phát bắn": "nhát kiếm",
        "12 phát": "60 kiếm ảnh",
    }
    for old, new in mapping.items():
        text = text.replace(old, new)
    combat.write_text(text, encoding="utf-8")

# Main legacy bridge and GM lock.
if MAIN.exists():
    text = MAIN.read_text(encoding="utf-8")
    for old, new in [
        ('"iris"', '"luc_tram"'),
        ('"Iris"', '"Lục Trầm"'),
        ("irisReunion", "lucTramReunion"),
        ("IRIS / SYVIAL FOLLOWER LOCK:", "LỤC TRẦM / SYVIAL FOLLOWER LOCK:"),
        ("Iris giữ canon Scout / Target Eliminator, Gunslinger với Tịch Quang và Thiên Cơ Bạch Kim Kiếm Khải; không tự gán cấp chiến lực cho Iris.",
         "Lục Trầm giữ canon R05: Chính Đạo Kiếm Tu, Tịch Quang và Thiên Cơ Bạch Kim Kiếm Khải; không tự gán tri thức Backrooms hoặc nâng power scale ngang Cao Minh."),
    ]:
        text = text.replace(old, new)
    MAIN.write_text(text, encoding="utf-8")

# UI projection: bind overlays by active Party/combat identity. This is appended
# as a final compatibility layer so it works with the generated snapshot DOM.
if INDEX.exists():
    html = INDEX.read_text(encoding="utf-8")
    marker = "/* CHARACTER_COMPANION_OVERLAY_R01 */"
    if marker not in html:
        html += r'''
<script>
/* CHARACTER_COMPANION_OVERLAY_R01 */
(function(){
  const specs = {
    luc_tram: {src:'luctram_overlay.png', alt:'Lục Trầm combat overlay'},
    lucia: {src:'lucia_luc_overlay.png', alt:'Lucia Lục combat overlay'}
  };
  function ids(state){
    const out=[];
    const p=state&&state.party;
    if(Array.isArray(p)) p.forEach(x=>out.push(String((x&&x.id)||x||'').toLowerCase()));
    if(p&&Array.isArray(p.memberIds)) p.memberIds.forEach(x=>out.push(String(x||'').toLowerCase()));
    return out;
  }
  window.renderCharacterCompanionOverlays=function(root,state){
    if(!root)return;
    root.querySelectorAll('[data-character-companion-overlay]').forEach(x=>x.remove());
    const active=ids(state);
    Object.keys(specs).forEach(id=>{
      if(active.indexOf(id)<0)return;
      const img=document.createElement('img');
      img.src=specs[id].src; img.alt=specs[id].alt;
      img.dataset.characterCompanionOverlay=id;
      img.style.cssText='position:absolute;inset:auto 0 0 auto;max-width:46%;max-height:88%;object-fit:contain;pointer-events:none;z-index:4';
      root.appendChild(img);
    });
  };
})();
</script>
'''
        INDEX.write_text(html, encoding="utf-8")

# Knowledge mirrors: remove Iris records/references, then ensure Lục Trầm R05
# identity remains discoverable. Do not merge Lucia into Lục Trầm.
for path in [ASSETS/"knowledge/characters_current.json", ASSETS/"knowledge/knowledge_db.json"]:
    if not path.exists():
        continue
    raw = path.read_text(encoding="utf-8")
    try:
        data = json.loads(raw)
    except Exception:
        continue
    if isinstance(data, dict) and isinstance(data.get("characters"), dict):
        data["characters"].pop("iris", None)
        if "luc_tram" in data["characters"] and isinstance(data["characters"]["luc_tram"], dict):
            data["characters"]["luc_tram"]["revision"] = "R05"
    if isinstance(data, dict) and isinstance(data.get("entries"), list):
        data["entries"] = [e for e in data["entries"] if "IRIS" not in str(e.get("id","")).upper()]
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

# Regression guard: active runtime must expose Lục Trầm, keep Lucia separate,
# and point both requested overlays at real packaged assets.
combined = "\n".join(p.read_text(encoding="utf-8") for p in [CORE/"SpecialFollowersCanon.kt", catalog, combat, MAIN, INDEX] if p.exists())
for required in [
    'const val LUC_TRAM_ID = "luc_tram"',
    'name = "Lục Trầm"',
    LUC_TRAM_AVATAR,
    "Tịch Quang Hợp Kích",
    "Thiên Kiếm Định Giới",
    LUC_TRAM_OVERLAY,
    LUCIA_OVERLAY,
]:
    if required not in combined:
        raise RuntimeError("Lục Trầm/Lucia final contract missing: " + required)
if 'const val LUCIA_ID = "lucia"' not in "\n".join(p.read_text(encoding="utf-8") for p in CORE.glob("*.kt")):
    raise RuntimeError("Lucia must remain a distinct runtime identity")

print("Replaced retired Iris runtime with Lục Trầm R05; Lucia remains distinct; companion overlays bound.")
