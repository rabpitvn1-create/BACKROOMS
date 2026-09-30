package com.rabpit.backroom.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GmNarratorContractTest {
  @Test public void promptContextEstablishesHybridGenreAndEvidenceDiscipline() {
    String prompt = GmNarratorContract.promptContext();

    assertTrue(prompt.contains("ngôi thứ ba hạn định"));
    assertTrue(prompt.contains("XIANXIA LÀ LĂNG KÍNH, BACKROOMS LÀ THỰC TẠI"));
    assertTrue(prompt.contains("thần thức"));
    assertTrue(prompt.contains("ma nguyên"));
    assertTrue(prompt.contains("pháp tắc"));
    assertTrue(prompt.contains("Không tự đổi cửa thành cấm chế"));
    assertTrue(prompt.contains("KỶ LUẬT BẰNG CHỨNG"));
    assertTrue(prompt.contains("PLAYER AGENCY"));
    assertFalse(prompt.endsWith("Bạn sẽ làm gì tiếp?"));
  }

  @Test public void caoMinhCardIsCompactAndMatchesCurrentTemperament() {
    String card = GmNarratorContract.caoMinhNarrativeCard();

    assertTrue(card.contains("Cao Minh / Vạn Giới Ma Tôn"));
    assertTrue(card.contains("Ma Đạo Kiếm Tu"));
    assertTrue(card.contains("bình thản"));
    assertTrue(card.contains("lười biếng"));
    assertTrue(card.contains("thích rượu"));
    assertTrue(card.contains("châm biếm"));
    assertTrue(card.contains("mặc định không biết tên Level"));
    assertTrue(card.length() < 800);
  }
}
