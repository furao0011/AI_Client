package com.bytecode.luyuan.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bytecode.luyuan.AIApplication
import com.bytecode.luyuan.ui.viewmodel.ChatViewModel
import com.bytecode.luyuan.ui.viewmodel.LoginViewModel
import com.bytecode.luyuan.ui.viewmodel.SessionListViewModel
import com.bytecode.luyuan.ui.viewmodel.SettingsViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            LoginViewModel(aiApplication().container.appRepository)
        }
        initializer {
            SessionListViewModel(aiApplication().container.appRepository)
        }
        initializer {
            ChatViewModel(aiApplication().container.appRepository)
        }
        initializer {
            SettingsViewModel(aiApplication().container.appRepository)
        }
    }
}

fun CreationExtras.aiApplication(): AIApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AIApplication)
