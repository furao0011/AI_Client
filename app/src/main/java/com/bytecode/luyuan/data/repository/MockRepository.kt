package com.bytecode.luyuan.data.repository

import com.bytecode.luyuan.data.model.Message
import com.bytecode.luyuan.data.model.Session
import com.bytecode.luyuan.data.model.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object MockRepository {

    // Mock User
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // Mock Sessions
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    // Mock Messages (Map sessionId -> List<Message>)
    private val _messages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())

    // Mock Settings
    private val _language = MutableStateFlow("English")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _darkMode = MutableStateFlow(false)
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    fun setLanguage(lang: String) {
        _language.value = lang
    }

    fun setDarkMode(enabled: Boolean) {
        _darkMode.value = enabled
    }

    init {
        // Initialize with some dummy data
        val initialSessions = listOf(
            Session("1", "测试1", "test", System.currentTimeMillis() - 100000),
            Session("2", "测试2", "test", System.currentTimeMillis() - 200000),
            Session("3", "测试3", "test", System.currentTimeMillis() - 300000)
        )
        _sessions.value = initialSessions

        val initialMessages = mapOf(
            "1" to listOf(
                Message("101", "1", "Hello! How can I help you today?", false, System.currentTimeMillis() - 100000),
                Message("102", "1", "I need help with Android development.", true, System.currentTimeMillis() - 90000),
                Message("103", "1", "Sure, I can help with that. What specific topic are you interested in?", false, System.currentTimeMillis() - 80000)
            )
        )
        _messages.value = initialMessages
    }

    suspend fun login(username: String, password: String): Boolean {
        delay(1000) // Simulate network delay
        // Hardcoded credential check
        if (username == "admin" && password == "123456") {
            _currentUser.value = User("u1", "Admin User", "admin@example.com")
            return true
        }
        return false
    }

    fun logout() {
        _currentUser.value = null
    }

    fun getMessages(sessionId: String): StateFlow<List<Message>> {
        // In a real app, this would return a flow from Room
        // Here we just return a derived flow or the current list for simplicity in this mock
        // For simplicity, we will just expose a flow that filters the main map
        // But since we can't easily transform StateFlow to StateFlow with filter in a simple way without a scope,
        // we will just return the current list wrapped in a MutableStateFlow for this specific session
        // A better approach for this mock is to have the ViewModel observe the global map.
        // However, let's simulate fetching.
        return MutableStateFlow(_messages.value[sessionId] ?: emptyList())
    }
    
    // Better approach for reactive UI:
    val allMessages: StateFlow<Map<String, List<Message>>> = _messages.asStateFlow()

    suspend fun sendMessage(sessionId: String, content: String) {
        delay(300)
        val newMessage = Message(
            id = System.currentTimeMillis().toString(),
            sessionId = sessionId,
            content = content,
            isUser = true,
            timestamp = System.currentTimeMillis()
        )
        
        updateMessages(sessionId, newMessage)

        // Simulate AI Response
        delay(1500)
        val aiResponse = Message(
            id = (System.currentTimeMillis() + 1).toString(),
            sessionId = sessionId,
            content = "This is a mock AI response to: \"$content\"",
            isUser = false,
            timestamp = System.currentTimeMillis()
        )
        updateMessages(sessionId, aiResponse)
    }

    private fun updateMessages(sessionId: String, message: Message) {
        _messages.update { currentMap ->
            val currentList = currentMap[sessionId] ?: emptyList()
            currentMap + (sessionId to (currentList + message))
        }
        
        // Update session last message
        _sessions.update { currentSessions ->
            currentSessions.map { session ->
                if (session.id == sessionId) {
                    session.copy(lastMessage = message.content, timestamp = message.timestamp)
                } else {
                    session
                }
            }
        }
    }
    
    fun createSession(): String {
        val newId = System.currentTimeMillis().toString()
        val newSession = Session(newId, "New Chat", "Start a conversation", System.currentTimeMillis())
        _sessions.update { listOf(newSession) + it }
        return newId
    }
}
