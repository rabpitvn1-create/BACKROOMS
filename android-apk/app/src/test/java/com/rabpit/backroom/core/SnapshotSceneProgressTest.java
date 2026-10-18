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

  @Test public void successfulExecuteAdvancesWithinSameLocationAndPersists() throws Exception {
    JSONObject before = state("0", "Level 0 / Hall", "", 2);
    JSONObject next = new JSONObject(before.toString());
    SnapshotSceneProgress.apply(before, next, roll(true), "EXECUTE", "khảo sát hành lang");
    assertEquals(3, step(next));
    assertEquals(2, step(before));
    assertEquals(3, step(new JSONObject(next.toString())));
  }

  @Test public void failedExitProbeDoesNotPreventVisualExploration() throws Exception {
    JSONObject before = state("0", "Level 0 / Hall", "A", 3);
    JSONObject next = state("0", "Level 0 / Hall", "A", 400);
    SnapshotSceneProgress.apply(before, next, roll(false), "EXECUTE", "quan sát xung quanh");
    assertEquals(4, step(next));
    assertEquals(3, step(before));
  }

  @Test public void entityEncounterDoesNotRotateEnvironmentSnapshot() throws Exception {
    JSONObject before = state("0", "Level 0 / Hall", "", 7);
    JSONObject next = state("0", "Level 0 / Hall", "", 100);
    JSONObject rolls = roll(true).put("entityEncounter", new JSONObject()
        .put("eligible", true).put("success", true));
    SnapshotSceneProgress.apply(before, next, rolls, "EXECUTE", "thăm dò hành lang");
    assertEquals(7, step(next));
    JSONObject ineligible = roll(false).put("entityEncounter", new JSONObject()
        .put("eligible", false).put("success", true));
    JSONObject safe = new JSONObject(before.toString());
    SnapshotSceneProgress.apply(before, safe, ineligible, "EXECUTE", "thăm dò hành lang");
    assertEquals(8, step(safe));
  }

  @Test public void deviceInputsPreserveCurrentSnapshotEvenIfActionLooksLikeExploration() throws Exception {
    JSONObject before = state("0", "Level 0 / Hall", "panel_area", 7);
    JSONObject next = state("0", "Level 0 / Hall", "different_area", 99);
    SnapshotSceneProgress.apply(before, next, new JSONObject(), "DEVICE", "Khảo sát bảng điều khiển");
    assertEquals(7, step(next));
    assertEquals("panel_area", next.getJSONObject("flags").getString("visualAreaKey"));
    assertEquals(7, step(before));
    // A later normal scene-facing action must still advance as before.
    JSONObject moved = new JSONObject(next.toString());
    SnapshotSceneProgress.apply(next, moved, new JSONObject(), "EXECUTE", "khảo sát hành lang");
    assertEquals(8, step(moved));
  }

  @Test public void searchDoesNotAdvanceButGameplayExecuteDoes() throws Exception {
    JSONObject before = state("1", "Level 1 / Parking", "", 4);
    JSONObject next = state("1", "Level 1 / Parking", "", 999);
    SnapshotSceneProgress.apply(before, next, new JSONObject(), "SEARCH", "khám phá");
    assertEquals(4, step(next));
    SnapshotSceneProgress.apply(before, next, new JSONObject(), "EXECUTE", "quan sát");
    assertEquals(5, step(next));
  }

  @Test public void confirmedLocationAndNodeChangesAdvanceRegardlessOfActionType() throws Exception {
    JSONObject before = state("1", "Level 1 / Parking", "", 4);
    JSONObject moved = state("1", "Level 1 / Stairwell", "", 0);
    SnapshotSceneProgress.apply(before, moved, new JSONObject(), "EXECUTE", "nói chuyện");
    assertEquals(5, step(moved));
    JSONObject changedNode = state("2", "Level 2 / Pipe Dreams", "", 0);
    SnapshotSceneProgress.apply(before, changedNode, new JSONObject(), "EXECUTE", "nói chuyện");
    assertEquals(5, step(changedNode));
    JSONObject area = state("1", "Level 1 / Parking", "north", 0);
    SnapshotSceneProgress.apply(before, area, new JSONObject(), "EXECUTE", "nói chuyện");
    assertEquals(5, step(area));
  }

  @Test public void readyExitExecuteAndStepOverflowRemainDeterministic() throws Exception {
    JSONObject before = state("0", "Level 0 / Hall", "", Integer.MAX_VALUE);
    JSONObject next = new JSONObject(before.toString());
    // Once exit is already ready no further probe is necessary; the explorer still moves.
    SnapshotSceneProgress.apply(before, next, new JSONObject(), "EXECUTE", "quan sát");
    assertEquals(0, step(next));
    JSONObject forged = state("0", "Level 0 / Hall", "", -50);
    JSONObject search = new JSONObject(forged.toString());
    SnapshotSceneProgress.apply(forged, search, new JSONObject(), "SEARCH", "khám phá");
    assertEquals(0, step(search));
  }
  @Test public void dialogueAndMetaDoNotRotateButMentalReconDoes() throws Exception {
    JSONObject before = state("0", "Level 0 / Hall", "", 3);
    JSONObject conversation = new JSONObject(before.toString());
    SnapshotSceneProgress.apply(before, conversation, new JSONObject(), "EXECUTE", "Nói chuyện với Lucia");
    assertEquals(3, step(conversation));
    JSONObject meta = new JSONObject(before.toString());
    SnapshotSceneProgress.apply(before, meta, new JSONObject(), "EXECUTE", "Xem trạng thái");
    assertEquals(3, step(meta));
    JSONObject spoken = new JSONObject(before.toString());
    SnapshotSceneProgress.apply(before, spoken, new JSONObject(), "EXECUTE", "Hỏi Lucia về thần thức");
    assertEquals(3, step(spoken));
    JSONObject mental = new JSONObject(before.toString());
    SnapshotSceneProgress.apply(before, mental, new JSONObject(), "EXECUTE", "Thả thần thức đi điều tra");
    assertEquals(4, step(mental));
    JSONObject nonExecute = new JSONObject(before.toString());
    SnapshotSceneProgress.apply(before, nonExecute, new JSONObject(), "SEARCH", "khảo sát hành lang");
    assertEquals(3, step(nonExecute));
  }

}
