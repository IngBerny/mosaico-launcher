package com.ingberny.mosaico

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
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

        /**
         * Mensajes recientes por app. En apps de chat (WhatsApp, Telegram, Messenger…) se leen los
         * mensajes individuales del estilo "MessagingStyle"; en las demás, título y texto.
         */
        fun previews(ctx: Context): Map<String, List<Preview>> {
            val out = HashMap<String, MutableList<Preview>>()
            for (sbn in active()) {
                val n = sbn.notification
                if (n.visibility == Notification.VISIBILITY_SECRET) continue
                val ex = n.extras
                val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                val convo = ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
                val avatar = try { n.getLargeIcon()?.loadDrawable(ctx) } catch (e: Exception) { null }
                val list = out.getOrPut(sbn.packageName) { mutableListOf() }
                @Suppress("DEPRECATION")
                val msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
                if (msgs != null && msgs.isNotEmpty()) {
                    for (m in msgs) {
                        val b = m as? Bundle ?: continue
                        val text = b.getCharSequence("text")?.toString() ?: continue
                        @Suppress("DEPRECATION")
                        val person = if (Build.VERSION.SDK_INT >= 28) b.getParcelable<android.app.Person>("sender_person") else null
                        val sender = person?.name?.toString() ?: b.getCharSequence("sender")?.toString() ?: title
                        val who = if (convo != null && convo != sender) "$sender · $convo" else sender
                        list += Preview(who, text, b.getLong("time", sbn.postTime), avatar)
                    }
                } else {
                    val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
                    if (title.isNotEmpty() || text.isNotEmpty()) list += Preview(title, text, sbn.postTime, avatar)
                }
            }
            return out.mapValues { (_, v) -> v.sortedByDescending { it.time }.take(6) }.filterValues { it.isNotEmpty() }
        }

        fun dismiss(key: String) { try { instance?.cancelNotification(key) } catch (e: Exception) { } }
        fun dismissAll() { try { instance?.cancelAllNotifications() } catch (e: Exception) { } }
    }

    override fun onListenerConnected() { instance = this; changed() }
    override fun onListenerDisconnected() { instance = null; changed() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { changed() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { changed() }
}
