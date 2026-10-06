from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
LUCIA = CORE / "LuciaCanon.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"
AVATAR = ROOT / "app/src/main/assets/avatars/lucia_avatar.jpg"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


lucia = LUCIA.read_text(encoding="utf-8")
if 'const val BATTLEFIELD_RECON_LOOT_BONUS_PERCENT = 5' not in lucia:
    lucia = replace_once(
        lucia,
        '  const val AVATAR_REF = "avatars/lucia_avatar.jpg"\n',
        '  const val AVATAR_REF = "avatars/lucia_avatar.jpg"\n  const val BATTLEFIELD_RECON_LOOT_BONUS_PERCENT = 5\n',
        "Lucia Battlefield Recon constant",
    )
metadata_anchor = '        "goal" to "Tìm lối sang Level 1"\n'
metadata_new = (
    '        "goal" to "Tìm lối sang Level 1",\n'
    '        "passiveSkill" to "Trinh sát chiến trường",\n'
    '        "lootChanceBonusPercent" to BATTLEFIELD_RECON_LOOT_BONUS_PERCENT.toString(),\n'
    '        "avatarBuild" to "EXIF_STRIPPED_JPEG_R02"\n'
)
lucia = replace_once(lucia, metadata_anchor, metadata_new, "Lucia passive metadata")
LUCIA.write_text(lucia, encoding="utf-8")

combat = COMBAT.read_text(encoding="utf-8")
if 'private const val LUCIA_M4A1_COMBAT_DAMAGE = 26' not in combat:
    combat = replace_once(
        combat,
        '  private const val DIEP_MINH_ULTIMATE_PERCENT = 5\n',
        '  private const val DIEP_MINH_ULTIMATE_PERCENT = 5\n  private const val LUCIA_M4A1_COMBAT_DAMAGE = 26\n',
        "Lucia combat damage constant",
    )
attack_start = combat.find('      Intent.ATTACK -> {\n')
attack_end = combat.find('      Intent.OTHER -> {\n', attack_start)
if attack_start < 0 or attack_end < 0:
    raise RuntimeError("Final CombatRuntime ATTACK block not found")
attack = combat[attack_start:attack_end]
if 'LUCIA_JOINT_ATTACK' not in attack:
    closing = attack.rfind('      }\n')
    if closing < 0:
        raise RuntimeError("CombatRuntime ATTACK closing brace not found")
    support = r'''        // LUCIA_JOINT_ATTACK: process the follower when the player explicitly orders both attackers.
        val jointOrder = action.lowercase().let { raw ->
          raw.contains("cả 2") || raw.contains("cả hai") || raw.contains("hai người") ||
            raw.contains("cùng tấn công") || raw.contains("cùng bắn") ||
            ((raw.contains("lucia") || raw.contains("lục")) && (raw.contains("tấn công") || raw.contains("bắn")))
        }
        val lucia = resolvedState.characters[LUCIA_ID]
        val luciaActive = LUCIA_ID in resolvedState.party.memberIds &&
          lucia?.presence == CharacterPresence.ACTIVE && (lucia.vitalState.currentHp > 0)
        if (jointOrder && luciaActive && c.entityHp > 0) {
          val luciaRoll = roll(c.copy(eventCounter = c.eventCounter + 83), 100)
          if (luciaRoll < hitChance) {
            val luciaDamage = max(1, LUCIA_M4A1_COMBAT_DAMAGE - profile.armor)
            val luciaHp = max(0, c.entityHp - luciaDamage)
            c = c.copy(
              entityHp = luciaHp,
              entityCondition = condition(luciaHp, c.entityMaxHp),
              noise = min(100, c.noise + 22)
            )
            log += "Lucia \"Lục\" bắn hỗ trợ bằng M4A1: -$luciaDamage HP (${c.entityHp}/${c.entityMaxHp})."
          } else {
            log += "Lucia \"Lục\" cũng khai hỏa nhưng phát bắn không trúng mục tiêu."
          }
        }
'''
    attack = attack[:closing] + support + attack[closing:]
    combat = combat[:attack_start] + attack + combat[attack_end:]
for marker in (
    'private const val LUCIA_M4A1_COMBAT_DAMAGE = 26',
    'LUCIA_JOINT_ATTACK',
    'LUCIA_ID in resolvedState.party.memberIds',
    'lucia?.presence == CharacterPresence.ACTIVE',
    'val luciaRoll = roll(c.copy(eventCounter = c.eventCounter + 83), 100)',
    'Lucia \"Lục\" bắn hỗ trợ bằng M4A1',
):
    if marker not in combat:
        raise RuntimeError("Lucia joint-combat contract missing: " + marker)
COMBAT.write_text(combat, encoding="utf-8")

main = MAIN.read_text(encoding="utf-8")
loot_old = '    rolls.put("loot", thresholdRoll("loot", 10000, lootThresholds[level], search, ""));\n'
loot_new = '''    int luciaScoutBonus = (partyHas(state, "lucia") || partyHas(state, "lục")) ? 500 : 0;
    int lootThreshold = Math.min(10000, lootThresholds[level] + luciaScoutBonus);
    rolls.put("loot", thresholdRoll("loot", 10000, lootThreshold, search,
      luciaScoutBonus > 0 ? " + Lucia Trinh sát chiến trường 5%" : ""));
'''
main = replace_once(main, loot_old, loot_new, "Lucia +5 percentage-point loot bonus")
if 'LUCIA SCOUT PASSIVE HARD LOCK:' not in main:
    prompt_anchor = 'LUCIA FOLLOWER HARD LOCK:'
    prompt_pos = main.find(prompt_anchor)
    if prompt_pos < 0:
        raise RuntimeError("Lucia prompt contract anchor missing")
    return_pos = main.rfind('    return actionDirective + ', 0, prompt_pos)
    if return_pos < 0:
        raise RuntimeError("Lucia writerPrompt return anchor missing")
    directive = (
        '    String luciaScoutDirective = "LUCIA SCOUT PASSIVE HARD LOCK: Khi Lucia \\\"Lục\\\" đang ở trong Party, '
        'passive Trinh sát chiến trường cộng đúng +5 điểm phần trăm vào generic loot roll hiện có. Không tạo roll vật phẩm '
        'riêng, không bỏ qua search eligibility, không tự nhặt vật phẩm và không vượt InventoryPolicy.";\n'
    )
    main = main[:return_pos] + directive + main[return_pos:]
    return_anchor = '    return actionDirective + "\\n" + healingItemDirective + "\\n" + "'
    return_new = '    return actionDirective + "\\n" + healingItemDirective + "\\n" + luciaScoutDirective + "\\n" + "'
    main = replace_once(main, return_anchor, return_new, "Lucia scout prompt injection")
for marker in (
    'int luciaScoutBonus = (partyHas(state, "lucia") || partyHas(state, "lục")) ? 500 : 0;',
    'int lootThreshold = Math.min(10000, lootThresholds[level] + luciaScoutBonus);',
    'LUCIA SCOUT PASSIVE HARD LOCK:',
    '+5 điểm phần trăm',
):
    if marker not in main:
        raise RuntimeError("Lucia scout loot contract missing: " + marker)
if 'thresholdRoll("luciaLoot"' in main or 'thresholdRoll("battlefieldRecon"' in main:
    raise RuntimeError("Lucia passive must augment generic loot, not add a second roll")
MAIN.write_text(main, encoding="utf-8")


def strip_exif_app1(raw: bytes) -> bytes:
    if len(raw) < 4 or raw[:2] != b"\xff\xd8":
        raise RuntimeError("Lucia avatar is not a JPEG")
    out = bytearray(raw[:2])
    i = 2
    while i < len(raw):
        if raw[i] != 0xFF:
            out.extend(raw[i:])
            break
        start = i
        while i < len(raw) and raw[i] == 0xFF:
            i += 1
        if i >= len(raw):
            out.extend(raw[start:])
            break
        marker = raw[i]
        i += 1
        if marker == 0xDA:
            out.extend(raw[start:])
            break
        if marker in (0xD8, 0xD9, 0x01) or 0xD0 <= marker <= 0xD7:
            out.extend(raw[start:i])
            if marker == 0xD9:
                break
            continue
        if i + 2 > len(raw):
            raise RuntimeError("Lucia avatar has a truncated JPEG segment")
        seg_len = int.from_bytes(raw[i:i + 2], "big")
        if seg_len < 2 or i + seg_len > len(raw):
            raise RuntimeError("Lucia avatar has an invalid JPEG segment length")
        segment = raw[start:i + seg_len]
        payload = raw[i + 2:i + seg_len]
        is_exif = marker == 0xE1 and payload.startswith(b"Exif\x00\x00")
        if not is_exif:
            out.extend(segment)
        i += seg_len
    return bytes(out)

avatar_before = AVATAR.read_bytes()
avatar_after = strip_exif_app1(avatar_before)
if len(avatar_after) <= 1024 or avatar_after[:2] != b"\xff\xd8" or avatar_after[-2:] != b"\xff\xd9":
    raise RuntimeError("Rebuilt Lucia avatar failed JPEG integrity checks")
if b"Exif\x00\x00" in avatar_after:
    raise RuntimeError("Rebuilt Lucia avatar still contains EXIF metadata")
AVATAR.write_bytes(avatar_after)

test = TEST.read_text(encoding="utf-8")
new_tests = r'''
  @Test fun luciaGetsAnIndependentCombatResolutionWhenBothAttack() {
    val initial = LuciaCanon.ensure(GameState.initial())
    var state = initial.copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID)))
    state = CombatRuntime.start(state, "diep_minh")
    state = state.copy(metadata = state.metadata + ("combat.eventCounter" to "0"))

    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả 2 cùng tấn công")
    assertTrue(result.handled)
    assertTrue(result.reply.contains("Lucia \"Lục\""))
    assertTrue(
      result.reply.contains("bắn hỗ trợ bằng M4A1") ||
        result.reply.contains("cũng khai hỏa nhưng phát bắn không trúng mục tiêu")
    )
  }

  @Test fun luciaDoesNotAutoAttackOnKaiOnlyAttackOrder() {
    val initial = LuciaCanon.ensure(GameState.initial())
    var state = initial.copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID)))
    state = CombatRuntime.start(state, "diep_minh")

    val result = CombatRuntime.resolve(state, "EXECUTE", "Kai tấn công")
    assertTrue(result.handled)
    assertFalse(result.reply.contains("Lucia \"Lục\" bắn hỗ trợ"))
    assertFalse(result.reply.contains("Lucia \"Lục\" cũng khai hỏa"))
  }
'''
if 'luciaGetsAnIndependentCombatResolutionWhenBothAttack' not in test:
    close = test.rfind('}\n')
    if close < 0:
        raise RuntimeError("CombatRuntimeTest class closing brace missing")
    test = test[:close] + new_tests + test[close:]
for marker in (
    'luciaGetsAnIndependentCombatResolutionWhenBothAttack',
    'CombatRuntime.resolve(state, "EXECUTE", "Cả 2 cùng tấn công")',
    'luciaDoesNotAutoAttackOnKaiOnlyAttackOrder',
):
    if marker not in test:
        raise RuntimeError("Lucia combat regression test missing: " + marker)
TEST.write_text(test, encoding="utf-8")

print(
    "Lucia combat/scout patch applied: joint attacks resolve Lucia, Battlefield Recon adds +5 percentage points "
    "to generic loot while she is in Party, and her packaged JPEG avatar is rebuilt without EXIF metadata."
)
