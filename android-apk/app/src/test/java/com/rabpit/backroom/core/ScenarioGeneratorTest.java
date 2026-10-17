package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class ScenarioGeneratorTest {
  private JSONObject request() throws Exception {
    return new JSONObject().put("kind","outline").put("levelNumber",0).put("index",1).put("nodeAnchor","0");
  }
  private String outline() throws Exception {
    JSONArray entries = new JSONArray();
    for (int i=1;i<=10;i++) entries.put(new JSONObject().put("index",i)
      .put("nodeAnchor","0").put("beat","Quan sát và đối chiếu")
      .put("objective","Giải mục tiêu").put("dependencyRefs",new JSONArray()));
    return new JSONObject().put("schemaVersion",4).put("levelNumber",0).put("nodeAnchor","0").put("entries",entries).toString();
  }
  @Test public void validOutlineUsesScenarioContractAndExactlyOneTransportCall() throws Exception {
    AtomicInteger calls=new AtomicInteger();
    String out=new ScenarioGenerator().generate(new String[]{"test-key"},request(),(key,prompt)->{
      calls.incrementAndGet(); assertTrue(prompt.contains("10 beat"));return outline();
    });
    assertEquals(10,new JSONObject(out).getJSONArray("entries").length());assertEquals(1,calls.get());
  }
  @Test public void scriptPromptAllowsNoNpcMultipleNpcAndNoForcedLies() throws Exception {
    String prompt = ScenarioGenerator.prompt(request().put("kind", "script"));
    assertTrue(prompt.contains("npcs:"));
    assertTrue(prompt.contains("npcs=[]"));
    assertTrue(prompt.contains("0..12 NPC"));
    assertTrue(prompt.contains("GIỚI HẠN TỐI ĐA"));
    assertTrue(prompt.contains("KHÔNG phải số NPC xuất hiện cùng lúc"));
    assertTrue(prompt.contains("không nhất thiết phải là điều kiện bắt buộc"));
    assertTrue(prompt.contains("KHÔNG bắt buộc có NPC hay lời khai sai"));
    assertTrue(prompt.contains("{schemaVersion:4,id:"));
    assertFalse(prompt.contains("{schemaVersion:3,id:"));
    assertTrue(prompt.contains("originPeriod"));
    assertTrue(prompt.contains("TỰ SÁNG TÁC tên riêng"));
    assertTrue(prompt.contains("hai originPeriod khác nhau"));
    assertTrue(prompt.contains("KHÔNG được tự xác nhận Backrooms du hành thời gian"));
    assertTrue(prompt.contains("observation:string 35..320"));
    assertTrue(prompt.contains("Lời khai là thông tin NHÂN VẬT TỰ NHẬN"));
    assertTrue(prompt.contains("Level 1"));
    assertTrue(prompt.contains("Không viết trùng observation"));
    assertFalse(prompt.contains(">=1 lời khai sai"));
  }
  @Test public void quotaAndMalformedResponsesHaveBoundedAttemptsAndIndependentCircuit() throws Exception {
    AtomicInteger calls=new AtomicInteger();ScenarioGenerator generator=new ScenarioGenerator();
    String[] keys={"secret-1","secret-2"};
    Exception error=assertThrows(IllegalStateException.class,()->generator.generate(keys,request(),(key,prompt)->{
      calls.incrementAndGet();throw new Exception("429 private payload "+key);
    }));
    assertEquals(2,calls.get());assertFalse(error.getMessage().contains("secret"));
    assertThrows(IllegalStateException.class,()->generator.generate(keys,request(),(key,prompt)->{calls.incrementAndGet();return "bad";}));
    assertEquals(2,calls.get());
    assertNotNull(new ScenarioGenerator().generate(keys,request(),(key,prompt)->outline()));
  }
  @Test public void exhaustedGeminiLanesFallBackOnceToValidatedGehihi() throws Exception {
    AtomicInteger geminiCalls = new AtomicInteger();
    AtomicInteger gehihiCalls = new AtomicInteger();
    ScenarioGenerator generator = new ScenarioGenerator();
    String result = generator.generate(new String[]{"key-a", "key-b"}, request(),
      (key, prompt) -> { geminiCalls.incrementAndGet(); throw new Exception("quota key=secret"); },
      prompt -> { gehihiCalls.incrementAndGet(); return outline(); });
    assertEquals(10, new JSONObject(result).getJSONArray("entries").length());
    assertEquals(2, geminiCalls.get());
    assertEquals(1, gehihiCalls.get());
    // Gemini lanes in cooldown: fallback should still work without another Gemini call.
    generator.generate(new String[]{"key-a", "key-b"}, request(),
      (key, prompt) -> { geminiCalls.incrementAndGet(); throw new Exception("quota"); },
      prompt -> { gehihiCalls.incrementAndGet(); return outline(); });
    assertEquals(2, geminiCalls.get());
    assertEquals(2, gehihiCalls.get());
  }

  @Test public void malformedGeminiScenarioUsesFallbackButValidGeminiDoesNot() throws Exception {
    AtomicInteger fallbackCalls = new AtomicInteger();
    ScenarioGenerator generator = new ScenarioGenerator();
    assertEquals(10, new JSONObject(generator.generate(new String[]{"a"}, request(),
      (key, prompt) -> "{\"schemaVersion\":3}", prompt -> {
        fallbackCalls.incrementAndGet(); return outline();
      })).getJSONArray("entries").length());
    assertEquals(1, fallbackCalls.get());
    assertEquals(10, new JSONObject(new ScenarioGenerator().generate(new String[]{"a"}, request(),
      (key, prompt) -> outline(), prompt -> {
        fallbackCalls.incrementAndGet(); throw new Exception("Must never fall back after success");
      })).getJSONArray("entries").length());
    assertEquals(1, fallbackCalls.get());
  }

  @Test public void fallbackWithoutGeminiKeysStillEnforcesCoreSchemaAndSafeFailure() throws Exception {
    AtomicInteger gehihiCalls = new AtomicInteger();
    assertEquals(10, new JSONObject(new ScenarioGenerator().generate(new String[0], request(),
      (key, prompt) -> { throw new AssertionError("Gemini must not run"); },
      prompt -> { gehihiCalls.incrementAndGet(); return outline(); })).getJSONArray("entries").length());
    assertEquals(1, gehihiCalls.get());
    Exception error = assertThrows(IllegalStateException.class, () -> new ScenarioGenerator().generate(
      new String[0], request(), (key, prompt) -> outline(),
      prompt -> "{\"schemaVersion\":3,\"private\":\"secret-token\"}"));
    assertTrue(error.getMessage().contains("Gemini và Gehihi"));
    assertFalse(error.getMessage().contains("secret-token"));
    assertThrows(IllegalStateException.class, () -> new ScenarioGenerator().generate(
      new String[0], request(), (key, prompt) -> outline(),
      prompt -> { throw new Exception("Bearer secret credential"); }));
  }

  @Test public void invalidGeminiJsonCanBeCorrectedWithSameKeyWithoutCooldown() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    java.util.List<String> issues = new java.util.ArrayList<>();
    ScenarioGenerator generator = new ScenarioGenerator();
    String out = generator.generate(new String[]{"valid-key"}, request(),
      (key, prompt) -> {
        int call = calls.incrementAndGet();
        if (call == 1) return "not-json";
        assertTrue(prompt.contains("scenario_json"));
        assertTrue(prompt.contains("LƯU Ý SỬA PHẢN HỒI TRƯỚC"));
        return outline();
      }, prompt -> { throw new AssertionError("Fallback must not run after corrected Gemini response"); },
      (provider, category, code) -> issues.add(provider + ":" + category + ":" + code));
    assertEquals(2, calls.get());
    assertEquals("gemini:validation:scenario_json", issues.get(0));
    assertEquals(1, issues.size());
    assertEquals(10, new JSONObject(out).getJSONArray("entries").length());
  }

  @Test public void validationFailuresReportOnlySafeCodesAndForwardRepairHintToGehihi() throws Exception {
    java.util.List<String> issues = new java.util.ArrayList<>();
    String result = new ScenarioGenerator().generate(new String[]{"key-a", "key-b"}, request(),
      (key, prompt) -> "{\"schemaVersion\":4,\"secret\":\"user-token\",\"invalid\":true}",
      prompt -> {
        assertTrue(prompt.contains("scenario_fields"));
        assertFalse(prompt.contains("user-token"));
        return outline();
      },
      (provider, category, code) -> issues.add(provider + ":" + category + ":" + code));
    assertEquals(10, new JSONObject(result).getJSONArray("entries").length());
    assertEquals(2, issues.size());
    assertEquals("gemini:validation:scenario_fields", issues.get(0));
    assertFalse(String.join("|", issues).contains("user-token"));
  }

  @Test public void transportFailureIsLoggedSeparatelyAndDoesNotExposeProviderPayload() throws Exception {
    java.util.List<String> issues = new java.util.ArrayList<>();
    ScenarioGenerator generator = new ScenarioGenerator();
    String result = generator.generate(new String[]{"key-a"}, request(),
      (key, prompt) -> { throw new Exception("429 private bearer secret"); },
      prompt -> outline(),
      (provider, category, code) -> issues.add(provider + ":" + category + ":" + code));
    assertEquals(10, new JSONObject(result).getJSONArray("entries").length());
    assertEquals(1, issues.size());
    assertEquals("gemini:transport:invalid_output", issues.get(0));
  }

  @Test public void gmRejectsProseEmptyTruncationExtraFieldsAndNeverParsesState() throws Exception {
    assertEquals("Cao Minh quan sát.",ProviderResponseContract.parseSceneWriter("{\"reply\":\"Cao Minh quan sát.\"}").getString("reply"));
    for(String raw:new String[]{"Văn xuôi", "{\"reply\":\"\"}","{\"reply\":\"dang dở", "{\"reply\":\"a\",\"state\":{}}", "{\"reply\":\"a\"}{\"reply\":\"b\"}"})
      assertThrows(Exception.class,()->ProviderResponseContract.parseSceneWriter(raw));
  }
}
