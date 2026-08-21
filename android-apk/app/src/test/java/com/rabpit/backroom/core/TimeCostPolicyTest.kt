package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeCostPolicyTest {
  @Test fun explicitVietnameseDurationsOverrideActionDefaults() {
    assertEquals(240, TimeCostPolicy.estimateMinutes("Kai ngủ 4 giờ"))
    assertEquals(30, TimeCostPolicy.estimateMinutes("Kai chờ 30 phút"))
    assertEquals(90, TimeCostPolicy.estimateMinutes("Nghỉ 1,5 giờ"))
    assertEquals(120, TimeCostPolicy.estimateMinutes("đợi hai tiếng"))
  }

  @Test fun actionCategoriesUseDifferentSubjectiveCosts() {
    assertEquals(10, TimeCostPolicy.estimateMinutes("Kai đi tiếp dọc hành lang"))
    assertEquals(5, TimeCostPolicy.estimateMinutes("Kai kiểm tra căn phòng"))
    assertEquals(1, TimeCostPolicy.estimateMinutes("Kai hỏi người lạ anh là ai"))
    assertEquals(1, TimeCostPolicy.estimateMinutes("Kai bắn vào mục tiêu"))
    assertEquals(30, TimeCostPolicy.estimateMinutes("Kai nghỉ tại đây"))
    assertEquals(2, TimeCostPolicy.estimateMinutes("Kai mở cửa"))
  }
}
