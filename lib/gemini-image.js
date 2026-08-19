const IMAGE_MODEL = process.env.GEMINI_IMAGE_MODEL || "gemini-3.1-flash-image";
const API_URL = "https://generativelanguage.googleapis.com/v1beta/interactions";
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

function findImage(payload) {
  const steps = Array.isArray(payload?.steps) ? payload.steps : [];
  for (let i = steps.length - 1; i >= 0; i -= 1) {
    const content = Array.isArray(steps[i]?.content) ? steps[i].content : [];
    for (let j = content.length - 1; j >= 0; j -= 1) {
      const part = content[j];
      if (part?.type === "image" && typeof part?.data === "string" && part.data) {
        return {
          data: part.data,
          mimeType: typeof part.mime_type === "string" ? part.mime_type : "image/jpeg",
        };
      }
    }
  }
  return null;
}

async function requestWithKey(key, prompt) {
  let lastError;

  for (let attempt = 0; attempt < MAX_ATTEMPTS_PER_KEY; attempt += 1) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 55_000);
    try {
      const response = await fetch(API_URL, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "x-goog-api-key": key,
        },
        signal: controller.signal,
        body: JSON.stringify({
          model: IMAGE_MODEL,
          input: [{ type: "text", text: prompt }],
          response_format: {
            type: "image",
            mime_type: "image/jpeg",
            aspect_ratio: "16:9",
            image_size: "512",
          },
        }),
      });
      clearTimeout(timeout);

      let payload = null;
      try {
        payload = await response.json();
      } catch {
        payload = null;
      }

      if (response.ok) {
        const image = findImage(payload);
        if (!image) throw new Error("GEMINI_IMAGE_EMPTY");
        return { ...image, model: IMAGE_MODEL };
      }

      const error = new Error(`Gemini image HTTP ${response.status} model=${IMAGE_MODEL}`);
      error.status = response.status;
      if (!RETRYABLE_STATUS.has(response.status)) throw error;
      lastError = error;
    } catch (error) {
      clearTimeout(timeout);
      if (!retryableNetworkError(error) && !RETRYABLE_STATUS.has(error?.status)) throw error;
      lastError = error;
    }

    if (attempt + 1 < MAX_ATTEMPTS_PER_KEY) await sleep(350 * 2 ** attempt);
  }

  throw lastError || new Error("Gemini image tạm thời không khả dụng.");
}

export async function generateSnapshotImage(prompt) {
  const keys = apiKeys();
  if (!keys.length) throw new Error("Gemini chưa được cấu hình.");

  let lastError;
  for (const key of keys) {
    try {
      return await requestWithKey(key, prompt);
    } catch (error) {
      lastError = error;
      if (!RETRYABLE_STATUS.has(error?.status) && !retryableNetworkError(error)) throw error;
    }
  }

  throw lastError || new Error("Gemini image tạm thời không khả dụng.");
}
