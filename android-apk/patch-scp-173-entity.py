from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"
ASSET = ROOT / "app/src/main/assets/entity/173.png"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# ---------------------------------------------------------------------------
# CombatRuntime
# ---------------------------------------------------------------------------
# SCP-173 extends the finalized Entity runtime. GameState remains authoritative:
# observation, blink counters, cooldowns and transient Blink/Stun are committed
# before the reply string is assembled. Bleed DOT remains untouched by Concrete
# Body; direct weapon/impact damage is reduced by Concrete Body and, while
# OBSERVED, by DON'T BLINK's additional reduction.
combat = COMBAT.read_text(encoding="utf-8")

if "import org.json.JSONArray" not in combat:
    combat = replace_once(
        combat,
        "import org.json.JSONObject\n",
        "import org.json.JSONArray\nimport org.json.JSONObject\n",
        "SCP-173 JSON import",
    )

constants_anchor = '  private const val JOHN_DOE_STUN_TURNS_KEY = "combat.johnDoeStunTurns"\n'
constants = '''  private const val SCP_173_KEY = "scp_173"
  private const val SCP_173_MAX_HP = 1730
  private const val SCP_173_REGEN_PER_TURN = 0
  private const val SCP_173_OBSERVED_DAMAGE_REDUCTION_PERCENT = 20
  private const val SCP_173_PHYSICAL_DAMAGE_REDUCTION_PERCENT = 25
  private const val SCP_173_UNOBSERVED_ACTION_SPEED_PERCENT = 150
  private const val SCP_173_OBSERVED_ACTION_SPEED_PERCENT = 100
  private const val SCP_173_BLINK_THRESHOLD = 3
  private const val SCP_173_FIRST_UNOBSERVED_BONUS_PERCENT = 5
  private const val SCP_173_SNAP_STRIKE_PERCENT = 10
  private const val SCP_173_SNAP_STRIKE_STUN_PERCENT = 25
  private const val SCP_173_CONCRETE_RUSH_PERCENT = 16
  private const val SCP_173_CONCRETE_RUSH_VULNERABLE_PERCENT = 20
  private const val SCP_173_CONCRETE_RUSH_STUN_PERCENT = 35
  private const val SCP_173_CONCRETE_RUSH_COOLDOWN = 2
  private const val SCP_173_NECK_SNAP_PERCENT = 30
  private const val SCP_173_NECK_SNAP_COOLDOWN = 4
  private const val SCP_173_EXECUTION_THRESHOLD_PERCENT = 15
  private const val SCP_173_BLINK_PRESSURE_COOLDOWN = 3
  private const val SCP_173_FORCED_BLINK_PERCENT = 30
  private const val SCP_173_STATE_KEY = "combat.scp173.state"
  private const val SCP_173_ACTION_SPEED_KEY = "combat.scp173.actionSpeedPercent"
  private const val SCP_173_FIRST_STRIKE_PENDING_KEY = "combat.scp173.firstUnobservedStrikePending"
  private const val SCP_173_CONCRETE_RUSH_CD_KEY = "combat.scp173.cooldown.concreteRush"
  private const val SCP_173_NECK_SNAP_CD_KEY = "combat.scp173.cooldown.neckSnap"
  private const val SCP_173_BLINK_PRESSURE_CD_KEY = "combat.scp173.cooldown.blinkPressure"
  private const val SCP_173_BLINK_COUNTER_PREFIX = "combat.scp173.blinkCounter."
  private const val SCP_173_STATUS_PREFIX = "scp173:"
  private const val SCP_173_STATUS_EXPIRES_EVENT_KEY = "expiresEvent"
'''
if 'private const val SCP_173_KEY = "scp_173"' not in combat:
    combat = replace_once(combat, constants_anchor, constants_anchor + constants, "SCP-173 constants")

profile_anchor = '    Profile(JOHN_DOE_KEY, "John Doe", JOHN_DOE_MAX_HP, 0, 6, 9)\n'
profile_new = '    Profile(JOHN_DOE_KEY, "John Doe", JOHN_DOE_MAX_HP, 0, 6, 9),\n    Profile(SCP_173_KEY, "SCP-173", SCP_173_MAX_HP, 0, 9, 10)\n'
combat = replace_once(combat, profile_anchor, profile_new, "SCP-173 combat profile")

combat = replace_once(
    combat,
    '    val enhancedEntityMaxHp = when (profile.key) { DIEP_MINH_KEY -> DIEP_MINH_MAX_HP; MONSTER_X_KEY -> MONSTER_X_MAX_HP; JOHN_DOE_KEY -> JOHN_DOE_MAX_HP; else -> profile.maxHp + ENTITY_HP_BONUS }\n',
    '    val enhancedEntityMaxHp = when (profile.key) { DIEP_MINH_KEY -> DIEP_MINH_MAX_HP; MONSTER_X_KEY -> MONSTER_X_MAX_HP; JOHN_DOE_KEY -> JOHN_DOE_MAX_HP; SCP_173_KEY -> SCP_173_MAX_HP; else -> profile.maxHp + ENTITY_HP_BONUS }\n',
    "SCP-173 exact encounter HP",
)
combat = replace_once(
    combat,
    '    val canonicalMaxHp = when (profile.key) { DIEP_MINH_KEY -> DIEP_MINH_MAX_HP; MONSTER_X_KEY -> MONSTER_X_MAX_HP; JOHN_DOE_KEY -> JOHN_DOE_MAX_HP; else -> profile.maxHp + ENTITY_HP_BONUS }\n',
    '    val canonicalMaxHp = when (profile.key) { DIEP_MINH_KEY -> DIEP_MINH_MAX_HP; MONSTER_X_KEY -> MONSTER_X_MAX_HP; JOHN_DOE_KEY -> JOHN_DOE_MAX_HP; SCP_173_KEY -> SCP_173_MAX_HP; else -> profile.maxHp + ENTITY_HP_BONUS }\n',
    "SCP-173 exact migrated HP",
)

helper_anchor = '  private fun johnDoePoisonedIds(state: GameState): Set<String> =\n'
helpers = r'''  private fun scp173LivePartyIds(state: GameState): List<String> =
    state.party.memberIds.distinct().filter { characterId ->
      val character = state.characters[characterId]
      character != null && character.presence == CharacterPresence.ACTIVE && character.vitalState.currentHp > 0
    }

  private fun scp173BlinkCounter(state: GameState, characterId: String): Int =
    state.metadata[SCP_173_BLINK_COUNTER_PREFIX + characterId]?.toIntOrNull()?.coerceIn(0, SCP_173_BLINK_THRESHOLD - 1) ?: 0

  private fun scp173WithBlinkCounter(state: GameState, characterId: String, value: Int): GameState {
    val metadata = state.metadata.toMutableMap()
    metadata[SCP_173_BLINK_COUNTER_PREFIX + characterId] = value.coerceIn(0, SCP_173_BLINK_THRESHOLD - 1).toString()
    return state.copy(metadata = metadata)
  }

  private fun scp173StatusMatches(effect: StatusEffect, vararg tokens: String): Boolean {
    val type = effect.type.trim().uppercase().replace('-', '_').replace(' ', '_')
    val id = effect.id.trim().uppercase().replace('-', '_').replace(' ', '_')
    return tokens.any { token -> type.contains(token) || id.contains(token) }
  }

  private fun scp173CharacterHasStatus(state: GameState, characterId: String, vararg tokens: String): Boolean {
    val character = state.characters[characterId] ?: return false
    return character.statusIds.asSequence().mapNotNull(state.statuses::get).any { scp173StatusMatches(it, *tokens) }
  }

  private fun scp173VisionBlocked(state: GameState, characterId: String): Boolean {
    val character = state.characters[characterId] ?: return true
    if (character.presence != CharacterPresence.ACTIVE || character.vitalState.currentHp <= 0) return true
    if (scp173CharacterHasStatus(state, characterId,
        "BLINK", "BLIND", "STUN", "UNCONSCIOUS", "KNOCKED_OUT", "NO_LINE_OF_SIGHT", "LINE_OF_SIGHT_LOST", "VISION_LOST", "VISION_BLOCKED")) return true
    val metadata = character.metadata.mapKeys { it.key.trim().lowercase() }
    if (metadata["blind"].equals("true", true) || metadata["stunned"].equals("true", true) ||
        metadata["unconscious"].equals("true", true) || metadata["knockedout"].equals("true", true)) return true
    val los = metadata["lineofsight"]?.trim()?.lowercase()
    return los in setOf("false", "lost", "blocked", "none")
  }

  private fun scp173Observed(state: GameState): Boolean =
    state.metadata[SCP_173_STATE_KEY] == "OBSERVED"

  private fun scp173WithObservation(state: GameState, observed: Boolean): GameState {
    val metadata = state.metadata.toMutableMap()
    val previous = metadata[SCP_173_STATE_KEY]
    val next = if (observed) "OBSERVED" else "UNOBSERVED"
    val wasPending = metadata[SCP_173_FIRST_STRIKE_PENDING_KEY].equals("true", true)
    val firstStrikePending = when {
      observed -> false
      previous != "UNOBSERVED" -> true
      else -> wasPending
    }
    metadata[SCP_173_STATE_KEY] = next
    metadata[SCP_173_ACTION_SPEED_KEY] = (if (observed) SCP_173_OBSERVED_ACTION_SPEED_PERCENT else SCP_173_UNOBSERVED_ACTION_SPEED_PERCENT).toString()
    metadata[SCP_173_FIRST_STRIKE_PENDING_KEY] = firstStrikePending.toString()
    metadata["combat.scp173.immunity.poison"] = "true"
    metadata["combat.scp173.immunity.fear"] = "true"
    metadata["combat.scp173.immunity.knockback"] = "true"
    metadata["combat.scp173.stunMaxTurns"] = "1"
    return state.copy(metadata = metadata)
  }

  private fun scp173RecomputeObservation(state: GameState): GameState =
    scp173WithObservation(state, scp173LivePartyIds(state).any { !scp173VisionBlocked(state, it) })

  private fun scp173ApplyTransientStatus(state: GameState, characterId: String, type: String, expiresEvent: Int): GameState {
    if (characterId !in state.characters) return state
    val id = SCP_173_STATUS_PREFIX + type.lowercase() + ":" + characterId
    val effect = StatusEffect(
      id = id,
      type = type,
      source = SCP_173_KEY,
      startTurnId = state.turn.currentTurnId,
      durationTurns = 1,
      persistent = false,
      metadata = mapOf(SCP_173_STATUS_EXPIRES_EVENT_KEY to expiresEvent.toString())
    )
    val operation = if (id in state.statuses) StatusCommand.Operation.UPDATE else StatusCommand.Operation.APPLY
    val result = StatusEngine.execute(state, StatusCommand(
      commandId = "SCP173:STATUS:$type:$characterId:$expiresEvent",
      turnId = state.turn.currentTurnId,
      actorId = KAI_ID,
      targetId = characterId,
      source = CommandSource.SYSTEM,
      operation = operation,
      effect = effect,
      statusId = id
    ))
    return if (result.applied) result.state else state
  }

  private fun scp173RemoveStatus(state: GameState, characterId: String, statusId: String): GameState {
    if (statusId !in state.statuses) return state
    val result = StatusEngine.execute(state, StatusCommand(
      commandId = "SCP173:STATUS:REMOVE:$statusId",
      turnId = state.turn.currentTurnId,
      actorId = KAI_ID,
      targetId = characterId,
      source = CommandSource.SYSTEM,
      operation = StatusCommand.Operation.REMOVE,
      statusId = statusId
    ))
    return if (result.applied) result.state else state
  }

  private fun scp173CleanupExpiredStatuses(state: GameState, nextEvent: Int): GameState {
    var next = state
    val expired = state.characters.values.flatMap { character ->
      character.statusIds.mapNotNull { statusId ->
        val effect = state.statuses[statusId] ?: return@mapNotNull null
        val expires = effect.metadata[SCP_173_STATUS_EXPIRES_EVENT_KEY]?.toIntOrNull() ?: return@mapNotNull null
        if (effect.source == SCP_173_KEY && effect.id.startsWith(SCP_173_STATUS_PREFIX) && expires < nextEvent) character.id to effect.id else null
      }
    }
    expired.forEach { (characterId, statusId) -> next = scp173RemoveStatus(next, characterId, statusId) }
    return next
  }

  private fun scp173RemoveAllTransientStatuses(state: GameState): GameState {
    var next = state
    val owned = state.characters.values.flatMap { character ->
      character.statusIds.mapNotNull { statusId ->
        val effect = state.statuses[statusId]
        if (effect != null && effect.source == SCP_173_KEY && effect.id.startsWith(SCP_173_STATUS_PREFIX)) character.id to effect.id else null
      }
    }
    owned.forEach { (characterId, statusId) -> next = scp173RemoveStatus(next, characterId, statusId) }
    return next
  }

  private fun scp173Cooldown(state: GameState, key: String, maximum: Int): Int =
    state.metadata[key]?.toIntOrNull()?.coerceIn(0, maximum) ?: 0

  private fun scp173WithCooldown(state: GameState, key: String, value: Int): GameState =
    withCombatCounter(state, key, max(0, value))

  private fun scp173TickCooldowns(state: GameState, nextEvent: Int): GameState {
    val speedTick = if (!scp173Observed(state) && nextEvent % 2 == 0) 2 else 1
    var next = state
    val values = listOf(
      Triple(SCP_173_CONCRETE_RUSH_CD_KEY, SCP_173_CONCRETE_RUSH_COOLDOWN, scp173Cooldown(next, SCP_173_CONCRETE_RUSH_CD_KEY, SCP_173_CONCRETE_RUSH_COOLDOWN)),
      Triple(SCP_173_NECK_SNAP_CD_KEY, SCP_173_NECK_SNAP_COOLDOWN, scp173Cooldown(next, SCP_173_NECK_SNAP_CD_KEY, SCP_173_NECK_SNAP_COOLDOWN)),
      Triple(SCP_173_BLINK_PRESSURE_CD_KEY, SCP_173_BLINK_PRESSURE_COOLDOWN, scp173Cooldown(next, SCP_173_BLINK_PRESSURE_CD_KEY, SCP_173_BLINK_PRESSURE_COOLDOWN))
    )
    values.forEach { (key, _, value) -> next = scp173WithCooldown(next, key, max(0, value - speedTick)) }
    return next
  }

  private fun scp173PrepareTurn(state: GameState, nextEvent: Int): GameState {
    var next = scp173CleanupExpiredStatuses(state, nextEvent)
    next = scp173TickCooldowns(next, nextEvent)
    scp173LivePartyIds(next).forEach { characterId ->
      if (scp173VisionBlocked(next, characterId)) return@forEach
      val advanced = scp173BlinkCounter(next, characterId) + 1
      if (advanced >= SCP_173_BLINK_THRESHOLD) {
        next = scp173WithBlinkCounter(next, characterId, 0)
        next = scp173ApplyTransientStatus(next, characterId, "BLINK", nextEvent)
      } else {
        next = scp173WithBlinkCounter(next, characterId, advanced)
      }
    }
    return scp173RecomputeObservation(next)
  }

  private fun scp173InitializeEncounter(state: GameState): GameState {
    var next = scp173RemoveAllTransientStatuses(state)
    scp173LivePartyIds(next).forEach { characterId -> next = scp173WithBlinkCounter(next, characterId, 0) }
    next = scp173WithCooldown(next, SCP_173_CONCRETE_RUSH_CD_KEY, 0)
    next = scp173WithCooldown(next, SCP_173_NECK_SNAP_CD_KEY, 0)
    next = scp173WithCooldown(next, SCP_173_BLINK_PRESSURE_CD_KEY, 0)
    return scp173RecomputeObservation(next)
  }

  private fun scp173ConsumeFirstStrike(state: GameState): GameState {
    val metadata = state.metadata.toMutableMap()
    metadata[SCP_173_FIRST_STRIKE_PENDING_KEY] = "false"
    return state.copy(metadata = metadata)
  }

  private fun scp173DirectDamage(rawDamage: Int, observed: Boolean): Int {
    if (rawDamage <= 0) return 0
    var adjusted = max(1, rawDamage * (100 - SCP_173_PHYSICAL_DAMAGE_REDUCTION_PERCENT) / 100)
    if (observed) adjusted = max(1, adjusted * (100 - SCP_173_OBSERVED_DAMAGE_REDUCTION_PERCENT) / 100)
    return adjusted
  }

  private fun scp173TargetId(state: GameState): String? {
    val live = scp173LivePartyIds(state)
    return if (KAI_ID in live) KAI_ID else live.firstOrNull()
  }

  private fun scp173KaiHp(state: GameState): Int {
    val maxHp = CharacterStatEngine.effective(state, KAI_ID).maxHp
    return state.characters[KAI_ID]?.vitalState?.currentHp?.coerceIn(0, maxHp) ?: maxHp
  }

'''
if 'private fun scp173LivePartyIds(' not in combat:
    combat = replace_once(combat, helper_anchor, helpers + helper_anchor, "SCP-173 state/status helpers")

# Initialize observation/cooldown/counter state at encounter start, before the
# first Game Master projection is returned.
start_index = combat.find('  fun start(state: GameState, entityKey: String): GameState {')
resolve_index = combat.find('\n  fun resolve(state: GameState, actionKind: String, action: String): Resolution {', start_index)
if start_index < 0 or resolve_index < 0:
    raise RuntimeError("SCP-173 start/resolve boundary missing")
start_section = combat[start_index:resolve_index]
if 'scp173InitializeEncounter(started)' not in start_section:
    start_section = replace_once(
        start_section,
        '    return encode(state, snapshot)\n',
        '    val started = encode(state, snapshot)\n    return if (entityKey == SCP_173_KEY) scp173InitializeEncounter(started) else started\n',
        "SCP-173 encounter initialization",
    )
    combat = combat[:start_index] + start_section + combat[resolve_index:]

# Observation and Blink are resolved before the player's action. A one-turn
# SCP-173 Stun is a real StatusEffect and suppresses the affected target action.
intent_old = '''    val requestedIntent = classify(actionKind, action)
    val monsterXStunTurns = state.metadata[MONSTER_X_STUN_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 1) ?: 0
    val monsterXPartyStunned = current.entityKey == MONSTER_X_KEY && monsterXStunTurns > 0
    val johnDoeStunTurns = state.metadata[JOHN_DOE_STUN_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 1) ?: 0
    val johnDoeTargetStunned = current.entityKey == JOHN_DOE_KEY && johnDoeStunTurns > 0
    val intent = if (monsterXPartyStunned || johnDoeTargetStunned) Intent.OTHER else requestedIntent
    var c = current.copy(eventCounter = current.eventCounter + 1)
    val log = mutableListOf<String>()
'''
intent_new = '''    val scp173NextEvent = current.eventCounter + 1
    val scp173PreparedState = if (current.entityKey == SCP_173_KEY) scp173PrepareTurn(state, scp173NextEvent) else state
    val scp173ObservedNow = current.entityKey == SCP_173_KEY && scp173Observed(scp173PreparedState)
    val requestedIntent = classify(actionKind, action)
    val monsterXStunTurns = state.metadata[MONSTER_X_STUN_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 1) ?: 0
    val monsterXPartyStunned = current.entityKey == MONSTER_X_KEY && monsterXStunTurns > 0
    val johnDoeStunTurns = state.metadata[JOHN_DOE_STUN_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, 1) ?: 0
    val johnDoeTargetStunned = current.entityKey == JOHN_DOE_KEY && johnDoeStunTurns > 0
    val scp173TargetStunned = current.entityKey == SCP_173_KEY && scp173CharacterHasStatus(scp173PreparedState, KAI_ID, "STUN")
    val intent = if (monsterXPartyStunned || johnDoeTargetStunned || scp173TargetStunned) Intent.OTHER else requestedIntent
    var c = current.copy(eventCounter = current.eventCounter + 1)
    val log = mutableListOf<String>()
'''
combat = replace_once(combat, intent_old, intent_new, "SCP-173 pre-action observation state")

resolved_old = '''    var resolvedState = state
    var monsterXBleedTurns = state.metadata[MONSTER_X_BLEED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, MONSTER_X_BLEED_DURATION_TURNS) ?: 0
'''
resolved_new = '''    var resolvedState = scp173PreparedState
    var monsterXBleedTurns = state.metadata[MONSTER_X_BLEED_TURNS_KEY]?.toIntOrNull()?.coerceIn(0, MONSTER_X_BLEED_DURATION_TURNS) ?: 0
'''
combat = replace_once(combat, resolved_old, resolved_new, "SCP-173 prepared authoritative state")

stun_log_anchor = '''    if (johnDoeTargetStunned) {
      resolvedState = withCombatCounter(resolvedState, JOHN_DOE_STUN_TURNS_KEY, 0)
      log += "John Doe Stun: mục tiêu bị Stun và không thể thực hiện hành động trong lượt hiện tại."
    }
'''
stun_log_new = stun_log_anchor + '''    if (scp173TargetStunned) {
      log += "SCP-173 Stun: mục tiêu đang bị Stun 1 lượt; hành động hiện tại bị chặn và mục tiêu không thể duy trì quan sát."
    }
'''
combat = replace_once(combat, stun_log_anchor, stun_log_new, "SCP-173 one-turn Stun consumption")

# Concrete Body applies to direct physical/weapon impact damage only. Existing
# Bleed variables use bleedDamage and intentionally remain on their original DOT
# path so SCP-173 is not made Bleed-immune.
resolve_start = combat.find('  fun resolve(state: GameState, actionKind: String, action: String): Resolution {')
response_start = combat.find('    if (entityStunnedThisTurn) {', resolve_start)
if resolve_start < 0 or response_start < 0:
    raise RuntimeError("SCP-173 direct-damage rewrite boundary missing")
prefix = combat[:response_start]
suffix = combat[response_start:]
pattern = re.compile(r'(?m)^(\s*)val damage = ([^\n]+)$')
rewritten = 0

def wrap_direct(match: re.Match) -> str:
    global rewritten
    indent, expression = match.group(1), match.group(2)
    if 'scp173DirectDamage(' in expression or expression.rstrip().endswith('{'):
        return match.group(0)
    rewritten += 1
    return f'{indent}val damage = if (c.entityKey == SCP_173_KEY) scp173DirectDamage(({expression}), scp173ObservedNow) else ({expression})'

prefix = pattern.sub(wrap_direct, prefix)
if rewritten < 5:
    raise RuntimeError(f"SCP-173 expected at least five direct-damage sites before Entity response, rewrote {rewritten}")
combat = prefix + suffix

# Guilty Crown keeps its globally locked raw 24 x 10 calculation, then applies
# the target's defenses. Non-SCP tests still see exactly 240 damage.
guilty_old = '''      val totalDamage = KAI_GUILTY_CROWN_SHOTS * KAI_GUILTY_CROWN_DAMAGE_PER_SHOT
      val hp = max(0, c.entityHp - totalDamage)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
'''
guilty_new = '''      val totalDamage = KAI_GUILTY_CROWN_SHOTS * KAI_GUILTY_CROWN_DAMAGE_PER_SHOT
      val appliedTotalDamage = if (c.entityKey == SCP_173_KEY) scp173DirectDamage(totalDamage, scp173ObservedNow) else totalDamage
      val hp = max(0, c.entityHp - appliedTotalDamage)
      c = c.copy(entityHp = hp, entityCondition = condition(hp, c.entityMaxHp))
'''
combat = replace_once(combat, guilty_old, guilty_new, "SCP-173 Guilty Crown mitigation")
combat = replace_once(
    combat,
    '        "mỗi phát -$KAI_GUILTY_CROWN_DAMAGE_PER_SHOT HP, tổng -$totalDamage HP (${c.entityHp}/${c.entityMaxHp})."\n',
    '        "mỗi phát -$KAI_GUILTY_CROWN_DAMAGE_PER_SHOT HP trước giảm trừ, tổng thực nhận -$appliedTotalDamage HP (${c.entityHp}/${c.entityMaxHp})."\n',
    "SCP-173 Guilty Crown narration from committed damage",
)

# DON'T BLINK owns SCP-173's response. OBSERVED forbids movement, attacks and
# approach; Blink Pressure is allowed because it is a non-movement pressure
# effect. UNOBSERVED enables attacks and the first one gains +5% target Max HP.
response_anchor = '''    if (entityStunnedThisTurn) {
      log += "Silent Lullaby: ${c.entityName} bị Stun và mất lượt phản ứng hiện tại."
    } else if (c.entityKey == JOHN_DOE_KEY) {
'''
response_new = '''    if (entityStunnedThisTurn) {
      log += "Silent Lullaby: ${c.entityName} bị Stun và mất lượt phản ứng hiện tại."
    } else if (c.entityKey == SCP_173_KEY) {
      if (scp173Observed(resolvedState)) {
        val pressureCooldown = scp173Cooldown(resolvedState, SCP_173_BLINK_PRESSURE_CD_KEY, SCP_173_BLINK_PRESSURE_COOLDOWN)
        if (pressureCooldown <= 0) {
          var pressureState = resolvedState
          val targets = scp173LivePartyIds(pressureState)
            .sortedWith(compareByDescending<String> { scp173BlinkCounter(pressureState, it) }.thenBy { it })
            .take(2)
          val details = mutableListOf<String>()
          targets.forEachIndexed { index, characterId ->
            val character = pressureState.characters[characterId] ?: return@forEachIndexed
            val advanced = scp173BlinkCounter(pressureState, characterId) + 1
            val thresholdBlink = advanced >= SCP_173_BLINK_THRESHOLD
            val forcedBlink = roll(c.copy(eventCounter = c.eventCounter + 733 + index * 17), 100) < SCP_173_FORCED_BLINK_PERCENT
            if (thresholdBlink || forcedBlink) {
              pressureState = scp173WithBlinkCounter(pressureState, characterId, 0)
              pressureState = scp173ApplyTransientStatus(pressureState, characterId, "BLINK", c.eventCounter + 1)
              details += "${character.name} Blink"
            } else {
              pressureState = scp173WithBlinkCounter(pressureState, characterId, advanced)
              details += "${character.name} blinkCounter=$advanced"
            }
          }
          pressureState = scp173WithCooldown(pressureState, SCP_173_BLINK_PRESSURE_CD_KEY, SCP_173_BLINK_PRESSURE_COOLDOWN)
          pressureState = scp173RecomputeObservation(pressureState)
          resolvedState = pressureState
          log += "Blink Pressure: tăng blinkCounter +1 cho ${targets.size} mục tiêu, Forced Blink ${SCP_173_FORCED_BLINK_PERCENT}%; ${if (details.isEmpty()) "không có mục tiêu sống hợp lệ" else details.joinToString("; ")}. State=${resolvedState.metadata[SCP_173_STATE_KEY]}."
        } else {
          log += "DON'T BLINK: SCP-173 đang OBSERVED, bất động hoàn toàn và không thể di chuyển, áp sát hay tấn công; Blink Pressure còn CD $pressureCooldown."
        }
      } else {
        val targetId = scp173TargetId(resolvedState)
        if (targetId == null) {
          log += "SCP-173 ở UNOBSERVED nhưng không còn mục tiêu ACTIVE hợp lệ."
        } else {
          val target = resolvedState.characters.getValue(targetId)
          val targetMaxHp = CharacterStatEngine.effective(resolvedState, targetId).maxHp
          val before = target.vitalState.currentHp.coerceIn(0, targetMaxHp)
          val firstStrike = resolvedState.metadata[SCP_173_FIRST_STRIKE_PENDING_KEY].equals("true", true)
          val bonusPercent = if (firstStrike) SCP_173_FIRST_UNOBSERVED_BONUS_PERCENT else 0
          val vulnerable = scp173CharacterHasStatus(resolvedState, targetId, "BLINK", "BLIND", "STUN")
          val neckCooldown = scp173Cooldown(resolvedState, SCP_173_NECK_SNAP_CD_KEY, SCP_173_NECK_SNAP_COOLDOWN)
          val rushCooldown = scp173Cooldown(resolvedState, SCP_173_CONCRETE_RUSH_CD_KEY, SCP_173_CONCRETE_RUSH_COOLDOWN)

          if (c.range == RangeBand.CLOSE && neckCooldown <= 0) {
            val execution = before * 100 <= targetMaxHp * SCP_173_EXECUTION_THRESHOLD_PERCENT
            val requested = percentDamage(targetMaxHp, SCP_173_NECK_SNAP_PERCENT + bonusPercent)
            val damage = if (execution) before else min(before, requested)
            resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, targetId, before - damage)
            resolvedState = scp173WithCooldown(resolvedState, SCP_173_NECK_SNAP_CD_KEY, SCP_173_NECK_SNAP_COOLDOWN)
            resolvedState = scp173ConsumeFirstStrike(resolvedState)
            if (targetId == KAI_ID) c = c.copy(playerHp = scp173KaiHp(resolvedState), momentum = max(-3, c.momentum - 1))
            log += if (execution) {
              "Neck Snap: ${target.name} ở ${before}/${targetMaxHp} HP (<=${SCP_173_EXECUTION_THRESHOLD_PERCENT}%), Execution hợp lệ; HP được cập nhật về 0 trước narration."
            } else {
              "Neck Snap: ${target.name} -$damage HP (${SCP_173_NECK_SNAP_PERCENT}% Max HP${if (firstStrike) " + ${SCP_173_FIRST_UNOBSERVED_BONUS_PERCENT}% first UNOBSERVED strike" else ""}); CD ${SCP_173_NECK_SNAP_COOLDOWN}."
            }
          } else if (rushCooldown <= 0) {
            val basePercent = if (vulnerable) SCP_173_CONCRETE_RUSH_VULNERABLE_PERCENT else SCP_173_CONCRETE_RUSH_PERCENT
            val totalPercent = basePercent + bonusPercent
            val damage = min(before, percentDamage(targetMaxHp, totalPercent))
            resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, targetId, before - damage)
            resolvedState = scp173WithCooldown(resolvedState, SCP_173_CONCRETE_RUSH_CD_KEY, SCP_173_CONCRETE_RUSH_COOLDOWN)
            resolvedState = scp173ConsumeFirstStrike(resolvedState)
            val after = resolvedState.characters[targetId]?.vitalState?.currentHp ?: max(0, before - damage)
            var stunned = false
            if (after > 0 && roll(c.copy(eventCounter = c.eventCounter + 769), 100) < SCP_173_CONCRETE_RUSH_STUN_PERCENT) {
              resolvedState = scp173ApplyTransientStatus(resolvedState, targetId, "STUN", c.eventCounter + 1)
              stunned = true
            }
            c = c.copy(range = RangeBand.CLOSE, playerHp = if (targetId == KAI_ID) scp173KaiHp(resolvedState) else c.playerHp, momentum = max(-3, c.momentum - 1))
            log += "Concrete Rush: ${target.name} -$damage HP ($totalPercent% Max HP${if (vulnerable) ", vulnerable Blink/Blind/Stun" else ""}); CD ${SCP_173_CONCRETE_RUSH_COOLDOWN}; ${if (stunned) "Stun 1 lượt (${SCP_173_CONCRETE_RUSH_STUN_PERCENT}% proc)" else "Stun không proc"}."
          } else {
            val totalPercent = SCP_173_SNAP_STRIKE_PERCENT + bonusPercent
            val damage = min(before, percentDamage(targetMaxHp, totalPercent))
            resolvedState = CharacterStatEngine.setCurrentHp(resolvedState, targetId, before - damage)
            resolvedState = scp173ConsumeFirstStrike(resolvedState)
            val after = resolvedState.characters[targetId]?.vitalState?.currentHp ?: max(0, before - damage)
            var stunned = false
            if (after > 0 && roll(c.copy(eventCounter = c.eventCounter + 797), 100) < SCP_173_SNAP_STRIKE_STUN_PERCENT) {
              resolvedState = scp173ApplyTransientStatus(resolvedState, targetId, "STUN", c.eventCounter + 1)
              stunned = true
            }
            c = c.copy(
              range = if (c.range == RangeBand.FAR) RangeBand.NEAR else c.range,
              playerHp = if (targetId == KAI_ID) scp173KaiHp(resolvedState) else c.playerHp,
              momentum = max(-3, c.momentum - 1)
            )
            log += "Snap Strike: ${target.name} -$damage HP ($totalPercent% Max HP); ${if (stunned) "Stun 1 lượt (${SCP_173_SNAP_STRIKE_STUN_PERCENT}% proc)" else "Stun không proc"}."
          }
        }
      }
    } else if (c.entityKey == JOHN_DOE_KEY) {
'''
combat = replace_once(combat, response_anchor, response_new, "SCP-173 observed/unobserved response")

regen_old = '    val entityRegen = when (c.entityKey) { DIEP_MINH_KEY -> DIEP_MINH_REGEN_PER_TURN; MONSTER_X_KEY -> MONSTER_X_REGEN_PER_TURN; JOHN_DOE_KEY -> JOHN_DOE_REGEN_PER_TURN; else -> ENTITY_REGEN_PER_TURN }\n'
regen_new = '    val entityRegen = when (c.entityKey) { DIEP_MINH_KEY -> DIEP_MINH_REGEN_PER_TURN; MONSTER_X_KEY -> MONSTER_X_REGEN_PER_TURN; JOHN_DOE_KEY -> JOHN_DOE_REGEN_PER_TURN; SCP_173_KEY -> SCP_173_REGEN_PER_TURN; else -> ENTITY_REGEN_PER_TURN }\n'
combat = replace_once(combat, regen_old, regen_new, "SCP-173 no unrequested regeneration")

# Clear SCP-created Blink/Stun when combat ends. This preserves external Blind,
# Stun or LOS statuses because only source=scp_173 + scp173:* IDs are removed.
clear_old = '''  private fun clearCombatOnly(state: GameState): GameState {
    val metadata = state.metadata.filterKeys { !it.startsWith(PREFIX) }
    return state.copy(metadata = metadata)
  }
'''
clear_new = '''  private fun clearCombatOnly(state: GameState): GameState {
    val metadata = state.metadata.filterKeys { !it.startsWith(PREFIX) }
    return scp173RemoveAllTransientStatuses(state.copy(metadata = metadata))
  }
'''
combat = replace_once(combat, clear_old, clear_new, "SCP-173 transient status cleanup")

# Project DON'T BLINK state into the same combat JSON consumed by the HUD/GM.
to_json_anchor = '    put("telegraphRevealed", c.telegraphRevealed)\n'
to_json_extra = '''    if (c.entityKey == SCP_173_KEY) {
      put("entityType", "Hostile Entity / Concrete Anomaly")
      put("passive", "DON'T BLINK")
      put("observationState", state.metadata[SCP_173_STATE_KEY] ?: "OBSERVED")
      put("actionSpeedPercent", state.metadata[SCP_173_ACTION_SPEED_KEY]?.toIntOrNull() ?: SCP_173_OBSERVED_ACTION_SPEED_PERCENT)
      put("firstUnobservedStrikePending", state.metadata[SCP_173_FIRST_STRIKE_PENDING_KEY].equals("true", true))
      put("observedDamageReductionPercent", SCP_173_OBSERVED_DAMAGE_REDUCTION_PERCENT)
      put("physicalDamageReductionPercent", SCP_173_PHYSICAL_DAMAGE_REDUCTION_PERCENT)
      put("stunMaxTurns", 1)
      put("immunities", JSONArray(listOf("POISON", "FEAR", "KNOCKBACK")))
      put("blinkCounters", JSONObject().apply { scp173LivePartyIds(state).forEach { characterId -> put(characterId, scp173BlinkCounter(state, characterId)) } })
      put("cooldowns", JSONObject().apply {
        put("concreteRush", scp173Cooldown(state, SCP_173_CONCRETE_RUSH_CD_KEY, SCP_173_CONCRETE_RUSH_COOLDOWN))
        put("neckSnap", scp173Cooldown(state, SCP_173_NECK_SNAP_CD_KEY, SCP_173_NECK_SNAP_COOLDOWN))
        put("blinkPressure", scp173Cooldown(state, SCP_173_BLINK_PRESSURE_CD_KEY, SCP_173_BLINK_PRESSURE_COOLDOWN))
      })
    }
'''
if 'put("observationState", state.metadata[SCP_173_STATE_KEY]' not in combat:
    combat = replace_once(combat, to_json_anchor, to_json_anchor + to_json_extra, "SCP-173 state projection")

for marker in (
    'private const val SCP_173_MAX_HP = 1730',
    'private const val SCP_173_OBSERVED_DAMAGE_REDUCTION_PERCENT = 20',
    'private const val SCP_173_PHYSICAL_DAMAGE_REDUCTION_PERCENT = 25',
    'private const val SCP_173_UNOBSERVED_ACTION_SPEED_PERCENT = 150',
    'private const val SCP_173_BLINK_THRESHOLD = 3',
    'private const val SCP_173_SNAP_STRIKE_PERCENT = 10',
    'private const val SCP_173_CONCRETE_RUSH_PERCENT = 16',
    'private const val SCP_173_CONCRETE_RUSH_VULNERABLE_PERCENT = 20',
    'private const val SCP_173_NECK_SNAP_PERCENT = 30',
    'private const val SCP_173_EXECUTION_THRESHOLD_PERCENT = 15',
    'private const val SCP_173_FORCED_BLINK_PERCENT = 30',
    'Profile(SCP_173_KEY, "SCP-173", SCP_173_MAX_HP, 0, 9, 10)',
    'scp173ApplyTransientStatus(resolvedState, targetId, "STUN", c.eventCounter + 1)',
    'scp173ApplyTransientStatus(pressureState, characterId, "BLINK", c.eventCounter + 1)',
    'DON\'T BLINK: SCP-173 đang OBSERVED',
    'Concrete Rush:',
    'Neck Snap:',
    'Snap Strike:',
    'Blink Pressure:',
    'SCP_173_KEY -> SCP_173_REGEN_PER_TURN',
    'put("observationState"',
):
    if marker not in combat:
        raise RuntimeError("SCP-173 combat contract missing: " + marker)

if 'val bleedDamage = if (c.entityKey == SCP_173_KEY)' in combat or 'scp173DirectDamage(bleedDamage' in combat:
    raise RuntimeError("Concrete Body must not make SCP-173 immune/resistant to Bleed DOT")
COMBAT.write_text(combat, encoding="utf-8")


# ---------------------------------------------------------------------------
# Android encounter + overlay
# ---------------------------------------------------------------------------
# SCP-173 has its own 5% dice channel on every otherwise-valid Entity encounter
# roll. It is not inserted into the shared pool and has no hidden Level gate.
main = MAIN.read_text(encoding="utf-8")

john_roll_anchor = '    rolls.put("johnDoeEncounter", johnDoeRoll);\n'
scp_roll = '''    JSONObject scp173Roll = thresholdRoll("scp173Encounter", 10000, 500,
      entityEncounterAction && entityAllowed,
      " SCP-173 independent 5% valid encounter");
    rolls.put("scp173Encounter", scp173Roll);
'''
if 'rolls.put("scp173Encounter", scp173Roll);' not in main:
    main = replace_once(main, john_roll_anchor, john_roll_anchor + scp_roll, "SCP-173 independent 5 percent roll")

main = replace_once(
    main,
    '      case "jeff_the_killer": case "jane_the_killer": case "slenderman": case "diep_minh": case "monster_x": case "john_doe":\n        return key;\n',
    '      case "jeff_the_killer": case "jane_the_killer": case "slenderman": case "diep_minh": case "monster_x": case "john_doe": case "scp_173":\n        return key;\n',
    "SCP-173 canonical key",
)
main = replace_once(
    main,
    '      case "john_doe": name = "John Doe"; break;\n',
    '      case "john_doe": name = "John Doe"; break;\n      case "scp_173": name = "SCP-173"; break;\n',
    "SCP-173 display name",
)
main = replace_once(
    main,
    '      .put("url", "file:///android_asset/entity/" + ("monster_x".equals(entityKey) ? "X.png" : ("john_doe".equals(entityKey) ? "John.png" : entityKey + ".png")));\n',
    '      .put("url", "file:///android_asset/entity/" + ("monster_x".equals(entityKey) ? "X.png" : ("john_doe".equals(entityKey) ? "John.png" : ("scp_173".equals(entityKey) ? "173.png" : entityKey + ".png"))));\n',
    "SCP-173 direct 173.png asset",
)
main = replace_once(
    main,
    "'jeff_the_killer','jane_the_killer','slenderman','diep_minh','monster_x','john_doe'];",
    "'jeff_the_killer','jane_the_killer','slenderman','diep_minh','monster_x','john_doe','scp_173'];",
    "SCP-173 overlay JS key",
)

helper_start = main.find('  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {')
helper_end = main.find('\n  private JSONObject resolveEntityOverlay(String rawEntityKey) throws Exception {', helper_start)
if helper_start < 0 or helper_end < 0:
    raise RuntimeError("SCP-173 final encounter helper boundary missing")
helper = r'''  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {
    if (candidateState == null || rolls == null) return;
    String entityKey = rolls.optString("roamingEntityKey", "").trim();
    JSONObject boss = rolls.optJSONObject("diepMinhEncounter");
    JSONObject monsterX = rolls.optJSONObject("monsterXEncounter");
    JSONObject johnDoe = rolls.optJSONObject("johnDoeEncounter");
    JSONObject scp173 = rolls.optJSONObject("scp173Encounter");
    if (boss != null && boss.optBoolean("success", false)) {
      entityKey = "diep_minh";
    } else if (monsterX != null && monsterX.optBoolean("success", false)) {
      entityKey = "monster_x";
    } else if (johnDoe != null && johnDoe.optBoolean("success", false)) {
      entityKey = "john_doe";
    } else if (scp173 != null && scp173.optBoolean("success", false)) {
      entityKey = "scp_173";
    } else {
      JSONObject normal = rolls.optJSONObject("entityEncounter");
      if (normal == null || !normal.optBoolean("success", false)) return;
      if (entityKey.isEmpty()) return;
    }
    JSONObject flags = candidateState.optJSONObject("flags");
    if (flags == null) {
      flags = new JSONObject();
      candidateState.put("flags", flags);
    }
    String canonicalKey = normalizedEntityKey(entityKey);
    flags.put("entityEncounterKey", canonicalKey);
    requireGameCore().startCombatState(candidateState.toString(), canonicalKey);
  }
'''
main = main[:helper_start] + helper + main[helper_end:]

for marker in (
    'thresholdRoll("scp173Encounter", 10000, 500',
    'entityEncounterAction && entityAllowed,',
    'rolls.put("scp173Encounter", scp173Roll)',
    'case "scp_173":',
    'case "scp_173": name = "SCP-173"; break;',
    '"scp_173".equals(entityKey) ? "173.png"',
    "'john_doe','scp_173']",
    'JSONObject scp173 = rolls.optJSONObject("scp173Encounter")',
    'entityKey = "scp_173";',
):
    if marker not in main:
        raise RuntimeError("SCP-173 encounter/overlay contract missing: " + marker)
MAIN.write_text(main, encoding="utf-8")


# ---------------------------------------------------------------------------
# Regression coverage
# ---------------------------------------------------------------------------
test = TEST.read_text(encoding="utf-8")
if 'scp173StartsObservedWithExactHpAndStateProjection' not in test:
    tests = r'''
  @Test fun scp173StartsObservedWithExactHpAndStateProjection() {
    val state = CombatRuntime.start(GameState.initial(), "scp_173")
    val active = CombatRuntime.active(state)!!
    val json = CombatRuntime.toJson(state)!!
    assertEquals(1730, active.entityMaxHp)
    assertEquals(1730, active.entityHp)
    assertEquals("OBSERVED", json.getString("observationState"))
    assertEquals(100, json.getInt("actionSpeedPercent"))
    assertEquals(25, json.getInt("physicalDamageReductionPercent"))
    assertEquals(20, json.getInt("observedDamageReductionPercent"))
    assertEquals(1, json.getInt("stunMaxTurns"))
  }

  @Test fun scp173ObservedCannotAttackMoveOrApproach() {
    var state = CombatRuntime.start(GameState.initial(), "scp_173")
    state = state.copy(metadata = state.metadata + ("combat.scp173.cooldown.blinkPressure" to "3"))
    val beforeHp = state.characters.getValue(KAI_ID).vitalState.currentHp
    val beforeRange = CombatRuntime.active(state)!!.range
    val result = CombatRuntime.resolve(state, "EXECUTE", "giữ phòng thủ và nhìn thẳng SCP-173")
    val after = CombatRuntime.active(result.state)!!
    assertEquals(beforeHp, result.state.characters.getValue(KAI_ID).vitalState.currentHp)
    assertEquals(beforeRange, after.range)
    assertTrue(result.reply, result.reply.contains("OBSERVED"))
    assertTrue(result.reply, result.reply.contains("không thể di chuyển, áp sát hay tấn công"))
  }

  @Test fun scp173ThirdObservedTurnForcesBlinkAndBecomesUnobserved() {
    var state = CombatRuntime.start(GameState.initial(), "scp_173")
    state = state.copy(metadata = state.metadata + ("combat.scp173.cooldown.blinkPressure" to "3"))
    repeat(3) {
      val result = CombatRuntime.resolve(state, "SEARCH", "tiếp tục nhìn SCP-173")
      assertTrue(result.handled)
      state = result.state
    }
    val json = CombatRuntime.toJson(state)!!
    assertEquals("UNOBSERVED", json.getString("observationState"))
    assertEquals(150, json.getInt("actionSpeedPercent"))
    assertTrue(state.characters.getValue(KAI_ID).statusIds.any { id -> state.statuses[id]?.type == "BLINK" })
  }

  @Test fun scp173ObservedConcreteBodyMitigatesGuiltyCrownDirectDamage() {
    var state = CombatRuntime.start(GameState.initial(), "scp_173")
    state = state.copy(metadata = state.metadata + mapOf(
      "combat.eventCounter" to "2",
      "combat.scp173.cooldown.blinkPressure" to "3"
    ))
    val result = CombatRuntime.resolve(state, "SEARCH", "duy trì quan sát")
    assertTrue(result.reply, result.reply.contains("Guilty Crown Override"))
    // Raw 240 direct physical damage -> -25% Concrete Body -> -20% OBSERVED = 144.
    assertTrue(result.reply, result.reply.contains("tổng thực nhận -144 HP"))
  }

  @Test fun scp173UnobservedConcreteRushUsesVulnerableTwentyPlusFirstStrikeFivePercent() {
    val initial = GameState.initial()
    val blindEffect = StatusEffect("test:blind:kai", "BLIND", "test", durationTurns = 5)
    val blinded = StatusEngine.execute(initial, StatusCommand(
      commandId = "test:blind", turnId = null, actorId = KAI_ID, targetId = KAI_ID,
      source = CommandSource.SYSTEM, operation = StatusCommand.Operation.APPLY, effect = blindEffect
    )).state
    val state = CombatRuntime.start(blinded, "scp_173")
    val maxHp = CharacterStatEngine.effective(state, KAI_ID).maxHp
    val before = state.characters.getValue(KAI_ID).vitalState.currentHp
    val result = CombatRuntime.resolve(state, "SEARCH", "không thể nhìn thấy SCP-173")
    val expected = maxOf(1, (maxHp * 25 + 99) / 100)
    assertTrue(result.reply, result.reply.contains("Concrete Rush"))
    assertTrue(result.reply, result.reply.contains("25% Max HP"))
    assertEquals(before - expected, result.state.characters.getValue(KAI_ID).vitalState.currentHp)
  }

  @Test fun scp173NeckSnapExecutesOnlyAtOrBelowFifteenPercent() {
    val initial = GameState.initial()
    val blindEffect = StatusEffect("test:blind:kai:neck", "BLIND", "test", durationTurns = 5)
    var state = StatusEngine.execute(initial, StatusCommand(
      commandId = "test:blind:neck", turnId = null, actorId = KAI_ID, targetId = KAI_ID,
      source = CommandSource.SYSTEM, operation = StatusCommand.Operation.APPLY, effect = blindEffect
    )).state
    state = CombatRuntime.start(state, "scp_173")
    val maxHp = CharacterStatEngine.effective(state, KAI_ID).maxHp
    val threshold = maxOf(1, maxHp * 15 / 100)
    state = CharacterStatEngine.setCurrentHp(state, KAI_ID, threshold)
    state = state.copy(metadata = state.metadata + ("combat.range" to CombatRuntime.RangeBand.CLOSE.name))
    val result = CombatRuntime.resolve(state, "SEARCH", "không thể quan sát SCP-173")
    assertEquals(0, result.state.characters.getValue(KAI_ID).vitalState.currentHp)
    assertTrue(result.reply, result.reply.contains("Neck Snap"))
    assertTrue(result.reply, result.reply.contains("Execution hợp lệ"))
  }

  @Test fun scp173SnapStrikeStunUsesStatusEngineForOneTurn() {
    var verified = false
    for (counter in 0..600) {
      if (verified) break
      val initial = GameState.initial()
      val blindEffect = StatusEffect("test:blind:kai:snap:$counter", "BLIND", "test", durationTurns = 5)
      var state = StatusEngine.execute(initial, StatusCommand(
        commandId = "test:blind:snap:$counter", turnId = null, actorId = KAI_ID, targetId = KAI_ID,
        source = CommandSource.SYSTEM, operation = StatusCommand.Operation.APPLY, effect = blindEffect
      )).state
      state = CombatRuntime.start(state, "scp_173")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.scp173.cooldown.concreteRush" to "2",
        "combat.scp173.cooldown.neckSnap" to "4"
      ))
      val result = CombatRuntime.resolve(state, "SEARCH", "mất tầm nhìn")
      if (!result.reply.contains("Snap Strike") || !result.reply.contains("Stun 1 lượt")) continue
      val stun = result.state.characters.getValue(KAI_ID).statusIds.mapNotNull(result.state.statuses::get)
        .firstOrNull { it.source == "scp_173" && it.type == "STUN" }
      assertNotNull(stun)
      assertEquals(1, stun!!.durationTurns)
      verified = true
    }
    assertTrue("Expected deterministic search to reach SCP-173 Snap Strike 25% Stun proc", verified)
  }
'''
    close = test.rfind('\n}')
    if close < 0:
        raise RuntimeError("CombatRuntimeTest closing brace missing")
    test = test[:close] + tests + test[close:]
    TEST.write_text(test, encoding="utf-8")

if not ASSET.is_file() or ASSET.stat().st_size <= 0:
    raise RuntimeError("SCP-173 asset missing: android-apk/app/src/main/assets/entity/173.png")
raw = ASSET.read_bytes()
if raw[:8] != b'\x89PNG\r\n\x1a\n':
    raise RuntimeError("173.png is not a valid PNG asset")
if b'data:image' in raw[:1024].lower() or b'base64,' in raw[:1024].lower():
    raise RuntimeError("173.png must remain a raw PNG asset, not an embedded Data URI/Base64 payload")

print("SCP-173 installed: 1730 HP, independent 5% encounter, DON'T BLINK observation/blink state, Snap Strike, Concrete Rush, Neck Snap, Blink Pressure, Concrete Body, raw 173.png asset.")
