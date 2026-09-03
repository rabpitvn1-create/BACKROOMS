package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityEncounterNarrativeAuthorityTest {
  @Test fun noEncounterLeavesNarrationUntouched() {
    val rolls = JSONObject()
      .put("entityEncounter", JSONObject().put("success", false))
      .toString()
    val reply = "Bạn lắng nghe tiếng đèn huỳnh quang rung nhẹ."

    assertEquals("", EntityEncounterNarrativeAuthority.selectedEntityKey(rolls))
    assertEquals(reply, EntityEncounterNarrativeAuthority.ensureReply(rolls, reply, ""))
  }

  @Test fun normalEncounterUsesAuthoritativeRoamingKey() {
    val rolls = JSONObject()
      .put("entityEncounter", JSONObject().put("success", true))
      .put("roamingEntityKey", "hound")
      .toString()

    assertEquals("hound", EntityEncounterNarrativeAuthority.selectedEntityKey(rolls))
    assertTrue(EntityEncounterNarrativeAuthority.visibleFact(rolls, "Hound").contains("canonicalKey=hound"))
  }

  @Test fun uniquePriorityWinsEvenIfMalformedDiceContainMultipleSuccesses() {
    val rolls = JSONObject()
      .put("diepMinhEncounter", JSONObject().put("success", true))
      .put("monsterXEncounter", JSONObject().put("success", true))
      .put("entityEncounter", JSONObject().put("success", true))
      .put("roamingEntityKey", "hound")
      .toString()

    assertEquals("diep_minh", EntityEncounterNarrativeAuthority.selectedEntityKey(rolls))
  }

  @Test fun violetWardenHasPriorityOverKaiDevilWithin() {
    val rolls = JSONObject()
      .put("violetWardenEncounter", JSONObject().put("success", true))
      .put("kaiDevilWithinEncounter", JSONObject().put("success", true))
      .toString()

    assertEquals("violet_warden", EntityEncounterNarrativeAuthority.selectedEntityKey(rolls))
  }

  @Test fun screenshotRegressionCannotSayNothingIsThereWhenHoundSpawned() {
    val rolls = JSONObject()
      .put("entityEncounter", JSONObject().put("success", true))
      .put("roamingEntityKey", "hound")
      .toString()
    val reply = "Bạn đợi. Một vài giây trôi qua. Vẫn không có gì."

    val guarded = EntityEncounterNarrativeAuthority.ensureReply(rolls, reply, "Hound")

    assertTrue(guarded, guarded.contains("Hound"))
    assertTrue(guarded, guarded.contains("mối đe dọa trực tiếp"))
    assertFalse(guarded, guarded.lowercase().contains("vẫn không có gì"))
  }

  @Test fun alreadyAcknowledgedEncounterIsNotRewritten() {
    val rolls = JSONObject()
      .put("entityEncounter", JSONObject().put("success", true))
      .put("roamingEntityKey", "hound")
      .toString()
    val reply = "Một Hound lao ra từ góc hành lang và chặn đường bạn."

    assertEquals(reply, EntityEncounterNarrativeAuthority.ensureReply(rolls, reply, "Hound"))
  }

  @Test fun omittedEncounterGetsGroundedCueWithoutDroppingExistingNarration() {
    val rolls = JSONObject()
      .put("entityEncounter", JSONObject().put("success", true))
      .put("roamingEntityKey", "smiler")
      .toString()
    val reply = "Tiếng đèn huỳnh quang tiếp tục rền trên đầu."

    val guarded = EntityEncounterNarrativeAuthority.ensureReply(rolls, reply, "Smiler")

    assertTrue(guarded, guarded.startsWith("Ngay lúc đó, Smiler xuất hiện"))
    assertTrue(guarded, guarded.contains(reply))
  }
}
