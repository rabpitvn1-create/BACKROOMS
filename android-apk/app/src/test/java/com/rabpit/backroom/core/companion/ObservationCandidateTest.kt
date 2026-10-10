package com.rabpit.backroom.core.companion

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ObservationCandidateTest {
  private fun candidate(p: JSONObject) = ObservationCandidate("o","cao_minh","e",
    ObservationCandidate.AccessKind.SEEN,ObservationCandidate.Certainty.PLAUSIBLE,
    "a".repeat(32),"t",1,"scene",CompanionExposurePolicy.VERSION,p)

  @Test fun sourceAndReadbackCannotMutateCandidate() {
    val payload=JSONObject().put("location","room")
    val c=candidate(payload); val digest=ObservationPublisherDigest.of(c)
    payload.put("location","secret")
    c.publicPayload.put("location","changed")
    assertEquals("room",c.publicPayload.getString("location"))
    assertEquals(digest,ObservationPublisherDigest.of(c))
  }

  @Test fun digestBindsPublicPayload() {
    assertNotEquals(ObservationPublisherDigest.of(candidate(JSONObject().put("location","room"))),
      ObservationPublisherDigest.of(candidate(JSONObject().put("location","other"))))
  }

  @Test fun digestDoesNotDependOnJsonInsertionOrder() {
    assertEquals(ObservationPublisherDigest.of(candidate(JSONObject().put("actor","cao_minh").put("minutes",30))),
      ObservationPublisherDigest.of(candidate(JSONObject().put("minutes",30).put("actor","cao_minh"))))
  }
}
