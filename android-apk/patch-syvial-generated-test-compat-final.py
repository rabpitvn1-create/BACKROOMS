from pathlib import Path

ROOT = Path(__file__).resolve().parent
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CompanionSkillCatalogTest.kt"
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"

text = TEST.read_text(encoding="utf-8")
combat = COMBAT.read_text(encoding="utf-8")
needle = "syvialDivineRebuke"
if needle in text:
    lines = text.splitlines()
    print("STALE_SYVIAL_SYMBOL_CONTEXT_BEGIN")
    for index, line in enumerate(lines):
        if needle not in line:
            continue
        start = max(0, index - 3)
        end = min(len(lines), index + 4)
        for cursor in range(start, end):
            print(f"{cursor + 1}: {lines[cursor]}")
    print("STALE_SYVIAL_SYMBOL_CONTEXT_END")
    print("Final CombatRuntime helper candidates:")
    for candidate in (
        "syvialHellscarRendEligible",
        "syvialBlacklineCleaveEligible",
        "syvialIronArcSeverEligible",
        "syvialRivetlineSeverEligible",
    ):
        print(f"{candidate}={candidate in combat}")
else:
    print("No stale syvialDivineRebuke reference in generated CompanionSkillCatalogTest.kt")
