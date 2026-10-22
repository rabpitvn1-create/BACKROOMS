package com.rabpit.backroom.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Wrong, premature and forged deductions must not open world gates. */
class Level0InferenceGuardTest {
  private fun fresh(seed: Long = 101L) =
    WorldGraphRuntime.initialize(CoreClientProjection.initialize(GameState.initial()), seed)
  private fun p(s: GameState) = MiniMapRuntime.progress(s)
  private fun actions(s: GameState) = Level0RiddleTestSupport.choices(s)

  @Test fun allCluesWithoutComparisonCannotPresentAnInferenceChoice() {
    val law = Level0WorldDefinition.evidenceLaws.first()
    var s = fresh()
    for (probe in law.probes) {
      s = Level0RiddleTestSupport.visit(s, probe.environmentId)
      s = Level0RiddleTestSupport.take(s, "probe:${probe.method}")
    }
    s = Level0RiddleTestSupport.visit(s, law.verifyEnvironmentId)
    assertFalse(actions(s).any { it.getString("targetId").startsWith("deduce:") })
    s = Level0RiddleTestSupport.take(s, "verify")
    assertFalse(p(s).getJSONObject("ruleFlags").optBoolean(law.id))

    s = Level0RiddleTestSupport.prepareLaw(s, law)
    assertTrue(actions(s).any { it.getString("targetId").startsWith("deduce:") })
    val second = law.inferenceSteps[1]
    assertFalse(actions(s).any { it.getString("targetId").startsWith("deduce:${second.id}:") })
  }

  @Test fun wrongHypothesisCostsAnActionWithoutGrantingProof() {
    val law = Level0WorldDefinition.evidenceLaws.first()
    var s = Level0RiddleTestSupport.prepareLaw(fresh(), law)
    val first = law.inferenceSteps.first()
    val wrong = first.options.first { it.id != first.answerId }
    s = Level0RiddleTestSupport.take(s, "deduce:${first.id}:${wrong.id}")
    val now = p(s)
    assertFalse(now.getJSONObject("inferenceDecisions").has(first.id))
    assertFalse(now.getJSONObject("ruleFlags").optBoolean(law.id))
    assertTrue(now.getJSONObject("environmentChanges")
      .getJSONObject(now.getString("roomId")).getInt("failedTests") >= 1)
    WorldGraphRuntime.validateSavedProgress(now)
    val restored = GameStateCodec.decode(GameStateCodec.encode(s))
    assertEquals(now.toString(), p(restored).toString())
    assertTrue(actions(restored).any { it.getString("targetId") == "deduce:${first.id}:${first.answerId}" })
  }

  @Test fun prerequisiteLawAndTwoPhysicalAnchorsAreMandatory() {
    val laws = Level0WorldDefinition.evidenceLaws
    val signal = laws.first { it.id == "signal-distortion" }
    var s = Level0RiddleTestSupport.prepareLaw(fresh(), signal)
    assertFalse(actions(s).any { it.getString("targetId").startsWith("deduce:signal-") })
    assertFalse(p(s).getJSONObject("ruleFlags").optBoolean(signal.id))

    val loop = laws.first { it.id == "loop-reflection" }
    var anchorState = fresh(55L)
    for (probe in loop.probes) {
      anchorState = Level0RiddleTestSupport.visit(anchorState, probe.environmentId)
      anchorState = Level0RiddleTestSupport.take(anchorState, "probe:${probe.method}")
    }
    val pair = loop.comparisonPairs.first()
    anchorState = Level0RiddleTestSupport.visit(anchorState, pair.first)
    anchorState = Level0RiddleTestSupport.take(anchorState, "study")
    val firstRoom = p(anchorState).getString("roomId")
    anchorState = Level0RiddleTestSupport.visit(anchorState, pair.second)
    anchorState = Level0RiddleTestSupport.take(anchorState, "study")
    anchorState = Level0RiddleTestSupport.take(anchorState, "compare:$firstRoom")
    anchorState = Level0RiddleTestSupport.visit(anchorState, loop.verifyEnvironmentId)
    assertFalse(actions(anchorState).any { it.getString("targetId").startsWith("deduce:loop-") })
  }

  @Test fun saveRejectsClaimedCorrectDecisionAndVerifiedRuleWithoutActualInvestigation() {
    val base = p(fresh())
    val step = Level0WorldDefinition.evidenceLaws.first().inferenceSteps.first()
    val forgedDecision = JSONObject(base.toString())
    forgedDecision.getJSONObject("inferenceDecisions").put(step.id, step.answerId)
    assertEquals("world_saved_inference", assertThrows(IllegalArgumentException::class.java) {
      WorldGraphRuntime.validateSavedProgress(forgedDecision)
    }.message)

    val forgedLaw = JSONObject(base.toString())
    forgedLaw.getJSONObject("ruleFlags").put("wet-ceiling", true)
    assertEquals("world_law_unverified", assertThrows(IllegalArgumentException::class.java) {
      WorldGraphRuntime.validateSavedProgress(forgedLaw)
    }.message)
    assertFalse(WorldGraphRuntime.exitUnlocked(base))
  }
}
