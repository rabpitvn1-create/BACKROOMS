package com.rabpit.backroom.core.canon

import android.content.Context
import com.rabpit.backroom.BuildConfig

/**
 * Feature flag 2 tầng cho Canon Runtime 2.0 (mapping §4):
 *
 * 1. Build-time: `BuildConfig.CANON_RUNTIME_2_ENABLED` — buildConfigField
 *    trong app/build.gradle, mặc định "false".
 * 2. Runtime override (để canary/test nội bộ không cần build lại):
 *    SharedPreferences "canon_runtime", key "cr2_enabled". Chỉ khi prefs
 *    CÓ key này mới override; không có key thì dùng giá trị build.
 *
 * Mặc định cả hai tầng đều OFF: đường cũ (prompt inline + KnowledgeContextEngine)
 * nguyên vẹn. Flag không liên quan save compatibility — bật/tắt không đổi
 * CURRENT_SAVE_VERSION, không migration. Không có UI bật flag cho người dùng.
 */
object CanonRuntime2Flags {
  const val PREFS_NAME = "canon_runtime"
  const val KEY_ENABLED = "cr2_enabled"

  @JvmStatic
  fun isEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return if (prefs.contains(KEY_ENABLED)) {
      prefs.getBoolean(KEY_ENABLED, false)
    } else {
      BuildConfig.CANON_RUNTIME_2_ENABLED
    }
  }
}
