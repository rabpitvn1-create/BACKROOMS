const GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models";
const AUTH_STATUS = new Set([401, 403]);
const QUOTA_STATUS = new Set([429]);
const TRANSIENT_STATUS = new Set([408, 500, 502, 503, 504]);

const WRITER_MODELS = Object.freeze([
  Object.freeze({ id: "gemini-3.6-flash", timeoutMs: 12_000, thinkingLevel: "low" }),
  Object.freeze({ id: "gemini-3.5-flash", timeoutMs: 10_000, thinkingLevel: "low" }),
  Object.freeze({ id: "gemini-3.5-flash-lite", timeoutMs: 7_000, thinkingLevel: "minimal" }),
]);
const AUDITOR_MODELS = Object.freeze([
  Object.freeze({ id: "gemini-3.5-flash-lite", timeoutMs: 6_000, thinkingLevel: "minimal" }),
  Object.freeze({ id: "gemini-3.5-flash", timeoutMs: 8_000, thinkingLevel: "low" }),
]);

const LUNA_REQUEST_TIMEOUT_MS = 12_000;
const TOTAL_PROVIDER_DEADLINE_MS = 55_000;
const LUNA_RESERVE_MS = 12_500;
const CREDENTIAL_AUTH_DISABLE_MS = 30 * 60_000;
const QUOTA_COOLDOWN_MS = 60_000;
const MODEL_ERROR_CIRCUIT_MS = 5 * 60_000;
const MODEL_TRANSIENT_CIRCUIT_MS = 45_000;
const HOST_TRANSPORT_CIRCUIT_MS = 30_000;
const FAILURE_SAMPLE_WINDOW_MS = 60_000;
const DISTINCT_TRANSIENT_SLOTS_TO_OPEN = 3;
const DISTINCT_TRANSPORT_SLOTS_TO_OPEN = 3;

const credentialHealth = new Map();
const laneHealth = new Map();
const modelHealth = new Map();
let geminiHostCircuitUntil = 0;
let selectionCounter = 0;

function now() {
  return Date.now();
}

function configuredGeminiWorkers() {
  return [1, 2, 3, 4, 5]
    .map((slot) => ({ slot, key: process.env[`GEMINI_API_KEY_${slot}`] }))
    .filter((worker) => typeof worker.key === "string" && worker.key.trim());
}

function credentialState(slot) {
  if (!credentialHealth.has(slot)) {
    credentialHealth.set(slot, { disabledUntil: 0, authFailures: 0, transportSamples: [] });
  }
  return credentialHealth.get(slot);
}

function laneKey(model, slot) {
  return `${model}::${slot}`;
}

function laneState(model, slot) {
  const key = laneKey(model, slot);
  if (!laneHealth.has(key)) {
    laneHealth.set(key, {
      failures: 0,
      successes: 0,
      cooldownUntil: 0,
      latencyEma: 0,
      inFlight: 0,
      lastFailureClass: "",
      lastStatus: 0,
    });
  }
  return laneHealth.get(key);
}

function modelState(model) {
  if (!modelHealth.has(model)) {
    modelHealth.set(model, {
      circuitUntil: 0,
      circuitReason: "",
      samples: new Map(),
      successes: 0,
      failures: 0,
    });
  }
  return modelHealth.get(model);
}

function remaining(deadline) {
  return Math.max(0, deadline - now());
}

function isNetworkLike(error) {
  return error instanceof TypeError || error?.name === "AbortError" || error?.network === true;
}

function safeGoogleError(payload, status, model) {
  const code = typeof payload?.error?.status === "string" ? payload.error.status.slice(0, 80) : "UNKNOWN";
  const providerMessage = typeof payload?.error?.message === "string" ? payload.error.message.slice(0, 180) : "";
  const error = new Error(`Gemini HTTP ${status} model=${model} code=${code}${providerMessage ? `: ${providerMessage}` : ""}`);
  error.status = status;
  error.providerCode = code;
  error.model = model;
  return error;
}

function classifyGeminiFailure(error) {
  const status = Number(error?.status || 0);
  const code = String(error?.providerCode || "").toUpperCase();
  if (AUTH_STATUS.has(status)) return { kind: "auth", status, fingerprint: `auth:${status}:${code}` };
  if (QUOTA_STATUS.has(status)) return { kind: "quota", status, fingerprint: `quota:${status}:${code}` };
  if (status === 400 || status === 404) return { kind: "model", status, fingerprint: `model:${status}:${code}` };
  if (TRANSIENT_STATUS.has(status)) return { kind: "transient", status, fingerprint: `transient:${status}:${code}` };
  if (isNetworkLike(error)) {
    const name = String(error?.name || "network");
    return { kind: "transport", status: 0, fingerprint: `transport:${name}` };
  }
  if (status >= 400 && status < 500) return { kind: "lane", status, fingerprint: `lane:${status}:${code}` };
  return { kind: "transient", status, fingerprint: `transient:${status}:${String(error?.name || "unknown")}` };
}

function pruneSamples(samples, cutoff) {
  for (const [slot, timestamp] of samples.entries()) {
    if (timestamp < cutoff) samples.delete(slot);
  }
}

function noteTransportFailure(slot) {
  const cutoff = now() - FAILURE_SAMPLE_WINDOW_MS;
  const samplesBySlot = new Map();
  for (const [credentialSlot, health] of credentialHealth.entries()) {
    const recent = health.transportSamples.filter((timestamp) => timestamp >= cutoff);
    health.transportSamples = recent;
    if (recent.length) samplesBySlot.set(credentialSlot, recent[recent.length - 1]);
  }
  const credential = credentialState(slot);
  credential.transportSamples.push(now());
  samplesBySlot.set(slot, now());
  if (samplesBySlot.size >= DISTINCT_TRANSPORT_SLOTS_TO_OPEN) {
    geminiHostCircuitUntil = Math.max(geminiHostCircuitUntil, now() + HOST_TRANSPORT_CIRCUIT_MS);
  }
}

function noteModelSample(model, slot, classification) {
  const health = modelState(model);
  health.failures += 1;
  const cutoff = now() - FAILURE_SAMPLE_WINDOW_MS;
  for (const samples of health.samples.values()) pruneSamples(samples, cutoff);

  if (classification.kind === "model") {
    health.circuitUntil = Math.max(health.circuitUntil, now() + MODEL_ERROR_CIRCUIT_MS);
    health.circuitReason = classification.fingerprint;
    return;
  }

  if (classification.kind !== "transient") return;
  if (!health.samples.has(classification.fingerprint)) health.samples.set(classification.fingerprint, new Map());
  const samples = health.samples.get(classification.fingerprint);
  samples.set(slot, now());
  pruneSamples(samples, cutoff);
  if (samples.size >= DISTINCT_TRANSIENT_SLOTS_TO_OPEN) {
    health.circuitUntil = Math.max(health.circuitUntil, now() + MODEL_TRANSIENT_CIRCUIT_MS);
    health.circuitReason = classification.fingerprint;
  }
}

function noteGeminiSuccess(model, slot, latencyMs) {
  const lane = laneState(model, slot);
  lane.successes += 1;
  lane.failures = Math.max(0, lane.failures - 1);
  lane.cooldownUntil = 0;
  lane.lastFailureClass = "";
  lane.lastStatus = 0;
  lane.latencyEma = lane.latencyEma ? Math.round(lane.latencyEma * 0.7 + latencyMs * 0.3) : latencyMs;

  const modelStatus = modelState(model);
  modelStatus.successes += 1;
  modelStatus.circuitUntil = 0;
  modelStatus.circuitReason = "";
  modelStatus.samples.clear();
}

function noteGeminiFailure(model, slot, error) {
  const classification = classifyGeminiFailure(error);
  const lane = laneState(model, slot);
  lane.failures += 1;
  lane.lastFailureClass = classification.kind;
  lane.lastStatus = classification.status;

  if (classification.kind === "auth") {
    const credential = credentialState(slot);
    credential.authFailures += 1;
    credential.disabledUntil = Math.max(credential.disabledUntil, now() + CREDENTIAL_AUTH_DISABLE_MS);
  } else if (classification.kind === "quota") {
    lane.cooldownUntil = Math.max(lane.cooldownUntil, now() + QUOTA_COOLDOWN_MS);
  } else if (classification.kind === "transient") {
    lane.cooldownUntil = Math.max(lane.cooldownUntil, now() + Math.min(15_000, 1_500 * lane.failures));
  } else if (classification.kind === "transport") {
    lane.cooldownUntil = Math.max(lane.cooldownUntil, now() + Math.min(10_000, 1_000 * lane.failures));
    noteTransportFailure(slot);
  } else if (classification.kind === "lane") {
    lane.cooldownUntil = Math.max(lane.cooldownUntil, now() + 30_000);
  }

  noteModelSample(model, slot, classification);
  return classification;
}

function workerScore(model, worker) {
  const credential = credentialState(worker.slot);
  const lane = laneState(model, worker.slot);
  const time = now();
  if (credential.disabledUntil > time) return Number.POSITIVE_INFINITY;
  if (lane.cooldownUntil > time) return 1_000_000 + lane.cooldownUntil - time;
  const latency = lane.latencyEma || 1_500;
  const rotationBias = ((worker.slot - selectionCounter + 5) % 5) * 5;
  return lane.inFlight * 10_000 + lane.failures * 2_000 + latency + rotationBias;
}

function orderedWorkers(model, { excludeSlots = [], onlySlots = null } = {}) {
  const excluded = new Set(excludeSlots);
  const only = Array.isArray(onlySlots) ? new Set(onlySlots) : null;
  selectionCounter = (selectionCounter + 1) % 5;
  return configuredGeminiWorkers()
    .filter((worker) => !excluded.has(worker.slot))
    .filter((worker) => !only || only.has(worker.slot))
    .sort((a, b) => workerScore(model, a) - workerScore(model, b));
}

function modelChainFor(options) {
  if (Array.isArray(options.models) && options.models.length) {
    return options.models.map((entry) => typeof entry === "string"
      ? { id: entry, timeoutMs: 8_000, thinkingLevel: entry.includes("lite") ? "minimal" : "low" }
      : entry);
  }
  return options.policy === "audit" ? AUDITOR_MODELS : WRITER_MODELS;
}

function modelAvailable(model) {
  return modelState(model).circuitUntil <= now();
}

function geminiHostAvailable() {
  return geminiHostCircuitUntil <= now();
}

function requestBudget(modelSpec, options, deadline, reserveLuna) {
  const desired = Number(options.timeoutMs) > 0 ? Number(options.timeoutMs) : modelSpec.timeoutMs;
  const hard = Math.min(Math.max(desired, 1_000), modelSpec.timeoutMs);
  const remainingBudget = remaining(deadline) - reserveLuna;
  return Math.min(hard, Math.max(0, remainingBudget));
}

async function requestGemini(worker, modelSpec, prompt, options, deadline, reserveLuna) {
  const model = modelSpec.id;
  const lane = laneState(model, worker.slot);
  const credential = credentialState(worker.slot);
  if (credential.disabledUntil > now()) throw Object.assign(new Error("Gemini credential đang bị khóa tạm thời."), { status: 401 });
  if (lane.cooldownUntil > now()) throw Object.assign(new Error("Gemini lane đang cooldown."), { status: 429 });

  const budget = requestBudget(modelSpec, options, deadline, reserveLuna);
  if (budget < 250) throw new Error("Provider deadline exhausted before Gemini request.");

  lane.inFlight += 1;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), budget);
  const started = now();
  try {
    const generationConfig = {
      responseMimeType: "application/json",
      thinkingConfig: { thinkingLevel: modelSpec.thinkingLevel },
    };
    if (Number.isFinite(options.maxOutputTokens) && options.maxOutputTokens > 0) {
      generationConfig.maxOutputTokens = Math.floor(options.maxOutputTokens);
    }

    const response = await fetch(`${GEMINI_API_BASE}/${encodeURIComponent(model)}:generateContent`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-goog-api-key": worker.key,
      },
      signal: controller.signal,
      body: JSON.stringify({
        contents: [{ role: "user", parts: [{ text: prompt }] }],
        generationConfig,
      }),
    });

    if (!response.ok) {
      let payload = null;
      try { payload = await response.json(); } catch { payload = null; }
      throw safeGoogleError(payload, response.status, model);
    }

    const data = await response.json();
    const text = data?.candidates?.[0]?.content?.parts?.map((part) => part?.text || "").join("").trim();
    if (!text) throw new Error(`Gemini ${model} không trả nội dung.`);
    noteGeminiSuccess(model, worker.slot, now() - started);
    return text;
  } catch (error) {
    noteGeminiFailure(model, worker.slot, error);
    throw error;
  } finally {
    clearTimeout(timeout);
    lane.inFlight = Math.max(0, lane.inFlight - 1);
  }
}

async function requestLuna(prompt, options, deadline) {
  const key = process.env.LUNA_API_KEY?.trim();
  const model = process.env.LUNA_MODEL?.trim();
  let baseUrl = process.env.LUNA_BASE_URL?.trim();
  if (!key || !model || !baseUrl) throw new Error("Luna chưa được cấu hình.");
  baseUrl = baseUrl.replace(/\/+$/, "");

  const budget = Math.min(
    Number(options.lunaTimeoutMs) > 0 ? Number(options.lunaTimeoutMs) : LUNA_REQUEST_TIMEOUT_MS,
    LUNA_REQUEST_TIMEOUT_MS,
    remaining(deadline),
  );
  if (budget < 500) throw new Error("Provider deadline exhausted before Luna fallback.");

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), budget);
  try {
    const response = await fetch(`${baseUrl}/chat/completions`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${key}` },
      signal: controller.signal,
      body: JSON.stringify({
        model,
        messages: [{ role: "user", content: prompt }],
        temperature: options.temperature ?? 0.75,
        max_tokens: Number.isFinite(options.maxOutputTokens) && options.maxOutputTokens > 0
          ? Math.floor(options.maxOutputTokens)
          : 1800,
        stream: false,
      }),
    });
    if (!response.ok) {
      const error = new Error(`Luna HTTP ${response.status}`);
      error.status = response.status;
      throw error;
    }
    const data = await response.json();
    const text = data?.choices?.[0]?.message?.content?.trim();
    if (!text) throw new Error("Luna không trả nội dung.");
    return text;
  } finally {
    clearTimeout(timeout);
  }
}

async function tryModelAcrossWorkers(prompt, modelSpec, options, deadline, phase) {
  const model = modelSpec.id;
  if (!geminiHostAvailable() || !modelAvailable(model)) return null;

  const workers = orderedWorkers(model, phase);
  let lastError = null;
  for (const worker of workers) {
    if (!geminiHostAvailable() || !modelAvailable(model)) break;
    const reserveLuna = options.allowLuna === false ? 0 : LUNA_RESERVE_MS;
    if (remaining(deadline) <= reserveLuna + 250) break;

    const credential = credentialState(worker.slot);
    const lane = laneState(model, worker.slot);
    if (credential.disabledUntil > now() || lane.cooldownUntil > now()) continue;

    try {
      const text = await requestGemini(worker, modelSpec, prompt, options, deadline, reserveLuna);
      return {
        text,
        provider: "Gemini",
        workerSlot: worker.slot,
        model,
      };
    } catch (error) {
      lastError = error;
      const classification = classifyGeminiFailure(error);
      if (classification.kind === "model") break;
      if (!geminiHostAvailable() || !modelAvailable(model)) break;
    }
  }

  if (lastError) throw lastError;
  return null;
}

async function tryGeminiChain(prompt, options, deadline, phase) {
  let lastError = null;
  for (const modelSpec of modelChainFor(options)) {
    if (!geminiHostAvailable()) break;
    if (!modelAvailable(modelSpec.id)) continue;
    try {
      const result = await tryModelAcrossWorkers(prompt, modelSpec, options, deadline, phase);
      if (result) return result;
    } catch (error) {
      lastError = error;
    }
  }
  if (lastError) throw lastError;
  return null;
}

export async function generateProviderText(prompt, options = {}) {
  let lastError = null;
  const totalMs = Math.min(
    Math.max(Number(options.totalDeadlineMs) || TOTAL_PROVIDER_DEADLINE_MS, 2_000),
    TOTAL_PROVIDER_DEADLINE_MS,
  );
  const deadline = now() + totalMs;
  const excluded = Array.isArray(options.excludeSlots) ? options.excludeSlots : [];

  try {
    const result = await tryGeminiChain(prompt, options, deadline, { excludeSlots: excluded });
    if (result) return result;
  } catch (error) {
    lastError = error;
  }

  if (excluded.length && options.reuseExcluded !== false && remaining(deadline) > (options.allowLuna === false ? 250 : LUNA_RESERVE_MS + 250)) {
    try {
      const result = await tryGeminiChain(prompt, options, deadline, { onlySlots: excluded });
      if (result) return result;
    } catch (error) {
      lastError = error;
    }
  }

  if (options.allowLuna !== false && remaining(deadline) >= 500) {
    try {
      const text = await requestLuna(prompt, options, deadline);
      return {
        text,
        provider: "Luna",
        workerSlot: null,
        model: process.env.LUNA_MODEL || "Luna",
      };
    } catch (error) {
      lastError = error;
    }
  }

  throw lastError || new Error("Không có AI provider khả dụng trong deadline.");
}

export function providerHealthSnapshot() {
  const primaryModel = WRITER_MODELS[0].id;
  return [1, 2, 3, 4, 5].map((slot) => {
    const credential = credentialState(slot);
    const primaryLane = laneState(primaryModel, slot);
    const perModel = Object.fromEntries(
      WRITER_MODELS.map(({ id }) => {
        const lane = laneState(id, slot);
        return [id, {
          failures: lane.failures,
          successes: lane.successes,
          latencyEma: lane.latencyEma,
          inFlight: lane.inFlight,
          coolingDown: lane.cooldownUntil > now(),
          lastFailureClass: lane.lastFailureClass,
          lastStatus: lane.lastStatus,
        }];
      }),
    );
    return {
      slot,
      configured: Boolean(process.env[`GEMINI_API_KEY_${slot}`]?.trim()),
      failures: primaryLane.failures,
      latencyEma: primaryLane.latencyEma,
      inFlight: primaryLane.inFlight,
      coolingDown: primaryLane.cooldownUntil > now(),
      disabled: credential.disabledUntil > now(),
      perModel,
    };
  });
}

export function modelHealthSnapshot() {
  return [...new Set([...WRITER_MODELS, ...AUDITOR_MODELS].map((entry) => entry.id))].map((model) => {
    const health = modelState(model);
    return {
      model,
      circuitOpen: health.circuitUntil > now(),
      circuitUntil: health.circuitUntil,
      circuitReason: health.circuitReason,
      successes: health.successes,
      failures: health.failures,
    };
  });
}

export function configuredGeminiCount() {
  return configuredGeminiWorkers().length;
}

export function resetProviderHealthForTests() {
  credentialHealth.clear();
  laneHealth.clear();
  modelHealth.clear();
  geminiHostCircuitUntil = 0;
  selectionCounter = 0;
}

export const PROVIDER_LIMITS = Object.freeze({
  writerModels: WRITER_MODELS.map((entry) => ({ ...entry })),
  auditorModels: AUDITOR_MODELS.map((entry) => ({ ...entry })),
  lunaTimeoutMs: LUNA_REQUEST_TIMEOUT_MS,
  totalDeadlineMs: TOTAL_PROVIDER_DEADLINE_MS,
  lunaReserveMs: LUNA_RESERVE_MS,
  transientCircuitSlots: DISTINCT_TRANSIENT_SLOTS_TO_OPEN,
  transportCircuitSlots: DISTINCT_TRANSPORT_SLOTS_TO_OPEN,
});
