from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
FACADE = CORE / "GameCoreFacade.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

facade = FACADE.read_text(encoding="utf-8")
main = MAIN.read_text(encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# Do not change the save schema. DiscoveryProjection is a read-only projection over the canonical
# LevelInstance. Hidden supports/facts/conditions/blueprint data never enter the JSON returned here.
(CORE / "DiscoveryProjection.kt").write_text(r'''package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Player/narrator-safe discovery view.
 * Canonical EvidenceState may support hidden facts, but consumers outside Core receive only
 * already-discovered presentation text plus explicit inference boundaries.
 */
object DiscoveryProjection {
  private const val OBSERVED_ONLY = "OBSERVED_DETAIL_ONLY"

  fun build(
    state: GameState,
    definition: LevelDefinition?,
    surfacedEvidenceIds: Set<String>,
    action: String
  ): JSONObject {
    val level = state.levelInstance ?: return emptyProjection()
    val evidenceOut = JSONArray()

    surfacedEvidenceIds.sorted().forEach { id ->
      val evidence = level.evidence[id] ?: return@forEach
      if (!evidence.discovered) return@forEach
      val text = visibleText(level, definition, id) ?: return@forEach
      evidenceOut.put(JSONObject()
        .put("text", text)
        .put("sources", JSONArray(evidence.sources.map { it.name }.sorted()))
        .put("meaningScope", OBSERVED_ONLY)
        .put("mayConcludeEscapeRoute", false)
        .put("mayConcludeRequiredAction", false)
        .put("mayRevealHiddenFact", false))
    }

    val allowedNpcStatements = JSONArray()
    val normalizedAction = normalize(action)
    level.npcKnowledge.toSortedMap().forEach { (npcId, factIds) ->
      if (!mentionsActor(normalizedAction, npcId)) return@forEach
      level.evidence.values
        .asSequence()
        .filter { it.discovered && it.supports.any(factIds::contains) }
        .mapNotNull { visibleText(level, definition, it.id) }
        .distinct()
        .sorted()
        .forEach(allowedNpcStatements::put)
    }

    return JSONObject()
      .put("evidence", evidenceOut)
      .put("allowedNpcStatements", allowedNpcStatements)
      .put("inferencePolicy", JSONObject()
        .put("evidenceIsObservationNotSolution", true)
        .put("npcMayUseOnlyAllowedStatements", true)
        .put("hiddenTruthUnavailable", true))
  }

  private fun emptyProjection() = JSONObject()
    .put("evidence", JSONArray())
    .put("allowedNpcStatements", JSONArray())
    .put("inferencePolicy", JSONObject()
      .put("evidenceIsObservationNotSolution", true)
      .put("npcMayUseOnlyAllowedStatements", true)
      .put("hiddenTruthUnavailable", true))

  private fun visibleText(level: LevelInstanceState, definition: LevelDefinition?, evidenceId: String): String? =
    (level.replies["evidence:$evidenceId"] ?: definition?.replies?.get("evidence:$evidenceId"))
      ?.trim()?.takeIf(String::isNotEmpty)

  private fun mentionsActor(action: String, npcId: String): Boolean {
    val actor = normalize(npcId)
    if (actor.isBlank()) return false
    return actor in action || actor.split(' ').filter(String::isNotBlank).all(action::contains)
  }

  private fun normalize(value: String): String = value.lowercase()
    .replace('_', ' ').replace('-', ' ')
    .replace(Regex("[^\\p{L}\\p{N} ]+"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()
}
''', encoding="utf-8")

(TESTS / "DiscoveryProjectionTest.kt").write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class DiscoveryProjectionTest {
  private fun definition(): LevelDefinition {
    val evidence = mapOf(
      "surface" to EvidenceState(
        id = "surface",
        supports = setOf("hidden.escape.fact"),
        sources = setOf(EvidenceSource.SURVIVOR, EvidenceSource.ENVIRONMENT),
        discovered = true,
        discoverConditions = setOf("fact:hidden.escape.fact")
      ),
      "future" to EvidenceState(
        id = "future",
        supports = setOf("future.secret"),
        sources = setOf(EvidenceSource.SEARCH),
        discovered = false
      )
    )
    return LevelDefinition(
      id = "test",
      name = "Test",
      initialZoneId = "z",
      zones = mapOf("z" to ZoneState("z", "Zone", tags = setOf("escape"))),
      escapeBlueprint = EscapeBlueprintState("secret.solution", setOf("hidden.escape.fact"), listOf("act")),
      evidence = evidence,
      npcKnowledge = mapOf("survivor_17" to setOf("hidden.escape.fact", "future.secret")),
      actions = mapOf("act" to LevelActionRule("act", listOf(setOf("act")), effects = listOf(LevelEffect(LevelEffectType.COMPLETE_LEVEL)))),
      replies = mapOf(
        "evidence:surface" to "Người sống sót nhớ rằng tiếng đèn đổi nhịp ở đoạn hành lang này.",
        "evidence:future" to "Một dấu hiệu chưa được phát hiện."
      )
    )
  }

  @Test fun projectionExposesOnlyDiscoveredPresentationAndInferenceBoundary() {
    val d = definition()
    val level = LevelInstanceState(
      runSeed = "r", levelId = d.id, generationId = "g", currentZoneId = "z",
      zones = d.zones, escapeBlueprint = d.escapeBlueprint,
      evidence = d.evidence, npcKnowledge = d.npcKnowledge, actions = d.actions, replies = d.replies
    )
    val state = GameState.initial().copy(levelInstance = level)
    val json = DiscoveryProjection.build(state, d, setOf("surface", "future"), "hỏi survivor 17")
    val raw = json.toString()

    assertTrue(raw.contains("Người sống sót nhớ rằng tiếng đèn đổi nhịp"))
    assertTrue(raw.contains("OBSERVED_DETAIL_ONLY"))
    assertTrue(raw.contains("allowedNpcStatements"))
    assertFalse(raw.contains("hidden.escape.fact"))
    assertFalse(raw.contains("future.secret"))
    assertFalse(raw.contains("secret.solution"))
    assertFalse(raw.contains("discoverConditions"))
    assertFalse(raw.contains("supports"))
    assertFalse(raw.contains("Một dấu hiệu chưa được phát hiện"))
  }

  @Test fun npcKnowledgeIsNotProjectedForUnmentionedActor() {
    val d = definition()
    val level = LevelInstanceState(
      runSeed = "r", levelId = d.id, generationId = "g", currentZoneId = "z",
      zones = d.zones, escapeBlueprint = d.escapeBlueprint,
      evidence = d.evidence, npcKnowledge = d.npcKnowledge, actions = d.actions, replies = d.replies
    )
    val state = GameState.initial().copy(levelInstance = level)
    val json = DiscoveryProjection.build(state, d, setOf("surface"), "quan sát bức tường")
    assertEquals(0, json.getJSONArray("allowedNpcStatements").length())
  }
}
''', encoding="utf-8")

facade_anchor = '''      .put("evidenceIds", JSONArray(result.evidenceIds.sorted()))
      .put("evidenceTexts", surfacedEvidence)
      .toString()
'''
facade_replacement = '''      .put("evidenceIds", JSONArray(result.evidenceIds.sorted()))
      .put("evidenceTexts", surfacedEvidence)
      .put("discoveryProjection", DiscoveryProjection.build(
        result.state,
        levelRegistry.get(levelId),
        result.evidenceIds,
        action
      ))
      .toString()
'''
facade = replace_once(facade, facade_anchor, facade_replacement, "registered discovery projection")

visible_anchor = '''        .put("narrativeCue", cue)
        .put("evidenceIds", evidenceIds)
        .put("evidenceTexts", evidenceTexts);
'''
visible_replacement = '''        .put("narrativeCue", cue)
        .put("evidenceIds", evidenceIds)
        .put("evidenceTexts", evidenceTexts)
        .put("discoveryProjection", resolved.optJSONObject("discoveryProjection") != null
          ? resolved.optJSONObject("discoveryProjection") : new JSONObject());
'''
main = replace_once(main, visible_anchor, visible_replacement, "narrative visible discovery projection")

prompt_line = '        + "Không được diễn giải hoặc chép lại evidenceTexts trong reply vì ứng dụng sẽ gắn nguyên văn chúng sau phần kể. "\n'
prompt_insert = prompt_line + '''        + "discoveryProjection chỉ cho biết ý nghĩa được phép: evidence là quan sát, không phải lời giải; không được suy ra đường thoát, bước bắt buộc hay hidden fact. "
        + "Nếu kể lời NPC, NPC chỉ được dùng nội dung trong allowedNpcStatements; nếu mảng rỗng thì không cho NPC tiết lộ thông tin puzzle. "
'''
main = replace_once(main, prompt_line, prompt_insert, "discovery semantics narration contract")

for forbidden in ("supports", "discoverConditions", "requiredFacts", "requiredActions", "solutionId", "escapeBlueprint"):
    projection_source = (CORE / "DiscoveryProjection.kt").read_text(encoding="utf-8")
    if f'.put("{forbidden}"' in projection_source:
        raise RuntimeError("hidden discovery field exposed: " + forbidden)

for marker in (
    "DiscoveryProjection.build(",
    '.put("discoveryProjection",',
):
    if marker not in facade:
        raise RuntimeError("facade discovery boundary missing: " + marker)
for marker in (
    'put("discoveryProjection", resolved.optJSONObject("discoveryProjection")',
    "NPC chỉ được dùng nội dung trong allowedNpcStatements",
):
    if marker not in main:
        raise RuntimeError("narrative discovery boundary missing: " + marker)

FACADE.write_text(facade, encoding="utf-8")
MAIN.write_text(main, encoding="utf-8")
print("Discovery/NPC knowledge boundary applied: hidden evidence semantics stay canonical; narrator sees only discovered presentation plus explicit inference limits and actor-scoped visible statements.")
