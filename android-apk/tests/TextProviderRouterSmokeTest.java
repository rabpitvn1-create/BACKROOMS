import com.rabpit.backroom.TextProviderRouter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

class TextProviderRouterSmokeTest {
  static void check(boolean ok) { if (!ok) throw new AssertionError(); }
  public static void main(String[] args) throws Exception {
    String[] keys = {"test1", "test2", "test3", "test4", "test5"};
    List<String> attempts = new ArrayList<>();
    TextProviderRouter router = new TextProviderRouter();
    check("primary".equals(router.generate(true, () -> "primary", keys,
      (key,index) -> { throw new AssertionError("fallback after primary success"); }, attempts::add)));
    check(attempts.equals(Arrays.asList("Gehihi")));
    attempts.clear();
    check("fifth".equals(router.generate(true, () -> {throw new Exception("private payload");}, keys,
      (key,index) -> {if(index < 4)throw new TextProviderRouter.HttpFailure(429, 90_000);return "fifth";}, attempts::add)));
    check(attempts.equals(Arrays.asList("Gehihi","Gemini K1","Gemini K2","Gemini K3","Gemini K4","Gemini K5")));
    // A successful fallback rotates the next request and temporarily skips exhausted keys.
    attempts.clear();
    router.generate(false, () -> {throw new AssertionError();}, keys,(key,index) -> "ok", attempts::add);
    check(attempts.equals(Arrays.asList("Gemini K5")));
    AtomicLong now = new AtomicLong(1000);
    router = new TextProviderRouter(now::get);
    for(int round=0;round<2;round++) {
      try {router.generate(false, ()->"",keys,(key,index)->{throw new TextProviderRouter.HttpFailure(429,90_000);},x->{});throw new AssertionError();}
      catch(Exception error){check(!error.getMessage().contains("test1"));}
      if(round==0) {
        attempts.clear();
        try {router.generate(false,()->"",keys,(key,index)->{throw new AssertionError("ignored cooldown");},attempts::add);}
        catch(Exception expected) {check(attempts.isEmpty());}
        now.addAndGet(90_001);
      }
    }
    router = new TextProviderRouter();attempts.clear();
    try {router.generate(true,()->{throw new Exception("secret primary");},new String[]{"same","same"},
      (key,index)->{throw new Exception("secret fallback");},attempts::add);throw new AssertionError();}
    catch(Exception error) {check(!error.getMessage().contains("secret"));check(!error.getMessage().contains("same"));}
    check(attempts.equals(Arrays.asList("Gehihi","Gemini K1")));
    System.out.println("PASS: primary routing, K1-K5 fallback, rotation, Retry-After cooldown, deduplication and secret-free errors");
  }
}
