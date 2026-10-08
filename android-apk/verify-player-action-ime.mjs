// Regression contract for the generated Action modal. Run AFTER the APK patch chain.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import vm from "node:vm";

const root = dirname(fileURLToPath(import.meta.url));
const html = readFileSync(join(root, "app/src/main/assets/index.html"), "utf8");
const java = readFileSync(join(root, "app/src/main/java/com/rabpit/backroom/MainActivity.java"), "utf8");

for (const fragment of [
  "PLAYER_ACTION_IME_FIX_R01", "WindowInsets.Type.ime()",
  "WindowInsets.Type.navigationBars()", "SOFT_INPUT_ADJUST_RESIZE",
  "webView.setOnApplyWindowInsetsListener", "notifyActionImeInset()",
  "window.backroomSetImeInset("
]) {
  assert(java.includes(fragment), "Native IME bridge absent: " + fragment);
}
for (const fragment of [
  "PLAYER_ACTION_IME_FIX_R01", "playerActionModal",
  "--action-visible-height", "nativeVisible",
  ".player-action-sheet .composer-actions", "position:sticky"
]) {
  assert(html.includes(fragment), "Popup keyboard-safe style/script absent: " + fragment);
}

// Execute the ACTUAL generated popup script against a deterministic mock viewport.
// Test both Android edge-to-edge IME overlay and a WebView that resizes itself.
const marker = 'var actionModal=document.getElementById("playerActionModal");';
const anchor = html.indexOf(marker);
assert(anchor > 0, "Action modal runtime not found");
const open = html.lastIndexOf("<script>", anchor);
const close = html.indexOf("</script>", anchor);
assert(open >= 0 && close > open, "Action modal script is not enclosed");
const script = html.slice(open + "<script>".length, close);

const elements = new Map();
const events = new Map();
function el(id) {
  if (!elements.has(id)) {
    const style = {
      attrs: {},
      setProperty(name, value) { this.attrs[name] = value; },
      removeProperty(name) { delete this.attrs[name]; }
    };
    elements.set(id, {
      id, hidden: id === "playerActionModal", style, disabled: false, value: "",
      addEventListener(name, fn) { events.set(id + ":" + name, fn); },
      setAttribute() {}, classList: { add() {}, remove() {} }
    });
  }
  return elements.get(id);
}

const viewportHandlers = new Map();
const win = {
  innerHeight: 800, devicePixelRatio: 2,
  visualViewport: {
    height: 800, offsetTop: 0,
    addEventListener(event, fn) { viewportHandlers.set(event, fn); }
  },
  addEventListener() {}
};
const doc = {
  getElementById: (id) => el(id),
  documentElement: { clientHeight: 800 },
  body: { classList: { add() {}, remove() {} } },
  addEventListener() {}
};
vm.runInNewContext(script, {
  window: win, document: doc, state: { combat: { active: false } },
  busy: false, setTimeout() {}
}, { timeout: 1000 });

function visibleHeight() {
  return Number.parseFloat(el("playerActionModal").style.height);
}
const click = events.get("submit:click");
assert.equal(typeof click, "function", "Execute action is not clickable");
click({ preventDefault() {}, stopPropagation() {} });
assert.equal(visibleHeight(), 800, "Popup should be full-height before keyboard");

win.backroomSetImeInset(760); // 760 native physical px = 380 CSS px
assert.equal(visibleHeight(), 420, "Native IME overlay must reduce popup viewport");
assert.equal(el("playerActionModal").style.attrs["--action-visible-height"], "420px");

win.visualViewport.height = 400;
win.visualViewport.offsetTop = 20;
viewportHandlers.get("resize")();
assert.equal(visibleHeight(), 400, "Viewport and native inset must not double-subtract");
assert.equal(el("playerActionModal").style.top, "20px");

win.visualViewport.height = 800;
win.visualViewport.offsetTop = 0;
win.backroomSetImeInset(0);
assert.equal(visibleHeight(), 800, "Closing keyboard must restore popup height");

win.visualViewport.height = 350;
viewportHandlers.get("resize")();
assert.equal(visibleHeight(), 350, "WebView viewport-only resize fallback must work");
console.log("PASS: Action popup remains inside IME-safe visual area on Android.");
