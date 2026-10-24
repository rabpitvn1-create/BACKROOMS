package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class GameCoreFacadeContractTest {
  @Test fun legacySynchronizationKeepsCaoMinhOutOfFollowerAvatarList() {
    val migrated = LegacySaveMigration.migrate(org.json.JSONObject("""{"turn":1,"inventory":[],"party":[]}"""))
    assertEquals(listOf(CAO_MINH_ID), migrated.party.memberIds)
    assertEquals(CURRENT_SAVE_VERSION, migrated.saveVersion)
  }
}
