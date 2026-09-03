from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
COMBAT = CORE / "CombatRuntime.kt"
PARTY = CORE / "PartyTurnCombat.kt"
INTERLEAVED_TEST = TESTS / "PartyTurnCombatInterleavedTest.kt"


def once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# Preserve Violet Warden's established direct-runtime narration while keeping the
# serialized Party log accurate. In PartyTurnCombat a stunned Kai loses only his
# personal action; a raw legacy CombatRuntime ATTACK retains the historical note
# that other ACTIVE members continue that legacy Party-wide command.
combat = COMBAT.read_text(encoding="utf-8")
old_violet = '''        if (!partyTurnActorMatches(resolvedState, KAI_ID)) {
          // Another serialized Party member owns this ATTACK event.
        } else if (partyTurnActorActionLocked(resolvedState, KAI_ID)) {
          log += "Kai đang bị choáng hoặc mất khả năng hành động nên không thực hiện được đòn đánh."
        } else if (violetWardenKaiActionLocked) {
          log += "Violet Warden STUN: Kai mất lượt hành động cá nhân."
'''
new_violet = '''        if (!partyTurnActorMatches(resolvedState, KAI_ID)) {
          // Another serialized Party member owns this ATTACK event.
        } else if (violetWardenKaiActionLocked) {
          log += if (partyTurnActorId(resolvedState) == null) {
            "Violet Warden STUN: Kai mất lượt hành động cá nhân; các thành viên ACTIVE khác vẫn tiếp tục lệnh TẤN CÔNG."
          } else {
            "Violet Warden STUN: Kai mất lượt hành động cá nhân."
          }
        } else if (partyTurnActorActionLocked(resolvedState, KAI_ID)) {
          log += "Kai đang bị choáng hoặc mất khả năng hành động nên không thực hiện được đòn đánh."
'''
combat = once(combat, old_violet, new_violet, "Violet-specific stun narration order")
COMBAT.write_text(combat, encoding="utf-8")


# Restore the response-only feedback schema consumed by the existing overlay.
# New structured fields stay additive; old id/encounterId/targetId semantics are
# preserved so terminal cleanup does not break callers after Entity death.
party = PARTY.read_text(encoding="utf-8")
party = once(
    party,
    '''        put("targetType", "entity")
        put("targetId", entityBefore.entityKey)
        put("targetName", entityBefore.entityName)
''',
    '''        put("targetType", "entity")
        put("targetId", "entity")
        put("entityKey", entityBefore.entityKey)
        put("targetName", entityBefore.entityName)
''',
    "Entity feedback target compatibility",
)
party = once(
    party,
    '''    return JSONObject().apply {
      put("eventId", "combat:${entityBefore?.encounterId ?: "none"}:${actionSerial(after)}")
''',
    '''    return JSONObject().apply {
      put("id", java.util.UUID.randomUUID().toString())
      put("encounterId", entityBefore?.encounterId ?: "")
      put("eventId", "combat:${entityBefore?.encounterId ?: "none"}:${actionSerial(after)}")
''',
    "Combat feedback legacy identity fields",
)
PARTY.write_text(party, encoding="utf-8")


# The production Lucia story gate may keep her non-ACTIVE until first contact.
# This regression explicitly exercises a three-combatant Party, so make the test
# fixture ACTIVE without weakening Lucia's story/canon presence rules.
test = INTERLEAVED_TEST.read_text(encoding="utf-8")
fixture_old = '''    state = LuciaCanon.ensure(state)
    return state.copy(
      party = state.party.copy(memberIds = listOf(KAI_ID, IRIS_ID, LUCIA_ID), maxMembers = 7)
    )
'''
fixture_new = '''    state = LuciaCanon.ensure(state)
    val lucia = state.characters.getValue(LUCIA_ID).copy(presence = CharacterPresence.ACTIVE)
    return state.copy(
      characters = state.characters + (LUCIA_ID to lucia),
      party = state.party.copy(memberIds = listOf(KAI_ID, IRIS_ID, LUCIA_ID), maxMembers = 7)
    )
'''
test = once(test, fixture_old, fixture_new, "Three-member interleave fixture presence")
INTERLEAVED_TEST.write_text(test, encoding="utf-8")

for marker, text in (
    ('partyTurnActorId(resolvedState) == null', combat),
    ('put("targetId", "entity")', party),
    ('put("entityKey", entityBefore.entityKey)', party),
    ('put("id", java.util.UUID.randomUUID().toString())', party),
    ('put("encounterId", entityBefore?.encounterId ?: "")', party),
    ('copy(presence = CharacterPresence.ACTIVE)', test),
):
    if marker not in text:
        raise RuntimeError("Interleaved combat V4 contract missing: " + marker)

print("Interleaved combat V4 applied: Violet-specific stun text preserved, overlay feedback backward compatible, and 3-member regression fixture explicit.")
