package com.webwatcher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.webwatcher.data.repository.WatchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 端末再起動後にWorkerを再スケジュールする
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        CoroutineScope(Dispatchers.IO).launch {
            val repo = WatchRepository(context)
            repo.getActiveTargets().forEach { target ->
                WatchScheduler.schedule(context, target.id, target.intervalMinutes)
            }
        }
    }
}
