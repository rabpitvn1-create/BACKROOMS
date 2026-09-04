package com.rabpit.backroom;

import java.util.Objects;

/**
 * Deterministic text-provider routing policy for the Android runtime.
 *
 * Active chain: HAKU -> LUNA -> controlled failure.
 * Gemini is intentionally not represented in this router while it is runtime-locked.
 */
public final class AiProviderRouter {
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
  }

  public static final class ProviderChainException extends Exception {
    public ProviderChainException(Exception hakuFailure, Exception lunaFailure) {
      super("AI provider chain failed: HAKU -> LUNA.", lunaFailure);
      if (hakuFailure != null) addSuppressed(hakuFailure);
    }
  }

  public static String route(
      String prompt,
      ProviderCall haku,
      ProviderCall luna,
      FallbackPolicy fallbackPolicy,
      Observer observer
  ) throws Exception {
    return route(prompt, haku, luna, fallbackPolicy, (provider, response) -> response, observer);
  }

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
      String response = haku.call(prompt);
      return responseValidator.validate(HAKU, response);
    } catch (Exception error) {
      hakuFailure = error;
      if (!fallbackPolicy.isFallbackEligible(error)) throw error;
      observer.onFallback(HAKU, LUNA, error);
    }

    observer.onSelected(LUNA);
    try {
      String response = luna.call(prompt);
      return responseValidator.validate(LUNA, response);
    } catch (Exception lunaFailure) {
      throw new ProviderChainException(hakuFailure, lunaFailure);
    }
  }
}
