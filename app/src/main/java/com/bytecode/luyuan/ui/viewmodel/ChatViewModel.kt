package com.bytecode.luyuan.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytecode.luyuan.data.model.Message
import com.bytecode.luyuan.data.repository.MockRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    
    private val _currentSessionId = MutableStateFlow<String?>(null)
    
    val messages: StateFlow<List<Message>> = MockRepository.allMessages
        .map { allMessagesMap ->
            _currentSessionId.value?.let { id -> allMessagesMap[id] } ?: emptyList()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setSessionId(sessionId: String) {
        _currentSessionId.value = sessionId
    }

    fun sendMessage(content: String) {
        val sessionId = _currentSessionId.value ?: return
        viewModelScope.launch {
            MockRepository.sendMessage(sessionId, content)
        }
    }
}
