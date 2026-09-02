package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GmItemGainPolicyTest {
  @Test fun newCandidateItemBecomesOneGain() {
    val candidate = JSONArray().put(JSONObject().put("name", "Almond Water").put("quantity", 1))
    val gains = GmItemGainPolicy.positiveDeltas(emptyMap(), candidate)
    assertEquals(1, gains.size)
    assertEquals("almond-water", gains.single().itemId)
    assertEquals("Almond Water", gains.single().itemName)
    assertEquals(1, gains.single().quantity)
  }

  @Test fun existingStackUsesOnlyPositiveDifference() {
    val current = mapOf("bandage" to ItemStack("bandage", "Bandage", 2))
    val candidate = JSONArray().put(JSONObject().put("id", "bandage").put("name", "Bandage").put("quantity", 3))
    val gains = GmItemGainPolicy.positiveDeltas(current, candidate)
    assertEquals(1, gains.size)
    assertEquals(1, gains.single().quantity)
  }

  @Test fun idlessExistingItemStillMatchesByName() {
    val current = mapOf("custom:flash" to ItemStack("custom:flash", "Emergency Flare", 1))
    val candidate = JSONArray().put(JSONObject().put("name", "Emergency Flare").put("quantity", 2))
    val gains = GmItemGainPolicy.positiveDeltas(current, candidate)
    assertEquals("custom:flash", gains.single().itemId)
    assertEquals(1, gains.single().quantity)
  }

  @Test fun candidateRemovalNeverBecomesAuthoritativeGain() {
    val current = mapOf("bandage" to ItemStack("bandage", "Bandage", 2))
    val candidate = JSONArray().put(JSONObject().put("id", "bandage").put("name", "Bandage").put("quantity", 1))
    assertTrue(GmItemGainPolicy.positiveDeltas(current, candidate).isEmpty())
  }
}
