package com.webwatcher.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 監視対象のURL設定
 */
@Entity(tableName = "watch_targets")
data class WatchTarget(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val title: String,
    val intervalMinutes: Int,       // 監視間隔（分）
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastCheckedAt: Long? = null,
    val lastChangedAt: Long? = null,
    val lastContentHash: String? = null,  // 変更検知用ハッシュ
    val cssSelector: String? = null       // 特定要素のみ監視（オプション）
)

/**
 * アクセス履歴・キャプチャ記録
 */
@Entity(tableName = "access_history")
data class AccessHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val targetId: Long,
    val accessedAt: Long = System.currentTimeMillis(),
    val hasChanged: Boolean,
    val contentHash: String,
    val snapshotPath: String?,      // HTMLスナップショットファイルパス
    val screenshotPath: String?,    // スクリーンショットファイルパス
    val diffSnapshotPath: String?,  // 差分ハイライト済みHTMLパス
    val statusCode: Int,
    val errorMessage: String? = null
)

/**
 * 監視間隔オプション
 */
enum class IntervalOption(val label: String, val minutes: Int) {
    MIN_15("15分", 15),
    MIN_30("30分", 30),
    HOUR_1("1時間", 60),
    HOUR_3("3時間", 180),
    HOUR_6("6時間", 360),
    HOUR_12("12時間", 720),
    HOUR_24("24時間", 1440)
}
