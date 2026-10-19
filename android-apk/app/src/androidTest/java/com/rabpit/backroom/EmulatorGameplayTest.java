package com.rabpit.backroom;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.rabpit.backroom.core.GameCoreFacade;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Drives the REAL packaged game APK on an emulator. Core stages are committed
 * with their factual text to avoid API charges. This is not a Gemini/Gehihi
 * network-quality test and does not claim to solve the whole Level.
 */
@RunWith(AndroidJUnit4.class)
public final class EmulatorGameplayTest {
  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  private final Context context = instrumentation.getTargetContext();
  private final JSONObject report = new JSONObject();
  private WebView webView;
  private Activity activity;
  private File evidence;

  private static WebView findWebView(View root) {
    if (root instanceof WebView) return (WebView) root;
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) {
        WebView found = findWebView(group.getChildAt(i));
        if (found != null) return found;
      }
    }
    return null;
  }

  private String js(String expression) throws Exception {
    CountDownLatch ready = new CountDownLatch(1);
    AtomicReference<String> output = new AtomicReference<>();
    instrumentation.runOnMainSync(() -> webView.evaluateJavascript(expression, value -> {
      output.set(value);
      ready.countDown();
    }));
    assertTrue("Timed out evaluating real WebView", ready.await(15, TimeUnit.SECONDS));
    String encoded = output.get();
    assertNotNull("WebView returned no response", encoded);
    // evaluateJavascript serializes the returned JS string as a JSON string.
    return new JSONArray("[" + encoded + "]").getString(0);
  }

  private JSONObject inspect(String objectExpression) throws Exception {
    return new JSONObject(js("JSON.stringify(" + objectExpression + ")"));
  }

  private AccessibilityNodeInfo findText(AccessibilityNodeInfo node, String needle) {
    if (node == null) return null;
    CharSequence text = node.getText();
    if (text != null && text.toString().contains(needle)) return node;
    CharSequence description = node.getContentDescription();
    if (description != null && description.toString().contains(needle)) return node;
    for (int i = 0; i < node.getChildCount(); i++) {
      AccessibilityNodeInfo found = findText(node.getChild(i), needle);
      if (found != null) return found;
    }
    return null;
  }

  private boolean dismissAndroidDialog(String title, String button) {
    UiAutomation ui = instrumentation.getUiAutomation();
    AccessibilityServiceInfo service = ui.getServiceInfo();
    if (service != null && (service.flags & AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS) == 0) {
      service.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
      ui.setServiceInfo(service);
    }
    for (AccessibilityWindowInfo window : ui.getWindows()) {
      AccessibilityNodeInfo root = window.getRoot();
      if (root == null || findText(root, title) == null) continue;
      AccessibilityNodeInfo control = findText(root, button);
      while (control != null && !control.isClickable()) control = control.getParent();
      if (control == null || !control.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        fail("Android dialog blocks game and cannot be dismissed: " + title);
      return true;
    }
    return false;
  }

  /** The old test passed behind 'Viewing full screen' and launcher ANR dialogs.
   * Screenshots and real taps must NEVER run beneath another Android window. */
  private void requireInteractiveForeground() throws Exception {
    for (int attempt = 0; attempt < 25; attempt++) {
      boolean dismissed = dismissAndroidDialog("Viewing full screen", "Got it");
      dismissed |= dismissAndroidDialog("Pixel Launcher isn't responding", "Close app");
      dismissed |= dismissAndroidDialog("Launcher isn't responding", "Close app");
      if (dismissed) {
        Thread.sleep(400);
        continue;
      }
      AtomicReference<Boolean> focused = new AtomicReference<>(false);
      instrumentation.runOnMainSync(() -> focused.set(activity.hasWindowFocus()));
      AccessibilityNodeInfo active = instrumentation.getUiAutomation().getRootInActiveWindow();
      String owner = active == null || active.getPackageName() == null
        ? "" : active.getPackageName().toString();
      if (focused.get() && context.getPackageName().equals(owner)) return;
      Thread.sleep(400);
    }
    fail("Android emulator has a blocking system window or the game is not foreground");
  }

  /** Injects a real Android touchscreen event, never JavaScript element.click().
   * This makes an inaccessible/covered/disabled game button fail the smoke test. */
  private void click(String expression) throws Exception {
    requireInteractiveForeground();
    JSONObject target = inspect("(()=>{const e=" + expression +
      ";if(!e)return {found:false};e.scrollIntoView({block:'center',inline:'nearest'});" +
      "const r=e.getBoundingClientRect(),x=(r.left+r.right)/2,y=(r.top+r.bottom)/2;" +
      "const top=document.elementFromPoint(x,y);" +
      "return {found:true,enabled:!e.disabled,visible:r.width>0&&r.height>0," +
      "uncovered:top===e||e.contains(top),x,y,w:window.innerWidth,h:window.innerHeight};})()");
    assertTrue("Button missing: " + expression, target.getBoolean("found"));
    assertTrue("Button disabled: " + expression, target.getBoolean("enabled"));
    assertTrue("Button hidden: " + expression, target.getBoolean("visible"));
    assertTrue("Button covered inside WebView: " + expression, target.getBoolean("uncovered"));
    float[] location = new float[2];
    instrumentation.runOnMainSync(() -> {
      int[] xy = new int[2];
      webView.getLocationOnScreen(xy);
      location[0] = xy[0] + (float) (target.optDouble("x") * webView.getWidth() / target.optDouble("w"));
      location[1] = xy[1] + (float) (target.optDouble("y") * webView.getHeight() / target.optDouble("h"));
    });
    long downTime = SystemClock.uptimeMillis();
    UiAutomation ui = instrumentation.getUiAutomation();
    MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN,
      location[0], location[1], 0);
    down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
    assertTrue("Touch-down event rejected", ui.injectInputEvent(down, true));
    down.recycle();
    MotionEvent up = MotionEvent.obtain(downTime, downTime + 85, MotionEvent.ACTION_UP,
      location[0], location[1], 0);
    up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
    assertTrue("Touch-up event rejected", ui.injectInputEvent(up, true));
    up.recycle();
    instrumentation.waitForIdleSync();
  }

  private void until(String expression, int maxSeconds) throws Exception {
    long end = System.currentTimeMillis() + maxSeconds * 1000L;
    while (System.currentTimeMillis() < end) {
      try {
        if (inspect("({ready:!!(" + expression + ")})").optBoolean("ready")) return;
      } catch (Exception ignored) { /* Page may still be booting. */ }
      Thread.sleep(250);
    }
    fail("UI did not reach expected state: " + expression);
  }

  private void screenshot(String name) throws Exception {
    requireInteractiveForeground();
    instrumentation.waitForIdleSync();
    Thread.sleep(350);
    Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
    assertNotNull("Emulator screenshot unavailable: " + name, bitmap);
    File file = new File(evidence, name + ".png");
    try (FileOutputStream output = new FileOutputStream(file)) {
      assertTrue("Cannot encode " + name, bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
    } finally {
      bitmap.recycle();
    }
    assertTrue("Empty screenshot: " + name, file.length() > 10000);
  }

  /** Wait for Android's screenshot compositor, not merely a DOM-open flag.
   * Old CI reported PASS while 01-game-start and 02-fullscreen-map were
   * byte-identical despite the map being logically open. */
  private void screenshotChangedFrom(String previous, String current) throws Exception {
    File baseline = new File(evidence, previous + ".png");
    assertTrue("Missing baseline screenshot: " + previous, baseline.isFile());
    byte[] original = Files.readAllBytes(baseline.toPath());
    for (int retry = 0; retry < 7; retry++) {
      screenshot(current);
      byte[] actual = Files.readAllBytes(new File(evidence, current + ".png").toPath());
      if (!java.util.Arrays.equals(original, actual)) return;
      Thread.sleep(450);
    }
    fail("Screen did not visually change from " + previous + " to " + current +
      "; WebView DOM assertions alone are insufficient");
  }

  private JSONObject coreState(GameCoreFacade core) throws Exception {
    return new JSONObject(core.currentClientState("{}"));
  }

  private JSONObject availableAction(JSONObject scene, String targetPrefix) throws Exception {
    JSONArray actions = scene.getJSONArray("availableActions");
    for (int i = 0; i < actions.length(); i++) {
      JSONObject action = actions.getJSONObject(i);
      if (action.getString("targetId").startsWith(targetPrefix)) return action;
    }
    fail("Expected Core action is missing: " + targetPrefix);
    return null;
  }

  private JSONObject performCoreAction(GameCoreFacade core, JSONObject action, String playerText) throws Exception {
    JSONObject command = new JSONObject(action.toString());
    command.remove("label");
    JSONObject stage = new JSONObject(core.beginSceneAction(command.toString(), playerText));
    assertFalse("Action must be newly staged", stage.getBoolean("committed"));
    JSONObject event = new JSONObject(core.sceneNarrationContext());
    assertTrue("Missing factual result", event.getString("result").length() > 3);
    // Commit only the Core-factual text. Production GM quality is tested separately.
    core.commitSceneAction(action.getString("actionId"), event.getString("result"), "{}");
    JSONObject state = coreState(core);
    js("JSON.stringify((()=>{window.backroomTurn(" + JSONObject.quote(state.toString()) +
      ");return {synced:true};})())");
    return state;
  }

  @Test public void installActualArtifactAndPlayLevel0World() throws Exception {
    evidence = new File(context.getExternalFilesDir(null), "emulator-smoke");
    assertTrue(evidence.exists() || evidence.mkdirs());
    Intent intent = new Intent(Intent.ACTION_MAIN);
    intent.setClassName(context.getPackageName(), "com.rabpit.backroom.MainActivity");
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    activity = instrumentation.startActivitySync(intent);
    assertNotNull(activity);
    webView = findWebView(activity.getWindow().getDecorView());
    assertNotNull("MainActivity must render its real WebView", webView);

    until("document.querySelector('#explorerControls button') && typeof Android !== 'undefined' && typeof state !== 'undefined' && state.scene && state.scene.map", 30);
    requireInteractiveForeground();
    GameCoreFacade core = GameCoreFacade.create(context, false);
    JSONObject initial = coreState(core);
    assertEquals("world-graph-v3", initial.getJSONObject("scene").getString("runtime"));
    assertEquals("0", initial.getJSONObject("scene").getString("nodeId"));
    String initialRoom = initial.getJSONObject("scene").getJSONObject("room").getString("id");
    JSONArray startingInventory = initial.getJSONArray("inventory");
    screenshot("01-game-start");
    report.put("startup", "PASS").put("initialRoom", initialRoom);

    click("Array.from(document.querySelectorAll('#explorerControls button')).find(b=>b.textContent.trim()==='MAP')");
    until("document.querySelector('#explorerChoices')?.open && document.querySelector('#explorerChoices .explorer-map-scroll svg')", 12);
    JSONObject map = inspect("(()=>{const d=document.querySelector('#explorerChoices'),s=d.querySelector('svg');return {open:d.open,full:d.classList.contains('explorer-map-mode'),title:d.querySelector('.explorer-dialog-header strong')?.textContent||'',zone:d.querySelector('.explorer-map-subtitle')?.textContent||'',nodes:s.querySelectorAll('[data-map-node]').length,locked:s.querySelectorAll('[data-map-node=locked]').length,unnamedLocked:s.querySelectorAll('[data-map-node=locked] text').length,position:s.querySelectorAll('[data-map-location=cao-minh]').length,dashed:s.querySelectorAll('.explorer-map-edge-locked').length,scrollable:!!d.querySelector('.explorer-map-scroll')};})()");
    assertTrue(map.getBoolean("open"));
    assertTrue(map.getBoolean("full"));
    assertEquals("LEVEL 0",map.getString("title"));
    assertFalse("Current Canon zone must be shown",map.getString("zone").trim().isEmpty());
    assertEquals(45, map.getInt("nodes"));
    assertEquals(44, map.getInt("locked"));
    assertEquals(0, map.getInt("unnamedLocked"));
    assertEquals(1, map.getInt("position"));
    assertTrue(map.getInt("dashed") > 0);
    assertTrue(map.getBoolean("scrollable"));
    screenshotChangedFrom("01-game-start", "02-fullscreen-map");
    report.put("map", map);

    click("document.querySelector('#explorerChoices button[aria-label=\"Đóng lựa chọn\"]')");
    JSONObject closed = inspect("(()=>{const d=document.querySelector('#explorerChoices');return {closed:!d.open,normal:!d.classList.contains('explorer-map-mode')};})()");
    assertTrue(closed.getBoolean("closed") && closed.getBoolean("normal"));
    click("document.getElementById('gameMenuOpen')");
    until("!document.getElementById('gameMenuModal').hidden", 4);
    click("document.getElementById('explorerCasebook')");
    until("document.querySelector('#explorerChoices')?.open", 5);
    JSONObject casebook = inspect("({title:document.querySelector('#explorerChoices')?.textContent.slice(0,200)||'',menuClosed:document.getElementById('gameMenuModal').hidden})");
    assertTrue(casebook.getString("title").contains("Sổ tay"));
    assertTrue(casebook.getBoolean("menuClosed"));
    screenshot("03-casebook");
    click("Array.from(document.querySelectorAll('#explorerChoices button')).find(b=>b.textContent.includes('Xem hành động Core'))");
    until("document.querySelector('#explorerChoices')?.textContent.includes('Hành động Core')", 5);
    screenshot("04-core-actions");
    report.put("casebook", "PASS");
    click("document.querySelector('#explorerChoices button[aria-label=\"Đóng lựa chọn\"]')");

    JSONObject afterMark = performCoreAction(core, availableAction(initial.getJSONObject("scene"), "mark"), "Cao Minh đánh dấu vị trí.");
    assertEquals(initialRoom, afterMark.getJSONObject("scene").getJSONObject("room").getString("id"));
    assertEquals(startingInventory.toString(), afterMark.getJSONArray("inventory").toString());
    assertFalse("Mark should only be available once", hasAction(afterMark.getJSONObject("scene"), "mark"));
    screenshot("05-marked-room");
    // Genuine Gehihi reply through the SHIPPED Android bridge, not a text stub.
    assertFalse("Real GM test requires GEHIHI_API_KEY configured in APK",
      BuildConfig.GEHIHI_API_KEY.trim().isEmpty());
    JSONObject beforeGm = coreState(core);
    JSONObject gmAction = availableAction(beforeGm.getJSONObject("scene"), "observe");
    int version = beforeGm.getJSONObject("scene").getInt("sceneVersion");
    int logCount = beforeGm.getJSONArray("log").length();
    String beforeRoom = beforeGm.getJSONObject("scene").getJSONObject("room").getString("id");
    JSONObject originalPlayer = beforeGm.getJSONObject("player");
    JSONArray originalItems = beforeGm.getJSONArray("inventory");
    JSONObject command = new JSONObject(gmAction.toString());command.remove("label");
    js("JSON.stringify((()=>{Android.submitSceneAction(" +
      JSONObject.quote(beforeGm.toString()) + "," + JSONObject.quote(command.toString()) +
      ");return {requested:true};})())");
    until("state.scene && state.scene.sceneVersion>" + version, 140);
    JSONObject afterGm = coreState(core);
    assertEquals(version+1,afterGm.getJSONObject("scene").getInt("sceneVersion"));
    assertEquals(beforeRoom,afterGm.getJSONObject("scene").getJSONObject("room").getString("id"));
    assertEquals(originalPlayer.toString(),afterGm.getJSONObject("player").toString());
    assertEquals(originalItems.toString(),afterGm.getJSONArray("inventory").toString());
    assertFalse("GM action must commit once, without pending state",
      afterGm.getJSONObject("scene").has("pendingActionId"));
    // The Core projection with {} contains only the latest receipt. The
    // WebView presentation carries the whole visible journal across turns.
    JSONArray log = new JSONObject(js("JSON.stringify(state)")).getJSONArray("log");
    assertTrue("GM response was not added to the visible journal",log.length()>=logCount+2);
    String narrative = "";
    for(int i=log.length()-1;i>=logCount;i--){
      JSONObject item=log.optJSONObject(i);
      if(item!=null && "gm".equals(item.optString("role"))){
        narrative=item.optString("text","");break;
      }
    }
    assertTrue("GM must produce substantive prose",narrative.trim().length()>=45);
    assertFalse("GM must not narrate a new Level 1 transition",
      java.util.regex.Pattern.compile("(?i)level\\s*1").matcher(narrative).find());
    assertFalse("GM must not use backend mini-map vocabulary",
      narrative.toLowerCase(java.util.Locale.ROOT).contains("mini-map"));
    screenshot("09-real-gm-narration");
    report.put("realGm",new JSONObject().put("status","PASS").put("provider","Gehihi")
      .put("sample",narrative.substring(0,Math.min(800,narrative.length())))
      .put("length",narrative.length()).put("coreUnchanged",true));


    JSONObject afterMove = performCoreAction(core, availableAction(coreState(core).getJSONObject("scene"), "room:"), "Cao Minh đi sang phòng kề.");
    String destination = afterMove.getJSONObject("scene").getJSONObject("room").getString("id");
    assertNotEquals(initialRoom, destination);
    assertEquals(startingInventory.toString(), afterMove.getJSONArray("inventory").toString());
    assertFalse("One move cannot complete Level 0", afterMove.getJSONObject("scene").optBoolean("completed"));
    report.put("movement", "PASS").put("destinationDifferent", true);
    screenshot("06-after-move");
    if(afterMove.has("pendingEncounter")){
      // A legal MOVE may lead to an Entity encounter. Do not treat this as an idle scene.
      until("document.getElementById('log')?.textContent.includes('Battle Start')",8);
      JSONObject encounter=inspect("({visible:document.getElementById('log').textContent.includes('Battle Start')})");
      assertTrue("Pending encounter must display Battle Start",encounter.getBoolean("visible"));
      report.put("encounterAfterMove","VISIBLE").put("afterMoveMap","NOT_TESTED_DURING_ENCOUNTER");
      screenshot("07-encounter-after-move");
    } else {
      click("Array.from(document.querySelectorAll('#explorerControls button')).find(b=>b.textContent.trim()==='MAP')");
      until("document.querySelector('#explorerChoices')?.open && document.querySelector('#explorerChoices svg')",6);
      JSONObject explored=inspect("(()=>{const s=document.querySelector('#explorerChoices svg');return {visited:s.querySelectorAll('[data-map-node=explored]').length,locked:s.querySelectorAll('[data-map-node=locked]').length,marker:s.querySelectorAll('[data-map-location=cao-minh]').length};})()");
      assertEquals(2,explored.getInt("visited"));
      assertEquals(43,explored.getInt("locked"));
      assertEquals(1,explored.getInt("marker"));
      screenshot("07-map-after-move");
      assertFalse("MAP picture must differ from gameplay screen",
        java.util.Arrays.equals(
          Files.readAllBytes(new File(evidence,"06-after-move.png").toPath()),
          Files.readAllBytes(new File(evidence,"07-map-after-move.png").toPath())));
      report.put("afterMoveMap",explored);
      click("Array.from(document.querySelectorAll('#explorerChoices button')).find(b=>b.getAttribute('aria-label')==='Đóng lựa chọn')");
    }

    JSONObject saved = inspect("({saved:save(),status:document.getElementById('status').textContent})");
    assertTrue("UI Save failed: " + saved.optString("status"), saved.getBoolean("saved"));
    JSONObject loaded = inspect("({loaded:load(),status:document.getElementById('status').textContent})");
    assertTrue("UI Load failed: " + loaded.optString("status"), loaded.getBoolean("loaded"));
    assertEquals(destination, coreState(core).getJSONObject("scene").getJSONObject("room").getString("id"));
    screenshot("08-restored-save");
    report.put("saveLoad", "PASS");
    report.put("result", "PASS");

    File reportFile = new File(evidence, "gameplay-report.json");
    try (FileOutputStream stream = new FileOutputStream(reportFile)) {
      stream.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
    }
    assertTrue(reportFile.length() > 100);
    activity.finish();
  }

  private boolean hasAction(JSONObject scene, String target) throws Exception {
    JSONArray actions = scene.getJSONArray("availableActions");
    for (int i = 0; i < actions.length(); i++)
      if (target.equals(actions.getJSONObject(i).getString("targetId"))) return true;
    return false;
  }
}
