package com.bytecode.luyuan.data.repository

import com.bytecode.luyuan.data.local.ApiConfig
import com.bytecode.luyuan.data.model.ApiConfigEntity
import com.bytecode.luyuan.data.model.Message
import com.bytecode.luyuan.data.model.Session
import com.bytecode.luyuan.data.model.User
import kotlinx.coroutines.flow.Flow

/**
 * 应用数据层核心接口
 * 
 * 定义所有数据操作的契约，供 UI 层调用
 */
interface AppRepository {
    val currentUser: Flow<User?>
    val sessions: Flow<List<Session>>
    
    /** 用户语言偏好 */
    val language: Flow<String>
    
    /** 深色模式偏好 */
    val darkMode: Flow<Boolean>
    
    /** API 配置 */
    val apiConfig: Flow<ApiConfig>
    
    /** 所有保存的 API 配置列表 */
    val savedApiConfigs: Flow<List<ApiConfigEntity>>

    /** 设置语言 */
    suspend fun setLanguage(lang: String)
    
    /** 设置深色模式 */
    suspend fun setDarkMode(enabled: Boolean)
    
    /** 设置 API 配置 */
    suspend fun setApiConfig(baseUrl: String, apiKey: String, modelName: String)
    
    /** 测试 API 连接 */
    suspend fun testApiConnection(): Result<Boolean>
    
    /** 保存新的 API 配置 */
    suspend fun saveApiConfig(name: String, baseUrl: String, apiKey: String, modelName: String)
    
    /** 删除已保存的 API 配置 */
    suspend fun deleteApiConfig(configId: String)
    
    /** 切换到指定的 API 配置 */
    suspend fun switchToApiConfig(configId: String)
    
    /** 将指定配置设为默认 */
    suspend fun setDefaultApiConfig(configId: String)

    suspend fun login(username: String, password: String): Boolean
    suspend fun logout()

    fun getMessages(sessionId: String): Flow<List<Message>>
    suspend fun sendMessage(sessionId: String, content: String, imageBase64: String? = null)
    suspend fun createSession(title: String): String
    suspend fun deleteSession(sessionId: String)
    suspend fun clearAllHistory()
    suspend fun editMessage(message: Message, newContent: String)
    
    /** 流式发送消息（SSE Streaming） */
    suspend fun sendMessageStream(
        sessionId: String, 
        content: String, 
        imageBase64: String? = null,
        onToken: suspend (String) -> Unit
    )
}
