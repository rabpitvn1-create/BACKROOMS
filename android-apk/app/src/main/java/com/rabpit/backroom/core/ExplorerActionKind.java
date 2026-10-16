package com.rabpit.backroom.core;

import java.util.regex.Pattern;

/** Routes free-form chat exploration to the existing EXPLORE gameplay pipeline. */
public final class ExplorerActionKind {
  private ExplorerActionKind() {}

  // The chat composer no longer has an Explore button. Classify explicit
  // traversal/exploration only; dialogue, item use, meta and combat remain EXECUTE.
  private static final Pattern EXPLORATION = Pattern.compile(
      "(?iu)(?:^|[^\\p{L}\\p{N}])(?:khám\\s+phá|khảo\\s+sát|thăm\\s+dò|thám\\s+hiểm"
    + "|(?:đi|bước|tiến|chạy)\\s+(?:tiếp|thẳng|vào|qua|đến|tới|dọc|theo|về|lên|xuống|ra|phía|sâu)"
    + "|tìm\\s+(?:đường|lối\\s+thoát|cửa\\s+ra)"
    + "|(?:men|lần)\\s+theo|quan\\s+sát\\s+(?:xung\\s+quanh|khu\\s+vực|hành\\s+lang)"
    + ")(?=$|[^\\p{L}\\p{N}])"
  );

  public static String resolve(String submittedKind, String action) {
    if (!"EXECUTE".equalsIgnoreCase(submittedKind) || action == null) return submittedKind;
    return EXPLORATION.matcher(action).find() ? "EXPLORE" : "EXECUTE";
  }
}
