package com.onesignal.onesignal.internal.application

import android.app.Activity

interface IActivityLifecycleHandler  {
    fun onAvailable(activity: Activity?)
    fun onStopped(activity: Activity?)
}
