package com.aster.boost

import android.content.Context

class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("aster_boost", Context.MODE_PRIVATE)

    fun save(config: String) {
        prefs.edit().putString("wg_config", config).apply()
    }

    fun load(): String? = prefs.getString("wg_config", null)
}
