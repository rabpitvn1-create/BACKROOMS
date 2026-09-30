package com.rabpit.backroom.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GameCoreRulesTest {
  @Test public void directPickupIsRejected() {
    assertTrue(GameCoreRules.isDirectPlayerPickupAction("Cao Minh nhặt chai Almond Water lên"));
    assertTrue(GameCoreRules.isDirectPlayerPickupAction("take the key"));
  }

  @Test public void vanTangGioiWithdrawalIsNotTreatedAsPickup() {
    assertFalse(GameCoreRules.isDirectPlayerPickupAction("lấy Huyết Ma Kiếm ra khỏi Vạn Tàng Giới"));
  }

  @Test public void restoreLocksInventoryMutation() {
    assertTrue(GameCoreRules.inventoryMutationLocked("hoàn nguyên vật thể trong nhẫn"));
  }

  @Test public void normalActionDoesNotLockInventory() {
    assertFalse(GameCoreRules.inventoryMutationLocked("Cao Minh kiểm tra hành lang phía trước"));
  }

  @Test public void routeExplorationActionsAreDetected() {
    assertTrue(GameCoreRules.isRouteExplorationAction("Cao Minh đi tiếp theo hành lang"));
    assertTrue(GameCoreRules.isRouteExplorationAction("rẽ sang trái"));
    assertTrue(GameCoreRules.isRouteExplorationAction("khảo sát lối đi phía trước"));
    assertTrue(GameCoreRules.isRouteExplorationAction("tìm đường ra"));
    assertFalse(GameCoreRules.isRouteExplorationAction("Cao Minh nghỉ tại đây"));
    assertFalse(GameCoreRules.isRouteExplorationAction("kiểm tra Inventory"));
  }

  @Test public void timeCostIsDeterministic() {
    assertEquals(30, GameCoreRules.estimateMinutes("Cao Minh ngủ một lúc"));
    assertEquals(10, GameCoreRules.estimateMinutes("Cao Minh nghỉ tại đây"));
    assertEquals(5, GameCoreRules.estimateMinutes("Cao Minh chạy sang phòng kế bên"));
    assertEquals(1, GameCoreRules.estimateMinutes("Cao Minh nhìn quanh"));
  }

  @Test public void levelIsInferredFromLocation() {
    assertEquals(0, GameCoreRules.levelFromLocation("Level 0 / The Lobby — khu phòng vàng"));
    assertEquals(5, GameCoreRules.levelFromLocation("Level 5 / Terror Hotel"));
    assertEquals(-1, GameCoreRules.levelFromLocation("Unknown corridor"));
  }

  @Test public void levelTransitionsFollowConnectedGraph() {
    assertFalse(GameCoreRules.levelTransitionAllowed(0, 1));
    assertFalse(GameCoreRules.levelTransitionAllowed(1, 0));
    assertTrue(GameCoreRules.levelTransitionAllowed(1, 2));
    assertTrue(GameCoreRules.levelTransitionAllowed(3, 6));
    assertTrue(GameCoreRules.levelTransitionAllowed(5, 6));
    assertTrue(GameCoreRules.levelTransitionAllowed(4, 4));
    assertFalse(GameCoreRules.levelTransitionAllowed(0, 5));
    assertFalse(GameCoreRules.levelTransitionAllowed(1, 6));
    assertFalse(GameCoreRules.levelTransitionAllowed(-1, 0));
  }

  @Test public void explicitLevelMustMatchLocation() {
    assertEquals(3, LevelCore.requestedLevel(3, "Level 3 / The Electrical Station", 2));
    assertEquals(4, LevelCore.requestedLevel(-1, "Level 4 / The Abandoned Office", 3));
    assertEquals(2, LevelCore.requestedLevel(-1, "Unknown maintenance corridor", 2));
    assertEquals(-2, LevelCore.requestedLevel(2, "Level 5 / Terror Hotel", 2));
  }
}
