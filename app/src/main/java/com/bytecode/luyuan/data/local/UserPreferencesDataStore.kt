package com.bytecode.luyuan.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * DataStore 扩展属性，单例模式
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * API 配置数据类
 * 
 * @param baseUrl API 基础 URL
 * @param apiKey API 密钥
 * @param modelName 模型名称
 * @param isConfigured 是否已配置（API Key 不为空）
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
 * 负责持久化用户的语言、深色模式、API 配置和认证信息
 */
class UserPreferencesDataStore(private val context: Context) {

    companion object {
        // 基础设置 Keys
        private val LANGUAGE_KEY = stringPreferencesKey("language")
        private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        
        // 自定义 API 配置 Keys
        private val API_BASE_URL_KEY = stringPreferencesKey("api_base_url")
        private val API_KEY_KEY = stringPreferencesKey("api_key")
        private val API_MODEL_NAME_KEY = stringPreferencesKey("api_model_name")
        
        // 认证相关 Keys
        private val AUTH_TOKEN_KEY = stringPreferencesKey("auth_token")
        private val TOKEN_EXPIRES_AT_KEY = longPreferencesKey("token_expires_at")
        
        // 服务端配置 Keys
        private val SERVER_BASE_URL_KEY = stringPreferencesKey("server_base_url")
        private val USE_SERVER_AI_SERVICE_KEY = booleanPreferencesKey("use_server_ai_service")
        
        const val DEFAULT_LANGUAGE = "English"
        const val DEFAULT_DARK_MODE = false
        
        // 默认 API 配置
        const val DEFAULT_API_BASE_URL = "https://api.openai.com"
        const val DEFAULT_API_KEY = ""
        const val DEFAULT_MODEL_NAME = "gpt-3.5-turbo"
        
        // 默认服务端配置
        const val DEFAULT_SERVER_BASE_URL = "http://localhost:8080"
        const val DEFAULT_USE_SERVER_AI_SERVICE = true
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
     * 自定义 API 配置的 Flow
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

    // ==================== 认证相关 ====================

    /**
     * 认证 Token 的 Flow
     */
    val authToken: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[AUTH_TOKEN_KEY]
        }

    /**
     * Token 过期时间的 Flow（毫秒时间戳）
     */
    val tokenExpiresAt: Flow<Long?> = context.dataStore.data
        .map { preferences ->
            preferences[TOKEN_EXPIRES_AT_KEY]
        }

    // ==================== 服务端配置相关 ====================

    /**
     * 服务端基础 URL 的 Flow
     */
    val serverBaseUrl: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[SERVER_BASE_URL_KEY] ?: DEFAULT_SERVER_BASE_URL
        }

    /**
     * 是否使用服务端 AI 服务的 Flow
     * 
     * true: 使用服务端 AI 网关，不需要自定义 API Key
     * false: 使用自定义 API 配置，直连 AI API
     */
    val useServerAiService: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[USE_SERVER_AI_SERVICE_KEY] ?: DEFAULT_USE_SERVER_AI_SERVICE
        }

    /**
     * 有效的 API 配置 Flow
     * 
     * 根据 useServerAiService 设置返回服务端配置或自定义配置
     */
    val effectiveApiConfig: Flow<ApiConfig> = combine(
        useServerAiService,
        serverBaseUrl,
        apiConfig
    ) { useServer, serverUrl, customConfig ->
        if (useServer) {
            ApiConfig(
                baseUrl = "$serverUrl/",
                apiKey = "", // 服务端模式使用 authToken
                modelName = "qwen-turbo",
                isConfigured = true
            )
        } else {
            customConfig
        }
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
     * 保存自定义 API 配置
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

    // ==================== 认证相关方法 ====================

    /**
     * 保存认证 Token
     * @param token JWT Token
     * @param expiresAt 过期时间戳（毫秒），可选
     */
    suspend fun setAuthToken(token: String, expiresAt: Long? = null) {
        context.dataStore.edit { preferences ->
            preferences[AUTH_TOKEN_KEY] = token
            if (expiresAt != null) {
                preferences[TOKEN_EXPIRES_AT_KEY] = expiresAt
            }
        }
    }

    /**
     * 清除认证 Token（登出时调用）
     */
    suspend fun clearAuthToken() {
        context.dataStore.edit { preferences ->
            preferences.remove(AUTH_TOKEN_KEY)
            preferences.remove(TOKEN_EXPIRES_AT_KEY)
        }
    }

    // ==================== 服务端配置方法 ====================

    /**
     * 保存服务端基础 URL
     * @param baseUrl 服务端地址
     */
    suspend fun setServerBaseUrl(baseUrl: String) {
        context.dataStore.edit { preferences ->
            preferences[SERVER_BASE_URL_KEY] = baseUrl
        }
    }

    /**
     * 设置是否使用服务端 AI 服务
     * @param useServer true 使用服务端网关，false 使用自定义 API
     */
    suspend fun setUseServerAiService(useServer: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_SERVER_AI_SERVICE_KEY] = useServer
        }
    }
}
