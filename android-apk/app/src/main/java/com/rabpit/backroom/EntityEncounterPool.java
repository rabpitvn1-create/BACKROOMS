package com.rabpit.backroom;

/** One d10000 roll determines both appearance (45%) and which Entity appears.
 * All weights are absolute basis points per eligible gameplay turn, on every Level.
 */
final class EntityEncounterPool {
  static final int ENCOUNTER_THRESHOLD = 4500;
  // 14 common Entities at 2.5% each: 35% in total.
  private static final String[] COMMON = {
    "hound", "clump", "duller", "deathmoth", "hostile_faceling",
    "false_puddle", "paintings", "smiler", "skin-stealer",
    "predatory_window", "biological_pipeline", "wretch",
    "cable_mimic", "hotel_corpse_lure"
  };
  // 3 Lifeforms at 2% each: 6% in total.
  private static final String[] LIFEFORM = {
    "blackroot_sentinel", "sinew_strider", "hollow_grasper"
  };
  // Special Entities at 1% each: 3% in total.
  private static final String[] RARE = {
    "the_beast_of_level_5", "slenderman", "diep_minh"
  };
  // Unique roaming killers at 0.5% each: 1% in total.
  private static final String[] KILLERS = {
    "jeff_the_killer", "jane_the_killer"
  };

  private EntityEncounterPool() {}

  static {
    if (COMMON.length * 250 + LIFEFORM.length * 200
        + RARE.length * 100 + KILLERS.length * 50 != ENCOUNTER_THRESHOLD) {
      throw new ExceptionInInitializerError("Entity weights must total 45%");
    }
  }

  static String pickForSuccessfulRoll(int roll) {
    if (roll < 1 || roll > ENCOUNTER_THRESHOLD) {
      throw new IllegalArgumentException("Unsuccessful d10000 Entity roll");
    }
    int ticket = roll - 1;
    if (ticket < COMMON.length * 250) return COMMON[ticket / 250];
    ticket -= COMMON.length * 250;
    if (ticket < LIFEFORM.length * 200) return LIFEFORM[ticket / 200];
    ticket -= LIFEFORM.length * 200;
    if (ticket < RARE.length * 100) return RARE[ticket / 100];
    ticket -= RARE.length * 100;
    return KILLERS[ticket / 50];
  }
}
