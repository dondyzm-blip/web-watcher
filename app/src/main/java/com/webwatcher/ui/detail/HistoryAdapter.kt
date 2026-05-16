package com.webwatcher.ui.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.webwatcher.R
import com.webwatcher.data.model.AccessHistory
import com.webwatcher.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private val onItemClick: (AccessHistory) -> Unit
) : ListAdapter<AccessHistory, HistoryAdapter.ViewHolder>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AccessHistory>() {
            override fun areItemsTheSame(a: AccessHistory, b: AccessHistory) = a.id == b.id
            override fun areContentsTheSame(a: AccessHistory, b: AccessHistory) = a == b
        }
        private val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN)
    }

    inner class ViewHolder(private val b: ItemHistoryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(h: AccessHistory) {
            b.tvTime.text = sdf.format(Date(h.accessedAt))
            b.tvStatus.text = when {
                h.errorMessage != null -> "エラー: ${h.errorMessage}"
                h.hasChanged -> "✅ 変更あり"
                else -> "変更なし"
            }
            b.tvStatusCode.text = if (h.statusCode > 0) "HTTP ${h.statusCode}" else ""
            b.ivChanged.setImageResource(
                if (h.hasChanged) R.drawable.ic_changed else R.drawable.ic_no_change
            )
            b.tvDiffAvailable.visibility =
                if (h.diffSnapshotPath != null) android.view.View.VISIBLE else android.view.View.GONE
            b.root.setOnClickListener { onItemClick(h) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))
}
