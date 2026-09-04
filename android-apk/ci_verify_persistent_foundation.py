from pathlib import Path

ROOT = Path(__file__).resolve().parent
APP = ROOT / "app/src/main/java/com/rabpit/backroom"
MAIN = APP / "MainActivity.java"
CORE = APP / "core/GameCoreFacade.kt"
FOUNDATION = APP / "core/foundation"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"Persistent Foundation verification failed: {message}")


main = MAIN.read_text(encoding="utf-8")
core = CORE.read_text(encoding="utf-8")

required_files = {
    "FoundationModels.kt",
    "FoundationCompiler.kt",
    "AndroidFoundationSourceCatalog.kt",
    "FoundationStore.kt",
    "FoundationScheduler.kt",
    "FoundationRuntime.kt",
}
require(required_files <= {path.name for path in FOUNDATION.glob("*.kt")}, "Foundation source set incomplete")
require("PERSISTENT_FOUNDATION_RUNTIME_R01" in main, "runtime integration marker missing")
require("fun foundationStateProjection()" in core, "Core-owned projection missing")
require('it.key != "flagsJson"' in core, "unbounded legacy flags leaked into Core projection")
require("FoundationRuntime.buildSlice" in main, "role-aware turn slice is not active")
require('"character_audit"' in main and '"canon_audit"' in main and '"repair"' in main, "role slices incomplete")
require("FoundationRuntime.releaseTurn" in main, "turn manifest pin is never released")
require("AiResponseSchemas.validate(role" in main, "provider-attempt semantic validation missing")
require("TurnBudget.start(75000L)" in main, "monotonic turn deadline missing")
require("providerTimeout(5000, 250)" in main, "connect timeout is not capped by turn deadline")
require("providerTimeout(30000, 500)" in main and "providerTimeout(22000, 500)" in main, "read timeouts are not capped")
require("futureTimeout(java.util.concurrent.TimeUnit.MILLISECONDS)" in main, "audit futures do not share the deadline")
require("canon.cancel(true)" in main and "character.cancel(true)" in main, "timed-out audit work is not cancelled")
require("private static final boolean GEMINI_RUNTIME_ENABLED = false;" in main, "Gemini lock policy changed unexpectedly")

foundation_text = "\n".join(path.read_text(encoding="utf-8") for path in FOUNDATION.glob("*.kt"))
require("SharedPreferencesSaveRepository" not in foundation_text, "Foundation must not access the game save repository")
require('File(appContext.filesDir, "foundation")' in foundation_text, "Foundation storage root is not isolated")
require("AtomicFile(target)" in foundation_text, "active pointer is not committed atomically")
require("FoundationSection.entries" in foundation_text, "six-section completeness is not enforced")
require('FoundationJobStatus.RUNNING' in foundation_text and 'leaseUntilEpochMs' in foundation_text, "durable job leases missing")
require('remoteEnrichmentEnabled", false' in foundation_text, "local-first policy is not explicit")

print("Persistent Foundation runtime contracts verified.")
