from pathlib import Path

ROOT = Path(__file__).resolve().parent
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"

text = TEST.read_text(encoding="utf-8")


def replace_in_test(name: str, old: str, new: str) -> None:
    global text
    marker = f"  @Test fun {name}() {{"
    start = text.find(marker)
    if start < 0:
        raise RuntimeError(f"Party combat test compat: missing test {name}")
    next_test = text.find("\n  @Test fun ", start + len(marker))
    class_end = text.rfind("\n}")
    end = next_test if next_test >= 0 else class_end
    if end < 0:
        raise RuntimeError(f"Party combat test compat: could not bound test {name}")
    block = text[start:end]
    if new in block:
        return
    if old not in block:
        raise RuntimeError(f"Party combat test compat: missing expected action in {name}")
    block = block.replace(old, new, 1)
    text = text[:start] + block + text[end:]


# Silent Lullaby is an offensive automatic skill, therefore it is only eligible
# when the Party chooses ATTACK. Keep the original deterministic counter sweep.
replace_in_test(
    "silentLullabyStunSuppressesCurrentEnemyResponse",
    'CombatRuntime.resolve(state, "SEARCH", "theo dõi nhịp phản công")',
    'CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")',
)

# Quick Step is valid on ATTACK or EVADE. Exercise the new Party EVADE command so
# the test simultaneously proves the skill can remain defensive without leaking damage.
replace_in_test(
    "quickStepGrantsFiftyEvasionForThreeTurnsAndCountsDown",
    'CombatRuntime.resolve(state, "SEARCH", "đổi góc quan sát")',
    'CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")',
)

# The Party finalizer already rewrites the common GCO action string in most patch
# chains. Keep this compatibility explicit in case an older generated test survives.
replace_in_test(
    "guiltyCrownTurnKeepsPriorityOverAutomaticGunSkillRolls",
    'CombatRuntime.resolve(state, "SEARCH", "giữ mục tiêu trong tầm quan sát")',
    'CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")',
)

for marker in (
    'silentLullabyStunSuppressesCurrentEnemyResponse',
    'CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng tấn công")',
    'quickStepGrantsFiftyEvasionForThreeTurnsAndCountsDown',
    'CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")',
    'guiltyCrownTurnKeepsPriorityOverAutomaticGunSkillRolls',
):
    if marker not in text:
        raise RuntimeError("Party combat test compat contract missing: " + marker)

TEST.write_text(text, encoding="utf-8")
print("Party combat test compatibility applied: Kai offensive tests use ATTACK; Quick Step uses Party EVADE.")
