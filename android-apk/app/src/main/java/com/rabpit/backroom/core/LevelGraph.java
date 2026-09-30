package com.rabpit.backroom.core;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class LevelGraph {
  static final String ASSET = "level_graph.json";

  static final class Node {
    final String key;
    final int parentLevel;
    final int stageIndex;
    final String displayName;
    final String defaultLocation;

    Node(JSONObject raw) {
      key = raw.optString("key", "").trim();
      parentLevel = raw.optInt("parentLevel", 0);
      stageIndex = raw.optInt("stageIndex", -1);
      displayName = raw.optString("displayName", key).trim();
      defaultLocation = raw.optString("defaultLocation", displayName).trim();
    }
  }

  private final Map<String, Node> nodes = new LinkedHashMap<>();
  private final Map<String, LinkedHashSet<String>> outgoing = new LinkedHashMap<>();

  static LevelGraph load(Context context) {
    if (context == null) return new LevelGraph();
    try {
      return fromText(readAsset(context, ASSET));
    } catch (Exception ignored) {
      return new LevelGraph();
    }
  }

  static LevelGraph fromText(String raw) throws Exception {
    LevelGraph graph = new LevelGraph();
    JSONObject root = new JSONObject(raw == null ? "{}" : raw);
    if (root.optInt("schemaVersion", 0) != 1) {
      throw new IllegalArgumentException("Unsupported Level graph schema");
    }
    JSONArray nodeList = root.optJSONArray("nodes");
    JSONArray edgeList = root.optJSONArray("edges");
    if (nodeList == null || nodeList.length() == 0 || edgeList == null) {
      throw new IllegalArgumentException("Level graph requires nodes and edges");
    }
    Set<Integer> stages = new LinkedHashSet<>();
    for (int i = 0; i < nodeList.length(); i++) {
      JSONObject rawNode = nodeList.optJSONObject(i);
      if (rawNode == null) throw new IllegalArgumentException("Invalid Level node");
      Node node = new Node(rawNode);
      if (node.key.isEmpty() || node.stageIndex < 0 || node.parentLevel < 0
          || graph.nodes.containsKey(node.key) || stages.contains(node.stageIndex)) {
        throw new IllegalArgumentException("Duplicate/invalid Level node: " + node.key);
      }
      graph.nodes.put(node.key, node);
      graph.outgoing.put(node.key, new LinkedHashSet<>());
      stages.add(node.stageIndex);
    }
    for (int i = 0; i < edgeList.length(); i++) {
      JSONObject edge = edgeList.optJSONObject(i);
      String from = edge == null ? "" : edge.optString("from", "").trim();
      String to = edge == null ? "" : edge.optString("to", "").trim();
      if (!graph.nodes.containsKey(from) || !graph.nodes.containsKey(to) || from.equals(to)) {
        throw new IllegalArgumentException("Invalid Level edge: " + from + " -> " + to);
      }
      graph.outgoing.get(from).add(to);
    }
    return graph;
  }

  boolean available() {
    return !nodes.isEmpty();
  }

  boolean contains(String key) {
    return nodes.containsKey(key == null ? "" : key.trim());
  }

  Node node(String key) {
    return nodes.get(key == null ? "" : key.trim());
  }

  boolean allows(String from, String to) {
    if (from == null || to == null) return false;
    if (from.equals(to)) return contains(from);
    LinkedHashSet<String> targets = outgoing.get(from);
    return targets != null && targets.contains(to);
  }

  List<String> outgoing(String from) {
    LinkedHashSet<String> targets = outgoing.get(from);
    if (targets == null || targets.isEmpty()) return Collections.emptyList();
    return Collections.unmodifiableList(new ArrayList<>(targets));
  }

  String singleNext(String from) {
    List<String> targets = outgoing(from);
    return targets.size() == 1 ? targets.get(0) : null;
  }

  int parentLevel(String key, int fallback) {
    Node node = node(key);
    return node == null ? fallback : node.parentLevel;
  }

  int stageIndex(String key, int fallback) {
    Node node = node(key);
    return node == null ? fallback : node.stageIndex;
  }

  String displayName(String key, String fallback) {
    Node node = node(key);
    return node == null || node.displayName.isEmpty() ? fallback : node.displayName;
  }

  String defaultLocation(String key, String fallback) {
    Node node = node(key);
    return node == null || node.defaultLocation.isEmpty() ? fallback : node.defaultLocation;
  }

  private static String readAsset(Context context, String path) throws Exception {
    StringBuilder text = new StringBuilder();
    try (InputStream input = context.getAssets().open(path);
         BufferedReader buffered = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
      String line;
      while ((line = buffered.readLine()) != null) text.append(line).append('\n');
    }
    return text.toString();
  }
}
