package com.ingberny.mosaico

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Acciones que se pueden asignar a gestos (deslizar en Inicio, doble toque, tocar la barra de estado…),
 * como en los launchers tipo Nova. Los ids se guardan en las preferencias: no cambiar su número.
 */
object SysActions {
    const val ACTION_CENTER = 0
    const val ANDROID_PANEL = 1
    const val ANDROID_QUICK = 2
    const val APPS = 3
    const val SEARCH = 4
    const val LOCK = 5
    const val RECENTS = 6
    const val SETTINGS = 7
    const val NONE = 8

    /** Nombres en el mismo orden que los ids. */
    val NAMES = listOf(
        "Centro de actividades de Mosaico",
        "Panel de notificaciones de Android",
        "Ajustes rápidos de Android",
        "Lista de apps",
        "Buscar",
        "Bloquear pantalla",
        "Apps recientes",
        "Personalizar Mosaico",
        "No hacer nada",
    )

    /** Extra con el que se pide a MainActivity abrir la lista de apps o la búsqueda. */
    const val EXTRA_OPEN = "mosaico_open"

    /**
     * Ejecuta una acción desde cualquier contexto (servicio de accesibilidad o actividad).
     * Las acciones propias de Inicio abren Mosaico con el extra correspondiente.
     */
    fun run(ctx: Context, id: Int) {
        when (id) {
            ACTION_CENTER -> start(ctx, Intent(ctx, ActionCenterActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
            APPS -> start(ctx, Intent(ctx, MainActivity::class.java).putExtra(EXTRA_OPEN, "apps"))
            SEARCH -> start(ctx, Intent(ctx, MainActivity::class.java).putExtra(EXTRA_OPEN, "search"))
            SETTINGS -> start(ctx, Intent(ctx, SettingsActivity::class.java))
            else -> runSystem(ctx, id)
        }
    }

    /** Acciones del sistema. Devuelve false si no se pudo (por ejemplo, sin el servicio de accesibilidad). */
    fun runSystem(ctx: Context, id: Int): Boolean {
        val svc = NavService.instance
        return when (id) {
            ANDROID_PANEL -> svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                ?: statusBar(ctx, "expandNotificationsPanel")
            ANDROID_QUICK -> svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                ?: statusBar(ctx, "expandSettingsPanel")
            LOCK -> svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) ?: needService(ctx)
            RECENTS -> svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) ?: needService(ctx)
            NONE -> true
            else -> false
        }
    }

    fun screenshot(ctx: Context): Boolean =
        NavService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) ?: needService(ctx)

    private fun statusBar(ctx: Context, method: String): Boolean = try {
        @Suppress("WrongConstant")
        val sb = ctx.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod(method).invoke(sb)
        true
    } catch (e: Throwable) { false }

    private fun needService(ctx: Context): Boolean {
        Toast.makeText(ctx, "Activa el \"Tema de Mosaico\" en Accesibilidad para usar esta acción", Toast.LENGTH_LONG).show()
        return false
    }

    private fun start(ctx: Context, i: Intent) {
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
    }
}
