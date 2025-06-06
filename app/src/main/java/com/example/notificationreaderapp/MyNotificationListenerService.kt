package com.example.notificationreaderapp // Make sure this matches your package

import android.content.BroadcastReceiver
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager // For sending local broadcasts

class MyNotificationListenerService : NotificationListenerService() {

    private val TAG = "NotificationListener"

    // In MyNotificationListenerService.kt
    companion object {
        const val ACTION_NOTIFICATION_POSTED = "com.example.notificationreaderapp.NOTIFICATION_POSTED"
        const val EXTRA_NOTIFICATION_DATA = "notification_data_parcelable"
        // --- NEW ACTION FOR FETCH COMMAND ---
        const val ACTION_REQUEST_FETCH_ACTIVE_NOTIFICATIONS = "com.example.notificationreaderapp.REQUEST_FETCH_ACTIVE_NOTIFICATIONS"
        // ---
        private const val TAG = "NotificationListener" // Assuming you have a TAG
    }

    // In MyNotificationListenerService.kt
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification Listener connected. Ready for fetch requests.")
        // No automatic fetch and broadcast of active notifications from here in this model.
        // User will trigger via button in MainActivity.
    }

    // In MyNotificationListenerService.kt
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn != null) {
            val packageName = sbn.packageName
            val extras = sbn.notification.extras
            val title = extras.getString("android.title")
            Log.i(TAG, "Notification Posted (Service - Not Broadcasting): Pkg: $packageName, Title: $title")
            // NO BROADCASTING FROM HERE ANYMORE for individual new notifications in this model
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn != null) {
            val packageName = sbn.packageName
            Log.i(TAG, "Notification Removed for package: $packageName")
            // TODO: You might want to update your UI if you're displaying this notification
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(TAG, "Notification Listener disconnected.")
    }

    // Optional helper function to process notifications already active when service connects
    // private fun processActiveNotifications(notifications: Array<StatusBarNotification>) {
    //     Log.i(TAG, "Processing ${notifications.size} active notifications.")
    //     for (sbn in notifications) {
    //         val packageName = sbn.packageName
    //         val notification = sbn.notification
    //         val extras = notification.extras
    //         val title = extras.getString("android.title")
    //         val text = extras.getCharSequence("android.text")?.toString()
    //
    //         Log.i(TAG, "Active Notification:")
    //         Log.i(TAG, "  Package: $packageName")
    //         Log.i(TAG, "  Title: $title")
    //         Log.i(TAG, "  Text: $text")
    //     }
    // }

    // In MyNotificationListenerService.kt

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_REQUEST_FETCH_ACTIVE_NOTIFICATIONS) {
                Log.i(TAG, "Received request to fetch active notifications.")
                fetchAndBroadcastActiveNotifications()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val intentFilter = IntentFilter(ACTION_REQUEST_FETCH_ACTIVE_NOTIFICATIONS)
        LocalBroadcastManager.getInstance(this).registerReceiver(commandReceiver, intentFilter)
        Log.i(TAG, "Command receiver registered in Service.")
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(commandReceiver)
        Log.i(TAG, "Command receiver unregistered in Service.")
    }

    private fun fetchAndBroadcastActiveNotifications() {
        val activeNotifications = getActiveNotifications() ?: return // getActiveNotifications can be null
        Log.i(TAG, "Found ${activeNotifications.size} active notifications to broadcast.")

        activeNotifications.forEach { sbn ->
            val packageName = sbn.packageName
            val extras = sbn.notification.extras
            val title = extras.getString("android.title")
            val text = extras.getCharSequence("android.text")?.toString()
            val postTime = sbn.postTime

            Log.i(TAG, "Broadcasting Active Notification: Pkg: $packageName, Title: $title")

            val notificationData = NotificationData(
                timestamp = postTime,
                packageName = packageName,
                title = title,
                text = text
            )

            // Use the SAME action that MainActivity is already listening for data
            val broadcastIntent = Intent(ACTION_NOTIFICATION_POSTED)
            broadcastIntent.putExtra(EXTRA_NOTIFICATION_DATA, notificationData)
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
        }
        if (activeNotifications.isEmpty()) {
            // Optional: Send a toast or a different kind of signal if no notifications found
            // For now, MainActivity will just not receive any new items.
            Log.i(TAG, "No active notifications found to broadcast.")
        }
    }

}
