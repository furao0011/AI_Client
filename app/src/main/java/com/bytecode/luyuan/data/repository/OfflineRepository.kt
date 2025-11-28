package com.bytecode.luyuan.data.repository

import com.bytecode.luyuan.data.local.ApiConfig
import com.bytecode.luyuan.data.local.ApiConfigDao
import com.bytecode.luyuan.data.local.MessageDao
import com.bytecode.luyuan.data.local.SessionDao
import com.bytecode.luyuan.data.local.UserDao
import com.bytecode.luyuan.data.local.UserPreferencesDataStore
import com.bytecode.luyuan.data.model.ApiConfigEntity
import com.bytecode.luyuan.data.model.Message
import com.bytecode.luyuan.data.model.Session
import com.bytecode.luyuan.data.model.User
import com.bytecode.luyuan.data.remote.OpenAiService
import com.bytecode.luyuan.data.remote.dto.ChatMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Room 数据库 + DataStore + 网络层实现的数据仓库
 * 
 * @param userDao 用户数据访问对象
 * @param sessionDao 会话数据访问对象
 * @param messageDao 消息数据访问对象
 * @param apiConfigDao API 配置数据访问对象
 * @param userPreferencesDataStore 用户偏好设置的 DataStore
 * @param openAiService OpenAI API 服务
 */
class OfflineRepository(
    private val userDao: UserDao,
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val apiConfigDao: ApiConfigDao,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val openAiService: OpenAiService
) : AppRepository {

    override val currentUser: Flow<User?> = userDao.getCurrentUser()
    override val sessions: Flow<List<Session>> = sessionDao.getAllSessions()

    // 使用 DataStore 持久化设置
    override val language: Flow<String> = userPreferencesDataStore.language
    override val darkMode: Flow<Boolean> = userPreferencesDataStore.darkMode
    override val apiConfig: Flow<ApiConfig> = userPreferencesDataStore.apiConfig
    
    // 多 API 配置管理
    override val savedApiConfigs: Flow<List<ApiConfigEntity>> = apiConfigDao.getAllConfigs()

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
        val config = apiConfig.first()
        if (!config.isConfigured) {
            return Result.failure(Exception("API not configured"))
        }
        return openAiService.testConnection(config.baseUrl, config.apiKey, config.modelName)
    }
    
    override suspend fun saveApiConfig(name: String, baseUrl: String, apiKey: String, modelName: String) {
        val config = ApiConfigEntity(
            id = UUID.randomUUID().toString(),
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
        // 应用到当前使用的配置
        userPreferencesDataStore.setApiConfig(config.baseUrl, config.apiKey, config.modelName)
    }
    
    override suspend fun setDefaultApiConfig(configId: String) {
        apiConfigDao.clearAllDefaults()
        apiConfigDao.setAsDefault(configId)
    }

    override suspend fun login(username: String, password: String): Boolean {
        delay(1000)
        if (username == "admin" && password == "123456") {
            val user = User("u1", "Admin User", "admin@example.com")
            userDao.insertUser(user)
            return true
        }
        return false
    }

    override suspend fun logout() {
        userDao.clearUsers()
    }

    override fun getMessages(sessionId: String): Flow<List<Message>> {
        return messageDao.getMessagesForSession(sessionId)
    }

    override suspend fun sendMessage(sessionId: String, content: String, imageBase64: String?) {
        // 检查是否是首条消息
        val existingMessages = messageDao.getMessagesForSessionSync(sessionId)
        val isFirstMessage = existingMessages.isEmpty()
        
        // 1. 保存用户消息（包含图片）
        val userMessage = Message(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            content = content,
            isUser = true,
            timestamp = System.currentTimeMillis(),
            imageBase64 = imageBase64
        )
        messageDao.insertMessage(userMessage)
        
        // 更新会话的最后消息
        val session = sessionDao.getSessionById(sessionId)
        if (session != null) {
            val displayMessage = if (imageBase64 != null) "[Image] $content" else content
            sessionDao.updateSession(session.copy(lastMessage = displayMessage, timestamp = System.currentTimeMillis()))
        }

        // 2. 调用 AI API 获取回复
        val (aiResponse, sessionTitle) = if (isFirstMessage && imageBase64 == null) {
            // 首条纯文本消息，获取标题
            getAiResponseWithTitle(content)
        } else if (imageBase64 != null) {
            // 包含图片的消息
            Pair(getAiResponseWithImage(sessionId, content, imageBase64), null)
        } else {
            Pair(getAiResponse(sessionId), null)
        }
        
        // 3. 保存 AI 回复
        val aiMessage = Message(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            content = aiResponse,
            isUser = false,
            timestamp = System.currentTimeMillis()
        )
        messageDao.insertMessage(aiMessage)
        
        // 更新会话的最后消息和标题（如果是首条消息）
        val updatedSession = sessionDao.getSessionById(sessionId)
        if (updatedSession != null) {
            val newSession = if (sessionTitle != null) {
                updatedSession.copy(
                    title = sessionTitle,
                    lastMessage = aiMessage.content, 
                    timestamp = System.currentTimeMillis()
                )
            } else {
                updatedSession.copy(lastMessage = aiMessage.content, timestamp = System.currentTimeMillis())
            }
            sessionDao.updateSession(newSession)
        }
    }

    /**
     * 获取首条消息的 AI 回复（带标题生成）
     * 
     * @param userMessage 用户消息
     * @return Pair<回复内容, 会话标题>
     */
    private suspend fun getAiResponseWithTitle(userMessage: String): Pair<String, String?> {
        val config = apiConfig.first()
        
        // 如果 API 未配置，使用 Mock 响应
        if (!config.isConfigured) {
            delay(1000)
            return Pair(
                "Echo: API not configured. Please configure API in settings.",
                userMessage.take(20)
            )
        }
        
        // 调用带标题的 API
        val result = openAiService.chatWithTitle(
            baseUrl = config.baseUrl,
            apiKey = config.apiKey,
            model = config.modelName,
            userMessage = userMessage
        )
        
        return result.fold(
            onSuccess = { response ->
                Pair(response.content, response.title)
            },
            onFailure = { error ->
                Pair("Error: ${error.message ?: "Unknown error"}", userMessage.take(20))
            }
        )
    }

    /**
     * 获取 AI 回复
     * 
     * 如果 API 已配置，调用真实 API；否则返回 Echo 响应
     */
    private suspend fun getAiResponse(sessionId: String): String {
        val config = apiConfig.first()
        
        // 如果 API 未配置，使用 Mock 响应
        if (!config.isConfigured) {
            delay(1000)
            return "Echo: API not configured. Please configure API in settings."
        }
        
        // 获取历史消息构建上下文
        val historyMessages = messageDao.getMessagesForSessionSync(sessionId)
        val chatMessages = historyMessages.map { msg ->
            ChatMessage(
                role = if (msg.isUser) "user" else "assistant",
                content = msg.content
            )
        }
        
        // 调用 API
        val result = openAiService.chat(
            baseUrl = config.baseUrl,
            apiKey = config.apiKey,
            model = config.modelName,
            messages = chatMessages
        )
        
        return result.getOrElse { error ->
            "Error: ${error.message ?: "Unknown error"}"
        }
    }
    
    /**
     * 获取包含图片的 AI 回复
     * 
     * @param sessionId 会话 ID
     * @param textContent 文本内容
     * @param imageBase64 Base64 编码的图片
     * @return AI 回复内容
     */
    private suspend fun getAiResponseWithImage(
        sessionId: String, 
        textContent: String, 
        imageBase64: String
    ): String {
        val config = apiConfig.first()
        
        // 如果 API 未配置，使用 Mock 响应
        if (!config.isConfigured) {
            delay(1000)
            return "Echo: API not configured. Please configure API in settings."
        }
        
        // 获取历史消息（不包含当前消息，因为当前消息已单独处理）
        val historyMessages = messageDao.getMessagesForSessionSync(sessionId)
            .dropLast(1)  // 排除刚插入的当前消息
            .map { msg ->
                ChatMessage(
                    role = if (msg.isUser) "user" else "assistant",
                    content = msg.content
                )
            }
        
        // 调用多模态 API
        val result = openAiService.chatWithImage(
            baseUrl = config.baseUrl,
            apiKey = config.apiKey,
            model = config.modelName,
            textContent = textContent,
            imageBase64 = imageBase64,
            historyMessages = historyMessages
        )
        
        return result.getOrElse { error ->
            "Error: ${error.message ?: "Unknown error"}"
        }
    }

    override suspend fun createSession(title: String): String {
        val id = UUID.randomUUID().toString()
        val session = Session(
            id = id,
            title = title,
            lastMessage = "",
            timestamp = System.currentTimeMillis()
        )
        sessionDao.insertSession(session)
        return id
    }

    override suspend fun deleteSession(sessionId: String) {
        sessionDao.deleteSessionById(sessionId)
    }

    override suspend fun clearAllHistory() {
        sessionDao.clearAllSessions()
        messageDao.clearAllMessages()
    }

    override suspend fun editMessage(message: Message, newContent: String) {
        // 删除该消息之后的所有消息（包括 AI 回复）
        messageDao.deleteMessagesAfter(message.sessionId, message.timestamp)
        
        // 更新当前消息内容
        val updatedMessage = message.copy(content = newContent, timestamp = System.currentTimeMillis())
        messageDao.updateMessage(updatedMessage)
        
        // 如果是用户消息，重新生成 AI 回复
        if (message.isUser) {
            val aiResponse = getAiResponse(message.sessionId)
            
            val aiMessage = Message(
                id = UUID.randomUUID().toString(),
                sessionId = message.sessionId,
                content = aiResponse,
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
            messageDao.insertMessage(aiMessage)
            
            val session = sessionDao.getSessionById(message.sessionId)
            if (session != null) {
                sessionDao.updateSession(session.copy(lastMessage = aiMessage.content, timestamp = System.currentTimeMillis()))
            }
        }
    }
    
    override suspend fun sendMessageStream(
        sessionId: String, 
        content: String, 
        imageBase64: String?,
        onToken: suspend (String) -> Unit
    ) {
        // 检查是否是首条消息
        val existingMessages = messageDao.getMessagesForSessionSync(sessionId)
        val isFirstMessage = existingMessages.isEmpty()
        
        // 1. 保存用户消息
        val userMessage = Message(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            content = content,
            isUser = true,
            timestamp = System.currentTimeMillis(),
            imageBase64 = imageBase64
        )
        messageDao.insertMessage(userMessage)
        
        // 更新会话的最后消息
        val session = sessionDao.getSessionById(sessionId)
        if (session != null) {
            val displayMessage = if (imageBase64 != null) "[Image] $content" else content
            sessionDao.updateSession(session.copy(lastMessage = displayMessage, timestamp = System.currentTimeMillis()))
        }
        
        val config = apiConfig.first()
        
        // 如果 API 未配置或包含图片，使用非流式响应
        if (!config.isConfigured || imageBase64 != null) {
            val aiResponse = if (imageBase64 != null) {
                getAiResponseWithImage(sessionId, content, imageBase64)
            } else {
                "Echo: API not configured. Please configure API in settings."
            }
            onToken(aiResponse)
            
            // 保存 AI 回复
            val aiMessage = Message(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                content = aiResponse,
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
            messageDao.insertMessage(aiMessage)
            
            val updatedSession = sessionDao.getSessionById(sessionId)
            if (updatedSession != null) {
                sessionDao.updateSession(updatedSession.copy(lastMessage = aiMessage.content, timestamp = System.currentTimeMillis()))
            }
            return
        }
        
        // 2. 流式获取 AI 回复
        val historyMessages = messageDao.getMessagesForSessionSync(sessionId)
        val chatMessages = historyMessages.map { msg ->
            ChatMessage(
                role = if (msg.isUser) "user" else "assistant",
                content = msg.content
            )
        }
        
        val responseBuilder = StringBuilder()
        
        try {
            openAiService.chatStream(
                baseUrl = config.baseUrl,
                apiKey = config.apiKey,
                model = config.modelName,
                messages = chatMessages
            ).collect { token ->
                responseBuilder.append(token)
                onToken(token)
            }
        } catch (e: Exception) {
            val errorMessage = "Error: ${e.message ?: "Unknown error"}"
            if (responseBuilder.isEmpty()) {
                responseBuilder.append(errorMessage)
                onToken(errorMessage)
            }
        }
        
        // 3. 保存完整的 AI 回复
        val aiResponse = responseBuilder.toString()
        val aiMessage = Message(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            content = aiResponse,
            isUser = false,
            timestamp = System.currentTimeMillis()
        )
        messageDao.insertMessage(aiMessage)
        
        // 如果是首条消息，尝试更新标题
        val updatedSession = sessionDao.getSessionById(sessionId)
        if (updatedSession != null) {
            val newSession = if (isFirstMessage && imageBase64 == null) {
                // 简单地用用户消息前20个字符作为标题
                updatedSession.copy(
                    title = content.take(20),
                    lastMessage = aiMessage.content, 
                    timestamp = System.currentTimeMillis()
                )
            } else {
                updatedSession.copy(lastMessage = aiMessage.content, timestamp = System.currentTimeMillis())
            }
            sessionDao.updateSession(newSession)
        }
    }
}
