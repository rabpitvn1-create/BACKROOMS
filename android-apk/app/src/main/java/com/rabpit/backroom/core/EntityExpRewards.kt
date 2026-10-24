package com.rabpit.backroom.core

/**
 * Persisted current-generation EXP/Level progression.
 * Separate from the already-live Core stat-upgrade economy.
 * The APK's progression is reconstructed, not byte-for-byte Kotlin source.
 */
object EntityExpRewards {
  const val MAX_LEVEL = 165
  private const val BASE_EXP = 2
  private const val PER_BACKROOMS_LEVEL = 2
  private const val PREFIX = "progression."
  private const val REWARDED = "exp.awarded."

  data class Progression(val level: Int, val exp: Int, val requiredExp: Int)
  data class Reward(val state: GameState, val amount: Int, val leveledUp: Int, val alreadyAwarded: Boolean)

  private fun levelKey(id: String) = "$PREFIX$id.level"
  private fun expKey(id: String) = "$PREFIX$id.exp"

  fun requiredExp(level: Int): Int {
    val l=level.coerceIn(1, MAX_LEVEL)
    return if(l==MAX_LEVEL) 0 else (8L*l + 10L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
  }

  fun progression(state: GameState, characterId: String): Progression {
    val level = (state.metadata[levelKey(characterId)]?.toIntOrNull() ?: 1).coerceIn(1, MAX_LEVEL)
    val required = requiredExp(level)
    val exp = (state.metadata[expKey(characterId)]?.toIntOrNull() ?: 0).coerceIn(0, if(required>0) required-1 else 0)
    return Progression(level, exp, required)
  }

  fun grantExp(state: GameState, characterId: String, amount: Int): Reward {
    if (amount<=0 || characterId !in state.characters) return Reward(state,0,0,false)
    val old=progression(state,characterId)
    var level=old.level
    var exp=old.exp.toLong()+amount.toLong()
    var gains=0
    while(level<MAX_LEVEL) {
      val threshold=requiredExp(level).toLong()
      if(exp<threshold)break
      exp-=threshold
      level++
      gains++
    }
    if(level==MAX_LEVEL)exp=0L
    val updated=state.copy(metadata=state.metadata + mapOf(levelKey(characterId) to level.toString(),expKey(characterId) to exp.toString()))
    return Reward(updated,amount,gains,false)
  }

  /**
   * Reward key is encounter-scoped, preventing repeated EXP after UI retries,
   * save/reload and duplicate callbacks.
   */
  fun award(state: GameState, encounterId: String, entityKey: String, characterId: String = CAO_MINH_ID): Reward {
    if(encounterId.isBlank()||entityKey.isBlank()||characterId !in state.characters)return Reward(state,0,0,false)
    val marker=REWARDED+encounterId
    if(marker in state.metadata)return Reward(state,0,0,true)
    val worldLevel = runCatching {
      org.json.JSONObject(state.world["levelJson"] ?: "{}").optInt("number", 0)
    }.getOrDefault(0)
    val exp=(BASE_EXP.toLong()+PER_BACKROOMS_LEVEL.toLong()*worldLevel.coerceAtLeast(0).toLong())
      .coerceIn(1,Int.MAX_VALUE.toLong()).toInt()
    val awarded=grantExp(state,characterId,exp)
    return awarded.copy(state=awarded.state.copy(metadata=awarded.state.metadata+(marker to entityKey)))
  }
}
