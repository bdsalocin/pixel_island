package com.bdsalocin.pixelisland.data.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MusicNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Le service est actif pour autoriser MediaSessionManager à accéder aux sessions
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Notification fermée
    }
}
