package com.rabpit.backroom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONTokener;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class Level0JourneySmokeTest {
  private static final String TAG = "Level0JourneySmoke";
  private static final long LOCAL_TURN_TIMEOUT_MS = 30_000L;
  private static final long PROVIDER_TURN_TIMEOUT_MS = 180_000L;

  private WebView webView;

  @Test
  public void freshGameEscapesLevelZeroAndStartsFirstPostLevelArea() throws Exception {
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(activity -> webView = findWebView(activity.getWindow().getDecorView()));
      assertNotNull("MainActivity must host the gameplay WebView", webView);

      waitJs(
        "document.readyState==='complete' && typeof state!=='undefined' && " +
          "typeof resetGame==='function' && typeof syncPrimaryActions==='function' && " +
          "document.getElementById('searchActionButton') && " +
          "document.getElementById('exploreActionButton') && document.getElementById('submit') && " +
          "window.Android && typeof window.Android.submitAction==='function' && " +
          "typeof window.Android.exportCoreState==='function'",
        30_000L,
        "game WebView did not become ready"
      );

      installSmokeHarness();
      eval("resetGame(); 'reset-requested';");
      waitJs("state && Number(state.turn)===1 && (typeof busy==='undefined' || busy===false)", 15_000L,
        "New Game did not settle at Turn 1");

      JSONObject fresh = stateSnapshot();
      assertEquals("New Game must start in Level 0", 0, fresh.optInt("level", -1));
      Log.i(TAG, "fresh => " + fresh);

      macro("EXPLORE", "explore-1");
      macro("SEARCH", "search-fluorescent-loop");
      macro("EXPLORE", "explore-marker-room");
      macro("EXPLORE", "explore-return-loop");
      macro("EXPLORE", "explore-memory-corridor");
      macro("SEARCH", "search-memory-corridor");

      execute(
        "đi theo hành lang có đèn rung và tiếng ù chồng lên nhau",
        "follow-transition-signs"
      );
      assertEquals(
        "Game State Core must still own Level 0 before the final crossing",
        "0",
        authoritativeAreaId()
      );

      execute(
        "tiếp tục cho tới khi kiến trúc đổi hẳn",
        "complete-level-zero"
      );

      JSONObject escaped = stateSnapshot();
      JSONObject escapedCore = coreSnapshot();
      JSONObject world = escapedCore.optJSONObject("world");
      assertNotNull("Core world state must exist after Level 0 handoff", world);
      assertEquals("Core handoff must point at epsilon", "epsilon", world.optString("levelId"));
      assertEquals("Authoritative active area after Level 0 must be epsilon", "epsilon", authoritativeAreaId());
      assertTrue(
        "UI must visibly relocate to Incessant Hum-Buzz after the Level 0 handoff: " + escaped,
        escaped.optString("title").contains("Incessant Hum-Buzz") ||
          escaped.optString("location").contains("Incessant Hum-Buzz") ||
          "epsilon".equals(escaped.optString("area"))
      );
      Log.i(TAG, "escaped-level-zero => ui=" + escaped + " core=" + escapedCore);

      int turnBeforePostLevelAction = escaped.optInt("turn", 0);
      macro("EXPLORE", "epsilon-first-explore", PROVIDER_TURN_TIMEOUT_MS);

      JSONObject afterFirstPostLevelAction = stateSnapshot();
      assertTrue("The first post-Level-0 action must advance the turn",
        afterFirstPostLevelAction.optInt("turn", 0) > turnBeforePostLevelAction);
      String authoritativePostArea = authoritativeAreaId();
      assertTrue(
        "After starting post-Level-0 progression, Core may remain in epsilon or move only to declared sublevel 0.01; area=" + authoritativePostArea + " state=" + afterFirstPostLevelAction,
        "epsilon".equals(authoritativePostArea) || "0.01".equals(authoritativePostArea)
      );
      assertFalse("Gameplay bridge reported an error: " + afterFirstPostLevelAction,
        afterFirstPostLevelAction.optBoolean("hasError", false));
      Log.i(TAG, "first-post-level-action => ui=" + afterFirstPostLevelAction + " core=" + coreSnapshot());
    }
  }

  private void installSmokeHarness() throws Exception {
    String script = "(function(){" +
      "window.__journeySmoke={completed:0,lastError:'',lastJson:''};" +
      "window.confirm=function(){return true;};" +
      "window.backroomTurn=function(json){try{" +
        "state=JSON.parse(json);" +
        "if(typeof busy!=='undefined')busy=false;" +
        "var submit=document.getElementById('submit');if(submit)submit.disabled=false;" +
        "var action=document.getElementById('action');if(action)action.value='';" +
        "if(typeof syncPrimaryActions==='function')syncPrimaryActions();" +
        "if(typeof render==='function')render();" +
        "try{localStorage.setItem('backroom-apk-state',JSON.stringify(state));}catch(_saveError){}" +
        "window.__journeySmoke.completed++;window.__journeySmoke.lastError='';window.__journeySmoke.lastJson=json;" +
      "}catch(e){window.__journeySmoke.lastError='backroomTurn:'+String(e);}};" +
      "window.backroomError=function(message){" +
        "if(typeof busy!=='undefined')busy=false;" +
        "var submit=document.getElementById('submit');if(submit)submit.disabled=false;" +
        "if(typeof syncPrimaryActions==='function')syncPrimaryActions();" +
        "window.__journeySmoke.lastError=String(message||'unknown bridge error');" +
      "};" +
      "return 'installed';})()";
    assertEquals("installed", eval(script));
  }

  private void macro(String kind, String label) throws Exception {
    macro(kind, label, LOCAL_TURN_TIMEOUT_MS);
  }

  private void macro(String kind, String label, long timeoutMs) throws Exception {
    String buttonId = "SEARCH".equals(kind) ? "searchActionButton" : "exploreActionButton";
    runTurn(label, "document.getElementById('" + buttonId + "').click(); 'clicked';", timeoutMs);
  }

  private void execute(String action, String label) throws Exception {
    String quoted = JSONObject.quote(action);
    String script = "(function(){" +
      "var a=document.getElementById('action');" +
      "if(!a)throw new Error('action input missing');" +
      "a.value=" + quoted + ";" +
      "a.dispatchEvent(new Event('input',{bubbles:true}));" +
      "document.getElementById('submit').click();" +
      "return 'clicked';})()";
    runTurn(label, script, LOCAL_TURN_TIMEOUT_MS);
  }

  private void runTurn(String label, String script, long timeoutMs) throws Exception {
    int before = evalInt("window.__journeySmoke.completed");
    String clickResult = eval(script);
    assertEquals("clicked", clickResult);

    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      String error = eval("window.__journeySmoke.lastError || ''; ");
      if (error != null && !error.isEmpty()) {
        fail(label + " failed: " + error + " state=" + stateSnapshot());
      }
      int completed = evalInt("window.__journeySmoke.completed");
      if (completed > before) {
        JSONObject snapshot = stateSnapshot();
        Log.i(TAG, label + " => ui=" + snapshot + " coreArea=" + authoritativeAreaId());
        return;
      }
      Thread.sleep(250L);
    }
    fail(label + " timed out after " + timeoutMs + " ms; state=" + stateSnapshot());
  }

  private JSONObject coreSnapshot() throws Exception {
    String coreRaw = eval("window.Android.exportCoreState();");
    if (coreRaw == null || coreRaw.trim().isEmpty()) fail("Android.exportCoreState() returned empty state");
    return new JSONObject(coreRaw);
  }

  private String authoritativeAreaId() throws Exception {
    JSONObject core = coreSnapshot();
    JSONObject instance = core.optJSONObject("levelInstance");
    if (instance != null && !instance.optBoolean("completed", false)) {
      String active = instance.optString("levelId", "").trim();
      if (!active.isEmpty()) return active;
    }
    JSONObject world = core.optJSONObject("world");
    return world == null ? "" : world.optString("levelId", "").trim();
  }

  private JSONObject stateSnapshot() throws Exception {
    String json = eval("(function(){" +
      "var f=(state&&state.flags)||{};var e=f.exploration||{};" +
      "var l=(state&&state.level)||{};" +
      "return JSON.stringify({" +
        "turn:Number((state&&state.turn)||0)," +
        "level:Number((l&&l.number)!=null?l.number:0)," +
        "area:String(e.areaId||''),areaName:String(e.areaName||'')," +
        "parentLevel:Number(e.parentLevel==null?-1:e.parentLevel)," +
        "levelTurns:Number(e.levelTurns||0)," +
        "hasError:!!(window.__journeySmoke&&window.__journeySmoke.lastError)," +
        "error:String((window.__journeySmoke&&window.__journeySmoke.lastError)||'')," +
        "location:String((state&&state.location)||''),title:String((state&&state.title)||'')" +
      "});})()" );
    return new JSONObject(json);
  }

  private void waitJs(String condition, long timeoutMs, String failureMessage) throws Exception {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      String value = eval("(function(){try{return !!(" + condition + ");}catch(e){return false;}})()");
      if ("true".equalsIgnoreCase(value)) return;
      Thread.sleep(200L);
    }
    fail(failureMessage);
  }

  private int evalInt(String expression) throws Exception {
    String value = eval("Number(" + expression + ")");
    return (int) Double.parseDouble(value);
  }

  private String eval(String script) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> raw = new AtomicReference<>();
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
      webView.evaluateJavascript(script, value -> {
        raw.set(value);
        latch.countDown();
      })
    );
    assertTrue("evaluateJavascript callback timed out for: " + script,
      latch.await(10, TimeUnit.SECONDS));
    String value = raw.get();
    if (value == null || "null".equals(value)) return null;
    Object decoded = new JSONTokener(value).nextValue();
    return decoded == JSONObject.NULL ? null : String.valueOf(decoded);
  }

  private static WebView findWebView(View view) {
    if (view instanceof WebView) return (WebView) view;
    if (!(view instanceof ViewGroup)) return null;
    ViewGroup group = (ViewGroup) view;
    for (int i = 0; i < group.getChildCount(); i++) {
      WebView found = findWebView(group.getChildAt(i));
      if (found != null) return found;
    }
    return null;
  }
}
