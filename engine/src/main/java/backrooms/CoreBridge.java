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
  static MemorySave repository;
  static GameCoreFacade facade;
  @JSExport public static String coreInit(String policy, String saved) {
    try {
      repository=new MemorySave();
      if(!saved.isEmpty())repository.value=GameStateCodec.INSTANCE.decode(saved);
      facade=new GameCoreFacade(repository, NoOpGamePipelineLogger.INSTANCE,
          new LevelTravelEngine(new LevelTravelPolicy(new JSONObject(policy)), () -> Math.random()),
          () -> (int)(Math.random()*10000));
      return "{\"ok\":true}";
    } catch(Throwable t) {return new JSONObject().put("bridgeError",t.toString()).toString();}
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
    }catch(Throwable t){return new JSONObject().put("bridgeError",t.toString()).toString();}
  }
}
