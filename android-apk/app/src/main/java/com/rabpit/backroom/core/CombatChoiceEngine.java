package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Authoritative 5D6 Poker Dice combat state machine.
 *
 * The WebView only displays values already produced here. Dice values, holds, reroll count,
 * finalized hand and PRNG sequence are serializable combat state, so reload cannot grant a free
 * reroll. One finalized hand resolves exactly one Character action, then the living Entities respond in encounter order.
 */
public final class CombatChoiceEngine {
  static final int MAX_COMBAT_PARTICIPANTS = 4;
  static final int MAX_REROLLS = 3;
  static final int DICE_COUNT = 5;

  private static final int ULTIMATE_BONUS_DAMAGE_PERCENT = 15;
  private static final int HUYET_MA_24_HIT_COUNT = 24;
  private static final int LUCIA_TOO_YOUNG_TO_DIE_SHOT_COUNT = 60;
  private static final int LUC_TRAM_THIEN_KIEM_HIT_COUNT = 60;
  private static final int CAO_MINH_BASE_ATTACK = 30;
  private static final int CRITICAL_DAMAGE_PERCENT = 150;
  private static final int ENTITY_BASE_CRITICAL_PERCENT = 5;
  private static final int ENTITY_BASE_EVASION_PERCENT = 5;
  private static final String DAI_DAO_MA_TON = "Đại Đạo Ma Tôn";
  private static final int DAI_DAO_MA_TON_HEAL_PERCENT = 10;
  private static final int DAI_DAO_MA_TON_ATTACK_PER_TURN_PERCENT = 20;
  private static final int DAI_DAO_MA_TON_CRITICAL_PER_TURN_PERCENT = 20;
  private static final int DAI_DAO_MA_TON_ALLY_CRITICAL_BONUS_PERCENT = 50;
  private static final int MAX_CRITICAL_CHANCE_PERCENT = 100;

  private static final class EntityProfile {
    final String key;
    final String name;
    final int maxHp;
    final int damage;

    EntityProfile(String key, String name, int maxHp, int damage) {
      this.key = key;
      this.name = name;
      this.maxHp = maxHp;
      this.damage = damage;
    }
  }

  private static final class TreasureRewardPolicy {
    final int firstKillBaseCore;
    final int repeatBaseCore;
    final boolean scaleWithStage;

    TreasureRewardPolicy(int firstKillBaseCore, int repeatBaseCore, boolean scaleWithStage) {
      this.firstKillBaseCore = Math.max(0, firstKillBaseCore);
      this.repeatBaseCore = Math.max(0, repeatBaseCore);
      this.scaleWithStage = scaleWithStage;
    }
  }

  private static final class Skill {
    final String name;
    final String description;
    final int damagePercent;
    final String effect;
    final int effectTurns;
    final int effectValue;

    Skill(String name, String description, int damagePercent, String effect, int effectTurns,
          int effectValue) {
      this.name = name;
      this.description = description;
      this.damagePercent = damagePercent;
      this.effect = effect;
      this.effectTurns = effectTurns;
      this.effectValue = effectValue;
    }
  }

  private static final class EntitySkill {
    final String name;
    final int damagePercent;
    final int procPercent;
    final int hitCount;
    final int defensePiercePercent;
    final int healPercentOfDamage;

    EntitySkill(String name, int damagePercent, int procPercent) {
      this(name, damagePercent, procPercent, 1, 0, 0);
    }

    EntitySkill(String name, int damagePercent, int procPercent, int hitCount,
                int defensePiercePercent, int healPercentOfDamage) {
      this.name = name;
      this.damagePercent = damagePercent;
      this.procPercent = procPercent;
      this.hitCount = Math.max(1, hitCount);
      this.defensePiercePercent = Math.max(0, Math.min(100, defensePiercePercent));
      this.healPercentOfDamage = Math.max(0, Math.min(100, healPercentOfDamage));
    }
  }

  private static final class CharacterProc {
    final String name;
    final int procPercent;
    final int bonusDamagePercent;
    final String effect;
    final int effectTurns;
    final int effectValue;

    CharacterProc(String name, int procPercent, int bonusDamagePercent, String effect,
                  int effectTurns, int effectValue) {
      this.name = name;
      this.procPercent = procPercent;
      this.bonusDamagePercent = bonusDamagePercent;
      this.effect = effect;
      this.effectTurns = effectTurns;
      this.effectValue = effectValue;
    }
  }

  private static final class Ultimate {
    final String name;
    final int hitCount;
    final int bonusPercent;

    Ultimate(String name, int hitCount, int bonusPercent) {
      this.name = name;
      this.hitCount = hitCount;
      this.bonusPercent = bonusPercent;
    }
  }

  private static final class ActionResult {
    String summary;
    boolean evadeResponse;
  }

  private static final Map<String, EntityProfile> ENTITIES = new LinkedHashMap<>();
  private static final Map<String, TreasureRewardPolicy> TREASURE_REWARDS = new LinkedHashMap<>();
  private static final Map<String, List<Skill>> SKILLS = new LinkedHashMap<>();
  private static final Map<String, List<EntitySkill>> ENTITY_SKILLS = new LinkedHashMap<>();
  private static final Map<String, List<CharacterProc>> CHARACTER_PROCS = new LinkedHashMap<>();
  private static final Map<String, Ultimate> ULTIMATES = new LinkedHashMap<>();

  static {
    entity("hound", "Hound", 150, 15);
    entity("the_lifeform_bacteria_01", "Bacterial Stalker", 173, 17);
    entity("the_lifeform_bacteria_02", "Bacterial Strider", 173, 17);
    entity("the_lifeform_bacteria_03", "Bacterial Weaver", 173, 17);
    entity("clump", "Clump", 190, 17);
    entity("duller", "Duller", 160, 14);
    entity("deathmoth", "Deathmoth", 120, 13);
    entity("hostile_faceling", "Hostile Faceling", 130, 14);
    entity("false_puddle", "False Puddle", 170, 16);
    entity("paintings", "Paintings", 120, 12);
    entity("smiler", "Smiler", 150, 18);
    entity("skin-stealer", "Skin-Stealer", 180, 18);
    entity("predatory_window", "Predatory Window", 210, 17);
    entity("biological_pipeline", "Biological Pipeline", 220, 18);
    entity("wretch", "Wretch", 145, 16);
    entity("cable_mimic", "Cable Mimic", 180, 17);
    entity("the_beast_of_level_5", "The Beast of Level 5", 300, 22);
    entity("hotel_corpse_lure", "Hotel Corpse Lure", 190, 18);
    entity("jeff_the_killer", "Jeff", 240, 20);
    entity("async_rifleman", "ASYNC Rifleman", 180, 20);
    entity("async_member_rifle_aim_right_01", "Research Async Member", 190, 20);
    entity("copx", "CopX", 260, 22);
    treasureEntity("tam_ma_cao_minh", "Tâm Ma Cao Minh", 300, 30, 100, 10, true);
    entity("jane_the_killer", "Jane", 270, 20);
    entity("slenderman", "Slenderman", 360, 23);
    entity("diep_minh", "Diệp Minh", 1200, 42);

    entitySkills("hound",
        entitySkill("Dead Bite", 120, 35),
        entitySkill("Rending Pounce", 115, 32),
        entitySkill("Pack Maul", 110, 34));
    entitySkills("the_lifeform_bacteria_01",
        entitySkill("Filament Lash", 110, 34),
        entitySkill("Hollow Rush", 115, 30),
        entitySkill("Tendril Rake", 120, 24),
        multiHitEntitySkill("Rib-Cage Clamp", 95, 20, 2));
    entitySkills("the_lifeform_bacteria_02",
        entitySkill("Filament Lash", 110, 34),
        entitySkill("Hollow Rush", 115, 30),
        entitySkill("Tendril Rake", 120, 24),
        piercingEntitySkill("Longstep Skewer", 125, 20, 60));
    entitySkills("the_lifeform_bacteria_03",
        entitySkill("Filament Lash", 110, 34),
        entitySkill("Hollow Rush", 115, 30),
        entitySkill("Tendril Rake", 120, 24),
        drainingEntitySkill("Black-Mesh Feeding", 120, 20, 50));
    entitySkills("clump",
        entitySkill("Grasping Crush", 120, 34),
        entitySkill("Limb Barrage", 115, 35),
        entitySkill("Drag Down", 110, 31));
    entitySkills("duller",
        entitySkill("Blindside Strike", 125, 33),
        entitySkill("Distorted Lunge", 115, 35),
        entitySkill("Column Ambush", 120, 30));
    entitySkills("deathmoth",
        entitySkill("Ceiling Dive", 110, 31),
        entitySkill("Wing Strike", 115, 32),
        entitySkill("Dark Recess Ambush", 120, 23));
    entitySkills("hostile_faceling",
        entitySkill("Sudden Grasp", 110, 35),
        entitySkill("Close-Quarters Strike", 115, 26),
        entitySkill("Predatory Lunge", 120, 27));
    entitySkills("false_puddle",
        entitySkill("Toothed Snap", 110, 31),
        entitySkill("Footstep Ambush", 115, 27),
        entitySkill("Puddle Bite", 120, 25));
    entitySkills("paintings",
        entitySkill("Canvas Reach", 110, 33),
        entitySkill("Frame Ambush", 115, 27),
        entitySkill("Pulling Grasp", 120, 21));
    entitySkills("smiler",
        entitySkill("Darkness Lunge", 110, 32),
        entitySkill("Blindside Strike", 115, 26),
        entitySkill("Shadow Bite", 120, 23));
    entitySkills("skin-stealer",
        entitySkill("Disguised Strike", 110, 32),
        entitySkill("Close-Range Slash", 115, 29),
        entitySkill("Ambush Lunge", 120, 21));
    entitySkills("predatory_window",
        entitySkill("Window Reach", 110, 34),
        entitySkill("Threshold Grasp", 115, 28),
        entitySkill("Close-Range Pull", 120, 25));
    entitySkills("biological_pipeline",
        entitySkill("Constriction", 110, 33),
        entitySkill("Pipeline Crush", 115, 30),
        entitySkill("Corrosive Contact", 120, 20));
    entitySkills("wretch",
        entitySkill("Ragged Swipe", 110, 32),
        entitySkill("Pain-Fueled Lunge", 115, 27),
        entitySkill("Scent-Tracked Strike", 120, 22));
    entitySkills("cable_mimic",
        entitySkill("Cable Lash", 110, 35),
        entitySkill("Bundle Constriction", 115, 29),
        entitySkill("Current-Driven Grip", 120, 28));
    entitySkills("the_beast_of_level_5",
        entitySkill("Cornered Strike", 110, 33),
        entitySkill("Isolated Prey Ambush", 115, 32),
        entitySkill("Hotel Corridor Lunge", 120, 21));
    entitySkills("hotel_corpse_lure",
        entitySkill("Corpse-Lure Grasp", 110, 35),
        entitySkill("Close-Range Ambush", 115, 28),
        entitySkill("Sudden Strike", 120, 20));
    entitySkills("jeff_the_killer",
        entitySkill("Stalking Slash", 110, 34),
        entitySkill("Close-Range Lunge", 115, 29),
        entitySkill("Tactical Ambush", 120, 23));
    entitySkills("async_rifleman",
        entitySkill("Controlled Burst", 110, 32),
        entitySkill("Cover Fire", 115, 32),
        entitySkill("Crossfire Burst", 120, 28));
    entitySkills("async_member_rifle_aim_right_01",
        entitySkill("Loạt Bắn Kiểm Soát", 110, 33),
        entitySkill("Hai Phát Liên Tiếp", 115, 28),
        entitySkill("Áp Chế Mẫu Vật", 120, 23));
    entitySkills("copx",
        entitySkill("Static Burst", 110, 30),
        entitySkill("Servo Pivot", 115, 25),
        entitySkill("Last Directive", 120, 20));
    entitySkills("tam_ma_cao_minh",
        entitySkill("Tâm Ma Trảm", 120, 35),
        entitySkill("Huyết Ảnh Phản Kích", 115, 40),
        entitySkill("Ma Hổ Phệ", 110, 45));
    entitySkills("jane_the_killer",
        entitySkill("Stalking Strike", 110, 33),
        entitySkill("Close-Range Slash", 115, 27),
        entitySkill("Sudden Lunge", 120, 25));
    entitySkills("slenderman",
        entitySkill("Tendril Strike", 110, 31),
        entitySkill("Tendril Sweep", 115, 26),
        entitySkill("Looming Grasp", 120, 21));
    entitySkills("diep_minh",
        entitySkill("Golden Sword Slash", 110, 32),
        entitySkill("Demonic Claw", 115, 32),
        entitySkill("Sword-Claw Assault", 120, 21));

    // Only offensive canonical skills participate in Poker Dice Skill hands. Passive/evasion
    // skills do not silently replace or modify dice outcomes.
    skills("cao_minh",
        skill("Huyết Ma Tứ Liên",
            "Bốn trảm liên hoàn; tổng 170% weapon damage và Chảy máu.", 170, "Chảy máu", 3, 5),
        skill("Ma Tâm Trấn Hồn",
            "Ma uy trấn áp thần hồn; tổng 130% weapon damage và Choáng.", 130, "Choáng", 1, 0),
        skill("Huyết Ảnh Ma Độn",
            "Dịch chuyển theo Huyết Ma Kiếm rồi chém đúng hai kiếm; tổng 147% weapon damage.",
            147, "", 0, 0));


    skills("syvial",
        skill("Rift Sever", "", 175, "", 0, 0),
        skill("Crimson Guillotine", "", 190, "Chảy máu", 3, 4),
        skill("Lucifer Breaker", "", 155, "Choáng", 1, 0),
        skill("Spatial Dominion", "", 210, "Mất phương hướng", 2, 25));

    skills("lucia",
        skill("M4A1 Joint Attack", "Phối hợp hỏa lực M4A1.", 150, "", 0, 0));

    skills("luc_tram",
        skill("Tịch Quang Hợp Kích",
            "Kiếm thế hợp kích bằng Tịch Quang; giữ nguyên projection 150% damage.", 150, "", 0, 0));

    // Independent proc skills may trigger after a basic attack or normal Skill, never an Ultimate.
    // Kai is Legacy and deliberately has no entry in this authoritative pool.
    characterProcs("cao_minh",
        characterProc("Huyết Sát Kiếm Ấn", 50, 25, "Chảy máu", 2, 3),
        characterProc("Phá Giáp Ma Kiếm", 48, 20, "Xuyên giáp", 2, 10),
        characterProc("Ma Tâm Chấn", 45, 15, "Choáng", 1, 0),
        characterProc("Huyết Độc Ma Khí", 47, 20, "Trúng độc", 2, 3),
        characterProc("Huyết Liệt Ma Ấn", 51, 20, "Chảy máu", 2, 4));
    characterProcs("lucia",
        characterProc("Toxic Burst", 52, 25, "Trúng độc", 2, 3),
        characterProc("Armor-Piercing Burst", 55, 20, "Xuyên giáp", 2, 10),
        characterProc("Concussive Burst", 46, 15, "Choáng", 1, 0),
        characterProc("Rending Burst", 49, 20, "Chảy máu", 2, 3),
        characterProc("Corrosive Burst", 51, 20, "Trúng độc", 2, 4));
    characterProcs("luc_tram",
        characterProc("Tịch Quang Phản Kiếm", 52, 25, "Trúng độc", 2, 3),
        characterProc("Nhất Tuyến Phá Vọng", 55, 20, "Xuyên giáp", 2, 10),
        characterProc("Thiên Kiếm Chấn", 46, 15, "Choáng", 1, 0),
        characterProc("Bạch Hồng Quán Nhật", 49, 20, "Chảy máu", 2, 3),
        characterProc("Vạn Kiếm Quy Tâm", 51, 20, "Trúng độc", 2, 4));

    // Ultimate damage derives from each character's current basic DMG instead of a fixed HP value.
    // SSF uses the listed total; FSF keeps the Poker Dice 200% Ultimate multiplier.
    ULTIMATES.put("cao_minh",
        new Ultimate("Huyết Ma Nhị Thập Tứ Trảm", HUYET_MA_24_HIT_COUNT,
            ULTIMATE_BONUS_DAMAGE_PERCENT));
    ULTIMATES.put("lucia",
        new Ultimate("Too Young To Die", LUCIA_TOO_YOUNG_TO_DIE_SHOT_COUNT,
            ULTIMATE_BONUS_DAMAGE_PERCENT));
    ULTIMATES.put("luc_tram",
        new Ultimate("Thiên Kiếm Định Giới", LUC_TRAM_THIEN_KIEM_HIT_COUNT,
            ULTIMATE_BONUS_DAMAGE_PERCENT));
  }

  private CombatChoiceEngine() {}

  private static void entity(String key, String name, int hp, int damage) {
    ENTITIES.put(key, new EntityProfile(key, name, hp, damage));
  }

  private static void treasureEntity(String key, String name, int hp, int damage,
                                     int firstKillBaseCore, int repeatBaseCore,
                                     boolean scaleRewardWithStage) {
    entity(key, name, hp, damage);
    TREASURE_REWARDS.put(key,
        new TreasureRewardPolicy(firstKillBaseCore, repeatBaseCore, scaleRewardWithStage));
  }

  private static Skill skill(String name, String description, int damage, String effect, int turns,
                             int value) {
    return new Skill(name, description, damage, effect, turns, value);
  }

  private static void skills(String id, Skill... definitions) {
    List<Skill> list = new ArrayList<>();
    for (Skill definition : definitions) list.add(definition);
    SKILLS.put(id, list);
  }

  private static EntitySkill entitySkill(String name, int damagePercent, int procPercent) {
    return new EntitySkill(name, damagePercent, procPercent);
  }

  private static EntitySkill multiHitEntitySkill(
      String name, int damagePercent, int procPercent, int hitCount) {
    return new EntitySkill(name, damagePercent, procPercent, hitCount, 0, 0);
  }

  private static EntitySkill piercingEntitySkill(
      String name, int damagePercent, int procPercent, int defensePiercePercent) {
    return new EntitySkill(name, damagePercent, procPercent, 1, defensePiercePercent, 0);
  }

  private static EntitySkill drainingEntitySkill(
      String name, int damagePercent, int procPercent, int healPercentOfDamage) {
    return new EntitySkill(name, damagePercent, procPercent, 1, 0, healPercentOfDamage);
  }

private static void entitySkills(String id,EntitySkill... definitions) {
  List<EntitySkill> list=new ArrayList<>(); for(EntitySkill d:definitions) list.add(d); ENTITY_SKILLS.put(id,list);
}
static int entitySkillCount(String key) {
  List<EntitySkill> pool=ENTITY_SKILLS.get(key==null?"":key.trim().toLowerCase(Locale.ROOT)); return pool==null?0:pool.size();
}
static int entitySkillDamage(int rawDamage,int percent) {
  return Math.max(1,(int)(((long)Math.max(1,rawDamage)*Math.max(0,percent)+50L)/100L));
}
static int entitySkillProcRoll(int seed,int round,int actorIndex,int skillIndex) {
  long x=(seed&0xffffffffL)*1664525L+(long)Math.max(1,round)*1013904223L+(long)(actorIndex+1)*2654435761L+(long)(skillIndex+1)*97531L;
  x^=x>>>16; x^=x<<11; return (int)Math.floorMod(x,100L);
}

  private static CharacterProc characterProc(String name, int procPercent, int bonusDamagePercent,
                                             String effect, int turns, int value) {
    return new CharacterProc(name, procPercent, bonusDamagePercent, effect, turns, value);
  }

  private static void characterProcs(String id, CharacterProc... definitions) {
    List<CharacterProc> list = new ArrayList<>();
    for (CharacterProc definition : definitions) list.add(definition);
    CHARACTER_PROCS.put(id, list);
  }

  static int characterProcCount(String id) {
    List<CharacterProc> pool = CHARACTER_PROCS.get(CharacterProgressionCore.normalizeCharacterId(id));
    return pool == null ? 0 : pool.size();
  }

  static int characterProcPercent(String id, int index) {
    List<CharacterProc> pool = CHARACTER_PROCS.get(CharacterProgressionCore.normalizeCharacterId(id));
    if (pool == null || index < 0 || index >= pool.size()) return -1;
    return pool.get(index).procPercent;
  }

  static int characterProcRoll(int seed, int sequence, String actorId, String procName) {
    long mixed = (seed & 0xffffffffL) * 31L + (long)(sequence + 1) * 131L
        + (long)(actorId + ":" + procName).hashCode() * 17L;
    return (int)Math.floorMod(mixed, 100L);
  }

  static Map<String, String> semanticCatalog() {
    Map<String, String> output = new LinkedHashMap<>();
    for (EntityProfile profile : ENTITIES.values()) output.put(profile.name, "entity");
    for (List<Skill> pool : SKILLS.values()) {
      for (Skill skill : pool) {
        output.put(skill.name, "skill");
        if (!skill.effect.trim().isEmpty()) output.put(skill.effect, "effect");
      }
    }
    for (List<CharacterProc> pool : CHARACTER_PROCS.values()) {
      for (CharacterProc proc : pool) {
        output.put(proc.name, "skill");
        if (!proc.effect.trim().isEmpty()) output.put(proc.effect, "effect");
      }
    }
    for (List<EntitySkill> pool : ENTITY_SKILLS.values()) {
      for (EntitySkill skill : pool) output.put(skill.name, "skill");
    }
    for (String token : new String[]{"[NO HAND]","[PAIR]","[TWO PAIR]","[TRIPLE]",
        "[STRAIGHT]","[FULL HOUSE]","[F.O.A.K]","[SSF]","[FSF]"}) {
      output.put(token, "stat");
    }
    for (Ultimate ultimate : ULTIMATES.values()) output.put(ultimate.name, "skill");
    // Canonical non-offensive skill retained for GM semantic recognition only. It is deliberately
    // excluded from Poker Dice Skill selection so it cannot alter evade/dice outcomes.
    output.put("Thiên Ma Bộ", "skill");
    output.put(DAI_DAO_MA_TON, "skill");
    return output;
  }

  public static boolean isKnownEntity(String key) {
    return key != null && ENTITIES.containsKey(key.trim().toLowerCase(Locale.ROOT));
  }

  public static boolean isActive(JSONObject state) {
    JSONObject combat = state == null ? null : state.optJSONObject("combat");
    return combat != null && combat.optBoolean("active", false);
  }

  static int maxCombatParticipants() {
    return MAX_COMBAT_PARTICIPANTS;
  }

  static boolean hasAuthoritativeUltimate(String characterId) {
    return ULTIMATES.containsKey(CharacterProgressionCore.normalizeCharacterId(characterId));
  }

  static String classify(int... dice) {
    if (dice == null || dice.length != DICE_COUNT) return "NO HAND";
    int[] values = dice.clone();
    for (int value : values) if (value < 1 || value > 6) return "NO HAND";

    boolean allSame = true;
    for (int i = 1; i < values.length; i++) allSame &= values[i] == values[0];
    if (allSame) return "FSF";

    if (matches(values, 1, 2, 3, 4, 5) || matches(values, 5, 4, 3, 2, 1)) return "SSF";
    if (matches(values, 2, 3, 4, 5, 6) || matches(values, 6, 5, 4, 3, 2)) return "STRAIGHT";

    int[] counts = new int[7];
    for (int value : values) counts[value]++;
    boolean four = false;
    boolean triple = false;
    int pairs = 0;
    for (int value = 1; value <= 6; value++) {
      if (counts[value] == 4) four = true;
      if (counts[value] == 3) triple = true;
      if (counts[value] == 2) pairs++;
    }
    if (four) return "FOUR OF A KIND";
    if (triple && pairs == 1) return "FULL HOUSE";
    if (triple) return "THREE OF A KIND";
    if (pairs >= 2) return "TWO PAIR";
    if (pairs == 1) return "ONE PAIR";
    return "NO HAND";
  }

  private static boolean matches(int[] values, int a, int b, int c, int d, int e) {
    return values[0] == a && values[1] == b && values[2] == c && values[3] == d
        && values[4] == e;
  }

  static int basicDamage(int baseDamage, int str, int handPercent) {
    return scaledDamage(baseDamage, CharacterProgressionCore.statPercent(str), handPercent);
  }

  static int skillDamage(int baseDamage, int skillPercent, int skl, int handPercent) {
    long skillBase = ((long)Math.max(1, baseDamage) * Math.max(0, skillPercent) + 50L) / 100L;
    return scaledDamage((int)Math.min(Integer.MAX_VALUE, Math.max(1L, skillBase)),
        CharacterProgressionCore.statPercent(skl), handPercent);
  }

  static int ultimateDamage(int currentDamage, int hitCount, int bonusPercent, int handPercent) {
    long perHit = ((long)Math.max(1, currentDamage) * (100L + Math.max(0, bonusPercent)) + 50L)
        / 100L;
    long total = perHit * Math.max(1, hitCount);
    long scaled = (total * Math.max(0, handPercent) + 50L) / 100L;
    return Math.max(1, (int)Math.min(Integer.MAX_VALUE, scaled));
  }

  private static int scaledDamage(int baseDamage, int statPercent, int handPercent) {
    long scaled = (long)Math.max(1, baseDamage) * Math.max(0, statPercent) * Math.max(0, handPercent);
    return (int)Math.min(Integer.MAX_VALUE, Math.max(1L, (scaled + 5_000L) / 10_000L));
  }

  static int defendedIncomingDamage(int rawDamage, int def) {
    int defPercent = CharacterProgressionCore.statPercent(def);
    long numerator = (long)Math.max(1, rawDamage) * 100L;
    return (int)Math.min(Integer.MAX_VALUE,
        Math.max(1L, (numerator + defPercent / 2L) / defPercent));
  }

  static int piercedIncomingDamage(int rawDamage, int def, int piercePercent) {
    int incoming = Math.max(1, rawDamage);
    int defended = defendedIncomingDamage(incoming, def);
    int pierce = Math.max(0, Math.min(100, piercePercent));
    int prevented = Math.max(0, incoming - defended);
    return Math.max(1, defended + (int)(((long)prevented * pierce + 50L) / 100L));
  }

  static int drainHealAmount(int dealtDamage, int healPercent) {
    if (dealtDamage <= 0 || healPercent <= 0) return 0;
    return Math.max(1, (int)(((long)dealtDamage * Math.min(100, healPercent) + 50L) / 100L));
  }

  static int criticalDamage(int damage) {
    long scaled = (long)Math.max(1, damage) * CRITICAL_DAMAGE_PERCENT;
    return Math.max(1, (int)Math.min(Integer.MAX_VALUE, (scaled + 50L) / 100L));
  }

  static int effectiveChance(int chancePercent, int resistancePercent) {
    return Math.max(0, Math.min(100,
        Math.max(0, chancePercent) - Math.max(0, resistancePercent)));
  }

  static int secondaryStatRoll(int seed, int round, int actorIndex, String salt) {
    long mixed = (seed & 0xffffffffL) * 1_664_525L
        + (long)Math.max(1, round) * 1_013_904_223L
        + (long)(Math.max(0, actorIndex) + 1) * 2_654_435_761L
        + (long)(salt == null ? 0 : salt.hashCode()) * 97_531L;
    mixed ^= mixed >>> 16;
    mixed ^= mixed << 11;
    return (int)Math.floorMod(mixed, 100L);
  }

  private static boolean secondaryChanceTriggers(
      JSONObject combat, int chancePercent, String salt) {
    int chance = Math.max(0, Math.min(100, chancePercent));
    if (chance <= 0) return false;
    if (usesScopedCombatRng(combat)) {
      try {
        return nextCombatInt(combat, 100) < chance;
      } catch (Exception e) {
        throw new IllegalStateException("Combat RNG failure", e);
      }
    }
    int round = Math.max(1, combat.optInt("resolvedRound", combat.optInt("round", 1)));
    int actorIndex = Math.max(0,
        combat.optInt("resolvedActorIndex", combat.optInt("actorIndex", 0)));
    return secondaryStatRoll(combat.optInt("seed", 1), round, actorIndex, salt) < chance;
  }

  static int deterministicDie(int seed, int sequence, int slot) {
    long mixed = (seed & 0xffffffffL) * 1_103_515_245L
        + (long)(sequence + 1) * 12_345L
        + (long)(slot + 1) * 2_654_435_761L;
    mixed ^= mixed >>> 17;
    mixed ^= mixed << 13;
    return 1 + (int)Math.floorMod(mixed, 6L);
  }

  public static JSONObject start(JSONObject state, String entityKey, int gmLogIndex) throws Exception {
    return startInternal(state, new JSONArray().put(entityKey), gmLogIndex, null, 0);
  }

  static JSONObject start(JSONObject state, String entityKey, int gmLogIndex,
                          String rngTurnId, int preTurnStateVersion) throws Exception {
    return startInternal(state, new JSONArray().put(entityKey), gmLogIndex, rngTurnId, preTurnStateVersion);
  }

  static JSONObject start(JSONObject state, JSONArray entityKeys, int gmLogIndex,
                          String rngTurnId, int preTurnStateVersion) throws Exception {
    return startInternal(state, entityKeys, gmLogIndex, rngTurnId, preTurnStateVersion);
  }

  private static JSONObject startInternal(JSONObject state, JSONArray entityKeys, int gmLogIndex,
                                          String rngTurnId, int preTurnStateVersion) throws Exception {
    if (state == null) throw new IllegalArgumentException("state is required");
    if (isActive(state)) return state;

    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(state);
    JSONArray participants = buildParticipants(state, progression);
    if (!hasLivingParticipant(participants)) return state;

    int stageIndex = LevelCore.stageIndex(state);
    JSONArray entities = new JSONArray();
    List<String> seen = new ArrayList<>();
    if (entityKeys != null) {
      for (int i = 0; i < entityKeys.length(); i++) {
        String normalized = entityKeys.optString(i, "").trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || seen.contains(normalized)) continue;
        EntityProfile profile = ENTITIES.get(normalized);
        if (profile == null) continue;
        seen.add(normalized);
        JSONObject scaled = new EntityStatCore().profile(
            normalized, profile.maxHp, profile.damage, stageIndex);
        entities.put(new JSONObject()
            .put("key", profile.key)
            .put("name", profile.name)
            .put("hp", scaled.getInt("maxHp"))
            .put("maxHp", scaled.getInt("maxHp"))
            .put("attack", scaled.getInt("damage"))
            .put("baseHp", profile.maxHp)
            .put("baseDamage", profile.damage)
            .put("stageIndex", stageIndex)
            .put("stagePercent", scaled.getInt("stagePercent"))
            .put("criticalChancePercent", ENTITY_BASE_CRITICAL_PERCENT)
            .put("evasionPercent", ENTITY_BASE_EVASION_PERCENT)
            .put("resCriticalPercent", 0)
            .put("resEvasionPercent", 0)
            .put("bleedTurns", 0)
            .put("bleedPercent", 0)
            .put("poisonTurns", 0)
            .put("poisonPercent", 0)
            .put("armorBreakTurns", 0)
            .put("armorBreakPercent", 0)
            .put("accuracyPenaltyTurns", 0)
            .put("accuracyPenalty", 0)
            .put("stunTurns", 0)
            .put("alive", true)
            .put("status", "alive"));
      }
    }
    if (entities.length() == 0) return state;

    String seedKey = entities.length() == 1
        ? entities.getJSONObject(0).getString("key")
        : entityKeySignature(entities);
    JSONObject combat = new JSONObject()
        .put("active", true)
        .put("round", 1)
        .put("actorIndex", firstLivingIndex(participants))
        .put("activeEntityIndex", 0)
        .put("targetEntityIndex", 0)
        .put("rngSequence", 0)
        .put("seed", stableSeed(state, seedKey, participants))
        .put("rngTurnId", rngTurnId == null ? "" : rngTurnId)
        .put("rngPreTurnStateVersion", Math.max(0, preTurnStateVersion))
        .put("rngCanonVersion", EmergentTurnEngine.CANON_VERSION)
        .put("rngSchemaVersion", EmergentTurnEngine.RNG_SCHEMA_VERSION)
        .put("logIndex", Math.max(0, gmLogIndex))
        .put("deathRestartAnchorLocation", state.optString("location", ""))
        .put("deathRestartLevelKey", state.optString(
            LevelCore.LEVEL_KEY, String.valueOf(state.optInt("currentLevel", 0))))
        .put("participants", participants)
        .put("stageIndex", stageIndex)
        .put("entities", entities)
        .put("entityDeaths", new JSONArray())
        .put("entityDeathsThisTurn", new JSONArray())
        .put("coreDropReward", 0)
        .put("coreDropResolved", false)
        .put("lootResolved", false);

    syncActiveEntityAlias(combat);
    state.put("combat", combat);
    prepareCurrentTurn(combat);
    return state;
  }

  public static JSONObject setHold(JSONObject state, int dieIndex, boolean held) throws Exception {
    if (!isActive(state)) throw new IllegalStateException("Không có trận chiến đang hoạt động.");
    if (dieIndex < 0 || dieIndex >= DICE_COUNT) throw new IllegalArgumentException("Die index không hợp lệ.");
    JSONObject dice = diceState(state.getJSONObject("combat"));
    if (!dice.optBoolean("hasRolled", false)) {
      throw new IllegalStateException("Phải ROLL trước khi HOLD.");
    }
    if (dice.optBoolean("finalized", false)) {
      throw new IllegalStateException("Hand đã được chốt.");
    }
    dice.getJSONArray("held").put(dieIndex, held);
    return state;
  }

  public static JSONObject roll(JSONObject state) throws Exception {
    if (!isActive(state)) throw new IllegalStateException("Không có trận chiến đang hoạt động.");
    JSONObject combat = state.getJSONObject("combat");
    JSONObject dice = diceState(combat);
    if (dice.optBoolean("finalized", false)) return state;

    boolean hasRolled = dice.optBoolean("hasRolled", false);
    if (!hasRolled) {
      rollDice(combat, dice, false);
      dice.put("hasRolled", true).put("rerollsUsed", 0);
      updateHand(dice);
      autoHoldDuplicateGroups(dice);
      return state;
    }

    int rerollsUsed = Math.max(0, dice.optInt("rerollsUsed", 0));
    if (rerollsUsed >= MAX_REROLLS || allHeld(dice.getJSONArray("held"))) return state;

    rollDice(combat, dice, true);
    dice.put("rerollsUsed", rerollsUsed + 1);
    updateHand(dice);
    autoHoldDuplicateGroups(dice);
    return state;
  }

  public static JSONObject finishHand(JSONObject state) throws Exception {
    if (!isActive(state)) throw new IllegalStateException("Không có trận chiến đang hoạt động.");
    JSONObject dice = diceState(state.getJSONObject("combat"));
    if (!dice.optBoolean("hasRolled", false)) {
      throw new IllegalStateException("Dice chưa có Initial Roll.");
    }
    if (!dice.optBoolean("finalized", false)) finalizeHand(dice);
    return state;
  }

  public static JSONObject resolveFinalized(JSONObject state) throws Exception {
    if (!isActive(state)) return state;
    JSONObject combat = state.getJSONObject("combat");
    JSONArray entities = normalizeCombatEntities(combat);
    JSONObject dice = diceState(combat);
    if (!dice.optBoolean("finalized", false)) {
      throw new IllegalStateException("Hand chưa được chốt.");
    }
    if (dice.optBoolean("resolved", false)) return state;

    combat.put("feedbackEvents", new JSONArray());
    combat.put("resolvedEntityTurn", false);
    combat.put("resolvedEntityTurns", new JSONArray());
    combat.put("entityDeathsThisTurn", new JSONArray());

    JSONArray participants = combat.getJSONArray("participants");
    int actorIndex = currentLivingIndex(combat, participants);
    if (actorIndex < 0) {
      finishDefeat(state, combat);
      return state;
    }
    JSONObject actor = participants.getJSONObject(actorIndex);
    combat.put("actorIndex", actorIndex);
    combat.put("resolvedActorIndex", actorIndex);
    combat.put("resolvedActorName", actor.optString("name", "Nhân vật"));
    combat.put("resolvedRound", Math.max(1, combat.optInt("round", 1)));

    int initiativeIndex = nextLivingEntityIndex(
        entities, combat.optInt("activeEntityIndex", 0), true);
    if (actorIndex == firstLivingIndex(participants) && combat.optInt("round", 1) > 1) {
      int chosenTarget = combat.optInt("targetEntityIndex", initiativeIndex);
      for (int i = 0; i < entities.length(); i++) {
        JSONObject current = entities.optJSONObject(i);
        if (!entityAlive(current)) continue;
        combat.put("feedbackEntityIndex", i);
        tickRoundStartEffects(combat, current);
        if (current.optInt("hp", 0) <= 0) resolveEntityDeath(state, combat, entities, i);
      }
      combat.remove("feedbackEntityIndex");
      if (!hasLivingEntity(entities)) {
        dice.put("resolved", true);
        syncParticipants(state, participants);
        finishVictory(state, combat);
        appendDeathLines(state, combat);
        return state;
      }
      initiativeIndex = nextLivingEntityIndex(entities, initiativeIndex, true);
      if (initiativeIndex < 0) initiativeIndex = firstLivingEntityIndex(entities);
      combat.put("activeEntityIndex", initiativeIndex);
      if (chosenTarget < 0 || chosenTarget >= entities.length()
          || !entityAlive(entities.optJSONObject(chosenTarget))) {
        chosenTarget = initiativeIndex;
      }
      combat.put("targetEntityIndex", chosenTarget);
      syncActiveEntityAlias(combat);
    }

    int targetIndex = livingTargetIndex(combat, entities);
    if (targetIndex < 0) {
      dice.put("resolved", true);
      syncParticipants(state, participants);
      finishVictory(state, combat);
      appendDeathLines(state, combat);
      return state;
    }
    combat.put("resolvedTargetEntityIndex", targetIndex);
    combat.put("feedbackEntityIndex", targetIndex);
    JSONObject entity = entities.getJSONObject(targetIndex);

    String hand = dice.optString("hand", "NO HAND");
    ActionResult result = resolveHandAction(combat, actor, entity, hand);
    dice.put("resolved", true);
    if (entity.optInt("hp", 0) <= 0) {
      resolveEntityDeath(state, combat, entities, targetIndex);
    }
    combat.remove("feedbackEntityIndex");

    JSONArray entityTurns = new JSONArray();
    int lastResponded = -1;
    if (hasLivingEntity(entities) && actor.optInt("hp", 0) > 0) {
      int responseStart = nextLivingEntityIndex(entities, initiativeIndex, true);
      if (responseStart >= 0) {
        for (int step = 0; step < entities.length() && actor.optInt("hp", 0) > 0; step++) {
          int index = Math.floorMod(responseStart + step, entities.length());
          JSONObject responder = entities.optJSONObject(index);
          if (!entityAlive(responder)) continue;
          combat.put("activeEntityIndex", index);
          syncActiveEntityAlias(combat);
          String summary = resolveEntityResponse(combat, actor, responder, result.evadeResponse);
          entityTurns.put(new JSONObject()
              .put("entityIndex", index)
              .put("entityKey", responder.optString("key", ""))
              .put("entityName", responder.optString("name", "Entity"))
              .put("summary", summary));
          lastResponded = index;
        }
      }
    }
    combat.put("resolvedEntityTurns", entityTurns);
    combat.put("resolvedEntityTurn", entityTurns.length() > 0);

    String passiveSummary = applyDaiDaoMaTonAfterTurn(combat, actor);
    syncParticipants(state, participants);
    new CharacterProgressionCore().advanceStatusEffects(
        state, actor.optString("id", ""), "actor_turn");
    refreshParticipant(state, actor);

    if (!hasLivingEntity(entities)) {
      finishVictory(state, combat);
    } else if (isCaoMinhDown(participants) || !hasLivingParticipant(participants)) {
      finishDefeat(state, combat);
    } else {
      int nextEntity = nextLivingEntityIndex(
          entities, lastResponded >= 0 ? lastResponded : initiativeIndex, false);
      if (nextEntity < 0) nextEntity = firstLivingEntityIndex(entities);
      combat.put("activeEntityIndex", nextEntity);
      int target = combat.optInt("targetEntityIndex", nextEntity);
      if (target < 0 || target >= entities.length() || !entityAlive(entities.optJSONObject(target))) {
        combat.put("targetEntityIndex", nextEntity);
      }
      syncActiveEntityAlias(combat);
      advanceActor(combat);
      combat.put("nextActorIndex", combat.optInt("actorIndex", 0));
      prepareCurrentTurn(combat);
    }

    appendBattleLine(state, combat, result.summary);
    appendDeathLines(state, combat);
    for (int i = 0; i < entityTurns.length(); i++) {
      JSONObject turn = entityTurns.optJSONObject(i);
      if (turn != null) appendBattleLine(state, combat, turn.optString("summary", ""));
    }
    appendBattleLine(state, combat, passiveSummary);
    return state;
  }

  private static ActionResult resolveHandAction(JSONObject combat, JSONObject actor, JSONObject entity,
                                                String hand) throws Exception {
    ActionResult result = new ActionResult();
    String actorName = actor.optString("name", "Nhân vật");
    int str = actor.optInt("STR", CharacterProgressionCore.BASE_STAT);
    int skl = actor.optInt("SKL", CharacterProgressionCore.BASE_STAT);
    int baseAttack = Math.max(1, actor.optInt("baseAttack", CAO_MINH_BASE_ATTACK));
    result.evadeResponse = "TWO PAIR".equals(hand);

    if ("NO HAND".equals(hand) || "ONE PAIR".equals(hand) || "TWO PAIR".equals(hand)) {
      String action = "TWO PAIR".equals(hand) ? "né và phản công" : "đánh thường";
      if (entityEvadesActor(combat, actor, entity)) {
        addFeedback(combat, "actor", "entity", "miss", "", false);
        result.summary = actorMissSummary(hand, actorName, action, false, entity);
        return result;
      }
      int hpBefore = Math.max(0, entity.optInt("hp", 0));
      int handPercent = "ONE PAIR".equals(hand) ? 125 : 100;
      boolean critical = actorCriticalTriggers(combat, actor, entity);
      int damage = basicDamage(baseAttack, str, handPercent);
      if (critical) damage = criticalDamage(damage);
      applyEntityDamage(combat, entity, damage);
      List<String> effects = applyCharacterProcs(combat, actor, entity, baseAttack);
      result.summary = actorBattleSummary(
          hand, actorName, action, false, critical, entity, hpBefore, effects);
      return result;
    }

    if ("SSF".equals(hand) || "FSF".equals(hand)) {
      int handPercent = "FSF".equals(hand) ? 200 : 100;
      Ultimate ultimate = ULTIMATES.get(actor.optString("id", ""));
      if (ultimate == null || ultimate.hitCount <= 0) {
        result.summary = "[" + handToken(hand) + "] " + actorName
            + " chưa có Ultimate authoritative.";
        return result;
      }
      // SSF/FSF are authoritative Poker Dice ultimates. Keep them deterministic and do not let
      // passive Critical/Evasion rolls multiply or nullify a rare hand outcome.
      int hpBefore = Math.max(0, entity.optInt("hp", 0));
      int currentDamage = basicDamage(baseAttack, str, 100);
      int damage = ultimateDamage(currentDamage, ultimate.hitCount, ultimate.bonusPercent, handPercent);
      applyEntityDamage(combat, entity, damage);
      result.summary = actorBattleSummary(
          hand, actorName, ultimate.name, true, false, entity, hpBefore, new ArrayList<String>());
      return result;
    }

    int handPercent = skillHandPercent(hand);
    JSONObject selected = combat.optJSONObject("currentSkill");
    if (selected == null || selected.optString("name", "").isEmpty()) {
      result.summary = "[" + handToken(hand) + "] " + actorName
          + " không có Skill authoritative.";
      return result;
    }

    String skillName = selected.optString("name", "Skill");
    if (entityEvadesActor(combat, actor, entity)) {
      addFeedback(combat, "actor", "entity", "miss", "", false);
      result.summary = actorMissSummary(hand, actorName, skillName, true, entity);
      return result;
    }

    int hpBefore = Math.max(0, entity.optInt("hp", 0));
    boolean critical = actorCriticalTriggers(combat, actor, entity);
    int damage = skillDamage(baseAttack, selected.optInt("damagePercent", 100), skl, handPercent);
    if (critical) damage = criticalDamage(damage);
    applyEntityDamage(combat, entity, damage);

    List<String> effects = new ArrayList<>();
    String selectedEffect = selected.optString("effect", "");
    applySkillEffect(entity, selected);
    if (isTrackedStatusEffect(selectedEffect)) effects.add(selectedEffect);
    effects.addAll(applyCharacterProcs(combat, actor, entity, baseAttack));

    result.summary = actorBattleSummary(
        hand, actorName, skillName, true, critical, entity, hpBefore, effects);
    return result;
  }

  private static boolean entityEvadesActor(JSONObject combat, JSONObject actor, JSONObject entity) {
    int chance = effectiveChance(
        entity.optInt("evasionPercent", 0), actor.optInt("resEvasionPercent", 0));
    return secondaryChanceTriggers(combat, chance,
        "entity-evasion:" + entity.optString("key", "") + ":" + actor.optString("id", ""));
  }

  private static boolean actorCriticalTriggers(JSONObject combat, JSONObject actor, JSONObject entity) {
    int chance = effectiveChance(
        actor.optInt("criticalChancePercent", 0), entity.optInt("resCriticalPercent", 0));
    return secondaryChanceTriggers(combat, chance,
        "actor-critical:" + actor.optString("id", "") + ":" + entity.optString("key", ""));
  }

  private static String actorMissSummary(String hand, String actorName, String action,
                                         boolean usesSkillVerb, JSONObject entity) {
    return "[" + handToken(hand) + "] " + actorName + (usesSkillVerb ? " dùng " : " ")
        + action + " nhưng " + entity.optString("name", "Entity") + " né được.";
  }

  private static int skillHandPercent(String hand) {
    if ("STRAIGHT".equals(hand)) return 150;
    if ("FULL HOUSE".equals(hand)) return 200;
    if ("FOUR OF A KIND".equals(hand)) return 250;
    return 100;
  }

  private static String handToken(String hand) {
    if ("ONE PAIR".equals(hand)) return "PAIR";
    if ("THREE OF A KIND".equals(hand)) return "TRIPLE";
    if ("FOUR OF A KIND".equals(hand)) return "F.O.A.K";
    return hand;
  }

  private static String canonicalStatusEffect(String effect) {
    if ("Chảy máu".equals(effect)
        || "Trúng độc".equals(effect)
        || "Xuyên giáp".equals(effect)
        || "Choáng".equals(effect)
        || "Mất phương hướng".equals(effect)) {
      return effect;
    }
    if ("Phá giáp".equals(effect)) return "Xuyên giáp";
    return "";
  }

  private static boolean isTrackedStatusEffect(String effect) {
    return !canonicalStatusEffect(effect).isEmpty();
  }

  private static String actorBattleSummary(String hand, String actorName, String action,
                                           boolean usesSkillVerb, boolean critical, JSONObject entity,
                                           int hpBefore, List<String> effects) {
    int hp = Math.max(0, entity.optInt("hp", 0));
    int maxHp = Math.max(1, entity.optInt("maxHp", 1));
    int dealt = Math.max(0, hpBefore - hp);
    StringBuilder text = new StringBuilder()
        .append("[").append(handToken(hand)).append("] ")
        .append(critical ? "[CRITICAL] " : "")
        .append(actorName).append(usesSkillVerb ? " dùng " : " ")
        .append(action).append(", ")
        .append(entity.optString("name", "Entity"))
        .append(" -").append(dealt).append(" HP [")
        .append(hp).append("/").append(maxHp).append(" HP]");
    String effectText = effectSummary(entity, effects);
    if (!effectText.isEmpty()) text.append(" và bị ").append(effectText);
    return text.append(".").toString();
  }

  private static String effectSummary(JSONObject entity, List<String> effects) {
    List<String> unique = new ArrayList<>();
    if (effects != null) {
      for (String effect : effects) {
        String canonical = canonicalStatusEffect(effect);
        if (!canonical.isEmpty() && !unique.contains(canonical)) unique.add(canonical);
      }
    }

    List<String> details = new ArrayList<>();
    for (String effect : unique) {
      if ("Chảy máu".equals(effect)) {
        int turns = Math.max(0, entity.optInt("bleedTurns", 0));
        if (turns > 0) details.add("Chảy máu trong " + turns + " lượt");
      } else if ("Trúng độc".equals(effect)) {
        int turns = Math.max(0, entity.optInt("poisonTurns", 0));
        if (turns > 0) details.add("Trúng độc trong " + turns + " lượt");
      } else if ("Xuyên giáp".equals(effect)) {
        int turns = Math.max(0, entity.optInt("armorBreakTurns", 0));
        int percent = Math.max(0, entity.optInt("armorBreakPercent", 0));
        if (turns > 0 && percent > 0) {
          details.add("Xuyên giáp " + percent + "% trong " + turns + " lượt");
        }
      } else if ("Choáng".equals(effect)) {
        int turns = Math.max(0, entity.optInt("stunTurns", 0));
        if (turns > 0) details.add("Choáng trong " + turns + " lượt");
      } else if ("Mất phương hướng".equals(effect)) {
        int turns = Math.max(0, entity.optInt("accuracyPenaltyTurns", 0));
        int percent = Math.max(0, entity.optInt("accuracyPenalty", 0));
        if (turns > 0 && percent > 0) {
          details.add("Mất phương hướng -" + percent + "% chính xác trong " + turns + " lượt");
        }
      }
    }
    return String.join(", ", details);
  }

  private static void applyEntityDamage(JSONObject combat, JSONObject entity, int damage)
      throws Exception {
    int raw = Math.max(1, damage);
    int armorBreak = entity.optInt("armorBreakTurns", 0) > 0
        ? Math.max(0, entity.optInt("armorBreakPercent", 0)) : 0;
    int resolved = Math.max(1, (int)(((long)raw * (100L + armorBreak) + 50L) / 100L));
    int hp = Math.max(0, entity.optInt("hp", 0) - resolved);
    entity.put("hp", hp);
    addFeedback(combat, "actor", "entity", "damage", "-" + resolved + " HP", true);
  }

  private static void applySkillEffect(JSONObject entity, JSONObject selected) throws Exception {
    if (entity.optInt("hp", 0) <= 0) return;
    applyStackingEffect(entity,
        selected.optString("effect", ""),
        Math.max(0, selected.optInt("effectTurns", 0)),
        Math.max(0, selected.optInt("effectValue", 0)));
  }

  private static List<String> applyCharacterProcs(JSONObject combat, JSONObject actor,
                                                   JSONObject entity, int baseAttack) throws Exception {
    List<String> effects = new ArrayList<>();
    if (entity.optInt("hp", 0) <= 0) return effects;
    String actorId = CharacterProgressionCore.normalizeCharacterId(actor.optString("id", ""));
    List<CharacterProc> pool = CHARACTER_PROCS.get(actorId);
    if (pool == null || pool.isEmpty()) return effects;

    for (CharacterProc proc : pool) {
      int roll;
      if (usesScopedCombatRng(combat)) {
        roll = nextCombatInt(combat, 100);
      } else {
        int sequence = combat.optInt("rngSequence", 0);
        roll = characterProcRoll(combat.optInt("seed", 1), sequence, actorId, proc.name);
        combat.put("rngSequence", sequence + 1);
      }
      if (roll >= proc.procPercent) continue;

      int bonus = Math.max(1,
          (int)(((long)Math.max(1, baseAttack) * proc.bonusDamagePercent + 50L) / 100L));
      applyEntityDamage(combat, entity, bonus);
      if (entity.optInt("hp", 0) <= 0) return effects;
      applyStackingEffect(entity, proc.effect, proc.effectTurns, proc.effectValue);
      if (isTrackedStatusEffect(proc.effect)) effects.add(proc.effect);
    }
    return effects;
  }

  static void applyStackingEffect(JSONObject entity, String effect, int turns, int value)
      throws Exception {
    effect = canonicalStatusEffect(effect);
    if ("Choáng".equals(effect) && turns > 0) {
      // Stun is the only effect whose duration stacks. Cap prevents permanent stun-lock.
      entity.put("stunTurns", Math.min(3,
          Math.max(0, entity.optInt("stunTurns", 0)) + turns));
      return;
    }

    if ("Chảy máu".equals(effect) && turns > 0 && value > 0) {
      int currentTurns = Math.max(0, entity.optInt("bleedTurns", 0));
      int currentPercent = currentTurns > 0 ? Math.max(0, entity.optInt("bleedPercent", 0)) : 0;
      entity.put("bleedTurns", currentTurns > 0 ? currentTurns : turns);
      entity.put("bleedPercent", Math.min(25, currentPercent + value));
      return;
    }

    if ("Trúng độc".equals(effect) && turns > 0 && value > 0) {
      int currentTurns = Math.max(0, entity.optInt("poisonTurns", 0));
      int currentPercent = currentTurns > 0 ? Math.max(0, entity.optInt("poisonPercent", 0)) : 0;
      entity.put("poisonTurns", currentTurns > 0 ? currentTurns : turns);
      entity.put("poisonPercent", Math.min(25, currentPercent + value));
      return;
    }

    if ("Xuyên giáp".equals(effect) && turns > 0 && value > 0) {
      int currentTurns = Math.max(0, entity.optInt("armorBreakTurns", 0));
      int currentPercent = currentTurns > 0 ? Math.max(0, entity.optInt("armorBreakPercent", 0)) : 0;
      entity.put("armorBreakTurns", currentTurns > 0 ? currentTurns : turns);
      entity.put("armorBreakPercent", Math.min(75, currentPercent + value));
      return;
    }

    if ("Mất phương hướng".equals(effect) && turns > 0 && value > 0) {
      entity.put("accuracyPenaltyTurns",
          Math.max(Math.max(0, entity.optInt("accuracyPenaltyTurns", 0)), turns));
      entity.put("accuracyPenalty",
          Math.max(Math.max(0, entity.optInt("accuracyPenalty", 0)), Math.min(95, value)));
    }
  }

  private static void consumeAccuracyPenaltyTurn(JSONObject entity) throws Exception {
    int turns = Math.max(0, entity.optInt("accuracyPenaltyTurns", 0));
    if (turns <= 0) return;
    int remaining = turns - 1;
    entity.put("accuracyPenaltyTurns", remaining);
    if (remaining == 0) entity.put("accuracyPenalty", 0);
  }

  private static String resolveEntityResponse(JSONObject combat, JSONObject actor, JSONObject entity,
                                              boolean evade) throws Exception {
    String entityName = entity.optString("name", "Entity");
    String actorName = actor.optString("name", "Nhân vật");
    int stunTurns = Math.max(0, entity.optInt("stunTurns", 0));
    if (stunTurns > 0) {
      entity.put("stunTurns", stunTurns - 1);
      consumeAccuracyPenaltyTurn(entity);
      addFeedback(combat, "entity", "actor", "miss", "", false);
      return entityName + " bị Choáng, không thể tấn công " + actorName + ".";
    }

    if (evade) {
      consumeAccuracyPenaltyTurn(entity);
      addFeedback(combat, "entity", "actor", "miss", "", false);
      return entityName + " tấn công " + actorName + " nhưng " + actorName + " né được.";
    }

    int accuracyTurns = Math.max(0, entity.optInt("accuracyPenaltyTurns", 0));
    int accuracyPenalty = accuracyTurns > 0
        ? Math.max(0, Math.min(95, entity.optInt("accuracyPenalty", 0))) : 0;
    if (accuracyPenalty > 0) {
      int roll = nextPercent(combat,
          "accuracy:" + entity.optString("key", "") + ":" + actor.optString("id", ""));
      if (roll < accuracyPenalty) {
        consumeAccuracyPenaltyTurn(entity);
        addFeedback(combat, "entity", "actor", "miss", "", false);
        return entityName + " bị Mất phương hướng và đánh trượt " + actorName + ".";
      }
    }

    int passiveEvasion = effectiveChance(
        actor.optInt("evasionPercent", 0), entity.optInt("resEvasionPercent", 0));
    if (secondaryChanceTriggers(combat, passiveEvasion,
        "actor-evasion:" + actor.optString("id", "") + ":" + entity.optString("key", ""))) {
      consumeAccuracyPenaltyTurn(entity);
      addFeedback(combat, "entity", "actor", "miss", "", false);
      return entityName + " tấn công " + actorName + " nhưng " + actorName
          + " né được nhờ Evasion.";
    }

    int before = Math.max(0, actor.optInt("hp", 0));
    int rawDamage = Math.max(1, entity.optInt("attack", 1));
    int def = actor.optInt("DEF", CharacterProgressionCore.BASE_STAT);
    int criticalChance = effectiveChance(
        entity.optInt("criticalChancePercent", 0), actor.optInt("resCriticalPercent", 0));
    boolean critical = secondaryChanceTriggers(combat, criticalChance,
        "entity-critical:" + entity.optString("key", "") + ":" + actor.optString("id", ""));
    List<EntitySkill> pool = ENTITY_SKILLS.get(entity.optString("key", ""));
    List<String> triggeredSkills = new ArrayList<>();
    if (pool != null) {
      int seed = combat.optInt("seed", 1);
      int round = Math.max(1, combat.optInt("round", 1));
      int actorIndex = Math.max(0, combat.optInt("actorIndex", 0));
      for (int i = 0; i < pool.size(); i++) {
        EntitySkill skill = pool.get(i);
        int procRoll = usesScopedCombatRng(combat)
            ? nextCombatInt(combat, 100)
            : entitySkillProcRoll(seed, round, actorIndex, i);
        if (procRoll >= skill.procPercent) continue;
        triggeredSkills.add(skill.name);
        int skillDamageDealt = 0;
        for (int hit = 0; hit < skill.hitCount && actor.optInt("hp", 0) > 0; hit++) {
          int incoming = entitySkillDamage(rawDamage, skill.damagePercent);
          if (critical) incoming = criticalDamage(incoming);
          int damage = piercedIncomingDamage(incoming, def, skill.defensePiercePercent);
          int hpBeforeHit = Math.max(0, actor.optInt("hp", 0));
          actor.put("hp", Math.max(0, hpBeforeHit - damage));
          int actualDamage = Math.max(0, hpBeforeHit - actor.optInt("hp", 0));
          skillDamageDealt += actualDamage;
          addFeedback(combat, "entity", "actor", "damage", "-" + actualDamage + " HP", true);
        }
        if (skill.healPercentOfDamage > 0 && skillDamageDealt > 0) {
          int heal = drainHealAmount(skillDamageDealt, skill.healPercentOfDamage);
          int hpBeforeHeal = Math.max(0, entity.optInt("hp", 0));
          int maxEntityHp = Math.max(1, entity.optInt("maxHp", 1));
          entity.put("hp", Math.min(maxEntityHp, hpBeforeHeal + heal));
          int actualHeal = Math.max(0, entity.optInt("hp", 0) - hpBeforeHeal);
          if (actualHeal > 0) {
            addFeedback(combat, "entity", "entity", "heal", "+" + actualHeal + " HP", true);
          }
        }
      }
    }

    if (triggeredSkills.isEmpty()) {
      int incoming = critical ? criticalDamage(rawDamage) : rawDamage;
      int damage = defendedIncomingDamage(incoming, def);
      actor.put("hp", Math.max(0, actor.optInt("hp", 0) - damage));
      addFeedback(combat, "entity", "actor", "damage", "-" + damage + " HP", true);
    }

    int hp = Math.max(0, actor.optInt("hp", 0));
    int maxHp = Math.max(1, actor.optInt("maxHp", 1));
    int dealt = Math.max(0, before - hp);
    String action = triggeredSkills.isEmpty()
        ? "tấn công"
        : "dùng " + String.join(" + ", triggeredSkills);
    consumeAccuracyPenaltyTurn(entity);
    return entityName + (critical ? " [CRITICAL] " : " ") + action + ", "
        + actorName + " -" + dealt + " HP [" + hp + "/" + maxHp + " HP].";
  }

  private static void tickRoundStartEffects(JSONObject combat, JSONObject entity) throws Exception {
    if (entity.optInt("hp", 0) <= 0) return;

    int bleedTurns = Math.max(0, entity.optInt("bleedTurns", 0));
    int bleedPercent = Math.max(0, entity.optInt("bleedPercent", 0));
    if (bleedTurns > 0 && bleedPercent > 0) {
      int damage = Math.max(1, entity.optInt("maxHp", 1) * bleedPercent / 100);
      entity.put("hp", Math.max(0, entity.optInt("hp", 0) - damage));
      int remaining = bleedTurns - 1;
      entity.put("bleedTurns", remaining);
      if (remaining == 0) entity.put("bleedPercent", 0);
      addFeedback(combat, "actor", "entity", "damage", "-" + damage + " HP", true);
    }

    if (entity.optInt("hp", 0) > 0) {
      int poisonTurns = Math.max(0, entity.optInt("poisonTurns", 0));
      int poisonPercent = Math.max(0, entity.optInt("poisonPercent", 0));
      if (poisonTurns > 0 && poisonPercent > 0) {
        int damage = Math.max(1, entity.optInt("maxHp", 1) * poisonPercent / 100);
        entity.put("hp", Math.max(0, entity.optInt("hp", 0) - damage));
        int remaining = poisonTurns - 1;
        entity.put("poisonTurns", remaining);
        if (remaining == 0) entity.put("poisonPercent", 0);
        addFeedback(combat, "actor", "entity", "damage", "-" + damage + " HP", true);
      }
    }

    int armorTurns = Math.max(0, entity.optInt("armorBreakTurns", 0));
    if (armorTurns > 0) {
      int remaining = armorTurns - 1;
      entity.put("armorBreakTurns", remaining);
      if (remaining == 0) entity.put("armorBreakPercent", 0);
    }
  }

  private static JSONArray buildParticipants(JSONObject state, CharacterProgressionCore progression)
      throws Exception {
    JSONArray output = new JSONArray();
    JSONObject player = state.optJSONObject("player");
    String playerName = player == null ? "Cao Minh" : player.optString("name", "Cao Minh");
    output.put(participant(state, progression, "cao_minh", playerName, -1, player));

    JSONArray party = state.optJSONArray("party");
    if (party == null) return output;
    for (int i = 0; i < party.length() && output.length() < MAX_COMBAT_PARTICIPANTS; i++) {
      JSONObject member = party.optJSONObject(i);
      if (member == null || !CharacterEncounterCore.isJoinedMember(member)) continue;
      String id = CharacterProgressionCore.normalizeCharacterId(
          member.optString("id", member.optString("name", "")));
      if (id.isEmpty() || "cao_minh".equals(id)) continue;
      String name = member.optString("name", id);
      output.put(participant(state, progression, id, name, i, member));
    }
    return output;
  }

  private static JSONObject participant(JSONObject state, CharacterProgressionCore progression,
                                        String id, String name, int sourceIndex, JSONObject source)
      throws Exception {
    JSONObject projected = new CharacterStatCore().project(state, source, id, progression);
    JSONObject stats = projected.getJSONObject("stats");
    JSONObject combatStatus = projected.getJSONObject("combatStatus");
    int baseAttack = baseAttackFor(source, id);
    int baseCritical = combatStatus.getInt("criticalChancePercent");
    JSONObject participant = new JSONObject()
        .put("id", id)
        .put("name", name)
        .put("sourceIndex", sourceIndex)
        .put("hp", projected.getInt("currentHp"))
        .put("maxHp", projected.getInt("maxHp"))
        .put("baseAttack", baseAttack)
        .put("baseCriticalChancePercent", baseCritical)
        .put("STR", stats.getJSONObject("STR").getInt("effective"))
        .put("DEF", stats.getJSONObject("DEF").getInt("effective"))
        .put("SKL", stats.getJSONObject("SKL").getInt("effective"))
        .put("VIT", stats.getJSONObject("VIT").getInt("effective"))
        .put("criticalChancePercent", baseCritical)
        .put("evasionPercent", combatStatus.getInt("evasionPercent"))
        .put("resCriticalPercent", combatStatus.getInt("resCriticalPercent"))
        .put("resEvasionPercent", combatStatus.getInt("resEvasionPercent"));

    if ("cao_minh".equals(id)) {
      participant.put("passiveSkill", DAI_DAO_MA_TON)
          .put("daiDaoMaTonStacks", 0)
          .put("daiDaoMaTonBaseAttack", baseAttack)
          .put("daiDaoMaTonAttackBonusPercent", 0)
          .put("daiDaoMaTonCriticalBonusPercent", 0)
          .put("daiDaoMaTonHealPercent", DAI_DAO_MA_TON_HEAL_PERCENT);
    } else {
      participant.put("daiDaoMaTonAllyCriticalBonusPercent",
          DAI_DAO_MA_TON_ALLY_CRITICAL_BONUS_PERCENT);
      participant.put("criticalChancePercent", Math.min(MAX_CRITICAL_CHANCE_PERCENT,
          baseCritical + DAI_DAO_MA_TON_ALLY_CRITICAL_BONUS_PERCENT));
    }
    return participant;
  }

  private static void refreshParticipant(JSONObject state, JSONObject actor) throws Exception {
    String id = actor.optString("id", "");
    int passiveStacks = Math.max(0, actor.optInt("daiDaoMaTonStacks", 0));
    int sourceIndex = actor.optInt("sourceIndex", -1);
    JSONArray party = state.optJSONArray("party");
    JSONObject source = sourceIndex < 0 ? state.optJSONObject("player")
        : party == null || sourceIndex >= party.length() ? null : party.optJSONObject(sourceIndex);
    JSONObject updated = participant(state, new CharacterProgressionCore(), id,
        actor.optString("name", id), sourceIndex, source);
    for (String key : new String[]{"hp", "maxHp", "baseAttack", "STR", "DEF", "SKL", "VIT",
        "criticalChancePercent", "evasionPercent", "resCriticalPercent", "resEvasionPercent",
        "baseCriticalChancePercent"}) {
      actor.put(key, updated.get(key));
    }

    if ("cao_minh".equals(id)) {
      actor.put("passiveSkill", DAI_DAO_MA_TON)
          .put("daiDaoMaTonBaseAttack", updated.getInt("daiDaoMaTonBaseAttack"))
          .put("daiDaoMaTonStacks", passiveStacks)
          .put("daiDaoMaTonHealPercent", DAI_DAO_MA_TON_HEAL_PERCENT);
      applyDaiDaoMaTonStackBonuses(actor);
    } else {
      actor.put("daiDaoMaTonAllyCriticalBonusPercent",
          updated.getInt("daiDaoMaTonAllyCriticalBonusPercent"));
    }
  }

  private static String applyDaiDaoMaTonAfterTurn(JSONObject combat, JSONObject actor)
      throws Exception {
    if (!"cao_minh".equals(actor.optString("id", "")) || actor.optInt("hp", 0) <= 0) return "";

    int maxHp = Math.max(1, actor.optInt("maxHp", 1));
    int hpBefore = Math.max(0, actor.optInt("hp", 0));
    int healAmount = Math.max(1,
        (int)Math.min(Integer.MAX_VALUE, ((long)maxHp * DAI_DAO_MA_TON_HEAL_PERCENT + 50L) / 100L));
    int hpAfter = Math.min(maxHp, hpBefore + healAmount);
    int healed = Math.max(0, hpAfter - hpBefore);
    actor.put("hp", hpAfter);

    int stacks = Math.max(0, actor.optInt("daiDaoMaTonStacks", 0)) + 1;
    actor.put("daiDaoMaTonStacks", stacks);
    applyDaiDaoMaTonStackBonuses(actor);

    if (healed > 0) {
      addFeedback(combat, "actor", "actor", "heal", "+" + healed + " HP", true);
    }
    return DAI_DAO_MA_TON + ": Cao Minh hồi " + healed + " HP; Attack +"
        + actor.getInt("daiDaoMaTonAttackBonusPercent") + "%, Critical +"
        + actor.getInt("daiDaoMaTonCriticalBonusPercent") + "% (" + stacks + " tầng).";
  }

  private static void applyDaiDaoMaTonStackBonuses(JSONObject actor) throws Exception {
    int stacks = Math.max(0, actor.optInt("daiDaoMaTonStacks", 0));
    int baseAttack = Math.max(1,
        actor.optInt("daiDaoMaTonBaseAttack", actor.optInt("baseAttack", CAO_MINH_BASE_ATTACK)));
    long attackBonus = Math.min(Integer.MAX_VALUE,
        (long)stacks * DAI_DAO_MA_TON_ATTACK_PER_TURN_PERCENT);
    long totalPercent = 100L + attackBonus;
    int attack = (int)Math.min(Integer.MAX_VALUE,
        Math.max(1L, ((long)baseAttack * totalPercent + 50L) / 100L));

    int baseCritical = Math.max(0,
        actor.optInt("baseCriticalChancePercent", actor.optInt("criticalChancePercent", 0)));
    int criticalBonus = (int)Math.min(Integer.MAX_VALUE,
        (long)stacks * DAI_DAO_MA_TON_CRITICAL_PER_TURN_PERCENT);
    int critical = Math.min(MAX_CRITICAL_CHANCE_PERCENT, baseCritical + criticalBonus);

    actor.put("baseAttack", attack)
        .put("criticalChancePercent", critical)
        .put("daiDaoMaTonAttackBonusPercent", (int)attackBonus)
        .put("daiDaoMaTonCriticalBonusPercent", criticalBonus);
  }

  static int baseAttackFor(JSONObject source, String rawId) {
    String id = CharacterProgressionCore.normalizeCharacterId(rawId);
    int fallback = "cao_minh".equals(id) ? CAO_MINH_BASE_ATTACK
        : "syvial".equals(id) ? 32
        : 24;
    return firstPositive(source, fallback, "attackMax", "attack", "ATK");
  }

  private static int firstPositive(JSONObject source, int fallback, String... keys) {
    if (source != null) {
      for (String key : keys) {
        int value = source.optInt(key, 0);
        if (value > 0) return value;
      }
    }
    return fallback;
  }

  static int stableSeed(JSONObject state, String entityKey, JSONArray participants) throws Exception {
    JSONArray canonicalParticipants = new JSONArray();
    for (int i = 0; i < participants.length(); i++) {
      JSONObject participant = participants.optJSONObject(i);
      if (participant == null) continue;
      canonicalParticipants.put(new JSONObject()
          .put("id", participant.optString("id", ""))
          .put("name", participant.optString("name", ""))
          .put("sourceIndex", participant.optInt("sourceIndex", -1))
          .put("hp", participant.optInt("hp", 0))
          .put("maxHp", participant.optInt("maxHp", 1))
          .put("baseAttack", participant.optInt("baseAttack", CAO_MINH_BASE_ATTACK))
          .put("STR", participant.optInt("STR", CharacterProgressionCore.BASE_STAT))
          .put("DEF", participant.optInt("DEF", CharacterProgressionCore.BASE_STAT))
          .put("SKL", participant.optInt("SKL", CharacterProgressionCore.BASE_STAT))
          .put("VIT", participant.optInt("VIT", CharacterProgressionCore.BASE_STAT)));
    }

    String basis = entityKey + "|" + state.optInt("turn", 1) + "|"
        + state.optString(LevelCore.LEVEL_KEY, "0") + "|" + canonicalParticipants.toString();
    int hash = basis.hashCode();
    return hash == Integer.MIN_VALUE ? 1 : Math.abs(hash);
  }

  private static JSONObject newDiceState() throws Exception {
    JSONArray values = new JSONArray();
    JSONArray held = new JSONArray();
    for (int i = 0; i < DICE_COUNT; i++) {
      values.put(0);
      held.put(false);
    }
    return new JSONObject()
        .put("values", values)
        .put("held", held)
        .put("hasRolled", false)
        .put("rerollsUsed", 0)
        .put("maxRerolls", MAX_REROLLS)
        .put("finalized", false)
        .put("resolved", false)
        .put("hand", "");
  }

  private static JSONObject diceState(JSONObject combat) throws Exception {
    JSONObject dice = combat.optJSONObject("diceState");
    if (dice == null) throw new IllegalStateException("Combat dice state bị thiếu.");
    JSONArray values = dice.optJSONArray("values");
    JSONArray held = dice.optJSONArray("held");
    if (values == null || held == null || values.length() != DICE_COUNT || held.length() != DICE_COUNT) {
      throw new IllegalStateException("Combat dice state không hợp lệ.");
    }
    return dice;
  }

  private static void ensureInitialRoll(JSONObject combat) throws Exception {
    JSONObject dice = combat.optJSONObject("diceState");
    if (dice == null) {
      dice = newDiceState();
      combat.put("diceState", dice);
    }
    dice = diceState(combat);
    if (!dice.optBoolean("hasRolled", false)) {
      rollDice(combat, dice, false);
      dice.put("hasRolled", true).put("rerollsUsed", 0);
      updateHand(dice);
      autoHoldDuplicateGroups(dice);
    }
    if (dice.optString("hand", "").trim().isEmpty()) updateHand(dice);
  }

  private static void rollDice(JSONObject combat, JSONObject dice, boolean respectHeld)
      throws Exception {
    JSONArray values = dice.getJSONArray("values");
    JSONArray held = dice.getJSONArray("held");
    for (int i = 0; i < DICE_COUNT; i++) {
      if (respectHeld && held.optBoolean(i, false)) continue;
      int value;
      if (usesScopedCombatRng(combat)) {
        value = 1 + nextCombatInt(combat, 6);
      } else {
        int sequence = combat.optInt("rngSequence", 0);
        value = deterministicDie(combat.optInt("seed", 1), sequence, i);
        combat.put("rngSequence", sequence + 1);
      }
      values.put(i, value);
    }
  }

  private static void updateHand(JSONObject dice) throws Exception {
    JSONArray values = dice.getJSONArray("values");
    int[] hand = new int[DICE_COUNT];
    for (int i = 0; i < DICE_COUNT; i++) hand[i] = values.optInt(i, 0);
    dice.put("hand", classify(hand));
  }

  static void autoHoldDuplicateGroups(JSONObject dice) throws Exception {
    JSONArray values = dice.getJSONArray("values");
    JSONArray held = dice.getJSONArray("held");
    int[] counts = new int[7];
    for (int i = 0; i < DICE_COUNT; i++) {
      int value = values.optInt(i, 0);
      if (value >= 1 && value <= 6) counts[value]++;
    }
    for (int i = 0; i < DICE_COUNT; i++) {
      int value = values.optInt(i, 0);
      if (value >= 1 && value <= 6 && counts[value] >= 2) {
        held.put(i, true);
      }
    }
  }

  private static boolean allHeld(JSONArray held) {
    if (held == null || held.length() != DICE_COUNT) return false;
    for (int i = 0; i < DICE_COUNT; i++) if (!held.optBoolean(i, false)) return false;
    return true;
  }

  private static void finalizeHand(JSONObject dice) throws Exception {
    updateHand(dice);
    dice.put("finalized", true);
  }

  private static void prepareCurrentTurn(JSONObject combat) throws Exception {
    JSONArray participants = combat.getJSONArray("participants");
    int actorIndex = currentLivingIndex(combat, participants);
    if (actorIndex < 0) return;
    combat.put("actorIndex", actorIndex);
    JSONObject actor = participants.getJSONObject(actorIndex);
    String id = actor.optString("id", "");

    List<Skill> pool = SKILLS.get(id);
    if (pool != null && !pool.isEmpty()) {
      int index = usesScopedCombatRng(combat)
          ? nextCombatInt(combat, pool.size())
          : Math.floorMod(
              combat.optInt("seed", 1) + combat.optInt("round", 1) * 31 + actorIndex * 17,
              pool.size());
      combat.put("currentSkill", skillJson(pool.get(index)));
    } else {
      combat.remove("currentSkill");
    }

    Ultimate ultimate = ULTIMATES.get(id);
    if (ultimate != null) {
      combat.put("currentUltimate", new JSONObject()
          .put("name", ultimate.name)
          .put("hitCount", ultimate.hitCount)
          .put("bonusPercent", ultimate.bonusPercent)
          .put("damageFormula", "100% current DMG + " + ultimate.bonusPercent + "% Bonus DMG per hit"));
    } else {
      combat.remove("currentUltimate");
    }

    combat.put("currentActor", actor.optString("name", "Nhân vật"));
    combat.put("diceState", newDiceState());
    ensureInitialRoll(combat);
  }

  private static JSONObject skillJson(Skill skill) throws Exception {
    return new JSONObject()
        .put("name", skill.name)
        .put("description", skill.description)
        .put("damagePercent", skill.damagePercent)
        .put("effect", skill.effect)
        .put("effectTurns", skill.effectTurns)
        .put("effectValue", skill.effectValue);
  }

  private static int currentLivingIndex(JSONObject combat, JSONArray participants) {
    int current = combat.optInt("actorIndex", 0);
    if (current >= 0 && current < participants.length()) {
      JSONObject actor = participants.optJSONObject(current);
      if (actor != null && actor.optInt("hp", 0) > 0) return current;
    }
    for (int step = 1; step <= participants.length(); step++) {
      int candidate = Math.floorMod(current + step, participants.length());
      JSONObject actor = participants.optJSONObject(candidate);
      if (actor != null && actor.optInt("hp", 0) > 0) return candidate;
    }
    return -1;
  }

  private static int firstLivingIndex(JSONArray participants) {
    for (int i = 0; i < participants.length(); i++) {
      JSONObject actor = participants.optJSONObject(i);
      if (actor != null && actor.optInt("hp", 0) > 0) return i;
    }
    return 0;
  }

  private static void advanceActor(JSONObject combat) throws Exception {
    JSONArray participants = combat.getJSONArray("participants");
    int current = combat.optInt("actorIndex", 0);
    for (int step = 1; step <= participants.length(); step++) {
      int candidate = (current + step) % participants.length();
      JSONObject participant = participants.optJSONObject(candidate);
      if (participant == null || participant.optInt("hp", 0) <= 0) continue;
      if (candidate <= current) {
        combat.put("round", Math.max(1, combat.optInt("round", 1)) + 1);
      }
      combat.put("actorIndex", candidate);
      return;
    }
  }

  private static boolean hasLivingParticipant(JSONArray participants) {
    for (int i = 0; i < participants.length(); i++) {
      JSONObject participant = participants.optJSONObject(i);
      if (participant != null && participant.optInt("hp", 0) > 0) return true;
    }
    return false;
  }

  private static boolean isCaoMinhDown(JSONArray participants) {
    for (int i = 0; i < participants.length(); i++) {
      JSONObject participant = participants.optJSONObject(i);
      if (participant != null && "cao_minh".equals(participant.optString("id", ""))) {
        return participant.optInt("hp", 0) <= 0;
      }
    }
    return false;
  }

  private static int nextPercent(JSONObject combat, String salt) throws Exception {
    if (usesScopedCombatRng(combat)) return nextCombatInt(combat, 100);
    int sequence = combat.optInt("rngSequence", 0);
    int seed = combat.optInt("seed", 1);
    long mixed = (seed & 0xffffffffL) * 31L + (long)(sequence + 1) * 131L
        + (long)salt.hashCode() * 17L;
    int value = (int)Math.floorMod(mixed, 100L);
    combat.put("rngSequence", sequence + 1);
    return value;
  }

  private static boolean usesScopedCombatRng(JSONObject combat) {
    return combat != null && !combat.optString("rngTurnId", "").trim().isEmpty();
  }

  private static int nextCombatInt(JSONObject combat, int bound) throws Exception {
    String turnId = combat.optString("rngTurnId", "").trim();
    if (turnId.isEmpty()) throw new IllegalStateException("Scoped combat RNG metadata is missing");
    int used = Math.max(0, combat.optInt("rngSequence", 0));
    TurnRng rng = new TurnRng(
        turnId,
        Math.max(0, combat.optInt("rngPreTurnStateVersion", 0)),
        combat.optString("rngCanonVersion", EmergentTurnEngine.CANON_VERSION),
        combat.optString("rngSchemaVersion", EmergentTurnEngine.RNG_SCHEMA_VERSION));
    rng.resume(TurnRng.Scope.COMBAT, used);
    int value = rng.nextInt(TurnRng.Scope.COMBAT, bound);
    combat.put("rngSequence", rng.drawsUsed(TurnRng.Scope.COMBAT));
    return value;
  }

  static JSONObject setTargetEntity(JSONObject state, int entityIndex) throws Exception {
    if (!isActive(state)) throw new IllegalStateException("Không có trận chiến đang hoạt động.");
    JSONObject combat = state.getJSONObject("combat");
    JSONArray entities = normalizeCombatEntities(combat);
    if (entityIndex < 0 || entityIndex >= entities.length()) {
      throw new IllegalArgumentException("Entity target index không hợp lệ.");
    }
    JSONObject target = entities.optJSONObject(entityIndex);
    if (!entityAlive(target)) throw new IllegalStateException("Entity target đã bị tiêu diệt.");
    combat.put("targetEntityIndex", entityIndex);
    syncActiveEntityAlias(combat);
    return state;
  }

  private static JSONArray normalizeCombatEntities(JSONObject combat) throws Exception {
    JSONArray entities = combat.optJSONArray("entities");
    JSONObject legacy = combat.optJSONObject("entity");
    if (entities == null || entities.length() == 0) {
      entities = new JSONArray();
      if (legacy != null) entities.put(legacy);
      combat.put("entities", entities);
    }
    for (int i = 0; i < entities.length(); i++) {
      JSONObject entity = entities.optJSONObject(i);
      if (entity == null) continue;
      boolean alive = entity.has("alive")
          ? entity.optBoolean("alive", entity.optInt("hp", 0) > 0)
          : entity.optInt("hp", 0) > 0;
      if (entity.optInt("hp", 0) <= 0) alive = false;
      entity.put("alive", alive).put("status", alive ? "alive" : "dead");
    }
    if (!(combat.opt("entityDeaths") instanceof JSONArray)) combat.put("entityDeaths", new JSONArray());
    if (!(combat.opt("entityDeathsThisTurn") instanceof JSONArray)) {
      combat.put("entityDeathsThisTurn", new JSONArray());
    }
    int active = combat.optInt("activeEntityIndex", 0);
    if (active < 0 || active >= entities.length()) active = 0;
    if (combat.optBoolean("active", false) && entities.length() > 0
        && !entityAlive(entities.optJSONObject(active))) {
      int living = nextLivingEntityIndex(entities, active, true);
      if (living >= 0) active = living;
    }
    combat.put("activeEntityIndex", active);
    int target = combat.optInt("targetEntityIndex", active);
    if (target < 0 || target >= entities.length()
        || (combat.optBoolean("active", false) && !entityAlive(entities.optJSONObject(target)))) {
      target = active;
    }
    combat.put("targetEntityIndex", target);
    syncActiveEntityAlias(combat);
    return entities;
  }

  private static void syncActiveEntityAlias(JSONObject combat) throws Exception {
    JSONArray entities = combat.optJSONArray("entities");
    if (entities == null || entities.length() == 0) return;
    int index = combat.optInt("activeEntityIndex", 0);
    if (index < 0 || index >= entities.length()) index = 0;
    JSONObject entity = entities.optJSONObject(index);
    if (entity != null) combat.put("entity", entity);
  }

  private static boolean entityAlive(JSONObject entity) {
    return entity != null && entity.optBoolean("alive", entity.optInt("hp", 0) > 0)
        && entity.optInt("hp", 0) > 0;
  }

  private static boolean hasLivingEntity(JSONArray entities) {
    return firstLivingEntityIndex(entities) >= 0;
  }

  private static int firstLivingEntityIndex(JSONArray entities) {
    if (entities == null) return -1;
    for (int i = 0; i < entities.length(); i++) {
      if (entityAlive(entities.optJSONObject(i))) return i;
    }
    return -1;
  }

  private static int nextLivingEntityIndex(JSONArray entities, int from, boolean includeFrom) {
    if (entities == null || entities.length() == 0) return -1;
    int start = includeFrom ? 0 : 1;
    for (int step = start; step < entities.length() + start; step++) {
      int index = Math.floorMod(from + step, entities.length());
      if (entityAlive(entities.optJSONObject(index))) return index;
    }
    return -1;
  }

  private static int livingTargetIndex(JSONObject combat, JSONArray entities) throws Exception {
    int target = combat.optInt("targetEntityIndex", combat.optInt("activeEntityIndex", 0));
    if (target >= 0 && target < entities.length() && entityAlive(entities.optJSONObject(target))) {
      return target;
    }
    int active = combat.optInt("activeEntityIndex", 0);
    int next = nextLivingEntityIndex(entities, active, true);
    if (next >= 0) {
      combat.put("targetEntityIndex", next).put("activeEntityIndex", next);
      syncActiveEntityAlias(combat);
    }
    return next;
  }

  private static String entityKeySignature(JSONArray entities) {
    List<String> keys = new ArrayList<>();
    if (entities != null) for (int i = 0; i < entities.length(); i++) {
      JSONObject entity = entities.optJSONObject(i);
      String key = entity == null ? "" : entity.optString("key", "").trim();
      if (!key.isEmpty()) keys.add(key);
    }
    return String.join("+", keys);
  }

  private static void resolveEntityDeath(JSONObject state, JSONObject combat, JSONArray entities, int index)
      throws Exception {
    JSONObject entity = entities.optJSONObject(index);
    if (entity == null || entity.optBoolean("deathResolved", false)) return;
    entity.put("hp", 0).put("alive", false).put("status", "dead");

    String entityKey = entity.optString("key", "entity");
    String droppedItem = "";
    if (!entity.optBoolean("lootResolved", false)) {
      int rate = ItemCore.entityDropRatePercent(entityKey);
      int roll = nextPercent(combat, "loot-drop:" + entityKey + ":" + index);
      entity.put("entityLootRatePercent", rate).put("entityLootRoll", roll);
      if (ItemCore.shouldDropEntityLoot(roll, rate)) {
        droppedItem = ItemCore.grantEntityLootItem(
            state, nextPercent(combat, "loot-item:" + entityKey + ":" + index));
        entity.put("droppedItem", droppedItem);
        JSONArray drops = combat.optJSONArray("droppedItems");
        if (drops == null) drops = new JSONArray();
        drops.put(new JSONObject().put("entityKey", entityKey).put("item", droppedItem));
        combat.put("droppedItems", drops);
        if (entities.length() == 1) combat.put("droppedItem", droppedItem);
      }
      entity.put("lootResolved", true);
    }

    int reward = 0;
    if (!entity.optBoolean("coreDropResolved", false)) {
      int stageIndex = Math.max(0, entity.optInt(
          "stageIndex", combat.optInt("stageIndex", LevelCore.stageIndex(state))));
      CharacterProgressionCore progression = new CharacterProgressionCore();
      TreasureRewardPolicy treasure = TREASURE_REWARDS.get(entityKey);
      if (treasure == null) {
        int baseReward = "copx".equals(entityKey) ? 5
            : CharacterProgressionCore.ENTITY_VICTORY_BASE_CORE;
        reward = CharacterProgressionCore.scaledCoreReward(baseReward, stageIndex);
        progression.grantCore(state, reward);
        entity.remove("coreDropRewardType");
      } else {
        int firstKillReward = treasure.scaleWithStage
            ? EntityStatCore.scale(treasure.firstKillBaseCore, stageIndex)
            : treasure.firstKillBaseCore;
        int repeatReward = treasure.scaleWithStage
            ? EntityStatCore.scale(treasure.repeatBaseCore, stageIndex)
            : treasure.repeatBaseCore;
        reward = progression.rewardTreasureEntityVictory(
            state, entityKey, stageIndex, firstKillReward, repeatReward);
        entity.put("coreDropRewardType", "treasure");
      }
      entity.put("coreDropRatePercent", 100)
          .put("coreDropReward", reward)
          .put("coreDropResolved", true);
      int total = Math.max(0, combat.optInt("coreDropReward", 0)) + reward;
      combat.put("coreDropReward", total).put("coreDropRatePercent", 100);
      if (entities.length() == 1 && treasure != null) combat.put("coreDropRewardType", "treasure");
    }

    entity.put("deathResolved", true);
    JSONObject death = new JSONObject()
        .put("entityIndex", index)
        .put("key", entityKey)
        .put("name", entity.optString("name", "Entity"))
        .put("round", Math.max(1, combat.optInt("round", 1)))
        .put("coreReward", reward);
    if (!droppedItem.isEmpty()) death.put("droppedItem", droppedItem);
    combat.getJSONArray("entityDeaths").put(new JSONObject(death.toString()));
    combat.getJSONArray("entityDeathsThisTurn").put(death);
    addFeedback(combat, "actor", "entity", "death",
        entity.optString("name", "Entity") + " bị tiêu diệt.", false);
    if (!hasLivingEntity(entities)) {
      combat.put("lootResolved", true).put("coreDropResolved", true);
    }
  }

  private static void appendDeathLines(JSONObject state, JSONObject combat) throws Exception {
    JSONArray deaths = combat.optJSONArray("entityDeathsThisTurn");
    if (deaths == null) return;
    for (int i = 0; i < deaths.length(); i++) {
      JSONObject death = deaths.optJSONObject(i);
      if (death == null) continue;
      appendBattleLine(state, combat, death.optString("name", "Entity") + " bị tiêu diệt.");
    }
  }

  private static void finishVictory(JSONObject state, JSONObject combat)
      throws Exception {
    JSONArray entities = normalizeCombatEntities(combat);
    if (hasLivingEntity(entities)) return;
    combat.put("active", false)
        .put("outcome", "victory")
        .put("lootResolved", true)
        .put("coreDropResolved", true);
    clearEncounterFlag(state);
  }

  private static void finishDefeat(JSONObject state, JSONObject combat) throws Exception {
    combat.put("active", false).put("outcome", "defeat");
    if (!combat.optBoolean("deathRecoveryApplied", false)) {
      String targetLocation = combat.optString(
          "deathRestartAnchorLocation", state.optString("location", "")).trim();
      String targetLevelKey = combat.optString("deathRestartLevelKey",
          state.optString(LevelCore.LEVEL_KEY, String.valueOf(state.optInt("currentLevel", 0)))).trim();

      // The death sentence is the first player-visible consequence. Penalty/recovery remains Core-owned
      // and is guarded by deathRecoveryApplied so normalize/load cannot apply it twice.
      JSONArray log = state.optJSONArray("log");
      if (log == null) log = new JSONArray();
      log.put(new JSONObject().put("role", "gm")
          .put("text", "Backrooms nuốt chửng lấy bạn khi bạn ngã xuống."));
      state.put("log", log);

      CharacterProgressionCore progression = new CharacterProgressionCore();
      progression.applyCaoMinhDeathPenalty(state);
      state.put(LevelCore.LEVEL_KEY, targetLevelKey);
      LevelCore.returnToCurrentLevelStart(state);
      combat.put("deathRestartAnchorLocation", targetLocation)
          .put("deathRestartLevelKey", targetLevelKey)
          .put("deathRestartPending", true)
          .put("deathRecoveryApplied", true)
          .put("playerRespawned", true);
    }
    clearEncounterFlag(state);
  }

  static void normalizeTerminalEncounter(JSONObject state) throws Exception {
    if (state == null) return;
    JSONObject combat = state.optJSONObject("combat");
    if (combat == null) return;
    normalizeCombatEntities(combat);
    if (combat.optBoolean("active", false)) {
      ensureInitialRoll(combat);
      return;
    }
    String outcome = combat.optString("outcome", "");
    if ("defeat".equals(outcome) && !combat.optBoolean("deathRecoveryApplied", false)) {
      finishDefeat(state, combat);
      return;
    }
    if ("victory".equals(outcome) || "defeat".equals(outcome)) clearEncounterFlag(state);
  }

  private static void clearEncounterFlag(JSONObject state) throws Exception {
    JSONObject flags = state.optJSONObject("flags");
    if (flags != null) {
      flags.put("entityEncounterKey", "");
      flags.put("entityEncounterKeys", new JSONArray());
    }
  }

  private static void syncParticipants(JSONObject state, JSONArray participants) throws Exception {
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(state);
    JSONArray party = state.optJSONArray("party");

    for (int i = 0; i < participants.length(); i++) {
      JSONObject participant = participants.optJSONObject(i);
      if (participant == null) continue;
      String id = participant.optString("id", "");
      progression.setCurrentHp(state, id, participant.optInt("hp", 0));
      if (!"cao_minh".equals(id) && participant.optInt("hp", 0) <= 0) {
        progression.markCompanionDown(state, id);
      }
      JSONObject profile = progression.profile(state, id);
      int hp = profile.getInt("currentHp");
      int maxHp = profile.getInt("maxHp");
      int sourceIndex = participant.optInt("sourceIndex", -1);
      if (sourceIndex < 0) {
        JSONObject player = state.optJSONObject("player");
        if (player != null) {
          player.put("hp", hp).put("maxHp", maxHp);
          if (hp <= 0) player.put("condition", "Bị hạ");
        }
      } else if (party != null && sourceIndex < party.length()) {
        JSONObject member = party.optJSONObject(sourceIndex);
        if (member != null) member.put("hp", hp).put("maxHp", maxHp);
      }
    }
  }

  private static void addFeedback(JSONObject combat, String phase, String target, String kind,
                                  String text, boolean flash) throws Exception {
    JSONArray events = combat.optJSONArray("feedbackEvents");
    if (events == null) events = new JSONArray();
    JSONObject event = new JSONObject()
        .put("phase", phase)
        .put("target", target)
        .put("kind", kind)
        .put("text", text == null ? "" : text)
        .put("flash", flash)
        .put("actorIndex", combat.optInt("resolvedActorIndex", combat.optInt("actorIndex", 0)));
    int entityIndex = combat.has("feedbackEntityIndex")
        ? combat.optInt("feedbackEntityIndex", combat.optInt("activeEntityIndex", 0))
        : combat.optInt("activeEntityIndex", 0);
    JSONArray entities = combat.optJSONArray("entities");
    JSONObject entity = entities != null && entityIndex >= 0 && entityIndex < entities.length()
        ? entities.optJSONObject(entityIndex) : combat.optJSONObject("entity");
    if (entity != null) {
      event.put("entityIndex", entityIndex)
          .put("entityKey", entity.optString("key", ""));
    }
    events.put(event);
    combat.put("feedbackEvents", events);
  }

  private static void appendBattleLine(JSONObject state, JSONObject combat, String text)
      throws Exception {
    JSONArray log = state.optJSONArray("log");
    if (log == null || log.length() == 0 || text == null || text.trim().isEmpty()) return;
    int index = Math.max(0, Math.min(combat.optInt("logIndex", log.length() - 1), log.length() - 1));
    JSONObject entry = log.optJSONObject(index);
    if (entry == null) return;
    JSONArray battleLog = entry.optJSONArray("battleLog");
    if (battleLog == null) battleLog = new JSONArray();
    battleLog.put(new JSONObject()
        .put("text", text)
        .put("highlights", GmChoiceContract.semanticHighlights(text, state)));
    entry.put("battleLog", battleLog);
  }
}
