from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
main = MAIN.read_text(encoding="utf-8")


def sub_once(pattern: str, replacement, text: str, label: str, flags=0) -> str:
    out, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise RuntimeError(f"{label}: expected 1 match, found {count}")
    return out


main = sub_once(
    r'  private static final String GEMINI_IMAGE_MODEL = "gemini-3\.1-flash-image";\n',
    '  private static final String[] GEMINI_IMAGE_MODELS = {"gemini-3.1-flash-image", "gemini-3.1-flash-lite-image"};\n'
    '  private static final String[] OPENAI_IMAGE_MODELS = {"gpt-image-2", "gpt-image-1-mini"};\n',
    main,
    "image model families",
)

if main.count("GEMINI SNAPSHOT") != 1:
    raise RuntimeError("snapshot placeholder label not found exactly once")
main = main.replace("GEMINI SNAPSHOT", "AI SNAPSHOT", 1)
if main.count("model:r.model||'Gemini'") != 1:
    raise RuntimeError("snapshot cache model label not found exactly once")
main = main.replace("model:r.model||'Gemini'", "model:r.model||'AI'", 1)
if main.count("(r.model||'Gemini')") != 1:
    raise RuntimeError("snapshot completion model label not found exactly once")
main = main.replace("(r.model||'Gemini')", "(r.model||'AI')", 1)

anchor = '      "window.backroomSnapshot=function(payload){'
if main.count(anchor) != 1:
    raise RuntimeError("snapshot callback anchor not found exactly once")
main = main.replace(
    anchor,
    '      "window.backroomSnapshotProvider=function(provider){var s=document.getElementById(\\\'status\\\');if(s)s.textContent=(provider||\\\'AI\\\')+\\\' đang tạo snapshot…\\\';};" +\n'
    + anchor,
    1,
)

new_pipeline = r'''  private SnapshotImage geminiImageModel(String prompt, String model) throws Exception {
    Exception last = null;
    boolean hasKey = false;
    for (String key : geminiKeys()) {
      if (key == null || key.isEmpty()) continue;
      hasKey = true;
      for (int attempt = 0; attempt < 2; attempt++) {
        try {
          JSONObject input = new JSONObject().put("type", "text").put("text", prompt);
          JSONObject format = new JSONObject()
            .put("type", "image")
            .put("mime_type", "image/jpeg")
            .put("aspect_ratio", "16:9");
          if ("gemini-3.1-flash-image".equals(model)) format.put("image_size", "512");
          else format.put("image_size", "1K");
          JSONObject body = new JSONObject()
            .put("model", model)
            .put("input", new JSONArray().put(input))
            .put("response_format", format);
          JSONObject result = new JSONObject(postJson("https://generativelanguage.googleapis.com/v1beta/interactions", key, "x-goog-api-key", body));
          SnapshotImage image = findSnapshotImage(result);
          if (image == null || image.data.isEmpty()) throw new Exception("Gemini image không trả ảnh.");
          if (image.data.length() > MAX_SNAPSHOT_BASE64) throw new Exception("Snapshot Gemini quá lớn để hiển thị trong APK.");
          return new SnapshotImage(image.data, image.mimeType, model, "Gemini");
        } catch (Exception e) {
          last = e;
          if (networkFailure(e)) throw e;
          int code = e instanceof HttpError ? ((HttpError)e).status : 0;
          if (code == 429) break;
          if (attempt == 0 && (code == 0 || retryable(code))) {
            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
            continue;
          }
          break;
        }
      }
    }
    if (!hasKey) throw new Exception("Gemini chưa có API key.");
    throw last != null ? last : new Exception("Gemini không tạo được ảnh.");
  }

  private SnapshotImage openAiImageModel(String prompt, String model) throws Exception {
    if (BuildConfig.OPENAI_API_KEY == null || BuildConfig.OPENAI_API_KEY.isEmpty()) {
      throw new Exception("GPT chưa có API key.");
    }
    Exception last = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        JSONObject body = new JSONObject()
          .put("model", model)
          .put("prompt", prompt)
          .put("size", "1536x1024")
          .put("quality", "low")
          .put("output_format", "jpeg");
        JSONObject result = new JSONObject(postJson("https://api.openai.com/v1/images/generations", BuildConfig.OPENAI_API_KEY, "Authorization", body));
        JSONArray data = result.optJSONArray("data");
        JSONObject first = data != null ? data.optJSONObject(0) : null;
        String imageData = first != null ? first.optString("b64_json", "") : "";
        if (imageData.isEmpty()) throw new Exception("GPT Image không trả ảnh.");
        if (imageData.length() > MAX_SNAPSHOT_BASE64) throw new Exception("Snapshot GPT quá lớn để hiển thị trong APK.");
        return new SnapshotImage(imageData, "image/jpeg", model, "GPT");
      } catch (Exception e) {
        last = e;
        if (networkFailure(e)) throw e;
        int code = e instanceof HttpError ? ((HttpError)e).status : 0;
        if (code == 429) break;
        if (attempt == 0 && (code == 0 || retryable(code))) {
          try { Thread.sleep(400); } catch (InterruptedException ignored) {}
          continue;
        }
        break;
      }
    }
    throw last != null ? last : new Exception("GPT Image không tạo được ảnh.");
  }

  private String friendlyImageFailure(String provider, Exception error) {
    if (error == null) return provider + ": không khả dụng";
    if (networkFailure(error)) return provider + ": lỗi mạng/DNS";
    int code = error instanceof HttpError ? ((HttpError)error).status : 0;
    if (code == 429) return provider + ": hết quota hoặc đang bị giới hạn tốc độ";
    if (code == 401) return provider + ": API key không hợp lệ hoặc chưa được cấu hình";
    if (code == 403) return provider + ": API key chưa có quyền dùng model ảnh";
    if (code == 404) return provider + ": model ảnh không khả dụng";
    if (code == 400) return provider + ": yêu cầu tạo ảnh bị provider từ chối";
    String message = error.getMessage() == null ? "" : error.getMessage();
    if (message.contains("chưa có API key")) return provider + ": chưa có API key";
    if (message.contains("quá lớn")) return provider + ": ảnh trả về quá lớn";
    return provider + ": không tạo được ảnh";
  }

  private SnapshotImage snapshotImage(String prompt) throws Exception {
    Exception geminiFailure = null;
    for (String model : GEMINI_IMAGE_MODELS) {
      emit("backroomSnapshotProvider", "Gemini");
      try {
        return geminiImageModel(prompt, model);
      } catch (Exception e) {
        geminiFailure = e;
        if (networkFailure(e)) break;
      }
    }

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

    if (networkFailure(geminiFailure) && networkFailure(gptFailure)) {
      throw new Exception(networkFailureMessage());
    }
    throw new Exception(
      friendlyImageFailure("Gemini", geminiFailure) + "; " +
      friendlyImageFailure("GPT", gptFailure)
    );
  }

'''

main = sub_once(
    r'  private SnapshotImage geminiImage\(String prompt\) throws Exception \{.*?\n  \}\n\n(?=  private String clipped)',
    lambda m: new_pipeline,
    main,
    "replace Gemini-only image pipeline",
    flags=re.S,
)

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
        .put("provider", image.provider)
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
    "snapshot provider payload",
    flags=re.S,
)

new_snapshot_class = r'''  private static class SnapshotImage {
    final String data;
    final String mimeType;
    final String model;
    final String provider;
    SnapshotImage(String data, String mimeType) {
      this(data, mimeType, "AI", "AI");
    }
    SnapshotImage(String data, String mimeType, String model, String provider) {
      this.data = data;
      this.mimeType = mimeType == null || mimeType.isEmpty() ? "image/jpeg" : mimeType;
      this.model = model == null || model.isEmpty() ? "AI" : model;
      this.provider = provider == null || provider.isEmpty() ? "AI" : provider;
    }
  }

'''
main = sub_once(
    r'  private static class SnapshotImage \{.*?\n  \}\n\n(?=  private static class HttpError)',
    lambda m: new_snapshot_class,
    main,
    "snapshot metadata class",
    flags=re.S,
)

MAIN.write_text(main, encoding="utf-8")
print("Snapshot fallback patched: Gemini image models -> GPT Image models, DNS-aware.")
