package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class GeminiIntentInterpreterTest {
  @Test fun schemaParserRejectsNarrativeInsteadOfTreatingItAsState() {
    val interpreter = GeminiIntentInterpreter(StructuredIntentClient { "" })
    val parsed = interpreter.strictObject("""{"intent":"PICKUP_ITEM","confidence":0.94,"isRequestedAction":true}""")
    assertEquals("PICKUP_ITEM", parsed.getString("intent"))
    assertThrows(IllegalArgumentException::class.java) { interpreter.strictObject("Kai nhặt chai nước") }
    assertThrows(IllegalArgumentException::class.java) { interpreter.strictObject("""{"reply":"đã nhặt","inventory":[]}""") }
  }
}
