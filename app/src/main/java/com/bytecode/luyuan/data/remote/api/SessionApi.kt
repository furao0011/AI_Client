package com.bytecode.luyuan.data.remote.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

/**
 * 会话管理 API 接口
 * 
 * 提供会话的增删改查功能
 */
interface SessionApi {
    
    /**
     * 获取会话列表
     * 
     * @param token Bearer Token
     * @param page 页码，从 0 开始
     * @param size 每页数量
     * @return 会话列表
     */
    @GET("api/sessions")
    suspend fun getSessions(
        @Header("Authorization") token: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): Response<ApiResponse<SessionListResult>>
    
    /**
     * 创建新会话
     * 
     * @param token Bearer Token
     * @param request 创建会话请求体
     * @return 新创建的会话信息
     */
    @POST("api/sessions")
    suspend fun createSession(
        @Header("Authorization") token: String,
        @Body request: CreateSessionRequest
    ): Response<ApiResponse<SessionInfo>>
    
    /**
     * 获取会话详情
     * 
     * @param token Bearer Token
     * @param sessionId 会话 ID
     * @return 会话详情
     */
    @GET("api/sessions/{sessionId}")
    suspend fun getSession(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String
    ): Response<ApiResponse<SessionInfo>>
    
    /**
     * 更新会话信息
     * 
     * @param token Bearer Token
     * @param sessionId 会话 ID
     * @param request 更新请求体
     * @return 更新后的会话信息
     */
    @PUT("api/sessions/{sessionId}")
    suspend fun updateSession(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String,
        @Body request: UpdateSessionRequest
    ): Response<ApiResponse<SessionInfo>>
    
    /**
     * 删除会话
     * 
     * @param token Bearer Token
     * @param sessionId 会话 ID
     * @return 操作结果
     */
    @DELETE("api/sessions/{sessionId}")
    suspend fun deleteSession(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String
    ): Response<ApiResponse<Unit>>
    
    /**
     * 清空所有会话
     * 
     * @param token Bearer Token
     * @return 操作结果
     */
    @DELETE("api/sessions/all")
    suspend fun clearAllSessions(
        @Header("Authorization") token: String
    ): Response<ApiResponse<Unit>>
}

// ==================== 请求数据类 ====================

/**
 * 创建会话请求体
 * 
 * @param title 会话标题（可选，默认"新对话"）
 */
data class CreateSessionRequest(
    @SerializedName("title")
    val title: String? = null
)

/**
 * 更新会话请求体
 * 
 * @param title 新标题
 */
data class UpdateSessionRequest(
    @SerializedName("title")
    val title: String
)

// ==================== 响应数据类 ====================

/**
 * 会话列表结果
 * 
 * @param sessions 会话列表
 * @param total 总数
 * @param page 当前页码
 * @param size 每页数量
 */
data class SessionListResult(
    @SerializedName("sessions")
    val sessions: List<SessionInfo>,
    
    @SerializedName("total")
    val total: Int,
    
    @SerializedName("page")
    val page: Int,
    
    @SerializedName("size")
    val size: Int
)

/**
 * 会话信息
 * 
 * @param id 会话唯一 ID
 * @param title 会话标题
 * @param lastMessage 最后一条消息预览
 * @param timestamp 最后更新时间戳
 */
data class SessionInfo(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("lastMessage")
    val lastMessage: String,
    
    @SerializedName("timestamp")
    val timestamp: Long
)
