import dalvik.system.DexClassLoader;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import org.json.JSONObject;

/** Runs the actual recovered APK router with fake calls, never real credentials. */
public final class RouterSmoke {
  static Class<?> routerType, callType, failureType;
  static Method generate;

  static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  static void scenario(int failedKeys, int status) throws Exception {
    List<String> attempts = new ArrayList<>();
    String[] keys = {"new-key-1", "new-key-2", "new-key-3", "new-key-4", "new-key-5", "new-key-6"};
    Object router = routerType.getConstructor().newInstance();
    Object primary = Proxy.newProxyInstance(callType.getClassLoader(), new Class<?>[]{callType},
        (proxy, method, args) -> {
          if (!method.getName().equals("call")) throw new AssertionError("Unexpected proxy method");
          int index = (Integer)args[1];
          check(keys[index].equals(args[0]), "Router selected an unexpected key");
          attempts.add("K" + (index + 1));
          if (index < failedKeys) throw (Exception)failureType.getConstructor(int.class, long.class).newInstance(status, 0L);
          return "primary-ok";
        });
    Callable<String> fallback = () -> { throw new AssertionError("Disabled fallback must never run"); };
    Consumer<String> activity = name -> {};
    try {
      String result = (String)generate.invoke(router, false, fallback, keys, primary, activity);
      check(failedKeys < 6, "Exhausted keys must report failure");
      check(result.equals("primary-ok"), "Unexpected provider result");
    } catch (InvocationTargetException error) {
      if (failedKeys < 6 || error.getCause() instanceof AssertionError) throw error;
    }
    check(!attempts.contains("Argon"), "Disabled fallback was selected");
    if (failedKeys < 6) {
      check(attempts.get(attempts.size() - 1).equals("K" + (failedKeys + 1)), "Wrong next key");
    } else {
      check(new HashSet<>(attempts).equals(new HashSet<>(Arrays.asList("K1", "K2", "K3", "K4", "K5", "K6"))), "Not all six primary keys ran");
      if (status == 401) check(attempts.equals(Arrays.asList("K1", "K2", "K3", "K4", "K5", "K6")), "Incorrect unauthorized-key order");
    }
    System.out.println("PASS: failures=" + failedKeys + ", HTTP=" + status + ", external fallback disabled");
  }

  public static void main(String[] args) throws Exception {
    ClassLoader loader = new DexClassLoader(args[0], "/data/local/tmp", null, RouterSmoke.class.getClassLoader());
    routerType = loader.loadClass("com.rabpit.backroom.TextProviderRouter");
    callType = loader.loadClass("com.rabpit.backroom.TextProviderRouter$GeminiCall");
    failureType = loader.loadClass("com.rabpit.backroom.TextProviderRouter$HttpFailure");
    generate = routerType.getMethod("generate", boolean.class, Callable.class, String[].class, callType, Consumer.class);
    scenario(0, 401);
    scenario(1, 401);
    scenario(6, 401);
    scenario(6, 429);
    Class<?> credentials = loader.loadClass("com.rabpit.backroom.ProviderCredentials");
    Method validate = credentials.getDeclaredMethod("validateConfig", JSONObject.class);
    validate.setAccessible(true);
    JSONObject config = new JSONObject();
    for (int i = 1; i <= 6; i++) config.put("GEMINI_API_KEY_" + i, "new-key-" + i);
    config.put("GEMINI_4_API_KEY", "");
    config.put("GEMINI_4_BASE_URL", "https://example.org/v1");
    config.put("GEMINI_4_MODEL", "");
    JSONObject actual = (JSONObject)validate.invoke(null, config);
    check(actual.length() == 9, "Credential schema changed");
    check(actual.getString("GEMINI_4_API_KEY").isEmpty(), "Fallback key must be empty");
    check(actual.getString("GEMINI_4_MODEL").isEmpty(), "Fallback model must be empty");
    System.out.println("PASS: APK credentials accept six current keys with external fallback disabled");
  }
}
