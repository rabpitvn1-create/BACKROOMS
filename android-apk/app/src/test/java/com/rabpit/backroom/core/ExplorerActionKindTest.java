package com.rabpit.backroom.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class ExplorerActionKindTest {
  @Test public void chatExplorationEnablesNativeExplorerTurn() {
    for (String action : new String[] {
        "Khám Phá", "Cao Minh khám phá xung quanh",
        "Cao Minh đi tiếp qua hành lang", "Hắn bước vào căn phòng",
        "Tiến về phía trước", "Tìm đường thoát", "Khảo sát khu vực",
        "Lần theo dấu vết", "Quan sát xung quanh"}) {
      assertEquals(action, "EXPLORE", ExplorerActionKind.resolve("EXECUTE", action));
    }
  }

  @Test public void dialogueCombatAndMetaStayExecute() {
    for (String action : new String[] {
        "Nói chuyện với Lucia", "Hỏi đường", "Tấn công Hound",
        "Xem trạng thái hiện tại", "Mở túi đồ", "Kiểm tra thanh kiếm",
        "Điểm kinh nghiệm", "Khám phái", ""}) {
      assertEquals(action, "EXECUTE", ExplorerActionKind.resolve("EXECUTE", action));
    }
  }

  @Test public void explicitActionKindsAreNotChanged() {
    assertEquals("EXPLORE", ExplorerActionKind.resolve("EXPLORE", "nói chuyện"));
    assertEquals("SEARCH", ExplorerActionKind.resolve("SEARCH", "khám phá"));
    assertEquals("EXECUTE", ExplorerActionKind.resolve("EXECUTE", null));
  }
}
