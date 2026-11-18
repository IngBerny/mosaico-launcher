package com.ingberny.mosaico

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Lee las notificaciones para los contadores de los mosaicos y el Centro de actividades. */
class NotifService : NotificationListenerService() {

    companion object {
        @Volatile var instance: NotifService? = null
        private val main = Handler(Looper.getMainLooper())
        private val listeners = mutableListOf<() -> Unit>()

        fun addListener(l: () -> Unit) { listeners += l }
        fun removeListener(l: () -> Unit) { listeners -= l }
        private fun changed() { main.post { listeners.toList().forEach { it() } } }

        fun hasAccess(ctx: Context): Boolean = try {
            ctx.getSystemService(NotificationManager::class.java)
                .isNotificationListenerAccessGranted(ComponentName(ctx, NotifService::class.java))
        } catch (e: Exception) { false }

        fun active(): List<StatusBarNotification> = try {
            instance?.activeNotifications?.filter {
                it.isClearable && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }

        fun counts(): Map<String, Int> = active().groupingBy { it.packageName }.eachCount()

        fun dismiss(key: String) { try { instance?.cancelNotification(key) } catch (e: Exception) { } }
        fun dismissAll() { try { instance?.cancelAllNotifications() } catch (e: Exception) { } }
    }

    override fun onListenerConnected() { instance = this; changed() }
    override fun onListenerDisconnected() { instance = null; changed() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { changed() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { changed() }
}
