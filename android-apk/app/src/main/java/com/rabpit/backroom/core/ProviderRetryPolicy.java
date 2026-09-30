package com.rabpit.backroom.core;

import java.util.Locale;

/** Pure retry classification so provider/content failures cannot accidentally trigger retry storms. */
public final class ProviderRetryPolicy {
  private ProviderRetryPolicy() {}

  public static boolean isContentOrJsonError(int httpStatus, String message) {
    if (httpStatus != 0) return false;
    String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
    return text.contains("json")
        || text.contains("không trả nội dung")
        || text.contains("không trả dữ liệu")
        || text.contains("phản hồi rỗng");
  }

  public static boolean shouldRetrySameProvider(int httpStatus, String message) {
    if (isContentOrJsonError(httpStatus, message)) return false;
    if (httpStatus == 0) return true;
    return httpStatus == 408 || httpStatus == 429
        || httpStatus == 500 || httpStatus == 502 || httpStatus == 503 || httpStatus == 504;
  }

  public static boolean shouldRotateGeminiKey(int httpStatus, String message) {
    if (isContentOrJsonError(httpStatus, message)) return false;
    if (httpStatus == 0) return true;
    return httpStatus == 401 || httpStatus == 403 || httpStatus == 408 || httpStatus == 429
        || httpStatus == 500 || httpStatus == 502 || httpStatus == 503 || httpStatus == 504;
  }
}
