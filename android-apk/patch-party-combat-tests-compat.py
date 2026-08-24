from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"
text = TEST.read_text(encoding="utf-8")


def replace_function(source: str, name: str, replacement: str) -> str:
    marker = f"  @Test fun {name}() {{"
    start = source.find(marker)
    if start < 0:
        raise RuntimeError(f"Missing generated regression: {name}")
    next_test = source.find("\n  @Test fun ", start + len(marker))
    class_end = source.rfind("\n}")
    end = next_test if next_test >= 0 else class_end
    if end < 0:
        raise RuntimeError(f"Could not bound generated regression: {name}")
    return source[:start] + replacement.rstrip() + "\n" + source[end:]

# Silent Lullaby is offensive, so exercise it only through the Party ATTACK command.
# Keep the original behavioral contract: its proc must suppress the current Entity response.
silent = r'''  @Test fun silentLullabyStunSuppressesCurrentEnemyResponse() {
    var verified = false
    for (counter in 0..240) {
      if (verified) break
      var state = CombatRuntime.start(GameState.initial(), "diep_minh")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")
      if (!result.reply.contains("Silent Lullaby tự động kích hoạt")) continue
      assertTrue(result.reply, result.reply.contains("bị Stun và mất lượt phản ứng hiện tại"))
      assertFalse(result.reply, result.reply.contains("Diệp Minh phản công:"))
      assertFalse(result.reply, result.reply.contains("Devils And Gold kích hoạt"))
      verified = true
    }
    assertTrue("Expected an ATTACK turn where Silent Lullaby activates", verified)
  }
'''
text = replace_function(text, "silentLullabyStunSuppressesCurrentEnemyResponse", silent)

# Quick Step is legal on ATTACK or EVADE. Test the explicit Party EVADE path so the
# defensive button contract is covered without allowing offensive skills to leak.
quick = r'''  @Test fun quickStepGrantsFiftyEvasionForThreeTurnsAndCountsDown() {
    var verified = false
    for (counter in 0..240) {
      if (verified) break
      var state = CombatRuntime.start(GameState.initial(), "diep_minh")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
      if (!result.reply.contains("Quick Step tự động kích hoạt")) continue
      assertTrue(result.reply, result.reply.contains("+50% Evasion trong 3 turn"))
      assertEquals("2", result.state.metadata["combat.kaiQuickStepTurns"])
      verified = true
    }
    assertTrue("Expected a Party EVADE turn where Quick Step activates", verified)
  }
'''
text = replace_function(text, "quickStepGrantsFiftyEvasionForThreeTurnsAndCountsDown", quick)

TEST.write_text(text, encoding="utf-8")
print("Party combat regression compatibility applied: Silent Lullaby uses Party ATTACK; Quick Step uses Party EVADE.")

# Final narrative layer: the player inhabits Kai directly, so the GM must narrate from
# Kai's second-person limited perspective after every earlier prompt transform has completed.
runpy.run_path(str(ROOT / "patch-kai-immersive-pov-final.py"), run_name="__main__")
