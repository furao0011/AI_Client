package com.bytecode.luyuan.data.remote

import android.util.Log
import com.bytecode.luyuan.data.remote.api.ApiException
import com.bytecode.luyuan.data.remote.api.ApiResponse
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 网络错误类型枚举
 */
enum class NetworkErrorType {
    /** 无网络连接 */
    NO_CONNECTION,
    /** 连接超时 */
    TIMEOUT,
    /** 服务器错误 (5xx) */
    SERVER_ERROR,
    /** 客户端错误 (4xx) */
    CLIENT_ERROR,
    /** 认证失败 (401/403) */
    AUTH_ERROR,
    /** 请求频率限制 (429) */
    RATE_LIMIT,
    /** 数据解析错误 */
    PARSE_ERROR,
    /** 业务逻辑错误 */
    BUSINESS_ERROR,
    /** 未知错误 */
    UNKNOWN
}

/**
 * 统一的网络错误封装类
 * 
 * @param type 错误类型
 * @param message 用户友好的错误消息
 * @param code HTTP 状态码或业务错误码
 * @param cause 原始异常
 */
data class NetworkError(
    val type: NetworkErrorType,
    val message: String,
    val code: Int = -1,
    val cause: Throwable? = null
) {
    /**
     * 是否需要重新登录
     */
    val requiresReLogin: Boolean
        get() = type == NetworkErrorType.AUTH_ERROR && code == 401

    /**
     * 是否可以重试
     */
    val isRetryable: Boolean
        get() = type in listOf(
            NetworkErrorType.NO_CONNECTION,
            NetworkErrorType.TIMEOUT,
            NetworkErrorType.SERVER_ERROR,
            NetworkErrorType.RATE_LIMIT
        )
}

/**
 * 网络错误处理工具类
 * 
 * 提供统一的网络错误处理和转换功能
 */
object NetworkErrorHandler {

    private const val TAG = "NetworkErrorHandler"

    // ==================== 错误消息常量 ====================

    private object Messages {
        const val NO_CONNECTION = "无法连接到服务器，请检查网络"
        const val TIMEOUT = "请求超时，请稍后重试"
        const val SERVER_ERROR = "服务器繁忙，请稍后重试"
        const val UNAUTHORIZED = "登录已过期，请重新登录"
        const val FORBIDDEN = "没有访问权限"
        const val NOT_FOUND = "请求的资源不存在"
        const val RATE_LIMIT = "请求过于频繁，请稍后重试"
        const val PARSE_ERROR = "数据解析失败"
        const val UNKNOWN = "发生未知错误"
        
        // 业务错误消息
        const val PARAM_ERROR = "参数错误"
        const val USERNAME_EXISTS = "用户名已存在"
        const val LOGIN_FAILED = "用户名或密码错误"
        const val TOKEN_INVALID = "登录凭证无效"
        const val TOKEN_EXPIRED = "登录已过期"
        const val SESSION_NOT_FOUND = "会话不存在"
        const val MESSAGE_NOT_FOUND = "消息不存在"
        const val UPLOAD_FAILED = "上传失败"
        const val AI_SERVICE_ERROR = "AI 服务调用失败"
        const val SYSTEM_ERROR = "系统错误"
    }
    
    /**
     * 根据错误码获取用户友好的错误消息
     * 
     * @param code 错误码
     * @return 错误消息
     */
    fun getErrorMessage(code: Int): String {
        return when (code) {
            ApiResponse.CODE_PARAM_ERROR -> Messages.PARAM_ERROR
            ApiResponse.CODE_USERNAME_EXISTS -> Messages.USERNAME_EXISTS
            ApiResponse.CODE_LOGIN_FAILED -> Messages.LOGIN_FAILED
            ApiResponse.CODE_TOKEN_INVALID -> Messages.TOKEN_INVALID
            ApiResponse.CODE_TOKEN_EXPIRED -> Messages.TOKEN_EXPIRED
            ApiResponse.CODE_SESSION_NOT_FOUND -> Messages.SESSION_NOT_FOUND
            ApiResponse.CODE_MESSAGE_NOT_FOUND -> Messages.MESSAGE_NOT_FOUND
            ApiResponse.CODE_UPLOAD_FAILED -> Messages.UPLOAD_FAILED
            ApiResponse.CODE_AI_SERVICE_ERROR -> Messages.AI_SERVICE_ERROR
            ApiResponse.CODE_SYSTEM_ERROR -> Messages.SYSTEM_ERROR
            else -> Messages.UNKNOWN
        }
    }

    /**
     * 将异常转换为 NetworkError
     * 
     * @param throwable 原始异常
     * @return 统一的 NetworkError 对象
     */
    fun handleException(throwable: Throwable): NetworkError {
        Log.e(TAG, "Network error occurred", throwable)

        return when (throwable) {
            // 协程取消，不需要处理
            is CancellationException -> throw throwable

            // API 业务错误
            is ApiException -> handleApiException(throwable)

            // 连接相关错误
            is UnknownHostException, is ConnectException -> NetworkError(
                type = NetworkErrorType.NO_CONNECTION,
                message = Messages.NO_CONNECTION,
                cause = throwable
            )

            // 超时错误
            is SocketTimeoutException -> NetworkError(
                type = NetworkErrorType.TIMEOUT,
                message = Messages.TIMEOUT,
                cause = throwable
            )

            // HTTP 错误
            is HttpException -> handleHttpException(throwable)

            // IO 错误
            is IOException -> NetworkError(
                type = NetworkErrorType.NO_CONNECTION,
                message = Messages.NO_CONNECTION,
                cause = throwable
            )

            // 其他未知错误
            else -> NetworkError(
                type = NetworkErrorType.UNKNOWN,
                message = throwable.message ?: Messages.UNKNOWN,
                cause = throwable
            )
        }
    }

    /**
     * 处理 API 业务错误
     */
    private fun handleApiException(exception: ApiException): NetworkError {
        val errorType = when (exception.code) {
            in 1001..1099 -> NetworkErrorType.AUTH_ERROR
            in 2001..2999 -> NetworkErrorType.BUSINESS_ERROR
            else -> NetworkErrorType.BUSINESS_ERROR
        }

        return NetworkError(
            type = errorType,
            message = exception.message ?: Messages.UNKNOWN,
            code = exception.code,
            cause = exception
        )
    }

    /**
     * 处理 HTTP 错误
     */
    private fun handleHttpException(exception: HttpException): NetworkError {
        val code = exception.code()
        val (type, message) = when (code) {
            401 -> NetworkErrorType.AUTH_ERROR to Messages.UNAUTHORIZED
            403 -> NetworkErrorType.AUTH_ERROR to Messages.FORBIDDEN
            404 -> NetworkErrorType.CLIENT_ERROR to Messages.NOT_FOUND
            429 -> NetworkErrorType.RATE_LIMIT to Messages.RATE_LIMIT
            in 400..499 -> NetworkErrorType.CLIENT_ERROR to (parseErrorBody(exception) ?: Messages.UNKNOWN)
            in 500..599 -> NetworkErrorType.SERVER_ERROR to Messages.SERVER_ERROR
            else -> NetworkErrorType.UNKNOWN to Messages.UNKNOWN
        }

        return NetworkError(
            type = type,
            message = message,
            code = code,
            cause = exception
        )
    }

    /**
     * 尝试解析错误响应体
     */
    private fun parseErrorBody(exception: HttpException): String? {
        return try {
            exception.response()?.errorBody()?.string()?.let { body ->
                // 简单提取 message 字段
                val messageRegex = """"message"\s*:\s*"([^"]+)"""".toRegex()
                messageRegex.find(body)?.groupValues?.getOrNull(1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse error body", e)
            null
        }
    }

    /**
     * 安全执行网络请求
     * 
     * @param block 网络请求代码块
     * @return Result<T> 包装的结果
     */
    suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = handleException(e)
            Result.failure(NetworkException(error))
        }
    }

    /**
     * 安全执行并处理 ApiResponse
     * 
     * @param block 返回 ApiResponse 的网络请求
     * @return Result<T> 包装的结果
     */
    suspend fun <T> safeApiCallWithResponse(
        block: suspend () -> ApiResponse<T>
    ): Result<T> {
        return try {
            val response = block()
            if (response.isSuccess) {
                val data = response.data
                if (data != null) {
                    Result.success(data)
                } else {
                    Result.failure(NetworkException(
                        NetworkError(
                            type = NetworkErrorType.PARSE_ERROR,
                            message = "响应数据为空"
                        )
                    ))
                }
            } else {
                Result.failure(NetworkException(
                    handleApiException(
                        ApiException(response.code, response.message ?: Messages.UNKNOWN)
                    )
                ))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = handleException(e)
            Result.failure(NetworkException(error))
        }
    }
}

/**
 * 网络异常类，包装 NetworkError
 * 
 * @param error 网络错误详情
 */
class NetworkException(val error: NetworkError) : Exception(error.message, error.cause)
