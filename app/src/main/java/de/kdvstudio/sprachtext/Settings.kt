package de.kdvstudio.sprachtext

import android.content.Context

object Settings {
    private const val PREFS = "settings"
    private const val KEY = "gemini_key"
    fun geminiKey(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
    fun setGeminiKey(ctx: Context, v: String) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, v.trim()).apply()
}
