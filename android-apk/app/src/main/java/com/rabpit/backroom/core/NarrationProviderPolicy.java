package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

/** At most one validation-guided repair after the initial writer content attempt. */
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

    String rejection;
    try {
      JSONObject generated = provider.generate("");
      rejection = validator.validate(generated);
      if (rejection.isEmpty()) return generated;
    } catch (Exception error) {
      // The provider already owns bounded transport failover. Do not add another content call here.
      return fallback(safeEvents);
    }

    try {
      JSONObject repaired = provider.generate(rejection);
      if (validator.validate(repaired).isEmpty()) return repaired;
    } catch (Exception error) {
      // One repair is the hard ceiling; any failure falls back locally.
    }
    return fallback(safeEvents);
  }

  private static JSONObject fallback(JSONArray safeEvents) throws Exception {
    return OfflinePresenter.present(safeEvents, () -> OfflinePresenter.fallback(safeEvents));
  }
}
