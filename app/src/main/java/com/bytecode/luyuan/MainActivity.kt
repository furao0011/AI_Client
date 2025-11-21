package com.bytecode.luyuan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.bytecode.luyuan.data.repository.MockRepository
import com.bytecode.luyuan.ui.navigation.AppNavigation
import com.bytecode.luyuan.ui.theme.ChineseStrings
import com.bytecode.luyuan.ui.theme.EnglishStrings
import com.bytecode.luyuan.ui.theme.LocalAppStrings
import com.bytecode.luyuan.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val language by MockRepository.language.collectAsState()
            val darkMode by MockRepository.darkMode.collectAsState()

            val appStrings = if (language == "Chinese") ChineseStrings else EnglishStrings

            CompositionLocalProvider(LocalAppStrings provides appStrings) {
                MyApplicationTheme(darkTheme = darkMode) {
                    AppNavigation()
                }
            }
        }
    }
}
