package com.bytecode.luyuan.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytecode.luyuan.data.model.Message
import com.bytecode.luyuan.data.model.Session
import com.bytecode.luyuan.data.repository.AppRepository
import com.bytecode.luyuan.data.repository.OnlineRepository
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
 * 支持本地存储和服务端同步两种模式
 */
class ChatViewModel(
    private val offlineRepository: AppRepository,
    private val onlineRepository: OnlineRepository? = null
) : ViewModel() {
    
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

    /** 错误信息 */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 是否使用服务端模式 */
    private val _useServerMode = MutableStateFlow(false)
    val useServerMode: StateFlow<Boolean> = _useServerMode.asStateFlow()

    /**
     * 获取当前有效的 Repository
     */
    private val currentRepository: AppRepository
        get() = if (_useServerMode.value && onlineRepository != null) onlineRepository else offlineRepository
    
    /** 所有会话列表（来自离线 Repository） */
    val sessions: StateFlow<List<Session>> = offlineRepository.sessions
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    /** 当前会话信息 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentSession: StateFlow<Session?> = _currentSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else offlineRepository.sessions.map { list -> list.find { it.id == id } }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    
    /**
     * 当前会话的消息列表（离线模式）
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val offlineMessages: StateFlow<List<Message>> = _currentSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else offlineRepository.getMessages(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    /**
     * 当前会话的消息列表（在线模式）
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val onlineMessages: StateFlow<List<Message>> = _currentSessionId
        .flatMapLatest { id ->
            if (id == null || onlineRepository == null) flowOf(emptyList())
            else onlineRepository.getMessages(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    /**
     * 当前会话的消息列表
     * 
     * 根据模式返回离线或在线消息
     */
    val messages: StateFlow<List<Message>>
        get() = if (_useServerMode.value && onlineRepository != null) onlineMessages else offlineMessages

    init {
        // 初始化时检查是否启用服务端模式
        viewModelScope.launch {
            offlineRepository.useServerAiService.collect { useServer ->
                _useServerMode.value = useServer
            }
        }
    }

    /**
     * 设置当前会话 ID
     * @param sessionId 会话唯一标识
     */
    fun setSessionId(sessionId: String) {
        if (_currentSessionId.value != sessionId) {
            _currentSessionId.value = sessionId
            
            // 如果是服务端模式，从服务端加载消息
            if (_useServerMode.value && onlineRepository != null) {
                loadServerMessages(sessionId)
            }
        }
    }

    /**
     * 从服务端加载消息
     */
    private fun loadServerMessages(sessionId: String) {
        if (onlineRepository == null) return
        
        viewModelScope.launch {
            val result = onlineRepository.refreshMessages(sessionId)
            result.fold(
                onSuccess = { _ ->
                    // 成功加载，数据已自动更新到 onlineMessages
                },
                onFailure = { throwable ->
                    _error.value = throwable.message ?: "加载消息失败"
                }
            )
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
        _error.value = null
        
        viewModelScope.launch {
            try {
                if (_streamingEnabled.value && imageBase64 == null) {
                    // 流式响应模式
                    _streamingContent.value = ""
                    currentRepository.sendMessageStream(sessionId, content, imageBase64) { token ->
                        _streamingContent.value = (_streamingContent.value ?: "") + token
                    }
                    _streamingContent.value = null
                } else {
                    // 非流式响应模式
                    currentRepository.sendMessage(sessionId, content, imageBase64)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "发送消息失败"
                _streamingContent.value = null
            }
            _isLoading.value = false
        }
    }

    fun editMessage(message: Message, newContent: String) {
        viewModelScope.launch {
            try {
                if (_useServerMode.value && onlineRepository != null && _streamingEnabled.value) {
                    // 服务端流式模式
                    _streamingContent.value = ""
                    onlineRepository.editMessage(message, newContent)
                    // 刷新消息列表
                    loadServerMessages(message.sessionId)
                    _streamingContent.value = null
                } else {
                    // 本地模式或非流式模式
                    currentRepository.editMessage(message, newContent)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "编辑消息失败"
                _streamingContent.value = null
            }
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _error.value = null
    }
}
