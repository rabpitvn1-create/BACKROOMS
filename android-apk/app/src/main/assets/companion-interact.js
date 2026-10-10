/* Native Cao Minh game host. Core/SQLite owns all turns; UI renders receipts only. */
(function () {
  "use strict";
  if (window.__companionInteractPreviewInstalled) return;
  window.__companionInteractPreviewInstalled = true;
  if (!window.Android || typeof window.Android.companionPreviewAvailable !== "function" ||
      !window.Android.companionPreviewAvailable()) return;

  var SLOT_KEY = "backroom-companion-native-slot";
  var PENDING_KEY = "backroom-companion-native-pending";
  var currentSlot = "";
  var busy = false;
  var awaitingNewGame = false;
  var lastRequest = null;
  var shownRevision = -1, suggestedRevision = -1;
  function el(id) { return document.getElementById(id); }
  function safeRead(key) { try { return localStorage.getItem(key) || ""; } catch (_) { return ""; } }
  function safeWrite(key, value) { try { localStorage.setItem(key, value); return true; } catch (_) { return false; } }
  function safeRemove(key) { try { localStorage.removeItem(key); } catch (_) {} }
  function text(node, value) { node.textContent = String(value == null ? "" : value); }
  var css = document.createElement("style");
  css.textContent =
    "body.companion-mode #playerActionBar,body.companion-mode #playerActionModal," +
    "body.companion-mode #primaryActionRow,body.companion-mode #searchActionButton," +
    "body.companion-mode #exploreActionButton,body.companion-mode #submit{" +
    "display:none!important}" +
    "#companionDock{position:fixed;left:0;right:0;bottom:0;z-index:170;" +
    "background:#0c1219;border-top:1px solid #5c6d80;padding:10px 12px " +
    "max(10px,env(safe-area-inset-bottom));display:none}" +
    "body.companion-mode #companionDock{display:block}" +
    "#companionDock textarea{box-sizing:border-box;width:100%;min-height:56px;" +
    "max-height:115px;background:#161c25;color:#f0f4fb;border:1px solid #586578;" +
    "padding:9px;font:inherit;border-radius:8px}" +
    "#companionDock button{margin-top:6px;min-height:42px;padding:8px 18px}" +
    "#companionStart{margin:8px 4px;padding:8px 12px}" +
    "#companionStatus{font-size:12px;line-height:1.5;margin:4px 0;color:#e2d8b8}" +
    "#companionNativeLog{padding:8px 2px 18px;white-space:pre-wrap}" +
    "body.companion-mode #log{display:none!important}" +
    "body.companion-mode #companionNativeLog{display:block}" +
    "#companionNativeLog{display:none}" +
    "body.companion-mode .game-menu-sheet button#saveButton," +
    "body.companion-mode .game-menu-sheet button#loadButton," +
    "body.companion-mode .game-menu-sheet button#newGameButton{" +
    "display:none!important}";
  document.head.appendChild(css);

  var start = document.createElement("button");
  start.type = "button"; start.id = "companionStart";
  text(start, "NEW GAME");
  var parent = el("playerActionBar") || document.querySelector("main") || document.body;
  parent.parentNode.insertBefore(start, parent);
  var narrative = document.createElement("section");
  narrative.id = "companionNativeLog";
  narrative.setAttribute("aria-live", "polite");
  (el("log") || document.body).parentNode.appendChild(narrative);

  var dock = document.createElement("section");
  dock.id = "companionDock";
  var heading = document.createElement("div");
  text(heading, "TƯƠNG TÁC VỚI CAO MINH");
  var status = document.createElement("div");
  status.id = "companionStatus";
  var input = document.createElement("textarea");
  input.id = "companionInput";
  input.setAttribute("aria-label", "Lời nói hoặc gợi ý cho Cao Minh");
  input.placeholder = "Nói chuyện hoặc góp ý. Để trống để Cao Minh tự quyết định.";
  var button = document.createElement("button");
  button.type = "button"; button.id = "companionSend";
  text(button, "TƯƠNG TÁC");
  dock.appendChild(heading);
  dock.appendChild(input);
  dock.appendChild(button);
  dock.appendChild(status);
  document.body.appendChild(dock);

  function statusText(message) { text(status, message); }
  function activate() {
    document.body.classList.add("companion-mode");
    text(start, "NEW GAME");
    statusText("Cao Minh tự quyết định; hành động chỉ có hiệu lực khi Core lưu thành công.");
    var log = el("log");
    if (log && log.parentNode && narrative.parentNode !== log.parentNode)
      log.parentNode.appendChild(narrative);
  }
  function appendLine(who, content) {
    var article = document.createElement("article");
    article.className = "message" + (who === "BẠN" ? " player" : "");
    var label = document.createElement("div");
    label.className = "role"; text(label, who);
    var body = document.createElement("div");
    body.className = "text"; text(body, content);
    article.appendChild(label); article.appendChild(body);
    narrative.appendChild(article);
    narrative.scrollTop = narrative.scrollHeight;
  }
  function project(json) {
    var data = JSON.parse(json);
    if (!data.slotId || !/^[a-f0-9]{32}$/.test(data.slotId))
      throw new Error("Native slot projection invalid");
    var committed = data.committedRevision != null;
    if (committed && !(Number(data.committedRevision) > 0))
      throw new Error("Native receipt revision invalid");
    currentSlot = data.slotId;
    shownRevision = Number(data.revision);
    if (!safeWrite(SLOT_KEY, currentSlot)) throw new Error("Local slot pointer cannot be saved");
    activate();
    narrative.textContent = "";
    appendLine("GAME MASTER", "Level " + (data.stop || "0") +
      ". " + (data.location || "") + ". Cao Minh đang dẫn đường.");
    var events = Array.isArray(data.publicEvents) ? data.publicEvents : [];
    events.forEach(function (event) {
      if (!event || !event.payload) return;
      var p = event.payload, result = "";
      if (event.type === "ACTOR_ACTION_COMPLETED") {
        var verbs = {SEARCH: "tìm kiếm", MOVE: "thăm dò lối đi", INSPECT: "kiểm tra kỹ",
          TALK: "trao đổi với người đang hiện diện"};
        if (p.actor === "cao_minh" && Object.prototype.hasOwnProperty.call(verbs, p.intent))
          result = p.intent === "TALK" && typeof p.utterance === "string"
            ? "Cao Minh nói: “" + p.utterance + "” (" + p.minutes + " phút)."
            : "Cao Minh đã tự chọn " + verbs[p.intent] + " tại " +
              p.location + " (" + p.minutes + " phút).";
      } else if (event.type === "WAIT_COMPLETED")
        result = "Cao Minh đã chờ và quan sát " + p.minutes + " phút tại " + p.location + ".";
      else if (event.type === "EXIT_STREAK_RESOLVED")
        result = p.completed ? "Lối đi được Core xác nhận; cả nhóm di chuyển cùng Cao Minh."
          : "Chưa tìm được đường sang khu vực kế tiếp.";
      else if (event.type === "WORLD_TRANSITION")
        result = "Cả nhóm sang khu vực " + p.target + ".";
      if (result) appendLine("CAO MINH / CORE", result);
    });
    if (committed && lastRequest && lastRequest.slotId === currentSlot) {
      // Project the user's suggestion only AFTER the Core receipt has been read.
      if (!lastRequest.autonomous) appendLine("BẠN", lastRequest.text);
      input.value = "";
      lastRequest = null;
      safeRemove(PENDING_KEY);
    }
    busy = false;
    awaitingNewGame = false;
    button.disabled = false;
    start.disabled = false;
    if (el("turn")) text(el("turn"), data.turn);
    if (el("location")) text(el("location"), data.location);
    statusText("Revision native " + data.revision +
      (committed ? " | Lượt đã commit vào SQLite." : " | Chưa có lượt mới."));
    // Cao Minh may form an independent intention after each verified scene
    // projection; it is NEVER displayed as an already-committed action.
    if (suggestedRevision !== shownRevision &&
        typeof window.Android.companionSuggest === "function" && !data.combatActive) {
      suggestedRevision = shownRevision;
      window.Android.companionSuggest(currentSlot);
    }
  }
  window.backroomCompanionSuggestion = function (raw) {
    try {
      var item=JSON.parse(raw);
      if (item.version !== "companion_suggestion.v1" || item.slotId !== currentSlot ||
          item.actor !== "cao_minh" || item.committed !== false ||
          Number(item.revision) !== shownRevision) return;
      var intents={SEARCH:"tìm kiếm manh mối",MOVE:"thăm dò đường đi",
        INSPECT:"kiểm tra vật thể hoặc khu vực",WAIT:"dừng lại quan sát",
        TALK:"trao đổi với người đang hiện diện",NONE:"chưa chọn hành động"};
      if (!Object.prototype.hasOwnProperty.call(intents,item.intent)) return;
      appendLine("Ý CHÍ CAO MINH (CHƯA THỰC HIỆN)",
        "Cao Minh đang cân nhắc: " + intents[item.intent] + ".");
    } catch (_) {}
  };
  window.backroomCompanionSuggestionError = function () {
    // Suggestion failure is not a gameplay failure; keep the composer usable.
  };

  window.backroomCompanionTurn = function (raw) {
    try { project(raw); }
    catch (error) {
      busy = false; button.disabled = false; start.disabled = false;
      statusText("Không thể xác minh projection: " + error.message);
    }
  };
  window.backroomCompanionError = function (message) {
    busy = false; awaitingNewGame = false;
    button.disabled = false; start.disabled = false;
    var reason = String(message || "Lỗi native");
    // SQLite REJECTED is a terminal alias, unlike DECISION_LOCKED/RESERVED.
    // Retain the player's words but mint a fresh alias for a new decision.
    if (reason.indexOf("companion_retry_new_alias:") === 0) {
      safeRemove(PENDING_KEY);
      lastRequest = null;
      statusText("Chưa commit: " + reason.slice("companion_retry_new_alias:".length).trim() +
        ". Lời đã nhập vẫn còn; lần thử tiếp theo dùng request mới.");
    } else {
      statusText("Chưa commit: " + reason +
        ". Giữ request hiện tại để phục hồi cùng lượt.");
    }
  };

  start.addEventListener("click", function () {
    if (busy || !confirm("Tạo một companion slot New Game riêng, không xóa save cũ?")) return;
    busy = true; awaitingNewGame = true;
    start.disabled = true; button.disabled = true;
    statusText("Đang tạo slot native với persona canon đã kiểm tra...");
    window.Android.companionNewGame();
  });
  button.addEventListener("click", function () {
    if (busy || !currentSlot) return;
    var autonomous = input.value.trim().length === 0;
    var value = autonomous
      ? "Tôi để Cao Minh tự đánh giá tình hình và chủ động lựa chọn hành động tiếp theo."
      : input.value;
    if ([...value.trim()].length < 15 || value.length > 500) {
      statusText("Lời tương tác phải dài ít nhất 15 ký tự, tối đa 500."); return;
    }
    var previous = null;
    try { previous = JSON.parse(safeRead(PENDING_KEY) || "null"); } catch (_) {}
    var alias = previous && previous.slotId === currentSlot && previous.text === value
      ? previous.alias
      : "req-" + Date.now().toString(36) + "-" + Math.random().toString(36).slice(2, 12);
    lastRequest = { slotId: currentSlot, text: value, alias: alias, autonomous: autonomous };
    if (!safeWrite(PENDING_KEY, JSON.stringify(lastRequest))) {
      statusText("Không lưu được request alias để phục hồi; lượt chưa gửi.");return;
    }
    busy = true; button.disabled = true;
    statusText("Đang chờ AI Cao Minh tự quyết định. Chưa có receipt.");
    window.Android.companionSubmit(currentSlot, value, alias);
  });
  // Native Companion is the normal gameplay path, not a hidden preview mode.
  // Existing legacy saves are not altered; fresh native campaign slots use SQLite.
  activate();
  var previousSlot = safeRead(SLOT_KEY);
  if (/^[0-9a-f]{32}$/.test(previousSlot)) {
    busy = true; start.disabled = true; button.disabled = true;
    activate(); statusText("Đang xác minh native slot đã lưu...");
    window.Android.companionOpen(previousSlot);
    var pending = null;
    try { pending = JSON.parse(safeRead(PENDING_KEY) || "null"); } catch (_) {}
    if (pending && pending.slotId === previousSlot && typeof pending.text === "string") {
      input.value = pending.autonomous ? "" : pending.text;
      lastRequest = pending;
    }
  } else {
    // Immediately initialize Level 0 with the verified native Cao Minh slot.
    // No extra preview button, duplicate GM turn or WebView-invented state.
    busy = true; awaitingNewGame = true;
    start.disabled = true; button.disabled = true;
    statusText("Đang khởi tạo Level 0 và ý chí Cao Minh...");
    window.Android.companionNewGame();
  }
})();
