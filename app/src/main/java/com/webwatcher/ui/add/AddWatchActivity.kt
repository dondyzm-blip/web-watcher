package com.webwatcher.ui.add

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.webwatcher.data.model.IntervalOption
import com.webwatcher.data.model.WatchTarget
import com.webwatcher.data.repository.WatchRepository
import com.webwatcher.databinding.ActivityAddWatchBinding
import com.webwatcher.service.WatchScheduler
import kotlinx.coroutines.launch

class AddWatchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddWatchBinding
    private val repo by lazy { WatchRepository(this) }
    private var editingTarget: WatchTarget? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddWatchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupIntervalSpinner()

        // 編集モード
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
                    supportActionBar?.title = "監視設定を編集"
                }
            }
        }

        binding.btnSave.setOnClickListener { save() }
    }

    private fun setupIntervalSpinner() {
        val labels = IntervalOption.entries.map { it.label }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerInterval.adapter = adapter
        binding.spinnerInterval.setSelection(2) // デフォルト1時間
    }

    private fun save() {
        val title = binding.etTitle.text.toString().trim()
        val url = binding.etUrl.text.toString().trim()
        val selector = binding.etSelector.text.toString().trim().ifEmpty { null }

        if (title.isEmpty()) { binding.tilTitle.error = "タイトルを入力してください"; return }
        if (url.isEmpty() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            binding.tilUrl.error = "正しいURLを入力してください (http/https)"
            return
        }

        val interval = IntervalOption.entries[binding.spinnerInterval.selectedItemPosition].minutes

        lifecycleScope.launch {
            val existing = editingTarget
            if (existing != null) {
                val updated = existing.copy(
                    title = title, url = url,
                    intervalMinutes = interval, cssSelector = selector
                )
                repo.updateTarget(updated)
                if (updated.isActive) WatchScheduler.schedule(this@AddWatchActivity, updated.id, interval)
            } else {
                val target = WatchTarget(
                    title = title, url = url,
                    intervalMinutes = interval, cssSelector = selector
                )
                val id = repo.addTarget(target)
                WatchScheduler.schedule(this@AddWatchActivity, id, interval)
                // 即時チェック
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
