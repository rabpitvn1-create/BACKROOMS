package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeCostPolicyTest {
  @Test fun explicitVietnameseDurationsOverrideActionDefaults() {
    assertEquals(240, TimeCostPolicy.estimateMinutes("Cao Minh ngủ 4 giờ"))
    assertEquals(30, TimeCostPolicy.estimateMinutes("Cao Minh chờ 30 phút"))
    assertEquals(90, TimeCostPolicy.estimateMinutes("Nghỉ 1,5 giờ"))
    assertEquals(120, TimeCostPolicy.estimateMinutes("đợi hai tiếng"))
  }

  @Test fun actionCategoriesUseDifferentSubjectiveCosts() {
    assertEquals(10, TimeCostPolicy.estimateMinutes("Cao Minh đi tiếp dọc hành lang"))
    assertEquals(5, TimeCostPolicy.estimateMinutes("Cao Minh kiểm tra căn phòng"))
    assertEquals(1, TimeCostPolicy.estimateMinutes("Cao Minh hỏi người lạ anh là ai"))
    assertEquals(1, TimeCostPolicy.estimateMinutes("Cao Minh bắn vào mục tiêu"))
    assertEquals(30, TimeCostPolicy.estimateMinutes("Cao Minh nghỉ tại đây"))
    assertEquals(2, TimeCostPolicy.estimateMinutes("Cao Minh mở cửa"))
  }
}
