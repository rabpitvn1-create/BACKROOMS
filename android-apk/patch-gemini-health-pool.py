from pathlib import Path


MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


text = MAIN.read_text(encoding="utf-8")

field_anchor = "  private final AtomicInteger latestSnapshotTurn = new AtomicInteger(0);\n"
field_block = field_anchor + r'''  private final Object geminiHealthLock = new Object();
  private final long[] geminiCooldownUntil = new long[5];
  private final int[] geminiFailures = new int[5];
  private final long[] geminiLatencyEma = new long[] {1500, 1500, 1500, 1500, 1500};
  private final int[] geminiInFlight = new int[5];
  private int geminiRotation = 0;
  private volatile int lastGeminiWorker = -1;
'''
text = replace_once(text, field_anchor, field_block, "Gemini health fields")

start = text.index("  private String geminiText(String prompt) throws Exception {\n")
end = text.index("\n  private String generateText(String prompt) throws Exception {", start)
new_method = r'''  private long geminiWorkerScoreUnsafe(int index) {
    long now = System.currentTimeMillis();
    if (index < 0 || index >= geminiCooldownUntil.length) return Long.MAX_VALUE;
    if (geminiCooldownUntil[index] > now) return 1_000_000L + (geminiCooldownUntil[index] - now);
    int rotationBias = ((index - geminiRotation + 5) % 5) * 5;
    return geminiInFlight[index] * 10_000L + geminiFailures[index] * 2_000L + geminiLatencyEma[index] + rotationBias;
  }

  private void noteGeminiSuccess(int index, long latency) {
    synchronized (geminiHealthLock) {
      geminiFailures[index] = Math.max(0, geminiFailures[index] - 1);
      geminiCooldownUntil[index] = 0;
      geminiLatencyEma[index] = Math.max(1, (geminiLatencyEma[index] * 7 + latency * 3) / 10);
    }
  }

  private void noteGeminiFailure(int index, Exception error) {
    synchronized (geminiHealthLock) {
      geminiFailures[index] += 1;
      int code = error instanceof HttpError ? ((HttpError) error).status : 0;
      long now = System.currentTimeMillis();
      if (code == 401 || code == 403) {
        geminiCooldownUntil[index] = now + 30L * 60_000L;
      } else if (code == 429) {
        geminiCooldownUntil[index] = now + 60_000L;
      } else if (retryable(code) || code == 0) {
        geminiCooldownUntil[index] = now + Math.min(30_000L, 2_000L * geminiFailures[index]);
      }
    }
  }

  private int chooseGeminiWorker(String[] keys, boolean[] attempted, int excludedIndex) {
    synchronized (geminiHealthLock) {
      long now = System.currentTimeMillis();
      int best = -1;
      long bestScore = Long.MAX_VALUE;
      for (int i = 0; i < keys.length && i < 5; i++) {
        if (i == excludedIndex || attempted[i] || keys[i] == null || keys[i].trim().isEmpty()) continue;
        if (geminiCooldownUntil[i] > now) continue;
        long score = geminiWorkerScoreUnsafe(i);
        if (score < bestScore) {
          best = i;
          bestScore = score;
        }
      }
      if (best >= 0) geminiInFlight[best] += 1;
      return best;
    }
  }

  private void releaseGeminiWorker(int index) {
    synchronized (geminiHealthLock) {
      if (index >= 0 && index < geminiInFlight.length) geminiInFlight[index] = Math.max(0, geminiInFlight[index] - 1);
    }
  }

  private String geminiTextPolicy(String prompt, int excludedIndex, double temperature, int maxOutputTokens, boolean rememberWorker) throws Exception {
    String[] keys = geminiKeys();
    Exception last = null;
    if (rememberWorker) lastGeminiWorker = -1;
    synchronized (geminiHealthLock) {
      geminiRotation = (geminiRotation + 1) % 5;
    }

    for (int phase = 0; phase < (excludedIndex >= 0 ? 2 : 1); phase++) {
      int activeExclude = phase == 0 ? excludedIndex : -1;
      boolean[] attempted = new boolean[Math.min(5, keys.length)];
      for (int workerAttempt = 0; workerAttempt < attempted.length; workerAttempt++) {
        int index = chooseGeminiWorker(keys, attempted, activeExclude);
        if (index < 0) break;
        attempted[index] = true;
        String key = keys[index];
        try {
          for (int attempt = 0; attempt < 2; attempt++) {
            long started = System.currentTimeMillis();
            try {
              JSONObject part = new JSONObject().put("text", prompt);
              JSONObject contents = new JSONObject().put("role", "user").put("parts", new JSONArray().put(part));
              JSONObject config = new JSONObject()
                .put("responseMimeType", "application/json")
                .put("temperature", temperature);
              if (maxOutputTokens > 0) config.put("maxOutputTokens", maxOutputTokens);
              JSONObject body = new JSONObject().put("contents", new JSONArray().put(contents)).put("generationConfig", config);
              JSONObject result = new JSONObject(postJson(
                "https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent",
                key,
                "x-goog-api-key",
                body
              ));
              JSONArray candidates = result.optJSONArray("candidates");
              StringBuilder responseText = new StringBuilder();
              if (candidates != null) {
                for (int c = 0; c < candidates.length(); c++) {
                  JSONObject candidate = candidates.optJSONObject(c);
                  JSONObject providerContent = candidate != null ? candidate.optJSONObject("content") : null;
                  JSONArray parts = providerContent != null ? providerContent.optJSONArray("parts") : null;
                  if (parts == null) continue;
                  for (int p = 0; p < parts.length(); p++) {
                    JSONObject responsePart = parts.optJSONObject(p);
                    String piece = responsePart != null ? responsePart.optString("text", "").trim() : "";
                    if (!piece.isEmpty()) {
                      if (responseText.length() > 0) responseText.append('\n');
                      responseText.append(piece);
                    }
                  }
                }
              }
              if (responseText.length() == 0) throw new Exception("Gemini không trả nội dung.");
              noteGeminiSuccess(index, System.currentTimeMillis() - started);
              if (rememberWorker) lastGeminiWorker = index;
              return responseText.toString();
            } catch (Exception e) {
              last = e;
              noteGeminiFailure(index, e);
              int code = e instanceof HttpError ? ((HttpError)e).status : 0;
              boolean retry = attempt == 0 && code != 401 && code != 403 && code != 429 && (code == 0 || retryable(code));
              if (retry) {
                try { Thread.sleep(250); } catch (InterruptedException ignored) {}
                continue;
              }
              break;
            }
          }
        } finally {
          releaseGeminiWorker(index);
        }
      }
    }

    throw last != null ? last : new Exception("Không có Gemini worker khỏe trong APK.");
  }

  private String geminiText(String prompt) throws Exception {
    return geminiTextPolicy(prompt, -1, 0.8, 1800, true);
  }

  private String geminiAuditText(String prompt, int excludedIndex) throws Exception {
    return geminiTextPolicy(prompt, excludedIndex, 0.1, 650, false);
  }
'''
text = text[:start] + new_method + text[end:]

MAIN.write_text(text, encoding="utf-8")
print("APK Gemini pool: health-weighted selection, in-flight balancing, writer separation and low-token audit policy enabled.")
