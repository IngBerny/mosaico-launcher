package com.ingberny.mosaico

import android.graphics.Bitmap

/** Lo que el Centro de actividades y la pantalla de bloqueo necesitan de la actividad que los contiene. */
interface TileHost {
    val repo: AppRepo
    val prefs: Prefs
    val palette: Palette
    val live: LiveState
    fun backdrop(): Bitmap?
}
