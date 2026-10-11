package com.rabpit.backroom.core;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class SnapshotSceneProgressTest {
  private static JSONObject state(String id, String location, String area, int step) throws Exception {
    return new JSONObject()
        .put("level", new JSONObject().put("id", id))
        .put("location", location)
        .put("flags", new JSONObject()
            .put("visualAreaKey", area)
            .put("exploration", new JSONObject().put(SnapshotSceneProgress.STEP_KEY, step)));
  }

  private static JSONObject roll(boolean success) throws Exception {
    return new JSONObject().put(HiddenExitStreak.ROLL_KEY, new JSONObject()
        .put("eligible", true).put("success", success));
  }

  private static int step(JSONObject state) {
    return state.optJSONObject("flags").optJSONObject("exploration")
        .optInt(SnapshotSceneProgress.STEP_KEY, -1);
  }

  @Test public void successfulExploreAdvancesWithinSameLocationAndPersists() throws Exception {
    JSONObject before = state("0", "Level 0 / Hall", "", 2);
    JSONObject next = new JSONObject(before.toString());
    SnapshotSceneProgress.apply(before, next, roll(true), "EXPLORE");
    assertEquals(3, step(next));
    assertEquals(2, step(before));
    assertEquals(3, step(new JSONObject(next.toString())));
  }

  @Test public void failedExploreCannotChangeSceneEvenIfGmProposesOne() throws Exception {
    JSONObject before = state("0", "Level 0 / Hall", "A", 3);
    JSONObject next = state("0", "Level 0 / Hall", "B", 400);
    SnapshotSceneProgress.apply(before, next, roll(false), "EXPLORE");
    assertEquals(3, step(next));
  }

  @Test public void searchAndRenderStyleUpdatesDoNotAdvanceScene() throws Exception {
    JSONObject before = state("1", "Level 1 / Parking", "", 4);
    JSONObject next = state("1", "Level 1 / Parking", "", 999);
    SnapshotSceneProgress.apply(before, next, new JSONObject(), "SEARCH");
    assertEquals(4, step(next));
    SnapshotSceneProgress.apply(before, next, new JSONObject(), "EXECUTE");
    assertEquals(4, step(next));
  }

  @Test public void confirmedLocationAndNodeChangesAdvanceRegardlessOfActionType() throws Exception {
    JSONObject before = state("1", "Level 1 / Parking", "", 4);
    JSONObject moved = state("1", "Level 1 / Stairwell", "", 0);
    SnapshotSceneProgress.apply(before, moved, new JSONObject(), "EXECUTE");
    assertEquals(5, step(moved));
    JSONObject changedNode = state("2", "Level 2 / Pipe Dreams", "", 0);
    SnapshotSceneProgress.apply(before, changedNode, new JSONObject(), "EXECUTE");
    assertEquals(5, step(changedNode));
    JSONObject area = state("1", "Level 1 / Parking", "north", 0);
    SnapshotSceneProgress.apply(before, area, new JSONObject(), "EXECUTE");
    assertEquals(5, step(area));
  }

  @Test public void readyExitExploreAndStepOverflowRemainDeterministic() throws Exception {
    JSONObject before = state("0", "Level 0 / Hall", "", Integer.MAX_VALUE);
    JSONObject next = new JSONObject(before.toString());
    // Once exit is already ready no further probe is necessary; the explorer still moves.
    SnapshotSceneProgress.apply(before, next, new JSONObject(), "EXPLORE");
    assertEquals(0, step(next));
    JSONObject forged = state("0", "Level 0 / Hall", "", -50);
    JSONObject search = new JSONObject(forged.toString());
    SnapshotSceneProgress.apply(forged, search, new JSONObject(), "SEARCH");
    assertEquals(0, step(search));
  }
}
