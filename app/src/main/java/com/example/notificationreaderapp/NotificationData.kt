package com.example.notificationreaderapp

import android.os.Parcelable // Import for Parcelable
import kotlinx.parcelize.Parcelize // Import for @Parcelize
import kotlinx.serialization.Serializable

@Serializable // <--- THIS ANNOTATION
@Parcelize // Add this annotation
data class NotificationData(
    val timestamp: Long,
    val packageName: String?,
    val title: String?,
    val text: String?
) : Parcelable // Add this to implement Parcelable