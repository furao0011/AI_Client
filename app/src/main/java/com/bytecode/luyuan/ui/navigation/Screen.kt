package com.bytecode.luyuan.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object SessionList : Screen("session_list")
    object Chat : Screen("chat/{sessionId}") {
        fun createRoute(sessionId: String) = "chat/$sessionId"
    }
    object Settings : Screen("settings")
}
