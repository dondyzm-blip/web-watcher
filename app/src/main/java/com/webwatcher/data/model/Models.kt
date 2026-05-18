package com.webwatcher.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_targets")
data class WatchTarget(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val title: String,
    val intervalMinutes: Int,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastCheckedAt: Long? = null,
    val lastChangedAt: Long? = null,
    val lastContentHash: String? = null,
    val cssSelector: String? = null,
    val waitSeconds: Int = 5  // ページ読み込み後の待機時間
)

@Entity(tableName = "access_history")
data class AccessHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val targetId: Long,
    val accessedAt: Long = System.currentTimeMillis(),
    val hasChanged: Boolean,
    val contentHash: String,
    val snapshotPath: String?,
    val screenshotPath: String?,
    val diffSnapshotPath: String?,
    val statusCode: Int,
    val errorMessage: String? = null
)

enum class IntervalOption(val label: String, val minutes: Int) {
    MIN_15("15分", 15),
    MIN_30("30分", 30),
    HOUR_1("1時間", 60),
    HOUR_3("3時間", 180),
    HOUR_6("6時間", 360),
    HOUR_12("12時間", 720),
    HOUR_24("24時間", 1440)
}

enum class WaitOption(val label: String, val seconds: Int) {
    SEC_0("待機なし", 0),
    SEC_3("3秒", 3),
    SEC_5("5秒", 5),
    SEC_10("10秒", 10),
    SEC_30("30秒", 30)
}
