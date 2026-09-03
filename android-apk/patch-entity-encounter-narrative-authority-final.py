from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
VERIFY = ROOT / "ci_verify_runtime_contracts.py"

text = MAIN.read_text(encoding="utf-8")


def method_bounds(source: str, signature: str) -> tuple[int, int]:
    start = source.find(signature)
    if start < 0:
        raise RuntimeError(f"method signature missing: {signature}")
    brace = source.find("{", start)
    if brace < 0:
        raise RuntimeError(f"method opening brace missing: {signature}")
    depth = 0
    for index in range(brace, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                end = index + 1
                while end < len(source) and source[end] in "\r\n":
                    end += 1
                return start, end
    raise RuntimeError(f"method closing brace missing: {signature}")


def block_bounds(source: str, marker: str) -> tuple[int, int]:
    start = source.find(marker)
    if start < 0:
        raise RuntimeError(f"block marker missing: {marker}")
    brace = source.find("{", start)
    if brace < 0:
        raise RuntimeError(f"block opening brace missing: {marker}")
    depth = 0
    for index in range(brace, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return start, index + 1
    raise RuntimeError(f"block closing brace missing: {marker}")


helpers = r'''  private String encounterEntityKey(JSONObject rolls) throws Exception {
    if (rolls == null) return "";
    String selected = com.rabpit.backroom.core.EntityEncounterNarrativeAuthority.selectedEntityKey(rolls.toString());
    if (selected == null || selected.trim().isEmpty()) return "";
    return normalizedEntityKey(selected);
  }

  private String encounterEntityDisplayName(JSONObject rolls) throws Exception {
    String key = encounterEntityKey(rolls);
    if (key.isEmpty()) return "";
    try {
      return resolveEntityOverlay(key).optString("name", key).trim();
    } catch (Exception ignored) {
      return key;
    }
  }

  private String encounterNarrativeFact(JSONObject rolls) throws Exception {
    if (rolls == null) return "";
    return com.rabpit.backroom.core.EntityEncounterNarrativeAuthority.visibleFact(
      rolls.toString(), encounterEntityDisplayName(rolls));
  }

  private String ensureEncounterNarrative(JSONObject rolls, String reply) throws Exception {
    if (rolls == null) return reply == null ? "" : reply.trim();
    return com.rabpit.backroom.core.EntityEncounterNarrativeAuthority.ensureReply(
      rolls.toString(), reply == null ? "" : reply, encounterEntityDisplayName(rolls));
  }

'''

writer_signature = "  private String writerPrompt(JSONObject before, String action, JSONObject rolls, JSONArray auditFeedback) throws Exception "
if "private String encounterEntityKey(JSONObject rolls)" not in text:
    writer_start = text.find(writer_signature)
    if writer_start < 0:
        raise RuntimeError("final writerPrompt missing for Entity narrative authority")
    text = text[:writer_start] + helpers + text[writer_start:]

# The GM sees the exact selected encounter as a visible fact, not merely as raw dice it may ignore.
writer_start, writer_end = method_bounds(text, writer_signature)
writer = text[writer_start:writer_end]
roll_fragment = '      "\\n\\nGAMEPLAY_ROLLS:\\n" + rolls.toString() +\n'
roll_with_fact = '      "\\n\\nGAMEPLAY_ROLLS:\\n" + rolls.toString() + encounterNarrativeFact(rolls) +\n'
if roll_with_fact not in writer:
    if writer.count(roll_fragment) != 1:
        raise RuntimeError(f"writer GAMEPLAY_ROLLS anchor expected once, found {writer.count(roll_fragment)}")
    writer = writer.replace(roll_fragment, roll_with_fact, 1)
    text = text[:writer_start] + writer + text[writer_end:]

# Combat startup consumes the exact same selected key as the narration authority.
force_signature = "  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception "
force_start, force_end = method_bounds(text, force_signature)
force_replacement = r'''  private void forceEntityEncounterFlag(JSONObject candidateState, JSONObject rolls) throws Exception {
    if (candidateState == null || rolls == null) return;
    String canonicalKey = encounterEntityKey(rolls);
    if (canonicalKey.isEmpty()) return;
    JSONObject flags = candidateState.optJSONObject("flags");
    if (flags == null) {
      flags = new JSONObject();
      candidateState.put("flags", flags);
    }
    flags.put("entityEncounterKey", canonicalKey);
    requireGameCore().startCombatState(candidateState.toString(), canonicalKey);
  }

'''
text = text[:force_start] + force_replacement + text[force_end:]

# Guard both the first writer result and the one allowed semantic-repair result before audits.
# Add one final guard before transcript commit as defense in depth against later local transformations.
bridge_start, bridge_end = block_bounds(text, "  private class GameBridge ")
bridge = text[bridge_start:bridge_end]
initial_anchor = '          String reply = generated.optString("reply", "").trim();\n'
initial_new = initial_anchor + '          reply = ensureEncounterNarrative(rolls, reply);\n'
if initial_new not in bridge:
    if bridge.count(initial_anchor) != 1:
        raise RuntimeError(f"initial GM reply anchor expected once in GameBridge, found {bridge.count(initial_anchor)}")
    bridge = bridge.replace(initial_anchor, initial_new, 1)

repair_anchor = '            reply = generated.optString("reply", "").trim();\n'
repair_new = repair_anchor + '            reply = ensureEncounterNarrative(rolls, reply);\n'
if repair_new not in bridge:
    if bridge.count(repair_anchor) != 1:
        raise RuntimeError(f"repair GM reply anchor expected once in GameBridge, found {bridge.count(repair_anchor)}")
    bridge = bridge.replace(repair_anchor, repair_new, 1)

log_anchor = '          log.put(new JSONObject().put("role", "gm").put("text", reply));\n'
log_new = '          reply = ensureEncounterNarrative(rolls, reply);\n' + log_anchor
if log_new not in bridge:
    if bridge.count(log_anchor) != 1:
        raise RuntimeError(f"GM transcript anchor expected once in GameBridge, found {bridge.count(log_anchor)}")
    bridge = bridge.replace(log_anchor, log_new, 1)

text = text[:bridge_start] + bridge + text[bridge_end:]

# Strict final contract checks. The old force helper may no longer select rolls independently.
force_start, force_end = method_bounds(text, force_signature)
force = text[force_start:force_end]
for marker in (
    'String canonicalKey = encounterEntityKey(rolls);',
    'flags.put("entityEncounterKey", canonicalKey);',
    'requireGameCore().startCombatState(candidateState.toString(), canonicalKey);',
):
    if marker not in force:
        raise RuntimeError("final encounter force contract missing: " + marker)
for forbidden in (
    'rolls.optJSONObject("diepMinhEncounter")',
    'rolls.optJSONObject("monsterXEncounter")',
    'rolls.optJSONObject("johnDoeEncounter")',
    'rolls.optJSONObject("scp173Encounter")',
    'rolls.optJSONObject("violetWardenEncounter")',
    'rolls.optJSONObject("kaiDevilWithinEncounter")',
    'rolls.optString("roamingEntityKey"',
):
    if forbidden in force:
        raise RuntimeError("duplicated Entity selection survived in force helper: " + forbidden)

for marker in (
    'EntityEncounterNarrativeAuthority.selectedEntityKey',
    'EntityEncounterNarrativeAuthority.visibleFact',
    'EntityEncounterNarrativeAuthority.ensureReply',
    'encounterNarrativeFact(rolls)',
    'reply = ensureEncounterNarrative(rolls, reply);',
):
    if marker not in text:
        raise RuntimeError("Entity narrative authority marker missing: " + marker)

MAIN.write_text(text, encoding="utf-8")

# Replace the stale verifier assumption that the final force helper must read roamingEntityKey
# directly. The new contract is stronger: narration and combat must share one selector.
verify = VERIFY.read_text(encoding="utf-8")
stale_contract = '    (\'String entityKey = rolls.optString("roamingEntityKey", "").trim();\', java),\n'
new_contract = '''    ('EntityEncounterNarrativeAuthority.selectedEntityKey', java),
    ('EntityEncounterNarrativeAuthority.visibleFact', java),
    ('EntityEncounterNarrativeAuthority.ensureReply', java),
    ('String canonicalKey = encounterEntityKey(rolls);', java),
    ('encounterNarrativeFact(rolls)', java),
    ('reply = ensureEncounterNarrative(rolls, reply);', java),
'''
if new_contract not in verify:
    if verify.count(stale_contract) != 1:
        raise RuntimeError(f"stale Entity runtime verifier contract expected once, found {verify.count(stale_contract)}")
    verify = verify.replace(stale_contract, new_contract, 1)
VERIFY.write_text(verify, encoding="utf-8")

print("Entity encounter narration synchronized: one selected canonical Entity now drives GM visible fact, deterministic prose guard, overlay flag and CombatRuntime startup.")
