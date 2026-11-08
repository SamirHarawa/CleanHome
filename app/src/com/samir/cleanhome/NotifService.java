package com.samir.cleanhome;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Lets the quick-settings panel list notifications, as Google TV's panel does. */
public class NotifService extends NotificationListenerService {
    static NotifService instance;

    @Override public void onListenerConnected() { instance = this; }
    @Override public void onListenerDisconnected() { instance = null; }

    static StatusBarNotification[] current() {
        try { return instance == null ? new StatusBarNotification[0] : instance.getActiveNotifications(); }
        catch (Exception e) { return new StatusBarNotification[0]; }
    }

    static void dismiss(String key) {
        try { if (instance != null) instance.cancelNotification(key); } catch (Exception ignored) { }
    }
}
