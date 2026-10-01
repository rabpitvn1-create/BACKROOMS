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
  @Test public void compatibilityLoaderPrefersStructuredContentButKeepsLegacyIdentity()
      throws Exception {
    JSONObject registry = new JSONObject().put("schemaVersion", 1)
        .put("sources", new JSONArray().put(new JSONObject()
            .put("id", "async").put("path", "ASYNC.md")
            .put("contentPath", "history/async.md")
            .put("type", "HISTORY").put("authority", "UNCLASSIFIED")
            .put("status", "UNCLASSIFIED").put("version", "").put("owner", "")
            .put("dependencies", new JSONArray()).put("mandatoryFor", new JSONArray())
            .put("supersedes", new JSONArray()).put("note", "")));
    Map<String, String> legacy = new LinkedHashMap<>();
    legacy.put("ASYNC.md", "# Legacy\nlegacy");
    legacy.put("Extra.md", "# Extra\nextra");
    Map<String, String> structured = new LinkedHashMap<>();
    structured.put("history/async.md", "# Structured\nstructured");

    Map<String, String> resolved =
        CanonRetriever.resolveRegistrySources(registry, legacy, structured);
    CanonRetriever index = new CanonRetriever(resolved);

    assertEquals("# Structured\nstructured", resolved.get("ASYNC.md"));
    assertFalse(resolved.containsKey("Extra.md"));
    assertTrue(index.sections().stream()
        .anyMatch(s -> s.sourceFile.equals("ASYNC.md") && s.rawText.contains("structured")));
  }

  @Test public void compatibilityLoaderFallsBackToLegacyAndFailsIfBothCopiesMissing()
      throws Exception {
    JSONObject source = new JSONObject()
        .put("id", "history").put("path", "History.md")
        .put("contentPath", "history/history.md")
        .put("type", "HISTORY").put("authority", "UNCLASSIFIED")
        .put("status", "UNCLASSIFIED").put("version", "").put("owner", "")
        .put("dependencies", new JSONArray()).put("mandatoryFor", new JSONArray())
        .put("supersedes", new JSONArray()).put("note", "");
    JSONObject registry = new JSONObject().put("schemaVersion", 1)
        .put("sources", new JSONArray().put(source));
    Map<String, String> legacy = new LinkedHashMap<>();
    legacy.put("History.md", "# History\nlegacy");

    assertEquals("# History\nlegacy",
        CanonRetriever.resolveRegistrySources(registry, legacy, new LinkedHashMap<>())
            .get("History.md"));

    try {
      CanonRetriever.resolveRegistrySources(
          registry, new LinkedHashMap<>(), new LinkedHashMap<>());
      fail("Missing registered source must fail closed");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("Registered canon source is missing"));
    }
  }

  @Test public void registryMandatoryBindingOverridesFilenameHeuristic() throws Exception {
    Map<String, String> files = new LinkedHashMap<>();
    files.put("BACKROOMS_WORLD.md", "# World\n## Tầng 0 — Lobby\nWorld.\n");
    files.put("Cao_Minh_Codex.md", "# Wrong\n## Định danh\nWRONG_CAO.\n");
    files.put("Hero.md", "# Hero\n## Identity\nREGISTRY_CAO.\n");

    JSONObject hero = registrySource(
        "hero", "Hero.md", "characters/hero.md", "CHARACTER", "CHARACTER_CANON", "CURRENT",
        new JSONArray().put("character:cao_minh"));
    JSONObject registry = new JSONObject().put("schemaVersion", 1)
        .put("sources", new JSONArray().put(hero));

    JSONObject state = new JSONObject().put("currentLevelKey", "0")
        .put("party", new JSONArray()).put("flags", new JSONObject());
    CanonRetriever.CanonPacket packet =
        new CanonRetriever(files, registry).retrieve(state, "đợi", 3000, true);

    assertTrue(packet.promptText().contains("REGISTRY_CAO"));
    assertFalse(packet.promptText().contains("WRONG_CAO"));
    assertTrue(packet.mandatory.stream()
        .anyMatch(s -> "Hero.md".equals(s.section.sourceFile)));
  }

  @Test public void registryPolicyExcludesUnclassifiedAndCandidateSupplementalSources()
      throws Exception {
    Map<String, String> files = new LinkedHashMap<>();
    files.put("BACKROOMS_WORLD.md", "# World\n## Tầng 0 — Lobby\nWorld.\n");
    files.put("Hero.md", "# Hero\n## Identity\nHero.\n");
    files.put("History.md", "# History\n## Forbidden Topic\nUNCLASSIFIED_FACT.\n");
    files.put("Candidate.md", "# Candidate\n## Candidate Topic\nCANDIDATE_FACT.\n");
    files.put("Reference.md", "# Reference\n## Visual Topic\nREFERENCE_FACT.\n");

    JSONArray sources = new JSONArray()
        .put(registrySource("hero", "Hero.md", "characters/hero.md", "CHARACTER",
            "CHARACTER_CANON", "CURRENT", new JSONArray().put("character:cao_minh")))
        .put(registrySource("history", "History.md", "history/history.md", "HISTORY",
            "UNCLASSIFIED", "UNCLASSIFIED", new JSONArray()))
        .put(registrySource("candidate", "Candidate.md", "phenomena/candidate.md", "ENVIRONMENT",
            "UNCLASSIFIED", "CANDIDATE", new JSONArray()))
        .put(registrySource("reference", "Reference.md", "entities/reference.md", "ENTITY_REFERENCE",
            "REFERENCE", "REFERENCE", new JSONArray()));
    JSONObject registry = new JSONObject().put("schemaVersion", 1).put("sources", sources);
    JSONObject state = new JSONObject().put("currentLevelKey", "0")
        .put("party", new JSONArray()).put("flags", new JSONObject());

    CanonRetriever index = new CanonRetriever(files, registry);
    assertTrue(index.retrieve(state, "Visual Topic", 3000, true)
        .promptText().contains("REFERENCE_FACT"));
    assertFalse(index.retrieve(state, "Forbidden Topic", 3000, true)
        .promptText().contains("UNCLASSIFIED_FACT"));
    assertFalse(index.retrieve(state, "Candidate Topic", 3000, true)
        .promptText().contains("CANDIDATE_FACT"));
  }

  @Test public void registryDependenciesAndSupersedesAreDeterministic() throws Exception {
    Map<String, String> files = new LinkedHashMap<>();
    files.put("BACKROOMS_WORLD.md", "# World\n## Tầng 0 — Lobby\nWorld.\n");
    files.put("Hero.md", "# Hero\n## Identity\nHero.\n");
    files.put("Current.md", "# Current\n## Current Topic\nCURRENT_FACT.\n");
    files.put("Dependency.md", "# Dependency\n## Base\nDEPENDENCY_FACT.\n");
    files.put("Old.md", "# Old\n## Old Topic\nOLD_FACT.\n");

    JSONObject hero = registrySource("hero", "Hero.md", "characters/hero.md", "CHARACTER",
        "CHARACTER_CANON", "CURRENT", new JSONArray().put("character:cao_minh"));
    JSONObject current = registrySource("current", "Current.md", "world/current.md", "WORLD",
        "WORLD_CANON", "CURRENT", new JSONArray())
        .put("dependencies", new JSONArray().put("dependency"))
        .put("supersedes", new JSONArray().put("old"));
    JSONObject dependency = registrySource("dependency", "Dependency.md", "world/dependency.md",
        "WORLD", "WORLD_CANON", "CURRENT", new JSONArray());
    JSONObject old = registrySource("old", "Old.md", "world/old.md", "WORLD",
        "WORLD_CANON", "CURRENT", new JSONArray());
    JSONObject registry = new JSONObject().put("schemaVersion", 1)
        .put("sources", new JSONArray().put(hero).put(current).put(dependency).put(old));
    JSONObject state = new JSONObject().put("currentLevelKey", "0")
        .put("party", new JSONArray()).put("flags", new JSONObject());

    CanonRetriever index = new CanonRetriever(files, registry);
    CanonRetriever.CanonPacket selected = index.retrieve(state, "Current Topic", 3000, true);
    assertTrue(selected.promptText().contains("CURRENT_FACT"));
    assertTrue(selected.promptText().contains("DEPENDENCY_FACT"));
    assertFalse(index.retrieve(state, "Old Topic", 3000, true).promptText().contains("OLD_FACT"));
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
    Path source = Paths.get("src/main/assets/content/world/backrooms-world.md");
    if (!Files.isRegularFile(source)) {
      source = Paths.get("app/src/main/assets/content/world/backrooms-world.md");
    }
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

  @Test public void canonPacketExposesDeterministicAuthorityMetadataWithoutChangingPrompt()
      throws Exception {
    Map<String, String> files = new LinkedHashMap<>();
    files.put("BACKROOMS_WORLD.md", "# World\n## Tầng 0 — Lobby\nWorld.\n");
    files.put("Hero.md", "# Hero\n## Identity\nHero.\n");
    files.put("Current.md", "# Current\n## Current Topic\nCURRENT_FACT.\n");
    files.put("Dependency.md", "# Dependency\n## Base\nDEPENDENCY_FACT.\n");

    JSONObject hero = registrySource("hero", "Hero.md", "characters/hero.md", "CHARACTER",
        "CHARACTER_CANON", "CURRENT", new JSONArray().put("character:cao_minh"));
    JSONObject current = registrySource("current", "Current.md", "world/current.md", "WORLD",
        "WORLD_CANON", "CURRENT", new JSONArray())
        .put("version", "R1")
        .put("dependencies", new JSONArray().put("dependency"));
    JSONObject dependency = registrySource("dependency", "Dependency.md", "world/dependency.md",
        "WORLD", "PROJECT_OVERRIDE", "CURRENT", new JSONArray());
    JSONObject registry = new JSONObject().put("schemaVersion", 1)
        .put("sources", new JSONArray().put(hero).put(current).put(dependency));
    JSONObject state = new JSONObject().put("currentLevelKey", "0")
        .put("party", new JSONArray()).put("flags", new JSONObject());

    CanonRetriever.CanonPacket packet =
        new CanonRetriever(files, registry).retrieve(state, "Current Topic", 4000, true);
    JSONArray metadata = packet.sourceMetadata();

    assertEquals(CanonRetriever.CanonPacket.CONTRACT_VERSION, packet.contractVersion);
    assertTrue(packet.promptText().contains("CURRENT_FACT"));
    assertFalse(packet.promptText().contains("WORLD_CANON"));
    assertEquals(3, metadata.length());

    JSONObject currentMeta = null;
    for (int i = 0; i < metadata.length(); i++) {
      JSONObject source = metadata.getJSONObject(i);
      if ("current".equals(source.getString("id"))) currentMeta = source;
    }
    assertNotNull(currentMeta);
    assertEquals("WORLD_CANON", currentMeta.getString("authority"));
    assertEquals("CURRENT", currentMeta.getString("status"));
    assertEquals("R1", currentMeta.getString("version"));
    assertEquals("Current.md", currentMeta.getString("path"));
    assertEquals("world/current.md", currentMeta.getString("contentPath"));
    assertEquals("WORLD", currentMeta.getString("type"));
    assertEquals("", currentMeta.getString("owner"));
    JSONObject selection = currentMeta.getJSONArray("selectionReasons").getJSONObject(0);
    assertEquals("supplemental", selection.getString("role"));
    assertTrue(selection.getString("reason").startsWith("search:"));
    assertTrue(selection.getString("sectionId").startsWith("current::"));
    assertEquals("dependency", currentMeta.getJSONArray("dependencies").getString(0));

    metadata.getJSONObject(0).put("authority", "MUTATED");
    assertFalse(packet.sourceMetadata().toString().contains("MUTATED"));
  }

  @Test public void explicitWriterMarkersAndInheritedSecretHeadingsCannotEnterPrompt() throws Exception {
    Map<String, String> files = wiki();
    files.put("Secrets.md", "# KNOWLEDGE LOCK\n## Public-looking child\nHIDDEN_FACT\n"
        + "# Ordinary\nPUBLIC_FACT\n");
    CanonRetriever index = new CanonRetriever(files);
    assertFalse(index.retrieve(state(), "Public-looking child", 4000, true)
        .promptText().contains("HIDDEN_FACT"));
    assertTrue(index.retrieve(state(), "Ordinary", 4000, true).promptText().contains("PUBLIC_FACT"));
    files.put("Extra.md", "# Extra\n## Alpha\n"
        + "<!-- canon: requires=secrets::knowledge_lock_public_looking_child -->\nA\n");
    CanonRetriever.CanonPacket blocked = new CanonRetriever(files).retrieve(state(), "Alpha", 4000, true);
    assertTrue(blocked.supplemental.isEmpty());
    assertFalse(blocked.missingRefs.isEmpty());
    assertFalse(blocked.promptText().contains("HIDDEN_FACT"));
  }

  @Test public void sectionRequiresCannotBypassSupersedesAndMandatoryFailureIsExplicit() throws Exception {
    Map<String, String> files = wiki();
    files.put("Hero.md", "# Hero\n## Identity\nHero\n<!-- canon: requires=old::old_topic -->\n");
    files.put("Current.md", "# Current\nCurrent\n");
    files.put("Old.md", "# Old Topic\nOLD_FACT\n");
    JSONObject hero = registrySource("hero", "Hero.md", "characters/hero.md", "CHARACTER",
        "CHARACTER_CANON", "CURRENT", new JSONArray().put("character:cao_minh"));
    JSONObject current = registrySource("current", "Current.md", "world/current.md", "WORLD",
        "WORLD_CANON", "CURRENT", new JSONArray()).put("supersedes", new JSONArray().put("old"));
    JSONObject old = registrySource("old", "Old.md", "world/old.md", "WORLD",
        "WORLD_CANON", "CURRENT", new JSONArray());
    JSONObject registry = new JSONObject().put("schemaVersion", 1)
        .put("sources", new JSONArray().put(hero).put(current).put(old));
    CanonRetriever.CanonPacket packet = new CanonRetriever(files, registry)
        .retrieve(state(), "đợi", 4000, true);
    assertFalse(packet.requiredComplete);
    assertFalse(packet.missingRefs.isEmpty());
    assertFalse(packet.promptText().contains("OLD_FACT"));
  }

  @Test public void mandatoryHeuristicsCannotBypassRegistryStatusOrSupersedes() throws Exception {
    Map<String, String> files = new LinkedHashMap<>();
    files.put("BACKROOMS_WORLD.md", "# World\n## Tầng 0 — Lobby\nOLD_WORLD_FACT\n");
    files.put("Hero.md", "# Hero\n## Identity\nHero\n");
    JSONObject old = registrySource("world", "BACKROOMS_WORLD.md", "world/world.md", "WORLD",
        "WORLD_CANON", "CURRENT", new JSONArray());
    JSONObject hero = registrySource("hero", "Hero.md", "characters/hero.md", "CHARACTER",
        "CHARACTER_CANON", "CURRENT", new JSONArray().put("character:cao_minh"))
        .put("supersedes", new JSONArray().put("world"));
    JSONObject registry = new JSONObject().put("schemaVersion", 1)
        .put("sources", new JSONArray().put(old).put(hero));
    CanonRetriever.CanonPacket packet = new CanonRetriever(files, registry).retrieve(state(), "đợi", 4000, true);
    assertTrue(packet.missingMandatoryRefs.contains("level:0"));
    assertFalse(packet.promptText().contains("OLD_WORLD_FACT"));
    hero.put("supersedes", new JSONArray());
    old.put("status", "CANDIDATE");
    assertFalse(new CanonRetriever(files, registry).retrieve(state(), "đợi", 4000, true)
        .promptText().contains("OLD_WORLD_FACT"));
    assertTrue(new CanonRetriever(files).retrieve(state(), "đợi", 4000, true)
        .promptText().contains("OLD_WORLD_FACT"));
  }

  @Test public void shippedRegistryRetrievalIsReadOnlyBudgetedAndDeterministic() throws Exception {
    Path assets = Paths.get("app/src/main/assets");
    if (!Files.isDirectory(assets)) assets = Paths.get("src/main/assets");
    JSONObject registry = new JSONObject(Files.readString(assets.resolve("canon/canon-registry.json")));
    Map<String, String> physical = new LinkedHashMap<>();
    JSONArray sources = registry.getJSONArray("sources");
    JSONArray reversed = new JSONArray();
    for (int i = 0; i < sources.length(); i++) {
      JSONObject source = sources.getJSONObject(i);
      String path = source.getString("contentPath");
      physical.put(path, Files.readString(assets.resolve("content").resolve(path)));
      reversed.put(sources.getJSONObject(sources.length() - i - 1));
    }
    Map<String, String> resolved = CanonRetriever.resolveRegistrySources(registry, Map.of(), physical);
    JSONObject scene = state().put("party", new JSONArray()
        .put(new JSONObject().put("id", "lucia").put("present", true))
        .put(new JSONObject().put("id", "luc_tram").put("present", true)));
    String before = scene.toString();
    CanonRetriever.CanonPacket packet = new CanonRetriever(resolved, registry)
        .retrieve(scene, "đợi", CanonRetriever.DEFAULT_BUDGET, true, "Level 0 — The Lobby");
    JSONObject reorder = new JSONObject().put("schemaVersion", 1).put("sources", reversed);
    CanonRetriever.CanonPacket same = new CanonRetriever(resolved, reorder)
        .retrieve(scene, "đợi", CanonRetriever.DEFAULT_BUDGET, true, "Level 0 — The Lobby");
    assertEquals(before, scene.toString());
    assertTrue(packet.requiredComplete);
    assertFalse(packet.budgetExceeded);
    assertTrue(packet.charCount <= CanonRetriever.DEFAULT_BUDGET);
    assertEquals(packet.promptText(), same.promptText());
    assertEquals(packet.sourceMetadata().toString(), same.sourceMetadata().toString());
    for (CanonRetriever.Selected selected : packet.all()) {
      assertTrue(KnowledgeContinuityFirewall.canExposeMarkdown(
          selected.section.headingPath, selected.section.rawText));
    }
    assertTrue(new CanonRetriever(resolved, registry).retrieve(scene, "đợi", 1, true).budgetExceeded);
  }

  private static JSONObject registrySource(
      String id, String path, String contentPath, String type, String authority, String status,
      JSONArray mandatoryFor) throws Exception {
    return new JSONObject()
        .put("id", id).put("path", path).put("contentPath", contentPath)
        .put("type", type).put("authority", authority).put("status", status)
        .put("version", "").put("owner", "CHARACTER".equals(type) ? id : "")
        .put("dependencies", new JSONArray()).put("mandatoryFor", mandatoryFor)
        .put("supersedes", new JSONArray()).put("note", "");
  }
}
