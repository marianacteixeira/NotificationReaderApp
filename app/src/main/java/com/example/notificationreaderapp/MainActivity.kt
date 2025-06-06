package com.example.notificationreaderapp // Make sure this matches your package

import androidx.recyclerview.widget.LinearLayoutManager // For RecyclerView
import androidx.recyclerview.widget.RecyclerView         // For RecyclerView
import android.content.ComponentName
import android.text.TextUtils
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent // Already likely there
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.os.Build // For the new getParcelableExtra method
import android.util.Log // For logging in MainActivity
import android.widget.Button // Already likely there
import android.widget.Toast // Already likely there
import androidx.appcompat.app.AppCompatActivity // Already there
import android.os.Bundle // Already there
import android.provider.Settings // Already there
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import java.io.OutputStream

import kotlinx.serialization.encodeToString // For saving
import kotlinx.serialization.decodeFromString // For loading
import kotlinx.serialization.json.Json // The Json format instance
import java.io.File // For file operations
import java.io.IOException // For exception handling


class MainActivity : AppCompatActivity() {

    // Constants used to check Notification Listener permission
    private val enabledNotificationListeners = "enabled_notification_listeners"
    private val actionNotificationListenerSettings = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
    private val notificationList = mutableListOf<NotificationData>() // To store received notifications
    private lateinit var notificationAdapter: NotificationAdapter // Use lateinit
    private lateinit var notificationsRecyclerView: RecyclerView  // Use lateinit

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("MainActivity_Receiver", "onReceive CALLED. Action: ${intent?.action}")

            if (intent?.action == MyNotificationListenerService.ACTION_NOTIFICATION_POSTED) {
                Log.d("MainActivity_Receiver", "Action MATCHED: ${MyNotificationListenerService.ACTION_NOTIFICATION_POSTED}")

                val data: NotificationData? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(MyNotificationListenerService.EXTRA_NOTIFICATION_DATA, NotificationData::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(MyNotificationListenerService.EXTRA_NOTIFICATION_DATA)
                }
                Log.d("MainActivity_Receiver", "Parcelable data extracted: $data")

                data?.let { receivedNotificationData ->
                    // Add to the top of the list (newest first)
                    notificationList.add(0, receivedNotificationData)
                    // Notify the adapter that an item was inserted at position 0
                    notificationAdapter.notifyItemInserted(0)
                    // Optional: Scroll to the top to show the new item
                    notificationsRecyclerView.scrollToPosition(0)

                    Log.d("MainActivity_Receiver", "SUCCESS: Received notification: ${receivedNotificationData.title}, List size: ${notificationList.size}")

                }
            } else {
                Log.w("MainActivity_Receiver", "Action MISMATCH or null. Actual action: ${intent?.action}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("MainActivity", "onCreate called.") // Keep or adjust logging
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. LOAD DATA FIRST
        loadNotificationList() // <--- This populates 'notificationList'

        // --- Permission Button ---
        val enableButton: Button = findViewById(R.id.buttonEnablePermission)
        enableButton.setOnClickListener {
            if (!isNotificationServiceEnabled()) {
                startActivity(Intent(actionNotificationListenerSettings))
            } else {
                Toast.makeText(this, "Permission already granted", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. NOW SETUP RECYCLERVIEW (it will use the 'notificationList' populated by loadNotificationList())
        setupRecyclerView()

        // --- Action Buttons ---
        val fetchButton: Button = findViewById(R.id.buttonFetchNotifications)
        fetchButton.setOnClickListener {
            Log.d("MainActivity", "Fetch Notifications button clicked. Sending request to service.")
            Toast.makeText(this, "Requesting active notifications...", Toast.LENGTH_SHORT).show()

            // Send a broadcast TO the service to request a fetch
            val fetchIntent = Intent(MyNotificationListenerService.ACTION_REQUEST_FETCH_ACTIVE_NOTIFICATIONS)
            LocalBroadcastManager.getInstance(this).sendBroadcast(fetchIntent)
        }

        val exportButton: Button = findViewById(R.id.buttonExportCsv)
        exportButton.setOnClickListener {
            Log.d("MainActivity", "Export CSV button clicked.")
            if (notificationList.isEmpty()) {
                Toast.makeText(this, "Notification list is empty. Nothing to export.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val csvData = generateCsvContent(notificationList)
            // Consider running saveCsvToFile on a background thread if notificationList can be very large
            // For simplicity now, keeping it on main thread. For very large lists, use Coroutines or AsyncTask.
            saveCsvToFile(csvData)
        }

        val clearButton: Button = findViewById(R.id.buttonClearList)
        clearButton.setOnClickListener {
            Log.d("MainActivity", "Clear List button clicked.")
            val oldSize = notificationList.size
            notificationList.clear()
            notificationAdapter.notifyItemRangeRemoved(0, oldSize) // Notify adapter after clearing the list

            // Also delete the persisted file
            try {
                val file = File(filesDir, NOTIFICATION_LIST_FILENAME)
                if (file.exists()) {
                    file.delete()
                    Log.d("MainActivity_Persistence", "Cleared persisted notifications file.")
                }
            } catch (e: Exception) {
                Log.e("MainActivity_Persistence", "Error deleting persisted notifications file", e)
            }
            Toast.makeText(this, "List cleared.", Toast.LENGTH_SHORT).show()
        }


        // Register receiver for notifications from service
        val intentFilter = IntentFilter(MyNotificationListenerService.ACTION_NOTIFICATION_POSTED)
        LocalBroadcastManager.getInstance(this).registerReceiver(notificationReceiver, intentFilter)
        Log.d("MainActivity", "NotificationReceiver registered in onCreate.")
    }

    override fun onResume() {
        super.onResume()
        // Check the permission status when the activity resumes
        // This is useful if the user goes to settings and comes back
        updatePermissionStatusDisplay()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Important to unregister receiver to avoid memory leaks
        LocalBroadcastManager.getInstance(this).unregisterReceiver(notificationReceiver)
        Log.d("MainActivity", "NotificationReceiver unregistered in onDestroy.")
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        // Get the list of enabled notification listeners
        val flat = Settings.Secure.getString(contentResolver, enabledNotificationListeners)
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.packageName)) {
                        // Our listener service is enabled
                        return true
                    }
                }
            }
        }
        // Our listener service is not enabled
        return false
    }

    private fun setupRecyclerView() {
        notificationsRecyclerView = findViewById(R.id.recyclerViewNotifications)
        notificationAdapter = NotificationAdapter(notificationList) // Pass the list

        notificationsRecyclerView.adapter = notificationAdapter
        notificationsRecyclerView.layoutManager = LinearLayoutManager(this)
        Log.d("MainActivity", "RecyclerView setup complete.")
    }

    private fun updatePermissionStatusDisplay() {
        if (isNotificationServiceEnabled()) {
            Toast.makeText(this, "Notification Listener Permission: GRANTED", Toast.LENGTH_SHORT).show()
            // TODO: Here you would typically start your app's main functionality
            //       that depends on the permission, like fetching/displaying notifications.
        } else {
            Toast.makeText(this, "Notification Listener Permission: NOT GRANTED. Please enable it.", Toast.LENGTH_LONG).show()
        }
    }

    private fun generateCsvContent(dataList: List<NotificationData>): String {
        val csvHeader = "Timestamp,EpochMillis,PackageName,Title,Text\n" // Define your CSV header
        val stringBuilder = StringBuilder()
        stringBuilder.append(csvHeader)

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        dataList.forEach { data ->
            val timestampStr = data.timestamp?.let { sdf.format(Date(it)) } ?: "N/A"
            val epochMillis = data.timestamp?.toString() ?: "N/A"
            val packageName = data.packageName?.replace(",", ";") ?: "N/A" // Replace commas to avoid CSV issues
            val title = data.title?.replace(",", ";")?.replace("\n", " ") ?: "N/A"      // Replace commas and newlines
            val text = data.text?.replace(",", ";")?.replace("\n", " ") ?: "N/A"        // Replace commas and newlines

            stringBuilder.append("$timestampStr,$epochMillis,$packageName,$title,$text\n")
        }
        return stringBuilder.toString()
    }

    private fun saveCsvToFile(csvContent: String) {
        val fileName = "NotificationLog_${System.currentTimeMillis()}.csv"
        var outputStream: OutputStream? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10 (API 29) and above - Use MediaStore
                val contentResolver = applicationContext.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    outputStream = contentResolver.openOutputStream(uri)
                } else {
                    Log.e("MainActivity_CSV", "MediaStore URI was null.")
                    Toast.makeText(this, "Failed to create CSV file (URI null)", Toast.LENGTH_LONG).show()
                    return
                }
            } else {
                // Below Android 10 - Direct file path (Requires WRITE_EXTERNAL_STORAGE permission)
                // You would need to request WRITE_EXTERNAL_STORAGE at runtime for API < 29
                // For simplicity in this example, we're focusing on API 29+ first.
                // If you need to support older versions, this part needs more robust permission handling.
                Log.e("MainActivity_CSV", "File saving for Android < Q not fully implemented here for brevity. Focus on Q+.")
                Toast.makeText(this, "CSV Export for this Android version needs WRITE_EXTERNAL_STORAGE (not fully implemented).", Toast.LENGTH_LONG).show()
                // Example for older versions (would require permission check):
                // val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                // if (!downloadsDir.exists()) downloadsDir.mkdirs()
                // val file = File(downloadsDir, fileName)
                // outputStream = FileOutputStream(file)
                return // For now, we'll just return if below Q to avoid crashes without permission code
            }

            if (outputStream == null) {
                Log.e("MainActivity_CSV", "OutputStream is null, cannot write file.")
                Toast.makeText(this, "Failed to get output stream for CSV.", Toast.LENGTH_LONG).show()
                return
            }

            outputStream.bufferedWriter().use { it.write(csvContent) }
            Toast.makeText(this, "CSV saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
            Log.i("MainActivity_CSV", "CSV file saved successfully: $fileName")

        } catch (e: Exception) {
            Log.e("MainActivity_CSV", "Error saving CSV file", e)
            Toast.makeText(this, "Error saving CSV: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            try {
                outputStream?.close()
            } catch (e: Exception) {
                Log.e("MainActivity_CSV", "Error closing output stream", e)
            }
        }
    }

    // Inside MainActivity class
    companion object {
        // ... any existing companion object content ...
        private const val NOTIFICATION_LIST_FILENAME = "notifications_log.json"
    }

    // Inside MainActivity class

    private fun saveNotificationList() {
        if (notificationList.isEmpty()) { // Optional: Don't save if empty, or save an empty list
            val file = File(filesDir, NOTIFICATION_LIST_FILENAME)
            if (file.exists()) {
                file.delete() // If the list is now empty, delete the old file
                Log.d("MainActivity_Persistence", "Notification list is empty. Deleted existing JSON file.")
            }
            return
        }

        try {
            val jsonString = Json.encodeToString(notificationList) // Serialize the whole list
            val file = File(filesDir, NOTIFICATION_LIST_FILENAME) // App's internal storage
            file.writeText(jsonString)
            Log.d("MainActivity_Persistence", "Notification list saved to $NOTIFICATION_LIST_FILENAME. Count: ${notificationList.size}")
        } catch (e: Exception) { // Catch more specific exceptions like SerializationException, IOException if preferred
            Log.e("MainActivity_Persistence", "Failed to save notification list", e)
            Toast.makeText(this, "Error saving notification data.", Toast.LENGTH_SHORT).show()
        }
    }


    @Suppress("UNCHECKED_CAST") // Suppress for the list cast if using non-reified type for decode

    private fun loadNotificationList() {
        val file = File(filesDir, NOTIFICATION_LIST_FILENAME)
        if (!file.exists()) {
            Log.d("MainActivity_Persistence", "Notification JSON file does not exist. Starting with an empty list.")
            notificationList.clear() // Ensure it's empty if no file
            return
        }

        try {
            val jsonString = file.readText()
            if (jsonString.isNotBlank()) {
                val json = Json { ignoreUnknownKeys = true }
                val loadedList = json.decodeFromString<List<NotificationData>>(jsonString)
                notificationList.clear()
                notificationList.addAll(loadedList) // Add them in the order they were saved
                // If newest was at index 0 when saved, it's still at index 0 here.
                Log.d("MainActivity_Persistence", "Notification list loaded from $NOTIFICATION_LIST_FILENAME. Count: ${notificationList.size}")
            } else {
                Log.d("MainActivity_Persistence", "Notification JSON file is blank. Starting with an empty list.")
                notificationList.clear()
            }
        } catch (e: Exception) {
            Log.e("MainActivity_Persistence", "Failed to load notification list", e)
            notificationList.clear()
            // Optional: Toast.makeText(this, "Error loading saved data.", Toast.LENGTH_SHORT).show()
            // Optional: try { file.delete() } catch (ioe: IOException) { /* log or ignore */ }
        }
    }

    override fun onStop() {
        super.onStop()
        saveNotificationList() // <--- THIS LINE IS CRUCIAL
        Log.d("MainActivity", "onStop called, saving notifications.")
    }
}