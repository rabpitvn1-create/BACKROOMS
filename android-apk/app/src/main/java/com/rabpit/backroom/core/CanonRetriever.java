package com.rabpit.backroom.core;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only, deterministic Markdown canon selector. No game state is modified. */
public final class CanonRetriever {
  public static final int DEFAULT_BUDGET = 5200;
  private static final Pattern HEADING = Pattern.compile("^(#{1,6})[ \\t]+(.+?)[ \\t]*#*[ \\t]*(?:\\r?\\n)?$");
  private static final Pattern META = Pattern.compile("<!--\\s*canon:\\s*(.*?)\\s*-->", Pattern.CASE_INSENSITIVE);
  private final List<Section> sections;
  private final Map<String, Section> byId;
  private final Map<String, JSONObject> registryByPath;
  private final boolean registryBacked;

  public static final class Section {
    public final String sourceFile, headingPath, sectionId, rawText, searchableText;
    public final List<String> aliases, requires, refs;
    public final boolean core;
    private final String heading, fileTerms;

    private Section(String file, List<String> path, String raw, int ordinal) {
      sourceFile = file;
      headingPath = String.join(" / ", path);
      heading = path.isEmpty() ? "" : path.get(path.size() - 1);
      sectionId = slug(file.replaceFirst("(?i)\\.md$", "")) + "::"
          + (path.isEmpty() ? "preamble" : slug(String.join("/", path)))
          + (ordinal > 1 ? "~" + ordinal : "");
      rawText = raw;
      fileTerms = normalize(file.replaceFirst("(?i)\\.md$", "").replace('_', ' '));
      searchableText = normalize(headingPath + " " + fileTerms + " " + raw);
      List<String> a = new ArrayList<>(), r = new ArrayList<>(), f = new ArrayList<>();
      boolean marked = false;
      Matcher m = META.matcher(raw);
      while (m.find()) for (String field : m.group(1).split(";")) {
        String[] pair = field.trim().split("=", 2);
        if (pair.length != 2) continue;
        List<String> dest = "aliases".equals(pair[0].trim()) ? a
            : "requires".equals(pair[0].trim()) ? r : "refs".equals(pair[0].trim()) ? f : null;
        if (dest != null) for (String value : pair[1].split(",")) {
          if (!value.trim().isEmpty()) dest.add(value.trim());
        }
        if ("core".equals(pair[0].trim())) marked = Boolean.parseBoolean(pair[1].trim());
      }
      aliases = Collections.unmodifiableList(a);
      requires = Collections.unmodifiableList(r);
      refs = Collections.unmodifiableList(f);
      core = marked;
    }
    public int size() { return rawText.length() + headingPath.length() + sourceFile.length() + 32; }
  }

  public static final class Selected {
    public final Section section;
    public final String reason;
    private Selected(Section section, String reason) { this.section = section; this.reason = reason; }
  }

  public static final class CanonPacket {
    public final List<Selected> mandatory, dependencies, supplemental;
    public final List<String> missingMandatoryRefs, missingRefs, trace;
    public final int charCount;
    public final boolean budgetExceeded;
    private CanonPacket(List<Selected> mandatory, List<Selected> dependencies,
        List<Selected> supplemental, List<String> missingMandatoryRefs, List<String> missingRefs,
        List<String> trace, int charCount, boolean budgetExceeded) {
      this.mandatory = Collections.unmodifiableList(new ArrayList<>(mandatory));
      this.dependencies = Collections.unmodifiableList(new ArrayList<>(dependencies));
      this.supplemental = Collections.unmodifiableList(new ArrayList<>(supplemental));
      this.missingMandatoryRefs = Collections.unmodifiableList(new ArrayList<>(missingMandatoryRefs));
      this.missingRefs = Collections.unmodifiableList(new ArrayList<>(missingRefs));
      this.trace = Collections.unmodifiableList(new ArrayList<>(trace));
      this.charCount = charCount;
      this.budgetExceeded = budgetExceeded;
    }
    public String promptText() {
      StringBuilder out = new StringBuilder();
      for (Selected selected : all()) {
        Section s = selected.section;
        out.append("\nSOURCE: ").append(s.sourceFile).append(" | ").append(s.headingPath)
            .append("\n").append(s.rawText).append('\n');
      }
      return out.toString();
    }
    public List<Selected> all() {
      List<Selected> all = new ArrayList<>(mandatory);
      all.addAll(dependencies);
      all.addAll(supplemental);
      return all;
    }
  }

  public static CanonRetriever fromAssets(Context context) throws Exception {
    Map<String, String> legacyFiles = new LinkedHashMap<>();
    String[] names = context.getAssets().list("canon");
    if (names == null) throw new IllegalStateException("Cannot list canon assets");
    Arrays.sort(names);
    for (String name : names) if (name.toLowerCase(Locale.ROOT).endsWith(".md")) {
      legacyFiles.put(name, readAsset(context, "canon/" + name));
    }

    String registryRaw;
    try {
      registryRaw = readAsset(context, "canon/canon-registry.json");
    } catch (IOException missingRegistry) {
      return new CanonRetriever(legacyFiles);
    }

    JSONObject registry = new JSONObject(registryRaw);
    JSONObject validation = CanonRegistry.validate(registry);
    if (!validation.optBoolean("valid", false)) {
      throw new IllegalStateException(
          "Invalid canon registry: " + validation.optJSONArray("errors"));
    }

    Map<String, String> structuredFiles = new LinkedHashMap<>();
    JSONArray sources = registry.getJSONArray("sources");
    for (int i = 0; i < sources.length(); i++) {
      JSONObject source = sources.getJSONObject(i);
      String contentPath = source.getString("contentPath");
      try {
        structuredFiles.put(contentPath, readAsset(context, "content/" + contentPath));
      } catch (IOException missingStructuredCopy) {
        // 6C compatibility: unresolved sources continue to use their legacy canon path.
      }
    }
    return new CanonRetriever(
        resolveRegistrySources(registry, legacyFiles, structuredFiles), registry);
  }

  static Map<String, String> resolveRegistrySources(
      JSONObject registry, Map<String, String> legacyFiles, Map<String, String> structuredFiles) {
    JSONObject validation = CanonRegistry.validate(registry);
    if (!validation.optBoolean("valid", false)) {
      throw new IllegalArgumentException(
          "Invalid canon registry: " + validation.optJSONArray("errors"));
    }

    Map<String, String> resolved = new LinkedHashMap<>();
    Set<String> registeredLegacyPaths = new LinkedHashSet<>();
    JSONArray sources = registry.optJSONArray("sources");
    for (int i = 0; sources != null && i < sources.length(); i++) {
      JSONObject source = sources.optJSONObject(i);
      String legacyPath = source == null ? "" : source.optString("path", "");
      String contentPath = source == null ? "" : source.optString("contentPath", "");
      registeredLegacyPaths.add(legacyPath);

      String text = structuredFiles == null ? null : structuredFiles.get(contentPath);
      if (text == null && legacyFiles != null) text = legacyFiles.get(legacyPath);
      if (text == null) {
        throw new IllegalStateException(
            "Registered canon source is missing: " + source.optString("id", legacyPath));
      }
      resolved.put(legacyPath, text);
    }

    if (legacyFiles != null) {
      List<String> extras = new ArrayList<>(legacyFiles.keySet());
      Collections.sort(extras);
      for (String legacyPath : extras) {
        if (!registeredLegacyPaths.contains(legacyPath)) {
          resolved.put(legacyPath, legacyFiles.get(legacyPath));
        }
      }
    }
    return resolved;
  }

  private static String readAsset(Context context, String path) throws IOException {
    try (InputStream in = context.getAssets().open(path)) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int count;
      while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
      return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }
  }

  public CanonRetriever(Map<String, String> markdownFiles) {
    this(markdownFiles, null);
  }

  CanonRetriever(Map<String, String> markdownFiles, JSONObject registry) {
    Map<String, JSONObject> metadata = new LinkedHashMap<>();
    if (registry != null) {
      JSONObject validation = CanonRegistry.validate(registry);
      if (!validation.optBoolean("valid", false)) {
        throw new IllegalArgumentException(
            "Invalid canon registry: " + validation.optJSONArray("errors"));
      }
      JSONArray sources = registry.optJSONArray("sources");
      for (int i = 0; sources != null && i < sources.length(); i++) {
        JSONObject source = sources.optJSONObject(i);
        if (source != null) {
          metadata.put(source.optString("path", ""), new JSONObject(source.toString()));
        }
      }
    }
    registryBacked = registry != null;
    registryByPath = Collections.unmodifiableMap(metadata);

    List<Section> result = new ArrayList<>();
    Map<String, Section> lookup = new LinkedHashMap<>();
    List<String> names = new ArrayList<>(markdownFiles.keySet());
    Collections.sort(names);
    for (String name : names) {
      if (!name.matches("[^/\\\\]+(?i:\\.md)") || markdownFiles.get(name) == null)
        throw new IllegalArgumentException("Invalid canon Markdown asset: " + name);
      parse(name, markdownFiles.get(name), result, lookup);
    }
    sections = Collections.unmodifiableList(result);
    byId = Collections.unmodifiableMap(lookup);
  }

  private static void parse(String file, String text, List<Section> out, Map<String, Section> ids) {
    String[] lines = text.split("(?<=\\n)", -1);
    List<String> path = new ArrayList<>();
    StringBuilder block = new StringBuilder();
    String fence = "";
    for (String line : lines) {
      String trimmed = line.trim();
      if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
        String marker = trimmed.substring(0, 3);
        if (fence.isEmpty()) fence = marker;
        else if (fence.equals(marker)) fence = "";
      }
      Matcher heading = fence.isEmpty() ? HEADING.matcher(line) : null;
      if (heading != null && heading.matches()) {
        add(file, path, block.toString(), out, ids);
        block.setLength(0);
        int depth = heading.group(1).length();
        while (path.size() >= depth) path.remove(path.size() - 1);
        while (path.size() < depth - 1) path.add("");
        path.add(heading.group(2).trim());
      }
      block.append(line);
    }
    add(file, path, block.toString(), out, ids);
  }

  private static void add(String file, List<String> path, String raw, List<Section> out,
      Map<String, Section> ids) {
    if (raw.trim().isEmpty()) return;
    int ordinal = 1;
    Section section;
    do { section = new Section(file, path, raw, ordinal++); } while (ids.containsKey(section.sectionId));
    out.add(section);
    ids.put(section.sectionId, section);
  }

  public List<Section> sections() { return sections; }

  public CanonPacket retrieve(JSONObject state, String action, int budget, boolean debug) {
    return retrieve(state, action, budget, debug, "");
  }

  public CanonPacket retrieve(JSONObject state, String action, int budget, boolean debug,
      String levelDisplayName) {
    JSONObject safe = state == null ? new JSONObject() : state;
    Set<String> subjects = new LinkedHashSet<>();
    String level = safe.optString("currentLevelKey", String.valueOf(safe.optInt("currentLevel", 0)));
    subjects.add("level:" + level);
    subjects.add("character:cao_minh");
    JSONArray party = safe.optJSONArray("party");
    if (party != null) for (int i = 0; i < party.length(); i++) {
      JSONObject member = party.optJSONObject(i);
      if (member != null && member.optBoolean("present", false)) subjects.add("character:" + member.optString("id"));
    }
    JSONObject flags = safe.optJSONObject("flags");
    String entity = flags == null ? "" : flags.optString("entityEncounterKey", "");
    // EntityCore owns this committed flag; absent means no active encounter.
    if (!entity.trim().isEmpty()) subjects.add("entity:" + entity);
    List<Selected> mandatory = new ArrayList<>(), dependencies = new ArrayList<>(), supplemental = new ArrayList<>();
    List<String> missing = new ArrayList<>(), missingRefs = new ArrayList<>(), trace = new ArrayList<>();
    Set<String> used = new LinkedHashSet<>();
    int size = 0;
    boolean exceeded = false;
    for (String subject : subjects) {
      Section core = coreFor(subject, levelDisplayName);
      if (core == null) { missing.add(subject); continue; }
      if (used.add(core.sectionId)) {
        mandatory.add(new Selected(core, "authoritative:" + subject));
        size += core.size();
      }
    }
    for (int i = 0; i < mandatory.size() + dependencies.size(); i++) {
      Section s = i < mandatory.size() ? mandatory.get(i).section : dependencies.get(i - mandatory.size()).section;
      for (String ref : s.requires) {
        Section target = byId.get(ref);
        if (target == null) {
          String failure = s.sectionId + " -> " + ref;
          missingRefs.add(failure);
          missing.add(failure);
          continue;
        }
        if (used.add(target.sectionId)) {
          dependencies.add(new Selected(target, "requires:" + s.sectionId));
          size += target.size();
        }
      }
    }
    if (size > budget) exceeded = true;
    if (!exceeded) {
      String query = normalize(action == null ? "" : action);
      List<Section> ranked = new ArrayList<>(sections);
      ranked.sort(Comparator.<Section>comparingInt(s -> score(s, query)).reversed()
          .thenComparing(s -> s.sectionId));
      for (Section s : ranked) {
        if (supplemental.size() >= 3) break;
        int score = score(s, query);
        if (conflictsWithLevel(s, level, levelDisplayName)) continue;
        if (score < 12 || used.contains(s.sectionId)) continue;
        List<Section> closure = new ArrayList<>();
        Set<String> local = new LinkedHashSet<>();
        List<String> candidateMissing = new ArrayList<>();
        collect(s, closure, local, used, candidateMissing);
        if (!candidateMissing.isEmpty()) {
          missingRefs.addAll(candidateMissing);
          if (debug) trace.add("missing-ref-skip:" + s.sectionId);
          continue;
        }
        int extra = 0;
        for (Section part : closure) extra += part.size();
        if (size + extra > budget) { if (debug) trace.add("budget-skip:" + s.sectionId); continue; }
        used.addAll(local);
        supplemental.add(new Selected(s, "search:" + score));
        for (Section part : closure) if (part != s)
          dependencies.add(new Selected(part, "requires:" + s.sectionId));
        size += extra;
      }
    }
    if (debug) for (Selected s : mandatory) trace.add(s.reason + ":" + s.section.sectionId);
    return new CanonPacket(mandatory, dependencies, supplemental, missing, missingRefs, trace, size, exceeded);
  }

  private void collect(Section s, List<Section> out, Set<String> local, Set<String> used,
      List<String> missing) {
    if (used.contains(s.sectionId) || !local.add(s.sectionId)) return;
    out.add(s);
    for (String ref : s.requires) {
      Section next = byId.get(ref);
      if (next == null) missing.add(s.sectionId + " -> " + ref);
      else collect(next, out, local, used, missing);
    }
  }

  private Section coreFor(String subject, String levelDisplayName) {
    JSONObject bound = mandatorySourceFor(subject);
    if (bound != null) {
      if (!"CURRENT".equals(bound.optString("status", ""))) return null;
      Section registryCore = coreForSource(
          bound.optString("path", ""), subject, levelDisplayName);
      if (registryCore != null) return registryCore;
      return null;
    }

    String[] parts = subject.split(":", 2);
    String key = normalize(parts.length > 1 ? parts[1].replace('_', ' ') : "");
    List<Section> candidates = new ArrayList<>();
    for (Section s : sections) {
      String heading = normalize(s.heading);
      String file = s.fileTerms;
      String expectedLevelHeading = normalize(levelDisplayName);
      boolean belongs = "level".equals(parts[0])
          ? (file.contains("world") && (heading.startsWith("tang " + key + " ")
              || heading.startsWith("level " + key + " ")
              || (!expectedLevelHeading.isEmpty() && heading.equals(expectedLevelHeading))))
          : "entity".equals(parts[0]) ? heading.equals(key) || file.equals(key)
          : file.startsWith(key) && (file.contains("codex") || heading.equals(key));
      if (belongs && !("level".equals(parts[0])
          && conflictsWithLevel(s, parts.length > 1 ? parts[1] : "", levelDisplayName))) {
        candidates.add(s);
      }
    }
    return bestCore(candidates);
  }

  private JSONObject mandatorySourceFor(String subject) {
    if (!registryBacked || subject == null || subject.trim().isEmpty()) return null;
    for (JSONObject source : registryByPath.values()) {
      JSONArray mandatoryFor = source.optJSONArray("mandatoryFor");
      if (mandatoryFor == null) continue;
      for (int i = 0; i < mandatoryFor.length(); i++) {
        if (subject.equals(mandatoryFor.optString(i, ""))) return source;
      }
    }
    return null;
  }

  private Section coreForSource(String sourcePath, String subject, String levelDisplayName) {
    String[] parts = subject == null ? new String[0] : subject.split(":", 2);
    String type = parts.length == 0 ? "" : parts[0];
    String key = normalize(parts.length > 1 ? parts[1].replace('_', ' ') : "");
    String expectedLevelHeading = normalize(levelDisplayName);
    List<Section> candidates = new ArrayList<>();
    for (Section s : sections) {
      if (!sourcePath.equals(s.sourceFile)) continue;
      String heading = normalize(s.heading);
      boolean belongs = "level".equals(type)
          ? (heading.startsWith("tang " + key + " ")
              || heading.startsWith("level " + key + " ")
              || (!expectedLevelHeading.isEmpty() && heading.equals(expectedLevelHeading)))
          : "entity".equals(type) ? heading.equals(key) || s.fileTerms.equals(key)
          : true;
      if (belongs && !("level".equals(type)
          && conflictsWithLevel(s, parts.length > 1 ? parts[1] : "", levelDisplayName))) {
        candidates.add(s);
      }
    }
    return bestCore(candidates);
  }

  private static Section bestCore(List<Section> candidates) {
    if (candidates == null || candidates.isEmpty()) return null;
    candidates.sort(Comparator.<Section>comparingInt(s -> s.core ? 0
        : normalize(s.heading).matches(
            ".*(ho so nhanh|truy xuat nhanh|identity|dinh danh|tong quan).*") ? 1 : 2)
        .thenComparingInt(Section::size).thenComparing(s -> s.sectionId));
    return candidates.get(0);
  }

  private static boolean conflictsWithLevel(Section section, String key, String displayName) {
    if (displayName == null || displayName.isEmpty() || !section.fileTerms.contains("world")) return false;
    String heading = normalize(section.heading);
    String normalizedKey = normalize(key);
    if (!heading.startsWith("tang " + normalizedKey + " ")
        && !heading.startsWith("level " + normalizedKey + " ")) return false;
    String expected = normalize(displayName).replaceFirst("^(level|tang) " + normalizedKey + " ", "");
    return !expected.isEmpty() && !heading.contains(expected);
  }

  private static int score(Section s, String query) {
    if (query.isEmpty()) return 0;
    if (query.contains(normalize(s.sectionId.replace("::", " ").replace('/', ' ')))) return 100;
    for (String alias : s.aliases) if (phrase(query, normalize(alias))) return 90;
    String heading = normalize(s.heading);
    if (heading.length() >= 4 && phrase(query, heading)) return 75;
    if (s.fileTerms.length() >= 5 && phrase(query, s.fileTerms)) return 55;
    int score = 0;
    for (String term : query.split(" ")) if (term.length() >= 4 && s.searchableText.contains(term)) score += 2;
    return Math.min(score, 20);
  }
  private static boolean phrase(String haystack, String needle) {
    return !needle.isEmpty() && (" " + haystack + " ").contains(" " + needle + " ");
  }
  private static String normalize(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
        .replace('đ', 'd').replace('Đ', 'd').toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{Alnum}]+", " ").trim().replaceAll(" +", " ");
  }
  private static String slug(String text) { return normalize(text).replace(' ', '_'); }
}
