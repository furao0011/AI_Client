package com.bytecode.luyuan.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.bytecode.luyuan.data.model.User
import com.bytecode.luyuan.data.repository.MockRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel : ViewModel() {
    val currentUser: StateFlow<User?> = MockRepository.currentUser

    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    val darkModeEnabled: StateFlow<Boolean> = MockRepository.darkMode
    val language: StateFlow<String> = MockRepository.language

    fun logout() {
        MockRepository.logout()
    }

    fun toggleNotifications(enabled: Boolean) {
        _notificationsEnabled.value = enabled
    }

    fun toggleDarkMode(enabled: Boolean) {
        MockRepository.setDarkMode(enabled)
    }

    fun clearHistory() {
        // TODO: Implement clear history logic in Repository
    }

    fun setLanguage(lang: String) {
        MockRepository.setLanguage(lang)
    }
}
