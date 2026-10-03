package com.saathi.assistant.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Only active once the user explicitly grants Notification Access in system
 * settings (Settings > Apps > Special access > Notification access). Reads
 * other apps' notifications so Saathi can answer "what notifications do I
 * have" / read the latest one aloud — it never posts on their behalf.
 */
class SaathiNotificationListenerService : NotificationListenerService() {

    data class CapturedNotification(val packageName: String, val title: String?, val text: String?, val postedAt: Long)

    companion object {
        private const val MAX_HISTORY = 50
        private val history = CopyOnWriteArrayList<CapturedNotification>()

        fun recent(limit: Int = 10): List<CapturedNotification> = history.takeLast(limit)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val captured = CapturedNotification(
            packageName = sbn.packageName,
            title = extras.getCharSequence("android.title")?.toString(),
            text = extras.getCharSequence("android.text")?.toString(),
            postedAt = sbn.postTime
        )
        history.add(captured)
        while (history.size > MAX_HISTORY) {
            history.removeAt(0)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // No-op: we only keep a rolling read history, nothing to clean up per-notification.
    }
}
