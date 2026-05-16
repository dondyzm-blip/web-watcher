package com.webwatcher.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.webwatcher.R
import com.webwatcher.data.model.WatchTarget
import com.webwatcher.databinding.ActivityMainBinding
import com.webwatcher.service.WatchScheduler
import com.webwatcher.ui.add.AddWatchActivity
import com.webwatcher.ui.detail.DetailActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: WatchTargetAdapter

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        requestNotificationPermission()
        setupRecyclerView()
        observeData()

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddWatchActivity::class.java))
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = WatchTargetAdapter(
            onItemClick = { target ->
                startActivity(
                    Intent(this, DetailActivity::class.java).apply {
                        putExtra(DetailActivity.EXTRA_TARGET_ID, target.id)
                    }
                )
            },
            onToggleActive = { target ->
                viewModel.toggleActive(target)
                if (!target.isActive) {
                    WatchScheduler.schedule(this, target.id, target.intervalMinutes)
                } else {
                    WatchScheduler.cancel(this, target.id)
                }
            },
            onCheckNow = { target ->
                WatchScheduler.runNow(this, target.id)
                Snackbar.make(binding.root, "チェック中...", Snackbar.LENGTH_SHORT).show()
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        // スワイプ削除
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val target = adapter.getItem(viewHolder.adapterPosition)
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("削除の確認")
                    .setMessage("「${target.title}」の監視を削除しますか？\n履歴も削除されます。")
                    .setPositiveButton("削除") { _, _ ->
                        WatchScheduler.cancel(this@MainActivity, target.id)
                        viewModel.deleteTarget(target)
                    }
                    .setNegativeButton("キャンセル") { _, _ ->
                        adapter.notifyItemChanged(viewHolder.adapterPosition)
                    }
                    .show()
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.recyclerView)
    }

    private fun observeData() {
        viewModel.targets.observe(this) { targets ->
            adapter.submitList(targets)
            binding.emptyView.visibility =
                if (targets.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_check_all -> {
                viewModel.targets.value?.filter { it.isActive }?.forEach {
                    WatchScheduler.runNow(this, it.id)
                }
                Snackbar.make(binding.root, "全URLをチェック中...", Snackbar.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
