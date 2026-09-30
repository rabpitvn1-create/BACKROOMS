package com.rabpit.backroom.core;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CaoMinhVoiceContractTest {
  @Test public void voiceLayerIsScopedToCaoMinhAndPreservesPlayerAgency() {
    String prompt = CaoMinhVoiceContract.promptContext();

    assertTrue(prompt.contains("Chỉ áp dụng lớp giọng này cho lời thoại trực tiếp của Cao Minh"));
    assertTrue(prompt.contains("Không ép lời kể Game Master, NPC hay companion"));
    assertTrue(prompt.contains("không được phá quyền điều khiển của người chơi"));
    assertTrue(prompt.contains("Không tự thêm lời nói, ý định hay quyết định cho Cao Minh"));
  }

  @Test public void voiceLayerUsesCultivationRegisterInsteadOfModernFutureSpeech() {
    String prompt = CaoMinhVoiceContract.promptContext();

    assertTrue(prompt.contains("thế giới tu tiên"));
    assertTrue(prompt.contains("Ma Đạo Kiếm Tu"));
    assertTrue(prompt.contains("'ta'"));
    assertTrue(prompt.contains("'ngươi'"));
    assertTrue(prompt.contains("'các hạ'"));
    assertTrue(prompt.contains("'cô nương'"));
    assertTrue(prompt.contains("không cho hắn mặc định nói như người hiện đại"));
    assertTrue(prompt.contains("Ok, team chia ra check hành lang"));
    assertTrue(prompt.contains("không tự biết Level, Entity"));
  }
}
