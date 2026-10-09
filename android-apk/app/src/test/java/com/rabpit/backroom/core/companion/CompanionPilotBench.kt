package com.rabpit.backroom.core.companion

import com.rabpit.backroom.core.companion.ActorContextBuilder.Packet
import com.rabpit.backroom.core.companion.CharacterDecisionOrchestrator.DecisionLedger
import com.rabpit.backroom.core.companion.CharacterDecisionOrchestrator.DecisionProvider
import com.rabpit.backroom.core.companion.CharacterDecisionOrchestrator.Decided
import com.rabpit.backroom.core.companion.CharacterDecisionOrchestrator.Input
import com.rabpit.backroom.core.companion.CharacterDecisionOrchestrator.Outcome
import com.rabpit.backroom.core.companion.DecisionPreflight.Intent
import com.rabpit.backroom.core.companion.DecisionPreflight.NativeScope
import com.rabpit.backroom.core.companion.DecisionPreflight.Proposal

/**
 * S2.5 routine pilot bench (issue #517).
 *
 * Measures the pure-Kotlin companion decision pipeline end-to-end over 300
 * routine iterations (#497 baseline):
 *   preflight → orchestration (fake provider) → TALK execution → belief reduce
 *
 * This is a JVM pilot, NOT a device measurement and NOT turn10k proof. Same-
 * environment, same-workload numbers for the frozen budget record only.
 * Storage I/O (SQLite writes/rows per turn, WAL) is measured separately by the
 * sqlite3 pilot harness.
 */
object CompanionPilotBench {
  private fun packet() = Packet(
    actorId = "luc_tram",
    canonRefs = ActorContextBuilder.CanonRefs(listOf("CAO-PER-01"), listOf("CAO-LIFE-02"), listOf()),
    brain = ActorContextBuilder.BrainView(emptyList(), emptyList(), "UNSET"),
    memories = emptyList(), sceneEvidence = emptyList(),
    pins = ActorContextBuilder.Pins("R17", "deadbeef",
      BrainContracts.RULE_VERSION, CompanionExposurePolicy.VERSION),
    truncated = false)

  private fun scope() = NativeScope(
    slotId = "slot-1", slotRevision = 42, actorId = "luc_tram", sceneId = "node-7",
    presentActorIds = setOf("luc_tram", "cao_minh"),
    capabilities = setOf("cap.talk", "cap.wait"),
    inventoryItemIds = emptySet(), legalTargetIds = setOf("cao_minh"),
    canonRevision = "R17", ruleVersion = BrainContracts.RULE_VERSION)

  private fun proposal() = Proposal(
    intent = Intent.TALK, targetId = "cao_minh", itemId = null,
    slotId = "slot-1", slotRevision = 42, actorId = "luc_tram",
    canonRevision = "R17", ruleVersion = BrainContracts.RULE_VERSION)

  @JvmStatic
  fun main(args: Array<String>) {
    val iterations = args.firstOrNull()?.toIntOrNull() ?: 300
    val provider = object : DecisionProvider {
      override fun propose(packet: Packet, binding: DecisionPreflight.DecisionBinding) =
        DecisionProvider.CallResult("""{"intent":"TALK","targetId":"cao_minh"}""", null)
    }
    val ledgers = mutableListOf<DecisionLedger>()
    var rngState = 12345L
    val rng = CharacterDecisionOrchestrator.RngSource { rngState++ }

    val tPreflight = LongArray(iterations)
    val tOrchestrate = LongArray(iterations)
    val tTalk = LongArray(iterations)
    val tBelief = LongArray(iterations)

    var brain = BrainContracts.BrainState(actorId = "cao_minh")
    var decided = 0
    var spoken = 0

    repeat(iterations) { i ->
      val ledger = object : DecisionLedger {
        private val map = mutableMapOf<String, Decided>()
        override fun get(digest: String) = map[digest]
        override fun put(d: Decided) = map.putIfAbsent(d.binding.proposalDigest, d) == null
      }
      ledgers.add(ledger)
      val t0 = System.nanoTime()
      val preflight = DecisionPreflight.preflight(proposal(), scope())
      tPreflight[i] = System.nanoTime() - t0
      check(preflight is DecisionPreflight.Result.Approved)

      val t1 = System.nanoTime()
      val out = CharacterDecisionOrchestrator.decide(Input(
        packet(), scope(), proposal(), provider, rng, ledger))
      tOrchestrate[i] = System.nanoTime() - t1
      check(out is Outcome.DecidedOutcome)
      decided++
      val d = out.decided

      val t2 = System.nanoTime()
      val talk = TalkExecutor.execute(TalkExecutor.TalkInput(
        d, "Đi theo tôi.", TalkExecutor.TalkNativeFacts(
          "luc_tram", "cao_minh", "node-7",
          speakerConscious = true, speakerAudible = true,
          listenerPresent = true, listenerConscious = true,
          sameScene = true, inReach = true),
        "turn-$i", "obs-$i", i.toLong()))
      tTalk[i] = System.nanoTime() - t2
      check(talk is TalkExecutor.TalkResult.Spoken)
      spoken++
      val bundle = talk.bundle

      val t3 = System.nanoTime()
      val claim = BrainContracts.Claim(
        "claim-$i", "node-8", BrainContracts.Predicates.AT_LOCATION, "safe",
        BrainContracts.Claim.Polarity.POSITIVE, "luc_tram", listOf(bundle.told.observationId))
      val res = BeliefReducer.reduceTold(
        BeliefReducer.ToldInput(brain, claim, bundle.told.observationId, "cao_minh"))
      tBelief[i] = System.nanoTime() - t3
      brain = res.state
    }

    fun stats(a: LongArray): String {
      val ms = a.map { it / 1_000_000.0 }.sorted()
      val avg = ms.average()
      val p50 = ms[ms.size / 2]
      val p95 = ms[(ms.size * 0.95).toInt().coerceAtMost(ms.size - 1)]
      val max = ms.last()
      return "avg=%.3f p50=%.3f p95=%.3f max=%.3f".format(avg, p50, p95, max)
    }
    println("PILOT iterations=$iterations decided=$decided spoken=$spoken beliefs=${brain.beliefs.size}")
    println("preflight_ms " + stats(tPreflight))
    println("orchestrate_ms " + stats(tOrchestrate))
    println("talk_ms " + stats(tTalk))
    println("belief_ms " + stats(tBelief))
    val total = tPreflight.sum() + tOrchestrate.sum() + tTalk.sum() + tBelief.sum()
    println("pipeline_total_ms=%.3f per_iter_ms=%.3f".format(
      total / 1_000_000.0, total / 1_000_000.0 / iterations))
  }
}
