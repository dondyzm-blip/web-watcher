package com.webwatcher.ui.add

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.webwatcher.data.model.IntervalOption
import com.webwatcher.data.model.WaitOption
import com.webwatcher.data.model.WatchTarget
import com.webwatcher.data.repository.WatchRepository
import com.webwatcher.databinding.ActivityAddWatchBinding
import com.webwatcher.service.WatchScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class AddWatchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddWatchBinding
    private val repo by lazy { WatchRepository(this) }
    private var editingTarget: WatchTarget? = null
    private var fetchTitleJob: Job? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddWatchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupSpinners()
        setupUrlAutoFetch()

        val editId = intent.getLongExtra(EXTRA_EDIT_ID, -1L)
        if (editId >= 0) {
            lifecycleScope.launch {
                repo.getTargetById(editId)?.let { target ->
                    editingTarget = target
                    binding.etTitle.setText(target.title)
                    binding.etUrl.setText(target.url)
                    binding.etSelector.setText(target.cssSelector ?: "")
                    val idx = IntervalOption.entries.indexOfFirst { it.minutes == target.intervalMinutes }
                    if (idx >= 0) binding.spinnerInterval.setSelection(idx)
                    val waitIdx = WaitOption.entries.indexOfFirst { it.seconds == target.waitSeconds }
                    if (waitIdx >= 0) binding.spinnerWait.setSelection(waitIdx)
                    supportActionBar?.title = "監視設定を編集"
                }
            }
        }

        binding.btnSave.setOnClickListener { save() }
    }

    private fun setupSpinners() {
        // 監視間隔
        ArrayAdapter(this, android.R.layout.simple_spinner_item,
            IntervalOption.entries.map { it.label }
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerInterval.adapter = it
        }
        binding.spinnerInterval.setSelection(2)

        // 待機時間
        ArrayAdapter(this, android.R.layout.simple_spinner_item,
            WaitOption.entries.map { it.label }
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerWait.adapter = it
        }
        binding.spinnerWait.setSelection(2) // デフォルト5秒
    }

    private fun setupUrlAutoFetch() {
        binding.etUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val url = s.toString().trim()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    fetchTitleJob?.cancel()
                    fetchTitleJob = lifecycleScope.launch {
                        delay(1000)
                        fetchPageTitle(url)
                    }
                }
            }
        })
    }

    private suspend fun fetchPageTitle(url: String) {
        if (binding.etTitle.text.toString().isNotBlank() && editingTarget == null) return
        binding.layoutFetching.visibility = View.VISIBLE
        try {
            val title = withContext(Dispatchers.IO) {
                val request = Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .build()
                val response = httpClient.newCall(request).execute()
                val html = response.body?.string() ?: return@withContext null
                val doc = Jsoup.parse(html)
                doc.select("meta[property=og:title]").attr("content").ifBlank {
                    doc.title().ifBlank { null }
                }
            }
            if (title != null && binding.etTitle.text.toString().isBlank()) {
                binding.etTitle.setText(title)
            }
        } catch (e: Exception) {
            // 無視
        } finally {
            binding.layoutFetching.visibility = View.GONE
        }
    }

    private fun save() {
        val title = binding.etTitle.text.toString().trim()
        val url = binding.etUrl.text.toString().trim()
        val selector = binding.etSelector.text.toString().trim().ifEmpty { null }

        if (url.isEmpty() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            binding.etUrl.error = "正しいURLを入力してください (http/https)"
            return
        }
        if (title.isEmpty()) {
            binding.etTitle.error = "サイト名を入力してください"
            return
        }

        val interval = IntervalOption.entries[binding.spinnerInterval.selectedItemPosition].minutes
        val wait = WaitOption.entries[binding.spinnerWait.selectedItemPosition].seconds

        lifecycleScope.launch {
            val existing = editingTarget
            if (existing != null) {
                repo.updateTarget(existing.copy(
                    title = title, url = url,
                    intervalMinutes = interval,
                    cssSelector = selector,
                    waitSeconds = wait
                ))
                if (existing.isActive) WatchScheduler.schedule(this@AddWatchActivity, existing.id, interval)
            } else {
                val id = repo.addTarget(WatchTarget(
                    title = title, url = url,
                    intervalMinutes = interval,
                    cssSelector = selector,
                    waitSeconds = wait
                ))
                WatchScheduler.schedule(this@AddWatchActivity, id, interval)
                WatchScheduler.runNow(this@AddWatchActivity, id)
            }
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    companion object {
        const val EXTRA_EDIT_ID = "extra_edit_id"
    }
}
