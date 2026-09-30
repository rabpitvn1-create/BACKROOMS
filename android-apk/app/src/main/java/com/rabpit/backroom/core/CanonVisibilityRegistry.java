package com.rabpit.backroom.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fail-closed visibility registry used when constructing AI narration context.
 * Unregistered fields are EPISTEMIC by default.
 */
final class CanonVisibilityRegistry {
  enum Visibility {
    PUBLIC,
    DIRECTLY_OBSERVABLE,
    EPISTEMIC
  }

  private static final Map<String, Visibility> ROOT_VISIBILITY;
  static {
    Map<String, Visibility> values = new LinkedHashMap<>();
    values.put("title", Visibility.PUBLIC);
    values.put("turn", Visibility.PUBLIC);
    values.put("currentLevel", Visibility.DIRECTLY_OBSERVABLE);
    values.put("currentLevelKey", Visibility.DIRECTLY_OBSERVABLE);
    values.put("location", Visibility.DIRECTLY_OBSERVABLE);
    values.put("player", Visibility.DIRECTLY_OBSERVABLE);
    values.put("party", Visibility.DIRECTLY_OBSERVABLE);
    values.put("inventory", Visibility.DIRECTLY_OBSERVABLE);
    values.put("gameTime", Visibility.DIRECTLY_OBSERVABLE);
    values.put("partyDetails", Visibility.DIRECTLY_OBSERVABLE);
    values.put("characterProgression", Visibility.DIRECTLY_OBSERVABLE);
    values.put("combat", Visibility.DIRECTLY_OBSERVABLE);
    ROOT_VISIBILITY = Collections.unmodifiableMap(values);
  }

  private CanonVisibilityRegistry() {}

  static Visibility rootVisibility(String key) {
    Visibility visibility = ROOT_VISIBILITY.get(key == null ? "" : key);
    return visibility == null ? Visibility.EPISTEMIC : visibility;
  }
}
