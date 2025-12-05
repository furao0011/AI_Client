package com.bytecode.luyuan.data.remote.api

import com.google.gson.annotations.SerializedName

/**
 * 服务端通用 API 响应包装类
 * 
 * @param T 响应数据类型
 */
data class ApiResponse<T>(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("data")
    val data: T?
) {
    companion object {
        /** 成功状态码 */
        const val CODE_SUCCESS = 0
        
        // 错误码定义
        const val CODE_PARAM_ERROR = 1001
        const val CODE_USERNAME_EXISTS = 1002
        const val CODE_LOGIN_FAILED = 1003
        const val CODE_TOKEN_INVALID = 2001
        const val CODE_TOKEN_EXPIRED = 2002
        const val CODE_SESSION_NOT_FOUND = 3001
        const val CODE_MESSAGE_NOT_FOUND = 3002
        const val CODE_UPLOAD_FAILED = 4001
        const val CODE_AI_SERVICE_ERROR = 5001
        const val CODE_SYSTEM_ERROR = 9999
    }
    
    /** 判断请求是否成功 */
    val isSuccess: Boolean get() = code == CODE_SUCCESS
    
    /** 判断是否为 Token 相关错误 */
    val isTokenError: Boolean get() = code == CODE_TOKEN_INVALID || code == CODE_TOKEN_EXPIRED
}

/**
 * 将 ApiResponse 转换为 Result
 * 
 * @return 成功时返回 data，失败时返回异常
 */
fun <T> ApiResponse<T>.toResult(): Result<T> {
    return if (isSuccess && data != null) {
        Result.success(data)
    } else {
        Result.failure(ApiException(code, message))
    }
}

/**
 * API 异常类
 * 
 * @param code 错误码
 * @param message 错误信息
 */
class ApiException(
    val code: Int,
    override val message: String
) : Exception(message) {
    
    /** 是否为 Token 相关错误 */
    val isTokenError: Boolean get() = code == ApiResponse.CODE_TOKEN_INVALID || 
                                       code == ApiResponse.CODE_TOKEN_EXPIRED
}
