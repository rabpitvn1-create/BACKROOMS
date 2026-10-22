package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class PlayerActionPlanTest {
  @Test fun freeAndCoreStepsCanShareOneUtterance() {
    val raw = """{"totalActions":4,"steps":[{"kind":"FREE","text":"Kiểm tra bóng đen"},{"kind":"MOVE","targetId":"room:4"},{"kind":"INTERACT","targetId":"passage:door4"},{"kind":"SEARCH","targetId":"inspect"}],"unresolved":false}"""
    val parsed = PlayerActionPlan.parse(raw, -1, 0)
    assertEquals(4, parsed.getJSONArray("steps").length())
    assertEquals("FREE", parsed.getJSONArray("steps").getJSONObject(0).getString("kind"))
  }

  @Test fun imaginaryItemNamesMustStayNarrative() {
    val raw = """{"totalActions":1,"steps":[{"kind":"FREE","text":"Đưa Almond Water, Thiên Tiên Đan và Diệt Thế Bảo Kiếm cho Lucia"}],"unresolved":false}"""
    assertEquals("FREE", PlayerActionPlan.parse(raw, -1, 0).getJSONArray("steps").getJSONObject(0).getString("kind"))
  }

  @Test fun replanPreservesSemanticActionTotal() {
    val raw = """{"totalActions":3,"steps":[{"kind":"SEARCH","targetId":"inspect"}],"unresolved":false}"""
    assertEquals(3, PlayerActionPlan.parse(raw, 3, 2).getInt("totalActions"))
    assertThrows(IllegalArgumentException::class.java) { PlayerActionPlan.parse(raw, 2, 0) }
  }

  @Test fun strictBoundaryRejectsItemCommandsAndUntrustedFields() {
    val item = """{"totalActions":1,"steps":[{"kind":"USE_ITEM","targetId":"almond-water"}],"unresolved":false}"""
    assertThrows(IllegalArgumentException::class.java) { PlayerActionPlan.parse(item, -1, 0) }
    val smuggled = """{"totalActions":1,"steps":[{"kind":"FREE","text":"Đưa nước","itemId":"almond-water"}],"unresolved":false}"""
    assertThrows(IllegalArgumentException::class.java) { PlayerActionPlan.parse(smuggled, -1, 0) }
    val overBudget = """{"totalActions":1,"steps":[{"kind":"FREE","text":"A"},{"kind":"FREE","text":"B"}],"unresolved":false}"""
    assertThrows(IllegalArgumentException::class.java) { PlayerActionPlan.parse(overBudget, -1, 0) }
  }
}
