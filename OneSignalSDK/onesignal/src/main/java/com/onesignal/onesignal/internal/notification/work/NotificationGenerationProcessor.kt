package com.onesignal.onesignal.internal.notification.work

import android.content.Context
import com.onesignal.onesignal.internal.application.IApplicationService
import com.onesignal.onesignal.internal.common.AndroidUtils
import com.onesignal.onesignal.internal.common.time.ITime
import com.onesignal.onesignal.internal.notification.Notification
import com.onesignal.onesignal.internal.notification.NotificationConstants
import com.onesignal.onesignal.internal.notification.NotificationReceivedEvent
import com.onesignal.onesignal.internal.notification.data.INotificationDataController
import com.onesignal.onesignal.internal.notification.generation.IGenerateNotification
import com.onesignal.onesignal.internal.notification.receipt.IReceiveReceiptService
import com.onesignal.onesignal.internal.params.IParamsService
import com.onesignal.onesignal.logging.Logging
import com.onesignal.onesignal.notification.INotificationWillShowInForegroundHandler
import com.onesignal.onesignal.notification.IRemoteNotificationReceivedHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONException
import org.json.JSONObject

/**
 * The [NotificationGenerationProcessor] is responsible for driving the displaying of a notification
 * to the user.
 */
internal class NotificationGenerationProcessor(
    private val _applicationService: IApplicationService,
    private val _notificationGeneration: IGenerateNotification,
    private val _receiptService: IReceiveReceiptService,
    private val _paramsService: IParamsService,
    private val _dataController: INotificationDataController,
    private val _time: ITime,
    private val _remoteNotificationReceivedHandler: IRemoteNotificationReceivedHandler?,
    private val _notificationWillShowInForegroundHandler: INotificationWillShowInForegroundHandler?
) : INotificationGenerationProcessor {
    override suspend fun processNotificationData(
                    context: Context,
                    androidNotificationId: Int,
                    jsonPayload: JSONObject,
                    isRestoring: Boolean,
                    timestamp: Long) {

        val notification = Notification(null, jsonPayload, androidNotificationId, _time)

        if (!isValidNotification(notification))
            return

        val notificationJob = NotificationGenerationJob(context)
        notificationJob.shownTimeStamp = timestamp
        notificationJob.isRestoring = isRestoring
        notificationJob.notification = notification
        notificationJob.jsonPayload = jsonPayload

        var shouldDisplay = true
        var didDisplay = false

        if (_remoteNotificationReceivedHandler == null) {
            Logging.warn("remoteNotificationReceivedHandler not setup, displaying normal OneSignal notification")
        }
        else {
            Logging.info("Fire remoteNotificationReceived")
            val serviceExtensionReceivedEvent = NotificationReceivedEvent(notification)

            try {
                withTimeout(30000L) {
                    _remoteNotificationReceivedHandler.remoteNotificationReceived(context, serviceExtensionReceivedEvent)
                }
            } catch (t: Throwable) {
                Logging.error("remoteNotificationReceived throw an exception. Displaying normal OneSignal notification.", t)
            }

            var shouldDisplay = processHandlerResponse(notificationJob, serviceExtensionReceivedEvent.notification?.copy(), isRestoring)
                ?: return
        }

        if(shouldDisplay) {
            if (shouldFireForegroundHandlers(notificationJob)) {
                Logging.info("Fire notificationWillShowInForegroundHandler")

                val foregroundReceivedEvent = NotificationReceivedEvent(notification)

                try {
                    withTimeout(30000L) {
                        _notificationWillShowInForegroundHandler!!.notificationWillShowInForeground(foregroundReceivedEvent)
                    }
                } catch (t: Throwable) {
                    Logging.error("Exception thrown while notification was being processed for display by notificationWillShowInForegroundHandler, showing notification in foreground!", t)
                }

                shouldDisplay = processHandlerResponse(notificationJob, foregroundReceivedEvent.notification?.copy(), isRestoring)
                    ?: return
            }

            if(shouldDisplay ) {
                // display the notification
                // Notification might end not displaying because the channel for that notification has notification disable
                didDisplay = _notificationGeneration.displayNotification(notificationJob)
            }
        }

        // finish up
        if (!notificationJob.isRestoring) {
            postProcessNotification(notificationJob, false, didDisplay)

            handleNotificationReceived(notificationJob)
        }

        // Delay to prevent CPU spikes
        // Normally more than one notification is restored at a time
        if (isRestoring)
            delay(100)
    }


    /**
     * Process the response to the external handler (either the foreground handler or the service extension).
     *
     * @param notificationJob The notification job covering the context the handler was called under.
     * @param notification The notification that is to be displayed, as determined by the handler.
     * @param isRestoring Whether this notification is being processed because of a restore.
     *
     * @return true if the job should continue display, false if the job should continue but not display, null if processing should stop.
     */
    private suspend fun processHandlerResponse(notificationJob: NotificationGenerationJob, notification: Notification?, isRestoring: Boolean) : Boolean? {
        if (notification != null) {
            val canDisplay = AndroidUtils.isStringNotEmpty(notification.body)
            val withinTtl: Boolean = isNotificationWithinTTL(notificationJob)

            if (canDisplay && withinTtl) {
                // Update the job to use the new notification
                notificationJob.notification = notification

                processCollapseKey(notificationJob)

                var shouldDisplay = shouldDisplayNotification(notificationJob)

                if (shouldDisplay) {
                    notificationJob.isNotificationToDisplay = true;
                    return true
                }

                return false
            }
        }

        // Processing should stop, save the notification as processed to prevent possible duplicate
        // calls from canonical ids.
        if (isRestoring) {
            // If we are not displaying a restored notification make sure we mark it as dismissed
            // This will prevent it from being restored again
            markNotificationAsDismissed(notificationJob)
        } else {
            // indicate the notification job did not display
            notificationJob.isNotificationToDisplay = false
            postProcessNotification(notificationJob, true, false)
            handleNotificationReceived(notificationJob)
        }

        return null
    }

    // If available TTL times comes in seconds, by default is 3 days in seconds
    private fun isNotificationWithinTTL(notificationJob: NotificationGenerationJob): Boolean {
        val useTtl = _paramsService.restoreTTLFilter
        if (!useTtl) return true
        val currentTimeInSeconds = _time.currentTimeMillis / 1000
        val sentTime = notificationJob.notification!!.sentTime
        // If available TTL times comes in seconds, by default is 3 days in seconds
        val ttl = notificationJob.notification!!.ttl
        return sentTime + ttl > currentTimeInSeconds
    }

    private suspend fun isValidNotification(notification: Notification) : Boolean {
        return !_dataController.isDuplicateNotification(notification.notificationId)
    }

    private fun shouldDisplayNotification(notificationJob: NotificationGenerationJob): Boolean {
        return notificationJob.hasExtender() || AndroidUtils.isStringNotEmpty(
            notificationJob.jsonPayload!!.optString("alert")
        )
    }

    private fun handleNotificationReceived(notificationJob: NotificationGenerationJob) {

        // TODO: Implement
//            OneSignal.handleNotificationReceived(notificationJob)
    }

    /**
     * Post process the notification: Save notification, updates Outcomes, and sends Received Receipt if they are enabled.
     */
    private suspend fun postProcessNotification(
        notificationJob: NotificationGenerationJob,
        wasOpened: Boolean,
        wasDisplayed: Boolean
    ) {
        saveNotification(notificationJob, wasOpened)
        if (!wasDisplayed) {
            // Notification channel disable or not displayed
            // save notification as dismissed to avoid user re-enabling channel and notification being displayed due to restore
            markNotificationAsDismissed(notificationJob)
            return
        }

        // Logic for when the notification is displayed
        val notificationId = notificationJob.apiNotificationId
        _receiptService.notificationReceived(notificationId)

        // TODO: Implement
//        OneSignal.getSessionManager().onNotificationReceived(notificationId)
    }

    // Saving the notification provides the following:
    //   * Prevent duplicates
    //   * Build summary notifications
    //   * Collapse key / id support - Used to lookup the android notification id later
    //   * Redisplay notifications after reboot, upgrade of app, or cold boot after a force kill.
    //   * Future - Public API to get a list of notifications
    private suspend fun saveNotification(notificationJob: NotificationGenerationJob, opened: Boolean) {
        Logging.debug("Saving Notification job: $notificationJob")

        val jsonPayload = notificationJob.jsonPayload!!
        try {
            val customJSON = getCustomJSONObject(jsonPayload)

            val collapseKey: String? = if(jsonPayload.has("collapse_key") && "do_not_collapse" != jsonPayload.optString("collapse_key")) jsonPayload.optString("collapse_key") else null

            // Set expire_time
            val sentTime = jsonPayload.optLong(
                NotificationConstants.GOOGLE_SENT_TIME_KEY,
                _time.currentTimeMillis
            ) / 1000L
            val ttl = jsonPayload.optInt(
                NotificationConstants.GOOGLE_TTL_KEY,
                NotificationConstants.DEFAULT_TTL_IF_NOT_IN_PAYLOAD
            )
            val expireTime = sentTime + ttl

            _dataController.saveNotification(
                customJSON.optString("i"),
                jsonPayload.optString("grp"),
                collapseKey,
                notificationJob.isNotificationToDisplay,
                opened,
                notificationJob.androidId,
                if (notificationJob.title != null) notificationJob.title.toString() else null,
                if (notificationJob.body != null) notificationJob.body.toString() else null,
                expireTime,
                jsonPayload.toString())
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private suspend fun markNotificationAsDismissed(notifiJob: NotificationGenerationJob) {
        if (!notifiJob.isNotificationToDisplay)
            return

        Logging.debug("Marking restored or disabled notifications as dismissed: $notifiJob")

        _dataController.markAsDismissed(notifiJob.androidId)
    }

    private suspend fun processCollapseKey(notificationJob: NotificationGenerationJob) {
        if (notificationJob.isRestoring) return
        if (notificationJob.jsonPayload?.has("collapse_key") == true || "do_not_collapse" == notificationJob.jsonPayload?.optString(
                "collapse_key"
            )
        ) return
        val collapse_id = notificationJob.jsonPayload!!.optString("collapse_key")

        val androidNotificationId = _dataController.getAndroidIdFromCollapseKey(collapse_id)

        if(androidNotificationId != null) {
            notificationJob.notification?.androidNotificationId = androidNotificationId
        }
    }

    @Throws(JSONException::class)
    fun getCustomJSONObject(jsonObject: JSONObject): JSONObject {
        return JSONObject(jsonObject.optString("custom"))
    }

    private fun shouldFireForegroundHandlers(notificationJob: NotificationGenerationJob): Boolean {
        if (!_applicationService.isInForeground) {
            Logging.info("App is in background, show notification")
            return false
        }
        if (_notificationWillShowInForegroundHandler == null) {
            Logging.info("No NotificationWillShowInForegroundHandler setup, show notification")
            return false
        }

        // Notification is restored. Don't fire for restored notifications.
        if (notificationJob.isRestoring) {
            Logging.info("Not firing notificationWillShowInForegroundHandler for restored notifications")
            return false
        }
        return true
    }
}