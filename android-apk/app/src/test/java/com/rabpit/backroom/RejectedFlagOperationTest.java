package com.rabpit.backroom;

import static org.junit.Assert.*;
import java.lang.reflect.Method;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class RejectedFlagOperationTest {
  private JSONArray audit(String beforeFlags, String afterFlags, JSONObject op) throws Exception {
    MainActivity activity = new MainActivity();
    Method method = MainActivity.class.getDeclaredMethod("rejectedOperationIssuesAndroid",
      JSONObject.class, JSONObject.class, JSONObject.class);
    method.setAccessible(true);
    return (JSONArray) method.invoke(activity,
      new JSONObject().put("flags", new JSONObject(beforeFlags)),
      new JSONObject().put("flags", new JSONObject(afterFlags)),
      new JSONObject().put("ops", new JSONArray().put(op)));
  }

  private JSONObject op(String root, Object value) throws Exception {
    return new JSONObject().put("type", "flag_patch").put("root", root).put("value", value);
  }

  @Test public void repeatedEmptyEncounterDoesNotBlockTurn() throws Exception {
    String flags = "{\"entityEncounterKey\":\"\"}";
    assertEquals(0, audit(flags, flags, op("entityEncounterKey", "")).length());
  }

  @Test public void repeatedFalseAndZeroDoNotBlockTurn() throws Exception {
    assertEquals(0, audit("{\"ready\":false}", "{\"ready\":false}", op("ready", false)).length());
    assertEquals(0, audit("{\"count\":0}", "{\"count\":0}", op("count", 0)).length());
  }

  @Test public void shallowObjectPatchAllowsUnmentionedSiblingFields() throws Exception {
    String flags = "{\"communication\":{\"online\":false,\"attempts\":2}}";
    assertEquals(0, audit(flags, flags, op("communication", new JSONObject("{\"online\":false}"))).length());
  }

  @Test public void repeatedArrayAndObjectKeyOrderAreEquivalent() throws Exception {
    String flags = "{\"exploration\":{\"route\":[{\"Aa\":1,\"BB\":2}]}}";
    assertEquals(0, audit(flags, flags, op("exploration",
      new JSONObject("{\"route\":[{\"BB\":2,\"Aa\":1}]}"))).length());
  }

  @Test public void nestedObjectsUseReplacementRatherThanDeepSubsetMatching() throws Exception {
    String flags = "{\"exploration\":{\"nested\":{\"a\":1,\"b\":2}}}";
    assertEquals(1, audit(flags, flags, op("exploration",
      new JSONObject("{\"nested\":{\"a\":1}}"))).length());
  }

  @Test public void actualRejectedChangeStillBlocksAndIdentifiesOperation() throws Exception {
    String flags = "{\"entityEncounterKey\":\"\"}";
    JSONArray issues = audit(flags, flags, op("entityEncounterKey", "hound"));
    assertEquals(1, issues.length());
    JSONObject issue = issues.getJSONObject(0);
    assertEquals("android_reducer", issue.getString("source"));
    assertEquals("hard", issue.getString("severity"));
    assertEquals(0, issue.getInt("opIndex"));
    assertTrue(issue.getString("reason").contains("root=entityEncounterKey"));
    assertFalse(issue.getString("reason").contains("hound"));
  }

  @Test public void absentRootOrValueCannotMasqueradeAsNoOp() throws Exception {
    assertEquals(1, audit("{}", "{}", op("missing", JSONObject.NULL)).length());
    assertEquals(1, audit("{\"ready\":false}", "{\"ready\":false}",
      new JSONObject().put("type", "flag_patch").put("root", "ready")).length());
    assertEquals(1, audit("{}", "{}", op("", JSONObject.NULL)).length());
  }

  @Test public void explicitNullIsDistinctFromMissingField() throws Exception {
    assertEquals(0, audit("{\"ready\":null}", "{\"ready\":null}", op("ready", JSONObject.NULL)).length());
    assertEquals(1, audit("{\"ready\":{}}", "{\"ready\":{}}",
      op("ready", new JSONObject("{\"missing\":null}"))).length());
  }

  @Test public void changedFlagStillPasses() throws Exception {
    assertEquals(0, audit("{\"entityEncounterKey\":\"hound\"}", "{\"entityEncounterKey\":\"\"}",
      op("entityEncounterKey", "")).length());
  }

  @Test public void rootWhitespaceMatchesReducerNormalization() throws Exception {
    assertEquals(0, audit("{\"ready\":false}", "{\"ready\":false}", op(" ready ", false)).length());
  }

  @Test public void typeMismatchAndArrayReorderingStillBlock() throws Exception {
    assertEquals(1, audit("{\"ready\":false}", "{\"ready\":false}", op("ready", "false")).length());
    assertEquals(1, audit("{\"route\":[1,2]}", "{\"route\":[1,2]}", op("route", new JSONArray("[2,1]"))).length());
  }
}
