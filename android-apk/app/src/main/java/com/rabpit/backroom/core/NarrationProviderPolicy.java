package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

/** At most one writer content attempt per scene; any rejection falls back locally. */
public final class NarrationProviderPolicy {
  @FunctionalInterface public interface Provider { JSONObject generate(String rejection) throws Exception; }
  @FunctionalInterface public interface Validator { String validate(JSONObject generated); }

  private NarrationProviderPolicy() {}

  public static JSONObject present(JSONArray safeEvents, Provider provider, Validator validator)
      throws Exception {
    if (OfflinePresenter.isCoreOwnedEntityLifecycle(safeEvents)) {
      return OfflinePresenter.present(safeEvents,
          () -> { throw new IllegalStateException("Core-owned Entity lifecycle must not call writer"); });
    }
    try {
      JSONObject generated = provider.generate("");
      if (validator.validate(generated).isEmpty()) return generated;
    } catch (Exception error) {
      // The provider owns bounded transport failover; this layer never starts another content attempt.
    }
    return fallback(safeEvents);
  }

  private static JSONObject fallback(JSONArray safeEvents) throws Exception {
    return OfflinePresenter.present(safeEvents, () -> OfflinePresenter.fallback(safeEvents));
  }
}
