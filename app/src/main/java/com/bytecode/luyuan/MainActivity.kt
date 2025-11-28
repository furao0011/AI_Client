package com.bytecode.luyuan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.bytecode.luyuan.ui.navigation.AppNavigation
import com.bytecode.luyuan.ui.theme.ChineseStrings
import com.bytecode.luyuan.ui.theme.EnglishStrings
import com.bytecode.luyuan.ui.theme.LocalAppStrings
import com.bytecode.luyuan.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = (application as AIApplication).container
        
        setContent {
            // 从 DataStore 收集持久化的用户偏好设置
            val language by appContainer.appRepository.language.collectAsState(initial = "English")
            val darkMode by appContainer.appRepository.darkMode.collectAsState(initial = false)

            val appStrings = if (language == "中文") ChineseStrings else EnglishStrings

            CompositionLocalProvider(LocalAppStrings provides appStrings) {
                MyApplicationTheme(darkTheme = darkMode) {
                    AppNavigation()
                }
            }
        }
    }
}
