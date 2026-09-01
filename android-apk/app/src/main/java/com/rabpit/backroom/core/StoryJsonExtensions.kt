package com.rabpit.backroom.core

import org.json.JSONObject

internal fun JSONObject.putNullable(key: String, value: Any?) {
  put(key, value ?: JSONObject.NULL)
}
