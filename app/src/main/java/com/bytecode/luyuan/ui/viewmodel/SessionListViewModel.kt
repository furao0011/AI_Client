package com.bytecode.luyuan.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytecode.luyuan.data.model.Session
import com.bytecode.luyuan.data.repository.AppRepository
import com.bytecode.luyuan.data.repository.OnlineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 会话列表 ViewModel
 *
 * 负责管理会话列表的获取、创建、删除功能
 * 支持本地存储和服务端同步两种模式
 */
class SessionListViewModel(
    private val offlineRepository: AppRepository,
    private val onlineRepository: OnlineRepository? = null
) : ViewModel() {
    
    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 错误信息
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // 是否使用服务端模式
    private val _useServerMode = MutableStateFlow(false)
    val useServerMode: StateFlow<Boolean> = _useServerMode.asStateFlow()

    /**
     * 获取当前有效的 Repository
     */
    private val currentRepository: AppRepository
        get() = if (_useServerMode.value && onlineRepository != null) onlineRepository else offlineRepository

    /**
     * 会话列表
     * 
     * 根据当前模式从对应 Repository 获取
     */
    val sessions: StateFlow<List<Session>> = offlineRepository.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 在线模式会话列表
     */
    val onlineSessions: StateFlow<List<Session>> = onlineRepository?.sessions
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        ?: MutableStateFlow(emptyList())

    init {
        // 初始化时检查是否启用服务端模式并加载会话
        viewModelScope.launch {
            offlineRepository.useServerAiService.collect { useServer ->
                _useServerMode.value = useServer
                if (useServer && onlineRepository != null) {
                    loadServerSessions()
                }
            }
        }
    }

    /**
     * 从服务端加载会话列表
     */
    fun loadServerSessions() {
        if (onlineRepository == null) return
        
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            val result = onlineRepository.refreshSessions()
            result.fold(
                onSuccess = { _ ->
                    // 成功加载，数据已自动更新到 onlineSessions
                },
                onFailure = { throwable ->
                    _error.value = throwable.message ?: "加载会话列表失败"
                }
            )
            
            _isLoading.value = false
        }
    }

    /**
     * 刷新会话列表
     */
    fun refresh() {
        if (_useServerMode.value && onlineRepository != null) {
            loadServerSessions()
        }
    }

    /**
     * 创建新会话
     *
     * @param onSessionCreated 会话创建成功后的回调，传递会话 ID
     */
    fun createNewSession(onSessionCreated: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                val id = currentRepository.createSession("New Chat")
                onSessionCreated(id)
            } catch (e: Exception) {
                _error.value = e.message ?: "创建会话失败"
                // 如果在线模式失败，回退到离线模式
                if (_useServerMode.value) {
                    try {
                        val id = offlineRepository.createSession("New Chat")
                        onSessionCreated(id)
                    } catch (e2: Exception) {
                        _error.value = e2.message ?: "创建会话失败"
                    }
                }
            }
            
            _isLoading.value = false
        }
    }

    /**
     * 删除会话
     *
     * @param sessionId 要删除的会话 ID
     */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                currentRepository.deleteSession(sessionId)
            } catch (e: Exception) {
                _error.value = e.message ?: "删除会话失败"
                // 如果在线模式失败，仍然尝试删除本地
                if (_useServerMode.value) {
                    try {
                        offlineRepository.deleteSession(sessionId)
                    } catch (e2: Exception) {
                        // 忽略
                    }
                }
            }
        }
    }

    /**
     * 清除所有历史
     */
    fun clearAllHistory() {
        viewModelScope.launch {
            try {
                currentRepository.clearAllHistory()
            } catch (e: Exception) {
                _error.value = e.message ?: "清除历史失败"
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
