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
    assertTrue(prompt.contains("originPeriod"));
    assertTrue(prompt.contains("TỰ SÁNG TÁC tên riêng"));
    assertTrue(prompt.contains("hai originPeriod khác nhau"));
    assertTrue(prompt.contains("KHÔNG được tự xác nhận Backrooms du hành thời gian"));
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
  @Test public void gmRejectsProseEmptyTruncationExtraFieldsAndNeverParsesState() throws Exception {
    assertEquals("Cao Minh quan sát.",ProviderResponseContract.parseSceneWriter("{\"reply\":\"Cao Minh quan sát.\"}").getString("reply"));
    for(String raw:new String[]{"Văn xuôi", "{\"reply\":\"\"}","{\"reply\":\"dang dở", "{\"reply\":\"a\",\"state\":{}}", "{\"reply\":\"a\"}{\"reply\":\"b\"}"})
      assertThrows(Exception.class,()->ProviderResponseContract.parseSceneWriter(raw));
  }
}
