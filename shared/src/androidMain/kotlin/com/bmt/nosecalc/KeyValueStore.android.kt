package com.bmt.nosecalc

import android.content.Context

actual class KeyValueStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    actual fun get(key: String): String? = prefs.getString(key, null)

    actual fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
