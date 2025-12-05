package com.bytecode.luyuan.data.repository

import android.util.Log
import com.bytecode.luyuan.data.local.ApiConfig
import com.bytecode.luyuan.data.local.ApiConfigDao
import com.bytecode.luyuan.data.local.UserPreferencesDataStore
import com.bytecode.luyuan.data.model.ApiConfigEntity
import com.bytecode.luyuan.data.model.Message
import com.bytecode.luyuan.data.model.Session
import com.bytecode.luyuan.data.model.User
import com.bytecode.luyuan.data.remote.AuthService
import com.bytecode.luyuan.data.remote.NetworkErrorHandler
import com.bytecode.luyuan.data.remote.api.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.bytecode.luyuan.data.remote.OpenAiService
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

/**
 * 统一 Repository 实现
 * 
 * 【架构说明】
 * - 服务端是唯一数据源 (Single Source of Truth)
 * - 所有会话和消息数据从服务端获取
 * - 本地缓存用于加速和离线查看
 * - useServerAiService 开关只控制 AI 请求路由:
 *   - true: AI 请求走服务端网关 /api/sessions/{id}/messages/stream
 *   - false: AI 请求走自定义 API (OpenAiService)
 * 
 * @param authService 认证服务
 * @param userPreferencesDataStore 用户偏好存储
 * @param apiConfigDao API 配置数据访问对象
 * @param openAiService OpenAI 服务（用于自定义 API 模式）
 */
class OnlineRepository(
    private val authService: AuthService,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val apiConfigDao: ApiConfigDao,
    private val openAiService: OpenAiService? = null
) : AppRepository {

    companion object {
        private const val TAG = "OnlineRepository"
    }

    private val gson = Gson()

    // ==================== 缓存的状态 ====================
    
    private val _sessionsCache = MutableStateFlow<List<Session>>(emptyList())
    private val _messagesCache = mutableMapOf<String, MutableStateFlow<List<Message>>>()

    // ==================== API 实例 ====================
    
    private var currentBaseUrl: String? = null
    private var retrofit: Retrofit? = null
    private var sessionApi: SessionApi? = null
    private var messageApi: MessageApi? = null

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)  // 流式响应需要更长的读取超时
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 获取或创建 API 实例
     */
    private suspend fun <T> getApi(apiClass: Class<T>): T {
        val baseUrl = userPreferencesDataStore.serverBaseUrl.first()
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        if (currentBaseUrl != normalizedUrl || retrofit == null) {
            currentBaseUrl = normalizedUrl
            retrofit = Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
            
            // 重置 API 实例
            sessionApi = null
            messageApi = null
        }

        return when (apiClass) {
            SessionApi::class.java -> {
                if (sessionApi == null) {
                    sessionApi = retrofit!!.create(SessionApi::class.java)
                }
                @Suppress("UNCHECKED_CAST")
                sessionApi as T
            }
            MessageApi::class.java -> {
                if (messageApi == null) {
                    messageApi = retrofit!!.create(MessageApi::class.java)
                }
                @Suppress("UNCHECKED_CAST")
                messageApi as T
            }
            else -> retrofit!!.create(apiClass)
        }
    }

    // ==================== 实现 AppRepository 接口 ====================

    override val currentUser: Flow<User?> = authService.currentUser.map { profile ->
        profile?.let { 
            User(
                id = it.id,
                username = it.username,
                email = it.email ?: "",
                avatarUrl = it.avatarUrl
            )
        }
    }

    override val sessions: Flow<List<Session>> = _sessionsCache

    override val language: Flow<String> = userPreferencesDataStore.language
    override val darkMode: Flow<Boolean> = userPreferencesDataStore.darkMode
    override val apiConfig: Flow<ApiConfig> = userPreferencesDataStore.apiConfig
    override val savedApiConfigs: Flow<List<ApiConfigEntity>> = apiConfigDao.getAllConfigs()
    override val useServerAiService: Flow<Boolean> = userPreferencesDataStore.useServerAiService

    override suspend fun setLanguage(lang: String) {
        userPreferencesDataStore.setLanguage(lang)
    }

    override suspend fun setDarkMode(enabled: Boolean) {
        userPreferencesDataStore.setDarkMode(enabled)
    }

    override suspend fun setApiConfig(baseUrl: String, apiKey: String, modelName: String) {
        userPreferencesDataStore.setApiConfig(baseUrl, apiKey, modelName)
    }

    override suspend fun testApiConnection(): Result<Boolean> {
        // 在线模式下不需要测试自定义 API
        return Result.success(true)
    }

    override suspend fun saveApiConfig(name: String, baseUrl: String, apiKey: String, modelName: String) {
        val config = ApiConfigEntity(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelName = modelName,
            isDefault = false,
            createdAt = System.currentTimeMillis()
        )
        apiConfigDao.insertConfig(config)
    }

    override suspend fun deleteApiConfig(configId: String) {
        apiConfigDao.deleteConfigById(configId)
    }

    override suspend fun switchToApiConfig(configId: String) {
        val config = apiConfigDao.getConfigById(configId) ?: return
        userPreferencesDataStore.setApiConfig(config.baseUrl, config.apiKey, config.modelName)
    }

    override suspend fun setDefaultApiConfig(configId: String) {
        apiConfigDao.clearAllDefaults()
        apiConfigDao.setAsDefault(configId)
    }
    
    override suspend fun setUseServerAiService(useServer: Boolean) {
        userPreferencesDataStore.setUseServerAiService(useServer)
    }

    // ==================== 认证相关 ====================

    override suspend fun login(username: String, password: String): Boolean {
        val result = authService.login(username, password)
        if (result.isSuccess) {
            // 登录成功后刷新会话列表
            refreshSessions()
        }
        return result.isSuccess
    }

    override suspend fun logout() {
        authService.logout()
        _sessionsCache.value = emptyList()
        _messagesCache.clear()
    }

    // ==================== 会话管理 ====================

    /**
     * 从服务端刷新会话列表
     */
    suspend fun refreshSessions(page: Int = 0, size: Int = 20): Result<List<Session>> {
        return withContext(Dispatchers.IO) {
            try {
                val api = getApi(SessionApi::class.java)
                val token = authService.getAuthHeader()
                
                if (token.isBlank()) {
                    return@withContext Result.failure(Exception("未登录"))
                }

                val response = api.getSessions(token, page, size)
                
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.isSuccess == true && body.data != null) {
                        val sessions = body.data.sessions.map { it.toSession() }
                        
                        // 如果是第一页，替换缓存；否则追加
                        if (page == 0) {
                            _sessionsCache.value = sessions
                        } else {
                            _sessionsCache.value = _sessionsCache.value + sessions
                        }
                        
                        Result.success(sessions)
                    } else {
                        handleApiError(body)
                    }
                } else {
                    Result.failure(Exception("HTTP ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh sessions", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun createSession(title: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val api = getApi(SessionApi::class.java)
                val token = authService.getAuthHeader()
                
                val response = api.createSession(token, CreateSessionRequest(title))
                
                if (response.isSuccessful && response.body()?.isSuccess == true) {
                    val sessionInfo = response.body()?.data
                    if (sessionInfo != null) {
                        val session = sessionInfo.toSession()
                        // 添加到缓存头部
                        _sessionsCache.value = listOf(session) + _sessionsCache.value
                        return@withContext session.id
                    }
                }
                
                throw Exception("创建会话失败")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create session", e)
                throw e
            }
        }
    }

    override suspend fun insertSession(session: Session) {
        // 在线模式下直接添加到缓存
        _sessionsCache.value = listOf(session) + _sessionsCache.value.filter { it.id != session.id }
    }

    override suspend fun deleteSession(sessionId: String) {
        withContext(Dispatchers.IO) {
            try {
                val api = getApi(SessionApi::class.java)
                val token = authService.getAuthHeader()
                
                val response = api.deleteSession(token, sessionId)
                
                if (response.isSuccessful && response.body()?.isSuccess == true) {
                    // 从缓存中移除
                    _sessionsCache.value = _sessionsCache.value.filter { it.id != sessionId }
                    _messagesCache.remove(sessionId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete session", e)
            }
        }
    }

    override suspend fun clearAllHistory() {
        withContext(Dispatchers.IO) {
            try {
                val api = getApi(SessionApi::class.java)
                val token = authService.getAuthHeader()
                
                val response = api.clearAllSessions(token)
                
                if (response.isSuccessful && response.body()?.isSuccess == true) {
                    _sessionsCache.value = emptyList()
                    _messagesCache.clear()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear all sessions", e)
            }
        }
    }

    // ==================== 消息管理 ====================

    override fun getMessages(sessionId: String): Flow<List<Message>> {
        // 获取或创建该会话的消息缓存
        return _messagesCache.getOrPut(sessionId) {
            MutableStateFlow<List<Message>>(emptyList())
        }
    }

    /**
     * 从服务端刷新消息列表
     */
    suspend fun refreshMessages(sessionId: String, page: Int = 0, size: Int = 50): Result<List<Message>> {
        return withContext(Dispatchers.IO) {
            try {
                val api = getApi(MessageApi::class.java)
                val token = authService.getAuthHeader()
                
                if (token.isBlank()) {
                    return@withContext Result.failure(Exception("未登录"))
                }

                val response = api.getMessages(token, sessionId, page, size)
                
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.isSuccess == true && body.data != null) {
                        val messages = body.data.messages.map { it.toMessage() }
                        
                        val messagesFlow = _messagesCache.getOrPut(sessionId) {
                            MutableStateFlow(emptyList())
                        }
                        
                        // 如果是第一页，替换；否则追加
                        if (page == 0) {
                            messagesFlow.value = messages
                        } else {
                            messagesFlow.value = messagesFlow.value + messages
                        }
                        
                        Result.success(messages)
                    } else {
                        handleApiError(body)
                    }
                } else {
                    Result.failure(Exception("HTTP ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh messages", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun insertMessage(message: Message) {
        // 在线模式下添加到缓存
        val messagesFlow = _messagesCache.getOrPut(message.sessionId) {
            MutableStateFlow(emptyList())
        }
        messagesFlow.value = messagesFlow.value + message
    }

    override suspend fun sendMessage(sessionId: String, content: String, imageBase64: String?) {
        // 在线模式下，imageBase64 参数实际上应该传入 imageUrl
        // 因为图片已经通过 ImageUploader 上传到服务端
        // 这里为了兼容接口，将参数名保持为 imageBase64，但实际使用为 imageUrl
        sendMessageWithUrl(sessionId, content, imageBase64)
    }
    
    /**
     * 发送消息（v0.1.7.3 新增）
     * 
     * 支持传入服务端图片 URL
     * 
     * @param sessionId 会话 ID
     * @param content 消息内容
     * @param imageUrl 服务端图片 URL（可选）
     */
    suspend fun sendMessageWithUrl(sessionId: String, content: String, imageUrl: String?) {
        withContext(Dispatchers.IO) {
            try {
                val api = getApi(MessageApi::class.java)
                val token = authService.getAuthHeader()
                
                val request = SendMessageRequest(content = content, imageUrl = imageUrl)
                val response = api.sendMessage(token, sessionId, request)
                
                if (response.isSuccessful && response.body()?.isSuccess == true) {
                    val result = response.body()?.data
                    if (result != null) {
                        // 添加用户消息到缓存
                        val userMessage = result.userMessage.toMessage()
                        val messagesFlow = _messagesCache.getOrPut(sessionId) {
                            MutableStateFlow(emptyList())
                        }
                        messagesFlow.value = messagesFlow.value + userMessage
                        
                        // 添加 AI 回复到缓存
                        result.aiMessage?.let { aiInfo ->
                            val aiMessage = aiInfo.toMessage()
                            messagesFlow.value = messagesFlow.value + aiMessage
                        }
                        
                        // 更新会话标题（如果返回了）
                        result.sessionTitle?.let { title ->
                            updateSessionInCache(sessionId, title, result.aiMessage?.content ?: content)
                        }
                        
                        // 更新会话最后消息
                        updateSessionLastMessage(sessionId, result.aiMessage?.content ?: content)
                    }
                } else {
                    throw Exception("发送消息失败: ${response.body()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                throw e
            }
        }
    }

    override suspend fun sendMessageStream(
        sessionId: String,
        content: String,
        imageBase64: String?,
        onToken: suspend (String) -> Unit
    ) {
        // 在线模式下，imageBase64 参数实际上应该传入 imageUrl
        sendMessageStreamWithUrl(sessionId, content, imageBase64, onToken)
    }
    
    /**
     * 流式发送消息（v0.1.7.3 新增）
     * 
     * 支持传入服务端图片 URL
     */
    suspend fun sendMessageStreamWithUrl(
        sessionId: String,
        content: String,
        imageUrl: String?,
        onToken: suspend (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val api = getApi(MessageApi::class.java)
                val token = authService.getAuthHeader()
                
                val request = SendMessageRequest(content = content, imageUrl = imageUrl)
                val response = api.sendMessageStream(token, sessionId, request)
                
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody != null) {
                        // 处理 SSE 流式响应
                        processStreamResponse(sessionId, content, responseBody.byteStream().bufferedReader(), onToken)
                    }
                } else {
                    throw Exception("流式请求失败: HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send stream message", e)
                // 回退到非流式发送
                onToken("Error: ${e.message}")
            }
        }
    }

    /**
     * 处理 SSE 流式响应
     * 
     * SSE 格式:
     * data: {"type":"user_message","data":{"id":"m_xxx","content":"你好"}}
     * data: {"type":"ai_token","data":"你"}
     * data: {"type":"ai_message","data":{"id":"m_yyy","content":"你好！"}}
     * data: {"type":"session_title","data":"问候对话"}
     * data: [DONE]
     */
    private suspend fun processStreamResponse(
        sessionId: String,
        userContent: String,
        reader: BufferedReader,
        onToken: suspend (String) -> Unit
    ) {
        val messagesFlow = _messagesCache.getOrPut(sessionId) {
            MutableStateFlow(emptyList())
        }
        
        val aiContentBuilder = StringBuilder()
        var userMessageAdded = false
        var aiMessageAdded = false
        var sessionTitle: String? = null

        try {
            reader.useLines { lines ->
                for (line in lines) {
                    if (line.isBlank()) continue
                    
                    if (!line.startsWith("data:")) continue
                    
                    val data = line.removePrefix("data:").trim()
                    
                    if (data == "[DONE]") {
                        break
                    }
                    
                    try {
                        val jsonObject = gson.fromJson(data, JsonObject::class.java)
                        val type = jsonObject.get("type")?.asString
                        val eventData = jsonObject.get("data")
                        
                        when (type) {
                            StreamEventType.USER_MESSAGE -> {
                                // 用户消息已发送
                                val messageObj = eventData?.asJsonObject
                                val userMessageId = messageObj?.get("id")?.asString
                                val messageContent = messageObj?.get("content")?.asString ?: userContent
                                
                                val userMessage = Message(
                                    id = userMessageId ?: java.util.UUID.randomUUID().toString(),
                                    sessionId = sessionId,
                                    content = messageContent,
                                    isUser = true,
                                    timestamp = System.currentTimeMillis(),
                                    imageBase64 = null
                                )
                                messagesFlow.value = messagesFlow.value + userMessage
                                userMessageAdded = true
                            }
                            
                            StreamEventType.AI_TOKEN -> {
                                // AI 回复的 token
                                val tokenText = eventData?.asString ?: ""
                                aiContentBuilder.append(tokenText)
                                withContext(Dispatchers.Main) {
                                    onToken(tokenText)
                                }
                            }
                            
                            StreamEventType.AI_MESSAGE -> {
                                // AI 完整回复
                                val messageObj = eventData?.asJsonObject
                                val aiMessageId = messageObj?.get("id")?.asString
                                val finalContent = messageObj?.get("content")?.asString ?: aiContentBuilder.toString()
                                
                                val aiMessage = Message(
                                    id = aiMessageId ?: java.util.UUID.randomUUID().toString(),
                                    sessionId = sessionId,
                                    content = finalContent,
                                    isUser = false,
                                    timestamp = System.currentTimeMillis(),
                                    imageBase64 = null
                                )
                                messagesFlow.value = messagesFlow.value + aiMessage
                                aiMessageAdded = true
                                
                                // 更新会话最后消息
                                updateSessionLastMessage(sessionId, finalContent)
                            }
                            
                            StreamEventType.SESSION_TITLE -> {
                                // 会话标题
                                sessionTitle = eventData?.asString
                                sessionTitle?.let { title ->
                                    updateSessionInCache(sessionId, title, aiContentBuilder.toString())
                                }
                            }
                            
                            StreamEventType.ERROR -> {
                                // 错误
                                val errorMsg = eventData?.asString ?: "未知错误"
                                Log.e(TAG, "SSE Error: $errorMsg")
                                // 不抛出异常，继续处理
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse SSE event: $data", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SSE stream error", e)
        } finally {
            reader.close()
            
            // 如果 AI 回复有内容但未添加完整消息，保存已收到的内容
            if (!aiMessageAdded && aiContentBuilder.isNotEmpty()) {
                val aiMessage = Message(
                    id = java.util.UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    content = aiContentBuilder.toString(),
                    isUser = false,
                    timestamp = System.currentTimeMillis(),
                    imageBase64 = null
                )
                messagesFlow.value = messagesFlow.value + aiMessage
                updateSessionLastMessage(sessionId, aiContentBuilder.toString())
                Log.d(TAG, "Saved incomplete AI message: ${aiContentBuilder.length} chars")
            }
            
            // 如果用户消息都没发出去，添加一个本地记录
            if (!userMessageAdded) {
                val userMessage = Message(
                    id = java.util.UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    content = userContent,
                    isUser = true,
                    timestamp = System.currentTimeMillis(),
                    imageBase64 = null
                )
                messagesFlow.value = messagesFlow.value + userMessage
                Log.d(TAG, "Added local user message as server didn't confirm")
            }
        }
    }

    override suspend fun editMessage(message: Message, newContent: String) {
        withContext(Dispatchers.IO) {
            try {
                val api = getApi(MessageApi::class.java)
                val token = authService.getAuthHeader()
                
                val request = EditMessageRequest(content = newContent)
                val response = api.editMessage(token, message.sessionId, message.id, request)
                
                if (response.isSuccessful && response.body()?.isSuccess == true) {
                    // 刷新消息列表
                    refreshMessages(message.sessionId)
                } else {
                    throw Exception("编辑消息失败: ${response.body()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to edit message", e)
                throw e
            }
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 更新缓存中的会话信息
     */
    private fun updateSessionInCache(sessionId: String, title: String, lastMessage: String) {
        _sessionsCache.value = _sessionsCache.value.map { session ->
            if (session.id == sessionId) {
                session.copy(
                    title = title,
                    lastMessage = lastMessage,
                    timestamp = System.currentTimeMillis()
                )
            } else {
                session
            }
        }
    }

    /**
     * 更新会话的最后消息
     */
    private fun updateSessionLastMessage(sessionId: String, lastMessage: String) {
        _sessionsCache.value = _sessionsCache.value.map { session ->
            if (session.id == sessionId) {
                session.copy(
                    lastMessage = lastMessage,
                    timestamp = System.currentTimeMillis()
                )
            } else {
                session
            }
        }
    }

    /**
     * 处理 API 错误响应
     */
    private fun <T> handleApiError(body: ApiResponse<T>?): Result<Nothing> {
        val code = body?.code ?: -1
        val message = body?.message ?: NetworkErrorHandler.getErrorMessage(code)
        
        // Token 相关错误需要重新登录
        if (body?.isTokenError == true) {
            // 清除登录状态
            // 这里可以触发一个事件让 UI 跳转到登录页
        }
        
        return Result.failure(ApiException(code, message))
    }
}

// ==================== 扩展函数：DTO 转 Model ====================

/**
 * SessionInfo -> Session
 */
private fun SessionInfo.toSession(): Session {
    return Session(
        id = this.id,
        title = this.title,
        lastMessage = this.lastMessage,
        timestamp = this.timestamp
    )
}

/**
 * MessageInfo -> Message
 */
private fun MessageInfo.toMessage(): Message {
    return Message(
        id = this.id,
        sessionId = this.sessionId,
        content = this.content,
        isUser = this.isUser,
        timestamp = this.timestamp,
        imageBase64 = null  // 在线模式使用 imageUrl，这里暂不处理
    )
}
