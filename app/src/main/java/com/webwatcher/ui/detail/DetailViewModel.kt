package com.webwatcher.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.webwatcher.data.repository.WatchRepository

class DetailViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = WatchRepository(app)
    private val targetIdLive = MutableLiveData<Long>()

    val target = targetIdLive.switchMap { id ->
        androidx.lifecycle.liveData { emit(repo.getTargetById(id)) }
    }

    val history = targetIdLive.switchMap { id ->
        repo.getHistoryByTargetLive(id)
    }

    fun load(targetId: Long) {
        targetIdLive.value = targetId
    }
}
