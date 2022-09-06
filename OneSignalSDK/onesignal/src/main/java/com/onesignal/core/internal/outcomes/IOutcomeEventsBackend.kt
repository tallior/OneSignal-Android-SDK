package com.onesignal.core.internal.outcomes

import com.onesignal.core.internal.backend.http.HttpResponse
import org.json.JSONObject

internal interface IOutcomeEventsBackend {
    suspend fun sendOutcomeEvent(jsonObject: JSONObject): HttpResponse
}