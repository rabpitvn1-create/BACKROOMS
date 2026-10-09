package com.rabpit.backroom.core.companion

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Same SHA-256 bytes/envelopes; fixed lowercase hex without per-byte formatting. */
internal object CompanionDigests {
  private const val HEX = "0123456789abcdef"
  fun sha256(value: String): String = sha256(value.toByteArray(StandardCharsets.UTF_8))
  fun sha256(bytes: ByteArray): String {
    val digest=MessageDigest.getInstance("SHA-256").digest(bytes)
    val hex=CharArray(digest.size*2)
    digest.forEachIndexed { i, byte ->
      val unsigned=byte.toInt() and 255
      hex[i*2]=HEX[unsigned ushr 4]; hex[i*2+1]=HEX[unsigned and 15]
    }
    return String(hex)
  }
}
