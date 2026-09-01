package com.rabpit.backroom.core

import org.json.JSONArray
import org.json.JSONObject

enum class StoryProgressStatus { ACTIVE, COMPLETED }
enum class StoryObjectiveConditionType { EVIDENCE_ANY, LEVEL_ESCAPED, ENTER_AREA }

data class StoryObjectiveCondition(
  val type: StoryObjectiveConditionType,
  val ids: Set<String> = emptySet(),
  val levelId: String? = null,
  val areaId: String? = null
)

data class StoryObjectiveDefinition(
  val id: String,
  val title: String,
  val completion: StoryObjectiveCondition
)

data class StoryQuestDefinition(
  val id: String,
  val title: String,
  val objectives: List<StoryObjectiveDefinition>
)

data class StoryActDefinition(
  val id: String,
  val title: String,
  val quests: List<StoryQuestDefinition>
)

data class StoryChapterDefinition(
  val id: String,
  val title: String,
  val act: StoryActDefinition
)

data class StoryQuestPlan(
  val planId: String,
  val storyId: String,
  val campaignId: String,
  val chapter: StoryChapterDefinition
) {
  data class Cursor(
    val chapter: StoryChapterDefinition,
    val act: StoryActDefinition,
    val quest: StoryQuestDefinition,
    val objective: StoryObjectiveDefinition
  )

  val cursors: List<Cursor> = chapter.act.quests.flatMap { quest ->
    quest.objectives.map { objective -> Cursor(chapter, chapter.act, quest, objective) }
  }

  init {
    require(planId.isNotBlank()) { "story_plan_id_missing" }
    require(storyId.isNotBlank()) { "story_id_missing" }
    require(campaignId.isNotBlank()) { "campaign_id_missing" }
    require(cursors.isNotEmpty()) { "story_objectives_missing" }
    require(cursors.map { it.objective.id }.distinct().size == cursors.size) { "duplicate_story_objective" }
    require(chapter.act.quests.map { it.id }.distinct().size == chapter.act.quests.size) { "duplicate_story_quest" }
  }

  fun cursorFor(story: StoryState): Cursor? = cursors.firstOrNull {
    it.chapter.id == story.chapterId &&
      it.act.id == story.actId &&
      it.quest.id == story.questId &&
      it.objective.id == story.objectiveId
  }

  companion object {
    fun parse(raw: String): StoryQuestPlan {
      val root = JSONObject(raw)
      val chapterJson = root.getJSONObject("chapter")
      val actJson = chapterJson.getJSONObject("act")
      val questsJson = actJson.getJSONArray("quests")
      val quests = buildList {
        for (questIndex in 0 until questsJson.length()) {
          val questJson = questsJson.getJSONObject(questIndex)
          val objectivesJson = questJson.getJSONArray("objectives")
          val objectives = buildList {
            for (objectiveIndex in 0 until objectivesJson.length()) {
              val objectiveJson = objectivesJson.getJSONObject(objectiveIndex)
              val completionJson = objectiveJson.getJSONObject("completion")
              val type = StoryObjectiveConditionType.valueOf(completionJson.getString("type"))
              val ids = linkedSetOf<String>()
              val idsJson = completionJson.optJSONArray("ids")
              if (idsJson != null) {
                for (index in 0 until idsJson.length()) ids += idsJson.getString(index)
              }
              add(
                StoryObjectiveDefinition(
                  id = objectiveJson.getString("id"),
                  title = objectiveJson.getString("title"),
                  completion = StoryObjectiveCondition(
                    type = type,
                    ids = ids,
                    levelId = completionJson.optString("levelId").takeIf { it.isNotBlank() },
                    areaId = completionJson.optString("areaId").takeIf { it.isNotBlank() }
                  )
                )
              )
            }
          }
          require(objectives.isNotEmpty()) { "story_quest_objectives_missing" }
          add(StoryQuestDefinition(questJson.getString("id"), questJson.getString("title"), objectives))
        }
      }
      require(quests.isNotEmpty()) { "story_quests_missing" }
      return StoryQuestPlan(
        planId = root.getString("planId"),
        storyId = root.getString("storyId"),
        campaignId = root.getString("campaignId"),
        chapter = StoryChapterDefinition(
          id = chapterJson.getString("id"),
          title = chapterJson.getString("title"),
          act = StoryActDefinition(
            id = actJson.getString("id"),
            title = actJson.getString("title"),
            quests = quests
          )
        )
      )
    }
  }
}

data class StoryState(
  val planId: String = "QUEST_PLAN_LEVEL0_TO_LEVEL1_R01",
  val storyId: String = "MAIN_LEVEL0_TO_LEVEL1_R01",
  val campaignId: String = "BACKROOMS_FANDOM_LEVELS_0_6_R01",
  val chapterId: String? = "CHAPTER_01_ASYNC_INVESTIGATION",
  val actId: String? = "ACT_01_SEPARATED_IN_BACKROOMS",
  val questId: String? = "QUEST_01_READ_LEVEL_ZERO",
  val objectiveId: String? = "OBJ_01_VERIFY_LAYOUT_ANOMALY",
  val status: StoryProgressStatus = StoryProgressStatus.ACTIVE,
  val completedObjectiveIds: Set<String> = emptySet(),
  val completedQuestIds: Set<String> = emptySet(),
  val completedActIds: Set<String> = emptySet(),
  val completedChapterIds: Set<String> = emptySet(),
  val lastObservedAreaId: String? = null,
  val revision: Int = 0
) {
  companion object { fun initial(): StoryState = StoryState() }
}

data class StorySignal(
  val areaId: String? = null,
  val evidenceIds: Set<String> = emptySet(),
  val escapedLevelId: String? = null
)

class StoryQuestEngine(private val plan: StoryQuestPlan) {
  fun normalize(story: StoryState): StoryState {
    if (story.status == StoryProgressStatus.COMPLETED) return story
    if (story.planId == plan.planId && plan.cursorFor(story) != null) return story
    val first = plan.cursors.first()
    return StoryState(
      planId = plan.planId,
      storyId = plan.storyId,
      campaignId = plan.campaignId,
      chapterId = first.chapter.id,
      actId = first.act.id,
      questId = first.quest.id,
      objectiveId = first.objective.id
    )
  }

  /**
   * Core-only progression. A single committed signal can complete at most one objective,
   * so a model cannot skip intermediate objectives by presenting a future area/state in one turn.
   */
  fun applySignal(state: GameState, signal: StorySignal): GameState {
    val normalized = normalize(state.story)
    val areaChanged = signal.areaId != null && signal.areaId != normalized.lastObservedAreaId
    val observed = normalized.copy(lastObservedAreaId = signal.areaId ?: normalized.lastObservedAreaId)
    if (observed.status == StoryProgressStatus.COMPLETED) {
      return if (observed == state.story) state else state.copy(story = observed)
    }

    val cursor = plan.cursorFor(observed) ?: return state.copy(story = observed)
    if (!conditionMet(cursor.objective.completion, signal, areaChanged)) {
      return if (observed == state.story) state else state.copy(story = observed)
    }

    val completedObjectives = observed.completedObjectiveIds + cursor.objective.id
    val questFinished = cursor.quest.objectives.last().id == cursor.objective.id
    val completedQuests = if (questFinished) observed.completedQuestIds + cursor.quest.id else observed.completedQuestIds
    val actFinished = questFinished && cursor.act.quests.last().id == cursor.quest.id
    val completedActs = if (actFinished) observed.completedActIds + cursor.act.id else observed.completedActIds
    val chapterFinished = actFinished
    val completedChapters = if (chapterFinished) observed.completedChapterIds + cursor.chapter.id else observed.completedChapterIds

    val currentIndex = plan.cursors.indexOfFirst { it.objective.id == cursor.objective.id }
    val next = plan.cursors.getOrNull(currentIndex + 1)
    val advanced = if (next == null) {
      observed.copy(
        chapterId = null,
        actId = null,
        questId = null,
        objectiveId = null,
        status = StoryProgressStatus.COMPLETED,
        completedObjectiveIds = completedObjectives,
        completedQuestIds = completedQuests,
        completedActIds = completedActs,
        completedChapterIds = completedChapters,
        revision = observed.revision + 1
      )
    } else {
      observed.copy(
        chapterId = next.chapter.id,
        actId = next.act.id,
        questId = next.quest.id,
        objectiveId = next.objective.id,
        completedObjectiveIds = completedObjectives,
        completedQuestIds = completedQuests,
        completedActIds = completedActs,
        completedChapterIds = completedChapters,
        revision = observed.revision + 1
      )
    }
    return state.copy(story = advanced)
  }

  private fun conditionMet(condition: StoryObjectiveCondition, signal: StorySignal, areaChanged: Boolean): Boolean = when (condition.type) {
    StoryObjectiveConditionType.EVIDENCE_ANY -> condition.ids.isNotEmpty() && signal.evidenceIds.any(condition.ids::contains)
    StoryObjectiveConditionType.LEVEL_ESCAPED -> signal.escapedLevelId != null && signal.escapedLevelId == condition.levelId
    StoryObjectiveConditionType.ENTER_AREA -> areaChanged && signal.areaId != null && signal.areaId == condition.areaId
  }
}

object StoryStateJson {
  fun encode(story: StoryState): JSONObject = JSONObject().apply {
    put("planId", story.planId)
    put("storyId", story.storyId)
    put("campaignId", story.campaignId)
    putNullable("chapterId", story.chapterId)
    putNullable("actId", story.actId)
    putNullable("questId", story.questId)
    putNullable("objectiveId", story.objectiveId)
    put("status", story.status.name)
    put("completedObjectiveIds", JSONArray(story.completedObjectiveIds.toList()))
    put("completedQuestIds", JSONArray(story.completedQuestIds.toList()))
    put("completedActIds", JSONArray(story.completedActIds.toList()))
    put("completedChapterIds", JSONArray(story.completedChapterIds.toList()))
    putNullable("lastObservedAreaId", story.lastObservedAreaId)
    put("revision", story.revision)
  }

  fun decode(json: JSONObject): StoryState = StoryState(
    planId = json.optString("planId", StoryState.initial().planId),
    storyId = json.optString("storyId", StoryState.initial().storyId),
    campaignId = json.optString("campaignId", StoryState.initial().campaignId),
    chapterId = nullable(json, "chapterId"),
    actId = nullable(json, "actId"),
    questId = nullable(json, "questId"),
    objectiveId = nullable(json, "objectiveId"),
    status = runCatching { StoryProgressStatus.valueOf(json.optString("status", StoryProgressStatus.ACTIVE.name)) }.getOrDefault(StoryProgressStatus.ACTIVE),
    completedObjectiveIds = strings(json.optJSONArray("completedObjectiveIds")),
    completedQuestIds = strings(json.optJSONArray("completedQuestIds")),
    completedActIds = strings(json.optJSONArray("completedActIds")),
    completedChapterIds = strings(json.optJSONArray("completedChapterIds")),
    lastObservedAreaId = nullable(json, "lastObservedAreaId"),
    revision = json.optInt("revision", 0).coerceAtLeast(0)
  )

  fun visible(plan: StoryQuestPlan, story: StoryState): JSONObject {
    val normalized = StoryQuestEngine(plan).normalize(story)
    val cursor = plan.cursorFor(normalized)
    return JSONObject().apply {
      put("status", normalized.status.name)
      put("chapterId", normalized.chapterId ?: JSONObject.NULL)
      put("chapterTitle", cursor?.chapter?.title ?: "")
      put("actId", normalized.actId ?: JSONObject.NULL)
      put("actTitle", cursor?.act?.title ?: "")
      put("questId", normalized.questId ?: JSONObject.NULL)
      put("questTitle", cursor?.quest?.title ?: "")
      put("objectiveId", normalized.objectiveId ?: JSONObject.NULL)
      put("objectiveTitle", cursor?.objective?.title ?: "")
      put("revision", normalized.revision)
    }
  }

  private fun nullable(json: JSONObject, key: String): String? =
    if (!json.has(key) || json.isNull(key)) null else json.optString(key).takeIf { it.isNotBlank() }

  private fun strings(array: JSONArray?): Set<String> {
    if (array == null) return emptySet()
    val result = linkedSetOf<String>()
    for (index in 0 until array.length()) array.optString(index).takeIf { it.isNotBlank() }?.let(result::add)
    return result
  }
}
