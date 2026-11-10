package com.example.whitelistfw

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun allowed(c: Context): Set<String> =
        sp(c).getStringSet("allowed_apps", emptySet())!!.toSet()

    fun setAllowed(c: Context, s: Set<String>) =
        sp(c).edit().putStringSet("allowed_apps", s.toSet()).apply()

    fun enabled(c: Context): Boolean = sp(c).getBoolean("enabled", false)

    fun setEnabled(c: Context, v: Boolean) =
        sp(c).edit().putBoolean("enabled", v).apply()
}
