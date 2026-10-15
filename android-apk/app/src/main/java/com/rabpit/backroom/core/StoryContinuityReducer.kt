package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/**
* StoryContinuityReducer — "ĐÃ XẢY RA / TRẠNG THÁI HIỆN TẠI" notes for the GM prompt.
*
* Standalone, pure-Kotlin reducer (org.json only, no Android dependencies).
* It derives continuity notes EXCLUSIVELY from Core-confirmed game state and NEVER
* from GM prose: nothing here is inferred from narration text, nothing is invented.
*
* Input: the prompt-facing GameState JSON (the same shape MainActivity passes to
* writerPrompt — clientSnapshot projection): level, location, title, flags,
* party, encounteredCharacters, pendingNpcEncounters, pendingEntityEncounter, pendingEncounter, combat,
* inventory, player, log, turn.
*
* Output: Vietnamese directive notes with two logical halves:
* - TRẠNG THÁI HIỆN TẠI: the true current Level/location, exit status, who is
* present, pending/active combat — hard anchors the GM must not contradict.
* - ĐÃ XẢY RA: Core-confirmed past events (first meetings, reserved encounters,
* discovered exits) that must NOT be re-narrated as if happening for the
* first time.
*
* NPC vocabulary (matches KnowledgeContextEngine / CoreClientProjection):
* - "encountered" = đã gặp (Core marked encountered=true at least once).
* - "present" = đang hiện diện trong cảnh, có thể tương tác.
* - "inParty" = đã chính thức gia nhập Party qua Core-accepted process.
* Present does NOT imply inParty. The reducer states the distinction explicitly.
*
* Exit vocabulary (matches HiddenExitStreak):
* - exitReady=false -> no Exit discovered; GM must not invent one.
* - exitReady=true (+ confirmedExit/candidate) -> Exit ALREADY discovered, player has
* NOT traversed it; GM must not narrate it as newly appearing.
* - a completed transition is simply the new current Level (Core commits moves
* atomically) — the Level anchor below is the only truth.
*
* Safety: never throws. Malformed/blank input yields "" (caller must tolerate an
* empty string). An Nhien (Legacy) entries are silently skipped.
*/
object StoryContinuityReducer {

/** Legacy character: kept in data, never surfaced to the GM. */
private const val AN_NHIEN_ID = "an_nhien"

/**
* Builds continuity notes from the pre-turn state plus Core-confirmed pending encounters
* and the current turn's rolls. Existing exits and new discoveries stay distinct.
* Returns "" when the input is blank or unparseable.
*/
@JvmStatic
fun continuityNotes(stateJson: String, rollsJson: String = "{}"): String {
if (stateJson.isBlank()) return ""
val state = try {
JSONObject(stateJson)
} catch (_: Exception) {
return ""
}
val rolls = runCatching { JSONObject(rollsJson) }.getOrElse { JSONObject() }
val turn = state.optInt("turn", -1)
val sb = StringBuilder()
sb.append("══ TIẾP NỐI CỐT TRUYỆN — SỰ THẬT DO GAME CORE XÁC NHẬN")
if (turn > 0) sb.append(" (lượt ").append(turn).append(')')
sb.append(" ══\n")
sb.append("Chỉ kể dựa trên các sự thật dưới đây và log các lượt gần nhất. ")
sb.append("CẤM suy diễn thêm sự kiện mới từ 'trí nhớ' của GM; ")
sb.append("một phát hiện đã được kể ở lượt trước thì ở lượt này là 'đã biết', không phải 'vừa phát hiện'.\n")

appendLevelAnchor(sb, state)
appendExitStatus(sb, state, rolls)
appendNpcContinuity(sb, state)
appendEntityContinuity(sb, state)
appendInventoryContinuity(sb, state)

return sb.toString().trim()
}

// ---------------------------------------------------------------------------
// 1. Level anchor — the single most important anti-hallucination guard.
// Diagnostic (4): narration implied Cao Minh nearly finished a Level while
// Core still confirmed Level 0. The true Level is stated here, verbatim.
// ---------------------------------------------------------------------------

private fun appendLevelAnchor(sb: StringBuilder, state: JSONObject) {
val level = state.optJSONObject("level")
val location = state.optString("location", "").trim()
val title = state.optString("title", "").trim()
sb.append("\n\n")
if (level!= null) {
val id = level.optString("id", "").trim()
val number = level.opt("number")
val name = level.optString("name", "").trim()
val label = buildString {
if (id.isNotBlank()) append("node '").append(id).append('\'')
if (number!= null && number!= JSONObject.NULL) append(" (Level ").append(number).append(')')
if (name.isNotBlank()) append(" — ").append(name)
}.ifBlank { "không xác định"}
sb.append("- Cao Minh đang ở: ").append(label).append(".\n")
} else {
sb.append("- Level hiện tại: không xác định được từ state — KHÔNG suy đoán Level.\n")
}
if (location.isNotBlank()) sb.append("- Địa điểm: ").append(location).append(".\n")
if (title.isNotBlank()) sb.append("- Tiêu đề khu vực: ").append(title).append(".\n")
sb.append("- Đây là vị trí THẬT. CẤM kể như Cao Minh đã sang Level/khu vực khác ")
sb.append("nếu không có sự kiện chuyển Level được Core xác nhận ở lượt này.\n")
}

// ---------------------------------------------------------------------------
// 2. Exit status — three mutually exclusive Core states.
// Diagnostic (6): already-discovered exits were re-narrated as newly appearing
// across consecutive turns. The exact status below forbids that.
// ---------------------------------------------------------------------------

private fun appendExitStatus(sb: StringBuilder, state: JSONObject, rolls: JSONObject) {
val exploration = state.optJSONObject("flags")?.optJSONObject("exploration")
val wasReady = HiddenExitStreak.ready(state)
val newlyReady = HiddenExitStreak.newlyReady(state, rolls)
sb.append("\n\n")
if (!wasReady && !newlyReady) {
sb.append("- Chưa phát hiện Exit nào. CẤM tự bịa thêm Exit/cánh cửa thoát mới ")
sb.append("để 'chống nhàm chán' hoặc tạo cao trào.\n")
return
}
if (newlyReady) {
sb.append("- EXIT VỪA ĐỦ ĐIỀU KIỆN Ở LƯỢT NÀY. Có thể hé lộ Exit thật hợp canon; ")
sb.append("đây là phát hiện mới, không phải Exit đã được kể từ trước.\n")
sb.append("- Exit available không đồng nghĩa đã chuyển Level. Chỉ kể chuyển Level khi Core xác nhận.\n")
return
}
sb.append("- EXIT ĐÃ ĐƯỢC PHÁT HIỆN TỪ TRƯỚC và vẫn còn đó. ")
val detail = exitDetail(exploration)
if (detail.isNotBlank()) sb.append("Dấu hiệu đã biết: ").append(detail).append(". ")
sb.append("Nó đã tồn tại trong cảnh — CẤM kể như vừa xuất hiện lần đầu, ")
sb.append("CẤM 'phát hiện lại' nó ở lượt này.\n")
sb.append("- Người chơi CHƯA bước qua Exit này, nên Level hiện tại KHÔNG đổi. ")
sb.append("Chỉ chuyển Level khi Core xác nhận hành động 'đi qua' hợp lệ; ")
sb.append("mô tả một cánh cửa KHÔNG đồng nghĩa đã sang Level khác.\n")
}

/** Best-effort human-readable description of the already-discovered exit. */
private fun exitDetail(exploration: JSONObject?): String {
if (exploration == null) return ""
for (key in arrayOf("confirmedExit", "exitCandidate")) {
val raw = exploration.opt(key)?: continue
if (raw == JSONObject.NULL) continue
if (raw is JSONObject) {
val desc = raw.optString("description", "").trim()
if (desc.isNotBlank()) return desc
val name = raw.optString("name", "").trim()
val id = raw.optString("id", "").trim()
return listOf(name, id).filter { it.isNotBlank()}.joinToString(" ").ifBlank { raw.toString()}
}
val text = raw.toString().trim()
if (text.isNotBlank()) return text
}
return ""
}

// ---------------------------------------------------------------------------
// 3. NPC continuity — encountered vs present vs inParty, stated explicitly.
// Lucia present-but-not-recruited MUST stay inParty=false.
// ---------------------------------------------------------------------------

private fun appendNpcContinuity(sb: StringBuilder, state: JSONObject) {
val lines = mutableListOf<String>()

// 3a. Core-confirmed first meetings for THIS turn (not yet committed to
// encounteredCharacters): narrate the first meeting NOW, still not Party.
state.optJSONArray("pendingNpcEncounters")?.let { pending ->
for (i in 0 until pending.length()) {
val event = pending.optJSONObject(i)?: continue
val id = event.optString("characterId", event.optString("flagKey", "")).trim()
if (id.isBlank() || id == AN_NHIEN_ID) continue
val name = event.optString("name", id).trim().ifBlank { id}
lines.add(
"- $name: Core xác nhận gặp LẦN ĐẦU ở lượt này — hãy kể cảnh gặp gỡ ngay, " +
"nhưng $name CHƯA gia nhập Party (inParty=false)."
)
}
}

// 3b. Already-encountered characters (Core-committed).
state.optJSONArray("encounteredCharacters")?.let { encountered ->
for (i in 0 until encountered.length()) {
val entry = encountered.optJSONObject(i)?: continue
val id = entry.optString("id", "").trim()
if (id.isBlank() || id == AN_NHIEN_ID) continue
val name = entry.optString("name", id).trim().ifBlank { id}
val turn = entry.optInt("turn", -1)
val present = entry.optBoolean("present", false)
val inParty = entry.optBoolean("inParty", false)
val metWhen = if (turn > 0) " (gặp lần đầu ở lượt $turn)" else ""
when {
present && inParty ->
lines.add("- $name: thành viên Party, đang hiện diện trong cảnh$metWhen.")
present ->
lines.add(
"- $name: đã gặp$metWhen, ĐANG hiện diện trong cảnh, " +
"CHƯA gia nhập Party (inParty=false). Có thể trò chuyện, quan sát, " +
"hành động độc lập — nhưng CẤM kể như đã là đồng đội chính thức."
)
else ->
lines.add(
"- $name: đã gặp$metWhen nhưng hiện KHÔNG có mặt trong cảnh — " +
"CẤM cho xuất hiện đột ngột nếu không có sự kiện Core xác nhận."
)
}
}
}

// 3c. Party members that are not in encounteredCharacters (e.g. Cao Minh's
// own entry is excluded upstream; keep this defensive and deduplicated).
val knownIds = mutableSetOf<String>()
state.optJSONArray("encounteredCharacters")?.let { encountered ->
for (i in 0 until encountered.length()) {
encountered.optJSONObject(i)?.optString("id", "")?.trim()?.let { if (it.isNotBlank()) knownIds.add(it)}
}
}
state.optJSONArray("party")?.let { party ->
for (i in 0 until party.length()) {
val member = party.optJSONObject(i)?: continue
val id = member.optString("id", "").trim()
if (id.isBlank() || id == AN_NHIEN_ID || id in knownIds) continue
val name = member.optString("name", id).trim().ifBlank { id}
val presence = member.optString("presence", "ACTIVE").trim()
lines.add("- $name: trong Party (presence=$presence).")
}
}

sb.append("\n\n")
if (lines.isEmpty()) {
sb.append("- Chưa gặp NPC nào được Core xác nhận. CẤM gọi tên NPC chưa tự giới thiệu ")
sb.append("như thể Cao Minh đã biết danh tính.\n")
} else {
lines.forEach { sb.append(it).append('\n')}
}
}

// ---------------------------------------------------------------------------
// 4. Entity continuity — pending/active encounters and the most recent one.
// A reserved encounter must be narrated (appearance only); a past one must
// not be re-narrated as new.
// ---------------------------------------------------------------------------

private fun appendEntityContinuity(sb: StringBuilder, state: JSONObject) {
val lines = mutableListOf<String>()

(state.optJSONObject("pendingEntityEncounter") ?: state.optJSONObject("pendingEncounter"))?.let { pending ->
val entityName = pending.optString("entityName", "").trim()
val entityKey = pending.optString("entityKey", "").trim()
val encounterId = pending.optString("encounterId", "").trim()
val label = entityName.ifBlank { entityKey}.ifBlank { "Entity chưa rõ"}
lines.add(
"- $label ĐÃ xuất hiện và đang chờ Cao Minh xác nhận đối đầu" +
(if (encounterId.isNotBlank()) " (ID: $encounterId)" else "") +
". Kể cảnh xuất hiện, DỪNG ở thế giằng co — " +
"CẤM kể trước sát thương, kết quả trận đánh hoặc phần thưởng."
)
}

state.optJSONObject("combat")?.let { combat ->
val entityName = combat.optString("entityName", combat.optString("entityKey", "")).trim()
if (entityName.isNotBlank()) {
lines.add("- ĐANG TRONG TRẬN CHIẾN với $entityName. Tiếp tục diễn biến combat hiện tại; không mở encounter mới.")
}
}

val flags = state.optJSONObject("flags")
val recentKey = flags?.optString("entityEncounterKey", "")?.trim().orEmpty()
val hasPendingOrActive = state.optJSONObject("pendingEntityEncounter") != null ||
state.optJSONObject("pendingEncounter")!= null ||
state.optJSONObject("combat")!= null
if (recentKey.isNotBlank() &&!hasPendingOrActive) {
lines.add(
"- Cuộc chạm trán gần nhất với Entity '$recentKey' đã xảy ra và đã được kể — " +
"CẤM kể lại như một cuộc gặp mới."
)
}

if (lines.isNotEmpty()) {
sb.append("\n\n")
lines.forEach { sb.append(it).append('\n')}
}
}

// ---------------------------------------------------------------------------
// 5. Important objects — items Cao Minh already carries must not be
// re-introduced as fresh discoveries.
// ---------------------------------------------------------------------------

private fun appendInventoryContinuity(sb: StringBuilder, state: JSONObject) {
val names = mutableListOf<String>()
state.optJSONArray("inventory")?.let { inventory ->
for (i in 0 until inventory.length()) {
val item = inventory.optJSONObject(i)?: continue
val name = item.optString("name", "").trim()
if (name.isBlank()) continue
val qty = item.optInt("quantity", 1)
names.add(if (qty > 1) "$name x$qty" else name)
}
}
if (names.isNotEmpty()) {
sb.append("\n\n")
sb.append("- Cao Minh đang mang: ").append(names.joinToString("; ")).append(".\n")
sb.append("- Những vật này ĐÃ tồn tại — CẤM giới thiệu lại như vừa nhặt/vừa phát hiện.\n")
}
}

/** Visible for tests: renders notes for an empty-but-valid state. */
internal fun emptyStateNotes(): String = continuityNotes("{}")
}
