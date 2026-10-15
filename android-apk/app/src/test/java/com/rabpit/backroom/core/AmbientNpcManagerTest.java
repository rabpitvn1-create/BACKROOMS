package com.rabpit.backroom.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class AmbientNpcManagerTest {
  private static final String VALID =
    "{\"description\":\"Một người lạ đứng trước tường.\"," +
    "\"dialogues\":[\"Xin chào.\",\"Có ai ở đó không?\"]," +
    "\"exit\":\"Người lạ bước khỏi hành lang.\"}";

  @Test public void validSceneCyclesDialoguesWithoutAnotherGeneration() {
    AmbientNpcManager.AmbientNpc scene = AmbientNpcManager.parse(VALID);
    assertNotNull(scene);
    AmbientNpcManager manager = new AmbientNpcManager();
    manager.setActive(scene);
    assertEquals("Một người lạ đứng trước tường.", manager.getDescription());
    assertEquals("Xin chào.", manager.nextDialogue());
    assertEquals("Có ai ở đó không?", manager.nextDialogue());
    assertEquals("Xin chào.", manager.nextDialogue());
    assertEquals("Người lạ bước khỏi hành lang.", manager.endEncounter());
    assertFalse(manager.hasActive());
    assertNull(manager.nextDialogue());
    assertNull(manager.endEncounter());
  }

  @Test public void oneSceneCacheRestoresExactDialoguePositionAndClears() {
    AmbientNpcManager first = new AmbientNpcManager();
    first.setActive(AmbientNpcManager.parse(VALID));
    assertEquals("Xin chào.", first.nextDialogue());
    String cached = first.snapshot();
    assertNotNull(cached);

    AmbientNpcManager restarted = new AmbientNpcManager();
    assertTrue(restarted.restore(cached));
    assertEquals("Có ai ở đó không?", restarted.nextDialogue());
    assertEquals("Xin chào.", restarted.nextDialogue());
    assertEquals("Người lạ bước khỏi hành lang.", restarted.endEncounter());
    assertNull(restarted.snapshot());
    assertFalse(restarted.restore("{}"));
  }

  @Test public void rejectsInvalidPayloadWithoutActivatingNpc() {
    assertNull(AmbientNpcManager.parse(null));
    assertNull(AmbientNpcManager.parse("{}"));
    assertNull(AmbientNpcManager.parse("{\"description\":\"x\",\"dialogues\":[\"only one\"],\"exit\":\"bye\"}"));
    assertNull(AmbientNpcManager.parse("{\"description\":\"x\",\"dialogues\":[\"a\",\"b\"],\"exit\":\"Nhận vật phẩm quý.\"}"));
    AmbientNpcManager manager = new AmbientNpcManager();
    manager.setActive(AmbientNpcManager.parse(VALID));
    manager.clear();
    assertFalse(manager.hasActive());
    assertNull(manager.getDescription());
  }

  @Test public void generationPromptContainsOnlyGivenDynamicContext() {
    String prompt = AmbientNpcManager.buildPrompt("0", "Hành lang vàng.");
    assertTrue(prompt.contains("Level hiện tại: 0"));
    assertTrue(prompt.contains("Bối cảnh: Hành lang vàng."));
    assertFalse(prompt.contains("GEMINI_API_KEY"));
    assertFalse(prompt.contains("game_state"));
    assertNotNull(AmbientNpcManager.parse("```json\n" + VALID + "\n```"));
  }
}
