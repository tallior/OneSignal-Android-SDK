package com.onesignal.onesignal.internal.notification.work

import android.content.Context
import org.json.JSONObject

interface INotificationGenerationProcessor {
    suspend fun processNotificationData(
        context: Context,
        androidNotificationId: Int,
        jsonPayload: JSONObject,
        isRestoring: Boolean,
        timestamp: Long
    )
}