package com.webwatcher

import android.app.Application
import com.webwatcher.util.NotificationHelper

class WebWatcherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }
}
