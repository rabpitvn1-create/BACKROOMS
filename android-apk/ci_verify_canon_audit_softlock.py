from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
java = MAIN.read_text(encoding="utf-8")

required = [
    "AUDIT_BLOCKING_POLICY_R01",
    "VALIDATED CANDIDATE STATE",
    "PROPOSED OPS (UNTRUSTED REFERENCE ONLY)",
    "private boolean auditRuleCanBlock(String rule)",
    "private JSONArray blockingAuditIssues(JSONArray hardIssues)",
    'emit("backroomAudit",',
    '.put("hardCount", hardIssues.length())',
    '.put("blockingCount", blockingIssues.length())',
    '"backroomAudit".equals(function)',
    "auditsForRisk(before, action, rolls, generated, candidateState, risk, writerWorker)",
]
for marker in required:
    assert marker in java, marker

blocking_start = java.index("  private boolean auditRuleCanBlock(String rule) {")
blocking_end = java.index("  private JSONArray blockingAuditIssues", blocking_start)
blocking_rule = java[blocking_start:blocking_end]
for marker in ("canon_conflict", "knowledge_leak", "state_narrative_mismatch"):
    assert marker in blocking_rule, marker
for forbidden in ("unsupported_claim", "character_voice"):
    assert forbidden not in blocking_rule, forbidden

assert 'throw new Exception("Lượt chơi không vượt qua kiểm tra canon; state không được thay đổi.");' not in java
assert "candidate state thắng" in java
assert java.count("auditsForRisk(before, action, rolls, generated, candidateState, risk, writerWorker)") >= 2

print("Canon audit softlock contract verified.")
