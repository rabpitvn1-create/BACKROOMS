package com.rabpit.backroom.core.companion

import android.content.Context
import com.rabpit.backroom.core.CharacterPresence
import com.rabpit.backroom.core.Combat93Runtime
import com.rabpit.backroom.core.CombatRuntime
import com.rabpit.backroom.core.GameState
import com.rabpit.backroom.core.GameStateCodec
import com.rabpit.backroom.core.KAI_ID
import com.rabpit.backroom.core.progression.FeaturedJourneyRoutes
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * A private, verified native decision context, never assembled from WebView
 * JSON or GM text. The provider receives a bounded actor-owned context packet.
 *
 * A snapshot digest identifies the exact native slot revision; retries must
 * re-read the slot and reject changed state before deciding or rolling.
 */
internal object CompanionNativeDecisionContext {
  data class Bound(
    val snapshot: ByteArray,
    val revision: Long,
    val packet: ActorContextBuilder.Packet,
    val scope: DecisionPreflight.NativeScope,
    val interaction: DecisionPreflight.InteractionIdentity
  )

  @Throws(IOException::class)
  fun bind(context: Context, store: CompanionSlotStore, requestId: String,
           input: String): Bound {
    if (input.length > InteractUiContract.MAX_INPUT_CHARS ||
        !com.rabpit.backroom.core.ExitStreakEngine.hasMinimumInput(input))
      throw IOException("interaction_input_invalid")
    val bytes = store.currentSnapshot()
    val revision = store.currentRevision()
    val state = try {
      val encoded = String(bytes, StandardCharsets.UTF_8)
      GameStateCodec.decode(encoded).also {
        if (GameStateCodec.encode(it) != encoded) throw IOException("snapshot_not_stable")
      }
    } catch (error: Exception) { throw IOException("snapshot_invalid", error) }
    if (KAI_ID != "cao_minh" ||
        state.characters[KAI_ID]?.presence != CharacterPresence.ACTIVE ||
        KAI_ID !in state.party.memberIds ||
        state.party.leaderId != KAI_ID)
      throw IOException("cao_minh_actor_not_available")
    if (state.turn.pending != null || Combat93Runtime.active(state) ||
        CombatRuntime.active(state) != null)
      throw IOException("companion_decision_scene_busy")
    val stop = state.world["journeyStopKey"] ?: throw IOException("scene_route_missing")
    val node = FeaturedJourneyRoutes.nodeIdAt(stop) ?: throw IOException("scene_route_unknown")
    if (state.world["worldNodeId"] != node ||
        !state.world.containsKey("levelJson"))
      throw IOException("scene_route_snapshot_mismatch")
    val persona = CompanionCanonPersonaRegistry.load(KAI_ID) { path ->
      context.assets.open(path).use { it.readBytes() }
    }
    // Current committed WAIT receipts have zero promoted memories. Never treat
    // GM prose or another actor's log as an actor-owned memory.
    val history = store.memoryHistory(KAI_ID)
    val packet = ActorContextBuilder.build(ActorContextBuilder.Input(
      slotId = store.slotId, actorId = KAI_ID,
      persona = persona,
      brain = BrainContracts.BrainState(actorId = KAI_ID, slotId = store.slotId),
      memories = history,
      sceneEvidence = emptyList()))
    val capabilities = linkedSetOf("cap.talk", "cap.search", "cap.wait", "cap.move")
    val legal = linkedSetOf("player_companion", stop)
    FeaturedJourneyRoutes.next(stop)?.let { legal.add(it.targetStopKey) }
    val scope = DecisionPreflight.NativeScope(
      slotId = store.slotId,
      slotRevision = revision,
      actorId = KAI_ID,
      sceneId = stop,
      presentActorIds = setOf(KAI_ID),
      capabilities = capabilities,
      inventoryItemIds = state.inventories[KAI_ID]?.items?.keys.orEmpty(),
      legalTargetIds = legal,
      canonRevision = persona.sourceRevision,
      ruleVersion = BrainContracts.RULE_VERSION
    )
    val digest = CompanionPendingTurn.Request.fromPlayerInput(
      store.slotId, requestId, revision, KAI_ID, input).inputDigest
    val identity = DecisionPreflight.InteractionIdentity(
      turnId = "companion-$requestId",
      inputDigest = digest,
      contextDigest = ActorContextBuilder.digest(packet),
      sourceSnapshotDigest = CompanionDigests.sha256(bytes))
    if (!identity.valid()) throw IOException("native_interaction_identity_invalid")
    return Bound(bytes,revision,packet,scope,identity)
  }

  fun exactSameSnapshot(store: CompanionSlotStore, bound: Bound): Boolean =
    try {
      store.currentRevision() == bound.revision &&
        store.currentSnapshot().contentEquals(bound.snapshot)
    } catch (_: Exception) { false }
}
