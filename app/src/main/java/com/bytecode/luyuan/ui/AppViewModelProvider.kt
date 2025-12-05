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
                offlineRepository = aiApplication().container.offlineRepository,
                onlineRepository = aiApplication().container.onlineRepository
            )
        }
        initializer {
            ChatViewModel(
                offlineRepository = aiApplication().container.offlineRepository,
                onlineRepository = aiApplication().container.onlineRepository
            )
        }
        initializer {
            SettingsViewModel(aiApplication().container.appRepository)
        }
    }
}

fun CreationExtras.aiApplication(): AIApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AIApplication)
