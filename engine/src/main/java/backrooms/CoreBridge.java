package backrooms;
import com.rabpit.backroom.core.*;
import org.json.JSONObject;
import org.teavm.jso.JSExport;

public final class CoreBridge {
  static class MemorySave implements SaveRepository {
    GameState value;
    public void save(GameState state) {value=state;}
    public void saveFresh(GameState state) {value=state;}
    public GameState load() {return value==null?GameState.Companion.initial():value;}
    public boolean exists() {return value!=null;}
    public void clear() {value=null;}
  }
  static final android.content.Context context=new android.content.Context();
  static final NarratorLogic narrator=new NarratorLogic();
  static MemorySave repository;
  static GameCoreFacade facade;
  @JSExport public static void coreAsset(String name,String text){android.content.res.AssetManager.files.put(name,text);}
  @JSExport public static boolean coreMeta(String action){return narrator.meta(action);}
  @JSExport public static String coreRolls(String state,String action,boolean meta){try{return narrator.rolls(state,action,meta);}catch(Throwable t){return error(t);}}
  @JSExport public static String corePrompt(String state,String action,String rolls){
    try{
      com.rabpit.backroom.core.canon.CanonOutcome result=com.rabpit.backroom.core.canon.CanonRuntime2.buildPacket(context,state,action,rolls,false);
      String packet=null;
      if(result instanceof com.rabpit.backroom.core.canon.CanonOutcome.Ready)packet=((com.rabpit.backroom.core.canon.CanonOutcome.Ready)result).getPacket();
      if(result instanceof com.rabpit.backroom.core.canon.CanonOutcome.SafeLocalResolution)throw new IllegalStateException(((com.rabpit.backroom.core.canon.CanonOutcome.SafeLocalResolution)result).getReason());
      return narrator.prompt(state,action,rolls,packet);
    }catch(Throwable t){return error(t);}
  }
  @JSExport public static String coreCandidate(String state,String generated,String rolls,String action){try{return narrator.candidate(state,generated,rolls,action);}catch(Throwable t){return error(t);}}
  private static String error(Throwable t){t.printStackTrace();return new JSONObject().put("bridgeError",t.toString()).toString();}
  @JSExport public static String coreInit(String policy, String saved) {
    try {
      repository=new MemorySave();
      if(!saved.isEmpty())repository.value=GameStateCodec.INSTANCE.decode(saved);
      facade=new GameCoreFacade(repository, NoOpGamePipelineLogger.INSTANCE,
          new LevelTravelEngine(new LevelTravelPolicy(new JSONObject(policy)), () -> Math.random()),
          () -> (int)(Math.random()*10000));
      return "{\"ok\":true}";
    } catch(Throwable t) {return error(t);}
  }
  @JSExport public static String coreCall(String name, String a, String b, String c, String d) {
    try {
      switch(name) {
        case "initializeLevelGame":return facade.initializeLevelGame(a,Boolean.parseBoolean(b));
        case "resumeLevelGame":return facade.resumeLevelGame(a,Boolean.parseBoolean(b));
        case "levelCoreSnapshot":return repository.exists()?facade.currentCoreState():"";
        case "processLevelAction":return facade.processLevelAction(a,b);
        case "finishLevelTurn":return facade.finishLevelTurn(a,b,c);
        case "processWorldPickup":return facade.processWorldPickup(a,b);
        case "processNamedFollowerRule":return facade.processNamedFollowerRule(a,b);
        case "processRule":return facade.processRule(a,b);
        case "processExplorationLoot":return facade.processExplorationLoot(a,b,c);
        case "prepareNamedFollowerTurn":return facade.prepareNamedFollowerTurn(a,b,c,d);
        case "commitNamedFollowerTurn":return facade.commitNamedFollowerTurn(a,b,c,d);
        case "processValidatedCandidate":return facade.processValidatedCandidate(a,b,c);
        case "processCombat":return facade.processCombat(a,b,c);
        case "startCombatState":return facade.startCombatState(a,b);
        case "coreStats":return facade.coreStats(a,b);
        case "processCoreUpgrade":return facade.processCoreUpgrade(a,b,c);
        case "prepareCombatDice":return facade.prepareCombatDice(a,b);
        case "setCombatDiceHold":return facade.setCombatDiceHold(a,Integer.parseInt(b),Boolean.parseBoolean(c));
        case "rerollCombatDice":return facade.rerollCombatDice(a);
        case "finishCombatDice":return facade.finishCombatDice(a);
        case "processItemAction":return facade.processItemAction(a,b);
        case "clear":facade.clear();return "{}";
        default:throw new IllegalArgumentException("Unknown core call: "+name);
      }
    }catch(Throwable t){return error(t);}
  }
}
