from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"

main = MAIN.read_text(encoding="utf-8")
index = INDEX.read_text(encoding="utf-8")


def sub_once(pattern: str, replacement, text: str, label: str, flags=0) -> str:
    out, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise RuntimeError(f"{label}: expected 1 match, found {count}")
    return out


# Remove every Gemini model constant and secret accessor from the Java source that is compiled.
main = re.sub(r'^  private static final String GEMINI_MODEL = .*?;\n', '', main, flags=re.M)
main = re.sub(r'^  private static final String\[\] GEMINI_IMAGE_MODELS = .*?;\n', '', main, flags=re.M)
main = re.sub(r'^  private static final String GEMINI_IMAGE_MODEL = .*?;\n', '', main, flags=re.M)
main = re.sub(
    r'  private String\[\] geminiKeys\(\) \{.*?\n  \}\n\n',
    '',
    main,
    flags=re.S,
)
main = re.sub(
    r'  private String geminiText\(String prompt\) throws Exception \{.*?\n  \}\n\n(?=  private boolean networkFailure)',
    '',
    main,
    flags=re.S,
)

# Own the image model list here instead of depending on a previous patch's class shape.
main = re.sub(r'^  private static final String\[\] OPENAI_IMAGE_MODELS = .*?;\n', '', main, flags=re.M)
anchor = '  private static final String OPENAI_MODEL = "gpt-5.4-mini";\n'
if main.count(anchor) != 1:
    raise RuntimeError("OPENAI_MODEL anchor not found exactly once")
main = main.replace(
    anchor,
    anchor + '  private static final String[] OPENAI_IMAGE_MODELS = {"gpt-image-2", "gpt-image-1.5", "gpt-image-1-mini"};\n',
    1,
)

# Final text order: Qwen first, then GPT. There is no third provider.
new_generate = r'''  private String generateText(String prompt) throws Exception {
    emit("backroomProvider", "Qwen");
    Exception qwenFailure;
    try {
      return qwenText(prompt);
    } catch (Exception error) {
      qwenFailure = error;
    }

    int qwenCode = qwenFailure instanceof HttpError ? ((HttpError)qwenFailure).status : 0;
    if (!networkFailure(qwenFailure) && (qwenCode == 0 || retryable(qwenCode))) {
      try { Thread.sleep(350); } catch (InterruptedException ignored) {}
      try {
        return qwenText(prompt);
      } catch (Exception secondFailure) {
        qwenFailure = secondFailure;
      }
    }

    emit("backroomProvider", "GPT");
    Exception gptFailure;
    try {
      return openAiText(prompt);
    } catch (Exception error) {
      gptFailure = error;
    }

    int gptCode = gptFailure instanceof HttpError ? ((HttpError)gptFailure).status : 0;
    if (!networkFailure(gptFailure) && (gptCode == 0 || retryable(gptCode))) {
      try { Thread.sleep(350); } catch (InterruptedException ignored) {}
      try {
        return openAiText(prompt);
      } catch (Exception secondFailure) {
        gptFailure = secondFailure;
      }
    }

    if (networkFailure(qwenFailure) && networkFailure(gptFailure)) {
      throw new Exception(networkFailureMessage());
    }
    String qwenMessage = qwenFailure != null && qwenFailure.getMessage() != null ? qwenFailure.getMessage() : "Qwen không khả dụng";
    String gptMessage = gptFailure != null && gptFailure.getMessage() != null ? gptFailure.getMessage() : "GPT không khả dụng";
    throw new Exception("Qwen: " + qwenMessage + "; GPT fallback: " + gptMessage);
  }

'''
main = sub_once(
    r'  private String generateText\(String prompt\) throws Exception \{.*?\n  \}\n\n(?=  private JSONObject parseModelJson)',
    lambda m: new_generate,
    main,
    "Qwen -> GPT text pipeline",
    flags=re.S,
)

# Replace the whole generated multi-provider image block with a self-contained GPT-only block.
new_image_pipeline = r'''  private SnapshotImage openAiImageModel(String prompt, String model) throws Exception {
    if (BuildConfig.OPENAI_API_KEY == null || BuildConfig.OPENAI_API_KEY.isEmpty()) {
      throw new Exception("GPT chưa có API key.");
    }
    Exception last = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        JSONObject body = new JSONObject()
          .put("model", model)
          .put("prompt", prompt)
          .put("size", "1536x1024");
        if (attempt == 0) body.put("quality", "low").put("output_format", "jpeg");
        JSONObject result = new JSONObject(postJson(
          "https://api.openai.com/v1/images/generations",
          BuildConfig.OPENAI_API_KEY,
          "Authorization",
          body
        ));
        JSONArray data = result.optJSONArray("data");
        JSONObject first = data != null ? data.optJSONObject(0) : null;
        String imageData = first != null ? first.optString("b64_json", "") : "";
        if (imageData.isEmpty()) throw new Exception("GPT Image không trả ảnh.");
        if (imageData.length() > MAX_SNAPSHOT_BASE64) throw new Exception("Snapshot GPT quá lớn để hiển thị trong APK.");
        String mimeType = attempt == 0 ? "image/jpeg" : "image/png";
        return new SnapshotImage(imageData, mimeType, model);
      } catch (Exception e) {
        last = e;
        if (networkFailure(e)) throw e;
        int code = e instanceof HttpError ? ((HttpError)e).status : 0;
        if (code == 429) break;
        if (code == 400 && attempt == 0) continue;
        if (attempt == 0 && (code == 0 || retryable(code))) {
          try { Thread.sleep(400); } catch (InterruptedException ignored) {}
          continue;
        }
        break;
      }
    }
    throw last != null ? last : new Exception("GPT Image không tạo được ảnh.");
  }

  private String compactProviderDetail(Exception error) {
    if (error == null || error.getMessage() == null) return "";
    String message = error.getMessage().replace('\n', ' ').replace('\r', ' ').trim();
    if (message.startsWith("Provider HTTP ")) {
      int colon = message.indexOf(": ");
      if (colon >= 0 && colon + 2 < message.length()) message = message.substring(colon + 2);
    }
    if (message.length() > 180) message = message.substring(0, 180) + "…";
    return message;
  }

  private String friendlyImageFailure(Exception error) {
    if (error == null) return "GPT: không khả dụng";
    if (networkFailure(error)) return "GPT: lỗi mạng/DNS";
    int code = error instanceof HttpError ? ((HttpError)error).status : 0;
    if (code == 429) return "GPT: hết quota hoặc đang bị giới hạn tốc độ";
    if (code == 401) return "GPT: API key không hợp lệ hoặc chưa được cấu hình";
    if (code == 403) return "GPT: API key chưa có quyền dùng model ảnh";
    if (code == 404) return "GPT: model ảnh không khả dụng";
    String message = error.getMessage() == null ? "" : error.getMessage();
    String lower = message.toLowerCase();
    if (code == 400) {
      if (lower.contains("verif")) return "GPT: tổ chức/tài khoản chưa được xác minh để dùng model ảnh";
      if (lower.contains("billing") || lower.contains("credit") || lower.contains("payment")) return "GPT: billing/credit không cho phép tạo ảnh";
      String detail = compactProviderDetail(error);
      return "GPT: HTTP 400" + (detail.isEmpty() ? "" : " - " + detail);
    }
    if (message.contains("chưa có API key")) return "GPT: chưa có API key";
    if (message.contains("quá lớn")) return "GPT: ảnh trả về quá lớn";
    return "GPT: không tạo được ảnh";
  }

  private SnapshotImage snapshotImage(String prompt) throws Exception {
    Exception gptFailure = null;
    for (String model : OPENAI_IMAGE_MODELS) {
      emit("backroomSnapshotProvider", "GPT");
      try {
        return openAiImageModel(prompt, model);
      } catch (Exception e) {
        gptFailure = e;
        if (networkFailure(e)) break;
      }
    }
    throw new Exception(friendlyImageFailure(gptFailure));
  }

'''
main = sub_once(
    r'  private SnapshotImage geminiImageModel\(String prompt, String model\) throws Exception \{.*?\n(?=  private String clipped)',
    lambda m: new_image_pipeline,
    main,
    "GPT-only image pipeline",
    flags=re.S,
)

# Replace requestSnapshotInternal as well, so it matches our own SnapshotImage class exactly.
new_request = r'''  private void requestSnapshotInternal(String stateJson) {
    try {
      JSONObject snapshotState = new JSONObject(stateJson);
      int turn = snapshotState.optInt("turn", 1);
      latestSnapshotTurn.updateAndGet(current -> Math.max(current, turn));
      SnapshotImage image = snapshotImage(snapshotPrompt(snapshotState));
      if (turn != latestSnapshotTurn.get()) return;
      JSONObject payload = new JSONObject()
        .put("turn", turn)
        .put("model", image.model)
        .put("provider", "GPT")
        .put("dataUri", "data:" + image.mimeType + ";base64," + image.data);
      emit("backroomSnapshot", payload.toString());
    } catch (Exception e) {
      try {
        JSONObject state = new JSONObject(stateJson);
        int turn = state.optInt("turn", 1);
        if (turn != latestSnapshotTurn.get()) return;
        JSONObject payload = new JSONObject()
          .put("turn", turn)
          .put("message", e.getMessage() == null ? "Không thể tạo snapshot." : e.getMessage());
        emit("backroomSnapshotError", payload.toString());
      } catch (Exception ignored) {
        emit("backroomSnapshotError", "{\"turn\":0,\"message\":\"Không thể tạo snapshot.\"}");
      }
    }
  }

'''
main = sub_once(
    r'  private void requestSnapshotInternal\(String stateJson\) \{.*?\n  \}\n\n(?=  private void emit)',
    lambda m: new_request,
    main,
    "GPT-only snapshot request",
    flags=re.S,
)

new_snapshot_class = r'''  private static class SnapshotImage {
    final String data;
    final String mimeType;
    final String model;
    SnapshotImage(String data, String mimeType) {
      this(data, mimeType, "GPT Image");
    }
    SnapshotImage(String data, String mimeType, String model) {
      this.data = data;
      this.mimeType = mimeType == null || mimeType.isEmpty() ? "image/jpeg" : mimeType;
      this.model = model == null || model.isEmpty() ? "GPT Image" : model;
    }
  }

'''
main = sub_once(
    r'  private static class SnapshotImage \{.*?\n  \}\n\n(?=  private static class HttpError)',
    lambda m: new_snapshot_class,
    main,
    "self-contained SnapshotImage class",
    flags=re.S,
)

# Remove old branding from UI strings; no Gemini endpoint, model, key or label may survive.
main = main.replace("GEMINI SNAPSHOT", "AI SNAPSHOT")
main = main.replace("Gemini đang tạo snapshot…", "GPT đang tạo snapshot…")
main = main.replace("model:r.model||'Gemini'", "model:r.model||'GPT'")
main = main.replace("(r.model||'Gemini')", "(r.model||'GPT')")
index = index.replace("Gemini", "AI").replace("GEMINI", "AI")

for forbidden in (
    "generativelanguage.googleapis.com",
    "x-goog-api-key",
    "BuildConfig.GEMINI",
    "GEMINI_",
    "geminiText(",
    "geminiKeys(",
    "geminiImage",
):
    if forbidden in main:
        raise RuntimeError(f"Removed provider still present in compiled MainActivity: {forbidden}")

MAIN.write_text(main, encoding="utf-8")
INDEX.write_text(index, encoding="utf-8")
print("Gemini fully removed with compile-safe self-contained runtime. Text: Qwen -> GPT. Snapshot: GPT only.")
