package com.rabpit.backroom;

import android.app.Activity;
import android.app.Instrumentation;
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

  private void click(String expression) throws Exception {
    JSONObject result = inspect("(()=>{const button=" + expression +
      ";if(!button)return {clicked:false};button.click();return {clicked:true};})()");
    assertTrue("Expected user control was not found: " + expression, result.getBoolean("clicked"));
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
    Activity activity = instrumentation.startActivitySync(intent);
    assertNotNull(activity);
    webView = findWebView(activity.getWindow().getDecorView());
    assertNotNull("MainActivity must render its real WebView", webView);

    until("document.querySelector('#explorerControls button') && typeof Android !== 'undefined' && typeof state !== 'undefined' && state.scene && state.scene.map", 30);
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
    JSONObject map = inspect("(()=>{const d=document.querySelector('#explorerChoices'),s=d.querySelector('svg');return {open:d.open,full:d.classList.contains('explorer-map-mode'),title:d.querySelector('h2')?.textContent||d.querySelector('header')?.textContent||'',nodes:s.querySelectorAll('[data-map-node]').length,locked:s.querySelectorAll('[data-map-node=locked]').length,unnamedLocked:s.querySelectorAll('[data-map-node=locked] text').length,position:s.querySelectorAll('[data-map-location=cao-minh]').length,dashed:s.querySelectorAll('.explorer-map-edge-locked').length,scrollable:!!d.querySelector('.explorer-map-scroll')};})()");
    assertTrue(map.getBoolean("open"));
    assertTrue(map.getBoolean("full"));
    assertEquals(45, map.getInt("nodes"));
    assertEquals(44, map.getInt("locked"));
    assertEquals(0, map.getInt("unnamedLocked"));
    assertEquals(1, map.getInt("position"));
    assertTrue(map.getInt("dashed") > 0);
    assertTrue(map.getBoolean("scrollable"));
    screenshot("02-fullscreen-map");
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

    JSONObject afterMove = performCoreAction(core, availableAction(afterMark.getJSONObject("scene"), "room:"), "Cao Minh đi sang phòng kề.");
    String destination = afterMove.getJSONObject("scene").getJSONObject("room").getString("id");
    assertNotEquals(initialRoom, destination);
    assertEquals(startingInventory.toString(), afterMove.getJSONArray("inventory").toString());
    assertFalse("One move cannot complete Level 0", afterMove.getJSONObject("scene").optBoolean("completed"));
    report.put("movement", "PASS").put("destinationDifferent", true);
    screenshot("06-after-move");
    click("Array.from(document.querySelectorAll('#explorerControls button')).find(b=>b.textContent.trim()==='MAP')");
    until("document.querySelector('#explorerChoices')?.open && document.querySelector('#explorerChoices svg')", 6);
    JSONObject explored = inspect("(()=>{const s=document.querySelector('#explorerChoices svg');return {visited:s.querySelectorAll('[data-map-node=explored]').length,locked:s.querySelectorAll('[data-map-node=locked]').length,marker:s.querySelectorAll('[data-map-location=cao-minh]').length};})()");
    assertEquals(2, explored.getInt("visited"));
    assertEquals(43, explored.getInt("locked"));
    assertEquals(1, explored.getInt("marker"));
    screenshot("07-map-after-move");
    report.put("afterMoveMap", explored);
    click("document.querySelector('#explorerChoices button[aria-label=\"Đóng lựa chọn\"]')");

    JSONObject saved = inspect("({saved:save(),status:document.getElementById('status').textContent})");
    assertTrue("UI Save failed: " + saved.optString("status"), saved.getBoolean("saved"));
    JSONObject loaded = inspect("({loaded:load(),status:document.getElementById('status').textContent})");
    assertTrue("UI Load failed: " + loaded.optString("status"), loaded.getBoolean("loaded"));
    assertEquals(destination, coreState(core).getJSONObject("scene").getJSONObject("room").getString("id"));
    screenshot("08-restored-save");
    report.put("saveLoad", "PASS").put("result", "PASS");

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
