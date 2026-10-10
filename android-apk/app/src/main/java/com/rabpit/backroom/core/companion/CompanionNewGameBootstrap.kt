package com.rabpit.backroom.core.companion

import android.content.Context
import com.rabpit.backroom.core.GameState
import com.rabpit.backroom.core.GameStateCodec
import com.rabpit.backroom.core.KAI_ID
import com.rabpit.backroom.core.progression.FeaturedJourneyRoutes
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Native-only entry for an EXPLICIT fresh companion campaign.
 *
 * No WebView state or model output is accepted. The slot owns its own verified
 * snapshot; the legacy SharedPreferences/localStorage saves are NOT imported.
 * A real gameplay host must select this slot as sole authority before making
 * the companion interaction UI available.
 */
internal object CompanionNewGameBootstrap {
  private const val STOP = "level-0"

  @JvmStatic
  fun seed(): ByteArray {
    if (KAI_ID != "cao_minh") throw IOException("cao_minh_identity_unavailable")
    val node = FeaturedJourneyRoutes.nodeIdAt(STOP)
      ?: throw IOException("level_zero_route_unavailable")
    val level = JSONObject()
      .put("number", 0).put("name", "The Lobby")
      .put("stopKey", STOP).put("nodeId", node)
    val flags = JSONObject().put("exploration", JSONObject()
      .put("exitStreakNode", STOP).put("exitStreak", 0))
    val state = GameState.initial().copy(
      world = mapOf(
        "title" to "Level 0 – The Lobby",
        "location" to "Level 0 / The Lobby",
        "levelJson" to level.toString(),
        "worldNodeId" to node,
        "journeyStopKey" to STOP,
        "flagsJson" to flags.toString()
      )
    )
    if (state.party.leaderId != KAI_ID || KAI_ID !in state.party.memberIds ||
        state.characters[KAI_ID]?.name != "Cao Minh" || state.turn.pending != null)
      throw IOException("companion_genesis_identity_invalid")
    val snapshot = GameStateCodec.encode(state)
    if (GameStateCodec.decode(snapshot) != state) throw IOException("companion_genesis_roundtrip_invalid")
    return snapshot.toByteArray(StandardCharsets.UTF_8)
  }

  /** Verifies live packaged canon bytes through CompanionSlotStore.create(). */
  @JvmStatic
  @Throws(IOException::class)
  fun create(context: Context): CompanionSlotStore =
    CompanionSlotStore.create(context.applicationContext, seed(), CompanionExposurePolicy.VERSION)

  /** Opens only an explicitly named fresh slot, re-verifying the packaged pins. */
  @JvmStatic
  @Throws(IOException::class)
  fun open(context: Context, slotId: String): CompanionSlotStore {
    if (!slotId.matches(Regex("[0-9a-f]{32}"))) throw IOException("slot_identity_invalid")
    return CompanionSlotStore.open(context.applicationContext, slotId, CompanionExposurePolicy.VERSION)
  }
}
