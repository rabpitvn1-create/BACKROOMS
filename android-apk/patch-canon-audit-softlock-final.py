from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
VERIFY = ROOT / "ci_verify_canon_audit_softlock.py"
MARKER = "AUDIT_BLOCKING_POLICY_R01"


def method_bounds(source: str, signature: str) -> tuple[int, int]:
    start = source.find(signature)
    if start < 0:
        raise RuntimeError(f"method signature missing: {signature}")
    brace = source.find("{", start)
    if brace < 0:
        raise RuntimeError(f"method opening brace missing: {signature}")
    depth = 0
    for index in range(brace, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                end = index + 1
                while end < len(source) and source[end] in "\r\n":
                    end += 1
                return start, end
    raise RuntimeError(f"method closing brace missing: {signature}")


def replace_method(source: str, signature: str, replacement: str) -> str:
    start, end = method_bounds(source, signature)
    return source[:start] + replacement.rstrip() + "\n\n" + source[end:]


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return source.replace(old, new, 1)


text = MAIN.read_text(encoding="utf-8")

run_audit = r'''  private JSONObject runAudit(JSONObject before, String action, JSONObject rolls, JSONObject generated, JSONObject candidateState, String scope, int excludedWorker) throws Exception {
    JSONObject compact = compactStateForPrompt(before);
    JSONObject candidateCompact = compactStateForPrompt(candidateState);
    String reply = generated.optString("reply", "");
    if (reply.length() > 7000) reply = reply.substring(0, 7000);
    String prompt = "Bạn là auditor riêng cho một lượt text game Backrooms. Không viết lại truyện, không tạo state, không thêm canon. " +
      "Chỉ báo lỗi khi có bằng chứng cụ thể từ canon/state/dice. Chỉ canon_conflict, knowledge_leak và state_narrative_mismatch được phép severity=hard. " +
      "unsupported_claim và character_voice chỉ được severity=soft vì chúng cần repair nhưng không được tự khóa cả lượt. " +
      "Mọi issue hard bắt buộc có claim và reason cụ thể. Khi PROPOSED OPS khác VALIDATED CANDIDATE STATE thì candidate state thắng vì Android đã loại operation không hợp lệ. Trả DUY NHẤT JSON.\n\n" +
      "AUDIT SCOPE: " + scope + "\n\n" +
      "AUTHORITATIVE CANON SLICE:\n" + auditScopeCanon(before, action, rolls, scope) + "\n\n" +
      "CURRENT STATE:\n" + compact.toString() + "\n\n" +
      "VALIDATED CANDIDATE STATE:\n" + candidateCompact.toString() + "\n\n" +
      "PLAYER ACTION:\n" + action + "\n\n" +
      "LOCKED DICE:\n" + rolls.toString() + "\n\n" +
      "PROPOSED OPS (UNTRUSTED REFERENCE ONLY):\n" + (generated.optJSONArray("ops") == null ? "[]" : generated.optJSONArray("ops").toString()) + "\n\n" +
      "PROPOSED REPLY:\n" + reply + "\n\n" +
      "Rule hợp lệ: canon_conflict, knowledge_leak, state_narrative_mismatch, unsupported_claim, character_voice. " +
      "JSON: {\"pass\":true,\"issues\":[]} hoặc {\"pass\":false,\"issues\":[{\"rule\":\"knowledge_leak\",\"severity\":\"hard\",\"claim\":\"...\",\"reason\":\"...\"},{\"rule\":\"character_voice\",\"severity\":\"soft\",\"claim\":\"...\",\"reason\":\"...\"}]}";
    JSONObject result = parseModelJson(geminiAuditText(prompt, excludedWorker));
    JSONArray issues = result.optJSONArray("issues");
    if (issues == null) issues = new JSONArray();
    return new JSONObject().put("scope", scope).put("issues", issues);
  }'''
text = replace_method(
    text,
    "  private JSONObject runAudit(JSONObject before, String action, JSONObject rolls, JSONObject generated, String scope, int excludedWorker) throws Exception ",
    run_audit,
)

audits_for_risk = r'''  private JSONArray auditsForRisk(JSONObject before, String action, JSONObject rolls, JSONObject generated, JSONObject candidateState, int risk, int writerWorker) throws Exception {
    JSONArray audits = new JSONArray();
    if (risk < 4) return audits;
    if (risk < 7) {
      audits.put(runAudit(before, action, rolls, generated, candidateState, "canon", writerWorker));
      return audits;
    }

    Future<JSONObject> canon = auditIo.submit(() -> runAudit(before, action, rolls, generated, candidateState, "canon", writerWorker));
    Future<JSONObject> character = auditIo.submit(() -> runAudit(before, action, rolls, generated, candidateState, "character", writerWorker));
    audits.put(canon.get());
    audits.put(character.get());
    return audits;
  }'''
text = replace_method(
    text,
    "  private JSONArray auditsForRisk(JSONObject before, String action, JSONObject rolls, JSONObject generated, int risk, int writerWorker) throws Exception ",
    audits_for_risk,
)

hard_signature = "  private JSONArray hardAuditIssues(JSONArray audits) throws Exception "
hard_start, hard_end = method_bounds(text, hard_signature)
hard_method = text[hard_start:hard_end].rstrip()
helpers = r'''

  /* AUDIT_BLOCKING_POLICY_R01 */
  private boolean auditRuleCanBlock(String rule) {
    String normalized = lower(rule).trim();
    return "canon_conflict".equals(normalized)
      || "knowledge_leak".equals(normalized)
      || "state_narrative_mismatch".equals(normalized);
  }

  private JSONArray blockingAuditIssues(JSONArray hardIssues) {
    JSONArray blocking = new JSONArray();
    if (hardIssues == null) return blocking;
    for (int i = 0; i < hardIssues.length(); i++) {
      JSONObject issue = hardIssues.optJSONObject(i);
      if (issue == null || !"hard".equalsIgnoreCase(issue.optString("severity", ""))) continue;
      if (!auditRuleCanBlock(issue.optString("rule", ""))) continue;
      String claim = issue.optString("claim", "").trim();
      String reason = issue.optString("reason", "").trim();
      if (claim.isEmpty() || reason.isEmpty()) continue;
      blocking.put(issue);
    }
    return blocking;
  }

  private String auditIssueRules(JSONArray issues) {
    if (issues == null || issues.length() == 0) return "none";
    StringBuilder out = new StringBuilder();
    int limit = Math.min(4, issues.length());
    for (int i = 0; i < limit; i++) {
      JSONObject issue = issues.optJSONObject(i);
      if (issue == null) continue;
      String rule = issue.optString("rule", "unknown").trim();
      if (rule.isEmpty()) rule = "unknown";
      if (out.length() > 0) out.append(',');
      out.append(rule);
    }
    return out.length() == 0 ? "unknown" : out.toString();
  }
'''
text = text[:hard_start] + hard_method + helpers + "\n\n" + text[hard_end:]

old_call = "auditsForRisk(before, action, rolls, generated, risk, writerWorker)"
call_count = text.count(old_call)
if call_count != 2:
    raise RuntimeError(f"validated audit call sites: expected 2, found {call_count}")
text = text.replace(
    old_call,
    "auditsForRisk(before, action, rolls, generated, candidateState, risk, writerWorker)",
)

old_gate = '''          if (hardIssues.length() > 0) {
            throw new Exception("Lượt chơi không vượt qua kiểm tra canon; state không được thay đổi.");
          }

          JSONObject state = candidateState;
'''
new_gate = '''          JSONArray blockingIssues = blockingAuditIssues(hardIssues);
          if (hardIssues.length() > 0) {
            emit("backroomAudit", "risk=" + risk + "; repaired=" + repaired + "; hard=" + hardIssues.length() + "; blocking=" + blockingIssues.length() + "; rules=" + auditIssueRules(hardIssues));
          }
          if (blockingIssues.length() > 0) {
            throw new Exception("Lượt chơi không vượt qua kiểm tra canon (" + auditIssueRules(blockingIssues) + "); state không được thay đổi.");
          }

          JSONObject state = candidateState;
'''
text = replace_once(text, old_gate, new_gate, "final canon audit gate")

text = replace_once(
    text,
    '''              .put("risk", risk)
              .put("count", audits.length())
              .put("repaired", repaired));
''',
    '''              .put("risk", risk)
              .put("count", audits.length())
              .put("hardCount", hardIssues.length())
              .put("blockingCount", blockingIssues.length())
              .put("repaired", repaired));
''',
    "lastAudit diagnostics",
)

text = replace_once(
    text,
    'if ("backroomProvider".equals(function) || function.endsWith("Error")) {',
    'if ("backroomProvider".equals(function) || "backroomAudit".equals(function) || function.endsWith("Error")) {',
    "debug audit event capture",
)

for marker in (
    MARKER,
    "VALIDATED CANDIDATE STATE",
    "PROPOSED OPS (UNTRUSTED REFERENCE ONLY)",
    "candidate state thắng",
    "blockingAuditIssues(hardIssues)",
    'emit("backroomAudit",',
    '.put("blockingCount", blockingIssues.length())',
    '"backroomAudit".equals(function)',
):
    if marker not in text:
        raise RuntimeError("canon audit softlock marker missing: " + marker)

blocking_start = text.index("  private boolean auditRuleCanBlock(String rule) {")
blocking_end = text.index("  private JSONArray blockingAuditIssues", blocking_start)
blocking_rule = text[blocking_start:blocking_end]
for forbidden in ("unsupported_claim", "character_voice"):
    if forbidden in blocking_rule:
        raise RuntimeError("subjective audit rule became blocking: " + forbidden)

MAIN.write_text(text, encoding="utf-8")
print("Canon audit softlock fixed: audit validated candidate state, subjective findings repair-only, objective hard conflicts still fail closed, diagnostics exported.")
runpy.run_path(str(VERIFY), run_name="__main__")
