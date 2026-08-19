import { GAME_MASTER_CANON } from "./canon.js";

const MODEL = process.env.GEMINI_MODEL || "gemini-2.5-flash";
const API_BASE = "https://generativelanguage.googleapis.com/v1beta/models";
const MAX_ATTEMPTS_PER_KEY = 2;

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function configuredKeys() {
  return [
    process.env.GEMINI_API_KEY_1,
    process.env.GEMINI_API_KEY_2,
    process.env.GEMINI_API_KEY_3
  ].filter(Boolean);
}

function isRetryableStatus(status, payload) {
  if ([429, 500, 502, 503, 504].includes(status)) return true;
  const code = payload?.error?.status;
  return code === "RESOURCE_EXHAUSTED" || code === "UNAVAILABLE";
}

function safeParseJson(text) {
  if (!text) return null;
  const trimmed = text.trim().replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/, "");
  try {
    return JSON.parse(trimmed);
  } catch {
    return null;
  }
}

function extractText(payload) {
  return (payload?.candidates?.[0]?.content?.parts || [])
    .map((part) => typeof part?.text === "string" ? part.text : "")
    .join("")
    .trim();
}

export async function callGeminiTurn({ action, state, rolls }) {
  const keys = configuredKeys();
  if (!keys.length) {
    return { ok: false, status: 500, error: "GEMINI_NOT_CONFIGURED" };
  }

  const requestBody = {
    systemInstruction: {
      parts: [{ text: GAME_MASTER_CANON }]
    },
    contents: [{
      role: "user",
      parts: [{
        text: `STATE HIỆN TẠI:\n${JSON.stringify(state)}\n\nHÀNH ĐỘNG NGƯỜI CHƠI:\n${action}\n\nDICE SERVER ĐÃ KHÓA CHO LƯỢT NÀY:\n${JSON.stringify(rolls)}\n\nHãy giải quyết đúng một gameplay turn. Các raw roll do server tạo là bất biến. Survivor/Iris/Syvial roll luôn phải được tôn trọng khi eligible. Exit/Loot/Hazard chỉ dùng raw roll tương ứng nếu hành động thực sự đủ điều kiện; nếu không đủ điều kiện, không biến raw roll thành sự kiện. Không tự tạo thêm random roll bằng lời.\n\nTrả duy nhất JSON hợp lệ dạng:\n{\"narrative\":\"văn bản GM cho người chơi\",\"stateChanges\":{\"location\":\"chỉ khi đổi\",\"player\":{},\"party\":[],\"inventory\":[],\"flags\":{}}}\nChỉ đưa các field stateChanges thật sự thay đổi. Không ghi sessionId, revision, version, turn, canonLoaded hoặc updatedAt trong stateChanges.`
      }]
    }],
    generationConfig: {
      temperature: 0.65,
      responseMimeType: "application/json"
    }
  };

  let transientCount = 0;

  for (const key of keys) {
    for (let attempt = 0; attempt < MAX_ATTEMPTS_PER_KEY; attempt += 1) {
      try {
        const response = await fetch(`${API_BASE}/${encodeURIComponent(MODEL)}:generateContent`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "x-goog-api-key": key
          },
          body: JSON.stringify(requestBody),
          signal: AbortSignal.timeout(45000)
        });

        let payload = null;
        try {
          payload = await response.json();
        } catch {
          payload = null;
        }

        if (response.ok) {
          const rawText = extractText(payload);
          const parsed = safeParseJson(rawText);
          if (!parsed || typeof parsed.narrative !== "string" || !parsed.narrative.trim()) {
            return { ok: false, status: 502, error: "GEMINI_INVALID_OUTPUT" };
          }
          return {
            ok: true,
            status: 200,
            narrative: parsed.narrative.trim(),
            stateChanges: parsed.stateChanges && typeof parsed.stateChanges === "object" ? parsed.stateChanges : {},
            model: MODEL
          };
        }

        if (!isRetryableStatus(response.status, payload)) {
          return {
            ok: false,
            status: response.status >= 400 && response.status < 500 ? response.status : 502,
            error: "GEMINI_REQUEST_REJECTED"
          };
        }

        transientCount += 1;
        if (attempt + 1 < MAX_ATTEMPTS_PER_KEY) {
          await sleep(Math.min(1500, 250 * (2 ** Math.min(transientCount - 1, 3))));
        }
      } catch {
        transientCount += 1;
        if (attempt + 1 < MAX_ATTEMPTS_PER_KEY) {
          await sleep(Math.min(1500, 250 * (2 ** Math.min(transientCount - 1, 3))));
        }
      }
    }
  }

  return { ok: false, status: 503, error: "GEMINI_TEMPORARILY_UNAVAILABLE" };
}
