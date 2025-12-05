package com.bytecode.luyuan.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytecode.luyuan.data.remote.AuthService
import com.bytecode.luyuan.data.remote.NetworkErrorHandler
import com.bytecode.luyuan.data.remote.api.ApiException
import com.bytecode.luyuan.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 登录页面的 ViewModel
 *
 * 负责处理用户登录逻辑，对接服务端 /api/auth/login API
 */
class LoginViewModel(
    private val repository: AppRepository,
    private val authService: AuthService
) : ViewModel() {
    
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    /**
     * 初始化时检查是否已登录
     */
    init {
        checkLoginStatus()
    }

    /**
     * 检查登录状态（App 启动时自动恢复）
     */
    private fun checkLoginStatus() {
        viewModelScope.launch {
            val isValid = authService.isLoginValid()
            if (isValid) {
                _loginState.value = LoginState.Success
            }
        }
    }

    /**
     * 用户登录
     *
     * @param username 用户名
     * @param password 密码
     */
    fun login(username: String, password: String) {
        // 输入校验
        if (username.isBlank()) {
            _loginState.value = LoginState.Error("USERNAME_EMPTY")
            return
        }
        if (password.isBlank()) {
            _loginState.value = LoginState.Error("PASSWORD_EMPTY")
            return
        }

        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            
            val result = authService.login(username.trim(), password)
            
            result.fold(
                onSuccess = {
                    _loginState.value = LoginState.Success
                },
                onFailure = { throwable ->
                    val errorMessage = when (throwable) {
                        is ApiException -> NetworkErrorHandler.getErrorMessage(throwable.code)
                        else -> throwable.message ?: "登录失败，请重试"
                    }
                    _loginState.value = LoginState.Error(errorMessage)
                }
            )
        }
    }
    
    /**
     * 重置状态
     */
    fun resetState() {
        _loginState.value = LoginState.Idle
    }

    /**
     * 设置语言（异步写入 DataStore）
     */
    fun setLanguage(lang: String) {
        viewModelScope.launch {
            repository.setLanguage(lang)
        }
    }
}

/**
 * 登录状态
 */
sealed class LoginState {
    /** 初始状态 */
    data object Idle : LoginState()
    
    /** 加载中 */
    data object Loading : LoginState()
    
    /** 登录成功 */
    data object Success : LoginState()
    
    /** 登录失败 */
    data class Error(val message: String) : LoginState()
}
