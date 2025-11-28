package com.bytecode.luyuan.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytecode.luyuan.data.model.Message
import com.bytecode.luyuan.data.model.Session
import com.bytecode.luyuan.data.repository.AppRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 聊天界面的 ViewModel
 * 
 * 管理当前会话的消息流，支持发送和编辑消息
 */
class ChatViewModel(private val repository: AppRepository) : ViewModel() {
    
    private val _currentSessionId = MutableStateFlow<String?>(null)
    
    /** 是否启用流式响应 */
    private val _streamingEnabled = MutableStateFlow(true)
    val streamingEnabled: StateFlow<Boolean> = _streamingEnabled.asStateFlow()
    
    /** 当前正在流式生成的消息内容 */
    private val _streamingContent = MutableStateFlow<String?>(null)
    val streamingContent: StateFlow<String?> = _streamingContent.asStateFlow()
    
    /** 是否正在加载（发送消息中） */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    /** 所有会话列表 */
    val sessions: StateFlow<List<Session>> = repository.sessions
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    /** 当前会话信息 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentSession: StateFlow<Session?> = _currentSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else repository.sessions.map { list -> list.find { it.id == id } }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    
    /**
     * 当前会话的消息列表
     * 
     * 使用 Eagerly 策略确保 Flow 在 ViewModel 生命周期内始终活跃，
     * 避免 recomposition 导致的订阅中断问题
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<Message>> = _currentSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getMessages(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    /**
     * 设置当前会话 ID
     * @param sessionId 会话唯一标识
     */
    fun setSessionId(sessionId: String) {
        if (_currentSessionId.value != sessionId) {
            _currentSessionId.value = sessionId
        }
    }
    
    /**
     * 切换流式响应开关
     */
    fun toggleStreaming(enabled: Boolean) {
        _streamingEnabled.value = enabled
    }

    fun sendMessage(content: String, imageBase64: String? = null) {
        val sessionId = _currentSessionId.value ?: return
        if (_isLoading.value) return
        
        _isLoading.value = true
        
        viewModelScope.launch {
            if (_streamingEnabled.value && imageBase64 == null) {
                // 流式响应模式
                _streamingContent.value = ""
                repository.sendMessageStream(sessionId, content, imageBase64) { token ->
                    _streamingContent.value = (_streamingContent.value ?: "") + token
                }
                _streamingContent.value = null
            } else {
                // 非流式响应模式
                repository.sendMessage(sessionId, content, imageBase64)
            }
            _isLoading.value = false
        }
    }

    fun editMessage(message: Message, newContent: String) {
        viewModelScope.launch {
            repository.editMessage(message, newContent)
        }
    }
}
