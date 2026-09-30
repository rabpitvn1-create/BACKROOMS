package com.rabpit.backroom.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GameCoreRules {
  private static final Pattern VAN_TANG_WITHDRAW = Pattern.compile(
      "(?:lấy|rút|triệu hồi).*(?:ra khỏi|khỏi|từ).*(?:vạn\\s+tàng|giới\\s+chỉ|nhẫn|kho)",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern DIRECT_PICKUP = Pattern.compile(
      "(?:^|\\s)(?:nhặt|lượm|cầm\\s+lên|lấy(?:\\s+lên)?|thu\\s+hồi|tịch\\s+thu|nhận(?:\\s+lấy)?|pick\\s+up|take|receive)(?:\\s|$)",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern INVENTORY_ASSERTION = Pattern.compile(
      "(?:thêm|bỏ|đưa).{0,80}(?:vào|trong)\\s+(?:inventory|kho đồ|túi đồ)",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern RESTORE = Pattern.compile(
      "(?:hoàn nguyên|restore|khôi phục vật)",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern INVENTORY_QUERY = Pattern.compile(
      "(?:inventory|kho đồ|túi đồ).*(?:gì|xem|kiểm tra|hiện tại)|(?:xem|kiểm tra).*(?:inventory|kho đồ|túi đồ)",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern PARTY_QUERY = Pattern.compile(
      "(?:party|đội hình|nhóm).*(?:ai|xem|kiểm tra|hiện tại)|(?:xem|kiểm tra).*(?:party|đội hình)",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern LONG_REST = Pattern.compile("(?:ngủ|sleep)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern REST = Pattern.compile("(?:nghỉ|rest)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern MOVE = Pattern.compile("(?:đi|chạy|di chuyển|leo|bò|walk|run|move)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern ROUTE_EXPLORATION = Pattern.compile(
      "(?:^|\\s|[.,;:!?])(?:"
          + "đi|chạy|di\\s+chuyển|tiến|rẽ|leo|bò|"
          + "khám\\s+phá|khảo\\s+sát|thăm\\s+dò|dò\\s+đường|"
          + "tìm\\s+(?:đường|lối(?:\\s+ra)?)|"
          + "theo\\s+(?:hướng|lối|hành\\s+lang|dấu)|men\\s+theo|"
          + "tiếp\\s+tục(?:\\s+đi)?|walk|run|move|explore|follow"
          + ")(?:\\s|$|[.,;:!?])",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern LEVEL_LOCATION = Pattern.compile("(?i)(?:^|\\b)level\\s*([0-6])(?:\\b|\\s|/|—|-)");

  private GameCoreRules() {}

  static boolean isDirectPlayerPickupAction(String action) {
    String text = action == null ? "" : action.trim();
    if (text.isEmpty() || VAN_TANG_WITHDRAW.matcher(text).find()) return false;
    return DIRECT_PICKUP.matcher(text).find() || INVENTORY_ASSERTION.matcher(text).find();
  }

  static boolean isRestoreAction(String action) {
    return RESTORE.matcher(action == null ? "" : action).find();
  }

  static boolean inventoryMutationLocked(String action) {
    return isDirectPlayerPickupAction(action) || isRestoreAction(action);
  }

  static boolean isInventoryQuery(String action) {
    return INVENTORY_QUERY.matcher(action == null ? "" : action).find();
  }

  static boolean isPartyQuery(String action) {
    return PARTY_QUERY.matcher(action == null ? "" : action).find();
  }

  static boolean isRouteExplorationAction(String action) {
    String text = action == null ? "" : action.trim();
    return !text.isEmpty() && ROUTE_EXPLORATION.matcher(text).find();
  }

  static int estimateMinutes(String action) {
    String text = action == null ? "" : action.trim();
    if (text.isEmpty()) return 0;
    if (LONG_REST.matcher(text).find()) return 30;
    if (REST.matcher(text).find()) return 10;
    if (MOVE.matcher(text).find()) return 5;
    return Math.min(5, Math.max(1, text.length() / 80 + 1));
  }

  static int levelFromLocation(String location) {
    Matcher matcher = LEVEL_LOCATION.matcher(location == null ? "" : location);
    if (!matcher.find()) return -1;
    try {
      return Integer.parseInt(matcher.group(1));
    } catch (Exception ignored) {
      return -1;
    }
  }

  static boolean levelTransitionAllowed(int from, int to) {
    if (from < 0 || from > 6 || to < 0 || to > 6) return false;
    if (from == to) return true;
    // Level 0 <-> Level 1 is no longer a direct edge. LevelCore owns the mandatory
    // Level 0 -> 0.1 -> 0.2 -> 0.5 -> 0.7 -> Manila -> Torment -> Red Rooms -> Level 1 chain.
    return edge(from, to, 1, 2)
        || edge(from, to, 2, 3)
        || edge(from, to, 3, 4)
        || edge(from, to, 3, 6)
        || edge(from, to, 4, 5)
        || edge(from, to, 5, 6);
  }

  private static boolean edge(int from, int to, int a, int b) {
    return (from == a && to == b) || (from == b && to == a);
  }

  static String stableItemId(String name) {
    String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}]+", "-")
        .replaceAll("^-+|-+$", "");
    if (!normalized.isEmpty()) return normalized;
    return "item-" + Integer.toUnsignedString(name == null ? 0 : name.hashCode());
  }
}
