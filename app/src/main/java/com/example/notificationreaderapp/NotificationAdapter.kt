package com.example.notificationreaderapp // Make sure this matches your package name

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationAdapter(
    private val notifications: List<NotificationData>
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    // Describes an item view and metadata about its place within the RecyclerView.
    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.textViewNotificationTitle)
        val textTextView: TextView = itemView.findViewById(R.id.textViewNotificationText)
        val packageTextView: TextView = itemView.findViewById(R.id.textViewNotificationPackage)
        val timestampTextView: TextView = itemView.findViewById(R.id.textViewNotificationTimestamp)
    }

    // Called when RecyclerView needs a new ViewHolder of the given type to represent an item.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        // Create a new view, which defines the UI of the list item
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false) // Use your item_notification.xml
        return NotificationViewHolder(itemView)
    }

    // Called by RecyclerView to display the data at the specified position.
    // This method updates the contents of the ViewHolder's itemView to reflect the item at the given position.
    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val currentNotification = notifications[position]

        holder.titleTextView.text = currentNotification.title ?: "No Title"
        holder.textTextView.text = currentNotification.text ?: "No Text"
        holder.packageTextView.text = currentNotification.packageName ?: "No Package Name"

        // Format the timestamp (Long) into a readable date/time string
        if (currentNotification.timestamp != null) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val date = Date(currentNotification.timestamp)
            holder.timestampTextView.text = sdf.format(date)
        } else {
            holder.timestampTextView.text = "No Timestamp"
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = notifications.size
}