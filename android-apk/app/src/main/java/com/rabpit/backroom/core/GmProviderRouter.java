package com.rabpit.backroom.core;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Executes one GM narration provider before trying its fallback on failure. */
public final class GmProviderRouter {
  private GmProviderRouter() {}

  public static String execute(Callable<String> primary, Callable<String> fallback,
      Consumer<Exception> onFallback) throws Exception {
    try {
      return primary.call();
    } catch (Exception primaryError) {
      onFallback.accept(primaryError);
      try {
        return fallback.call();
      } catch (Exception fallbackError) {
        if (fallbackError != primaryError) fallbackError.addSuppressed(primaryError);
        throw fallbackError;
      }
    }
  }
}
