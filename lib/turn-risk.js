const HIGH_RISK_FLAG_ROOTS = new Set([
  "iris",
  "syvial",
  "survivorRegistry",
  "entityRegistry",
  "survivorsConfirmed",
  "entitiesConfirmedLocal",
  "madGod",
  "reunionPath",
]);

const MEDIUM_RISK_FLAG_ROOTS = new Set([
  "omnivault",
  "communication",
  "exploration",
  "visualAreaKey",
  "visualEventKey",
  "entityEncounterKey",
]);

const KNOWLEDGE_SIGNAL = /\b(biết|nhớ|nhận ra|hiểu rằng|tiết lộ|bí mật|nguồn gốc|thật ra|kể rằng|told|knows|knew|remember|secret|origin)\b/i;
const RELATIONSHIP_SIGNAL = /\b(yêu|thích|ghen|tin tưởng|phản bội|người yêu|hẹn hò|quan hệ|love|trust|betray|relationship)\b/i;

export const AUDIT_LEVEL = Object.freeze({
  NONE: "none",
  NARROW: "narrow",
  CRITICAL: "critical",
});

export function scoreTurnRisk({ current, generated, acceptedOps = [], rejectedOps = [] }) {
  let score = 0;
  const reasons = [];

  for (const op of acceptedOps) {
    switch (op?.type) {
      case "set_level":
        score += 4;
        reasons.push("level_transition");
        break;
      case "party_upsert":
      case "party_remove":
        score += 3;
        reasons.push("party_change");
        break;
      case "inventory_upsert":
      case "inventory_remove":
        score += 1;
        reasons.push("inventory_change");
        break;
      case "patch_player":
        score += 1;
        reasons.push("player_state_change");
        break;
      case "flag_patch": {
        const root = String(op.root || "");
        if (HIGH_RISK_FLAG_ROOTS.has(root)) {
          score += 3;
          reasons.push(`high_flag:${root}`);
        } else if (MEDIUM_RISK_FLAG_ROOTS.has(root)) {
          score += 1;
          reasons.push(`flag:${root}`);
        }
        break;
      }
      default:
        break;
    }
  }

  const reply = String(generated?.reply || "");
  const hasPresentCharacters = Array.isArray(current?.party) && current.party.length > 0;
  if (hasPresentCharacters && KNOWLEDGE_SIGNAL.test(reply)) {
    score += 2;
    reasons.push("character_knowledge_claim");
  }
  if (hasPresentCharacters && RELATIONSHIP_SIGNAL.test(reply)) {
    score += 2;
    reasons.push("relationship_claim");
  }
  if (Array.isArray(rejectedOps) && rejectedOps.length > 0) {
    score += Math.min(3, rejectedOps.length);
    reasons.push("rejected_state_ops");
  }

  const uniqueReasons = [...new Set(reasons)];
  const level = score >= 7 ? AUDIT_LEVEL.CRITICAL : score >= 4 ? AUDIT_LEVEL.NARROW : AUDIT_LEVEL.NONE;
  return { score, level, reasons: uniqueReasons };
}
