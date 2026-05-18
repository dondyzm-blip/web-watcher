package com.webwatcher.ui.detail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.webwatcher.R
import com.webwatcher.data.model.AccessHistory
import com.webwatcher.data.repository.WatchRepository
import com.webwatcher.databinding.ActivityDetailBinding
import com.webwatcher.service.WatchScheduler
import com.webwatcher.ui.add.AddWatchActivity
import kotlinx.coroutines.launch
import java.io.File

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private val viewModel: DetailViewModel by viewModels()
    private val repo by lazy { WatchRepository(this) }
    private lateinit var historyAdapter: HistoryAdapter
    private var targetId: Long = -1
    private var isSelectMode = false

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
        setupSelectModeButton()
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
                allowFileAccess = true
                allowContentAccess = true
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
            }

            // 要素選択結果を受け取るJSブリッジ
            addJavascriptInterface(object : Any() {
                @JavascriptInterface
                fun onElementSelected(selector: String, preview: String) {
                    runOnUiThread {
                        showSelectorConfirmDialog(selector, preview)
                    }
                }
            }, "SelectorBridge")

            webViewClient = WebViewClient()
        }
    }

    private fun setupSelectModeButton() {
        binding.btnSelectElement.setOnClickListener {
            isSelectMode = !isSelectMode
            if (isSelectMode) {
                enableSelectMode()
            } else {
                disableSelectMode()
            }
        }
    }

    private fun enableSelectMode() {
        binding.tvSelectModeBanner.visibility = View.VISIBLE
        binding.btnSelectElement.text = "選択キャンセル"

        // ページにタップ検知JSを注入
        val js = """
            (function() {
                // 既存のハイライトを削除
                var old = document.getElementById('_ww_highlight');
                if (old) old.parentNode.removeChild(old);

                var style = document.createElement('style');
                style.id = '_ww_highlight';
                style.textContent = '._ww_hover { outline: 3px solid #1976D2 !important; cursor: pointer !important; background-color: rgba(25,118,210,0.1) !important; }';
                document.head.appendChild(style);

                function getSelector(el) {
                    if (el.id) return '#' + el.id;
                    var path = [];
                    while (el && el.nodeType === 1) {
                        var selector = el.tagName.toLowerCase();
                        if (el.className) {
                            var classes = el.className.trim().split(/\s+/)
                                .filter(function(c){ return c && !c.startsWith('_ww_'); })
                                .slice(0, 2);
                            if (classes.length > 0) selector += '.' + classes.join('.');
                        }
                        path.unshift(selector);
                        el = el.parentElement;
                        if (path.length >= 3) break;
                    }
                    return path.join(' > ');
                }

                document.addEventListener('mouseover', function(e) {
                    e.target.classList.add('_ww_hover');
                });
                document.addEventListener('mouseout', function(e) {
                    e.target.classList.remove('_ww_hover');
                });
                document.addEventListener('click', function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    var el = e.target;
                    var selector = getSelector(el);
                    var preview = el.innerText ? el.innerText.substring(0, 80) : el.outerHTML.substring(0, 80);
                    SelectorBridge.onElementSelected(selector, preview);
                }, true);
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(js, null)
    }

    private fun disableSelectMode() {
        binding.tvSelectModeBanner.visibility = View.GONE
        binding.btnSelectElement.text = "要素選択"
        // JSイベントリスナーを除去するためページをリロード
        binding.webView.reload()
    }

    private fun showSelectorConfirmDialog(selector: String, preview: String) {
        isSelectMode = false
        binding.tvSelectModeBanner.visibility = View.GONE
        binding.btnSelectElement.text = "要素選択"

        MaterialAlertDialogBuilder(this)
            .setTitle("この要素を監視しますか？")
            .setMessage("セレクター: $selector\n\nプレビュー:\n$preview")
            .setPositiveButton("設定する") { _, _ ->
                lifecycleScope.launch {
                    val target = repo.getTargetById(targetId) ?: return@launch
                    repo.updateTarget(target.copy(cssSelector = selector))
                    com.google.android.material.snackbar.Snackbar
                        .make(binding.root, "CSSセレクターを設定しました: $selector", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                        .show()
                }
            }
            .setNegativeButton("キャンセル") { _, _ ->
                disableSelectMode()
            }
            .show()
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter { history -> showHistory(history, showDiff = false) }
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
            binding.tvInterval.text = "${target.intervalMinutes}分ごとに確認　読込待機: ${target.waitSeconds}秒"
        }

        viewModel.history.observe(this) { list ->
            historyAdapter.submitList(list)
            if (list.isNotEmpty()) {
                val toOpen = if (openHistoryId >= 0)
                    list.firstOrNull { it.id == openHistoryId } ?: list.first()
                else list.first()
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

        if (path != null && File(path).exists()) {
            binding.webViewCard.visibility = View.VISIBLE
            binding.webView.loadUrl("file://$path")
            binding.tvViewingLabel.text =
                if (showDiff && history.diffSnapshotPath != null) "差分表示" else "ページキャプチャ"
        } else {
            binding.webViewCard.visibility = View.GONE
        }

        binding.btnToggleDiff.visibility =
            if (history.diffSnapshotPath != null) View.VISIBLE else View.GONE
        binding.btnToggleDiff.text = if (showDiff) "通常表示" else "差分表示"
        binding.btnToggleDiff.setOnClickListener { showHistory(history, !showDiff) }
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
                    .make(binding.root, "チェック中...", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                true
            }
            R.id.action_open_browser -> {
                viewModel.target.value?.url?.let {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
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
