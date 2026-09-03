package com.rabpit.backroom.core

import org.json.JSONObject
import java.util.Locale

/**
 * Single source of truth for the Entity selected by authoritative encounter dice and
 * for the minimum player-facing narration required by that selection.
 *
 * Combat startup and GM prose must consume the same selected key so an Entity cannot
 * be visible/active in CombatRuntime while the narrative still describes an empty scene.
 */
object EntityEncounterNarrativeAuthority {
  private val uniquePriority = listOf(
    "diepMinhEncounter" to "diep_minh",
    "monsterXEncounter" to "monster_x",
    "johnDoeEncounter" to "john_doe",
    "scp173Encounter" to "scp_173",
    "violetWardenEncounter" to "violet_warden",
    "kaiDevilWithinEncounter" to "kai_the_devil_within"
  )

  @JvmStatic
  fun selectedEntityKey(rollsJson: String): String {
    val rolls = runCatching { JSONObject(rollsJson) }.getOrNull() ?: return ""
    for ((rollName, entityKey) in uniquePriority) {
      if (rolls.optJSONObject(rollName)?.optBoolean("success", false) == true) return entityKey
    }

    val normal = rolls.optJSONObject("entityEncounter")
    if (normal?.optBoolean("success", false) != true) return ""
    return normalizeKey(rolls.optString("roamingEntityKey", ""))
  }

  @JvmStatic
  fun visibleFact(rollsJson: String, displayName: String): String {
    val key = selectedEntityKey(rollsJson)
    if (key.isBlank()) return ""
    val name = displayName.trim().ifEmpty { humanize(key) }
    return "\n\nENTITY ENCOUNTER VISIBLE FACT (HARD LOCK): active=true; canonicalKey=$key; displayName=$name. " +
      "Android đã xác định Entity này xuất hiện trong chính lượt hiện tại và CombatRuntime sẽ bắt đầu với đúng Entity này sau khi lượt được validate. " +
      "Reply của GM bắt buộc phải báo rõ Entity đang trực tiếp hiện diện/đe dọa trước khi tiếp tục mô tả hành động cũ. " +
      "Cấm kể khu vực vẫn trống, yên ổn hoặc không có gì xảy ra. Chỉ báo encounter, không tự bịa kết quả combat."
  }

  @JvmStatic
  fun ensureReply(rollsJson: String, reply: String, displayName: String): String {
    val key = selectedEntityKey(rollsJson)
    val trimmed = reply.trim()
    if (key.isBlank()) return trimmed

    val name = displayName.trim().ifEmpty { humanize(key) }
    val normalizedReply = normalizeText(trimmed)
    val normalizedName = normalizeText(name)
    val deniesEncounter = listOf(
      "vẫn không có gì",
      "không có gì xảy ra",
      "không có thực thể",
      "không thấy thực thể",
      "không có entity",
      "không thấy entity",
      "không có quái",
      "không thấy quái",
      "không có sinh vật",
      "không thấy sinh vật",
      "không có kẻ địch",
      "không thấy kẻ địch",
      "không có mối đe dọa",
      "không thấy mối đe dọa"
    ).any(normalizedReply::contains)
    val acknowledgesEncounter = normalizedName.isNotBlank() && normalizedReply.contains(normalizedName) ||
      listOf("entity", "thực thể", "quái vật", "sinh vật", "kẻ địch", "mối đe dọa").any(normalizedReply::contains)

    val cue = "Ngay lúc đó, $name xuất hiện trong khu vực, cắt ngang hành động của bạn và trở thành mối đe dọa trực tiếp."
    return when {
      deniesEncounter -> "$cue Cuộc chạm trán lập tức chuyển thành đối đầu trực tiếp."
      acknowledgesEncounter -> trimmed
      trimmed.isEmpty() -> cue
      else -> "$cue\n\n$trimmed"
    }
  }

  private fun normalizeKey(value: String): String = value.trim().lowercase(Locale.ROOT)

  private fun normalizeText(value: String): String = value.lowercase(Locale.ROOT)
    .replace('–', '-')
    .replace('—', '-')
    .replace(Regex("\\s+"), " ")
    .trim()

  private fun humanize(key: String): String = key
    .split('_')
    .filter(String::isNotBlank)
    .joinToString(" ") { part -> part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } }
}
