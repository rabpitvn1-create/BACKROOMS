from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
FACADE = CORE / "GameCoreFacade.kt"
POLICY = CORE / "GmItemGainPolicy.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/GmItemGainPolicyTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# ---------------------------------------------------------------------------
# Game Master contract: when the GM actually grants loot/reward to Kai, the same
# response must emit a gm_gain inventory_upsert. This is distinct from merely
# describing an item in the environment.
# ---------------------------------------------------------------------------
java = MAIN.read_text(encoding="utf-8")
prompt_anchor = '      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory. " +\n'
prompt_rule = '      "GM ITEM GAIN HARD LOCK: khi reply xác nhận Kai thực sự nhận, nhặt, loot, được trao hoặc được thưởng một item trong chính lượt này, bắt buộc kèm inventory_upsert với basis:\\"gm_gain\\". Với gm_gain, item.quantity là số lượng vừa Gain trong lượt, mặc định 1, không phải tổng tồn kho. Không dùng gm_gain cho vật chỉ được nhìn thấy hoặc nhắc tới nhưng chưa thuộc quyền sở hữu của Kai. " +\n'
if prompt_rule not in java:
    if prompt_anchor not in java:
        raise RuntimeError("Game Master Inventory prompt anchor missing")
    java = java.replace(prompt_anchor, prompt_anchor + prompt_rule, 1)

# The existing reducer already accepts carefully validated world_consequence loot.
# Add one explicit post-audit GM channel without weakening MadGod's special discovery lock.
old_gate = '''        String acquisitionBasis = lower(op.optString("basis", "")).trim();
        boolean worldAcquisition = acquisitionBasis.equals("world_consequence");
        boolean directAcquisition = acquisitionIntent(action);
        boolean copyIntent = containsAny(action, "copy", "sao chép", "nhân bản", "tạo thêm", "tạo ra thêm", "nhân thêm");
        boolean almondRoll = rollSuccess(rolls, "almondWater");
        boolean lootRoll = rollSuccess(rolls, "loot");
        if (existing >= 0) allowedNew = true;
        else if (madGod) allowedNew = directAcquisition && madGodAlreadySpawned && establishedStructured;
        else if (copyIntent) allowedNew = directAcquisition && establishedStructured;
        else if (almond) allowedNew = (directAcquisition || worldAcquisition) && (establishedStructured || almondRoll);
        else allowedNew = (directAcquisition || worldAcquisition) && (establishedStructured || lootRoll);
'''
new_gate = '''        String acquisitionBasis = lower(op.optString("basis", "")).trim();
        boolean worldAcquisition = acquisitionBasis.equals("world_consequence");
        boolean gmGain = acquisitionBasis.equals("gm_gain");
        boolean directAcquisition = acquisitionIntent(action);
        boolean copyIntent = containsAny(action, "copy", "sao chép", "nhân bản", "tạo thêm", "tạo ra thêm", "nhân thêm");
        boolean almondRoll = rollSuccess(rolls, "almondWater");
        boolean lootRoll = rollSuccess(rolls, "loot");
        if (existing >= 0) allowedNew = true;
        else if (madGod) allowedNew = (directAcquisition || gmGain) && madGodAlreadySpawned && establishedStructured;
        else if (copyIntent) allowedNew = gmGain || (directAcquisition && establishedStructured);
        else if (almond) allowedNew = gmGain || ((directAcquisition || worldAcquisition) && (establishedStructured || almondRoll));
        else allowedNew = gmGain || ((directAcquisition || worldAcquisition) && (establishedStructured || lootRoll));
'''
java = replace_once(java, old_gate, new_gate, "gm_gain reducer gate")

# gm_gain quantity is an increment. Existing generic inventory_upsert semantics are
# preserved for every other basis so this does not rewrite unrelated inventory behavior.
old_upsert = '''        if (existing >= 0) inventory.put(existing, new JSONObject(item.toString()));
        else if (allowedNew) inventory.put(new JSONObject(item.toString()));
        state.put("inventory", inventory);
        continue;
'''
new_upsert = '''        int requestedQuantity = Math.max(1, Math.min(999, item.optInt("quantity", 1)));
        if (existing >= 0) {
          if (gmGain) {
            JSONObject previousItem = inventory.optJSONObject(existing);
            JSONObject mergedItem = new JSONObject(item.toString());
            int previousQuantity = previousItem != null ? Math.max(1, previousItem.optInt("quantity", 1)) : 1;
            mergedItem.put("quantity", Math.min(999, previousQuantity + requestedQuantity));
            if (!mergedItem.has("id") && previousItem != null && previousItem.has("id")) mergedItem.put("id", previousItem.get("id"));
            inventory.put(existing, mergedItem);
          } else {
            inventory.put(existing, new JSONObject(item.toString()));
          }
        } else if (allowedNew) {
          JSONObject addedItem = new JSONObject(item.toString());
          if (gmGain) addedItem.put("quantity", requestedQuantity);
          inventory.put(addedItem);
        }
        state.put("inventory", inventory);
        continue;
'''
java = replace_once(java, old_upsert, new_upsert, "gm_gain increment semantics")

# Capture authoritative gain notifications returned by Game State Core.
core_commit_anchor = '          candidateState = coreCommit.getJSONObject("state");\n'
core_commit_new = core_commit_anchor + '          JSONArray gainNotifications = coreCommit.optJSONArray("gainNotifications");\n'
java = replace_once(java, core_commit_anchor, core_commit_new, "gain notification bridge")

log_anchor = '          log.put(new JSONObject().put("role", "gm").put("text", reply));\n'
log_new = log_anchor + '''          if (gainNotifications != null) {
            for (int gainIndex = 0; gainIndex < gainNotifications.length(); gainIndex++) {
              JSONObject gain = gainNotifications.optJSONObject(gainIndex);
              if (gain == null) continue;
              String gainName = gain.optString("name", "Item").trim();
              int gainQuantity = Math.max(1, gain.optInt("quantity", 1));
              log.put(new JSONObject().put("role", "gain").put("text", "Gain " + gainName + " ×" + gainQuantity + " Item"));
            }
          }
'''
java = replace_once(java, log_anchor, log_new, "gain chat log insertion")
MAIN.write_text(java, encoding="utf-8")


# ---------------------------------------------------------------------------
# Core policy: only positive deltas from the already Android-validated candidate
# become authoritative PICKUP commands. Candidate removals remain read-only.
# ---------------------------------------------------------------------------
POLICY.write_text(r'''package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

data class GmItemGain(
  val itemId: String,
  val itemName: String,
  val quantity: Int,
  val metadata: Map<String, String>
)

object GmItemGainPolicy {
  private data class Desired(
    val itemId: String,
    val itemName: String,
    val quantity: Int,
    val metadata: Map<String, String>
  )

  private fun stableItemId(name: String): String = name.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').ifBlank { "item-${name.hashCode().toUInt()}" }

  private fun metadata(json: JSONObject?): Map<String, String> {
    if (json == null) return emptyMap()
    val result = linkedMapOf<String, String>()
    json.keys().forEach { key -> result[key] = json.optString(key) }
    return result
  }

  fun positiveDeltas(current: Map<String, ItemStack>, candidateInventory: JSONArray?): List<GmItemGain> {
    if (candidateInventory == null) return emptyList()
    val desired = linkedMapOf<String, Desired>()
    for (index in 0 until candidateInventory.length()) {
      val json = candidateInventory.optJSONObject(index) ?: continue
      val name = json.optString("name").trim()
      if (name.isBlank()) continue
      val byName = current.values.firstOrNull { it.name.equals(name, ignoreCase = true) }
      val explicitId = json.optString("id").trim()
      val id = explicitId.ifBlank { byName?.itemId ?: stableItemId(name) }
      val old = current[id] ?: byName
      val quantity = json.optInt("quantity", 1).coerceIn(1, 999)
      val mergedMetadata = old?.metadata.orEmpty() + metadata(json.optJSONObject("metadata"))
      desired[id] = Desired(id, name, quantity, mergedMetadata)
    }

    return desired.values.mapNotNull { item ->
      val currentStack = current[item.itemId] ?: current.values.firstOrNull { it.name.equals(item.itemName, ignoreCase = true) }
      val oldQuantity = currentStack?.quantity ?: 0
      val delta = item.quantity - oldQuantity
      if (delta <= 0) null else GmItemGain(item.itemId, item.itemName, delta, item.metadata)
    }
  }
}
''', encoding="utf-8")

facade = FACADE.read_text(encoding="utf-8")
core_anchor = '    val inventoryLocked = true // INVENTORY_AUTHORITY: candidate snapshots are read-only\n'
core_gain = core_anchor + '''    val gmItemGains = GmItemGainPolicy.positiveDeltas(current, candidate.optJSONArray("inventory"))
    gmItemGains.forEachIndexed { index, gain ->
      commands += ItemCommand(
        commandId = "$turnId:GEMINI:GM_GAIN:$index",
        turnId = turnId,
        actorId = KAI_ID,
        source = CommandSource.GEMINI,
        operation = ItemCommand.Operation.PICKUP,
        itemId = gain.itemId,
        itemName = gain.itemName,
        quantity = gain.quantity,
        metadata = gain.metadata
      )
    }
'''
facade = replace_once(facade, core_anchor, core_gain, "authoritative positive GM gain commands")

return_anchor = '    return response(true, synchronized, null, "gemini_delta_committed")\n'
return_new = '''    val payload = JSONObject(response(true, synchronized, null, "gemini_delta_committed"))
    if (gmItemGains.isNotEmpty()) {
      payload.put("gainNotifications", JSONArray().apply {
        gmItemGains.forEach { gain -> put(JSONObject().put("name", gain.itemName).put("quantity", gain.quantity)) }
      })
    }
    return payload.toString()
'''
facade = replace_once(facade, return_anchor, return_new, "Game Core gain notification payload")
FACADE.write_text(facade, encoding="utf-8")


# ---------------------------------------------------------------------------
# Compact in-chat presentation for Gain messages.
# ---------------------------------------------------------------------------
html = INDEX.read_text(encoding="utf-8")
warning_css = '.message.warning{border-left-color:#d99a2b;background:#231a0b}.message.warning .role{color:#e3a83a}.message.warning .text{color:#ffd27a;font-weight:650}'
gain_css = warning_css + '.message.gain{margin:5px 0 9px;padding:6px 9px;border-left-width:2px;background:#151b1f}.message.gain .role{font-size:9px;letter-spacing:.16em}.message.gain .text{font-size:12px;font-weight:700}'
html = replace_once(html, warning_css, gain_css, "Gain chat CSS")

old_renderer = 'logEl.innerHTML=(state.log||[]).map(x=>{const w=x.role!=="player"&&String(x.text||"").trim().startsWith("[Warning]");return "<article class=\'message "+(x.role==="player"?"player":"")+(w?" warning":"")+"\'><div class=\'role\'>"+(x.role==="player"?"BẠN":"GAME MASTER")+"</div><div class=\'text\'>"+esc(x.text)+"</div></article>"}).join("")'
new_renderer = 'logEl.innerHTML=(state.log||[]).map(x=>{const w=x.role!=="player"&&String(x.text||"").trim().startsWith("[Warning]"),g=x.role==="gain";return "<article class=\'message "+(x.role==="player"?"player":"")+(w?" warning":"")+(g?" gain":"")+"\'><div class=\'role\'>"+(x.role==="player"?"BẠN":g?"GAIN":"GAME MASTER")+"</div><div class=\'text\'>"+esc(x.text)+"</div></article>"}).join("")'
html = replace_once(html, old_renderer, new_renderer, "Gain chat renderer")
INDEX.write_text(html, encoding="utf-8")


# ---------------------------------------------------------------------------
# Regression coverage for positive-only authoritative deltas.
# ---------------------------------------------------------------------------
TEST.write_text(r'''package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GmItemGainPolicyTest {
  @Test fun newCandidateItemBecomesOneGain() {
    val candidate = JSONArray().put(JSONObject().put("name", "Almond Water").put("quantity", 1))
    val gains = GmItemGainPolicy.positiveDeltas(emptyMap(), candidate)
    assertEquals(1, gains.size)
    assertEquals("almond-water", gains.single().itemId)
    assertEquals("Almond Water", gains.single().itemName)
    assertEquals(1, gains.single().quantity)
  }

  @Test fun existingStackUsesOnlyPositiveDifference() {
    val current = mapOf("bandage" to ItemStack("bandage", "Bandage", 2))
    val candidate = JSONArray().put(JSONObject().put("id", "bandage").put("name", "Bandage").put("quantity", 3))
    val gains = GmItemGainPolicy.positiveDeltas(current, candidate)
    assertEquals(1, gains.size)
    assertEquals(1, gains.single().quantity)
  }

  @Test fun idlessExistingItemStillMatchesByName() {
    val current = mapOf("custom:flash" to ItemStack("custom:flash", "Emergency Flare", 1))
    val candidate = JSONArray().put(JSONObject().put("name", "Emergency Flare").put("quantity", 2))
    val gains = GmItemGainPolicy.positiveDeltas(current, candidate)
    assertEquals("custom:flash", gains.single().itemId)
    assertEquals(1, gains.single().quantity)
  }

  @Test fun candidateRemovalNeverBecomesAuthoritativeGain() {
    val current = mapOf("bandage" to ItemStack("bandage", "Bandage", 2))
    val candidate = JSONArray().put(JSONObject().put("id", "bandage").put("name", "Bandage").put("quantity", 1))
    assertTrue(GmItemGainPolicy.positiveDeltas(current, candidate).isEmpty())
  }
}
''', encoding="utf-8")

combined = MAIN.read_text(encoding="utf-8") + "\n" + FACADE.read_text(encoding="utf-8") + "\n" + INDEX.read_text(encoding="utf-8") + "\n" + POLICY.read_text(encoding="utf-8") + "\n" + TEST.read_text(encoding="utf-8")
for marker in (
    'basis:\\"gm_gain\\"',
    'boolean gmGain = acquisitionBasis.equals("gm_gain")',
    'previousQuantity + requestedQuantity',
    'GmItemGainPolicy.positiveDeltas',
    'ItemCommand.Operation.PICKUP',
    'gainNotifications',
    '"role", "gain"',
    '"Gain " + gainName + " ×" + gainQuantity + " Item"',
    '.message.gain',
    'g?"GAIN":"GAME MASTER"',
    'candidateRemovalNeverBecomesAuthoritativeGain',
):
    if marker not in combined:
        raise RuntimeError("GM item gain final contract missing: " + marker)

print("Game Master item gains finalized: validated GM loot commits directly to Inventory and emits compact Gain chat notices.")
