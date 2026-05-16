package com.webwatcher.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.webwatcher.data.model.WatchTarget
import com.webwatcher.data.repository.WatchRepository
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = WatchRepository(app)
    val targets = repo.getAllTargetsLive()

    fun toggleActive(target: WatchTarget) = viewModelScope.launch {
        repo.setActive(target.id, !target.isActive)
    }

    fun deleteTarget(target: WatchTarget) = viewModelScope.launch {
        repo.deleteTarget(target)
    }
}
