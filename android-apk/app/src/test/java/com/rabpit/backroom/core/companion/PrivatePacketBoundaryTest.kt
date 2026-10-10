package com.rabpit.backroom.core.companion

import org.junit.Assert.*
import org.junit.Test

/** Pure packet fixtures. Native load/receipt qualification remains a separate gate. */
class PrivatePacketBoundaryTest {
  private fun memory(id:String,summary:String="summary")=MemoryRetrieval.MemoryView(id,"s","cao_minh","o","t",1,
    summary,"topic",EpisodicMemory.Salience.ORDINARY,null,"scene",mutableSetOf("cao_minh"))
  private fun input(memories:List<MemoryRetrieval.MemoryView>)=ActorContextBuilder.Input("s","cao_minh",
    CompanionPersonaFixture.load("cao_minh"),BrainContracts.BrainState("cao_minh",slotId="s"),memories,emptyList())
  @Test fun packetSnapshotsNestedCollectionsBeforeProvider() {
    val actors=mutableSetOf("cao_minh")
    val rows=mutableListOf(memory("m").copy(involvedActorIds=actors))
    val packet=ActorContextBuilder.build(input(rows))
    val before=ActorContextBuilder.canonical(packet)
    rows.clear();actors.add("WRITER_SECRET")
    assertEquals(before,ActorContextBuilder.canonical(packet))
    assertEquals(setOf("cao_minh"),packet.memories.single().involvedActorIds)
    try { (packet.memories as MutableList<MemoryRetrieval.MemoryView>).clear();fail("packet mutable") }
    catch(_:UnsupportedOperationException) { }
  }
  @Test fun completeDigestIncludesProvenanceAndCorrectionLink() {
    val base=ActorContextBuilder.build(input(listOf(memory("m"))))
    val changed=ActorContextBuilder.build(input(listOf(memory("m").copy(supersedesMemoryId="prior"))))
    assertNotEquals(ActorContextBuilder.digest(base),ActorContextBuilder.digest(changed))
    val json=org.json.JSONObject(ActorContextBuilder.canonical(base))
    assertFalse(json.getJSONArray("memories").getJSONObject(0).getBoolean("worldTruth"))
    assertEquals("STYLE_ONLY_NOT_EPISODIC_HISTORY",json.getJSONObject("canonRefs").getJSONObject("voice").getString("usage"))
  }
  @Test fun byteBudgetAccountsForUnicodeAndWholePacket() {
    val rows=(1..20).map { memory("m$it","界".repeat(280)) }
    val packet=ActorContextBuilder.build(input(rows).copy(maxPacketBytes=2048))
    assertTrue(packet.truncated)
    assertTrue(ActorContextBuilder.canonical(packet).toByteArray(Charsets.UTF_8).size<=2048)
    assertEquals(20,rows.size)
    assertEquals(ActorContextBuilder.canonical(packet),ActorContextBuilder.canonical(ActorContextBuilder.build(input(rows).copy(maxPacketBytes=2048))))
  }
  @Test fun duplicateMemoryAndControlCharacterFailClosed() {
    for(rows in listOf(listOf(memory("same"),memory("same")),listOf(memory("m","secret\u0000")))) {
      try { ActorContextBuilder.build(input(rows));fail("invalid packet accepted") }
      catch(_:IllegalArgumentException) { }
    }
  }
}
