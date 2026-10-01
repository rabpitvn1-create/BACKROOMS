package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

/** One initial storytelling request, at most one hard-validation retry, then safe local fallback. */
public final class NarrationProviderPolicy {
  @FunctionalInterface public interface Provider { JSONObject generate(String rejection) throws Exception; }
  @FunctionalInterface public interface Validator { String validate(JSONObject generated); }

  private NarrationProviderPolicy() {}

  public static JSONObject present(JSONArray safeEvents, Provider provider, Validator validator)
      throws Exception {
    if (OfflinePresenter.isOffline(safeEvents)) {
      return OfflinePresenter.present(safeEvents,
          () -> { throw new IllegalStateException("Offline presentation cannot dispatch provider"); });
    }
    try {
      JSONObject generated = provider.generate("");
      String rejection = validator.validate(generated);
      if (rejection.isEmpty()) return generated;
      generated = provider.generate(rejection);
      if (validator.validate(generated).isEmpty()) return generated;
    } catch (Exception error) {
      // ponytail: transport/content failures fall back locally; no hidden key rotation or retry loop.
    }
    return OfflinePresenter.fallback(safeEvents);
  }
}
