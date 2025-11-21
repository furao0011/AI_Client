package com.bytecode.luyuan.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytecode.luyuan.data.model.Session
import com.bytecode.luyuan.data.repository.MockRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SessionListViewModel : ViewModel() {
    val sessions: StateFlow<List<Session>> = MockRepository.sessions

    fun createNewSession(onSessionCreated: (String) -> Unit) {
        viewModelScope.launch {
            val newId = MockRepository.createSession()
            onSessionCreated(newId)
        }
    }
}
