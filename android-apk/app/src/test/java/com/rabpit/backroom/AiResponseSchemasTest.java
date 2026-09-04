package com.rabpit.backroom;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AiResponseSchemasTest {
  @Test public void writerSchemaAcceptsCompleteResponse() throws Exception {
    String value = AiResponseSchemas.validate(AiResponseSchemas.Role.WRITER,
      "{\"reply\":\"Bạn nghe tiếng đèn.\",\"ops\":[],\"snapshotEvent\":{\"shouldGenerate\":false,\"kind\":\"\",\"reason\":\"\"}}");
    assertTrue(value.contains("Bạn nghe tiếng đèn"));
  }

  @Test public void writerSchemaRejectsUnknownOperation() {
    assertThrows(Exception.class, () -> AiResponseSchemas.validate(AiResponseSchemas.Role.WRITER,
      "{\"reply\":\"x\",\"ops\":[{\"type\":\"replace_state\"}],\"snapshotEvent\":{\"shouldGenerate\":false,\"kind\":\"\",\"reason\":\"\"}}"));
  }

  @Test public void auditSchemaRejectsContradictoryPassFlag() {
    assertThrows(Exception.class, () -> AiResponseSchemas.validate(AiResponseSchemas.Role.AUDIT,
      "{\"pass\":true,\"issues\":[{\"rule\":\"canon_conflict\",\"severity\":\"hard\",\"claim\":\"x\",\"reason\":\"y\"}]}"));
  }
}
