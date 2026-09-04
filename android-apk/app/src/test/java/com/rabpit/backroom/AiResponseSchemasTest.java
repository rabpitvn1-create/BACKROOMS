package com.rabpit.backroom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AiResponseSchemasTest {
  @Test public void writerSchemaAcceptsCompleteResponse() throws Exception {
    String value = AiResponseSchemas.validate(AiResponseSchemas.Role.WRITER,
      "{\"reply\":\"Bạn nghe tiếng đèn.\",\"ops\":[],\"snapshotEvent\":{\"shouldGenerate\":false,\"kind\":\"\",\"reason\":\"\"}}");
    assertTrue(value.contains("Bạn nghe tiếng đèn"));
  }

  @Test public void writerSchemaRejectsUnknownOperationWithRoleAndReason() {
    AiResponseSchemas.ValidationException error = assertThrows(AiResponseSchemas.ValidationException.class,
      () -> AiResponseSchemas.validate(AiResponseSchemas.Role.WRITER,
        "{\"reply\":\"x\",\"ops\":[{\"type\":\"replace_state\"}],\"snapshotEvent\":{\"shouldGenerate\":false,\"kind\":\"\",\"reason\":\"\"}}"));
    assertEquals(AiResponseSchemas.Role.WRITER, error.role());
    assertTrue(error.reason().contains("unsupported op type: replace_state"));
    assertTrue(error.getMessage().contains("[WRITER]"));
  }

  @Test public void auditSchemaRejectsContradictoryPassFlagWithAuditRole() {
    AiResponseSchemas.ValidationException error = assertThrows(AiResponseSchemas.ValidationException.class,
      () -> AiResponseSchemas.validate(AiResponseSchemas.Role.AUDIT,
        "{\"pass\":true,\"issues\":[{\"rule\":\"canon_conflict\",\"severity\":\"hard\",\"claim\":\"x\",\"reason\":\"y\"}]}"));
    assertEquals(AiResponseSchemas.Role.AUDIT, error.role());
    assertTrue(error.reason().contains("pass must agree with issues"));
  }

  @Test public void malformedJsonReportsRoleAndJsonReason() {
    AiResponseSchemas.ValidationException error = assertThrows(AiResponseSchemas.ValidationException.class,
      () -> AiResponseSchemas.validate(AiResponseSchemas.Role.REPAIR, "{not-json"));
    assertEquals(AiResponseSchemas.Role.REPAIR, error.role());
    assertTrue(error.reason().contains("invalid JSON"));
  }
}
