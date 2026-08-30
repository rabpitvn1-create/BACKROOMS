from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

html = INDEX.read_text(encoding="utf-8")
java = MAIN.read_text(encoding="utf-8")

# Keep one authoritative calculation for gameplay and HUD. The current runtime uses
# threshold-based exit discovery, a six-turn progression gate and An Nhiên's +2%
# follower bonus, so a hard-coded HUD percentage would eventually lie to the player.
effective_helper = r'''  private int effectiveExitThresholdAndroid(JSONObject state) {
    int threshold = exitThresholdAndroid(state);
    if (anNhienFollowing(state)) threshold = Math.min(10000, threshold + 200);
    return threshold;
  }

  private double currentLevelEscapeChancePercent(JSONObject state) {
    if (!progressionReady(state)) return 0.0;
    JSONObject flags = state.optJSONObject("flags");
    JSONObject exploration = flags != null ? flags.optJSONObject("exploration") : null;
    String confirmedExit = exploration != null ? exploration.optString("confirmedExit", "") : "";
    if (confirmedExit != null && !confirmedExit.trim().isEmpty()) return 100.0;
    return Math.max(0.0, Math.min(100.0, effectiveExitThresholdAndroid(state) / 100.0));
  }

'''

if "private int effectiveExitThresholdAndroid(JSONObject state)" not in java:
    old_threshold = '''    int exitThreshold = exitThresholdAndroid(state);
    if (anNhienFollowing) exitThreshold = Math.min(10000, exitThreshold + 200);
'''
    new_threshold = '''    int exitThreshold = effectiveExitThresholdAndroid(state);
'''
    if java.count(old_threshold) != 1:
        raise RuntimeError(
            f"Escape HUD expected one final exit-threshold block, found {java.count(old_threshold)}"
        )
    java = java.replace(old_threshold, new_threshold, 1)

    bridge_anchor = "  private class GameBridge {\n"
    if java.count(bridge_anchor) != 1:
        raise RuntimeError(
            f"Escape HUD GameBridge anchor expected exactly 1 match, found {java.count(bridge_anchor)}"
        )
    java = java.replace(bridge_anchor, effective_helper + bridge_anchor, 1)

bridge_method = r'''    @JavascriptInterface public double getEscapeChancePercent(String stateJson) {
      try {
        return currentLevelEscapeChancePercent(new JSONObject(stateJson));
      } catch (Exception ignored) {
        return 0.0;
      }
    }

'''
if "getEscapeChancePercent(String stateJson)" not in java:
    bridge_anchor = "  private class GameBridge {\n"
    if java.count(bridge_anchor) != 1:
        raise RuntimeError("Escape HUD GameBridge method anchor missing")
    java = java.replace(bridge_anchor, bridge_anchor + bridge_method, 1)

old_turn_pattern = re.compile(
    r'<div class="turn">\s*TURN\s*<strong id="turn"></strong>\s*</div>'
)
new_turn = (
    '<div class="turn"><div>TURN <strong id="turn"></strong></div>'
    '<div id="escapeChance">ESCAPE: 0%</div></div>'
)
if 'id="escapeChance"' not in html:
    html, count = old_turn_pattern.subn(new_turn, html, count=1)
    if count != 1:
        raise RuntimeError(f"Escape HUD turn anchor expected exactly 1 match, found {count}")

marker = "// ESCAPE_CHANCE_HUD_R02"
if marker not in html:
    render_anchor = "function render(){"
    if html.count(render_anchor) != 1:
        raise RuntimeError(
            f"Escape HUD render anchor expected exactly 1 match, found {html.count(render_anchor)}"
        )
    helper = r'''// ESCAPE_CHANCE_HUD_R02
function escapeChancePercent(){
  try{
    if(window.Android&&typeof window.Android.getEscapeChancePercent==="function"){
      const value=Number(window.Android.getEscapeChancePercent(JSON.stringify(state)));
      if(Number.isFinite(value))return Math.max(0,Math.min(100,value));
    }
  }catch(ignore){}
  return 0;
}
function formatEscapeChance(value){
  const rounded=Math.round(Number(value||0)*10000)/10000;
  return String(rounded);
}
function renderEscapeChance(){
  const el=byId("escapeChance");
  if(el)el.textContent="ESCAPE: "+formatEscapeChance(escapeChancePercent())+"%";
}
'''
    html = html.replace(render_anchor, helper + render_anchor, 1)

render_start = "function render(){"
render_with_escape = "function render(){renderEscapeChance();"
if render_with_escape not in html:
    if html.count(render_start) != 1:
        raise RuntimeError(
            f"Escape HUD render start expected exactly 1 match, found {html.count(render_start)}"
        )
    html = html.replace(render_start, render_with_escape, 1)

# Contracts: the label lives inside `.turn`, so it inherits the same font, size,
# weight/color inheritance and letter-spacing as TURN. No separate escape font CSS.
for contract in (
    'id="escapeChance"',
    marker,
    'window.Android.getEscapeChancePercent(JSON.stringify(state))',
    'function render(){renderEscapeChance();',
    '<div class="turn"><div>TURN <strong id="turn"></strong></div><div id="escapeChance">',
):
    if contract not in html:
        raise RuntimeError(f"Escape HUD HTML contract missing: {contract}")

for contract in (
    'private int effectiveExitThresholdAndroid(JSONObject state)',
    'if (anNhienFollowing(state)) threshold = Math.min(10000, threshold + 200);',
    'if (!progressionReady(state)) return 0.0;',
    'getEscapeChancePercent(String stateJson)',
    'int exitThreshold = effectiveExitThresholdAndroid(state);',
):
    if contract not in java:
        raise RuntimeError(f"Escape HUD Android contract missing: {contract}")

if html.count('id="escapeChance"') != 1:
    raise RuntimeError("Escape HUD must contain exactly one escapeChance element")
if "ESCAPE_CHANCE_HUD_R01" in html:
    raise RuntimeError("Obsolete Escape HUD R01 survived")

INDEX.write_text(html, encoding="utf-8")
MAIN.write_text(java, encoding="utf-8")
print(
    "Escape HUD R02 applied: ESCAPE inherits TURN typography and reads the authoritative current level-transition chance from Android."
)
