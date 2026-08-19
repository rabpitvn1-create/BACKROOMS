import { GAME_MASTER_CANON } from "./canon.js";

const CONFIGURED_MODEL = process.env.GEMINI_MODEL || "gemini-3.6-flash";
const MODEL = CONFIGURED_MODEL === "gemini-2.5-flash" ? "gemini-3.6-flash" : CONFIGURED_MODEL;
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
  return `Gemini HTTP ${status} model=${MODEL} code=${code}`;
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
- Field level phải luôn là Level hiện tại theo dạng {"number":"0","name":"The Lobby"}. Giữ nguyên level nếu chưa có chuyển Level thực sự. Không dùng tên campaign, tên nhân vật hoặc mã nội bộ làm tên Level.
- Không tự đổi title để trang trí; backend sẽ tự hiển thị tiêu đề từ field level.

QUY TẮC SNAPSHOT CỰC KỲ NGHIÊM:
- Mặc định snapshotEvent.shouldGenerate=false. Snapshot KHÔNG phải ảnh theo turn và KHÔNG được tạo chỉ vì có hành động mới.
- Chỉ đặt shouldGenerate=true khi CHÍNH LƯỢT NÀY tạo ra một mốc hình ảnh mới, rõ ràng và đáng ghi lại.
- snapshotEvent.type chỉ được là một trong: "none", "level_transition", "special_area", "entity_encounter", "character_encounter", "major_event".
- "level_transition": Kai thực sự đã sang Level khác. Chỉ dùng khi field level thay đổi thật.
- "special_area": Kai vừa bước vào một vùng đặc biệt có diện mạo khác rõ rệt so với vùng đang đi. Khi dùng loại này, flags.visualAreaKey phải đổi sang một mã ổn định mô tả vùng đó; tiếp tục đi trong cùng vùng thì GIỮ NGUYÊN mã và snapshot=false.
- "entity_encounter": một Entity thật, được xác nhận, vừa mới xuất hiện trong cảnh hiện tại. Khi dùng loại này, tăng/cập nhật dữ kiện xác nhận Entity phù hợp và đặt flags.entityEncounterKey thành mã ổn định cho lần chạm trán mới đó.
- "character_encounter": Kai vừa thực sự gặp survivor/NPC/Iris/Syvial trong không gian hiện tại. Phải phản ánh bằng party, survivorsConfirmed hoặc continuity tương ứng.
- "major_event": một sự kiện lớn/hiếm vừa làm thay đổi rõ hình ảnh của cảnh hiện tại, không phải chỉ nghe tiếng, nghi ngờ nguy hiểm, quan sát hay di chuyển thường. Khi dùng loại này, flags.visualEventKey phải đổi sang một mã ổn định cho sự kiện mới.
- Phải snapshot=false cho đi lại/quan sát/tìm kiếm thông thường, hội thoại không đổi cảnh, hành động lặp lại, lượt yên, thay đổi nhỏ, nguy hiểm chỉ mới được nghi ngờ, hoặc khi cảnh nhìn tổng thể vẫn giống ảnh hiện có.
- Không dùng số turn, thời gian kể từ ảnh trước, việc người chơi vừa gửi lệnh, hay mong muốn tạo kịch tính làm lý do tạo Snapshot.
- Không tạo một event key mới chỉ để ép Snapshot. Nếu không có thay đổi hình ảnh đủ lớn, giữ nguyên các key hiện tại và shouldGenerate=false.

STATE HIỆN TẠI:
${JSON.stringify(state)}

HÀNH ĐỘNG NGƯỜI CHƠI:
${action}

DICE SERVER:
${JSON.stringify(rolls)}

JSON cần có dạng:
{
  "reply": "phản hồi của Game Master bằng tiếng Việt tự nhiên",
  "mode": "ai",
  "canonLoaded": true,
  "level": {"number": "0", "name": "The Lobby"},
  "location": "vị trí sau lượt nếu có thay đổi",
  "player": {},
  "party": [],
  "inventory": [],
  "flags": {},
  "snapshotEvent": {
    "shouldGenerate": false,
    "type": "none",
    "reason": ""
  }
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
