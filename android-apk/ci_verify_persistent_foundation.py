from pathlib import Path

ROOT = Path(__file__).resolve().parent
APP = ROOT / "app/src/main/java/com/rabpit/backroom"
MAIN = APP / "MainActivity.java"
CORE = APP / "core/GameCoreFacade.kt"
FOUNDATION = APP / "core/foundation"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"Persistent Foundation verification failed: {message}")


def method_block(source: str, signature: str) -> str:
    start = source.find(signature)
    require(start >= 0, f"missing method: {signature}")
    brace = source.find("{", start)
    require(brace >= 0, f"missing opening brace: {signature}")
    depth = 0
    for index in range(brace, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    raise SystemExit(f"Persistent Foundation verification failed: missing closing brace: {signature}")


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
require("GEMINI_FOUNDATION_AUTHORITY_R02" in main, "Gemini/Foundation final authority missing")
require("fun foundationStateProjection()" in core, "Core-owned projection missing")
require('it.key != "flagsJson"' in core, "unbounded legacy flags leaked into Core projection")
require("FoundationRuntime.buildSlice" in main, "role-aware turn slice is not active")
require('"character_audit"' in main and '"canon_audit"' in main and '"repair"' in main, "role slices incomplete")
require("FoundationRuntime.releaseTurn" in main, "turn manifest pin is never released")
require("AiResponseSchemas.validate(role" in main, "provider-attempt semantic validation missing")
require("TurnBudget.start(75000L)" in main, "monotonic turn deadline missing")
require("providerTimeout(4000, 250)" in main and "providerTimeout(8000, 500)" in main, "Gemini timeout is not capped")
require("providerTimeout(5000, 250)" in main, "fallback connect timeout is not capped")
require("providerTimeout(30000, 500)" in main and "providerTimeout(22000, 500)" in main, "fallback read timeouts are not capped")
require("futureTimeout(java.util.concurrent.TimeUnit.MILLISECONDS)" in main, "audit futures do not share the deadline")
require("canon.cancel(true)" in main and "character.cancel(true)" in main, "timed-out audit work is not cancelled")
require("private static final boolean GEMINI_RUNTIME_ENABLED = true;" in main, "Gemini is not enabled in final runtime")
require("private static final boolean GEMINI_RUNTIME_ENABLED = false;" not in main, "retired Gemini lock survived")
require("Gemini runtime intentionally locked." not in main, "retired Gemini guard survived")
require('emit("backroomFoundation", "active role=" + role + " slice=v1")' in main, "Foundation active telemetry missing")
require('emit("backroomFoundation", "legacy-fallback role=" + role)' in main, "Foundation fallback telemetry missing")

structured = method_block(
    main,
    "  private String generateStructuredText(String prompt, AiResponseSchemas.Role role, TurnBudget budget) throws Exception ",
)
order = [
    structured.find("this::geminiFoundationText"),
    structured.find("this::hakuFallbackText"),
    structured.find("this::lunaText"),
]
require(all(value >= 0 for value in order), "Foundation provider chain incomplete")
require(order == sorted(order), "Foundation provider priority must be GEMINI -> HAKU -> LUNA")

foundation_text = "\n".join(path.read_text(encoding="utf-8") for path in FOUNDATION.glob("*.kt"))
require("SharedPreferencesSaveRepository" not in foundation_text, "Foundation must not access the game save repository")
require('File(appContext.filesDir, "foundation")' in foundation_text, "Foundation storage root is not isolated")
require("AtomicFile(target)" in foundation_text, "active pointer is not committed atomically")
require("FoundationSection.entries" in foundation_text, "six-section completeness is not enforced")
require('FoundationJobStatus.RUNNING' in foundation_text and 'leaseUntilEpochMs' in foundation_text, "durable job leases missing")
require('remoteEnrichmentEnabled", false' in foundation_text, "local-first build policy is not explicit")

print("Persistent Foundation runtime contracts verified with Gemini K1-K6 high-priority provider authority.")
