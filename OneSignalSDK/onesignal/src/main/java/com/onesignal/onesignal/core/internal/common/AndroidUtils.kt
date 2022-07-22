package com.onesignal.onesignal.core.internal.common

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.View
import com.onesignal.onesignal.core.internal.logging.Logging
import java.lang.ref.WeakReference
import java.util.*

object AndroidUtils {
    private val MARGIN_ERROR_PX_SIZE = dpToPx(24)

    fun getRandomDelay(minDelay: Int, maxDelay: Int): Int {
        return Random().nextInt(maxDelay + 1 - minDelay) + minDelay
    }

    fun isStringNotEmpty(body: String?): Boolean {
        return !TextUtils.isEmpty(body)
    }

    /**
     * Check if the keyboard is currently being shown.
     * Does not work for cases when keyboard is full screen.
     */
    fun isKeyboardUp(activityWeakReference: WeakReference<Activity?>): Boolean {
        val metrics = DisplayMetrics()
        val visibleBounds = Rect()
        var view: View? = null
        var isOpen = false
        if (activityWeakReference.get() != null) {
            val window = activityWeakReference.get()!!.window
            view = window.decorView
            view.getWindowVisibleDisplayFrame(visibleBounds)
            window.windowManager.defaultDisplay.getMetrics(metrics)
        }
        if (view != null) {
            val heightDiff = metrics.heightPixels - visibleBounds.bottom
            isOpen = heightDiff > MARGIN_ERROR_PX_SIZE
        }
        return isOpen
    }

    fun dpToPx(dp: Int): Int {
        return (dp * Resources.getSystem().displayMetrics.density).toInt()
    }

    fun sleep(ms: Int) {
        try {
            Thread.sleep(ms.toLong())
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    fun hasConfigChangeFlag(activity: Activity, configChangeFlag: Int): Boolean {
        var hasFlag = false
        try {
            val configChanges =
                activity.packageManager.getActivityInfo(activity.componentName, 0).configChanges
            val flagInt = configChanges and configChangeFlag
            hasFlag = flagInt != 0
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return hasFlag
    }

    fun getManifestMeta(context: Context, metaName: String?): String? {
        val bundle = getManifestMetaBundle(context)
        return bundle?.getString(metaName)
    }

    fun getManifestMetaBundle(context: Context): Bundle? {
        val ai: ApplicationInfo
        try {
            ai = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            return ai.metaData
        } catch (e: PackageManager.NameNotFoundException) {
            Logging.error("Manifest application info not found", e)
        }
        return null
    }

    fun getResourceString(context: Context, key: String?, defaultStr: String?): String? {
        val resources = context.resources
        val bodyResId = resources.getIdentifier(key, "string", context.packageName)
        return if (bodyResId != 0) resources.getString(bodyResId) else defaultStr
    }

    fun isValidResourceName(name: String?): Boolean {
        return name != null && !name.matches("^[0-9]".toRegex())
    }

    fun getRootCauseThrowable(subjectThrowable: Throwable): Throwable {
        var throwable = subjectThrowable
        while (throwable.cause != null && throwable.cause !== throwable) {
            throwable = throwable.cause!!
        }
        return throwable
    }

    fun getRootCauseMessage(throwable: Throwable): String? {
        return getRootCauseThrowable(throwable).message
    }

    fun isRunningOnMainThread(): Boolean {
        return Thread.currentThread() == Looper.getMainLooper().thread
    }

    fun getTargetSdkVersion(context: Context): Int {
        val packageName = context.packageName
        val packageManager = context.packageManager
        try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            return applicationInfo.targetSdkVersion
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1
    }
}