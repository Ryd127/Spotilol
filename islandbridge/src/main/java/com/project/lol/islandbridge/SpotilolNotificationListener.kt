package com.project.lol.islandbridge

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class SpotilolNotificationListener : NotificationListenerService() {

    private var activeSourceKey: String? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeNotifications
            ?.asSequence()
            ?.filter { it.packageName == BridgeNotificationCaster.SOURCE_PACKAGE }
            ?.mapNotNull { sbn ->
                val source = BridgeNotificationCaster.fromSourceNotification(this, sbn.notification)
                if (source != null) sbn to source else null
            }
            ?.lastOrNull()
            ?.let { (sbn, source) ->
                activeSourceKey = sbn.key
                BridgeNotificationCaster.post(this, source)
            }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != BridgeNotificationCaster.SOURCE_PACKAGE) return
        val source = BridgeNotificationCaster.fromSourceNotification(this, sbn.notification) ?: return
        activeSourceKey = sbn.key
        BridgeNotificationCaster.post(this, source)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.key != activeSourceKey) return
        activeSourceKey = null
        BridgeNotificationCaster.cancel(this)
    }

    override fun onListenerDisconnected() {
        activeSourceKey = null
        BridgeNotificationCaster.cancel(this)
        super.onListenerDisconnected()
    }
}
