package com.ositos.fitness.notifications

import android.content.Context

/** Qué recordatorios quiere recibir cada uno (se eligen en el Perfil; quedan en el celu). */
object Reminders {
    const val WATER = "water_on"
    const val MEALS = "meals_on"
    const val STORIES = "stories_on"

    private fun prefs(context: Context) = context.getSharedPreferences("worker", Context.MODE_PRIVATE)

    fun isOn(context: Context, key: String): Boolean = prefs(context).getBoolean(key, true)

    fun set(context: Context, key: String, on: Boolean) {
        prefs(context).edit().putBoolean(key, on).apply()
    }
}
