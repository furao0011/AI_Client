package com.bytecode.luyuan.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytecode.luyuan.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 登录页面的 ViewModel
 */
class LoginViewModel(private val repository: AppRepository) : ViewModel() {
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            val success = repository.login(username, password)
            if (success) {
                _loginState.value = LoginState.Success
            } else {
                _loginState.value = LoginState.Error("Invalid username or password")
            }
        }
    }
    
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

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}
