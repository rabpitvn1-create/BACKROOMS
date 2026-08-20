import { generateProviderText } from "./ai-provider-pool.js";
import { canonPacketFor } from "./canon-router.js";

function compactAuditState(state) {
  return {
    level: state?.level,
    location: state?.location,
    player: state?.player,
    party: state?.party,
    inventory: state?.inventory,
    flags: state?.flags,
    recentLog: Array.isArray(state?.log) ? state.log.slice(-4) : [],
  };
}

function parseAudit(text, provider) {
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new Error(`${provider} auditor trả JSON không hợp lệ.`);
  }
  const issues = Array.isArray(parsed?.issues) ? parsed.issues.slice(0, 12) : [];
  return {
    pass: parsed?.pass === true && !issues.some((issue) => issue?.severity === "hard"),
    issues,
  };
}

export async function auditTurn({
  scope = "canon",
  current,
  action,
  rolls,
  generated,
  acceptedOps,
  rejectedOps,
  excludeSlots = [],
}) {
  const packet = canonPacketFor(current, action, rolls);
  const scopeCanon = scope === "character"
    ? `${packet.characterCanon}\n\n${packet.writingCanon}`
    : `${packet.gameMasterCanon}\n\n${packet.worldCanon}`;

  const prompt = `Bạn là auditor độc lập cho một lượt text game Backrooms. Không viết lại truyện, không tạo state, không thêm canon. Chỉ kiểm các claim đã có dựa trên dữ kiện được cung cấp và trả DUY NHẤT JSON.

AUDIT SCOPE: ${scope}

AUTHORITATIVE CANON SLICE:
${scopeCanon}

CURRENT STATE:
${JSON.stringify(compactAuditState(current))}

PLAYER ACTION:
${action}

LOCKED DICE:
${JSON.stringify(rolls)}

ACCEPTED OPS:
${JSON.stringify(acceptedOps)}

REJECTED OPS:
${JSON.stringify(rejectedOps)}

PROPOSED REPLY:
${String(generated?.reply || "").slice(0, 9000)}

CHỈ BÁO HARD khi có xung đột cụ thể và chứng minh được từ canon/state/dice trên. Không báo lỗi chỉ vì bạn thích cách viết khác. Không suy diễn dữ kiện không được cung cấp.
Các rule hợp lệ: canon_conflict, knowledge_leak, state_narrative_mismatch, unsupported_claim, character_voice.
JSON:
{"pass":true,"issues":[]}
hoặc
{"pass":false,"issues":[{"rule":"knowledge_leak","severity":"hard","claim":"...","reason":"..."}]}`;

  const result = await generateProviderText(prompt, {
    policy: "audit",
    allowLuna: false,
    excludeSlots,
    reuseExcluded: true,
    maxOutputTokens: 650,
    totalDeadlineMs: 18_000,
  });
  return {
    ...parseAudit(result.text, result.provider),
    provider: result.provider,
    workerSlot: result.workerSlot,
    model: result.model,
    scope,
  };
}
