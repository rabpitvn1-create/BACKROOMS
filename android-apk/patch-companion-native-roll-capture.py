"""Extract final native roll policy verbatim; inject draws without inventing RNG/policy."""
from pathlib import Path
import hashlib,json,re
ROOT=Path(__file__).resolve().parent
MAIN=ROOT/'app/src/main/java/com/rabpit/backroom/MainActivity.java'
source=MAIN.read_text()
names=['lower','currentLevel','containsAny','partyHas','flagSpawned','anNhienFollowing',
       'anNhienEncountered','reunionEligibleAndroid','thresholdRoll','makeGameplayRolls',
       'applyLevelBoundEntitySpawns','rollSuccess']

def method(name):
    matches=list(re.finditer(r'^  private [^\n]+\b'+name+r'\(',source,re.M))
    if len(matches)!=1:raise RuntimeError('Ambiguous native method '+name)
    start=matches[0].start(); brace=source.index('{',matches[0].end())
    depth=0; mode='code'; i=brace
    while i<len(source):
        ch=source[i]; nxt=source[i:i+2]
        if mode in ('string','char'):
            if ch=='\\':i+=2;continue
            if ch==('"' if mode=='string' else "'"):mode='code'
        elif mode=='line':
            if ch=='\n':mode='code'
        elif mode=='comment':
            if nxt=='*/':mode='code';i+=2;continue
        elif nxt=='//':mode='line';i+=2;continue
        elif nxt=='/*':mode='comment';i+=2;continue
        elif ch=='"':mode='string'
        elif ch=="'":mode='char'
        elif ch=='{':depth+=1
        elif ch=='}':
            depth-=1
            if depth==0:return source[start:i+1]
        i+=1
    raise RuntimeError('Unclosed native method '+name)

original='\n\n'.join(method(n) for n in names)
if original.count('GAME_RNG.nextInt(')!=3:raise RuntimeError('Native RNG sites changed; review required')
body=original.replace('GAME_RNG.nextInt(max)','draws.next(purpose(label), max)')
body=body.replace('GAME_RNG.nextInt(roamingPool.length)','draws.next(Purpose.ROAMING_ENTITY_KEY, roamingPool.length)')
body=body.replace('GAME_RNG.nextInt(com.rabpit.backroom.core.EntityEncounterPolicy.DIE)',
                  'draws.next(Purpose.LEVEL_BOUND_ENTITY, com.rabpit.backroom.core.EntityEncounterPolicy.DIE)')
labels={'anNhienEncounter':'AN_NHIEN_ENCOUNTER','survivor':'SURVIVOR','irisReunion':'IRIS_REUNION',
'syvialReunion':'SYVIAL_REUNION','luciaEncounter':'LUCIA_ENCOUNTER','lucTramEncounter':'LUC_TRAM_ENCOUNTER',
'anNhienHazardCheck':'AN_NHIEN_HAZARD_CHECK','hazard':'HAZARD','diepMinhEncounter':'DIEP_MINH_ENCOUNTER',
'entityEncounter':'ENTITY_ENCOUNTER','loot':'LOOT','madGodSet':'MAD_GOD_SET','almondWater':'ALMOND_WATER'}
for label in re.findall(r'thresholdRoll\("([^"]+)"',body):
    if label not in labels:raise RuntimeError('Unregistered native purpose '+label)
header='''package com.rabpit.backroom.core.companion;
import org.json.JSONObject;
import org.json.JSONArray;
import com.rabpit.backroom.core.companion.CompanionRollTape.Purpose;
/** GENERATED from final MainActivity policy. No independent rules, generator or persistence. */
public final class CompanionNativeGameplayRolls {
  public interface Draws { int next(Purpose purpose, int bound); }
  private final Draws draws;
  public CompanionNativeGameplayRolls(Draws draws) { this.draws = draws; }
  public JSONObject make(JSONObject state, String kind, String action, boolean meta) throws Exception {
    return makeGameplayRolls(state, kind, action, meta);
  }
  private static Purpose purpose(String label) {
    switch(label) {
'''
header=header.replace('  public interface Draws', '  public static final String POLICY_DIGEST = \"'+hashlib.sha256(original.encode()).hexdigest()+'\";\n  public interface Draws')
header+=''.join('      case "'+k+'": return Purpose.'+v+';\n' for k,v in labels.items())
header+='      default: throw new IllegalArgumentException("native_purpose_unknown");\n    }\n  }\n'
p=ROOT/'app/src/main/java/com/rabpit/backroom/core/companion/CompanionNativeGameplayRolls.java'
p.parent.mkdir(parents=True,exist_ok=True);p.write_text(header+body+'\n}\n')
reference='''package com.rabpit.backroom.core.companion;
import org.json.JSONObject;
import org.json.JSONArray;
/** Exact final native method bodies, test oracle with only an injected bounded callback. */
final class CompanionNativeRollReference {
  private final java.util.function.IntUnaryOperator draws;
  CompanionNativeRollReference(java.util.function.IntUnaryOperator draws) { this.draws = draws; }
  JSONObject make(JSONObject state, String kind, String action, boolean meta) throws Exception {
    return makeGameplayRolls(state, kind, action, meta);
  }
'''+original.replace('GAME_RNG.nextInt(', 'draws.applyAsInt(')+'\n}\n'
p=ROOT/'app/src/test/java/com/rabpit/backroom/core/companion/CompanionNativeRollReference.java';p.write_text(reference)
report=ROOT/'app/build/reports/companion-native-roll-extraction';report.mkdir(parents=True,exist_ok=True)
(report/'provenance.json').write_text(json.dumps({'main_sha256':hashlib.sha256(source.encode()).hexdigest(),
 'methods':{n:hashlib.sha256(method(n).encode()).hexdigest() for n in names},
 'rng_sites':3,'policy_class_sha256':hashlib.sha256((header+body+'\n}\n').encode()).hexdigest()},indent=2))
print('Extracted final native roll policy: 12 method bodies, 3 original RNG sites; MainActivity unchanged.')
