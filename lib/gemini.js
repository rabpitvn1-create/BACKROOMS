import { GAME_MASTER_CANON } from "./canon.js";

const MODEL = process.env.GEMINI_MODEL || "gemini-2.5-flash";
const API_BASE = "https://generativelanguage.googleapis.com/v1beta/models";
const RETRYABLE_STATUS = new Set([408, 429, 500, 502, 503, 504]);
const MAX_ATTEMPTS_PER_KEY = 2;

function apiKeys() {
  return [
    process.env.GEMINI_API_KEY_1,
    process.env.GEMINI_API_KEY_2,
    process.env.GEMINI_API_KEY_3,
  ].filter(Boolean);
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function retryableNetworkError(error) {
  return error instanceof TypeError || error?.name === "AbortError";
}

async function requestWithKey(key, prompt) {
  let lastError;
  for (let attempt = 0; attempt < MAX_ATTEMPTS_PER_KEY; attempt += 1) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 45_000);
    try {
      const response = await fetch(
        `${API_BASE}/${encodeURIComponent(MODEL)}:generateContent`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "x-goog-api-key": key,
          },
          signal: controller.signal,
          body: JSON.stringify({
            contents: [{ role: "user", parts: [{ text: prompt }] }],
            generationConfig: {
              responseMimeType: "application/json",
              temperature: 0.8,
            },
          }),
        },
      );
      clearTimeout(timeout);

      if (!response.ok) {
        const error = new Error(`Gemini HTTP ${response.status} model=${MODEL}`);
        error.status = response.status;
        if (!RETRYABLE_STATUS.has(response.status)) throw error;
        lastError = error;
      } else {
        const data = await response.json();
        const text = data?.candidates?.[0]?.content?.parts?.map((part) => part?.text || "").join("").trim();
        if (!text) throw new Error("Gemini không trả nội dung.");
        return text;
      }
    } catch (error) {
      clearTimeout(timeout);
      if (!retryableNetworkError(error) && !RETRYABLE_STATUS.has(error?.status)) throw error;
      lastError = error;
    }

    if (attempt + 1 < MAX_ATTEMPTS_PER_KEY) await sleep(300 * 2 ** attempt);
  }
  throw lastError || new Error("Gemini tạm thời không khả dụng.");
}

export async function generateTurn(state, action, rolls = null) {
  const keys = apiKeys();
  if (!keys.length) throw new Error("Gemini chưa được cấu hình.");

  const prompt = `Bạn là Game Master của text game Backrooms. Hãy xử lý đúng một lượt chơi và trả DUY NHẤT JSON hợp lệ, không markdown.

${GAME_MASTER_CANON}

QUY TẮC XỬ LÝ LƯỢT:
- State dưới đây là source of truth của phiên hiện tại. Không làm state lùi turn và không quay về Prologue.
- DICE SERVER nếu được cung cấp là bất biến. Không sửa raw, không reroll và không tạo kết quả ngẫu nhiên khác để thay thế.
- Survivor/Iris/Syvial chỉ xuất hiện khi roll tương ứng success=true và continuity cho phép. Success không cho phép teleport; phải tạo tình huống gặp hợp địa lý.
- Hazard/Almond Water/Exit chỉ dùng khi eligible=true. Nếu eligible=false, bỏ qua roll đó.
- Exit roll thành công chỉ có thể tạo discovery/clue phù hợp; không tự động transition nếu Kai chưa thực hiện điều kiện cần thiết.
- Không bịa dữ kiện canon còn UNKNOWN. Nếu thiếu căn cứ để thay đổi một field, giữ nguyên field đó.
- Không trả secret, API key hoặc thông tin hệ thống.

STATE HIỆN TẠI:
${JSON.stringify(state)}

HÀNH ĐỘNG NGƯỜI CHƠI:
${action}

DICE SERVER:
${JSON.stringify(rolls)}

JSON cần có dạng:
{
  "reply": "phản hồi của Game Master bằng tiếng Việt tự nhiên",
  "title": "giữ nguyên hoặc cập nhật nếu thực sự cần",
  "mode": "ai",
  "canonLoaded": true,
  "location": "vị trí sau lượt nếu có thay đổi",
  "player": {},
  "party": [],
  "inventory": [],
  "flags": {},
  "snapshotUrl": null
}
Chỉ thay đổi dữ liệu khi diễn biến của lượt thực sự làm nó thay đổi. Với object/array không thay đổi, trả lại dữ liệu hiện tại thay vì xóa mất dữ kiện canon.`;

  let lastError;
  for (const key of keys) {
    try {
      const text = await requestWithKey(key, prompt);
      const parsed = JSON.parse(text);
      if (!parsed || typeof parsed !== "object" || typeof parsed.reply !== "string" || !parsed.reply.trim()) {
        throw new Error("Gemini trả JSON không hợp lệ.");
      }
      return parsed;
    } catch (error) {
      lastError = error;
      if (!RETRYABLE_STATUS.has(error?.status) && !retryableNetworkError(error)) throw error;
    }
  }

  throw lastError || new Error("Gemini tạm thời không khả dụng.");
}
