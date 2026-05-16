package com.webwatcher.ui.detail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.webwatcher.R
import com.webwatcher.data.model.AccessHistory
import com.webwatcher.databinding.ActivityDetailBinding
import com.webwatcher.service.WatchScheduler
import com.webwatcher.ui.add.AddWatchActivity
import com.webwatcher.util.SnapshotStorage
import java.io.File

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private val viewModel: DetailViewModel by viewModels()
    private lateinit var historyAdapter: HistoryAdapter
    private var targetId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        targetId = intent.getLongExtra(EXTRA_TARGET_ID, -1L)
        val openHistoryId = intent.getLongExtra(EXTRA_HISTORY_ID, -1L)
        val showDiff = intent.getBooleanExtra(EXTRA_SHOW_DIFF, false)

        if (targetId < 0) { finish(); return }

        viewModel.load(targetId)
        setupWebView()
        setupRecyclerView()
        observeData(openHistoryId, showDiff)
    }

    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            webViewClient = WebViewClient()
        }
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter { history ->
            showHistory(history, showDiff = false)
        }
        binding.recyclerHistory.apply {
            layoutManager = LinearLayoutManager(this@DetailActivity)
            adapter = historyAdapter
        }
    }

    private fun observeData(openHistoryId: Long, showDiff: Boolean) {
        viewModel.target.observe(this) { target ->
            target ?: return@observe
            supportActionBar?.title = target.title
            binding.tvUrl.text = target.url
            binding.tvInterval.text = "${target.intervalMinutes}分ごとに確認"
        }

        viewModel.history.observe(this) { list ->
            historyAdapter.submitList(list)
            if (list.isNotEmpty()) {
                val toOpen = if (openHistoryId >= 0) {
                    list.firstOrNull { it.id == openHistoryId } ?: list.first()
                } else list.first()
                showHistory(toOpen, showDiff)
            }
        }
    }

    private fun showHistory(history: AccessHistory, showDiff: Boolean) {
        val path = when {
            showDiff && history.diffSnapshotPath != null -> history.diffSnapshotPath
            history.snapshotPath != null -> history.snapshotPath
            else -> null
        }

        if (path != null) {
            binding.webViewCard.visibility = View.VISIBLE
            binding.webView.loadUrl("file://$path")
            binding.tvViewingLabel.text = if (showDiff && history.diffSnapshotPath != null)
                "差分表示" else "ページキャプチャ"
        } else {
            binding.webViewCard.visibility = View.GONE
        }

        // 差分/通常の切り替えボタン
        binding.btnToggleDiff.visibility =
            if (history.diffSnapshotPath != null) View.VISIBLE else View.GONE
        binding.btnToggleDiff.text = if (showDiff) "通常表示" else "差分表示"
        binding.btnToggleDiff.setOnClickListener {
            showHistory(history, !showDiff)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_edit -> {
                startActivity(Intent(this, AddWatchActivity::class.java).apply {
                    putExtra(AddWatchActivity.EXTRA_EDIT_ID, targetId)
                })
                true
            }
            R.id.action_check_now -> {
                WatchScheduler.runNow(this, targetId)
                com.google.android.material.snackbar.Snackbar
                    .make(binding.root, "チェック中...", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                    .show()
                true
            }
            R.id.action_open_browser -> {
                viewModel.target.value?.url?.let { url ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    companion object {
        const val EXTRA_TARGET_ID = "extra_target_id"
        const val EXTRA_HISTORY_ID = "extra_history_id"
        const val EXTRA_SHOW_DIFF = "extra_show_diff"
    }
}
