package com.rabpit.backroom.core

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rabpit.backroom.MainActivity
import com.rabpit.backroom.BuildConfig
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual APK gameplay for CP1 and CP2; SOL planner, Gehihi writer, no fake receipts or damage. */
@RunWith(AndroidJUnit4::class)
class LiveSixCheckpointsGameplayTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context = instrumentation.targetContext
  private val tag = "BackroomLiveSixCP"

  private fun js(web: WebView, script: String): String {
    val latch = CountDownLatch(1)
    var value = ""
    instrumentation.runOnMainSync {
      web.evaluateJavascript(script) { raw -> value = raw; latch.countDown() }
    }
    check(latch.await(10, TimeUnit.SECONDS)) { "WebView JS did not respond" }
    return value
  }

  private fun snapshot(web: WebView): JSONObject {
    val script = """
      (function(){
        if(typeof state==='undefined')return JSON.stringify({ready:false});
        var s=state||{},c=s.combat||{},d=c.diceState||{},story=s.story||{};
        var panel=document.getElementById('milestoneLoading');
        var retry=document.getElementById('milestoneLoadingRetry');
        var status=document.getElementById('status');
        var finish=document.getElementById('pokerDiceFinish');
        var warnings=document.querySelectorAll('.message.gm.warning');
        // Observe real callback errors even when rerendering removes warning rows.
        if(!window.__liveTestErrorEvents){
          window.__liveTestErrorEvents=[];
          var recordError=function(source,message){
            window.__liveTestErrorEvents.push({source:source,message:String(message||'').slice(0,900)});
          };
          var originalGmError=window.backroomError;
          if(typeof originalGmError==='function'){
            window.backroomError=function(message){
              recordError('gm',message);
              return originalGmError.apply(this,arguments);
            };
          }
          var originalMilestoneStatus=window.backroomMilestoneStatus;
          if(typeof originalMilestoneStatus==='function'){
            window.backroomMilestoneStatus=function(raw){
              try{
                var event=typeof raw==='string'?JSON.parse(raw):raw;
                if(event&&event.status==='error')recordError('milestone',event.message);
              }catch(ignore){}
              return originalMilestoneStatus.apply(this,arguments);
            };
          }
        }
        return JSON.stringify({
          ready:true,cp:String(story.checkpointId||''),turn:Number(s.turn)||0,
          plan:story.hasPlan===true,waiting:story.waiting===true,
          loading:!!panel&&!panel.hidden,milestoneError:!!retry&&!retry.hidden,
          milestoneMessage:String(document.getElementById('milestoneLoadingMessage')?.textContent||'').slice(0,180),
          blocked:!!window.__milestoneBlocked,
          busy:typeof busy!=='undefined'&&!!busy,
          combat:c.active===true,diceReady:!!c.diceState,diceFinal:d.finalized===true,
          diceBusy:!!window.__combatDiceBusy||!!window.__combatFeedbackBusy,
          canFinish:!!finish&&!finish.disabled,
          status:String(status&&status.textContent||'').slice(0,900),
          warnings:warnings.length,
          errorEvents:window.__liveTestErrorEvents||[],
          error:warnings.length?String(warnings[warnings.length-1].textContent||'').slice(0,900):''
        });
      })();
    """.trimIndent()
    return JSONObject(JSONTokener(js(web, script)).nextValue() as String)
  }

  private fun click(web: WebView, id: String): Boolean =
    js(web, """
      (function(){
        var button=document.getElementById('$id');
        if(!button||button.disabled||button.hidden)return false;
        button.click();return true;
      })();
    """.trimIndent()) == "true"

  @Test fun playFirstTwoCheckpointsWithRealProvidersAndUi() {
    assertTrue("Only SOL Milestone and Gehihi GM are allowed in this live test",
      BuildConfig.DEBUG && BuildConfig.TWO_PROVIDER_GAMEPLAY_TEST)
    assertTrue("SOL is required", BuildConfig.SOL_API_KEY.isNotBlank())
    assertTrue("Gehihi is required", BuildConfig.GEHIHI_API_KEY.isNotBlank())
    assertTrue("Gemini credentials must be absent", listOf(
      BuildConfig.GEMINI_API_KEY_1, BuildConfig.GEMINI_API_KEY_2,
      BuildConfig.GEMINI_API_KEY_3, BuildConfig.GEMINI_API_KEY_4,
      BuildConfig.GEMINI_API_KEY_5).all { it.isBlank() })
    assertTrue("Haku credentials must be absent", BuildConfig.HAKU_API_KEY.isBlank())
    SharedPreferencesSaveRepository(context).clear()
    val activity = instrumentation.startActivitySync(
      Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
    val field = MainActivity::class.java.getDeclaredField("webView")
    field.isAccessible = true
    val web = field.get(activity) as WebView
    val started = SystemClock.elapsedRealtime()
    var lastChangeAt = started
    var priorCp = ""
    var priorTurn = 0
    var actionAt = 0L
    var diceAt = 0L
    var explorerActions = 0
    var combatFinishes = 0
    var lastHeartbeatAt = started
    // Record recoverable UI errors; do not stop the playtest while a normal retry can proceed.
    var observedErrorEvents = 0
    var totalTimeouts = 0
    val timeoutPattern = Regex(
      "timeout|timed?\\s*out|deadline|http\\s*408|quá\\s+thời\\s+gian\\s+chờ|không\\s+trả\\s+kịp",
      RegexOption.IGNORE_CASE
    )
    var retryAfter = 0L
    var lastMilestoneRetryAt = 0L
    var lastMilestoneRetryKey = ""
    val errorsPerTurn = linkedMapOf<String, Int>()
    val milestoneRetries = linkedMapOf<String, Int>()
    val issues = mutableListOf<String>()
    try {
      while (SystemClock.elapsedRealtime() - started < TimeUnit.HOURS.toMillis(3)) {
        val ui = snapshot(web)
        if (!ui.optBoolean("ready")) { Thread.sleep(1000); continue }
        val cp = ui.optString("cp")
        val turn = ui.optInt("turn")
        val combat = ui.optBoolean("combat")
        val now = SystemClock.elapsedRealtime()
        if (cp != priorCp || turn != priorTurn) {
          Log.i(tag, "CP=" + cp + " ExplorerTurn=" + turn + " combat=" + combat +
            " elapsedSec=" + (now - started) / 1000)
          priorCp = cp
          priorTurn = turn
          lastChangeAt = now
        }
        val events = ui.optJSONArray("errorEvents")
        if (events != null) {
          if (events.length() < observedErrorEvents) observedErrorEvents = 0
          while (observedErrorEvents < events.length()) {
            val event = events.getJSONObject(observedErrorEvents++)
            val source = event.optString("source")
            val message = event.optString("message")
            val key = cp + ":turn=" + turn
            val summary = "CP=" + cp + " turn=" + turn + " source=" + source + " error=" + message
            issues.add(summary)
            Log.w(tag, "LIVE_PROVIDER_ERROR " + summary)
            if (timeoutPattern.containsMatchIn(message)) {
              totalTimeouts++
              Log.w(tag, "LIVE_TIMEOUT_COUNT=" + totalTimeouts + " " + summary)
              if (totalTimeouts > 3)
                fail("LIVE_TIMEOUT_LIMIT_EXCEEDED totalTimeouts=" + totalTimeouts +
                  " CP=" + cp + " Turn=" + turn + " last=" + message)
            }
            if (source == "gm") {
              val attempts = (errorsPerTurn[key] ?: 0) + 1
              errorsPerTurn[key] = attempts
              retryAfter = now + 5_000L
              lastChangeAt = now
              if (attempts >= 5)
                fail("LIVE_BLOCKING_GM_ERROR " + summary + " attempts=" + attempts)
            }
          }
        }

        // CP1 and CP2 must be completed, including actual Boss combat.
        if (cp == "CP-L00-03") {
          val stored = SharedPreferencesSaveRepository(context).load()
          val history = org.json.JSONArray(stored.world["story.checkpointHistory"] ?: "[]")
          assertEquals(2, history.length())
          for (i in 0..1) {
            val item = history.getJSONObject(i)
            assertEquals("CP-L00-" + (i + 1).toString().padStart(2, '0'), item.getString("checkpointId"))
            assertTrue(item.getLong("turnCount") >= 18L)
            assertTrue(item.getInt("entityEncounterCount") * 5L >= item.getLong("turnCount") * 2L)
            assertEquals("DEFEATED", item.getString("bossResult"))
          }
          StoryMilestoneRuntime.validateStoredState(stored)
          Log.i(tag, "LIVE_2CP_PASS explorerActions=" + explorerActions +
            " combatFinishes=" + combatFinishes + " totalTimeouts=" + totalTimeouts +
            " recoverableIssues=" + issues.size)
          issues.forEachIndexed { index, issue -> Log.i(tag, "RECOVERABLE_$index " + issue) }
          return
        }
        if (ui.optBoolean("milestoneError")) {
          val key = cp + ":turn=" + turn
          if (key != lastMilestoneRetryKey || now - lastMilestoneRetryAt > 25_000L) {
            val attempts = (milestoneRetries[key] ?: 0) + 1
            milestoneRetries[key] = attempts
            val message = "CP=" + cp + " turn=" + turn + " milestone=" +
              ui.optString("milestoneMessage")
            issues.add(message)
            Log.w(tag, "NON_BLOCKING_MILESTONE_ERROR " + message + " retry=" + attempts)
            if (attempts >= 4) fail("LIVE_BLOCKING_MILESTONE_ERROR " + message +
              " attempts=" + attempts)
            if (!click(web, "milestoneLoadingRetry"))
              fail("LIVE_BLOCKING_MILESTONE_RETRY_UNAVAILABLE " + message)
            lastMilestoneRetryKey = key
            lastMilestoneRetryAt = now
            lastChangeAt = now
          }
          Thread.sleep(1000)
          continue
        }
        if (cp.isNotEmpty() && cp != "CP-L00-01" && cp != "CP-L00-02")
          fail("LIVE_UNEXPECTED_CP=" + cp + " Turn=" + turn)
        // An actual writer + independent audits + one repair + evidence verifier can
        // legitimately exceed nine minutes during provider 503/timeout failover.
        // Do not abort an in-flight request merely because Explorer Turn is unchanged.
        val pending = ui.optBoolean("busy")
        val loading = ui.optBoolean("loading") || ui.optBoolean("blocked")
        val idleLimit = when {
          pending -> TimeUnit.MINUTES.toMillis(22)
          loading -> TimeUnit.MINUTES.toMillis(8)
          combat -> TimeUnit.MINUTES.toMillis(9)
          else -> TimeUnit.MINUTES.toMillis(4)
        }
        if (now - lastHeartbeatAt >= TimeUnit.MINUTES.toMillis(1)) {
          Log.i(tag, "HEARTBEAT cp=" + cp + " turn=" + turn +
            " busy=" + pending + " blocked=" + ui.optBoolean("blocked") +
            " plan=" + ui.optBoolean("plan") + " waiting=" + ui.optBoolean("waiting") +
            " loading=" + loading + " combat=" + combat +
            " idleSec=" + ((now - lastChangeAt) / 1000) +
            " providerStatus=" + ui.optString("status") +
            " recoverableIssues=" + issues.size + " totalTimeouts=" + totalTimeouts)
          lastHeartbeatAt = now
        }
        if (now - lastChangeAt > idleLimit)
          fail("LIVE_STALLED CP=" + cp + " Turn=" + turn +
            " busy=" + pending + " blocked=" + ui.optBoolean("blocked") +
            " plan=" + ui.optBoolean("plan") + " waiting=" + ui.optBoolean("waiting") +
            " combat=" + combat + " idleSec=" + ((now - lastChangeAt) / 1000) +
            " status=" + ui.optString("status") +
            " loading=" + ui.optString("milestoneMessage") + " issues=" + issues.size)

        if (combat) {
          if (now - diceAt > 1500 && ui.optBoolean("diceReady") && !ui.optBoolean("diceFinal") &&
            !ui.optBoolean("diceBusy") && ui.optBoolean("canFinish")) {
            if (click(web, "pokerDiceFinish")) {
              diceAt = now
              combatFinishes++
              lastChangeAt = now // A valid Poker Dice command is real Combat progress, not Explorer Turn.
              if (combatFinishes > 800) fail("LIVE_TOO_MANY_COMBAT_FINISHES CP=" + cp)
              if (combatFinishes % 10 == 0) Log.i(tag, "CP=" + cp + " combatFinishes=" + combatFinishes)
            }
          }
        } else if (ui.optBoolean("plan") && !ui.optBoolean("waiting") &&
          !ui.optBoolean("blocked") && !ui.optBoolean("busy") &&
          now >= retryAfter && now - actionAt > 1100) {
          if (click(web, "exploreActionButton")) {
            explorerActions++
            actionAt = now
            lastChangeAt = now // A newly submitted live API request resets the idle watch.
          }
        }
        Thread.sleep(1000)
      }
      fail("LIVE_GLOBAL_TIMEOUT 3h CP=" + priorCp + " Turn=" + priorTurn +
        " explorerActions=" + explorerActions + " totalTimeouts=" + totalTimeouts +
        " issues=" + issues.size)
    } finally {
      instrumentation.runOnMainSync { activity.finish() }
    }
  }
}
