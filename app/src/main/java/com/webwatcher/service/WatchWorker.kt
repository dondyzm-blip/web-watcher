package com.webwatcher.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.work.*
import com.webwatcher.data.model.AccessHistory
import com.webwatcher.data.repository.WatchRepository
import com.webwatcher.util.HashUtil
import com.webwatcher.util.HtmlDiffEngine
import com.webwatcher.util.NotificationHelper
import com.webwatcher.util.SnapshotStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

private const val TAG = "WatchWorker"
const val KEY_TARGET_ID = "target_id"

class WatchWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val repo = WatchRepository(context)

    override suspend fun doWork(): Result {
        val targetId = inputData.getLong(KEY_TARGET_ID, -1L)
        if (targetId < 0) return Result.failure()

        val target = repo.getTargetById(targetId) ?: return Result.failure()
        if (!target.isActive) return Result.success()

        Log.d(TAG, "Checking: ${target.url}")

        return try {
            // WebViewでHTMLを取得（メインスレッドで実行）
            val html = withContext(Dispatchers.Main) {
                fetchHtmlWithWebView(target.url, target.waitSeconds)
            }

            if (html == null) {
                repo.insertHistory(AccessHistory(
                    targetId = targetId,
                    hasChanged = false,
                    contentHash = "",
                    snapshotPath = null,
                    screenshotPath = null,
                    diffSnapshotPath = null,
                    statusCode = 0,
                    errorMessage = "ページの読み込みに失敗しました"
                ))
                return Result.retry()
            }

            val now = System.currentTimeMillis()
            val newHash = HashUtil.computeContentHash(html, target.cssSelector)
            val hasChanged = target.lastContentHash != null && target.lastContentHash != newHash

            val snapshotPath = withContext(Dispatchers.IO) {
                SnapshotStorage.saveHtml(context, targetId, html)
            }

            var diffPath: String? = null
            if (hasChanged) {
                val prevHistory = repo.getLatestTwoHistory(targetId).getOrNull(1)
                val prevHtml = prevHistory?.snapshotPath?.let {
                    withContext(Dispatchers.IO) { SnapshotStorage.readHtml(it) }
                }
                if (prevHtml != null) {
                    val diffHtml = HtmlDiffEngine.generateDiffHtml(prevHtml, html, target.cssSelector)
                    diffPath = withContext(Dispatchers.IO) {
                        SnapshotStorage.saveHtml(context, targetId, diffHtml, "_diff")
                    }
                }
            }

            val historyId = repo.insertHistory(AccessHistory(
                targetId = targetId,
                hasChanged = hasChanged,
                contentHash = newHash,
                snapshotPath = snapshotPath,
                screenshotPath = null,
                diffSnapshotPath = diffPath,
                statusCode = 200
            ))

            repo.updateCheckInfo(targetId, now, newHash)
            if (hasChanged) {
                repo.updateChangedAt(targetId, now)
                NotificationHelper.showChangeNotification(
                    context, targetId, target.title, historyId, targetId.toInt()
                )
            }

            withContext(Dispatchers.IO) {
                SnapshotStorage.deleteOldFiles(context, targetId)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking ${target.url}", e)
            Result.retry()
        }
    }

    private suspend fun fetchHtmlWithWebView(url: String, waitSeconds: Int): String? =
        suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            val webView = WebView(context)
            var resumed = false

            fun resumeWith(html: String?) {
                if (!resumed) {
                    resumed = true
                    webView.destroy()
                    cont.resume(html)
                }
            }

            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Safari/537.36"
            }

            // JSからHTMLを受け取るインターフェース
            class HtmlReceiver {
                @JavascriptInterface
                fun receive(html: String) {
                    handler.post { resumeWith(html) }
                }
            }
            webView.addJavascriptInterface(HtmlReceiver(), "AndroidBridge")

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                    // 指定秒数待機後にHTMLを取得
                    handler.postDelayed({
                        webView.evaluateJavascript(
                            "AndroidBridge.receive(document.documentElement.outerHTML);"
                        ) { /* コールバック不要 */ }
                    }, waitSeconds * 1000L)
                }
            }

            // タイムアウト（待機時間 + 30秒）
            handler.postDelayed({
                resumeWith(null)
            }, (waitSeconds + 30) * 1000L)

            webView.loadUrl(url)

            cont.invokeOnCancellation { resumeWith(null) }
        }
}

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
    }

    fun cancel(context: Context, targetId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(targetId))
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
