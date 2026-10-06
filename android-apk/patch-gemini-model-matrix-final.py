from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str):
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    text = text.replace(old, new, 1)


field_anchor = "  private volatile int lastGeminiWorker = -1;\n"
field_block = field_anchor + r'''  private final Object geminiMatrixLock = new Object();
  private final long[] geminiCredentialDisabledUntilMatrix = new long[5];
  private final long[][] geminiLaneCooldownUntilMatrix = new long[3][5];
  private final int[][] geminiLaneFailuresMatrix = new int[3][5];
  private final long[][] geminiLaneLatencyMatrix = new long[3][5];
  private final int[][] geminiLaneInFlightMatrix = new int[3][5];
  private final long[] geminiModelCircuitUntilMatrix = new long[3];
  private final int[] geminiModelTransientMaskMatrix = new int[3];
  private long geminiHostCircuitUntilMatrix = 0L;
  private int geminiTransportMaskMatrix = 0;
  private volatile int lastGeminiModel = -1;
'''
replace_once(field_anchor, field_block, "Gemini matrix health fields")

old_leaf_methods = r'''  private String geminiText(String prompt) throws Exception {
    return geminiTextPolicy(prompt, -1, 0.8, 1800, true);
  }

  private String geminiAuditText(String prompt, int excludedIndex) throws Exception {
    return geminiTextPolicy(prompt, excludedIndex, 0.1, 650, false);
  }
'''

new_leaf_methods = r'''  private String[] geminiModelChain() {
    return new String[] {"gemini-3.6-flash", "gemini-3.5-flash", "gemini-3.5-flash-lite"};
  }

  private String geminiModelLabel(int modelIndex) {
    if (modelIndex == 0) return "Gemini 3.6 Flash";
    if (modelIndex == 1) return "Gemini 3.5 Flash";
    if (modelIndex == 2) return "Gemini 3.5 Flash-Lite";
    return "Gemini";
  }

  private String geminiThinkingLevel(int modelIndex) {
    return modelIndex == 2 ? "minimal" : "low";
  }

  private int geminiModelTimeoutMs(int modelIndex) {
    if (modelIndex == 0) return 45000;
    if (modelIndex == 1) return 35000;
    return 25000;
  }

  private boolean geminiModelCircuitOpenMatrix(int modelIndex) {
    synchronized (geminiMatrixLock) {
      return modelIndex < 0 || modelIndex >= 3 || geminiModelCircuitUntilMatrix[modelIndex] > System.currentTimeMillis();
    }
  }

  private long geminiLaneScoreMatrix(int modelIndex, int keyIndex) {
    synchronized (geminiMatrixLock) {
      long now = System.currentTimeMillis();
      if (geminiCredentialDisabledUntilMatrix[keyIndex] > now) return Long.MAX_VALUE;
      if (geminiLaneCooldownUntilMatrix[modelIndex][keyIndex] > now) {
        return 1_000_000L + (geminiLaneCooldownUntilMatrix[modelIndex][keyIndex] - now);
      }
      long latency = geminiLaneLatencyMatrix[modelIndex][keyIndex] > 0 ? geminiLaneLatencyMatrix[modelIndex][keyIndex] : 1500L;
      int rotationBias = ((keyIndex - geminiRotation + 5) % 5) * 5;
      return geminiLaneInFlightMatrix[modelIndex][keyIndex] * 10_000L
        + geminiLaneFailuresMatrix[modelIndex][keyIndex] * 2_000L
        + latency + rotationBias;
    }
  }

  private int chooseGeminiMatrixWorker(String[] keys, int modelIndex, boolean[] attempted, int excludedIndex) {
    synchronized (geminiMatrixLock) {
      long now = System.currentTimeMillis();
      if (geminiHostCircuitUntilMatrix > now || geminiModelCircuitUntilMatrix[modelIndex] > now) return -1;
      int best = -1;
      long bestScore = Long.MAX_VALUE;
      for (int i = 0; i < Math.min(5, keys.length); i++) {
        if (i == excludedIndex || attempted[i] || keys[i] == null || keys[i].trim().isEmpty()) continue;
        if (geminiCredentialDisabledUntilMatrix[i] > now || geminiLaneCooldownUntilMatrix[modelIndex][i] > now) continue;
        long score = geminiLaneScoreMatrix(modelIndex, i);
        if (score < bestScore) { best = i; bestScore = score; }
      }
      if (best >= 0) geminiLaneInFlightMatrix[modelIndex][best] += 1;
      return best;
    }
  }

  private void releaseGeminiMatrixWorker(int modelIndex, int keyIndex) {
    synchronized (geminiMatrixLock) {
      if (modelIndex >= 0 && modelIndex < 3 && keyIndex >= 0 && keyIndex < 5) {
        geminiLaneInFlightMatrix[modelIndex][keyIndex] = Math.max(0, geminiLaneInFlightMatrix[modelIndex][keyIndex] - 1);
      }
    }
  }

  private boolean geminiHostNetworkFailureMatrix(Exception error) {
    Throwable cause = error;
    while (cause != null) {
      if (cause instanceof java.net.SocketTimeoutException) return false;
      if (cause instanceof java.net.UnknownHostException ||
          cause instanceof java.net.ConnectException ||
          cause instanceof java.net.SocketException ||
          cause instanceof java.io.IOException) return true;
      cause = cause.getCause();
    }
    return false;
  }

  private void noteGeminiMatrixSuccess(int modelIndex, int keyIndex, long latency) {
    synchronized (geminiMatrixLock) {
      geminiLaneFailuresMatrix[modelIndex][keyIndex] = Math.max(0, geminiLaneFailuresMatrix[modelIndex][keyIndex] - 1);
      geminiLaneCooldownUntilMatrix[modelIndex][keyIndex] = 0L;
      long oldLatency = geminiLaneLatencyMatrix[modelIndex][keyIndex];
      geminiLaneLatencyMatrix[modelIndex][keyIndex] = oldLatency > 0 ? Math.max(1L, (oldLatency * 7L + latency * 3L) / 10L) : Math.max(1L, latency);
      geminiModelCircuitUntilMatrix[modelIndex] = 0L;
      geminiModelTransientMaskMatrix[modelIndex] = 0;
      geminiTransportMaskMatrix &= ~(1 << keyIndex);
      if (Integer.bitCount(geminiTransportMaskMatrix) < 3) geminiHostCircuitUntilMatrix = 0L;
    }
  }

  private String noteGeminiMatrixFailure(int modelIndex, int keyIndex, Exception error) {
    synchronized (geminiMatrixLock) {
      long now = System.currentTimeMillis();
      int code = error instanceof HttpError ? ((HttpError)error).status : 0;
      boolean transport = geminiHostNetworkFailureMatrix(error);
      geminiLaneFailuresMatrix[modelIndex][keyIndex] += 1;

      if (code == 401 || code == 403) {
        geminiCredentialDisabledUntilMatrix[keyIndex] = Math.max(geminiCredentialDisabledUntilMatrix[keyIndex], now + 30L * 60_000L);
        return "auth";
      }
      if (code == 429) {
        geminiLaneCooldownUntilMatrix[modelIndex][keyIndex] = Math.max(geminiLaneCooldownUntilMatrix[modelIndex][keyIndex], now + 60_000L);
        return "quota";
      }
      if (code == 400 || code == 404) {
        geminiModelCircuitUntilMatrix[modelIndex] = Math.max(geminiModelCircuitUntilMatrix[modelIndex], now + 5L * 60_000L);
        return "model";
      }
      if (transport) {
        geminiLaneCooldownUntilMatrix[modelIndex][keyIndex] = Math.max(geminiLaneCooldownUntilMatrix[modelIndex][keyIndex], now + 5_000L);
        geminiTransportMaskMatrix |= (1 << keyIndex);
        if (Integer.bitCount(geminiTransportMaskMatrix) >= 3) geminiHostCircuitUntilMatrix = Math.max(geminiHostCircuitUntilMatrix, now + 30_000L);
        return "transport";
      }
      if (code == 408 || code == 500 || code == 502 || code == 503 || code == 504 || code == 0) {
        geminiLaneCooldownUntilMatrix[modelIndex][keyIndex] = Math.max(geminiLaneCooldownUntilMatrix[modelIndex][keyIndex], now + 5_000L);
        geminiModelTransientMaskMatrix[modelIndex] |= (1 << keyIndex);
        if (Integer.bitCount(geminiModelTransientMaskMatrix[modelIndex]) >= 5) {
          geminiModelCircuitUntilMatrix[modelIndex] = Math.max(geminiModelCircuitUntilMatrix[modelIndex], now + 45_000L);
        }
        return "transient";
      }
      geminiLaneCooldownUntilMatrix[modelIndex][keyIndex] = Math.max(geminiLaneCooldownUntilMatrix[modelIndex][keyIndex], now + 30_000L);
      return "lane";
    }
  }

  private String postJsonGeminiMatrix(String endpoint, String key, JSONObject payload, int timeoutMs) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(timeoutMs);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty("x-goog-api-key", key);
    try (OutputStream output = connection.getOutputStream()) {
      output.write(payload.toString().getBytes("UTF-8"));
    }
    int status = connection.getResponseCode();
    InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
    StringBuilder body = new StringBuilder();
    if (stream != null) {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
        String line;
        while ((line = reader.readLine()) != null) body.append(line);
      }
    }
    connection.disconnect();
    if (status < 200 || status >= 300) {
      String detail = body.length() > 220 ? body.substring(0, 220) : body.toString();
      throw new HttpError(status, "Gemini HTTP " + status + (detail.isEmpty() ? "" : ": " + detail));
    }
    return body.toString();
  }

  private String geminiMatrixRequest(String prompt, int modelIndex, int keyIndex, int maxOutputTokens, long deadlineMs) throws Exception {
    String[] keys = geminiKeys();
    String[] models = geminiModelChain();
    long remaining = deadlineMs - System.currentTimeMillis();
    if (remaining < 500L) throw new java.net.SocketTimeoutException("Gemini matrix deadline exhausted");
    int timeout = (int)Math.min((long)geminiModelTimeoutMs(modelIndex), remaining);

    JSONObject part = new JSONObject().put("text", prompt);
    JSONObject contents = new JSONObject().put("role", "user").put("parts", new JSONArray().put(part));
    JSONObject config = new JSONObject()
      .put("responseMimeType", "application/json")
      .put("thinkingConfig", new JSONObject().put("thinkingLevel", geminiThinkingLevel(modelIndex)));
    if (maxOutputTokens > 0) config.put("maxOutputTokens", maxOutputTokens);
    JSONObject body = new JSONObject().put("contents", new JSONArray().put(contents)).put("generationConfig", config);

    JSONObject result = new JSONObject(postJsonGeminiMatrix(
      "https://generativelanguage.googleapis.com/v1beta/models/" + models[modelIndex] + ":generateContent",
      keys[keyIndex], body, timeout));
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
    return responseText.toString();
  }

  private String geminiModelMatrixPolicy(String prompt, int[] modelOrder, int excludedKeyIndex, int maxOutputTokens, boolean rememberWorker, long totalBudgetMs) throws Exception {
    String[] keys = geminiKeys();
    Exception last = null;
    long deadlineMs = System.currentTimeMillis() + totalBudgetMs;
    if (rememberWorker) { lastGeminiWorker = -1; lastGeminiModel = -1; }
    synchronized (geminiMatrixLock) { geminiRotation = (geminiRotation + 1) % 5; }

    for (int phase = 0; phase < (excludedKeyIndex >= 0 ? 2 : 1); phase++) {
      int activeExclude = phase == 0 ? excludedKeyIndex : -1;
      boolean onlyExcluded = phase == 1;
      for (int modelPos = 0; modelPos < modelOrder.length; modelPos++) {
        int modelIndex = modelOrder[modelPos];
        synchronized (geminiMatrixLock) {
          if (geminiHostCircuitUntilMatrix > System.currentTimeMillis()) break;
          if (geminiModelCircuitUntilMatrix[modelIndex] > System.currentTimeMillis()) continue;
        }
        boolean[] attempted = new boolean[Math.min(5, keys.length)];
        for (int workerAttempt = 0; workerAttempt < attempted.length; workerAttempt++) {
          if (System.currentTimeMillis() >= deadlineMs) break;
          int keyIndex;
          if (onlyExcluded) {
            keyIndex = excludedKeyIndex;
            if (keyIndex < 0 || keyIndex >= attempted.length || attempted[keyIndex]) break;
            synchronized (geminiMatrixLock) {
              long now = System.currentTimeMillis();
              if (geminiCredentialDisabledUntilMatrix[keyIndex] > now || geminiLaneCooldownUntilMatrix[modelIndex][keyIndex] > now ||
                  geminiModelCircuitUntilMatrix[modelIndex] > now || geminiHostCircuitUntilMatrix > now) break;
              geminiLaneInFlightMatrix[modelIndex][keyIndex] += 1;
            }
          } else {
            keyIndex = chooseGeminiMatrixWorker(keys, modelIndex, attempted, activeExclude);
            if (keyIndex < 0) break;
          }
          attempted[keyIndex] = true;
          long started = System.currentTimeMillis();
          try {
            String result = geminiMatrixRequest(prompt, modelIndex, keyIndex, maxOutputTokens, deadlineMs);
            noteGeminiMatrixSuccess(modelIndex, keyIndex, System.currentTimeMillis() - started);
            if (rememberWorker) { lastGeminiWorker = keyIndex; lastGeminiModel = modelIndex; }
            return result;
          } catch (Exception error) {
            last = error;
            String failureClass = noteGeminiMatrixFailure(modelIndex, keyIndex, error);
            if (failureClass.equals("model")) break;
            synchronized (geminiMatrixLock) {
              if (geminiHostCircuitUntilMatrix > System.currentTimeMillis() || geminiModelCircuitUntilMatrix[modelIndex] > System.currentTimeMillis()) break;
            }
          } finally {
            releaseGeminiMatrixWorker(modelIndex, keyIndex);
          }
        }
      }
    }
    throw last != null ? last : new Exception("Không có Gemini model/key lane khỏe trong APK.");
  }

  private String geminiText(String prompt) throws Exception {
    return geminiModelMatrixPolicy(prompt, new int[] {0, 1, 2}, -1, 1800, true, 120_000L);
  }

  private String geminiAuditText(String prompt, int excludedIndex) throws Exception {
    return geminiModelMatrixPolicy(prompt, new int[] {2, 1}, excludedIndex, 650, false, 60_000L);
  }
'''
replace_once(old_leaf_methods, new_leaf_methods, "Gemini matrix writer/auditor methods")

old_initial_label = '    emit("backroomProvider", "Gemini");\n'
new_initial_label = '    emit("backroomProvider", "Gemini 3.6 Flash");\n'
replace_once(old_initial_label, new_initial_label, "Gemini initial provider label")

old_success_label = '      emit("backroomProvider", "Gemini K" + (lastGeminiWorker + 1));\n'
new_success_label = '      emit("backroomProvider", geminiModelLabel(lastGeminiModel) + " K" + (lastGeminiWorker + 1));\n'
replace_once(old_success_label, new_success_label, "Gemini model/key success label")

for required in [
    '"gemini-3.6-flash", "gemini-3.5-flash", "gemini-3.5-flash-lite"',
    'new int[] {0, 1, 2}',
    'new int[] {2, 1}',
    'geminiLaneCooldownUntilMatrix',
    'geminiCredentialDisabledUntilMatrix',
    'Integer.bitCount(geminiModelTransientMaskMatrix[modelIndex]) >= 5',
    'Integer.bitCount(geminiTransportMaskMatrix) >= 3',
    'cause instanceof java.net.SocketTimeoutException) return false',
    'geminiTransportMaskMatrix &= ~(1 << keyIndex)',
    'return 45000',
    'true, 120_000L',
    'code == 400 || code == 404',
    'code == 429',
    'geminiModelLabel(lastGeminiModel) + " K"',
    'geminiModelMatrixPolicy(prompt, new int[] {0, 1, 2}',
]:
    if required not in text:
        raise RuntimeError(f"Gemini model matrix marker missing: {required}")

MAIN.write_text(text, encoding="utf-8")
print("Android Gemini matrix enabled: Writer 3.6 -> 3.5 -> Lite across five keys; Auditor Lite -> 3.5; classified circuit breaking before Luna.")
