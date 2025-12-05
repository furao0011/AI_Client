package com.bytecode.luyuan.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bytecode.luyuan.AIApplication
import com.bytecode.luyuan.ui.viewmodel.ChatViewModel
import com.bytecode.luyuan.ui.viewmodel.LoginViewModel
import com.bytecode.luyuan.ui.viewmodel.RegisterViewModel
import com.bytecode.luyuan.ui.viewmodel.SessionListViewModel
import com.bytecode.luyuan.ui.viewmodel.SettingsViewModel

/**
 * ViewModel 工厂提供者
 * 
 * 【架构说明】
 * - 服务端是唯一数据源 (Single Source of Truth)
 * - 所有 ViewModel 使用 appRepository (OnlineRepository)
 * - useServerAiService 开关只控制 AI 请求路由，不影响数据源
 */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            LoginViewModel(
                aiApplication().container.appRepository,
                aiApplication().container.authService
            )
        }
        initializer {
            RegisterViewModel(
                aiApplication().container.appRepository,
                aiApplication().container.authService
            )
        }
        initializer {
            SessionListViewModel(
                repository = aiApplication().container.appRepository
            )
        }
        initializer {
            ChatViewModel(
                repository = aiApplication().container.appRepository,
                imageUploader = aiApplication().container.imageUploader
            )
        }
        initializer {
            SettingsViewModel(aiApplication().container.appRepository)
        }
    }
}

fun CreationExtras.aiApplication(): AIApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AIApplication)
