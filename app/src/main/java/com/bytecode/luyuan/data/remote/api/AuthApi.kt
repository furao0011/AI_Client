package com.bytecode.luyuan.data.remote.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT

/**
 * 用户认证 API 接口
 * 
 * 提供用户注册、登录、获取/更新用户信息等功能
 */
interface AuthApi {
    
    /**
     * 用户注册
     * 
     * @param request 注册请求体
     * @return 包含用户信息和 Token 的响应
     */
    @POST("api/auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<ApiResponse<AuthResult>>
    
    /**
     * 用户登录
     * 
     * @param request 登录请求体
     * @return 包含用户信息和 Token 的响应
     */
    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<ApiResponse<AuthResult>>
    
    /**
     * 获取当前用户信息
     * 
     * @param token Bearer Token
     * @return 用户信息
     */
    @GET("api/user/profile")
    suspend fun getProfile(
        @Header("Authorization") token: String
    ): Response<ApiResponse<UserProfile>>
    
    /**
     * 更新用户信息
     * 
     * @param token Bearer Token
     * @param request 更新请求体
     * @return 更新后的用户信息
     */
    @PUT("api/user/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): Response<ApiResponse<UserProfile>>
    
    /**
     * 修改密码
     * 
     * @param token Bearer Token
     * @param request 修改密码请求体
     * @return 操作结果
     */
    @PUT("api/user/password")
    suspend fun changePassword(
        @Header("Authorization") token: String,
        @Body request: ChangePasswordRequest
    ): Response<ApiResponse<Unit>>
}

// ==================== 请求数据类 ====================

/**
 * 注册请求体
 * 
 * @param username 用户名（3-20字符）
 * @param password 密码（6-32字符）
 * @param email 邮箱（可选）
 */
data class RegisterRequest(
    @SerializedName("username")
    val username: String,
    
    @SerializedName("password")
    val password: String,
    
    @SerializedName("email")
    val email: String? = null
)

/**
 * 登录请求体
 * 
 * @param username 用户名
 * @param password 密码
 */
data class LoginRequest(
    @SerializedName("username")
    val username: String,
    
    @SerializedName("password")
    val password: String
)

/**
 * 更新用户信息请求体
 */
data class UpdateProfileRequest(
    @SerializedName("username")
    val username: String? = null,
    
    @SerializedName("email")
    val email: String? = null,
    
    @SerializedName("avatarUrl")
    val avatarUrl: String? = null
)

/**
 * 修改密码请求体
 */
data class ChangePasswordRequest(
    @SerializedName("oldPassword")
    val oldPassword: String,
    
    @SerializedName("newPassword")
    val newPassword: String
)

// ==================== 响应数据类 ====================

/**
 * 认证结果
 * 
 * @param user 用户信息
 * @param token JWT Token
 * @param expiresAt Token 过期时间戳（毫秒）
 */
data class AuthResult(
    @SerializedName("user")
    val user: UserProfile,
    
    @SerializedName("token")
    val token: String,
    
    @SerializedName("expiresAt")
    val expiresAt: Long? = null
)

/**
 * 用户信息
 * 
 * @param id 用户唯一 ID
 * @param username 用户名
 * @param email 邮箱
 * @param avatarUrl 头像 URL
 * @param createdAt 创建时间戳
 */
data class UserProfile(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("username")
    val username: String,
    
    @SerializedName("email")
    val email: String? = null,
    
    @SerializedName("avatarUrl")
    val avatarUrl: String? = null,
    
    @SerializedName("createdAt")
    val createdAt: Long? = null
)
