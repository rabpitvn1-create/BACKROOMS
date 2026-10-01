package com.rabpit.backroom.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class CanonRegistryTest {
  @Test public void shippedRegistryIsValidAndInventorySized() throws Exception {
    Path source = Paths.get("src/main/assets/canon/canon-registry.json");
    if (!Files.isRegularFile(source)) {
      source = Paths.get("app/src/main/assets/canon/canon-registry.json");
    }
    String raw = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);

    JSONObject validation = CanonRegistry.validate(raw);

    assertTrue(validation.getJSONArray("errors").toString(),
        validation.getBoolean("valid"));
    assertEquals(12, validation.getInt("sourceCount"));
  }

  @Test public void asyncHistoryIsMigratedToStructuredContentOnly() throws Exception {
    Path structured = Paths.get("src/main/assets/content/history/async-backroomsv2.md");
    Path legacy = Paths.get("src/main/assets/canon/ASYNC_BackroomsV2.md");
    if (!Files.isRegularFile(structured)) {
      structured = Paths.get("app/src/main/assets/content/history/async-backroomsv2.md");
      legacy = Paths.get("app/src/main/assets/canon/ASYNC_BackroomsV2.md");
    }

    assertTrue(Files.isRegularFile(structured));
    assertFalse(Files.exists(legacy));
    String text = new String(Files.readAllBytes(structured), StandardCharsets.UTF_8);
    assertTrue(text.contains("ASYNC RESEARCH INSTITUTE"));
    assertTrue(text.contains("Project KV31"));
  }

  @Test public void entityVisualReferenceIsMigratedToStructuredContentOnly() throws Exception {
    Path structured = Paths.get(
        "src/main/assets/content/entities/entity-visual-reference.md");
    Path legacy = Paths.get("src/main/assets/canon/Entity.md");
    if (!Files.isRegularFile(structured)) {
      structured = Paths.get(
          "app/src/main/assets/content/entities/entity-visual-reference.md");
      legacy = Paths.get("app/src/main/assets/canon/Entity.md");
    }

    assertTrue(Files.isRegularFile(structured));
    assertFalse(Files.exists(legacy));
    String text = new String(Files.readAllBytes(structured), StandardCharsets.UTF_8);
    assertTrue(text.contains("MÔ TẢ THỊ GIÁC THỰC THỂ"));
    assertTrue(text.contains("## Wretch"));
  }

  @Test public void backroomsAuraIsMigratedToStructuredContentOnly() throws Exception {
    Path structured = Paths.get(
        "src/main/assets/content/phenomena/backrooms-linh-khi.md");
    Path legacy = Paths.get("src/main/assets/canon/Backrooms_Linh_Khi.md");
    if (!Files.isRegularFile(structured)) {
      structured = Paths.get(
          "app/src/main/assets/content/phenomena/backrooms-linh-khi.md");
      legacy = Paths.get("app/src/main/assets/canon/Backrooms_Linh_Khi.md");
    }

    assertTrue(Files.isRegularFile(structured));
    assertFalse(Files.exists(legacy));
    String text = new String(Files.readAllBytes(structured), StandardCharsets.UTF_8);
    assertTrue(text.contains("LINH KHÍ TRONG BACKROOMS"));
    assertTrue(text.contains("Nguồn gốc của lượng linh khí này là **UNKNOWN**"));
  }

  @Test public void cultivatorAuraEffectsAreMigratedToStructuredContentOnly() throws Exception {
    Path structured = Paths.get(
        "src/main/assets/content/phenomena/backrooms-linh-khi-anh-huong-tu-si.md");
    Path legacy = Paths.get(
        "src/main/assets/canon/Backrooms_Linh_Khi_Anh_Huong_Tu_Si.md");
    if (!Files.isRegularFile(structured)) {
      structured = Paths.get(
          "app/src/main/assets/content/phenomena/backrooms-linh-khi-anh-huong-tu-si.md");
      legacy = Paths.get(
          "app/src/main/assets/canon/Backrooms_Linh_Khi_Anh_Huong_Tu_Si.md");
    }

    assertTrue(Files.isRegularFile(structured));
    assertFalse(Files.exists(legacy));
    String text = new String(Files.readAllBytes(structured), StandardCharsets.UTF_8);
    assertTrue(text.contains("ẢNH HƯỞNG CỦA LINH KHÍ BACKROOMS LÊN TU SĨ"));
    assertTrue(text.contains("linh khí nhiều = đột phá tự động"));
    assertTrue(text.contains("không được suy thành Cao Minh có ma nguyên vô hạn"));
  }

  @Test public void caoFamilyMassacreHistoryIsMigratedToStructuredContentOnly() throws Exception {
    Path structured = Paths.get(
        "src/main/assets/content/history/huyet-tay-cao-gia.md");
    Path legacy = Paths.get("src/main/assets/canon/Huyet_Tay_Cao_Gia.md");
    if (!Files.isRegularFile(structured)) {
      structured = Paths.get(
          "app/src/main/assets/content/history/huyet-tay-cao-gia.md");
      legacy = Paths.get("app/src/main/assets/canon/Huyet_Tay_Cao_Gia.md");
    }

    assertTrue(Files.isRegularFile(structured));
    assertFalse(Files.exists(legacy));
    String text = new String(Files.readAllBytes(structured), StandardCharsets.UTF_8);
    assertTrue(text.contains("Cao gia có hơn bốn trăm người"));
    assertTrue(text.contains("Diệp Minh"));
    assertTrue(text.contains("Cao Minh trở về"));
  }

  @Test public void duplicateIdAndPathFailClosed() throws Exception {
    JSONObject source = source(
        "same", "A.md", "world/a.md", "WORLD", "WORLD_CANON", "CURRENT");
    JSONObject duplicate = source(
        "same", "A.md", "world/a.md", "WORLD", "WORLD_CANON", "CURRENT");
    JSONObject registry = registry(source, duplicate);

    JSONObject validation = CanonRegistry.validate(registry);
    String errors = validation.getJSONArray("errors").toString();

    assertFalse(validation.getBoolean("valid"));
    assertTrue(errors.contains("source_id_duplicate:same"));
    assertTrue(errors.contains("source_path_duplicate:A.md"));
    assertTrue(errors.contains("source_content_path_duplicate:world/a.md"));
  }

  @Test public void missingDependencyAndCycleAreVisible() throws Exception {
    JSONObject a = source(
        "a", "A.md", "world/a.md", "WORLD", "WORLD_CANON", "CURRENT");
    JSONObject b = source(
        "b", "B.md", "history/b.md", "HISTORY", "UNCLASSIFIED", "UNCLASSIFIED");
    a.put("dependencies", new JSONArray().put("b"));
    b.put("dependencies", new JSONArray().put("a").put("missing"));

    JSONObject validation = CanonRegistry.validate(registry(a, b));
    String errors = validation.getJSONArray("errors").toString();

    assertFalse(validation.getBoolean("valid"));
    assertTrue(errors.contains("dependency_missing:b->missing"));
    assertTrue(errors.contains("dependency_cycle:a->b->a"));
  }

  @Test public void unknownAuthorityAndUnknownFieldFailClosed() throws Exception {
    JSONObject source = source(
        "a", "A.md", "world/a.md", "WORLD", "NOT_REAL", "CURRENT")
        .put("mystery", true);

    JSONObject validation = CanonRegistry.validate(registry(source));
    String errors = validation.getJSONArray("errors").toString();

    assertFalse(validation.getBoolean("valid"));
    assertTrue(errors.contains("source_authority_invalid:a"));
    assertTrue(errors.contains("unknown_field:source:0:mystery"));
  }

  @Test public void invalidStructuredContentPathFailsClosed() throws Exception {
    JSONObject source = source(
        "world", "World.md", "../outside.md", "WORLD", "WORLD_CANON", "CURRENT");

    JSONObject validation = CanonRegistry.validate(registry(source));

    assertFalse(validation.getBoolean("valid"));
    assertTrue(validation.getJSONArray("errors").toString()
        .contains("source_content_path_invalid:world"));
  }

  @Test public void characterAuthorityRequiresOwner() throws Exception {
    JSONObject source = source(
        "cao-minh", "Cao_Minh_Codex.md", "characters/cao-minh.md",
        "CHARACTER", "CHARACTER_CANON", "CURRENT");

    JSONObject validation = CanonRegistry.validate(registry(source));

    assertFalse(validation.getBoolean("valid"));
    assertTrue(validation.getJSONArray("errors").toString()
        .contains("source_owner_required:cao-minh"));
  }

  @Test public void byIdReturnsDefensiveCopiesOnlyForValidRegistry() throws Exception {
    JSONObject source = source(
        "world", "World.md", "world/world.md", "WORLD", "WORLD_CANON", "CURRENT");
    JSONObject registry = registry(source);

    JSONObject copy = CanonRegistry.byId(registry).get("world");
    copy.put("status", "UNCLASSIFIED");

    assertEquals("CURRENT",
        registry.getJSONArray("sources").getJSONObject(0).getString("status"));
  }

  private static JSONObject registry(JSONObject... sources) throws Exception {
    JSONArray array = new JSONArray();
    for (JSONObject source : sources) array.put(source);
    return new JSONObject()
        .put("schemaVersion", CanonRegistry.SCHEMA_VERSION)
        .put("sources", array);
  }

  private static JSONObject source(
      String id, String path, String contentPath, String type, String authority, String status)
      throws Exception {
    return new JSONObject()
        .put("id", id)
        .put("path", path)
        .put("contentPath", contentPath)
        .put("type", type)
        .put("authority", authority)
        .put("status", status)
        .put("version", "")
        .put("owner", "")
        .put("dependencies", new JSONArray())
        .put("mandatoryFor", new JSONArray())
        .put("supersedes", new JSONArray())
        .put("note", "");
  }
}
