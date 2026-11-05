package com.bdsalocin.pixelisland.data.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MusicNotificationListenerService : NotificationListenerService() {

    companion object {
        var onNotificationChangeListener: (() -> Unit)? = null
        private var instance: MusicNotificationListenerService? = null

        fun hasActiveMediaNotification(packageName: String): Boolean {
            val service = instance ?: return true
            return try {
                val notifs = service.activeNotifications ?: return true
                notifs.any { it.packageName == packageName }
            } catch (_: Exception) {
                true
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        onNotificationChangeListener?.invoke()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        onNotificationChangeListener?.invoke()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        onNotificationChangeListener?.invoke()
    }
}
