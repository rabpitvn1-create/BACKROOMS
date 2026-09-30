import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

/** Run from the repository root with Java 17. PNG overlays are measured directly.
 * Entity WebP files use committed alpha bounds and are verified by dimensions + SHA-256. */
class GenerateOverlayMetrics {
  static final Path ASSETS = Path.of("android-apk/app/src/main/assets");
  static final String START = "  // BEGIN GENERATED OVERLAY METRICS\n";
  static final String END = "  // END GENERATED OVERLAY METRICS\n";
  static final Pattern WIDTH = Pattern.compile("\\\"width\\\":(\\d+)");
  static final Pattern HEIGHT = Pattern.compile("\\\"height\\\":(\\d+)");
  static final Pattern SHA = Pattern.compile("\\\"sha256\\\":\\\"([0-9a-f]{64})\\\"");

  static String bounds(BufferedImage image, int threshold) {
    int left=image.getWidth(), top=image.getHeight(), right=0, bottom=0;
    for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
      if((image.getRGB(x,y)>>>24)>threshold) {
        left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x+1);bottom=Math.max(bottom,y+1);
      }
    }
    if(right<=left || bottom<=top) return null;
    return "{\"left\":"+left+",\"top\":"+top+",\"right\":"+right+",\"bottom\":"+bottom+"}";
  }

  static String sha256(Path file) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
  }

  static int le16(byte[] b,int o){ return (b[o]&255)|((b[o+1]&255)<<8); }
  static int le24(byte[] b,int o){ return (b[o]&255)|((b[o+1]&255)<<8)|((b[o+2]&255)<<16); }
  static String fourcc(byte[] b,int o){ return new String(b,o,4,java.nio.charset.StandardCharsets.US_ASCII); }

  static int[] webpDimensions(Path file) throws Exception {
    byte[] b=Files.readAllBytes(file);
    if(b.length<30 || !"RIFF".equals(fourcc(b,0)) || !"WEBP".equals(fourcc(b,8)))
      throw new IllegalStateException("Invalid WebP: "+file);
    String chunk=fourcc(b,12);
    if("VP8X".equals(chunk)) return new int[]{1+le24(b,24),1+le24(b,27)};
    if("VP8L".equals(chunk)) {
      int o=20;
      if((b[o]&255)!=0x2f) throw new IllegalStateException("Invalid VP8L: "+file);
      int width=1+((b[o+1]&255)|((b[o+2]&0x3f)<<8));
      int height=1+(((b[o+2]&0xc0)>>6)|((b[o+3]&255)<<2)|((b[o+4]&0x0f)<<10));
      return new int[]{width,height};
    }
    if("VP8 ".equals(chunk)) {
      int o=20;
      if((b[o+3]&255)!=0x9d || (b[o+4]&255)!=0x01 || (b[o+5]&255)!=0x2a)
        throw new IllegalStateException("Invalid VP8 frame: "+file);
      return new int[]{le16(b,o+6)&0x3fff,le16(b,o+8)&0x3fff};
    }
    throw new IllegalStateException("Unsupported WebP chunk "+chunk+": "+file);
  }

  static int value(Pattern p,String line,String key) {
    Matcher m=p.matcher(line); if(!m.find()) throw new IllegalStateException("Missing "+key+" in metadata: "+line);
    return Integer.parseInt(m.group(1));
  }
  static String hashValue(String line) {
    Matcher m=SHA.matcher(line); if(!m.find()) throw new IllegalStateException("Missing sha256 in metadata: "+line);
    return m.group(1);
  }

  static Map<String,String> existingEntityMetrics(String source) {
    int start=source.indexOf(START),end=source.indexOf(END);
    if(start<0 || end<start) throw new IllegalStateException("Missing metadata anchors");
    Map<String,String> out=new HashMap<>();
    for(String raw:source.substring(start+START.length(),end).split("\n")) {
      String line=raw.trim();
      if(!line.startsWith("\"entity/") || !line.contains(".webp\"")) continue;
      int q=line.indexOf("\":");
      String key=line.substring(1,q);
      if(line.endsWith(",")) line=line.substring(0,line.length()-1);
      out.put(key,"    "+line);
    }
    return out;
  }

  public static void main(String[] args) throws Exception {
    Path script=ASSETS.resolve("snapshot-ui.js");
    String source=Files.readString(script);
    Map<String,String> entityMetrics=existingEntityMetrics(source);
    List<Path> files=new ArrayList<>();
    try(var paths=Files.list(ASSETS)) {
      paths.filter(p -> {
        String name=p.getFileName().toString();
        return name.matches(".*_(snapshot|entity)_overlay\\.png") || name.equals("luctram_overlay.png");
      }).forEach(files::add);
    }
    try(var paths=Files.list(ASSETS.resolve("entity"))) {
      paths.filter(p->p.toString().endsWith(".webp")).forEach(files::add);
    }
    files.sort(Comparator.comparing(Path::toString));
    if(files.isEmpty()) throw new IllegalStateException("No overlay assets found");

    var lines=new ArrayList<String>();
    for(Path file:files) {
      String key=ASSETS.relativize(file).toString().replace('\\','/');
      if(key.endsWith(".webp")) {
        String line=entityMetrics.get(key);
        if(line==null) throw new IllegalStateException("Missing WebP metadata: "+key);
        int[] dims=webpDimensions(file);
        if(value(WIDTH,line,"width")!=dims[0] || value(HEIGHT,line,"height")!=dims[1])
          throw new IllegalStateException("WebP dimensions changed; refresh bounds metadata: "+key);
        if(!hashValue(line).equals(sha256(file)))
          throw new IllegalStateException("WebP hash changed; refresh bounds metadata: "+key);
        lines.add(line);
        continue;
      }
      BufferedImage image=ImageIO.read(file.toFile());
      if(image==null) throw new IllegalStateException("Cannot decode "+file);
      String paint=bounds(image,8),body=bounds(image,128);
      if(paint==null) throw new IllegalStateException("Empty overlay: "+file);
      if(body==null) body=paint;
      lines.add("    \""+key+"\":{\"width\":"+image.getWidth()+",\"height\":"+image.getHeight()+",\"paint\":"+paint+",\"body\":"+body+",\"sha256\":\""+sha256(file)+"\"}");
    }

    int start=source.indexOf(START),end=source.indexOf(END);
    if(start<0 || end<start) throw new IllegalStateException("Missing metadata anchors");
    String replacement=START+"  var bundledMetrics={\n"+String.join(",\n",lines)+"\n  };\n";
    String generated=source.substring(0,start)+replacement+source.substring(end);
    if(Arrays.asList(args).contains("--check")) {
      if(!source.equals(generated)) throw new IllegalStateException("Overlay metadata stale");
    } else Files.writeString(script,generated);
    System.out.println("Verified bounds, dimensions and hashes for "+files.size()+" overlay assets");
  }
}
