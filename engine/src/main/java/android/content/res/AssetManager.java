package android.content.res;
import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
public class AssetManager {
 public static final Map<String,String> files=new HashMap<>();
 public InputStream open(String name)throws IOException{
  String text=files.get(name);if(text==null)throw new FileNotFoundException(name);
  return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
 }
}
