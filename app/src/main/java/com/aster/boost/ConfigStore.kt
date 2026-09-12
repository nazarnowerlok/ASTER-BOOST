package com.aster.boost

import android.content.Context

class ConfigStore(context: Context) {
    // Deliberately separate from the old 1.x preferences so a broken/old config
    // cannot silently carry into the clean 2.0 rebuild.
    private val prefs = context.getSharedPreferences("aster_boost_v2", Context.MODE_PRIVATE)

    fun save(config: String) {
        prefs.edit().putString("wg_config", config).apply()
    }

    fun load(): String? = prefs.getString("wg_config", null)

    fun clear() {
        prefs.edit().remove("wg_config").apply()
    }
}
