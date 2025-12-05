package com.bytecode.luyuan.data.remote.api

import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * 消息管理 API 接口
 * 
 * 提供消息的获取、发送、编辑、删除功能，支持流式响应
 */
interface MessageApi {
    
    /**
     * 获取会话消息列表
     * 
     * @param token Bearer Token
     * @param sessionId 会话 ID
     * @param page 页码，从 0 开始
     * @param size 每页数量
     * @return 消息列表
     */
    @GET("api/sessions/{sessionId}/messages")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50
    ): Response<ApiResponse<MessageListResult>>
    
    /**
     * 发送消息（非流式）
     * 
     * @param token Bearer Token
     * @param sessionId 会话 ID
     * @param request 发送消息请求体
     * @return 用户消息和 AI 回复
     */
    @POST("api/sessions/{sessionId}/messages")
    suspend fun sendMessage(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String,
        @Body request: SendMessageRequest
    ): Response<ApiResponse<SendMessageResult>>
    
    /**
     * 发送消息（流式 SSE）
     * 
     * @param token Bearer Token
     * @param sessionId 会话 ID
     * @param request 发送消息请求体
     * @return SSE 流式响应
     */
    @Streaming
    @POST("api/sessions/{sessionId}/messages/stream")
    suspend fun sendMessageStream(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String,
        @Body request: SendMessageRequest
    ): Response<ResponseBody>
    
    /**
     * 编辑消息
     * 
     * 编辑用户消息后，服务端自动删除该消息之后的所有消息并重新生成 AI 回复
     * 
     * @param token Bearer Token
     * @param sessionId 会话 ID
     * @param messageId 消息 ID
     * @param request 编辑消息请求体
     * @return 编辑后的消息和新的 AI 回复
     */
    @PUT("api/sessions/{sessionId}/messages/{messageId}")
    suspend fun editMessage(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String,
        @Path("messageId") messageId: String,
        @Body request: EditMessageRequest
    ): Response<ApiResponse<SendMessageResult>>
    
    /**
     * 删除消息
     * 
     * 删除指定消息及其之后的所有消息
     * 
     * @param token Bearer Token
     * @param sessionId 会话 ID
     * @param messageId 消息 ID
     * @return 操作结果
     */
    @DELETE("api/sessions/{sessionId}/messages/{messageId}")
    suspend fun deleteMessage(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String,
        @Path("messageId") messageId: String
    ): Response<ApiResponse<Unit>>
}

// ==================== 请求数据类 ====================

/**
 * 发送消息请求体
 * 
 * @param content 文本内容
 * @param imageUrl 图片 URL（可选）
 */
data class SendMessageRequest(
    @SerializedName("content")
    val content: String,
    
    @SerializedName("imageUrl")
    val imageUrl: String? = null
)

/**
 * 编辑消息请求体
 * 
 * @param content 新的文本内容
 */
data class EditMessageRequest(
    @SerializedName("content")
    val content: String
)

// ==================== 响应数据类 ====================

/**
 * 消息列表结果
 * 
 * @param messages 消息列表
 * @param total 总数
 * @param page 当前页码
 * @param size 每页数量
 */
data class MessageListResult(
    @SerializedName("messages")
    val messages: List<MessageInfo>,
    
    @SerializedName("total")
    val total: Int,
    
    @SerializedName("page")
    val page: Int,
    
    @SerializedName("size")
    val size: Int
)

/**
 * 消息信息
 * 
 * @param id 消息唯一 ID
 * @param sessionId 所属会话 ID
 * @param content 消息文本内容
 * @param isUser true=用户消息, false=AI消息
 * @param timestamp 消息时间戳
 * @param imageUrl 图片 URL（可选）
 */
data class MessageInfo(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("sessionId")
    val sessionId: String,
    
    @SerializedName("content")
    val content: String,
    
    @SerializedName("isUser")
    val isUser: Boolean,
    
    @SerializedName("timestamp")
    val timestamp: Long,
    
    @SerializedName("imageUrl")
    val imageUrl: String? = null
)

/**
 * 发送消息结果
 * 
 * @param userMessage 用户消息
 * @param aiMessage AI 回复消息（可能为 null，如流式响应）
 * @param sessionTitle 会话标题（仅首条消息时返回）
 */
data class SendMessageResult(
    @SerializedName("userMessage")
    val userMessage: MessageInfo,
    
    @SerializedName("aiMessage")
    val aiMessage: MessageInfo? = null,
    
    @SerializedName("sessionTitle")
    val sessionTitle: String? = null
)

// ==================== SSE 事件数据类 ====================

/**
 * SSE 流式事件类型
 */
object StreamEventType {
    const val USER_MESSAGE = "user_message"
    const val AI_TOKEN = "ai_token"
    const val AI_MESSAGE = "ai_message"
    const val SESSION_TITLE = "session_title"
    const val ERROR = "error"
}

/**
 * SSE 流式事件
 * 
 * @param type 事件类型
 * @param data 事件数据
 */
data class StreamEvent(
    @SerializedName("type")
    val type: String,
    
    @SerializedName("data")
    val data: Any?
)
