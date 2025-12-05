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
 * 【架构说明】
 * - 服务端是唯一数据源 (Single Source of Truth)
 * - 本地缓存用于加速和离线查看
 * - useServerAiService 开关只控制 AI 请求路由，不影响数据源
 */
class SessionListViewModel(
    private val repository: AppRepository
) : ViewModel() {
    
    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 错误信息
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * 会话列表 - 从服务端获取
     */
    val sessions: StateFlow<List<Session>> = repository.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // 初始化时从服务端加载会话列表
        refresh()
    }

    /**
     * 刷新会话列表（从服务端获取）
     */
    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                // OnlineRepository 实现了 refreshSessions 方法
                if (repository is OnlineRepository) {
                    val result = repository.refreshSessions()
                    result.onFailure { throwable ->
                        _error.value = throwable.message ?: "加载会话列表失败"
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "加载会话列表失败"
            }
            
            _isLoading.value = false
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
                val id = repository.createSession("New Chat")
                onSessionCreated(id)
            } catch (e: Exception) {
                _error.value = e.message ?: "创建会话失败"
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
                repository.deleteSession(sessionId)
            } catch (e: Exception) {
                _error.value = e.message ?: "删除会话失败"
            }
        }
    }

    /**
     * 清除所有历史
     */
    fun clearAllHistory() {
        viewModelScope.launch {
            try {
                repository.clearAllHistory()
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
