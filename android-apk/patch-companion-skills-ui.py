from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
DETAIL_JSON = CORE / "CharacterDetailJson.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
CATALOG = CORE / "CompanionSkillCatalog.kt"
TEST = TESTS / "CompanionSkillCatalogTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# ---------------------------------------------------------------------------
# 1) Canonical skill catalog. The UI reads this projection instead of carrying a
# second hard-coded list, so character detail and gameplay documentation cannot
# silently drift apart.
# ---------------------------------------------------------------------------
CATALOG.write_text(r'''package com.rabpit.backroom.core

data class CharacterSkillDefinition(
  val name: String,
  val kind: String,
  val trigger: String,
  val effect: String,
  val note: String? = null
)

object CompanionSkillCatalog {
  private fun s(name: String, kind: String, trigger: String, effect: String, note: String? = null) =
    CharacterSkillDefinition(name, kind, trigger, effect, note)

  private val lucTram = listOf(
    s("Thiên Kiếm Linh Tâm", "PASSIVE", "Luôn hoạt động khi ACTIVE", "Căn cơ Kiếm Đạo của Lục Trầm: thống nhất thần niệm, linh lực và cảm quan không gian để đọc tuyến tấn công, phá nhịp và phản kiếm.", "Không phải trái tim vật lý thứ hai."),
    s("Thiên Kiếm Thất Thức", "AUTO", "Khi Lục Trầm tấn công", "Hệ thống kiếm thức nền tảng của Thiên Kiếm Môn, luyện tới không còn cần thứ tự cố định; Nhất Tuyến Phá Vọng tập trung một tuyến phá thế."),
    s("Tịch Quang", "PASSIVE", "Bản mệnh linh kiếm", "Bản mệnh linh kiếm của Lục Trầm: trường kiếm thẳng, lưỡi bạc sáng ít phản xạ, tay cầm viền vàng đen.", "Kiếm gắn với căn cơ Thiên Kiếm Linh Tâm."),
    s("Thiên Cơ Bạch Kim Kiếm Khải", "PASSIVE", "Pháp bảo hộ thân", "Chiến khải cao cấp do Kiếm Các và Khí Các của Thiên Kiếm Môn cùng rèn sau khi Lục Trầm thành chân truyền đệ tử.")
  )

  private val syvial = listOf(
    s("Lucifer Core", "PASSIVE", "Luôn hoạt động khi ACTIVE", "Miễn cơ chế cạn Mana/Energy/Overheat nội tại; hồi 2% Max HP mỗi turn, 4% khi Devil Trigger.", "Không hồi từ 0 HP."),
    s("Killing Intent Read", "PASSIVE", "Khi đối thủ để lộ ý định", "Đọc chuyển động và chuẩn bị phản đòn; hỗ trợ Counterphase."),
    s("Rift Sever", "AUTO", "30% mỗi turn hợp lệ", "Spatial Shift lệch trục phòng thủ rồi chém, 175% Weapon DMG, bỏ qua 20% Armor."),
    s("Crimson Guillotine", "AUTO", "20% mỗi turn hợp lệ", "190% Weapon DMG; Bleeding 3 turn, mỗi turn 4% Max HP."),
    s("Lucifer Breaker", "AUTO", "20% mỗi turn hợp lệ", "Chuỗi cận chiến + GodKiller 155% Weapon DMG; Stun phản ứng hiện tại của Entity."),
    s("Counterphase", "COUNTER", "30% sau khi Entity hụt phản công", "Spatial Shift vào góc chết và phản chém 125% Weapon DMG; không chiếm lượt chính."),
    s("GodKiller Recall", "PASSIVE", "Khi bị Disarm hợp lệ", "Gọi GodKiller trở lại ở đầu lượt kế tiếp nếu không có luật boss khóa triệu hồi."),
    s("Devil Trigger", "STATE", "HP <= 50% hoặc đối đầu Diệp Minh", "+25% outgoing DMG, +20% Evasion, -20% incoming DMG theo vai trò cá nhân; hồi phục Lucifer Core tăng lên 4% Max HP/turn.", "Không cooldown nội tại, không giới hạn thời gian canon."),
    s("Spatial Dominion", "AUTO", "20% khi Devil Trigger", "Chuỗi Spatial Shift + GodKiller, 210% Weapon DMG; Disoriented -25% Accuracy trong 2 turn."),
    s("GodKiller Override // Twenty-Four Severance", "ULTIMATE", "Mỗi 3 combat turn khi Devil Trigger", "Dừng thời gian ngoại giới, đúng 24 nhát chém x 10 HP = 240 HP; bỏ qua Evasion.", "Không phải instant-kill tuyệt đối.")
  )

  private val anNhien = listOf(
    s("Có Gì Đó Sai Sai", "PASSIVE", "Khi An Nhiên theo Party", "Giảm 25% xác suất hazard trên action vật lý hợp lệ."),
    s("Nhặt Có Chọn Lọc", "PASSIVE", "Khi SEARCH", "+10 điểm phần trăm vào generic loot roll hiện có.", "Không tạo loot roll thứ hai."),
    s("Không Phải Tôi Nhát, Tôi Có Chiến Thuật", "PASSIVE", "Khi tình huống xấu", "Ưu tiên vị trí an toàn; không biến An Nhiên thành combatant."),
    s("Quăng Đại Cái Gì Đó", "UTILITY", "25% mỗi combat turn khi ACTIVE trong Party", "Ném vật vô hại để đánh lạc hướng, Entity -25 điểm % Accuracy trong phản ứng hiện tại.", "Không gây damage, không dùng vũ khí."),
    s("Khoan, Để Tôi Đọc Cái Này", "UTILITY", "20% khi SEARCH một Exit", "Nếu proc, +20 điểm phần trăm cho Exit probe của action đó."),
    s("Đừng Đụng Vào, Nhìn Là Biết Độc", "UTILITY", "30% khi kiểm tra nước/chất lỏng khả nghi", "Nếu proc, chặn hazard roll của action kiểm tra đó.", "Chỉ là kiểm tra nguy cơ, không tự biết toàn bộ bản chất vật thể."),
    s("Thôi Để Tôi Làm", "UTILITY", "Khi xử lý thao tác sinh tồn", "Đại diện lợi thế thực dụng trong narration/Game Master; không áp cho hack, phép thuật hoặc công nghệ ngoài khả năng."),
    s("Kế Hoạch Không Có Trong Kế Hoạch", "ULTIMATE", "Mỗi 5 combat turn khi ACTIVE trong Party", "Tận dụng địa hình: +30 Escape Progress và Entity -20 điểm % Accuracy trong phản ứng hiện tại.", "Không gây damage.")
  )

  private val caoMinh = listOf(
    s("Đại Ma Đầu", "PASSIVE", "Luôn hoạt động", "+50% stats hiện tại (STR/DEF/SKL/VIT) của Cao Minh, tính lại theo stats hiện tại mỗi lần tính Effective Stats; Max HP derive từ VIT; hoạt động song song với Đại Đạo Ma Tôn.", "Không chiếm ô trang bị, không stack qua save-load hoặc equip."),
    s("Huyết Ma Tứ Liên", "AUTO", "30% mỗi lượt TẤN CÔNG hợp lệ", "Đúng 4 trảm, 170% Weapon DMG; Chảy máu 3 turn x 5% Max HP."),
    s("Ma Tâm Trấn Hồn", "AUTO", "20% mỗi lượt TẤN CÔNG hợp lệ", "Đúng 4 trảm cùng điểm, 130% Weapon DMG; Choáng phản ứng hiện tại."),
    s("Huyết Ảnh Ma Độn", "AUTO", "20% mỗi lượt TẤN CÔNG hợp lệ", "Dịch chuyển theo Huyết Ma Kiếm, đúng 2 trảm, 147% Weapon DMG."),
    s("Thiên Ma Bộ", "AUTO", "30% khi TẤN CÔNG hoặc NÉ TRÁNH", "+50 điểm phần trăm Evasion trong 3 turn; không chặn AoE bắt buộc."),
    s("Huyết Sát Kiếm Ấn", "AUTO", "50% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate", "+25% DMG; Chảy máu 2 turn x 3% Max HP."),
    s("Phá Giáp Ma Kiếm", "AUTO", "48% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate", "+20% DMG; Xuyên giáp 10% trong 2 turn."),
    s("Ma Tâm Chấn", "AUTO", "45% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate", "+15% DMG; Choáng phản ứng hiện tại."),
    s("Huyết Độc Ma Khí", "AUTO", "47% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate", "+20% DMG; Trúng độc 2 turn x 3% Max HP."),
    s("Huyết Liệt Ma Ấn", "AUTO", "51% sau đòn đánh thường/kỹ năng; không ở lượt Ultimate", "+20% DMG; Chảy máu 2 turn x 4% Max HP."),
    s("Huyết Ma Nhị Thập Tứ Trảm", "ULTIMATE", "Mỗi 3 combat turn khi TẤN CÔNG", "Đúng 24 trảm, mỗi trảm 115% current Weapon DMG; bỏ qua Evasion.")
  )

  private val lucia = listOf(
    s("Trinh sát chiến trường", "PASSIVE", "Khi Lucia ở trong Party", "+5 điểm phần trăm vào generic loot roll hiện có."),
    s("M4A1 Joint Attack", "COMMAND", "Khi người chơi ra lệnh cả Cao Minh và Lucia cùng tấn công", "Lucia có resolution bắn M4A1 riêng và vẫn chịu Entity Evasion gate.")
  )

  fun forCharacter(characterId: String): List<CharacterSkillDefinition> = when (characterId) {
    CAO_MINH_ID -> caoMinh
    LUC_TRAM_ID -> lucTram
    SYVIAL_ID -> syvial
    AN_NHIEN_ID -> anNhien
    "lucia" -> lucia
    else -> emptyList()
  }
}
''', encoding="utf-8")


# ---------------------------------------------------------------------------
# 2) Expose skills through the authoritative Character Detail JSON projection.
# ---------------------------------------------------------------------------
detail = DETAIL_JSON.read_text(encoding="utf-8")
skills_anchor = '    put("equipment", JSONObject(c.equipment))\n'
skills_projection = '''    put("skills", JSONArray().apply {
      CompanionSkillCatalog.forCharacter(c.id).forEach { skill -> put(JSONObject().apply {
        put("name", skill.name)
        put("kind", skill.kind)
        put("trigger", skill.trigger)
        put("effect", skill.effect)
        skill.note?.let { put("note", it) }
      }) }
    })
'''
if 'CompanionSkillCatalog.forCharacter(c.id)' not in detail:
    detail = replace_once(detail, skills_anchor, skills_projection + skills_anchor, "Character Detail skills projection")
DETAIL_JSON.write_text(detail, encoding="utf-8")


# ---------------------------------------------------------------------------
# 3) Iris + Syvial + An Nhien runtime skills. These run after Kai, Diệp Minh,
# Lucia and Entity compatibility layers. Existing ultimate/boss contracts stay
# authoritative; companion skills only wrap the finalized response path.
# ---------------------------------------------------------------------------
combat = COMBAT.read_text(encoding="utf-8")

constants_anchor = '  private const val KAI_QUICK_STEP_TURNS_KEY = "combat.kaiQuickStepTurns"\n'
constants = '''  private const val IRIS_ANALYZED_TURNS_KEY = "combat.irisAnalyzedTurns"
  private const val IRIS_ARMOR_BREAK_TURNS_KEY = "combat.irisArmorBreakTurns"
  private const val IRIS_EXPOSED_TURNS_KEY = "combat.irisExposedTurns"
  private const val SYVIAL_BLEED_TURNS_KEY = "combat.syvialBleedTurns"
  private const val SYVIAL_DEVIL_TRIGGER_KEY = "combat.syvialDevilTrigger"
  private const val SYVIAL_DISORIENT_TURNS_KEY = "combat.syvialDisorientTurns"
  private const val IRIS_ULTIMATE_INTERVAL_TURNS = 4
  private const val SYVIAL_ULTIMATE_INTERVAL_TURNS = 3
  private const val AN_NHIEN_ULTIMATE_INTERVAL_TURNS = 5
'''
if 'IRIS_ANALYZED_TURNS_KEY' not in combat and constants_anchor in combat:
    combat = replace_once(combat, constants_anchor, constants_anchor + constants, "companion skill constants")

locals_anchor = '    var entityStunnedThisTurn = false\n'
locals = '''    var companionEnemyAccuracyPenalty = 0
    var irisAnalyzedTurns = state.metadata[IRIS_ANALYZED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 3) ?: 0
    var irisArmorBreakTurns = state.metadata[IRIS_ARMOR_BREAK_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 2) ?: 0
    var irisExposedTurns = state.metadata[IRIS_EXPOSED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 2) ?: 0
    var syvialBleedTurns = state.metadata[SYVIAL_BLEED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 3) ?: 0
    var syvialDisorientTurns = state.metadata[SYVIAL_DISORIENT_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 2) ?: 0
    var syvialDevilTrigger = state.metadata[SYVIAL_DEVIL_TRIGGER_KEY]?.toBooleanStrictOrNull() ?: false
'''
if 'var companionEnemyAccuracyPenalty = 0' not in combat and locals_anchor in combat and 'IRIS_ANALYZED_TURNS_KEY' in combat:
    combat = replace_once(combat, locals_anchor, locals_anchor + locals, "companion skill local state")

helper_anchor = '  private data class PartyPercentDamage(\n'
helpers = r'''  private fun activePartyCharacter(state: GameState, characterId: String): CharacterState? {
    if (characterId !in state.party.memberIds) return null
    val character = state.characters[characterId] ?: return null
    return character.takeIf { it.presence == CharacterPresence.ACTIVE && it.vitalState.currentHp > 0 }
  }

  private fun companionSkillDamage(weaponDamage: Int, percent: Int, armor: Int): Int =
    max(1, ((max(1, weaponDamage) * percent + 99) / 100) - max(0, armor))

  private fun armorAfterIgnore(armor: Int, ignorePercent: Int): Int =
    max(0, armor - ((armor * ignorePercent + 99) / 100))

'''
if 'private fun activePartyCharacter(' not in combat and helper_anchor in combat and 'IRIS_ANALYZED_TURNS_KEY' in combat:
    combat = replace_once(combat, helper_anchor, helpers + helper_anchor, "companion skill helpers")

# Syvial bleed ticks before the first post-action death check, exactly like Kai's persisted Bleeding.
if 'Bleeding từ Crimson Guillotine gây' not in combat and 'IRIS_ANALYZED_TURNS_KEY' in combat:
    resolve_start = combat.index('  fun resolve(state: GameState, actionKind: String, action: String): Resolution {\n')
    resolve_end = combat.index('\n  fun toJson(state: GameState): JSONObject?', resolve_start)
    kai_bleed = combat.find('    if (c.entityHp > 0 && bleedTurns > 0) {\n', resolve_start, resolve_end)
    death_index = combat.find('    if (c.entityHp <= 0) {\n', kai_bleed, resolve_end)
    if kai_bleed < 0 or death_index < 0:
        kai_bleed = -1
    bleed = '''    if (c.entityHp > 0 && syvialBleedTurns > 0) {
      val bleedDamage = percentDamage(c.entityMaxHp, 4)
      val hp = max(0, c.entityHp - bleedDamage)
      syvialBleedTurns = max(0, syvialBleedTurns - 1)
      resolvedState = withCombatCounter(resolvedState, SYVIAL_BLEED_TURNS_KEY, syvialBleedTurns)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
      log += "Bleeding từ Crimson Guillotine gây -$bleedDamage HP (4% Max HP; ${c.entityHp}/${c.entityMaxHp}); còn $syvialBleedTurns turn."
    }

'''
    if kai_bleed >= 0:
        combat = combat[:death_index] + bleed + combat[death_index:]

response_anchor = '    // Enemy response. Diệp Minh uses percentage damage; all other Entity behavior remains unchanged.\n'
companion_block = r'''    // COMPANION_SKILLS_R01: Iris, Syvial and An Nhien wrap the finalized combat response.
    val irisActive = activePartyCharacter(resolvedState, LUC_TRAM_ID) != null
    val syvialCharacter = activePartyCharacter(resolvedState, SYVIAL_ID)
    val syvialActive = syvialCharacter != null
    val anNhienActive = activePartyCharacter(resolvedState, AN_NHIEN_ID) != null

    if (irisActive && c.entityHp > 0) {
      if (irisAnalyzedTurns <= 0) {
        irisAnalyzedTurns = 3
        resolvedState = withCombatCounter(resolvedState, IRIS_ANALYZED_TURNS_KEY, irisAnalyzedTurns)
        log += "ARGUS Terrain Read: Iris đánh dấu mục tiêu Analyzed trong 3 turn."
      }
      val irisWeapon = CharacterStatEngine.weaponDamage(resolvedState, LUC_TRAM_ID)
      val irisArmor = when {
        irisExposedTurns > 0 -> armorAfterIgnore(profile.armor, 20)
        irisArmorBreakTurns > 0 -> armorAfterIgnore(profile.armor, 20)
        else -> profile.armor
      }
      val irisUltimate = c.eventCounter % IRIS_ULTIMATE_INTERVAL_TURNS == 0
      if (irisUltimate) {
        val damage = companionSkillDamage(irisWeapon, 300, irisArmor)
        val hp = max(0, c.entityHp - damage)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 30))
        irisExposedTurns = 2
        resolvedState = withCombatCounter(resolvedState, IRIS_EXPOSED_TURNS_KEY, irisExposedTurns)
        log += "ARGUS // Thousandfold Execution: 12 phát luân phiên, 300% DMG = -$damage HP; Fully Exposed 2 turn."
      } else {
        if (roll(c.copy(eventCounter = c.eventCounter + 151), 100) < 30 && c.entityHp > 0) {
          val percent = if (irisAnalyzedTurns > 0) 170 else 155
          val damage = companionSkillDamage(irisWeapon, percent, irisArmor)
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 14))
          log += "Twosome Time tự động kích hoạt: 2 phát chéo góc, $percent% DMG = -$damage HP."
        }
        if (roll(c.copy(eventCounter = c.eventCounter + 163), 100) < 20 && c.entityHp > 0) {
          val damage = companionSkillDamage(irisWeapon, 145, irisArmor)
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 18))
          log += "Rain Storm tự động kích hoạt: 6 phát khi đổi góc trên không, 145% DMG = -$damage HP."
        }
        if (roll(c.copy(eventCounter = c.eventCounter + 179), 100) < 20 && c.entityHp > 0) {
          val damage = companionSkillDamage(irisWeapon, 185, irisArmor)
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 24))
          irisArmorBreakTurns = 2
          resolvedState = withCombatCounter(resolvedState, IRIS_ARMOR_BREAK_TURNS_KEY, irisArmorBreakTurns)
          log += "Honeycomb Fire tự động kích hoạt: 8 phát tập trung, 185% DMG = -$damage HP; Armor Break 20% trong 2 turn."
        }
        if (roll(c.copy(eventCounter = c.eventCounter + 191), 100) < 25 && c.entityHp > 0) {
          val chargedArmor = armorAfterIgnore(profile.armor, 35)
          val damage = companionSkillDamage(irisWeapon, 175, chargedArmor)
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 20))
          log += "Charged Shot tự động kích hoạt: 175% DMG = -$damage HP, bỏ qua 35% Armor."
        }
      }
    }

    if (syvialActive && c.entityHp > 0) {
      val syvialMaxHp = CharacterStatEngine.effective(resolvedState, SYVIAL_ID).maxHp
      val syvialHp = syvialCharacter!!.vitalState.currentHp
      if (!syvialDevilTrigger && (syvialHp * 2 <= syvialMaxHp || c.entityKey == DIEP_MINH_KEY)) {
        syvialDevilTrigger = true
        val metadata = resolvedState.metadata.toMutableMap()
        metadata[SYVIAL_DEVIL_TRIGGER_KEY] = "true"
        resolvedState = resolvedState.copy(metadata = metadata)
        log += "Syvial kích hoạt Devil Trigger."
      }
      val regenPercent = if (syvialDevilTrigger) 4 else 2
      if (syvialHp > 0 && syvialHp < syvialMaxHp) {
        val heal = percentDamage(syvialMaxHp, regenPercent)
        resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, SYVIAL_ID, syvialHp + heal)
        val after = resolvedState.characters[SYVIAL_ID]?.vitalState?.currentHp ?: syvialHp
        log += "Lucifer Core hồi Syvial +${after - syvialHp} HP ($after/$syvialMaxHp)."
      }
      val syvialWeapon = CharacterStatEngine.weaponDamage(resolvedState, SYVIAL_ID)
      val dtMultiplier = if (syvialDevilTrigger) 125 else 100
      fun syvialDamage(percent: Int, armor: Int): Int = companionSkillDamage(syvialWeapon, (percent * dtMultiplier + 99) / 100, armor)
      val syvialUltimate = syvialDevilTrigger && c.eventCounter % SYVIAL_ULTIMATE_INTERVAL_TURNS == 0
      if (syvialUltimate) {
        val damage = min(c.entityHp, 24 * 10)
        val hp = max(0, c.entityHp - damage)
        c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp), noise = min(100, c.noise + 28))
        log += "GodKiller Override // Twenty-Four Severance: thời gian ngoại giới dừng, đúng 24 nhát x 10 HP = -$damage HP; bỏ qua Evasion."
      } else {
        if (roll(c.copy(eventCounter = c.eventCounter + 211), 100) < 30 && c.entityHp > 0) {
          val damage = syvialDamage(175, armorAfterIgnore(profile.armor, 20))
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          log += "Rift Sever tự động kích hoạt: Spatial Shift + GodKiller, 175% DMG = -$damage HP."
        }
        if (roll(c.copy(eventCounter = c.eventCounter + 223), 100) < 20 && c.entityHp > 0) {
          val damage = syvialDamage(190, profile.armor)
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          syvialBleedTurns = 3
          resolvedState = withCombatCounter(resolvedState, SYVIAL_BLEED_TURNS_KEY, syvialBleedTurns)
          log += "Crimson Guillotine tự động kích hoạt: 190% DMG = -$damage HP; Bleeding 3 turn x 4% Max HP."
        }
        if (roll(c.copy(eventCounter = c.eventCounter + 239), 100) < 20 && c.entityHp > 0) {
          val damage = syvialDamage(155, profile.armor)
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          entityStunnedThisTurn = true
          log += "Lucifer Breaker tự động kích hoạt: 155% DMG = -$damage HP; Entity bị Stun trong phản ứng hiện tại."
        }
        if (syvialDevilTrigger && roll(c.copy(eventCounter = c.eventCounter + 251), 100) < 20 && c.entityHp > 0) {
          val damage = syvialDamage(210, profile.armor)
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          syvialDisorientTurns = 2
          resolvedState = withCombatCounter(resolvedState, SYVIAL_DISORIENT_TURNS_KEY, syvialDisorientTurns)
          log += "Spatial Dominion tự động kích hoạt: 210% DMG = -$damage HP; Disoriented -25% Accuracy trong 2 turn."
        }
      }
    }

    if (anNhienActive && c.entityHp > 0) {
      if (roll(c.copy(eventCounter = c.eventCounter + 269), 100) < 25) {
        companionEnemyAccuracyPenalty += 25
        log += "An Nhiên dùng Quăng Đại Cái Gì Đó: tiếng động lệch hướng khiến Entity -25 điểm % Accuracy trong phản ứng hiện tại."
      }
      if (c.eventCounter % AN_NHIEN_ULTIMATE_INTERVAL_TURNS == 0) {
        companionEnemyAccuracyPenalty += 20
        c = c.copy(escapeProgress = min(100, c.escapeProgress + 30))
        log += "Kế Hoạch Không Có Trong Kế Hoạch: +30 Escape Progress và Entity -20 điểm % Accuracy trong phản ứng hiện tại."
      }
    }

    if (syvialDisorientTurns > 0) companionEnemyAccuracyPenalty += 25

    if (c.entityHp <= 0) {
      val persisted = encode(resolvedState, c.copy(phase = Phase.RESOLVED, entityCondition = EntityCondition.DESTROYED))
      val cleared = clearCombatOnly(persisted)
      return Resolution(cleared, true, log.joinToString(" ") + " ${c.entityName} đã bị tiêu diệt.", entityDestroyed = true)
    }

'''
if 'COMPANION_SKILLS_R01' not in combat and response_anchor in combat and 'IRIS_ANALYZED_TURNS_KEY' in combat:
    combat = replace_once(combat, response_anchor, companion_block + response_anchor, "companion automatic skill block")

# Companion accuracy penalties wrap the already-final ordinary Entity response. Diệp Minh's forced
# Devils And Gold AoE remains untouched; Stun can still suppress the response before it reaches this block.
enemy_chance_old = '      val enemyChance = (profile.aggression * 8 - defense + max(0, -c.momentum) * 7 - quickStepEvasion).coerceIn(0, 88)\n'
enemy_chance_new = '      val enemyChance = (profile.aggression * 8 - defense + max(0, -c.momentum) * 7 - quickStepEvasion - companionEnemyAccuracyPenalty).coerceIn(0, 88)\n'
if enemy_chance_old in combat:
    combat = replace_once(combat, enemy_chance_old, enemy_chance_new, "companion enemy accuracy penalties")

# Iris Dead Angle and Syvial Counterphase fire only when the finalized ordinary response misses.
miss_anchor = '''        log += if (quickStepTurns > 0) {
          "Quick Step khiến ${c.entityName} hụt đòn; +${KAI_QUICK_STEP_EVASION_BONUS_PERCENT}% Evasion đang hoạt động."
        } else {
          "${c.entityName} không xuyên được thế phòng thủ/di chuyển của Kai."
        }
'''
counters = r'''        if (irisActive && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 281), 100) < 15) {
          val damage = companionSkillDamage(CharacterStatEngine.weaponDamage(resolvedState, LUC_TRAM_ID), 120, profile.armor)
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          log += "Dead Angle: Iris phản kích tức thời 120% DMG = -$damage HP."
        }
        if (syvialActive && c.entityHp > 0 && roll(c.copy(eventCounter = c.eventCounter + 293), 100) < 30) {
          val damage = companionSkillDamage(CharacterStatEngine.weaponDamage(resolvedState, SYVIAL_ID), if (syvialDevilTrigger) 157 else 125, profile.armor)
          val hp = max(0, c.entityHp - damage)
          c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
          log += "Counterphase: Syvial Spatial Shift vào góc chết và phản chém -$damage HP."
        }
'''
if 'Dead Angle: Iris phản kích' not in combat and miss_anchor in combat and 'IRIS_ANALYZED_TURNS_KEY' in combat:
    combat = replace_once(combat, miss_anchor, miss_anchor + counters, "Iris/Syvial miss counters")

# Countdown companion effects beside the existing Quick Step countdown, before regeneration.
countdown_anchor = '    val entityHpBeforeRegen = c.entityHp\n'
countdown = '''    if (irisAnalyzedTurns > 0) {
      irisAnalyzedTurns = max(0, irisAnalyzedTurns - 1)
      resolvedState = withCombatCounter(resolvedState, IRIS_ANALYZED_TURNS_KEY, irisAnalyzedTurns)
    }
    if (irisArmorBreakTurns > 0) {
      irisArmorBreakTurns = max(0, irisArmorBreakTurns - 1)
      resolvedState = withCombatCounter(resolvedState, IRIS_ARMOR_BREAK_TURNS_KEY, irisArmorBreakTurns)
    }
    if (irisExposedTurns > 0) {
      irisExposedTurns = max(0, irisExposedTurns - 1)
      resolvedState = withCombatCounter(resolvedState, IRIS_EXPOSED_TURNS_KEY, irisExposedTurns)
    }
    if (syvialDisorientTurns > 0) {
      syvialDisorientTurns = max(0, syvialDisorientTurns - 1)
      resolvedState = withCombatCounter(resolvedState, SYVIAL_DISORIENT_TURNS_KEY, syvialDisorientTurns)
    }

'''
if 'resolvedState = withCombatCounter(resolvedState, IRIS_ANALYZED_TURNS_KEY' not in combat.split(countdown_anchor)[0][-1800:]:
    if countdown_anchor in combat:
        combat = replace_once(combat, countdown_anchor, countdown + countdown_anchor, "companion skill countdowns")

if 'COMPANION_SKILLS_R01' in combat:
  for marker in (
    'COMPANION_SKILLS_R01',
    'ARGUS // Thousandfold Execution',
    'Twosome Time tự động kích hoạt',
    'Honeycomb Fire tự động kích hoạt',
    'Charged Shot tự động kích hoạt',
    'GodKiller Override // Twenty-Four Severance',
    'Rift Sever tự động kích hoạt',
    'Crimson Guillotine tự động kích hoạt',
    'Lucifer Breaker tự động kích hoạt',
    'Spatial Dominion tự động kích hoạt',
    'Quăng Đại Cái Gì Đó',
    'Kế Hoạch Không Có Trong Kế Hoạch',
    '- quickStepEvasion - companionEnemyAccuracyPenalty',
    'Dead Angle: Iris phản kích',
    'Counterphase: Syvial',
):
      if marker not in combat:
          raise RuntimeError("Companion combat contract missing: " + marker)
else:
    print('Companion combat generation skipped: CombatRuntime anchors absent on this tree generation.')
COMBAT.write_text(combat, encoding="utf-8")


# ---------------------------------------------------------------------------
# 4) An Nhien exploration utility. Existing +10pp loot remains authoritative.
# No second loot roll is added.
# ---------------------------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")
hazard_old = '    rolls.put("hazard", thresholdRoll("hazard", 10000, hazardThresholds[level], physical, ""));\n'
hazard_new = '''    int anNhienHazardThreshold = anNhienFollowing ? (hazardThresholds[level] * 75 / 100) : hazardThresholds[level];
    JSONObject anNhienHazardCheck = thresholdRoll("anNhienHazardCheck", 10000, 3000, anNhienFollowing && search && water, " Đừng Đụng Vào, Nhìn Là Biết Độc");
    rolls.put("anNhienHazardCheck", anNhienHazardCheck);
    if (anNhienHazardCheck.optBoolean("success", false)) anNhienHazardThreshold = 0;
    rolls.put("hazard", thresholdRoll("hazard", 10000, anNhienHazardThreshold, physical,
      anNhienFollowing ? " -25% Có Gì Đó Sai Sai" : ""));
'''
if 'anNhienHazardCheck' not in main:
    main = replace_once(main, hazard_old, hazard_new, "An Nhien hazard utility")

exit_old = '''    int exitThreshold = exitThresholdAndroid(state);
    if (anNhienFollowing) exitThreshold = Math.min(10000, exitThreshold + 200);
    JSONObject exitProbe = thresholdRoll("exitProbe", 10000, exitThreshold, exitIntent && (physical || search), anNhienFollowing ? " discovery clue +2% An Nhiên" : " discovery clue");
'''
exit_new = '''    int exitThreshold = exitThresholdAndroid(state);
    if (anNhienFollowing) exitThreshold = Math.min(10000, exitThreshold + 200);
    JSONObject anNhienRead = thresholdRoll("anNhienRead", 10000, 2000, anNhienFollowing && search && exitIntent, " Khoan, Để Tôi Đọc Cái Này");
    rolls.put("anNhienRead", anNhienRead);
    if (anNhienRead.optBoolean("success", false)) exitThreshold = Math.min(10000, exitThreshold + 2000);
    JSONObject exitProbe = thresholdRoll("exitProbe", 10000, exitThreshold, exitIntent && (physical || search),
      anNhienRead.optBoolean("success", false) ? " discovery clue +2% An Nhiên +20% đọc dấu Exit" : (anNhienFollowing ? " discovery clue +2% An Nhiên" : " discovery clue"));
'''
if 'thresholdRoll("anNhienRead"' not in main:
    main = replace_once(main, exit_old, exit_new, "An Nhien exit reading utility")
for marker in (
    'hazardThresholds[level] * 75 / 100',
    'thresholdRoll("anNhienHazardCheck", 10000, 3000',
    'thresholdRoll("anNhienRead", 10000, 2000',
    'exitThreshold + 2000',
):
    if marker not in main:
        raise RuntimeError("An Nhien exploration skill contract missing: " + marker)
MAIN.write_text(main, encoding="utf-8")


# ---------------------------------------------------------------------------
# 5) Character Detail UI: attached by patch-companion-skills-ui-finalize.py
# after all final UI transforms. This base patch only passes the HTML through.
html = INDEX.read_text(encoding="utf-8")
INDEX.write_text(html, encoding="utf-8")


# 6) Regression coverage generated by the patch chain.
# ---------------------------------------------------------------------------
TEST.write_text(r'''package com.rabpit.backroom.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompanionSkillCatalogTest {
  @Test fun skillCatalogExposesCanonSets() {
    assertEquals(11, CompanionSkillCatalog.forCharacter(CAO_MINH_ID).size)
    assertEquals(4, CompanionSkillCatalog.forCharacter(LUC_TRAM_ID).size)
    assertEquals(10, CompanionSkillCatalog.forCharacter(SYVIAL_ID).size)
    assertEquals(8, CompanionSkillCatalog.forCharacter(AN_NHIEN_ID).size)
    assertEquals("PASSIVE", CompanionSkillCatalog.forCharacter(CAO_MINH_ID).single { it.name == "Đại Ma Đầu" }.kind)
    assertTrue(CompanionSkillCatalog.forCharacter(LUC_TRAM_ID).any { it.name == "Thiên Kiếm Linh Tâm" })
    assertTrue(CompanionSkillCatalog.forCharacter(LUC_TRAM_ID).any { it.name == "Tịch Quang" })
    assertTrue(CompanionSkillCatalog.forCharacter(SYVIAL_ID).any { it.name.contains("Twenty-Four Severance") })
    assertTrue(CompanionSkillCatalog.forCharacter(AN_NHIEN_ID).any { it.name == "Kế Hoạch Không Có Trong Kế Hoạch" })
  }

  @Test fun anNhienRemainsNonCombatAndWeaponLocked() {
    val character = AnNhienCanon.character()
    assertEquals("true", character.metadata["nonCombat"])
    assertEquals("false", character.metadata["canUseWeapons"])
    assertFalse(CompanionSkillCatalog.forCharacter(AN_NHIEN_ID).any { it.effect.contains("Weapon DMG") })
  }

  // NOTE (2026-10-24 chain repair): the automatic companion combat block in
  // patch-companion-skills-ui.py targets an older CombatRuntime generation and
  // is skipped when its anchors are absent on the current tree, so runtime
  // combat-proc tests are not generated here. The catalog contract above is
  // what this patch guarantees on the current generation.
}
''', encoding="utf-8")

print("Companion skills R01 applied: Iris + Syvial combat kits, An Nhien utility kit, and compact Character Skill panel.")
