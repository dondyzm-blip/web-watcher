package com.webwatcher.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.webwatcher.data.model.AccessHistory
import com.webwatcher.data.repository.WatchRepository
import com.webwatcher.util.HashUtil
import com.webwatcher.util.HtmlDiffEngine
import com.webwatcher.util.NotificationHelper
import com.webwatcher.util.SnapshotStorage
import com.webwatcher.util.WebFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val TAG = "WatchWorker"
const val KEY_TARGET_ID = "target_id"

class WatchWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val repo = WatchRepository(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val targetId = inputData.getLong(KEY_TARGET_ID, -1L)
        if (targetId < 0) return@withContext Result.failure()

        val target = repo.getTargetById(targetId) ?: return@withContext Result.failure()
        if (!target.isActive) return@withContext Result.success()

        Log.d(TAG, "Checking: ${target.url}")

        val fetchResult = WebFetcher.fetch(target.url)
        val now = System.currentTimeMillis()

        if (fetchResult.error != null || fetchResult.statusCode == 0) {
            // ネットワークエラー記録
            repo.insertHistory(AccessHistory(
                targetId = targetId,
                hasChanged = false,
                contentHash = "",
                snapshotPath = null,
                screenshotPath = null,
                diffSnapshotPath = null,
                statusCode = fetchResult.statusCode,
                errorMessage = fetchResult.error
            ))
            return@withContext Result.retry()
        }

        val newHash = HashUtil.computeContentHash(fetchResult.html, target.cssSelector)
        val hasChanged = target.lastContentHash != null && target.lastContentHash != newHash

        // HTMLスナップショット保存
        val snapshotPath = SnapshotStorage.saveHtml(context, targetId, fetchResult.html)

        // 差分HTML生成（前回のスナップショットと比較）
        var diffPath: String? = null
        if (hasChanged) {
            val prevHistory = repo.getLatestTwoHistory(targetId).getOrNull(1)
            val prevHtml = prevHistory?.snapshotPath?.let { SnapshotStorage.readHtml(it) }
            if (prevHtml != null) {
                val diffHtml = HtmlDiffEngine.generateDiffHtml(prevHtml, fetchResult.html, target.cssSelector)
                diffPath = SnapshotStorage.saveHtml(context, targetId, diffHtml, "_diff")
            }
        }

        // 履歴保存
        val historyId = repo.insertHistory(AccessHistory(
            targetId = targetId,
            hasChanged = hasChanged,
            contentHash = newHash,
            snapshotPath = snapshotPath,
            screenshotPath = null,
            diffSnapshotPath = diffPath,
            statusCode = fetchResult.statusCode
        ))

        // DB更新
        repo.updateCheckInfo(targetId, now, newHash)
        if (hasChanged) {
            repo.updateChangedAt(targetId, now)

            // 通知発火
            NotificationHelper.showChangeNotification(
                context,
                targetId,
                target.title,
                historyId,
                targetId.toInt()
            )
        }

        // 古いファイル削除
        SnapshotStorage.deleteOldFiles(context, targetId)

        Result.success()
    }
}

// ─── WorkManager スケジューラ ───────────────────────────────────────────────

object WatchScheduler {

    fun schedule(context: Context, targetId: Long, intervalMinutes: Int) {
        val data = workDataOf(KEY_TARGET_ID to targetId)

        val request = PeriodicWorkRequestBuilder<WatchWorker>(
            intervalMinutes.toLong(), TimeUnit.MINUTES
        )
            .setInputData(data)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workName(targetId),
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        Log.d(TAG, "Scheduled target $targetId every $intervalMinutes min")
    }

    fun cancel(context: Context, targetId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(targetId))
        Log.d(TAG, "Cancelled target $targetId")
    }

    fun runNow(context: Context, targetId: Long) {
        val data = workDataOf(KEY_TARGET_ID to targetId)
        val request = OneTimeWorkRequestBuilder<WatchWorker>()
            .setInputData(data)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    private fun workName(targetId: Long) = "watch_target_$targetId"
}
