package com.bytecode.luyuan.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore 扩展属性，单例模式
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * API 配置数据类
 */
data class ApiConfig(
    val baseUrl: String,
    val apiKey: String,
    val modelName: String,
    val isConfigured: Boolean
)

/**
 * 用户偏好设置的 DataStore 管理类
 * 
 * 负责持久化用户的语言、深色模式和 API 配置
 */
class UserPreferencesDataStore(private val context: Context) {

    companion object {
        private val LANGUAGE_KEY = stringPreferencesKey("language")
        private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        
        // API 配置 Keys
        private val API_BASE_URL_KEY = stringPreferencesKey("api_base_url")
        private val API_KEY_KEY = stringPreferencesKey("api_key")
        private val API_MODEL_NAME_KEY = stringPreferencesKey("api_model_name")
        
        const val DEFAULT_LANGUAGE = "English"
        const val DEFAULT_DARK_MODE = false
        
        // 默认 API 配置
        const val DEFAULT_API_BASE_URL = "https://api.openai.com"
        const val DEFAULT_API_KEY = ""
        const val DEFAULT_MODEL_NAME = "gpt-3.5-turbo"
    }

    /**
     * 语言设置的 Flow
     */
    val language: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[LANGUAGE_KEY] ?: DEFAULT_LANGUAGE
        }

    /**
     * 深色模式设置的 Flow
     */
    val darkMode: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[DARK_MODE_KEY] ?: DEFAULT_DARK_MODE
        }

    /**
     * API 配置的 Flow
     */
    val apiConfig: Flow<ApiConfig> = context.dataStore.data
        .map { preferences ->
            ApiConfig(
                baseUrl = preferences[API_BASE_URL_KEY] ?: DEFAULT_API_BASE_URL,
                apiKey = preferences[API_KEY_KEY] ?: DEFAULT_API_KEY,
                modelName = preferences[API_MODEL_NAME_KEY] ?: DEFAULT_MODEL_NAME,
                isConfigured = !preferences[API_KEY_KEY].isNullOrBlank()
            )
        }

    /**
     * 保存语言设置
     * @param language 语言标识 ("English" 或 "中文")
     */
    suspend fun setLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = language
        }
    }

    /**
     * 保存深色模式设置
     * @param enabled 是否启用深色模式
     */
    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_MODE_KEY] = enabled
        }
    }

    /**
     * 保存 API 配置
     * @param baseUrl API 基础 URL
     * @param apiKey API 密钥
     * @param modelName 模型名称
     */
    suspend fun setApiConfig(baseUrl: String, apiKey: String, modelName: String) {
        context.dataStore.edit { preferences ->
            preferences[API_BASE_URL_KEY] = baseUrl
            preferences[API_KEY_KEY] = apiKey
            preferences[API_MODEL_NAME_KEY] = modelName
        }
    }
}
