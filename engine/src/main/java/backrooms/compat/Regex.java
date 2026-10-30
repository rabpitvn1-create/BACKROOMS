package backrooms.compat;
import java.util.*;
import kotlin.ranges.IntRange;
import kotlin.sequences.Sequence;
import kotlin.text.*;
import org.json.*;
import org.teavm.jso.JSBody;

/** Kotlin Regex contract hosted by the browser's Unicode regular expression engine. */
public final class Regex {
 public static final Companion Companion=new Companion();
 private final String source;private final int options;
 public Regex(String source){this.source=source;options=0;}
 public Regex(String source,RegexOption option){this.source=source;options=option.getValue();}
 public boolean containsMatchIn(CharSequence text){return find(text,0)!=null;}
 public MatchResult find(CharSequence text,int start){
  if(start<0||start>text.length())throw new IndexOutOfBoundsException();
  String raw=findNative(source,options,text.toString(),start);
  if(raw==null||"null".equals(raw))return null;
  return new Result(this,text.toString(),new JSONObject(raw));
 }
 public static MatchResult find$default(Regex self,CharSequence text,int start,int mask,Object unused){return self.find(text,(mask&2)!=0?0:start);}
 public Sequence<MatchResult> findAll(CharSequence text,int start){return ()->new Iterator<MatchResult>(){
  MatchResult next=find(text,start);
  public boolean hasNext(){return next!=null;}
  public MatchResult next(){if(next==null)throw new NoSuchElementException();MatchResult out=next;next=next.next();return out;}
 };}
 public static Sequence<MatchResult> findAll$default(Regex self,CharSequence text,int start,int mask,Object unused){return self.findAll(text,(mask&2)!=0?0:start);}
 public boolean matches(CharSequence text){return matchEntire(text)!=null;}
 public MatchResult matchEntire(CharSequence text){MatchResult m=find(text,0);return m!=null&&m.getRange().getFirst()==0&&m.getValue().length()==text.length()?m:null;}
 public List<String> split(CharSequence text,int limit){
  if(limit<0)throw new IllegalArgumentException();
  List<String> result=new ArrayList<>();String input=text.toString();int pos=0;
  if(limit==1){result.add(input);return result;}
  MatchResult m=find(input,0);
  while(m!=null&&(limit==0||result.size()<limit-1)){
   result.add(input.substring(pos,m.getRange().getFirst()));pos=m.getRange().getFirst()+m.getValue().length();m=m.next();
  }
  result.add(input.substring(pos));return result;
 }
 public String replace(CharSequence text,String replacement){
  String input=text.toString();StringBuilder out=new StringBuilder();int pos=0;MatchResult m=find(input,0);
  while(m!=null){
   out.append(input,pos,m.getRange().getFirst());
   for(int i=0;i<replacement.length();i++){
    char c=replacement.charAt(i);
    if(c=='\\'&&i+1<replacement.length())out.append(replacement.charAt(++i));
    else if(c=='$'&&i+1<replacement.length()&&Character.isDigit(replacement.charAt(i+1))){
     int n=replacement.charAt(++i)-'0';while(i+1<replacement.length()&&Character.isDigit(replacement.charAt(i+1))&&n*10+replacement.charAt(i+1)-'0'<m.getGroupValues().size())n=n*10+replacement.charAt(++i)-'0';out.append(m.getGroupValues().get(n));
    }else out.append(c);
   }
   pos=m.getRange().getFirst()+m.getValue().length();m=m.next();
  }
  return out.append(input.substring(pos)).toString();
 }
 @Override public String toString(){return source;}
 @JSBody(params={"source","options","input","start"},script="return JSON.stringify(globalThis.BackroomsRegex.find(source,options,input,start));")
 private static native String findNative(String source,int options,String input,int start);
 public static final class Companion {
  public String escape(String text){return escapeNative(text);}
  @JSBody(params="text",script="return globalThis.BackroomsRegex.escape(text);")
  private static native String escapeNative(String text);
 }
 private static final class Result implements MatchResult {
  final Regex owner;final String input;final JSONObject match;
  Result(Regex owner,String input,JSONObject match){this.owner=owner;this.input=input;this.match=match;}
  public String getValue(){return match.getString("value");}
  public IntRange getRange(){int start=match.getInt("index");return new IntRange(start,start+getValue().length()-1);}
  public List<String> getGroupValues(){List<String> values=new ArrayList<>();JSONArray g=match.getJSONArray("groups");for(int i=0;i<g.length();i++)values.add(g.isNull(i)?"":g.getString(i));return values;}
  public MatchResult next(){int next=getRange().getFirst()+Math.max(1,getValue().length());return next>input.length()?null:owner.find(input,next);}
  public MatchResult.Destructured getDestructured(){return new MatchResult.Destructured(this);}
  public MatchGroupCollection getGroups(){return new Groups(this);}
 }
 private static final class Groups extends AbstractCollection<MatchGroup> implements MatchGroupCollection {
  final Result result;Groups(Result result){this.result=result;}
  public int size(){return result.match.getJSONArray("groups").length();}
  public MatchGroup get(int index){JSONArray groups=result.match.getJSONArray("groups");if(groups.isNull(index))return null;JSONArray range=result.match.getJSONArray("ranges").getJSONArray(index);return new MatchGroup(groups.getString(index),new IntRange(range.getInt(0),range.getInt(1)-1));}
  public Iterator<MatchGroup> iterator(){return new Iterator<MatchGroup>(){int index=0;public boolean hasNext(){return index<size();}public MatchGroup next(){return get(index++);}};}
 }
}
