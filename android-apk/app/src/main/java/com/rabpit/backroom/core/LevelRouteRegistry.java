package com.rabpit.backroom.core;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authoritative environment-node topology for Levels 0–6.
 * A node has a stable id and a parent main-level number: hazard/encounter scaling continues
 * to use the parent number, while location, exits, continuity and saves use the exact id.
 */
public final class LevelRouteRegistry {
  public static final class Node {
    public final String id;
    public final int parent;
    public final String name;
    public final String kind;

    private Node(String id, int parent, String name, String kind) {
      this.id = id;
      this.parent = parent;
      this.name = name;
      this.kind = kind;
    }

    public JSONObject asJson() throws Exception {
      return new JSONObject().put("id", id).put("number", parent).put("name", name).put("kind", kind);
    }

    public String title() {
      return "Level " + id + " – " + name;
    }

    public String location() {
      return (kind.equals("AREA") || kind.equals("SPECIAL") ? name : "Level " + id + " / " + name);
    }
  }

  private static final Map<String, Node> NODES = new LinkedHashMap<>();
  private static final Map<String, Set<String>> EXITS = new LinkedHashMap<>();
  private static final Pattern NUMBERED = Pattern.compile("^\\s*level\\s+([0-6](?:\\.[0-9]+)?)\\b", Pattern.CASE_INSENSITIVE);

  static {
    add("0", 0, "The Lobby", "MAIN");
    add("0.1", 0, "Zenith Station", "SUBLEVEL");
    add("0.2", 0, "Remodeled Mess", "SUBLEVEL");
    add("0.5", 0, "Aquaclaustrophobic Infirmary", "SUBLEVEL");
    add("0.7", 0, "The Reminiscence District", "SUBLEVEL");
    add("manila_room", 0, "Manila Room", "SPECIAL");
    add("the_torment", 0, "The Torment", "SPECIAL");
    add("red_rooms", 0, "Red Rooms", "SPECIAL");
    add("dullness", 0, "Dullness", "AREA");
    add("epsilon", 0, "Epsilon", "AREA");
    add("ls_2", 0, "LS-2", "AREA");

    add("1", 1, "Parking Zone", "MAIN");
    add("1.2", 1, "Level 1.2", "SUBLEVEL");
    add("1.5", 1, "Level 1.5", "SUBLEVEL");
    add("traders_vault", 1, "Trader's Vault", "AREA");
    add("base_alpha", 1, "Base Alpha", "AREA");

    add("2", 2, "Pipe Dreams", "MAIN");
    add("office_space_el3a", 2, "Office Space EL3A", "AREA");

    add("3", 3, "The Electrical Station", "MAIN");
    add("4", 4, "The Abandoned Office", "MAIN");
    add("office_market", 4, "The Office Market", "AREA");
    add("5", 5, "Terror Hotel", "MAIN");
    add("5.1", 5, "Level 5.1", "SUBLEVEL");
    add("6", 6, "Lights Out", "MAIN");
    add("6.1", 6, "Level 6.1", "SUBLEVEL");

    // Locked one-way Level-0 route from the project world bible; never allow a skip.
    forward("0", "0.1");
    forward("0.1", "0.2");
    forward("0.2", "0.5");
    forward("0.5", "0.7");
    forward("0.7", "manila_room");
    forward("manila_room", "the_torment");
    forward("the_torment", "red_rooms");
    forward("red_rooms", "1");

    // Project main-level topology (Level 7 is deliberately excluded).
    both("1", "2");
    both("2", "3");
    both("3", "4");
    both("3", "6");
    both("4", "5");
    both("5", "6");

    // Image-referenced optional environments, attached only to their parent level.
    both("0", "dullness");
    both("0", "epsilon");
    both("0", "ls_2");
    both("1", "1.2");
    both("1", "1.5");
    both("1", "traders_vault");
    both("1", "base_alpha");
    both("2", "office_space_el3a");
    both("4", "office_market");
    both("5", "5.1");
    both("6", "6.1");
  }

  private LevelRouteRegistry() {}

  private static void add(String id, int parent, String name, String kind) {
    NODES.put(id, new Node(id, parent, name, kind));
    EXITS.put(id, new LinkedHashSet<>());
  }

  private static void forward(String from, String to) {
    EXITS.get(from).add(to);
  }

  private static void both(String first, String second) {
    forward(first, second);
    forward(second, first);
  }

  public static Node node(String id) { return NODES.get(id); }

  public static int size() { return NODES.size(); }

  public static String currentId(JSONObject state) {
    JSONObject level = state == null ? null : state.optJSONObject("level");
    if (level != null) {
      String id = level.optString("id", "").trim();
      if (NODES.containsKey(id)) return id;
      String number = valueAsId(level.opt("number"));
      if (NODES.containsKey(number)) return number;
    }
    return "0";
  }

  /** Do not coerce decimals through optInt: Level 1.2 is not Level 1. */
  public static Node requested(JSONObject level) {
    if (level == null) return null;
    String id = level.optString("id", "").trim();
    if (!id.isEmpty()) return NODES.get(id);
    return NODES.get(valueAsId(level.opt("number")));
  }

  private static String valueAsId(Object number) {
    if (number == null || number == JSONObject.NULL) return "";
    return String.valueOf(number).trim().replaceFirst("\\.0+$", "");
  }

  /** Only a leading, explicit level or named area can identify an environment node. */
  public static Node fromLocation(String location) {
    if (location == null || location.trim().isEmpty()) return null;
    Matcher match = NUMBERED.matcher(location);
    if (match.find()) return NODES.get(match.group(1));
    String lower = location.trim().toLowerCase(Locale.ROOT);
    for (Node n : NODES.values()) {
      if (!(n.kind.equals("AREA") || n.kind.equals("SPECIAL"))) continue;
      String title = n.name.toLowerCase(Locale.ROOT);
      if (lower.equals(title) || lower.startsWith(title + " /") ||
          lower.startsWith(title + " –") || lower.startsWith(title + " -") ||
          lower.startsWith(title + ":")) return n;
    }
    return null;
  }

  public static boolean canMove(String from, String to) {
    return EXITS.containsKey(from) && EXITS.get(from).contains(to);
  }

  public static List<String> exits(String id) {
    Set<String> outgoing = EXITS.get(id);
    return outgoing == null ? Collections.emptyList() :
      Collections.unmodifiableList(new ArrayList<>(outgoing));
  }

  public static String gmRoutingContext(String currentId) {
    Node current = node(currentId);
    if (current == null) return "";
    StringBuilder result = new StringBuilder("ENVIRONMENT ROUTING HARD LOCK: Current node=")
        .append(current.id).append(" (").append(current.name).append("), parent Level ")
        .append(current.parent).append(". Allowed immediate destinations: ");
    List<String> destinations = exits(current.id);
    if (destinations.isEmpty()) result.append("none");
    for (int i = 0; i < destinations.size(); i++) {
      Node next = node(destinations.get(i));
      if (i > 0) result.append("; ");
      result.append(next.id).append(" (").append(next.name).append(")");
    }
    result.append(". State must use set_level{level:{id:\"EXACT_NODE_ID\"}} on actual movement, ")
      .append("not a freeform change to location alone. Never skip mandatory Level-0 nodes. ")
      .append("An available exit does not move the player: only the player's deliberate traversal does. ")
      .append("All moves require authoritative hidden Exit readiness. ")
      .append("Named optional areas are local detours, not shortcuts across main levels. ")
      .append("Use parent Level for hazards/entity rolls. No Level 7.");
    return result.toString();
  }
}
