package backrooms.compat;
import org.teavm.jso.JSBody;
public final class Normalizer {
  public enum Form {NFD,NFC,NFKD,NFKC}
  public static String normalize(CharSequence text, Form form) {return nativeNormalize(text.toString(),form.name());}
  public static boolean isNormalized(CharSequence text, Form form) {return text.toString().equals(normalize(text,form));}
  @JSBody(params={"text","form"},script="return text.normalize(form);")
  private static native String nativeNormalize(String text,String form);
}
