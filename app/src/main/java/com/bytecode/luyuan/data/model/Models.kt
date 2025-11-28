package com.bytecode.luyuan.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String,
    val username: String,
    val email: String,
    val avatarUrl: String? = null
)

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey val id: String,
    val title: String,
    val lastMessage: String,
    val timestamp: Long
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class Message(
    @PrimaryKey val id: String,
    val sessionId: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long,
    val imageBase64: String? = null  // Base64 编码的图片数据（用于多模态输入）
)

/**
 * API 配置实体
 * 
 * 用于存储多个 API 配置，支持切换使用
 */
@Entity(tableName = "api_configs")
data class ApiConfigEntity(
    @PrimaryKey val id: String,
    val name: String,              // 配置名称
    val baseUrl: String,           // API 地址
    val apiKey: String,            // API 密钥
    val modelName: String,         // 模型名称
    val isDefault: Boolean = false, // 是否为默认配置
    val createdAt: Long = System.currentTimeMillis()
)
