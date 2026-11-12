package com.dymok.asciimedia

import android.content.Context

object Prefs {
    private const val PREFS_NAME = "ascii_media_prefs"
    private const val KEY_AMOLED_BG = "amoled_bg"
    private const val KEY_MONET_COVER = "monet_cover"
    private const val KEY_LAST_PACKAGE = "last_package"

    fun isAmoledBg(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AMOLED_BG, true)

    fun setAmoledBg(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AMOLED_BG, enabled).apply()
    }

    fun isMonetCover(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_MONET_COVER, true)

    fun setMonetCover(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_MONET_COVER, enabled).apply()
    }

    fun getLastPackage(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_PACKAGE, null)

    fun setLastPackage(context: Context, pkg: String?) {
        if (pkg.isNullOrBlank()) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_PACKAGE, pkg).apply()
    }
}
