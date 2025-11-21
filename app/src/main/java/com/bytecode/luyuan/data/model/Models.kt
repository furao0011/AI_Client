package com.bytecode.luyuan.data.model

data class User(
    val id: String,
    val username: String,
    val email: String,
    val avatarUrl: String? = null
)

data class Session(
    val id: String,
    val title: String,
    val lastMessage: String,
    val timestamp: Long
)

data class Message(
    val id: String,
    val sessionId: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long
)
