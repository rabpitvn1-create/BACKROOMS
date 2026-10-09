package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.GameStateCodec
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Native slot read/preflight. A returned Bound describes this read, not a commit entitlement. */
object CompanionWaitSlotBinding {
  @JvmStatic
  @Throws(IOException::class)
  fun verify(store: CompanionSlotStore, requestId: String, expectedRevision: Long,
             exactPlayerInput: String): CompanionWaitAuthorizer.Bound =
    store.inspectNative(requestId) { view -> verifyWithin(view, requestId, expectedRevision, exactPlayerInput) }

  // Capture must invoke this again inside reserveVerified's writer transaction.
  internal fun verifyWithin(view: CompanionSlotStore.NativeView, requestId: String,
                            expectedRevision: Long, input: String): CompanionWaitAuthorizer.Bound {
    if (view.revision != expectedRevision) throw IOException("revision_mismatch")
    val bytes = view.snapshot()
    val state = try {
      val text = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
      GameStateCodec.decode(text)
    } catch (error: Exception) {
      throw IOException("native_snapshot_invalid", error)
    }
    val gate = CompanionWaitAuthorizer.preflight(bytes, state, view.turn,
      view.slotId, requestId, view.revision, view.policyVersion, input)
    return gate.bound ?: throw IOException(gate.error ?: "wait_binding_rejected")
  }
}
