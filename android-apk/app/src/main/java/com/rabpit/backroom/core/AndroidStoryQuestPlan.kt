package com.rabpit.backroom.core

import android.content.Context

object AndroidStoryQuestPlan {
  private const val ASSET_PATH = "campaign_story/level0-to-level1-quests.json"

  fun load(context: Context): StoryQuestPlan = context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { reader ->
    StoryQuestPlan.parse(reader.readText())
  }
}
