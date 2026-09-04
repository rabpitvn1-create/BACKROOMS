package com.rabpit.backroom;

import java.util.Objects;

/**
 * Deterministic text-provider routing policy for the Android runtime.
 *
 * Active high-priority chain: GEMINI -> HAKU -> LUNA -> controlled failure.
 * Gemini performs deterministic credential fallback internally.
 */
public final class AiProviderRouter {
  public static final String GEMINI = "GEMINI";
  public static final String HAKU = "HAKU";
  public static final String LUNA = "LUNA";

  private AiProviderRouter() {}

  @FunctionalInterface
  public interface ProviderCall {
    String call(String prompt) throws Exception;
  }

  @FunctionalInterface
  public interface FallbackPolicy {
    boolean isFallbackEligible(Exception error);
  }

  @FunctionalInterface
  public interface ResponseValidator {
    String validate(String provider, String response) throws Exception;
  }

  public interface Observer {
    void onSelected(String provider);
    void onFallback(String fromProvider, String toProvider, Exception error);
    default void onFailure(String provider, Exception error) {}
  }

  public static final class ProviderChainException extends Exception {
    public ProviderChainException(Exception hakuFailure, Exception lunaFailure) {
      super("AI provider chain failed: HAKU -> LUNA.", lunaFailure);
      if (hakuFailure != null) addSuppressed(hakuFailure);
    }

    public ProviderChainException(Exception geminiFailure, Exception hakuFailure, Exception lunaFailure) {
      super("AI provider chain failed: GEMINI -> HAKU -> LUNA.", lunaFailure);
      if (geminiFailure != null) addSuppressed(geminiFailure);
      if (hakuFailure != null) addSuppressed(hakuFailure);
    }
  }

  /** Compatibility overload retained for legacy two-provider HAKU -> LUNA callers. */
  public static String route(
      String prompt,
      ProviderCall haku,
      ProviderCall luna,
      FallbackPolicy fallbackPolicy,
      Observer observer
  ) throws Exception {
    return route(prompt, haku, luna, fallbackPolicy, (provider, response) -> response, observer);
  }

  /** Compatibility overload retained for legacy two-provider HAKU -> LUNA callers. */
  public static String route(
      String prompt,
      ProviderCall haku,
      ProviderCall luna,
      FallbackPolicy fallbackPolicy,
      ResponseValidator responseValidator,
      Observer observer
  ) throws Exception {
    Objects.requireNonNull(haku, "haku");
    Objects.requireNonNull(luna, "luna");
    Objects.requireNonNull(fallbackPolicy, "fallbackPolicy");
    Objects.requireNonNull(responseValidator, "responseValidator");
    Objects.requireNonNull(observer, "observer");

    observer.onSelected(HAKU);
    Exception hakuFailure;
    try {
      return responseValidator.validate(HAKU, haku.call(prompt));
    } catch (Exception error) {
      hakuFailure = error;
      observer.onFailure(HAKU, error);
      if (!fallbackPolicy.isFallbackEligible(error)) throw error;
      observer.onFallback(HAKU, LUNA, error);
    }

    observer.onSelected(LUNA);
    try {
      return responseValidator.validate(LUNA, luna.call(prompt));
    } catch (Exception lunaFailure) {
      observer.onFailure(LUNA, lunaFailure);
      throw new ProviderChainException(hakuFailure, lunaFailure);
    }
  }

  public static String route(
      String prompt,
      ProviderCall gemini,
      ProviderCall haku,
      ProviderCall luna,
      FallbackPolicy fallbackPolicy,
      Observer observer
  ) throws Exception {
    return route(prompt, gemini, haku, luna, fallbackPolicy, (provider, response) -> response, observer);
  }

  public static String route(
      String prompt,
      ProviderCall gemini,
      ProviderCall haku,
      ProviderCall luna,
      FallbackPolicy fallbackPolicy,
      ResponseValidator responseValidator,
      Observer observer
  ) throws Exception {
    Objects.requireNonNull(gemini, "gemini");
    Objects.requireNonNull(haku, "haku");
    Objects.requireNonNull(luna, "luna");
    Objects.requireNonNull(fallbackPolicy, "fallbackPolicy");
    Objects.requireNonNull(responseValidator, "responseValidator");
    Objects.requireNonNull(observer, "observer");

    observer.onSelected(GEMINI);
    Exception geminiFailure;
    try {
      return responseValidator.validate(GEMINI, gemini.call(prompt));
    } catch (Exception error) {
      geminiFailure = error;
      observer.onFailure(GEMINI, error);
      if (!fallbackPolicy.isFallbackEligible(error)) throw error;
      observer.onFallback(GEMINI, HAKU, error);
    }

    observer.onSelected(HAKU);
    Exception hakuFailure;
    try {
      return responseValidator.validate(HAKU, haku.call(prompt));
    } catch (Exception error) {
      hakuFailure = error;
      observer.onFailure(HAKU, error);
      if (!fallbackPolicy.isFallbackEligible(error)) throw error;
      observer.onFallback(HAKU, LUNA, error);
    }

    observer.onSelected(LUNA);
    try {
      return responseValidator.validate(LUNA, luna.call(prompt));
    } catch (Exception lunaFailure) {
      observer.onFailure(LUNA, lunaFailure);
      throw new ProviderChainException(geminiFailure, hakuFailure, lunaFailure);
    }
  }
}
