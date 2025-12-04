# 客户端 API 调用示例

本文档提供客户端对接服务端 API 的 Kotlin 代码示例，基于现有的 Retrofit + OkHttp 架构。

---

## 一、API 接口定义

### 1.1 认证接口 (AuthApi.kt)

```kotlin
package com.bytecode.luyuan.data.remote

import retrofit2.Response
import retrofit2.http.*

/**
 * 用户认证 API 接口
 */
interface AuthApi {
    
    @POST("api/auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<ApiResponse<AuthResult>>
    
    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<ApiResponse<AuthResult>>
    
    @GET("api/user/profile")
    suspend fun getProfile(
        @Header("Authorization") token: String
    ): Response<ApiResponse<UserInfo>>
    
    @PUT("api/user/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): Response<ApiResponse<UserInfo>>
}

// DTO 数据类
data class RegisterRequest(
    val username: String,
    val password: String,
    val email: String? = null
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class UpdateProfileRequest(
    val username: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null
)

data class AuthResult(
    val user: UserInfo,
    val token: String,
    val expiresAt: Long? = null
)

data class UserInfo(
    val id: String,
    val username: String,
    val email: String?,
    val avatarUrl: String?,
    val createdAt: Long? = null
)
```

---

### 1.2 会话接口 (SessionApi.kt)

```kotlin
package com.bytecode.luyuan.data.remote

import retrofit2.Response
import retrofit2.http.*

/**
 * 会话管理 API 接口
 */
interface SessionApi {
    
    @GET("api/sessions")
    suspend fun getSessions(
        @Header("Authorization") token: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): Response<ApiResponse<SessionListResult>>
    
    @POST("api/sessions")
    suspend fun createSession(
        @Header("Authorization") token: String,
        @Body request: CreateSessionRequest
    ): Response<ApiResponse<SessionInfo>>
    
    @GET("api/sessions/{sessionId}")
    suspend fun getSession(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String
    ): Response<ApiResponse<SessionInfo>>
    
    @PUT("api/sessions/{sessionId}")
    suspend fun updateSession(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String,
        @Body request: UpdateSessionRequest
    ): Response<ApiResponse<SessionInfo>>
    
    @DELETE("api/sessions/{sessionId}")
    suspend fun deleteSession(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String
    ): Response<ApiResponse<Unit>>
    
    @DELETE("api/sessions/all")
    suspend fun clearAllSessions(
        @Header("Authorization") token: String
    ): Response<ApiResponse<Unit>>
}

// DTO 数据类
data class CreateSessionRequest(
    val title: String? = null
)

data class UpdateSessionRequest(
    val title: String
)

data class SessionListResult(
    val sessions: List<SessionInfo>,
    val total: Int,
    val page: Int,
    val size: Int
)

data class SessionInfo(
    val id: String,
    val title: String,
    val lastMessage: String,
    val timestamp: Long
)
```

---

### 1.3 消息接口 (MessageApi.kt)

```kotlin
package com.bytecode.luyuan.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * 消息管理 API 接口
 */
interface MessageApi {
    
    @GET("api/sessions/{sessionId}/messages")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50
    ): Response<ApiResponse<MessageListResult>>
    
    @POST("api/sessions/{sessionId}/messages")
    suspend fun sendMessage(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String,
        @Body request: SendMessageRequest
    ): Response<ApiResponse<SendMessageResult>>
    
    @Streaming
    @POST("api/sessions/{sessionId}/messages/stream")
    suspend fun sendMessageStream(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String,
        @Body request: SendMessageRequest
    ): Response<ResponseBody>
    
    @PUT("api/sessions/{sessionId}/messages/{messageId}")
    suspend fun editMessage(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String,
        @Path("messageId") messageId: String,
        @Body request: EditMessageRequest
    ): Response<ApiResponse<SendMessageResult>>
    
    @DELETE("api/sessions/{sessionId}/messages/{messageId}")
    suspend fun deleteMessage(
        @Header("Authorization") token: String,
        @Path("sessionId") sessionId: String,
        @Path("messageId") messageId: String
    ): Response<ApiResponse<Unit>>
}

// DTO 数据类
data class SendMessageRequest(
    val content: String,
    val imageUrl: String? = null
)

data class EditMessageRequest(
    val content: String
)

data class MessageListResult(
    val messages: List<MessageInfo>,
    val total: Int,
    val page: Int,
    val size: Int
)

data class MessageInfo(
    val id: String,
    val sessionId: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long,
    val imageUrl: String? = null
)

data class SendMessageResult(
    val userMessage: MessageInfo,
    val aiMessage: MessageInfo?,
    val sessionTitle: String? = null
)
```

---

### 1.4 图片上传接口 (UploadApi.kt)

```kotlin
package com.bytecode.luyuan.data.remote

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

/**
 * 文件上传 API 接口
 */
interface UploadApi {
    
    @Multipart
    @POST("api/upload/image")
    suspend fun uploadImage(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part
    ): Response<ApiResponse<ImageUploadResult>>
    
    @POST("api/upload/image/base64")
    suspend fun uploadImageBase64(
        @Header("Authorization") token: String,
        @Body request: Base64UploadRequest
    ): Response<ApiResponse<ImageUploadResult>>
}

// DTO 数据类
data class Base64UploadRequest(
    val base64: String,
    val filename: String? = null
)

data class ImageUploadResult(
    val imageId: String,
    val url: String,
    val size: Long,
    val mimeType: String
)
```

---

### 1.5 通用响应包装 (ApiResponse.kt)

```kotlin
package com.bytecode.luyuan.data.remote

import com.google.gson.annotations.SerializedName

/**
 * 通用 API 响应包装类
 */
data class ApiResponse<T>(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("data")
    val data: T?
) {
    val isSuccess: Boolean get() = code == 0
}
```

---

## 二、服务封装

### 2.1 认证服务 (AuthService.kt)

```kotlin
package com.bytecode.luyuan.data.remote

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 认证服务
 * 
 * 管理用户登录状态和 Token
 */
class AuthService(
    private val context: Context,
    private val authApi: AuthApi
) {
    private val _token = MutableStateFlow<String?>(null)
    val token: Flow<String?> = _token.asStateFlow()
    
    private val _currentUser = MutableStateFlow<UserInfo?>(null)
    val currentUser: Flow<UserInfo?> = _currentUser.asStateFlow()
    
    val isLoggedIn: Boolean get() = _token.value != null
    
    /**
     * 获取 Authorization Header 值
     */
    fun getAuthHeader(): String = "Bearer ${_token.value}"
    
    /**
     * 用户注册
     */
    suspend fun register(username: String, password: String, email: String?): Result<AuthResult> {
        return try {
            val response = authApi.register(RegisterRequest(username, password, email))
            if (response.isSuccessful && response.body()?.isSuccess == true) {
                val result = response.body()!!.data!!
                _token.value = result.token
                _currentUser.value = result.user
                saveToken(result.token)
                Result.success(result)
            } else {
                Result.failure(Exception(response.body()?.message ?: "注册失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 用户登录
     */
    suspend fun login(username: String, password: String): Result<AuthResult> {
        return try {
            val response = authApi.login(LoginRequest(username, password))
            if (response.isSuccessful && response.body()?.isSuccess == true) {
                val result = response.body()!!.data!!
                _token.value = result.token
                _currentUser.value = result.user
                saveToken(result.token)
                Result.success(result)
            } else {
                Result.failure(Exception(response.body()?.message ?: "登录失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 登出
     */
    suspend fun logout() {
        _token.value = null
        _currentUser.value = null
        clearToken()
    }
    
    /**
     * 从本地存储恢复 Token
     */
    suspend fun restoreToken() {
        val savedToken = loadToken()
        if (savedToken != null) {
            _token.value = savedToken
            // 验证 Token 有效性
            try {
                val response = authApi.getProfile("Bearer $savedToken")
                if (response.isSuccessful && response.body()?.isSuccess == true) {
                    _currentUser.value = response.body()!!.data
                } else {
                    // Token 无效，清除
                    logout()
                }
            } catch (e: Exception) {
                logout()
            }
        }
    }
    
    // Token 持久化方法（使用 DataStore 或 SharedPreferences）
    private suspend fun saveToken(token: String) {
        // TODO: 实现 Token 保存
    }
    
    private suspend fun loadToken(): String? {
        // TODO: 实现 Token 读取
        return null
    }
    
    private suspend fun clearToken() {
        // TODO: 实现 Token 清除
    }
}
```

---

### 2.2 图片上传工具 (ImageUploader.kt)

```kotlin
package com.bytecode.luyuan.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

/**
 * 图片上传工具类
 */
class ImageUploader(
    private val context: Context,
    private val uploadApi: UploadApi,
    private val authService: AuthService
) {
    
    /**
     * 上传图片 URI，返回图片 URL
     */
    suspend fun uploadImage(imageUri: Uri): Result<String> {
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: return Result.failure(Exception("无法读取图片"))
            
            val bytes = inputStream.readBytes()
            inputStream.close()
            
            // 压缩图片
            val compressedBytes = compressImage(bytes)
            
            val requestBody = compressedBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", "image.jpg", requestBody)
            
            val response = uploadApi.uploadImage(
                authService.getAuthHeader(),
                part
            )
            
            if (response.isSuccessful && response.body()?.isSuccess == true) {
                Result.success(response.body()!!.data!!.url)
            } else {
                Result.failure(Exception(response.body()?.message ?: "上传失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 上传 Base64 图片，返回图片 URL
     */
    suspend fun uploadBase64(base64: String): Result<String> {
        return try {
            val response = uploadApi.uploadImageBase64(
                authService.getAuthHeader(),
                Base64UploadRequest(base64 = base64)
            )
            
            if (response.isSuccessful && response.body()?.isSuccess == true) {
                Result.success(response.body()!!.data!!.url)
            } else {
                Result.failure(Exception(response.body()?.message ?: "上传失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 压缩图片
     */
    private fun compressImage(bytes: ByteArray, maxSize: Int = 1024 * 1024): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        var quality = 90
        var output: ByteArray
        
        do {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            output = stream.toByteArray()
            quality -= 10
        } while (output.size > maxSize && quality > 10)
        
        return output
    }
}
```

---

## 三、使用示例

### 3.1 登录并获取会话列表

```kotlin
// 登录
val loginResult = authService.login("admin", "123456")
if (loginResult.isSuccess) {
    val user = loginResult.getOrNull()?.user
    Log.d("Auth", "登录成功: ${user?.username}")
    
    // 获取会话列表
    val sessionsResult = sessionApi.getSessions(authService.getAuthHeader())
    if (sessionsResult.isSuccessful) {
        val sessions = sessionsResult.body()?.data?.sessions
        sessions?.forEach { session ->
            Log.d("Session", "${session.title}: ${session.lastMessage}")
        }
    }
}
```

### 3.2 发送带图片的消息

```kotlin
// 1. 先上传图片
val imageUrl = imageUploader.uploadImage(imageUri).getOrNull()

// 2. 发送消息
val request = SendMessageRequest(
    content = "这张图片里有什么？",
    imageUrl = imageUrl
)
val result = messageApi.sendMessage(
    authService.getAuthHeader(),
    sessionId,
    request
)
```

### 3.3 流式消息处理

```kotlin
val response = messageApi.sendMessageStream(
    authService.getAuthHeader(),
    sessionId,
    SendMessageRequest(content = "你好")
)

response.body()?.byteStream()?.bufferedReader()?.use { reader ->
    var line: String?
    while (reader.readLine().also { line = it } != null) {
        if (line?.startsWith("data: ") == true) {
            val json = line!!.removePrefix("data: ")
            if (json == "[DONE]") break
            
            // 解析 SSE 事件
            val event = gson.fromJson(json, StreamEvent::class.java)
            when (event.type) {
                "ai_token" -> onToken(event.data as String)
                "ai_message" -> onComplete(event.data as MessageInfo)
            }
        }
    }
}
```
