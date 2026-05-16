package com.webwatcher.data.repository

import android.content.Context
import com.webwatcher.data.db.AppDatabase
import com.webwatcher.data.model.AccessHistory
import com.webwatcher.data.model.WatchTarget

class WatchRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val targetDao = db.watchTargetDao()
    private val historyDao = db.accessHistoryDao()

    // ── WatchTargets ──────────────────────────────────────────────────────────

    fun getAllTargetsLive() = targetDao.getAllLive()

    suspend fun getAllTargets() = targetDao.getAll()

    suspend fun getActiveTargets() = targetDao.getActive()

    suspend fun getTargetById(id: Long) = targetDao.getById(id)

    suspend fun addTarget(target: WatchTarget): Long = targetDao.insert(target)

    suspend fun updateTarget(target: WatchTarget) = targetDao.update(target)

    suspend fun deleteTarget(target: WatchTarget) {
        historyDao.deleteByTarget(target.id)
        targetDao.delete(target)
    }

    suspend fun updateCheckInfo(id: Long, time: Long, hash: String) =
        targetDao.updateCheckInfo(id, time, hash)

    suspend fun updateChangedAt(id: Long, time: Long) =
        targetDao.updateChangedAt(id, time)

    suspend fun setActive(id: Long, active: Boolean) =
        targetDao.setActive(id, active)

    // ── AccessHistory ─────────────────────────────────────────────────────────

    fun getHistoryByTargetLive(targetId: Long) = historyDao.getByTargetLive(targetId)

    suspend fun getHistoryByTarget(targetId: Long) = historyDao.getByTarget(targetId)

    suspend fun getHistoryById(id: Long) = historyDao.getById(id)

    suspend fun getLatestTwoHistory(targetId: Long) = historyDao.getLatestTwo(targetId)

    suspend fun insertHistory(history: AccessHistory): Long {
        val id = historyDao.insert(history)
        // 古い履歴を削除（最新50件を保持）
        historyDao.deleteOldHistory(history.targetId, 50)
        return id
    }
}
