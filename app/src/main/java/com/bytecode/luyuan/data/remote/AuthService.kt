package com.bytecode.luyuan.data.remote

import com.bytecode.luyuan.data.local.UserPreferencesDataStore
import com.bytecode.luyuan.data.remote.api.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 认证服务
 * 
 * 管理用户登录状态、Token 持久化和 API 调用
 * 
 * @param userPreferencesDataStore 用户偏好存储
 */
class AuthService(
    private val userPreferencesDataStore: UserPreferencesDataStore
) {
    
    companion object {
        private const val TAG = "AuthService"
    }
    
    private val gson = Gson()
    
    // Token 状态
    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()
    
    // 当前用户
    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()
    
    // 登录状态
    val isLoggedIn: Boolean get() = _token.value != null
    
    // API 实例缓存
    private var currentBaseUrl: String? = null
    private var retrofit: Retrofit? = null
    private var authApi: AuthApi? = null
    
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * 获取 Authorization Header 值
     * 
     * @return "Bearer {token}" 或 空字符串
     */
    fun getAuthHeader(): String {
        val tokenValue = _token.value
        return if (tokenValue != null) "Bearer $tokenValue" else ""
    }
    
    /**
     * 检查登录状态是否有效
     * 
     * App 启动时调用，从 DataStore 恢复 Token 并验证
     * 
     * @return 登录是否有效
     */
    suspend fun isLoginValid(): Boolean {
        val savedToken = userPreferencesDataStore.authToken.first()
        val expiresAt = userPreferencesDataStore.tokenExpiresAt.first()
        
        if (savedToken.isNullOrBlank()) {
            return false
        }
        
        // 检查 Token 是否过期
        if (expiresAt != null && expiresAt < System.currentTimeMillis()) {
            logout()
            return false
        }
        
        // 恢复 Token 到内存
        _token.value = savedToken
        return true
    }
    
    /**
     * 获取 AuthApi 实例
     * 
     * @param baseUrl 服务端基础 URL
     * @return AuthApi 实例
     */
    private fun getApi(baseUrl: String): AuthApi {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        
        if (currentBaseUrl != normalizedUrl || authApi == null) {
            currentBaseUrl = normalizedUrl
            retrofit = Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
            authApi = retrofit!!.create(AuthApi::class.java)
        }
        
        return authApi!!
    }
    
    /**
     * 用户注册
     * 
     * @param baseUrl 服务端地址
     * @param username 用户名
     * @param password 密码
     * @param email 邮箱（可选）
     * @return 注册结果
     */
    suspend fun register(
        baseUrl: String,
        username: String,
        password: String,
        email: String? = null
    ): Result<AuthResult> {
        return try {
            val api = getApi(baseUrl)
            val response = api.register(RegisterRequest(username, password, email))
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.isSuccess == true && body.data != null) {
                    // 保存 Token 和用户信息
                    _token.value = body.data.token
                    _currentUser.value = body.data.user
                    userPreferencesDataStore.setAuthToken(body.data.token, body.data.expiresAt)
                    Result.success(body.data)
                } else {
                    Result.failure(ApiException(body?.code ?: -1, body?.message ?: "注册失败"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(Exception("HTTP ${response.code()}: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 用户注册（使用默认服务端地址）
     * 
     * @param username 用户名
     * @param password 密码
     * @param email 邮箱（可选）
     * @return 注册结果
     */
    suspend fun register(
        username: String,
        password: String,
        email: String? = null
    ): Result<AuthResult> {
        val baseUrl = userPreferencesDataStore.serverBaseUrl.first()
        return register(baseUrl, username, password, email)
    }
    
    /**
     * 用户登录
     * 
     * @param baseUrl 服务端地址
     * @param username 用户名
     * @param password 密码
     * @return 登录结果
     */
    suspend fun login(
        baseUrl: String,
        username: String,
        password: String
    ): Result<AuthResult> {
        return try {
            val api = getApi(baseUrl)
            val response = api.login(LoginRequest(username, password))
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.isSuccess == true && body.data != null) {
                    // 保存 Token 和用户信息
                    _token.value = body.data.token
                    _currentUser.value = body.data.user
                    userPreferencesDataStore.setAuthToken(body.data.token, body.data.expiresAt)
                    Result.success(body.data)
                } else {
                    Result.failure(ApiException(body?.code ?: -1, body?.message ?: "登录失败"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(Exception("HTTP ${response.code()}: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 用户登录（使用默认服务端地址）
     * 
     * @param username 用户名
     * @param password 密码
     * @return 登录结果
     */
    suspend fun login(
        username: String,
        password: String
    ): Result<AuthResult> {
        val baseUrl = userPreferencesDataStore.serverBaseUrl.first()
        return login(baseUrl, username, password)
    }
    
    /**
     * 登出
     */
    suspend fun logout() {
        _token.value = null
        _currentUser.value = null
        userPreferencesDataStore.clearAuthToken()
    }
    
    /**
     * 从本地存储恢复 Token
     * 
     * @param baseUrl 服务端地址
     * @return 恢复是否成功
     */
    suspend fun restoreToken(baseUrl: String): Boolean {
        val savedToken = userPreferencesDataStore.authToken.first()
        val expiresAt = userPreferencesDataStore.tokenExpiresAt.first()
        
        if (savedToken.isNullOrBlank()) {
            return false
        }
        // 检查 Token 是否过期
        if (expiresAt != null && expiresAt < System.currentTimeMillis()) {
            logout()
            return false
        }
        
        _token.value = savedToken
        
        // 验证 Token 有效性，获取用户信息
        return try {
            val api = getApi(baseUrl)
            val response = api.getProfile("Bearer $savedToken")
            
            if (response.isSuccessful && response.body()?.isSuccess == true) {
                _currentUser.value = response.body()?.data
                true
            } else {
                // Token 无效，清除
                logout()
                false
            }
        } catch (e: Exception) {
            // 网络错误，暂时保留 Token
            true
        }
    }
    
    /**
     * 获取当前用户信息
     * 
     * @param baseUrl 服务端地址
     * @return 用户信息
     */
    suspend fun getProfile(baseUrl: String): Result<UserProfile> {
        val tokenValue = _token.value ?: return Result.failure(Exception("未登录"))
        
        return try {
            val api = getApi(baseUrl)
            val response = api.getProfile("Bearer $tokenValue")
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.isSuccess == true && body.data != null) {
                    _currentUser.value = body.data
                    Result.success(body.data)
                } else {
                    if (body?.isTokenError == true) {
                        logout()
                    }
                    Result.failure(ApiException(body?.code ?: -1, body?.message ?: "获取用户信息失败"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 更新用户信息
     * 
     * @param baseUrl 服务端地址
     * @param request 更新请求
     * @return 更新后的用户信息
     */
    suspend fun updateProfile(
        baseUrl: String,
        request: UpdateProfileRequest
    ): Result<UserProfile> {
        val tokenValue = _token.value ?: return Result.failure(Exception("未登录"))
        
        return try {
            val api = getApi(baseUrl)
            val response = api.updateProfile("Bearer $tokenValue", request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.isSuccess == true && body.data != null) {
                    _currentUser.value = body.data
                    Result.success(body.data)
                } else {
                    Result.failure(ApiException(body?.code ?: -1, body?.message ?: "更新失败"))
                }
            } else {
                Result.failure(Exception("HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
