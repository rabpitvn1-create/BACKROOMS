"""Debug-gated native Companion interaction seam installed after the 46-patch runtime.

The shipped legacy mode remains available until all companion intents have
native atomic writers. This explicitly does NOT treat GM prose as actor agency.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
HTML = ROOT / "app/src/main/assets/index.html"


def replace_one(src: str, old: str, new: str, label: str) -> str:
    count = src.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one generated anchor, got {count}")
    return src.replace(old, new, 1)


java = MAIN.read_text(encoding="utf-8")
html = HTML.read_text(encoding="utf-8")
marker = "COMPANION_NATIVE_INTERACT_PREVIEW_R01"
if marker in java or 'src="companion-interact.js"' in html:
    raise RuntimeError("Companion interaction bridge was applied twice")

# These methods are injected INSIDE GameBridge, never in the unpatched base
# MainActivity. Companions use native SQLite as sole state authority: no
# submitAction(), GameCoreFacade save, or WebView caller-provided state.
bridge = r'''  // COMPANION_NATIVE_INTERACT_PREVIEW_R01: native slot/decision only.
    @JavascriptInterface public boolean companionPreviewAvailable() {
      // Non-WAIT actor intents are not qualified for production publication.
      return BuildConfig.DEBUG;
    }

    @JavascriptInterface public void companionNewGame() {
      if (!BuildConfig.DEBUG) {
        emit("backroomCompanionError", "Chưa đủ điều kiện kích hoạt Companion.");
        return;
      }
      io.execute(() -> {
        try {
          String publicProjection = com.rabpit.backroom.core.companion.CompanionAndroidBridge.createNewGame(MainActivity.this);
          emit("backroomCompanionTurn", publicProjection);
        } catch (Exception error) {
          emit("backroomCompanionError", error.getMessage() == null ? "Không thể tạo Companion New Game." : error.getMessage());
        }
      });
    }

    @JavascriptInterface public void companionOpen(String slotId) {
      if (!BuildConfig.DEBUG) return;
      io.execute(() -> {
        try {
          String publicProjection = com.rabpit.backroom.core.companion.CompanionAndroidBridge.open(MainActivity.this, slotId);
          emit("backroomCompanionTurn", publicProjection);
        } catch (Exception error) {
          emit("backroomCompanionError", error.getMessage() == null ? "Companion slot không hợp lệ." : error.getMessage());
        }
      });
    }

    @JavascriptInterface public void companionSubmit(String slotId, String exactPlayerInput, String requestAlias) {
      if (!BuildConfig.DEBUG) {
        emit("backroomCompanionError", "Companion chưa được kích hoạt ở bản phát hành.");
        return;
      }
      if (exactPlayerInput == null || exactPlayerInput.length() > 500 ||
          requestAlias == null || requestAlias.length() > 96) {
        emit("backroomCompanionError", "INTERACT payload vượt giới hạn.");
        return;
      }
      io.execute(() -> {
        try {
          String result = com.rabpit.backroom.core.companion.CompanionAndroidBridge.submit(
            MainActivity.this, slotId, exactPlayerInput, requestAlias,
            prompt -> {
              // Reuse the shipped provider pool. No provider result becomes a Core command.
              // A separately bounded native decision gate authorizes any proposed intent.
              try {
                JSONObject envelope = parseModelJson(generateText(prompt +
                "\nChọn intent độc lập theo context, không mặc định WAIT. " +
                "Các lựa chọn native có thể commit: SEARCH, MOVE, INSPECT, WAIT. " +
                "TALK chỉ khi có người nghe thật; tự kiểm tra legalTargetIds trong private context. " +
                "Trả một JSON object có reply không rỗng, ops là [], " +
                "actorDecision là object gồm intent, targetId, itemId (null nếu không sử dụng). " +
                "Ví dụ về CẤU TRÚC, không phải quyết định: " +
                "{\"reply\":\"Tôi đã cân nhắc điều kiện hiện tại.\",\"ops\":[]," +
                "\"actorDecision\":{\"intent\":\"SEARCH\",\"targetId\":null,\"itemId\":null}}." +
                " Tuyệt đối không sáng tác mục tiêu hoặc vật phẩm."));
              JSONObject decision = envelope.optJSONObject("actorDecision");
              if (decision == null) throw new Exception("AI không trả quyết định Cao Minh.");
                return decision.toString();
              } catch (Exception error) {
                throw new IllegalStateException("companion_actor_provider_failed", error);
              }
            },
            prompt -> {
              // Semantic/canon audit MUST be an independent provider response.
              // A missing/invalid verdict fails closed; it is not a local PASS flag.
              try {
                JSONObject verdict = parseModelJson(geminiAuditText(prompt +
                "\nTrả đúng JSON {\"verdict\":\"PASS\"} hoặc {\"verdict\":\"HARD\"}.", -1));
                return verdict.toString();
              } catch (Exception error) {
                throw new IllegalStateException("companion_audit_provider_failed", error);
              }
            },
            bound -> GAME_RNG.nextInt(bound)
          );
          emit("backroomCompanionTurn", result);
        } catch (Exception error) {
          emit("backroomCompanionError", error.getMessage() == null ? "Ý chí Cao Minh chưa commit được lượt." : error.getMessage());
        }
      });
    }

'''
java = replace_one(java, "  private class GameBridge {\n",
    "  private class GameBridge {\n" + bridge, "native interaction bridge")
html = replace_one(html, "</body>",
    '<script src="companion-interact.js"></script>\n</body>', "preview interaction asset")
for token in (
    marker, "companionNewGame()", "companionSubmit(String slotId",
    "CompanionAndroidBridge.submit(", "geminiAuditText(", "GAME_RNG.nextInt(bound)",
    "return BuildConfig.DEBUG;"):
    if token not in java:
        raise RuntimeError("native interaction contract missing: " + token)
MAIN.write_text(java, encoding="utf-8")
HTML.write_text(html, encoding="utf-8")
print("Companion native decision preview installed; non-WAIT and production remain fail-closed.")
