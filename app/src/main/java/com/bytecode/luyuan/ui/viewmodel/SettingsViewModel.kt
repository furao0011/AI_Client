package com.bytecode.luyuan.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytecode.luyuan.data.local.ApiConfig
import com.bytecode.luyuan.data.model.ApiConfigEntity
import com.bytecode.luyuan.data.model.User
import com.bytecode.luyuan.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页面的 ViewModel
 * 
 * 管理用户偏好设置，包括语言、深色模式、通知、API 配置等
 */
class SettingsViewModel(private val repository: AppRepository) : ViewModel() {
    
    val currentUser: StateFlow<User?> = repository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    /** 深色模式设置（持久化） */
    val darkModeEnabled: StateFlow<Boolean> = repository.darkMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    /** 语言设置（持久化） */
    val language: StateFlow<String> = repository.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "English")
    
    /** API 配置（持久化） */
    val apiConfig: StateFlow<ApiConfig> = repository.apiConfig
        .stateIn(
            viewModelScope, 
            SharingStarted.WhileSubscribed(5000), 
            ApiConfig("https://api.openai.com", "", "gpt-3.5-turbo", false)
        )
    
    /** 保存的多 API 配置列表 */
    val savedApiConfigs: StateFlow<List<ApiConfigEntity>> = repository.savedApiConfigs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    /** API 连接测试状态 */
    private val _apiTestState = MutableStateFlow<ApiTestState>(ApiTestState.Idle)
    val apiTestState: StateFlow<ApiTestState> = _apiTestState.asStateFlow()

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        _notificationsEnabled.value = enabled
    }

    /**
     * 切换深色模式（异步写入 DataStore）
     */
    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDarkMode(enabled)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }

    /**
     * 设置语言（异步写入 DataStore）
     */
    fun setLanguage(lang: String) {
        viewModelScope.launch {
            repository.setLanguage(lang)
        }
    }

    /**
     * 保存 API 配置
     */
    fun saveApiConfig(baseUrl: String, apiKey: String, modelName: String) {
        viewModelScope.launch {
            repository.setApiConfig(baseUrl, apiKey, modelName)
        }
    }

    /**
     * 测试 API 连接
     */
    fun testApiConnection() {
        viewModelScope.launch {
            _apiTestState.value = ApiTestState.Testing
            val result = repository.testApiConnection()
            _apiTestState.value = if (result.isSuccess) {
                ApiTestState.Success
            } else {
                ApiTestState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    /**
     * 重置测试状态
     */
    fun resetApiTestState() {
        _apiTestState.value = ApiTestState.Idle
    }
    
    /**
     * 保存新的 API 配置到列表
     */
    fun saveNewApiConfig(name: String, baseUrl: String, apiKey: String, modelName: String) {
        viewModelScope.launch {
            repository.saveApiConfig(name, baseUrl, apiKey, modelName)
        }
    }
    
    /**
     * 删除已保存的 API 配置
     */
    fun deleteApiConfig(configId: String) {
        viewModelScope.launch {
            repository.deleteApiConfig(configId)
        }
    }
    
    /**
     * 切换到指定的 API 配置
     */
    fun switchToApiConfig(configId: String) {
        viewModelScope.launch {
            repository.switchToApiConfig(configId)
        }
    }
    
    /**
     * 设置默认 API 配置
     */
    fun setDefaultApiConfig(configId: String) {
        viewModelScope.launch {
            repository.setDefaultApiConfig(configId)
        }
    }
}

/**
 * API 连接测试状态
 */
sealed class ApiTestState {
    data object Idle : ApiTestState()
    data object Testing : ApiTestState()
    data object Success : ApiTestState()
    data class Error(val message: String) : ApiTestState()
}
