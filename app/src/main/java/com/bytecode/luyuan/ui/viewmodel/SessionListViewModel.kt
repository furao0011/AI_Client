package com.bytecode.luyuan.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytecode.luyuan.data.model.Session
import com.bytecode.luyuan.data.repository.AppRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionListViewModel(private val repository: AppRepository) : ViewModel() {
    val sessions: StateFlow<List<Session>> = repository.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createNewSession(onSessionCreated: (String) -> Unit) {
        viewModelScope.launch {
            val id = repository.createSession("New Chat")
            onSessionCreated(id)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
        }
    }
}
