package com.bytecode.luyuan.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bytecode.luyuan.ui.AppViewModelProvider
import com.bytecode.luyuan.ui.screens.ChatScreen
import com.bytecode.luyuan.ui.screens.LoginScreen
import com.bytecode.luyuan.ui.screens.SessionListScreen
import com.bytecode.luyuan.ui.screens.SettingsScreen
import com.bytecode.luyuan.ui.viewmodel.ChatViewModel
import com.bytecode.luyuan.ui.viewmodel.LoginViewModel
import com.bytecode.luyuan.ui.viewmodel.SessionListViewModel
import com.bytecode.luyuan.ui.viewmodel.SettingsViewModel

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Login.route) {
        composable(Screen.Login.route) {
            val viewModel: LoginViewModel = viewModel(factory = AppViewModelProvider.Factory)
            LoginScreen(navController, viewModel)
        }
        composable(Screen.SessionList.route) {
            val viewModel: SessionListViewModel = viewModel(factory = AppViewModelProvider.Factory)
            SessionListScreen(navController, viewModel)
        }
        composable(
            route = Screen.Chat.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            // 使用 sessionId 作为 key，确保每个会话有独立的 ViewModel 实例
            val viewModel: ChatViewModel = viewModel(
                key = "chat_$sessionId",
                factory = AppViewModelProvider.Factory
            )
            ChatScreen(navController, viewModel, sessionId)
        }
        composable(Screen.Settings.route) {
            val viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
            SettingsScreen(navController, viewModel)
        }
    }
}
