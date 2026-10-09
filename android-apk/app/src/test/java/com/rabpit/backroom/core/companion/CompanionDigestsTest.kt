package com.rabpit.backroom.core.companion

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class CompanionDigestsTest {
  private fun original(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
    .joinToString("") { "%02x".format(it.toInt() and 255) }
  @Test fun publishedSha256VectorsAndFixedWidth() {
    assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",CompanionDigests.sha256(""))
    assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",CompanionDigests.sha256("abc"))
  }
  @Test fun utf8EnvelopesPreserveExactBytesWithoutNormalization() {
    for(text in listOf("Cao Minh — Lục Trầm","é","e\u0301","a\u0000b","💬"))
      assertEquals(original(text.toByteArray(StandardCharsets.UTF_8)),CompanionDigests.sha256(text))
    assertNotEquals(CompanionDigests.sha256("é"),CompanionDigests.sha256("e\u0301"))
  }
  @Test fun allUnsignedBytesMatchPriorFormattingAndNeverMutateInput() {
    for(prefix in 0..256) {
      val bytes=ByteArray(prefix) { it.toByte() }; val originalBytes=bytes.copyOf()
      val hash=CompanionDigests.sha256(bytes)
      assertEquals(original(bytes),hash); assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
      assertArrayEquals(originalBytes,bytes)
    }
  }
}
