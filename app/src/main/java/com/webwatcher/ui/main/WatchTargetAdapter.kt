package com.webwatcher.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.webwatcher.data.model.WatchTarget
import com.webwatcher.databinding.ItemWatchTargetBinding
import java.text.SimpleDateFormat
import java.util.*

class WatchTargetAdapter(
    private val onItemClick: (WatchTarget) -> Unit,
    private val onToggleActive: (WatchTarget) -> Unit,
    private val onCheckNow: (WatchTarget) -> Unit
) : ListAdapter<WatchTarget, WatchTargetAdapter.ViewHolder>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<WatchTarget>() {
            override fun areItemsTheSame(a: WatchTarget, b: WatchTarget) = a.id == b.id
            override fun areContentsTheSame(a: WatchTarget, b: WatchTarget) = a == b
        }
        private val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN)
    }

    fun getItem(position: Int): WatchTarget = super.getItem(position)

    inner class ViewHolder(private val binding: ItemWatchTargetBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(target: WatchTarget) {
            binding.apply {
                tvTitle.text = target.title
                tvUrl.text = target.url
                tvInterval.text = "${target.intervalMinutes}分ごと"
                tvLastChecked.text = target.lastCheckedAt
                    ?.let { "最終確認: ${sdf.format(Date(it))}" }
                    ?: "未確認"
                tvLastChanged.text = target.lastChangedAt
                    ?.let { "最終変更: ${sdf.format(Date(it))}" }
                    ?: ""
                switchActive.isChecked = target.isActive
                switchActive.setOnClickListener { onToggleActive(target) }
                btnCheckNow.setOnClickListener { onCheckNow(target) }
                root.setOnClickListener { onItemClick(target) }

                // 変更インジケーター
                val hasRecentChange = target.lastChangedAt != null &&
                    (target.lastCheckedAt ?: 0L) - (target.lastChangedAt ?: 0L) < 60_000L * 60
                indicatorChanged.visibility =
                    if (hasRecentChange) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWatchTargetBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
