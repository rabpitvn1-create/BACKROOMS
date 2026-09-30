package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class CanonRetrieverTest {
  private static Map<String, String> wiki() {
    Map<String, String> files = new LinkedHashMap<>();
    files.put("BACKROOMS_WORLD.md", "# World\n## Tầng 0 — Lobby\nYellow walls.\n");
    files.put("Cao_Minh_Codex.md", "# Cao Minh\n## Định danh\nA player.\n");
    files.put("Lục_Trầm_Codex.md", "# Lục Trầm\n## Hồ sơ nhanh\nA sword user.\n");
    files.put("Entity.md", "# Entity\n## Wretch\nA hostile entity.\n");
    files.put("Other.md", "# Other\n## Táng Kiếm Cốc\nA different file.\n"
        + "<!-- canon: aliases=Valley of Buried Swords -->\n");
    return files;
  }
  private static JSONObject state() throws Exception {
    return new JSONObject().put("currentLevelKey", "0")
        .put("party", new JSONArray().put(new JSONObject().put("id", "luc_tram").put("present", true))
            .put(new JSONObject().put("id", "absent_friend").put("present", false)))
        .put("flags", new JSONObject().put("entityEncounterKey", "wretch"));
  }
  @Test public void parserKeepsPreamblePathsFencesRawAndStableIds() {
    Map<String, String> files = Map.of("new.md", "Preamble\n# Root\nParent\n```md\n## Fake\n```\n## Child\nRaw\n");
    CanonRetriever index = new CanonRetriever(files);
    assertEquals(3, index.sections().size());
    assertEquals("Preamble\n", index.sections().get(0).rawText);
    assertEquals("Root / Child", index.sections().get(2).headingPath);
    assertFalse(index.sections().get(1).rawText.contains("Raw"));
    assertTrue(index.sections().get(1).rawText.contains("## Fake"));
    assertEquals(index.sections().get(2).sectionId,
        new CanonRetriever(files).sections().get(2).sectionId);
  }
  @Test public void newFileAutomaticallyIndexesAndSearchesAcrossWiki() throws Exception {
    Map<String, String> files = wiki();
    files.put("Added.md", "# New Subject\n## Crystal Nexus\nDistinct fact.\n");
    CanonRetriever index = new CanonRetriever(files);
    assertTrue(index.sections().stream().anyMatch(s -> s.sourceFile.equals("Added.md")));
    CanonRetriever.CanonPacket found = index.retrieve(state(), "Crystal Nexus", 3000, true);
    assertTrue(found.supplemental.stream().anyMatch(s -> s.section.sourceFile.equals("Added.md")));
    assertFalse(found.promptText().contains("A different file."));
  }
  @Test public void committedSubjectsAreMandatoryRegardlessOfAction() throws Exception {
    CanonRetriever.CanonPacket packet = new CanonRetriever(wiki()).retrieve(state(), "Tôi đợi.", 3000, false);
    assertTrue(packet.missingMandatoryRefs.toString(), packet.missingMandatoryRefs.isEmpty());
    String mandatory = packet.mandatory.toString();
    assertEquals(4, packet.mandatory.size());
    assertTrue(packet.promptText().contains("Yellow walls."));
    assertTrue(packet.promptText().contains("A hostile entity."));
    assertTrue(packet.promptText().contains("A sword user."));
    assertFalse(mandatory.contains("absent_friend"));
  }
  @Test public void exactHeadingAliasAndUnrelatedExclusion() throws Exception {
    CanonRetriever index = new CanonRetriever(wiki());
    for (String query : new String[]{"Táng Kiếm Cốc", "Valley of Buried Swords"}) {
      CanonRetriever.CanonPacket packet = index.retrieve(state(), query, 3000, false);
      assertTrue(packet.supplemental.stream().anyMatch(s -> s.section.sourceFile.equals("Other.md")));
    }
    assertTrue(index.retrieve(state(), "Tôi đợi.", 3000, false).supplemental.isEmpty());
  }
  @Test public void conflictingWorldLevelNameCannotOverrideCommittedLevel() throws Exception {
    Map<String, String> files = wiki();
    files.put("BACKROOMS_WORLD.md", files.get("BACKROOMS_WORLD.md")
        + "## Level 0.5 — Chaotic Structure\nWrong for this runtime.\n");
    JSONObject state = state().put("currentLevelKey", "0.5");
    CanonRetriever.CanonPacket packet = new CanonRetriever(files).retrieve(
        state, "Level 0.5", 3000, true, "Level 0.5 — Aquaclaustrophobic Infirmary");
    assertTrue(packet.missingMandatoryRefs.contains("level:0.5"));
    assertFalse(packet.promptText().contains("Wrong for this runtime."));
  }
  @Test public void importedLevelZeroPointFiveMatchesCommittedLevel() throws Exception {
    Path source = Paths.get("src/main/assets/canon/BACKROOMS_WORLD.md");
    if (!Files.isRegularFile(source)) source = Paths.get("app/src/main/assets/canon/BACKROOMS_WORLD.md");
    Map<String, String> files = wiki();
    files.put("BACKROOMS_WORLD.md", new String(Files.readAllBytes(source), StandardCharsets.UTF_8));
    JSONObject state = new JSONObject().put("currentLevelKey", "0.5").put("party", new JSONArray());
    CanonRetriever.CanonPacket packet = new CanonRetriever(files).retrieve(state, "Tôi đi tiếp.",
        CanonRetriever.DEFAULT_BUDGET, true, "Level 0.5 — Aquaclaustrophobic Infirmary");
    assertFalse(packet.missingMandatoryRefs.toString(), packet.missingMandatoryRefs.contains("level:0.5"));
    assertTrue(packet.promptText().contains("Waterlogged Passages"));
    assertFalse(packet.promptText().contains("Chaotic Structure"));
    assertFalse(packet.budgetExceeded);
  }

  @Test public void nonNumericLevelDisplayNameCanProvideMandatoryWorldCore() throws Exception {
    Map<String, String> files = wiki();
    files.put("BACKROOMS_WORLD_SUBLEVELS_1_6.md",
        "# Sublevels\n## Base Alpha\n<!-- canon: aliases=Base Alpha; core=true -->\n"
            + "BASE_ALPHA_CANON_FACT.\n");
    JSONObject state = new JSONObject().put("currentLevelKey", "base_alpha")
        .put("party", new JSONArray());
    CanonRetriever.CanonPacket packet = new CanonRetriever(files).retrieve(
        state, "Tôi quan sát khu căn cứ.", 3000, true, "Base Alpha");
    assertFalse(packet.missingMandatoryRefs.toString(),
        packet.missingMandatoryRefs.contains("level:base_alpha"));
    assertTrue(packet.promptText().contains("BASE_ALPHA_CANON_FACT"));
  }

  @Test public void dependenciesCyclesMissingRefsAndBudgetAreVisible() throws Exception {
    Map<String, String> files = wiki();
    files.put("Extra.md", "# Extra\n## A\n<!-- canon: aliases=Alpha; requires=extra::extra_b -->\nA.\n"
        + "## B\n<!-- canon: requires=extra::extra_a,missing::ref -->\nB.\n");
    CanonRetriever index = new CanonRetriever(files);
    CanonRetriever.CanonPacket missing = index.retrieve(state(), "Alpha", 3000, true);
    assertFalse(missing.missingRefs.isEmpty());
    assertTrue(missing.supplemental.isEmpty());
    files.put("Extra.md", files.get("Extra.md").replace(",missing::ref", ""));
    CanonRetriever.CanonPacket selected = new CanonRetriever(files).retrieve(state(), "Alpha", 3000, true);
    assertEquals(1, selected.supplemental.size());
    assertEquals(1, selected.dependencies.size());
    CanonRetriever.CanonPacket tight = new CanonRetriever(files).retrieve(state(), "Alpha", 1, false);
    assertTrue(tight.budgetExceeded);
    assertEquals(4, tight.mandatory.size());
    assertTrue(tight.supplemental.isEmpty());
    assertEquals(selected.promptText(), new CanonRetriever(files).retrieve(state(), "Alpha", 3000, true).promptText());
  }
}
