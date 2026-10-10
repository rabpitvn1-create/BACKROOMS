package com.rabpit.backroom.core.companion

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CompanionPreviewSessionTest {
  @Test fun unavailableFoundationNeverCreatesFallback() {
    val session = CompanionPreviewSession(null)
    assertFalse(session.available)
    try { session.createFresh(); fail("Expected unavailable foundation") }
    catch (failure: IOException) { assertEquals("qualified_foundation_unavailable", failure.message) }
    assertNull(session.scope().slotId)
  }
  @Test fun closingScopeInvalidatesQueuedCallbackEvenWithoutOpenSlot() {
    val session = CompanionPreviewSession(null)
    val previous = session.scope()
    assertTrue(session.isCurrent(previous))
    session.closeSlot()
    assertFalse(session.isCurrent(previous))
    assertTrue(session.isCurrent(session.scope()))
    session.close()
    assertFalse(session.isCurrent(session.scope()))
  }
  @Test fun closedSessionCannotReload() {
    val session = CompanionPreviewSession(null)
    session.close()
    try { session.load("a".repeat(32)); fail("Expected closed session") }
    catch (failure: IOException) { assertEquals("session_closed", failure.message) }
  }
}
