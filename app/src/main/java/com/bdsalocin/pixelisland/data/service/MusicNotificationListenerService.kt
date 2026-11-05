package com.bdsalocin.pixelisland.data.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MusicNotificationListenerService : NotificationListenerService() {

    companion object {
        var onNotificationChangeListener: (() -> Unit)? = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        onNotificationChangeListener?.invoke()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        onNotificationChangeListener?.invoke()
    }
}
