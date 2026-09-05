package com.rabpit.backroom;

import static org.junit.Assert.*;
import java.lang.reflect.Method;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class LuciaRecruitmentTest {
  private final MainActivity activity = new MainActivity();

  private JSONObject contact(boolean identity) throws Exception {
    return new JSONObject().put("level", new JSONObject().put("number", 0))
      .put("party", new JSONArray()).put("flags", new JSONObject().put("lucia",
        new JSONObject().put("encountered", true).put("present", true)
          .put("spawned", true).put("identityKnown", identity)));
  }

  private JSONObject apply(JSONObject before, JSONArray ops) throws Exception {
    Method method = MainActivity.class.getDeclaredMethod("applyModelOperations",
      JSONObject.class, JSONArray.class, JSONObject.class, String.class);
    method.setAccessible(true);
    return (JSONObject) method.invoke(activity, before, ops, new JSONObject(), "Mời Lucia gia nhập party");
  }

  private JSONObject identityOp() throws Exception {
    return new JSONObject().put("type", "flag_patch").put("root", "lucia")
      .put("value", new JSONObject().put("identityKnown", true));
  }

  private JSONObject joinOp() throws Exception {
    return new JSONObject().put("type", "party_upsert").put("member", new JSONObject()
      .put("id", "lucia").put("name", "Lucia Lục").put("present", true).put("joinConfirmed", true));
  }

  @Test public void identityThenLaterInvitationAddsLuciaWithoutRandomSurvivorRoll() throws Exception {
    JSONObject before = contact(false);
    JSONObject introduced = apply(before, new JSONArray().put(identityOp()));
    assertTrue(introduced.getJSONObject("flags").getJSONObject("lucia").getBoolean("identityKnown"));
    assertFalse(before.getJSONObject("flags").getJSONObject("lucia").getBoolean("identityKnown"));
    assertEquals(0, introduced.getJSONArray("party").length());
    JSONObject joined = apply(introduced, new JSONArray().put(joinOp()));
    assertEquals("lucia", joined.getJSONArray("party").getJSONObject(0).getString("id"));
    assertEquals(1, apply(joined, new JSONArray().put(joinOp())).getJSONArray("party").length());
  }

  @Test public void identityAndJoinInSameTurnDoNotRecruit() throws Exception {
    assertEquals(0, apply(contact(false), new JSONArray().put(identityOp()).put(joinOp()))
      .getJSONArray("party").length());
  }

  @Test public void absentContactAndWrongLevelCannotRecruitOrWriteIdentity() throws Exception {
    for (JSONObject before : new JSONObject[] {
      new JSONObject().put("level", new JSONObject().put("number", 0)).put("party", new JSONArray()),
      contact(false).put("level", new JSONObject().put("number", 1))
    }) {
      JSONObject result = apply(before, new JSONArray().put(identityOp()).put(joinOp()));
      assertEquals(0, result.getJSONArray("party").length());
      JSONObject lucia = result.getJSONObject("flags").optJSONObject("lucia");
      assertTrue(lucia == null || !lucia.optBoolean("identityKnown", false));
    }
  }

  @Test public void dialogueCannotForgeEncounterOrRecruitmentFlags() throws Exception {
    JSONObject op = identityOp().put("value", new JSONObject().put("identityKnown", true)
      .put("encountered", false).put("present", false).put("follower", true).put("joinConfirmed", true));
    JSONObject flags = apply(contact(false), new JSONArray().put(op)).getJSONObject("flags").getJSONObject("lucia");
    assertTrue(flags.getBoolean("encountered"));
    assertTrue(flags.getBoolean("present"));
    assertFalse(flags.optBoolean("follower", false));
    assertFalse(flags.optBoolean("joinConfirmed", false));
  }
}
