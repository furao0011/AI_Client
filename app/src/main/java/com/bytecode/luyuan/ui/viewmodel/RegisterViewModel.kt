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
 * 注册页面的 ViewModel
 *
 * 负责处理用户注册逻辑，对接服务端 /api/auth/register API
 */
class RegisterViewModel(
    private val repository: AppRepository,
    private val authService: AuthService
) : ViewModel() {
    
    private val _registerState = MutableStateFlow<RegisterState>(RegisterState.Idle)
    val registerState: StateFlow<RegisterState> = _registerState.asStateFlow()

    /**
     * 用户注册
     *
     * @param username 用户名（3-20字符）
     * @param password 密码（6-32字符）
     * @param confirmPassword 确认密码
     * @param email 邮箱（可选）
     */
    fun register(
        username: String,
        password: String,
        confirmPassword: String,
        email: String?
    ) {
        // 输入校验
        val validationError = validateInput(username, password, confirmPassword, email)
        if (validationError != null) {
            _registerState.value = RegisterState.Error(validationError)
            return
        }

        viewModelScope.launch {
            _registerState.value = RegisterState.Loading
            
            // 处理邮箱：空字符串转为 null
            val emailToSend = email?.takeIf { it.isNotBlank() }
            
            val result = authService.register(
                username = username.trim(),
                password = password,
                email = emailToSend
            )
            
            result.fold(
                onSuccess = {
                    _registerState.value = RegisterState.Success
                },
                onFailure = { throwable ->
                    val errorMessage = when (throwable) {
                        is ApiException -> NetworkErrorHandler.getErrorMessage(throwable.code)
                        else -> throwable.message ?: "注册失败，请重试"
                    }
                    _registerState.value = RegisterState.Error(errorMessage)
                }
            )
        }
    }
    
    /**
     * 验证输入
     * 
     * @return 错误消息，如果验证通过返回 null
     */
    private fun validateInput(
        username: String,
        password: String,
        confirmPassword: String,
        email: String?
    ): String? {
        // 用户名校验
        if (username.isBlank()) {
            return "USERNAME_EMPTY"
        }
        if (username.trim().length < 3) {
            return "USERNAME_TOO_SHORT"
        }
        if (username.trim().length > 20) {
            return "USERNAME_TOO_LONG"
        }
        
        // 密码校验
        if (password.isBlank()) {
            return "PASSWORD_EMPTY"
        }
        if (password.length < 6) {
            return "PASSWORD_TOO_SHORT"
        }
        if (password.length > 32) {
            return "PASSWORD_TOO_LONG"
        }
        
        // 确认密码校验
        if (password != confirmPassword) {
            return "PASSWORD_MISMATCH"
        }
        
        // 邮箱校验（如果有填写）
        if (!email.isNullOrBlank()) {
            val emailPattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".toRegex()
            if (!emailPattern.matches(email)) {
                return "INVALID_EMAIL"
            }
        }
        
        return null
    }
    
    /**
     * 重置状态
     */
    fun resetState() {
        _registerState.value = RegisterState.Idle
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
 * 注册状态
 */
sealed class RegisterState {
    /** 初始状态 */
    data object Idle : RegisterState()
    
    /** 加载中 */
    data object Loading : RegisterState()
    
    /** 注册成功 */
    data object Success : RegisterState()
    
    /** 注册失败 */
    data class Error(val message: String) : RegisterState()
}
