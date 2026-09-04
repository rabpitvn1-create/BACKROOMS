from pathlib import Path

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# BIOLOGICAL_PIPELINE_ROUTE_CONSTRICTION_V1
#
# Active Entity skill. Exactly one proc roll is evaluated only during Biological
# Pipeline's own Entity-response turn. It cannot trigger from taking damage, HP
# thresholds, Party movement, player attacks, or any other out-of-turn event.
#
# Canon fit: Biological Pipeline senses heat/vibration, opens apparently safe
# crawlspaces, lets prey move deeper, then changes direction and locks retreat.
# Route Constriction turns that route-control behavior into bounded combat pressure
# without inventing mobility, ranged damage, or hard crowd control.
#
# Balance: 21% per Biological Pipeline Entity turn. No direct damage, no Stun, no
# Cover loss and no persistent stack. On proc it removes only 6 Escape progress and
# one Opening. EVADE fully counters the constriction by abandoning the closing route.
+combat = COMBAT.read_text(encoding="utf-8")
+
+constant_anchor = '  private const val PARTY_TURN_SKILL_CONTEXT_KEY = "partyCombat.skillContext"\n'
+constant_block = '''  internal const val BIOLOGICAL_PIPELINE_ROUTE_CONSTRICTION_PROC_PERCENT = 21
+  private const val BIOLOGICAL_PIPELINE_ROUTE_CONSTRICTION_ESCAPE_LOSS = 6
+'''
+if 'BIOLOGICAL_PIPELINE_ROUTE_CONSTRICTION_PROC_PERCENT = 21' not in combat:
+    combat = replace_once(
+        combat,
+        constant_anchor,
+        constant_anchor + constant_block,
+        "Biological Pipeline Route Constriction constants",
+    )
+
+response_anchor = '      log += "ENTITY ACTION BUDGET: ${c.entityName} = ${entityDirectTargets.size}; one direct action per completed Party actor turn."\n'
+response_block = response_anchor + '''
+      // BIOLOGICAL_PIPELINE_ROUTE_CONSTRICTION_V1: one proc check on Biological Pipeline's Entity response turn only.
+      if (c.entityKey == "biological_pipeline" &&
+          roll(c.copy(eventCounter = c.eventCounter + 2311), 100) < BIOLOGICAL_PIPELINE_ROUTE_CONSTRICTION_PROC_PERCENT) {
+        if (intent == Intent.EVADE) {
+          log += "Route Constriction: proc ${BIOLOGICAL_PIPELINE_ROUTE_CONSTRICTION_PROC_PERCENT}% nhưng Party bỏ tuyến đang khép và đổi góc né; kỹ năng bị vô hiệu."
+        } else {
+          val escapeBefore = c.escapeProgress
+          val openingBefore = c.opening
+          c = c.copy(
+            escapeProgress = max(0, escapeBefore - BIOLOGICAL_PIPELINE_ROUTE_CONSTRICTION_ESCAPE_LOSS),
+            opening = max(0, openingBefore - 1)
+          )
+          log += "Route Constriction: Biological Pipeline đổi hướng đường ống quanh lối rút; proc ${BIOLOGICAL_PIPELINE_ROUTE_CONSTRICTION_PROC_PERCENT}%, Escape ${escapeBefore} -> ${c.escapeProgress}, Opening ${openingBefore} -> ${c.opening}."
+        }
+      }
+'''
+if 'BIOLOGICAL_PIPELINE_ROUTE_CONSTRICTION_V1' not in combat:
+    combat = replace_once(
+        combat,
+        response_anchor,
+        response_block,
+        "Biological Pipeline Route Constriction Entity-turn proc",
+    )
+
+for marker in (
+    'BIOLOGICAL_PIPELINE_ROUTE_CONSTRICTION_V1',
+    'BIOLOGICAL_PIPELINE_ROUTE_CONSTRICTION_PROC_PERCENT = 21',
+    'BIOLOGICAL_PIPELINE_ROUTE_CONSTRICTION_ESCAPE_LOSS = 6',
+    'c.entityKey == "biological_pipeline"',
+    'intent == Intent.EVADE',
+    'escapeProgress = max(0, escapeBefore - BIOLOGICAL_PIPELINE_ROUTE_CONSTRICTION_ESCAPE_LOSS)',
+    'opening = max(0, openingBefore - 1)',
+):
+    if marker not in combat:
+        raise RuntimeError("Biological Pipeline Route Constriction runtime contract missing: " + marker)
+
+COMBAT.write_text(combat, encoding="utf-8")
+
+
+test = TEST.read_text(encoding="utf-8")
+if 'biologicalPipelineRouteConstrictionIsEntityTurnOnlyPressureWithEvadeCounterplay' not in test:
+    tests = r'''
+  @Test fun biologicalPipelineRouteConstrictionIsEntityTurnOnlyPressureWithEvadeCounterplay() {
+    assertEquals(21, CombatRuntime.BIOLOGICAL_PIPELINE_ROUTE_CONSTRICTION_PROC_PERCENT)
+    var pressureResult: CombatRuntime.Resolution? = null
+
+    for (counter in 0..300) {
+      var state = CombatRuntime.start(GameState.initial(), "biological_pipeline")
+      state = state.copy(metadata = state.metadata + mapOf(
+        "combat.eventCounter" to counter.toString(),
+        "combat.escapeProgress" to "40",
+        "combat.opening" to "2"
+      ))
+      val result = CombatRuntime.resolve(state, "OTHER", "giữ tuyến và quan sát đường ống")
+      if (result.reply.contains("Route Constriction:")) {
+        pressureResult = result
+        break
+      }
+    }
+
+    assertNotNull("21% Biological Pipeline proc must be reachable across deterministic Entity turns", pressureResult)
+    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("kỹ năng bị vô hiệu"))
+    assertFalse(pressureResult!!.reply, pressureResult!!.reply.contains("Stun"))
+    assertTrue(pressureResult!!.reply, pressureResult!!.reply.contains("Escape 40 -> 34"))
+    assertTrue(pressureResult!!.reply, pressureResult!!.reply.contains("Opening 2 -> 1"))
+    val pressured = CombatRuntime.active(pressureResult!!.state)!!
+    assertEquals(34, pressured.escapeProgress)
+    assertEquals(1, pressured.opening)
+
+    var evadeResult: CombatRuntime.Resolution? = null
+    for (counter in 0..300) {
+      var state = CombatRuntime.start(GameState.initial(), "biological_pipeline")
+      state = state.copy(metadata = state.metadata + mapOf(
+        "combat.eventCounter" to counter.toString(),
+        "combat.escapeProgress" to "40",
+        "combat.opening" to "2"
+      ))
+      val result = CombatRuntime.resolve(state, "EVADE", "né khỏi tuyến đang khép và đổi góc")
+      if (result.reply.contains("Route Constriction:")) {
+        evadeResult = result
+        break
+      }
+    }
+
+    assertNotNull("Biological Pipeline proc must also be reachable on an EVADE Entity-response turn", evadeResult)
+    assertTrue(evadeResult!!.reply, evadeResult!!.reply.contains("kỹ năng bị vô hiệu"))
+    assertFalse(evadeResult!!.reply, evadeResult!!.reply.contains("Escape 40 -> 34"))
+    assertFalse(evadeResult!!.reply, evadeResult!!.reply.contains("Stun"))
+    assertTrue(CombatRuntime.active(evadeResult!!.state)!!.escapeProgress >= 40)
+  }
+'''
+    close = test.rfind("}\n")
+    if close < 0:
+        raise RuntimeError("CombatRuntimeTest class closing brace not found")
+    test = test[:close] + tests + test[close:]
+
+TEST.write_text(test, encoding="utf-8")
+print("Hourly Entity skill applied: Biological Pipeline Route Constriction (ACTIVE, 21% on Biological Pipeline Entity turn, EVADE counterplay).")
