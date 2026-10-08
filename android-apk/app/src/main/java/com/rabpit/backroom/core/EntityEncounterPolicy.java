package com.rabpit.backroom.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One non-overlapping d10000 draw per eligible gameplay action.
 *
 * The normal pools are a gameplay-specific level assignment. They override the old
 * all-level roaming selection, not the historical world/visual reference documents.
 * Rare Entities deliberately remain eligible on every main Level.
 */
public final class EntityEncounterPolicy {
  public static final int DIE = 10000;
  public static final int NORMAL_CHANCE = 800; // 8% for EACH Entity on its assigned Level.

  private static final String[] RARE_KEYS = {
      "diep_minh", "luc_tram_hac_hoa",
      "the_lifeform_bacteria_01", "the_lifeform_bacteria_02",
      "the_lifeform_bacteria_03", "research_async_member_knife_01",
      "slenderman", "jane_the_killer", "jeff_the_killer"
  };

  private static final String[] RARE_ROLL_LABELS = {
      "diepMinhEncounter", "lucTramEntityEncounter",
      "bacterialStalkerEncounter", "bacterialStriderEncounter",
      "bacterialWeaverEncounter", "researchAsyncMemberEncounter",
      "rareSlendermanEncounter", "rareJaneTheKillerEncounter", "rareJeffTheKillerEncounter"
  };

  // 0/6 have no assigned normal Entity; their rare windows still work normally.
  // Level 1: Habitable Zone; 2: Utility Halls; 3: Electrical Station;
  // Level 4: Abandoned Office; 5: Terror Hotel.
  private static final String[][] NORMAL_BY_LEVEL = {
      {},
      {"hound", "duller", "hostile_faceling", "false_puddle", "skin-stealer"},
      {"clump", "smiler", "biological_pipeline"},
      {"wretch", "cable_mimic"},
      {"predatory_window"},
      {"deathmoth", "paintings", "the_beast_of_level_5", "hotel_corpse_lure"},
      {}
  };

  public static final class Window {
    public final String entityKey;
    public final String rollLabel;
    public final int start;
    public final int end;
    public final boolean rare;

    private Window(String entityKey, String rollLabel, int start, int end, boolean rare) {
      this.entityKey = entityKey;
      this.rollLabel = rollLabel;
      this.start = start;
      this.end = end;
      this.rare = rare;
    }

    public int chanceBasisPoints() {
      return end - start + 1;
    }

    public boolean matches(int roll) {
      return start <= roll && roll <= end;
    }
  }

  private static final List<List<Window>> WINDOWS_BY_LEVEL = createWindows();

  private EntityEncounterPolicy() {}

  private static List<List<Window>> createWindows() {
    List<List<Window>> levels = new ArrayList<>();
    for (int level = 0; level < NORMAL_BY_LEVEL.length; level++) {
      List<Window> windows = new ArrayList<>();
      Set<String> keys = new HashSet<>();
      int cursor = 0;
      for (int i = 0; i < RARE_KEYS.length; i++) {
        int width = i == 0 ? 300 : 400; // Diệp Minh 3%; other eight 4% EACH.
        if (!keys.add(RARE_KEYS[i])) throw new IllegalStateException("Duplicate rare Entity");
        windows.add(new Window(RARE_KEYS[i], RARE_ROLL_LABELS[i],
            cursor + 1, cursor + width, true));
        cursor += width;
      }
      for (String key : NORMAL_BY_LEVEL[level]) {
        if (!keys.add(key)) throw new IllegalStateException("Duplicate Entity at Level " + level);
        windows.add(new Window(key, "levelEntityEncounter", cursor + 1,
            cursor + NORMAL_CHANCE, false));
        cursor += NORMAL_CHANCE;
      }
      if (cursor > DIE) {
        throw new IllegalStateException("Spawn probabilities exceed 100% at Level " + level);
      }
      levels.add(Collections.unmodifiableList(windows));
    }
    return Collections.unmodifiableList(levels);
  }

  public static List<Window> windowsForLevel(int level) {
    if (level < 0 || level >= WINDOWS_BY_LEVEL.size()) {
      throw new IllegalArgumentException("Unsupported spawn Level: " + level);
    }
    return WINDOWS_BY_LEVEL.get(level);
  }

  public static List<Window> rareWindows() {
    return WINDOWS_BY_LEVEL.get(0);
  }

  public static int totalChanceBasisPoints(int level) {
    List<Window> windows = windowsForLevel(level);
    return windows.get(windows.size() - 1).end;
  }

  public static Window pick(int level, int roll) {
    if (roll < 1 || roll > DIE) {
      throw new IllegalArgumentException("d10000 result outside 1..10000");
    }
    for (Window window : windowsForLevel(level)) {
      if (window.matches(roll)) return window;
    }
    return null;
  }
}
