import test from "node:test";
import assert from "node:assert/strict";
import { AUDIT_LEVEL, scoreTurnRisk } from "../lib/turn-risk.js";

function current(party = []) { return { party, flags: {} }; }

test("quiet turn does not request an LLM audit", () => {
  const risk = scoreTurnRisk({
    current: current(),
    generated: { reply: "Kai đi tiếp qua dãy cột bê tông." },
    acceptedOps: [{ type: "set_location", value: "Level 1 / East Ramp" }],
    rejectedOps: [],
  });
  assert.equal(risk.level, AUDIT_LEVEL.NONE);
});

test("level transition requests a narrow audit", () => {
  const risk = scoreTurnRisk({
    current: current(),
    generated: { reply: "Không gian hẹp dần thành mạng đường ống." },
    acceptedOps: [{ type: "set_level", level: { number: "2", name: "Pipe Dreams" } }],
    rejectedOps: [],
  });
  assert.equal(risk.level, AUDIT_LEVEL.NARROW);
});

test("Vietnamese knowledge signal is Unicode-aware", () => {
  const risk = scoreTurnRisk({
    current: current([{ name: "Iris" }]),
    generated: { reply: "Iris nhớ nguồn gốc của dấu vết này." },
    acceptedOps: [{ type: "party_upsert", member: { name: "Iris" } }],
    rejectedOps: [],
  });
  assert.ok(risk.reasons.includes("character_knowledge_claim"));
});

test("reunion plus knowledge claim escalates to critical audit", () => {
  const risk = scoreTurnRisk({
    current: current([{ name: "Iris" }]),
    generated: { reply: "Iris nói rằng cô biết bí mật về nguồn gốc nơi này." },
    acceptedOps: [
      { type: "party_upsert", member: { name: "Iris" } },
      { type: "flag_patch", root: "iris", value: { continuity: "REUNITED / WITH KAI" } },
    ],
    rejectedOps: [],
  });
  assert.equal(risk.level, AUDIT_LEVEL.CRITICAL);
  assert.ok(risk.reasons.includes("character_knowledge_claim"));
});

test("even one rejected operation forces at least a narrow audit", () => {
  const risk = scoreTurnRisk({
    current: current(),
    generated: { reply: "Kai nhặt khẩu súng vừa xuất hiện." },
    acceptedOps: [],
    rejectedOps: [{ reason: "inventory_acquisition_not_established" }],
  });
  assert.equal(risk.level, AUDIT_LEVEL.NARROW);
  assert.ok(risk.score >= 4);
  assert.ok(risk.reasons.includes("rejected_state_ops"));
});
