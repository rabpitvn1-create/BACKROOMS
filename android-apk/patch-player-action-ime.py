from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
java = MAIN.read_text(encoding="utf-8")
html = INDEX.read_text(encoding="utf-8")

MARKER = "PLAYER_ACTION_IME_FIX_R01"
if MARKER in java or MARKER in html:
    raise RuntimeError("Player Action keyboard fix already applied")

def replace_one(source, old, new, label):
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected 1 anchor, found {count}")
    return source.replace(old, new, 1)

# This script MUST run after patch-character-detail-avatar-fallback.py
# (immersive mode) and patch-ui-1.1.99-shell.py (generated Action modal).
if "private void applyImmersiveFullscreen()" not in java:
    raise RuntimeError("Immersive Android runtime patch must run first")
if 'id="playerActionModal"' not in html or "window.backroomSetImeInset" not in html:
    raise RuntimeError("Player Action shell must be generated before IME fix")

java = replace_one(java,
    "  private WebView webView;\n",
    "  private WebView webView;\n  private int actionImeInsetPx = 0; // PLAYER_ACTION_IME_FIX_R01\n",
    "IME state")
java = replace_one(java,
    "            installUiEnhancements();\n",
    "            installUiEnhancements();\n            notifyActionImeInset();\n",
    "WebView ready callback")
java = replace_one(java,
    "      setContentView(webView);\n      webView.loadUrl",
    """      setContentView(webView);
    // Fullscreen edge-to-edge layouts do not reliably shrink with the IME.
    // Receive physical keyboard insets and forward them to the HTML popup.
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      webView.setOnApplyWindowInsetsListener((view, insets) -> {
        int ime = insets.getInsets(WindowInsets.Type.ime()).bottom;
        int navigation = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
        int covered = Math.max(0, ime - navigation);
        if (covered != actionImeInsetPx) {
          actionImeInsetPx = covered;
          notifyActionImeInset();
        }
        return insets;
      });
      webView.requestApplyInsets();
    }
    webView.loadUrl""",
    "native window inset listener")
java = replace_one(java,
    "  @Override protected void onDestroy() {\n",
    """  private void notifyActionImeInset() {
    if (webView == null) return;
    webView.post(() -> {
      if (webView != null && !isFinishing()) {
        webView.evaluateJavascript(
            "if(window.backroomSetImeInset)window.backroomSetImeInset(" + actionImeInsetPx + ");",
            null);
      }
    });
  }

  @Override protected void onDestroy() {
""",
    "native to HTML IME notification")

# The authoritative input-length gate runs before any non-combat interception.
# In-combat actions intentionally bypass this check and never affect exit progress.
java = replace_one(java,
    '          if (!traverseTurn) {\n            if (requireGameCore().blocksTextItemAction(action)) {',
    '''          if (!traverseTurn) {
            JSONObject inputStateForLength = new JSONObject(stateJson);
            boolean combatTurnForLength = com.rabpit.backroom.core.CombatChoiceEngine.isActive(inputStateForLength);
            if (!combatTurnForLength && !isMetaAction(action)
                && !com.rabpit.backroom.core.ExitStreakEngine.hasMinimumInput(action)) {
              emit("backroomError", "Hành động không hợp lệ: Nội dung phải có ít nhất 15 ký tự.");
              return;
            }
            if (requireGameCore().blocksTextItemAction(action)) {''',
    "ordinary action minimum length / combat exclusion")

# SEARCH and EXPLORE are buttons, not typed input: submit canonical full-length
# descriptions so the same 15-code-point gate applies consistently.
html = replace_one(html,
    'window.Android.submitAction(JSON.stringify(state),kind,label);',
    'window.Android.submitAction(JSON.stringify(state),kind,kind==="SEARCH"?"Tôi tìm kiếm trong khu vực hiện tại.":"Tôi khám phá khu vực xung quanh.");',
    "typed macro actions satisfy minimum length")

# The modal must be bounded to *visible* space, not full app height.
old_fit = """  function fitActionModal(){
    if(!actionModal||actionModal.hidden)return;
    var vv=window.visualViewport,layout=layoutHeight(),visual=Math.max(1,vv&&vv.height?vv.height:layout),top=Math.max(0,vv&&vv.offsetTop?vv.offsetTop:0);
    if(imeInsetCss<=0)modalBaselineHeight=Math.max(modalBaselineHeight,layout,visual);
    var baseline=Math.max(modalBaselineHeight,layout,visual),usable=imeInsetCss>0?Math.min(visual,Math.max(1,baseline-imeInsetCss)):visual;
    actionModal.style.top=top+"px";actionModal.style.height=Math.max(1,usable)+"px";
  }"""
new_fit = """  function fitActionModal(){
    if(!actionModal||actionModal.hidden)return;
    var vv=window.visualViewport,layout=layoutHeight(),visual=Math.max(1,vv&&vv.height?vv.height:layout),top=Math.max(0,vv&&vv.offsetTop?vv.offsetTop:0);
    if(imeInsetCss<=0)modalBaselineHeight=Math.max(modalBaselineHeight,layout,visual+top);
    var baseline=Math.max(modalBaselineHeight,layout,visual+top);
    // On immersive Android WebView visualViewport may remain full-screen while
    // the keyboard overlays it. Use the native IME inset as the second bound.
    var nativeVisible=imeInsetCss>0?Math.max(1,baseline-imeInsetCss-top):visual;
    var usable=Math.max(1,Math.min(visual,nativeVisible));
    actionModal.style.top=top+"px";
    actionModal.style.height=usable+"px";
    actionModal.style.setProperty("--action-visible-height",usable+"px");
  }"""
html = replace_one(html, old_fit, new_fit, "visualViewport/IME fit")
old_css = "/* UI_119_BACKPORT_R01 */"
if old_css not in html:
    raise RuntimeError("1.1.99 styling not present")
fix_css = """/* PLAYER_ACTION_IME_FIX_R01: Action sheet stays above Android keyboard. */
.player-action-modal{bottom:auto!important;overflow:hidden}
.player-action-sheet{
  box-sizing:border-box;
  max-height:min(calc(var(--action-visible-height,100dvh) - 8px),760px)!important;
  overflow-y:auto;
  -webkit-overflow-scrolling:touch;
}
.player-action-sheet .player-action-head{position:sticky;top:0;z-index:3;background:#0e1114}
.player-action-sheet textarea{
  min-height:56px!important;
  height:min(150px,max(56px,calc(var(--action-visible-height,100dvh) - 165px)));
  max-height:max(56px,calc(var(--action-visible-height,100dvh) - 150px))!important;
}
.player-action-sheet .composer-actions{
  position:sticky;
  bottom:0;
  z-index:2;
  background:#0e1114;
  padding:6px 0 max(6px,env(safe-area-inset-bottom));
}
"""
if "</style>" not in html:
    raise RuntimeError("Original app stylesheet is missing")
html = html.replace("</style>", fix_css + "\n</style>", 1)

for marker in ["ExitStreakEngine.hasMinimumInput(action)", "combatTurnForLength",
               "WindowInsets.Type.ime()", "notifyActionImeInset();",
               "SOFT_INPUT_ADJUST_RESIZE", "actionImeInsetPx"]:
    if marker not in java:
        raise RuntimeError(f"Missing native IME contract: {marker}")
for marker in ["PLAYER_ACTION_IME_FIX_R01", "--action-visible-height",
               "nativeVisible", 'id="playerActionConfirm"']:
    if marker not in html:
        raise RuntimeError(f"Missing Action sheet IME contract: {marker}")

MAIN.write_text(java, encoding="utf-8")
INDEX.write_text(html, encoding="utf-8")
print("Player Action modal now uses Android IME insets and visible-height-safe sizing.")
