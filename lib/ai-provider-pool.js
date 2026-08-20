const GEMINI_MODEL = process.env.GEMINI_MODEL || "gemini-3.6-flash";
const GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models";
const RETRYABLE_STATUS = new Set([408, 429, 500, 502, 503, 504]);
const AUTH_STATUS = new Set([401, 403]);
const MAX_ATTEMPTS_PER_WORKER = 1;
const GEMINI_REQUEST_TIMEOUT_MS = 5_000;
const LUNA_REQUEST_TIMEOUT_MS = 12_000;
const TOTAL_PROVIDER_DEADLINE_MS = 42_000;

const workerHealth = new Map();
let selectionCounter = 0;

function now() { return Date.now(); }
function configuredGeminiWorkers() {
  return [1, 2, 3, 4, 5]
    .map((slot) => ({ slot, key: process.env[`GEMINI_API_KEY_${slot}`] }))
    .filter((worker) => typeof worker.key === "string" && worker.key.trim());
}
function stateFor(slot) {
  if (!workerHealth.has(slot)) workerHealth.set(slot, { failures: 0, cooldownUntil: 0, disabledUntil: 0, latencyEma: 0, inFlight: 0, successes: 0 });
  return workerHealth.get(slot);
}
function retryableNetworkError(error) { return error instanceof TypeError || error?.name === "AbortError"; }
function safeGoogleError(payload, status) {
  const code = typeof payload?.error?.status === "string" ? payload.error.status.slice(0, 80) : "UNKNOWN";
  const error = new Error(`Gemini HTTP ${status} model=${GEMINI_MODEL} code=${code}`);
  error.status = status;
  return error;
}
function workerScore(worker) {
  const health = stateFor(worker.slot); const time = now();
  if (health.disabledUntil > time) return Number.POSITIVE_INFINITY;
  if (health.cooldownUntil > time) return 1_000_000 + health.cooldownUntil - time;
  const latency = health.latencyEma || 1_500;
  const rotationBias = ((worker.slot - selectionCounter + 5) % 5) * 5;
  return health.inFlight * 10_000 + health.failures * 2_000 + latency + rotationBias;
}
function orderedWorkers(excludeSlots = []) {
  const excluded = new Set(excludeSlots);
  const workers = configuredGeminiWorkers().filter((worker) => !excluded.has(worker.slot));
  selectionCounter = (selectionCounter + 1) % 5;
  return workers.sort((a, b) => workerScore(a) - workerScore(b));
}
function noteSuccess(slot, latencyMs) {
  const health = stateFor(slot); health.successes += 1; health.failures = Math.max(0, health.failures - 1); health.cooldownUntil = 0;
  health.latencyEma = health.latencyEma ? Math.round(health.latencyEma * 0.7 + latencyMs * 0.3) : latencyMs;
}
function noteFailure(slot, error) {
  const health = stateFor(slot); const status = Number(error?.status || 0); health.failures += 1;
  if (AUTH_STATUS.has(status)) health.disabledUntil = now() + 30 * 60_000;
  else if (status === 429) health.cooldownUntil = now() + 60_000;
  else if (RETRYABLE_STATUS.has(status)) health.cooldownUntil = now() + Math.min(30_000, 2_000 * health.failures);
  else if (retryableNetworkError(error)) health.cooldownUntil = now() + Math.min(10_000, 1_000 * health.failures);
}
function remaining(deadline) { return Math.max(0, deadline - now()); }

async function requestGemini(worker, prompt, options, deadline) {
  let lastError; const health = stateFor(worker.slot); health.inFlight += 1;
  try {
    for (let attempt = 0; attempt < MAX_ATTEMPTS_PER_WORKER; attempt += 1) {
      const budget = Math.min(options.timeoutMs || GEMINI_REQUEST_TIMEOUT_MS, GEMINI_REQUEST_TIMEOUT_MS, remaining(deadline));
      if (budget < 250) throw new Error("Provider deadline exhausted before Gemini request.");
      const controller = new AbortController(); const timeout = setTimeout(() => controller.abort(), budget); const started = now();
      try {
        const generationConfig = { responseMimeType: "application/json", temperature: options.temperature ?? 0.8 };
        if (Number.isFinite(options.maxOutputTokens) && options.maxOutputTokens > 0) generationConfig.maxOutputTokens = Math.floor(options.maxOutputTokens);
        const response = await fetch(`${GEMINI_API_BASE}/${encodeURIComponent(GEMINI_MODEL)}:generateContent`, {
          method: "POST", headers: { "Content-Type": "application/json", "x-goog-api-key": worker.key }, signal: controller.signal,
          body: JSON.stringify({ contents: [{ role: "user", parts: [{ text: prompt }] }], generationConfig }),
        });
        clearTimeout(timeout);
        if (!response.ok) {
          let payload = null; try { payload = await response.json(); } catch { payload = null; }
          throw safeGoogleError(payload, response.status);
        }
        const data = await response.json();
        const text = data?.candidates?.[0]?.content?.parts?.map((part) => part?.text || "").join("").trim();
        if (!text) throw new Error("Gemini không trả nội dung.");
        noteSuccess(worker.slot, now() - started); return text;
      } catch (error) {
        clearTimeout(timeout); lastError = error; noteFailure(worker.slot, error);
      }
    }
  } finally { health.inFlight = Math.max(0, health.inFlight - 1); }
  throw lastError || new Error("Gemini worker không khả dụng.");
}

async function requestLuna(prompt, options, deadline) {
  const key = process.env.LUNA_API_KEY?.trim(); const model = process.env.LUNA_MODEL?.trim(); let baseUrl = process.env.LUNA_BASE_URL?.trim();
  if (!key || !model || !baseUrl) throw new Error("Luna chưa được cấu hình.");
  baseUrl = baseUrl.replace(/\/+$/, "");
  const budget = Math.min(options.lunaTimeoutMs || LUNA_REQUEST_TIMEOUT_MS, LUNA_REQUEST_TIMEOUT_MS, remaining(deadline));
  if (budget < 500) throw new Error("Provider deadline exhausted before Luna fallback.");
  const controller = new AbortController(); const timeout = setTimeout(() => controller.abort(), budget);
  try {
    const response = await fetch(`${baseUrl}/chat/completions`, {
      method: "POST", headers: { "Content-Type": "application/json", Authorization: `Bearer ${key}` }, signal: controller.signal,
      body: JSON.stringify({ model, messages: [{ role: "user", content: prompt }], temperature: options.temperature ?? 0.75,
        max_tokens: Number.isFinite(options.maxOutputTokens) && options.maxOutputTokens > 0 ? Math.floor(options.maxOutputTokens) : 1800, stream: false }),
    });
    if (!response.ok) { const error = new Error(`Luna HTTP ${response.status}`); error.status = response.status; throw error; }
    const data = await response.json(); const text = data?.choices?.[0]?.message?.content?.trim();
    if (!text) throw new Error("Luna không trả nội dung."); return text;
  } finally { clearTimeout(timeout); }
}

async function tryGeminiWorkers(prompt, workers, options, deadline) {
  let lastError;
  for (const worker of workers) {
    if (remaining(deadline) < 750) break;
    const health = stateFor(worker.slot); if (health.disabledUntil > now() || health.cooldownUntil > now()) continue;
    try { const text = await requestGemini(worker, prompt, options, deadline); return { text, provider: "Gemini", workerSlot: worker.slot, model: GEMINI_MODEL }; }
    catch (error) { lastError = error; }
  }
  if (lastError) throw lastError; return null;
}

export async function generateProviderText(prompt, options = {}) {
  let lastError;
  const totalMs = Math.min(Math.max(Number(options.totalDeadlineMs) || TOTAL_PROVIDER_DEADLINE_MS, 2_000), TOTAL_PROVIDER_DEADLINE_MS);
  const deadline = now() + totalMs;
  const excluded = options.excludeSlots || [];
  try { const result = await tryGeminiWorkers(prompt, orderedWorkers(excluded), options, deadline); if (result) return result; } catch (error) { lastError = error; }
  if (excluded.length && options.reuseExcluded !== false && remaining(deadline) >= 750) {
    try { const result = await tryGeminiWorkers(prompt, orderedWorkers([]).filter((worker) => excluded.includes(worker.slot)), options, deadline); if (result) return result; } catch (error) { lastError = error; }
  }
  if (options.allowLuna !== false && remaining(deadline) >= 500) {
    try { const text = await requestLuna(prompt, options, deadline); return { text, provider: "Luna", workerSlot: null, model: process.env.LUNA_MODEL || "Luna" }; }
    catch (error) { lastError = error; }
  }
  throw lastError || new Error("Không có AI provider khả dụng trong deadline.");
}

export function providerHealthSnapshot() {
  return [1, 2, 3, 4, 5].map((slot) => { const configured = Boolean(process.env[`GEMINI_API_KEY_${slot}`]?.trim()); const health = stateFor(slot);
    return { slot, configured, failures: health.failures, latencyEma: health.latencyEma, inFlight: health.inFlight, coolingDown: health.cooldownUntil > now(), disabled: health.disabledUntil > now() }; });
}
export function configuredGeminiCount() { return configuredGeminiWorkers().length; }
export const PROVIDER_LIMITS = Object.freeze({ geminiTimeoutMs: GEMINI_REQUEST_TIMEOUT_MS, lunaTimeoutMs: LUNA_REQUEST_TIMEOUT_MS, totalDeadlineMs: TOTAL_PROVIDER_DEADLINE_MS });
