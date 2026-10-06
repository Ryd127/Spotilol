package com.project.lol.mediaprobe;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public final class ProbeNotificationListener extends NotificationListenerService {
    private static volatile ProbeNotificationListener instance;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
    }

    @Override
    public void onListenerDisconnected() {
        if (instance == this) {
            instance = null;
        }
        super.onListenerDisconnected();
    }

    public static StatusBarNotification[] currentNotifications() {
        ProbeNotificationListener service = instance;
        if (service == null) {
            return null;
        }
        try {
            return service.getActiveNotifications();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
