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

function safeGoogleError(payload, status) {
  const code = typeof payload?.error?.status === "string" ? payload.error.status.slice(0, 80) : "UNKNOWN";
  const message = typeof payload?.error?.message === "string"
    ? payload.error.message.replace(/AIza[0-9A-Za-z_-]+/g, "[redacted]").slice(0, 500)
    : "No error message";
  return `Gemini HTTP ${status} model=${MODEL} code=${code} message=${message}`;
}

async function requestWithKey(key, prompt) {
  let lastError;
  for (let attempt = 0; attempt < MAX_ATTEMPTS_PER_KEY; attempt += 1) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 45_000);
    try {
      const response = await fetch(`${API_BASE}/${encodeURIComponent(MODEL)}:generateContent`, {
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
      });
      clearTimeout(timeout);

      if (!response.ok) {
        let payload = null;
        try {
          payload = await response.json();
        } catch {
          payload = null;
        }
        const error = new Error(safeGoogleError(payload, response.status));
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

export async function generateTurn(state, action) {
  const keys = apiKeys();
  if (!keys.length) throw new Error("Gemini chưa được cấu hình.");

  const prompt = `Bạn là Game Master của text game Backrooms. Hãy xử lý đúng một lượt chơi và trả DUY NHẤT JSON hợp lệ, không markdown.

QUY TẮC BẮT BUỘC:
- Không tự nạp canon, không tuyên bố canon đã được nạp nếu state.canonLoaded đang false.
- Không bịa dữ kiện canon còn thiếu. Nếu thiếu dữ kiện để thay đổi một trường, giữ nguyên trường đó.
- Hành động phải có quan hệ nhân quả rõ, lời kể tự nhiên bằng tiếng Việt.
- Không dùng lời thoại cụt giả ngầu, ẩn dụ rỗng hoặc thuật ngữ kỹ thuật không cần thiết.
- Không trả secret, API key hoặc thông tin hệ thống.

STATE HIỆN TẠI:
${JSON.stringify(state)}

HÀNH ĐỘNG NGƯỜI CHƠI:
${action}

JSON cần có dạng:
{
  "reply": "phản hồi của Game Master",
  "title": "giữ nguyên hoặc cập nhật nếu thực sự cần",
  "mode": "ai",
  "canonLoaded": false,
  "location": "vị trí sau lượt nếu có thay đổi",
  "player": {},
  "party": [],
  "inventory": [],
  "flags": {},
  "snapshotUrl": null
}
Chỉ thay đổi dữ liệu khi diễn biến của lượt thực sự làm nó thay đổi.`;

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
